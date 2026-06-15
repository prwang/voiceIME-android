#!/usr/bin/env bash
# Pull a Termux/Terminus cat-capture file and compare against the MIXED payload
# (spec section 13.6). Documented terminal newline normalization is tolerated:
# the comparison is also retried with CRLF/CR collapsed to LF.
# Override adb via: ADB="adb -H host -P port" ./pull_termux_capture.sh
set -euo pipefail

ADB="${ADB:-adb}"
REMOTE="${1:-/sdcard/Download/a11y_ime_probe.txt}"
LOCAL="${2:-results/a11y_ime_probe.txt}"

mkdir -p "$(dirname "$LOCAL")"
$ADB pull "$REMOTE" "$LOCAL"

# Expected MIXED payload (matches ProbeConfig.TEXT_MIXED).
EXPECTED=$'Hello World\n（中文测试）你好，世界！'

ACTUAL="$(cat "$LOCAL")"
NORM_ACTUAL="$(printf '%s' "$ACTUAL" | tr -d '\r')"

echo "--- expected ---"
printf '%s\n' "$EXPECTED"
echo "--- actual (newline-normalized) ---"
printf '%s\n' "$NORM_ACTUAL"
echo "----------------"

if [ "$NORM_ACTUAL" = "$EXPECTED" ]; then
  echo "Termux capture: PASS (exact after newline normalization)"
else
  echo "Termux capture: REVIEW (does not match MIXED payload exactly)" >&2
  exit 1
fi
