package org.telegram.ui.Cells;

import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * PrimeGram: prepares the text of chat-list rows that are about to scroll into view.
 *
 * Binding a DialogCell builds its text layouts on the UI thread, and most of that time is text
 * shaping: turning each word into positioned glyphs. Android keeps shaped words in a process-wide
 * cache shared by every thread, so shaping the same words with the same paints here, on a background
 * thread, just before the row appears, turns the UI thread's work into cache hits. Nothing produced
 * here is handed to the cell - if a row's text changes meanwhile, the cell simply shapes it itself,
 * exactly as before.
 */
public final class PrimeDialogTextWarmer {

    private static final int AHEAD = 8;
    private static final int PREVIEW_CHARS = 160;
    private static final int REMEMBERED = 400;

    private static DispatchQueue queue;
    private static final HashSet<Object> warmed = new HashSet<>();

    private PrimeDialogTextWarmer() {
    }

    /** UI thread. {@code items} are the adapter's items in scroll order, nearest first. */
    public static void warm(int account, ArrayList<Object> items, int width) {
        if (items == null || items.isEmpty() || width <= 0) {
            return;
        }
        final MessagesController controller = MessagesController.getInstance(account);
        final ArrayList<CharSequence> names = new ArrayList<>();
        final ArrayList<CharSequence> previews = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            final Object item = items.get(i);
            if (!(item instanceof TLRPC.Dialog) || item instanceof TLRPC.TL_dialogFolder) {
                continue;
            }
            final TLRPC.Dialog dialog = (TLRPC.Dialog) item;
            final ArrayList<MessageObject> messages = controller.dialogMessage.get(dialog.id);
            final MessageObject message = messages != null && !messages.isEmpty() ? messages.get(0) : null;
            // A row already prepared for its current last message needs nothing more.
            final Object key = message != null ? message : dialog;
            if (warmed.contains(key)) {
                continue;
            }
            if (warmed.size() > REMEMBERED) {
                warmed.clear();
            }
            warmed.add(key);
            String name = null;
            if (dialog.id > 0) {
                final TLRPC.User user = controller.getUser(dialog.id);
                if (user != null) {
                    name = UserObject.isUserSelf(user) ? null : ContactsController.formatName(user.first_name, user.last_name);
                }
            } else {
                final TLRPC.Chat chat = controller.getChat(-dialog.id);
                if (chat != null) {
                    name = chat.title;
                }
            }
            if (!TextUtils.isEmpty(name)) {
                names.add(name.replace('\n', ' '));
            }
            if (message != null) {
                CharSequence text = message.caption != null ? message.caption : message.messageText;
                if (!TextUtils.isEmpty(text)) {
                    // Plain text: shaping is cached per word, so spans and emoji replacement the
                    // cell adds later do not change which words it asks for.
                    String plain = text.toString();
                    if (plain.length() > PREVIEW_CHARS) {
                        plain = plain.substring(0, PREVIEW_CHARS);
                    }
                    previews.add(plain.replace('\n', ' '));
                }
            }
        }
        if (names.isEmpty() && previews.isEmpty()) {
            return;
        }
        // Theme paints belong to the UI thread (buildLayout resizes them); copies travel instead.
        final TextPaint namePaint = new TextPaint(Theme.dialogs_namePaint[1]);
        final TextPaint messagePaint = new TextPaint(Theme.dialogs_messagePaint[1]);
        if (queue == null) {
            queue = new DispatchQueue("primeDialogText");
            queue.setPriority(Thread.MIN_PRIORITY);
        }
        queue.postRunnable(() -> {
            for (int i = 0; i < names.size(); i++) {
                shape(names.get(i), namePaint, width);
            }
            for (int i = 0; i < previews.size(); i++) {
                shape(previews.get(i), messagePaint, width);
            }
        });
    }

    private static void shape(CharSequence text, TextPaint paint, int width) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build();
            } else {
                new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            }
        } catch (Throwable ignore) {
        }
    }

    /** The row width the cell lays text out in, near enough for shaping (which ignores width). */
    public static int textWidth(int listWidth) {
        return Math.max(AndroidUtilities.dp(100), listWidth - AndroidUtilities.dp(90));
    }
}
