package com.kazumaproject.markdownhelperkeyboard.keyboard_setting

import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardSettingBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardSettingFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentKeyboardSettingBinding? = null
    private val binding get() = _binding!!
    private var isRightAligned = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeyboardSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isRightAligned = appPreference.keyboard_position ?: true

        binding.keyboardHeightSeekbar.apply {
            binding.keyboardHeightSeekbar.apply {
                max = 100
                progress = (appPreference.keyboard_height
                    ?: 280) - 180

                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        val actualProgress =
                            progress + 180
                        appPreference.keyboard_height = actualProgress

                        val density = resources.displayMetrics.density
                        val heightInPx = (actualProgress * density).toInt()

                        val layoutParams = binding.keyboardView.layoutParams
                        layoutParams.height = heightInPx
                        binding.keyboardView.layoutParams = layoutParams

                        val padding = when {
                            actualProgress in 210..280 -> {
                                val startPadding = 12 * density
                                val endPadding = 21 * density
                                val normalizedProgress =
                                    (actualProgress - 210) / 70f
                                startPadding + (endPadding - startPadding) * normalizedProgress
                            }

                            actualProgress < 210 -> 12 * density
                            else -> 21 * density
                        }
                        binding.keyboardView.setPaddingToSideKeySymbol(padding.toInt())
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
        }

        binding.keyboardWidthSeekbar.apply {
            max = 30
            progress =
                (appPreference.keyboard_width ?: 100) - 70

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val actualProgress =
                        progress + 70
                    appPreference.keyboard_width = actualProgress

                    val layoutParams = binding.keyboardView.layoutParams
                    val screenWidth = Resources.getSystem().displayMetrics.widthPixels

                    if (actualProgress == 100) {
                        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        val widthInPx = (screenWidth * (actualProgress / 100f)).toInt()
                        layoutParams.width = widthInPx
                    }

                    binding.keyboardView.layoutParams = layoutParams
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        binding.keyboardPositionButton.apply {
            isPressed = appPreference.keyboard_position ?: true
            text = if (isEnabled) {
                "右寄せ"
            } else {
                "左寄せ"
            }
            setOnClickListener {
                isRightAligned = !isRightAligned
                updateKeyboardAlignment()
            }
        }

        binding.keyboardView.apply {
            val heightFromPreference = appPreference.keyboard_height ?: 280
            val widthFromPreference = appPreference.keyboard_width ?: 100
            val density = resources.displayMetrics.density
            val heightInPx = (heightFromPreference * density).toInt()
            val screenWidth = Resources.getSystem().displayMetrics.widthPixels
            val layoutParams = binding.keyboardView.layoutParams

            layoutParams.apply {
                height = heightInPx
                width = if (widthFromPreference == 100) {
                    ViewGroup.LayoutParams.MATCH_PARENT
                } else {
                    (screenWidth * (widthFromPreference / 100f)).toInt()
                }
            }
            binding.keyboardView.layoutParams = layoutParams
        }

        updateKeyboardAlignment()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateKeyboardAlignment() {
        val constraintLayout = binding.keyboardSettingConstraint
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        if (isRightAligned) {
            constraintSet.connect(
                binding.keyboardView.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            constraintSet.clear(binding.keyboardView.id, ConstraintSet.START)

            binding.keyboardPositionButton.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.blue)
            )
            binding.keyboardPositionButton.text = "右寄せ"
            appPreference.keyboard_position = true
        } else {
            constraintSet.connect(
                binding.keyboardView.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            constraintSet.clear(binding.keyboardView.id, ConstraintSet.END)

            binding.keyboardPositionButton.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.qwety_key_bg_color
                )
            )
            binding.keyboardPositionButton.text = "左寄せ"
            appPreference.keyboard_position = false
        }

        constraintSet.applyTo(constraintLayout)
    }
}