package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.data.emoticon.EmoticonCategory
import com.kazumaproject.domain.categorizeEmoji
import com.kazumaproject.domain.toEmoticonCategory
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EmojiCategoryInstrumentedTest {

    @Test
    fun bundledEmojiDictionaryUsesPublishedMetadataOnDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        ).kanaKanjiEngine()

        val emojis = engine.getSymbolEmojiCandidates()
        val emoticons = engine.getSymbolEmoticonCandidates()
        val emojiCounts = EmojiCategory.entries.associateWith { category ->
            emojis.count { it.category == category }
        }
        val emoticonCounts = EmoticonCategory.entries.associateWith { category ->
            emoticons.count { it.category == category }
        }

        val report = buildString {
            appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT}")
            appendLine("emojiTotal=${emojis.size}")
            EmojiCategory.entries.forEach { appendLine("emoji.${it.name}=${emojiCounts.getValue(it)}") }
            emojis.asSequence()
                .filter { it.category == EmojiCategory.UNKNOWN }
                .take(30)
                .forEach { emoji ->
                    appendLine(
                        "emoji.unknown=${emoji.symbol} [" +
                            emoji.symbol.codePoints().toArray()
                                .joinToString(" ") { "U+%04X".format(it) } +
                            "]"
                    )
                }
            appendLine("emoticonTotal=${emoticons.size}")
            EmoticonCategory.entries.forEach {
                appendLine("emoticon.${it.name}=${emoticonCounts.getValue(it)}")
            }
        }
        val reportFile = File(context.filesDir, "emoji-category-device-report.txt")
        reportFile.writeText(report)
        println(report)

        assertEquals(1_902, emojis.size)
        assertEquals(436, emoticons.size)
        assertEquals(EmojiCategory.FLAGS, categorizeEmoji("🇯🇵"))
        assertEquals(EmojiCategory.SYMBOLS, categorizeEmoji("#️⃣"))
        assertEquals(EmojiCategory.SMILEYS_EMOTION, categorizeEmoji("❤️‍🔥"))
        assertEquals(EmoticonCategory.SMILE, ":-)".toEmoticonCategory())
        assertEquals(EmoticonCategory.SADNESS, "(TT)".toEmoticonCategory())
        assertEquals(4, emojiCounts.getValue(EmojiCategory.UNKNOWN))
        assertTrue(emojiCounts.getValue(EmojiCategory.UNKNOWN) < emojis.size)
        assertTrue(emoticonCounts.getValue(EmoticonCategory.UNKNOWN) < emoticons.size)
    }
}
