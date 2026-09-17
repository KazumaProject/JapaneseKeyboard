package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.LexicalPart
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix

/** Resolves a supplement against actual dictionary entries and their internal connection costs. */
internal class NumberLexicon(
    private val lookup: (String) -> List<Entry>,
    private val matrix: ConnectionMatrix.CostTable,
    private val observer: NumericPathObserver? = null,
    private val cancellationCheck: () -> Unit = {},
) {
    data class Entry(val text: String, val left: Short, val right: Short, val cost: Int,
        val parts: List<LexicalPart> = listOf(LexicalPart(text, left, right)))
    private val numberCache = HashMap<Pair<Long, String?>, List<Entry>>()
    private val scoringModel = QuantityRuntime.scoringModel
    private val compatibility = if (scoringModel == null) LegacyNumberLexicon(lookup, matrix, cancellationCheck) else null
    private val numericIds = QuantityRuntime.dictionary.numericContextIds
    private fun counterEntries(reading: String, output: String,
                               role: com.kazumaproject.quantity.QuantityScoringModel.UnitRole =
                                   com.kazumaproject.quantity.QuantityScoringModel.UnitRole.COUNTER): List<Entry> {
        scoringModel?.let { model ->
            return model.units(reading).filter { it.text == output && it.role == role }
                .map { Entry(it.text, it.left.toShort(), it.right.toShort(), it.cost) }
        }
        val entries = lookup(reading).filter { it.text == output }
        val counters = entries.filter { it.left.toInt() in QuantityRuntime.dictionary.counterContextIds }
        return counters.ifEmpty { entries }
    }
    private fun customEntries(reading: String, output: String): List<Entry> =
        lookup(reading).filter { it.left.toInt() in QuantityRuntime.dictionary.counterContextIds }
            .let { counters -> counters.filter { it.text == output }.ifEmpty { counters } }
            .ifEmpty { counterEntries("こ", "個") }.map { Entry(output, it.left, it.right, it.cost) }

    fun forms(proof: ValidatedNumber): List<NumberGraphMatcher.Form> {
        compatibility?.let { return it.forms(proof) }
        if (numericIds.isEmpty()) return emptyList()
        if (proof.clock != null) {
            if (scoringModel == null) return emptyList()
            val components = proof.clockParts ?: return emptyList()
            val hours = forms(components.first)
            val minutes = forms(components.second)
            return hours.flatMap { hour -> minutes.map { minute ->
                NumberGraphMatcher.Form(proof.basicForms[0], hour.leftId, minute.rightId,
                    hour.cost + matrix.cost(hour.rightId.toInt(), minute.leftId.toInt()) + minute.cost,
                    hour.parts + minute.parts)
            } }
        }
        // Some atomic counter readings are already numeric lexemes (e.g. とお).
        // The parser supplies the implicit unit; do not charge another spoken counter.
        val nativeForms = if (scoringModel != null && proof.customUnit == null) {
            val coreReading = proof.reading.take(proof.coreInputLength)
            var atomic = scoringModel.numbers(coreReading).filter { it.value == proof.value }
                .map { Entry(it.text, it.left.toShort(), it.right.toShort(), it.cost) } +
                scoringModel.quantities(coreReading).filter { it.value == proof.value && it.unit == proof.baseCounter }
                    .map { Entry(it.text, it.left.toShort(), it.right.toShort(), it.cost) }
            if (atomic.isNotEmpty()) {
                for (suffix in proof.counterSuffixes) atomic = atomic.flatMap { before ->
                    counterEntries(suffix.reading, suffix.output, com.kazumaproject.quantity.QuantityScoringModel.UnitRole.SUFFIX).map { after ->
                        Entry(before.text + after.text, before.left, after.right,
                            before.cost + matrix.cost(before.right.toInt(), after.left.toInt()) + after.cost + scoringModel.suffixJoiningCost,
                            before.parts + after.parts)
                    }
                }
                atomic.map { NumberGraphMatcher.Form(proof.basicForms[0], it.left, it.right, it.cost, it.parts) }
            } else emptyList()
        } else emptyList()
        if (scoringModel != null && proof.counter.isEmpty()) return numbers(proof).map {
            NumberGraphMatcher.Form(proof.basicForms[0], it.left, it.right, it.cost, it.parts)
        }
        val baseReading = proof.builtInCounter?.reading ?: proof.customUnit?.reading ?: when (proof.baseCounter) {
            "円" -> "えん"; "人" -> "にん"; "分" -> "ふん"; "時" -> "じ"; else -> null
        } ?: return nativeForms
        val reading = baseReading + proof.counterSuffixes.joinToString("") { it.reading }
        var counters = if (proof.customUnit == null) counterEntries(reading, proof.counter) else emptyList()
        if (counters.isEmpty()) {
            counters = if (proof.customUnit != null) customEntries(baseReading, proof.customUnit.output)
                else counterEntries(baseReading, proof.baseCounter)
            for (suffix in proof.counterSuffixes) {
                counters = counters.flatMap { before ->
                    counterEntries(suffix.reading, suffix.output,
                        com.kazumaproject.quantity.QuantityScoringModel.UnitRole.SUFFIX).map { after ->
                        Entry(before.text + after.text, before.left, after.right,
                            before.cost + matrix.cost(before.right.toInt(), after.left.toInt()) + after.cost + (scoringModel?.suffixJoiningCost ?: 0),
                            before.parts + after.parts)
                    }
                }
            }
        }
        return (nativeForms + numbers(proof).flatMap { number -> counters.map { counter ->
            NumberGraphMatcher.Form(proof.basicForms[0], number.left, counter.right,
                number.cost + matrix.cost(number.right.toInt(), counter.left.toInt()) + counter.cost, number.parts + counter.parts)
        } }).groupBy { Triple(it.leftId, it.rightId, it.parts) }.values.map { entries -> entries.minBy { it.cost } }
    }
    private fun numbers(proof: ValidatedNumber): List<Entry> = numberCache.getOrPut(proof.value to proof.cardinalReading) {
        val value = proof.value
        val acceptedReading = proof.cardinalReading
        val model = scoringModel ?: return@getOrPut emptyList()
        val canonicalReading = com.kazumaproject.quantity.CardinalGrammar.reading(value)
        val acceptedExpression = proof.cardinalExpression
        // Standalone cardinal aliases (e.g. じゅうよ / じゅうく) are validated by
        // ValidatedNumber, but are intentionally not accepted by the strict
        // counter grammar. Score those proofs through their canonical cardinal
        // while keeping counter readings on the strict path. This must remain
        // counterless-only so readings such as じゅうよふん are not broadened.
        val expression = acceptedExpression ?: if (acceptedReading == null || proof.counter.isEmpty())
            com.kazumaproject.quantity.CardinalGrammar.parse(canonicalReading) else null
        if (expression == null || expression.value != value) return@getOrPut emptyList()
        val accepted = if (acceptedExpression != null) acceptedReading!! else canonicalReading
        val grammar = com.kazumaproject.quantity.NumericGrammarGraph(accepted, expression, model)
        val numeric = NumericLattice(observer)
        val rows = Array(grammar.size + 1) { numeric.row() }
        for (start in 0 until grammar.size) {
            cancellationCheck()
            for (arc in grammar.starts[start]) {
                val word = arc.lexeme
                val entry = Entry(word.text, word.left.toShort(), word.right.toShort(), word.cost)
                val adjustment = model.constructionCost(arc.joining)
                if (start == 0) numeric.add(rows[arc.end], null, entry, adjustment)
                else for (before in numeric.vertices(rows[start])) numeric.add(rows[arc.end], before, entry,
                    matrix.cost(before.right.toInt(), word.left) + adjustment)
            }
        }
        return@getOrPut numeric.vertices(rows.last()).map { path ->
            Entry(value.toString(), path.left, path.right, path.cost, path.materialize())
        }
    }
}
