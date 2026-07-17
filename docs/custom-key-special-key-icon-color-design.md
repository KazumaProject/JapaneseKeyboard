# カスタムキーボード特殊キーアイコン/色設計

## 目的

カスタムキーボードの特殊キーについて、次を既存機能を壊さずに実現する。

- 濁点/小文字系アクションの既定アイコンを、現在より読みやすいサイズで表示する。
- 特殊キーごとに、キーの見た目色を「特殊キー色」または「通常キー色」から選べるようにする。

ここでいう濁点/小文字系アクションは次を対象にする。

- `KeyAction.ToggleDakuten`
- `KeyAction.ToggleDakutenOnly`
- `KeyAction.ToggleHandakutenOnly`
- `KeyAction.ToggleCase`

## 現状

`custom_keyboard` 側では `KeyData.isSpecialKey` が、入力処理上の特殊キー判定と見た目上の特殊キー色判定を兼ねている。

代表例:

- `KeyIconResolver.hasIcon()` は `isSpecialKey` かつ `drawableResId`/`icon` がある場合に `AppCompatImageButton` として描画する。
- `FlickKeyboardView.createKeyView()` は `keyData.isSpecialKey` を見て、side 系背景、`customSpecialKeyColor`、`customSpecialKeyTextColor` を使う。
- `FlickKeyboardView.getSpecialIconTargetSizePx()` は全特殊キーアイコンへ共通サイズを使い、入力モード切替系だけ `INPUT_MODE_SWITCH_ICON_SIZE_MULTIPLIER` で大きくしている。
- `KeyActionMapper` と `KeyboardRepository.drawableResIdForAction()` は濁点/小文字系アクションに `kana_small` / `kana_small_custom` / `english_small` を割り当てている。

このため、濁点/小文字系アクションは他の特殊キーと同じアイコンサイズになり、視認上小さく見える。また、特殊キーの動作を保ったまま通常キー色で表示する選択肢がない。

## アイコンが小さい根本原因

濁点/小文字系アイコンの小ささは、`FlickKeyboardView` のスケール計算だけが原因ではない。

`FlickKeyboardView.updateImageButtonMatrix()` は drawable の `intrinsicWidth` / `intrinsicHeight` を使って、drawable 全体が目標サイズに収まるようにスケールしている。これは一般的な `ImageView` として自然な処理であり、通常の 24dp アイコンでは問題になりにくい。

問題は、対象 drawable の有効描画範囲が viewport に対して極端に小さいこと。

- `kana_small.xml`: `width/height = 100dp`, `viewport = 100x100`。実際の path はおおむね x=37〜64、y=36〜62 に集中しており、描画範囲は viewport の約 27% 四方しかない。
- `kana_small_custom.xml`: `width/height = 42dp`, `viewport = 100x100`。path の座標範囲は `kana_small.xml` と同系統なので、intrinsic size を変えても余白比率は残る。
- `english_small.xml`: `width/height = 100dp`, `viewport = 100x100`。path はおおむね x=34〜66、y=42〜58 で、特に縦方向の描画範囲が約 15% しかない。
- 比較対象の `backspace_24px.xml` は viewport 960 に対して x=80〜880、y=160〜800 程度を使っており、表示面積が大きい。

つまり、現在の濁点/小文字系 drawable は「大きな透明余白を含むアイコン」として定義されている。ビュー側で個別倍率を足すと見た目は改善するが、壊れた asset framing を描画側で補正する形になり、同じ drawable を使う別表示や将来の icon sizing でも同じ問題が残る。

## 設計方針

1. `isSpecialKey` は動作判定として維持する。
2. キーの色だけを切り替える描画専用プロパティを追加する。
3. 既存データはすべて従来通り「特殊キー色」で復元する。
4. 濁点/小文字系アイコンは、drawable の有効描画範囲を正規化した action default icon を用意して根本から改善する。
5. `FlickKeyboardView` には濁点/小文字専用倍率を追加しない。ビューは「同じ optical size に整った drawable を同じ規則で描画する」責務に留める。

## データモデル

`custom_keyboard/src/main/java/com/kazumaproject/custom_keyboard/data/KeyModels.kt` に特殊キー色スタイルを追加する。

```kotlin
enum class SpecialKeyColorStyle(val dbValue: String) {
    SPECIAL("SPECIAL"),
    NORMAL("NORMAL");

    companion object {
        fun fromDbValue(value: String?): SpecialKeyColorStyle =
            entries.firstOrNull { it.dbValue == value } ?: SPECIAL
    }
}
```

`KeyData` には末尾パラメータとして追加し、既存コンストラクタ呼び出しを壊さない。

```kotlin
data class KeyData(
    ...
    val keyType: KeyType = if (isFlickable) KeyType.CIRCULAR_FLICK else KeyType.NORMAL,
    val specialKeyColorStyle: SpecialKeyColorStyle = SpecialKeyColorStyle.SPECIAL
)
```

