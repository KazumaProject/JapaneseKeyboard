# フリック長押し KeyType 設計

## 要望の意味

要望者が言っていることは、長押しを「キーを押した直後」だけでなく「フリック方向を選んだ後」にも判定したい、という意味です。

たとえば `u` を上フリックして `un` を選び、その状態で指を離さず一定時間待ったら `yun` に切り替えたい、という操作です。

別の例では、`c` のあとに `a` を左上フリックした時点で、短く離せば `かく`、そのまま保持すれば `きゃく` を出したい、ということです。つまり、同じ方向操作に対して「短く離す」と「保持して離す」で別の出力を持たせたい、という要望です。

## 想定する出力数

はい。新しい KeyType では、次の合計18出力を設定・出力できる想定にします。

- 通常出力: 9方向
- 長押し出力: 9方向

9方向は `TfbiFlickDirection` の全方向です。

```text
TAP
UP
DOWN
LEFT
RIGHT
UP_LEFT
UP_RIGHT
DOWN_LEFT
DOWN_RIGHT
```

出力値は「1文字」に限定せず、既存の `FlickAction.Input.char` と同じく `String` として扱います。したがって `か` のような1文字だけでなく、`かく`、`きゃく`、`un`、`yun` のような複数文字も出せます。

## 新 KeyType の名前

既存の `TWO_STEP_FLICK`、`STICKY_TWO_STEP_FLICK`、`HIERARCHICAL_FLICK` は変更しません。

新しい KeyType を追加します。

```kotlin
FLICK_LONG_PRESS
```

画面表示名は次にします。

```text
フリック長押し
```

意味は「フリック方向ごとに、通常出力と長押し出力を設定できる入力方式」です。

ユーザーには方向数よりも「フリックの長押しを設定できる」ことが重要なので、コード名も画面表示名も `FLICK_LONG_PRESS` / `フリック長押し` に寄せます。

## データ構造

既存の `FlickDirection` は7方向相当なので、この KeyType では使いません。9方向を表せる `TfbiFlickDirection` を使います。

実装では `KeyboardLayout` に新しい保存フィールドは追加しません。

既存の `twoStepFlickKeyMaps` と `twoStepLongPressKeyMaps` を、`first == second` の self-pair として再利用します。

```kotlin
// 通常出力: 方向 UP の出力を UP -> UP として保存
twoStepFlickKeyMaps[keyId][TfbiFlickDirection.UP][TfbiFlickDirection.UP] = "un"

// 長押し出力: 同じ方向の長押し出力も self-pair として保存
twoStepLongPressKeyMaps[keyId][TfbiFlickDirection.UP][TfbiFlickDirection.UP] = "yun"
```

新しい KeyType の分岐でだけ self-pair として解釈するため、既存の `TWO_STEP_FLICK` の意味は変えません。新しい Room table や migration を増やさずに済むのも、この方式の利点です。

## 未設定方向の扱い

未設定方向は「無効方向」として扱います。

ここでいう未設定方向とは、通常出力と長押し出力の両方が空の方向です。

```text
normal[direction].isNullOrEmpty()
longPress[direction].isNullOrEmpty()
```

無効方向の予定挙動:

- ポップアップやガイドには表示しない。
- 方向判定の候補に入れない。
- その方向へフリックしても、その方向の文字は出さない。
- 指を離した時点で有効な方向が選択されていなければ、入力はキャンセルする。

ただし、タップだけは別扱いです。

- 指の移動量がフリック閾値未満なら `TAP` と判定する。
- `TAP` の通常出力があれば短押しで出力する。
- `TAP` の長押し出力があれば、移動せず保持した時に出力する。
- `TAP` も未設定なら何も出力しない。

つまり、「未設定方向へ大きくフリックしたのに TAP が出る」という挙動にはしません。誤入力を避けるため、未設定方向はキャンセル扱いにします。

## 片方だけ設定された方向

通常出力だけ設定されている方向:

- 短く離すと通常出力。
- 保持しても長押し出力はないため、通常出力のまま。

長押し出力だけ設定されている方向:

- その方向は有効方向として扱う。
- 短く離すと何も出力しない。
- 長押し時間を超えてから離すと長押し出力。

通常出力と長押し出力が両方ある方向:

- 短く離すと通常出力。
- 長押し時間を超えてから離すと長押し出力。

## 専用コントローラ

新規ファイル:

```text
custom_keyboard/src/main/java/com/kazumaproject/custom_keyboard/controller/FlickLongPressInputController.kt
```

主な責務:

1. `ACTION_DOWN` で `TAP` 候補を開始する。
2. 指の移動に応じて9方向から有効方向だけを対象に現在候補を更新する。
3. 現在候補に長押し出力がある場合、候補選択時点から長押しタイマーを開始する。
4. 候補が変わったら古いタイマーをキャンセルし、新候補で張り直す。
5. 長押しタイマー発火時点でまだ同じ候補なら、長押し出力を確定候補にする。
6. `ACTION_UP` で通常出力または長押し出力を通知する。

内部状態案:

```kotlin
private var currentDirection: TfbiFlickDirection? = null
private var longPressDirection: TfbiFlickDirection? = null
private var isLongPressActive = false
private var hasExceededFlickThreshold = false
private val longPressRunnable = Runnable { ... }
```

方向の有効判定:

```kotlin
private fun isEnabled(direction: TfbiFlickDirection): Boolean {
    return normalMap[direction].orEmpty().isNotEmpty() ||
        longPressMap[direction].orEmpty().isNotEmpty()
}
```

確定出力:

