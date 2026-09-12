package com.kazumaproject.markdownhelperkeyboard

import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets instrumentation seed and clean up fixtures in the same Room instance used by the IME. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BunsetsuTestDatabaseEntryPoint {
    fun database(): AppDatabase
}
