# PiP中の文字入力遅延の実機解析

## 結論

CPU使用率の採取は必要だった。Pixel 6で入力中のCPU時間を採取した結果、端末全体のCPU飽和は確認できなかった。したがって「PiPの動画再生でCPUが使い切られた」が原因とは断定できない。

一方、入力1回ごとに候補生成と候補UI更新が走り、通常の入力中は見えていない全候補一覧にも`AsyncListDiffer`の`DiffUtil`を実行していたことは、コードと実機ログの両方で確認できた。今回の修正ではこの不要な差分計算を止め、候補行の差分キーも位置ベースにした。

ライブ変換、nBest、beam width、incremental設定、スレッド優先度は変更していない。

## 条件

- ブランチ: `codex/analyze-pip-typing-lag`（`dev`から作成）
- 実機: Pixel 6 / Android 16 (API 36) / 1080x2400 / 90Hz / 8 CPU
- 入力先: ChromeのURL入力欄
- 操作: 画面タップ10回
- 比較: カスタムIME、Gboard
- 端末設定確認値: `live_conversion=true`、`nBest=14`、`beam width=20`、`incremental=false`

## CPU実測

`/proc/stat`と各プロセスの`/proc/<pid>/stat`を約250ms間隔で同時採取した。プロセス値は8コア全体ではなく、1コアを100%とする換算値である。

| 条件 | 入力中の端末全体 | IME / Gboard | Chrome | 動画プロセス |
| --- | ---: | ---: | ---: | ---: |
| カスタムIME、PiPなし | 約15–18% | IME 約10–25% | 約30–48% | YouTube 0% |
| Gboard、PiPなし | 約20–27% | Gboard 約40–67% | 約3–8% | — |

カスタムIMEは入力中に1コアを使い切っておらず、8コア全体も飽和していない。GboardはカスタムIMEより瞬間的なプロセスCPU使用率が高い区間でも、フレーム落ちが少なかった。このため、CPU使用率の大小だけを原因とは扱えない。

## フレーム実測

同じPixel 6で取得した実PiPを含むフレーム計測は次の通り。PiP時はYouTubeの実PiPを`dumpsys activity activities`で`mode=pinned`として確認した。

| 条件 | 総フレーム | ジャンク | ジャンク率 | 高入力レイテンシ | Slow UI |
| --- | ---: | ---: | ---: | ---: | ---: |
| カスタムIME + PiP | 37 | 10 | 27.03% | 32 | 10 |
| カスタムIME + PiPなし | 38 | 7 | 18.42% | 30 | 7 |
| Gboard + PiPなし | 83 | 1 | 1.20% | 21 | 1 |

別途、入力経路が異なるGboard + PiPでは21フレーム中ジャンク0だったため、補足値として扱う。

## 実装上確認できた負荷

修正前の実機ログでは、1入力ごとに表示中の候補Adapterに加えて、通常は非表示の全候補Adapterにも`DiffUtil`が走り、後者だけで約10–28msかかる入力があった。

今回の修正後は、非表示の全候補Adapterへのsubmitを行わず、全候補画面を開いた時だけ最新状態をsubmitするようにした。候補行の`areItemsTheSame`は候補文字列ではなく表示位置をキーにし、文字列変更は`areContentsTheSame`で再bindする。修正後の実機ログでは入力ごとの表示中Adapterの差分計算は1回だけで、約0.09–3.01msだった。

候補生成自体は残っており、同じ実機ログでは暖機後のエンジン処理が約5.27–27.24ms、別の冷間・負荷のある入力列では約50.74–98.82msだった。候補生成の遅延は今後も別途計測対象だが、nBestを下げる、ライブ変換を無効化する、CPU優先度を下げるといった設定変更は行わない。

## 変更ファイル

- `IMEService.kt`: 非表示の全候補Adapterには入力ごとの差分計算を送らず、全候補表示への切替時に一度だけ同期する。
- `SuggestionAdapter.kt`: 候補行を位置ベースのidentityにして、候補文字列変更をremove/insertの連鎖として扱わない。

## 検証

- `SuggestionAdapterDisplayItemTest`、`SuggestionAdapterListUpdateTest`: 成功
- `:app:assembleFullStandardDebug`: 成功
- 修正版APKをPixel 6へインストールして実機ログ・CPU・フレーム計測を実施
- 最終確認で`live_conversion=true`、`nBest=14`、`beam width=20`、`incremental=false`を確認
- `Process.THREAD_PRIORITY_DISPLAY`は維持

PiP中のCPU値は、今回の最終CPU採取時にはYouTube側がPremium制限画面となり再現できなかったため、未採取である。PiP中のフレーム値と、PiPなしのCPU比較は分けて記録しており、PiP時のCPU競合についてはこの実測だけから推測していない。
