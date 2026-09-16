package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer
import com.kazumaproject.quantity.QuantityDictionary.Feature

/** One weighted DAG per input; quantity constraints inspect only matching intervals. */
internal class QuantityGuidedSearch(
    graph: Map<Int, List<Node>>,
    private val policy: NumberPathPolicy,
    private val matrix: ConnectionMatrix.CostTable,
    private val legal: (Node, Node) -> Boolean,
    private val scorer: NgramRuleScorer,
    private val cancellationCheck: () -> Unit = {},
    private val workspace: QuantitySearchWorkspace = QuantitySearchWorkspace(),
    // Supplied only when the caller can prove which node properties its
    // boundary predicate observes. Arbitrary predicates keep distinct nodes.
    private val boundaryClass: ((Node) -> Any)? = null,
    private val systemDictionary: com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramDictionary =
        com.kazumaproject.markdownhelperkeyboard.converter.ngram.EmptySystemNgramDictionary,
    private val onCandidatePath: ((String, List<Node>) -> Unit)? = null,
) {
    private companion object { val EMPTY_PHASES = IntArray(0) }

    private val machine = QuantityScoreMachine(scorer, systemDictionary)
    private val weighted = policy.usesWeightedScoring

    private data class Target(val end: Int, val text: String)
    private data class Progress(val feature: Int, val target: Target? = null, val offset: Int = 0)
    private data class ObservableNode(val start: Int, val length: Short, val left: Short, val right: Short,
        val attributes: Int, val type: MozcNodeType, val source: CandidateSource, val generated: Boolean,
        val wordClass: Int, val leftClass: Int, val rightClass: Int, val digits: Boolean, val reading: String, val text: String, val boundary: Any, val system: Any)
    private val allNodes = graph.values.flatten()
    private val bos = allNodes.first { it.tango == "BOS" }
    private val eos = allNodes.first { it.tango == "EOS" }
    private val length = policy.input.length
    private val nodes: List<Node> = run {
        val literalWords = (if (weighted) emptyList() else QuantityRuntime.rules).flatten().filterIsInstance<Feature.Word>().mapTo(HashSet()) { it.text }
        data class SurfaceRange(val end: Int, val forms: List<String>)
        val covering = arrayOfNulls<MutableList<SurfaceRange>>(length + 1)
        for ((start, end) in if (weighted) emptyList() else policy.recognizedSpans()) {
            val forms = policy.parse(policy.input.substring(start, end)).flatMap { it.basicForms }.distinct()
            val range = SurfaceRange(end, forms)
            for (at in start until end) {
                (covering[at] ?: ArrayList<SurfaceRange>().also { covering[at] = it }).add(range)
            }
        }
        val best = LinkedHashMap<ObservableNode, Node>()
        for ((ordinal, node) in allNodes.withIndex()) {
            if (node.tango == "BOS" || node.tango == "EOS") continue
            // A spelling that cannot occur inside any verified quantity form
            // rejects every quantity transition. Such ordinary homophones need
            // no additional text distinction beyond POS, scoring and literals.
            val quantityPart = covering[node.sPos].orEmpty().any { range ->
                node.sPos + node.len <= range.end && range.forms.any { node.tango in it }
            }
            val key = ObservableNode(node.sPos, node.len, node.l, node.r, node.mozcAttributes, node.mozcNodeType,
                node.candidateSource, node.isGeneratedNumber, scorer.wordClass(node), scorer.leftIdClass(node),
                scorer.rightIdClass(node), node.tango.any(Char::isDigit), node.yomiUsed,
                if (weighted || quantityPart || node.tango in literalWords) node.tango else "", boundaryClass?.invoke(node) ?: ordinal, machine.systemClass(node))
            val previous = best[key]
            if (previous == null || node.adjustedScore < previous.adjustedScore) best[key] = node
        }
        listOf(bos) + best.values
    }
    private val starts = run {
        val counts = IntArray(length + 1)
        for (id in 1 until nodes.size) counts[nodes[id].sPos]++
        val index = Array(length + 1) { IntArray(counts[it]) }
        counts.fill(0)
        for (id in 1 until nodes.size) {
            val at = nodes[id].sPos
            index[at][counts[at]++] = id
        }
        index
    }
    private val symbols = IntArray(nodes.size) { if (it == 0) 0 else machine.symbol(nodes[it]) }
    private val flags = IntArray(nodes.size) {
        if (it == 0) 0 else (when (nodes[it].candidateSource) {
            CandidateSource.USER_DICTIONARY -> 2
            CandidateSource.LEARNED_DICTIONARY -> 4
            // These have identical path-level behavior. Their individual nodes
            // remain distinct for lexical scoring and quantity verification.
            CandidateSource.SYSTEM, CandidateSource.UNKNOWN -> 0
        }) or if (nodes[it].tango.any(Char::isDigit) && (!weighted || !nodes[it].isVerifiedQuantity)) 1 else 0
    }
    private data class OutgoingClass(val end: Int, val right: Short, val boundary: Any)
    private val outgoingClasses = run {
        val ids = HashMap<OutgoingClass, Int>()
        IntArray(nodes.size) { id ->
            val node = nodes[id]
            val key = OutgoingClass(if (id == 0) 0 else node.sPos + node.len, node.r, boundaryClass?.invoke(node) ?: id)
            ids.getOrPut(key) { ids.size }
        }
    }
    private var polls = 0
    private inline fun poll() { if (polls++ and 1023 == 0) cancellationCheck() }
    private fun end(node: Int): Int = if (node == 0) 0 else nodes[node].sPos + nodes[node].len

    // DAG columns: node, scoring state, source/digit flags, prefix cost, prefix predecessor,
    // first edge, edge count, suffix cost, suffix successor, next state at this input position,
    // actual prefix node, actual suffix node. States share boundaries; edges retain lexical identity.
    private fun state(node: Int, context: Int, source: Int): Int {
        val key = (outgoingClasses[node].toLong() shl 32) or (context.toLong() shl 5) or source.toLong()
        val old = workspace.stateIndex[key]
        if (old >= 0) return old
        val id = workspace.states.add(); val rows = workspace.states
        rows[id, 0] = node; rows[id, 1] = context; rows[id, 2] = source
        rows[id, 3] = Int.MAX_VALUE; rows[id, 4] = -1; rows[id, 5] = 0; rows[id, 6] = 0
        rows[id, 7] = Int.MAX_VALUE; rows[id, 8] = -1; rows[id, 10] = -1; rows[id, 11] = -1; rows[id, 12] = -1
        val at = end(node); rows[id, 9] = workspace.stateHeads[at]; workspace.stateHeads[at] = id
        workspace.stateIndex[key] = id
        return id
    }

    private fun buildCosts() {
        workspace.reset(length)
        val rows = workspace.states; val edges = workspace.edges
        val root = state(0, 0, 0); rows[root, 3] = 0
        for (at in 0..length) {
            var current = workspace.stateHeads[at]
            while (current >= 0) {
                poll()
                rows[current, 5] = edges.size
                val before = nodes[rows[current, 0]]
                for (node in starts[end(rows[current, 0])]) {
                    poll()
                    if (!legal(before, nodes[node])) continue
                    val connection = matrix.cost(before.r.toInt(), nodes[node].l.toInt()) + nodes[node].adjustedScore
                    val step = machine.step(rows[current, 1], symbols[node])
                    val next = state(node, machine.next(step), rows[current, 2] or flags[node] or machine.matchedMask(step))
                    val cost = connection + machine.cost(step)
                    val edge = edges.add()
                    edges[edge, 0] = next; edges[edge, 1] = cost; edges[edge, 2] = node
                    val total = rows[current, 3] + cost
                    if (total < rows[next, 3]) {
                        rows[next, 3] = total; rows[next, 4] = current; rows[next, 10] = node
                    }
                }
                rows[current, 6] = edges.size - rows[current, 5]
                current = rows[current, 9]
            }
        }
        for (at in length downTo 0) {
            var current = workspace.stateHeads[at]
            while (current >= 0) {
                poll()
                val node = nodes[rows[current, 0]]
                if (at == length && legal(node, eos)) {
                    rows[current, 12] = if (rows[current, 2] and 8 != 0) 1 else 0
                    rows[current, 7] = matrix.cost(node.r.toInt(), eos.l.toInt()) + if (rows[current, 2] and 1 != 0) 2000 else 0
                } else {
                    val first = rows[current, 5]
                    for (edge in first until first + rows[current, 6]) {
                        poll()
                        val next = edges[edge, 0]
                        if (rows[next, 7] == Int.MAX_VALUE) continue
                        val cost = edges[edge, 1] + rows[next, 7]
                        if (rows[next, 12] > rows[current, 12] || rows[next, 12] == rows[current, 12] && cost < rows[current, 7]) {
                            rows[current, 12] = rows[next, 12]
                            rows[current, 7] = cost; rows[current, 8] = next; rows[current, 11] = edges[edge, 2]
                        }
                    }
                }
                current = rows[current, 9]
            }
        }
    }

    private class Targets(val generic: List<Target>, val contextual: List<Target>)
    private val targetsByStart = arrayOfNulls<Map<String, Targets>>(length + 1)
    private val numericSpans = policy.recognizedSpans().groupBy { it.first }

    private fun targets(start: Int, unit: String, contextual: Boolean): List<Target> {
        val byUnit = targetsByStart[start] ?: run {
            // Index each verified span once. Repeating the parse/filter walk for
            // every unit in every rule allocated mostly empty intermediate lists.
            val generic = HashMap<String, MutableSet<Target>>()
            val all = HashMap<String, MutableSet<Target>>()
            for ((_, end) in numericSpans[start].orEmpty()) {
                val allowed = policy.permitsPreference(start, end, false)
                for (proof in policy.parse(policy.input.substring(start, end))) {
                    fun add(unit: String) {
                        val forms = all.getOrPut(unit) { LinkedHashSet() }
                        val preferred = if (allowed) generic.getOrPut(unit) { LinkedHashSet() } else null
                        for (form in proof.basicForms) {
                            val target = Target(end, form)
                            forms.add(target); preferred?.add(target)
                        }
                    }
                    add(proof.counter)
                    if (proof.customUnit != null) add("@registered")
                }
            }
            all.mapValues { (unit, forms) -> Targets(generic[unit]?.toList().orEmpty(), forms.toList()) }
                .also { targetsByStart[start] = it }
        }
        val targets = byUnit[unit] ?: return emptyList()
        return if (contextual) targets.contextual else targets.generic
    }

    private fun advance(rule: List<Feature>, progress: Progress, node: Node): List<Progress> {
        if (progress.feature == rule.size) return listOf(progress)
        val feature = rule[progress.feature]
        if (feature is Feature.Word) return if (node.tango == feature.text) listOf(Progress(progress.feature + 1)) else emptyList()
        if (node.candidateSource == CandidateSource.USER_DICTIONARY || node.candidateSource == CandidateSource.LEARNED_DICTIONARY) return emptyList()
        if (progress.target == null && !policy.startsQuantity(node)) return emptyList()
        fun next(target: Target): Progress? {
            val end = node.sPos + node.len
            if (end > target.end || !target.text.startsWith(node.tango, progress.offset)) return null
            val offset = progress.offset + node.tango.length
            return if (end == target.end) {
                if (offset == target.text.length) Progress(progress.feature + 1) else null
            } else if (offset < target.text.length) Progress(progress.feature, target, offset) else null
        }
        if (progress.target != null) return next(progress.target)?.let(::listOf) ?: emptyList()
        var result: MutableList<Progress>? = null
        for (target in targets(node.sPos, (feature as Feature.Quantity).unit, rule.first() is Feature.Word)) {
            val value = next(target) ?: continue
            if (result == null) result = ArrayList()
            result.add(value)
        }
        return result ?: emptyList()
    }

    fun candidates(
        segments: MutableMap<String, List<CandidateConversionSegment>>?,
        splitPatterns: MutableMap<String, List<Int>>? = null,
        independent: (Short) -> Boolean = { false },
        requested: Int = 8,
    ): List<Candidate> {
        if (weighted) return weightedCandidates(segments, splitPatterns, independent, requested)
        val words = nodes.mapTo(HashSet()) { it.tango }
        val rules = QuantityRuntime.rules.filter { rule -> rule.all { feature -> when (feature) {
            is Feature.Word -> feature.text in words
            is Feature.Quantity -> numericSpans.keys.any { targets(it, feature.unit, rule.first() is Feature.Word).isNotEmpty() }
        } } }
        if (rules.isEmpty()) return emptyList()
        buildCosts()
        val dag = workspace.states; val edges = workspace.edges; val local = workspace.local
        val results = ArrayList<Candidate>()
        for (rule in rules) {
            cancellationCheck()
            local.clear(); workspace.localIndex.clear(); workspace.localHeads.fill(-1)
            val progress = arrayListOf(Progress(0))
            val progressIds = hashMapOf(progress[0] to 0)
            val advanceIndex = QuantityLongIndex()
            val advances = ArrayList<IntArray>()
            fun admit(state: Int, phase: Int, cost: Int, previous: Int, lexicalNode: Int = -1) {
                val key = (state.toLong() shl 32) or phase.toLong()
                var id = workspace.localIndex[key]
                if (id >= 0 && cost >= local[id, 2]) return
                if (id < 0) {
                    id = local.add(); workspace.localIndex[key] = id
                    local[id, 0] = state; local[id, 1] = phase
                    val at = end(dag[state, 0]); local[id, 4] = workspace.localHeads[at]; workspace.localHeads[at] = id
                }
                local[id, 2] = cost; local[id, 3] = previous; local[id, 5] = lexicalNode
            }
            val startPositions = when (val first = rule.first()) {
                is Feature.Word -> nodes.filter { it.tango == first.text }.map { it.sPos }.distinct()
                is Feature.Quantity -> numericSpans.keys.filter { targets(it, first.unit, false).isNotEmpty() }
            }
            for (at in startPositions) {
                var state = workspace.stateHeads[at]
                while (state >= 0) { admit(state, 0, dag[state, 3], -1); state = dag[state, 9] }
            }
            var best = Int.MAX_VALUE; var bestParent = -1; var bestEnd = -1; var bestEndNode = -1
            for (at in 0 until length) {
                var current = workspace.localHeads[at]
                while (current >= 0) {
                    poll()
                    val state = local[current, 0]; val phase = local[current, 1]
                    val first = dag[state, 5]
                    for (edge in first until first + dag[state, 6]) {
                        poll()
                        val next = edges[edge, 0]; val node = edges[edge, 2]
                        if (dag[next, 7] == Int.MAX_VALUE) continue
                        val key = (phase.toLong() shl 32) or node.toLong()
                        var advanceId = advanceIndex[key]
                        if (advanceId < 0) {
                            val nextProgress = advance(rule, progress[phase], nodes[node])
                            val values = if (nextProgress.isEmpty()) EMPTY_PHASES else IntArray(nextProgress.size) { index ->
                                val value = nextProgress[index]
                                progressIds.getOrPut(value) { progress.add(value); progress.lastIndex }
                            }
                            advanceId = advances.size
                            advances.add(values)
                            advanceIndex[key] = advanceId
                        }
                        val phases = advances[advanceId]
                        if (phases.isEmpty()) continue
                        val cost = local[current, 2] + edges[edge, 1]
                        for (p in phases) {
                            if (progress[p].feature == rule.size) {
                                val total = cost + dag[next, 7]
                                if (total < best) { best = total; bestParent = current; bestEnd = next; bestEndNode = node }
                            } else admit(next, p, cost, current, node)
                        }
                    }
                    current = local[current, 4]
                }
            }
            if (bestEnd < 0) continue
            val middle = ArrayList<Node>(); middle.add(nodes[bestEndNode])
            var cursor = bestParent
            while (local[cursor, 3] >= 0) {
                middle.add(nodes[local[cursor, 5]]); cursor = local[cursor, 3]
            }
            val prefix = ArrayList<Node>(); var state = local[cursor, 0]
            while (dag[state, 4] >= 0) { prefix.add(nodes[dag[state, 10]]); state = dag[state, 4] }
            prefix.reverse(); middle.reverse(); prefix.addAll(middle)
            state = bestEnd
            while (dag[state, 8] >= 0) { prefix.add(nodes[dag[state, 11]]); state = dag[state, 8] }
            val path = prefix
            val strength = policy.matchStrength(path)
            if (strength == 0) continue
            val text = path.joinToString("") { it.tango }
            segments?.set(text, path.map { CandidateConversionSegment(it.sPos, it.sPos + it.len, it.tango) })
            splitPatterns?.set(text, path.drop(1).filter { independent(it.l) }.map { it.sPos })
            val type: Byte = when {
                path.any { it.candidateSource == CandidateSource.LEARNED_DICTIONARY } -> CANDIDATE_TYPE_LEARNED_DICTIONARY
                path.any { it.candidateSource == CandidateSource.USER_DICTIONARY } -> CANDIDATE_TYPE_USER_DICTIONARY
                else -> 1
            }
            results += Candidate(text, type, length.toUByte(), best, yomi = policy.input,
                leftId = path.first().l, rightId = path.last().r, numberSpans = policy.spans(path), quantityPreference = strength)
        }
        return results
    }
    /** Lazy k-best suffix streams. Each edge merges an already sorted successor stream.
     * Equivalent suffixes are shared only at boundaries no recognized quantity can cross. */
    private fun weightedCandidates(segments: MutableMap<String, List<CandidateConversionSegment>>?,
        splitPatterns: MutableMap<String, List<Int>>?, independent: (Short) -> Boolean, requested: Int): List<Candidate> {
        if (requested <= 0) return emptyList()
        buildCosts()
        val rows = workspace.states
        val edges = workspace.edges
        if (rows[0, 7] == Int.MAX_VALUE) return emptyList()
        val safe = BooleanArray(length + 1) { true }
        for ((start, finish) in policy.recognizedSpans()) for (at in start + 1 until finish) safe[at] = false
        class Output(val node: Int, val tail: Output?, val cost: Int, val matched: Int = tail?.matched ?: 0) {
            var key: Int = if (node < 0) 0 else -1

        }
        class Choice(val edge: Int, val rank: Int, val output: Output, val ordinal: Int)
        class Stream {
            val values = ArrayList<Output>()
            val seen = HashSet<Int>()
            var initialized = false
            var pending: Choice? = null
            val queue = java.util.PriorityQueue<Choice> { a, b ->
                val priority = b.output.matched.compareTo(a.output.matched)
                val order = if (priority != 0) priority else a.output.cost.compareTo(b.output.cost)
                if (order != 0) order else a.ordinal.compareTo(b.ordinal)
            }
        }
        val streams = arrayOfNulls<Stream>(rows.size)
        var ordinal = 0
        // Intern characters onto the already shared suffix. Full suffix strings
        // would allocate quadratic bytes as the input grows, even for rank zero.
        val characterKeys = QuantityLongIndex()
        var nextCharacterKey = 1
        fun intern(prefix: String, suffixKey: Int): Int {
            var suffix = suffixKey
            check(suffix >= 0)
            for (at in prefix.indices.reversed()) {
                val identity = (prefix[at].code.toLong() shl 32) or suffix.toLong()
                var id = characterKeys[identity]
                if (id < 0) { id = nextCharacterKey++; characterKeys[identity] = id }
                suffix = id
            }
            return suffix
        }
        fun key(output: Output): Int {
            if (output.key >= 0) return output.key
            val block = ArrayList<Node>()
            var cursor: Output? = output
            while (true) {
                val step = cursor ?: break
                if (step.node < 0 || block.isNotEmpty() && safe[nodes[step.node].sPos]) break
                block.add(nodes[step.node]); cursor = step.tail
            }
            val text = block.joinToString("") { it.tango }
            val prefix = if (safe[block.first().sPos]) policy.key(text, policy.spans(block)) else text
            return intern(prefix, cursor?.key ?: 0).also { output.key = it }
        }
        fun get(state: Int, rank: Int): Output? {
            poll()
            val stream = streams[state] ?: Stream().also { streams[state] = it }
            if (stream.values.isEmpty()) {
                if (rows[state, 7] == Int.MAX_VALUE) return null
                val first = if (end(rows[state, 0]) == length) Output(-1, null, rows[state, 7], rows[state, 12])
                    else Output(rows[state, 11], get(rows[state, 8], 0), rows[state, 7])
                stream.values.add(first)
                stream.seen.add(key(first))
            }
            if (rank < stream.values.size) return stream.values[rank]
            if (!stream.initialized) {
                stream.initialized = true
                if (end(rows[state, 0]) != length) {
                    val first = rows[state, 5]
                    for (edge in first until first + rows[state, 6]) {
                        val next = edges[edge, 0]
                        if (rows[next, 7] == Int.MAX_VALUE) continue
                        val suffix = get(next, 0) ?: continue
                        stream.queue.add(Choice(edge, 0, Output(edges[edge, 2], suffix,
                            edges[edge, 1] + suffix.cost), ordinal++))
                    }
                }
            }
            while (stream.values.size <= rank) {
                poll()
                stream.pending?.let { previous ->
                    stream.pending = null
                    val suffix = get(edges[previous.edge, 0], previous.rank + 1)
                    if (suffix != null) stream.queue.add(Choice(previous.edge, previous.rank + 1,
                        Output(edges[previous.edge, 2], suffix, edges[previous.edge, 1] + suffix.cost), ordinal++))
                }
                val choice = stream.queue.poll() ?: return null
                stream.pending = choice
                if (stream.seen.add(key(choice.output))) stream.values.add(choice.output)
            }
            return stream.values[rank]
        }
        val result = ArrayList<Candidate>()
        for (rank in 0 until requested) {
            val output = get(0, rank) ?: break
            val path = ArrayList<Node>()
            var cursor: Output? = output
            while (true) {
                val step = cursor ?: break
                if (step.node < 0) break
                path.add(nodes[step.node]); cursor = step.tail
            }
            val text = path.joinToString("") { it.tango }
            val spans = policy.spans(path)
            onCandidatePath?.invoke(text, path)
            segments?.set(text, path.map { CandidateConversionSegment(it.sPos, it.sPos + it.len, it.tango) })
            splitPatterns?.set(text, path.drop(1).filter { independent(it.l) }.map { it.sPos })
            val type: Byte = when {
                path.any { it.candidateSource == CandidateSource.LEARNED_DICTIONARY } -> CANDIDATE_TYPE_LEARNED_DICTIONARY
                path.any { it.candidateSource == CandidateSource.USER_DICTIONARY } -> CANDIDATE_TYPE_USER_DICTIONARY
                else -> 1
            }
            result.add(Candidate(text, type, length.toUByte(), output.cost, yomi = path.joinToString("") { it.yomiUsed },
                leftId = path.first().l, rightId = path.last().r, numberSpans = spans))
        }
        return result
    }
}
