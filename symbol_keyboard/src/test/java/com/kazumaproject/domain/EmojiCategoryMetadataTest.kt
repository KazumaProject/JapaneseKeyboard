package com.kazumaproject.domain

import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.data.emoticon.EmoticonCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCategoryMetadataTest {

    @Test
    fun unicodeSequencesUseTheirPublishedTopLevelGroups() {
        val expected = mapOf(
            "😀" to EmojiCategory.SMILEYS_EMOTION,
            "☺" to EmojiCategory.SMILEYS_EMOTION,
            "❤️‍🔥" to EmojiCategory.SMILEYS_EMOTION,
            "👍" to EmojiCategory.PEOPLE_BODY,
            "👍🏽" to EmojiCategory.PEOPLE_BODY,
            "👩‍💻" to EmojiCategory.PEOPLE_BODY,
            "🐶" to EmojiCategory.ANIMALS_NATURE,
            "🍎" to EmojiCategory.FOOD_DRINK,
            "✈️" to EmojiCategory.TRAVEL_PLACES,
            "⚽" to EmojiCategory.ACTIVITIES,
            "💡" to EmojiCategory.OBJECTS,
            "#️⃣" to EmojiCategory.SYMBOLS,
            "🇯🇵" to EmojiCategory.FLAGS,
            "⌚️" to EmojiCategory.TRAVEL_PLACES,
            "♈️" to EmojiCategory.SYMBOLS,
            "🫩" to EmojiCategory.SMILEYS_EMOTION,
            "🫪" to EmojiCategory.SMILEYS_EMOTION,
        )

        expected.forEach { (symbol, category) ->
            assertEquals(symbol, category, categorizeEmoji(symbol))
        }
        assertEquals(EmojiCategory.UNKNOWN, categorizeEmoji("not-an-emoji"))
    }

    @Test
    fun emojiSortUsesPublishedCldrOrderAndLeavesUnknownLast() {
        val input = listOf(
            Emoji("not-an-emoji", EmojiCategory.UNKNOWN),
            Emoji("😃", categorizeEmoji("😃")),
            Emoji("😀", categorizeEmoji("😀")),
            Emoji("🇯🇵", categorizeEmoji("🇯🇵")),
        )

        assertEquals(
            listOf("😀", "😃", "🇯🇵", "not-an-emoji"),
            input.sortByEmojiCategory().map { it.symbol },
        )
    }

    @Test
    fun originalEmoticonCategoriesRemainUnchanged() {
        val expected = mapOf(
            ":-)" to EmoticonCategory.SMILE,
            "( •̀ㅁ•́;)" to EmoticonCategory.SWEAT,
            "Σ(ﾟДﾟ)" to EmoticonCategory.SURPRISE,
            "(TT)" to EmoticonCategory.SADNESS,
            "(-_-)" to EmoticonCategory.DISPLEASURE,
        )

        expected.forEach { (symbol, category) ->
            assertEquals(symbol, category, symbol.toEmoticonCategory())
        }
        assertEquals(EmoticonCategory.UNKNOWN, "not-an-emoticon".toEmoticonCategory())
    }

    @Test
    fun generatedMetadataContainsThePinnedSourceData() {
        assertEquals("17.0", EmojiCategoryMetadata.UNICODE_VERSION)
        assertTrue(EmojiCategoryMetadata.emojiEntryCount() > 5_000)
    }
}
