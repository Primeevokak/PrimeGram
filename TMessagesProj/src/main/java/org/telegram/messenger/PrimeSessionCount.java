package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

/**
 * PrimeGram: how many devices are signed in, for the Devices row in Settings.
 *
 * <p>Telegram fetches this only when the sessions screen is opened, so the number is not sitting
 * anywhere to be read. Keeping a small cache per account means the row can show it immediately on
 * a later visit, with a refresh in the background.
 *
 * <p>The value is a count of other devices, current session excluded - "1" next to Devices when
 * the phone in your hand is the only one would be noise, so zero renders as nothing at all.
 */
public class PrimeSessionCount {

    private static final int[] counts = new int[UserConfig.MAX_ACCOUNT_COUNT];
    private static final long[] fetchedAt = new long[UserConfig.MAX_ACCOUNT_COUNT];
    private static final boolean[] inFlight = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    /** Sessions change rarely; refetching more often than this buys nothing. */
    private static final long TTL_MS = 5 * 60 * 1000L;

    static {
        for (int i = 0; i < counts.length; i++) {
            counts[i] = -1;
        }
    }

    /** The cached count, or -1 when nothing has been fetched yet. */
    public static int get(int account) {
        if (account < 0 || account >= counts.length) {
            return -1;
        }
        return counts[account];
    }

    /**
     * Fetches the count unless a fresh one is already held.
     *
     * <p>{@code onChanged} runs on the main thread, and only when the number actually moved -
     * a screen that rebinds on every resume should not rebuild its list for an unchanged value.
     */
    public static void refresh(int account, Runnable onChanged) {
        if (account < 0 || account >= counts.length) {
            return;
        }
        if (!UserConfig.getInstance(account).isClientActivated()) {
            return;
        }
        final long now = System.currentTimeMillis();
        synchronized (PrimeSessionCount.class) {
            if (inFlight[account] || now - fetchedAt[account] < TTL_MS && counts[account] >= 0) {
                return;
            }
            inFlight[account] = true;
        }
        TL_account.getAuthorizations req = new TL_account.getAuthorizations();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            int value = -1;
            if (error == null && response instanceof TL_account.authorizations) {
                final TL_account.authorizations res = (TL_account.authorizations) response;
                int others = 0;
                for (TLRPC.TL_authorization authorization : res.authorizations) {
                    // Flag 0 marks the session we are running in. Password-pending ones are still
                    // real signed-in devices, so they count.
                    if ((authorization.flags & 1) == 0) {
                        others++;
                    }
                }
                value = others;
            }
            final int result = value;
            AndroidUtilities.runOnUIThread(() -> {
                boolean changed;
                synchronized (PrimeSessionCount.class) {
                    inFlight[account] = false;
                    if (result < 0) {
                        // Leave whatever we had; a failed request is not news about the count.
                        return;
                    }
                    fetchedAt[account] = System.currentTimeMillis();
                    changed = counts[account] != result;
                    counts[account] = result;
                }
                if (changed && onChanged != null) {
                    onChanged.run();
                }
            });
        });
    }

    /** Forgets the cached value, so the next refresh really asks. */
    public static void invalidate(int account) {
        if (account < 0 || account >= counts.length) {
            return;
        }
        synchronized (PrimeSessionCount.class) {
            counts[account] = -1;
            fetchedAt[account] = 0;
        }
    }
}
