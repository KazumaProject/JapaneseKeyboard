# PR #1098 検証結果（2026-10-01）

修正版は **56/56ケース、各20周期、合計1,120周期**で成功した。動画解析と証拠監査を含む結果であり、入力テストの完走だけを成功とはしていない。未解決の失敗は0件。UMIDIGI power5は未接続のため未検証。

作業・親コミットの比較ビルド・修正・最終ビルドはすべて `/Users/kazuma/.codex/worktrees/issue-1084-repro/MarkdownHelperKeyboard-image-button`、既存ブランチ `codex/issue-1084-toolbar-flicker` で実施した。

## 対象と実行結果

| 端末 | API | 通常ON/OFF | 拡張設定を含む合計 | 周期数 |
|---|---:|---:|---:|---:|
| Pixel 6実機（Android 17） | 37 | 2/2 | 24/24 | 480 |
| Android 11 AVD | 30 | 2/2 | 24/24 | 480 |
| Android 7.0 AVD | 24 | 2/2 | 2/2 | 40 |
| Android 10 AVD | 29 | 2/2 | 2/2 | 40 |
| Android 13 AVD | 33 | 2/2 | 2/2 | 40 |
| Android 16 AVD | 36 | 2/2 | 2/2 | 40 |

通常設定はTenKey、ライブ変換OFF、候補あり・なしとも60dp、独立ツールバーON/OFF。各周期で実際のキーをタップして「か入力→変換→Enter確定」を行い、文字列、未確定スパン、IMEの変換開始・終了、確定文字列を確認した。最終文字列は全ケースで「か」20文字。

Pixel 6とAPI 30の各24ケースは以下の組み合わせを含む。重複する通常設定は1回として数えた。

- 独立ツールバーON：候補タブON/OFF × 高さ32/36/72dp × 縦横表示。
- 独立ツールバーOFFおよび統合表示：候補タブON/OFF × 縦横表示。
- フローティング：縦横表示。
- 候補なし60dp／候補あり80dp：縦横表示。

関連単体テスト48件は全件成功。アプリAPKとAndroidテストAPKのビルドも成功。専用applicationIdへの変更に伴うフォントProviderのAndroid実行テスト3件はAPI 36 AVDで全件成功した。アプリのバージョン（1.7.124 / 817）と公開APIは変更していない。

## 比較で見つかった問題と修正

親コミット `93922ed2168651339bdb71ef2c7e927188de1f67` を同じWorktreeで一時的にビルドし、Pixel 6とAPI 30でON/OFF各20周期を実行した。その後PRブランチに戻した。ONでは両端末で入力ビューの高さが860↔954pxに変化した。API 30ではキーボード全体が消える34フレームを動画から検出し、実際の画像でも確認した。Pixel 6では高さ変化を検出したが、動画上の消失は検出しなかった。

元のPRの通常ON設定は高さ954pxで一定だった。しかしAPI 30の拡張設定には消失が残っていた。

| API 30の設定 | 比較版 | 入力ビュー高さ(px) | 消失フレーム | 最終修正版の消失 |
|---|---|---|---:|---:|
| 通常ON | 親コミット | 860 / 954 | 34 | 0 |
| 通常ON | 元のPR | 954 | 0 | 0 |
| 独立OFF・候補タブON・縦 | 元のPR | 860 / 955 | 38 | 0 |
| 候補高さ60→80dp・縦 | 元のPR | 954 / 1007 | 36 | 0 |

今回の修正では、候補サイズが変わっても描画用のIMEウィンドウを設定上の最大サイズに保つ。入力ビューは設定どおり増減させ、下端に配置することでキーの位置を維持する。アプリに伝わるIME Insetsは実際の入力ビューの高さに追従する。フローティング、物理キーボード、分割表示では通常のウィンドウ方針に戻す。余白を縮める仕様変更は行っていない。

最終テストは通常設定の入力ビュー高さと、ドッキング時のウィンドウ高さ・キー位置を連続サンプルで確認した。高さ変更設定では意図した入力ビューの増減だけを許容し、API 30以降では入力先Activityが受け取るIME Insetsも確認した。

