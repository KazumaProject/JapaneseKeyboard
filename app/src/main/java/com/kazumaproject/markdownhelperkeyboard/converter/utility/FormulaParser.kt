package com.kazumaproject.markdownhelperkeyboard.converter.utility

/**
 * Parses the keyboard's formula shorthand and the intentionally finite LaTeX subset.
 *
 * This parser is kept independent of Android so it can run on the candidate worker thread and
 * be exercised with ordinary JVM tests.  It is conservative by design: an unsupported command,
 * an unfinished group, or an input that looks like prose is rejected instead of producing a
 * surprising candidate.
 */
class FormulaParser(
    private val maxCodePoints: Int = MAX_CODE_POINTS,
    private val maxTokens: Int = MAX_TOKENS,
    private val maxDepth: Int = MAX_DEPTH,
) {
    fun parse(input: String): ParsedFormula? {
        if (!isWithinLimits(input)) return null
        val trimmed = input.trim()
        if (trimmed.isEmpty() || looksLikeNonFormula(trimmed)) return null

        val (source, hadMathDelimiters) = stripMathDelimiters(trimmed) ?: return null
        if (source.isEmpty()) return null
        if (source.firstOrNull() == '^' || source.firstOrNull() == '_') return null

        val ast = if ('\\' in source) {
            LatexFormulaParser(source, maxTokens, maxDepth).parse()
        } else {
            SimpleFormulaParser(source, maxTokens, maxDepth).parse()
        } ?: return null

        if (ast.isEmptyFormula() || !isMathLike(source, ast)) return null
        val (unicodeText, normalizedTex) = FormulaFormatter.render(ast)
        if (unicodeText.isBlank() || normalizedTex.isBlank()) return null

        return ParsedFormula(
            ast = ast,
            unicodeText = unicodeText,
            normalizedTex = normalizedTex,
            sourceText = trimmed,
            sourceWasNormalizedTex = !hadMathDelimiters && '\\' in source &&
                source == normalizedTex,
        )
    }

    private fun isWithinLimits(input: String): Boolean =
        input.codePointCount(0, input.length) <= maxCodePoints && input.length <= MAX_UTF16_LENGTH

    private fun stripMathDelimiters(input: String): Pair<String, Boolean>? {
        if (input.startsWith("$$") && input.endsWith("$$") && input.length > 4) {
            return input.substring(2, input.length - 2).trim() to true
        }
        if (input.startsWith('$') && input.endsWith('$') && input.length > 2) {
            return input.substring(1, input.length - 1).trim() to true
        }
        if ('$' in input) return null
        return input to false
    }

    private fun looksLikeNonFormula(input: String): Boolean {
        if ('\n' in input || '\r' in input || '\t' in input) return true
        if (input.contains("```") || input.contains('`')) return true
        if (
            input.contains(';') ||
            input.contains("//") ||
            input.contains("/*") ||
            input.contains("*/") ||
            input.any { it == '"' || it == '\'' }
        ) return true
        if (Regex("(?i)^(?:https?|ftp)://|^www\\.").containsMatchIn(input)) return true
        if (input.contains("://")) return true
        if (Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(input)) return true
        if (input.startsWith("<") && input.endsWith(">")) return true
        if (Regex("(?i)^(?:fun|val|var|const|let|return|class|object|function|def|public|private|protected|new|if|else|for|while|switch|case|try|catch|throw|interface|package|import|select|insert|update|delete)\\b")
                .containsMatchIn(input)
        ) return true
        return false
    }

    private fun isMathLike(source: String, ast: FormulaNode): Boolean {
        if ('\\' in source) return true
        if (source.any {
                it in "^_=/+*%!<>≤≥≠≈≃≡≢≅≍≰≱∼∝→⇒↔⇔←↦⟶⟹⟷⟺⟵⟼√|‖∥∣×÷±∓⋅⊄⊅⊈⊉∈∉∋⊂⊆⊃⊇∪∩∧∨¬⌊⌋⌈⌉⟨⟩"
            }) return true
        if (source.any {
                it in "πΠ∞∑∏∫∬∭∮∂∇∀∃∈∉⊂⊆⊃⊇∪∩ℕℤℚℝℂℵ" ||
                    it in "αβγδεζηθικλμνξοπρστυφχψωϵεϑϕφςϖϱΓΔΘΛΞΠΣΥΦΨΩ"
            }
        ) return true
        if (source == "e") return true
        if (Regex("(?i)(?:^|[^a-z])(?:sqrt|root|nroot|sum|sigma|prod|product|int|integral|iint|iiint|oint|lim|limit|abs|absolute|norm|floor|ceil|ceiling|vec|vector|hat|bar|overline|dot|ddot|tilde|underline|bold)\\s*[({]")
                .containsMatchIn(source)
        ) return true
        return containsMathStructure(ast) || containsMathSymbol(ast)
    }

    private fun containsMathStructure(node: FormulaNode): Boolean = when (node) {
        is FormulaNode.Number,
        is FormulaNode.Symbol -> false
        is FormulaNode.Row -> node.children.any(::containsMathStructure)
        is FormulaNode.Fraction,
        is FormulaNode.Script,
        is FormulaNode.Radical,
        is FormulaNode.LargeOperator,
        is FormulaNode.Accent -> true
        is FormulaNode.Delimited -> containsMathStructure(node.content)
    }

    private fun containsMathSymbol(node: FormulaNode): Boolean = when (node) {
        is FormulaNode.Symbol -> node.texName != null
        is FormulaNode.Row -> node.children.any(::containsMathSymbol)
        is FormulaNode.Delimited -> containsMathSymbol(node.content)
        else -> false
    }

    private companion object {
        const val MAX_CODE_POINTS = 200
        const val MAX_UTF16_LENGTH = 255
        const val MAX_TOKENS = 128
        const val MAX_DEPTH = 32
    }
}

