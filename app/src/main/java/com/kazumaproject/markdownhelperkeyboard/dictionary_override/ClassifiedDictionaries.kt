package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import androidx.annotation.StringRes
import com.kazumaproject.markdownhelperkeyboard.R

data class ClassifiedDictionarySpec(
    val category: DictionaryCategory,
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val labelRes: Int,
) {
    val licenseAssetPath: String get() = "licenses/classified/$id.html"
}

object ClassifiedDictionaries {
    const val VERSION = "v1.7.262-pre.1"
    val all: List<ClassifiedDictionarySpec> = listOf(
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_PERSON, "person", R.string.classified_dictionary_person, R.string.classified_label_person),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_CHARACTER, "character", R.string.classified_dictionary_character, R.string.classified_label_character),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_MYTHOLOGICAL_FIGURE, "mythological_figure", R.string.classified_dictionary_mythological_figure, R.string.classified_label_mythological_figure),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_PLACE, "place", R.string.classified_dictionary_place, R.string.classified_label_place),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_ADDRESS, "address", R.string.classified_dictionary_address, R.string.classified_label_address),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_STATION, "station", R.string.classified_dictionary_station, R.string.classified_label_station),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_FACILITY, "facility", R.string.classified_dictionary_facility, R.string.classified_label_facility),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_TRANSPORT_ROUTE, "transport_route", R.string.classified_dictionary_transport_route, R.string.classified_label_transport_route),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_ORGANIZATION, "organization", R.string.classified_dictionary_organization, R.string.classified_label_organization),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_LINEAGE, "lineage", R.string.classified_dictionary_lineage, R.string.classified_label_lineage),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_PRODUCT, "product", R.string.classified_dictionary_product, R.string.classified_label_product),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_VEHICLE, "vehicle", R.string.classified_dictionary_vehicle, R.string.classified_label_vehicle),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_EVENT, "event", R.string.classified_dictionary_event, R.string.classified_label_event),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_AWARD, "award", R.string.classified_dictionary_award, R.string.classified_label_award),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_AGREEMENT, "agreement", R.string.classified_dictionary_agreement, R.string.classified_label_agreement),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_INITIATIVE, "initiative", R.string.classified_dictionary_initiative, R.string.classified_label_initiative),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_TERM, "term", R.string.classified_dictionary_term, R.string.classified_label_term),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_FRANCHISE, "work_franchise", R.string.classified_dictionary_work_franchise, R.string.classified_label_work_franchise),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_MANGA, "work_manga", R.string.classified_dictionary_work_manga, R.string.classified_label_work_manga),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_ANIME, "work_anime", R.string.classified_dictionary_work_anime, R.string.classified_label_work_anime),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_FILM, "work_film", R.string.classified_dictionary_work_film, R.string.classified_label_work_film),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_PROGRAM, "work_program", R.string.classified_dictionary_work_program, R.string.classified_label_work_program),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_BOOK, "work_book", R.string.classified_dictionary_work_book, R.string.classified_label_work_book),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_GAME, "work_game", R.string.classified_dictionary_work_game, R.string.classified_label_work_game),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_MUSIC, "work_music", R.string.classified_dictionary_work_music, R.string.classified_label_work_music),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_STAGE, "work_stage", R.string.classified_dictionary_work_stage, R.string.classified_label_work_stage),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_PERIODICAL, "work_periodical", R.string.classified_dictionary_work_periodical, R.string.classified_label_work_periodical),
        ClassifiedDictionarySpec(DictionaryCategory.CLASSIFIED_WORK_OTHER, "work_other", R.string.classified_dictionary_work_other, R.string.classified_label_work_other),
    )
    val categories: Set<DictionaryCategory> = all.map { it.category }.toSet()
    fun find(category: DictionaryCategory): ClassifiedDictionarySpec? = all.firstOrNull { it.category == category }
    fun ordered(categories: Set<DictionaryCategory>): List<ClassifiedDictionarySpec> = all.filter { it.category in categories }
}
