package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.PrimeLogCollector;
import org.telegram.messenger.PrimeLogOverlayState;
import org.telegram.ui.ActionBar.Theme;

import java.util.List;

/**
 * PrimeGram: "Логи" on-screen overlay - a live, on-device view of this process's own logcat
 * output, sitting next to {@link PrimeUiInspectorOverlay} as the same kind of tool: something you
 * flip on from Settings only while actively diagnosing a bug, that lets you grab the evidence
 * without a computer/ADB attached. Collapsed to a small pill by default; tapping it opens a
 * scrollable panel with the recent tail plus Copy/Clear/Close, mirroring the inspector's own
 * "grab the dump without leaving the buggy screen" chip.
 *
 * <p>Add as the LAST child of the activity's root FrameLayout (same spot as the inspector), with
 * MATCH_PARENT bounds; stays GONE and does no work while {@link PrimeLogOverlayState#isEnabled()}
 * is false.
 */
public class PrimeLogOverlay extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int REFRESH_INTERVAL_MS = 400;
    private static final int TAIL_LINES = 600;

    private final TextView badge;
    private final LinearLayout panel;
    private final TextView logText;
    private final ScrollView scrollView;

    private boolean expanded;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshRunnable = this::refresh;

    public PrimeLogOverlay(Context context) {
        super(context);

        badge = new TextView(context);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        badge.setPadding(dp(14), dp(8), dp(14), dp(8));
        badge.setBackground(Theme.createRoundRectDrawable(dp(18), 0xEE2E7D32));
        addView(badge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 16, 0, 0, 120));
        badge.setOnClickListener(v -> setExpanded(!expanded));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xEE101010);
        panel.setVisibility(GONE);
        addView(panel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 340, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText("Логи");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(AndroidUtilities.bold());
        header.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        header.addView(makeHeaderButton(context, "Очистить", v -> {
            PrimeLogCollector.clear();
            refresh();
        }));
        header.addView(makeHeaderButton(context, "Копировать", v -> copyToClipboard()));
        header.addView(makeHeaderButton(context, "✕", v -> setExpanded(false)));

        scrollView = new ScrollView(context);
        panel.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        logText = new TextView(context);
        logText.setTextColor(0xFFB0F0B0);
        logText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setPadding(dp(10), dp(4), dp(10), dp(20));
        logText.setTextIsSelectable(true);
        scrollView.addView(logText, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        setWillNotDraw(true);
        updateVisibility();
    }

    private TextView makeHeaderButton(Context context, String text, OnClickListener onClick) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextColor(0xFF66CCFF);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.leftMargin = dp(4);
        button.setLayoutParams(lp);
        return button;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.primeLogOverlayChanged);
        updateVisibility();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.primeLogOverlayChanged);
        handler.removeCallbacks(refreshRunnable);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.primeLogOverlayChanged) {
            updateVisibility();
        }
    }

    private void updateVisibility() {
        final boolean enabled = PrimeLogOverlayState.isEnabled();
        setVisibility(enabled ? VISIBLE : GONE);
        handler.removeCallbacks(refreshRunnable);
        if (enabled) {
            handler.post(refreshRunnable);
        } else {
            setExpanded(false);
        }
    }

    private void setExpanded(boolean value) {
        expanded = value;
        panel.setVisibility(value ? VISIBLE : GONE);
        badge.setVisibility(value ? GONE : VISIBLE);
    }

    private void refresh() {
        if (!PrimeLogOverlayState.isEnabled()) {
            return;
        }
        badge.setText("Логи (" + PrimeLogCollector.lineCount() + ")");
        if (expanded) {
            final List<String> lines = PrimeLogCollector.tail(TAIL_LINES);
            logText.setText(TextUtils.join("\n", lines));
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    private void copyToClipboard() {
        final String dump = PrimeLogCollector.dumpAll();
        AndroidUtilities.addToClipboard(dump.isEmpty() ? "(лог пуст)" : dump);
        Toast.makeText(getContext(), "Скопировано: " + PrimeLogCollector.lineCount() + " строк", Toast.LENGTH_SHORT).show();
    }
}
