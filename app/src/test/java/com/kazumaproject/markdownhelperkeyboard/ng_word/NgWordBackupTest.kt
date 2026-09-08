package com.kazumaproject.markdownhelperkeyboard.ng_word

import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordMatchMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NgWordBackupTest {
    @Test
    fun legacyBackupDefaultsToPartialAndDoesNotRestoreDatabaseIds() {
        val words = NgWordBackup.fromJson("""[{"id":42,"yomi":"きょう","tango":"今日"}]""")
        assertEquals(listOf(NgWord(yomi = "きょう", tango = "今日")), words)
    }

    @Test
    fun bothModesSurviveExportAndImport() {
        val words = listOf(
            NgWord(yomi = "きょう", tango = "今日"),
            NgWord(yomi = "あした", tango = "明日", matchMode = NgWordMatchMode.EXACT),
        )
        assertEquals(words, NgWordBackup.fromJson(NgWordBackup.toJson(words)))
    }

    @Test
    fun unknownAndNullModesDefaultToPartial() {
        for (mode in listOf("\"UNKNOWN\"", "null")) {
            assertEquals(
                listOf(NgWord(yomi = "きょう", tango = "今日")),
                NgWordBackup.fromJson("""[{"yomi":"きょう","tango":"今日","matchMode":$mode}]"""),
            )
        }
    }

    @Test
    fun invalidEntriesAreSkippedWithoutDiscardingValidEntries() {
        val json = """[{"yomi":" "},{"tango":"今日"},{"yomi":"きょう","tango":"今日","matchMode":"EXACT"}]"""
        assertEquals(
            listOf(NgWord(yomi = "きょう", tango = "今日", matchMode = NgWordMatchMode.EXACT)),
            NgWordBackup.fromJson(json),
        )
    }
}
