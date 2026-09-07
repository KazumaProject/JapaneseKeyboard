# Enter の Gboard 比較テスト

Enter の確定・改行・エディタアクション・キーイベントを検証する。期待値は端末で観測した結果だけを採用し、実装から推測して作らない。公開 API と CI 構成は変更していない。

## テストの構成

- `GboardEnterBaselineTest`：保存済みの入力属性と実測結果を使う、Enter 判断のパラメーター化単体テスト。
- `EnterActionTest`：送出の内容・アクション ID・回数と、接続側の失敗時に別の操作を追加送出しないことを検証する。既存動作の回帰と Gboard 実測を区別する。
- `EnterProbeInstrumentedTest`：本物の IME キーを操作し、Enter 前後の文字列・選択・未確定範囲・アクションを記録／比較する。ソフト Enter はタップ、物理 Enter は修飾状態付きキーイベントで入力する。
- `EnterObservationComparisonTest` と `test_compare.py`：改行欠落、誤送信、二重アクション、確定漏れ、初期状態不一致を比較器が検出することを確認する。

`EnterProbeCase.all()` の240ケースは次の内訳。

| 条件 | ケース数 |
|---|---:|
| 9アクション × 8フラグ組み合わせ × 未確定文字の有無 | 144 |
| 入力タイプ10種類 × 8アクション | 80 |
| 編集位置・空欄・範囲選択 | 6 |
| 連打・長押し・物理 Enter・修飾キー・テンキー Enter | 7 |
| 変換中・文節変換中・未確定文字の途中 | 3 |

アクション番号0〜7は Android の定数。番号8は ID 12345 / ラベル `Probe action` のカスタムアクション。フラグ値の bit 0 は MULTI_LINE、bit 1 は IME_MULTI_LINE、bit 2 は NO_ENTER_ACTION。

## ビルドと単体テスト

以下のコマンドはリポジトリのルートで実行する。

```sh
./gradlew :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest
./gradlew :app:testLiteStandardDebugUnitTest \
  --tests '*GboardEnterBaselineTest' --tests '*EnterActionTest' \
  --tests '*InputTypeExtensionTest' --tests '*EnterObservationComparisonTest' \
  --tests '*KeyInputBehaviorDispatcherTest' --tests '*PhysicalShortcutMatcherTest'
python3 -m unittest discover -s tools/enter -v
```

## 実機の準備と比較

`ANDROID_SERIAL` に対象端末を指定する。端末の画面を点灯してロックを解除し、使用する IME をあらかじめ有効化しておく。

```sh
adb install -r app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
adb install -r app/build/outputs/apk/androidTest/liteStandard/debug/app-lite-standard-debug-androidTest.apk
python3 tools/enter/export_baselines.py --output /tmp/enter-baselines \
  app/src/test/resources/enter/gboard-observations.json \
  app/src/test/resources/enter/gboard-*-observations.json
adb push /tmp/enter-baselines/. \
  /sdcard/Android/data/com.kazumaproject.markdownhelperkeyboard.lite/files/enter-baselines/
python3 tools/enter/run_device.py --mode compare --surface normal --keyboard TENKEY \
  --cases 'action-.*-committed'
python3 tools/enter/run_device.py --mode compare --surface normal --keyboard TENKEY \
  --cases 'action-.*-composing' --setup key_1 --before-text abcあ
```

`--mode compare` はこのアプリの IME を選択して比較し、終了後に元の IME に戻す。Gboard 同士の自己比較は受け付けない。

`--surface floating` でフローティング経路を実際に表示して検証する。自分の IME の `--keyboard` は TENKEY / SUMIRE / QWERTY / ROMAJI / GOJUON。指定した表示設定・キーボード順序と選択 IME は終了時に復元する。テスト中は端末を操作したり別の UIAutomator セッションを開始したりしない。

`probeMode` の指定がない通常の instrumentation 実行では、この実機操作テストはスキップする。専用の `run_device.py` はこの引数を明示して実行する。

