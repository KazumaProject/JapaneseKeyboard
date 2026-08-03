# フリック選択文字の入力欄プレビュー設計

## 1. 結論

TenKey と Sumire の文字フリックに、指を離す前から選択中の文字を入力欄へ仮表示する機能を追加する。

- 設定は TenKey 用、Sumire 用に分けず、フリック系入力共通の 1 個の Switch とする。
- 新設定画面と従来設定画面には、同じ SharedPreferences キーを持つ項目をそれぞれ配置する。
- デフォルトは OFF とし、OFF の場合は既存の入力イベントと IME 処理を一切変更しない。
- QWERTY は対象外とする。Sumire の英語モードが QWERTY 表示へ切り替わっている場合も対象外である。
- 押下中の文字は `commitText()` せず、一時的な `setComposingText()` として表示する。
- `_inputString`、候補検索、学習、IME 内部の編集履歴は指を離すまで更新しない。

入力欄へ一度 `commitText()` してから削除・置換する方式は採用しない。この方式は入力先アプリの Undo、TextWatcher、選択範囲、カーソル位置を壊す可能性があるためである。

`setComposingText()` による仮表示自体は入力先アプリから観測できる。検索欄の即時検索や独自 TextWatcher が MOVE 中の文字を受け取ることは、この機能の性質上避けられない。また、エディタによっては composing 更新も独自の Undo 単位として扱う可能性がある。このため本機能は opt-in、デフォルト OFF とし、代表的な EditText、Compose TextField、WebView で互換性を確認する。

## 2. 対象範囲

### 2.1 対象

初回実装では次を対象とする。

- TenKey
  - 通常表示
  - フローティング表示
  - 日本語・英語・数字モードのうち、最終的に composing 入力を行う通常文字キー
- Sumire
  - 通常表示
  - フローティング表示
  - 日本語・英語・数字モードのうち、最終的に composing 入力を行う通常文字キー
  - Sumire の標準入力スタイルすべて

| Sumire の設定値 | `KeyType` | コントローラー |
|---|---|---|
| `default` | `PETAL_FLICK` | `CrossFlickInputController` の TEXT モード |
| `circle` | `STANDARD_FLICK` | `StandardFlickInputController` |
| `second-flick` | `TWO_STEP_FLICK` | `TfbiInputController` |
| `third-flick` | `HIERARCHICAL_FLICK` | `TfbiHierarchicalFlickController` |
| `sumire` | `CIRCULAR_FLICK` | `CustomAngleFlickController` |

通常文字キーとは、最終出力が `KeyAction.Text` または `FlickAction.Input` として表現されるキーを指す。出力は 1 文字に限定せず、Sumire の複数文字出力も対象にする。

### 2.2 初回実装の対象外

- QWERTY、QWERTY ローマ字、QWERTY 数字
- Tablet 五十音キーボード
- ユーザー作成カスタムキーボードの実行モード
  - `FlickKeyboardView` の共通イベント基盤は再利用できるように作るが、IME 側の適用判定では `TenKeyQWERTYMode.Custom` を除外する。
- Sumire の特殊キー
  - 削除、空白、変換、Enter、カーソル、濁点切替など
  - 特殊キーの方向に文字列オーバーライドが設定されていても初回実装では対象外とする。
- 変換中、文節選択中、選択モード、操作中のカーソル移動モード
- composing 内のカーソル編集で `stringInTail` が空でない場合も対象とする。DOWN 時点の tail を固定し、プレビュー表示へ連結する一方、入力 mutation からは除外する。
- パスワード、電話番号、日時など、composing 表示を安全に保証できない入力欄
- Direct Commit が選択されている入力動作
- ダブルタップバインディングがあるキー

対象外条件では設定を自動的に OFF へ書き戻さず、その 1 ジェスチャーだけ従来方式へフォールバックする。

## 3. ユーザー向け設定

### 3.1 Preference

共通キーは次とする。

```text
flick_editor_preview_preference
```

値は Boolean、デフォルトは `false` とする。既存ユーザーの移行処理は不要である。

推奨表示文言は次のとおり。

