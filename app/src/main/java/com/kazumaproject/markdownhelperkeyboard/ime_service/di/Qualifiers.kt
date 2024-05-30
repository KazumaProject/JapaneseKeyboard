package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import javax.inject.Qualifier

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ConnectionIds
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class Rank0ArrayLBSYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class Rank1ArrayLBSYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class Rank1ArrayIsLeafYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class Rank0ArrayTokenArrayBitvector
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class Rank1ArrayTokenArrayBitvector

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class Rank0ArrayTangoLBS

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class Rank1ArrayTangoLBS

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class YomiLBSBooleanArray