```kotlin
private fun outputFor(direction: TfbiFlickDirection): String {
    val longPress = longPressMap[direction].orEmpty()
    if (isLongPressActive && longPress.isNotEmpty()) return longPress
    return normalMap[direction].orEmpty()
}
```

## FlickKeyboardView の分岐

`FlickKeyboardView.attachKeyBehavior` に `KeyType.FLICK_LONG_PRESS` の分岐を追加します。

取得するマップ:

```kotlin
val normalMap = extractFlickLongPressMap(
    layout.twoStepFlickKeyMaps[keyData.keyId]
        ?: layout.twoStepFlickKeyMaps[keyData.label]
)

val longPressMap = extractFlickLongPressMap(
    layout.twoStepLongPressKeyMaps[keyData.keyId]
        ?: layout.twoStepLongPressKeyMaps[keyData.label]
)
```

`normalMap` と `longPressMap` の両方が空なら、キーは何も出力しないキーとして扱います。クラッシュや既存 fallback はさせません。

## キー編集 UI

キー編集画面には、既存の入力スタイルとは別に次のチップを追加します。

```text
フリック長押し
```

既存の `outputModeChipGroup` はそのまま使います。つまり、ユーザーは「通常出力」と「長押し出力」を切り替えながら同じ3x3グリッドを編集します。

```text
入力方式:  フリック / ドーナツ / 2段フリック / フリック長押し
出力設定:  通常 / 長押し
```

`フリック長押し` を選んだ時のグリッドは、タップを含む9方向すべてを表示します。

```text
┌────────┬────┬────────┐
│ 左上   │ 上 │ 右上   │
├────────┼────┼────────┤
│ 左     │ Tap│ 右     │
├────────┼────┼────────┤
│ 左下   │ 下 │ 右下   │
└────────┴────┴────────┘
```

編集の流れ:

1. 入力方式で `フリック長押し` を選ぶ。
2. `通常` を選んで、3x3グリッドの各方向に通常出力を設定する。
3. `長押し` を選んで、同じ3x3グリッドの各方向に長押し出力を設定する。
4. セルをタップすると下の入力欄に現在値を表示し、文字列を編集する。

入力欄のラベル:

- 通常モード: `通常出力`
- 長押しモード: `長押し出力`

グリッド内の表示:

- 通常モードでは通常出力の値を表示する。
- 長押しモードでは長押し出力の値を表示する。
- 未設定セルは空欄または薄いプレースホルダー表示にする。

この方式なら、画面上に18個の入力欄を一度に並べずに済みます。既存の「通常 / 長押し」切り替えに合わせて、同じ9方向グリッドを2枚編集する感覚になります。

### FlickGridEditorView の拡張

既存の `FlickGridEditorView` にはすでに `TfbiFlickDirection` の3x3配置があります。新しい `GridMode` を追加して使います。

```kotlin
enum class GridMode {
    PETAL,
    TWO_STEP,
    SPECIAL_FLICK,
    FLICK_LONG_PRESS
}
```

新しいセルモード:

```kotlin
sealed class CellMode {
    data class FlickLongPress(val direction: TfbiFlickDirection) : CellMode()
}
```

新しい表示データ:

```kotlin
data class FlickLongPressMappingItem(
    val direction: TfbiFlickDirection,
    val output: String
)
```

`KeyEditorFragment` では、通常用と長押し用の2リストを持ちます。

```kotlin
private var currentFlickLongPressNormalItems =
    mutableListOf<FlickLongPressMappingItem>()

private var currentFlickLongPressHoldItems =
    mutableListOf<FlickLongPressMappingItem>()
```

`outputModeChipGroup` が `通常` なら normal list、`長押し` なら hold list を `FlickGridEditorView` に渡します。

## import/export と DB

新しい KeyType は保存・復元対象に追加します。出力マップは既存の `twoStepFlickKeyMaps` / `twoStepLongPressKeyMaps` に self-pair として保存するため、DB schema 変更と Room migration は不要です。

確認対象:

- `KeyboardLayoutJsonExporter`
- `KeyboardBackupPipeline`
- `ImportableKeyboardLayout`
- keyId 付け替え処理
- キー削除時の関連マップ削除

既存の `flickKeyMaps`、`hierarchicalFlickMaps` は変更しません。`twoStepFlickKeyMaps` は保存領域として再利用しますが、`KeyType.FLICK_LONG_PRESS` のときだけ self-pair として平坦化します。

## テスト観点

新規コントローラの単体テストで確認します。

- `TAP` を短く離すと通常 `TAP` 出力になる。
- `TAP` を長押しして離すと `TAP` 長押し出力になる。
- `UP` へフリックしてすぐ離すと通常 `UP` 出力になる。
- `UP` へフリックして保持すると `UP` 長押し出力になる。
- 長押し出力が未設定の方向は、保持しても通常出力になる。
- 通常出力が未設定で長押し出力だけある方向は、短く離すと無出力、保持すると長押し出力になる。
- 通常・長押しの両方が未設定の方向へフリックした場合、入力はキャンセルされる。
- 有効方向から未設定方向へ移動した場合、長押しタイマーはキャンセルされる。
- `ACTION_CANCEL` でタイマーがキャンセルされる。
- 既存 KeyType の挙動が変わらない。

## 結論

想定としては「9方向の通常出力 + 9方向の長押し出力 = 最大18個の文字列」を設定・出力できる KeyType で間違いありません。

設定されていないフリック方向は無効方向として扱い、ポップアップにも方向判定にも含めません。未設定方向へフリックして離した場合は、TAP にフォールバックせずキャンセル扱いにするのが安全です。
