package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.date.DateCandidateConfig
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryRequest
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversionTieOrderInstrumentedTest {
    @Test
    fun tiedCandidatesAndBunsetsuMatchAcrossInputHistories() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        engine.applyDictionaryOverrideState(context)
        val repository = entry.userDictionaryRepository()

        val inputs = listOf("えきをでたらみぎにまがる", "やくようしょくぶつしどうせんたー")
        val modes = listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)
        for (input in inputs) for (mode in modes) for (backend in ConversionBackend.entries) {
            val request = KanaKanjiQueryRequest(
                input = input,
                mode = mode,
                bunsetsuSeparation = true,
                n = 16,
                userDictionaryRepository = repository,
                learnRepository = null,
                omissionSearchEnabled = false,
                typoCorrectionJapaneseFlickEnabled = false,
                typoCorrectionQwertyEnglishEnabled = false,
                typoCorrectionOffsetScore = 3000,
                omissionSearchOffsetScore = 1900,
                beamWidth = 20,
                collectCandidateSegments = true,
                dateCandidateConfig = DateCandidateConfig(enabledFormats = emptySet()),
            )
            val label = "$input / $mode / $backend"
            val direct = KanaKanjiConversionSession(engine, backend).query(request)
            assertTrue(label, direct.candidates.isNotEmpty())
            assertNotNull(label, direct.bunsetsuResult)
            assertTrue(label, direct.candidateSegmentsByString.isNotEmpty())

            val session = KanaKanjiConversionSession(engine, backend)
            for (length in 1 until input.length) {
                session.query(request.copy(input = input.take(length)))
            }
            assertEquals("$label / typed", direct, session.query(request))
            session.query(request.copy(input = input.dropLast(1)))
            assertEquals("$label / delete and restore", direct, session.query(request))
        }
    }
}
