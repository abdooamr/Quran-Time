package com.qurantime.kotlin.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Records start-up and the Mushaf reading path (swipes, the jump sheet). */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.qurantime.kotlin") {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.res("mushaf-pager")), 15_000)
        val w = device.displayWidth
        val y = device.displayHeight / 2
        repeat(10) {
            device.swipe(w / 6, y, w * 5 / 6, y, 12)
            device.waitForIdle(500)
        }
        repeat(10) {
            device.swipe(w * 5 / 6, y, w / 6, y, 12)
            device.waitForIdle(500)
        }
        device.findObject(By.res("surah-cartouche"))?.click()
        device.waitForIdle(1000)
        device.pressBack()
    }
}
