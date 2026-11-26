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

    // Review: Auto Backup skips an app entirely above 25 MB; the ~80 MB of
    // pages and downloaded audio (both re-downloadable) must not count.
    private fun excludes(xmlId: Int): Set<String> {
        val parser = context.resources.getXml(xmlId)
        val out = mutableSetOf<String>()
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "exclude") {
                out += parser.getAttributeValue(null, "domain") + ":" + parser.getAttributeValue(null, "path")
            }
        }
        return out
    }

    @Test
    fun `backups leave out the pages and downloaded audio`() {
        val wanted = setOf("file:mushaf/", "file:audio/")
        assertTrue(excludes(R.xml.backup_rules).containsAll(wanted))
        assertTrue(excludes(R.xml.data_extraction_rules).containsAll(wanted))
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }
}
