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
 *
 * <p>Deliberately generous with volume (a dozen-plus contacts, two group chats, a channel,
 * several messages per thread with varied, non-uniform timestamps) - a real account that's seen
 * any real use looks like this; three contacts each with a three-line script and every timestamp
 * exactly 15 minutes apart reads as obviously synthetic the moment anyone actually looks at it.
 */
public final class PrimeDecoyPersona {

    public final TLRPC.TL_user self;
    public final ArrayList<TLRPC.TL_user> contacts = new ArrayList<>();
    public final ArrayList<TLRPC.TL_chat> groups = new ArrayList<>();
    public final TLRPC.TL_channel channel;
    /** One TL_dialog per conversation, newest first. */
    public final ArrayList<TLRPC.TL_dialog> dialogs = new ArrayList<>();
    /** Every message across every conversation, in no particular order - getDialogs wants the
     *  latest one per dialog alongside the dialog list; getHistory wants a peer's own slice. */
    public final ArrayList<TLRPC.TL_message> allMessages = new ArrayList<>();

    private static final String[] FIRST_NAMES_M = {"Артём", "Максим", "Иван", "Дмитрий", "Сергей", "Егор", "Никита", "Павел"};
    private static final String[] FIRST_NAMES_F = {"Дарья", "Ольга", "Света", "Анна", "Мария", "Катя", "Юля", "Настя"};
    private static final String[] LAST_NAMES = {"Волков", "Кузнецов", "Соколов", "Морозов", "Попов", "Егоров", "Новиков", "Фёдоров", "Романов", "Беляев"};

    /** Ten distinct, ordinary-sounding 1:1 threads - logistics, favors, catching up, nothing
     *  scripted-sounding. Alternates who speaks last so half the previews end on an outgoing
     *  message and half on an incoming one, like a real chat list actually looks. */
    private static final String[][] SCRIPTS = {
            {"Слушай, ты вчера говорил про мастера по холодильнику, скинь контакт?",
             "Да, сейчас найду",
             "+7 916 ХХХ-ХХ-ХХ, зовут Игорь, скажи что от меня",
             "Красава, спасибо"},
            {"Ты завтра во сколько освободишься?",
             "Часам к семи примерно",
             "Погнали тогда в семь у метро",
             "Договорились"},
            {"Забыл дома зарядку от ноута, у тебя такая же случайно нет?",
             "Есть, могу занести после работы",
             "Спаситель просто",
             "🙂"},
            {"С днём рождения! Здоровья, всего самого лучшего 🎉",
             "Спасибо большое!",
             "Как отмечаешь?",
             "Тихо, дома с семьёй в основном"},
            {"Скинь, пожалуйста, фотки с той поездки, хочу показать родителям",
             "Ща, дома скину, на компе они",
             "Ок, не горит",
             "Скинул, смотри почту"},
            {"Слушай, можешь завтра подменить меня на смене? Приболел что-то",
             "Могу, во сколько начало?",
             "В девять, до шести",
             "Понял, разберусь, выздоравливай"},
            {"Давно не виделись, как сам?",
             "Да норм, закрутился с переездом",
             "О, переехал уже? Куда",
             "На Юго-Запад, район спокойный, нравится"},
            {"Заказ пришёл, будешь дома чтобы принять?",
             "Буду, весь день дома",
             "Ок, курьер часам к трём должен",
             "Понял, встречу"},
            {"Ты в отпуск в итоге куда решил?",
             "Ещё думаю, между Грузией и Турцией",
             "Грузия вроде дешевле сейчас",
             "Да, склоняюсь туда, посмотрю билеты на неделе"},
            {"Можешь глянуть мой текст перед отправкой, боюсь накосячить",
             "Кидай",
             "Отправила в личку файлом",
             "Гляну сегодня вечером, отпишусь"},
            {"У тебя есть номер стоматолога, к которому ходишь?",
             "Да, скину",
             "Спасибо, а то мой в отпуске",
             "Она хорошая, но записывают недели за две вперёд, имей в виду"},
            {"Мы на субботу договаривались или это перенесли?",
             "Нет, всё в силе, во сколько тебе удобно",
             "Часа в два норм?",
             "Отлично, увидимся"},
    };

