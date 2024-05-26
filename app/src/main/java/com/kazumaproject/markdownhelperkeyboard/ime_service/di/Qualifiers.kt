package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import javax.inject.Qualifier

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ConnectionIds
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class LBSYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class IsLeafYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class TokenArrayBitvector
