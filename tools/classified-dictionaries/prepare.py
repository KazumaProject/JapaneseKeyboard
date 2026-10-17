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
REVISION = 'd45a1aa16bf3f6fe43c3d4e62aa5100290c30746'
VERSION = 'v1.7.263-pre.5'
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
    manifest = json.loads(fetch(RELEASE + 'dictionary-distribution-manifest.json'))
    assert manifest['revision'] == REVISION
    assert manifest['archiveSha256'] == 'dd3b1359ea9454829de92976e6cfd43a21e8b122e02860ec0a4ec9abe8df3920'
    data = fetch(RELEASE + 'japanese_keyboard_dictionary_assets.zip')
    assert sha(data) == manifest['archiveSha256']
    zipped = zipfile.ZipFile(io.BytesIO(data))
    assert set(zipped.namelist()) == set(manifest['assets'])
    for name, checksum in manifest['assets'].items():
        path = Path(name)
        assert name.startswith('app/src/main/assets/') and not path.is_absolute() and '..' not in path.parts, name
        assert sha(zipped.read(name)) == checksum, name
    for name in manifest['assets']:
        write(ROOT / name, zipped.read(name))
    for directory in ('person_name', 'places', 'wiki', 'neologd', 'web'):
        path = ROOT / 'app/src/main/assets' / directory
        if path.exists():
            for child in path.iterdir():
                child.unlink()
            path.rmdir()
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
            for index, source in enumerate(SOURCES):
                if pairs[(reading, surface)] & (1 << index):
                    counts[category][source] += 1
    result = {'revision': REVISION, 'version': VERSION, 'inputSha256': manifest['inputs'],
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
    for line in gzip.decompress(specialist_provenance).decode().splitlines():
        row = json.loads(line)
        if row['status'] == 'adopted':
            original = original_sources[row['source']][row['sourceRow']]
            assert original['reading'] == row['originalReading']
            assert original['surface'] == row['originalSurface']
            assert (row['reading'], row['surface']) in admitted[row['category']]
            counts[row['category']][row['source']] += 1
    assert {k: sum(v.values()) for k, v in counts.items() if k in admitted} == specialist['categoryRows']
    # Record each reviewed person's source URL without copying the source page.
    person_additions = []
    for addition in manifest['provenance']['personAdditions']:
        frozen = fetch(RAW + addition['path'])
        assert sha(frozen) == addition['sha256']
        person_additions.extend(json.loads(line) for line in frozen.decode().splitlines())
    result['personAdditions'] = person_additions
    result['specialist'] = specialist
    result['categorySourcePairs'] = dict(sorted(counts.items()))
    fingerprints = {}
    surface_overrides = {}
    hira = set('あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんぁぃぅぇぉゃゅょゎっゔーがぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽ')
    kata = set('アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンァィゥェォャュョヮッヴーガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポ')

    for line in (gzip.decompress(records_data) + gzip.decompress(specialist_records)).decode().splitlines():
        category, reading, left, right, cost, surface = line.split('\t')
        normalized_surface = unicodedata.normalize('NFKC', surface)
        sentinel = -2 if surface == reading or normalized_surface and set(normalized_surface) <= hira else -1 if normalized_surface and set(normalized_surface) <= kata else None
        decoded = reading if sentinel == -2 else ''.join(chr(ord(c) + 96) if '\u3041' <= c <= '\u3093' or c == 'ゔ' else c for c in reading) if sentinel == -1 else surface
        if decoded != surface:
            key = '\t'.join((str(sentinel), reading, left, right, cost))
            surface_overrides.setdefault(category, {})[key] = surface
        fingerprint = fingerprints.setdefault(category, {'count': 0, 'sum': 0, 'sample': {'reading': reading, 'surface': surface}})
        record = '\t'.join((reading, left, right, cost, surface))
        fingerprint['sum'] += int(sha(record.encode()), 16)
        fingerprint['count'] += 1
        if 4 <= len(reading) <= 8 and not 4 <= len(fingerprint['sample']['reading']) <= 8:
            fingerprint['sample'] = {'reading': reading, 'surface': surface}
    assert sum(item['count'] for item in fingerprints.values()) == manifest['dictionaryRows'] + sum(specialist['categoryRows'].values())
    assert len(fingerprints) == 30
    expected = {category: {'count': item['count'], 'digestSum': format(item['sum'] % (1 << 256), '064x'), 'sample': item['sample']} for category, item in fingerprints.items()}
    write(ROOT / 'app/src/androidTest/assets/classified-expected-records.json', json.dumps(expected, ensure_ascii=False, indent=2) + '\n')
    assert not surface_overrides, surface_overrides
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
    distribution = json.loads(fetch(RELEASE + 'dictionary-distribution-manifest.json'))
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
    categories = json.loads(fetch(RAW + 'src/main/classification/categories.json'))['categories']
    style = '<style>body{font:16px sans-serif;padding:16px;overflow-wrap:anywhere}pre{white-space:pre-wrap;font:14px monospace}h2{margin-top:28px}</style>'
    def page(title, details, keys):
        body = '<!doctype html><meta charset="utf-8">' + style + '<title>' + html.escape(title) + '</title><h1>' + html.escape(title) + '</h1>' + details
        for key in keys:
            body += '<h2>' + html.escape(key) + '</h2><p><a href="' + html.escape(upstream[key]) + '">Source / 出典</a></p><pre>' + html.escape((assets/'sources'/(key+'.txt')).read_text()) + '</pre>'
        return body
    common = '<p>Distribution / 配布: ' + VERSION + ' (' + REVISION + ')</p>'
    common += '<p>Classified and converted to IME binaries / 分類・IME辞書バイナリへの変換を行っています。</p>'
    common += '<p>Input acquisition revision and per-entry copyright attribution: unconfirmed. 元辞書の取得版・各語の著作権者への対応: 未確認。Below are preserved upstream notices; source-code licenses do not replace data terms. 以下は保持した出典の通知です。プログラムのライセンスはデータの条件を置き換えません。</p>'
    for category in categories:
        ids = set(counts[category['id']])
        keys = ['mozc', 'merge-ut']
        keys += [source for source in SOURCES if source in ids and source != 'web']
        if 'web' in ids:
            keys += [key for key in ('wiki', 'neologd') if key not in keys]
        keys += ['cc-by-sa-4', 'apache-2']
        details = common + '<p>Frozen source inputs / 固定入力: ' + ', '.join(f'{html.escape(SOURCES[source][0])}: {counts[category["id"]][source]} pairs / 組' for source in SOURCES if source in ids) + '</p>'
        details += '<p>Wikipedia: attribution and ShareAlike / 表示・継承。Neologd: Apache 2.0 with its source-data notices / 出典データの通知を含むApache 2.0。Personal-names upstream notice: Apache 2.0. Japan Post place-data notice: no copyright claim for postcode data. 各入力と通知の取得版の同一性は未確認です。</p>'
        if category['id'] == 'person':
            additions = json.loads((ROOT/'tools/classified-dictionaries/category-provenance.json').read_text())['personAdditions']
            details += '<h2>Reviewed additions / 公式資料を照合した追加</h2><p>Names/readings selected, normalized, assigned POS/cost and encoded. 氏名・読みの選定、正規化、品詞・コスト割当とバイナリ化。Source page text is not redistributed; per-entry copyright terms remain unconfirmed / 原資料本文は転載していません。各語の著作権条件は未確認。</p>'
            details += '<ul>' + ''.join('<li>' + html.escape(row['reading'] + ' / ' + row['surface']) + ': <a href="' + html.escape(row['source_url']) + '">Source / 出典</a></li>' for row in additions) + '</ul>'
        write(assets/'classified'/(category['id']+'.html'), page(category['name']+' / '+category['id'], details, keys))
    source_metadata = json.loads(fetch(RAW + 'src/main/specialist/sources.json'))['sources']
    provenance = json.loads((ROOT/'tools/classified-dictionaries/category-provenance.json').read_text())
    for category in json.loads(fetch(RAW + 'src/main/specialist/categories.json'))['categories']:
        ident = category['id']
        sources = [source for source in source_metadata if counts[ident][source['id']]]
        details = '<p>Distribution / 配布: ' + VERSION + ' (' + REVISION + ')</p>'
        details += '<p>Changes / 加工: field extraction, normalization, duplicate filtering, POS/cost assignment and binary conversion / 項目抽出・正規化・重複除外・品詞とコスト割当・バイナリ化。</p>'
        if ident == 'medical':
            inferred = specialist['readingOrigins']['gemini_inferred_unverified']
            details += f'<p>{inferred:,} adopted readings were supplemented with Gemini. Automatic and inferred readings are unverified / 採用語の読み{inferred:,}件をGeminiで補完。自動読み・推定読みの正確性は未確認。</p>'
        else:
            details += '<p>Automatic readings are unverified / 自動読みの正確性は未確認。</p>'
        for source in sources:
            details += '<h2>' + html.escape(source['id']) + '</h2><p>' + html.escape(source['attribution']) + '</p><p>Version / 版: ' + html.escape(source['version']) + '</p><p>License / 条件: ' + html.escape(source['license']) + '</p><p><a href="' + html.escape(source['page']) + '">Source / 元データ</a></p><p>Input ZIP SHA-256: ' + source['archiveSha256'] + '</p><p>Adopted rows / 採用行: ' + str(counts[ident][source['id']]) + '</p>'
        keys = [source['id'] for source in sources] + (['cc-by-sa-2.1-jp'] if ident == 'kampo' else ['cc-by-4.0']) + ['release-data']
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
    assert sha((ROOT/'app/src/androidTest/assets/english-reading-expected.json').read_bytes()) == provenance_manifest['englishFixtureSha256']
    assert sha((ROOT/'app/src/androidTest/assets/classified-expected-records.json').read_bytes()) == provenance_manifest['classifiedFixtureSha256']
    assert sha((ROOT/'app/src/main/assets/licenses/sources/distribution-licenses.txt').read_bytes()) == m['licensesSha256']
    print(f"Verified {len(m['assets'])} distributed assets and all bundled notice hashes")

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check', action='store_true');args=parser.parse_args()
    if args.check:
        check()
    else:
        archive();notices(provenance());check()
