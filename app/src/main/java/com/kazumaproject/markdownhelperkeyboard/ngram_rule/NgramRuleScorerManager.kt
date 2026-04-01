package com.kazumaproject.markdownhelperkeyboard.ngram_rule

import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.ThreeNodeRule
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.TwoNodeRule
import com.kazumaproject.markdownhelperkeyboard.repository.NgramRuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NgramRuleScorerManager @Inject constructor(
    private val repository: NgramRuleRepository,
) {
    private val scorerRef = AtomicReference(NgramRuleScorer.createDefault())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            repository.observeDomainRules().collectLatest { (twoRules, threeRules) ->
                setScorer(twoRules, threeRules)
            }
        }
    }

    fun currentScorer(): NgramRuleScorer = scorerRef.get()

    suspend fun refreshNow() {
        val (twoRules, threeRules) = repository.loadDomainRules()
        setScorer(twoRules, threeRules)
    }

    private fun setScorer(twoRules: List<TwoNodeRule>, threeRules: List<ThreeNodeRule>) {
        scorerRef.set(
            NgramRuleScorer(
                twoNodeRules = NgramRuleScorer.defaultTwoNodeRules() + twoRules,
                threeNodeRules = NgramRuleScorer.defaultThreeNodeRules() + threeRules,
            )
        )
    }
}

