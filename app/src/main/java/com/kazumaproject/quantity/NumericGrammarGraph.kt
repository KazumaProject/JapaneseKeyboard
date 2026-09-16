package com.kazumaproject.quantity

/** Typed arcs over the accepted cardinal, shared by calibration and the IME. */
class NumericGrammarGraph(reading: String, val expression: CardinalGrammar.Expression, model: QuantityScoringModel) {
    data class Arc(val start: Int, val end: Int, val lexeme: QuantityScoringModel.Lexeme,
                   val joining: CardinalGrammar.Features)
    val size: Int = expression.atoms.size
    val starts: List<List<Arc>>

    init {
        val atoms = expression.atoms
        fun features(from: Int, until: Int): CardinalGrammar.Features {
            var products = 0; var largeProducts = 0; var additions = 0
            for (at in from until until) {
                val role = atoms[at].role
                val previous = if (at > from) atoms[at - 1].role else null
                if (role == CardinalGrammar.Role.LARGE_MAGNITUDE) { if (previous != null) largeProducts++ }
                else if (role == CardinalGrammar.Role.SMALL_MAGNITUDE && previous == CardinalGrammar.Role.DIGIT) products++
                else if (previous == CardinalGrammar.Role.SMALL_MAGNITUDE || previous == CardinalGrammar.Role.LARGE_MAGNITUDE) additions++
            }
            return CardinalGrammar.Features(products, largeProducts, additions)
        }
        val prefixes = List(size + 1) { features(0, it) }
        starts = List(size) { start ->
            buildList {
                for (end in start + 1..this@NumericGrammarGraph.size) {
                    val text = reading.substring(atoms[start].start, atoms[end - 1].end)
                    val value = if (end == start + 1) atoms[start].value else CardinalGrammar.parse(text)?.value ?: continue
                    val aliases = if (end == start + 1) listOf(text, atoms[start].lexicalReading).distinct() else listOf(text)
                    val words = aliases.flatMap(model::numbers).filter { it.value == value }.distinct()
                    // A complete dictionary lexeme already includes its internal
                    // composition cost. Charge only newly joined grammar edges.
                    val joining = prefixes[end] - prefixes[start] - features(start, end)
                    for (word in words) add(Arc(start, end, word, joining))
                }
            }
        }
    }
}
