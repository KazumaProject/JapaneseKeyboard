package com.kazumaproject.markdownhelperkeyboard.setting_activity.other

import android.content.Context
import de.psdev.licensesdialog.licenses.License

class WikiLicense : License() {

    override fun getName(): String {
        return "jawiki-latest-pages-articles-multistream-index.txt: CC BY-SA"
    }

    override fun readSummaryTextFromResources(context: Context?): String {
        return "この記事は、<a href=\"https://creativecommons.org/licenses/by-sa/4.0/\">クリエイティブ・コモンズ 表示-継承 4.0 国際 パブリック・ライセンス</a>のもとで公表されたウィキペディアの項目<a href=\"http://ja.wikipedia.org/wiki/%E3%83%A1%E3%82%BF%E6%A7%8B%E6%96%87%E5%A4%89%E6%95%B0\">「メタ構文変数」</a>を素材として二次利用しています。"
    }

    override fun readFullTextFromResources(context: Context?): String {
        return ""
    }

    override fun getVersion(): String {
        return ""
    }

    override fun getUrl(): String {
        return "https://ja.wikipedia.org/wiki/Wikipedia:%E3%82%A6%E3%82%A3%E3%82%AD%E3%83%9A%E3%83%87%E3%82%A3%E3%82%A2%E3%82%92%E4%BA%8C%E6%AC%A1%E5%88%A9%E7%94%A8%E3%81%99%E3%82%8B"
    }
}
