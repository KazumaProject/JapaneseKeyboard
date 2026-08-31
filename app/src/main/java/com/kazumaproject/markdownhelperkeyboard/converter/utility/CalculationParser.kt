package com.kazumaproject.markdownhelperkeyboard.converter.utility

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

sealed interface CalculationExpression {
    data class NumberLiteral(val value: BigDecimal) : CalculationExpression
    data class Constant(val name: String) : CalculationExpression
    data class Unary(val operator: UnaryOperator, val operand: CalculationExpression) : CalculationExpression
    data class Binary(
        val left: CalculationExpression,
        val operator: BinaryOperator,
        val right: CalculationExpression,
    ) : CalculationExpression
    data class Percentage(val operand: CalculationExpression) : CalculationExpression
    data class Factorial(val operand: CalculationExpression) : CalculationExpression
    data class Function(val name: String, val arguments: List<CalculationExpression>) : CalculationExpression
}

enum class UnaryOperator { PLUS, MINUS }
enum class BinaryOperator { ADD, SUBTRACT, MULTIPLY, DIVIDE, POWER, MOD }

class CalculationParser(
    private val maxTokens: Int = 128,
    private val maxParenthesisDepth: Int = 32,
) {
    private val mathContext = MathContext(34, RoundingMode.HALF_EVEN)

    fun parse(expression: String): CalculationExpression? {
        val tokens = Lexer(expression, maxTokens).tokenize() ?: return null
        return Parser(tokens, maxParenthesisDepth).parse()
    }

    fun evaluate(
        expression: CalculationExpression,
        angleMode: AngleMode = AngleMode.DEGREES,
    ): BigDecimal? = try {
        Evaluator(angleMode, mathContext).evaluate(expression)?.takeIf(::isWithinResultLimits)
    } catch (_: ArithmeticException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun calculate(
        expression: String,
        angleMode: AngleMode = AngleMode.DEGREES,
    ): BigDecimal? = parse(expression)?.let { evaluate(it, angleMode) }

    private fun isWithinResultLimits(value: BigDecimal): Boolean {
        val decimalExponent = value.precision().toLong() - value.scale().toLong()
        return abs(decimalExponent) <= MAX_DECIMAL_EXPONENT
    }

    private enum class TokenType {
        NUMBER, IDENTIFIER, PLUS, MINUS, STAR, SLASH, CARET, PERCENT, BANG,
        LEFT_PAREN, RIGHT_PAREN, COMMA, END,
    }

    private data class Token(val type: TokenType, val text: String = "")

    private class Lexer(
        private val source: String,
        private val maxTokens: Int,
    ) {
        private var index = 0
        private val tokens = mutableListOf<Token>()

        fun tokenize(): List<Token>? {
            while (index < source.length) {
                val char = source[index]
                when {
                    char.isWhitespace() -> index++
                    char.isDigit() || (char == '.' && source.getOrNull(index + 1)?.isDigit() == true) -> {
                        val number = readNumber() ?: return null
                        tokens += Token(TokenType.NUMBER, number)
                    }
                    char.isLetter() || char == 'π' -> tokens += Token(
                        TokenType.IDENTIFIER,
                        readIdentifier().lowercase(),
                    )
                    else -> {
                        val type = when (char) {
                            '+' -> TokenType.PLUS
                            '-' -> TokenType.MINUS
                            '*' -> TokenType.STAR
                            '/' -> TokenType.SLASH
                            '^' -> TokenType.CARET
                            '%' -> TokenType.PERCENT
                            '!' -> TokenType.BANG
                            '(' -> TokenType.LEFT_PAREN
                            ')' -> TokenType.RIGHT_PAREN
                            ',' -> TokenType.COMMA
                            else -> return null
                        }
                        tokens += Token(type, char.toString())
                        index++
                    }
                }
                if (tokens.size > maxTokens) return null
            }
            tokens += Token(TokenType.END)
            return tokens
        }

        private fun readNumber(): String? {
            val start = index
            var digitCount = 0
            var integerDigits = 0
            while (source.getOrNull(index)?.isDigit() == true) {
                index++
                digitCount++
                integerDigits++
            }

            if (integerDigits > 0) {
                var consumedGrouping = false
                while (source.getOrNull(index) == ',') {
                    val groupStart = index + 1
                    var groupLength = 0
                    while (source.getOrNull(groupStart + groupLength)?.isDigit() == true) groupLength++
                    val validFirstGroup = consumedGrouping || integerDigits in 1..3
                    if (groupLength != 3 || !validFirstGroup) break
                    consumedGrouping = true
                    index += 4
                    digitCount += 3
                }
            }

            if (source.getOrNull(index) == '.') {
                index++
                while (source.getOrNull(index)?.isDigit() == true) {
                    index++
                    digitCount++
                }
            }
            if (digitCount == 0 || digitCount > MAX_LITERAL_DIGITS) return null

            val exponentStart = index
            if (source.getOrNull(index) == 'e' || source.getOrNull(index) == 'E') {
                var probe = index + 1
                if (source.getOrNull(probe) == '+' || source.getOrNull(probe) == '-') probe++
                val exponentDigitsStart = probe
                while (source.getOrNull(probe)?.isDigit() == true) probe++
                if (probe > exponentDigitsStart) {
                    if (probe - exponentDigitsStart > 6) return null
                    index = probe
                } else {
                    index = exponentStart
                }
            }
            return source.substring(start, index).replace(",", "")
        }

        private fun readIdentifier(): String {
            val start = index
            while (source.getOrNull(index)?.let { it.isLetter() || it == 'π' } == true) index++
            return source.substring(start, index)
        }
    }

    private class Parser(
        private val tokens: List<Token>,
        private val maxDepth: Int,
    ) {
        private var index = 0
        private var depth = 0

        fun parse(): CalculationExpression? {
            val result = parseAddition() ?: return null
            return result.takeIf { peek().type == TokenType.END }
        }

        private fun parseAddition(): CalculationExpression? {
            var left = parseMultiplication() ?: return null
            while (true) {
                val operator = when (peek().type) {
                    TokenType.PLUS -> BinaryOperator.ADD
                    TokenType.MINUS -> BinaryOperator.SUBTRACT
                    else -> return left
                }
                advance()
                val right = parseMultiplication() ?: return null
                left = CalculationExpression.Binary(left, operator, right)
            }
        }

        private fun parseMultiplication(): CalculationExpression? {
            var left = parseUnary() ?: return null
            while (true) {
                val explicit = when {
                    match(TokenType.STAR) -> BinaryOperator.MULTIPLY
                    match(TokenType.SLASH) -> BinaryOperator.DIVIDE
                    peek().type == TokenType.IDENTIFIER && peek().text == "mod" -> {
                        advance()
                        BinaryOperator.MOD
                    }
                    else -> null
                }
                if (explicit != null) {
                    val right = parseUnary() ?: return null
                    left = CalculationExpression.Binary(left, explicit, right)
                    continue
                }
                if (peek().type == TokenType.LEFT_PAREN || peek().type == TokenType.IDENTIFIER) {
                    val right = parseUnary() ?: return null
                    left = CalculationExpression.Binary(left, BinaryOperator.MULTIPLY, right)
                    continue
                }
                return left
            }
        }

        private fun parseUnary(): CalculationExpression? = when {
            match(TokenType.PLUS) -> parseUnary()?.let {
                CalculationExpression.Unary(UnaryOperator.PLUS, it)
            }
            match(TokenType.MINUS) -> parseUnary()?.let {
                CalculationExpression.Unary(UnaryOperator.MINUS, it)
            }
            else -> parsePower()
        }

        private fun parsePower(): CalculationExpression? {
            val left = parsePostfix() ?: return null
            if (!match(TokenType.CARET)) return left
            val right = parseUnary() ?: return null
            return CalculationExpression.Binary(left, BinaryOperator.POWER, right)
        }

        private fun parsePostfix(): CalculationExpression? {
            var value = parsePrimary() ?: return null
            while (true) {
                value = when {
                    match(TokenType.PERCENT) -> CalculationExpression.Percentage(value)
                    match(TokenType.BANG) -> CalculationExpression.Factorial(value)
                    else -> return value
                }
            }
        }

        private fun parsePrimary(): CalculationExpression? {
            if (match(TokenType.NUMBER)) {
                return previous().text.toBigDecimalOrNull()?.let(CalculationExpression::NumberLiteral)
            }
            if (match(TokenType.IDENTIFIER)) {
                val name = previous().text
                if (!match(TokenType.LEFT_PAREN)) {
                    return if (name == "pi" || name == "π" || name == "e") {
                        CalculationExpression.Constant(name)
                    } else {
                        null
                    }
                }
                if (++depth > maxDepth) return null
                val arguments = mutableListOf<CalculationExpression>()
                if (!match(TokenType.RIGHT_PAREN)) {
                    do {
                        arguments += parseAddition() ?: return null
                    } while (match(TokenType.COMMA))
                    if (!match(TokenType.RIGHT_PAREN)) return null
                }
                depth--
                return CalculationExpression.Function(name, arguments)
            }
            if (match(TokenType.LEFT_PAREN)) {
                if (++depth > maxDepth) return null
                val value = parseAddition() ?: return null
                if (!match(TokenType.RIGHT_PAREN)) return null
                depth--
                return value
            }
            return null
        }

        private fun peek(): Token = tokens[index]
        private fun previous(): Token = tokens[index - 1]
        private fun advance(): Token = tokens[index++]
        private fun match(type: TokenType): Boolean {
            if (peek().type != type) return false
            advance()
            return true
        }
    }

    private class Evaluator(
        private val angleMode: AngleMode,
        private val context: MathContext,
    ) {
        fun evaluate(expression: CalculationExpression): BigDecimal? = when (expression) {
            is CalculationExpression.NumberLiteral -> expression.value
            is CalculationExpression.Constant -> when (expression.name) {
                "pi", "π" -> BigDecimal(StrictMath.PI, context)
                "e" -> BigDecimal(StrictMath.E, context)
                else -> null
            }
            is CalculationExpression.Unary -> evaluate(expression.operand)?.let {
                if (expression.operator == UnaryOperator.MINUS) it.negate(context) else it
            }
            is CalculationExpression.Percentage -> evaluate(expression.operand)
                ?.divide(ONE_HUNDRED, context)
            is CalculationExpression.Factorial -> evaluate(expression.operand)?.let(::factorial)
            is CalculationExpression.Binary -> evaluateBinary(expression)
            is CalculationExpression.Function -> evaluateFunction(expression)
        }

        private fun evaluateBinary(expression: CalculationExpression.Binary): BigDecimal? {
            val left = evaluate(expression.left) ?: return null
            if (
                (expression.operator == BinaryOperator.ADD || expression.operator == BinaryOperator.SUBTRACT) &&
                expression.right is CalculationExpression.Percentage
            ) {
                val percent = evaluate(expression.right.operand)?.divide(ONE_HUNDRED, context)
                    ?: return null
                val delta = left.multiply(percent, context)
                return if (expression.operator == BinaryOperator.ADD) {
                    left.add(delta, context)
                } else {
                    left.subtract(delta, context)
                }
            }
            val right = evaluate(expression.right) ?: return null
            return when (expression.operator) {
                BinaryOperator.ADD -> left.add(right, context)
                BinaryOperator.SUBTRACT -> left.subtract(right, context)
                BinaryOperator.MULTIPLY -> left.multiply(right, context)
                BinaryOperator.DIVIDE -> if (right.signum() == 0) null else left.divide(right, context)
                BinaryOperator.MOD -> if (right.signum() == 0) null else left.remainder(right, context)
                BinaryOperator.POWER -> power(left, right)
            }
        }

        private fun power(base: BigDecimal, exponent: BigDecimal): BigDecimal? {
            val stripped = exponent.stripTrailingZeros()
            if (stripped.scale() <= 0) {
                val integer = stripped.toBigIntegerExact()
                if (integer.bitLength() > 31) return null
                val power = integer.toInt()
                if (abs(power.toLong()) > MAX_INTEGER_POWER) return null
                if (power >= 0) return base.pow(power, context)
                if (base.signum() == 0) return null
                return BigDecimal.ONE.divide(base.pow(-power, context), context)
            }
            if (base.signum() < 0) return null
            return finiteDecimal(StrictMath.pow(base.toDouble(), exponent.toDouble()))
        }

        private fun factorial(value: BigDecimal): BigDecimal? {
            val integer = try {
                value.toBigIntegerExact()
            } catch (_: ArithmeticException) {
                return null
            }
            if (integer.signum() < 0 || integer.bitLength() > 31) return null
            val count = integer.toInt()
            if (count > MAX_FACTORIAL) return null
            var result = BigDecimal.ONE
            for (factor in 2..count) result = result.multiply(BigDecimal.valueOf(factor.toLong()))
            return result
        }

        private fun evaluateFunction(function: CalculationExpression.Function): BigDecimal? {
            val args = function.arguments.map { evaluate(it) ?: return null }
            fun unary(block: (Double) -> Double): BigDecimal? =
                args.singleOrNull()?.toDouble()?.let(block)?.let(::finiteDecimal)
            fun positiveUnary(block: (Double) -> Double): BigDecimal? {
                val value = args.singleOrNull() ?: return null
                if (value.signum() <= 0) return null
                return finiteDecimal(block(value.toDouble()))
            }
            return when (function.name) {
                "sqrt" -> {
                    val value = args.singleOrNull() ?: return null
                    if (value.signum() < 0) null else finiteDecimal(StrictMath.sqrt(value.toDouble()))
                }
                "cbrt" -> unary(StrictMath::cbrt)
                "abs" -> args.singleOrNull()?.abs(context)
                "sin" -> unary { StrictMath.sin(toRadians(it)) }
                "cos" -> unary { StrictMath.cos(toRadians(it)) }
                "tan" -> unary { StrictMath.tan(toRadians(it)) }
                "asin" -> unary { fromRadians(StrictMath.asin(it)) }
                "acos" -> unary { fromRadians(StrictMath.acos(it)) }
                "atan" -> unary { fromRadians(StrictMath.atan(it)) }
                "sinh" -> unary(StrictMath::sinh)
                "cosh" -> unary(StrictMath::cosh)
                "tanh" -> unary(StrictMath::tanh)
                "asinh" -> unary { StrictMath.log(it + StrictMath.sqrt(it * it + 1.0)) }
                "acosh" -> {
                    val value = args.singleOrNull()?.toDouble() ?: return null
                    if (value < 1.0) null else finiteDecimal(
                        StrictMath.log(value + StrictMath.sqrt(value * value - 1.0))
                    )
                }
                "atanh" -> {
                    val value = args.singleOrNull()?.toDouble() ?: return null
                    if (abs(value) >= 1.0) null else finiteDecimal(
                        0.5 * StrictMath.log((1.0 + value) / (1.0 - value))
                    )
                }
                "log" -> when (args.size) {
                    1 -> positiveUnary(StrictMath::log10)
                    2 -> {
                        val value = args[0].toDouble()
                        val base = args[1].toDouble()
                        if (value <= 0.0 || base <= 0.0 || base == 1.0) null
                        else finiteDecimal(StrictMath.log(value) / StrictMath.log(base))
                    }
                    else -> null
                }
                "ln" -> positiveUnary(StrictMath::log)
                "exp" -> unary(StrictMath::exp)
                "min" -> args.takeIf { it.isNotEmpty() }?.minOrNull()
                "max" -> args.takeIf { it.isNotEmpty() }?.maxOrNull()
                "floor" -> args.singleOrNull()?.setScale(0, RoundingMode.FLOOR)
                "ceil" -> args.singleOrNull()?.setScale(0, RoundingMode.CEILING)
                "round" -> args.singleOrNull()?.setScale(0, RoundingMode.HALF_UP)
                "factorial", "fact" -> args.singleOrNull()?.let(::factorial)
                else -> null
            }
        }

        private fun toRadians(value: Double): Double = if (angleMode == AngleMode.DEGREES) {
            StrictMath.toRadians(value)
        } else value

        private fun fromRadians(value: Double): Double = if (angleMode == AngleMode.DEGREES) {
            StrictMath.toDegrees(value)
        } else value

        private fun finiteDecimal(value: Double): BigDecimal? =
            value.takeIf(Double::isFinite)?.let(BigDecimal::valueOf)
    }

    private companion object {
        const val MAX_LITERAL_DIGITS = 64
        const val MAX_DECIMAL_EXPONENT = 100_000L
        const val MAX_INTEGER_POWER = 1_000L
        const val MAX_FACTORIAL = 1_000
        val ONE_HUNDRED = BigDecimal("100")
    }
}
