package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHiraganaWithSymbols
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import java.util.Locale

enum class OtherDictFormat { AUTO, GOOGLE_JP_INPUT, MICROSOFT_IME }

object OtherDictionaryUserWordParser {

    private const val IMPORTED_POS_SCORE = 5000
    private const val NO_INDEX = -1

    private data class ParsedColumns(
        val reading: String,
        val word: String,
        val posHint: String?,
    )

    private data class PosPattern(
        val posIndex: Int,
        val keywords: List<String>,
    )

    private val posPatterns = listOf(
        PosPattern(4, listOf("助動詞", "auxiliary")),
        PosPattern(3, listOf("副詞", "adverb")),
        PosPattern(5, listOf("助詞", "particle")),
        PosPattern(2, listOf("形容詞", "形容動詞", "adjective")),
        PosPattern(1, listOf("動詞", "verb")),
        PosPattern(6, listOf("感動詞", "interjection")),
        PosPattern(7, listOf("接続詞", "conjunction")),
        PosPattern(8, listOf("接頭詞", "接頭語", "prefix")),
        PosPattern(9, listOf("記号", "顔文字", "symbol", "emoticon")),
        PosPattern(10, listOf("連体詞", "adnominal")),
        PosPattern(11, listOf("その他", "接尾", "other", "suffix")),
        PosPattern(
            0,
            listOf(
                "名詞",
                "固有名詞",
                "人名",
                "姓",
                "名",
                "地名",
                "地域",
                "組織",
                "短縮よみ",
                "サジェストのみ",
                "noun",
                "proper",
                "person",
                "place",
                "name",
                "organization",
            ),
        ),
    )

    fun parse(text: String, format: OtherDictFormat): List<UserWord> {
        val lines = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split('\n')

        val out = ArrayList<UserWord>(lines.size)
        val seen = HashSet<String>(lines.size)

        lines.forEach { raw ->
            val parsed = parseLine(raw, format) ?: return@forEach
            val key = "${parsed.reading}\t${parsed.word}"
            if (!seen.add(key)) return@forEach

            out.add(
                UserWord(
                    id = 0,
                    word = parsed.word,
                    reading = parsed.reading,
                    posIndex = resolvePosIndex(parsed.posHint),
                    posScore = IMPORTED_POS_SCORE,
                ),
            )
        }

        return out
    }

    fun resolvePosIndex(posHint: String?): Int {
        if (posHint.isNullOrBlank()) return 0

        val normalized = posHint.lowercase(Locale.ROOT)
        return posPatterns
            .firstOrNull { pattern ->
                pattern.keywords.any { keyword -> normalized.contains(keyword.lowercase(Locale.ROOT)) }
            }
            ?.posIndex
            ?: 0
    }

    private fun parseLine(raw: String, format: OtherDictFormat): ParsedColumns? {
        val line = raw.trim()
        if (line.isEmpty()) return null
        if (line.startsWith("#") || line.startsWith("//") || line.startsWith(";")) return null
        if (line.contains("Microsoft IME", ignoreCase = true) && line.count { it == '\t' } < 1) {
            return null
        }

        val columns = when {
            '\t' in line -> line.split('\t')
            ',' in line -> parseCsvColumns(line)
            else -> return null
        }.map { it.trim().trim('"') }

        if (columns.size < 2) return null
        if (looksLikeHeader(columns)) return null

        val parsed = chooseReadingAndWord(columns, format) ?: return null
        if (parsed.reading.isEmpty() || parsed.word.isEmpty()) return null
        if (!parsed.reading.isAllHiraganaWithSymbols()) return null

        return parsed
    }

    private fun chooseReadingAndWord(
        columns: List<String>,
        format: OtherDictFormat,
    ): ParsedColumns? {
        val preferred = when (format) {
            OtherDictFormat.GOOGLE_JP_INPUT -> listOf(0 to 1, 1 to 0)
            OtherDictFormat.MICROSOFT_IME -> listOf(1 to 0, 0 to 1)
            OtherDictFormat.AUTO -> listOf(NO_INDEX to NO_INDEX)
        }

        preferred.forEach { (readingIndex, wordIndex) ->
            if (readingIndex == NO_INDEX) return@forEach
            val parsed = buildParsedColumns(columns, readingIndex, wordIndex)
            if (parsed != null && looksLikeReading(parsed.reading)) return parsed
        }

        val readingIndex = columns.indexOfFirst { looksLikeReading(it) }
        if (readingIndex < 0) return null

        val wordIndex = columns.indices.firstOrNull { index ->
            index != readingIndex && columns[index].isNotBlank()
        } ?: return null

        return buildParsedColumns(columns, readingIndex, wordIndex)
    }

    private fun buildParsedColumns(
        columns: List<String>,
        readingIndex: Int,
        wordIndex: Int,
    ): ParsedColumns? {
        val rawReading = columns.getOrNull(readingIndex)?.trim().orEmpty()
        val word = columns.getOrNull(wordIndex)?.trim().orEmpty()
        if (rawReading.isEmpty() || word.isEmpty()) return null

        val posHint = columns.indices
            .firstOrNull { index ->
                index != readingIndex && index != wordIndex && looksLikePosHint(columns[index])
            }
            ?.let { columns[it] }
            ?: columns.getOrNull(2)?.takeIf { 2 != readingIndex && 2 != wordIndex }

        return ParsedColumns(
            reading = normalizeYomi(rawReading),
            word = word,
            posHint = posHint,
        )
    }

    private fun looksLikeHeader(columns: List<String>): Boolean {
        val normalized = columns.take(4).map { it.lowercase(Locale.ROOT) }
        val hasReadingHeader = normalized.any { it in listOf("よみ", "読み", "yomi", "reading") }
        val hasWordHeader = normalized.any { it in listOf("単語", "語句", "word", "phrase") }
        val hasPosHeader = normalized.any { it in listOf("品詞", "part of speech", "pos") }
        return hasReadingHeader && hasWordHeader && hasPosHeader
    }

    private fun parseCsvColumns(line: String): List<String> {
        val result = mutableListOf<String>()
        val builder = StringBuilder()
        var inQuote = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuote && i + 1 < line.length && line[i + 1] == '"' -> {
                    builder.append('"')
                    i += 1
                }

                ch == '"' -> inQuote = !inQuote
                ch == ',' && !inQuote -> {
                    result += builder.toString()
                    builder.clear()
                }

                else -> builder.append(ch)
            }
            i += 1
        }

        result += builder.toString()
        return result
    }

    private fun normalizeYomi(value: String): String {
        val normalized = StringBuilder(value.length)
        value.forEach { ch ->
            when (ch) {
                in 'ァ'..'ヶ' -> normalized.append((ch.code - 0x60).toChar())
                else -> normalized.append(ch)
            }
        }
        return normalized.toString().lowercase(Locale.JAPAN)
    }

    private fun looksLikeReading(value: String): Boolean {
        return normalizeYomi(value).isAllHiraganaWithSymbols()
    }

    private fun looksLikePosHint(value: String): Boolean {
        if (value.isBlank()) return false
        val normalized = value.lowercase(Locale.ROOT)
        return posPatterns.any { pattern ->
            pattern.keywords.any { keyword -> normalized.contains(keyword.lowercase(Locale.ROOT)) }
        }
    }
}
