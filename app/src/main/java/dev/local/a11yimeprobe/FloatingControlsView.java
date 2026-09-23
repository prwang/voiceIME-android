package dev.local.a11yimeprobe;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One panel: the same Hold/handle pair anchors both collapsed and expanded states. */
public final class FloatingControlsView extends LinearLayout {
    public static final int WIDTH_DP = 186;
    public static final int HEIGHT_DP = 270;
    private final TextView talk;
    private final View handle;
    private final View spacer;
    private final View hide;
    private final View close;
    private final ScrollView keys;
    private final LinearLayout header;
    private boolean expanded;

    public interface Actions extends MiniKeyboardView.Actions {
        void hide();
        void close();
    }

    public FloatingControlsView(Context context, Actions actions) {
        super(context);
        setOrientation(VERTICAL);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        setPadding(dp(6), dp(6), dp(6), dp(6));
        setBackground(OverlayStyle.panel(context));

        header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        talk = new TextView(context);
        talk.setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        talk.setTextColor(0xFFF4F7FC);
        talk.setSingleLine(true);
        talk.setHorizontallyScrolling(false);
        talk.setGravity(Gravity.CENTER);
        talk.setPadding(dp(4), 0, dp(4), 0);
        Drawable mic = context.getDrawable(R.drawable.key_mic);
        mic.setBounds(0, 0, dp(12), dp(12));
        talk.setCompoundDrawables(null, null, mic, null);
        talk.setCompoundDrawablePadding(dp(3));
        header.addView(talk, new LayoutParams(dp(60), dp(36)));
        setTalkState("Hold", "Hold to talk", OverlayStyle.TILE_COLOR);

        handle = new GripView(context);
        handle.setContentDescription("Drag to move; long press to expand or collapse keyboard");
        OverlayStyle.tile(handle, OverlayStyle.TILE_COLOR);
        LayoutParams handleParams = new LayoutParams(dp(28), dp(36));
        handleParams.leftMargin = dp(4);
        header.addView(handle, handleParams);

        spacer = new View(context);
        header.addView(spacer, new LayoutParams(0, 1, 1));
        hide = OverlayStyle.icon(context, R.drawable.key_hide, "Hide keyboard", actions::hide);
        header.addView(hide, new LayoutParams(dp(36), dp(36)));
        close = OverlayStyle.icon(context, R.drawable.key_close, "Close until next input", actions::close);
        LayoutParams closeParams = new LayoutParams(dp(36), dp(36));
        closeParams.leftMargin = dp(4);
        header.addView(close, closeParams);
        addView(header);

        keys = new ScrollView(context);
        keys.setVerticalScrollBarEnabled(false);
        keys.addView(new MiniKeyboardView(context, actions));
        LayoutParams keyParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(MiniKeyboardView.HEIGHT_DP));
        keyParams.topMargin = dp(7);
        addView(keys, keyParams);
        setExpanded(false, dp(HEIGHT_DP));
    }

    public TextView getTalkButton() { return talk; }
    public View getDragHandle() { return handle; }
    public boolean isExpanded() { return expanded; }

    public void setExpanded(boolean value, int availableHeight) {
        expanded = value;
        int visibility = value ? VISIBLE : GONE;
        spacer.setVisibility(visibility);
        hide.setVisibility(visibility);
        close.setVisibility(visibility);
        keys.setVisibility(visibility);
        header.getLayoutParams().width = value ? dp(WIDTH_DP - 12) : LayoutParams.WRAP_CONTENT;
        keys.getLayoutParams().height = Math.min(dp(MiniKeyboardView.HEIGHT_DP),
                Math.max(dp(39), availableHeight - dp(55)));
        requestLayout();
    }

    public void setTalkState(String text, String description, int color) {
        talk.setText(text);
        talk.setContentDescription(description);
        OverlayStyle.tile(talk, color);
    }

    /** Six-dot grip drawn directly so font fallback cannot hide the handle. */
    private static final class GripView extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        GripView(Context context) {
            super(context);
            paint.setColor(0xFFF4F7FC);
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            for (int col = -1; col <= 1; col += 2) {
                for (int row = -1; row <= 1; row++) {
                    canvas.drawCircle(getWidth() / 2f + col * 2.5f * density,
                            getHeight() / 2f + row * 5 * density, 1.2f * density, paint);
                }
            }
        }
    }

    private int dp(int value) { return OverlayStyle.dp(getContext(), value); }
}
