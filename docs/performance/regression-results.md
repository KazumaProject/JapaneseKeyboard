# 回帰結果

初期の End-to-End 測定で、TenKey の「あさ」と QWERTY の `key` の編集中の文字列、候補表示、タップした候補の確定を検証する。候補 TSV はテストで観測できた表示分の順序を保存する。

実機でのフリック、長押し、2段フリック、学習、ユーザー辞書、外部エディタ、レートや省電力を変えた状態の動作は未検証。既存の回帰テストで代替できる範囲は実行結果とともに記録する。 [既存の連続入力マトリクス](../../app/src/androidTest/java/com/kazumaproject/markdownhelperkeyboard/FastInputMatrixInstrumentedTest.kt#L65)
