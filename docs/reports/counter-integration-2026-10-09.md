# 助数詞・時刻辞書の統合：実装・実機計測 / Counter/time integration: implementation and device measurements

## 日本語

最新の `origin/dev` を取得した `9d1fe55da944ce50b71e14f218207a29ac3e2009` を起点に、専用worktreeと `codex/counter-time-dictionary` ブランチで実装しました。
上流PR #121の固定コミット `333c338bbbcf9feada95796cf1e88e3aa6473bc4` の実行コードと配布辞書を使用します。
実測対象の実装コミットは `1f6178206` です。報告・生データの追加は実装を変更しません。

### 変更と正しさ

- 数量・時刻を完全な読み区間を持つノードとしてグラフに追加し、通常・予測・予測なし・連文節・英数かなモードに統合しました。
- 明確な数量は半角数字を優先し、漢数字・全角数字・別表記・時計表記も選べます。単独の「にほん／ごご／さんご／いっぱい」は強い既存の一般語候補を優先します。
- 数字候補への既存の加点を補正し、同点の探索経路をオブジェクト識別値ではなく内容で比較します。逐次入力で一般語の優先度が変わる場合はキャッシュを再構築します。
- 読みと表示文字列の長さが違っても区間を保ち、文節ごとの表記変更後も正しく連結します。辞書は一度読み込み、変換器を共有します。

| 実行した検証 | 結果 |
| --- | --- |
| 関連JUnit（変換器・文節表示・読み注釈等） | 290件、失敗0・エラー0、通常実行で性能プローブ等4件スキップ |
| 独立した上流の正解・不正解TSV | 全106件成功（JVMおよび各Android辞書プローブ） |
| 最終実装のPixel 6変換テスト | 4件すべて成功：6通りのモード/文節設定、区間、逐次入力、編集、キャンセル復元、複数助数詞、一般語、文節表記選択 |
| Lite Standard Debug / Full Standard Debug | 両方ビルド成功、両APKの辞書バイト列・SHA-256一致 |
| 実際のIME表示・選択・確定 | ローマ字入力→`123本を買う`、次候補選択→`百二十三本を買う`を確定。画面を保存 |
| 導入前・導入後・辞書単体の実機性能プローブ | 各3回、全9回成功 |

画面確認は隔離パッケージのみで実施し、終了後に元のIMEと試験用設定を復元しました。
[候補表示](counter-integration-2026-10-09/screenshots/quantity-sentence-candidates.png)・[漢数字候補の確定](counter-integration-2026-10-09/screenshots/quantity-sentence-kanji-committed.png)。

確認した例：`ひゃくにじゅうさんぼんをかう → 123本を買う`、`ねこがさんびきいる → 猫が3匹いる`、
`ほんをさんさつとえんぴつをにほんかう → 本を3冊と鉛筆を2本買う`、`ごごさんじはん → 午後3時半 / 15:30`。
「あう」は既存の同音語順位に従い「合う」が先頭、「会う」も候補にあります。

### 計測条件

Google Pixel 6、Android 17 / API 37、arm64-v8a。ビルド指紋は `google/oriole/oriole:17/CP3A.260905.009/16091614:user/release-keys`。
同じLite Standard Debug隔離パッケージ、同じ計測クラス、同じ辞書・設定を使用し、各回プロセスを停止してから開始しました。
最終APKと基準APKのハッシュは[ビルド記録](counter-integration-2026-10-09/build-metadata.json)に保存しています。

`n=8`、beam=20、追加Mozc辞書・学習・脱字検索・タイプミス補正は無効です。標準システムn-gramは新規試験パッケージの既定状態です。
文節区間の収集を有効にし、候補生成とセッション処理を計測します。UIの描画・ライブ変換待ち時間は含みません。
計測コードの結果ログ出力は区間外ですが、既存エンジンのDebugログは有効です。Releaseビルドの速度ではありません。

通常モード・予測なしモードは14入力を100巡ウォームアップし、入力を巡回させながら各1,000回測定しました。
逐次入力は3文を各100回ウォームアップし、空入力で区切って1文字ずつ入力する全シーケンスを各1,000回測定しました。
3回分を入力ごとにプール（各3,000標本）、nearest-rankで中央値・p95・p99を計算します。辞書単体は106入力・各1,000回×3回です。

### 予測なし連文節変換（ms）

