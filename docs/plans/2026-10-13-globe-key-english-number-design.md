# 地球儀ボタンが英字・数字モードで効かない問題: 原因調査と修正方針

- 対象チケット: TAK-225（コード変更なし。本ドキュメントのみ）
- 対象ファイル: `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt`（以下 `IME`）、`tenkey/.../TenKey.kt`、`gojuon_keyboard/.../GojuonKeyboardView.kt`
- 調査方法: 静的読解のみ（実機・エミュレータでの再現は未実施）。行番号は `origin/dev` @ e091c4b1 時点。

## 現状の経路表（面×モード×タップ/長押し→呼ばれる関数）

地球儀キーの実体は `Key.KeyDakutenSmall`（tenkey の `keySmallLetter`、gojuon の `key10`）。
`tenkeyShowIMEButtonPreference == true` のときだけ地球儀アイコンになり、切替が有効になる。

| 面 | モード | タップ | 長押し |
|---|---|---|---|
| tenkey / sumire | 日本語 | `handleDakutenSmallLetterKey`(IME:28894) → `dakutenSmallLetter`(28658)。入力文字列が空 かつ `!onKeyboardSwitchLongPressUp` かつ `qwertyMode != Custom` かつ pref==true で `switchNextKeyboard()`(28779) | `handleLongPress` の `Key.KeyDakutenSmall`(11425) → `showListPopup()`(12489) |
| tenkey / sumire | 英字 | `smallBigLetterConversionEnglish`(28854)。**入力文字列が空のときだけ** `switchNextKeyboard()`。非空なら大小変換のみ | 同上（pref==true なら `showListPopup()`） |
| tenkey / sumire | 数字 | `handleDakutenSmallLetterKey` の `ModeNumber` 分岐(IME:28926-28942)。`sendCharTap` / `sendCharFlick` で文字送信。**`switchNextKeyboard()` を呼ばない** | 同上 |
| gojuon | 日本語 | `dakutenSmallLetter`（tenkey と同じ） | 同上 |
| gojuon | 英字 | `smallBigLetterConversionEnglish`（tenkey と同じ） | 同上 |
| gojuon | 数字 | `_tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }` のみ(IME:28913-28917)。**次のキーボードへの切替なし**（QWERTY 数字レイアウトへ遷移する） | 同上 |
| custom（カスタムレイアウト） | 全モード | `KeyAction.SwitchToNextIme`(IME:15147)。`!onKeyboardSwitchLongPressUp` なら `switchNextKeyboard()` | `showListPopup` 系（15902 で解除） |
| QWERTY | — | `QWERTYKey.QWERTYKeySwitchDefaultLayout`(IME:23350)。本チケットの対象外 | — |

アイコン表示の補足:
- tenkey の数字モードでは `TenKey.setNumberSmallKeyPresentation()`(TenKey.kt:2038) がこのキーに「括弧/数字用」の絵を出す。一方 IME の入力文字列監視(IME:25534-25553)は `ModeNumber` でも `setBackgroundSmallLetterKey(isLanguageEnable = pref, ...)` を呼ぶため、**状況により地球儀アイコンが出たまま、タップは文字送信になる**可能性がある（実機確認が必要）。
- 英字モードの `setKeysInEnglishText`(TenKey.kt:2827) は pref==true なら常に地球儀アイコンを出す。つまり「アイコンは常に地球儀、動作は入力文字列が空のときだけ切替」というズレがある。

## 原因（仮説a〜dの判定と根拠の行番号）

| 仮説 | 判定 | 根拠 |
|---|---|---|
| (a) 数字モードで `switchNextKeyboard()` が呼ばれない | **原因（確定）** | IME:28926-28942（tenkey/sumire は文字送信）、IME:28913-28917（gojuon は `_tenKeyQWERTYMode` 更新のみ）。数字モード分岐に `switchNextKeyboard()` 呼び出しが存在しない |
| (b) 英字モードで入力文字列が非空のとき大小変換のみ | **原因（確定）** | IME:28854-28872。`insertString.isNotEmpty()` 側は `getDakutenSmallChar()` による変換のみで切替しない。切替は `else`（空のとき）のみ |
| (c) `onKeyboardSwitchLongPressUp` が `true` のまま残る | **副次的要因の可能性（要実機確認）** | `showListPopup()`(IME:12489-12490) は先頭で `true` にし、入力文字列が非空だと `return` する（ポップアップも出ず、解除経路は次回キー押下時の `cancelOngoingLongPressForKey`(15857-15859) か `stopAllOngoingKeyLongPresses`(15830) のみ）。ポップアップ表示時の解除は `setOnDismissListener`(26725) と項目選択(26693)。解除漏れがあれば英字・数字で切替が抑止されうるが、通常のタップ経路では押下時にリセットされるため主因ではない |
| (d) `switchNextKeyboard()` が同じ面に戻る | **条件付きの要因** | IME:28779-28826。`keyboardOrder` が1件、または `resolveKeyboardForDisplay`(13177) が同じ面に解決すると切替が見えない。また `qwertyMode == Number` のときの後処理(28846-28858)が `_tenKeyQWERTYMode` を上書きする。ユーザー設定で `keyboardOrder` が複数ある通常ケースの主因ではない |

結論: 主因は (a) と (b)。地球儀アイコンが出ているのに、数字モードでは常に、英字モードでは入力中に切替が行われない。(c)(d) は再現条件が揃った場合の副次要因として、テストで固定する。

## 修正方針（どのファイルのどの関数をどう変えるか、変更しない挙動は何か）

