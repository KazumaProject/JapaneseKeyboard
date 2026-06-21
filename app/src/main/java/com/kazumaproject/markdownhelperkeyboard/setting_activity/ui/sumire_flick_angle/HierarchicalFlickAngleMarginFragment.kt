package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.sumire_flick_angle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import androidx.fragment.app.Fragment
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentHierarchicalFlickAngleMarginBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class HierarchicalFlickAngleMarginFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentHierarchicalFlickAngleMarginBinding? = null
    private val binding get() = _binding!!
    private var isUpdatingUi = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHierarchicalFlickAngleMarginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sliderMargin.value =
            appPreference.hierarchical_flick_mode_switch_angle_margin_preference.toFloat()
        updateMargin(binding.sliderMargin.value.roundToInt(), persist = false)
        setupActualKeyPreview()

        binding.sliderMargin.addOnChangeListener { _, value, fromUser ->
            updateMargin(value.roundToInt(), persist = fromUser)
        }

        binding.btnReset.setOnClickListener {
            binding.sliderMargin.value = DEFAULT_MARGIN.toFloat()
            updateMargin(DEFAULT_MARGIN, persist = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateMargin(value: Int, persist: Boolean) {
        val margin = value.coerceIn(0, 34)
        isUpdatingUi = true
        binding.tvMarginValue.text = getString(
            com.kazumaproject.markdownhelperkeyboard.R.string.hierarchical_flick_angle_margin_value,
            margin
        )
        if (binding.sliderMargin.value.roundToInt() != margin) {
            binding.sliderMargin.value = margin.toFloat()
        }
        isUpdatingUi = false
        binding.actualKeyPreview.setHierarchicalFlickModeSwitchAngleMargin(margin.toDouble())
        if (persist) {
            appPreference.hierarchical_flick_mode_switch_angle_margin_preference = margin
        }
    }

    private fun setupActualKeyPreview() {
        binding.actualKeyPreview.apply {
            setPopupWindowAnchorProvider(null)
            setVisibleKeyLabels(PREVIEW_VISIBLE_KEY_LABELS)
            setFlickSensitivityValue(appPreference.flick_sensitivity_preference ?: 100)
            setLongPressTimeout((appPreference.long_press_timeout_preference ?: 300).toLong())
            setHierarchicalFlickModeSwitchAngleMargin(
                appPreference.hierarchical_flick_mode_switch_angle_margin_preference.toDouble()
            )
            applyKeySizing(
                keyWidthScalePercent = appPreference.flick_key_width_scale_percent ?: 160,
                keyHeightScalePercent = appPreference.flick_key_height_scale_percent ?: 160,
                iconScalePercent = appPreference.flick_key_icon_scale_percent ?: 80,
                textSizeSp = appPreference.flick_key_text_size_sp ?: 16.0f,
                specialKeyTextSizeSp = appPreference.flick_special_key_text_size_sp ?: 16.0f
            )
            setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
                override fun onPress(action: KeyAction) = Unit
                override fun onAction(action: KeyAction, isFlick: Boolean) {
                    binding.tvActualKeyResult.text = getString(
                        com.kazumaproject.markdownhelperkeyboard.R.string.hierarchical_flick_angle_margin_result,
                        action.previewText()
                    )
                }

                override fun onActionLongPress(action: KeyAction) = Unit
                override fun onActionUpAfterLongPress(action: KeyAction) = Unit
                override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
                override fun onFlickActionLongPress(action: KeyAction) = Unit
                override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
                override fun onLongPressActionCanceled(action: KeyAction) = Unit
            })
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.requestAncestorsDisallowIntercept(true)
                    }

                    MotionEvent.ACTION_MOVE ->
                        view.requestAncestorsDisallowIntercept(true)

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.requestAncestorsDisallowIntercept(false)
                    }
                }
                false
            }
            setKeyboard(createHierarchicalPreviewLayout())
        }
    }

    private fun createHierarchicalPreviewLayout() =
        KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = emptyMap(),
            inputLayoutType = "flick",
            inputStyle = "third-flick"
        )

    private fun View.requestAncestorsDisallowIntercept(disallow: Boolean) {
        var current: ViewParent? = parent
        while (current != null) {
            current.requestDisallowInterceptTouchEvent(disallow)
            current = current.parent
        }
    }

    private fun KeyAction.previewText(): String =
        when (this) {
            is KeyAction.Text -> text
            is KeyAction.InputText -> text
            else -> this::class.simpleName.orEmpty()
        }

    private companion object {
        const val DEFAULT_MARGIN = 20
        val PREVIEW_VISIBLE_KEY_LABELS = setOf("か")
    }
}
