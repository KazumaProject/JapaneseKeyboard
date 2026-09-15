package com.kazumaproject.markdownhelperkeyboard.converter.engine

/** Matches complete counter readings at lattice positions; the dictionary handles surrounding words. */
internal class NumberGraphMatcher(
    private val input: String,
    private val config: PredictionConfig,
) {
    private companion object {
        // One complete number + counter token, rather than two independently scored words.
        const val COUNTER_WORD_COST = 2500
        val durationCounters = setOf(BuiltInCounter.DAY, BuiltInCounter.YEAR, BuiltInCounter.MONTHS, BuiltInCounter.NIGHTS)
    }

    data class Form(val text: String, val leftId: Short, val rightId: Short, val cost: Int)
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
                    config.numberCandidateOrder.indices.mapIndexedNotNull { rank, index ->
                        val text = proof.basicForms[index]
                        if (!config.numberCandidateConfig.permits(proof, text)) null
                        else {
                            // Context IDs from assets/id.def. A duration can modify a verb;
                            // treating 時間 as an ordinary counter incorrectly prefers 時間末.
                            val right: Short = when {
                                proof.customUnit != null -> 2011
                                proof.builtInCounter == BuiltInCounter.HOURS -> 1916
                                proof.builtInCounter in durationCounters || proof.counter == "分" -> 1909
                                proof.builtInCounter == BuiltInCounter.THINGS -> 2012
                                proof.builtInCounter == BuiltInCounter.TIMES -> 2014
                                proof.builtInCounter == BuiltInCounter.MONTH -> 2016
                                proof.builtInCounter == BuiltInCounter.FLOORS -> 2018
                                proof.counter == "時" -> 2015
                                proof.counter == "人" -> 1851
                                else -> 2011
                            }
                            Form(text, if (proof.counter == "人" && proof.customUnit == null) 1851 else 2044,
                                right, COUNTER_WORD_COST + rank)
                        }
                    }
                }.distinctBy { Triple(it.text, it.leftId, it.rightId) }
                if (forms.isNotEmpty()) add(Match(end, reading, forms))
            }
        }
    }
}