```text
タイトル:
入力欄でフリック文字をプレビュー

OFF の概要:
従来方式：指を離したときに文字を入力します

ON の概要:
TenKey・Sumireで、選択中のタップ／フリック文字を入力欄に仮表示します
```

英語リソースも同時に追加する。

```text
Title:
Preview flick characters in the text field

Off summary:
Enter the character when you release the key

On summary:
Preview the selected tap or flick text while holding a TenKey or Sumire key
```

### 3.2 配置

新設定画面では `pref_operation_feedback.xml` に `category_flick_input_title`（表示名「フリック入力」）を設け、次を同じカテゴリにまとめる。

- `flick_input_only_preference`
- `flick_editor_preview_preference`
- `flick_sensitivity_preference`
- `flick_threshold_shape_preference`

新しい Switch は `flick_input_only_preference` の直後へ配置する。現在の先頭 `category_function_title` にあるフリック以外の設定は、既存の `category_function_title` に残す。

従来設定画面でも `pref_common_legacy.xml` に同じ `category_flick_input_title` を設け、上記 4 項目を現在の `category_function_title` から移す。Preference キー、デフォルト値、保存先は変更せず、表示上の分類だけを揃える。

両方が同じキーを使うため、画面間の同期コードは追加しない。既存の `PreferenceManager.getDefaultSharedPreferences()` が唯一の保存先になる。

設定検索には XML から自動登録される。よく使う設定の候補にも表示できるよう、`frequentCandidatePreferenceKeys` に同じキーを追加する。

### 3.3 設定読み込み

次を追加する。

- `AppPreference.FLICK_EDITOR_PREVIEW_KEY`
- `AppPreference.flick_editor_preview_preference: Boolean`
- `ImePreferencesSnapshot.flickEditorPreviewPreference: Boolean`
- `IMEService.flickEditorPreviewPreference: Boolean`
- `runtimeInputPreferenceKeys` へのキー登録
- `syncRuntimeInputPreferences()` での再読み込み

設定値はジェスチャー開始時にスナップショットする。押下中に設定が変更されても、そのジェスチャーは DOWN 時の設定で完了し、次の DOWN から新しい値を使う。

## 4. 現行処理と変更方針

### 4.1 TenKey

現行の `TenKey.onTouch()` は次の流れになっている。

```text
ACTION_DOWN
  -> FlickListener.onFlick(Down, key, null)

ACTION_MOVE
  -> setTapInActionMove() / setFlickInActionMove()
  -> キーとポップアップの表示だけ変更

ACTION_UP
  -> FlickListener.onFlick(Tap/Flick..., key, char)
  -> IMEService.handleTapAndFlick()
  -> _inputString 更新
```

既存の `FlickListener.onFlick()` は確定通知の意味を維持し、MOVE からは呼ばない。別のプレビューイベントを追加する。

### 4.2 Sumire

`FlickKeyboardView` は複数のコントローラーを `OnKeyboardActionListener` へ集約しているが、MOVE 中に共通して得られるのは一部の方向変更だけである。方向だけでは、2 段・階層・長押し出力・円形マップ切替後の最終文字を判断できない。

各コントローラーが、方向ではなく次を通知するようにする。

> 今この状態で指を離した場合に、通常の確定処理へ渡される文字列と `isFlick`

特殊アクション、無効方向、まだ文字が決まっていない階層は `text = null` とする。

## 5. 共通プレビューイベント

TenKey と Sumire が共有できる型を `core` モジュールへ追加する。

```kotlin
data class FlickTextSelection(
    val text: String?,
    val isFlick: Boolean,
)

sealed interface FlickTextPreviewEvent {
    val gestureId: Long

    data class Started(
        override val gestureId: Long,
        val selection: FlickTextSelection,
    ) : FlickTextPreviewEvent

    data class Changed(
        override val gestureId: Long,
        val selection: FlickTextSelection,
    ) : FlickTextPreviewEvent

    data class CommitPending(
        override val gestureId: Long,
        val selection: FlickTextSelection,
    ) : FlickTextPreviewEvent

    data class Finished(
        override val gestureId: Long,
    ) : FlickTextPreviewEvent

    data class Canceled(
        override val gestureId: Long,
    ) : FlickTextPreviewEvent
}

fun interface FlickTextPreviewListener {
    fun onFlickTextPreview(event: FlickTextPreviewEvent)
}
```

