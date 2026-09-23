# Local Android UI preview

Render the actual production view classes locally using Robolectric native
Android graphics. No phone or emulator is required:

```sh
source /etc/profile.d/android.sh
./gradlew -p tools/keyboard-preview testDebugUnitTest
```

Images are written to `results/keyboard-preview/local-{collapsed,expanded,
large-font,short-window}.png`. Checks cover panel dimensions, requested key
order, unchanged Hold/handle positions, no text wrapping/clipping, larger fonts,
and vertical scrolling in a short window. Robolectric is a preview/test-only
dependency; it does not enter the production APK. Rendering can differ slightly
from an OEM device; key handling in Termius still needs app-level confirmation.

The optional standalone preview APK remains available via `assembleDebug`.
Its activity is `dev.local.a11yimeprobe.preview/dev.local.a11yimeprobe.KeyboardPreviewActivity`.
Use `--ez editor true` for a disposable editor. It logs received key codes and
modifier flags under `KeyboardPreview`. ET should deliver Ctrl+backslash
(keycode 73) and Ctrl+N (keycode 42), each with down/up events.
