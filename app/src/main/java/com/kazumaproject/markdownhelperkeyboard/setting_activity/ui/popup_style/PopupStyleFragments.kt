package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.popup_style

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

class TenKeyPopupStyleSettingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        AppPreference.init(requireContext())
        return singleStyleEditor(
            preview = PopupStylePreviewView(requireContext()).apply { previewText = "あ" },
            initialStyle = PopupViewStyle(
                AppPreference.tenkey_popup_size_scale_percent ?: DEFAULT_SIZE,
                AppPreference.tenkey_popup_text_size_sp ?: DEFAULT_TENKEY_TEXT
            ),
            defaultTextSize = DEFAULT_TENKEY_TEXT,
            onChanged = {
                AppPreference.tenkey_popup_size_scale_percent = it.sizeScalePercent
                AppPreference.tenkey_popup_text_size_sp = it.textSizeSp
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
    }

    private fun setupMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {

            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}

class QwertyPopupStyleSettingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        AppPreference.init(requireContext())
        val root = editorRoot()
        root.addView(sectionTitle("Key preview popup"))
        root.addView(
            styleEditorSection(
                preview = PopupStylePreviewView(requireContext()).apply { previewText = "A" },
                initialStyle = PopupViewStyle(
                    AppPreference.qwerty_key_preview_popup_size_scale_percent ?: DEFAULT_SIZE,
                    AppPreference.qwerty_key_preview_popup_text_size_sp
                        ?: DEFAULT_QWERTY_PREVIEW_TEXT
                ),
                defaultTextSize = DEFAULT_QWERTY_PREVIEW_TEXT,
                onChanged = {
                    AppPreference.qwerty_key_preview_popup_size_scale_percent = it.sizeScalePercent
                    AppPreference.qwerty_key_preview_popup_text_size_sp = it.textSizeSp
                }
            )
        )
        root.addView(sectionTitle("Long-press variation popup"))
        root.addView(
            styleEditorSection(
                preview = PopupStylePreviewView(requireContext()).apply { previewText = "á" },
                initialStyle = PopupViewStyle(
                    AppPreference.qwerty_variation_popup_size_scale_percent ?: DEFAULT_SIZE,
                    AppPreference.qwerty_variation_popup_text_size_sp
                        ?: DEFAULT_QWERTY_VARIATION_TEXT
                ),
                defaultTextSize = DEFAULT_QWERTY_VARIATION_TEXT,
                onChanged = {
                    AppPreference.qwerty_variation_popup_size_scale_percent = it.sizeScalePercent
                    AppPreference.qwerty_variation_popup_text_size_sp = it.textSizeSp
                }
            )
        )
        return scroll(root)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
    }

    private fun setupMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {

            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}

class FlickKeyboardPopupStyleListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = editorRoot()
        listOf(
            FlickTarget.DIRECTIONAL to ("通常入力" to "通常入力の方向別ポップアップ"),
            FlickTarget.CROSS to ("長押し、特殊キー" to "キーを長押し、特殊なキーをフリックしたときに表示されるポップアップ"),
            FlickTarget.STANDARD to ("サークル入力" to "サークル入力のポップアップ"),
            FlickTarget.TFBI to ("２段 / ３段フリック入力" to "２段フリック入力・３段フリック入力のポップアップ"),
        ).forEach { (target, texts) ->
            root.addView(listRow(texts.first, texts.second) {
                findNavController().navigate(
                    R.id.action_flickKeyboardPopupStyleListFragment_to_flickKeyboardPopupStyleEditFragment,
                    bundleOf("target" to target.name)
                )
            })
        }
        return scroll(root)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
    }

    private fun setupMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {

            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}

class FlickKeyboardPopupStyleEditFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        AppPreference.init(requireContext())
        val target = FlickTarget.from(arguments?.getString("target"))
        val preview = FlickPopupStylePreviewView(requireContext())
        return singleStyleEditor(
            preview = preview,
            initialStyle = readStyle(target),
            defaultTextSize = target.defaultTextSize,
            onChanged = { writeStyle(target, it) },
            flickTarget = target
        )
    }

    private fun readStyle(target: FlickTarget): PopupViewStyle {
        return when (target) {
            FlickTarget.DIRECTIONAL -> PopupViewStyle(
                AppPreference.flick_directional_popup_size_scale_percent ?: DEFAULT_SIZE,
                AppPreference.flick_directional_popup_text_size_sp ?: DEFAULT_DIRECTIONAL_TEXT
            )

            FlickTarget.CROSS -> PopupViewStyle(
                AppPreference.flick_cross_popup_size_scale_percent ?: DEFAULT_SIZE,
                AppPreference.flick_cross_popup_text_size_sp ?: DEFAULT_CROSS_TEXT
            )

            FlickTarget.STANDARD -> PopupViewStyle(
                AppPreference.flick_standard_popup_size_scale_percent ?: DEFAULT_SIZE,
                AppPreference.flick_standard_popup_text_size_sp ?: DEFAULT_STANDARD_TEXT
            )

            FlickTarget.TFBI -> PopupViewStyle(
                AppPreference.flick_tfbi_popup_size_scale_percent ?: DEFAULT_SIZE,
                AppPreference.flick_tfbi_popup_text_size_sp ?: DEFAULT_TFBI_TEXT
            )
        }
    }

    private fun writeStyle(target: FlickTarget, style: PopupViewStyle) {
        when (target) {
            FlickTarget.DIRECTIONAL -> {
                AppPreference.flick_directional_popup_size_scale_percent = style.sizeScalePercent
                AppPreference.flick_directional_popup_text_size_sp = style.textSizeSp
            }

            FlickTarget.CROSS -> {
                AppPreference.flick_cross_popup_size_scale_percent = style.sizeScalePercent
                AppPreference.flick_cross_popup_text_size_sp = style.textSizeSp
            }

            FlickTarget.STANDARD -> {
                AppPreference.flick_standard_popup_size_scale_percent = style.sizeScalePercent
                AppPreference.flick_standard_popup_text_size_sp = style.textSizeSp
            }

            FlickTarget.TFBI -> {
                AppPreference.flick_tfbi_popup_size_scale_percent = style.sizeScalePercent
                AppPreference.flick_tfbi_popup_text_size_sp = style.textSizeSp
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
    }

    private fun setupMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {

            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}

private enum class FlickTarget(
    val defaultTextSize: Float,
    val previewTarget: FlickPopupStylePreviewView.Target
) {
    DIRECTIONAL(DEFAULT_DIRECTIONAL_TEXT, FlickPopupStylePreviewView.Target.DIRECTIONAL),
    CROSS(DEFAULT_CROSS_TEXT, FlickPopupStylePreviewView.Target.CROSS),
    STANDARD(DEFAULT_STANDARD_TEXT, FlickPopupStylePreviewView.Target.STANDARD),
    TFBI(DEFAULT_TFBI_TEXT, FlickPopupStylePreviewView.Target.TFBI);

    companion object {
        fun from(value: String?): FlickTarget {
            return entries.firstOrNull { it.name == value } ?: DIRECTIONAL
        }
    }
}

private fun Fragment.singleStyleEditor(
    preview: View,
    initialStyle: PopupViewStyle,
    defaultTextSize: Float,
    onChanged: (PopupViewStyle) -> Unit,
    flickTarget: FlickTarget? = null
): View {
    val root = editorRoot()
    root.addView(
        styleEditorSection(
            preview = preview,
            initialStyle = initialStyle,
            defaultTextSize = defaultTextSize,
            onChanged = onChanged,
            flickTarget = flickTarget
        )
    )
    return scroll(root)
}

private fun Fragment.styleEditorSection(
    preview: View,
    initialStyle: PopupViewStyle,
    defaultTextSize: Float,
    onChanged: (PopupViewStyle) -> Unit,
    flickTarget: FlickTarget? = null
): View {
    val context = requireContext()
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(16))
    }
    val sizeLabel = valueLabel("Size", "${initialStyle.sizeScalePercent}%")
    val textLabel = valueLabel("Text size", String.format("%.1fsp", initialStyle.textSizeSp))
    val sizeSeek = SeekBar(context).apply {
        max = MAX_SIZE - MIN_SIZE
        progress = initialStyle.sizeScalePercent.coerceIn(MIN_SIZE, MAX_SIZE) - MIN_SIZE
    }
    val textSeek = SeekBar(context).apply {
        max = (MAX_TEXT - MIN_TEXT).toInt()
        progress = (initialStyle.textSizeSp.coerceIn(MIN_TEXT, MAX_TEXT) - MIN_TEXT).toInt()
    }

    fun currentStyle(): PopupViewStyle {
        return PopupViewStyle(
            MIN_SIZE + sizeSeek.progress,
            MIN_TEXT + textSeek.progress
        )
    }

    fun render() {
        val style = currentStyle()
        sizeLabel.second.text = "${style.sizeScalePercent}%"
        textLabel.second.text = String.format("%.1fsp", style.textSizeSp)
        when (preview) {
            is PopupStylePreviewView -> preview.applyStyle(style)
            is FlickPopupStylePreviewView -> preview.applyStyle(
                flickTarget?.previewTarget ?: FlickPopupStylePreviewView.Target.DIRECTIONAL,
                style
            )
        }
        onChanged(style)
    }

    val listener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            render()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }
    sizeSeek.setOnSeekBarChangeListener(listener)
    textSeek.setOnSeekBarChangeListener(listener)

    preview.layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    root.addView(preview)
    root.addView(sizeLabel.first)
    root.addView(sizeSeek)
    root.addView(textLabel.first)
    root.addView(textSeek)
    root.addView(Button(context).apply {
        text = "デフォルトに戻す"
        setOnClickListener {
            sizeSeek.progress = DEFAULT_SIZE - MIN_SIZE
            textSeek.progress = (defaultTextSize - MIN_TEXT).toInt()
            render()
        }
    })
    render()
    return root
}

