package com.kazumaproject.core.data.keyboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

class KeyboardSkinStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var store: KeyboardSkinStore
    private lateinit var definition: ImportedKeyboardSkinDefinition

    @Before
    fun setUp() {
        store = KeyboardSkinStore(temporaryFolder.newFolder("keyboard-skins"))
        definition = parsedExample()
        KeyboardSkinRuntime.clear()
    }

    @After
    fun tearDown() {
        KeyboardSkinRuntime.clear()
    }

    @Test
    fun savesListsAtomicallyAndRejectsDuplicateUntilReplaceIsRequested() {
        assertTrue(store.save(definition, replace = false) is StoreWriteResult.Saved)
        assertEquals(listOf(definition.id), store.list().map { it.definition.id })
        assertTrue(store.fileFor(definition.id).readText().contains("ai-sakura-cyber"))

        assertTrue(store.save(definition, replace = false) is StoreWriteResult.Duplicate)
        val updatedJson = definition.normalizedJson.replaceFirst("桜サイバー", "桜サイバー更新")
        val updated = (KeyboardSkinJsonParser.parse(updatedJson) as KeyboardSkinParseResult.Success).definition
        assertTrue(store.save(updated, replace = true) is StoreWriteResult.Saved)
        assertEquals("桜サイバー更新", store.list().single().definition.name)
    }

    @Test
    fun skipsBrokenAndMismatchedFilesAndDeletesOnlyTheAppCopy() {
        assertTrue(store.save(definition, replace = false) is StoreWriteResult.Saved)
        File(store.fileFor("broken-skin").parentFile, "broken-skin.json").writeText("not json")
        File(store.fileFor("wrong-name").parentFile, "wrong-name.json").writeText(definition.normalizedJson)

        assertEquals(listOf(definition.id), store.list().map { it.definition.id })
        assertTrue(store.delete(definition.id))
        assertFalse(store.fileFor(definition.id).exists())
        assertFalse(store.delete(definition.id))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun runtimeKeepsOnlyCompiledImmutableDefinitionsAndMissingIdsFallBack() {
        KeyboardSkinRuntime.replace(listOf(definition))

        assertEquals(definition, KeyboardSkinRuntime.definitionFor(definition.id))
        assertEquals(definition.spec, KeyboardSkinCatalog.specFor(definition.reference))
        assertNull(KeyboardSkinRuntime.definitionFor("missing-skin"))
        assertEquals(
            KeyboardSkinId.DEFAULT,
            KeyboardSkinCatalog.specFor(KeyboardSkinRef.Imported("missing-skin")).id,
        )
    }

    private fun parsedExample(): ImportedKeyboardSkinDefinition {
        val path = listOf(
            File("docs/keyboard-skins/import-v1/example.json"),
            File("../docs/keyboard-skins/import-v1/example.json"),
            File("../../docs/keyboard-skins/import-v1/example.json"),
        ).firstOrNull(File::isFile) ?: error("example.json is missing")
        return (KeyboardSkinJsonParser.parse(path.readBytes()) as KeyboardSkinParseResult.Success).definition
    }
}
