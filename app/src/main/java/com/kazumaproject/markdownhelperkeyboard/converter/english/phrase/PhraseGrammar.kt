// Adapted from kotlin-kana-kanji-converter at dbbb97f (MIT). See the bundled English dictionary notices.
package com.kazumaproject.markdownhelperkeyboard.converter.english.phrase

@JvmInline
internal value class PhraseEvidence private constructor(private val packed: Long) : Comparable<PhraseEvidence> {
    constructor(score: Int = 0, clauses: Int = 0) :
        this((clauses.toLong() shl 32) or (score.toLong() and 0xffff_ffffL))

    val score: Int get() = packed.toInt()
    val clauses: Int get() = (packed ushr 32).toInt()

    operator fun plus(other: PhraseEvidence) = PhraseEvidence(score + other.score, clauses + other.clauses)

    fun add(points: Int, clause: Boolean = false) =
        PhraseEvidence(score + points, clauses + if (clause) 1 else 0)

    override fun compareTo(other: PhraseEvidence): Int {
        val byScore = score.compareTo(other.score)
        return if (byScore != 0) byScore else clauses.compareTo(other.clauses)
    }
}

/** Scores local constructions. A missing or ambiguous analysis is not a rejection. */
internal class PhraseGrammar(private val grammarRoles: GrammarRoles) {
    private data class Word(val text: String, val roles: Set<String>) {
        fun any(categories: Set<String>) = roles.any { it in categories }
        fun has(category: String) = category in roles
        fun family(prefix: String) = roles.any { it.startsWith(prefix) }
    }

    private data class NounPhrase(val end: Int, val agreement: Agreement, val evidence: PhraseEvidence)
    private data class Predicate(val end: Int, val evidence: PhraseEvidence)
    private val words = mutableMapOf<String, Word>()
    private val clause = ArrayList<Word>()

    fun score(surfaces: Array<String?>, size: Int): PhraseEvidence {
        if (size < 2) return PhraseEvidence()
        var evidence = PhraseEvidence()
        for (index in 0 until size) {
            val surface = checkNotNull(surfaces[index])
            when {
                surface.any { it in clausePunctuation } && surface.none(Char::isLetterOrDigit) -> {
                    evidence += scoreClause(clause)
                    clause.clear()
                }
                surface.isNotBlank() -> clause.add(word(surface))
            }
        }
        evidence += scoreClause(clause)
        clause.clear()
        return evidence
    }

    fun supportsNounCompound(first: String, second: String): Boolean {
        val left = word(first)
        val right = word(second)
        return left.any(commonNouns) && right.any(commonNouns) &&
            left.roles.none(::isFunctionWordRole) && right.roles.none(::isFunctionWordRole) &&
            left.roles.any { it.startsWith("topic_") && !it.endsWith("_general") && right.has(it) }
    }

    private fun word(surface: String): Word = words.getOrPut(surface) {
        val normalized = lookupKey(surface)
        Word(normalized, grammarRoles.lookup(surface, normalized))
    }

    private fun scoreClause(original: List<Word>): PhraseEvidence {
        if (original.size < 2) return PhraseEvidence()
        if (original.none { it.has("contraction") && it.text in contractions }) return scoreExpanded(original)
        // Keep the original reading as an option for homographs such as a work title.
        var alternatives = listOf(emptyList<Word>())
        for (token in original) {
            val expansions = if (token.has("contraction")) contractions[token.text].orEmpty() else emptyList()
            val variants = expansions.map { expansion -> expansion.map(::word) } + listOf(listOf(token))
            alternatives = alternatives.flatMap { prefix -> variants.map { prefix + it } }.take(MAX_ANALYSES)
        }
        var best = noAnalysis
        for (alternative in alternatives) {
            val evidence = scoreExpanded(alternative)
            if (evidence > best) best = evidence
        }
        return best
    }

    private fun scoreExpanded(words: List<Word>): PhraseEvidence {
        var total = PhraseEvidence()
        var start = 0
        for (index in 1 until words.lastIndex) {
            val marker = words[index]
            val subordinate = marker.text in subordinators
            if (!subordinate && !marker.has("conjunction_coordinating")) continue
            val nextClause = words.subList(index + 1, words.size)
            if (!hasSubjectAndVerb(nextClause)) continue
            val first = simpleClause(words.subList(start, index))
            if (first.clauses == 0) continue
            total += first.add(1)
            start = index + 1
        }
        return total + simpleClause(words.subList(start, words.size))
    }

    private fun hasSubjectAndVerb(words: List<Word>): Boolean {
        forEachNounPhrase(words, 0, subject = true) { subject ->
            if (words.getOrNull(skipAdverbs(words, subject.end))?.any(finiteRoles) == true) return true
        }
        return false
    }

