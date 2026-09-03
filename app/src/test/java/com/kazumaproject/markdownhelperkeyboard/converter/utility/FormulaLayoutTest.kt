package com.kazumaproject.markdownhelperkeyboard.converter.utility

import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaLayoutTest {
    private val measure = FormulaTextMeasurer { value, size -> value.length * size * 0.6f }

    @Test
    fun fractionNumeratorIsAboveRuleAndDenominatorIsBelowRule() {
        val layout = FormulaLayoutEngine.layout(
            FormulaNode.Fraction(FormulaNode.Number("12"), FormulaNode.Number("34")),
            FormulaLayoutConfig(fontSize = 20f),
            measure,
        )
        val text = layout.operations.filterIsInstance<FormulaDrawOperation.Text>()
        val rule = layout.operations.filterIsInstance<FormulaDrawOperation.Line>().single()
        val numerator = text.first { it.value == "12" }
        val denominator = text.first { it.value == "34" }

        assertTrue(numerator.baseline < rule.startY)
        assertTrue(denominator.baseline > rule.startY)
        assertTrue(numerator.x > 0f)
        assertTrue(denominator.x > 0f)
        assertTrue(layout.width >= numerator.x + 2f * 20f * 0.82f * 0.6f)
    }

    @Test
    fun nestedScriptsAndLargeOperatorHaveFiniteBounds() {
        val parsed = FormulaParser().parse("sum(i=1,n,i^2)") ?: error("formula did not parse")
        val layout = FormulaLayoutEngine.layout(parsed.ast, FormulaLayoutConfig(fontSize = 18f), measure)
        assertTrue(layout.width > 0f)
        assertTrue(layout.height > 0f)
        assertTrue(layout.operations.all { operation ->
            when (operation) {
                is FormulaDrawOperation.Text -> operation.x >= 0f && operation.baseline.isFinite()
                is FormulaDrawOperation.Line -> operation.startX >= 0f && operation.endX >= operation.startX
            }
        })
    }

    @Test
    fun upperOperationsStayInsideTheReportedAscent() {
        val parsed = FormulaParser().parse("\\hat{x_1^2}+\\sum_{i=1}^{n}i^2")
            ?: error("formula did not parse")
        val config = FormulaLayoutConfig(fontSize = 18f)
        val layout = FormulaLayoutEngine.layout(parsed.ast, config, measure)

        assertTrue(layout.operations.all { operation ->
            when (operation) {
                is FormulaDrawOperation.Text -> {
                    val ascent = operation.fontSize * config.ascentRatio
                    val descent = operation.fontSize * config.descentRatio
                    operation.baseline - ascent >= -layout.ascent - 0.001f &&
                        operation.baseline + descent <= layout.descent + 0.001f
                }

                is FormulaDrawOperation.Line -> {
                    val halfStroke = operation.strokeWidth / 2f
                    operation.startY - halfStroke >= -layout.ascent - 0.001f &&
                        operation.endY + halfStroke <= layout.descent + 0.001f
                }
            }
        })
    }
}
