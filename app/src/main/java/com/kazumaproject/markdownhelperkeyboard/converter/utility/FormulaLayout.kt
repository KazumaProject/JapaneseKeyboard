package com.kazumaproject.markdownhelperkeyboard.converter.utility

import kotlin.math.max

/**
 * Font-independent drawing instructions for a formula.  Keeping these instructions free of
 * Android classes makes the important placement rules testable on the JVM.
 */
sealed interface FormulaDrawOperation {
    data class Text(
        val value: String,
        val x: Float,
        val baseline: Float,
        val fontSize: Float,
        val bold: Boolean = false,
    ) : FormulaDrawOperation

    data class Line(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val strokeWidth: Float,
    ) : FormulaDrawOperation
}

data class FormulaLayout(
    val width: Float,
    val ascent: Float,
    val descent: Float,
    val operations: List<FormulaDrawOperation>,
) {
    val height: Float get() = ascent + descent
}

fun interface FormulaTextMeasurer {
    fun measure(value: String, fontSize: Float): Float
}

data class FormulaLayoutConfig(
    val fontSize: Float = 16f,
    val scriptScale: Float = 0.72f,
    val fractionScale: Float = 0.82f,
    val ascentRatio: Float = 0.80f,
    val descentRatio: Float = 0.22f,
    val horizontalGapRatio: Float = 0.12f,
    val verticalGapRatio: Float = 0.10f,
    val ruleThicknessRatio: Float = 0.06f,
) {
    internal fun metrics(size: Float): Pair<Float, Float> =
        size * ascentRatio to size * descentRatio

    internal fun horizontalGap(size: Float): Float = size * horizontalGapRatio

    internal fun verticalGap(size: Float): Float = size * verticalGapRatio

    internal fun ruleThickness(size: Float): Float = max(1f, size * ruleThicknessRatio)
}

/**
 * Measures a FormulaNode around a baseline at y=0 and returns operations in that coordinate
 * system.  A caller can translate every operation by the final baseline when drawing.
 */
object FormulaLayoutEngine {
    fun layout(
        node: FormulaNode,
        config: FormulaLayoutConfig = FormulaLayoutConfig(),
        measureText: FormulaTextMeasurer = FormulaTextMeasurer { value, size ->
            value.length * size * 0.58f
        },
    ): FormulaLayout = layoutNode(node, config.fontSize, config, measureText)

    private fun layoutNode(
        node: FormulaNode,
        size: Float,
        config: FormulaLayoutConfig,
        measureText: FormulaTextMeasurer,
    ): FormulaLayout = when (node) {
        is FormulaNode.Number -> textLayout(node.value, size, config, measureText)
        is FormulaNode.Symbol -> textLayout(node.value, size, config, measureText)
        is FormulaNode.Row -> rowLayout(node.children, size, config, measureText)
        is FormulaNode.Fraction -> fractionLayout(node, size, config, measureText)
        is FormulaNode.Script -> scriptLayout(node, size, config, measureText)
        is FormulaNode.Radical -> radicalLayout(node, size, config, measureText)
        is FormulaNode.Delimited -> delimitedLayout(node, size, config, measureText)
        is FormulaNode.LargeOperator -> largeOperatorLayout(node, size, config, measureText)
        is FormulaNode.Accent -> accentLayout(node, size, config, measureText)
    }

    private fun textLayout(
        value: String,
        size: Float,
        config: FormulaLayoutConfig,
        measureText: FormulaTextMeasurer,
        bold: Boolean = false,
    ): FormulaLayout {
        val (ascent, descent) = config.metrics(size)
        return FormulaLayout(
            width = measureText.measure(value, size).coerceAtLeast(0f),
            ascent = ascent,
            descent = descent,
            operations = if (value.isEmpty()) {
                emptyList()
            } else {
                listOf(FormulaDrawOperation.Text(value, 0f, 0f, size, bold))
            },
        )
    }