    private fun simpleClause(words: List<Word>): PhraseEvidence {
        var best = noAnalysis
        forEachSubject(words, 0) { subject ->
            val position = skipAdverbs(words, subject.end)
            val evidence = if (position >= words.size) {
                subject.evidence
            } else {
                val predicate = predicate(words, position, position + 1, subject.agreement)
                subject.evidence + predicate.evidence + objectPhrase(words, predicate.end)
            }
            if (evidence > best) best = evidence
        }
        if (words.firstOrNull()?.any(auxiliaries) == true) {
            forEachSubject(words, skipAdverbs(words, 1)) { subject ->
                if (subject.end < words.size) {
                    val predicate = predicate(words, 0, subject.end, subject.agreement)
                    if (predicate.evidence.clauses > 0 && predicate.end > subject.end) {
                        val evidence = subject.evidence + predicate.evidence.add(1) + objectPhrase(words, predicate.end)
                        if (evidence > best) best = evidence
                    }
                }
            }
        }
        // Prefer a grounded analysis, including a mismatch, over a no-evidence fallback.
        return if (best == noAnalysis) PhraseEvidence() else best
    }

    private fun predicate(words: List<Word>, position: Int, complementStart: Int, agreement: Agreement): Predicate {
        val verb = words[position]
        if (!verb.any(finiteByAgreement.getValue(agreement))) {
            val evidence = if (verb.any(finiteRoles)) PhraseEvidence(score = -3)
            else PhraseEvidence()
            return Predicate(complementStart, evidence)
        }
        val finite = PhraseEvidence(score = 3)
        val requirement = when {
            verb.has("aux_modal") || verb.family("aux_do_") -> bare
            verb.family("aux_have_") -> participle
            verb.family("aux_be_") -> beComplement
            else -> return Predicate(complementStart, finite.add(0, clause = true))
        }
        val complementIndex = skipAdverbs(words, complementStart)
        val complement = words.getOrNull(complementIndex) ?: return Predicate(complementStart, finite)
        if (complement.any(requirement)) {
            return Predicate(complementIndex + 1, finite.add(3, clause = true))
        }
        if (requirement === beComplement && complement.has("adjective")) {
            return Predicate(complementIndex + 1, finite.add(1, clause = true))
        }
        val nominal = longestNounPhrase(words, complementIndex)
        if (nominal != null && nominal.evidence.score > 0) {
            if (requirement === beComplement) {
                return Predicate(nominal.end, finite.add(2, clause = true) + nominal.evidence)
            }
            if (verb.family("aux_have_") || verb.family("aux_do_")) {
                return Predicate(nominal.end, finite.add(0, clause = true) + nominal.evidence)
            }
        }
        val evidence = if (complement.any(verbForms)) finite.add(-4) else finite
        return Predicate(complementStart, evidence)
    }

    private inline fun forEachSubject(words: List<Word>, start: Int, visit: (NounPhrase) -> Unit) {
        forEachNounPhrase(words, start, subject = true) {
            val head = withPrepositionalPhrase(words, it)
            visit(head)
            if (words.getOrNull(head.end)?.text == "and") {
                forEachNounPhrase(words, head.end + 1, subject = true) { next ->
                    visit(NounPhrase(next.end, Agreement.OTHER, head.evidence + next.evidence))
                }
            }
        }
    }

    private fun withPrepositionalPhrase(words: List<Word>, head: NounPhrase): NounPhrase {
        var end = head.end
        var evidence = head.evidence
        while (words.getOrNull(end)?.has("preposition") == true) {
            val objectHead = longestNounPhrase(words, end + 1) ?: break
            end = objectHead.end
            evidence += objectHead.evidence.add(1)
        }
        return if (end == head.end) head else head.copy(end = end, evidence = evidence)
    }

    private fun longestNounPhrase(words: List<Word>, start: Int): NounPhrase? {
        var longest: NounPhrase? = null
        forEachNounPhrase(words, start) { phrase ->
            if (phrase.end > (longest?.end ?: -1)) longest = phrase
        }
        return longest
    }

    private fun objectPhrase(words: List<Word>, start: Int): PhraseEvidence {
        var best = noAnalysis
        forEachNounPhrase(words, start) {
            val evidence = withPrepositionalPhrase(words, it).evidence
            if (evidence > best) best = evidence
        }
        return if (best == noAnalysis) PhraseEvidence() else best
    }

