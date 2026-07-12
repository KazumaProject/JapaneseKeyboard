# N-gram 補正 4/5 ノード対応・性能観測設計

## 1. 目的

現在の 2 ノード・3 ノード補正を 4 ノード・5 ノードまで拡張する。
同時に、N とルール数が以下に与える影響を再現可能な形で観測する。

- スコアラーが常駐時に保持するメモリ
- スコアラー生成時の割り当て量と生成時間
- 1 回の `score()` にかかる時間
- 実際のかな漢字変換にかかる時間、割り当て量、GC 回数
- 補正による探索量（展開回数・キュー最大サイズ）の変化

性能値は端末・ビルド種別・実行時状態に依存するため、最初は合否判定ではなくレポートとして保存する。

## 2. 現状

### 2.1 実行時

- `NgramRule.kt` は `TwoNodeRule` と `ThreeNodeRule` を別々の型で持つ。
- `NgramRuleScorer` は 2 ノードを `current.word`、3 ノードを `second.word` で索引化する。
- `FindPath` の後方探索で辺 `prevNode -> currentNode` を展開するときに `score(prevNode, currentNode)` を 1 回呼ぶ。
- 3 ノード目は `currentNode.next` から取得する。
- 2 ノードと 3 ノードの両方が一致した場合、および完全一致ルールとワイルドカードルールが一致した場合は補正値を加算する。

後方探索時には右側の経路が既に確定している。そのため 4/5 ノード対応でも新しい経路を列挙せず、次の固定されたウィンドウを照合できる。

```text
2-node: prev, current
3-node: prev, current, current.next
4-node: prev, current, current.next, current.next.next
5-node: prev, current, current.next, current.next.next, current.next.next.next
```

### 2.2 永続化・UI

- Room は `two_node_rule` と `three_node_rule` の別テーブルで、DB バージョンは 37。
- DAO、Repository、ViewModel、編集ダイアログ、一覧、JSON バックアップがすべて 2/3 ノード別に分岐している。
- 現在の JUnit ベンチマークは 2/3 ノードの `ns/op` を `println` するが、端末上の ART、保持メモリ、割り当て量、分位点は測っていない。

## 3. 採用方針

4/5 ノード専用クラスと専用テーブルを単純追加せず、2〜5 ノードを一つのモデルで表現する。これにより、スコアラー、Repository、UI、バックアップ、テストに同じ分岐を増やさない。

上限は今回の要件に合わせて 5 とする。実行時のホットパスでは可変長モデルをそのまま辿らず、ロード時に 2〜5 ノード用の索引へコンパイルする。

### 3.1 ドメインモデル

概念上は次のモデルに統一する。

```kotlin
data class NgramRule(
    val nodes: List<NodeFeature>, // size: 2..5
    val adjustment: Int,
)
```

生成時に以下を検証する。

- `nodes.size` は 2〜5
- adjustment は -10000〜10000
- word、leftId、rightId がすべてワイルドカードのノードも現行互換として許可

`TwoNodeRule` / `ThreeNodeRule` は移行中だけ変換アダプターとして残し、呼び出し元とテストの移行後に削除する。

### 3.2 実行時索引

ルールはロード時に次の二段階で索引化する。

1. N（2、3、4、5）
2. アンカーノードである `nodes[1].word`（現在の `current` / `second` と同じ位置）

各 N について、完全な word キーのバケットと word ワイルドカードのバケットを分ける。`score()` は現在語のバケットとワイルドカードバケットだけを走査する。

```text
compiledRules[N][currentWord] -> candidate rules
wildcardRules[N]             -> candidate rules
```

照合時に `List`、`Sequence`、一時 `Array` を生成しない。最大 5 ノードの参照をローカル変数として取得し、必要な N のルールが存在するときだけ `next` を先へ辿る。これにより、4/5 ノード機能を追加しただけで、ルール未登録時の変換割り当て量が増えないようにする。

### 3.3 スコア計算の意味

