package com.kazumaproject.markdownhelperkeyboard.ime_service.english

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnglishGrammarCorrectionTest {
    @Test fun correctsRegularAndIrregularVerbsWithPastTimeCues() {
        val cases = listOf(
            "I [walk] to the station yesterday " to "walked",
            "We [play] chess last night " to "played",
            "They [clean] the kitchen last week " to "cleaned",
            "I [visit] my friends yesterday " to "visited",
            "We [cook] dinner last night " to "cooked",
            "I [study] French last week " to "studied",
            "We [watch] a movie last night " to "watched",
            "I [stop] at the store yesterday " to "stopped",
            "We [plan] a trip last week " to "planned",
            "I [carry] the bags yesterday " to "carried",
            "I [finish] my shift last night " to "finished",
            "I [work] late yesterday " to "worked",
            "He [works] late last night " to "worked",
            "I [go] to school yesterday " to "went",
            "I [go] to yesterday " to "went",
            "I [see] you yesterday " to "saw",
            "She [sees] him yesterday " to "saw",
            "We [take] the bus last week " to "took",
            "I [buy] bread yesterday " to "bought",
            "We [bring] lunch yesterday " to "brought",
            "We [make] dinner last night " to "made",
            "I [eat] breakfast yesterday " to "ate",
            "I [find] it yesterday " to "found",
            "We [give] her a ride yesterday " to "gave",
            "They [meet] us last week " to "met",
            "I [lose] my keys yesterday " to "lost",
            "I [send] the email yesterday " to "sent",
            "I [write] the letter last week " to "wrote",
            "We [swim] yesterday " to "swam",
            "They [run] in the park yesterday " to "ran",
            "I [drive] home last night " to "drove",
            "We [catch] the bus yesterday " to "caught",
            "I [sleep] well last night " to "slept",
            "We [speak] on the phone last week " to "spoke",
            "He [is] tired yesterday " to "was",
            "It [is] cold last night " to "was",
            "You [are] at home last night " to "were",
            "They [are] at work last week " to "were",
            "They [was] at home yesterday " to "were",
            "She [were] tired last night " to "was",
            "I [am] tired yesterday " to "was",
            "Done.  We [WALK] last night." to "WALKED",
            "Done!\nWe [Walk] home yesterday!" to "Walked",
            "I\t[go]\thome\tyesterday " to "went",
        )
        for ((sentence, replacement) in cases) assertCorrection(sentence, replacement)
    }

    @Test fun agreesAcrossPronounsAndVerbForms() {
        val verbPairs = listOf(
            "go" to "goes", "play" to "plays", "study" to "studies", "work" to "works",
            "have" to "has", "do" to "does", "watch" to "watches", "walk" to "walks",
        )
        for (subject in listOf("I", "You", "He", "She", "It", "We", "They")) {
            val thirdPerson = subject in setOf("He", "She", "It")
            for ((base, third) in verbPairs) {
                val valid = if (thirdPerson) third else base
                val invalid = if (thirdPerson) base else third
                assertCorrection("$subject [$invalid] ", valid)
                assertNoCorrection("$subject $valid ")
            }

            val presentBe = when {
                subject == "I" -> "am"
                thirdPerson -> "is"
                else -> "are"
            }
            val pastBe = if (subject == "I" || thirdPerson) "was" else "were"
            for ((forms, expected) in listOf(
                listOf("am", "is", "are") to presentBe,
                listOf("was", "were") to pastBe,
            )) {
                for (form in forms) {
                    if (form == expected) assertNoCorrection("$subject $form ")
                    else assertCorrection("$subject [$form] ", expected)
                }
            }
        }
    }

    @Test fun leavesCorrectAndAmbiguousSentencesAlone() {
        for (sentence in listOf(
            "I went to school yesterday", "We played chess last night", "They cleaned the kitchen last week",
            "I saw you yesterday", "I read the report yesterday", "I put it away yesterday",
            "I cut the rope yesterday", "They were at home last night", "He was tired yesterday",
            "She works every day", "He goes to school today", "I walk to work every day",
            "I see you today", "I work this morning", "We leave next week",
            "I remember yesterday", "I remember the lesson from yesterday",
            "I like the photos from last week", "I think about last night",
            "I refer to yesterday", "I prefer last week", "I want the report by yesterday",
            "I know you went yesterday", "I hope it works yesterday",
            "I know the shop closed yesterday", "I remember what happened yesterday",
            "I go to the restaurant that closed yesterday", "I go to town and eat yesterday",
            "I can go yesterday", "I will go yesterday", "I have been there yesterday",
            "He go?", "Did he go yesterday?", "I go to yesterday's meeting",
            "I go to yesterday’s meeting", "\"I go yesterday\"", "I go to 東京 yesterday",
            "John go yesterday", "I gO yesterday", "I made it to work yesterday",
            "", "    ",
        )) {
            assertNoCorrection(sentence)
        }
        assertNoCorrection("He go ", startsAtDocument = false)
        assertNoCorrection("go home yesterday", startsAtDocument = false)
        assertCorrection("… Previous sentence. I [buy] bread yesterday", "bought", startsAtDocument = false)
    }

    @Test fun leavesUnresolvedPastFormsAlone() {
        // The bundled table omits ambiguous spellings or senses such as learned/learnt and left/leaved.
        for (sentence in listOf(
            "I learn a new word yesterday", "We travel to Paris last week", "I leave early yesterday",
            "I come home yesterday", "I have a meeting yesterday",
        )) {
            assertNoCorrection(sentence)
        }
    }

    @Test fun everyBundledReplacementIsStableInItsOwnContext() {
        val contexts = listOf(
            "I " to " ", "He " to " ", "They " to " ",
            "I " to " yesterday ", "They " to " yesterday ",
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.assets.open("english/grammar/verb-forms.tsv").bufferedReader().useLines { rows ->
            for (row in rows) {
                val forms = row.split('\t').drop(1)
                assertEquals(contexts.size, forms.size)
                for (index in contexts.indices) {
                    val form = forms[index]
                    if (form != "-") {
                        val (prefix, suffix) = contexts[index]
                        assertNoCorrection(prefix + form + suffix)
                    }
                }
            }
        }
    }

    private fun assertCorrection(marked: String, replacement: String, startsAtDocument: Boolean = true) {
        val start = marked.indexOf('[')
        val end = marked.indexOf(']')
        val original = marked.substring(start + 1, end)
        val sentence = marked.removeRange(end, end + 1).removeRange(start, start + 1)
        val corrected = marked.replaceRange(start, end + 1, replacement)
        val result = forms.suggest(sentence, startsAtDocument)
        assertNotNull(sentence, result)
        result!!
        assertEquals(sentence, EnglishGrammarCorrection(start, end - 1, original, replacement), result)
        assertEquals(sentence, corrected, sentence.replaceRange(result.start, result.end, result.replacement))
        assertNoCorrection(corrected, startsAtDocument)
    }

    private fun assertNoCorrection(sentence: String, startsAtDocument: Boolean = true) {
        assertNull(sentence, forms.suggest(sentence, startsAtDocument))
    }

    companion object {
        private val forms by lazy {
            ApplicationProvider.getApplicationContext<Context>()
                .assets.open("english/grammar/verb-forms.tsv").reader(Charsets.UTF_8).use(EnglishVerbForms::read)
        }
    }
}
