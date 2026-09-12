package com.kazumaproject.markdownhelperkeyboard.custom_romaji

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.ui.RomajiMapFragment
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RomajiBehaviorPersistenceTest {
    private fun parse(json: String): List<RomajiMapEntity> = ReflectionHelpers.callInstanceMethod(
        RomajiMapFragment(), "parseImportMaps", ClassParameter.from(String::class.java, json))

    @Test fun oldImportsDefaultOnAndAllSettingCombinationsRoundTrip() {
        val old = """[{"name":"old","mapData":{"pp":{"first":"っ","second":2}}}]"""
        assertTrue(parse(old).single().autoSokuon)
        assertTrue(parse(old).single().autoN)
        for (sokuon in listOf(false, true)) for (n in listOf(false, true)) {
            val map = RomajiMapEntity(name = "custom", mapData = mapOf("pp" to ("っ" to 2)), autoSokuon = sokuon, autoN = n)
            val restored = parse(Gson().toJson(listOf(map))).single()
            assertEquals(map, restored)
            assertEquals(sokuon, map.copy(name = "copy").autoSokuon)
            assertEquals(n, map.copy(name = "copy").autoN)
        }
    }

    @Test fun supportedLegacyShapesKeepTheirRulesAndEnableBothSettings() {
        val nodes = listOf(
            """{"kana":"っ","consume":2}""",
            """{"first":"っ","second":2}""",
            """{"c":"っ","d":2}"""
        )
        for (node in nodes) for (mapKey in listOf("mapData", "map", "data")) {
            val map = """{"pp":$node}"""
            for (payload in listOf(map, Gson().toJson(map))) {
                val item = """{"name":"legacy","$mapKey":$payload}"""
                for (root in listOf(item, "[$item]", """{"maps":[$item]}""")) {
                    val restored = parse(root).single()
                    assertEquals(mapOf("pp" to ("っ" to 2)), restored.mapData)
                    assertTrue(restored.autoSokuon)
                    assertTrue(restored.autoN)
                }
            }
        }
    }

    @Test fun migrationPreservesRulesAndSettingsSurviveReopening() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "romaji-migration-${System.nanoTime()}.db"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_47_48).allowMainThreadQueries().build()
        suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
            val database = open()
            try { block(database) } finally { database.close() }
        }
        try {
            withDatabase { database ->
                val db = database.openHelper.writableDatabase
                db.execSQL("DROP TABLE romaji_maps")
                db.execSQL("CREATE TABLE romaji_maps (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, mapData TEXT NOT NULL, isActive INTEGER NOT NULL, isDeletable INTEGER NOT NULL)")
                val data = """{"pp":{"first":"っ","second":2}}"""
                db.execSQL("INSERT INTO romaji_maps VALUES (1, 'custom', ?, 1, 1)", arrayOf(data))
                db.execSQL("INSERT INTO romaji_maps VALUES (2, 'default', ?, 0, 0)", arrayOf(data))
                db.execSQL("PRAGMA user_version = 47")
            }
            withDatabase { database ->
                val dao = database.romajiMapDao()
                val migrated = dao.getMapById(1).first()!!
                assertEquals(mapOf("pp" to ("っ" to 2)), migrated.mapData)
                assertTrue(migrated.autoSokuon)
                assertTrue(migrated.autoN)
                dao.setAutoSokuon(1, false)
                dao.setAutoN(1, false)
                dao.setAutoSokuon(2, false)
                dao.setAutoN(2, false)
                assertTrue(dao.getMapById(2).first()!!.autoSokuon)
                assertTrue(dao.getMapById(2).first()!!.autoN)
            }
            withDatabase { database ->
                val restored = database.romajiMapDao().getMapById(1).first()!!
                assertFalse(restored.autoSokuon)
                assertFalse(restored.autoN)
                assertEquals("custom", restored.name)
                assertTrue(restored.isActive)
            }
        } finally { context.deleteDatabase(name) }
    }
}
