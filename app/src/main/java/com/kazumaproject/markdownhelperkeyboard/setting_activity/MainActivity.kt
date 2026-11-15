package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.kazumaproject.markdownhelperkeyboard.setting_activity.navigation.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.lifecycle.withStarted
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.kazumaproject.markdownhelperkeyboard.local_font.LocalFontRepository
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import android.widget.FrameLayout
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingsLoadingUi
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingsLoadStage
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.settingsIo
import kotlinx.coroutines.Job
import com.kazumaproject.core.R as CoreR

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appPreferenceProvider: Provider<AppPreference>
    @Inject
    lateinit var localFontRepositoryProvider: Provider<LocalFontRepository>
    private var initializationJob: Job? = null
    private lateinit var initializationUi: SettingsLoadingUi
    private lateinit var contentContainer: FrameLayout
    private lateinit var appPreference: AppPreference
    private lateinit var binding: ActivityMainBinding
    private lateinit var mainNavController: NavController
    private var bottomNavigationView: BottomNavigationView? = null
    private lateinit var navigationStore: SettingsNavigationStore
    private lateinit var navigationDrawer: SettingsNavigationDrawer
    private lateinit var appBarConfiguration: AppBarConfiguration
    private var bottomDrag: SettingsBottomDrag? = null
    private var restoredNavigationState: Bundle? = null
    private val navigationPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsNavigationStore.KEY && ::navigationStore.isInitialized) runOnUiThread { renderBottomNavigation() }
    }
    private val drawerBack = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { binding.settingsDrawerLayout.closeDrawer(GravityCompat.START) }
    }

    private var currentDestinationId: Int? = null
    private var restoredNavHost: NavHostFragment? = null
    private var deferredFragmentManagerState: Bundle? = null
    private var initialIntentHandled = false
    private var pendingIntentReceivedDuringInitialization = false
    private var pendingIntentRequest: String? = null
    internal var isSettingsContentReady = false
        private set

    init {
        // FragmentActivity restores fragments when ComponentActivity creates its context,
        // before super.onCreate() returns. Cap the restored host before dispatchCreate()
        // so its child preference fragments cannot read preferences on the UI thread.
        addOnContextAvailableListener {
            restoredNavHost = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
            restoredNavHost?.let { host ->
                supportFragmentManager.beginTransaction()
                    .setMaxLifecycle(host, Lifecycle.State.INITIALIZED)
                    .commitNow()
            }
        }
    }

    private val destinationsWithoutBottomNavigation = setOf(
        R.id.popupMapEditorFragment,
        R.id.candidateViewHeightSettingFragment,
        R.id.candidateHeightLandscapeSettingFragment,
        R.id.candidateHeightDefaultsFragment,
        R.id.localFontSettingsFragment,
    )
    private val destinationsWithOwnToolbar = setOf(
        R.id.candidateViewHeightSettingFragment,
        R.id.candidateHeightLandscapeSettingFragment,
        R.id.candidateHeightDefaultsFragment,
        R.id.shortcutToolbarSizeSettingFragment,
    )
    private val destinationsWithoutSharedActionBar =
        destinationsWithOwnToolbar + R.id.enableKeyboardFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        // ComponentActivity consumes the nested saved-state bundle while restoring
        // FragmentManager. Keep the original fragment state for saves made before
        // the restored NavHost is allowed to create its child fragments.
        deferredFragmentManagerState = savedInstanceState
            ?.getBundle(SAVED_STATE_REGISTRY_KEY)
            ?.getBundle(FRAGMENT_MANAGER_STATE_KEY)
            ?.let(::Bundle)
        initialIntentHandled = savedInstanceState?.getBoolean(
            INITIAL_INTENT_HANDLED_KEY,
            true,
        ) ?: false
        pendingIntentReceivedDuringInitialization = savedInstanceState
            ?.getBoolean(PENDING_INTENT_RECEIVED_KEY) ?: false
        pendingIntentRequest = savedInstanceState?.getString(PENDING_INTENT_REQUEST_KEY)

        restoredNavigationState = savedInstanceState?.getBundle("settings-navigation-ui")
        super.onCreate(savedInstanceState)
        // Window fitting must be configured before the delayed preferences load.
        // Configuring it after the Activity has started can leave AppCompat's
        // ActionBar and content with different system-bar insets.
        enableEdgeToEdge()
        initializationUi = SettingsLoadingUi(this, ::loadSettingsContent)
        contentContainer = FrameLayout(this)
        // Keep the inset receiver attached while replacing loading content. AppCompat
        // caches its inner insets and need not dispatch them again to a new root.
        val contentRoot = initializationUi.wrap(contentContainer)
        // AppCompat's ActionBarOverlayLayout includes the visible ActionBar in the
        // content insets. Apply those insets once, then consume them for descendants.
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout(),
                    Insets.NONE,
                )
                .build()
        }
        contentRoot.doOnAttach { root ->
            root.post { ViewCompat.requestApplyInsets(root) }
        }
        setContentView(contentRoot)
        loadSettingsContent()
    }

    private fun loadSettingsContent() {
        if (initializationJob?.isActive == true || isSettingsContentReady) return
        initializationJob = lifecycleScope.launch {
            initializationUi.load {
                appPreference = settingsIo(SettingsLoadStage.PREFERENCES) {
                    initializationGateForTest?.invoke()
                    appPreferenceProvider.get().also {
                        it.startInitialization(applicationContext)
                        it.awaitInitializationSuspending()
                    }
                }
                settingsIo(SettingsLoadStage.FONT) { localFontRepositoryProvider.get().loadIfNeeded() }
                val initializedWhileStarted = lifecycle.withStarted {
                    initializeSettingsContentIfSafe()
                }
                if (!initializedWhileStarted) {
                    lifecycle.withResumed { initializeSettingsContentIfSafe() }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val navigationState = if (::navigationDrawer.isInitialized) navigationDrawer.saveState().apply {
            putBoolean("open", binding.settingsDrawerLayout.isDrawerVisible(GravityCompat.START))
        } else restoredNavigationState
        navigationState?.let { outState.putBundle("settings-navigation-ui", it) }
        outState.putBoolean(INITIAL_INTENT_HANDLED_KEY, initialIntentHandled)
        outState.putBoolean(
            PENDING_INTENT_RECEIVED_KEY,
            pendingIntentReceivedDuringInitialization,
        )
        outState.putString(PENDING_INTENT_REQUEST_KEY, pendingIntentRequest)

        deferredFragmentManagerState?.let { deferredState ->
            val registryState = outState.getBundle(SAVED_STATE_REGISTRY_KEY) ?: Bundle()
            registryState.putBundle(FRAGMENT_MANAGER_STATE_KEY, Bundle(deferredState))
            outState.putBundle(SAVED_STATE_REGISTRY_KEY, registryState)
        }
    }

    private fun initializeSettingsContent() {
        val seedColor = appPreference.seedColor
        val dynamicColorsAvailable = DynamicColors.isDynamicColorAvailable()

        if (seedColor == 0x00000000) {
            DynamicColors.applyToActivityIfAvailable(this)
        } else {
            val options = DynamicColorsOptions.Builder()
                .setContentBasedSource(seedColor)
                .build()
            DynamicColors.applyToActivityIfAvailable(
                this,
                options
            )
        }
        if (dynamicColorsAvailable) {
            // AppCompat may have created decor while preferences loaded.
            themedBackground(android.R.attr.windowBackground)?.let(window::setBackgroundDrawable)
        }
        // Creating the restored host now also creates its child preference fragments.
        // AppPreference is ready, and the activity theme has already been applied.
        restoredNavHost?.let { host ->
            supportFragmentManager.beginTransaction()
                .setMaxLifecycle(host, Lifecycle.State.CREATED)
                .commitNow()
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        contentContainer.removeAllViews()
        contentContainer.addView(binding.root, FrameLayout.LayoutParams(-1, -1))

        mainNavController = findMainNavController()
        val navController = mainNavController
        installNavigationGraph(navController)
        navigationStore = SettingsNavigationStore(this)
        setupNavigationDrawer(navController)
        appBarConfiguration = AppBarConfiguration(
            SettingsNavigationStore.DEFAULT_BOTTOM.mapNotNull { key ->
                navigationStore.catalog.firstOrNull { it.key == key }?.destination
            }.toSet() + R.id.settingMainFragment,
            binding.settingsDrawerLayout,
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        if (dynamicColorsAvailable) {
            themedBackground(com.google.android.material.R.attr.colorSurfaceContainer)
                ?.let { supportActionBar?.setBackgroundDrawable(it) }
        }
        setupSettingHomeSwitchMenu(navController)
        applySettingHomeModeFromPreference(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            currentDestinationId = destination.id
            if (destination.id == R.id.navigation_setting ||
                destination.id == R.id.settingMainFragment
            ) {
                bottomNavigationView
                    ?.menu
                    ?.findItem(R.id.navigation_setting)
                    ?.isChecked = true
            }
            updateBottomNavigationVisibility(navController)
            updateNavigationSelection(destination.id)
            updateSharedActionBarVisibility(destination.id)
            invalidateOptionsMenu()
        }

        if (pendingIntentReceivedDuringInitialization) {
            handleSettingRequest(pendingIntentRequest)
            pendingIntentReceivedDuringInitialization = false
            pendingIntentRequest = null
            initialIntentHandled = true
        } else if (!initialIntentHandled) {
            if (!handleIntent(intent)) {
                navigateToPreferredSettingHome(navController)
            }
            initialIntentHandled = true
        }
        isSettingsContentReady = true
    }

    private fun initializeSettingsContentIfSafe(): Boolean {
        if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return false

        initializeSettingsContent()
        restoredNavHost?.let { host ->
            supportFragmentManager.beginTransaction()
                .setMaxLifecycle(host, Lifecycle.State.RESUMED)
                .commitNow()
        }
        deferredFragmentManagerState = null
        return true
    }

    private fun themedBackground(attribute: Int): Drawable? {
        val attrs = theme.obtainStyledAttributes(intArrayOf(attribute))
        return try {
            attrs.getDrawable(0)
        } finally {
            attrs.recycle()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isSettingsContentReady) {
            handleIntent(intent)
            initialIntentHandled = true
        } else {
            pendingIntentReceivedDuringInitialization = true
            pendingIntentRequest = intent?.getStringExtra(OPEN_SETTING_ACTIVITY_EXTRA)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!isSettingsContentReady) return super.onSupportNavigateUp()
        if (binding.settingsDrawerLayout.isDrawerVisible(GravityCompat.START)) {
            binding.settingsDrawerLayout.closeDrawer(GravityCompat.START)
            return true
        }
        val navController = currentNavController()
        val current = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as? androidx.navigation.fragment.NavHostFragment)?.childFragmentManager?.primaryNavigationFragment
        if (current is com.kazumaproject.markdownhelperkeyboard.sumire_popup.PopupMapEditorFragment) {
            current.leaveEditor()
            return true
        }
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun updateSharedActionBarVisibility(destinationId: Int) {
        if (destinationId in destinationsWithoutSharedActionBar) {
            supportActionBar?.hide()
        } else {
            supportActionBar?.show()
        }
    }

    /**
     * Intentを処理して適切な画面に遷移する
     */
    private fun handleIntent(intent: Intent?): Boolean {
        return handleSettingRequest(intent?.getStringExtra(OPEN_SETTING_ACTIVITY_EXTRA))
    }

    private fun handleSettingRequest(request: String?): Boolean {
        return when (request) {
            "setting_fragment_request" -> {
                navigateToPreferredSettingHome(currentNavController())
                true
            }

            "dictionary_fragment_request" -> {
                currentNavController().navigate(R.id.navigation_learn_dictionary)
                true
            }

            else -> false
        }
    }

    private fun currentNavController(): NavController {
        return if (::mainNavController.isInitialized) {
            mainNavController
        } else {
            findMainNavController().also { mainNavController = it }
        }
    }

    private fun findMainNavController(): NavController {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        return navHostFragment.navController
    }

    private fun installNavigationGraph(navController: NavController) {
        if (navController.currentDestination != null) return
        val graph = navController.navInflater.inflate(R.navigation.mobile_navigation)
        graph.setStartDestination(preferredSettingHomeDestination())
        navController.graph = graph
    }

    private fun preferredSettingHomeDestination(): Int =
        if (appPreference.setting_use_new_home_screen_preference) {
            R.id.navigation_setting
        } else {
            R.id.settingMainFragment
        }

    fun applySettingHomeModeFromPreference(navController: NavController? = null) {
        val resolvedNavController = navController ?: currentNavController()
        updateBottomNavigationVisibility(resolvedNavController)
        ensurePreferredSettingHomeIfNeeded(resolvedNavController)
    }

    private fun updateBottomNavigationVisibility(
        navController: NavController,
    ) {
        ensureBottomNavigation(navController)
        binding.navViewContainer.visibility =
            if (navController.currentDestination?.id in destinationsWithoutBottomNavigation || navigationStore.bottom().isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }
    }

    private fun ensureBottomNavigation(navController: NavController): BottomNavigationView {
        bottomNavigationView?.let { return it }
        val navView = layoutInflater.inflate(
            R.layout.view_legacy_bottom_navigation,
            binding.navViewContainer,
            false,
        ) as BottomNavigationView
        binding.navViewContainer.addView(navView)
        setupBottomNavigation(navView, navController)
        bottomNavigationView = navView
        bottomDrag = SettingsBottomDrag(navView, navigationStore, ::renderBottomNavigation)
        renderBottomNavigation()
        return navView
    }

    private fun setupBottomNavigation(
        navView: BottomNavigationView,
        navController: NavController,
    ) {
        navView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.navigation_setting) {
                navigateToPreferredSettingHome(navController)
                true
            } else if (item.itemId == R.id.settingSearchFragment) {
                navController.navigate(item.itemId, navigationArguments(item.itemId), NavOptions.Builder()
                    .setLaunchSingleTop(true).setPopUpTo(navController.graph.findStartDestination().id, false).build())
                true
            } else {
                NavigationUI.onNavDestinationSelected(item, navController)
            }
        }
        navView.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.navigation_setting) {
                navigateToPreferredSettingHome(navController)
            }
        }
    }

    private fun navigationArguments(destination: Int): Bundle? = if (destination == R.id.settingSearchFragment) Bundle().apply {
        putString(com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchFragment.ARG_SEARCH_SCOPE,
            (if (appPreference.setting_use_new_home_screen_preference)
                com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchScope.NEW_HOME
            else com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchScope.LEGACY_TABS).name)
    } else null

    private fun setupNavigationDrawer(navController: NavController) {
        val width = minOf((360 * resources.displayMetrics.density).toInt(), resources.displayMetrics.widthPixels - (56 * resources.displayMetrics.density).toInt())
        binding.settingsDrawerContent.layoutParams = binding.settingsDrawerContent.layoutParams.apply { this.width = width }
        navigationDrawer = SettingsNavigationDrawer(this, navigationStore) { item ->
            binding.settingsDrawerLayout.closeDrawer(GravityCompat.START)
            if (item.key == "home") navigateToPreferredSettingHome(navController)
            else navController.navigate(item.destination, navigationArguments(item.destination), NavOptions.Builder().setLaunchSingleTop(true)
                .setPopUpTo(navController.graph.findStartDestination().id, false).build())
        }
        binding.settingsDrawerContent.addView(navigationDrawer)
        navigationDrawer.restoreState(restoredNavigationState)
        if (restoredNavigationState?.getBoolean("open") == true) binding.settingsDrawerLayout.post {
            if (!isFinishing && !isDestroyed) binding.settingsDrawerLayout.openDrawer(GravityCompat.START)
        }
        restoredNavigationState = null
        binding.settingsDrawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            private var handlingBack = false
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (slideOffset > 0f && !handlingBack) {
                    onBackPressedDispatcher.addCallback(this@MainActivity, drawerBack); handlingBack = true
                }
            }
            override fun onDrawerClosed(drawerView: View) {
                drawerBack.remove(); handlingBack = false; navigationDrawer.finishEditing()
            }
        })
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(navigationPreferencesListener)
    }

    private fun renderBottomNavigation() {
        if (bottomDrag?.isDragging == true) return
        val bar = bottomNavigationView ?: return
        val items = navigationStore.bottom()
        bar.menu.clear()
        items.forEachIndexed { index, item ->
            val title = when (item.key) {
                "home" -> getString(R.string.settings_nav_settings)
                "custom" -> getString(R.string.settings_nav_custom)
                else -> item.title
            }
            bar.menu.add(Menu.NONE, item.destination, index, title).setIcon(item.icon)
        }
        bottomDrag?.bind()
        updateNavigationSelection(mainNavController.currentDestination?.id)
        binding.navViewContainer.visibility = if (items.isEmpty() || mainNavController.currentDestination?.id in destinationsWithoutBottomNavigation) View.GONE else View.VISIBLE
    }

    private fun updateNavigationSelection(destination: Int?) {
        val resolved = if (destination == R.id.settingMainFragment) R.id.navigation_setting else destination
        navigationDrawer.select(resolved)
        val menu = bottomNavigationView?.menu ?: return
        val directlyRepresented = (0 until menu.size()).any { menu.getItem(it).itemId == resolved }
        val basicPages = (com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations.categories(this) +
            com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations.homeCategories(this))
            .mapNotNull { com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations.destinationId(it.destination) } +
            com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType.entries.map {
                com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.keyboardSettingsDestination(it)
            }
        val bottomDestination = if (!directlyRepresented && destination in basicPages) R.id.navigation_setting else resolved
        for (index in 0 until menu.size()) menu.getItem(index).isChecked = menu.getItem(index).itemId == bottomDestination
    }

    override fun onDestroy() {
        if (::navigationStore.isInitialized) androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(navigationPreferencesListener)
        super.onDestroy()
    }

    private fun setupSettingHomeSwitchMenu(navController: NavController) {
        addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.setting_home_switch_menu, menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    updateSettingHomeSwitchMenuItem(
                        menu.findItem(R.id.action_switch_setting_home)
                    )
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_switch_setting_home -> {
                            switchSettingHome(navController)
                            true
                        }

                        else -> false
                    }
                }
            },
            this,
        )
    }

    private fun updateSettingHomeSwitchMenuItem(item: MenuItem?) {
        if (item == null) return
        val destinationId = currentDestinationId
        val visible = destinationId == R.id.navigation_setting ||
            destinationId == R.id.settingMainFragment
        item.isVisible = visible
        if (!visible) return

        if (appPreference.setting_use_new_home_screen_preference) {
            item.setTitle(R.string.settings_legacy_short)
        } else {
            item.setTitle(R.string.settings_new_short)
        }
        item.setIcon(null)
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
    }

    private fun switchSettingHome(navController: NavController) {
        appPreference.setting_use_new_home_screen_preference =
            !appPreference.setting_use_new_home_screen_preference
        applySettingHomeModeFromPreference()
        navigateToPreferredSettingHome(navController)
        invalidateOptionsMenu()
    }

    private fun ensurePreferredSettingHomeIfNeeded(navController: NavController): Boolean {
        val currentDestinationId = navController.currentDestination?.id ?: return false
        if (currentDestinationId != R.id.navigation_setting &&
            currentDestinationId != R.id.settingMainFragment
        ) {
            return false
        }
        return navigateToPreferredSettingHome(navController)
    }

    private fun navigateToPreferredSettingHome(navController: NavController): Boolean {
        val targetDestinationId = preferredSettingHomeDestination()
        if (navController.currentDestination?.id == targetDestinationId) return false
        // Both home modes are roots. Clear detail/tab history even when the
        // other home has never been created in this activity.
        navController.graph.setStartDestination(targetDestinationId)
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(navController.graph.id, false)
            .build()
        navController.navigate(targetDestinationId, null, options)
        return true
    }

    internal companion object {
        @Volatile
        internal var initializationGateForTest: (() -> Unit)? = null

        private const val OPEN_SETTING_ACTIVITY_EXTRA = "openSettingActivity"
        private const val SAVED_STATE_REGISTRY_KEY =
            "androidx.lifecycle.BundlableSavedStateRegistry.key"
        private const val FRAGMENT_MANAGER_STATE_KEY = "android:support:fragments"
        private const val INITIAL_INTENT_HANDLED_KEY = "main.initialIntentHandled"
        private const val PENDING_INTENT_RECEIVED_KEY =
            "main.pendingIntentReceivedDuringInitialization"
        private const val PENDING_INTENT_REQUEST_KEY = "main.pendingIntentRequest"
    }
}
