package dev.local.a11yimeprobe;

/**
 * Final transcript cleanup (spec section 18.4 / 21.4). MVP policy: trim
 * surrounding whitespace, drop carriage returns, and replace internal newlines
 * with a space so a transcript can never auto-submit in a terminal. Chinese
 * punctuation and internal spacing are preserved. Newline is NEVER turned into a
 * key event.
 */
public final class TranscriptPostprocessor {

    public String cleanFinalTranscript(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.replace("\r", "");
        t = t.replace('\n', ' ');
        return t.trim();
    }
}
