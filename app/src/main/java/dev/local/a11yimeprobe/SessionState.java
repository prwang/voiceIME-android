package dev.local.a11yimeprobe;

import android.os.SystemClock;
import android.view.inputmethod.EditorInfo;

/**
 * One object per overlay hold (spec section 5 / 8). Captures a best-effort
 * editor fingerprint at session creation and provides the same-editor gate
 * used before delayed commits. Never caches an AccessibilityInputConnection.
 */
public final class SessionState {

    private static long counter = 0;

    public final long sessionId;
    public final long createdElapsedMs;
    public final String mode;
    public final String payloadName;

    // Editor fingerprint (spec section 8.2).
    public final long editorGeneration;
    public final String packageName;
    public final int fieldId;
    public final int inputType;
    public final int imeOptions;

    public volatile boolean cancelled = false;
    public long stopElapsedMs = 0L;

    private SessionState(long sessionId, long createdElapsedMs, String mode,
                         String payloadName, long editorGeneration, String packageName,
                         int fieldId, int inputType, int imeOptions) {
        this.sessionId = sessionId;
        this.createdElapsedMs = createdElapsedMs;
        this.mode = mode;
        this.payloadName = payloadName;
        this.editorGeneration = editorGeneration;
        this.packageName = packageName;
        this.fieldId = fieldId;
        this.inputType = inputType;
        this.imeOptions = imeOptions;
    }

    public static synchronized SessionState capture(ProbeInputMethod im, String mode,
                                                    String payloadName) {
        EditorInfo ei = im != null ? im.getLastEditorInfoForLogOnly() : null;
        long gen = im != null ? im.getEditorGeneration() : 0L;
        String pkg = (ei != null && ei.packageName != null) ? ei.packageName.toString() : null;
        int fieldId = ei != null ? ei.fieldId : 0;
        int inputType = ei != null ? ei.inputType : 0;
        int imeOptions = ei != null ? ei.imeOptions : 0;
        return new SessionState(++counter, SystemClock.elapsedRealtime(), mode, payloadName,
                gen, pkg, fieldId, inputType, imeOptions);
    }

    /**
     * Same-editor gate (spec section 8.3). The strict path requires an
     * unchanged editor generation in the same package. For terminal-style
     * editors where fieldId/generation may be unstable, a generation change is
     * tolerated only when field identity (fieldId + inputType + imeOptions) is
     * meaningful and unchanged in the same package. Any relaxation is logged by
     * the caller.
     */
    public boolean matchesCurrent(EditorInfo current, long currentGeneration) {
        if (current == null) {
            return false;
        }
        String curPkg = current.packageName != null ? current.packageName.toString() : null;
        if (packageName == null || curPkg == null || !packageName.equals(curPkg)) {
            return false;
        }
        if (currentGeneration == editorGeneration) {
            return fieldId == 0 || current.fieldId == 0 || fieldId == current.fieldId;
        }
        // Generation changed: only accept with stable, meaningful field identity.
        return fieldId != 0 && current.fieldId != 0 && fieldId == current.fieldId
                && inputType == current.inputType && imeOptions == current.imeOptions;
    }

    public String describe() {
        return "session=" + sessionId
                + " mode=" + mode
                + " payload=" + payloadName
                + " gen=" + editorGeneration
                + " pkg=" + packageName
                + " fieldId=" + fieldId
                + " inputType=0x" + Integer.toHexString(inputType)
                + " imeOptions=0x" + Integer.toHexString(imeOptions);
    }
}
