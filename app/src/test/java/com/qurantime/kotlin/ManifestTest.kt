package com.qurantime.kotlin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestTest {
    private fun requested(): List<String> {
        val c = ApplicationProvider.getApplicationContext<Context>()
        return c.packageManager.getPackageInfo(c.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
    }

    @Test
    fun `plays in the background with a media notification`() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        for (p in listOf(
            Manifest.permission.FOREGROUND_SERVICE,
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.POST_NOTIFICATIONS,
        )) {
            assertTrue(p, p in requested())
        }
        val service = c.packageManager.getServiceInfo(
            android.content.ComponentName(c, "com.qurantime.kotlin.feature.audio.PlaybackService"),
            0,
        )
        assertTrue(service.exported)
    }

    @Test
    fun `can read the network state (OnlineStatus needs it)`() {
        assertTrue(requested().toString(), Manifest.permission.ACCESS_NETWORK_STATE in requested())
        assertTrue(Manifest.permission.INTERNET in requested())
    }
}
