package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FreeFlickLayoutRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val repository = FreeFlickLayoutRepository(context)
    private val active = SumirePopupRepository(context)
    @Before fun setup() { preferences.edit().clear().commit(); AppPreference.init(context) }
    private fun modified(key: String, text: String): PopupKeyConfig {
        val config = SumirePopupDefaults.create(key)
        return config.withMap(config.maps.single().copy(tapText = text))
    }
    private fun shaped(): PopupKeyConfig {
        val config = SumirePopupDefaults.create("は"); val map = config.maps.single()
        val child = map.copy(id = "child", name = "ば行", tapText = "ば", regions = emptyList())
        return config.copy(version = 2, maps = listOf(map.copy(regions = map.regions.mapIndexed { i, r ->
            when (i) { 0 -> r.copy(outline = PopupOutline(PopupOutline.TRIANGLE)); 1 -> r.copy(outline = PopupOutline()); else -> r }
        } + PopupRegion("switch", PopupBounds(-1.5f,-2.4f,-.6f,-1.6f), PopupAction(PopupActionType.SWITCH, targetMapId = "child"), "ば")), child))
    }

    @Test fun openingLibraryMigratesAllExistingKeysWithoutChangingRuntimeOrUnreadableValues() {
        val edited = modified("は", "★")
        val json = active.encode(edited)
        preferences.edit().putString(SumirePopupRepository.PREFIX + "は", json)
            .putString(SumirePopupRepository.PREFIX + "か", "{broken").commit()
        val selected = repository.find(repository.selectedId())
        assertEquals(10, selected.keys.size)
        assertEquals(edited, selected.keys.first { it.keyId == "は" })
        assertEquals(json, preferences.getString(SumirePopupRepository.PREFIX + "は", null))
        assertEquals("{broken", preferences.getString(SumirePopupRepository.PREFIX + "か", null))
        assertEquals(2, repository.list().size)
        assertEquals(selected, FreeFlickLayoutRepository(context).find(selected.id))
    }
    @Test fun editingTheBuiltinCreatesAnEditableSelectedLayoutAndKeepsTheTemplate() {
        assertEquals(FreeFlickLayoutRepository.BUILTIN_ID, repository.selectedId())
        val changed = modified("は", "★")
        val id = active.save(changed)
        assertNotEquals(FreeFlickLayoutRepository.BUILTIN_ID, id)
        assertEquals(id, repository.selectedId())
        assertEquals(changed, active.load("は"))
        assertEquals(SumirePopupDefaults.create("は"), repository.builtin().keys.first { it.keyId == "は" })
    }
    @Test fun nonSelectedEditsAreIsolatedAndSelectingReplacesAllTenKeysAtomically() {
        val first = repository.create("First"); val second = repository.create("Second")
        repository.select(first.id)
        active.save(modified("は", "A"), first.id); active.save(modified("か", "B"), first.id)
        val before = preferences.all
        active.save(modified("は", "C"), second.id)
        assertEquals("A", active.load("は").maps.single().tapText)
        assertEquals("B", active.load("か").maps.single().tapText)
        assertEquals(before.filterKeys { it.startsWith(SumirePopupRepository.PREFIX) }, preferences.all.filterKeys { it.startsWith(SumirePopupRepository.PREFIX) })
        repository.select(second.id)
        assertEquals("C", active.load("は").maps.single().tapText)
        assertEquals("か", active.load("か").maps.single().tapText)
        assertEquals(second.id, repository.selectedId())
    }
    @Test fun namedSnapshotDoesNotSelectOrModifyInputAndDuplicateGetsAnIndependentId() {
        active.save(shaped())
        val selected = repository.selectedId()
        val saved = repository.snapshot("Snapshot")
        val duplicate = repository.duplicate(saved.id, "Copy")
        assertEquals(saved.keys, duplicate.keys); assertNotEquals(saved.id, duplicate.id)
        assertEquals(selected, repository.selectedId())
        active.save(modified("は", "NEW"), saved.id)
        assertEquals(shaped(), duplicate.keys.first { it.keyId == "は" })
        assertEquals(shaped(), active.load("は"))
    }
    @Test fun renamingAndDeletingAreScopedAndDeletingSelectedUsesTheBuiltin() {
        val first = repository.create("First"); val second = repository.create("Second")
        repository.rename(first.id, "Renamed")
        repository.select(first.id); repository.delete(second.id)
        assertEquals("Renamed", repository.find(repository.selectedId()).name)
        repository.delete(first.id)
        assertEquals(FreeFlickLayoutRepository.BUILTIN_ID, repository.selectedId())
        assertEquals(repository.builtin().keys, SumirePopupDefaults.rows.keys.map { active.load(it) })
        assertThrows(IllegalArgumentException::class.java) { repository.delete(FreeFlickLayoutRepository.BUILTIN_ID) }
        assertThrows(IllegalArgumentException::class.java) { active.save(modified("は", "STALE"), first.id) }
    }
    @Test fun exportedV1AndV2MapsShapesAndTransitionsRoundTripWithoutSelectingTheImport() {
        active.save(shaped())
        val source = repository.snapshot("Shapes")
        val json = FreeFlickLayoutCodec.encode(source)
        val parsed = FreeFlickLayoutCodec.read(ByteArrayInputStream(json.toByteArray()))
        assertEquals(source.keys, parsed.keys)
        assertEquals("sumire-free-flick-layout", JSONObject(json).getString("format"))
        assertFalse(JSONObject(json).has("id"))
        val selected = repository.selectedId()
        val imported = repository.importLayout(parsed); val again = repository.importLayout(parsed)
        assertEquals("Shapes (2)", imported.name); assertEquals("Shapes (3)", again.name)
        assertNotEquals(imported.id, source.id); assertEquals(source.keys, imported.keys)
        assertEquals(selected, repository.selectedId())
        repository.select(imported.id)
        assertEquals(shaped(), active.load("は"))
    }
    @Test fun invalidImportsCannotPartiallyChangeTheLibraryOrActiveKeys() {
        val layout = repository.create("Safe")
        val valid = FreeFlickLayoutCodec.encode(layout)
        val before = preferences.all
        val invalid = listOf<(JSONObject) -> Unit>(
            { it.put("version", 99) },
            { it.getJSONArray("keys").remove(0) },
            { it.getJSONArray("keys").put(0, it.getJSONArray("keys").getJSONObject(1)) },
            { it.getJSONArray("keys").getJSONObject(0).getJSONArray("maps").getJSONObject(0).remove("tapText") },
            { it.getJSONArray("keys").getJSONObject(0).getJSONArray("maps").getJSONObject(0).getJSONArray("regions").getJSONObject(0).getJSONObject("action").put("type", "UNKNOWN") },
            { it.getJSONArray("keys").getJSONObject(0).getJSONArray("maps").getJSONObject(0).getJSONArray("regions").getJSONObject(0).getJSONObject("bounds").put("left", "-1.5") },
            { it.getJSONArray("keys").getJSONObject(0).getJSONArray("maps").getJSONObject(0).getJSONArray("regions").getJSONObject(0).getJSONObject("bounds").put("left", -1000) },
            { it.getJSONArray("keys").getJSONObject(0).put("startMapId", "missing") },
            { it.getJSONArray("keys").getJSONObject(0).getJSONArray("maps").getJSONObject(0).getJSONArray("regions").getJSONObject(0).put("outline", JSONObject("{\"vertices\":[{\"x\":0,\"y\":0}]}")) }
        )
        invalid.forEach { change ->
            val json = JSONObject(valid).also(change).toString()
            assertThrows(Exception::class.java) { repository.importLayout(FreeFlickLayoutCodec.decode(json)) }
            assertEquals(before, preferences.all)
        }
        for (json in listOf("{broken", valid + " trailing", "[".repeat(100) + "]".repeat(100))) {
            assertThrows(Exception::class.java) { FreeFlickLayoutCodec.decode(json) }
        }
        assertThrows(IllegalArgumentException::class.java) { FreeFlickLayoutCodec.read(ByteArrayInputStream(ByteArray(FreeFlickLayoutCodec.MAX_BYTES + 1))) }
        assertEquals(before, preferences.all)
    }
    @Test fun backupRestoresLibrarySelectionAndAllRuntimeKeys() {
        val layout = repository.create("Portable"); active.save(shaped(), layout.id); repository.select(layout.id)
        val before = repository.list()
        val backup = AppPreference.exportAllToJson()
        preferences.edit().clear().commit(); AppPreference.importAllFromJson(backup)
        assertEquals(before, repository.list()); assertEquals(layout.id, repository.selectedId()); assertEquals(shaped(), active.load("は"))
    }
    @Test fun corruptLibraryIsPreservedAndDoesNotChangeInput() {
        active.save(modified("は", "KEEP"))
        preferences.edit().putString(FreeFlickLayoutRepository.STORAGE_KEY, "{broken").commit()
        val before = preferences.all
        assertThrows(Exception::class.java) { repository.list() }
        assertEquals(before, preferences.all); assertEquals("KEEP", active.load("は").maps.single().tapText)
    }
    @Test fun anUnreadableRuntimeKeyDoesNotMarkAnUnselectedSavedLayoutAsInvalid() {
        val first = repository.create("Active"); val second = repository.create("Saved")
        repository.select(first.id)
        preferences.edit().putString(SumirePopupRepository.PREFIX + "は", "{broken").commit()
        assertTrue(active.hasInvalidSavedConfig("は", first.id))
        assertFalse(active.hasInvalidSavedConfig("は", second.id))
        assertEquals(SumirePopupDefaults.create("は"), active.load("は", second.id))
    }
    private fun legacyDefaults(key: String): PopupKeyConfig {
        val config = SumirePopupDefaults.create(key)
        val old = listOf(PopupBounds(-1.5f,-.4f,-.6f,.4f), PopupBounds(-.45f,-1.4f,.45f,-.6f),
            PopupBounds(.6f,-.4f,1.5f,.4f), PopupBounds(-.45f,.6f,.45f,1.4f))
        return config.copy(maps=config.maps.map { map -> map.copy(regions=map.regions.map {
            it.copy(bounds=old[it.id.substringAfterLast('-').toInt()])
        }) })
    }
    @Test fun untouchedBuiltinRuntimeMovesToTheGaplessTemplateButCopiesStayExact() {
        repository.selectedId()
        val copy = repository.create("Saved old template")
        val oldKeys = SumirePopupDefaults.rows.keys.map(::legacyDefaults)
        oldKeys.forEach { active.save(it, copy.id) }
        preferences.edit().also { editor -> oldKeys.forEach { editor.putString(SumirePopupRepository.PREFIX+it.keyId,active.encode(it)) } }.commit()
        assertEquals(repository.builtin().keys, SumirePopupDefaults.rows.keys.map { active.load(it) })
        assertEquals(oldKeys, repository.find(copy.id).keys)
        val after = preferences.all
        repository.list(); active.load("は")
        assertEquals(after, preferences.all)
        repository.select(copy.id)
        assertEquals(oldKeys, SumirePopupDefaults.rows.keys.map { active.load(it) })
    }
    @Test fun editedRuntimeWhileBuiltinSelectedIsNeverMigrated() {
        repository.selectedId()
        val old = legacyDefaults("は")
        val edited = old.withMap(old.maps.single().copy(tapText="CUSTOM"))
        preferences.edit().putString(SumirePopupRepository.PREFIX+"は",active.encode(edited)).commit()
        val before = preferences.all
        assertEquals(edited, active.load("は")); assertEquals(before,preferences.all)
    }

}
