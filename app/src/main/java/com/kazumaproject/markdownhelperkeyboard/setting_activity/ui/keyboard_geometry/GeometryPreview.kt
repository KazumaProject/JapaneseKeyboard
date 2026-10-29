package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.GridLayout
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.annotation.VisibleForTesting
import com.google.android.material.R as MaterialR
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.geometry.KeyboardCurveHand
import com.kazumaproject.core.ui.geometry.KeyboardCurveDirection
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.ui.skin.SkinKeyRole
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryCandidatePlacement
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryKeyboard
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateSizing
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.verticalCandidateText
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.IndependentKeyboardSize
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyIconResolver
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyVisualStyleResolver
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.gojuon_keyboard.extenstions.setGojuonKeyTextJapanese
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.ModeKeySizing
import com.kazumaproject.gojuon_keyboard.R as GojuonR
import com.kazumaproject.qwerty_keyboard.R as QwertyR
import com.kazumaproject.tenkey.R as TenkeyR
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A display-only keyboard. It creates only ordinary, unattached buttons and never creates an
 * IME keyboard view, input controller, or input connection. The original XML is read as a
 * presentation resource; the three input-aware widgets are replaced by passive equivalents.
 */
internal enum class PreviewKeyboard { QWERTY, TENKEY, SUMIRE, GOJUON, CUSTOM }

internal class GeometryPreview(context: Context, private val keyboard: PreviewKeyboard) : View(context) {
    constructor(context: Context, keyboard: GeometryKeyboard) : this(context, when (keyboard) {
        GeometryKeyboard.QWERTY -> PreviewKeyboard.QWERTY
        GeometryKeyboard.TENKEY -> PreviewKeyboard.TENKEY
        GeometryKeyboard.SUMIRE -> PreviewKeyboard.SUMIRE
    })
    private var value = KeyboardGeometryPreference()
    private var landscape = false
    private var keyboardWidthPercent = 100
    private var alignEnd = true
    private var sizeOverride: IndependentKeyboardSize? = null
    private var customLayout: KeyboardLayout? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scene: PreviewScene? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun update(value: KeyboardGeometryPreference, landscape: Boolean, widthPercent: Int, alignEnd: Boolean,
        sizeOverride: IndependentKeyboardSize? = null, customLayout: KeyboardLayout? = null) {
        this.value = value
        this.landscape = landscape
        this.keyboardWidthPercent = widthPercent
        this.alignEnd = alignEnd
        this.sizeOverride = sizeOverride
        this.customLayout = customLayout
        scene = null
        requestLayout()
        invalidate()
    }

    @VisibleForTesting
    internal fun snapshotForTesting(): GeometryPreviewSnapshot {
        val model = requireNotNull(scene) { "Measure the preview before inspecting it" }
        return GeometryPreviewSnapshot(model.screenWidth, model.bodyHeight, model.bodyLeft,
            model.bodyWidth, model.items.map {
                GeometryPreviewKeyFrame(it.view.id, (it.view as? TextView)?.text?.toString(),
                    it.x, it.y, it.angle, it.halfWidth(), it.halfHeight())
            })
    }

    /** Coordinates in this view, used solely by the settings resize handles. */
    internal fun bodyBounds(): RectF? = scene?.let {
        val scale = min(width / it.screenWidth, height / it.height)
        RectF(it.bodyLeft * scale, it.chromeHeight * scale,
            (it.bodyLeft + it.bodyWidth) * scale, (it.chromeHeight + it.bodyHeight) * scale)
    }

    /** The resize and move controls cover the keyboard together with its side candidates. */
    internal fun adjustmentBounds(): RectF? = scene?.let {
        val scale = min(width / it.screenWidth, height / it.height)
        RectF(it.surfaceLeft * scale, it.chromeHeight * scale,
            (it.surfaceLeft + it.surfaceWidth) * scale, (it.chromeHeight + it.bodyHeight) * scale)
    }

