package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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

    private fun dictionaryDataFiles(): List<File> = listOf(yomiFile, tangoFile, tokenFile, posTableFile)

    private fun allExportFiles(): List<File> = dictionaryDataFiles() + metaFile

    fun ensureDirectory() {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    fun hasBuiltDictionary(): Boolean {
        return dictionaryDataFiles().all { it.exists() }
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

    fun exportBuiltDictionary(outputStream: OutputStream): Boolean {
        if (!hasBuiltDictionary()) return false

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            allExportFiles().filter { it.exists() }.forEach { file ->
                zipOut.putNextEntry(ZipEntry(file.name))
                FileInputStream(file).use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
        return true
    }

    fun importBuiltDictionary(inputStream: InputStream): BuildMetadata? {
        val importedBytes = mutableMapOf<String, ByteArray>()
        ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val name = File(entry.name).name
                if (!entry.isDirectory && name in setOf(
                        YOMI_FILE_NAME,
                        TANGO_FILE_NAME,
                        TOKEN_FILE_NAME,
                        POS_TABLE_FILE_NAME,
                        META_FILE_NAME,
                    )
                ) {
                    importedBytes[name] = zipIn.readBytes()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        val hasAllRequiredFiles = listOf(
            YOMI_FILE_NAME,
            TANGO_FILE_NAME,
            TOKEN_FILE_NAME,
            POS_TABLE_FILE_NAME,
        ).all { importedBytes.containsKey(it) }
        if (!hasAllRequiredFiles) return null

        ensureDirectory()
        writeAtomically(yomiFile, importedBytes.getValue(YOMI_FILE_NAME))
        writeAtomically(tangoFile, importedBytes.getValue(TANGO_FILE_NAME))
        writeAtomically(tokenFile, importedBytes.getValue(TOKEN_FILE_NAME))
        writeAtomically(posTableFile, importedBytes.getValue(POS_TABLE_FILE_NAME))

        val importedMetadataBytes = importedBytes[META_FILE_NAME]
        if (importedMetadataBytes != null) {
            writeAtomically(metaFile, importedMetadataBytes)
        } else {
            writeMetadata(
                BuildMetadata(
                    entryCount = 0,
                    builtAt = System.currentTimeMillis(),
                ),
            )
        }

        return readMetadata()
    }

    private fun writeAtomically(targetFile: File, content: ByteArray) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        FileOutputStream(tempFile).use { output ->
            output.write(content)
            output.flush()
        }
        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
    }
}
