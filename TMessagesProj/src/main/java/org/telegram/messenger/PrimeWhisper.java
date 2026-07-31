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

    public static final String[] MODEL_NAMES = {"Tiny", "Base", "Small"};

    /**
     * What each model costs and buys.
     *
     * <p>All three are the q5_1 quantisations rather than the full-precision files: on a phone the
     * accuracy difference is small and the size difference is roughly threefold, and a 466 MB
     * download for a feature people try once is not a reasonable ask.
     */
    public static final String[] MODEL_DESCRIPTIONS = {
        "31 МБ · быстро, разборчивую речь узнаёт",
        "57 МБ · заметно точнее, разумный выбор",
        "181 МБ · точнее всех, но медленно на слабых телефонах"
    };

    public static final long[] MODEL_BYTES = {32_600_000L, 60_000_000L, 190_000_000L};

    private static final String[] MODEL_FILES = {
        "ggml-tiny-q5_1.bin",
        "ggml-base-q5_1.bin",
        "ggml-small-q5_1.bin"
    };

    private static final String MODEL_BASE_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";

    /** whisper wants exactly this: mono, 16 kHz, float samples in [-1, 1]. */
    private static final int TARGET_RATE = 16000;

    public interface Callback {
        void onResult(String text);
        void onError(String message);
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

    /** True when this account should use the on-device path instead of Telegram's. */
    public static boolean shouldHandle(int account) {
        try {
            return !UserConfig.getInstance(account).hasRealPremium()
                && isEnabled()
                && isModelDownloaded(getModel());
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
    private static native String nativeTranscribe(long ptr, float[] pcm, String language, int threads);

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
                final String text = run(file);
                if (TextUtils.isEmpty(text)) {
                    AndroidUtilities.runOnUIThread(() -> callback.onError("Не удалось разобрать речь"));
                } else {
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(text));
                }
            } catch (Throwable t) {
                FileLog.e("PrimeWhisper.transcribe", t);
                final String message = t.getMessage() == null ? "Ошибка расшифровки" : t.getMessage();
                AndroidUtilities.runOnUIThread(() -> callback.onError(message));
            }
        });
    }

    private static String run(File file) throws Exception {
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
            return nativeTranscribe(context, pcm, TextUtils.isEmpty(language) ? "auto" : language, threads);
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
