package com.kazumaproject.markdownhelperkeyboard.text_macro

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TextMacroLimits {
    const val NAME = 80
    const val READING = 64
    const val BODY = 10_000
    const val TOKEN = 128
    const val EXPANDED = 100_000
}

data class TextMacroContext(
    val selection: String? = null,
    val clipboard: String? = null,
    val locale: Locale = Locale.getDefault(),
    val timeZone: TimeZone = TimeZone.getDefault(),
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class ExpandedMacro(
    val text: String,
    val cursorOffset: Int,
)

enum class TextMacroContextRequirement {
    SELECTION,
    CLIPBOARD,
}

/**
 * The complete, intentionally small macro language exposed by both the compiler and editor UI.
 * Keeping this catalog beside the compiler prevents the editor from advertising unsupported
 * tokens or silently omitting newly supported ones.
 */
enum class TextMacroVariable(
    val tokenName: String,
    val acceptsPattern: Boolean = false,
    val requirement: TextMacroContextRequirement? = null,
) {
    DATE(tokenName = "date", acceptsPattern = true),
    TIME(tokenName = "time", acceptsPattern = true),
    SELECTION(tokenName = "selection", requirement = TextMacroContextRequirement.SELECTION),
    CLIPBOARD(tokenName = "clipboard", requirement = TextMacroContextRequirement.CLIPBOARD),
    CURSOR(tokenName = "cursor"),
    NEWLINE(tokenName = "newline"),
    ;

    fun source(argument: String? = null): String = buildString {
        append('{').append(tokenName)
        argument?.takeIf(String::isNotBlank)?.let { append(':').append(it) }
        append('}')
    }

    companion object {
        private val byTokenName = entries.associateBy(TextMacroVariable::tokenName)

        fun fromTokenName(tokenName: String): TextMacroVariable? = byTokenName[tokenName]
    }
}

class TextMacroSyntaxException(
    message: String,
    val position: Int,
) : IllegalArgumentException(message)

sealed interface CompiledTextMacroPart {
    data class Literal(val value: String) : CompiledTextMacroPart
    data class Variable(val name: String, val argument: String?) : CompiledTextMacroPart
    data object Cursor : CompiledTextMacroPart
}

data class CompiledTextMacro internal constructor(
    val parts: List<CompiledTextMacroPart>,
    val requirements: Set<TextMacroContextRequirement>,
) {
    fun expand(context: TextMacroContext): ExpandedMacro {
        // Capture one Date for the whole expansion so every date/time token sees the same instant.
        val now = Date(context.timestampMillis)
        val output = StringBuilder()
        var cursorOffset: Int? = null

        parts.forEach { part ->
            when (part) {
                is CompiledTextMacroPart.Literal -> output.append(part.value)
                CompiledTextMacroPart.Cursor -> cursorOffset = output.length
                is CompiledTextMacroPart.Variable -> output.append(
                    expandVariable(part, context, now)
                )
            }
            if (output.length > TextMacroLimits.EXPANDED) {
                throw TextMacroSyntaxException(
                    "Expanded macro exceeds ${TextMacroLimits.EXPANDED} UTF-16 characters",
                    TextMacroLimits.EXPANDED,
                )
            }
        }
        return ExpandedMacro(
            text = output.toString(),
            cursorOffset = cursorOffset ?: output.length,
        )
    }

    private fun expandVariable(
        part: CompiledTextMacroPart.Variable,
        context: TextMacroContext,
        now: Date,
    ): String = when (part.name) {
        "date" -> if (part.argument == null) {
            DateFormat.getDateInstance(DateFormat.SHORT, context.locale).apply {
                timeZone = context.timeZone
            }.format(now)
        } else {
            formatExplicitDateTime(part.argument, context, now)
        }

        "time" -> if (part.argument == null) {
            DateFormat.getTimeInstance(DateFormat.SHORT, context.locale).apply {
                timeZone = context.timeZone
            }.format(now)
        } else {
            formatExplicitDateTime(part.argument, context, now)
        }

        "selection" -> context.selection?.takeIf { it.isNotEmpty() }
            ?: throw TextMacroSyntaxException("Selected text is unavailable", 0)

        "clipboard" -> context.clipboard?.takeIf { it.isNotEmpty() }
            ?: throw TextMacroSyntaxException("Text clipboard is unavailable", 0)

        "newline" -> "\n"
        else -> error("Compiler produced unsupported variable: ${part.name}")
    }

    private fun formatExplicitDateTime(
        pattern: String,
        context: TextMacroContext,
        now: Date,
    ): String = SimpleDateFormat(pattern, context.locale).apply {
        timeZone = context.timeZone
    }.format(now)
}

object TextMacroCompiler {
    fun compile(body: String): CompiledTextMacro {
        if (body.length > TextMacroLimits.BODY) {
            throw TextMacroSyntaxException(
                "Body exceeds ${TextMacroLimits.BODY} UTF-16 characters",
                TextMacroLimits.BODY,
            )
        }

        val parts = mutableListOf<CompiledTextMacroPart>()
        val literal = StringBuilder()
        val requirements = linkedSetOf<TextMacroContextRequirement>()
        var cursorCount = 0
        var index = 0

        fun flushLiteral() {
            if (literal.isNotEmpty()) {
                parts += CompiledTextMacroPart.Literal(literal.toString())
                literal.clear()
            }
        }

        while (index < body.length) {
            when {
                body.startsWith("{{", index) -> {
                    literal.append('{')
                    index += 2
                }

                body.startsWith("}}", index) -> {
                    literal.append('}')
                    index += 2
                }

                body[index] == '}' -> throw TextMacroSyntaxException(
                    "Unexpected closing brace; escape it as }}",
                    index,
                )

                body[index] == '{' -> {
                    flushLiteral()
                    val close = body.indexOf('}', startIndex = index + 1)
                    if (close < 0) {
                        throw TextMacroSyntaxException("Unclosed macro token", index)
                    }
                    val tokenLength = close - index + 1
                    if (tokenLength > TextMacroLimits.TOKEN) {
                        throw TextMacroSyntaxException(
                            "Token exceeds ${TextMacroLimits.TOKEN} UTF-16 characters",
                            index,
                        )
                    }
                    val token = body.substring(index + 1, close)
                    if (token.isEmpty() || token.contains('{')) {
                        throw TextMacroSyntaxException("Invalid macro token", index)
                    }
                    val colon = token.indexOf(':')
                    val name = if (colon < 0) token else token.substring(0, colon)
                    val argument = if (colon < 0) null else token.substring(colon + 1)
                    val variable = TextMacroVariable.fromTokenName(name)
                    if (variable == null) {
                        throw TextMacroSyntaxException("Unknown macro token: $name", index)
                    }
                    if (argument != null && !variable.acceptsPattern) {
                        throw TextMacroSyntaxException("$name does not accept an argument", index)
                    }
                    if (argument != null && argument.isEmpty()) {
                        throw TextMacroSyntaxException("Empty date/time pattern", index)
                    }
                    if (variable.acceptsPattern) {
                        argument?.let {
                            try {
                                SimpleDateFormat(it, Locale.ROOT)
                            } catch (exception: IllegalArgumentException) {
                                throw TextMacroSyntaxException(
                                    "Invalid date/time pattern: ${exception.message}",
                                    index,
                                )
                            }
                        }
                    }
                    when (variable) {
                        TextMacroVariable.CURSOR -> {
                            cursorCount += 1
                            if (cursorCount > 1) {
                                throw TextMacroSyntaxException("Only one {cursor} is allowed", index)
                            }
                            parts += CompiledTextMacroPart.Cursor
                        }

                        else -> {
                            parts += CompiledTextMacroPart.Variable(name, argument)
                            variable.requirement?.let(requirements::add)
                        }
                    }
                    index = close + 1
                }

                else -> {
                    literal.append(body[index])
                    index += 1
                }
            }
        }
        flushLiteral()
        return CompiledTextMacro(parts = parts, requirements = requirements)
    }
}

object TextMacroValidator {
    fun validateDefinition(name: String, reading: String?, body: String) {
        if (name.isBlank()) throw TextMacroSyntaxException("Name is required", 0)
        if (name.length > TextMacroLimits.NAME) {
            throw TextMacroSyntaxException("Name exceeds ${TextMacroLimits.NAME} characters", 0)
        }
        if ((reading?.length ?: 0) > TextMacroLimits.READING) {
            throw TextMacroSyntaxException("Reading exceeds ${TextMacroLimits.READING} characters", 0)
        }
        TextMacroCompiler.compile(body)
    }
}
