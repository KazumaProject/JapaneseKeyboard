package com.kazumaproject.markdownhelperkeyboard.variant

import com.kazumaproject.markdownhelperkeyboard.BuildConfig

object AppVariantConfig {
    val hasZenz: Boolean = BuildConfig.HAS_ZENZ
    val hasGemma: Boolean = BuildConfig.HAS_GEMMA
    val isFdroid: Boolean = BuildConfig.IS_FDROID
    val edition: String = BuildConfig.APP_EDITION
    val distributionChannel: String = BuildConfig.DIST_CHANNEL
}
