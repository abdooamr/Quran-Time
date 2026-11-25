#!/usr/bin/env bash
# Measures one installed app the same way for the Flutter and Kotlin builds.
#
# Usage: tools/perf.sh <package> <activity> <apk> <out.md> [serial]
#        SNAPSHOT_ONLY=1 tools/perf.sh <package> <activity> <apk> <out.md> [serial]
#
#   fully_drawn_ms  median of 10 cold starts ("Fully drawn" in logcat; falls
#                   back to `am start -W` TotalTime when the app doesn't report)
#   jank_pct        dumpsys gfxinfo "Janky frames" of the running app
#   pss_mb          dumpsys meminfo TOTAL PSS of the running app
#   apk_mb          size of the given APK
#
# SNAPSHOT_ONLY=1 skips the starts and reads jank/pss of the app as it runs
# now (use it after a swipe journey, or with audio playing).
set -euo pipefail
PKG=$1; ACT=$2; APK=$3; OUT=$4; SERIAL=${5:-}
ADB=(adb); [ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

model=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')
now=$(date '+%Y-%m-%d %H:%M')

snapshot() {
  jank=$("${ADB[@]}" shell dumpsys gfxinfo "$PKG" | tr -d '\r' | grep -m1 "Janky frames" | sed -E 's/.*\(([0-9.]+)%\).*/\1/' || true)
  pss=$("${ADB[@]}" shell dumpsys meminfo "$PKG" | tr -d '\r' | grep -m1 -E "TOTAL PSS:|^ +TOTAL " | awk '{for(i=1;i<=NF;i++) if($i ~ /^[0-9]+$/){print int($i/1024); exit}}' || true)
}

if [ "${SNAPSHOT_ONLY:-}" = "1" ]; then
  snapshot
  {
    echo "## $PKG snapshot on $model — $now"
    echo ""
    echo "| metric | value |"
    echo "|---|---|"
    echo "| jank_pct | ${jank:-n/a} |"
    echo "| pss_mb | ${pss:-n/a} |"
    echo ""
  } | tee -a "$OUT"
  exit 0
fi

times=()
for i in $(seq 1 10); do
  "${ADB[@]}" shell am force-stop "$PKG"
  sleep 1
  "${ADB[@]}" logcat -c
  total=$("${ADB[@]}" shell am start -W -n "$PKG/$ACT" | tr -d '\r' | awk '/TotalTime/ {print $2}')
  sleep 3
  fd=$("${ADB[@]}" logcat -d | tr -d '\r' | grep -m1 "Fully drawn $PKG" | sed -E 's/.*\+(([0-9]+)s)?([0-9]+)ms.*/\2 \3/' || true)
  if [ -n "$fd" ]; then
    set -- $fd
    if [ $# -eq 2 ]; then times+=($(( $1 * 1000 + $2 ))); else times+=("$1"); fi
  else
    times+=("$total")
  fi
done

# The app stays open after the last start: read it once it has settled.
sleep 2
snapshot
median=$(printf '%s\n' "${times[@]}" | sort -n | awk '{a[NR]=$1} END {print (NR%2 ? a[(NR+1)/2] : int((a[NR/2]+a[NR/2+1])/2))}')
apk=$(awk "BEGIN {printf \"%.2f\", $(stat -c %s "$APK")/1048576}")

{
  echo "## $PKG on $model — $now"
  echo ""
  echo "| metric | value |"
  echo "|---|---|"
  echo "| fully_drawn_ms (median of 10) | $median |"
  echo "| starts | ${times[*]} |"
  echo "| jank_pct (since last start) | ${jank:-n/a} |"
  echo "| pss_mb | ${pss:-n/a} |"
  echo "| apk_mb | $apk |"
  echo ""
} | tee -a "$OUT"
