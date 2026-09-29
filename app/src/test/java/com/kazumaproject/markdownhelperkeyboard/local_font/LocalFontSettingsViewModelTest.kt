package com.kazumaproject.markdownhelperkeyboard.local_font

import android.graphics.Typeface
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class LocalFontSettingsViewModelTest {
    @Test
    fun failedStandardRestoreClearsDiscardedPreviewAndDisablesApplyState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = mock<LocalFontRepository>()
        val active = LocalFontState(
            snapshot = KeyboardFontSnapshot(Typeface.DEFAULT, revision = 4),
            displayName = "Active font",
        )
        val preview = LocalFontPreview(
            generation = 7,
            record = LocalFontRecord("preview-id", "ttf", "Preview font", 1, "hash"),
            typeface = Typeface.create("serif", Typeface.NORMAL),
            displayName = "Preview font",
        )
        whenever(repository.state).thenReturn(MutableStateFlow(active))
        whenever(repository.loadIfNeeded()).thenReturn(active)
        whenever(repository.prepare(any())).thenReturn(preview)
        whenever(repository.cancelPreview(anyOrNull())).thenReturn(Unit)
        whenever(repository.restoreStandard()).thenAnswer { throw IOException("state write failed") }

        val viewModel = LocalFontSettingsViewModel(repository)
        val viewModelStore = ViewModelStore().apply { put("font-settings", viewModel) }
        try {
            runCurrent()
            viewModel.select(Uri.parse("content://fonts/preview"))
            runCurrent()

            assertEquals("Preview font", viewModel.uiState.value.previewName)
            assertSame(preview.typeface, viewModel.uiState.value.previewTypeface)

            viewModel.restoreStandard()
            runCurrent()

            val state = viewModel.uiState.value
            assertFalse(state.busy)
            assertEquals("Active font", state.currentName)
            assertNull(state.previewName)
            assertNull(state.previewTypeface)
            assertEquals(LocalFontUiError.STORAGE, state.error)

            viewModel.applyPreview()
            runCurrent()
            verify(repository, never()).apply(preview)
        } finally {
            viewModelStore.clear()
            Dispatchers.resetMain()
        }
    }
}
