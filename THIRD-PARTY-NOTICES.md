# Dictionary data notices / 辞書データのライセンス

Repository documentation: [license information index / ライセンス情報一覧](docs/licenses/README.md),
[all dictionaries / 全辞書の対応表](docs/licenses/DICTIONARIES.md),
[resolved dependencies / 依存ライブラリ](docs/licenses/DEPENDENCIES.md).

The application's source code remains under the MIT license in `LICENSE`.
Dictionary data keeps its upstream terms; it is not uniformly MIT licensed.
アプリのコードはMITですが、辞書データには出典ごとの条件が適用されます。

## Classified dictionaries / 143カテゴリ辞書

Distribution: `v1.7.278`, converter revision
`39b3059dba81d6abb9fff3f57253c67e6e74c08e`.
The classified dictionaries are reclassified, filtered, and converted from the five
frozen inputs in that revision. Their exact input hashes and source-pair counts
are in `tools/classified-dictionaries/category-provenance.json`.
A source-derived sidecar restores three literal comedian spellings lost by the
upstream sentinel encoding (`くりき / くりきひなた`, `しゅうじ / シュージ`,
and `まつだ / ﾏﾂﾀﾞ`). Published
binary bytes, POS IDs and costs are retained.
The classified categories contain Wiki, Neologd, and their shared Web input. Some also
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

- **Kampo / 漢方・生薬** (434 records): 432 KNApSAcK KAMPO core and herb records,
  CC BY-SA 2.1 Japan. KNApSAcK Family © 金谷 重彦 (奈良先端科学技術大学院大学).
  The source release names the version LATEST; exact input ZIP hashes are pinned.
  The two additional Tsumura records are described separately below.
- **Medical / 医療** (92,683 records): NAIST Social Computing Lab's
  MANBYO_202106 and HYAKUYAKU_202107, CC BY 4.0. The official creator notices
  and legal text are bundled offline. 2,003 missing readings were supplied using
  Gemini; the accuracy of automatic and inferred readings is unconfirmed / 未確認.

Changes include field extraction, normalization, duplicate filtering, optional
reading inference, assigned POS/cost and IME binary conversion. Adopted records
are matched to the frozen source-row provenance before assigning source notices.
The notices preserve attribution, changes and Kampo's ShareAlike requirements.
The person dictionary also includes reviewed fighter names: the per-record
source URLs and input hashes are preserved, without redistributing source page
text. Per-entry copyright conditions for these names remain unconfirmed.

The full v1.7.278 `DICTIONARY-LICENSES.txt` is retained in
`app/src/main/assets/licenses/sources/distribution-licenses.txt`, byte-identical
and verified against the release asset SHA-256 `ec74cdd99e434b17081944759e07aef3b388dda6fad0d9c9616bc4dd3988ebf6`.

Two Kampo additions are backed by frozen Tsumura product fields. Source URLs and
evidence hashes are recorded; source page bodies are not redistributed. Their
per-entry copyright terms remain unconfirmed.

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

## Release software and native notices / リリース版のソフトウェア・ネイティブ通知

The in-app Licenses and sources screen uses the release dependency catalog in
`app/src/main/assets/software-licenses/catalog.json`. It distinguishes Full,
Lite Standard, and Lite F-Droid dependencies and preserves available notices
from their AAR/JAR distributions. Original notices and license texts are available
offline. POM declarations do not replace the individual third-party notices.

Zenz model metadata is pinned to
`Miwa-Keita/zenz-v3.2-xsmall-gguf@4f5423f0fad41a73b1242eb96fe5c12ae4fdca83`
(Apache 2.0 declaration); native llama.cpp is pinned to
`88b97a47dc7f5892e2d5a6856fbe9cfe237f9e5c` (MIT).
The OpenMP runtime notice is retained from Android NDK `29.0.14206865`
`NOTICE.toolchain`, including Apache 2.0 with LLVM Exceptions and its legacy
notices. See `docs/licenses/README.md` for the evidence and regeneration commands.
User-provided models retain their distributors’ terms.
