package com.kazumaproject.markdownhelperkeyboard.update

import android.app.Application
import android.content.res.Resources
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.model.LearnResult
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnUpdateResult
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary.UserDictionaryViewModel
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary.UserWordUpdateResult
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplateDao
import com.kazumaproject.markdownhelperkeyboard.user_template.ui.UserTemplateUpdateResult
import com.kazumaproject.markdownhelperkeyboard.user_template.ui.UserTemplateViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserDictionarySafeUpdateTest {

    @Test
    fun userTemplateUpdateSafely_returnsDuplicateOnlyForDifferentItemWithSameKey() = runTest {
        val dao = FakeUserTemplateDao(
            mutableListOf(
                UserTemplate(id = 1, word = "x", reading = "y", posIndex = 0, posScore = 4000),
                UserTemplate(id = 2, word = "a", reading = "b", posIndex = 0, posScore = 4000)
            )
        )
        val viewModel = UserTemplateViewModel(
            UserTemplateRepository(dao),
            fakeApplication()
        )

        val duplicateResult = viewModel.updateSafely(
            UserTemplate(id = 2, word = "x", reading = "y", posIndex = 0, posScore = 4000)
        )
        val sameItemResult = viewModel.updateSafely(
            UserTemplate(id = 1, word = "x", reading = "y", posIndex = 0, posScore = 4000)
        )

        assertEquals(UserTemplateUpdateResult.Duplicate, duplicateResult)
        assertEquals(UserTemplateUpdateResult.Updated, sameItemResult)
    }

    @Test
    fun learnUpdateSafely_returnsDuplicateOnlyForDifferentItemWithSameKey() = runTest {
        val dao = FakeLearnDao(
            mutableListOf(
                LearnEntity(id = 1, input = "x", out = "y"),
                LearnEntity(id = 2, input = "a", out = "b")
            )
        )
        val repository = LearnRepository(dao)

        val duplicateResult = repository.updateSafely(
            LearnEntity(id = 2, input = "x", out = "y")
        )
        val sameItemResult = repository.updateSafely(
            LearnEntity(id = 1, input = "x", out = "y")
        )

        assertEquals(LearnUpdateResult.Duplicate, duplicateResult)
        assertEquals(LearnUpdateResult.Updated, sameItemResult)
    }

    @Test
    fun userWordUpdateSafely_returnsDuplicateOnlyForDifferentItemWithSameKey() = runTest {
        val dao = FakeUserWordDao(
            mutableListOf(
                UserWord(id = 1, word = "x", reading = "y", posIndex = 0, posScore = 4000),
                UserWord(id = 2, word = "a", reading = "b", posIndex = 0, posScore = 4000)
            )
        )
        val viewModel = UserDictionaryViewModel(
            UserDictionaryRepository(dao),
            fakeApplication()
        )

        val duplicateResult = viewModel.updateSafely(
            UserWord(id = 2, word = "x", reading = "y", posIndex = 0, posScore = 4000)
        )
        val sameItemResult = viewModel.updateSafely(
            UserWord(id = 1, word = "x", reading = "y", posIndex = 0, posScore = 4000)
        )

        assertEquals(UserWordUpdateResult.Duplicate, duplicateResult)
        assertEquals(UserWordUpdateResult.Updated, sameItemResult)
    }

    private fun fakeApplication(): Application {
        val application = mock<Application>()
        val resources = mock<Resources>()
        whenever(resources.getStringArray(com.kazumaproject.core.R.array.parts_of_speech))
            .thenReturn(arrayOf("名詞"))
        whenever(application.resources).thenReturn(resources)
        return application
    }
}

private class FakeUserTemplateDao(
    private val templates: MutableList<UserTemplate>
) : UserTemplateDao {
    override fun getAll(): LiveData<List<UserTemplate>> = MutableLiveData(templates)

    override suspend fun getAllSuspend(): List<UserTemplate> = templates

    override fun searchByReadingExact(reading: String): LiveData<List<UserTemplate>> =
        MutableLiveData(templates.filter { it.reading == reading })

    override suspend fun searchByReadingExactSuspend(
        reading: String,
        limit: Int
    ): List<UserTemplate> = templates.filter { it.reading == reading }.take(limit)

    override suspend fun existsDuplicateForUpdate(
        word: String,
        reading: String,
        excludeId: Int
    ): Boolean = templates.any { it.word == word && it.reading == reading && it.id != excludeId }

    override suspend fun insert(userTemplate: UserTemplate) {
        if (templates.none { it.word == userTemplate.word && it.reading == userTemplate.reading }) {
            templates.add(userTemplate)
        }
    }

    override suspend fun insertAll(templates: List<UserTemplate>) {
        templates.forEach { insert(it) }
    }

    override suspend fun update(userTemplate: UserTemplate) {
        if (existsDuplicateForUpdate(userTemplate.word, userTemplate.reading, userTemplate.id)) {
            throw SQLiteConstraintException()
        }
        val index = templates.indexOfFirst { it.id == userTemplate.id }
        if (index >= 0) templates[index] = userTemplate
    }

    override suspend fun delete(id: Int) {
        templates.removeAll { it.id == id }
    }

    override suspend fun deleteAll() {
        templates.clear()
    }
}

