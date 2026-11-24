# メモリ・GC・辞書ロード

IME の初回ロードは `startKanaKanjiEngineLoad` から始まり、英語辞書は `ensureDictionariesLoaded` が初回アクセス時に同期して準備する。暖まった後は `dictionaryData` を再利用する。初回入力の待ちと変換ループ中の処理時間は別計測。 [かな漢字ロード](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L3464) · [英語ロード](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L848)

辞書の展開を使うビルドでは、`MappedDictionaryAssetCache.fileFor` がプロセス内とファイルのロックを取得する。検証済みのファイルを再利用し、なければ XZ の展開と SHA-256 の検証を行う。展開済みファイルを作成できない場合はバイト配列へのフォールバックがある。キャッシュがある起動と削除直後の起動、Debug と Release の数値は混ぜない。 [MappedDictionaryAssetCache](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/dictionary_override/MappedDictionaryAssetCache.kt#L34) · [メモリへのフォールバック](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/dictionary_override/MappedDictionaryAssetCache.kt#L66)

候補リスト、レイアウト、Loader、変換グラフの一時割り当ては、Perfetto のプロセス統計・GC スライスと UI thread の停止を照合してから原因と認定する。メモリ圧迫、APK サイズ、初回展開、実機での長時間入力の差分は現在未計測。単発のエミュレーター比較で実機の GC 停止やページフォルトの原因は特定できない。
