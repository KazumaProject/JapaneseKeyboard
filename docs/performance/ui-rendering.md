# 候補とキーボードの表示

候補要求の Collector は候補領域の表示・高さを更新してから変換に進む。結果の公開時は Main で短縮候補と全候補を更新する。レイアウトが変わる場合は候補関連の View と IME インセットの更新を含むので、辞書の時間と分けて追跡する。 [候補領域の高さ](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L21644) · [候補公開](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L1315) · [表示の反映](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L24827)

`SuggestionAdapter` の `AsyncListDiffer` は差分を専用の Executor に渡す。コミット時の世代チェックがあり、差分待ちの間に新しい入力を受け付ける。候補 State 公開、差分コミット、View バインド、画面表示の時刻は別々に扱う。 [差分 Executor](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/adapters/SuggestionAdapter.kt#L863) · [差分コミット](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/adapters/SuggestionAdapter.kt#L982) · [View バインド](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/adapters/SuggestionAdapter.kt#L1512)

Perfetto では IME の UI thread、RenderThread、SurfaceFlinger の FrameTimeline を同じ時間軸で確認する。エディタのフレーム提出と候補ビューの pre-draw は、SurfaceFlinger のフレーム表示より手前の観測点。実機とエミュレーターの結果は別々に記録する。 [Perfetto FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline) · [Android registerFrameCommitCallback](https://developer.android.com/reference/android/view/ViewTreeObserver#registerFrameCommitCallback(java.lang.Runnable))
