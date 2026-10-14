package com.exteragram.messenger.pillstack.core;

import android.content.Context;

import androidx.annotation.Keep;

import com.exteragram.messenger.pillstack.ui.pills.BasePill;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * exteraGram API, PrimeGram implementation: the catalogue of pills - one entry per pill a plugin
 * (or the app) can show in the pill stack above the chat list, each with a factory that builds
 * its View.
 *
 * <p>Same contract as exteraGram's, member for member, so a plugin written against it registers
 * and activates pills here unchanged (reSwaga's now-playing pill does). What differs on purpose:
 * no built-in pills. exteraGram registers weather and currency-rate pills backed by its own
 * services; PrimeGram's stack is an empty stage for plugins (see {@code PrimePillStack}), so the
 * catalogue starts empty. {@link PillType}'s ids stay reserved all the same.
 *
 * <p>Shown by {@code org.telegram.ui.Components.PrimePillStackView}, next to the plain text pills
 * PrimeGram's own plugin API publishes.
 */
public class PillRegistry {

    public interface PillCreator {
        BasePill create(Context context, Theme.ResourcesProvider resourcesProvider);
    }

    /** A record in exteraGram ({@code id()}, {@code name()}...), kept with the same accessors. */
    public static final class PillInfo {
        private final int id;
        private final CharSequence name;
        private final int iconRes;
        private final int iconColorTop;
        private final int iconColorBottom;
        private final PillCreator creator;

        public PillInfo(int id, CharSequence name, int iconRes, int iconColorTop, int iconColorBottom, PillCreator creator) {
            this.id = id;
            this.name = name;
            this.iconRes = iconRes;
            this.iconColorTop = iconColorTop;
            this.iconColorBottom = iconColorBottom;
            this.creator = creator;
        }

        public int id() {
            return id;
        }

        public CharSequence name() {
            return name;
        }

        public int iconRes() {
            return iconRes;
        }

        public int iconColorTop() {
            return iconColorTop;
        }

        public int iconColorBottom() {
            return iconColorBottom;
        }

        public PillCreator creator() {
            return creator;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PillInfo)) {
                return false;
            }
            final PillInfo other = (PillInfo) o;
            return id == other.id && iconRes == other.iconRes && iconColorTop == other.iconColorTop
                    && iconColorBottom == other.iconColorBottom && Objects.equals(name, other.name)
                    && Objects.equals(creator, other.creator);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, iconRes, iconColorTop, iconColorBottom, creator);
        }

        @Override
        public String toString() {
            return "PillInfo[id=" + id + ", name=" + name + "]";
        }
    }

    private static boolean batchRegistration;
    private static final Map<Integer, PillInfo> registry = Collections.synchronizedMap(new LinkedHashMap<>());

    private PillRegistry() {
    }

    private static void postLayoutChanged() {
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged));
    }

    @Keep
    public static void beginTransaction() {
        batchRegistration = true;
    }

    @Keep
    public static void endTransaction() {
        batchRegistration = false;
        if (PillStackConfig.getConfigLoaded()) {
            PillStackConfig.sanitizePills();
            postLayoutChanged();
        }
    }

    public static void register(PillInfo info) {
        if (info == null) {
            return;
        }
        registry.put(info.id(), info);
        if (batchRegistration) {
            return;
        }
        PillStackConfig.sanitizePills();
        postLayoutChanged();
    }

    @Keep
    public static void unregister(int id) {
        if (registry.remove(id) == null || batchRegistration) {
            return;
        }
        PillStackConfig.sanitizePills();
        postLayoutChanged();
    }

    @Keep
    public static void activatePill(int id) {
        if (!isRegistered(id) || PillStackConfig.getActivePills().contains(id)) {
            return;
        }
        PillStackConfig.getHiddenPills().remove(Integer.valueOf(id));
        PillStackConfig.getActivePills().add(id);
        PillStackConfig.savePillsLayout();
        postLayoutChanged();
    }

    public static PillInfo getPillInfo(int id) {
        return registry.get(id);
    }

    /** A snapshot, so callers can iterate while a plugin registers on another thread. */
    public static Collection<PillInfo> getRegisteredPills() {
        synchronized (registry) {
            return new ArrayList<>(registry.values());
        }
    }

    public static boolean isRegistered(int id) {
        return registry.containsKey(id);
    }
}
