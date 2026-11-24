# 入力パイプライン性能調査

- [IME ライフサイクルと構成](architecture.md)
- [タッチから候補表示・確定まで](input-pipeline.md)
- [日本語変換](japanese-conversion.md) · [英語予測](english-prediction.md)
- [非同期処理](coroutine-analysis.md) · [描画](ui-rendering.md) · [メモリ](memory-analysis.md)
- [測定方法](benchmark-methodology.md) · [変更前](benchmark-before.md) · [変更後](benchmark-after.md)
- [原因と証拠](bottlenecks.md) · [改善の採用基準](optimization-plan.md) · [回帰結果](regression-results.md)

開始点は `dev f3bc31372`。日英の通常経路をコードで追跡し、Lite のオフライン試験と Perfetto の計測点を用意した。断続的な遅延の原因と高速化の効果は、実測と再計測が得られた段階で判断する。
