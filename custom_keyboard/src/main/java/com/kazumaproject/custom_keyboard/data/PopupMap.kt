package com.kazumaproject.custom_keyboard.data

import androidx.annotation.Keep
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.hypot
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyMap

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
    /** Euclidean distance to the drawn rounded rectangle, in physical pixels. */
    fun distanceTo(x: Float, y: Float, keyWidth: Float, keyHeight: Float): Float {
        val radius = min(width * keyWidth, height * keyHeight) * .16f
        val dx = abs((x - (left + right) / 2) * keyWidth) - width * keyWidth / 2 + radius
        val dy = abs((y - (top + bottom) / 2) * keyHeight) - height * keyHeight / 2 + radius
        return (hypot(dx.coerceAtLeast(0f), dy.coerceAtLeast(0f)) + min(maxOf(dx, dy), 0f) - radius).coerceAtLeast(0f)
    }
    companion object { val KEY = PopupBounds(-.5f, -.5f, .5f, .5f) }
}

@Keep
enum class PopupActionType { TEXT, SWITCH, BACK }
@Keep
data class PopupAction(val type: PopupActionType = PopupActionType.TEXT, val text: String = "", val targetMapId: String? = null)
@Keep
data class PopupRegion(
    val id: String,
    val bounds: PopupBounds,
    val action: PopupAction = PopupAction(),
    val label: String = "",
    val outline: PopupOutline? = null
) {
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
    fun withMap(map: PopupMap): PopupKeyConfig {
        val updated = maps.map { if (it.id == map.id) map else it }
        return copy(maps = updated, version = if (updated.any { page -> page.regions.any { it.outline != null } }) maxOf(version, 2) else version)
    }
}

enum class PopupConflictType { INVALID, KEY, OVERLAP, TARGET, TRANSITION }
data class PopupConflict(val type: PopupConflictType, val mapId: String, val regionId: String? = null, val otherMapId: String? = null, val otherRegionId: String? = null)

