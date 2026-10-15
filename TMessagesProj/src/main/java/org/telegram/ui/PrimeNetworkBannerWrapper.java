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

    // Each check is several binder calls into system services (airplane setting, active network,
    // every network's capabilities, Wi-Fi state); on the main thread one of them stalled a frame for
    // ~0.5 s right after launch. They run on a background queue now; only the result touches views.
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (attached) {
                handler.postDelayed(this, 3000);
            }
        }
    };
    private boolean checkInFlight;

    public PrimeNetworkBannerWrapper(Context context) {
        super(context);
        setClipToPadding(false);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // A floating rounded pill rather than a full-width strip, the way iOS shows these.
        final android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(0xF02B2B33);
        pill.setCornerRadius(dp(18));
        row.setBackground(pill);
        row.setElevation(dp(4));
        row.setPadding(dp(16), dp(10), dp(12), dp(10));

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

        addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 12, 0, 12, 0));
        row.setVisibility(GONE);
    }

    private View bannerRow() {
        return getChildAt(0);
    }

    public void refresh() {
        if (checkInFlight) {
            return;
        }
        checkInFlight = true;
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            final int variant = PrimeConnectivityState.shouldShowAirplaneBanner() ? 0
                    : PrimeConnectivityState.shouldShowMobileDataBanner() ? 1 : -1;
            AndroidUtilities.runOnUIThread(() -> {
                checkInFlight = false;
                apply(variant);
            });
        });
    }

    private void apply(int variant) {
        final String message = variant == 0 ? "Включён авиарежим — сообщения не отправляются и не приходят."
                : variant == 1 ? "Мобильный интернет выключен, а Wi-Fi недоступен — соединения нет." : null;
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
        // Not during launch: the banner only matters once the user is looking at the list.
        handler.postDelayed(pollRunnable, 2000);
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
        // Since 12.10 the screen is drawn edge to edge: this container starts under the status
        // bar, and a banner placed at its very top sat behind the clock and icons with part of its
        // text covered. Kept below the status bar, with a little air.
        final int top = AndroidUtilities.statusBarHeight + dp(6);
        if (getPaddingTop() != top) {
            setPadding(0, top, 0, dp(6));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