入力タイプ・編集位置・キー操作の追加93ケースと、変換中の個別ケースは次のように実行する。数値 Enter の指定は、後述の標準レイアウト確認を前提とする。

```sh
python3 tools/enter/run_device.py --mode compare --surface normal --keyboard TENKEY \
  --cases 'type-.*|position-.*|operation-.*' --enter builtin-number-enter
python3 tools/enter/run_device.py --mode compare --surface normal --keyboard TENKEY \
  --cases action-1-flags-5-converting --setup 'key_1;key_space' \
  --before-text abcあ --state-marker key_enter
```

`run_device.py` は正規表現やセレクターをデバイス側シェル向けに引用し、テスト失敗を非ゼロの終了コードとして返す。`adb am instrument` 自体はテスト失敗でも終了コード0になることがある。

数値キーボードのアイコン Enter はアクセシビリティノードを持たない。**標準の4×4数値レイアウトを使用していることを確認した場合だけ** `--enter builtin-number-enter` を指定する。数字キーの配置を検証し、その時点の座標から4行4列目をタップする。カスタム数値レイアウトには使用しない。

## Gboard の記録

```sh
python3 tools/enter/run_device.py \
  --ime com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME \
  --cases 'action-.*-committed'
python3 tools/enter/run_device.py \
  --ime com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME \
  --cases 'action-.*-composing' --setup a --before-text abcあ
```

記録先はアプリの外部ファイル領域 `enter-observations/`。ケース ID、実際の EditorInfo、IME バージョン、Android API、Enter 前後の結果、InputConnection 呼び出しログを保存する。`after` は操作ごとのスナップショット。アクション履歴は Enter 前のクリア以降の累積。

`--setup` は実際のキーセレクターをセミコロンで区切る。セレクターは resource ID の末尾、表示文字列、contentDescription の完全一致。`selection:4` は入力先のカーソルを4へ移動する。`left` / `right` は準備用の物理矢印キー、`expect:文字列` は準備途中の文字列検証。未確定範囲は操作後に検証し、エディタに偽の composing span を設定しない。

変換状態では `--before-text` と可視ノードの `--state-marker` も指定する。日本語 QWERTY の変換準備例は `--setup 'a;スペース'`、自分のテンキーは `--setup 'key_1;key_space'`。準備結果が違う場合は Enter を押さず失敗として記録する。`--enter` で Enter のセレクターを明示できる。

未確定文字の途中は、Gboard の内部カーソルを Android の選択位置だけでは確認できないため、途中への挿入・削除を検証してから Enter を押す。

```sh
# Gboard: --ime を上記の Gboard ID に指定
python3 tools/enter/run_device.py --ime com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME \
  --cases action-1-flags-5-composing-middle \
  --setup 'a;n;a;左;i;expect:abcあいな;削除;expect:abcあな' --before-text abcあな
python3 tools/enter/run_device.py --mode compare --surface normal --keyboard ROMAJI \
  --cases action-1-flags-5-composing-middle \
  --setup 'key_a;key_n;key_a;left;key_i;expect:abcあいな;key_delete;expect:abcあな' --before-text abcあな
```

文節変換は「私は日本人」で、1回目は「私は」のみ確定、2回目は残りを確定、3回目は改行する。自分の IME は `--bunsetsu` で文節分離と文節カーソル移動を一時的に有効にし、終了時に復元する。左右移動で後続文節の候補を読み込み、先頭文節へ戻して初期表示を揃える。辞書の候補が変わった場合は準備失敗として扱う。

```sh
python3 tools/enter/run_device.py --ime com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME \
  --cases action-1-flags-5-segment \
  --setup 'w;a;t;a;s;h;i;h;a;n;i;h;o;n;j;i;n;n;スペース' \
  --before-text abc私は日本人 --state-marker key_pos_ime_action
python3 tools/enter/run_device.py --mode compare --surface normal --keyboard ROMAJI --bunsetsu \
  --cases action-1-flags-5-segment \
  --setup 'key_w;key_a;key_t;key_a;key_s;key_h;key_i;key_h;key_a;key_n;key_i;key_h;key_o;key_n;key_j;key_i;key_n;key_n;key_space;right;left' \
  --before-text abc私は日本人 --state-marker key_return
```