private class FakeLearnDao(
    private val entries: MutableList<LearnEntity>
) : LearnDao {
    override suspend fun insert(learnData: LearnEntity) {
        if (entries.none { it.input == learnData.input && it.out == learnData.out }) {
            entries.add(learnData)
        }
    }

    override suspend fun findByInput(input: String): List<LearnResult>? = entries
        .filter { it.input == input }
        .map { LearnResult(it.out, it.score.toInt()) }

    override suspend fun findByInputAndOutput(input: String, output: String): LearnEntity? =
        entries.firstOrNull { it.input == input && it.out == output }

    override suspend fun existsDuplicateForUpdate(
        input: String,
        output: String,
        excludeId: Int
    ): Boolean = entries.any { it.input == input && it.out == output && it.id != excludeId }

    override fun all(): Flow<List<LearnEntity>> = flowOf(entries)

    override suspend fun getAllSuspend(): List<LearnEntity> = entries

    override suspend fun insertAll(learnDataList: List<LearnEntity>) {
        learnDataList.forEach { insert(it) }
    }

    override suspend fun predictiveSearchByInput(prefix: String, limit: Int): List<LearnEntity> =
        entries.filter { it.input.startsWith(prefix) }.take(limit)

    override suspend fun findCommonPrefixes(searchTerm: String): List<LearnEntity> =
        entries.filter { searchTerm.startsWith(it.input) }

    override suspend fun updateLearnedData(learnData: LearnEntity) {
        val id = learnData.id ?: return
        if (existsDuplicateForUpdate(learnData.input, learnData.out, id)) {
            throw SQLiteConstraintException()
        }
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) entries[index] = learnData
    }

    override suspend fun delete(learnData: LearnEntity) {
        entries.removeAll { it.id == learnData.id }
    }

    override suspend fun deleteAll() {
        entries.clear()
    }

    override suspend fun deleteByInput(input: String): Int {
        val before = entries.size
        entries.removeAll { it.input == input }
        return before - entries.size
    }

    override suspend fun deleteByInputAndOutput(input: String, output: String): Int {
        val before = entries.size
        entries.removeAll { it.input == input && it.out == output }
        return before - entries.size
    }
}

private class FakeUserWordDao(
    private val words: MutableList<UserWord>
) : UserWordDao {
    override fun getAll(): LiveData<List<UserWord>> = MutableLiveData(words)

    override suspend fun getAllSuspend(): List<UserWord> = words

    override fun searchByReadingPrefix(prefix: String): LiveData<List<UserWord>> =
        MutableLiveData(words.filter { it.reading.startsWith(prefix) })

    override suspend fun searchByReadingPrefixSuspend(
        prefix: String,
        limit: Int
    ): List<UserWord> = words.filter { it.reading.startsWith(prefix) }.take(limit)

    override suspend fun searchByReadingExactSuspend(reading: String): List<UserWord> =
        words.filter { it.reading == reading }

    override suspend fun existsDuplicateForUpdate(
        word: String,
        reading: String,
        excludeId: Int
    ): Boolean = words.any { it.word == word && it.reading == reading && it.id != excludeId }

    override suspend fun commonPrefixSearchInUserDict(inputStr: String): List<UserWord> =
        words.filter { inputStr.startsWith(it.reading) }

    override suspend fun insert(userWord: UserWord) {
        if (words.none { it.word == userWord.word && it.reading == userWord.reading }) {
            words.add(userWord)
        }
    }

    override suspend fun insertAll(words: List<UserWord>) {
        words.forEach { insert(it) }
    }

    override suspend fun update(userWord: UserWord) {
        if (existsDuplicateForUpdate(userWord.word, userWord.reading, userWord.id)) {
            throw SQLiteConstraintException()
        }
        val index = words.indexOfFirst { it.id == userWord.id }
        if (index >= 0) words[index] = userWord
    }

    override suspend fun delete(id: Int) {
        words.removeAll { it.id == id }
    }

    override suspend fun deleteAll() {
        words.clear()
    }
}
