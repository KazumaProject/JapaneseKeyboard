package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.*
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.*
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramRuntime
import com.kazumaproject.quantity.QuantityDictionary
import org.junit.Assert.*
import org.junit.Test

class QuantitySearchTest {
    private fun typedLexicon(lookup: (String) -> List<NumberLexicon.Entry>, matrix: ConnectionMatrix.CostTable,
                             observer: NumericPathObserver? = null): NumberLexicon {
        val old = QuantityRuntime.scoringModel
        val readings = listOf("いち", "に", "さん", "ご", "じゅう", "ひゃく", "さんびゃく", "ごじゅう", "まん", "おく", "ちょう",
            "こ", "にん", "えん", "ぶん", "きろ", "たんい")
        val entries = readings.flatMap { reading -> lookup(reading).map { reading to it } }
        val numbers = entries.mapNotNull { (reading, word) ->
            val value = com.kazumaproject.quantity.CardinalGrammar.surfaceValue(word.text)
            if (word.left.toInt() !in QuantityRuntime.dictionary.numericContextIds || word.right.toInt() !in QuantityRuntime.dictionary.numericContextIds || value == null) null
            else com.kazumaproject.quantity.QuantityScoringModel.Lexeme(reading, word.text, value, word.left.toInt(), word.right.toInt(), word.cost)
        }
        val units = entries.filter { it.second.left.toInt() in QuantityRuntime.dictionary.counterContextIds }.flatMap { (reading, word) ->
            com.kazumaproject.quantity.QuantityScoringModel.UnitRole.entries.map { role ->
                com.kazumaproject.quantity.QuantityScoringModel.UnitLexeme(reading, word.text, role, word.left.toInt(), word.right.toInt(), word.cost)
            }
        }
        val source = com.kazumaproject.quantity.QuantityScoringModel("a".repeat(64), "b".repeat(64), numbers, units,
            intArrayOf(0, 0, 0), emptyMap(), emptyList(), emptyList())
        return try {
            QuantityRuntime.installScoringModel(source, source.posFingerprint, source.connectionFingerprint)
            NumberLexicon(lookup, matrix, observer)
        } finally { QuantityRuntime.installScoringModel(old, old?.posFingerprint.orEmpty(), old?.connectionFingerprint.orEmpty()) }
    }

    private fun node(text: String, reading: String = text, start: Int = 0, cost: Int = 0) =
        Node(1, 1, cost, cost, tango = text, len = reading.length.toShort(), yomiUsed = reading, sPos = start)