    private fun rowLayout(
        children: List<FormulaNode>,
        size: Float,
        config: FormulaLayoutConfig,
        measureText: FormulaTextMeasurer,
    ): FormulaLayout {
        if (children.isEmpty()) return textLayout("", size, config, measureText)
        val layouts = children.map { layoutNode(it, size, config, measureText) }
        var x = 0f
        var ascent = 0f
        var descent = 0f
        val operations = buildList {
            layouts.forEachIndexed { index, child ->
                addAll(child.operations.map { it.translate(x, 0f) })
                x += child.width
                val sourceNode = children[index]
                val isLimitedOperator = sourceNode is FormulaNode.LargeOperator &&
                    (sourceNode.lower != null || sourceNode.upper != null)
                val nextNode = children.getOrNull(index + 1)
                if (index < layouts.lastIndex &&
                    (isLimitedOperator ||
                        (nextNode !is FormulaNode.Delimited && sourceNode.needsOperandGap()))
                ) {
                    x += config.horizontalGap(size)
                }
                ascent = max(ascent, child.ascent)
                descent = max(descent, child.descent)
            }
        }
        return FormulaLayout(x, ascent, descent, operations)
    }

    private fun FormulaNode.needsOperandGap(): Boolean = when (this) {
        is FormulaNode.Symbol -> value in setOf(
            "sin", "cos", "tan", "arcsin", "arccos", "arctan",
            "sinh", "cosh", "tanh", "sec", "csc", "cot",
            "arcsinh", "arccosh", "arctanh", "ln", "log", "exp",
            "lim", "min", "max", "sup", "inf", "mod",
        )
        is FormulaNode.Script -> base.needsOperandGap()
        else -> false
    }

    private fun fractionLayout(
        node: FormulaNode.Fraction,
        size: Float,
        config: FormulaLayoutConfig,
        measureText: FormulaTextMeasurer,
    ): FormulaLayout {
        val childSize = size * config.fractionScale
        val numerator = layoutNode(node.numerator, childSize, config, measureText)
        val denominator = layoutNode(node.denominator, childSize, config, measureText)
        val horizontalGap = config.horizontalGap(size)
        val verticalGap = config.verticalGap(size)
        val rule = config.ruleThickness(size)
        val contentWidth = max(numerator.width, denominator.width)
        val width = contentWidth + horizontalGap * 2f
        val numeratorBaseline = -(rule / 2f + verticalGap + numerator.descent)
        val denominatorBaseline = rule / 2f + verticalGap + denominator.ascent
        val numeratorX = (width - numerator.width) / 2f
        val denominatorX = (width - denominator.width) / 2f
        val top = numeratorBaseline - numerator.ascent
        val bottom = denominatorBaseline + denominator.descent
        return FormulaLayout(
            width = width,
            ascent = max(0f, -top),
            descent = max(0f, bottom),
            operations = buildList {
                addAll(numerator.operations.map { it.translate(numeratorX, numeratorBaseline) })
                add(
                    FormulaDrawOperation.Line(
                        startX = horizontalGap / 2f,
                        startY = 0f,
                        endX = width - horizontalGap / 2f,
                        endY = 0f,
                        strokeWidth = rule,
                    )
                )
                addAll(denominator.operations.map { it.translate(denominatorX, denominatorBaseline) })
            },
        )
    }

    private fun scriptLayout(
        node: FormulaNode.Script,
        size: Float,
        config: FormulaLayoutConfig,
        measureText: FormulaTextMeasurer,
    ): FormulaLayout {
        val base = layoutNode(node.base, size, config, measureText)
        val scriptSize = size * config.scriptScale
        val superscript = node.superscript?.let { layoutNode(it, scriptSize, config, measureText) }
        val subscript = node.subscript?.let { layoutNode(it, scriptSize, config, measureText) }
        if (superscript == null && subscript == null) return base

        val scriptX = base.width + max(1f, size * 0.04f)
        val scriptsArePaired = superscript != null && subscript != null
        val superscriptBaseline = if (scriptsArePaired) {
            -(config.verticalGap(size) + (superscript?.descent ?: 0f))
        } else {
            -base.ascent * 0.56f
        }
        val subscriptBaseline = if (scriptsArePaired) {
            config.verticalGap(size) + (subscript?.ascent ?: 0f)
        } else {
            base.descent * 0.72f
        }
        val scriptWidth = max(superscript?.width ?: 0f, subscript?.width ?: 0f)
        val ascent = max(
            base.ascent,
            superscript?.let { -superscriptBaseline + it.ascent } ?: 0f,
        )
        val descent = max(
            base.descent,
            subscript?.let { subscriptBaseline + it.descent } ?: 0f,
        )
        return FormulaLayout(
            width = scriptX + scriptWidth,
            ascent = ascent,
            descent = descent,
            operations = buildList {
                addAll(base.operations)
                superscript?.let {
                    addAll(it.operations.map { operation -> operation.translate(scriptX, superscriptBaseline) })
                }
                subscript?.let {
                    addAll(it.operations.map { operation -> operation.translate(scriptX, subscriptBaseline) })
                }
            },
        )
    }

