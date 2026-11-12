package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.*
import org.junit.Assert.*
import org.junit.Test

class QwertyKeyBackupTest {
    @Test fun backupRoundTripRetainsBothLayersLabelsAndBlankPositions() {
        val config = QwertyKeyConfig(QwertyKeyLayer("a", "label", "preview", 2,
            listOf(QwertyPopupSlot("á", "first"), QwertyPopupSlot(), QwertyPopupSlot("👩‍💻"))),
            QwertyKeyLayer("lowercase", "SHIFT", "Custom", 1, listOf(QwertyPopupSlot("Address", "住所"))))
        val key = KeyDefinition(ownerLayoutId = 1, label = "label", row = 0, column = 0,
            keyType = KeyType.QWERTY, keyIdentifier = "qwerty", action = KeyActionMapper.fromKeyAction(KeyAction.Text("a")),
            qwertyConfigJson = QwertyKeyConfigCodec.encode(config))
        val layout = FullKeyboardLayout(CustomKeyboardLayout(layoutId = 1, name = "Test", columnCount = 1, rowCount = 1),
            listOf(KeyWithFlicks(key, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())), emptyList())
        val json = KeyboardLayoutJsonExporter.toJson(listOf(layout))
        val result = KeyboardLayoutJsonImporter.parse(json) as KeyboardLayoutImportResult.Success
        val restored = result.layouts.single().keysWithFlicks.single().key
        assertEquals(KeyType.QWERTY, restored.keyType)
        assertEquals(config, QwertyKeyConfigCodec.decode(restored.qwertyConfigJson))
        assertEquals("a", (KeyActionMapper.toKeyAction(restored.action) as KeyAction.Text).text)
    }

    @Test fun invalidQwertyPayloadIsReportedInsteadOfDroppingItsSettings() {
        val result = KeyboardLayoutJsonImporter.parse("""{"schemaVersion":6,"layouts":[{"layout":{"name":"Broken","columnCount":1,"rowCount":1},"keysWithFlicks":[{"key":{"keyType":"QWERTY","row":0,"column":0,"qwerty":{"normal":{"output":"a","columns":0}}}}]}]}""")
        assertTrue(result is KeyboardLayoutImportResult.Failure || result is KeyboardLayoutImportResult.PartialSuccess)
    }
}