## 証拠と判定

検証した製品コードとテストAPKのソースは `f2390928aa14866ec6db2f9db813785b30bb95bc`。以降の変更は動画解析・監査・報告用ファイルのみ。

| APK | SHA-256 |
|---|---|
| 親コミットの比較アプリ | `ceb4c5a623652d99ef025953e7144313a453c596334b9772dd3413f111e3d2bb` |
| 元のPRの比較アプリ | `3313cebecc79987e02fbe33515a34b2c0f9bf0c9b364bf7f96524235ebb43f3f` |
| 最終修正版アプリ | `d1db063b1ecb7f257f8eb7f1ebdbca2de798a09ee727ffa01e71cd528c921860` |
| 最終Androidテスト | `3b8ddf34711bf576af40aa91fdacf13a19e3c6979ec325568f4b7de6802da396` |

ローカル証拠はWorktree内の `build/reports/toolbar-flicker/` に保存した。大容量の動画・ログはGitへ追加していない。

- `final-summary.json`：56ケースの結果、ソースコミット、APKハッシュ、表示サンプル数、動画フレーム数、高さ、記録間隔。
- `evidence-sha256.json`：最終ケースの各証拠ファイルのSHA-256。
- `<serial>-api<API>/<label>/metadata.json`：端末fingerprint、設定・コマンド、APKハッシュ、元の端末設定と復元値。
- 各ケースの `screen.mp4` または `screen.webm`、`logcat.txt`、`instrumentation.log`、`video-frames.csv`、`video-analysis.json`。
- 各ケースの `toolbar-regression/<label>/result.json`：端末uptimeで対応付けた操作・変換状態・確定結果と、vsync／描画直前の表示状態。
- 同ディレクトリの起動前後PNG、ウィンドウ情報、InputMethod情報。
- `before/`：親コミット、元のPR、初期検証の証拠。`apks/`：比較用と最終APK。
- `toolbar-final-build.log` はWorktree内 `build/` に保存。フォントProvider結果は `local-font-api36.txt`。

最終動画は解析バージョン4で全61,418フレームを確認し、消失0件。表示状態135,126サンプルでも消失0件。最大記録間隔は動画73ms、表示状態84ms。証拠監査は100msを超える記録間隔、開始・終了の欠落、タイムアウト、APK不一致、記録不足、設定復元不一致を成功に数えない。

`final-summary.json` のSHA-256は `2ee32a27d161bce8960a4add1d95223f7dadc23b575f7d9aca9dee2e90c96f64`。`evidence-sha256.json` は `11bb4f7ed70a99d4c12a15c7e313325d13ac6ea3114f5ee937b350ba8abc7f51`。

初期検証では、XML寸法の丸め（36dpが95px）を94pxと比較した期待値の誤り、ウィンドウ固定の試作でInsetsが実寸に追従しない不具合、Pixel 6横向きフローティングの動画校正失敗を失敗として保存した。丸めとInsetsは修正後に全ケースを再実行した。動画校正失敗は低解像度・間引きによるキー文字の消失だったため、解析解像度を上げ、全録画を再解析した。同じ解析方法で親コミットの実際の消失34フレームを引き続き検出できた。初期Pixel行列ログの23/24は校正失敗を含む当時の結果であり、最終判定は56/56の証拠監査に基づく。

各ケースで既定IME・有効IME・回転設定を復元した。Pixel 6では検証全体の開始前スナップショットとの完全一致も確認した。通常のユーザーAPKを検証用APKで置き換えていない。

## 再実行

Java 17、Android SDK、接続端末または起動済みAVDを使用する。専用ID `com.kazumaproject.toolbarqa` をinit scriptで設定し、通常APKをインストールしない。ABI指定時のAPKは `outputs/apk` ではなく `intermediates/apk` に生成される場合があるため、runnerがapplicationId付きメタデータから選択し、aaptでIDを検証する。

