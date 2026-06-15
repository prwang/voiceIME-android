# VoiceImeMobile — Manual Test Directive (P1/P2 build)

Build under test: `app/build/outputs/apk/debug/app-debug.apk` (package `dev.local.a11yimeprobe`).
Covers: draggable two-region overlay, MainActivity DashScope config, disabled/needs-config
state, Fake ASR (offline), and real DashScope cloud ASR.

All adb commands assume the remote server. Set once:
```bash
export ADB="adb -H <host> -P <port>"      # set to the provided adb server (or: adb connect <host>:<port>)
```

---

## 0. Setup (once per build)

```bash
# 1. Install (grants runtime perms)
$ADB install -r -g app/build/outputs/apk/debug/app-debug.apk

# 2. Confirm accessibility service still enabled; if not, re-enable via adb:
$ADB shell settings get secure enabled_accessibility_services
$ADB shell settings put secure enabled_accessibility_services \
    dev.local.a11yimeprobe/dev.local.a11yimeprobe.ProbeAccessibilityService
$ADB shell settings put secure accessibility_enabled 1

# 3. Start log capture to disk (leave running in its own terminal)
$ADB logcat -c
$ADB logcat -v time -s \
  A11yImeProbe/Main A11yImeProbe/Service A11yImeProbe/Ime A11yImeProbe/Commit \
  A11yImeProbe/Audio A11yImeProbe/Overlay A11yImeProbe/Session A11yImeProbe/Asr \
  | tee results/logs/p1-session.log
```

In MainActivity, tap **Request RECORD_AUDIO** once and allow (if not already granted).

**Visibility model (new in this build):** even with the master toggle ON, the floating button
is shown only when (a) the device is **unlocked** AND (b) an **editor field has input focus**
(driven by the accessibility input method's `onStartInput`/`onFinishInput` — no window-content
scraping). It hides on the lock screen and whenever no text field is focused, and stays up
through an in-progress hold/transcription. So in tests below, "tap into a field" is what makes
the button appear.

**Floating button color/text legend (talk region):**
`🎤 Hold to talk`=idle · `● Recording`=recording · `… Transcribing`=uploading ·
`✓ Done`=committed · `✗ Failed`=error/gate-drop · `… no speech`=empty result ·
`⚙ Set API key`=not configured.

---

## TEST A — Offline Fake ASR (no network, validates full flow)

Goal: prove record → transcribe → same-editor gate → commit end-to-end without a key.

Steps:
1. Open A11yImeProbe. Check **Use fake ASR (offline test)**. Tap **Save config**.
2. Check **Show floating dictation button**. → floating `🎤 Hold to talk` appears.
3. Open any editable field (browser field, Notes). Tap into it (caret blinks).
4. Press & hold `🎤 Hold to talk` (turns `● Recording`), wait ~1 s, release.
5. Button shows `… Transcribing` briefly, then `✓ Done`.

Pass:
- Field receives: `[fake cloud transcript] 你好，世界！`
- Logs in order:
  ```
  Overlay: OVERLAY_DOWN
  Session: SESSION_CREATED session=N mode=DICTATION ...
  Audio:   AUDIO_START ...
  Overlay: OVERLAY_UP
  Audio:   AUDIO_RESULT duration_ms=... rms=...
  Asr:     ASR_RESULT session=N latency_ms=~300 len=18 reqId=fake-0
  Commit:  COMMIT_TEXT_CALLED session=N len=18
  Ime:     IME_UPDATE_SELECTION ...   (normal editors; terminals may omit)
  ```

---

## TEST B — Draggable two-region overlay (the hold-to-talk vs hold-to-drag fix)

Goal: dragging never triggers dictation; talking never moves the window; position persists.

Steps:
1. Press & hold the **`⋮⋮`** handle and drag the window to a new spot (e.g. off the keyboard area). Release.
2. Verify the window moved and **no** recording happened (no `OVERLAY_DOWN`/`AUDIO_START`).
3. Press & hold the **`🎤`** region → it records (does **not** move).
4. Force-stop and reopen, or toggle the overlay off/on; verify it reappears at the dragged position.

Pass:
- Drag emits only: `Overlay: OVERLAY_MOVED x=.. y=..` (no session/audio logs).
- Talk emits the normal dictation logs from Test A.
- Reposition survives overlay hide/show (reads `overlay_x/overlay_y`).

---

## TEST C — Disabled / needs-config state

Goal: with no key and fake OFF, the button is inert and points the user to config.

Steps:
1. In MainActivity, **uncheck Use fake ASR**, clear the **API key** field, tap **Save config**.
2. The floating button should read **`⚙ Set API key`**.
3. Tap into a field, press & release the button.

Pass:
- Button stays `⚙ Set API key`; **no** recording, **no** commit.
- Log: `Asr: NEEDS_CONFIG no api key` (with `OVERLAY_DOWN`, but no `AUDIO_START`/`SESSION_CREATED`).

---

## TEST D — Master show/hide toggle

Steps:
1. Uncheck **Show floating dictation button** → window disappears (`Overlay: OVERLAY_REMOVED`).
2. Re-check it → window reappears (`Overlay: OVERLAY_ADDED x=.. y=..`).

