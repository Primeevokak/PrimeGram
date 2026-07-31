package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.PrimeTweaks;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: a real sticker at exactly the size the setting will give it.
 *
 * <p>The first attempt at this reused the theme preview, which draws two text bubbles and no
 * sticker at all - so the one control it sat above was the one thing it could not show. This draws
 * a sticker, and its height is the computed sticker height, so dragging the slider moves the whole
 * row. That movement is the feedback; a picture that stays the same size while a number changes
 * tells you nothing.
 *
 * <p>The size formula is copied from {@code ChatMessageCell}, not approximated. A preview that is
 * nearly right is worse than none: it would send people looking for a setting that is already
 * correct.
 */
public class PrimeStickerPreviewCell extends View {

    private final Theme.ResourcesProvider resourcesProvider;
    private final ImageReceiver imageReceiver = new ImageReceiver(this);
    private final TextPaint placeholderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint placeholderFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private TLRPC.Document sticker;
    private Object parentObject;
    private int stickerSizePx;

    public PrimeStickerPreviewCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        imageReceiver.setAspectFit(true);
        placeholderPaint.setTextSize(AndroidUtilities.dp(13));
        placeholderPaint.setTextAlign(Paint.Align.CENTER);
        placeholderFill.setStyle(Paint.Style.STROKE);
        placeholderFill.setStrokeWidth(AndroidUtilities.dp(1.5f));
        findSticker();
    }

    /**
     * Whatever sticker the user actually has: their recent ones first, then the first installed
     * set. Someone with no stickers at all still gets the size box, which is what the preview is
     * for - the picture inside it is illustration.
     */
    private void findSticker() {
        final int account = UserConfig.selectedAccount;
        final MediaDataController controller = MediaDataController.getInstance(account);
        try {
            final java.util.ArrayList<TLRPC.Document> recent =
                    controller.getRecentStickers(MediaDataController.TYPE_IMAGE);
            if (recent != null && !recent.isEmpty()) {
                sticker = recent.get(0);
                parentObject = "recent";
                return;
            }
            final java.util.ArrayList<TLRPC.TL_messages_stickerSet> sets =
                    controller.getStickerSets(MediaDataController.TYPE_IMAGE);
            if (sets != null) {
                for (int i = 0; i < sets.size(); i++) {
                    final TLRPC.TL_messages_stickerSet set = sets.get(i);
                    if (set != null && set.documents != null && !set.documents.isEmpty()) {
                        sticker = set.documents.get(0);
                        parentObject = set;
                        return;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /** The size {@code ChatMessageCell} will give a sticker, in pixels. */
    private int computeSize() {
        final int scale = PrimeTweaks.stickerSize();
        if (AndroidUtilities.isTablet()) {
            return (int) (AndroidUtilities.getMinTabletSide() * (scale / 35f));
        }
        final int width = getMeasuredWidth() > 0 ? getMeasuredWidth() : AndroidUtilities.displaySize.x;
        return (int) (Math.min(width, AndroidUtilities.displaySize.y) * (scale / 28f));
    }

    /** Re-reads the setting. Called as the slider moves. */
    public void update() {
        final int size = computeSize();
        if (size != stickerSizePx) {
            stickerSizePx = size;
            loadImage();
            requestLayout();
        }
        invalidate();
    }

    private void loadImage() {
        if (sticker == null || stickerSizePx <= 0) {
            return;
        }
        final String filter = stickerSizePx + "_" + stickerSizePx;
        final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
        final SvgHelper.SvgDrawable svgThumb =
                DocumentObject.getSvgThumb(sticker, Theme.key_windowBackgroundGray, 1.0f, 1f, resourcesProvider);
        // ImageReceiver's overloads are not BackupImageView's: the extension and cache type are
        // explicit here, and an animated sticker must not be asked for as "webp".
        if (MessageObject.canAutoplayAnimatedSticker(sticker)) {
            imageReceiver.setImage(ImageLocation.getForDocument(sticker), filter,
                    svgThumb, null, parentObject, 0);
        } else if (thumb != null) {
            imageReceiver.setImage(ImageLocation.getForDocument(sticker), filter,
                    ImageLocation.getForDocument(thumb, sticker), null, svgThumb, parentObject, 0);
        } else {
            imageReceiver.setImage(ImageLocation.getForDocument(sticker), filter,
                    svgThumb, "webp", parentObject, 0);
        }
        imageReceiver.setAutoRepeat(1);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, 0);
        stickerSizePx = computeSize();
        if (imageReceiver.getImageLocation() == null) {
            loadImage();
        }
        // The whole row is the sticker plus breathing room, so its height is the answer to the
        // question the slider is asking.
        setMeasuredDimension(width, stickerSizePx + AndroidUtilities.dp(28));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final Drawable wallpaper = Theme.getCachedWallpaperNonBlocking();
        if (wallpaper != null) {
            wallpaper.setBounds(0, 0, getWidth(), getHeight());
            wallpaper.draw(canvas);
        } else {
            canvas.drawColor(Theme.getColor(Theme.key_chat_wallpaper, resourcesProvider));
        }

        // Placed like an outgoing message - against the right edge - because that is where the
        // user's own stickers appear, and the setting is about their own outgoing ones too.
        final int size = Math.max(AndroidUtilities.dp(40), stickerSizePx);
        final float right = getWidth() - AndroidUtilities.dp(12);
        final float top = AndroidUtilities.dp(14);

        if (sticker != null) {
            imageReceiver.setImageCoords(right - size, top, size, size);
            imageReceiver.draw(canvas);
        } else {
            placeholderFill.setColor(Theme.getColor(Theme.key_chat_serviceText, resourcesProvider));
            placeholderFill.setAlpha(90);
            rect.set(right - size, top, right, top + size);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), placeholderFill);
            placeholderPaint.setColor(Theme.getColor(Theme.key_chat_serviceText, resourcesProvider));
            canvas.drawText("Стикер", rect.centerX(), rect.centerY() + AndroidUtilities.dp(5), placeholderPaint);
        }
    }
}
