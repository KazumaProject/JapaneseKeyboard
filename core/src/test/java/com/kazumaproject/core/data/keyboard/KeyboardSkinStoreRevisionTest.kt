package com.kazumaproject.core.data.keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KeyboardSkinStoreRevisionTest {

    @Test
    fun appBoundStoreIncrementsRevisionForSaveAndDelete() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "keyboard-skin-revision-${System.nanoTime()}")
        val store = KeyboardSkinStore(directory, context)
        try {
            val templateFile = listOf(
                File("docs/keyboard-skins/import-v1/template.json"),
                File("../docs/keyboard-skins/import-v1/template.json"),
                File("../../docs/keyboard-skins/import-v1/template.json"),
            ).firstOrNull(File::isFile) ?: error("template.json is missing")
            val definition = (KeyboardSkinJsonParser.parse(templateFile.readBytes()) as KeyboardSkinParseResult.Success).definition
            val beforeSave = KeyboardSkinStore.revision(context)

            assertTrue(store.save(definition, replace = false) is StoreWriteResult.Saved)
            assertEquals(beforeSave + 1L, KeyboardSkinStore.revision(context))
            assertTrue(store.delete(definition.id))
            assertEquals(beforeSave + 2L, KeyboardSkinStore.revision(context))
        } finally {
            directory.deleteRecursively()
        }
    }
}
