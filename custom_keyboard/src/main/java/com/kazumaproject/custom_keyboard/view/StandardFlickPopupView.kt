package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.toColorInt
import androidx.core.text.inSpans
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import kotlin.math.roundToInt

/**
 * フリック入力時に表示される単一の円形ポップアップビュー。
 * 内部のテキストはコントローラーによって動的に更新される。
 */
class StandardFlickPopupView(context: Context) : AppCompatTextView(context) {

    val viewSize = dpToPx(72) // ポップアップの直径
    private val backgroundDrawable: GradientDrawable = createBackground()

    // 追加: テーマ保持（setPopupColors のため）
    private var colorTheme: FlickPopupColorTheme? = null

    // 追加: 方向（方向別テーマがある実装にも対応）
    private var flickDirection: FlickDirection = FlickDirection.TAP

    // フォールバック用（テーマから色が取れない場合）
    private var lastBackgroundColor: Int = "#FFFFFF".toColorInt()
    private var lastTextColor: Int = Color.BLACK
    private var lastStrokeColor: Int = Color.LTGRAY

    private class YOffsetSpan(private val yOffset: Int) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return paint.measureText(text, start, end).roundToInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            canvas.drawText(text, start, end, x, (y + yOffset).toFloat(), paint)
        }
    }

    init {
        width = viewSize
        height = viewSize
        gravity = Gravity.CENTER
        setTextColor(lastTextColor)
        maxLines = 4
        setLineSpacing(0f, 0.8f)
        background = backgroundDrawable
    }

    /**
     * 既存: 直接色指定
     */
    fun setColors(backgroundColor: Int, textColor: Int, strokeColor: Int) {
        lastBackgroundColor = backgroundColor
        lastTextColor = textColor
        lastStrokeColor = strokeColor

        setTextColor(textColor)
        backgroundDrawable.setColor(backgroundColor)
        backgroundDrawable.setStroke(dpToPx(1), strokeColor)
        invalidate()
    }

    /**
     * 追加: GridFlickInputController と同じスタイルでテーマを渡せるようにする
     *
     * FlickPopupColorTheme の実装詳細（プロパティ名/関数名）が不明でもコンパイルできるよう、
     * リフレクションで以下を優先的に探索します:
     * - 一律色: backgroundColor / textColor / strokeColor（または類似名）
     * - 方向別: getBackgroundColor(FlickDirection) 等（または類似名）
     *
     * 取得できなければ現在の色（last*）を維持します。
     */
    fun setPopupColors(theme: FlickPopupColorTheme) {
        colorTheme = theme
        applyTheme(theme, flickDirection)
    }

    /**
     * 任意: 方向をセット（DirectionalKeyPopupView と同じ使い方ができる）
     */
    fun setFlickDirection(direction: FlickDirection) {
        flickDirection = direction
        colorTheme?.let { applyTheme(it, direction) }
    }

    fun updateText(text: String?) {
        if (text.isNullOrEmpty()) {
            this.text = ""
            return
        }
        this.text = createSpannableText(text)
    }

    fun updateMultiCharText(characters: Map<FlickDirection, String>) {
        val up = characters[FlickDirection.UP] ?: ""
        val left = characters[FlickDirection.UP_LEFT_FAR] ?: ""
        val tap = characters[FlickDirection.TAP] ?: ""
        val right = characters[FlickDirection.UP_RIGHT_FAR] ?: ""
        val down = characters[FlickDirection.DOWN] ?: ""

        val tapSize = 19f
        val sideSize = 11f
        val verticalOffset = spToPx(-1.5f)
        val transparent = ForegroundColorSpan(Color.TRANSPARENT)

        val builder = SpannableStringBuilder()

        // Line 1: UP
        if (up.isNotEmpty()) {
            builder.inSpans(AbsoluteSizeSpan(spToPx(sideSize))) { append(up) }
        } else {
            builder.inSpans(AbsoluteSizeSpan(spToPx(sideSize)), transparent) { append(" ") }
        }
        builder.append("\n")

        // Line 2: LEFT
        if (left.isNotEmpty()) {
            builder.inSpans(
                AbsoluteSizeSpan(spToPx(sideSize)),
                YOffsetSpan(verticalOffset)
            ) { append("$left  ") } // Two spaces
        } else {
            builder.inSpans(
                AbsoluteSizeSpan(spToPx(sideSize)),
                YOffsetSpan(verticalOffset),
                transparent
            ) { append("   ") } // Three spaces
        }

        // Line 2: TAP
        builder.inSpans(
            AbsoluteSizeSpan(spToPx(tapSize)),
            StyleSpan(Typeface.BOLD)
        ) { append(tap) }

        // Line 2: RIGHT
        if (right.isNotEmpty()) {
            builder.inSpans(
                AbsoluteSizeSpan(spToPx(sideSize)),
                YOffsetSpan(verticalOffset)
            ) { append("  $right") } // Two spaces
        } else {
            builder.inSpans(
                AbsoluteSizeSpan(spToPx(sideSize)),
                YOffsetSpan(verticalOffset),
                transparent
            ) { append("   ") } // Three spaces
        }
        builder.append("\n")

        // Spacer Line
        builder.inSpans(AbsoluteSizeSpan(spToPx(4f)), transparent) { append(" \n") }

        // Line 3: DOWN
        if (down.isNotEmpty()) {
            builder.inSpans(AbsoluteSizeSpan(spToPx(sideSize))) { append(down) }
        } else {
            builder.inSpans(AbsoluteSizeSpan(spToPx(sideSize)), transparent) { append(" ") }
        }

        this.text = builder
    }

    private fun createSpannableText(text: String): SpannableString {
        val spannable = SpannableString(text)
        if (text.contains("\n")) {
            val parts = text.split("\n", limit = 2)
            val primaryText = parts[0]
            spannable.setSpan(
                AbsoluteSizeSpan(spToPx(26f)),
                0,
                primaryText.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            spannable.setSpan(
                AbsoluteSizeSpan(spToPx(14f)),
                primaryText.length,
                text.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        } else {
            spannable.setSpan(
                AbsoluteSizeSpan(spToPx(26f)),
                0,
                text.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        return spannable
    }

    private fun createBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor("#FFFFFF".toColorInt())
            setStroke(dpToPx(1), Color.LTGRAY)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            resources.displayMetrics
        ).toInt()
    }

    // -------------------------
    // Theme application (reflection-based, compile-safe)
    // -------------------------

    private fun applyTheme(theme: FlickPopupColorTheme, direction: FlickDirection) {
        val bg = resolveDirectionalColor(
            theme = theme,
            direction = direction,
            directionalMethodNames = arrayOf(
                "getBackgroundColor",
                "backgroundColorFor",
                "backgroundColor",
                "bgColorFor",
                "bgColor"
            ),
            flatPropertyNames = arrayOf(
                "backgroundColor", "bgColor", "popupBackgroundColor", "normalBackgroundColor"
            )
        ) ?: lastBackgroundColor

        val fg = resolveDirectionalColor(
            theme = theme,
            direction = direction,
            directionalMethodNames = arrayOf(
                "getTextColor", "textColorFor", "textColor", "fgColorFor", "fgColor"
            ),
            flatPropertyNames = arrayOf(
                "textColor", "fgColor", "popupTextColor", "normalTextColor"
            )
        ) ?: lastTextColor

        val stroke = resolveDirectionalColor(
            theme = theme,
            direction = direction,
            directionalMethodNames = arrayOf(
                "getStrokeColor", "strokeColorFor", "strokeColor", "borderColorFor", "borderColor"
            ),
            flatPropertyNames = arrayOf(
                "strokeColor",
                "borderColor",
                "outlineColor",
                "popupStrokeColor",
                "normalStrokeColor"
            )
        ) ?: lastStrokeColor

        setColors(bg, fg, stroke)
    }

    private fun resolveDirectionalColor(
        theme: Any,
        direction: FlickDirection,
        directionalMethodNames: Array<String>,
        flatPropertyNames: Array<String>
    ): Int? {
        // 1) Try direction-aware methods: method(FlickDirection) -> Int
        run {
            val cls = theme.javaClass
            val methods = cls.methods + cls.declaredMethods
            for (name in directionalMethodNames) {
                val m = methods.firstOrNull { method ->
                    method.name == name &&
                            method.parameterTypes.size == 1 &&
                            method.returnType == Int::class.javaPrimitiveType &&
                            method.parameterTypes[0].isAssignableFrom(FlickDirection::class.java)
                } ?: methods.firstOrNull { method ->
                    // 一部実装で引数型が Enum/Any になっているケースも救済
                    method.name == name &&
                            method.parameterTypes.size == 1 &&
                            method.returnType == Int::class.javaPrimitiveType
                }

                if (m != null) {
                    try {
                        m.isAccessible = true
                        val value =
                            if (m.parameterTypes[0].isAssignableFrom(FlickDirection::class.java)) {
                                m.invoke(theme, direction)
                            } else {
                                // 引数型が厳密一致でない場合でもとりあえず渡してみる
                                m.invoke(theme, direction)
                            }
                        if (value is Int) return value
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
            }
        }

        // 2) Try flat properties/fields: theme.backgroundColor -> Int
        for (prop in flatPropertyNames) {
            readIntMember(theme, prop)?.let { return it }
        }

        return null
    }

    private fun readIntMember(obj: Any, name: String): Int? {
        // a) field
        try {
            val f = obj.javaClass.declaredFields.firstOrNull { it.name == name }
            if (f != null) {
                f.isAccessible = true
                val v = f.get(obj)
                if (v is Int) return v
            }
        } catch (_: Throwable) {
            // ignore
        }

        // b) getter method: getXxx() -> Int, xxx() -> Int
        val getterCandidates = listOf(
            name,
            "get" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        )

        for (methodName in getterCandidates) {
            try {
                val m = (obj.javaClass.methods + obj.javaClass.declaredMethods)
                    .firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() && it.returnType == Int::class.javaPrimitiveType }
                if (m != null) {
                    m.isAccessible = true
                    val v = m.invoke(obj)
                    if (v is Int) return v
                }
            } catch (_: Throwable) {
                // ignore
            }
        }

        return null
    }
}
