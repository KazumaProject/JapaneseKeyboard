package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnglishReadingDictionaryInstrumentedTest {

    @Test
    fun englishReadingCandidatesFollowTheSetting() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val hadPreviousValue = preferences.contains(
            DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE
        )
        val previousValue = preferences.getBoolean(
            DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE,
            true,
        )
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()

        try {
            setEnabled(preferences, true)
            engine.applyDictionaryOverrideState(context)
            val enabledCandidates = convert(engine, repository, "かー", nBest = 4)
            val enabledEnglishCases = enabledCandidates.map { it.string }.toSet()
            assertTrue(
                "English reading case candidates were not found: $enabledCandidates",
                setOf("car", "Car", "CAR").all(enabledEnglishCases::contains),
            )
            val galleryCandidates = convert(engine, repository, "ぎゃらりー")
            assertTrue(
                "Gallery candidate was not found: $galleryCandidates",
                galleryCandidates.any { it.string == "gallery" },
            )
            val artGalleryCandidates = convert(engine, repository, "あーとぎゃらりー")
            assertTrue(
                "Art gallery candidate was not found: $artGalleryCandidates",
                artGalleryCandidates.any { it.string == "art gallery" },
            )

            setEnabled(preferences, false)
            engine.applyDictionaryOverrideState(context)
            val disabledCandidates = convert(engine, repository, "かー")
            assertFalse(
                "English reading case candidates remained after disabling: $disabledCandidates",
                disabledCandidates.any { it.string in setOf("car", "Car", "CAR") },
            )
        } finally {
            if (hadPreviousValue) {
                setEnabled(preferences, previousValue)
            } else {
                preferences.edit()
                    .remove(DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE)
                    .commit()
            }
            engine.applyDictionaryOverrideState(context)
        }
    }

    private suspend fun convert(
        engine: KanaKanjiEngine,
        repository: com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository,
        input: String,
        nBest: Int = 64,
    ) = engine.getCandidatesWithBunsetsuSeparation(
        input = input,
        n = nBest,
        mozcUtPersonName = false,
        mozcUTPlaces = false,
        mozcUTWiki = false,
        mozcUTNeologd = false,
        mozcUTWeb = false,
        userDictionaryRepository = repository,
        learnRepository = null,
        isOmissionSearchEnable = false,
        enableTypoCorrectionJapaneseFlick = false,
        enableTypoCorrectionQwertyEnglish = false,
        typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900,
        beamWidth = 20,
    ).candidates

    private fun setEnabled(
        preferences: android.content.SharedPreferences,
        enabled: Boolean,
    ) {
        preferences.edit()
            .putBoolean(DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE, enabled)
            .commit()
    }
}
