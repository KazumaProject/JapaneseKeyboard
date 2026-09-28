package com.kazumaproject.markdownhelperkeyboard.converter.number

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NumberCandidatePresenterDeviceTest {

    @Test
    fun leadingZeroPresentationRemainsStableWhenAppliedAgain() {
        val input = "0012"
        val candidate = Candidate(
            string = input,
            type = 1,
            length = "number".length.toUByte(),
            score = 0,
            yomi = "number",
        )
        val segments = mapOf(
            input to listOf(
                CandidateConversionSegment(
                    inputStart = 0,
                    inputEnd = input.length,
                    output = input,
                    reading = "number",
                    leftId = 2044,
                    rightId = 2044,
                ),
            ),
        )
        val config = NumberPresentationConfig()
        val first = NumberCandidatePresenter.present(listOf(candidate), segments, config)
        val expected = listOf("0012", "００１２", "〇〇一二")

        assertEquals(expected, first.candidates.map(Candidate::string))
        assertEquals(
            listOf(NumberSpan(0, 4, 0, 4, "0012", digitSequence = true)),
            first.candidates.single { it.string == "〇〇一二" }.numberMetadata?.numericSpans,
        )

        val second = NumberCandidatePresenter.present(
            first.candidates,
            first.segmentsByCandidateString,
            config,
        )
        assertEquals(expected, second.candidates.map(Candidate::string))
    }
}
