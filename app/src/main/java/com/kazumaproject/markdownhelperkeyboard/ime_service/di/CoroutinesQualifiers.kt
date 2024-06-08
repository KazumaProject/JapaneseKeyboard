package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import javax.inject.Qualifier

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class DefaultDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class IoDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class MainDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class InputBackGroundDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SuggestionDispatcher
