package org.telegram.ui.Components.primeui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * PrimeGram settings: big tiles, one or two to a row - quick toggles and the section grid.
 *
 * <p>A quick toggle is the shade-panel idea: the few switches people flip often get a large
 * target and show their state by colour, readable at a glance without reading a word. A long
 * press opens where that setting lives, for everything around it. A navigation tile is the same
 * shape without an on/off state and with a live summary line, so the root screen says what is
 * going on inside each section instead of only naming it.
 */
public class PrimeTileRow extends PrimeCardRow {

    /** One tile. Plain data: the row copies nothing out of it and keeps no reference past a bind. */
    public static final class Tile {
        public final int id;
        public final int icon;
        public final int colorTop, colorBottom;
        public final CharSequence title;
        public final CharSequence status;
        /** A toggle shows its state; a navigation tile does not. */
        public final boolean toggle;
        public final boolean checked;

        public Tile(int id, int icon, int colorTop, int colorBottom, CharSequence title, CharSequence status,
                    boolean toggle, boolean checked) {
            this.id = id;
            this.icon = icon;
            this.colorTop = colorTop;
            this.colorBottom = colorBottom;
            this.title = title;
            this.status = status;
            this.toggle = toggle;
            this.checked = checked;
        }

        boolean same(Tile o) {
            return o != null && id == o.id && icon == o.icon && colorTop == o.colorTop && colorBottom == o.colorBottom
                    && toggle == o.toggle && checked == o.checked
                    && TextUtils.equals(title, o.title) && TextUtils.equals(status, o.status);
        }
    }

    public interface Listener {
        void onTile(Tile tile, boolean longPress);
    }

    private static final int TILE_HEIGHT = 96;
    private static final int PAD = 12;
    private static final int TILE_RADIUS = 20;
    private static final int GAP = 10;
    private static final int ICON_SIZE = 32;

    private static TextPaint titlePaint, statusPaint;

    private Tile[] tiles = new Tile[0];
    private Listener listener;

    // Per tile, rebuilt only on bind or size change.
    private final RectF[] rects = {new RectF(), new RectF()};
    private final Drawable[] icons = new Drawable[2];
    private final LinearGradient[] gradients = new LinearGradient[2];
    private final String[] shownTitles = new String[2];
    private final String[] shownStatuses = new String[2];
    private final AnimatedFloat[] on = new AnimatedFloat[2];
    private final AnimatedFloat[] press = new AnimatedFloat[2];

    public PrimeTileRow(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        setPosition(POS_NONE);
        for (int i = 0; i < 2; i++) {
            on[i] = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
            press[i] = new AnimatedFloat(this, 0, 140, CubicBezierInterpolator.EASE_OUT);
        }
        if (titlePaint == null) {
            titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            titlePaint.setTextSize(dp(14));
            titlePaint.setTypeface(AndroidUtilities.bold());
            statusPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            statusPaint.setTextSize(dp(12));
        }
    }

    public void set(Tile[] newTiles, Listener listener, boolean animated) {
        this.listener = listener;
        final Tile[] prev = tiles;
        tiles = newTiles == null ? new Tile[0] : newTiles;
        boolean geometry = prev.length != tiles.length;
        for (int i = 0; i < Math.min(2, tiles.length); i++) {
            final Tile t = tiles[i];
            final Tile old = i < prev.length ? prev[i] : null;
            if (old == null || old.icon != t.icon) {
                final Drawable d = t.icon != 0 ? ContextCompat.getDrawable(getContext(), t.icon) : null;
                icons[i] = d == null ? null : d.mutate();
                if (icons[i] != null) {
                    icons[i].setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
                }
            }
            if (old == null || old.colorTop != t.colorTop || old.colorBottom != t.colorBottom
                    || !TextUtils.equals(old.title, t.title) || !TextUtils.equals(old.status, t.status)) {
                geometry = true;
            }
            on[i].set(t.toggle && t.checked ? 1f : 0f, !animated || old == null || old.id != t.id);
        }
        if (geometry) {
            onGeometryChanged();
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(TILE_HEIGHT) + dp(GAP));
    }

