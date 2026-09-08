# 候補欄とシステム領域 / Candidate strip and system insets

## 原因 / Root causes

録画用 `SkinFidelityHostActivity` はキー単体を表示する部品検証用で、候補欄を含んでいませんでした。また、edge-to-edge の Activity 下端に配置し、ナビゲーション Insets を処理していませんでした。したがって、この録画だけで本番 IME 全体のレイアウトを検証したことにはなりません。部品ホストにはシステム Insets を適用し、全体表示は実際の `IMEService` を使う別の検証に分けました。

The component recording host omitted candidates and positioned its keyboard at an edge-to-edge Activity's bottom without system insets. It now respects those insets. Whole-keyboard validation uses the actual InputMethodService, its candidate adapters and its IME window.

本番 IME にも別の不具合がありました。親の高さは「キー＋候補領域」なのに、ナビゲーション領域をその高さの内側の padding として消費していました。Pixel 6 / Android 17 / 3ボタン式では、キーはナビゲーション領域の直上にありましたが、候補領域が 126 px 削られていました。

| Pixel 6, density 2.625 | Configured strip | Space above keys before fix |
| --- | ---: | ---: |
| Empty composition | 60 dp / 157 px | 31 px |
| Composing | 110 dp / 288 px | 162 px |

Separately, the production IME reserved navigation padding **inside** its configured content height. The measured Pixel 6 keyboard ended at y=2274, exactly where its 126 px navigation area began; its candidate region lost those 126 px. The recording-host overlap and the production candidate clipping had different causes.

候補の文字色・ショートカット・展開ボタンにも更新漏れがありました。キーは入力開始ごとに着せ替えを反映しましたが、候補アダプター等の配色はビュー作成時にしか反映されず、ライトでも前の暗いテーマ用の文字色が残りました。

Candidate text, shortcuts and the expansion button also retained stale colors: keys refreshed at input-session start, while these candidate surfaces applied appearance only during view creation.

## 修正 / Changes

- 親の高さを「内容の高さ＋実際の下部 Insets」とし、キーの寸法・候補の設定値・列数は維持します。旧レイアウト更新経路も同じ扱いにします。
- 入力開始時とビュー作成時に候補の配色を共通処理で適用します。
- デフォルトへ戻すと、候補各部の元の `ColorStateList` と背景、ショートカットの tint を復元します。
- 着せ替えを初回起動から使ってもデフォルトへ戻せるよう、ビューの基礎 Context は保存済みテーマから作り、その上に着せ替えの描画を適用します。

The window reserves insets outside the configured content. Candidate appearance refreshes in both lifecycle paths. Clearing overrides restores each role's original stateful colors and backgrounds. The underlying saved theme context is retained, including dynamic/seed colors, even when launching directly into a skin.

## 検証 / Verification

`SkinImeLayoutInstrumentedTest` temporarily selects only the isolated fidelity IME and restores the previous IME and preferences in `finally`. Clipboard content is excluded. It tests Default → Light → Dark → Default, with an empty composition, an active composition and the state after selecting a candidate.

Assertions cover navigation separation, reserved heights of 60/110/60 dp, unchanged two-row candidate columns, candidate/shortcut pixels adopting the selected skin color, and successful text input/selection. API 35 emulator runs cover portrait gesture navigation and landscape three-button navigation (1080×2400, density 2.625), plus portrait three-button navigation (1440×3120, density 3.5). These are layout/behavior checks, separate from the timestamped iOS/Android motion matrix.

- [Light, empty](evidence/ime-layout/light-empty.png)
- [Light, composing](evidence/ime-layout/light-composing.png)
- [Dark, empty](evidence/ime-layout/dark-empty.png)
- [Dark, composing](evidence/ime-layout/dark-composing.png)
- [Landscape, composing](evidence/ime-layout/landscape-light-composing.png)
- [Three-button measurements](evidence/ime-layout/three-button-measurements.json)
- [Landscape measurements](evidence/ime-layout/landscape-measurements.json)

- [Gesture-navigation measurements](evidence/ime-layout/gesture-measurements.json)
