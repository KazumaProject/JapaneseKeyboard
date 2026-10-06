package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.XmlRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.Preference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/** Inflate only unpublished hierarchies on IO. Publish and configure them on main. */
abstract class AsyncPreferenceFragment : PreferenceFragmentCompat() {
    @get:XmlRes protected open val preferencesXmlRes: Int = 0
    private var rootKey: String? = null
    private var pendingPreferenceState: Bundle? = null
    private var pendingListState: android.os.Parcelable? = null
    private var loadingUi: SettingsLoadingUi? = null
    private var loadJob: Job? = null
    protected var preferencesReady = false
        private set

    final override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        this.rootKey = rootKey
        pendingPreferenceState = savedInstanceState?.getBundle(PREFERENCES_STATE)?.let(::Bundle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val content = super.onCreateView(inflater, container, savedInstanceState)
        return SettingsLoadingUi(content.context, ::reloadPreferences).also { loadingUi = it }.wrap(content)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (preferencesReady) {
            onPreferencesReady(savedInstanceState, rootKey)
        } else {
            reloadPreferences()
        }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    protected open fun createPreferenceHierarchy(context: Context): PreferenceScreen =
        preferenceManager.inflateFromResource(context, preferencesXmlRes, null)

    // Programmatic builders describe callbacks on IO; only main attaches listeners.
    protected fun Preference.configureClickListener(listener: Preference.OnPreferenceClickListener) {
        checkNotNull(hierarchyListeners.get()).add { onPreferenceClickListener = listener }
    }

    protected fun Preference.configureChangeListener(listener: Preference.OnPreferenceChangeListener) {
        checkNotNull(hierarchyListeners.get()).add { onPreferenceChangeListener = listener }
    }

    protected open suspend fun preparePreferenceData(context: Context) = Unit
    protected open fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) = Unit
    protected open fun onPreferencesResumed() = Unit

    protected fun reloadPreferences() {
        if (loadJob?.isActive == true || view == null) return
        if (preferencesReady) {
            pendingPreferenceState = Bundle().also { preferenceScreen?.saveHierarchyState(it) }
            pendingListState = listView.layoutManager?.onSaveInstanceState()
        }
        preferencesReady = false
        preferenceScreen = null
        val context = requireContext()
        val ui = loadingUi ?: return
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            ui.load {
                settingsIo(SettingsLoadStage.PREFERENCES) { AppPreference.awaitInitializationSuspending() }
                preparePreferenceData(context)
                val (screen, listeners) = settingsIo(SettingsLoadStage.XML) {
                    // AndroidX's inflater has a static unsynchronized constructor cache.
                    // All settings inflation goes through this mutex, including reloads.
                    inflationMutex.withLock {
                        val listeners = mutableListOf<() -> Unit>()
                        hierarchyListeners.set(listeners)
                        try {
                            val inflated = createPreferenceHierarchy(context)
                            val screen = rootKey?.let { key ->
                                requireNotNull(inflated.findPreference<Preference>(key) as? PreferenceScreen) {
                                    "Preference root $key is not a screen"
                                }
                            } ?: inflated
                            screen to listeners
                        } finally {
                            hierarchyListeners.remove()
                        }
                    }
                }
                coroutineContext.ensureActive()
                // No screen is published while the worker owns PreferenceManager's editor.
                preferenceScreen = screen
                listeners.forEach { it() }
                pendingPreferenceState?.let(screen::restoreHierarchyState)
                onPreferencesReady(null, rootKey)
                preferencesReady = true
                if (isResumed) onPreferencesResumed()
                // setPreferenceScreen posts the RecyclerView adapter binding first.
                awaitPreferenceLayout()
                pendingListState?.let { listView.layoutManager?.onRestoreInstanceState(it) }
                pendingListState = null
                pendingPreferenceState = null
                view?.let { scrollToHighlightedPreferenceAfterLayout(it) }
            }
        }
    }

    protected suspend fun refreshPreferenceData(work: suspend () -> Unit) {
        loadingUi?.load { work() }
    }

    protected fun launchPreferenceRefresh(work: suspend () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch { refreshPreferenceData(work) }
    }

    override fun onResume() {
        super.onResume()
        if (preferencesReady) onPreferencesResumed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!preferencesReady) pendingPreferenceState?.let { outState.putBundle(PREFERENCES_STATE, Bundle(it)) }
    }

    override fun onDestroyView() {
        // The unpublished worker result is discarded by withContext's cancellation check.
        loadJob?.cancel()
        loadJob = null
        loadingUi = null
        super.onDestroyView()
    }

    private suspend fun awaitPreferenceLayout() = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
        val list = listView
        val observer = list.viewTreeObserver
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (list.adapter != null && list.isLaidOut) {
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                    if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
                }
                return true
            }
        }
        observer.addOnPreDrawListener(listener)
        continuation.invokeOnCancellation {
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
    }

    companion object {
        private const val PREFERENCES_STATE = "android:preferences"
        private val inflationMutex = Mutex()
        private val hierarchyListeners = ThreadLocal<MutableList<() -> Unit>>()
    }
}
