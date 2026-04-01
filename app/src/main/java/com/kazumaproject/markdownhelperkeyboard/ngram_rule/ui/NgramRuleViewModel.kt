package com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHiragana
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.NgramRuleScorerManager
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.ThreeNodeRuleEntity
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.TwoNodeRuleEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.NgramRuleRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.IdDefEntry
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.IdDefEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NgramRuleViewModel @Inject constructor(
    private val repository: NgramRuleRepository,
    private val scorerManager: NgramRuleScorerManager,
    private val kanaKanjiEngine: KanaKanjiEngine,
    private val userDictionaryRepository: UserDictionaryRepository,
    private val learnRepository: LearnRepository,
    private val appPreference: AppPreference,
    private val idDefEntryRepository: IdDefEntryRepository,
) : ViewModel() {

    val twoNodeRules: LiveData<List<TwoNodeRuleItem>> =
        repository.observeTwoNodeRules().map { list -> list.map { it.toItem() } }.asLiveData()

    val threeNodeRules: LiveData<List<ThreeNodeRuleItem>> =
        repository.observeThreeNodeRules().map { list -> list.map { it.toItem() } }.asLiveData()

    private val _wordSuggestions = MutableLiveData<List<WordSuggestion>>(emptyList())
    val wordSuggestions: LiveData<List<WordSuggestion>> = _wordSuggestions

    fun idEntries(): List<IdDefEntry> = idDefEntryRepository.getEntries()

    fun searchWordsByHiragana(input: String) {
        val yomi = input.trim()
        if (yomi.isEmpty() || !yomi.isAllHiragana()) {
            _wordSuggestions.value = emptyList()
            return
        }

        viewModelScope.launch {
            val suggestions = runCatching {
                withContext(Dispatchers.Default) {
                    kanaKanjiEngine.getCandidatesOriginal(
                        input = yomi,
                        n = 120,
                        mozcUtPersonName = appPreference.mozc_ut_person_names_preference,
                        mozcUTPlaces = appPreference.mozc_ut_places_preference,
                        mozcUTWiki = appPreference.mozc_ut_wiki_preference,
                        mozcUTNeologd = appPreference.mozc_ut_neologd_preference,
                        mozcUTWeb = appPreference.mozc_ut_web_preference,
                        userDictionaryRepository = userDictionaryRepository,
                        learnRepository = if (appPreference.learn_dictionary_preference == true) learnRepository else null,
                        isOmissionSearchEnable = appPreference.omission_search_preference == true,
                        enableTypoCorrectionJapaneseFlick = appPreference.enable_typo_correction_japanese_flick_keyboard_preference,
                        enableTypoCorrectionQwertyEnglish = appPreference.enable_typo_correction_qwerty_english_keyboard_preference,
                        typoCorrectionOffsetScore = appPreference.enable_typo_correction_japanese_flick_keyboard_offset_score_preference,
                        omissionSearchOffsetScore = appPreference.omission_search_offset_score_preference,
                    )
                        .asSequence()
                        .filter { candidate -> candidate.type.toInt() == 1 }
                        .filter { candidate -> candidate.length.toInt() == yomi.length }
                        .map { candidate -> candidate.toSuggestion() }
                        .distinctBy { it.word }
                        .sortedBy { it.score }
                        .take(100)
                        .toList()
                }
            }.getOrDefault(emptyList())

            _wordSuggestions.value = suggestions
        }
    }

    fun saveTwoNodeRule(form: TwoNodeRuleForm) {
        val adjustment = form.adjustment.coerceIn(ADJUSTMENT_MIN, ADJUSTMENT_MAX)
        viewModelScope.launch {
            repository.upsertTwoNodeRule(
                entity = TwoNodeRuleEntity(
                    prevWord = NgramRuleRepository.normalizeWord(form.prev.word),
                    prevLeftId = NgramRuleRepository.normalizeId(form.prev.leftId),
                    prevRightId = NgramRuleRepository.normalizeId(form.prev.rightId),
                    currentWord = NgramRuleRepository.normalizeWord(form.current.word),
                    currentLeftId = NgramRuleRepository.normalizeId(form.current.leftId),
                    currentRightId = NgramRuleRepository.normalizeId(form.current.rightId),
                    adjustment = adjustment,
                ),
                editingId = form.id,
            )
            scorerManager.refreshNow()
        }
    }

    fun saveThreeNodeRule(form: ThreeNodeRuleForm) {
        val adjustment = form.adjustment.coerceIn(ADJUSTMENT_MIN, ADJUSTMENT_MAX)
        viewModelScope.launch {
            repository.upsertThreeNodeRule(
                entity = ThreeNodeRuleEntity(
                    firstWord = NgramRuleRepository.normalizeWord(form.first.word),
                    firstLeftId = NgramRuleRepository.normalizeId(form.first.leftId),
                    firstRightId = NgramRuleRepository.normalizeId(form.first.rightId),
                    secondWord = NgramRuleRepository.normalizeWord(form.second.word),
                    secondLeftId = NgramRuleRepository.normalizeId(form.second.leftId),
                    secondRightId = NgramRuleRepository.normalizeId(form.second.rightId),
                    thirdWord = NgramRuleRepository.normalizeWord(form.third.word),
                    thirdLeftId = NgramRuleRepository.normalizeId(form.third.leftId),
                    thirdRightId = NgramRuleRepository.normalizeId(form.third.rightId),
                    adjustment = adjustment,
                ),
                editingId = form.id,
            )
            scorerManager.refreshNow()
        }
    }

    fun deleteTwoNodeRule(id: Int) {
        viewModelScope.launch {
            repository.deleteTwoNodeRule(id)
            scorerManager.refreshNow()
        }
    }

    fun deleteThreeNodeRule(id: Int) {
        viewModelScope.launch {
            repository.deleteThreeNodeRule(id)
            scorerManager.refreshNow()
        }
    }

    fun deleteAllRules() {
        viewModelScope.launch {
            repository.deleteAll()
            scorerManager.refreshNow()
        }
    }

    fun replaceAll(backup: NgramRuleBackup) {
        viewModelScope.launch {
            repository.replaceAll(
                twoRules = backup.twoNodeRules.map {
                    TwoNodeRuleEntity(
                        prevWord = NgramRuleRepository.normalizeWord(it.prev.word),
                        prevLeftId = NgramRuleRepository.normalizeId(it.prev.leftId),
                        prevRightId = NgramRuleRepository.normalizeId(it.prev.rightId),
                        currentWord = NgramRuleRepository.normalizeWord(it.current.word),
                        currentLeftId = NgramRuleRepository.normalizeId(it.current.leftId),
                        currentRightId = NgramRuleRepository.normalizeId(it.current.rightId),
                        adjustment = it.adjustment.coerceIn(ADJUSTMENT_MIN, ADJUSTMENT_MAX),
                    )
                },
                threeRules = backup.threeNodeRules.map {
                    ThreeNodeRuleEntity(
                        firstWord = NgramRuleRepository.normalizeWord(it.first.word),
                        firstLeftId = NgramRuleRepository.normalizeId(it.first.leftId),
                        firstRightId = NgramRuleRepository.normalizeId(it.first.rightId),
                        secondWord = NgramRuleRepository.normalizeWord(it.second.word),
                        secondLeftId = NgramRuleRepository.normalizeId(it.second.leftId),
                        secondRightId = NgramRuleRepository.normalizeId(it.second.rightId),
                        thirdWord = NgramRuleRepository.normalizeWord(it.third.word),
                        thirdLeftId = NgramRuleRepository.normalizeId(it.third.leftId),
                        thirdRightId = NgramRuleRepository.normalizeId(it.third.rightId),
                        adjustment = it.adjustment.coerceIn(ADJUSTMENT_MIN, ADJUSTMENT_MAX),
                    )
                },
            )
            scorerManager.refreshNow()
        }
    }

    private fun TwoNodeRuleEntity.toItem(): TwoNodeRuleItem {
        return TwoNodeRuleItem(
            id = id,
            prev = NodeFeatureInput(
                word = prevWord.ifBlank { null },
                leftId = prevLeftId.toNullableId(),
                rightId = prevRightId.toNullableId(),
            ),
            current = NodeFeatureInput(
                word = currentWord.ifBlank { null },
                leftId = currentLeftId.toNullableId(),
                rightId = currentRightId.toNullableId(),
            ),
            adjustment = adjustment,
        )
    }

    private fun ThreeNodeRuleEntity.toItem(): ThreeNodeRuleItem {
        return ThreeNodeRuleItem(
            id = id,
            first = NodeFeatureInput(
                word = firstWord.ifBlank { null },
                leftId = firstLeftId.toNullableId(),
                rightId = firstRightId.toNullableId(),
            ),
            second = NodeFeatureInput(
                word = secondWord.ifBlank { null },
                leftId = secondLeftId.toNullableId(),
                rightId = secondRightId.toNullableId(),
            ),
            third = NodeFeatureInput(
                word = thirdWord.ifBlank { null },
                leftId = thirdLeftId.toNullableId(),
                rightId = thirdRightId.toNullableId(),
            ),
            adjustment = adjustment,
        )
    }

    private fun Int.toNullableId(): Int? = if (this == NgramRuleRepository.WILDCARD_ID) null else this

    private fun Candidate.toSuggestion(): WordSuggestion {
        return WordSuggestion(
            word = string,
            score = score,
        )
    }

    companion object {
        const val ADJUSTMENT_MIN = -10000
        const val ADJUSTMENT_MAX = 10000
    }
}