    @Test fun factoredSearchMatchesExhaustivePathsWithOverlappingRules() {
        val old = QuantityRuntime.dictionary
        try {
            val rules = listOf(
                listOf(QuantityDictionary.Feature.Quantity("本"), QuantityDictionary.Feature.Word("買う")),
                listOf(QuantityDictionary.Feature.Word("前"), QuantityDictionary.Feature.Quantity("本")))
            QuantityRuntime.install(QuantityDictionary(rules, emptyList(), setOf(1), setOf(1)))
            SystemNgramRuntime.resetForTesting()
            val random = java.util.Random(7129)
            val workspace = QuantitySearchWorkspace()
            repeat(24) { round ->
                val bos = node("BOS", "", 0).copy(l = 0, r = 0, mozcNodeType = MozcNodeType.BOS)
                val eos = node("EOS", "", 8).copy(l = 0, r = 0, mozcNodeType = MozcNodeType.EOS)
                val prefix = listOf("前", "外").mapIndexed { index, text ->
                    node(text, "あ", 0, random.nextInt(500)).copy(candidateSource =
                        if (index == 0) CandidateSource.SYSTEM else CandidateSource.UNKNOWN)
                }
                val macro = node("3本", "さんぼん", 1, random.nextInt(500)).copy(isGeneratedNumber = true,
                    lexicalParts = listOf(LexicalPart("三", 1, 1), LexicalPart("本", 1, 1)))
                val number = node("三", "さん", 1, random.nextInt(500))
                val counter = node("本", "ぼん", 3, random.nextInt(500))
                val verbs = listOf(node("買う", "かう", 5, random.nextInt(500)), node("飼う", "かう", 5, random.nextInt(500)))
                val tail = listOf(node("上", "う", 7, random.nextInt(500)), node("宇", "う", 7, random.nextInt(500)))
                val all = prefix + listOf(macro, number, counter) + verbs + tail
                val graph = all.groupBy { it.sPos + it.len } + mapOf(0 to listOf(bos), 9 to listOf(eos))
                val matrix = ConnectionMatrix.fromShortArray(ShortArray(4) { (random.nextInt(40) - 20).toShort() }, 2)
                val scorer = NgramRuleScorer(listOf(
                    NgramRule(listOf("前", "三", "本", "買う").map { NodeFeature(word = it) }, -700),
                    NgramRule(listOf(NodeFeature(rightId = 1), NodeFeature(word = "本")), -31),
                    NgramRule(listOf("本", "買う", "上").map { NodeFeature(word = it) }, 89)))
                val legal: (Node, Node) -> Boolean = { a, b -> !(round % 2 == 0 && a.tango == "外" && b === macro) }
                val paths = mutableListOf<List<Node>>()
                fun enumerate(path: List<Node>, at: Int) {
                    if (at == 8) { paths += path; return }
                    for (next in all.filter { it.sPos == at }) if (legal(path.lastOrNull() ?: bos, next))
                        enumerate(path + next, next.sPos + next.len)
                }
                enumerate(emptyList(), 0)
                val expected = rules.mapNotNull { rule ->
                    val dictionary = QuantityDictionary(listOf(rule), emptyList())
                    paths.filter { path -> dictionary.matches(path.map { QuantityDictionary.Token(it.tango, it.yomiUsed) }) { reading, text, unit ->
                        reading == "さんぼん" && text in listOf("3本", "三本") && unit == "本"
                    } }.map { path ->
                        val cost = path.indices.sumOf { n -> path[n].adjustedScore +
                            matrix.cost((path.getOrNull(n - 1) ?: bos).r.toInt(), path[n].l.toInt()) + scorer.scoreEndingAt(path.take(n + 1)) } +
                            matrix.cost(path.last().r.toInt(), eos.l.toInt()) + if (path.any { it.tango.any(Char::isDigit) }) 2000 else 0
                        cost to path.joinToString("") { it.tango }
                    }.minByOrNull { it.first }
                }
                val actual = QuantityGuidedSearch(graph, NumberPathPolicy("あさんぼんかうう", PredictionConfig()), matrix,
                    legal, scorer, workspace = workspace).candidates(null).map { it.score to it.string }
                assertEquals("round $round", expected, actual)
                val shared = QuantityGuidedSearch(graph, NumberPathPolicy("あさんぼんかうう", PredictionConfig()), matrix,
                    legal, scorer, workspace = workspace, boundaryClass = { if (round % 2 == 0) it.tango else 0 }).candidates(null).map { it.score to it.string }
                assertEquals("shared round $round", expected, shared)
            }
        } finally { QuantityRuntime.install(old); SystemNgramRuntime.resetForTesting() }
    }

