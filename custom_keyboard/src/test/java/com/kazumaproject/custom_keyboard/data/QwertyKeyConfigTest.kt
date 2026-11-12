package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.*
import org.junit.Test

class QwertyKeyConfigTest {
    private val normal = QwertyKeyLayer("a", "letter", "preview", 2,
        listOf(QwertyPopupSlot("á"), QwertyPopupSlot(), QwertyPopupSlot("👩‍💻", "work")))

    @Test fun backupRetainsLabelsEmptySlotsAndUnicodeStrings() {
        val config = QwertyKeyConfig(normal, normal.copy(output = "lowercase override", columns = 1))
        assertEquals(config, QwertyKeyConfigCodec.decode(QwertyKeyConfigCodec.encode(config)))
        assertEquals(2, normal.rowCount)
        assertEquals("👩‍💻", config.normal.slots[2].output)
    }

    @Test fun missingShiftUsesAsciiUppercaseWhileExplicitShiftIsVerbatim() {
        val automatic = QwertyKeyConfig(normal).layer(KeyCharacterCase.UPPERCASE)
        assertEquals("A", automatic.output)
        assertEquals("LETTER", automatic.displayLabel)
        assertEquals("á", automatic.slots[0].output)
        val explicit = normal.copy(output = "keepCase", label = "Aa")
        assertEquals(explicit, QwertyKeyConfig(normal, explicit).layer(KeyCharacterCase.UPPERCASE))
    }

    @Test fun malformedAndUnboundedDefinitionsAreRejected() {
        listOf("{}", "null", "{", "{\"normal\":null}",
            "{\"normal\":{\"output\":\"a\",\"columns\":0}}",
            "{\"normal\":{\"output\":\"a\",\"slots\":[null]}}",
            "{\"normal\":{\"output\":\"a\",\"slots\":null}}")
            .forEach { assertNull(it, QwertyKeyConfigCodec.decode(it)) }
        assertFalse(QwertyKeyConfig(normal.copy(slots = List(121) { QwertyPopupSlot() })).isValid())
    }

    @Test fun defaultsKeepBlankSlotsAndUseOutputAsDisplay() {
        assertEquals("address", QwertyPopupSlot("address").displayLabel)
        assertEquals(" ", QwertyPopupSlot(" ").output)
        assertEquals("a", QwertyKeyLayer("a").previewLabel)
        assertNotNull(QwertyKeyConfigCodec.decode(QwertyKeyConfigCodec.encode(QwertyKeyConfig()), requireOutput = false))
    }
}
