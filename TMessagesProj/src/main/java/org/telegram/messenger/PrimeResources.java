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
        try {
            final String name = getResourceEntryName(id);
            final Drawable packIcon = PrimeIconPacks.getIcon(name);
            if (packIcon != null) {
                return packIcon;
            }
        } catch (Throwable ignore) {
            // A resource id an icon pack was never meant to see - a color, a layout, whatever -
            // falls straight through to the original Resources, same as if no pack were active.
        }
        return original.getDrawableForDensity(id, density, theme);
    }
}
