package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.google.android.material.R
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.custom_keyboard.controller.CrossFlickInputController
import com.kazumaproject.custom_keyboard.controller.FlickInputController
import com.kazumaproject.custom_keyboard.controller.PetalFlickInputController
import com.kazumaproject.custom_keyboard.controller.PopupPosition
import com.kazumaproject.custom_keyboard.controller.StandardFlickInputController
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import kotlin.math.pow
import kotlin.math.sqrt

class FlickKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onKey(text: String, isFlick: Boolean)
        fun onAction(action: KeyAction)
        fun onActionLongPress(action: KeyAction)
        fun onActionUpAfterLongPress(action: KeyAction)
        fun onFlickDirectionChanged(direction: FlickDirection)
        fun onFlickActionLongPress(action: KeyAction)
        fun onFlickActionUpAfterLongPress(action: KeyAction)
    }

    private var listener: OnKeyboardActionListener? = null
    private val flickControllers = mutableListOf<FlickInputController>()
    private val crossFlickControllers = mutableListOf<CrossFlickInputController>()
    private val standardFlickControllers = mutableListOf<StandardFlickInputController>()
    private val petalFlickControllers = mutableListOf<PetalFlickInputController>()

    private val hitRect = Rect()

    private var flickSensitivity: Int = 100

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    fun setFlickSensitivityValue(sensitivity: Int) {
        flickSensitivity = sensitivity
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setKeyboard(layout: KeyboardLayout) {
        this.removeAllViews()
        flickControllers.forEach { it.cancel() }
        flickControllers.clear()
        crossFlickControllers.forEach { it.cancel() }
        crossFlickControllers.clear()
        standardFlickControllers.forEach { it.cancel() }
        standardFlickControllers.clear()
        petalFlickControllers.forEach { it.cancel() }
        petalFlickControllers.clear()

        this.columnCount = layout.columnCount
        this.rowCount = layout.rowCount
        this.isFocusable = false

        layout.keys.forEach { keyData ->
            // ▼ 変更点1: マージン値をピクセル単位のインセット値として定義
            val (leftInset, topInset, rightInset, bottomInset) = if (keyData.isSpecialKey) {
                listOf(6, 12, 6, 6)
            } else {
                listOf(6, 9, 6, 9)
            }

            val isDarkTheme = context.isDarkThemeOn()
            val keyView: View = if (keyData.isSpecialKey && keyData.drawableResId != null) {
                AppCompatImageButton(context).apply {
                    isFocusable = false
                    elevation = 2f
                    setImageResource(keyData.drawableResId)
                    contentDescription = keyData.label

                    // ▼ 変更点2: 背景に InsetDrawable を使用
                    val originalBg = ContextCompat.getDrawable(
                        context,
                        if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                    )
                    val insetBg = android.graphics.drawable.InsetDrawable(
                        originalBg, leftInset, topInset, rightInset, bottomInset
                    )
                    background = insetBg

                    if (keyData.isHiLighted) {
                        isPressed = true
                    }
                }
            } else {
                AutoSizeButton(context).apply {
                    isFocusable = false
                    isAllCaps = false
                    if (keyData.label.contains("\n")) {
                        val parts = keyData.label.split("\n", limit = 2)
                        val primaryText = parts[0]
                        val secondaryText = parts.getOrNull(1) ?: ""
                        val spannable = SpannableString(keyData.label)
                        spannable.setSpan(
                            AbsoluteSizeSpan(spToPx(16f)),
                            0,
                            primaryText.length,
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        )
                        if (secondaryText.isNotEmpty()) {
                            spannable.setSpan(
                                AbsoluteSizeSpan(spToPx(10f)),
                                primaryText.length + 1,
                                keyData.label.length,
                                Spannable.SPAN_INCLUSIVE_INCLUSIVE
                            )
                        }
                        this.maxLines = 2
                        this.setLineSpacing(0f, 0.9f)
                        this.setPadding(0, dpToPx(4), 0, dpToPx(4))
                        this.gravity = Gravity.CENTER
                        this.text = spannable
                    } else {
                        text = keyData.label
                        gravity = Gravity.CENTER
                    }

                    val originalBg: android.graphics.drawable.Drawable? =
                        if (keyData.isSpecialKey) {
                            elevation = 2f
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                            ContextCompat.getDrawable(
                                context,
                                if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                            )
                        } else if (keyData.keyType != KeyType.STANDARD_FLICK) {
                            ContextCompat.getDrawable(
                                context,
                                if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_center_bg_material else com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light
                            )
                        } else {
                            null
                        }

                    originalBg?.let {
                        val insetBg = android.graphics.drawable.InsetDrawable(
                            it, leftInset, topInset, rightInset, bottomInset
                        )
                        background = insetBg
                    }
                }
            }

            val params = LayoutParams().apply {
                rowSpec = spec(keyData.row, keyData.rowSpan, FILL, 1f)
                columnSpec = spec(keyData.column, keyData.colSpan, FILL, 1f)
                width = 0
                height = 0
                elevation = 2f
            }
            keyView.layoutParams = params

            // The rest of this method (the `when` block) remains unchanged
            when (keyData.keyType) {
                KeyType.CIRCULAR_FLICK -> {
                    val flickKeyMapsList = layout.flickKeyMaps[keyData.label]
                    if (!flickKeyMapsList.isNullOrEmpty()) {
                        val controller = FlickInputController(context).apply {
                            val secondaryColor =
                                context.getColorFromAttr(R.attr.colorSecondaryContainer)
                            val surfaceContainerLow =
                                context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                            val surfaceContainerHighest =
                                context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                            val textColor =
                                context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)
                            val dynamicColorTheme = FlickPopupColorTheme(
                                segmentColor = surfaceContainerLow,
                                segmentHighlightGradientStartColor = secondaryColor,
                                segmentHighlightGradientEndColor = secondaryColor,
                                centerGradientStartColor = surfaceContainerHighest,
                                centerGradientEndColor = surfaceContainerLow,
                                centerHighlightGradientStartColor = secondaryColor,
                                centerHighlightGradientEndColor = secondaryColor,
                                separatorColor = textColor,
                                textColor = textColor
                            )
                            setPopupColors(dynamicColorTheme)
                            this.listener = object : FlickInputController.FlickListener {
                                override fun onStateChanged(
                                    view: View, newMap: Map<FlickDirection, String>
                                ) {
                                }

                                override fun onFlick(direction: FlickDirection, character: String) {
                                    if (character.isNotEmpty()) {
                                        this@FlickKeyboardView.listener?.onKey(
                                            text = character,
                                            isFlick = direction != FlickDirection.TAP
                                        )
                                    }
                                }

                                override fun onFlickDirectionChanged(newDirection: FlickDirection) {
                                    this@FlickKeyboardView.listener?.onFlickDirectionChanged(
                                        newDirection
                                    )
                                }
                            }
                            setPopupPosition(PopupPosition.CENTER)
                            val stringMaps = flickKeyMapsList.map { actionMap ->
                                actionMap.mapValues { (_, flickAction) ->
                                    (flickAction as? FlickAction.Input)?.char ?: ""
                                }
                            }
                            attach(keyView, stringMaps)
                            val scaleFactor = 1.4f
                            val newCenter = 64f * scaleFactor
                            val newOrbit = 170f * scaleFactor
                            val newTextSize = 55f * scaleFactor
                            setPopupViewSize(
                                center = newCenter,
                                target = newOrbit,
                                orbit = newOrbit,
                                textSize = newTextSize
                            )
                        }
                        flickControllers.add(controller)
                    }
                }

                KeyType.CROSS_FLICK -> {
                    val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                    if (flickActionMap != null) {
                        val controller = CrossFlickInputController(context).apply {
                            this.listener = object : CrossFlickInputController.CrossFlickListener {
                                override fun onFlick(flickAction: FlickAction) {
                                    when (flickAction) {
                                        is FlickAction.Input -> this@FlickKeyboardView.listener?.onKey(
                                            flickAction.char, isFlick = true
                                        )

                                        is FlickAction.Action -> this@FlickKeyboardView.listener?.onAction(
                                            flickAction.action
                                        )
                                    }
                                }

                                override fun onFlickLongPress(flickAction: FlickAction) {
                                    when (flickAction) {
                                        is FlickAction.Action -> this@FlickKeyboardView.listener?.onFlickActionLongPress(
                                            flickAction.action
                                        )

                                        is FlickAction.Input -> {}
                                    }
                                }

                                override fun onFlickUpAfterLongPress(flickAction: FlickAction) {
                                    when (flickAction) {
                                        is FlickAction.Action -> this@FlickKeyboardView.listener?.onFlickActionUpAfterLongPress(
                                            flickAction.action
                                        )

                                        is FlickAction.Input -> {}
                                    }
                                }
                            }
                            attach(keyView, flickActionMap)
                        }
                        crossFlickControllers.add(controller)
                    }
                }

                KeyType.STANDARD_FLICK -> {
                    val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                    if (flickActionMap != null && keyView is Button) {
                        val label = keyData.label

                        val keyBaseColor =
                            if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                R.attr.colorSurface
                            )
                        val keyHighlightColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val keyTextColor = context.getColorFromAttr(R.attr.colorOnSurface)

                        val segmentedDrawable = SegmentedBackgroundDrawable(
                            label = label,
                            baseColor = keyBaseColor,
                            highlightColor = keyHighlightColor,
                            textColor = keyTextColor,
                            cornerRadius = 20f
                        )
                        keyView.background = segmentedDrawable
                        keyView.setTextColor(Color.TRANSPARENT)

                        val controller = StandardFlickInputController(context).apply {
                            this.listener =
                                object : StandardFlickInputController.StandardFlickListener {
                                    override fun onFlick(character: String) {
                                        this@FlickKeyboardView.listener?.onKey(
                                            character, isFlick = true
                                        )
                                    }
                                }

                            val popupBackgroundColor =
                                if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                    R.attr.colorSurface
                                )
                            val popupTextColor = context.getColorFromAttr(R.attr.colorOnSurface)
                            val popupStrokeColor = context.getColorFromAttr(R.attr.colorOutline)

                            val dynamicColorTheme = FlickPopupColorTheme(
                                segmentHighlightGradientStartColor = popupBackgroundColor,
                                textColor = popupTextColor,
                                separatorColor = popupStrokeColor,
                                segmentColor = 0,
                                segmentHighlightGradientEndColor = 0,
                                centerGradientStartColor = 0,
                                centerGradientEndColor = 0,
                                centerHighlightGradientStartColor = 0,
                                centerHighlightGradientEndColor = 0
                            )
                            setPopupColors(dynamicColorTheme)

                            val stringMap = flickActionMap.mapValues { (_, flickAction) ->
                                (flickAction as? FlickAction.Input)?.char ?: ""
                            }
                            attach(keyView, stringMap, segmentedDrawable)
                        }
                        standardFlickControllers.add(controller)
                    }
                }

                KeyType.PETAL_FLICK -> {
                    val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                    if (flickActionMap != null) {
                        val controller = PetalFlickInputController(
                            context, flickSensitivity
                        ).apply {
                            val secondaryColor =
                                context.getColorFromAttr(R.attr.colorSecondaryContainer)
                            val surfaceContainerLow =
                                context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                            val surfaceContainerHighest =
                                if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                    R.attr.colorSurface
                                )
                            val textColor =
                                context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)

                            val dynamicColorTheme = FlickPopupColorTheme(
                                segmentColor = surfaceContainerHighest,
                                segmentHighlightGradientStartColor = secondaryColor,
                                segmentHighlightGradientEndColor = secondaryColor,
                                centerGradientStartColor = surfaceContainerHighest,
                                centerGradientEndColor = surfaceContainerLow,
                                centerHighlightGradientStartColor = secondaryColor,
                                centerHighlightGradientEndColor = secondaryColor,
                                separatorColor = textColor,
                                textColor = textColor
                            )
                            setPopupColors(dynamicColorTheme)

                            elevation = 1f

                            this.listener = object : PetalFlickInputController.PetalFlickListener {
                                override fun onFlick(character: String, isFlick: Boolean) {
                                    this@FlickKeyboardView.listener?.onKey(
                                        character, isFlick = isFlick
                                    )
                                }
                            }
                            val stringMap = flickActionMap.mapValues { (_, flickAction) ->
                                (flickAction as? FlickAction.Input)?.char ?: ""
                            }
                            attach(keyView, stringMap)
                        }
                        petalFlickControllers.add(controller)
                    }
                }

                KeyType.NORMAL -> {
                    keyData.action?.let { action ->
                        Log.d("NORMAL", "$action")
                        var isLongPressTriggered = false
                        keyView.setOnClickListener {
                            this@FlickKeyboardView.listener?.onAction(
                                action
                            )
                        }
                        keyView.setOnLongClickListener {
                            isLongPressTriggered =
                                true; this@FlickKeyboardView.listener?.onActionLongPress(action); true
                        }
                        keyView.setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                                if (isLongPressTriggered) {
                                    this@FlickKeyboardView.listener?.onActionUpAfterLongPress(action); isLongPressTriggered =
                                        false
                                }
                            }
                            false
                        }
                    }
                }
            }
            this.addView(keyView)
        }
    }

    // onTouchEvent の前に、ポインターIDとViewをマッピングするためのプロパティを追加します。
    private val motionTargets = mutableMapOf<Int, View>()
    private val pointerDownTime = mutableMapOf<Int, Long>()
    private val TAG = "FlickKeyboardViewTouch"

    private fun findTargetView(x: Float, y: Float): View? {
        // まず、キーの矩形内に直接ヒットしたかチェック
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.getHitRect(hitRect)
            if (hitRect.contains(x.toInt(), y.toInt())) {
                return child
            }
        }

        // 直接ヒットしなかった場合（マージンなどをタッチした場合）、最も近いキーを探す
        var nearestChild: View? = null
        var minDistance = Double.MAX_VALUE

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childCenterX = child.left + child.width / 2f
            val childCenterY = child.top + child.height / 2f
            val distance = sqrt((x - childCenterX).pow(2) + (y - childCenterY).pow(2))

            if (distance < minDistance) {
                minDistance = distance.toDouble()
                nearestChild = child
            }
        }
        return nearestChild
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        Log.d(TAG, "onInterceptTouchEvent: ${MotionEvent.actionToString(action)} $")

        // 最初の指が触れた瞬間に true を返すことで、
        // この後のすべてのタッチイベント(MOVE, UP, POINTER_DOWNなど)を
        // このビューの onTouchEvent で処理することを決定する。
        if (action == MotionEvent.ACTION_DOWN) {
            Log.d(TAG, "-> Intercepting gesture from ACTION_DOWN. Returning true.")
            return true
        }

        // すでにインターセプトしている場合は、子には渡さない
        if (motionTargets.isNotEmpty()) {
            return true
        }

        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // 最初の指が触れた。すべての状態をクリアして開始。
                motionTargets.clear()
                pointerDownTime.clear()

                // この指の情報を保存
                pointerDownTime[pointerId] = event.downTime
                val x = event.x
                val y = event.y
                val targetView = findTargetView(x, y)

                targetView?.let {
                    motionTargets[pointerId] = it

                    // ★★★ 修正箇所 ★★★
                    // システムのイベントをコピーするのではなく、2本指目と同様に
                    // クリーンなACTION_DOWNイベントを自作する。
                    val newEvent = MotionEvent.obtain(
                        event.downTime,    // downTime
                        event.eventTime,   // eventTime
                        MotionEvent.ACTION_DOWN, // action
                        x,                 // x
                        y,                 // y
                        event.metaState    // metaState
                    )
                    // ★★★ ここまで ★★★

                    newEvent.offsetLocation(-it.left.toFloat(), -it.top.toFloat())
                    it.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 新しい指が追加された
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                // 新しい指の情報を保存
                pointerDownTime[pointerId] = event.eventTime // このイベント時刻を「押下開始時刻」とする
                val targetView = findTargetView(x, y)

                targetView?.let {
                    motionTargets[pointerId] = it
                    // この指専用の「ACTION_DOWN」イベントを自作する
                    val newEvent = MotionEvent.obtain(
                        pointerDownTime[pointerId]!!, // downTime
                        event.eventTime,              // eventTime
                        MotionEvent.ACTION_DOWN,      // action (新しいジェスチャーの開始として偽装)
                        x,                            // x
                        y,                            // y
                        event.metaState               // metaState
                    )
                    // 自作したきれいなDOWNイベントをターゲットにディスパッチ
                    newEvent.offsetLocation(-it.left.toFloat(), -it.top.toFloat())
                    it.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // 指が動いた。追跡中のすべての指に対して、それぞれ専用のMOVEイベントを作成する
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val target = motionTargets[pId]
                    val downTime = pointerDownTime[pId]

                    if (target != null && downTime != null) {
                        val x = event.getX(i)
                        val y = event.getY(i)

                        // この指専用の「ACTION_MOVE」イベントを自作
                        val newEvent = MotionEvent.obtain(
                            downTime,
                            event.eventTime,
                            MotionEvent.ACTION_MOVE,
                            x,
                            y,
                            event.metaState
                        )
                        newEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                        target.dispatchTouchEvent(newEvent)
                        newEvent.recycle()
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 最初の指以外の指が離された
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                motionTargets[pointerId]?.let { target ->
                    val downTime = pointerDownTime[pointerId]!!
                    // この指専用の「ACTION_UP」イベントを自作
                    val newEvent = MotionEvent.obtain(
                        downTime, event.eventTime, MotionEvent.ACTION_UP, // ジェスチャーの終了として偽装
                        x, y, event.metaState
                    )
                    newEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                    target.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }

                // 離された指の情報を削除
                motionTargets.remove(pointerId)
                pointerDownTime.remove(pointerId)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 最後の指が離された、またはジェスチャーがキャンセルされた
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val actionToDispatch =
                    if (action == MotionEvent.ACTION_UP) MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL

                motionTargets[pointerId]?.let { target ->
                    val downTime = pointerDownTime[pointerId]!!
                    // この指専用のUP/CANCELイベントを自作
                    val newEvent = MotionEvent.obtain(
                        downTime, event.eventTime, actionToDispatch, x, y, event.metaState
                    )
                    newEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                    target.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }

                // すべての状態をクリア
                motionTargets.clear()
                pointerDownTime.clear()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flickControllers.forEach { it.cancel() }
        crossFlickControllers.forEach { it.cancel() }
        standardFlickControllers.forEach { it.cancel() }
        petalFlickControllers.forEach { it.cancel() }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(
            attrRes, typedValue, true
        )
        return ContextCompat.getColor(this, typedValue.resourceId)
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
            .toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