| 読み / Reading | 前 p50 | 後 p50 | p50差 | 前 p95 | 後 p95 | 後 p99 | 後 最大 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `いちほん` | 4.066 | 4.006 | -1.5% | 5.145 | 4.127 | 5.469 | 10.660 |
| `いっちょうにせんさんびゃくよんじゅうごおくろくせんななひゃくはちじゅうきゅうまんいっせんにひゃくさんじゅうよんえん` | 34.633 | 30.096 | -13.1% | 41.263 | 30.533 | 38.136 | 45.997 |
| `いっぽん` | 2.394 | 2.551 | +6.6% | 3.111 | 2.671 | 3.464 | 8.100 |
| `きょうはいいてんきですね` | 11.215 | 11.439 | +2.0% | 13.673 | 11.697 | 13.447 | 20.804 |
| `ごごさんじはん` | 6.458 | 4.108 | -36.4% | 8.043 | 4.197 | 5.233 | 11.331 |
| `ごごさんじはんにあう` | 13.165 | 5.740 | -56.4% | 16.105 | 5.919 | 7.553 | 15.637 |
| `さんびき` | 3.084 | 2.886 | -6.4% | 4.013 | 2.972 | 3.755 | 9.159 |
| `にじゅうさんじごじゅうきゅうふんごじゅうきゅうびょう` | 18.434 | 10.442 | -43.4% | 22.392 | 10.627 | 12.952 | 20.961 |
| `ねこがさんびきいる` | 8.784 | 7.402 | -15.7% | 10.913 | 7.608 | 9.651 | 15.336 |
| `ひとり` | 2.590 | 2.843 | +9.8% | 3.332 | 2.943 | 3.681 | 8.276 |
| `ひゃくにじゅうさんぼん` | 8.321 | 6.101 | -26.7% | 10.332 | 6.225 | 7.373 | 14.032 |
| `ひゃくにじゅうさんぼんをかう` | 12.731 | 13.217 | +3.8% | 15.585 | 13.533 | 16.519 | 22.993 |
| `ほんをさんさつとえんぴつをにほんかう` | 43.007 | 24.081 | -44.0% | 51.348 | 24.657 | 26.901 | 39.096 |
| `よしよし` | 3.978 | 4.090 | +2.8% | 4.987 | 4.191 | 4.892 | 9.905 |

全入力が速くなるわけではありません。短い「いっぽん／ひとり」や「123本を買う」の中央値は少し増えます。
時刻や複数数量では、数量・時刻をまとめた規則ノードにより探索が減る入力があります。最大値は今回観測した値で、最悪時間の保証ではありません。

### 通常モードの連文節変換（ms）

| 読み / Reading | 前 p50 | 後 p50 | p50差 | 前 p95 | 後 p95 | 後 p99 | 後 最大 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `いちほん` | 4.313 | 4.293 | -0.5% | 5.242 | 4.485 | 5.448 | 13.428 |
| `いっちょうにせんさんびゃくよんじゅうごおくろくせんななひゃくはちじゅうきゅうまんいっせんにひゃくさんじゅうよんえん` | 35.016 | 30.521 | -12.8% | 39.945 | 31.450 | 36.218 | 46.238 |
| `いっぽん` | 4.173 | 4.215 | +1.0% | 5.195 | 5.201 | 5.955 | 11.645 |
| `きょうはいいてんきですね` | 11.502 | 11.725 | +1.9% | 13.607 | 12.048 | 14.761 | 22.011 |
| `ごごさんじはん` | 6.779 | 4.396 | -35.1% | 8.261 | 4.619 | 5.709 | 12.024 |
| `ごごさんじはんにあう` | 13.444 | 6.013 | -55.3% | 15.812 | 6.268 | 7.822 | 13.587 |
| `さんびき` | 3.432 | 3.249 | -5.3% | 4.318 | 3.439 | 4.290 | 16.875 |
| `にじゅうさんじごじゅうきゅうふんごじゅうきゅうびょう` | 18.767 | 10.736 | -42.8% | 22.148 | 11.201 | 12.795 | 21.102 |
| `ねこがさんびきいる` | 9.011 | 7.646 | -15.2% | 10.789 | 7.919 | 10.474 | 16.049 |
| `ひとり` | 5.830 | 6.078 | +4.3% | 6.969 | 6.771 | 7.418 | 14.847 |
| `ひゃくにじゅうさんぼん` | 8.652 | 6.395 | -26.1% | 10.463 | 6.657 | 8.019 | 14.012 |
| `ひゃくにじゅうさんぼんをかう` | 12.996 | 13.529 | +4.1% | 15.522 | 13.934 | 16.466 | 27.775 |
| `ほんをさんさつとえんぴつをにほんかう` | 43.106 | 24.392 | -43.4% | 50.271 | 24.950 | 28.950 | 37.134 |
| `よしよし` | 4.296 | 4.392 | +2.2% | 5.254 | 4.570 | 5.587 | 13.765 |

### 逐次入力（1文字単位ではなく、空入力＋全文を入力する1シーケンスのms）

