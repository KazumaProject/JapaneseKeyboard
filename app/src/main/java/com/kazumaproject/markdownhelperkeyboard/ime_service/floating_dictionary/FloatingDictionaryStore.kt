package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_dictionary

import androidx.lifecycle.asFlow
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal enum class DictionaryKind(val shortcut: ShortcutType, val title: Int) {
    LEARN(ShortcutType.FLOATING_LEARN_DICTIONARY, R.string.floating_dictionary_learn),
    USER(ShortcutType.FLOATING_USER_DICTIONARY, R.string.floating_dictionary_user),
    TEMPLATE(ShortcutType.FLOATING_USER_TEMPLATE, R.string.floating_dictionary_template),
}

internal data class DictionaryEntry(
    val id: Int,
    val reading: String,
    val word: String,
    val score: Int,
    val pos: Int = 0,
)

/** Shares the settings dictionaries and their conversion-cache invalidation paths. */
internal class FloatingDictionaryStore(
    private val learn: LearnRepository,
    private val user: UserDictionaryRepository,
    private val template: UserTemplateRepository,
) {
    fun observe(kind: DictionaryKind): Flow<List<DictionaryEntry>> = when (kind) {
        DictionaryKind.LEARN -> learn.all().map { rows -> rows.map {
            DictionaryEntry(requireNotNull(it.id), it.input, it.out, it.score)
        }.sortedWith(compareBy<DictionaryEntry> { it.reading }.thenBy { it.score }) }
        DictionaryKind.USER -> user.allWords.asFlow().map { rows -> rows.map {
            DictionaryEntry(it.id, it.reading, it.word, it.posScore, it.posIndex)
        } }
        DictionaryKind.TEMPLATE -> template.allTemplates.asFlow().map { rows -> rows.map {
            DictionaryEntry(it.id, it.reading, it.word, it.posScore, it.posIndex)
        } }
    }

    suspend fun save(kind: DictionaryKind, entry: DictionaryEntry, adding: Boolean) {
        when (kind) {
            DictionaryKind.LEARN -> {
                if (adding) learn.insertStrict(LearnEntity(input = entry.reading, out = entry.word, score = entry.score))
                else learn.editEntry(entry.id, entry.reading, entry.word, entry.score)
            }
            DictionaryKind.USER -> {
                val value = UserWord(entry.id, entry.word, entry.reading, entry.pos, entry.score)
                if (adding) user.insertStrict(value) else user.editEntry(value)
            }
            DictionaryKind.TEMPLATE -> {
                val value = UserTemplate(entry.id, entry.word, entry.reading, entry.pos, entry.score)
                if (adding) template.insertStrict(value) else template.editEntry(value)
            }
        }
    }

    suspend fun delete(kind: DictionaryKind, entry: DictionaryEntry) {
        when (kind) {
            DictionaryKind.LEARN -> learn.deleteById(entry.id)
            DictionaryKind.USER -> user.delete(entry.id)
            DictionaryKind.TEMPLATE -> template.delete(entry.id)
        }
    }
}
