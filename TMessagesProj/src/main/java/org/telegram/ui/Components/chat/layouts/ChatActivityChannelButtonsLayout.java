package org.telegram.ui.Components.chat.layouts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundButton;

import java.util.HashSet;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class ChatActivityChannelButtonsLayout extends FrameLayout implements FactorAnimator.Target {
    public static final int BUTTON_SEARCH = 0;
    public static final int BUTTON_GIFT = 1;
    public static final int BUTTON_DIRECT = 2;
    public static final int BUTTON_GIGA_GROUP_INFO = 3;
    public static final int BUTTON_RECENT_ACTIONS_INFO = 4;
    private static final int BUTTONS_COUNT = 5;

    private final ButtonHolder[] buttonHolders = new ButtonHolder[BUTTONS_COUNT];
    private final OnClickListener[] onClickListeners = new OnClickListener[BUTTONS_COUNT];
    private final OnButtonFullyVisibleListener[] onButtonFullyVisible = new OnButtonFullyVisibleListener[BUTTONS_COUNT];
    private OnButtonsTotalWidthChanged onButtonsTotalWidthChanged;
    private final FrameLayout container;

    private final HashSet<View> wrapContentButtons = new HashSet<>();

    private static final @DrawableRes int[] buttonIcons = new int[] {
        R.drawable.msg_search,
        R.drawable.input_gift_s,
        R.drawable.input_message,
        R.drawable.msg_help,
        R.drawable.msg_help
    };
    private static final int[] buttonsOrderLeft = new int[] {
        BUTTON_SEARCH
    };
    private static final int[] buttonsOrderRight = new int[] {
        BUTTON_GIFT,
        BUTTON_DIRECT,
        BUTTON_GIGA_GROUP_INFO,
        BUTTON_RECENT_ACTIONS_INFO
    };

    private final Theme.ResourcesProvider resourcesProvider;
    private final BlurredBackgroundDrawableViewFactory blurredBackgroundDrawableViewFactory;
    private final BlurredBackgroundColorProvider colorProvider;

    public ChatActivityChannelButtonsLayout(@NonNull Context context,
                                            Theme.ResourcesProvider resourcesProvider,
                                            BlurredBackgroundColorProvider colorProvider,
                                            BlurredBackgroundDrawableViewFactory blurredBackgroundDrawableViewFactory) {
        super(context);
        this.blurredBackgroundDrawableViewFactory = blurredBackgroundDrawableViewFactory;
        this.colorProvider = colorProvider;
        this.resourcesProvider = resourcesProvider;

        container = new FrameLayout(context);
        container.setClipToOutline(true);
        container.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, dp(22)));
        addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.CENTER_VERTICAL));
    }

    public void updateColors() {
        for (ButtonHolder holder : buttonHolders) {
            if (holder != null) {
                holder.button.updateColors();
            }
        }
    }

    public FrameLayout getContainer() {
        return container;
    }

    /** Matches the "button" enum values {@code action.ui.set_channel_button_visible} registers in BuiltinActions. */
    private static String overrideKeyFor(int buttonId) {
        switch (buttonId) {
            case BUTTON_SEARCH:
                return "search";
            case BUTTON_GIFT:
                return "gift";
            case BUTTON_DIRECT:
                return "direct";
            default:
                return "channel_button_" + buttonId;
        }
    }

    public void makeViewWrapContent(View view) {
        wrapContentButtons.add(view);
    }

    public void showButton(final int buttonId, boolean show, boolean animated) {
        if (buttonId < 0 || buttonId >= buttonHolders.length) {
            return;
        }

        // PrimeGram Blocks: a script's action.ui.set_channel_button_visible can only ever turn a
        // "show" the chat's own logic already decided on into a "don't show" - never force one on
        // that this call site didn't already want, so this can't desync from real chat state.
        // See PrimeBlocksUiOverrides' own javadoc.
        show = show && org.telegram.messenger.blocks.PrimeBlocksUiOverrides.isVisible(overrideKeyFor(buttonId));

        if (buttonHolders[buttonId] == null && !show) {
            return;
        }

        if (buttonHolders[buttonId] == null) {
            final int animatorId = (buttonId << 16) | VISIBILITY_ANIMATOR_ID;
            final BoolAnimator visibilityAnimator = new BoolAnimator(animatorId, this,
                CubicBezierInterpolator.EASE_OUT_QUINT, 300);

            final ChatActivityBlurredRoundButton button = ChatActivityBlurredRoundButton.create(
                getContext(),
                blurredBackgroundDrawableViewFactory,
                colorProvider,
                resourcesProvider,
                buttonIcons[buttonId],
                48
            );

            if (buttonId == BUTTON_GIFT) {
                button.setContentDescription(getString(R.string.ProfileActionsGift));
            } else if (buttonId == BUTTON_DIRECT) {
                button.setContentDescription(getString(R.string.ChannelOpenDirect));
            } else if (buttonId == BUTTON_SEARCH) {
                button.setContentDescription(getString(R.string.Search));
            } else if (buttonId == BUTTON_GIGA_GROUP_INFO) {
                button.setContentDescription(getString(R.string.BroadcastGroupInfo));
            }

            ScaleStateListAnimator.apply(button, .13f, 2f);
            if (org.telegram.messenger.NonIslandHelper.chatElements()) {
                button.setBlurredBackgroundDrawable(null);
            }
            button.setVisibility(GONE);
            button.setOnClickListener(v -> {
                if (onClickListeners[buttonId] != null) {
                    onClickListeners[buttonId].onClick(v);
                }
            });
            addView(button, LayoutHelper.createFrame(56, 56, Gravity.CENTER_VERTICAL | Gravity.LEFT));

            buttonHolders[buttonId] = new ButtonHolder(button, visibilityAnimator);
            checkButtonsPositionsAndVisibility();
        }

        buttonHolders[buttonId].visibilityAnimator.setValue(show, animated);
    }

    private BlurredBackgroundDrawable containerDrawable;
    public void setupDrawableForContainer() {
        final boolean flat = org.telegram.messenger.NonIslandHelper.chatElements();
        if (flat) {
            container.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, 0));
        }
        containerDrawable = blurredBackgroundDrawableViewFactory.create(this)
            .setColorProvider(colorProvider)
            .setRadius(dp(flat ? 0 : 22))
            .setPadding(dp(flat ? 0 : 6));
    }

    public boolean isButtonVisible(final int buttonId) {
        if (buttonId < 0 || buttonId >= buttonHolders.length || buttonHolders[buttonId] == null) {
            return false;
        }

        return buttonHolders[buttonId].visibilityAnimator.getValue();
    }

    public void setButtonOnClickListener(int buttonId, View.OnClickListener listener) {
        this.onClickListeners[buttonId] = listener;
    }

    public void setButtonOnFullyVisibleListener(int buttonId, OnButtonFullyVisibleListener listener) {
        this.onButtonFullyVisible[buttonId] = listener;
    }

    public void setOnButtonsTotalWidthChanged(OnButtonsTotalWidthChanged onButtonsTotalWidthChanged) {
        this.onButtonsTotalWidthChanged = onButtonsTotalWidthChanged;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        checkButtonsPositionsAndVisibility();
        checkContainerPaddings(false);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkButtonsPositionsAndVisibility();
    }



    private static final int CENTER_ACCENT_BACKGROUND_ANIMATOR_ID = 99;
    private final BoolAnimator animatorCenterAccentBackground = new BoolAnimator(
        CENTER_ACCENT_BACKGROUND_ANIMATOR_ID, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320L
    );
    private static final int WRAPPING_BUTTON_ANIMATOR_ID = 100;
    private final BoolAnimator animatorWrappingButton = new BoolAnimator(
        WRAPPING_BUTTON_ANIMATOR_ID, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320L
    );

    public void setCenterAccentBackground(boolean accent, boolean animated) {
        animatorCenterAccentBackground.setValue(accent, animated);
    }

    private static final int VISIBILITY_ANIMATOR_ID = 1;

    private float totalVisibilityFactor;
    public void setTotalVisibilityFactor(float factor) {
        if (totalVisibilityFactor != factor) {
            totalVisibilityFactor = factor;
            checkButtonsPositionsAndVisibility();
            checkContainerPaddings(true);
            invalidate();
        }
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == CENTER_ACCENT_BACKGROUND_ANIMATOR_ID) {
            invalidate();
            return;
        }
        if (id == WRAPPING_BUTTON_ANIMATOR_ID) {
            checkButtonsPositionsAndVisibility();
            checkContainerPaddings(true);
            invalidate();
        }

        final int buttonId = id >> 16;
        final int animatorId = id & 0xFFFF;
        if (buttonId < 0 || buttonId >= buttonHolders.length || buttonHolders[buttonId] == null) {
            return;
        }

        if (animatorId == VISIBILITY_ANIMATOR_ID) {
            checkButtonsPositionsAndVisibility();
            checkContainerPaddings(true);
            invalidate();
        }
    }

    @Override
    public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
        if (id == CENTER_ACCENT_BACKGROUND_ANIMATOR_ID || id == WRAPPING_BUTTON_ANIMATOR_ID) {
            invalidate();
        }

        final int buttonId = id >> 16;
        final int animatorId = id & 0xFFFF;
        if (buttonId < 0 || buttonId >= buttonHolders.length || buttonHolders[buttonId] == null) {
            return;
        }

        final ButtonHolder holder = buttonHolders[buttonId];
        if (animatorId == VISIBILITY_ANIMATOR_ID) {
            if (holder.visibilityAnimator.getValue()) {
                if (onButtonFullyVisible[buttonId] != null) {
                    onButtonFullyVisible[buttonId].onButtonFullyVisible(holder.button, buttonId, !holder.wasShown);
                }
                holder.wasShown = true;
            }
        }
    }

    private float totalWidthLeft, totalWidthRight;

    // PrimeGram: the single source of truth for where container's pill visually belongs.
    // Horizontal bounds are computed synchronously from totalWidthLeft/totalWidthRight (updated by
    // checkButtonsPositionsAndVisibility(), including its wrap-content-button shrink) and this
    // view's own measured width - never from container.getLeft/Right(), which only reflect
    // wherever container's last COMPLETED layout pass put it. Margin changes applied via
    // requestLayout() take effect on a future traversal, not immediately - so anything reading
    // container's actual horizontal bounds right after a margin change could see stale, wider
    // bounds for however many frames that traversal was delayed, which is exactly the "pill still
    // bulges past where the buttons/icons actually are" ghost this was written to close.
    //
    // Vertical bounds, unlike horizontal, are NOT synchronously derived - container.getTop()/
    // getBottom() are used directly. Nothing here ever changes container's vertical margins or
    // height (only checkContainerPaddings()'s left/rightMargin are touched), so there is no
    // equivalent staleness risk on that axis, and deriving top/bottom from getMeasuredHeight()
    // instead (as an earlier version of this method did) is actively wrong: this view's OWN
    // measured height can legitimately differ, frame to frame, from whatever height was in effect
    // when container was last actually laid out (e.g. while contentPanTranslation/hideFactor pans
    // this view for the keyboard) - producing a same-size pill offset vertically from the real one.
    private void computeContainerRect(RectF out) {
        final float left, right;
        if (org.telegram.messenger.NonIslandHelper.chatElements() && containerDrawable != null) {
            left = 0;
            right = getMeasuredWidth();
        } else {
            left = dp(7) + totalWidthLeft;
            right = getMeasuredWidth() - dp(7) - totalWidthRight;
        }
        out.set(left, container.getTop(), right, container.getBottom());
    }

    private void checkContainerPaddings(boolean canRequestLayout) {
        computeContainerRect(tmpRect);
        final int paddingLeft = Math.round(tmpRect.left);
        final int paddingRight = Math.round(getMeasuredWidth() - tmpRect.right);

        final MarginLayoutParams lp = (MarginLayoutParams) container.getLayoutParams();

        if (lp.leftMargin != paddingLeft || lp.rightMargin != paddingRight) {
            lp.leftMargin = paddingLeft;
            lp.rightMargin = paddingRight;
            if (canRequestLayout) {
                // PrimeGram: requestLayout() alone only SCHEDULES a future traversal - container
                // (and its MATCH_PARENT children, e.g. the "Убрать звук"/"Mute" text) keeps
                // reporting its OLD, wider bounds until that traversal actually runs, which can be
                // a frame or more later during a live button show/hide animation (this runs once
                // per animator tick). Meanwhile computeContainerRect() above is fully synchronous -
                // the pill painted from it in drawChild() already reflects THIS frame's true target.
                // That gap between "pill already at the new bounds" and "container's real children
                // still laid out at the old ones" is exactly the transient ghost/ desync caught
                // mid-animation. Measuring and laying out container immediately, right here, closes
                // it completely: by the time this call returns, container's real bounds already
                // match what was just painted - nothing left to catch up on a later frame.
                if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
                    final int width = getMeasuredWidth() - paddingLeft - paddingRight;
                    final int height = container.getMeasuredHeight() > 0 ? container.getMeasuredHeight() : dp(44);
                    container.measure(
                        View.MeasureSpec.makeMeasureSpec(Math.max(width, 0), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    );
                    final int top = container.getTop();
                    container.layout(paddingLeft, top, paddingLeft + width, top + height);
                } else {
                    container.requestLayout();
                }
            }
        }
    }

    private void checkButtonsPositionsAndVisibility() {
        totalWidthLeft = 0;
        totalWidthRight = 0;

        for (final ButtonHolder holder: buttonHolders) {
            if (holder == null) {
                continue;
            }

            final float visibility = holder.visibilityAnimator.getFloatValue() * totalVisibilityFactor;
            holder.button.setVisibility(visibility > 0 ? VISIBLE : GONE);
            holder.button.setAlpha(visibility);
            holder.button.setScaleX(lerp(0.4f, 1f, visibility));
            holder.button.setScaleY(lerp(0.4f, 1f, visibility));
        }

        for (final int buttonId : buttonsOrderLeft) {
            final ButtonHolder holder = buttonHolders[buttonId];
            if (holder == null) {
                continue;
            }

            final float width = holder.visibilityAnimator.getFloatValue() * dp(44 + 10);    // width + margin
            holder.button.setTranslationX(dp(1) + totalWidthLeft);
            totalWidthLeft += width;
        }

        for (final int buttonId : buttonsOrderRight) {
            final ButtonHolder holder = buttonHolders[buttonId];
            if (holder == null) {
                continue;
            }

            final float width = holder.visibilityAnimator.getFloatValue() * dp(44 + 10);    // width + margin
            holder.button.setTranslationX(getMeasuredWidth() - holder.button.getMeasuredWidth() - dp(1) - totalWidthRight);
            totalWidthRight += width;
        }

        if (totalVisibilityFactor < 1) {
            for (final int buttonId : buttonsOrderLeft) {
                final ButtonHolder holder = buttonHolders[buttonId];
                if (holder == null) {
                    continue;
                }

                holder.button.setTranslationX(holder.button.getTranslationX() - totalWidthLeft * (1 - totalVisibilityFactor));
            }

            for (final int buttonId : buttonsOrderRight) {
                final ButtonHolder holder = buttonHolders[buttonId];
                if (holder == null) {
                    continue;
                }

                holder.button.setTranslationX(holder.button.getTranslationX() + totalWidthRight * (1 - totalVisibilityFactor));
            }

            totalWidthLeft *= totalVisibilityFactor;
            totalWidthRight *= totalVisibilityFactor;
        }

        final float wrapping = animatorWrappingButton.getFloatValue();
        if (wrapping > 0 && getMeasuredWidth() > 0) {
            float left = getMeasuredWidth(), right = 0;
            for (int i = 0; i < getContainer().getChildCount(); ++i) {
                final View child = getContainer().getChildAt(i);
                if (wrapContentButtons.contains(child)) {
                    left  = Math.min(left, child.getLeft());
                    right = Math.max(right, child.getRight());
                }
            }
            if (left > right) {
                left = right = (left + right) / 2f;
            }
            totalWidthLeft = lerp(totalWidthLeft, left - dp(3.33f), wrapping);
            totalWidthRight = lerp(totalWidthRight, getMeasuredWidth() - right - dp(17.66f), wrapping);
        }

        if (onButtonsTotalWidthChanged != null) {
            onButtonsTotalWidthChanged.onButtonsTotalWidthChanged(totalWidthLeft, totalWidthRight);
        }
    }

    public void updateWrappingVisible(boolean animated) {
        boolean hasVisibleWrapping = false;
        if (getVisibility() == View.VISIBLE && getContainer().getVisibility() == View.VISIBLE) {
            for (int i = 0; i < getContainer().getChildCount(); ++i) {
                final View child = getContainer().getChildAt(i);
                if (wrapContentButtons.contains(child) && child.getVisibility() == View.VISIBLE) {
                    hasVisibleWrapping = true;
                }
            }
        }
        animatorWrappingButton.setValue(hasVisibleWrapping, animated);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        updateWrappingVisible(false);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        checkButtonsPositionsAndVisibility();
        // PrimeGram: checkButtonsPositionsAndVisibility() just recomputed totalWidthLeft/Right
        // (used LIVE by computeContainerRect()/the pill in drawChild()) against THIS layout pass's
        // fresh getMeasuredWidth() - but nothing here told checkContainerPaddings() to re-derive
        // container's own margins from that same fresh value. Every other call site that touches
        // totalWidthLeft/Right already re-syncs immediately after (onMeasure, the animator
        // callbacks, setTotalVisibilityFactor); onLayout() was the one place that didn't, so
        // container's real margins could go stale relative to what the pill was already painting
        // as soon as any ordinary layout pass (rotation, keyboard, an ancestor relayout - not just
        // this view's own button animations) ran without also happening to hit one of those other
        // call sites - exactly the "pill wider than the real button, off by an inconsistent amount"
        // ghost, confirmed by comparing the inspector's real container rect against the painted one.
        checkContainerPaddings(true);

        // PrimeGram: containerDrawable is painted manually inside drawChild(), from container's
        // CURRENT bounds at the moment drawChild() happens to run - it is not part of the normal
        // View invalidation graph, so the platform has no idea it needs repainting whenever
        // container's own bounds change. Without this, containerDrawable only repaints when this
        // view happens to redraw for some unrelated reason, and otherwise stays visually stuck at
        // wherever it was last painted while container (and its text) moves to its new layout -
        // exactly the "text moved, pill background didn't" ghost. onLayout() is called every time
        // container's bounds are actually finalized, so forcing a redraw here keeps them in sync.
        invalidate();
    }

    public interface OnButtonsTotalWidthChanged {
        void onButtonsTotalWidthChanged(float left, float right);
    }

    private static final RectF tmpRect = new RectF();
    private final Paint backgroundAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface OnButtonFullyVisibleListener {
        void onButtonFullyVisible(View v, int buttonId, boolean firstTime);
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (child == container && containerDrawable != null) {
            computeContainerRect(tmpRect);
            if (!org.telegram.messenger.NonIslandHelper.chatElements()) {
                // PrimeGram: BlurredBackgroundDrawable insets its own visible shape INWARD from
                // setBounds() by its configured padding (see Props.build(), boundsWithPadding =
                // bounds.inset(padding, padding)) - setupDrawableForContainer() sets that padding
                // to dp(6). So setBounds() must be given container's real bounds EXPANDED by that
                // same dp(6) on every edge, or the painted pill ends up dp(6) smaller than
                // container (and its text) on every side instead of matching it.
                final int pad = dp(6);
                tmpRect.inset(-pad, -pad);
            }

            tmpRect.round(AndroidUtilities.rectTmp2);
            containerDrawable.setBounds(AndroidUtilities.rectTmp2);
            containerDrawable.draw(canvas);
            org.telegram.messenger.PrimeUiInspector.recordManualDraw(this, "containerDrawable (pill background)",
                    tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom);
        }

        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final int accentAlpha = (int) (255 * totalVisibilityFactor * animatorCenterAccentBackground.getFloatValue());
        if (accentAlpha > 0) {
            // PrimeGram: this used to be computed independently from totalWidthLeft/Right +
            // dp(10)/dp(9) against this view's own (56dp) height, never in sync with container's
            // real (44dp, dp(7)-margin) bounds - a second, permanently oversized rounded-rect
            // ghost behind the actual pill, untouched by any of the containerDrawable fixes above
            // since it's a completely separate draw call. Match the same computed rect instead.
            computeContainerRect(tmpRect);
            backgroundAccentPaint.setColor(accentColor);
            backgroundAccentPaint.setAlpha(accentAlpha);
            canvas.drawRoundRect(tmpRect, dp(22), dp(22), backgroundAccentPaint);
            org.telegram.messenger.PrimeUiInspector.recordManualDraw(this, "accent background",
                    tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom);
        }

        super.dispatchDraw(canvas);
    }

    private int accentColor = 0;

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
    }

    private static class ButtonHolder {
        public final ChatActivityBlurredRoundButton button;
        public final BoolAnimator visibilityAnimator;
        public boolean wasShown;

        private ButtonHolder(ChatActivityBlurredRoundButton button, BoolAnimator visibilityAnimator) {
            this.button = button;
            this.visibilityAnimator = visibilityAnimator;
        }
    }
}
