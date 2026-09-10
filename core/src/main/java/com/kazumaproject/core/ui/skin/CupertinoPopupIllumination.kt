package com.kazumaproject.core.ui.skin

import android.graphics.Shader

/** Smooth illumination field fitted from opaque iOS dark kana surfaces (degree 6).
 * No text or bitmap imagery is stored. Held-out spatial samples are reported by the verifier.
 */
internal object CupertinoPopupIllumination {
    private val left = floatArrayOf(65.90633196f, -0.73561342f, -4.29819396f, 0.23745823f, 1.92876745f, -0.42833287f, -1.30081644f, -14.15336375f, -0.40897419f, 0.38729521f, 1.52483875f, 0.17754722f, -0.47584543f, -28.37474079f, -0.26590651f, -1.20301052f, 0.30869057f, 2.03544454f, 9.48670121f, 1.06973074f, 1.54929119f, -1.33039694f, 26.23944168f, 0.45245365f, 1.81493967f, 0.50332774f, -0.47044894f, -8.00019616f)
    private val right = floatArrayOf(67.48793735f, -0.72646072f, -5.45510725f, 0.33139367f, 1.73442000f, -0.23437891f, -0.45113913f, 14.50436220f, 0.51403336f, -1.23890348f, -0.47724252f, 0.33120101f, 0.39481370f, -32.73589385f, 0.53991219f, 5.64786879f, -0.10219171f, -1.97287730f, -9.37664135f, -2.31377708f, 0.00803734f, 0.44598226f, 32.17486764f, -0.48716088f, -2.80348489f, -1.00943147f, 1.75578239f, -11.19126001f)
    private val top = floatArrayOf(64.73328660f, -3.74425475f, -24.09179612f, 10.14268868f, 17.52421576f, -4.21703177f, -5.57271407f, -0.12299945f, -0.19643169f, 0.53806706f, 0.48708026f, -0.49146695f, -0.27602461f, -13.67501665f, 1.57427534f, 9.82634867f, -7.37191743f, -7.83777557f, 0.52984377f, 0.48506095f, -1.06157120f, -1.29150858f, 7.49573253f, 0.25047353f, -4.26662864f, -0.37897149f, -0.21488585f, -1.93542001f)
    private val bottom = floatArrayOf(65.44462394f, 3.12899464f, -19.79043962f, -1.90037867f, 16.32402286f, -0.27023091f, -5.19661059f, 0.44104370f, 0.91211154f, -1.04369483f, -1.08822529f, 2.56121264f, -1.47246076f, -13.42302920f, 2.28537632f, 3.78488334f, 0.49000519f, -3.24124307f, -0.40337139f, -2.47687501f, -0.87484653f, 2.14282468f, 7.12660464f, -2.10556304f, 1.75831249f, 0.25555920f, 1.56947817f, -2.18126280f)
    fun shader(width: Int, height: Int, direction: PopupDirection): Shader {
        val coefficients = when(direction) {
            PopupDirection.LEFT -> left
            PopupDirection.RIGHT -> right
            PopupDirection.TOP -> top
            else -> bottom
        }
        return CupertinoIllumination.shader(coefficients, width, height, 1)
    }
}
