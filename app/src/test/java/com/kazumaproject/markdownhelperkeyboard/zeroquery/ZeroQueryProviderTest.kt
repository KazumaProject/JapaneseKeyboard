package com.kazumaproject.markdownhelperkeyboard.zeroquery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZeroQueryProviderTest {

    @Test
    fun lookup_returnsNormalZeroQuerySuggestion() {
        val values = provider().lookup("あけまして").map { it.value }

        assertEquals(listOf("おめでとうございます"), values)
    }

    @Test
    fun lookup_returnsNumberZeroQuerySuggestionInExpectedOrder() {
        val values = provider().lookup("30").map { it.value }

        assertEquals(listOf("分", "日", "年", "歳", "代"), values.take(5))
    }

    @Test
    fun lookup_returnsNumberZeroQuerySuggestionFor10() {
        val values = provider().lookup("10").map { it.value }

        assertEquals(listOf("分", "時", "月", "年", "個"), values.take(5))
    }

    @Test
    fun lookup_normalizesFullWidthNumberKey() {
        val provider = provider()

        assertEquals(
            provider.lookup("10").map { it.value },
            provider.lookup("１０").map { it.value }
        )
    }

    @Test
    fun lookup_appendsDefaultNumberSuffix() {
        val values = provider().lookup("30").map { it.value }

        assertTrue(values.contains("円"))
        assertTrue(values.indexOf("円") > values.indexOf("分"))
    }

    @Test
    fun lookup_deduplicatesPreservingOrder() {
        val values = provider().lookup("30").map { it.value }

        assertEquals(values.distinct(), values)
        assertEquals(listOf("分", "日", "年", "歳", "代"), values.take(5))
    }

    @Test
    fun lookup_filtersBlankSuggestions() {
        val values = provider().lookup("30").map { it.value }

        assertFalse(values.any { it.isBlank() })
    }

    @Test
    fun getIfEnabled_doesNotInitializeProviderWhenDisabled() {
        var factoryCalled = false
        val holder = LazyZeroQueryProvider {
            factoryCalled = true
            ZeroQueryProvider(
                object : ZeroQueryAssetReader {
                    override fun readBytes(path: String): ByteArray =
                        error("Reader must not be touched while zero-query is disabled")
                }
            )
        }

        assertNull(holder.getIfEnabled(enabled = false))
        assertFalse(factoryCalled)
        assertFalse(holder.isInitialized)
    }

    private fun provider(): ZeroQueryProvider {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return ZeroQueryProvider(AndroidZeroQueryAssetReader(context.assets))
    }
}
