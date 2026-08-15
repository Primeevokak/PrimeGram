package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Random;

/**
 * PrimeGram: the deterministic fake account a decoy renders.
 *
 * <p>Built from {@link java.util.Random}, not {@link java.security.SecureRandom} - on purpose.
 * The seed itself ({@link PrimeDecoyState}) is chosen with a real SecureRandom once, but the
 * persona built FROM that seed must reproduce byte-for-byte the same fake self, contacts, chats
 * and messages on every cold start, or the decoy would visibly reshuffle its own history every
 * time the app restarts - the opposite of "functional and boring."
 *
 * <p>Every object here is a real {@code TLRPC} object, built the same way a genuine server
 * response would be, and is only ever handed directly to {@link PrimeDecoyServer}'s callers as a
 * Java object graph - never serialized to bytes, so none of the {@code flags}/wire-format
 * bookkeeping those classes also carry needs to be correct here, only the plain fields real
 * downstream code (MessagesController, the chat UI) actually reads.
 */
public final class PrimeDecoyPersona {

    public final TLRPC.TL_user self;
    public final ArrayList<TLRPC.TL_user> contacts = new ArrayList<>();
    public final TLRPC.TL_chat group;
    public final TLRPC.TL_channel channel;
    /** One TL_dialog per conversation, newest first. */
    public final ArrayList<TLRPC.TL_dialog> dialogs = new ArrayList<>();
    /** Every message across every conversation, in no particular order - getDialogs wants the
     *  latest one per dialog alongside the dialog list; getHistory wants a peer's own slice. */
    public final ArrayList<TLRPC.TL_message> allMessages = new ArrayList<>();

    private static final String[] FIRST_NAMES = {"Артём", "Дарья", "Максим", "Ольга", "Иван", "Света"};
    private static final String[] LAST_NAMES = {"Волков", "Кузнецова", "Соколов", "Морозова", "Попов", "Егорова"};
    private static final String[][] SCRIPTS = {
            {"Привет! Как добрался?", "Норм, только приехал", "Отлично, отпишись как разберёшься"},
            {"Ты видел новости?", "Ага, жесть конечно", "Ладно, потом обсудим"},
            {"Скинь фотки с выходных", "Сейчас, дома буду", "Ок, жду 🙂"},
            {"Завтра во сколько встречаемся?", "Давай в 7", "Погнали, до завтра"},
            {"Заказ уже приехал?", "Да, всё отлично", "Супер, спасибо"},
            {"Ты сегодня работаешь?", "До шести", "Понял, увидимся вечером"},
    };
    private static final String[] GROUP_LINES = {
            "Всем привет!", "Кто идёт в субботу?", "Я за", "И я", "Тогда сбор в 12"};
    private static final String[] CHANNEL_LINES = {
            "Новый пост уже на сайте", "Обновление вышло, смотрите описание", "Спасибо всем за поддержку 🙏"};

    private PrimeDecoyPersona(TLRPC.TL_user self, TLRPC.TL_chat group, TLRPC.TL_channel channel) {
        this.self = self;
        this.group = group;
        this.channel = channel;
    }