private class SimpleFormulaParser(
    private val source: String,
    private val maxTokens: Int,
    private val maxDepth: Int,
) {
    private var index = 0
    private var tokenCount = 0
    private var depth = 0
    private var failed = false

    fun parse(): FormulaNode? {
        val result = parseRelation(emptySet()) ?: return null
        skipSpaces()
        return result.takeIf { !failed && index == source.length }
    }

    private fun parseRelation(stop: Set<Char>): FormulaNode? {
        var left = parseAdditive(stop) ?: return null
        while (!failed && !atStop(stop)) {
            skipSpaces()
            val relation = readRelationOperator() ?: break
            val right = parseAdditive(stop) ?: return null
            left = formulaRow(listOf(left, formulaSymbol(relation), right))
        }
        return left
    }

    private fun parseAdditive(stop: Set<Char>): FormulaNode? {
        var left = parseMultiplicative(stop) ?: return null
        while (!failed && !atStop(stop)) {
            skipSpaces()
            val operator = when {
                consume('+') -> "+"
                peek() == '-' && !source.startsWith("->", index) -> {
                    consume('-')
                    "-"
                }
                consume('−') -> "−"
                else -> null
            } ?: break
            val right = parseMultiplicative(stop) ?: return null
            left = formulaRow(listOf(left, formulaSymbol(operator), right))
        }
        return left
    }

    private fun parseMultiplicative(stop: Set<Char>): FormulaNode? {
        var left = parseUnary(stop) ?: return null
        while (!failed && !atStop(stop)) {
            skipSpaces()
            when {
                consume('*') -> {
                    val right = parseUnary(stop) ?: return null
                    left = formulaRow(listOf(left, FormulaNode.Symbol("×", "\\times"), right))
                }

                consume('·') || consume('×') || consume('⋅') -> {
                    val right = parseUnary(stop) ?: return null
                    val symbol = if (source[index - 1] == '×') "×" else "⋅"
                    val texName = if (symbol == "×") "\\times" else "\\cdot"
                    left = formulaRow(listOf(left, FormulaNode.Symbol(symbol, texName), right))
                }

                consume('/') || consume('÷') -> {
                    val right = parseUnary(stop) ?: return null
                    left = FormulaNode.Fraction(left, right)
                }

                peekIdentifier("mod") -> {
                    readIdentifier()
                    val right = parseUnary(stop) ?: return null
                    left = formulaRow(listOf(left, formulaSymbol("mod"), right))
                }

                startsPrimary() -> {
                    val right = parseUnary(stop) ?: return null
                    left = attachLargeOperatorOperand(left, right)
                        ?: formulaRow(listOf(left, right))
                }

                else -> return left
            }
        }
        return left
    }

    private fun attachLargeOperatorOperand(
        operator: FormulaNode,
        operand: FormulaNode,
    ): FormulaNode? {
        if (operator !is FormulaNode.LargeOperator) return null
        if (operator.lower == null && operator.upper == null) return null
        if (operator.operand == null) return operator.copy(operand = operand)
        if (operator.operand is FormulaNode.Symbol && operand is FormulaNode.Delimited) {
            return operator.copy(
                operand = formulaRow(listOf(operator.operand, operand)),
            )
        }
        return null
    }

    private fun parseUnary(stop: Set<Char>): FormulaNode? {
        skipSpaces()
        return when {
            consume('+') -> formulaRow(listOf(formulaSymbol("+"), parseUnary(stop) ?: return null))
            consume('-') -> formulaRow(listOf(formulaSymbol("-"), parseUnary(stop) ?: return null))
            consume('−') -> formulaRow(listOf(formulaSymbol("−"), parseUnary(stop) ?: return null))
            else -> parsePower(stop)
        }
    }

    private fun parsePower(stop: Set<Char>): FormulaNode? {
        return parsePostfix(stop)
    }

    private fun parsePostfix(stop: Set<Char>): FormulaNode? {
        var value = parsePrimary(stop) ?: return null
        var subscript: FormulaNode? = null
        var superscript: FormulaNode? = null
        var subscriptWasParenthesized = false
        var superscriptWasParenthesized = false
        while (!failed) {
            skipSpaces()
            when {
                consume('_') -> {
                    val (operand, parenthesized) = parseScriptOperand(stop) ?: return null
                    if (subscript != null) return null
                    subscript = operand
                    subscriptWasParenthesized = parenthesized
                }

                consume('^') -> {
                    val (operand, parenthesized) = parseScriptOperand(stop) ?: return null
                    if (superscript != null) return null
                    superscript = operand
                    superscriptWasParenthesized = parenthesized
                }

                consume('!') -> value = formulaRow(listOf(value, formulaSymbol("!")))
                consume('%') -> value = formulaRow(listOf(value, formulaSymbol("%")))
                else -> break
            }
        }
        return if (subscript == null && superscript == null) {
            value
        } else if (value is FormulaNode.Symbol) {
            simpleLargeOperatorName(value.value)?.let { name ->
                FormulaNode.LargeOperator(
                    name = name,
                    lower = subscript,
                    upper = superscript,
                )
            } ?: FormulaNode.Script(
                base = value,
                subscript = subscript,
                superscript = superscript,
                subscriptWasParenthesized = subscriptWasParenthesized,
                superscriptWasParenthesized = superscriptWasParenthesized,
            )
        } else {
            FormulaNode.Script(
                base = value,
                subscript = subscript,
                superscript = superscript,
                subscriptWasParenthesized = subscriptWasParenthesized,
                superscriptWasParenthesized = superscriptWasParenthesized,
            )
        }
    }

    private fun parsePrimary(stop: Set<Char>): FormulaNode? {
        skipSpaces()
        if (atEnd() || atStop(stop)) return null
        if (consume('(')) {
            if (!enterDepth()) return null
            val content = parseRelation(setOf(')')) ?: return null
            skipSpaces()
            if (!consume(')')) return null
            leaveDepth()
            return FormulaNode.Delimited("(", content, ")")
        }
        if (consume('[')) {
            if (!enterDepth()) return null
            val content = parseRelation(setOf(']')) ?: return null
            skipSpaces()
            if (!consume(']')) return null
            leaveDepth()
            return FormulaNode.Delimited("[", content, "]")
        }
        if (peek() == '⌊') return parseSimpleDelimited('⌊', '⌋')
        if (peek() == '⌈') return parseSimpleDelimited('⌈', '⌉')
        if (consume('{')) {
            if (!enterDepth()) return null
            val content = parseRelation(setOf('}')) ?: return null
            skipSpaces()
            if (!consume('}')) return null
            leaveDepth()
            return content
        }
        if (source.startsWith("||", index)) return parseAsciiNorm()
        if (peek() == '|') return parseAbsoluteValue()
        if (peek() == '‖') return parseDoubleBarValue()
        if (peek() == '√') {
            consume('√')
            val radicand = parseFunctionArgument(stop) ?: return null
            return FormulaNode.Radical(
                radicand = radicand,
                radicandWasParenthesized = shouldParenthesizeScriptForUnicode(radicand),
            )
        }
        if (peek()?.isDigit() == true || (peek() == '.' && source.getOrNull(index + 1)?.isDigit() == true)) {
            return FormulaNode.Number(readNumber())
        }
        if (isIdentifierStart(peek())) {
            val identifier = readIdentifier()
            return parseIdentifier(identifier, stop)
        }
        val symbol = readSimpleSymbol() ?: return null
        return formulaSymbol(symbol)
    }

    private fun parseIdentifier(identifier: String, stop: Set<Char>): FormulaNode? {
        val lower = identifier.lowercase()
        when (lower) {
            "sqrt" -> {
                val radicand = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Radical(
                    radicand = radicand,
                    radicandWasParenthesized = shouldParenthesizeScriptForUnicode(radicand),
                )
            }

            "root", "nroot" -> {
                val arguments = parseCommaArguments(expectedAtLeast = 2) ?: return null
                return FormulaNode.Radical(arguments[1], index = arguments[0])
            }

            "sum", "sigma", "prod", "product", "int", "integral", "iint", "iiint", "oint" -> {
                skipSpaces()
                if (peek() != '(') return formulaSymbol(identifier)
                val arguments = parseCommaArguments(expectedAtLeast = 3) ?: return null
                val name = when (lower) {
                    "sigma" -> "sum"
                    "product" -> "prod"
                    "integral" -> "int"
                    else -> lower
                }
                return FormulaNode.LargeOperator(
                    name = name,
                    lower = arguments[0],
                    upper = arguments[1],
                    operand = formulaRow(arguments.drop(2)),
                )
            }

            "lim", "limit" -> {
                skipSpaces()
                if (peek() != '(') return formulaSymbol(identifier)
                val arguments = parseCommaArguments(expectedAtLeast = 2) ?: return null
                return FormulaNode.LargeOperator(
                    name = "lim",
                    lower = arguments[0],
                    operand = formulaRow(arguments.drop(1)),
                )
            }

            "min", "max" -> {
                val arguments = parseCommaArguments(expectedAtLeast = 2)
                    ?: return null
                return formulaRow(
                    listOf(
                        FormulaNode.Symbol(lower, "\\$lower"),
                        FormulaNode.Delimited("(", formulaRow(arguments), ")"),
                    )
                )
            }

            "abs", "absolute" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Delimited("|", argument, "|")
            }

            "norm" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Delimited("‖", argument, "‖")
            }

            "floor", "ceil", "ceiling" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Delimited(
                    left = if (lower == "floor") "⌊" else "⌈",
                    content = argument,
                    right = if (lower == "floor") "⌋" else "⌉",
                )
            }

            "vec", "vector" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Accent(AccentKind.VEC, argument)
            }

            "hat" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Accent(AccentKind.HAT, argument)
            }

            "bar", "overline" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Accent(AccentKind.BAR, argument)
            }

            "dot" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Accent(AccentKind.DOT, argument)
            }

            "ddot" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Accent(AccentKind.DDOT, argument)
            }

            "tilde" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Accent(AccentKind.TILDE, argument)
            }

            "underline" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Accent(AccentKind.UNDERLINE, argument)
            }

            "bold" -> {
                val argument = parseFunctionArgument(stop) ?: return null
                return FormulaNode.Accent(AccentKind.BOLD, argument)
            }
        }

        val knownSymbol = simpleIdentifierSymbol(identifier)
        if (knownSymbol != null) return knownSymbol

        return formulaSymbol(identifier)
    }

    private fun parseFunctionArgument(stop: Set<Char>): FormulaNode? {
        skipSpaces()
        return when {
            consume('(') -> {
                if (!enterDepth()) return null
                val content = parseRelation(setOf(')')) ?: return null
                skipSpaces()
                if (!consume(')')) return null
                leaveDepth()
                content
            }

            consume('{') -> {
                if (!enterDepth()) return null
                val content = parseRelation(setOf('}')) ?: return null
                skipSpaces()
                if (!consume('}')) return null
                leaveDepth()
                content
            }

            else -> {
                if (!enterDepth()) return null
                val value = parsePrimary(stop)
                leaveDepth()
                value
            }
        }
    }

    private fun parseSimpleDelimited(left: Char, right: Char): FormulaNode? {
        if (!consume(left)) return null
        if (!enterDepth()) return null
        val content = parseRelation(setOf(right)) ?: return null
        skipSpaces()
        if (!consume(right)) return null
        leaveDepth()
        return FormulaNode.Delimited(left.toString(), content, right.toString())
    }

    private fun parseCommaArguments(expectedAtLeast: Int): List<FormulaNode>? {
        skipSpaces()
        if (!consume('(')) return null
        if (!enterDepth()) return null
        val arguments = mutableListOf<FormulaNode>()
        while (true) {
            val argument = parseRelation(setOf(',', ')')) ?: return null
            arguments += argument
            skipSpaces()
            when {
                consume(',') -> Unit
                consume(')') -> break
                else -> return null
            }
            if (arguments.size > maxTokens) return null
        }
        leaveDepth()
        return arguments.takeIf { it.size >= expectedAtLeast }
    }

    private fun parseAbsoluteValue(): FormulaNode? {
        consume('|')
        if (!enterDepth()) return null
        val content = parseRelation(setOf('|')) ?: return null
        skipSpaces()
        if (!consume('|')) return null
        leaveDepth()
        return FormulaNode.Delimited("|", content, "|")
    }

    private fun parseDoubleBarValue(): FormulaNode? {
        consume('‖')
        if (!enterDepth()) return null
        val content = parseRelation(setOf('‖')) ?: return null
        skipSpaces()
        if (!consume('‖')) return null
        leaveDepth()
        return FormulaNode.Delimited("‖", content, "‖")
    }

    private fun parseAsciiNorm(): FormulaNode? {
        if (!consume('|') || !consume('|')) return null
        if (!enterDepth()) return null
        val content = parseRelation(setOf('|')) ?: return null
        skipSpaces()
        if (!consume('|') || !consume('|')) return null
        leaveDepth()
        return FormulaNode.Delimited("‖", content, "‖")
    }

    private fun readNumber(): String {
        countToken()
        val start = index
        var digits = 0
        while (source.getOrNull(index)?.isDigit() == true) {
            index++
            digits++
        }
        if (source.getOrNull(index) == '.') {
            index++
            while (source.getOrNull(index)?.isDigit() == true) {
                index++
                digits++
            }
        }
        if (digits == 0) failed = true
        return source.substring(start, index)
    }

    private fun readIdentifier(): String {
        countToken()
        val start = index
        while (isIdentifierPart(source.getOrNull(index))) index++
        return source.substring(start, index)
    }

    private fun readSimpleSymbol(): String? {
        val twoCharacter = source.substring(index).take(2)
        val symbol = when {
            twoCharacter == "<=" -> "≤"
            twoCharacter == ">=" -> "≥"
            twoCharacter == "!=" -> "≠"
            twoCharacter == "==" -> "="
            twoCharacter == "->" -> "→"
            twoCharacter == "=>" -> "⇒"
            else -> null
        }
        if (symbol != null) {
            countToken()
            index += 2
            return symbol
        }
        val char = peek() ?: return null
        if (char in "+-*/^_=<>%,!()[]{}|·×÷≤≥≠≈≃≡≢≅≍≰≱∼∝→⇒↔⇔←↦⟶⟹⟷⟺⟵⟼±∓⋅⊄⊅⊈⊉∈∉∋⊂⊆⊃⊇∪∩∀∃∧∨¬∅ℕℤℚℝℂ∞πΠ∑∏∫∬∭∮∂∇‖∥∣:;" ||
            char.isLetter() || char == '√'
        ) {
            countToken()
            index++
            return when (char) {
                '*' -> "×"
                '|' -> "|"
                else -> char.toString()
            }
        }
        failed = true
        return null
    }

    private fun readRelationOperator(): String? {
        skipSpaces()
        val remaining = source.substring(index)
        val operator = when {
            remaining.startsWith("<->") -> "↔"
            remaining.startsWith("=>") -> "⇒"
            remaining.startsWith("->") -> "→"
            remaining.startsWith("<=") -> "≤"
            remaining.startsWith(">=") -> "≥"
            remaining.startsWith("!=") -> "≠"
            remaining.startsWith("==") -> "="
            peek() == '=' -> "="
            peek() == '<' -> "<"
            peek() == '>' -> ">"
            peek() == '≤' -> "≤"
            peek() == '≥' -> "≥"
            peek() == '≠' -> "≠"
            peek() == '≈' -> "≈"
            peek() == '≃' -> "≃"
            peek() == '≡' -> "≡"
            peek() == '≢' -> "≢"
            peek() == '≅' -> "≅"
            peek() == '≍' -> "≍"
            peek() == '≰' -> "≰"
            peek() == '≱' -> "≱"
            peek() == '∼' -> "∼"
            peek() == '∝' -> "∝"
            peek() == '→' -> "→"
            peek() == '⇒' -> "⇒"
            peek() == '↔' -> "↔"
            peek() == '↦' -> "↦"
            peek() == '⟶' -> "⟶"
            peek() == '⟹' -> "⟹"
            peek() == '⟷' -> "⟷"
            peek() == '⟺' -> "⟺"
            peek() == '⟵' -> "⟵"
            peek() == '⟼' -> "⟼"
            peek() == '⇔' -> "⇔"
            peek() == '←' -> "←"
            peek() == '∈' -> "∈"
            peek() == '∉' -> "∉"
            peek() == '∋' -> "∋"
            peek() == '⊂' -> "⊂"
            peek() == '⊆' -> "⊆"
            peek() == '⊃' -> "⊃"
            peek() == '⊇' -> "⊇"
            peek() == '⊄' -> "⊄"
            peek() == '⊅' -> "⊅"
            peek() == '⊈' -> "⊈"
            peek() == '⊉' -> "⊉"
            peek() == '∪' -> "∪"
            peek() == '∩' -> "∩"
            peek() == '∧' -> "∧"
            peek() == '∨' -> "∨"
            peek() == '∥' -> "∥"
            peek() == '∣' -> "∣"
            peek() == '±' -> "±"
            peek() == '∓' -> "∓"
            else -> null
        } ?: return null
        countToken()
        index += when {
            remaining.startsWith("<->") -> 3
            remaining.startsWith("=>") ||
                remaining.startsWith("->") -> 2
            remaining.startsWith("<=") || remaining.startsWith(">=") ||
                remaining.startsWith("!=") || remaining.startsWith("==") -> 2
            else -> 1
        }
        return operator
    }

    private fun simpleIdentifierSymbol(identifier: String): FormulaNode? {
        val lower = identifier.lowercase()
        val greek = GREEK_SYMBOLS[identifier] ?: GREEK_SYMBOLS[lower]
        if (greek == null) return when (lower) {
            "pi" -> FormulaNode.Symbol("π", "\\pi")
            "infty", "infinity" -> FormulaNode.Symbol("∞", "\\infty")
            "aleph" -> FormulaNode.Symbol("ℵ", "\\aleph")
            "forall" -> FormulaNode.Symbol("∀", "\\forall")
            "exists" -> FormulaNode.Symbol("∃", "\\exists")
            "in" -> FormulaNode.Symbol("∈", "\\in")
            "notin" -> FormulaNode.Symbol("∉", "\\notin")
            "ni", "owns" -> FormulaNode.Symbol("∋", "\\ni")
            "subset" -> FormulaNode.Symbol("⊂", "\\subset")
            "subseteq" -> FormulaNode.Symbol("⊆", "\\subseteq")
            "nsubset" -> FormulaNode.Symbol("⊄", "\\nsubset")
            "nsubseteq" -> FormulaNode.Symbol("⊈", "\\nsubseteq")
            "supset" -> FormulaNode.Symbol("⊃", "\\supset")
            "supseteq" -> FormulaNode.Symbol("⊇", "\\supseteq")
            "nsupset" -> FormulaNode.Symbol("⊅", "\\nsupset")
            "nsupseteq" -> FormulaNode.Symbol("⊉", "\\nsupseteq")
            "union", "cup" -> FormulaNode.Symbol("∪", "\\cup")
            "intersect", "intersection", "cap" -> FormulaNode.Symbol("∩", "\\cap")
            "and", "land", "wedge" -> FormulaNode.Symbol("∧", "\\land")
            "or", "lor", "vee" -> FormulaNode.Symbol("∨", "\\lor")
            "not", "neg", "lnot" -> FormulaNode.Symbol("¬", "\\neg")
            "iff" -> FormulaNode.Symbol("⇔", "\\Leftrightarrow")
            "implies" -> FormulaNode.Symbol("⇒", "\\Rightarrow")
            "parallel" -> FormulaNode.Symbol("∥", "\\parallel")
            "mid" -> FormulaNode.Symbol("∣", "\\mid")
            "equiv" -> FormulaNode.Symbol("≡", "\\equiv")
            "nequiv" -> FormulaNode.Symbol("≢", "\\not\\equiv")
            "cong" -> FormulaNode.Symbol("≅", "\\cong")
            "approx" -> FormulaNode.Symbol("≈", "\\approx")
            "simeq" -> FormulaNode.Symbol("≃", "\\simeq")
            "sim" -> FormulaNode.Symbol("∼", "\\sim")
            "propto" -> FormulaNode.Symbol("∝", "\\propto")
            "to", "rightarrow" -> FormulaNode.Symbol("→", "\\to")
            "leftarrow" -> FormulaNode.Symbol("←", "\\leftarrow")
            "mapsto" -> FormulaNode.Symbol("↦", "\\mapsto")
            "emptyset", "empty" -> FormulaNode.Symbol("∅", "\\varnothing")
            "natural", "naturals" -> FormulaNode.Symbol("ℕ", "\\mathbb{N}")
            "integer", "integers" -> FormulaNode.Symbol("ℤ", "\\mathbb{Z}")
            "rational", "rationals" -> FormulaNode.Symbol("ℚ", "\\mathbb{Q}")
            "real", "reals" -> FormulaNode.Symbol("ℝ", "\\mathbb{R}")
            "complex", "complexes" -> FormulaNode.Symbol("ℂ", "\\mathbb{C}")
            "sin" -> FormulaNode.Symbol("sin", "\\sin")
            "cos" -> FormulaNode.Symbol("cos", "\\cos")
            "tan" -> FormulaNode.Symbol("tan", "\\tan")
            "arcsin", "asin" -> FormulaNode.Symbol("arcsin", "\\arcsin")
            "arccos", "acos" -> FormulaNode.Symbol("arccos", "\\arccos")
            "arctan", "atan" -> FormulaNode.Symbol("arctan", "\\arctan")
            "sinh" -> FormulaNode.Symbol("sinh", "\\sinh")
            "cosh" -> FormulaNode.Symbol("cosh", "\\cosh")
            "tanh" -> FormulaNode.Symbol("tanh", "\\tanh")
            "sec" -> FormulaNode.Symbol("sec", "\\sec")
            "csc" -> FormulaNode.Symbol("csc", "\\csc")
            "cot" -> FormulaNode.Symbol("cot", "\\cot")
            "arcsinh", "asinh" -> FormulaNode.Symbol("arcsinh", "\\operatorname{arcsinh}")
            "arccosh", "acosh" -> FormulaNode.Symbol("arccosh", "\\operatorname{arccosh}")
            "arctanh", "atanh" -> FormulaNode.Symbol("arctanh", "\\operatorname{arctanh}")
            "ln" -> FormulaNode.Symbol("ln", "\\ln")
            "log" -> FormulaNode.Symbol("log", "\\log")
            "exp" -> FormulaNode.Symbol("exp", "\\exp")
            "min" -> FormulaNode.Symbol("min", "\\min")
            "max" -> FormulaNode.Symbol("max", "\\max")
            "mod" -> FormulaNode.Symbol("mod", "\\bmod")
            else -> null
        }
        val texIdentifier = if (GREEK_SYMBOLS.containsKey(identifier)) identifier else lower
        return FormulaNode.Symbol(greek, "\\$texIdentifier")
    }

    private fun simpleLargeOperatorName(value: String): String? = when (value.lowercase()) {
        "∑", "sum", "sigma" -> "sum"
        "∏", "prod", "product" -> "prod"
        "∫", "int", "integral" -> "int"
        "∬", "iint" -> "iint"
        "∭", "iiint" -> "iiint"
        "∮", "oint" -> "oint"
        "lim", "limit" -> "lim"
        "min" -> "min"
        "max" -> "max"
        "sup" -> "sup"
        "inf" -> "inf"
        else -> null
    }

    private fun peekIdentifier(identifier: String): Boolean {
        val start = index
        if (!source.regionMatches(start, identifier, 0, identifier.length, ignoreCase = true)) {
            return false
        }
        return !isIdentifierPart(source.getOrNull(start + identifier.length))
    }

    private fun startsPrimary(): Boolean {
        skipSpaces()
        val char = peek() ?: return false
        return char == '(' || char == '[' || char == '{' || char == '|' || char == '‖' ||
            char == '√' || char.isDigit() || char == '.' && source.getOrNull(index + 1)?.isDigit() == true ||
            isIdentifierStart(char) || char in "πΠ∞∑∏∫∬∭∮∂∇∀∃∅ℕℤℚℝℂ"
    }

    private fun isIdentifierStart(char: Char?): Boolean =
        char?.isLetter() == true || char?.let { it in "πΠ∞" } == true

    private fun isIdentifierPart(char: Char?): Boolean =
        char?.isLetterOrDigit() == true || char == 'π' || char == 'Π' || char == '∞'

    private fun atStop(stop: Set<Char>): Boolean = peek()?.let(stop::contains) == true

    private fun peek(): Char? = source.getOrNull(index)

    private fun consume(expected: Char): Boolean {
        if (peek() != expected) return false
        countToken()
        index++
        return true
    }

    private fun skipSpaces() {
        while (source.getOrNull(index)?.isWhitespace() == true) index++
    }

    private fun atEnd(): Boolean = index >= source.length

    private fun countToken() {
        tokenCount++
        if (tokenCount > maxTokens) failed = true
    }

    private fun enterDepth(): Boolean {
        depth++
        if (depth > maxDepth) {
            failed = true
            return false
        }
        return true
    }

    private fun leaveDepth() {
        depth = (depth - 1).coerceAtLeast(0)
    }

    private fun parseScriptOperand(stop: Set<Char>): Pair<FormulaNode, Boolean>? {
        skipSpaces()
        return when {
            consume('(') -> {
                if (!enterDepth()) return null
                val value = parseRelation(setOf(')')) ?: return null
                skipSpaces()
                if (!consume(')')) return null
                leaveDepth()
                value to shouldParenthesizeScriptForUnicode(value)
            }

            consume('{') -> {
                if (!enterDepth()) return null
                val value = parseRelation(setOf('}')) ?: return null
                skipSpaces()
                if (!consume('}')) return null
                leaveDepth()
                value to shouldParenthesizeScriptForUnicode(value)
            }

            // An unbraced script is one primary atom.  Calling parsePostfix here would absorb
            // the next ^/_ into the script itself (x_1^2 -> x_{1^2}).
            else -> parsePrimary(stop)?.let { it to false }
        }
    }

    private companion object {
        val GREEK_SYMBOLS = mapOf(
            "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
            "epsilon" to "ϵ", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
            "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
            "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
            "omicron" to "ο", "pi" to "π", "varpi" to "ϖ", "rho" to "ρ",
            "varrho" to "ϱ", "sigma" to "σ", "varsigma" to "ς", "tau" to "τ",
            "upsilon" to "υ", "phi" to "ϕ", "varphi" to "φ", "chi" to "χ",
            "psi" to "ψ", "omega" to "ω", "Gamma" to "Γ", "Delta" to "Δ",
            "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
            "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ",
            "Omega" to "Ω",
        )
    }
}

