# ベンチマークの再現手順

## 端末とビルド

同じ端末・OS、画面リフレッシュレート、温度・電源条件を固定する。実機とエミュレーターの結果は別に記録する。比較時は同じエディタ、辞書、設定、入力を使い、変更前後の実行順も入れ替える。アプリは Lite Standard Debug を使用し、Zenz・Gemma・LiteRT が利用できない条件にする。ソースを取得済みで SDK と依存関係が揃っていれば、この手順はオフラインで実行できる。Release 相当での確認は別の測定として扱う。 [Lite 構成](../../app/build.gradle#L68)

既存のインストールとデータを区別できるよう、検証用 APK を次の一時的な Gradle init script で作る。ビルドを比較するときもパッケージ名を揃える。

```bash
performance_init=$(mktemp /tmp/sumire-perf-init.XXXXXX)
performance_manifest=$(mktemp /tmp/sumire-perf-manifest.XXXXXX)
sed -e 's/com.kazumaproject.markdownhelperkeyboard.settingsbackuptest/com.kazumaproject.inputperformance.lite.test.settingsbackuptest/' \
    -e 's/com.kazumaproject.markdownhelperkeyboard.lite.localfonttest/com.kazumaproject.inputperformance.lite.test.localfonttest/' \
    app/src/androidTest/AndroidManifest.xml > "$performance_manifest"
cat > "$performance_init" <<'GRADLE'
allprojects { project ->
    project.plugins.withId('com.android.application') {
        if (project.path == ':app') {
            project.androidComponents.finalizeDsl { android ->
                android.defaultConfig.applicationId = 'com.kazumaproject.inputperformance'
                android.sourceSets.androidTest.manifest.srcFile(
                    project.providers.environmentVariable('SUMIRE_INPUT_PERF_MANIFEST').get()
                )
            }
        }
    }
}
GRADLE
SUMIRE_INPUT_PERF_MANIFEST="$performance_manifest" ./gradlew --offline -I "$performance_init" \
  :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest
adb install -r app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/liteStandard/debug/app-lite-standard-debug-androidTest.apk
```

検証用 test APK では未使用のテスト用コンテンツプロバイダーの authority も変更しておく。ほかの検証用 APK が既にインストールされている端末でも共存できる。

Mozc の生成入力とコミット済み出力が比較元と一致する場合、調査用ビルドに `-x :app:generateMozcSegmenterData` を指定できる。この場合も整合性検査は実行する。辞書データを変更した比較では通常の生成を使う。 [ビルド時の検査](../../app/build.gradle#L1289)

## 実行と集計

1. オフライン動作も検証する場合は、テスト端末のネットワークを無効にする。画面をロック解除して縦向きにし、向きは計測用エディタを開いてから検証する。接続先が複数ある場合、`adb -s SERIAL` で対象を固定する。
2. 次のテストを実行する。既定のサンプル数は日英それぞれ 200 で、ウォームアップを 20 回行う。まず少数で経路を確認できる。TenKey の「あ」に「さ」を追加、QWERTY の `ke` に `y` を追加し、候補をタップして確定する。候補の一時表示、ライブ変換、学習、装飾エフェクトを除外した通常入力シナリオ。 [試験本体](../../app/src/androidTest/java/com/kazumaproject/markdownhelperkeyboard/FastInputMatrixInstrumentedTest.kt#L74)
3. 出力に含まれる `INPUT_PIPELINE_REPORT` のパスを `adb pull` で取得する。ファイルは言語ごとのサンプルを保持する。候補はプライマリ一覧の pre-draw で対象の候補ビューと候補配列を同時に読み取る。確定用のタップは候補ビューの座標が安定してから送信する。必須の文字変更・候補・確定は期限内に観測できなければテストを失敗させる。HW フレームのコールバックがなかったサンプルは 0 を記録し、集計では未取得として扱う。

```bash
adb shell am instrument -w -r \
  -e class 'com.kazumaproject.markdownhelperkeyboard.FastInputMatrixInstrumentedTest#offlineInputPipelineLatency' \
  -e pipelineSamples 200 -e pipelineWarmup 20 \
  com.kazumaproject.inputperformance.lite.test/androidx.test.runner.AndroidJUnitRunner
python3 tools/performance/summarize-input-pipeline.py before.tsv after.tsv
```

サマリーは平均、nearest-rank の P50 / P95 / P99、最大を報告する。少数サンプルの P99 は上位のごく少数、100 未満では最大と一致することがある。同じ入力でプライマリ一覧に渡された候補文字列の順序と確定結果を比較する。内部スコアはエンジンの回帰試験で比較する。

DOWN と UP はタップ送信直前の時刻。DOWN 起点には押下中の待ちと DOWN のイベント配信が含まれる。タップ後の処理を比較する場合は UP 起点を併記する。

## Perfetto の収集

アプリの Trace と OS のフレーム・スケジューラ・GC・プロセス統計を同じトレースに収録する。最初にトレースなしで同じワークロードを実行し、次に収録しながら実行して観測負荷を評価する。収録は最大 300 秒。処理が収録範囲を超える場合は試行を分割する。 [Perfetto CLI](https://perfetto.dev/docs/reference/perfetto-cli)

```bash
adb shell perfetto --txt --detach=sumire-input \
  -c - -o /data/misc/perfetto-traces/sumire-input.pftrace \
  < tools/performance/input-pipeline.perfetto.pbtxt
# 同じ instrumentation コマンドを実行
adb shell perfetto --attach=sumire-input --stop
adb pull /data/misc/perfetto-traces/sumire-input.pftrace
```

| 計測点 | この測定での観測点 |
|---|---|
| T01–T02 | IME ルートの DOWN / MOVE / UP / CANCEL。入力イベント時刻と `uptimeMillis` の受信差は ms 単位 |
| T03 | IME ルートのフォールバック対象解決。各キー View 内の方向判定単独は未分離 |
| T04–T05 | TenKey・QWERTY・Sumire のアクションと Main の文字列処理 |
| T06・T15 | 主要な InputConnection の書き込み呼び出し。テストのエディタで文字変更と確定を別に観測 |
| T07–T08 | `sessionId/revision` 付きの要求公開と Collector 開始。専用変換スレッドの待ち単独は未分離 |
| T09–T11 | 辞書準備、英語の prefix・候補生成・順位、通常のかな漢字 DP/N-best、候補統合。かな漢字のグラフには既存のセッション計測を併用 |
| T12–T13 | 候補の公開・submit とアダプターの Diff 処理 |
| T14 | エディタフレームの提出、候補ビューの pre-draw、利用可能なら SurfaceFlinger の FrameTimeline |
| T16 | `dalvik` の GC と scheduler、Main thread の停止、プロセスのメモリ統計 |

TSV は単調増加の `elapsedRealtimeNanos` を使う。T08 は同じプロセスでの `System.nanoTime` の差。時計の異なる絶対値を引き算さない。試行は `Sumire benchmark <lang> <sample>` の非同期スライスで識別する。単発試行はテストの入力区間と各候補要求を照合できる。連続・マルチタッチ時の厳密な追跡にはタッチと候補世代の対応付けが必要。`registerFrameCommitCallback` は HW フレームの提出、候補の pre-draw は View の描画直前を知らせる。画面表示の時刻には SurfaceFlinger 側の情報が必要。 [計測コード](../../app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/CandidatePerfTrace.kt#L34) · [Android ViewTreeObserver](https://developer.android.com/reference/android/view/ViewTreeObserver#registerFrameCommitCallback(java.lang.Runnable))

Perfetto のプロセス名がアプリ起動直後に `zygote64` と記録される場合、`Sumire T01 touch DOWN` のスレッドから `upid` を取得する。同じ `upid` で FrameTimeline と RSS を照合する。計測用エディタと IME は同じプロセスで動くため、プロセス全体の統計には両方が含まれる。

長文、スペルミス、大小文字、複数文節、連続削除・再入力、高速フリック、起動・再表示、長時間、外部アプリ間切り替え、メモリ圧迫、省電力、リフレッシュレート差は条件別の追加測定。初期試験の結果をこれらの条件に拡張しない。
