# IMEService リファクタリング設計メモ

## 目的

`IMEService.kt` は現在 13,000 行を超えており、以下の責務が 1 クラスに集中している。

- IME のライフサイクル制御
- 入力状態の保持
- 候補生成と変換制御
- 通常キーボードとフローティングキーボードの UI 制御
- 各種設定の読み込み
- クリップボード、音声入力、物理キーボード、Zenz 連携
- `InputConnection` の委譲

この状態では、ある機能を変更したときに別の機能へ副作用が波及しやすい。  
本メモの目的は、現在の機能を壊さずに責務を分割し、変更しやすい構造へ段階的に移行するための設計方針を定めること。

## 現状の問題

### 1. 状態が分散している

- `var`
- `AtomicBoolean`
- `MutableStateFlow`
- `MutableSharedFlow`

が `IMEService` に大量に散在しており、どの状態がどの機能の所有物かが分かりにくい。

### 2. UI とドメインロジックが密結合

- 候補生成
- 変換確定
- カーソル移動
- Enter / Space / Delete の振る舞い

の中で、直接 View 更新や Drawable 切り替えが行われている。

### 3. 通常 UI とフローティング UI の重複が多い

以下のような通常版と Floating 版のペアが多数存在する。

- `handleTapAndFlick` / `handleTapAndFlickFloating`
- `handleTap` / `handleTapFloating`
- `handleFlick` / `handleFlickFloating`
- `handleLongPress` / `handleLongPressFloating`
- `handleSpaceKeyClick` / `handleSpaceKeyClickFloating`
- `handleJapaneseModeSpaceKey` / `handleJapaneseModeSpaceKeyFloating`
- `handleNonEmptyInputEnterKey` / `handleNonEmptyInputEnterKeyFloating`

この構造では、仕様変更時に両方へ修正が必要になり、修正漏れが起こりやすい。

### 4. 候補生成ロジックが重複している

候補生成には少なくとも以下の系統がある。

- 通常候補
- Original 候補
- prediction なし候補
- 英数かな候補
- Zenz / Zenzai 補助候補

それぞれに、辞書検索、学習辞書、NG ワード除外、ローマ字変換候補の組み立てが似た形で重複している。

### 5. 設定読み込みが肥大化している

`onStartInput()` で `AppPreference` から大量の値を読み出してメンバに展開しているため、

- 設定の追加変更に弱い
- どの設定がどこで使われるか追いにくい
- テストしにくい

という問題がある。

## リファクタリング方針

### 方針 1. `IMEService` を薄いオーケストレータにする

`IMEService` は Android フレームワーク境界に専念し、実際の入力処理や候補生成は別コンポーネントへ委譲する。

### 方針 2. 状態と処理をセットで分割する

単にメソッドを別ファイルへ移すのではなく、以下のように「状態の所有者」と「処理の責務」を揃えて分割する。

- 候補に関する状態と処理
- キーボードモードに関する状態と処理
- エディタ操作に関する状態と処理
- 表示面に関する状態と処理

### 方針 3. 通常 UI とフローティング UI の差分を表現する

ロジックは共通化し、描画差分だけを抽象化する。  
`Floating` 専用の処理を増やすのではなく、同一ロジックを別 Surface に適用する方向に寄せる。

### 方針 4. 一気に分解しない

最初から大規模な構造変更を行うと、IME のような副作用の多い機能では壊れやすい。  
そのため、小さな PR 単位で段階的に移行する。

## 目標アーキテクチャ

## 全体像

```text
IMEService
  |- ImeSessionState
  |- ImePreferencesSnapshot
  |- EditorGateway
  |- CandidateService
  |- InputActionDispatcher
  |- KeyboardModeController
  |- KeyboardSurfaceCoordinator
  |- HardwareKeyboardCoordinator
  |- ExternalFeatureCoordinator
```

## コンポーネント設計

### `ImeSessionState`

役割:

- 入力セッション中の状態を集約する

保持対象の例:

