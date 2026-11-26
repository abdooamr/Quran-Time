package com.qurantime.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while turning Mushaf pages: 10 pages forward and 10 back, with
 * the pages already on disk (the first iteration downloads them; later ones
 * measure the reader as people use it).
 */
@RunWith(AndroidJUnit4::class)
class SwipeBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun swipe20() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 6,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res("mushaf-pager")), 10_000)
            device.waitForIdle()
        },
    ) {
        swipePages(forward = true, count = 10)
        swipePages(forward = false, count = 10)
    }
}

/** The next page is to the left: a finger moving right turns forward. */
fun MacrobenchmarkScope.swipePages(forward: Boolean, count: Int) {
    val w = device.displayWidth
    val y = device.displayHeight / 2
    repeat(count) {
        if (forward) device.swipe(w / 6, y, w * 5 / 6, y, 12) else device.swipe(w * 5 / 6, y, w / 6, y, 12)
        device.waitForIdle(400)
    }
}