- N ノードルールは、辺 `nodes[0] -> nodes[1]` を展開した時点で 1 回だけ評価する。
- 必要な `next` がない、または途中が EOS の場合、その N のルールは一致しない。
- BOS/EOS を補正対象外とする現行仕様を維持する。
- 2/3/4/5 ノードおよび完全一致・ワイルドカードで複数ルールが一致した場合、現行どおり adjustment をすべて加算する。
- `Int` 加算のオーバーフローを避けるため、内部合計は `Long` か飽和加算を用い、最終的に `Int` 範囲へ収める。ルール数を増やす性能試験で意図しないオーバーフローを起こさないようにする。

### 3.4 経路探索との関係

`FindPath` の三つの後方探索経路すべてで、スコア計算前に `currentNode.next` 以降が対象の候補経路を指していることを保証する。

特に `PathQueueElement` を使う探索では、共有 `Node.next` の書き換えに依存すると別候補経路の suffix を参照する危険がある。実装時に以下のどちらかを選び、テストで固定する。

- 推奨: `score(prevNode, currentNode, next1, next2, next3)` のようにキュー要素が持つ経路から suffix を明示的に渡す。一時リストやラムダは作らない。
- 最小変更: 現行どおり `Node.next` を使うが、スコア直前に正しい suffix を設定し、並行・再利用される経路で上書きされないことを証明する。

4/5 ノードでは suffix の誤参照が見えやすくなるため、推奨案を採る。

## 4. Room 設計

DB バージョンを 37 から 38 に上げ、2〜5 ノード共通の `ngram_rule` テーブルへ移行する。ホットパスでのメモリ効率と DAO の単純さを優先し、最大 5 ノード分の列を持つ単一行形式とする。未使用ノードは空文字と `-1` で保存する。

主要列は以下。

```text
id INTEGER PRIMARY KEY AUTOINCREMENT
nodeCount INTEGER NOT NULL                 -- 2..5
node1Word, node1LeftId, node1RightId
...
node5Word, node5LeftId, node5RightId
adjustment INTEGER NOT NULL
```

制約・索引:

- nodeCount の 2〜5 制約は Entity 生成・Repository・import の各境界で検証する（Room の生成スキーマと migration を一致させるため DB 固有の CHECK には依存しない）
- 全特徴量と nodeCount の UNIQUE index で重複登録を防ぐ
- 一覧取得用に `(nodeCount, id)` index を作る
- 実行時検索は DB ではなくメモリ上のコンパイル済み索引を使う

`MIGRATION_37_38` は次の順で実施する。

1. `ngram_rule` を作成する。
2. `two_node_rule` を nodeCount=2 として `INSERT OR IGNORE` する。
3. `three_node_rule` を nodeCount=3 として `INSERT OR IGNORE` する。
4. 件数検証後に旧 2 テーブルを削除する。
5. `AppModule` に migration を登録する。

Room migration test で、ワイルドカード、ID、adjustment、重複制約が保存されることを確認する。

## 5. Repository・更新通知

- DAO は `Flow<List<NgramRuleEntity>>` を一つ公開する。
- Repository は Entity と `NgramRule` の相互変換、正規化、2〜5 の検証を担当する。
- `replaceAll` は Room の `@Transaction` にし、削除と挿入の途中状態を observer に見せない。
- `NgramRuleScorerManager` は全ルールを一度コンパイルし、完成した不変スコアラーを `AtomicReference` で差し替える。
- UI 保存後の明示的 `refreshNow()` と Flow 更新による二重再構築は整理し、原則 Flow を単一の更新元にする。保存直後の反映保証が必要なら Repository の transaction 完了後に一度だけ同期更新する。

## 6. UI と JSON 互換性

### 6.1 UI

編集処理を `showRuleDialog(nodeCount, existing)` に統一する。編集画面には最大 5 個のノード入力欄を用意し、選択した N を超える欄は非表示にする。

- 追加時に 2 / 3 / 4 / 5 ノードを選べる
- 一覧タイトルは「Nノードルール」
- 詳細は全ノードを `->` で連結
- 編集・削除処理は N ごとの `when` 分岐を持たない
- 既存の辞書単語検索と ID 入力補助を全ノードで利用できる

