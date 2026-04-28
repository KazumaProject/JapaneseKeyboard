package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.circular_slot

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.circular_slot.CircularSlotActionSetting
import com.kazumaproject.markdownhelperkeyboard.setting_activity.circular_slot.CircularSlotActionType

class CircularSlotActionSettingFragment : Fragment() {

    private data class ModeItem(val mode: KeyboardInputMode, val label: String)
    private data class KeyItem(val keyData: KeyData) {
        val identifier: String = keyData.keyId ?: keyData.label
        val label: String = if (keyData.keyId.isNullOrBlank()) {
            keyData.label
        } else {
            "${keyData.label} (${keyData.keyId})"
        }
    }

    private val modes = listOf(
        ModeItem(KeyboardInputMode.HIRAGANA, "日本語"),
        ModeItem(KeyboardInputMode.ENGLISH, "英語"),
        ModeItem(KeyboardInputMode.SYMBOLS, "数字・記号")
    )
    private val slots = listOf(
        CircularFlickDirection.SLOT_4,
        CircularFlickDirection.SLOT_5,
        CircularFlickDirection.SLOT_6
    )
    private val actionTypes = listOf(
        CircularSlotActionType.NONE to "なし",
        CircularSlotActionType.INPUT_TEXT to "文字を入力",
        CircularSlotActionType.SWITCH_MAP to "map 切り替え",
        CircularSlotActionType.SHOW_EMOJI_KEYBOARD to "絵文字キーボードを表示",
        CircularSlotActionType.SWITCH_TO_NEXT_IME to "次のIMEへ切り替え",
        CircularSlotActionType.SWITCH_TO_KANA_LAYOUT to "かな配列へ切り替え",
        CircularSlotActionType.SWITCH_TO_ENGLISH_LAYOUT to "英語配列へ切り替え",
        CircularSlotActionType.SWITCH_TO_NUMBER_LAYOUT to "数字配列へ切り替え"
    )

    private lateinit var modeSpinner: Spinner
    private lateinit var keySpinner: Spinner
    private lateinit var slotSpinner: Spinner
    private lateinit var actionSpinner: Spinner
    private lateinit var textInput: EditText
    private lateinit var saveButton: Button
    private var keyItems: List<KeyItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Circular Flick SLOT4〜6 設定"
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        AppPreference.init(requireContext())
        return androidx.core.widget.NestedScrollView(requireContext()).apply {
            isFillViewport = true
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    requireContext().dpToPx(16f),
                    requireContext().dpToPx(16f),
                    requireContext().dpToPx(16f),
                    requireContext().dpToPx(24f)
                )

