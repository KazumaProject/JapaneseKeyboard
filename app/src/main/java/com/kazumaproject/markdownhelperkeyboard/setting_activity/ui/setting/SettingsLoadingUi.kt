package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.kazumaproject.markdownhelperkeyboard.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

internal enum class SettingsLoadStage { PREFERENCES, FONT, XML, PACKAGE_INFO, MODELS, DATABASE, SEARCH, KEYBOARD_STATUS }

/** Test gates run at the real work boundary, never on the main thread. */
internal object SettingsLoadDiagnostics {
    @Volatile var beforeLoad: (suspend (SettingsLoadStage) -> Unit)? = null
    suspend fun before(stage: SettingsLoadStage) { beforeLoad?.invoke(stage) }
}

internal suspend fun <T> settingsIo(stage: SettingsLoadStage, work: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        SettingsLoadDiagnostics.before(stage)
        work()
    }

internal sealed interface SettingsLoadingState {
    data object Loading : SettingsLoadingState
    data object Ready : SettingsLoadingState
    data class Error(val cause: Throwable) : SettingsLoadingState
}

/** Each view owns its loading operations; one completion cannot hide another's progress. */
internal class SettingsLoadingUi(context: Context, private val retry: () -> Unit, private val blocksContent: Boolean = true) {
    val overlay = FrameLayout(context).apply {
        id = R.id.settings_loading_overlay
        isClickable = blocksContent
        isFocusable = blocksContent
        if (blocksContent) {
            val colors = context.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorSurface))
            try { setBackgroundColor(colors.getColor(0, android.graphics.Color.TRANSPARENT)) }
            finally { colors.recycle() }
        }
    }
    private val progress = ProgressBar(context).apply { id = R.id.settings_loading_progress }
    private val message = TextView(context).apply {
        gravity = Gravity.CENTER
        setText(R.string.settings_loading)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val retryButton = Button(context).apply {
        id = R.id.settings_loading_retry
        setText(R.string.settings_loading_retry)
        setOnClickListener { retry() }
    }
    private var pending = 0
    private var failure: Throwable? = null
    var state: SettingsLoadingState = SettingsLoadingState.Ready
        private set

    init {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(progress)
            addView(message)
            addView(retryButton)
        }
        overlay.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
        ))
        render()
    }

    fun wrap(content: View): FrameLayout = FrameLayout(content.context).apply {
        addView(content, FrameLayout.LayoutParams(-1, -1))
        addView(this@SettingsLoadingUi.overlay, FrameLayout.LayoutParams(-1, -1))
    }

    suspend fun <T> load(work: suspend () -> T): T? {
        if (pending == 0) failure = null
        pending++
        render()
        return try {
            work()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error
            Timber.e(error, "Settings loading failed")
            null
        } finally {
            pending--
            render()
        }
    }

    private fun render() {
        state = when {
            pending > 0 -> SettingsLoadingState.Loading
            failure != null -> SettingsLoadingState.Error(failure!!)
            else -> SettingsLoadingState.Ready
        }
        overlay.isVisible = state != SettingsLoadingState.Ready
        progress.isVisible = state == SettingsLoadingState.Loading
        retryButton.isVisible = state is SettingsLoadingState.Error
        message.setText(if (state is SettingsLoadingState.Error) R.string.settings_loading_failed else R.string.settings_loading)
    }
}
