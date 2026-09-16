package com.kazumaproject.markdownhelperkeyboard.converter.engine

/** Matches complete counter readings at lattice positions; the dictionary handles surrounding words. */
internal class NumberGraphMatcher(
    private val input: String,
    private val config: PredictionConfig,
    private val resolve: ((ValidatedNumber) -> List<Form>)? = null,
) {
    data class Form(val text: String, val leftId: Short, val rightId: Short, val cost: Int, val parts: List<com.kazumaproject.graph.LexicalPart> = emptyList())
    data class Match(val end: Int, val reading: String, val forms: List<Form>)

    private val units = config.numberCandidateConfig.compiledUnits
    private val enabled = config.japaneseNumberCandidatesEnabled && input.length <= UByte.MAX_VALUE.toInt()
    private val readingRunEnds = IntArray(input.length)
    init {
        var end = input.length
        for (index in input.indices.reversed()) {
            if (input[index] !in 'ぁ'..'ゖ' && input[index] != 'ー') end = index
            readingRunEnds[index] = end
        }
    }
    private val possibleEnds = if (enabled) input.indices.filter { index ->
        val char = input[index]
        char in "んじり" || BuiltInCounter.canEndWith(char) || char in units.endCharacters
    }.map { it + 1 } else emptyList()

    fun matches(start: Int, minimumEnd: Int = 0): List<Match> {
        if (!enabled || input[start] !in "いにさしよごろなはきじひせぜれふ" &&
            !BuiltInCounter.canStartWith(input[start]) &&
            input[start] !in units.startCharacters) return emptyList()
        return buildList {
            for (end in possibleEnds) {
                if (end > readingRunEnds[start]) break
                if (end <= start || end < minimumEnd) continue
                val reading = input.substring(start, end)
                // Do not disturb the whole-input parse cache used by generation and ordering.
                val proofs = ValidatedNumber.parseUncached(reading, config.numberCandidateConfig)
                    .filter { it.counter.isNotEmpty() }
                val forms = proofs.flatMap { proof ->
                    if (proof.basicForms.none { config.numberCandidateConfig.permits(proof, it) }) emptyList()
                    else resolve?.invoke(proof) ?: listOf(Form(proof.basicForms[0], 0, 0, 0))
                }.distinctBy { Triple(it.text, it.leftId, it.rightId) }
                if (forms.isNotEmpty()) add(Match(end, reading, forms))
            }
        }
    }
}
