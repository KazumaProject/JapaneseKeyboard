package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry

/** Native previews load persisted layouts and fonts after attachment, just like settings. */
internal class NativeGeometryPreviewHarness(activity: Activity, create: () -> GeometryPreview) : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val view: GeometryPreview
    init {
        var created: GeometryPreview? = null
        instrumentation.runOnMainSync {
            created = create()
            (activity.window.decorView as ViewGroup).addView(created, ViewGroup.LayoutParams(-1, -2))
        }
        view = requireNotNull(created)
    }
    fun update(block: (GeometryPreview) -> Unit) {
        instrumentation.runOnMainSync { block(view) }
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            var loaded = false
            instrumentation.runOnMainSync {
                val width = view.resources.displayMetrics.widthPixels
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                view.layout(0, 0, width, view.measuredHeight)
                loaded = runCatching { view.snapshotForTesting().keys.isNotEmpty() }.getOrDefault(false)
            }
            if (loaded) return
            SystemClock.sleep(50)
        }
        error("Native geometry preview did not load")
    }
    fun inspect(block: (GeometryPreview) -> Unit) = instrumentation.runOnMainSync { block(view) }
    override fun close() = instrumentation.runOnMainSync { (view.parent as? ViewGroup)?.removeView(view) }
}
