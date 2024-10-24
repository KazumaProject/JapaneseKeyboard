package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import javax.inject.Qualifier

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class EmojiList

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class EmoticonList

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SymbolList