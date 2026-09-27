#pragma once

// PrimeGram: hardware decoding for the 12.10 frame reader.
//
// MediaCodec decodes into the surface of an AImageReader; each frame is then read back as
// YUV_420_888 and copied into an I420 AVFrame, so everything downstream of the reader (seeking,
// trimming, the bitmap conversion) is the same code the software path uses.
//
// Why a surface and not MediaCodec's own output buffers: in byte-buffer mode vendors are free to
// hand back proprietary tiled layouts (MediaTek and Qualcomm both do), which cannot be read safely
// by plane arithmetic. An ImageReader always presents YUV_420_888 with explicit strides.
//
// The previous PrimeGram attempt relied on FFmpeg's h264_mp4toannexb filter, which the bundled
// FFmpeg is not built with - so it never actually ran. The bitstream conversion (length-prefixed
// NAL units to start codes, codec config from avcC/hvcC) is done here instead.
//
// FrameSource picks between this and the software VideoFrameReader and falls back to software
// whenever the hardware path fails, including after it has started.

#include <atomic>
#include <cstring>
#include <dlfcn.h>
#include <functional>
#include <unistd.h>
#include <vector>
#include <android/api-level.h>
#include <android/native_window.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaError.h>
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>
#include <libyuv.h>
#include <tgnet/FileLog.h>
#include "video_frame_reader.h"

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
}

namespace prime_hw {

// AImageReader is API 24+ while the native code builds for API 21, so it is resolved at runtime.
struct ImageReaderApi {
    media_status_t (*newReader)(int32_t, int32_t, int32_t, int32_t, AImageReader **) = nullptr;
    void (*deleteReader)(AImageReader *) = nullptr;
    media_status_t (*getWindow)(AImageReader *, ANativeWindow **) = nullptr;
    media_status_t (*acquireLatest)(AImageReader *, AImage **) = nullptr;
    void (*deleteImage)(AImage *) = nullptr;
    media_status_t (*planeData)(const AImage *, int, uint8_t **, int *) = nullptr;
    media_status_t (*rowStride)(const AImage *, int, int32_t *) = nullptr;
    media_status_t (*pixelStride)(const AImage *, int, int32_t *) = nullptr;
    media_status_t (*cropRect)(const AImage *, AImageCropRect *) = nullptr;
    bool ok = false;
};

inline const ImageReaderApi &imageReaderApi() {
    static const ImageReaderApi api = [] {
        ImageReaderApi a;
        // 26: the first release where AImageReader + decoder surfaces behave consistently.
        if (android_get_device_api_level() < 26) {
            return a;
        }
        void *lib = dlopen("libmediandk.so", RTLD_NOW);
        if (lib == nullptr) {
            return a;
        }
        a.newReader = (decltype(a.newReader)) dlsym(lib, "AImageReader_new");
        a.deleteReader = (decltype(a.deleteReader)) dlsym(lib, "AImageReader_delete");
        a.getWindow = (decltype(a.getWindow)) dlsym(lib, "AImageReader_getWindow");
        a.acquireLatest = (decltype(a.acquireLatest)) dlsym(lib, "AImageReader_acquireLatestImage");
        a.deleteImage = (decltype(a.deleteImage)) dlsym(lib, "AImage_delete");
        a.planeData = (decltype(a.planeData)) dlsym(lib, "AImage_getPlaneData");
        a.rowStride = (decltype(a.rowStride)) dlsym(lib, "AImage_getPlaneRowStride");
        a.pixelStride = (decltype(a.pixelStride)) dlsym(lib, "AImage_getPlanePixelStride");
        a.cropRect = (decltype(a.cropRect)) dlsym(lib, "AImage_getCropRect");
        a.ok = a.newReader && a.deleteReader && a.getWindow && a.acquireLatest && a.deleteImage &&
               a.planeData && a.rowStride && a.pixelStride && a.cropRect;
        return a;
    }();
    return api;
}

// Hardware decoders are a scarce, device-wide resource (often 8-16 instances, shared with the
// camera and other apps). A chat full of autoplaying GIFs must not take them all.
static const int MAX_ACTIVE = 3;
// Small clips are cheaper to decode in software than to round-trip through a surface.
static const int MIN_PIXELS = 480 * 360;
// Process-wide breaker: after a few failures on this device, stop trying for the session.
static const int ANOMALY_LIMIT = 3;

inline std::atomic<int> &activeCount() {
    static std::atomic<int> count{0};
    return count;
}

inline std::atomic<int> &anomalyCount() {
    static std::atomic<int> count{0};
    return count;
}

inline void reportAnomaly(const char *reason) {
    int count = ++anomalyCount();
    LOGE("hw decode anomaly (%s), count=%d", reason, count);
}

inline bool disabledForSession() {
    return anomalyCount().load() >= ANOMALY_LIMIT;
}

class MediaCodecFrameReader {
public:
    using Status = VideoFrameReader::Status;

