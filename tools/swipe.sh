#!/usr/bin/env bash
# Turns Mushaf pages with real touch input and reports frame jank.
# The next page is to the left, so a finger moving right turns forward.
#
# Usage: tools/swipe.sh <package> <out.md> [serial] [count]
#   A warm-up pass puts the pages on disk; then `count` forward swipes and
#   `count` back are measured. framestats are sampled every 3 swipes (gfxinfo
#   keeps only 120 frames) and merged by tools/framestats.py.
set -euo pipefail
PKG=$1; OUT=$2; SERIAL=${3:-}; COUNT=${4:-10}
ADB=(adb); [ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")
DIR=$(mktemp -d)
HERE=$(cd "$(dirname "$0")" && pwd)

size=$("${ADB[@]}" shell wm size | tr -d '\r' | awk -F'[ x]' '/Physical/ {print $3" "$4}')
W=${size% *}; H=${size#* }
Y=$((H / 2)); L=$((W / 6)); R=$((W * 5 / 6))
n=0
swipe() {
  if [ "$1" = fwd ]; then "${ADB[@]}" shell input swipe "$L" "$Y" "$R" "$Y" 120; else "${ADB[@]}" shell input swipe "$R" "$Y" "$L" "$Y" 120; fi
  sleep 0.6
  n=$((n + 1))
  if [ "${MEASURE:-0}" = 1 ] && [ $((n % 3)) = 0 ]; then
    "${ADB[@]}" shell dumpsys gfxinfo "$PKG" framestats | tr -d '\r' > "$DIR/fs-$n.txt"
  fi
}
pass() {
  for i in $(seq 1 "$COUNT"); do swipe fwd; done
  for i in $(seq 1 "$COUNT"); do swipe back; done
}

pass # warm-up: download and convert the pages once
sleep 2
"${ADB[@]}" shell dumpsys gfxinfo "$PKG" reset >/dev/null
MEASURE=1; n=0
pass
"${ADB[@]}" shell dumpsys gfxinfo "$PKG" framestats | tr -d '\r' > "$DIR/fs-end.txt"
model=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')
{
  echo "## $PKG page swipes on $model — $(date '+%Y-%m-%d %H:%M') ($((COUNT * 2)) swipes)"
  echo ""
  echo "| metric | value |"
  echo "|---|---|"
  python "$HERE/framestats.py" "$DIR"/fs-*.txt
  echo ""
} | tee -a "$OUT"
