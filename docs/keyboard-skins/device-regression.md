# PR #992 修正版の実機検証

## 結果

Pixel 6 の本番 IME で修正版の全８条件が成功しました。テンキー・QWERTYで
デフォルト／クパチーノ ライト／クパチーノ ダークとデフォルトへの復帰を操作し、
56 組の操作シーケンス、476 画面を記録しました。
クパチーノの候補表示、ナビゲーションとの境界、記号入力・通常入力への復帰に
今回の検証範囲で未解決の不具合はありません。

デフォルトの基準は PR 前の `bbdc7414746adeffc815d5158631e415a1167007` です。
同じ実機で基準 APK の６条件・102 画面も再測定しました。
候補欄や記号画面の位置・高さの比較238件はすべて一致しました。
デフォルトにも高さ補正が必要だったという以前の説明は取り消しています。

## 条件

- Pixel 6、Android 17 / SDK 37、1080 × 2400 px、420 dpi。
- 隔離アプリ `com.kazumaproject.skinfidelity.lite` の本番 `IMEService` を使用。
  エミュレーターや録画用キーボード表示を実機 IME の代用にしていません。
- 候補２列、候補高さは未入力60 dp／入力中110 dp。
  高さ変更条件は90 dp／150 dp。アプリの通常設定値を変更する修正はありません。
- 通常アプリを上書きせず、テスト後は元の入力方法と３ボタンナビへ復元済み。
- 作業・ビルドは元の `MarkdownHelperKeyboard-keyboard-skins` worktree で実施。

| 条件 | キーボード | スキン順 | 結果 |
| --- | --- | --- | --- |
| ３ボタン・縦 | テンキー、QWERTY | Default → Light → Dark → Default | PASS |
| ３ボタン・横 | 同上 | 同上 | PASS |
| ３ボタン・候補高さ変更 | 同上 | 同上 | PASS |
| ３ボタン・フローティング | 同上 | 同上 | PASS |
| ジェスチャー・縦 | 同上 | 同上 | PASS |
| ジェスチャー・横 | 同上 | 同上 | PASS |
| ライトで初回起動 | 同上 | Light → Default | PASS |
| ダークで初回起動 | 同上 | Dark → Default | PASS |

各シーケンスで未入力、文字入力、候補操作、フリック／長押し、削除、
絵文字、記号カテゴリ、通常入力への復帰を操作しています。
記号タブの選択アイコンも画面の画素で可視性を検査しています。
加えて実機のポップアップ３テストが成功しました。短押しの確定、長押し中の方向選択、
指を離した後とビュー破棄時のウィンドウ解放・文字色復元を確認しています。

## デフォルトの画像比較

未入力・入力中・確定後・フリック／長押し後・削除後・通常入力への復帰の
168 画面でキーボード領域を比較しました。フローティングは候補欄から操作バーまで含みます。
162 件は、PR 前に撮影したいずれかの同条件画像と領域全体が完全一致しました。

残る６件は、ジェスチャー横画面の QWERTY の削除アイコンにだけ主比較との差がありました。
同じ PR 前 APK の再撮影でも、ベクターアイコンのラスタライズに差が発生しています。
この６件は、キー本体が PR 前の別撮影と完全一致し、候補欄を含む残りの領域が
主比較画像と完全一致することを確認しました。差分を無視するマスクや許容誤差は使っていません。
ただし、この６件を「単一の基準画像と全領域が一致した」とは扱っていません。

[比較結果 JSON](evidence/device-regression/default-comparison.json) に
主画像との差分矩形、使用した基準画像、領域を分けた６件を記録しています。
比較コードは [`verify_default_device_layout.py`](../../tools/keyboard-skins/verify_default_device_layout.py) です。
一部の旧基準 JSON にないフローティングの座標は、同時保存したノード一覧から読み取ります。

## ビルド・単体テスト

修正版の APK と instrumentation APK のビルドが成功しました。
関連単体テスト406件（app 129、core 30、custom_keyboard 194、tenkey 6、
qwerty_keyboard 34、gojuon_keyboard 1、symbol_keyboard 12）は失敗・エラー・スキップ0です。
[スイート別結果](evidence/device-regression/unit-test-summary.json) を保存しています。

APK の SHA-256:

| APK | SHA-256 |
| --- | --- |
| PR 前 | `86db959e31c0ea85a28656ee640608595dfb4293d9ca76320033115ddbce683d` |
| 修正版 | `affddc8ae05503e52deb098b01c95f820e8fa22153d2befb1ff12f4ba2f384df` |
| 修正版テスト | `10c252ce402cf57a4961c4fea90b9892cc02d1d1b45b383855e5b8f309d83bda` |

[metadata.json](evidence/device-regression/metadata.json) には本番変更ファイルのハッシュも記録しています。
検証後に本番コードを変更していません。

## 証拠と再実行

[evidence/device-regression/](evidence/device-regression/) に測定値、実機ログ、
代表画像13枚、全撮影画像の SHA-256 一覧を保存しました。
全 PNG のローカル保存先は `/tmp/pr992-evidence/skin-regression` です。
リポジトリには全 PNG を含めていないため、比較コードの全件再実行にはこの保存先か再撮影結果が必要です。

```sh
adb -s DEVICE_SERIAL shell am instrument -w -r \
  -e class com.kazumaproject.markdownhelperkeyboard.SkinRegressionDeviceTest \
  -e label final-threebutton-portrait \
  com.kazumaproject.skinfidelity.lite.test/androidx.test.runner.AndroidJUnitRunner
```

横画面は `-e rotation landscape`、高さ変更は `-e layoutSize large`、
フローティングは `-e floating true`、初回起動は
`-e skins cupertino_light,default` または `cupertino_dark,default` を追加します。
ナビゲーションモードは各条件に合わせて端末側で切り替えます。
PR 前は同じテストに `-e skins default` を指定します。

旧エミュレーター資料や以前の iOS 比較を、最新版で再実行した実機検証とは扱いません。
この結果は上記の端末・設定での確認であり、すべての端末・保存テーマの組み合わせを
実機で網羅したという意味ではありません。
