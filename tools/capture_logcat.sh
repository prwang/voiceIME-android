#!/usr/bin/env bash
# Capture probe logs with the fixed tags (spec section 11.3).
# Override adb (e.g. a remote server) via: ADB="adb -H host -P port" ./capture_logcat.sh
set -euo pipefail

ADB="${ADB:-adb}"

# Optional: clear first with CLEAR=1
if [ "${CLEAR:-0}" = "1" ]; then
  $ADB logcat -c
fi

exec $ADB logcat -v time -s \
  A11yImeProbe/Main \
  A11yImeProbe/Service \
  A11yImeProbe/Ime \
  A11yImeProbe/Commit \
  A11yImeProbe/Audio \
  A11yImeProbe/Overlay \
  A11yImeProbe/Session \
  A11yImeProbe/Asr
