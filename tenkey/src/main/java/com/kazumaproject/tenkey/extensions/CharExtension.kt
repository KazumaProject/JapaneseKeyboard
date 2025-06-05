package com.kazumaproject.tenkey.extensions

fun Char.getNextInputChar(charAtInsertPosition: Char): Char? {
    return when {
        this == 'あ' && charAtInsertPosition == 'あ' -> 'い'
        this == 'い' && charAtInsertPosition == 'あ' -> 'う'
        this == 'う' && charAtInsertPosition == 'あ' -> 'え'
        this == 'え' && charAtInsertPosition == 'あ' -> 'お'
        this == 'お' && charAtInsertPosition == 'あ' -> 'ぁ'
        this == 'ぁ' && charAtInsertPosition == 'あ' -> 'ぃ'
        this == 'ぃ' && charAtInsertPosition == 'あ' -> 'ぅ'
        this == 'ぅ' && charAtInsertPosition == 'あ' -> 'ぇ'
        this == 'ぇ' && charAtInsertPosition == 'あ' -> 'ぉ'
        this == 'ぉ' && charAtInsertPosition == 'あ' -> 'あ'

        this == 'か' && charAtInsertPosition == 'か' -> 'き'
        this == 'き' && charAtInsertPosition == 'か' -> 'く'
        this == 'く' && charAtInsertPosition == 'か' -> 'け'
        this == 'け' && charAtInsertPosition == 'か' -> 'こ'
        this == 'こ' && charAtInsertPosition == 'か' -> 'か'

        this == 'さ' && charAtInsertPosition == 'さ' -> 'し'
        this == 'し' && charAtInsertPosition == 'さ' -> 'す'
        this == 'す' && charAtInsertPosition == 'さ' -> 'せ'
        this == 'せ' && charAtInsertPosition == 'さ' -> 'そ'
        this == 'そ' && charAtInsertPosition == 'さ' -> 'さ'

        this == 'た' && charAtInsertPosition == 'た' -> 'ち'
        this == 'ち' && charAtInsertPosition == 'た' -> 'つ'
        this == 'つ' && charAtInsertPosition == 'た' -> 'て'
        this == 'て' && charAtInsertPosition == 'た' -> 'と'
        this == 'と' && charAtInsertPosition == 'た' -> 'っ'
        this == 'っ' && charAtInsertPosition == 'た' -> 'た'

        this == 'な' && charAtInsertPosition == 'な' -> 'に'
        this == 'に' && charAtInsertPosition == 'な' -> 'ぬ'
        this == 'ぬ' && charAtInsertPosition == 'な' -> 'ね'
        this == 'ね' && charAtInsertPosition == 'な' -> 'の'
        this == 'の' && charAtInsertPosition == 'な' -> 'な'

        this == 'は' && charAtInsertPosition == 'は' -> 'ひ'
        this == 'ひ' && charAtInsertPosition == 'は' -> 'ふ'
        this == 'ふ' && charAtInsertPosition == 'は' -> 'へ'
        this == 'へ' && charAtInsertPosition == 'は' -> 'ほ'
        this == 'ほ' && charAtInsertPosition == 'は' -> 'は'

        this == 'ま' && charAtInsertPosition == 'ま' -> 'み'
        this == 'み' && charAtInsertPosition == 'ま' -> 'む'
        this == 'む' && charAtInsertPosition == 'ま' -> 'め'
        this == 'め' && charAtInsertPosition == 'ま' -> 'も'
        this == 'も' && charAtInsertPosition == 'ま' -> 'ま'

        this == 'や' && charAtInsertPosition == 'や' -> 'ゆ'
        this == 'ゆ' && charAtInsertPosition == 'や' -> 'よ'
        this == 'よ' && charAtInsertPosition == 'や' -> 'ゃ'
        this == 'ゃ' && charAtInsertPosition == 'や' -> 'ゅ'
        this == 'ゅ' && charAtInsertPosition == 'や' -> 'ょ'
        this == 'ょ' && charAtInsertPosition == 'や' -> 'や'

        this == 'ら' && charAtInsertPosition == 'ら' -> 'り'
        this == 'り' && charAtInsertPosition == 'ら' -> 'る'
        this == 'る' && charAtInsertPosition == 'ら' -> 'れ'
        this == 'れ' && charAtInsertPosition == 'ら' -> 'ろ'
        this == 'ろ' && charAtInsertPosition == 'ら' -> 'ら'

        this == 'わ' && charAtInsertPosition == 'わ' -> 'を'
        this == 'を' && charAtInsertPosition == 'わ' -> 'ん'
        this == 'ん' && charAtInsertPosition == 'わ' -> 'ゎ'
        this == 'ゎ' && charAtInsertPosition == 'わ' -> 'ー'
        this == 'ー' && charAtInsertPosition == 'わ' -> '〜'
        this == '〜' && charAtInsertPosition == 'わ' -> 'わ'

        this == '、' && charAtInsertPosition == '、' -> '。'
        this == '。' && charAtInsertPosition == '、' -> '？'
        this == '？' && charAtInsertPosition == '、' -> '！'
        this == '！' && charAtInsertPosition == '、' -> '…'
        this == '…' && charAtInsertPosition == '、' -> '・'
        this == '・' && charAtInsertPosition == '、' -> '、'

        this == '@' && charAtInsertPosition == '@' -> '#'
        this == '#' && charAtInsertPosition == '@' -> '&'
        this == '&' && charAtInsertPosition == '@' -> '_'
        this == '_' && charAtInsertPosition == '@' -> '1'
        this == '1' && charAtInsertPosition == '@' -> '@'

        this == 'a' && charAtInsertPosition == 'a' -> 'b'
        this == 'b' && charAtInsertPosition == 'a' -> 'c'
        this == 'c' && charAtInsertPosition == 'a' -> 'A'
        this == 'A' && charAtInsertPosition == 'a' -> 'B'
        this == 'B' && charAtInsertPosition == 'a' -> 'C'
        this == 'C' && charAtInsertPosition == 'a' -> '2'
        this == '2' && charAtInsertPosition == 'a' -> 'a'

        this == 'd' && charAtInsertPosition == 'd' -> 'e'
        this == 'e' && charAtInsertPosition == 'd' -> 'f'
        this == 'f' && charAtInsertPosition == 'd' -> 'D'
        this == 'D' && charAtInsertPosition == 'd' -> 'E'
        this == 'E' && charAtInsertPosition == 'd' -> 'F'
        this == 'F' && charAtInsertPosition == 'd' -> '3'
        this == '3' && charAtInsertPosition == 'd' -> 'd'

        this == 'g' && charAtInsertPosition == 'g' -> 'h'
        this == 'h' && charAtInsertPosition == 'g' -> 'i'
        this == 'i' && charAtInsertPosition == 'g' -> 'G'
        this == 'G' && charAtInsertPosition == 'g' -> 'H'
        this == 'H' && charAtInsertPosition == 'g' -> 'I'
        this == 'I' && charAtInsertPosition == 'g' -> '4'
        this == '4' && charAtInsertPosition == 'g' -> 'g'

        this == 'j' && charAtInsertPosition == 'j' -> 'k'
        this == 'k' && charAtInsertPosition == 'j' -> 'l'
        this == 'l' && charAtInsertPosition == 'j' -> 'J'
        this == 'J' && charAtInsertPosition == 'j' -> 'K'
        this == 'K' && charAtInsertPosition == 'j' -> 'L'
        this == 'L' && charAtInsertPosition == 'j' -> '5'
        this == '5' && charAtInsertPosition == 'j' -> 'j'

        this == 'm' && charAtInsertPosition == 'm' -> 'n'
        this == 'n' && charAtInsertPosition == 'm' -> 'o'
        this == 'o' && charAtInsertPosition == 'm' -> 'M'
        this == 'M' && charAtInsertPosition == 'm' -> 'N'
        this == 'N' && charAtInsertPosition == 'm' -> 'O'
        this == 'O' && charAtInsertPosition == 'm' -> '6'
        this == '6' && charAtInsertPosition == 'm' -> 'm'

        this == 'p' && charAtInsertPosition == 'p' -> 'q'
        this == 'q' && charAtInsertPosition == 'p' -> 'r'
        this == 'r' && charAtInsertPosition == 'p' -> 's'
        this == 's' && charAtInsertPosition == 'p' -> 'P'
        this == 'P' && charAtInsertPosition == 'p' -> 'Q'
        this == 'Q' && charAtInsertPosition == 'p' -> 'R'
        this == 'R' && charAtInsertPosition == 'p' -> 'S'
        this == 'S' && charAtInsertPosition == 'p' -> '7'
        this == '7' && charAtInsertPosition == 'p' -> 'p'

        this == 't' && charAtInsertPosition == 't' -> 'u'
        this == 'u' && charAtInsertPosition == 't' -> 'v'
        this == 'v' && charAtInsertPosition == 't' -> 'T'
        this == 'T' && charAtInsertPosition == 't' -> 'U'
        this == 'U' && charAtInsertPosition == 't' -> 'V'
        this == 'V' && charAtInsertPosition == 't' -> '8'
        this == '8' && charAtInsertPosition == 't' -> 't'

        this == 'w' && charAtInsertPosition == 'w' -> 'x'
        this == 'x' && charAtInsertPosition == 'w' -> 'y'
        this == 'y' && charAtInsertPosition == 'w' -> 'z'
        this == 'z' && charAtInsertPosition == 'w' -> 'W'
        this == 'W' && charAtInsertPosition == 'w' -> 'X'
        this == 'X' && charAtInsertPosition == 'w' -> 'Y'
        this == 'Y' && charAtInsertPosition == 'w' -> 'Z'
        this == 'Z' && charAtInsertPosition == 'w' -> '9'
        this == '9' && charAtInsertPosition == 'w' -> 'w'

        this == '\'' && charAtInsertPosition == '\'' -> '\"'
        this == '\"' && charAtInsertPosition == '\'' -> ':'
        this == ':' && charAtInsertPosition == '\'' -> ';'
        this == ';' && charAtInsertPosition == '\'' -> '0'
        this == '0' && charAtInsertPosition == '\'' -> '\''

        this == '.' && charAtInsertPosition == '.' -> ','
        this == ',' && charAtInsertPosition == '.' -> '?'
        this == '?' && charAtInsertPosition == '.' -> '!'
        this == '!' && charAtInsertPosition == '.' -> '-'
        this == '-' && charAtInsertPosition == '.' -> '.'

        else -> null
    }
}

