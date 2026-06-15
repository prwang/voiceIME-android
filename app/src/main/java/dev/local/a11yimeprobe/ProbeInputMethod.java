package dev.local.a11yimeprobe;

import android.accessibilityservice.InputMethod;
import android.view.inputmethod.EditorInfo;

/**
 * Accessibility input method (spec section 6.3). Logs lifecycle callbacks,
 * stores the last EditorInfo for diagnostics and same-editor gating, and
 * maintains a monotonically increasing editor generation counter.
 */
public final class ProbeInputMethod extends InputMethod {

    private final ProbeAccessibilityService service;
    private EditorInfo lastEditorInfo;
    private long editorGeneration = 0;

    public ProbeInputMethod(ProbeAccessibilityService service) {
        super(service);
        this.service = service;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        editorGeneration++;
        lastEditorInfo = attribute;
        ProbeLog.i("Ime",
                "IME_START_INPUT gen=" + editorGeneration
                        + " pkg=" + (attribute != null ? attribute.packageName : null)
                        + " fieldId=" + (attribute != null ? attribute.fieldId : 0)
                        + " inputType=0x" + Integer.toHexString(attribute != null ? attribute.inputType : 0)
                        + " imeOptions=0x" + Integer.toHexString(attribute != null ? attribute.imeOptions : 0)
                        + " restarting=" + restarting);
        // Legitimate input-focus signal: an editor field is now focused. Drives
        // overlay visibility (show only when there is somewhere to dictate into).
        service.onEditorFocusChanged(true);
    }

    @Override
    public void onFinishInput() {
        editorGeneration++;
        ProbeLog.i("Ime", "IME_FINISH_INPUT gen=" + editorGeneration);
        lastEditorInfo = null;
        service.onEditorFocusChanged(false);
    }

    @Override
    public void onUpdateSelection(
            int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        ProbeLog.i("Ime",
                "IME_UPDATE_SELECTION old=" + oldSelStart + "," + oldSelEnd
                        + " new=" + newSelStart + "," + newSelEnd
                        + " cand=" + candidatesStart + "," + candidatesEnd);
    }

    public EditorInfo getLastEditorInfoForLogOnly() {
        return lastEditorInfo;
    }

    public long getEditorGeneration() {
        return editorGeneration;
    }
}
