# Sumire Keyboard の入力性能調査

調査対象の開始点は `dev` の `f3bc31372ccb578100cba73eeafbfccccd8f3403`。実行経路の起点は `IMEService`、描画とタッチ処理は `tenkey`、`qwerty_keyboard`、`custom_keyboard` に分かれている。辞書、エディタ連携、候補生成と候補表示は `app` にある。Lite では Zenz と Gemma のビルドフラグが無効で、LiteRT ランタイムも Full の構成に限られる。 [app/build.gradle](../../app/build.gradle#L68)

## ライフサイクル

| コールバック | コードで確認した処理 | 根拠 |
|---|---|---|
| `onCreate` | 設定監視登録、フォント状態の購読、かな漢字エンジンのロード開始。辞書の準備は非同期。 | [IMEService.kt](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L3513) |
| `onCreateInputView` | 初回はコンテナとキー View を生成する。再作成時は既存コンテナを再利用し、親から取り外してリスナーを再設定する。既存の Main scope の子 Job をキャンセルし、Collector を設定する。 | [IMEService.kt](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L4269) |
| `onStartInput` | エディタの選択と編集中の状態を初期化し、設定スナップショットを適用する。候補のセッションと世代を更新し、エンジン準備後に候補表示を更新する。 | [IMEService.kt](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L4336) |
| `onStartInputView` | 設定の再同期、リスナーの再接続、候補リスト初期化、入力種別に応じた View 更新。エディタを作り直さずに呼ばれる場合がある。 | [IMEService.kt](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L6595) |
| `onFinishInput` | 変換プレビュー、エディタ補助機能、ポップアップと物理キーボードの候補セッションを停止・初期化する。 | [IMEService.kt](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L6960) |
| `onFinishInputView` | タッチプレビューを解除し、候補・入力の待機状態を初期化する。候補セッションの無効化はこの経路にもある。 | [IMEService.kt](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L6991) |
| `onDestroy` | 設定とフォントの購読、カーソルワーカー、描画補助を解除する。破棄処理で Main/IO scope と専用変換 Dispatcher を閉じる。 | [onDestroy](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L7083) · [actionInDestroy](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L27589) |

IME 再表示時に繰り返される View・候補の処理は上表のとおり。再表示の遅延への寄与は、辞書がロード済みの条件とプロセス起動を伴う条件を分けて確認する。 [辞書ロード](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L3464)
