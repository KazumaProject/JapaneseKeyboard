package com.kazumaproject.markdownhelperkeyboard.converter.date

/** The supported renderings for daily date candidates. Preference values are persisted IDs. */
enum class DateCandidateFormat(val preferenceValue: String) {
    YEAR_MONTH_DAY("yyyy_mm_dd"),
    MONTH_DAY("m_d"),
    MONTH_DAY_WEEKDAY("m_month_d_day_weekday"),
    REIWA_DATE("reiwa_date"),
    REIWA_SHORT_DATE("reiwa_short_date"),
    WEEKDAY_SHORT("weekday_short"),
    WEEKDAY_LONG("weekday_long"),
}

data class DateCandidateConfig(
    val order: List<DateCandidateFormat> = DEFAULT_ORDER,
    val enabledFormats: Set<DateCandidateFormat> = DateCandidateFormat.entries.toSet(),
) {
    /** Removes duplicate or missing persisted entries while retaining a complete stable order. */
    val normalizedOrder: List<DateCandidateFormat>
        get() = buildList {
            val seen = mutableSetOf<DateCandidateFormat>()
            order.forEach { format ->
                if (seen.add(format)) add(format)
            }
            DEFAULT_ORDER.forEach { format ->
                if (seen.add(format)) add(format)
            }
        }

    companion object {
        val DEFAULT_ORDER: List<DateCandidateFormat> = listOf(
            DateCandidateFormat.YEAR_MONTH_DAY,
            DateCandidateFormat.MONTH_DAY,
            DateCandidateFormat.MONTH_DAY_WEEKDAY,
            DateCandidateFormat.REIWA_DATE,
            DateCandidateFormat.REIWA_SHORT_DATE,
            DateCandidateFormat.WEEKDAY_SHORT,
            DateCandidateFormat.WEEKDAY_LONG,
        )
    }
}
