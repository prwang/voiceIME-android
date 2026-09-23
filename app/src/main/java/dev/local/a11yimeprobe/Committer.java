package dev.local.a11yimeprobe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.InputMethod;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.InputDevice;
import android.os.SystemClock;

/**
 * The only allowed text-output path (spec section 2.1 / 6.4). Inserts text via
 * AccessibilityInputConnection.commitText(). It never uses node mutation,
 * clipboard or editor actions. Explicit mini-keyboard taps may send key events.
 */
public final class Committer {

    private final AccessibilityService service;
    private final ProbeConfig config;

    public Committer(AccessibilityService service, ProbeConfig config) {
        this.service = service;
        this.config = config;
    }

    /** Neovim's terminal-mode escape: Ctrl+backslash, then Ctrl+N. */
    public boolean exitTerminalMode() {
        InputMethod im = service.getInputMethod();
        InputMethod.AccessibilityInputConnection ic =
                im != null ? im.getCurrentInputConnection() : null;
        if (ic == null) return false;
        int ctrl = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        // Keep all four events on the same connection, in order, without delays.
        sendKey(ic, KeyEvent.KEYCODE_BACKSLASH, ctrl);
        sendKey(ic, KeyEvent.KEYCODE_N, ctrl);
        return true;
    }

    /** Explicit mini-keyboard actions only; never a dictation fallback. */
    public boolean sendKeyboardKey(int keyCode, int metaState) {
        InputMethod im = service.getInputMethod();
        InputMethod.AccessibilityInputConnection ic =
                im != null ? im.getCurrentInputConnection() : null;
        if (ic == null) return false;
        sendKey(ic, keyCode, metaState);
        return true;
    }

    private void sendKey(InputMethod.AccessibilityInputConnection ic, int keyCode, int metaState) {
        long now = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0,
                metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_KEYBOARD));
        ic.sendKeyEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_KEYBOARD));
    }

    public boolean commitText(String text, SessionState session) {
        ProbeLog.i("Commit", "COMMIT_ATTEMPT " + session.describe() + " len=" + text.length());

        InputMethod im = service.getInputMethod();
        if (im == null) {
            ProbeLog.w("Commit", "NO_INPUT_METHOD session=" + session.sessionId);
            return false;
        }

        InputMethod.AccessibilityInputConnection ic = im.getCurrentInputConnection();
        if (ic == null) {
            ProbeLog.w("Commit", "NO_CURRENT_INPUT_CONNECTION session=" + session.sessionId);
            return false;
        }

        if (config.clearMetaBeforeCommit()) {
            ic.clearMetaKeyStates(
                    KeyEvent.META_SHIFT_ON
                            | KeyEvent.META_ALT_ON
                            | KeyEvent.META_CTRL_ON
                            | KeyEvent.META_META_ON);
        }

        ic.commitText(text, 1, null);
        ProbeLog.i("Commit", "COMMIT_TEXT_CALLED session=" + session.sessionId + " len=" + text.length());
        return true;
    }
}
