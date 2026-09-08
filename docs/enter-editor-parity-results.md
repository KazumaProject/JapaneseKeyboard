# Enter 検証結果

## 環境と期待値の由来

- 起点: `dev` の `bbdc7414746adeffc815d5158631e415a1167007`。
- 実機: Pixel 6、Android 17、locale `ja-JP`。
- OS fingerprint: `google/oriole/oriole:17/CP2A.260705.006/15641320:user/release-keys`。
- Gboard: `18.1.3.962075747-release-arm64-v8a`、versionCode `175963030`。
- Gboard は日本語 QWERTY・左寄せ片手表示。本アプリの通常表示／フローティング表示を、この Gboard の観測と比較する。
- キーボードは liteStandardDebug の別 application ID でインストール。通常利用中のアプリの APK・設定データは置換しない。
- 期待値は Gboard の画面上の Enter をタッチして取得した実測ログから生成した。プロダクションの判定関数は検証アプリにも期待値生成器にも依存しない。
- Gboard 全 6,209 行の非圧縮ログ SHA-256: `72dabe93268911e6ca2572651fbd1a0563a909bf58f80e6f6c782bbaeca5a5ec`。

## 実行状況

全検証完了。主行列は通常／フローティングとも、不一致・未実行・実行不能・入力情報不一致・IME 不一致が 0 件。

| 検証 | 状態 |
| --- | --- |
| Gboard の全条件観測 | 6,209 / 6,209 観測済み |
| 本アプリ通常表示 | 6,209 / 6,209 一致。送出方式も全件一致 |
| 本アプリフローティング表示 | 6,209 / 6,209 一致。送出方式も全件一致 |
| 元の dev での不具合再現 | 選択した 4 条件すべてで Gboard との差を実測 |
| dev と修正版の変換・文節回帰比較 | 通常／フローティング × OFF 7 段階／ON 8 段階、計 30 段階一致 |
| レイアウト・表示の補助検証 | 5 レイアウト × 2 表示 × 8 アクション = 80 ケース一致。ラベル・アイコン確認済み |
| TYPE_NULL の明示的直接入力設定 | GO 指定でも既存の Enter キーイベントを送ることを実測 |
| 手動検証画面 | 絞り込み・読み込み・画面上 Enter・結果ダイアログを確認 |
| 実行中の割り込み | 別画面への遷移／IME 変更の両方で送出前に停止することを確認 |

アプリの関連 JVM テストは 45 件、custom_keyboard の関連 JVM テストは 51 件、比較／期待値生成器の Python テストは 13 件が成功した。JVM の判定テストには Gboard の 6,161 行の実測期待値照合を含む。null EditorInfo と未定義アクションの単体テストは防御的な既定動作のテストであり、Gboard 実測による互換性保証とは区別する。

変換回帰では画面上のローマ字キーで入力し、変換前・変換後・各 Enter 押下後・確定終了後を記録した。文節区切り ON の変換後は、最初の Enter 後にも composing span が残り、2 回目でなくなる既存動作を含めて一致した。確定途中に editor action はなく、最後の空入力 Enter が DONE を 1 回送出した。

## 観測から得られた Enter 判定

この Gboard バージョンと宣言した検証条件では、`NO_ENTER_ACTION` がなければ GO / SEARCH / SEND / NEXT / DONE / PREVIOUS の標準アクションを送出した。数値・電話・日時・パスワードなども、入力種類だけで DONE に固定されるわけではなかった。

NONE / UNSPECIFIED、または `NO_ENTER_ACTION` がある場合は Enter の DOWN/UP を送出した。ただしテキストの SHORT_MESSAGE variation は `commitText("\n", 1)` を使用した。この 160 ケースも別の期待値として保存した。

宣言した組み合わせでは `actionLabel` と `actionId` の有無によるキーの送出変更は観測されなかった。ヒントなどのメタデータを検索・パスワード判定に使う既存処理は変更せず、Enter 判定のみを独立させた。

## 差分レビュー

以下を確認し、検証ツール側の問題も修正した。