    @Override
    protected void onGeometryChanged() {
        final int width = getMeasuredWidth();
        if (width <= 0) {
            return;
        }
        final int n = Math.min(2, tiles.length);
        final float left = dp(CARD_MARGIN), right = width - dp(CARD_MARGIN);
        final float tileWidth = n <= 1 ? right - left : (right - left - dp(GAP)) / 2f;
        for (int i = 0; i < n; i++) {
            final int visual = rtl() ? n - 1 - i : i;
            final float l = left + visual * (tileWidth + dp(GAP));
            rects[i].set(l, 0, l + tileWidth, dp(TILE_HEIGHT));
            final Tile t = tiles[i];
            gradients[i] = new LinearGradient(rects[i].left, rects[i].top, rects[i].right, rects[i].bottom,
                    t.colorTop, t.colorBottom, Shader.TileMode.CLAMP);
            // Icon on top, text underneath: the text gets the tile's full width, which a row layout
            // on a phone-width half tile did not leave enough of for "Медиа и музыка".
            final float textWidth = tileWidth - dp(PAD) * 2;
            shownTitles[i] = TextUtils.ellipsize(t.title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString();
            shownStatuses[i] = TextUtils.isEmpty(t.status) ? null
                    : TextUtils.ellipsize(t.status, statusPaint, textWidth, TextUtils.TruncateAt.END).toString();
        }
    }

    @Override
    protected void drawContent(Canvas canvas) {
        final int n = Math.min(2, tiles.length);
        final int card = color(Theme.key_windowBackgroundWhite);
        final int textColor = color(Theme.key_windowBackgroundWhiteBlackText);
        final int grayText = color(Theme.key_windowBackgroundWhiteGrayText2);
        final float radius = dp(TILE_RADIUS);
        for (int i = 0; i < n; i++) {
            final RectF r = rects[i];
            final Tile t = tiles[i];
            final float s = on[i].set(t.toggle && t.checked ? 1f : 0f);
            final float p = press[i].set(pressedTarget == i ? 1f : 0f);
            final boolean scaled = p > 0;
            if (scaled) {
                canvas.save();
                final float sc = 1f - 0.035f * p;
                canvas.scale(sc, sc, r.centerX(), r.centerY());
            }

            if (s < 1f) {
                fillPaint.setShader(null);
                fillPaint.setColor(card);
                canvas.drawRoundRect(r, radius, radius, fillPaint);
            }
            if (s > 0f) {
                fillPaint.setShader(gradients[i]);
                fillPaint.setAlpha((int) (255 * s));
                canvas.drawRoundRect(r, radius, radius, fillPaint);
                fillPaint.setShader(null);
                fillPaint.setAlpha(255);
            }

            // Icon: a coloured disc on the card when off, a light disc on the colour when on.
            final float iconLeft = rtl() ? r.right - dp(PAD) - dp(ICON_SIZE) : r.left + dp(PAD);
            final float cy = r.top + dp(PAD) + dp(ICON_SIZE) / 2f;
            final float cx = iconLeft + dp(ICON_SIZE) / 2f;
            if (s < 1f) {
                fillPaint.setShader(gradients[i]);
                fillPaint.setAlpha((int) (255 * (1f - s)));
                canvas.drawCircle(cx, cy, dp(ICON_SIZE) / 2f, fillPaint);
                fillPaint.setShader(null);
                fillPaint.setAlpha(255);
            }
            if (s > 0f) {
                fillPaint.setColor(Theme.multAlpha(0xFFFFFFFF, 0.24f * s));
                canvas.drawCircle(cx, cy, dp(ICON_SIZE) / 2f, fillPaint);
            }
            final Drawable icon = icons[i];
            if (icon != null) {
                final int half = dp(10);
                icon.setBounds((int) cx - half, (int) cy - half, (int) cx + half, (int) cy + half);
                icon.draw(canvas);
            }

            final float textLeft = r.left + dp(PAD);
            final float textRight = r.right - dp(PAD);
            titlePaint.setColor(ColorUtils.blendARGB(textColor, 0xFFFFFFFF, s));
            statusPaint.setColor(ColorUtils.blendARGB(grayText, 0xE6FFFFFF, s));
            final String title = shownTitles[i];
            final String status = shownStatuses[i];
            final float titleY = r.top + dp(PAD) + dp(ICON_SIZE) + dp(10) + dp(14);
            if (title != null) {
                final float tx = rtl() ? textRight - titlePaint.measureText(title) : textLeft;
                canvas.drawText(title, tx, status == null ? titleY + dp(8) : titleY, titlePaint);
            }
            if (status != null) {
                final float sx = rtl() ? textRight - statusPaint.measureText(status) : textLeft;
                canvas.drawText(status, sx, titleY + dp(17), statusPaint);
            }
            if (scaled) {
                canvas.restore();
            }
        }
    }

    /** Whether this row carries the tile with this id. */
    public static boolean contains(UItem item, int tileId) {
        if (item == null || !(item.object instanceof Tile[])) {
            return false;
        }
        for (Tile t : (Tile[]) item.object) {
            if (t.id == tileId) {
                return true;
            }
        }
        return false;
    }

    /** The tile's rectangle in this view's coordinates, or null. */
    public RectF tileBounds(int tileId) {
        for (int i = 0; i < Math.min(2, tiles.length); i++) {
            if (tiles[i].id == tileId) {
                return new RectF(rects[i]);
            }
        }
        return null;
    }

    @Override
    protected int hitTest(float x, float y) {
        for (int i = 0; i < Math.min(2, tiles.length); i++) {
            if (rects[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected boolean supportsLongPress(int target) {
        return true;
    }

    @Override
    protected void onTargetClick(int target, boolean longPress) {
        if (listener != null && target < tiles.length) {
            listener.onTile(tiles[target], longPress);
        }
    }

    public static class Factory extends UItem.UItemFactory<PrimeTileRow> {
        static { setup(new Factory()); }

        @Override
        public PrimeTileRow createView(Context context, RecyclerListView listView, int currentAccount,
                                       int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PrimeTileRow(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final PrimeTileRow row = (PrimeTileRow) view;
            final boolean same = row.getTag() instanceof Integer && (Integer) row.getTag() == item.id;
            row.setTag(item.id);
            row.set((Tile[]) item.object, (Listener) item.object2, same);
        }

        @Override
        public boolean isClickable() {
            return false;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            if (a.id != b.id) {
                return false;
            }
            final Tile[] ta = (Tile[]) a.object, tb = (Tile[]) b.object;
            if (ta == null || tb == null || ta.length != tb.length) {
                return ta == tb;
            }
            for (int i = 0; i < ta.length; i++) {
                if (!ta[i].same(tb[i])) {
                    return false;
                }
            }
            return true;
        }

        /** One row of one or two tiles. */
        public static UItem of(int id, Listener listener, Tile... tiles) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.object = tiles;
            item.object2 = listener;
            return item;
        }
    }
}
