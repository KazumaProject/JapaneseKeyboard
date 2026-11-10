package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.res.Configuration
import android.content.res.XmlResourceParser
import androidx.annotation.IdRes
import androidx.annotation.XmlRes
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import org.xmlpull.v1.XmlPullParser
import java.text.Normalizer
import java.util.Locale

object SettingSearchIndex {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"
    private const val SINGLE_CHARACTER_RESULT_LIMIT = 20
    private const val MULTI_CHARACTER_RESULT_LIMIT = 50

    @Volatile
    private var destinationCache: DestinationCache? = null

    private data class DestinationCache(
        val configuration: Configuration,
        val destinations: List<SettingDestination>,
    )
    private val previousJapaneseTitles = mapOf(
        "candidate_tab_visibility_preference" to "変換候補タブ",
        "keyboard_key_letter_size_fragment_preference" to "キー・変換候補の文字サイズ",
        "delete_key_flick_down_preference" to "削除キーの下フリック",
        "delete_key_flick_left_preference" to "削除キーの左フリック",
        "delete_key_flick_up_preference" to "削除キーの上フリック",
        "emoji_candidate_enable_preference" to "絵文字の変換候補",
        "emoticon_candidate_enable_preference" to "顔文字の変換候補",
        "enable_typo_correction_japanese_flick_keyboard_preference" to "フリック（日本語）",
        "enable_typo_correction_qwerty_english_keyboard_preference" to "QWERTY（英語）",
        "english_prediction_enable_preference" to "英語の予測変換",
        "english_reading_dictionary_enable_preference" to "英語読み辞書",
        "external_mozc_prediction_enable_preference" to "外部Mozc辞書",
        "flick_input_only_preference" to "フリック・トグル入力",
        "floating_cursor_enabled" to "カーソル・トラックパッドフロート",
        "henkan_delete_key_action_preference" to "候補ハイライト時の削除キー動作",
        "hide_candidate_password_preference" to "パスワード入力時の変換候補",
        "incremental_conversion_session_preference" to "セッション型変換エンジン",
        "japanese_prediction_enable_preference" to "日本語の予測変換",
        "keyboard_floating_preference" to "フローティングキーボード",
        "landscape_force_qwerty_romaji_preference" to "横画面 QWERTY の入力モード",
        "live_conversion_preference" to "ライブ変換 β",
        "morse_auto_boundary" to "文字の自動区切り",
        "morse_beginner_guide" to "初心者ガイドの開閉キー",
        "morse_sound" to "モールス音",
        "omission_search_preference" to "修飾キー省略入力",
        "proverb_prediction_enable_preference" to "ことわざ辞書",
        "qwerty_english_space_flick_preference" to "スペースキーのフリック",
        "qwerty_glide_input_preference" to "英語 QWERTY のグライド入力 β",
        "qwerty_romaji_hankaku_number_preference" to "ローマ字入力時の半角数字",
        "qwerty_romaji_hankaku_symbol_preference" to "ローマ字入力時の半角記号",
        "qwerty_romaji_space_flick_preference" to "スペースキーのフリック",
        "qwerty_romaji_zenkaku_space_preference" to "ローマ字キーボードの全角スペース",
        "qwerty_show_cursor_buttons_preference" to "カーソルボタン",
        "qwerty_show_emoji_button_preference" to "絵文字ボタン",
        "qwerty_show_kutouten_buttons_preference" to "句読点ボタン",
        "qwerty_show_number_keys_buttons_preference" to "数字キー",
        "reading_correction_prediction_enable_preference" to "読み補正辞書",
        "shortcut_toolbar_integrated_in_suggestion_preference" to "ショートカットを候補欄に統合",
        "shortcut_toolbar_visibility_preference" to "ショートカットツールバー",
        "show_learned_candidates_in_incognito_preference" to "シークレットモード中の学習済み候補",
        "sumire_keymap_guide_english" to "英語モードのキーガイド",
        "sumire_keymap_guide_japanese" to "日本語モードのキーガイド",
        "sumire_keymap_guide_number" to "数字モードのキーガイド",
        "switch_qwerty_keyboard_password_preference" to "パスワード入力時",
        "symbol_candidate_enable_preference" to "記号の変換候補",
        "symbol_emoji_prediction_enable_preference" to "記号・絵文字・顔文字",
        "system_dictionary_prediction_enable_preference" to "システム辞書の予測変換",
        "system_user_dictionary_prediction_enable_preference" to "システムユーザー辞書",
        "tenkey_keymap_guide" to "日本語モードのキーガイド",
        "tenkey_keymap_guide_english" to "英語モードのキーガイド",
        "tenkey_keymap_guide_number" to "数字モードのキーガイド",
        "tenkey_space_flick_preference" to "スペースキーのフリック",
        "learn_prediction_preference" to "予測変換",
        "user_dictionary_prefix_match_number" to "予測変換",
        "learn_dictionary_prediction_candidate_limit_preference" to "予測候補数",
        "user_dictionary_prediction_candidate_limit_preference" to "予測候補数",
        "undo_enable_preference" to "元に戻す機能",
        "voice_input_space_replacement_enabled" to "半角スペースの自動置換",
        "zero_query_suggestion_preference" to "確定後の後続語候補",
    )

