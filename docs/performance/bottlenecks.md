# 遅延の原因候補と証拠

| 確認した処理 | 現時点の判断と必要な計測 | 根拠 |
|---|---|---|
| 候補要求は古い処理のキャンセルを伴う | T08 と変換スレッドに残る作業を同じトレースで確認する。キャンセルと Main の処理負荷を分ける。 | [collectLatest](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L19565) |
| 最初の候補要求はキー領域の高さの更新を伴いうる | 最初の入力と連続入力で Layout/Draw と T08〜T14 の差を見る。 | [候補領域の高さ](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L21644) |
| 英語辞書は初回にロックを使ってロードする | 初回アクセス・再表示とウォーム補完を分離する。スタックの存在だけで継続入力の原因と扱わない。 | [英語辞書](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L848) |
| 旧計測は suspend 区間にスレッド専用の Trace を使っていた | 計測の不整合要因。非同期 Trace で開始と終了を組にする。実際の入力遅延への影響は未計測。 | [CandidatePerfTrace](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/CandidatePerfTrace.kt#L81) |

上の項目は調査対象。断続的な遅延の根本原因の確定には、異常サンプルの区間とその処理の照合が必要。

## 実機で確認した一例

Pixel 6 の「あさ」への更新で、候補要求公開から Collector 開始まで 19.459 ms。その間に Main は 17.662 ms 実行状態で、`Choreographer#doFrame` の中の View 計測に 15.004 ms を使っていた。同じ要求内の DP・N-best は 2.736 ms、候補公開後の差分コマンドは 11.596 ms。この試行では変換だけを短縮しても、Main の待ちと差分処理は残る。 [候補要求](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L22232) · [差分コマンド](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/adapters/SuggestionAdapter.kt#L863)

この試行はエディタと IME が同一プロセスの計測ホストで採取した。View 計測の内訳を IME とエディタに分離できておらず、常用時の断続的な遅延の原因までは確定していない。 [計測値と範囲](benchmark-before.md)
