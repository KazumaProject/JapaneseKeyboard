package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.*
import org.junit.Test

class FreeFlickSensitivityDirectionTest {
    private fun session(width: Float = 209f, height: Float = 134f, threshold: Float = 73.5f,
        originX: Float = 0f, originY: Float = 0f,
        config: PopupKeyConfig = SumirePopupDefaults.create("は"), nearest: Boolean = true): PopupMapSession =
        PopupMapSession(config, width, height,
            PopupMapInputOptions(nearestEnabled = nearest, tapSizeMode = PopupTapSizeMode.FLICK_SENSITIVITY),
            PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY, width, height, threshold), originX, originY)

    @Test fun recordedJapaneseTwelveKeyGesturesUseTheSameDirectionsAtTheCenterAndEdges() {
        // Relative screen-pixel traces compared against Gboard 14.2 on API 35.
        val traces = listOf(
            floatArrayOf(0f, 0f, 90f, 70f) to "へ",
            floatArrayOf(0f, 0f, 180f, 140f) to "へ",
            floatArrayOf(0f, 66f, 90f, 15f) to "へ",
            floatArrayOf(0f, -66f, 90f, -15f) to "へ",
            floatArrayOf(0f, 0f, -90f, -70f) to "ひ",
            floatArrayOf(0f, 0f, 0f, -90f) to "ふ",
            floatArrayOf(0f, 0f, 0f, 90f) to "ほ",
            floatArrayOf(0f, 0f, 0f, 0f) to "は",
        )
        for ((trace, expected) in traces) {
            val ox = trace[0] / 209f; val oy = trace[1] / 134f
            val session = session(originX = ox, originY = oy)
            for (step in 1..12) session.move(ox + trace[2] / 209f * step / 12, oy + trace[3] / 134f * step / 12)
            assertEquals(trace.toList().toString(), expected, session.release(ox + trace[2] / 209f, oy + trace[3] / 134f))
        }
    }

    @Test fun extendingTheSameRayDoesNotChangeItsDirectionAcrossTheKeyBoundary() {
        for ((w, h) in listOf(100f to 100f, 200f to 100f, 100f to 200f)) {
            val session = session(w, h, 20f)
            for (distance in listOf(30f, 60f, 90f, 120f, 150f)) {
                assertEquals("${w}x$h distance=$distance", "へ", session.release(distance / w, distance * .53333336f / h))
            }
        }
    }

    @Test fun savedRegionOrderDoesNotAffectDirectionalSelection() {
        val original = SumirePopupDefaults.create("は")
        val reversed = original.withMap(original.maps.single().let { it.copy(regions = it.regions.reversed()) })
        assertEquals("へ", session(200f, 100f, 20f, config = reversed).release(.15f, .16f))
    }

    @Test fun customContoursKeepGeometricNearestSelection() {
        val original = SumirePopupDefaults.create("は")
        val shaped = original.withMap(original.maps.single().let { map ->
            map.copy(regions = map.regions.mapIndexed { index, region ->
                if (index == 0) region.copy(outline = PopupOutline()) else region
            })
        })
        assertEquals("ほ", session(200f, 100f, 20f, config = shaped).release(.15f, .16f))
    }

    @Test fun disablingNearestRetainsDirectHitsOnDisplayedRegions() {
        assertEquals("ほ", session(originY = 66f / 134f, nearest = false).release(90f / 209f, 81f / 134f))
    }
}
