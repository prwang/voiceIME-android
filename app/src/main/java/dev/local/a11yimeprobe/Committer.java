package dev.local.a11yimeprobe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.InputMethod;
import android.view.KeyEvent;

/**
 * The only allowed text-output path (spec section 2.1 / 6.4). Inserts text via
 * AccessibilityInputConnection.commitText(). It never uses node mutation,
 * clipboard, synthetic key events, editor actions, or any other fallback.
 */
public final class Committer {

    private final AccessibilityService service;
    private final ProbeConfig config;

    public Committer(AccessibilityService service, ProbeConfig config) {
        this.service = service;
        this.config = config;
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
