package org.telegram.messenger;

import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * PrimeGram: voice transcription that never leaves the device.
 *
 * <p>The online path needs an API key and sends the recording to somebody else's server. That is
 * the wrong default for a client whose whole point is working where communication is watched, so
 * this exists alongside it: a whisper.cpp model on disk, run locally, with nothing sent anywhere.
 *
 * <p>The native side lives in its own {@code libprimewhisper.so} and is loaded lazily - only once
 * a model has actually been downloaded - so a device that never uses this never maps ggml into
 * memory, and a failure to load it can never take the messenger down.
 */
public class PrimeWhisper {

    public static final String KEY_ENABLED = "primegram_whisper_enabled";
    public static final String KEY_MODEL = "primegram_whisper_model";
    public static final String KEY_LANGUAGE = "primegram_whisper_language";

    public static final int MODEL_TINY = 0;
    public static final int MODEL_BASE = 1;
    public static final int MODEL_SMALL = 2;
    public static final int MODEL_MEDIUM = 3;
    public static final int MODEL_TURBO = 4;

    public static final String[] MODEL_NAMES = {"Tiny", "Base", "Small", "Medium", "Large v3 Turbo"};

    /**
     * What each model costs and buys.
     *
     * <p>All of them are quantised rather than full-precision: on a phone the accuracy difference
     * is small and the size difference is roughly threefold, and a 1.5 GB download for a feature
     * people try once is not a reasonable ask.
     *
     * <p>Large v3 proper is deliberately absent. Its encoder is the same as the turbo variant's
     * and its decoder is eight times deeper, so on a phone it buys a barely measurable amount of
     * accuracy for several times the wait and twice the download. Turbo is what large-quality
     * transcription looks like on a device you hold in your hand.
     */
    public static final String[] MODEL_DESCRIPTIONS = {
        "31 МБ · быстро, разборчивую речь узнаёт",
        "57 МБ · заметно точнее, разумный выбор",
        "181 МБ · точнее всех, но медленно на слабых телефонах",
        "514 МБ · для мощных телефонов, заметно медленнее Small",
        "547 МБ · лучшее качество, только для флагманов"
    };

    public static final long[] MODEL_BYTES = {
        32_600_000L, 60_000_000L, 190_000_000L, 539_212_467L, 574_041_195L
    };

    private static final String[] MODEL_FILES = {
        "ggml-tiny-q5_1.bin",
        "ggml-base-q5_1.bin",
        "ggml-small-q5_1.bin",
        "ggml-medium-q5_0.bin",
        "ggml-large-v3-turbo-q5_0.bin"
    };

    private static final String MODEL_BASE_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";

    // ---- what the device can actually carry ----

    /**
     * Models that are not a reasonable default for anybody.
     *
     * <p>Small already transcribes remarkably well; these two exist for people who want the last
     * few percent and have a phone that can pay for it. Offering them without saying so would be
     * handing somebody a half-gigabyte download and a transcription that never finishes.
     */
    public static boolean isHeavy(int model) {
        return model >= MODEL_MEDIUM;
    }

    /**
     * Roughly what the model occupies while it runs: the weights, plus the key-value cache and
     * the compute buffers ggml allocates around them.
     */
    public static long requiredMemory(int model) {
        if (model < 0 || model >= MODEL_BYTES.length) {
            return 0;
        }
        return MODEL_BYTES[model] * 3 / 2 + 250L * 1024 * 1024;
    }

