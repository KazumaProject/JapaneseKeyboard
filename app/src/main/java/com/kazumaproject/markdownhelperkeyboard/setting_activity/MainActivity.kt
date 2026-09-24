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
import androidx.lifecycle.lifecycleScope
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
    private var intentReceivedDuringInitialization: Intent? = null
    internal var isSettingsContentReady = false
        private set
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
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            appPreference = withContext(Dispatchers.IO) {
                appPreferenceProvider.get()
            }
            initializeSettingsContent(savedInstanceState)
        }
    }

    private fun initializeSettingsContent(savedInstanceState: Bundle?) {
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

        val pendingIntent = intentReceivedDuringInitialization
        if (pendingIntent != null) {
            handleIntent(pendingIntent)
            intentReceivedDuringInitialization = null
        } else if (savedInstanceState == null && !handleIntent(intent)) {
            navigateToPreferredSettingHome(navController)
        }
        isSettingsContentReady = true
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
        } else {
            intentReceivedDuringInitialization = intent
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
        val extra = intent?.getStringExtra("openSettingActivity")
        return extra?.let { request ->
            val navController = currentNavController()
            when (request) {
                "setting_fragment_request" -> {
                    navigateToPreferredSettingHome(navController)
                    true
                }

                "dictionary_fragment_request" -> {
                    navController.navigate(R.id.navigation_learn_dictionary)
                    true
                }

                else -> false
            }
        } ?: false
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
}
