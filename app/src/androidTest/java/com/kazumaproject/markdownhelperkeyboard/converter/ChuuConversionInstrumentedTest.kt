package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryRequest
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChuuConversionInstrumentedTest {
    @Test fun chuuSurvivesTypingAndAllQueryModes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        for (backend in ConversionBackend.entries) {
            for (bunsetsu in listOf(false, true)) {
                val session = KanaKanjiConversionSession(engine, backend)
                for (input in listOf("ちゅう", "ち", "ちゅ", "ちゅう")) {
                    for (mode in CandidateQueryMode.entries) {
                        val result = session.query(KanaKanjiQueryRequest(
                            input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = 8,
                            userDictionaryRepository = entry.userDictionaryRepository(), learnRepository = null,
                            omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
                            typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 3000,
                            omissionSearchOffsetScore = 3000, beamWidth = 20,
                        ))
                        assertTrue("$backend/$bunsetsu/$input/$mode", result.candidates.isNotEmpty())
                        if (input == "ちゅう" && mode != CandidateQueryMode.EISUKANA) {
                            assertTrue("Missing 中: $backend/$bunsetsu/$mode", result.candidates.any { it.string == "中" })
                        }
                    }
                }
            }
        }
    }
}
