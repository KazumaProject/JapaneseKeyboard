package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.graph.Node
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemNgramRuntimeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SystemNgramRuntime.resetForTesting()
    }

    @After
    fun tearDown() {
        SystemNgramRuntime.resetForTesting()
    }

    @Test
    fun coldDisabledStateDoesNotLoadAndReenableReusesOneDictionary() {
        SystemNgramRuntime.initialize(context, false)
        assertEquals(0, SystemNgramRuntime.current().ruleCount)
        assertEquals(0, SystemNgramRuntime.loadedDictionary().ruleCount)

        SystemNgramRuntime.setEnabled(context, true)
        val loaded = SystemNgramRuntime.loadedDictionary()
        assertTrue(loaded.ruleCount > 0)
        assertSame(loaded, SystemNgramRuntime.current())

        SystemNgramRuntime.setEnabled(context, false)
        assertEquals(0, SystemNgramRuntime.current().ruleCount)
        assertSame(loaded, SystemNgramRuntime.loadedDictionary())

        SystemNgramRuntime.setEnabled(context, true)
        assertSame(loaded, SystemNgramRuntime.current())
    }

    @Test
    fun loadsVersion3AndVersion4AssetsAsOneDictionary() {
        SystemNgramRuntime.initialize(context, true)

        val loaded = SystemNgramRuntime.loadedDictionary()
        assertEquals(1_715 + 471, loaded.ruleCount)
        assertEquals(57_376 + 15_191, loaded.storageBytes)
        assertTrue(loaded.matchesSingleNode(node("カワボ")))
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
