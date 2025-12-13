package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.qwerty_button_size

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentQwertyMarginSettingBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class QwertyMarginSettingFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentQwertyMarginSettingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQwertyMarginSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // キーボードの状態を初期化（表示用）
        binding.previewQwertyView.resetQWERTYKeyboard()

        setupSeekBars()
        setupResetButton()
        updatePreview()
        setupMenu()
    }

    private fun setupSeekBars() {
        // Vertical Margin
        setupSingleSeekBar(
            binding.seekbarVerticalMargin,
            binding.valueVerticalMargin,
            appPreference.qwerty_key_vertical_margin ?: 5.0f,
            "dp"
        ) { value ->
            appPreference.qwerty_key_vertical_margin = value
            updatePreview()
        }

        // Horizontal Gap
        setupSingleSeekBar(
            binding.seekbarHorizontalGap,
            binding.valueHorizontalGap,
            appPreference.qwerty_key_horizontal_gap ?: 2.0f,
            "dp"
        ) { value ->
            appPreference.qwerty_key_horizontal_gap = value
            updatePreview()
        }

        // Indent Large
        setupSingleSeekBar(
            binding.seekbarIndentLarge,
            binding.valueIndentLarge,
            appPreference.qwerty_key_indent_large ?: 23.0f,
            "dp"
        ) { value ->
            appPreference.qwerty_key_indent_large = value
            updatePreview()
        }

        // Indent Small
        setupSingleSeekBar(
            binding.seekbarIndentSmall,
            binding.valueIndentSmall,
            appPreference.qwerty_key_indent_small ?: 9.0f,
            "dp"
        ) { value ->
            appPreference.qwerty_key_indent_small = value
            updatePreview()
        }

        // Side Margin
        setupSingleSeekBar(
            binding.seekbarSideMargin,
            binding.valueSideMargin,
            appPreference.qwerty_key_side_margin ?: 4.0f,
            "dp"
        ) { value ->
            appPreference.qwerty_key_side_margin = value
            updatePreview()
        }

        // ★ 追加: Text Size
        setupSingleSeekBar(
            binding.seekbarTextSize,
            binding.valueTextSize,
            appPreference.qwerty_key_text_size ?: 18.0f,
            "sp"
        ) { value ->
            appPreference.qwerty_key_text_size = value
            updatePreview()
        }
    }

    private fun setupSingleSeekBar(
        seekBar: SeekBar,
        valueText: android.widget.TextView,
        initialValue: Float,
        unit: String, // 単位表示用に追加
        onChanged: (Float) -> Unit
    ) {
        // 初期値をProgressに変換 (小数点1桁まで扱うため10倍)
        seekBar.progress = (initialValue * 10).toInt()
        valueText.text = String.format(Locale.US, "%.1f%s", initialValue, unit)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                val floatValue = progress / 10.0f
                valueText.text = String.format(Locale.US, "%.1f%s", floatValue, unit)
                if (fromUser) {
                    onChanged(floatValue)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }

    private fun setupResetButton() {
        binding.buttonReset.setOnClickListener {
            // デフォルト値に戻す
            appPreference.qwerty_key_vertical_margin = 5.0f
            appPreference.qwerty_key_horizontal_gap = 2.0f
            appPreference.qwerty_key_indent_large = 23.0f
            appPreference.qwerty_key_indent_small = 9.0f
            appPreference.qwerty_key_side_margin = 4.0f
            appPreference.qwerty_key_text_size = 18.0f // ★ 追加

            // UI反映
            setupSeekBars()
            updatePreview()
        }
    }

    private fun updatePreview() {
        // appPreferenceから最新の値を取得
        val vMargin = appPreference.qwerty_key_vertical_margin ?: 5.0f
        val hGap = appPreference.qwerty_key_horizontal_gap ?: 2.0f
        val iLarge = appPreference.qwerty_key_indent_large ?: 23.0f
        val iSmall = appPreference.qwerty_key_indent_small ?: 9.0f
        val side = appPreference.qwerty_key_side_margin ?: 4.0f
        val textSize = appPreference.qwerty_key_text_size ?: 18.0f // ★ 追加

        // QWERTYKeyboardViewのマージンとテキストサイズを更新
        binding.previewQwertyView.setKeyMargins(
            verticalDp = vMargin,
            horizontalGapDp = hGap,
            indentLargeDp = iLarge,
            indentSmallDp = iSmall,
            sideMarginDp = side,
            textSizeSp = textSize // ★ 引数追加
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {

            }

            override fun onPrepareMenu(menu: Menu) {
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
