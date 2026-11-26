package com.qurantime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.l10n.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as QuranTimeApp).container
    private var settingsLoaded by mutableStateOf(false)

    private fun warmOpeningPage() {
        val page = container.settings.state.value.lastPage
        val width = resources.displayMetrics.widthPixels
        container.appScope.launch(Dispatchers.IO) {
            // Only a page already on disk: converting it here would guess the width wrong.
            if (!container.pages.isCached(page)) return@launch
            runCatching {
                val r = container.pages.mask(page, width)
                val bmp = r.file.inputStream().buffered().use { container.maskCodec.decode(it) }.also { it.prepareToDraw() }
                container.pageBitmaps.put(page, r.bucket, bmp)
            }
        }
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** The language the activity was created in (dialogs and sheets use it). */
    private var appliedLocale = "ar"

    override fun attachBaseContext(newBase: Context) {
        appliedLocale = AppLocale.saved(newBase)
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        container.notificationPermission = {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // The splash stays only until settings (language, theme, last page) are read.
        installSplashScreen().setKeepOnScreenCondition { !settingsLoaded }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            container.settings.load()
            // The activity's own language (used by dialogs and sheets) must match
            // the setting; if not, re-create behind the splash before anything shows.
            if (container.settings.state.value.locale != appliedLocale) {
                AppLocale.save(this@MainActivity, container.settings.state.value.locale)
                recreate()
                return@launch
            }
            launch {
                container.settings.state.collect { s ->
                    if (s.locale != appliedLocale) {
                        AppLocale.save(this@MainActivity, s.locale)
                        recreate()
                    }
                }
            }
            // Decode the opening page while the first frame is being built.
            warmOpeningPage()
            settingsLoaded = true
            // After the first frame: warm the surah list (the header needs it) off the main thread.
            Choreographer.getInstance().postFrameCallback {
                val lang = ApiLanguage.fromTag(container.settings.state.value.locale)
                container.appScope.launch(Dispatchers.IO) { runCatching { container.catalog.suwar(lang) } }
                // Catch up with downloads that finished while the app was closed.
                container.appScope.launch { runCatching { container.downloads.reconcile() } }
                // Bring back the last recitation, paused, where it was.
                container.mainScope.launch { container.playerController.restore() }
            }
        }
        setContent {
            if (settingsLoaded) QuranTimeRoot(container)
        }
    }
}
