package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.NgramRuleRepository
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.NgramRuleScorerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface KanaKanjiEngineEntryPoint {
    fun kanaKanjiEngine(): KanaKanjiEngine
    fun userDictionaryRepository(): UserDictionaryRepository
    fun ngramRuleRepository(): NgramRuleRepository
    fun ngramRuleScorerManager(): NgramRuleScorerManager
}