- `inputString`
- `stringInTail`
- `isHenkan`
- `hasConvertedKatakana`
- `suggestionClickNum`
- `currentInputType`
- `current keyboard mode`
- `selectMode`
- `cursorMoveMode`
- `bunsetsuPositionList`

ポイント:

- `AtomicBoolean` と `var` の乱立をやめる
- UI 状態と入力状態を最低限分離する
- まずは単純なデータ集約から始める

### `ImePreferencesSnapshot`

役割:

- `AppPreference` から現在セッションで使う設定値を読み出し、不変オブジェクトにまとめる

例:

```kotlin
data class ImePreferencesSnapshot(
    val isLiveConversionEnabled: Boolean,
    val nBest: Int,
    val qwertyShowCursorButtons: Boolean,
    val candidateTabVisibility: Boolean,
    val keyboardThemeMode: String,
)
```

ポイント:

- `onStartInput()` で 1 回ロードする
- 以降は `appPreference.xxx` ではなく snapshot を参照する
- テスト時に設定差し替えしやすくなる

### `EditorGateway`

役割:

- `currentInputConnection` へのアクセスを一元化する

責務:

- `commitText`
- `setComposingText`
- `finishComposingText`
- `deleteSurroundingText`
- `getTextBeforeCursor`
- `getTextAfterCursor`
- `setSelection`
- `sendKeyEvent`

ポイント:

- `IMEService` 自体が `InputConnection` を直接抱えない形へ寄せる
- null 判定や API 差異を gateway に閉じ込める
- 入力先アプリとの境界を明確にする

### `CandidateService`

役割:

- 候補生成の組み立てを担当する

構成案:

- `CandidateRequest`
- `CandidateSources`
- `CandidateFilters`
- `CandidateAssembler`

イメージ:

```kotlin
data class CandidateRequest(
    val input: String,
    val mode: CandidateMode,
    val qwertyMode: TenKeyQWERTYMode,
    val usePrediction: Boolean,
    val useBunsetsu: Boolean
)
```

責務:

- ユーザー辞書候補取得
- テンプレート候補取得
- 学習候補取得
- エンジン候補取得
- ローマ字変換候補生成
- NG ワード除外
- 重複除去
- Zenz 補助候補との連携

ポイント:

- 現在の `getSuggestionList*` 系を 1 つの API へ寄せる
- `Original`, `WithoutPrediction`, `EnglishKana` を mode 差分として扱う

### `InputActionDispatcher`

役割:

- キー入力イベントをドメイン操作へ変換する

責務:

- Tap / Flick / LongPress の解釈
- Enter / Space / Delete の分岐
- カーソル移動
- 範囲選択
- コピペ
- 濁点、小文字、カタカナ切り替え

ポイント:

- `handle*` メソッド群の入口を整理する
- 「入力イベントをどう解釈するか」を 1 箇所に寄せる

### `KeyboardModeController`

役割:

- キーボード種別と表示モードの遷移を担当する

責務:

- TenKey / QWERTY / Romaji / Sumire / Custom / Number の切り替え
- 現在モードに応じた入力モード変更
- Sumire 動的キー状態更新
- カスタムレイアウト切り替え

ポイント:

- `showKeyboard()`
- `updateKeyboardLayout()`
- `createNewKeyboardLayoutForSumire()`

周辺をこの層へ寄せる。

### `KeyboardSurfaceCoordinator`

役割:

- 通常キーボード UI とフローティング UI の表示差分を吸収する

インターフェース案:

```kotlin
interface KeyboardSurface {
    fun showSuggestions(items: List<CandidateItem>)
    fun hideSuggestions()
    fun updateHenkanUi(input: String)
    fun updateEnterKey(inputType: InputTypeForIME)
    fun setKeyboardVisible(visible: Boolean)
    fun setSymbolKeyboardVisible(visible: Boolean)
}
```

実装候補:

- `MainKeyboardSurface`
- `FloatingKeyboardSurface`

ポイント:

- `*Floating` の重複ロジックを減らす
- ロジックは共通で、描画だけ差し替える

### `HardwareKeyboardCoordinator`

役割:

