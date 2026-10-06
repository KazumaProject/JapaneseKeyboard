package com.kazumaproject.markdownhelperkeyboard.converter.date

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class DateCandidateProviderTest {
    @Test
    fun dateStringsUseJapaneseWeekdaysAndGregorianAsciiDigitsRegardlessOfDefaultLocale() {
        val originalLocale = Locale.getDefault()
        try {
            val calendar = dateCalendar(2026, Calendar.OCTOBER, 4, Locale.US)
            val expected = listOf(
                "2026/10/04",
                "10/4",
                "10月4日(日)",
                "令和8年10月4日",
                "R8/10/04",
                "日曜",
                "日曜日",
            )

            Locale.setDefault(Locale.US)
            val usStrings = DateCandidateFormat.entries.map { DateCandidateProvider.format(it, calendar) }
            Locale.setDefault(Locale("ar"))
            val arabicStrings = DateCandidateFormat.entries.map { DateCandidateProvider.format(it, calendar) }

            assertEquals(expected, usStrings)
            assertEquals(usStrings, arabicStrings)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun formattedDatesRemainGregorianAcrossMonthAndYearBoundaries() {
        val september = dateCalendar(2026, Calendar.SEPTEMBER, 30)
        val newYear = dateCalendar(2026, Calendar.DECEMBER, 31)

        assertEquals("2026/09/30", DateCandidateProvider.format(DateCandidateFormat.YEAR_MONTH_DAY, september))
        assertEquals("9月30日(水)", DateCandidateProvider.format(DateCandidateFormat.MONTH_DAY_WEEKDAY, september))
        assertEquals("2027/01/01", DateCandidateProvider.format(DateCandidateFormat.YEAR_MONTH_DAY, newYear.apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }))
    }

    @Test
    fun providerUsesConfiguredPriorityAndDisabledFormats() {
        val config = DateCandidateConfig(
            order = listOf(DateCandidateFormat.WEEKDAY_LONG, DateCandidateFormat.YEAR_MONTH_DAY),
            enabledFormats = setOf(DateCandidateFormat.YEAR_MONTH_DAY, DateCandidateFormat.WEEKDAY_LONG),
        )

        val candidates = DateCandidateProvider.provide(
            dateCalendar(2026, Calendar.OCTOBER, 4),
            "きょう",
            config,
        )

        assertEquals(
            listOf(DateCandidateFormat.WEEKDAY_LONG, DateCandidateFormat.YEAR_MONTH_DAY),
            candidates.map { it.dateFormat },
        )
        assertEquals(listOf(7000, 7001), candidates.map { it.score })
        assertEquals(listOf("日曜日", "2026/10/04"), candidates.map { it.string })
    }

    @Test
    fun defaultScoreOrderKeepsYearMonthDayFirstAcrossMonthBoundary() {
        listOf(
            dateCalendar(2026, Calendar.SEPTEMBER, 30),
            dateCalendar(2026, Calendar.OCTOBER, 4),
        ).forEach { calendar ->
            val first = DateCandidateProvider.provide(calendar, "きょう").sortedBy { it.score }.first()

            assertEquals(DateCandidateFormat.YEAR_MONTH_DAY, first.dateFormat)
        }
    }

    @Test
    fun invalidOrPartialOrderIsNormalizedWithoutDuplicates() {
        val config = DateCandidateConfig(
            order = listOf(
                DateCandidateFormat.WEEKDAY_LONG,
                DateCandidateFormat.WEEKDAY_LONG,
                DateCandidateFormat.MONTH_DAY,
            ),
        )

        assertEquals(
            listOf(
                DateCandidateFormat.WEEKDAY_LONG,
                DateCandidateFormat.MONTH_DAY,
                DateCandidateFormat.YEAR_MONTH_DAY,
                DateCandidateFormat.MONTH_DAY_WEEKDAY,
                DateCandidateFormat.REIWA_DATE,
                DateCandidateFormat.REIWA_SHORT_DATE,
                DateCandidateFormat.WEEKDAY_SHORT,
            ),
            config.normalizedOrder,
        )
        assertEquals(DateCandidateConfig.DEFAULT_ORDER, DateCandidateConfig(order = emptyList()).normalizedOrder)
    }

    @Test
    fun composerReordersAndFiltersOnlyTaggedDailyDateCandidates() {
        val calendar = dateCalendar(2026, Calendar.OCTOBER, 4)
        val dateCandidates = DateCandidateProvider.provide(calendar, "きょう")
        val prefix = Candidate("通常候補", 14, 3u, 10)
        val time = Candidate("12:30", 14, 3u, 20)
        val year = Candidate("2026年", 14, 3u, 30)
        val input = listOf(prefix) + dateCandidates + listOf(time, year)
        val config = DateCandidateConfig(
            order = listOf(
                DateCandidateFormat.WEEKDAY_LONG,
                DateCandidateFormat.YEAR_MONTH_DAY,
                DateCandidateFormat.WEEKDAY_SHORT,
            ),
            enabledFormats = setOf(
                DateCandidateFormat.WEEKDAY_LONG,
                DateCandidateFormat.YEAR_MONTH_DAY,
                DateCandidateFormat.WEEKDAY_SHORT,
            ),
        )

        val composed = DateCandidateComposer.compose("きょう", input, config)

        assertEquals(
            listOf("通常候補", "日曜日", "2026/10/04", "日曜", "12:30", "2026年"),
            composed.map { it.string },
        )
        assertEquals(listOf(prefix, time, year), composed.filter { it.dateFormat == null })
        assertEquals(listOf(7000, 7001, 7002), composed.filter { it.dateFormat != null }.map { it.score })

        val allHidden = DateCandidateComposer.compose(
            "きょう",
            input,
            config.copy(enabledFormats = emptySet()),
        )
        assertEquals(listOf("通常候補", "12:30", "2026年"), allHidden.map { it.string })
        assertFalse(allHidden.any { it.dateFormat != null })
        assertTrue(DateCandidateComposer.compose("こんにちは", input, config) === input)
    }

    @Test
    fun composerKeepsOrdinaryCandidatesBetweenDateSlots() {
        val dateCandidates = DateCandidateProvider.provide(
            dateCalendar(2026, Calendar.OCTOBER, 4),
            "きょう",
        ).associateBy { it.dateFormat }
        val prefix = Candidate("前の候補", 14, 3u, 10)
        val middle = Candidate("途中の候補", 14, 3u, 20)
        val suffix = Candidate("後ろの候補", 14, 3u, 30)
        val input = listOf(
            prefix,
            dateCandidates.getValue(DateCandidateFormat.YEAR_MONTH_DAY),
            middle,
            dateCandidates.getValue(DateCandidateFormat.MONTH_DAY),
            dateCandidates.getValue(DateCandidateFormat.MONTH_DAY_WEEKDAY),
            suffix,
        )
        val config = DateCandidateConfig(
            order = listOf(
                DateCandidateFormat.MONTH_DAY_WEEKDAY,
                DateCandidateFormat.YEAR_MONTH_DAY,
                DateCandidateFormat.MONTH_DAY,
            ),
            enabledFormats = setOf(
                DateCandidateFormat.MONTH_DAY_WEEKDAY,
                DateCandidateFormat.YEAR_MONTH_DAY,
                DateCandidateFormat.MONTH_DAY,
            ),
        )

        val composed = DateCandidateComposer.compose("きょう", input, config)

        assertEquals(
            listOf("前の候補", "10月4日(日)", "途中の候補", "2026/10/04", "10/4", "後ろの候補"),
            composed.map { it.string },
        )
        assertEquals(listOf(prefix, middle, suffix), composed.filter { it.dateFormat == null })
    }

    private fun dateCalendar(
        year: Int,
        month: Int,
        day: Int,
        locale: Locale = Locale.JAPAN,
    ): Calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), locale).apply {
        clear()
        set(year, month, day, 12, 0, 0)
    }
}
