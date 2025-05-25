package com.kazumaproject.core.domain.key

sealed class KeyInfo {
    data object Null : KeyInfo()

    abstract class KeyTapFlickInfo : KeyInfo() {
        abstract val tap: Char?
        abstract val flickLeft: Char?
        abstract val flickTop: Char?
        abstract val flickRight: Char?
        abstract val flickBottom: Char?
    }

    object KeyAJapanese : KeyTapFlickInfo() {
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

    object KeyKAJapanese : KeyTapFlickInfo() {
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

    object KeySAJapanese : KeyTapFlickInfo() {
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

    object KeyTAJapanese : KeyTapFlickInfo() {
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

    object KeyNAJapanese : KeyTapFlickInfo() {
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

    object KeyHAJapanese : KeyTapFlickInfo() {
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

    object KeyMAJapanese : KeyTapFlickInfo() {
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

    object KeyYAJapanese : KeyTapFlickInfo() {
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

    object KeyRAJapanese : KeyTapFlickInfo() {
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

    object KeyWAJapanese : KeyTapFlickInfo() {
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

    object KeyKigouJapanese : KeyTapFlickInfo() {
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

    object Key1English : KeyTapFlickInfo() {
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

    object Key2English : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'a'
        override val flickLeft: Char
            get() = 'b'
        override val flickTop: Char
            get() = 'c'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char
            get() = '2'
    }

    object Key3English : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'd'
        override val flickLeft: Char
            get() = 'e'
        override val flickTop: Char
            get() = 'f'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char
            get() = '3'
    }

    object Key4English : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'g'
        override val flickLeft: Char
            get() = 'h'
        override val flickTop: Char
            get() = 'i'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char
            get() = '4'
    }

    object Key5English : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'j'
        override val flickLeft: Char
            get() = 'k'
        override val flickTop: Char
            get() = 'l'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char
            get() = '5'
    }

    object Key6English : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'm'
        override val flickLeft: Char
            get() = 'n'
        override val flickTop: Char
            get() = 'o'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char
            get() = '6'
    }

    object Key7English : KeyTapFlickInfo() {
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

    object Key8English : KeyTapFlickInfo() {
        override val tap: Char
            get() = 't'
        override val flickLeft: Char
            get() = 'u'
        override val flickTop: Char
            get() = 'v'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char
            get() = '8'
    }

    object Key9English : KeyTapFlickInfo() {
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

    object Key0English : KeyTapFlickInfo() {
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

    object KeyKigouEnglish : KeyTapFlickInfo() {
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

    object Key1Number : KeyTapFlickInfo() {
        override val tap: Char
            get() = '1'
        override val flickLeft: Char
            get() = '☆'
        override val flickTop: Char
            get() = '♪'
        override val flickRight: Char
            get() = '→'
        override val flickBottom: Char?
            get() = null
    }

    object Key2Number : KeyTapFlickInfo() {
        override val tap: Char
            get() = '2'
        override val flickLeft: Char
            get() = '￥'
        override val flickTop: Char
            get() = '$'
        override val flickRight: Char
            get() = '€'
        override val flickBottom: Char?
            get() = null
    }

    object Key3Number : KeyTapFlickInfo() {
        override val tap: Char
            get() = '3'
        override val flickLeft: Char
            get() = '%'
        override val flickTop: Char
            get() = '°'
        override val flickRight: Char
            get() = '#'
        override val flickBottom: Char?
            get() = null
    }

    object Key4Number : KeyTapFlickInfo() {
        override val tap: Char
            get() = '4'
        override val flickLeft: Char
            get() = '○'
        override val flickTop: Char
            get() = '*'
        override val flickRight: Char
            get() = '・'
        override val flickBottom: Char?
            get() = null
    }

    object Key5Number : KeyTapFlickInfo() {
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

    object Key6Number : KeyTapFlickInfo() {
        override val tap: Char
            get() = '6'
        override val flickLeft: Char
            get() = '<'
        override val flickTop: Char
            get() = '='
        override val flickRight: Char
            get() = '>'
        override val flickBottom: Char?
            get() = null
    }

    object Key7Number : KeyTapFlickInfo() {
        override val tap: Char
            get() = '7'
        override val flickLeft: Char
            get() = '「'
        override val flickTop: Char
            get() = '」'
        override val flickRight: Char
            get() = ':'
        override val flickBottom: Char?
            get() = null
    }

    object Key8Number : KeyTapFlickInfo() {
        override val tap: Char
            get() = '8'
        override val flickLeft: Char
            get() = '〒'
        override val flickTop: Char
            get() = '々'
        override val flickRight: Char
            get() = '〆'
        override val flickBottom: Char?
            get() = null
    }

    object Key9Number : KeyTapFlickInfo() {
        override val tap: Char
            get() = '9'
        override val flickLeft: Char
            get() = '^'
        override val flickTop: Char
            get() = '|'
        override val flickRight: Char
            get() = '\\'
        override val flickBottom: Char?
            get() = null
    }

    object Key0Number : KeyTapFlickInfo() {
        override val tap: Char
            get() = '0'
        override val flickLeft: Char
            get() = '~'
        override val flickTop: Char
            get() = '…'
        override val flickRight: Char
            get() = '@'
        override val flickBottom: Char?
            get() = null
    }

