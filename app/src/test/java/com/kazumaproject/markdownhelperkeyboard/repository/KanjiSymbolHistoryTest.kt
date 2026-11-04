package com.kazumaproject.markdownhelperkeyboard.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KanjiSymbolHistoryTest {
    @Test
    fun repeatsMoveKanjiToTheFrontAndRemovalIsModeSpecific() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val history = ClickedSymbolRepository(database.clickedSymbolDao())
            history.insert(SymbolMode.SYMBOL, "一")
            history.insert(SymbolMode.KANJI, "一")
            history.insert(SymbolMode.KANJI, "𠮟")
            history.insert(SymbolMode.KANJI, "一")

            assertEquals(
                listOf(SymbolMode.KANJI to "一", SymbolMode.KANJI to "𠮟", SymbolMode.SYMBOL to "一"),
                history.getAll().map { it.mode to it.symbol },
            )
            history.delete(SymbolMode.KANJI, "一")
            assertEquals(
                listOf(SymbolMode.KANJI to "𠮟", SymbolMode.SYMBOL to "一"),
                history.getAll().map { it.mode to it.symbol },
            )
        } finally {
            database.close()
        }
    }
}
