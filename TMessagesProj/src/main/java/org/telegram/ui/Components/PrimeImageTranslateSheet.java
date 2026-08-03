package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.PrimeTranslator;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TranslateAlert2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: "translate this image" - on-device OCR (ML Kit, offline, no key) finds the text,
 * {@link PrimeTranslator}'s keyless engines translate each line, and the result is drawn back
 * over the spot the original text occupied, background-matched so it reads as a replacement
 * rather than a caption. No text-recognizing translator is wired into this fork, so this is the
 * OCR-then-translate-then-overlay path exteraGram's own image search inspired the idea for.
 */
public class PrimeImageTranslateSheet extends BottomSheet {

    private final OverlayView overlayView;
    private final ProgressBar spinner;
    private final TextView statusView;

    public PrimeImageTranslateSheet(Context context, File file, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        useBackgroundTopPadding = false;

        int actionBarHeight = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec((int) (AndroidUtilities.displaySize.y * 0.85f), View.MeasureSpec.EXACTLY));
            }
        };
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        overlayView = new OverlayView(context);
        overlayView.setVisibility(View.INVISIBLE);
        frameLayout.addView(overlayView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, actionBarHeight / AndroidUtilities.density, 0, 0));

        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        frameLayout.addView(spinner, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        statusView = new TextView(context);
        statusView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        statusView.setTextSize(14);
        statusView.setGravity(Gravity.CENTER);
        statusView.setVisibility(View.GONE);
        frameLayout.addView(statusView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));

        ActionBar actionBar = new ActionBar(context, resourcesProvider);
        actionBar.setOccupyStatusBar(true);
        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setBackButtonImage(R.drawable.ic_close_white);
        actionBar.setTitle("Перевод изображения");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    dismiss();
                }
            }
        });
        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, actionBarHeight / AndroidUtilities.density));

        setCustomView(frameLayout);

        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            final Bitmap bitmap = decodeBitmap(file);
            AndroidUtilities.runOnUIThread(() -> {
                if (bitmap == null) {
                    showError("Не удалось открыть изображение");
                    return;
                }
                recognizeAndTranslate(bitmap);
            });
        });
    }

    private void showError(String text) {
        spinner.setVisibility(View.GONE);
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(text);
    }

    /** Downscaled to a size that keeps OCR accurate on real photographed text without turning a
     *  4000px camera shot into a multi-megabyte in-memory bitmap. */
    private static Bitmap decodeBitmap(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1920) {
                sample *= 2;
            }
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private void recognizeAndTranslate(Bitmap bitmap) {
        final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        final InputImage input = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(input)
                .addOnSuccessListener(visionText -> onTextRecognized(bitmap, visionText))
                .addOnFailureListener(e -> {
                    FileLog.e(e);
                    showError("Не удалось распознать текст на изображении");
                });
    }

    private void onTextRecognized(Bitmap bitmap, Text visionText) {
        final List<Text.Line> lines = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            lines.addAll(block.getLines());
        }
        if (lines.isEmpty()) {
            overlayView.setBitmap(bitmap, null);
            overlayView.setVisibility(View.VISIBLE);
            spinner.setVisibility(View.GONE);
            showError("Текст на изображении не найден");
            statusView.setVisibility(View.VISIBLE);
            return;
        }

        final ArrayList<TLRPC.TL_textWithEntities> texts = new ArrayList<>(lines.size());
        for (Text.Line line : lines) {
            final TLRPC.TL_textWithEntities t = new TLRPC.TL_textWithEntities();
            t.text = line.getText();
            texts.add(t);
        }
        final String targetLang = TranslateAlert2.getToLanguage();
        PrimeTranslator.translate(texts, targetLang, (response, error) -> {
            final ArrayList<OverlayBlock> overlays = new ArrayList<>();
            if (response instanceof TLRPC.TL_messages_translateResult) {
                final TLRPC.TL_messages_translateResult result = (TLRPC.TL_messages_translateResult) response;
                for (int i = 0; i < lines.size() && i < result.result.size(); i++) {
                    final Rect box = lines.get(i).getBoundingBox();
                    final String translated = result.result.get(i).text;
                    if (box != null && !TextUtils.isEmpty(translated)) {
                        overlays.add(new OverlayBlock(new RectF(box), translated));
                    }
                }
            }
            spinner.setVisibility(View.GONE);
            if (overlays.isEmpty()) {
                showError("Не удалось перевести распознанный текст");
            }
            overlayView.setBitmap(bitmap, overlays);
            overlayView.setVisibility(View.VISIBLE);
        });
    }

    private static class OverlayBlock {
        final RectF rect;
        final String text;

        OverlayBlock(RectF rect, String text) {
            this.rect = rect;
            this.text = text;
        }
    }

    /** Draws the source bitmap scaled to fit, then paints each translated line back over the
     *  spot its original occupied - background colour sampled from the bitmap under that spot,
     *  so it reads as "the text changed" rather than "a caption was pasted on top". */
    private static class OverlayView extends View {
        private Bitmap bitmap;
        private List<OverlayBlock> overlays;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Matrix matrix = new android.graphics.Matrix();
        private float scale, offsetX, offsetY;

        OverlayView(Context context) {
            super(context);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(AndroidUtilities.bold());
        }

        void setBitmap(Bitmap bitmap, List<OverlayBlock> overlays) {
            this.bitmap = bitmap;
            this.overlays = overlays;
            requestLayout();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            computeTransform();
        }

        private void computeTransform() {
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
                return;
            }
            scale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
            offsetX = (getWidth() - bitmap.getWidth() * scale) / 2f;
            offsetY = (getHeight() - bitmap.getHeight() * scale) / 2f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (bitmap == null) {
                return;
            }
            if (scale == 0) {
                computeTransform();
            }
            canvas.save();
            canvas.translate(offsetX, offsetY);
            canvas.scale(scale, scale);
            canvas.drawBitmap(bitmap, 0, 0, null);

            if (overlays != null) {
                for (OverlayBlock overlay : overlays) {
                    drawOverlay(canvas, overlay);
                }
            }
            canvas.restore();
        }

        private void drawOverlay(Canvas canvas, OverlayBlock overlay) {
            final RectF r = overlay.rect;
            if (r.width() <= 0 || r.height() <= 0) {
                return;
            }
            bgPaint.setColor(averageColor(bitmap, r));
            final float pad = Math.min(r.width(), r.height()) * 0.08f;
            canvas.drawRoundRect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad, pad, pad, bgPaint);

            textPaint.setColor(bestTextColorFor(bgPaint.getColor()));
            float textSize = r.height() * 0.72f;
            StaticLayout layout = null;
            final int maxWidth = Math.max(1, Math.round(r.width()));
            while (textSize > 6) {
                textPaint.setTextSize(textSize);
                layout = StaticLayout.Builder.obtain(overlay.text, 0, overlay.text.length(), textPaint, maxWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0, 1f)
                        .build();
                if (layout.getHeight() <= r.height() * 1.4f) {
                    break;
                }
                textSize -= 1;
            }
            if (layout == null) {
                return;
            }
            canvas.save();
            canvas.translate(r.left, r.centerY() - layout.getHeight() / 2f);
            layout.draw(canvas);
            canvas.restore();
        }

        /** Coarse average over a downsampled grid rather than every pixel - this runs once per
         *  overlay per frame, and a 6x6 sample reads close enough to the true average for a
         *  background patch nobody is meant to study closely. */
        private static int averageColor(Bitmap bitmap, RectF rect) {
            final int steps = 6;
            long r = 0, g = 0, b = 0;
            int count = 0;
            final int left = Math.max(0, (int) rect.left);
            final int top = Math.max(0, (int) rect.top);
            final int right = Math.min(bitmap.getWidth() - 1, (int) rect.right);
            final int bottom = Math.min(bitmap.getHeight() - 1, (int) rect.bottom);
            if (right <= left || bottom <= top) {
                return Color.DKGRAY;
            }
            for (int i = 0; i < steps; i++) {
                for (int j = 0; j < steps; j++) {
                    final int x = left + (right - left) * i / Math.max(1, steps - 1);
                    final int y = top + (bottom - top) * j / Math.max(1, steps - 1);
                    try {
                        final int pixel = bitmap.getPixel(x, y);
                        r += Color.red(pixel);
                        g += Color.green(pixel);
                        b += Color.blue(pixel);
                        count++;
                    } catch (Throwable ignore) {
                    }
                }
            }
            if (count == 0) {
                return Color.DKGRAY;
            }
            return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        }

        private static int bestTextColorFor(int backgroundColor) {
            final double luminance = 0.299 * Color.red(backgroundColor) + 0.587 * Color.green(backgroundColor) + 0.114 * Color.blue(backgroundColor);
            return luminance > 150 ? Color.BLACK : Color.WHITE;
        }
    }
}
