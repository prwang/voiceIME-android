package dev.local.a11yimeprobe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.InputMethod;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Voice dictation accessibility service (spec section 18). Owns a draggable,
 * two-region floating overlay (talk button + drag handle), the accessibility
 * input method, the per-hold session, and the record -> ASR -> same-editor-gate
 * -> commit flow. The only text output path is the reused Committer
 * (AccessibilityInputConnection.commitText()).
 */
public final class ProbeAccessibilityService extends AccessibilityService {

    private static final int COLOR_IDLE = OverlayStyle.TILE_COLOR;
    private static final int COLOR_RECORDING = 0xCCCC3333;
    private static final int COLOR_TRANSCRIBING = 0xCCCC8800;
    private static final int COLOR_DONE = 0xCC228833;
    private static final int COLOR_FAILED = 0xCCAA2222;
    private static final int COLOR_DISABLED = 0xAA666666;

    private static final long REVERT_MS = 900L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final TranscriptPostprocessor postprocessor = new TranscriptPostprocessor();

    private ProbeInputMethod inputMethod;
    private ProbeConfig config;
    private Committer committer;
    private Recorder recorder;
    private ExecutorService asrExecutor;
    private AsrClient asrClient;

    private WindowManager windowManager;
    private FloatingControlsView container;
    private TextView talkButton;
    private View dragHandle;
    private SessionState pendingSession;
    private boolean dismissedUntilInput;
    private boolean dragging;
    private boolean longPressed;
    private final Runnable showHandleMenu = () -> {
        if (container == null || dragging) return;
        longPressed = true;
        dragHandle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        setMenuVisible(!container.isExpanded());
    };
    private WindowManager.LayoutParams lp;

    private SessionState currentSession;
    private CancellationSignal currentCancel;

    // Visibility inputs. The overlay is shown only when the master toggle is on,
    // the device is unlocked, and an editor currently has input focus (or a
    // hold/transcription is in flight so we never yank the view mid-interaction).
    private KeyguardManager keyguard;
    private BroadcastReceiver screenReceiver;
    private boolean editorFocused;
    private boolean transcribing;

    // Drag state.
    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartX;
    private int dragStartY;

    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    @Override
    public InputMethod onCreateInputMethod() {
        ProbeLog.i("Ime", "A11Y_ON_CREATE_INPUT_METHOD");
        inputMethod = new ProbeInputMethod(this);
        return inputMethod;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        config = new ProbeConfig(this);
        committer = new Committer(this, config);
        recorder = new Recorder(this);
        asrExecutor = Executors.newSingleThreadExecutor();
        asrClient = buildAsrClient();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            setServiceInfo(info);
        }
        ProbeLog.i("Service", "SERVICE_CONNECTED flags=0x"
                + Integer.toHexString(info != null ? info.flags : 0) + " " + config.describe());

        prefsListener = (sp, key) -> main.post(() -> onConfigChanged(key));
        config.prefs().registerOnSharedPreferenceChangeListener(prefsListener);

        keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        registerScreenReceiver();

