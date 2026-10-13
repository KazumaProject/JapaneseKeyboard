# 変換候補欄の高さ安定化：実装と検証

2026-10-02。`origin/dev` の `5642c71bb` を起点に、新しい worktree の
`codex/stabilize-candidate-strip` で実装。versionCode / versionName は変更していない。

## 動作

新旧「共通」画面で、同じ `stabilize_candidate_strip_height_preference` を使用する。
初期値は OFF。設定検索・よく使う設定・設定スナップショット・バックアップにも対応。

ON 時は、現在の画面方向・列数について、次の合計高さの大きい方を確保する。

- 未入力時の候補欄＋有効な独立ショートカットツールバー
- 入力時の候補欄＋有効な候補タブ

キーボード本体・ナビゲーション用余白・下マージンは従来の計算に従う。
表示内容自体の高さは変えず、差分を透明にする。
`contentTopInsets` / `visibleTopInsets` は確保領域の上端で固定し、
タッチ領域は表示中のキーボードとアプリ所有のオーバーレイのみから構成する。
高さや画面方向の設定変更時には再計算する。

フローティング・物理キーボード・候補の別ウィンドウ・全画面・記号画面の
既存分岐を維持。これらのモードは高さ安定化の対象外。

## 検証結果

- 関連単体テスト：53 件、失敗 0。
- `assembleFullStandardDebug` / `assembleFullStandardDebugAndroidTest`：成功。
- 実際の IME を選択し、文字入力→変換→確定→削除で入力欄の矩形を測定。
- ON の各状態で矩形が同一。OFF では従来の高さ変化を確認。
- 透明領域をタップし、入力先 Activity の `dispatchTouchEvent` に届くことを確認。

| AVD API | 画面 | ケース | 結果 |
| --- | --- | --- | --- |
| 24 | 縦 | 8 | 成功 |
| 29 | 縦 | 8 | 成功 |
| 30 | 縦 | 8 | 成功 |
| 36 | 横、実際の画面回転を確認 | 8 | 成功 |

ケースは、1 列＋ツールバー 72dp＋タブ、2 列でツールバーなし、3 列＋タブ、
未入力 120dp が入力中 60dp より高い場合、統合ツールバー、Cupertino Classic、
片手幅 70%、安定化 OFF。横画面では本体高さ 180dp を使用した。
Android 36 の縦画面でも、初期の 7 ケースを確認済み。

例：Android 30 のツールバーなし 2 列では、入力欄の下端は全状態で 1455px。
OFF の 3 列では、未入力 1508px → 入力中 1403px → 確定後 1508px。
Android 36 の横画面で同じ OFF 条件は 325px → 220px → 325px。

API 24・29・30 の初回実行では IME キー取得待機がタイムアウトした。
再実行は同じ変更を含む APK で成功。初回失敗は成功として数えていない。
回転設定のみの試行では実際の画面が縦のままだったため、横画面の結果から除外した。
最終テストは Activity が要求した向きと実際の Configuration を照合する。
接続中の Pixel 6 実機は操作・検証していない。対象外モードの全デバイス試験は未実施。

## 再実行

使い捨ての AVD を起動し、初期化が完了した状態で実行する。
テストは選択 IME・有効状態・設定・アクセシビリティフラグを保存して復元する。
テスト画面は debug 専用で、通常のテスト実行ではこのデバイス試験はスキップする。

```sh
./gradlew :app:testFullStandardDebugUnitTest \
  --tests '*CandidateStripPresentationPolicyTest' \
  --tests '*DockedCandidateInsetsTest' \
  --tests '*CandidateHeightStabilizationSettingsTest' \
  --tests '*CandidateStripLayoutPolicyTest' \
  --tests '*ShortcutToolbarPresentationPolicyTest'
./gradlew :app:assembleFullStandardDebug :app:assembleFullStandardDebugAndroidTest
adb -s SERIAL install -r app/build/outputs/apk/fullStandard/debug/app-full-standard-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/fullStandard/debug/app-full-standard-debug-androidTest.apk
adb -s SERIAL shell am instrument -w \
  -e class com.kazumaproject.markdownhelperkeyboard.CandidateHeightStabilizationDeviceTest \
  -e candidate_height_device_test true \
  -e candidate_height_rotation 0 \
  com.kazumaproject.markdownhelperkeyboard.test/androidx.test.runner.AndroidJUnitRunner
```

横画面は `candidate_height_rotation 1` を指定する。
矩形の測定値は logcat の `System.out` に `CandidateHeight` として出力する。
今回の生ログは worktree 内 `build/reports/candidate-height-stabilization/` に保存。
API 24 の logcat はリングバッファにより前半の測定ログが失われているが、最終テスト結果は保存済み。
単体テスト結果は `app/build/reports/tests/testFullStandardDebugUnitTest/` を参照。
