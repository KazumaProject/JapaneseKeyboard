package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryKeyboard
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.KeyboardSurfaceSizePreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.KeyboardSurfaceSizeTarget

/** QWERTY and romaji use the same center-gap preference. */
class QwertyGapFragment : Fragment() {
    private var landscape = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        landscape = savedInstanceState?.getBoolean("landscape") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("landscape", landscape)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val geometries = KeyboardGeometryPreferences(prefs)
        val sizes = KeyboardSurfaceSizePreferences(prefs)
        val density = resources.displayMetrics.density
        val page = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (24 * density).toInt()) }
        val direction = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val portrait = RadioButton(context).apply { id = View.generateViewId(); setText(R.string.keyboard_geometry_portrait) }
        val horizontal = RadioButton(context).apply { id = View.generateViewId(); setText(R.string.keyboard_geometry_landscape) }
        direction.addView(portrait); direction.addView(horizontal); direction.check(if (landscape) horizontal.id else portrait.id)
        page.addView(direction)
        val romaji = arguments?.getBoolean("romaji") == true
        val preview = GeometryPreview(context, if (romaji) PreviewKeyboard.ROMAJI else PreviewKeyboard.QWERTY)
        page.addView(preview, LinearLayout.LayoutParams(-1, -2))
        val label = TextView(context)
        page.addView(label)
        val seek = SeekBar(context).apply { max = 40 }
        page.addView(seek)
        val warning = TextView(context).apply { setText(R.string.keyboard_geometry_glide_disabled) }
        page.addView(warning)
        fun refresh() {
            val geometry = geometries.read(GeometryKeyboard.QWERTY, landscape)
            val size = sizes.read(if (romaji) KeyboardSurfaceSizeTarget.ROMAJI else KeyboardSurfaceSizeTarget.QWERTY, landscape)
            label.text = getString(R.string.keyboard_geometry_split, geometry.geometry.splitPercent)
            warning.visibility = if (geometry.geometry.splitPercent > 0) View.VISIBLE else View.GONE
            if (seek.progress != geometry.geometry.splitPercent) seek.progress = geometry.geometry.splitPercent
            preview.update(geometry, landscape, size.widthPercent.coerceIn(1, 100), size.positionEnd, size)
        }
        direction.setOnCheckedChangeListener { _, id -> landscape = id == horizontal.id; refresh() }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) { geometries.saveQwertyGap(landscape, progress); refresh() }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        page.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.candidate_display_reset_gap)
            setOnClickListener { geometries.saveQwertyGap(landscape, 0); refresh() }
        })
        refresh()
        return ScrollView(context).apply { isFillViewport = true; addView(page) }
    }
}
