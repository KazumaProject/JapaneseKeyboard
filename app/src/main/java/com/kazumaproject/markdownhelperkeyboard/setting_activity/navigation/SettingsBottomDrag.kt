package com.kazumaproject.markdownhelperkeyboard.setting_activity.navigation

import android.content.ClipData
import android.view.DragEvent
import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kazumaproject.markdownhelperkeyboard.R
import kotlin.math.abs

/** Keep the menu intact during a drag, animating slots until the order is committed on drop. */
internal class SettingsBottomDrag(
    private val bar: BottomNavigationView,
    private val store: SettingsNavigationStore,
    private val refresh: () -> Unit,
) {
    private data class Dragged(val key: String, val owner: BottomNavigationView)
    var isDragging = false; private set
    private var original = emptyList<SettingsNavigationItem>()
    private var pending = emptyList<SettingsNavigationItem>()
    private val centers = mutableMapOf<String, Float>()
    init {
        bar.setOnDragListener { _, event ->
            val dragged = event.localState as? Dragged
            if (dragged?.owner !== bar) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> { isDragging = true; original = store.bottom(); pending = original; captureCenters(); true }
                DragEvent.ACTION_DRAG_LOCATION -> { preview(dragged.key, event.x); true }
                DragEvent.ACTION_DROP -> {
                    preview(dragged.key, event.x)
                    val config = store.read(); val visible = original.map { it.key }.toSet(); val order = pending.map { it.key }.iterator()
                    store.save(config.copy(bottom = config.bottom.map { if (it in visible) order.next() else it }))
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    isDragging = false
                    original.forEach { item -> bar.findViewById<View>(item.destination)?.apply { animate().cancel(); translationX = 0f; alpha = 1f } }
                    bar.post { refresh() }; true
                }
                else -> true
            }
        }
    }
    private fun captureCenters() {
        centers.clear()
        original.forEach { item -> bar.findViewById<View>(item.destination)?.let { view ->
            val rect = android.graphics.Rect(); view.getDrawingRect(rect); bar.offsetDescendantRectToMyCoords(view, rect)
            centers[item.key] = rect.exactCenterX()
        } }
    }
    private fun preview(key: String, x: Float) {
        if (original.size < 2 || centers.size != original.size) return
        val target = original.indices.minByOrNull { abs(centers.getValue(original[it].key) - x) } ?: return
        val moved = original.find { it.key == key } ?: return
        pending = original.filterNot { it.key == key }.toMutableList().apply { add(target, moved) }
        pending.forEachIndexed { index, item ->
            bar.findViewById<View>(item.destination)?.apply {
                alpha = if (item.key == key) .4f else 1f
                animate().translationX(centers.getValue(original[index].key) - centers.getValue(item.key)).setDuration(100).start()
            }
        }
    }
    fun bind() {
        val items = store.bottom()
        items.forEachIndexed { index, item ->
            bar.findViewById<View>(item.destination)?.let { view ->
                view.setOnLongClickListener {
                    it.startDragAndDrop(ClipData.newPlainText("settings-tab", item.key), View.DragShadowBuilder(it), Dragged(item.key, bar), 0)
                }
                ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        if (index > 0) info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.setting_frequent_action_move_up,
                            bar.context.getString(R.string.setting_frequent_move_up, item.title)))
                        if (index < items.lastIndex) info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.setting_frequent_action_move_down,
                            bar.context.getString(R.string.setting_frequent_move_down, item.title)))
                    }
                    override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean {
                        val offset = when (action) { R.id.setting_frequent_action_move_up -> -1; R.id.setting_frequent_action_move_down -> 1; else -> return super.performAccessibilityAction(host, action, args) }
                        val config = store.read(); val order = store.bottom(config).map { it.key }.toMutableList()
                        val from = order.indexOf(item.key); val to = from + offset
                        if (from < 0 || to !in order.indices) return false
                        java.util.Collections.swap(order, from, to)
                        val available = order.toSet(); val iterator = order.iterator()
                        store.save(config.copy(bottom = config.bottom.map { if (it in available) iterator.next() else it }))
                        refresh(); return true
                    }
                })
            }
        }
    }
}