fun Char.getNextReturnInputChar(): Char? {
    return when (this) {
        'あ' -> 'ぉ'
        'い' -> 'あ'
        'う' -> 'い'
        'え' -> 'う'
        'お' -> 'え'
        'ぁ' -> 'お'
        'ぃ' -> 'ぁ'
        'ぅ' -> 'ぃ'
        'ぇ' -> 'ぅ'
        'ぉ' -> 'ぇ'

        'か' -> 'こ'
        'き' -> 'か'
        'く' -> 'き'
        'け' -> 'く'
        'こ' -> 'け'

        'さ' -> 'そ'
        'し' -> 'さ'
        'す' -> 'し'
        'せ' -> 'す'
        'そ' -> 'せ'

        'た' -> 'っ'
        'ち' -> 'た'
        'つ' -> 'ち'
        'て' -> 'つ'
        'と' -> 'て'
        'っ' -> 'と'

        'な' -> 'の'
        'に' -> 'な'
        'ぬ' -> 'に'
        'ね' -> 'ぬ'
        'の' -> 'ね'

        'は' -> 'ほ'
        'ひ' -> 'は'
        'ふ' -> 'ひ'
        'へ' -> 'ふ'
        'ほ' -> 'へ'

        'ま' -> 'も'
        'み' -> 'ま'
        'む' -> 'み'
        'め' -> 'む'
        'も' -> 'め'

        'や' -> 'ょ'
        'ゆ' -> 'や'
        'よ' -> 'ゆ'
        'ゃ' -> 'よ'
        'ゅ' -> 'ゃ'
        'ょ' -> 'ゅ'

        'ら' -> 'ろ'
        'り' -> 'ら'
        'る' -> 'り'
        'れ' -> 'る'
        'ろ' -> 'れ'

        'わ' -> '〜'
        'を' -> 'わ'
        'ん' -> 'を'
        'ゎ' -> 'ん'
        'ー' -> 'ゎ'
        '〜' -> 'ー'

        '、' -> '・'
        '。' -> '、'
        '？' -> '。'
        '！' -> '？'
        '…' -> '！'
        '・' -> '…'

        '@' -> '1'
        '#' -> '@'
        '&' -> '#'
        '_' -> '&'
        '1' -> '_'

        'a' -> '2'
        'b' -> 'a'
        'c' -> 'b'
        'A' -> 'c'
        'B' -> 'A'
        'C' -> 'B'
        '2' -> 'C'

        'd' -> '3'
        'e' -> 'd'
        'f' -> 'e'
        'D' -> 'f'
        'E' -> 'D'
        'F' -> 'E'
        '3' -> 'F'

        'g' -> '4'
        'h' -> 'g'
        'i' -> 'h'
        'G' -> 'i'
        'H' -> 'G'
        'I' -> 'H'
        '4' -> 'I'

        'j' -> '5'
        'k' -> 'j'
        'l' -> 'k'
        'J' -> 'l'
        'K' -> 'J'
        'L' -> 'K'
        '5' -> 'L'

        'm' -> '6'
        'n' -> 'm'
        'o' -> 'n'
        'M' -> 'o'
        'N' -> 'M'
        'O' -> 'N'
        '6' -> 'O'

        'p' -> '7'
        'q' -> 'p'
        'r' -> 'q'
        's' -> 'r'
        'P' -> 's'
        'Q' -> 'P'
        'R' -> 'Q'
        'S' -> 'R'
        '7' -> 'S'

        't' -> '8'
        'u' -> 't'
        'v' -> 'u'
        'T' -> 'v'
        'U' -> 'T'
        'V' -> 'U'
        '8' -> 'V'

        'x' -> '9'
        'w' -> 'x'
        'y' -> 'w'
        'z' -> 'y'
        'X' -> 'z'
        'W' -> 'X'
        'Y' -> 'W'
        'Z' -> 'Y'
        '9' -> 'Z'

        '\'' -> '0'
        '\"' -> '\''
        ':' -> '\"'
        ';' -> ':'
        '0' -> ';'

        '.' -> '-'
        ',' -> '.'
        '?' -> ','
        '!' -> '?'
        '-' -> '!'

        else -> null
    }
}

