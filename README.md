# VoiceImeMobile (probe build)

Hold-to-talk **voice dictation** for Android that types into any focused text
field — including terminals — **without** being a keyboard. Text is produced
solely through the accessibility input connection
(`AccessibilityInputConnection.commitText()`); speech is transcribed by a cloud
ASR endpoint (Alibaba DashScope, OpenAI-compatible `qwen3-asr-flash`).

Package id `dev.local.a11yimeprobe` and the `Probe*` class names are retained
from the Phase-0 risk-validation probe (renaming would force re-enabling the
accessibility service on the test device). See `../spec.md` for the full spec.

## Architecture constraints (hard rules)

- **Only output path** is `Committer` → `AccessibilityInputConnection.commitText()`.
  No `ACTION_SET_TEXT`/`ACTION_PASTE`, clipboard, key-event injection,
  `InputMethodService`, `performEditorAction`, etc. `tools/check_forbidden_apis.sh`
  fails the build if any of these appear in executable code.
- The overlay window is `TYPE_ACCESSIBILITY_OVERLAY` with
  `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL` — it never steals editor focus.
- **Java + platform SDK only** — no Kotlin, AndroidX, OkHttp/Retrofit/Gson, or the
  DashScope SDK. Networking is `HttpURLConnection` + `org.json` + `android.util.Base64`.
- APK stays **< 1 MB** (`tools/check_apk_size.sh`). minSdk 33, target/compileSdk 35.

## Toolchain (configured in this container)

- JDK 21 (Android Studio bundled JBR): `/work/android-studio/jbr`
- Android SDK: `/work/android-sdk` (platform-tools, platforms;android-35, build-tools;35.0.0)
- Gradle 8.10.2 (wrapper pinned), Android Gradle Plugin 8.7.2
- Env exported by `/etc/profile.d/android.sh`. `local.properties` (`sdk.dir`) is
  machine-specific and git-ignored.

## Build & gates

```bash
source /etc/profile.d/android.sh
cd /work/probe
./gradlew :app:assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk

bash tools/check_forbidden_apis.sh app/src/main/java
bash tools/check_apk_size.sh app/build/outputs/apk/debug/app-debug.apk
```

## Install & enable

```bash
export ADB="adb -H <host> -P <port>"      # external adb server; or: adb connect <host>:<port>
$ADB install -r -g app/build/outputs/apk/debug/app-debug.apk
```

Open **VoiceImeMobile**, tap **Request RECORD_AUDIO**, then enable the service:
Settings → Accessibility → VoiceImeMobile → Enable (on some OEMs first allow
restricted settings for the app).

## Configuration

Set in MainActivity (persisted to `SharedPreferences`; the service reacts live in
the same process):

| Field | Default | Notes |
|---|---|---|
| API key | — | DashScope key; stored on-device only, never logged |
| Base URL | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Singapore; China = `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| Model | `qwen3-asr-flash` | |
| Language hint | auto | blank = auto-detect |
| enable_itn | off | inverse text normalization |
| Use fake ASR | off | offline canned transcript, for testing without a key/network |
| Show floating button | off | master visibility toggle |

Optional adb automation (mirrors the UI):

```bash
$ADB shell am start -n dev.local.a11yimeprobe/.MainActivity \
  --es asr_api_key "sk-..." \
  --es asr_base_url "https://dashscope-intl.aliyuncs.com/compatible-mode/v1" \
  --ez overlay_enabled true
# offline test toggle:
$ADB shell am start -n dev.local.a11yimeprobe/.MainActivity --ez use_fake_asr true --ez overlay_enabled true
```

## Floating button behavior

Draggable, **two-region** window so hold-to-talk and hold-to-drag never conflict:

- `🎤` **talk region** — press-and-hold to record, release to transcribe & commit.
- `⋮⋮` **drag handle** — reposition; position persists.

Visibility is a single predicate — the button is shown only when **all** hold:

```
overlayEnabled  &&  device unlocked  &&  (an editor has input focus  ||  a hold is in flight)
```

Input focus comes from the accessibility input method's `onStartInput` /
`onFinishInput` callbacks (no window-content scraping). The lock-screen gate uses
`KeyguardManager` plus `SCREEN_OFF/ON` and `USER_PRESENT`. With no API key (and
fake ASR off) the button shows a disabled `⚙ Set API key` state and does nothing.

## Cloud ASR (low-dependency, no SDK)

`DashScopeAsrClient` POSTs `<base_url>/chat/completions` with the WAV inlined as a
base64 `data:audio/wav;base64,...` data URI (no multipart / file hosting), reads
`choices[0].message.content`. `FakeAsrClient` is the offline path; both implement
`AsrClient`. Logging under `A11yImeProbe/Asr` is **metadata only** (status,
latency, length) — the transcript is never logged, and the mic runs only during a
hold.

## Components

| File | Role |
|---|---|
| `MainActivity.java` | settings UI (ASR config, toggles), adb extras |
| `ProbeAccessibilityService.java` | overlay, visibility predicate, hold session, record→ASR→gate→commit |
| `ProbeInputMethod.java` | accessibility IME callbacks, editor fingerprint + generation, focus signal |
| `Committer.java` | the only output path: `AccessibilityInputConnection.commitText()` |
| `Recorder.java` / `WavWriter.java` | hold-to-record PCM 16 kHz mono → WAV |
| `SessionState.java` | per-hold editor fingerprint + same-editor gate |
| `AsrClient.java` / `AsrRequest.java` / `AsrResult.java` | ASR abstraction + DTOs |
| `DashScopeAsrClient.java` / `FakeAsrClient.java` | real cloud client / offline stub |
| `TranscriptPostprocessor.java` | trim + strip trailing newline (terminals don't auto-submit) |
| `ProbeConfig.java` / `ProbeLog.java` | SharedPreferences config, fixed-tag logging |

## Testing

`test_directive.md` is the manual test plan (Tests A–J): offline fake flow,
draggable two-region, needs-config, show/hide, real DashScope, focus-race drop,
terminal target, error handling, auto-hide on no input focus, and never-on-lock-screen.
Run results live under `results/` (only the markdown write-ups are tracked; raw
logs and audio recordings are git-ignored).