| 読み / Reading | 前 p50 | 後 p50 | p50差 | 前 p95 | 後 p95 | 後 p99 | 後 最大 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `ごごさんじはんにあう` | 39.483 | 25.829 | -34.6% | 48.023 | 26.460 | 27.936 | 77.628 |
| `ねこがさんびきいる` | 32.613 | 29.461 | -9.7% | 39.297 | 30.406 | 33.295 | 45.723 |
| `ひゃくにじゅうさんぼんをかう` | 59.464 | 54.569 | -8.2% | 70.638 | 56.075 | 64.918 | 70.144 |

### 辞書単体と初回処理

| 項目 / Metric | 実測 / Measured |
| --- | ---: |
| 配布辞書 | 41,327 bytes（40.36 KiB） |
| プリミティブ索引の要素分 | 18,674 bytes（オブジェクト全体の保持量ではない） |
| 辞書＋変換器の保持ヒープ概算 | 79,712 bytes（77.84 KiB）、全3回同値 |
| 初回ロード＋変換器作成（各回の最初の1回） | 41.974, 39.839, 42.385 ms |
| 辞書単体・候補生成込みの318,000標本：p50 / p95 / p99 / 最大 | 0.017 / 0.044 / 0.059 / 3.933 ms |
| 辞書単体のプロセス割当量概算 / 呼出し | 1,949.1 bytes |
| エンジン初期化（任意辞書状態初期化を含む）中央値：前 → 後 | 273.306 → 283.448 ms |

独立した辞書プローブの初回ロードにはクラスの初回利用・検証・辞書の展開/解析・変換器作成を含みます。
上流資料の20 msというロード目標には、このDebug実測では達していません。初期化済みエンジンの通常変換と分けて報告します。
保持量は128個の辞書/変換器を生存させ、GC後のヒープ差を128で割った概算です。クラスメタデータや一時バッファの全費用ではありません。

### プロセスメモリ（3回の中央値）

| 経路 / Path | 割当 KiB/呼出し 前 | 後 | 差 | GC後Java MiB 前 | 後 | GC後Native MiB 前 | 後 | GC後PSS KiB 前 | 後 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| NO_TAB_DEFAULT | 1209.47 | 850.48 | -29.7% | 24.012 | 24.098 | 7.520 | 7.529 | 130,409 | 131,527 |
| CONVERSION | 1193.99 | 832.97 | -30.2% | 24.329 | 24.411 | 7.524 | 7.533 | 130,879 | 131,692 |
| incremental | 1486.54 | 1545.88 | +4.0% | 25.305 | 25.364 | 7.561 | 7.571 | 134,411 | 135,524 |

逐次入力の割当量は1文字ではなく1シーケンス当たりです。通常/予測なしの割当量は減り、逐次入力の割当量は増えました。
割当量・GC回数はARTのプロセス統計で、背景処理も含む概算です。ネイティブ・PSSの小さな差は辞書の保持量とは解釈しません。
GC前の終点スナップショットも生データに保存していますが、GCのタイミングで大きく変わるためピークメモリとは呼びません。

### 範囲と再現

- 数量・時刻・128文字制限は上流の規則範囲です。小数・負数・汎用接尾辞の解析を追加していません。
- 完全一致APIの不正解例は全件拒否します。通常のグラフは既存辞書のノードも連結するため、完全一致APIが拒否した読みの候補まで一律に削除しません。
- 短い有効区間から連結されたN-bestには混合表記も残ります（例：時刻の短い時計表記＋秒の数量）。検証した第一候補・規則辞書の完全一致候補・文節表示の正しさと区別します。
- Pixel 6のDebug実測です。Pixel 4、Release性能、端末間の保証、UI遅延全体は未計測です。

[実装と出典](../counter-dictionary-integration.md)、[全入力・全回の統計](counter-integration-2026-10-09/summary.json)、
[生データ](counter-integration-2026-10-09/raw/)、[JUnit集計](counter-integration-2026-10-09/junit-summary.json)、[実機テスト結果](counter-integration-2026-10-09/correctness.log)。
各`.txt.gz`は全ナノ秒標本・初回候補・Java/Native/PSSの前/直後/GC後・割当量・GC回数を含みます。

```sh
# JDK 17 / Gradle 8.11.1 / Android SDK 36。隔離APKを作成する。
./gradlew -I investigation/counter-probe.init.gradle \
  :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest
adb install -r app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
adb install -r app/build/outputs/apk/androidTest/liteStandard/debug/app-lite-standard-debug-androidTest.apk
python3 investigation/run-counter-device-probes.py --label integrated --output /tmp/counter-probes
python3 investigation/run-counter-device-probes.py --rules --label rules --output /tmp/counter-probes
python3 investigation/summarize-counter-probes.py /tmp/counter-probes --output /tmp/counter-summary.json
```