`text = null` は「現時点では仮表示できる文字出力がない」ことを表す。空文字はイベント送出前に `null` へ正規化する。

### 5.1 イベント順序

通常確定は必ず次の順序にする。

```text
Started
Changed (0回以上)
CommitPending
既存の確定コールバック
Finished
```

キャンセルは次の順序にする。

```text
Started
Changed (0回以上)
Canceled
```

`CommitPending` と `Finished` の間で既存の確定コールバックがプレビュー済み入力計画を消費しなかった場合、`Finished` で元の composing 表示を復元する。これにより、特殊アクションへの変化、ダブルタップによる遅延、イベント不一致があっても仮文字が残らない。

### 5.2 Emitter

イベント順序、gesture ID、同一選択の重複抑止を共通化するため、Android View に依存しない `FlickTextPreviewEmitter` を `core` に追加する。

```kotlin
class FlickTextPreviewEmitter {
    var listener: FlickTextPreviewListener? = null

    fun begin(selection: FlickTextSelection)
    fun update(selection: FlickTextSelection)
    fun commit(selection: FlickTextSelection, dispatch: () -> Unit)
    fun cancel()
}
```

`commit()` は `try/finally` で `CommitPending -> dispatch -> Finished` を保証する。`update()` は `text` と `isFlick` が直前と同じなら通知しない。

## 6. キーボード側の実装

### 6.1 TenKey

`TenKey` に次を追加する。

```kotlin
fun setOnFlickTextPreviewListener(listener: FlickTextPreviewListener?)
```

通常文字キーについて、現在の `InputMode` と `KeyTapFlickInfo` から選択文字を解決する共通関数を作る。

```kotlin
private fun resolveTextSelection(
    key: Key,
    gestureType: GestureType,
): FlickTextSelection
```

呼び出し位置は次のとおり。

- `ACTION_DOWN`: TAP 出力で `emitter.begin()`
- `ACTION_MOVE`: `getGestureType()` 後、選択が変わったとき `emitter.update()`
- `ACTION_UP`: 最終選択を渡して `emitter.commit { 既存 FlickListener.onFlick() }`
- `ACTION_CANCEL`: `emitter.cancel()`
- `cancelActiveTouch()`、View 非表示、detach: `emitter.cancel()`
- 2 本目の指、カーソルモード、長押し特殊動作へ移行する場合: 確定処理の前に `emitter.cancel()`

通常表示とフローティング表示は同じ `TenKey` クラスを使うため、IME 側でそれぞれ同じプレビューリスナーを登録する。

### 6.2 Sumire 共通

`FlickKeyboardView` に次を追加する。

```kotlin
fun setOnFlickTextPreviewListener(listener: FlickTextPreviewListener?)
```

各コントローラーの emitter は `FlickKeyboardView` の現在の listener へイベントを転送する。`FlickKeyboardView` は通常文字キーかつダブルタップバインディングなしの場合だけ emitter を接続する。

View の `onVisibilityChanged()`、`onDetachedFromWindow()`、`cancelTrackedTouchState()` はすべて controller の `cancel()` を経由し、必ず `Canceled` を送る。

### 6.3 Sumire 各入力スタイル

#### PETAL_FLICK

`CrossFlickInputController` の TEXT モードで、`resolveText(currentDirection, preferLongPress)` の結果を通知する。

- DOWN: TAP の通常文字
- MOVE: 方向変更後の通常文字
- 長押し成立: 長押し文字が存在すればその文字へ Changed
- UP: 実際に commit する通常／長押し文字
- CANCEL: Canceled

ACTION モードの特殊キーはプレビュー対象にしない。

#### STANDARD_FLICK

`StandardFlickInputController.characterMap` から現在方向の文字列を通知する。

- DOWN は TAP
- MOVE は `calculateDirection()` の結果
- UP は finalDirection

現在の確定処理が TAP も `isFlick = true` として渡している場合、プレビューイベントも既存確定処理と同じ値を使う。ここで入力方式の意味を変更しない。

#### CIRCULAR_FLICK