- 物理キーボード接続時の表示モード切り替えを担当する

責務:

- `InputManager` による接続状態監視
- 物理キーボード判定
- 接続中フラグの管理
- `requestCursorUpdates()` の開始と停止
- floating candidate の表示制御
- dock 表示制御
- 物理キーボード接続時の insets / window ポリシー反映

状態の例:

- `hasHardwareKeyboardConnected`
- `physicalKeyboardEnable`
- `physicalKeyboardFloatingXPosition`
- `physicalKeyboardFloatingYPosition`
- `initialCursorDetectInFloatingCandidateView`
- `initialCursorXPosition`

ポイント:

- 物理キーボード接続は単なるデバイス検知ではなく、IME の表示モード切り替えとして扱う
- 候補表示先が通常候補ビューではなく floating candidate へ切り替わる点を設計上明示する
- `onInputDeviceAdded` / `onInputDeviceChanged` / `onInputDeviceRemoved` と `onUpdateCursorAnchorInfo` を同じ責務で管理する

### `ExternalFeatureCoordinator`

役割:

- 外部機能連携の窓口

対象:

- 音声入力
- クリップボード履歴
- Zenz / Zenzai 初期化と実行

ポイント:

- IME 入力の中核ロジックから副作用の強い機能を切り離す
- 初期化失敗時のフォールバックをまとめる

## データフロー案

### 入力から候補表示まで

```text
KeyEvent / FlickEvent
  -> InputActionDispatcher
  -> ImeSessionState 更新
  -> CandidateService.requestCandidates()
  -> HardwareKeyboardCoordinator が表示先を判定
  -> KeyboardSurfaceCoordinator.showSuggestions()
  -> EditorGateway.setComposingText()
```

### 物理キーボード接続時

```text
InputManager event
  -> HardwareKeyboardCoordinator
  -> hasHardwareKeyboardConnected / physicalKeyboardEnable 更新
  -> requestCursorUpdates on/off
  -> dock 表示切り替え
  -> floating candidate 表示切り替え
  -> IMEService / Surface へ状態通知
```

### Enter / Space / Delete の処理

```text
InputActionDispatcher
  -> EnterActionHandler / SpaceActionHandler / DeleteActionHandler
  -> EditorGateway
  -> ImeSessionState
  -> KeyboardSurfaceCoordinator
```

## まず分けるべき責務

優先度の高い順に以下を切り出す。

### 1. `ImePreferencesSnapshot`

理由:

- 副作用が比較的小さい
- `onStartInput()` を短くできる
- 後続のクラス切り出しで設定参照が楽になる

### 2. `CandidateService`

理由:

- 重複が多く効果が大きい
- 候補生成ロジックをテストしやすくなる
- 将来的に Zenz 連携を分離しやすい

### 3. `EditorGateway`

理由:

- `currentInputConnection` 直接参照を減らせる
- カーソル操作や commit 系の安全性を高められる

### 4. `KeyboardSurfaceCoordinator`

理由:

- Floating 系重複を減らす土台になる
- UI 差分の閉じ込め先を作れる

### 5. `HardwareKeyboardCoordinator`

理由:

- 物理キーボード分岐が候補表示、dock、cursor update、window 制御にまたがっている
- UI 差分ではなく、表示モード切り替えの責務として独立させた方が見通しがよい
- フローティング候補や `CursorAnchorInfo` 追従を 1 箇所に集約できる

## 後回しにすべきもの

以下は依存範囲が広いため、初手で触らない方が安全。

- `onKeyDown()` / `onKeyUp()` の全面再設計
- `InputConnection` 実装の完全置換
- 音声入力、画像貼り付け、クリップボード履歴の同時整理
- Sumire / Custom keyboard の仕様変更

## 段階的移行プラン

### Phase 0. 現状固定

やること:

- スモークテスト観点の整理
- 現状挙動を壊してはいけない操作を列挙

優先テスト対象:

- 日本語入力の通常変換
- live conversion
- Enter / Space / Delete
- QWERTY とテンキー切り替え
- フローティング時の候補表示
- クリップボード貼り付け
- 物理キーボード接続時の挙動

