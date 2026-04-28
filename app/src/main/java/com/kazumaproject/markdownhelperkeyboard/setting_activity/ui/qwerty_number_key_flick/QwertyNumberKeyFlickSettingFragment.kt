package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.qwerty_number_key_flick

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.setMargins
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentQwertyNumberKeyFlickSettingBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

class QwertyNumberKeyFlickSettingFragment : Fragment() {

    private var _binding: FragmentQwertyNumberKeyFlickSettingBinding? = null
    private val binding get() = _binding!!

    private val upInputs = mutableMapOf<String, EditText>()
    private val downInputs = mutableMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "QWERTY 数字キーのフリック設定"
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQwertyNumberKeyFlickSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        buildRows()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : androidx.core.view.MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun buildRows() {
        val upChars = AppPreference.getQwertyNumberKeyFlickUpChars()
        val downChars = AppPreference.getQwertyNumberKeyFlickDownChars()
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { key ->
            binding.qwertyNumberKeyFlickContainer.addView(
                createRow(
                    key = key,
                    upValue = upChars[key].orEmpty(),
                    downValue = downChars[key].orEmpty()
                )
            )
        }
    }

    private fun createRow(key: String, upValue: String, downValue: String): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = requireContext().dpToPx(64f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val keyLabel = TextView(requireContext()).apply {
            text = key
            textSize = 24f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                requireContext().dpToPx(40f),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val upInput = createInput("上フリック", upValue)
        val downInput = createInput("下フリック", downValue)
        upInputs[key] = upInput
        downInputs[key] = downInput

        row.addView(keyLabel)
        row.addView(createFieldGroup("上フリック", upInput))
        row.addView(createFieldGroup("下フリック", downInput))

        upInput.doAfterTextChanged { saveCurrentValues() }
        downInput.doAfterTextChanged { saveCurrentValues() }

        return row
    }

    private fun createFieldGroup(label: String, editText: EditText): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(requireContext().dpToPx(6f))
            }

            addView(TextView(requireContext()).apply {
                text = label
                textSize = 12f
            })
            addView(editText)
        }
    }

    private fun createInput(hintText: String, value: String): EditText {
        return EditText(requireContext()).apply {
            hint = hintText
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(1))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_NEXT
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setText(value.take(1))
            setSelectAllOnFocus(true)
        }
    }

    private fun saveCurrentValues() {
        AppPreference.saveQwertyNumberKeyFlickUpChars(
            upInputs.mapValues { (_, input) -> input.text?.toString().orEmpty() }
        )
        AppPreference.saveQwertyNumberKeyFlickDownChars(
            downInputs.mapValues { (_, input) -> input.text?.toString().orEmpty() }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        upInputs.clear()
        downInputs.clear()
    }
}
