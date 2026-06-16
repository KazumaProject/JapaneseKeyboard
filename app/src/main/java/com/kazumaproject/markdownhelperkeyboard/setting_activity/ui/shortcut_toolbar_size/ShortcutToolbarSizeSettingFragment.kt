package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.shortcut_toolbar_size

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.annotation.AttrRes
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.R as MaterialR
import com.google.android.material.textfield.TextInputEditText
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentShortcutToolbarSizeSettingBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class ShortcutToolbarSizeSettingFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentShortcutToolbarSizeSettingBinding? = null
    private val binding get() = _binding!!

    private var isSyncingControls = false
    private var toolbarHeightDp = AppPreference.SHORTCUT_TOOLBAR_HEIGHT_DEFAULT_DP
    private var iconSizeDp = AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_DEFAULT_DP

    private val previewShortcutItems = listOf(
        ShortcutType.SETTINGS,
        ShortcutType.EMOJI,
        ShortcutType.TEMPLATE,
        ShortcutType.KEYBOARD_PICKER,
        ShortcutType.PASTE
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShortcutToolbarSizeSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        setupToolbar()
        setupControls()
        loadCurrentValues()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.show()
        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(AppCompatR.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupControls() {
        binding.toolbarHeightSeekBar.max =
            AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MAX_DP -
                AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MIN_DP
        binding.iconSizeSeekBar.max =
            AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_MAX_DP -
                AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP

        binding.toolbarHeightSeekBar.setOnSeekBarChangeListener(
            seekBarChangeListener { progress ->
                applyToolbarHeightDp(
                    AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MIN_DP + progress,
                    persist = true
                )
            }
        )
        binding.iconSizeSeekBar.setOnSeekBarChangeListener(
            seekBarChangeListener { progress ->
                applyIconSizeDp(
                    AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP + progress,
                    persist = true
                )
            }
        )

        binding.toolbarHeightEditText.doAfterTextChanged { editable ->
            if (isSyncingControls) return@doAfterTextChanged
            handleEditTextChange(
                rawValue = editable?.toString().orEmpty(),
                min = AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MIN_DP,
                max = AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MAX_DP,
                applyValue = { applyToolbarHeightDp(it, persist = true) }
            )
        }
        binding.iconSizeEditText.doAfterTextChanged { editable ->
            if (isSyncingControls) return@doAfterTextChanged
            handleEditTextChange(
                rawValue = editable?.toString().orEmpty(),
                min = AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP,
                max = AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_MAX_DP,
                applyValue = { applyIconSizeDp(it, persist = true) }
            )
        }

        binding.toolbarHeightEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                applyEditTextOrSync(binding.toolbarHeightEditText, ::applyToolbarHeightDp)
            }
        }
        binding.iconSizeEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                applyEditTextOrSync(binding.iconSizeEditText, ::applyIconSizeDp)
            }
        }

        binding.resetButton.setOnClickListener {
            applyToolbarHeightDp(AppPreference.SHORTCUT_TOOLBAR_HEIGHT_DEFAULT_DP, persist = true)
            applyIconSizeDp(AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_DEFAULT_DP, persist = true)
        }
    }

    private fun loadCurrentValues() {
        toolbarHeightDp = appPreference.shortcut_toolbar_height_dp_preference
        iconSizeDp = appPreference.shortcut_toolbar_icon_size_dp_preference
        syncControls()
        updatePreview()
    }

    private fun applyToolbarHeightDp(value: Int, persist: Boolean) {
        toolbarHeightDp = value.coerceIn(
            AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MIN_DP,
            AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MAX_DP
        )
        if (persist) {
            appPreference.shortcut_toolbar_height_dp_preference = toolbarHeightDp
        }
        syncControls()
        updatePreview()
    }

    private fun applyIconSizeDp(value: Int, persist: Boolean) {
        iconSizeDp = value.coerceIn(
            AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP,
            AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_MAX_DP
        )
        if (persist) {
            appPreference.shortcut_toolbar_icon_size_dp_preference = iconSizeDp
        }
        syncControls()
        updatePreview()
    }

    private fun syncControls() {
        isSyncingControls = true
        binding.toolbarHeightSeekBar.progress =
            toolbarHeightDp - AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MIN_DP
        binding.iconSizeSeekBar.progress =
            iconSizeDp - AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP
        syncText(binding.toolbarHeightEditText, toolbarHeightDp)
        syncText(binding.iconSizeEditText, iconSizeDp)
        isSyncingControls = false
    }

    private fun syncText(editText: TextInputEditText, value: Int) {
        val next = value.toString()
        if (editText.text?.toString() == next) return
        editText.setText(next)
        editText.setSelection(next.length)
    }

    private fun applyEditTextOrSync(
        editText: TextInputEditText,
        applyValue: (value: Int, persist: Boolean) -> Unit
    ) {
        val value = editText.text?.toString()?.toIntOrNull()
        if (value == null) {
            syncControls()
        } else {
            applyValue(value, true)
        }
    }

    private fun handleEditTextChange(
        rawValue: String,
        min: Int,
        max: Int,
        applyValue: (Int) -> Unit
    ) {
        if (rawValue.isBlank()) return
        val value = rawValue.toIntOrNull() ?: return
        val shouldApply = value in min..max ||
            value > max ||
            rawValue.length >= max.toString().length
        if (shouldApply) {
            applyValue(value)
        }
    }

    private fun seekBarChangeListener(onUserProgressChanged: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isSyncingControls) return
                onUserProgressChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

    private fun updatePreview() {
        val toolbarHeightPx = toolbarHeightDp.dpToPx()
        val resolvedIconSizeDp = appPreference.resolveShortcutToolbarIconSizeDp(
            toolbarHeightDp = toolbarHeightDp,
            iconSizeDp = iconSizeDp
        )
        val iconSizePx = resolvedIconSizeDp.dpToPx()
        val itemMinWidthPx = 64.dpToPx()
        val horizontalPaddingPx = 36.dpToPx()
        val itemWidthPx = maxOf(itemMinWidthPx, iconSizePx + horizontalPaddingPx)
        val iconTint = ColorStateList.valueOf(resolveThemeColor(MaterialR.attr.colorOnSurface))

        binding.shortcutToolbarPreviewContainer.layoutParams =
            binding.shortcutToolbarPreviewContainer.layoutParams.apply {
                height = toolbarHeightPx
            }
        binding.shortcutToolbarPreviewContainer.removeAllViews()
        previewShortcutItems.forEach { shortcut ->
            val itemView = FrameLayout(requireContext()).apply {
                isClickable = false
                isFocusable = false
            }
            val iconView = AppCompatImageView(requireContext()).apply {
                setImageResource(shortcut.iconResId)
                imageTintList = iconTint
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = shortcut.description
            }
            itemView.addView(
                iconView,
                FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
            )
            binding.shortcutToolbarPreviewContainer.addView(
                itemView,
                LinearLayout.LayoutParams(itemWidthPx, toolbarHeightPx)
            )
        }
    }

    private fun resolveThemeColor(@AttrRes attr: Int): Int {
        val typedArray = requireContext().obtainStyledAttributes(intArrayOf(attr))
        return try {
            typedArray.getColor(0, Color.TRANSPARENT)
        } finally {
            typedArray.recycle()
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).roundToInt()
}
