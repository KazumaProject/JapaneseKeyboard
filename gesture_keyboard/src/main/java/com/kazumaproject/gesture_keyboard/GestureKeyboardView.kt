package com.kazumaproject.gesture_keyboard

import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.WindowManager
import android.app.Activity
import java.util.concurrent.Executors
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.ui.skin.SkinKeyRole

class GestureKeyboardView @JvmOverloads constructor(baseContext: Context, attrs: AttributeSet? = null) : LinearLayout(localizedGestureContext(baseContext), attrs) {
    var inputAllowed: () -> Boolean = { true }
    var onInteraction: () -> Unit = {}
    var onOutput: (GestureOutput) -> Boolean = { false }
    var onCommand: (GestureCommand) -> Unit = {}
    var onOpenSettings: () -> Unit = {}
    /** Floating windows are subwindows; attach dialogs to the original IME window. */
    var dialogToken: () -> android.os.IBinder? = { windowToken }
    var onStateChanged: (GestureSessionState) -> Unit = {}
    var onControlSelected: ((String) -> Unit)? = null
    var onControlMove: ((String, GestureSide, Int) -> Unit)? = null
    var onControlAdd: ((GestureSide) -> Unit)? = null
    var selectedControlId: String? = null
        set(value) { field = value; refreshSelection() }
    var session = GestureSessionState()
        private set
    val isEditing: Boolean get() = onControlSelected != null
    val leftPad = GesturePadView(context)
    val rightPad = GesturePadView(context)
    private val ja get() = resources.configuration.locales[0].language == "ja"
    private val main = Handler(Looper.getMainLooper())
    private var epoch = 0L
    private var blockedTouch = false
    private var sets = GesturePresets.sets(ja)
    private var layout = GestureControlLayout()
    private var maxDistance = GestureRecognizer.DEFAULT_MAX_DISTANCE
    private var showGuide = true
    private var renderedEditing = false
    private var dialog: AlertDialog? = null
    private var prepared = emptyMap<String, GestureRecognizer.Prepared>()
    private var bg = 0xfff1f4fa.toInt()
    private var keyBg = Color.WHITE
    private var fg = 0xff243049.toInt()
    private var special = 0xffe8edf6.toInt()
    private var skinId = KeyboardSkinId.DEFAULT
    private val body = LinearLayout(context)
    private val middle = LinearLayout(context)
    private val preview = TextView(context)
    private val controls = mutableMapOf<String, Button>()
    private val titles = mutableMapOf<GestureSide, Button>()
    private var repeatTask: Runnable? = null

