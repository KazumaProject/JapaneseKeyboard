package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.repository.DuplicateTextMacroNameException
import com.kazumaproject.markdownhelperkeyboard.repository.TextMacroRepository
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroCompiler
import com.kazumaproject.markdownhelperkeyboard.text_macro.CompiledTextMacroPart
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroContext
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroEditorBlock
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroEditorDocument
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroLimits
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroSyntaxException
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroVariable
import com.kazumaproject.markdownhelperkeyboard.text_macro.database.TextMacro
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TextMacroEditorMode {
    BLOCKS,
    SOURCE,
}

data class TextMacroDraftBlock(
    val editorId: Long,
    val block: TextMacroEditorBlock,
)

data class TextMacroEditorUiState(
    val macroId: Long = 0L,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val name: String = "",
    val reading: String = "",
    val enabled: Boolean = true,
    val mode: TextMacroEditorMode = TextMacroEditorMode.BLOCKS,
    val blocks: List<TextMacroDraftBlock> = emptyList(),
    val source: String = "",
    val insertionIndex: Int = 0,
    val nameError: String? = null,
    val readingError: String? = null,
    val syntaxErrorMessage: String? = null,
    val syntaxErrorPosition: Int? = null,
    val preview: String = "",
    val containsCursor: Boolean = false,
    val dirty: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val loadFailed: Boolean = false,
    val removedBlock: RemovedTextMacroBlock? = null,
)

data class RemovedTextMacroBlock(
    val item: TextMacroDraftBlock,
    val index: Int,
)

private data class TextMacroDraftSnapshot(
    val name: String,
    val reading: String,
    val body: String,
    val enabled: Boolean,
)

