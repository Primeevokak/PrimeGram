package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.plugins.PluginManifest;
import org.telegram.messenger.plugins.PrimePlugin;
import org.telegram.messenger.plugins.PrimePluginIcons;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: one installed plugin, drawn as a card.
 *
 * <p>The row this replaced was a single line of text with a decorative checkbox - honest about
 * doing less than it looked like, which was the whole complaint. This is what exteraGram's own
 * plugin list looks like, brought over as far as it maps onto something we actually have: a real
 * toggle, a real settings button, a real share, a real delete. Two of their row of icons are
 * missing on purpose rather than by omission - "pin" and "open the author's channel" have nothing
 * behind them here, and a button that does nothing is worse than a button that is not there.
 *
 * <p>A plain {@link LinearLayout} stack rather than manually positioned children: the description
 * wraps to a different number of lines depending on the plugin, and letting the layout system size
 * around that is one line of XML-equivalent code instead of a hand-written two-pass
 * {@code onMeasure}.
 *
 * <p>{@code __icon__} is not artwork bundled with exteraGram - it is a sticker set's short name
 * and an index into it, the same short name a {@code t.me/addstickers/...} link carries. Any
 * Telegram client can resolve that, so the icon shown here is the real sticker, fetched by
 * {@link PrimePluginIcons}. The letter-on-a-coloured-square underneath is what shows while that
 * request is in flight, and what stays if a plugin ships no icon at all or names one that no
 * longer exists - the same fallback the rest of Telegram already uses for a chat with no photo.
 */
public class PrimePluginCardCell extends LinearLayout {

    public interface Listener {
        void onToggle();

        void onOpenSettings();

        void onShare();

        void onDelete();

        /** Only called when the plugin has an error - copies the full traceback, not just the
         *  one-line message the card shows. */
        void onCopyError();
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private Listener listener;

    private final View iconBackground;
    private final TextView iconLetter;
    private final StickerIconView iconSticker;
    private final Switch toggle;
    /** Guards against a slow network reply landing after the cell has been rebound to another
     * plugin - a card scrolled past should not suddenly grow someone else's icon. */
    private int iconRequest;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView descriptionView;
    private final View divider;
    private final ImageView shareButton;
    private final ImageView settingsButton;
    private final ImageView deleteButton;
    private final ImageView copyErrorButton;

