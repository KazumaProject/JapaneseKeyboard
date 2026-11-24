# 入力パイプライン性能調査

- [IME ライフサイクルと構成](architecture.md)
- [タッチから候補表示・確定まで](input-pipeline.md)
- [日本語変換](japanese-conversion.md) · [英語予測](english-prediction.md)
- [非同期処理](coroutine-analysis.md) · [描画](ui-rendering.md) · [メモリ](memory-analysis.md)
- [測定方法](benchmark-methodology.md) · [変更前](benchmark-before.md) · [変更後](benchmark-after.md)
- [原因と証拠](bottlenecks.md) · [改善の採用基準](optimization-plan.md) · [回帰結果](regression-results.md)

開始点は `dev f3bc31372`。Pixel 6 の `key` 入力では英語フレーズ生成に 46.951〜57.282 ms かかる試行があり、別の CPU プロファイルで単語補完の辞書探索を確認した。英語辞書で短い接頭辞の補完結果を、接頭辞と上限が同じときに再利用する。

変更前後で日本語・英語各100回を比較した。英語のキー UP から候補の pre-draw までの P95 は 77.113 → 72.685 ms、P99 は 82.564 → 74.667 ms、最大は 83.765 → 78.364 ms。200組で候補の内容・順序と確定文字列が一致した。実機の回帰テストは10件成功。JVM は41件中40件成功し、失敗した半角の `H8` 候補テストは元の `dev` のアプリコードでも再現した。 [前後比較](benchmark-after.md) · [回帰結果](regression-results.md)

日本語の候補 P95 は 95.122 → 100.712 ms。日本語トレースの一部では、候補 Collector が始まる前の Main の View 計測と、候補公開後の差分計算が重なっている。エディタと IME を同じプロセスで動かす計測のため、それぞれの View 負荷や日常入力での発生頻度は未確定。外部エディタ、Release 相当、長時間・コールド起動、高速フリック・言語切り替えを実行し、画面への提示フレームまで追跡する必要がある。 [遅延箇所の証拠](bottlenecks.md) · [測定限界](benchmark-methodology.md)
