# 英語の補完・修正経路

QWERTY のタップとフリックから `handleQwertyKeyRelease` に入り、通常の編集中は日本語と同じ候補要求・候補表示基盤を通る。直接入力は別の設定経路。候補タブ非表示または Prediction タブの単一英字では、辞書の候補が届く前に大文字・小文字の候補が表示される。先行表示の遅延と辞書候補の遅延を分ける。 [QWERTY の release](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L25662) · [先行候補](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L28240)

共通のかな漢字エンジンから `EnglishEngine` を呼ぶ。半角 ASCII の場合、`GraphBuilder.constructGraph` が空グラフで早期終了し、日本語の N-best 探索には入らない。かなと英字が混在する文字列は別条件として扱う。 [ASCII の条件](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/graph/GraphBuilder.kt#L349) · [EnglishEngine 呼び出し](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/KanaKanjiEngine.kt#L3933)

`EnglishEngine.getConversionCandidates` は、一語の候補と設定に応じた複数語の補完を統合する。読みトライの prefix、短語の順位、大小文字の変換と重複除去がある。タイプミス補正は設定が有効で条件を満たすときに実行する。ワード予測の英語文法データは遅延ロード。 [変換候補](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L533) · [辞書のロード](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L848)

フレーズの格子で末尾の語を補完するとき、`EnglishPhraseDictionary.complete` が組み込みの読みトライを走査する。既存のコスト上限で枝を絞り、表記ルールで候補を選び、件数上限付きで順位を決定する。短い接頭辞の同一要求は辞書インスタンス内でその結果を再利用する。 [格子](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/english/phrase/PhraseConverter.kt#L220) · [単語補完](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/english/phrase/EnglishPhraseDictionary.kt#L56)

確定済みの文脈からのゼロクエリ候補と、確定後の英語修正は入力中の補完とは別経路。初期ベンチマークではゼロクエリを無効にした。候補リストに載った語、確定できた語、入力対象エディタのコンテキストを別々に観測する。 [ゼロクエリの補完](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L1692) · [確定後の英語修正](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L4104)
