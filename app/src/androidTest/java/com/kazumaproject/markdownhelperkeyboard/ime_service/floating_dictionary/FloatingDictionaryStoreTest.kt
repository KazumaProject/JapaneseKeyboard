package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_dictionary

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingDictionaryStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var store: FloatingDictionaryStore
    private lateinit var learn: LearnRepository
    private lateinit var user: UserDictionaryRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        learn = LearnRepository(db.learnDao())
        user = UserDictionaryRepository(db.userWordDao())
        store = FloatingDictionaryStore(learn, user, UserTemplateRepository(db.userTemplateDao()))
    }
    @After fun close() { db.close() }

    @Test fun allThreeDictionariesSupportCrudAndRejectDuplicateAddsAndEdits() = runBlocking {
        for (kind in DictionaryKind.entries) {
            val entry = DictionaryEntry(0, "てすと", "テスト", 3000)
            store.save(kind, entry, true)
            try { store.save(kind, entry.copy(score = 1), true); fail("Duplicate must fail: $kind") }
            catch (_: SQLiteConstraintException) { }
            val id = id(kind, "てすと")
            store.save(kind, entry.copy(reading = "べつ", word = "別"), true)
            try { store.save(kind, entry.copy(id = id, reading = "べつ", word = "別"), false); fail("Duplicate edit must fail: $kind") }
            catch (_: SQLiteConstraintException) { }
            store.save(kind, entry.copy(id = id, reading = "へんこう", word = "変更", score = 2000), false)
            assertEquals(id, id(kind, "へんこう"))
            store.delete(kind, entry.copy(id = id))
            assertEquals(1, count(kind))
        }
    }

    @Test fun learnedMetadataAndConversionCacheSurviveEditing() = runBlocking {
        val original = LearnEntity("てすと", "テスト", 3000, 1, 2, 17, 123456L, true)
        learn.insertStrict(original)
        assertEquals("テスト", learn.findExactMatchesForConversion("てすと").single().out)
        val saved = db.learnDao().getAllSuspend().single()
        store.save(DictionaryKind.LEARN, DictionaryEntry(saved.id!!, "てすと", "変更", 1000), false)
        val updated = db.learnDao().getAllSuspend().single()
        assertEquals(saved.copy(out = "変更", score = 1000), updated)
        assertEquals("変更", learn.findExactMatchesForConversion("てすと").single().out)
        store.save(DictionaryKind.USER, DictionaryEntry(0, "てすと", "テスト", 3000), true)
        assertEquals("テスト", user.exactMatchesForConversion("てすと").single().word)
        val userId = id(DictionaryKind.USER, "てすと")
        store.save(DictionaryKind.USER, DictionaryEntry(userId, "てすと", "変更", 1000), false)
        assertEquals("変更", user.exactMatchesForConversion("てすと").single().word)
    }

    private suspend fun id(kind: DictionaryKind, reading: String): Int = when (kind) {
        DictionaryKind.LEARN -> db.learnDao().getAllSuspend().single { it.input == reading }.id!!
        DictionaryKind.USER -> db.userWordDao().getAllSuspend().single { it.reading == reading }.id
        DictionaryKind.TEMPLATE -> db.userTemplateDao().getAllSuspend().single { it.reading == reading }.id
    }
    private suspend fun count(kind: DictionaryKind): Int = when (kind) {
        DictionaryKind.LEARN -> db.learnDao().getAllSuspend().size
        DictionaryKind.USER -> db.userWordDao().getAllSuspend().size
        DictionaryKind.TEMPLATE -> db.userTemplateDao().getAllSuspend().size
    }
}
