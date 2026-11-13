package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.SharedPreferences
import com.google.gson.Gson
import com.kazumaproject.custom_keyboard.data.*

/** Only the built-in selection may update. Named layouts, copies and edited runtime data stay exact. */
internal object FreeFlickDefaultMigration {
    private val lock = Any()
    private val oldBounds = listOf(PopupBounds(-1.5f,-.4f,-.6f,.4f), PopupBounds(-.45f,-1.4f,.45f,-.6f),
        PopupBounds(.6f,-.4f,1.5f,.4f), PopupBounds(-.45f,.6f,.45f,1.4f))
    fun update(preferences: SharedPreferences) = synchronized(lock) {
        val library = preferences.getString(FreeFlickLayoutRepository.STORAGE_KEY, null) ?: return@synchronized
        val state = runCatching { FreeFlickLayoutCodec.decodeLibrary(library) }.getOrNull() ?: return@synchronized
        if (state.selectedId != FreeFlickLayoutRepository.BUILTIN_ID) return@synchronized
        val gson = Gson()
        val updates = mutableListOf<PopupKeyConfig>()
        for (key in SumirePopupDefaults.rows.keys) {
            val raw = preferences.getString(SumirePopupRepository.PREFIX + key, null) ?: continue
            val saved = runCatching { gson.fromJson(raw, PopupKeyConfig::class.java) }.getOrNull() ?: return@synchronized
            val current = SumirePopupDefaults.create(key)
            val legacy = current.copy(maps = current.maps.map { map -> map.copy(regions = map.regions.map { region ->
                region.copy(bounds = oldBounds[region.id.substringAfterLast('-').toInt()])
            }) })
            when (saved) {
                legacy -> updates += current
                current -> Unit
                else -> return@synchronized // A user change must not be treated as an untouched template.
            }
        }
        if (updates.isNotEmpty()) preferences.edit().also { editor ->
            updates.forEach { editor.putString(SumirePopupRepository.PREFIX + it.keyId, gson.toJson(it)) }
        }.apply()
    }
}
