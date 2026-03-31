package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemUserDictionaryFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val DIRECTORY_NAME = "system_user_dictionary"
        private const val YOMI_FILE_NAME = "yomi_system_user_dictionary.dat"
        private const val TANGO_FILE_NAME = "tango_system_user_dictionary.dat"
        private const val TOKEN_FILE_NAME = "token_system_user_dictionary.dat"
        private const val POS_TABLE_FILE_NAME = "pos_table_system_user_dictionary.dat"
        private const val META_FILE_NAME = "meta_system_user_dictionary.dat"
    }

    data class BuildMetadata(
        val entryCount: Int,
        val builtAt: Long,
    )

    val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME)

    val yomiFile: File
        get() = File(directory, YOMI_FILE_NAME)

    val tangoFile: File
        get() = File(directory, TANGO_FILE_NAME)

    val tokenFile: File
        get() = File(directory, TOKEN_FILE_NAME)

    val posTableFile: File
        get() = File(directory, POS_TABLE_FILE_NAME)

    private val metaFile: File
        get() = File(directory, META_FILE_NAME)

    fun ensureDirectory() {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    fun hasBuiltDictionary(): Boolean {
        return yomiFile.exists() && tangoFile.exists() && tokenFile.exists() && posTableFile.exists()
    }

    fun clearAll() {
        if (directory.exists()) {
            directory.deleteRecursively()
        }
    }

    fun writeMetadata(metadata: BuildMetadata) {
        ensureDirectory()
        ObjectOutputStream(BufferedOutputStream(FileOutputStream(metaFile))).use { out ->
            out.writeInt(metadata.entryCount)
            out.writeLong(metadata.builtAt)
        }
    }

    fun readMetadata(): BuildMetadata? {
        if (!metaFile.exists()) return null
        return runCatching {
            ObjectInputStream(BufferedInputStream(FileInputStream(metaFile))).use { input ->
                BuildMetadata(
                    entryCount = input.readInt(),
                    builtAt = input.readLong(),
                )
            }
        }.getOrNull()
    }
}
