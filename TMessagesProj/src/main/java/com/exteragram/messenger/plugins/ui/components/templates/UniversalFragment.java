package com.exteragram.messenger.plugins.ui.components.templates;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/**
 * PrimeGram: exteraGram's list screen whose whole content comes from a plugin's delegate - the
 * usual way a plugin shows a screen of its own ({@code UniversalFragment(delegate)} then
 * {@code presentFragment}). Every delegate method has a default, so a plugin implements only what
 * it needs; an exception from the plugin is logged, never allowed to take the screen down.
 */
public class UniversalFragment extends org.telegram.ui.Components.UniversalFragment {

    public interface UniversalFragmentDelegate {
        default View beforeCreateView() {
            return null;
        }

        default View afterCreateView(View view) {
            return view;
        }

        default CharSequence getTitle() {
            return "";
        }

        default void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        }

        default void onClick(UItem item, View view, int position, float x, float y) {
        }

        default boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }

        default void onMenuItemClick(int id) {
        }

        default void onFragmentCreate() {
        }

        /** null keeps the default back handling; TRUE/FALSE is the answer to onBackPressed. */
        default Boolean onBackPressed() {
            return null;
        }

        default void onFragmentDestroy() {
        }
    }

    private final UniversalFragmentDelegate delegate;

    public UniversalFragment() {
        this(null);
    }

    public UniversalFragment(UniversalFragmentDelegate delegate) {
        this.delegate = delegate;
    }

    public UniversalFragmentDelegate getDelegate() {
        return delegate;
    }

    @Override
    public View createView(Context context) {
        if (delegate != null) {
            try {
                final View replacement = delegate.beforeCreateView();
                if (replacement != null) {
                    return fragmentView = replacement;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        View view = super.createView(context);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                    return;
                }
                if (delegate != null) {
                    try {
                        delegate.onMenuItemClick(id);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            }
        });
        if (delegate != null) {
            try {
                final View after = delegate.afterCreateView(view);
                if (after != null) {
                    view = fragmentView = after;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return view;
    }

    @Override
    public boolean onFragmentCreate() {
        if (delegate != null) {
            try {
                delegate.onFragmentCreate();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (delegate != null) {
            try {
                delegate.onFragmentDestroy();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        super.onFragmentDestroy();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (delegate != null) {
            try {
                final Boolean answer = delegate.onBackPressed();
                if (answer != null) {
                    return answer;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return super.onBackPressed(invoked);
    }

    @Override
    protected CharSequence getTitle() {
        try {
            return delegate == null ? "" : delegate.getTitle();
        } catch (Throwable e) {
            FileLog.e(e);
            return "";
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (delegate != null) {
            try {
                delegate.fillItems(items, adapter);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (delegate != null) {
            try {
                delegate.onClick(item, view, position, x, y);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        try {
            return delegate != null && delegate.onLongClick(item, view, position, x, y);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }
}
