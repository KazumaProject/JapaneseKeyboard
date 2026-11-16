package com.kazumaproject.markdownhelperkeyboard

import com.kazumaproject.custom_keyboard.data.*

/** Existing v1 configuration, deliberately independent of the new default template. */
internal object LegacyPopupFixture {
    val rows = linkedMapOf("あ" to "あいうえお", "か" to "かきくけこ", "さ" to "さしすせそ", "た" to "たちつてと", "な" to "なにぬねの", "は" to "はひふへほ", "ま" to "まみむめも", "や" to "やゆよ", "ら" to "らりるれろ", "わ" to "わをんー")
    private val voiced = mapOf("か" to "がぎぐげご", "さ" to "ざじずぜぞ", "た" to "だぢづでど", "は" to "ばびぶべぼ")
    private val slots = listOf(PopupBounds(-1.4f,-1.1f,-.6f,-.3f), PopupBounds(-.4f,-1.4f,.4f,-.6f), PopupBounds(.6f,-1.1f,1.4f,-.3f), PopupBounds(-.4f,-2.4f,.4f,-1.6f))
    fun create(keyId: String): PopupKeyConfig {
        val row = rows.getValue(keyId)
        fun page(id: String, chars: String): PopupMap {
            val regions = chars.drop(1).mapIndexed { index, char -> PopupRegion("$id-$index", slots[index], PopupAction(text = char.toString())) }.toMutableList()
            if (id != "normal") regions += PopupRegion("$id-back", PopupBounds(-.4f,-3.4f,.4f,-2.6f), PopupAction(PopupActionType.BACK), "戻る")
            return PopupMap(id, chars.first().toString() + "行", chars.first().toString(), regions)
        }
        val normal = page("normal", row).copy(name = "通常")
        val maps = mutableListOf(normal)
        voiced[keyId]?.let { maps += page("voiced", it) }
        if (keyId == "は") maps += page("semi", "ぱぴぷぺぽ")
        val controls = maps.drop(1).mapIndexed { index, target ->
            val x = if (index == 0) -1.4f else .6f
            PopupRegion("open-${target.id}", PopupBounds(x,-2.4f,x+.8f,-1.6f), PopupAction(PopupActionType.SWITCH, targetMapId = target.id), target.name)
        }
        return PopupKeyConfig(keyId, "normal", listOf(normal.copy(regions = normal.regions + controls)) + maps.drop(1))
    }
}
