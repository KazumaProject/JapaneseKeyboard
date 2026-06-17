package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.annotation.IdRes
import androidx.annotation.XmlRes
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import org.xmlpull.v1.XmlPullParser
import java.text.Normalizer
import java.util.Locale

object SettingSearchIndex {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"
    private const val SINGLE_CHARACTER_RESULT_LIMIT = 20
    private const val MULTI_CHARACTER_RESULT_LIMIT = 50
    private val extraKeywordsByKey = mapOf(
        "shortcut_toolbar_size_setting_fragment_preference" to listOf(
            "shortcut",
            "toolbar",
            "height",
            "icon",
            "size",
            "ショートカット",
            "ツールバー",
            "高さ",
            "アイコン",
            "サイズ",
        )
    )

    private data class PreferenceXmlSource(
        @XmlRes val xmlRes: Int,
        @IdRes val destinationId: Int,
        val category: SettingCategory,
    )

    private data class LegacyPreferenceXmlSource(
        val tabKey: String,
        val tabTitle: String,
        @XmlRes val xmlRes: Int,
        @IdRes val destinationId: Int,
        val category: SettingCategory,
    )

    private data class ParsedSearchQuery(
        val normalized: String,
        val tokens: List<String>,
    )

    private data class ScoredDestination(
        val destination: SettingDestination,
        val score: Int,
    )

    private val highlightableDestinations = setOf(
        R.id.keyboardDisplayPreferenceFragment,
        R.id.inputMethodPreferenceFragment,
        R.id.candidateConversionPreferenceFragment,
        R.id.aiConversionPreferenceFragment,
        R.id.operationFeedbackPreferenceFragment,
        R.id.clipboardShortcutPreferenceFragment,
        R.id.dictionaryPreferenceFragment,
        R.id.generalInfoPreferenceFragment,
        R.id.advancedPreferenceFragment,
        R.id.zenzPreferenceFragment,
        R.id.gemmaPreferenceFragment,
        R.id.legacyCommonPreferenceFragment,
    )

    fun searchable(context: Context): List<SettingDestination> {
        val topLevel = SettingDestinations.categories(context) +
            SettingDestinations.management(context) +
            SettingDestinations.frequentCandidates(context)
        val xmlItems = sources().flatMap { source ->
            readPreferenceXml(context, source)
        }
        return (topLevel + xmlItems).distinctBy { destination ->
            val targetId = SettingDestinations.destinationId(destination.destination)
            "${destination.key}:${destination.category}:$targetId"
        }
    }

    fun searchable(
        context: Context,
        scope: SettingSearchScope,
    ): List<SettingDestination> =
        when (scope) {
            SettingSearchScope.NEW_HOME -> searchable(context)
            SettingSearchScope.LEGACY_TABS -> legacySearchable(context)
        }

    fun legacySearchable(context: Context): List<SettingDestination> {
        val themeItem = SettingTabRegistry.createTabs()
            .firstOrNull { it.key == SettingTabRegistry.TAB_THEME }
            ?.let { tab ->
                val title = tab.title(context)
                val location = context.getString(R.string.setting_search_legacy_location, title)
                SettingDestinations.destination(
                    key = "legacy_keyboard_theme",
                    title = title,
                    summary = "",
                    category = SettingCategory.KEYBOARD_DISPLAY,
                    keywords = listOf("theme", "color", "keyboard", "legacy"),
                    destinationId = tab.destinationId,
                    iconRes = SettingDestinations.defaultIconForCategory(SettingCategory.KEYBOARD_DISPLAY),
                    destinationType = SettingDestinationType.NavDestination(tab.destinationId),
                    legacyTarget = LegacySettingTarget(
                        tabKey = tab.key,
                        xmlRes = null,
                        destinationId = tab.destinationId,
                        preferenceKey = "legacy_keyboard_theme",
                        filterResultMode = false,
                    ),
                    searchScope = SettingSearchScope.LEGACY_TABS,
                    location = location,
                )
            }
        val xmlItems = legacySources(context).flatMap { source ->
            readLegacyPreferenceXml(context, source)
        }
        return (listOfNotNull(themeItem) + xmlItems).distinctBy { destination ->
            val legacyTarget = destination.legacyTarget
            "${legacyTarget?.tabKey}:${destination.key}:${legacyTarget?.destinationId}"
        }
    }

