package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TgWsProxyService;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

/**
 * PrimeGram: the tunnel's log, on screen.
 *
 * <p>The service has always kept the last two hundred lines and offered a listener for them; there
 * was simply nowhere to look. Which meant that when the tunnel misbehaved, the only way to find out
 * why was to attach a debugger to a release build.
 *
 * <p>New lines append and the view follows them, but only while the user is already at the bottom.
 * Scrolling up is how someone reads the line that explains a failure, and yanking them back down a
 * second later would make the log unreadable exactly when it matters.
 */
public class PrimeTgWsLogActivity extends BaseFragment implements TgWsProxyService.LogListener {

    private static final int ID_COPY = 1;
    private static final int ID_CLEAR = 2;

    private ScrollView scrollView;
    private TextView textView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Журнал сервера");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_COPY) {
                    AndroidUtilities.addToClipboard(textView.getText().toString());
                    BulletinFactory.of(PrimeTgWsLogActivity.this)
                            .createCopyBulletin("Журнал скопирован").show();
                }
            }
        });
        final ActionBarMenuItem menu = actionBar.createMenu().addItem(0, R.drawable.ic_ab_other);
        menu.addSubItem(ID_COPY, R.drawable.msg_copy, "Скопировать");

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextIsSelectable(true);
        textView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        scrollView = new ScrollView(context);
        scrollView.addView(textView, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        root.addView(scrollView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = root;
        render();
        return root;
    }

    private void render() {
        final List<String> lines = TgWsProxyService.getLogBuffer();
        textView.setText(lines.isEmpty()
                ? "Пока пусто. Строки появляются, когда сервер подключается или переподключается."
                : android.text.TextUtils.join("\n", lines));
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public boolean onFragmentCreate() {
        TgWsProxyService.setLogListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        TgWsProxyService.setLogListener(null);
        super.onFragmentDestroy();
    }

    @Override
    public void onLogAdded(String line) {
        AndroidUtilities.runOnUIThread(() -> {
            if (textView == null || scrollView == null) {
                return;
            }
            final boolean atBottom = !scrollView.canScrollVertically(1);
            textView.append("\n" + line);
            if (atBottom) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        });
    }
}
