package com.kazumaproject.zenz

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ZenzBridgePromptContractTest {

    @Test
    fun promptBuilderOrdersLeftRightInputOutputTags() {
        val source = bridgeSource().readText()
        val builder = promptBuilder(source)

        val leftIndex = builder.indexOf("prompt += leftContextTag + leftContext;")
        val rightIndex = builder.indexOf("prompt += rightContextTag + rightContext;")
        val inputIndex = builder.indexOf("prompt += inputTag + input + outputTag;")

        assertTrue("left context tag must be appended", leftIndex >= 0)
        assertTrue("right context tag must be appended", rightIndex >= 0)
        assertTrue("input/output tags must be appended", inputIndex >= 0)
        assertTrue("left context must come before right context", leftIndex < rightIndex)
        assertTrue("right context must come before input", rightIndex < inputIndex)
    }

    @Test
    fun promptBuilderAddsRightTagOnlyWhenRightContextIsNotEmpty() {
        val source = bridgeSource().readText()
        val builder = promptBuilder(source)

        val tagIndex = source.indexOf("const std::string rightContextTag = u8\"\\uEE07\";")
        val guardIndex = builder.indexOf("if (!rightContext.empty())")
        val appendIndex = builder.indexOf("prompt += rightContextTag + rightContext;")

        assertTrue("right context tag must be U+EE07", tagIndex >= 0)
        assertTrue("right context append must be guarded", guardIndex >= 0)
        assertTrue("right context tag append must be inside the non-empty branch", guardIndex < appendIndex)
    }

    private fun bridgeSource(): File {
        val source = listOf(
            File("src/main/cpp/zenz_bridge.cpp"),
            File("zenz/src/main/cpp/zenz_bridge.cpp"),
            File("../zenz/src/main/cpp/zenz_bridge.cpp")
        ).firstOrNull { it.isFile }
        if (source != null) return source
        fail("zenz_bridge.cpp was not found")
        throw AssertionError("unreachable")
    }

    private fun promptBuilder(source: String): String {
        val builder = Regex(
            pattern = "static std::string build_zenz_prompt[\\s\\S]*?return prompt;\\s*\\}",
            options = setOf(RegexOption.MULTILINE)
        ).find(source)?.value
        if (builder != null) return builder
        fail("build_zenz_prompt was not found")
        throw AssertionError("unreachable")
    }
}
