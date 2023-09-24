package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import javax.inject.Qualifier

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class PopUpWindowTop

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class PopUpWindowLeft

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class PopUpWindowBottom

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class PopUpWindowRight

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class PopUpWindowCenter

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class PopUpBubbleLayoutCenter

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class PopUpTextCenter

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class PopUpTextActive