# Dictionary data notices / 辞書データのライセンス

The application's source code remains under the MIT license in `LICENSE`.
Dictionary data keeps its upstream terms; it is not uniformly MIT licensed.
アプリのコードはMITですが、辞書データには出典ごとの条件が適用されます。

## Classified dictionaries / 28カテゴリ辞書

Distribution: `v1.7.262-pre.1`, converter revision
`574d450c652e42e81713cc1c926a57c3f5310320`.
The 28 dictionaries are reclassified, filtered, and converted from the five
frozen inputs in that revision. Their exact input hashes and source-pair counts
are in `tools/classified-dictionaries/category-provenance.json`.
A source-derived sidecar restores one literal Katakana spelling lost by the
upstream sentinel encoding (`あとこしゃらか / アトコシヤラカ`). Published
binary bytes, POS IDs and costs are retained.
All categories contain Wiki, Neologd, and their shared Web input. Some also
contain personal-name or place-name input. These are source-input memberships,
not a claim of sole authorship or an exhaustive per-record legal determination.

- Wikipedia-derived material: applicable attribution and ShareAlike terms.
- Neologd: Apache 2.0 and the additional source-data acknowledgements in COPYING.
- Personal-name upstream notice: Apache 2.0.
- Place-name upstream notice: Japan Post does not claim copyright over postcode data.
- Mozc context IDs and resources: the respective Mozc/dictionary notices.
- merge-ut source-code terms do not replace the combined dictionary-data terms.

The acquisition revisions of the five original inputs and per-entry copyright
attribution are **unconfirmed / 未確認**. Preserved upstream notices are separately
pinned and hashed in `app/src/main/assets/licenses/notice-snapshots.json`; they
must not be mistaken for proof of the original input acquisition revisions.

## Other bundled dictionaries / その他の同梱辞書

Mozc resources retain the mixed Google/Mozc, NAIST/IPADIC, ICOT and Okinawa
notices. JMdict-derived English-reading data retains CC BY-SA 4.0 and EDRDG
attribution. N-gram/unigram data retains the JapaneseCorpus notices, including
Wikipedia and Aozora source information. The exact upstream attribution of the
standalone alphabetic English-word asset is unconfirmed.

The Open source licenses screen includes individual category entries and
bundled offline source notices under `app/src/main/assets/licenses/`.
Read the preserved notices for the full terms. Redistribution must retain
applicable copyright, attribution, license and source-data notices.

## Reproduction / 再生成

`python3 tools/classified-dictionaries/prepare.py` imports the pinned release,
verifies archive and asset hashes, derives source-input memberships from the
hash-verified frozen records, and snapshots upstream notices.
`python3 tools/classified-dictionaries/prepare.py --check` verifies the bundled
release assets and notice hashes without network access.
