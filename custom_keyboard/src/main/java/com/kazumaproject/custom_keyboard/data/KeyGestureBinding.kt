package com.kazumaproject.custom_keyboard.data

/**
 * Internal strategy for resolving a special key's first tap.
 *
 * This remains serialized for backup compatibility, but it is deliberately not user-configurable:
 * Shift uses [PROMOTE], while every other supported shortcut uses [EXCLUSIVE].
 */
enum class DoubleTapPolicy(val serializedName: String) {
    PROMOTE("PROMOTE"),
    EXCLUSIVE("EXCLUSIVE");

    companion object {
        fun fromSerializedName(value: String?): DoubleTapPolicy? =
            entries.firstOrNull { it.serializedName == value }
    }
}

data class DoubleTapBinding(
    val action: KeyAction,
    val policy: DoubleTapPolicy = DoubleTapPolicy.EXCLUSIVE
)

/**
 * Normal character keys must dispatch repeated taps as repeated input, never as a hidden gesture.
 * Special keys are the only keys that may own a double-tap shortcut.
 */
val KeyData.supportsDoubleTap: Boolean
    get() = isSpecialKey

fun automaticDoubleTapPolicy(
    normalAction: KeyAction?,
    doubleTapAction: KeyAction
): DoubleTapPolicy =
    if (
        normalAction == KeyAction.ShiftKey &&
        doubleTapAction == KeyAction.CapLockKey
    ) {
        DoubleTapPolicy.PROMOTE
    } else {
        DoubleTapPolicy.EXCLUSIVE
    }

/**
 * Resolves persisted bindings through the current capability and policy rules.
 *
 * Runtime callers use the action that was actually committed so old or imported policy values
 * cannot re-enable immediate execution for non-Shift shortcuts.
 */
fun KeyData.effectiveDoubleTapBinding(normalAction: KeyAction?): DoubleTapBinding? =
    doubleTapBinding
        ?.takeIf { supportsDoubleTap }
        ?.let { binding ->
            binding.copy(
                policy = automaticDoubleTapPolicy(
                    normalAction = normalAction,
                    doubleTapAction = binding.action
                )
            )
        }
