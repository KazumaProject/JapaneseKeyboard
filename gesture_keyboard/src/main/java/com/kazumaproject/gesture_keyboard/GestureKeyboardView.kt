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
import android.widget.PopupWindow
import android.view.WindowManager
import android.app.Activity
import java.util.concurrent.Executors
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import androidx.core.graphics.drawable.DrawableCompat
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.ui.skin.SkinKeyRole

class GestureKeyboardView @JvmOverloads constructor(baseContext: Context, attrs: AttributeSet? = null) : LinearLayout(localizedGestureContext(baseContext), attrs) {
    var inputAllowed: () -> Boolean = { true }
    var onInteraction: () -> Unit = {}
    var onOutput: (GestureOutput) -> Boolean = { false }
    var onCommand: (KeyAction) -> Unit = {}
    var onOpenSettings: () -> Unit = {}
    /** Floating windows are subwindows; attach dialogs to the original IME window. */
    var dialogToken: () -> android.os.IBinder? = { windowToken }
    var menuAnchor: () -> View = { this }
    var onBeforeStateChange: () -> Unit = {}
    var onPadBoundsChanged: ((String, GestureBounds) -> Unit)? = null
    var onStateChanged: (GestureSessionState) -> Unit = {}
    var onControlSelected: ((String) -> Unit)? = null
    var onControlMove: ((String, GestureSide, Int) -> Unit)? = null
    var onControlAdd: ((GestureSide) -> Unit)? = null
    var selectedControlId: String? = null
        set(value) { field = value; refreshSelection() }
    var session = GestureSessionState()
        private set
    val isEditing: Boolean get() = onControlSelected != null
    private val canvas = GestureCanvasView(context)
    val leftPad get() = canvas.pads.values.first()
    val rightPad get() = canvas.pads.values.elementAtOrNull(1) ?: leftPad
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
    private var popup: PopupWindow? = null
    private var prepared = emptyMap<String, GestureRecognizer.Prepared>()
    private var bg = 0xfff1f4fa.toInt()
    private var keyBg = Color.WHITE
    private var fg = 0xff243049.toInt()
    private var special = 0xffe8edf6.toInt()
    private var skinId = KeyboardSkinId.DEFAULT
    private val body = LinearLayout(context)
    private val middle = canvas
    private val preview = TextView(context)
    private val controls = mutableMapOf<String, Button>()

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
        val safeState = state.copy(pads = state.pads.map { pad ->
            if (pad.setId in ids) pad else pad.copy(setId = nextSets.first().id, romajiOverride = null)
        })
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
        canvas.pads.values.forEach { it.cancelStroke() }
        main.removeCallbacksAndMessages(null); repeatTask = null
    }
    private fun rebuild() {
        cancelInput()
        body.removeAllViews(); controls.clear()
        setBackgroundColor(bg)
        KeyboardSkinRegistry.find(skinId)?.let { background = it.keyboardDrawable(resources) }
        preview.setTextColor(fg)
        body.addView(controlColumn(GestureSide.LEFT), LayoutParams(if (isEditing || layout.leftKeys.isNotEmpty()) dp(48) else 0, -1))
        body.addView(middle, LayoutParams(0, -1, 1f))
        body.addView(controlColumn(GestureSide.RIGHT), LayoutParams(dp(48), -1))
        canvas.onBoundsChanged = { id, bounds -> onPadBoundsChanged?.invoke(id, bounds) }
        canvas.configure(session, sets, isEditing, fg,
            { KeyboardSkinRegistry.find(skinId)?.keyDrawable(resources, role = SkinKeyRole.CHARACTER) ?: background(keyBg) },
            { inputAllowed() },
            { id ->
                if (session.activePadId != id) { session = session.copy(activePadId = id); onStateChanged(session); refreshSelection() }
                onInteraction()
            }, ::recognize)
        refreshSelection()
    }
    private fun recognize(padId: String, stroke: List<GesturePoint>) {
        if (!inputAllowed() || isEditing) return
        val generation = epoch
        val pad = session.pads.first { it.id == padId }
        val set = sets.first { it.id == pad.setId }
        val matcher = prepared[set.id] ?: GestureRecognizer.Prepared(set)
        val threshold = maxDistance
        worker.execute {
            val match = matcher.recognize(stroke, threshold)
            main.post {
                if (epoch != generation || !inputAllowed() || isEditing || !isShown) return@post
                if (match == null) {
                    preview.text = if (ja) "認識できません。もう一度描いてください" else "Not recognized. Please try again."
                } else {
                    val raw = match.entry.output
                    val text = when (raw) { is GestureOutput.LiteralText -> raw.text; is GestureOutput.Reading -> raw.text; else -> null }
                    val output = when {
                        raw is GestureOutput.Command -> raw
                        pad.direct && text != null -> GestureOutput.LiteralText(if (pad.uppercase || pad.shift) text.uppercase(java.util.Locale.ROOT) else text)
                        raw is GestureOutput.LiteralText && (pad.romajiOverride ?: set.isRomaji) && raw.text.all { it in 'a'..'z' || it in 'A'..'Z' } -> GestureOutput.Romaji(raw.text)
                        raw is GestureOutput.LiteralText && (pad.uppercase || pad.shift) -> raw.copy(text = raw.text.uppercase(java.util.Locale.ROOT))
                        else -> raw
                    }
                    preview.text = GestureLabels.output(output, context)
                    if (output is GestureOutput.Command) { session = session.copy(activePadId = padId); onCommand(output.action) }
                    else onOutput(output)
                    if (pad.shift && output !is GestureOutput.Command) { session = session.update(padId) { it.copy(shift = false) }; onStateChanged(session); refreshSelection() }
                }
            }
        }
    }
    fun handlePadAction(action: KeyAction): Boolean {
        val id = session.activePadId
        val pad = session.activePad()
        val set = sets.first { it.id == pad.setId }
        val next = when (action) {
            KeyAction.SwitchToKanaLayout -> session.withSet(id, GesturePresets.KANA)
            KeyAction.SwitchToEnglishLayout -> session.withSet(id, GesturePresets.LATIN)
            KeyAction.SwitchToNumberLayout -> session.withSet(id, GesturePresets.DIGITS)
            KeyAction.ShiftKey -> session.update(id) { it.copy(shift = !it.shift) }
            KeyAction.CapLockKey -> session.update(id) { it.copy(uppercase = !it.uppercase, shift = false) }
            KeyAction.SwitchDirectMode -> session.update(id) { it.copy(direct = !it.direct) }
            KeyAction.SwitchRomajiEnglish -> session.update(id) { it.copy(romajiOverride = !(it.romajiOverride ?: set.isRomaji)) }
            else -> return false
        }
        // Keep already queued key actions alive; these state changes do not change geometry.
        canvas.pads.values.forEach { it.cancelStroke() }
        session = next; onStateChanged(session)
        session.pads.forEachIndexed { index, area ->
            canvas.pads[area.id]?.contentDescription = "${index + 1}: ${sets.first { it.id == area.setId }.name}"
        }
        refreshSelection()
        return true
    }
    private fun selectSet(padId: String) {
        cancelInput()
        showChoices(if (ja) "文字セット" else "Gesture set", sets.map { it.name }) { index ->
            onBeforeStateChange()
            session = session.withSet(padId, sets[index].id); onStateChanged(session); rebuild()
        }
    }
    private fun menu() {
        cancelInput()
        val options = session.pads.mapIndexed { index, pad ->
            "${index + 1} · ${sets.first { it.id == pad.setId }.name}" +
                (if (pad.uppercase) " · CAPS" else if (pad.shift) " · Shift" else "") +
                (if (pad.direct) (if (ja) " · 直接入力" else " · Direct") else "")
        }
        showChoices(if (ja) "入力領域" else "Drawing areas", options + (if (ja) "設定" else "Settings")) { index ->
            if (index == options.size) { if (!isEditing) onOpenSettings() }
            else {
                val pad = session.pads[index]
                val actions = mutableListOf(if (ja) "文字セットを変更" else "Change gesture set")
                if (showGuide) actions.add(if (ja) "文字表・描き方" else "Gesture guide")
                showChoices(options[index], actions) { item ->
                    if (item == 0) selectSet(pad.id) else showSetGuide(pad.id)
                }
            }
        }
    }
    /** Choices need touch input only. Keeping editor focus avoids an IME restart on each submenu. */
    private fun showChoices(title: String, choices: List<String>, choose: (Int) -> Unit) {
        popup?.dismiss()
        val content = LinearLayout(context).apply {
            orientation = VERTICAL; setBackgroundColor(bg)
            addView(TextView(context).apply {
                text = title; textSize = 16f; setTextColor(fg); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
            }, LayoutParams(-1, dp(48)))
        }
        val scroll = ScrollView(context)
        val list = LinearLayout(context).apply { orientation = VERTICAL }
        choices.forEachIndexed { index, label -> list.addView(button(label).apply {
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { popup?.dismiss(); choose(index) }
        }, LayoutParams(-1, dp(48))) }
        scroll.addView(list)
        content.addView(scroll, LayoutParams(-1, 0, 1f))
        val popupWidth = minOf(width, dp(360)).coerceAtLeast(dp(120))
        val popupHeight = minOf(dp((choices.size + 1) * 48), (resources.displayMetrics.heightPixels * .65f).toInt())
        val next = PopupWindow(content, popupWidth, popupHeight, false).apply {
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(background(bg)); elevation = dp(8).toFloat()
        }
        popup = next
        next.setOnDismissListener { if (popup === next) popup = null }
        val location = IntArray(2).also(::getLocationOnScreen)
        next.showAtLocation(menuAnchor(), Gravity.TOP or Gravity.LEFT,
            (location[0] + (width - popupWidth) / 2).coerceAtLeast(0),
            location[1].coerceIn(0, (resources.displayMetrics.heightPixels - popupHeight).coerceAtLeast(0)))
    }
    private fun showSetGuide(padId: String) {
        val set = sets.first { it.id == session.pads.first { pad -> pad.id == padId }.setId }
        val content = LinearLayout(context).apply { orientation = VERTICAL }
        set.entries.forEach { entry ->
            val row = LinearLayout(context)
            row.addView(TextView(context).apply { text = GestureLabels.output(entry.output, context); gravity = Gravity.CENTER; textSize = 22f }, LayoutParams(dp(64), dp(100)))
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
            val label = key.labelOverride?.takeIf { it.isNotBlank() } ?: GestureLabels.command(key.action, context)
            var repeated = false
            val button = button(label).apply {
                if (key.labelOverride.isNullOrBlank()) applyActionIcon(this, key.action)
                textSize = if (label.length > 3) 10f else 18f
                contentDescription = key.labelOverride ?: GestureLabels.command(key.action, context)
                setOnClickListener {
                    if (isEditing) onControlSelected?.invoke(key.id)
                    else if (inputAllowed()) { onInteraction(); enqueueCommand(key.action) }
                }
                if (isEditing) setOnLongClickListener {
                    onControlSelected?.invoke(key.id)
                    startDragAndDrop(ClipData.newPlainText("gesture-key", key.id), DragShadowBuilder(this), key.id, 0)
                } else if (key.action in setOf(KeyAction.Delete, KeyAction.MoveCursorLeft, KeyAction.MoveCursorRight)) {
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
        if (side == GestureSide.RIGHT) column.addView(button("").apply {
            id = R.id.gesture_menu
            contentDescription = if (ja) "ジェスチャーメニュー" else "Gesture menu"
            setIcon(this, com.kazumaproject.core.R.drawable.more_vert_24px)
            setOnClickListener { menu() }
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
        controls.forEach { (id, button) ->
            button.background = background(if (id == selectedControlId) special else keyBg, id == selectedControlId)
            val key = (layout.leftKeys + layout.rightKeys).first { it.id == id }
            if (key.labelOverride.isNullOrBlank()) applyActionIcon(button, key.action)
        }
    }
    private fun enqueueCommand(command: KeyAction) {
        val generation = epoch
        // This barrier follows earlier stroke recognition tasks on the same worker.
        worker.execute {
            main.post {
                if (epoch == generation && !isEditing && inputAllowed() && isShown) onCommand(command)
            }
        }
    }
    private fun applyActionIcon(button: Button, action: KeyAction) {
        val pad = session.activePad()
        val icon = when (action) {
            KeyAction.ShiftKey -> if (pad.shift) com.kazumaproject.core.R.drawable.shift_fill_24px else com.kazumaproject.core.R.drawable.shift_24px
            KeyAction.CapLockKey -> if (pad.uppercase) com.kazumaproject.core.R.drawable.caps_lock else com.kazumaproject.core.R.drawable.caps_lock_outline
            KeyAction.SwitchDirectMode -> if (pad.direct) com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px else com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px
            KeyAction.SwitchRomajiEnglish -> if (pad.romajiOverride ?: sets.first { it.id == pad.setId }.isRomaji)
                com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px else com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px
            else -> KeyActionMapper.iconResIdForAction(action)
        }
        icon?.let { button.text = ""; setIcon(button, it) }
    }
    private fun setIcon(button: Button, resource: Int) {
        val drawable = context.getDrawable(resource)?.mutate() ?: return
        DrawableCompat.setTint(drawable, fg)
        drawable.setBounds(0, 0, dp(24), dp(24))
        button.setCompoundDrawables(null, drawable, null, null)
        button.gravity = Gravity.CENTER
    }
    private fun button(label: String) = object : Button(context) {
        override fun onDraw(canvas: android.graphics.Canvas) {
            val icon = compoundDrawables[1]
            if (text.isEmpty() && icon != null) {
                canvas.save()
                canvas.translate((width - icon.bounds.width()) / 2f, (height - icon.bounds.height()) / 2f)
                icon.draw(canvas)
                canvas.restore()
            } else super.onDraw(canvas)
        }
    }.apply {
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
        if (visibility != VISIBLE && childCount > 0) { cancelInput(); dialog?.dismiss(); dialog = null; popup?.dismiss(); popup = null }
    }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (visibility != VISIBLE && childCount > 0) { cancelInput(); dialog?.dismiss(); dialog = null; popup?.dismiss(); popup = null } }
    override fun onDetachedFromWindow() { cancelInput(); dialog?.dismiss(); dialog = null; popup?.dismiss(); popup = null; main.removeCallbacksAndMessages(null); super.onDetachedFromWindow() }
    companion object {
        // One queue preserves the order of strokes from both pads.
        private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "gesture-recognition").apply { isDaemon = true } }
    }
}
