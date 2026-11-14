package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.fever.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.AsyncPreferenceFragment

/** Both settings experiences navigate here and use the same preference storage. */
class FeverSettingsFragment : AsyncPreferenceFragment() {
    private var preview: AlertDialog? = null
    override fun createPreferenceHierarchy(context: Context): PreferenceScreen {
        val screen = preferenceManager.createPreferenceScreen(context)
        fun toggle(key: String, title: Int, default: Boolean, summary: Int? = null) {
            screen.addPreference(SwitchPreferenceCompat(context).apply {
                this.key = key; setTitle(title); setDefaultValue(default)
                summary?.let(::setSummary)
            })
        }
        fun slider(key: String, title: Int, min: Int, max: Int, default: Int, summary: Int? = null) {
            screen.addPreference(SeekBarPreference(context).apply {
                this.key = key; setTitle(title); this.min = min; this.max = max
                setDefaultValue(default); showSeekBarValue = true; updatesContinuously = false
                summary?.let(::setSummary)
            })
        }
        screen.addPreference(Preference(context).apply {
            setTitle(R.string.fever_preview); setSummary(R.string.fever_preview_summary)
            configureClickListener { showPreview(); true }
        })
        toggle(FeverSettings.FULL_SCREEN, R.string.fever_full_screen, false, R.string.fever_full_screen_summary)
        slider(FeverSettings.INTENSITY, R.string.fever_intensity, 0, 2, 2, R.string.fever_intensity_summary)
        toggle(FeverSettings.SOUND, R.string.fever_sound, true, R.string.fever_sound_summary)
        slider(FeverSettings.VOLUME, R.string.fever_volume, 0, 100, 50)
        toggle(FeverSettings.VIBRATION, R.string.fever_vibration, true, R.string.fever_feedback_summary)
        slider(FeverSettings.HIT_RATE, R.string.fever_hit_rate, 0, 100, 5, R.string.fever_hit_rate_summary)
        slider(FeverSettings.COMBO_SECONDS, R.string.fever_combo_seconds, 1, 10, 3)
        toggle(FeverSettings.KEYWORDS_ENABLED, R.string.fever_keywords_enabled, true)
        screen.addPreference(Preference(context).apply {
            key = FeverSettings.KEYWORDS; setTitle(R.string.fever_keywords)
            configureClickListener { editKeywords(); true }
        })
        return screen
    }
    override fun onPreferencesResumed() {
        val settings = FeverSettings.read(preferenceManager.sharedPreferences!!)
        findPreference<Preference>(FeverSettings.KEYWORDS)?.summary = settings.keywords.joinToString("、").ifEmpty { getString(R.string.fever_keywords_empty) }
    }
    private fun editKeywords() {
        val prefs = preferenceManager.sharedPreferences!!
        val edit = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 5; maxLines = 12; gravity = Gravity.TOP
            setText(FeverSettings.read(prefs).keywords.joinToString("\n"))
            setPadding(32, 20, 32, 20)
        }
        AlertDialog.Builder(requireContext()).setTitle(R.string.fever_keywords)
            .setMessage(R.string.fever_keywords_help).setView(edit)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.fever_restore_words) { _, _ ->
                FeverSettings.saveKeywords(prefs, FeverSettings.DEFAULT_KEYWORDS); onPreferencesResumed()
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                FeverSettings.saveKeywords(prefs, edit.text.toString().lineSequence().map(String::trim).filter(String::isNotEmpty).toList())
                onPreferencesResumed()
            }.show()
    }
    private fun showPreview() {
        val context = requireContext()
        val settings = FeverSettings.read(preferenceManager.sharedPreferences!!)
        val frame = FrameLayout(context)
        val height = (320 * resources.displayMetrics.density).toInt()
        val miniature = KeyboardThemePreviewView(context, KeyboardSkinId.FEVER)
        frame.addView(miniature, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        val effect = FeverEffectView(context).apply { intensity = settings.intensity }
        frame.addView(effect, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        val feedback = FeverFeedback(context)
        var previewStep = 0
        fun play() {
            effect.setArena(RectF(0f, 0f, frame.width.toFloat(), frame.height.toFloat()))
            effect.protectKeys(RectF(0f, miniature.top.toFloat(), frame.width.toFloat(), frame.height.toFloat()))
            val sx = miniature.width / 320f
            val sy = miniature.height / 184f
            effect.setKeyEdges((0 until 20).map { index ->
                val left = (6 + index % 5 * 63) * sx
                val top = miniature.top + (34 + index / 5 * 37) * sy
                RectF(left, top, left + 57 * sx, top + 32 * sy)
            }, RectF(0f, miniature.top.toFloat(), miniature.width.toFloat(), miniature.top + 34 * sy))
            previewStep++
            val event = when (previewStep) {
                10 -> FeverEvent(10, FeverAward.COMBO)
                11 -> FeverEvent(25, FeverAward.FEVER)
                12 -> FeverEvent(50, FeverAward.HOT)
                13 -> FeverEvent(100, FeverAward.JACKPOT)
                else -> FeverEvent(previewStep)
            }
            val key = (previewStep - 1) % 20
            effect.show(event, (34 + key % 5 * 63) * sx, miniature.top + (50 + key / 5 * 37) * sy)
            feedback.playEvent(event, settings, AppPreference.vibration_preference != false)
        }
        fun restart() {
            previewRunnable?.let(frame::removeCallbacks)
            previewStep = 0; effect.clear(); feedback.stop()
            previewRunnable = object : Runnable {
                override fun run() {
                    play()
                    if (previewStep < 13) frame.postDelayed(this, if (previewStep < 10) 300 else 2100)
                }
            }
            frame.postDelayed(previewRunnable, 180)
        }
        frame.setOnClickListener { restart() }
        preview = AlertDialog.Builder(context).setTitle(R.string.fever_preview).setView(frame)
            .setPositiveButton(android.R.string.ok, null).create().also { dialog ->
                dialog.setOnDismissListener { previewRunnable?.let(frame::removeCallbacks); effect.clear(); feedback.release(); preview = null }
                dialog.show()
            }
        restart()
    }
    private var previewRunnable: Runnable? = null
    override fun onDestroyView() { preview?.dismiss(); super.onDestroyView() }
}
