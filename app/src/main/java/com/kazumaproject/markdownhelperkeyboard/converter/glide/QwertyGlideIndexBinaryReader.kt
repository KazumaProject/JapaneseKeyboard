package com.kazumaproject.markdownhelperkeyboard.converter.glide

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

class QwertyGlideIndexFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class QwertyGlideIndexBinaryReader(
    private val maxEntryCount: Int = DEFAULT_MAX_ENTRY_COUNT
) {
    fun read(input: InputStream): List<QwertyGlideIndexedEntry> {
        val dataInput = DataInputStream(BufferedInputStream(input))
        try {
            val magic = dataInput.readUTF()
            if (magic != MAGIC) {
                throw QwertyGlideIndexFormatException("Invalid qwerty glide index magic: $magic")
            }
            val version = dataInput.readInt()
            if (version != VERSION) {
                throw QwertyGlideIndexFormatException("Unsupported qwerty glide index version: $version")
            }
            val entryCount = dataInput.readInt()
            if (entryCount <= 0) {
                throw QwertyGlideIndexFormatException("Invalid qwerty glide index entryCount: $entryCount")
            }
            if (entryCount > maxEntryCount) {
                throw QwertyGlideIndexFormatException("Qwerty glide index entryCount is too large: $entryCount")
            }

            val entries = ArrayList<QwertyGlideIndexedEntry>(entryCount)
            var previous: QwertyGlideIndexedEntry? = null
            repeat(entryCount) { index ->
                val entry = readEntry(dataInput, index)
                val previousEntry = previous
                if (previousEntry != null && ENTRY_COMPARATOR.compare(previousEntry, entry) > 0) {
                    throw QwertyGlideIndexFormatException("Qwerty glide index is not sorted at entry $index")
                }
                entries += entry
                previous = entry
            }
            if (dataInput.read() != -1) {
                throw QwertyGlideIndexFormatException("Qwerty glide index has trailing bytes")
            }
            return entries
        } catch (error: QwertyGlideIndexFormatException) {
            throw error
        } catch (error: EOFException) {
            throw QwertyGlideIndexFormatException("Unexpected end of qwerty glide index", error)
        }
    }

    private fun readEntry(input: DataInputStream, index: Int): QwertyGlideIndexedEntry {
        val word = input.readUTF()
        val wordCost = input.readInt()
        val firstChar = input.readChar()
        val lastChar = input.readChar()
        val length = input.readInt()
        val characterMask = input.readInt()
        val transitionMask = input.readLong()

        if (word.length !in WORD_LENGTH_RANGE) {
            throw QwertyGlideIndexFormatException("Invalid word length at entry $index: ${word.length}")
        }
        if (word.any { it !in 'a'..'z' }) {
            throw QwertyGlideIndexFormatException("Invalid word characters at entry $index: $word")
        }
        if (wordCost < 0) {
            throw QwertyGlideIndexFormatException("Invalid wordCost at entry $index: $wordCost")
        }
        if (firstChar != word.first()) {
            throw QwertyGlideIndexFormatException("Invalid firstChar at entry $index: $firstChar")
        }
        if (lastChar != word.last()) {
            throw QwertyGlideIndexFormatException("Invalid lastChar at entry $index: $lastChar")
        }
        if (length != word.length) {
            throw QwertyGlideIndexFormatException("Invalid length at entry $index: $length")
        }
        val expectedCharacterMask = word.characterMask()
        if (characterMask != expectedCharacterMask) {
            throw QwertyGlideIndexFormatException("Invalid characterMask at entry $index: $characterMask")
        }
        val expectedTransitionMask = word.transitionMask()
        if (transitionMask != expectedTransitionMask) {
            throw QwertyGlideIndexFormatException("Invalid transitionMask at entry $index: $transitionMask")
        }

        return QwertyGlideIndexedEntry(
            word = word,
            wordCost = wordCost,
            firstChar = firstChar,
            lastChar = lastChar,
            length = length,
            characterMask = characterMask,
            transitionMask = transitionMask
        )
    }

    companion object {
        const val MAGIC = "QGIX"
        const val VERSION = 1
        const val DEFAULT_MAX_ENTRY_COUNT = 500_000
        val WORD_LENGTH_RANGE = 2..24

        private val ENTRY_COMPARATOR = compareBy<QwertyGlideIndexedEntry> { it.firstChar }
            .thenBy { it.lastChar }
            .thenBy { it.length }
            .thenBy { it.word }
            .thenBy { it.wordCost }
    }
}
