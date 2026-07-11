package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRule
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NodeFeature
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.NgramRuleDao
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.database.NgramRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NgramRuleRepository @Inject constructor(
    private val dao: NgramRuleDao,
) {
    fun observeEntities(): Flow<List<NgramRuleEntity>> = dao.observeRules()

    fun observeDomainRules(): Flow<List<NgramRule>> =
        dao.observeRules().map { entities -> entities.map { it.toDomain() } }

    suspend fun loadDomainRules(): List<NgramRule> = dao.getAllRules().map { it.toDomain() }

    suspend fun loadEntities(): List<NgramRuleEntity> = dao.getAllRules()

    suspend fun upsertRule(entity: NgramRuleEntity, editingId: Int?) {
        validateNodeCount(entity.nodeCount)
        if (editingId != null && editingId != entity.id) dao.deleteRule(editingId)
        dao.upsertRule(entity)
    }

    suspend fun deleteRule(id: Int) = dao.deleteRule(id)

    suspend fun deleteAll() = dao.deleteAllRules()

    suspend fun replaceAll(rules: List<NgramRuleEntity>) {
        rules.forEach { validateNodeCount(it.nodeCount) }
        dao.replaceAll(rules)
    }

    fun NgramRuleEntity.toDomain(): NgramRule {
        validateNodeCount(nodeCount)
        val allNodes = listOf(
            feature(node1Word, node1LeftId, node1RightId),
            feature(node2Word, node2LeftId, node2RightId),
            feature(node3Word, node3LeftId, node3RightId),
            feature(node4Word, node4LeftId, node4RightId),
            feature(node5Word, node5LeftId, node5RightId),
        )
        return NgramRule(allNodes.take(nodeCount), adjustment)
    }

    private fun feature(word: String, leftId: Int, rightId: Int): NodeFeature =
        NodeFeature(word.toNullableWord(), leftId.toNullableId(), rightId.toNullableId())

    companion object {
        const val WILDCARD_ID = -1

        fun normalizeWord(word: String?): String = word?.trim().orEmpty()

        fun normalizeId(id: Int?): Int = id ?: WILDCARD_ID

        fun validateNodeCount(nodeCount: Int) {
            require(nodeCount in NgramRule.MIN_NODE_COUNT..NgramRule.MAX_NODE_COUNT) {
                "N-gram node count must be between 2 and 5: $nodeCount"
            }
        }

        fun entityFromNodes(
            id: Int = 0,
            nodes: List<NodeFeatureValue>,
            adjustment: Int,
        ): NgramRuleEntity {
            validateNodeCount(nodes.size)
            val padded = nodes + List(NgramRule.MAX_NODE_COUNT - nodes.size) { NodeFeatureValue() }
            return NgramRuleEntity(
                id = id,
                nodeCount = nodes.size,
                node1Word = normalizeWord(padded[0].word),
                node1LeftId = normalizeId(padded[0].leftId),
                node1RightId = normalizeId(padded[0].rightId),
                node2Word = normalizeWord(padded[1].word),
                node2LeftId = normalizeId(padded[1].leftId),
                node2RightId = normalizeId(padded[1].rightId),
                node3Word = normalizeWord(padded[2].word),
                node3LeftId = normalizeId(padded[2].leftId),
                node3RightId = normalizeId(padded[2].rightId),
                node4Word = normalizeWord(padded[3].word),
                node4LeftId = normalizeId(padded[3].leftId),
                node4RightId = normalizeId(padded[3].rightId),
                node5Word = normalizeWord(padded[4].word),
                node5LeftId = normalizeId(padded[4].leftId),
                node5RightId = normalizeId(padded[4].rightId),
                adjustment = adjustment,
            )
        }

        private fun String.toNullableWord(): String? = ifBlank { null }
        private fun Int.toNullableId(): Short? = if (this < 0) null else toShort()
    }
}

data class NodeFeatureValue(
    val word: String? = null,
    val leftId: Int? = null,
    val rightId: Int? = null,
)
