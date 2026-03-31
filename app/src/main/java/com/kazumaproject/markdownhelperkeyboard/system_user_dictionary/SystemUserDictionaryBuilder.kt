package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary

import com.kazumaproject.Louds.Converter
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.dictionary.models.Dictionary
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryEntry
import com.kazumaproject.prefix.PrefixTree
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.util.SortedMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemUserDictionaryBuilder @Inject constructor(
    private val fileManager: SystemUserDictionaryFileManager,
) {

    fun build(entries: List<SystemUserDictionaryEntry>): SystemUserDictionaryFileManager.BuildMetadata {
        if (entries.isEmpty()) {
            fileManager.clearAll()
            return SystemUserDictionaryFileManager.BuildMetadata(
                entryCount = 0,
                builtAt = System.currentTimeMillis(),
            )
        }

        fileManager.ensureDirectory()

        val dictionaryMap = entries
            .groupBy { it.yomi }
            .mapValues { (_, values) ->
                values.map { entry ->
                    Dictionary(
                        yomi = entry.yomi,
                        leftId = entry.leftId.toShort(),
                        rightId = entry.rightId.toShort(),
                        cost = entry.score.toShort(),
                        tango = entry.tango,
                    )
                }
            }
            .toSortedMap(compareBy<String> { it.length }.thenBy { it })

        val yomiTree = PrefixTreeWithTermId()
        val tangoTree = PrefixTree()
        dictionaryMap.forEach { (yomi, dictionaries) ->
            yomiTree.insert(yomi)
            dictionaries.forEach { dictionary ->
                if (!dictionary.tango.isKanaOnly()) {
                    tangoTree.insert(dictionary.tango)
                }
            }
        }

        val yomiLouds = ConverterWithTermId().convert(yomiTree.root).apply { convertListToBitSet() }
        val tangoLouds = Converter().convert(tangoTree.root).apply { convertListToBitSet() }
        val posIndexMap = buildPosIndexMap(dictionaryMap)

        writeAtomically(fileManager.yomiFile) { file ->
            ObjectOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
                yomiLouds.writeExternalNotCompress(out)
            }
        }
        writeAtomically(fileManager.tangoFile) { file ->
            ObjectOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
                tangoLouds.writeExternalNotCompress(out)
            }
        }
        writeAtomically(fileManager.tokenFile) { file ->
            ObjectOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
                TokenArray().buildTokenArray(dictionaryMap, tangoLouds, out, posIndexMap)
            }
        }
        writeAtomically(fileManager.posTableFile) { file ->
            ObjectOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
                writePosTable(out, posIndexMap)
            }
        }

        val metadata = SystemUserDictionaryFileManager.BuildMetadata(
            entryCount = entries.size,
            builtAt = System.currentTimeMillis(),
        )
        fileManager.writeMetadata(metadata)
        return metadata
    }

    private fun writePosTable(
        out: ObjectOutputStream,
        posIndexMap: Map<Pair<Short, Short>, Int>,
    ) {
        val orderedPairs = posIndexMap.entries
            .sortedBy { it.value }
            .map { it.key }
        out.writeObject(orderedPairs.map { it.first }.toShortArray())
        out.writeObject(orderedPairs.map { it.second }.toShortArray())
    }

    private fun buildPosIndexMap(
        dictionaryMap: SortedMap<String, List<Dictionary>>,
    ): Map<Pair<Short, Short>, Int> {
        val result = LinkedHashMap<Pair<Short, Short>, Int>()
        dictionaryMap.values.flatten().forEach { dictionary ->
            val key = dictionary.leftId to dictionary.rightId
            if (!result.containsKey(key)) {
                result[key] = result.size
            }
        }
        return result
    }

    private fun writeAtomically(targetFile: File, writer: (File) -> Unit) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        writer(tempFile)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
    }

    private fun String.isKanaOnly(): Boolean {
        return isNotEmpty() && all { char ->
            char in 'ぁ'..'ゖ' || char in 'ァ'..'ヶ' || char == 'ー'
        }
    }
}
