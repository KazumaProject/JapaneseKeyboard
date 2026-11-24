# Coroutine・Flow・キャンセル

| 境界 | 所有者と実行契機 | 根拠 |
|---|---|---|
| ホットな入力状態 | IME の Main scope に `inputString.collect` があり、エディタの表示更新と候補要求を起動する。 | [scope](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L943) · [Collector](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L20175) |
| 候補の集約 | `CandidateRefreshCoordinator` の `MutableStateFlow` は最新リクエストを保持する。同じ読みでも revision が異なる。新しい候補要求で `collectLatest` が前の作業をキャンセルする。 | [Coordinator](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/candidate/CandidateRefreshCoordinator.kt#L15) · [collectLatest](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L19565) |
| 変換スレッド | `kanaKanjiConversionDispatcher` は単一スレッド。候補の経路によって辞書・テンプレート・学習検索を IO 等に渡す。集計は段階の重なりを考慮する。 | [Dispatcher](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L1009) · [getSuggestionList](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L28694) |
| 古い結果の排除 | 独立したリクエストトークンにモードとエディタ変化の世代を持たせる。計算が完了しても適用直前に照合する。 | [CandidateRequestTracker](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/candidate/CandidateQueryPolicy.kt#L33) · [shouldApplyCandidateResult](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L20646) |
| 停止と再開 | View の再作成で子 Job を取り消して購読を設定し直す。変換セッションはキャンセルを伝播し未完のグラフ状態を戻す。 | [View の再作成](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L4269) · [変換セッション](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/session/KanaKanjiConversionSession.kt#L130) |

Trace の T08 は候補公開から Collector の開始までの時間で、前リクエストの終了を待つ時間も含みうる。変換スレッドの実行前待ち、辞書準備待ち、実行中の CPU 時間を同一視しない。StateFlow が取り込まなかった要求は候補集約として数え、キーイベントの取りこぼしは実際のエディタ出力で検出する。 [公開時刻](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/candidate/CandidateRefreshCoordinator.kt#L71)
