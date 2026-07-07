package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class KeyboardTouchEffectContainerContractTest {

    @Test
    fun normalKeyboardTouchEffectsAreOutsideBackgroundContainer() {
        listOf(
            "res/layout/main_layout.xml",
            "res/layout-land/main_layout.xml",
            "res/layout-sw600dp/main_layout.xml"
        ).forEach { path ->
            val document = parseMainXml(path)
            val backgroundChildren = childIdsOf(document, "keyboard_background_container")
            val touchEffectChildren = childIdsOf(document, "keyboard_touch_effect_container")

            assertTrue(
                "$path background container should keep only background media",
                backgroundChildren == listOf("keyboard_background_video", "keyboard_background_image")
            )
            normalEffectIds.forEach { id ->
                assertFalse("$path background container must not contain $id", id in backgroundChildren)
                assertTrue("$path touch effect container must contain $id", id in touchEffectChildren)
            }
        }
    }

    @Test
    fun floatingKeyboardTouchEffectsAreOutsideBackgroundContainer() {
        val document = parseMainXml("res/layout/floating_keyboard_layout.xml")
        val backgroundChildren = childIdsOf(document, "floating_keyboard_background_container")
        val touchEffectChildren = childIdsOf(document, "floating_keyboard_touch_effect_container")

        assertTrue(
            "floating background container should keep only background media",
            backgroundChildren == listOf(
                "floating_keyboard_background_video",
                "floating_keyboard_background_image"
            )
        )
        floatingEffectIds.forEach { id ->
            assertFalse("floating background container must not contain $id", id in backgroundChildren)
            assertTrue("floating touch effect container must contain $id", id in touchEffectChildren)
        }
    }

    @Test
    fun touchDispatchUsesTouchEffectContainers() {
        val lines = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readLines()
        val mainSetup = functionBody(lines, "setupMainKeyboardTouchEffect").joinToString("\n")
        val floatingSetup = functionBody(lines, "setupFloatingKeyboardTouchEffect").joinToString("\n")

        assertTrue(mainSetup.contains("targetContainer = mainView.keyboardTouchEffectContainer"))
        assertFalse(mainSetup.contains("targetContainer = mainView.keyboardBackgroundContainer"))
        assertTrue(floatingSetup.contains("targetContainer = floatingView.floatingKeyboardTouchEffectContainer"))
        assertFalse(floatingSetup.contains("targetContainer = floatingView.floatingKeyboardBackgroundContainer"))
    }

    @Test
    fun touchEffectBoundsStayOnKeyboardBodyToAvoidCandidateHeightResizeFlicker() {
        val lines = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readLines()
        val source = lines.joinToString("\n")
        val normalBounds =
            functionBody(lines, "updateNormalKeyboardTouchEffectBounds").joinToString("\n")
        val floatingBounds =
            functionBody(lines, "updateFloatingKeyboardTouchEffectBounds").joinToString("\n")

        assertTrue(normalBounds.contains("keyboardBodyHeightPx: Int"))
        assertTrue(source.contains("keyboardBodyHeightPx = heightPx"))
        assertFalse(source.contains("touchEffectHeightPx = backgroundSurfaceHeight"))
        assertFalse(source.contains("keyboardBodyHeightPx = backgroundSurfaceHeight"))

        assertTrue(floatingBounds.contains("floatingView.floatingSymbolKeyboard"))
        assertTrue(floatingBounds.contains("floatingView.candidatesRowView"))
        assertTrue(floatingBounds.contains("floatingView.floatingKeyboardContainer"))
        assertFalse(floatingBounds.contains("floatingView.floatingKeyboardBackgroundContainer"))
    }

    @Test
    fun rendererResizeSurfaceHasNoOpGuards() {
        listOf(
            "FluidInkRenderer.kt",
            "LiquidRippleRenderer.kt",
            "SprayPaintRenderer.kt"
        ).forEach { fileName ->
            val lines = mainFile(
                "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/$fileName"
            ).readLines()
            val resizeSurface = functionBody(lines, "resizeSurface").joinToString("\n")

            assertTrue(resizeSurface.contains("if (width <= 0 || height <= 0) return@postOnRenderer"))
            assertTrue(resizeSurface.contains("if (width == surfaceWidth && height == surfaceHeight) return@postOnRenderer"))
        }
    }

    private fun parseMainXml(path: String): Element {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(mainFile(path))
        document.documentElement.normalize()
        return document.documentElement
    }

    private fun childIdsOf(root: Element, parentId: String): List<String> {
        val parent = elementWithId(root, parentId)
        val children = parent.childNodes
        return buildList {
            for (index in 0 until children.length) {
                val child = children.item(index) as? Element ?: continue
                idName(child)?.let(::add)
            }
        }
    }

    private fun elementWithId(root: Element, id: String): Element {
        val nodes = root.ownerDocument.getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            if (idName(element) == id) return element
        }
        error("Missing view id $id")
    }

    private fun idName(element: Element): String? {
        val value = element.getAttribute("android:id").takeIf { it.isNotBlank() }
            ?: element.getAttribute("id").takeIf { it.isNotBlank() }
            ?: return null
        return value.substringAfter("@+id/").substringAfter("@id/")
    }

    private fun functionBody(lines: List<String>, functionName: String): List<String> {
        val start = lines.indexOfFirst { it.contains("fun $functionName") }
        assertTrue("Missing function $functionName", start >= 0)

        var depth = 0
        var seenBody = false
        for (index in start until lines.size) {
            val line = lines[index]
            depth += line.count { it == '{' }
            seenBody = seenBody || line.contains('{')
            depth -= line.count { it == '}' }
            if (seenBody && depth == 0) {
                return lines.subList(start, index + 1)
            }
        }
        error("Missing function body end for $functionName")
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }

    private companion object {
        val normalEffectIds = listOf(
            "suminagashi_ink_view",
            "liquid_ripple_effect_view",
            "spray_paint_effect_view",
            "luminous_blob_effect_view",
            "cinematic_wave_effect_view"
        )
        val floatingEffectIds = listOf(
            "floating_suminagashi_ink_view",
            "floating_liquid_ripple_effect_view",
            "floating_spray_paint_effect_view",
            "floating_luminous_blob_effect_view",
            "floating_cinematic_wave_effect_view"
        )
    }
}
