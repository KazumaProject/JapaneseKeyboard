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
from pathlib import Path
import urllib.request
import zipfile
import unicodedata

ROOT = Path(__file__).resolve().parents[2]
REVISION = '574d450c652e42e81713cc1c926a57c3f5310320'
VERSION = 'v1.7.262-pre.1'
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
    assert manifest['archiveSha256'] == '1f5248c48689be664e78aad02ad316f44810a1bd4ccb16b54ef087177eaa2d05'
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
    fingerprints = {}
    surface_overrides = {}
    hira = set('あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんぁぃぅぇぉゃゅょゎっゔーがぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽ')
    kata = set('アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンァィゥェォャュョヮッヴーガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポ')

    for line in gzip.decompress(records_data).decode().splitlines():
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
    expected = {category: {'count': item['count'], 'digestSum': format(item['sum'] % (1 << 256), '064x'), 'sample': item['sample']} for category, item in fingerprints.items()}
    write(ROOT / 'app/src/androidTest/assets/classified-expected-records.json', json.dumps(expected, ensure_ascii=False, indent=2) + '\n')
    assert sum(map(len, surface_overrides.values())) == 1, surface_overrides
    write(ROOT / 'app/src/main/assets/categories/surface-overrides.json', json.dumps(surface_overrides, ensure_ascii=False, indent=2) + '\n')
    result['surfaceOverridesSha256'] = sha((ROOT / 'app/src/main/assets/categories/surface-overrides.json').read_bytes())
    write(ROOT / 'tools/classified-dictionaries/category-provenance.json', json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    return counts

def notices(counts):
    assets = ROOT / 'app/src/main/assets/licenses'
    upstream = {
        'mozc': RAW + 'src/main/resources/third-party/MOZC-LICENSE',
        'ipadic': RAW + 'src/main/resources/third-party/IPADIC-NOTICE',
        'jmdict': RAW + 'src/main/resources/third-party/JMDICT-LICENSE.html',
        'data': RAW + 'src/main/resources/third-party/LICENSE-DATA.md',
        'corpus': RAW + 'src/main/resources/third-party/NOTICE.md',
        'converter': RAW + 'src/main/resources/THIRD-PARTY-NOTICES.md',
        'cc-by-sa-4': 'https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt',
        'apache-2': 'https://www.apache.org/licenses/LICENSE-2.0.txt',
    }
    # Snapshot notices independently. Input acquisition revisions are not recoverable from the release.
    for key, repo, path, ref in [
        ('person', 'utuhiro78/mozcdic-ut-personal-names', 'LICENSE', 'main'),
        ('place', 'utuhiro78/mozcdic-ut-place-names', 'LICENSE', 'main'),
        ('wiki', 'utuhiro78/mozcdic-ut-jawiki', 'LICENSE', 'main'),
        ('neologd', 'neologd/mecab-ipadic-neologd', 'COPYING', 'master'),
        ('merge-ut', 'utuhiro78/merge-ut-dictionaries', 'LICENSE', 'main'),
    ]:
        commit = json.loads(fetch(f'https://api.github.com/repos/{repo}/commits/{ref}'))['sha']
        upstream[key] = f'https://raw.githubusercontent.com/{repo}/{commit}/{path}'
    snapshot_file = assets / 'notice-snapshots.json'
    if snapshot_file.exists():
        upstream = {key: value['url'] for key, value in json.loads(snapshot_file.read_text()).items()}
    snapshots = {}
    existing_snapshots = json.loads(snapshot_file.read_text()) if snapshot_file.exists() else {}
    for key, url in upstream.items():
        data = fetch(url)
        if key in existing_snapshots:
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
        if category['id'] == 'place':
            details += '<p>One literal spelling is restored from the frozen source / 固定入力から1件の表記を復元: あとこしゃらか → アトコシヤラカ。</p>'
        details += '<p>Wikipedia: attribution and ShareAlike / 表示・継承。Neologd: Apache 2.0 with its source-data notices / 出典データの通知を含むApache 2.0。Personal-names upstream notice: Apache 2.0. Japan Post place-data notice: no copyright claim for postcode data. 各入力と通知の取得版の同一性は未確認です。</p>'
        write(assets/'classified'/(category['id']+'.html'), page(category['name']+' / '+category['id'], details, keys))
    for ident, title, keys in [
        ('wiki', 'Japanese Wikipedia-derived data / 日本語Wikipedia由来の辞書データ', ['wiki','cc-by-sa-4']),
        ('core', 'Mozc dictionaries / Mozc基本・単漢字・絵文字・顔文字・記号辞書', ['mozc','ipadic']),
        ('english-reading', 'English reading dictionary / 英語読み辞書', ['converter','jmdict','cc-by-sa-4']),
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
    assert sha((ROOT / 'app/src/main/assets/categories/surface-overrides.json').read_bytes()) == provenance_manifest['surfaceOverridesSha256']
    print('Verified 117 distributed assets and all bundled notice hashes')

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check', action='store_true');args=parser.parse_args()
    if args.check:
        check()
    else:
        archive();notices(provenance());check()
