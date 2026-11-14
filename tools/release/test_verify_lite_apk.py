import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('lite_apk', Path(__file__).with_name('verify-lite-apk.py'))
lite_apk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lite_apk)


class LiteApkTest(unittest.TestCase):
    def fixture(self, directory, *, compressed=None, extra=None, bad_header=None, missing=None, stored_stream=None):
        path = Path(directory) / 'app-lite.apk'
        with zipfile.ZipFile(path, 'w') as archive:
            for name, magic in lite_apk.MAPPED_ASSETS.items():
                if name != missing:
                    archive.writestr(f'assets/{name}', b'OLD1' if name == bad_header else magic,
                                     compress_type=zipfile.ZIP_DEFLATED if name == compressed else zipfile.ZIP_STORED)
            for name in lite_apk.STREAMED_ASSETS:
                archive.writestr(f'assets/{name}', b'connection data',
                                 compress_type=zipfile.ZIP_STORED if name == stored_stream else zipfile.ZIP_DEFLATED)
            if extra:
                archive.writestr(extra, b'unexpected')
        return path

    def test_valid_packaging_passes(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            lite_apk.verify_apk(self.fixture(directory))

    def test_compressed_mapped_asset_fails(self):
        for name in lite_apk.MAPPED_ASSETS:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                with self.assertRaisesRegex(ValueError, 'memory-mapped asset'):
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

    def test_legacy_copies_native_libraries_and_models_fail(self):
        for name in [f'assets/{asset}' for asset in lite_apk.LEGACY_ASSETS] + [
                'lib/arm64-v8a/libexample.so', 'assets/model.gguf', 'assets/model.tflite', 'assets/model.litertlm']:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(ValueError):
                    lite_apk.verify_apk(self.fixture(directory, extra=name))


if __name__ == '__main__':
    unittest.main()