Pass: window visibility tracks the toggle with no restart needed.

---

## TEST E — Real DashScope cloud ASR  ⭐ (the P2 goal)

Goal: real transcription commits through the same Committer.

Steps:
1. In MainActivity: **uncheck Use fake ASR**; paste the **DashScope API key**; confirm
   **Base URL** = `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` (or China endpoint);
   **Model** = `qwen3-asr-flash`. Optionally set **Language hint** (`zh` / `en`) else leave blank.
   Tap **Save config**. Status should read `Dictation: ready`.
2. Ensure **Show floating dictation button** is on.
3. Tap into a browser field. Hold `🎤`, speak a sentence (try Chinese, then English), release.
4. Wait for `… Transcribing` → `✓ Done`.

Pass:
- Spoken text appears in the field.
- Logs: `Asr: ASR_RESULT session=N latency_ms=<network> len=<n> reqId=<id>` then
  `Commit: COMMIT_TEXT_CALLED`. **No transcript text is logged** (privacy).
- WAV is deleted after (no leftover `cache/asr-*.wav`).

Privacy check: confirm no `Asr` line prints the recognized sentence, and the API key never
appears in logs.

---

## TEST F — Focus-race drop with real latency

Goal: a transcript must never land in the wrong editor after network delay.

Steps:
1. Real ASR configured (Test E). Tap into field #1. Hold `🎤`, speak, release.
2. **During** `… Transcribing`, tap a different field or app.

Pass:
- No text committed anywhere; button shows `✗ Failed`.
- Log: `Session: FOCUS_OR_INPUT_CONNECTION_CHANGED session=N curGen=.. curPkg=..`
  (or `NO_CURRENT_INPUT_CONNECTION` if the editor was dismissed).

---

## TEST G — Terminal target with real ASR (architecture-critical)

Steps:
1. Open Termius (or Termux). Tap the terminal input line.
2. Hold `🎤`, speak a short command/phrase, release.

Pass:
- Text reaches the terminal input via `commitText` (visually confirmed).
- `Commit: COMMIT_TEXT_CALLED` present; `IME_UPDATE_SELECTION` may be absent (expected for terminals).
- Transcript should **not** auto-submit (postprocessor strips trailing newline).

---

## TEST H — Error handling (bad key / network)

Steps:
1. Set an obviously wrong API key, Save. (Or enable airplane mode.)
2. Hold `🎤`, speak, release.

Pass:
- Button shows `✗ Failed`; nothing committed.
- Log: `Asr: ASR_ERROR session=N latency_ms=.. err=IOException:ASR_HTTP_401 ...`
  (or timeout / UnknownHost). Error metadata only — no transcript.

---

## TEST I — Auto-hide when no input focus (legitimate focus detection)

Goal: the button appears only when there is an editor to dictate into.

Steps:
1. Master toggle **on**. Go to the home screen / app launcher (no text field focused).
2. Observe: **no** floating button.
3. Open any app and tap into a text field (caret blinks).
4. Observe: the button appears. Tap elsewhere to dismiss the keyboard / blur the field.
5. Observe: the button disappears.

Pass:
- Button visibility tracks editor focus.
- Logs: `Ime: IME_START_INPUT ...` immediately followed by `Overlay: OVERLAY_ADDED ...`;
  on blur `Ime: IME_FINISH_INPUT ...` then `Overlay: OVERLAY_HIDE reason=no_editor`.

---

## TEST J — Never shown on the lock screen (hard gate)

Goal: the overlay is unconditionally removed over the keyguard.

Steps:
1. Tap into a text field so the button is showing.
2. Lock the device (power button).
3. Wake the screen to the **lock screen** (do not unlock).
4. Observe: **no** floating button anywhere on the keyguard.
5. Unlock and return to a focused field.

Pass:
- Nothing renders over the lock screen.
- Logs: on lock `Overlay: SCREEN_EVENT android.intent.action.SCREEN_OFF locked=true` then
  `Overlay: OVERLAY_HIDE reason=locked`; on unlock `SCREEN_EVENT ...USER_PRESENT locked=false`
  and the button returns only once a field is focused again.
- Even if `isKeyguardLocked()` briefly lags on wake, the button must not be interactable on
  the keyguard.

---

## Recording results

For each test note PASS/FAIL + the key log lines in a dated file under `results/`
(use `results/RESULT_TEMPLATE.md` as the base). Update `results/ARCHITECTURE_GATE_SUMMARY.md`
with the P1/P2 outcome (real ASR commit, focus-race drop, terminal).

### Quick adb config helpers (optional, instead of tapping the UI)
```bash
# set key + endpoint + enable overlay via intent extras
$ADB shell am start -n dev.local.a11yimeprobe/.MainActivity \
  --es asr_api_key "sk-xxxx" \
  --es asr_base_url "https://dashscope-intl.aliyuncs.com/compatible-mode/v1" \
  --ez overlay_enabled true
# offline fake toggle
$ADB shell am start -n dev.local.a11yimeprobe/.MainActivity --ez use_fake_asr true --ez overlay_enabled true
```
