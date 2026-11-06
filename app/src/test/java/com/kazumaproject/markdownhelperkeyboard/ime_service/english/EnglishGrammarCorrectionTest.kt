package com.kazumaproject.markdownhelperkeyboard.ime_service.english

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnglishGrammarCorrectionTest {
    private val forms = ApplicationProvider.getApplicationContext<Context>()
        .assets.open("english/grammar/verb-forms.tsv").reader().use(EnglishVerbForms::read)

    @Test fun usesPastTimeAndKeepsTheSourceRange() {
        assertEquals(
            EnglishGrammarCorrection(2, 4, "go", "went"),
            forms.suggest("I go to school yesterday ", startsAtDocument = true),
        )
        assertEquals(
            EnglishGrammarCorrection(10, 14, "WALK", "WALKED"),
            forms.suggest("Done.  We WALK last night.", startsAtDocument = false),
        )
    }

    @Test fun agreesWithThePronoun() {
        for ((text, expected) in listOf("He go " to "goes", "They goes " to "go", "I is " to "am",
            "She are " to "is", "We is " to "are")) {
            assertEquals(text, expected, forms.suggest(text, startsAtDocument = true)?.replacement)
        }
    }

    @Test fun skipsAlreadyValidOrAmbiguousText() {
        for (text in listOf(
            "I saw her yesterday", "He works ", "I can go yesterday", "He go?",
            "I go to yesterday's meeting", "I go to the restaurant that closed yesterday",
            "I go to town and eat yesterday", "I go to 東京 yesterday",
        )) {
            assertNull(text, forms.suggest(text, startsAtDocument = true))
        }
        assertNull(forms.suggest("He go ", startsAtDocument = false))
    }
}
