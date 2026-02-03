package com.kazumaproject.core.domain.extensions

/**
 * Extension function to convert all Hiragana characters in this String to their corresponding Katakana.
 *
 * Hiragana Unicode range: U+3041 (ぁ) to U+3096 (ゖ)
 * Katakana Unicode range: U+30A1 (ァ) to U+30F6 (ヶ)
 *
 * The offset between Hiragana and Katakana code points is 0x60.
 */
fun String.hiraganaToKatakana(): String = buildString {
    for (ch in this@hiraganaToKatakana) {
        when (ch) {
            // Basic Hiragana range (ぁ〜ゖ)
            in '\u3041'..'\u3096' -> append(ch + 0x60)
            // Hiragana iteration mark (ゝ U+309D) → Katakana iteration mark (ヽ U+30FD)
            '\u309D' -> append('\u30FD')
            // Hiragana voiced iteration mark (ゞ U+309E) → Katakana voiced iteration mark (ヾ U+30FE)
            '\u309E' -> append('\u30FE')
            else -> append(ch)
        }
    }
}

fun String.toZenkakuKatakana(): String {
    // 半角カタカナと全角カタカナのマッピング
    val hankakuToZenkakuMap = mapOf(
        "ｱ" to "ア", "ｲ" to "イ", "ｳ" to "ウ", "ｴ" to "エ", "ｵ" to "オ",
        "ｶ" to "カ", "ｷ" to "キ", "ｸ" to "ク", "ｹ" to "ケ", "ｺ" to "コ",
        "ｻ" to "サ", "ｼ" to "シ", "ｽ" to "ス", "ｾ" to "セ", "ｿ" to "ソ",
        "ﾀ" to "タ", "ﾁ" to "チ", "ﾂ" to "ツ", "ﾃ" to "テ", "ﾄ" to "ト",
        "ﾅ" to "ナ", "ﾆ" to "ニ", "ﾇ" to "ヌ", "ﾈ" to "ネ", "ﾉ" to "ノ",
        "ﾊ" to "ハ", "ﾋ" to "ヒ", "ﾌ" to "フ", "ﾍ" to "ヘ", "ﾎ" to "ホ",
        "ﾏ" to "マ", "ﾐ" to "ミ", "ﾑ" to "ム", "ﾒ" to "メ", "ﾓ" to "モ",
        "ﾔ" to "ヤ", "ﾕ" to "ユ", "ﾖ" to "ヨ",
        "ﾗ" to "ラ", "ﾘ" to "リ", "ﾙ" to "ル", "ﾚ" to "レ", "ﾛ" to "ロ",
        "ﾜ" to "ワ", "ｦ" to "ヲ", "ﾝ" to "ン",
        "ｧ" to "ァ", "ｨ" to "ィ", "ｩ" to "ゥ", "ｪ" to "ェ", "ｫ" to "ォ",
        "ｬ" to "ャ", "ｭ" to "ュ", "ｮ" to "ョ",
        "ｯ" to "ッ", "ｰ" to "ー",
        "｡" to "。", "､" to "、", "｢" to "「", "｣" to "」",
        // 濁点・半濁点付きの変換
        "ｶﾞ" to "ガ", "ｷﾞ" to "ギ", "ｸﾞ" to "グ", "ｹﾞ" to "ゲ", "ｺﾞ" to "ゴ",
        "ｻﾞ" to "ザ", "ｼﾞ" to "ジ", "ｽﾞ" to "ズ", "ｾﾞ" to "ゼ", "ｿﾞ" to "ゾ",
        "ﾀﾞ" to "ダ", "ﾁﾞ" to "ヂ", "ﾂﾞ" to "ヅ", "ﾃﾞ" to "デ", "ﾄﾞ" to "ド",
        "ﾊﾞ" to "バ", "ﾋﾞ" to "ビ", "ﾌﾞ" to "ブ", "ﾍﾞ" to "ベ", "ﾎﾞ" to "ボ",
        "ﾊﾟ" to "パ", "ﾋﾟ" to "ピ", "ﾌﾟ" to "プ", "ﾍﾟ" to "ペ", "ﾎﾟ" to "ポ",
        "ｳﾞ" to "ヴ"
    )

    val sb = StringBuilder()
    var i = 0
    while (i < this.length) {
        // --- 半角カタカナの処理 ---
        // 先に濁点・半濁点付きの2文字をチェック
        if (i + 1 < this.length) {
            val twoChars = this.substring(i, i + 2)
            val zenkaku = hankakuToZenkakuMap[twoChars]
            if (zenkaku != null) {
                sb.append(zenkaku)
                i += 2
                continue
            }
        }
        // 1文字の半角カタカナをチェック
        val oneChar = this.substring(i, i + 1)
        val zenkaku = hankakuToZenkakuMap[oneChar]
        if (zenkaku != null) {
            sb.append(zenkaku)
            i += 1
            continue
        }

        // --- ひらがな、その他の文字の処理 ---
        val ch = this[i]
        when (ch) {
            // ひらがな (ぁ〜ゖ) を全角カタカナに
            in '\u3041'..'\u3096' -> sb.append(ch + 0x60)
            // 繰り返し記号
            '\u309D' -> sb.append('\u30FD') // ゝ -> ヽ
            '\u309E' -> sb.append('\u30FE') // ゞ -> ヾ
            // 上記以外（全角カタカナ、漢字、英数字など）はそのまま
            else -> sb.append(ch)
        }
        i++
    }
    return sb.toString()
}

