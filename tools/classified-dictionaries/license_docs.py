"""Render the release-specific sections of the dictionary license reference."""
import json


DOC = 'docs/licenses/DICTIONARIES.md'
LICENSE_ASSETS = '../../app/src/main/assets/licenses'
PROVENANCE_LINK = '../../tools/classified-dictionaries/category-provenance.json'


def specialist_table(sources, counts, names):
    rows = ['| 入力 | 採用行数 | 取得版 | 出典 | 通知 |', '| --- | ---: | --- | --- | --- |']
    for source in sources:
        ident = source['id']
        if ident in names:
            label, page_label = names[ident]
            version = source['version'].replace('|', '\\|')
            rows.append(f'| {label} | {counts.get(ident, 0):,} | {version} | '
                        f'[{page_label}]({source["page"]}) | [{ident}.txt]({LICENSE_ASSETS}/sources/{ident}.txt) |')
    return '\n'.join(rows)


def update_license_doc(root, manifest, evidence, snapshots, reviewed, update):
    path = root / DOC
    original = path.read_text(encoding='utf-8')
    version, revision = manifest['version'], manifest['revision']
    categories = manifest['categories']
    total = sum(category['recordCount'] for category in categories)
    populated = sum(category['recordCount'] > 0 for category in categories)
    release_url = snapshots['distribution-licenses']['url'].rsplit('/', 1)[0].replace('/download/', '/tag/')
    raw = f'https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/{revision}/'
    counts = evidence['categorySourcePairs']
    english = manifest['englishDictionary']
    english_readings = json.loads((root / 'app/src/androidTest/assets/english-reading-expected.json').read_bytes())['count']
    mhlw = evidence['mhlwReview']

    category_table = ['| ID | 辞書名 | レコード数 | 出典・適用条件の参照 | アプリで表示する詳細（同梱HTML） |',
                      '| --- | --- | ---: | --- | --- |']
    for category in categories:
        ident = category['id']
        sources = counts.get(ident, {})
        if ident == 'kampo':
            source_links = ['[K](#k) + [追加出典](#漢方追加の出典)']
        elif ident == 'medical':
            source_links = ['[M](#m)']
        else:
            source_links = [link for keys, link in [(('wiki', 'web'), '[W](#w)'),
                            (('neologd', 'web'), '[N](#n)'), (('person',), '[P](#p)'),
                            (('place',), '[J](#j)'), (('mhlw',), '[L](#l)')]
                            if any(sources.get(key) for key in keys)]
            if ident.startswith('person/'):
                person_sources = '[人物追加](#人物追加の出典)'
                if source_links:
                    source_links[-1] += ' + ' + person_sources
                else:
                    source_links.append(person_sources)
        if ident in reviewed:
            source_links.append('[R](#r)')
        links = ' / '.join(source_links)
        if not links:
            links = '詳細を参照' if category['recordCount'] else '0件（配布カテゴリのみ）'
        label = category['label'].replace('|', '\\|')
        category_table.append(f'| `{ident}` | {label} | {category["recordCount"]:,} | {links} | '
                              f'[詳細]({LICENSE_ASSETS}/classified/{ident}.html) |')

    review_text = ''
    if review := evidence.get('releaseReview'):
        links = f'[追加語の確認記録]({raw}{review["reviewPath"]})'
        if followup := review.get('followupPath'):
            links += f'、[対象層分類・再分類の追記]({raw}{followup})'
        review_text = (
            f'<a id="r"></a>\n### R: {version}の追加語・対象層分類 / Reviewed additions and audience classifications\n\n'
            f'確認資料に基づく読み・表記の追加は{review["addedPairs"]:,}組、対象層分類の追加は{review.get("audienceAssignments", 0):,}件です。'
            f'確認記録は{links}、SHA-256は[来歴データ]({PROVENANCE_LINK})に掲載しています。'
            '該当カテゴリのアプリ内詳細に、最終的な収録先ごとの読み・表記と出典URLを掲載しています。各語の著作権条件は未確認です。'
        )

    notice_table = ['| 資料 | リポジトリ内原文 | 取得元 |', '| --- | --- | --- |']
    for key, snapshot in snapshots.items():
        notice_table.append(f'| `{key}` | [{key}.txt]({LICENSE_ASSETS}/sources/{key}.txt) | [固定URL]({snapshot["url"]}) |')

    blocks = {
        'release': (
            f'配布版は [`{version}`]({release_url})、配布コミットは `{revision}` です。'
            f'[配布マニフェスト](../../tools/classified-dictionaries/distribution-manifest.json) に英単語辞書を除く{len(manifest["assets"]):,}同梱ファイル、'
            f'{len(categories):,}カテゴリの{total:,}レコード、および英単語辞書の配布情報を記録しています。'
            f'語が収録されているカテゴリは{populated:,}件です。'
        ),
        'categories': '\n'.join(category_table),
        'kampo': specialist_table(evidence['specialistSources'], counts['kampo'], {
            'kampo_core': ('`kampo_core`', 'KNApSAcK KAMPO'), 'kampo_herb': ('`kampo_herb`', 'KNApSAcK KAMPO'),
        }),
        'medical': specialist_table(evidence['specialistSources'], counts['medical'], {
            'manbyo': ('万病辞書', '万病辞書'), 'hyakuyaku': ('百薬辞書', '百薬辞書'),
        }) + (
            f'\n\n[CC BY 4.0全文]({LICENSE_ASSETS}/sources/cc-by-4.0.txt)。'
            f'空欄{evidence["specialist"]["readingOrigins"]["gemini_inferred_unverified"]:,}件にはGeminiによる推定読みの補完があり、'
            '自動読み・推定読みの全件の正確性は未確認です。品詞・コストは配布元の割当です。'
        ),
        'mhlw': (
            f'採用件数は{mhlw["addedRecords"]:,}件です。上流の確認記録では{mhlw["reclassifiedRecords"]:,}件を再分類しています。'
            f'分類先とSHA-256は[来歴データ]({PROVENANCE_LINK})、出典は[固定した上流レビュー]({raw}{mhlw["path"]})に掲載しています。'
        ),
        'release-review': review_text,
        'english': (
            f'英語読み辞書の採用件数は{english_readings:,}組です。英単語辞書の配布版は `{version}`、'
            f'生成コミットは `{english["revision"]}` です。'
            f'[出典・語彙台帳]({english["catalogUrl"]})と[動詞訂正表の入力原本]({english["reviewedGrammarSourceUrl"]})を参照できます。'
        ),
        'notices': '\n'.join(notice_table) + '\n\n'
                   f'配布の全ライセンス文書 [DICTIONARY-LICENSES.txtの保持原文]({LICENSE_ASSETS}/sources/distribution-licenses.txt) '
                   f'は公開添付と同じバイト列です。SHA-256は `{manifest["licensesSha256"]}` です。',
    }
    rendered = original
    for key, content in blocks.items():
        begin = f'<!-- BEGIN GENERATED: {key} -->'
        end = f'<!-- END GENERATED: {key} -->'
        before, start, rest = rendered.partition(begin)
        _, finish, after = rest.partition(end)
        if not start or not finish or begin in after or end in after:
            raise ValueError(f'Missing or duplicate generated block {key} in {DOC}')
        rendered = before + begin + '\n\n' + content + '\n\n' + end + after
    if update:
        path.write_text(rendered, encoding='utf-8')
    elif rendered != original:
        raise ValueError(f'{DOC} is out of date. Run prepare.py to regenerate the license documentation.')
