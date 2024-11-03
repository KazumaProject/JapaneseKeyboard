[![Android Release CI](https://github.com/KazumaProject/JapaneseKeyboard/actions/workflows/android.yml/badge.svg)](https://github.com/KazumaProject/JapaneseKeyboard/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](/LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/KazumaProject/JapaneseKeyboard)](https://github.com/KazumaProject/JapaneseKeyboard/releases)
![Gradle Version](https://img.shields.io/badge/gradle-8.2-blue)
![Min Sdk](https://img.shields.io/badge/minSdk-24-blue)
![Target Sdk](https://img.shields.io/badge/targetSdk-35-blue)

# スミレ - 完全オフラインの日本語キーボードアプリ

<p align="center">
<img src="images/demo.gif" width="auto" height="512px">
</p>

## ネットワーク権限不要なキーボードアプリ⌨️

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.kazumaproject.markdownhelperkeyboard&pli=1">
    <img src="https://cdn.rawgit.com/steverichey/google-play-badge-svg/master/img/fr_get.svg" width="25%">
  </a>
</p>

# 謝礼
このアプリは以下の書籍、論文、記事を参考にして作成しました。

- [日本語入力を支える技術　～変わり続けるコンピュータと言葉の世界 (WEB+DB PRESS plus)](https://www.amazon.co.jp/%E6%97%A5%E6%9C%AC%E8%AA%9E%E5%85%A5%E5%8A%9B%E3%82%92%E6%94%AF%E3%81%88%E3%82%8B%E6%8A%80%E8%A1%93-%EF%BD%9E%E5%A4%89%E3%82%8F%E3%82%8A%E7%B6%9A%E3%81%91%E3%82%8B%E3%82%B3%E3%83%B3%E3%83%94%E3%83%A5%E3%83%BC%E3%82%BF%E3%81%A8%E8%A8%80%E8%91%89%E3%81%AE%E4%B8%96%E7%95%8C-WEB-DB-PRESS-plus/dp/4774149934)

- [辞書と言語モデルの効率のよい圧縮とかな漢字変換への応用](https://www.anlp.jp/proceedings/annual_meeting/2011/pdf_dir/C4-2.pdf)

- [アスペ日記 簡潔データ構造 LOUDS の解説](https://takeda25.hatenablog.jp/entry/20120421/1335019644)

- [Trie木の純粋な実装とLOUDSのサイズ比較(Java)](https://zenn.dev/pakio/articles/eb864b89416637fe43fe)

- [Trie4J - various trie implementation for Java.](https://github.com/takawitter/trie4j)

- [AndroidX時代のIME作成 (1: 簡単なキーボードの作成)](https://qiita.com/Dooteeen/items/d32446be401096c75712)

### 辞書
- [mozc](https://github.com/google/mozc)

# スミレ - オフライン日本語キーボードアプリ
「スミレ」は、完全オフラインで使用でき、ネットワークの権限を一切必要としない日本語キーボードアプリです。プライバシーを守りながら、軽快かつ直感的な入力体験を提供します。

さらに、「スミレ」では、高品質な日本語入力を実現するために **mozc** の辞書を使用しています。mozcは、信頼性の高い辞書データを提供しており、豊富な語彙と正確な変換をサポートします。詳しくは[こちら](https://github.com/google/mozc)をご覧ください。

### 特長
- **完全オフライン対応**  
  ネット接続なしでどこでも使えるので、安心して利用可能です。

- **ネットワーク権限不要**  
  アプリ自体にネットワークの権限が必要ありません。外部との通信が一切ないため、データはすべて端末内で管理されます。

- **mozc辞書採用**  
  正確で豊富な変換候補を提供する mozc の辞書を使用しており、快適な日本語入力をサポートします。

- **プライバシー保護**  
  データは全て端末内に保存され、外部に送信されることはありません。あなたの情報は完全に安全です。

- **シンプルかつ洗練されたデザイン**  
  直感的な操作性と美しいデザインで、誰でもすぐに使いこなせます。

- **高速で快適な入力**  
  軽量設計で、スムーズで素早い入力を実現します。ストレスを感じることはありません。

- **多機能なサポート**  
  予測変換、フリック入力など、便利な機能が満載。日々の入力を効率化します。

「スミレ」は、シンプルかつパワフルな日本語入力を実現します。ネットワークの心配が不要な、完全オフラインの快適な入力体験を手に入れ、mozcの信頼性の高い辞書でさらに便利な入力を体感しましょう。さあ、今すぐ「スミレ」をダウンロードして、自由な入力を楽しんでください。

---

# Sumire - Offline Japanese Keyboard App

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.kazumaproject.markdownhelperkeyboard&pli=1">
    <img src="https://cdn.rawgit.com/steverichey/google-play-badge-svg/master/img/fr_get.svg" width="25%">
  </a>
</p>

"Sumire" is a Japanese keyboard app that operates entirely offline, requiring no network permissions whatsoever. It offers a smooth and intuitive typing experience while safeguarding your privacy.

Additionally, "Sumire" uses the **mozc** dictionary to ensure high-quality Japanese input. Mozc provides reliable dictionary data with extensive vocabulary and accurate conversions. For more information, visit [here](https://github.com/google/mozc).

### Features
- **Completely Offline**  
  You can use it anywhere without an internet connection, providing peace of mind.

- **No Network Permissions Required**  
  The app doesn't need network permissions, and since it doesn't communicate with any external sources, all your data is managed solely within your device.

- **Uses the mozc Dictionary**  
  By utilizing the mozc dictionary, it offers accurate and rich conversion suggestions, making Japanese typing more comfortable.

- **Privacy Protection**  
  All your data is stored on your device, with no information sent outside. Your privacy is fully protected.

- **Simple and Elegant Design**  
  With intuitive usability and a beautiful design, anyone can master it quickly.

- **Fast and Comfortable Input**  
  The app is lightweight, delivering smooth and rapid typing without any stress.

- **Feature-Rich Support**  
  Includes useful functions like predictive text and flick input, making everyday typing more efficient.

"Sumire" provides a simple yet powerful Japanese input experience. Enjoy worry-free, completely offline typing with the reliability of mozc's trusted dictionary. Download "Sumire" now and experience the freedom of seamless input!