fun String.hiraganaToHankakuKatakana(): String {
    val hankakuKatakanaMap = mapOf(
        'あ' to "ｱ", 'い' to "ｲ", 'う' to "ｳ", 'え' to "ｴ", 'お' to "ｵ",
        'か' to "ｶ", 'き' to "ｷ", 'く' to "ｸ", 'け' to "ｹ", 'こ' to "ｺ",
        'さ' to "ｻ", 'し' to "ｼ", 'す' to "ｽ", 'せ' to "ｾ", 'そ' to "ｿ",
        'た' to "ﾀ", 'ち' to "ﾁ", 'つ' to "ﾂ", 'て' to "ﾃ", 'と' to "ﾄ",
        'な' to "ﾅ", 'に' to "ﾆ", 'ぬ' to "ﾇ", 'ね' to "ﾈ", 'の' to "ﾉ",
        'は' to "ﾊ", 'ひ' to "ﾋ", 'ふ' to "ﾌ", 'へ' to "ﾍ", 'ほ' to "ﾎ",
        'ま' to "ﾏ", 'み' to "ﾐ", 'む' to "ﾑ", 'め' to "ﾒ", 'も' to "ﾓ",
        'や' to "ﾔ", 'ゆ' to "ﾕ", 'よ' to "ﾖ",
        'ら' to "ﾗ", 'り' to "ﾘ", 'る' to "ﾙ", 'れ' to "ﾚ", 'ろ' to "ﾛ",
        'わ' to "ﾜ", 'を' to "ｦ", 'ん' to "ﾝ",
        'が' to "ｶﾞ", 'ぎ' to "ｷﾞ", 'ぐ' to "ｸﾞ", 'げ' to "ｹﾞ", 'ご' to "ｺﾞ",
        'ざ' to "ｻﾞ", 'じ' to "ｼﾞ", 'ず' to "ｽﾞ", 'ぜ' to "ｾﾞ", 'ぞ' to "ｿﾞ",
        'だ' to "ﾀﾞ", 'ぢ' to "ﾁﾞ", 'づ' to "ﾂﾞ", 'で' to "ﾃﾞ", 'ど' to "ﾄﾞ",
        'ば' to "ﾊﾞ", 'び' to "ﾋﾞ", 'ぶ' to "ﾌﾞ", 'べ' to "ﾍﾞ", 'ぼ' to "ﾎﾞ",
        'ぱ' to "ﾊﾟ", 'ぴ' to "ﾋﾟ", 'ぷ' to "ﾌﾟ", 'ぺ' to "ﾍﾟ", 'ぽ' to "ﾎﾟ",
        'ぁ' to "ｧ", 'ぃ' to "ｨ", 'ぅ' to "ｩ", 'ぇ' to "ｪ", 'ぉ' to "ｫ",
        'ゃ' to "ｬ", 'ゅ' to "ｭ", 'ょ' to "ｮ",
        'っ' to "ｯ",
        'ー' to "ｰ",
        '、' to "､", '。' to "｡",
        '「' to "｢", '」' to "｣"
    )

    val sb = StringBuilder()
    var i = 0
    while (i < this.length) {
        // 2文字で構成される濁音・半濁音を先にチェック
        if (i + 1 < this.length) {
            val twoChars = this.substring(i, i + 2)
            if (hankakuKatakanaMap.containsKey(twoChars[0]) && (twoChars[1] == 'ﾞ' || twoChars[1] == 'ﾟ')) {
                val base = hankakuKatakanaMap[twoChars[0]]
                val diacritic = if (twoChars[1] == 'ﾞ') "ﾞ" else "ﾟ"
                sb.append(base).append(diacritic)
                i += 2
                continue
            }
        }

        val char = this[i]
        sb.append(hankakuKatakanaMap[char] ?: char)
        i++
    }
    return sb.toString()
}

