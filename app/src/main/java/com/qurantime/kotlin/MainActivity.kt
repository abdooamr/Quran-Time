package com.qurantime.kotlin

import android.os.Bundle
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.qurantime.kotlin.core.api.ApiLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as QuranTimeApp).container
    private var settingsLoaded by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays only until settings (language, theme, last page) are read.
        installSplashScreen().setKeepOnScreenCondition { !settingsLoaded }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            container.settings.load()
            settingsLoaded = true
            // After the first frame: warm the surah list (the header needs it) off the main thread.
            Choreographer.getInstance().postFrameCallback {
                val lang = ApiLanguage.fromTag(container.settings.state.value.locale)
                container.appScope.launch(Dispatchers.IO) { runCatching { container.catalog.suwar(lang) } }
            }
        }
        setContent {
            if (settingsLoaded) QuranTimeRoot(container)
        }
    }
}
