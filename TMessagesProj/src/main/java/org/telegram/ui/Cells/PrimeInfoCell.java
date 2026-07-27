package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: a one-line explanation with the rest of it a tap away.
 *
 * <p>The settings screens used to end every group with four or five paragraphs of grey text. The
 * writing was not the problem - the placement was: an explanation nobody asked for, printed at
 * full length between the switch they just used and the next one they were looking for. It turned
 * every screen into a wall to scroll past, which is the surest way to make sure none of it is
 * read.
 *
 * <p>So the short form stays in the list and the long form moves behind "Подробнее". The detail is
 * still one tap from the setting it belongs to, and the list stays a list.
 */
public class PrimeInfoCell extends TextView {

    private static final String MORE = "Подробнее";

    private final CharSequence details;
    private final String title;

    public PrimeInfoCell(Context context, String title, CharSequence summary, CharSequence details) {
        super(context);
        this.details = details;
        this.title = title;

        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        setPadding(dp(21), dp(10), dp(21), dp(17));
        setBackground(Theme.getThemedDrawable(context, org.telegram.messenger.R.drawable.greydivider,
                Theme.key_windowBackgroundGrayShadow));

        if (details == null) {
            setText(summary);
            return;
        }
        final SpannableStringBuilder text = new SpannableStringBuilder(summary);
        text.append("  ").append(MORE);
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showDetails();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint paint) {
                super.updateDrawState(paint);
                // No underline: this sits inside a sentence, and an underlined word there reads
                // as a correction rather than as a control.
                paint.setUnderlineText(false);
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            }
        }, text.length() - MORE.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        setText(text);
        setMovementMethod(LinkMovementMethod.getInstance());
        // Without this the whole cell keeps the ripple of a pressed link after the sheet opens.
        setHighlightColor(0);
    }

    private void showDetails() {
        final Context context = getContext();
        if (context == null) {
            return;
        }
        final android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(14), dp(22), dp(16));

        if (title != null) {
            final TextView header = new TextView(context);
            header.setText(title);
            header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            header.setTypeface(AndroidUtilities.bold());
            header.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            layout.addView(header, org.telegram.ui.Components.LayoutHelper.createLinear(
                    org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                    org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 0, 0, 12));
        }

        final TextView body = new TextView(context);
        body.setText(details);
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        body.setLineSpacing(dp(2), 1f);
        body.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        layout.addView(body, org.telegram.ui.Components.LayoutHelper.createLinear(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT));

        final android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.addView(layout);

        final BottomSheet sheet = new BottomSheet.Builder(context)
                .setCustomView(scroll)
                .create();
        sheet.show();
    }
}
