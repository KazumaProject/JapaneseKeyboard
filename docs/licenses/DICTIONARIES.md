# 辞書のライセンス一覧 / Dictionary licenses

本書はPR #1156で採用した全30カテゴリ、その他の同梱辞書、共通資源、保持した通知全文への対応表です。辞書名の意味から適用条件を推測せず、固定入力と採用レコード・分類結果の対応を根拠にしています。

配布: [`v1.7.262-pre.3`](https://github.com/KazumaProject/kotlin-kana-kanji-converter/releases/tag/v1.7.262-pre.3)、配布コミット `d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5`。全123配布ファイル・861,053カテゴリレコードの対象は [配布マニフェスト](../../tools/classified-dictionaries/distribution-manifest.json) を参照してください。

[出典・固定入力・採用行対応](../../tools/classified-dictionaries/category-provenance.json) にハッシュと出典別件数を記録しています。専門辞書は固定元データの行・元読み・元表記と採用来歴を照合しています。各カテゴリの3ファイルは `app/src/main/assets/categories/<ID>/{tango,yomi,token}.dat.zip` です。分類・正規化・重複処理・品詞/コスト割当・バイナリ変換を行っています。地名1件は固定入力から表記を補正しています。

## 全30カテゴリ

W/N/P/J/K/Mは次節の出典を指します。WとNには共通Web入力の該当分を含み、併記は単独著作権者の断定や同じ語の二重計上を意味しません。共通品詞・接続情報にはMozcの通知も適用されます。

| ID | 辞書名 | レコード数 | 出典・適用条件の参照 | アプリで表示する詳細（同梱HTML） |
| --- | --- | ---: | --- | --- |
| `person` | 人物・人名 | 277,028 | [W](#w) / [N](#n) / [P](#p) / [J](#j) + [格闘家追加](#人物追加の出典) | [person.html](../../app/src/main/assets/licenses/classified/person.html) |
| `character` | 架空キャラクター | 8,870 | [W](#w) / [N](#n) / [P](#p) | [character.html](../../app/src/main/assets/licenses/classified/character.html) |
| `mythological_figure` | 神話・伝承の存在 | 3,629 | [W](#w) / [N](#n) / [P](#p) | [mythological_figure.html](../../app/src/main/assets/licenses/classified/mythological_figure.html) |
| `place` | 地名 | 85,399 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [place.html](../../app/src/main/assets/licenses/classified/place.html) |
| `address` | 住所 | 122,248 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [address.html](../../app/src/main/assets/licenses/classified/address.html) |
| `station` | 駅・停留所 | 6,816 | [W](#w) / [N](#n) / [J](#j) | [station.html](../../app/src/main/assets/licenses/classified/station.html) |
| `facility` | 施設 | 28,669 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [facility.html](../../app/src/main/assets/licenses/classified/facility.html) |
| `transport_route` | 交通路線 | 3,211 | [W](#w) / [N](#n) / [J](#j) | [transport_route.html](../../app/src/main/assets/licenses/classified/transport_route.html) |
| `organization` | 組織 | 47,185 | [W](#w) / [N](#n) / [P](#p) | [organization.html](../../app/src/main/assets/licenses/classified/organization.html) |
| `lineage` | 家系・氏族 | 2,986 | [W](#w) / [N](#n) / [P](#p) | [lineage.html](../../app/src/main/assets/licenses/classified/lineage.html) |
| `product` | 商品・サービス | 13,629 | [W](#w) / [N](#n) / [P](#p) | [product.html](../../app/src/main/assets/licenses/classified/product.html) |
| `vehicle` | 車両・船舶 | 3,403 | [W](#w) / [N](#n) / [P](#p) | [vehicle.html](../../app/src/main/assets/licenses/classified/vehicle.html) |
| `event` | イベント・事件 | 9,955 | [W](#w) / [N](#n) / [P](#p) | [event.html](../../app/src/main/assets/licenses/classified/event.html) |
| `award` | 賞 | 1,004 | [W](#w) / [N](#n) | [award.html](../../app/src/main/assets/licenses/classified/award.html) |
| `agreement` | 条約・協定 | 1,744 | [W](#w) / [N](#n) / [P](#p) | [agreement.html](../../app/src/main/assets/licenses/classified/agreement.html) |
| `initiative` | 事業・キャンペーン | 1,540 | [W](#w) / [N](#n) / [P](#p) | [initiative.html](../../app/src/main/assets/licenses/classified/initiative.html) |
| `term` | 一般語・専門用語 | 127,770 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [term.html](../../app/src/main/assets/licenses/classified/term.html) |
| `work_franchise` | 作品シリーズ | 270 | [W](#w) / [N](#n) / [P](#p) | [work_franchise.html](../../app/src/main/assets/licenses/classified/work_franchise.html) |
| `work_manga` | 漫画作品 | 1,003 | [W](#w) / [N](#n) / [P](#p) | [work_manga.html](../../app/src/main/assets/licenses/classified/work_manga.html) |
| `work_anime` | アニメ作品 | 642 | [W](#w) / [N](#n) / [P](#p) | [work_anime.html](../../app/src/main/assets/licenses/classified/work_anime.html) |
| `work_film` | 映画作品 | 1,180 | [W](#w) / [N](#n) / [P](#p) | [work_film.html](../../app/src/main/assets/licenses/classified/work_film.html) |
| `work_program` | 番組 | 2,381 | [W](#w) / [N](#n) / [P](#p) | [work_program.html](../../app/src/main/assets/licenses/classified/work_program.html) |
| `work_book` | 書籍作品 | 5,353 | [W](#w) / [N](#n) / [P](#p) | [work_book.html](../../app/src/main/assets/licenses/classified/work_book.html) |
| `work_game` | ゲーム作品 | 1,506 | [W](#w) / [N](#n) / [P](#p) | [work_game.html](../../app/src/main/assets/licenses/classified/work_game.html) |
| `work_music` | 音楽作品 | 3,789 | [W](#w) / [N](#n) / [P](#p) | [work_music.html](../../app/src/main/assets/licenses/classified/work_music.html) |
| `work_stage` | 舞台作品 | 354 | [W](#w) / [N](#n) / [P](#p) | [work_stage.html](../../app/src/main/assets/licenses/classified/work_stage.html) |
| `work_periodical` | 定期刊行物 | 1,029 | [W](#w) / [N](#n) / [P](#p) | [work_periodical.html](../../app/src/main/assets/licenses/classified/work_periodical.html) |
| `work_other` | 媒体未特定の作品・美術作品 | 5,173 | [W](#w) / [N](#n) / [P](#p) | [work_other.html](../../app/src/main/assets/licenses/classified/work_other.html) |
| `kampo` | 漢方・生薬 | 440 | [K](#k) | [kampo.html](../../app/src/main/assets/licenses/classified/kampo.html) |
| `medical` | 医療 | 92,847 | [M](#m) | [medical.html](../../app/src/main/assets/licenses/classified/medical.html) |

## 元データ・帰属・条件

<a id="w"></a>
### W: 日本語Wikipedia由来

入力は `only_wiki.txt.zip` および共通 `wiki_neologd_common.txt.zip`。Wikipediaの寄稿者の帰属・CC BY-SAの継承条件を保持します。[保存した上流通知](../../app/src/main/assets/licenses/sources/wiki.txt) はCC BY-SAへの参照を示していますが、旧入力の取得版、各語の記事・編集履歴との対応、当該入力に適用されたCCの版は未確認です。別途 [CC BY-SA 4.0全文](../../app/src/main/assets/licenses/sources/cc-by-sa-4.txt) を保持していますが、この全文の同梱だけで未確認の旧入力の版を確定しません。

<a id="n"></a>
### N: mecab-ipadic-neologd由来

入力は `only_neologd.txt.zip` および共通Web入力。[COPYINGの原文](../../app/src/main/assets/licenses/sources/neologd.txt) に Copyright (C) 2015–2019 Toshinori Sato (@overlast)、Apache License 2.0、および元データの表示が含まれます。はてなキーワード、日本郵便の郵便番号データ、スナフキん氏の駅名一覧、工藤拓氏の人名データ等への出典表示・謝辞も保持してください。旧入力の取得版と個々の元データの対応は未確認です。

<a id="p"></a>
### P: 人名の旧入力

`names.txt`。[上流personal-namesの通知](../../app/src/main/assets/licenses/sources/person.txt) はApache License 2.0を示しています。通知の取得リビジョンと入力の取得版は別の情報です。元入力の取得版・各語の著作権者の対応は未確認です。

<a id="j"></a>
### J: 地名の旧入力

`place.txt.zip`。[上流place-namesの通知](../../app/src/main/assets/licenses/sources/place.txt) は、日本郵便が郵便番号データに限って著作権を主張しない旨を引用しています。この記載を全地名・住所・地理情報の一律の無条件利用許諾として扱いません。旧入力の取得版・各語との対応は未確認です。

<a id="k"></a>
### K: 漢方・生薬 / Kampo

KNApSAcK KAMPOの処方名・生薬名。KNApSAcK Family © 金谷 重彦（奈良先端科学技術大学院大学）。条件はCC BY-SA 2.1 Japanで、出典・著作権・加工表示と継承条件を保持します。

| 入力 | 採用行数 | 取得版 | 出典 | 通知 |
| --- | ---: | --- | --- | --- |
| `kampo_core` | 257 | LATEST（取得ZIPをSHA-256で固定。日付付き版は未確認） | [KNApSAcK KAMPO](https://dbarchive.biosciencedbc.jp/jp/knapsack-kampo/data-1.html) | [kampo_core.txt](../../app/src/main/assets/licenses/sources/kampo_core.txt) |
| `kampo_herb` | 183 | LATEST（取得ZIPをSHA-256で固定。日付付き版は未確認） | [KNApSAcK KAMPO](https://dbarchive.biosciencedbc.jp/jp/knapsack-kampo/data-2.html) | [kampo_herb.txt](../../app/src/main/assets/licenses/sources/kampo_herb.txt) |

[CC BY-SA 2.1 Japan全文](../../app/src/main/assets/licenses/sources/cc-by-sa-2.1-jp.txt)。元の自動読みの全件の正確性は未確認です。取得ZIPのハッシュは出典・採用行対応ファイルに保持しています。

<a id="m"></a>
### M: 医療 / Medical

奈良先端科学技術大学院大学ソーシャル・コンピューティング研究室の万病辞書・百薬辞書。由来データと編集・読み補完部分はCC BY 4.0。原本に記された作成者の帰属、ライセンス、出典・加工表示を保持します。

| 入力 | 採用行数 | 版 | 出典 | 通知 |
| --- | ---: | --- | --- | --- |
| 万病辞書 | 72,156 | MANBYO_202106 | [万病辞書](https://sociocom.naist.jp/manbyou-dic/) | [manbyo.txt](../../app/src/main/assets/licenses/sources/manbyo.txt) |
| 百薬辞書 | 20,691 | HYAKUYAKU_202107 | [百薬辞書](https://sociocom.naist.jp/hyakuyaku-dic/) | [hyakuyaku.txt](../../app/src/main/assets/licenses/sources/hyakuyaku.txt) |

[CC BY 4.0全文](../../app/src/main/assets/licenses/sources/cc-by-4.0.txt)。空欄2,006件にはGeminiによる推定読みの補完があり、自動読み・推定読みの全件の正確性は未確認です。品詞・コストは配布元の割当です。

### 人物追加の出典

公式資料を照合した格闘家490組・492レコードの追加を含みます。各レコードの元URL・確認日・原資料ハッシュは出典・採用行対応ファイルの `personAdditions`、アプリの [人名詳細](../../app/src/main/assets/licenses/classified/person.html) に保持しています。氏名・読みを選定・正規化・バイナリ化していますが、原ページ本文を転載していません。各語の著作権条件は未確認です。

## その他の同梱辞書・共通資源

| 資源 | 出典・帰属と条件 | 詳細・原文 |
| --- | --- | --- |
| 基本・単漢字・絵文字・顔文字・記号辞書 | Google/Mozc由来。Google、NAIST/IPAdic、ICOT、沖縄辞書等の各通知を保持。コードのBSD条件だけを全辞書へ適用しない | [core.html](../../app/src/main/assets/licenses/core.html)、[MOZC通知](../../app/src/main/assets/licenses/sources/mozc.txt)、[IPADIC COPYING](../../app/src/main/assets/licenses/sources/ipadic-copying.txt)、[IPADIC NOTICE](../../app/src/main/assets/licenses/sources/ipadic.txt) |
| 接続コスト・品詞表・id.def・zero-query | Mozc関連資源。各データの通知を保持。元の個別行との網羅的な対応は未確認 | [MOZC通知](../../app/src/main/assets/licenses/sources/mozc.txt)、[merge-ut通知](../../app/src/main/assets/licenses/sources/merge-ut.txt) |
| 読み修正・ことわざ | 配布元に保持されたMozc関連通知。各語の原本・帰属との全件対応は未確認 | [auxiliary.html](../../app/src/main/assets/licenses/auxiliary.html)、[converter.txt](../../app/src/main/assets/licenses/sources/converter.txt) |
| 英語読み（5,138組） | JMdict: James William BREEN / EDRDG、CC BY-SA 4.0。NGSL 1.2: Charles Browne / Brent Culligan / Joseph Phillips、語選定・頻度調査。CMUdict: Copyright (C) 1993–2015 Carnegie Mellon University、原本の再配布条件・免責を保持。編集・追加部分はCC BY-SA 4.0で、CMUdictの条件を置き換えない | [english-reading.html](../../app/src/main/assets/licenses/english-reading.html)、[JMdict通知](../../app/src/main/assets/licenses/sources/jmdict.txt)、[CMUdict原文](../../app/src/main/assets/licenses/sources/cmudict.txt)、[配布元のデータ条件](../../app/src/main/assets/licenses/sources/release-data.txt) |
| N-gram / Unigram | JapaneseCorpus由来、生成物はCC BY-SA 4.0。日本語Wikipediaの帰属・継承、青空文庫の著作権フラグなしの作品と出典・書誌情報を保持。Mozcコンテキスト資源の原条件も保持 | [ngram.html](../../app/src/main/assets/licenses/ngram.html)、[data.txt](../../app/src/main/assets/licenses/sources/data.txt)、[corpus.txt](../../app/src/main/assets/licenses/sources/corpus.txt) |
| 独立英単語・glide辞書（english/） | 旧 [ENGLISH_DICTIOANRY_LICENSE](../../ENGLISH_DICTIOANRY_LICENSE) はSalesforce WikiText由来・CC BY-SA 4.0を宣言し、Merity / Xiong / Bradbury / Socherの論文を引用。ただし記載対象 `english.dat` と現行 `word.dat` 等との取得版・生成来歴の対応は未確認。宣言を削除せず、適用範囲の未確認を明示 | [english.html](../../app/src/main/assets/licenses/english.html)、[WikiText](https://huggingface.co/datasets/Salesforce/wikitext) |
| Mozc segmenter / node attribute | pre.3 ZIPに含まれない現行資源。ローカルmanifestはboundary.def / segmenter.def / id.def由来を記録。取得コミット・原本との全件対応は未確認 | [segmenter manifest](../../app/src/main/assets/mozc/segmenter_manifest.json)、[node attribute manifest](../../app/src/main/assets/mozc/node_attribute_manifest.json)、[MOZC通知](../../app/src/main/assets/licenses/sources/mozc.txt) |

## 保持した全通知・ライセンス全文

以下の全資料はリポジトリとアプリ内に保持しています。通知と法的条件を区別し、必要な帰属・免責も保持します。各通知のSHA-256・固定URLは [notice-snapshots.json](../../app/src/main/assets/licenses/notice-snapshots.json) に記録しています。URLが示す通知の取得版と、元辞書データの取得版は同一と断定しません。

| 資料 | リポジトリ内原文 | 取得元 |
| --- | --- | --- |
| `mozc` | [mozc.txt](../../app/src/main/assets/licenses/sources/mozc.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/resources/third-party/MOZC-LICENSE) |
| `cmudict` | [cmudict.txt](../../app/src/main/assets/licenses/sources/cmudict.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/resources/third-party/CMUDICT-LICENSE) |
| `ipadic-copying` | [ipadic-copying.txt](../../app/src/main/assets/licenses/sources/ipadic-copying.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/resources/third-party/IPADIC-COPYING) |
| `release-data` | [release-data.txt](../../app/src/main/assets/licenses/sources/release-data.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/LICENSE-DATA.md) |
| `ipadic` | [ipadic.txt](../../app/src/main/assets/licenses/sources/ipadic.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/resources/third-party/IPADIC-NOTICE) |
| `jmdict` | [jmdict.txt](../../app/src/main/assets/licenses/sources/jmdict.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/resources/third-party/JMDICT-LICENSE.html) |
| `data` | [data.txt](../../app/src/main/assets/licenses/sources/data.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/resources/third-party/LICENSE-DATA.md) |
| `corpus` | [corpus.txt](../../app/src/main/assets/licenses/sources/corpus.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/resources/third-party/NOTICE.md) |
| `converter` | [converter.txt](../../app/src/main/assets/licenses/sources/converter.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/resources/THIRD-PARTY-NOTICES.md) |
| `cc-by-sa-4` | [cc-by-sa-4.txt](../../app/src/main/assets/licenses/sources/cc-by-sa-4.txt) | [固定URL](https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt) |
| `apache-2` | [apache-2.txt](../../app/src/main/assets/licenses/sources/apache-2.txt) | [固定URL](https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `person` | [person.txt](../../app/src/main/assets/licenses/sources/person.txt) | [固定URL](https://raw.githubusercontent.com/utuhiro78/mozcdic-ut-personal-names/b557dd4c1548cc7c2164d5f8b14170c40cbb6f81/LICENSE) |
| `place` | [place.txt](../../app/src/main/assets/licenses/sources/place.txt) | [固定URL](https://raw.githubusercontent.com/utuhiro78/mozcdic-ut-place-names/98cf8e9d7731695a52e5bc72ed633933c71d1913/LICENSE) |
| `wiki` | [wiki.txt](../../app/src/main/assets/licenses/sources/wiki.txt) | [固定URL](https://raw.githubusercontent.com/utuhiro78/mozcdic-ut-jawiki/57af6adefedab262c295c4bfff71a89902cbe05c/LICENSE) |
| `neologd` | [neologd.txt](../../app/src/main/assets/licenses/sources/neologd.txt) | [固定URL](https://raw.githubusercontent.com/neologd/mecab-ipadic-neologd/abc61e33d8be3d0ead202e6b1df064c72d5ccf11/COPYING) |
| `merge-ut` | [merge-ut.txt](../../app/src/main/assets/licenses/sources/merge-ut.txt) | [固定URL](https://raw.githubusercontent.com/utuhiro78/merge-ut-dictionaries/6239884099ddc0c4d5bb1453b20441121d20593e/LICENSE) |
| `cc-by-4.0` | [cc-by-4.0.txt](../../app/src/main/assets/licenses/sources/cc-by-4.0.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/specialist/notices/CC-BY-4.0.txt) |
| `cc-by-sa-2.1-jp` | [cc-by-sa-2.1-jp.txt](../../app/src/main/assets/licenses/sources/cc-by-sa-2.1-jp.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/specialist/notices/CC-BY-SA-2.1-JP.txt) |
| `hyakuyaku` | [hyakuyaku.txt](../../app/src/main/assets/licenses/sources/hyakuyaku.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/specialist/notices/hyakuyaku.txt) |
| `kampo_core` | [kampo_core.txt](../../app/src/main/assets/licenses/sources/kampo_core.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/specialist/notices/kampo_core.txt) |
| `kampo_herb` | [kampo_herb.txt](../../app/src/main/assets/licenses/sources/kampo_herb.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/specialist/notices/kampo_herb.txt) |
| `manbyo` | [manbyo.txt](../../app/src/main/assets/licenses/sources/manbyo.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5/src/main/specialist/notices/manbyo.txt) |
| `distribution-licenses` | [distribution-licenses.txt](../../app/src/main/assets/licenses/sources/distribution-licenses.txt) | [固定URL](https://github.com/KazumaProject/kotlin-kana-kanji-converter/releases/download/v1.7.262-pre.3/DICTIONARY-LICENSES.txt) |

配布の全ライセンス文書 [DICTIONARY-LICENSES.txtの保持原文](../../app/src/main/assets/licenses/sources/distribution-licenses.txt) は公開添付と同じバイト列です。SHA-256は `7dababd4e8f080dd133216e64c7f64bca3052287dc0e47155067ee0732962a6a`。アプリのMITと第三者データの条件は区別してください。

## 再配布・更新時の表示

原文で要求される著作権、帰属、ライセンス本文、免責、出典URLと変更表示を保持します。CCの継承条件は該当する加工データに適用し、Apache/Mozc/CMUdict等の追加通知をCCの表示だけで代替しません。各入力の未確認事項は未確認のまま表示し、後から判明した原本の版・条件を記録してください。

```sh
python3 tools/classified-dictionaries/prepare.py --check
```

このコマンドで配布ファイル、同梱原文・通知、生成補正・期待レコードのハッシュを検証できます。配布版を変更する際は入力・来歴・同梱HTML・本対応表と通知スナップショットを一緒に更新してください。外部インポートした別辞書・ユーザー登録・任意の持ち込みデータの条件を、この同梱一覧で一律に与えることはできません。