    private fun radicalLayout(
        node: FormulaNode.Radical,
        size: Float,
        config: FormulaLayoutConfig,
        measureText: FormulaTextMeasurer,
    ): FormulaLayout {
        val radicandNode = if (node.radicandWasParenthesized) {
            FormulaNode.Delimited("(", node.radicand, ")")
        } else {
            node.radicand
        }
        val radicand = layoutNode(radicandNode, size, config, measureText)
        val root = textLayout("√", size, config, measureText)
        val gap = config.horizontalGap(size)
        val radicandX = root.width + gap
        val overlineY = -radicand.ascent - config.verticalGap(size) / 2f
        val index = node.index?.let { layoutNode(it, size * config.scriptScale, config, measureText) }
        val indexBaseline = -root.ascent * 0.58f
        val width = radicandX + radicand.width
        val ascent = max(
            root.ascent,
            max(
                -overlineY + config.ruleThickness(size) / 2f,
                index?.let { -indexBaseline + it.ascent } ?: 0f,
            ),
        )
        val descent = max(root.descent, radicand.descent)
        return FormulaLayout(
            width = width,
            ascent = ascent,
            descent = descent,
            operations = buildList {
                addAll(root.operations)
                addAll(radicand.operations.map { it.translate(radicandX, 0f) })
                add(
                    FormulaDrawOperation.Line(
                        startX = radicandX,
                        startY = overlineY,
                        endX = width,
                        endY = overlineY,
                        strokeWidth = config.ruleThickness(size),
                    )
                )
                index?.let {
                    addAll(it.operations.map { operation -> operation.translate(0f, indexBaseline) })
                }
            },
        )
    }

    private fun delimitedLayout(
        node: FormulaNode.Delimited,
        size: Float,
        config: FormulaLayoutConfig,
        measureText: FormulaTextMeasurer,
    ): FormulaLayout {
        val content = layoutNode(node.content, size, config, measureText)
        val baseDelimiterHeight = config.metrics(size).let { (ascent, descent) -> ascent + descent }
        val delimiterScale = if (baseDelimiterHeight > 0f) {
            (content.height / baseDelimiterHeight).coerceAtLeast(1f)
        } else {
            1f
        }
        val delimiterSize = size * delimiterScale
        val left = textLayout(
            node.left,
            if (node.left.isEmpty()) size else delimiterSize,
            config,
            measureText,
        )
        val right = textLayout(
            node.right,
            if (node.right.isEmpty()) size else delimiterSize,
            config,
            measureText,
        )
        val rightX = left.width + content.width
        return FormulaLayout(
            width = left.width + content.width + right.width,
            ascent = maxOf(left.ascent, content.ascent, right.ascent),
            descent = maxOf(left.descent, content.descent, right.descent),
            operations = buildList {
                addAll(left.operations)
                addAll(content.operations.map { it.translate(left.width, 0f) })
                addAll(right.operations.map { it.translate(rightX, 0f) })
            },
        )
    }

