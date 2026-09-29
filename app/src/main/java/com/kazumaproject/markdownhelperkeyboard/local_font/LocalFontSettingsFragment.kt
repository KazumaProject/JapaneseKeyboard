package com.kazumaproject.markdownhelperkeyboard.local_font

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LocalFontSettingsFragment : Fragment() {
    private val viewModel: LocalFontSettingsViewModel by viewModels()
    private var currentView: TextView? = null
    private var previewNameView: TextView? = null
    private var sampleView: TextView? = null
    private var statusView: TextView? = null
    private var chooseButton: Button? = null
    private var applyButton: Button? = null
    private var cancelButton: Button? = null
    private var standardButton: Button? = null

    private val picker = registerForActivityResult(object : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: android.content.Context, input: Unit): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
    }) { uri -> if (uri != null) viewModel.select(uri) }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(resources.getDimensionPixelSize(R.dimen.local_font_screen_padding))
        }
        val scroll = ScrollView(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, resources.getDimensionPixelSize(R.dimen.local_font_section_spacing), 0, 0)
        }
        currentView = TextView(context).apply { textSize = 14f }
        content.addView(currentView, matchWrap())

        chooseButton = Button(context).apply {
            setText(R.string.local_font_choose)
            setOnClickListener { picker.launch(Unit) }
        }
        content.addView(chooseButton, matchWrap())
        content.addView(TextView(context).apply {
            setText(R.string.local_font_help)
            textSize = 14f
        }, matchWrap())
        statusView = TextView(context).apply { textSize = 14f }
        content.addView(statusView, matchWrap())
        previewNameView = TextView(context).apply { textSize = 14f }
        content.addView(previewNameView, matchWrap())
        sampleView = TextView(context).apply {
            text = getString(R.string.local_font_preview_sample)
            textSize = 18f
            includeFontPadding = true
            setPadding(resources.getDimensionPixelSize(R.dimen.local_font_sample_padding))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }
        content.addView(sampleView, matchWrap())
        content.addView(TextView(context).apply {
            setText(R.string.local_font_preview_small_label)
            textSize = 12f
        }, matchWrap())
        content.addView(TextView(context).apply {
            setText(R.string.local_font_preview_small_sample)
            textSize = 10f
            setPadding(resources.getDimensionPixelSize(R.dimen.local_font_sample_padding))
            tag = "local_font_small_preview"
        }, matchWrap())

        val buttons = LinearLayout(context).apply { gravity = Gravity.END }
        applyButton = Button(context).apply {
            setText(R.string.local_font_apply)
            setOnClickListener { viewModel.applyPreview() }
        }
        cancelButton = Button(context).apply {
            setText(R.string.local_font_cancel_selection)
            setOnClickListener { viewModel.cancelPreview() }
        }
        standardButton = Button(context).apply {
            setText(R.string.local_font_restore_standard)
            setOnClickListener { viewModel.restoreStandard() }
        }
        buttons.addView(cancelButton, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(applyButton, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(buttons, matchWrap())
        content.addView(standardButton, matchWrap())

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onDestroy() {
        if (!requireActivity().isChangingConfigurations) viewModel.discardPreviewOnExit()
        super.onDestroy()
    }

    private fun render(state: LocalFontSettingsUiState) {
        currentView?.text = getString(
            R.string.local_font_current,
            state.currentName ?: getString(R.string.local_font_standard),
        )
        previewNameView?.text = state.previewName?.let { getString(R.string.local_font_previewing, it) }.orEmpty()
        sampleView?.typeface = state.previewTypeface
        view?.findViewWithTag<TextView>("local_font_small_preview")?.typeface = state.previewTypeface
        statusView?.text = when {
            state.busy -> getString(R.string.local_font_loading)
            state.restoreWarning -> getString(R.string.local_font_restore_warning)
            state.error != null -> getString(state.error.toStringRes())
            else -> ""
        }
        chooseButton?.isEnabled = true
        applyButton?.isEnabled = !state.busy && state.previewName != null
        cancelButton?.setText(
            if (state.busy) R.string.local_font_cancel_loading else R.string.local_font_cancel_selection,
        )
        cancelButton?.isEnabled = state.busy || state.previewName != null
        standardButton?.isEnabled = true
    }

    private fun LocalFontUiError.toStringRes(): Int = when (this) {
        LocalFontUiError.UNSUPPORTED -> R.string.local_font_error_unsupported
        LocalFontUiError.TOO_LARGE -> R.string.local_font_error_too_large
        LocalFontUiError.INVALID -> R.string.local_font_error_invalid
        LocalFontUiError.STORAGE -> R.string.local_font_error_storage
        LocalFontUiError.RESTORE_FAILED -> R.string.local_font_restore_warning
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
}
