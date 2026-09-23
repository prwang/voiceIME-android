package dev.local.a11yimeprobe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

/** Shared surfaces for the collapsed controls and the expanded keyboard. */
final class OverlayStyle {
    static final int TILE_COLOR = 0xFF2D3D55;

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable panel(Context context) {
        GradientDrawable panel = shape(context, 0xFF172235);
        panel.setStroke(dp(context, 1), 0xFF42516A);
        return panel;
    }

    static void tile(View view, int color) {
        GradientDrawable tile = shape(view.getContext(), color);
        tile.setStroke(dp(view.getContext(), 1), 0xFF50617B);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x557CB8FF), tile, null));
    }

    static ImageButton icon(Context context, int resource, String description, Runnable action) {
        ImageButton key = new ImageButton(context);
        key.setImageResource(resource);
        key.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = dp(context, 9);
        key.setPadding(padding, padding, padding, padding);
        decorate(key, description, action);
        return key;
    }

    static void decorate(View key, String description, Runnable action) {
        tile(key, TILE_COLOR);
        key.setContentDescription(description);
        key.setTooltipText(description);
        key.setOnClickListener(v -> action.run());
    }

    private static GradientDrawable shape(Context context, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, 8));
        return shape;
    }
}