@HiltViewModel
class TextMacroEditorViewModel @Inject constructor(
    private val repository: TextMacroRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val macroId: Long = savedStateHandle[ARG_MACRO_ID] ?: 0L
    private val previewTimestampMillis = System.currentTimeMillis()
    private var nextEditorId = 1L
    private var original: TextMacroDraftSnapshot? = null

    private val _uiState = MutableStateFlow(TextMacroEditorUiState(macroId = macroId))
    val uiState: StateFlow<TextMacroEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (macroId == 0L) {
                initializeDraft(TextMacro(name = "", body = ""))
            } else {
                val macro = repository.getById(macroId)
                if (macro == null) {
                    _uiState.update { it.copy(loading = false, loadFailed = true) }
                } else {
                    initializeDraft(macro)
                }
            }
        }
    }

    fun setName(value: String) = updateDraft {
        it.copy(name = value, nameError = null)
    }

    fun setReading(value: String) = updateDraft {
        it.copy(reading = value, readingError = null)
    }

    fun setEnabled(value: Boolean) = updateDraft { it.copy(enabled = value) }

    fun selectInsertionIndex(index: Int) {
        _uiState.update { state ->
            state.copy(insertionIndex = index.coerceIn(0, state.blocks.size))
        }
    }

    fun addTextBlock() {
        val state = _uiState.value
        val index = state.insertionIndex.coerceIn(0, state.blocks.size)
        val changed = state.blocks.toMutableList().apply {
            add(index, newItem(TextMacroEditorBlock.Text("")))
        }
        updateBlocks(changed, insertionIndex = index + 1)
    }

    fun addVariable(variable: TextMacroVariable) {
        val state = _uiState.value
        if (variable == TextMacroVariable.CURSOR && state.containsCursor) return
        val index = state.insertionIndex.coerceIn(0, state.blocks.size)
        val changed = state.blocks.toMutableList().apply {
            add(index, newItem(TextMacroEditorBlock.Token(variable.tokenName)))
        }
        updateBlocks(changed, insertionIndex = index + 1)
    }

    fun updateTextBlock(editorId: Long, value: String) {
        updateBlock(editorId) { TextMacroEditorBlock.Text(value) }
    }

    fun updateTokenPattern(editorId: Long, value: String) {
        updateBlock(editorId) { current ->
            val token = current as? TextMacroEditorBlock.Token ?: return@updateBlock current
            token.copy(argument = value.takeIf(String::isNotBlank))
        }
    }

    fun moveBlock(fromPosition: Int, toPosition: Int): Boolean {
        val state = _uiState.value
        if (fromPosition !in state.blocks.indices || toPosition !in state.blocks.indices) {
            return false
        }
        if (fromPosition == toPosition) return false
        val changed = state.blocks.toMutableList().apply {
            val moved = removeAt(fromPosition)
            add(toPosition, moved)
        }
        updateBlocks(changed, insertionIndex = (toPosition + 1).coerceAtMost(changed.size))
        return true
    }

    fun moveBlockBy(editorId: Long, offset: Int): Boolean {
        val from = _uiState.value.blocks.indexOfFirst { it.editorId == editorId }
        return moveBlock(from, from + offset)
    }

    fun removeBlock(editorId: Long) {
        val state = _uiState.value
        val index = state.blocks.indexOfFirst { it.editorId == editorId }
        if (index < 0) return
        val changed = state.blocks.toMutableList()
        val removed = changed.removeAt(index)
        updateBlocks(
            blocks = changed,
            insertionIndex = index.coerceAtMost(changed.size),
            removedBlock = RemovedTextMacroBlock(removed, index),
        )
    }

    fun undoRemove() {
        val state = _uiState.value
        val removed = state.removedBlock ?: return
        val changed = state.blocks.toMutableList().apply {
            add(removed.index.coerceIn(0, size), removed.item)
        }
        updateBlocks(
            blocks = changed,
            insertionIndex = (removed.index + 1).coerceAtMost(changed.size),
            removedBlock = null,
        )
    }

    fun consumeRemovedBlock() {
        _uiState.update { it.copy(removedBlock = null) }
    }

    fun showSourceMode() {
        val source = blocksToSource(_uiState.value.blocks)
        _uiState.update { state ->
            validatedState(state.copy(mode = TextMacroEditorMode.SOURCE, source = source))
                .withDirtyFlag()
        }
    }

    fun showBlockMode(): Boolean {
        val state = _uiState.value
        return try {
            val parsed = TextMacroEditorDocument.parse(state.source)
            val blocks = parsed.blocks.ifEmpty { listOf(TextMacroEditorBlock.Text("")) }
                .map(::newItem)
            _uiState.value = validatedState(
                state.copy(
                    mode = TextMacroEditorMode.BLOCKS,
                    blocks = blocks,
                    insertionIndex = blocks.size,
                    syntaxErrorMessage = null,
                    syntaxErrorPosition = null,
                )
            ).withDirtyFlag()
            true
        } catch (exception: TextMacroSyntaxException) {
            _uiState.value = state.copy(
                mode = TextMacroEditorMode.SOURCE,
                syntaxErrorMessage = exception.message,
                syntaxErrorPosition = exception.position + 1,
                preview = "",
            ).withDirtyFlag()
            false
        }
    }

    fun setSource(value: String) {
        _uiState.update { state ->
            validatedState(state.copy(source = value)).withDirtyFlag()
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.loading || state.saving) return
        val source = currentSource(state)
        val trimmedName = state.name.trim()
        val trimmedReading = state.reading.trim()
        var nameError: String? = null
        var readingError: String? = null
        if (trimmedName.isEmpty()) {
            nameError = context.getString(R.string.text_macro_name_required)
        } else if (trimmedName.length > TextMacroLimits.NAME) {
            nameError = context.getString(R.string.text_macro_name_too_long, TextMacroLimits.NAME)
        }
        if (trimmedReading.length > TextMacroLimits.READING) {
            readingError = context.getString(
                R.string.text_macro_call_keyword_too_long,
                TextMacroLimits.READING,
            )
        }
        val syntaxException = runCatching { TextMacroCompiler.compile(source) }
            .exceptionOrNull() as? TextMacroSyntaxException
        if (nameError != null || readingError != null || syntaxException != null) {
            _uiState.value = state.copy(
                nameError = nameError,
                readingError = readingError,
                syntaxErrorMessage = syntaxException?.message,
                syntaxErrorPosition = syntaxException?.position?.plus(1),
            )
            return
        }

        _uiState.update { it.copy(saving = true, nameError = null, readingError = null) }
        viewModelScope.launch {
            runCatching {
                repository.save(
                    TextMacro(
                        id = macroId,
                        name = trimmedName,
                        reading = trimmedReading.takeIf(String::isNotEmpty),
                        body = source,
                        enabled = state.enabled,
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(saving = false, saved = true, dirty = false) }
            }.onFailure { exception ->
                if (exception is DuplicateTextMacroNameException) {
                    _uiState.update {
                        it.copy(
                            saving = false,
                            nameError = context.getString(R.string.text_macro_duplicate_name),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            saving = false,
                            syntaxErrorMessage = exception.message
                                ?: context.getString(R.string.text_macro_save_failed),
                        )
                    }
                }
            }
        }
    }

    fun delete() {
        if (macroId == 0L || _uiState.value.saving) return
        viewModelScope.launch {
            runCatching { repository.deleteById(macroId) }
                .onSuccess { _uiState.update { it.copy(deleted = true, dirty = false) } }
                .onFailure { exception ->
                    _uiState.update { it.copy(syntaxErrorMessage = exception.message) }
                }
        }
    }

    fun consumeNavigationEvent() {
        _uiState.update { it.copy(saved = false, deleted = false, loadFailed = false) }
    }

    private fun initializeDraft(macro: TextMacro) {
        val parsed = runCatching { TextMacroEditorDocument.parse(macro.body) }.getOrNull()
        val blocks = parsed?.blocks.orEmpty()
            .ifEmpty { listOf(TextMacroEditorBlock.Text("")) }
            .map(::newItem)
        val base = TextMacroEditorUiState(
            macroId = macro.id,
            loading = false,
            name = macro.name,
            reading = macro.reading.orEmpty(),
            enabled = macro.enabled,
            mode = if (parsed == null) TextMacroEditorMode.SOURCE else TextMacroEditorMode.BLOCKS,
            blocks = blocks,
            source = macro.body,
            insertionIndex = blocks.size,
        )
        original = TextMacroDraftSnapshot(
            name = macro.name,
            reading = macro.reading.orEmpty(),
            body = macro.body,
            enabled = macro.enabled,
        )
        _uiState.value = validatedState(base).copy(dirty = false)
    }

    private fun updateBlock(
        editorId: Long,
        transform: (TextMacroEditorBlock) -> TextMacroEditorBlock,
    ) {
        val state = _uiState.value
        val changed = state.blocks.map { item ->
            if (item.editorId == editorId) item.copy(block = transform(item.block)) else item
        }
        updateBlocks(changed, state.insertionIndex)
    }

    private fun updateBlocks(
        blocks: List<TextMacroDraftBlock>,
        insertionIndex: Int,
        removedBlock: RemovedTextMacroBlock? = _uiState.value.removedBlock,
    ) {
        val source = blocksToSource(blocks)
        _uiState.update { state ->
            validatedState(
                state.copy(
                    blocks = blocks,
                    source = source,
                    insertionIndex = insertionIndex.coerceIn(0, blocks.size),
                    removedBlock = removedBlock,
                )
            ).withDirtyFlag()
        }
    }

    private fun updateDraft(transform: (TextMacroEditorUiState) -> TextMacroEditorUiState) {
        _uiState.update { transform(it).withDirtyFlag() }
    }

    private fun validatedState(state: TextMacroEditorUiState): TextMacroEditorUiState {
        val source = currentSource(state)
        return try {
            val compiled = TextMacroCompiler.compile(source)
            val expanded = compiled.expand(
                TextMacroContext(
                    selection = context.getString(R.string.text_macro_sample_selection),
                    clipboard = context.getString(R.string.text_macro_sample_clipboard),
                    timestampMillis = previewTimestampMillis,
                )
            )
            val preview = buildString {
                append(expanded.text.substring(0, expanded.cursorOffset))
                append(PREVIEW_CURSOR)
                append(expanded.text.substring(expanded.cursorOffset))
            }
            state.copy(
                syntaxErrorMessage = null,
                syntaxErrorPosition = null,
                preview = preview,
                containsCursor = compiled.parts.any { it == CompiledTextMacroPart.Cursor },
            )
        } catch (exception: TextMacroSyntaxException) {
            state.copy(
                syntaxErrorMessage = exception.message,
                syntaxErrorPosition = exception.position + 1,
                preview = "",
                containsCursor = source.contains(TextMacroVariable.CURSOR.source()),
            )
        }
    }

    private fun TextMacroEditorUiState.withDirtyFlag(): TextMacroEditorUiState {
        val original = original ?: return copy(dirty = false)
        return copy(
            dirty = TextMacroDraftSnapshot(
                name = name,
                reading = reading,
                body = currentSource(this),
                enabled = enabled,
            ) != original
        )
    }

    private fun currentSource(state: TextMacroEditorUiState): String =
        if (state.mode == TextMacroEditorMode.SOURCE) state.source else blocksToSource(state.blocks)

    private fun blocksToSource(blocks: List<TextMacroDraftBlock>): String =
        TextMacroEditorDocument(blocks.map(TextMacroDraftBlock::block)).toSource()

    private fun newItem(block: TextMacroEditorBlock): TextMacroDraftBlock =
        TextMacroDraftBlock(editorId = nextEditorId++, block = block)

    companion object {
        const val ARG_MACRO_ID = "macroId"
        private const val PREVIEW_CURSOR = "▌"
    }
}

object TextMacroEditorNavigation {
    fun arguments(macroId: Long): Bundle = Bundle().apply {
        putLong(TextMacroEditorViewModel.ARG_MACRO_ID, macroId)
    }
}