`CustomAngleFlickController` の現在マップと方向から `FlickAction` を解決する。

- `FlickAction.Input` は `char` を通知
- `FlickAction.Action(KeyAction.Text)` は `text` を通知
- それ以外の Action、マップ切替方向、無効方向は `text = null`
- マップが切り替わった場合は、新しいマップで同じ方向を再解決して Changed を送る

#### TWO_STEP_FLICK

`TfbiInputController` の `firstFlickDirection` と `currentSecondFlickDirection` を provider に渡し、現時点の最終文字を通知する。

- DOWN は `TAP/TAP`
- 1 段目確定時にも Changed
- 2 段目のハイライト変更時にも Changed
- 中央へ戻って状態がリセットされた場合は `TAP/TAP` へ Changed
- 長押し出力が成立した場合は longPressProvider の文字へ Changed
- UP は、既存処理が最終的に選んだ first/second の組を使用

#### HIERARCHICAL_FLICK

`TfbiHierarchicalFlickController` は現在の `currentMap` と `currentHighlight` から「UP した場合の selectedNode」を解決する純粋関数を持つ。

```kotlin
private fun resolveCurrentOutput(): String?
```

- 終端 `Input`: その文字
- `SubMenu`: 現行 UP 処理と同様に `nextMap[TAP]` が Input ならその文字
- 無効方向、出力を持たない submenu: `null`
- 階層 push/pop、ハイライト変更、内部モード変更のたびに再解決して Changed
- UP でも同じ resolver を使用し、プレビューと確定の分岐を重複させない

## 7. IME 側の状態設計

### 7.1 FlickInputPreviewCoordinator

`IMEService` へロジックを直接追加し続けず、`ime_service/flick_preview/FlickInputPreviewCoordinator.kt` を追加する。

```kotlin
data class ActiveFlickPreview(
    val gestureId: Long,
    val editorSessionId: Long,
    val baseInput: String,
    val baseCanonicalRevision: Long,
    val settingEnabledAtDown: Boolean,
    val lastSelection: FlickTextSelection?,
    val lastMutation: FlickTextMutation?,
)
```

リスナー登録時に `FlickPreviewSource.TENKEY` または `FlickPreviewSource.SUMIRE` を含む `FlickPreviewContext` を組み立てる。イベント型自体はキーボード固有 enum を持たず、適用対象の判定は IME 境界で行う。

主な API は次とする。

```kotlin
fun onEvent(event: FlickTextPreviewEvent, context: FlickPreviewContext)
fun consumePendingCommit(text: String, isFlick: Boolean): FlickTextMutation?
fun cancel(reason: FlickPreviewCancelReason, restore: Boolean)
```

`editorSessionId` は `onStartInput()` ごとに増加させる。別の InputConnection から届いた古いイベントは無視する。

### 7.2 ComposingTextArbiter

プレビュー中には、旧候補計算や live conversion の非同期結果が `setComposingText()` を呼ぶ可能性がある。その書き込みでプレビューが上書きされないよう、canonical 表示と preview 表示を調停する。

```kotlin
sealed interface CanonicalComposingState {
    data class Text(val value: CharSequence, val cursorPosition: Int) : CanonicalComposingState
    data object Finished : CanonicalComposingState
}

class ComposingTextArbiter {
    fun setCanonical(text: CharSequence?, cursorPosition: Int): Boolean
    fun showPreview(text: CharSequence, cursorPosition: Int): Boolean
    fun suspendPreviewAndRestore(): Boolean
    fun releasePreview(leaveDisplayedText: Boolean)
    fun cancelPreviewAndRestore(): Boolean
    fun finishCanonical(): Boolean
}
```

規則は次のとおり。

- プレビューなし: canonical 書き込みをそのまま InputConnection へ転送する。
- プレビュー中: canonical 書き込みは最新値を保存するが、エディタには転送しない。
- preview 書き込み: InputConnection へ直接転送するが canonical 状態は変更しない。
- `text = null` への移動: preview 表示だけを停止して canonical を復元するが、ジェスチャーセッションは保持する。文字方向へ戻ったら同じ `baseInput` から preview を再開する。
- CANCEL: 最新 canonical 状態を復元する。DOWN 時点の古い状態へ固定的に戻さない。
- 正常 UP: preview を表示したまま所有権だけ解放し、直後の `_inputString` 更新による canonical 描画へ接続する。
- `commitText()`、`finishComposingText()`、削除、selection 変更など文字プレビュー以外の編集操作が入る場合: 先に preview をキャンセルしてから操作する。