/** Used by the editor, persistence boundary and runtime; never a separate preview rule. */
object PopupMapValidator {
    fun conflicts(config: PopupKeyConfig): List<PopupConflict> {
        val result = mutableListOf<PopupConflict>()
        if (config.version !in 1..2 || config.maps.isEmpty() || config.map(config.startMapId) == null || config.maps.map { it.id }.distinct().size != config.maps.size) {
            return listOf(PopupConflict(PopupConflictType.INVALID, config.startMapId))
        }
        val parents = config.maps.associate { target -> target.id to config.maps.filter { source -> source.regions.any { it.action.type == PopupActionType.SWITCH && it.action.targetMapId == target.id } } }
        config.maps.forEach { map ->
            if (map.regions.map { it.id }.distinct().size != map.regions.size) result += PopupConflict(PopupConflictType.INVALID, map.id)
            map.regions.forEachIndexed { index, region ->
                val b = region.bounds
                if (listOf(b.left, b.top, b.right, b.bottom).any { !it.isFinite() } || b.width <= 0 || b.height <= 0) result += PopupConflict(PopupConflictType.INVALID, map.id, region.id)
                if (region.outline != null && (config.version < 2 || !region.outline.isValid())) result += PopupConflict(PopupConflictType.INVALID, map.id, region.id)
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

/** Runtime preferences are independent of the per-key saved maps. */
data class PopupMapInputOptions(
    val nearestEnabled: Boolean = true,
    val nearestText: Boolean = true,
    val nearestSwitch: Boolean = false,
    val nearestBack: Boolean = false,
    val presentation: PopupMapPresentation = PopupMapPresentation.AROUND_KEY,
    val tapSizeMode: PopupTapSizeMode = PopupTapSizeMode.KEY
) {
    fun includes(region: PopupRegion, canGoBack: Boolean): Boolean = nearestEnabled && when (region.action.type) {
        PopupActionType.TEXT -> nearestText && region.action.text.isNotEmpty()
        PopupActionType.SWITCH -> nearestSwitch
        PopupActionType.BACK -> nearestBack && canGoBack
    }
}

enum class PopupMapPresentation(val preferenceValue: String) {
    AROUND_KEY("around_key"), GUIDE_ABOVE_KEY("guide_above_key");
    companion object {
        fun fromPreference(value: String?) = entries.firstOrNull { it.preferenceValue == value } ?: AROUND_KEY
    }
}

/** Stateful selection engine shared by the real IME and the editor's Try tab. */
class PopupMapSession(
    val config: PopupKeyConfig,
    private val keyWidth: Float = 1f,
    private val keyHeight: Float = 1f,
    val options: PopupMapInputOptions = PopupMapInputOptions(),
    val tapArea: PopupTapArea = PopupTapArea.KEY,
    // Only sensitivity-based tap detection follows the initial touch. Map coordinates stay key-relative.
    val tapOriginX: Float = 0f,
    val tapOriginY: Float = 0f
) {
    private val history = mutableListOf(config.startMapId)
    val currentMap get() = requireNotNull(config.map(history.last()))
    val mapPath get() = history.toList()
    var selectedRegionId: String? = null; private set
    var selectedText: String? = null; private set
    var isTap = true; private set
    private var arrivalControl: String? = null
    init { require(PopupMapValidator.conflicts(config).isEmpty()); select(tapOriginX, tapOriginY) }
    fun move(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        val region = resolve(x, y)
        if (arrivalControl != null && region?.id != arrivalControl) arrivalControl = null
        if (region?.isControl == true && region.id != arrivalControl) {
            val before = history.toList()
            when (region.action.type) {
                PopupActionType.SWITCH -> history += requireNotNull(region.action.targetMapId)
                PopupActionType.BACK -> if (history.size > 1) history.removeAt(history.lastIndex)
                else -> Unit
            }
            if (history != before) {
                selectedRegionId = null
                // A newly displayed control must be exited and entered again. Text needs no reentry.
                arrivalControl = resolve(x, y)?.takeIf { it.isControl }?.id
            }
        }
        select(x, y)
    }
    fun release(x: Float, y: Float): String? { select(x, y); return selectedText?.takeIf { it.isNotEmpty() } }
    private fun insideKey(x: Float, y: Float) = tapArea.contains(x-tapOriginX, y-tapOriginY, keyWidth, keyHeight)
    private fun resolve(x: Float, y: Float): PopupRegion? {
        if (!x.isFinite() || !y.isFinite() || insideKey(x,y)) return null
        currentMap.regions.firstOrNull { it.contains(x, y, keyWidth, keyHeight) }?.let { return it }
        val candidates = currentMap.regions.filter { options.includes(it, history.size > 1) }
        // The sensitivity box can leave a gap inside the reserved key. Project that motion
        // to the key's edge, so a short horizontal flick does not pick a nearer top/bottom
        // popup simply because the physical key is wider than it is tall.
        val dx=x-tapOriginX;val dy=y-tapOriginY
        val extent=maxOf(abs(dx),abs(dy))
        val projectFromTouch=tapArea.followsSensitivity && extent>0f && abs(x)<=.5f && abs(y)<=.5f
        val scale=if(projectFromTouch) .5f/extent else 1f
        val nearestX=if(projectFromTouch) dx*scale else x
        val nearestY=if(projectFromTouch) dy*scale else y
        val distances = candidates.map { it to it.distanceTo(nearestX,nearestY,keyWidth,keyHeight) }
        val minimum = distances.minOfOrNull { it.second } ?: return null
        val tied = distances.filter { abs(it.second - minimum) < .001f }.map { it.first }
        return tied.firstOrNull { it.id == selectedRegionId } ?: tied.firstOrNull()
    }
    private fun select(x: Float, y: Float) {
        val region = resolve(x, y)
        selectedRegionId = region?.id
        isTap = insideKey(x,y)
        selectedText = if (isTap) currentMap.tapText else region?.takeIf { !it.isControl }?.action?.text
    }
}

object SumirePopupDefaults {
    // Keep the persisted style ID. Default maps remain v1; custom outlines opt into v2.
    const val STYLE = "free-popup"
    const val PUNCTUATION_KEY = "、。?!"
    private val keys = listOf(Key.KeyA, Key.KeyKA, Key.KeySA, Key.KeyTA, Key.KeyNA, Key.KeyHA, Key.KeyMA, Key.KeyYA, Key.KeyRA, Key.KeyWA)
    private val keyMap = KeyMap()
    private val definitions = keys.associateTo(linkedMapOf()) { key ->
        val info = keyMap.getKeyInfoJapanese(key, false) as KeyInfo.KeyTapFlickInfo
        info.tap.toString() to listOf(info.tap, info.flickLeft, info.flickTop, info.flickRight, info.flickBottom)
    }.apply { put(PUNCTUATION_KEY, listOf('、', '。', '？', '！', '…')) }
    val rows = definitions.mapValues { (_, characters) -> characters.filterNotNull().joinToString("") }
    private val slots = listOf(
        PopupBounds.KEY.offset(-1f, 0f), PopupBounds.KEY.offset(0f, -1f),
        PopupBounds.KEY.offset(1f, 0f), PopupBounds.KEY.offset(0f, 1f)
    )
    /** Standard 12-key text assignments, with no preconfigured map transitions. */
    fun create(keyId: String): PopupKeyConfig {
        val definition = definitions.getValue(keyId)
        val characters = definition.drop(1)
        val regions = characters.mapIndexedNotNull { index, character ->
            character?.let { PopupRegion("normal-$index", slots[index], PopupAction(text=it.toString())) }
        }
        return PopupKeyConfig(keyId,"normal",listOf(PopupMap("normal","通常",definition.first().toString(),regions)))
    }
}