    /** Physical RAM, or 0 when the system will not say. */
    public static long deviceMemory() {
        try {
            final android.app.ActivityManager manager = (android.app.ActivityManager)
                ApplicationLoader.applicationContext.getSystemService(android.content.Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return 0;
            }
            final android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            return info.totalMem;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Whether this phone is likely to run the model at all.
     *
     * <p>Four times what the model needs, not once: Android hands an app a fraction of the total,
     * the rest of the messenger is in that fraction too, and a native allocation that cannot be
     * met does not fail politely - it takes the process with it.
     */
    public static boolean deviceCanHandle(int model) {
        if (!isHeavy(model)) {
            return true;
        }
        final long memory = deviceMemory();
        if (memory > 0 && memory < requiredMemory(model) * 4) {
            return false;
        }
        return SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE;
    }

    /** What the user should be told before choosing this model, or null when there is nothing. */
    public static String capabilityWarning(int model) {
        if (!isHeavy(model)) {
            return null;
        }
        final StringBuilder text = new StringBuilder();
        if (!deviceCanHandle(model)) {
            text.append("Судя по характеристикам, этот телефон её не потянет. ");
        }
        text.append("Модель занимает около ")
            .append(AndroidUtilities.formatFileSize(requiredMemory(model)))
            .append(" оперативной памяти во время работы и расшифровывает в несколько раз дольше, чем Small.");
        final long memory = deviceMemory();
        if (memory > 0) {
            text.append(" На этом устройстве всего ")
                .append(AndroidUtilities.formatFileSize(memory))
                .append(".");
        }
        text.append("\n\nSmall при этом даёт качество, которое почти невозможно отличить на слух — "
            + "выбирайте это, только если разница действительно нужна.");
        return text.toString();
    }

    /** whisper wants exactly this: mono, 16 kHz, float samples in [-1, 1]. */
    private static final int TARGET_RATE = 16000;

    public interface Callback {
        void onResult(String text);
        void onError(String message);

        /**
         * The transcript as far as it has been decoded, on the main thread.
         *
         * <p>Called several times before {@link #onResult}, each time with the whole text rather
         * than the newest piece. A minute of audio takes a while on a phone, and watching the
         * sentence appear is the difference between "it is working" and "it has hung".
         */
        void onPartial(String text);
    }

    /** What the native side calls as each segment comes out of the decoder. */
    public interface SegmentListener {
        void onSegment(String textSoFar);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onFinished(boolean ok, String message);
    }

    // ---- settings ----

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
            .getSharedPreferences("mainconfig", android.content.Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        try {
            return prefs().getBoolean(KEY_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        try {
            prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
        } catch (Throwable ignore) {
        }
    }

    public static int getModel() {
        try {
            final int value = prefs().getInt(KEY_MODEL, MODEL_BASE);
            return value >= 0 && value < MODEL_FILES.length ? value : MODEL_BASE;
        } catch (Throwable t) {
            return MODEL_BASE;
        }
    }

    public static void setModel(int model) {
        try {
            prefs().edit().putInt(KEY_MODEL, model).apply();
        } catch (Throwable ignore) {
        }
    }

    /** Empty means auto-detect, which is what whisper does with "auto". */
    public static String getLanguage() {
        try {
            return prefs().getString(KEY_LANGUAGE, "");
        } catch (Throwable t) {
            return "";
        }
    }

    public static void setLanguage(String language) {
        try {
            prefs().edit().putString(KEY_LANGUAGE, language == null ? "" : language).apply();
        } catch (Throwable ignore) {
        }
    }

    // ---- model files ----

    public static File getModelFile(int model) {
        final int index = model >= 0 && model < MODEL_FILES.length ? model : MODEL_BASE;
        final File dir = new File(ApplicationLoader.getFilesDirFixed(), "whisper");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, MODEL_FILES[index]);
    }

    /**
     * Remembered per model, because this is a filesystem stat and it is asked from a view
     * constructor: every transcribe button built while scrolling a chat full of voice messages
     * hit the disk twice. The answer changes only when a model is downloaded or deleted, and both
     * of those clear it.
     */
    private static final java.util.HashMap<Integer, Boolean> downloadedCache = new java.util.HashMap<>();

    public static boolean isModelDownloaded(int model) {
        final Boolean cached = downloadedCache.get(model);
        if (cached != null) {
            return cached;
        }
        final File file = getModelFile(model);
        // A partial download left by a killed process would be a file that exists and loads as
        // garbage, so a plausible size is part of "downloaded", not a separate check.
        final boolean downloaded = file.exists() && file.length() > MODEL_BYTES[model] / 2;
        downloadedCache.put(model, downloaded);
        return downloaded;
    }

    /** Called whenever a model file appears or disappears. */
    public static void invalidateModelCache() {
        downloadedCache.clear();
    }

    public static boolean deleteModel(int model) {
        synchronized (PrimeWhisper.class) {
            if (loadedModel == model) {
                releaseLocked();
            }
        }
        final boolean deleted = getModelFile(model).delete();
        invalidateModelCache();
        return deleted;
    }

    public static final String KEY_PREFER_OVER_PREMIUM = "primegram_whisper_prefer";

    /**
     * Whether to use the on-device model even on an account that has real Premium.
     *
     * <p>Off by default, because Telegram's own transcription is instant and costs the user
     * nothing they have not already paid for. On, because it turns out the local model reads some
     * voices markedly better - and someone who has noticed that should not have to give up their
     * subscription to act on it.
     */
    public static boolean preferOverPremium() {
        try {
            return prefs().getBoolean(KEY_PREFER_OVER_PREMIUM, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setPreferOverPremium(boolean prefer) {
        try {
            prefs().edit().putBoolean(KEY_PREFER_OVER_PREMIUM, prefer).apply();
        } catch (Throwable ignore) {
        }
    }

    /** True when this account should use the on-device path instead of Telegram's. */
    public static boolean shouldHandle(int account) {
        try {
            if (!isEnabled() || !isModelDownloaded(getModel())) {
                return false;
            }
            return !UserConfig.getInstance(account).hasRealPremium() || preferOverPremium();
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- download ----

    public static void download(int model, DownloadCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            final File target = getModelFile(model);
            // Downloaded beside the real name, moved into place only when complete: an interrupted
            // download must never look like a usable model.
            final File temp = new File(target.getAbsolutePath() + ".part");
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(MODEL_BASE_URL + MODEL_FILES[model]).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                final int code = connection.getResponseCode();
                if (code != 200) {
                    throw new Exception("HTTP " + code);
                }
                final long total = connection.getContentLength() > 0
                    ? connection.getContentLength()
                    : MODEL_BYTES[model];
                long done = 0;
                int lastPercent = -1;
                try (InputStream in = connection.getInputStream();
                     FileOutputStream out = new FileOutputStream(temp)) {
                    final byte[] buffer = new byte[65536];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        done += read;
                        final int percent = (int) Math.min(100, done * 100 / Math.max(1, total));
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            final int reported = percent;
                            AndroidUtilities.runOnUIThread(() -> callback.onProgress(reported));
                        }
                    }
                }
                target.delete();
                if (!temp.renameTo(target)) {
                    throw new Exception("не удалось сохранить модель");
                }
                invalidateModelCache();
                AndroidUtilities.runOnUIThread(() -> callback.onFinished(true, null));
            } catch (Throwable t) {
                FileLog.e("PrimeWhisper.download", t);
                temp.delete();
                final String message = t.getMessage() == null ? "Ошибка загрузки" : t.getMessage();
                AndroidUtilities.runOnUIThread(() -> callback.onFinished(false, message));
            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Throwable ignore) {
                    }
                }
            }
        });
    }

    // ---- native ----

    private static boolean libraryLoaded;
    private static boolean libraryFailed;
    private static long context;
    private static int loadedModel = -1;

    private static native long nativeInit(String modelPath);
    private static native void nativeFree(long ptr);
    private static native String nativeTranscribe(long ptr, float[] pcm, String language, int threads,
                                                  SegmentListener listener);

    private static synchronized boolean ensureLibrary() {
        if (libraryLoaded) {
            return true;
        }
        if (libraryFailed) {
            return false;
        }
        try {
            System.loadLibrary("primewhisper");
            libraryLoaded = true;
        } catch (Throwable t) {
            FileLog.e("PrimeWhisper.loadLibrary", t);
            // Remembered so a device without the library does not retry the load on every
            // voice message it sees.
            libraryFailed = true;
        }
        return libraryLoaded;
    }

    private static void releaseLocked() {
        if (context != 0) {
            try {
                nativeFree(context);
            } catch (Throwable ignore) {
            }
            context = 0;
        }
        loadedModel = -1;
    }

    /** Drops the loaded model. Call when the feature is switched off - it holds real memory. */
    public static synchronized void release() {
        releaseLocked();
    }

    // ---- transcription ----

    public static void transcribe(MessageObject messageObject, Callback callback) {
        final File file = PrimeTranscription.resolveFile(messageObject);
        if (file == null) {
            AndroidUtilities.runOnUIThread(() -> callback.onError("Сначала загрузите голосовое"));
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                // Called from inside the decoder, so it does nothing but hand the text to the main
                // thread: whatever the interface does with it must not be happening while whisper
                // is holding its context.
                final Typewriter typewriter = new Typewriter(callback);
                final SegmentListener listener = textSoFar -> {
                    if (!TextUtils.isEmpty(textSoFar)) {
                        AndroidUtilities.runOnUIThread(() -> typewriter.offer(textSoFar.trim()));
                    }
                };
                final String text = run(file, listener);
                if (TextUtils.isEmpty(text)) {
                    AndroidUtilities.runOnUIThread(() -> {
                        typewriter.stop();
                        callback.onError("Не удалось разобрать речь");
                    });
                } else {
                    AndroidUtilities.runOnUIThread(() -> typewriter.finish(text));
                }
            } catch (Throwable t) {
                FileLog.e("PrimeWhisper.transcribe", t);
                final String message = t.getMessage() == null ? "Ошибка расшифровки" : t.getMessage();
                AndroidUtilities.runOnUIThread(() -> callback.onError(message));
            }
        });
    }

    /**
     * Reveals the transcript a few characters at a time instead of in whole slabs.
     *
     * <p>whisper decodes in thirty-second windows and cannot be persuaded otherwise: the encoder
     * always works on a fixed-length spectrogram, so a two-minute recording produces four bursts
     * of text and a short one produces exactly one, at the end. Making that arrive as typing does
     * not make it faster, but it turns four jumps into something that reads as work in progress.
     *
     * <p>Everything here runs on the main thread, one post at a time - no thread, no lock.
     */
    private static final class Typewriter {

        /** Slow enough to read as typing, fast enough not to become the thing you wait for. */
        private static final long TICK_MS = 33;
        private static final int MIN_PER_TICK = 2;
        /** However far behind it falls, it catches up within about this long. */
        private static final long MAX_LAG_MS = 1200;

        private final Callback callback;
        private String pending = "";
        private int shown;
        private boolean finishing;
        private boolean stopped;
        private Runnable tick;

        Typewriter(Callback callback) {
            this.callback = callback;
        }

        /** More text has been decoded; it always starts with what is already on screen. */
        void offer(String text) {
            if (stopped || text == null || text.length() <= pending.length()) {
                return;
            }
            pending = text;
            schedule();
        }

        /** The last of it. Types out the remainder and only then calls the transcript final. */
        void finish(String text) {
            if (stopped) {
                return;
            }
            if (text != null && text.length() >= pending.length()) {
                pending = text;
            }
            finishing = true;
            schedule();
        }

        void stop() {
            stopped = true;
            if (tick != null) {
                AndroidUtilities.cancelRunOnUIThread(tick);
                tick = null;
            }
        }

        private void schedule() {
            if (tick != null || stopped) {
                return;
            }
            tick = this::step;
            AndroidUtilities.runOnUIThread(tick, TICK_MS);
        }

        private void step() {
            tick = null;
            if (stopped) {
                return;
            }
            final int remaining = pending.length() - shown;
            if (remaining <= 0) {
                if (finishing) {
                    stopped = true;
                    callback.onResult(pending);
                }
                return;
            }
            // The rate follows how much is waiting, so a long tail does not take a minute to
            // appear while a short one still looks like typing.
            final int perTick = Math.max(MIN_PER_TICK,
                    (int) Math.ceil(remaining / (double) (MAX_LAG_MS / TICK_MS)));
            shown = Math.min(pending.length(), shown + perTick);
            callback.onPartial(pending.substring(0, shown));
            schedule();
        }
    }

    private static String run(File file, SegmentListener listener) throws Exception {
        if (!ensureLibrary()) {
            throw new Exception("Модуль распознавания недоступен");
        }
        final int model = getModel();
        final File modelFile = getModelFile(model);
        if (!isModelDownloaded(model)) {
            throw new Exception("Модель не загружена");
        }
        final float[] pcm = decodeToPcm(file);
        if (pcm == null || pcm.length == 0) {
            throw new Exception("Не удалось прочитать запись");
        }
        synchronized (PrimeWhisper.class) {
            if (context == 0 || loadedModel != model) {
                releaseLocked();
                context = nativeInit(modelFile.getAbsolutePath());
                if (context == 0) {
                    throw new Exception("Модель повреждена, загрузите заново");
                }
                loadedModel = model;
            }
            // Held for the whole run on purpose: one whisper context is not safe to use from two
            // threads, and two contexts would mean two copies of the model in memory.
            final int threads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() - 1));
            final String language = getLanguage();
            return nativeTranscribe(context, pcm, TextUtils.isEmpty(language) ? "auto" : language,
                    threads, listener);
        }
    }

    /**
     * Decodes a voice message into the mono 16 kHz float samples whisper expects.
     *
     * <p>Uses the platform decoder rather than the Opus decoder bundled with the app: this runs
     * off any container Telegram might hand us, including the mp4-wrapped recordings that come
     * from other clients, and costs no extra native surface.
     */
    private static float[] decodeToPcm(File file) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(file.getAbsolutePath());
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                final MediaFormat candidate = extractor.getTrackFormat(i);
                final String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = candidate;
                    break;
                }
            }
            if (track < 0) {
                throw new Exception("В файле нет звука");
            }
            extractor.selectTrack(track);
            final int sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            final int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            final java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
            final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                if (!inputDone) {
                    final int index = codec.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        final ByteBuffer buffer = codec.getInputBuffer(index);
                        final int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                final int index = codec.dequeueOutputBuffer(info, 10000);
                if (index >= 0) {
                    if (info.size > 0) {
                        final ByteBuffer buffer = codec.getOutputBuffer(index);
                        if (buffer != null) {
                            final byte[] chunk = new byte[info.size];
                            buffer.position(info.offset);
                            buffer.get(chunk);
                            raw.write(chunk);
                        }
                    }
                    codec.releaseOutputBuffer(index, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            return toMono16k(raw.toByteArray(), sourceRate, channels);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Throwable ignore) {
                }
                try {
                    codec.release();
                } catch (Throwable ignore) {
                }
            }
            try {
                extractor.release();
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     * 16-bit PCM at any rate to mono float at 16 kHz.
     *
     * <p>The resampler is linear interpolation, not a windowed filter. That is deliberate: speech
     * recognition is not listening for the aliasing a cheap resampler introduces above 8 kHz, and
     * a proper filter would cost far more code than the accuracy it buys here.
     */
    private static float[] toMono16k(byte[] raw, int sourceRate, int channels) {
        if (raw.length < 2) {
            return new float[0];
        }
        final ShortBuffer shorts = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        final int frames = shorts.remaining() / Math.max(1, channels);
        final float[] mono = new float[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += shorts.get(i * channels + c);
            }
            mono[i] = sum / (float) channels / 32768f;
        }
        if (sourceRate == TARGET_RATE) {
            return mono;
        }
        final int outLength = (int) ((long) frames * TARGET_RATE / sourceRate);
        if (outLength <= 0) {
            return new float[0];
        }
        final float[] out = new float[outLength];
        final double step = (double) frames / outLength;
        for (int i = 0; i < outLength; i++) {
            final double position = i * step;
            final int left = (int) position;
            final int right = Math.min(frames - 1, left + 1);
            final float fraction = (float) (position - left);
            out[i] = mono[left] * (1 - fraction) + mono[right] * fraction;
        }
        return out;
    }
}
