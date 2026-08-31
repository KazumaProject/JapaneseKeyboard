package com.kazumaproject.markdownhelperkeyboard.text_macro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.text_macro.ui.presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextMacroVariableCatalogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun catalogExactlyMatchesTheSixSupportedVariables() {
        assertEquals(
            listOf("date", "time", "selection", "clipboard", "cursor", "newline"),
            TextMacroVariable.entries.map { it.tokenName },
        )
        assertTrue(TextMacroVariable.DATE.acceptsPattern)
        assertTrue(TextMacroVariable.TIME.acceptsPattern)
        assertFalse(TextMacroVariable.SELECTION.acceptsPattern)
    }

    @Test
    fun everyCatalogItemHasANameExplanationExampleAndRestriction() {
        TextMacroVariable.entries.forEach { variable ->
            val presentation = variable.presentation(context)
            assertTrue(variable.tokenName, presentation.title.isNotBlank())
            assertEquals(variable.source(), presentation.syntax)
            assertTrue(variable.tokenName, presentation.description.isNotBlank())
            assertTrue(variable.tokenName, presentation.example.isNotBlank())
            assertTrue(variable.tokenName, presentation.restriction?.isNotBlank() == true)
        }
    }

    @Test
    fun editorUsesUserFacingNameAndCallKeywordTerms() {
        assertEquals("Macro name", context.getString(R.string.text_macro_name_label))
        assertTrue(context.getString(R.string.text_macro_name_helper).contains("suggestions"))
        assertEquals(
            "Call keyword (optional)",
            context.getString(R.string.text_macro_call_keyword_label),
        )
        assertTrue(context.getString(R.string.text_macro_call_keyword_helper).contains("exact"))
        assertFalse(context.getString(R.string.text_macro_name_label).contains("Unique"))
    }
}