private fun Fragment.editorRoot(): LinearLayout {
    return LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }
}

private fun Fragment.scroll(child: View): View {
    return ScrollView(requireContext()).apply {
        addView(child)
    }
}

private fun Fragment.sectionTitle(text: String): TextView {
    return TextView(requireContext()).apply {
        this.text = text
        textSize = 18f
        setPadding(0, dp(16), 0, dp(4))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
}

private fun Fragment.valueLabel(title: String, value: String): Pair<LinearLayout, TextView> {
    val valueView = TextView(requireContext()).apply {
        text = value
        textSize = 16f
    }
    val row = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(12), 0, 0)
        addView(TextView(requireContext()).apply {
            text = title
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(valueView)
    }
    return row to valueView
}

private fun Fragment.listRow(title: String, summary: String, onClick: () -> Unit): View {
    return LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(14), 0, dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        addView(TextView(requireContext()).apply {
            text = title
            textSize = 17f
        })
        addView(TextView(requireContext()).apply {
            text = summary
            textSize = 14f
            setTextColor(ColorCompat.secondaryText(requireContext()))
            setPadding(0, dp(4), 0, 0)
        })
    }
}

private fun Fragment.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

private object ColorCompat {
    fun secondaryText(context: android.content.Context): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        return if (typedValue.resourceId != 0) context.getColor(typedValue.resourceId) else typedValue.data
    }
}

private const val MIN_SIZE = 50
private const val MAX_SIZE = 200
private const val DEFAULT_SIZE = 100
private const val MIN_TEXT = 8f
private const val MAX_TEXT = 48f
private const val DEFAULT_TENKEY_TEXT = 28f
private const val DEFAULT_QWERTY_PREVIEW_TEXT = 28f
private const val DEFAULT_QWERTY_VARIATION_TEXT = 28f
private const val DEFAULT_DIRECTIONAL_TEXT = 28f
private const val DEFAULT_CROSS_TEXT = 18f
private const val DEFAULT_STANDARD_TEXT = 19f
private const val DEFAULT_TFBI_TEXT = 20f