    std::function<bool()> shouldAbort;

    MediaCodecFrameReader(AVFormatContext *fmt, int streamIndex) : m_fmt(fmt), m_streamIndex(streamIndex) {
        m_pkt = av_packet_alloc();
        m_frame = av_frame_alloc();
    }

    ~MediaCodecFrameReader() {
        if (m_codec != nullptr) {
            AMediaCodec_stop(m_codec);
            AMediaCodec_delete(m_codec);
            m_codec = nullptr;
        }
        if (m_reader != nullptr) {
            imageReaderApi().deleteReader(m_reader);
            m_reader = nullptr;
        }
        if (m_holdsSlot) {
            --activeCount();
        }
        av_frame_free(&m_frame);
        av_packet_free(&m_pkt);
    }

    MediaCodecFrameReader(const MediaCodecFrameReader &) = delete;
    MediaCodecFrameReader &operator=(const MediaCodecFrameReader &) = delete;

    bool init() {
        if (m_pkt == nullptr || m_frame == nullptr || disabledForSession()) {
            return false;
        }
        const ImageReaderApi &api = imageReaderApi();
        if (!api.ok) {
            return false;
        }
        AVCodecParameters *par = m_fmt->streams[m_streamIndex]->codecpar;
        const char *mime;
        if (par->codec_id == AV_CODEC_ID_H264) {
            mime = "video/avc";
        } else if (par->codec_id == AV_CODEC_ID_HEVC) {
            mime = "video/hevc";
        } else {
            return false;
        }
        m_width = par->width;
        m_height = par->height;
        if (m_width <= 0 || m_height <= 0 || m_width * m_height < MIN_PIXELS || m_width > 4096 || m_height > 4096) {
            return false;
        }
        if (!parseCodecConfig(par)) {
            return false;
        }
        if (++activeCount() > MAX_ACTIVE) {
            --activeCount();
            return false;
        }
        m_holdsSlot = true;

        if (api.newReader(m_width, m_height, AIMAGE_FORMAT_YUV_420_888, 4, &m_reader) != AMEDIA_OK || m_reader == nullptr) {
            m_reader = nullptr;
            reportAnomaly("image reader");
            return false;
        }
        ANativeWindow *window = nullptr;
        if (api.getWindow(m_reader, &window) != AMEDIA_OK || window == nullptr) {
            reportAnomaly("image reader window");
            return false;
        }
        m_codec = AMediaCodec_createDecoderByType(mime);
        if (m_codec == nullptr) {
            return false;
        }
        AMediaFormat *format = AMediaFormat_new();
        AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, m_width);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, m_height);
        if (!m_csd0.empty()) {
            AMediaFormat_setBuffer(format, "csd-0", m_csd0.data(), m_csd0.size());
        }
        if (!m_csd1.empty()) {
            AMediaFormat_setBuffer(format, "csd-1", m_csd1.data(), m_csd1.size());
        }
        media_status_t configured = AMediaCodec_configure(m_codec, format, window, nullptr, 0);
        AMediaFormat_delete(format);
        if (configured != AMEDIA_OK || AMediaCodec_start(m_codec) != AMEDIA_OK) {
            AMediaCodec_delete(m_codec);
            m_codec = nullptr;
            reportAnomaly("configure/start");
            return false;
        }
        return true;
    }

    Status getNextFrame() {
        if (m_codec == nullptr) {
            return Status::Error;
        }
        for (int idle = 0; idle < 150;) {
            if (shouldAbort && shouldAbort()) {
                return Status::Aborted;
            }
            if (m_outputEos) {
                return Status::Eof;
            }
            while (!m_inputEos) {
                ssize_t in = AMediaCodec_dequeueInputBuffer(m_codec, 0);
                if (in < 0) {
                    break;
                }
                if (!queueInput((size_t) in)) {
                    return Status::Error;
                }
            }
            AMediaCodecBufferInfo info;
            ssize_t out = AMediaCodec_dequeueOutputBuffer(m_codec, &info, 8000);
            if (out >= 0) {
                const bool eos = (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0;
                if (eos) {
                    m_outputEos = true;
                }
                if (!eos || info.size > 0) {
                    AMediaCodec_releaseOutputBuffer(m_codec, (size_t) out, true);
                    if (readImage(info.presentationTimeUs)) {
                        m_failures = 0;
                        return Status::Ok;
                    }
                    if (++m_failures >= 3) {
                        reportAnomaly("no image from decoder surface");
                        return Status::Error;
                    }
                    continue;
                }
                AMediaCodec_releaseOutputBuffer(m_codec, (size_t) out, false);
                return Status::Eof;
            } else if (out == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                idle++;
            } else if (out == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED || out == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                continue;
            } else {
                reportAnomaly("dequeue output");
                return Status::Error;
            }
        }
        reportAnomaly("decoder stalled");
        return Status::Error;
    }

    bool seek(int64_t pts, int flags = AVSEEK_FLAG_BACKWARD) {
        if (m_codec == nullptr) {
            return false;
        }
        if (av_seek_frame(m_fmt, m_streamIndex, pts, flags) < 0) {
            return false;
        }
        AMediaCodec_flush(m_codec);
        m_inputEos = false;
        m_outputEos = false;
        // Frames rendered before the flush must not be read as the first frames after it.
        AImage *stale = nullptr;
        while (imageReaderApi().acquireLatest(m_reader, &stale) == AMEDIA_OK && stale != nullptr) {
            imageReaderApi().deleteImage(stale);
            stale = nullptr;
        }
        return true;
    }

    AVFrame *frame() const { return m_frame; }

    double frameTimeSeconds() const {
        AVRational tb = m_fmt->streams[m_streamIndex]->time_base;
        return m_frame->best_effort_timestamp * av_q2d(tb);
    }

    bool producedFrames() const { return m_producedFrames; }

private:
    static int readBe(const uint8_t *p, int bytes) {
        int v = 0;
        for (int i = 0; i < bytes; i++) {
            v = (v << 8) | p[i];
        }
        return v;
    }

    static void appendNal(std::vector<uint8_t> &out, const uint8_t *nal, int len) {
        static const uint8_t startCode[4] = {0, 0, 0, 1};
        out.insert(out.end(), startCode, startCode + 4);
        out.insert(out.end(), nal, nal + len);
    }

    // Codec config to Annex B: csd-0/csd-1 for MediaCodec, and m_nalLengthSize for packets.
    bool parseCodecConfig(AVCodecParameters *par) {
        const uint8_t *ed = par->extradata;
        const int size = par->extradata_size;
        if (ed == nullptr || size < 4) {
            return false;
        }
        if ((ed[0] == 0 && ed[1] == 0 && ed[2] == 1) || (ed[0] == 0 && ed[1] == 0 && ed[2] == 0 && ed[3] == 1)) {
            m_nalLengthSize = 0;  // already start codes
            m_csd0.assign(ed, ed + size);
            return true;
        }
        if (ed[0] != 1) {
            return false;
        }
        if (par->codec_id == AV_CODEC_ID_H264) {
            if (size < 7) {
                return false;
            }
            m_nalLengthSize = (ed[4] & 3) + 1;
            int p = 5;
            const int numSps = ed[p++] & 0x1f;
            for (int i = 0; i < numSps; i++) {
                if (p + 2 > size) return false;
                const int len = readBe(ed + p, 2);
                p += 2;
                if (p + len > size) return false;
                appendNal(m_csd0, ed + p, len);
                p += len;
            }
            if (p >= size) return false;
            const int numPps = ed[p++];
            for (int i = 0; i < numPps; i++) {
                if (p + 2 > size) return false;
                const int len = readBe(ed + p, 2);
                p += 2;
                if (p + len > size) return false;
                appendNal(m_csd1, ed + p, len);
                p += len;
            }
        } else {
            if (size < 23) {
                return false;
            }
            m_nalLengthSize = (ed[21] & 3) + 1;
            const int numArrays = ed[22];
            int p = 23;
            for (int a = 0; a < numArrays; a++) {
                if (p + 3 > size) return false;
                p += 1;  // array type
                const int count = readBe(ed + p, 2);
                p += 2;
                for (int i = 0; i < count; i++) {
                    if (p + 2 > size) return false;
                    const int len = readBe(ed + p, 2);
                    p += 2;
                    if (p + len > size) return false;
                    appendNal(m_csd0, ed + p, len);
                    p += len;
                }
            }
        }
        return m_nalLengthSize != 3 && !m_csd0.empty();
    }

    // Writes one packet as Annex B, with the codec config in front of keyframes so a decoder that
    // was flushed or started mid-stream always has it. Returns bytes written, or -1 if it did not fit.
    long writeAnnexB(const AVPacket *pkt, uint8_t *dst, size_t cap) const {
        size_t pos = 0;
        if ((pkt->flags & AV_PKT_FLAG_KEY) != 0) {
            if (m_csd0.size() + m_csd1.size() > cap) return -1;
            memcpy(dst, m_csd0.data(), m_csd0.size());
            pos += m_csd0.size();
            if (!m_csd1.empty()) {
                memcpy(dst + pos, m_csd1.data(), m_csd1.size());
                pos += m_csd1.size();
            }
        }
        if (m_nalLengthSize == 0) {
            if (pos + pkt->size > cap) return -1;
            memcpy(dst + pos, pkt->data, pkt->size);
            return (long) (pos + pkt->size);
        }
        int off = 0;
        while (off + m_nalLengthSize <= pkt->size) {
            const int len = readBe(pkt->data + off, m_nalLengthSize);
            off += m_nalLengthSize;
            if (len < 0 || len > pkt->size - off) {
                return -1;
            }
            if (pos + 4 + len > cap) return -1;
            dst[pos] = 0; dst[pos + 1] = 0; dst[pos + 2] = 0; dst[pos + 3] = 1;
            memcpy(dst + pos + 4, pkt->data + off, len);
            pos += 4 + len;
            off += len;
        }
        return (long) pos;
    }

    bool queueInput(size_t index) {
        for (;;) {
            int ret = av_read_frame(m_fmt, m_pkt);
            if (ret < 0) {
                AMediaCodec_queueInputBuffer(m_codec, index, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                m_inputEos = true;
                return true;
            }
            if (m_pkt->stream_index != m_streamIndex) {
                av_packet_unref(m_pkt);
                continue;
            }
            size_t cap = 0;
            uint8_t *buf = AMediaCodec_getInputBuffer(m_codec, index, &cap);
            long written = buf != nullptr ? writeAnnexB(m_pkt, buf, cap) : -1;
            int64_t pts = m_pkt->pts != AV_NOPTS_VALUE ? m_pkt->pts : m_pkt->dts;
            av_packet_unref(m_pkt);
            if (written < 0) {
                // Malformed or oversized: hand the buffer back empty, let the software path decide.
                AMediaCodec_queueInputBuffer(m_codec, index, 0, 0, 0, 0);
                reportAnomaly("packet conversion");
                return false;
            }
            AMediaCodec_queueInputBuffer(m_codec, index, 0, (size_t) written, (uint64_t) (pts < 0 ? 0 : pts), 0);
            return true;
        }
    }

    bool readImage(int64_t pts) {
        const ImageReaderApi &api = imageReaderApi();
        AImage *image = nullptr;
        // Rendering to the reader's surface is asynchronous; the frame lands a few ms later.
        for (int i = 0; i < 40; i++) {
            if (api.acquireLatest(m_reader, &image) == AMEDIA_OK && image != nullptr) {
                break;
            }
            image = nullptr;
            usleep(1000);
        }
        if (image == nullptr) {
            return false;
        }
        bool ok = false;
        uint8_t *y = nullptr, *u = nullptr, *v = nullptr;
        int yLen = 0, uLen = 0, vLen = 0;
        int32_t yStride = 0, uStride = 0, vStride = 0, uvPixel = 0;
        AImageCropRect crop{0, 0, m_width, m_height};
        if (api.planeData(image, 0, &y, &yLen) == AMEDIA_OK &&
            api.planeData(image, 1, &u, &uLen) == AMEDIA_OK &&
            api.planeData(image, 2, &v, &vLen) == AMEDIA_OK &&
            api.rowStride(image, 0, &yStride) == AMEDIA_OK &&
            api.rowStride(image, 1, &uStride) == AMEDIA_OK &&
            api.rowStride(image, 2, &vStride) == AMEDIA_OK &&
            api.pixelStride(image, 1, &uvPixel) == AMEDIA_OK) {
            api.cropRect(image, &crop);
            const int left = crop.left > 0 ? crop.left & ~1 : 0;
            const int top = crop.top > 0 ? crop.top & ~1 : 0;
            const int cropW = crop.right > crop.left ? crop.right - crop.left : m_width;
            const int cropH = crop.bottom > crop.top ? crop.bottom - crop.top : m_height;
            if (cropW >= m_width && cropH >= m_height && ensureFrame()) {
                // libyuv needs the V plane stride equal to U's for the interleaved case; both
                // are what the image reports, and Android420ToI420 handles pixel stride 1 and 2.
                libyuv::Android420ToI420(
                        y + (size_t) top * yStride + left, yStride,
                        u + (size_t) (top / 2) * uStride + (size_t) (left / 2) * uvPixel, uStride,
                        v + (size_t) (top / 2) * vStride + (size_t) (left / 2) * uvPixel, vStride,
                        uvPixel,
                        m_frame->data[0], m_frame->linesize[0],
                        m_frame->data[1], m_frame->linesize[1],
                        m_frame->data[2], m_frame->linesize[2],
                        m_width, m_height);
                AVCodecParameters *par = m_fmt->streams[m_streamIndex]->codecpar;
                m_frame->pts = pts;
                m_frame->best_effort_timestamp = pts;
                m_frame->colorspace = par->color_space;
                m_frame->color_range = par->color_range;
                m_producedFrames = true;
                ok = true;
            } else {
                reportAnomaly("image smaller than stream");
            }
        }
        api.deleteImage(image);
        return ok;
    }

    bool ensureFrame() {
        if (m_frame->data[0] != nullptr && m_frame->width == m_width && m_frame->height == m_height) {
            return true;
        }
        av_frame_unref(m_frame);
        m_frame->format = AV_PIX_FMT_YUV420P;
        m_frame->width = m_width;
        m_frame->height = m_height;
        return av_frame_get_buffer(m_frame, 32) == 0;
    }

    AVFormatContext *m_fmt;
    int m_streamIndex;
    AVPacket *m_pkt = nullptr;
    AVFrame *m_frame = nullptr;
    AMediaCodec *m_codec = nullptr;
    AImageReader *m_reader = nullptr;
    std::vector<uint8_t> m_csd0;
    std::vector<uint8_t> m_csd1;
    int m_nalLengthSize = 4;
    int m_width = 0;
    int m_height = 0;
    int m_failures = 0;
    bool m_inputEos = false;
    bool m_outputEos = false;
    bool m_holdsSlot = false;
    bool m_producedFrames = false;
};

}  // namespace prime_hw

