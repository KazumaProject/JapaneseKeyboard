# Inline Autofill QA 実行手順

この手順では、Android の Inline Suggestions と Sumire の候補欄を実機で確認します。
`fullStandardDebug` の本体 APK に含まれるデバッグ専用 AutofillService と、
`fullStandardDebugAndroidTest` の QA 用ログイン画面を使用します。

> この QA は Android 11（API 30）以降で実行してください。表示されるアカウント情報はすべて架空のテストデータです。

## 1. 前提条件

- リポジトリのルートでコマンドを実行する
- Android SDK、JDK、Gradle Wrapper が利用できる
- USB デバッグを有効にした Android 11 以降の実機、またはエミュレーターが接続されている
- `adb devices -l` に確認対象の端末が表示される

```bash
adb devices -l
```

複数の端末が表示される場合は、以降のコマンドに `-s <serial>` を追加して対象端末を固定してください。

## 2. APK をビルドしてインストールする

本体 APK と QA 用 test APK をビルドします。

```bash
./gradlew \
  :app:assembleFullStandardDebug \
  :app:assembleFullStandardDebugAndroidTest
```

生成された 2 つの APK を端末へインストールします。

```bash
adb install -r app/build/outputs/apk/fullStandard/debug/app-full-standard-debug.apk
adb install -r app/build/outputs/apk/androidTest/fullStandard/debug/app-full-standard-debug-androidTest.apk
```

役割は次のとおりです。

| APK | 役割 |
|:--|:--|
| `app-full-standard-debug.apk` | Sumire 本体、IME、デバッグ用 `DebugInlineAutofillService` |
| `app-full-standard-debug-androidTest.apk` | QA 用 `InlineAutofillLoginActivity` |

デバッグ用 AutofillService は `src/debug` にのみ存在するため、Release APK ではこの手順を実行できません。

## 3. Sumire とデバッグ用 AutofillService を選択する

Sumire を有効化して、現在の IME に設定します。

```bash
adb shell ime enable com.kazumaproject.markdownhelperkeyboard/.ime_service.IMEService
adb shell ime set com.kazumaproject.markdownhelperkeyboard/.ime_service.IMEService
```

デバッグ用 AutofillService を選択します。

```bash
adb shell settings put secure autofill_service com.kazumaproject.markdownhelperkeyboard/.autofill.DebugInlineAutofillService
```

設定結果を確認します。

```bash
adb shell ime list -s
adb shell settings get secure autofill_service
```

`settings get` の結果が次の値になっていれば、デバッグ用 AutofillService が選択されています。

```text
com.kazumaproject.markdownhelperkeyboard/.autofill.DebugInlineAutofillService
```

また、Sumire の設定画面で **共通設定 → インライン候補 → インライン候補（自動入力）を使用** が ON になっていることも確認してください。

## 4. QA 画面を起動する

端末がスリープしている場合は先に起こします。

```bash
adb shell input keyevent KEYCODE_WAKEUP
```

QA 用ログイン画面を起動します。

```bash
adb shell am force-stop com.kazumaproject.markdownhelperkeyboard.test
adb shell am start -n com.kazumaproject.markdownhelperkeyboard.test/com.kazumaproject.markdownhelperkeyboard.qa.InlineAutofillLoginActivity
```

画面には「ユーザー名」と「パスワード」の入力欄が表示されます。起動後、次の動作を確認します。

1. 3 秒ほど待つとパスワード欄が選択され、Sumire が表示される。
2. inline 候補欄に `🔐 個人アカウント`、`🔐 仕事用` などの架空候補が表示される。
3. 候補欄左端の切り替えアイコンをタップすると、inline 候補が通常の変換候補欄へ切り替わる。
4. 同じアイコンをもう一度タップすると、inline 候補欄へ戻る。

候補が表示されない場合は、ユーザー名またはパスワードの入力欄を一度タップしてから数秒待ってください。

## 5. スクリーンショットとログを取得する

表示確認用のスクリーンショットをホスト側へ保存します。

```bash
adb exec-out screencap -p > /tmp/inline-autofill-qa.png
```

デバッグ用 AutofillService と IME のログを確認します。

```bash
adb logcat -d -t 300 | rg 'SumireInlineAutofillQA|IMEService|Inline'
```

特定タグをリアルタイムで見る場合は、別のターミナルで次を実行します。

```bash
adb logcat -s SumireInlineAutofillQA:D IMEService:D '*:S'
```

正常時は、デバッグ用サービスが 4 件の Dataset を返し、IME が inline suggestion view を描画したログが確認できます。

## 6. トラブルシューティング

### inline 候補が表示されない

次を順番に確認します。

```bash
adb shell settings get secure autofill_service
adb shell ime list -s
adb shell cmd autofill reset
```

- 端末が Android 11 以降であること
- `DebugInlineAutofillService` が選択されていること
- Sumire が現在の IME であること
- QA 画面の入力欄をタップしてフォーカスを移していること
- Sumire の「インライン候補（自動入力）」設定が ON であること

設定を変更した後は、QA 画面を再起動してください。

### スクリーンショットが黒い

端末がスリープ中の可能性があります。

```bash
adb shell input keyevent KEYCODE_WAKEUP
```

その後、QA 画面を再起動して撮影します。

### インストール先のパッケージが合わない

この手順は `fullStandardDebug` 専用です。`lite` や `fdroid` をビルドした場合は、APK の出力先とパッケージ名に suffix が付くため、上記の component 名はそのまま使用できません。

## 7. 端末を元に戻す

QA 終了後、デバッグ用 AutofillService と Sumire の選択を解除します。

```bash
adb shell cmd autofill reset
adb shell settings delete secure autofill_service
adb shell ime reset
adb shell am force-stop com.kazumaproject.markdownhelperkeyboard.test
```

`settings put secure autofill_service` の実行前に別の AutofillService を使っていた場合は、削除する代わりに元の component 名を再設定してください。

## 関連ファイル

- `app/src/debug/java/com/kazumaproject/markdownhelperkeyboard/autofill/DebugInlineAutofillService.kt`
- `app/src/androidTest/java/com/kazumaproject/markdownhelperkeyboard/qa/InlineAutofillLoginActivity.java`
- `app/src/debug/res/xml/debug_inline_autofill_service.xml`
