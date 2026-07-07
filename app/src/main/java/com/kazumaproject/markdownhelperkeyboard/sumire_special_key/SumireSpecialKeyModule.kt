package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui.AppSumireSpecialKeyActionEditorDefaultActionsProvider
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui.SumireSpecialKeyActionEditorDefaultActionsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SumireSpecialKeyModule {
    @Binds
    @Singleton
    abstract fun bindSumireSpecialKeyDataSource(
        repository: SumireSpecialKeyRepository
    ): SumireSpecialKeyDataSource

    @Binds
    abstract fun bindSumireSpecialKeyActionEditorDefaultActionsProvider(
        provider: AppSumireSpecialKeyActionEditorDefaultActionsProvider
    ): SumireSpecialKeyActionEditorDefaultActionsProvider
}
