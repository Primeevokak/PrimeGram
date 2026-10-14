package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.exteragram.messenger.pillstack.core.PillRegistry;
import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.plugins.PrimePillStack;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: the row of plugin-supplied pills above the chat list - empty and gone (zero height)
 * until a plugin puts something there, so an install with no such plugin sees no trace of it.
 *
 * <p>Two kinds share the row. View pills come through the exteraGram API ({@link PillRegistry}
 * + {@link PillStackConfig}): a plugin registers a {@link BasePill} factory and activates its id;
 * the row shows every active one in the saved order, building each View once and keeping it
 * while its id stays active (a pill holds loaded data and refresh timers - rebuilding it on every
 * change would refetch). Text pills come through PrimeGram's own {@code add_pill} API
 * ({@link PrimePillStack}) and follow them. exteraGram shows one pill at a time and swipes
 * between them; here they sit side by side, so nothing hides behind a gesture.
 */
public class PrimePillStackView extends HorizontalScrollView implements NotificationCenter.NotificationCenterDelegate {

    private final LinearLayout row;
    private final Theme.ResourcesProvider resourcesProvider;
    private final SparseArray<BasePill> viewPills = new SparseArray<>();
    private final ArrayList<BasePill> activeViewPills = new ArrayList<>();
    private boolean stackVisible;

    public PrimePillStackView(Context context) {
        this(context, null);
    }

    public PrimePillStackView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setClipChildren(false);
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        addView(row);
        rebuild();
        PrimePillStack.setOnChangedListener(this::rebuild);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final NotificationCenter center = NotificationCenter.getGlobalInstance();
        center.addObserver(this, NotificationCenter.pillStackLayoutChanged);
        center.addObserver(this, NotificationCenter.pillStackSettingsChanged);
        center.addObserver(this, NotificationCenter.didSetNewTheme);
        rebuild();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        final NotificationCenter center = NotificationCenter.getGlobalInstance();
        center.removeObserver(this, NotificationCenter.pillStackLayoutChanged);
        center.removeObserver(this, NotificationCenter.pillStackSettingsChanged);
        center.removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.pillStackLayoutChanged) {
            rebuild();
        } else if (id == NotificationCenter.pillStackSettingsChanged) {
            for (BasePill pill : activeViewPills) {
                final int pillId = pill.getPillId();
                if (PillStackConfig.shouldUpdatePill(args, pillId)) {
                    PillStackConfig.checkAndClearPendingUpdate(pillId);
                    safely(() -> pill.onUpdateData(true));
                }
            }
        } else if (id == NotificationCenter.didSetNewTheme) {
            for (BasePill pill : activeViewPills) {
                safely(() -> {
                    pill.updateColors();
                    pill.updateLoadingColors();
                });
            }
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateStackVisible();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        updateStackVisible();
    }

    private void updateStackVisible() {
        if (activeViewPills == null) {
            return; // a super constructor calling setVisibility, before our fields exist
        }
        final boolean visible = getVisibility() == VISIBLE && getWindowVisibility() == VISIBLE;
        if (stackVisible == visible) {
            return;
        }
        stackVisible = visible;
        for (BasePill pill : activeViewPills) {
            safely(() -> pill.onStackVisibilityChanged(visible));
        }
    }

    private void rebuild() {
        final ArrayList<BasePill> nextViewPills = collectViewPills();
        final List<PrimePillStack.Pill> textPills = PrimePillStack.getAll();

        for (BasePill pill : activeViewPills) {
            if (!nextViewPills.contains(pill)) {
                safely(pill::onPillUnselected);
            }
        }
        row.removeAllViews();
        for (BasePill pill : nextViewPills) {
            AndroidUtilities.removeFromParent(pill);
            row.addView(pill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
            if (!activeViewPills.contains(pill)) {
                safely(pill::onPillSelected);
            }
        }
        activeViewPills.clear();
        activeViewPills.addAll(nextViewPills);

        for (PrimePillStack.Pill pill : textPills) {
            row.addView(makeChip(pill), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        }
        setVisibility(nextViewPills.isEmpty() && textPills.isEmpty() ? GONE : VISIBLE);
        requestLayout();
    }

    /** Active registered pills in saved order, reusing the View already built for an id. */
    private ArrayList<BasePill> collectViewPills() {
        final ArrayList<BasePill> result = new ArrayList<>();
        final List<Integer> active = new ArrayList<>(PillStackConfig.getActivePills());
        final SparseArray<BasePill> kept = new SparseArray<>();
        for (Integer pillId : active) {
            if (pillId == null) {
                continue;
            }
            final PillRegistry.PillInfo info = PillRegistry.getPillInfo(pillId);
            if (info == null || info.creator() == null) {
                continue;
            }
            BasePill pill = viewPills.get(pillId);
            if (pill == null) {
                try {
                    pill = info.creator().create(getContext(), resourcesProvider);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                if (pill == null) {
                    continue;
                }
                final BasePill created = pill;
                created.setOnClickListener(v -> safely(created::onPillClicked));
                created.setOnLongClickListener(v -> {
                    try {
                        return created.onPillLongClicked();
                    } catch (Throwable e) {
                        FileLog.e(e);
                        return false;
                    }
                });
            }
            kept.put(pillId, pill);
            result.add(pill);
        }
        viewPills.clear();
        for (int i = 0; i < kept.size(); i++) {
            viewPills.put(kept.keyAt(i), kept.valueAt(i));
        }
        return result;
    }

    /** A plugin's pill throwing must cost that pill, not the chat list it sits above. */
    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private View makeChip(PrimePillStack.Pill pill) {
        final TextView chip = new TextView(getContext());
        chip.setText(pill.text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        chip.setTextColor(Color.WHITE);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));

        int color;
        try {
            color = pill.colorHex != null ? Color.parseColor(pill.colorHex) : Theme.getColor(Theme.key_chats_actionBackground);
        } catch (Throwable t) {
            color = Theme.getColor(Theme.key_chats_actionBackground);
        }
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(AndroidUtilities.dp(16));
        bg.setColor(color);
        chip.setBackground(bg);

        if (pill.icon != null) {
            final int iconRes = ApplicationLoader.applicationContext.getResources()
                    .getIdentifier(pill.icon, "drawable", ApplicationLoader.applicationContext.getPackageName());
            if (iconRes != 0) {
                chip.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
                chip.setCompoundDrawablePadding(AndroidUtilities.dp(6));
            }
        }

        chip.setOnClickListener(v -> PrimePillStack.click(pill));
        return chip;
    }
}
