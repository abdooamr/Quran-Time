# Page mask encoding (Task 10)

Measured with `MaskCodecDeviceTest` (debug build) at a 1080 px page width, on the API 35 x86_64 emulator, because the A50 was not connected. Decode times are the median of 20 runs.

| codec | page 3 bytes | page 300 bytes | decode median (ms) | encode (ms) |
|---|---|---|---|---|
| nibble-packed deflate (`qtm`) | 125,800 | 128,779 | 12.9 / 10.6 | 277 / 270 |
| **raw 16-level deflate (`qtr`)** | 129,172 | 131,841 | **8.0 / 5.2** | 205 / 226 |
| lossless WebP | 146,460 | 147,248 | 11.9 / 13.2 | 184 / 119 |

SVG render at 1080 px: 364 ms (page 3, first run) and 101 ms (page 300).

**Chosen: `qtr`.** About 130 KB a page, so all 604 pages take about 78 MB (target ≤ 100 MB). The original SVGs take about 180 MB. Decoding is one native zlib call into the bitmap, with no per-pixel Kotlin loop.

## On the Galaxy A50 (SM-A505FN, Android 12), debug build

| page | bytes | decode median (ms) | encode (ms) | SVG render (ms) |
|---|---|---|---|---|
| 3 | 129,008 | 7.3 | 527 | 348 |
| 300 | 131,681 | 7.3 | 490 | 187 |

Decode is within the 8 ms target. Making a page costs about 0.7–0.9 s once (render plus encode, off the main thread); prefetching the two pages on each side hides it while reading.
