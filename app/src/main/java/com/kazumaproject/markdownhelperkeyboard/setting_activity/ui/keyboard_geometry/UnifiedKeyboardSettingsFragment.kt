package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.kazumaproject.core.ui.geometry.KeyboardCurveDirection
import com.kazumaproject.core.ui.geometry.KeyboardCurveHand
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryCandidatePlacement
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryKeyboard
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreferences
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateSizing
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.IndependentKeyboardSize
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.KeyboardSurfaceSizePreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.KeyboardSurfaceSizeTarget
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.core.R as CoreR
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A passive preview and editors. Input-aware keyboard views never belong to this screen. */
@AndroidEntryPoint
class UnifiedKeyboardSettingsFragment : Fragment() {
    @Inject lateinit var repository: KeyboardRepository
    private lateinit var sizes: KeyboardSurfaceSizePreferences
    private lateinit var geometries: KeyboardGeometryPreferences
    private var type = PreviewKeyboard.TENKEY
    private var dedicated = false
    private var landscape = false
    private var shapeTab = false
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var selectedCustomId: String? = null
    private var customLayout: KeyboardLayout? = null
    private var layouts: List<CustomKeyboardLayout> = emptyList()
    private var loadJob: Job? = null
    private var size = IndependentKeyboardSize()
    private var geometry = KeyboardGeometryPreference()
    private lateinit var preview: GeometryPreview
    private lateinit var handles: SizeHandles
    private lateinit var previewFrame: FrameLayout
    private lateinit var previewHolder: HorizontalScrollView
    private lateinit var body: LinearLayout
    private lateinit var customBox: LinearLayout
    private lateinit var tabs: RadioGroup
    private lateinit var warning: TextView
    private lateinit var effectiveHeight: TextView
    private lateinit var screen: FrameLayout
    private lateinit var normalScroll: ScrollView
    private var editLayer: View? = null
    private var editMode: SizeEditMode? = null
    private var pendingEditMode: SizeEditMode? = null
    private var actionBarWasVisible = false
    private var backCallback: OnBackPressedCallback? = null
    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destination = runCatching { findNavController().currentDestination?.id }.getOrNull()
        val argument = arguments?.getString("geometryKeyboard")
        type = savedInstanceState?.getString("type")?.let { runCatching { PreviewKeyboard.valueOf(it) }.getOrNull() }
            ?: argument?.let { runCatching { PreviewKeyboard.valueOf(it) }.getOrNull() } ?: PreviewKeyboard.TENKEY
        dedicated = type == PreviewKeyboard.GOJUON || type == PreviewKeyboard.CUSTOM
        landscape = savedInstanceState?.getBoolean("landscape") ?: when (destination) {
            R.id.keyboardSettingFragment -> false
            R.id.keyboardSizeLandscapeFragment -> true
            else -> resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        shapeTab = savedInstanceState?.getBoolean("shape") ?: (argument != null && !dedicated)
        selectedCustomId = savedInstanceState?.getString("custom")
        pendingEditMode = savedInstanceState?.getString("editMode")?.let { runCatching { SizeEditMode.valueOf(it) }.getOrNull() }
        originalOrientation = savedInstanceState?.getInt("originalOrientation") ?: requireActivity().requestedOrientation
        sizes = KeyboardSurfaceSizePreferences(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        geometries = KeyboardGeometryPreferences(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        setActivityOrientation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("type", type.name); outState.putBoolean("landscape", landscape)
        outState.putBoolean("shape", shapeTab); outState.putString("custom", selectedCustomId)
        outState.putString("editMode", editMode?.name)
        outState.putInt("originalOrientation", originalOrientation)
    }

    override fun onDestroy() {
        activity?.takeUnless { it.isChangingConfigurations }?.requestedOrientation = originalOrientation
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val page = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(24)) }
        fun section() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }.also(page::addView)
        val top = section()
        if (!dedicated) {
            top.addView(label(R.string.keyboard_layout_type))
            val types = radio(listOf(1 to R.string.keyboard_layout_tenkey, 2 to R.string.keyboard_layout_qwerty, 3 to R.string.keyboard_layout_sumire))
            types.id = R.id.keyboard_layout_type_selector
            types.check(when (type) { PreviewKeyboard.QWERTY -> 2; PreviewKeyboard.SUMIRE -> 3; else -> 1 })
            top.addView(types)
            types.setOnCheckedChangeListener { _, id ->
                type = when (id) { 2 -> PreviewKeyboard.QWERTY; 3 -> PreviewKeyboard.SUMIRE; else -> PreviewKeyboard.TENKEY }
                loadValues(); rebuildPreview(); buildBody()
            }
        }
        val orientation = radio(listOf(1 to R.string.keyboard_geometry_portrait, 2 to R.string.keyboard_geometry_landscape))
        orientation.id = R.id.keyboard_layout_orientation_selector
        orientation.check(if (landscape) 2 else 1); top.addView(orientation)
        orientation.setOnCheckedChangeListener { _, id ->
            val next = id == 2
            if (landscape != next) { landscape = next; loadValues(); setActivityOrientation(); rebuildPreview(); buildBody() }
        }
        customBox = section()
        customBox.isVisible = type == PreviewKeyboard.CUSTOM
        previewFrame = UnscaledPreviewFrame(ctx) { previewScreenWidth() }
        previewHolder = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(previewFrame, ViewGroup.LayoutParams(previewScreenWidth(), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        page.addView(previewHolder, LinearLayout.LayoutParams(-1, -2))
        val below = section()
        effectiveHeight = TextView(ctx).also(below::addView)
        below.addView(TextView(ctx).apply { setText(R.string.keyboard_layout_drag_hint); setPadding(0, dp(4), 0, dp(8)) })
        tabs = radio(listOf(1 to R.string.keyboard_layout_size_tab, 2 to R.string.keyboard_layout_shape_tab))
        tabs.id = R.id.keyboard_layout_tab_selector; tabs.check(if (shapeTab) 2 else 1)
        tabs.isVisible = !dedicated; below.addView(tabs)
        tabs.setOnCheckedChangeListener { _, id -> shapeTab = id == 2; buildBody() }
        body = LinearLayout(ctx).apply { this.orientation = LinearLayout.VERTICAL }
        below.addView(body)
        warning = TextView(ctx).apply { setPadding(0, dp(8), 0, dp(8)) }
        below.addView(warning)
        loadValues(); rebuildPreview(); buildBody()
        initialized = true
        normalScroll = ScrollView(ctx).apply { isFillViewport = true; addView(page) }
        screen = FrameLayout(ctx).apply { addView(normalScroll, FrameLayout.LayoutParams(-1, -1)) }
        return screen
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.keyboard_layout_settings_title)
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = leaveEditor()
        }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
        if (type == PreviewKeyboard.CUSTOM) observeCustomLayouts()
        pendingEditMode?.let { pendingEditMode = null; enterEditor(it) }
    }

    override fun onDestroyView() {
        if (editMode != null && actionBarWasVisible) (activity as? AppCompatActivity)?.supportActionBar?.show()
        editMode = null; initialized = false; loadJob?.cancel(); backCallback = null
        super.onDestroyView()
    }

    private fun setActivityOrientation() {
        requireActivity().requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private fun previewScreenWidth(): Int {
        val metrics = resources.displayMetrics
        return if (landscape) max(metrics.widthPixels, metrics.heightPixels) else min(metrics.widthPixels, metrics.heightPixels)
    }
    private fun label(res: Int) = TextView(requireContext()).apply { setText(res); textSize = 16f; setPadding(0, dp(10), 0, dp(3)) }
    private fun radio(options: List<Pair<Int, Int>>, vertical: Boolean = false) = RadioGroup(requireContext()).apply {
        orientation = if (vertical) RadioGroup.VERTICAL else RadioGroup.HORIZONTAL
        options.forEach { (key, text) -> addView(RadioButton(context).apply { id = key; setText(text) },
            if (vertical) RadioGroup.LayoutParams(-1, -2) else RadioGroup.LayoutParams(0, -2, 1f)) }
    }
    private fun target() = when (type) {
        PreviewKeyboard.QWERTY -> KeyboardSurfaceSizeTarget.QWERTY
        PreviewKeyboard.GOJUON -> KeyboardSurfaceSizeTarget.GOJUON
        PreviewKeyboard.CUSTOM -> KeyboardSurfaceSizeTarget.CUSTOM
        else -> KeyboardSurfaceSizeTarget.TENKEY
    }
    private fun geometryTarget() = when (type) {
        PreviewKeyboard.QWERTY -> GeometryKeyboard.QWERTY
        PreviewKeyboard.TENKEY -> GeometryKeyboard.TENKEY
        PreviewKeyboard.SUMIRE -> GeometryKeyboard.SUMIRE
        else -> null
    }
    private fun loadValues() {
        size = sizes.read(target(), landscape)
        geometry = geometryTarget()?.let { geometries.read(it, landscape) } ?: KeyboardGeometryPreference()
    }
    private fun rebuildPreview() {
        if (!::previewFrame.isInitialized) return
        (previewFrame.parent as? HorizontalScrollView)?.let {
            previewFrame.layoutParams = previewFrame.layoutParams.apply { width = previewScreenWidth() }
        }
        previewFrame.removeAllViews()
        preview = GeometryPreview(requireContext(), type).apply { id = R.id.keyboard_layout_preview }
        preview.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (view === preview && ::effectiveHeight.isInitialized) {
                val heightDp = (preview.snapshotForTesting().bodyHeight / resources.displayMetrics.density).roundToInt()
                effectiveHeight.text = getString(R.string.keyboard_layout_effective_height, heightDp)
                // Post the layout request: changing another child's size during layout can
                // otherwise be ignored, leaving a zero-height, untouchable overlay.
                val expectedHeight = view.height
                view.post {
                    if (view === preview && ::handles.isInitialized && handles.layoutParams.height != expectedHeight) {
                        handles.layoutParams = handles.layoutParams.apply { height = expectedHeight }
                    }
                }
            }
        }
        previewFrame.addView(preview, FrameLayout.LayoutParams(-1, -2))
        handles = SizeHandles(requireContext(), preview, { size }, { updateSize(it, false) }, { updateSize(it, true) }).apply {
            id = R.id.keyboard_layout_handles
            mode = editMode
            isVisible = editMode != null && (type != PreviewKeyboard.CUSTOM || customLayout != null)
        }
        // A MATCH_PARENT overlay in a vertically unbounded ScrollView can make the frame
        // consume the viewport and hide the controls below it. Track the preview's height.
        previewFrame.addView(handles, FrameLayout.LayoutParams(-1, 0))
        updatePreview()
    }
    private fun updatePreview() {
        if (!::preview.isInitialized) return
        preview.update(geometry, landscape, size.widthPercent.coerceIn(1, 100), size.positionEnd, size, customLayout)
        handles.invalidate()
        if (::warning.isInitialized) updateWarning()
    }
    private fun updateSize(next: IndependentKeyboardSize, save: Boolean) {
        size = next
        if (save) { sizes.save(target(), landscape, next); buildBody() }
        updatePreview()
    }
    private fun saveGeometry(next: KeyboardGeometryPreference) {
        geometry = next
        geometryTarget()?.let { geometries.save(it, landscape, next) }
        updatePreview()
    }

    private fun buildBody() {
        if (!::body.isInitialized) return
        body.removeAllViews()
        if (!shapeTab || dedicated) buildSizeBody() else buildShapeBody()
        updateWarning()
    }
    private fun buildSizeBody() {
        val header = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(requireContext()).apply { text = getString(R.string.keyboard_layout_size_value, size.heightDp, size.widthPercent); textSize = 16f }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ImageButton(requireContext()).apply {
            id = R.id.keyboard_layout_more; setImageResource(CoreR.drawable.more_vert_24px)
            contentDescription = getString(R.string.keyboard_layout_more); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { anchor -> PopupMenu(context, anchor).apply {
                menu.add(0, 1, 0, R.string.keyboard_layout_exact_values)
                menu.add(0, 2, 1, R.string.keyboard_layout_reset_size)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) { 1 -> showExactValues(); 2 -> updateSize(IndependentKeyboardSize(), true) }
                    true
                }
            }.show() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(header)
        if (type == PreviewKeyboard.CUSTOM) body.addView(TextView(requireContext()).apply { setText(R.string.keyboard_layout_custom_shared) })
        body.addView(editCard(R.id.keyboard_layout_edit_size, R.string.keyboard_layout_edit_size,
            R.string.keyboard_layout_edit_size_summary, R.drawable.composing_guide_edit, SizeEditMode.SIZE))
        body.addView(editCard(R.id.keyboard_layout_edit_position, R.string.keyboard_layout_edit_position,
            R.string.keyboard_layout_edit_position_summary, CoreR.drawable.arrows_output_24px, SizeEditMode.POSITION))
        body.addView(label(R.string.keyboard_layout_alignment))
        val align = radio(listOf(1 to R.string.keyboard_layout_align_start, 2 to R.string.keyboard_layout_align_end))
        align.check(if (size.positionEnd) 2 else 1)
        align.setOnCheckedChangeListener { _, id -> updateSize(size.copy(positionEnd = id == 2), true) }
        body.addView(align)
    }

    private fun editCard(id: Int, title: Int, description: Int, icon: Int, mode: SizeEditMode): View {
        val ctx = requireContext()
        return MaterialCardView(ctx).apply {
            this.id = id; radius = dp(16).toFloat(); strokeWidth = dp(1)
            setStrokeColor(MaterialColors.getColor(this, MaterialR.attr.colorOutlineVariant, Color.LTGRAY))
            setCardBackgroundColor(MaterialColors.getColor(this, MaterialR.attr.colorSurfaceContainerLow, Color.WHITE))
            isClickable = true; isFocusable = true
            isEnabled = type != PreviewKeyboard.CUSTOM || customLayout != null
            alpha = if (isEnabled) 1f else .45f
            contentDescription = "${getString(title)}. ${getString(description)}"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
            row.addView(ImageView(ctx).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.DKGRAY))
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            row.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, 0, 0)
                addView(TextView(ctx).apply { setText(title); textSize = 16f; setTextColor(MaterialColors.getColor(this, MaterialR.attr.colorOnSurface, Color.BLACK)) })
                addView(TextView(ctx).apply { setText(description); textSize = 13f; setTextColor(MaterialColors.getColor(this, MaterialR.attr.colorOnSurfaceVariant, Color.DKGRAY)) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(row)
            setOnClickListener { enterEditor(mode) }
        }
    }

    /** The preview remains at the same screen edge and margins as the docked IME. */
    private fun enterEditor(mode: SizeEditMode) {
        if (editMode != null) return
        val ctx = requireContext()
        editMode = mode; backCallback?.isEnabled = true
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar
        actionBarWasVisible = actionBar?.isShowing == true
        if (actionBarWasVisible) actionBar?.hide()
        previewHolder.removeView(previewFrame)
        normalScroll.isVisible = false
        val layer = FrameLayout(ctx)
        layer.addView(KeyboardPositionCanvas(ctx, previewFrame, previewScreenWidth()), FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(12), dp(4))
        }
        header.addView(TextView(ctx).apply {
            setText(if (mode == SizeEditMode.SIZE) R.string.keyboard_layout_edit_size_hint else R.string.keyboard_layout_edit_position_hint)
            textSize = 14f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(MaterialButton(ctx).apply {
            id = R.id.keyboard_layout_edit_done; setText(R.string.keyboard_layout_done)
            isAllCaps = false; setOnClickListener { leaveEditor() }
        }, LinearLayout.LayoutParams(-2, -2))
        layer.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        screen.addView(layer, FrameLayout.LayoutParams(-1, -1)); editLayer = layer
        handles.mode = mode
        handles.isVisible = type != PreviewKeyboard.CUSTOM || customLayout != null
        updatePreview()
    }

    private fun leaveEditor() {
        if (editMode == null) return
        (previewFrame.parent as? ViewGroup)?.removeView(previewFrame)
        screen.removeView(editLayer); editLayer = null
        previewHolder.addView(previewFrame, ViewGroup.LayoutParams(previewScreenWidth(), ViewGroup.LayoutParams.WRAP_CONTENT))
        editMode = null; handles.mode = null; handles.isVisible = false
        normalScroll.isVisible = true; backCallback?.isEnabled = false
        if (actionBarWasVisible) (activity as? AppCompatActivity)?.supportActionBar?.show()
        actionBarWasVisible = false
        buildBody(); updatePreview()
    }
    private fun buildShapeBody() {
        val ctx = requireContext()
        fun slider(title: Int, current: Int, maximum: Int, minimum: Int = 0, viewId: Int = View.NO_ID, changed: (Int) -> Unit) {
            val caption = TextView(ctx).apply { text = getString(title, current); setPadding(0, dp(8), 0, 0) }
            body.addView(caption)
            body.addView(SeekBar(ctx).apply {
                id = viewId; max = maximum - minimum; progress = current - minimum; contentDescription = caption.text
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val actual = value + minimum
                            caption.text = getString(title, actual); contentDescription = caption.text; changed(actual)
                        }
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                })
            })
        }
        fun choices(heading: Int, options: List<Pair<Int, Int>>, checked: Int, viewId: Int = View.NO_ID, action: (Int) -> Unit) {
            body.addView(label(heading)); body.addView(radio(options, true).apply { id = viewId; check(checked); setOnCheckedChangeListener { _, id -> action(id) } })
        }
        if (type == PreviewKeyboard.QWERTY) slider(R.string.keyboard_geometry_split, geometry.geometry.splitPercent, 40) {
            saveGeometry(geometry.copy(geometry = geometry.geometry.copy(splitPercent = it)))
        }
        slider(R.string.keyboard_geometry_curve, geometry.geometry.curveDegrees, 25) {
            saveGeometry(geometry.copy(geometry = geometry.geometry.copy(curveDegrees = it)))
        }
        choices(R.string.keyboard_geometry_curve_direction, listOf(1 to R.string.keyboard_geometry_curve_down, 2 to R.string.keyboard_geometry_curve_up),
            if (geometry.geometry.curveDirection == KeyboardCurveDirection.UP) 2 else 1) {
            saveGeometry(geometry.copy(geometry = geometry.geometry.copy(curveDirection = if (it == 2) KeyboardCurveDirection.UP else KeyboardCurveDirection.DOWN)))
        }
        if (type != PreviewKeyboard.QWERTY) choices(R.string.keyboard_geometry_hand,
            listOf(1 to R.string.keyboard_geometry_right_hand, 2 to R.string.keyboard_geometry_left_hand),
            if (geometry.geometry.hand == KeyboardCurveHand.LEFT) 2 else 1) {
            saveGeometry(geometry.copy(geometry = geometry.geometry.copy(hand = if (it == 2) KeyboardCurveHand.LEFT else KeyboardCurveHand.RIGHT)))
        }
        choices(R.string.keyboard_geometry_height, listOf(1 to R.string.keyboard_geometry_height_auto, 2 to R.string.keyboard_geometry_height_fixed),
            if (geometry.geometry.autoHeight) 1 else 2) { saveGeometry(geometry.copy(geometry = geometry.geometry.copy(autoHeight = it == 1))) }
        val candidates = if (type == PreviewKeyboard.QWERTY) listOf(1 to R.string.keyboard_geometry_top, 2 to R.string.keyboard_geometry_center)
            else listOf(1 to R.string.keyboard_geometry_top, 3 to R.string.keyboard_geometry_left, 4 to R.string.keyboard_geometry_right)
        choices(R.string.keyboard_geometry_candidates, candidates, when (geometry.candidates) {
            GeometryCandidatePlacement.TOP -> 1; GeometryCandidatePlacement.CENTER -> 2; GeometryCandidatePlacement.LEFT -> 3; GeometryCandidatePlacement.RIGHT -> 4
        }, viewId = R.id.keyboard_geometry_candidate_placement) {
            val oldSide = geometry.candidates in setOf(GeometryCandidatePlacement.LEFT, GeometryCandidatePlacement.RIGHT)
            val placement = when (it) {
                2 -> GeometryCandidatePlacement.CENTER; 3 -> GeometryCandidatePlacement.LEFT; 4 -> GeometryCandidatePlacement.RIGHT; else -> GeometryCandidatePlacement.TOP
            }
            saveGeometry(geometry.copy(candidates = placement))
            if (oldSide != (placement in setOf(GeometryCandidatePlacement.LEFT, GeometryCandidatePlacement.RIGHT))) buildBody()
        }
        if (type != PreviewKeyboard.QWERTY && geometry.candidates in setOf(GeometryCandidatePlacement.LEFT, GeometryCandidatePlacement.RIGHT)) {
            slider(R.string.keyboard_geometry_candidate_width, geometry.sideCandidateWidthPercent,
                SideCandidateSizing.MAX_PERCENT, SideCandidateSizing.MIN_PERCENT, R.id.keyboard_geometry_candidate_width) {
                saveGeometry(geometry.copy(sideCandidateWidthPercent = it))
            }
            choices(R.string.keyboard_geometry_candidate_order,
                listOf(1 to R.string.keyboard_geometry_candidate_order_horizontal, 2 to R.string.keyboard_geometry_candidate_order_vertical),
                if (geometry.sideCandidateVerticalText) 2 else 1, R.id.keyboard_geometry_candidate_order) {
                saveGeometry(geometry.copy(sideCandidateVerticalText = it == 2))
            }
        }
        body.addView(MaterialButton(ctx, null, MaterialR.attr.materialButtonOutlinedStyle).apply { isAllCaps = false; setText(R.string.keyboard_layout_reset_shape); setOnClickListener {
            geometryTarget()?.let { geometries.reset(it, landscape) }; loadValues(); updatePreview(); buildBody()
        } })
    }
    private fun updateWarning() {
        if (!::warning.isInitialized) return
        val metrics = resources.displayMetrics
        val screen = if (landscape) max(metrics.widthPixels, metrics.heightPixels) else min(metrics.widthPixels, metrics.heightPixels)
        val available = (screen - (size.marginStartDp + size.marginEndDp).coerceAtLeast(0) * metrics.density).coerceAtLeast(1f)
        val width = min(available, screen * size.widthPercent.coerceIn(1, 100) / 100f)
        val requested = width * geometry.geometry.splitPercent / 100f
        val gap = min(requested, (width - 240f * metrics.density).coerceAtLeast(0f))
        warning.text = buildList {
            if (type == PreviewKeyboard.QWERTY && gap > 0) add(getString(R.string.keyboard_geometry_glide_disabled))
            if (type == PreviewKeyboard.QWERTY && gap < requested) add(getString(R.string.keyboard_geometry_gap_limited))
            if (type == PreviewKeyboard.QWERTY && geometry.candidates == GeometryCandidatePlacement.CENTER && gap < 96f * metrics.density) add(getString(R.string.keyboard_geometry_candidate_fallback))
            if (type in listOf(PreviewKeyboard.TENKEY, PreviewKeyboard.SUMIRE) && geometry.candidates != GeometryCandidatePlacement.TOP && width < 256f * metrics.density) add(getString(R.string.keyboard_geometry_candidate_fallback))
            if (type in listOf(PreviewKeyboard.TENKEY, PreviewKeyboard.SUMIRE) && geometry.candidates in setOf(GeometryCandidatePlacement.LEFT, GeometryCandidatePlacement.RIGHT)) {
                val pixels = width.roundToInt()
                val actual = SideCandidateSizing.width(pixels, metrics.density, geometry.sideCandidateWidthPercent)
                val requestedSide = pixels * geometry.sideCandidateWidthPercent / 100
                if (actual > 0 && actual != requestedSide) add(getString(R.string.keyboard_geometry_candidate_width_limited))
            }
        }.joinToString("\n")
    }

    private fun showExactValues() {
        val ctx = requireContext(); val density = resources.displayMetrics.density
        val fields = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(16)) }
        val inputs = mutableListOf<TextInputLayout>()
        fun field(title: Int, value: String): EditText {
            val input = TextInputLayout(ctx, null, MaterialR.attr.textInputOutlinedStyle).apply {
                hint = getString(title); boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            }
            val edit = TextInputEditText(ctx).apply {
                setText(value); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                isSingleLine = true
            }
            input.addView(edit); inputs += input
            fields.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            return edit
        }
        val height = field(R.string.keyboard_layout_height, size.heightDp.toString())
        val width = field(R.string.keyboard_layout_width, size.widthPercent.toString())
        val bottomText = "%.2f".format(java.util.Locale.US, size.bottomMarginPx / density).trimEnd('0').trimEnd('.')
        val bottom = field(R.string.keyboard_layout_bottom, bottomText)
        val start = field(R.string.keyboard_layout_start, size.marginStartDp.toString())
        val end = field(R.string.keyboard_layout_end, size.marginEndDp.toString())
        val card = MaterialCardView(ctx).apply {
            id = R.id.keyboard_layout_size_input; radius = dp(16).toFloat(); strokeWidth = dp(1)
            setStrokeColor(MaterialColors.getColor(this, MaterialR.attr.colorOutlineVariant, Color.LTGRAY))
            setCardBackgroundColor(MaterialColors.getColor(this, MaterialR.attr.colorSurfaceContainerLow, Color.WHITE))
            addView(fields)
        }
        val wrapper = LinearLayout(ctx).apply {
            setPadding(dp(16), 0, dp(16), 0); addView(card, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = MaterialAlertDialogBuilder(ctx).setTitle(R.string.keyboard_layout_exact_values).setView(ScrollView(ctx).apply { addView(wrapper) })
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val values = listOf(height, width, bottom, start, end).map { it.text.toString().toFloatOrNull() }
                if (values.any { it == null || !it.isFinite() || it < 0f || it > 100_000f }) {
                    inputs[values.indexOfFirst { it == null || !it.isFinite() || it < 0f || it > 100_000f }].error = getString(R.string.keyboard_layout_invalid)
                    return@setOnClickListener
                }
                inputs.forEach { it.error = null }
                val v = values.map { it!! }
                updateSize(size.copy(heightDp = v[0].roundToInt().coerceIn(100, 420), widthPercent = v[1].roundToInt().coerceIn(32, 100),
                    bottomMarginPx = if (bottom.text.toString() == bottomText) size.bottomMarginPx else (v[2] * density).roundToInt(),
                    marginStartDp = v[3].roundToInt(), marginEndDp = v[4].roundToInt()), true)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun observeCustomLayouts() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getLayouts().catch { showCustomError() }.collectLatest { updated ->
                layouts = updated
                val remembered = if (AppPreference.remember_last_custom_keyboard_preference == true) AppPreference.last_used_custom_keyboard_stable_id else null
                selectedCustomId = selectedCustomId?.takeIf { selected -> updated.any { it.stableId == selected } }
                    ?: remembered?.takeIf { selected -> updated.any { it.stableId == selected } } ?: updated.firstOrNull()?.stableId
                renderCustomChooser()
                loadSelectedCustom()
            }
        }
    }
    private fun renderCustomChooser() {
        customBox.removeAllViews()
        customBox.addView(label(R.string.keyboard_layout_custom))
        if (layouts.isEmpty()) {
            customBox.addView(TextView(requireContext()).apply { setText(R.string.keyboard_layout_no_custom) })
            customBox.addView(MaterialButton(requireContext(), null, MaterialR.attr.materialButtonOutlinedStyle).apply {
                isAllCaps = false; setText(R.string.keyboard_layout_create_custom); setOnClickListener { navigateSafely(R.id.keyboardListFragment) }
            })
            return
        }
        val spinner = Spinner(requireContext()).apply {
            id = R.id.keyboard_layout_custom_selector
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, layouts.map { it.name })
            setSelection(layouts.indexOfFirst { it.stableId == selectedCustomId }.coerceAtLeast(0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selected = layouts.getOrNull(position)?.stableId ?: return
                    if (selectedCustomId != selected) { selectedCustomId = selected; loadSelectedCustom() }
                }
            }
        }
        customBox.addView(spinner)
    }
    private fun loadSelectedCustom() {
        loadJob?.cancel(); customLayout = null; if (::handles.isInitialized) { handles.isVisible = false; updatePreview(); buildBody() }
        val selected = layouts.firstOrNull { it.stableId == selectedCustomId } ?: run {
            if (editMode != null) leaveEditor()
            return
        }
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            repository.getFullLayout(selected.layoutId).catch { showCustomError() }.collectLatest { layout ->
                customLayout = repository.convertLayout(layout)
                if (initialized) { handles.isVisible = editMode != null; updatePreview(); buildBody() }
            }
        }
    }
    private fun showCustomError() {
        if (!initialized) return
        customLayout = null; if (editMode != null) leaveEditor()
        handles.isVisible = false; updatePreview(); buildBody()
        customBox.addView(TextView(requireContext()).apply { setText(R.string.keyboard_layout_custom_error) })
    }
}

