package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Random;

/**
 * PrimeGram: what a tap on a name badge opens - a plain {@code AlertDialog.setMessage(...)} did
 * the job but read as an afterthought next to the badge itself, which is an animated emoji. This
 * gives the emoji the same kind of small stage Telegram gives an emoji status or a gift: a glow
 * that breathes behind it, a handful of drifting sparkles, and a bounce on entrance instead of a
 * dialog just appearing.
 */
public class PrimeBadgeInfoSheet extends BottomSheet {

    public PrimeBadgeInfoSheet(Context context, long customEmojiId, CharSequence title, CharSequence text, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        setApplyTopPadding(false);

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(16));

        final StageView stage = new StageView(context, customEmojiId);
        root.addView(stage, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        final TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleView.setText(title);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        final TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        textView.setGravity(Gravity.CENTER);
        textView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        textView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
        textView.setText(text);
        root.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 4));

        final TextView button = new TextView(context);
        button.setText(LocaleController.getString(R.string.OK));
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        button.setBackground(Theme.AdaptiveRipple.filledRect(getThemedColor(Theme.key_featuredStickers_addButton), AndroidUtilities.dp(8)));
        button.setOnClickListener(v -> dismiss());
        root.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 20, 0, 0));

        setCustomView(root);

        // Staggered entrance: the stage bounces in first, the text rises in a beat behind it -
        // arriving all at once reads as a dialog; arriving in sequence reads as composed.
        root.setAlpha(0f);
        titleView.setAlpha(0f);
        textView.setAlpha(0f);
        button.setAlpha(0f);
        titleView.setTranslationY(AndroidUtilities.dp(8));
        textView.setTranslationY(AndroidUtilities.dp(8));
        button.setTranslationY(AndroidUtilities.dp(8));
        root.post(() -> {
            root.setAlpha(1f);
            stage.playEntrance();
            titleView.animate().alpha(1f).translationY(0).setStartDelay(90).setDuration(220).start();
            textView.animate().alpha(1f).translationY(0).setStartDelay(140).setDuration(220).start();
            button.animate().alpha(1f).translationY(0).setStartDelay(190).setDuration(220).start();
        });
    }

    /** Glow + sparkles + the emoji itself, all in one view so they share one invalidate loop. */
    private static class StageView extends View {

        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<Sparkle> sparkles = new ArrayList<>();
        private final Random random = new Random();

        private float glowPhase;
        private float entranceScale;
        private long lastFrameTime;

        private static final class Sparkle {
            float angle, distance, size, speed, phase;
        }

        StageView(Context context, long customEmojiId) {
            super(context);
            emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, AndroidUtilities.dp(64), AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS);
            emoji.set(customEmojiId, false);
            emoji.setColorFilter(null);

            sparklePaint.setColor(0xFFFFD700);
            for (int i = 0; i < 7; i++) {
                final Sparkle sparkle = new Sparkle();
                sparkle.angle = random.nextFloat() * 360f;
                sparkle.distance = AndroidUtilities.dp(38) + random.nextFloat() * AndroidUtilities.dp(14);
                sparkle.size = AndroidUtilities.dp(1.5f) + random.nextFloat() * AndroidUtilities.dp(1.5f);
                sparkle.speed = 12f + random.nextFloat() * 18f;
                sparkle.phase = random.nextFloat() * (float) Math.PI * 2f;
                sparkles.add(sparkle);
            }
        }

        void playEntrance() {
            final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(480);
            animator.setInterpolator(new OvershootInterpolator(2.2f));
            animator.addUpdateListener(a -> {
                entranceScale = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            emoji.attach();
            lastFrameTime = System.currentTimeMillis();
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            emoji.detach();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final long now = System.currentTimeMillis();
            final float dt = Math.min(0.05f, (now - lastFrameTime) / 1000f);
            lastFrameTime = now;
            glowPhase += dt;

            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;

            // Breathing glow behind the emoji.
            final float pulse = 0.85f + 0.15f * (float) Math.sin(glowPhase * 1.6);
            final float glowRadius = AndroidUtilities.dp(52) * pulse * entranceScale;
            if (glowRadius > 0) {
                glowPaint.setShader(new RadialGradient(cx, cy, glowRadius, 0x33FFD700, 0x00FFD700, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, glowRadius, glowPaint);
            }

            // Sparkles drifting around the glow, each on its own slow orbit.
            for (Sparkle sparkle : sparkles) {
                final float t = glowPhase * sparkle.speed + sparkle.phase;
                final float wobble = (float) Math.sin(t * 0.7) * AndroidUtilities.dp(4);
                final double rad = Math.toRadians(sparkle.angle + t * 6f);
                final float sx = cx + (float) Math.cos(rad) * (sparkle.distance + wobble) * entranceScale;
                final float sy = cy + (float) Math.sin(rad) * (sparkle.distance + wobble) * entranceScale;
                final float alpha = 0.35f + 0.35f * (float) Math.sin(t * 1.3);
                sparklePaint.setAlpha((int) (alpha * 255) * (entranceScale > 0.05f ? 1 : 0));
                canvas.drawCircle(sx, sy, sparkle.size * entranceScale, sparklePaint);
            }

            // The emoji itself, scaled in with the entrance overshoot.
            final int size = AndroidUtilities.dp(64);
            final int half = size / 2;
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(entranceScale, entranceScale);
            emoji.setBounds(-half, -half, half, half);
            emoji.draw(canvas);
            canvas.restore();

            invalidate();
        }
    }
}