通常キーではこの値を無視する。特殊キーだけ、`SPECIAL` なら従来通り特殊キー色、`NORMAL` なら通常キー色として描画する。

## 保存形式

Room entity `KeyDefinition` に列を追加する。

```kotlin
val specialKeyColorStyle: String = SpecialKeyColorStyle.SPECIAL.dbValue
```

DB は現在 version 38 なので、実装時は 39 に上げ、次の migration を追加する。

```sql
ALTER TABLE key_definitions
ADD COLUMN specialKeyColorStyle TEXT NOT NULL DEFAULT 'SPECIAL'
```

対応箇所:

- `AppDatabase`: `version = 39`、`MIGRATION_38_39` 追加。
- `AppModule`: `MIGRATION_38_39` を import し `addMigrations()` に追加。
- `KeyboardRepository.toDbEntities()`: `KeyData.specialKeyColorStyle.dbValue` を保存。
- `KeyboardRepository.toUiLayout()`: `SpecialKeyColorStyle.fromDbValue(dbKey.specialKeyColorStyle)` で復元。
- `KeyboardLayoutExportDtos.KeyDefinitionDto`: nullable な `specialKeyColorStyle: String?` を追加。
- `KeyboardLayoutJsonExporter`: export に含める。
- `KeyboardBackupPipeline`: import 時に `null`/未知値は `SPECIAL` へ正規化する。

import/export の schemaVersion は必須で上げなくてもよい。新フィールドは optional で、欠損時は `SPECIAL` にする。上げる場合でも、v2 以前の JSON は `SPECIAL` として読み込む。

## 描画設計

`FlickKeyboardView` に、動作判定ではなく見た目色を解決する小さな helper を置く。

```kotlin
private data class KeyVisualPalette(
    val usesSpecialSurface: Boolean,
    val baseColor: Int,
    val textColor: Int,
    val highlightColor: Int
)

private fun resolveKeyVisualPalette(keyData: KeyData): KeyVisualPalette
```

解決ルール:

- `!keyData.isSpecialKey`: 通常キー色。
- `keyData.isSpecialKey && keyData.specialKeyColorStyle == SPECIAL`: 特殊キー色。
- `keyData.isSpecialKey && keyData.specialKeyColorStyle == NORMAL`: 通常キー色。

ただし、余白、行列サイズ、特殊キー用のアクション dispatch は `isSpecialKey` のまま判定する。色変更によってタッチ領域や入力挙動が変わらないようにする。

置き換え対象:

- `createKeyView()` の通常/side 背景選択。
- custom theme の `targetBaseColor` / `targetTextColor` / `targetHighlightColor`。
- `AppCompatImageButton` の背景色と tint。
- `getGuideTextColor()`。
- custom theme 時の popup color 設定。

default theme では次のように扱う。

- `SPECIAL`: 既存の `ten_keys_side_bg_material(_light)`。
- `NORMAL`: 既存の `ten_keys_center_bg_material(_light)`。

custom theme では次のように扱う。

- `SPECIAL`: `customSpecialKeyColor` / `customSpecialKeyTextColor`。
- `NORMAL`: `customKeyColor` / `customKeyTextColor`。

## アイコン改善設計

濁点/小文字系アクションの既定アイコンは、正規化済み vector drawable を新規追加して使う。

既存の `kana_small` / `kana_small_custom` / `english_small` を直接置き換える案もあるが、これらは tenkey レイアウトや設定画面、既存ユーザーの内蔵 drawable 選択でも参照されている。今回の要望はカスタムキーボードの action default icon なので、既存 resource を破壊的に変更するより、新しい正規化 resource を追加して action mapping を差し替える方が安全。

追加 resource 案:

```text
core/src/main/res/drawable/custom_key_kana_case_24.xml
core/src/main/res/drawable/custom_key_english_case_24.xml
```

正規化の基準:

- `android:width` / `android:height` は `24dp`。
- `viewportWidth` / `viewportHeight` は `24`。
- 実際の path bbox が横方向で 18〜20 viewport units 程度を使う。
- 上下左右の透明余白は optical balance 用の最小限だけにする。
- `fillColor` は `@color/keyboard_icon_color`。tint と `FlickKeyboardView.applyImageButtonTint()` の既存挙動を保つ。
- `ToggleDakuten` 系は、濁点/半濁点/小文字を表す glyph を現行と同等の意味で再構成する。
- `ToggleCase` は `a/A` の意味を維持しつつ、縦方向にも読みやすい太さと高さにする。

差し替え対象:

- `KeyActionMapper.getDisplayActions()`
  - `ToggleDakuten`, `ToggleDakutenOnly`, `ToggleHandakutenOnly` は `custom_key_kana_case_24`。
  - `ToggleCase` は `custom_key_english_case_24`。
- `KeyActionMapper.iconResIdForAction()`
  - 同上。
