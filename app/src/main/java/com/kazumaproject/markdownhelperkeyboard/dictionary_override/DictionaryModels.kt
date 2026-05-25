package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import androidx.annotation.StringRes
import com.kazumaproject.markdownhelperkeyboard.R

enum class DictionaryCategory {
    COMMON,
    SYSTEM,
    SINGLE_KANJI,
    EMOJI,
    EMOTICON,
    SYMBOL,
    READING_CORRECTION,
    KOTOWAZA,
    ENGLISH,
    PERSON_NAME,
    PLACES,
    WIKI,
    NEOLOGD,
    WEB,
}

enum class DictionaryFileRole {
    CONNECTION_ID,
    POS_TABLE,
    ID_DEF,
    TANGO,
    YOMI,
    TOKEN,
    ENGLISH_READING,
    ENGLISH_WORD,
    ENGLISH_TOKEN,
    ENGLISH_QWERTY_GLIDE_INDEX,
}

enum class DictionaryContentType {
    LOUDS,
    LOUDS_WITH_TERM_ID,
    TOKEN_ARRAY,
    CONNECTION_IDS,
    POS_TABLE,
    ID_DEF_TEXT,
    ENGLISH_READING,
    ENGLISH_WORD,
    ENGLISH_TOKEN,
    ENGLISH_QWERTY_GLIDE_INDEX,
}

enum class DictionaryFileKey {
    CONNECTION_ID,
    POS_TABLE,
    ID_DEF,
    SYSTEM_TANGO,
    SYSTEM_YOMI,
    SYSTEM_TOKEN,
    SINGLE_KANJI_TANGO,
    SINGLE_KANJI_YOMI,
    SINGLE_KANJI_TOKEN,
    EMOJI_TANGO,
    EMOJI_YOMI,
    EMOJI_TOKEN,
    EMOTICON_TANGO,
    EMOTICON_YOMI,
    EMOTICON_TOKEN,
    SYMBOL_TANGO,
    SYMBOL_YOMI,
    SYMBOL_TOKEN,
    READING_CORRECTION_TANGO,
    READING_CORRECTION_YOMI,
    READING_CORRECTION_TOKEN,
    KOTOWAZA_TANGO,
    KOTOWAZA_YOMI,
    KOTOWAZA_TOKEN,
    ENGLISH_READING,
    ENGLISH_WORD,
    ENGLISH_TOKEN,
    ENGLISH_QWERTY_GLIDE_INDEX,
    PERSON_NAME_TANGO,
    PERSON_NAME_YOMI,
    PERSON_NAME_TOKEN,
    PLACES_TANGO,
    PLACES_YOMI,
    PLACES_TOKEN,
    WIKI_TANGO,
    WIKI_YOMI,
    WIKI_TOKEN,
    NEOLOGD_TANGO,
    NEOLOGD_YOMI,
    NEOLOGD_TOKEN,
    WEB_TANGO,
    WEB_YOMI,
    WEB_TOKEN,
}

data class DictionaryFileSpec(
    val key: DictionaryFileKey,
    val category: DictionaryCategory,
    val role: DictionaryFileRole,
    val bundledAssetPath: String,
    val contentType: DictionaryContentType,
    @StringRes val displayNameRes: Int,
    val requiredForCoreEngine: Boolean,
    val partOfTripleDictionary: Boolean,
)

enum class ValidationStatus {
    VALID,
    INVALID,
}

data class ValidationResult(
    val status: ValidationStatus,
    val message: String,
) {
    val isValid: Boolean
        get() = status == ValidationStatus.VALID

    companion object {
        fun valid(message: String = "OK") = ValidationResult(ValidationStatus.VALID, message)
        fun invalid(message: String) = ValidationResult(ValidationStatus.INVALID, message)
    }
}

data class DictionaryOverrideMetadata(
    val key: DictionaryFileKey,
    val category: DictionaryCategory,
    val originalFileName: String,
    val importedAt: Long,
    val size: Long,
    val contentType: DictionaryContentType,
    val validationStatus: ValidationStatus,
    val validationMessage: String,
)

