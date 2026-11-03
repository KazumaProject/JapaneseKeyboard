package com.kazumaproject.gesture_keyboard

import android.content.Context
import android.view.Gravity
import android.widget.*
import androidx.core.widget.doAfterTextChanged

/** A shared reference and practice surface; practice never sends text to the editor. */
class GestureGuideView(
    context: Context,
    private val set: GestureSet,
    private val threshold: Float,
    searchable: Boolean = true,
    private val onRecorded: ((GestureEntry, List<GesturePoint>) -> Unit)? = null,
    private val onEdit: ((GestureEntry) -> Unit)? = null,
    private val onDelete: ((GestureEntry) -> Unit)? = null
) : LinearLayout(context) {
    private val ja = resources.configuration.locales[0].language == "ja"
    private fun t(j: String, e: String) = if (ja) j else e
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private val available = set.entries + if (set.palm) PalmGesturePresets.punctuation.entries else emptyList()
    private var selected = available.firstOrNull()
    private var variant = 0
    private var recorded: List<GesturePoint>? = null
    private val matcher = GestureRecognizer.Prepared(set)
    private val punctuationMatcher = GestureRecognizer.Prepared(PalmGesturePresets.punctuation)
    private val label = TextView(context)
    private val referenceInk = label.currentTextColor
    private val result = TextView(context)
    private val pad = GesturePadView(context)
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }
    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(12))
        addView(TextView(context).apply { text = t("●から矢印の方向に一筆。見本を選んで練習できます。", "Start at the dot and follow the arrow. Select a sample to practice.") })
        if (set.palm) addView(TextView(context).apply {
            val controls = if (set.builtIn) t("←削除  →空白／変換  ↙改行", "← Delete  → Space/convert  ↙ Enter")
                else t("登録した操作は下の文字表で確認できます。", "See the chart below for your assigned commands.")
            val shift = if (set.builtIn && set.entries.any { it.output == GestureOutput.Command(com.kazumaproject.custom_keyboard.data.KeyAction.ShiftKey) })
                t("  ↑Shift→Caps→解除（英字）", "  ↑ Shift→Caps→off (Alphabet)") else ""
            text = controls + shift + t("\nタップで記号待ち、もう一度タップで .", "\nTap for punctuation; tap again for a period.")
        })
        addView(label)
        pad.setBackgroundColor(0xffeef2f8.toInt())
        pad.inkColor = 0xff243049.toInt()
        pad.onStroke = { points ->
            recorded = points
            result.text = when (val match = (if (selected in set.entries) matcher else punctuationMatcher).evaluate(points, threshold)) {
                is GestureRecognizer.Decision.Accepted -> {
                    val correct = match.match.entry.output == selected?.output
                    (if (correct) t("一致: ", "Matched: ") else t("認識結果: ", "Recognized: ")) + GestureLabels.output(match.match.entry.output, context)
                }
                is GestureRecognizer.Decision.Ambiguous -> t("候補: ", "Matches: ") + match.candidates.joinToString(" / ") { GestureLabels.output(it.entry.output, context) }
                GestureRecognizer.Decision.Rejected -> t("認識できません。始点と方向を確認してください。", "Not recognized. Check the starting point and direction.")
            }
        }
        addView(pad, LayoutParams(-1, dp(170)))
        addView(result)
        val actions = LinearLayout(context)
        fun action(text: String, run: () -> Unit) = Button(context).apply { this.text = text; isAllCaps = false; setOnClickListener { run() } }
        actions.addView(action(t("書き順を再生", "Replay stroke")) { pad.animateGuide = true }, LayoutParams(0, -2, 1f))
        actions.addView(action(t("別の見本", "Next sample")) {
            selected?.let { variant = (variant + 1) % it.samples.size; updateSelected() }
        }, LayoutParams(0, -2, 1f))
        addView(actions)
        if (onRecorded != null) addView(action(t("練習した線を登録", "Save practice stroke")) {
            val entry = selected; val points = recorded
            if (entry != null && points != null) onRecorded.invoke(entry, points)
            else result.text = t("先に練習領域に描いてください", "Draw in the practice area first")
        })
        if (onEdit != null) addView(action(t("選択したジェスチャーを編集", "Edit selected gesture")) {
            selected?.takeIf { it in set.entries }?.let(onEdit)
        })
        if (onDelete != null) addView(action(t("選択したジェスチャーを削除", "Delete selected gesture")) {
            selected?.takeIf { it in set.entries }?.let(onDelete)
        })
        if (searchable) addView(EditText(context).apply {
            hint = t("文字・操作を検索", "Find a character or command")
            setSingleLine()
            doAfterTextChanged { render(it.toString()) }
        })
        addView(grid)
        render("")
        updateSelected()
    }
    private fun updateSelected() {
        selected?.let {
            label.text = GestureLabels.output(it.output, context) + " · ${variant + 1}/${it.samples.size}"
            pad.guide = it.samples[variant]
            pad.animateGuide = false
            recorded = null
            result.text = ""
        }
    }
    private fun render(query: String) {
        grid.removeAllViews()
        available.filter { GestureLabels.output(it.output, context).contains(query, ignoreCase = true) }
            .chunked(3).forEach { entries ->
                val row = LinearLayout(context)
                entries.forEach { entry ->
                    val tile = LinearLayout(context).apply {
                        orientation = VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(2), dp(6), dp(2), dp(6))
                        contentDescription = GestureLabels.output(entry.output, context)
                        setOnClickListener { selected = entry; variant = 0; updateSelected() }
                    }
                    tile.addView(TextView(context).apply {
                        text = (if (entry !in set.entries) "· → " else "") + GestureLabels.output(entry.output, context); gravity = Gravity.CENTER; maxLines = 2
                    }, LayoutParams(-1, dp(40)))
                    tile.addView(GesturePadView(context).apply {
                        guide = entry.samples.first(); inkColor = referenceInk; captureAllowed = { false }
                        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LayoutParams(-1, dp(72)))
                    row.addView(tile, LayoutParams(0, -2, 1f))
                }
                repeat(3 - entries.size) { row.addView(Space(context), LayoutParams(0, 1, 1f)) }
                grid.addView(row)
            }
    }
    fun cancelPractice() { pad.cancelStroke(); pad.animateGuide = false }
    override fun onDetachedFromWindow() { cancelPractice(); super.onDetachedFromWindow() }
}
