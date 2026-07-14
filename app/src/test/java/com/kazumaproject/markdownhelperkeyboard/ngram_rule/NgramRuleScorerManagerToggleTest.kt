package com.kazumaproject.markdownhelperkeyboard.ngram_rule

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.repository.NgramRuleRepository
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NgramRuleScorerManagerToggleTest {
    @Test
    fun disablingRemovesLegacyScoreAndReenableRestoresIt() {
        val repository = mock<NgramRuleRepository>()
        whenever(repository.observeDomainRules()).thenReturn(flowOf(emptyList()))
        val manager = NgramRuleScorerManager(repository)
        val noun = node("机", 1851)
        val particle = node("で")
        val verb = node("拭く")

        assertEquals(-2000, manager.currentScorer().score(noun, particle, verb))
        manager.setEnabled(false)
        assertEquals(0, manager.currentScorer().score(noun, particle, verb))
        manager.setEnabled(true)
        assertEquals(-2000, manager.currentScorer().score(noun, particle, verb))
    }

    private fun node(word: String, id: Int = 1) = Node(
        l = id.toShort(), r = id.toShort(), score = 0, f = 0,
        tango = word, len = 1, yomiUsed = word, sPos = 0,
    )
}
