# Dynamic Orbit β

## 日本語

設定のキーボード一覧から **Dynamic Orbit β** を追加します。既存の一覧・順序は自動変更しません。

1. 入力面の枠内に指を置きます。その位置が、指を離すまで変わらない中心です。
2. 外側へ移動して行を選び、さらに外へ進んで母音を選びます。
3. 母音リング内を円周方向へ動くと母音を変更できます。内側へ戻ると母音、行の順に取り消せます。
4. 最外周を越えるとかな1文字を読みへ追加します。指を中心へ戻すと、同じストロークで次の文字を入力できます。
5. 指を離すと終了します。境界前のプレビューは入力されません。

行は上から時計回りに「あ・か・さ・た・な・は・ま・や・ら・わ」。母音は左下「あ」、左上「い」、上「う」、右上「え」、右下「お」です。反対側の母音は中心を横切らず、円周に沿って選択します。

や行は「や・無効・ゆ・無効・よ」、わ行は「わ・を・ん・ー・無効」。濁点・小文字専用ジェスチャーはありません。必要な場合は他のキーボードへ切り替えて編集します。

Command Bar の Space は変換／空白、Delete は削除、Enter は確定／アプリのEnter操作です。Cursor は方向選択、Mode は言語・記号・キーボード・Android入力方法の切り替えです。英語と数字は既存のQWERTY画面を利用し、かなへの切り替えキーからOrbitへ戻れます。

### 元の構想から変更した点

確定地点へ中心を移す方式は、繰り返し入力で画面端へ到達するため採用していません。共通中心へ戻る循環型にし、中心の周囲に必要な余白を取れる開始領域を表示します。フロートガイドの位置補正は表示だけに適用し、判定方向や中心を変えません。

初期値（すべてdp）：行選択24／解除14、母音選択44／行へ戻る36、入力72、無効方向で外周を越えた後の再準備64。開始領域の余白80、Command Barの高さ48、表示の最小幅240・最小高さ256です。既存のサイズ設定の保存値には影響しません。

### 検証と限界

入力開始時に、そのストロークだけ [unbuffered dispatch](https://developer.android.com/reference/android/view/View#requestUnbufferedDispatch(android.view.MotionEvent)) を要求します。Android の座標予測による外周の架空の通過を防ぐためです。判定器は時刻に依存せず、同じ軌跡を複数のMOVEに分けても履歴付きイベントとして受け取っても境界を処理します。入力後の戻り道で追加の文字を出しません。Orbit内部の「入力」はかなをcomposingへ追加する意味で、漢字変換やアプリへの確定は既存IME処理が担当します。

このβ版は人による入力速度の優位性を実証したものではありません。半径76dpまでの標準往復軌跡は47文字・記号すべてで構成でき、平均移動距離は約231dp／文字です。自動ストローク試験の結果を人の入力速度とは見なしません。

再現用の実機テストは、普段のアプリを上書きしない専用applicationIdで実行します。

```sh
./gradlew :app:testLiteStandardDebugUnitTest --tests '*dynamic_orbit.*'
./gradlew -I investigation/dynamic-orbit.init.gradle :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest
# 上記APKを実機へインストールしてから、接続対象を明示して実行します。
adb -s DEVICE_SERIAL shell am instrument -w -r -e class com.kazumaproject.markdownhelperkeyboard.DynamicOrbitDeviceTest com.kazumaproject.dynamicorbittest.lite.test/androidx.test.runner.AndroidJUnitRunner
```

### 実装時の検証記録（2026-09-13）

- Lite Standard Debug の通常ビルドと、専用applicationIdの実機検証ビルドが成功。
- 対象の単体・回帰テスト120件が成功。47×47の全順序付き文字対、反復入力、方向変更、無効スロット、履歴MOVE、領域外DOWN、UP／CANCEL、非表示化、複数ポインターを含みます。
- Pixel 6（API 37）で実際のIMEウィンドウへタッチイベントを注入。通常、フロート、候補／ComposingTextの分離、ライト、ダーク＋統合ガイド、横画面、最小サイズの7条件で入力・削除・確定・変換・カーソル・英語／数字／記号からの復帰を確認しました。通常表示では次のキーボードとAndroid入力方法ピッカーも確認しています。
- 同端末の判定器単体は9,830 MOVEで中央値8.83µs、p95 54.65µs、最大752.08µs。ウォームアップ後の参考値であり、描画・変換・人の操作時間は含みません。
- 実機試験後に差分とスクリーンショットをレビューし、座標予測による誤入力、フロート表示内のウィンドウトークン、英数字からの復帰、ライトスキンの文字コントラストを修正・再検証しました。
- lintは、変更していない `ComposingGuideWindow.kt` の既存NewApiエラー7件で失敗します。同ファイルは起点のdev（`a8df834f1`）と一致しています。新規Orbitコードのlintエラーはありません。

## English

Add **Dynamic Orbit β** to the keyboard list in settings. Touch inside the outlined starting area, select a row on the inner ring, then a vowel on the outer ring. Move around the ring to change the selection; move inward to cancel a level. Crossing the outer boundary appends one kana to composing text. Return to the same center to start the next character without lifting. Lifting discards an uncommitted preview.

The center stays fixed throughout a stroke. This cyclic design replaces the original drifting-center proposal, so repeated characters do not walk toward a screen edge. A start area reserves enough space for every direction. Display-only floating guides never own touch events or the editor's input connection.

Rows run clockwise from the top in standard Japanese order. Vowels are A at lower left, I at upper left, U at top, E at upper right and O at lower right. The ya row has ya/yu/yo; the wa row has wa/wo/n/long-vowel mark. Empty slots do not produce text. Dakuten and small-kana gestures are outside this beta.

The command bar uses existing conversion, space, delete, Enter, cursor and mode-switching behavior. English and numbers use the existing QWERTY surface with a return path to Orbit. The feature supports normal and floating keyboards, themes, and the existing candidate/composing guides. Keyboard order is opt-in and uses the existing settings format.

Recognition uses distance, direction and spatial hysteresis, with no dwell or timeout. Human typing-speed benefits remain unproven: nominal cyclic paths average approximately 231dp per character. Automated injection validates correctness, not human typing speed. Use the isolated test application above for physical-device checks; the device test restores the previous IME and preferences.

Implementation validation: 120 selected unit/regression tests passed. Two instrumentation tests passed on a physical Pixel 6 (API 37), including seven IME configurations and a geometry-only timing sample. Normal and isolated Lite Standard Debug builds succeeded. Lint remains blocked by seven existing NewApi errors in unchanged ComposingGuideWindow.kt; no Orbit lint errors were reported. The final screenshots and code diff were reviewed after device testing.
