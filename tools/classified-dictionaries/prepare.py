#!/usr/bin/env python3
"""Import the pinned release and derive category provenance from hash-verified frozen inputs."""
import argparse
import collections
import concurrent.futures
import gzip
import hashlib
import html
import io
import json
import re
from pathlib import Path
import urllib.request
import zipfile
import unicodedata

ROOT = Path(__file__).resolve().parents[2]
REVISION = '13e391bc7a4a245f63aa0091c1e9c11db3dbd759'
VERSION = 'v1.7.277'
RAW = f'https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/{REVISION}/'
RELEASE = f'https://github.com/KazumaProject/kotlin-kana-kanji-converter/releases/download/{VERSION}/'
SOURCES = {
    'person': ('src/main/bin/names.txt', None),
    'place': ('src/main/bin/place.txt.zip', 'place.txt'),
    'wiki': ('src/main/bin/only_wiki.txt.zip', 'only_wiki.txt'),
    'neologd': ('src/main/bin/only_neologd.txt.zip', 'only_neologd.txt'),
    'web': ('src/main/bin/wiki_neologd_common.txt.zip', 'wiki_neologd_common.txt'),
}
CACHE = ROOT / '.cache/classified-distribution'

def fetch(url):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / hashlib.sha256(url.encode()).hexdigest()
    if not path.exists():
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=120) as response:
            path.write_bytes(response.read())
    return path.read_bytes()

def sha(data):
    return hashlib.sha256(data).hexdigest()

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data if isinstance(data, bytes) else data.encode())

