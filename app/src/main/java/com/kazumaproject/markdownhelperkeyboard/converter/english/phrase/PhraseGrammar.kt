// Adapted from kotlin-kana-kanji-converter at dbbb97f (MIT). See the bundled English dictionary notices.
package com.kazumaproject.markdownhelperkeyboard.converter.english.phrase

internal data class PhraseEvidence(
    val score: Int = 0,
    val clauses: Int = 0,
    val reasons: List<String> = emptyList(),
) {
    operator fun plus(other: PhraseEvidence) =
        PhraseEvidence(score + other.score, clauses + other.clauses, reasons + other.reasons)

    fun add(points: Int, reason: String, clause: Boolean = false) =
        PhraseEvidence(score + points, clauses + if (clause) 1 else 0, reasons + reason)
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

    fun score(surfaces: List<String>): PhraseEvidence {
        if (surfaces.size < 2) return PhraseEvidence()
        var evidence = PhraseEvidence()
        val clause = mutableListOf<Word>()
        for (surface in surfaces) {
            when {
                surface.any { it in clausePunctuation } && surface.none(Char::isLetterOrDigit) -> {
                    evidence += scoreClause(clause)
                    clause.clear()
                }
                surface.isNotBlank() -> clause.add(word(surface))
            }
        }
        return evidence + scoreClause(clause)
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
        return alternatives.map(::scoreExpanded).maxWith(evidenceOrder)
    }

    private fun scoreExpanded(words: List<Word>): PhraseEvidence {
        var total = PhraseEvidence()
        var start = 0
        for (index in 1 until words.lastIndex) {
            val marker = words[index]
            val subordinate = marker.text in subordinators
            if (!subordinate && !marker.has("conjunction_coordinating")) continue
            val nextClause = words.subList(index + 1, words.size)
            val hasSubjectAndVerb = nounPhrases(nextClause, 0, subject = true).any {
                nextClause.getOrNull(skipAdverbs(nextClause, it.end))?.any(finiteRoles) == true
            }
            if (!hasSubjectAndVerb) continue
            val first = simpleClause(words.subList(start, index))
            if (first.clauses == 0) continue
            total += first.add(1, if (subordinate) "SUBORDINATE_CLAUSE" else "COORDINATED_CLAUSE")
            start = index + 1
        }
        return total + simpleClause(words.subList(start, words.size))
    }

    private fun simpleClause(words: List<Word>): PhraseEvidence {
        val analyses = mutableListOf<PhraseEvidence>()
        for (subject in subjects(words, 0)) {
            val position = skipAdverbs(words, subject.end)
            val verb = words.getOrNull(position)
            if (verb == null) {
                analyses += subject.evidence
            } else {
                val predicate = predicate(words, position, position + 1, subject.agreement)
                analyses += subject.evidence + predicate.evidence + objectPhrase(words, predicate.end)
            }
        }
        if (words.firstOrNull()?.any(auxiliaries) == true) {
            for (subject in subjects(words, skipAdverbs(words, 1))) {
                if (subject.end >= words.size) continue
                val predicate = predicate(words, 0, subject.end, subject.agreement)
                if (predicate.evidence.clauses > 0 && predicate.end > subject.end) {
                    analyses += subject.evidence + predicate.evidence.add(1, "QUESTION_INVERSION") +
                        objectPhrase(words, predicate.end)
                }
            }
        }
        // Prefer a grounded analysis, including a mismatch, over a no-evidence fallback.
        return analyses.maxWithOrNull(evidenceOrder) ?: PhraseEvidence()
    }

    private fun predicate(words: List<Word>, position: Int, complementStart: Int, agreement: Agreement): Predicate {
        val verb = words[position]
        if (!verb.any(finiteByAgreement.getValue(agreement))) {
            val evidence = if (verb.any(finiteRoles)) PhraseEvidence().add(-3, "EXPECTED_FINITE_$agreement")
            else PhraseEvidence()
            return Predicate(complementStart, evidence)
        }
        val finite = PhraseEvidence().add(3, "FINITE_$agreement")
        val requirement = when {
            verb.has("aux_modal") || verb.family("aux_do_") -> "BARE_AFTER_MODAL_OR_DO" to bare
            verb.family("aux_have_") -> "PERFECT_PARTICIPLE" to participle
            verb.family("aux_be_") -> "BE_COMPLEMENT" to beComplement
            else -> return Predicate(complementStart, finite.copy(clauses = 1))
        }
        val complementIndex = skipAdverbs(words, complementStart)
        val complement = words.getOrNull(complementIndex) ?: return Predicate(complementStart, finite)
        if (complement.any(requirement.second)) {
            return Predicate(complementIndex + 1, finite.add(3, requirement.first, clause = true))
        }
        if (requirement.first == "BE_COMPLEMENT" && complement.has("adjective")) {
            return Predicate(complementIndex + 1, finite.add(1, requirement.first, clause = true))
        }
        val nominal = nounPhrases(words, complementIndex).maxByOrNull { it.end }
        if (nominal != null && nominal.evidence.score > 0) {
            if (requirement.first == "BE_COMPLEMENT") {
                return Predicate(nominal.end, finite.add(2, requirement.first, clause = true) + nominal.evidence)
            }
            if (verb.family("aux_have_") || verb.family("aux_do_")) {
                return Predicate(nominal.end, finite.copy(clauses = 1) + nominal.evidence)
            }
        }
        val evidence = if (complement.any(verbForms)) finite.add(-4, "EXPECTED_${requirement.first}") else finite
        return Predicate(complementStart, evidence)
    }

    private fun subjects(words: List<Word>, start: Int): List<NounPhrase> {
        val heads = nounPhrases(words, start, subject = true).map { withPrepositionalPhrase(words, it) }
        val coordinated = heads.flatMap { head ->
            if (words.getOrNull(head.end)?.text != "and") emptyList() else
                nounPhrases(words, head.end + 1, subject = true).map {
                    NounPhrase(it.end, Agreement.OTHER, head.evidence + it.evidence)
                }
        }
        return heads + coordinated
    }

    private fun withPrepositionalPhrase(words: List<Word>, head: NounPhrase): NounPhrase {
        var end = head.end
        var evidence = head.evidence
        while (words.getOrNull(end)?.has("preposition") == true) {
            val objectHead = nounPhrases(words, end + 1).maxByOrNull { it.end } ?: break
            end = objectHead.end
            evidence += objectHead.evidence.add(1, "PREPOSITIONAL_PHRASE")
        }
        return head.copy(end = end, evidence = evidence)
    }

    private fun objectPhrase(words: List<Word>, start: Int): PhraseEvidence =
        nounPhrases(words, start).maxOfWithOrNull(evidenceOrder) { withPrepositionalPhrase(words, it).evidence }
            ?: PhraseEvidence()

    private fun nounPhrases(words: List<Word>, start: Int, subject: Boolean = false): List<NounPhrase> {
        val first = words.getOrNull(start) ?: return emptyList()
        if (subject) {
            val pronoun = when {
                first.has("pronoun_subject_1sg") -> Agreement.FIRST_SINGULAR
                first.any(pluralPronoun) -> Agreement.OTHER
                first.any(singularPronoun) -> Agreement.THIRD_SINGULAR
                else -> null
            }
            if (pronoun != null) return listOf(NounPhrase(start + 1, pronoun, PhraseEvidence()))
        }
        val determiner = first.family("det_")
        val result = mutableListOf<NounPhrase>()
        var position = start + if (determiner) 1 else 0
        while (position < words.size) {
            val head = words[position]
            val singular = head.any(bareSingular) || (determiner || !subject) && head.has("noun_count_singular")
            val plural = head.any(pluralNoun)
            val needsSingular = determiner && first.any(singularDeterminer)
            val needsPlural = determiner && first.any(pluralDeterminer)
            val evidence = if (determiner) PhraseEvidence().add(1, "NOUN_PHRASE") else PhraseEvidence()
            if (singular && !needsPlural) result += NounPhrase(position + 1, Agreement.THIRD_SINGULAR, evidence)
            if (plural && !needsSingular) result += NounPhrase(position + 1, Agreement.OTHER, evidence)
            if (!determiner || !head.has("adjective")) break
            position++
        }
        return result
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
        val evidenceOrder = compareBy<PhraseEvidence> { it.score }.thenBy { it.clauses }
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
