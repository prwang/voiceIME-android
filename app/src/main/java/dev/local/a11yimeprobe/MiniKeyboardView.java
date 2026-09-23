package dev.local.a11yimeprobe;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Four columns, five rows at 75% of the original key dimensions. */
public final class MiniKeyboardView extends LinearLayout {
    public static final int HEIGHT_DP = 215;
    private static final int CTRL = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;

    public interface Actions {
        void text(String text);
        void key(int keyCode, int modifiers);
        void exitTerminal();
    }

    public MiniKeyboardView(Context context, Actions actions) {
        super(context);
        setOrientation(VERTICAL);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout row = row();
        add(row, icon(R.drawable.key_space, "Space", () -> actions.text(" ")));
        add(row, icon(R.drawable.key_up, "Arrow up", () -> actions.key(KeyEvent.KEYCODE_DPAD_UP, 0)));
        add(row, icon(R.drawable.key_backspace, "Backspace", () -> actions.key(KeyEvent.KEYCODE_DEL, 0)));
        add(row, text("ET", "Exit terminal mode: Control backslash, Control N", actions::exitTerminal));
        row = row();
        add(row, icon(R.drawable.key_left, "Arrow left", () -> actions.key(KeyEvent.KEYCODE_DPAD_LEFT, 0)));
        add(row, icon(R.drawable.key_down, "Arrow down", () -> actions.key(KeyEvent.KEYCODE_DPAD_DOWN, 0)));
        add(row, icon(R.drawable.key_right, "Arrow right", () -> actions.key(KeyEvent.KEYCODE_DPAD_RIGHT, 0)));
        add(row, icon(R.drawable.key_enter, "Enter", () -> actions.key(KeyEvent.KEYCODE_ENTER, 0)));
        row = row();
        add(row, text("$", "Dollar sign", () -> actions.text("$")));
        add(row, text("\"", "Double quote", () -> actions.text("\"")));
        add(row, icon(R.drawable.key_ctrl_k, "Control K", () -> actions.key(KeyEvent.KEYCODE_K, CTRL)));
        add(row, icon(R.drawable.key_ctrl_j, "Control J", () -> actions.key(KeyEvent.KEYCODE_J, CTRL)));
        row = row();
        add(row, icon(R.drawable.key_home, "Home", () -> actions.key(KeyEvent.KEYCODE_MOVE_HOME, 0)));
        add(row, icon(R.drawable.key_end, "End", () -> actions.key(KeyEvent.KEYCODE_MOVE_END, 0)));
        add(row, icon(R.drawable.key_page_up, "Page up", () -> actions.key(KeyEvent.KEYCODE_PAGE_UP, 0)));
        add(row, icon(R.drawable.key_page_down, "Page down", () -> actions.key(KeyEvent.KEYCODE_PAGE_DOWN, 0)));
        row = row();
        for (String symbol : new String[]{"/", "-", "_", "\\"}) {
            add(row, text(symbol, symbol, () -> actions.text(symbol)));
        }
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, dp(39));
        if (getChildCount() > 0) params.topMargin = dp(5);
        addView(row, params);
        return row;
    }

    private void add(LinearLayout row, View key) {
        LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
        if (row.getChildCount() > 0) params.leftMargin = dp(4);
        row.addView(key, params);
    }

    private View icon(int resource, String description, Runnable action) {
        return OverlayStyle.icon(getContext(), resource, description, action);
    }

    private TextView text(String label, String description, Runnable action) {
        TextView key = new TextView(getContext());
        key.setText(label);
        key.setTextColor(0xFFF4F7FC);
        key.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        key.setGravity(Gravity.CENTER);
        key.setSingleLine(true);
        key.setHorizontallyScrolling(false);
        key.setIncludeFontPadding(false);
        key.setAutoSizeTextTypeUniformWithConfiguration(10, label.length() > 1 ? 13 : 18, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        key.setPadding(dp(3), 0, dp(3), 0);
        OverlayStyle.decorate(key, description, action);
        return key;
    }

    private int dp(int value) { return OverlayStyle.dp(getContext(), value); }
}
