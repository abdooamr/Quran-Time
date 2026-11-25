package com.qurantime.kotlin.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Records the start-up journey (later tasks add the Mushaf swipe path). */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.qurantime.kotlin") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}
