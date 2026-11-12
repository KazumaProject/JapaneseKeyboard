"""Reject release APKs or public tags that do not match the workflow commit."""
import argparse
from pathlib import Path
import re
import subprocess
import zipfile


def verify_apk(apk, revision):
    with zipfile.ZipFile(apk) as archive:
        try:
            metadata = archive.read('META-INF/version-control-info.textproto').decode('utf-8')
        except KeyError as error:
            raise ValueError(f'{apk}: missing version-control-info.textproto') from error
    revisions = re.findall(r'^\s*revision:\s*"([0-9a-f]{40})"\s*$', metadata, re.MULTILINE)
    if revisions != [revision]:
        raise ValueError(f'{apk}: expected revision {revision}, found {revisions}')
    print(f'{apk}: source revision {revision}')


def verify_tag(tag, revision):
    ref = f'refs/tags/{tag}'
    output = subprocess.check_output(
        ['git', 'ls-remote', '--exit-code', 'origin', ref, f'{ref}^{{}}'], text=True)
    refs = dict(line.split()[::-1] for line in output.splitlines())
    # Annotated tags have a tag-object SHA and a separate peeled commit SHA.
    actual = refs.get(f'{ref}^{{}}', refs.get(ref))
    if actual != revision:
        raise ValueError(f'{tag}: public tag points to {actual}, expected {revision}; publish a new version/tag')
    print(f'{tag}: public tag matches {revision}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--tag', help='Also check the current public tag on origin')
    parser.add_argument('apks', type=Path, nargs='+')
    args = parser.parse_args()
    if not re.fullmatch(r'[0-9a-f]{40}', args.revision):
        parser.error('--revision must be a full Git commit SHA')
    for apk in args.apks:
        verify_apk(apk, args.revision)
    if args.tag:
        verify_tag(args.tag, args.revision)


if __name__ == '__main__':
    main()