    @Test fun cancelledSearchWorkspaceCanBeReusedForAnotherInput() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(listOf(listOf(
                QuantityDictionary.Feature.Quantity("本"), QuantityDictionary.Feature.Word("買う"))), emptyList()))
            val workspace = QuantitySearchWorkspace()
            val matrix = ConnectionMatrix.fromShortArray(ShortArray(4), 2)
            val scorer = NgramRuleScorer(emptyList())
            fun search(repeats: Int, cancel: () -> Unit): List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate> {
                val input = "さんぼんかう".repeat(repeats)
                val all = (0 until repeats).flatMap { n -> listOf(
                    node("3本", "さんぼん", n * 6, 100).copy(isGeneratedNumber = true),
                    node("買う", "かう", n * 6 + 4, 20)) }
                val graph = all.groupBy { it.sPos + it.len } + mapOf(
                    0 to listOf(node("BOS", "").copy(mozcNodeType = MozcNodeType.BOS)),
                    input.length + 1 to listOf(node("EOS", "", input.length).copy(mozcNodeType = MozcNodeType.EOS)))
                return QuantityGuidedSearch(graph, NumberPathPolicy(input, PredictionConfig()), matrix,
                    { _, _ -> true }, scorer, cancel, workspace).candidates(null)
            }
            var checks = 0
            try {
                search(20) { if (++checks == 1) throw java.util.concurrent.CancellationException() }
                fail("search must propagate cancellation")
            } catch (_: java.util.concurrent.CancellationException) { }
            val candidates = search(1) { }
            assertEquals("3本買う", candidates.single().string)
            assertEquals(2120, candidates.single().score)
        } finally { QuantityRuntime.install(old) }
    }

    @Test fun primitiveStateIndexSurvivesGrowthAndReuse() {
        val index = QuantityLongIndex()
        repeat(3) {
            for (n in 0 until 4096) index[(n.toLong() shl 32) or (n % 7).toLong()] = n
            for (n in 0 until 4096) assertEquals(n, index[(n.toLong() shl 32) or (n % 7).toLong()])
            index.clear()
            assertEquals(-1, index[0])
        }
    }

    @Test fun customUnitsDoNotBorrowHomophonousProperNounCosts() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(2)))
            val lookup = mapOf(
                "さん" to listOf(NumberLexicon.Entry("三", 1, 1, 100)),
                "きろ" to listOf(NumberLexicon.Entry("帰路", 3, 3, 1), NumberLexicon.Entry("キロ", 2, 2, 300)),
                "こ" to listOf(NumberLexicon.Entry("個", 2, 2, 400)))
            val matrix = ConnectionMatrix.fromShortArray(ShortArray(16), 4)
            val config = NumberCandidateConfig(units = listOf(CustomNumberUnit("kg", "kg", "きろ")))
            val proof = ValidatedNumber.parseUncached("さんきろ", config).first { it.customUnit != null }
            val forms = typedLexicon({ lookup[it].orEmpty() }, matrix).forms(proof)
            assertEquals(400, forms.single().cost)
            assertEquals(2.toShort(), forms.single().rightId)
            val withoutCounter = typedLexicon({ lookup[it].orEmpty().filter { it.text != "キロ" } }, matrix).forms(proof)
            assertEquals(500, withoutCounter.single().cost)
        } finally { QuantityRuntime.install(old) }
    }

    @Test fun customUnitLabelsAreNotParsedAsCompoundSuffixes() {
        val old = QuantityRuntime.dictionary
        try {
            val suffixes = listOf(QuantityDictionary.Suffix("人", "ぶん", "分"),
                QuantityDictionary.Suffix("@registered", "ぶん", "分"))
            QuantityRuntime.install(QuantityDictionary(emptyList(), suffixes, setOf(1), setOf(2)))
            val lookup = mapOf(
                "じゅう" to listOf(NumberLexicon.Entry("十", 1, 1, 100)),
                "に" to listOf(NumberLexicon.Entry("二", 1, 1, 100)),
                "こ" to listOf(NumberLexicon.Entry("個", 2, 2, 100)),
                "ぶん" to listOf(NumberLexicon.Entry("分", 2, 2, 100)))
            val unit = CustomNumberUnit("portion", "人分", "たんい")
            val config = NumberCandidateConfig(units = listOf(unit))
            val lexicon = typedLexicon({ lookup[it].orEmpty() }, ConnectionMatrix.fromShortArray(ShortArray(9), 3))
            val base = ValidatedNumber.parseUncached("じゅうにたんい", config).single { it.customUnit != null }
            assertEquals(emptyList<QuantityDictionary.Suffix>(), base.counterSuffixes)
            assertEquals(listOf("十", "二", "人分"), lexicon.forms(base).single().parts.map { it.text })
            val compound = ValidatedNumber.parseUncached("じゅうにたんいぶん", config).single { it.customUnit != null }
            assertEquals("人分", compound.baseCounter)
            assertEquals(listOf(suffixes[1]), compound.counterSuffixes)
            assertEquals(listOf("十", "二", "人分", "分"), lexicon.forms(compound).single().parts.map { it.text })
        } finally { QuantityRuntime.install(old) }
    }

    @Test fun compoundPeopleRetainsTheBaseCounterLexicon() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(emptyList(),
                listOf(QuantityDictionary.Suffix("人", "ぶん", "分")), setOf(1), setOf(2)))
            val lookup = mapOf(
                "じゅう" to listOf(NumberLexicon.Entry("十", 1, 1, 100)),
                "に" to listOf(NumberLexicon.Entry("二", 1, 1, 100)),
                "にん" to listOf(NumberLexicon.Entry("人", 2, 2, 100)),
                "ぶん" to listOf(NumberLexicon.Entry("分", 2, 2, 100)))
            val proof = ValidatedNumber.parseUncached("じゅうににんぶん", NumberCandidateConfig()).single()
            val forms = typedLexicon({ lookup[it].orEmpty() }, ConnectionMatrix.fromShortArray(ShortArray(9), 3)).forms(proof)
            assertEquals("12人分", forms.single().text)
            assertEquals(listOf("十", "二", "人", "分"), forms.single().parts.map { it.text })
        } finally { QuantityRuntime.install(old) }
    }

    @Test fun lexiconUsesTheAcceptedReadingAndOnlyNumericAllomorphs() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(2)))
            val lookup = mapOf(
                "さんびゃく" to listOf(NumberLexicon.Entry("三百", 3, 3, 1), NumberLexicon.Entry("300", 1, 1, 200)),
                "さん" to listOf(NumberLexicon.Entry("三", 1, 1, 100)),
                "ひゃく" to listOf(NumberLexicon.Entry("百", 1, 1, 200)),
                "えん" to listOf(NumberLexicon.Entry("円", 2, 2, 100)))
            val proof = ValidatedNumber.parse("さんびゃくえん")!!
            assertEquals("さんびゃく", proof.cardinalReading)
            val matrix = ConnectionMatrix.fromShortArray(ShortArray(16), 4)
            assertEquals(listOf("300", "円"), typedLexicon({ lookup[it].orEmpty() }, matrix).forms(proof).minBy { it.cost }.parts.map { it.text })
            val withoutWhole = typedLexicon({ lookup[it].orEmpty().filter { e -> e.text != "300" } }, matrix)
            assertEquals(listOf("三", "百", "円"), withoutWhole.forms(proof).single().parts.map { it.text })
        } finally { QuantityRuntime.install(old) }
    }

    @Test fun acceptedCardinalSurvivesCounterContractionsAndSuffixes() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(emptyList(), listOf(QuantityDictionary.Suffix("本", "ぶん", "分"))))
            val config = NumberCandidateConfig()
            val examples = mapOf("さんびゃくえん" to "さんびゃく", "ろっぴゃっぷん" to "ろっぴゃく",
                "にじゅうさんぼんぶん" to "にじゅうさん", "じゅうよじ" to "じゅうよん",
                "じゅうよにん" to "じゅうよん", "にじゅうくにち" to "にじゅうきゅう")
            for ((input, expected) in examples) {
                assertEquals(input, expected, ValidatedNumber.parseUncached(input, config).first().cardinalReading)
            }
            assertNull(ValidatedNumber.parseUncached("ふつか", config).single().cardinalReading)
        } finally { QuantityRuntime.install(old) }
    }

    @Test fun numericLexiconRequiresSurfaceValueAndBothNumericContexts() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(2)))
            val lookup = mapOf(
                "ごじゅう" to listOf(NumberLexicon.Entry("五重", 1, 1, 1),
                    NumberLexicon.Entry("五十", 1, 2, 2), NumberLexicon.Entry("60", 1, 1, 3),
                    NumberLexicon.Entry("五十", 1, 1, 400)),
                "えん" to listOf(NumberLexicon.Entry("円", 2, 2, 100)))
            val proof = ValidatedNumber.parse("ごじゅうえん")!!
            val forms = typedLexicon({ lookup[it].orEmpty() }, ConnectionMatrix.fromShortArray(ShortArray(9), 3)).forms(proof)
            assertEquals(500, forms.single().cost)
            assertEquals(listOf("五十", "円"), forms.single().parts.map { it.text })
        } finally { QuantityRuntime.install(old) }
    }

    @Test fun largeMagnitudesRemainMultiplicativeLexemes() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(2)))
            val lookup = mapOf("いち" to listOf(NumberLexicon.Entry("一", 1, 1, 100)),
                "まん" to listOf(NumberLexicon.Entry("万", 1, 1, 100)),
                "おく" to listOf(NumberLexicon.Entry("億", 1, 1, 100)),
                "ちょう" to listOf(NumberLexicon.Entry("兆", 1, 1, 100)),
                "えん" to listOf(NumberLexicon.Entry("円", 2, 2, 100)))
            val lexicon = typedLexicon({ lookup[it].orEmpty() }, ConnectionMatrix.fromShortArray(ShortArray(9), 3))
            for ((input, expected) in mapOf("いちまんえん" to "万", "いちおくえん" to "億", "いっちょうえん" to "兆")) {
                val forms = lexicon.forms(ValidatedNumber.parse(input)!!)
                assertEquals(input, listOf("一", expected, "円"), forms.single().parts.map { it.text })
            }
        } finally { QuantityRuntime.install(old) }
    }

    @Test fun quantityLexemesWithDifferentNgramHistoriesSurviveUntilScoring() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(2)))
            val lookup = mapOf("に" to listOf(NumberLexicon.Entry("2", 1, 1, 0), NumberLexicon.Entry("二", 1, 1, 100)),
                "こ" to listOf(NumberLexicon.Entry("個", 2, 2, 0)))
            val matrix = ConnectionMatrix.fromShortArray(ShortArray(9), 3)
            val scorer = NgramRuleScorer(listOf(NgramRule(listOf("二", "個", "買う").map { NodeFeature(word = it) }, -1000)))
            val lexicon = typedLexicon({ lookup[it].orEmpty() }, matrix,
                NumericPathObserver(scorer, com.kazumaproject.markdownhelperkeyboard.converter.ngram.EmptySystemNgramDictionary))
            val forms = NumberGraphMatcher("にこ", PredictionConfig(), resolve = lexicon::forms).matches(0).single().forms
            assertEquals(2, forms.size)
            val scored = forms.map { form ->
                val path = listOf(node(form.text).copy(lexicalParts = form.parts), node("買う"))
                form.cost + path.indices.sumOf { scorer.scoreEndingAt(path.take(it + 1)) }
            }
            assertEquals(-900, scored.minOrNull())
        } finally { QuantityRuntime.install(old) }
    }

    @Test fun reducedForwardContextPreservesOverlappingAndCompositeScores() {
        val rules = listOf(
            NgramRule(listOf("前", "三", "本", "買う").map { NodeFeature(word = it) }, -101),
            NgramRule(listOf(NodeFeature(rightId = 1), NodeFeature(word = "本")), -7),
            NgramRule(listOf("本", "買う", "後").map { NodeFeature(word = it) }, -23))
        val scorer = NgramRuleScorer(rules)
        val macro = node("3本").copy(lexicalParts = listOf(LexicalPart("三", 1, 1), LexicalPart("本", 1, 1)))
        val path = listOf(node("無関係"), node("前"), macro, node("買う"), node("後"), node("末"))
        var tail = emptyList<Node>(); var actual = 0
        for (node in path) {
            actual += scorer.scoreEndingAt(tail + node)
            tail = scorer.futureContext(tail + node)
        }
        assertEquals(path.indices.sumOf { scorer.scoreEndingAt(path.take(it + 1)) }, actual)
        val literalOnly = NgramRuleScorer(listOf(rules.first()))
        assertTrue(literalOnly.futureContext(listOf(node("無関係"))).isEmpty())
    }

    @Test fun composedNodesPreserveEveryNgramCostInBothDirections() {
        val expanded = listOf("前", "三", "本", "買う", "後").map { node(it) }
        val scorer = NgramRuleScorer((2..5).flatMap { order ->
            expanded.windowed(order).map { NgramRule(it.map { n -> NodeFeature(word = n.tango) }, -order * 10) }
        })
        val macro = node("3本").copy(lexicalParts = listOf(LexicalPart("三", 1, 1), LexicalPart("本", 1, 1)))
        val composed = listOf(expanded[0], macro, expanded[3], expanded[4])
        fun backward(path: List<Node>) = path.indices.sumOf { i ->
            if (i + 1 == path.size) 0 else scorer.score(path[i], path[i + 1], path.getOrNull(i + 2), path.getOrNull(i + 3), path.getOrNull(i + 4))
        }
        fun forward(path: List<Node>) = path.indices.sumOf { scorer.scoreEndingAt(path.take(it + 1)) }
        assertEquals(backward(expanded), backward(composed))
        assertEquals(backward(expanded), forward(composed))
        assertEquals(backward(expanded), forward(expanded))
    }

    @Test fun constrainedSearchUsesNgramScoresAndRealBoundaryEdges() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(listOf(listOf(QuantityDictionary.Feature.Quantity("本"), QuantityDictionary.Feature.Word("買う"))), emptyList()))
            SystemNgramRuntime.resetForTesting()
            val a = node("安", "あ", 0, 10)
            val b = node("適", "あ", 0, 100)
            val number = node("3本", "さんぼん", 1, 300).copy(isGeneratedNumber = true, lexicalParts = listOf(LexicalPart("三", 1, 1), LexicalPart("本", 1, 1)))
            val buy = node("買う", "かう", 5, 50)
            val bos = node("BOS", "", 0).copy(l = 0, r = 0, mozcNodeType = MozcNodeType.BOS)
            val eos = node("EOS", "", 8).copy(l = 0, r = 0, mozcNodeType = MozcNodeType.EOS)
            val graph = mapOf(0 to listOf(bos), 1 to listOf(a, b), 5 to listOf(number), 7 to listOf(buy), 8 to listOf(eos))
            val scorer = NgramRuleScorer(listOf(NgramRule(listOf("適", "三", "本", "買う").map { NodeFeature(word = it) }, -200)))
            val matrix = ConnectionMatrix.fromShortArray(ShortArray(4) { 7 }, 2)
            var bosChecked = false; var eosChecked = false
            val candidates = QuantityGuidedSearch(graph, NumberPathPolicy("あさんぼんかう", PredictionConfig()), matrix,
                { left, right ->
                    if (left === bos) bosChecked = true
                    if (right === eos) eosChecked = true
                    true
                }, scorer).candidates(null)
            assertEquals("適3本買う", candidates.single().string)
            assertEquals(100 + 300 + 50 + 4 * 7 - 200 + 2000, candidates.single().score)
            assertTrue(bosChecked && eosChecked)
            val blocked = QuantityGuidedSearch(graph, NumberPathPolicy("あさんぼんかう", PredictionConfig()), matrix,
                { _, right -> right !== eos }, scorer).candidates(null)
            assertTrue(blocked.isEmpty())
        } finally { QuantityRuntime.install(old); SystemNgramRuntime.resetForTesting() }
    }
}