data class DictionaryOverrideState(
    val spec: DictionaryFileSpec,
    val hasOverride: Boolean,
    val metadata: DictionaryOverrideMetadata?,
    val externalEnabled: Boolean,
)

enum class DictionaryCategoryLoadState {
    Bundled,
    User,
    Disabled,
    Partial,
    Invalid,
    Missing,
}

object DictionaryFileSpecs {
    private val bundledRuntimeArtifacts = setOf(
        DictionaryFileKey.ENGLISH_QWERTY_GLIDE_INDEX,
    )

    val all: List<DictionaryFileSpec> = listOf(
        spec(DictionaryFileKey.CONNECTION_ID, DictionaryCategory.COMMON, DictionaryFileRole.CONNECTION_ID, "connectionId.dat.zip", DictionaryContentType.CONNECTION_IDS, R.string.external_dictionary_file_connection_id, true, false),
        spec(DictionaryFileKey.POS_TABLE, DictionaryCategory.COMMON, DictionaryFileRole.POS_TABLE, "pos_table.dat", DictionaryContentType.POS_TABLE, R.string.external_dictionary_file_pos_table, true, false),
        spec(DictionaryFileKey.ID_DEF, DictionaryCategory.COMMON, DictionaryFileRole.ID_DEF, "id.def", DictionaryContentType.ID_DEF_TEXT, R.string.external_dictionary_file_id_def, false, false),

        triple(DictionaryFileKey.SYSTEM_TANGO, DictionaryCategory.SYSTEM, DictionaryFileRole.TANGO, "system/tango.dat.zip", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, true),
        triple(DictionaryFileKey.SYSTEM_YOMI, DictionaryCategory.SYSTEM, DictionaryFileRole.YOMI, "system/yomi.dat.zip", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, true),
        triple(DictionaryFileKey.SYSTEM_TOKEN, DictionaryCategory.SYSTEM, DictionaryFileRole.TOKEN, "system/token.dat.zip", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, true),

        triple(DictionaryFileKey.SINGLE_KANJI_TANGO, DictionaryCategory.SINGLE_KANJI, DictionaryFileRole.TANGO, "single_kanji/tango_singleKanji.dat", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, true),
        triple(DictionaryFileKey.SINGLE_KANJI_YOMI, DictionaryCategory.SINGLE_KANJI, DictionaryFileRole.YOMI, "single_kanji/yomi_singleKanji.dat", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, true),
        triple(DictionaryFileKey.SINGLE_KANJI_TOKEN, DictionaryCategory.SINGLE_KANJI, DictionaryFileRole.TOKEN, "single_kanji/token_singleKanji.dat", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, true),

        triple(DictionaryFileKey.EMOJI_TANGO, DictionaryCategory.EMOJI, DictionaryFileRole.TANGO, "emoji/tango_emoji.dat", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, true),
        triple(DictionaryFileKey.EMOJI_YOMI, DictionaryCategory.EMOJI, DictionaryFileRole.YOMI, "emoji/yomi_emoji.dat", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, true),
        triple(DictionaryFileKey.EMOJI_TOKEN, DictionaryCategory.EMOJI, DictionaryFileRole.TOKEN, "emoji/token_emoji.dat", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, true),

        triple(DictionaryFileKey.EMOTICON_TANGO, DictionaryCategory.EMOTICON, DictionaryFileRole.TANGO, "emoticon/tango_emoticon.dat", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, true),
        triple(DictionaryFileKey.EMOTICON_YOMI, DictionaryCategory.EMOTICON, DictionaryFileRole.YOMI, "emoticon/yomi_emoticon.dat", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, true),
        triple(DictionaryFileKey.EMOTICON_TOKEN, DictionaryCategory.EMOTICON, DictionaryFileRole.TOKEN, "emoticon/token_emoticon.dat", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, true),

        triple(DictionaryFileKey.SYMBOL_TANGO, DictionaryCategory.SYMBOL, DictionaryFileRole.TANGO, "symbol/tango_symbol.dat", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, true),
        triple(DictionaryFileKey.SYMBOL_YOMI, DictionaryCategory.SYMBOL, DictionaryFileRole.YOMI, "symbol/yomi_symbol.dat", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, true),
        triple(DictionaryFileKey.SYMBOL_TOKEN, DictionaryCategory.SYMBOL, DictionaryFileRole.TOKEN, "symbol/token_symbol.dat", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, true),

        triple(DictionaryFileKey.READING_CORRECTION_TANGO, DictionaryCategory.READING_CORRECTION, DictionaryFileRole.TANGO, "reading_correction/tango_reading_correction.dat", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, true),
        triple(DictionaryFileKey.READING_CORRECTION_YOMI, DictionaryCategory.READING_CORRECTION, DictionaryFileRole.YOMI, "reading_correction/yomi_reading_correction.dat", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, true),
        triple(DictionaryFileKey.READING_CORRECTION_TOKEN, DictionaryCategory.READING_CORRECTION, DictionaryFileRole.TOKEN, "reading_correction/token_reading_correction.dat", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, true),

        triple(DictionaryFileKey.KOTOWAZA_TANGO, DictionaryCategory.KOTOWAZA, DictionaryFileRole.TANGO, "kotowaza/tango_kotowaza.dat", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, true),
        triple(DictionaryFileKey.KOTOWAZA_YOMI, DictionaryCategory.KOTOWAZA, DictionaryFileRole.YOMI, "kotowaza/yomi_kotowaza.dat", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, true),
        triple(DictionaryFileKey.KOTOWAZA_TOKEN, DictionaryCategory.KOTOWAZA, DictionaryFileRole.TOKEN, "kotowaza/token_kotowaza.dat", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, true),

        triple(DictionaryFileKey.ENGLISH_READING, DictionaryCategory.ENGLISH, DictionaryFileRole.ENGLISH_READING, "english/reading.dat.zip", DictionaryContentType.ENGLISH_READING, R.string.external_dictionary_file_english_reading, true),
        triple(DictionaryFileKey.ENGLISH_WORD, DictionaryCategory.ENGLISH, DictionaryFileRole.ENGLISH_WORD, "english/word.dat", DictionaryContentType.ENGLISH_WORD, R.string.external_dictionary_file_english_word, true),
        triple(DictionaryFileKey.ENGLISH_TOKEN, DictionaryCategory.ENGLISH, DictionaryFileRole.ENGLISH_TOKEN, "english/token.dat.zip", DictionaryContentType.ENGLISH_TOKEN, R.string.external_dictionary_file_english_token, true),
        spec(DictionaryFileKey.ENGLISH_QWERTY_GLIDE_INDEX, DictionaryCategory.ENGLISH, DictionaryFileRole.ENGLISH_QWERTY_GLIDE_INDEX, "english/qwerty_glide_index.dat", DictionaryContentType.ENGLISH_QWERTY_GLIDE_INDEX, R.string.external_dictionary_file_english_word, false, false),

        triple(DictionaryFileKey.PERSON_NAME_TANGO, DictionaryCategory.PERSON_NAME, DictionaryFileRole.TANGO, "person_name/tango_person_names.dat", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, false),
        triple(DictionaryFileKey.PERSON_NAME_YOMI, DictionaryCategory.PERSON_NAME, DictionaryFileRole.YOMI, "person_name/yomi_person_names.dat", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, false),
        triple(DictionaryFileKey.PERSON_NAME_TOKEN, DictionaryCategory.PERSON_NAME, DictionaryFileRole.TOKEN, "person_name/token_person_names.dat", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, false),

        triple(DictionaryFileKey.PLACES_TANGO, DictionaryCategory.PLACES, DictionaryFileRole.TANGO, "places/tango_places.dat.zip", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, false),
        triple(DictionaryFileKey.PLACES_YOMI, DictionaryCategory.PLACES, DictionaryFileRole.YOMI, "places/yomi_places.dat.zip", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, false),
        triple(DictionaryFileKey.PLACES_TOKEN, DictionaryCategory.PLACES, DictionaryFileRole.TOKEN, "places/token_places.dat.zip", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, false),

        triple(DictionaryFileKey.WIKI_TANGO, DictionaryCategory.WIKI, DictionaryFileRole.TANGO, "wiki/tango_wiki.dat.zip", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, false),
        triple(DictionaryFileKey.WIKI_YOMI, DictionaryCategory.WIKI, DictionaryFileRole.YOMI, "wiki/yomi_wiki.dat.zip", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, false),
        triple(DictionaryFileKey.WIKI_TOKEN, DictionaryCategory.WIKI, DictionaryFileRole.TOKEN, "wiki/token_wiki.dat.zip", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, false),

        triple(DictionaryFileKey.NEOLOGD_TANGO, DictionaryCategory.NEOLOGD, DictionaryFileRole.TANGO, "neologd/tango_neologd.dat.zip", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, false),
        triple(DictionaryFileKey.NEOLOGD_YOMI, DictionaryCategory.NEOLOGD, DictionaryFileRole.YOMI, "neologd/yomi_neologd.dat.zip", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, false),
        triple(DictionaryFileKey.NEOLOGD_TOKEN, DictionaryCategory.NEOLOGD, DictionaryFileRole.TOKEN, "neologd/token_neologd.dat.zip", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, false),

        triple(DictionaryFileKey.WEB_TANGO, DictionaryCategory.WEB, DictionaryFileRole.TANGO, "web/tango_web.dat.zip", DictionaryContentType.LOUDS, R.string.external_dictionary_file_tango, false),
        triple(DictionaryFileKey.WEB_YOMI, DictionaryCategory.WEB, DictionaryFileRole.YOMI, "web/yomi_web.dat.zip", DictionaryContentType.LOUDS_WITH_TERM_ID, R.string.external_dictionary_file_yomi, false),
        triple(DictionaryFileKey.WEB_TOKEN, DictionaryCategory.WEB, DictionaryFileRole.TOKEN, "web/token_web.dat.zip", DictionaryContentType.TOKEN_ARRAY, R.string.external_dictionary_file_token, false),
    )