                modeSpinner = addLabeledSpinner("モード")
                keySpinner = addLabeledSpinner("キー")
                slotSpinner = addLabeledSpinner("SLOT")
                actionSpinner = addLabeledSpinner("アクション")
                textInput = addTextInput("入力する文字")
                saveButton = Button(requireContext()).apply {
                    text = "保存"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(requireContext().dpToPx(4f))
                    }
                }
                addView(saveButton)
            })
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupStaticAdapters()
        setupListeners()
        rebuildKeysForSelectedMode()
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

    private fun LinearLayout.addLabeledSpinner(label: String): Spinner {
        addView(TextView(requireContext()).apply {
            text = label
            textSize = 14f
        })
        return Spinner(requireContext()).also { spinner ->
            spinner.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                requireContext().dpToPx(48f)
            ).apply {
                setMargins(requireContext().dpToPx(4f))
            }
            addView(spinner)
        }
    }

    private fun LinearLayout.addTextInput(label: String): EditText {
        addView(TextView(requireContext()).apply {
            text = label
            textSize = 14f
        })
        return EditText(requireContext()).also { editText ->
            editText.setSingleLine(true)
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            editText.imeOptions = EditorInfo.IME_ACTION_DONE
            editText.gravity = Gravity.CENTER_VERTICAL
            editText.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(requireContext().dpToPx(4f))
            }
            addView(editText)
        }
    }

    private fun setupStaticAdapters() {
        modeSpinner.adapter = simpleAdapter(modes.map { it.label })
        slotSpinner.adapter = simpleAdapter(slots.map { it.name })
        actionSpinner.adapter = simpleAdapter(actionTypes.map { it.second })
    }

    private fun setupListeners() {
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                rebuildKeysForSelectedMode()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        keySpinner.onItemSelectedListener = reloadSelectionListener()
        slotSpinner.onItemSelectedListener = reloadSelectionListener()
        actionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateTextInputVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        saveButton.setOnClickListener { saveCurrentSetting() }
    }

    private fun reloadSelectionListener(): AdapterView.OnItemSelectedListener {
        return object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadCurrentSetting()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun rebuildKeysForSelectedMode() {
        val layout = KeyboardDefaultLayouts.createFinalLayout(
            mode = selectedMode(),
            dynamicKeyStates = emptyMap(),
            inputLayoutType = AppPreference.sumire_input_method,
            inputStyle = AppPreference.sumire_keyboard_style,
            isDeleteFlickEnabled = AppPreference.delete_key_left_flick_preference
        )
        keyItems = layout.keys
            .filter { key ->
                !key.isSpecialKey &&
                    key.label.isNotBlank() &&
                    key.keyType == KeyType.CIRCULAR_FLICK
            }
            .map(::KeyItem)
            .distinctBy { it.identifier }

        keySpinner.adapter = simpleAdapter(keyItems.map { it.label })
        keySpinner.isEnabled = keyItems.isNotEmpty()
        loadCurrentSetting()
    }

    private fun loadCurrentSetting() {
        val keyItem = selectedKeyItem() ?: return
        val setting = AppPreference.getCircularSlotActionSetting(
            mode = selectedMode(),
            keyIdentifier = keyItem.identifier,
            slot = selectedSlot()
        )
        val actionType = setting?.actionType ?: CircularSlotActionType.NONE
        actionSpinner.setSelection(actionTypes.indexOfFirst { it.first == actionType }.coerceAtLeast(0))
        textInput.setText(setting?.value.orEmpty())
        updateTextInputVisibility()
    }

    private fun saveCurrentSetting() {
        val keyItem = selectedKeyItem() ?: return
        val actionType = selectedActionType()
        val value = textInput.text?.toString().orEmpty()
        val normalizedActionType = if (
            actionType == CircularSlotActionType.INPUT_TEXT && value.isBlank()
        ) {
            CircularSlotActionType.NONE
        } else {
            actionType
        }

        AppPreference.upsertCircularSlotActionSetting(
            CircularSlotActionSetting(
                mode = selectedMode(),
                keyIdentifier = keyItem.identifier,
                slot = selectedSlot(),
                actionType = normalizedActionType,
                value = value.takeIf { normalizedActionType == CircularSlotActionType.INPUT_TEXT }
            )
        )
        Toast.makeText(requireContext(), "保存しました", Toast.LENGTH_SHORT).show()
        loadCurrentSetting()
    }

    private fun updateTextInputVisibility() {
        textInput.visibility = if (selectedActionType() == CircularSlotActionType.INPUT_TEXT) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun selectedMode(): KeyboardInputMode {
        return modes.getOrNull(modeSpinner.selectedItemPosition)?.mode ?: KeyboardInputMode.HIRAGANA
    }

    private fun selectedKeyItem(): KeyItem? {
        return keyItems.getOrNull(keySpinner.selectedItemPosition)
    }

    private fun selectedSlot(): CircularFlickDirection {
        return slots.getOrNull(slotSpinner.selectedItemPosition) ?: CircularFlickDirection.SLOT_4
    }

    private fun selectedActionType(): CircularSlotActionType {
        return actionTypes.getOrNull(actionSpinner.selectedItemPosition)?.first
            ?: CircularSlotActionType.NONE
    }

    private fun simpleAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            items
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }
}
