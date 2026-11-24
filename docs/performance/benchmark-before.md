# 変更前の性能

比較元は `dev f3bc31372` に性能計測用のトレースとテストを加えた状態。入力と候補の挙動に手を入れる前に測る。数値を取得した条件とサンプル数、失敗・未取得数をこのファイルに記録する。

Pixel 6、Android 17（API 37）、Lite Standard Debug、縦向き。TenKey の「あ→あさ」と QWERTY の `ke → key` を日英それぞれ10回ウォームアップ後に100回測定した。集計時の Perfetto は停止。エディタの確定文字列は各回で「朝」「key」を確認した。端末の無線設定は維持し、アプリと計測用 APK の両方に `android.permission.INTERNET` がないことを `aapt dump permissions` で確認した。
## 計測値

| Language | Endpoint | n | Mean ms | P50 ms | P95 ms | P99 ms | Max ms |
|---|---|---:|---:|---:|---:|---:|---:|
| ja | DOWN → editor text change | 100 | 48.039 | 47.951 | 56.575 | 57.748 | 60.146 |
| ja | UP → editor text change | 100 | 10.327 | 10.368 | 12.068 | 12.676 | 12.828 |
| ja | DOWN → editor frame submission | 100 | 60.240 | 60.307 | 68.412 | 70.508 | 71.859 |
| ja | DOWN → candidate observation | 100 | 112.662 | 110.405 | 136.079 | 152.858 | 160.277 |
| ja | UP → candidate observation | 100 | 74.950 | 74.166 | 95.122 | 112.294 | 115.382 |
| ja | Commit UP → committed text observation | 100 | 27.173 | 24.961 | 36.702 | 37.761 | 38.203 |
| en | DOWN → editor text change | 100 | 57.461 | 56.904 | 66.934 | 67.460 | 70.109 |
| en | UP → editor text change | 100 | 12.560 | 12.541 | 14.345 | 15.559 | 19.384 |
| en | DOWN → editor frame submission | 100 | 64.412 | 64.454 | 74.070 | 74.469 | 76.191 |
| en | DOWN → candidate observation | 100 | 103.049 | 102.925 | 125.142 | 134.886 | 136.377 |
| en | UP → candidate observation | 100 | 58.148 | 57.313 | 77.113 | 82.564 | 83.765 |
| en | Commit UP → committed text observation | 100 | 22.545 | 21.768 | 30.147 | 33.361 | 33.878 |

## Pixel 6 での経路確認

Android 17（API 37）の Pixel 6 で、Lite Standard Debug を実行し、日本語 TenKey の「あさ」への更新を Perfetto で確認した。以下は、後段の確定結果の検査で停止した最初のウォームアップ試行のスライス。連続試行の分布には含めない。

| 観測区間 | この試行の値 |
|---|---:|
| IME の UP dispatch | 4.836 ms |
| エディタの `setComposingText` 呼び出し | 2.238 ms |
| 候補要求の公開マーカーから Collector 開始 | 19.459 ms |
| 候補要求の処理スライス | 29.311 ms |
| その内側の DP・N-best | 2.736 ms |
| アダプター差分コマンド | 11.596 ms |
| UP 受信から候補リスト更新コールバック | 73.720 ms |
| 候補タップ後の `InputConnection.commitText` | 2.468 ms |

候補タップは IME の `processCandidate` に到達し、Trace にエディタの書き込みがあった。確定結果の観測条件が満たされなかったため、エディタでの確定までの遅延、英語の結果、P95 / P99 はこの実行から算出していない。端末の無線設定は変更していない。
