package com.kazumaproject.markdownhelperkeyboard.ime_service.components

sealed class TenKeyInfo{

    object Null : TenKeyInfo()

    abstract class TenKeyTapFlickInfo : TenKeyInfo() {
        abstract val tap: Char
        abstract val flickLeft: Char
        abstract val flickTop: Char
        abstract val flickRight: Char
        abstract val flickBottom: Char
    }

    object KeyAJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'あ'
        override val flickLeft: Char
            get() = 'い'
        override val flickTop: Char
            get() = 'う'
        override val flickRight: Char
            get() = 'え'
        override val flickBottom: Char
            get() = 'お'
    }

    object KeyKAJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'か'
        override val flickLeft: Char
            get() = 'き'
        override val flickTop: Char
            get() = 'く'
        override val flickRight: Char
            get() = 'け'
        override val flickBottom: Char
            get() = 'こ'
    }

    object KeySAJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'さ'
        override val flickLeft: Char
            get() = 'し'
        override val flickTop: Char
            get() = 'す'
        override val flickRight: Char
            get() = 'せ'
        override val flickBottom: Char
            get() = 'そ'
    }

    object KeyTAJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'た'
        override val flickLeft: Char
            get() = 'ち'
        override val flickTop: Char
            get() = 'つ'
        override val flickRight: Char
            get() = 'て'
        override val flickBottom: Char
            get() = 'と'
    }

    object KeyNAJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'な'
        override val flickLeft: Char
            get() = 'に'
        override val flickTop: Char
            get() = 'ぬ'
        override val flickRight: Char
            get() = 'ね'
        override val flickBottom: Char
            get() = 'の'
    }

    object KeyHAJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'は'
        override val flickLeft: Char
            get() = 'ひ'
        override val flickTop: Char
            get() = 'ふ'
        override val flickRight: Char
            get() = 'へ'
        override val flickBottom: Char
            get() = 'ほ'
    }

    object KeyMAJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'ま'
        override val flickLeft: Char
            get() = 'み'
        override val flickTop: Char
            get() = 'む'
        override val flickRight: Char
            get() = 'め'
        override val flickBottom: Char
            get() = 'も'
    }

    object KeyYAJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'や'
        override val flickLeft: Char
            get() = '（'
        override val flickTop: Char
            get() = 'ゆ'
        override val flickRight: Char
            get() = '）'
        override val flickBottom: Char
            get() = 'よ'
    }

    object KeyRAJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'ら'
        override val flickLeft: Char
            get() = 'り'
        override val flickTop: Char
            get() = 'る'
        override val flickRight: Char
            get() = 'れ'
        override val flickBottom: Char
            get() = 'ろ'
    }

    object KeyWAJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'わ'
        override val flickLeft: Char
            get() = 'を'
        override val flickTop: Char
            get() = 'ん'
        override val flickRight: Char
            get() = 'ー'
        override val flickBottom: Char
            get() = '〜'
    }

    object KeyKigouJapanese : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '、'
        override val flickLeft: Char
            get() = '。'
        override val flickTop: Char
            get() = '？'
        override val flickRight: Char
            get() = '！'
        override val flickBottom: Char
            get() = '…'
    }

    object Key1English : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '@'
        override val flickLeft: Char
            get() = '#'
        override val flickTop: Char
            get() = '&'
        override val flickRight: Char
            get() = '_'
        override val flickBottom: Char
            get() = '1'
    }

    object Key2English : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'a'
        override val flickLeft: Char
            get() = 'b'
        override val flickTop: Char
            get() = 'c'
        override val flickRight: Char
            get() = ' '
        override val flickBottom: Char
            get() = '2'
    }

    object Key3English : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'd'
        override val flickLeft: Char
            get() = 'e'
        override val flickTop: Char
            get() = 'f'
        override val flickRight: Char
            get() = ' '
        override val flickBottom: Char
            get() = '3'
    }

    object Key4English : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'g'
        override val flickLeft: Char
            get() = 'h'
        override val flickTop: Char
            get() = 'i'
        override val flickRight: Char
            get() = ' '
        override val flickBottom: Char
            get() = '4'
    }

    object Key5English : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'j'
        override val flickLeft: Char
            get() = 'k'
        override val flickTop: Char
            get() = 'l'
        override val flickRight: Char
            get() = ' '
        override val flickBottom: Char
            get() = '5'
    }

    object Key6English : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'm'
        override val flickLeft: Char
            get() = 'n'
        override val flickTop: Char
            get() = 'o'
        override val flickRight: Char
            get() = ' '
        override val flickBottom: Char
            get() = '6'
    }

    object Key7English : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'p'
        override val flickLeft: Char
            get() = 'q'
        override val flickTop: Char
            get() = 'r'
        override val flickRight: Char
            get() = 's'
        override val flickBottom: Char
            get() = '7'
    }

    object Key8English : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 't'
        override val flickLeft: Char
            get() = 'u'
        override val flickTop: Char
            get() = 'v'
        override val flickRight: Char
            get() = ' '
        override val flickBottom: Char
            get() = '8'
    }

    object Key9English : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = 'w'
        override val flickLeft: Char
            get() = 'x'
        override val flickTop: Char
            get() = 'y'
        override val flickRight: Char
            get() = 'z'
        override val flickBottom: Char
            get() = '9'
    }

    object Key0English : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '\''
        override val flickLeft: Char
            get() = '\"'
        override val flickTop: Char
            get() = ':'
        override val flickRight: Char
            get() = ';'
        override val flickBottom: Char
            get() = '0'
    }

    object KeyKigouEnglish : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '.'
        override val flickLeft: Char
            get() = ','
        override val flickTop: Char
            get() = '?'
        override val flickRight: Char
            get() = '!'
        override val flickBottom: Char
            get() = '-'
    }

    object Key1Number : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '1'
        override val flickLeft: Char
            get() = '☆'
        override val flickTop: Char
            get() = '♪'
        override val flickRight: Char
            get() = '→'
        override val flickBottom: Char
            get() = ' '
    }

    object Key2Number : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '2'
        override val flickLeft: Char
            get() = '￥'
        override val flickTop: Char
            get() = '$'
        override val flickRight: Char
            get() = '€'
        override val flickBottom: Char
            get() = ' '
    }

    object Key3Number : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '3'
        override val flickLeft: Char
            get() = '%'
        override val flickTop: Char
            get() = '°'
        override val flickRight: Char
            get() = '#'
        override val flickBottom: Char
            get() = ' '
    }

    object Key4Number : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '4'
        override val flickLeft: Char
            get() = '○'
        override val flickTop: Char
            get() = '*'
        override val flickRight: Char
            get() = '・'
        override val flickBottom: Char
            get() = ' '
    }

    object Key5Number : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '5'
        override val flickLeft: Char
            get() = '+'
        override val flickTop: Char
            get() = '×'
        override val flickRight: Char
            get() = '÷'
        override val flickBottom: Char
            get() = '-'
    }

    object Key6Number : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '6'
        override val flickLeft: Char
            get() = '<'
        override val flickTop: Char
            get() = '='
        override val flickRight: Char
            get() = '>'
        override val flickBottom: Char
            get() = ' '
    }

    object Key7Number : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '7'
        override val flickLeft: Char
            get() = '「'
        override val flickTop: Char
            get() = '」'
        override val flickRight: Char
            get() = ':'
        override val flickBottom: Char
            get() = ' '
    }

    object Key8Number : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '8'
        override val flickLeft: Char
            get() = '〒'
        override val flickTop: Char
            get() = '々'
        override val flickRight: Char
            get() = '〆'
        override val flickBottom: Char
            get() = ' '
    }

    object Key9Number : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '9'
        override val flickLeft: Char
            get() = '^'
        override val flickTop: Char
            get() = '|'
        override val flickRight: Char
            get() = '\\'
        override val flickBottom: Char
            get() = ' '
    }

    object Key0Number : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '0'
        override val flickLeft: Char
            get() = '~'
        override val flickTop: Char
            get() = '…'
        override val flickRight: Char
            get() = '@'
        override val flickBottom: Char
            get() = ' '
    }

    object KeyKigouNumber : TenKeyTapFlickInfo() {
        override val tap: Char
            get() = '.'
        override val flickLeft: Char
            get() = ','
        override val flickTop: Char
            get() = '-'
        override val flickRight: Char
            get() = '/'
        override val flickBottom: Char
            get() = ' '
    }

}
