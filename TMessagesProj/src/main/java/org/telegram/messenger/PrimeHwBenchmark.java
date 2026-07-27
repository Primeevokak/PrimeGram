package org.telegram.messenger;

import android.graphics.Bitmap;
import android.os.Process;

import org.telegram.ui.Components.AnimatedFileNative;

import java.io.File;

/**
 * PrimeGram: measures what the hardware video decoding setting is actually worth.
 *
 * <p>The setting routes H.264/HEVC decoding to the SoC's video block through MediaCodec instead
 * of ffmpeg's software decoder. That is expected to cost less CPU, but "expected" is not a number,
 * and vendor decoders differ enough that a figure from someone else's phone says little about
 * yours. So this decodes the same file twice in the same process, once down each path, and
 * reports what it saw.
 *
 * <p>What is measured, and what that means:
 *
 * <ul>
 *   <li><b>Wall time</b> - how long the frames took. Hardware is not always faster here; the win
 *       is usually in CPU, not latency.
 *   <li><b>CPU time</b> - process CPU consumed during the run. This is the number that maps to
 *       battery. Caveat worth stating plainly: some vendor codecs run their work in a separate
 *       process, and that time cannot be seen from here. Where that happens the hardware path
 *       will look better than it is.
 * </ul>
 *
 * <p>The result is one measurement on one file on one device under whatever else the phone was
 * doing at the time. Run it more than once before believing it.
 */
public class PrimeHwBenchmark {

    /** Frames decoded per pass. Enough to swamp per-run noise without making the user wait. */
    private static final int FRAMES = 90;
    /** Discarded before timing starts, so codec setup and the first keyframe are not counted. */
    private static final int WARMUP_FRAMES = 10;
    private static final long MIN_FILE_SIZE = 200 * 1024;

    public static class PassResult {
        public boolean ok;
        public String failure;
        public int frames;
        public long wallMs;
        public long cpuMs;

        public float msPerFrame() {
            return frames == 0 ? 0 : wallMs / (float) frames;
        }
    }

    public static class Result {
        public boolean ok;
        public String failure;
        public String fileName;
        public int width;
        public int height;
        public PassResult software = new PassResult();
        public PassResult hardware = new PassResult();
        /** Gap between the two rounds of the same path - how much this device's numbers wander. */
        public long softwareSpreadMs;
        public long hardwareSpreadMs;
    }

    /** Averages the two rounds of one path. Fails if either round failed. */
    private static PassResult mean(PassResult a, PassResult b) {
        PassResult out = new PassResult();
        out.ok = a.ok && b.ok;
        out.failure = a.ok ? b.failure : a.failure;
        if (out.ok) {
            out.frames = (a.frames + b.frames) / 2;
            out.wallMs = (a.wallMs + b.wallMs) / 2;
            out.cpuMs = (a.cpuMs + b.cpuMs) / 2;
        }
        return out;
    }

