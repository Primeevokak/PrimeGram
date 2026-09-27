package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Trace;

import org.telegram.messenger.AnimatedFileDrawableStream;
import org.telegram.messenger.BuildConfig;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnimatedFileNative {

    private final int[] mMetaData;
    private long mNativePtr;
    private final AtomicBoolean mRecycled = new AtomicBoolean(false);

    private AnimatedFileNative(long nativePtr, int[] metaData) {
        mNativePtr = nativePtr;
        mMetaData = metaData;
    }

    public static AnimatedFileNative createDecoderFrom(String src, int[] params, int account, long streamFileSize, AnimatedFileDrawableStream readCallback, boolean preview) {
        return createDecoderFrom(src, params, account, streamFileSize, readCallback, preview, hardwareEnabled());
    }

    private static AnimatedFileNative createDecoderFrom(String src, int[] params, int account, long streamFileSize, AnimatedFileDrawableStream readCallback, boolean preview, boolean hwAccel) {
        long ptr = createDecoder(src, params, account, streamFileSize, readCallback, preview, hwAccel);
        if (ptr == 0) {
            return null;
        }
        return new AnimatedFileNative(ptr, params);
    }

    // PrimeGram: hardware decoding (MediaCodec into an ImageReader, native side in
    // jni/gifvideo/mediacodec_frame_reader.h). The native side decides per file whether it applies
    // (H.264/HEVC, large enough, a free decoder instance) and falls back to software on any failure.
    private static final String HW_KEY = "primegram_hw_accel";
    private static volatile Boolean hwEnabled;

    private static boolean hardwareEnabled() {
        Boolean enabled = hwEnabled;
        if (enabled == null) {
            synchronized (AnimatedFileNative.class) {
                enabled = hwEnabled;
                if (enabled == null) {
                    // A crash of the process soon after the first hardware decoder turns the
                    // setting off on the next launch (CrashSafeToggle), instead of crashing again.
                    enabled = org.telegram.messenger.CrashSafeToggle.checkAndArm(HW_KEY);
                    if (enabled) {
                        org.telegram.messenger.CrashSafeToggle.beginAttempt(HW_KEY);
                        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> org.telegram.messenger.CrashSafeToggle.confirmSuccess(HW_KEY), 30000);
                    }
                    hwEnabled = enabled;
                }
            }
        }
        return enabled;
    }

    /** PrimeGram: benchmark entry point - the same decoder with hardware decoding forced on or off. */
    public static AnimatedFileNative createDecoderForBenchmark(String src, int[] params, boolean hwAccel) {
        return createDecoderFrom(src, params, 0, 0, null, false, hwAccel);
    }

    public static boolean isHardwareDecodingAvailable() {
        return Build.VERSION.SDK_INT >= 26;
    }

    /** Whether this decoder is currently decoding in hardware (it may have fallen back since). */
    public boolean isHardware() {
        return mNativePtr != 0 && nIsHardware(mNativePtr);
    }

    public int getWidth() {
        return mMetaData[0];
    }

    public int getHeight() {
        return mMetaData[1];
    }

    public int getRotation() {
        return mMetaData[2];
    }

    public int getProgress(TimeUnit timeUnit) {
        return (int) timeUnit.convert(mMetaData[3], TimeUnit.MILLISECONDS);
    }

    public int getDuration(TimeUnit timeUnit) {
        return (int) timeUnit.convert(mMetaData[4], TimeUnit.MILLISECONDS);
    }

    public int getFps() {
        return mMetaData[5];
    }

    public boolean isLastFrameOpaque() {
        return mMetaData[6] == 1;
    }

    public boolean isStaticVideoDetected() {
        return mMetaData[7] == 1;
    }

    public void stopDecoder() {
        checkNotDestroyed();
        stopDecoder(mNativePtr);
    }

    public int getVideoFrame(Bitmap bitmap, boolean preview, float startTimeSeconds, float endTimeSeconds, boolean loop) {
        checkNotDestroyed();
        return getVideoFrame(mNativePtr, bitmap, mMetaData, preview, startTimeSeconds, endTimeSeconds, loop);
    }

    public void seekToMs(long ms, boolean precise) {
        checkNotDestroyed();
        seekToMs(mNativePtr, ms, mMetaData, precise);
    }

    public int getFrameAtTime(long ms, Bitmap bitmap) {
        checkNotDestroyed();
        return getFrameAtTime(mNativePtr, ms, bitmap, mMetaData);
    }

    public void prepareToSeek() {
        checkNotDestroyed();
        prepareToSeek(mNativePtr);
    }

    public boolean isDestroyed() {
        return mRecycled.get();
    }

    public void recycle() {
        if (mRecycled.compareAndSet(false, true)) {
            long ptr = mNativePtr;
            mNativePtr = 0;
            if (ptr != 0) {
                destroyDecoder(ptr);
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (!mRecycled.get()) {
                recycle();
            }
        } finally {
            super.finalize();
        }
    }

    private void checkNotDestroyed() {
        if (mRecycled.get()) {
            if (BuildConfig.DEBUG_PRIVATE_VERSION) {
                throw new IllegalStateException("Called method on a destroyed AnimatedFileNative instance");
            }
        }
    }

    private static long createDecoder(String src, int[] params, int account, long streamFileSize, AnimatedFileDrawableStream readCallback, boolean preview, boolean hwAccel) {
        Trace.beginSection("AnimatedFileNative#createDecoder");
        try {
            return nCreateDecoder(src, params, account, streamFileSize, readCallback, preview, hwAccel);
        } finally {
            Trace.endSection();
        }
    }

    private static void destroyDecoder(long ptr) {
        Trace.beginSection("AnimatedFileNative#destroyDecoder");
        try {
            nDestroyDecoder(ptr);
        } finally {
            Trace.endSection();
        }
    }

    private static void stopDecoder(long ptr) {
        Trace.beginSection("AnimatedFileNative#stopDecoder");
        try {
            nStopDecoder(ptr);
        } finally {
            Trace.endSection();
        }
    }

    private static int getVideoFrame(long ptr, Bitmap bitmap, int[] params, boolean preview, float startTimeSeconds, float endTimeSeconds, boolean loop) {
        Trace.beginSection("AnimatedFileNative#getVideoFrame");
        try {
            return nGetVideoFrame(ptr, bitmap, params, preview, startTimeSeconds, endTimeSeconds, loop);
        } finally {
            Trace.endSection();
        }
    }

    private static void seekToMs(long ptr, long ms, int[] params, boolean precise) {
        Trace.beginSection("AnimatedFileNative#seekToMs");
        try {
            nSeekToMs(ptr, ms, params, precise);
        } finally {
            Trace.endSection();
        }
    }

    private static int getFrameAtTime(long ptr, long ms, Bitmap bitmap, int[] data) {
        Trace.beginSection("AnimatedFileNative#getFrameAtTime");
        try {
            return nGetFrameAtTime(ptr, ms, bitmap, data);
        } finally {
            Trace.endSection();
        }
    }

    private static void prepareToSeek(long ptr) {
        Trace.beginSection("AnimatedFileNative#prepareToSeek");
        try {
            nPrepareToSeek(ptr);
        } finally {
            Trace.endSection();
        }
    }

    public static void getVideoInfo(String src, int[] params, long fileOffset) {
        Trace.beginSection("AnimatedFileNative#getVideoInfo");
        try {
            nGetVideoInfo(src, params, fileOffset);
        } finally {
            Trace.endSection();
        }
    }



    private static native long nCreateDecoder(String src, int[] params, int account, long streamFileSize, Object readCallback, boolean preview, boolean hwAccel);

    private static native boolean nIsHardware(long ptr);

    private static native void nDestroyDecoder(long ptr);

    private static native void nStopDecoder(long ptr);

    private static native int nGetVideoFrame(long ptr, Bitmap bitmap, int[] params, boolean preview, float startTimeSeconds, float endTimeSeconds, boolean loop);

    private static native void nSeekToMs(long ptr, long ms, int[] params, boolean precise);

    private static native int nGetFrameAtTime(long ptr, long ms, Bitmap bitmap, int[] data);

    private static native void nPrepareToSeek(long ptr);

    private static native void nGetVideoInfo(String src, int[] params, long fileOffset);
}