# フリック文字プレビュー 実装・実機試験報告

試験日: 2026-08-02（背景Span修正の追試: 2026-08-03）

## 結論

`onDown` と `onMove` で、選択中の TenKey／Sumire の文字を入力先アプリの composing text に仮表示する設定を実装した。設定は新規設定画面と従来設定画面の両方に追加し、デフォルトは OFF とした。OFF の場合は従来どおり `onUp` または二本目の指を検知した時点で入力へ反映する。

Pixel 6 実機では、候補変換の開始を `onUp` まで遅延したまま、Down 表示、Move 置換、既存PreEditと同じ背景・文字色・下線Span、Up 確定、Cancel 復元が正常に動作した。候補表示時間に有意と判断できる悪化は観測されなかった。プレビューONでは Down／Move／Cancel ごとに入力先アプリとの composing-text 通信が発生するため、合成ジェスチャ試験の同期処理時間と一時割り当て量は増えたが、GC後PSSの増加は観測されなかった。

## 実装内容

- 共通設定キー: `flick_editor_preview_preference`
- デフォルト: `false`
- 設定画面: 新規設定の「フリック入力」カテゴリ、従来設定の「フリック入力」カテゴリ
- 対象: TenKey と Sumire のテキスト入力キー
- Sumire確認スタイル: `default`、`circle`、`second-flick`、`third-flick`、`sumire`
- 通常表示とフローティング表示の両方へ同じリスナーを接続
- `onDown`: タップ位置の文字を仮表示
- `onMove`: 現在選択中の文字で仮表示を置換
- Down／Moveとも通常入力のPreEditと同じ `BackgroundColorSpan`、任意の `ForegroundColorSpan`、`UnderlineSpan` を適用し、カスタム入力色設定にも追従
- `onUp`: プレビューと同一の入力変換を一度だけ確定し、その後に従来どおり候補生成
- `ACTION_CANCEL`、ビュー非表示、入力セッション切替: 仮表示を破棄して正規の composing text を復元
- パスワード、数値、直接入力、変換中、範囲選択中、カーソル移動中など、安全にプレビューできない状態では無効
- プレビュー処理中は `_inputString` とトグル入力状態を変更せず、確定時だけ反映

実機試験で、composing text が存在しない状態へ `finishComposingText()` で復元すると、Pixel 6では表示中のプレビューが確定されることを検出した。復元処理は空の composing region を設定する方式に変更し、Cancel時に文字が残らないことを確認した。

また、端末に復元されていた既存のキーボード順序JSONに未知の列挙値が含まれると起動時に `null` が混入する問題を検出したため、未知値を除外し、空になった場合は TenKey／QWERTYへフォールバックするようにした。

## 実機機能試験

端末は USB 接続した Pixel 6 (`oriole`)、Android 16、API 36。`liteStandardDebug` を使用した。

以下を自動操作で確認し、最終差分に対する試験は成功した。

- 設定ON: TenKeyおよびSumire 5スタイルで Down直後に文字が表示される
- 設定ON: TenKeyおよびSumire 5スタイルのDownで、デフォルト背景色の `BackgroundColorSpan` が全文範囲に `SPAN_COMPOSING` として存在する
- 設定ON: Up前は候補欄が空であり、変換処理が開始されない
- 設定ON: Cancel後に仮表示が消え、元の composing stateへ戻る
- 設定ON: TenKeyおよびSumire `default` で Moveにより文字が変化する
- 設定ON: TenKeyおよびSumire `default` のMove後も、カスタム設定した背景色Spanと下線Spanが変更後の全文範囲に存在する
- 設定ON: Up後の確定結果がMove時のプレビューと一致する
- 設定OFF: TenKeyおよびSumireでDown時は未反映、Up時に従来どおり入力される

## 変換時間

測定区間は `ACTION_UP` 注入開始から最初の候補が表示されるまで。各条件で3回ウォームアップ後、15回測定した。

| 条件 | 平均 | P50 | P95 |
|---|---:|---:|---:|
| OFF | 125.475 ms | 123.272 ms | 156.910 ms |
| ON | 122.038 ms | 120.908 ms | 141.366 ms |
| ON − OFF | -3.437 ms (-2.74%) | -2.363 ms (-1.92%) | -15.543 ms (-9.91%) |

平均、P50、P95はいずれもONで短かった。サンプル数15かつ端末上のスケジューリングを含むため、この差から高速化を主張することはできない。一方、平均値での遅延増加はなく、Up前に候補生成が開始されないことも各試行で検証しているため、背景Span修正後も変換開始後の明確な性能悪化は検出されなかった。

## プレビュー処理時間とメモリ

Down→Move→Cancelを1ジェスチャとして30回ウォームアップ後、500回連続実行した。時間は3個の同期タッチイベント注入、IME処理、入力先アプリへの更新を含む。割り当て量は ART の `art.gc.bytes-allocated`、PSSは `Debug.getPss()` で取得した。

| 条件 | 1ジェスチャ平均 | 一時割り当て/ジェスチャ | PSS開始 | PSS直後 | GC後PSS |
|---|---:|---:|---:|---:|---:|
| OFF | 50.879 ms | 71,157 bytes | 231,947 KB | 264,615 KB | 243,903 KB |
| ON | 73.977 ms | 103,082 bytes | 234,181 KB | 255,181 KB | 239,333 KB |
| ON − OFF | +23.098 ms (+45.40%) | +31,925 bytes (+44.87%) | +2,234 KB | -9,434 KB | -4,570 KB |

時間と割り当て量の増加は、ON時だけ Down、Move、Cancelで装飾済みSpannableを生成して `InputConnection.setComposingText()` を実行し、入力先アプリとの同期処理および一時オブジェクト生成が追加されるためである。この51–75 msは1回のコールバックそのものではなく、UiAutomationによる3イベントの同期注入を含む試験全体の壁時計時間であり、そのままユーザーが感じる遅延とは解釈できない。

背景Span修正後の一時割り当て増加は1ジェスチャあたり31,925 bytesだった。一方、最終試験のGC後PSSはONの方が4,570 KB低かった。測定順序はOFF→ONで固定され、JIT、ヒープ拡張、GCタイミングの影響を受けるため、ONでメモリが減るとは判断しない。結論は「装飾を含むプレビュー操作ごとの一時割り当ては約32 KB増えるが、保持メモリの増加は今回の試験では検出されなかった」である。

## 検証コマンド

```text
./gradlew :core:testDebugUnitTest :app:testLiteStandardDebugUnitTest \
  :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest

./gradlew :app:connectedLiteStandardDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.FastInputMatrixInstrumentedTest#flickEditorPreviewFunctionalAndPerformanceOnPhysicalDevice
```

単体テスト、APK／instrumentation APKの組み立て、Pixel 6のinstrumentation testはいずれも成功した。

## 制約

- 物理端末はPixel 6の1機種、デバッグビルドのみ。
- 候補表示時間は各条件15サンプルの探索的測定であり、正式なMacrobenchmarkではない。
- 自動機能試験は通常表示で実施した。フローティング表示は同じプレビューリスナーへ接続し、コンパイル対象として検証したが、今回の実機自動操作マトリクスには含めていない。
- PSS比較は単一プロセス内かつOFF→ONの固定順であり、小さい保持メモリ差を判定する用途には不十分。
