package com.kazumaproject.markdownhelperkeyboard.local_font

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalFontStoreTest {
    private fun newStore(): LocalFontStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return LocalFontStore(File(context.cacheDir, "local-font-store-test-${System.nanoTime()}"))
    }

    @Test fun persistsOnlyTheSingleActiveFontRecordAndCanRestoreStandard() {
        val store = newStore()
        val record = LocalFontRecord(
            id = "a1234567-1234-1234-1234-123456789abc",
            extension = "otf",
            displayName = "User font.otf",
            sizeBytes = 256,
            sha256 = "a".repeat(64),
        )

        store.writeState(record)
        assertEquals(record, store.readState()?.record)
        store.writeState(null)
        assertNull(store.readState()?.record)
    }

    @Test fun rejectsPathComponentsAndUnsupportedExtensions() {
        val store = newStore()
        assertThrows(IllegalArgumentException::class.java) {
            store.fileFor(LocalFontRecord("../escape", "ttf", "bad", 1, "a".repeat(64)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.fileFor(LocalFontRecord("a1234567-1234-1234-1234-123456789abc", "woff", "bad", 1, "a".repeat(64)))
        }
    }

    @Test fun sanitizesProviderNamesBeforeStoringThem() {
        assertEquals("font name.ttf", LocalFontStore.sanitizeDisplayName("../font\nname.ttf"))
        assertEquals("Local font", LocalFontStore.sanitizeDisplayName("\u0000 / "))
    }
}