private enum class SizeEditMode { SIZE, POSITION }

/** The preview is a physical keyboard-sized surface even in a narrower settings window. */
@SuppressLint("ViewConstructor")
private class UnscaledPreviewFrame(context: android.content.Context, private val screenWidth: () -> Int) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(screenWidth(), MeasureSpec.EXACTLY), heightMeasureSpec)
    }
}

/** Measures the keyboard at its real size even if part of it lies above the viewport. */
@SuppressLint("ViewConstructor")
private class KeyboardPositionCanvas(context: android.content.Context, private val keyboard: View, private val screenWidth: Int) : ViewGroup(context) {
    var availableHeight = 0
        private set
    init { addView(keyboard); clipChildren = true }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec); val height = MeasureSpec.getSize(heightMeasureSpec)
        keyboard.measure(MeasureSpec.makeMeasureSpec(screenWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(width, height)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val navigationInset = ViewCompat.getRootWindowInsets(this)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val frameId = resources.getIdentifier("navigation_bar_frame_height", "dimen", "android")
        // IME windows on Android 9+ extend into the navigation bar and receive the
        // navigation *frame* inset, which may be taller than an activity's inset.
        val imeInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && navigationInset > 0 && frameId != 0)
            max(navigationInset, resources.getDimensionPixelSize(frameId)) else navigationInset
        @Suppress("DEPRECATION")
        val displaySize = Point().also { context.getSystemService(WindowManager::class.java).defaultDisplay.getRealSize(it) }
        val location = IntArray(2); getLocationOnScreen(location)
        val bottomOffset = (location[1] + height - (displaySize.y - imeInset)).coerceAtLeast(0)
        availableHeight = (height - bottomOffset).coerceAtLeast(0)
        val screenLeft = -location[0]
        keyboard.layout(screenLeft, height - bottomOffset - keyboard.measuredHeight,
            screenLeft + screenWidth, height - bottomOffset)
    }
}

