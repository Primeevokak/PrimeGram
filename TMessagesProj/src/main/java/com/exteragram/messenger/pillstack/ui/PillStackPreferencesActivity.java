package com.exteragram.messenger.pillstack.ui;

import android.view.View;

import com.exteragram.messenger.pillstack.core.PillRegistry;
import com.exteragram.messenger.pillstack.core.PillStackConfig;

import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * exteraGram API, PrimeGram implementation: the screen listing registered pills, shown and hidden.
 * Plugins open it by name (reSwaga's pill menu has a "configure" entry that presents it), so the
 * class and its no-argument constructor are the contract; the screen itself is ours - a tap moves
 * a pill between the two sections, and the shown section can be dragged into order.
 */
public class PillStackPreferencesActivity extends UniversalFragment {

    private int activeSectionId = -1;
    private int hiddenSectionId = -1;

    @Override
    public android.view.View createView(android.content.Context context) {
        final View view = super.createView(context);
        if (listView != null) {
            listView.allowReorder(true);
            listView.listenReorder(this::onReordered);
        }
        return view;
    }

    @Override
    protected CharSequence getTitle() {
        return "Пилюли";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        activeSectionId = -1;
        hiddenSectionId = -1;
        final List<Integer> active = PillStackConfig.getActivePills();
        final List<Integer> hidden = PillStackConfig.getHiddenPills();
        if (active.isEmpty() && hidden.isEmpty()) {
            items.add(UItem.asShadow("Ни один плагин пока не добавил пилюлю."));
            return;
        }
        if (!active.isEmpty()) {
            activeSectionId = addSection(items, adapter, "Показаны", active);
            items.add(UItem.asShadow("Нажмите на пилюлю, чтобы скрыть или показать её. Порядок показанных меняется перетаскиванием."));
        }
        if (!hidden.isEmpty()) {
            hiddenSectionId = addSection(items, adapter, "Скрыты", hidden);
            items.add(UItem.asShadow(null));
        }
    }

    private int addSection(ArrayList<UItem> items, UniversalAdapter adapter, String header, List<Integer> ids) {
        adapter.whiteSectionStart();
        items.add(UItem.asHeader(header));
        final int sectionId = adapter.reorderSectionStart();
        for (Integer id : ids) {
            final PillRegistry.PillInfo info = id != null ? PillRegistry.getPillInfo(id) : null;
            if (info == null) {
                continue;
            }
            final UItem item = UItem.asButton(id, info.iconRes(), info.name());
            item.bind = view -> {
                if (view instanceof TextCell && info.iconRes() != 0) {
                    ((TextCell) view).setColorfulIcon(info.iconColorTop(), info.iconColorBottom(), info.iconRes());
                }
            };
            items.add(item);
        }
        adapter.reorderSectionEnd();
        adapter.whiteSectionEnd();
        return sectionId;
    }

    private void onReordered(int sectionId, ArrayList<UItem> items) {
        final ArrayList<Integer> ids = new ArrayList<>(items.size());
        for (UItem item : items) {
            ids.add(item.id);
        }
        if (sectionId == activeSectionId) {
            PillStackConfig.getActivePills().clear();
            PillStackConfig.getActivePills().addAll(ids);
        } else if (sectionId == hiddenSectionId) {
            PillStackConfig.getHiddenPills().clear();
            PillStackConfig.getHiddenPills().addAll(ids);
        }
        saveAndNotify();
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final Integer id = item.id;
        if (PillStackConfig.getActivePills().remove(id)) {
            if (!PillStackConfig.getHiddenPills().contains(id)) {
                PillStackConfig.getHiddenPills().add(0, id);
            }
        } else if (PillStackConfig.getHiddenPills().remove(id)) {
            PillStackConfig.getActivePills().add(id);
        } else {
            return;
        }
        saveAndNotify();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void saveAndNotify() {
        PillStackConfig.savePillsLayout();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
