"""Check compressed runtime dictionaries in release Lite APKs."""
import argparse
import hashlib
import json
import lzma
from pathlib import Path
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


def verify_apk(apk):
    with zipfile.ZipFile(apk) as archive:
        manifest = json.loads(archive.read('assets/dictionary-assets.json'))
        if manifest['version'] != 1 or set(manifest['assets']) != set(MAPPED_ASSETS):
            raise ValueError(f'{apk}: invalid dictionary asset manifest')
        for asset, magic in MAPPED_ASSETS.items():
            if f'assets/{asset}' in archive.namelist():
                raise ValueError(f'{apk}: duplicate uncompressed dictionary: {asset}')
            name = f'assets/{asset}.xz'
            info = archive.getinfo(name)
            if info.compress_type != zipfile.ZIP_STORED:
                raise ValueError(f'{apk}: XZ dictionary is compressed again by ZIP: {name}')
            record = manifest['assets'][asset]
            if not 0 < record['size'] <= 128 * 1024 * 1024:
                raise ValueError(f'{apk}: invalid dictionary size: {asset}')
            size = 0
            digest = hashlib.sha256()
            with archive.open(info) as raw, lzma.LZMAFile(raw) as stream:
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
            if f'assets/{asset}' in archive.namelist():
                raise ValueError(f'{apk}: duplicate legacy dictionary: {asset}')
        for name in archive.namelist():
            if name.startswith('lib/') or name.lower().endswith(('.gguf', '.tflite', '.litertlm')):
                raise ValueError(f'{apk}: unexpected native library or AI model in Lite: {name}')
    print(f'{apk}: compressed runtime dictionary packaging verified; {apk.stat().st_size:,} bytes')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apks', type=Path, nargs='+')
    args = parser.parse_args()
    for apk in args.apks:
        verify_apk(apk)


if __name__ == '__main__':
    main()
