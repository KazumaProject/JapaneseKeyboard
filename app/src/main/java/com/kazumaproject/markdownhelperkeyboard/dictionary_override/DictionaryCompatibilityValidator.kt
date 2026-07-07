package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import androidx.annotation.StringRes
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import timber.log.Timber
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class DictionaryCompatibilityResult {
    data object Compatible : DictionaryCompatibilityResult()
    data class Incompatible(
        val problems: List<DictionaryCompatibilityProblem>,
    ) : DictionaryCompatibilityResult()

    val isCompatible: Boolean
        get() = this is Compatible
}

data class DictionaryCompatibilityProblem(
    val affectedCategory: DictionaryCategory?,
    val affectedFileKey: DictionaryFileKey?,
    val requiredFileKeys: List<DictionaryFileKey>,
    val messageForLog: String,
    @StringRes val messageResId: Int,
    val messageArgs: List<Any> = emptyList(),
)

class DictionaryCompatibilityException(
    val result: DictionaryCompatibilityResult.Incompatible,
) : IllegalStateException(
    result.problems.joinToString(separator = "\n") { it.messageForLog }
)

@Singleton
class DictionaryCompatibilityValidator @Inject constructor(
    private val resolver: DictionarySourceResolver,
) {
    fun validateActiveState(
        forceOverrideKeys: Set<DictionaryFileKey> = emptySet(),
    ): DictionaryCompatibilityResult {
        val categories = activeJapaneseTokenCategories()
        return validateSources(
            tokenCategories = categories,
            sourceOpener = { key ->
                resolver.openForKeyInPlan(
                    key = key,
                    forceOverrideKeys = forceOverrideKeys,
                )
            },
        )
    }

    fun validateCategoryReplacement(
        category: DictionaryCategory,
    ): DictionaryCompatibilityResult {
        if (!category.hasJapaneseTokenArray()) return DictionaryCompatibilityResult.Compatible
        return validateSources(
            tokenCategories = listOf(category),
            sourceOpener = { key ->
                resolver.openForKeyInPlan(
                    key = key,
                    forceOverrideCategories = setOf(category),
                )
            },
        )
    }

    fun requireActiveStateCompatible() {
        when (val result = validateActiveState()) {
            DictionaryCompatibilityResult.Compatible -> Unit
            is DictionaryCompatibilityResult.Incompatible -> {
                result.problems.forEach {
                    Timber.w("Dictionary compatibility check failed: %s", it.messageForLog)
                }
                throw DictionaryCompatibilityException(result)
            }
        }
    }

    private fun activeJapaneseTokenCategories(): List<DictionaryCategory> =
        DictionaryFileSpecs.tripleCategories()
            .filter { it.hasJapaneseTokenArray() }
            .filter { category ->
                if (category.isDisableableBundledDictionary()) {
                    resolver.resolveCategoryLoadState(category) != DictionaryCategoryLoadState.Disabled
                } else {
                    true
                }
            }

    companion object {
        fun validateSources(
            tokenCategories: List<DictionaryCategory>,
            sourceOpener: (DictionaryFileKey) -> InputStream,
        ): DictionaryCompatibilityResult {
            val problems = mutableListOf<DictionaryCompatibilityProblem>()
            val posTableStats = readPosTableStats(sourceOpener, problems)
            val connectionStats = readConnectionIdStats(sourceOpener, problems)
            val connectionMatrixSize = connectionStats?.matrixSize

            if (posTableStats != null) {
                if (posTableStats.leftRowCount != posTableStats.rightRowCount) {
                    problems += DictionaryCompatibilityProblem(
                        affectedCategory = DictionaryCategory.COMMON,
                        affectedFileKey = DictionaryFileKey.POS_TABLE,
                        requiredFileKeys = listOf(DictionaryFileKey.POS_TABLE),
                        messageForLog = "pos_table.dat left/right row count mismatch: left=${posTableStats.leftRowCount}, right=${posTableStats.rightRowCount}",
                        messageResId = R.string.external_dictionary_compat_pos_table_row_mismatch,
                        messageArgs = listOf(posTableStats.leftRowCount, posTableStats.rightRowCount),
                    )
                }
                if (connectionMatrixSize != null && posTableStats.maxLeftId >= connectionMatrixSize) {
                    problems += DictionaryCompatibilityProblem(
                        affectedCategory = DictionaryCategory.COMMON,
                        affectedFileKey = DictionaryFileKey.POS_TABLE,
                        requiredFileKeys = listOf(DictionaryFileKey.POS_TABLE, DictionaryFileKey.CONNECTION_ID),
                        messageForLog = "pos_table.dat max leftId ${posTableStats.maxLeftId} exceeds connection matrix id range ${connectionMatrixSize - 1}",
                        messageResId = R.string.external_dictionary_compat_pos_table_id_out_of_range,
                        messageArgs = listOf(posTableStats.maxLeftId, connectionMatrixSize),
                    )
                }
                if (connectionMatrixSize != null && posTableStats.maxRightId >= connectionMatrixSize) {
                    problems += DictionaryCompatibilityProblem(
                        affectedCategory = DictionaryCategory.COMMON,
                        affectedFileKey = DictionaryFileKey.POS_TABLE,
                        requiredFileKeys = listOf(DictionaryFileKey.POS_TABLE, DictionaryFileKey.CONNECTION_ID),
                        messageForLog = "pos_table.dat max rightId ${posTableStats.maxRightId} exceeds connection matrix id range ${connectionMatrixSize - 1}",
                        messageResId = R.string.external_dictionary_compat_pos_table_id_out_of_range,
                        messageArgs = listOf(posTableStats.maxRightId, connectionMatrixSize),
                    )
                }
            }

            if (connectionStats != null && connectionStats.matrixSize == null) {
                problems += DictionaryCompatibilityProblem(
                    affectedCategory = DictionaryCategory.COMMON,
                    affectedFileKey = DictionaryFileKey.CONNECTION_ID,
                    requiredFileKeys = listOf(DictionaryFileKey.CONNECTION_ID),
                    messageForLog = "connectionId.dat size ${connectionStats.shortArraySize} is not a valid square matrix",
                    messageResId = R.string.external_dictionary_compat_connection_id_invalid_size,
                    messageArgs = listOf(connectionStats.shortArraySize),
                )
            }

            if (posTableStats != null && posTableStats.leftRowCount == posTableStats.rightRowCount) {
                tokenCategories.forEach { category ->
                    val tokenKey = DictionaryFileSpecs.forCategory(category)
                        .firstOrNull { it.role == DictionaryFileRole.TOKEN }
                        ?.key
                        ?: return@forEach
                    val tokenStats = readTokenStats(sourceOpener, tokenKey) ?: return@forEach
                    if (tokenStats.minPosTableIndex < 0) {
                        problems += DictionaryCompatibilityProblem(
                            affectedCategory = category,
                            affectedFileKey = tokenKey,
                            requiredFileKeys = listOf(DictionaryFileKey.POS_TABLE, DictionaryFileKey.CONNECTION_ID),
                            messageForLog = "$tokenKey contains negative posTableIndex ${tokenStats.minPosTableIndex}",
                            messageResId = R.string.external_dictionary_compat_token_negative_pos_index,
                        )
                    }
                    if (tokenStats.maxPosTableIndex >= posTableStats.leftRowCount) {
                        problems += DictionaryCompatibilityProblem(
                            affectedCategory = category,
                            affectedFileKey = tokenKey,
                            requiredFileKeys = listOf(DictionaryFileKey.POS_TABLE, DictionaryFileKey.CONNECTION_ID),
                            messageForLog = "$tokenKey requires pos_table.dat with at least ${tokenStats.maxPosTableIndex + 1} rows, but current pos_table.dat has ${posTableStats.leftRowCount} rows",
                            messageResId = R.string.external_dictionary_compat_token_requires_pos_table_rows,
                            messageArgs = listOf(tokenStats.maxPosTableIndex + 1, posTableStats.leftRowCount),
                        )
                    }
                }
            }

            return if (problems.isEmpty()) {
                DictionaryCompatibilityResult.Compatible
            } else {
                DictionaryCompatibilityResult.Incompatible(problems)
            }
        }

        private fun readTokenStats(
            sourceOpener: (DictionaryFileKey) -> InputStream,
            key: DictionaryFileKey,
        ): TokenStats? =
            runCatching {
                sourceOpener(key).use { input ->
                    DictionaryBinaryReader.openZipAwareObject(input, key.name).use { objectInput ->
                        val tokenArray = TokenArray()
                        tokenArray.readExternal(objectInput)
                        TokenStats(
                            minPosTableIndex = tokenArray.minPosTableIndex(),
                            maxPosTableIndex = tokenArray.maxPosTableIndex(),
                        )
                    }
                }
            }.onFailure {
                Timber.w(it, "Skipping compatibility check for unreadable token file: %s", key)
            }.getOrNull()

        private fun readPosTableStats(
            sourceOpener: (DictionaryFileKey) -> InputStream,
            problems: MutableList<DictionaryCompatibilityProblem>,
        ): PosTableStats? =
            runCatching {
                sourceOpener(DictionaryFileKey.POS_TABLE).use { input ->
                    DictionaryBinaryReader.openZipAwareObject(input, DictionaryFileKey.POS_TABLE.name).use { objectInput ->
                        val tokenArray = TokenArray()
                        tokenArray.readPOSTable(objectInput)
                        val leftIds = tokenArray.leftIds
                        val rightIds = tokenArray.rightIds
                        PosTableStats(
                            leftRowCount = leftIds.size,
                            rightRowCount = rightIds.size,
                            maxLeftId = leftIds.maxOrNull()?.toInt() ?: -1,
                            maxRightId = rightIds.maxOrNull()?.toInt() ?: -1,
                        )
                    }
                }
            }.getOrElse { error ->
                problems += DictionaryCompatibilityProblem(
                    affectedCategory = DictionaryCategory.COMMON,
                    affectedFileKey = DictionaryFileKey.POS_TABLE,
                    requiredFileKeys = listOf(DictionaryFileKey.POS_TABLE),
                    messageForLog = "pos_table.dat could not be read: ${error.message ?: error::class.java.simpleName}",
                    messageResId = R.string.external_dictionary_compat_pos_table_unreadable,
                )
                null
            }

        private fun readConnectionIdStats(
            sourceOpener: (DictionaryFileKey) -> InputStream,
            problems: MutableList<DictionaryCompatibilityProblem>,
        ): ConnectionIdStats? =
            runCatching {
                sourceOpener(DictionaryFileKey.CONNECTION_ID).use { input ->
                    DictionaryBinaryReader.openZipAwareRaw(input, DictionaryFileKey.CONNECTION_ID.name).use { raw ->
                        val size = ConnectionIdBuilder().readShortArrayFromBytes(raw).size
                        ConnectionIdStats(
                            shortArraySize = size,
                            matrixSize = ConnectionMatrix.inferMatrixSize(size),
                        )
                    }
                }
            }.getOrElse { error ->
                problems += DictionaryCompatibilityProblem(
                    affectedCategory = DictionaryCategory.COMMON,
                    affectedFileKey = DictionaryFileKey.CONNECTION_ID,
                    requiredFileKeys = listOf(DictionaryFileKey.CONNECTION_ID),
                    messageForLog = "connectionId.dat could not be read: ${error.message ?: error::class.java.simpleName}",
                    messageResId = R.string.external_dictionary_compat_connection_id_unreadable,
                )
                null
            }
    }
}

private data class TokenStats(
    val minPosTableIndex: Int,
    val maxPosTableIndex: Int,
)

private data class PosTableStats(
    val leftRowCount: Int,
    val rightRowCount: Int,
    val maxLeftId: Int,
    val maxRightId: Int,
)

private data class ConnectionIdStats(
    val shortArraySize: Int,
    val matrixSize: Int?,
)

private fun DictionaryCategory.hasJapaneseTokenArray(): Boolean =
    this != DictionaryCategory.COMMON && this != DictionaryCategory.ENGLISH
