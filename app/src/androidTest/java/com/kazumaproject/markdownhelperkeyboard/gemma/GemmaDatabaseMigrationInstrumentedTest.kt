package com.kazumaproject.markdownhelperkeyboard.gemma

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.repository.GemmaPromptTemplateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the production 40 -> 41 migration against the database installed on the device. */
@RunWith(AndroidJUnit4::class)
class GemmaDatabaseMigrationInstrumentedTest {
    @Test
    fun migrateExistingDatabaseAndSeedMediaActions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, AppDatabase::class.java, "learn_database")
            .addMigrations(AppDatabase.MIGRATION_40_41)
            .build()
        try {
            assertEquals(41, database.openHelper.writableDatabase.version)
            val repository = GemmaPromptTemplateRepository(database.gemmaPromptTemplateDao())
            repository.ensureBuiltIns()
            val keys = database.gemmaPromptTemplateDao().getBuiltInKeys()
            assertEquals(8, keys.size)
            assertTrue(keys.contains("image_extract_text"))
            assertTrue(keys.contains("audio_dictate"))
        } finally {
            database.close()
        }
    }
}