基準値は、同じworktreeで計測クラス・隔離設定だけを先に追加し、本体が`dev`の状態のAPKを保存して計測しました。
再現する場合も同じ計測クラスを基準コミットへ適用し、別の`--label baseline`で3回実行します。
通常のIMEパッケージにはインストールせず、計測中に他のプローブを同時実行しません。

## English

Implemented in a dedicated worktree based on freshly fetched `origin/dev` commit `9d1fe55da944ce50b71e14f218207a29ac3e2009`.
The runtime and binary asset are pinned to upstream PR #121 commit `333c338bbbcf9feada95796cf1e88e3aa6473bc4`.
The measured implementation is `1f6178206`; subsequent report/data commits do not change the implementation.

### Behavior and validation

Quantity/time readings become atomic lattice nodes with original input offsets and appropriate number/counter or time context IDs.
All production conversion modes use the rules. Clear quantities prefer ASCII digits, with kanji, fullwidth, aliases and clock alternatives available.
Stronger whole-reading lexical entries preserve Japan/afternoon/postpartum/lots readings instead of forcing their numeric homonyms.
Equal-cost queue entries compare path contents, and changes in whole-reading lexical priority invalidate incremental caches.

All 290 relevant JVM tests passed (four opt-in probes skipped), all 106 independent upstream golden cases passed on JVM and in each Android rule probe,
and all four final Pixel 6 conversion tests passed. These cover all mode/bunsetsu combinations, input/output coverage, typing/editing parity,
cancellation recovery, multiple counters, ordinary readings, and changing bunsetsu notation without corrupting the rest of the sentence.
Lite and Full Debug builds passed; the dictionary asset hash matches in both APKs.
A real isolated IME session displayed `123本を買う`, offered kanji/fullwidth alternatives, and committed the selected `百二十三本を買う`.
The original IME and test preferences were restored. The screenshots linked above contain this test session.

### Timing interpretation

The shared tables above report pooled nearest-rank percentiles from three independent fresh-process runs per version (3,000 samples per input).
The Pixel 6 runs Android 17/API 37 on arm64. Both versions use the same Lite Standard Debug package, harness, corpus and options:
100 warmup rounds, 1,000 measured calls per input, n=8, beam=20, segment collection enabled, optional Mozc dictionaries/learning/omission/typo correction disabled.
Default system n-gram state is retained. Candidates and session processing are timed; UI rendering/debounce is excluded.
The probe's own report output is excluded, but existing engine Debug logging remains enabled. These are not Release benchmarks.

Clock and multi-quantity cases became substantially faster, while some short inputs and the 123-counter sentence have a small median increase.
The typing table times one complete sequence (empty query plus all prefixes), not one keystroke.
The observed maxima are observations, not worst-case guarantees. Individual runs, raw samples and all per-input p50/p95/p99/max values are linked above.

### Dictionary and memory

The asset is 41,327 bytes; primitive index elements occupy 18,674 bytes.
The retained dictionary/converter heap estimate is 79,712 bytes (77.84 KiB), identical in all three amplified measurements.
The estimate holds 128 independent instances alive, forces GC and divides the heap increase by 128; it excludes full class metadata and transient buffers.
The 318,000 standalone parsing/candidate-generation samples yielded p50 0.017, p95 0.044, p99 0.059, maximum 3.933 ms.
First independent load/converter creation took 41.974, 39.839, 42.385 ms, including first-use classes, validation and decoding.
This Debug measurement does not meet the upstream 20 ms load target. Median production-engine initialization was 273.306 ms before and 283.448 ms after.

The shared memory table reports medians across runs. Standard/no-prediction allocation decreased, while allocation per complete typing sequence increased.
ART allocation/GC counters are process-wide estimates. Java/native/PSS before, immediately after and after GC are retained in the raw files.
Endpoint snapshots are not peak measurements; GC timing makes their values vary substantially. Small PSS/native differences are not dictionary-object retention.

### Limits and reproduction

The upstream quantity/time ranges and 128-code-unit per-reading limit are preserved; no decimal, negative-number or generic suffix parser was added.
The exact API rejects the negative golden cases, while the general lattice still composes existing dictionary nodes and shorter valid spans.
Mixed notation can therefore remain among other N-best paths, including shorter clock prefixes followed by second quantities.
Verified primary results, exact-rule alternatives and bunsetsu projection are distinguished from such general-lattice alternatives.
Pixel 4, Release performance, cross-device guarantees and total UI latency were not measured.

Use the build/install/probe commands above. Recreate the baseline by applying the same harness and isolated package setup to the pinned dev commit,
then execute three `--label baseline` runs before installing the final APK. No normal IME package needs to be replaced.
