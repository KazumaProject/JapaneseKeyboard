#!/usr/bin/env python3
"""Snapshot release dependency license declarations and bundled notices for offline display.

Input is a JSON map from release variant to Gradle's resolved external modules
(group/name/version). POM evidence comes from docs/licenses/dependency-metadata.json;
missing coordinates must be audited there before adding them to the app catalog.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('resolved', type=Path)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--gradle-cache', type=Path, default=Path.home()/'.gradle/caches/modules-2/files-2.1')
    args = parser.parse_args()
    evidence = json.loads((ROOT/'docs/licenses/dependency-metadata.json').read_text())
    declared = {(m['group'], m['name'], m['version']): m for m in evidence['modules']}
    resolved = json.loads(args.resolved.read_text())
    modules = {}
    output = ROOT/'app/src/main/assets/software-licenses'
    if args.check:
        catalog = json.loads((output/'catalog.json').read_text())
        assert sorted(resolved) == catalog['variants'], 'Release variants changed; refresh the license catalog'
        for variant, rows in resolved.items():
            expected = {(row['group'], row['name'], row['version']) for row in rows}
            actual = {(row['group'], row['name'], row['version']) for row in catalog['modules'] if variant in row['variants']}
            assert expected == actual, f'Stale release license catalog for {variant}'
        for module in catalog['modules']:
            key = (module['group'], module['name'], module['version'])
            assert module['licenses'] == declared[key]['licenses'], key
            for notice in module['notices']:
                body = (ROOT/'app/src/main/assets'/notice['asset']).read_bytes()
                assert hashlib.sha256(body).hexdigest() == notice['sha256'], notice['asset']
        print('Verified release module coverage, declarations, and all preserved notice hashes')
        return
    output.mkdir(parents=True, exist_ok=True)

    def notices(data, prefix, result):
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for name in archive.namelist():
                if name.endswith('/'):
                    continue
                if name == 'classes.jar':
                    notices(archive.read(name), prefix + name + '/', result)
                elif any(word in Path(name).name.lower() for word in ('license', 'notice', 'copying')) and not name.endswith('.class'):
                    body = archive.read(name)
                    body.decode('utf-8')  # Refuse binary or unknown encodings rather than corrupting legal text.
                    digest = hashlib.sha256(body).hexdigest()
                    asset = f'software-licenses/{digest}.txt'
                    (ROOT/'app/src/main/assets'/asset).write_bytes(body)
                    result.append({'title': prefix + name, 'asset': asset, 'sha256': digest})

    for variant, rows in resolved.items():
        for row in rows:
            key = (row['group'], row['name'], row['version'])
            if key not in modules:
                original = declared[key]
                assert all('apache' in license['name'].lower() for license in original['licenses']), f'Audit license text mapping before adding {key}'
                # Recheck the exact POM evidence against cached bytes when available.
                for item in original['evidence']:
                    if item['url'].endswith('.pom'):
                        matches = list(args.gradle_cache.joinpath(*key).glob('*/*.pom'))
                        for pom in matches:
                            if pom.name == Path(item['url']).name:
                                assert hashlib.sha256(pom.read_bytes()).hexdigest() == item['sha256'], key
                module = {k: original[k] for k in ('group', 'name', 'version', 'pomUrl', 'licenses', 'evidence')}
                module['variants'] = []
                module['notices'] = []
                for archive in sorted(args.gradle_cache.joinpath(*key).glob('*/*')):
                    if archive.suffix in ('.aar', '.jar') and not any(x in archive.name for x in ('-sources', '-javadoc')):
                        notices(archive.read_bytes(), archive.name + '/', module['notices'])
                if key == ('javax.inject', 'javax.inject', '1'):
                    body = (ROOT/'docs/licenses/notices/javax-inject-1.txt').read_bytes()
                    digest = hashlib.sha256(body).hexdigest()
                    (output/(digest+'.txt')).write_bytes(body)
                    module['notices'].append({'title': 'Inject.java copyright and license header', 'asset': f'software-licenses/{digest}.txt', 'sha256': digest})
                modules[key] = module
            modules[key]['variants'].append(variant)
    catalog = {'variants': sorted(resolved), 'modules': [modules[k] for k in sorted(modules)]}
    (output/'catalog.json').write_text(json.dumps(catalog, ensure_ascii=False, indent=2)+'\n')
    print(f'Snapshotted {len(modules)} release module declarations and bundled notices')


if __name__ == '__main__':
    main()
