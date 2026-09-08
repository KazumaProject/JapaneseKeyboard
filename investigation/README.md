# 設定フリーズの制御実験

## 結論と適用範囲

通常起動の反復ではなく、実際の処理が待つ条件を固定して調べた。**新旧設定・Lite/full に共通する、UIスレッド上のタイムアウトなしの待ち経路を2つ確認した。** ただし、実ユーザーの端末でディスク処理が停滞した事実までは確認しておらず、報告されたフリーズとの同一性は未確定。テスト用ゲートによる停止を、自然発生したデッドロックと呼ばない。

作業基点は `dev` の `bbdc7414746adeffc815d5158631e415a1167007`、アプリ 1.7.112 / code 805。専用 worktree `MarkdownHelperKeyboard-settings-freeze-investigation`、ブランチ `codex/investigate-settings-freeze`。以下の制御実験は製品コード変更前の記録。今回の改善内容と変更後の検証は `improvement-review.md` に分けて記録する。通常アプリのデータは変更していない。

## 候補ごとの証拠

| 候補 | 到達経路・成立条件 | 制御実験・対照 | 判断 |
| --- | --- | --- | --- |
| 初回の設定ファイル読み込み待ち | MainActivity の Hilt 注入 → AppModule.providesPreference → AppPreference.init → removeUnsafeLegacyGemmaHandwritingPrompt → SharedPreferences.contains → awaitLoadedLocked。初回ロードが未完了の場合、画面生成前にメインが待つ | Android 15 の実ロード用 executor をバックグラウンドで保留してから本物の MainActivity を起動。メインが awaitLoadedLocked にいることを3時点で確認し、heartbeat 不応答を確認。解除後に起動・選択した新旧ホームへの到達を確認。先に executor を解放する対照では起動する | **待ち機構の因果関係を確認**。旧設定の処理に入る前なので新旧共通。ストレージ・ロード用スレッドの遅延が必要条件。自然発生する停滞の原因は未確定 |
| 未完了の設定保存を画面停止時に待つ | AppPreference の実 setter → Editor.apply → QueuedWork。ActivityThread.handleStopActivity が未完了の保存を待つ。入力モード保存では IMEService.onFinishInputView から複数 setter が呼ばれる | 実 SharedPreferences のディスク書き込み用ロックだけを別スレッドで保留。モード保存時刻の実 setter を呼び、実ActivityをSTOPPEDへ遷移。3時点で QueuedWork 待ち・heartbeat 不応答。解除後に復帰。同じ書き込みを先に完了した対照では、ロック保持中もメインが応答 | **待ち機構の因果関係を確認**。設定を開くすべての経路で必ずSTOPが走るとは限らない。特に他アプリのSTOPは別プロセスであり、本アプリの保存待ちと直結させない |
| DB書き込み競合と初期化 | 新旧ホームの romaji_map_data_version=0 → Dispatchers.IO の初期化。Roomの別接続が書き込みトランザクションを保持 | ケースごとに初期化対象の合成辞書行を消し、実DBトランザクションを保留。初期化フラグが0の間に3回heartbeat・全スタック採取。解除後にフラグ1への完了を確認 | **この競合条件ではUIは待たない**。DBの全マイグレーション・全障害・大量データ一般を除外したわけではない |
| 動画再生スレッドが終了処理を実行できない | IMEService の背景動画解放 → ExoPlayer.release → ExoPlayerImplInternal.release | 実 ExoPlayer の playbackLooper を保留したままメインから release。ゲート解除前に戻ることと複数スタックを確認 | 約500msのライブラリ側タイムアウトで戻る。release内部の無期限待ちという仮説には反証。ただし PlayerView の Surface切断・GPU/ドライバ待ち全体は対象外 |
| 設定XML解析・新ホームの重複生成 | 旧設定選択時も初期グラフが新ホームを生成。その後旧設定へ遷移。新ホームはカード生成時に全設定XMLを解析し、onResumeでも再構築 | コードおよび過去の起動計測を確認。要素数・ループは有限。今回この処理を削って原因特定した扱いにはしない | 起動負荷の候補。永続停止の証拠なし。速くなることと報告された不具合の解消を分ける |
| full版のモデル・タッチ効果 | Zenz/Gemmaの推論サービスは別プロセス。設定の通常入口はモデルを直接同期ロードしない。効果rendererのpause/releaseは専用Handlerへpost | 関連コンストラクタ、DI、設定fragment、renderer、runtime clientを静的追跡 | 確認した経路にメインへの同期待ち戻りを伴う循環を発見していない。モデル有効時IPCや実GPUのSurface破棄は未検証 |

`awaitLoadedLocked` はロード完了通知までの `wait()`、`QueuedWork` は未完了処理の待機であり、アプリが一定時間でUIに戻す設計にはなっていない。これは「applyは非同期だから画面は止まらない」という見方を否定する一方、「このアプリ固有のロック循環を発見した」という意味ではない。

## 既存資料の扱い

- `47698281b` の大量辞書・起動診断、`fe7faf583` の起動処理削減、`001e44de0` のナビゲーションコンテナ修正を確認。修正の存在自体は原因の証明に使っていない。
- `/tmp/settings-freeze-final/analysis.md` には7,040行の測定と、原因特定基準を満たす候補0件が記録されていた。HardwareRendererや画面inflateのスタックを含むが、今回の保存待ち・読み込み待ちと同一事象とは断定しない。
- 初期の実験にはActivityScenario補助Activity待ちによる終了遅延があった。メインはnativePollOnceで待機しており、アプリの停止とは区別した。実験用Activityを直接終了するようテスト側だけを修正。
- メソッドサンプリングによるトレース採取中、ARTの `tlsPtr_.method_trace_buffer == nullptr` チェックを伴うSIGSEGVを観測。診断の影響を疑い、この採取方式を撤去。最終実験は外部Perfettoで採取する。該当失敗ログもresultsに保持し、製品のフリーズ原因に数えない。
- ABI指定ビルドではAPKがoutputsではなくintermediatesに出るため、実行器はapplicationIdに一致する最新メタデータからAPKを選び、インストールしたAPKのSHA-256を保存する。

