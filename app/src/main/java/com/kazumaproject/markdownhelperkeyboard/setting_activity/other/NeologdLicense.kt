package com.kazumaproject.markdownhelperkeyboard.setting_activity.other

import android.content.Context
import de.psdev.licensesdialog.licenses.License

class NeologdLicense : License() {
    override fun getName(): String {
        return "mecab-ipadic-neologd"
    }

    override fun readSummaryTextFromResources(context: Context?): String {
        return "Copyright (C) 2015-2019 Toshinori Sato (@overlast)\n" +
                "\n" +
                "      https://github.com/neologd/mecab-ipadic-neologd\n" +
                "\n" +
                "    i. 本データは、株式会社はてなが提供するはてなキーワード一覧ファイル\n" +
                "       中の表記、及び、読み仮名の大半を使用している。\n" +
                "\n" +
                "       はてなキーワード一覧ファイルの著作権は、株式会社はてなにある。\n" +
                "\n" +
                "       はてなキーワード一覧ファイルの使用条件に基づき、また、\n" +
                "       データ使用の許可を頂いたことに対する感謝の意を込めて、\n" +
                "       以下に株式会社はてなおよびはてなキーワードへの参照をURLで示す。\n" +
                "\n" +
                "       株式会社はてな : http://hatenacorp.jp/information/outline\n" +
                "\n" +
                "       はてなキーワード :\n" +
                "       http://developer.hatena.ne.jp/ja/documents/keyword/misc/catalog\n" +
                "\n" +
                "   ii. 本データは、日本郵便株式会社が提供する郵便番号データ中の表記、\n" +
                "       及び、読み仮名を使用している。\n" +
                "\n" +
                "       日本郵便株式会社は、郵便番号データに限っては著作権を主張しないと\n" +
                "       述べている。\n" +
                "\n" +
                "       日本郵便株式会社の郵便番号データに対する感謝の意を込めて、\n" +
                "       以下に日本郵便株式会社および郵便番号データへの参照をURLで示す。\n" +
                "\n" +
                "       日本郵便株式会社 :\n" +
                "         http://www.post.japanpost.jp/about/profile.html\n" +
                "\n" +
                "       郵便番号データ :\n" +
                "         http://www.post.japanpost.jp/zipcode/dl/readme.html\n" +
                "\n" +
                "  iii. 本データは、スナフキん氏が提供する日本全国駅名一覧中の表記、及び\n" +
                "       読み仮名を使用している。\n" +
                "\n" +
                "       日本全国駅名一覧の著作権は、スナフキん氏にある。\n" +
                "\n" +
                "       スナフキん氏は 「このデータを利用されるのは自由ですが、その際に\n" +
                "       不利益を被ったりした場合でも、スナフキんは一切責任は負えません\n" +
                "       ことをご承知おき下さい」と述べている。\n" +
                "\n" +
                "       スナフキん氏に対する感謝の意を込めて、\n" +
                "       以下に日本全国駅名一覧のコーナーへの参照をURLで示す。\n" +
                "\n" +
                "       日本全国駅名一覧のコーナー :\n" +
                "         http://www5a.biglobe.ne.jp/~harako/data/station.htm\n" +
                "\n" +
                "   iv. 本データは、工藤拓氏が提供する人名(姓/名)エントリデータ中の、\n" +
                "       漢字表記の姓・名とそれに対応する読み仮名を使用している。\n" +
                "\n" +
                "       人名(姓/名)エントリデータは被災者・安否不明者の人名の\n" +
                "       表記揺れ対策として、Mozcの人名辞書を活用できるという\n" +
                "       工藤氏の考えによって提供されている。\n" +
                "\n" +
                "       工藤氏に対する感謝の意を込めて、\n" +
                "       以下にデータ本体と経緯が分かる情報への参照をURLで示す。\n" +
                "\n" +
                "       人名(姓/名)エントリデータ :\n" +
                "         http://chasen.org/~taku/software/misc/personal_name.zip\n" +
                "\n" +
                "       上記データが提供されることになった経緯\n" +
                "         http://togetter.com/li/111529\n" +
                "\n" +
                "    v. 本データは、Web上からクロールした大量の文書データから抽出した\n" +
                "       表記とそれに対応する読み仮名のデータを含んでいる。\n" +
                "\n" +
                "       抽出した表記とそれに対応する読み仮名の組は、上記の i. から iv.\n" +
                "       の言語資源の組み合わせによって得られる組のみを採録した。\n" +
                "\n" +
                "       Web 上に文書データを公開して下さっている皆様に感謝いたします。\n" +
                "\n" +
                "Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);\n" +
                "you may not use this file except in compliance with the License.\n" +
                "You may obtain a copy of the License at\n" +
                "\n" +
                "      http://www.apache.org/licenses/LICENSE-2.0\n" +
                "\n" +
                "Unless required by applicable law or agreed to in writing, software\n" +
                "distributed under the License is distributed on an &quot;AS IS&quot; BASIS,\n" +
                "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                "See the License for the specific language governing permissions and\n" +
                "limitations under the License."
    }

    override fun readFullTextFromResources(context: Context?): String {
        return ""
    }

    override fun getVersion(): String {
        return ""
    }

    override fun getUrl(): String {
        return "https://github.com/neologd/mecab-ipadic-neologd/blob/master/COPYING"
    }
}