固定 XML をさらに複製せず、ノード入力部分を再利用可能な child layout として最大 5 個 inflate する。

### 6.2 JSON

新形式には明示的なバージョンを持たせる。

```json
{
  "version": 2,
  "rules": [
    { "nodes": [/* 2..5 NodeFeatureInput */], "adjustment": -2000 }
  ]
}
```

import は JSON オブジェクトを先に検査する。

- version=2: `rules` を読む
- version がなく `twoNodeRules` / `threeNodeRules` がある: 旧形式として新モデルへ変換
- 未知の version、N が 2〜5 以外、不正な ID・adjustment: エラー位置を示して全体を取り込まない

export は version=2 のみとする。ProGuard keep 対象も新バックアップ型へ更新する。

## 7. テスト設計

### 7.1 正しさを保証する通常テスト

通常の unit test は性能値を assert せず、以下を高速に検証する。

- 2/3 ノードの既存挙動が不変
- 4 ノード完全一致・不一致・suffix 不足・途中 EOS
- 5 ノード完全一致・不一致・suffix 不足・途中 EOS
- word / leftId / rightId の各ワイルドカード
- 2/3/4/5 が同時一致したときの加算
- 同じ current word の完全一致バケットとワイルドカードバケットの加算
- 異なる候補経路の suffix を取り違えない
- 4/5 ノード補正によって期待する変換候補が 1 位になる統合テスト
- 旧 JSON import、新 JSON round-trip、DB 37→38 migration

### 7.2 性能プローブの層

性能観測は三層に分ける。

#### A. スコアラー単体

対象:

- N: 2、3、4、5
- ルール数: 0、10、100、500、1000（必要なら 5000）
- 分布: current word が分散した通常ケース / 同じ current word に集中した最悪ケース / word ワイルドカード集中
- 結果: hit / miss

指標:

- scorer 構築時間
- scorer 構築時 allocated bytes
- scorer の GC 後 retained Java heap（複数 scorer を保持して差分を拡大し、1 個あたりへ換算）
- `score()` の p50 / p95 / ns/op
- `score()` 1 回あたり allocated bytes（目標 0）

#### B. 合成グラフによる FindPath

同じノード数・分岐数・候補数のグラフで、N とルール数だけを変える。

指標:

- 変換 p50 / p95
- 変換 1 回あたり allocated bytes
- A* ループ回数、展開辺数、キュー最大サイズ
- 候補 fingerprint

補正が一致しない no-op ルールでは fingerprint が baseline と同一であることを確認する。一致ルールの試験では期待候補だけが変わることを確認する。

#### C. 実辞書・実端末

短文、中程度、長文、および 5 ノード以上に分節される日本語入力の固定 corpus を使う。既存の `ConversionPerformanceProbeTest` と `PrefixConversionPerformanceInstrumentedTest` のレポート方式を再利用する。

指標:

- cold 変換時間
- warm p50 / p95 / max
- 逐次入力 1 セッション全体の時間
- `art.gc.bytes-allocated` の差分、GC 回数
- GC 後 Java heap 差分、参考値として PSS
- 最終候補 fingerprint

端末上の結果を正式値とし、デスクトップ JVM の値は高速な傾向確認に限定する。

### 7.3 比較条件

最低限、次のケースを同一プロセス・同一端末で順序を入れ替えて複数回測る。

| ケース | 有効なルール |
|---|---|
| baseline | 追加ルール 0 |
| N=2 | 2 ノードのみ K 件 |
| N=3 | 3 ノードのみ K 件 |
| N=4 | 4 ノードのみ K 件 |
| N=5 | 5 ノードのみ K 件 |
| mixed | 2/3/4/5 を各 K 件 |

ルール総数を揃える比較と「各 N に K 件」の比較を分ける。後者だけでは mixed の総数が 4 倍になり、N の影響と件数の影響を区別できない。

