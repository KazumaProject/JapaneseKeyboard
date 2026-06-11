package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceConvertCursorMovePolicyTest {
    @Test
    fun disabledPreferenceRejectsEverySpaceConvertLongPressSourceWhenInputStringExists() {
        SpaceConvertCursorMoveSource.entries.forEach { source ->
            assertFalse(
                source.name,
                SpaceConvertCursorMovePolicy.shouldEnterCursorMoveMode(
                    conversionKeySwipeCursorMovePreference = false,
                    hasInputString = true,
                    source = source
                )
            )
        }
    }

    @Test
    fun missingPreferenceRejectsEverySpaceConvertLongPressSourceWhenInputStringExists() {
        SpaceConvertCursorMoveSource.entries.forEach { source ->
            assertFalse(
                source.name,
                SpaceConvertCursorMovePolicy.shouldEnterCursorMoveMode(
                    conversionKeySwipeCursorMovePreference = null,
                    hasInputString = true,
                    source = source
                )
            )
        }
    }

    @Test
    fun enabledPreferenceAllowsEverySpaceConvertLongPressSourceWhenInputStringExists() {
        SpaceConvertCursorMoveSource.entries.forEach { source ->
            assertTrue(
                source.name,
                SpaceConvertCursorMovePolicy.shouldEnterCursorMoveMode(
                    conversionKeySwipeCursorMovePreference = true,
                    hasInputString = true,
                    source = source
                )
            )
        }
    }

    @Test
    fun emptyInputStringAllowsEverySpaceConvertLongPressSourceRegardlessOfPreference() {
        listOf(false, null, true).forEach { preference ->
            SpaceConvertCursorMoveSource.entries.forEach { source ->
                assertTrue(
                    "${source.name}: $preference",
                    SpaceConvertCursorMovePolicy.shouldEnterCursorMoveMode(
                        conversionKeySwipeCursorMovePreference = preference,
                        hasInputString = false,
                        source = source
                    )
                )
            }
        }
    }
}
