package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsLoadingUiTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun firstCompletionCannotHideAnotherPendingLoad() = runTest {
        val ui = SettingsLoadingUi(context, {})
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        launch { ui.load { first.await() } }
        launch { ui.load { second.await() } }
        runCurrent()
        assertEquals(SettingsLoadingState.Loading, ui.state)
        first.complete(Unit)
        runCurrent()
        assertEquals(SettingsLoadingState.Loading, ui.state)
        second.complete(Unit)
        runCurrent()
        assertEquals(SettingsLoadingState.Ready, ui.state)
    }

    @Test fun failureIsRetainedUntilPendingWorkFinishesAndRetryClearsIt() = runTest {
        val ui = SettingsLoadingUi(context, {})
        val other = CompletableDeferred<Unit>()
        launch { ui.load { other.await() } }
        launch { ui.load { throw IllegalStateException("controlled failure") } }
        runCurrent()
        assertEquals(SettingsLoadingState.Loading, ui.state)
        other.complete(Unit)
        runCurrent()
        assertTrue(ui.state is SettingsLoadingState.Error)
        ui.load { Unit }
        assertEquals(SettingsLoadingState.Ready, ui.state)
    }

    @Test fun cancellationDoesNotBecomeALoadingError() = runTest {
        val ui = SettingsLoadingUi(context, {})
        val pending = CompletableDeferred<Unit>()
        val job = launch { ui.load { pending.await() } }
        runCurrent()
        job.cancel()
        runCurrent()
        assertEquals(SettingsLoadingState.Ready, ui.state)
    }
}