    private val extraKeywordsByKey = mapOf(
        "floating_keyboard_hide_button_position" to listOf(
            "floating", "hide", "arrow", "position", "フローティング", "下向き矢印", "やじるし", "隠す", "位置",
        ),
        "floating_keyboard_show_edit_button" to listOf(
            "floating", "pencil", "resize", "フローティング", "鉛筆", "えんぴつ", "サイズ調整",
        ),
        "keyboard_selection_preference" to listOf(
            "choose",
            "select",
            "apply",
            "layout",
            "keyboard layout",
            "キーボードを選ぶ",
            "キーボードを適用",
            "レイアウト",
            "レイアウトを選ぶ",
            "選択",
            "適用",
        ),
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
        R.id.candidateIndicatorPreferenceFragment,
        R.id.candidateDisplayPreferenceFragment,
        R.id.morsePreferenceFragment,
        R.id.splitKeyboardPreferenceFragment,
        R.id.keyboardDisplayPreferenceFragment,
        R.id.inputMethodPreferenceFragment,
        R.id.voiceInputPreferenceFragment,
        R.id.candidateConversionPreferenceFragment,
        R.id.conversionEnginePreferenceFragment,
        R.id.utilityCandidatePreferenceFragment,
        R.id.aiConversionPreferenceFragment,
        R.id.operationFeedbackPreferenceFragment,
        R.id.clipboardShortcutPreferenceFragment,
        R.id.dictionaryPreferenceFragment,
        R.id.generalInfoPreferenceFragment,
        R.id.advancedPreferenceFragment,
        R.id.zenzPreferenceFragment,
        R.id.gemmaPreferenceFragment,
        R.id.legacyCommonPreferenceFragment,
        R.id.keyboardThemeFragment,
    )

