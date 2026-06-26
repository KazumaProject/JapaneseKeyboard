package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcKotlinJapaneseConversionEngine
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import javax.inject.Inject

class JapaneseConversionEngineSelector @Inject constructor(
    private val appPreference: AppPreference,
    private val legacyEngine: LegacySumireJapaneseConversionEngine,
    private val mozcKotlinEngine: MozcKotlinJapaneseConversionEngine,
) {
    fun current(): JapaneseConversionEngine =
        when (appPreference.conversion_engine_preference) {
            "mozc_kotlin" -> mozcKotlinEngine
            else -> legacyEngine
        }
}
