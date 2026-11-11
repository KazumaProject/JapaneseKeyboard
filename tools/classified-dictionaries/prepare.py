#!/usr/bin/env python3
"""Import a published dictionary release and regenerate its offline license notices."""
import argparse
import collections
import gzip
import hashlib
import html
import io
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unicodedata
import urllib.request
import zipfile

from english_verb_forms import build_english_verb_forms
from license_docs import update_license_doc

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = 'KazumaProject/kotlin-kana-kanji-converter'
MANIFEST = Path('tools/classified-dictionaries/distribution-manifest.json')
PROVENANCE = Path('tools/classified-dictionaries/category-provenance.json')
LICENSES = Path('app/src/main/assets/licenses')
CLASSIFIED_FIXTURE = Path('app/src/androidTest/assets/classified-expected-records.json')
ENGLISH_READING_FIXTURE = Path('app/src/androidTest/assets/english-reading-expected.json')
SURFACE_OVERRIDES = Path('app/src/main/assets/categories/surface-overrides.json')
ARCHIVE_FILE = 'japanese_keyboard_dictionary_assets.zip'
UPSTREAM_MANIFEST = 'dictionary-distribution-manifest.json'
LICENSE_FILE = 'DICTIONARY-LICENSES.txt'
SPECIALIST_IDS = {'kampo', 'medical'}
PERSON_LICENSE_HEADER = '<h2>Reviewed additions / 公式資料を照合した追加</h2>'
ENGLISH_LICENSE_HEADER = '<h2>著作権表示・ライセンス原文 / Copyright notices and original license texts</h2>'
SOURCES = {
    'person': ('src/main/bin/names.txt', None),
    'place': ('src/main/bin/place.txt.zip', 'place.txt'),
    'wiki': ('src/main/bin/only_wiki.txt.zip', 'only_wiki.txt'),
    'neologd': ('src/main/bin/only_neologd.txt.zip', 'only_neologd.txt'),
    'web': ('src/main/bin/wiki_neologd_common.txt.zip', 'wiki_neologd_common.txt'),
}
CACHE = ROOT / '.cache/classified-distribution'
ENGLISH_SUPPORT_FILE = 'english_dictionary_runtime_support.zip'
ENGLISH_ASSET_ROOT = 'app/src/main/assets/english/'
ENGLISH_ASSETS = {
    'reading.dat.zip': 'reading.dat.zip',
    'word.dat': 'word.dat',
    'token.dat.zip': 'token.dat.zip',
    'runtime-support/qwerty_glide_index.dat': 'qwerty_glide_index.dat',
    'grammar/categories.erol': 'grammar/categories.erol',
}
ENGLISH_NOTICE = 'app/src/main/assets/licenses/english.html'
ENGLISH_VERB_FORMS = ENGLISH_ASSET_ROOT + 'grammar/verb-forms.tsv'
ENGLISH_REVIEWED_SOURCE = (
    'https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/'
    + 'dbbb97fc9b7cdd32441d3719b76eb0493fa787a2/english-ime/data/reviewed/reviewed-source.tar.gz'
)

def download(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'Sumire-dictionary-importer'})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def fetch(url, expected=None):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / hashlib.sha256(url.encode()).hexdigest()
    if path.exists():
        data = path.read_bytes()
        if expected is None or sha(data) == expected:
            return data
    data = download(url)
    if expected is not None and sha(data) != expected:
        raise ValueError(f'SHA-256 mismatch: {url}')
    with tempfile.NamedTemporaryFile(dir=CACHE, delete=False) as cached:
        cached.write(data)
    Path(cached.name).replace(path)
    return data

def sha(data):
    return hashlib.sha256(data).hexdigest()

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data if isinstance(data, bytes) else data.encode())

def read_json(path):
    return json.loads(path.read_bytes())


