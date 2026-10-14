package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory

internal fun classifiedCandidateLabel(context: Context, categories: Set<DictionaryCategory>): String {
    val labels = ClassifiedDictionaries.ordered(categories).map { context.getString(it.labelRes) }
    return if (labels.isEmpty()) "" else labels.joinToString("・", "[", "]")
}
