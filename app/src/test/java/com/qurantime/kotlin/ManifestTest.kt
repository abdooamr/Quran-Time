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
    fun `can read the network state (OnlineStatus needs it)`() {
        assertTrue(requested().toString(), Manifest.permission.ACCESS_NETWORK_STATE in requested())
        assertTrue(Manifest.permission.INTERNET in requested())
    }
}