fun Char.getDakutenSmallChar(): Char? {
    return when (this) {
        'あ' -> 'ぁ'
        'ぁ' -> 'あ'
        'い' -> 'ぃ'
        'ぃ' -> 'い'
        'う' -> 'ぅ'
        'ぅ' -> 'ゔ'
        'ゔ' -> 'う'
        'え' -> 'ぇ'
        'ぇ' -> 'え'
        'お' -> 'ぉ'
        'ぉ' -> 'お'

        'か' -> 'が'
        'が' -> 'か'
        'き' -> 'ぎ'
        'ぎ' -> 'き'
        'く' -> 'ぐ'
        'ぐ' -> 'く'
        'け' -> 'げ'
        'げ' -> 'け'
        'こ' -> 'ご'
        'ご' -> 'こ'

        'さ' -> 'ざ'
        'ざ' -> 'さ'
        'し' -> 'じ'
        'じ' -> 'し'
        'す' -> 'ず'
        'ず' -> 'す'
        'せ' -> 'ぜ'
        'ぜ' -> 'せ'
        'そ' -> 'ぞ'
        'ぞ' -> 'そ'

        'た' -> 'だ'
        'だ' -> 'た'
        'ち' -> 'ぢ'
        'ぢ' -> 'ち'
        'つ' -> 'っ'
        'っ' -> 'づ'
        'づ' -> 'つ'
        'て' -> 'で'
        'で' -> 'て'
        'と' -> 'ど'
        'ど' -> 'と'

        'は' -> 'ば'
        'ば' -> 'ぱ'
        'ぱ' -> 'は'
        'ひ' -> 'び'
        'び' -> 'ぴ'
        'ぴ' -> 'ひ'
        'ふ' -> 'ぶ'
        'ぶ' -> 'ぷ'
        'ぷ' -> 'ふ'
        'へ' -> 'べ'
        'べ' -> 'ぺ'
        'ぺ' -> 'へ'
        'ほ' -> 'ぼ'
        'ぼ' -> 'ぽ'
        'ぽ' -> 'ほ'

        'や' -> 'ゃ'
        'ゃ' -> 'や'
        'ゆ' -> 'ゅ'
        'ゅ' -> 'ゆ'
        'よ' -> 'ょ'
        'ょ' -> 'よ'

        'わ' -> 'ゎ'
        'ゎ' -> 'わ'

        'a' -> 'A'
        'b' -> 'B'
        'c' -> 'C'
        'A' -> 'a'
        'B' -> 'b'
        'C' -> 'c'

        'd' -> 'D'
        'e' -> 'E'
        'f' -> 'F'
        'D' -> 'd'
        'E' -> 'e'
        'F' -> 'f'

        'g' -> 'G'
        'h' -> 'H'
        'i' -> 'I'
        'G' -> 'g'
        'H' -> 'h'
        'I' -> 'i'

        'j' -> 'J'
        'k' -> 'K'
        'l' -> 'L'
        'J' -> 'j'
        'K' -> 'k'
        'L' -> 'l'

        'm' -> 'M'
        'n' -> 'N'
        'o' -> 'O'
        'M' -> 'm'
        'N' -> 'n'
        'O' -> 'o'

        'p' -> 'P'
        'q' -> 'Q'
        'r' -> 'R'
        's' -> 'S'
        'P' -> 'p'
        'Q' -> 'q'
        'R' -> 'r'
        'S' -> 's'

        't' -> 'T'
        'u' -> 'U'
        'v' -> 'V'
        'T' -> 't'
        'U' -> 'u'
        'V' -> 'v'

        'w' -> 'W'
        'x' -> 'X'
        'y' -> 'Y'
        'z' -> 'Z'
        'W' -> 'w'
        'X' -> 'x'
        'Y' -> 'y'
        'Z' -> 'z'

        else -> null
    }
}

