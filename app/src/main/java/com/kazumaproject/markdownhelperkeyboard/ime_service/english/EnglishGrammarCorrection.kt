package com.kazumaproject.markdownhelperkeyboard.ime_service.english

import java.io.Reader

internal data class EnglishGrammarCorrection(
    val start: Int,
    val end: Int,
    val original: String,
    val replacement: String,
)

internal class EnglishVerbForms private constructor(private val forms: Map<String, List<String>>) {
    fun suggest(beforeCursor: String, startsAtDocument: Boolean): EnglishGrammarCorrection? {
        val trimmedEnd = beforeCursor.indexOfLast { !it.isWhitespace() } + 1
        if (trimmedEnd == 0 || beforeCursor[trimmedEnd - 1] == '?') return null
        val end = if (beforeCursor[trimmedEnd - 1] in ".!") trimmedEnd - 1 else trimmedEnd
        val boundary = beforeCursor.take(end).indexOfLast { it in ".!?\n" }
        if (boundary < 0 && !startsAtDocument) return null
        val sentence = beforeCursor.substring(boundary + 1, end)
        if (!simpleClause.matches(sentence)) return null
        val words = word.findAll(sentence).toList()
        val subject = words.getOrNull(0)?.value?.lowercase() ?: return null
        val agreement = subjects[subject] ?: return null
        val verb = words.getOrNull(1) ?: return null
        val original = verb.value
        val source = original.lowercase()
        val tail = words.drop(2).map { it.value.lowercase() }
        val cueLength = when {
            tail.lastOrNull() == "yesterday" -> 1
            tail.takeLast(2) in pastPhrases -> 2
            else -> 0
        }
        val column = when {
            cueLength > 0 && isPastCueAttachedToVerb(source, tail, cueLength) ->
                if (agreement == OTHER) PAST_OTHER else PAST_SINGULAR
            tail.isEmpty() -> agreement
            else -> return null
        }
        val form = forms[source]?.get(column)?.takeUnless { it == "-" } ?: return null
        val replacement = when {
            original.all(Char::isUpperCase) -> form.uppercase()
            original.first().isUpperCase() && original.drop(1).all(Char::isLowerCase) ->
                form.replaceFirstChar(Char::uppercaseChar)
            original.all(Char::isLowerCase) -> form
            else -> return null
        }
        return EnglishGrammarCorrection(boundary + 1 + verb.range.first, boundary + 2 + verb.range.last, original, replacement)
    }

    private fun isPastCueAttachedToVerb(verb: String, tail: List<String>, cueLength: Int): Boolean {
        val cueStart = tail.size - cueLength
        // A temporal phrase can be an object or belong to a different clause.
        if (cueStart == 0 && verb in temporalObjectVerbs) return false
        if (cueStart > 1 && verb in clauseTakingVerbs) return false
        val preceding = tail.getOrNull(cueStart - 1)
        // Keep the existing suggestion for an unfinished "go to" before the cue.
        val unfinishedGoTo = cueStart == 1 && preceding == "to" && (verb == "go" || verb == "goes")
        if (preceding in cuePrepositions && !unfinishedGoTo) return false
        for (index in 0 until cueStart) {
            val word = tail[index]
            if (word in clauseMarkers) return false
            if (word in subjects && (index != cueStart - 1 || word !in objectPronouns)) return false
        }
        return true
    }

    companion object {
        private const val FIRST = 0
        private const val THIRD = 1
        private const val OTHER = 2
        private const val PAST_SINGULAR = 3
        private const val PAST_OTHER = 4
        private val subjects = mapOf("i" to FIRST, "he" to THIRD, "she" to THIRD, "it" to THIRD,
            "you" to OTHER, "we" to OTHER, "they" to OTHER)
        private val pastPhrases = setOf(listOf("last", "night"), listOf("last", "week"))
        private val clauseMarkers = setOf("and", "or", "but", "that", "which", "who", "when", "where",
            "while", "after", "before", "because", "if", "unless", "although", "though", "than",
            "what", "whom", "whose", "whether", "how")
        private val objectPronouns = setOf("you", "it")
        private val cuePrepositions = setOf("about", "at", "by", "during", "for", "from", "in",
            "of", "on", "over", "since", "through", "to", "until", "with")
        private val clauseTakingVerbs = setOf("believe", "believes", "hope", "hopes", "know", "knows",
            "recall", "recalls", "remember", "remembers", "think", "thinks")
        private val temporalObjectVerbs = clauseTakingVerbs + setOf("forget", "forgets", "like", "likes",
            "love", "loves", "miss", "misses", "prefer", "prefers")
        private val word = Regex("[A-Za-z]+")
        private val simpleClause = Regex("[ \\t]*[A-Za-z]+(?:[ \\t]+[A-Za-z]+)*[ \\t]*")

        fun read(reader: Reader): EnglishVerbForms = reader.buffered().useLines { lines ->
            EnglishVerbForms(lines.associate { line ->
                val fields = line.split('\t')
                require(fields.size == 6)
                fields[0] to fields.drop(1)
            })
        }
    }
}
