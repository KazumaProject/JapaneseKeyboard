package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.lifecycle.withStarted
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider
import com.kazumaproject.core.R as CoreR

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appPreferenceProvider: Provider<AppPreference>
    private lateinit var appPreference: AppPreference
    private lateinit var binding: ActivityMainBinding
    private lateinit var mainNavController: NavController
    private var bottomNavigationView: BottomNavigationView? = null
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
        R.id.candidateViewHeightSettingFragment,
        R.id.candidateHeightLandscapeSettingFragment,
        R.id.candidateHeightDefaultsFragment,
    )
    private val destinationsWithOwnToolbar =
        destinationsWithoutBottomNavigation + R.id.shortcutToolbarSizeSettingFragment
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

        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            appPreference = withContext(Dispatchers.IO) {
                initializationGateForTest?.invoke()
                appPreferenceProvider.get().also { it.awaitInitialization() }
            }
            val initializedWhileStarted = lifecycle.withStarted {
                initializeSettingsContentIfSafe()
            }
            if (!initializedWhileStarted) {
                lifecycle.withResumed {
                    initializeSettingsContentIfSafe()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
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
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.doOnAttach { ViewCompat.requestApplyInsets(it) }

        mainNavController = findMainNavController()
        val navController = mainNavController
        installNavigationGraph(navController)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_setting,
                R.id.settingMainFragment,
                R.id.navigation_learn_dictionary,
                R.id.navigation_user_dictionary,
            )
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
            updateBottomNavigationVisibility(
                appPreference.setting_use_new_home_screen_preference,
                navController,
            )
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
        val navController = currentNavController()
        return navController.navigateUp() || super.onSupportNavigateUp()
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
        val useNewDashboard = appPreference.setting_use_new_home_screen_preference
        val resolvedNavController = navController ?: currentNavController()
        updateBottomNavigationVisibility(useNewDashboard, resolvedNavController)
        ensurePreferredSettingHomeIfNeeded(resolvedNavController)
    }

    private fun updateBottomNavigationVisibility(
        useNewDashboard: Boolean,
        navController: NavController,
    ) {
        if (useNewDashboard) {
            binding.navViewContainer.visibility = View.GONE
            return
        }
        ensureBottomNavigation(navController)
        binding.navViewContainer.visibility =
            if (navController.currentDestination?.id in destinationsWithoutBottomNavigation) {
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
        return navView
    }

    private fun setupBottomNavigation(
        navView: BottomNavigationView,
        navController: NavController,
    ) {
        navView.setupWithNavController(navController)
        navView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.navigation_setting) {
                navigateToPreferredSettingHome(navController)
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
            item.setTitle(R.string.setting_switch_to_legacy_home)
        } else {
            item.setTitle(R.string.setting_switch_to_new_home)
        }
        item.setIcon(CoreR.drawable.swap_horiz_24px)
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
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
