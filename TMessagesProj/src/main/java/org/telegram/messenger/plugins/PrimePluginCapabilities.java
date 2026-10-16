package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * PrimeGram: what a plugin's code says it can do, read before it is installed.
 *
 * <p>A static read - nothing is imported or run. It answers "what does this plugin reach for", so
 * the install sheet can say so before the user agrees, instead of the blanket "a plugin can do
 * anything" warning that everyone learns to skip. It is not a sandbox: a plugin that hides what it
 * does from a text scan can still do it. That is what {@link #obfuscation} is for - hiding is
 * itself the thing worth telling the user about.
 *
 * <p>Source ({@code .py}) is matched with syntax-aware patterns. Compiled modules ({@code .pyc},
 * which elyxbuilder produces for every ordinary {@code .elyx}) are not skipped: the names a module
 * uses and its string constants sit in the marshal stream as plain text, so they are pulled out
 * ({@link #pycTokens}) and matched by name. That is weaker than source - it sees which functions
 * exist, not how they are called - but it is a long way from "unknown", and a compiled package is
 * no longer reported as hidden code just for being compiled.
 *
 * <p>Deliberately Java rather than a Python AST pass: the install sheet opens before the Python
 * runtime is necessarily up, and a set of regular expressions over a few MB is milliseconds.
 */
public final class PrimePluginCapabilities {

    public enum Capability {
        SESSION("Получить доступ к ключу авторизации и файлам сессии — этого достаточно, чтобы увести аккаунт", true),
        DYNAMIC_CODE("Выполнять код, которого нет в файле плагина", true),
        PROCESSES("Запускать системные команды", true),
        NATIVE("Загружать нативный код", true),
        PAYMENTS("Тратить звёзды и совершать платежи", false),
        MESSAGES("Отправлять, изменять и удалять сообщения от вашего имени", false),
        ACCOUNT("Читать данные аккаунта, переписку и делать любые запросы к Telegram", false),
        CONTACTS("Читать контакты телефона", false),
        LOCATION("Получать геолокацию", false),
        CLIPBOARD("Читать буфер обмена", false),
        FILES("Читать и записывать файлы", false),
        HOOKS("Встраиваться в работу приложения (перехват методов)", false),
        NETWORK("Выходить в интернет", false);

        public final String title;
        /** Enough on its own to make the plugin high-risk. */
        public final boolean critical;

        Capability(String title, boolean critical) {
            this.title = title;
            this.critical = critical;
        }
    }

    public enum Risk {
        LOW("Риск: низкий"),
        MEDIUM("Риск: средний"),
        HIGH("Риск: высокий");

        public final String title;

        Risk(String title) {
            this.title = title;
        }
    }

    public final EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
    /** Human-readable reasons the code looks deliberately hidden; empty when it does not. */
    public final List<String> obfuscation = new ArrayList<>();
    /** Hosts and IP addresses the code mentions in URLs, in the order found. */
    public final Set<String> hosts = new LinkedHashSet<>();
    /** Requirements the plugin declares ({@code __requirements__}) - fetched from PyPI on install. */
    public final Set<String> requirements = new LinkedHashSet<>();
    /** Top-level imports this app does not ship - the loader tries PyPI for each on first load. */
    public final Set<String> fetchable = new LinkedHashSet<>();
    /** How many {@code .pyc} modules were read by name instead of by source. */
    public int compiledFiles;
    /** False when there was nothing readable to scan (unreadable archive, empty file). */
    public boolean scanned;
    public Risk risk = Risk.LOW;
    /** One line explaining {@link #risk}; null for a low risk. */
    public String riskReason;

    private static final int MAX_SCAN_BYTES = 6 * 1024 * 1024;

    // Source patterns: syntax-aware, over .py text with whole-line comments removed.
    private static final Object[][] SOURCE_PATTERNS = {
            // Not getFilesDirFixed(): plugins keep their own fonts and caches under the app's files
            // directory, so the directory alone says nothing; the session files in it do.
            {Capability.SESSION, "tgnet\\.dat|\\bauth_?[kK]ey\\b|getAuthKey|userconfi?n?g\\.xml|\\bcache4\\.db|/data/(data|user/\\d+)/org\\.telegram\\.messenger[^/'\"]*/(files|shared_prefs)|shared_prefs/userconf"},
            {Capability.DYNAMIC_CODE, "(?<![.\\w])(exec|eval)\\s*\\(\\s*(?![\"'rbfu]{0,2}[\"'])|\\b__import__\\s*\\(\\s*(?![\"'])|\\bimportlib\\.import_module\\s*\\(\\s*(?![\"'])|\\bcompile\\s*\\([^)]*['\"]exec['\"]"},
            {Capability.PROCESSES, "\\bsubprocess\\b|\\bos\\.(system|popen|exec[lv]p?e?|spawn\\w*|fork)\\b|Runtime\\.getRuntime\\(\\)\\.exec|\\bProcessBuilder\\b|\\bpty\\b"},
            {Capability.NATIVE, "\\bctypes\\b|\\bcffi\\b|System\\.load(Library)?\\s*\\(|\\.so['\"]|\\bdlopen\\b"},
            // Spending, not merely showing a balance (a plugin reading StarsController for a badge is not paying).
            {Capability.PAYMENTS, "\\bTL_payments_(send|transfer|convert|upgrade|buy)\\w*|sendStarsPayment|\\bTL_inputInvoice\\w*|\\bTL_inputStorePayment\\w*"},
            {Capability.MESSAGES, "\\bsend_(message|text|photo|video|document|audio|sticker|media)\\s*\\(|\\bedit_message\\s*\\(|\\bsendMessage\\b|\\bdeleteMessages\\b|\\bdeleteDialog\\b|TL_messages_(send|edit|forward|delete)\\w*"},
            {Capability.ACCOUNT, "\\bsend_request\\s*\\(|\\bsendRequest\\s*\\(|\\bget_user_config\\s*\\(|getCurrentUser\\s*\\(|getClientUserId|getClientPhone|\\bget_messages_storage\\s*\\(|\\bMessagesStorage\\b|\\bTL_(account|auth|users|contacts|messages_getHistory|messages_search)\\w*"},
            {Capability.CONTACTS, "\\bContactsContract\\b|READ_CONTACTS"},
            {Capability.LOCATION, "\\bLocationManager\\b|getLastKnownLocation|requestLocationUpdates|FusedLocation\\w*|ACCESS_(FINE|COARSE)_LOCATION"},
            {Capability.CLIPBOARD, "getPrimaryClip\\b|\\bpyperclip\\b|getText\\s*\\(\\s*\\)\\s*.*clipboard|clipboard\\w*\\.\\s*getPrimaryClip"},
            {Capability.FILES, "(?<![.\\w])open\\s*\\(|\\bos\\.(remove|unlink|rename|makedirs|listdir|walk|scandir|rmdir)\\b|\\bshutil\\.|\\bpathlib\\b|java\\.io\\.File|\\bFileOutputStream\\b|\\b(read|write)_file(_bytes)?\\s*\\("},
            {Capability.HOOKS, "\\bhook_(method|all_methods|all_constructors)\\s*\\(|XposedBridge|\\bXC_MethodHook\\b|\\badd_hook\\s*\\(|\\bClassBuilder\\b"},
            {Capability.NETWORK, "\\bimport\\s+(requests|urllib3?|http\\.client|socket|aiohttp|httpx|ssl|websockets?)\\b|\\bfrom\\s+(requests|urllib3?|http|socket|aiohttp|httpx|websockets?)\\b|java\\.net\\b|okhttp3?\\b|\\bHttpURLConnection\\b|\\burlopen\\s*\\(|\\bWebView\\b|\\bloadUrl\\s*\\("},
    };

    // Name patterns: one token per line, as pulled out of a .pyc - names and string constants.
    private static final Object[][] NAME_PATTERNS = {
            {Capability.SESSION, "(?m)tgnet\\.dat|^auth_?[kK]ey$|getAuthKey|userconfi?n?g\\.xml|cache4\\.db|/data/(data|user/\\d+)/org\\.telegram\\.messenger[^/]*/(files|shared_prefs)|shared_prefs/userconf"},
            {Capability.PROCESSES, "(?m)^(subprocess|popen|ProcessBuilder|execv|execvp|execve|spawnv|spawnvp|pty)$"},
            {Capability.NATIVE, "(?m)^(ctypes|cffi|loadLibrary|dlopen|CDLL)$|\\.so$"},
            {Capability.PAYMENTS, "(?m)^TL_payments_(send|transfer|convert|upgrade|buy)\\w*$|^sendStarsPayment$|^TL_inputInvoice\\w*$|^TL_inputStorePayment\\w*$"},
            {Capability.MESSAGES, "(?m)^(send_message|send_text|send_photo|send_video|send_document|send_audio|send_sticker|edit_message|sendMessage|deleteMessages|deleteDialog)$|^TL_messages_(send|edit|forward|delete)\\w*$"},
            {Capability.ACCOUNT, "(?m)^(send_request|sendRequest|get_user_config|getCurrentUser|getClientUserId|getClientPhone|get_messages_storage|MessagesStorage)$|^TL_(account|auth|users|contacts|messages_getHistory|messages_search)\\w*$"},
            {Capability.CONTACTS, "(?m)^(ContactsContract|READ_CONTACTS)$"},
            {Capability.LOCATION, "(?m)^(LocationManager|getLastKnownLocation|requestLocationUpdates|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION)$"},
            {Capability.CLIPBOARD, "(?m)^(getPrimaryClip|pyperclip)$"},
            {Capability.FILES, "(?m)^(open|remove|unlink|makedirs|listdir|scandir|rmdir|rmtree|shutil|pathlib|FileOutputStream|read_file|write_file|read_file_bytes|write_file_bytes)$"},
            {Capability.HOOKS, "(?m)^(hook_method|hook_all_methods|hook_all_constructors|XposedBridge|XC_MethodHook|add_hook|ClassBuilder)$"},
            {Capability.NETWORK, "(?m)^(requests|urllib|urllib3|urlopen|socket|aiohttp|httpx|websocket|websockets|HttpURLConnection|okhttp3|WebView|loadUrl)$|^(urllib\\.request|http\\.client|java\\.net)"},
    };
    // Embedded DEX, matched over its string pool: class descriptors (Ljava/net/URL;) and member names.
    private static final Object[][] DEX_PATTERNS = {
            {Capability.SESSION, "tgnet\\.dat|getAuthKey|userconfi?n?g\\.xml|cache4\\.db"},
            {Capability.DYNAMIC_CODE, "Ldalvik/system/(Dex|InMemoryDex|Path|Base)ClassLoader;|Ljava/net/URLClassLoader;"},
            {Capability.PROCESSES, "Ljava/lang/ProcessBuilder;|(?s)Ljava/lang/Runtime;.*\\nexec\\n"},
            {Capability.NATIVE, "\\nloadLibrary\\n|\\nnativeLoad\\n"},
            {Capability.PAYMENTS, "TL_payments_(send|transfer|convert|upgrade|buy)\\w*|TL_inputInvoice"},
            {Capability.MESSAGES, "Lorg/telegram/messenger/SendMessagesHelper;|TL_messages_(send|edit|forward|delete)\\w*|\\ndeleteMessages\\n"},
            {Capability.ACCOUNT, "Lorg/telegram/messenger/MessagesStorage;|\\nsendRequest\\n|\\ngetClientUserId\\n|\\ngetClientPhone\\n|TL_(account|auth|contacts)_\\w+"},
            {Capability.CONTACTS, "Landroid/provider/ContactsContract"},
            {Capability.LOCATION, "Landroid/location/LocationManager;"},
            {Capability.CLIPBOARD, "\\ngetPrimaryClip\\n"},
            {Capability.FILES, "Ljava/io/FileOutputStream;|Ljava/io/RandomAccessFile;|Ljava/nio/file/Files;"},
            {Capability.NETWORK, "Ljava/net/(URL|Socket|HttpURLConnection);|Ljavax/net/ssl/|Lokhttp3/|Landroid/webkit/WebView;"},
    };
    private static final Pattern[] SOURCE_COMPILED = compileAll(SOURCE_PATTERNS);
    private static final Pattern[] NAME_COMPILED = compileAll(NAME_PATTERNS);
    private static final Pattern[] DEX_COMPILED = compileAll(DEX_PATTERNS);
    /** Total size of Java bytecode (DEX) found embedded as base64 and scanned by name. */
    public int embeddedDexBytes;
    /** Parts of the app the code names, by {@link PrimeHookCatalog} simple class name. */
    public final Set<String> touched = new LinkedHashSet<>();
    private static final Pattern KNOWN_CLASS = Pattern.compile(
            "\\b(" + String.join("|", PrimeHookCatalog.knownSimpleNames()).replace("$", "\\$") + ")\\b");
    private static final Pattern QUALIFIED_CLASS = Pattern.compile("L?org[./]telegram[./][A-Za-z0-9_./$]+|L?androidx?[./][A-Za-z0-9_./$]+");

    // Running text that was only a string a moment ago - the shape almost every hidden payload
    // takes, and one ordinary plugins have no reason to use.
    private static final Pattern DYNAMIC_EXEC = Pattern.compile(
            "\\b(exec|eval)\\s*\\(\\s*(compile\\s*\\(|(base64|zlib|lzma|bz2|codecs|marshal|bytes\\.fromhex|binascii)\\b|[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*decode\\s*\\()");
    private static final Pattern MARSHAL = Pattern.compile("\\bmarshal\\.loads\\s*\\(|\\bimportlib\\.util\\.spec_from_loader|\\btypes\\.CodeType\\s*\\(");
    private static final Pattern LONG_BLOB = Pattern.compile("['\"][A-Za-z0-9+/=_\\\\x-]{2000,}['\"]");
    private static final Pattern ESCAPED = Pattern.compile("(\\\\x[0-9a-fA-F]{2}){200,}");

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?|wss?|ftp)://(?:[^\\s/@'\"]+@)?((?:[a-z0-9-]+\\.)+[a-z]{2,24}|\\d{1,3}(?:\\.\\d{1,3}){3}|\\[[0-9a-f:]+\\])(?::(\\d{1,5}))?");
    private static final Pattern BARE_IP_PORT = Pattern.compile("(?<![\\d.])(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{2,5})(?![\\d.])");
    private static final Pattern REQUIREMENTS_LIST = Pattern.compile("__requirements__\\s*=\\s*[\\[(]([^\\])]*)[\\])]");
    private static final Pattern REQUIREMENTS_STRING = Pattern.compile("__requirements__\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern QUOTED = Pattern.compile("[\"']([^\"']+)[\"']");
    private static final Pattern IMPORT = Pattern.compile("(?m)^[ \\t]*(?:import|from)[ \\t]+([A-Za-z_][A-Za-z0-9_]*)");

    private PrimePluginCapabilities() {
    }

    private static Pattern[] compileAll(Object[][] patterns) {
        final Pattern[] result = new Pattern[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            result[i] = Pattern.compile((String) patterns[i][1]);
        }
        return result;
    }

    /** Scans a single-file plugin or a package ({@code .elyx}, {@code .eaf}). Never throws. */
    public static PrimePluginCapabilities scan(File file) {
        final PrimePluginCapabilities result = new PrimePluginCapabilities();
        if (file == null || !file.isFile()) {
            return result;
        }
        final Set<String> ownModules = new HashSet<>();
        final Set<String> imports = new LinkedHashSet<>();
        try {
            if (PrimePluginsController.isPackageFileName(file.getName())) {
                try (ZipFile zip = new ZipFile(file)) {
                    // The package's own top-level names first, so its internal imports are not
                    // mistaken for something to download.
                    for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                        collectOwnModules(e.nextElement().getName(), ownModules);
                    }
                    final Enumeration<? extends ZipEntry> entries = zip.entries();
                    int budget = MAX_SCAN_BYTES;
                    while (entries.hasMoreElements() && budget > 0) {
                        final ZipEntry entry = entries.nextElement();
                        final String name = entry.getName();
                        if (entry.isDirectory()) {
                            continue;
                        }
                        if (name.endsWith(".so") || name.endsWith(".dex") || name.endsWith(".jar")) {
                            result.capabilities.add(Capability.NATIVE);
                            result.addObfuscation("В пакете лежит машинный код (" + shortName(name) + "), его содержимое не проверить");
                            continue;
                        }
                        if (name.endsWith(".pyc")) {
                            try (InputStream in = zip.getInputStream(entry)) {
                                final byte[] data = readBytes(in, budget);
                                budget -= data.length;
                                result.compiledFiles++;
                                result.analyzeCompiled(data);
                            }
                            continue;
                        }
                        if (!name.endsWith(".py")) {
                            continue;
                        }
                        try (InputStream in = zip.getInputStream(entry)) {
                            final String text = new String(readBytes(in, budget), StandardCharsets.UTF_8);
                            budget -= text.length();
                            result.analyzeSource(text, imports);
                        }
                    }
                }
            } else {
                try (InputStream in = new FileInputStream(file)) {
                    result.analyzeSource(new String(readBytes(in, MAX_SCAN_BYTES), StandardCharsets.UTF_8), imports);
                }
            }
        } catch (Throwable t) {
            FileLog.e("PrimePluginCapabilities.scan " + file, new Exception(t));
        }
        for (String module : imports) {
            if (!ownModules.contains(module) && !isAvailable(module) && !result.requirements.contains(module)) {
                result.fetchable.add(module);
            }
        }
        result.assessRisk();
        return result;
    }

    private void analyzeSource(String text, Set<String> imports) {
        if (text.isEmpty()) {
            return;
        }
        scanned = true;
        final String code = classifyBlobs(stripComments(text));
        for (int i = 0; i < SOURCE_PATTERNS.length; i++) {
            final Capability capability = (Capability) SOURCE_PATTERNS[i][0];
            if (!capabilities.contains(capability) && SOURCE_COMPILED[i].matcher(code).find()) {
                capabilities.add(capability);
            }
        }
        if (DYNAMIC_EXEC.matcher(code).find()) {
            addObfuscation("Выполняет код, собранный из закодированной строки во время работы");
        }
        if (MARSHAL.matcher(code).find()) {
            addObfuscation("Загружает спрятанный внутри байткод");
        }
        if (ESCAPED.matcher(code).find()) {
            addObfuscation("Содержит большой закодированный фрагмент данных");
        }
        if (unreadableShare(code) > 0.3f) {
            addObfuscation("Большая часть кода нечитаема");
        }
        collectHosts(code);
        collectTouched(code);
        collectRequirements(code);
        final Matcher importMatcher = IMPORT.matcher(code);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }
    }

    private void analyzeCompiled(byte[] data) {
        final List<String> tokens = pycTokens(data);
        if (tokens.isEmpty()) {
            return;
        }
        scanned = true;
        final StringBuilder joined = new StringBuilder();
        final Set<String> names = new HashSet<>();
        for (String token : tokens) {
            joined.append(token).append('\n');
            names.add(token);
        }
        final String text = joined.toString();
        for (int i = 0; i < NAME_PATTERNS.length; i++) {
            final Capability capability = (Capability) NAME_PATTERNS[i][0];
            if (!capabilities.contains(capability) && NAME_COMPILED[i].matcher(text).find()) {
                capabilities.add(capability);
            }
        }
        // Names alone cannot show how exec is called, only that decoding and executing meet in
        // one module - the combination hidden payloads need.
        final boolean executes = names.contains("exec") || names.contains("eval");
        if (executes && (names.contains("b64decode") || names.contains("decompress") || names.contains("unhexlify") || names.contains("fromhex"))) {
            addObfuscation("Выполняет код, собранный из закодированной строки во время работы");
        } else if (executes || names.contains("__import__") || names.contains("import_module")) {
            capabilities.add(Capability.DYNAMIC_CODE);
        }
        if (names.contains("marshal") && names.contains("loads")) {
            addObfuscation("Загружает спрятанный внутри байткод");
        }
        for (String token : tokens) {
            if (token.length() > 2000 && token.matches("[A-Za-z0-9+/=_-]+")) {
                classifyBlob(token);
            } else if (token.length() < 200) {
                addTouched(token);
            }
        }
        collectHosts(text);
    }

    /**
     * Long base64 literals, one by one: embedded Java bytecode (DEX) is decoded and scanned by its
     * string pool; images and stickers are just data; anything else stays reported as an encoded
     * payload. Returns the code with every blob blanked out, so a single long data line no longer
     * makes the whole file look "unreadable" either.
     */
    private String classifyBlobs(String code) {
        final Matcher blob = LONG_BLOB.matcher(code);
        if (!blob.find()) {
            return code;
        }
        final StringBuilder out = new StringBuilder(code.length());
        int last = 0;
        do {
            classifyBlob(code.substring(blob.start() + 1, blob.end() - 1));
            out.append(code, last, blob.start()).append("''");
            last = blob.end();
        } while (blob.find());
        out.append(code, last, code.length());
        return out.toString();
    }

    private void classifyBlob(String base64) {
        final byte[] head = decodeBase64(base64.substring(0, Math.min(base64.length(), 64)));
        if (head == null) {
            addObfuscation("Содержит большой закодированный фрагмент данных");
            return;
        }
        if (startsWith(head, "dex\n")) {
            final byte[] dex = decodeBase64(base64);
            if (dex != null) {
                embeddedDexBytes += dex.length;
                analyzeDex(dex);
                return;
            }
        }
        if (isMedia(head)) {
            return;
        }
        addObfuscation("Содержит большой закодированный фрагмент данных");
    }

    /** Images, stickers, fonts, archives of them: data a plugin ships, not code it hides. */
    private static boolean isMedia(byte[] head) {
        return startsWith(head, "\u0089PNG") || (head.length > 2 && (head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8)
                || startsWith(head, "GIF8") || startsWith(head, "RIFF") || startsWith(head, "<svg") || startsWith(head, "<?xml")
                || (head.length > 1 && (head[0] & 0xff) == 0x1f && (head[1] & 0xff) == 0x8b) // gzip: .tgs stickers
                || startsWith(head, "{") || startsWith(head, "OTTO") || startsWith(head, "wOF2") || startsWith(head, "wOFF")
                || (head.length > 3 && head[0] == 0 && head[1] == 1 && head[2] == 0 && head[3] == 0); // ttf
    }

    private void analyzeDex(byte[] dex) {
        // The string pool is MUTF-8 runs; printable runs of 3+ recover it well enough to match names.
        final StringBuilder text = new StringBuilder("\n");
        int runStart = -1;
        for (int i = 0; i <= dex.length; i++) {
            final int b = i < dex.length ? dex[i] & 0xff : 0;
            final boolean printable = b >= 0x20 && b < 0x7f;
            if (printable && runStart < 0) {
                runStart = i;
            } else if (!printable && runStart >= 0) {
                if (i - runStart >= 3) {
                    text.append(new String(dex, runStart, i - runStart, StandardCharsets.US_ASCII)).append('\n');
                }
                runStart = -1;
            }
        }
        final String pool = text.toString();
        for (int i = 0; i < DEX_PATTERNS.length; i++) {
            final Capability capability = (Capability) DEX_PATTERNS[i][0];
            if (!capabilities.contains(capability) && DEX_COMPILED[i].matcher(pool).find()) {
                capabilities.add(capability);
            }
        }
        collectHosts(pool);
        for (String line : pool.split("\n")) {
            if (line.startsWith("Lorg/telegram/") || line.startsWith("Landroid")) {
                addTouched(line);
            }
        }
    }

    private void collectTouched(String code) {
        final Matcher known = KNOWN_CLASS.matcher(code);
        while (known.find()) {
            touched.add(known.group(1));
        }
        final Matcher qualified = QUALIFIED_CLASS.matcher(code);
        while (qualified.find()) {
            addTouched(qualified.group());
        }
    }

    private void addTouched(String name) {
        final PrimeHookCatalog.Entry entry = PrimeHookCatalog.find(name);
        if (entry != null) {
            touched.add(PrimeHookCatalog.simpleName(entry.className));
        }
    }

    private static boolean startsWith(byte[] data, String prefix) {
        if (data.length < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if ((data[i] & 0xff) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Plain and URL-safe base64, padding optional; null on anything else. Own code: java.util.Base64 is API 26. */
    static byte[] decodeBase64(String s) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() * 3 / 4);
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            final int v;
            if (c >= 'A' && c <= 'Z') {
                v = c - 'A';
            } else if (c >= 'a' && c <= 'z') {
                v = c - 'a' + 26;
            } else if (c >= '0' && c <= '9') {
                v = c - '0' + 52;
            } else if (c == '+' || c == '-') {
                v = 62;
            } else if (c == '/' || c == '_') {
                v = 63;
            } else if (c == '=' || c == '\n' || c == '\r' || c == '\\') {
                continue;
            } else {
                return null;
            }
            buffer = buffer << 6 | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write(buffer >> bits & 0xff);
            }
        }
        return out.toByteArray();
    }

    /**
     * The strings in a {@code .pyc}'s marshal stream: identifiers (every name a module uses -
     * imports, attributes, calls) and string constants. Marshal writes each as a type byte (the
     * high bit is a reference flag), a length, then the bytes; ASCII ones dominate. Matching that
     * shape over the raw bytes recovers them without a full unmarshaller - stray matches inside
     * other data are short noise that no pattern here cares about.
     */
    static List<String> pycTokens(byte[] data) {
        final List<String> tokens = new ArrayList<>();
        int i = 16; // magic, flags, and source mtime/size or hash
        while (i < data.length - 1) {
            final int type = data[i] & 0x7f;
            int length = -1;
            int start = 0;
            if (type == 'z' || type == 'Z') {
                length = data[i + 1] & 0xff;
                start = i + 2;
            } else if ((type == 'a' || type == 'A' || type == 'u' || type == 't') && i + 4 < data.length) {
                length = (data[i + 1] & 0xff) | (data[i + 2] & 0xff) << 8 | (data[i + 3] & 0xff) << 16 | (data[i + 4] & 0xff) << 24;
                start = i + 5;
            }
            if (length >= 2 && length <= 1 << 20 && start + length <= data.length && isText(data, start, length, type == 'u' || type == 't')) {
                tokens.add(new String(data, start, length, StandardCharsets.UTF_8));
                i = start + length;
            } else {
                i++;
            }
        }
        return tokens;
    }

    private static boolean isText(byte[] data, int start, int length, boolean utf8) {
        for (int j = start; j < start + length; j++) {
            final int b = data[j] & 0xff;
            if (b >= 0x80) {
                if (!utf8) {
                    return false;
                }
            } else if (b < 0x20 && b != '\n' && b != '\t' && b != '\r') {
                return false;
            }
        }
        return true;
    }

    private void collectHosts(String text) {
        final Matcher url = URL.matcher(text);
        while (url.find() && hosts.size() < 64) {
            final String host = url.group(1).toLowerCase(Locale.ROOT);
            hosts.add(url.group(2) != null ? host + ":" + url.group(2) : host);
        }
        final Matcher ip = BARE_IP_PORT.matcher(text);
        while (ip.find() && hosts.size() < 64) {
            if (validIp(ip.group(1))) {
                hosts.add(ip.group(1) + ":" + ip.group(2));
            }
        }
    }

    private static boolean validIp(String ip) {
        for (String part : ip.split("\\.")) {
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private void collectRequirements(String code) {
        final Matcher list = REQUIREMENTS_LIST.matcher(code);
        while (list.find()) {
            final Matcher quoted = QUOTED.matcher(list.group(1));
            while (quoted.find()) {
                addRequirement(quoted.group(1));
            }
        }
        final Matcher single = REQUIREMENTS_STRING.matcher(code);
        while (single.find()) {
            for (String part : single.group(1).split("[,\\s]+")) {
                addRequirement(part);
            }
        }
    }

    private void addRequirement(String requirement) {
        final String trimmed = requirement.trim();
        if (!trimmed.isEmpty()) {
            requirements.add(trimmed);
        }
    }

    /** The overall verdict the sheet leads with, and the one reason that decided it. */
    private void assessRisk() {
        if (!obfuscation.isEmpty()) {
            risk = Risk.HIGH;
            riskReason = "Код плагина прячет, что делает";
            return;
        }
        for (Capability capability : capabilities) {
            if (capability.critical) {
                risk = Risk.HIGH;
                riskReason = capability.title;
                return;
            }
        }
        if (capabilities.contains(Capability.NETWORK)
                && (capabilities.contains(Capability.ACCOUNT) || capabilities.contains(Capability.CONTACTS)
                || capabilities.contains(Capability.LOCATION) || capabilities.contains(Capability.CLIPBOARD))) {
            risk = Risk.MEDIUM;
            riskReason = "Читает ваши данные и может отправить их в интернет";
            return;
        }
        for (Capability capability : capabilities) {
            if (capability != Capability.NETWORK && capability != Capability.HOOKS) {
                risk = Risk.MEDIUM;
                riskReason = capability.title;
                return;
            }
        }
        if (!fetchable.isEmpty()) {
            risk = Risk.MEDIUM;
            riskReason = "Докачает из интернета код, которого нет в приложении";
            return;
        }
        risk = Risk.LOW;
        riskReason = null;
    }

    private void addObfuscation(String reason) {
        if (!obfuscation.contains(reason)) {
            obfuscation.add(reason);
        }
    }

    private static void collectOwnModules(String path, Set<String> out) {
        String name = path;
        final int slash = name.indexOf('/');
        if (slash > 0) {
            out.add(name.substring(0, slash));
        }
        // Every directory and module along the way: an .elyx imports itself both as
        // "reSwaga.src.ui" and, inside src/, as "ui".
        for (String segment : name.split("/")) {
            final int dot = segment.indexOf('.');
            out.add(dot > 0 ? segment.substring(0, dot) : segment);
        }
    }

    /** Whether {@code module} needs no download: the standard library, a bundled package, the SDK. */
    private static boolean isAvailable(String module) {
        return STDLIB.contains(module) || BUNDLED.contains(module) || module.startsWith("_");
    }

    private static final Set<String> STDLIB = new HashSet<>(Arrays.asList(
            "abc", "argparse", "array", "ast", "asyncio", "atexit", "base64", "binascii", "bisect", "builtins",
            "bz2", "calendar", "cgi", "cmath", "codecs", "collections", "colorsys", "concurrent", "configparser",
            "contextlib", "contextvars", "copy", "copyreg", "csv", "ctypes", "dataclasses", "datetime", "decimal",
            "difflib", "dis", "email", "encodings", "enum", "errno", "faulthandler", "fnmatch", "fractions",
            "functools", "gc", "getpass", "gettext", "glob", "gzip", "hashlib", "heapq", "hmac", "html", "http",
            "imaplib", "importlib", "inspect", "io", "ipaddress", "itertools", "json", "keyword", "linecache",
            "locale", "logging", "lzma", "mailbox", "marshal", "math", "mimetypes", "mmap", "multiprocessing",
            "numbers", "operator", "os", "pathlib", "pickle", "pkgutil", "platform", "plistlib", "pprint",
            "profile", "pty", "queue", "quopri", "random", "re", "reprlib", "sched", "secrets", "select",
            "selectors", "shelve", "shlex", "shutil", "signal", "site", "smtplib", "socket", "socketserver",
            "sqlite3", "ssl", "stat", "statistics", "string", "stringprep", "struct", "subprocess", "sys",
            "sysconfig", "tarfile", "tempfile", "textwrap", "threading", "time", "timeit", "token", "tokenize",
            "traceback", "tracemalloc", "types", "typing", "unicodedata", "unittest", "urllib", "uuid", "warnings",
            "wave", "weakref", "webbrowser", "xml", "xmlrpc", "zipfile", "zipimport", "zlib", "zoneinfo",
            "__future__", "cProfile", "pstats", "graphlib", "tomllib", "asynchat", "asyncore", "binhex", "chunk",
            "crypt", "imghdr", "nntplib", "pipes", "sndhdr", "sunau", "telnetlib", "uu", "xdrlib", "audioop"));

    /** Bundled pip packages (TMessagesProj/build.gradle), their dependencies, and the plugin SDK. */
    private static final Set<String> BUNDLED = new HashSet<>(Arrays.asList(
            "requests", "httpx", "bs4", "dateutil", "packaging", "lxml", "regex", "aiohttp", "brotli", "PIL",
            "pyzbar", "cryptography", "Crypto", "nacl", "bcrypt", "tgcrypto", "miniaudio", "soxr", "lameenc",
            "matplotlib", "contourpy", "kiwisolver", "pandas", "pywt", "ephem", "wordcloud", "editdistance",
            "bitarray", "lru", "cytoolz", "toolz", "netifaces", "markupsafe", "greenlet", "argon2", "numpy",
            "yaml", "zstandard", "lz4", "psutil", "six", "certifi", "urllib3", "idna", "charset_normalizer",
            "anyio", "h11", "httpcore", "attr", "attrs", "multidict", "yarl", "frozenlist", "aiosignal",
            "soupsieve", "typing_extensions", "pytz", "tzdata", "cffi", "pycparser", "fontTools", "cycler",
            "pyparsing", "mpl_toolkits", "sniffio", "exceptiongroup", "propcache", "aiohappyeyeballs",
            "java", "android", "androidx", "org", "com", "dalvik", "kotlin", "chaquopy", "de",
            "android_utils", "base_plugin", "client_utils", "file_utils", "hook_utils", "intents",
            "markdown_utils", "plugin_settings", "plugins_manager", "ui", "elyx", "extera_utils"));

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

    private static byte[] readBytes(InputStream in, int limit) throws java.io.IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] chunk = new byte[16 * 1024];
        int total = 0;
        int n;
        while (total < limit && (n = in.read(chunk, 0, Math.min(chunk.length, limit - total))) > 0) {
            buffer.write(chunk, 0, n);
            total += n;
        }
        return buffer.toByteArray();
    }

    private static String shortName(String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