fun String.toHankakuKatakana(): String {
    // ひらがな・全角カタカナから半角カタカナへの統合マッピング
    val conversionMap = mapOf(
        'あ' to "ｱ", 'い' to "ｲ", 'う' to "ｳ", 'え' to "ｴ", 'お' to "ｵ",
        'か' to "ｶ", 'き' to "ｷ", 'く' to "ｸ", 'け' to "ｹ", 'こ' to "ｺ",
        'さ' to "ｻ", 'し' to "ｼ", 'す' to "ｽ", 'せ' to "ｾ", 'そ' to "ｿ",
        'た' to "ﾀ", 'ち' to "ﾁ", 'つ' to "ﾂ", 'て' to "ﾃ", 'と' to "ﾄ",
        'な' to "ﾅ", 'に' to "ﾆ", 'ぬ' to "ﾇ", 'ね' to "ﾈ", 'の' to "ﾉ",
        'は' to "ﾊ", 'ひ' to "ﾋ", 'ふ' to "ﾌ", 'へ' to "ﾍ", 'ほ' to "ﾎ",
        'ま' to "ﾏ", 'み' to "ﾐ", 'む' to "ﾑ", 'め' to "ﾒ", 'も' to "ﾓ",
        'や' to "ﾔ", 'ゆ' to "ﾕ", 'よ' to "ﾖ",
        'ら' to "ﾗ", 'り' to "ﾘ", 'る' to "ﾙ", 'れ' to "ﾚ", 'ろ' to "ﾛ",
        'わ' to "ﾜ", 'を' to "ｦ", 'ん' to "ﾝ",
        'が' to "ｶﾞ", 'ぎ' to "ｷﾞ", 'ぐ' to "ｸﾞ", 'げ' to "ｹﾞ", 'ご' to "ｺﾞ",
        'ざ' to "ｻﾞ", 'じ' to "ｼﾞ", 'ず' to "ｽﾞ", 'ぜ' to "ｾﾞ", 'ぞ' to "ｿﾞ",
        'だ' to "ﾀﾞ", 'ぢ' to "ﾁﾞ", 'づ' to "ﾂﾞ", 'で' to "ﾃﾞ", 'ど' to "ﾄﾞ",
        'ば' to "ﾊﾞ", 'び' to "ﾋﾞ", 'ぶ' to "ﾌﾞ", 'べ' to "ﾍﾞ", 'ぼ' to "ﾎﾞ",
        'ぱ' to "ﾊﾟ", 'ぴ' to "ﾋﾟ", 'ぷ' to "ﾌﾟ", 'ぺ' to "ﾍﾟ", 'ぽ' to "ﾎﾟ",
        'ぁ' to "ｧ", 'ぃ' to "ｨ", 'ぅ' to "ｩ", 'ぇ' to "ｪ", 'ぉ' to "ｫ",
        'ゃ' to "ｬ", 'ゅ' to "ｭ", 'ょ' to "ｮ", 'っ' to "ｯ",

        'ア' to "ｱ", 'イ' to "ｲ", 'ウ' to "ｳ", 'エ' to "ｴ", 'オ' to "ｵ",
        'カ' to "ｶ", 'キ' to "ｷ", 'ク' to "ｸ", 'ケ' to "ｹ", 'コ' to "ｺ",
        'サ' to "ｻ", 'シ' to "ｼ", 'ス' to "ｽ", 'セ' to "ｾ", 'ソ' to "ｿ",
        'タ' to "ﾀ", 'チ' to "ﾁ", 'ツ' to "ﾂ", 'テ' to "ﾃ", 'ト' to "ﾄ",
        'ナ' to "ﾅ", 'ニ' to "ﾆ", 'ヌ' to "ﾇ", 'ネ' to "ﾈ", 'ノ' to "ﾉ",
        'ハ' to "ﾊ", 'ヒ' to "ﾋ", 'フ' to "ﾌ", 'ヘ' to "ﾍ", 'ホ' to "ﾎ",
        'マ' to "ﾏ", 'ミ' to "ﾐ", 'ム' to "ﾑ", 'メ' to "ﾒ", 'モ' to "ﾓ",
        'ヤ' to "ﾔ", 'ユ' to "ﾕ", 'ヨ' to "ﾖ",
        'ラ' to "ﾗ", 'リ' to "ﾘ", 'ル' to "ﾙ", 'レ' to "ﾚ", 'ロ' to "ﾛ",
        'ワ' to "ﾜ", 'ヲ' to "ｦ", 'ン' to "ﾝ", 'ヴ' to "ｳﾞ",
        'ガ' to "ｶﾞ", 'ギ' to "ｷﾞ", 'グ' to "ｸﾞ", 'ゲ' to "ｹﾞ", 'ゴ' to "ｺﾞ",
        'ザ' to "ｻﾞ", 'ジ' to "ｼﾞ", 'ズ' to "ｽﾞ", 'ゼ' to "ｾﾞ", 'ゾ' to "ｿﾞ",
        'ダ' to "ﾀﾞ", 'ヂ' to "ﾁﾞ", 'ヅ' to "ﾂﾞ", 'デ' to "ﾃﾞ", 'ド' to "ﾄﾞ",
        'バ' to "ﾊﾞ", 'ビ' to "ﾋﾞ", 'ブ' to "ﾌﾞ", 'ベ' to "ﾍﾞ", 'ボ' to "ﾎﾞ",
        'パ' to "ﾊﾟ", 'ピ' to "ﾋﾟ", 'プ' to "ﾌﾟ", 'ペ' to "ﾍﾟ", 'ポ' to "ﾎﾟ",
        'ァ' to "ｧ", 'ィ' to "ｨ", 'ゥ' to "ｩ", 'ェ' to "ｪ", 'ォ' to "ｫ",
        'ャ' to "ｬ", 'ュ' to "ｭ", 'ョ' to "ｮ", 'ッ' to "ｯ",

        'ー' to "ｰ", '、' to "､", '。' to "｡", '「' to "｢", '」' to "｣"
    )

    return buildString {
        for (char in this@toHankakuKatakana) {
            // マップから変換後の文字を取得。なければ元の文字をそのまま使用。
            append(conversionMap[char] ?: char)
        }
    }
}

