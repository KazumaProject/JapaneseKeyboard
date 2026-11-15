package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface KeyboardPreviewDependencies {
    fun keyboards(): KeyboardRepository
    fun sumire(): SumireSpecialKeyRepository
    fun gemma(): com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
    fun fonts(): com.kazumaproject.markdownhelperkeyboard.local_font.LocalFontRepository
    fun shortcuts(): ShortcutRepository
}