    public PrimePluginCardCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(VERTICAL);
        setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider),
                Theme.getColor(Theme.key_listSelector, resourcesProvider)));
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(4));

        // Icon, name+subtitle, switch - one row, three widgets.
        final LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final FrameLayout iconFrame = new FrameLayout(context);
        header.addView(iconFrame, LayoutHelper.createLinear(44, 44));

        iconBackground = new View(context);
        iconFrame.addView(iconBackground, LayoutHelper.createFrame(44, 44));

        iconLetter = new TextView(context);
        iconLetter.setGravity(Gravity.CENTER);
        iconLetter.setTextColor(Color.WHITE);
        iconLetter.setTypeface(AndroidUtilities.bold());
        iconLetter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        iconFrame.addView(iconLetter, LayoutHelper.createFrame(44, 44));

        // On top of the letter, drawing nothing until a sticker actually arrives - the fallback
        // stays visible underneath for exactly as long as it is needed and not a frame longer.
        iconSticker = new StickerIconView(context);
        iconFrame.addView(iconSticker, LayoutHelper.createFrame(44, 44));

        final LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        final LinearLayout.LayoutParams textsParams = LayoutHelper.createLinear(
                0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL);
        textsParams.leftMargin = AndroidUtilities.dp(12);
        textsParams.rightMargin = AndroidUtilities.dp(8);
        header.addView(texts, textsParams);

        titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setMaxLines(1);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        toggle = new Switch(context, resourcesProvider);
        toggle.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked,
                Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        toggle.setFocusable(false);
        header.addView(toggle, LayoutHelper.createLinear(37, 40));
        toggle.setOnClickListener(v -> {
            if (listener != null) {
                listener.onToggle();
            }
        });

        descriptionView = new TextView(context);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionView.setMaxLines(3);
        descriptionView.setEllipsize(TextUtils.TruncateAt.END);
        descriptionView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        final LinearLayout.LayoutParams descParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        descParams.topMargin = AndroidUtilities.dp(10);
        descParams.rightMargin = AndroidUtilities.dp(4);
        addView(descriptionView, descParams);

        divider = new View(context);
        final LinearLayout.LayoutParams dividerParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1);
        dividerParams.topMargin = AndroidUtilities.dp(14);
        dividerParams.rightMargin = AndroidUtilities.dp(4);
        addView(divider, dividerParams);

        final LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.RIGHT);
        addView(actions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        copyErrorButton = addAction(context, actions, R.drawable.msg_copy, "Скопировать ошибку целиком", true, v -> {
            if (listener != null) {
                listener.onCopyError();
            }
        });
        shareButton = addAction(context, actions, R.drawable.msg_share, "Поделиться файлом плагина", false, v -> {
            if (listener != null) {
                listener.onShare();
            }
        });
        settingsButton = addAction(context, actions, R.drawable.msg_edit, "Настройки", false, v -> {
            if (listener != null) {
                listener.onOpenSettings();
            }
        });
        deleteButton = addAction(context, actions, R.drawable.msg_delete, "Удалить", true, v -> {
            if (listener != null) {
                listener.onDelete();
            }
        });

        applyColors();
    }

    private ImageView addAction(Context context, LinearLayout row, int icon, String description, boolean destructive, OnClickListener onClick) {
        final ImageView button = new ImageView(context);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setImageResource(icon);
        button.setContentDescription(description);
        applyActionButtonBackground(button, destructive);
        button.setOnClickListener(onClick);
        applyPressBounce(button);
        row.addView(button, LayoutHelper.createLinear(40, 40));
        return button;
    }

    private void applyActionButtonBackground(ImageView button, boolean destructive) {
        final int fill = destructive
                ? Theme.multAlpha(0xFFE05654, 0.12f)
                : Theme.getColor(Theme.key_listSelector, resourcesProvider);
        button.setBackground(Theme.createSelectorDrawable(fill, Theme.RIPPLE_MASK_CIRCLE_20DP));
    }

    /** exteraGram's own plugin card does this on every icon button: a small bounce-down on
     *  press and a springy overshoot back on release - the same tactile confirmation a real
     *  button press gives, which a flat ripple alone does not. */
    private static void applyPressBounce(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).setInterpolator(null).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(350)
                            .setInterpolator(new OvershootInterpolator(1.5f)).start();
                    break;
            }
            return false;
        });
    }

    private void applyColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        final int iconTint = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
        shareButton.setColorFilter(iconTint);
        settingsButton.setColorFilter(iconTint);
        deleteButton.setColorFilter(0xFFE05654);
        copyErrorButton.setColorFilter(0xFFE05654);
        applyActionButtonBackground(shareButton, false);
        applyActionButtonBackground(settingsButton, false);
        applyActionButtonBackground(deleteButton, true);
        applyActionButtonBackground(copyErrorButton, true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void set(PrimePlugin plugin, CharSequence subtitle, CharSequence description, boolean hasError) {
        final String name = plugin.name() != null && !plugin.name().isEmpty() ? plugin.name() : plugin.id();
        titleView.setText(name);
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);

        descriptionView.setText(TextUtils.isEmpty(description) ? "" : description);
        descriptionView.setVisibility(TextUtils.isEmpty(description) ? GONE : VISIBLE);
        descriptionView.setTypeface(hasError ? Typeface.MONOSPACE : Typeface.DEFAULT);
        descriptionView.setTextColor(hasError ? 0xFFE05654
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));

        toggle.setChecked(plugin.isEnabled(), false);
        toggle.setAlpha(hasError ? 0.4f : 1f);
        // Errored plugins keep the gear too - it is where the full error, a copy button and a
        // retry live now, not just a switch that quietly does nothing.
        descriptionView.setTextIsSelectable(hasError);
        copyErrorButton.setVisibility(hasError ? VISIBLE : GONE);

        final int color = letterColor(plugin.id());
        final GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(AndroidUtilities.dp(12));
        background.setColor(color);
        iconBackground.setBackground(background);
        iconLetter.setText(name.substring(0, 1).toUpperCase());
        iconSticker.setSticker(null);

        final int request = ++iconRequest;
        PrimePluginIcons.resolve(plugin.manifest, sticker -> {
            if (request == iconRequest) {
                iconSticker.setSticker(sticker);
            }
        });
    }

    /** Draws a plugin's icon sticker once it has one; empty (and so transparent) until then.
     *  Public - the plugin install sheet reuses this exact view for the same icon, at a larger
     *  size, so what a plugin's icon looks like before and after installing is the same drawing
     *  code rather than two things that can drift apart. */
    public static final class StickerIconView extends View {

        private final ImageReceiver imageReceiver = new ImageReceiver(this);
        private final int sizeDp;

        public StickerIconView(Context context) {
            this(context, 44);
        }

        public StickerIconView(Context context, int sizeDp) {
            super(context);
            this.sizeDp = sizeDp;
            imageReceiver.setAspectFit(true);
            imageReceiver.setRoundRadius(AndroidUtilities.dp(12));
        }

        public void setSticker(TLRPC.Document sticker) {
            if (sticker == null) {
                imageReceiver.setImageBitmap((android.graphics.drawable.Drawable) null);
                return;
            }
            final String filter = AndroidUtilities.dp(sizeDp) + "_" + AndroidUtilities.dp(sizeDp);
            final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
            final SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(sticker, Theme.key_windowBackgroundGray, 1.0f);
            if (MessageObject.canAutoplayAnimatedSticker(sticker)) {
                imageReceiver.setImage(ImageLocation.getForDocument(sticker), filter, svgThumb, null, sticker, 0);
            } else if (thumb != null) {
                imageReceiver.setImage(ImageLocation.getForDocument(sticker), filter,
                        ImageLocation.getForDocument(thumb, sticker), null, svgThumb, sticker, 0);
            } else {
                imageReceiver.setImage(ImageLocation.getForDocument(sticker), filter, svgThumb, "webp", sticker, 0);
            }
            imageReceiver.setAutoRepeat(1);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            imageReceiver.setImageCoords(0, 0, w, h);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            imageReceiver.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            imageReceiver.draw(canvas);
        }
    }

    /** The same palette Telegram already uses for a chat with no photo, picked by id. */
    private static int letterColor(String id) {
        final int index = Math.abs((id == null ? "" : id).hashCode()) % Theme.keys_avatar_background.length;
        return Theme.getColor(Theme.keys_avatar_background[index]);
    }
}
