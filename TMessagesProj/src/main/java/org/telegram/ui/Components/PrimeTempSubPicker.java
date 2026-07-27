package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: picks how long a temporary subscription lasts, from ten minutes to a month.
 *
 * <p>A slider rather than a list of presets, because the useful durations are not evenly spread:
 * the difference between ten and forty minutes matters as much as the difference between one and
 * four weeks, and a linear scale would spend nine tenths of its travel on days nobody wanted.
 * The mapping is therefore geometric - every equal step multiplies the duration by the same
 * factor - so the slider feels the same wherever you grab it.
 */
public class PrimeTempSubPicker extends FrameLayout {

    public static final long MIN_MS = 10 * 60 * 1000L;
    public static final long MAX_MS = 30 * 24 * 60 * 60 * 1000L;

    private final SeekBarView seekBar;
    private final android.widget.TextView valueText;
    private final Theme.ResourcesProvider resourcesProvider;

    private long durationMs = 60 * 60 * 1000L;

    public PrimeTempSubPicker(Context context, long initialMs, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.durationMs = clamp(initialMs);

        valueText = new android.widget.TextView(context);
        valueText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        valueText.setTypeface(AndroidUtilities.bold());
        valueText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        valueText.setGravity(Gravity.CENTER);
        addView(valueText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT, 22, 8, 22, 0));

        seekBar = new SeekBarView(context, resourcesProvider);
        seekBar.setReportChanges(true);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                durationMs = fromProgress(progress);
                updateText();
            }

            @Override
            public CharSequence getContentDescription() {
                return format(durationMs);
            }
        });
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38,
                Gravity.TOP | Gravity.LEFT, 6, 40, 6, 0));

        seekBar.setProgress(toProgress(durationMs));
        updateText();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(92), MeasureSpec.EXACTLY));
    }

    private void updateText() {
        valueText.setText(format(durationMs));
    }

    public long getDurationMs() {
        return durationMs;
    }

    private static long clamp(long ms) {
        return Math.max(MIN_MS, Math.min(MAX_MS, ms));
    }

    /**
     * Position on the slider for a duration, and back.
     *
     * <p>Geometric: {@code duration = MIN * (MAX / MIN) ^ progress}. Ten minutes sits at 0, a
     * month at 1, and an hour lands about a fifth of the way along.
     */
    private static float toProgress(long ms) {
        final double span = Math.log((double) MAX_MS / MIN_MS);
        return (float) Math.max(0, Math.min(1, Math.log((double) clamp(ms) / MIN_MS) / span));
    }

    private static long fromProgress(float progress) {
        final double span = Math.log((double) MAX_MS / MIN_MS);
        final long raw = (long) (MIN_MS * Math.exp(span * Math.max(0, Math.min(1, progress))));
        return round(clamp(raw));
    }

    /**
     * Rounds to something a person would have chosen.
     *
     * <p>Without this the slider produces "1 час 47 минут 12 секунд", which is technically what
     * was dragged to and useless to read. The step grows with the value, so short durations stay
     * precise to the minute while long ones snap to whole days.
     */
    private static long round(long ms) {
        final long minute = 60 * 1000L;
        final long hour = 60 * minute;
        final long day = 24 * hour;
        final long step;
        if (ms < hour) {
            step = 5 * minute;
        } else if (ms < 6 * hour) {
            step = 15 * minute;
        } else if (ms < day) {
            step = hour;
        } else if (ms < 7 * day) {
            step = 6 * hour;
        } else {
            step = day;
        }
        return Math.max(MIN_MS, (ms + step / 2) / step * step);
    }

    /** Russian, with the plural forms the language actually needs. */
    public static String format(long ms) {
        final long minutes = ms / (60 * 1000L);
        if (minutes < 60) {
            return minutes + " " + plural(minutes, "минута", "минуты", "минут");
        }
        final long hours = minutes / 60;
        if (hours < 24) {
            final long restMinutes = minutes % 60;
            String out = hours + " " + plural(hours, "час", "часа", "часов");
            if (restMinutes > 0) {
                out += " " + restMinutes + " " + plural(restMinutes, "минута", "минуты", "минут");
            }
            return out;
        }
        final long days = hours / 24;
        if (days < 7) {
            final long restHours = hours % 24;
            String out = days + " " + plural(days, "день", "дня", "дней");
            if (restHours > 0) {
                out += " " + restHours + " " + plural(restHours, "час", "часа", "часов");
            }
            return out;
        }
        if (days % 7 == 0 && days < 30) {
            final long weeks = days / 7;
            return weeks + " " + plural(weeks, "неделя", "недели", "недель");
        }
        if (days >= 30) {
            return "1 месяц";
        }
        return days + " " + plural(days, "день", "дня", "дней");
    }

    private static String plural(long n, String one, String few, String many) {
        final long mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        switch ((int) (n % 10)) {
            case 1:
                return one;
            case 2:
            case 3:
            case 4:
                return few;
            default:
                return many;
        }
    }

    /** "через 3 дня" style text for a countdown, given a remaining interval. */
    public static String formatRemaining(long remainingMs) {
        if (remainingMs <= 0) {
            return "меньше минуты";
        }
        if (remainingMs < 60 * 1000L) {
            return "меньше минуты";
        }
        return format(remainingMs);
    }
}