手動記録は debug の `EnterProbeActivity` を起動し、「Capture BEFORE」→ Enter 操作 →「Save AFTER」の順で行う。Intent extras は `caseId`、`inputType`、`imeOptions`、任意の `actionId` / `actionLabel` / `text` / `selectionStart` / `selectionEnd`。

## 期待値の扱いと検証範囲

保存済み基準は Pixel 6 / Android API 37 / Gboard 18.1.3.962075747-release-arm64-v8a の実測。Gboard は既存の日本語 QWERTY・片手表示のまま測定した。

| ファイル | 内容 |
|---|---|
| `gboard-observations.json` | 未確定文字なし72ケース |
| `gboard-composing-observations.json` | 日本語「あ」の未確定文字あり72ケース |
| `gboard-type-observations.json` | 入力タイプ別80ケース |
| `gboard-operation-observations.json` | 編集位置・キー操作13ケース |
| `gboard-editing-observations.json` | 変換中・文節変換中・未確定文字の途中の3ケース |

記録直後は `observed-unreviewed`。入力条件、Enter 前の状態、操作内容、結果を確認したものだけ `reviewed` にする。未観測・準備失敗・別の初期状態を期待値として採用しない。Gboard 更新時も期待値を自動上書きしない。

`--mode inventory` は IME を切り替えず全ケース一覧を `enter-cases.json` に出力する。次の比較では未レビュー・未実施・基準未取得を成功扱いしない。各ディレクトリは1ケースにつき1記録にする。

```sh
python3 tools/enter/compare.py reviewed-gboard-directory actual-directory \
  --inventory enter-cases.json
```

比較は入力先での結果が主基準。異なるが同等な API 呼び出し列は許容し、ログは原因調査に残す。入力画面は競合する EditorInfo 条件を再現するため TextView の自動補正後に指定属性を渡す合成エディタであり、全アプリ・全 Gboard バージョンへの保証ではない。

明示的な直接入力モード／直接入力ショートカットは独自仕様として従来のキーイベント送出を維持し、標準の Gboard 比較から分ける。

## 今回の検証結果

2026-09-07、上記 Pixel 6 で保存済み Gboard 実測と照合。

- 通常表示：定義した240ケースが一致（テンキー238、ローマ字入力で途中カーソル・文節変換の2ケース）。
- 追加経路：フローティングのテンキー12ケース、フローティングの文節変換1ケース、通常 QWERTY の8ケースが一致。
- JVM テスト184件、Python 比較器テスト7件が成功。debug 本体・instrumentation APK のビルド成功。
- 比較結果は `240 matched; 0 failed/unverified`。表示／キーボードを分けた実機確認は計261ケース。

実測で確認した不一致に対し、元の EditorInfo によるアクション優先順位、数値・TYPE_NULL の標準 Enter、未確定文字がないときの Alt+Enter、ソフト Enter の文節単位確定を修正した。文節確定は既存の学習・再変換処理を利用し、後続文節の変換状態を保持する。独自の物理 COMMIT ショートカットは従来の全体確定を維持しており、物理 Enter の文節変換中の互換性は今回の個別ケースに含まれない。

全ケースを全表示・全キーボードで掛け合わせた検証ではない。初期候補・未確定状態が同じことを確認できた記録だけを比較し、準備失敗や別の読みで行った診断記録は検証件数に含めていない。

PR 作成前の追加レビューでは、通常の instrumentation 実行時のスキップ、比較対象 IME の自動選択と終了後の復元を確認した。文節確定後は選択中の区切りパターンも維持する。学習セッションの既存テスト3件を加えた JVM テスト187件と Python テスト7件が成功。
