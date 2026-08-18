package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram Music: a small reusable horizontal tab strip (namida's own library browser
 * splits Tracks/Albums/Artists/Genres/Playlists/Folders/Search into distinct screens
 * reachable from its drawer - since PrimeGram only gets one "Музыка" tab, not a whole
 * app, that split has to live as internal navigation like this instead).
 */
public class PrimeMusicSegmentedTabBar extends FrameLayout {

    public interface Listener {
        void onSegmentSelected(int index);
    }

    private final LinearLayout row;
    private final View indicator;
    private final List<TextView> labelViews = new ArrayList<>();
    private int selected;
    private Listener listener;

    public PrimeMusicSegmentedTabBar(Context context, String[] labels) {
        super(context);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout stack = new FrameLayout(context);
        scroll.addView(stack, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(4));
        stack.addView(row, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        indicator = new View(context);
        indicator.setBackground(org.telegram.ui.ActionBar.Theme.createRoundRectDrawable(dp(2), 0xFF1DB954));
        stack.addView(indicator, LayoutHelper.createFrame(0, 3, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 0, 0));

        for (int i = 0; i < labels.length; i++) {
            TextView label = new TextView(context);
            label.setText(labels[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            label.setTypeface(AndroidUtilities.bold());
            label.setPadding(0, 0, 0, dp(10));
            label.setClickable(true);
            final int index = i;
            label.setOnClickListener(v -> select(index, true));
            row.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, i == labels.length - 1 ? 0 : 22, 0));
            labelViews.add(label);
        }

        setSelectedColors();
        post(() -> moveIndicator(false));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getSelected() {
        return selected;
    }

    public void select(int index, boolean notify) {
        if (index < 0 || index >= labelViews.size()) {
            return;
        }
        selected = index;
        setSelectedColors();
        moveIndicator(true);
        if (notify && listener != null) {
            listener.onSegmentSelected(index);
        }
    }

    private void setSelectedColors() {
        for (int i = 0; i < labelViews.size(); i++) {
            labelViews.get(i).setTextColor(i == selected ? 0xFFFFFFFF : 0xFF6E6E77);
        }
    }

    private void moveIndicator(boolean animated) {
        TextView target = labelViews.get(selected);
        if (target.getWidth() == 0) {
            post(() -> moveIndicator(animated));
            return;
        }
        float x = target.getLeft();
        float width = target.getWidth();
        if (animated) {
            indicator.animate().x(x).setDuration(160).start();
            android.view.ViewGroup.LayoutParams lp = indicator.getLayoutParams();
            lp.width = (int) width;
            indicator.setLayoutParams(lp);
        } else {
            indicator.setX(x);
            android.view.ViewGroup.LayoutParams lp = indicator.getLayoutParams();
            lp.width = (int) width;
            indicator.setLayoutParams(lp);
        }
    }
}
