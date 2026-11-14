"""Check the runtime dictionary packaging of release Lite APKs."""
import argparse
from pathlib import Path
import zipfile


# openFd() and the dictionary readers require these assets to be stored.
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
        for asset, magic in MAPPED_ASSETS.items():
            name = f'assets/{asset}'
            info = archive.getinfo(name)
            if info.compress_type != zipfile.ZIP_STORED:
                raise ValueError(f'{apk}: memory-mapped asset is compressed: {name}')
            with archive.open(info) as stream:
                if stream.read(4) != magic:
                    raise ValueError(f'{apk}: unexpected dictionary format: {name}')
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
    print(f'{apk}: runtime dictionary packaging verified; {apk.stat().st_size:,} bytes')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apks', type=Path, nargs='+')
    args = parser.parse_args()
    for apk in args.apks:
        verify_apk(apk)


if __name__ == '__main__':
    main()
