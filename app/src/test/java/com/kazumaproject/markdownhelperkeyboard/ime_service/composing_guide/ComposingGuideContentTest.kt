package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import org.junit.Assert.*
import org.junit.Test

class ComposingGuideContentTest {
    @Test fun readingRequiresEveryDisplayConditionAndKeepsTheOriginalReading() {
        val content = ComposingGuideContent("今日は", "きょうは", true)
        assertEquals("きょうは", content.visibleReading(true, true))
        assertEquals("", content.visibleReading(false, true))
        assertEquals("", content.visibleReading(true, false))
        assertEquals("", content.copy(liveConversion = false).visibleReading(true, true))
        assertEquals("", content.copy(text = "").visibleReading(true, true))
        assertEquals("", ComposingGuideContent().visibleReading(true, true))
    }
}