    /** Two groups, both real coherent threads with the self account actually participating (not
     *  just a silent observer of other people's conversation) - that's what makes a message land
     *  on the correct, outgoing side of the chat UI instead of every single line in the group
     *  looking like it came from someone else. -1 = spoken by self. */
    private static final int[] MAIN_GROUP_SPEAKERS = {0, 1, -1, 2, 0, 1, -1, 3, 0, 2, -1, 1};
    private static final String[] MAIN_GROUP_LINES = {
            "Ребят, что по субботе? Го на шашлыки за город",
            "О, я за, погоду вроде обещают нормальную",
            "Давайте, только я машину не успею забрать, кто-то может подвезти?",
            "Не вопрос, заеду за тобой часам к 11",
            "Тогда сбор в 12 у меня во дворе",
            "Мясо я возьму, замариную с вечера",
            "Я тогда салаты и напитки прихвачу",
            "А мангал у кого, брать свой?",
            "Мангал есть, дрова тоже, не парьтесь",
            "Погнали тогда 🔥",
            "До субботы, ребят!",
            "До встречи 👋",
    };

    private static final int[] SECOND_GROUP_SPEAKERS = {4, -1, 5, -1};
    private static final String[] SECOND_GROUP_LINES = {
            "Как добрались, всё нормально?",
            "Да, доехали спокойно, уже дома",
            "Ужин будет готов к восьми, приходи не опаздывай",
            "Хорошо, буду примерно без пятнадцати",
    };

    private static final String[] CHANNEL_LINES = {
            "Новый пост уже на сайте, ссылка в комментариях",
            "Небольшое обновление по расписанию на следующую неделю, смотрите закреп",
            "Спасибо всем, кто был вчера — получилось круто 🙏",
            "Отвечаем на частые вопросы в следующем посте, накидывайте свои в комментарии",
            "Небольшой технический перерыв ночью, не пугайтесь если что-то не грузится",
    };

    /** Roughly how long ago each private dialog's last message landed, oldest last - spans
     *  minutes to two weeks so the chat list doesn't read as one uniform burst of activity. */
    private static final int[] AGO_SECONDS = {
            5 * 60, 40 * 60, 2 * 3600, 6 * 3600,
            22 * 3600, 30 * 3600, 2 * 86400, 3 * 86400,
            5 * 86400, 6 * 86400, 9 * 86400, 13 * 86400,
    };

    private PrimeDecoyPersona(TLRPC.TL_user self, TLRPC.TL_channel channel) {
        this.self = self;
        this.channel = channel;
    }

