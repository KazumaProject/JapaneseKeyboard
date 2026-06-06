package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.annotation.IdRes
import androidx.annotation.XmlRes
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import org.xmlpull.v1.XmlPullParser

object SettingSearchIndex {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"

    private data class PreferenceXmlSource(
        @XmlRes val xmlRes: Int,
        @IdRes val destinationId: Int,
        val category: SettingCategory,
    )

    private val highlightableDestinations = setOf(
        R.id.keyboardDisplayPreferenceFragment,
        R.id.inputMethodPreferenceFragment,
        R.id.candidateConversionPreferenceFragment,
        R.id.aiConversionPreferenceFragment,
        R.id.operationFeedbackPreferenceFragment,
        R.id.clipboardShortcutPreferenceFragment,
        R.id.generalInfoPreferenceFragment,
        R.id.advancedPreferenceFragment,
    )

    fun searchable(context: Context): List<SettingDestination> {
        val topLevel = SettingDestinations.categories(context) +
            SettingDestinations.frequentCandidates(context)
        val xmlItems = sources().flatMap { source ->
            readPreferenceXml(context, source)
        }
        return (topLevel + xmlItems).distinctBy { destination ->
            val targetId = when (val target = destination.destination) {
                is SettingDestinationType.NavDestination -> target.destinationId
            }
            "${destination.key}:${destination.category}:$targetId"
        }
    }

    private fun sources(): List<PreferenceXmlSource> = buildList {
        add(PreferenceXmlSource(R.xml.pref_keyboard_display, R.id.keyboardDisplayPreferenceFragment, SettingCategory.KEYBOARD_DISPLAY))
        add(PreferenceXmlSource(R.xml.pref_input_method, R.id.inputMethodPreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_candidate_conversion, R.id.candidateConversionPreferenceFragment, SettingCategory.CANDIDATE_CONVERSION))
        add(PreferenceXmlSource(R.xml.pref_dictionary, R.id.dictionaryPreferenceFragment, SettingCategory.DICTIONARY))
        add(PreferenceXmlSource(R.xml.pref_ai_conversion, R.id.aiConversionPreferenceFragment, SettingCategory.AI_CONVERSION))
        if (AppVariantConfig.hasZenz) {
            add(PreferenceXmlSource(R.xml.pref_zenz, R.id.zenzPreferenceFragment, SettingCategory.AI_CONVERSION))
        }
        if (AppVariantConfig.hasGemma) {
            add(PreferenceXmlSource(R.xml.pref_gemma, R.id.gemmaPreferenceFragment, SettingCategory.AI_CONVERSION))
        }
        add(PreferenceXmlSource(R.xml.pref_clipboard_shortcut, R.id.clipboardShortcutPreferenceFragment, SettingCategory.CLIPBOARD_SHORTCUT))
        add(PreferenceXmlSource(R.xml.pref_operation_feedback, R.id.operationFeedbackPreferenceFragment, SettingCategory.OPERATION_FEEDBACK))
        add(PreferenceXmlSource(R.xml.pref_general_info, R.id.generalInfoPreferenceFragment, SettingCategory.APP_INFO))
        add(PreferenceXmlSource(R.xml.pref_advanced, R.id.advancedPreferenceFragment, SettingCategory.ADVANCED))
        add(PreferenceXmlSource(R.xml.pref_kana, R.id.kanaPreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_qwerty, R.id.qwertyPreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_sumire, R.id.sumirePreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_custom, R.id.customKeyboardPreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_tablet, R.id.tabletPreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_hardware_keyboard, R.id.hardwareKeyboardPreferenceFragment, SettingCategory.INPUT_METHOD))
    }

    private fun readPreferenceXml(
        context: Context,
        source: PreferenceXmlSource,
    ): List<SettingDestination> {
        val parser = context.resources.getXml(source.xmlRes)
        return parser.use {
            buildList {
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        val key = parser.getAttributeValue(ANDROID_NS, "key")
                        if (!key.isNullOrBlank() && isVisibleInCurrentVariant(key)) {
                            val title = readTextAttribute(context, parser, "title")
                                .ifBlank { key }
                            val summary = readSummary(context, parser)
                            val destinationId =
                                SettingDestinations.routeDestinationId(key) ?: source.destinationId
                            val highlightKey =
                                key.takeIf {
                                    destinationId == source.destinationId &&
                                        source.destinationId in highlightableDestinations
                                }
                            add(
                                SettingDestinations.destination(
                                    key = key,
                                    title = title,
                                    summary = summary,
                                    category = source.category,
                                    keywords = buildKeywords(key, title, summary),
                                    destinationId = destinationId,
                                    iconRes = readIcon(parser)
                                        ?: SettingDestinations.defaultIconForCategory(source.category),
                                    highlightPreferenceKey = highlightKey,
                                )
                            )
                        }
                    }
                    parser.next()
                }
            }
        }
    }

    private fun isVisibleInCurrentVariant(key: String): Boolean =
        when (key) {
            "setting_route_zenz_preferences" -> AppVariantConfig.hasZenz
            "setting_route_gemma_preferences" -> AppVariantConfig.hasGemma
            else -> true
        }

    private fun readTextAttribute(
        context: Context,
        parser: XmlResourceParser,
        name: String,
    ): String {
        return readTextAttribute(context, parser, ANDROID_NS, name)
            .ifBlank { readTextAttribute(context, parser, APP_NS, name) }
    }

    private fun readTextAttribute(
        context: Context,
        parser: XmlResourceParser,
        namespace: String,
        name: String,
    ): String {
        val resourceId = parser.getAttributeResourceValue(namespace, name, 0)
        if (resourceId != 0) {
            return runCatching { context.getString(resourceId) }.getOrDefault("")
        }
        return parser.getAttributeValue(namespace, name).orEmpty()
    }

    private fun readSummary(context: Context, parser: XmlResourceParser): String {
        val summary = readTextAttribute(context, parser, "summary")
        if (summary.isNotBlank()) return summary
        val onOffSummary = listOf(
            readTextAttribute(context, parser, "summaryOn"),
            readTextAttribute(context, parser, "summaryOff"),
        ).filter { it.isNotBlank() }
        return onOffSummary.joinToString(" / ")
    }

    private fun readIcon(parser: XmlResourceParser): Int? {
        val resourceId = parser.getAttributeResourceValue(ANDROID_NS, "icon", 0)
        return resourceId.takeIf { it != 0 }
    }

    private fun buildKeywords(
        key: String,
        title: String,
        summary: String,
    ): List<String> =
        buildList {
            add(key)
            add(title)
            add(summary)
            addAll(key.split('_', '-', '.'))
        }
}
