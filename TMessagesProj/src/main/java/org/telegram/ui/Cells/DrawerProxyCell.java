package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

/** PrimeGram: proxy quick-toggle row in the classic drawer menu - not a stock pre-redesign class,
 *  ported from inugram's Kotlin {@code DrawerProxyCell}. */
public class DrawerProxyCell extends FrameLayout {

    private final BackupImageView imageView;
    private final TextView textView;
    private final Switch checkBox;

    private final int touchSlop;

    public interface OnSwitchToggled {
        void onToggled(boolean checked);
    }

    public OnSwitchToggled onSwitchToggled;

    public DrawerProxyCell(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        imageView = new BackupImageView(context);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuItemIcon), PorterDuff.Mode.SRC_IN));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER_VERTICAL | (isRTL() ? Gravity.RIGHT : Gravity.LEFT));

        checkBox = new Switch(context);
        checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_chats_menuBackground, Theme.key_chats_menuBackground);
        checkBox.setClickable(false);
        checkBox.setFocusable(false);

        int startGravity = isRTL() ? Gravity.RIGHT : Gravity.LEFT;
        int endGravity = isRTL() ? Gravity.LEFT : Gravity.RIGHT;
        addView(imageView, LayoutHelper.createFrame(24, 24, startGravity | Gravity.TOP, isRTL() ? 0 : 19, 12, isRTL() ? 19 : 0, 0));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, startGravity | Gravity.TOP, isRTL() ? 70 : 72, 0, isRTL() ? 72 : 70, 0));
        addView(checkBox, LayoutHelper.createFrame(37, 24, endGravity | Gravity.CENTER_VERTICAL, isRTL() ? 22 : 0, 0, isRTL() ? 0 : 22, 0));

        setClipChildren(false);
    }

    private static boolean isRTL() {
        return LocaleController.isRTL;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuItemIcon), PorterDuff.Mode.SRC_IN));
        checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_chats_menuBackground, Theme.key_chats_menuBackground);
    }

    private float switchDownX = -1f;
    private boolean inSwitchZone;

    private boolean isInSwitchZone(float x) {
        if (checkBox.getVisibility() != VISIBLE) return false;
        return isRTL() ? x <= checkBox.getRight() + AndroidUtilities.dp(12) : x >= checkBox.getLeft() - AndroidUtilities.dp(12);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            inSwitchZone = isInSwitchZone(ev.getX());
            if (inSwitchZone) {
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            }
        }
        return inSwitchZone;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!inSwitchZone) return super.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                switchDownX = event.getX();
                break;
            case MotionEvent.ACTION_UP:
                if (Math.abs(event.getX() - switchDownX) < touchSlop) {
                    boolean newState = !checkBox.isChecked();
                    checkBox.setChecked(newState, true);
                    if (onSwitchToggled != null) onSwitchToggled.onToggled(newState);
                }
                inSwitchZone = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                inSwitchZone = false;
                break;
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY)
        );
    }

    public void bind(CharSequence text, int iconRes) {
        textView.setText(text);
        imageView.setImageResource(iconRes);
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked, isAttachedToWindow());
    }

    public void setSwitchVisible(boolean visible) {
        checkBox.setVisibility(visible ? VISIBLE : GONE);
    }
}
