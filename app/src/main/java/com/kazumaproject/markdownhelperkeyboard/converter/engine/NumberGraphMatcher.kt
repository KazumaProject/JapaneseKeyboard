package com.kazumaproject.markdownhelperkeyboard.converter.engine

/** Matches complete counter readings at lattice positions; the dictionary handles surrounding words. */
internal class NumberGraphMatcher(
    private val input: String,
    private val config: PredictionConfig,
    private val parseReading: (String) -> List<ValidatedNumber> = { ValidatedNumber.parseUncached(it, config.numberCandidateConfig) },
    private val recognized: ((Int) -> List<Match>)? = null,
    private val resolve: ((ValidatedNumber) -> List<Form>)? = null,
) {
    data class Form(val text: String, val leftId: Short, val rightId: Short, val cost: Int, val parts: List<com.kazumaproject.graph.LexicalPart> = emptyList())
    data class Match(val end: Int, val reading: String, val forms: List<Form>)

    private val units = config.numberCandidateConfig.compiledUnits
    private val suffixes = QuantityRuntime.dictionary.suffixes
    private val suffixEndCharacters = suffixes.mapNotNull { it.reading.lastOrNull() }.toSet()
    private val enabled = config.japaneseNumberCandidatesEnabled && input.length <= UByte.MAX_VALUE.toInt()
    private val readingRunEnds = IntArray(input.length)
    init {
        var end = input.length
        for (index in input.indices.reversed()) {
            if (input[index] !in 'ぁ'..'ゖ' && input[index] != 'ー') end = index
            readingRunEnds[index] = end
        }
    }
    private val maximumEnds: IntArray by lazy {
        // After the first character outside the cardinal alphabet, only a
        // configured unit ending and a valid suffix chain can remain. Derive
        // this bound from the grammar, including custom exact/composed rules.
        val lengths = HashMap<String, Int>()
        val visiting = HashSet<String>()
        fun extension(unit: String): Int {
            lengths[unit]?.let { return it }
            if (!visiting.add(unit)) return 255 // Conservative for malformed direct-constructor data.
            val length = suffixes.filter { it.base == unit }.maxOfOrNull {
                it.reading.length + extension(unit + it.output)
            } ?: 0
            visiting.remove(unit)
            return length.coerceAtMost(255).also { lengths[unit] = it }
        }
        val ordinarySuffix = suffixes.maxOfOrNull { extension(it.base) } ?: 0
        val customSuffix = units.outputs.maxOfOrNull { unit ->
            suffixes.filter { it.base == "@registered" }.maxOfOrNull {
                it.reading.length + extension(unit + it.output)
            } ?: 0
        } ?: 0
        val tail = maxOf(BuiltInCounter.maximumNonCardinalTail, units.maximumNonCardinalTail, 3) +
            maxOf(ordinarySuffix, customSuffix)
        var numericEnd = input.length
        IntArray(input.length).also { ends ->
            for (index in input.indices.reversed()) {
                if (input[index] !in ValidatedNumber.cardinalCharacters) numericEnd = index
                ends[index] = minOf(readingRunEnds[index], numericEnd + tail)
            }
        }
    }

    private val possibleEnds = if (enabled) input.indices.filter { index ->
        val char = input[index]
        char in "んじり" || BuiltInCounter.canEndWith(char) || char in units.endCharacters || char in suffixEndCharacters
    }.map { it + 1 } else emptyList()

    fun matches(start: Int, minimumEnd: Int = 0): List<Match> {
        if (!enabled || input[start] !in "いにさしよごろなはきじひせぜれふ" &&
            !BuiltInCounter.canStartWith(input[start]) &&
            input[start] !in units.startCharacters) return emptyList()
        return buildList {
            val known = recognized?.invoke(start)
            val ends = known?.map { it.end } ?: possibleEnds
            for ((index, end) in ends.withIndex()) {
                if (end > (if (known == null) maximumEnds[start] else readingRunEnds[start])) break
                if (end <= start || end < minimumEnd) continue
                val reading = known?.get(index)?.reading ?: input.substring(start, end)
                // Share reading proofs with the lattice policy for this input.
                val proofs = parseReading(reading)
                    .filter { it.counter.isNotEmpty() }
                val forms = proofs.flatMap { proof ->
                    if (proof.basicForms.none { config.numberCandidateConfig.permits(proof, it) }) emptyList()
                    else resolve?.invoke(proof) ?: listOf(Form(proof.basicForms[0], 0, 0, 0))
                }.groupBy { listOf(it.text, it.leftId, it.rightId, it.parts) }.values.map { variants -> variants.minBy { it.cost } }
                if (forms.isNotEmpty()) add(Match(end, reading, forms))
            }
        }
    }
}
