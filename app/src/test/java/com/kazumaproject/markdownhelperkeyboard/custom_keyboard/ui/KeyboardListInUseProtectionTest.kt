package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.repository.CustomKeyboardDeleteImpact
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardListInUseProtectionTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun customLayoutsAreBadgedAndDeletionIsBlockedWhileCustomInputMethodIsSelected() =
        runTest(dispatcher) {
            val layout = layout()
            val repository = repositoryWith(layout)
            AppPreference.keyboard_order = listOf(KeyboardType.TENKEY, KeyboardType.CUSTOM)
            val viewModel = KeyboardListViewModel(repository, AppPreference)
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.layoutItems.collect {}
            }
            advanceUntilIdle()

            assertTrue(viewModel.layoutItems.value.single().isInUse)

            viewModel.requestDeleteLayout(layout.layoutId)
            advanceUntilIdle()

            assertTrue(viewModel.deleteEvents.first() is KeyboardDeleteEvent.BlockedInUse)
            verify(repository, never()).getDeleteImpactForLayout(any())
            verify(repository, never()).deleteLayoutConfirmed(any())
            collection.cancel()
        }

    @Test
    fun removingCustomInputMethodClearsBadgeAndAllowsDeletion() = runTest(dispatcher) {
        val layout = layout()
        val repository = repositoryWith(layout)
        whenever(repository.getDeleteImpactForLayout(layout.layoutId)).thenReturn(
            CustomKeyboardDeleteImpact(
                layoutId = layout.layoutId,
                layoutName = layout.name,
                stableId = layout.stableId,
                references = emptyList(),
            )
        )
        AppPreference.keyboard_order = listOf(KeyboardType.TENKEY, KeyboardType.CUSTOM)
        val viewModel = KeyboardListViewModel(repository, AppPreference)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.layoutItems.collect {}
        }
        advanceUntilIdle()

        assertTrue(viewModel.layoutItems.value.single().isInUse)

        AppPreference.keyboard_order = listOf(KeyboardType.TENKEY)
        viewModel.refreshCustomKeyboardUsage()
        advanceUntilIdle()

        assertFalse(viewModel.layoutItems.value.single().isInUse)

        viewModel.requestDeleteLayout(layout.layoutId)
        advanceUntilIdle()

        verify(repository).deleteLayoutConfirmed(layout.layoutId)
        assertTrue(viewModel.deleteEvents.first() is KeyboardDeleteEvent.Deleted)
        collection.cancel()
    }

    @Test
    fun referenceConfirmationCannotBypassAReenabledCustomInputMethodLock() =
        runTest(dispatcher) {
            val layout = layout()
            val repository = repositoryWith(layout)
            AppPreference.keyboard_order = listOf(KeyboardType.CUSTOM)
            val viewModel = KeyboardListViewModel(repository, AppPreference)

            viewModel.confirmDeleteWithReferences(layout.layoutId)
            advanceUntilIdle()

            assertTrue(viewModel.deleteEvents.first() is KeyboardDeleteEvent.BlockedInUse)
            verify(repository, never()).deleteLayoutConfirmed(any())
        }

    private fun repositoryWith(layout: CustomKeyboardLayout): KeyboardRepository =
        mock<KeyboardRepository>().also { repository ->
            whenever(repository.getLayouts()).thenReturn(MutableStateFlow(listOf(layout)))
        }

    private fun layout() = CustomKeyboardLayout(
        layoutId = 32L,
        name = "Active layout",
        columnCount = 5,
        rowCount = 4,
        stableId = "active-layout",
    )
}