```sh
cd /Users/kazuma/.codex/worktrees/issue-1084-repro/MarkdownHelperKeyboard-image-button
export ANDROID_HOME=/Users/kazuma/Library/Android/sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME=/Users/kazuma/Library/Java/JavaVirtualMachines/jbr-17.0.12/Contents/Home
./gradlew -I investigation/toolbar-flicker.init.gradle \
  :app:testFullStandardDebugUnitTest \
  --tests '*CandidateStripPresentationPolicyTest' \
  --tests '*CandidateStripLayoutPolicyTest' \
  --tests '*ShortcutToolbarPresentationPolicyTest' \
  :app:assembleFullStandardDebug :app:assembleFullStandardDebugAndroidTest \
  -Pandroid.injected.build.abi=arm64-v8a -x :app:generateMozcSegmenterData --console=plain
python3 -m venv build/toolbar-tools
build/toolbar-tools/bin/pip install -r investigation/toolbar-regression-requirements.txt
build/toolbar-tools/bin/python investigation/run_toolbar_matrix.py <Pixelのserial> --extended
build/toolbar-tools/bin/python investigation/run_toolbar_matrix.py emulator-5580 --extended
build/toolbar-tools/bin/python investigation/run_toolbar_matrix.py emulator-5582
build/toolbar-tools/bin/python investigation/run_toolbar_matrix.py emulator-5584
build/toolbar-tools/bin/python investigation/run_toolbar_matrix.py emulator-5586
build/toolbar-tools/bin/python investigation/run_toolbar_matrix.py emulator-5588
```

APKの自動選択で再実行した場合、監査が参照する `apks/final-app.apk` と `apks/final-test.apk` に、その実行に使ったAPKを保存する。再実行で既存証拠を上書きしないよう、現在の `build/reports/toolbar-flicker/` を先に別名へ退避する。今回の証拠だけを再監査するコマンドは次のとおり。

```sh
python3 investigation/audit_toolbar_evidence.py \
  --source-commit f2390928aa14866ec6db2f9db813785b30bb95bc \
  --pixel-serial <今回のPixelのserial>
```

新しい実行の監査では、その実行時のクリーンなコミットを `--source-commit` に指定する。各ケースだけの再実行は `run_toolbar_regression.py <serial> <新しいlabel> --cycles 20` に `--mode off`、`--tab`、`--height 32`、`--landscape`、`--floating`、`--candidate-height 80` 等を追加し、続けて `analyze_toolbar_video.py <証拠ディレクトリ>` を実行する。

AVDは以下を使用した。必要に応じて `emulator -avd <名前> -sysdir "$ANDROID_HOME/system-images/<イメージ>" -port <ポート> -no-snapshot -no-window -no-audio -gpu host` で起動する。古いAPIで端末内screenrecordが使えない場合にも、runnerはエミュレータのホスト録画を使う。

| API | AVD名 | ポート | SDKイメージ |
|---:|---|---:|---|
| 24 | issue1084_api24 | 5582 | android-24/default/arm64-v8a |
| 29 | issue1084_api29 | 5584 | android-29/default/arm64-v8a |
| 30 | issue1084_android11 | 5580 | android-30/google_apis/arm64-v8a |
| 33 | issue1084_api33 | 5586 | android-33/google_apis/arm64-v8a |
| 36 | issue1084_api36 | 5588 | android-36/google_apis_playstore/arm64-v8a |

親コミットの比較を繰り返す場合は、PRのテストAPKを保存したうえで、同じWorktreeを一時的に親コミットへ切り替え、上記のinit scriptを保存してアプリAPKだけをビルドする。比較アプリと保存したPRテストAPKをrunnerの `--app-apk` / `--test-apk` に渡す。ONは `--expect-height-change` を指定し、OFFは指定しない。終了後は必ずPRブランチに戻す。init scriptやrunnerは親コミットに含まれないため、切替前にWorktree内の `build/` に保存して使う。

## 未検証範囲

UMIDIGI power5実機での解消は断定しない。録画と表示サンプルの間にだけ起きる消失を完全に排除することもできない。検証範囲は上記の端末・TenKey・設定であり、全スキン、物理キーボード、分割表示、抽出編集などの網羅検証は行っていない。
