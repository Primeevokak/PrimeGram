package org.telegram.ui.Components;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PrimeWhatsNew;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

import static org.telegram.messenger.AndroidUtilities.dp;

/**
 * PrimeGram: the "what's new" panel.
 *
 * <p>A sheet rather than a full screen, and dismissable by swiping it away, because it arrives
 * uninvited on the launch after an update - when the user opened the app to read a message, not to
 * read about the app.
 *
 * <p>Built to be read at a glance and to be a little fun: an animated header with the version in a
 * travelling highlight, sections with coloured badges, rows that arrive in a short wave and give a
 * springy press when they lead somewhere. Tapping the header replays its animation; keep tapping.
 * With system animations off, everything is simply there - no motion, same content.
 */
public class PrimeWhatsNewSheet {

    private PrimeWhatsNewSheet() {
    }

    public static void show(BaseFragment fragment) {
        final Context context = fragment == null ? null : fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final Theme.ResourcesProvider rp = fragment.getResourceProvider();
        final boolean animate = SharedConfig.animationsEnabled();
        final List<View> wave = new ArrayList<>();
        final List<View> tiles = new ArrayList<>();
        final BottomSheet[] sheetRef = new BottomSheet[1];

        final FrameLayout root = new FrameLayout(context);
        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        final FireworksOverlay fireworks = new FireworksOverlay(context);
        root.addView(fireworks, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // region header
        final LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);

        final RLottieImageView hero = new RLottieImageView(context);
        hero.setAnimation(R.raw.boosts, 96, 96);
        header.addView(hero, LayoutHelper.createLinear(96, 96, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        final ShimmerTextView title = new ShimmerTextView(context, animate,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp),
                Theme.getColor(Theme.key_featuredStickers_addButton, rp));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        title.setText("PrimeGram " + PrimeWhatsNew.currentVersion());
        header.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 6, 21, 0));

