# 数量を含む連文節変換の性能改善

## 原因と変更

数量がある連文節では `QuantityGuidedSearch.buildCosts` がシステム文脈辞書と組み合わせた経路を探索する。
従来の `QuantityScoreMachine` は「先頭の単語が何らかのルールに出る」ことだけを基準に最大4語を保持していた。
この判定には単語単独のルールも含まれ、既に複数語ルールの先頭と一致しなくなった履歴まで区別していた。
さらに遷移ごとに履歴の語分類・リストを再構築していた。

- 複数語ルールの**真の接頭辞**を索引化し、将来の一致に必要な最長の接尾履歴だけを保持する。
- 単語単独の一致は従来どおりその場で判定し、過去に一致した事実は探索状態のフラグで保持する。後続の判定のために単語を保持しない。
- 索引のハッシュ衝突は余分な履歴の保持にしかならない。完全一致は既存の辞書照合を使用する。
- 語分類は探索ごとのキャッシュで再利用する。キャッシュは探索終了時に解放可能で、セッションやThreadLocalに入力ノードを追加保持しない。
- 辞書形式、数量設定、要求候補数、文脈スコアは変更しない。
- 通常探索の候補の右品詞IDを先頭ノードから末尾ノードへ修正し、数量探索と一致させる。

## 測定条件

比較基準は対象PRを統合した `fe8ebeeaa`。元worktreeで作成したLite Standard Debug APKを使用。
システム文脈辞書と数量モデルは有効、候補数4、予測・文節区切りあり、追加辞書・省略検索・タイプミス補正なし。
ウォームアップ5回、時間測定30回、割り当て測定10回。従来・増分バックエンドの実行順は反復ごとに交互にする。
以下の速度・割り当ては増分バックエンドの結果。割り当て量はARTの累計割り当てであり、常駐量やピーク使用量ではない。
JavaヒープとPSSは強制GC後のスナップショットで、タイミング測定の外で採取する。
GC回数・時間は時間測定区間のプロセス全体（両バックエンド合計）の差分。
初期化・描画・入力イベント配送は速度に含まれない。Debugビルドの測定値であり、Releaseや全端末の速度保証ではない。

### Pixel 6 / API 37

| 入力・測定範囲 | 平均時間（前→後） | 1クエリp95（前→後） | 最大時間（前→後） | 累計割り当て（前→後） |
|---|---:|---:|---:|---:|
| わたしはよんふんまつ：10文字の逐次入力合計 | 2285.6 → 113.2 ms | 784.76 → 26.36 ms | 811.92 → 34.82 ms | 120.41 → 5.19 MB |
| りんごをさんこかう：全文1回 | 461.0 → 47.3 ms | 479.91 → 65.42 ms | 492.22 → 66.64 ms | 28.22 → 1.98 MB |
| 通常文：22文字の逐次入力合計 | 193.2 → 191.7 ms | 15.53 → 15.45 ms | 20.99 → 18.51 ms | 6.70 → 6.70 MB |

通常文は「このあぷりのへんかんこうほをこうそくにしたい」。最大時間の列も1クエリの値。
例文のp95は約30倍、割り当ては約96%減少。全文1回の例ではp95が約7.3倍、割り当ては約93%減少した。
例文のGCは時間測定区間で58回/2,299msから3回/141msへ減少。
GC後Javaヒープは例文で28.50→25.00MiB、全文1回の例で27.00→25.01MiB。
PSSは実行間の共有ページ・JIT等の影響があるため、単独では効果判定に使用しない。

### 探索量と保持メモリ

エミュレータ/API 35で逐次入力・削除・再入力を含む同じコーパスを比較した最大値：

| 入力 | 状態数（前→後） | 遷移数（前→後） |
|---|---:|---:|
| わたしはよんふんまつ | 7,890 → 201 | 249,812 → 4,608 |
| りんごをさんこかう | 7,551 → 239 | 123,580 → 6,785 |
| さんびゃくごじゅうえんつかう | 13,067 → 382 | 347,537 → 10,806 |
| にじゅうさんじごふんにでる | 32,883 → 600 | 780,062 → 13,880 |
| さんまいとにまい | 20,159 → 330 | 472,788 → 11,054 |
| はつかまでまつ | 2,945 → 121 | 18,202 → 3,110 |

「わたしはよんふんまってりんごをさんこかってひゃくえんはらう」の入力・削除・再入力136クエリも通過。
エミュレータでの診断実行では増分・文節ありの最大時間は26.1ms、最大状態731、最大遷移19,933。
両バックエンドの長短反復300クエリ後のJavaヒープ差は、エミュレータで従来+0.22MB・増分−0.14MB、Pixel 6で従来+0.22MB・増分−0.17MB。
この反復範囲では大きな保持量の増加は見られなかった。任意の長さ・端末で一定時間や一定メモリを保証するものではない。

