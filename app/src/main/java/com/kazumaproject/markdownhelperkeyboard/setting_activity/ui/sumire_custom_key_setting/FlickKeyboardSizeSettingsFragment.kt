package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.sumire_custom_key_setting

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

class FlickKeyboardSizeSettingsFragment : Fragment() {

    private var previewKeyboardView: FlickKeyboardView? = null
    private var widthSeekBar: SeekBar? = null
    private var heightSeekBar: SeekBar? = null
    private var iconSeekBar: SeekBar? = null
    private var textSeekBar: SeekBar? = null

    private var widthValueText: TextView? = null
    private var heightValueText: TextView? = null
    private var iconValueText: TextView? = null
    private var textValueText: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_flick_keyboard_size_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AppPreference.init(requireContext())

        previewKeyboardView = view.findViewById(R.id.previewFlickKeyboardView)
        widthSeekBar = view.findViewById(R.id.seekBarKeyWidth)
        heightSeekBar = view.findViewById(R.id.seekBarKeyHeight)
        iconSeekBar = view.findViewById(R.id.seekBarIconSize)
        textSeekBar = view.findViewById(R.id.seekBarTextSize)

        widthValueText = view.findViewById(R.id.textKeyWidthValue)
        heightValueText = view.findViewById(R.id.textKeyHeightValue)
        iconValueText = view.findViewById(R.id.textIconSizeValue)
        textValueText = view.findViewById(R.id.textTextSizeValue)

        val resetButton: Button = view.findViewById(R.id.buttonResetFlickKeyboardSize)

