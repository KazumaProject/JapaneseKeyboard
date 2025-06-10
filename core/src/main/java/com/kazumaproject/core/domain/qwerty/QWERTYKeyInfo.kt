package com.kazumaproject.core.domain.qwerty

sealed class QWERTYKeyInfo {
    data object Null : QWERTYKeyInfo()

    abstract class QWERTYVariation : QWERTYKeyInfo() {
        abstract val tap: Char?
        abstract val capChar: Char?
        abstract val variations: List<Char>?
        abstract val capVariations: List<Char>?
    }

    object KeyA : QWERTYVariation() {
        override val tap: Char get() = 'a'
        override val capChar: Char get() = 'A'
        override val variations: List<Char> get() = listOf('à', 'á', 'â', 'æ', 'ã', 'å', 'ā')
        override val capVariations: List<Char> get() = listOf('À', 'Á', 'Â', 'Æ', 'Ã', 'Å', 'Ā')
    }

    object KeyB : QWERTYVariation() {
        override val tap: Char get() = 'b'
        override val capChar: Char get() = 'B'
        override val variations: List<Char> = listOf('ƀ', 'ɓ')
        override val capVariations: List<Char> = listOf('Ɓ', 'Ƀ')
    }

    object KeyC : QWERTYVariation() {
        override val tap: Char get() = 'c'
        override val capChar: Char get() = 'C'
        override val variations: List<Char> get() = listOf('ç', 'ć', 'č', 'ĉ', 'ċ')
        override val capVariations: List<Char> get() = listOf('Ç', 'Ć', 'Č', 'Ĉ', 'Ċ')
    }

    object KeyD : QWERTYVariation() {
        override val tap: Char get() = 'd'
        override val capChar: Char get() = 'D'
        override val variations: List<Char> get() = listOf('ď', 'đ')
        override val capVariations: List<Char> get() = listOf('Ď', 'Đ')
    }

    object KeyE : QWERTYVariation() {
        override val tap: Char get() = 'e'
        override val capChar: Char get() = 'E'
        override val variations: List<Char> get() = listOf('è', 'é', 'ê', 'ë', 'ē', 'ė', 'ę', 'ě')
        override val capVariations: List<Char>
            get() = listOf(
                'È',
                'É',
                'Ê',
                'Ë',
                'Ē',
                'Ė',
                'Ę',
                'Ě'
            )
    }

    object KeyF : QWERTYVariation() {
        override val tap: Char get() = 'f'
        override val capChar: Char get() = 'F'
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyG : QWERTYVariation() {
        override val tap: Char get() = 'g'
        override val capChar: Char get() = 'G'
        override val variations: List<Char> get() = listOf('ĝ', 'ğ', 'ġ', 'ģ')
        override val capVariations: List<Char> get() = listOf('Ĝ', 'Ğ', 'Ġ', 'Ģ')
    }

    object KeyH : QWERTYVariation() {
        override val tap: Char get() = 'h'
        override val capChar: Char get() = 'H'
        override val variations: List<Char> get() = listOf('ĥ', 'ħ')
        override val capVariations: List<Char> get() = listOf('Ĥ', 'Ħ')
    }

    object KeyI : QWERTYVariation() {
        override val tap: Char get() = 'i'
        override val capChar: Char get() = 'I'
        override val variations: List<Char> get() = listOf('ì', 'í', 'î', 'ï', 'ī', 'į', 'ǐ', 'ı')
        override val capVariations: List<Char>
            get() = listOf(
                'Ì',
                'Í',
                'Î',
                'Ï',
                'Ī',
                'Į',
                'Ǐ',
                'İ'
            )
    }

    object KeyJ : QWERTYVariation() {
        override val tap: Char get() = 'j'
        override val capChar: Char get() = 'J'
        override val variations: List<Char> get() = listOf('ĵ')
        override val capVariations: List<Char> get() = listOf('Ĵ')
    }

    object KeyK : QWERTYVariation() {
        override val tap: Char get() = 'k'
        override val capChar: Char get() = 'K'
        override val variations: List<Char> get() = listOf('ķ')
        override val capVariations: List<Char> get() = listOf('Ķ')
    }

    object KeyL : QWERTYVariation() {
        override val tap: Char get() = 'l'
        override val capChar: Char get() = 'L'
        override val variations: List<Char> get() = listOf('ĺ', 'ļ', 'ľ', 'ł')
        override val capVariations: List<Char> get() = listOf('Ĺ', 'Ļ', 'Ľ', 'Ł')
    }

