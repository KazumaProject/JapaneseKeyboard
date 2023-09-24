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
annotation class MainImmediateDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class InputBackGroundDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class KeyInputDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SuggestionDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class CursorMoveDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class DeleteLongDispatcher