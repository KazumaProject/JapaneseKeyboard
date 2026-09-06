package com.kazumaproject.core.domain.physical_keyboard

import com.kazumaproject.core.data.floating_candidate.CandidateInputRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingCandidateCompositionResolverTest {
    @Test
    fun resolve_replacesSelectedPrefix_andPreservesTail() {
        val result = FloatingCandidateCompositionResolver.resolve(
            originalInput = "ここでは",
            replacementText = "此処",
            inputRange = CandidateInputRange(0, 2),
        )

        requireNotNull(result)
        assertEquals("此処では", result.text)
        assertEquals("では", result.tail)
        assertEquals(0, result.selectedTextStart)
        assertEquals(2, result.selectedTextEndExclusive)
    }

    @Test
    fun resolve_issue974_preservesAllInputWhenCandidateOutputIsShorter() {
        val originalInput = "あいたじかんでＩＴぱすぽ－とのべんきょうをする"
        val result = FloatingCandidateCompositionResolver.resolve(
            originalInput = originalInput,
            replacementText = "空いた時間で",
            inputRange = CandidateInputRange(0, 7),
        )

        requireNotNull(result)
        assertEquals(
            "空いた時間でＩＴぱすぽ－とのべんきょうをする",
            result.text,
        )
        assertEquals("ＩＴぱすぽ－とのべんきょうをする", result.tail)
        assertEquals(16, result.tail.length)
        assertEquals(23, originalInput.length)
    }

    @Test
    fun resolve_supportsNonPrefixRange_withoutDroppingPrefixOrTail() {
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
    fun resolve_usesUtf16Offsets_notOutputLength() {
        val result = FloatingCandidateCompositionResolver.resolve(
            originalInput = "きょうは",
            replacementText = "今日",
            inputRange = CandidateInputRange(0, 3),
        )

        requireNotNull(result)
        assertEquals("今日は", result.text)
        assertEquals("は", result.tail)
    }

    @Test
    fun resolve_returnsNull_forInvalidRange_insteadOfDroppingInput() {
        assertNull(
            FloatingCandidateCompositionResolver.resolve(
                originalInput = "ここでは",
                replacementText = "此処",
                inputRange = CandidateInputRange(0, 100),
            )
        )
    }
}
