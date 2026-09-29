package com.kazumaproject.core.ui.font

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import java.util.WeakHashMap

/** A process-local rendering snapshot. A null typeface means each target's native style. */
data class KeyboardFontSnapshot(
    val typeface: Typeface? = null,
    val revision: Long = 0L,
)

/** Implemented by renderers that own Canvas paints or other non-TextView text paths. */
interface KeyboardFontAware {
    fun setKeyboardFont(snapshot: KeyboardFontSnapshot)
}

/** Applies a shared family while preserving each target's own baseline typeface and style. */
object KeyboardFontApplicator {
    @Volatile
    var processSnapshot: KeyboardFontSnapshot = KeyboardFontSnapshot()
        private set

    private data class AppliedState<T>(
        var baseline: T,
        var applied: T?,
        var revision: Long,
    )

    private val textStates = WeakHashMap<TextView, AppliedState<Typeface?>>()
    private val paintStates = WeakHashMap<Paint, AppliedState<Typeface?>>()
    private val renderers = WeakHashMap<KeyboardFontAware, Unit>()

    fun updateProcessSnapshot(snapshot: KeyboardFontSnapshot) {
        processSnapshot = snapshot
        val tracked = synchronized(renderers) { renderers.keys.toList() }
        Handler(Looper.getMainLooper()).post {
            tracked.forEach { renderer -> runCatching { renderer.setKeyboardFont(snapshot) } }
        }
    }

    fun track(renderer: KeyboardFontAware) {
        synchronized(renderers) { renderers[renderer] = Unit }
    }

    @Synchronized
    fun apply(textView: TextView, snapshot: KeyboardFontSnapshot) {
        val state = textStates[textView]
        val current = textView.typeface
        if (state == null) {
            val baseline = current
            if (snapshot.typeface == null) return
            val applied = Typeface.create(snapshot.typeface, baseline?.style ?: Typeface.NORMAL)
            textStates[textView] = AppliedState(baseline, applied, snapshot.revision)
            textView.typeface = applied
            textView.requestLayout()
            textView.invalidate()
            return
        }

        if (state.revision == snapshot.revision &&
            if (snapshot.typeface == null) state.applied == null && current === state.baseline
            else current === state.applied
        ) return
        if (state.applied != null && current !== state.applied) {
            // A theme or a mode-specific style changed the native typeface after our last update.
            state.baseline = current
        } else if (state.applied == null && current !== state.baseline) {
            // Keep tracking theme/style changes made while standard typography is active.
            state.baseline = current
        }
        val custom = snapshot.typeface
        if (custom == null) {
            if (state.applied != null) textView.typeface = state.baseline
            state.applied = null
        } else {
            val applied = Typeface.create(custom, state.baseline?.style ?: Typeface.NORMAL)
            state.applied = applied
            textView.typeface = applied
        }
        state.revision = snapshot.revision
        textView.requestLayout()
        textView.invalidate()
    }

    @Synchronized
    fun apply(paint: Paint, snapshot: KeyboardFontSnapshot) {
        val state = paintStates[paint]
        val current = paint.typeface
        if (state == null) {
            val baseline = current
            if (snapshot.typeface == null) return
            val applied = Typeface.create(snapshot.typeface, baseline?.style ?: Typeface.NORMAL)
            paintStates[paint] = AppliedState(baseline, applied, snapshot.revision)
            paint.typeface = applied
            return
        }

        if (state.revision == snapshot.revision &&
            if (snapshot.typeface == null) state.applied == null && current === state.baseline
            else current === state.applied
        ) return
        if (state.applied != null && current !== state.applied) state.baseline = current
        else if (state.applied == null && current !== state.baseline) state.baseline = current
        val custom = snapshot.typeface
        if (custom == null) {
            if (state.applied != null) paint.typeface = state.baseline
            state.applied = null
        } else {
            val applied = Typeface.create(custom, state.baseline?.style ?: Typeface.NORMAL)
            state.applied = applied
            paint.typeface = applied
        }
        state.revision = snapshot.revision
    }

    /** Use only on an explicitly owned keyboard subtree whose normal-text roles are known. */
    fun applyToTextViews(root: View, snapshot: KeyboardFontSnapshot, include: (TextView) -> Boolean) {
        if (root is TextView) {
            if (include(root)) apply(root, snapshot)
            return
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                applyToTextViews(root.getChildAt(index), snapshot, include)
            }
        }
    }

    /** Applies text to a named UI subtree and delegates custom Canvas renderers to their owner. */
    fun applyToKeyboardViews(root: View, snapshot: KeyboardFontSnapshot) {
        when (root) {
            is KeyboardFontAware -> root.setKeyboardFont(snapshot)
            is TextView -> apply(root, snapshot)
            is ImageView -> (root.drawable as? KeyboardFontAware)?.setKeyboardFont(snapshot)
            is ViewGroup -> {
                for (index in 0 until root.childCount) {
                    applyToKeyboardViews(root.getChildAt(index), snapshot)
                }
            }
        }
        // A KeyboardFontAware container owns its text handling, but may contain drawable-backed
        // glyphs. Update those separately without broadening the text traversal.
        if (root is KeyboardFontAware && root is ViewGroup) {
            applyToImageDrawables(root, snapshot)
        }
    }

    private fun applyToImageDrawables(root: View, snapshot: KeyboardFontSnapshot) {
        if (root is ImageView) {
            (root.drawable as? KeyboardFontAware)?.setKeyboardFont(snapshot)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                applyToImageDrawables(root.getChildAt(index), snapshot)
            }
        }
    }
}