`IMEService.setComposingText()` は canonical 経路として arbiter を通す。現在 `updateComposingText()` にある `currentInputConnection?.setComposingText()` の直接呼び出しも canonical 経路へ統一する。preview だけが arbiter の専用 bypass API から実 InputConnection を呼ぶ。

`CharSequence` は後から Span が変化しないよう `SpannableString` へコピーして保存する。

`showPreview()` が `false` を返した場合、そのジェスチャーでは preview を中止して従来方式へフォールバックする。InputConnection の戻り値が `true` でも独自実装が表示を無視する場合までは自動判定できないため、設定は best effort とする。

### 7.3 候補と非同期処理

MOVE 中は次を行わない。

- `_inputString` 更新
- `requestCandidateRefresh()`
- Zenz リクエスト
- 学習状態更新
- `finishComposingText()`
- `commitText()`
- `deleteSurroundingText()`

DOWN 前から動作していた候補処理は継続してよい。結果の canonical composing 書き込みだけ arbiter が保留する。CANCEL なら最新結果を復元でき、UP なら正式入力後の新しい candidate token によって古い結果が無効化される。

## 8. 入力結果の予測と正式反映

### 8.1 純粋な mutation resolver

プレビュー文字を単純に `baseInput + selection.text` としてはならない。TenKey と Sumire の 1 文字タップにはトグル入力があるため、例えば既に「あ」がある状態で「あ」キーをタップすると、結果は「ああ」ではなく「い」になる場合がある。

`sendCharTap()`、`sendCharFlick()`、`handleOnKeyForSumire()` に散らばる通常 composing 入力の判断を、純粋な resolver と適用処理へ分ける。

```kotlin
sealed interface FlickTextMutation {
    data class ReplaceComposingInput(
        val resultInput: String,
        val effects: FlickInputEffects,
    ) : FlickTextMutation

    data class Unsupported(val reason: FlickPreviewUnsupportedReason) : FlickTextMutation
}
```

resolver の入力には次を含める。

- DOWN 時点の `baseInput`
- 選択文字列
- `isFlick`
- `isFlickOnlyMode`
- `isContinuousTapInputEnabled`
- `lastFlickConvertedNextHiragana`
- 現在の InputType と `ResolvedInputBehavior`
- TenKey / Sumire のモード
- 変換、選択、cursor、tail の各状態

1 文字出力は既存の `getNextInputChar()` と同じ規則を使い、複数文字出力は既存 Sumire 処理と同様に末尾へ追加する。

### 8.2 プレビューと確定で同じ計画を使う

`Started` / `Changed` では resolver の結果だけを preview composing として表示する。

`CommitPending` では最後の mutation を pending として保持する。直後の既存文字確定コールバックが `text` と `isFlick` の一致する pending mutation を取得し、`_inputString` と既存フラグを一度だけ更新する。

これにより次を保証する。

- プレビューが「い」なのに UP 後「ああ」にならない。
- MOVE 回数だけ文字が増えない。
- プレビュー中にトグル入力フラグが変わらない。
- 候補検索は最終文字に対して 1 回だけ開始される。

pending mutation が一致しない場合は preview を復元して、既存の確定処理へフォールバックする。

## 9. 詳細な状態遷移

### 9.1 通常例

入力前が「か」、押したキーが「あ」の場合:

```text
Idle
  -> DOWN/TAP
     editor preview = "かあ"
     _inputString   = "か"
  -> MOVE/UP direction
     editor preview = "かう"
     _inputString   = "か"
  -> MOVE/back to TAP
     editor preview = "かあ"
     _inputString   = "か"
  -> UP
     pending mutation = "かあ"
     existing final callback consumes mutation
     _inputString = "かあ"
     canonical composing/candidate refresh runs
  -> Idle
```

### 9.2 無効方向・特殊方向

