package com.kazumaproject.markdownhelperkeyboard.learning.multiple

import org.junit.Before
import org.junit.Test

class LearnMultipleTest {

    private lateinit var learnMultiple: LearnMultiple

    @Before
    fun setUp() {
        learnMultiple = LearnMultiple()
    }

    @Test
    fun `LearnMultiple の使用方法`() {
        val yomi = "じろう"
        learnMultiple.apply {
            start()
            setInput(yomi)
            setWordToStringBuilder("二")
            setWordToStringBuilder("郎")
        }
        println("${learnMultiple.getInputAndStringBuilder()} ${learnMultiple.enabled()}")
        learnMultiple.stop()

        println("${learnMultiple.getInputAndStringBuilder()} ${learnMultiple.enabled()}")
    }
}