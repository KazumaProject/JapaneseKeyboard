package com.kazumaproject.markdownhelperkeyboard.converter.date

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

object DateCandidateProvider {
    private const val BASE_SCORE = 7000
    private const val DATE_PART_OF_SPEECH_ID: Short = 1851

    private val japaneseWeekdays = listOf(
        "日", "月", "火", "水", "木", "金", "土",
    )

    private val formatsByValue = DateCandidateFormat.entries.associateBy { it.preferenceValue }

    fun format(format: DateCandidateFormat, calendar: Calendar): String {
        // Render a Gregorian date in the supplied timezone. Locale.ROOT prevents locale-specific
        // calendar systems and digits (for example, Arabic-Indic digits) from leaking into output.
        val date = GregorianCalendar(calendar.timeZone, Locale.ROOT).apply {
            timeInMillis = calendar.timeInMillis
        }
        val year = date.get(Calendar.YEAR)
        val month = date.get(Calendar.MONTH) + 1
        val day = date.get(Calendar.DAY_OF_MONTH)
        val weekday = japaneseWeekdays[date.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY]
        val reiwaYear = year - 2018

        return when (format) {
            DateCandidateFormat.YEAR_MONTH_DAY -> "$year/${month.pad2()}/${day.pad2()}"
            DateCandidateFormat.MONTH_DAY -> "$month/$day"
            DateCandidateFormat.MONTH_DAY_WEEKDAY -> "${month}月${day}日($weekday)"
            DateCandidateFormat.REIWA_DATE -> "令和${reiwaYear}年${month}月${day}日"
            DateCandidateFormat.REIWA_SHORT_DATE -> "R${reiwaYear}/${month.pad2()}/${day.pad2()}"
            DateCandidateFormat.WEEKDAY_SHORT -> "${weekday}曜"
            DateCandidateFormat.WEEKDAY_LONG -> "${weekday}曜日"
        }
    }

    fun provide(
        calendar: Calendar,
        input: String,
        config: DateCandidateConfig = DateCandidateConfig(),
    ): List<Candidate> {
        val order = config.normalizedOrder
        return order.mapIndexedNotNull { rank, format ->
            if (format !in config.enabledFormats) return@mapIndexedNotNull null
            Candidate(
                string = format(format, calendar),
                type = 14,
                length = input.length.toUByte(),
                score = BASE_SCORE + rank,
                leftId = DATE_PART_OF_SPEECH_ID,
                rightId = DATE_PART_OF_SPEECH_ID,
                dateFormat = format,
            )
        }
    }

    /** Parses persisted IDs, ignoring unknown values from newer or corrupted preference data. */
    fun formatForPreferenceValue(value: String): DateCandidateFormat? = formatsByValue[value]

    private fun Int.pad2(): String = toString().padStart(2, '0')
}
