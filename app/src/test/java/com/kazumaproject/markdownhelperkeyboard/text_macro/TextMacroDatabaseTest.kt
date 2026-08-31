package com.kazumaproject.markdownhelperkeyboard.text_macro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.repository.TextMacroRepository
import com.kazumaproject.markdownhelperkeyboard.text_macro.database.TextMacro
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextMacroDatabaseTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: TextMacroRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TextMacroRepository(database.textMacroDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun crudSearchAndIndividualEnableUseNameOrdering() = runBlocking {
        val beta = repository.save(TextMacro(name = "Beta", reading = "b", body = "B"))
        val alpha = repository.save(TextMacro(name = "Alpha", reading = "a", body = "find me"))

        assertEquals(listOf("Alpha", "Beta"), repository.observeAll().first().map { it.name })
        assertEquals(listOf("Alpha"), repository.search("find").first().map { it.name })
        repository.setEnabled(alpha, false)
        assertTrue(repository.getEnabledByReading("a").isEmpty())
        repository.deleteById(beta)
        assertEquals(listOf("Alpha"), repository.observeAll().first().map { it.name })
    }

    @Test
    fun backupIsStrictValidatedAndAppliedByNameWithoutIds() = runBlocking {
        repository.save(TextMacro(name = "Existing", reading = "old", body = "old"))
        val json = """
            {
              "version": 1,
              "macros": [
                {"name":"Existing","reading":"new","body":"{date}","enabled":false},
                {"name":"Added","reading":null,"body":"{{literal}}","enabled":true}
              ]
            }
        """.trimIndent()

        val plan = repository.prepareImport(json)
        assertEquals(1, plan.added)
        assertEquals(1, plan.overwritten)
        val counts = repository.applyImport(plan)
        assertEquals(1, counts.added)
        assertEquals(1, counts.overwritten)
        assertFalse(repository.getById(1)!!.enabled)

        val exported = repository.exportJson()
        assertFalse(exported.contains("\"id\""))
        assertTrue(exported.contains("\"version\":1"))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.prepareImport(
                    """{"version":1,"macros":[],"unknown":true}"""
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.prepareImport(
                    """{"version":1,"macros":[{"name":"Bad","reading":null,"body":"{unknown}","enabled":true}]}"""
                )
            }
        }
        Unit
    }
}
