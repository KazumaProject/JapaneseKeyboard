package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary

import java.nio.charset.Charset
import java.util.Locale

object SystemUserDictionaryExternalParser {

    data class ParsedEntry(
        val yomi: String,
        val tango: String,
        val posHint: String?,
    )

    data class ParseResult(
        val entries: List<ParsedEntry>,
        val skippedLines: Int,
    )

    fun parse(inputBytes: ByteArray): ParseResult {
        val text = decodeText(inputBytes)
        val lines = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split('\n')

        val entries = mutableListOf<ParsedEntry>()
        var skipped = 0

        lines.forEach { line ->
            val parsed = parseLine(line)
            if (parsed == null) {
                skipped += 1
            } else {
                entries += parsed
            }
        }

        return ParseResult(entries = entries, skippedLines = skipped)
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        fun startsWith(vararg sig: Int): Boolean {
            if (bytes.size < sig.size) return false
            return sig.indices.all { i -> (bytes[i].toInt() and 0xFF) == sig[i] }
        }

        val charset = when {
            startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8
            startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE
            startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE
            bytes.count { it == 0.toByte() } > bytes.size / 10 -> Charsets.UTF_16LE
            else -> Charsets.UTF_8
        }

        return runCatching { String(bytes, charset) }
            .getOrElse { String(bytes, Charset.forName("Shift_JIS")) }
    }

    private fun parseLine(line: String): ParsedEntry? {
        val trimmedLine = line.trim()
        if (trimmedLine.isBlank()) return null
        if (trimmedLine.startsWith("#") || trimmedLine.startsWith("//") || trimmedLine.startsWith(";")) {
            return null
        }

        val columns = when {
            '\t' in trimmedLine -> trimmedLine.split('\t')
            ',' in trimmedLine -> parseCsvColumns(trimmedLine)
            else -> return null
        }.map { it.trim().trim('"') }
            .filter { it.isNotBlank() }

        if (columns.size < 2) return null

        val normalized = columns.map { normalizeYomi(it) }
        val yomiIndex = normalized.indexOfFirst { looksLikeYomi(it) }
        if (yomiIndex < 0) return null

        val yomi = normalized[yomiIndex]
        val tangoIndex = columns.indices.firstOrNull { idx -> idx != yomiIndex && columns[idx].isNotBlank() }
            ?: return null
        val tango = columns[tangoIndex]

        val posHint = columns.indices
            .firstOrNull { idx -> idx != yomiIndex && idx != tangoIndex && looksLikePosHint(columns[idx]) }
            ?.let { columns[it] }
            ?: columns.getOrNull(2)

        return ParsedEntry(yomi = yomi, tango = tango, posHint = posHint)
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

    private fun looksLikeYomi(value: String): Boolean {
        if (value.isBlank()) return false
        return value.all { it in 'ぁ'..'ゖ' || it == 'ー' }
    }

    private fun looksLikePosHint(value: String): Boolean {
        val normalized = value.lowercase(Locale.ROOT)
        val keywords = listOf(
            "名詞", "人名", "固有名詞", "地名", "動詞", "形容詞", "副詞", "助動詞", "助詞", "記号", "接頭詞",
            "noun", "verb", "adjective", "adverb", "auxiliary", "particle", "symbol", "interjection",
            "conjunction", "prefix", "adnominal", "proper", "person",
        )
        return keywords.any { keyword -> normalized.contains(keyword) }
    }
}