    internal fun surfaceWidth(): Float? = scene?.let { it.surfaceWidth * min(width / it.screenWidth, height / it.height) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val model = buildScene()
        scene = model
        // Scale both axes together: the preview shows the same width/height ratio as the IME.
        val desiredHeight = ceil(availableWidth * model.height / model.screenWidth).toInt().coerceAtLeast(1)
        setMeasuredDimension(resolveSize(availableWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val model = scene ?: buildScene().also { scene = it }
        val scale = min(width / model.screenWidth, height / model.height)
        canvas.save()
        canvas.scale(scale, scale)
        val bg = RectF(model.surfaceLeft, 0f, model.surfaceLeft + model.surfaceWidth, model.chromeHeight + model.bodyHeight)
        paint.color = model.backgroundColor
        val path = Path()
        val radius = if (AppPreference.keyboard_corner_round_preference) {
            AppPreference.keyboard_corner_radius_dp_preference.coerceIn(0, 64) * resources.displayMetrics.density
        } else 0f
        val radii = floatArrayOf(
            if (AppPreference.keyboard_corner_top_left_preference) radius else 0f,
            if (AppPreference.keyboard_corner_top_left_preference) radius else 0f,
            if (AppPreference.keyboard_corner_top_right_preference) radius else 0f,
            if (AppPreference.keyboard_corner_top_right_preference) radius else 0f,
            if (AppPreference.keyboard_corner_bottom_right_preference) radius else 0f,
            if (AppPreference.keyboard_corner_bottom_right_preference) radius else 0f,
            if (AppPreference.keyboard_corner_bottom_left_preference) radius else 0f,
            if (AppPreference.keyboard_corner_bottom_left_preference) radius else 0f,
        )
        path.addRoundRect(bg, radii, Path.Direction.CW)
        val skin = KeyboardSkinRegistry.find(AppPreference.keyboardSkin)
        if (skin == null) {
            canvas.drawPath(path, paint)
        } else {
            canvas.save()
            if (skin.id == KeyboardSkinId.CUPERTINO_CLASSIC && AppPreference.keyboard_corner_round_preference) canvas.clipPath(path)
            skin.keyboardDrawable(resources).apply {
                setBounds(bg.left.roundToInt(), bg.top.roundToInt(), bg.right.roundToInt(), bg.bottom.roundToInt())
                draw(canvas)
            }
            canvas.restore()
        }
        model.items.forEach { item ->
            canvas.save()
            canvas.translate(model.bodyLeft + item.x, model.chromeHeight + item.y)
            canvas.rotate(item.angle)
            canvas.scale(item.scaleX, item.scaleY)
            canvas.translate(-item.view.width / 2f, -item.view.height / 2f)
            item.view.draw(canvas)
            canvas.restore()
        }
        paint.color = model.candidateTextColor
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            AppPreference.candidate_letter_size ?: 14f, resources.displayMetrics)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = KeyboardFontApplicator.processSnapshot.typeface
        val examples = listOf(
            context.getString(R.string.keyboard_geometry_example_today),
            context.getString(R.string.keyboard_geometry_example_hello),
            context.getString(R.string.keyboard_geometry_example_candidate)
        )
        if (model.candidateSideWidth > 0f) {
            val left = if (value.candidates == GeometryCandidatePlacement.CENTER) {
                model.bodyLeft + (model.bodyWidth - model.candidateSideWidth) / 2f
            } else if (value.candidates == GeometryCandidatePlacement.LEFT) {
                model.bodyLeft - model.candidateSideWidth
            } else model.bodyLeft + model.bodyWidth
            canvas.save()
            canvas.clipRect(left, model.chromeHeight, left + model.candidateSideWidth, model.chromeHeight + model.bodyHeight)
            if (value.candidates != GeometryCandidatePlacement.CENTER && value.sideCandidateVerticalText) {
                val density = resources.displayMetrics.density
                val columns = SideCandidateSizing.columnCount(model.candidateSideWidth.roundToInt(), density, paint.textSize)
                val cellWidth = model.candidateSideWidth / columns
                var top = model.chromeHeight
                for (row in examples.map { verticalCandidateText(it).split('\n') }.chunked(columns)) {
                    val itemHeight = row.maxOf { maxOf(48f * density, it.size * paint.fontSpacing) }
                    row.forEachIndexed { column, characters ->
                        val columnWidth = characters.maxOfOrNull { paint.measureText(it) } ?: 0f
                        val textHeight = characters.size * paint.fontSpacing
                        val cx = left + column * cellWidth + 5f * density + columnWidth / 2f
                        var baseline = top + (itemHeight - textHeight) / 2f - paint.fontMetrics.ascent
                        for (character in characters) {
                            canvas.drawText(character, cx, baseline, paint)
                            baseline += paint.fontSpacing
                        }
                    }
                    top += itemHeight + 8f * density
                }
            } else if (value.candidates == GeometryCandidatePlacement.CENTER) {
                val cx = left + model.candidateSideWidth / 2f
                examples.forEachIndexed { i, label ->
                    canvas.drawText(label, cx, model.chromeHeight + (i + 0.8f) * 42f * resources.displayMetrics.density, paint)
                }
            } else {
                val density = resources.displayMetrics.density
                paint.textAlign = Paint.Align.LEFT
                examples.forEachIndexed { i, label ->
                    val spaces = if (i == 0) 4 else if (label.length == 2) 2 else 1
                    val x = left + 5f * density + paint.measureText(" ".repeat(spaces))
                    canvas.drawText(label, x, model.chromeHeight + (i + 0.8f) * 32f * density, paint)
                }
                paint.textAlign = Paint.Align.CENTER
            }
            canvas.restore()
        } else {
            examples.forEachIndexed { i, label ->
                canvas.drawText(label, model.surfaceLeft + model.surfaceWidth * (i * .3f + .2f), model.chromeHeight * .6f, paint)
            }
        }
        canvas.restore()
    }

    private fun buildScene(): PreviewScene {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val screenWidth = (if (landscape) max(metrics.widthPixels, metrics.heightPixels) else min(metrics.widthPixels, metrics.heightPixels)).toFloat().coerceAtLeast(1f)
        val screenHeight = (if (landscape) min(metrics.widthPixels, metrics.heightPixels) else max(metrics.widthPixels, metrics.heightPixels)).toFloat()
        val qwerty = keyboard == PreviewKeyboard.QWERTY
        val heightDp = sizeOverride?.heightDp ?: when {
            qwerty && landscape -> AppPreference.qwerty_keyboard_height_landscape
            qwerty -> AppPreference.qwerty_keyboard_height
            landscape -> AppPreference.keyboard_height_landscape
            else -> AppPreference.keyboard_height
        } ?: 220
        val baseHeight = heightDp.coerceIn(100, 420) * density
        val chromeHeight = ((if (landscape) AppPreference.candidate_view_height_dp_landscape else AppPreference.candidate_view_height_dp) ?: 60).coerceIn(0, 200) * density
        val startDp = sizeOverride?.marginStartDp ?: when {
            qwerty && landscape -> AppPreference.qwerty_keyboard_margin_start_dp_landscape
            qwerty -> AppPreference.qwerty_keyboard_margin_start_dp
            landscape -> AppPreference.keyboard_margin_start_dp_landscape
            else -> AppPreference.keyboard_margin_start_dp
        } ?: 0
        val endDp = sizeOverride?.marginEndDp ?: when {
            qwerty && landscape -> AppPreference.qwerty_keyboard_margin_end_dp_landscape
            qwerty -> AppPreference.qwerty_keyboard_margin_end_dp
            landscape -> AppPreference.keyboard_margin_end_dp_landscape
            else -> AppPreference.keyboard_margin_end_dp
        } ?: 0
        val start = startDp.coerceAtLeast(0) * density
        val end = endDp.coerceAtLeast(0) * density
        val available = (screenWidth - start - end).coerceAtLeast(1f)
        val requested = min(available, screenWidth * keyboardWidthPercent / 100f)
        val side = if (keyboard in setOf(PreviewKeyboard.TENKEY, PreviewKeyboard.SUMIRE) && value.candidates != GeometryCandidatePlacement.TOP) {
            SideCandidateSizing.width(requested.roundToInt(), density, value.sideCandidateWidthPercent).toFloat()
        } else 0f
        val bodyWidth = (requested - side).coerceAtLeast(1f)
        val surfaceWidth = requested
        val surfaceLeft = if (!alignEnd) start else screenWidth - end - surfaceWidth
        val bodyLeft = when {
            side > 0f && value.candidates == GeometryCandidatePlacement.LEFT -> surfaceLeft + side
            else -> surfaceLeft
        }
        val previewContext = orientationContext()
        val keys = when (keyboard) {
            PreviewKeyboard.SUMIRE -> createSumireKeys(previewContext, bodyWidth, baseHeight)
            PreviewKeyboard.GOJUON -> createGojuonKeys(previewContext, bodyWidth, baseHeight)
            PreviewKeyboard.CUSTOM -> createCustomKeys(previewContext, bodyWidth, baseHeight, customLayout)
            else -> createXmlKeys(previewContext, bodyWidth, baseHeight, qwerty)
        }
        val gap = if (qwerty) min(bodyWidth * value.geometry.splitPercent / 100f, max(0f, bodyWidth - 240f * density)) else 0f
        val placed = PreviewGeometry.place(keys, bodyWidth, baseHeight, gap, value.geometry.curveDegrees, value.geometry.curveDirection,
            value.geometry.hand == KeyboardCurveHand.RIGHT, qwerty, value.geometry.autoHeight, (screenHeight - chromeHeight).coerceAtLeast(baseHeight))
        val colors = previewColors(previewContext)
        val candidateSide = when {
            side > 0f -> side
            qwerty && value.candidates == GeometryCandidatePlacement.CENTER && gap >= 96f * density -> gap
            else -> 0f
        }
        val bottomMargin = sizeOverride?.bottomMarginPx?.coerceAtLeast(0)?.toFloat() ?: 0f
        return PreviewScene(screenWidth, chromeHeight + placed.height + bottomMargin, bodyLeft, bodyWidth,
            chromeHeight, placed.height, surfaceLeft, surfaceWidth, candidateSide, colors.background, colors.candidateText, placed.items)
    }

    private fun orientationContext(): Context {
        val configuration = Configuration(resources.configuration).apply {
            orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        return ContextThemeWrapper(context.createConfigurationContext(configuration), 0).apply {
            theme.setTo(context.theme)
        }
    }

    private fun createXmlKeys(ctx: Context, width: Float, height: Float, qwerty: Boolean): List<PreviewItem> {
        val root = ConstraintLayout(ctx)
        PreviewLayoutInflater(ctx).inflate(if (qwerty) QwertyR.layout.qwerty_layout else TenkeyR.layout.keyboard_layout, root, true)
        if (qwerty) configureQwerty(root) else configureTenkey(root)
        root.measure(MeasureSpec.makeMeasureSpec(width.roundToInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height.roundToInt(), MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val qLeft = setOf(QwertyR.id.key_q, QwertyR.id.key_w, QwertyR.id.key_e, QwertyR.id.key_r, QwertyR.id.key_t,
            QwertyR.id.key_a, QwertyR.id.key_s, QwertyR.id.key_d, QwertyR.id.key_f, QwertyR.id.key_g,
            QwertyR.id.key_z, QwertyR.id.key_x, QwertyR.id.key_c, QwertyR.id.key_v,
            QwertyR.id.key_1, QwertyR.id.key_2, QwertyR.id.key_3, QwertyR.id.key_4, QwertyR.id.key_5)
        val qRight = setOf(QwertyR.id.key_y, QwertyR.id.key_u, QwertyR.id.key_i, QwertyR.id.key_o, QwertyR.id.key_p,
            QwertyR.id.key_h, QwertyR.id.key_j, QwertyR.id.key_k, QwertyR.id.key_l,
            QwertyR.id.key_b, QwertyR.id.key_n, QwertyR.id.key_m,
            QwertyR.id.key_6, QwertyR.id.key_7, QwertyR.id.key_8, QwertyR.id.key_9, QwertyR.id.key_0)
        val result = mutableListOf<PreviewItem>()
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.visibility != VISIBLE || child.width <= 0 || child.height <= 0 || child is androidx.constraintlayout.widget.Guideline) continue
            val rect = RectF(child.left.toFloat(), child.top.toFloat(), child.right.toFloat(), child.bottom.toFloat())
            if (qwerty && child.id == QwertyR.id.key_space && value.geometry.splitPercent > 0 && width > 240f * resources.displayMetrics.density) {
                result += PreviewItem(child, RectF(rect.left, rect.top, rect.centerX(), rect.bottom), PreviewGroup.LEFT)
                result += PreviewItem(child, RectF(rect.centerX(), rect.top, rect.right, rect.bottom), PreviewGroup.RIGHT)
            } else {
                val group = when {
                    !qwerty || child.id == QwertyR.id.key_space -> PreviewGroup.SINGLE
                    child.id in qLeft -> PreviewGroup.LEFT
                    child.id in qRight -> PreviewGroup.RIGHT
                    rect.centerX() < width / 2f -> PreviewGroup.LEFT
                    else -> PreviewGroup.RIGHT
                }
                result += PreviewItem(child, rect, group)
            }
        }
        return result
    }

    private fun configureQwerty(root: ConstraintLayout) {
        val normal = setOf(QwertyR.id.key_1, QwertyR.id.key_2, QwertyR.id.key_3, QwertyR.id.key_4, QwertyR.id.key_5,
            QwertyR.id.key_6, QwertyR.id.key_7, QwertyR.id.key_8, QwertyR.id.key_9, QwertyR.id.key_0,
            QwertyR.id.key_q, QwertyR.id.key_w, QwertyR.id.key_e, QwertyR.id.key_r, QwertyR.id.key_t,
            QwertyR.id.key_y, QwertyR.id.key_u, QwertyR.id.key_i, QwertyR.id.key_o, QwertyR.id.key_p,
            QwertyR.id.key_a, QwertyR.id.key_s, QwertyR.id.key_d, QwertyR.id.key_f, QwertyR.id.key_g,
            QwertyR.id.key_h, QwertyR.id.key_j, QwertyR.id.key_k, QwertyR.id.key_l, QwertyR.id.key_at_mark,
            QwertyR.id.key_z, QwertyR.id.key_x, QwertyR.id.key_c, QwertyR.id.key_v, QwertyR.id.key_b,
            QwertyR.id.key_n, QwertyR.id.key_m, QwertyR.id.key_space, QwertyR.id.key_kuten, QwertyR.id.key_touten)
        val showNumber = AppPreference.qwerty_show_number_buttons == true
        val number = listOf(QwertyR.id.key_1, QwertyR.id.key_2, QwertyR.id.key_3, QwertyR.id.key_4, QwertyR.id.key_5,
            QwertyR.id.key_6, QwertyR.id.key_7, QwertyR.id.key_8, QwertyR.id.key_9, QwertyR.id.key_0)
        number.forEach { root.findViewById<View>(it).visibility = if (showNumber) VISIBLE else GONE }
        root.findViewById<View>(QwertyR.id.key_at_mark).visibility = GONE
        listOf(QwertyR.id.cursor_left, QwertyR.id.cursor_right).forEach { root.findViewById<View>(it).visibility = if (AppPreference.qwerty_show_cursor_buttons == true) VISIBLE else GONE }
        root.findViewById<View>(QwertyR.id.key_switch_default).visibility = if (AppPreference.qwerty_show_ime_button != false) VISIBLE else GONE
        root.findViewById<View>(QwertyR.id.key_emoji).visibility = if (AppPreference.qwerty_show_emoji_button == true) VISIBLE else GONE
        listOf(QwertyR.id.key_kuten, QwertyR.id.key_touten).forEach { root.findViewById<View>(it).visibility = if (AppPreference.qwerty_show_kutouten_buttons == true) VISIBLE else GONE }
        // This preview shows the English QWERTY state. The Japanese/English switch is only
        // present in the separate Romaji state.
        root.findViewById<View>(QwertyR.id.switch_romaji_english).visibility = GONE
        root.findViewById<View>(QwertyR.id.switch_number_layout).visibility = if (AppPreference.qwerty_switch_number_key_without_number_preference) VISIBLE else GONE
        val set = ConstraintSet().apply { clone(root) }
        set.setGuidelinePercent(QwertyR.id.guideline_number_row, if (showNumber) .2f else 0f)
        set.setGuidelinePercent(QwertyR.id.guideline_q_row, if (showNumber) .4f else .25f)
        set.setGuidelinePercent(QwertyR.id.guideline_a_row, if (showNumber) .6f else .5f)
        set.setGuidelinePercent(QwertyR.id.guideline_z_row, if (showNumber) .8f else .75f)
        if (!showNumber) listOf(QwertyR.id.key_q, QwertyR.id.key_w, QwertyR.id.key_e, QwertyR.id.key_r, QwertyR.id.key_t,
            QwertyR.id.key_y, QwertyR.id.key_u, QwertyR.id.key_i, QwertyR.id.key_o, QwertyR.id.key_p).forEach {
            set.connect(it, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        }
        set.applyTo(root)
        val density = resources.displayMetrics.density
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            if (v is androidx.constraintlayout.widget.Guideline) continue
            val lp = v.layoutParams as? ConstraintLayout.LayoutParams ?: continue
            lp.topMargin = ((AppPreference.qwerty_key_vertical_margin ?: 5f) * density).toInt()
            lp.bottomMargin = lp.topMargin
            lp.marginStart = ((AppPreference.qwerty_key_horizontal_gap ?: 2f) * density).toInt()
            lp.marginEnd = lp.marginStart
            if (v.id == QwertyR.id.key_a) lp.marginStart = ((AppPreference.qwerty_key_indent_large ?: 23f) * density).toInt()
            if (v.id == QwertyR.id.key_l) lp.marginEnd = ((AppPreference.qwerty_key_indent_large ?: 23f) * density).toInt()
            if (v.id == QwertyR.id.key_shift) lp.marginStart = ((AppPreference.qwerty_key_side_margin ?: 4f) * density).toInt()
            if (v.id == QwertyR.id.key_delete) lp.marginEnd = ((AppPreference.qwerty_key_side_margin ?: 4f) * density).toInt()
            v.layoutParams = lp
            val role = when {
                v.id == QwertyR.id.key_space -> SkinKeyRole.SPACE
                v.id in normal -> SkinKeyRole.CHARACTER
                else -> SkinKeyRole.MODIFIER
            }
            styleKey(v, role, qwerty = true)
            if (v is TextView) v.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                if (v.id in normal && v.id != QwertyR.id.key_space) AppPreference.qwerty_key_text_size ?: 18f else AppPreference.qwerty_special_key_text_size ?: 12f)
            if (v is PreviewQwertyButton) {
                val character = v.text?.toString().orEmpty()
                if (showNumber && character.length == 1 && character[0].isDigit()) {
                    if (AppPreference.qwerty_enable_flick_up_preference == true)
                        v.upperGuide = AppPreference.getQwertyNumberKeyFlickUpChars()[character]?.firstOrNull()
                    if (AppPreference.qwerty_enable_flick_down_preference == true)
                        v.lowerGuide = AppPreference.getQwertyNumberKeyFlickDownChars()[character]?.firstOrNull()
                } else if (AppPreference.qwerty_show_keymap_symbols == true) {
                    val withoutNumbers = "1234567890"
                    val withNumbers = "%\\|=[]<>{}"
                    val topRow = "qwertyuiop".indexOf(character)
                    v.upperGuide = if (character.length == 1 && topRow >= 0) {
                        (if (showNumber) withNumbers else withoutNumbers)[topRow]
                    } else mapOf("a" to '@', "s" to '#', "d" to '$', "f" to '_', "g" to '&',
                        "h" to '-', "j" to '+', "k" to '(', "l" to ')', "z" to '*', "x" to '"',
                        "c" to '\'', "v" to ':', "b" to ';', "n" to '!', "m" to '?')[character]
                }
            }
        }
    }

    private fun configureTenkey(root: ConstraintLayout) {
        val normal = setOf(TenkeyR.id.key_1, TenkeyR.id.key_2, TenkeyR.id.key_3, TenkeyR.id.key_4, TenkeyR.id.key_5,
            TenkeyR.id.key_6, TenkeyR.id.key_7, TenkeyR.id.key_8, TenkeyR.id.key_9, TenkeyR.id.key_11,
            TenkeyR.id.key_12, TenkeyR.id.key_small_letter)
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            val lp = v.layoutParams as? ConstraintLayout.LayoutParams ?: continue
            val widthFactor = (200f - (AppPreference.tenkey_key_width_scale_percent ?: 100).coerceIn(0, 200)) / 100f
            val heightFactor = (200f - (AppPreference.tenkey_key_height_scale_percent ?: 100).coerceIn(0, 200)) / 100f
            lp.marginStart = (lp.marginStart * widthFactor).roundToInt()
            lp.marginEnd = (lp.marginEnd * widthFactor).roundToInt()
            lp.topMargin = (lp.topMargin * heightFactor).roundToInt()
            lp.bottomMargin = (lp.bottomMargin * heightFactor).roundToInt()
            v.layoutParams = lp
            styleKey(v, if (v.id in normal) SkinKeyRole.CHARACTER else SkinKeyRole.MODIFIER, qwerty = false,
                round = v.id == TenkeyR.id.key_enter || v.id == TenkeyR.id.key_switch_key_mode)
            if (v is TextView) {
                val guide = AppPreference.tenkey_keymap_guide_layout == true
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (guide) 11f else 17f) + (AppPreference.key_letter_size ?: 0f))
                v.gravity = Gravity.CENTER
                if (v.id == TenkeyR.id.key_12) {
                    v.maxLines = 3
                    v.text = SpannableString("？\n。 , !\n…").apply {
                        for (range in listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7))
                            setSpan(RelativeSizeSpan(.6f), range.first, range.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(RelativeSizeSpan(.8f), 8, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(RelativeSizeSpan(.5f), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else if (guide && v.id in normal) {
                    val labels = when (v.id) {
                        TenkeyR.id.key_1 -> "あいうえお"; TenkeyR.id.key_2 -> "かきくけこ"
                        TenkeyR.id.key_3 -> "さしすせそ"; TenkeyR.id.key_4 -> "たちつてと"
                        TenkeyR.id.key_5 -> "なにぬねの"; TenkeyR.id.key_6 -> "はひふへほ"
                        TenkeyR.id.key_7 -> "まみむめも"; TenkeyR.id.key_8 -> "や(ゆ)よ"
                        TenkeyR.id.key_9 -> "らりるれろ"; TenkeyR.id.key_11 -> "わをんー〜"
                        else -> ""
                    }
                    if (labels.length == 5) {
                        v.maxLines = 3
                        v.setLineSpacing(0f, .9f)
                        v.text = SpannableString("  ${labels[2]}  \n${labels[1]} ${labels[0]} ${labels[3]}\n  ${labels[4]}  ").apply {
                            for (index in listOf(2, 6, 10, 14)) setSpan(RelativeSizeSpan(.6f), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
            }
        }
        (root.findViewById<View>(TenkeyR.id.sideKey_symbol_mode_container) as? ImageView)?.setImageResource(CoreR.drawable.symbol)
        (root.findViewById<View>(TenkeyR.id.key_small_letter) as? ImageView)?.setImageResource(
            if (AppPreference.tenkey_show_language_button_preference) CoreR.drawable.language_24dp else CoreR.drawable.kana_small)
        (root.findViewById<View>(TenkeyR.id.key_enter) as? ImageView)?.setImageResource(CoreR.drawable.baseline_keyboard_return_24)
        if (KeyboardSkinRegistry.find(AppPreference.keyboardSkin) == null && AppPreference.theme_mode != "custom") {
            // The mode icon carries its own selected/unselected colors.
            (root.findViewById<View>(TenkeyR.id.key_switch_key_mode) as? ImageView)?.imageTintList = null
        }
        if (!AppPreference.tenkey_use_three_state_keyboard_preference) {
            (root.findViewById<View>(TenkeyR.id.key_switch_key_mode) as? ImageView)?.setImageResource(CoreR.drawable.language_japanese_kana_left_bold_24px)
        }
    }

    private fun createSumireKeys(ctx: Context, width: Float, height: Float): List<PreviewItem> {
        val result = mutableListOf<PreviewItem>()
        val method = AppPreference.sumire_input_method
        val toggle = method == "toggle"
        val effective = method == "switch-mode-effective"
        val labels = arrayOf(arrayOf("あ", "か", "さ"), arrayOf("た", "な", "は"), arrayOf("ま", "や", "ら"), arrayOf("^_^", "わ", "、。?!"))
        val density = resources.displayMetrics.density
        val w = width / 5f
        val h = height / 4f
        val horizontalFactor = (200f - (AppPreference.flick_key_width_scale_percent ?: 160).coerceIn(0, 200)) / 100f
        val verticalFactor = (200f - (AppPreference.flick_key_height_scale_percent ?: 160).coerceIn(0, 200)) / 100f
        fun add(row: Int, col: Int, span: Int = 1, text: String? = null, icon: Int? = null, special: Boolean = false) {
            val view: View = if (icon != null) AppCompatImageButton(ctx).apply {
                setImageResource(icon); scaleType = ImageView.ScaleType.CENTER_INSIDE
            } else PreviewGuideButton(ctx).apply {
                isAllCaps = false; this.text = text.orEmpty(); gravity = Gravity.CENTER; setPadding(0, 0, 0, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (special) AppPreference.flick_special_key_text_size_sp ?: 12f else AppPreference.flick_key_text_size_sp ?: 16f)
                if (!special && AppPreference.sumire_keymap_guide_japanese) guide = when (text) {
                    "あ" -> "いうえお"; "か" -> "きくけこ"; "さ" -> "しすせそ"
                    "た" -> "ちつてと"; "な" -> "にぬねの"; "は" -> "ひふへほ"
                    "ま" -> "みむめも"; "や" -> "(ゆ)よ"; "ら" -> "りるれろ"
                    "わ" -> "をんー〜"; else -> null
                }
            }
            styleKey(view, if (special) SkinKeyRole.MODIFIER else SkinKeyRole.CHARACTER, qwerty = false)
            val circle = !special && AppPreference.sumire_keyboard_style == "circle"
            val horizontal = ((if (circle) 6f else if (special) 3f else 4f) * horizontalFactor).roundToInt() * density
            val vertical = ((if (circle) 9f else 6f) * verticalFactor).roundToInt() * density
            view.background?.let { background ->
                val insetH = (6f * horizontalFactor).roundToInt()
                val insetTop = ((if (special) 12f else 9f) * verticalFactor).roundToInt()
                val insetBottom = ((if (special) 6f else 9f) * verticalFactor).roundToInt()
                view.background = InsetDrawable(background, insetH, insetTop, insetH, insetBottom)
            }
            val rect = RectF(col * w + horizontal, row * h + vertical, (col + 1) * w - horizontal, (row + span) * h - vertical)
            view.measure(MeasureSpec.makeMeasureSpec(rect.width().roundToInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(rect.height().roundToInt().coerceAtLeast(1), MeasureSpec.EXACTLY))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            result += PreviewItem(view, rect, PreviewGroup.SINGLE)
        }
        for (row in 0..3) {
            if (effective) {
                when (row) {
                    0 -> add(row, 0, icon = CoreR.drawable.language_24dp, special = true)
                    1 -> add(row, 0, icon = CoreR.drawable.outline_arrow_left_alt_24, special = true)
                    2 -> add(row, 0, text = "123", special = true)
                    else -> add(row, 0, text = "ABC", special = true)
                }
            } else if (toggle) {
                when (row) {
                    0 -> add(row, 0, icon = CoreR.drawable.content_paste_24px, special = true)
                    1 -> add(row, 0, icon = CoreR.drawable.baseline_arrow_left_24, special = true)
                    2 -> add(row, 0, text = "ABC", special = true)
                    else -> add(row, 0, icon = CoreR.drawable.language_24dp, special = true)
                }
            } else {
                when (row) {
                    0 -> add(row, 0, text = "123", special = true)
                    1 -> add(row, 0, text = "ABC", special = true)
                    2 -> add(row, 0, text = "あいう", special = true)
                    else -> add(row, 0, icon = CoreR.drawable.language_24dp, special = true)
                }
            }
            for (col in 1..3) add(row, col, text = labels[row][col - 1])
        }
        add(0, 4, icon = CoreR.drawable.backspace_24px, special = true)
        if (effective) {
            add(1, 4, icon = CoreR.drawable.outline_arrow_right_alt_24, special = true)
            add(2, 4, icon = CoreR.drawable.baseline_space_bar_24, special = true)
            add(3, 4, text = "改行", special = true)
        } else {
            add(1, 4, text = "空白", special = true)
            add(2, 4, span = 2, text = "改行", special = true)
        }
        return result
    }

    private fun createGojuonKeys(ctx: Context, width: Float, height: Float): List<PreviewItem> {
        val root = ConstraintLayout(ctx)
        PreviewLayoutInflater(ctx).inflate(GojuonR.layout.gojuon_layout, root, true)
        fun scaled(margin: Int, percent: Int): Int {
            val n = if (percent >= 100) margin * (140 - percent) / 40f
            else margin + max(margin.toFloat(), 2 * resources.displayMetrics.density) * (100 - percent) / 40f * 1.5f
            return n.roundToInt().coerceAtLeast(0)
        }
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            if (v is androidx.constraintlayout.widget.Guideline) continue
            val special = v is ImageView || v.id == GojuonR.id.cursor_keys_container || v.id == GojuonR.id.key_10
            (v.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
                val ws = if (special) AppPreference.gojuon_special_key_width_scale_percent else AppPreference.gojuon_key_width_scale_percent
                val hs = if (special) AppPreference.gojuon_special_key_height_scale_percent else AppPreference.gojuon_key_height_scale_percent
                if (special && lp.width > 0) lp.width = (lp.width * ws / 100f).roundToInt()
                lp.marginStart = scaled(lp.marginStart, ws); lp.marginEnd = scaled(lp.marginEnd, ws)
                lp.topMargin = scaled(lp.topMargin, hs); lp.bottomMargin = scaled(lp.bottomMargin, hs)
                v.layoutParams = lp
            }
            if (v is AppCompatButton) {
                v.setGojuonKeyTextJapanese(v.id, if (special) AppPreference.gojuon_special_text_size_sp else ModeKeySizing.gojuonCenter(KeyboardInputMode.HIRAGANA))
            }
            if (v is ViewGroup) {
                for (j in 0 until v.childCount) styleKey(v.getChildAt(j), SkinKeyRole.MODIFIER, false)
            } else styleKey(v, if (v is ImageView) SkinKeyRole.MODIFIER else SkinKeyRole.CHARACTER, false)
            if (v is AppCompatButton && KeyboardSkinRegistry.find(AppPreference.keyboardSkin) == null && AppPreference.theme_mode != "custom") {
                // The 50-on grid uses connected rectangular cells and rounds only its
                // lower outside corners; the separate key-size editor adjusts the gaps.
                val background = when (v.id) {
                    GojuonR.id.key_5 -> CoreR.drawable.selector_corner_bottom_left_material
                    GojuonR.id.key_55 -> CoreR.drawable.selector_corner_bottom_right_material
                    else -> CoreR.drawable.gojuon_keyboard_center_bg_material
                }
                v.background = AppCompatResources.getDrawable(ctx, background)?.mutate()
                if (AppPreference.liquid_glass_preference) v.background?.alpha = AppPreference.liquid_glass_key_alpha.coerceIn(0, 255)
            }
            if (v.id == GojuonR.id.key_switch_key_mode && v is ImageView &&
                KeyboardSkinRegistry.find(AppPreference.keyboardSkin) == null && AppPreference.theme_mode != "custom") v.imageTintList = null
        }
        root.measure(MeasureSpec.makeMeasureSpec(width.roundToInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height.roundToInt(), MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        fun applyIconScale(view: View) {
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) applyIconScale(view.getChildAt(i))
            } else if (view is ImageView && AppPreference.gojuon_special_icon_scale_percent != 100) {
                val drawable = view.drawable ?: return
                val w = drawable.intrinsicWidth.toFloat(); val h = drawable.intrinsicHeight.toFloat()
                val availableWidth = view.width - view.paddingLeft - view.paddingRight
                val availableHeight = view.height - view.paddingTop - view.paddingBottom
                if (w <= 0 || h <= 0 || availableWidth <= 0 || availableHeight <= 0) return
                val fit = min(availableWidth / w, availableHeight / h)
                val base = when (view.scaleType) { ImageView.ScaleType.CENTER -> 1f; ImageView.ScaleType.CENTER_INSIDE -> min(1f, fit); else -> fit }
                val scale = base * AppPreference.gojuon_special_icon_scale_percent / 100f
                view.scaleType = ImageView.ScaleType.MATRIX
                view.imageMatrix = Matrix().apply { setScale(scale, scale); postTranslate((availableWidth - w * scale) / 2f, (availableHeight - h * scale) / 2f) }
            }
        }
        applyIconScale(root)
        return (0 until root.childCount).mapNotNull { i ->
            val v = root.getChildAt(i)
            if (v.visibility != VISIBLE || v.width <= 0 || v.height <= 0 || v is androidx.constraintlayout.widget.Guideline) null
            else PreviewItem(v, RectF(v.left.toFloat(), v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat()), PreviewGroup.SINGLE)
        }
    }

    private fun createCustomKeys(ctx: Context, width: Float, height: Float, layout: KeyboardLayout?): List<PreviewItem> {
        if (layout == null) return emptyList()
        val grid = GridLayout(ctx).apply {
            columnCount = if (layout.items.isNotEmpty()) layout.columnUnitCount else layout.columnCount
            rowCount = if (layout.items.isNotEmpty()) layout.rowUnitCount else layout.rowCount
        }
        val horizontal = (200f - (AppPreference.flick_key_width_scale_percent ?: 160).coerceIn(0, 200)) / 100f
        val vertical = (200f - (AppPreference.flick_key_height_scale_percent ?: 160).coerceIn(0, 200)) / 100f
        val density = resources.displayMetrics.density
        val iconViews = mutableMapOf<ImageView, KeyData>()
        fun params(p: GridPlacement, baseH: Int = 0, baseV: Int = 0) = GridLayout.LayoutParams().apply {
            rowSpec = GridLayout.spec(p.rowUnits, p.rowSpanUnits, GridLayout.FILL, p.rowSpanUnits.toFloat())
            columnSpec = GridLayout.spec(p.columnUnits, p.columnSpanUnits, GridLayout.FILL, p.columnSpanUnits.toFloat())
            this.width = 0; this.height = 0
            val h = ((baseH * horizontal).roundToInt() * density).roundToInt()
            val v = ((baseV * vertical).roundToInt() * density).roundToInt()
            setMargins(h, v, h, v)
        }
        val items = layout.items.ifEmpty { layout.keys.map {
            KeyItem(it.keyId.orEmpty(), it, GridPlacement(it.row, it.column, it.rowSpan, it.colSpan))
        } }
        items.forEach { item ->
            when (item) {
                is SpacerItem -> grid.addView(Space(ctx), params(item.placement))
                is KeyItem -> {
                    val data = item.keyData
                    val modeLabel = if (KeyIconResolver.hasIconOverride(data)) null else when (data.drawableResId) {
                        CoreR.drawable.input_mode_english_custom -> "ABC"
                        CoreR.drawable.input_mode_number_select_custom -> "123"
                        CoreR.drawable.input_mode_japanese_select_custom -> "あいう"
                        else -> null
                    }
                    val view: View = if (KeyIconResolver.hasIcon(data) && modeLabel == null) AppCompatImageButton(ctx).apply {
                        KeyIconResolver.setImage(this, data); scaleType = ImageView.ScaleType.MATRIX
                        setPadding(0, 0, 0, 0); iconViews[this] = data
                    } else PreviewGuideButton(ctx).apply {
                        text = modeLabel ?: KeyIconResolver.resolvedLabelForRendering(data); isAllCaps = false; gravity = Gravity.CENTER
                        setPadding(0, 0, 0, 0)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (data.isSpecialKey) AppPreference.flick_special_key_text_size_sp ?: 12f else AppPreference.flick_key_text_size_sp ?: 16f)
                        if (AppPreference.flick_keymap_guide_layout == true && !data.isSpecialKey) {
                            fun shown(action: FlickAction?): String = when (action) {
                                is FlickAction.Input -> action.char
                                else -> ""
                            }.take((AppPreference.flick_guide_max_characters_preference ?: 1).coerceIn(1, 4))
                            val circular = (layout.circularFlickKeyMaps[data.keyId] ?: layout.circularFlickKeyMaps[data.label])?.firstOrNull()
                            val flick = (layout.flickKeyMaps[data.keyId] ?: layout.flickKeyMaps[data.label])?.firstOrNull()
                            guideLabels = if (data.keyType == KeyType.CIRCULAR_FLICK && circular != null) listOf(
                                shown(circular[CircularFlickDirection.SLOT_3]), shown(circular[CircularFlickDirection.SLOT_0]),
                                shown(circular[CircularFlickDirection.SLOT_1]), shown(circular[CircularFlickDirection.SLOT_2]))
                            else if (flick != null) listOf(shown(flick[FlickDirection.UP_LEFT_FAR] ?: flick[FlickDirection.UP_LEFT]),
                                shown(flick[FlickDirection.UP]), shown(flick[FlickDirection.UP_RIGHT_FAR] ?: flick[FlickDirection.UP_RIGHT]),
                                shown(flick[FlickDirection.DOWN])) else null
                        }
                    }
                    val role = KeyVisualStyleResolver.resolveSkinKeyRole(data,
                        useModifierSurfaceForSpecialSpaceKeys = AppPreference.keyboardSkin == KeyboardSkinId.CUPERTINO_CLASSIC)
                    styleKey(view, role, false)
                    if (view is ImageView && !KeyIconResolver.shouldTintIcon(data)) view.imageTintList = null
                    view.background?.let { bg ->
                        val insetH = (6f * horizontal).roundToInt()
                        val insetTop = ((if (data.isSpecialKey) 12f else 9f) * vertical).roundToInt()
                        val insetBottom = ((if (data.isSpecialKey) 6f else 9f) * vertical).roundToInt()
                        view.background = InsetDrawable(bg, insetH, insetTop, insetH, insetBottom)
                    }
                    if (view is TextView && data.keyType == KeyType.STANDARD_FLICK &&
                        (layout.flickKeyMaps[data.label]?.firstOrNull() != null) && AppPreference.theme_mode != "custom") {
                        fun colorAttr(attr: Int, fallback: Int): Int {
                            val typed = TypedValue()
                            return if (ctx.theme.resolveAttribute(attr, typed, true)) {
                                if (typed.resourceId != 0) runCatching { ContextCompat.getColor(ctx, typed.resourceId) }.getOrDefault(fallback) else typed.data
                            } else fallback
                        }
                        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                        val textSize = AppPreference.flick_key_text_size_sp ?: 16f
                        view.background = SegmentedBackgroundDrawable(data.label,
                            colorAttr(if (dark) MaterialR.attr.colorSurfaceContainerHighest else MaterialR.attr.colorSurface, Color.LTGRAY),
                            colorAttr(MaterialR.attr.colorSecondaryContainer, Color.LTGRAY),
                            colorAttr(MaterialR.attr.colorOnSurface, Color.BLACK), 20f,
                            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSize, resources.displayMetrics),
                            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, (textSize * .625f).coerceAtLeast(8f), resources.displayMetrics))
                        view.setTextColor(Color.TRANSPARENT)
                    }
                    val h = if (data.keyType == KeyType.STANDARD_FLICK) 6 else if (data.isSpecialKey) 3 else 4
                    val v = if (data.keyType == KeyType.STANDARD_FLICK) 9 else 6
                    grid.addView(view, params(item.placement, h, v))
                }
            }
        }
        grid.measure(MeasureSpec.makeMeasureSpec(width.roundToInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height.roundToInt(), MeasureSpec.EXACTLY))
        grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
        iconViews.forEach { (view, data) ->
            val drawable = view.drawable ?: return@forEach
            val w = drawable.intrinsicWidth.toFloat(); val h = drawable.intrinsicHeight.toFloat()
            if (w <= 0 || h <= 0 || view.width <= 0 || view.height <= 0) return@forEach
            val large = data.action in setOf(KeyAction.SwitchToNumberLayout, KeyAction.SwitchToEnglishLayout, KeyAction.SwitchToKanaLayout) ||
                data.label in setOf("SwitchToNumber", "SwitchToEnglish", "SwitchToKana")
            val target = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics) * 1.6f *
                (AppPreference.flick_key_icon_scale_percent ?: 80) / 100f * (if (large) 1.65f else 1f)
            val scale = min(min(target / w, target / h), min(view.width / w, view.height / h))
            view.imageMatrix = Matrix().apply { postScale(scale, scale); postTranslate((view.width - w * scale) / 2f, (view.height - h * scale) / 2f) }
        }
        return (0 until grid.childCount).mapNotNull { i ->
            val v = grid.getChildAt(i)
            if (v is Space || v.width <= 0 || v.height <= 0) null
            else PreviewItem(v, RectF(v.left.toFloat(), v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat()), PreviewGroup.SINGLE)
        }
    }

    private fun styleKey(view: View, role: SkinKeyRole, qwerty: Boolean, round: Boolean = false) {
        val skin = KeyboardSkinRegistry.find(AppPreference.keyboardSkin)
        val colors = previewColors(view.context)
        val special = role == SkinKeyRole.MODIFIER
        val color = if (role == SkinKeyRole.SPACE && skin != null) skin.palette.spaceText else if (special) colors.specialText else colors.text
        val bg = if (skin != null) {
            skin.keyDrawable(view.resources, qwerty = qwerty, role = role)
        } else if (AppPreference.theme_mode == "custom") {
            val base = if (special) AppPreference.custom_theme_special_key_color else AppPreference.custom_theme_key_color
            val radius = (if (round) 32f else 8f) * resources.displayMetrics.density
            fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
            if (AppPreference.custom_theme_border_enable) shape(base).apply {
                setStroke((AppPreference.custom_theme_border_width.coerceAtLeast(1) * resources.displayMetrics.density).roundToInt(),
                    AppPreference.custom_theme_border_color)
            } else {
                fun shade(factor: Float) = Color.argb(Color.alpha(base), (Color.red(base) * factor).toInt().coerceIn(0, 255),
                    (Color.green(base) * factor).toInt().coerceIn(0, 255), (Color.blue(base) * factor).toInt().coerceIn(0, 255))
                val offset = (4 * resources.displayMetrics.density).toInt()
                val padding = (2 * resources.displayMetrics.density).toInt()
                LayerDrawable(arrayOf(shape(shade(.8f)), shape(shade(1.2f)), shape(base))).apply {
                    setLayerInset(0, offset, offset, 0, 0); setLayerInset(1, 0, 0, offset, offset)
                    setLayerInset(2, padding, padding, padding, padding)
                }
            }
        } else {
            val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val res = if (round) {
                if (dark) CoreR.drawable.round_key_bg_material else CoreR.drawable.round_key_bg_material_light
            } else if (special) {
                if (dark) CoreR.drawable.ten_keys_side_bg_material else CoreR.drawable.ten_keys_side_bg_material_light
            } else if (dark) CoreR.drawable.ten_keys_center_bg_material else CoreR.drawable.ten_keys_center_bg_material_light
            AppCompatResources.getDrawable(view.context, res)?.constantState?.newDrawable(view.resources)?.mutate()
        }
        view.background = bg
        if (AppPreference.liquid_glass_preference) view.background?.alpha = AppPreference.liquid_glass_key_alpha.coerceIn(0, 255)
        view.isClickable = false
        view.isFocusable = false
        if (view is TextView) {
            view.setTextColor(color)
            KeyboardFontApplicator.processSnapshot.typeface?.let { view.typeface = it }
        }
        if (view is ImageView) view.imageTintList = ColorStateList.valueOf(color)
    }

    private fun previewColors(ctx: Context): PreviewColors {
        val skin = KeyboardSkinRegistry.find(AppPreference.keyboardSkin)
        if (skin != null) return PreviewColors(skin.palette.background, skin.palette.text, skin.palette.specialText, skin.palette.text)
        if (AppPreference.theme_mode == "custom") return PreviewColors(AppPreference.custom_theme_bg_color,
            AppPreference.custom_theme_key_text_color, AppPreference.custom_theme_special_key_text_color, AppPreference.custom_theme_candidate_text_color)
        fun attr(id: Int, fallback: Int): Int {
            val typed = TypedValue()
            return if (ctx.theme.resolveAttribute(id, typed, true)) {
                if (typed.resourceId != 0) runCatching { ContextCompat.getColor(ctx, typed.resourceId) }.getOrDefault(fallback) else typed.data
            } else fallback
        }
        return PreviewColors(attr(MaterialR.attr.colorSurfaceContainer, Color.LTGRAY), attr(MaterialR.attr.colorOnSurface, Color.BLACK),
            attr(MaterialR.attr.colorOnSecondaryContainer, Color.BLACK), attr(MaterialR.attr.colorOnSurface, Color.BLACK))
    }
}

@VisibleForTesting
internal data class GeometryPreviewSnapshot(val screenWidth: Float, val bodyHeight: Float, val bodyLeft: Float,
    val bodyWidth: Float, val keys: List<GeometryPreviewKeyFrame>)

@VisibleForTesting
internal data class GeometryPreviewKeyFrame(val id: Int, val text: String?, val x: Float, val y: Float,
    val angle: Float, val halfWidth: Float, val halfHeight: Float)

private data class PreviewColors(val background: Int, val text: Int, val specialText: Int, val candidateText: Int)
private data class PreviewScene(val screenWidth: Float, val height: Float, val bodyLeft: Float, val bodyWidth: Float,
    val chromeHeight: Float, val bodyHeight: Float, val surfaceLeft: Float, val surfaceWidth: Float,
    val candidateSideWidth: Float, val backgroundColor: Int, val candidateTextColor: Int,
    val items: List<PlacedPreviewItem>)
private enum class PreviewGroup { LEFT, RIGHT, SINGLE }
private data class PreviewItem(val view: View, val bounds: RectF, val group: PreviewGroup)
private data class PlacedPreviewItem(val view: View, val bounds: RectF, val group: PreviewGroup,
    var x: Float, var y: Float, var scaleX: Float, var scaleY: Float, val angle: Float) {
    private val cosine get() = cos(Math.toRadians(angle.toDouble())).toFloat()
    private val sine get() = sin(Math.toRadians(angle.toDouble())).toFloat()
    fun halfWidth() = (abs(cosine) * view.width * scaleX + abs(sine) * view.height * scaleY) / 2f
    fun halfHeight() = (abs(sine) * view.width * scaleX + abs(cosine) * view.height * scaleY) / 2f
    fun intersects(other: PlacedPreviewItem, factor: Float): Boolean {
        val dx = other.x - x; val dy = other.y - y
        val aw = view.width * scaleX * factor / 2f; val ah = view.height * scaleY * factor / 2f
        val bw = other.view.width * other.scaleX * factor / 2f; val bh = other.view.height * other.scaleY * factor / 2f
        val c = abs(cosine * other.cosine + sine * other.sine)
        val s = abs(cosine * other.sine - sine * other.cosine)
        return abs(dx * cosine + dy * sine) < aw + bw * c + bh * s &&
            abs(-dx * sine + dy * cosine) < ah + bw * s + bh * c &&
            abs(dx * other.cosine + dy * other.sine) < bw + aw * c + ah * s &&
            abs(-dx * other.sine + dy * other.cosine) < bh + aw * s + ah * c
    }
}

/** Independent display-only geometry; it never transforms the real IME's views. */
private object PreviewGeometry {
    data class Result(val height: Float, val items: List<PlacedPreviewItem>)
    fun place(keys: List<PreviewItem>, width: Float, baseHeight: Float, gap: Float, degrees: Int, direction: KeyboardCurveDirection,
        rightHand: Boolean, qwerty: Boolean, autoHeight: Boolean, maximumHeight: Float): Result {
        if (keys.isEmpty()) return Result(baseHeight, emptyList())
        if (degrees == 0 && gap == 0f) return Result(baseHeight, keys.map {
            PlacedPreviewItem(it.view, it.bounds, it.group, it.bounds.centerX(), it.bounds.centerY(), 1f, 1f, 0f)
        })
        val sideWidth = (width - gap) / 2f
        val groups = keys.groupBy { it.group }.mapValues { (_, v) -> v.minOf { it.bounds.left } to v.maxOf { it.bounds.right } }
        val placed = keys.map { key ->
            val bounds = key.bounds
            var x = bounds.centerX()
            var sx = bounds.width() / key.view.width
            var sy = bounds.height() / key.view.height
            if (qwerty && gap > 0 && key.group != PreviewGroup.SINGLE) {
                val (left, right) = groups.getValue(key.group)
                val fit = min(1f, sideWidth / (right - left).coerceAtLeast(1f))
                val edge = if (key.group == PreviewGroup.LEFT) 0f else width - sideWidth
                x = edge + (x - left) * fit
                sx *= fit
            }
            val mirror = when (key.group) { PreviewGroup.LEFT -> false; PreviewGroup.RIGHT -> true; PreviewGroup.SINGLE -> rightHand }
            val span = if (qwerty) sideWidth else width
            val distance = if (qwerty && key.group == PreviewGroup.SINGLE) span else if (mirror) width - x else x
            val radians = Math.toRadians(degrees.coerceIn(0, 25).toDouble())
            val radius = if (degrees > 0) span / sin(radians).toFloat() else Float.POSITIVE_INFINITY
            val d = distance.coerceIn(0f, span)
            val directionSign = if (direction == KeyboardCurveDirection.UP) -1f else 1f
            val offset = if (degrees > 0) (radius - sqrt(max(0f, radius * radius - d * d))) * directionSign else 0f
            val angle = if (degrees > 0 && !(qwerty && key.group == PreviewGroup.SINGLE))
                Math.toDegrees(asin((d / radius).coerceIn(-1f, 1f)).toDouble()).toFloat() * (if (mirror) -1f else 1f) * directionSign else 0f
            PlacedPreviewItem(key.view, bounds, key.group, x, bounds.centerY() + offset, sx, sy, angle)
        }
        if (qwerty && gap > 0) {
            for (group in listOf(PreviewGroup.LEFT, PreviewGroup.RIGHT)) {
                val part = placed.filter { it.group == group }
                if (part.isEmpty()) continue
                val minX = part.minOf { it.x - it.halfWidth() }; val maxX = part.maxOf { it.x + it.halfWidth() }
                val fit = min(1f, sideWidth / (maxX - minX).coerceAtLeast(1f))
                val edge = if (group == PreviewGroup.LEFT) 0f else width - sideWidth
                part.forEach { it.x = edge + (it.x - minX) * fit; it.scaleX *= fit; it.scaleY *= fit }
            }
        } else {
            val minX = placed.minOf { it.x - it.halfWidth() }; val maxX = placed.maxOf { it.x + it.halfWidth() }
            if (minX < 0 || maxX > width) {
                val fit = min(1f, width / (maxX - minX).coerceAtLeast(1f))
                val start = ((width - (maxX - minX) * fit) / 2f).coerceAtLeast(0f)
                placed.forEach { it.x = start + (it.x - minX) * fit; it.scaleX *= fit; it.scaleY *= fit }
            }
        }
        fun overlaps(factor: Float): Boolean {
            for (i in placed.indices) for (j in i + 1 until placed.size) {
                if (!RectF.intersects(placed[i].bounds, placed[j].bounds) && placed[i].intersects(placed[j], factor)) return true
            }
            return false
        }
        if (overlaps(1f)) {
            var low = 0f; var high = 1f
            repeat(12) { val middle = (low + high) / 2f; if (overlaps(middle)) high = middle else low = middle }
            placed.forEach { it.scaleX *= low; it.scaleY *= low }
        }
        val top = keys.minOf { it.bounds.top }.coerceAtLeast(0f)
        val bottomPadding = (baseHeight - keys.maxOf { it.bounds.bottom }).coerceAtLeast(0f)
        val minY = placed.minOf { it.y - it.halfHeight() }; val maxY = placed.maxOf { it.y + it.halfHeight() }
        val desired = max(baseHeight, maxY - minY + top + bottomPadding)
        val height = if (autoHeight) min(maximumHeight, desired) else baseHeight
        val fit = min(1f, (height - top - bottomPadding).coerceAtLeast(1f) / (maxY - minY).coerceAtLeast(1f))
        placed.forEach { it.y = height - bottomPadding - (maxY - it.y) * fit; it.scaleX *= fit; it.scaleY *= fit }
        return Result(height, placed)
    }
}

/** Inflate the existing layout description without constructing any input-aware widgets. */
private class PreviewLayoutInflater(context: Context) : LayoutInflater(context) {
    init {
        factory2 = object : Factory2 {
            override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? = when (name) {
                "com.kazumaproject.qwerty_keyboard.ui.QWERTYButton" -> PreviewQwertyButton(context, attrs)
                "com.kazumaproject.core.ui.input_mode_witch.InputModeSwitch",
                "com.kazumaproject.tenkey.view.SideKeySymbolModeContainerView" -> AppCompatImageButton(context, attrs)
                else -> null
            }
            override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? = onCreateView(null, name, context, attrs)
        }
    }
    override fun cloneInContext(newContext: Context): LayoutInflater = PreviewLayoutInflater(newContext)
}

/** A plain display-only button for the small QWERTY corner labels. */
private class PreviewQwertyButton(context: Context, attrs: AttributeSet) : AppCompatButton(context, attrs) {
    var upperGuide: Char? = null
    var lowerGuide: Char? = null
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        guidePaint.color = currentTextColor
        guidePaint.typeface = typeface
        guidePaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            AppPreference.qwerty_symbol_keymap_text_size ?: 9f, resources.displayMetrics)
        upperGuide?.let { canvas.drawText(it.toString(), (width - paddingRight).toFloat(), paddingTop + guidePaint.textSize, guidePaint) }
        lowerGuide?.let { canvas.drawText(it.toString(), (width - paddingRight).toFloat(), height - paddingBottom - guidePaint.fontMetrics.descent, guidePaint) }
    }
}

/** Draws guide labels around a normal text button without creating a flick controller. */
private class PreviewGuideButton(context: Context) : AppCompatButton(context) {
    var guide: String? = null
    var guideLabels: List<String>? = null
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val labels = guideLabels ?: guide?.takeIf { it.length == 4 }?.map(Char::toString) ?: return
        if (labels.size != 4) return
        val size = min(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            (AppPreference.flick_guide_text_size_sp_preference ?: 9).coerceIn(6, 16).toFloat(), resources.displayMetrics),
            min(width, height) * .22f)
        guidePaint.color = currentTextColor; guidePaint.textSize = size; guidePaint.typeface = typeface
        val fm = guidePaint.fontMetrics
        val centerY = height / 2f - (fm.ascent + fm.descent) / 2f
        val inset = max(10f * resources.displayMetrics.density, width * .16f)
        canvas.drawText(labels[0], inset, centerY, guidePaint)
        canvas.drawText(labels[1], width / 2f, 3f * resources.displayMetrics.density - fm.ascent, guidePaint)
        canvas.drawText(labels[2], width - inset, centerY, guidePaint)
        canvas.drawText(labels[3], width / 2f, height - 3f * resources.displayMetrics.density - fm.descent, guidePaint)
    }
}
