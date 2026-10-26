# 辞書データのライセンスと出典

このリポジトリのプログラムコードには [MIT License](LICENSE) を適用します。
辞書データには以下の出典ごとの条件が適用されます。MITの表記は、第三者由来の
辞書データをMITに変更するものではありません。

## かな→英語辞書

`src/main/english/reading-supplement.tsv`、採用・表記決定を記録した
`src/main/english/candidate-review.tsv` と、その内容を含む `english_reading` 辞書は、
JMdict由来部分およびこのプロジェクトの編集・追加部分を
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) で提供します。
CMUdict由来部分の著作権表示・条件・免責も維持します。
第三者の原資料について、当プロジェクトが新たな権利を主張するものではありません。

| 出典 | 権利者・作成者 | 適用条件とリポジトリ内の表示 |
| --- | --- | --- |
| [JMdict / EDRDG](https://www.edrdg.org/edrdg/licence.html) | James William BREEN、Electronic Dictionary Research and Development Group | CC BY-SA 4.0。著作権・出典・改変表示と継承条件。[保存した公式表記](src/main/resources/third-party/JMDICT-LICENSE.html) |
| [NGSL 1.2](https://www.newgeneralservicelist.com/new-general-service-list) | Charles Browne、Brent Culligan、Joseph Phillips | CC BY-SA 4.0。一般語の選定・頻度順の調査に利用。原本の頻度値は配布辞書に含めない |
| [CMUdict](https://github.com/cmusphinx/cmudict/tree/74790861f652b15e4ac49015a90074ad62a27690) | Copyright (C) 1993–2015 Carnegie Mellon University. All rights reserved. | 原本の再配布条件・免責を維持。[LICENSE全文](src/main/resources/third-party/CMUDICT-LICENSE) |
| [JapaneseCorpus](https://github.com/KazumaProject/JapaneseCorpus/releases) | 上流配布に記録された各出典の権利者 | 上流の英語辞書と出典表示を利用。[第三者表記](src/main/resources/THIRD-PARTY-NOTICES.md) |

変更内容は、読み・表記の選定、カタカナからひらがなへの正規化、確認した読み揺れの追加、
CMUdictの米語発音から確認したかな読みの作成、コスト設定、品質検査、実用性に基づく採用・除外、一般語の小文字化、略語・固有名詞の表記維持、重複統合とバイナリ化です。
各採用行の出典・判断は補完TSV、版・リビジョン・SHA-256・作成手順は
[辞書作成ドキュメント](docs/english-dictionary.md) に記録しています。
日本語の読みと英語表記を確認した公式資料は、各行の `source` にURLを記録しています。
資料の文章やPDF自体は補完TSVに転載していません。

CC BY-SA 4.0の[全文](https://creativecommons.org/licenses/by-sa/4.0/legalcode.en)に従い、
出典・著作権・ライセンス・免責・変更内容の表示を維持し、改変データの継承条件を守ってください。
ライセンスで認められた利用を制限する追加条件や技術的制限は付けられません。
EDRDGの公式表記には、アプリ内の出典表示とデータ更新手順に関する記述もあります。
既存行を追加・更新するときは最新資料との対応を確認し、出典・変更理由を記録します。

## 既存の日本語辞書とその他のデータ

Mozcのコード部分と辞書部分は条件が異なります。辞書にはNAIST/IPAdic、ICOT、
沖縄辞書などの条件が含まれます。原本の表示を次のファイルに保存しています。

- [MOZC-LICENSE](src/main/resources/third-party/MOZC-LICENSE)
- [IPADIC-COPYING](src/main/resources/third-party/IPADIC-COPYING)
- [IPADIC-NOTICE](src/main/resources/third-party/IPADIC-NOTICE)

JapaneseCorpus由来データの原本表記は
[third-party/LICENSE-DATA.md](src/main/resources/third-party/LICENSE-DATA.md) と
[third-party/NOTICE.md](src/main/resources/third-party/NOTICE.md) に保存しています。
これらは上流の文書であり、その中の「このリポジトリ」はJapaneseCorpusを指します。
その他の辞書入力も、それぞれの出典・ライセンス条件に従います。この文書は、未確認の
入力データに一律のライセンスを与えるものではありません。

## 追加した専門用語

人事・労務の追加用語には、厚生労働省の[雇用管理に役立つ多言語用語集](https://www.mhlw.go.jp/yougosyu/index.html)から用語の表記と、資料中に明示された読みを採録しています。本リポジトリで用語を選定し、読みを対応づけ、既存の専門用語カテゴリへ整理しています。取得元の行、読みの根拠、元データの SHA-256 は `src/main/classification/reviews/2026-10-26-business-mhlw.jsonl` に記録しています。定義、翻訳、例文、資料本文は複製していません。同省の[利用規約](https://www.mhlw.go.jp/chosakuken/)に基づき、厚生労働省用語集由来のデータには公共データ利用規約（第1.0版）（PDL1.0）を適用し、出典と条件を表示します。

Mozc 由来の追加レコードは、[Google Mozc の固定コミット](https://github.com/google/mozc/tree/c4b2aef7c65e61be394bacadaec200eb5b97f91c/src/data/dictionary_oss)に照合し、読み・品詞・コストを元レコードのまま収録しています。これらのレコードは再ライセンスせず、既存の Mozc および同梱辞書データに記載した出典別条件を維持します。

## アニメ・漫画・架空キャラクターの対象層レビュー

`src/main/classification/reviews/2026-10-23-media-demographics-complete.jsonl` の完了レビューは、
[patelris の Anime and Manga Dataset 2026](https://www.kaggle.com/datasets/patelris/anime-and-manga-dataset-2026)
version 6（CC BY 4.0）の `demographics`、`genres`、`rating`、MyAnimeList IDを利用します。
架空キャラクターの名前と出典IDには
[fadhilakbar の Anime Characters Dataset](https://huggingface.co/datasets/fadhilakbar/anime-characters-dataset)
（CC BY 4.0）を利用します。帰属表示、取得版、入力ファイルのSHA-256、対象の参照先を各レビューに記録します。

加工内容は、作品名のUnicode正規化、かな読みのローマ字照合、MyAnimeList IDを介した出典結合、
明示タグの対象層ラベルへの対応付け、Hentai／Rx - Hentaiタグの成人向け除外です。
キャラクターは同一の出典IDを持ち、確認できた出典アニメの対象層が一致する場合だけ分類します。
入力CSV、作品説明、レビュー本文はリポジトリに複製しません。ライセンス表示は元データを
CC BY 4.0の下で利用したことを示し、原作出版社による一次資料での全件確認を意味しません。

`src/main/classification/reviews/2026-10-23-media-demographics-gemini.jsonl` は、出典未確認だった候補8,197組をGemini 3.5 Flash-Liteで最善推定したレビューです。送信内容は辞書候補の読み・表記などの公開語彙データです。モデルのカテゴリ選択は独立した典拠確認ではなく、成人向けと推定した34組は全配布カテゴリから除外しています。モデル名・入力とプロンプトのハッシュ・集計・トークン使用量は `src/main/classification/media-demographic-gemini-manifest.json` に記録し、生のAPI応答とキーは含めません。実行は無料枠だけを使用し、課金は有効になっていません。Googleの[Gemini API料金・利用条件](https://ai.google.dev/gemini-api/docs/pricing)では無料枠のコンテンツがGoogle製品の改善に使用され得ると記載されています。

## 哲学者・思想家の人名データ

`src/main/classification/person-additions/philosophers-2026-10-22.jsonl` の人名表記・読みは、各行の `source_url` に記録した[Web NDL Authorities](https://id.ndl.go.jp/)の典拠データを確認して採録しています。同データは国立国会図書館の出典を表示して再利用します。各行には典拠ID、原資料の表記・読み、確認箇所、確認日、典拠レスポンスのSHA-256を記録します。

人物を哲学者・思想家カテゴリに分類する根拠は、各行の `classification_source_url` に記録したWikidata項目の職業記述です。Wikidataのデータは[CC0](https://creativecommons.org/publicdomain/zero/1.0/)で提供されています。分類テキストや典拠資料の本文はリポジトリへ転載しません。

## 2026-10-25の著名人の人名データ

`src/main/classification/person-additions/notable-people-2026-10-25.jsonl` は、
[JLPGA](https://www.lpga.or.jp/)、[日本棋院](https://www.nihonkiin.or.jp/)、
[日本プロ麻雀協会](https://npm2001.com/)、各プロ野球球団、
[国立国会図書館「近代日本人の肖像」](https://www.ndl.go.jp/portrait/)、
[Web NDL Authorities](https://id.ndl.go.jp/)、[京都賞](https://www.kyotoprize.org/)、
[日本学士院](https://www.japan-acad.go.jp/)、文学賞の公式資料、
[青二プロダクション](https://www.aoni.co.jp/)、[賢プロダクション](https://www.kenproduction.co.jp/)、
[スターダストプロモーション](https://www.stardust.co.jp/)、[UUUM](https://uuum.jp/)、
[UNIVERSAL MUSIC JAPAN](https://www.universal-music.co.jp/)の公開資料で確認した
氏名・公表活動名と明示的なかな読みを採録しています。各行に実際の出典URL、原表記・原読み、
確認箇所、確認日、取得原本のSHA-256を記録し、分類確認に別資料を用いた場合もその出典を示します。

加工は空白の除去、かなの機械的なひらがな化（半角かなの濁点等の結合を含む）、
分類、既存辞書との重複除外、品詞・コストの割当です。英字からの推測読みや非公開の別名は追加していません。
プロフィール本文、写真、名簿ページ、検索結果そのものは転載しません。
第三者資料の権利は各提供元に帰属し、この記載によりそれらにMIT等のライセンスを付与するものではありません。
国立国会図書館のデータは出典を表示して利用します。

`src/main/classification/person-additions/board-sports-2026-10-25.jsonl` の氏名・読みは、[AJSA公認プロスケートボーダー](https://ajsa.jp/professional-skateboarder/)、[X Games Japan選手一覧](https://xgamesjapan.com/athletes/)、[JOC公式オリンピック・ユース五輪・アジア冬季大会名鑑と結果](https://www.joc.or.jp/)、[日本ダンススポーツ連盟（JDSF）公式資料](https://www.jdsf.or.jp/)で確認しています。各候補行に参照URL、原表記・原読み、確認箇所と取得ページのSHA-256を記録します。公式かな表記だけを採録し、英字から読みを推定しません。名簿・プロフィール・結果表の本文は転載せず、第三者資料の権利は各提供元に帰属します。

## 漢方・医療辞書

`src/main/specialist` のKNApSAcK KAMPO由来データと `kampo` 辞書の加工部分は
[CC BY-SA 2.1 日本](https://creativecommons.org/licenses/by-sa/2.1/jp/) に従います。
KNApSAcK Family © 金谷 重彦 (奈良先端科学技術大学院大学)。
万病辞書 MANBYO_202106 と百薬辞書 HYAKUYAKU_202107 由来データおよび
`medical` 辞書の編集・読み補完部分は [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) で提供します。
作成者の表示は各提供元の保存した公式文書を維持します。

加工は表記・読みの抽出と正規化、重複除外、空欄へのGemini推定読みの追加、
品詞・コストの割当、バイナリ化です。原本の自動読みと推定読みの正確性は全件確認していません。
出典・版・取得ZIPハッシュ・各行の原本対応・加工状態は
[専門辞書の文書](docs/specialist-dictionaries.md) と `src/main/specialist` に記録します。
出典・著作権・条件・加工表示を保持し、漢方の継承条件を守ってください。
配布時はライセンス全文を含む別添 `DICTIONARY-LICENSES.txt` を維持してください。
