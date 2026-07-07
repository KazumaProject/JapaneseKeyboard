package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui

import androidx.lifecycle.SavedStateHandle
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyDataSource
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyOverrideType
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SumireSpecialKeyActionEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun draftSelectionDoesNotPersistUntilSaveAndSaveWritesEachDirection() = runTest(dispatcher) {
        val repository = FakeSumireSpecialKeyDataSource()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.setKeyAction(SumireSpecialKeyDirection.TAP, KeyAction.Delete)
        viewModel.setKeyAction(SumireSpecialKeyDirection.UP, KeyAction.Space)
        viewModel.setKeyAction(SumireSpecialKeyDirection.RIGHT, KeyAction.Enter)
        viewModel.setKeyAction(SumireSpecialKeyDirection.DOWN, KeyAction.Paste)
        viewModel.setKeyAction(SumireSpecialKeyDirection.LEFT, KeyAction.Copy)
        advanceUntilIdle()

        assertTrue(repository.upserts.isEmpty())
        assertTrue(repository.deletedDirections.isEmpty())

        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            listOf("TAP", "UP", "RIGHT", "DOWN", "LEFT"),
            repository.upserts.map { it.direction }
        )
        assertEquals(
            listOf("Delete", "Space", "Enter", "Paste", "Copy"),
            repository.upserts.map { it.actionString }
        )
        assertTrue(repository.deletedDirections.isEmpty())
    }

    @Test
    fun setDefaultDeletesOnlyThatDirectionOnSave() = runTest(dispatcher) {
        val repository = FakeSumireSpecialKeyDataSource()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        SumireSpecialKeyDirection.entries.forEach {
            viewModel.setKeyAction(it, KeyAction.Delete)
        }
        viewModel.setDefault(SumireSpecialKeyDirection.RIGHT)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            listOf(SumireSpecialKeyDirection.RIGHT),
            repository.deletedDirections.map { it.direction }
        )
        assertEquals(
            listOf("TAP", "UP", "DOWN", "LEFT"),
            repository.upserts.map { it.direction }
        )
    }

    @Test
    fun doNothingIsSavedAsExplicitKeyActionAndDefaultStillDeletesOverride() = runTest(dispatcher) {
        val repository = FakeSumireSpecialKeyDataSource()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.setKeyAction(SumireSpecialKeyDirection.TAP, KeyAction.DoNothing)
        viewModel.setKeyAction(SumireSpecialKeyDirection.UP, KeyAction.Delete)
        viewModel.setDefault(SumireSpecialKeyDirection.UP)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf("TAP"), repository.upserts.map { it.direction })
        assertEquals(listOf("DoNothing"), repository.upserts.map { it.actionString })
        assertTrue(repository.deletedDirections.map { it.direction }.contains(SumireSpecialKeyDirection.UP))
    }

    @Test
    fun legacyNoneAndInputTextRecordsAreReadWithoutCrashing() = runTest(dispatcher) {
        val repository = FakeSumireSpecialKeyDataSource()
        repository.keyOverrides.value = listOf(
            entity(SumireSpecialKeyDirection.TAP, SumireSpecialKeyOverrideType.NONE),
            entity(
                SumireSpecialKeyDirection.UP,
                SumireSpecialKeyOverrideType.INPUT_TEXT,
                inputText = "legacy"
            )
        )

        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(
            SumireSpecialKeyOverrideType.DEFAULT,
            viewModel.uiState.value.drafts.getValue(SumireSpecialKeyDirection.TAP).overrideType
        )
        assertEquals(
            SumireSpecialKeyOverrideType.DEFAULT,
            viewModel.uiState.value.drafts.getValue(SumireSpecialKeyDirection.UP).overrideType
        )
    }

    private fun viewModel(
        repository: FakeSumireSpecialKeyDataSource
    ): SumireSpecialKeyActionEditorViewModel {
        return SumireSpecialKeyActionEditorViewModel(
            repository = repository,
            defaultActionsProvider = object : SumireSpecialKeyActionEditorDefaultActionsProvider {
                override fun buildDefaultActions(
                    layoutType: String,
                    inputMode: String,
                    keyId: String
                ): Map<SumireSpecialKeyDirection, KeyAction?> =
                    SumireSpecialKeyDirection.entries.associateWith { null }
            },
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "layoutType" to "toggle",
                    "inputMode" to "HIRAGANA",
                    "keyId" to "special_key"
                )
            )
        )
    }

    private fun entity(
        direction: SumireSpecialKeyDirection,
        overrideType: SumireSpecialKeyOverrideType,
        actionString: String? = null,
        inputText: String? = null
    ) = SumireSpecialKeyActionOverrideEntity(
        layoutType = "toggle",
        inputMode = "HIRAGANA",
        keyId = "special_key",
        direction = direction.name,
        overrideType = overrideType.name,
        actionString = actionString,
        inputText = inputText,
        updatedAt = 1L
    )

    private class FakeSumireSpecialKeyDataSource : SumireSpecialKeyDataSource {
        val keyOverrides = MutableStateFlow<List<SumireSpecialKeyActionOverrideEntity>>(emptyList())
        val upserts = mutableListOf<SumireSpecialKeyActionOverrideEntity>()
        val deletedDirections = mutableListOf<DeletedDirection>()

        override fun observeAllActionOverrides(): Flow<List<SumireSpecialKeyActionOverrideEntity>> =
            MutableStateFlow(emptyList())

        override fun observeAllPlacementOverrides(): Flow<List<SumireSpecialKeyPlacementOverrideEntity>> =
            MutableStateFlow(emptyList())

        override fun observeActionOverrides(
            layoutType: String,
            inputMode: String
        ): Flow<List<SumireSpecialKeyActionOverrideEntity>> = MutableStateFlow(emptyList())

        override fun observeActionOverridesForKey(
            layoutType: String,
            inputMode: String,
            keyId: String
        ): Flow<List<SumireSpecialKeyActionOverrideEntity>> = keyOverrides

        override fun observePlacementOverrides(
            layoutType: String,
            inputMode: String
        ): Flow<List<SumireSpecialKeyPlacementOverrideEntity>> = MutableStateFlow(emptyList())

        override suspend fun upsertActionOverride(entity: SumireSpecialKeyActionOverrideEntity) {
            upserts += entity
        }

        override suspend fun deleteActionDirection(
            layoutType: String,
            inputMode: String,
            keyId: String,
            direction: SumireSpecialKeyDirection
        ) {
            deletedDirections += DeletedDirection(layoutType, inputMode, keyId, direction)
        }

        override suspend fun deleteActionKey(layoutType: String, inputMode: String, keyId: String) = Unit
        override suspend fun deleteAllActions(layoutType: String, inputMode: String) = Unit
        override suspend fun upsertPlacementOverrides(entities: List<SumireSpecialKeyPlacementOverrideEntity>) = Unit
        override suspend fun deletePlacementKey(layoutType: String, inputMode: String, keyId: String) = Unit
        override suspend fun deleteAllPlacements(layoutType: String, inputMode: String) = Unit
    }

    private data class DeletedDirection(
        val layoutType: String,
        val inputMode: String,
        val keyId: String,
        val direction: SumireSpecialKeyDirection
    )
}
