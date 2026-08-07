package org.telegram.messenger;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

/**
 * PrimeGram: the one place an active icon pack actually changes what gets drawn.
 *
 * <p>{@code Resources.getDrawable(int)} - and every convenience wrapper around it, including
 * {@code ContextCompat.getDrawable} and XML inflation - calls {@link #getDrawableForDensity} on
 * modern Android internally. Overriding it here is the same trick exteraGram's own {@code
 * ExteraResources} uses: one method catches nearly every icon load in the app, rather than editing
 * every one of the thousands of {@code R.drawable.*} call sites individually.
 *
 * <p>Installed via {@link org.telegram.ui.LaunchActivity#getResources()}, which is where every
 * fragment's {@code getContext().getResources()} ultimately resolves to.
 */
public final class PrimeResources extends Resources {

    private final Resources original;

    public PrimeResources(Resources original) {
        super(original.getAssets(), original.getDisplayMetrics(), original.getConfiguration());
        this.original = original;
    }

    @Override
    public Drawable getDrawableForDensity(int id, int density, Resources.Theme theme) {
        Drawable stock = null;
        try {
            final String name = getResourceEntryName(id);
            // The stock drawable is what tells a pack icon what pixel size to actually be - most
            // of this codebase's icon draw sites call Drawable.setBounds(0, 0, expectedSize,
            // expectedSize) themselves rather than trusting the drawable's own intrinsic size, and
            // an unscaled pack bitmap (built at whatever resolution its author exported it at, not
            // this app's density grid) painted into those bounds without matching them first drew
            // small and pinned to the bounds' origin - "shrunk into the corner" rather than
            // visibly wrong-sized. Decoding the stock drawable first to read its intrinsic size
            // costs one extra resource lookup only when a pack is active at all.
            stock = original.getDrawableForDensity(id, density, theme);
            final int w = stock != null ? stock.getIntrinsicWidth() : -1;
            final int h = stock != null ? stock.getIntrinsicHeight() : -1;
            final Drawable packIcon = PrimeIconPacks.getIcon(name, w, h);
            if (packIcon != null) {
                return packIcon;
            }
        } catch (Throwable ignore) {
            // A resource id an icon pack was never meant to see - a color, a layout, whatever -
            // falls straight through to the original Resources, same as if no pack were active.
        }
        return stock != null ? stock : original.getDrawableForDensity(id, density, theme);
    }
}
