package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.graph.LearnedConversionRegressionTest
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LearnedConversionSessionTest {
    @Test
    fun exactNamesAndGeneralWordsKeepTheirPreferencesThroughEveryJapaneseQueryMode() = runBlocking {
        val engine = TestEngineFactory.create()
        val userDictionary = mock<UserDictionaryRepository>()
        whenever(userDictionary.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        whenever(userDictionary.exactMatchesForConversion(any())).thenReturn(emptyList())
        val history = listOf(
            LearnEntity("けい", "慧", score = 0, leftId = 1922, rightId = 1922, id = 1),
            LearnEntity("ちょう", "徴", score = 0, leftId = 1841, rightId = 1841, id = 2),
        )
        val repository = LearnRepository(LearnedConversionRegressionTest.InMemoryReadOnlyLearnDao(history))
        fun request(input: String, mode: CandidateQueryMode, bunsetsu: Boolean, learned: LearnRepository?) =
            KanaKanjiQueryRequest(
                input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = 4,
                userDictionaryRepository = userDictionary, learnRepository = learned,
                omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
                typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 3000,
                omissionSearchOffsetScore = 1900, beamWidth = 20,
            )
        val expressions = listOf("けいさん", "けいかいたいせい", "かんけいない",
            "ちょうし", "けんちょう", "かちょうふうげつ", "ちょうかい", "けいちょう")
        val modes = listOf(CandidateQueryMode.CONVERSION, CandidateQueryMode.PREDICTION, CandidateQueryMode.NO_TAB_DEFAULT)
        for (backend in ConversionBackend.entries) for (bunsetsu in listOf(false, true)) for (mode in modes) {
            val session = KanaKanjiConversionSession(engine, backend)
            for ((reading, name) in listOf("けい" to "慧", "ちょう" to "徴")) {
                assertEquals("$backend/$bunsetsu/$mode/$reading", name,
                    session.query(request(reading, mode, bunsetsu, repository)).candidates.first().string)
            }
            for (input in expressions) {
                val before = session.query(request(input, mode, bunsetsu, null)).candidates.first().string
                val after = session.query(request(input, mode, bunsetsu, repository)).candidates.first().string
                assertEquals("$backend/$bunsetsu/$mode/$input", before, after)
            }
        }
        assertEquals(history, repository.allSuspend())
    }
}