    fun searchable(context: Context): List<SettingDestination> {
        val topLevel = SettingDestinations.categories(context) +
            SettingDestinations.management(context) +
            SettingDestinations.frequentCandidates(context)
        val xmlItems = preferenceXmlDestinations(context)
        val officialIds = setOf(R.id.legacyCommonPreferenceFragment, R.id.keyboardThemeFragment, R.id.conversionEnginePreferenceFragment, R.id.candidateDisplayPreferenceFragment)
        val official = xmlItems.filter { SettingDestinations.destinationId(it.destination) in officialIds }
        return (official + topLevel + xmlItems).distinctBy { it.key }
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
        val tabs = SettingTabRegistry.createTabs()
        val themeItem = tabs
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
        val textMacroItem = tabs
            .firstOrNull { it.key == SettingTabRegistry.TAB_TEXT_MACRO }
            ?.let { tab ->
                val title = tab.title(context)
                val location = context.getString(R.string.setting_search_legacy_location, title)
                SettingDestinations.destination(
                    key = "legacy_text_macro",
                    title = title,
                    summary = context.getString(R.string.setting_category_text_macro_summary),
                    category = SettingCategory.TEXT_MACRO,
                    keywords = listOf("macro", "dynamic", "template", "snippet", "定型文"),
                    destinationId = tab.destinationId,
                    iconRes = SettingDestinations.defaultIconForCategory(SettingCategory.TEXT_MACRO),
                    destinationType = SettingDestinationType.NavDestination(tab.destinationId),
                    legacyTarget = LegacySettingTarget(
                        tabKey = tab.key,
                        xmlRes = null,
                        destinationId = tab.destinationId,
                        preferenceKey = "legacy_text_macro",
                        filterResultMode = false,
                    ),
                    searchScope = SettingSearchScope.LEGACY_TABS,
                    location = location,
                )
            }
        val xmlItems = legacySources(context).flatMap { source ->
            readLegacyPreferenceXml(context, source)
        }
        val candidateItems = readPreferenceXml(context, PreferenceXmlSource(
            R.xml.pref_candidate_display, R.id.candidateDisplayPreferenceFragment, SettingCategory.CANDIDATE_CONVERSION
        )).map { it.copy(searchScope = SettingSearchScope.LEGACY_TABS) }
        val indicatorItems = readPreferenceXml(context, PreferenceXmlSource(
            R.xml.pref_candidate_indicator_styles, R.id.candidateIndicatorPreferenceFragment, SettingCategory.CANDIDATE_CONVERSION
        )).map { it.copy(searchScope = SettingSearchScope.LEGACY_TABS) }
        return (candidateItems + listOfNotNull(themeItem, textMacroItem) + xmlItems + indicatorItems).distinctBy { it.key }
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
        return preferenceXmlDestinations(context)
            .filter { it.key in keySet }
            .distinctBy { it.key }
            .sortedBy { order[it.key] ?: Int.MAX_VALUE }
    }

