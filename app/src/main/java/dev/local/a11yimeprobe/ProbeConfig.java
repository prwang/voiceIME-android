package dev.local.a11yimeprobe;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences wrapper (spec section 5 / 18). Holds the product dictation
 * config: DashScope ASR settings, overlay enable/position, and a debug fake-ASR
 * toggle. MainActivity and the accessibility service share one process, so the
 * service can observe changes via OnSharedPreferenceChangeListener.
 */
public final class ProbeConfig {

    public static final String PREFS = "probe_config";

    // Default International (Singapore) endpoint; editable in MainActivity.
    public static final String DEFAULT_BASE_URL =
            "https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
    public static final String DEFAULT_MODEL = "qwen3-asr-flash";

    public static final String KEY_API_KEY = "asr_api_key";
    public static final String KEY_BASE_URL = "asr_base_url";
    public static final String KEY_MODEL = "asr_model";
    public static final String KEY_LANGUAGE = "asr_language";
    public static final String KEY_ENABLE_ITN = "enable_itn";
    public static final String KEY_OVERLAY_ENABLED = "overlay_enabled";
    public static final String KEY_OVERLAY_X = "overlay_x";
    public static final String KEY_OVERLAY_Y = "overlay_y";
    public static final String KEY_USE_FAKE_ASR = "use_fake_asr";
    public static final String KEY_CLEAR_META = "clear_meta_before_commit";

    private final SharedPreferences sp;

    public ProbeConfig(Context context) {
        sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public SharedPreferences prefs() {
        return sp;
    }

    public String getAsrApiKey() {
        return sp.getString(KEY_API_KEY, "");
    }

    public void setAsrApiKey(String v) {
        sp.edit().putString(KEY_API_KEY, v != null ? v.trim() : "").apply();
    }

    public String getAsrBaseUrl() {
        String v = sp.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
        return (v == null || v.isEmpty()) ? DEFAULT_BASE_URL : v;
    }

    public void setAsrBaseUrl(String v) {
        sp.edit().putString(KEY_BASE_URL, (v == null || v.trim().isEmpty()) ? DEFAULT_BASE_URL : v.trim()).apply();
    }

    public String getAsrModel() {
        String v = sp.getString(KEY_MODEL, DEFAULT_MODEL);
        return (v == null || v.isEmpty()) ? DEFAULT_MODEL : v;
    }

    public void setAsrModel(String v) {
        sp.edit().putString(KEY_MODEL, (v == null || v.trim().isEmpty()) ? DEFAULT_MODEL : v.trim()).apply();
    }

    public String getAsrLanguage() {
        return sp.getString(KEY_LANGUAGE, "");
    }

    public void setAsrLanguage(String v) {
        sp.edit().putString(KEY_LANGUAGE, v != null ? v.trim() : "").apply();
    }

    public boolean enableItn() {
        return sp.getBoolean(KEY_ENABLE_ITN, false);
    }

    public void setEnableItn(boolean v) {
        sp.edit().putBoolean(KEY_ENABLE_ITN, v).apply();
    }

    public boolean overlayEnabled() {
        return sp.getBoolean(KEY_OVERLAY_ENABLED, false);
    }

    public void setOverlayEnabled(boolean v) {
        sp.edit().putBoolean(KEY_OVERLAY_ENABLED, v).apply();
    }

    public int getOverlayX() {
        return sp.getInt(KEY_OVERLAY_X, -1);
    }

    public int getOverlayY() {
        return sp.getInt(KEY_OVERLAY_Y, -1);
    }

    public void setOverlayPosition(int x, int y) {
        sp.edit().putInt(KEY_OVERLAY_X, x).putInt(KEY_OVERLAY_Y, y).apply();
    }

    public boolean useFakeAsr() {
        return sp.getBoolean(KEY_USE_FAKE_ASR, false);
    }

    public void setUseFakeAsr(boolean v) {
        sp.edit().putBoolean(KEY_USE_FAKE_ASR, v).apply();
    }

    public boolean clearMetaBeforeCommit() {
        return sp.getBoolean(KEY_CLEAR_META, false);
    }

    /** True when the real client can run (API key present). */
    public boolean isConfigured() {
        return !getAsrApiKey().isEmpty();
    }

    public String describe() {
        return "configured=" + isConfigured()
                + " base=" + getAsrBaseUrl()
                + " model=" + getAsrModel()
                + " lang=" + (getAsrLanguage().isEmpty() ? "auto" : getAsrLanguage())
                + " itn=" + enableItn()
                + " overlay=" + overlayEnabled()
                + " fake=" + useFakeAsr();
    }
}
