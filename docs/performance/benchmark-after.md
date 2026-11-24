# 変更後の性能

Pixel 6 の同一エディタ、同一 Lite Standard Debug 構成、同一の計測 APK で、10回のウォームアップ後に日英各100回を比較した。候補の観測点は対象ビューの pre-draw。Perfetto を停止した状態で下表を取得した。 [変更前](benchmark-before.md) · [測定方法](benchmark-methodology.md)

| UP → 候補 pre-draw | P50 | P95 | P99 | 最大 |
|---|---:|---:|---:|---:|
| 日本語「あさ」 | 74.166 → 74.411 ms | 95.122 → 100.712 ms（−5.9%） | 112.294 → 109.681 ms（+2.3%） | 115.382 → 112.518 ms（+2.5%） |
| 英語 `key` | 57.313 → 53.224 ms | 77.113 → 72.685 ms（+5.7%） | 82.564 → 74.667 ms（+9.6%） | 83.765 → 78.364 ms（+6.4%） |

パーセントは変更前を基準にした改善率。200試行のプライマリ候補一覧の内容と順序、確定文字列に差分はなかった。英語は繰り返す同一補完のワークロード。日本語の候補 P95 と英語のエディタ更新 P95 は増加した。今回の変更は英語の補完検索に限定しており、両指標の変動要因は確定していない。

## 全指標


| Language | Endpoint | n | Mean ms | P50 ms | P95 ms | P99 ms | Max ms |
|---|---|---:|---:|---:|---:|---:|---:|
| ja | DOWN → editor text change | 100 | 48.341 | 48.291 | 56.284 | 58.712 | 58.917 |
| ja | UP → editor text change | 100 | 10.340 | 10.387 | 12.376 | 12.672 | 12.865 |
| ja | DOWN → editor frame submission | 100 | 60.420 | 60.070 | 68.205 | 72.062 | 73.309 |
| ja | DOWN → candidate observation | 100 | 114.172 | 112.129 | 137.010 | 148.300 | 150.662 |
| ja | UP → candidate observation | 100 | 76.171 | 74.411 | 100.712 | 109.681 | 112.518 |
| ja | Commit UP → committed text observation | 100 | 26.538 | 24.756 | 35.926 | 37.537 | 37.612 |
| en | DOWN → editor text change | 100 | 57.940 | 57.804 | 68.271 | 69.826 | 70.988 |
| en | UP → editor text change | 100 | 12.754 | 12.502 | 15.127 | 17.308 | 22.805 |
| en | DOWN → editor frame submission | 100 | 64.841 | 64.219 | 75.685 | 76.405 | 78.396 |
| en | DOWN → candidate observation | 100 | 97.758 | 96.934 | 118.094 | 121.808 | 131.820 |
| en | UP → candidate observation | 100 | 52.572 | 53.224 | 72.685 | 74.667 | 78.364 |
| en | Commit UP → committed text observation | 100 | 23.353 | 22.504 | 30.864 | 32.298 | 32.526 |