方針: 「地球儀アイコンが表示されているキーのタップは、必ず次キーボードへ切り替える」を原則にし、判定を純粋関数に切り出す。

1. 新規 `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/state/GlobeKeyActionPolicy.kt` を追加する。
   - `enum class GlobeKeyAction { SwitchNextKeyboard, DakutenSmallConversion, SendCharacter, EnterQwertyNumber, None }`
   - `fun resolveGlobeKeyAction(surface: GlobeKeySurface, mode: InputMode, hasComposingText: Boolean, imeButtonEnabled: Boolean, longPressSuppressed: Boolean, isCustomQwerty: Boolean): GlobeKeyAction`
   - `enum class GlobeKeySurface { TENKEY, SUMIRE, GOJUON }`
2. `IMEService.handleDakutenSmallLetterKey`(28894) と `handleDakutenSmallLetterKeyFloating`(28957) を、上記ポリシーの戻り値で `when` 分岐する形に変更する。
   - 数字モード（tenkey/sumire/gojuon）: `imeButtonEnabled && !longPressSuppressed && !isCustomQwerty` なら `SwitchNextKeyboard`。それ以外（pref==false）は現状どおり。
   - 英字モード: **入力文字列の有無にかかわらず**、`imeButtonEnabled` のときは地球儀アイコンと動作を一致させる。入力中の大小変換が失われるため、ここは後続チケットで次のいずれかに決める（推奨は A）。
     - A: 入力中（`hasComposingText`）は大小変換を維持し、アイコンを地球儀ではなく大小変換アイコンに切り替える（アイコンと動作を一致させる）。切替はキー長押しのポップアップで行う。
     - B: 入力中でも切替を優先し、大小変換は別キーに移す（挙動変更が大きい）。
   - gojuon 数字モード: 現状の `TenKeyQWERTY` への遷移は「数字レイアウトへの切替」として意図的な可能性があるため、アイコンが地球儀のときのみ `SwitchNextKeyboard` に変える。
3. `IMEService.showListPopup`(12489): 入力文字列が非空で `return` する前に `onKeyboardSwitchLongPressUp = true` を立てないよう順序を入れ替える（(c) の解除漏れ対策）。
4. 数字モードのアイコン: IME:25534-25553 と `TenKey.setNumberSmallKeyPresentation` の優先順位を整理し、「地球儀を出すモードでは切替する」という一致を保つ。

変更しない挙動:
- 日本語モードの `dakutenSmallLetter`（濁点・小書き変換、空のとき切替）
- 長押しでの `showListPopup()`（キーボード選択ポップアップ）と `tenkeyShowIMEButtonPreference == false` のときの挙動
- カスタムレイアウトの `KeyAction.SwitchToNextIme`、QWERTY キーボード、フローティングキーボードの描画・サイズ処理
- `switchNextKeyboard()` / `resolveKeyboardForDisplay` 本体（(d) は設定依存のため今回は変更しない）

## ユニットテスト方針（既存の `app/src/test/java/com/kazumaproject/markdownhelperkeyboard/ime_service/` 配下に追加するテストクラス名と、IMEService全体を立ち上げずに検証するために切り出す純粋関数/ポリシーの名前と署名）

- テストクラス: `app/src/test/java/com/kazumaproject/markdownhelperkeyboard/ime_service/GlobeKeyActionPolicyTest.kt`（既存の `CandidateStripLayoutPolicyTest` などと同じ配置・スタイル）
- 対象: 上記 `resolveGlobeKeyAction(surface, mode, hasComposingText, imeButtonEnabled, longPressSuppressed, isCustomQwerty): GlobeKeyAction`（`ime_service/state/GlobeKeyActionPolicy.kt`、Android 依存なしの純粋関数）
- テストケース（先に書いて失敗を確認する。TDD）:
  - `number mode on tenkey switches keyboard when ime button enabled`（(a)）
  - `number mode on sumire switches keyboard when ime button enabled`
  - `number mode on gojuon switches keyboard when ime button enabled`
  - `number mode sends character when ime button disabled`
  - `english mode with composing text keeps case conversion` / `english mode with empty text switches keyboard`（(b)。方針A採用時の期待値）
  - `japanese mode behaves as before`（空のとき切替、非空なら濁点・小書き変換）
  - `long press suppression blocks switch`（(c)）
  - `custom qwerty mode never switches via this key`
- (c) 用に `showListPopup` の順序修正を直接テストするのは難しいため、フラグ設定を `fun shouldMarkKeyboardSwitchLongPress(hasComposingText: Boolean): Boolean` として切り出し、同ファイルでテストする。

## 受け入れ条件

- tenkey / sumire / gojuon の数字モードで、地球儀アイコンが表示されているときにタップすると次のキーボードへ切り替わる。
- 英字モードで、アイコンと動作が一致している（方針Aなら入力中は大小変換アイコン、入力なしは地球儀で切替）。
- 日本語モードの濁点・小書き変換、キー長押しのキーボード選択ポップアップ、`tenkeyShowIMEButtonPreference == false` の挙動が変わらない。
- 入力文字列が非空の状態で長押しした後も、次のキー押下で `onKeyboardSwitchLongPressUp` が残らず、地球儀タップが効く。
- `GlobeKeyActionPolicyTest` が追加され、`./gradlew :app:testDebugUnitTest --tests "*GlobeKeyActionPolicyTest"` が通る。
- QWERTY・カスタムレイアウト・フローティングキーボードの既存テストが壊れない。
