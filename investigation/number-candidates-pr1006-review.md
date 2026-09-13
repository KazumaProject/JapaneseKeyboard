# PR #1006 最終レビュー・検証記録

検証日: 2026-09-13。実装コミット: `70f119f3d`。統合したdev: `a8df834f1`。
作業はPR作成元の `MarkdownHelperKeyboard-number-candidates-all-modes` worktreeで実施。

## 修正内容

- 読みからの数字候補生成を、PredictionConfig・未保存設定・新旧設定画面すべてで初期ONに統一。保存済みON/OFFは変更しない。
- 直接数字入力の候補、辞書の正常な数字表記、通常語、不正候補検証は維持。
- `java.time.Month` の2箇所をAPI 24対応の共通判定へ置換。年なし候補は2月29日を許可し、4月31日などを拒否。
- devとの競合を解消し、物理キーの候補情報保持と `fromPhysicalKeyboard = true` の両方を維持。
- dev由来のComposingGuideWindowのLint NewApi 7件を解消。API固有値をSDK分岐内でRectへ移し、計算結果は維持。

## 修正前の失敗と切り分け

- 未保存の設定と画面の初期値が一致すること、保存済みON/OFFを維持することを回帰検査。最終仕様は初期ON。
- API 24で旧APKの `101` 入力から `NoClassDefFoundError: java.time.Month` を再現。同じ日付テストは修正後に成功。
- 数字生成ONを暗黙の初期値に依存していた既存テストはONを明示。期待する候補・確定文字列は変更していない。
- API 24の負荷検査でARTの `Tried to mark 0xebadde09` を再現。ログのinvalid rootは検査関数 `JapaneseNumberConversionInstrumentedTest.measureConversions` のコルーチンフレームだった。検査ループを同期フレームにして1クエリごとにrunBlockingを完結させ、同一本体APKで成功。入力・期待値・反復数は維持。計測値にはrunBlockingの費用を含む。
- Pixel初回のUI検査は消灯、API 24のソフトキー検査はハードウェアキーボード設定のため対象キーを発見できず失敗。点灯／キーボード設定を整え、対象検査を再実行して成功。

## 自動検査

| 検査 | 結果 |
| --- | --- |
| 数字・設定・変換セッション・辞書コーパス・ComposingGuide | 121テスト成功、失敗・エラー・スキップ0 |
| 候補順・候補表示・学習・Zenz・文節・グラフ・FindPath等の関連統合検査 | 135テスト成功、失敗・エラー・スキップ0 |
| Lite本体／AndroidTest APK | assemble成功 |
| Full | compileFullStandardDebugKotlin成功 |
| Android Lint | エラー0、警告455件 |
| git diff origin/dev --check | 成功 |

実行タスク: `:app:testLiteStandardDebugUnitTest`（数字・設定等121件と関連135件を別実行）、`:app:assembleLiteStandardDebug`、`:app:assembleLiteStandardDebugAndroidTest`、`:app:compileFullStandardDebugKotlin`、`:app:lintLiteStandardDebug`。

コーパス検査の内訳: 全747,244読み・1,291,771辞書エントリーで正常な辞書語の拒否0。
助数詞等15,726読み×16経路=251,616件、0〜9999の全値×16経路=160,000件、音変化を崩した5,000読み×16経路=80,000件が成功。独立した助数詞不正例78,000件も成功。
新規／未保存設定でON、保存済みON/OFFの維持、設定切替、古い非同期結果、ユーザー登録、通常語、表記順、先頭ゼロ、確定文字列を検査した。

## 端末検査

| 環境 | 検査 | 結果 |
| --- | --- | --- |
| API 24 arm64エミュレーター、最終APK | デフォルトONと数字・日付ON/OFF、通常変換、20,000回の英数カナ／500回の辞書変換 | OK (2 tests) |
| API 24 | 物理キーによる文節・選択・取消・部分確定等 | OK (14 tests) |
| API 24 | 未保存設定OFFの全16経路、通常語選択、不正候補除外 | 76項目成功、OK (1 test) |
| API 24 | 月日の表示・選択確定／不正候補除外 | 6項目成功、OK (1 test) |
| Pixel 6 / API 37 | 全モード・両バックエンド・文節ON/OFF・禁止例・全6優先順 | 299項目成功、OK (1 test) |
| Pixel 6 / API 37、最終APK | 数字・日付ON/OFF、通常変換、同じ負荷検査 | OK (2 tests) |
| Pixel 6 / API 37、最終APK | 明示的OFFの全16経路、通常語選択、不正候補除外 | 76項目成功、OK (1 test) |
| Pixel 6 / API 37、最終APK | 全6優先順の表示・ライブ変換・3表記の選択確定 | 18項目成功、OK (1 test) |

Pixelの既定IME・テスト対象アプリの設定を検査前後で比較し、復元一致を確認。
API 24の既定IMEを復元し、エミュレーターを終了、AVD設定ファイルを元の内容へ復元。

### APKの対応

- 最終本体APK SHA-256: `7dce8017819dec9d5150340dfb90ffe83df90deb653d45000e2cff84dbf3fe97`
- 最終AndroidTest APK SHA-256: `ba8764d2600254ba7ddc1a403ce6ecd8373a615e344938f4b4509ff7af337c3a`
- Pixelの最初の全モード検査APK SHA-256: `2db46d52c5020180f092ca4f0450d726bca95171d43fc69afe4f74ecbbcd761a`

Pixelの全299項目は初期OFF・ComposingGuideWindowのLint整理前のAPKで、生成ONを明示して実施した。API 24の物理キー14テスト・未保存OFF76項目・日付UI6項目は初期OFF版（本体SHA-256: `32bcfc1727011ae1ad3fbf02e0003a8852d0e64a1bca26b1fffaf262b2b19f1e`）で実施した。
最終ON版は初期値3箇所のみを変更しており、生成ONを明示する各変換経路の実装は維持している。最終APKでAPI 24とPixelのエンジン検査、Pixelの明示的OFF76項目・優先順18項目を再実行し、設定未保存時のONは設定テストと端末エンジン検査で確認した。全299項目および物理キー14テストの最終APKでの再実行はしていない。

## 最終レビュー

最新devとの差分全体と関連コードを再確認。解析・助数詞・派生表記、辞書と学習の出自、ユーザー登録の例外範囲、文節結合、固定候補との優先順位、キャッシュ・非同期更新、表示・選択・確定、物理キー、翻訳結果の扱いを確認した。
上記で発見した実装上・検査上の問題を修正し、対象検査を再実行。今回確認した範囲で未解決のレビュー指摘はない。Lintの警告455件は残存する。検証範囲とAPKの対応は上記のとおりで、未実施の組合せまで保証するものではない。
