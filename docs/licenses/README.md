# ライセンス情報 / License information

このリポジトリのライセンス資料の入口です。アプリ本体、辞書・共通資源、依存ライブラリ、Full版のネイティブコード・モデルを区別して記録しています。確認できない帰属・取得版・適用範囲は「未確認」と明記しています。

This index separates application code, dictionaries/data, dependencies and optional native/model resources. It records the evidence and keeps unconfirmed provenance or applicability explicitly unconfirmed.

| 対象 / Component | ドキュメント・原文 / Document or original notice |
| --- | --- |
| アプリ本体のコード | [MIT LICENSE](../../LICENSE): Copyright (c) 2024 Kazuma Naka |
| 全143カテゴリ辞書・その他の同梱辞書・共通資源 | [辞書ライセンス対応表](DICTIONARIES.md) |
| 辞書の全23通知・ライセンス原文・出典URL | [原文索引](DICTIONARIES.md#保持した全通知ライセンス全文)、[ハッシュと固定URL](../../app/src/main/assets/licenses/notice-snapshots.json) |
| v1.7.277の全ライセンス文書 | [公開添付と同じ原文](../../app/src/main/assets/licenses/sources/distribution-licenses.txt) |
| Lite/Full Standard Debugの全139外部依存モジュール | [依存ライブラリ一覧](DEPENDENCIES.md)、[版・POM・照合情報](dependency-metadata.json) |
| 全体の第三者通知 | [THIRD-PARTY-NOTICES.md](../../THIRD-PARTY-NOTICES.md) |
| Full版のモデル・ネイティブコード、外部入力 | 以下の一覧 |

MITは第三者の辞書・ライブラリ・モデルのライセンスを変更しません。表の要約は元の著作権表示、NOTICE、免責、帰属・継承条件を置き換えません。パッケージのPOMに書かれたライセンス宣言と、配布物に必要な追加通知も別の情報です。

## ネイティブコード・モデル

| 対象 | 出典・確認した条件 | 版・適用範囲・原文 |
| --- | --- | --- |
| `flexbox` のGoogle由来コード | Apache License 2.0。Copyright 2016 Google Inc. | [ソースの表示](../../flexbox/src/main/java/com/kazumaproject/android/flexbox/AlignContent.java)、[Apache 2.0全文](../../app/src/main/assets/licenses/sources/apache-2.txt)。元の全取得版は未確認 |
| `azooKey/llama.cpp` | MIT。Copyright (c) 2023–2024 The ggml authors | git submoduleの固定コミット `88b97a47dc7f5892e2d5a6856fbe9cfe237f9e5c` の [LICENSE](https://github.com/azooKey/llama.cpp/blob/88b97a47dc7f5892e2d5a6856fbe9cfe237f9e5c/LICENSE)。コードの実ビルド範囲は [CMakeLists.txt](../../zenz/src/main/cpp/CMakeLists.txt) を参照 |
| llama.cpp内のGGUF Pythonツール | MIT。Copyright (c) 2023 Georgi Gerganov | [固定版のgguf-py/LICENSE](https://github.com/azooKey/llama.cpp/blob/88b97a47dc7f5892e2d5a6856fbe9cfe237f9e5c/gguf-py/LICENSE)。ビルド時ツールとアプリ内の実行コードを区別 |
| llama.cpp内のlinenoise例 | BSD 2-Clause形式の個別表示・免責。Copyright 2010–2014 Salvatore Sanfilippo、2010–2013 Pieter Noordhuis、2025 Eric Curtin | [固定版の原文](https://github.com/azooKey/llama.cpp/blob/88b97a47dc7f5892e2d5a6856fbe9cfe237f9e5c/examples/run/linenoise.cpp/LICENSE)。例に付属する資料で、現行アプリへの組込みは未確認 |
| Zenz GGUFモデル | 公開モデルカードはApache 2.0を宣言 | [固定版モデルカード](https://huggingface.co/Miwa-Keita/zenz-v3.2-xsmall-gguf/blob/4f5423f0fad41a73b1242eb96fe5c12ae4fdca83/README.md)、[保持したメタデータ](notices/zenz-model-card.md)。[zenz/build.gradle](../../zenz/build.gradle) の既定値は `Miwa-Keita/zenz-v3.2-xsmall-gguf`、同リビジョン、Q5_K_M。ビルド設定による差替え、個々の重みの追加帰属・生成来歴は別途確認が必要で、網羅確認は未確認 |
| LiteRT-LM Androidランタイム | `com.google.ai.edge.litertlm:litertlm-android:0.14.0` のPOMはApache 2.0を宣言 | [依存ライブラリ一覧](DEPENDENCIES.md)。Fullのみ。ランタイムの条件を持ち込むモデルへ一律に適用しない |
| Gemmaモデル | GoogleはGemma 4用の [Apache 2.0](https://ai.google.dev/gemma/apache_2) と、対象を限定した従来の [Gemma Terms of Use](https://ai.google.dev/gemma/terms) を区別 | アプリはユーザーが指定したモデルファイルを読み込む。実際の持込み版・モデル派生物・付随する条件は未確認。モデル名だけから同一条件と断定しない |
| 持込み辞書・フォント・ユーザー登録・その他の外部入力 | 提供者またはユーザーの元資料の条件 | 同梱品の表から外部入力へ一律の許諾を与えない。個々の取得版・権利情報は本リポジトリから確認できない |

## アプリの表示と確認範囲

アプリの「ライセンスと出典」は、概要、辞書・データ、ライブラリ、Full版のAIモデル・ネイティブコードに分けて表示します。辞書はv1.7.277の固定入力・同梱HTML・通知原文に対応します。ライセンス原文と取得した通知はオフラインで閲覧できます。

ライブラリはFull Standard Releaseの137件、Lite Standard/F-Droid Releaseの136件の解決結果に対応します（BOM等のメタデータ依存を含む）。版別一覧とPOMの根拠は `app/src/main/assets/software-licenses/catalog.json` に保持しています。対象版のPOM照合に加え、取得できたAAR/JARとそのclasses.jarのLICENSE/NOTICE/COPYINGを原文のまま保存し、表示します。解決されたモジュールと縮小後APKの到達可能コードとの一対一対応を示すものではありません。

コルーチンは対象版のPOMに基づくApache 2.0、Zenzモデルは固定版モデルカードのApache 2.0として表示します。根拠のない一律の著作権表示は付与しません。個別著作権表示を取得できない場合は、その範囲を詳細画面に明記します。ユーザーが追加するモデルの利用条件は提供元の資料によります。

古いDebug依存の調査記録は [DEPENDENCIES.md](DEPENDENCIES.md) に残しています。アプリの表示に使うRelease版の一覧とは区別してください。

## 資料の更新と検証

辞書版を更新するときは、分類一覧・元入力のハッシュ・来歴・通知原文・本ドキュメントを同時に更新してください。依存版やモデル版を変更したときは、その版の一次資料を照合し、現在の別版の資料で代用しないでください。

```sh
python3 tools/classified-dictionaries/prepare.py --check
```

上記は配布辞書・通知・生成データのハッシュを確認します。依存ライブラリの根拠と再取得手順は [DEPENDENCIES.md](DEPENDENCIES.md) に記録しています。

Release依存の再照合とアプリ内資料の更新:

```sh
./gradlew -I tools/licenses/export-release.init.gradle :app:exportLicenseDependencies --no-configuration-cache
python3 tools/licenses/prepare-libraries.py build/reports/licenses/resolved-release.json
python3 tools/licenses/prepare-libraries.py build/reports/licenses/resolved-release.json --check
```

未登録の依存版・未対応のライセンス宣言は生成時に停止します。先に対象版のPOM・ソース資料を監査し、`dependency-metadata.json` の根拠を更新してください。原文スナップショットはバイナリ等の検証成果物ではなく、オフラインのライセンス表示に使用する配布資料です。

OpenMPの表示は、使用するNDK `29.0.14206865` の `NOTICE.toolchain` にあるOpenMPのApache 2.0 with LLVM Exceptionsおよび旧ライセンス・Intel通知の連続した原文を保持しています。保持した原文のSHA-256は `b69026e4bfd3f3d255d5754da7b1766ed0559362a744c23e08ec477c34c01274` です。
