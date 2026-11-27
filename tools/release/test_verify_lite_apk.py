import contextlib
import importlib.util
import io
import gzip
import json
import hashlib
import lzma
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('lite_apk', Path(__file__).with_name('verify-lite-apk.py'))
lite_apk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lite_apk)


class LiteApkTest(unittest.TestCase):
    def fixture(self, directory, *, compressed=None, extra=None, bad_header=None, missing=None, stored_stream=None, bad_digest=False, encoding='xz'):
        path = Path(directory) / 'app-lite.apk'
        manifest = {'version': 1, 'assets': {}}
        with zipfile.ZipFile(path, 'w') as archive:
            for name, magic in lite_apk.MAPPED_ASSETS.items():
                data = b'OLD1' if name == bad_header else magic
                record = {'size': len(data), 'sha256': '0' * 64 if bad_digest else hashlib.sha256(data).hexdigest()}
                if encoding != 'xz':
                    record['compression'] = encoding
                manifest['assets'][name] = record
                if name != missing:
                    encode = gzip.compress if encoding == 'gz' else lzma.compress
                    archive.writestr(f'assets/{name}.{encoding}', encode(data),
                                     compress_type=zipfile.ZIP_DEFLATED if name == compressed else zipfile.ZIP_STORED)
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

    def test_gzip_requires_the_expected_release_format(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            apk = self.fixture(directory, encoding='gz')
            lite_apk.verify_apk(apk, 'gz')
            with self.assertRaisesRegex(ValueError, 'unexpected dictionary compression'):
                lite_apk.verify_apk(apk)

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
