# 変換結果の完全一致と性能評価

比較元は `dev f3bc31372`。今回の差分は端末テスト、集計スクリプト、調査記録に限る。既存のエンジンでベースラインを測り、以後の変更は同一条件の結果を一件ずつ照合してから評価する。

## ソースで確認した経路

| 経路 | 処理と比較対象 | 根拠 |
|---|---|---|
| IME から変換 | モード、文節、予測、補正条件を `KanaKanjiQueryRequest` に設定。編集中のセッションを専用 Dispatcher で実行する。 | [IMEService.queryKanaKanjiCore](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt#L28999) |
| 日本語 | セッションがモード別に通常候補、予測、変換、英数かなを呼び出す。インクリメンタル方式はグラフと前向き DP の状態を再利用し、編集・キャンセル時はトランザクションで整合を保つ。 | [KanaKanjiConversionSession.query](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/session/KanaKanjiConversionSession.kt#L87) · [GraphBuilder](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/graph/GraphBuilder.kt) · [FindPath](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/path_algorithm/FindPath.kt) |
| 英語 | 半角 ASCII では日本語のグラフが空になる。英語側で単語、フレーズ、場合によりタイプミス補完を求め、候補を統合する。確定済みの文脈に対する登録フレーズの継続候補は別の入口。 | [GraphBuilder](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/graph/GraphBuilder.kt#L349) · [EnglishEngine](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L517) · [EnglishPhraseDictionary](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/english/phrase/EnglishPhraseDictionary.kt#L50) |
| 候補出力 | エンジンの候補を型、読み、確定文字列、スコア、由来とともに保持する。表示の重複統合は先頭候補の振る舞いを維持する。文節パターン、文字列ごとの変換経路も操作に利用する。 | [Candidate](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/Candidate.kt#L11) · [CandidateDisplayMerger.merge](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/candidate/CandidateDisplayMerger.kt#L12) |

対象は辞書・グラフ・探索・順位付けの CPU 経路。候補描画、編集アプリとの通信、外部辞書、UI での全候補並べ替えはこの実験の外にある。

## 完全一致の判定

Android テストでは組み込み辞書と、メモリ内 Room の固定ユーザー辞書・学習データを使用する。日本語は短文、長文、同音異義語、文節、固有名詞、記号、数字、補正、学習、予測の無効化を含む。英語は接頭辞、連続入力と削除、フレーズ、大小文字、補正、記号、予測の無効化、確定済みの文脈を含む。LEGACY と INCREMENTAL_SESSION の結果をそれぞれ保存し、比較時は同じ方式・ケース・編集ステップを照合する。現在時刻で結果が変わる日付専用入力は固定コーパスから除く。 [端末テスト](../../app/src/androidTest/java/com/kazumaproject/markdownhelperkeyboard/converter/ConversionParityInstrumentedTest.kt)

候補の一覧を切り詰めず、表示・確定文字列、件数、順位、スコア、型、読み、辞書由来、文節位置と変換経路を比較する。辞書由来など集合型の要素だけを整列する。候補配列と文節パターンの順序は保持する。JSON のフィールド、候補の数値、候補の順序、ケース欠落のいずれかに差があれば比較は失敗する。結果には最初に異なるフィールドと順位が出る。 [比較ツール](../../tools/performance/compare-conversion.py)

## 再現手順

同じ Android 端末、OS ビルド、APK フレーバー、言語、タイムゾーン、辞書アセットで実行する。温度と電源状態を記録し、性能の判定では実行順を入れ替えて繰り返す。テストは Lite Standard Debug を用い、Zenz・Gemma・LiteRT を無効にする。比較元と対象リビジョンに同一のテストファイルを置き、両 APK をビルドする。テスト APK とアプリ APK の組は比較のたびに入れ替える。計測用アプリには固定の隔離 ID を割り当て、ユーザーの入力設定や辞書を使用しない。 SDK と依存関係を取得済みなら以下はオフラインで実行できる。

```bash
conversion_build_dir=$(mktemp -d)
sed -e 's/com.kazumaproject.markdownhelperkeyboard.settingsbackuptest/com.kazumaproject.conversionparity.lite.test.settingsbackuptest/' \
    -e 's/com.kazumaproject.markdownhelperkeyboard.lite.localfonttest/com.kazumaproject.conversionparity.lite.test.localfonttest/' \
    app/src/androidTest/AndroidManifest.xml > "$conversion_build_dir/AndroidTestManifest.xml"
cat > "$conversion_build_dir/parity.init.gradle" <<'GRADLE'
allprojects { project ->
    project.plugins.withId('com.android.application') {
        if (project.path == ':app') {
            project.androidComponents.finalizeDsl { android ->
                android.defaultConfig.applicationId = 'com.kazumaproject.conversionparity'
                android.sourceSets.androidTest.manifest.srcFile(
                    project.providers.environmentVariable('CONVERSION_PARITY_MANIFEST').get()
                )
            }
        }
    }
}
GRADLE
CONVERSION_PARITY_MANIFEST="$conversion_build_dir/AndroidTestManifest.xml" \
  ./gradlew --offline -I "$conversion_build_dir/parity.init.gradle" \
  :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest
adb install -r app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/liteStandard/debug/app-lite-standard-debug-androidTest.apk
cp app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk /tmp/conversion-before.apk

tools/performance/capture-conversion.sh /tmp/conversion-before snapshot
tools/performance/capture-conversion.sh /tmp/conversion-before benchmark ja-long INCREMENTAL_SESSION 20 100
tools/performance/capture-conversion.sh /tmp/conversion-before benchmark en-prefix INCREMENTAL_SESSION 20 100
tools/performance/capture-conversion.sh /tmp/conversion-before stages ja-long INCREMENTAL_SESSION 20 100
tools/performance/capture-conversion.sh /tmp/conversion-before allocations en-prefix INCREMENTAL_SESSION 20 100
# 比較対象の APK を /tmp/conversion-after.apk に保存してインストールする
# 同じ計測コマンドで、結果は /tmp/conversion-after に保存する
python3 tools/performance/compare-conversion.py assets /tmp/conversion-before.apk /tmp/conversion-after.apk
python3 tools/performance/compare-conversion.py snapshot \
  /tmp/conversion-before/snapshot.json /tmp/conversion-after/snapshot.json
python3 tools/performance/compare-conversion.py benchmark \
  /tmp/conversion-before/benchmark-INCREMENTAL_SESSION-ja-long.json \
  /tmp/conversion-after/benchmark-INCREMENTAL_SESSION-ja-long.json
```

アプリ内の辞書オーバーライドを入れていない隔離インストールを使う。`assets` コマンドは比較用に保存した両 APK の組み込みアセット名と SHA-256 を全件照合する。ハーネスは各計測前に隔離アプリのプロセスを終了し、テストが成功して新しい結果を出力した場合にだけ JSON を保存する。端末が複数の場合は `ANDROID_SERIAL`、ADB サーバーが別ポートの場合は `CONVERSION_PARITY_ADB_PORT` を指定できる。Mozc の生成入力が比較元と同一の場合は `-x :app:generateMozcSegmenterData` とコミット済み成果物の検証を使える。

`benchmark` ではエンジン解決時間、プロセス内の最初のクエリ、ウォームアップ後の各クエリを記録する。候補の JSON 化は計測区間外。各試行の実時間から平均・nearest-rank の P50/P95/P99・最大を計算する。複数ステップのケースはクエリごとの生データを保持するため、最初のクエリと続けて編集したクエリを比較できる。初回は一プロセスにつき一回。OS のファイルキャッシュを残す条件なので、端末再起動直後のコールド起動とは区別する。

プロセス CPU 時間、PSS、ART の割り当てバイト数と GC はウォーム計測ブロックの前後で採取する。値にはテストと同一プロセスの他の処理も含まれる。割り当て件数は ART の旧カウンターを使う別実行でのみ収集し、利用できない場合は未計測とする。ステージ計測も別実行で、計測用コードの負荷を通常の時間測定から除く。 [Android Debug API](https://developer.android.com/reference/android/os/Debug)

## 実測と改善候補

Pixel 6、Android 17（API 37）、ja-JP、Asia/Tokyo で同じ APK を二度実行し、107 / 107 ケースが完全一致した。LEGACY と INCREMENTAL_SESSION が各51ケース、英語の確定文脈が5ケース。エンジンの候補5,872件、表示統合後4,298件に加えて文節データも照合した。試験辞書の「菫計測」と学習候補の「すみれ計測」は両方式で出力された。

実測結果の456位に表示文字列、確定文字列、スコアの各変更を独立に注入し、それぞれの比較が失敗した。末尾の候補削除と末尾近くの並べ替えでも失敗した。APK アセットの照合は671件。使用したアプリ APK に `INTERNET` 権限や Zenz・Gemma・LiteRT のネイティブライブラリはなかった。

既存の端末回帰テストは3件とも成功。対象は、補完インデックスと参照実装の候補順序の一致、両方式での文脈付きフレーズの空白維持、複数語候補の確定文字列。 [既存の英語回帰テスト](../../app/src/androidTest/java/com/kazumaproject/markdownhelperkeyboard/converter/EnglishPhraseDictionaryInstrumentedTest.kt)

### 変換時間のベースライン

デフォルトの20ラウンドをウォームアップに使い、その後100ラウンドを集計した。編集やフレーズのケースは一ラウンドに複数クエリを含む。バッテリー温度は計測開始時30.4°C、終了時29.9°C。

| ケース | ウォーム n | 初回クエリ ms | 平均 ms | P50 ms | P95 ms | P99 ms | 最大 ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| `ja-short` | 100 | 22.309 | 4.830 | 4.332 | 7.492 | 7.883 | 8.353 |
| `ja-long` | 100 | 158.923 | 14.824 | 13.399 | 20.578 | 24.267 | 29.197 |
| `ja-bunsetsu` | 100 | 160.698 | 17.504 | 15.446 | 30.241 | 33.261 | 40.377 |
| `ja-edit` | 600 | 17.600 | 3.143 | 3.040 | 5.376 | 5.828 | 9.978 |
| `en-prefix` | 100 | 1295.898 | 23.794 | 23.318 | 29.375 | 30.485 | 30.575 |
| `en-edit` | 600 | 1121.483 | 9.665 | 7.509 | 20.256 | 22.568 | 26.967 |
| `en-phrase` | 400 | 1496.739 | 28.429 | 29.610 | 56.522 | 58.545 | 60.048 |
| `en-typo` | 300 | 1215.511 | 14.597 | 14.624 | 20.707 | 23.664 | 28.288 |

| ケース | PSS 開始 → 終了 KiB | 割り当て MiB | CPU ms | GC 回数 |
|---|---:|---:|---:|---:|
| `ja-short` | 171,183 → 175,071 | 7.00 | 563 | 0 |
| `ja-long` | 176,747 → 186,099 | 49.94 | 1,555 | 1 |
| `ja-bunsetsu` | 176,185 → 184,357 | 48.78 | 1,875 | 1 |
| `ja-edit` | 175,443 → 178,876 | 43.53 | 2,101 | 1 |
| `en-prefix` | 229,761 → 207,156 | 50.56 | 2,524 | 1 |
| `en-edit` | 260,662 → 215,714 | 183.00 | 6,054 | 3 |
| `en-phrase` | 224,819 → 235,088 | 286.44 | 11,535 | 4 |
| `en-typo` | 263,584 → 271,832 | 192.59 | 4,629 | 2 |

PSS、割り当て、CPU、GC はウォーム計測ブロックのプロセス全体の値。停止を伴う GC の総時間の増分は全ケースで0 ms。PSS の開始値と終了値は GC と辞書のメモリページに応じて変わり、変換処理単体のピークメモリとは区別する。

| 割り当て件数の別実行 | クエリ数 | ART オブジェクト割り当て | 割り当て MiB |
|---|---:|---:|---:|
| `ja-long` | 100 | 2,106,604 | 49.91 |
| `en-prefix` | 100 | 1,825,203 | 50.59 |
| `en-phrase` | 400 | 13,012,017 | 286.56 |

件数取得はプロセス全体の別実行なので、通常の時間ベンチマークや変換エンジン単体の割り当てと混ぜない。

### 計測の再現性とプロファイル

同じ APK を別プロセスで再計測すると、日本語長文の P95 は 20.578 → 20.330 ms、英語 `key` は 29.375 → 28.765 ms。英語 `key` の P99 は 30.485 → 32.212 ms、最大は 30.575 → 34.804 ms。いずれも同一コードの試行間の差。以後の A/B は実行順を入れ替えて繰り返し、測定のばらつきより小さな差を改善判定に使わない。

- 日本語長文のステージ計測で、グラフ構築の P50 は5.993 ms、P95 は7.944 ms。通常候補の `FindPath.backwardAStar` は既存のステージカウンターが前向き・後ろ向きに分かれていないため、これらは未計測。文節変換の別実行では、グラフの P95 が5.919 ms、前向き DP が2.389 ms、後ろ向き探索が16.196 msだった。 [グラフ構築](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/graph/GraphBuilder.kt#L322) · [文節探索の計測](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/path_algorithm/FindPath.kt#L1791)
- ユーザー空間の CPU プロファイルで、日本語長文の2,468サンプルのうち、子を含む割合は `FindPath.backwardAStar` 25.32%、`GraphBuilder.constructGraph` 19.00%、`forwardDp` 13.53%、`enqueueBackwardExpansion` 6.48%、`getEdgeCost` 4.58%。 [探索](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/path_algorithm/FindPath.kt#L1094)
- 英語フレーズの11,160 CPU サンプルでは `EnglishPhraseDictionary.complete` 53.15%、`RuntimeGrammarRoles.lookup` 21.81%、`LoudsTermIterator.hasNext` 18.73%（いずれも子を含む）。測定は `simpleperf record --app com.kazumaproject.conversionparity.lite -e cpu-clock:u -f 465 --call-graph dwarf,16384 --duration 40` を、各ケースのウォームアップ20回・測定150回の別実行に重ねて採取した。両言語とも初期化、ART、テストランナーも母数に含み、親子の割合は重複する。 [英語補完](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/english/phrase/EnglishPhraseDictionary.kt#L50) · [文法辞書](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/english/phrase/RuntimeGrammarRoles.kt#L94)

### 改善案と優先順位

効果は検証する仮説。探索範囲、候補件数、スコア計算、タイブレーク、入出力の順序を固定する。

| 優先度と対象 | 問題と根拠 | 改善方針と期待する効果 | 難易度・リスク・検証 |
|---|---|---|---|
| 1. 英語の単語補完と文法辞書 | `complete` の内部で索引を走査し、表記ポリシーが候補ごとに `lookup` を呼ぶ。各ブロック検索でバッファを複製し、復号配列を作る。補完と文法辞書が CPU の過半の経路とその内側にある。 | まず、同一の辞書・表記・正規化語の再照会回数を調べる。重複があれば一つの要求内で役割集合を再利用し、復号作業領域を同じ探索内で使い回す。候補の評価回数・順位を固定したまま CPU と割り当ての削減を狙う。 | 中。表記違いや正規化の統合、共有バッファの競合がリスク。完全一致のほか、大小文字・記号・辞書切替・同時要求を検証し、`en-phrase` の P95/P99 と割り当て件数を比較する。 [補完ループ](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/english/phrase/EnglishPhraseDictionary.kt#L63) · [ブロック検索](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/english/phrase/RuntimeGrammarRoles.kt#L180) |
| 2. 日本語の文節後ろ向き探索 | 文節の後ろ向き探索 P95 は16.196 ms。既に探索用キューとセッション作業領域の再利用がある。 | `enqueueBackwardExpansion` の一時データを割り当てプロファイルで確認する。候補の構築に不要な一時表現の再利用を選び、後ろ向き探索の P95/P99 と割り当て件数を削減する。 | 高。キューの同点順序とキャンセル時の状態に影響し得る。文節位置、最良候補、全候補とスコアの完全一致に加え、キャンセル・再開時の既存回帰も使う。 [文節探索](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/path_algorithm/FindPath.kt#L1700) |
| 3. 日本語のグラフ構築 | システム辞書の各トークンで表層を `getLetter` から取り出し、可変ノードを作る。長文プロファイルでは `getLetter` が子を含め3.89%、グラフ全体の P95 は7.944 ms。 | 同じ語彙トライの同じトークン位置の復号頻度を調べる。重複があれば、表層文字列だけを要求内で再利用する。位置・品詞・コストを持つノードは従来どおり生成する。 | 中。辞書の混同、読み依存の特殊トークン、辞書変更がリスク。混在入力と辞書・学習の固定ケースを照合し、`ja-long` と `ja-edit` の時間・割り当て・PSS を比較する。 [トークン展開](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/graph/GraphBuilder.kt#L874) |
| 4. 英語の初回変換 | 英語は最初のクエリに1.121〜1.497秒。エンジンの解決とは別に英語辞書を最初の利用時に同期ロードする実装がある。 | 初回クエリを辞書オープン、検証・ロード、初回探索に分けて測り、サイズ増加や仕事の移動を伴わない重複処理を探す。 | 調査は低、実装難易度は未確定。ロード失敗・フォールバック・資産検証を維持し、アプリ起動から最初の候補までを再起動試行で比較する。 [辞書ロード](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/EnglishEngine.kt#L836) |

今回追跡した変換経路の処理は CPU 上の辞書照会・探索。GPU への転送を伴う区間は確認していない。SIMD や並列探索は、計算の独立性と同点時の順序を保てる箇所をプロファイルで確認した場合に検討する。
