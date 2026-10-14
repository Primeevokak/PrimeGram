package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * PrimeGram: what a plugin's source says it can do, read before it is installed.
 *
 * <p>A static read of the text - nothing is imported or run. It answers "what does this plugin
 * reach for", so the install sheet can say so before the user agrees, instead of the blanket
 * "a plugin can do anything" warning that everyone learns to skip. It is not a sandbox: a plugin
 * that hides what it does from a text scan can still do it. That is what {@link #obfuscation}
 * is for - hiding is itself the thing worth telling the user about.
 *
 * <p>Deliberately written in Java rather than as a Python AST pass: the install sheet opens before
 * the Python runtime is necessarily up, and a set of regular expressions over a few hundred KB is
 * a few milliseconds.
 */
public final class PrimePluginCapabilities {

    public enum Capability {
        NETWORK("Выходить в интернет"),
        MESSAGES("Отправлять и изменять сообщения от вашего имени"),
        ACCOUNT("Читать данные аккаунта и переписку"),
        FILES("Читать и записывать файлы"),
        HOOKS("Встраиваться в работу приложения (перехват методов)"),
        PROCESSES("Запускать системные команды"),
        NATIVE("Загружать нативный код");

        public final String title;

        Capability(String title) {
            this.title = title;
        }
    }

    public final EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
    /** Human-readable reasons the code looks deliberately hidden; empty when it does not. */
    public final List<String> obfuscation = new ArrayList<>();
    /** False when there was nothing readable to scan (binary payload, unreadable archive). */
    public boolean scanned;

    private static final int MAX_SCAN_BYTES = 4 * 1024 * 1024;

    private static final Object[][] PATTERNS = {
            {Capability.NETWORK, "\\bimport\\s+(requests|urllib3?|http\\.client|socket|aiohttp|httpx|ssl)\\b|\\bfrom\\s+(requests|urllib3?|http|socket|aiohttp|httpx)\\b|java\\.net\\b|okhttp3?\\b|\\bHttpURLConnection\\b|\\burlopen\\s*\\("},
            {Capability.MESSAGES, "\\bsend_(message|text|photo|video|document|audio)\\s*\\(|\\bedit_message\\s*\\(|\\bsendMessage\\b|TL_messages_send|TL_messages_edit|TL_messages_forward|TL_messages_delete"},
            {Capability.ACCOUNT, "\\bget_user_config\\s*\\(|getCurrentUser\\s*\\(|getClientUserId|\\bget_messages_storage\\s*\\(|\\bMessagesStorage\\b|\\bget_contacts_controller\\s*\\(|TL_messages_getHistory|TL_contacts_"},
            {Capability.FILES, "(?<![.\\w])open\\s*\\(|\\bos\\.(remove|unlink|rename|makedirs|listdir|walk|scandir)\\b|\\bshutil\\.|\\bpathlib\\b|java\\.io\\.File|\\bFileOutputStream\\b|\\bread_file(_bytes)?\\s*\\(|\\bwrite_file(_bytes)?\\s*\\("},
            {Capability.HOOKS, "\\bhook_(method|all_methods|all_constructors)\\s*\\(|XposedBridge|\\bXC_MethodHook\\b|\\badd_hook\\s*\\(|\\bClassBuilder\\b"},
            {Capability.PROCESSES, "\\bsubprocess\\b|\\bos\\.(system|popen|exec[lv]p?e?|spawn)|Runtime\\.getRuntime\\(\\)\\.exec|\\bProcessBuilder\\b"},
            {Capability.NATIVE, "\\bctypes\\b|\\bcffi\\b|System\\.load(Library)?\\s*\\(|\\.so['\"]"},
    };
    private static final Pattern[] COMPILED = new Pattern[PATTERNS.length];

    static {
        for (int i = 0; i < PATTERNS.length; i++) {
            COMPILED[i] = Pattern.compile((String) PATTERNS[i][1]);
        }
    }

    // Running text that was only a string a moment ago - the shape almost every hidden payload
    // takes, and one ordinary plugins have no reason to use.
    private static final Pattern DYNAMIC_EXEC = Pattern.compile(
            "\\b(exec|eval)\\s*\\(\\s*(compile\\s*\\(|(base64|zlib|lzma|bz2|codecs|marshal|bytes\\.fromhex|binascii)\\b|[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*decode\\s*\\()");
    private static final Pattern MARSHAL = Pattern.compile("\\bmarshal\\.loads\\s*\\(|\\bimportlib\\.util\\.spec_from_loader|\\btypes\\.CodeType\\s*\\(");
    private static final Pattern LONG_BLOB = Pattern.compile("['\"][A-Za-z0-9+/=_\\\\x-]{2000,}['\"]");
    private static final Pattern ESCAPED = Pattern.compile("(\\\\x[0-9a-fA-F]{2}){200,}");

    private PrimePluginCapabilities() {
    }

    /** Scans a single-file plugin or an {@code .elyx} package. Never throws. */
    public static PrimePluginCapabilities scan(File file) {
        final PrimePluginCapabilities result = new PrimePluginCapabilities();
        if (file == null || !file.isFile()) {
            return result;
        }
        try {
            if (PrimePluginsController.isPackageFileName(file.getName())) {
                try (ZipFile zip = new ZipFile(file)) {
                    final Enumeration<? extends ZipEntry> entries = zip.entries();
                    int budget = MAX_SCAN_BYTES;
                    while (entries.hasMoreElements() && budget > 0) {
                        final ZipEntry entry = entries.nextElement();
                        final String name = entry.getName();
                        if (entry.isDirectory()) {
                            continue;
                        }
                        if (name.endsWith(".pyc") || name.endsWith(".so") || name.endsWith(".dex")) {
                            // Shipping compiled code instead of source is what hiding looks like.
                            result.obfuscation.add("В пакете скомпилированный код вместо исходного (" + shortName(name) + ")");
                            continue;
                        }
                        if (!name.endsWith(".py")) {
                            continue;
                        }
                        try (InputStream in = zip.getInputStream(entry)) {
                            final String text = read(in, budget);
                            budget -= text.length();
                            result.analyze(text);
                        }
                    }
                }
            } else {
                try (InputStream in = new FileInputStream(file)) {
                    result.analyze(read(in, MAX_SCAN_BYTES));
                }
            }
        } catch (Throwable t) {
            FileLog.e("PrimePluginCapabilities.scan " + file, new Exception(t));
        }
        return result;
    }

    private void analyze(String text) {
        if (text.isEmpty()) {
            return;
        }
        scanned = true;
        final String code = stripComments(text);
        for (int i = 0; i < PATTERNS.length; i++) {
            final Capability capability = (Capability) PATTERNS[i][0];
            if (!capabilities.contains(capability) && COMPILED[i].matcher(code).find()) {
                capabilities.add(capability);
            }
        }
        if (DYNAMIC_EXEC.matcher(code).find()) {
            addObfuscation("Выполняет код, собранный из строки во время работы");
        }
        if (MARSHAL.matcher(code).find()) {
            addObfuscation("Загружает заранее скомпилированный байткод");
        }
        if (LONG_BLOB.matcher(code).find() || ESCAPED.matcher(code).find()) {
            addObfuscation("Содержит большой закодированный фрагмент данных");
        }
        if (unreadableShare(code) > 0.3f) {
            addObfuscation("Большая часть кода нечитаема");
        }
    }

    private void addObfuscation(String reason) {
        if (!obfuscation.contains(reason)) {
            obfuscation.add(reason);
        }
    }

    /** Share of the text that sits on absurdly long lines - minified or generated code. */
    private static float unreadableShare(String code) {
        int longChars = 0;
        int start = 0;
        final int length = code.length();
        while (start < length) {
            int end = code.indexOf('\n', start);
            if (end < 0) {
                end = length;
            }
            if (end - start > 1000) {
                longChars += end - start;
            }
            start = end + 1;
        }
        return length == 0 ? 0 : longChars / (float) length;
    }

    /** Drops whole-line comments so an import mentioned in a comment is not reported. */
    private static String stripComments(String text) {
        final StringBuilder out = new StringBuilder(text.length());
        final Matcher lines = Pattern.compile("(?m)^[ \\t]*#.*$").matcher(text);
        int last = 0;
        while (lines.find()) {
            out.append(text, last, lines.start());
            last = lines.end();
        }
        out.append(text, last, text.length());
        return out.toString();
    }

    private static String read(InputStream in, int limit) throws java.io.IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] chunk = new byte[16 * 1024];
        int total = 0;
        int n;
        while (total < limit && (n = in.read(chunk, 0, Math.min(chunk.length, limit - total))) > 0) {
            buffer.write(chunk, 0, n);
            total += n;
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String shortName(String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
