package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PrimeConnectivityState;
import org.telegram.ui.Components.LayoutHelper;

/**
 * PrimeGram: a persistent (not auto-hiding) app-shell nag banner for the two
 * connectivity-loss cases the user asked to be warned about - airplane mode with no
 * other connection, and mobile data off with no other connection. Modeled directly on
 * {@link UpdateLayoutWrapper}'s self-collapsing-when-empty shape: zero measured height
 * when nothing needs showing, so it never reserves dead space in the layout.
 *
 * {@code Bulletin}/{@code BulletinFactory} (the fork's toast system) was deliberately
 * not reused here - every variant there auto-hides within a few seconds, and this needs
 * to stay up until the user acts or the situation resolves itself.
 */
public class PrimeNetworkBannerWrapper extends FrameLayout {

    private final TextView textView;
    private final Handler handler = new Handler();
    private boolean attached;
    private int shownVariant = -1; // 0 = airplane, 1 = mobile data, -1 = none

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (attached) {
                handler.postDelayed(this, 3000);
            }
        }
    };

    public PrimeNetworkBannerWrapper(Context context) {
        super(context);
        setClipToPadding(false);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(0xFF2B2B33);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));

        textView = new TextView(context);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        row.addView(textView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView okButton = new TextView(context);
        okButton.setText("OK");
        okButton.setTextColor(0xFF6FCF97);
        okButton.setTypeface(AndroidUtilities.bold());
        okButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        okButton.setPadding(dp(16), dp(4), dp(4), dp(4));
        okButton.setClickable(true);
        okButton.setOnClickListener(v -> {
            if (shownVariant == 0) {
                PrimeConnectivityState.dismissAirplaneBanner();
            } else if (shownVariant == 1) {
                PrimeConnectivityState.dismissMobileDataBanner();
            }
            refresh();
        });
        row.addView(okButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        row.setVisibility(GONE);
    }

    private View bannerRow() {
        return getChildAt(0);
    }

    public void refresh() {
        int variant;
        String message;
        if (PrimeConnectivityState.shouldShowAirplaneBanner()) {
            variant = 0;
            message = "Включён авиарежим — сообщения не отправляются и не приходят.";
        } else if (PrimeConnectivityState.shouldShowMobileDataBanner()) {
            variant = 1;
            message = "Мобильный интернет выключен, а Wi-Fi недоступен — соединения нет.";
        } else {
            variant = -1;
            message = null;
        }
        if (variant == shownVariant) {
            return;
        }
        shownVariant = variant;
        if (variant == -1) {
            bannerRow().setVisibility(GONE);
        } else {
            textView.setText(message);
            bannerRow().setVisibility(VISIBLE);
        }
        requestLayout();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        refresh();
        handler.post(pollRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        handler.removeCallbacks(pollRunnable);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final boolean visible = bannerRow().getVisibility() == VISIBLE;
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        if (!visible) {
            setMeasuredDimension(width, 0);
            final int zeroSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY);
            bannerRow().measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), zeroSpec);
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
