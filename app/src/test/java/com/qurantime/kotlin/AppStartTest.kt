package com.qurantime.kotlin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppStartTest {
    @Test
    fun `app container builds without touching disk`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val before = context.filesDir.listFiles()?.toList().orEmpty()
        AppContainer(context)
        val after = context.filesDir.listFiles()?.toList().orEmpty()
        assertTrue("container wrote $after", after == before)
    }
}
