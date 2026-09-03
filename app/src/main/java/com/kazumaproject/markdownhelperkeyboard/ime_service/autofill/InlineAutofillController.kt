package com.kazumaproject.markdownhelperkeyboard.ime_service.autofill

import android.content.Context
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlineContentView
import androidx.annotation.RequiresApi
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Inflates framework-owned inline suggestions without inspecting or converting their contents.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal class InlineAutofillController(
    context: Context,
    private val onViewsChanged: (List<InlineContentView>) -> Unit,
) {
    // InlineContentView is attached to the IME window, so inflate it with the service context.
    private val hostContext = context
    private val mainExecutor = context.mainExecutor
    private val inflateExecutor: ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "InlineAutofillInflater").apply { isDaemon = true }
    }
    private val generationTracker = InlineAutofillGenerationTracker()
    private val stateLock = Any()
    private var inflatedViews: List<InlineContentView> = emptyList()
    private var activeToken: InlineAutofillGenerationTracker.Token? = null
    private var destroyed = false

    fun startInputSession() {
        val token = synchronized(stateLock) {
            inflatedViews = emptyList()
            generationTracker.startInputSession().also { activeToken = it }
        }
        publishIfCurrent(token, emptyList())
    }

    fun handleResponse(response: InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
            .take(InlineSuggestionsRequestFactory.MAX_SUGGESTION_COUNT)
        Log.d(TAG, "Received ${suggestions.size} inline suggestions")
        val token = synchronized(stateLock) {
            if (destroyed) return false
            inflatedViews = emptyList()
            generationTracker.beginResponse().also { activeToken = it }
        }
        if (suggestions.isEmpty()) {
            publishIfCurrent(token, emptyList())
        } else {
            inflate(token, suggestions)
        }
        return true
    }

    fun clear() {
        val token = synchronized(stateLock) {
            inflatedViews = emptyList()
            generationTracker.invalidateResponse().also { activeToken = it }
        }
        publishIfCurrent(token, emptyList())
    }

    fun destroy() {
        val token = synchronized(stateLock) {
            destroyed = true
            inflatedViews = emptyList()
            generationTracker.invalidateResponse().also { activeToken = it }
        }
        publishIfCurrent(token, emptyList())
        inflateExecutor.shutdownNow()
    }

    private fun inflate(
        token: InlineAutofillGenerationTracker.Token,
        suggestions: List<InlineSuggestion>,
    ) {
        val results = arrayOfNulls<InlineContentView>(suggestions.size)
        val resultLock = Any()
        var completed = 0

        fun complete(index: Int, view: InlineContentView?) {
            val readyViews = synchronized(resultLock) {
                results[index] = view
                completed += 1
                if (completed == results.size) results.filterNotNull() else null
            }
            if (readyViews != null) {
                publishIfCurrent(token, readyViews)
            }
        }

        suggestions.forEachIndexed { index, suggestion ->
            runCatching {
                suggestion.inflate(
                    hostContext,
                    Size(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                    inflateExecutor,
                ) { view ->
                    Log.d(TAG, "Inflated inline suggestion ${index + 1}/${suggestions.size}: ${view != null}")
                    complete(index, view)
                }
            }.onFailure {
                Log.w(TAG, "Inline suggestion inflation failed", it)
                complete(index, null)
            }
        }
    }

    private fun publishIfCurrent(
        token: InlineAutofillGenerationTracker.Token,
        views: List<InlineContentView>,
    ) {
        mainExecutor.execute {
            if (!generationTracker.isCurrent(token)) {
                Log.d(TAG, "Discarded stale inline suggestion response")
                return@execute
            }
            synchronized(stateLock) {
                if (destroyed && views.isNotEmpty()) return@execute
                inflatedViews = views
            }
            Log.d(TAG, "Publishing ${views.size} inline suggestion views")
            onViewsChanged(views)
        }
    }

    private companion object {
        const val TAG = "SumireInlineAutofill"
    }
}