    object KeyKigouNumber : KeyTapFlickInfo() {
        override val tap: Char
            get() = '.'
        override val flickLeft: Char
            get() = ','
        override val flickTop: Char
            get() = '-'
        override val flickRight: Char
            get() = '/'
        override val flickBottom: Char?
            get() = null
    }

    object KeyDakutenSmallNumber : KeyTapFlickInfo() {
        override val tap: Char
            get() = '('
        override val flickLeft: Char
            get() = ')'
        override val flickTop: Char
            get() = '['
        override val flickRight: Char
            get() = ']'
        override val flickBottom: Char?
            get() = null
    }
    
    /** タブレット **/
    object TabletKeyAJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'あ'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = 'ぁ'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyIJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'い'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = 'ぃ'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyUJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'う'
        override val flickLeft: Char
            get() = 'ゔ'
        override val flickTop: Char
            get() = 'ぅ'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyEJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'え'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = 'ぇ'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyOJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'お'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = 'ぉ'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object TabletKeyKAJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'か'
        override val flickLeft: Char
            get() = 'が'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyKIJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'き'
        override val flickLeft: Char
            get() = 'ぎ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyKUJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'く'
        override val flickLeft: Char
            get() = 'ぐ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyKEJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'け'
        override val flickLeft: Char
            get() = 'げ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyKOJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'こ'
        override val flickLeft: Char
            get() = 'ご'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object TabletKeySAJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'さ'
        override val flickLeft: Char
            get() = 'ざ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeySHIJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'し'
        override val flickLeft: Char
            get() = 'じ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeySUJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'す'
        override val flickLeft: Char
            get() = 'ず'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeySEJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'せ'
        override val flickLeft: Char
            get() = 'ぜ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeySOJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'そ'
        override val flickLeft: Char
            get() = 'ぞ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object TabletKeyTAJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'た'
        override val flickLeft: Char
            get() = 'だ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyCHIJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ち'
        override val flickLeft: Char
            get() = 'ぢ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyTSUJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'つ'
        override val flickLeft: Char
            get() = 'づ'
        override val flickTop: Char
            get() = 'っ'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyTEJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'て'
        override val flickLeft: Char
            get() = 'で'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyTOJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'と'
        override val flickLeft: Char
            get() = 'ど'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object TabletKeyNAJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'な'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyNIJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'に'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyNUJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ぬ'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyNEJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ね'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyNOJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'の'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object TabletKeyHAJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'は'
        override val flickLeft: Char
            get() = 'ば'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char
            get() = 'ぱ'
        override val flickBottom: Char?
            get() = null
    }

    object KeyHIJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ひ'
        override val flickLeft: Char
            get() = 'び'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char
            get() = 'ぴ'
        override val flickBottom: Char?
            get() = null
    }

    object KeyFUJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ふ'
        override val flickLeft: Char
            get() = 'ぶ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char
            get() = 'ぷ'
        override val flickBottom: Char?
            get() = null
    }

    object KeyHEJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'へ'
        override val flickLeft: Char
            get() = 'べ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char
            get() = 'ぺ'
        override val flickBottom: Char?
            get() = null
    }

    object KeyHOJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ほ'
        override val flickLeft: Char
            get() = 'ぼ'
        override val flickTop: Char?
            get() = null
        override val flickRight: Char
            get() = 'ぽ'
        override val flickBottom: Char?
            get() = null
    }

    object TabletKeyMAJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ま'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyMIJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'み'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyMUJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'む'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyMEJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'め'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyMOJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'も'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object TabletKeyYAJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'や'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = 'ゃ'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyYUJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ゆ'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = 'ゅ'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyYOJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'よ'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = 'ょ'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object TabletKeyRAJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ら'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyRIJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'り'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyRUJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'る'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyREJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'れ'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyROJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ろ'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object TabletKeyWAJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'わ'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = 'ゎ'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyWOJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'を'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyNNJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ん'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyMINUSJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = 'ー'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = '-'
        override val flickRight: Char
            get() = '〜'
        override val flickBottom: Char?
            get() = null
    }

    object KeyDAKUTENJapanese : KeyTapFlickInfo() {
        override val tap: Char?
            get() = null
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyKAGIKAKKOJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = '「'
        override val flickLeft: Char
            get() = '「'
        override val flickTop: Char
            get() = '('
        override val flickRight: Char
            get() = '」'
        override val flickBottom: Char
            get() = ')'
    }

    object KeyQUESTIONJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = '？'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = '?'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyCAUTIONJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = '！'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char
            get() = '!'
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyKUTENJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = '、'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }

    object KeyTOUTENJapanese : KeyTapFlickInfo() {
        override val tap: Char
            get() = '。'
        override val flickLeft: Char?
            get() = null
        override val flickTop: Char?
            get() = null
        override val flickRight: Char?
            get() = null
        override val flickBottom: Char?
            get() = null
    }
    
}