    private fun largeOperatorLayout(
        node: FormulaNode.LargeOperator,
        size: Float,
        config: FormulaLayoutConfig,
        measureText: FormulaTextMeasurer,
    ): FormulaLayout {
        val operator = textLayout(
            FormulaFormatter.largeOperatorDisplay(node.name),
            size,
            config,
            measureText,
        )
        val limitSize = size * config.scriptScale
        val lower = node.lower?.let { layoutNode(it, limitSize, config, measureText) }
        val upper = node.upper?.let { layoutNode(it, limitSize, config, measureText) }
        val operatorRegionWidth = maxOf(operator.width, lower?.width ?: 0f, upper?.width ?: 0f)
        val operatorX = (operatorRegionWidth - operator.width) / 2f
        val limitGap = config.verticalGap(size)
        val upperBaseline = upper?.let { -(operator.ascent + limitGap + it.descent) }
        val lowerBaseline = lower?.let { operator.descent + limitGap + it.ascent }
        val operand = node.operand?.let { layoutNode(it, size, config, measureText) }
        val operandX = if (operand == null) {
            operatorRegionWidth
        } else {
            operatorRegionWidth + config.horizontalGap(size)
        }
        return FormulaLayout(
            width = operandX + (operand?.width ?: 0f),
            ascent = maxOf(
                operator.ascent,
                upper?.let { -(upperBaseline ?: 0f) + it.ascent } ?: 0f,
                operand?.ascent ?: 0f,
            ),
            descent = maxOf(
                operator.descent,
                lower?.let { (lowerBaseline ?: 0f) + it.descent } ?: 0f,
                operand?.descent ?: 0f,
            ),
            operations = buildList {
                addAll(operator.operations.map { it.translate(operatorX, 0f) })
                upper?.let {
                    addAll(it.operations.map { operation -> operation.translate((operatorRegionWidth - it.width) / 2f, upperBaseline ?: 0f) })
                }
                lower?.let {
                    addAll(it.operations.map { operation -> operation.translate((operatorRegionWidth - it.width) / 2f, lowerBaseline ?: 0f) })
                }
                operand?.let {
                    addAll(it.operations.map { operation -> operation.translate(operandX, 0f) })
                }
            },
        )
    }

    private fun accentLayout(
        node: FormulaNode.Accent,
        size: Float,
        config: FormulaLayoutConfig,
        measureText: FormulaTextMeasurer,
    ): FormulaLayout {
        val base = layoutNode(node.base, size, config, measureText)
        if (node.kind == AccentKind.BOLD) {
            return base.copy(operations = base.operations.map { it.bold() })
        }
        val accentValue = when (node.kind) {
            AccentKind.HAT -> "⌃"
            AccentKind.BAR,
            AccentKind.OVERLINE -> "¯"
            AccentKind.DOT -> "˙"
            AccentKind.DDOT -> "¨"
            AccentKind.TILDE -> "˜"
            AccentKind.VEC -> "→"
            AccentKind.UNDERLINE -> ""
            AccentKind.BOLD -> ""
        }
        if (node.kind == AccentKind.UNDERLINE) {
            val underlineY = base.descent + config.verticalGap(size)
            return base.copy(
                descent = underlineY + config.ruleThickness(size),
                operations = base.operations + FormulaDrawOperation.Line(
                    startX = 0f,
                    startY = underlineY,
                    endX = base.width,
                    endY = underlineY,
                    strokeWidth = config.ruleThickness(size),
                ),
            )
        }
        val accent = textLayout(accentValue, size * 0.9f, config, measureText)
        val accentBaseline = -base.ascent - config.verticalGap(size) / 2f
        return base.copy(
            ascent = max(base.ascent, -accentBaseline + accent.ascent),
            operations = base.operations + accent.operations.map {
                it.translate((base.width - accent.width) / 2f, accentBaseline)
            },
        )
    }

    private fun FormulaDrawOperation.translate(dx: Float, dy: Float): FormulaDrawOperation = when (this) {
        is FormulaDrawOperation.Text -> copy(x = x + dx, baseline = baseline + dy)
        is FormulaDrawOperation.Line -> copy(
            startX = startX + dx,
            startY = startY + dy,
            endX = endX + dx,
            endY = endY + dy,
        )
    }

    private fun FormulaDrawOperation.bold(): FormulaDrawOperation = when (this) {
        is FormulaDrawOperation.Text -> copy(bold = true)
        is FormulaDrawOperation.Line -> this
    }
}
