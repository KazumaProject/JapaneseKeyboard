# upstream の短い語の学習による変換順位への影響

調査日: 2026-11-10

追加検査: [「ちょう→徴」と指定5入力の結果](upstream-chou-learning-2026-11-10.md)。一般的な候補が上位4件から外れる条件を確認したが、元の先頭候補の完全脱落は確認していない。

## 結論

zenz を含めない通常変換コアで、短い読みの学習が別の一般表現の順位にも影響することを確認した。ただし、今回の公開可能な仮の例では「本来の候補が全く出なくなる」という強さの現象までは再現していない。実際に問題が起きた非公開の語との同一性は断定しない。

確認した upstream:

- dev: `e0df25028948605d80640d6c42ccd816b9e943b6`（versionName 1.7.147）
- main: `427baa8ef19a5917ea931d6731ec9d1fd95289d7`（versionName 1.7.140）

実行検査は dev のソースを使用した。main については、学習処理一式、LearnRepository、GraphBuilder、FindPath、および検査に使用したシステム辞書・接続行列・Mozc 文節データ・N-gram 資産が dev と同一であることを Git の差分で確認した。アプリ全体が同一という意味ではない。

## 実行検査

upstream の実ソース 68 ファイルと同梱辞書を JVM 上でコンパイルし、GraphBuilder → FindPath を実行した。N-best=4、beamWidth=20、ユーザー辞書・追加分類辞書・修飾キー省略・フリック補正なし。zenz/Gemma はリンクも実行もしていない。文節処理あり／なし、システム N-gram あり／なしを比較した。

学習テーブルへの保存先はインメモリ DAO に置き換え、LearnRepository、LearnDao の upsert 本体、ConversionLearningSession、LearningScorePolicy は upstream の実装を使用した。Android UI、最終候補の追加・統合、端末上の入力操作は実行していない。

次表は `けい → 慧` の学習スコアが最小値 0 に達した状態と、未学習状態の比較。元のシステム辞書にある「慧」のコストは 4525、左右 ID は 1922/1922。

| 読み | 未学習の先頭 | 学習スコア 0・文節処理なし | 学習スコア 0・文節処理あり |
| --- | --- | --- | --- |
| けいさん | 計算 | 慧さん（計算は2位に残る） | 計算（慧さんは2位） |
| けいかいたいせい | 警戒態勢 | 警戒態勢 | 警戒態勢 |
| かんけいない | 関係ない | 関係ない | 関係ない |

この3入力では N-gram の ON/OFF で上記結論は変わらなかった。文節処理なしの「けいさん」のコストは、未学習の計算が4747、学習後の慧さんが3951だった。

追加した公開例では「計算が早い」が「慧さんが早い」「慧さんが速い」に押されて3位に、「計算が正しい」が「慧さんが正しい」に押されて2位になった。別の読み8組の一般語33件、および「けい」の一般表現10件をそれぞれ文節処理あり／なしで比較したが、追加検査の86条件で未学習時の先頭候補が学習後の上位4件から完全に消える結果はなかった。

### 回数に関する制約

これは「実際の画面で2～3回選択・確定した」端末テストではなく、最小学習コストでの変換コア検査である。基礎プローブはシステム辞書配列中の位置59を LearningFragment の candidateIndex に渡して0を生成しているが、59は画面で観測した表示順位ではない。したがって、その step 番号を実操作で必要な確定回数と解釈してはいけない。

別途、スコア計算自体については、候補コスト4525・仮の表示インデックス4の条件で、学習コストが2525 → 1025 → 0になることを実行確認した。インデックス10なら初回から0になる。後述の先頭候補学習設定により、すべての確定が再学習対象になるとは限らない。

## コードで確認した仕組み

1. **低い順位から選択するほど強く優遇される。** 初回コストは `candidateScore - 500 × candidateIndex`、下限0。再学習は既存値と新しい値の小さい方から1500を引き、下限0にする。[LearningScorePolicy](https://github.com/KazumaProject/JapaneseKeyboard/blob/e0df25028948605d80640d6c42ccd816b9e943b6/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/learning/database/LearningScorePolicy.kt#L6-L23)、[LearnDao](https://github.com/KazumaProject/JapaneseKeyboard/blob/e0df25028948605d80640d6c42ccd816b9e943b6/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/learning/database/LearnDao.kt#L107-L118)
2. **学習は完全一致の入力だけに限定されない。** 読みの各開始位置で学習語の共通接頭辞を探し、同じコストの単語ノードとして組み込む。「けいさん」の先頭にも「かんけいない」の途中にも「けい→慧」を適用できる。長い一般語の内部では減衰させる、といった保護はこの処理にない。[GraphBuilder](https://github.com/KazumaProject/JapaneseKeyboard/blob/e0df25028948605d80640d6c42ccd816b9e943b6/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/graph/GraphBuilder.kt#L599-L664)、[LearnRepository](https://github.com/KazumaProject/JapaneseKeyboard/blob/e0df25028948605d80640d6c42ccd816b9e943b6/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/repository/LearnRepository.kt#L101-L108)
3. **候補数の制限が順位変化を増幅し得る。** 経路探索には beam と N-best による打ち切りがある。学習語を含む複数の経路が上位に入れば、元の経路が返らなくなる可能性はある。ただし今回の元の先頭候補について、その完全脱落は実測できていない。またエンジンは N-best の後に辞書候補などを追加するため、N-best からの脱落と画面の全候補からの消失は同義ではない。[FindPath](https://github.com/KazumaProject/JapaneseKeyboard/blob/e0df25028948605d80640d6c42ccd816b9e943b6/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/path_algorithm/FindPath.kt#L1298-L1325)、[候補追加処理](https://github.com/KazumaProject/JapaneseKeyboard/blob/e0df25028948605d80640d6c42ccd816b9e943b6/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/KanaKanjiEngine.kt#L3363-L3375)
4. **候補を切り替えるだけでは学習は保存されない。** 確定後に学習セッションを保存する。先頭候補の学習は既定OFFであり、先頭に上がった候補を確定し続けるだけで必ず強化されるわけではない。[ConversionLearningSession](https://github.com/KazumaProject/JapaneseKeyboard/blob/e0df25028948605d80640d6c42ccd816b9e943b6/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/learning/session/ConversionLearningSession.kt#L33-L82)

単独の「けい→慧」の確定を学習する通常処理では、「けいさん」等の誤った読みで保存される必要はない。学習 DB の内容が壊れなくても、短い語の学習コストを別の入力の部分経路へ再利用することで今回の順位変化が生じる。

## 今後の修正を検討する場合

候補の表示順位に比例する無制限な優遇を抑えること、および単独語の学習と長い一般表現への部分適用で優遇の強さを分けることが検討対象になる。学習候補を無条件に取り除くと人名や複合語の学習が損なわれるため、正しい再学習の効果も残ることを回帰条件にする必要がある。

今回、製品コードの修正、学習データの削除、APKの作成、端末へのインストール、issue投稿、push は行っていない。

## ローカルの検査資料

作業ツリーの `build/kei-investigation/` に、`Probe.kt`、`compile_probe.py`、`sources.txt`、`results.txt`、コンパイルログおよび upstream ソースの展開結果を保存した。これらは既存の build 除外設定により Git 管理外。`results.txt` の `RESULT` は最小コスト状態の比較、`SCORE_MODEL` は仮の表示順位でのコスト推移、`STRESS` は追加の公開語による比較を表す。
