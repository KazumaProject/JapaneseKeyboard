# カスタムローマ字配列の画面入力設定

## 変更内容

`dev` (`dc6e4ec86`) を起点とする `fix/custom-romaji-behavior` worktree で実装。レビュー時に最新の `origin/dev` (`461ddb9d0`) を取り込んだ。
カスタム配列の詳細画面に二つの設定を追加した。

- **同じ子音の連続を「っ」にする**：有効なら `ppa → っぱ`。無効なら登録ルールに従い、`pp → っ` があれば `ppa → っあ`。
- **子音の前・確定時の n を「ん」にする**：有効なら `kanta → かんた`、確定時の末尾 `n → ん`。無効でも明示的な `nn → ん` は有効。

両設定とも新規・既存・旧形式インポートの初期値は有効。配列ごとに保存し、書き出し・読み戻しでも保持する。Room 47 → 48 で二列を追加し、登録済みテーブルは変更しない。画面入力で使われていた旧グローバル「全角n」設定は配列別設定に置き換えた。

画面上のQWERTYとカスタムレイアウトだけが新しい `CustomRomajiScreenConverter` を使用する。自動規則の判定は半角・全角の小文字英字に対応し、通常のテーブル照合は登録されたキーと出力を保持する。大文字は従来のShift／直接英字入力として扱う。

物理キーボードとデフォルト配列は既存の `RomajiKanaConverter` を引き続き使用し、このクラスには変更を加えていない。共有するEnter／Space処理では物理入力を明示して画面用設定を適用しない。設定だけの更新では物理入力の変換器を再生成しない。

自動促音はデフォルトQWERTYと同じ子音集合を使用する。`nn`、母音の重複、`tch` は自動促音の対象外。自動撥音は次が母音・y・n以外の英字の場合と、確定時の末尾nに適用する。記号・空白の前のnは自動確定しない。

Space変換では、末尾nを直した後に更新前の候補を選んでしまう不具合も実機で検出した。カスタムでは固定64ms待ちではなく、その読みの候補が反映されたことを確認してから変換する。入力・配列・入力先が変わった場合は古い処理を適用しない。候補が2秒以内に取得できなければ、補正した読みを保持し、古い候補を選ばない。二回目以降のSpaceは待機せず、現在の候補を順送りする。レビューでこの順送りの退行を修正し、実機テストに連続Spaceを追加した。デフォルトと物理入力の待機処理は変更していない。

## 自動検証

- 全312登録ルール × 半角／全角：自動処理を無効にした時の登録値の尊重。
- 253後続規則 × 2文字幅 × 2撥音設定 × 4連続回数 = **4,048組み合わせ**：一文字入力と一括入力の両方で促音を確認。
- 二設定の全4通り、nの境界、独自登録・未登録、削除・再入力・確定。
- 固定シードで250本の20語入力列について、一括・一文字・ランダム分割の結果を比較。
- デフォルトの全英字キーと子音を追加した入力列について、588入力列の各打鍵の結果を既存デフォルト変換器と比較。
- 物理入力の半角／全角 × 画面設定4通りで既存の入力結果を確認。
- 実際の旧形式インポート、全設定のGson往復、Room移行・スキーマ検証・再オープン。
- 旧QWERTY経路では `yappari → やっあり`、新経路では `やっぱり` になることを実際のKotlinコードで検証。

変異テストは `investigation/custom-romaji-mutations.py` で実行する。通常実装で8テスト成功を確認してから、①子音を二文字消費、②半角判定の欠落、③撥音設定の無視をそれぞれ入れた一時コピーをコンパイルし、テストが失敗することを確認する。製品ソースは変更しない。

## 再実行

Android SDKとJDKを設定した上でリポジトリのルートから実行する。

```sh
./gradlew :core:testDebugUnitTest --tests '*Romaji*' --tests '*Physical*'
./gradlew :app:testLiteStandardDebugUnitTest --tests '*Romaji*' --tests '*NgWordMigration*' --tests '*CustomToggle*' --tests '*CustomKeyboardShift*' --tests '*Qwerty*' --tests '*Physical*' -Pkotlin.compiler.execution.strategy=in-process
python3 investigation/custom-romaji-mutations.py
./gradlew :app:connectedLiteStandardDebugAndroidTest -I investigation/custom-toggle.init.gradle -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.CustomRomajiBehaviorDeviceTest -Pkotlin.compiler.execution.strategy=in-process
```

実機テストは専用アプリIDを強制し、実際の設定画面のスイッチとソフトキーを操作する。終了時に元のIME選択を復元する。

## 検証結果

- 関連するJVMテスト：app 174件、core 17件、合計191件。失敗・エラー・スキップなし。
- Pixel 6（Android 17）：設定画面を操作し、QWERTY／カスタムレイアウト × 設定4通りで、入力・削除後の再入力・Enter確定・Space変換・再度Spaceでの候補順送りを確認。
- 変異テスト：通常実装は8件成功。子音の完全消費、半角判定漏れ、撥音設定無視の3変異はすべて検出。
- `git diff --check` 成功。元のdev worktreeは変更なし。

実機操作は通常表示で検証した。フローティング表示や別機種での実機操作は未実施。文字幅・境界条件・長い入力列などは上記の自動テストで検証している。
