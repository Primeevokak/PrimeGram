package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.TgWsProxyService;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

public class ProxySetupActivity extends BaseFragment {

    private FrameLayout contentFrame;
    private TextView progressText;
    private TextView statusText;
    private RadarView radarView;
    private ProgressBarView progressBar;
    
    private Runnable progressRunnable;
    private int currentProgress = 0;
    private boolean isProxyReady = false;

    @Override
    public View createView(Context context) {
        if (actionBar != null) {
            actionBar.setVisibility(View.GONE);
        }

        contentFrame = new FrameLayout(context) {
            private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            
            @Override
            protected void onDraw(Canvas canvas) {
                if (bgPaint.getShader() == null) {
                    RadialGradient gradient = new RadialGradient(
                            getWidth() / 2f, getHeight() / 2f,
                            Math.max(getWidth(), getHeight()) * 0.8f,
                            new int[]{0xFF1A1F2C, 0xFF0B0E14},
                            null, Shader.TileMode.CLAMP
                    );
                    bgPaint.setShader(gradient);
                }
                canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
            }
        };
        contentFrame.setWillNotDraw(false);

        TextView titleView = new TextView(context);
        titleView.setText("PrimeGram");
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setGravity(Gravity.CENTER);
        contentFrame.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 80, 0, 0));

        TextView subtitleView = new TextView(context);
        subtitleView.setText("Встроенный обход ограничений");
        subtitleView.setTextColor(0x8AFFFFFF);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitleView.setGravity(Gravity.CENTER);
        contentFrame.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 130, 0, 0));

        radarView = new RadarView(context);
        contentFrame.addView(radarView, LayoutHelper.createFrame(240, 240, Gravity.CENTER, 0, -50, 0, 0));

        progressText = new TextView(context);
        progressText.setTextColor(0xFF00F0FF);
        progressText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        progressText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        progressText.setGravity(Gravity.CENTER);
        progressText.setText("0%");
        contentFrame.addView(progressText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, -50, 0, 0));

        statusText = new TextView(context);
        statusText.setTextColor(0xCCFFFFFF);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setText("Запуск сетевого туннеля...");
        contentFrame.addView(statusText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 24, 0, 24, 180));

        progressBar = new ProgressBarView(context);
        contentFrame.addView(progressBar, LayoutHelper.createFrame(260, 6, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 140));

        // Skip Button in top right - styled as a beautiful round pill button
        TextView skipButton = new TextView(context);
        skipButton.setText("Пропустить");
        skipButton.setTextColor(0xFFFFFFFF);
        skipButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        skipButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        skipButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        skipButton.setGravity(Gravity.CENTER);
        skipButton.setBackground(org.telegram.ui.ActionBar.Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(18), 0x1AFFFFFF, 0x33FFFFFF));
        skipButton.setOnClickListener(v -> {
            if (progressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(progressRunnable);
                progressRunnable = null;
            }
            presentFragment(new LoginActivity(), true);
        });
        contentFrame.addView(skipButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 16, 16, 16, 0));

        startProgressAnimation();

        fragmentView = contentFrame;
        return fragmentView;
    }

    private void startProgressAnimation() {
        isProxyReady = false;
        currentProgress = 0;
        
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (progressRunnable == null) {
                    return;
                }
                
                if (currentProgress < 60) {
                    currentProgress++;
                } else if (currentProgress == 60) {
                    if (TgWsProxyService.isSocketBound) {
                        isProxyReady = true;
                        currentProgress++;
                    } else {
                        if (!TgWsProxyService.isRunning()) {
                            TgWsProxyService.startService(ApplicationLoader.applicationContext);
                        }
                        // Smoothly freeze at 60% with the correct status
                        progressText.setText("60%");
                        progressBar.setProgress(60);
                        statusText.setText("Активация обходного прокси-сервера...");
                        AndroidUtilities.runOnUIThread(this, 100);
                        return;
                    }
                } else if (currentProgress < 100) {
                    currentProgress++;
                } else {
                    presentFragment(new LoginActivity(), true);
                    return;
                }

                progressText.setText(currentProgress + "%");
                progressBar.setProgress(currentProgress);

                if (currentProgress < 25) {
                    statusText.setText("Подготовка сетевого окружения...");
                } else if (currentProgress < 50) {
                    statusText.setText("Поиск оптимальных доменов Cloudflare...");
                } else if (currentProgress < 60) {
                    statusText.setText("Проверка пинга и маршрутизация...");
                } else if (currentProgress < 75) {
                    statusText.setText("Активация обходного прокси-сервера...");
                } else if (currentProgress < 95) {
                    statusText.setText("Синхронизация защищенного туннеля...");
                } else {
                    statusText.setText("Настройка успешно завершена!");
                }

                // If proxy is ready, speed up transition to the login screen
                long delay = isProxyReady ? 15 : 40;
                AndroidUtilities.runOnUIThread(this, delay);
            }
        };
        AndroidUtilities.runOnUIThread(progressRunnable);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (progressRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(progressRunnable);
            progressRunnable = null;
        }
    }

    private static class RadarView extends View {
        private Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float pulseRadius = 0;
        private int pulseAlpha = 255;
        private ValueAnimator pulseAnimator;

        public RadarView(Context context) {
            super(context);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(AndroidUtilities.dp(2));
            circlePaint.setColor(0x3300F0FF);

            pulsePaint.setStyle(Paint.Style.STROKE);
            pulsePaint.setStrokeWidth(AndroidUtilities.dp(3));
            pulsePaint.setColor(0xFF00F0FF);

            pulseAnimator = ValueAnimator.ofFloat(0, 1);
            pulseAnimator.setDuration(1800);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.addUpdateListener(animation -> {
                float progress = (Float) animation.getAnimatedValue();
                pulseRadius = progress * getWidth() / 2f;
                pulseAlpha = (int) ((1f - progress) * 255);
                invalidate();
            });
            pulseAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            canvas.drawCircle(cx, cy, cx * 0.4f, circlePaint);
            canvas.drawCircle(cx, cy, cx * 0.7f, circlePaint);
            canvas.drawCircle(cx, cy, cx - AndroidUtilities.dp(4), circlePaint);

            pulsePaint.setAlpha(pulseAlpha);
            canvas.drawCircle(cx, cy, pulseRadius, pulsePaint);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
            }
        }
    }

    private static class ProgressBarView extends View {
        private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 0f;

        public ProgressBarView(Context context) {
            super(context);
            bgPaint.setColor(0x22FFFFFF);
            
            progressPaint.setColor(0xFF00F0FF);
            progressPaint.setShadowLayer(AndroidUtilities.dp(4), 0, 0, 0xFF00F0FF);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        public void setProgress(int val) {
            this.progress = val / 100f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float r = h / 2f;

            canvas.drawRoundRect(0, 0, w, h, r, r, bgPaint);

            if (progress > 0) {
                canvas.drawRoundRect(0, 0, w * progress, h, r, r, progressPaint);
            }
        }
    }
}
