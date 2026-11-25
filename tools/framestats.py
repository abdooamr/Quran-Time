"""Frame jank from `dumpsys gfxinfo <pkg> framestats` dumps (one or more files).

Frames are merged by IntendedVsync across dumps (gfxinfo keeps only the last
120). A frame is janky when it completes after its deadline. Frames that never
completed (abandoned, nothing to draw) are reported separately: gfxinfo's own
"Janky frames" counts them, and adb-injected touches produce many of them.
"""
import sys

rows = {}
for path in sys.argv[1:]:
    hdr = None
    for l in open(path).read().splitlines():
        if l.startswith('Flags,'):
            hdr = l.rstrip(',').split(',')
            continue
        if hdr and l and l[0].isdigit():
            v = l.rstrip(',').split(',')
            try:
                r = dict(zip(hdr, map(int, v[:len(hdr)])))
            except ValueError:
                continue
            rows[r['IntendedVsync']] = r

frames = list(rows.values())
done = [r for r in frames if r['Flags'] == 0 and 0 < r['FrameCompleted'] < 4e18]
d = sorted((r['FrameCompleted'] - r['IntendedVsync']) / 1e6 for r in done)
missed = [r for r in done if r['FrameCompleted'] > r['FrameDeadline']]
pct = lambda p: round(d[min(len(d) - 1, int(len(d) * p / 100))], 1) if d else 0
print(f"| frames (completed / unfinished) | {len(done)} / {len(frames) - len(done)} |")
print(f"| missed deadline | {len(missed)} ({100 * len(missed) / max(1, len(done)):.2f}%) |")
print(f"| frame time p50 / p90 / p95 / p99 (ms) | {pct(50)} / {pct(90)} / {pct(95)} / {pct(99)} |")
