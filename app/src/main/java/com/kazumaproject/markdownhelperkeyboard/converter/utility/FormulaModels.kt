package com.kazumaproject.markdownhelperkeyboard.converter.utility

/**
 * A deliberately small, renderer-independent representation of a mathematical expression.
 *
 * The tree is shared by the shorthand parser, the supported LaTeX parser, the text exporters,
 * and the Android canvas renderer.  It is intentionally not an evaluation tree: a formula is
 * allowed to contain variables and symbols which cannot be calculated.
 */
sealed interface FormulaNode {
    data class Number(val value: String) : FormulaNode

    class Symbol(
        val value: String,
        val texName: String? = null,
    ) : FormulaNode {
        // texName is an output hint, not part of the mathematical structure.
        override fun equals(other: Any?): Boolean = other is Symbol && value == other.value

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = "Symbol(value=$value, texName=$texName)"
    }

    data class Row(val children: List<FormulaNode>) : FormulaNode

    data class Fraction(
        val numerator: FormulaNode,
        val denominator: FormulaNode,
    ) : FormulaNode

    class Script(
        val base: FormulaNode,
        val subscript: FormulaNode? = null,
        val superscript: FormulaNode? = null,
        val subscriptWasParenthesized: Boolean = false,
        val superscriptWasParenthesized: Boolean = false,
    ) : FormulaNode {
        // Parentheses flags only describe how a linear Unicode fallback is written.
        override fun equals(other: Any?): Boolean = other is Script &&
            base == other.base &&
            subscript == other.subscript &&
            superscript == other.superscript

        override fun hashCode(): Int = 31 * (31 * base.hashCode() + (subscript?.hashCode() ?: 0)) +
            (superscript?.hashCode() ?: 0)

        override fun toString(): String = "Script(base=$base, subscript=$subscript, superscript=$superscript)"
    }

    class Radical(
        val radicand: FormulaNode,
        val index: FormulaNode? = null,
        val radicandWasParenthesized: Boolean = false,
    ) : FormulaNode {
        override fun equals(other: Any?): Boolean = other is Radical &&
            radicand == other.radicand && index == other.index

        override fun hashCode(): Int = 31 * radicand.hashCode() + (index?.hashCode() ?: 0)

        override fun toString(): String = "Radical(radicand=$radicand, index=$index)"
    }

    class Delimited(
        val left: String,
        val content: FormulaNode,
        val right: String,
        val latexSized: Boolean = false,
    ) : FormulaNode {
        // \left/\right is a layout spelling; the delimiters and their content are the AST.
        override fun equals(other: Any?): Boolean = other is Delimited &&
            left == other.left && content == other.content && right == other.right

        override fun hashCode(): Int = ((31 * left.hashCode() + content.hashCode()) * 31) + right.hashCode()

        override fun toString(): String = "Delimited(left=$left, content=$content, right=$right)"
    }

    data class LargeOperator(
        val name: String,
        val lower: FormulaNode? = null,
        val upper: FormulaNode? = null,
        val operand: FormulaNode? = null,
    ) : FormulaNode

    data class Accent(
        val kind: AccentKind,
        val base: FormulaNode,
    ) : FormulaNode
}

enum class AccentKind(
    val unicodeMark: String?,
    val texCommand: String,
) {
    HAT("̂", "hat"),
    BAR("̄", "bar"),
    OVERLINE("̄", "overline"),
    DOT("̇", "dot"),
    DDOT("̈", "ddot"),
    TILDE("̃", "tilde"),
    VEC("⃗", "vec"),
    UNDERLINE("̲", "underline"),
    BOLD(null, "mathbf"),
}

enum class FormulaCandidateType {
    UNICODE,
    TEX,
}

