package com.exteragram.messenger.plugins.models;

import android.view.View;

import com.exteragram.messenger.plugins.Plugin;

import org.telegram.ui.Components.UItem;

/**
 * PrimeGram: exteraGram's free-form settings row - a ready {@link UItem}, a {@link View}, or a
 * {@link Factory} that builds the row's view. Plugins subclass the factory from Python
 * ({@code @java_subclass(CustomSetting.Factory)}) and hand an instance to {@code Custom(factory=...)};
 * PrimeGram's settings screen then registers it and calls {@code create -> createView -> bindView},
 * the order exteraGram uses.
 */
public final class CustomSetting {

    public static abstract class Factory<V extends View> extends UItem.UItemFactory<V> {
        private boolean isShadowValue;
        private boolean isClickableValue = true;

        /** The row's item; null lets the caller make a plain one for this factory. */
        public UItem create(Plugin plugin, CustomSetting setting, Object args) {
            return null;
        }

        public void onClick(Plugin plugin, UItem item, View view) {
        }

        public void onLongClick(Plugin plugin, UItem item, View view) {
        }

        @Override
        public boolean isShadow() {
            return isShadowValue;
        }

        @Override
        public boolean isClickable() {
            return isClickableValue;
        }

        public final boolean isShadowValue() {
            return isShadowValue;
        }

        public final void setShadowValue(boolean value) {
            isShadowValue = value;
        }

        public final boolean isClickableValue() {
            return isClickableValue;
        }

        public final void setClickableValue(boolean value) {
            isClickableValue = value;
        }
    }

    private final Factory<?> factory;
    private final UItem item;
    private final Object factoryArgs;
    private final Object onClickCallback;
    private final Object createSubFragmentCallback;
    private final Object onLongClickCallback;
    private final String linkAlias;

    public CustomSetting(UItem item, Object onClickCallback, Object createSubFragmentCallback, Object onLongClickCallback, String linkAlias) {
        this(null, item, null, onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias);
        if (item != null) {
            item.settingItem = this;
        }
    }

    public CustomSetting(View view, Object onClickCallback, Object createSubFragmentCallback, Object onLongClickCallback, String linkAlias) {
        this(UItem.asCustom(view), onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias);
    }

    public CustomSetting(Factory<?> factory, Object onClickCallback, Object createSubFragmentCallback, Object onLongClickCallback, String linkAlias) {
        this(factory, null, null, onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias);
    }

    public CustomSetting(Factory<?> factory, Object factoryArgs, Object onClickCallback, Object createSubFragmentCallback, Object onLongClickCallback, String linkAlias) {
        this(factory, null, factoryArgs, onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias);
    }

    private CustomSetting(Factory<?> factory, UItem item, Object factoryArgs, Object onClickCallback,
                          Object createSubFragmentCallback, Object onLongClickCallback, String linkAlias) {
        this.factory = factory;
        this.item = item;
        this.factoryArgs = factoryArgs;
        this.onClickCallback = onClickCallback;
        this.createSubFragmentCallback = createSubFragmentCallback;
        this.onLongClickCallback = onLongClickCallback;
        this.linkAlias = linkAlias;
    }

    public Factory<?> getFactory() {
        return factory;
    }

    public UItem getItem() {
        return item;
    }

    public Object getFactoryArgs() {
        return factoryArgs;
    }

    public Object getOnClickCallback() {
        return onClickCallback;
    }

    public Object getCreateSubFragmentCallback() {
        return createSubFragmentCallback;
    }

    public Object getOnLongClickCallback() {
        return onLongClickCallback;
    }

    public String getLinkAlias() {
        return linkAlias;
    }

    public String getType() {
        return "custom";
    }

    /** Registers a plugin's factory with the item system (UItem.ofFactory needs it) - idempotent. */
    public static void register(Factory<?> factory) {
        if (factory != null) {
            UItem.UItemFactory.setup(factory);
        }
    }
}
