package com.kazumaproject.markdownhelperkeyboard.ngram_rule

import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRule
import com.kazumaproject.markdownhelperkeyboard.repository.NgramRuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NgramRuleScorerManager @Inject constructor(
    private val repository: NgramRuleRepository,
) {
    private val scorerRef = AtomicReference(NgramRuleScorer.createDefault())
    private val enabled = AtomicBoolean(true)
    private val emptyScorer = NgramRuleScorer(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            repository.observeDomainRules().collectLatest { rules ->
                setScorer(rules)
            }
        }
    }

    fun currentScorer(): NgramRuleScorer = if (enabled.get()) scorerRef.get() else emptyScorer

    fun setEnabled(value: Boolean) {
        enabled.set(value)
    }

    fun isEnabled(): Boolean = enabled.get()

    suspend fun refreshNow() {
        setScorer(repository.loadDomainRules())
    }

    private fun setScorer(rules: List<NgramRule>) {
        scorerRef.set(
            NgramRuleScorer(NgramRuleScorer.defaultRules() + rules)
        )
    }
}