    public static PrimeDecoyPersona generate(long seed) {
        final Random r = new Random(seed);
        final long baseId = 5_000_000_000L + (Math.abs(r.nextLong()) % 900_000_000L);

        final TLRPC.TL_user self = new TLRPC.TL_user();
        self.id = baseId;
        self.self = true;
        self.first_name = pick(r, FIRST_NAMES);
        self.last_name = pick(r, LAST_NAMES);
        self.username = null;
        self.status = new TLRPC.TL_userStatusOnline();

        final TLRPC.TL_chat group = new TLRPC.TL_chat();
        group.id = baseId + 1;
        group.title = "Наш чат";
        group.participants_count = 4 + r.nextInt(6);
        group.date = nowMinusDays(r, 200);
        group.version = 1;

        final TLRPC.TL_channel channel = new TLRPC.TL_channel();
        channel.id = baseId + 2;
        channel.title = "Новости";
        channel.broadcast = true;
        channel.megagroup = false;
        channel.signature_profiles = false;
        channel.date = nowMinusDays(r, 400);
        channel.participants_count = 50 + r.nextInt(500);

        final PrimeDecoyPersona persona = new PrimeDecoyPersona(self, group, channel);

        int contactCount = 3 + r.nextInt(3);
        for (int i = 0; i < contactCount; i++) {
            final TLRPC.TL_user u = new TLRPC.TL_user();
            u.id = baseId + 10 + i;
            u.first_name = pick(r, FIRST_NAMES);
            u.last_name = pick(r, LAST_NAMES);
            u.contact = true;
            u.mutual_contact = true;
            u.status = r.nextBoolean() ? new TLRPC.TL_userStatusOnline() : new TLRPC.TL_userStatusRecently();
            persona.contacts.add(u);
        }

        int nowDate = (int) (System.currentTimeMillis() / 1000L);
        long midCounter = 0;

        // Private conversations, one per contact, newest first (each a bit older than the last).
        for (int i = 0; i < persona.contacts.size(); i++) {
            final TLRPC.TL_user u = persona.contacts.get(i);
            final String[] script = SCRIPTS[i % SCRIPTS.length];
            int date = nowDate - (i + 1) * 900;
            TLRPC.TL_message last = null;
            for (int j = 0; j < script.length; j++) {
                boolean out = j % 2 == 1;
                TLRPC.TL_message m = buildMessage(++midCounter, out ? self.id : u.id, u.id, script[j], date + j * 30, out);
                persona.allMessages.add(m);
                last = m;
            }
            persona.dialogs.add(buildDialog(last, u.id, false));
        }

        // The group.
        {
            int date = nowDate - 1800;
            TLRPC.TL_message last = null;
            long groupMidCounter = 0;
            for (int j = 0; j < GROUP_LINES.length; j++) {
                TLRPC.TL_user speaker = persona.contacts.get(j % persona.contacts.size());
                TLRPC.TL_message m = buildGroupMessage(++groupMidCounter, -group.id, speaker.id, GROUP_LINES[j], date + j * 20, false);
                persona.allMessages.add(m);
                last = m;
            }
            persona.dialogs.add(buildDialog(last, -group.id, false));
        }

        // The channel - posts are "from the channel" (post = true), not from a person.
        {
            int date = nowDate - 3600;
            TLRPC.TL_message last = null;
            long channelMidCounter = 0;
            // Dialog id for both a Chat and a Channel is just -id (DialogObject.getDialogId) -
            // no special offset the way "isChannel" might suggest; the two are told apart by which
            // Peer subtype the dialog's own `peer` field holds, not by the id's magnitude.
            long channelDialogId = -channel.id;
            for (int j = 0; j < CHANNEL_LINES.length; j++) {
                TLRPC.TL_message m = buildChannelPost(++channelMidCounter, channel.id, CHANNEL_LINES[j], date + j * 600);
                persona.allMessages.add(m);
                last = m;
            }
            persona.dialogs.add(buildDialog(last, channelDialogId, true));
        }

        return persona;
    }

    private static TLRPC.TL_message buildMessage(long mid, long fromId, long peerUserId, String text, int date, boolean out) {
        TLRPC.TL_message m = new TLRPC.TL_message();
        m.id = (int) mid;
        m.out = out;
        m.from_id = new TLRPC.TL_peerUser();
        m.from_id.user_id = fromId;
        m.peer_id = new TLRPC.TL_peerUser();
        m.peer_id.user_id = peerUserId;
        m.date = date;
        m.message = text;
        m.unread = false;
        return m;
    }

    private static TLRPC.TL_message buildGroupMessage(long mid, long chatDialogId, long fromUserId, String text, int date, boolean out) {
        TLRPC.TL_message m = new TLRPC.TL_message();
        m.id = (int) mid;
        m.out = out;
        m.from_id = new TLRPC.TL_peerUser();
        m.from_id.user_id = fromUserId;
        m.peer_id = new TLRPC.TL_peerChat();
        m.peer_id.chat_id = -chatDialogId;
        m.date = date;
        m.message = text;
        m.unread = false;
        return m;
    }

    private static TLRPC.TL_message buildChannelPost(long mid, long channelId, String text, int date) {
        TLRPC.TL_message m = new TLRPC.TL_message();
        m.id = (int) mid;
        m.out = false;
        m.post = true;
        m.from_id = new TLRPC.TL_peerChannel();
        m.from_id.channel_id = channelId;
        m.peer_id = new TLRPC.TL_peerChannel();
        m.peer_id.channel_id = channelId;
        m.date = date;
        m.message = text;
        m.unread = false;
        m.views = 100 + (int) (mid * 37 % 900);
        return m;
    }

    private static TLRPC.TL_dialog buildDialog(TLRPC.TL_message lastMessage, long peerRawId, boolean isChannel) {
        TLRPC.TL_dialog d = new TLRPC.TL_dialog();
        if (isChannel) {
            d.peer = new TLRPC.TL_peerChannel();
            d.peer.channel_id = -peerRawId;
        } else if (peerRawId < 0) {
            d.peer = new TLRPC.TL_peerChat();
            d.peer.chat_id = -peerRawId;
        } else {
            d.peer = new TLRPC.TL_peerUser();
            d.peer.user_id = peerRawId;
        }
        d.top_message = lastMessage != null ? lastMessage.id : 0;
        d.read_inbox_max_id = d.top_message;
        d.read_outbox_max_id = d.top_message;
        d.unread_count = 0;
        d.notify_settings = new TLRPC.TL_peerNotifySettings();
        return d;
    }

    private static int nowMinusDays(Random r, int maxDays) {
        return (int) (System.currentTimeMillis() / 1000L) - r.nextInt(Math.max(1, maxDays)) * 86400;
    }

    private static String pick(Random r, String[] values) {
        return values[r.nextInt(values.length)];
    }
}
