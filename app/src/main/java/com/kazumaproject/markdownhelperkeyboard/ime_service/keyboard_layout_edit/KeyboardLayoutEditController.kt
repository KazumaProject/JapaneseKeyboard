package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit

import android.content.Context
import android.graphics.Rect
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeyboardLayoutEditController(
    private val context: Context,
) {
    private val _state = MutableStateFlow<KeyboardLayoutEditState>(KeyboardLayoutEditState.Disabled)
    val state: StateFlow<KeyboardLayoutEditState> = _state.asStateFlow()

    private var overlayView: KeyboardLayoutEditOverlayView? = null
    private var activeSurface: KeyboardLayoutEditSurfaceAdapter? = null

    fun start(
        state: KeyboardLayoutEditState.Enabled,
        surfaceAdapter: KeyboardLayoutEditSurfaceAdapter,
        callbacks: KeyboardLayoutEditOverlayView.Callbacks,
    ) {
        stop()
        activeSurface = surfaceAdapter
        _state.value = state
        val overlay = KeyboardLayoutEditOverlayView(context).apply {
            configure(
                mode = when (state.surface) {
                    KeyboardLayoutEditSurface.Normal -> KeyboardLayoutEditOverlayView.Mode.Normal
                    KeyboardLayoutEditSurface.Floating -> KeyboardLayoutEditOverlayView.Mode.Floating
                },
                initialValues = state.values,
                boundsProvider = surfaceAdapter::currentBoundsInParent,
                callbacks = callbacks,
            )
        }
        overlayView = overlay
        surfaceAdapter.setEditing(true)
        surfaceAdapter.parent.addView(overlay, KeyboardLayoutEditOverlayView.defaultLayoutParams())
    }

    fun stop() {
        overlayView?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        overlayView = null
        activeSurface?.setEditing(false)
        activeSurface = null
        _state.value = KeyboardLayoutEditState.Disabled
    }

    fun requestOverlayLayout() {
        overlayView?.requestLayout()
    }

    val isActive: Boolean
        get() = _state.value is KeyboardLayoutEditState.Enabled
}

interface KeyboardLayoutEditSurfaceAdapter {
    val parent: FrameLayout
    fun currentBoundsInParent(): Rect
    fun setEditing(isEditing: Boolean)
}
