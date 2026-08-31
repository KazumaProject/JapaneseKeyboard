package com.kazumaproject.markdownhelperkeyboard.text_macro

import org.junit.Assert.assertEquals
import org.junit.Test

class TextMacroEditorDocumentTest {
    @Test
    fun supportsMoveEditDeleteAndLiteralBraceRoundTrip() {
        val parsed = TextMacroEditorDocument.parse("A{{B}}{date:yyyy}{newline}C")
        assertEquals(4, parsed.blocks.size)

        val changed = parsed
            .move(2, 1)
            .replace(2, TextMacroEditorBlock.Token("time", "HH:mm"))
            .remove(3)
            .add(TextMacroEditorBlock.Token("cursor"))

        assertEquals("A{{B}}{newline}{time:HH:mm}{cursor}", changed.toSource())
        TextMacroCompiler.compile(changed.toSource())
    }
}

