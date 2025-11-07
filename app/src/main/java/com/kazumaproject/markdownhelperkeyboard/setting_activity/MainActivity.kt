package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appPreference: AppPreference
    private lateinit var binding: ActivityMainBinding

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
                R.id.navigation_learn_dictionary,
                R.id.navigation_user_dictionary,
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Intentを処理して適切な画面に遷移する
     */
    private fun handleIntent(intent: Intent?) {
        val extra = intent?.getStringExtra("openSettingActivity")
        extra?.let { request ->
            val navController = findNavController(R.id.nav_host_fragment_activity_main)
            when (request) {
                "setting_fragment_request" -> {
                    navController.popBackStack()
                    navController.navigate(R.id.navigation_setting)
                }

                "dictionary_fragment_request" -> {
                    navController.navigate(R.id.navigation_learn_dictionary)
                }
            }
        }
    }

}
