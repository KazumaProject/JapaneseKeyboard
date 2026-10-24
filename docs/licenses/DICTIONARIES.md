# 辞書のライセンス一覧 / Dictionary licenses

本書はv1.7.271で採用した全143カテゴリ、その他の同梱辞書、共通資源、保持した通知全文への対応表です。辞書名の意味から適用条件を推測せず、固定入力と採用レコード・分類結果の対応を根拠にしています。

配布: [`v1.7.271`](https://github.com/KazumaProject/kotlin-kana-kanji-converter/releases/tag/v1.7.271)、配布コミット `02320777884161421eca4b03f8d7ee22f29ee650`。全463配布ファイル・862,354カテゴリレコードの対象は [配布マニフェスト](../../tools/classified-dictionaries/distribution-manifest.json) を参照してください。

[出典・固定入力・採用行対応](../../tools/classified-dictionaries/category-provenance.json) にハッシュと出典別件数を記録しています。専門辞書は固定元データの行・元読み・元表記と採用来歴を照合しています。各カテゴリの3ファイルは `app/src/main/assets/categories/<ID>/{tango,yomi,token}.dat.zip` です。分類・正規化・重複処理・品詞/コスト割当・バイナリ変換を行っています。人名のかな番兵で失われる3表記は、固定入力から導出したサイドカーで復元します。

## 全143カテゴリ

W/N/P/J/K/Mは次節の出典を指します。WとNには共通Web入力の該当分を含み、併記は単独著作権者の断定や同じ語の二重計上を意味しません。共通品詞・接続情報にはMozcの通知も適用されます。

| ID | 辞書名 | レコード数 | 出典・適用条件の参照 | アプリで表示する詳細（同梱HTML） |
| --- | --- | ---: | --- | --- |
| `character` | 架空キャラクター | 6,848 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/character.html) |
| `mythological_figure` | 神話・伝承の存在 | 3,436 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/mythological_figure.html) |
| `place` | 地名 | 45,090 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/place.html) |
| `address` | 住所 | 152,874 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/address.html) |
| `station` | 駅・停留所 | 12,759 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/station.html) |
| `facility/religious/shrine` | 神社・神宮 | 3,824 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/religious/shrine.html) |
| `facility/religious/temple` | 寺院 | 4,005 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/religious/temple.html) |
| `facility/religious/church` | 教会・礼拝堂 | 368 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/religious/church.html) |
| `facility/religious/monastery` | 修道院・宗教共同体施設 | 22 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/religious/monastery.html) |
| `facility/education/school` | 学校 | 603 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/education/school.html) |
| `facility/education/preschool` | 保育・幼児教育施設 | 94 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/education/preschool.html) |
| `facility/education/university` | 大学・高等教育施設 | 63 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/education/university.html) |
| `facility/education/research` | 研究施設 | 80 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/education/research.html) |
| `facility/healthcare/hospital` | 病院 | 1,013 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/healthcare/hospital.html) |
| `facility/healthcare/clinic` | 診療所・専門医療施設 | 59 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/healthcare/clinic.html) |
| `facility/healthcare/care` | 介護・福祉施設 | 95 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/healthcare/care.html) |
| `facility/public/government` | 官公庁・行政窓口 | 541 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/public/government.html) |
| `facility/public/police_fire` | 警察・消防・防災 | 1,299 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/public/police_fire.html) |
| `facility/public/judicial` | 司法・矯正施設 | 137 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/public/judicial.html) |
| `facility/public/postal` | 郵便施設 | 1,626 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/public/postal.html) |
| `facility/cultural/museum` | 博物館・資料館 | 1,192 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/cultural/museum.html) |
| `facility/cultural/art_museum` | 美術館・ギャラリー | 377 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/cultural/art_museum.html) |
| `facility/cultural/library` | 図書館・文書館 | 750 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/facility/cultural/library.html) |
| `facility/cultural/memorial` | 記念館・文化会館 | 1,183 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/cultural/memorial.html) |
| `facility/entertainment/theater` | 劇場・演芸場 | 415 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/entertainment/theater.html) |
| `facility/entertainment/cinema` | 映画館 | 149 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/entertainment/cinema.html) |
| `facility/entertainment/live_venue` | ライブ・興行会場 | 146 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/entertainment/live_venue.html) |
| `facility/entertainment/amusement` | 遊園地・娯楽施設 | 140 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/entertainment/amusement.html) |
| `facility/sports/stadium` | 球場・競技場 | 624 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/sports/stadium.html) |
| `facility/sports/gymnasium` | 体育館・運動施設 | 450 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/facility/sports/gymnasium.html) |
| `facility/sports/golf` | ゴルフ場 | 47 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/sports/golf.html) |
| `facility/sports/ski` | スキー場・雪上競技施設 | 192 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/sports/ski.html) |
| `facility/sports/aquatic` | プール・水上スポーツ施設 | 11 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/facility/sports/aquatic.html) |
| `facility/commercial/shopping` | 商業施設・百貨店 | 1,291 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/commercial/shopping.html) |
| `facility/commercial/restaurant` | 飲食店 | 542 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/facility/commercial/restaurant.html) |
| `facility/commercial/market` | 市場・商店 | 158 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/commercial/market.html) |
| `facility/hospitality/hotel` | ホテル・宿泊施設 | 803 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/hospitality/hotel.html) |
| `facility/hospitality/inn` | 旅館・民宿 | 118 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/hospitality/inn.html) |
| `facility/hospitality/hot_spring` | 温泉・入浴施設 | 638 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/hospitality/hot_spring.html) |
| `facility/transport/airport` | 空港・航空施設 | 463 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/transport/airport.html) |
| `facility/transport/harbor` | 港湾・船舶施設 | 704 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/transport/harbor.html) |
| `facility/transport/railway_facility` | 鉄道設備 | 550 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/transport/railway_facility.html) |
| `facility/transport/road_facility` | 道路・自動車交通施設 | 1,638 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/facility/transport/road_facility.html) |
| `facility/transport/parking` | 駐車・車庫施設 | 6 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/transport/parking.html) |
| `facility/infrastructure/bridge` | 橋梁 | 1,469 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/infrastructure/bridge.html) |
| `facility/infrastructure/tunnel` | トンネル | 371 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/infrastructure/tunnel.html) |
| `facility/infrastructure/dam` | ダム・堰 | 93 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/infrastructure/dam.html) |
| `facility/infrastructure/water_control` | 水門・水路・治水設備 | 107 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/facility/infrastructure/water_control.html) |
| `facility/industrial/factory` | 工場・製造施設 | 391 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/industrial/factory.html) |
| `facility/industrial/power` | 発電・エネルギー施設 | 432 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/industrial/power.html) |
| `facility/industrial/logistics` | 物流・倉庫施設 | 97 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/facility/industrial/logistics.html) |
| `facility/industrial/communications` | 通信設備 | 517 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/industrial/communications.html) |
| `facility/recreation/park_garden` | 公園・庭園 | 1,567 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/recreation/park_garden.html) |
| `facility/recreation/zoo_aquarium` | 動物園・水族館 | 155 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/facility/recreation/zoo_aquarium.html) |
| `facility/recreation/outdoor_nature` | 自然体験・レジャー施設 | 236 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/recreation/outdoor_nature.html) |
| `facility/heritage/castle` | 城郭・宮殿 | 1,528 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/facility/heritage/castle.html) |
| `facility/heritage/archaeological_site` | 古墳・遺跡 | 232 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/heritage/archaeological_site.html) |
| `facility/heritage/historic_residence` | 歴史的住宅・邸宅 | 504 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/heritage/historic_residence.html) |
| `facility/residential/housing_complex` | 団地・住宅群 | 120 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/residential/housing_complex.html) |
| `facility/residential/condominium` | マンション・集合住宅 | 409 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/residential/condominium.html) |
| `facility/residential/mixed_use_building` | 複合建築物 | 90 | [W](#w) / [N](#n) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/facility/residential/mixed_use_building.html) |
| `facility/military/base` | 基地・駐屯地 | 348 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/facility/military/base.html) |
| `facility/military/garrison` | 兵営・防衛拠点 | 16 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/military/garrison.html) |
| `facility/military/training_ground` | 演習・訓練施設 | 40 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/facility/military/training_ground.html) |
| `transport_route` | 交通路線 | 4,230 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/transport_route.html) |
| `organization` | 組織 | 47,509 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/organization.html) |
| `lineage` | 家系・氏族 | 2,019 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/lineage.html) |
| `product` | 商品・サービス | 10,125 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/product.html) |
| `vehicle` | 車両・船舶 | 2,580 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/vehicle.html) |
| `event/occasion/organized_event` | 催し・行事 | 4,059 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/event/occasion/organized_event.html) |
| `event/occasion/observance` | 記念日・啓発日 | 802 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/event/occasion/observance.html) |
| `event/occasion/exam_qualification` | 試験・検定 | 169 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/event/occasion/exam_qualification.html) |
| `event/incident/natural_disaster` | 自然災害 | 456 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/event/incident/natural_disaster.html) |
| `event/incident/social_issue_litigation` | 社会問題・騒動・訴訟 | 641 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/event/incident/social_issue_litigation.html) |
| `event/history/conflict` | 歴史的紛争・政変 | 1,534 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/event/history/conflict.html) |
| `award` | 賞 | 1,163 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/award.html) |
| `agreement` | 条約・協定 | 424 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/agreement.html) |
| `initiative` | 事業・キャンペーン | 1,061 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/initiative.html) |
| `term/general/products_daily_goods` | 商品・日用品（一般名称） | 1,159 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/term/general/products_daily_goods.html) |
| `term/general/food_drink_cooking` | 食品・飲料・料理 | 4,077 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/term/general/food_drink_cooking.html) |
| `term/general/internet_slang_culture` | ネットスラング・ネット文化 | 574 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/term/general/internet_slang_culture.html) |
| `term/general/neologisms_trends_youth_language` | 新語・流行語・若者言葉 | 485 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/term/general/neologisms_trends_youth_language.html) |
| `term/general/other_general` | その他の一般語 | 20,502 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/term/general/other_general.html) |
| `term/specialist/information_communications` | 情報・通信 | 6,082 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/term/specialist/information_communications.html) |
| `term/specialist/mathematics_physics` | 数学・物理 | 4,171 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/term/specialist/mathematics_physics.html) |
| `term/specialist/chemistry_life_sciences` | 化学・生命科学 | 27,566 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/term/specialist/chemistry_life_sciences.html) |
| `term/specialist/engineering_technology` | 工学・技術 | 3,509 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/term/specialist/engineering_technology.html) |
| `term/specialist/earth_environment_resources` | 地球・環境・資源 | 2,110 | [W](#w) / [N](#n) | [詳細](../../app/src/main/assets/licenses/classified/term/specialist/earth_environment_resources.html) |
| `term/specialist/economics_management` | 経済・経営 | 5,779 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/term/specialist/economics_management.html) |
| `term/specialist/law_politics` | 法律・政治 | 5,274 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/term/specialist/law_politics.html) |
| `term/specialist/humanities_social_sciences` | 人文・社会科学 | 17,877 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/term/specialist/humanities_social_sciences.html) |
| `term/specialist/culture_arts_sports` | 文化・芸術・スポーツ | 5,360 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/term/specialist/culture_arts_sports.html) |
| `term/specialist/transport_safety_defense` | 交通・安全・防衛 | 4,037 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/term/specialist/transport_safety_defense.html) |
| `work_franchise` | 作品シリーズ | 849 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/work_franchise.html) |
| `work_manga` | 漫画作品 | 5,082 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/work_manga.html) |
| `work_anime` | アニメ作品 | 1,088 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/work_anime.html) |
| `media_demographic/audience/shonen` | 少年向け | 4,119 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/media_demographic/audience/shonen.html) |
| `media_demographic/audience/seinen` | 青年向け | 1,731 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/media_demographic/audience/seinen.html) |
| `media_demographic/audience/shojo` | 少女向け | 826 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/media_demographic/audience/shojo.html) |
| `media_demographic/audience/josei` | 女性向け（Josei） | 355 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/media_demographic/audience/josei.html) |
| `media_demographic/audience/kids` | 子ども向け（Kids） | 549 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/media_demographic/audience/kids.html) |
| `media_demographic/audience/mixed` | 複数対象層 | 0 | 0件（配布カテゴリのみ） | [詳細](../../app/src/main/assets/licenses/classified/media_demographic/audience/mixed.html) |
| `media_demographic/audience/unspecified` | 対象層の指定なし・一般向け | 5,099 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/media_demographic/audience/unspecified.html) |
| `media_demographic/resolution/source_unverified` | 出典未確認・対象層未確定 | 0 | 0件（配布カテゴリのみ） | [詳細](../../app/src/main/assets/licenses/classified/media_demographic/resolution/source_unverified.html) |
| `work_film` | 映画作品 | 6,504 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/work_film.html) |
| `work_program` | 番組 | 9,309 | [W](#w) / [N](#n) / [P](#p) / [J](#j) | [詳細](../../app/src/main/assets/licenses/classified/work_program.html) |
| `work_book` | 書籍作品 | 7,659 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/work_book.html) |
| `work_game` | ゲーム作品 | 3,158 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/work_game.html) |
| `work_music` | 音楽作品 | 10,337 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/work_music.html) |
| `work_stage` | 舞台作品 | 571 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/work_stage.html) |
| `work_periodical` | 定期刊行物 | 1,811 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/work_periodical.html) |
| `work_other` | 媒体未特定の作品・美術作品 | 2,683 | [W](#w) / [N](#n) / [P](#p) | [詳細](../../app/src/main/assets/licenses/classified/work_other.html) |
| `kampo` | 漢方・生薬 | 434 | [K](#k) + [追加出典](#漢方追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/kampo.html) |
| `medical` | 医療 | 92,683 | [M](#m) | [詳細](../../app/src/main/assets/licenses/classified/medical.html) |
| `person/surname_origin_unconfirmed` | 姓・名字（由来未確認） | 24,182 | [W](#w) / [N](#n) / [P](#p) / [J](#j) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/surname_origin_unconfirmed.html) |
| `person/given_name_origin_unconfirmed` | 名・下の名前（由来未確認） | 4,332 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/given_name_origin_unconfirmed.html) |
| `person/personal_name_origin_unconfirmed` | 人名・姓名（由来未確認） | 93,650 | [W](#w) / [N](#n) / [P](#p) / [J](#j) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/personal_name_origin_unconfirmed.html) |
| `person/actor` | 俳優 | 6,428 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/actor.html) |
| `person/voice_actor` | 声優 | 579 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/voice_actor.html) |
| `person/musician` | 歌手・音楽家 | 971 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/musician.html) |
| `person/idol` | アイドル | 154 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/idol.html) |
| `person/comedian` | お笑い芸人 | 1,749 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/comedian.html) |
| `person/broadcaster` | タレント・モデル・放送・報道 | 1,307 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/broadcaster.html) |
| `person/online_creator` | 配信者・動画投稿者 | 124 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/online_creator.html) |
| `person/sports` | スポーツ関係者 | 6,571 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/sports.html) |
| `person/competitive_games` | 棋士・競技ゲーム選手 | 103 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/competitive_games.html) |
| `person/writer` | 作家・文筆家 | 1,134 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/writer.html) |
| `person/manga_illustration` | 漫画家・イラストレーター | 215 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/manga_illustration.html) |
| `person/film_game_creator` | 映画・映像・ゲーム制作者 | 200 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/film_game_creator.html) |
| `person/art_design` | 美術・写真・建築・デザイン | 922 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/art_design.html) |
| `person/performing_arts` | 舞台・伝統芸能 | 295 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/performing_arts.html) |
| `person/science_education` | 研究・技術・教育 | 7,585 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/science_education.html) |
| `person/politics_public` | 政治・行政・外交・社会運動 | 1,553 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/politics_public.html) |
| `person/business` | 実業・経営 | 4,019 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/business.html) |
| `person/royalty_nobility` | 皇室・王室・貴族 | 286 | [W](#w) / [N](#n) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/royalty_nobility.html) |
| `person/other_identified` | その他の特定人物 | 5,020 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/other_identified.html) |
| `person/thought` | 哲学者・思想家 | 255 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/thought.html) |
| `person/surname_origin_japanese` | 姓・名字（日本由来） | 25,012 | [W](#w) / [N](#n) / [P](#p) / [J](#j) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/surname_origin_japanese.html) |
| `person/surname_origin_foreign` | 姓・名字（海外由来） | 57 | [W](#w) / [N](#n) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/surname_origin_foreign.html) |
| `person/given_name_origin_japanese` | 名・下の名前（日本由来） | 2,286 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/given_name_origin_japanese.html) |
| `person/given_name_origin_foreign` | 名・下の名前（海外由来） | 47 | [W](#w) / [N](#n) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/given_name_origin_foreign.html) |
| `person/personal_name_origin_japanese` | 人名・姓名（日本由来） | 75,946 | [W](#w) / [N](#n) / [P](#p) / [J](#j) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/personal_name_origin_japanese.html) |
| `person/personal_name_origin_foreign` | 人名・姓名（海外由来） | 1,046 | [W](#w) / [N](#n) / [P](#p) + [人物追加](#人物追加の出典) | [詳細](../../app/src/main/assets/licenses/classified/person/personal_name_origin_foreign.html) |

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

[CC BY 4.0全文](../../app/src/main/assets/licenses/sources/cc-by-4.0.txt)。空欄2,003件にはGeminiによる推定読みの補完があり、自動読み・推定読みの全件の正確性は未確認です。品詞・コストは配布元の割当です。

### 人物追加の出典

人物の追加出典は、[出典・採用行対応](../../tools/classified-dictionaries/category-provenance.json) の `personAdditions` と各人物カテゴリの同梱HTMLに保持しています。採用された読み・表記に対応するURLを掲載し、原資料本文は転載しません。氏名・読みの選定・正規化・バイナリ化を行い、各語の著作権条件は未確認です。

### 漢方追加の出典

ツムラの公開製品項目から読みと名称を照合した2語を含みます。固定入力のハッシュと照合項目は `collectedTerms`、元URLは [漢方の詳細](../../app/src/main/assets/licenses/classified/kampo.html) に保持しています。原資料本文は転載せず、各語の著作権条件は未確認です。

## その他の同梱辞書・共通資源

| 資源 | 出典・帰属と条件 | 詳細・原文 |
| --- | --- | --- |
| 基本・単漢字・絵文字・顔文字・記号辞書 | Google/Mozc由来。Google、NAIST/IPAdic、ICOT、沖縄辞書等の各通知を保持。コードのBSD条件だけを全辞書へ適用しない | [core.html](../../app/src/main/assets/licenses/core.html)、[MOZC通知](../../app/src/main/assets/licenses/sources/mozc.txt)、[IPADIC COPYING](../../app/src/main/assets/licenses/sources/ipadic-copying.txt)、[IPADIC NOTICE](../../app/src/main/assets/licenses/sources/ipadic.txt) |
| 接続コスト・品詞表・id.def・zero-query | Mozc関連資源。各データの通知を保持。元の個別行との網羅的な対応は未確認 | [MOZC通知](../../app/src/main/assets/licenses/sources/mozc.txt)、[merge-ut通知](../../app/src/main/assets/licenses/sources/merge-ut.txt) |
| 読み修正・ことわざ | 配布元に保持されたMozc関連通知。各語の原本・帰属との全件対応は未確認 | [auxiliary.html](../../app/src/main/assets/licenses/auxiliary.html)、[converter.txt](../../app/src/main/assets/licenses/sources/converter.txt) |
| 英語読み（5,138組） | JMdict: James William BREEN / EDRDG、CC BY-SA 4.0。NGSL 1.2: Charles Browne / Brent Culligan / Joseph Phillips、語選定・頻度調査。CMUdict: Copyright (C) 1993–2015 Carnegie Mellon University、原本の再配布条件・免責を保持。編集・追加部分はCC BY-SA 4.0で、CMUdictの条件を置き換えない | [english-reading.html](../../app/src/main/assets/licenses/english-reading.html)、[JMdict通知](../../app/src/main/assets/licenses/sources/jmdict.txt)、[CMUdict原文](../../app/src/main/assets/licenses/sources/cmudict.txt)、[配布元のデータ条件](../../app/src/main/assets/licenses/sources/release-data.txt) |
| N-gram / Unigram | JapaneseCorpus由来、生成物はCC BY-SA 4.0。日本語Wikipediaの帰属・継承、青空文庫の著作権フラグなしの作品と出典・書誌情報を保持。Mozcコンテキスト資源の原条件も保持 | [ngram.html](../../app/src/main/assets/licenses/ngram.html)、[data.txt](../../app/src/main/assets/licenses/sources/data.txt)、[corpus.txt](../../app/src/main/assets/licenses/sources/corpus.txt) |
| 独立英単語・glide辞書（english/） | 旧 [ENGLISH_DICTIOANRY_LICENSE](../../ENGLISH_DICTIOANRY_LICENSE) はSalesforce WikiText由来・CC BY-SA 4.0を宣言し、Merity / Xiong / Bradbury / Socherの論文を引用。ただし記載対象 `english.dat` と現行 `word.dat` 等との取得版・生成来歴の対応は未確認。宣言を削除せず、適用範囲の未確認を明示 | [english.html](../../app/src/main/assets/licenses/english.html)、[WikiText](https://huggingface.co/datasets/Salesforce/wikitext) |
| Mozc segmenter / node attribute | 配布ZIPに含まれない現行資源。ローカルmanifestはboundary.def / segmenter.def / id.def由来を記録。取得コミット・原本との全件対応は未確認 | [segmenter manifest](../../app/src/main/assets/mozc/segmenter_manifest.json)、[node attribute manifest](../../app/src/main/assets/mozc/node_attribute_manifest.json)、[MOZC通知](../../app/src/main/assets/licenses/sources/mozc.txt) |

## 保持した全通知・ライセンス全文

以下の全資料はリポジトリとアプリ内に保持しています。通知と法的条件を区別し、必要な帰属・免責も保持します。各通知のSHA-256・固定URLは [notice-snapshots.json](../../app/src/main/assets/licenses/notice-snapshots.json) に記録しています。URLが示す通知の取得版と、元辞書データの取得版は同一と断定しません。

| 資料 | リポジトリ内原文 | 取得元 |
| --- | --- | --- |
| `mozc` | [mozc.txt](../../app/src/main/assets/licenses/sources/mozc.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/resources/third-party/MOZC-LICENSE) |
| `cmudict` | [cmudict.txt](../../app/src/main/assets/licenses/sources/cmudict.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/resources/third-party/CMUDICT-LICENSE) |
| `ipadic-copying` | [ipadic-copying.txt](../../app/src/main/assets/licenses/sources/ipadic-copying.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/resources/third-party/IPADIC-COPYING) |
| `release-data` | [release-data.txt](../../app/src/main/assets/licenses/sources/release-data.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/LICENSE-DATA.md) |
| `ipadic` | [ipadic.txt](../../app/src/main/assets/licenses/sources/ipadic.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/resources/third-party/IPADIC-NOTICE) |
| `jmdict` | [jmdict.txt](../../app/src/main/assets/licenses/sources/jmdict.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/resources/third-party/JMDICT-LICENSE.html) |
| `data` | [data.txt](../../app/src/main/assets/licenses/sources/data.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/resources/third-party/LICENSE-DATA.md) |
| `corpus` | [corpus.txt](../../app/src/main/assets/licenses/sources/corpus.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/resources/third-party/NOTICE.md) |
| `converter` | [converter.txt](../../app/src/main/assets/licenses/sources/converter.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/resources/THIRD-PARTY-NOTICES.md) |
| `cc-by-sa-4` | [cc-by-sa-4.txt](../../app/src/main/assets/licenses/sources/cc-by-sa-4.txt) | [固定URL](https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt) |
| `apache-2` | [apache-2.txt](../../app/src/main/assets/licenses/sources/apache-2.txt) | [固定URL](https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `person` | [person.txt](../../app/src/main/assets/licenses/sources/person.txt) | [固定URL](https://raw.githubusercontent.com/utuhiro78/mozcdic-ut-personal-names/b557dd4c1548cc7c2164d5f8b14170c40cbb6f81/LICENSE) |
| `place` | [place.txt](../../app/src/main/assets/licenses/sources/place.txt) | [固定URL](https://raw.githubusercontent.com/utuhiro78/mozcdic-ut-place-names/98cf8e9d7731695a52e5bc72ed633933c71d1913/LICENSE) |
| `wiki` | [wiki.txt](../../app/src/main/assets/licenses/sources/wiki.txt) | [固定URL](https://raw.githubusercontent.com/utuhiro78/mozcdic-ut-jawiki/57af6adefedab262c295c4bfff71a89902cbe05c/LICENSE) |
| `neologd` | [neologd.txt](../../app/src/main/assets/licenses/sources/neologd.txt) | [固定URL](https://raw.githubusercontent.com/neologd/mecab-ipadic-neologd/abc61e33d8be3d0ead202e6b1df064c72d5ccf11/COPYING) |
| `merge-ut` | [merge-ut.txt](../../app/src/main/assets/licenses/sources/merge-ut.txt) | [固定URL](https://raw.githubusercontent.com/utuhiro78/merge-ut-dictionaries/6239884099ddc0c4d5bb1453b20441121d20593e/LICENSE) |
| `cc-by-4.0` | [cc-by-4.0.txt](../../app/src/main/assets/licenses/sources/cc-by-4.0.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/specialist/notices/CC-BY-4.0.txt) |
| `cc-by-sa-2.1-jp` | [cc-by-sa-2.1-jp.txt](../../app/src/main/assets/licenses/sources/cc-by-sa-2.1-jp.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/specialist/notices/CC-BY-SA-2.1-JP.txt) |
| `hyakuyaku` | [hyakuyaku.txt](../../app/src/main/assets/licenses/sources/hyakuyaku.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/specialist/notices/hyakuyaku.txt) |
| `kampo_core` | [kampo_core.txt](../../app/src/main/assets/licenses/sources/kampo_core.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/specialist/notices/kampo_core.txt) |
| `kampo_herb` | [kampo_herb.txt](../../app/src/main/assets/licenses/sources/kampo_herb.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/specialist/notices/kampo_herb.txt) |
| `manbyo` | [manbyo.txt](../../app/src/main/assets/licenses/sources/manbyo.txt) | [固定URL](https://raw.githubusercontent.com/KazumaProject/kotlin-kana-kanji-converter/02320777884161421eca4b03f8d7ee22f29ee650/src/main/specialist/notices/manbyo.txt) |
| `distribution-licenses` | [distribution-licenses.txt](../../app/src/main/assets/licenses/sources/distribution-licenses.txt) | [固定URL](https://github.com/KazumaProject/kotlin-kana-kanji-converter/releases/download/v1.7.271/DICTIONARY-LICENSES.txt) |

配布の全ライセンス文書 [DICTIONARY-LICENSES.txtの保持原文](../../app/src/main/assets/licenses/sources/distribution-licenses.txt) は公開添付と同じバイト列です。SHA-256は `7dababd4e8f080dd133216e64c7f64bca3052287dc0e47155067ee0732962a6a`。アプリのMITと第三者データの条件は区別してください。

## 再配布・更新時の表示

原文で要求される著作権、帰属、ライセンス本文、免責、出典URLと変更表示を保持します。CCの継承条件は該当する加工データに適用し、Apache/Mozc/CMUdict等の追加通知をCCの表示だけで代替しません。各入力の未確認事項は未確認のまま表示し、後から判明した原本の版・条件を記録してください。

```sh
python3 tools/classified-dictionaries/prepare.py --check
```

このコマンドで配布ファイル、同梱原文・通知、生成補正・期待レコードのハッシュを検証できます。配布版を変更する際は入力・来歴・同梱HTML・本対応表と通知スナップショットを一緒に更新してください。外部インポートした別辞書・ユーザー登録・任意の持ち込みデータの条件を、この同梱一覧で一律に与えることはできません。
