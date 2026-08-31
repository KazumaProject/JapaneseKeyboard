package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.graph.Node
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemNgramAssetInstrumentedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        SystemNgramRuntime.resetForTesting()
    }

    @Test
    fun version3AndVersion4AssetsLoadAndMatchOnPhysicalDevice() {
        val dictionary = SystemNgramAssetLoader.load(context)

        assertEquals(1_715 + 471, dictionary.ruleCount)
        assertEquals(57_376 + 15_191, dictionary.storageBytes)
        assertTrue(dictionary.matchesSingleNode(node("カワボ")))
        assertFalse(dictionary.matchesSingleNode(node("存在しない表記")))
    }

    private fun node(word: String) = Node(
        l = 1.toShort(),
        r = 1.toShort(),
        score = 0,
        f = 0,
        tango = word,
        len = 1.toShort(),
        yomiUsed = word,
        sPos = 0,
    )
}
