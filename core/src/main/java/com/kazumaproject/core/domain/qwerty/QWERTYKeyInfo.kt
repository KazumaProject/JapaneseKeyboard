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
        override val variations: List<Char>
            get() = listOf(
                '@',
                'a',
                'à',
                'á',
                'â',
                'æ',
                'ã',
                'å',
                'ā',
                'A',
            )
        override val capVariations: List<Char>
            get() = listOf(
                '@', 'A', 'À', 'Á', 'Â', 'Æ', 'Ã', 'Å', 'Ā'
            )
    }

    object KeyB : QWERTYVariation() {
        override val tap: Char get() = 'b'
        override val capChar: Char get() = 'B'
        override val variations: List<Char> = listOf(';')
        override val capVariations: List<Char> = listOf(';')
    }

    object KeyC : QWERTYVariation() {
        override val tap: Char get() = 'c'
        override val capChar: Char get() = 'C'
        override val variations: List<Char>
            get() = listOf(
                '\'',
                'ç',
            )
        override val capVariations: List<Char>
            get() = listOf(
                '\"', 'Ç',
            )
    }

    object KeyD : QWERTYVariation() {
        override val tap: Char get() = 'd'
        override val capChar: Char get() = 'D'
        override val variations: List<Char> get() = listOf('$')
        override val capVariations: List<Char> get() = listOf('$')
    }

    object KeyE : QWERTYVariation() {
        override val tap: Char get() = 'e'
        override val capChar: Char get() = 'E'
        override val variations: List<Char>
            get() = listOf(
                'ē',
                'ê',
                'ë',
                '3',
                'è',
                'é',
            )
        override val capVariations: List<Char>
            get() = listOf(
                'Ē',
                'Ê',
                'Ë',
                '3',
                'È',
                'É',
            )
    }

    object KeyF : QWERTYVariation() {
        override val tap: Char get() = 'f'
        override val capChar: Char get() = 'F'
        override val variations: List<Char> get() = listOf('_')
        override val capVariations: List<Char> get() = listOf('_')
    }

    object KeyG : QWERTYVariation() {
        override val tap: Char get() = 'g'
        override val capChar: Char get() = 'G'
        override val variations: List<Char> get() = listOf('&')
        override val capVariations: List<Char> get() = listOf('&')
    }

    object KeyH : QWERTYVariation() {
        override val tap: Char get() = 'h'
        override val capChar: Char get() = 'H'
        override val variations: List<Char> get() = listOf('-')
        override val capVariations: List<Char> get() = listOf('-')
    }

    object KeyI : QWERTYVariation() {
        override val tap: Char get() = 'i'
        override val capChar: Char get() = 'I'
        override val variations: List<Char>
            get() = listOf(
                'ì', 'ï', 'ī', 'î', 'í', '8',
            )
        override val capVariations: List<Char>
            get() = listOf(
                'Ì', 'Ï', 'Ī', 'Î', 'Í', '8'
            )
    }

    object KeyJ : QWERTYVariation() {
        override val tap: Char get() = 'j'
        override val capChar: Char get() = 'J'
        override val variations: List<Char> get() = listOf('+')
        override val capVariations: List<Char> get() = listOf('+')
    }

    object KeyK : QWERTYVariation() {
        override val tap: Char get() = 'k'
        override val capChar: Char get() = 'K'
        override val variations: List<Char> get() = listOf('(')
        override val capVariations: List<Char> get() = listOf('(')
    }

    object KeyL : QWERTYVariation() {
        override val tap: Char get() = 'l'
        override val capChar: Char get() = 'L'
        override val variations: List<Char> get() = listOf(')')
        override val capVariations: List<Char> get() = listOf(')')
    }

    object KeyM : QWERTYVariation() {
        override val tap: Char get() = 'm'
        override val capChar: Char get() = 'M'
        override val variations: List<Char> get() = listOf('?')
        override val capVariations: List<Char> get() = listOf('?')
    }

    object KeyN : QWERTYVariation() {
        override val tap: Char get() = 'n'
        override val capChar: Char get() = 'N'
        override val variations: List<Char> get() = listOf('!', 'ñ')
        override val capVariations: List<Char> get() = listOf('!', 'Ñ')
    }

    object KeyO : QWERTYVariation() {
        override val tap: Char get() = 'o'
        override val capChar: Char get() = 'O'
        override val variations: List<Char>
            get() = listOf(
                'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō', 'ő', '9', 'O'
            )
        override val capVariations: List<Char>
            get() = listOf(
                'Ò', 'Ó', 'Ô', 'Õ', 'Ö', 'Ø', 'Ō', 'Ő', '9'
            )
    }

    object KeyP : QWERTYVariation() {
        override val tap: Char get() = 'p'
        override val capChar: Char get() = 'P'
        override val variations: List<Char> get() = listOf('0')
        override val capVariations: List<Char> get() = listOf('0')
    }

    object KeyQ : QWERTYVariation() {
        override val tap: Char get() = 'q'
        override val capChar: Char get() = 'Q'
        override val variations: List<Char> get() = listOf('1')
        override val capVariations: List<Char> get() = listOf('1')
    }

    object KeyR : QWERTYVariation() {
        override val tap: Char get() = 'r'
        override val capChar: Char get() = 'R'
        override val variations: List<Char> get() = listOf('4')
        override val capVariations: List<Char> get() = listOf('4')
    }

    object KeyS : QWERTYVariation() {
        override val tap: Char get() = 's'
        override val capChar: Char get() = 'S'
        override val variations: List<Char> get() = listOf('#', 'β')
        override val capVariations: List<Char> get() = listOf('#', 'β')
    }

    object KeyT : QWERTYVariation() {
        override val tap: Char get() = 't'
        override val capChar: Char get() = 'T'
        override val variations: List<Char> get() = listOf('5')
        override val capVariations: List<Char> get() = listOf('5')
    }

    object KeyU : QWERTYVariation() {
        override val tap: Char get() = 'u'
        override val capChar: Char get() = 'U'
        override val variations: List<Char>
            get() = listOf(
                'ū', 'ü', 'ù', 'ú', 'û', '7',
            )
        override val capVariations: List<Char>
            get() = listOf(
                'Ū', 'Ü', 'Ù', 'Ú', 'Û', '7'
            )
    }

    object KeyV : QWERTYVariation() {
        override val tap: Char get() = 'v'
        override val capChar: Char get() = 'V'
        override val variations: List<Char> get() = listOf(':')
        override val capVariations: List<Char> get() = listOf(':')
    }

    object KeyW : QWERTYVariation() {
        override val tap: Char get() = 'w'
        override val capChar: Char get() = 'W'
        override val variations: List<Char> get() = listOf('2')
        override val capVariations: List<Char> get() = listOf('2')
    }

    object KeyX : QWERTYVariation() {
        override val tap: Char get() = 'x'
        override val capChar: Char get() = 'X'
        override val variations: List<Char> get() = listOf('"')
        override val capVariations: List<Char> get() = listOf('"')
    }

    object KeyY : QWERTYVariation() {
        override val tap: Char get() = 'y'
        override val capChar: Char get() = 'Y'
        override val variations: List<Char> get() = listOf('6')
        override val capVariations: List<Char> get() = listOf('6')
    }

    object KeyZ : QWERTYVariation() {
        override val tap: Char get() = 'z'
        override val capChar: Char get() = 'Z'
        override val variations: List<Char> get() = listOf('*')
        override val capVariations: List<Char> get() = listOf('*')
    }

    object Key1 : QWERTYVariation() {
        override val tap: Char get() = '1'
        override val capChar: Char? get() = null
        override val variations: List<Char> =
            listOf('1', '¹', '¼', '½', '⅐', '⅑', '⅒', '⅓', '⅕', '⅙', '⅛')
        override val capVariations: List<Char>? = null
    }

    object Key2 : QWERTYVariation() {
        override val tap: Char get() = '2'
        override val capChar: Char? get() = null
        override val variations: List<Char> = listOf(
            '2', '²', '⅔', // 2/3
            '⅖'  // 2/5
        )
        override val capVariations: List<Char>? = null
    }

    object Key3 : QWERTYVariation() {
        override val tap: Char get() = '3'
        override val capChar: Char? get() = null
        override val variations: List<Char>
            get() = listOf(
                '3', '³', '¾', // 3/4
                '⅗', // 3/5
                '⅜'  // 3/8
            )
        override val capVariations: List<Char>? get() = null
    }

    object Key4 : QWERTYVariation() {
        override val tap: Char get() = '4'
        override val capChar: Char? get() = null
        override val variations: List<Char>
            get() = listOf(
                '4', '⁴', '⅘'  // 4/5
            )
        override val capVariations: List<Char>? get() = null
    }

    object Key5 : QWERTYVariation() {
        override val tap: Char get() = '5'
        override val capChar: Char? get() = null
        override val variations: List<Char>
            get() = listOf(
                '5', '⁵', '⅚', // 5/6
                '⅝'  // 5/8
            )
        override val capVariations: List<Char>? get() = null
    }

    object Key6 : QWERTYVariation() {
        override val tap: Char get() = '6'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('6', '⁶')
        override val capVariations: List<Char>? get() = null
    }

    object Key7 : QWERTYVariation() {
        override val tap: Char get() = '7'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('7', '⁷', '⅞')
        override val capVariations: List<Char>? get() = null
    }

    object Key8 : QWERTYVariation() {
        override val tap: Char get() = '8'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('8', '⁸')
        override val capVariations: List<Char>? get() = null
    }

    object Key9 : QWERTYVariation() {
        override val tap: Char get() = '9'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('9', '⁹')
        override val capVariations: List<Char>? get() = null
    }

    object Key0 : QWERTYVariation() {
        override val tap: Char get() = '0'
        override val capChar: Char? get() = null
        override val variations: List<Char> = listOf('0', '⁰', 'ⁿ', '∅')
        override val capVariations: List<Char>? = null
    }

    object Key1JP : QWERTYVariation() {
        override val tap: Char get() = '1'
        override val capChar: Char? get() = null
        override val variations: List<Char> =
            listOf('1', '１', '¹', '¼', '½', '⅐', '⅑', '⅒', '⅓', '⅕', '⅙', '⅛')
        override val capVariations: List<Char>? = null
    }

    object Key2JP : QWERTYVariation() {
        override val tap: Char get() = '2'
        override val capChar: Char? get() = null
        override val variations: List<Char> = listOf(
            '2', '２', '²', '⅔', // 2/3
            '⅖'  // 2/5
        )
        override val capVariations: List<Char>? = null
    }

    object Key3JP : QWERTYVariation() {
        override val tap: Char get() = '3'
        override val capChar: Char? get() = null
        override val variations: List<Char>
            get() = listOf(
                '3', '３', '³', '¾', // 3/4
                '⅗', // 3/5
                '⅜'  // 3/8
            )
        override val capVariations: List<Char>? get() = null
    }

    object Key4JP : QWERTYVariation() {
        override val tap: Char get() = '4'
        override val capChar: Char? get() = null
        override val variations: List<Char>
            get() = listOf(
                '4', '４', '⁴', '⅘'  // 4/5
            )
        override val capVariations: List<Char>? get() = null
    }

    object Key5JP : QWERTYVariation() {
        override val tap: Char get() = '5'
        override val capChar: Char? get() = null
        override val variations: List<Char>
            get() = listOf(
                '5', '５', '⁵', '⅚', // 5/6
                '⅝'  // 5/8
            )
        override val capVariations: List<Char>? get() = null
    }

    object Key6JP : QWERTYVariation() {
        override val tap: Char get() = '6'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('6', '６', '⁶')
        override val capVariations: List<Char>? get() = null
    }

    object Key7JP : QWERTYVariation() {
        override val tap: Char get() = '7'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('7', '７', '⁷', '⅞')
        override val capVariations: List<Char>? get() = null
    }

    object Key8JP : QWERTYVariation() {
        override val tap: Char get() = '8'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('8', '８', '⁸')
        override val capVariations: List<Char>? get() = null
    }

    object Key9JP : QWERTYVariation() {
        override val tap: Char get() = '9'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('9', '９', '⁹')
        override val capVariations: List<Char>? get() = null
    }

    object Key0JP : QWERTYVariation() {
        override val tap: Char get() = '0'
        override val capChar: Char? get() = null
        override val variations: List<Char> = listOf('0', '０', '⁰', 'ⁿ', '∅')
        override val capVariations: List<Char>? = null
    }

    object KeyMinus : QWERTYVariation() {
        override val tap: Char get() = '-'
        override val capChar: Char? get() = null
        override val variations: List<Char> = listOf('-', '—', '_', '–', '·')
        override val capVariations: List<Char>? = null
    }

    object KeyPlus : QWERTYVariation() {
        override val tap: Char get() = '+'
        override val capChar: Char? get() = null
        override val variations: List<Char> = listOf('+', '±')
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
        override val variations: List<Char> get() = listOf('(', '[', '<', '{')
        override val capVariations: List<Char>? get() = null
    }

    object KeyParenClose : QWERTYVariation() {
        override val tap: Char get() = ')'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf(')', ']', '}', '>')
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
        override val variations: List<Char> get() = listOf('\"', '„', '“', '”', '«', '»')
        override val capVariations: List<Char>? get() = null
    }

    object KeyDot : QWERTYVariation() {
        override val tap: Char get() = '.'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('.', '…')
        override val capVariations: List<Char>? get() = null
    }

    object KeyComma : QWERTYVariation() {
        override val tap: Char get() = ','
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('、', '©', '®', '™', '¶', '§', '∆')
        override val capVariations: List<Char>? get() = null
    }

    object KeyQuestion : QWERTYVariation() {
        override val tap: Char get() = '?'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('?', '¿', '‽')
        override val capVariations: List<Char>? get() = null
    }

    object KeyExclamation : QWERTYVariation() {
        override val tap: Char get() = '!'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('!', '¡')
        override val capVariations: List<Char>? get() = null
    }

    object KeyApostrophe : QWERTYVariation() {
        override val tap: Char get() = '\''
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('\'', '`', '‚', '‘', '’', '‹', '›')
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
        override val variations: List<Char> get() = listOf('#', '№')
        override val capVariations: List<Char>? get() = null
    }

    object KeyPercent : QWERTYVariation() {
        override val tap: Char get() = '%'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('%', '‰', '℅')
        override val capVariations: List<Char>? get() = null
    }

    object KeyCaret : QWERTYVariation() {
        override val tap: Char get() = '^'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('^', '←', '↑', '↓', '→')
        override val capVariations: List<Char>? get() = null
    }

    object KeyAsterisk : QWERTYVariation() {
        override val tap: Char get() = '*'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('*', '×', '★', '†', '‡')
        override val capVariations: List<Char>? get() = null
    }

    object KeyEqual : QWERTYVariation() {
        override val tap: Char get() = '='
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('=', '∞', '≠', '≈')
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
        override val variations: List<Char> get() = listOf('/', '÷', '√', '✓')
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
        override val variations: List<Char> get() = listOf('$', '₹', '₱', '€', '¢', '£')
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

    object KeyAJP : QWERTYVariation() {
        override val tap: Char get() = 'a'
        override val capChar: Char get() = 'A'
        override val variations: List<Char> get() = listOf('@')
        override val capVariations: List<Char> get() = listOf('@')
    }

    object KeyBJP : QWERTYVariation() {
        override val tap: Char get() = 'b'
        override val capChar: Char get() = 'B'
        override val variations: List<Char> get() = listOf(';')
        override val capVariations: List<Char> get() = listOf(';')
    }

    object KeyCJP : QWERTYVariation() {
        override val tap: Char get() = 'c'
        override val capChar: Char get() = 'C'
        override val variations: List<Char> get() = listOf('\'')
        override val capVariations: List<Char> get() = listOf('\'')
    }

    object KeyDJP : QWERTYVariation() {
        override val tap: Char get() = 'd'
        override val capChar: Char get() = 'D'
        override val variations: List<Char> get() = listOf('¥')
        override val capVariations: List<Char> get() = listOf('¥')
    }

    object KeyEJP : QWERTYVariation() {
        override val tap: Char get() = 'e'
        override val capChar: Char get() = 'E'
        override val variations: List<Char> get() = listOf('3')
        override val capVariations: List<Char> get() = listOf('3')
    }

    object KeyFJP : QWERTYVariation() {
        override val tap: Char get() = 'f'
        override val capChar: Char get() = 'F'
        override val variations: List<Char> get() = listOf('_')
        override val capVariations: List<Char> get() = listOf('_')
    }

    object KeyGJP : QWERTYVariation() {
        override val tap: Char get() = 'g'
        override val capChar: Char get() = 'G'
        override val variations: List<Char> get() = listOf('&')
        override val capVariations: List<Char> get() = listOf('&')
    }

    object KeyHJP : QWERTYVariation() {
        override val tap: Char get() = 'h'
        override val capChar: Char get() = 'H'
        override val variations: List<Char> get() = listOf('-')
        override val capVariations: List<Char> get() = listOf('-')
    }

    object KeyIJP : QWERTYVariation() {
        override val tap: Char get() = 'i'
        override val capChar: Char get() = 'I'
        override val variations: List<Char> get() = listOf('8')
        override val capVariations: List<Char> get() = listOf('8')
    }

    object KeyJJP : QWERTYVariation() {
        override val tap: Char get() = 'j'
        override val capChar: Char get() = 'J'
        override val variations: List<Char> get() = listOf('+')
        override val capVariations: List<Char> get() = listOf('+')
    }

    object KeyKJP : QWERTYVariation() {
        override val tap: Char get() = 'k'
        override val capChar: Char get() = 'K'
        override val variations: List<Char> get() = listOf('(')
        override val capVariations: List<Char> get() = listOf('(')
    }

    object KeyLJP : QWERTYVariation() {
        override val tap: Char get() = 'l'
        override val capChar: Char get() = 'L'
        override val variations: List<Char> get() = listOf(')')
        override val capVariations: List<Char> get() = listOf(')')
    }

    object KeyMJP : QWERTYVariation() {
        override val tap: Char get() = 'm'
        override val capChar: Char get() = 'M'
        override val variations: List<Char> get() = listOf('?')
        override val capVariations: List<Char> get() = listOf('?')
    }

    object KeyNJP : QWERTYVariation() {
        override val tap: Char get() = 'n'
        override val capChar: Char get() = 'N'
        override val variations: List<Char> get() = listOf('!')
        override val capVariations: List<Char> get() = listOf('!')
    }

    object KeyOJP : QWERTYVariation() {
        override val tap: Char get() = 'o'
        override val capChar: Char get() = 'O'
        override val variations: List<Char> get() = listOf('9')
        override val capVariations: List<Char> get() = listOf('9')
    }

    object KeyPJP : QWERTYVariation() {
        override val tap: Char get() = 'p'
        override val capChar: Char get() = 'P'
        override val variations: List<Char> get() = listOf('0')
        override val capVariations: List<Char> get() = listOf('0')
    }

    object KeyQJP : QWERTYVariation() {
        override val tap: Char get() = 'q'
        override val capChar: Char get() = 'Q'
        override val variations: List<Char> get() = listOf('1')
        override val capVariations: List<Char> get() = listOf('1')
    }

    object KeyRJP : QWERTYVariation() {
        override val tap: Char get() = 'r'
        override val capChar: Char get() = 'R'
        override val variations: List<Char> get() = listOf('4')
        override val capVariations: List<Char> get() = listOf('4')
    }

    object KeySJP : QWERTYVariation() {
        override val tap: Char get() = 's'
        override val capChar: Char get() = 'S'
        override val variations: List<Char> get() = listOf('#')
        override val capVariations: List<Char> get() = listOf('#')
    }

    object KeyTJP : QWERTYVariation() {
        override val tap: Char get() = 't'
        override val capChar: Char get() = 'T'
        override val variations: List<Char> get() = listOf('5')
        override val capVariations: List<Char> get() = listOf('5')
    }

    object KeyUJP : QWERTYVariation() {
        override val tap: Char get() = 'u'
        override val capChar: Char get() = 'U'
        override val variations: List<Char> get() = listOf('7')
        override val capVariations: List<Char> get() = listOf('7')
    }

    object KeyVJP : QWERTYVariation() {
        override val tap: Char get() = 'v'
        override val capChar: Char get() = 'V'
        override val variations: List<Char> get() = listOf(':')
        override val capVariations: List<Char> get() = listOf(':')
    }

    object KeyWJP : QWERTYVariation() {
        override val tap: Char get() = 'w'
        override val capChar: Char get() = 'W'
        override val variations: List<Char> get() = listOf('2')
        override val capVariations: List<Char> get() = listOf('2')
    }

    object KeyXJP : QWERTYVariation() {
        override val tap: Char get() = 'x'
        override val capChar: Char get() = 'X'
        override val variations: List<Char> get() = listOf('"')
        override val capVariations: List<Char> get() = listOf('"')
    }

    object KeyYJP : QWERTYVariation() {
        override val tap: Char get() = 'y'
        override val capChar: Char get() = 'Y'
        override val variations: List<Char> get() = listOf('6')
        override val capVariations: List<Char> get() = listOf('6')
    }

    object KeyZJP : QWERTYVariation() {
        override val tap: Char get() = 'z'
        override val capChar: Char get() = 'Z'
        override val variations: List<Char> get() = listOf('*')
        override val capVariations: List<Char> get() = listOf('*')
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
        override val variations: List<Char>
            get() = listOf(
                '。',
                '&',
                '%',
                '"',
                '”',
                '\'',
                '@',
                '-',
                '+',
                ';',
                '/',
                ',',
                '#',
            )
        override val capVariations: List<Char>? get() = null
    }

    object KeyCommaJP : QWERTYVariation() {
        override val tap: Char get() = '、'
        override val capChar: Char? get() = null
        override val variations: List<Char> get() = listOf('、', '©', '®', '™', '¶', '§', '∆')
        override val capVariations: List<Char>? get() = null
    }

    object KeyMinusJP : QWERTYVariation() {
        override val tap: Char get() = 'ー'
        override val capChar: Char get() = 'ー'
        override val variations: List<Char> get() = listOf('/')
        override val capVariations: List<Char> get() = listOf('/')
    }

    object KeyQNumber : QWERTYVariation() {
        override val tap: Char get() = 'q'
        override val capChar: Char get() = 'Q'
        override val variations: List<Char> get() = listOf('%')
        override val capVariations: List<Char> get() = listOf('%')
    }

    object KeyWNumber : QWERTYVariation() {
        override val tap: Char get() = 'w'
        override val capChar: Char get() = 'W'
        override val variations: List<Char> get() = listOf('\\')
        override val capVariations: List<Char> get() = listOf('\\')
    }

    object KeyENumber : QWERTYVariation() {
        override val tap: Char get() = 'e'
        override val capChar: Char get() = 'E'
        override val variations: List<Char> get() = listOf('|')
        override val capVariations: List<Char> get() = listOf('|')
    }

    object KeyRNumber : QWERTYVariation() {
        override val tap: Char get() = 'r'
        override val capChar: Char get() = 'R'
        override val variations: List<Char> get() = listOf('=')
        override val capVariations: List<Char> get() = listOf('=')
    }

    object KeyTNumber : QWERTYVariation() {
        override val tap: Char get() = 't'
        override val capChar: Char get() = 'T'
        override val variations: List<Char> get() = listOf('[')
        override val capVariations: List<Char> get() = listOf('[')
    }

    object KeyYNumber : QWERTYVariation() {
        override val tap: Char get() = 'y'
        override val capChar: Char get() = 'Y'
        override val variations: List<Char> get() = listOf(']')
        override val capVariations: List<Char> get() = listOf(']')
    }

    object KeyUNumber : QWERTYVariation() {
        override val tap: Char get() = 'u'
        override val capChar: Char get() = 'U'
        override val variations: List<Char> get() = listOf('<')
        override val capVariations: List<Char> get() = listOf('<')
    }

    object KeyINumber : QWERTYVariation() {
        override val tap: Char get() = 'i'
        override val capChar: Char get() = 'I'
        override val variations: List<Char> get() = listOf('>')
        override val capVariations: List<Char> get() = listOf('>')
    }

    object KeyONumber : QWERTYVariation() {
        override val tap: Char get() = 'o'
        override val capChar: Char get() = 'O'
        override val variations: List<Char> get() = listOf('{')
        override val capVariations: List<Char> get() = listOf('{')
    }

    object KeyPNumber : QWERTYVariation() {
        override val tap: Char get() = 'p'
        override val capChar: Char get() = 'P'
        override val variations: List<Char> get() = listOf('}')
        override val capVariations: List<Char> get() = listOf('}')
    }

}
