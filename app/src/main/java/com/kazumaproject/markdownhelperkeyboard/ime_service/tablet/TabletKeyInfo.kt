package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

sealed class TabletKeyInfo {
    data object Null : TabletKeyInfo()
    abstract class TabletTapFlickInfo : TabletKeyInfo() {
        abstract val tap: Char?
        abstract val flickLeft: Char?
        abstract val flickTop: Char?
        abstract val flickRight: Char?
        abstract val flickBottom: Char?
    }

    object KeyAJapanese : TabletTapFlickInfo() {
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

    object KeyIJapanese : TabletTapFlickInfo() {
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

    object KeyUJapanese : TabletTapFlickInfo() {
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

    object KeyEJapanese : TabletTapFlickInfo() {
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

    object KeyOJapanese : TabletTapFlickInfo() {
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

    object KeyKAJapanese : TabletTapFlickInfo() {
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

    object KeyKIJapanese : TabletTapFlickInfo() {
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

    object KeyKUJapanese : TabletTapFlickInfo() {
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

    object KeyKEJapanese : TabletTapFlickInfo() {
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

    object KeyKOJapanese : TabletTapFlickInfo() {
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

    object KeySAJapanese : TabletTapFlickInfo() {
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

    object KeySHIJapanese : TabletTapFlickInfo() {
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

    object KeySUJapanese : TabletTapFlickInfo() {
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

    object KeySEJapanese : TabletTapFlickInfo() {
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

    object KeySOJapanese : TabletTapFlickInfo() {
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

    object KeyTAJapanese : TabletTapFlickInfo() {
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

    object KeyCHIJapanese : TabletTapFlickInfo() {
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

    object KeyTSUJapanese : TabletTapFlickInfo() {
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

    object KeyTEJapanese : TabletTapFlickInfo() {
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

    object KeyTOJapanese : TabletTapFlickInfo() {
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

    object KeyNAJapanese : TabletTapFlickInfo() {
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

    object KeyNIJapanese : TabletTapFlickInfo() {
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

    object KeyNUJapanese : TabletTapFlickInfo() {
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

    object KeyNEJapanese : TabletTapFlickInfo() {
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

    object KeyNOJapanese : TabletTapFlickInfo() {
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

    object KeyHAJapanese : TabletTapFlickInfo() {
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

    object KeyHIJapanese : TabletTapFlickInfo() {
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

    object KeyFUJapanese : TabletTapFlickInfo() {
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

    object KeyHEJapanese : TabletTapFlickInfo() {
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

    object KeyHOJapanese : TabletTapFlickInfo() {
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

    object KeyMAJapanese : TabletTapFlickInfo() {
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

    object KeyMIJapanese : TabletTapFlickInfo() {
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

    object KeyMUJapanese : TabletTapFlickInfo() {
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

    object KeyMEJapanese : TabletTapFlickInfo() {
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

    object KeyMOJapanese : TabletTapFlickInfo() {
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

    object KeyYAJapanese : TabletTapFlickInfo() {
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

    object KeyYUJapanese : TabletTapFlickInfo() {
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

    object KeyYOJapanese : TabletTapFlickInfo() {
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

    object KeyRAJapanese : TabletTapFlickInfo() {
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

    object KeyRIJapanese : TabletTapFlickInfo() {
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

    object KeyRUJapanese : TabletTapFlickInfo() {
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

    object KeyREJapanese : TabletTapFlickInfo() {
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

    object KeyROJapanese : TabletTapFlickInfo() {
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

    object KeyWAJapanese : TabletTapFlickInfo() {
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

    object KeyWOJapanese : TabletTapFlickInfo() {
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

    object KeyNNJapanese : TabletTapFlickInfo() {
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

    object KeyMINUSJapanese : TabletTapFlickInfo() {
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

    object KeyDAKUTENJapanese : TabletTapFlickInfo() {
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

    object KeyKAGIKAKKOJapanese : TabletTapFlickInfo() {
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

    object KeyQUESTIONJapanese : TabletTapFlickInfo() {
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

    object KeyCAUTIONJapanese : TabletTapFlickInfo() {
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

    object KeyKUTENJapanese : TabletTapFlickInfo() {
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

    object KeyTOUTENJapanese : TabletTapFlickInfo() {
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