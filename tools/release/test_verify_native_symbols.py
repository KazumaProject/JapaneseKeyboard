"""Regression cases for the release bundle guard; no Android SDK is needed."""
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('symbols', Path(__file__).with_name('verify-native-symbols.py'))
symbols = importlib.util.module_from_spec(spec)
spec.loader.exec_module(symbols)


class NativeSymbolsTest(unittest.TestCase):
    def bundle(self, directory, remove=None, mismatch=False):
        path = Path(directory)/'app.aab'
        with zipfile.ZipFile(path, 'w') as archive:
            for abi in ('arm64-v8a', 'x86_64'):
                for library in ('libzenz.so', 'libomp.so'):
                    binary = f'base/lib/{abi}/{library}'
                    metadata = f'BUNDLE-METADATA/com.android.tools.build.debugsymbols/{abi}/{library}.sym'
                    archive.writestr(binary, 'abc123')
                    if metadata != remove:
                        archive.writestr(metadata, 'def456' if mismatch else 'abc123')
        return path

    @staticmethod
    def readelf(command, **kwargs):
        return '.symtab' if '--sections' in command else 'Build ID: ' + Path(command[-1]).read_text()

    def test_matching_symbol_tables_pass(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(symbols.subprocess, 'check_output', side_effect=self.readelf), contextlib.redirect_stdout(io.StringIO()):
            symbols.verify(self.bundle(directory), 'llvm-readelf')

    def test_missing_abi_symbol_table_fails(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(symbols.subprocess, 'check_output', side_effect=self.readelf):
            path = self.bundle(directory, remove='BUNDLE-METADATA/com.android.tools.build.debugsymbols/x86_64/libzenz.so.sym')
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, 'Missing native library or symbol table'):
                symbols.verify(path, 'llvm-readelf')

    def test_unrelated_symbols_fail_build_id_check(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(symbols.subprocess, 'check_output', side_effect=self.readelf):
            with self.assertRaisesRegex(ValueError, 'Build ID mismatch'):
                symbols.verify(self.bundle(directory, mismatch=True), 'llvm-readelf')


if __name__ == '__main__':
    unittest.main()
