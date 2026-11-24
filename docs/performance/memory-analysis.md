# メモリ・GC・辞書ロード

IME の初回ロードは `startKanaKanjiEngineLoad` から始まり、英語辞書は `ensureDictionariesLoaded` が初回アクセス時に同期して準備する。暖まった後は `dictionaryData` を再利用する。初回入力の待ちと変換ループ中の処理時間は別計測。 [かな漢字ロード](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L3464) · [英語ロード](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L848)

辞書の展開を使うビルドでは、`MappedDictionaryAssetCache.fileFor` がプロセス内とファイルのロックを取得する。検証済みのファイルを再利用し、なければ XZ の展開と SHA-256 の検証を行う。展開済みファイルを作成できない場合はバイト配列へのフォールバックがある。キャッシュがある起動と削除直後の起動、Debug と Release の数値は混ぜない。 [MappedDictionaryAssetCache](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/dictionary_override/MappedDictionaryAssetCache.kt#L34) · [メモリへのフォールバック](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/dictionary_override/MappedDictionaryAssetCache.kt#L66)

英語補完は1つの辞書インスタンスで最大32件を保持する。各結果が最大32候補なので、常駐する候補オブジェクトは最大1,024個。対象は12文字以下の接頭辞に限り、アプリの再起動で消える。辞書データを追加ロードしない。 [英語補完](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/english/phrase/EnglishPhraseDictionary.kt#L15)

実機にインストールした Lite Standard Debug の APK は 135,490,043 → 135,855,630 バイト（+365,587 バイト、+0.27%）。ZIP 内のアセット671件は CRC とサイズがすべて一致し、圧縮済み DEX の合計は +1,504 バイト。Release のサイズは未計測。

短い実機トレースのプロセス RSS の P50 は406.383 → 407.514 MiB、最大は453.480 → 479.957 MiB。ART の `ThreadFlipSuspendAll` は計22 → 18件で、P95 は0.205 → 0.280 msだった。計測用エディタを含めたプロセス全体の値であり、キャッシュ単体の追加メモリや GC 停止を分離できていない。 [比較条件](benchmark-after.md#perfetto-での比較)

候補リスト、レイアウト、Loader、変換グラフの一時割り当ては、Perfetto のプロセス統計・GC スライスと UI thread の停止を照合して判断する。初回展開、端末のメモリ圧迫、長時間入力での比較は未計測。
