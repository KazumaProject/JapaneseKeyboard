package com.kazumaproject.core.domain.physical_keyboard

import com.kazumaproject.core.data.floating_candidate.CandidateInputRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingCandidateCompositionResolverTest {
    @Test
    fun resolve_replacesPrefix_andPreservesFollowingInput() {
        val result = FloatingCandidateCompositionResolver.resolve(
            originalInput = "あいたじかんでＩＴぱすぽ－とのべんきょうをする",
            replacementText = "空いた時間で",
            inputRange = CandidateInputRange(0, 7),
        )

        requireNotNull(result)
        assertEquals(
            "空いた時間でＩＴぱすぽ－とのべんきょうをする",
            result.text,
        )
        assertEquals("ＩＴぱすぽ－とのべんきょうをする", result.tail)
        assertEquals(0, result.selectedTextStart)
        assertEquals("空いた時間で".length, result.selectedTextEndExclusive)
    }

    @Test
    fun resolve_replacesNonPrefixRange_withoutDroppingEitherSide() {
        val result = FloatingCandidateCompositionResolver.resolve(
            originalInput = "ここでは",
            replacementText = "でわ",
            inputRange = CandidateInputRange(2, 3),
        )

        requireNotNull(result)
        assertEquals("ここでわは", result.text)
        assertEquals("は", result.tail)
        assertEquals(2, result.selectedTextStart)
        assertEquals(4, result.selectedTextEndExclusive)
    }

    @Test
    fun resolve_usesUtf16SourceOffsets_notOutputLength() {
        val result = FloatingCandidateCompositionResolver.resolve(
            originalInput = "😀かな",
            replacementText = "絵",
            inputRange = CandidateInputRange(2, 3),
        )

        requireNotNull(result)
        assertEquals("😀絵な", result.text)
        assertEquals("な", result.tail)
    }

    @Test
    fun resolve_returnsNull_forInvalidRange_withoutCreatingShortText() {
        assertNull(
            FloatingCandidateCompositionResolver.resolve(
                originalInput = "ここでは",
                replacementText = "此処",
                inputRange = CandidateInputRange(0, 100),
            )
        )
    }
}