/** 濁点 **/
fun Char.getDakutenFlickLeft(): Char? {
    return when (this) {
        'う', 'ぅ' -> 'ゔ'
        'か' -> 'が'
        'き' -> 'ぎ'
        'く' -> 'ぐ'
        'け' -> 'げ'
        'こ' -> 'ご'
        'さ' -> 'ざ'
        'し' -> 'じ'
        'す' -> 'ず'
        'せ' -> 'ぜ'
        'そ' -> 'ぞ'
        'た' -> 'だ'
        'ち' -> 'ぢ'
        'つ', 'っ' -> 'づ'
        'て' -> 'で'
        'と' -> 'ど'
        'は', 'ぱ' -> 'ば'
        'ひ', 'ぴ' -> 'び'
        'ふ', 'ぷ' -> 'ぶ'
        'へ', 'ぺ' -> 'べ'
        'ほ', 'ぽ' -> 'ぼ'
        else -> null
    }
}

/** 半濁点 **/
fun Char.getDakutenFlickRight(): Char? {
    return when (this) {
        'は', 'ば' -> 'ぱ'
        'ひ', 'び' -> 'ぴ'
        'ふ', 'ぶ' -> 'ぷ'
        'へ', 'べ' -> 'ぺ'
        'ほ', 'ぼ' -> 'ぽ'
        else -> null
    }
}

/** 小文字 **/
fun Char.getDakutenFlickTop(): Char? {
    return when (this) {
        'あ' -> 'ぁ'
        'い' -> 'ぃ'
        'う', 'ゔ' -> 'ぅ'
        'え' -> 'ぇ'
        'お' -> 'ぉ'
        'つ', 'づ' -> 'っ'
        'や' -> 'ゃ'
        'ゆ' -> 'ゅ'
        'よ' -> 'ょ'
        'わ' -> 'ゎ'
        else -> null
    }
}

fun Char.isHiragana(): Boolean {
    val excludedHiragana = listOf(
        'な', 'に', 'ぬ', 'ね', 'の',
        'ま', 'み', 'む', 'め', 'も',
        'ら', 'り', 'る', 'れ', 'ろ',
        'を', 'ん'
    )
    return this in '\u3040'..'\u309F' && this !in excludedHiragana
}

fun Char.isLatinAlphabet(): Boolean {
    return this in 'A'..'Z' || this in 'a'..'z'
}