def archive():
    manifest_data = fetch(RELEASE + 'dictionary-distribution-manifest.json')
    manifest = json.loads(manifest_data)
    archive_sha = 'f2b1c1521a2dacd8fdfdf40a06cf9b7566db60a2592c14be78ea9ce7d5e26d63'
    assert fetch(RELEASE + 'DICTIONARY-SHA256SUMS').decode().split() == [archive_sha, 'japanese_keyboard_dictionary_assets.zip']
    licenses_sha = '1002230bbdf760c6a00a81b6dd19d93299b9bba3a3e31815d2eff16bd1bd2ff2'
    assert sha(fetch(RELEASE + 'DICTIONARY-LICENSES.txt')) == licenses_sha
    data = fetch(RELEASE + 'japanese_keyboard_dictionary_assets.zip')
    assert sha(data) == archive_sha
    zipped = zipfile.ZipFile(io.BytesIO(data))
    assert len(zipped.namelist()) == len(set(zipped.namelist()))
    assert set(zipped.namelist()) == set(manifest['files']) | {'dictionary-distribution-manifest.json'}
    assert zipped.read('dictionary-distribution-manifest.json') == manifest_data
    for name, checksum in manifest['files'].items():
        path = Path(name)
        assert name.startswith('app/src/main/assets/') and not path.is_absolute() and '..' not in path.parts, name
        assert sha(zipped.read(name)) == checksum, name
    old = json.loads((ROOT / 'tools/classified-dictionaries/distribution-manifest.json').read_text())
    for name in set(old['assets']) - set(manifest['files']):
        path = Path(name)
        assert name.startswith('app/src/main/assets/') and '..' not in path.parts, name
        (ROOT / path).unlink(missing_ok=True)
    for name in manifest['files']:
        write(ROOT / name, zipped.read(name))
    # Normalize the release's person IDs to full asset paths for every consumer.
    for category in manifest['categories']:
        category['id'] = str(Path(category['assetPaths'][0]).parent.relative_to('app/src/main/assets/categories'))
    manifest['personCategoryFamily']['categoryIds'] = [
        'person/' + ident for ident in manifest['personCategoryFamily']['categoryIds']
    ]
    manifest.update(revision=REVISION, version=VERSION, archiveSha256=archive_sha,
                    upstreamManifestSha256=sha(manifest_data),
                    licensesSha256=licenses_sha,
                    assets=manifest.pop('files'))
    write(ROOT / 'tools/classified-dictionaries/distribution-manifest.json', json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')

def provenance():
    manifest = json.loads(fetch(RAW + 'src/main/classification/distribution-manifest.json'))
    pairs = collections.defaultdict(int)
    for index, (source, (path, member)) in enumerate(SOURCES.items()):
        data = fetch(RAW + path)
        assert sha(data) == manifest['inputs'][path]
        text = zipfile.ZipFile(io.BytesIO(data)).read(member).decode() if member else data.decode()
        for line in text.splitlines():
            row = line.split('\t')
            pairs[(row[0], row[4])] |= 1 << index
    path = 'src/main/classification/distribution-decisions.tsv.gz'
    data = fetch(RAW + path)
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
    person_data = fetch(RAW + 'src/main/classification/person-dictionary-candidates.tsv.gz')
    distribution = json.loads((ROOT / 'tools/classified-dictionaries/distribution-manifest.json').read_text())
    assert sha(person_data) == distribution['personSourceSha256']
    person_records = []
    for line in gzip.decompress(person_data).decode().splitlines():
        category, reading, left, right, cost, surface, *tags = line.split('\t')
        category = 'person/' + category
        person_records.append('\t'.join((category, reading, left, right, cost, surface)))
        for index, source in enumerate(SOURCES):
            if pairs[(reading, surface)] & (1 << index):
                counts[category][source] += 1
    result = {'personSourceSha256': sha(person_data), 'revision': REVISION, 'version': VERSION, 'inputSha256': manifest['inputs'],
              'decisionSha256': sha(data), 'categorySourcePairs': dict(sorted(counts.items()))}
    write(ROOT / 'tools/classified-dictionaries/category-provenance.json', json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    records_path = 'src/main/classification/distribution-candidates.tsv.gz'
    records_data = fetch(RAW + records_path)
    assert sha(records_data) == manifest['files'][Path(records_path).name]
    specialist = json.loads(fetch(RAW + 'src/main/specialist/distribution-manifest.json'))
    specialist_records = fetch(RAW + 'src/main/specialist/distribution-candidates.tsv.gz')
    assert sha(specialist_records) == specialist['files']['distribution-candidates.tsv.gz']
    specialist_provenance = fetch(RAW + 'src/main/specialist/provenance.jsonl.gz')
    assert sha(specialist_provenance) == specialist['files']['provenance.jsonl.gz']
    admitted = collections.defaultdict(set)
    for line in gzip.decompress(specialist_records).decode().splitlines():
        category, reading, left, right, cost, surface = line.split('\t')
        admitted[category].add((reading, surface))
    original_sources = {}
    for source in ('kampo_core', 'kampo_herb', 'manbyo', 'hyakuyaku'):
        path = f'src/main/specialist/sources/{source}.jsonl.gz'
        frozen = fetch(RAW + path)
        assert sha(frozen) == specialist['inputs'][path]
        original_sources[source] = {row['row']: row for row in
            (json.loads(line) for line in gzip.decompress(frozen).decode().splitlines())}
    collected_terms = []
    for path, checksum in specialist['inputs'].items():
        if '/collected-terms/' not in path:
            continue
        frozen = fetch(RAW + path)
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
    mhlw_data = fetch(RAW + mhlw_path)
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
        frozen = fetch(RAW + addition['path'])
        assert sha(frozen) == addition['sha256']
        person_additions.extend(json.loads(line) for line in frozen.decode().splitlines())
    result['personAdditions'] = person_additions
    result['specialist'] = specialist
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
    assert {k: v['count'] for k, v in fingerprints.items()} == {c['id']: c['recordCount'] for c in distribution['categories']}
    expected = {category: {'count': item['count'], 'digestSum': format(item['sum'] % (1 << 256), '064x'), 'sample': item['sample']} for category, item in fingerprints.items()}
    write(ROOT / 'app/src/androidTest/assets/classified-expected-records.json', json.dumps(expected, ensure_ascii=False, indent=2) + '\n')
    repairs = {}
    for category, surfaces in surface_overrides.items():
        assert all(len(sentinel_surfaces[(category, key)]) == 1 for key in surfaces), category
        paths = next(c['assetPaths'] for c in distribution['categories'] if c['id'] == category)
        repairs[category] = {'assets': {
            path.removeprefix('app/src/main/assets/'): distribution['assets'][path] for path in paths
        }, 'surfaces': surfaces}
    surface_asset = ROOT / 'app/src/main/assets/categories/surface-overrides.json'
    write(surface_asset, json.dumps({'revision': REVISION, 'categories': repairs}, ensure_ascii=False, indent=2) + '\n')
    result['surfaceOverridesSha256'] = sha(surface_asset.read_bytes())
    catalog = fetch(RAW + 'docs/english-dictionary-catalog.md')
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
    assert len(english_rows) == 5138
    english_fingerprint = sum(int(sha(row.encode()), 16) for row in english_rows) % (1 << 256)
    write(ROOT/'app/src/androidTest/assets/english-reading-expected.json', json.dumps({
        'revision': REVISION, 'catalogSha256': sha(catalog), 'count': len(english_rows),
        'digestSum': format(english_fingerprint, '064x'),
    }, indent=2) + '\n')
    result['englishCatalogSha256'] = sha(catalog)
    result['englishFixtureSha256'] = sha((ROOT/'app/src/androidTest/assets/english-reading-expected.json').read_bytes())
    result['classifiedFixtureSha256'] = sha((ROOT/'app/src/androidTest/assets/classified-expected-records.json').read_bytes())
    write(ROOT / 'tools/classified-dictionaries/category-provenance.json', json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    return counts

def notices(counts):
    assets = ROOT / 'app/src/main/assets/licenses'
    upstream = {
        'mozc': RAW + 'src/main/resources/third-party/MOZC-LICENSE',
        'cmudict': RAW + 'src/main/resources/third-party/CMUDICT-LICENSE',
        'ipadic-copying': RAW + 'src/main/resources/third-party/IPADIC-COPYING',
        'release-data': RAW + 'LICENSE-DATA.md',
        'ipadic': RAW + 'src/main/resources/third-party/IPADIC-NOTICE',
        'jmdict': RAW + 'src/main/resources/third-party/JMDICT-LICENSE.html',
        'data': RAW + 'src/main/resources/third-party/LICENSE-DATA.md',
        'corpus': RAW + 'src/main/resources/third-party/NOTICE.md',
        'converter': RAW + 'src/main/resources/THIRD-PARTY-NOTICES.md',
        'cc-by-sa-4': 'https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt',
        'apache-2': 'https://www.apache.org/licenses/LICENSE-2.0.txt',
    }
    # Preserve already pinned external notices; refresh release-owned notices.
    snapshot_file = assets / 'notice-snapshots.json'
    old_snapshots = json.loads(snapshot_file.read_text()) if snapshot_file.exists() else {}
    for key in ('person', 'place', 'wiki', 'neologd', 'merge-ut'):
        upstream[key] = old_snapshots[key]['url']
    for key in ('cc-by-sa-4', 'apache-2'):
        if key in old_snapshots:
            upstream[key] = old_snapshots[key]['url']
    specialist = json.loads(fetch(RAW + 'src/main/specialist/distribution-manifest.json'))
    for path, checksum in specialist['inputs'].items():
        if '/notices/' in path:
            key = Path(path).stem.lower()
            upstream[key] = RAW + path
            assert sha(fetch(upstream[key])) == checksum, key
    distribution = json.loads((ROOT / 'tools/classified-dictionaries/distribution-manifest.json').read_text())
    upstream['distribution-licenses'] = RELEASE + 'DICTIONARY-LICENSES.txt'
    assert sha(fetch(upstream['distribution-licenses'])) == distribution['licensesSha256']
    snapshots = {}
    existing_snapshots = json.loads(snapshot_file.read_text()) if snapshot_file.exists() else {}
    for key, url in upstream.items():
        data = fetch(url)
        if key in existing_snapshots and url == existing_snapshots[key]['url']:
            assert sha(data) == existing_snapshots[key]['sha256'], key
        write(assets / 'sources' / (key + '.txt'), data)
        snapshots[key] = {'url': url, 'sha256': sha(data)}
    write(assets / 'notice-snapshots.json', json.dumps(snapshots, indent=2) + '\n')
    categories = json.loads((ROOT / 'tools/classified-dictionaries/distribution-manifest.json').read_text())['categories']
    categories = [dict(c, name=c['label']) for c in categories if c['id'] not in ('kampo', 'medical')]
    provenance = json.loads((ROOT / 'tools/classified-dictionaries/category-provenance.json').read_text())
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
            body += '<h2>' + html.escape(key) + '</h2><p><a href="' + html.escape(upstream[key]) + '">Source / 出典</a></p><pre>' + html.escape((assets/'sources'/(key+'.txt')).read_text()) + '</pre>'
        return body
    common = heading('概要', 'Overview') + '<p>' + translated('本アプリに同梱する辞書データです。出典ごとの利用条件と通知原文を以下に掲載しています。', 'Dictionary data bundled with this app. Source-specific terms and original notices are provided below.') + '</p>'
    common += heading('出典', 'Source') + '<p><a href="https://github.com/KazumaProject/kotlin-kana-kanji-converter/releases/tag/' + VERSION + '">' + translated('辞書データの配布元', 'Dictionary distribution') + '</a></p>'
    common += heading('対象版', 'Version') + '<p>Distribution / 配布: ' + VERSION + ' (' + REVISION + ')</p>'
    common += heading('加工内容', 'Modifications') + '<p>' + translated('分類・正規化・重複処理・品詞とコストの割当を行い、IME 用の辞書バイナリに変換しています。', 'Categorized, normalized, deduplicated, assigned parts of speech and costs, and converted to IME dictionary binaries.') + '</p>'
    common += heading('利用条件', 'License terms') + '<p>' + translated('各出典には、それぞれの利用条件が適用されます。元データの取得版および各語の著作権者との対応は、配布元の資料からは確認できていません。', 'Each source retains its respective terms. The original acquisition revision and per-entry copyright attribution could not be confirmed from the distributor’s materials.') + '</p>'
    person_pairs = collections.defaultdict(set)
    for line in gzip.decompress(fetch(RAW + 'src/main/classification/person-dictionary-candidates.tsv.gz')).decode().splitlines():
        category, reading, left, right, cost, surface, *tags = line.split('\t')
        person_pairs['person/' + category].add((reading, surface))
    for retired in ('person', 'event', 'term', 'facility'):
        (assets / 'classified' / (retired + '.html')).unlink(missing_ok=True)
    for category in categories:
        ids = set(counts[category['id']])
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
        if 'mhlw' in ids:
            additions = mhlw_review['categoryCounts'][category['id']]
            details += '<h2>MHLW employment terms / 厚生労働省の人事・労務用語</h2>'
            details += '<p>Source / 出典: <a href="' + html.escape(mhlw_review['sourceUrl']) + '">MHLW Multilingual Glossary for Employment Management / 雇用管理に役立つ多言語用語集</a>. License / 条件: <a href="' + html.escape(mhlw_review['licenseUrl']) + '">Public Data License (Version 1.0) / 公共データ利用規約（第1.0版） (PDL1.0)</a>.</p>'
            details += '<p>' + str(additions) + ' terms were selected from the source, including explicitly provided Japanese readings, then categorized and converted to IME dictionary data. Definitions, translations and examples were not copied. / 出典の用語と明記された日本語読みを選定し、分類してIME辞書データに変換しています。定義・翻訳・例文は転載していません。</p>'
            details += '<p>Source terms and processing notice / 出典と加工内容: <a href="' + html.escape(mhlw_review['termsUrl']) + '">MHLW usage terms / 厚生労働省の利用規約</a>. Upstream reviewed additions and reclassifications / 上流の確認記録: <a href="' + html.escape(RAW + mhlw_review['path']) + '">' + html.escape(mhlw_review['path']) + '</a> (SHA-256: ' + mhlw_review['sha256'] + ').</p>'
        if category['id'].startswith('person/'):
            additions = json.loads((ROOT/'tools/classified-dictionaries/category-provenance.json').read_text())['personAdditions']
            additions = [row for row in additions if (row['reading'], row['surface']) in person_pairs[category['id']]]
            details += '<h2>Reviewed additions / 公式資料を照合した追加</h2><p>Names/readings selected, normalized, assigned POS/cost and encoded. 氏名・読みの選定、正規化、品詞・コスト割当とバイナリ化。Source page text is not redistributed; per-entry copyright terms remain unconfirmed / 原資料本文は転載していません。各語の著作権条件は未確認。</p>'
            details += '<ul>' + ''.join('<li>' + html.escape(row['reading'] + ' / ' + row['surface']) + ': <a href="' + html.escape(row['source_url']) + '">Source / 出典</a></li>' for row in additions) + '</ul>'
        write(assets/'classified'/(category['id']+'.html'), page(category['name']+' / '+category['id'], details, keys))
    source_metadata = json.loads(fetch(RAW + 'src/main/specialist/sources.json'))['sources']
    provenance = json.loads((ROOT/'tools/classified-dictionaries/category-provenance.json').read_text())
    for category in json.loads(fetch(RAW + 'src/main/specialist/categories.json'))['categories']:
        ident = category['id']
        sources = [source for source in source_metadata if counts[ident][source['id']]]
        details = common
        if ident == 'medical':
            inferred = specialist['readingOrigins']['gemini_inferred_unverified']
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
        write(assets/'classified'/(ident+'.html'), page(category['name']+' / '+ident, details, keys))
    for ident, title, keys in [
        ('wiki', 'Japanese Wikipedia-derived data / 日本語Wikipedia由来の辞書データ', ['wiki','cc-by-sa-4']),
        ('core', 'Mozc dictionaries / Mozc基本・単漢字・絵文字・顔文字・記号辞書', ['mozc','ipadic','ipadic-copying']),
        ('english-reading', 'English reading dictionary / 英語読み辞書', ['converter','release-data','jmdict','cmudict','cc-by-sa-4']),
        ('ngram', 'N-gram / Unigram dictionaries', ['data','corpus','mozc','ipadic','cc-by-sa-4']),
        ('auxiliary', 'Reading correction / Proverbs / 読み修正・ことわざ辞書', ['converter','mozc']),
        ('english', 'English word dictionary / 英単語辞書', ['converter']),
    ]:
        write(assets/(ident+'.html'), page(title, common + ('<p>Source and applicable data license: unconfirmed / 元データ・適用されるデータライセンス: 未確認。This asset is not part of the pinned dictionary release / この資源は当該配布ZIPには含まれません。</p>' if ident == 'english' else ''), keys))

def check():
    m=json.loads((ROOT/'tools/classified-dictionaries/distribution-manifest.json').read_text())
    for path, checksum in m['assets'].items():
        assert sha((ROOT/path).read_bytes()) == checksum, path
    for key, value in json.loads((ROOT/'app/src/main/assets/licenses/notice-snapshots.json').read_text()).items():
        assert sha((ROOT/'app/src/main/assets/licenses/sources'/(key+'.txt')).read_bytes()) == value['sha256'], key
    provenance_manifest = json.loads((ROOT / 'tools/classified-dictionaries/category-provenance.json').read_text())
    mhlw_review = provenance_manifest['mhlwReview']
    assert mhlw_review['sha256'] == provenance_manifest['inputSha256'][mhlw_review['path']]
    assert sum(mhlw_review['categoryCounts'].values()) == mhlw_review['addedRecords']
    for metadata in (m, provenance_manifest):
        assert metadata['version'] == VERSION, 'Dictionary version does not match the importer'
        assert metadata['revision'] == REVISION, 'Dictionary revision does not match the importer'
    for path in (ROOT / 'app/src/main/assets/licenses').rglob('*.html'):
        text = path.read_text()
        assert f'配布: {VERSION} ({REVISION})' in text, f'Stale dictionary notice: {path}'
    for key, snapshot in json.loads((ROOT/'app/src/main/assets/licenses/notice-snapshots.json').read_text()).items():
        if 'KazumaProject/kotlin-kana-kanji-converter' in snapshot['url']:
            assert REVISION in snapshot['url'] or f'/{VERSION}/' in snapshot['url'], key
    for category in mhlw_review['categoryCounts']:
        page = ROOT / 'app/src/main/assets/licenses/classified' / (category + '.html')
        text = page.read_text()
        assert mhlw_review['sourceUrl'] in text
        assert mhlw_review['licenseUrl'] in text

    assert sha((ROOT/'app/src/androidTest/assets/english-reading-expected.json').read_bytes()) == provenance_manifest['englishFixtureSha256']
    assert sha((ROOT/'app/src/androidTest/assets/classified-expected-records.json').read_bytes()) == provenance_manifest['classifiedFixtureSha256']
    assert sha((ROOT/'app/src/main/assets/licenses/sources/distribution-licenses.txt').read_bytes()) == m['licensesSha256']
    assert sha((ROOT/'app/src/main/assets/categories/surface-overrides.json').read_bytes()) == provenance_manifest['surfaceOverridesSha256']
    print(f"Verified {len(m['assets'])} distributed assets and all bundled notice hashes")

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check', action='store_true');args=parser.parse_args()
    if args.check:
        check()
    else:
        archive();notices(provenance());check()
