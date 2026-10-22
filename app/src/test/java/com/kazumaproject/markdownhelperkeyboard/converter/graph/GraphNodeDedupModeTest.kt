package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.graph.Node
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphNodeDedupModeTest {

    @Test
    fun existingModeReplacesSameTangoLeftRightWithLowerCost() {
        val graph = mutableMapOf<Int, MutableList<Node>>()
        val builder = GraphBuilder()

        invokeAddOrUpdate(builder, graph, node(score = 1000), GraphNodeDedupMode.EXISTING_BY_TANGO_L_R)
        invokeAddOrUpdate(builder, graph, node(score = 500), GraphNodeDedupMode.EXISTING_BY_TANGO_L_R)
        invokeAddOrUpdate(builder, graph, node(score = 700), GraphNodeDedupMode.EXISTING_BY_TANGO_L_R)

        assertEquals(1, graph.getValue(1).size)
        assertEquals(500, graph.getValue(1).single().score)
    }

    @Test
    fun mozcModeKeepsSameTangoLeftRightNodes() {
        val graph = mutableMapOf<Int, MutableList<Node>>()
        val builder = GraphBuilder()

        invokeAddOrUpdate(builder, graph, node(score = 1000), GraphNodeDedupMode.MOZC_KEEP_ALL)
        invokeAddOrUpdate(builder, graph, node(score = 500), GraphNodeDedupMode.MOZC_KEEP_ALL)

        assertEquals(2, graph.getValue(1).size)
        assertEquals(listOf(1000, 500), graph.getValue(1).map { it.score })
    }

    @Test
    fun equalClassifiedTokensMergeLabelsInBothModesButDifferentCostsDoNot() {
        GraphNodeDedupMode.entries.forEach { mode ->
            val graph = mutableMapOf<Int, MutableList<Node>>()
            val builder = GraphBuilder()
            invokeAddOrUpdate(builder, graph, node(1000).copy(dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_PERSON_ACTOR)), mode)
            invokeAddOrUpdate(builder, graph, node(1000).copy(dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_PLACE)), mode)
            assertEquals(setOf(DictionaryCategory.CLASSIFIED_PERSON_ACTOR, DictionaryCategory.CLASSIFIED_PLACE), graph.getValue(1).single().dictionaryCategories)
            invokeAddOrUpdate(builder, graph, node(500).copy(dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_CHARACTER)), mode)
            if (mode == GraphNodeDedupMode.EXISTING_BY_TANGO_L_R) {
                assertEquals(setOf(DictionaryCategory.CLASSIFIED_CHARACTER), graph.getValue(1).single().dictionaryCategories)
            } else {
                assertEquals(2, graph.getValue(1).size)
            }
        }
    }

    @Test fun personalWordInheritsLabelsWithoutChangingAnyOtherNodeFields() {
        for (source in listOf(CandidateSource.LEARNED_DICTIONARY, CandidateSource.USER_DICTIONARY)) {
            val personal = node(500).copy(candidateSource = source, l = 3, r = 4)
            val original = personal.copy()
            val category = DictionaryCategory.CLASSIFIED_KAMPO
            attachLabels(listOf(personal), node(1000).copy(dictionaryCategories = setOf(category)))
            assertEquals(original.copy(dictionaryCategories = setOf(category)), personal)
        }
    }

    @Test fun personalLabelsUnionOnlyForIdenticalOutputReadingAndInputSpan() {
        val original = node(500).copy(candidateSource = CandidateSource.LEARNED_DICTIONARY)
        val different = listOf(original.copy(tango = "別"), original.copy(yomiUsed = "べつ"),
            original.copy(sPos = 1), original.copy(len = 2))
        val nodes = listOf(original) + different
        val category = DictionaryCategory.CLASSIFIED_KAMPO
        attachLabels(nodes, node(1000).copy(dictionaryCategories = setOf(category)))
        assertEquals(setOf(category), original.dictionaryCategories)
        assertEquals(List(4) { emptySet<DictionaryCategory>() }, different.map { it.dictionaryCategories })
        attachLabels(nodes, node(2000).copy(dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_MEDICAL)))
        assertEquals(setOf(category, DictionaryCategory.CLASSIFIED_MEDICAL), original.dictionaryCategories)
    }

    @Test fun classifiedAnnotationDoesNotChangeDedupWinnerOrNodeCountInEitherMode() {
        for (mode in GraphNodeDedupMode.entries) {
            val graph = mutableMapOf<Int, MutableList<Node>>()
            val builder = GraphBuilder()
            val personal = node(500).copy(candidateSource = CandidateSource.LEARNED_DICTIONARY)
            val dictionary = node(1000).copy(dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_KAMPO))
            invokeAddOrUpdate(builder, graph, personal, mode)
            attachLabels(listOf(personal), dictionary)
            invokeAddOrUpdate(builder, graph, dictionary, mode)
            assertEquals(500, graph.getValue(1).first().score)
            assertEquals(CandidateSource.LEARNED_DICTIONARY, graph.getValue(1).first().candidateSource)
            assertEquals(setOf(DictionaryCategory.CLASSIFIED_KAMPO), graph.getValue(1).first().dictionaryCategories)
            assertEquals(if (mode == GraphNodeDedupMode.MOZC_KEEP_ALL) 2 else 1, graph.getValue(1).size)
        }
    }

    private fun attachLabels(personal: List<Node>, dictionary: Node) {
        GraphBuilder::class.java.getDeclaredMethod("attachClassifiedLabelsToPersonalNodes", List::class.java,
            Node::class.java).apply { isAccessible = true }.invoke(GraphBuilder(), personal, dictionary)
    }

    private fun invokeAddOrUpdate(
        builder: GraphBuilder,
        graph: MutableMap<Int, MutableList<Node>>,
        node: Node,
        mode: GraphNodeDedupMode,
    ) {
        val method = GraphBuilder::class.java.getDeclaredMethod(
            "addOrUpdateNode",
            MutableMap::class.java,
            Int::class.javaPrimitiveType,
            Node::class.java,
            GraphNodeDedupMode::class.java,
            MutableList::class.java,
            String::class.java,
            String::class.java,
        )
        method.isAccessible = true
        method.invoke(builder, graph, 1, node, mode, null, "よみ", "TEST")
    }

    private fun node(score: Int): Node =
        Node(
            l = 1.toShort(),
            r = 2.toShort(),
            score = score,
            f = score,
            tango = "語",
            len = 1.toShort(),
            yomiUsed = "よ",
            sPos = 0,
        )
}
