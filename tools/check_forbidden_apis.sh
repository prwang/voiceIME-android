#!/usr/bin/env bash
# Static source scan: fail if executable code uses a forbidden output path
# (spec section 2.2 / 17.1). Comment-only lines are ignored so the forbidden
# names may still appear in documentation, per the spec exception.
set -euo pipefail

ROOT="${1:-app/src/main/java}"
PATTERN='ACTION_SET_TEXT|ACTION_PASTE|ClipboardManager|sendKeyEvent\(|Instrumentation|injectInputEvent|InputMethodService|switchToInputMethod|performEditorAction\(|performContextMenuAction\('

# Strip // line comments and /* ... */ block comments before scanning so that
# explanatory comments do not trigger false positives.
tmp_hits="$(
  find "$ROOT" -name '*.java' -print0 \
    | while IFS= read -r -d '' f; do
        # Remove block comments, then line comments, keep file:line via awk.
        awk '
          BEGIN { inblock=0 }
          {
            line=$0
            # crude block-comment stripping
            while (inblock && match(line, /\*\//)) { line=substr(line, RSTART+2); inblock=0 }
            if (inblock) next
            gsub(/\/\*([^*]|\*[^\/])*\*\//, "", line)
            if (match(line, /\/\*/)) { line=substr(line, 1, RSTART-1); inblock=1 }
            sub(/\/\/.*/, "", line)
            if (line ~ /[^[:space:]]/) printf "%s:%d:%s\n", FILENAME, FNR, line
          }
        ' "$f"
      done \
    | grep -E "$PATTERN" || true
)"

if [ -n "$tmp_hits" ]; then
  echo "Forbidden API reference found in executable code:" >&2
  echo "$tmp_hits" >&2
  exit 1
fi

echo "Forbidden API scan: PASS ($ROOT)"
