"""Check compressed runtime dictionaries in release Lite APKs."""
import argparse
from contextlib import contextmanager
import hashlib
import json
import lzma
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile


MAPPED_ASSETS = {
    'system/system.compact.kdict': b'KDS1',
    'english/reading.compact': b'KER2',
    'english/word.compact': b'KEW2',
    'english/token.compact': b'KET1',
    'english/english.lookup.index': b'KEI1',
    'english/grammar/categories.erol': b'EROL',
}
STREAMED_ASSETS = ('connection/connection.compact', 'connection/connection.compact.index')
LEGACY_ASSETS = (
    'system/tango.dat.zip', 'system/yomi.dat.zip', 'system/token.dat.zip',
    'english/reading.dat.zip', 'english/word.dat', 'english/token.dat.zip',
)


ZSTD_LIBRARY = re.compile(r'lib/[^/]+/libzstd-jni-[^/]+\.so')


@contextmanager
def open_dictionary(raw, compression):
    if compression == 'xz':
        with lzma.LZMAFile(raw, format=lzma.FORMAT_XZ) as stream:
            yield stream
        return

    # Use a temporary compressed input so zstd can stream output without a feeder thread.
    header = raw.read(4)
    if header != b'\x28\xb5\x2f\xfd':
        raise ValueError('invalid Zstandard dictionary frame')
    with tempfile.TemporaryFile() as compressed:
        compressed.write(header)
        shutil.copyfileobj(raw, compressed)
        compressed.seek(0)
        with subprocess.Popen(['zstd', '-qdc', '-M4MiB'], stdin=compressed, stdout=subprocess.PIPE) as process:
            with process.stdout as stream:
                yield stream
            if process.wait() != 0:
                raise ValueError('invalid Zstandard dictionary stream')


def verify_apk(apk, compression='xz'):
    extension = 'zst' if compression == 'zstd' else 'xz'
    other_extension = 'xz' if compression == 'zstd' else 'zst'
    with zipfile.ZipFile(apk) as archive:
        manifest = json.loads(archive.read('assets/dictionary-assets.json'))
        if manifest['version'] != 1 or set(manifest['assets']) != set(MAPPED_ASSETS):
            raise ValueError(f'{apk}: invalid dictionary asset manifest')
        names = set(archive.namelist())
        for asset, magic in MAPPED_ASSETS.items():
            if f'assets/{asset}' in names:
                raise ValueError(f'{apk}: duplicate uncompressed dictionary: {asset}')
            record = manifest['assets'][asset]
            if record.get('compression', 'xz') != compression:
                raise ValueError(f'{apk}: unexpected dictionary compression: {asset}')
            if f'assets/{asset}.{other_extension}' in names:
                raise ValueError(f'{apk}: duplicate compressed dictionary: {asset}')
            name = f'assets/{asset}.{extension}'
            info = archive.getinfo(name)
            if info.compress_type != zipfile.ZIP_STORED:
                raise ValueError(f'{apk}: dictionary is compressed again by ZIP: {name}')
            if not 0 < record['size'] <= 128 * 1024 * 1024:
                raise ValueError(f'{apk}: invalid dictionary size: {asset}')
            size = 0
            digest = hashlib.sha256()
            with archive.open(info) as raw, open_dictionary(raw, compression) as stream:
                if (header := stream.read(4)) != magic:
                    raise ValueError(f'{apk}: unexpected dictionary format: {name}')
                size += len(header)
                digest.update(header)
                while data := stream.read(64 * 1024):
                    size += len(data)
                    if size > record['size']:
                        raise ValueError(f'{apk}: dictionary exceeds expected size: {name}')
                    digest.update(data)
            if size != record['size'] or digest.hexdigest() != record['sha256']:
                raise ValueError(f'{apk}: dictionary checksum/size mismatch: {name}')
        for asset in STREAMED_ASSETS:
            info = archive.getinfo(f'assets/{asset}')
            if info.compress_type != zipfile.ZIP_DEFLATED:
                raise ValueError(f'{apk}: streamed connection asset is not compressed: {asset}')
        for asset in LEGACY_ASSETS:
            if f'assets/{asset}' in names:
                raise ValueError(f'{apk}: duplicate legacy dictionary: {asset}')
        has_zstd = False
        for name in sorted(names):
            if compression == 'zstd' and ZSTD_LIBRARY.fullmatch(name):
                has_zstd = True
            elif name.startswith('lib/') or name.lower().endswith(('.gguf', '.tflite', '.litertlm')):
                raise ValueError(f'{apk}: unexpected native library or AI model in Lite: {name}')
        if compression == 'zstd' and not has_zstd:
            raise ValueError(f'{apk}: missing Zstandard native library')
    print(f'{apk}: compressed runtime dictionary packaging verified; {apk.stat().st_size:,} bytes')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compression', choices=('xz', 'zstd'), default='xz')
    parser.add_argument('apks', type=Path, nargs='+')
    args = parser.parse_args()
    for apk in args.apks:
        verify_apk(apk, args.compression)


if __name__ == '__main__':
    main()
