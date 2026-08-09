package org.telegram.ui.Components.design;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Bitmap;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;

/**
 * PrimeGram: the same snapshot -> swap -> circular reveal -> discard technique
 * {@code LaunchActivity} already uses for day/night and custom-theme switches (see its
 * {@code needSetDayNightTheme} handling), pulled out as a small reusable piece instead of reused
 * in place.
 *
 * <p>Not literally wired into that existing code path: it is built around a Lottie sun/moon icon
 * morph specific to day/night, whose iconography would not make sense for switching Flat to
 * Material. Reusing the technique rather than the exact method keeps this addition from touching
 * a stable, constantly-used feature at all - zero risk to it - while still being the same visual
 * language, not a new one.
 */
public final class DesignSwitchAnimator {

    private DesignSwitchAnimator() {
    }

    /**
     * @param root       the view whose current appearance is snapshotted before {@code applyChange} runs.
     * @param centerX    reveal origin, in {@code root}'s own coordinates - typically the tapped view's center.
     * @param centerY    reveal origin, in {@code root}'s own coordinates.
     * @param applyChange runs once, right after the snapshot is taken, before the reveal starts.
     */
    public static void run(ViewGroup root, int centerX, int centerY, Runnable applyChange) {
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) {
            applyChange.run();
            return;
        }

        final Bitmap bitmap = AndroidUtilities.snapshotView(root);
        applyChange.run();
        if (bitmap == null) {
            return;
        }

        final ImageView overlay = new ImageView(root.getContext());
        overlay.setImageBitmap(bitmap);
        root.addView(overlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final int w = root.getWidth();
        final int h = root.getHeight();
        final float finalRadius = (float) Math.max(
                Math.hypot(w - centerX, h - centerY),
                Math.hypot(centerX, centerY));

        final Animator anim = ViewAnimationUtils.createCircularReveal(overlay, centerX, centerY, finalRadius, 0f);
        anim.setDuration(200);
        anim.setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (overlay.getParent() == root) {
                    root.removeView(overlay);
                }
            }
        });
        anim.start();
    }
}
