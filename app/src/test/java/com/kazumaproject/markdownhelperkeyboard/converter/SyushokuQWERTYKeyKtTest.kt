package com.kazumaproject.markdownhelperkeyboard.converter

import org.junit.Test

class SyushokuQWERTYKeyKtTest {
    @Test
    fun testShushokuKey(){
        val input = "たんこ"
        val combinations = generateCombinations(input)
        val input2 = "かつこう"
        val combinations2 = generateCombinations(input2)
        println(combinations)
        println(combinations2)
    }
}
