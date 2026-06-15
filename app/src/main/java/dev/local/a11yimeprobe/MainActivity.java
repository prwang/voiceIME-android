package dev.local.a11yimeprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Product settings screen (spec section 10 / 18). Holds the DashScope ASR config,
 * a master toggle to show/hide the floating dictation button, and a debug
 * fake-ASR toggle. Built in code to avoid AppCompat/Material dependencies.
 */
public final class MainActivity extends Activity {

    private ProbeConfig config;
    private EditText apiKeyField;
    private EditText baseUrlField;
    private EditText modelField;
    private EditText languageField;
    private CheckBox enableItn;
    private CheckBox useFakeAsr;
    private CheckBox overlayEnabled;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = new ProbeConfig(this);
        applyIntentExtras(getIntent());
        setContentView(buildUi());
        refreshStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntentExtras(intent);
        loadFieldsFromConfig();
        refreshStatus();
    }

    /** Optional automation: --es asr_api_key .. --es asr_base_url .. --ez overlay_enabled .. */
    private void applyIntentExtras(Intent intent) {
        if (intent == null) {
            return;
        }
        boolean any = false;
        if (intent.hasExtra("asr_api_key")) {
            config.setAsrApiKey(intent.getStringExtra("asr_api_key"));
            any = true;
        }
        if (intent.hasExtra("asr_base_url")) {
            config.setAsrBaseUrl(intent.getStringExtra("asr_base_url"));
            any = true;
        }
        if (intent.hasExtra("asr_model")) {
            config.setAsrModel(intent.getStringExtra("asr_model"));
            any = true;
        }
        if (intent.hasExtra("use_fake_asr")) {
            config.setUseFakeAsr(intent.getBooleanExtra("use_fake_asr", false));
            any = true;
        }
        if (intent.hasExtra("overlay_enabled")) {
            config.setOverlayEnabled(intent.getBooleanExtra("overlay_enabled", false));
            any = true;
        }
        if (any) {
            ProbeLog.i("Main", "CONFIG_APPLIED " + config.describe());
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = 24;
        root.setPadding(pad, pad, pad, pad);

        TextView device = new TextView(this);
        device.setText("VoiceImeMobile (probe build)\n"
                + "package: " + getPackageName() + "\n"
                + "API level: " + Build.VERSION.SDK_INT + "\n"
                + "device: " + Build.MANUFACTURER + " " + Build.MODEL);
        root.addView(device);

        root.addView(button("Request RECORD_AUDIO", v ->
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1)));
        root.addView(button("Open Accessibility Settings", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        root.addView(label("DashScope ASR config"));
        apiKeyField = field("API key (DashScope)", config.getAsrApiKey(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        root.addView(apiKeyField);
        baseUrlField = field("Base URL", config.getAsrBaseUrl(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(baseUrlField);
        modelField = field("Model", config.getAsrModel(), InputType.TYPE_CLASS_TEXT);
        root.addView(modelField);
        languageField = field("Language hint (blank = auto)", config.getAsrLanguage(),
                InputType.TYPE_CLASS_TEXT);
        root.addView(languageField);

        enableItn = new CheckBox(this);
        enableItn.setText("enable_itn (inverse text normalization)");
        enableItn.setChecked(config.enableItn());
        root.addView(enableItn);

        useFakeAsr = new CheckBox(this);
        useFakeAsr.setText("Use fake ASR (offline test)");
        useFakeAsr.setChecked(config.useFakeAsr());
        root.addView(useFakeAsr);

        root.addView(button("Save config", v -> saveConfig()));

        root.addView(label("Floating button"));
        overlayEnabled = new CheckBox(this);
        overlayEnabled.setText("Show floating dictation button");
        overlayEnabled.setChecked(config.overlayEnabled());
        overlayEnabled.setOnCheckedChangeListener((b, checked) -> {
            config.setOverlayEnabled(checked);
            ProbeLog.i("Main", "OVERLAY_ENABLED=" + checked);
            refreshStatus();
        });
        root.addView(overlayEnabled);

        statusView = new TextView(this);
        statusView.setPadding(0, 24, 0, 0);
        root.addView(statusView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void saveConfig() {
        config.setAsrApiKey(apiKeyField.getText().toString());
        config.setAsrBaseUrl(baseUrlField.getText().toString());
        config.setAsrModel(modelField.getText().toString());
        config.setAsrLanguage(languageField.getText().toString());
        config.setEnableItn(enableItn.isChecked());
        config.setUseFakeAsr(useFakeAsr.isChecked());
        // Reflect any normalization back into the fields.
        loadFieldsFromConfig();
        ProbeLog.i("Main", "CONFIG_APPLIED " + config.describe());
        refreshStatus();
    }

    private void loadFieldsFromConfig() {
        if (apiKeyField == null) {
            return;
        }
        apiKeyField.setText(config.getAsrApiKey());
        baseUrlField.setText(config.getAsrBaseUrl());
        modelField.setText(config.getAsrModel());
        languageField.setText(config.getAsrLanguage());
        enableItn.setChecked(config.enableItn());
        useFakeAsr.setChecked(config.useFakeAsr());
        overlayEnabled.setChecked(config.overlayEnabled());
    }

    private void refreshStatus() {
        if (statusView == null) {
            return;
        }
        String dictate = (config.useFakeAsr() || config.isConfigured()) ? "ready" : "needs API key";
        statusView.setText("Accessibility enabled: " + (isServiceEnabled() ? "yes" : "no / unknown")
                + "\nDictation: " + dictate
                + "\n" + config.describe());
    }

    private EditText field(String hint, String value, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(inputType);
        e.setSingleLine(true);
        return e;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText("\n" + text);
        t.setPadding(0, 16, 0, 4);
        return t;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setOnClickListener(listener);
        return btn;
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName());
    }
}