```text
text selection
  -> MOVE to text = null
     canonical composing を復元して preview を一時停止
  -> MOVE back to text selection
     同じ baseInput から preview を再表示
```

`text = null` へ移動した時点で gesture 自体は終了させない。後から文字方向へ戻れるためである。

### 9.3 CANCEL

次では canonical composing を復元し、IME 内部入力を変更しない。

- `ACTION_CANCEL`
- キーボード View 非表示
- View detach / rebuild
- フローティングキーボード dismiss
- 入力モード・キーボード種類変更
- `onFinishInputView()`
- 新しい `onStartInput()`
- editor session ID 不一致
- 2 本目の指による既存特殊操作
- プレビュー中の commit/delete/selection 操作

InputConnection が終了済みの場合は `restore = false` で状態だけ破棄する。

## 10. 適用判定

`FlickPreviewEligibilityPolicy` を pure class として追加し、次をすべて満たす場合だけ有効にする。

```text
setting enabled at DOWN
AND source is TenKey or Sumire
AND QWERTY surface is not active
AND ordinary text-producing key
AND no double-tap binding
AND input behavior is composing
AND not password/numeric/phone/date/time direct field
AND !isHenkan
AND !selectMode
AND !cursorMoveMode
AND stringInTail is captured as an immutable preview tail
AND currentInputConnection exists
AND editor session matches
```

`stringInTail` がある場合、背景 Span はカーソル前の入力プレビューまで、下線 Span は tail を含む composing 全体まで適用する。CANCEL では元の composing 全体を復元し、UP の mutation は tail を含めず、選択文字だけをカーソル前へ確定する。

Sumire については `TenKeyQWERTYMode.Sumire` と、Sumire の数字レイアウトとして表示している `TenKeyQWERTYMode.Number` の composing 文字キーを許可する。`TenKeyQWERTYMode.Custom` は初回実装では拒否する。

## 11. 性能要件

- MotionEvent ごとに `setComposingText()` しない。
- 選択文字列または `isFlick` が変化した場合だけ更新する。
- MOVE 処理で coroutine を起動しない。
- MOVE 処理で候補検索・辞書検索・DB 書き込みを行わない。
- `SpannableString` は選択変化時に 1 個だけ生成する。
- 1 ジェスチャー中に保持する preview state は 1 件だけとする。

通常の 4 方向 TenKey では、指が同じ方向にいる限り MOVE が何回来ても editor 呼び出しは増えない。

## 12. 変更予定ファイル

### core

- `core/src/main/java/com/kazumaproject/core/domain/flick/FlickTextPreviewEvent.kt`
- `core/src/main/java/com/kazumaproject/core/domain/flick/FlickTextPreviewEmitter.kt`
- emitter の unit test

### tenkey

- `tenkey/src/main/java/com/kazumaproject/tenkey/TenKey.kt`
- TenKey selection resolver の unit test
- DOWN/MOVE/UP/CANCEL の instrumented test

### custom_keyboard

- `FlickKeyboardView.kt`
- `CrossFlickInputController.kt`
- `StandardFlickInputController.kt`
- `CustomAngleFlickController.kt`
- `TfbiInputController.kt`
- `TfbiHierarchicalFlickController.kt`
- 各 controller の selection/commit/cancel test

### app

- `AppPreference.kt`
- `ImePreferencesSnapshot.kt`
- `IMEService.kt`
- `ime_service/flick_preview/FlickInputPreviewCoordinator.kt`
- `ime_service/flick_preview/ComposingTextArbiter.kt`
- `ime_service/flick_preview/FlickTextMutationResolver.kt`
- `ime_service/flick_preview/FlickPreviewEligibilityPolicy.kt`
- `pref_operation_feedback.xml`
- `pref_common_legacy.xml`
- `values/strings.xml`
- `values-ja/strings.xml`
- coordinator、arbiter、resolver、eligibility の unit test
- TenKey/Sumire の instrumented test

## 13. テスト計画

### 13.1 Unit test

#### Emitter

- Started は 1 回だけ
- 同じ selection の Changed は抑止
- CommitPending -> callback -> Finished の順序
- callback が例外でも Finished
- CANCEL 後の Changed/Finished は無視

#### Coordinator

