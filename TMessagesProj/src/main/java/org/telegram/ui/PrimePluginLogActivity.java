package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.PrimePluginLog;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

/**
 * PrimeGram: the journal of one plugin - its log lines, every hook call and every error with a
 * traceback (see {@link PrimePluginLog}). Opened from the plugin's settings screen.
 */
public class PrimePluginLogActivity extends BaseFragment {

    private static final int ID_REFRESH = 1;
    private static final int ID_COPY = 2;
    private static final int ID_CLEAR = 3;
    /** The screen shows the tail; copying takes everything. */
    private static final int MAX_SHOWN_CHARS = 60_000;

    private final String pluginId;
    private final String pluginName;
    private TextView textView;
    private ScrollView scrollView;

    public PrimePluginLogActivity(String pluginId, String pluginName) {
        this.pluginId = pluginId;
        this.pluginName = pluginName;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Журнал: " + pluginName);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_REFRESH) {
                    reload(true);
                } else if (id == ID_COPY) {
                    AndroidUtilities.addToClipboard(PrimePluginLog.dump(pluginId));
                    BulletinFactory.of(PrimePluginLogActivity.this).createSimpleBulletin(R.raw.copy, "Журнал скопирован").show();
                } else if (id == ID_CLEAR) {
                    PrimePluginLog.clear(pluginId);
                    reload(false);
                }
            }
        });
        final ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(ID_REFRESH, R.drawable.msg_retry);
        menu.addItem(ID_COPY, R.drawable.msg_copy);
        menu.addItem(ID_CLEAR, R.drawable.msg_delete);

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        scrollView = new ScrollView(context);
        textView = new TextView(context);
        textView.setTextIsSelectable(true);
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(24));
        scrollView.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        reload(true);
        return root;
    }

    private void reload(boolean toEnd) {
        String text = PrimePluginLog.dump(pluginId);
        if (text.isEmpty()) {
            text = "Пока пусто. Записи появляются, когда плагин что-то пишет в лог, вызывает хуки или падает.\n"
                    + "Откройте нужный экран и нажмите «обновить».";
        } else if (text.length() > MAX_SHOWN_CHARS) {
            text = "… (показан конец, целиком — кнопкой «копировать»)\n" + text.substring(text.length() - MAX_SHOWN_CHARS);
        }
        textView.setText(text);
        if (toEnd) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }
}
