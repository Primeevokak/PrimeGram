package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.blocks.BlockType;
import org.telegram.messenger.blocks.ParamSpec;
import org.telegram.ui.ActionBar.Theme;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram Blocks: one Canvas-drawn "puzzle piece" row in the script stack - the replacement for
 * v1's plain {@code UItem.asButton} text rows (see the "Editor Redesign v2" plan §2).
 *
 * <p>Reads render state through {@link #bind}, not through {@code BlockNode}/{@code BlockType}
 * directly, so this view stays a dumb renderer the editor feeds - the same separation
 * {@link org.telegram.ui.Cells.PrimeSliderCell} keeps from whatever setting it happens to be
 * showing.
 *
 * <p>Does not listen to its own touches: {@code RecyclerListView} already resolves row taps and
 * hands the editor an (x, y) local to this view (see {@code UniversalFragment#onClick}), so the
 * editor calls {@link #hitTestParam(float, float)} itself to tell a pill tap from a plain chip
 * tap - one hit-test path instead of two competing touch handlers.
 */
public class PrimeBlockChipView extends View {

    private static final class PillBounds {
        final ParamSpec spec;
        final RectF rect;

        PillBounds(ParamSpec spec, RectF rect) {
            this.spec = spec;
            this.rect = rect;
        }
    }

    private final Theme.ResourcesProvider resourcesProvider;

    private String label = "";
    private BlockType.Category category = BlockType.Category.ACTION;
    private int depth = 0;
    private List<ParamSpec> params = new ArrayList<>();
    private JSONObject paramValues = new JSONObject();
    private boolean unsupported = false;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF chipRect = new RectF();
    private final RectF accentRect = new RectF();
    private final List<PillBounds> pills = new ArrayList<>();

    public PrimeBlockChipView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeWidth(dp(3));
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        labelPaint.setTextSize(dp(15));
        labelPaint.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        pillTextPaint.setTextSize(dp(12));
    }

    public void bind(String label, BlockType.Category category, int depth, List<ParamSpec> params,
                      JSONObject paramValues, boolean unsupported) {
        this.label = label != null ? label : "";
        this.category = category != null ? category : BlockType.Category.ACTION;
        this.depth = Math.max(0, depth);
        this.params = params != null ? params : new ArrayList<>();
        this.paramValues = paramValues != null ? paramValues : new JSONObject();
        this.unsupported = unsupported;
        requestLayout();
        invalidate();
    }

    private boolean hasVisiblePills() {
        if (unsupported) {
            return false;
        }
        for (ParamSpec spec : params) {
            if (paramValues.has(spec.key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int height = hasVisiblePills() ? dp(60) : dp(44);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    private int categoryColor() {
        if (unsupported) {
            return IconBackgroundColors.GRAY.top;
        }
        return PrimeBlockPaletteSheet.categoryColor(category);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        pills.clear();
        final int accent = categoryColor();
        final int surface = PrimeSettingsUi.surfaceColor();
        final int outline = PrimeSettingsUi.outlineColor();

        final float indent = dp(18) * depth;
        for (int level = 0; level < depth; level++) {
            final float x = dp(9) + dp(18) * level;
            barPaint.setColor(ColorUtils.setAlphaComponent(IconBackgroundColors.PURPLE.top, 110));
            canvas.drawLine(x, 0, x, getHeight(), barPaint);
        }

        chipRect.set(indent + dp(4), dp(3), getWidth() - dp(10), getHeight() - dp(3));
        fillPaint.setColor(ColorUtils.blendARGB(surface, accent, 0.16f));
        if (unsupported) {
            fillPaint.setAlpha((int) (fillPaint.getAlpha() * 0.6f));
        }
        canvas.drawRoundRect(chipRect, dp(10), dp(10), fillPaint);
        strokePaint.setColor(ColorUtils.blendARGB(outline, accent, 0.3f));
        canvas.drawRoundRect(chipRect, dp(10), dp(10), strokePaint);

        accentRect.set(chipRect.left, chipRect.top, chipRect.left + dp(4), chipRect.bottom);
        accentPaint.setColor(accent);
        canvas.drawRoundRect(accentRect, dp(2), dp(2), accentPaint);

        final float textX = chipRect.left + dp(14);
        labelPaint.setColor(Theme.getColor(unsupported ? Theme.key_windowBackgroundWhiteGrayText
                : Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        final boolean showPills = hasVisiblePills();
        final float labelY = showPills ? chipRect.top + dp(21) : chipRect.top + (chipRect.height() / 2f) + dp(5);
        canvas.drawText(label, textX, labelY, labelPaint);

        if (showPills) {
            float pillX = textX;
            final float pillY = chipRect.top + dp(32);
            for (ParamSpec spec : params) {
                if (!paramValues.has(spec.key)) {
                    continue;
                }
                final String text = spec.label + ": " + paramValues.opt(spec.key);
                final float textWidth = pillTextPaint.measureText(text);
                final RectF pillRect = new RectF(pillX, pillY, pillX + textWidth + dp(16), pillY + dp(22));
                if (pillRect.right > chipRect.right - dp(6)) {
                    break;
                }
                pillFillPaint.setColor(ColorUtils.setAlphaComponent(accent, 46));
                canvas.drawRoundRect(pillRect, dp(11), dp(11), pillFillPaint);
                pillTextPaint.setColor(accent);
                canvas.drawText(text, pillRect.left + dp(8), pillRect.top + dp(15), pillTextPaint);
                pills.add(new PillBounds(spec, new RectF(pillRect)));
                pillX = pillRect.right + dp(6);
            }
        }
    }

    /** Local coordinates, exactly as {@code UniversalFragment#onClick}'s (x, y) params are. */
    public ParamSpec hitTestParam(float x, float y) {
        for (PillBounds pill : pills) {
            if (pill.rect.contains(x, y)) {
                return pill.spec;
            }
        }
        return null;
    }
}