def write_json(path, value):
    write(path, json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def resolve_release(tag, previous):
    if tag is None:
        return previous
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', tag):
        raise ValueError(f'Invalid dictionary release tag: {tag}')
    api = f'https://api.github.com/repos/{REPOSITORY}'
    release = json.loads(download(f'{api}/releases/tags/{tag}'))
    revision = json.loads(download(f'{api}/commits/refs/tags/{tag}'))['sha']
    if release['tag_name'] != tag or release['draft'] or not re.fullmatch(r'[0-9a-f]{40}', revision):
        raise ValueError(f'Invalid published release metadata for {tag}')
    digests = {asset['name']: asset.get('digest') or '' for asset in release['assets']}
    published = {'version': tag, 'revision': revision}
    for key, name in [('archiveSha256', ARCHIVE_FILE), ('upstreamManifestSha256', UPSTREAM_MANIFEST),
                      ('licensesSha256', LICENSE_FILE), ('supportArchiveSha256', ENGLISH_SUPPORT_FILE)]:
        digest = digests.get(name, '')
        if not re.fullmatch(r'sha256:[0-9a-f]{64}', digest):
            raise ValueError(f'{tag} is missing a published SHA-256 for {name}')
        published[key] = digest.removeprefix('sha256:')
    if tag == previous['version']:
        for key, value in published.items():
            if previous.get(key, previous['englishDictionary'].get(key)) != value:
                raise ValueError(f'Published metadata for pinned release {tag} changed: {key}')
    published['englishDictionary'] = {'supportArchiveSha256': published.pop('supportArchiveSha256')}
    return published


def archive(root, release, download_base):
    manifest_data = fetch(download_base + UPSTREAM_MANIFEST, release['upstreamManifestSha256'])
    manifest = json.loads(manifest_data)
    checksums = dict(line.split()[::-1] for line in fetch(download_base + 'DICTIONARY-SHA256SUMS').decode().splitlines())
    for name, expected in [(ARCHIVE_FILE, release['archiveSha256']), (UPSTREAM_MANIFEST, release['upstreamManifestSha256']),
                           (LICENSE_FILE, release['licensesSha256']),
                           (ENGLISH_SUPPORT_FILE, release['englishDictionary']['supportArchiveSha256'])]:
        assert checksums.get(name) == expected, f'Published checksums disagree for {name}'
    zipped = zipfile.ZipFile(io.BytesIO(fetch(download_base + ARCHIVE_FILE, release['archiveSha256'])))
    assert len(zipped.namelist()) == len(set(zipped.namelist()))
    assert set(zipped.namelist()) == set(manifest['files']) | {UPSTREAM_MANIFEST}
    assert zipped.read(UPSTREAM_MANIFEST) == manifest_data
    for name, checksum in manifest['files'].items():
        path = Path(name)
        assert name.startswith('app/src/main/assets/') and not path.is_absolute() and '..' not in path.parts, name
        assert sha(zipped.read(name)) == checksum, name
    # English payloads and notices are imported separately into the app layout.
    assets = {name: digest for name, digest in manifest.pop('files').items()
              if not name.startswith(ENGLISH_ASSET_ROOT)}
    for name in assets:
        write(root / name, zipped.read(name))
    # Normalize the release's person IDs to full asset paths for every consumer.
    for category in manifest['categories']:
        category['id'] = str(Path(category['assetPaths'][0]).parent.relative_to('app/src/main/assets/categories'))
    manifest['personCategoryFamily']['categoryIds'] = [
        'person/' + ident for ident in manifest['personCategoryFamily']['categoryIds']
    ]
    categories = manifest['categories']
    assert len({category['id'] for category in categories}) == len(categories) == manifest['categoryCount']
    for category in categories:
        assert re.fullmatch(r'[a-z0-9_]+(?:/[a-z0-9_]+)*', category['id']), category['id']
        assert set(category['assetPaths']) == {f'app/src/main/assets/categories/{category["id"]}/{name}.dat.zip'
                                               for name in ('yomi', 'tango', 'token')}
        assert all(path in assets for path in category['assetPaths'])
    manifest.update(revision=release['revision'], version=release['version'], archiveSha256=release['archiveSha256'],
                    upstreamManifestSha256=release['upstreamManifestSha256'], licensesSha256=release['licensesSha256'],
                    assets=assets)
    return manifest, zipped

def provenance(root, distribution, previous, raw):
    revision, version = distribution['revision'], distribution['version']
    manifest = json.loads(fetch(raw + 'src/main/classification/distribution-manifest.json'))
    pairs = collections.defaultdict(int)
    for index, (source, (path, member)) in enumerate(SOURCES.items()):
        data = fetch(raw + path)
        assert sha(data) == manifest['inputs'][path]
        text = zipfile.ZipFile(io.BytesIO(data)).read(member).decode() if member else data.decode()
        for line in text.splitlines():
            row = line.split('\t')
            pairs[(row[0], row[4])] |= 1 << index
    path = 'src/main/classification/distribution-decisions.tsv.gz'
    data = fetch(raw + path)
    assert sha(data) == manifest['files'][Path(path).name]
    counts = collections.defaultdict(collections.Counter)
    for line in gzip.decompress(data).decode().splitlines():
        reading, surface, categories, origin, status = line.split('\t')
        if status != 'classified':
            continue
        for category in categories.split(','):
            if category == 'person':
                continue
            for index, source in enumerate(SOURCES):
                if pairs[(reading, surface)] & (1 << index):
                    counts[category][source] += 1
    person_data = fetch(raw + 'src/main/classification/person-dictionary-candidates.tsv.gz')
    assert sha(person_data) == distribution['personSourceSha256']
    person_records = []
    for line in gzip.decompress(person_data).decode().splitlines():
        category, reading, left, right, cost, surface, *tags = line.split('\t')
        category = 'person/' + category
        person_records.append('\t'.join((category, reading, left, right, cost, surface)))
        for index, source in enumerate(SOURCES):
            if pairs[(reading, surface)] & (1 << index):
                counts[category][source] += 1
    result = {'personSourceSha256': sha(person_data), 'revision': revision, 'version': version, 'inputSha256': manifest['inputs'],
              'decisionSha256': sha(data), 'categorySourcePairs': {}}
    records_path = 'src/main/classification/distribution-candidates.tsv.gz'
    records_data = fetch(raw + records_path)
    assert sha(records_data) == manifest['files'][Path(records_path).name]
    specialist = json.loads(fetch(raw + 'src/main/specialist/distribution-manifest.json'))
    specialist_sources_path = 'src/main/specialist/sources.json'
    specialist_sources = json.loads(fetch(raw + specialist_sources_path, specialist['inputs'][specialist_sources_path]))
    specialist_records = fetch(raw + 'src/main/specialist/distribution-candidates.tsv.gz')
    assert sha(specialist_records) == specialist['files']['distribution-candidates.tsv.gz']
    specialist_provenance = fetch(raw + 'src/main/specialist/provenance.jsonl.gz')
    assert sha(specialist_provenance) == specialist['files']['provenance.jsonl.gz']
    admitted = collections.defaultdict(set)
    for line in gzip.decompress(specialist_records).decode().splitlines():
        category, reading, left, right, cost, surface = line.split('\t')
        admitted[category].add((reading, surface))
    original_sources = {}
    for source in ('kampo_core', 'kampo_herb', 'manbyo', 'hyakuyaku'):
        path = f'src/main/specialist/sources/{source}.jsonl.gz'
        frozen = fetch(raw + path)
        assert sha(frozen) == specialist['inputs'][path]
        original_sources[source] = {row['row']: row for row in
            (json.loads(line) for line in gzip.decompress(frozen).decode().splitlines())}
    collected_terms = []
    for path, checksum in specialist['inputs'].items():
        if '/collected-terms/' not in path:
            continue
        frozen = fetch(raw + path)
        assert sha(frozen) == checksum, path
        rows = [json.loads(line) for line in frozen.decode().splitlines()]
        original_sources['collected/' + path.removeprefix('src/main/specialist/')] = dict(enumerate(rows, 1))
        collected_terms.extend(rows)
    for line in gzip.decompress(specialist_provenance).decode().splitlines():
        row = json.loads(line)
        if row['status'] == 'adopted':
            original = original_sources[row['source']][row['sourceRow']]
            if row['source'].startswith('collected/'):
                assert original == row['collectionEvidence']
                assert original['reading'] == row['reading']
                assert original['surface'] == row['surface']
            else:
                assert original['reading'] == row['originalReading']
                assert original['surface'] == row['originalSurface']
            assert (row['reading'], row['surface']) in admitted[row['category']]
            counts[row['category']][row['source']] += 1
    assert {k: sum(v.values()) for k, v in counts.items() if k in admitted} == specialist['categoryRows']
    # Keep the new public-data-license attribution tied to its reviewed additions.
    mhlw_path = 'src/main/classification/reviews/2026-10-26-business-mhlw.jsonl'
    mhlw_data = fetch(raw + mhlw_path)
    assert sha(mhlw_data) == manifest['inputs'][mhlw_path]
    mhlw_rows = [json.loads(line) for line in mhlw_data.decode().splitlines()]
    mhlw_additions = [row for row in mhlw_rows if row['action'] == 'add']
    mhlw_reclassifications = [row for row in mhlw_rows if row['action'] == 'reclassify']
    assert all(row['evidence']['sourceName'] == 'MHLW Multilingual Glossary for Employment Management'
               and row['evidence']['sourceLicense'] == '公共データ利用規約（第1.0版） (PDL1.0)'
               for row in mhlw_additions + mhlw_reclassifications)
    mhlw_counts = collections.Counter(category for row in mhlw_additions for category in row['categories'])
    for category, count in mhlw_counts.items():
        counts[category]['mhlw'] += count
    result['mhlwReview'] = {
        'path': mhlw_path,
        'sha256': sha(mhlw_data),
        'sourceUrl': 'https://www.mhlw.go.jp/yougosyu/index.html',
        'termsUrl': 'https://www.mhlw.go.jp/chosakuken/',
        'licenseUrl': 'https://www.digital.go.jp/resources/open_data/public_data_license_v1.0',
        'sourceSha256': sorted({row['evidence']['sourceSha256'] for row in mhlw_additions + mhlw_reclassifications}),
        'license': '公共データ利用規約（第1.0版） (PDL1.0)',
        'addedRecords': len(mhlw_additions),
        'reclassifiedRecords': len(mhlw_reclassifications),
        'categoryCounts': dict(sorted(mhlw_counts.items())),
    }
    # Record each reviewed person's source URL without copying the source page.
    person_additions = []
    for addition in manifest['provenance']['personAdditions']:
        frozen = fetch(raw + addition['path'])
        assert sha(frozen) == addition['sha256']
        person_additions.extend(json.loads(line) for line in frozen.decode().splitlines())
    updates = distribution.get('dictionaryUpdates')
    if updates:
        assert sha(fetch(raw + 'src/main/classification/distribution-manifest.json')) == updates['classificationManifestSha256']
        review = fetch(raw + updates['reviewPath'], updates['reviewSha256'])
        result['releaseReview'] = dict(updates, records=[json.loads(line) for line in review.decode().splitlines()])
        followup_path = updates['reviewPath'].removesuffix('.jsonl') + '_demographics.jsonl'
        if followup_path in manifest['inputs']:
            followup = fetch(raw + followup_path, manifest['inputs'][followup_path])
            result['releaseReview'].update(followupPath=followup_path,
                                           followupRecords=[json.loads(line) for line in followup.decode().splitlines()])
        assert not updates.get('audienceAssignments') or result['releaseReview'].get('followupRecords'), followup_path
    result['personAdditions'] = person_additions
    result['specialist'] = specialist
    result['specialistSources'] = specialist_sources['sources']
    result['collectedTerms'] = collected_terms
    result['categorySourcePairs'] = dict(sorted(counts.items()))
    fingerprints = {}
    surface_overrides = {}
    sentinel_surfaces = collections.defaultdict(set)
    hira = set('あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんぁぃぅぇぉゃゅょゎっゔーがぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽ')
    kata = set('アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンァィゥェォャュョヮッヴーガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポ')

    base_records = [line for line in gzip.decompress(records_data).decode().splitlines() if not line.startswith('person\t')]
    all_records = base_records + gzip.decompress(specialist_records).decode().splitlines() + person_records
    for line in all_records:
        category, reading, left, right, cost, surface = line.split('\t')
        normalized_surface = unicodedata.normalize('NFKC', surface)
        sentinel = -2 if surface == reading or normalized_surface and set(normalized_surface) <= hira else -1 if normalized_surface and set(normalized_surface) <= kata else None
        decoded = reading if sentinel == -2 else ''.join(chr(ord(c) + 96) if '\u3041' <= c <= '\u3093' or c == 'ゔ' else c for c in reading) if sentinel == -1 else surface
        if sentinel is not None:
            sentinel_surfaces[(category, f'{sentinel}\t{reading}')].add(surface)
        if decoded != surface:
            key = f'{sentinel}\t{reading}'
            surface_overrides.setdefault(category, {})[key] = surface
        fingerprint = fingerprints.setdefault(category, {'count': 0, 'sum': 0, 'sample': {'reading': reading, 'surface': surface}})
        record = '\t'.join((reading, left, right, cost, surface))
        fingerprint['sum'] += int(sha(record.encode()), 16)
        fingerprint['count'] += 1
        if 4 <= len(reading) <= 8 and not 4 <= len(fingerprint['sample']['reading']) <= 8:
            fingerprint['sample'] = {'reading': reading, 'surface': surface}
    # Empty categories are intentionally distributed for taxonomy stability.
    for category in distribution['categories']:
        fingerprints.setdefault(category['id'], {'count': 0, 'sum': 0, 'sample': {'reading': '', 'surface': ''}})
    expected_path = root / CLASSIFIED_FIXTURE
    category_counts = {c['id']: c['recordCount'] for c in distribution['categories']}
    repairs = {}
    for category, surfaces in surface_overrides.items():
        assert all(len(sentinel_surfaces[(category, key)]) == 1 for key in surfaces), category
        paths = next(c['assetPaths'] for c in distribution['categories'] if c['id'] == category)
        repairs[category] = {'assets': {
            path.removeprefix('app/src/main/assets/'): distribution['assets'][path] for path in paths
        }, 'surfaces': surfaces}
    surface_asset = root / SURFACE_OVERRIDES
    write_json(surface_asset, {'revision': revision, 'categories': repairs})
    result['surfaceOverridesSha256'] = sha(surface_asset.read_bytes())
    if distribution.get('filterRevision'):
        # The public catalog may contain entries filtered out of the shipped archive.
        category_paths = {path for category in distribution['categories'] for path in category['assetPaths']}
        fixture_paths = category_paths | {'app/src/main/assets/pos_table.dat'}
        previous_fixture_paths = {path for category in previous['categories'] for path in category['assetPaths']}
        previous_repairs = read_json(ROOT / SURFACE_OVERRIDES)['categories']
        if (category_paths != previous_fixture_paths
                or any(distribution['assets'][path] != previous['assets'].get(path) for path in fixture_paths)
                or repairs != previous_repairs):
            write_json(root / MANIFEST, distribution)
            print('Generating record fingerprints from the verified filtered assets', flush=True)
            subprocess.run(['./gradlew', ':app:testLiteStandardDebugUnitTest', '--tests', '*ClassifiedFixtureGeneratorTest',
                            f'-PclassifiedFixtureRoot={root}'], cwd=ROOT, check=True)
        else:
            old_fixture = (ROOT / CLASSIFIED_FIXTURE).read_bytes()
            assert sha(old_fixture) == read_json(ROOT / PROVENANCE)['classifiedFixtureSha256']
            write(expected_path, old_fixture)
    else:
        expected = {category: {'count': item['count'], 'digestSum': format(item['sum'] % (1 << 256), '064x'), 'sample': item['sample']} for category, item in fingerprints.items()}
        write_json(expected_path, expected)
    expected = read_json(expected_path)
    assert {k: v['count'] for k, v in expected.items()} == category_counts
    assert all(re.fullmatch(r'[0-9a-f]{64}', v['digestSum']) for v in expected.values())
    catalog = fetch(raw + 'docs/english-dictionary-catalog.md')
    english_rows = []
    for line in catalog.decode().splitlines():
        if not line.startswith('| ') or line.startswith('| ---'):
            continue
        cells = line.split(' | ')
        surface = cells[0].removeprefix('| ').replace('\\|', '|')
        if len(cells) != 2:
            continue
        for reading, cost in re.findall(r'([^、]+?) \((-?\d+)\)', cells[1].removesuffix(' |')):
            english_rows.append(f'{reading}\t{cost}\t{surface}')
    assert english_rows, 'English reading catalog did not contain any dictionary rows'
    english_fingerprint = sum(int(sha(row.encode()), 16) for row in english_rows) % (1 << 256)
    write_json(root / ENGLISH_READING_FIXTURE, {
        'revision': revision, 'catalogSha256': sha(catalog), 'count': len(english_rows),
        'digestSum': format(english_fingerprint, '064x'),
    })
    result['englishCatalogSha256'] = sha(catalog)
    result['englishFixtureSha256'] = sha((root / ENGLISH_READING_FIXTURE).read_bytes())
    result['classifiedFixtureSha256'] = sha(expected_path.read_bytes())
    write_json(root / PROVENANCE, result)
    return result

def reviewed_categories(review):
    reviewed = {row['candidate_key']: row for row in review.get('records', []) if row['action'] == 'add'}
    for row in review.get('followupRecords', []):
        assert row['action'] == 'reclassify' and row['candidate_key'] in reviewed
        reviewed[row['candidate_key']] = row
    by_category = collections.defaultdict(list)
    for row in reviewed.values():
        for category in row['categories']:
            by_category[category].append(row)
    return by_category


def notices(root, distribution, provenance, raw, release_page, download_base):
    assets = root / LICENSES
    upstream = {
        'mozc': raw + 'src/main/resources/third-party/MOZC-LICENSE',
        'cmudict': raw + 'src/main/resources/third-party/CMUDICT-LICENSE',
        'ipadic-copying': raw + 'src/main/resources/third-party/IPADIC-COPYING',
        'release-data': raw + 'LICENSE-DATA.md',
        'ipadic': raw + 'src/main/resources/third-party/IPADIC-NOTICE',
        'jmdict': raw + 'src/main/resources/third-party/JMDICT-LICENSE.html',
        'data': raw + 'src/main/resources/third-party/LICENSE-DATA.md',
        'corpus': raw + 'src/main/resources/third-party/NOTICE.md',
        'converter': raw + 'src/main/resources/THIRD-PARTY-NOTICES.md',
        'cc-by-sa-4': 'https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt',
        'apache-2': 'https://www.apache.org/licenses/LICENSE-2.0.txt',
    }
    # Preserve already pinned external notices; refresh release-owned notices.
    snapshot_file = assets / 'notice-snapshots.json'
    old_snapshots = read_json(ROOT / LICENSES / snapshot_file.name)
    for key in ('person', 'place', 'wiki', 'neologd', 'merge-ut'):
        upstream[key] = old_snapshots[key]['url']
    for key in ('cc-by-sa-4', 'apache-2'):
        if key in old_snapshots:
            upstream[key] = old_snapshots[key]['url']
    specialist = provenance['specialist']
    for path, checksum in specialist['inputs'].items():
        if '/notices/' in path:
            key = Path(path).stem.lower()
            upstream[key] = raw + path
            assert sha(fetch(upstream[key], checksum)) == checksum, key
    upstream['distribution-licenses'] = download_base + LICENSE_FILE
    snapshots = {}
    for key, url in upstream.items():
        previous = old_snapshots.get(key, {})
        expected = previous.get('sha256') if previous.get('url') == url else None
        if key == 'distribution-licenses':
            expected = distribution['licensesSha256']
        data = fetch(url, expected)
        write(assets / 'sources' / (key + '.txt'), data)
        snapshots[key] = {'url': url, 'sha256': sha(data)}
    write_json(snapshot_file, snapshots)
    person_pairs = collections.defaultdict(set)
    data = fetch(raw + 'src/main/classification/person-dictionary-candidates.tsv.gz', distribution['personSourceSha256'])
    for line in gzip.decompress(data).decode().splitlines():
        category, reading, left, right, cost, surface, *tags = line.split('\t')
        person_pairs['person/' + category].add((reading, surface))
    person_attributions = {}
    for category in distribution['categories']:
        ident = category['id']
        if ident.startswith('person/'):
            additions = [row for row in provenance['personAdditions'] if (row['reading'], row['surface']) in person_pairs[ident]]
            details = PERSON_LICENSE_HEADER + '<p>Names/readings selected, normalized, assigned POS/cost and encoded. 氏名・読みの選定、正規化、品詞・コスト割当とバイナリ化。Source page text is not redistributed; per-entry copyright terms remain unconfirmed / 原資料本文は転載していません。各語の著作権条件は未確認。</p>'
            details += '<ul>' + ''.join('<li>' + html.escape(row['reading'] + ' / ' + row['surface']) + ': <a href="' + html.escape(row['source_url']) + '">Source / 出典</a></li>' for row in additions) + '</ul>'
            person_attributions[ident] = details
    render_dictionary_notices(assets, assets, distribution, provenance, snapshots, person_attributions, raw, release_page)


def render_dictionary_notices(output, source_directory, distribution, provenance, snapshots, person_attributions, raw, release_page):
    counts = provenance['categorySourcePairs']
    categories = [c for c in distribution['categories'] if c['id'] not in SPECIALIST_IDS]
    mhlw_review = provenance.get('mhlwReview', {})
    style = '<style>body{font:16px sans-serif;line-height:1.6;padding:16px;overflow-wrap:anywhere}pre{white-space:pre-wrap;font:13px monospace;line-height:1.5}h1{font-size:20px}h2{font-size:17px;margin-top:24px}</style>'
    def translated(japanese, english):
        return '<span class="translation" lang="ja">' + japanese + '</span><span class="translation" lang="en">' + english + '</span>'
    def heading(japanese, english):
        return '<h2>' + translated(japanese, english) + '</h2>'
    def page(title, details, keys):
        body = '<!doctype html><meta charset="utf-8">' + style + '<title>' + html.escape(title) + '</title><h1>' + html.escape(title) + '</h1>' + details
        body += heading('著作権表示・ライセンス原文', 'Copyright notices and original license texts')
        for key in keys:
            original = (source_directory / 'sources' / (key + '.txt')).read_text(encoding='utf-8')
            body += '<h2>' + html.escape(key) + '</h2><p><a href="' + html.escape(snapshots[key]['url']) + '">Source / 出典</a></p><pre>' + html.escape(original) + '</pre>'
        return body
    common = heading('概要', 'Overview') + '<p>' + translated('本アプリに同梱する辞書データです。出典ごとの利用条件と通知原文を以下に掲載しています。', 'Dictionary data bundled with this app. Source-specific terms and original notices are provided below.') + '</p>'
    common += heading('出典', 'Source') + f'<p><a href="{release_page}">' + translated('辞書データの配布元', 'Dictionary distribution') + '</a></p>'
    common += heading('対象版', 'Version') + f'<p>Distribution / 配布: {distribution["version"]} ({distribution["revision"]})</p>'
    common += heading('加工内容', 'Modifications') + '<p>' + translated('分類・正規化・重複処理・品詞とコストの割当を行い、IME 用の辞書バイナリに変換しています。', 'Categorized, normalized, deduplicated, assigned parts of speech and costs, and converted to IME dictionary binaries.') + '</p>'
    review = provenance.get('releaseReview', {})
    reviewed_by_category = reviewed_categories(review)
    if review:
        common += '<p>Reviewed release additions / 配布版の追加語・出典確認: <a href="' + html.escape(raw + review['reviewPath']) + '">' + html.escape(review['reviewPath']) + '</a> (SHA-256: ' + review['reviewSha256'] + ')</p>'
    common += heading('利用条件', 'License terms') + '<p>' + translated('各出典には、それぞれの利用条件が適用されます。元データの取得版および各語の著作権者との対応は、配布元の資料からは確認できていません。', 'Each source retains its respective terms. The original acquisition revision and per-entry copyright attribution could not be confirmed from the distributor’s materials.') + '</p>'
    for category in categories:
        ids = set(counts.get(category['id'], {}))
        keys = ['mozc', 'merge-ut']
        keys += [source for source in SOURCES if source in ids and source != 'web']
        if 'web' in ids:
            keys += [key for key in ('wiki', 'neologd') if key not in keys]
        if 'mhlw' in ids:
            keys += ['release-data']
        keys += ['cc-by-sa-4', 'apache-2']
        source_labels = [html.escape({'person': 'Personal names', 'place': 'Japan Post place data', 'wiki': 'Wikipedia', 'neologd': 'mecab-ipadic-neologd', 'web': 'Wikipedia / Neologd common data'}[source]) for source in SOURCES if source in ids]
        if 'mhlw' in ids:
            source_labels.append('MHLW employment terms / 厚生労働省の人事・労務用語')
        details = common + '<p>採用出典 / Contributing sources: ' + ', '.join(source_labels) + '</p>'
        details += '<p>Wikipedia: attribution and ShareAlike / 表示・継承。Neologd: Apache 2.0 with its source-data notices / 出典データの通知を含むApache 2.0。Personal-names upstream notice: Apache 2.0. Japan Post place-data notice: no copyright claim for postcode data. 各入力と通知の取得版が同一であることは、配布元の資料からは確認できていません。</p>'
        if rows := reviewed_by_category[category['id']]:
            details += heading('追加語・分類の出典', 'Sources for new entries and classifications')
            details += '<p>' + translated(
                '参照資料で照合した表記・読みや分類を辞書に収録しています。各語の著作権条件は未確認です。',
                'The bundled entries use spellings, readings, and classifications checked against the cited sources. Per-entry copyright terms are unconfirmed.',
            ) + '</p><ul>'
            for row in rows:
                label = html.escape(row['reading'] + ' / ' + row['surface'])
                details += f'<li><a href="{html.escape(row["evidence"]["reference"])}">{label}</a></li>'
            details += '</ul>'
            if category['id'].startswith('media_demographic/') and review.get('followupPath'):
                details += f'<p><a href="{html.escape(raw + review["followupPath"])}">' + translated('対象層分類の確認記録', 'Audience classification review') + '</a></p>'
        if 'mhlw' in ids:
            additions = mhlw_review['categoryCounts'][category['id']]
            details += '<h2>MHLW employment terms / 厚生労働省の人事・労務用語</h2>'
            details += '<p>Source / 出典: <a href="' + html.escape(mhlw_review['sourceUrl']) + '">MHLW Multilingual Glossary for Employment Management / 雇用管理に役立つ多言語用語集</a>. License / 条件: <a href="' + html.escape(mhlw_review['licenseUrl']) + '">Public Data License (Version 1.0) / 公共データ利用規約（第1.0版） (PDL1.0)</a>.</p>'
            details += '<p>' + str(additions) + ' terms were selected from the source, including explicitly provided Japanese readings, then categorized and converted to IME dictionary data. Definitions, translations and examples were not copied. / 出典の用語と明記された日本語読みを選定し、分類してIME辞書データに変換しています。定義・翻訳・例文は転載していません。</p>'
            details += '<p>Source terms and processing notice / 出典と加工内容: <a href="' + html.escape(mhlw_review['termsUrl']) + '">MHLW usage terms / 厚生労働省の利用規約</a>. Upstream reviewed additions and reclassifications / 上流の確認記録: <a href="' + html.escape(raw + mhlw_review['path']) + '">' + html.escape(mhlw_review['path']) + '</a> (SHA-256: ' + mhlw_review['sha256'] + ').</p>'
        details += person_attributions.get(category['id'], '')
        write(output / 'classified' / (category['id'] + '.html'), page(category['label'] + ' / ' + category['id'], details, keys))
    source_metadata = provenance['specialistSources']
    for category in distribution['categories']:
        if category['id'] not in SPECIALIST_IDS:
            continue
        ident = category['id']
        sources = [source for source in source_metadata if counts[ident].get(source['id'], 0)]
        details = common
        if ident == 'medical':
            inferred = provenance['specialist']['readingOrigins']['gemini_inferred_unverified']
            details += f'<p>{inferred:,} adopted readings were supplemented with Gemini. Automatic and inferred readings are unverified / 採用語の読み{inferred:,}件をGeminiで補完。自動読み・推定読みの正確性は未確認。</p>'
        else:
            details += '<p>Automatic readings are unverified / 自動読みの正確性は未確認。</p>'
        for source in sources:
            details += '<h2>' + html.escape(source['id']) + '</h2><p>' + html.escape(source['attribution']) + '</p><p>Version / 版: ' + html.escape(source['version']) + '</p><p>License / 条件: ' + html.escape(source['license']) + '</p><p><a href="' + html.escape(source['page']) + '">Source / 元データ</a></p><p>Input ZIP SHA-256: ' + source['archiveSha256'] + '</p><p>Adopted rows / 採用行: ' + str(counts[ident][source['id']]) + '</p>'
        keys = [source['id'] for source in sources] + (['cc-by-sa-2.1-jp'] if ident == 'kampo' else ['cc-by-4.0']) + ['release-data']
        collected = [row for row in provenance['collectedTerms'] if row['category'] == ident]
        if collected:
            details += '<h2>Source-backed additions / 出典を照合した追加</h2><p>Selected names and readings; original source page text is not redistributed. Per-entry copyright terms remain unconfirmed / 名称と読みを選定。原資料本文は転載していません。各語の著作権条件は未確認。</p><ul>'
            details += ''.join('<li>' + html.escape(row['reading'] + ' / ' + row['surface']) + ': <a href="' + html.escape(row['source_url']) + '">Source / 出典</a></li>' for row in collected) + '</ul>'
        write(output / 'classified' / (ident + '.html'), page(category['label'] + ' / ' + ident, details, keys))
    for ident, title, keys in [
        ('wiki', 'Japanese Wikipedia-derived data / 日本語Wikipedia由来の辞書データ', ['wiki','cc-by-sa-4']),
        ('core', 'Mozc dictionaries / Mozc基本・単漢字・絵文字・顔文字・記号辞書', ['mozc','ipadic','ipadic-copying']),
        ('english-reading', 'English reading dictionary / 英語読み辞書', ['converter','release-data','jmdict','cmudict','cc-by-sa-4']),
        ('ngram', 'N-gram / Unigram dictionaries', ['data','corpus','mozc','ipadic','cc-by-sa-4']),
        ('auxiliary', 'Reading correction / Proverbs / 読み修正・ことわざ辞書', ['converter','mozc']),
    ]:
        write(output / (ident + '.html'), page(title, common, keys))

def english_dictionary(root, archive, distribution, release, release_page, download_base):
    support_sha = release['englishDictionary']['supportArchiveSha256']
    support_data = fetch(download_base + ENGLISH_SUPPORT_FILE, support_sha)
    assets = {}
    with zipfile.ZipFile(io.BytesIO(support_data)) as support:
        manifest = json.loads(archive.read(UPSTREAM_MANIFEST))
        english = manifest['englishDictionary']
        support_manifest = json.loads(support.read('english-runtime-support-manifest.json'))
        builder = english['builderRevision']
        assert re.fullmatch(r'[0-9a-f]{40}', builder), builder
        assert builder == support_manifest['builderRevision']
        assert english['optionalSupport']['file'] == ENGLISH_SUPPORT_FILE
        assert english['optionalSupport']['sha256'] == support_sha
        for payload, checksum in support_manifest['requiredCorePayloadSha256'].items():
            assert english['runtimePayloadSha256'][payload] == checksum, payload
        assert len(support.namelist()) == len(set(support.namelist()))

        def verified(path):
            name = ENGLISH_ASSET_ROOT + path
            checksums, zipped = (manifest['files'], archive) if name in manifest['files'] else (support_manifest['files'], support)
            content = zipped.read(name)
            assert sha(content) == checksums[name], name
            runtime_path = path.removeprefix('runtime-support/')
            payload = content
            if path in ('reading.dat.zip', 'token.dat.zip'):
                runtime_path = path.removesuffix('.zip')
                with zipfile.ZipFile(io.BytesIO(content)) as wrapper:
                    assert wrapper.namelist() == [runtime_path], path
                    payload = wrapper.read(runtime_path)
            assert sha(payload) == english['runtimePayloadSha256'][runtime_path], path
            return content

        for source, destination in ENGLISH_ASSETS.items():
            path = ENGLISH_ASSET_ROOT + destination
            content = verified(source)
            write(root / path, content)
            assets[path] = sha(content)

        notices = collections.defaultdict(list)
        for name in sorted(manifest['files']):
            prefix = ENGLISH_ASSET_ROOT + 'grammar/notices/'
            if name.startswith(prefix):
                notices[verified(name.removeprefix(ENGLISH_ASSET_ROOT)).decode('utf-8')].append(name.removeprefix(prefix))

        source = f'https://github.com/{REPOSITORY}/blob/{builder}/english-ime/data/catalog/NAMED-VOCABULARY.md'
        original_notices = ''
        for original, names in notices.items():
            escaped = html.escape(original).replace('\r', '&#13;').replace('\n', '&#10;')
            original_notices += '<h3>' + html.escape(', '.join(names)) + '</h3><pre>' + escaped + '</pre>\n'

    distribution['englishDictionary'] = {
        'version': distribution['version'],
        'revision': builder,
        'archiveSha256': distribution['archiveSha256'],
        'supportArchiveSha256': support_sha,
        'catalogUrl': source,
        'reviewedGrammarSourceUrl': ENGLISH_REVIEWED_SOURCE,
        'assets': assets,
    }
    page = english_license_header(distribution, release_page) + original_notices
    write(root / ENGLISH_NOTICE, page)
    assets[ENGLISH_NOTICE] = sha((root / ENGLISH_NOTICE).read_bytes())
    write(root / ENGLISH_VERB_FORMS, build_english_verb_forms(fetch(ENGLISH_REVIEWED_SOURCE)))
    assets[ENGLISH_VERB_FORMS] = sha((root / ENGLISH_VERB_FORMS).read_bytes())


def english_license_header(distribution, release_page):
    title = 'English word dictionary / 英単語辞書'
    english = distribution['englishDictionary']
    return (
        f'<!doctype html><meta charset="utf-8"><title>{title}</title><h1>{title}</h1>'
        '<h2>出典と配布版 / Source and version</h2>'
        f'<p>配布: {distribution["version"]} ({distribution["revision"]})</p>'
        f'<p><a href="{release_page}">Android dictionary distribution / Android 向け配布</a></p>'
        f'<p>English dictionary builder revision / 英語辞書の生成コミット: <code>{english["revision"]}</code></p>'
        f'<p><a href="{english["catalogUrl"]}">English dictionary catalog / 英語辞書の出典・語彙台帳</a></p>'
        f'<p><a href="{english["reviewedGrammarSourceUrl"]}">Reviewed grammar input / レビュー済み文法データの入力原本</a></p>'
        '<p>Android bundles the reading, word, token, Glide and grammar files. '
        'The build repackages the reading, word and token arrays for Android. '
        'The verb correction table is derived from the reviewed finite verb forms. '
        'Each source retains the terms reproduced below.</p>'
        '<p>読み・表記・トークン・Glide・文法のデータを収録しています。'
        '読み・表記・トークンはビルド時に Android 向けの形式に再格納します。'
        '動詞の訂正用データはレビュー済みの活用形から生成しています。'
        '出典ごとの利用条件を以下に掲載します。</p>'
        + ENGLISH_LICENSE_HEADER
    )


def notice_pages_sha256(directory):
    pages = (f'{path.relative_to(directory).as_posix()}\t{sha(path.read_bytes())}\n'
             for path in sorted(directory.rglob('*.html')))
    return sha(''.join(pages).encode())


def check(root, update_docs=False, generated_licenses=None):
    manifest = read_json(root / MANIFEST)
    license_pages = generated_licenses or root / LICENSES
    version, revision = manifest['version'], manifest['revision']
    assert re.fullmatch(r'[0-9a-f]{40}', revision), revision
    for path, checksum in manifest['assets'].items():
        assert sha((root / path).read_bytes()) == checksum, path
    snapshots = read_json(root / LICENSES / 'notice-snapshots.json')
    release_url = f'https://github.com/{REPOSITORY}/releases/tag/{version}'
    assert snapshots['distribution-licenses']['url'] == f'https://github.com/{REPOSITORY}/releases/download/{version}/{LICENSE_FILE}'
    for key, value in snapshots.items():
        assert sha((root / LICENSES / 'sources' / (key + '.txt')).read_bytes()) == value['sha256'], key
        if REPOSITORY in value['url']:
            assert revision in value['url'] or f'/{version}/' in value['url'], key
    evidence = read_json(root / PROVENANCE)
    assert (evidence['version'], evidence['revision']) == (version, revision)
    mhlw = evidence['mhlwReview']
    assert mhlw['sha256'] == evidence['inputSha256'][mhlw['path']]
    assert sum(mhlw['categoryCounts'].values()) == mhlw['addedRecords']
    english = manifest['englishDictionary']
    assert (english['version'], english['archiveSha256']) == (version, manifest['archiveSha256'])
    assert re.fullmatch(r'[0-9a-f]{40}', english['revision'])
    assert re.fullmatch(r'[0-9a-f]{64}', english['supportArchiveSha256'])
    assert set(english['assets']) == {ENGLISH_ASSET_ROOT + name for name in ENGLISH_ASSETS.values()} | {ENGLISH_NOTICE, ENGLISH_VERB_FORMS}
    for path, checksum in english['assets'].items():
        asset = license_pages / 'english.html' if path == ENGLISH_NOTICE else root / path
        assert sha(asset.read_bytes()) == checksum, path
    classified = license_pages / 'classified'
    assert set(classified.rglob('*.html')) == {classified / (c['id'] + '.html') for c in manifest['categories']}
    for path in license_pages.rglob('*.html'):
        text = path.read_text()
        assert f'配布: {version} ({revision})' in text and release_url in text, f'Stale dictionary notice: {path}'
        if path == license_pages / 'english.html':
            assert f'<code>{english["revision"]}</code>' in text, path
            assert english['catalogUrl'] in text, path
            assert english['reviewedGrammarSourceUrl'] in text, path
    for category in mhlw['categoryCounts']:
        text = (classified / (category + '.html')).read_text()
        assert mhlw['sourceUrl'] in text and mhlw['licenseUrl'] in text
    reviewed = reviewed_categories(evidence.get('releaseReview', {}))
    for category, rows in reviewed.items():
        text = (classified / (category + '.html')).read_text()
        for row in rows:
            label = html.escape(row['reading'] + ' / ' + row['surface'])
            assert f'href="{html.escape(row["evidence"]["reference"])}">{label}</a>' in text, (category, label)
    assert sha((root / ENGLISH_READING_FIXTURE).read_bytes()) == evidence['englishFixtureSha256']
    assert sha((root / CLASSIFIED_FIXTURE).read_bytes()) == evidence['classifiedFixtureSha256']
    assert snapshots['distribution-licenses']['sha256'] == manifest['licensesSha256']
    assert sha((root / SURFACE_OVERRIDES).read_bytes()) == evidence['surfaceOverridesSha256']
    assert notice_pages_sha256(license_pages) == manifest['licensePagesSha256'], 'Dictionary license HTML differs from the pinned release'
    if generated_licenses is None:
        update_license_doc(root, manifest, evidence, snapshots, reviewed, update_docs)
    print(f"Verified {len(manifest['assets'])} distributed assets and the dictionary notices for {version}")


def generate_license_assets(output):
    sources = ROOT / LICENSES
    destination = output / 'licenses'
    if destination.resolve() == sources.resolve():
        raise ValueError('Generate the license assets in a build directory')
    manifest = read_json(ROOT / MANIFEST)
    evidence = read_json(ROOT / PROVENANCE)
    snapshots = read_json(sources / 'notice-snapshots.json')
    raw = f'https://raw.githubusercontent.com/{REPOSITORY}/{manifest["revision"]}/'
    release_page = f'https://github.com/{REPOSITORY}/releases/tag/{manifest["version"]}'

    # Reuse the source-specific sections preserved by the importer. The final
    # page digest verifies that these sections belong to the bundled release.
    person_attributions = {}
    for category in manifest['categories']:
        ident = category['id']
        if ident.startswith('person/'):
            page = (sources / 'classified' / (ident + '.html')).read_text(encoding='utf-8')
            _, heading, tail = page.partition(PERSON_LICENSE_HEADER)
            body, end, _ = tail.partition('</ul>')
            assert heading and end, f'Missing person attribution: {ident}'
            person_attributions[ident] = heading + body + end
    english = (ROOT / ENGLISH_NOTICE).read_bytes()
    _, heading, original_notices = english.partition(ENGLISH_LICENSE_HEADER.encode())
    assert heading and original_notices, 'Missing English word dictionary notices'

    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='dictionary-licenses-', dir=output.parent) as directory:
        staged = Path(directory) / 'licenses'
        render_dictionary_notices(staged, sources, manifest, evidence, snapshots, person_attributions, raw, release_page)
        write(staged / 'english.html', english_license_header(manifest, release_page).encode() + original_notices)
        check(ROOT, generated_licenses=staged)
        if destination.exists():
            shutil.rmtree(destination)
        staged.replace(destination)