    private val byKey = all.associateBy { it.key }

    fun get(key: DictionaryFileKey): DictionaryFileSpec = byKey.getValue(key)

    fun forCategory(category: DictionaryCategory): List<DictionaryFileSpec> =
        all.filter { it.category == category && it.key !in bundledRuntimeArtifacts }

    fun tripleCategories(): List<DictionaryCategory> =
        all.filter { it.partOfTripleDictionary }.map { it.category }.distinct()

    private fun spec(
        key: DictionaryFileKey,
        category: DictionaryCategory,
        role: DictionaryFileRole,
        bundledAssetPath: String,
        contentType: DictionaryContentType,
        @StringRes displayNameRes: Int,
        requiredForCoreEngine: Boolean,
        partOfTripleDictionary: Boolean,
    ) = DictionaryFileSpec(
        key = key,
        category = category,
        role = role,
        bundledAssetPath = bundledAssetPath,
        contentType = contentType,
        displayNameRes = displayNameRes,
        requiredForCoreEngine = requiredForCoreEngine,
        partOfTripleDictionary = partOfTripleDictionary,
    )

    private fun triple(
        key: DictionaryFileKey,
        category: DictionaryCategory,
        role: DictionaryFileRole,
        bundledAssetPath: String,
        contentType: DictionaryContentType,
        @StringRes displayNameRes: Int,
        requiredForCoreEngine: Boolean,
    ) = spec(key, category, role, bundledAssetPath, contentType, displayNameRes, requiredForCoreEngine, true)
}

fun DictionaryCategory.isOptionalMozcUt(): Boolean =
    this in setOf(
        DictionaryCategory.PERSON_NAME,
        DictionaryCategory.PLACES,
        DictionaryCategory.WIKI,
        DictionaryCategory.NEOLOGD,
        DictionaryCategory.WEB,
    )

fun DictionaryCategory.isDisableableBundledDictionary(): Boolean =
    this in setOf(
        DictionaryCategory.READING_CORRECTION,
        DictionaryCategory.KOTOWAZA,
        DictionaryCategory.PERSON_NAME,
        DictionaryCategory.PLACES,
        DictionaryCategory.WIKI,
        DictionaryCategory.NEOLOGD,
        DictionaryCategory.WEB,
    )
