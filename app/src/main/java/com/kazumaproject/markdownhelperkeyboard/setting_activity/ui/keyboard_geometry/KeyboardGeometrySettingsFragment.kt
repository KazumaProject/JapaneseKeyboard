package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.kazumaproject.core.ui.geometry.KeyboardCurveDirection
import com.kazumaproject.core.ui.geometry.KeyboardCurveHand
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryCandidatePlacement
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryKeyboard
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlin.math.min

/** Settings and an illustrative renderer; no IME view or input controller is created here. */
class KeyboardGeometrySettingsFragment : Fragment() {
    private lateinit var store: KeyboardGeometryPreferences
    private lateinit var keyboard: GeometryKeyboard
    private var landscape = false
    private var updating = false
    private var value = KeyboardGeometryPreference()
    private lateinit var preview: GeometryPreview
    private lateinit var splitLabel: TextView
    private lateinit var curveLabel: TextView
    private lateinit var warning: TextView
    private lateinit var split: SeekBar
    private lateinit var curve: SeekBar
    private lateinit var orientation: RadioGroup
    private lateinit var hand: RadioGroup
    private lateinit var curveDirection: RadioGroup
    private lateinit var height: RadioGroup
    private lateinit var candidates: RadioGroup

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        store = KeyboardGeometryPreferences(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        keyboard = runCatching { GeometryKeyboard.valueOf(requireArguments().getString("geometryKeyboard", "QWERTY")) }.getOrDefault(GeometryKeyboard.QWERTY)
        landscape = savedInstanceState?.getBoolean("landscape")
            ?: (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val context = requireContext()
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }
        fun heading(text: Int) = TextView(context).apply { setText(text); textSize = 16f; setPadding(0, dp(14), 0, dp(4)) }.also(content::addView)
        fun group(options: List<Pair<Int, Int>>, vertical: Boolean = false): RadioGroup = RadioGroup(context).apply {
            orientation = if (vertical) RadioGroup.VERTICAL else RadioGroup.HORIZONTAL
            options.forEach { (id, label) -> addView(RadioButton(context).apply { this.id = id; setText(label) },
                if (vertical) RadioGroup.LayoutParams(-1, -2) else RadioGroup.LayoutParams(0, -2, 1f)) }
        }.also(content::addView)
        orientation = group(listOf(1 to R.string.keyboard_geometry_portrait, 2 to R.string.keyboard_geometry_landscape))
        heading(R.string.keyboard_geometry_preview)
        preview = GeometryPreview(context, keyboard).also { content.addView(it, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        content.addView(TextView(context).apply { setText(R.string.keyboard_geometry_preview_description) })
        splitLabel = TextView(context).also(content::addView)
        split = SeekBar(context).apply { max = 40 }.also(content::addView)
        splitLabel.visibility = if (keyboard == GeometryKeyboard.QWERTY) View.VISIBLE else View.GONE
        split.visibility = splitLabel.visibility
        curveLabel = TextView(context).also(content::addView)
        curve = SeekBar(context).apply { max = 25 }.also(content::addView)
        heading(R.string.keyboard_geometry_curve_direction)
        curveDirection = group(listOf(11 to R.string.keyboard_geometry_curve_down, 12 to R.string.keyboard_geometry_curve_up), vertical = true)
        val handLabel = heading(R.string.keyboard_geometry_hand)
        hand = group(listOf(3 to R.string.keyboard_geometry_right_hand, 4 to R.string.keyboard_geometry_left_hand))
        handLabel.visibility = if (keyboard == GeometryKeyboard.QWERTY) View.GONE else View.VISIBLE
        hand.visibility = handLabel.visibility
        heading(R.string.keyboard_geometry_height)
        height = group(listOf(5 to R.string.keyboard_geometry_height_auto, 6 to R.string.keyboard_geometry_height_fixed), vertical = true)
        heading(R.string.keyboard_geometry_candidates)
        candidates = group(if (keyboard == GeometryKeyboard.QWERTY) listOf(7 to R.string.keyboard_geometry_top, 8 to R.string.keyboard_geometry_center)
            else listOf(7 to R.string.keyboard_geometry_top, 9 to R.string.keyboard_geometry_left, 10 to R.string.keyboard_geometry_right), vertical = true)
        warning = TextView(context).apply { setPadding(0, dp(12), 0, dp(8)) }.also(content::addView)
        content.addView(Button(context).apply { setText(R.string.keyboard_geometry_reset); setOnClickListener { store.reset(keyboard, landscape); load() } })

        orientation.setOnCheckedChangeListener { _, id -> if (!updating) { landscape = id == 2; load() } }
        hand.setOnCheckedChangeListener { _, id -> if (!updating) save(value.copy(geometry = value.geometry.copy(hand = if (id == 4) KeyboardCurveHand.LEFT else KeyboardCurveHand.RIGHT))) }
        curveDirection.setOnCheckedChangeListener { _, id -> if (!updating) save(value.copy(geometry = value.geometry.copy(curveDirection = if (id == 12) KeyboardCurveDirection.UP else KeyboardCurveDirection.DOWN))) }
        height.setOnCheckedChangeListener { _, id -> if (!updating) save(value.copy(geometry = value.geometry.copy(autoHeight = id == 5))) }
        candidates.setOnCheckedChangeListener { _, id -> if (!updating) save(value.copy(candidates = when (id) {
            8 -> GeometryCandidatePlacement.CENTER; 9 -> GeometryCandidatePlacement.LEFT; 10 -> GeometryCandidatePlacement.RIGHT; else -> GeometryCandidatePlacement.TOP
        })) }
        fun listener(change: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser && !updating) change(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        split.setOnSeekBarChangeListener(listener { save(value.copy(geometry = value.geometry.copy(splitPercent = it))) })
        curve.setOnSeekBarChangeListener(listener { save(value.copy(geometry = value.geometry.copy(curveDegrees = it))) })
        load()
        return ScrollView(context).apply { addView(content) }
    }

    private fun save(next: KeyboardGeometryPreference) { value = next; store.save(keyboard, landscape, next); render() }
    private fun load() { value = store.read(keyboard, landscape); render() }
    private fun render() {
        updating = true
        orientation.check(if (landscape) 2 else 1)
        split.progress = value.geometry.splitPercent
        curve.progress = value.geometry.curveDegrees
        splitLabel.text = getString(R.string.keyboard_geometry_split, value.geometry.splitPercent)
        curveLabel.text = getString(R.string.keyboard_geometry_curve, value.geometry.curveDegrees)
        split.contentDescription = splitLabel.text
        curve.contentDescription = curveLabel.text
        hand.check(if (value.geometry.hand == KeyboardCurveHand.LEFT) 4 else 3)
        curveDirection.check(if (value.geometry.curveDirection == KeyboardCurveDirection.UP) 12 else 11)
        height.check(if (value.geometry.autoHeight) 5 else 6)
        candidates.check(when (value.candidates) {
            GeometryCandidatePlacement.TOP -> 7; GeometryCandidatePlacement.CENTER -> 8
            GeometryCandidatePlacement.LEFT -> 9; GeometryCandidatePlacement.RIGHT -> 10
        })
        val metrics = resources.displayMetrics
        val screenWidth = if (landscape) maxOf(metrics.widthPixels, metrics.heightPixels) else minOf(metrics.widthPixels, metrics.heightPixels)
        val widthPercent = when {
            keyboard == GeometryKeyboard.QWERTY && landscape -> AppPreference.qwerty_keyboard_width_landscape
            keyboard == GeometryKeyboard.QWERTY -> AppPreference.qwerty_keyboard_width
            landscape -> AppPreference.keyboard_width_landscape
            else -> AppPreference.keyboard_width
        } ?: 100
        val startMargin = when {
            keyboard == GeometryKeyboard.QWERTY && landscape -> AppPreference.qwerty_keyboard_margin_start_dp_landscape
            keyboard == GeometryKeyboard.QWERTY -> AppPreference.qwerty_keyboard_margin_start_dp
            landscape -> AppPreference.keyboard_margin_start_dp_landscape
            else -> AppPreference.keyboard_margin_start_dp
        } ?: 0
        val endMargin = when {
            keyboard == GeometryKeyboard.QWERTY && landscape -> AppPreference.qwerty_keyboard_margin_end_dp_landscape
            keyboard == GeometryKeyboard.QWERTY -> AppPreference.qwerty_keyboard_margin_end_dp
            landscape -> AppPreference.keyboard_margin_end_dp_landscape
            else -> AppPreference.keyboard_margin_end_dp
        } ?: 0
        val availableWidth = (screenWidth - (startMargin.coerceAtLeast(0) + endMargin.coerceAtLeast(0)) * metrics.density).coerceAtLeast(0f)
        val bodyWidth = if (keyboard == GeometryKeyboard.QWERTY) min(availableWidth, screenWidth * widthPercent.coerceIn(1, 100) / 100f) else availableWidth
        val alignEnd = when {
            keyboard == GeometryKeyboard.QWERTY && landscape -> AppPreference.qwerty_keyboard_position_landscape
            keyboard == GeometryKeyboard.QWERTY -> AppPreference.qwerty_keyboard_position
            landscape -> AppPreference.keyboard_position_landscape
            else -> AppPreference.keyboard_position
        } ?: true
        preview.update(value, landscape, widthPercent.coerceIn(1, 100), alignEnd)
        val requestedGap = bodyWidth * value.geometry.splitPercent / 100f
        val effectiveGap = min(requestedGap, (bodyWidth - 240f * metrics.density).coerceAtLeast(0f))
        warning.text = buildList {
            if (keyboard == GeometryKeyboard.QWERTY && effectiveGap > 0f) add(getString(R.string.keyboard_geometry_glide_disabled))
            if (keyboard == GeometryKeyboard.QWERTY && effectiveGap < requestedGap) add(getString(R.string.keyboard_geometry_gap_limited))
            if (keyboard == GeometryKeyboard.QWERTY && value.candidates == GeometryCandidatePlacement.CENTER && effectiveGap < 96f * metrics.density) add(getString(R.string.keyboard_geometry_candidate_fallback))
            if (keyboard != GeometryKeyboard.QWERTY && value.candidates != GeometryCandidatePlacement.TOP && availableWidth < 256f * metrics.density) add(getString(R.string.keyboard_geometry_candidate_fallback))
        }.joinToString("\n")
        updating = false
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("landscape", landscape); super.onSaveInstanceState(outState) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
