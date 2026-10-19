package com.kazumaproject.morse_keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Non-focusable popup content: using the guide never changes the editor's input connection. */
internal class MorseGuidePanel(context: Context) : LinearLayout(context) {
    var onSelect: (MorseEntry) -> Unit = {}
    var onResize: () -> Unit = {}
    var onReplay: () -> Unit = {}
    var onReset: () -> Unit = {}
    private val title = TextView(context).apply { textSize = 14f }
    private val detail = TextView(context).apply { textSize = 18f; gravity = Gravity.CENTER; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val code = TextView(context).apply { textSize = 23f; setSingleLine(true); gravity = Gravity.CENTER }
    private val codeScroll = android.widget.HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = true
        isFillViewport = true
        addView(code, LayoutParams(-2, -1))
    }
    private val progress = TextView(context).apply { textSize = 13f; gravity = Gravity.CENTER }
    private val grid = GridLayout(context).apply { columnCount = 6 }
    private val scroll = ScrollView(context).apply { addView(grid) }
    private var ink = 0
    private var tile = 0
    private var entries = emptyList<MorseEntry>()
    private var name = ""
    private var mapId = ""
    private var label: (MorseOutput) -> String = { "" }
    private var selected: MorseEntry? = null
    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(10))
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(title, LayoutParams(0, dp(40), 1f))
        fun action(icon: Int, description: Int, click: () -> Unit) = ImageButton(context).apply {
            setImageResource(icon); contentDescription = context.getString(description)
            background = null; setPadding(dp(10),dp(10),dp(10),dp(10)); setOnClickListener { click() }
            header.addView(this, LayoutParams(dp(40), dp(40)))
        }
        action(android.R.drawable.ic_menu_agenda, R.string.morse_guide_list) {
            scroll.visibility = if (scroll.visibility == VISIBLE) GONE else VISIBLE
            onResize()
        }
        action(android.R.drawable.ic_media_play, R.string.morse_guide_replay) { onReplay() }
        action(android.R.drawable.ic_menu_revert, R.string.morse_guide_reset) { onReset() }
        addView(header)
        addView(scroll, LayoutParams(-1, dp(144)))
        addView(detail, LayoutParams(-1, -2))
        addView(codeScroll, LayoutParams(-1, dp(36)))
        addView(progress, LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }
    fun bind(map: MorseMap, outputLabel: (MorseOutput) -> String, foreground: Int, backgroundColor: Int, keyColor: Int) {
        val changed = mapId != map.id || entries != map.entries || name != map.name
        entries = map.entries; name = map.name; mapId = map.id; label = outputLabel; ink = foreground; tile = keyColor
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(backgroundColor); setStroke(dp(1), foreground and 0x00ffffff or 0x33000000) }
        title.text = map.name
        title.setTextColor(ink); detail.setTextColor(ink); code.setTextColor(ink); progress.setTextColor(ink)
        val header = getChildAt(0) as LinearLayout
        for (index in 1 until header.childCount) (header.getChildAt(index) as ImageButton).imageTintList = ColorStateList.valueOf(ink)
        if (changed) { selected = null; scroll.visibility = VISIBLE; scroll.post { scroll.scrollTo(0, 0) } }
        rebuildEntries()
    }
    private fun rebuildEntries() {
        grid.removeAllViews()
        entries.forEach { entry ->
            val text = label(entry.output)
            val button = TextView(context).apply {
                this.text = text; textSize = if (text.length > 3) 12f else 18f
                gravity = Gravity.CENTER; maxLines = 2; isClickable = true
                setTextColor(ink); contentDescription = context.getString(R.string.morse_guide_choose, text, symbols(entry.signal))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat(); setColor(tile)
                    if (selected == entry) setStroke(dp(2), ink)
                }
                setOnClickListener { selected = entry; onSelect(entry); rebuildEntries() }
            }
            grid.addView(button, GridLayout.LayoutParams().apply {
                width = 0; height = dp(44); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(2),dp(2),dp(2),dp(2))
            })
        }
    }
    fun update(entry: MorseEntry?, state: MorseGuide.Progress, demo: Boolean, straight: Boolean) {
        detail.text = if (entry == null) context.getString(R.string.morse_guide_pick)
            else label(entry.output)
        codeScroll.visibility = if (entry == null) GONE else VISIBLE
        code.text = entry?.signal?.let(::symbols).orEmpty()
        progress.text = when {
            entry == null -> context.getString(R.string.morse_guide_list_hint)
            demo -> context.getString(R.string.morse_guide_demo)
            state.mismatch -> context.getString(R.string.morse_guide_mismatch, state.count, state.total)
            state.completed -> context.getString(R.string.morse_guide_completed, state.total)
            state.count == state.total -> context.getString(R.string.morse_guide_divide, state.count, state.total)
            else -> context.getString(R.string.morse_guide_progress, state.count, state.total,
                context.getString(if (straight) {
                    if (state.next == '.') R.string.morse_guide_short else R.string.morse_guide_long
                } else if (state.next == '.') R.string.morse_dot else R.string.morse_dash))
        }
    }
    private fun symbols(signal: String) = signal.map { if (it == '.') "・" else "－" }.joinToString(" ")
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
