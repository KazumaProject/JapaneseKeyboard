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