- stale gesture ID を無視
- TenKey と Sumire の source context を識別
- setting OFF、QWERTY、Custom のイベントを editor へ転送しない
- pending mutation が既存確定コールバックで消費されなかった場合に復元

#### Mutation resolver

- 空入力 + 「あ」TAP
- 「あ」+ 同じキー TAP が「い」へ置換
- Flick は末尾追加
- フリックのみ設定 ON の TAP は末尾追加
- Sumire 複数文字出力
- unsupported InputType
- henkan/select/操作中の cursor 状態を拒否し、安定した tail 状態を許可
- tail 付き DOWN/MOVE/CANCEL/UP の表示、Span 範囲、mutation 分離
- resolver 結果と既存確定結果の一致

#### Arbiter

- preview 中の canonical write は editor へ出さず最新値だけ保存
- CANCEL は最新 canonical を復元
- 正常 release は preview 表示を消さない
- finish/commit/delete 前に preview を復元
- editor session 変更後の古い CANCEL を無視

### 13.2 Controller test

各 Sumire スタイルについて次を確認する。

- DOWN の TAP selection
- 方向変更の Changed
- 同方向 MOVE の重複抑止
- 中央へ戻る
- 無効方向の `text = null`
- UP の final selection と既存確定出力が同じ
- ACTION_CANCEL / cancel() の Canceled
- 2 段、階層、map switch、長押し出力

### 13.3 Instrumented test

Fake editor またはテスト Activity の EditText で InputConnection 呼び出しを記録する。

- OFF: DOWN/MOVE では editor 書き込みなし、UP は従来どおり
- ON: DOWN/MOVE は `setComposingText()` のみ
- ON: MOVE 中に `commitText()`、delete、candidate request がない
- UP: `_inputString` 更新と候補 request は 1 回だけ
- CANCEL: 入力前の composing と `_inputString` に戻る
- rapid input: 前の canonical 更新が次の preview を上書きしない
- normal/floating の両方
- TenKey と Sumire 5 スタイル
- live conversion ON/OFF
- flick-only ON/OFF
- 候補欄 1/2/3 列、候補タブ表示 ON/OFF
- 縦/横
- password、number、direct mode の fallback
- View hide、keyboard switch、IME restart

Undo 対応エディタでは、1 ジェスチャーが中間文字ごとの複数 Undo 履歴にならないことも手動確認する。

## 14. 受け入れ条件

1. 設定 OFF では既存の文字列、候補、振動、音、ポップアップ、長押し、フリック判定が変わらない。
2. 設定 ON では、対象キーの DOWN 直後に TAP 文字が入力欄へ表示される。
3. 方向を変えると、前の仮文字が増殖せず現在選択文字へ置き換わる。
4. 中央へ戻す、2 段目を変える、階層を戻る操作でも最終出力と表示が一致する。
5. UP 後の `_inputString` と preview 最終表示が一致する。
6. MOVE 中には候補生成、学習、commit、delete が走らない。
7. CANCEL 後に仮文字が残らない。
8. TenKey の通常・フローティングと、Sumire の通常・フローティングおよび 5 スタイルで同じ原則が成立する。
9. QWERTY と対象外入力欄は従来動作を維持する。
10. 新設定画面と従来設定画面の Switch が同じ値を表示する。

## 15. 実装順序

1. Preference、snapshot、runtime 同期を追加する。ただしまだ挙動には接続しない。
2. core の event/emitter と unit test を追加する。
3. TenKey へ event を追加し、OFF 時回帰 test を通す。
4. Sumire 5 controller へ selection event を追加し、controller test を通す。
5. mutation resolver と eligibility policy を追加し、既存確定処理を共通 mutation へ寄せる。
6. composing arbiter を導入し、すべての canonical `setComposingText()` を経由させる。
7. preview coordinator を TenKey/Sumire の通常・フローティングへ接続する。
8. CANCEL/lifecycle/direct-operation の統合 test を追加する。
9. rapid input、live conversion、候補欄レイアウトを含む instrumented matrix を実行する。

この順序により、キーボードイベント、入力結果計算、Editor 境界を分けて検証でき、設定 OFF の既存動作を各段階で確認できる。
