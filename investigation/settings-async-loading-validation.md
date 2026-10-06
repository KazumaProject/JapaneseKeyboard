# 設定読み込みの非同期化と検証

## 作業対象

- 基点: `dev` / `be9cccd7e`。新規 worktree、ブランチ `codex/fix-settings-loading-freeze`。
- 設定キー、保存形式、外部公開 API は変更していない。
- 通常アプリとは別の `.freezeprobe` APK で検証。通常アプリのデータと既定 IME は変更しない。

## 修正した経路

Activity は依存先を取得する前に Progress を表示し、設定初期化完了を coroutine で待つ。フォント repository は Provider 経由で IO 上で取得し、ストアのコンストラクタも遅延する。Application 上の設定プリロードと codeCacheDir 操作を初期化ワーカーへ移した。サブ設定画面からの同期 AppPreference.init 呼び出しも除去した。

設定タブの XML・プログラムによる未公開 Preference 階層は IO 上で直列に構築する。View・公開・リスナー・Fragment 操作は main に戻す。AndroidX の static inflater constructor cache の同時アクセスを mutex で防ぐ。パッケージ情報、フォント、Gemma モデル一覧・選択・概要、外部辞書検証、DB 初期化、マクロ初回読み込み、検索／頻用設定用 XML 解析も非同期にした。復帰時の状態確認・更新も同じロード管理に通す。

各画面は Loading / Ready / Error と未完了処理数を管理し、全処理と Preference 初回レイアウトが完了してから Progress を終了する。失敗は再試行ボタンに切り替える。戻る操作と旧ホームのタブバーは遮らない。View の lifecycle で cancel し、未公開の結果を破棄する。設定状態・スクロール位置・検索ハイライト・既存ナビゲーション復元を保持する。フォントストアの初期化が例外で中断した場合は loaded を立てず、同じ repository で再試行できる。

## 原因について確認できた事実

修正前のフォント repository コンストラクタは `context.noBackupFilesDir` と LocalFontStore 初期化を main で実行していた。実 getter に 1,500 ms の制御遅延を入れ、修正前 APK と修正後 APK を同一 API 35 エミュレーターで比較した。

| 観測 | 修正前 | 修正後 |
| --- | --- | --- |
| コンストラクタ時間 | 1,511 ms | 0 ms |
| コンストラクタのディレクトリ取得回数 | 1 | 0 |
| 遅延するディレクトリ取得の実行スレッド | main | worker |
| 遅延中 main のサンプル | コンストラクタ経由の sleep | nativePollOnce、heartbeat 応答 |

これは残存する同期経路による停止の因果関係を示す制御実験であり、報告された自然発生フリーズの停止中スタックではない。自然発生の同一原因まで特定したとは扱わない。既存 `investigation/README.md` の過去の制御実験も参照できるが、今回の自然発生の証拠として転用しない。

## 検証結果

- Lite／Full Standard Debug の app と androidTest: BUILD SUCCESSFUL。最新ソースで再ビルド済み。
- JVM: 205 件、失敗・エラー・スキップ 0（設定、ナビゲーション補助、フォント、マクロ）。
- API 35 エミュレーターの回帰 suite: Lite 52 件（50 成功、Full 専用 2 件スキップ）、Full 52 件（全成功）。最新ソースの APK で再実行し、ナビゲーション、設定分類、フォントのキャンセル、非同期読み込み、フォント初期化失敗後の再試行を含む。
- 最後のフォント loaded フラグ修正後、最新 APK で非同期読み込み・フォントのキャンセル・コンストラクタ・初期化失敗後の再試行を再実行: Lite 15 件（14 成功、Gemma 1 件スキップ）。Full 15 件（全成功）。
- `git diff --check`: 成功。

遅延テストは設定初期化・フォント・XML・パッケージ情報・モデル探索・DB・検索を個別に保留し、Progress の表示と main heartbeat を３回ずつ確認する。戻る操作、読み込み中の再作成、XML 失敗／再試行、マクロ初回 DB 待機、旧タブと共通する新設定の全 Preference 表示も確認した。既存ナビゲーション suite は両ホームの復元、繰り返し再作成中の初期化待機、Intent 到着と１回だけの処理、設定分類／保存を確認した。

## 既存テストの扱いと限界

分類テストの４件は未変更の dev ソースを使った比較 APK でも同じ失敗を再現した。重複キーの既定ルートは legacyCommon であるため期待値を合わせ、削除済み boolean の墨流し設定を現行 ListPreference の液体インク選択肢の検証へ更新した。依存項目テストは PreferenceManager に root と対象 Preference を登録してから依存関係を設定するよう修正した。検索ルートの製品動作は変更していない。

既存フォント Provider の開始待ち latch は初期値 0 で、query 前にも「開始済み」を返していた。そのため query を実行する前に cancel する競合があり、close 待機が timeout した。開始待ち latch の初期値を 1 にして、実処理開始を検証条件にした（解放用 latch は変更しない）。

既存の `candidatePreviewsKeepNavigationHiddenAcrossRecreationAndRestoreItOnReturn` は dev 比較 APK でも ActivityScenario の待機が終わらず、35 秒で打ち切った。最終 suite からこの１件を除外し、成功扱いにしていない。プレビューの連続描画中に global idle を待つ可能性があるが、自然発生フリーズとの同一性は確認できていない。

接続済み Pixel 6 は検証開始時に接続が外れた。今回の端末結果は `emulator-5580` / Android API 35 / arm64 の結果であり、Pixel 6 の実機確認は未完了。完全なプロセス終了・復元の新規実験と自然発生の前後時間比較も未完了。画面再作成・停止復帰・初期化待ち中の Intent／バックスタック復元は既存ナビゲーションテストで確認する。

## 再実行

`investigation/probe.init.gradle` で `.freezeprobe` を指定して Lite／Full Standard Debug の app と androidTest をビルドする。通常パッケージ上では新規端末テストを skip する。各段階の遅延は `SettingsLoadDiagnostics.beforeLoad` の test-only hook で実作業境界に入れ、main 以外であること、Progress の表示、main heartbeat、戻る操作を確認する。フックは通常 null、各テストの終了時に解除する。

端末 suite の再実行（probe APK のビルド完了後に実行する）:

```sh
ADB=/Users/kazuma/Library/Android/sdk/platform-tools/adb ANDROID_SERIAL=emulator-5580 \
  python3 investigation/run_settings_async_checks.py
```

テスト用 Provider は固定 authority のため Lite／Full の test APK を同時に登録できない。実行器は他方の `.freezeprobe.test` だけを外してからインストールする。ログは `/tmp/settings-loading-device-{lite,full}-final.log` に保存する。既定 IME は切り替えない。実行器は追加した probe の IME 有効化を終了時に解除する。今回も終了時に新規 probe IME の有効化を解除し、既定 IME／有効 IME 一覧が検証前と一致することを確認した。
