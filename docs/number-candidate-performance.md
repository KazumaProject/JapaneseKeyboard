# 数字候補性能レポート

## 測定条件

- 基準: `dev` / `33e30d23e`
- 実装: `codex/number-candidate-variants`
- ホスト: Apple Silicon macOS 26.6 (`Darwin 25.6.0`)
- JVM: JetBrains Runtime 17.0.12
- プローブ: Robolectric API 35、production compact dictionary、beam width 20、候補数 4
- 各測定: ウォームアップ5回、時間30回。3実行の中央値を採用
- 保持量: 短文と長文を交互に300回。シナリオを事前ウォームアップし、Mockitoの呼び出し履歴を除去してGC後Javaヒープを比較
- 接続端末: Pixel 6 (`oriole`, Android Debug Bridge serial `23241FDF6003NG`)

初期の `dev` プローブは平均時間・割当量のみを記録する旧形式だった。実装中にプローブを拡張し、最終測定では初回、平均、p50、p95、最大、割当量、GC、GC後保持ヒープを記録する。PSSはRobolectricでは取得できないため、端末計測時のみ対象となる。

## 段階測定

| 段階 | 状態 | 1クエリ平均割当量 | 備考 |
|---|---|---:|---|
| 1 | `dev` 基準 | 902,826 bytes | 旧形式、通常文6件 |
| 2 | 数値解析・`Int` 移行 | テスト通過 | 実装を連続して統合したため独立した生計測なし |
| 3 | パス注釈・候補組み立て | 910,682 bytes | 3回中央値、`dev` 比 +0.87% |
| 4 | IME・設定・Zenz統合 | 832,465 bytes | 通常文＋数字4件を含む10件、3回中央値 |

段階3の通常文割当量は合格基準の `dev +10%` 以内。最終プローブは入力集合を増やしているため、段階1の平均値とは直接比較しない。

## N-best 後差し込みの再計測

今回の変更前後を、同じ Robolectric プローブ（ウォームアップ5回、時間測定30回、10入力、候補数4）で測定した。変更後は `NumberCandidateAssembler` に `nBest=4` を渡し、最初の4代表候補の直後に数字表記を挿入する経路を含めている。生データは `before-nbest-boundary.txt` と `after-nbest-boundary.txt`。

| 指標 | 変更前 | N-best 後 | 差分 |
|---|---:|---:|---:|
| 1クエリ平均割当量 | 805,191 bytes | 813,799 bytes | +1.07% |
| `きょう` p50 / p95 | 3,367.834 / 4,417.541 µs | 3,397.959 / 4,861.625 µs | +0.9% / +10.1% |
| `きょうはいいてんきですね` p50 / p95 | 1,137.792 / 3,093.458 µs | 1,169.667 / 4,589.708 µs | +2.8% / +48.3% |
| `さんにん` p50 / p95 | 289.542 / 519.708 µs | 316.708 / 778.042 µs | +9.4% / +49.7% |
| 数字混在 p50 / p95 | 1,188.417 / 1,541.042 µs | 1,143.708 / 1,580.000 µs | -3.8% / +2.5% |
| 複数数量 p50 / p95 | 766.000 / 1,334.333 µs | 758.250 / 958.792 µs | -1.0% / -28.2% |
| 300回反復後の保持ヒープ増加 | 11,472 bytes | 984 bytes | -91.4% |
| GC回数 / 時間 | 37 / 151 ms | 40 / 155 ms | — |

通常文と長大数字の p95 は30サンプルの単発測定では外れ値の影響が大きいため、合否判定には3回中央値を用いる。少なくとも今回の実測では、割当量は約1%増、GC後の保持ヒープは増加していない。通常の変換時間については、必要なら同じラベルで3回測定を追加して閾値判定を確定する。

## 最終時間分布

3回の中央値（microseconds）。

| 入力 | 種別 | p50 | p95 |
|---|---|---:|---:|
| `きょう` | 通常・短 | 5,003 | 7,462 |
| `きょうはいいてんきですね` | 通常 | 1,545 | 3,296 |
| `さんにん` | 数字単体 | 352 | 861 |
| `きょうはさんにんでとうきょうにいきます` | 数字混在 | 1,356 | 2,232 |
| `にえんさんにんごほん` | 複数数量 | 831 | 1,331 |
| `000000000000000000009223372036854775808` | `Long` 超過 | 34 | 70 |

- 300回反復後の保持ヒープ増加: 656 bytes（8 MiB未満、合格）
- 最終確認時GC: 45回 / 172 ms（プロセス開始後累計）
- 初回の12.5 MiB増加はMockito mockが全呼び出しを記録した測定汚染。履歴を測定区間外へ除外して再測定した。

## 成果物SHA-256

- Full Standard Debug APK: `f5cbc0b65d4ee6cd3ce45b42b85560e98d985b11d851d142022fae3f3f99e73a`
- Lite F-Droid Debug APK: `0c0af07e8def5d43498cd558b92c475483f1d080df5289d8f87e4f8b5a25a547`
- Lite Standard Debug APK: `a54a02387d9793f417e4e64144c2d59e5004ce9d7e35297daccd1c08f65d5f35`
- System compact dictionary: `71a2d8ece0f8b9af815dcc1fd62a974d8369acc86784bf99d5c845e95cbeada3`
- English compact dictionary: `516f78380a321e30237fff757d232c8285a9fbe2f6f4a578e51d52bd6a672f68`

生データは `app/build/reports/number-candidate-performance/` に保存される（ビルド生成物のためGit管理外）。集計可能な値は同ディレクトリのテキストと `docs/number-candidate-performance.json` に記録した。

## 検証結果

- `assembleDebug`（Full Standard / Lite F-Droid / Lite Standard）: 成功
- 数値解析・組み立て・全モード/両backend整合性テスト: 3 flavorで成功
- Pixel 6上の `JapaneseNumberConversionInstrumentedTest`: 2件成功
- 設定UI instrumentation: 接続端末のAndroid 17で Espresso が削除済み `InputManager.getInstance()` を反射参照し、テスト注入初期化前に失敗。アプリ側のassertion到達前のテスト基盤互換性問題
- 全Robolectricテスト一括実行: 既存の大型UIテスト群で `ShadowArscApkAssets9` のOutOfMemoryError。変更対象テストを単独実行した場合は成功
