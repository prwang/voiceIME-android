package dev.local.a11yimeprobe;

/**
 * ASR transcription result (spec section 19.1). Immutable.
 */
public final class AsrResult {

    public final String text;
    public final boolean partial;
    public final String providerRequestId;

    public AsrResult(String text, boolean partial, String providerRequestId) {
        this.text = text != null ? text : "";
        this.partial = partial;
        this.providerRequestId = providerRequestId;
    }
}
