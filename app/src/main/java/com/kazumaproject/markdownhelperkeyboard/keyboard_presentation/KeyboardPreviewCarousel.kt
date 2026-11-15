package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeCatalog
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection.getKeyboardDisplayName
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class KeyboardPreviewCarousel(
    context: Context,
    private val owner: LifecycleOwner,
    private val manager: FragmentManager,
    private val summary: TextView,
    private val onEdit: () -> Unit,
) : LinearLayout(context) {
    private var layouts = emptyList<CustomKeyboardLayout>()
    private var targets = emptyList<KeyboardPreviewTarget>()
    var selectedId: String? = null; private set
    private var job: Job? = null
    private var playbackObserver: Job? = null
    private var selectionObserver: Job? = null
    private var statusRes: Int = R.string.settings_keyboard_startup
    private val pager = ViewPager2(context).apply {
        id = R.id.keyboard_preview_pager
        isUserInputEnabled = false
        offscreenPageLimit = 1
        clipChildren = false
        (getChildAt(0) as RecyclerView).apply { clipToPadding = false; setPadding(0, 0, 0, 0) }
        setPageTransformer { page, position ->
            val column = page as? LinearLayout
            val alpha = (1f - kotlin.math.abs(position)).coerceIn(0f, 1f)
            column?.getChildAt(0)?.alpha = alpha
            column?.getChildAt(2)?.alpha = alpha
        }
    }
    private val indicator = MaterialTextView(context).apply { gravity = Gravity.CENTER; textSize = 12f }
    init {
        orientation = VERTICAL
        clipChildren = false
        addView(pager, LayoutParams(-1, dp(200)))
        indicator.isVisible = false
        addView(indicator, LayoutParams(-1, 0))
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = R.id.setting_home_preview_edit
                setText(R.string.settings_keyboard_settings); makeTextButton(this); setOnClickListener { onEdit() }
            }, LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
            addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                setText(R.string.keyboard_preview_try); makeTextButton(this)
                setOnClickListener { targets.getOrNull(pager.currentItem)?.let { KeyboardTrialDialog.show(manager, it) } }
            }, LayoutParams(0, dp(48), 1f))
        }, LayoutParams(-1, dp(48)))
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectedId = targets.getOrNull(position)?.id
                updatePlayback()
                updatePosition()
            }
        })
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        playbackObserver = owner.lifecycleScope.launch { KeyboardTrialSession.active.collect { updatePlayback() } }
        selectionObserver = owner.lifecycleScope.launch { KeyboardPreviewSelection.current.collect { reload() } }
    }
    override fun onDetachedFromWindow() {
        playbackObserver?.cancel()
        selectionObserver?.cancel()
        job?.cancel()
        super.onDetachedFromWindow()
    }
    private fun updatePlayback() {
        fun update(view: View) {
            if (view is KeyboardPreviewView) view.setPlaybackActive(view.target.id == selectedId && !KeyboardTrialSession.active.value)
            if (view is ViewGroup) for (i in 0 until view.childCount) update(view.getChildAt(i))
        }
        update(pager)
    }
    fun reload() {
        job?.cancel()
        job = owner.lifecycleScope.launch {
            try {
                val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, KeyboardPreviewDependencies::class.java)
                layouts = withContext(Dispatchers.IO) { dependencies.keyboards().getLayoutsNotFlow() }
                val prefs = AppPreference
                val live = KeyboardPreviewSelection.current.value
                val last = withContext(Dispatchers.IO) { KeyboardPreviewSelection.lastUsed(context) }
                statusRes = when {
                    live?.isAvailable(prefs.keyboard_order, layouts) == true -> R.string.settings_keyboard_selected
                    last?.isAvailable(prefs.keyboard_order, layouts) == true -> R.string.settings_keyboard_last_used
                    else -> R.string.settings_keyboard_startup
                }
                targets = listOf(resolveHomeKeyboardPreviewTarget(prefs.keyboard_order, layouts,
                    prefs.save_last_used_keyboard_enable_preference == true, prefs.save_last_used_keyboard_position_preference ?: 0,
                    prefs.remember_last_custom_keyboard_preference == true, prefs.last_used_custom_keyboard_stable_id, live, last))
                selectedId = targets.single().id
                pager.adapter = Pages()
                pager.setCurrentItem(0, false)
                updatePosition()
                setOnClickListener(null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.e("KeyboardPreview", "Cannot load registered keyboard layouts", e)
                targets = emptyList()
                pager.adapter = null
                updatePosition()
                setOnClickListener { reload() }
            }
        }
    }
    private fun updatePosition() {
        if (targets.isEmpty()) {
            summary.setText(R.string.keyboard_preview_unavailable)
            indicator.text = ""
            return
        }
        summary.setText(statusRes)
        indicator.isVisible = false
    }

    private inner class Pages : RecyclerView.Adapter<Page>() {
        override fun getItemCount() = targets.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Page = Page(LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(0, 0, dp(8), 0); layoutParams = ViewGroup.LayoutParams(-1, -1)
        })
        override fun onBindViewHolder(holder: Page, position: Int) {
            val target = targets[position]
            val name = target.name(context, layouts)
            holder.column.removeAllViews()
            val preview = KeyboardPreviewView(context, target).apply { includeCandidates = false; respectOrientationOverride = target.inputMode == null; setPlaybackActive(position == pager.currentItem && !KeyboardTrialSession.active.value) }
            val text = MaterialTextView(context).apply { textSize = 13f; maxLines = 2; text = description(target.type) }
            holder.column.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(MaterialTextView(context).apply { this.text = name; typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL; textSize = 18f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LayoutParams(0, -1, 1f))
                addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    this.text = "ⓘ"; makeTextButton(this); contentDescription = context.getString(R.string.keyboard_preview_info, name)
                    minWidth = dp(48); minimumWidth = dp(48); setPadding(0, 0, 0, 0)
                    setOnClickListener { showDetails(target, name, preview) }
                }, LayoutParams(dp(48), dp(40)))
            }, LayoutParams(-1, dp(40)))
            holder.column.addView(preview, LayoutParams(-1, dp(136)))
            holder.column.addView(text, LayoutParams(-1, dp(24)))
            preview.onReady = {
                text.text = description(target, preview)
            }
            preview.contentDescription = context.getString(R.string.keyboard_preview_expand)
            preview.setOnClickListener { showExpanded(target, name, preview) }
        }
    }
    private class Page(val column: LinearLayout) : RecyclerView.ViewHolder(column)
    private fun description(type: KeyboardType): String = context.getString(when (type) {
        KeyboardType.TENKEY -> R.string.keyboard_preview_tenkey
        KeyboardType.GOJUON -> R.string.keyboard_preview_gojuon
        KeyboardType.SUMIRE -> R.string.keyboard_preview_sumire
        KeyboardType.QWERTY -> R.string.keyboard_preview_qwerty
        KeyboardType.ROMAJI -> R.string.keyboard_preview_romaji
        KeyboardType.CUSTOM -> R.string.keyboard_preview_custom
        KeyboardType.SPLIT -> R.string.keyboard_preview_split
        KeyboardType.MORSE -> R.string.keyboard_preview_morse
    })
    private fun description(target: KeyboardPreviewTarget, preview: KeyboardPreviewView): String {
        if (preview.visualType != target.type) return context.getString(R.string.keyboard_preview_showing_layout,
            context.getKeyboardDisplayName(preview.visualType))
        if (target.type !in listOf(KeyboardType.QWERTY, KeyboardType.ROMAJI, KeyboardType.SPLIT, KeyboardType.MORSE)) {
            if (preview.previewInputMode == InputMode.ModeEnglish) return context.getString(R.string.keyboard_preview_english_mode)
            if (preview.previewInputMode == InputMode.ModeNumber) return context.getString(R.string.keyboard_preview_number_mode)
        }
        if (target.type != KeyboardType.SUMIRE && target.type != KeyboardType.CUSTOM) return description(target.type)
        val types = preview.resolvedLayout?.keys?.filter { !it.isSpecialKey }?.map { it.keyType }.orEmpty()
        return context.getString(when {
            types.any { it == KeyType.FREE_FLICK } -> R.string.custom_free_flick
            types.any { it == KeyType.TWO_STEP_FLICK || it == KeyType.STICKY_TWO_STEP_FLICK } -> R.string.keyboard_preview_two_step_short
            types.any { it == KeyType.CIRCULAR_FLICK } -> R.string.keyboard_preview_circle_short
            types.any { it != KeyType.NORMAL && it != KeyType.QWERTY } -> R.string.keyboard_preview_flick_short
            else -> if (target.type == KeyboardType.CUSTOM) R.string.keyboard_preview_custom else R.string.keyboard_preview_sumire
        })
    }
    private fun showDetails(target: KeyboardPreviewTarget, name: String, source: KeyboardPreviewView) {
        source.setPlaybackActive(false)
        val dialog = BottomSheetDialog(context)
        dialog.setOnDismissListener { if (source.isAttachedToWindow) source.setPlaybackActive(true) }
        val content = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(24))
            addView(MaterialTextView(context).apply { text = name; textSize = 22f })
            addView(MaterialTextView(context).apply {
                text = context.getString(KeyboardThemeCatalog.find(AppPreference.keyboardSkin).titleRes) + " · " + context.getString(R.string.keyboard_preview_idle)
                setPadding(0, dp(8), 0, dp(12))
            })
            addView(KeyboardPreviewView(context, target), LayoutParams(-1, dp(240)))
            addView(MaterialTextView(context).apply {
                text = description(target, source) +
                    (if (source.resolvedLayout?.keys?.any { (it.keyType == KeyType.TWO_STEP_FLICK || it.keyType == KeyType.STICKY_TWO_STEP_FLICK) } == true)
                        "\n\n" + context.getString(R.string.keyboard_preview_two_step) else "") +
                    (if (target.type == KeyboardType.QWERTY || target.type == KeyboardType.ROMAJI) {
                        fun state(on: Boolean) = context.getString(if (on) R.string.keyboard_preview_enabled else R.string.keyboard_preview_disabled)
                        "\n\n" + context.getString(R.string.keyboard_preview_qwerty_help,
                            state(AppPreference.qwerty_show_number_buttons == true), state(AppPreference.qwerty_show_cursor_buttons == true),
                            state(AppPreference.qwerty_glide_input_preference))
                    } else "") +
                    (if (target.type == KeyboardType.ROMAJI) "\n\n" + context.getString(R.string.keyboard_preview_romaji_help) else "") +
                    "\n\n" + context.getString(R.string.keyboard_preview_details_note)
                setPadding(0, dp(16), 0, dp(16))
            })
            addView(MaterialButton(context).apply {
                setText(R.string.keyboard_preview_try)
                setOnClickListener { dialog.dismiss(); KeyboardTrialDialog.show(manager, target) }
            })
        }
        dialog.setContentView(androidx.core.widget.NestedScrollView(context).apply { addView(content) })
        dialog.show()
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }
    private fun showExpanded(target: KeyboardPreviewTarget, name: String, source: KeyboardPreviewView) {
        source.setPlaybackActive(false)
        val dialog = BottomSheetDialog(context)
        dialog.setOnDismissListener { if (source.isAttachedToWindow) source.setPlaybackActive(true) }
        val height = (resources.displayMetrics.heightPixels * .9f).toInt()
        val content = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(16))
            addView(MaterialTextView(context).apply { text = name; textSize = 22f })
            addView(KeyboardPreviewView(context, target).apply { id = R.id.keyboard_preview_expanded }, LayoutParams(-1, 0, 1f))
            addView(MaterialButton(context).apply {
                id = R.id.keyboard_preview_expanded_close
                setText(R.string.keyboard_preview_close); setOnClickListener { dialog.dismiss() }
            }, LayoutParams(-1, dp(48)))
        }
        dialog.setContentView(content)
        content.layoutParams = content.layoutParams.apply { this.height = height }
        dialog.show()
        dialog.behavior.peekHeight = height
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    private fun makeTextButton(button: MaterialButton) {
        button.strokeWidth = 0
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        button.setTextColor(com.google.android.material.color.MaterialColors.getColor(button, androidx.appcompat.R.attr.colorPrimary))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