/**
 * 文字列に含まれる全角アルファベット（Ａ-Ｚ, ａ-ｚ）と全角スペース（　）を半角に変換します。
 * その他の文字はそのまま維持されます。
 */
fun String.toHankakuAlphabet(): String {
    return buildString(this.length) {
        for (char in this@toHankakuAlphabet) {
            // 全角アルファベットのUnicode範囲内かチェック
            // 'Ａ'(U+FF21) から 'A'(U+0041) の差は 0xFEE0
            val convertedChar = when (char) {
                in 'Ａ'..'Ｚ', in 'ａ'..'ｚ' -> (char.code - 0xFEE0).toChar()
                '　' -> ' ' // 全角スペースを半角スペースに
                else -> char
            }
            append(convertedChar)
        }
    }
}

/**
 * 文字列に含まれる半角アルファベット（A-Z, a-z）と半角スペース（ ）を全角に変換します。
 * その他の文字はそのまま維持されます。
 */
fun String.toZenkakuAlphabet(): String {
    return buildString(this.length) {
        for (char in this@toZenkakuAlphabet) {
            // 半角アルファベットのUnicode範囲内かチェック
            // 'A'(U+0041) から 'Ａ'(U+FF21) の差は 0xFEE0
            val convertedChar = when (char) {
                in 'A'..'Z', in 'a'..'z' -> (char.code + 0xFEE0).toChar()
                ' ' -> '　' // 半角スペースを全角スペースに
                else -> char
            }
            append(convertedChar)
        }
    }
}


