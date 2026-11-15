package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.DialogInterface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

internal class KeyboardTrialDialog : BottomSheetDialogFragment() {
    private var token: String? = null
    private var editor: TextInputEditText? = null
    private var note: TextView? = null
    private var choose: MaterialButton? = null
    private var focusListener: android.view.ViewTreeObserver.OnWindowFocusChangeListener? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        token = KeyboardTrialSession.begin(KeyboardPreviewTarget(KeyboardType.valueOf(args.getString("type")!!),
            args.getString("stable"), args.getLong("layout").takeIf { it > 0 }))
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val pad = (24 * resources.displayMetrics.density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { setText(R.string.keyboard_preview_try); textSize = 22f })
            addView(TextView(context).apply {
                note = this; setText(R.string.keyboard_preview_trial_note); setPadding(0, pad / 2, 0, pad / 2)
            })
            addView(MaterialButton(context).apply {
                choose = this; setText(R.string.keyboard_preview_choose_keyboard)
                setOnClickListener { context.getSystemService<InputMethodManager>()?.showInputMethodPicker() }
            })
            val input = object : TextInputEditText(context) {
                override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                    val connection = super.onCreateInputConnection(outAttrs)
                    outAttrs.extras = Bundle(outAttrs.extras ?: Bundle()).apply { putString(KeyboardTrialSession.EXTRA_TOKEN, token) }
                    return connection
                }
            }.apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                minLines = 2; maxLines = 4
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                id = R.id.keyboard_trial_input
            }
            editor = input
            addView(TextInputLayout(context).apply { hint = getString(R.string.keyboard_preview_trial_hint); addView(input) })
            addView(MaterialButton(context).apply {
                setText(android.R.string.ok)
                setOnClickListener { dismiss() }
            })
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            KeyboardTrialSession.unavailable.collect { unavailable ->
                if (unavailable) editor?.error = getString(R.string.keyboard_preview_unavailable)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        updateKeyboardOwnership()
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) updateKeyboardOwnership() }
        focusListener = listener
        dialog?.window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener(listener)
    }
    private fun updateKeyboardOwnership() {
        val selected = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val own = selected?.substringBefore('/') == requireContext().packageName
        choose?.visibility = if (own) View.GONE else View.VISIBLE
        note?.setText(if (own) R.string.keyboard_preview_trial_note else R.string.keyboard_preview_choose_ime)
        editor?.isEnabled = own
        if (own) editor?.let { input ->
            input.requestFocus()
            input.post { context?.getSystemService<InputMethodManager>()?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
        }
    }
    override fun onPause() {
        focusListener?.let { listener -> dialog?.window?.decorView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(listener) }
        focusListener = null
        super.onPause()
    }
    override fun onDismiss(dialog: DialogInterface) {
        editor?.let { context?.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(it.windowToken, 0) }
        token?.let(KeyboardTrialSession::end)
        super.onDismiss(dialog)
    }
    override fun onDestroy() {
        token?.let(KeyboardTrialSession::end)
        super.onDestroy()
    }
    companion object {
        fun show(manager: FragmentManager, target: KeyboardPreviewTarget) {
            if (manager.findFragmentByTag("keyboard-trial") != null) return
            KeyboardTrialDialog().apply { arguments = Bundle().apply {
                putString("type", target.type.name); putString("stable", target.customStableId); putLong("layout", target.customLayoutId ?: 0)
            } }.show(manager, "keyboard-trial")
        }
    }
}
