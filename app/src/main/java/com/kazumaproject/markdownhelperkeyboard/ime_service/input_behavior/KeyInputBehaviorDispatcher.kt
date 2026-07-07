package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import android.view.inputmethod.InputConnection

class KeyInputBehaviorDispatcher(
    private val directCommitHandler: DirectCommitHandler = DirectCommitHandler(),
) {
    fun dispatchText(
        behavior: ResolvedInputBehavior,
        inputConnection: InputConnection?,
        text: String,
        composingPipeline: () -> Unit,
    ): Boolean {
        return dispatchDirect(
            behavior = behavior,
            directAction = { directCommitHandler.commitText(inputConnection, text) },
            composingPipeline = composingPipeline,
        )
    }

    fun dispatchEnter(
        behavior: ResolvedInputBehavior,
        inputConnection: InputConnection?,
        composingPipeline: () -> Unit,
    ): Boolean {
        return dispatchDirect(
            behavior = behavior,
            directAction = { directCommitHandler.sendEnter(inputConnection) },
            composingPipeline = composingPipeline,
        )
    }

    fun dispatchBackspace(
        behavior: ResolvedInputBehavior,
        inputConnection: InputConnection?,
        composingPipeline: () -> Unit,
    ): Boolean {
        return dispatchDirect(
            behavior = behavior,
            directAction = { directCommitHandler.sendBackspace(inputConnection) },
            composingPipeline = composingPipeline,
        )
    }

    fun dispatchTab(
        behavior: ResolvedInputBehavior,
        inputConnection: InputConnection?,
        composingPipeline: () -> Unit,
    ): Boolean {
        return dispatchDirect(
            behavior = behavior,
            directAction = { directCommitHandler.sendTab(inputConnection) },
            composingPipeline = composingPipeline,
        )
    }

    private fun dispatchDirect(
        behavior: ResolvedInputBehavior,
        directAction: () -> Unit,
        composingPipeline: () -> Unit,
    ): Boolean {
        if (behavior == ResolvedInputBehavior.DIRECT_COMMIT) {
            directAction()
            return true
        }
        composingPipeline()
        return false
    }
}