/**
 * Extension function to convert all Katakana characters in this String to their corresponding Hiragana.
 *
 * Katakana Unicode range: U+30A1 (ァ) to U+30F6 (ヶ)
 * Hiragana Unicode range: U+3041 (ぁ) to U+3096 (ゖ)
 *
 * The offset between Katakana and Hiragana code points is -0x60.
 */
fun String.katakanaToHiragana(): String = buildString {
    for (ch in this@katakanaToHiragana) {
        when (ch) {
            // Basic Katakana range (ァ〜ヶ)
            in '\u30A1'..'\u30F6' -> append(ch - 0x60)
            // Katakana iteration mark (ヽ U+30FD) → Hiragana iteration mark (ゝ U+309D)
            '\u30FD' -> append('\u309D')
            // Katakana voiced iteration mark (ヾ U+30FE) → Hiragana voiced iteration mark (ゞ U+309E)
            '\u30FE' -> append('\u309E')
            else -> append(ch)
        }
    }
}

fun String.toHiragana(): String {
    // 半角カタカナとひらがなのマッピング
    // 濁音・半濁音は結合後の2文字をキーにする
    val hankakuKatakanaMap = mapOf(
        "ｱ" to "あ", "ｲ" to "い", "ｳ" to "う", "ｴ" to "え", "ｵ" to "お",
        "ｶ" to "か", "ｷ" to "き", "ｸ" to "く", "ｹ" to "け", "ｺ" to "こ",
        "ｻ" to "さ", "ｼ" to "し", "ｽ" to "す", "ｾ" to "せ", "ｿ" to "そ",
        "ﾀ" to "た", "ﾁ" to "ち", "ﾂ" to "つ", "ﾃ" to "て", "ﾄ" to "と",
        "ﾅ" to "な", "ﾆ" to "に", "ﾇ" to "ぬ", "ﾈ" to "ね", "ﾉ" to "の",
        "ﾊ" to "は", "ﾋ" to "ひ", "ﾌ" to "ふ", "ﾍ" to "へ", "ﾎ" to "ほ",
        "ﾏ" to "ま", "ﾐ" to "み", "ﾑ" to "む", "ﾒ" to "め", "ﾓ" to "も",
        "ﾔ" to "や", "ﾕ" to "ゆ", "ﾖ" to "よ",
        "ﾗ" to "ら", "ﾘ" to "り", "ﾙ" to "る", "ﾚ" to "れ", "ﾛ" to "ろ",
        "ﾜ" to "わ", "ｦ" to "を", "ﾝ" to "ん",
        "ｧ" to "ぁ", "ｨ" to "ぃ", "ｩ" to "ぅ", "ｪ" to "ぇ", "ｫ" to "ぉ",
        "ｬ" to "ゃ", "ｭ" to "ゅ", "ｮ" to "ょ",
        "ｯ" to "っ", "ｰ" to "ー",
        "｡" to "。", "､" to "、", "｢" to "「", "｣" to "」", "ﾞ" to "゛", "ﾟ" to "゜",

        // 濁音
        "ｶﾞ" to "が", "ｷﾞ" to "ぎ", "ｸﾞ" to "ぐ", "ｹﾞ" to "げ", "ｺﾞ" to "ご",
        "ｻﾞ" to "ざ", "ｼﾞ" to "じ", "ｽﾞ" to "ず", "ｾﾞ" to "ぜ", "ｿﾞ" to "ぞ",
        "ﾀﾞ" to "だ", "ﾁﾞ" to "ぢ", "ﾂﾞ" to "づ", "ﾃﾞ" to "で", "ﾄﾞ" to "ど",
        "ﾊﾞ" to "ば", "ﾋﾞ" to "び", "ﾌﾞ" to "ぶ", "ﾍﾞ" to "べ", "ﾎﾞ" to "ぼ",

        // 半濁音
        "ﾊﾟ" to "ぱ", "ﾋﾟ" to "ぴ", "ﾌﾟ" to "ぷ", "ﾍﾟ" to "ぺ", "ﾎﾟ" to "ぽ"
    )

    val sb = StringBuilder()
    var i = 0
    while (i < this.length) {
        // 2文字で構成される半角の濁音・半濁音を先にチェック
        if (i + 1 < this.length) {
            val twoChars = this.substring(i, i + 2)
            val hiragana = hankakuKatakanaMap[twoChars]
            if (hiragana != null) {
                sb.append(hiragana)
                i += 2
                continue
            }
        }

        // 1文字の処理
        val ch = this[i]

        // 1文字の半角カタカナをチェック
        val singleHankakuHiragana = hankakuKatakanaMap[ch.toString()]
        if (singleHankakuHiragana != null) {
            sb.append(singleHankakuHiragana)
        } else {
            // 全角カタカナまたはその他の文字の処理
            when (ch) {
                // 全角カタカナ (ァ〜ヶ)
                in '\u30A1'..'\u30F6' -> sb.append(ch - 0x60)
                // カタカナ長音記号
                'ー' -> sb.append('ー')
                // 繰り返し記号
                '\u30FD' -> sb.append('\u309D') // ヽ -> ゝ
                '\u30FE' -> sb.append('\u309E') // ヾ -> ゞ
                // 上記以外はそのまま追加
                else -> sb.append(ch)
            }
        }
        i++
    }
    return sb.toString()
}