## 再実行方法（こちらで実行するための手順）

通常のアプリを操作する必要はない。API 35のエミュレーター上に `.freezeprobe` を付けた専用APKを入れる。製品パッケージではテストはskipされ、ゲートや合成データ作成は実行されない。

```sh
export ANDROID_HOME=/Users/kazuma/Library/Android/sdk
export JAVA_HOME=/Users/kazuma/Library/Java/JavaVirtualMachines/jbr-17.0.12/Contents/Home
./gradlew -I investigation/probe.init.gradle -Pandroid.injected.build.abi=arm64-v8a \
  :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest \
  :app:assembleFullStandardDebug :app:assembleFullStandardDebugAndroidTest
python3 investigation/run_matrix.py
```

fullの新しいworktreeでは、ビルド前に `git submodule update --init --recursive` が必要。モデルはプロジェクト既存のビルド処理で用意する。

単独実験:

```sh
python3 investigation/run_contention_probe.py --edition full
python3 investigation/run_contention_probe.py --edition full \
  --test com.kazumaproject.markdownhelperkeyboard.diagnostics.ColdSettingsReadProbeTest \
  --home legacy --cold-mode held
```

`--home new`、`--cold-mode drained` が対照条件。コールドロードは各回新しいプロセスで実行し、ケース間のメモリキャッシュを持ち越さない。DB実験も対象2行の合成データをケースごとに初期状態へ戻す。

実行器は180秒でタイムアウトし、レポート・ANR情報・トレースを採取して専用アプリを停止する。ゲート自体にも15秒（動画は10秒）の解除上限がある。Android内部のロックアクセス用にエミュレーターのhidden_api_policyを一時変更し、最後に元へ戻す。専用IMEの有効化も元へ戻し、既定IMEは変更しない。

results内の各実行ディレクトリに以下を保存する（Git管理外）:

- `apks.txt`, `revision.txt`, `device.txt`: 対象コード・APK・OSの識別情報。
- `instrumentation.txt`, `reports.tar`: 検証結果、制御条件、複数時点の全Javaスレッドスタック。
- `system.pftrace`: Perfettoのスケジューリング・Activity・描画・Binderの実行トレース。
- `lastanr.txt`, `logcat.txt`: 補足情報。プロセス終了時はlogcatが取得できない場合がある。

## 根拠のソース

- 本worktree: `AppPreference.kt:907`（初期化）、`:1011`付近（旧プロンプト確認）、`:1020`（apply）、`IMEService.kt:5053`（入力画面終了）、`:11595`（入力モード保存）。
- ローカルAndroid SDK API35: `android/app/SharedPreferencesImpl.java:281`（ロード待ち）、`:679`（ディスク書き込みロック）、`android/app/ActivityThread.java:5767`、`android/app/QueuedWork.java:186`（画面停止時の保存待ち）。[AOSP QueuedWork](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-30/+/refs/heads/main/android/app/QueuedWork.java)にも同じ待ち機構の説明がある。実験対象の実装確認はローカルAPI35ソースを使用。
- GradleにあるMedia3 1.5.1 sources: `ExoPlayerImplInternal.release` と `ExoPlayer.DEFAULT_RELEASE_TIMEOUT_MS`。

## 残る仮説と次にこちらで行える実験

1. **実ファイルの読み書きの停滞源**: 起動直前からPerfettoのI/O・スケジューリングを採取し、ロードexecutorの待ちと実ストレージ待ちを分ける。合成データは本アプリが保存できる設定の形式・サイズに限定し、過大な任意データで停止しただけの結果は採用しない。
2. **IPCとSurfaceの待ち**: 別プロセスの入力欄から専用IMEの実ボタンで設定を起動し、背景動画あり/なし・効果あり/なしで、Surface切断、RenderThread、Binder双方を採取する。今回のrelease単体の結果でGPU待ちを除外しない。
3. **復元中の遷移**: 保存処理中の設定再表示・画面回転・プロセス復元を制御し、保存待ちがどの起動経路まで持ち越されるか確認する。特にコールド起動と既存Activityへの復帰を区別する。

原因調査段階では製品の修正を入れていない。どの実条件が待ちを発生させるかを確かめず、XMLキャッシュや一律のスレッド移動を「フリーズ修正」として扱わない。利用者のログ提出を次工程の前提にはしない。

## 改善前の最終検証結果

Android 15 / arm64 エミュレーターで、外部Perfetto方式による **10条件・計22テストが成功（failed=0）**。

- Lite/fullそれぞれ: 保存待ちあり/保存完了の対照 × 新旧、DB書き込み競合 × 新旧、動画解放待ちの7テスト。
- Lite/fullそれぞれ: コールドロード保留/先行完了 × 新旧の4条件を別プロセスで実行。
- 各実行のAPKハッシュ、結果、トレースサイズは `evidence-summary.json`、代表的な停止中メインスタックは `evidence-main-stacks.txt` に保存。全スタックとPerfetto本体は同JSONが指すresultsディレクトリにある。
- hidden_api_policyは検証前のnull（設定なし）へ復元。既定IMEは検証前後とも通常full版のまま。
- 全テスト成功は **制御した待ち機構と対照結果が一致した** という意味であり、自然発生するフリーズの原因特定・修正完了・他条件の安全性を意味しない。
