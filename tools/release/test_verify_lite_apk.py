import contextlib
import importlib.util
import io
import json
import hashlib
import lzma
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('lite_apk', Path(__file__).with_name('verify-lite-apk.py'))
lite_apk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lite_apk)


class LiteApkTest(unittest.TestCase):
    def fixture(self, directory, *, compressed=None, extra=None, bad_header=None, missing=None,
                stored_stream=None, bad_digest=False, compression='xz', missing_library=False,
                wrong_payload=False):
        path = Path(directory) / 'app-lite.apk'
        manifest = {'version': 1, 'assets': {}}
        with zipfile.ZipFile(path, 'w') as archive:
            for name, magic in lite_apk.MAPPED_ASSETS.items():
                data = b'OLD1' if name == bad_header else magic
                manifest['assets'][name] = {'size': len(data), 'sha256': '0' * 64 if bad_digest else hashlib.sha256(data).hexdigest()}
                if compression == 'zstd':
                    manifest['assets'][name]['compression'] = 'zstd'
                    payload = subprocess.run(['zstd', '-qc'], input=data, check=True, capture_output=True).stdout
                    extension = 'zst'
                else:
                    payload = lzma.compress(data)
                    extension = 'xz'
                if wrong_payload:
                    payload = lzma.compress(data)
                if name != missing:
                    archive.writestr(f'assets/{name}.{extension}', payload,
                                     compress_type=zipfile.ZIP_DEFLATED if name == compressed else zipfile.ZIP_STORED)
            if compression == 'zstd' and not missing_library:
                archive.writestr('lib/arm64-v8a/libzstd-jni-1.5.7-29.so', b'library placeholder')
            archive.writestr('assets/dictionary-assets.json', json.dumps(manifest))
            for name in lite_apk.STREAMED_ASSETS:
                archive.writestr(f'assets/{name}', b'connection data',
                                 compress_type=zipfile.ZIP_STORED if name == stored_stream else zipfile.ZIP_DEFLATED)
            if extra:
                archive.writestr(extra, b'unexpected')
        return path

    def test_valid_packaging_passes(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            lite_apk.verify_apk(self.fixture(directory))

    def test_valid_zstandard_packaging_passes(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            lite_apk.verify_apk(self.fixture(directory, compression='zstd'), 'zstd')

    def test_zstandard_missing_library_and_extra_native_library_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'missing Zstandard native library'):
                lite_apk.verify_apk(self.fixture(directory, compression='zstd', missing_library=True), 'zstd')
            with self.assertRaisesRegex(ValueError, 'unexpected native library'):
                lite_apk.verify_apk(self.fixture(directory, compression='zstd', extra='lib/arm64-v8a/libextra.so'), 'zstd')

    def test_wrong_compression_and_duplicate_compressed_asset_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'unexpected dictionary compression'):
                lite_apk.verify_apk(self.fixture(directory), 'zstd')
            asset = next(iter(lite_apk.MAPPED_ASSETS))
            with self.assertRaisesRegex(ValueError, 'duplicate compressed dictionary'):
                lite_apk.verify_apk(self.fixture(directory, compression='zstd', extra=f'assets/{asset}.xz'), 'zstd')

    def test_zstandard_rejects_an_xz_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'invalid Zstandard dictionary frame'):
                lite_apk.verify_apk(self.fixture(directory, compression='zstd', wrong_payload=True), 'zstd')

    def test_zstandard_wrong_digest_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'checksum/size mismatch'):
                lite_apk.verify_apk(self.fixture(directory, compression='zstd', bad_digest=True), 'zstd')

    def test_compressed_mapped_asset_fails(self):
        for name in lite_apk.MAPPED_ASSETS:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                with self.assertRaisesRegex(ValueError, 'compressed again'):
                    lite_apk.verify_apk(self.fixture(directory, compressed=name))

    def test_missing_or_old_dictionary_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(KeyError):
                lite_apk.verify_apk(self.fixture(directory, missing='english/reading.compact'))
            with self.assertRaisesRegex(ValueError, 'unexpected dictionary format'):
                lite_apk.verify_apk(self.fixture(directory, bad_header='english/reading.compact'))

    def test_uncompressed_streamed_asset_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'streamed connection asset'):
                lite_apk.verify_apk(self.fixture(directory, stored_stream=lite_apk.STREAMED_ASSETS[0]))

    def test_wrong_digest_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'checksum/size mismatch'):
                lite_apk.verify_apk(self.fixture(directory, bad_digest=True))

    def test_duplicate_uncompressed_dictionary_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'duplicate uncompressed dictionary'):
                lite_apk.verify_apk(self.fixture(directory, extra='assets/english/reading.compact'))

    def test_legacy_copies_native_libraries_and_models_fail(self):
        for name in [f'assets/{asset}' for asset in lite_apk.LEGACY_ASSETS] + [
                'lib/arm64-v8a/libexample.so', 'assets/model.gguf', 'assets/model.tflite', 'assets/model.litertlm']:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(ValueError):
                    lite_apk.verify_apk(self.fixture(directory, extra=name))


if __name__ == '__main__':
    unittest.main()
