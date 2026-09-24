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
