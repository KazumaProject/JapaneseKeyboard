package com.kazumaproject.markdownhelperkeyboard.setting_activity

import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

internal data class GojuonKeyboardTypeMigrationResult(
    val keyboardOrder: List<KeyboardType>,
    val selectedPosition: Int,
)

internal object GojuonKeyboardTypeMigration {
    fun resolve(
        legacyGojuonEnabled: Boolean,
        keyboardOrder: List<KeyboardType>,
        selectedPosition: Int,
    ): GojuonKeyboardTypeMigrationResult {
        val selectedType = keyboardOrder.getOrNull(selectedPosition)
        val migratedSelectedType = selectedType?.migrateLegacyType(legacyGojuonEnabled)
        val migratedOrder = keyboardOrder
            .map { it.migrateLegacyType(legacyGojuonEnabled) }
            .distinct()
        val migratedPosition = if (migratedOrder.isEmpty()) {
            0
        } else {
            migratedSelectedType
                ?.let(migratedOrder::indexOf)
                ?.takeIf { it >= 0 }
                ?: selectedPosition.coerceIn(0, migratedOrder.lastIndex)
        }

        return GojuonKeyboardTypeMigrationResult(
            keyboardOrder = migratedOrder,
            selectedPosition = migratedPosition,
        )
    }

    private fun KeyboardType.migrateLegacyType(legacyGojuonEnabled: Boolean): KeyboardType {
        return if (legacyGojuonEnabled && this == KeyboardType.TENKEY) {
            KeyboardType.GOJUON
        } else {
            this
        }
    }
}
