package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import org.junit.Assert.*
import org.junit.Test

class FeverEngineTest {
    private var time = 0L
    private var draws = 0
    private fun engine(settings: FeverSettings = FeverSettings(), roll: Int = 0) =
        FeverEngine(settings, { time }, { draws++; roll })

    @Test fun oneLogicalOperationCountsOnceAndExpiresAtTheConfiguredBoundary() {
        val e = engine()
        assertEquals(1, e.character(1)!!.combo)
        assertNull(e.character(1))
        time = 2999; assertEquals(2, e.character(2)!!.combo)
        time = 5999; assertEquals(1, e.character(3)!!.combo)
        time = 8999; assertTrue(e.expireCombo()); assertEquals(0, e.combo)
    }
    @Test fun milestonesEscalateAndRepeatEveryFiftyAfterOneHundred() {
        val e = engine()
        val awards = mutableMapOf<Int, FeverAward>()
        for (i in 1..150) {
            time += 250
            e.character(i.toLong())?.award?.let { awards[i] = it }
        }
        assertEquals(mapOf(10 to FeverAward.COMBO, 25 to FeverAward.FEVER,
            50 to FeverAward.HOT, 100 to FeverAward.JACKPOT, 150 to FeverAward.JACKPOT), awards)
    }
    @Test fun failedAndEmptyCommitsNeverConsumeAnOperationOrDraw() {
        val e = engine(FeverSettings(hitRate = 100))
        assertNull(e.committed(1, "abc", false)); assertNull(e.committed(1, "", true))
        assertEquals(0, draws)
        assertEquals(FeverAward.JACKPOT, e.committed(1, "abc", true)!!.award)
        assertNull(e.committed(1, "abc", true)); assertEquals(1, draws)
        assertEquals(0, e.combo) // candidate confirmation itself is not a character
    }
    @Test fun zeroAndOneHundredPercentAndTheThreeSecondDrawInterval() {
        val e = engine(FeverSettings(hitRate = 0))
        assertNull(e.committed(1, "a", true)); assertEquals(0, draws)
        e.configure(FeverSettings(hitRate = 100))
        assertEquals(FeverAward.JACKPOT, e.committed(2, "b", true)!!.award)
        time = 2999; assertNull(e.committed(3, "c", true)); assertEquals(1, draws)
        time = 3000; assertEquals(FeverAward.JACKPOT, e.committed(4, "d", true)!!.award)
        assertEquals(2, draws)
    }
    @Test fun keywordNormalizationEditingAndDisabledReactions() {
        val e = engine(FeverSettings(hitRate = 0, keywords = listOf("ＦＥＶＥＲ", "好き")))
        assertEquals(FeverAward.HOT, e.committed(1, "I like fever and 好き", true)!!.award)
        e.configure(FeverSettings(hitRate = 0, keywords = listOf("別の語")))
        time += 2100
        assertNull(e.committed(2, "fever", true))
        assertEquals(FeverAward.HOT, e.committed(3, "別の語", true)!!.award)
        e.configure(FeverSettings(hitRate = 0, keywordsEnabled = false, keywords = listOf("別の語")))
        time += 2100; assertNull(e.committed(4, "別の語", true))
        assertEquals(0, draws)
    }
    @Test fun weakerEventsNeverReplaceAnActiveJackpotAndResetClearsSession() {
        val e = engine(FeverSettings(hitRate = 100))
        assertEquals(FeverAward.JACKPOT, e.committed(1, "a", true)!!.award)
        assertNull(e.committed(2, "最高", true))
        e.reset(); assertEquals(0, e.combo)
        assertEquals(FeverAward.HOT, e.committed(1, "最高", true)!!.award)
    }
}
