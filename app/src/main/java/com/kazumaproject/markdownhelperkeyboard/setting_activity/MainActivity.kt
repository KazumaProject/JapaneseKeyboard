package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appPreference: AppPreference
    private lateinit var binding: ActivityMainBinding
    private var currentDestinationId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seedColor = appPreference.seedColor

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
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_setting,
                R.id.settingMainFragment,
                R.id.navigation_learn_dictionary,
                R.id.navigation_user_dictionary,
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        setupBottomNavigation(navView, navController)
        setupSettingHomeSwitchMenu(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            currentDestinationId = destination.id
            if (destination.id == R.id.navigation_setting ||
                destination.id == R.id.settingMainFragment
            ) {
                navView.menu.findItem(R.id.navigation_setting)?.isChecked = true
            }
            invalidateOptionsMenu()
        }

        val handledIntent = handleIntent(intent)
        if (savedInstanceState == null && !handledIntent) {
            navigateToPreferredSettingHome(navController)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    /**
     * Intentを処理して適切な画面に遷移する
     */
    private fun handleIntent(intent: Intent?): Boolean {
        val extra = intent?.getStringExtra("openSettingActivity")
        return extra?.let { request ->
            val navController = findNavController(R.id.nav_host_fragment_activity_main)
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

        if (destinationId == R.id.navigation_setting) {
            item.setTitle(R.string.setting_switch_to_legacy_home)
        } else {
            item.setTitle(R.string.setting_switch_to_new_home)
        }
        item.setIcon(CoreR.drawable.swap_horiz_24px)
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    private fun switchSettingHome(navController: NavController) {
        when (currentDestinationId ?: navController.currentDestination?.id) {
            R.id.navigation_setting -> {
                appPreference.setting_use_new_home_screen_preference = false
                navigateReplacingCurrentHome(
                    navController = navController,
                    targetDestinationId = R.id.settingMainFragment,
                    popUpToDestinationId = R.id.navigation_setting,
                    inclusive = false,
                )
            }

            R.id.settingMainFragment -> {
                appPreference.setting_use_new_home_screen_preference = true
                navigateReplacingCurrentHome(
                    navController = navController,
                    targetDestinationId = R.id.navigation_setting,
                    popUpToDestinationId = R.id.settingMainFragment,
                    inclusive = true,
                )
            }
        }
    }

    private fun navigateToPreferredSettingHome(navController: NavController): Boolean {
        val targetDestinationId = if (appPreference.setting_use_new_home_screen_preference) {
            R.id.navigation_setting
        } else {
            R.id.settingMainFragment
        }
        if (navController.currentDestination?.id == targetDestinationId) return false
        val currentDestinationId = navController.currentDestination?.id
        val popUpToDestinationId = if (currentDestinationId == R.id.settingMainFragment) {
            R.id.settingMainFragment
        } else {
            R.id.navigation_setting
        }
        val inclusive = targetDestinationId == R.id.navigation_setting ||
            popUpToDestinationId == R.id.settingMainFragment
        return navigateReplacingCurrentHome(
            navController = navController,
            targetDestinationId = targetDestinationId,
            popUpToDestinationId = popUpToDestinationId,
            inclusive = inclusive,
        )
    }

    private fun navigateReplacingCurrentHome(
        navController: NavController,
        targetDestinationId: Int,
        popUpToDestinationId: Int,
        inclusive: Boolean,
    ): Boolean {
        return runCatching {
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(popUpToDestinationId, inclusive)
                .build()
            navController.navigate(targetDestinationId, null, options)
            true
        }.getOrDefault(false)
    }
}
