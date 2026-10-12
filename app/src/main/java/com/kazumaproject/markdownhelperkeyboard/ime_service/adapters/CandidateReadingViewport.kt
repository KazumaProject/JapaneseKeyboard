package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import java.util.IdentityHashMap

/** Owns the temporary viewport used by readings above horizontally scrolling candidate rows. */
internal object CandidateReadingViewport {
    private val viewports = IdentityHashMap<RecyclerView, Viewport>()
    private data class ClipLease(val original: Boolean, var owners: Int = 1)
    private val clipLeases = IdentityHashMap<ViewGroup, ClipLease>()

    private fun acquire(view: ViewGroup) {
        val lease = clipLeases[view]
        if (lease == null) clipLeases[view] = ClipLease(view.clipChildren) else lease.owners++
        view.clipChildren = false
    }

    private fun relinquish(view: ViewGroup) {
        val lease = clipLeases[view] ?: return
        if (--lease.owners == 0) {
            view.clipChildren = lease.original
            clipLeases.remove(view)
        }
    }

    fun headroom(candidate: CandidateReadingLayout): Int {
        val recycler = candidate.parent as? RecyclerView ?: return 0
        if (!isHorizontal(recycler)) {
            release(recycler)
            return 0
        }
        val viewport = viewports.getOrPut(recycler) { Viewport(recycler) }
        viewport.update()
        return viewport.headroom(candidate)
    }

    fun refresh(recycler: RecyclerView) {
        viewports[recycler]?.update()
    }

    fun release(recycler: RecyclerView) {
        viewports.remove(recycler)?.restore()
    }

    private fun isHorizontal(recycler: RecyclerView): Boolean =
        (recycler.layoutManager as? LinearLayoutManager)?.orientation == RecyclerView.HORIZONTAL

    private fun hasReading(view: View): Boolean =
        view is CandidateReadingLayout && view.visibility == View.VISIBLE &&
            view.findViewById<View>(R.id.suggestion_item_yomi_text_view)?.visibility == View.VISIBLE &&
            view.findViewById<View>(R.id.suggestion_item_text_view)?.visibility == View.VISIBLE

    private class Viewport(private val recycler: RecyclerView) {
        private val originalClipBounds = recycler.clipBounds?.let(::Rect)
        private val unclippedParents = mutableSetOf<ViewGroup>()
        private val observer = recycler.viewTreeObserver
        private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { update() }
        private val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = release(recycler)
        }
        private val childListener = object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) = Unit
            override fun onChildViewDetachedFromWindow(view: View) = update(excluded = view)
        }
        private var restored = false
        private var recyclerTop = 0
        private var framePaddingTop = 0

        init {
            observer.addOnGlobalLayoutListener(globalLayoutListener)
            recycler.addOnAttachStateChangeListener(attachListener)
            recycler.addOnChildAttachStateChangeListener(childListener)
        }

        fun update(excluded: View? = null) {
            if (restored) return
            if (!isHorizontal(recycler) || (0 until recycler.childCount).none {
                    recycler.getChildAt(it).let { child -> child !== excluded && hasReading(child) }
                }) {
                release(recycler)
                return
            }
            var frame = recycler.parent as? ViewGroup ?: run {
                release(recycler)
                return
            }
            val parents = mutableListOf<ViewGroup>(recycler)
            recyclerTop = recycler.top
            while (frame.layoutParams?.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                val outer = frame.parent as? ViewGroup ?: break
                parents += frame
                recyclerTop += frame.top
                frame = outer
            }
            parents += frame
            unclippedParents.toList().filter { it !in parents }.forEach {
                unclippedParents.remove(it)
                relinquish(it)
            }
            parents.forEach {
                if (unclippedParents.add(it)) acquire(it)
            }
            framePaddingTop = frame.paddingTop
            // Grow only vertically into the existing frame; retain the horizontal scroll boundary.
            recycler.clipBounds = Rect(0, frame.paddingTop - recyclerTop,
                recycler.width, frame.height - frame.paddingBottom - recyclerTop)
        }

        fun headroom(candidate: View): Int =
            (candidate.top + recyclerTop - framePaddingTop).coerceAtLeast(0)

        fun restore() {
            if (restored) return
            restored = true
            recycler.clipBounds = originalClipBounds
            unclippedParents.forEach(::relinquish)
            unclippedParents.clear()
            val currentObserver = if (observer.isAlive) observer else recycler.viewTreeObserver
            if (currentObserver.isAlive) currentObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            recycler.removeOnAttachStateChangeListener(attachListener)
            recycler.removeOnChildAttachStateChangeListener(childListener)
        }
    }
}