/**
 * 文字が半角の「数字または記号」であるかを判定します。
 * アルファベットは false を返します。
 */
fun Char.isHalfWidthNumericSymbol(): Boolean {
    return when (this) {
        // 半角数字 (0-9)
        in '\u0030'..'\u0039' -> true
        // 半角記号とスペース
        in '\u0020'..'\u002F', in '\u003A'..'\u0040', in '\u005B'..'\u0060', in '\u007B'..'\u007E' -> true
        // それ以外（アルファベット等）は false
        else -> false
    }
}

/**
 * 文字が全角の「数字または記号」であるかを判定します。
 * アルファベットは false を返します。
 */
fun Char.isFullWidthNumericSymbol(): Boolean {
    return when (this) {
        // 全角数字 (０-９)
        in '\uFF10'..'\uFF19' -> true
        // 全角記号と全角スペース
        '\u3000', in '\uFF01'..'\uFF0F', in '\uFF1A'..'\uFF20', in '\uFF3B'..'\uFF40', in '\uFF5B'..'\uFF5E' -> true
        // それ以外（アルファベット等）は false
        else -> false
    }
}

/**
 * 文字列がすべて半角の「数字または記号」で構成されているかを判定します。
 */
fun String.isAllHalfWidthNumericSymbol(): Boolean {
    if (this.isEmpty() || this.length > 2) return false
    return this.all { it.isHalfWidthNumericSymbol() }
}

/**
 * 文字列がすべて全角の「数字または記号」で構成されているかを判定します。
 */
fun String.isAllFullWidthNumericSymbol(): Boolean {
    if (this.isEmpty() || this.length > 2) return false
    return this.all { it.isFullWidthNumericSymbol() }
}

/**
 * (拡張関数)
 * 文字列内に同じ文字がN文字以上連続して出現するかどうかを判定します。
 */
fun String.hasNConsecutiveChars(n: Int): Boolean {
    val regex = Regex("(.)\\1{$n,}")
    return regex.containsMatchIn(this)
}

/**
 * getCharVariationsで定義された文字のバリエーションから、
 * その文字の基本形（濁点や小文字ではない文字）を返すためのマップ。
 * 例：'が' -> 'か', 'っ' -> 'つ'
 */
private val charVariationToBaseMap: Map<Char, Char> by lazy {
    val map = mutableMapOf<Char, Char>()
    // getCharVariationsで定義されているすべての基本文字
    val baseChars = listOf(
        'か', 'き', 'く', 'け', 'こ',
        'さ', 'し', 'す', 'せ', 'そ',
        'た', 'ち', 'つ', 'て', 'と',
        'は', 'ひ', 'ふ', 'へ', 'ほ',
        'や', 'ゆ', 'よ',
        'あ', 'い', 'う', 'え', 'お'
    )
    for (base in baseChars) {
        for (variation in getCharVariations(base)) {
            map[variation] = base
        }
    }
    map
}

