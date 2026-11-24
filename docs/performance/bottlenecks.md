# 遅延の原因候補と証拠

| 確認した処理 | 現時点の判断と必要な計測 | 根拠 |
|---|---|---|
| 候補要求は古い処理のキャンセルを伴う | T08 と変換スレッドに残る作業を同じトレースで確認する。キャンセルと Main の処理負荷を分ける。 | [collectLatest](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L19565) |
| 最初の候補要求はキー領域の高さの更新を伴いうる | 最初の入力と連続入力で Layout/Draw と T08〜T14 の差を見る。 | [候補領域の高さ](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L21644) |
| 英語辞書は初回にロックを使ってロードする | 初回アクセス・再表示とウォーム補完を分離する。スタックの存在だけで継続入力の原因と扱わない。 | [英語辞書](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L848) |
| 旧計測は suspend 区間にスレッド専用の Trace を使っていた | 計測の不整合要因。非同期 Trace で開始と終了を組にする。実際の入力遅延への影響は未計測。 | [CandidatePerfTrace](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/CandidatePerfTrace.kt#L81) |

上の項目は調査対象。断続的な遅延の根本原因の確定には、異常サンプルの区間とその処理の照合が必要。
