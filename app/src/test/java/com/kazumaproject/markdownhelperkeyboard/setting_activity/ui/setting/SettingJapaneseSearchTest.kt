package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingJapaneseSearchTest {
    @Test fun previousLabelsStillFindTheCanonicalSettingAfterWordingChanges() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        AppPreference.init(base)
        val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.JAPANESE) })
        val queries = mapOf(
            "フリック・トグル入力" to "flick_input_only_preference",
            "候補ハイライト時の削除キー動作" to "henkan_delete_key_action_preference",
            "日本語の予測変換" to "japanese_prediction_enable_preference",
            "キー・変換候補の文字サイズ" to "keyboard_key_letter_size_fragment_preference",
        )
        for (scope in SettingSearchScope.entries) {
            val index = SettingSearchIndex.searchable(context, scope)
            queries.forEach { (query, key) ->
                val results = SettingSearchIndex.search(context, index, query).filter { it.key == key }
                assertEquals("$scope: $query", 1, results.size)
                assertTrue(results.single().title.isNotBlank())
            }
        }
    }

    @Test fun dictionaryPredictionLabelsDistinguishTheirSourceAndKeepPreviousSearchTerms() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        AppPreference.init(base)
        val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.JAPANESE) })
        val oldTitles = mapOf(
            "learn_prediction_preference" to "予測変換",
            "user_dictionary_prefix_match_number" to "予測変換",
            "learn_dictionary_prediction_candidate_limit_preference" to "予測候補数",
            "user_dictionary_prediction_candidate_limit_preference" to "予測候補数",
        )
        for (scope in SettingSearchScope.entries) {
            val index = SettingSearchIndex.searchable(context, scope)
            assertEquals(oldTitles.size, index.filter { it.key in oldTitles }.map { it.title }.distinct().size)
            oldTitles.forEach { (key, previousTitle) ->
                assertTrue(SettingSearchIndex.search(context, index, previousTitle).any { it.key == key })
            }
        }
    }
}
