package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.domain.categorizeEmoji
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.ObjectInputStream

class BundledEmojiCategoryTest {

    @Test
    fun allBundledEmojiCandidatesAreCountedAgainstUnicodeMetadata() {
        val assetsDir = findAssetsDir()
        val tangoTrie = ObjectInputStream(
            BufferedInputStream(File(assetsDir, "emoji/tango_emoji.dat").inputStream())
        ).use { LOUDS().readExternalNotCompress(it) }
        val tokenArray = TokenArray().also { tokenArray ->
            ObjectInputStream(
                BufferedInputStream(File(assetsDir, "emoji/token_emoji.dat").inputStream())
            ).use(tokenArray::readExternal)
        }
        val tangoLbs = SuccinctBitVector(tangoTrie.LBS)
        val symbols = tokenArray.getNodeIds()
            .map { tangoTrie.getLetterShortArray(it, tangoLbs) }
            .distinct()
        val counts = EmojiCategory.entries.associateWith { category ->
            symbols.count { categorizeEmoji(it) == category }
        }
        val classifiedCount = symbols.size - counts.getValue(EmojiCategory.UNKNOWN)
        val expectedCounts = mapOf(
            EmojiCategory.SMILEYS_EMOTION to 168,
            EmojiCategory.PEOPLE_BODY to 385,
            EmojiCategory.ANIMALS_NATURE to 158,
            EmojiCategory.FOOD_DRINK to 130,
            EmojiCategory.TRAVEL_PLACES to 218,
            EmojiCategory.ACTIVITIES to 85,
            EmojiCategory.OBJECTS to 262,
            EmojiCategory.SYMBOLS to 223,
            EmojiCategory.FLAGS to 269,
            EmojiCategory.UNKNOWN to 4,
        )

        println("emojiTotal=${symbols.size}")
        EmojiCategory.entries.forEach { println("emoji.${it.name}=${counts.getValue(it)}") }
        println("emojiClassified=$classifiedCount")

        assertEquals(1_902, symbols.size)
        assertEquals(expectedCounts, counts)
        assertEquals(1_898, classifiedCount)
        assertEquals(
            setOf("🦰", "🦱", "🦲", "🦳"),
            symbols.filter { categorizeEmoji(it) == EmojiCategory.UNKNOWN }.toSet(),
        )
    }

    private fun findAssetsDir(): File {
        var current = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(current, "app/src/main/assets")
            if (candidate.isDirectory) return candidate
            current = current.parentFile
                ?: error("Cannot find app/src/main/assets from ${System.getProperty("user.dir")}")
        }
        error("Cannot find app/src/main/assets from ${System.getProperty("user.dir")}")
    }
}
