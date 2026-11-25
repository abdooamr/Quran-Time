package com.qurantime.kotlin

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qurantime.kotlin.feature.mushaf.image.RawDeflateMaskCodec
import com.qurantime.kotlin.feature.mushaf.image.SvgRasterizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the page-mask encoding on a real device: file size, decode
 * time (median of 20) and SVG render time. Read the results with
 * `adb logcat -d | grep codec=`.
 */
@RunWith(AndroidJUnit4::class)
class MaskCodecDeviceTest {
    @Test
    fun measure() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val width = 1080 // the A50's page area rounds up to this bucket
        val model = android.os.Build.MODEL
        for (page in listOf("page-003.svg", "page-300.svg")) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val mask = assets.open(page).use { SvgRasterizer.render(it, Size(345f, 550f), width) }
            val renderMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
            for (codec in listOf(RawDeflateMaskCodec)) {
                val out = ByteArrayOutputStream()
                val e0 = SystemClock.elapsedRealtimeNanos()
                codec.encode(mask, out)
                val encodeMs = (SystemClock.elapsedRealtimeNanos() - e0) / 1e6
                val bytes = out.toByteArray()
                val times = (1..20).map {
                    val d0 = SystemClock.elapsedRealtimeNanos()
                    codec.decode(ByteArrayInputStream(bytes)).recycle()
                    (SystemClock.elapsedRealtimeNanos() - d0) / 1e6
                }.sorted()
                Log.i(
                    "QTPerf",
                    "codec=${codec.ext} page=$page device=$model bytes=${bytes.size} decodeMedianMs=%.1f encodeMs=%.0f renderMs=%.0f"
                        .format(times[10], encodeMs, renderMs),
                )
            }
        }
    }
}
