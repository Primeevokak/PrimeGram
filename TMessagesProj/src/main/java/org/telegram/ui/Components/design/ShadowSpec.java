package org.telegram.ui.Components.design;

/**
 * PrimeGram: one role's drop-shadow, as plain numbers a {@link DesignSystem} hands back instead
 * of every call site building its own {@code Paint.setShadowLayer(...)} with its own hardcoded
 * blur/offset - which is how shadows are painted everywhere in the app today (see
 * {@code Theme.java}'s own ad hoc 9-patch shadow cache and one-off {@code setShadowLayer} calls).
 */
public final class ShadowSpec {

    public static final ShadowSpec NONE = new ShadowSpec(0, 0, 0, 0);

    public final float radiusDp;
    public final float dxDp;
    public final float dyDp;
    /** 0-255. */
    public final int alpha;

    public ShadowSpec(float radiusDp, float dxDp, float dyDp, int alpha) {
        this.radiusDp = radiusDp;
        this.dxDp = dxDp;
        this.dyDp = dyDp;
        this.alpha = alpha;
    }
}
