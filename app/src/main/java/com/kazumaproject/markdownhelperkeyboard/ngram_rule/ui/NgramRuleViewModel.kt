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
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.NgramRuleEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.NgramRuleRepository
import com.kazumaproject.markdownhelperkeyboard.repository.NodeFeatureValue
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

    val rules: LiveData<List<NgramRuleItem>> = repository.observeEntities()
        .map { entities -> entities.map { it.toItem() } }
        .asLiveData()

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
                        .filter { it.type.toInt() == 1 && it.length.toInt() == yomi.length }
                        .map { it.toSuggestion() }
                        .distinctBy { it.word }
                        .sortedBy { it.score }
                        .take(100)
                        .toList()
                }
            }.getOrDefault(emptyList())
            _wordSuggestions.value = suggestions
        }
    }

    fun saveRule(form: NgramRuleForm) {
        val entity = NgramRuleRepository.entityFromNodes(
            id = form.id ?: 0,
            nodes = form.nodes.map { it.toValue() },
            adjustment = form.adjustment.coerceIn(ADJUSTMENT_MIN, ADJUSTMENT_MAX),
        )
        viewModelScope.launch {
            repository.upsertRule(entity, editingId = form.id)
            scorerManager.refreshNow()
        }
    }

    fun deleteRule(id: Int) {
        viewModelScope.launch {
            repository.deleteRule(id)
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
        validateBackup(backup)
        viewModelScope.launch {
            repository.replaceAll(
                backup.rules.map { rule ->
                    NgramRuleRepository.entityFromNodes(
                        nodes = rule.nodes.map { it.toValue() },
                        adjustment = rule.adjustment.coerceIn(ADJUSTMENT_MIN, ADJUSTMENT_MAX),
                    )
                },
            )
            scorerManager.refreshNow()
        }
    }

    private fun validateBackup(backup: NgramRuleBackup) {
        require(backup.version == NGRAM_BACKUP_VERSION)
        val validIds = idEntries().mapTo(HashSet()) { it.id }
        backup.rules.forEach { rule ->
            require(rule.nodes.size in 2..5)
            require(rule.adjustment in ADJUSTMENT_MIN..ADJUSTMENT_MAX)
            rule.nodes.forEach { node ->
                require(node.leftId == null || node.leftId in validIds)
                require(node.rightId == null || node.rightId in validIds)
            }
        }
    }

    private fun NgramRuleEntity.toItem(): NgramRuleItem {
        val allNodes = listOf(
            input(node1Word, node1LeftId, node1RightId),
            input(node2Word, node2LeftId, node2RightId),
            input(node3Word, node3LeftId, node3RightId),
            input(node4Word, node4LeftId, node4RightId),
            input(node5Word, node5LeftId, node5RightId),
        )
        return NgramRuleItem(id, allNodes.take(nodeCount), adjustment)
    }

    private fun input(word: String, leftId: Int, rightId: Int) = NodeFeatureInput(
        word = word.ifBlank { null },
        leftId = leftId.takeUnless { it == NgramRuleRepository.WILDCARD_ID },
        rightId = rightId.takeUnless { it == NgramRuleRepository.WILDCARD_ID },
    )

    private fun NodeFeatureInput.toValue() = NodeFeatureValue(word, leftId, rightId)
    private fun Candidate.toSuggestion() = WordSuggestion(string, score)

    companion object {
        const val ADJUSTMENT_MIN = -10000
        const val ADJUSTMENT_MAX = 10000
        const val NGRAM_BACKUP_VERSION = 2
    }
}

data class NodeFeatureInput(
    val word: String? = null,
    val leftId: Int? = null,
    val rightId: Int? = null,
)

data class NgramRuleItem(
    val id: Int,
    val nodes: List<NodeFeatureInput>,
    val adjustment: Int,
)

data class NgramRuleForm(
    val id: Int? = null,
    val nodes: List<NodeFeatureInput>,
    val adjustment: Int,
)

data class WordSuggestion(val word: String, val score: Int)

data class NgramRuleBackup(
    val version: Int = NgramRuleViewModel.NGRAM_BACKUP_VERSION,
    val rules: List<NgramRuleBackupItem> = emptyList(),
)

data class NgramRuleBackupItem(
    val nodes: List<NodeFeatureInput>,
    val adjustment: Int,
)

/** Version 1 JSON compatibility models. */
data class LegacyNgramRuleBackup(
    val twoNodeRules: List<TwoNodeRuleBackup> = emptyList(),
    val threeNodeRules: List<ThreeNodeRuleBackup> = emptyList(),
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
