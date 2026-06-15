#!/usr/bin/env bash
# Verify an APK is under the 1 MB hard target (spec section 4.2).
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
LIMIT="${2:-1048576}"   # 1 MB

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK" >&2
  exit 2
fi

SIZE="$(stat -c%s "$APK")"
printf 'APK: %s\nsize: %d bytes (%.1f KB)\nlimit: %d bytes\n' \
  "$APK" "$SIZE" "$(echo "scale=1; $SIZE/1024" | bc 2>/dev/null || echo 0)" "$LIMIT"

if [ "$SIZE" -lt "$LIMIT" ]; then
  echo "APK size: PASS"
else
  echo "APK size: FAIL (>= $LIMIT bytes)" >&2
  exit 1
fi