    private inline fun forEachNounPhrase(
        words: List<Word>,
        start: Int,
        subject: Boolean = false,
        visit: (NounPhrase) -> Unit,
    ) {
        val first = words.getOrNull(start) ?: return
        if (subject) {
            val pronoun = when {
                first.has("pronoun_subject_1sg") -> Agreement.FIRST_SINGULAR
                first.any(pluralPronoun) -> Agreement.OTHER
                first.any(singularPronoun) -> Agreement.THIRD_SINGULAR
                else -> null
            }
            if (pronoun != null) {
                visit(NounPhrase(start + 1, pronoun, PhraseEvidence()))
                return
            }
        }
        val determiner = first.family("det_")
        val needsSingular = determiner && first.any(singularDeterminer)
        val needsPlural = determiner && first.any(pluralDeterminer)
        val evidence = PhraseEvidence(score = if (determiner) 1 else 0)
        var position = start + if (determiner) 1 else 0
        while (position < words.size) {
            val head = words[position]
            val singular = head.any(bareSingular) || (determiner || !subject) && head.has("noun_count_singular")
            val plural = head.any(pluralNoun)
            if (singular && !needsPlural) visit(NounPhrase(position + 1, Agreement.THIRD_SINGULAR, evidence))
            if (plural && !needsSingular) visit(NounPhrase(position + 1, Agreement.OTHER, evidence))
            if (!determiner || !head.has("adjective")) break
            position++
        }
    }

    private fun skipAdverbs(words: List<Word>, start: Int): Int {
        var index = start
        while (index < words.size) {
            val word = words[index]
            if (!word.has("negation") && (!word.family("adverb_") || word.any(verbForms))) break
            index++
        }
        return index
    }

    private companion object {
        const val MAX_ANALYSES = 8
        val noAnalysis = PhraseEvidence(score = Int.MIN_VALUE)
        val clausePunctuation = ".!?;:,\r\n"
        val contractions = mapOf(
            "it's" to listOf(listOf("it", "is"), listOf("it", "has")),
            "she's" to listOf(listOf("she", "is"), listOf("she", "has")),
            "we're" to listOf(listOf("we", "are")),
            "can't" to listOf(listOf("can", "not")),
            "don't" to listOf(listOf("do", "not")),
            "doesn't" to listOf(listOf("does", "not")),
            "didn't" to listOf(listOf("did", "not")),
        )
        val subordinators = setOf("that", "because", "although", "if", "when", "while")
        val finiteByAgreement = Agreement.entries.associateWith(::finiteCategories)
        val finiteRoles = finiteByAgreement.values.flatten().toSet()
        val auxiliaries = finiteRoles.filter { it.startsWith("aux_") }.toSet()
        val bare = setOf("verb_base", "aux_be_base")
        val participle = setOf("verb_past_participle", "aux_be_participle")
        val beComplement = participle + setOf("verb_ing", "aux_be_ing", "adjective_predicative")
        val verbForms = finiteRoles + bare + beComplement - "adjective_predicative"
        val commonNouns = setOf("noun_count_singular", "noun_count_plural", "noun_uncountable", "noun_plural_only")
        val bareSingular = setOf("noun_uncountable", "noun_proper", "name_person")
        val pluralNoun = setOf("noun_count_plural", "noun_plural_only")
        val singularPronoun = setOf("pronoun_subject_3sg", "pronoun_indefinite_singular")
        val pluralPronoun = setOf("pronoun_subject_2nd", "pronoun_subject_plural", "pronoun_indefinite_plural")
        val singularDeterminer = setOf("det_count_singular", "det_demonstrative_singular")
        val pluralDeterminer = setOf("det_count_plural", "det_demonstrative_plural")
    }
}

internal enum class Agreement { FIRST_SINGULAR, THIRD_SINGULAR, OTHER }

internal fun finiteCategories(subject: Agreement): Set<String> {
    val present = when (subject) {
        Agreement.FIRST_SINGULAR -> setOf("aux_be_present_1sg", "verb_present_non3sg", "aux_have_base", "aux_do_base")
        Agreement.THIRD_SINGULAR -> setOf("aux_be_present_3sg", "verb_present_3sg", "aux_have_3sg", "aux_do_3sg")
        Agreement.OTHER -> setOf("aux_be_present_other", "verb_present_non3sg", "aux_have_base", "aux_do_base")
    }
    val pastBe = if (subject == Agreement.OTHER) "aux_be_past_other" else "aux_be_past_1sg_3sg"
    return present + setOf(pastBe, "verb_past", "aux_have_past", "aux_do_past", "aux_modal")
}

internal fun lookupKey(surface: String): String = surface.replace('’', '\'').lowercase()

internal fun isFunctionWordRole(role: String): Boolean =
    role == "preposition" || role == "determiner" || role == "pronoun" || role == "conjunction" ||
        role.startsWith("det_") || role.startsWith("pronoun_") ||
        role.startsWith("conjunction_") || role.startsWith("aux_")