/** The complete, reusable result of parsing one formula source. */
data class ParsedFormula(
    val ast: FormulaNode,
    val unicodeText: String,
    val normalizedTex: String,
    val sourceText: String,
    val sourceWasNormalizedTex: Boolean,
) {
    fun presentation(type: FormulaCandidateType): FormulaCandidatePresentation =
        FormulaCandidatePresentation(
            ast = ast,
            unicodeText = unicodeText,
            normalizedTex = normalizedTex,
            type = type,
        )
}

/** Data carried by a Candidate independently of the string sent to InputConnection. */
data class FormulaCandidatePresentation(
    val ast: FormulaNode,
    val unicodeText: String,
    val normalizedTex: String,
    val type: FormulaCandidateType,
) {
    val commitText: String
        get() = if (type == FormulaCandidateType.UNICODE) unicodeText else normalizedTex

    val fallbackText: String
        get() = commitText
}

internal fun formulaRow(children: Iterable<FormulaNode>): FormulaNode {
    val flattened = buildList {
        children.forEach { child ->
            when {
                child is FormulaNode.Row -> addAll(child.children)
                child is FormulaNode.Symbol && child.value.isEmpty() -> Unit
                else -> add(child)
            }
        }
    }
    return when (flattened.size) {
        0 -> FormulaNode.Symbol("")
        1 -> flattened.single()
        else -> FormulaNode.Row(flattened)
    }
}

/** Creates a symbol with the canonical TeX spelling for direct Unicode input when available. */
internal fun formulaSymbol(value: String, texName: String? = null): FormulaNode.Symbol =
    FormulaNode.Symbol(value, texName ?: FormulaFormatter.texNameForUnicode(value))

internal fun FormulaNode.asRowChildren(): List<FormulaNode> = when (this) {
    is FormulaNode.Row -> children
    else -> listOf(this)
}

internal fun shouldParenthesizeScriptForUnicode(node: FormulaNode): Boolean = when (node) {
    is FormulaNode.Row -> node.children.size > 1
    is FormulaNode.Fraction,
    is FormulaNode.Radical,
    is FormulaNode.LargeOperator,
    is FormulaNode.Accent -> true
    is FormulaNode.Delimited,
    is FormulaNode.Number,
    is FormulaNode.Symbol,
    is FormulaNode.Script -> false
}

internal fun FormulaNode.isEmptyFormula(): Boolean =
    this is FormulaNode.Symbol && value.isEmpty()