    init {
        orientation = VERTICAL
        setPadding(dp(2), dp(2), dp(2), dp(2))
        preview.gravity = Gravity.CENTER
        preview.textSize = 13f
        addView(preview, LayoutParams(-1, dp(24)))
        addView(body, LayoutParams(-1, 0, 1f))
        body.orientation = HORIZONTAL
        rebuild()
    }
    fun configure(available: List<GestureSet>, state: GestureSessionState, controls: GestureControlLayout,
                  guide: Boolean = true, distance: Float = GestureRecognizer.DEFAULT_MAX_DISTANCE) {
        val nextSets = available.ifEmpty { GesturePresets.sets(ja) }
        val ids = nextSets.map { it.id }.toSet()
        val safeState = state.copy(
            leftSetId = state.leftSetId.takeIf { it in ids } ?: nextSets.first().id,
            rightSetId = state.rightSetId.takeIf { it in ids } ?: nextSets.last().id)
        if (sets == nextSets && session == safeState && layout == controls && showGuide == guide && maxDistance == distance && renderedEditing == isEditing) return
        cancelInput()
        sets = nextSets; session = safeState; layout = controls; showGuide = guide; maxDistance = distance
        renderedEditing = isEditing
        prepared = sets.associate { it.id to GestureRecognizer.Prepared(it) }
        rebuild()
    }
    fun setAppearance(background: Int, key: Int, text: Int, specialKey: Int, skin: KeyboardSkinId = KeyboardSkinId.DEFAULT) {
        val palette = KeyboardSkinRegistry.find(skin)?.palette
        val nextBg = palette?.background ?: background
        val nextKey = palette?.key ?: key
        val nextText = palette?.text ?: text
        val nextSpecial = palette?.specialKey ?: specialKey
        if (skinId == skin && bg == nextBg && keyBg == nextKey && fg == nextText && special == nextSpecial) return
        skinId = skin; bg = nextBg; keyBg = nextKey; fg = nextText; special = nextSpecial
        rebuild()
    }
    fun cancelInput() {
        epoch++
        leftPad.cancelStroke(); rightPad.cancelStroke()
        main.removeCallbacksAndMessages(null); repeatTask = null
    }
    private fun rebuild() {
        cancelInput()
        body.removeAllViews(); middle.removeAllViews(); controls.clear(); titles.clear()
        setBackgroundColor(bg)
        KeyboardSkinRegistry.find(skinId)?.let { background = it.keyboardDrawable(resources) }
        preview.setTextColor(fg)
        body.addView(controlColumn(GestureSide.LEFT), LayoutParams(if (isEditing || layout.leftKeys.isNotEmpty()) dp(48) else 0, -1))
        body.addView(middle, LayoutParams(0, -1, 1f))
        body.addView(controlColumn(GestureSide.RIGHT), LayoutParams(if (isEditing || layout.rightKeys.isNotEmpty()) dp(48) else 0, -1))
        middle.orientation = HORIZONTAL
        GestureSide.entries.forEach { side ->
            val column = LinearLayout(context).apply { orientation = VERTICAL }
            val header = LinearLayout(context)
            val set = sets.firstOrNull { it.id == session.setId(side) } ?: sets.first()
            val title = button(set.name).apply {
                id = if (side == GestureSide.LEFT) R.id.gesture_set_left else R.id.gesture_set_right
                textSize = 12f
                setOnClickListener { selectSet(side) }
                contentDescription = (if (side == GestureSide.LEFT) { if (ja) "左: " else "Left: " } else { if (ja) "右: " else "Right: " }) + set.name
            }
            titles[side] = title
            header.addView(title, LayoutParams(0, -1, 1f))
            if (set.id == GesturePresets.LATIN) header.addView(button(if (session.uppercase(side)) "A" else "a").apply {
                setOnClickListener { cancelInput(); session = session.toggleCase(side); onStateChanged(session); rebuild() }
            }, LayoutParams(dp(32), -1))
            val menu = button("⋮").apply { contentDescription = if (ja) "メニュー" else "Menu"; setOnClickListener { menu(side) } }
            header.addView(menu, LayoutParams(dp(28), -1))
            column.addView(header, LayoutParams(-1, dp(40)))
            val pad = if (side == GestureSide.LEFT) leftPad else rightPad
            pad.id = if (side == GestureSide.LEFT) R.id.gesture_pad_left else R.id.gesture_pad_right
            (pad.parent as? android.view.ViewGroup)?.removeView(pad)
            pad.inkColor = fg
            pad.background = KeyboardSkinRegistry.find(skinId)?.keyDrawable(resources, role = SkinKeyRole.CHARACTER) ?: background(keyBg)
            pad.captureAllowed = { !isEditing && inputAllowed() }
            pad.onStart = { onInteraction() }
            pad.onStroke = { stroke -> recognize(side, stroke) }
            column.addView(pad, LayoutParams(-1, 0, 1f))
            middle.addView(column, LayoutParams(0, -1, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        refreshSelection()
    }
    private fun recognize(side: GestureSide, stroke: List<GesturePoint>) {
        if (!inputAllowed() || isEditing) return
        val generation = epoch
        val matcher = prepared[session.setId(side)] ?: GestureRecognizer.Prepared(sets.first { it.id == session.setId(side) })
        val uppercase = session.uppercase(side) && session.setId(side) == GesturePresets.LATIN
        val threshold = maxDistance
        worker.execute {
            val match = matcher.recognize(stroke, threshold)
            main.post {
                if (epoch != generation || !inputAllowed() || isEditing || !isShown) return@post
                if (match == null) {
                    preview.text = if (ja) "認識できません。もう一度描いてください" else "Not recognized. Please try again."
                } else {
                    val output = match.entry.output.let {
                        if (uppercase && it is GestureOutput.LiteralText) it.copy(text = it.text.uppercase(java.util.Locale.ROOT)) else it
                    }
                    preview.text = GestureLabels.output(output, ja)
                    if (output is GestureOutput.Command) onCommand(output.action) else onOutput(output)
                }
            }
        }
    }
    private fun selectSet(side: GestureSide) {
        cancelInput()
        showDialog(AlertDialog.Builder(context).setTitle(if (ja) "文字セット" else "Gesture set")
            .setItems(sets.map { it.name }.toTypedArray()) { _, index ->
                session = session.withSet(side, sets[index].id); onStateChanged(session); rebuild()
            })
    }
    private fun menu(side: GestureSide) {
        cancelInput()
        val options = mutableListOf<String>()
        if (showGuide) options.add(if (ja) "文字表・描き方" else "Gesture guide")
        options.add(if (ja) "設定" else "Settings")
        options.add(if (ja) "次のキーボード" else "Next keyboard")
        showDialog(AlertDialog.Builder(context).setItems(options.toTypedArray()) { _, selected ->
            when {
                showGuide && selected == 0 -> showSetGuide(side)
                selected == options.size - 2 -> if (!isEditing) onOpenSettings()
                else -> if (!isEditing && inputAllowed()) enqueueCommand(GestureCommand.NEXT_KEYBOARD)
            }
        })
    }
    private fun showSetGuide(side: GestureSide) {
        val set = sets.first { it.id == session.setId(side) }
        val content = LinearLayout(context).apply { orientation = VERTICAL }
        set.entries.forEach { entry ->
            val row = LinearLayout(context)
            row.addView(TextView(context).apply { text = GestureLabels.output(entry.output, ja); gravity = Gravity.CENTER; textSize = 22f }, LayoutParams(dp(64), dp(100)))
            row.addView(GesturePadView(context).apply { guide = entry.samples.first(); inkColor = Color.DKGRAY; captureAllowed = { false } }, LayoutParams(0, dp(100), 1f))
            content.addView(row)
        }
        showDialog(AlertDialog.Builder(context).setTitle(set.name + if (ja) " · ●始点／矢印の方向" else " · dot=start / arrow=direction")
            .setView(ScrollView(context).apply { addView(content) }).setPositiveButton(android.R.string.ok, null))
    }
    private fun showDialog(builder: AlertDialog.Builder) {
        dialog?.dismiss()
        val next = builder.create()
        // An IME is a Service, so the ordinary Activity dialog token is unavailable.
        // Attach to the input view's window rather than requesting overlay permission.
        if (context !is Activity) {
            val token = dialogToken() ?: return
            next.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
            next.window?.attributes = next.window!!.attributes.apply { this.token = token }
            next.window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        dialog = next
        next.show()
    }
    private fun controlColumn(side: GestureSide): View {
        val column = LinearLayout(context).apply { orientation = VERTICAL }
        val scroll = ScrollView(context).apply { isFillViewport = true; addView(column) }
        val keys = layout.keys(side)
        val gaps = mutableListOf<View>()
        fun gap() { val marker = View(context).apply { setBackgroundColor(Color.TRANSPARENT) }; gaps.add(marker); column.addView(marker, LayoutParams(-1, dp(3))) }
        gap()
        keys.forEach { key ->
            val label = key.labelOverride?.takeIf { it.isNotBlank() } ?: GestureLabels.glyph(key.action)
            var repeated = false
            val button = button(label).apply {
                textSize = if (label.length > 3) 10f else 18f
                contentDescription = key.labelOverride ?: GestureLabels.command(key.action, ja)
                setOnClickListener {
                    if (isEditing) onControlSelected?.invoke(key.id)
                    else if (inputAllowed()) { onInteraction(); enqueueCommand(key.action) }
                }
                if (isEditing) setOnLongClickListener {
                    onControlSelected?.invoke(key.id)
                    startDragAndDrop(ClipData.newPlainText("gesture-key", key.id), DragShadowBuilder(this), key.id, 0)
                } else if (key.action in setOf(GestureCommand.DELETE, GestureCommand.CURSOR_LEFT, GestureCommand.CURSOR_RIGHT)) {
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                repeated = false
                                val task = object : Runnable {
                                    override fun run() {
                                        if (inputAllowed()) { repeated = true; enqueueCommand(key.action); main.postDelayed(this, 70L) }
                                    }
                                }
                                repeatTask = task; main.postDelayed(task, 400L)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                repeatTask?.let(main::removeCallbacks); repeatTask = null
                                if (repeated) { view.isPressed = false; return@setOnTouchListener true }
                            }
                            MotionEvent.ACTION_MOVE -> if (event.x !in 0f..view.width.toFloat() || event.y !in 0f..view.height.toFloat()) {
                                repeatTask?.let(main::removeCallbacks); repeatTask = null
                            }
                        }
                        false
                    }
                }
            }
            controls[key.id] = button
            column.addView(button, LayoutParams(-1, dp(48)))
            gap()
        }
        if (isEditing) column.addView(button("+").apply {
            contentDescription = if (ja) (if (side == GestureSide.LEFT) "左にキーを追加" else "右にキーを追加")
                else if (side == GestureSide.LEFT) "Add left key" else "Add right key"
            setOnClickListener { onControlAdd?.invoke(side) }
        }, LayoutParams(-1, dp(48)))
        if (isEditing) {
            var insertion = 0
            var lastDragY = 0f
            var autoDirection = 0
            fun updateInsertion(y: Float) {
                val contentY = y + scroll.scrollY
                insertion = keys.indices.firstOrNull { i -> contentY < (controls[keys[i].id]?.let { it.top + it.height / 2 } ?: 0) } ?: keys.size
                gaps.forEachIndexed { i, marker -> marker.setBackgroundColor(if (i == insertion) fg else Color.TRANSPARENT) }
            }
            val autoScroll = object : Runnable {
                override fun run() {
                    if (autoDirection == 0) return
                    scroll.scrollBy(0, autoDirection * dp(8)); updateInsertion(lastDragY); main.postDelayed(this, 32L)
                }
            }
            fun clear() { gaps.forEach { it.setBackgroundColor(Color.TRANSPARENT) }; autoDirection = 0; main.removeCallbacks(autoScroll) }
            scroll.setOnDragListener { _, event ->
                val id = event.localState as? String
                if (id == null || (layout.leftKeys + layout.rightKeys).none { it.id == id }) return@setOnDragListener false
                when (event.action) {
                    DragEvent.ACTION_DRAG_LOCATION, DragEvent.ACTION_DRAG_ENTERED -> {
                        lastDragY = event.y; updateInsertion(lastDragY)
                        val direction = if (event.y < dp(32)) -1 else if (event.y > scroll.height - dp(32)) 1 else 0
                        if (direction != autoDirection) { main.removeCallbacks(autoScroll); autoDirection = direction; if (direction != 0) main.post(autoScroll) }
                    }
                    DragEvent.ACTION_DROP -> { updateInsertion(event.y); clear(); onControlMove?.invoke(id, side, insertion) }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> clear()
                }
                true
            }
        }
        return scroll
    }
    private fun refreshSelection() {
        controls.forEach { (id, button) -> button.background = background(if (id == selectedControlId) special else keyBg, id == selectedControlId) }
    }
    private fun enqueueCommand(command: GestureCommand) {
        val generation = epoch
        // This barrier follows earlier stroke recognition tasks on the same worker.
        worker.execute {
            main.post {
                if (epoch == generation && !isEditing && inputAllowed() && isShown) onCommand(command)
            }
        }
    }
    private fun button(label: String) = Button(context).apply {
        text = label; isAllCaps = false; setPadding(0, 0, 0, 0); minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        setTextColor(fg); background = background(keyBg)
    }
    private fun background(color: Int, selected: Boolean = false): android.graphics.drawable.Drawable =
        if (!selected && KeyboardSkinRegistry.find(skinId) != null)
            KeyboardSkinRegistry.find(skinId)!!.keyDrawable(resources, role = SkinKeyRole.MODIFIER)
        else GradientDrawable().apply { setColor(color); cornerRadius = dp(8).toFloat(); if (selected) setStroke(dp(2), fg) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!isEditing && event.pointerCount > 1) {
            if (!blockedTouch) {
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel); cancel.recycle()
            }
            blockedTouch = true; cancelInput()
        }
        if (blockedTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) blockedTouch = false
            return true
        }
        return super.dispatchTouchEvent(event)
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Android can call this from the superclass constructor before fields are initialized.
        if (visibility != VISIBLE && childCount > 0) { cancelInput(); dialog?.dismiss(); dialog = null }
    }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (visibility != VISIBLE && childCount > 0) { cancelInput(); dialog?.dismiss(); dialog = null } }
    override fun onDetachedFromWindow() { cancelInput(); dialog?.dismiss(); dialog = null; main.removeCallbacksAndMessages(null); super.onDetachedFromWindow() }
    companion object {
        // One queue preserves the order of strokes from both pads.
        private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "gesture-recognition").apply { isDaemon = true } }
    }
}
