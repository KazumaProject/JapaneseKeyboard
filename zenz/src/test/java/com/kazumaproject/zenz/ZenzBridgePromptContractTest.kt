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

        val leftTagIndex = builder.indexOf("prompt += leftContextTag;")
        val leftContextIndex = builder.indexOf("prompt += leftContext;")
        val rightTagIndex = builder.indexOf("prompt += rightContextTag;")
        val rightContextIndex = builder.indexOf("prompt += rightContext;")
        val inputTagIndex = builder.indexOf("prompt += inputTag;")
        val inputIndex = builder.indexOf("prompt += input;")
        val outputTagIndex = builder.indexOf("prompt += outputTag;")

        assertTrue("left context tag must be appended", leftTagIndex >= 0)
        assertTrue("left context must be appended", leftContextIndex >= 0)
        assertTrue("right context tag must be appended", rightTagIndex >= 0)
        assertTrue("right context must be appended", rightContextIndex >= 0)
        assertTrue("input tag must be appended", inputTagIndex >= 0)
        assertTrue("input must be appended", inputIndex >= 0)
        assertTrue("output tag must be appended", outputTagIndex >= 0)
        assertTrue("left tag must come before left context", leftTagIndex < leftContextIndex)
        assertTrue("left context must come before right tag", leftContextIndex < rightTagIndex)
        assertTrue("right tag must come before right context", rightTagIndex < rightContextIndex)
        assertTrue("right context must come before input tag", rightContextIndex < inputTagIndex)
        assertTrue("input tag must come before input", inputTagIndex < inputIndex)
        assertTrue("input must come before output tag", inputIndex < outputTagIndex)
    }

    @Test
    fun promptBuilderAddsRightTagOnlyWhenRightContextIsNotEmpty() {
        val source = bridgeSource().readText()
        val builder = promptBuilder(source)

        val tagIndex = Regex("""rightContextTag\[\]\s*=\s*u8"\\uEE07"""").find(source)?.range?.first ?: -1
        val guardIndex = builder.indexOf("if (!rightContext.empty())")
        val appendIndex = builder.indexOf("prompt += rightContextTag;")

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
