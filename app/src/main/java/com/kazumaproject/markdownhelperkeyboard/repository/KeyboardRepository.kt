package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.copyWithItems
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.custom_keyboard.data.toCircularFlickDirection
import com.kazumaproject.custom_keyboard.data.toKeyItem
import com.kazumaproject.custom_keyboard.data.usesFlexiblePlacement
import com.kazumaproject.custom_keyboard.data.withCanonicalFlexibleBounds
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.LongPressFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.SpacerDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepLongPressMappingEntity
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toDbStrings
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toFlickAction
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.ImportableKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutImportError
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutImportResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private data class DbKeyboardLayoutParts(
    val keys: List<KeyDefinition>,
    val flicksMap: Map<String, List<FlickMapping>>,
    val circularFlicksMap: Map<String, List<CircularFlickMapping>>,
    val twoStepMap: Map<String, List<TwoStepFlickMapping>>,
    val longPressFlicksMap: Map<String, List<LongPressFlickMapping>>,
    val twoStepLongPressMap: Map<String, List<TwoStepLongPressMappingEntity>>,
    val spacers: List<SpacerDefinition> = emptyList()
)

/**
 * `saveLayout(id = X)` で渡された X が DB 上に存在しなかった場合の例外。
 *
 * 旧実装は黙って新規作成にフォールバックして stableId を再生成していたため、
 * MoveToCustomKeyboard の参照が壊れる原因になっていた。新実装では明示的に
 * 例外を投げ、ViewModel 側で握り潰すかリカバリーするかを選択させる。
 */
class LayoutNotFoundException(val layoutId: Long) : NoSuchElementException(
    "CustomKeyboardLayout(layoutId=$layoutId) does not exist; cannot update."
)

/**
 * MoveToCustomKeyboard が参照している場所を表す。
 */
data class MoveToCustomKeyboardReference(
    val sourceLayoutId: Long,
    val sourceLayoutName: String,
    val sourceKeyIdentifier: String,
    val sourceKeyLabel: String?,
    val targetStableId: String
)

/**
 * 削除対象レイアウトの参照状況を表す。
 */
data class CustomKeyboardDeleteImpact(
    val layoutId: Long,
    val layoutName: String,
    val stableId: String,
    val references: List<MoveToCustomKeyboardReference>
) {
    val hasReferences: Boolean
        get() = references.isNotEmpty()
}

fun ensureStableIdsForLayouts(
    layouts: List<CustomKeyboardLayout>,
    generateStableId: () -> String = { UUID.randomUUID().toString() }
): List<CustomKeyboardLayout> {
    return layouts.map { layout ->
        if (layout.stableId.isBlank()) {
            layout.copy(stableId = generateStableId())
        } else {
            layout
        }
    }
}

