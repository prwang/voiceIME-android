package dev.local.a11yimeprobe;

import android.util.Log;

/**
 * Central logging helper with fixed tags (spec section 5 / 11.3).
 * Tags are emitted as "A11yImeProbe/<category>" so logcat can filter with
 * -s A11yImeProbe/Main A11yImeProbe/Commit ...
 *
 * Privacy: callers must log metadata only by default. Fixed probe payloads
 * may be logged because they are not user content.
 */
public final class ProbeLog {

    private static final String PREFIX = "A11yImeProbe/";

    private ProbeLog() {
    }

    public static void i(String category, String msg) {
        Log.i(PREFIX + category, msg);
    }

    public static void w(String category, String msg) {
        Log.w(PREFIX + category, msg);
    }

    public static void e(String category, String msg) {
        Log.e(PREFIX + category, msg);
    }
}
