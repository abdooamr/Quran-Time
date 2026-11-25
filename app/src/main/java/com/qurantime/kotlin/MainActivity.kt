package com.qurantime.kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.qurantime.kotlin.core.nav.AppNavHost
import com.qurantime.kotlin.core.settings.SettingsDrivenApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as QuranTimeApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays only until settings (language, theme, last page) are read.
        var settingsLoaded = false
        installSplashScreen().setKeepOnScreenCondition { !settingsLoaded }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            container.settings.load()
            settingsLoaded = true
        }
        setContent {
            SettingsDrivenApp(container.settings) {
                AppNavHost(rememberNavController(), online = container.online)
            }
        }
    }
}
