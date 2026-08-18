# SumireキーボードスキンJSON v1

この仕様は、Sumire（Markdownヘルパーキーボード）の設定画面からオフラインで読み込める宣言型スキンを定義します。Sumire自身はAIサービス、ネットワーク、画像・フォント・コード実行を使いません。

正式な機械検証用ファイルは [`sumire-keyboard-skin-v1.schema.json`](./sumire-keyboard-skin-v1.schema.json)、そのまま編集できる雛形は [`template.json`](./template.json)、機能を一通り使った検証済み例は [`example.json`](./example.json) です。

## トップレベル

| パス | 型／必須 | 内容 |
| --- | --- | --- |
| `format` | 文字列・必須 | `sumire-keyboard-skin` 固定 |
| `formatVersion` | 整数・必須 | `1` 固定 |
| `id` | 文字列・必須 | `[a-z][a-z0-9._-]{2,63}`。保存名と設定値は `imported:<id>` |
| `name` | 文字列・必須 | 1～50文字。設定画面に表示 |
| `author` | 文字列・任意 | 最大50文字。省略時は「インポート済み」 |
| `description` | 文字列・任意 | 最大200文字 |
| `palette` | オブジェクト・必須 | スタイルから参照できる色 |
| `keys` | オブジェクト・必須 | キーの基本スタイルと役割別上書き |
| `surfaces` | オブジェクト・必須 | デッキ、候補欄、ツールバー、ポップアップ |
| `typography` | オブジェクト・必須 | 許可された組み込みフォント |
| `motion` | オブジェクト・必須 | 押下と背景アニメーション |

未知フィールドは受け付けません。将来のバージョンでフィールドが増えた場合も、v1のアプリは自動で無視せず、フィールドパスを示して拒否します。

## 色（`palette`）

`background`、`normalKey`、`specialKey`、`actionKey`、`normalKeyText`、`specialKeyText`、`actionKeyText`、`accent`、`secondaryAccent`、`candidateSurface`、`candidateText` をすべて指定します。色は `#RRGGBB`、`#AARRGGBB`、またはスタイル内だけで使える `@palette.<名前>` です。`#RRGGBB` のアルファ値は`FF`として扱います。

候補欄を含む文字色はWCAGの相対輝度からコントラスト比を計算します。通常・特殊・アクション・候補欄のいずれかが4.5:1未満の場合は警告を表示します。警告を確認して続行すれば保存できます。警告はエラーではありません。

## スタイル

`keys.base` と各 `surfaces` の値は次のスタイルオブジェクトです。`keys.character`、`modifier`、`action`、`space`、`candidate`、`toolbar`、`popup` は基本スタイルに対する部分上書きで、指定しなかった値を`base`から継承します。

```json
{
  "shape": "roundedRect",
  "fill": { "type": "solid", "color": "@palette.normalKey" },
  "cornerRadiusDp": 8,
  "insetDp": 1,
  "roughnessDp": 0,
  "cutSizeDp": 0,
  "stroke": { "color": "@palette.accent", "widthDp": 1 },
  "shadows": [],
  "decoration": {
    "type": "none",
    "color": "@palette.accent",
    "opacity": 0,
    "sizeDp": 1,
    "spacingDp": 8,
    "angleDegrees": 0
  }
}
```

### 図形

`roundedRect`、`capsule`、`cutCorner`、`hexagon`、`pixelNotched`、`roughRect` のみです。角丸は0～32dp、インセットは0～8dp、粗さは0～3dpです。`cutSizeDp`は0～32dpです。

### 塗り

- `solid`: `color`を1色指定。
- `linearGradient`: 2～4色の`colors`、同じ個数の`stops`、`angleDegrees`（0～360）を指定。
- `radialGradient`: 2～4色の`colors`、同じ個数の`stops`、中心`centerX`・`centerY`（0～1）、`radius`（0.01～1）を指定。

停止点は0で始まり1で終わり、厳密に増加していなければなりません。範囲外の数値をアプリが補正することはありません。

### 線、影、装飾

線の幅は0～4dpです。影は最大2個で、オフセットは各方向-8～8dp、ぼかしは0～12dpです。装飾は`none`、`dots`、`grid`、`stripes`、`scanlines`、`speckles`、`weave`のいずれかです。装飾の不透明度は0～1、サイズは0.1～8dp、間隔は0.5～32dp、角度は0～360です。

## サーフェス

`surfaces.deck`、`candidateStrip`、`candidatePanel`、`toolbar`、`popup` をすべて指定します。それぞれ通常のスタイルオブジェクトを持ち、役割ごとに背景や装飾を変えられます。

## 文字

`typography.font` は `sans`、`sansMedium`、`sansCondensed`、`serif`、`monospace` のみです。`weight` は `normal`、`medium`、`bold`、`letterSpacing` は-0.1～0.2です。任意フォントファイルやURLは指定できません。

## モーション

`motion.press` の`scale`は0.90～1.05、`translationXDp`・`translationYDp`は-4～4dp、各時間は0～500msです。`motion.background.type` は `none`、`pulse`、`sweep`、`shift` のいずれかです。`none`の周期は0～30秒、その他は2～30秒です。

設定画面のモーション設定は次の意味です。

- **フル**：押下の拡縮・移動と背景アニメーションを有効にします。
- **軽減**：移動と連続アニメーションを止め、押下時の状態変化だけを残します。
- **オフ**：すべてのスキンモーションを止めます。

キーごとの常時アニメーションはありません。画面全体につき低頻度の背景アニメーションを1本だけ使います。

## 入力、保存、互換性

- UTF-8のJSONだけを受け付け、最大256KiBです。先頭のUTF-8 BOMと、JSON全体を一重に囲む` ```json ... ``` `だけは除去します。それ以外の説明文や複数フェンスは拒否します。
- JSONは検証済みのままアプリ専用領域`filesDir/keyboard_skins/v1/<id>.json`へ`AtomicFile`で保存します。選択した元URIの永続権限は保持しません。
- 同じIDを再度読み込むと更新確認を表示し、承認したときだけ原子的に置換します。成功すると自動選択されます。
- JSON、ZIP、SVG、画像、任意フォント、URL、コード、シェーダー、スクリプト、テンプレート生成、AI呼び出しはv1の入力ではありません。
- 不在または壊れたインポートファイル、未知の参照、古い保存値は安全にデフォルトへフォールバックします。ストア変更ごとに`keyboard_skin_revision`を増やし、IMEを開いたままでも同じIDの更新・削除を再描画します。

## エラーの読み方

エラーは`keys.base.fill.colors[1]`のようなフィールドパスと理由で表示されます。パスをコピーしてAIへ返し、「そのパスだけを直し、仕様にない説明を出さず、JSON全体を再出力してください」と依頼してください。未知フィールドは削除し、範囲外の値は範囲内へ自分で選び直します。コントラスト警告だけなら、内容を確認してインポートを続行できます。