- `KeyboardRepository.drawableResIdForAction()`
  - `ToggleDakuten` と `ToggleCase` を新 resource にする。
- `KeyIconBuiltInDrawable.allowList`
  - 新 resource 名を追加する。
  - 既存の `kana_small` / `kana_small_custom` / `english_small` は互換性のため削除しない。

`FlickKeyboardView.getSpecialIconTargetSizePx()` は、濁点/小文字系専用倍率を持たせない。入力モード切替系の既存特別扱いは別件として維持するが、今回の修正では触らない。

既存 layout / DB に古い `drawableResId` が入っていた場合は、そのまま表示できる。編集画面で該当 action を保存し直すと新 resource に更新される。既存データの一括 migration は不要。

## 編集 UI

`app/src/main/res/layout/fragment_key_editor.xml` の特殊キー設定に、色スタイル選択を追加する。

配置は `specialCategoryChipGroup` の下、または `keyIconOverrideGroup` の上にする。

```text
特殊キーの見た目色
[特殊キー色] [通常キー色]
```

UI 要素案:

- `TextView`: `text_special_key_color_style_title`
- `ChipGroup`: `special_key_color_style_chip_group`
- `Chip`: `chip_special_key_color_style_special`
- `Chip`: `chip_special_key_color_style_normal`

表示条件:

- `chip_special` 選択時のみ表示。
- `chip_normal` 選択時は非表示。

初期値:

- 既存キー: `key.specialKeyColorStyle`。
- 新規/旧データ: `SPECIAL`。

保存:

- 特殊キーとして保存する場合だけ選択値を `updatedKey.specialKeyColorStyle` に入れる。
- 通常キーとして保存する場合は `SPECIAL` に戻すか、値を保持しても描画では無視する。実装はデータの意味を明確にするため `SPECIAL` へ戻す。

## 動的キーとの関係

`FlickKeyboardView.updateDynamicKey()` は `info.keyData.copy(label/action/drawableResId)` で動的状態を反映している。新フィールドは copy 元から保持されるため、Sumire 特殊キーや dynamicStates の表示更新でも色スタイルは失われない。

`KeyActionMapper` の action mapping のうち、保存文字列と `KeyAction` の意味は変更しない。変更するのは濁点/小文字系アクションの既定 icon resource だけにする。

## テスト方針

単体テスト:

- `SpecialKeyColorStyle.fromDbValue(null/unknown)` が `SPECIAL` を返す。
- `KeyData` の default が `SPECIAL` である。
- `KeyActionMapper.iconResIdForAction()` が濁点/小文字系アクションで正規化済み drawable を返す。
- `KeyboardRepository.drawableResIdForAction()` が濁点/小文字系アクションで正規化済み drawable を返す。
- `KeyIconBuiltInDrawable` が新旧 resource 名の両方を許可する。
- 通常キーは `specialKeyColorStyle = NORMAL/SPECIAL` に関係なく通常キー色として解決される。
- 特殊キー `SPECIAL` は特殊キー色、`NORMAL` は通常キー色として解決される。

Repository/import-export テスト:

- 旧 DB/旧 JSON 相当で `specialKeyColorStyle` 欠損時、復元結果は `SPECIAL`。
- `NORMAL` を保存した特殊キーが DB round-trip で `NORMAL` のまま戻る。
- JSON export/import で `specialKeyColorStyle` が保持される。

手動確認:

- カスタムキーボードで `ToggleDakuten` / `ToggleCase` の既定アイコンが従来より大きく見える。
- action picker と特殊キーの実表示で、濁点/小文字系アイコンの透明余白が他の action icon と同程度になっている。
- 既存 layout の古い drawable icon も引き続き表示される。
- 特殊キー色/通常キー色を切り替えても、タップ、フリック、長押し、MoveToCustomKeyboard、ユーザー画像アイコンが従来通り動作する。
- custom theme、default light、default dark で色の切り替わりが破綻しない。

## 実装順序

1. `SpecialKeyColorStyle` と `KeyData` のフィールドを追加する。
2. Room entity、migration、Repository、import/export を対応する。
3. 正規化済みの濁点/小文字系 drawable を追加する。
4. `KeyActionMapper`、`KeyboardRepository.drawableResIdForAction()`、`KeyIconBuiltInDrawable.allowList` を新 drawable へ対応させる。
5. `FlickKeyboardView` に visual palette helper を追加し、直接 `isSpecialKey` で色を選ぶ箇所を置き換える。
6. `KeyEditorFragment` と `fragment_key_editor.xml` に色スタイル選択 UI を追加する。
7. 単体テストを追加し、既存テストを通す。

## 非対象

- `KeyAction` の保存文字列や入力処理の変更。
- 既存の `kana_small` / `kana_small_custom` / `english_small` resource の削除や破壊的変更。
- `FlickKeyboardView` への濁点/小文字専用サイズ倍率追加。
- 通常キーごとの任意色指定。
- 全体テーマ設定の追加。
