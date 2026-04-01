package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NodeFeature
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.ThreeNodeRule
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.TwoNodeRule
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.NgramRuleDao
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.ThreeNodeRuleEntity
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.TwoNodeRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NgramRuleRepository @Inject constructor(
    private val dao: NgramRuleDao,
) {
    fun observeTwoNodeRules(): Flow<List<TwoNodeRuleEntity>> = dao.observeTwoNodeRules()

    fun observeThreeNodeRules(): Flow<List<ThreeNodeRuleEntity>> = dao.observeThreeNodeRules()

    fun observeDomainRules(): Flow<Pair<List<TwoNodeRule>, List<ThreeNodeRule>>> {
        return combine(observeTwoNodeRules(), observeThreeNodeRules()) { two, three ->
            two.map { it.toDomain() } to three.map { it.toDomain() }
        }
    }

    suspend fun loadDomainRules(): Pair<List<TwoNodeRule>, List<ThreeNodeRule>> {
        return dao.getAllTwoNodeRules().map { it.toDomain() } to
            dao.getAllThreeNodeRules().map { it.toDomain() }
    }

    suspend fun upsertTwoNodeRule(entity: TwoNodeRuleEntity, editingId: Int?) {
        if (editingId != null) dao.deleteTwoNodeRule(editingId)
        dao.upsertTwoNodeRule(entity)
    }

    suspend fun upsertThreeNodeRule(entity: ThreeNodeRuleEntity, editingId: Int?) {
        if (editingId != null) dao.deleteThreeNodeRule(editingId)
        dao.upsertThreeNodeRule(entity)
    }

    suspend fun deleteTwoNodeRule(id: Int) = dao.deleteTwoNodeRule(id)

    suspend fun deleteThreeNodeRule(id: Int) = dao.deleteThreeNodeRule(id)

    suspend fun deleteAll() {
        dao.deleteAllTwoNodeRules()
        dao.deleteAllThreeNodeRules()
    }

    suspend fun replaceAll(twoRules: List<TwoNodeRuleEntity>, threeRules: List<ThreeNodeRuleEntity>) {
        dao.deleteAllTwoNodeRules()
        dao.deleteAllThreeNodeRules()
        dao.insertAllTwoNodeRules(twoRules)
        dao.insertAllThreeNodeRules(threeRules)
    }

    private fun TwoNodeRuleEntity.toDomain(): TwoNodeRule {
        return TwoNodeRule(
            prev = NodeFeature(
                word = prevWord.toNullableWord(),
                leftId = prevLeftId.toNullableId(),
                rightId = prevRightId.toNullableId(),
            ),
            current = NodeFeature(
                word = currentWord.toNullableWord(),
                leftId = currentLeftId.toNullableId(),
                rightId = currentRightId.toNullableId(),
            ),
            adjustment = adjustment,
        )
    }

    private fun ThreeNodeRuleEntity.toDomain(): ThreeNodeRule {
        return ThreeNodeRule(
            first = NodeFeature(
                word = firstWord.toNullableWord(),
                leftId = firstLeftId.toNullableId(),
                rightId = firstRightId.toNullableId(),
            ),
            second = NodeFeature(
                word = secondWord.toNullableWord(),
                leftId = secondLeftId.toNullableId(),
                rightId = secondRightId.toNullableId(),
            ),
            third = NodeFeature(
                word = thirdWord.toNullableWord(),
                leftId = thirdLeftId.toNullableId(),
                rightId = thirdRightId.toNullableId(),
            ),
            adjustment = adjustment,
        )
    }

    private fun String.toNullableWord(): String? = if (isBlank()) null else this

    private fun Int.toNullableId(): Short? = if (this < 0) null else this.toShort()

    companion object {
        const val WILDCARD_ID = -1

        fun normalizeWord(word: String?): String = word?.trim().orEmpty()

        fun normalizeId(id: Int?): Int = id ?: WILDCARD_ID
    }
}

