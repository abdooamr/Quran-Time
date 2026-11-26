package com.qurantime.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.core.ui.AsyncView
import com.qurantime.core.ui.OfflineBanner
import com.qurantime.support.FakeOnlineStatus
import androidx.compose.material3.Text
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(AndroidJUnit4::class)
class OnlineStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `reports offline when the validated network is lost`() {
        val cm = ApplicationProvider.getApplicationContext<Context>().getSystemService(ConnectivityManager::class.java)
        val status = ConnectivityOnlineStatus(cm)
        val callback = shadowOf(cm).networkCallbacks.single()
        val network = cm.activeNetwork!!

        callback.onLost(network)
        assertFalse(status.online.value)

        val caps = ShadowNetworkCapabilities.newInstance().also {
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        callback.onCapabilitiesChanged(network, caps)
        assertTrue(status.online.value)
    }

    @Test
    fun `offline banner appears and goes away`() {
        val online = FakeOnlineStatus()
        compose.setContent { OfflineBanner(online) }
        assertTrue(compose.onAllNodesWithText("You're offline", substring = true).fetchSemanticsNodes().isEmpty())
        online.set(false)
        compose.waitForIdle()
        compose.onNodeWithText("You're offline", substring = true).assertExists()
        online.set(true)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("You're offline", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `async view shows an error with retry, then the data`() {
        var calls = 0
        compose.setContent {
            AsyncView(load = { if (calls++ == 0) error("offline") else "loaded" }) { Text(it) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Couldn't load this", substring = true).assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("loaded").assertExists()
        assertEquals(2, calls)
    }
}
