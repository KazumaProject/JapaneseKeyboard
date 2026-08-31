package com.kazumaproject.markdownhelperkeyboard.text_macro

sealed interface TextMacroEditorBlock {
    data class Text(val value: String) : TextMacroEditorBlock
    data class Token(val name: String, val argument: String? = null) : TextMacroEditorBlock {
        val source: String
            get() = buildString {
                append('{').append(name)
                argument?.let { append(':').append(it) }
                append('}')
            }
    }
}

/** Editable, lossless-at-runtime representation used by the visual macro composer. */
data class TextMacroEditorDocument(
    val blocks: List<TextMacroEditorBlock>,
) {
    fun move(from: Int, to: Int): TextMacroEditorDocument {
        if (from !in blocks.indices || to !in blocks.indices || from == to) return this
        val changed = blocks.toMutableList()
        val block = changed.removeAt(from)
        changed.add(to, block)
        return copy(blocks = changed)
    }

    fun replace(index: Int, block: TextMacroEditorBlock): TextMacroEditorDocument =
        copy(blocks = blocks.toMutableList().also { it[index] = block })

    fun remove(index: Int): TextMacroEditorDocument =
        copy(blocks = blocks.toMutableList().also { it.removeAt(index) })

    fun add(block: TextMacroEditorBlock, index: Int = blocks.size): TextMacroEditorDocument =
        copy(blocks = blocks.toMutableList().also { it.add(index.coerceIn(0, it.size), block) })

    fun toSource(): String = blocks.joinToString(separator = "") { block ->
        when (block) {
            is TextMacroEditorBlock.Text -> block.value
                .replace("{", "{{")
                .replace("}", "}}")
            is TextMacroEditorBlock.Token -> block.source
        }
    }

    companion object {
        fun parse(source: String): TextMacroEditorDocument {
            val compiled = TextMacroCompiler.compile(source)
            return TextMacroEditorDocument(
                compiled.parts.map { part ->
                    when (part) {
                        is CompiledTextMacroPart.Literal -> TextMacroEditorBlock.Text(part.value)
                        is CompiledTextMacroPart.Variable -> TextMacroEditorBlock.Token(
                            name = part.name,
                            argument = part.argument,
                        )
                        CompiledTextMacroPart.Cursor -> TextMacroEditorBlock.Token("cursor")
                    }
                }
            )
        }
    }
}