        applyOverlayVisibility();
    }

    /**
     * Listen for lock/unlock and screen transitions so the overlay is removed
     * over the keyguard and re-evaluated on unlock. SCREEN_OFF also clears the
     * editor-focus flag so it can never go stale across a lock.
     */
    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent != null ? intent.getAction() : null;
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    editorFocused = false;
                }
                ProbeLog.i("Overlay", "SCREEN_EVENT " + action
                        + " locked=" + (keyguard != null && keyguard.isKeyguardLocked()));
                main.post(ProbeAccessibilityService.this::applyOverlayVisibility);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    /** Called from the accessibility input method when editor focus changes. */
    void onEditorFocusChanged(boolean focused) {
        editorFocused = focused;
        if (focused) dismissedUntilInput = false;
        if (container != null) setMenuVisible(false);
        applyOverlayVisibility();
    }

    private AsrClient buildAsrClient() {
        return config.useFakeAsr() ? new FakeAsrClient() : new DashScopeAsrClient();
    }

    private void onConfigChanged(String key) {
        if (ProbeConfig.KEY_OVERLAY_ENABLED.equals(key)) {
            applyOverlayVisibility();
            return;
        }
        if (ProbeConfig.KEY_OVERLAY_X.equals(key) || ProbeConfig.KEY_OVERLAY_Y.equals(key)) {
            return; // position-only change from dragging; nothing to rebuild
        }
        // Any ASR-affecting change: rebuild client and refresh button affordance.
        asrClient = buildAsrClient();
        updateTalkButtonState();
    }

    // ---- visibility predicate ------------------------------------------------

    /**
     * Single source of truth for whether the floating window should be on screen.
     * Idempotent: adds or removes the overlay to match the desired state.
     */
    private void applyOverlayVisibility() {
        if (config == null) return;
        boolean enabled = config.overlayEnabled();
        boolean locked = keyguard != null && keyguard.isKeyguardLocked();
        boolean busy = currentSession != null || transcribing;
        boolean want = enabled && !locked && !dismissedUntilInput && (editorFocused || busy);

        if (want == (container != null)) {
            return; // already in the desired state
        }
        if (want) {
            addOverlay();
        } else {
            String reason = !enabled ? "disabled" : locked ? "locked" : "no_editor";
            ProbeLog.i("Overlay", "OVERLAY_HIDE reason=" + reason);
            removeOverlay();
        }
    }

    // ---- overlay construction ------------------------------------------------

    private void addOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        container = new FloatingControlsView(this, new FloatingControlsView.Actions() {
            @Override public void text(String text) {
                if (canUseKeyboard()) committer.commitText(text,
                        SessionState.capture(inputMethod, "KEYBOARD", "text"));
            }
            @Override public void key(int keyCode, int modifiers) {
                if (canUseKeyboard()) committer.sendKeyboardKey(keyCode, modifiers);
            }
            @Override public void exitTerminal() {
                if (canUseKeyboard()) committer.exitTerminalMode();
            }
            @Override public void hide() { setMenuVisible(false); }
            @Override public void close() {
                handleTalkCancel();
                dismissedUntilInput = true;
                applyOverlayVisibility();
            }
        });
        talkButton = container.getTalkButton();
        talkButton.setOnTouchListener((v, ev) -> {
            onTalkTouch(ev);
            return true;
        });
        dragHandle = container.getDragHandle();
        dragHandle.setOnLongClickListener(v -> {
            showHandleMenu.run();
            return true;
        });
        dragHandle.setOnTouchListener((v, ev) -> {
            onDragTouch(ev);
            return true;
        });

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // NOT_FOCUSABLE: never steal editor focus (spec 7.3).
                // NOT_TOUCH_MODAL: touches outside the overlay reach the app.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        int x = config.getOverlayX();
        int y = config.getOverlayY();
        lp.x = x >= 0 ? x : dp(24);
        lp.y = y >= 0 ? y : dp(120);

        try {
            windowManager.addView(container, lp);
            ProbeLog.i("Overlay", "OVERLAY_ADDED x=" + lp.x + " y=" + lp.y);
        } catch (Exception e) {
            ProbeLog.e("Overlay", "OVERLAY_ADD_FAILED");
        }
        updateTalkButtonState();
    }

    private void removeOverlay() {
        main.removeCallbacks(showHandleMenu);
        if (windowManager != null && container != null) {
            try {
                windowManager.removeView(container);
                ProbeLog.i("Overlay", "OVERLAY_REMOVED");
            } catch (Exception ignored) {
            }
        }
        container = null;
        talkButton = null;
        dragHandle = null;
    }

    // ---- talk (hold-to-dictate) ---------------------------------------------

    private boolean canDictate() {
        return config.useFakeAsr() || config.isConfigured();
    }

    private void onTalkTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handleTalkDown();
                break;
            case MotionEvent.ACTION_UP:
                handleTalkUp();
                break;
            case MotionEvent.ACTION_CANCEL:
                handleTalkCancel();
                break;
            default:
                break;
        }
    }

    private void handleTalkDown() {
        ProbeLog.i("Overlay", "OVERLAY_DOWN");
        if (transcribing || currentSession != null) return;
        if (!canDictate()) {
            setTalkState(TalkState.NEEDS_CONFIG);
            ProbeLog.w("Asr", "NEEDS_CONFIG no api key");
            currentSession = null;
            return;
        }
        currentSession = SessionState.capture(inputMethod, "DICTATION", "voice");
        currentCancel = new CancellationSignal();
        ProbeLog.i("Session", "SESSION_CREATED " + currentSession.describe());
        setTalkState(TalkState.RECORDING);
        recorder.start(currentSession);
    }

    private void handleTalkUp() {
        ProbeLog.i("Overlay", "OVERLAY_UP");
        final SessionState s = currentSession;
        currentSession = null;
        if (s == null) {
            updateTalkButtonState();
            return;
        }
        s.stopElapsedMs = SystemClock.elapsedRealtime();

        Recorder.Result r = recorder.stop();
        if (r != null) {
            ProbeLog.i("Audio", r.describe());
        }
        if (r == null || r.wavPath == null) {
            ProbeLog.w("Asr", "NO_AUDIO session=" + s.sessionId);
            setTalkState(TalkState.FAILED);
            scheduleRevert();
            return;
        }

        // Recorder reuses a single probe-last.wav; move to a per-session file so a
        // subsequent hold cannot overwrite audio that is still being uploaded.
        File wav = new File(r.wavPath);
        File unique = new File(getCacheDir(), "asr-" + s.sessionId + ".wav");
        if (wav.renameTo(unique)) {
            wav = unique;
        }
        final File wavFinal = wav;

        setTalkState(TalkState.TRANSCRIBING);
        transcribing = true;
        pendingSession = s;
        final AsrClient client = asrClient;
        final AsrRequest req = AsrRequest.fromConfig(config);
        final CancellationSignal cancel = currentCancel;

        asrExecutor.execute(() -> {
            long t0 = SystemClock.elapsedRealtime();
            try {
                AsrResult res = client.transcribe(wavFinal, req, cancel);
                long dt = SystemClock.elapsedRealtime() - t0;
                main.post(() -> onTranscriptReady(s, res, dt));
            } catch (Exception e) {
                long dt = SystemClock.elapsedRealtime() - t0;
                main.post(() -> onAsrError(s, e, dt));
            } finally {
                if (wavFinal.exists() && !wavFinal.delete()) {
                    ProbeLog.w("Asr", "WAV_DELETE_FAILED");
                }
            }
        });
    }

    private void handleTalkCancel() {
        ProbeLog.i("Overlay", "OVERLAY_CANCEL");
        if (currentSession != null) {
            currentSession.cancelled = true;
            ProbeLog.i("Session", "SESSION_CANCELLED session=" + currentSession.sessionId);
        }
        if (pendingSession != null) pendingSession.cancelled = true;
        if (currentCancel != null) {
            currentCancel.cancel();
        }
        recorder.cancel();
        currentSession = null;
        transcribing = false;
        updateTalkButtonState();
        applyOverlayVisibility();
    }

    private void onTranscriptReady(SessionState s, AsrResult res, long latencyMs) {
        if (pendingSession == s) {
            transcribing = false;
            pendingSession = null;
        }
        if (s.cancelled) {
            ProbeLog.i("Asr", "ASR_RESULT_DROPPED_CANCELLED session=" + s.sessionId);
            return;
        }
        String text = postprocessor.cleanFinalTranscript(res.text);
        ProbeLog.i("Asr", "ASR_RESULT session=" + s.sessionId + " latency_ms=" + latencyMs
                + " len=" + text.length() + " reqId=" + res.providerRequestId);

        if (text.isEmpty()) {
            setTalkState(TalkState.NO_SPEECH);
            scheduleRevert();
            return;
        }

        InputMethod im = getInputMethod();
        InputMethod.AccessibilityInputConnection ic =
                im != null ? im.getCurrentInputConnection() : null;
        EditorInfo current = inputMethod != null ? inputMethod.getLastEditorInfoForLogOnly() : null;
        long currentGen = inputMethod != null ? inputMethod.getEditorGeneration() : -1L;

        if (im == null || ic == null) {
            ProbeLog.w("Session", "NO_CURRENT_INPUT_CONNECTION session=" + s.sessionId);
            setTalkState(TalkState.FAILED);
            scheduleRevert();
            return;
        }
        if (current == null || !s.matchesCurrent(current, currentGen)) {
            ProbeLog.w("Session", "FOCUS_OR_INPUT_CONNECTION_CHANGED session=" + s.sessionId
                    + " curGen=" + currentGen
                    + " curPkg=" + (current != null ? current.packageName : null));
            setTalkState(TalkState.FAILED);
            scheduleRevert();
            return;
        }

        boolean ok = committer.commitText(text, s);
        setTalkState(ok ? TalkState.DONE : TalkState.FAILED);
        scheduleRevert();
    }

    private void onAsrError(SessionState s, Exception e, long latencyMs) {
        if (s.cancelled) return;
        transcribing = false;
        pendingSession = null;
        // Metadata only: never log the transcript. Exception message may carry an
        // HTTP status code but not user content.
        ProbeLog.e("Asr", "ASR_ERROR session=" + s.sessionId + " latency_ms=" + latencyMs
                + " err=" + e.getClass().getSimpleName() + ":" + e.getMessage());
        setTalkState(TalkState.FAILED);
        scheduleRevert();
    }

    // ---- drag handle ---------------------------------------------------------

    private void onDragTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartRawX = ev.getRawX();
                dragStartRawY = ev.getRawY();
                dragStartX = lp.x;
                dragStartY = lp.y;
                dragging = false;
                longPressed = false;
                main.postDelayed(showHandleMenu, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (longPressed) return;
                float dx = ev.getRawX() - dragStartRawX;
                float dy = ev.getRawY() - dragStartRawY;
                int slop = ViewConfiguration.get(this).getScaledTouchSlop();
                if (!dragging && dx * dx + dy * dy <= slop * slop) return;
                dragging = true;
                main.removeCallbacks(showHandleMenu);
                lp.x = Math.max(0, dragStartX + (int) dx);
                lp.y = Math.max(0, dragStartY + (int) dy);
                windowManager.updateViewLayout(container, lp);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                main.removeCallbacks(showHandleMenu);
                if (dragging) config.setOverlayPosition(lp.x, lp.y);
                dragging = false;
                break;
            default:
                break;
        }
    }

    private void setMenuVisible(boolean visible) {
        android.view.WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        android.graphics.Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
        int width = metrics.getBounds().width() - insets.left - insets.right;
        int height = metrics.getBounds().height() - insets.top - insets.bottom;
        container.setExpanded(visible, height);
        if (!visible) return;
        int panelHeight = Math.min(dp(FloatingControlsView.HEIGHT_DP), height);
        lp.x = Math.max(0, Math.min(lp.x, width - dp(FloatingControlsView.WIDTH_DP)));
        lp.y = Math.max(0, Math.min(lp.y, height - panelHeight));
        windowManager.updateViewLayout(container, lp);
    }

    private boolean canUseKeyboard() {
        return editorFocused && !dismissedUntilInput && currentSession == null && !transcribing
                && (keyguard == null || !keyguard.isKeyguardLocked());
    }

    // ---- visual state --------------------------------------------------------

    private enum TalkState { IDLE, RECORDING, TRANSCRIBING, DONE, FAILED, NO_SPEECH, NEEDS_CONFIG }

    private void setTalkState(TalkState state) {
        if (talkButton == null) {
            return;
        }
        String text;
        String description;
        int color;
        switch (state) {
            case RECORDING: text = "Rec"; description = "Recording; release to transcribe"; color = COLOR_RECORDING; break;
            case TRANSCRIBING: text = "…"; description = "Transcribing"; color = COLOR_TRANSCRIBING; break;
            case DONE: text = "✓"; description = "Dictation inserted"; color = COLOR_DONE; break;
            case FAILED: text = "!"; description = "Dictation failed"; color = COLOR_FAILED; break;
            case NO_SPEECH: text = "—"; description = "No speech detected"; color = COLOR_DISABLED; break;
            case NEEDS_CONFIG: text = "Set"; description = "Set API key in app settings"; color = COLOR_DISABLED; break;
            case IDLE:
            default: text = "Hold"; description = "Hold to talk"; color = COLOR_IDLE; break;
        }
        container.setTalkState(text, description, color);
    }

    private void updateTalkButtonState() {
        setTalkState(canDictate() ? TalkState.IDLE : TalkState.NEEDS_CONFIG);
    }

    private void scheduleRevert() {
        main.postDelayed(() -> {
            updateTalkButtonState();
            // The hold is fully over; the editor may have lost focus meanwhile.
            applyOverlayVisibility();
        }, REVERT_MS);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    // ---- lifecycle -----------------------------------------------------------

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No window-content inspection (canRetrieveWindowContent=false).
    }

    @Override
    public void onInterrupt() {
        ProbeLog.i("Service", "SERVICE_INTERRUPT");
        if (recorder != null) {
            recorder.cancel();
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (prefsListener != null && config != null) {
            config.prefs().unregisterOnSharedPreferenceChangeListener(prefsListener);
        }
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {
            }
            screenReceiver = null;
        }
        removeOverlay();
        if (recorder != null) {
            recorder.cancel();
        }
        if (asrExecutor != null) {
            asrExecutor.shutdownNow();
        }
        return super.onUnbind(intent);
    }
}
