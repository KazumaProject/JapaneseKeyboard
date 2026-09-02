package com.kazumaproject.markdownhelperkeyboard.converter.utility

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_FORMULA_TEX
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_FORMULA_UNICODE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaParserTest {
    private val parser = FormulaParser()
    private val provider = UtilityCandidateProvider()

    @Test
    fun requiredShorthandAndLatexExamplesHaveExactOutputs() {
        val expected = mapOf(
            "25^2" to ("25²" to "25^{2}"),
            "x_1^(n+1)" to ("x₁⁽ⁿ⁺¹⁾" to "x_{1}^{n+1}"),
            "1/2" to ("½" to "\\frac{1}{2}"),
            "12/34" to ("¹²⁄₃₄" to "\\frac{12}{34}"),
            "\\frac{a+b}{c+d}" to ("(a+b)⁄(c+d)" to "\\frac{a+b}{c+d}"),
            "sqrt(x^2+y^2)" to ("√(x²+y²)" to "\\sqrt{x^{2}+y^{2}}"),
            "sum(i=1,n,i^2)" to ("∑ᵢ₌₁ⁿ i²" to "\\sum_{i=1}^{n}i^{2}"),
        )

        expected.forEach { (source, output) ->
            val parsed = parser.parse(source)
            assertTrue(source, parsed != null)
            assertEquals(source, output.first, parsed?.unicodeText)
            assertEquals(source, output.second, parsed?.normalizedTex)
        }
    }

    @Test
    fun formulaCandidatesKeepCommitTextSeparateFromCanvasPresentation() {
        val utilityCandidates = provider.provide("25^2").candidates
        val candidates = UtilityCandidateComposer.compose(
            input = "25^2",
            existingCandidates = emptyList(),
            result = provider.provide("25^2"),
        )
        assertEquals(listOf("25²", "25^{2}"), utilityCandidates.map { it.text })
        assertEquals(CANDIDATE_TYPE_FORMULA_UNICODE, candidateType(candidates[0]))
        assertEquals(CANDIDATE_TYPE_FORMULA_TEX, candidateType(candidates[1]))
        assertEquals("25²", candidates[0].presentation?.unicodeText)
        assertEquals("25^{2}", candidates[1].presentation?.normalizedTex)
        assertEquals(listOf("25²", "25^{2}", "25^2"), candidates.map { it.commitText })
    }

    @Test
    fun canonicalLatexDoesNotAddItsOwnSourceAgain() {
        val existing = listOf(candidate("\\frac{a+b}{c+d}"), candidate("別候補"))
        val result = provider.provide("\\frac{a+b}{c+d}")
        val composed = UtilityCandidateComposer.compose("\\frac{a+b}{c+d}", existing, result)

        assertEquals(
            listOf("(a+b)⁄(c+d)", "\\frac{a+b}{c+d}", "別候補"),
            composed.map { it.commitText },
        )
    }

    @Test
    fun disabledFormulaCandidatesDoNotChangeExistingUtilityBehavior() {
        val config = UtilityCandidateConfig(formulaCandidateEnabled = false)
        assertFalse(provider.provide("25^2", config).hasCandidates)
        assertEquals(
            listOf("625", "25^2=625"),
            provider.provide("25^2=", config).candidates.map { it.text },
        )
    }

    @Test
    fun calculatedPowerKeepsResultFirstAndFormatsOnlyTheExpressionCandidate() {
        val candidates = provider.provide("25^2=").candidates
        assertEquals(listOf("625", "25²=625"), candidates.map { it.text })
        assertEquals("25²=625", candidates[1].formulaPresentation?.unicodeText)
        assertEquals("25^{2}=625", candidates[1].formulaPresentation?.normalizedTex)
    }

    @Test
    fun unsupportedInputsAreRejectedWithoutThrowing() {
        listOf(
            "",
            "ordinary text",
            "https://example.com/a^2",
            "mail@example.com",
            "\\unknown{a}",
            "\\begin{matrix}a&b\\end{matrix}",
            "\\frac{a+b",
            "_2",
            "^2",
            "1".repeat(201),
            "a\n+b",
        ).forEach { source ->
            assertEquals(source, null, parser.parse(source))
            assertFalse(source, provider.provide(source).hasCandidates)
        }
    }

    @Test
    fun formulasAndLatexUseTheSameNodeFamilies() {
        val shorthand = parser.parse("a/b")?.ast
        val latex = parser.parse("\\frac{a}{b}")?.ast
        assertTrue(shorthand is FormulaNode.Fraction)
        assertTrue(latex is FormulaNode.Fraction)
        assertEquals(
            parser.parse("x_1^(n+1)")?.ast,
            parser.parse("x_{1}^{n+1}")?.ast,
        )
        assertEquals(parser.parse("sqrt(x)")?.ast, parser.parse("\\sqrt{x}")?.ast)
        assertEquals(parser.parse("a*b")?.ast, parser.parse("a\\times b")?.ast)
        assertEquals(parser.parse("a<=b")?.ast, parser.parse("a\\leq b")?.ast)
        assertEquals(parser.parse("π")?.ast, parser.parse("\\pi")?.ast)
        assertEquals(
            parser.parse("sum(i=1,n,i^2)")?.ast,
            parser.parse("\\sum_{i=1}^{n}i^2")?.ast,
        )
        assertEquals(
            parser.parse("lim(x->0,f(x))")?.ast,
            parser.parse("\\lim_{x\\to0}f(x)")?.ast,
        )
        assertTrue(parser.parse("e") != null)
    }

    @Test
    fun supportedFormulaFamiliesParseWithoutAndroidOrExternalLibraries() {
        listOf(
            "a*b", "a<=b", "a≈b", "a∝b", "x->y", "5!", "10%",
            "sin(x)", "asin(x)", "sinh(x)", "log(x)", "exp(x)", "min(x,y)",
            "floor(x)", "ceil(x)", "⌊x⌋", "root(3,x)", "abs(x)", "norm(v)", "||v||",
            "alpha", "Gamma", "π", "∞", "prod(i=1,n,i)", "int(a,b,f)",
            "lim(x->0,f(x))", "forall x in A", "vec(v)", "dot(x)", "underline(x)",
            "\\frac{a}{\\frac{b}{c}}", "\\sqrt[3]{x}", "\\left|x+y\\right|",
            "\\lfloor x\\rfloor", "\\left\\lfloor x\\right\\rfloor",
            "\\langle x,y\\rangle", "\\sum_{i=1}^{n}i^2",
            "\\prod_{i=1}^{n}i", "\\int_0^1x\\,dx", "\\iint_D f", "\\iiint_V f",
            "\\oint_C f", "\\lim_{x\\to0}f(x)", "\\partial_x f", "\\nabla f",
            "\\mathbb{R}", "\\forall x\\in A", "\\vec{v}", "\\hat{x}",
            "\\bar{y}", "\\dot{z}", "\\ddot{z}", "\\tilde{x}", "\\mathcal{F}",
        ).forEach { source ->
            assertTrue(source, parser.parse(source) != null)
        }
    }

    @Test
    fun texControlWordsAreSeparatedWhenFollowedByLetters() {
        assertEquals("a\\times b", parser.parse("a*b")?.normalizedTex)
        assertEquals("\\sin x", parser.parse("\\sin x")?.normalizedTex)
        assertEquals("\\leq", parser.parse("≤")?.normalizedTex)
        assertEquals("sin(x)", parser.parse("sin(x)")?.unicodeText)
        assertEquals("sin x", parser.parse("\\sin x")?.unicodeText)
    }

    @Test
    fun ordinaryFunctionLookingTextIsNotTreatedAsFormula() {
        assertEquals(null, parser.parse("hello(world)"))
    }

    @Test
    fun directUnicodeOperatorsAndCodeFragmentsAreHandledConservatively() {
        assertEquals("a\\cup b", parser.parse("a∪b")?.normalizedTex)
        assertEquals("a\\in b", parser.parse("a∈b")?.normalizedTex)
        assertEquals("\\frac{2}{3}", parser.parse("2÷3")?.normalizedTex)
        assertEquals("a\\pm b", parser.parse("a±b")?.normalizedTex)
        assertEquals("a\\equiv b", parser.parse("a≡b")?.normalizedTex)
        assertEquals("a\\not\\equiv b", parser.parse("a≢b")?.normalizedTex)
        assertEquals("a\\not\\equiv b", parser.parse("a\\not\\equiv b")?.normalizedTex)
        assertEquals("\\mathbb{R}", parser.parse("\\mathbb{R}")?.normalizedTex)
        assertEquals("\\left\\langle x\\right\\rangle", parser.parse("\\left\\langle x\\right\\rangle")?.normalizedTex)
        assertEquals(
            "\\sum_{i=1}^{n}i^{2}",
            parser.parse("∑_{i=1}^{n}i^2")?.normalizedTex,
        )
        assertEquals(
            parser.parse("sqrt(x^2+y^2)")?.ast,
            parser.parse("√(x^2+y^2)")?.ast,
        )

        listOf(
            "const x = a^2;",
            "if (x^2) { y++; }",
            "value = \"x^2\"",
            "for(i=0;i<n;i++)",
        ).forEach { source ->
            assertEquals(source, null, parser.parse(source))
        }

        assertEquals(
            listOf("a→b", "a\\to b"),
            provider.provide("a→b").candidates.map { it.text },
        )
        assertEquals(
            listOf("ℕ", "\\mathbb{N}"),
            provider.provide("ℕ").candidates.map { it.text },
        )
        assertEquals("25^2", UtilityInputNormalizer.normalizeForFormula("２５＾２"))
    }

    private fun candidateType(candidate: Candidate): Byte = candidate.type

    private fun candidate(text: String) = Candidate(
        string = text,
        type = 1,
        length = text.length.toUByte(),
        score = 0,
    )
}