private class LatexFormulaParser(
    private val source: String,
    private val maxTokens: Int,
    private val maxDepth: Int,
) {
    private var index = 0
    private var tokenCount = 0
    private var depth = 0
    private var failed = false

    fun parse(): FormulaNode? {
        val result = parseRow(Stop.End) ?: return null
        skipWhitespace()
        return result.takeIf { !failed && index == source.length }
    }

    private fun parseRow(stop: Stop): FormulaNode? {
        val children = mutableListOf<FormulaNode>()
        while (!failed) {
            skipWhitespace()
            if (isStop(stop)) break
            if (atEnd()) {
                if (stop != Stop.End) failed = true
                break
            }
            val atom = parseAtom() ?: return null
            val scriptedAtom = applyScripts(atom) ?: return null
            val previous = children.lastOrNull()
            if (previous is FormulaNode.LargeOperator) {
                val updatedOperator = attachLargeOperatorOperand(previous, scriptedAtom)
                if (updatedOperator != null) {
                    children[children.lastIndex] = updatedOperator
                    continue
                }
            }
            children += scriptedAtom
        }
        if (children.isEmpty()) return null
        return formulaRow(children)
    }

    private fun attachLargeOperatorOperand(
        operator: FormulaNode.LargeOperator,
        operand: FormulaNode,
    ): FormulaNode? {
        if (operator.lower == null && operator.upper == null) return null
        if (operator.operand == null) return operator.copy(operand = operand)
        if (operator.operand is FormulaNode.Symbol && operand is FormulaNode.Delimited) {
            return operator.copy(
                operand = formulaRow(listOf(operator.operand, operand)),
            )
        }
        return null
    }

    private fun parseAtom(): FormulaNode? {
        if (atEnd()) return null
        return when {
            consume('{') -> parseBraceGroup()
            peek() == '\\' -> parseCommand()
            peek()?.isDigit() == true || (peek() == '.' && source.getOrNull(index + 1)?.isDigit() == true) ->
                FormulaNode.Number(readNumber())
            peek()?.isLetter() == true -> formulaSymbol(readIdentifier())
            peek() == '(' -> parsePlainDelimited('(', ')')
            peek() == '[' -> parsePlainDelimited('[', ']')
            peek() == '|' -> {
                consume('|')
                FormulaNode.Symbol("|")
            }
            peek() == '‖' -> {
                consume('‖')
                FormulaNode.Symbol("‖", "\\Vert")
            }
            else -> readLatexSymbol()?.let(::formulaSymbol)
        }
    }

    private fun parseCommand(): FormulaNode? {
        if (!consume('\\')) return null
        val command = readCommandName() ?: return null
        return when (command) {
            "frac", "dfrac", "tfrac" -> {
                val numerator = parseRequiredGroup() ?: return null
                val denominator = parseRequiredGroup() ?: return null
                FormulaNode.Fraction(numerator, denominator)
            }

            "sqrt" -> {
                val index = parseOptionalBracket()
                val radicand = parseRequiredGroup() ?: return null
                FormulaNode.Radical(
                    radicand = radicand,
                    index = index,
                    radicandWasParenthesized = shouldParenthesizeScriptForUnicode(radicand),
                )
            }

            "left" -> parseSizedDelimited()
            "lfloor" -> parsePairedCommandDelimited("⌊", "rfloor", "⌋")
            "lceil" -> parsePairedCommandDelimited("⌈", "rceil", "⌉")
            "lvert" -> parsePairedCommandDelimited("|", "rvert", "|")
            "lVert" -> parsePairedCommandDelimited("‖", "rVert", "‖")
            "langle" -> parsePairedCommandDelimited("⟨", "rangle", "⟩")
            "lbrace" -> parsePairedCommandDelimited("{", "rbrace", "}")
            "lbrack" -> parsePairedCommandDelimited("[", "rbrack", "]")
            "lparen" -> parsePairedCommandDelimited("(", "rparen", ")")
            "right", "middle", "begin", "end" -> null

            "operatorname" -> {
                val content = parseRequiredGroup() ?: return null
                val text = FormulaFormatter.unicode(content)
                FormulaNode.Symbol(text, "\\operatorname{$text}")
            }

            "not" -> {
                val next = parseAtom() ?: return null
                val nextUnicode = FormulaFormatter.unicode(next)
                val unicode = when (nextUnicode) {
                    "=" -> "≠"
                    "∈" -> "∉"
                    "≡" -> "≢"
                    "⊂" -> "⊄"
                    "⊃" -> "⊅"
                    "⊆" -> "⊈"
                    "⊇" -> "⊉"
                    else -> "¬$nextUnicode"
                }
                FormulaNode.Symbol(unicode, "\\not${FormulaFormatter.tex(next)}")
            }

            "mathbb", "Bbb" -> parseNumberSet()
            "mathbf", "boldsymbol" -> {
                val content = parseRequiredGroup() ?: return null
                FormulaNode.Accent(AccentKind.BOLD, content)
            }

            "mathcal", "mathsf", "mathtt", "mathit", "mathnormal" -> {
                val content = parseRequiredGroup() ?: return null
                val tex = FormulaFormatter.tex(content)
                FormulaNode.Symbol(
                    FormulaFormatter.unicode(content),
                    "\\$command{$tex}",
                )
            }

            "text", "mathrm" -> {
                val content = parseRequiredGroup() ?: return null
                val text = FormulaFormatter.unicode(content)
                FormulaNode.Symbol(text, "\\$command{$text}")
            }

            in SPACING_COMMANDS -> FormulaNode.Symbol("")

            in ACCENT_COMMANDS -> {
                val content = parseRequiredGroup() ?: return null
                FormulaNode.Accent(ACCENT_COMMANDS.getValue(command), content)
            }

            "{" -> FormulaNode.Symbol("{", "\\{")
            "}" -> FormulaNode.Symbol("}", "\\}")
            in LARGE_OPERATOR_COMMANDS -> FormulaNode.LargeOperator(command)
            else -> LATEX_SYMBOLS[command]?.let { (unicode, tex) -> FormulaNode.Symbol(unicode, tex) }
        }
    }

    private fun parseSizedDelimited(): FormulaNode? {
        val left = readDelimiter() ?: return null
        val content = parseRow(Stop.RightCommand) ?: return null
        if (!peekCommand("right")) return null
        consumeCommand("right")
        val right = readDelimiter() ?: return null
        return FormulaNode.Delimited(left, content, right, latexSized = true)
    }

    private fun parsePairedCommandDelimited(
        left: String,
        rightCommand: String,
        right: String,
    ): FormulaNode? {
        val content = parseRow(Stop.CommandStop(rightCommand)) ?: return null
        if (!peekCommand(rightCommand)) return null
        consumeCommand(rightCommand)
        return FormulaNode.Delimited(left, content, right)
    }

    private fun parseNumberSet(): FormulaNode? {
        val content = parseRequiredGroup() ?: return null
        val (symbol, asciiName) = when (FormulaFormatter.unicode(content)) {
            "N", "ℕ" -> "ℕ" to "N"
            "Z", "ℤ" -> "ℤ" to "Z"
            "Q", "ℚ" -> "ℚ" to "Q"
            "R", "ℝ" -> "ℝ" to "R"
            "C", "ℂ" -> "ℂ" to "C"
            else -> return FormulaNode.Accent(AccentKind.BOLD, content)
        }
        return FormulaNode.Symbol(symbol, "\\mathbb{$asciiName}")
    }

    private fun parsePlainDelimited(left: Char, right: Char): FormulaNode? {
        if (!consume(left)) return null
        if (!enterDepth()) return null
        val content = parseRow(Stop.CharStop(right)) ?: return null
        skipWhitespace()
        if (!consume(right)) return null
        leaveDepth()
        return FormulaNode.Delimited(left.toString(), content, right.toString())
    }

    private fun parseBraceGroup(): FormulaNode? {
        if (!enterDepth()) return null
        val content = parseRow(Stop.CharStop('}')) ?: return null
        skipWhitespace()
        if (!consume('}')) return null
        leaveDepth()
        return content
    }

    private fun parseRequiredGroup(): FormulaNode? {
        skipWhitespace()
        if (!consume('{')) return null
        return parseBraceGroup()
    }

    private fun parseOptionalBracket(): FormulaNode? {
        skipWhitespace()
        if (!consume('[')) return null
        if (!enterDepth()) return null
        val content = parseRow(Stop.CharStop(']')) ?: return null
        skipWhitespace()
        if (!consume(']')) return null
        leaveDepth()
        return content
    }

    private fun applyScripts(base: FormulaNode): FormulaNode? {
        var value = base
        var subscript: FormulaNode? = null
        var superscript: FormulaNode? = null
        var subscriptWasParenthesized = false
        var superscriptWasParenthesized = false
        while (!failed) {
            skipWhitespace()
            when {
                consume('_') -> {
                    val (operand, parenthesized) = parseScriptArgument() ?: return null
                    if (subscript != null) return null
                    subscript = operand
                    subscriptWasParenthesized = parenthesized
                }

                consume('^') -> {
                    val (operand, parenthesized) = parseScriptArgument() ?: return null
                    if (superscript != null) return null
                    superscript = operand
                    superscriptWasParenthesized = parenthesized
                }

                else -> break
            }
        }
        if (subscript == null && superscript == null) return value
        if (value is FormulaNode.LargeOperator) {
            return value.copy(lower = subscript, upper = superscript)
        }
        return FormulaNode.Script(
            value,
            subscript = subscript,
            superscript = superscript,
            subscriptWasParenthesized = subscriptWasParenthesized,
            superscriptWasParenthesized = superscriptWasParenthesized,
        )
    }

    private fun parseScriptArgument(): Pair<FormulaNode, Boolean>? {
        skipWhitespace()
        return if (consume('{')) {
            parseBraceGroup()?.let { it to shouldParenthesizeScriptForUnicode(it) }
        } else {
            parseAtom()?.let { atom ->
                applyScripts(atom)?.let { it to false }
            }
        }
    }

    private fun readDelimiter(): String? {
        skipWhitespace()
        if (peek() == '\\') {
            if (!consume('\\')) return null
            val command = readCommandName() ?: return null
            return when (command) {
                "langle" -> "⟨"
                "rangle" -> "⟩"
                "lfloor" -> "⌊"
                "rfloor" -> "⌋"
                "lceil" -> "⌈"
                "rceil" -> "⌉"
                "lvert", "rvert", "vert" -> "|"
                "Vert", "lVert", "rVert", "|" -> "‖"
                "lbrace" -> "{"
                "rbrace" -> "}"
                "lbrack" -> "["
                "rbrack" -> "]"
                "lparen" -> "("
                "rparen" -> ")"
                "{", "}" -> command
                else -> null
            }
        }
        val char = peek() ?: return null
        if (char in "([{|⟨⟩)]}‖⌊⌋⌈⌉") {
            consume(char)
            return char.toString()
        }
        if (char == '.') {
            consume('.')
            return ""
        }
        return null
    }

    private fun readLatexSymbol(): String? {
        val char = peek() ?: return null
        val symbol = when (char) {
            '+', '-', '*', '/', '=', '<', '>', ',', '.', ':', ';', '!', '(', ')', '[', ']',
            '|', '%', '&', '#', '~' -> char.toString()
            '×', '÷', '≤', '≥', '≠', '≈', '∞', 'π', 'Π', '∂', '∇', '√',
            '∑', '∏', '∫', '∬', '∭', '∮', '∼', '∝', '→', '⇒', '↔', '←', '↦',
            '±', '∓', '⋅', '∈', '∉', '⊂', '⊆', '⊃', '⊇', '∪', '∩', '∀', '∃',
            '∧', '∨', '¬', '∅', 'ℕ', 'ℤ', 'ℚ', 'ℝ', 'ℂ', 'ℵ', '‖', '∥', '∣' -> char.toString()
            else -> return null.also { failed = true }
        }
        consume(char)
        return symbol
    }

    private fun readNumber(): String {
        countToken()
        val start = index
        while (source.getOrNull(index)?.isDigit() == true) index++
        if (source.getOrNull(index) == '.') {
            index++
            while (source.getOrNull(index)?.isDigit() == true) index++
        }
        return source.substring(start, index)
    }

    private fun readIdentifier(): String {
        countToken()
        val start = index
        while (source.getOrNull(index)?.isLetterOrDigit() == true) index++
        return source.substring(start, index)
    }

    private fun readCommandName(): String? {
        if (atEnd()) return null
        val start = index
        if (source[index].isLetter()) {
            while (source.getOrNull(index)?.isLetter() == true) index++
        } else {
            index++
        }
        return source.substring(start, index).takeIf { it.isNotEmpty() }
    }

    private fun peekCommand(command: String): Boolean {
        if (peek() != '\\') return false
        var cursor = index + 1
        while (source.getOrNull(cursor)?.isLetter() == true) cursor++
        return source.substring(index + 1, cursor) == command
    }

    private fun consumeCommand(command: String): Boolean {
        if (!peekCommand(command)) return false
        consume('\\')
        readCommandName()
        return true
    }

    private fun skipWhitespace() {
        while (source.getOrNull(index)?.isWhitespace() == true) index++
    }

    private fun isStop(stop: Stop): Boolean = when (stop) {
        Stop.End -> false
        is Stop.CharStop -> peek() == stop.char
        Stop.RightCommand -> peekCommand("right")
        is Stop.CommandStop -> peekCommand(stop.command)
    }

    private fun atEnd(): Boolean = index >= source.length

    private fun peek(): Char? = source.getOrNull(index)

    private fun consume(expected: Char): Boolean {
        if (peek() != expected) return false
        countToken()
        index++
        return true
    }

    private fun countToken() {
        tokenCount++
        if (tokenCount > maxTokens) failed = true
    }

    private fun enterDepth(): Boolean {
        depth++
        if (depth > maxDepth) {
            failed = true
            return false
        }
        return true
    }

    private fun leaveDepth() {
        depth = (depth - 1).coerceAtLeast(0)
    }

    private sealed interface Stop {
        data object End : Stop
        data class CharStop(val char: Char) : Stop
        data object RightCommand : Stop
        data class CommandStop(val command: String) : Stop
    }

    private companion object {
        val ACCENT_COMMANDS = mapOf(
            "hat" to AccentKind.HAT,
            "widehat" to AccentKind.HAT,
            "bar" to AccentKind.BAR,
            "overline" to AccentKind.OVERLINE,
            "dot" to AccentKind.DOT,
            "ddot" to AccentKind.DDOT,
            "tilde" to AccentKind.TILDE,
            "widetilde" to AccentKind.TILDE,
            "vec" to AccentKind.VEC,
            "underline" to AccentKind.UNDERLINE,
        )

        val LARGE_OPERATOR_COMMANDS = setOf(
            "sum", "prod", "int", "iint", "iiint", "oint", "lim", "min", "max", "sup", "inf",
        )

        val LATEX_SYMBOLS = mapOf(
            "pi" to ("π" to "\\pi"),
            "Pi" to ("Π" to "\\Pi"),
            "infty" to ("∞" to "\\infty"),
            "emptyset" to ("∅" to "\\emptyset"),
            "varnothing" to ("∅" to "\\varnothing"),
            "pm" to ("±" to "\\pm"),
            "mp" to ("∓" to "\\mp"),
            "times" to ("×" to "\\times"),
            "cdot" to ("⋅" to "\\cdot"),
            "div" to ("÷" to "\\div"),
            "le" to ("≤" to "\\leq"),
            "leq" to ("≤" to "\\leq"),
            "ge" to ("≥" to "\\geq"),
            "geq" to ("≥" to "\\geq"),
            "ne" to ("≠" to "\\neq"),
            "neq" to ("≠" to "\\neq"),
            "nleq" to ("≰" to "\\nleq"),
            "ngeq" to ("≱" to "\\ngeq"),
            "approx" to ("≈" to "\\approx"),
            "simeq" to ("≃" to "\\simeq"),
            "sim" to ("∼" to "\\sim"),
            "propto" to ("∝" to "\\propto"),
            "equiv" to ("≡" to "\\equiv"),
            "nequiv" to ("≢" to "\\not\\equiv"),
            "cong" to ("≅" to "\\cong"),
            "asymp" to ("≍" to "\\asymp"),
            "to" to ("→" to "\\to"),
            "rightarrow" to ("→" to "\\rightarrow"),
            "longrightarrow" to ("⟶" to "\\longrightarrow"),
            "Rightarrow" to ("⇒" to "\\Rightarrow"),
            "Longrightarrow" to ("⟹" to "\\Longrightarrow"),
            "leftrightarrow" to ("↔" to "\\leftrightarrow"),
            "Leftrightarrow" to ("⇔" to "\\Leftrightarrow"),
            "iff" to ("⇔" to "\\Leftrightarrow"),
            "longleftrightarrow" to ("⟷" to "\\longleftrightarrow"),
            "Longleftrightarrow" to ("⟺" to "\\Longleftrightarrow"),
            "leftarrow" to ("←" to "\\leftarrow"),
            "longleftarrow" to ("⟵" to "\\longleftarrow"),
            "mapsto" to ("↦" to "\\mapsto"),
            "longmapsto" to ("⟼" to "\\longmapsto"),
            "in" to ("∈" to "\\in"),
            "notin" to ("∉" to "\\notin"),
            "ni" to ("∋" to "\\ni"),
            "owns" to ("∋" to "\\owns"),
            "subset" to ("⊂" to "\\subset"),
            "subseteq" to ("⊆" to "\\subseteq"),
            "nsubseteq" to ("⊈" to "\\nsubseteq"),
            "supset" to ("⊃" to "\\supset"),
            "supseteq" to ("⊇" to "\\supseteq"),
            "nsupseteq" to ("⊉" to "\\nsupseteq"),
            "nsubset" to ("⊄" to "\\nsubset"),
            "nsupset" to ("⊅" to "\\nsupset"),
            "cup" to ("∪" to "\\cup"),
            "cap" to ("∩" to "\\cap"),
            "forall" to ("∀" to "\\forall"),
            "exists" to ("∃" to "\\exists"),
            "land" to ("∧" to "\\land"),
            "wedge" to ("∧" to "\\wedge"),
            "lor" to ("∨" to "\\lor"),
            "vee" to ("∨" to "\\vee"),
            "neg" to ("¬" to "\\neg"),
            "lnot" to ("¬" to "\\lnot"),
            "partial" to ("∂" to "\\partial"),
            "nabla" to ("∇" to "\\nabla"),
            "parallel" to ("∥" to "\\parallel"),
            "mid" to ("∣" to "\\mid"),
            "Vert" to ("‖" to "\\Vert"),
            "vert" to ("|" to "|"),
            "|" to ("‖" to "\\Vert"),
            "sin" to ("sin" to "\\sin"),
            "cos" to ("cos" to "\\cos"),
            "tan" to ("tan" to "\\tan"),
            "arcsin" to ("arcsin" to "\\arcsin"),
            "arccos" to ("arccos" to "\\arccos"),
            "arctan" to ("arctan" to "\\arctan"),
            "asin" to ("arcsin" to "\\arcsin"),
            "acos" to ("arccos" to "\\arccos"),
            "atan" to ("arctan" to "\\arctan"),
            "sec" to ("sec" to "\\sec"),
            "csc" to ("csc" to "\\csc"),
            "cot" to ("cot" to "\\cot"),
            "sinh" to ("sinh" to "\\sinh"),
            "cosh" to ("cosh" to "\\cosh"),
            "tanh" to ("tanh" to "\\tanh"),
            "log" to ("log" to "\\log"),
            "ln" to ("ln" to "\\ln"),
            "exp" to ("exp" to "\\exp"),
            "min" to ("min" to "\\min"),
            "max" to ("max" to "\\max"),
            "bmod" to ("mod" to "\\bmod"),
            "colon" to (":" to "\\colon"),
            "aleph" to ("ℵ" to "\\aleph"),
            "%" to ("%" to "\\%"),
            "#" to ("#" to "\\#"),
        ) + greekLatexSymbols()

        val SPACING_COMMANDS = setOf(
            " ", "!", ",", ":", ";", "quad", "qquad", "enspace", "thinspace",
            "medspace", "thickspace",
        )

        private fun greekLatexSymbols(): Map<String, Pair<String, String>> = mapOf(
            "alpha" to ("α" to "\\alpha"), "beta" to ("β" to "\\beta"),
            "gamma" to ("γ" to "\\gamma"), "delta" to ("δ" to "\\delta"),
            "epsilon" to ("ϵ" to "\\epsilon"), "varepsilon" to ("ε" to "\\varepsilon"),
            "zeta" to ("ζ" to "\\zeta"), "eta" to ("η" to "\\eta"),
            "theta" to ("θ" to "\\theta"), "vartheta" to ("ϑ" to "\\vartheta"),
            "iota" to ("ι" to "\\iota"), "kappa" to ("κ" to "\\kappa"),
            "lambda" to ("λ" to "\\lambda"), "mu" to ("μ" to "\\mu"),
            "nu" to ("ν" to "\\nu"), "xi" to ("ξ" to "\\xi"),
            "omicron" to ("ο" to "\\omicron"), "rho" to ("ρ" to "\\rho"),
            "varrho" to ("ϱ" to "\\varrho"), "sigma" to ("σ" to "\\sigma"),
            "varsigma" to ("ς" to "\\varsigma"), "tau" to ("τ" to "\\tau"),
            "upsilon" to ("υ" to "\\upsilon"), "phi" to ("ϕ" to "\\phi"),
            "varphi" to ("φ" to "\\varphi"), "chi" to ("χ" to "\\chi"),
            "psi" to ("ψ" to "\\psi"), "omega" to ("ω" to "\\omega"),
            "Gamma" to ("Γ" to "\\Gamma"), "Delta" to ("Δ" to "\\Delta"),
            "Theta" to ("Θ" to "\\Theta"), "Lambda" to ("Λ" to "\\Lambda"),
            "Xi" to ("Ξ" to "\\Xi"), "Sigma" to ("Σ" to "\\Sigma"),
            "Upsilon" to ("Υ" to "\\Upsilon"), "Phi" to ("Φ" to "\\Phi"),
            "Psi" to ("Ψ" to "\\Psi"), "Omega" to ("Ω" to "\\Omega"),
        )
    }
}