// The reader VideoInfo holds: hardware when asked for and possible, software otherwise, and
// software from the point hardware fails on.
class FrameSource {
public:
    using Status = VideoFrameReader::Status;

    std::function<bool()> shouldAbort;

    FrameSource(AVFormatContext *fmt, AVCodecContext *dec, int streamIndex, bool tryHardware)
            : m_sw(fmt, dec, streamIndex) {
        m_sw.shouldAbort = [this]() { return shouldAbort && shouldAbort(); };
        if (tryHardware) {
            m_hw = new prime_hw::MediaCodecFrameReader(fmt, streamIndex);
            m_hw->shouldAbort = [this]() { return shouldAbort && shouldAbort(); };
            // init() reads no packets, so the software reader is still at the start if it fails.
            if (!m_hw->init()) {
                delete m_hw;
                m_hw = nullptr;
            }
        }
    }

    ~FrameSource() {
        delete m_hw;
    }

    FrameSource(const FrameSource &) = delete;
    FrameSource &operator=(const FrameSource &) = delete;

    Status getNextFrame() {
        if (m_hw != nullptr) {
            Status st = m_hw->getNextFrame();
            if (st != Status::Error) {
                return st;
            }
            LOGE("hw decode failed, continuing in software");
            delete m_hw;
            m_hw = nullptr;
            m_sw.seek(m_lastSeekPts);
        }
        return m_sw.getNextFrame();
    }

    bool seek(int64_t pts, int flags = AVSEEK_FLAG_BACKWARD) {
        m_lastSeekPts = pts;
        return m_hw != nullptr ? m_hw->seek(pts, flags) : m_sw.seek(pts, flags);
    }

    AVFrame *frame() const { return m_hw != nullptr ? m_hw->frame() : m_sw.frame(); }

    double frameTimeSeconds() const { return m_hw != nullptr ? m_hw->frameTimeSeconds() : m_sw.frameTimeSeconds(); }

    bool isHardware() const { return m_hw != nullptr; }

private:
    VideoFrameReader m_sw;
    prime_hw::MediaCodecFrameReader *m_hw = nullptr;
    int64_t m_lastSeekPts = 0;
};
