package com.kazumaproject.core.ui.skin

/** Measured dark variation-container lighting; selection layout remains keyboard-owned. */
internal object CupertinoVariationIllumination {
    private val coefficients = floatArrayOf(59.47080466f, 10.15125911f, 0.17550128f, 1.54443538f, 1.96814287f, -0.52384167f, 0.04739240f, -0.34698783f, -0.47878484f, 0.38072059f, 0.75511030f, -0.24691024f, -0.47106114f, -14.36535929f, -1.26795435f, 2.26469937f, 0.75621700f, -0.70182788f, 1.52372247f, 1.21640486f, -0.37914731f, -0.42950034f, 41.32053073f, 1.67818665f, -0.26809228f, -1.51170631f, -0.83550913f, -32.35127413f)
    fun shader(width: Int, height: Int) = CupertinoIllumination.shader(coefficients, width, height)
}
