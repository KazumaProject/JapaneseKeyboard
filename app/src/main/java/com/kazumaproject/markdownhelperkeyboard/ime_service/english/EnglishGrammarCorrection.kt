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
        val tail = words.drop(2).map { it.value.lowercase() }
        val pastCue = tail.lastOrNull() == "yesterday" || tail.takeLast(2) in pastPhrases
        val column = when {
            pastCue && tail.none { it in clauseMarkers || it in subjects } -> if (agreement == OTHER) PAST_OTHER else PAST_SINGULAR
            tail.isEmpty() -> agreement
            else -> return null
        }
        val original = verb.value
        val form = forms[original.lowercase()]?.get(column)?.takeUnless { it == "-" } ?: return null
        val replacement = when {
            original.all(Char::isUpperCase) -> form.uppercase()
            original.first().isUpperCase() && original.drop(1).all(Char::isLowerCase) ->
                form.replaceFirstChar(Char::uppercaseChar)
            original.all(Char::isLowerCase) -> form
            else -> return null
        }
        return EnglishGrammarCorrection(boundary + 1 + verb.range.first, boundary + 2 + verb.range.last, original, replacement)
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
            "while", "after", "before", "because", "if", "unless", "although", "though", "than")
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
