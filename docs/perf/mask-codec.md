# Page mask encoding (Task 10)

Measured with `MaskCodecDeviceTest` (debug build) at a 1080 px page width, on the API 35 x86_64 emulator, because the A50 was not connected. Decode times are the median of 20 runs.

| codec | page 3 bytes | page 300 bytes | decode median (ms) | encode (ms) |
|---|---|---|---|---|
| nibble-packed deflate (`qtm`) | 125,800 | 128,779 | 12.9 / 10.6 | 277 / 270 |
| **raw 16-level deflate (`qtr`)** | 129,172 | 131,841 | **8.0 / 5.2** | 205 / 226 |
| lossless WebP | 146,460 | 147,248 | 11.9 / 13.2 | 184 / 119 |

SVG render at 1080 px: 364 ms (page 3, first run) and 101 ms (page 300).

**Chosen: `qtr`.** About 130 KB a page, so all 604 pages take about 78 MB (target ≤ 100 MB). The original SVGs take about 180 MB. Decoding is one native zlib call into the bitmap, with no per-pixel Kotlin loop.
