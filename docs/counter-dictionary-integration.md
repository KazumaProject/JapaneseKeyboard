# Counter and time dictionary integration / 助数詞・時刻辞書の統合

## Source / 出典

- Upstream PR: https://github.com/KazumaProject/kotlin-kana-kanji-converter/pull/121
- Pinned source: `333c338bbbcf9feada95796cf1e88e3aa6473bc4`
- Preview release: https://github.com/KazumaProject/kotlin-kana-kanji-converter/releases/tag/dictionary-preview-333c338bbbcf
- Asset extracted from `japanese_keyboard_dictionary_assets.zip`: `app/src/main/assets/counter/counter_rules.dat`
- Asset size: 41,327 bytes. SHA-256: `7decaa2574df37bd341898f08a655afed0a7987f956ce7fef3bfa5a42f732e08`.
- Upstream MIT license: `app/src/main/assets/licenses/counter-dictionary-MIT.txt`.

| Upstream runtime source | Original SHA-256 |
| --- | --- |
| `CounterModels.kt` | `3cd007ccf77fd30430fcb27f0d72194d3e2f8c3e31ba14ffa69e4f37f22d286b` |
| `CounterConverter.kt` | `997162087762dc556cd93a1000ecdfb8c1f5f85150ae889193bcd0b68dbf8115` |
| `CounterDictionary.kt` | `1d97d5b8ed984eb11491630597824fc5de2fa98523e1c7153deba6c8378e7dfb` |
| `CounterTrie.kt` | `f16b0461b8bd99308c1c12d592bee9083205f27613c568e4521a82869ac07a6e` |

`CounterModels`, `CounterDictionary`, and `CounterTrie` retain the pinned upstream implementation.
`CounterConverter` retains upstream exact parsing and adds bounded prefix lookup, length-preserving
normalization, and a start-character index for restored tails. `CounterNodePolicy` is application code.
No remote library or dictionary download is needed at application runtime.

`CounterModels`・`CounterDictionary`・`CounterTrie` は上流の固定コミットの実装です。
`CounterConverter` は完全一致の解析規則を保持し、文中の区間検索・長さを変えない正規化・
復元する数の末尾に対応した開始文字索引を追加しています。`CounterNodePolicy` は利用側の実装です。
実行中のネットワークアクセスはありません。

## Runtime / 実行時

`AppModule` loads the bundled asset and builds one immutable dictionary/converter for the singleton
`KanaKanjiEngine`. `GraphBuilder` calls `forEachPrefix(reading, startIndex, minimumEndExclusive)`
at each reading position, emits complete quantity/time nodes, and includes the converter identity
in its incremental cache signature. Appends emit only new end positions. Deletes/edits rebuild via
the existing session rules; cancellation follows existing transaction rollback.

The original input offsets and reading are retained even when katakana/fullwidth digits are
normalized for parsing. A quantity uses number-left/counter-right Mozc context IDs; time uses the
adverbial noun context. The IDs are documented in the bundled `id.def`.

Candidates preserve upstream ASCII → kanji → fullwidth ordering, aliases and 24-hour clock outputs.
A stronger nonnumeric whole-reading dictionary entry keeps ambiguous readings such as 日本/午後/産後/いっぱい ahead of their new numeric interpretations. Changes in that whole-reading preference invalidate the append cache. Sentence spans retain ordinary contextual scoring.
Exact matches also enter the final candidate list, so alternatives remain available beyond the
K-best graph cutoff. Whole-rule candidates carry one exact input/output segment and an empty
internal bunsetsu split pattern. Longer sentence paths use existing segment collection and projection.
English/kana mode uses the same exact converter rather than the legacy small counter table.

`AppModule` が辞書を一度読み込み、エンジンとグラフで同じ変換器を使います。
各開始位置から助数詞・時刻の読み全体を探索し、読みの元の区間を持つノードを生成します。
1文字追加では新しい終了位置のみを生成し、削除・途中編集・キャンセルは既存のセッション処理に従います。
カタカナ・全角数字を正規化しても、元の入力区間・読みは保持します。

単独の読みが日本・午後・産後・いっぱいなどの強い一般語と競合する場合は、既存辞書の一般語を優先します。
この優先度が入力追加で変わる場合はキャッシュを再構築し、連文節の区間は通常の文脈評価を使います。
半角数字・漢数字・全角数字・別表記・時計表記を候補に含めます。完全一致の候補は最終候補にも追加し、
グラフの候補数上限による別表記の欠落を防ぎます。数量・時刻の内部では文節を分割しません。
長い文章は既存の読み区間に基づく文節表示を利用します。英数かなモードも同じ辞書を使用します。

`FindPath` already adds 2,000 to outputs containing digits. Numeric rule nodes compensate for that
existing penalty; their notation cost step is 200. Kanji nodes keep their original rule cost.
Graph nodes have canonical ordering, and equal-cost backward queue entries compare their complete
path structurally instead of object identity. This keeps ties consistent between fresh and retained lattices.

既存の数字候補への2,000点の加点を規則辞書の数字ノードで補正します。表記ごとのコスト差は200点です。
同点候補はオブジェクトの識別値ではなく、変換経路の内容で比較します。

## Boundaries / 対応範囲

Each recognized reading is limited to 128 UTF-16 code units, matching upstream. Quantities are
nonnegative signed 64-bit integers. Time ranges and irregular readings follow the pinned dictionary.
Generic suffix grammar, decimals and negative-number parsing are not added. A sentence can still
convert individually valid spans adjacent to other text; that is not acceptance of the complete
reading by the exact rule parser. Homonyms retain multiple counter IDs (e.g. `さんばい`: 3倍/3杯).
The existing dictionary/context still chooses ordinary words such as 日本語 and homonyms such as 合う/会う.

認識する一つの読みは上流と同じ128 UTF-16文字までです。数量・時刻・音変化の範囲も上流に従います。
小数・負数・汎用的な接尾辞文法は追加しません。文中では有効な区間だけを変換する場合があり、
それは完全一致APIが入力全体を受理したことを意味しません。「さんばい」のような同音の助数詞は保持します。
日本語などの一般語、「合う／会う」の選択は既存辞書と文脈の候補も使用します。

## Verification / 検証

- `CounterDictionaryIntegrationTest`: pinned asset hash, 106 independent upstream golden cases,
  prefix matches/offsets, append frontier, CRC corruption, integer overflow and input length.
- `CounterEngineRegressionTest`: real dictionaries, sentence ranking, segment coverage, ordinary
  words, incremental append/delete/edit parity.
- `FindPathQueueOrderTest`: equal-cost ordering independent of allocation and insertion order.
- `CounterConversionInstrumentedTest`: all production modes, actual Android dictionaries,
  cancellation recovery, multiple counters, and bunsetsu display/notation selection projection.
- `CounterConversionPerformanceInstrumentedTest`: same fresh-process harness on base/final APKs.
- `CounterDictionaryPerformanceInstrumentedTest`: isolated parsing, golden cases and retained heap estimate.

Use `investigation/counter-probe.init.gradle` for a separate `.counterprobe` application and provider
names. Build Lite Debug + its AndroidTest APK, install them, then run
`python3 investigation/run-counter-device-probes.py --label integrated --output /tmp/counter-probes`.
Use `--rules --label rules` for isolated dictionary measurements. Full and Lite share the runtime asset/code.

詳細な測定結果・実行条件・再現手順は、この変更の実測報告を参照してください。