/**
 * 文字の基本形を返すヘルパー関数。
 * @param char 変換元の文字
 * @return 基本形の文字
 */
private fun getBaseChar(char: Char): Char {
    return charVariationToBaseMap[char] ?: char
}


/**
 * (拡張関数)
 * 文字列内に、同じバリエーションの文字が合計でN個以上出現するかどうかを判定します。
 * 'か'と'が'、'つ'と'っ'などは同じバリエーションと見なされます。
 *
 * @param n 出現回数のしきい値
 * @return 条件を満たす文字グループが存在する場合は true、そうでない場合は false
 */
fun String.hasNCharVariations(n: Int): Boolean {
    if (n <= 1 && this.isNotEmpty()) return true
    if (this.length < n) return false

    // 文字の基本形ごとに出現回数をカウントするマップ
    val counts = mutableMapOf<Char, Int>()

    // 文字列を一度だけループし、各文字の基本形のカウントを増やす
    for (char in this) {
        val baseChar = getBaseChar(char)
        counts[baseChar] = counts.getOrDefault(baseChar, 0) + 1
    }

    // いずれかのカウントがn以上であれば true を返す
    return counts.any { it.value >= n }
}

private fun getCharVariations(char: Char): List<Char> {
    return when (char) {
        'か' -> listOf('が')
        'き' -> listOf('ぎ')
        'く' -> listOf('ぐ')
        'け' -> listOf('げ')
        'こ' -> listOf('ご')
        'さ' -> listOf('ざ')
        'し' -> listOf('じ')
        'す' -> listOf('ず')
        'せ' -> listOf('ぜ')
        'そ' -> listOf('ぞ')
        'た' -> listOf('だ')
        'ち' -> listOf('ぢ')
        'つ' -> listOf('づ', 'っ')
        'て' -> listOf('で')
        'と' -> listOf('ど')
        'は' -> listOf('ば', 'ぱ')
        'ひ' -> listOf('び', 'ぴ')
        'ふ' -> listOf('ぶ', 'ぷ')
        'へ' -> listOf('べ', 'ぺ')
        'ほ' -> listOf('ぼ', 'ぽ')
        'や' -> listOf('ゃ')
        'ゆ' -> listOf('ゅ')
        'よ' -> listOf('ょ')
        else -> listOf(char) // 上記以外はそのまま
    }
}

/**
 * 文字列内の半角英数字・記号を全角に変換します。
 * @param input 変換元の文字列
 * @return 全角に変換された文字列
 */
fun String.toZenkaku(): String {
    val sb = StringBuilder()
    for (char in this) {
        when (char) {
            // 半角英字 (a-z, A-Z)
            in 'a'..'z' -> sb.append(char + ('ａ' - 'a'))
            in 'A'..'Z' -> sb.append(char + ('Ａ' - 'A'))
            // 半角数字 (0-9)
            in '0'..'9' -> sb.append(char + ('０' - '0'))
            // Mapのキーに含まれる主な記号
            '-' -> sb.append('－')
            '~' -> sb.append('～')
            '.' -> sb.append('．')
            ',' -> sb.append('，')
            '/' -> sb.append('／')
            '[' -> sb.append('［')
            ']' -> sb.append('］')
            // 上記以外の文字はそのまま追加
            else -> sb.append(char)
        }
    }
    return sb.toString()
}

fun String.kanjiCount(): Int {
    var c = 0
    for (ch in this) {
        val block = Character.UnicodeBlock.of(ch)
        if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
        ) {
            c++
        }
    }
    return c
}

fun String.duplicateCharCount(): Int {
    // 2回目以降の総数（重複が多いほど大きい）
    val counts = this.groupingBy { it }.eachCount()
    return counts.values.sumOf { (it - 1).coerceAtLeast(0) }
}

// もし「同じ文字が連続しているのが嫌」なら、こちらも併用できます
fun String.maxCharFrequency(): Int {
    // 1文字が最大何回出現するか（小さいほど偏りが少ない）
    val counts = this.groupingBy { it }.eachCount()
    return counts.values.maxOrNull() ?: 0
}
