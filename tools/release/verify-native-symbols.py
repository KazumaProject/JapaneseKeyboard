#!/usr/bin/env python3
"""Reject Play bundles missing extractable native symbols, and verify ELF build IDs."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile


def verify(bundle, readelf):
    required = {(abi, library) for abi in ('arm64-v8a', 'x86_64')
                for library in ('libzenz.so', 'libomp.so')}
    with zipfile.ZipFile(bundle) as archive, tempfile.TemporaryDirectory() as directory:
        def build_id(name):
            path = Path(directory) / Path(name).name
            path.write_bytes(archive.read(name))
            output = subprocess.check_output([str(readelf), '--notes', str(path)], text=True)
            match = re.search(r'Build ID: ([0-9a-f]+)', output)
            if not match:
                raise ValueError(f'Missing ELF build ID: {name}')
            return match[1]

        names = set(archive.namelist())
        for abi, library in sorted(required):
            binary = f'base/lib/{abi}/{library}'
            symbols = f'BUNDLE-METADATA/com.android.tools.build.debugsymbols/{abi}/{library}.sym'
            if binary not in names or symbols not in names:
                raise ValueError(f'Missing native library or symbol table: {abi}/{library}')
            if archive.getinfo(symbols).file_size == 0:
                raise ValueError(f'Empty symbol table: {symbols}')
            if build_id(binary) != build_id(symbols):
                raise ValueError(f'Build ID mismatch: {abi}/{library}')
            path = Path(directory) / Path(symbols).name
            sections = subprocess.check_output([str(readelf), '--sections', str(path)], text=True)
            if '.symtab' not in sections:
                raise ValueError(f'Missing ELF symbol table: {symbols}')
            print(f'Verified native symbols and build ID: {abi}/{library}')
        print('LiteRT-LM is distributed stripped; its private debug symbols are not available.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bundle', type=Path)
    parser.add_argument('--readelf', type=Path)
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or
               str(Path.home() / 'Library/Android/sdk'))
    candidates = sorted((sdk / 'ndk/29.0.14206865/toolchains/llvm/prebuilt').glob('*/bin/llvm-readelf'))
    executable = args.readelf or (candidates[0] if candidates else shutil.which('llvm-readelf'))
    if not executable:
        parser.error('llvm-readelf not found; install NDK 29.0.14206865 or pass --readelf')
    verify(args.bundle, executable)


if __name__ == '__main__':
    main()
