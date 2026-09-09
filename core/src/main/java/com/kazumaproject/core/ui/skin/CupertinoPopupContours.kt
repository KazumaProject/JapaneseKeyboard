package com.kazumaproject.core.ui.skin

import android.graphics.Path

/** Vector contours measured from lossless iOS 26.4.1 kana popup silhouettes.
 * Samples are simplified at <=0.65 reference pixel; no raster assets or glyphs are embedded.
 */
internal object CupertinoPopupContours {
    private val left = floatArrayOf(
        0.0562500f, 0.0000000f, 0.0281250f, 0.0178571f, 0.0093750f, 0.0535714f, 0.0000000f, 0.1071429f, 0.0000000f, 0.8869048f, 0.0093750f, 0.9404762f, 0.0281250f, 0.9761905f, 0.0593750f, 0.9940476f, 0.7875000f, 1.0000000f, 0.8093750f, 0.9940476f, 0.8250000f, 0.9821429f, 0.8562500f, 0.9285714f, 0.8687500f, 0.8809524f, 0.8781250f, 0.8630952f, 0.9093750f, 0.7678571f, 0.9187500f, 0.7500000f, 0.9562500f, 0.6369048f, 0.9906250f, 0.5476190f, 0.9968750f, 0.5119048f, 0.9968750f, 0.4940476f, 0.9906250f, 0.4583333f, 0.9562500f, 0.3690476f, 0.9187500f, 0.2559524f, 0.9093750f, 0.2380952f, 0.8781250f, 0.1428571f, 0.8687500f, 0.1250000f, 0.8562500f, 0.0773810f, 0.8250000f, 0.0238095f, 0.7906250f, 0.0059524f
    )
    private val top = floatArrayOf(
        0.0736434f, 0.0000000f, 0.0542636f, 0.0042017f, 0.0310078f, 0.0168067f, 0.0077519f, 0.0462185f, 0.0038760f, 0.0756303f, 0.0000000f, 0.0798319f, 0.0000000f, 0.6848739f, 0.0038760f, 0.6890756f, 0.0077519f, 0.7142857f, 0.0193798f, 0.7352941f, 0.0426357f, 0.7605042f, 0.0658915f, 0.7773109f, 0.4767442f, 0.9873950f, 0.5038760f, 0.9957983f, 0.5232558f, 0.9915966f, 0.9341085f, 0.7815126f, 0.9573643f, 0.7647059f, 0.9806202f, 0.7394958f, 0.9922481f, 0.7184874f, 0.9961240f, 0.6932773f, 1.0000000f, 0.6890756f, 1.0000000f, 0.0840336f, 0.9961240f, 0.0798319f, 0.9922481f, 0.0504202f, 0.9844961f, 0.0378151f, 0.9689922f, 0.0210084f, 0.9457364f, 0.0084034f, 0.9263566f, 0.0042017f
    )
    fun path(width: Float, height: Float, direction: PopupDirection): Path {
        val points = if (direction == PopupDirection.LEFT || direction == PopupDirection.RIGHT) left else top
        return Path().apply {
            for (i in points.indices step 2) {
                val x = (if (direction == PopupDirection.RIGHT) 1f - points[i] else points[i]) * width
                val y = (if (direction == PopupDirection.BOTTOM) 1f - points[i+1] else points[i+1]) * height
                if (i == 0) moveTo(x,y) else lineTo(x,y)
            }
            close()
        }
    }
}
