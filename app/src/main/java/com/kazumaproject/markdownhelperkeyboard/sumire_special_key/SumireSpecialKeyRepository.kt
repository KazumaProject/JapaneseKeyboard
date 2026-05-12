package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideDao
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideDao
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface SumireSpecialKeyDataSource {
    fun observeAllActionOverrides(): Flow<List<SumireSpecialKeyActionOverrideEntity>>
    fun observeAllPlacementOverrides(): Flow<List<SumireSpecialKeyPlacementOverrideEntity>>
    fun observeActionOverrides(
        layoutType: String,
        inputMode: String
    ): Flow<List<SumireSpecialKeyActionOverrideEntity>>

    fun observeActionOverridesForKey(
        layoutType: String,
        inputMode: String,
        keyId: String
    ): Flow<List<SumireSpecialKeyActionOverrideEntity>>

    fun observePlacementOverrides(
        layoutType: String,
        inputMode: String
    ): Flow<List<SumireSpecialKeyPlacementOverrideEntity>>

    suspend fun upsertActionOverride(entity: SumireSpecialKeyActionOverrideEntity)
    suspend fun deleteActionDirection(
        layoutType: String,
        inputMode: String,
        keyId: String,
        direction: SumireSpecialKeyDirection
    )

    suspend fun deleteActionKey(layoutType: String, inputMode: String, keyId: String)
    suspend fun deleteAllActions(layoutType: String, inputMode: String)
    suspend fun upsertPlacementOverrides(entities: List<SumireSpecialKeyPlacementOverrideEntity>)
    suspend fun deletePlacementKey(layoutType: String, inputMode: String, keyId: String)
    suspend fun deleteAllPlacements(layoutType: String, inputMode: String)
}

@Singleton
class SumireSpecialKeyRepository @Inject constructor(
    private val actionDao: SumireSpecialKeyActionOverrideDao,
    private val placementDao: SumireSpecialKeyPlacementOverrideDao
) : SumireSpecialKeyDataSource {
    override fun observeAllActionOverrides(): Flow<List<SumireSpecialKeyActionOverrideEntity>> =
        actionDao.observeAll()

    override fun observeAllPlacementOverrides(): Flow<List<SumireSpecialKeyPlacementOverrideEntity>> =
        placementDao.observeAll()

    override fun observeActionOverrides(
        layoutType: String,
        inputMode: String
    ): Flow<List<SumireSpecialKeyActionOverrideEntity>> =
        actionDao.getByLayoutAndMode(layoutType, inputMode)

    override fun observeActionOverridesForKey(
        layoutType: String,
        inputMode: String,
        keyId: String
    ): Flow<List<SumireSpecialKeyActionOverrideEntity>> =
        actionDao.getByKey(layoutType, inputMode, keyId)

    override fun observePlacementOverrides(
        layoutType: String,
        inputMode: String
    ): Flow<List<SumireSpecialKeyPlacementOverrideEntity>> =
        placementDao.getByLayoutAndMode(layoutType, inputMode)

    override suspend fun upsertActionOverride(entity: SumireSpecialKeyActionOverrideEntity) {
        actionDao.upsert(entity)
    }

    override suspend fun deleteActionDirection(
        layoutType: String,
        inputMode: String,
        keyId: String,
        direction: SumireSpecialKeyDirection
    ) {
        actionDao.deleteDirection(layoutType, inputMode, keyId, direction.name)
    }

    override suspend fun deleteActionKey(layoutType: String, inputMode: String, keyId: String) {
        actionDao.deleteKey(layoutType, inputMode, keyId)
    }

    override suspend fun deleteAllActions(layoutType: String, inputMode: String) {
        actionDao.deleteLayoutMode(layoutType, inputMode)
    }

    override suspend fun upsertPlacementOverrides(
        entities: List<SumireSpecialKeyPlacementOverrideEntity>
    ) {
        placementDao.upsertAll(entities)
    }

    override suspend fun deletePlacementKey(layoutType: String, inputMode: String, keyId: String) {
        placementDao.deleteKey(layoutType, inputMode, keyId)
    }

    override suspend fun deleteAllPlacements(layoutType: String, inputMode: String) {
        placementDao.deleteLayoutMode(layoutType, inputMode)
    }
}

