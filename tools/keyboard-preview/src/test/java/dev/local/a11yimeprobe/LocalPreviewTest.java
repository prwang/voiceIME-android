package dev.local.a11yimeprobe;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LocalPreviewTest {
    private final FloatingControlsView.Actions actions = new FloatingControlsView.Actions() {
        public void text(String text) {}
        public void key(int code, int modifiers) {}
        public void exitTerminal() {}
        public void hide() {}
        public void close() {}
    };

    @Test public void renderAndCheckExpandedCollapsedAndLargeText() throws Exception {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                android.R.style.Theme_Material_NoActionBar);
        FloatingControlsView panel = new FloatingControlsView(context, actions);
        layout(panel);
        int talkX = panel.getTalkButton().getLeft();
        int handleX = panel.getDragHandle().getLeft();
        save(panel, "local-collapsed.png");
        assertEquals(dp(context, 104), panel.getWidth());
        assertEquals(dp(context, 48), panel.getHeight());

        panel.setExpanded(true, dp(context, 270));
        layout(panel);
        assertEquals(dp(context, 186), panel.getWidth());
        assertEquals(dp(context, 270), panel.getHeight());
        assertEquals(talkX, panel.getTalkButton().getLeft());
        assertEquals(handleX, panel.getDragHandle().getLeft());
        List<String> labels = new ArrayList<>();
        inspect(panel, labels);
        assertEquals(java.util.Arrays.asList("Hide keyboard", "Close until next input",
                "Space", "Arrow up", "Backspace", "Exit terminal mode: Control backslash, Control N",
                "Arrow left", "Arrow down", "Arrow right", "Enter", "Dollar sign", "Double quote",
                "Control K", "Control J", "Home", "End", "Page up", "Page down", "/", "-", "_", "\\"), labels);
        save(panel, "local-expanded.png");

        Configuration large = new Configuration(context.getResources().getConfiguration());
        large.fontScale = 1.5f;
        Context largeContext = new ContextThemeWrapper(context.createConfigurationContext(large),
                android.R.style.Theme_Material_NoActionBar);
        FloatingControlsView largePanel = new FloatingControlsView(largeContext, actions);
        largePanel.setExpanded(true, dp(largeContext, 270));
        layout(largePanel);
        inspect(largePanel, new ArrayList<>());
        save(largePanel, "local-large-font.png");

        panel.setExpanded(true, dp(context, 190));
        layout(panel);
        assertEquals(dp(context, 190), panel.getHeight());
        assertEquals(dp(context, 186), panel.getWidth());
        save(panel, "local-short-window.png");
    }

    private void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private void inspect(View view, List<String> labels) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            assertEquals("Wrapped: " + text.getText(), 1, text.getLineCount());
            assertTrue("Clipped text: " + text.getText(), text.getPaint().measureText(text.getText().toString())
                    <= text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight());
        }
        if (view.isClickable() && view.getContentDescription() != null) {
            labels.add(view.getContentDescription().toString());
            assertTrue(view.getWidth() >= dp(view.getContext(), 36));
            assertTrue(view.getHeight() >= dp(view.getContext(), 36));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) inspect(group.getChildAt(i), labels);
        }
    }

    private void save(View view, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF0E1522);
        view.draw(canvas);
        File file = new File(System.getProperty("preview.output"), name);
        file.getParentFile().mkdirs();
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }
    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
