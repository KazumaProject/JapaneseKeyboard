package com.kazumaproject.core.ui.skin

import android.graphics.Shader

/** Smooth illumination field fitted from opaque iOS dark edge-preview surfaces (degree 6).
 * No text or bitmap imagery is stored. Held-out spatial samples are reported by the verifier.
 */
internal object CupertinoKeyPreviewIllumination {
    private val q = floatArrayOf(66.23103727f, 31.72536268f, 19.16423913f, -20.18404092f, -37.67877984f, 6.01343472f, 19.40827464f, -1.14446856f, -4.39457713f, -4.22021953f, 0.80870767f, 3.51292222f, 1.34286520f, 2.67613014f, -4.15758208f, -7.29148615f, 4.03475986f, 5.86425507f, -0.16577629f, -0.12543586f, 3.29528095f, 2.07091574f, -8.10239770f, 2.90226628f, 5.02798260f, 1.24309307f, 2.40717854f, 6.29259223f)
    private val p = floatArrayOf(66.13524220f, 30.84606576f, 18.16729395f, -18.79958940f, -36.11545839f, 5.51204163f, 18.65514532f, 0.50211012f, 5.43899220f, 2.64569044f, -4.96290781f, -2.75902830f, 1.46901004f, 1.23397095f, -3.42352603f, -4.83848143f, 3.94277968f, 5.00484883f, 1.14535501f, -1.64272968f, -2.55229854f, -1.19723804f, -4.52251830f, 0.91920935f, 2.66127059f, -1.35179250f, -0.41028446f, 2.86405221f)
    private val middle = floatArrayOf(66.61176827f, 28.42515993f, 17.69848204f, -14.93025100f, -36.41279354f, 3.49736636f, 19.13480819f, -1.27112068f, 2.25926661f, 1.63368479f, -4.26502887f, -1.05051998f, 2.70345792f, -0.71260326f, -6.42916133f, -6.95853267f, 3.63563980f, 5.78549875f, -0.72053564f, -1.85204618f, 2.28190390f, 0.74850191f, -0.25927262f, -2.10058212f, -2.41011954f, 1.30776079f, 3.80906016f, 0.18455738f)
    private val lower = floatArrayOf(81.12914274f, -8.62222667f, 17.35002958f, 31.38246562f, -38.45324674f, -19.69897658f, 17.76691793f, 0.12229099f, 0.61167672f, 5.94024158f, 5.37015333f, -4.15591390f, -4.98242945f, 2.22781224f, -14.21083466f, -14.76155867f, 10.39427903f, 14.00337178f, 2.36869145f, 2.19035549f, -6.63765395f, -4.34578069f, -1.71343618f, -1.32729509f, -1.98375780f, -1.94653650f, -4.24208287f, 0.41227235f)
    private val lowerRight = floatArrayOf(80.59744858f, -8.82455088f, 12.25550693f, 25.37769284f, -34.53417381f, -14.19271801f, 18.54824168f, -0.10130124f, -3.63086846f, -0.77644563f, 5.20222304f, 0.96394620f, -2.85373962f, 2.07593830f, -8.64496688f, -6.18655792f, 7.32364563f, 8.78398339f, 0.39999088f, 0.83687952f, -3.36295867f, -1.37806663f, -3.94336881f, -7.02920962f, -9.25080891f, -1.05919102f, -2.70102216f, 2.33522052f)
    private val lowerFields by lazy {
        Array(17) { step -> FloatArray(lower.size) { i -> lower[i] + (lowerRight[i] - lower[i]) * step / 16f } }
    }
    fun shader(width: Int,height: Int,rightEdge: Boolean, center: Boolean = false, lowerRowPosition: Float? = null): Shader {
        val coefficients=if(lowerRowPosition != null) lowerFields[kotlin.math.round(lowerRowPosition.coerceIn(0f,1f)*16).toInt()] else if(center)middle else if(rightEdge)p else q
        return CupertinoIllumination.shader(coefficients, width, height, 1)
    }
}
