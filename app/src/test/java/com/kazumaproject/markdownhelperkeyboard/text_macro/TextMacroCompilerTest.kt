package com.kazumaproject.markdownhelperkeyboard.text_macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class TextMacroCompilerTest {
    private val fixedContext = TextMacroContext(
        selection = "{date}",
        clipboard = "{time}",
        locale = Locale.US,
        timeZone = TimeZone.getTimeZone("America/Toronto"),
        timestampMillis = 1_788_098_706_000L,
    )

    @Test
    fun expandsAllVariablesAtOneFixedInstant() {
        val expanded = TextMacroCompiler.compile(
            "{date:yyyy-MM-dd}|{time:HH:mm:ss}|{selection}|{clipboard}|{newline}x{cursor}y"
        ).expand(fixedContext)

        assertEquals("2026-08-30|10:05:06|{date}|{time}|\nxy", expanded.text)
        assertEquals(expanded.text.length - 1, expanded.cursorOffset)
    }

    @Test
    fun localizedDefaultsUseProvidedLocaleAndTimeZone() {
        val expanded = TextMacroCompiler.compile("{date} {time}").expand(fixedContext)
        assertEquals("8/30/26 10:05 AM", expanded.text)
    }

    @Test
    fun escapedBracesAreLiteralAndContextValuesAreNotRecursivelyExpanded() {
        val expanded = TextMacroCompiler.compile("{{date}} {selection} {clipboard}")
            .expand(fixedContext)
        assertEquals("{date} {date} {time}", expanded.text)
    }

    @Test
    fun reportsSyntaxErrorPosition() {
        val error = assertThrows(TextMacroSyntaxException::class.java) {
            TextMacroCompiler.compile("abc{unknown}")
        }
        assertEquals(3, error.position)
    }

    @Test
    fun rejectsInvalidSyntaxAndPatterns() {
        listOf(
            "{date",
            "}",
            "{clipboard:x}",
            "{date:}",
            "{date:'}",
            "{cursor}{cursor}",
            "{outer:{date}}",
        ).forEach { body ->
            assertThrows(body, TextMacroSyntaxException::class.java) {
                TextMacroCompiler.compile(body)
            }
        }
    }

    @Test
    fun exposesContextRequirementsWithoutExpanding() {
        val compiled = TextMacroCompiler.compile("{selection}{clipboard}{date}")
        assertEquals(
            setOf(
                TextMacroContextRequirement.SELECTION,
                TextMacroContextRequirement.CLIPBOARD,
            ),
            compiled.requirements,
        )
    }

    @Test
    fun enforcesDefinitionAndExpansionLimits() {
        assertThrows(TextMacroSyntaxException::class.java) {
            TextMacroValidator.validateDefinition("n".repeat(81), null, "body")
        }
        assertThrows(TextMacroSyntaxException::class.java) {
            TextMacroValidator.validateDefinition("name", "r".repeat(65), "body")
        }
        assertThrows(TextMacroSyntaxException::class.java) {
            TextMacroCompiler.compile("b".repeat(TextMacroLimits.BODY + 1))
        }
        assertThrows(TextMacroSyntaxException::class.java) {
            TextMacroCompiler.compile("{date:${"y".repeat(TextMacroLimits.TOKEN)}}")
        }

        val hugeContext = fixedContext.copy(selection = "s".repeat(TextMacroLimits.EXPANDED + 1))
        assertThrows(TextMacroSyntaxException::class.java) {
            TextMacroCompiler.compile("{selection}").expand(hugeContext)
        }
    }

    @Test
    fun cursorDefaultsToEndAndSupportsStartMiddleAndEnd() {
        val noCursor = TextMacroCompiler.compile("ab").expand(fixedContext)
        val start = TextMacroCompiler.compile("{cursor}ab").expand(fixedContext)
        val middle = TextMacroCompiler.compile("a{cursor}b").expand(fixedContext)
        val end = TextMacroCompiler.compile("ab{cursor}").expand(fixedContext)

        assertEquals(2, noCursor.cursorOffset)
        assertEquals(0, start.cursorOffset)
        assertEquals(1, middle.cursorOffset)
        assertEquals(2, end.cursorOffset)
    }

    @Test
    fun rejectsMissingRequiredContext() {
        val empty = TextMacroContext(locale = Locale.US, timeZone = TimeZone.getTimeZone("UTC"))
        assertThrows(TextMacroSyntaxException::class.java) {
            TextMacroCompiler.compile("{selection}").expand(empty)
        }
        assertThrows(TextMacroSyntaxException::class.java) {
            TextMacroCompiler.compile("{clipboard}").expand(empty)
        }
        assertFalse(TextMacroCompiler.compile("{date}").requirements.isNotEmpty())
        assertTrue(TextMacroCompiler.compile("{selection}").requirements.isNotEmpty())
    }

}
