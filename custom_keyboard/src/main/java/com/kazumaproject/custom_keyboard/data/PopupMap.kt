package com.kazumaproject.custom_keyboard.data

import androidx.annotation.Keep
import kotlin.math.abs
import kotlin.math.min

/** Coordinates are key-width/key-height units relative to the original key's centre. */
@Keep
data class PopupBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun offset(dx: Float, dy: Float) = copy(left = left + dx, right = right + dx, top = top + dy, bottom = bottom + dy)
    fun intersects(other: PopupBounds) = left < other.right && right > other.left && top < other.bottom && bottom > other.top
    fun contains(x: Float, y: Float, keyWidth: Float = 1f, keyHeight: Float = 1f): Boolean {
        if (x < left || x >= right || y < top || y >= bottom) return false
        val radius = min(width * keyWidth, height * keyHeight) * .16f
        val dx = ((abs(x - (left + right) / 2) - width / 2) * keyWidth + radius).coerceAtLeast(0f)
        val dy = ((abs(y - (top + bottom) / 2) - height / 2) * keyHeight + radius).coerceAtLeast(0f)
        return dx * dx + dy * dy <= radius * radius
    }
    companion object { val KEY = PopupBounds(-.5f, -.5f, .5f, .5f) }
}

@Keep
enum class PopupActionType { TEXT, SWITCH, BACK }
@Keep
data class PopupAction(val type: PopupActionType = PopupActionType.TEXT, val text: String = "", val targetMapId: String? = null)
@Keep
data class PopupRegion(val id: String, val bounds: PopupBounds, val action: PopupAction = PopupAction(), val label: String = "") {
    val isControl get() = action.type != PopupActionType.TEXT
    fun displayLabel() = label.ifEmpty {
        when (action.type) { PopupActionType.TEXT -> action.text; PopupActionType.SWITCH -> "↗"; PopupActionType.BACK -> "↶" }
    }
}
@Keep
data class PopupMap(val id: String, val name: String, val tapText: String, val regions: List<PopupRegion> = emptyList())
@Keep
data class PopupKeyConfig(val keyId: String, val startMapId: String, val maps: List<PopupMap>, val version: Int = 1) {
    fun map(id: String) = maps.firstOrNull { it.id == id }
    fun withMap(map: PopupMap) = copy(maps = maps.map { if (it.id == map.id) map else it })
}

enum class PopupConflictType { INVALID, KEY, OVERLAP, TARGET, TRANSITION }
data class PopupConflict(val type: PopupConflictType, val mapId: String, val regionId: String? = null, val otherMapId: String? = null, val otherRegionId: String? = null)

/** Used by the editor, persistence boundary and runtime; never a separate preview rule. */
object PopupMapValidator {
    fun conflicts(config: PopupKeyConfig): List<PopupConflict> {
        val result = mutableListOf<PopupConflict>()
        if (config.version != 1 || config.maps.isEmpty() || config.map(config.startMapId) == null || config.maps.map { it.id }.distinct().size != config.maps.size) {
            return listOf(PopupConflict(PopupConflictType.INVALID, config.startMapId))
        }
        val parents = config.maps.associate { target -> target.id to config.maps.filter { source -> source.regions.any { it.action.type == PopupActionType.SWITCH && it.action.targetMapId == target.id } } }
        config.maps.forEach { map ->
            if (map.regions.map { it.id }.distinct().size != map.regions.size) result += PopupConflict(PopupConflictType.INVALID, map.id)
            map.regions.forEachIndexed { index, region ->
                val b = region.bounds
                if (listOf(b.left, b.top, b.right, b.bottom).any { !it.isFinite() } || b.width <= 0 || b.height <= 0) result += PopupConflict(PopupConflictType.INVALID, map.id, region.id)
                if (b.intersects(PopupBounds.KEY)) result += PopupConflict(PopupConflictType.KEY, map.id, region.id)
                map.regions.drop(index + 1).filter { b.intersects(it.bounds) }.forEach { result += PopupConflict(PopupConflictType.OVERLAP, map.id, region.id, map.id, it.id) }
                if (!region.isControl) return@forEachIndexed
                val destinations = when (region.action.type) {
                    PopupActionType.SWITCH -> listOfNotNull(config.map(region.action.targetMapId.orEmpty()))
                    PopupActionType.BACK -> parents[map.id].orEmpty()
                    else -> emptyList()
                }
                if (region.action.type == PopupActionType.SWITCH && (destinations.isEmpty() || region.action.targetMapId == map.id)) result += PopupConflict(PopupConflictType.TARGET, map.id, region.id)
                destinations.forEach { target -> target.regions.filter { it.isControl && b.intersects(it.bounds) }.forEach {
                    result += PopupConflict(PopupConflictType.TRANSITION, map.id, region.id, target.id, it.id)
                } }
            }
        }
        return result
    }
}

/** Stateful selection engine shared by the real IME and the editor's Try tab. */
class PopupMapSession(val config: PopupKeyConfig, private val keyWidth: Float = 1f, private val keyHeight: Float = 1f) {
    private val history = mutableListOf(config.startMapId)
    val currentMap get() = requireNotNull(config.map(history.last()))
    val mapPath get() = history.toList()
    var selectedRegionId: String? = null; private set
    var selectedText: String? = null; private set
    var isTap = true; private set
    init { require(PopupMapValidator.conflicts(config).isEmpty()); select(0f, 0f) }
    fun move(x: Float, y: Float) {
        val region = currentMap.regions.firstOrNull { it.bounds.contains(x, y, keyWidth, keyHeight) }
        if (region?.isControl == true) {
            when (region.action.type) {
                PopupActionType.SWITCH -> history += requireNotNull(region.action.targetMapId)
                PopupActionType.BACK -> if (history.size > 1) history.removeAt(history.lastIndex)
                else -> Unit
            }
        }
        // One transition per position. Text immediately under the finger is selectable.
        select(x, y)
    }
    fun release(x: Float, y: Float): String? { select(x, y); return selectedText?.takeIf { it.isNotEmpty() } }
    private fun select(x: Float, y: Float) {
        val region = currentMap.regions.firstOrNull { it.bounds.contains(x, y, keyWidth, keyHeight) }
        selectedRegionId = region?.id
        isTap = x >= -.5f && x < .5f && y >= -.5f && y < .5f
        selectedText = if (isTap) currentMap.tapText else region?.takeIf { !it.isControl }?.action?.text
    }
}

object SumirePopupDefaults {
    const val STYLE = "free-popup"
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
