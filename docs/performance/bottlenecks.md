# 遅延の原因候補と証拠

| 確認した処理 | 現時点の判断と必要な計測 | 根拠 |
|---|---|---|
| 候補要求は古い処理のキャンセルを伴う | T08 と変換スレッドに残る作業を同じトレースで確認する。キャンセルと Main の処理負荷を分ける。 | [collectLatest](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L19565) |
| 最初の候補要求はキー領域の高さの更新を伴いうる | 最初の入力と連続入力で Layout/Draw と T08〜T14 の差を見る。 | [候補領域の高さ](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L21644) |
| 英語フレーズ生成内で短い単語の補完を辞書から作り直す | Pixel 6 の `key` ではフレーズ全体に 46.951〜57.282 ms。別の英語回帰ワークロードの CPU サンプルでも補完の辞書探索を確認した。 | [フレーズ呼び出し](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L563) · [辞書探索](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/english/phrase/EnglishPhraseDictionary.kt#L73) |
| 英語辞書は初回にロックを使ってロードする | 初回アクセス・再表示とウォーム補完を分離する。スタックの存在だけで継続入力の原因と扱わない。 | [英語辞書](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L848) |
| 旧計測は suspend 区間にスレッド専用の Trace を使っていた | 計測の不整合要因。非同期 Trace で開始と終了を組にする。実際の入力遅延への影響は未計測。 | [CandidatePerfTrace](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/CandidatePerfTrace.kt#L81) |

上の項目は調査対象。断続的な遅延の根本原因の確定には、異常サンプルの区間とその処理の照合が必要。

## 実機で確認した一例

Pixel 6 の「あさ」への更新で、候補要求公開から Collector 開始まで 19.459 ms。その間に Main は 17.662 ms 実行状態で、`Choreographer#doFrame` の中の View 計測に 15.004 ms を使っていた。同じ要求内の DP・N-best は 2.736 ms、候補公開後の差分コマンドは 11.596 ms。この試行では変換だけを短縮しても、Main の待ちと差分処理は残る。 [候補要求](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L22232) · [差分コマンド](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/adapters/SuggestionAdapter.kt#L863)

この試行はエディタと IME が同一プロセスの計測ホストで採取した。View 計測の内訳を IME とエディタに分離できておらず、常用時の断続的な遅延の原因までは確定していない。 [計測値と範囲](benchmark-before.md)

## 英語の補完

同一端末の `ke → key` の Perfetto 4試行で、フレーズ生成は 46.951〜57.282 ms。直接の英語単語候補生成は 4.980〜6.150 ms、同処理の prefix lookup は 0.261〜0.888 ms、順位付けは 0.110〜0.148 ms だった。区間は一部入れ子であり、合計して候補遅延を算出できない。 [英語の候補生成](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L546)

別途、日常語、カタログ名、複数語の英語回帰3件の実行プロセスを simpleperf で採取した。ユーザー空間の CPU サンプル 3,416 件で `PhraseConverter.convert` がプロセス全体の 13.17%、`EnglishPhraseDictionary.complete` が 8.81%（いずれも子を含む）。このサンプルは辞書テストであり、`key` 単体における内訳や壁時計の割合は示せない。 [補完比較に使用した試験](../../app/src/androidTest/java/com/kazumaproject/markdownhelperkeyboard/converter/EnglishPhraseDictionaryInstrumentedTest.kt)

変更後のトレースで最初の `key` の `EnglishPhraseDictionary.complete` の探索区間は33.143 ms。ウォームアップ後の `key` の補完は12回とも再利用された。フレーズ区間の P50 は変更前27.433 ms、変更後14.843 ms。短い接頭辞を繰り返した場合に観測できた改善で、初めて入力する接頭辞や日常入力での命中率は未計測。 [変更後のトレース比較](benchmark-after.md#perfetto-での比較)
