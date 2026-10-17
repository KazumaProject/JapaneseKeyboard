# Dictionary data notices / 辞書データのライセンス

Repository documentation: [license information index / ライセンス情報一覧](docs/licenses/README.md),
[all dictionaries / 全辞書の対応表](docs/licenses/DICTIONARIES.md),
[resolved dependencies / 依存ライブラリ](docs/licenses/DEPENDENCIES.md).

The application's source code remains under the MIT license in `LICENSE`.
Dictionary data keeps its upstream terms; it is not uniformly MIT licensed.
アプリのコードはMITですが、辞書データには出典ごとの条件が適用されます。

## Classified dictionaries / 30カテゴリ辞書

Distribution: `v1.7.262-pre.3`, converter revision
`d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5`.
The original 28 dictionaries are reclassified, filtered, and converted from the five
frozen inputs in that revision. Their exact input hashes and source-pair counts
are in `tools/classified-dictionaries/category-provenance.json`.
A source-derived sidecar restores one literal Katakana spelling lost by the
upstream sentinel encoding (`あとこしゃらか / アトコシヤラカ`). Published
binary bytes, POS IDs and costs are retained.
The original 28 categories contain Wiki, Neologd, and their shared Web input. Some also
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

## Specialist dictionaries / 漢方・医療辞書

- **Kampo / 漢方・生薬** (440 records): KNApSAcK KAMPO core and herbs,
  CC BY-SA 2.1 Japan. KNApSAcK Family © 金谷 重彦 (奈良先端科学技術大学院大学).
  The source release names the version LATEST; exact input ZIP hashes are pinned.
- **Medical / 医療** (92,847 records): NAIST Social Computing Lab's
  MANBYO_202106 and HYAKUYAKU_202107, CC BY 4.0. The official creator notices
  and legal text are bundled offline. 2,006 missing readings were supplied using
  Gemini; the accuracy of automatic and inferred readings is unconfirmed / 未確認.

Changes include field extraction, normalization, duplicate filtering, optional
reading inference, assigned POS/cost and IME binary conversion. Adopted records
are matched to the frozen source-row provenance before assigning source notices.
The notices preserve attribution, changes and Kampo's ShareAlike requirements.
The person dictionary also includes reviewed fighter names: the per-record
source URLs and input hashes are preserved, without redistributing source page
text. Per-entry copyright conditions for these names remain unconfirmed.

The full pre.3 `DICTIONARY-LICENSES.txt` is retained in
`app/src/main/assets/licenses/sources/distribution-licenses.txt`, byte-identical
and verified against the release manifest's `licensesSha256`.

## Other bundled dictionaries / その他の同梱辞書

Mozc resources retain the mixed Google/Mozc, NAIST/IPADIC, ICOT and Okinawa
notices. JMdict-derived English-reading data retains CC BY-SA 4.0 and EDRDG
attribution. The updated English-reading dictionary also retains NGSL 1.2
selection attribution and the Carnegie Mellon University CMUdict copyright,
redistribution conditions and disclaimer. Project editorial additions are
CC BY-SA 4.0; CMUdict terms still apply to its portion. N-gram/unigram data retains the JapaneseCorpus notices, including
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
