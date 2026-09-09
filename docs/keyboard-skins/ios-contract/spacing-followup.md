# 候補欄の余白・高さ設定の追加検証（2026-09-09）

前回の候補テストは行数・見切れ・色を確認しましたが、テーマ間の候補タブの位置まで比較しておらず、不要な空白を見落としていました。

## 原因と修正

クパチーノだけ `finalKeyboardHeight + systemBottomInset` をウィンドウの高さに指定していました。
下寄せの候補・キーは動かず、上寄せのタブだけが Pixel 6 の下部インセット 126 px 分上へ離れていました。
その特例を削除し、全テーマで従来の `finalKeyboardHeight` と下部 padding を使います。
デフォルトの高さ計算、候補アダプター、フリック・長押しの入力処理は今回変更していません。
特例の足し算だけを正しいと仮定していた単体テストも削除し、実機でテーマ間の座標を比較する回帰チェックに置き換えました。

3 行・タブ有効の旧クパチーノは、タブ下端 1277 px、候補親上端 1415 px（間隔 138 px）でした。
修正後はタブ下端 1403 px、候補親上端 1415 px（間隔 12 px）です。
キーと候補は移動せず、デフォルトと同じ間隔です。設定による高さの余裕は削除しません。

[ライトの修正後画像](evidence/spacing-fix/cupertino_light-composing.png)・[ダークの修正後画像](evidence/spacing-fix/cupertino_dark-composing.png)

## 高さ設定の検証方法

候補 1・2・3 行 × タブ ON/OFF × 縦横の 12 設定について、デフォルト→クパチーノ・ライト→ダーク→デフォルトの順に表示します。
基本設定に加え、未入力時だけ 60→90 dp、入力時だけ設定値＋20 dp の 2 系列を試験します。
入力領域の座標差が指定した dp のピクセル換算に一致し、反対側の状態の座標とキーの位置が変わらないことを比較します。
タブの有無、候補行数、見切れ、色、テーマによる余白の増加も各回に検査します。
実機の 24 回の追加試験は完了しました。192 件の状態比較のうち 188 件は指定の増分と完全一致しました。
残り 4 件は「横・3 行・タブ ON・入力時 140 dp」で、試験のキー領域 220 dp と合計すると画面高を超える条件です。
入力領域上端が画面内に制限され、タブ上部も 33 px 見切れました。これを合格扱いにはしていません。
PR 前の APK で同じ条件を再現し、未入力／入力中とも座標・画像が完全一致しました。今回の PR による変化ではありません。
高さ計算を変更せず、収まる 125 dp（基準 120 dp＋5）でも再試験し、8 状態すべて指定どおり（入力時＋13 px、未入力時は不変）、タブ高 95 px 全体が表示されました。

- [基本 12 設定＋縦横の操作試験](evidence/spacing-fix/spacing-device-results.json)
- [未入力時／入力時を独立に変更する 24 試験](evidence/spacing-fix/height-settings-device-results.json)
- [192 件の実測値（上限到達した 4 件を含む）](evidence/spacing-fix/height-settings-measurements.json)
- [上限到達条件の PR 前比較](evidence/spacing-fix/overflow-baseline-comparison.json)
- [画面内に収まる設定の追加確認](evidence/spacing-fix/height-fitting-comparison.json)

検証ツール `tools/keyboard-skins/verify_candidate_height_settings.py` は、上限到達した元の測定に対して 4 件の不一致を報告して失敗します。
結果を通すために許容差や判定を緩めていません。入力設定の正確な dp→px 差を比較しています。
元のキャプチャは `build/keyboard-skins/spacing-review` に保存しています。

## iOS 側の追加確認

Simulator の画面操作を再開しました。キーから入力領域へ指を離しても「ぬ」が入力され、単なるキー外への移動はキャンセルではありませんでした。
標準 UITextField の `resignFirstResponder()` を接触開始 0.8 秒後に呼ぶ診断操作を用意し、キーボードの中断を検証しました。
縦横・ライト／ダーク各 4 回（予備 1＋計測 3）、計 16 回成功。アプリは再起動せず、入力文字は増えず、ガイドとキーボードが消えました。
接触イベントの連番も維持されています。これは入力先が閉じられた場合の検証であり、あらゆるシステム中断や直接の CANCEL 注入の証明ではありません。

[中断の記録](evidence/spacing-fix/ios-dismissal-manifest.json)・[表示フレーム](evidence/spacing-fix/ios-dismissal-held-guide-dismissal.png)

前回の「Mac のロック解除待ち」は解消しています。受け入れ基準全体の完了判定とは区別し、未検証項目を合格扱いにはしません。

## 長押しの形状比較

[背景を白に揃えた iOS／実機比較](evidence/spacing-fix/held-guide-white-comparison.png)。
参照は iOS 26.4.1 の標準日本語かなで、中央と四方向は一体の十字形です。
最初の比較は iOS 側の入力画面を灰色にしており、透過背景の明暗差がありました。
白に揃えた再記録でも一体形であることを確認しました。灰色の入力画面由来の差を理由に製品の色を変更していません。
比較はキー幅を揃えて切り出しています。Android の設定によるキーの縦横比と既存フォントは維持しています。
「popup がくっつく」という指摘の対象テーマは確認を求めており、この指摘に対して新しいデザイン変更はまだ入れていません。

## デフォルトの popup と従来表示

PR 前の APK と、修正版のデフォルト初回／クパチーノから戻した後を Pixel 6 で比較しました。
縦横 × 通常フリック／長押し × 上・右・左・下・中央・UP 後 × 初回／復帰の 48 画像比較すべて一致しました。
これは比較した表示状態の確認であり、動画の全フレームの時間的一致を主張するものではありません。

[popup 比較 48 件](evidence/spacing-fix/default-popup-comparison.json)・[従来レイアウト比較 24 件](evidence/spacing-fix/default-layout-comparison.json)

ソース差分でも、デフォルトの popup のサイズ・配置・描画は従来処理を使います。
`KeyboardSkinRegistry.find(DEFAULT)` は null、`SkinPopupPlacement.show` は復元処理後 false を返し、従来の処理へ進みます。
長押しの Blur と通常フリック中の元キー文字の消去もデフォルトの経路に残っています。
今回の余白修正では TenKey、popup 描画・入力処理を変更していません。

## ビルドと成果物

Android アプリ・実機テスト APK のビルド成功。候補・テーマ関連の単体テスト 269 件、失敗 0。
検証 APK（別パッケージ `com.kazumaproject.skinfidelity.lite`）は `build/keyboard-skins/reviewed-cupertino.apk`。
SHA-256: `8e7db17bef491c94c7bc9534c7ce1c4d364629c4e84f8fa58de797797f8d4fe7`。
実機試験後に選択中の入力方法と回転設定を復元しました。PR の push・マージは行っていません。