    object KeyM : QWERTYVariation() {
        override val tap: Char get() = 'm'
        override val capChar: Char get() = 'M'
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyN : QWERTYVariation() {
        override val tap: Char get() = 'n'
        override val capChar: Char get() = 'N'
        override val variations: List<Char> get() = listOf('ñ', 'ń', 'ņ', 'ň')
        override val capVariations: List<Char> get() = listOf('Ñ', 'Ń', 'Ņ', 'Ň')
    }

    object KeyO : QWERTYVariation() {
        override val tap: Char get() = 'o'
        override val capChar: Char get() = 'O'
        override val variations: List<Char> get() = listOf('ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō', 'ő')
        override val capVariations: List<Char>
            get() = listOf(
                'Ò',
                'Ó',
                'Ô',
                'Õ',
                'Ö',
                'Ø',
                'Ō',
                'Ő'
            )
    }

    object KeyP : QWERTYVariation() {
        override val tap: Char get() = 'p'
        override val capChar: Char get() = 'P'
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyQ : QWERTYVariation() {
        override val tap: Char get() = 'q'
        override val capChar: Char get() = 'Q'
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyR : QWERTYVariation() {
        override val tap: Char get() = 'r'
        override val capChar: Char get() = 'R'
        override val variations: List<Char> get() = listOf('ŕ', 'ř')
        override val capVariations: List<Char> get() = listOf('Ŕ', 'Ř')
    }

    object KeyS : QWERTYVariation() {
        override val tap: Char get() = 's'
        override val capChar: Char get() = 'S'
        override val variations: List<Char> get() = listOf('ś', 'š', 'ş', 'ș')
        override val capVariations: List<Char> get() = listOf('Ś', 'Š', 'Ş', 'Ș')
    }

    object KeyT : QWERTYVariation() {
        override val tap: Char get() = 't'
        override val capChar: Char get() = 'T'
        override val variations: List<Char> get() = listOf('ţ', 'ť', 'ț')
        override val capVariations: List<Char> get() = listOf('Ţ', 'Ť', 'Ț')
    }

    object KeyU : QWERTYVariation() {
        override val tap: Char get() = 'u'
        override val capChar: Char get() = 'U'
        override val variations: List<Char> get() = listOf('ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ů', 'ű')
        override val capVariations: List<Char>
            get() = listOf(
                'Ù',
                'Ú',
                'Û',
                'Ü',
                'Ũ',
                'Ū',
                'Ů',
                'Ű'
            )
    }

    object KeyV : QWERTYVariation() {
        override val tap: Char get() = 'v'
        override val capChar: Char get() = 'V'
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyW : QWERTYVariation() {
        override val tap: Char get() = 'w'
        override val capChar: Char get() = 'W'
        override val variations: List<Char> get() = listOf('ŵ')
        override val capVariations: List<Char> get() = listOf('Ŵ')
    }

    object KeyX : QWERTYVariation() {
        override val tap: Char get() = 'x'
        override val capChar: Char get() = 'X'
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyY : QWERTYVariation() {
        override val tap: Char get() = 'y'
        override val capChar: Char get() = 'Y'
        override val variations: List<Char> get() = listOf('ý', 'ÿ', 'ŷ')
        override val capVariations: List<Char> get() = listOf('Ý', 'Ÿ', 'Ŷ')
    }

    object KeyZ : QWERTYVariation() {
        override val tap: Char get() = 'z'
        override val capChar: Char get() = 'Z'
        override val variations: List<Char> get() = listOf('ź', 'ž', 'ż')
        override val capVariations: List<Char> get() = listOf('Ź', 'Ž', 'Ż')
    }

    object Key1 : QWERTYVariation() {
        override val tap: Char get() = '1'
        override val capChar: Char? get() = null
        override val variations: List<Char>? = null
        override val capVariations: List<Char>? = null
    }

    object Key2 : QWERTYVariation() {
        override val tap: Char get() = '2'
        override val capChar: Char? get() = null
        override val variations: List<Char>? = null
        override val capVariations: List<Char>? = null
    }

    object Key3 : QWERTYVariation() {
        override val tap: Char get() = '3'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object Key4 : QWERTYVariation() {
        override val tap: Char get() = '4'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object Key5 : QWERTYVariation() {
        override val tap: Char get() = '5'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object Key6 : QWERTYVariation() {
        override val tap: Char get() = '6'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object Key7 : QWERTYVariation() {
        override val tap: Char get() = '7'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object Key8 : QWERTYVariation() {
        override val tap: Char get() = '8'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object Key9 : QWERTYVariation() {
        override val tap: Char get() = '9'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object Key0 : QWERTYVariation() {
        override val tap: Char get() = '0'
        override val capChar: Char? get() = null
        override val variations: List<Char>? = null
        override val capVariations: List<Char>? = null
    }

    object KeyMinus : QWERTYVariation() {
        override val tap: Char get() = '-'
        override val capChar: Char? get() = null
        override val variations: List<Char>? = null
        override val capVariations: List<Char>? = null
    }

    object KeyPlus : QWERTYVariation() {
        override val tap: Char get() = '+'
        override val capChar: Char? get() = null
        override val variations: List<Char>? = null
        override val capVariations: List<Char>? = null
    }

    object KeyColon : QWERTYVariation() {
        override val tap: Char get() = ':'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeySemicolon : QWERTYVariation() {
        override val tap: Char get() = ';'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyParenOpen : QWERTYVariation() {
        override val tap: Char get() = '('
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyParenClose : QWERTYVariation() {
        override val tap: Char get() = ')'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyYen : QWERTYVariation() {
        override val tap: Char get() = '¥'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyAmpersand : QWERTYVariation() {
        override val tap: Char get() = '&'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyAtMark : QWERTYVariation() {
        override val tap: Char get() = '@'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyQuote : QWERTYVariation() {
        override val tap: Char get() = '\"'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyDot : QWERTYVariation() {
        override val tap: Char get() = '.'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyComma : QWERTYVariation() {
        override val tap: Char get() = ','
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyQuestion : QWERTYVariation() {
        override val tap: Char get() = '?'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyExclamation : QWERTYVariation() {
        override val tap: Char get() = '!'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyApostrophe : QWERTYVariation() {
        override val tap: Char get() = '\''
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyBracketOpen : QWERTYVariation() {
        override val tap: Char get() = '['
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyBracketClose : QWERTYVariation() {
        override val tap: Char get() = ']'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyBraceOpen : QWERTYVariation() {
        override val tap: Char get() = '{'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyBraceClose : QWERTYVariation() {
        override val tap: Char get() = '}'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyHash : QWERTYVariation() {
        override val tap: Char get() = '#'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyPercent : QWERTYVariation() {
        override val tap: Char get() = '%'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyCaret : QWERTYVariation() {
        override val tap: Char get() = '^'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyAsterisk : QWERTYVariation() {
        override val tap: Char get() = '*'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyEqual : QWERTYVariation() {
        override val tap: Char get() = '='
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyUnderscore : QWERTYVariation() {
        override val tap: Char get() = '_'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeySlash : QWERTYVariation() {
        override val tap: Char get() = '/'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyBackslash : QWERTYVariation() {
        override val tap: Char get() = '\\'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyTilde : QWERTYVariation() {
        override val tap: Char get() = '~'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyLessThan : QWERTYVariation() {
        override val tap: Char get() = '<'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyGreaterThan : QWERTYVariation() {
        override val tap: Char get() = '>'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyDollar : QWERTYVariation() {
        override val tap: Char get() = '$'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyEuro : QWERTYVariation() {
        override val tap: Char get() = '€'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyPound : QWERTYVariation() {
        override val tap: Char get() = '£'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyMiddleDot : QWERTYVariation() {
        override val tap: Char get() = '·'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyShift : QWERTYVariation() {
        override val tap: Char? get() = null
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyDelete : QWERTYVariation() {
        override val tap: Char? get() = null
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeySwitchDefaultLayout : QWERTYVariation() {
        override val tap: Char? get() = null
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeySwitchMode : QWERTYVariation() {
        override val tap: Char? get() = null
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeySpace : QWERTYVariation() {
        override val tap: Char? get() = null
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyReturn : QWERTYVariation() {
        override val tap: Char? get() = null
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyBracketLeftJP : QWERTYVariation() {
        override val tap: Char get() = '「'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyBracketRightJP : QWERTYVariation() {
        override val tap: Char get() = '」'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyVerticalBarJP : QWERTYVariation() {
        override val tap: Char get() = '|'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyDotJP : QWERTYVariation() {
        override val tap: Char get() = '。'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

    object KeyCommaJP : QWERTYVariation() {
        override val tap: Char get() = '、'
        override val capChar: Char? get() = null
        override val variations: List<Char>? get() = null
        override val capVariations: List<Char>? get() = null
    }

}
