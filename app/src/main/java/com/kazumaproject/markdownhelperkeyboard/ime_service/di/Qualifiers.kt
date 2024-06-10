package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import javax.inject.Qualifier

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ConnectionIds

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemYomiTrie

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemTangoTrie

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemTokenArray
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemRank0ArrayLBSYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemRank1ArrayLBSYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemRank1ArrayIsLeafYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemRank0ArrayTokenArrayBitvector
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemRank1ArrayTokenArrayBitvector

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemRank0ArrayTangoLBS

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemRank1ArrayTangoLBS

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SystemYomiLBSBooleanArray

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiYomiTrie

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiTangoTrie

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiTokenArray
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiRank0ArrayLBSYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiRank1ArrayLBSYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiRank1ArrayIsLeafYomi
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiRank0ArrayTokenArrayBitvector
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiRank1ArrayTokenArrayBitvector

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiRank0ArrayTangoLBS

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiRank1ArrayTangoLBS

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SingleKanjiYomiLBSBooleanArray