### Phase 1. `ImePreferencesSnapshot` 導入

やること:

- 設定値群を data class 化
- `onStartInput()` で snapshot をロード
- 参照先を順次 `preferences.xxx` に置換

完了条件:

- `onStartInput()` の設定代入ブロックが大幅に短くなる

### Phase 2. `CandidateService` 導入

やること:

- `getSuggestionListOriginal`
- `getSuggestionList`
- `getSuggestionListWithoutPrediction`
- `getSuggestionListEnglishKana`

を新クラスへ移管する。

完了条件:

- 候補生成の大半が `IMEService` 外にある
- mode 切り替えで候補取得できる

### Phase 3. `EditorGateway` 導入

やること:

- 入力先アプリへの書き込み API を gateway 化
- 主要な commit / delete / selection 操作を移す

完了条件:

- `currentInputConnection` 直接参照箇所が大幅に減る

### Phase 4. `KeyboardSurfaceCoordinator` 導入

やること:

- 通常 UI / Floating UI の共通操作を interface 化
- `updateUIinHenkan*` などの重複関数を統合

完了条件:

- `Floating` サフィックス関数が減り始める

### Phase 5. `HardwareKeyboardCoordinator` 導入

やること:

- `isDevicePhysicalKeyboard()`
- `checkForPhysicalKeyboard()`
- `onInputDeviceAdded()` / `Changed()` / `Removed()`
- `onUpdateCursorAnchorInfo()`

周辺の責務を集約する。

完了条件:

- 物理キーボード接続時の表示切り替えと候補追従の責務が `IMEService` 外へ移る
- `physicalKeyboardEnable` の解釈が 1 箇所に集約される

### Phase 6. `InputActionDispatcher` / `KeyboardModeController` 分離

やること:

- `handle*` 群の責務整理
- キーボード遷移と入力解釈の分離

完了条件:

- `IMEService` は Android イベント受け取りと委譲に集中する

## テスト戦略

### 1. まず回帰観点を文章で固定する

IME は UI テストや端末依存が強いため、いきなり完全自動化を目指すより、まず回帰観点を明文化する。

### 2. 分離後に単体テストを増やす

単体テストを書きやすい対象:

- `ImePreferencesSnapshot`
- `CandidateService`
- `EditorGateway` の判定ロジック
- `HardwareKeyboardCoordinator`
- `KeyboardModeController`

### 3. 結合テストは代表操作に絞る

最低限の代表ケース:

- ひらがな入力から候補表示
- Space で変換
- Enter で確定
- Delete 長押し
- フローティングで候補選択
- 物理キーボード接続時の候補表示

## 実装ルール

- 1 PR で責務を 1 つだけ分離する
- リネームと仕様変更を同時にしない
- まず移設、その後に整理する
- 通常 UI と Floating UI の統合は interface 導入後に行う
- テスト不能な処理は最低でも回帰チェックリストを付ける

## 初手の具体案

最初の PR では、以下だけを行うのが安全。

1. `ImePreferencesSnapshot` を追加する
2. `AppPreference` から snapshot を作る mapper を追加する
3. `onStartInput()` の設定代入を snapshot 利用に置き換える
4. 挙動変更は一切しない

その次の PR で `CandidateService` を導入し、その後に `HardwareKeyboardCoordinator` を分離する。

## 非目標

今回のリファクタリングでは、以下は目的に含めない。

- 新機能追加
- キーボード仕様変更
- 候補順位アルゴリズムの見直し
- Zenz の機能改善
- UI デザイン刷新

## まとめ

このリファクタリングで重要なのは、`IMEService` を一度に分解し切ることではない。  
先に境界を作り、

- 設定
- 状態
- 候補生成
- エディタ操作
- UI surface
- 物理キーボード表示モード

を分離できる形に変えることが最優先である。

最初の成功条件は「クラスを大量に増やすこと」ではなく、  
`IMEService` を読んだときに変更点の入口が予測できる状態を作ることとする。
