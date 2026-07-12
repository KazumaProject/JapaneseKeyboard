package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideValidator
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProductionConnectionMatrixRegressionTest {

    @Test
    fun dictionaryBinaryReaderLoadsAndCachesShortArrayBackedConnectionMatrix() {
        val reader = productionDictionaryReader()

        val first = reader.loadConnectionMatrix(DictionaryFileKey.CONNECTION_ID)
        val second = reader.loadConnectionMatrix(DictionaryFileKey.CONNECTION_ID)

        assertSame(first, second)
        assertEquals(first.matrixSize * first.matrixSize, first.entryCount)
        assertFalse(first.javaClass.name.contains("ByteBuffer"))
    }

    @Test
    fun productionConnectionMatrixKeepsCandidateOrderAndBunsetsuResults() = runBlocking {
        val productionMatrix = productionDictionaryReader().loadConnectionMatrix(DictionaryFileKey.CONNECTION_ID)
        val productionEngine = TestEngineFactory.create(connectionMatrix = productionMatrix)
        val referenceEngine = TestEngineFactory.create()
        val userDictionaryRepository = mock<UserDictionaryRepository>()
        whenever(userDictionaryRepository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())

        regressionInputs.forEach { input ->
            val expected = referenceEngine.convertForRegression(input, userDictionaryRepository)
            val actual = productionEngine.convertForRegression(input, userDictionaryRepository)

            assertEquals("candidates changed for $input", expected.candidates, actual.candidates)
            assertEquals("splitPatterns changed for $input", expected.splitPatterns, actual.splitPatterns)
            assertEquals(
                "splitPatternByCandidateString changed for $input",
                expected.splitPatternByCandidateString,
                actual.splitPatternByCandidateString,
            )
        }
    }

    @Test
    fun bunsetsuConversionExposesSequentialSplitPositionWithMozcParityEnabled() = runBlocking {
        val engine = TestEngineFactory.create()
        val userDictionaryRepository = mock<UserDictionaryRepository>()
        whenever(userDictionaryRepository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())

        val result = engine.convertForRegression(
            input = "きょうはいいてんきですね",
            userDictionaryRepository = userDictionaryRepository,
        )

        assertEquals("今日はいい天気ですね", result.candidates.first().string)
        assertEquals(listOf(4), result.primarySplitPositions)
    }

    private suspend fun KanaKanjiEngine.convertForRegression(
        input: String,
        userDictionaryRepository: UserDictionaryRepository,
    ): BunsetsuCandidateResult =
        getCandidatesWithBunsetsuSeparation(
            input = input,
            n = 20,
            mozcUtPersonName = false,
            mozcUTPlaces = false,
            mozcUTWiki = false,
            mozcUTNeologd = false,
            mozcUTWeb = false,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = null,
            isOmissionSearchEnable = false,
            enableTypoCorrectionJapaneseFlick = false,
            enableTypoCorrectionQwertyEnglish = false,
            typoCorrectionOffsetScore = 3000,
            omissionSearchOffsetScore = 1900,
            beamWidth = 20,
        )

    private fun productionDictionaryReader(): DictionaryBinaryReader {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        return DictionaryBinaryReader(
            resolver = DictionarySourceResolver(context, store),
            store = store,
        )
    }

    private companion object {
        val regressionInputs = listOf(
            "きょう",
            "きょうはいいてんきですね",
            "とうきょうとちよだく",
            "けいたいでにほんごをへんかんする",
            "わたしはきのうともだちとえきまえであいました",
            "このあぷりのへんかんこうほをこうそくにしたい",
        )
    }
}