    /**
     * Reads the file through once so neither pass is the one that pays for cold storage.
     * Failures are ignored - a cold cache makes the numbers noisier, not wrong.
     */
    private static void warmFileCache(File file) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[256 * 1024];
            while (in.read(buffer) > 0) {
                // Reading is the point; the bytes are not needed.
            }
        } catch (Throwable ignore) {
        }
    }

    public interface Callback {
        void onFinished(Result result);
    }

    /** Runs the benchmark off the main thread and delivers the result back on it. */
    public static void run(Callback callback) {
        new Thread(() -> {
            Result result = measure();
            AndroidUtilities.runOnUIThread(() -> callback.onFinished(result));
        }, "PrimeHwBenchmark").start();
    }

    private static Result measure() {
        Result result = new Result();
        File sample = findSampleVideo();
        if (sample == null) {
            result.failure = "Не нашёл видео в кэше. Откройте любое видео или гифку в Telegram и попробуйте снова — тест работает на реальном файле, а не на синтетическом.";
            return result;
        }
        result.fileName = sample.getName();

        int[] probe = new int[org.telegram.ui.Components.AnimatedFileDrawable.PARAM_NUM_COUNT];
        try {
            AnimatedFileNative.getVideoInfo(sample.getAbsolutePath(), probe, 0);
        } catch (Throwable t) {
            result.failure = "Не удалось прочитать файл: " + t.getMessage();
            return result;
        }
        result.width = probe[org.telegram.ui.Components.AnimatedFileDrawable.PARAM_NUM_WIDTH];
        result.height = probe[org.telegram.ui.Components.AnimatedFileDrawable.PARAM_NUM_HEIGHT];
        if (result.width <= 0 || result.height <= 0) {
            result.failure = "Файл не содержит видеодорожки, пригодной для теста.";
            return result;
        }

        // Whichever pass runs second reads a file the first pass already pulled into the page
        // cache. Rather than pick which path that favours, the confound is removed: the file is
        // read once up front so both passes start warm, and then each path is run twice with the
        // order swapped so any residual ordering effect cancels between the two rounds.
        warmFileCache(sample);

        PassResult softwareFirst = runPass(sample, result.width, result.height, false);
        PassResult hardwareFirst = runPass(sample, result.width, result.height, true);
        PassResult hardwareSecond = runPass(sample, result.width, result.height, true);
        PassResult softwareSecond = runPass(sample, result.width, result.height, false);

        result.software = mean(softwareFirst, softwareSecond);
        result.hardware = mean(hardwareFirst, hardwareSecond);
        result.softwareSpreadMs = Math.abs(softwareFirst.wallMs - softwareSecond.wallMs);
        result.hardwareSpreadMs = Math.abs(hardwareFirst.wallMs - hardwareSecond.wallMs);
        result.ok = result.software.ok && result.hardware.ok;
        if (!result.ok) {
            result.failure = result.software.ok ? result.hardware.failure : result.software.failure;
        }
        return result;
    }

    private static PassResult runPass(File file, int width, int height, boolean hwAccel) {
        PassResult pass = new PassResult();
        AnimatedFileNative decoder = null;
        Bitmap bitmap = null;
        try {
            int[] params = new int[7];
            decoder = AnimatedFileNative.createDecoderForBenchmark(file.getAbsolutePath(), params, hwAccel);
            if (decoder == null) {
                pass.failure = (hwAccel ? "Аппаратный" : "Программный") + " декодер не создался.";
                return pass;
            }
            // Decoding at native resolution keeps the two passes comparable; scaling would put
            // work in a place that has nothing to do with the decoder being measured.
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            for (int i = 0; i < WARMUP_FRAMES; i++) {
                decoder.getVideoFrame(bitmap, false, 0, 0, true);
            }

            long cpuStart = Process.getElapsedCpuTime();
            long wallStart = System.nanoTime();
            int decoded = 0;
            for (int i = 0; i < FRAMES; i++) {
                if (decoder.getVideoFrame(bitmap, false, 0, 0, true) != 0) {
                    decoded++;
                }
            }
            pass.wallMs = (System.nanoTime() - wallStart) / 1_000_000L;
            pass.cpuMs = Process.getElapsedCpuTime() - cpuStart;
            pass.frames = decoded;
            pass.ok = decoded > 0;
            if (!pass.ok) {
                pass.failure = "Ни один кадр не декодировался.";
            }
        } catch (Throwable t) {
            pass.failure = String.valueOf(t.getMessage());
        } finally {
            if (decoder != null) {
                try {
                    decoder.recycle();
                } catch (Throwable ignore) {
                }
            }
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        return pass;
    }

    /** The largest recently cached video, on the theory that a bigger file measures more decoding. */
    private static File findSampleVideo() {
        File best = null;
        try {
            File dir = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO);
            best = largestVideoIn(dir, best);
            best = largestVideoIn(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), best);
        } catch (Throwable ignore) {
        }
        return best;
    }

    private static File largestVideoIn(File dir, File best) {
        if (dir == null || !dir.isDirectory()) {
            return best;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return best;
        }
        for (File f : files) {
            if (!f.isFile() || f.length() < MIN_FILE_SIZE) {
                continue;
            }
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".mp4") && !name.endsWith(".mov") && !name.endsWith(".mkv")) {
                continue;
            }
            if (best == null || f.length() > best.length()) {
                best = f;
            }
        }
        return best;
    }

    /** Human-readable report, formatted for reading rather than for looking impressive. */
    public static String format(Result r) {
        if (!r.ok) {
            return r.failure == null ? "Тест не удался." : r.failure;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Файл: ").append(r.fileName).append("\n");
        sb.append("Разрешение: ").append(r.width).append("×").append(r.height).append("\n");
        sb.append("Кадров в проходе: ").append(r.software.frames).append("\n");
        sb.append("Каждый путь прогнан дважды, порядок менялся; ниже — среднее.\n\n");

        sb.append("Программный декодер (ffmpeg)\n");
        sb.append("  время: ").append(r.software.wallMs).append(" мс");
        sb.append("  (").append(String.format(java.util.Locale.US, "%.1f", r.software.msPerFrame())).append(" мс/кадр)\n");
        sb.append("  CPU: ").append(r.software.cpuMs).append(" мс\n\n");

        sb.append("Аппаратный декодер (MediaCodec)\n");
        sb.append("  время: ").append(r.hardware.wallMs).append(" мс");
        sb.append("  (").append(String.format(java.util.Locale.US, "%.1f", r.hardware.msPerFrame())).append(" мс/кадр)\n");
        sb.append("  CPU: ").append(r.hardware.cpuMs).append(" мс\n\n");

        sb.append(compare("Время", r.software.wallMs, r.hardware.wallMs));
        sb.append("\n");
        sb.append(compare("CPU", r.software.cpuMs, r.hardware.cpuMs));
        sb.append("\n\nРазброс между прогонами: программный ").append(r.softwareSpreadMs);
        sb.append(" мс, аппаратный ").append(r.hardwareSpreadMs).append(" мс. ");
        sb.append("Если разброс сопоставим с разницей между путями — разница шумовая, и телефон в этот момент был занят чем-то ещё.\n\n");
        sb.append("CPU — это та цифра, которая превращается в расход батареи. ");
        sb.append("Учтите: у части устройств кодек работает в отдельном процессе, и его время отсюда не видно — там аппаратный путь выглядит лучше, чем есть.");
        return sb.toString();
    }

    private static String compare(String label, long softwareValue, long hardwareValue) {
        if (softwareValue <= 0 || hardwareValue <= 0) {
            return label + ": сравнить не с чем.";
        }
        if (hardwareValue < softwareValue) {
            long percent = Math.round(100.0 * (softwareValue - hardwareValue) / softwareValue);
            return label + ": аппаратный путь меньше на " + percent + "%.";
        }
        long percent = Math.round(100.0 * (hardwareValue - softwareValue) / softwareValue);
        return label + ": аппаратный путь больше на " + percent + "% — выигрыша нет.";
    }
}
