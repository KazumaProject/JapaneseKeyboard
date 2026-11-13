package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import androidx.preference.PreferenceManager
import com.kazumaproject.custom_keyboard.data.PopupKeyConfig
import com.kazumaproject.custom_keyboard.data.SumirePopupDefaults
import com.kazumaproject.markdownhelperkeyboard.R
import java.util.UUID

/** Named layouts plus an atomic mirror into the existing runtime preference keys. */
class FreeFlickLayoutRepository(private val context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val active get() = SumirePopupRepository(context)
    fun builtin() = FreeFlickLayout(BUILTIN_ID, context.getString(R.string.free_flick_template_12),
        SumirePopupDefaults.rows.keys.map(SumirePopupDefaults::create))
    fun list(): List<FreeFlickLayout> = synchronized(lock) { listOf(builtin()) + read().layouts }
    fun selectedId(): String = synchronized(lock) { read().selectedId }
    fun find(id: String): FreeFlickLayout = synchronized(lock) {
        if (id == BUILTIN_ID) builtin() else requireNotNull(read().layouts.firstOrNull { it.id == id }) { "Layout no longer exists" }
    }
    fun snapshot(name: String): FreeFlickLayout = synchronized(lock) { add(FreeFlickLayout("", name, SumirePopupDefaults.rows.keys.map { active.load(it) })) }
    fun create(name: String): FreeFlickLayout = add(builtin().copy(name = name))
    fun duplicate(id: String, name: String): FreeFlickLayout = add(find(id).copy(name = name))
    fun importLayout(layout: FreeFlickLayout): FreeFlickLayout = synchronized(lock) {
        val state = read()
        add(layout.copy(name = uniqueName(layout.name, state)), state)
    }
    private fun add(layout: FreeFlickLayout, previous: FreeFlickLayoutLibrary? = null): FreeFlickLayout = synchronized(lock) {
        val state = previous ?: read()
        require(state.layouts.size < FreeFlickLayoutCodec.MAX_LAYOUTS)
        val saved = layout.copy(id = UUID.randomUUID().toString(), name = layout.name.trim())
        checkName(saved.name, state)
        FreeFlickLayoutCodec.encode(saved) // Also enforce the portable file-size limit when saving.
        persist(state.copy(layouts = state.layouts + saved))
        saved
    }
    fun rename(id: String, name: String) = synchronized(lock) {
        val state = read(); val layout = find(id)
        require(id != BUILTIN_ID)
        checkName(name.trim(), state, id)
        persist(state.copy(layouts = state.layouts.map { if (it.id == id) layout.copy(name = name.trim()) else it }))
    }
    fun select(id: String) = synchronized(lock) {
        val state = read(); val layout = find(id)
        persist(state.copy(selectedId = id), layout.keys)
    }
    fun delete(id: String) = synchronized(lock) {
        val state = read(); require(id != BUILTIN_ID); find(id)
        val selected = state.selectedId == id
        persist(state.copy(selectedId = if (selected) BUILTIN_ID else state.selectedId,
            layouts = state.layouts.filterNot { it.id == id }), if (selected) builtin().keys else emptyList())
    }
    fun saveKey(config: PopupKeyConfig, layoutId: String? = null): String = synchronized(lock) {
        val state = read(); val target = layoutId ?: state.selectedId
        val original = find(target)
        val updated = original.copy(keys = original.keys.map { if (it.keyId == config.keyId) config else it })
        require(config.keyId in SumirePopupDefaults.rows)
        FreeFlickLayoutCodec.encode(updated)
        val saved = if (target == BUILTIN_ID) updated.copy(id = UUID.randomUUID().toString(),
            name = uniqueName(context.getString(R.string.free_flick_layout_current), state)) else updated
        if (target == BUILTIN_ID) require(state.layouts.size < FreeFlickLayoutCodec.MAX_LAYOUTS)
        val selected = state.selectedId == target
        persist(state.copy(selectedId = if (selected) saved.id else state.selectedId,
            layouts = if (target == BUILTIN_ID) state.layouts + saved else state.layouts.map { if (it.id == target) saved else it }),
            if (selected) listOf(config) else emptyList())
        saved.id
    }
    private fun read(): FreeFlickLayoutLibrary {
        preferences.getString(STORAGE_KEY, null)?.let { return FreeFlickLayoutCodec.decodeLibrary(it) }
        val legacy = SumirePopupDefaults.rows.keys.any { preferences.contains(SumirePopupRepository.PREFIX + it) }
        val saved = if (legacy) FreeFlickLayout(UUID.randomUUID().toString(), context.getString(R.string.free_flick_layout_current),
            SumirePopupDefaults.rows.keys.map { active.load(it) }) else null
        // Keep existing per-key data intact, including unreadable values, until an explicit save/select.
        val state = FreeFlickLayoutLibrary(saved?.id ?: BUILTIN_ID, listOfNotNull(saved))
        persist(state)
        return state
    }
    private fun persist(state: FreeFlickLayoutLibrary, runtimeKeys: List<PopupKeyConfig> = emptyList()) {
        val editor = preferences.edit().putString(STORAGE_KEY, FreeFlickLayoutCodec.encodeLibrary(state))
        runtimeKeys.forEach { editor.putString(SumirePopupRepository.PREFIX + it.keyId, active.encode(it)) }
        editor.apply()
    }
    private fun uniqueName(name: String, state: FreeFlickLayoutLibrary): String {
        val base = name.trim().take(56)
        var result = base; var suffix = 2
        while (state.layouts.any { it.name == result } || result == builtin().name) result = "$base (${suffix++})"
        return result
    }
    private fun checkName(name: String, state: FreeFlickLayoutLibrary, exceptId: String? = null) {
        require(name.isNotBlank() && name.length <= 64 && name != builtin().name && state.layouts.none { it.id != exceptId && it.name == name })
    }
    companion object {
        const val BUILTIN_ID = "builtin-12key"
        const val STORAGE_KEY = "sumire_free_flick_layout_library_v1"
        const val ARG_LAYOUT_ID = "freeFlickLayoutId"
        private val lock = Any()
    }
}
