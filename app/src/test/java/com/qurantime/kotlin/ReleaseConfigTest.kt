package com.qurantime.kotlin

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.kotlin.feature.audio.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks on the merged manifest (with every library's additions), as the store sees it. */
@RunWith(AndroidJUnit4::class)
class ReleaseConfigTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pm = context.packageManager

    private val allowed = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
        "android.permission.WAKE_LOCK",
        "android.permission.POST_NOTIFICATIONS",
        // WorkManager: re-schedules unfinished downloads after a restart.
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    )

    @Test
    fun `manifest has only the permissions the app needs`() {
        val requested = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertTrue("INTERNET missing", "android.permission.INTERNET" in requested)
        assertTrue("reads storage/media: $requested", requested.none { it.startsWith("android.permission.READ_") })
        assertTrue("asks for location: $requested", requested.none { "LOCATION" in it })
        assertEquals("unexpected permissions", emptySet<String>(), requested - allowed)
    }

    @Test
    fun `the playback service is a media playback foreground service`() {
        val info = pm.getServiceInfo(ComponentName(context, PlaybackService::class.java), 0)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    @Test
    fun `the app has a launcher icon`() {
        assertNotEquals(0, context.applicationInfo.icon)
        assertNotEquals(0, context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName))
    }
}