/** Four resize handles or direct movement. Drags remain drafts until released. */
@SuppressLint("ViewConstructor")
private class SizeHandles(context: android.content.Context, private val preview: GeometryPreview,
    private val current: () -> IndependentKeyboardSize, private val draft: (IndependentKeyboardSize) -> Unit,
    private val commit: (IndependentKeyboardSize) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val resizeTargetHalfDepth = 24f * density
    private val resizeTargetHalfLength = 36f * density
    private val handleColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.rgb(54, 106, 197))
    private val handleIconColor = MaterialColors.getColor(this, MaterialR.attr.colorOnPrimary, Color.WHITE)
    private val activeColor = MaterialColors.getColor(this, MaterialR.attr.colorPrimaryContainer, Color.rgb(210, 225, 255))
    private val activeIconColor = MaterialColors.getColor(this, MaterialR.attr.colorOnPrimaryContainer, Color.BLACK)
    private val handleOutlineColor = MaterialColors.getColor(this, MaterialR.attr.colorSurface, Color.WHITE)
    private val gripColor = MaterialColors.getColor(this, MaterialR.attr.colorOnSurfaceVariant, Color.DKGRAY)
    private var gesture = 0
    private var originX = 0f; private var originY = 0f
    private var initialSurfaceWidth = 0f
    private var initialLeft = 0f; private var initialRight = 0f
    private var maxBottomMargin = Int.MAX_VALUE
    private var initial = IndependentKeyboardSize()
    private var latest = initial
    var mode: SizeEditMode? = null
        set(value) {
            field = value
            contentDescription = context.getString(if (value == SizeEditMode.POSITION) R.string.keyboard_layout_move else R.string.keyboard_layout_resize)
            if (value == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) systemGestureExclusionRects = emptyList()
            invalidate()
        }
    init { contentDescription = context.getString(R.string.keyboard_layout_resize); isFocusable = true }
    override fun onDraw(canvas: Canvas) {
        val b = preview.adjustmentBounds() ?: return
        updateGestureExclusion(b)
        when (mode) {
            SizeEditMode.SIZE -> (2..5).forEach { edge -> drawResizeGrip(canvas, b, edge) }
            SizeEditMode.POSITION -> drawMoveHandle(canvas, handleCenter(b, 1))
            null -> Unit
        }
    }

    /** Keep the move control or the entire resize touch target inside the view. */
    private fun handleCenter(bounds: RectF, handle: Int): PointF {
        val side = handle == 4 || handle == 5
        val halfWidth = if (handle == 1) 20f * density else if (side) resizeTargetHalfDepth else resizeTargetHalfLength
        val halfHeight = if (handle == 1) 20f * density else if (side) resizeTargetHalfLength else resizeTargetHalfDepth
        val x = when (handle) { 4 -> bounds.left; 5 -> bounds.right; else -> bounds.centerX() }
        val y = when (handle) { 2 -> bounds.top; 3 -> bounds.bottom; else -> bounds.centerY() }
        fun visibleCenter(value: Float, limit: Int, halfSize: Float): Float =
            if (limit < halfSize * 2) limit / 2f else value.coerceIn(halfSize, limit - halfSize)
        return PointF(visibleCenter(x, width, halfWidth), visibleCenter(y, height, halfHeight))
    }

    /** The rounded grip fits within the touch target, including its active outline. */
    private fun drawResizeGrip(canvas: Canvas, bounds: RectF, edge: Int) {
        val side = edge == 4 || edge == 5
        val active = gesture == edge
        val length = (if (active) 68f else 64f) * density
        val thickness = (if (active) 14f else 12f) * density
        val outline = 1.5f * density
        val halfWidth = (if (side) thickness else length) / 2f
        val halfHeight = (if (side) length else thickness) / 2f
        val x = when (edge) { 4 -> bounds.left; 5 -> bounds.right; else -> bounds.centerX() }
        val y = when (edge) { 2 -> bounds.top; 3 -> bounds.bottom; else -> bounds.centerY() }
        fun visibleCenter(value: Float, limit: Int, halfSize: Float): Float =
            if (limit < halfSize * 2) limit / 2f else value.coerceIn(halfSize, limit - halfSize)
        val cx = visibleCenter(x, width, halfWidth + outline)
        val cy = visibleCenter(y, height, halfHeight + outline)
        val rect = RectF(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = outline * 2
        paint.color = handleOutlineColor
        canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (active) handleColor else gripColor
        canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint)
    }

    private fun drawMoveHandle(canvas: Canvas, center: PointF) {
        val halfWidth = 20 * density
        val halfHeight = 20 * density
        val radius = min(halfWidth, halfHeight)
        val rect = RectF(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
        paint.style = Paint.Style.FILL
        paint.color = if (gesture == 1) activeColor else handleColor
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * density
        paint.color = handleOutlineColor
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.color = if (gesture == 1) activeIconColor else handleIconColor
        paint.strokeWidth = 2 * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        drawArrows(canvas, center, horizontal = true, lengthDp = 10.5f)
        drawArrows(canvas, center, horizontal = false, lengthDp = 10.5f)
        paint.style = Paint.Style.FILL
    }

    /** A double-headed arrow shows the direction in which this control can be dragged. */
    private fun drawArrows(canvas: Canvas, center: PointF, horizontal: Boolean, lengthDp: Float) {
        val end = lengthDp * density
        val head = 3.5f * density
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            if (horizontal) canvas.drawLine(center.x + x1, center.y + y1, center.x + x2, center.y + y2, paint)
            else canvas.drawLine(center.x + y1, center.y + x1, center.x + y2, center.y + x2, paint)
        }
        line(-end, 0f, end, 0f)
        line(-end, 0f, -end + head, -head); line(-end, 0f, -end + head, head)
        line(end, 0f, end - head, -head); line(end, 0f, end - head, head)
    }
    private fun updateGestureExclusion(bounds: RectF) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val hit = resizeTargetHalfDepth.roundToInt()
        val sideCenterY = handleCenter(bounds, 4).y
        val top = (if (mode == SizeEditMode.POSITION) bounds.top else sideCenterY - resizeTargetHalfLength).roundToInt().coerceIn(0, height)
        val bottom = (if (mode == SizeEditMode.POSITION) bounds.bottom else sideCenterY + resizeTargetHalfLength).roundToInt().coerceIn(top, height)
        val leftNearEdge = if (mode == SizeEditMode.POSITION) bounds.left <= hit else handleCenter(bounds, 4).x - hit < hit
        val rightNearEdge = if (mode == SizeEditMode.POSITION) width - bounds.right <= hit else width - handleCenter(bounds, 5).x - hit < hit
        val rects = buildList {
            if (mode != null && leftNearEdge) add(Rect(0, top, min(width, hit * 2), bottom))
            if (mode != null && rightNearEdge) add(Rect(max(0, width - hit * 2), top, width, bottom))
        }
        if (systemGestureExclusionRects != rects) systemGestureExclusionRects = rects
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val b = preview.adjustmentBounds() ?: return false
                fun near(handle: Int): Boolean {
                    val center = handleCenter(b, handle)
                    val side = handle == 4 || handle == 5
                    val halfWidth = if (side) resizeTargetHalfDepth else resizeTargetHalfLength
                    val halfHeight = if (side) resizeTargetHalfLength else resizeTargetHalfDepth
                    return abs(event.x - center.x) <= halfWidth && abs(event.y - center.y) <= halfHeight
                }
                val edit = mode ?: return false
                gesture = when {
                    edit == SizeEditMode.POSITION && b.contains(event.x, event.y) -> 1
                    edit != SizeEditMode.SIZE -> 0
                    near(2) -> 2
                    near(3) -> 3
                    near(4) -> 4
                    near(5) -> 5
                    else -> 0
                }
                if (gesture == 0) return false
                originX = event.rawX; originY = event.rawY; initial = current(); latest = initial
                initialSurfaceWidth = preview.surfaceWidth() ?: b.width()
                initialLeft = b.left; initialRight = b.right
                val canvas = parent?.parent as? KeyboardPositionCanvas
                maxBottomMargin = canvas?.let { (it.availableHeight - b.bottom).roundToInt().coerceAtLeast(initial.bottomMarginPx) }
                    ?: Int.MAX_VALUE
                parent?.requestDisallowInterceptTouchEvent(true); invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gesture == 0) return false
                val dx = event.rawX - originX; val dy = event.rawY - originY
                val widthPx = width.coerceAtLeast(1).toFloat()
                latest = when (gesture) {
                    1 -> {
                        val maxStart = ((widthPx - initialSurfaceWidth - initial.marginEndDp * density) / density).roundToInt().coerceAtLeast(0)
                        val maxEnd = ((widthPx - initialSurfaceWidth - initial.marginStartDp * density) / density).roundToInt().coerceAtLeast(0)
                        initial.copy(bottomMarginPx = (initial.bottomMarginPx - dy).roundToInt().coerceIn(0, maxBottomMargin),
                            marginStartDp = if (initial.positionEnd) initial.marginStartDp else (initial.marginStartDp + dx / density).roundToInt().coerceIn(0, maxStart),
                            marginEndDp = if (initial.positionEnd) (initial.marginEndDp - dx / density).roundToInt().coerceIn(0, maxEnd) else initial.marginEndDp)
                    }
                    2 -> initial.copy(heightDp = (initial.heightDp - dy / density).roundToInt().coerceIn(100, 420))
                    3 -> initial.copy(heightDp = (initial.heightDp + dy / density).roundToInt().coerceIn(100, 420), bottomMarginPx = (initial.bottomMarginPx - dy).roundToInt().coerceAtLeast(0))
                    4, 5 -> if (abs(dx) < 1f) initial else {
                        // Keep the opposite edge fixed while the dragged edge follows the finger.
                        val leftHandle = gesture == 4
                        val widthPercent = ((initialSurfaceWidth + if (leftHandle) -dx else dx) * 100 / widthPx)
                            .roundToInt().coerceIn(32, 100)
                        val newWidth = widthPx * widthPercent / 100f
                        if (leftHandle) initial.copy(widthPercent = widthPercent, positionEnd = true,
                            marginEndDp = ((widthPx - initialRight) / density).roundToInt().coerceAtLeast(0),
                            marginStartDp = min(initial.marginStartDp, ((initialRight - newWidth) / density).roundToInt().coerceAtLeast(0)))
                        else initial.copy(widthPercent = widthPercent, positionEnd = false,
                            marginStartDp = (initialLeft / density).roundToInt().coerceAtLeast(0),
                            marginEndDp = min(initial.marginEndDp, ((widthPx - initialLeft - newWidth) / density).roundToInt().coerceAtLeast(0)))
                    }
                    else -> initial
                }
                draft(latest); return true
            }
            MotionEvent.ACTION_UP -> { if (gesture != 0) { gesture = 0; invalidate(); commit(latest); performClick(); return true } }
            MotionEvent.ACTION_CANCEL -> { if (gesture != 0) { gesture = 0; invalidate(); draft(initial); return true } }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