- Enter の判定元を入力種類の分類から `EditorInfo` に分離し、表示と送出で共通の判定を使う。
- 送出時に最新の `EditorInfo` を読む。入力欄の切り替え後に以前のアクションを保持しない。
- アクションの戻り値に応じた Enter の再送を行わず、二重送出を避ける。比較器も二重イベント・異なる送出方式を失敗にする。
- 変換候補・文節の確定処理とショートカットの照合処理は既存の経路を維持する。
- 共通の直接入力処理を使う強制改行への影響をレビューで発見し、既定 TYPE_NULL の新判定を空入力の通常 Enter に限定した。
- フローティング IME は application window として見える場合もあるため、検証側のキー探索は window type ではなく選択 IME の package を照合する。
- Compose は IME の dump から実際の EditorInfo を取得し、WebView は初期カーソルを末尾にそろえる。古い WebView の非同期コールバックを除外する。
- 別作業による IME 切り替えを検出した実行は停止し、異なる IME の観測を成功扱いにしない。終了時も外部から変更された IME 選択を上書きしない。
- TSV の書き出しは全 raw 条件の正常観測・実 EditorInfo・送出イベントを検査する。欠測や混在した送出を期待値として受理しない。

## 制限

6,209 ケースは検証画面が宣言した行列の全件であり、任意の全アプリ・全ビット列・任意の privateImeOptions 文字列・将来の Gboard バージョンの一致を保証するものではない。主条件は直積で生成し、その他のフラグとメタデータは各アクション／抑制状態に対する独立プローブとした。条件の詳細と再実行方法は [検証画面の説明](enter-editor-parity.md) を参照。

Compose の InputConnection 生イベントは直接捕捉していない。実 EditorInfo、keyboardAction コールバック、文字列、フォーカスを比較する。EditText と WebView は InputConnection の送出イベントも比較する。

キーイベントの比較は action と keyCode に正規化しており、時刻・device ID・flags・modifier state の一致までは検証していない。

キーのラベル・位置は観測ログに保存する。アイコンやテーマのピクセル一致は要件にせず、Enter の意味と送出を確認する。Gboard の変換・文節動作を本アプリの仕様にはしない。


## 保存データと再確認

[`validation-status.json`](../tools/enter_parity/results/validation-status.json) に集計、
[`comparison.tsv`](../tools/enter_parity/results/comparison.tsv) に全ケースの状態を保存した。
実測の全ログは `gboard.jsonl.gz`、`keyboard-normal.jsonl.gz`、`keyboard-floating.jsonl.gz`。
非圧縮時の内容を保持しており、比較器で直接読み込める。環境情報・APK ハッシュ・
変換比較・レイアウト比較・画面キャプチャは同じ results ディレクトリにある。

```sh
python3 tools/enter_parity/compare.py tools/enter_parity/results/gboard.jsonl.gz tools/enter_parity/results/keyboard-normal.jsonl.gz /tmp/normal-comparison.json
python3 tools/enter_parity/compare.py tools/enter_parity/results/gboard.jsonl.gz tools/enter_parity/results/keyboard-floating.jsonl.gz /tmp/floating-comparison.json
```

中断した通常表示の異なる IME の 55 行、Gboard の初期接続失敗 1 行、フローティングでの Compose 初期情報 3 行、元の dev のラベルなし数値キー取得失敗 2 行、入力開始前提が異なった変換試行の 8 行は `rejected-observations.jsonl.gz` に分離した。これら 69 行は期待値・合格数に含めない。

通常表示は中断前の 4,007 件と再実行分 2,202 件を統合した。TYPE_NULL の空入力制限を確定した後の再実行分に、すべての TYPE_NULL 条件を含む。最終の製品 APK ではフローティングの全件とレイアウト別・変換回帰も確認した。フローティングの Compose は空の EditorInfo を検出した後、安定した値を待つようにし、16 件すべてを再取得した。変換比較ではキー位置と各文字入力のコールバックも待ち、Enter 前の状態が異なる実行を採用しなかった。

実機は検証開始前に選択されていた IME と元のアニメーション設定に戻した。
最終差分レビューで未解消の問題はない。