@Singleton
class KeyboardRepository @Inject constructor(
    private val dao: KeyboardLayoutDao
) {

    // -----------------------------
    // Export / Import
    // -----------------------------

    suspend fun getAllFullLayoutsForExport(): List<FullKeyboardLayout> {
        return dao.getAllFullLayoutsOneShot()
    }

    /**
     * インポート処理（TWO_STEP_FLICK 含む）
     * - 名前衝突回避
     * - createdAt は import 時刻
     * - sortOrder は「最上位に積む」(max+1) を順に付与
     *
     * 引数は [ImportableKeyboardLayout] (= 既に importer 側で正規化済みで、
     * 全ての List が non-null になっているモデル)。
     * 外部 JSON DTO ([com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutExportDto])
     * は決してここに渡さない。
     */
    suspend fun importLayouts(layouts: List<ImportableKeyboardLayout>): KeyboardLayoutImportResult {
        if (layouts.isEmpty()) {
            return KeyboardLayoutImportResult.Failure(KeyboardLayoutImportError.NoImportableLayouts)
        }

        // まとめて import するときに max を毎回 DB に聞かない
        var currentMaxOrder = dao.getMaxSortOrder()
        val importedLayouts = mutableListOf<ImportableKeyboardLayout>()
        val errors = mutableListOf<KeyboardLayoutImportError>()

        layouts.forEachIndexed { layoutIndex, importable ->
            try {
                val importName = normalizeImportedLayoutName(
                    layoutIndex = layoutIndex,
                    rawName = importable.layout.name
                )
                var newName = importName
                var nameExists = dao.findLayoutByName(newName) != null
                var counter = 1
                while (nameExists) {
                    newName = "$importName (${counter})"
                    nameExists = dao.findLayoutByName(newName) != null
                    counter++
                }

                currentMaxOrder += 1
                val importedStableId = importable.layout.stableId
                val stableIdToInsert = if (importedStableId.isNullOrBlank() ||
                    dao.findLayoutByStableId(importedStableId) != null
                ) {
                    UUID.randomUUID().toString()
                } else {
                    importedStableId
                }

                val layoutToInsert = importable.layout.copy(
                    layoutId = 0,
                    name = newName,
                    createdAt = System.currentTimeMillis(),
                    sortOrder = currentMaxOrder,
                    stableId = stableIdToInsert
                )

                // Imported numeric ids are never trusted. The DAO inserts with
                // auto-generated ids and then reattaches child rows by stable
                // keyIdentifier, so legacy keyId/ownerLayoutId collisions cannot
                // replace existing rows.
                val normalizedKeysWithFlicks = importable.keysWithFlicks.map { keyWithFlicks ->
                    keyWithFlicks.copy(
                        key = keyWithFlicks.key.copy(keyId = 0, ownerLayoutId = 0),
                        flicks = keyWithFlicks.flicks.map { it.copy(ownerKeyId = 0) },
                        circularFlicks = keyWithFlicks.circularFlicks.map { it.copy(ownerKeyId = 0) },
                        twoStepFlicks = keyWithFlicks.twoStepFlicks.map { it.copy(ownerKeyId = 0) },
                        longPressFlicks = keyWithFlicks.longPressFlicks.map { it.copy(ownerKeyId = 0) },
                        twoStepLongPressFlicks = keyWithFlicks.twoStepLongPressFlicks.map {
                            it.copy(ownerKeyId = 0)
                        }
                    )
                }

                val keysToInsert = normalizedKeysWithFlicks.map { it.key }

                val flicksMap = normalizedKeysWithFlicks.associate { keyWithFlicks ->
                    keyWithFlicks.key.keyIdentifier to keyWithFlicks.flicks
                }

                val circularFlicksMap = normalizedKeysWithFlicks.associate { keyWithFlicks ->
                    keyWithFlicks.key.keyIdentifier to keyWithFlicks.circularFlicks
                }

                val twoStepMap = normalizedKeysWithFlicks.associate { keyWithFlicks ->
                    keyWithFlicks.key.keyIdentifier to keyWithFlicks.twoStepFlicks
                }

                val longPressFlicksMap = normalizedKeysWithFlicks.associate { keyWithFlicks ->
                    keyWithFlicks.key.keyIdentifier to keyWithFlicks.longPressFlicks
                }

                val twoStepLongPressMap = normalizedKeysWithFlicks.associate { keyWithFlicks ->
                    keyWithFlicks.key.keyIdentifier to keyWithFlicks.twoStepLongPressFlicks
                }

                // SpacerItem 復元: 元レイアウトの spacers を新規 layoutId 用に複製
                val spacersToInsert = importable.spacers.map { spacer ->
                    spacer.copy(spacerId = 0, ownerLayoutId = 0)
                }

                if (layoutToInsert.usageMode == KeyboardLayoutUsageMode.Number) {
                    val insertedLayoutId = dao.insertFullKeyboardLayout(
                        layoutToInsert,
                        keysToInsert,
                        flicksMap,
                        circularFlicksMap,
                        twoStepMap,
                        longPressFlicksMap,
                        twoStepLongPressMap,
                        spacersToInsert
                    )
                    dao.clearNumberUsageModeExcept(insertedLayoutId)
                } else {
                    dao.insertFullKeyboardLayout(
                        layoutToInsert,
                        keysToInsert,
                        flicksMap,
                        circularFlicksMap,
                        twoStepMap,
                        longPressFlicksMap,
                        twoStepLongPressMap,
                        spacersToInsert
                    )
                }
                importedLayouts += importable.copy(
                    layout = layoutToInsert,
                    keysWithFlicks = normalizedKeysWithFlicks,
                    spacers = spacersToInsert
                )
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "importLayouts storage failed index=%s exception=%s",
                    layoutIndex,
                    e::class.java.simpleName
                )
                errors += KeyboardLayoutImportError.StorageFailed(
                    layoutIndex = layoutIndex,
                    exceptionClass = e::class.java.simpleName,
                    message = e.message
                )
            }
        }

        return when {
            importedLayouts.isNotEmpty() && errors.isEmpty() ->
                KeyboardLayoutImportResult.Success(importedLayouts)

            importedLayouts.isNotEmpty() ->
                KeyboardLayoutImportResult.PartialSuccess(importedLayouts, errors)

            else -> KeyboardLayoutImportResult.Failure(
                errors.firstOrNull() ?: KeyboardLayoutImportError.StorageFailed()
            )
        }
    }

    private fun normalizeImportedLayoutName(layoutIndex: Int, rawName: String): String {
        return rawName.trim().takeIf { it.isNotEmpty() }
            ?: "Imported Keyboard ${layoutIndex + 1}"
    }

    // -----------------------------
    // Sorting (Drag & Drop persistence)
    // -----------------------------

    /**
     * RecyclerView で表示されている順（上→下）の layoutId リストを受け取り、
     * DB の sortOrder を振り直して永続化する。
     *
     * 例: adapter.currentList.map { it.layoutId } を渡す。
     */
    suspend fun updateLayoutOrder(layoutIdsInDisplayOrder: List<Long>) {
        dao.updateLayoutOrdersInDisplayOrder(layoutIdsInDisplayOrder)
    }

    private suspend fun nextTopSortOrder(): Int {
        return dao.getMaxSortOrder() + 1
    }

    // -----------------------------
    // Queries
    // -----------------------------

    fun getLayouts(): Flow<List<CustomKeyboardLayout>> = dao.getLayoutsList()

    suspend fun getLayoutsNotFlow(): List<CustomKeyboardLayout> =
        dao.getLayoutsListNotFlow()

    suspend fun ensureStableIds() {
        val currentLayouts = dao.getLayoutsListNotFlow()
        val ensuredLayouts = ensureStableIdsForLayouts(currentLayouts)
        currentLayouts.zip(ensuredLayouts)
            .filter { (current, ensured) -> current.stableId != ensured.stableId }
            .forEach { (_, ensured) -> dao.updateLayout(ensured) }
    }

    suspend fun getLayoutsNotFlowEnsuringStableIds(): List<CustomKeyboardLayout> {
        ensureStableIds()
        return dao.getLayoutsListNotFlow()
    }

    suspend fun setCurrentLayoutUsageMode(
        layoutId: Long,
        usageMode: KeyboardLayoutUsageMode
    ) {
        dao.setLayoutUsageModeExclusive(layoutId, usageMode)
    }

    suspend fun getLayoutName(id: Long): String? = dao.getLayoutName(id)

    fun getFullLayout(id: Long): Flow<KeyboardLayout> {
        return dao.getFullLayoutById(id).map { dbLayout ->
            convertToUiModel(dbLayout)
        }
    }

    fun getAllCustomKeyboardLayouts(): Flow<List<KeyboardLayout>> {
        return dao.getAllFullLayouts().map { dbLayouts ->
            dbLayouts.map { dbLayout ->
                convertToUiModel(dbLayout)
            }
        }
    }

    suspend fun doesNameExist(name: String, currentId: Long?): Boolean {
        val foundLayout = dao.findLayoutByName(name)
        return when {
            foundLayout == null -> false
            foundLayout.layoutId == currentId -> false
            else -> true
        }
    }

    // -----------------------------
    // Save / Delete / Duplicate
    // -----------------------------

    /**
     * レイアウトの保存処理（TWO_STEP_FLICK 含む）。
     *
     * 設計:
     * - id == null / id <= 0 → 新規作成 ([createNewLayoutInternal]):
     *   stableId を新規 UUID で生成し、createdAt は現在時刻、sortOrder は max+1。
     * - id > 0 → 既存更新 ([updateExistingLayoutInternal]):
     *   既存レイアウトを取得し、layoutId / stableId / createdAt / sortOrder を維持。
     *   name / rowCount / columnCount / isRomaji / isDirectMode のみ更新。
     *   子要素は DAO のトランザクション内で再構築。
     *   既存が見つからない場合は [LayoutNotFoundException] を投げる
     *   (黙って新規作成にフォールバックして stableId を再生成しないため)。
     *
     * @return 保存後の layoutId
     */
    suspend fun saveLayout(layout: KeyboardLayout, name: String, id: Long?): Long {
        Timber.d("saveLayout: id=%s name=%s", id, name)
        val layoutToSave = if (layout.usesFlexiblePlacement()) {
            layout.withCanonicalFlexibleBounds()
        } else {
            layout
        }

        val savedLayoutId = if (id == null || id <= 0L) {
            createNewLayoutInternal(layoutToSave, name)
        } else {
            updateExistingLayoutInternal(id, layoutToSave, name)
        }
        if (layoutToSave.usageMode == KeyboardLayoutUsageMode.Number) {
            dao.clearNumberUsageModeExcept(savedLayoutId)
        }
        return savedLayoutId
    }

    private suspend fun createNewLayoutInternal(layout: KeyboardLayout, name: String): Long {
        val newStableId = generateUniqueStableId()
        val newLayout = CustomKeyboardLayout(
            layoutId = 0,
            name = name,
            columnCount = layout.columnCount,
            rowCount = layout.rowCount,
            isRomaji = layout.isRomaji,
            isDirectMode = layout.isDirectMode,
            createdAt = System.currentTimeMillis(),
            sortOrder = nextTopSortOrder(),
            stableId = newStableId,
            isFlexiblePlacementLayout = layout.usesFlexiblePlacement(),
            usageMode = layout.usageMode
        )
        val parts = convertToDbModel(layout)
        val newLayoutId = dao.insertFullKeyboardLayout(
            newLayout,
            parts.keys,
            parts.flicksMap,
            parts.circularFlicksMap,
            parts.twoStepMap,
            parts.longPressFlicksMap,
            parts.twoStepLongPressMap,
            parts.spacers
        )
        Timber.d(
            "createNewLayoutInternal: inserted layoutId=%s stableId=%s",
            newLayoutId,
            newStableId
        )
        return newLayoutId
    }

    private suspend fun updateExistingLayoutInternal(
        layoutId: Long,
        layout: KeyboardLayout,
        name: String
    ): Long {
        val existing = dao.getFullLayoutOneShot(layoutId)?.layout
            ?: throw LayoutNotFoundException(layoutId).also {
                Timber.e("updateExistingLayoutInternal: layoutId=%s not found", layoutId)
            }

        // identity (layoutId / stableId / createdAt / sortOrder) は決して書き換えない。
        // stableId が空の既存 row が来た場合は (旧データ) 新しい UUID を割り当てておく。
        // これは "blank → 何かしらの stableId" への一方向の修復であり、
        // 既存の有効な stableId は絶対に変更しない。
        val repairedStableId = if (existing.stableId.isBlank()) {
            generateUniqueStableId()
        } else {
            existing.stableId
        }

        val updatedParent = existing.copy(
            name = name,
            columnCount = layout.columnCount,
            rowCount = layout.rowCount,
            isRomaji = layout.isRomaji,
            isDirectMode = layout.isDirectMode,
            stableId = repairedStableId,
            isFlexiblePlacementLayout = layout.usesFlexiblePlacement(),
            usageMode = layout.usageMode
        )

        val parts = convertToDbModel(layout)
        dao.updateFullKeyboardLayoutKeepingIdentity(
            layout = updatedParent,
            keys = parts.keys,
            flicksMap = parts.flicksMap,
            circularFlicksMap = parts.circularFlicksMap,
            twoStepFlicksMap = parts.twoStepMap,
            longPressFlicksMap = parts.longPressFlicksMap,
            twoStepLongPressFlicksMap = parts.twoStepLongPressMap,
            spacers = parts.spacers
        )
        Timber.d(
            "updateExistingLayoutInternal: kept identity layoutId=%s stableId=%s",
            updatedParent.layoutId, updatedParent.stableId
        )
        return updatedParent.layoutId
    }

    /**
     * 既存の stableId と衝突しない UUID を生成する。
     * unique index 違反による insert 失敗を防ぐためのガード。
     */
    private suspend fun generateUniqueStableId(): String {
        repeat(10) {
            val candidate = UUID.randomUUID().toString()
            if (dao.findLayoutByStableId(candidate) == null) {
                return candidate
            }
        }
        // ここまで来る確率は事実上ゼロ (UUIDv4 は 2^122 通り)。最後の保険として
        // タイムスタンプ付きの値を返す。
        return "fallback-stable-${System.nanoTime()}-${UUID.randomUUID()}"
    }

    /**
     * UI からの「とりあえず削除して」を受け付ける従来 API。
     *
     * 通常は [deleteLayoutConfirmed] を使うこと。これは参照チェック後に呼び出される
     * 内部 API としても利用される。
     */
    suspend fun deleteLayout(id: Long) {
        Timber.d("deleteLayout: id=%s", id)
        dao.deleteLayout(id)
    }

    /**
     * 削除前に必ず参照チェックを行うことを呼び出し側に明示するためのラッパー。
     * 参照の有無に関わらず削除を実行する。
     *
     * UI 層は事前に [getDeleteImpactForLayout] を呼び、
     * 参照ありなら警告ダイアログでユーザーの了承を取り、
     * 了承後にこのメソッドを呼ぶ。
     */
    suspend fun deleteLayoutConfirmed(id: Long) {
        Timber.d("deleteLayoutConfirmed: id=%s", id)
        dao.deleteLayout(id)
    }

    // -----------------------------
    // MoveToCustomKeyboard 参照検査
    // -----------------------------

    /**
     * 指定 layoutId を削除した場合に「削除済みのカスタムキーボード」になる
     * MoveToCustomKeyboard の参照一覧を返す。
     *
     * - 削除対象が存在しない場合は references=空、layoutName=空文字、stableId=空文字。
     * - stableId が空の場合は参照が無いとみなす (MoveToCustomKeyboard("") はそもそも
     *   永続化時点で破棄される設計のため)。
     */
    suspend fun getDeleteImpactForLayout(layoutId: Long): CustomKeyboardDeleteImpact {
        val target = dao.getFullLayoutOneShot(layoutId)?.layout
        if (target == null) {
            return CustomKeyboardDeleteImpact(
                layoutId = layoutId,
                layoutName = "",
                stableId = "",
                references = emptyList()
            )
        }
        val references = if (target.stableId.isBlank()) {
            emptyList()
        } else {
            findMoveToCustomKeyboardReferences(target.stableId)
        }
        return CustomKeyboardDeleteImpact(
            layoutId = target.layoutId,
            layoutName = target.name,
            stableId = target.stableId,
            references = references
        )
    }

    /**
     * 全カスタムキーボードを走査し、`MoveToCustomKeyboard(targetStableId)` に
     * 該当する参照箇所を集める。tap action / petal flick / circular flick /
     * two-step flick / long-press flick / two-step long-press flick のすべてを対象。
     *
     * 既存設計に合わせ、DB 文字列ではなく
     * [com.kazumaproject.custom_keyboard.data.KeyActionMapper.toKeyAction]
     * と [toFlickAction]/[FlickMapping/CircularFlickMapping#toFlickAction] を通して
     * KeyAction として復元してから判定する。これにより、
     * 文字列表現の揺れ (`MoveToCustomKeyboard:xxx` / `MoveToCustomKeyboard` の
     * actionType=stableId) の両形式を取りこぼさない。
     */
    suspend fun findMoveToCustomKeyboardReferences(
        targetStableId: String
    ): List<MoveToCustomKeyboardReference> {
        if (targetStableId.isBlank()) return emptyList()

        val references = mutableListOf<MoveToCustomKeyboardReference>()
        val allLayouts = dao.getAllFullLayoutsOneShot()

        for (full in allLayouts) {
            val sourceLayoutId = full.layout.layoutId
            val sourceLayoutName = full.layout.name

            for (keyWithFlicks in full.keysWithFlicks) {
                val key = keyWithFlicks.key
                val keyIdentifier = key.keyIdentifier
                val keyLabel = key.label.takeIf { it.isNotBlank() }

                // 1) tap action
                val tapAction = KeyActionMapper.toKeyAction(key.action)
                if (tapAction is KeyAction.MoveToCustomKeyboard &&
                    tapAction.stableId == targetStableId
                ) {
                    references += MoveToCustomKeyboardReference(
                        sourceLayoutId = sourceLayoutId,
                        sourceLayoutName = sourceLayoutName,
                        sourceKeyIdentifier = keyIdentifier,
                        sourceKeyLabel = keyLabel,
                        targetStableId = targetStableId
                    )
                }

                // 2) flick mapping
                for (flick in keyWithFlicks.flicks) {
                    val act = flick.toFlickAction()
                    if (act is FlickAction.Action) {
                        val a = act.action
                        if (a is KeyAction.MoveToCustomKeyboard &&
                            a.stableId == targetStableId
                        ) {
                            references += MoveToCustomKeyboardReference(
                                sourceLayoutId = sourceLayoutId,
                                sourceLayoutName = sourceLayoutName,
                                sourceKeyIdentifier = keyIdentifier,
                                sourceKeyLabel = keyLabel,
                                targetStableId = targetStableId
                            )
                        }
                    }
                }

                // 3) circular flick mapping
                for (cflick in keyWithFlicks.circularFlicks) {
                    val act = cflick.toFlickAction()
                    if (act is FlickAction.Action) {
                        val a = act.action
                        if (a is KeyAction.MoveToCustomKeyboard &&
                            a.stableId == targetStableId
                        ) {
                            references += MoveToCustomKeyboardReference(
                                sourceLayoutId = sourceLayoutId,
                                sourceLayoutName = sourceLayoutName,
                                sourceKeyIdentifier = keyIdentifier,
                                sourceKeyLabel = keyLabel,
                                targetStableId = targetStableId
                            )
                        }
                    }
                }

                // two-step / long-press / two-step long-press は
                // 出力テキスト (String) を保持する設計なので、KeyAction を
                // 持つことはなく MoveToCustomKeyboard 参照は発生しない。
                // 将来的に actionType を持つ拡張があった場合はここに追加する。
            }
        }
        return references
    }

    /**
     * レイアウト複製:
     * - 名前衝突回避
     * - createdAt は複製時刻
     * - sortOrder は最上位へ (max+1)
     */
    suspend fun duplicateLayout(id: Long) {
        val originalLayout = dao.getFullLayoutOneShot(id) ?: return

        val baseName = originalLayout.layout.name + " (コピー)"
        var finalName = baseName
        var counter = 2
        while (dao.findLayoutByName(finalName) != null) {
            finalName = "$baseName ($counter)"
            counter++
        }

        val newLayoutInfo = originalLayout.layout.copy(
            layoutId = 0,
            name = finalName,
            createdAt = System.currentTimeMillis(),
            sortOrder = nextTopSortOrder(),
            stableId = UUID.randomUUID().toString(),
            usageMode = KeyboardLayoutUsageMode.Normal
        )

        val newKeys = originalLayout.keysWithFlicks.map { keyWithFlicks ->
            keyWithFlicks.key.copy(keyId = 0, ownerLayoutId = 0)
        }

        val newFlicksMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newFlicks = keyWithFlicks.flicks.map { flick ->
                flick.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newFlicks
        }

        val newCircularFlicksMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newFlicks = keyWithFlicks.circularFlicks.map { flick ->
                flick.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newFlicks
        }

        val newTwoStepMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newTwoStep = keyWithFlicks.twoStepFlicks.map { m ->
                m.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newTwoStep
        }

        val newLongPressFlicksMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newLongPressFlicks = keyWithFlicks.longPressFlicks.map { m ->
                m.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newLongPressFlicks
        }

        val newTwoStepLongPressMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newTwoStepLongPress = keyWithFlicks.twoStepLongPressFlicks.map { mapping ->
                mapping.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newTwoStepLongPress
        }

        // 元レイアウトの SpacerItem も複製
        val newSpacers = originalLayout.spacers.map { spacer ->
            spacer.copy(spacerId = 0, ownerLayoutId = 0)
        }

        dao.insertFullKeyboardLayout(
            newLayoutInfo,
            newKeys,
            newFlicksMap,
            newCircularFlicksMap,
            newTwoStepMap,
            newLongPressFlicksMap,
            newTwoStepLongPressMap,
            newSpacers
        )
    }

    // -----------------------------
    // Convert models
    // -----------------------------

    fun convertLayout(dbLayout: KeyboardLayout): KeyboardLayout {
        val uuidToTapCharMap = dbLayout.flickKeyMaps.mapNotNull { (uuid, flickActionStates) ->
            val tapAction = flickActionStates.firstOrNull()?.get(FlickDirection.TAP)
            if (tapAction is FlickAction.Input) uuid to tapAction.char else null
        }.toMap()

        val uuidToFinalLabelMap = dbLayout.keys.mapNotNull { keyData ->
            val uuid = keyData.keyId ?: return@mapNotNull null
            val finalLabel =
                if (keyData.label.isNotEmpty()) keyData.label else uuidToTapCharMap[uuid]
            if (finalLabel != null) uuid to finalLabel else null
        }.toMap()

        val newFlickKeyMaps = dbLayout.flickKeyMaps
            .mapNotNull { (uuid, flickActions) ->
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) finalLabel to flickActions else null
            }
            .toMap()

        val newLongPressFlickKeyMaps = dbLayout.longPressFlickKeyMaps
            .mapNotNull { (uuid, longPressMap) ->
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) finalLabel to longPressMap else null
            }
            .toMap()

        val newTwoStepFlickKeyMaps = dbLayout.twoStepFlickKeyMaps
            .mapNotNull { (uuid, twoStepMap) ->
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) finalLabel to twoStepMap else null
            }
            .toMap()

        val newTwoStepLongPressKeyMaps = dbLayout.twoStepLongPressKeyMaps
            .mapNotNull { (uuid, twoStepLongPressMap) ->
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) finalLabel to twoStepLongPressMap else null
            }
            .toMap()

        val newKeys = dbLayout.keys.map { keyData ->
            if (keyData.isSpecialKey) {
                keyData
            } else {
                val finalLabel = uuidToFinalLabelMap[keyData.keyId]
                if (finalLabel != null) keyData.copy(label = finalLabel) else keyData
            }
        }

        val convertedLayout = if (dbLayout.usesFlexiblePlacement()) {
            val newKeysById = newKeys.mapNotNull { key ->
                key.keyId?.let { it to key }
            }.toMap()
            val newItems = dbLayout.items.map { item ->
                when (item) {
                    is SpacerItem -> item
                    is KeyItem -> {
                        val updatedKey = newKeysById[item.keyData.keyId] ?: item.keyData
                        item.copy(keyData = updatedKey)
                    }
                }
            }
            dbLayout.copyWithItems(newItems)
        } else {
            dbLayout.copyWithKeys(newKeys)
        }

        return convertedLayout.copy(
            flickKeyMaps = newFlickKeyMaps,
            twoStepFlickKeyMaps = newTwoStepFlickKeyMaps,
            longPressFlickKeyMaps = newLongPressFlickKeyMaps,
            twoStepLongPressKeyMaps = newTwoStepLongPressKeyMaps
        )
    }

    private fun convertToUiModel(dbLayout: FullKeyboardLayout): KeyboardLayout {
        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> =
            dbLayout.keysWithFlicks.associate { keyWithFlicks ->
                val identifier = keyWithFlicks.key.keyIdentifier

                val flicksByState = keyWithFlicks.flicks
                    .groupBy { it.stateIndex }
                    .mapValues { (_, stateFlicks) ->
                        stateFlicks.associate { flick ->
                            flick.flickDirection to flick.toFlickAction()
                        }
                    }
                    .toSortedMap()
                    .values
                    .toList()

                identifier to flicksByState
            }

        val circularFlickMaps: Map<String, List<Map<CircularFlickDirection, FlickAction>>> =
            dbLayout.keysWithFlicks.associate { keyWithFlicks ->
                val identifier = keyWithFlicks.key.keyIdentifier

                val flicksByState = keyWithFlicks.circularFlicks
                    .groupBy { it.stateIndex }
                    .mapValues { (_, stateFlicks) ->
                        stateFlicks.associate { flick ->
                            flick.circularDirection to flick.toFlickAction()
                        }
                    }
                    .toSortedMap()
                    .values
                    .toList()

                identifier to flicksByState
            }.filterValues { it.isNotEmpty() }

        val twoStepMaps: Map<String, Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>> =
            dbLayout.keysWithFlicks.mapNotNull { keyWithFlicks ->
                val identifier = keyWithFlicks.key.keyIdentifier
                if (keyWithFlicks.twoStepFlicks.isEmpty()) return@mapNotNull null

                val firstMap = keyWithFlicks.twoStepFlicks
                    .groupBy { it.firstDirection }
                    .mapValues { (_, list) ->
                        list.associate { it.secondDirection to it.output }
                    }

                identifier to firstMap
            }.toMap()

        val longPressFlickMaps: Map<String, Map<FlickDirection, String>> =
            dbLayout.keysWithFlicks.mapNotNull { keyWithFlicks ->
                val identifier = keyWithFlicks.key.keyIdentifier
                if (keyWithFlicks.longPressFlicks.isEmpty()) return@mapNotNull null

                identifier to keyWithFlicks.longPressFlicks.associate { mapping ->
                    mapping.flickDirection to mapping.output
                }
            }.toMap()

        val twoStepLongPressMaps: Map<String, Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>> =
            dbLayout.keysWithFlicks.mapNotNull { keyWithFlicks ->
                val identifier = keyWithFlicks.key.keyIdentifier
                if (keyWithFlicks.twoStepLongPressFlicks.isEmpty()) return@mapNotNull null

                val firstMap = keyWithFlicks.twoStepLongPressFlicks
                    .groupBy { it.firstDirection }
                    .mapValues { (_, list) ->
                        list.associate { it.secondDirection to it.output }
                    }

                identifier to firstMap
            }.toMap()

        val keyItems = mutableListOf<KeyItem>()

        val keys: List<KeyData> = dbLayout.keysWithFlicks.map { keyWithFlicks ->
            val dbKey = keyWithFlicks.key

            val actionObject: KeyAction? = KeyActionMapper.toKeyAction(dbKey.action)
            val restoredAction = actionObject ?: if (
                !dbKey.isSpecialKey &&
                dbKey.keyType == KeyType.NORMAL &&
                dbKey.label.isNotBlank()
            ) {
                KeyAction.Text(dbKey.label)
            } else {
                null
            }

            val keyData = if (restoredAction == null) {
                KeyData(
                    label = dbKey.label,
                    row = dbKey.row,
                    column = dbKey.column,
                    isFlickable = dbKey.keyType != KeyType.NORMAL,
                    keyType = dbKey.keyType,
                    rowSpan = dbKey.rowSpan,
                    colSpan = dbKey.colSpan,
                    isSpecialKey = dbKey.isSpecialKey,
                    drawableResId = null,
                    keyId = dbKey.keyIdentifier,
                    action = null
                )
            } else {
                KeyData(
                    label = dbKey.label,
                    row = dbKey.row,
                    column = dbKey.column,
                    isFlickable = dbKey.keyType != KeyType.NORMAL,
                    keyType = dbKey.keyType,
                    rowSpan = dbKey.rowSpan,
                    colSpan = dbKey.colSpan,
                    isSpecialKey = dbKey.isSpecialKey,
                    drawableResId = if (dbKey.isSpecialKey) drawableResIdForAction(restoredAction) else null,
                    keyId = dbKey.keyIdentifier,
                    action = restoredAction
                )
            }
            val placement = GridPlacement(
                rowUnits = dbKey.rowUnits ?: dbKey.row * 2,
                columnUnits = dbKey.columnUnits ?: dbKey.column * 2,
                rowSpanUnits = dbKey.rowSpanUnits ?: dbKey.rowSpan * 2,
                columnSpanUnits = dbKey.columnSpanUnits ?: dbKey.colSpan * 2
            )
            keyItems += KeyItem(
                id = keyData.keyId ?: dbKey.keyIdentifier,
                keyData = keyData,
                placement = placement
            )
            keyData
        }
        // 永続化された SpacerItem を復元 (行内 Spacer 含む完全復元)
        // 旧データに spacer_definitions が無い場合は restoreLeadingSpacers() に
        // フォールバックして「行頭 Spacer」だけは推測復元する。
        val storedSpacers: List<SpacerItem> = dbLayout.spacers
            .sortedBy { it.sortOrder }
            .map { spacer ->
                SpacerItem(
                    id = spacer.itemIdentifier.ifBlank {
                        "spacer_${spacer.spacerId}"
                    },
                    placement = GridPlacement(
                        rowUnits = spacer.rowUnits,
                        columnUnits = spacer.columnUnits,
                        rowSpanUnits = spacer.rowSpanUnits,
                        columnSpanUnits = spacer.columnSpanUnits
                    )
                )
            }

        val items: List<KeyboardLayoutItem> = if (storedSpacers.isNotEmpty()) {
            // 完全復元: items 順は (Spacer, Key) を rowUnits → columnUnits でマージ
            (storedSpacers + keyItems).sortedWith(
                compareBy({ it.placement.rowUnits }, { it.placement.columnUnits })
            )
        } else {
            // 旧データ互換: spacer_definitions が無いレイアウトは行頭 Spacer のみ推測
            restoreLeadingSpacers(keyItems) + keyItems
        }

        // columnUnitCount / rowUnitCount は KeyDefinition.rowUnits 等の有無に応じて
        // 厳密値 / フォールバック値を決定する。
        val derivedColumnUnitCount = items
            .maxOfOrNull { it.placement.columnUnits + it.placement.columnSpanUnits }
            ?: (dbLayout.layout.columnCount * 2)
        val derivedRowUnitCount = items
            .maxOfOrNull { it.placement.rowUnits + it.placement.rowSpanUnits }
            ?: (dbLayout.layout.rowCount * 2)

        val columnUnitCount = maxOf(derivedColumnUnitCount, dbLayout.layout.columnCount * 2)
        val rowUnitCount = maxOf(derivedRowUnitCount, dbLayout.layout.rowCount * 2)

        val restoredLayout = KeyboardLayout(
            keys = keys,
            flickKeyMaps = flickMaps,
            columnCount = dbLayout.layout.columnCount,
            rowCount = dbLayout.layout.rowCount,
            isRomaji = dbLayout.layout.isRomaji,
            isDirectMode = dbLayout.layout.isDirectMode,
            circularFlickKeyMaps = circularFlickMaps.ifEmpty {
                flickMaps.mapValues { (_, states) ->
                    states.map { stateMap ->
                        stateMap.mapKeys { (direction, _) ->
                            direction.toCircularFlickDirection()
                        }
                    }
                }
            },
            twoStepFlickKeyMaps = twoStepMaps,
            longPressFlickKeyMaps = longPressFlickMaps,
            twoStepLongPressKeyMaps = twoStepLongPressMaps,
            items = items,
            columnUnitCount = columnUnitCount,
            rowUnitCount = rowUnitCount,
            isFlexiblePlacementLayout = dbLayout.layout.isFlexiblePlacementLayout,
            usageMode = dbLayout.layout.usageMode
        )
        return if (restoredLayout.usesFlexiblePlacement()) {
            restoredLayout.withCanonicalFlexibleBounds()
        } else {
            restoredLayout
        }
    }

    private fun drawableResIdForAction(action: KeyAction): Int? {
        return when (action) {
            KeyAction.Backspace -> com.kazumaproject.core.R.drawable.backspace_24px
            KeyAction.ChangeInputMode -> com.kazumaproject.core.R.drawable.backspace_24px
            KeyAction.Convert -> com.kazumaproject.core.R.drawable.henkan
            KeyAction.Copy -> com.kazumaproject.core.R.drawable.content_copy_24dp
            KeyAction.Delete -> com.kazumaproject.core.R.drawable.backspace_24px
            KeyAction.Enter -> com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
            KeyAction.ForceNewLine -> com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
            KeyAction.MoveCursorLeft -> com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            KeyAction.MoveCursorUp -> com.kazumaproject.core.R.drawable.outline_arrow_drop_up_24
            KeyAction.MoveCursorDown -> com.kazumaproject.core.R.drawable.outline_arrow_drop_down_24
            KeyAction.MoveCursorRight -> com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            KeyAction.MoveCustomKeyboardTab -> com.kazumaproject.core.R.drawable.keyboard_command_key_24px
            is KeyAction.MoveToCustomKeyboard -> com.kazumaproject.core.R.drawable.keyboard_24px
            KeyAction.Paste -> com.kazumaproject.core.R.drawable.content_paste_24px
            KeyAction.SelectAll -> com.kazumaproject.core.R.drawable.text_select_start_24dp
            KeyAction.SelectLeft -> com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            KeyAction.SelectRight -> com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            KeyAction.ShiftKey -> com.kazumaproject.core.R.drawable.shift_24px
            KeyAction.CapLockKey -> com.kazumaproject.core.R.drawable.caps_lock_outline
            KeyAction.SwitchRomajiEnglish -> com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px
            KeyAction.ShowEmojiKeyboard -> com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24
            KeyAction.Space -> com.kazumaproject.core.R.drawable.baseline_space_bar_24
            KeyAction.ForceFullWidthSpace -> com.kazumaproject.core.R.drawable.baseline_space_bar_24
            KeyAction.ForceHalfWidthSpace -> com.kazumaproject.core.R.drawable.baseline_space_bar_24
            KeyAction.SwitchToEnglishLayout -> com.kazumaproject.core.R.drawable.input_mode_english_custom
            KeyAction.SwitchToKanaLayout -> com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom
            KeyAction.SwitchToNextIme -> com.kazumaproject.core.R.drawable.language_24dp
            KeyAction.SwitchToNumberLayout -> com.kazumaproject.core.R.drawable.input_mode_number_select_custom
            KeyAction.ToggleCase -> com.kazumaproject.core.R.drawable.english_small
            KeyAction.ToggleDakuten -> com.kazumaproject.core.R.drawable.kana_small_custom
            KeyAction.ToggleKatakana -> com.kazumaproject.core.R.drawable.katakana
            KeyAction.VoiceInput -> com.kazumaproject.core.R.drawable.settings_voice_24px
            KeyAction.DeleteUntilSymbol -> com.kazumaproject.core.R.drawable.backspace_24px_until_symbol
            KeyAction.DeleteAfterCursorUntilSymbol -> com.kazumaproject.core.R.drawable.backspace_24px_after_cursor
            KeyAction.SwitchDirectMode -> com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px
            else -> null
        }
    }

    private fun restoreLeadingSpacers(keyItems: List<KeyItem>): List<KeyboardLayoutItem> {
        return keyItems
            .groupBy { it.placement.rowUnits }
            .mapNotNull { (rowUnits, rowItems) ->
                val minColumnUnits =
                    rowItems.minOfOrNull { it.placement.columnUnits } ?: return@mapNotNull null
                if (minColumnUnits <= 0) return@mapNotNull null
                val rowSpanUnits = rowItems.minOfOrNull { it.placement.rowSpanUnits } ?: 2
                SpacerItem(
                    id = "restored_row_${rowUnits}_start_spacer",
                    placement = GridPlacement(
                        rowUnits = rowUnits,
                        columnUnits = 0,
                        rowSpanUnits = rowSpanUnits,
                        columnSpanUnits = minColumnUnits
                    )
                )
            }
    }

    private fun convertToDbModel(
        uiLayout: KeyboardLayout
    ): DbKeyboardLayoutParts {

        val keys = mutableListOf<KeyDefinition>()
        val flicksMap = mutableMapOf<String, MutableList<FlickMapping>>()
        val circularFlicksMap = mutableMapOf<String, MutableList<CircularFlickMapping>>()
        val twoStepMap = mutableMapOf<String, MutableList<TwoStepFlickMapping>>()
        val longPressFlicksMap = mutableMapOf<String, MutableList<LongPressFlickMapping>>()
        val twoStepLongPressMap = mutableMapOf<String, MutableList<TwoStepLongPressMappingEntity>>()
        val itemByKeyId = uiLayout.items
            .filterIsInstance<KeyItem>()
            .flatMap { item ->
                listOfNotNull(
                    item.id to item,
                    item.keyData.keyId?.let { it to item }
                )
            }
            .toMap()

        uiLayout.keys.forEach { keyData ->
            val keyIdentifier = keyData.keyId ?: UUID.randomUUID().toString()

            val actionString: String? = KeyActionMapper.fromKeyAction(keyData.action)
            val placement = itemByKeyId[keyIdentifier]?.placement ?: keyData.toKeyItem().placement

            keys.add(
                KeyDefinition(
                    keyId = 0,
                    ownerLayoutId = 0,
                    label = keyData.label,
                    row = keyData.row,
                    column = keyData.column,
                    rowSpan = keyData.rowSpan,
                    colSpan = keyData.colSpan,
                    keyType = keyData.keyType,
                    isSpecialKey = keyData.isSpecialKey,
                    drawableResId = null,
                    keyIdentifier = keyIdentifier,
                    action = actionString,
                    rowUnits = placement.rowUnits,
                    columnUnits = placement.columnUnits,
                    rowSpanUnits = placement.rowSpanUnits,
                    columnSpanUnits = placement.columnSpanUnits
                )
            )

            uiLayout.flickKeyMaps[keyIdentifier]?.forEachIndexed { stateIndex, stateMap ->
                stateMap.forEach { (direction, flickAction) ->
                    val (actionType, actionValue) = flickAction.toDbStrings()
                    val flick = FlickMapping(
                        ownerKeyId = 0,
                        stateIndex = stateIndex,
                        flickDirection = direction,
                        actionType = actionType,
                        actionValue = actionValue
                    )
                    flicksMap.getOrPut(keyIdentifier) { mutableListOf() }.add(flick)
                }
            }

            uiLayout.circularFlickKeyMaps[keyIdentifier]?.forEachIndexed { stateIndex, stateMap ->
                stateMap.forEach { (direction, flickAction) ->
                    val (actionType, actionValue) = flickAction.toDbStrings()
                    val flick = CircularFlickMapping(
                        ownerKeyId = 0,
                        stateIndex = stateIndex,
                        circularDirection = direction,
                        actionType = actionType,
                        actionValue = actionValue
                    )
                    circularFlicksMap.getOrPut(keyIdentifier) { mutableListOf() }.add(flick)
                }
            }

            val twoStep = uiLayout.twoStepFlickKeyMaps[keyIdentifier]
            twoStep?.forEach { (first, secondMap) ->
                secondMap.forEach { (second, output) ->
                    val mapping = TwoStepFlickMapping(
                        ownerKeyId = 0,
                        firstDirection = first,
                        secondDirection = second,
                        output = output
                    )
                    twoStepMap.getOrPut(keyIdentifier) { mutableListOf() }.add(mapping)
                }
            }

            val longPressFlicks = uiLayout.longPressFlickKeyMaps[keyIdentifier]
            longPressFlicks?.forEach { (direction, output) ->
                if (output.isNotEmpty()) {
                    val mapping = LongPressFlickMapping(
                        ownerKeyId = 0,
                        flickDirection = direction,
                        output = output
                    )
                    longPressFlicksMap.getOrPut(keyIdentifier) { mutableListOf() }.add(mapping)
                }
            }

            val twoStepLongPress = uiLayout.twoStepLongPressKeyMaps[keyIdentifier]
            twoStepLongPress?.forEach { (first, secondMap) ->
                secondMap.forEach { (second, output) ->
                    if (output.isNotEmpty()) {
                        val mapping = TwoStepLongPressMappingEntity(
                            ownerKeyId = 0,
                            firstDirection = first,
                            secondDirection = second,
                            output = output
                        )
                        twoStepLongPressMap.getOrPut(keyIdentifier) { mutableListOf() }.add(mapping)
                    }
                }
            }
        }

        // SpacerItem を SpacerDefinition に変換 (順序情報は items の登場順を保持)
        val spacerDefinitions = uiLayout.items
            .filterIsInstance<SpacerItem>()
            .mapIndexed { index, spacer ->
                SpacerDefinition(
                    spacerId = 0,
                    ownerLayoutId = 0,
                    itemIdentifier = spacer.id,
                    rowUnits = spacer.placement.rowUnits,
                    columnUnits = spacer.placement.columnUnits,
                    rowSpanUnits = spacer.placement.rowSpanUnits,
                    columnSpanUnits = spacer.placement.columnSpanUnits,
                    sortOrder = index
                )
            }

        // Map<String, MutableList<...>> -> Map<String, List<...>> にして返す
        return DbKeyboardLayoutParts(
            keys = keys,
            flicksMap = flicksMap.mapValues { it.value.toList() },
            circularFlicksMap = circularFlicksMap.mapValues { it.value.toList() },
            twoStepMap = twoStepMap.mapValues { it.value.toList() },
            longPressFlicksMap = longPressFlicksMap.mapValues { it.value.toList() },
            twoStepLongPressMap = twoStepLongPressMap.mapValues { it.value.toList() },
            spacers = spacerDefinitions
        )
    }
}
