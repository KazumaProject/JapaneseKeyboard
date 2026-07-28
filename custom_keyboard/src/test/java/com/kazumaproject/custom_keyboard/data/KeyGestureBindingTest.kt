package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyGestureBindingTest {
    @Test
    fun normalCharacterKey_neverExposesPersistedDoubleTapBinding() {
        val key = key(
            normalAction = KeyAction.Text("a"),
            isSpecialKey = false,
            binding = DoubleTapBinding(KeyAction.Copy, DoubleTapPolicy.PROMOTE)
        )

        assertNull(key.effectiveDoubleTapBinding(KeyAction.Text("a")))
    }

    @Test
    fun shiftCapsLock_usesPromoteRegardlessOfPersistedPolicy() {
        val key = key(
            normalAction = KeyAction.ShiftKey,
            isSpecialKey = true,
            binding = DoubleTapBinding(KeyAction.CapLockKey, DoubleTapPolicy.EXCLUSIVE)
        )

        assertEquals(
            DoubleTapPolicy.PROMOTE,
            key.effectiveDoubleTapBinding(KeyAction.ShiftKey)?.policy
        )
    }

    @Test
    fun otherSpecialShortcut_usesExclusiveRegardlessOfPersistedPolicy() {
        val key = key(
            normalAction = KeyAction.SelectAll,
            isSpecialKey = true,
            binding = DoubleTapBinding(KeyAction.Copy, DoubleTapPolicy.PROMOTE)
        )

        assertEquals(
            DoubleTapPolicy.EXCLUSIVE,
            key.effectiveDoubleTapBinding(KeyAction.SelectAll)?.policy
        )
    }

    private fun key(
        normalAction: KeyAction,
        isSpecialKey: Boolean,
        binding: DoubleTapBinding
    ) = KeyData(
        label = "test",
        row = 0,
        column = 0,
        isFlickable = false,
        action = normalAction,
        isSpecialKey = isSpecialKey,
        keyId = "test",
        keyType = KeyType.NORMAL,
        doubleTapBinding = binding
    )
}
