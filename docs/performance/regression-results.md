# 回帰結果

End-to-End 測定では、TenKey の「あさ」と QWERTY の `key` の編集中の文字列、候補一覧の順序、タップした候補の確定、composing span の解除を検証した。変更前後の各実機計測は各言語100回とウォームアップ10回を完了。200組でプライマリ候補の内容・順序と確定文字列がすべて一致した。Lite のアプリ APK と計測 APK に `INTERNET` 権限がないことを確認した。

変更後の JVM テストは41件中40件が成功。対象は候補世代、候補欄レイアウト、QWERTY 下フリック、英語直接入力、composing の仲裁、長押し削除条件、かな漢字変換セッション。失敗した `asciiInputKeepsHalfWidthUppercaseAcrossQueryModes` は、英語予測を無効にした `h8` に半角の `H8` が含まれない既存の失敗。比較元 `dev f3bc31372` のアプリコードに戻した同条件の JVM テストで、同じ候補列と失敗メッセージを再現した。 [失敗したテスト](../../app/src/test/java/com/kazumaproject/markdownhelperkeyboard/converter/session/KanaKanjiConversionSessionParityTest.kt#L55)

変更後の実機回帰テストは10件成功。英語の7件では、未インデックスの辞書と候補・コスト・順序を比較し、短い接頭辞、登録済みフレーズ、結合文、辞書・予測設定を検証した。キャッシュの回帰テストには、異なる件数上限、同じ接頭辞への再入力、アポストロフィを含む表記条件がある。IME の3件では、QWERTY の二指重複入力、TenKey の長押し削除、カーソル移動と確定を実機で検証した。 [英語候補の回帰テスト](../../app/src/androidTest/java/com/kazumaproject/markdownhelperkeyboard/converter/EnglishPhraseDictionaryInstrumentedTest.kt#L330) · [IME の実機テスト](../../app/src/androidTest/java/com/kazumaproject/markdownhelperkeyboard/FastInputMatrixInstrumentedTest.kt#L1189)

2段フリック、学習・ユーザー辞書を有効にした実機入力、外部エディタ、省電力、リフレッシュレートを変えた試行は未検証。日本語の候補比較は「あさ」の繰り返し条件に限る。
