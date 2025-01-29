package com.kazumaproject.markdownhelperkeyboard.mozc_ut_dictionaries

import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class BuildMozcUTDictionaryTest {

    private lateinit var kanaKanjiEngine: KanaKanjiEngine

    @Before
    fun setup() {
        kanaKanjiEngine = KanaKanjiEngine()
        kanaKanjiEngine.buildPersonNamesDictionary(ApplicationProvider.getApplicationContext())
        kanaKanjiEngine.buildPlaceDictionary(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        kanaKanjiEngine.releasePersonNamesDictionary()
        kanaKanjiEngine.releasePlacesDictionary()
    }

    @Test
    fun buildMozcUTPersonName() = runBlocking {
        val a = kanaKanjiEngine.getMozcUTPersonNames("わんふぁいぶ")
        val b = kanaKanjiEngine.getMozcUTPersonNames("わたなべゆういちろう")
        println("mozc ut person name: $a")
        println("mozc ut person name: $b")
    }

    @Test
    fun buildMozcUTPlace() = runBlocking {
        val a = kanaKanjiEngine.getMozcUTPlace("わっかないしそうやむらみねおか")
        val b = kanaKanjiEngine.getMozcUTPlace("わたらいぐんわたらいちょうまきど")
        println("mozc ut person name: $a")
        println("mozc ut person name: $b")
    }

}