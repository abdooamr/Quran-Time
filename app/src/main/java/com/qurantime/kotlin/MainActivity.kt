package com.qurantime.kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.qurantime.kotlin.core.l10n.LocalizedContent
import com.qurantime.kotlin.core.nav.AppNavHost
import com.qurantime.kotlin.core.theme.AppThemeMode
import com.qurantime.kotlin.core.theme.QuranTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LocalizedContent("ar") {
                QuranTimeTheme(AppThemeMode.SYSTEM) {
                    AppNavHost(rememberNavController())
                }
            }
        }
    }
}
