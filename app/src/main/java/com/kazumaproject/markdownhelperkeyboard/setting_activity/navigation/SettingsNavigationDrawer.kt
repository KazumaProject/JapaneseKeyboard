package com.kazumaproject.markdownhelperkeyboard.setting_activity.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.markdownhelperkeyboard.R

/** Editing stays inside the drawer. Names come from the destination catalog. */
internal class SettingsNavigationDrawer(
    context: Context,
    private val store: SettingsNavigationStore,
    private val navigate: (SettingsNavigationItem) -> Unit,
) : LinearLayout(context) {
    private enum class Section { DRAWER, BOTTOM }
    private var section = Section.DRAWER
    private var editing = false
    private var selectedDestination: Int? = null
    private val rows = Rows()
    private val list = RecyclerView(context).apply {
        id = R.id.settings_drawer_list
        layoutManager = LinearLayoutManager(context); adapter = rows
    }
    private val tabs = TabLayout(context)
    private val hint = TextView(context).apply { textSize = 12f; setPadding(dp(16), dp(8), dp(16), dp(8)) }
    private val edit = textButton(R.string.settings_navigation_edit).apply {
        id = R.id.settings_navigation_edit
        setOnClickListener { editing = !editing; render() }
    }
    private val add = textButton(R.string.settings_navigation_add).apply {
        id = R.id.settings_navigation_add
        setOnClickListener { addItem() }
    }
    private val reset = textButton(R.string.settings_navigation_reset).apply {
        setOnClickListener {
            MaterialAlertDialogBuilder(context).setMessage(R.string.settings_navigation_reset_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_navigation_reset) { _, _ -> store.save(store.defaults); render() }.show()
        }
    }
    private val drag = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun isLongPressDragEnabled() = editing
        override fun onMove(recyclerView: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val a = from.bindingAdapterPosition; val b = to.bindingAdapterPosition
            if (!editing || a < 0 || b < 0) return false
            rows.entries.add(b, rows.entries.removeAt(a)); rows.notifyItemMoved(a, b)
            return true
        }
        override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
        override fun clearView(recyclerView: RecyclerView, holder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, holder)
            if (editing) { saveOrder(rows.entries.map { it.key }); render() }
        }
    })
    init {
        orientation = VERTICAL
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(4), dp(8), dp(4))
            addView(TextView(context).apply { setText(R.string.settings_navigation_title); textSize = 20f }, LayoutParams(0, -2, 1f))
            addView(edit, LayoutParams(-2, -2))
        })
        listOf(R.string.settings_navigation_side, R.string.settings_navigation_bottom).forEach {
            tabs.addTab(tabs.newTab().setText(it))
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { section = Section.entries[tab.position]; render() }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        addView(tabs); addView(hint)
        addView(list, LayoutParams(-1, 0, 1f))
        addView(add, LayoutParams(-1, -2).apply { setMargins(dp(8), 0, dp(8), 0) })
        addView(reset, LayoutParams(-1, -2).apply { setMargins(dp(8), 0, dp(8), dp(8)) })
        drag.attachToRecyclerView(list)
        render()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun textButton(label: Int) = MaterialButton(context).apply {
        setText(label); minHeight = dp(48); minimumHeight = dp(48)
        backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        setTextColor(com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
        insetTop = 0; insetBottom = 0
    }
    private fun entries(config: SettingsNavigationConfig): List<SettingsNavigationItem> = when (if (editing) section else Section.DRAWER) {
        Section.DRAWER -> store.items(config.drawer)
        Section.BOTTOM -> store.bottom(config)
    }
    fun saveState() = Bundle().apply {
        putBoolean("editing", editing); putString("section", section.name)
        putParcelable("scroll", list.layoutManager?.onSaveInstanceState())
    }
    fun restoreState(state: Bundle?) {
        if (state == null) return
        editing = state.getBoolean("editing")
        section = runCatching { Section.valueOf(state.getString("section").orEmpty()) }.getOrDefault(Section.DRAWER)
        tabs.getTabAt(section.ordinal)?.select(); render()
        @Suppress("DEPRECATION") val scroll = state.getParcelable<android.os.Parcelable>("scroll")
        list.layoutManager?.onRestoreInstanceState(scroll)
    }
    fun select(destination: Int?) { selectedDestination = destination; rows.notifyDataSetChanged() }
    fun finishEditing() { editing = false; render() }
    private fun render() {
        edit.setText(if (editing) R.string.settings_navigation_done else R.string.settings_navigation_edit)
        tabs.isVisible = editing; add.isVisible = editing; reset.isVisible = editing
        hint.setText(if (!editing) R.string.settings_navigation_hint else when (section) {
            Section.DRAWER -> R.string.settings_navigation_side_hint
            Section.BOTTOM -> R.string.settings_navigation_bottom_hint
        })
        rows.entries = entries(store.read()).toMutableList(); rows.notifyDataSetChanged()
    }
    private fun keys(config: SettingsNavigationConfig) = when (section) {
        Section.DRAWER -> config.drawer; Section.BOTTOM -> config.bottom
    }
    private fun replace(config: SettingsNavigationConfig, keys: List<String>) = when (section) {
        Section.DRAWER -> config.copy(drawer = keys); Section.BOTTOM -> config.copy(bottom = keys)
    }
    private fun saveOrder(order: List<String>) {
        val config = store.read(); val available = entries(config).map { it.key }.toSet()
        val iterator = order.iterator()
        val merged = keys(config).mapNotNull { if (it in available) { if (iterator.hasNext()) iterator.next() else null } else it } + iterator.asSequence().toList()
        store.save(replace(config, merged))
    }
    private fun remove(key: String) { val config = store.read(); store.save(replace(config, keys(config) - key)); render() }
    private fun addItem() {
        val config = store.read()
        if (section == Section.BOTTOM && store.bottom(config).size >= SettingsNavigationStore.MAX_BOTTOM) {
            android.widget.Toast.makeText(context, R.string.settings_navigation_limit, android.widget.Toast.LENGTH_SHORT).show(); return
        }
        val available = store.catalog
        val choices = available.filter { it.key !in keys(config) }
        if (choices.isEmpty()) { android.widget.Toast.makeText(context, R.string.settings_navigation_all_added, android.widget.Toast.LENGTH_SHORT).show(); return }
        MaterialAlertDialogBuilder(context).setTitle(R.string.settings_navigation_add)
            .setItems(choices.map { it.title }.toTypedArray()) { _, index ->
                store.save(replace(config, keys(config) + choices[index].key)); render()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private inner class Rows : RecyclerView.Adapter<Rows.Holder>() {
        var entries = mutableListOf<SettingsNavigationItem>()
        override fun getItemCount() = entries.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(LinearLayout(context))
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(entries[position])
        inner class Holder(private val row: LinearLayout) : RecyclerView.ViewHolder(row) {
            private val handle = textButton(R.string.settings_navigation_drag_handle)
            private val title = TextView(context).apply { textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
            private val remove = textButton(R.string.settings_navigation_remove_symbol)
            init {
                row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                row.gravity = Gravity.CENTER_VERTICAL; row.minimumHeight = dp(56)
                handle.setPadding(0, 0, 0, 0); handle.minWidth = 0; handle.minimumWidth = 0
                remove.setPadding(0, 0, 0, 0); remove.minWidth = 0; remove.minimumWidth = 0
                row.addView(handle, LayoutParams(dp(48), -2)); row.addView(title, LayoutParams(0, -2, 1f))
                row.addView(remove, LayoutParams(dp(48), -2))
            }
            @SuppressLint("ClickableViewAccessibility")
            fun bind(item: SettingsNavigationItem) {
                title.text = item.title
                row.isActivated = selectedDestination == item.destination
                row.setBackgroundColor(if (row.isActivated) com.google.android.material.color.MaterialColors.getColor(row,
                    com.google.android.material.R.attr.colorSecondaryContainer) else android.graphics.Color.TRANSPARENT)
                row.setOnClickListener { if (!editing) navigate(item) }
                row.isClickable = !editing
                row.isFocusable = true; row.contentDescription = item.title
                handle.isVisible = editing; remove.isVisible = editing
                handle.contentDescription = context.getString(R.string.setting_frequent_drag_reorder) + ": " + item.title
                handle.setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_DOWN) { drag.startDrag(this); true } else false }
                remove.contentDescription = context.getString(R.string.settings_navigation_remove_item, item.title)
                remove.setOnClickListener { remove(item.key) }
                ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        if (!editing) return
                        if (bindingAdapterPosition > 0) info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.setting_frequent_action_move_up, context.getString(R.string.setting_frequent_move_up, item.title)))
                        if (bindingAdapterPosition < entries.lastIndex) info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.setting_frequent_action_move_down, context.getString(R.string.setting_frequent_move_down, item.title)))
                    }
                    override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                        val delta = when (action) { R.id.setting_frequent_action_move_up -> -1; R.id.setting_frequent_action_move_down -> 1; else -> return super.performAccessibilityAction(host, action, args) }
                        val from = entries.indexOfFirst { it.key == item.key }; val to = from + delta
                        if (!editing || from < 0 || to !in entries.indices) return false
                        entries.add(to, entries.removeAt(from)); saveOrder(entries.map { it.key }); render(); return true
                    }
                })
            }
        }
    }
}
