package com.kazumaproject.markdownhelperkeyboard.local_font

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Test

class LocalFontRepositoryCancellationDeviceTest {
    @Test
    fun cancellingBlockedProviderReadAllowsAnotherSelectionAndRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LocalFontRepository(context)
        val resolver = context.contentResolver
        val authority = "com.kazumaproject.markdownhelperkeyboard.lite.localfonttest"
        val blockedUri = Uri.parse("content://$authority/font/blocked")
        val validUri = Uri.parse("content://$authority/font/valid")
        repository.loadIfNeeded()

        val selections = mutableListOf<kotlinx.coroutines.Job>()
        try {
            resolver.call(blockedUri, "reset", null, null)
            val firstSelection = launch(Dispatchers.IO) { repository.prepare(blockedUri) }
            selections += firstSelection
            waitForBlockedRead(resolver, blockedUri)
            firstSelection.cancel()
            withTimeout(OPERATION_TIMEOUT_MS) { firstSelection.join() }

            val nextSelection = withTimeout(OPERATION_TIMEOUT_MS) { repository.prepare(validUri) }
            repository.cancelPreview(nextSelection)

            resolver.call(blockedUri, "reset", null, null)
            val finalSelection = launch(Dispatchers.IO) { repository.prepare(blockedUri) }
            selections += finalSelection
            waitForBlockedRead(resolver, blockedUri)
            withTimeout(OPERATION_TIMEOUT_MS) { repository.restoreStandard() }
            withTimeout(OPERATION_TIMEOUT_MS) { finalSelection.join() }
            assertNull(repository.state.value.displayName)
        } finally {
            runCatching { resolver.call(blockedUri, "release", null, null) }
            selections.forEach { it.cancelAndJoin() }
        }
    }

    private suspend fun waitForBlockedRead(resolver: android.content.ContentResolver, uri: Uri) {
        withTimeout(OPERATION_TIMEOUT_MS) {
            while (resolver.call(uri, "opened", null, null)?.getBoolean("opened") != true) {
                delay(25)
            }
        }
        delay(300)
    }

    private companion object {
        const val OPERATION_TIMEOUT_MS = 5_000L
    }
}
