package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading

/** One label policy for the real strip, the preview and physical-keyboard candidates. */
internal fun candidateLabelPresentation(
    context: Context,
    candidate: Candidate,
    showDictionaryLabels: Boolean,
    showCharacterIndicators: Boolean,
    displayText: String = candidate.string,
): CharSequence {
    fun label(id: Int) = context.getString(id)
    fun widthLabel(): String = when {
        showCharacterIndicators -> ""
        displayText.isNotEmpty() && displayText.all { it in '０'..'９' || it == '　' || it in '！'..'／' || it in '：'..'＠' || it in '［'..'｀' || it in '｛'..'～' } -> label(R.string.candidate_label_full_width)
        displayText.isNotEmpty() && displayText.all { it in ' '..'~' && !it.isLetter() } -> label(R.string.candidate_label_half_width)
        else -> ""
    }
    val base: CharSequence = when (candidate.type) {
        CANDIDATE_TYPE_PARTIAL -> label(R.string.candidate_label_partial)
        CANDIDATE_TYPE_SYMBOL, CANDIDATE_TYPE_NUMBER_SYMBOL -> widthLabel()
        CANDIDATE_TYPE_FULL_WIDTH_NUMBER, CANDIDATE_TYPE_FULL_WIDTH ->
            if (showCharacterIndicators) "" else label(R.string.candidate_label_full_width)
        CANDIDATE_TYPE_HALF_WIDTH ->
            if (showCharacterIndicators) "" else label(R.string.candidate_label_half_width)
        CANDIDATE_TYPE_DATE, CANDIDATE_TYPE_DIGIT_DATE -> label(R.string.candidate_label_date)
        CANDIDATE_TYPE_READING_CORRECTION -> {
            val prefix = label(R.string.candidate_label_reading) + " "
            SpannableString(prefix + candidate.string.correctReading().second).apply {
                if (length > prefix.length) setSpan(RelativeSizeSpan(1.25f), prefix.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        CANDIDATE_TYPE_ZENZ, CANDIDATE_TYPE_ZENZ_LOCAL_FALLBACK,
        CANDIDATE_TYPE_ZENZ_REGENERATED, 42.toByte(), 44.toByte() -> label(R.string.candidate_label_ai)
        41.toByte(), 43.toByte() -> label(R.string.candidate_label_translation)
        CANDIDATE_TYPE_ENGLISH_CORRECTION -> label(R.string.candidate_label_correction)
        CANDIDATE_TYPE_CALCULATION -> label(R.string.candidate_badge_calculation)
        CANDIDATE_TYPE_UNIT_CONVERSION -> label(R.string.candidate_badge_unit_conversion)
        CANDIDATE_TYPE_FORMULA_UNICODE -> label(R.string.candidate_badge_formula_unicode)
        CANDIDATE_TYPE_FORMULA_TEX -> label(R.string.candidate_badge_formula_tex)
        CANDIDATE_TYPE_TEXT_MACRO -> label(R.string.candidate_label_macro)
        else -> ""
    }
    val result = SpannableStringBuilder()
    fun append(part: CharSequence) {
        if (part.isEmpty()) return
        if (result.isNotEmpty()) result.append(" ")
        result.append(part)
    }
    append(base)
    if (showDictionaryLabels) {
        val originLabel = when {
            CandidateDictionaryOrigin.LEARNED_DICTIONARY in candidate.dictionaryOrigins -> R.string.candidate_label_learned
            CandidateDictionaryOrigin.USER_DICTIONARY in candidate.dictionaryOrigins -> R.string.candidate_label_user
            CandidateDictionaryOrigin.USER_TEMPLATE in candidate.dictionaryOrigins -> R.string.candidate_label_template
            else -> null
        }
        originLabel?.let { append(label(it)) }
    }
    if (showCharacterIndicators && candidate.presentation == null) {
        val forms = characterKindLabels(context, displayText)
        if (forms.isNotEmpty()) append("[$forms]")
    }
    append(classifiedCandidateLabel(context, candidate.dictionaryCategories))
    return result
}
