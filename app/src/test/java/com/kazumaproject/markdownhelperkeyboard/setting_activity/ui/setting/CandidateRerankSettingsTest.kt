package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.annotation.XmlRes
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidateRerankSettingsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val modelActions = listOf("candidate_scoring_model", "candidate_scoring_tokenizer", "candidate_scoring_clear")
    private val route = "setting_route_candidate_rerank"

    @Before
    fun initializePreferences() {
        AppPreference.init(context)
    }

    @Test
    fun zenzSetupPrecedesTheModelTools() {
        val zenz = preferenceKeys(R.xml.pref_zenz)
        assertEquals("enable_ai_conversion_zenz_preference", zenz.first())
        assertTrue(route in zenz)
        assertFalse(zenz.any { it in modelActions })
        val rerank = preferenceKeys(R.xml.pref_candidate_rerank)
        assertEquals(listOf("candidate_rerank_current", "candidate_rerank_zenz_setup", "candidate_rerank_custom_model"), rerank.take(3))
        assertEquals(modelActions, rerank.drop(3))
    }

    @Test
    fun bothSearchModesOpenTheNewPageOnSupportedEditions() {
        for (scope in SettingSearchScope.entries) {
            val index = SettingSearchIndex.searchable(context, scope)
            assertFalse(index.any { it.key == "candidate_rerank_current" })
            for (key in listOf(route) + modelActions) {
                val matches = index.filter { it.key == key }
                if (!AppVariantConfig.hasZenz) {
                    assertTrue("$scope $key", matches.isEmpty())
                    continue
                }
                val destination = matches.single().destination
                assertEquals(R.id.candidateRerankPreferenceFragment, SettingDestinations.destinationId(destination))
                assertEquals(key.takeIf { it != route }, SettingDestinations.highlightPreferenceKey(destination))
            }
        }
    }

    private fun preferenceKeys(@XmlRes xml: Int): List<String> = context.resources.getXml(xml).use { parser ->
        buildList {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    parser.getAttributeValue("http://schemas.android.com/apk/res/android", "key")?.let(::add)
                }
                parser.next()
            }
        }
    }
}