各ケースは warm-up 後に 20 回以上のバッチを実行し、平均だけでなく p50 と p95 を保存する。端末、API、ABI、build type、アプリ version、ルール seed、反復数もレポートへ記録する。

### 7.4 出力

人間向けテキストに加え、比較可能な TSV または JSON を `build/reports/ngram-performance/` に出力する。

```text
device, buildType, layer, distribution, n, ruleCount,
buildNs, retainedBytes, allocatedBytesPerOp,
p50Ns, p95Ns, conversionP50Ms, conversionP95Ms,
expandedEdges, maxQueueSize, fingerprint
```

最初の実装では閾値による CI failure を入れない。基準端末で 5 回以上の baseline を集め、分散を確認してから、例えば「p95 が baseline 比 15% 以上悪化」のような回帰閾値を別途決める。

## 8. 予想される性能特性

- 4/5 ノード対応コードだけを追加し、ルールが 0 件なら、常駐メモリと変換時間への影響はほぼ固定かつ小さい。
- ルール保持メモリは、概ね「ルール数 × N」に比例する。
- word 索引が効く通常ケースの `score()` 時間は全ルール総数ではなく、同じ current word のバケット件数に主に比例する。
- current word ワイルドカードが多い場合は毎回走査されるため、ルール数に比例して遅くなる。
- N=5 は N=2 より最大 3 個多く node/feature を比較するため、同一バケット件数なら遅くなるが、経路組合せの指数的増加はない。
- adjustment により A* の探索順が変わるため、実変換時間は単体 scorer の増分だけでは説明できない場合がある。そのため探索量も同時記録する。

## 9. 実装順序

1. 2〜5 ノード共通ドメインモデルと、suffix を明示する scorer API を導入する。
2. 既存 2/3 の正しさテストを新モデルで通し、4/5 の scorer・FindPath テストを追加する。
3. Room 37→38 migration、共通 DAO/Repository、migration test を実装する。
4. ScorerManager を共通ルールへ移行する。
5. 共通 UI、4/5 ノード入力、一覧、編集、削除を実装する。
6. JSON v2 export と v1/v2 import を実装する。
7. 単体・合成グラフ・実端末の性能プローブとレポート生成を実装する。
8. 基準端末で baseline / N=2 / N=3 / N=4 / N=5 / mixed を測り、結果を比較する。

## 10. 完了条件

- 2/3 ノードの既存ルール、DB データ、旧 JSON が失われない。
- UI から 2/3/4/5 ノードルールを追加・編集・削除・export/import できる。
- 4/5 ノード補正が三つの後方探索経路すべてで一度だけ適用される。
- ルール 0 件時に `score()` がヒープ割り当てを行わない。
- N とルール数ごとの保持メモリ、割り当て量、単体時間、変換時間、探索量を同じ形式で出力できる。
- 候補 fingerprint によって、性能測定中も結果の同一性または意図した変化を検証できる。

## 11. 実装後の計測コマンド

スコアラー単体の JVM プローブは明示的に有効化したときだけ実行される。

```bash
NGRAM_PERF_PROBE=true \
NGRAM_PERF_COUNTS=0,10,100,500,1000 \
NGRAM_PERF_WARMUP=10000 \
NGRAM_PERF_ITERATIONS=100000 \
./gradlew :app:testLiteStandardDebugUnitTest \
  --tests '*NgramRuleScorerPerformanceProbeTest'
```

結果は `app/build/reports/ngram-performance/scorer.tsv` に出力される。

実辞書・実端末の変換プローブは接続端末に対して次のように実行する。

```bash
./gradlew :app:connectedLiteStandardDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.converter.NgramConversionPerformanceInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.ngramPerfProbe=true \
  -Pandroid.testInstrumentationRunnerArguments.ngramPerfCounts=0,10,100,500,1000 \
  -Pandroid.testInstrumentationRunnerArguments.ngramPerfIterations=20
```

端末、温度、バックグラウンド負荷を揃え、正式比較では複数回実行する。テストは開始前の N-gram ルールを退避し、終了時に復元する。