全測定値、APKと辞書アセットのSHA-256は [測定データ](quantity-conversion-performance.json) に保存した。


## 正確性とレビュー

最終検証: JDK 17で全1,790ユニットテスト（失敗0、既存スキップ4）、Pixel 6とエミュレータで数量回帰各4件・保持メモリ試験を通過。差分レビューの未解決指摘なし。

- 7文を一文字ずつ入力し、削除・再入力も含む472クエリを改善前後で照合。両バックエンド・文節区切りON/OFFを含む。
- 候補数、順序、表記、コスト、読み、左品詞ID、数量範囲、候補の文節情報、システム文脈一致は同じ。右品詞IDの意図した修正だけを別扱いにして比較する。
- 内部マップの列挙順は意味を持たないため、キーで整列して比較する。
- 単語単独、2〜5語、品詞、ワイルドカード、重なり、複合数量ノードをテスト。固定シードの2,000経路について保守的な履歴保持と一致フラグ・スコアを比較する。
- 既存のキャンセル後の復帰、設定変更、ユーザー・学習辞書、両バックエンドのテストを実行する。
- 長文→短文→追加入力を両バックエンドで各300クエリ反復し、GC後の保持量を記録する。
- レビューでは接頭辞の取りこぼし、ハッシュ衝突、状態共有の同値性、キャッシュ寿命、品詞IDの経路整合性を確認する。

## 再実行

JDK 17でビルド・ユニットテスト（全スイートではテストJVMを3GiB、80クラスごとの再起動、並列数1に設定）：

```groovy
// /tmp/quantity-test-memory.gradle
allprojects {
    tasks.withType(Test).configureEach {
        maxHeapSize = '3g'
        forkEvery = 80
        maxParallelForks = 1
    }
}
```

既定512MiBでは全スイートでメモリ不足が発生したため、アプリの設定を変えずにテスト用initスクリプトで調整した。
JDK 21では既存の時刻表記テストが空白文字の差で失敗するため、JDK 17を使用する。

```sh
./gradlew --no-configuration-cache -I /tmp/quantity-test-memory.gradle \
  :app:testLiteStandardDebugUnitTest :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest
adb -s DEVICE install -r app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
adb -s DEVICE install -r app/build/outputs/apk/androidTest/liteStandard/debug/app-lite-standard-debug-androidTest.apk
```

速度・割り当て・保持メモリ：

```sh
adb -s DEVICE shell am instrument -w -r \
  -e class com.kazumaproject.markdownhelperkeyboard.converter.IncrementalConversionSessionPerformanceInstrumentedTest#compareLegacyAndIncrementalSessionsOnDevice \
  -e incrementalConversionPerfProbe true \
  -e conversionPerfInput わたしはよんふんまつ \
  -e conversionPerfWarmups 5 -e conversionPerfIterations 30 \
  com.kazumaproject.markdownhelperkeyboard.lite.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE exec-out run-as com.kazumaproject.markdownhelperkeyboard.lite \
  cat files/conversion-perf/incremental-session-prediction-bunsetsu-true.txt
```

全文1回を測る場合は `-e conversionPerfPrefixLengths 9` のように入力長を指定する。
文節区切りなしは `-e conversionPerfBunsetsu false`（出力ファイル名も `false` に変わる）。

候補・探索状態のキャプチャは同じテストAPKを基準版と改善版のアプリAPKに対して実行できる：

```sh
adb -s DEVICE shell am instrument -w -r \
  -e class com.kazumaproject.markdownhelperkeyboard.converter.QuantityPerformanceInstrumentedTest#captureCorpus \
  -e quantityCorpusProbe true \
  com.kazumaproject.markdownhelperkeyboard.lite.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE exec-out run-as com.kazumaproject.markdownhelperkeyboard.lite \
  cat files/conversion-perf/quantity-corpus.json > before.json
# 改善版APKへ更新後に同じテストを実行し、after.jsonとして保存
python3 tools/compare_quantity_corpus.py before.json after.json --allow-right-id-fix
```

`--allow-right-id-fix` は今回の右品詞ID修正を含む比較にだけ使用する。以降の比較では省略する。
キャプチャの時間・割り当ては診断用であり、ウォームアップした速度測定とは別に扱う。
数量状態数は作業領域の直近の探索を示す。数量探索がない入力では前回値が残り得るため、文ごとの最大値を比較する。

保持メモリ試験は `QuantityPerformanceInstrumentedTest#repeatedLongAndShortEditsKeepRetainedMemoryBounded` に
`-e quantityRetentionProbe true` を渡す。結果は `files/conversion-perf/quantity-retention.json`。
既存の数量回帰は `QuantityModelConversionInstrumentedTest` クラス全体を実行する。
