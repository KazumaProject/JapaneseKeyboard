package com.kazumaproject.markdownhelperkeyboard.local_font

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.util.AtomicFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

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
        assertEquals(LocalFontReadResult.Stored(LocalFontStoredState(record)), store.readState())
        store.writeState(null)
        assertEquals(LocalFontReadResult.Stored(LocalFontStoredState(null)), store.readState())
    }

    @Test fun distinguishesMissingStateFromUnreadableState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "local-font-invalid-state-test-${System.nanoTime()}")
        val store = LocalFontStore(root)
        val stateFile = File(root, "custom_fonts/state.json")

        assertEquals(LocalFontReadResult.Missing, store.readState())
        stateFile.writeText("not json")
        assertEquals(LocalFontReadResult.Failed, store.readState())
    }

    @Test @Config(sdk = [26]) fun restoresOreoBackupAndKeepsRecordedFontDuringGarbageCollection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "local-font-backup-test-${System.nanoTime()}")
        val store = LocalFontStore(root)
        val fontBytes = byteArrayOf(1, 2, 3)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(fontBytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        val record = LocalFontRecord(
            id = "a1234567-1234-1234-1234-123456789abc",
            extension = "ttf",
            displayName = "User font.ttf",
            sizeBytes = fontBytes.size.toLong(),
            sha256 = sha256,
        )
        val fontFile = store.fileFor(record).apply { writeBytes(fontBytes) }
        store.writeState(record)

        val stateFile = File(root, "custom_fonts/state.json")
        val interruptedWrite = AtomicFile(stateFile).startWrite()
        interruptedWrite.close()
        assertTrue(File("${stateFile.path}.bak").exists())
        assertTrue(stateFile.delete())
        assertFalse(stateFile.exists())

        assertEquals(LocalFontReadResult.Stored(LocalFontStoredState(record)), store.readState())
        assertTrue(stateFile.isFile)
        assertFalse(File("${stateFile.path}.bak").exists())
        store.collectGarbage(activeId = record.id, previewId = null)
        assertTrue("Recovered font copy should remain active", fontFile.isFile)
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