data class NodeFeatureInput(
    val word: String? = null,
    val leftId: Int? = null,
    val rightId: Int? = null,
)

data class TwoNodeRuleItem(
    val id: Int,
    val prev: NodeFeatureInput,
    val current: NodeFeatureInput,
    val adjustment: Int,
)

data class ThreeNodeRuleItem(
    val id: Int,
    val first: NodeFeatureInput,
    val second: NodeFeatureInput,
    val third: NodeFeatureInput,
    val adjustment: Int,
)

data class TwoNodeRuleForm(
    val id: Int? = null,
    val prev: NodeFeatureInput,
    val current: NodeFeatureInput,
    val adjustment: Int,
)

data class ThreeNodeRuleForm(
    val id: Int? = null,
    val first: NodeFeatureInput,
    val second: NodeFeatureInput,
    val third: NodeFeatureInput,
    val adjustment: Int,
)

data class WordSuggestion(
    val word: String,
    val score: Int,
)

data class NgramRuleBackup(
    val twoNodeRules: List<TwoNodeRuleBackup>,
    val threeNodeRules: List<ThreeNodeRuleBackup>,
)

data class TwoNodeRuleBackup(
    val prev: NodeFeatureInput,
    val current: NodeFeatureInput,
    val adjustment: Int,
)

data class ThreeNodeRuleBackup(
    val first: NodeFeatureInput,
    val second: NodeFeatureInput,
    val third: NodeFeatureInput,
    val adjustment: Int,
)

