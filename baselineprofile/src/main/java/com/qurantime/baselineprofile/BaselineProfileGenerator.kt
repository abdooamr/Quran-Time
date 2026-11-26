package com.qurantime.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records start-up, the Mushaf reading path (swipes, the jump sheet), reading
 * along with a recitation (follow-along highlight, mini player, swipes while
 * it plays) and the first open of every tab.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.qurantime") {
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
        device.waitForIdle(500)

        // Read along: play this page, turn pages while it plays, then pause.
        device.findObject(By.res("play-page"))?.click()
        device.wait(Until.hasObject(By.res("mini-player")), 10_000)
        Thread.sleep(6_000)
        repeat(4) {
            device.swipe(w / 6, y, w * 5 / 6, y, 12)
            device.waitForIdle(500)
        }
        repeat(4) {
            device.swipe(w * 5 / 6, y, w / 6, y, 12)
            device.waitForIdle(500)
        }
        device.findObject(By.res("mini-play"))?.click()
        device.waitForIdle(500)

        // Every tab once (their first screens load from the network or cache).
        for (tab in listOf("listen", "live", "explore", "more", "mushaf")) {
            device.findObject(By.res("tab-$tab"))?.click()
            device.waitForIdle(1500)
        }
    }
}