        final TextView subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, rp));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setText("Что нового");
        header.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 2, 21, 12));

        final int[] taps = {0};
        header.setOnClickListener(v -> {
            taps[0]++;
            hero.setProgress(0);
            hero.playAnimation();
            bounce(title, animate);
            if (taps[0] == 3) {
                fireworks.start();
            } else if (taps[0] == 7) {
                subtitle.setText("Ты нашёл пасхалку 🥚 Спасибо, что ты с нами!");
                fireworks.start(true);
            } else if (taps[0] > 7 && taps[0] % 5 == 0) {
                fireworks.start(true);
            }
        });
        content.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        // endregion

        // region sections
        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        for (PrimeWhatsNew.Section section : PrimeWhatsNew.sections()) {
            final LinearLayout rows = new LinearLayout(context);
            rows.setOrientation(LinearLayout.VERTICAL);
            for (PrimeWhatsNew.Entry entry : section.entries) {
                final View row = row(context, rp, section, entry, tiles, () -> open(fragment, sheetRef[0], entry.action));
                rows.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                if (!section.collapsed) {
                    wave.add(row);
                }
            }
            final View head = sectionHeader(context, rp, section, rows, list, animate);
            list.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            wave.add(wave.size() - (section.collapsed ? 0 : section.entries.size()), head);
            rows.setVisibility(section.collapsed ? View.GONE : View.VISIBLE);
            list.addView(rows, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        // Scrolls, and is capped, because the list grows with every release and a sheet taller than
        // the screen would put the button out of reach.
        final ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // Everything between the header and the button: the sheet may take most of the
                // screen, and a fixed fraction left the list cramped over empty space.
                // The whole usable height minus the header, the button row and the sheet's own
                // top padding - the button sits at the bottom, the list takes everything else.
                final int cap = Math.max(dp(160), AndroidUtilities.displaySize.y
                        - header.getMeasuredHeight() - dp(68) - dp(16));
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        content.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        // endregion

        final BounceTextView button = new BounceTextView(context);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(AndroidUtilities.bold());
        button.setText("Понятно");
        button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, rp));
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton, rp),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, rp)));
        content.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 12, 16, 8));

        final BottomSheet sheet = new BottomSheet.Builder(context, false, rp)
                .setCustomView(root)
                .create();
        sheetRef[0] = sheet;
        button.setOnClickListener(v -> sheet.dismiss());
        // Marked as seen when it is shown, not when the button is pressed: a user who swipes it
        // away has still seen it, and showing it again on the next launch would be a punishment
        // for dismissing it the way the sheet invites them to.
        PrimeWhatsNew.markSeen();

        if (animate) {
            for (View v : wave) {
                v.setAlpha(0f);
                v.setTranslationY(dp(18));
            }
            for (View t : tiles) {
                t.setScaleX(0.6f);
                t.setScaleY(0.6f);
                t.setRotation(-14f);
            }
        }
        fragment.showDialog(sheet);
        root.postDelayed(() -> {
            hero.playAnimation();
            if (!animate) {
                return;
            }
            for (int i = 0; i < wave.size(); i++) {
                wave.get(i).animate().alpha(1f).translationY(0).setStartDelay(40L * i)
                        .setDuration(420).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            }
            for (int i = 0; i < tiles.size(); i++) {
                tiles.get(i).animate().scaleX(1f).scaleY(1f).rotation(0).setStartDelay(80L + 40L * i)
                        .setDuration(480).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();
            }
        }, 180);
    }

    private static void open(BaseFragment fragment, BottomSheet sheet, String action) {
        if (action == null || fragment == null) {
            return;
        }
        final BaseFragment target;
        switch (action) {
            case PrimeWhatsNew.ACTION_PLUGINS:
                target = new org.telegram.ui.PrimePluginsActivity();
                break;
            case PrimeWhatsNew.ACTION_SETTINGS:
                target = new org.telegram.ui.PrimeGramSettingsActivity();
                break;
            case PrimeWhatsNew.ACTION_PROXY:
                target = new org.telegram.ui.ProxyListActivity();
                break;
            default:
                return;
        }
        if (sheet != null) {
            sheet.dismiss();
        }
        fragment.presentFragment(target);
    }

    private static void bounce(View view, boolean animate) {
        if (!animate) {
            return;
        }
        view.animate().cancel();
        view.setScaleX(1.14f);
        view.setScaleY(1.14f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();
    }

    private static View sectionHeader(Context context, Theme.ResourcesProvider rp, PrimeWhatsNew.Section section,
                                      View rows, ViewGroup list, boolean animate) {
        final LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(21), dp(14), dp(21), dp(6));

        final TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp));
        name.setText(section.title);
        head.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        if (section.badge != null) {
            final TextView badge = new TextView(context);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setLetterSpacing(0.06f);
            badge.setTextColor(0xFFFFFFFF);
            badge.setText(section.badge);
            badge.setPadding(dp(7), dp(2), dp(7), dp(2));
            final GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(6));
            pill.setColor(section.color);
            badge.setBackground(pill);
            head.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        }

        if (section.collapsed) {
            final TextView count = new TextView(context);
            count.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            count.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, rp));
            count.setText(" · " + section.entries.size());
            head.addView(count, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            final View spacer = new View(context);
            head.addView(spacer, LayoutHelper.createLinear(0, 1, 1f));

            final ImageView arrow = new ImageView(context);
            arrow.setImageResource(R.drawable.arrow_more);
            arrow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, rp), PorterDuff.Mode.SRC_IN));
            head.addView(arrow, LayoutHelper.createLinear(24, 24));
            head.setOnClickListener(v -> {
                final boolean open = rows.getVisibility() != View.VISIBLE;
                if (animate) {
                    TransitionManager.beginDelayedTransition(list);
                    ObjectAnimator.ofFloat(arrow, View.ROTATION, open ? 180f : 0f).setDuration(220).start();
                } else {
                    arrow.setRotation(open ? 180f : 0f);
                }
                rows.setVisibility(open ? View.VISIBLE : View.GONE);
            });
            head.setBackground(Theme.getSelectorDrawable(false));
        }
        return head;
    }

    private static View row(Context context, Theme.ResourcesProvider rp, PrimeWhatsNew.Section section,
                            PrimeWhatsNew.Entry entry, List<View> tiles, Runnable onClick) {
        final BounceFrameLayout container = new BounceFrameLayout(context);
        container.setPadding(dp(21), dp(9), dp(21), dp(9));
        if (entry.action != null) {
            container.setOnClickListener(v -> onClick.run());
            container.setBackground(Theme.getSelectorDrawable(false));
        }

        final FrameLayout tile = new FrameLayout(context);
        final GradientDrawable tileBg = new GradientDrawable();
        tileBg.setCornerRadius(dp(11));
        tileBg.setColor((section.color & 0x00FFFFFF) | 0x24000000);
        tile.setBackground(tileBg);
        final ImageView iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        final int iconRes = context.getResources().getIdentifier(entry.icon, "drawable", context.getPackageName());
        if (iconRes != 0) {
            iconView.setImageResource(iconRes);
        }
        iconView.setColorFilter(new PorterDuffColorFilter(section.color, PorterDuff.Mode.SRC_IN));
        tile.addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        container.addView(tile, LayoutHelper.createFrame(38, 38, Gravity.LEFT | Gravity.TOP, 0, 1, 0, 0));
        tiles.add(tile);

        final LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        final TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp));
        titleView.setText(entry.title);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setLineSpacing(dp(1.5f), 1f);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, rp));
        textView.setText(entry.text);
        texts.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        container.addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 52, 0, entry.action != null ? 28 : 0, 0));
        if (entry.action != null) {
            final ImageView chevron = new ImageView(context);
            chevron.setImageResource(R.drawable.arrow_more);
            chevron.setRotation(-90f);
            chevron.setColorFilter(new PorterDuffColorFilter(section.color, PorterDuff.Mode.SRC_IN));
            container.addView(chevron, LayoutHelper.createFrame(22, 22, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        }
        return container;
    }

    /** A row that springs when pressed - only when it leads somewhere, i.e. is clickable. */
    private static class BounceFrameLayout extends FrameLayout {
        private final ButtonBounce bounce = new ButtonBounce(this);

        BounceFrameLayout(Context context) {
            super(context);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (isClickable()) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    bounce.setPressed(true);
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    bounce.setPressed(false);
                }
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            final float s = bounce.getScale(0.02f);
            canvas.save();
            canvas.scale(s, s, getWidth() / 2f, getHeight() / 2f);
            super.dispatchDraw(canvas);
            canvas.restore();
        }
    }

    private static class BounceTextView extends TextView {
        private final ButtonBounce bounce = new ButtonBounce(this);

        BounceTextView(Context context) {
            super(context);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                bounce.setPressed(true);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                bounce.setPressed(false);
            }
            return super.onTouchEvent(event);
        }

        @Override
        public void draw(Canvas canvas) {
            final float s = bounce.getScale(0.04f);
            canvas.save();
            canvas.scale(s, s, getWidth() / 2f, getHeight() / 2f);
            super.draw(canvas);
            canvas.restore();
        }
    }

    /**
     * The version title with a highlight that sweeps across it every few seconds. Only redraws
     * itself while the highlight is moving, and waits out the pause with a single delayed
     * invalidate - not a frame-by-frame loop - so an open sheet costs nothing between sweeps.
     */
    private static class ShimmerTextView extends TextView {
        private static final long SWEEP_MS = 1300;
        private static final long PERIOD_MS = 3600;
        private final boolean animate;
        private final int base;
        private final int accent;
        private final Matrix matrix = new Matrix();
        private LinearGradient gradient;
        private final long start = SystemClock.elapsedRealtime();

        ShimmerTextView(Context context, boolean animate, int base, int accent) {
            super(context);
            this.animate = animate;
            this.base = base;
            this.accent = accent;
            setTextColor(base);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            gradient = w <= 0 ? null : new LinearGradient(0, 0, w * 0.6f, 0,
                    new int[]{base, accent, base}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!animate || gradient == null || !isAttachedToWindow()) {
                getPaint().setShader(null);
                super.onDraw(canvas);
                return;
            }
            final long phase = (SystemClock.elapsedRealtime() - start) % PERIOD_MS;
            if (phase < SWEEP_MS) {
                final float t = phase / (float) SWEEP_MS;
                matrix.setTranslate(-getWidth() * 0.6f + (getWidth() * 1.6f) * t, 0);
                gradient.setLocalMatrix(matrix);
                getPaint().setShader(gradient);
                super.onDraw(canvas);
                postInvalidateOnAnimation();
            } else {
                getPaint().setShader(null);
                super.onDraw(canvas);
                postInvalidateDelayed(PERIOD_MS - phase);
            }
        }
    }
}
