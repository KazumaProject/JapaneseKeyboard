package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryRelease286InstrumentedTest {
    @Test fun swipePreferenceUsesThePublishedSurfaceAcrossBackendsAndModes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(entry.kanaKanjiEngine(), backend)
            for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) for (bunsetsu in listOf(false, true)) {
                val result = session.query(KanaKanjiQueryRequest(
                    input = "すわいぷ", mode = mode, bunsetsuSeparation = bunsetsu, n = 20,
                    enabledDictionaryCategories = emptySet(),
                    userDictionaryRepository = entry.userDictionaryRepository(), learnRepository = null,
                    omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
                    typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 0,
                    omissionSearchOffsetScore = 0, beamWidth = 100,
                ))
                assertEquals("$backend $mode bunsetsu=$bunsetsu: ${result.candidates.take(5)}",
                    "スワイプ", result.candidates.first().string)
            }
        }
    }
}