        bindSeekBars()
        bindResetButton(resetButton)
        renderPreview()
        setupMenu()
    }

    override fun onDestroyView() {
        previewKeyboardView = null
        widthSeekBar = null
        heightSeekBar = null
        iconSeekBar = null
        textSeekBar = null
        widthValueText = null
        heightValueText = null
        iconValueText = null
        textValueText = null
        super.onDestroyView()
    }

    private fun bindSeekBars() {
        widthSeekBar?.max = MAX_PERCENT - MIN_PERCENT
        heightSeekBar?.max = MAX_PERCENT - MIN_PERCENT
        iconSeekBar?.max = MAX_ICON_PERCENT - MIN_PERCENT
        textSeekBar?.max = (MAX_TEXT_SIZE_SP - MIN_TEXT_SIZE_SP).toInt()

        widthSeekBar?.progress =
            (AppPreference.flick_key_width_scale_percent ?: DEFAULT_WIDTH_PERCENT) - MIN_PERCENT
        heightSeekBar?.progress =
            (AppPreference.flick_key_height_scale_percent ?: DEFAULT_HEIGHT_PERCENT) - MIN_PERCENT
        iconSeekBar?.progress =
            (AppPreference.flick_key_icon_scale_percent ?: DEFAULT_ICON_PERCENT) - MIN_PERCENT
        textSeekBar?.progress =
            ((AppPreference.flick_key_text_size_sp
                ?: DEFAULT_TEXT_SIZE_SP) - MIN_TEXT_SIZE_SP).toInt()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                saveCurrentValues()
                updateValueLabels()
                renderPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

        widthSeekBar?.setOnSeekBarChangeListener(listener)
        heightSeekBar?.setOnSeekBarChangeListener(listener)
        iconSeekBar?.setOnSeekBarChangeListener(listener)
        textSeekBar?.setOnSeekBarChangeListener(listener)

        updateValueLabels()
    }

    private fun bindResetButton(button: Button) {
        button.setOnClickListener {
            widthSeekBar?.progress = DEFAULT_WIDTH_PERCENT - MIN_PERCENT
            heightSeekBar?.progress = DEFAULT_HEIGHT_PERCENT - MIN_PERCENT
            iconSeekBar?.progress = DEFAULT_ICON_PERCENT - MIN_PERCENT
            textSeekBar?.progress = (DEFAULT_TEXT_SIZE_SP - MIN_TEXT_SIZE_SP).toInt()
            saveCurrentValues()
            updateValueLabels()
            renderPreview()
        }
    }

    private fun saveCurrentValues() {
        AppPreference.flick_key_width_scale_percent = currentWidthPercent
        AppPreference.flick_key_height_scale_percent = currentHeightPercent
        AppPreference.flick_key_icon_scale_percent = currentIconPercent
        AppPreference.flick_key_text_size_sp = currentTextSizeSp
    }

    private fun updateValueLabels() {
        widthValueText?.text = "${currentWidthPercent}%"
        heightValueText?.text = "${currentHeightPercent}%"
        iconValueText?.text = "${currentIconPercent}%"
        textValueText?.text = String.format("%.1fsp", currentTextSizeSp)
    }

    private fun renderPreview() {
        val keyboardView = previewKeyboardView ?: return
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        keyboardView.applyKeyboardTheme(
            themeMode = AppPreference.theme_mode,
            currentNightMode = currentNightMode,
            isDynamicColorEnabled = false,
            customBgColor = AppPreference.custom_theme_bg_color,
            customKeyColor = AppPreference.custom_theme_key_color,
            customSpecialKeyColor = AppPreference.custom_theme_special_key_color,
            customKeyTextColor = AppPreference.custom_theme_key_text_color,
            customSpecialKeyTextColor = AppPreference.custom_theme_special_key_text_color,
            liquidGlassEnable = AppPreference.liquid_glass_preference,
            customBorderEnable = AppPreference.custom_theme_border_enable,
            customBorderColor = AppPreference.custom_theme_border_color,
            liquidGlassKeyAlphaEnable = AppPreference.liquid_glass_key_alpha,
            borderWidth = AppPreference.custom_theme_border_width
        )

        keyboardView.applyKeySizing(
            keyWidthScalePercent = currentWidthPercent,
            keyHeightScalePercent = currentHeightPercent,
            iconScalePercent = currentIconPercent,
            textSizeSp = currentTextSizeSp
        )

        keyboardView.setOnKeyboardActionListener(object :
            FlickKeyboardView.OnKeyboardActionListener {
            override fun onKey(text: String, isFlick: Boolean) = Unit
            override fun onAction(
                action: com.kazumaproject.custom_keyboard.data.KeyAction,
                view: View,
                isFlick: Boolean
            ) = Unit

            override fun onActionLongPress(action: com.kazumaproject.custom_keyboard.data.KeyAction) =
                Unit

            override fun onActionUpAfterLongPress(action: com.kazumaproject.custom_keyboard.data.KeyAction) =
                Unit

            override fun onFlickDirectionChanged(direction: com.kazumaproject.custom_keyboard.data.FlickDirection) =
                Unit

            override fun onFlickActionLongPress(action: com.kazumaproject.custom_keyboard.data.KeyAction) =
                Unit

            override fun onFlickActionUpAfterLongPress(
                action: com.kazumaproject.custom_keyboard.data.KeyAction,
                isFlick: Boolean
            ) = Unit
        })

        keyboardView.setKeyboard(KeyboardDefaultLayouts.defaultLayout())
    }

    private val currentWidthPercent: Int
        get() = MIN_PERCENT + (widthSeekBar?.progress ?: 0)

    private val currentHeightPercent: Int
        get() = MIN_PERCENT + (heightSeekBar?.progress ?: 0)

    private val currentIconPercent: Int
        get() = MIN_PERCENT + (iconSeekBar?.progress ?: 0)

    private val currentTextSizeSp: Float
        get() = MIN_TEXT_SIZE_SP + (textSeekBar?.progress ?: 0).toFloat()

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
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

    companion object {
        private const val MIN_PERCENT = 40
        private const val MAX_PERCENT = 220
        private const val MAX_ICON_PERCENT = 220

        private const val DEFAULT_WIDTH_PERCENT = 160
        private const val DEFAULT_HEIGHT_PERCENT = 160
        private const val DEFAULT_ICON_PERCENT = 80

        private const val MIN_TEXT_SIZE_SP = 8f
        private const val MAX_TEXT_SIZE_SP = 32f
        private const val DEFAULT_TEXT_SIZE_SP = 16f
    }
}
