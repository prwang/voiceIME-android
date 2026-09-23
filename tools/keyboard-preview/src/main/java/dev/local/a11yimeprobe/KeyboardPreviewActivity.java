package dev.local.a11yimeprobe;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Separate disposable APK: uses the exact production keyboard view and resources. */
public final class KeyboardPreviewActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(48), dp(16), dp(16));
        root.setBackgroundColor(0xFF0E1522);
        status = new TextView(this);
        status.setText("Voice IME · keyboard preview");
        status.setTextSize(18);
        status.setTextColor(0xFFE7EFFA);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(64)));
        if (getIntent().getBooleanExtra("editor", false)) {
            EditText editor = new EditText(this);
            editor.setHint("Safe test editor");
            editor.setText("Test editing here");
            editor.setMinLines(3);
            editor.setTextColor(0xFFFFFFFF);
            editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editor.setShowSoftInputOnFocus(false);
            root.addView(editor, new LinearLayout.LayoutParams(-1, dp(180)));
            editor.requestFocus();
        } else {
            FloatingControlsView controls = new FloatingControlsView(this, new FloatingControlsView.Actions() {
                public void text(String text) { status.setText("Text key tapped"); }
                public void key(int key, int modifiers) { status.setText(KeyEvent.keyCodeToString(key)); }
                public void exitTerminal() { status.setText("Control backslash, Control N"); }
                public void hide() { status.setText("Hide keyboard"); }
                public void close() { status.setText("Close until next input"); }
            });
            controls.setExpanded(true, dp(FloatingControlsView.HEIGHT_DP));
            root.addView(controls, new LinearLayout.LayoutParams(-2, -2));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        root.postDelayed(() -> {
            try {
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                        root.getWidth(), root.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                root.draw(new android.graphics.Canvas(bitmap));
                try (java.io.FileOutputStream out = openFileOutput("preview.png", MODE_PRIVATE)) {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                }
                String report = checkLayout(root);
                try (java.io.FileOutputStream out = openFileOutput("layout.txt", MODE_PRIVATE)) {
                    out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (Exception error) { android.util.Log.e("KeyboardPreview", "Preview failed", error); }
        }, 1000);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            String result = "Key: " + event.getKeyCode() + " / " + Integer.toHexString(event.getMetaState());
            status.setText(result);
            android.util.Log.i("KeyboardPreview", result);
        }
        return super.dispatchKeyEvent(event);
    }
    private String checkLayout(android.view.View view) {
        StringBuilder report = new StringBuilder();
        if (view.isClickable() && view.getContentDescription() != null) {
            report.append(view.getContentDescription()).append(": ")
                    .append(view.getWidth()).append("x").append(view.getHeight());
            if (view instanceof TextView) report.append(" lines=").append(((TextView) view).getLineCount());
            report.append("\n");
            if (view.getWidth() < dp(28) || view.getHeight() < dp(36))
                throw new IllegalStateException("Small key: " + view.getContentDescription());
            if (view instanceof TextView && ((TextView) view).getLineCount() != 1)
                throw new IllegalStateException("Wrapped key: " + view.getContentDescription());
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) report.append(checkLayout(group.getChildAt(i)));
        }
        return report.toString();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