object FormulaFormatter {
    private val superscriptCharacters = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '−' to '⁻', '=' to '⁼',
        '(' to '⁽', ')' to '⁾', 'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ',
        'd' to 'ᵈ', 'e' to 'ᵉ', 'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ',
        'i' to 'ⁱ', 'j' to 'ʲ', 'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ',
        'n' to 'ⁿ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ',
        't' to 'ᵗ', 'u' to 'ᵘ', 'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ',
        'y' to 'ʸ', 'z' to 'ᶻ',
    )
    private val subscriptCharacters = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '−' to '₋', '=' to '₌',
        '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ',
        'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ',
        'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ',
        't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ',
    )

    private val vulgarFractions = mapOf(
        "1/2" to "½",
        "1/3" to "⅓",
        "2/3" to "⅔",
        "1/4" to "¼",
        "3/4" to "¾",
        "1/5" to "⅕",
        "2/5" to "⅖",
        "3/5" to "⅗",
        "4/5" to "⅘",
        "1/6" to "⅙",
        "5/6" to "⅚",
        "1/8" to "⅛",
        "3/8" to "⅜",
        "5/8" to "⅝",
        "7/8" to "⅞",
    )

    private val unicodeToTex = mapOf(
        "Π" to "\\Pi", "∞" to "\\infty",
        "∑" to "\\sum", "∏" to "\\prod", "∫" to "\\int", "∬" to "\\iint",
        "∭" to "\\iiint", "∮" to "\\oint", "√" to "\\sqrt",
        "≤" to "\\leq", "≥" to "\\geq", "≠" to "\\neq", "≈" to "\\approx",
        "≃" to "\\simeq", "≡" to "\\equiv", "≢" to "\\not\\equiv",
        "≅" to "\\cong", "≍" to "\\asymp", "≰" to "\\nleq", "≱" to "\\ngeq",
        "∼" to "\\sim", "∝" to "\\propto", "→" to "\\to",
        "⇒" to "\\Rightarrow", "↔" to "\\leftrightarrow", "⇔" to "\\Leftrightarrow",
        "←" to "\\leftarrow", "↦" to "\\mapsto", "⟶" to "\\longrightarrow",
        "⟹" to "\\Longrightarrow", "⟷" to "\\longleftrightarrow",
        "⟺" to "\\Longleftrightarrow", "⟵" to "\\longleftarrow", "⟼" to "\\longmapsto",
        "∈" to "\\in",
        "∉" to "\\notin", "∋" to "\\ni", "⊂" to "\\subset", "⊆" to "\\subseteq",
        "⊈" to "\\nsubseteq", "⊃" to "\\supset", "⊇" to "\\supseteq",
        "⊉" to "\\nsupseteq", "⊄" to "\\nsubset", "⊅" to "\\nsupset",
        "∪" to "\\cup", "∩" to "\\cap",
        "∀" to "\\forall", "∃" to "\\exists", "∧" to "\\land", "∨" to "\\lor",
        "¬" to "\\neg", "∅" to "\\emptyset", "ℕ" to "\\mathbb{N}",
        "ℤ" to "\\mathbb{Z}", "ℚ" to "\\mathbb{Q}", "ℝ" to "\\mathbb{R}",
        "ℂ" to "\\mathbb{C}", "±" to "\\pm", "∓" to "\\mp", "×" to "\\times",
        "⋅" to "\\cdot", "÷" to "\\div", "∂" to "\\partial", "∇" to "\\nabla",
        "‖" to "\\Vert", "∥" to "\\parallel", "∣" to "\\mid",
        "mod" to "\\bmod",
        "α" to "\\alpha", "β" to "\\beta", "γ" to "\\gamma", "δ" to "\\delta",
        "ϵ" to "\\epsilon", "ε" to "\\varepsilon", "ζ" to "\\zeta", "η" to "\\eta",
        "θ" to "\\theta", "ϑ" to "\\vartheta", "ι" to "\\iota", "κ" to "\\kappa",
        "λ" to "\\lambda", "μ" to "\\mu", "ν" to "\\nu", "ξ" to "\\xi",
        "ο" to "\\omicron", "π" to "\\pi", "ϖ" to "\\varpi", "ρ" to "\\rho",
        "ϱ" to "\\varrho", "σ" to "\\sigma", "ς" to "\\varsigma", "τ" to "\\tau",
        "υ" to "\\upsilon", "ϕ" to "\\phi", "φ" to "\\varphi", "χ" to "\\chi",
        "ψ" to "\\psi", "ω" to "\\omega", "Γ" to "\\Gamma", "Δ" to "\\Delta",
        "Θ" to "\\Theta", "Λ" to "\\Lambda", "Ξ" to "\\Xi",
        "Σ" to "\\Sigma", "Υ" to "\\Upsilon", "Φ" to "\\Phi", "Ψ" to "\\Psi",
        "Ω" to "\\Omega",
    )

    fun unicode(node: FormulaNode): String = when (node) {
        is FormulaNode.Number -> node.value
        is FormulaNode.Symbol -> node.value
        is FormulaNode.Row -> unicodeRow(node.children)
        is FormulaNode.Fraction -> unicodeFraction(node)
        is FormulaNode.Script -> buildString {
            append(unicode(node.base))
            node.subscript?.let {
                append(scriptText(it, subscriptCharacters, node.subscriptWasParenthesized))
            }
            node.superscript?.let {
                append(scriptText(it, superscriptCharacters, node.superscriptWasParenthesized))
            }
        }
        is FormulaNode.Radical -> buildString {
            append("√")
            if (node.index != null) {
                append(scriptText(node.index, superscriptCharacters, false))
                append("⟨")
                append(unicode(node.radicand))
                append("⟩")
            } else if (node.radicandWasParenthesized) {
                append('(')
                append(unicode(node.radicand))
                append(')')
            } else {
                append(unicode(node.radicand))
            }
        }
        is FormulaNode.Delimited -> node.left + unicode(node.content) + node.right
        is FormulaNode.LargeOperator -> buildString {
            append(largeOperatorDisplay(node.name))
            node.lower?.let { append(scriptText(it, subscriptCharacters, false)) }
            node.upper?.let { append(scriptText(it, superscriptCharacters, false)) }
            node.operand?.let {
                append(' ')
                append(unicode(it))
            }
        }
        is FormulaNode.Accent -> unicodeAccent(node)
    }

    fun tex(node: FormulaNode): String = when (node) {
        is FormulaNode.Number -> node.value
        is FormulaNode.Symbol -> node.texName ?: unicodeToTex[node.value] ?: texSymbol(node.value)
        is FormulaNode.Row -> texRow(node.children)
        is FormulaNode.Fraction -> "\\frac{" + tex(node.numerator) + "}{" +
            tex(node.denominator) + "}"
        is FormulaNode.Script -> buildString {
            append(tex(node.base))
            node.subscript?.let { append("_{${tex(it)}}") }
            node.superscript?.let { append("^{${tex(it)}}") }
        }
        is FormulaNode.Radical -> {
            if (node.index == null) {
                "\\sqrt{" + tex(node.radicand) + "}"
            } else {
                "\\sqrt[" + tex(node.index) + "]{" + tex(node.radicand) + "}"
            }
        }
        is FormulaNode.Delimited -> if (node.latexSized) {
            "\\left${texDelimiter(node.left, followedByContent = true)}${tex(node.content)}" +
                "\\right${texDelimiter(node.right)}"
        } else {
            texDelimiter(node.left, followedByContent = true) +
                tex(node.content) + texDelimiter(node.right)
        }
        is FormulaNode.LargeOperator -> buildString {
            append("\\")
            append(node.name)
            node.lower?.let { append("_{${tex(it)}}") }
            node.upper?.let { append("^{${tex(it)}}") }
            node.operand?.let {
                if (node.lower == null && node.upper == null) append(' ')
                append(tex(it))
            }
        }
        is FormulaNode.Accent -> "\\${node.kind.texCommand}{${tex(node.base)}}"
    }

    fun render(node: FormulaNode): Pair<String, String> = unicode(node) to tex(node)

    internal fun texNameForUnicode(value: String): String? = unicodeToTex[value]

    private fun unicodeRow(children: List<FormulaNode>): String = buildString {
            children.forEachIndexed { index, child ->
            val previous = children.getOrNull(index - 1)
            if (index > 0 &&
                (child is FormulaNode.LargeOperator ||
                    (child !is FormulaNode.Delimited && previous?.needsUnicodeOperandGap() == true))
            ) {
                append(' ')
            }
            append(unicode(child))
        }
    }

    private fun FormulaNode.needsUnicodeOperandGap(): Boolean = when (this) {
        is FormulaNode.LargeOperator -> true
        is FormulaNode.Symbol -> value in setOf(
            "sin", "cos", "tan", "arcsin", "arccos", "arctan",
            "sinh", "cosh", "tanh", "sec", "csc", "cot",
            "arcsinh", "arccosh", "arctanh", "ln", "log", "exp",
            "lim", "min", "max", "sup", "inf", "mod",
        )
        is FormulaNode.Script -> this.base.needsUnicodeOperandGap()
        else -> false
    }

    private fun texRow(children: List<FormulaNode>): String = buildString {
        var previous: String? = null
        children.forEach { child ->
            val current = tex(child)
            if (previous?.endsWithBareControlWord() == true && current.firstOrNull()?.isLetter() == true) {
                append(' ')
            }
            append(current)
            previous = current
        }
    }

    private fun String.endsWithBareControlWord(): Boolean {
        val slash = lastIndexOf('\\')
        return slash >= 0 && substring(slash + 1).isNotEmpty() &&
            substring(slash + 1).all(Char::isLetter)
    }

    private fun unicodeFraction(node: FormulaNode.Fraction): String {
        val numerator = unicode(node.numerator)
        val denominator = unicode(node.denominator)
        vulgarFractions["$numerator/$denominator"]?.let { return it }

        if (numerator.all(Char::isDigit) && denominator.all(Char::isDigit)) {
            return toSuperscript(numerator) + "⁄" + toSubscript(denominator)
        }

        val renderedNumerator = if (needsFractionParentheses(node.numerator)) {
            "($numerator)"
        } else {
            numerator
        }
        val renderedDenominator = if (needsFractionParentheses(node.denominator)) {
            "($denominator)"
        } else {
            denominator
        }
        return "$renderedNumerator⁄$renderedDenominator"
    }

    private fun needsFractionParentheses(node: FormulaNode): Boolean = when (node) {
        is FormulaNode.Row -> node.children.size > 1
        is FormulaNode.Delimited -> false
        is FormulaNode.Number,
        is FormulaNode.Symbol,
        is FormulaNode.Script,
        is FormulaNode.Radical,
        is FormulaNode.LargeOperator,
        is FormulaNode.Accent,
        is FormulaNode.Fraction -> true
    }

    private fun scriptText(
        node: FormulaNode,
        mapping: Map<Char, Char>,
        wasParenthesized: Boolean,
    ): String {
        val source = unicode(node)
        val mapped = buildString(source.length) {
            source.forEach { char -> append(mapping[char] ?: char) }
        }
        return if (wasParenthesized) {
            val left = mapping['('] ?: '('
            val right = mapping[')'] ?: ')'
            "$left$mapped$right"
        } else {
            mapped
        }
    }

    private fun toSuperscript(source: String): String = source.map { superscriptCharacters[it] ?: it }.joinToString("")

    private fun toSubscript(source: String): String = source.map { subscriptCharacters[it] ?: it }.joinToString("")

    internal fun largeOperatorDisplay(name: String): String = when (name) {
        "sum" -> "∑"
        "prod" -> "∏"
        "int" -> "∫"
        "iint" -> "∬"
        "iiint" -> "∭"
        "oint" -> "∮"
        "lim" -> "lim"
        "min" -> "min"
        "max" -> "max"
        "sup" -> "sup"
        "inf" -> "inf"
        else -> name
    }

    private fun unicodeAccent(node: FormulaNode.Accent): String {
        val base = unicode(node.base)
        return when (node.kind) {
            AccentKind.BOLD -> base
            else -> base + node.kind.unicodeMark.orEmpty()
        }
    }

    private fun texDelimiter(delimiter: String, followedByContent: Boolean = false): String {
        val value = when (delimiter) {
        "{" -> "\\{"
        "}" -> "\\}"
        "⟨" -> "\\langle"
        "⟩" -> "\\rangle"
        "⌊" -> "\\lfloor"
        "⌋" -> "\\rfloor"
        "⌈" -> "\\lceil"
        "⌉" -> "\\rceil"
        "|" -> "|"
        "‖" -> "\\Vert"
        "∥" -> "\\parallel"
        else -> delimiter
        }
        return if (followedByContent && value.lastOrNull()?.isLetter() == true) {
            "$value "
        } else {
            value
        }
    }

    private fun texSymbol(value: String): String = when (value) {
        "#" -> "\\#"
        "%" -> "\\%"
        "&" -> "\\&"
        "_" -> "\\_"
        "{" -> "\\{"
        "}" -> "\\}"
        else -> value
    }
}