    private fun preferenceXmlDestinations(context: Context): List<SettingDestination> {
        val configuration = Configuration(context.resources.configuration)
        destinationCache
            ?.takeIf { it.configuration == configuration }
            ?.let { return it.destinations }

        val destinations = sources().flatMap { source -> readPreferenceXml(context, source) }
        destinationCache = DestinationCache(configuration, destinations)
        return destinations
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
        add(PreferenceXmlSource(R.xml.pref_theme, R.id.keyboardThemeFragment, SettingCategory.KEYBOARD_DISPLAY))
        add(PreferenceXmlSource(R.xml.pref_conversion_engine, R.id.conversionEnginePreferenceFragment, SettingCategory.CONVERSION_ENGINE))
        add(PreferenceXmlSource(R.xml.pref_common, R.id.commonPreferenceFragment, SettingCategory.ADVANCED))
        add(PreferenceXmlSource(R.xml.pref_keyboard_display, R.id.keyboardDisplayPreferenceFragment, SettingCategory.KEYBOARD_DISPLAY))
        add(PreferenceXmlSource(R.xml.pref_split_keyboard, R.id.splitKeyboardPreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_input_method, R.id.inputMethodPreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_voice_input, R.id.voiceInputPreferenceFragment, SettingCategory.VOICE_INPUT))
        add(PreferenceXmlSource(R.xml.pref_candidate_display, R.id.candidateDisplayPreferenceFragment, SettingCategory.CANDIDATE_CONVERSION))
        add(PreferenceXmlSource(R.xml.pref_candidate_indicator_styles, R.id.candidateIndicatorPreferenceFragment, SettingCategory.CANDIDATE_CONVERSION))
        add(PreferenceXmlSource(R.xml.pref_candidate_conversion, R.id.candidateConversionPreferenceFragment, SettingCategory.CANDIDATE_CONVERSION))
        add(PreferenceXmlSource(R.xml.pref_utility_candidate, R.id.utilityCandidatePreferenceFragment, SettingCategory.CONVERSION_ENGINE))
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
        add(PreferenceXmlSource(R.xml.pref_morse, R.id.morsePreferenceFragment, SettingCategory.MORSE))
        add(PreferenceXmlSource(R.xml.pref_gojuon, R.id.gojuonPreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_qwerty, R.id.qwertyPreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_qwerty_english, R.id.qwertyEnglishPreferenceFragment, SettingCategory.INPUT_METHOD))
        add(PreferenceXmlSource(R.xml.pref_qwerty_romaji, R.id.qwertyRomajiPreferenceFragment, SettingCategory.INPUT_METHOD))
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
            SettingTabRegistry.TAB_CONVERSION_ENGINE -> SettingCategory.CONVERSION_ENGINE
            SettingTabRegistry.TAB_DICTIONARY -> SettingCategory.DICTIONARY
            SettingTabRegistry.TAB_TEXT_MACRO -> SettingCategory.TEXT_MACRO
            SettingTabRegistry.TAB_MORSE -> SettingCategory.MORSE
            SettingTabRegistry.TAB_VOICE_INPUT -> SettingCategory.VOICE_INPUT
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
                val headings = ArrayDeque<String>()
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        if (parser.isPreferenceCategory()) headings.addLast(readTextAttribute(context, parser, "title"))
                        val key = parser.getAttributeValue(ANDROID_NS, "key")
                        if (
                            !key.isNullOrBlank() &&
                            isVisibleInCurrentVariant(key) &&
                            key != "candidate_display_preview" &&
                            !key.startsWith("setting_route_canonical_") &&
                            !key.startsWith("settings_theme_enable_") &&
                            !key.startsWith("settings_theme_unavailable_") &&
                            !key.startsWith("settings_engine_enable_") &&
                            !parser.isPreferenceCategoryOrScreen()
                        ) {
                            val title = readTextAttribute(context, parser, "title")
                                .ifBlank { key }
                            val summary = readSummary(context, parser)
                            val isOfficial = source.xmlRes in setOf(R.xml.pref_common_legacy, R.xml.pref_theme, R.xml.pref_conversion_engine, R.xml.pref_candidate_display)
                            val destinationId = if (isOfficial) source.destinationId else
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
                                    keywords = buildKeywords(key, title, summary) + headings,
                                    destinationId = destinationId,
                                    iconRes = readIcon(parser)
                                        ?: SettingDestinations.defaultIconForCategory(source.category),
                                    highlightPreferenceKey = highlightKey,
                                    destinationType = if (isOfficial) SettingDestinationType.NavDestination(destinationId, highlightKey) else readDestinationType(
                                        context = context,
                                        parser = parser,
                                        key = key,
                                        destinationId = destinationId,
                                        highlightPreferenceKey = highlightKey,
                                    ),
                                    location = headings.filter { it.isNotBlank() }.joinToString(" › ").ifBlank { null },
                                )
                            )
                        }
                    }
                    if (parser.eventType == XmlPullParser.END_TAG && parser.isPreferenceCategory() && headings.isNotEmpty()) headings.removeLast()
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
                val headings = ArrayDeque<String>()
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        if (parser.isPreferenceCategory()) headings.addLast(readTextAttribute(context, parser, "title"))
                        val key = parser.getAttributeValue(ANDROID_NS, "key")
                        if (
                            !key.isNullOrBlank() &&
                            isVisibleInCurrentVariant(key) &&
                            (!key.startsWith("setting_route_") || key == "setting_route_utility_candidates") &&
                            !key.startsWith("settings_theme_enable_") &&
                            !key.startsWith("settings_theme_unavailable_") &&
                            !key.startsWith("settings_engine_enable_") &&
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
                                    keywords = buildKeywords(key, title, summary) + headings,
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
                                    location = (listOf(location) + headings.filter { it.isNotBlank() }).joinToString(" › "),
                                )
                            )
                        }
                    }
                    if (parser.eventType == XmlPullParser.END_TAG && parser.isPreferenceCategory() && headings.isNotEmpty()) headings.removeLast()
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
            if (key.startsWith("live_conversion_candidate_yomi_")) add("settings_theme_enable_live_conversion_link")
            if (key == "composing_guide_show_live_reading" || key == "composing_guide_text_size_setting") add("settings_theme_enable_composing_guide_link")
            if (key in setOf("enable_prediction_search_learn_dictionary_preference", "learn_prediction_preference", "learn_dictionary_prediction_candidate_limit_preference")) add("settings_engine_enable_learning_dictionary_link")
            if (key in setOf("user_dictionary_prefix_match_number", "user_dictionary_prediction_candidate_limit_preference")) add("settings_engine_enable_user_dictionary_link")
            if (key.startsWith("keyboard_touch_effect_") && key != "keyboard_touch_effect_type_preference") {
                add("settings_theme_unavailable_effects")
                add("keyboard_touch_effect_type_preference")
            }
            if (key.startsWith("theme_custom_") || key.startsWith("key_popup_")) {
                add("theme_custom")
                add("settings_theme_unavailable_colors")
                add("settings_theme_unavailable_corners")
            }
            if (key.startsWith("theme_custom_border_")) add("theme_custom_border_enable")
            if (key.startsWith("key_popup_")) add("key_popup_use_custom_color_preference")
            if (key.startsWith("theme_custom_pre_edit_") || key.startsWith("theme_custom_post_edit_")) add("theme_custom_input_color_enable")
            if (key.startsWith("liquid_glass_")) {
                add("liquid_glass_preference")
                add("settings_theme_unavailable_effects")
            }
            if (key.startsWith("keyboard_touch_effect_cinematic_wave_")) {
                add("keyboard_touch_effect_type_preference")
                add("keyboard_touch_effect_cinematic_wave_color_mode_preference")
            }
            if (
                key == "keyboard_touch_effect_liquid_ink_density_preference" ||
                key == "keyboard_touch_effect_aurora_ink_density_preference"
            ) {
                add("keyboard_touch_effect_type_preference")
            }
            if (key.startsWith("voice_input_")) {
                add(AppPreference.VOICE_INPUT_ENGINE_KEY)
                add(AppPreference.VOICE_INPUT_MODEL_KEY)
                add(AppPreference.VOICE_INPUT_BACKEND_KEY)
                add(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_ENABLED_KEY)
                add(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_KEY)
                add(AppPreference.VOICE_INPUT_SPACE_CUSTOM_TEXT_KEY)
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
        // These switches use DictionaryOverrideStore and compatibility checks.
        // Search/frequent cards must navigate to their handler, not write default preferences.
        if (com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries.all
                .any { it.preferenceKey == key }) return fallback
        // Open the shared screen so replacement/custom-text dependencies also apply to search/favorites.
        if (destinationId == R.id.voiceInputPreferenceFragment) return fallback

        val simpleTagName = parser.name.substringAfterLast('.')
        return when (simpleTagName) {
            "SwitchPreferenceCompat",
            "SwitchPreference" -> SettingDestinationType.SwitchPreference(
                preferenceKey = key,
                defaultValue = readBooleanAttribute(
                    context = context,
                    parser = parser,
                    name = "defaultValue",
                    defaultValue = false,
                ),
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
        context: Context,
        parser: XmlResourceParser,
        name: String,
        defaultValue: Boolean,
    ): Boolean {
        listOf(ANDROID_NS, APP_NS).forEach { namespace ->
            val resourceId = parser.getAttributeResourceValue(namespace, name, 0)
            if (resourceId != 0) {
                return runCatching {
                    context.resources.getBoolean(resourceId)
                }.getOrDefault(defaultValue)
            }
        }

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
            "CollapsiblePreferenceCategory",
            "PreferenceScreen" -> true
            else -> false
        }

    private fun XmlResourceParser.isPreferenceCategory(): Boolean =
        name.substringAfterLast('.') in setOf("PreferenceCategory", "CollapsiblePreferenceCategory")

    private fun buildKeywords(
        key: String,
        title: String,
        summary: String,
    ): List<String> =
        buildList {
            add(key)
            add(title)
            add(summary)
            previousJapaneseTitles[key]?.let(::add)
            addAll(key.split('_', '-', '.'))
            addAll(extraKeywordsByKey[key].orEmpty())
        }
}