def prepare(tag):
    previous = read_json(ROOT / MANIFEST)
    release = resolve_release(tag, previous)
    version, revision = release['version'], release['revision']
    raw = f'https://raw.githubusercontent.com/{REPOSITORY}/{revision}/'
    release_page = f'https://github.com/{REPOSITORY}/releases/tag/{version}'
    download_base = f'https://github.com/{REPOSITORY}/releases/download/{version}/'
    CACHE.mkdir(parents=True, exist_ok=True)
    doc = Path('docs/licenses/DICTIONARIES.md')
    original_doc = (ROOT / doc).read_bytes()
    with tempfile.TemporaryDirectory(prefix='import-', dir=CACHE) as directory:
        stage = Path(directory)
        write(stage / doc, original_doc)
        distribution, zipped = archive(stage, release, download_base)
        with zipped:
            english_dictionary(stage, zipped, distribution, release, release_page, download_base)
        evidence = provenance(stage, distribution, previous, raw)
        notices(stage, distribution, evidence, raw, release_page, download_base)
        distribution['licensePagesSha256'] = notice_pages_sha256(stage / LICENSES)
        write_json(stage / MANIFEST, distribution)
        check(stage, update_docs=True)

        # Publish only after every input and generated document has been verified.
        lock = CACHE / 'publish.lock'
        try:
            lock.mkdir()
        except FileExistsError as error:
            raise ValueError(f'Another dictionary import is publishing files: {lock}') from error
        try:
            assert read_json(ROOT / MANIFEST) == previous, 'The bundled distribution changed during import'
            assert (ROOT / doc).read_bytes() == original_doc, f'{doc} changed during import'
            files = {path.relative_to(stage) for path in stage.rglob('*') if path.is_file()}
            managed = set(map(Path, previous['assets']))
            managed.update(map(Path, previous['englishDictionary']['assets']))
            managed.update(p.relative_to(ROOT) for p in (ROOT / LICENSES / 'classified').rglob('*.html'))
            managed.update(LICENSES / 'sources' / f'{key}.txt' for key in read_json(ROOT / LICENSES / 'notice-snapshots.json'))
            for path in sorted(files - {MANIFEST}):
                target = ROOT / path
                if not target.exists() or target.read_bytes() != (stage / path).read_bytes():
                    target.parent.mkdir(parents=True, exist_ok=True)
                    (stage / path).replace(target)
            for path in sorted(managed - files):
                (ROOT / path).unlink(missing_ok=True)
            (stage / MANIFEST).replace(ROOT / MANIFEST)
        finally:
            lock.rmdir()


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Run the dictionary importer with verification enabled (without python -O)')
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--check', action='store_true', help='Verify the bundled release and notices offline')
    mode.add_argument('--release', metavar='TAG', help='Import a published dictionary release')
    mode.add_argument('--generate-license-assets', type=Path, metavar='DIR', help='Generate verified Android license assets offline')
    args = parser.parse_args()
    if args.check:
        check(ROOT)
    elif args.generate_license_assets:
        generate_license_assets(args.generate_license_assets)
    else:
        prepare(args.release)