    fun search(
        context: Context,
        destinations: List<SettingDestination>,
        query: String,
    ): List<SettingDestination> {
        val parsedQuery = parseQuery(query)
        if (parsedQuery.tokens.isEmpty()) return emptyList()
        val limit = if (parsedQuery.normalized.filterNot { it.isWhitespace() }.length <= 1) {
            SINGLE_CHARACTER_RESULT_LIMIT
        } else {
            MULTI_CHARACTER_RESULT_LIMIT
        }
        return rankedMatches(context, destinations, parsedQuery).take(limit)
    }

    fun filter(
        context: Context,
        destinations: List<SettingDestination>,
        query: String,
    ): List<SettingDestination> {
        val parsedQuery = parseQuery(query)
        if (parsedQuery.tokens.isEmpty()) return stableSort(context, destinations)
        return rankedMatches(context, destinations, parsedQuery)
    }

    fun normalizeForSearch(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[_\\-.]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun destinationsForKeys(
        context: Context,
        keys: List<String>,
    ): List<SettingDestination> {
        val keySet = keys.toSet()
        val order = keys.withIndex().associate { it.value to it.index }
        return sources()
            .flatMap { source -> readPreferenceXml(context, source) }
            .filter { it.key in keySet }
            .distinctBy { it.key }
            .sortedBy { order[it.key] ?: Int.MAX_VALUE }
    }

    fun legacyDestinationsForKeys(
        context: Context,
        keys: List<String>,
    ): List<SettingDestination> {
        val keySet = keys.toSet()
        val order = keys.withIndex().associate { it.value to it.index }
        return legacySearchable(context)
            .filter { it.key in keySet }
            .distinctBy { it.key }
            .sortedBy { order[it.key] ?: Int.MAX_VALUE }
    }

    private fun parseQuery(query: String): ParsedSearchQuery {
        val normalized = normalizeForSearch(query)
        return ParsedSearchQuery(
            normalized = normalized,
            tokens = normalized.split(' ').filter { it.isNotBlank() },
        )
    }

    private fun rankedMatches(
        context: Context,
        destinations: List<SettingDestination>,
        query: ParsedSearchQuery,
    ): List<SettingDestination> =
        destinations
            .mapNotNull { destination ->
                score(context, destination, query)?.let { score ->
                    ScoredDestination(destination, score)
                }
            }
            .sortedWith(
                compareByDescending<ScoredDestination> { it.score }
                    .thenBy { it.destination.category.ordinal }
                    .thenBy { it.destination.title }
                    .thenBy { it.destination.key }
            )
            .map { it.destination }

    private fun stableSort(
        context: Context,
        destinations: List<SettingDestination>,
    ): List<SettingDestination> =
        destinations.sortedWith(
            compareBy<SettingDestination> { it.category.ordinal }
                .thenBy { SettingDestinations.categoryTitle(context, it.category) }
                .thenBy { it.title }
                .thenBy { it.key }
        )

    private fun score(
        context: Context,
        destination: SettingDestination,
        query: ParsedSearchQuery,
    ): Int? {
        val normalizedTitle = normalizeForSearch(destination.title)
        val normalizedKey = normalizeForSearch(destination.key)
        val normalizedSummary = normalizeForSearch(destination.summary)
        val normalizedCategory =
            normalizeForSearch(SettingDestinations.categoryTitle(context, destination.category))
        val coreFields = setOf(normalizedTitle, normalizedKey, normalizedSummary)
        val normalizedKeywords = destination.keywords
            .map(::normalizeForSearch)
            .filter { it.isNotBlank() && it !in coreFields }

        var score = 0
        query.tokens.forEach { token ->
            score += scoreToken(
                token = token,
                title = normalizedTitle,
                key = normalizedKey,
                keywords = normalizedKeywords,
                summary = normalizedSummary,
                category = normalizedCategory,
            ) ?: return null
        }

        if (normalizedTitle == query.normalized) {
            score += 100_000
        } else if (normalizedTitle.startsWith(query.normalized)) {
            score += 8_000
        }

        return score
    }

    private fun scoreToken(
        token: String,
        title: String,
        key: String,
        keywords: List<String>,
        summary: String,
        category: String,
    ): Int? =
        when {
            title == token -> 10_000
            title.startsWith(token) -> 8_000
            title.contains(token) -> 6_000
            key.contains(token) -> 4_500
            keywords.any { it.contains(token) } -> 3_500
            summary.contains(token) -> 2_500
            category.contains(token) -> 1_500
            else -> null
        }

    private fun sources(): List<PreferenceXmlSource> = buildList {
        add(PreferenceXmlSource(R.xml.pref_common_legacy, R.id.legacyCommonPreferenceFragment, SettingCategory.ADVANCED))
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

    private fun legacySources(context: Context): List<LegacyPreferenceXmlSource> =
        SettingTabRegistry.createTabs().mapNotNull { tab ->
            val xmlRes = tab.xmlRes ?: return@mapNotNull null
            LegacyPreferenceXmlSource(
                tabKey = tab.key,
                tabTitle = tab.title(context),
                xmlRes = xmlRes,
                destinationId = tab.destinationId,
                category = legacyCategoryForTab(tab.key),
            )
        }

    private fun legacyCategoryForTab(tabKey: String): SettingCategory =
        when (tabKey) {
            SettingTabRegistry.TAB_DICTIONARY -> SettingCategory.DICTIONARY
            SettingTabRegistry.TAB_ZENZ,
            SettingTabRegistry.TAB_GEMMA -> SettingCategory.AI_CONVERSION
            SettingTabRegistry.TAB_COMMON -> SettingCategory.ADVANCED
            else -> SettingCategory.INPUT_METHOD
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
                        if (
                            !key.isNullOrBlank() &&
                            isVisibleInCurrentVariant(key) &&
                            !parser.isPreferenceCategoryOrScreen()
                        ) {
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
                                    destinationType = readDestinationType(
                                        context = context,
                                        parser = parser,
                                        key = key,
                                        destinationId = destinationId,
                                        highlightPreferenceKey = highlightKey,
                                    ),
                                )
                            )
                        }
                    }
                    parser.next()
                }
            }
        }
    }

    private fun readLegacyPreferenceXml(
        context: Context,
        source: LegacyPreferenceXmlSource,
    ): List<SettingDestination> {
        val parser = context.resources.getXml(source.xmlRes)
        val location = context.getString(R.string.setting_search_legacy_location, source.tabTitle)
        return parser.use {
            buildList {
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        val key = parser.getAttributeValue(ANDROID_NS, "key")
                        if (
                            !key.isNullOrBlank() &&
                            isVisibleInCurrentVariant(key) &&
                            !key.startsWith("setting_route_") &&
                            !parser.isPreferenceCategoryOrScreen()
                        ) {
                            val title = readTextAttribute(context, parser, "title")
                                .ifBlank { key }
                            val summary = readSummary(context, parser)
                            val dependencyKey = readDependency(parser)
                            val relatedKeys = legacyRelatedKeys(key, dependencyKey)
                            val legacyTarget = LegacySettingTarget(
                                tabKey = source.tabKey,
                                xmlRes = source.xmlRes,
                                destinationId = source.destinationId,
                                preferenceKey = key,
                                relatedPreferenceKeys = relatedKeys,
                            )
                            add(
                                SettingDestinations.destination(
                                    key = key,
                                    title = title,
                                    summary = summary,
                                    category = source.category,
                                    keywords = buildKeywords(key, title, summary),
                                    destinationId = source.destinationId,
                                    iconRes = readIcon(parser)
                                        ?: SettingDestinations.defaultIconForCategory(source.category),
                                    highlightPreferenceKey = key,
                                    destinationType = SettingDestinationType.NavDestination(
                                        destinationId = source.destinationId,
                                        highlightPreferenceKey = key,
                                    ),
                                    legacyTarget = legacyTarget,
                                    searchScope = SettingSearchScope.LEGACY_TABS,
                                    location = location,
                                )
                            )
                        }
                    }
                    parser.next()
                }
            }
        }
    }

    private fun legacyRelatedKeys(key: String, dependencyKey: String?): List<String> =
        buildList {
            if (key == "shortcut_toolbar_integrated_in_suggestion_preference") {
                add("shortcut_toolbar_visibility_preference")
            }
            if (key.startsWith("keyboard_touch_effect_cinematic_wave_")) {
                add("keyboard_touch_effect_type_preference")
                add("keyboard_touch_effect_cinematic_wave_color_mode_preference")
            }
            if (!dependencyKey.isNullOrBlank()) {
                add(dependencyKey)
            }
        }.distinct()

    private fun isVisibleInCurrentVariant(key: String): Boolean =
        when (key) {
            "setting_route_zenz_preferences" -> AppVariantConfig.hasZenz
            "setting_route_gemma_preferences" -> AppVariantConfig.hasGemma
            else -> true
        }

    private fun readDestinationType(
        context: Context,
        parser: XmlResourceParser,
        key: String,
        @IdRes destinationId: Int,
        highlightPreferenceKey: String?,
    ): SettingDestinationType {
        val fallback = fallbackDestinationType(key, destinationId, highlightPreferenceKey)
        if (SettingDestinations.isManagementDestinationKey(key)) return fallback

        val simpleTagName = parser.name.substringAfterLast('.')
        return when (simpleTagName) {
            "SwitchPreferenceCompat",
            "SwitchPreference" -> SettingDestinationType.SwitchPreference(
                preferenceKey = key,
                defaultValue = readBooleanAttribute(parser, "defaultValue", defaultValue = false),
                destinationId = destinationId,
                highlightPreferenceKey = highlightPreferenceKey ?: key,
            )

            "ListPreference",
            "DropDownPreference" -> readListPreferenceType(
                context = context,
                parser = parser,
                key = key,
                destinationId = destinationId,
                highlightPreferenceKey = highlightPreferenceKey,
            ) ?: fallback

            "SeekBarPreference" -> readSeekBarPreferenceType(
                parser = parser,
                key = key,
                destinationId = destinationId,
                highlightPreferenceKey = highlightPreferenceKey,
            ) ?: fallback

            "EditTextPreference" -> SettingDestinationType.EditTextPreference(
                preferenceKey = key,
                defaultValue = readTextAttribute(context, parser, "defaultValue"),
                obscureValue = key.contains("password", ignoreCase = true) ||
                    key.contains("secret", ignoreCase = true) ||
                    key.contains("token", ignoreCase = true) ||
                    key.contains("api_key", ignoreCase = true),
                destinationId = destinationId,
                highlightPreferenceKey = highlightPreferenceKey ?: key,
            )

            "Preference" ->
                SettingDestinations.plainPreferenceInlineEditException(
                    key = key,
                    destinationId = destinationId,
                    highlightPreferenceKey = highlightPreferenceKey,
                ) ?: fallback

            else -> fallback
        }
    }

    private fun fallbackDestinationType(
        key: String,
        @IdRes destinationId: Int,
        highlightPreferenceKey: String?,
    ): SettingDestinationType =
        if (SettingDestinations.isManagementDestinationKey(key)) {
            SettingDestinationType.ManagementDestination(
                destinationId = destinationId,
                highlightPreferenceKey = highlightPreferenceKey ?: key,
            )
        } else {
            SettingDestinationType.NavDestination(
                destinationId = destinationId,
                highlightPreferenceKey = highlightPreferenceKey,
            )
        }

    private fun readListPreferenceType(
        context: Context,
        parser: XmlResourceParser,
        key: String,
        @IdRes destinationId: Int,
        highlightPreferenceKey: String?,
    ): SettingDestinationType.ListPreference? {
        val entriesResId = readResourceAttribute(parser, "entries") ?: return null
        val entryValuesResId = readResourceAttribute(parser, "entryValues") ?: return null
        val arraysMatch = runCatching {
            val entries = context.resources.getStringArray(entriesResId)
            val entryValues = context.resources.getStringArray(entryValuesResId)
            entries.isNotEmpty() && entries.size == entryValues.size
        }.getOrDefault(false)
        if (!arraysMatch) return null

        return SettingDestinationType.ListPreference(
            preferenceKey = key,
            entriesResId = entriesResId,
            entryValuesResId = entryValuesResId,
            defaultValue = readTextAttribute(context, parser, "defaultValue"),
            destinationId = destinationId,
            highlightPreferenceKey = highlightPreferenceKey ?: key,
        )
    }

    private fun readSeekBarPreferenceType(
        parser: XmlResourceParser,
        key: String,
        @IdRes destinationId: Int,
        highlightPreferenceKey: String?,
    ): SettingDestinationType.SeekBarPreference? {
        val min = readIntAttribute(parser, "min") ?: 0
        val max = readIntAttribute(parser, "max") ?: return null
        val increment = readIntAttribute(parser, "seekBarIncrement") ?: 1
        val defaultValue = readIntAttribute(parser, "defaultValue") ?: min
        if (max < min || increment <= 0) return null

        return SettingDestinationType.SeekBarPreference(
            preferenceKey = key,
            min = min,
            max = max,
            increment = increment,
            defaultValue = defaultValue.coerceIn(min, max),
            destinationId = destinationId,
            highlightPreferenceKey = highlightPreferenceKey ?: key,
        )
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

    private fun readBooleanAttribute(
        parser: XmlResourceParser,
        name: String,
        defaultValue: Boolean,
    ): Boolean {
        val raw = parser.getAttributeValue(ANDROID_NS, name)
            ?: parser.getAttributeValue(APP_NS, name)
            ?: return defaultValue
        return when (raw.lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> defaultValue
        }
    }

    private fun readIntAttribute(
        parser: XmlResourceParser,
        name: String,
    ): Int? =
        parser.getAttributeValue(ANDROID_NS, name)?.toIntOrNull()
            ?: parser.getAttributeValue(APP_NS, name)?.toIntOrNull()

    private fun readResourceAttribute(
        parser: XmlResourceParser,
        name: String,
    ): Int? {
        val androidResourceId = parser.getAttributeResourceValue(ANDROID_NS, name, 0)
        if (androidResourceId != 0) return androidResourceId
        val appResourceId = parser.getAttributeResourceValue(APP_NS, name, 0)
        return appResourceId.takeIf { it != 0 }
    }

    private fun readSummary(context: Context, parser: XmlResourceParser): String {
        val summary = readTextAttribute(context, parser, "summary")
        if (summary == "%s") return ""
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

    private fun readDependency(parser: XmlResourceParser): String? =
        parser.getAttributeValue(ANDROID_NS, "dependency")
            ?: parser.getAttributeValue(APP_NS, "dependency")

    private fun XmlResourceParser.isPreferenceCategoryOrScreen(): Boolean =
        when (name.substringAfterLast('.')) {
            "PreferenceCategory",
            "PreferenceScreen" -> true
            else -> false
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
            addAll(extraKeywordsByKey[key].orEmpty())
        }
}