    public static PrimeDecoyPersona generate(long seed) {
        final Random r = new Random(seed);
        final long baseId = 5_000_000_000L + (Math.abs(r.nextLong()) % 900_000_000L);

        final TLRPC.TL_user self = new TLRPC.TL_user();
        self.id = baseId;
        self.self = true;
        boolean selfMale = r.nextBoolean();
        self.first_name = pick(r, selfMale ? FIRST_NAMES_M : FIRST_NAMES_F);
        self.last_name = pick(r, LAST_NAMES) + (selfMale ? "" : "а");
        self.username = null;
        self.phone = fakeRuPhone(r);
        self.status = new TLRPC.TL_userStatusOnline();

        final TLRPC.TL_channel channel = new TLRPC.TL_channel();
        channel.id = baseId + 2;
        channel.title = "Новости";
        channel.broadcast = true;
        channel.megagroup = false;
        channel.signature_profiles = false;
        channel.date = nowMinusDays(r, 400);
        channel.participants_count = 50 + r.nextInt(500);

        final PrimeDecoyPersona persona = new PrimeDecoyPersona(self, channel);

        final TLRPC.TL_chat mainGroup = new TLRPC.TL_chat();
        mainGroup.id = baseId + 1;
        mainGroup.title = "Наш чат";
        mainGroup.participants_count = 6 + r.nextInt(5);
        mainGroup.date = nowMinusDays(r, 200);
        mainGroup.version = 1;
        persona.groups.add(mainGroup);

        final TLRPC.TL_chat secondGroup = new TLRPC.TL_chat();
        secondGroup.id = baseId + 3;
        secondGroup.title = "Семья";
        secondGroup.participants_count = 3 + r.nextInt(3);
        secondGroup.date = nowMinusDays(r, 300);
        secondGroup.version = 1;
        persona.groups.add(secondGroup);

        int contactCount = SCRIPTS.length;
        for (int i = 0; i < contactCount; i++) {
            final TLRPC.TL_user u = new TLRPC.TL_user();
            u.id = baseId + 10 + i;
            boolean male = r.nextBoolean();
            u.first_name = pick(r, male ? FIRST_NAMES_M : FIRST_NAMES_F);
            u.last_name = pick(r, LAST_NAMES) + (male ? "" : "а");
            u.contact = true;
            u.mutual_contact = true;
            u.phone = fakeRuPhone(r);
            u.status = r.nextInt(3) == 0 ? new TLRPC.TL_userStatusOnline() : new TLRPC.TL_userStatusRecently();
            persona.contacts.add(u);
        }

        int nowDate = (int) (System.currentTimeMillis() / 1000L);
        long[] midCounter = {0};

        // Private conversations, one per contact/script pair, spread across a realistic spread
        // of recency (see AGO_SECONDS) rather than every thread being minutes apart from the last.
        for (int i = 0; i < persona.contacts.size(); i++) {
            final TLRPC.TL_user u = persona.contacts.get(i);
            final String[] script = SCRIPTS[i];
            int ago = AGO_SECONDS[i % AGO_SECONDS.length];
            int date = nowDate - ago;
            TLRPC.TL_message last = null;
            for (int j = 0; j < script.length; j++) {
                // Threads alternate which side opens - odd-length or even-length scripts both
                // end up with a natural mix of who sent the last message in each preview.
                boolean out = j % 2 == 1;
                TLRPC.TL_message m = buildMessage(++midCounter[0], out ? self.id : u.id, u.id, script[j], date + j * 45, out);
                persona.allMessages.add(m);
                last = m;
            }
            persona.dialogs.add(buildDialog(last, u.id, false));
        }

        // The two groups - each a coherent thread the self account actually takes part in.
        buildGroupThread(persona, mainGroup, MAIN_GROUP_SPEAKERS, MAIN_GROUP_LINES, nowDate - 1800, midCounter);
        buildGroupThread(persona, secondGroup, SECOND_GROUP_SPEAKERS, SECOND_GROUP_LINES, nowDate - 5 * 3600, midCounter);

        // The channel - posts are "from the channel" (post = true), not from a person.
        {
            int date = nowDate - 3 * 3600;
            TLRPC.TL_message last = null;
            long channelDialogId = -channel.id;
            for (int j = 0; j < CHANNEL_LINES.length; j++) {
                TLRPC.TL_message m = buildChannelPost(++midCounter[0], channel.id, CHANNEL_LINES[j], date + j * 600);
                persona.allMessages.add(m);
                last = m;
            }
            persona.dialogs.add(buildDialog(last, channelDialogId, true));
        }

        return persona;
    }

    private static void buildGroupThread(PrimeDecoyPersona persona, TLRPC.TL_chat group, int[] speakers, String[] lines, int baseDate, long[] midCounter) {
        TLRPC.TL_message last = null;
        for (int j = 0; j < lines.length; j++) {
            long fromId = speakers[j] < 0 ? persona.self.id : persona.contacts.get(speakers[j] % persona.contacts.size()).id;
            boolean out = speakers[j] < 0;
            TLRPC.TL_message m = buildGroupMessage(++midCounter[0], -group.id, fromId, lines[j], baseDate + j * 40, out);
            persona.allMessages.add(m);
            last = m;
        }
        persona.dialogs.add(buildDialog(last, -group.id, false));
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

    /** A phone number shaped like a real Russian mobile number (+7 9XX XXX-XX-XX) - never dialed,
     *  never sent anywhere, purely what the profile/sidebar display. */
    private static String fakeRuPhone(Random r) {
        final int[] prefixes = {901, 903, 905, 909, 916, 917, 926, 929, 950, 963, 977, 985};
        int prefix = prefixes[r.nextInt(prefixes.length)];
        int rest = 1_000_000 + r.nextInt(9_000_000);
        return "7" + prefix + rest;
    }
}
