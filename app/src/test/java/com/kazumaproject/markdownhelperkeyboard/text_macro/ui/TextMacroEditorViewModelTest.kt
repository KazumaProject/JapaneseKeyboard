package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.repository.TextMacroRepository
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroEditorBlock
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroVariable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextMacroEditorViewModelTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var viewModel: TextMacroEditorViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        viewModel = TextMacroEditorViewModel(
            repository = TextMacroRepository(database.textMacroDao()),
            savedStateHandle = SavedStateHandle(
                mapOf(TextMacroEditorViewModel.ARG_MACRO_ID to 0L)
            ),
            context = context,
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun supportsInsertionMoveDeleteUndoAndInlinePatternEditing() {
        viewModel.selectInsertionIndex(0)
        viewModel.addVariable(TextMacroVariable.DATE)
        val dateId = viewModel.uiState.value.blocks.first().editorId
        viewModel.updateTokenPattern(dateId, "yyyy-MM-dd")
        viewModel.addVariable(TextMacroVariable.SELECTION)

        assertTrue(viewModel.uiState.value.source.startsWith("{date:yyyy-MM-dd}{selection}"))
        assertTrue(viewModel.moveBlockBy(dateId, 1))
        assertTrue(viewModel.uiState.value.source.startsWith("{selection}{date:yyyy-MM-dd}"))

        viewModel.removeBlock(dateId)
        assertNotNull(viewModel.uiState.value.removedBlock)
        assertFalse(viewModel.uiState.value.source.contains("{date"))
        viewModel.undoRemove()
        assertTrue(viewModel.uiState.value.source.contains("{date:yyyy-MM-dd}"))
    }

    @Test
    fun invalidSourceCannotSwitchToBlocksAndReportsOneBasedPosition() {
        viewModel.showSourceMode()
        viewModel.setSource("abc{")

        assertFalse(viewModel.showBlockMode())
        assertEquals(TextMacroEditorMode.SOURCE, viewModel.uiState.value.mode)
        assertEquals(4, viewModel.uiState.value.syntaxErrorPosition)

        viewModel.setSource("A{{B}}{selection}{cursor}")
        assertTrue(viewModel.showBlockMode())
        assertEquals(TextMacroEditorMode.BLOCKS, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.blocks.any {
            (it.block as? TextMacroEditorBlock.Token)?.name == "selection"
        })
        assertTrue(viewModel.uiState.value.containsCursor)
    }

    @Test
    fun navigationArgumentsKeepNewAndExistingMacroIds() {
        assertEquals(
            0L,
            TextMacroEditorNavigation.arguments(0L)
                .getLong(TextMacroEditorViewModel.ARG_MACRO_ID),
        )
        assertEquals(
            42L,
            TextMacroEditorNavigation.arguments(42L)
                .getLong(TextMacroEditorViewModel.ARG_MACRO_ID),
        )
    }
}
