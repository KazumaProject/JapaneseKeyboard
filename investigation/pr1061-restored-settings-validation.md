# PR #1061: 詳細設定の復元時に読み込みを待たないことの確認

2026-09-24、Pixel 6（Android 17）で `.lite.freezeprobe` の隔離APKを使用。既定IMEは変更していない。

テーマ設定を表示してホームへ戻り、バックグラウンドのアプリプロセスを終了してからタスクを再表示した。設定ファイルの読み込み遅延を確実に作るため、**計測用APKだけ** `AppPreference.init()` の前に4秒の待ちを入れた。待ちと計測ログはPRの製品コードから除去済み。

|  | 修正前 | 修正後 |
| --- | ---: | ---: |
| `am start -W` の `TotalTime` | 4,545 ms | 454 ms |
| `MainActivity.super.onCreate()` | 設定初期化の完了まで戻らない | 初期化中に戻る |
| テーマ Fragment の `onCreatePreferences()` | 初期化が終わる前に開始し、UIスレッドで待つ | 初期化が終わってから開始 |

修正前のログ順序: `MainActivity before super` → `KeyboardThemeFragment onCreatePreferences` → `Initialization completed` → `MainActivity after super`。修正後: `MainActivity before super` → `MainActivity after super` → `Initialization completed` → `KeyboardThemeFragment onCreatePreferences`。復元後のテーマ画面も表示できた。

遅延を除いたPRのコードでは、`SettingsNavigationLayoutInstrumentedTest` がPixel 6で9件すべて成功した。新しいテストはテーマ画面の復元（新旧ホーム）と、復元Fragmentの生成順序を検証する。Lite Standard Debug / AndroidTest APK、Full Standard Debug APKのビルドも成功。

これは設定読み込みが遅い場合の復元経路を制御して確認した結果であり、すべての端末の間欠的な停止原因を特定した結果ではない。

## 初期化待ち中の再作成回帰テスト

2026-09-24、Pixel 6（Android 17）で `SettingsNavigationLayoutInstrumentedTest` を実行し、12件すべて成功した。初期化ゲートを閉じた状態で、詳細画面を復元してから2回再作成し、解放後も同じ詳細画面と直前のバックスタック項目へ戻れることを新旧ホームで確認した。

辞書画面指定のコールド起動と、初期化待ち中の `onNewIntent()` の両方で、再作成後に辞書画面へ一度だけ遷移することも確認した。読み込み完了後の通常再作成では遷移要求を再実行しない。Lite Standard Debug APK / AndroidTest APK と Full Standard Debug APK のビルドも成功した。

## 表示中の `STARTED` 状態での初期化

2026-09-25、API 28 エミュレータで `SettingsNavigationLayoutInstrumentedTest` を実行し、13件中12件成功、Android 10 以降専用の復元順序テスト1件をスキップした。新しい `settingsContentInitializesWhileActivityIsStartedWithoutResuming` テストでは初期化ゲートを解放した後も Activity を `STARTED` に保ち、設定初期化完了、NavHost の生成・接続・表示、ナビゲーショングラフの設定を確認した。

同日、Pixel 6（Android 17）で同クラスを実行し、12件すべて成功した。最初の実行では端末が Dozing 状態だったため ActivityScenario が Activity を `STOPPED` と扱い失敗したが、端末を起こした後の再実行は成功した。API 28 と Pixel 6 で状態保持、バックスタック、辞書画面要求の既存テストも成功した。

Lite Standard Debug APK / AndroidTest APK と Full Standard Debug APK のビルドに成功した。
