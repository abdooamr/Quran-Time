package com.qurantime

import android.content.ComponentCallbacks2
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Review I3: the system's memory callbacks reach the container. */
@RunWith(AndroidJUnit4::class)
class AppTrimMemoryTest {
    @Test
    fun `trim memory when hidden reaches the container`() {
        val app = ApplicationProvider.getApplicationContext<QuranTimeApp>()
        val before = app.container.memoryTrims.value
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(before, app.container.memoryTrims.value)
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(before + 1, app.container.memoryTrims.value)
    }
}
