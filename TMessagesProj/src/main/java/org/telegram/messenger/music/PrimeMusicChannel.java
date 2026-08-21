package org.telegram.messenger.music;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

/**
 * PrimeGram: the bridge between the Music tab's Dart UI and the existing native music
 * backend ({@link PrimeMusicLibrary}, {@link PrimeMusicWave}, {@link PrimeMusicPlayback},
 * {@link MediaController}) - pure marshalling, no business logic of its own. Mirrors
 * {@code prime_flutter_module/lib/music/bridge.dart} 1:1; see that file for the Dart side
 * of each call.
 */
public final class PrimeMusicChannel {

    private static final String METHOD_CHANNEL = "primegram/music";
    private static final String EVENT_CHANNEL = "primegram/music/events";

    private PrimeMusicChannel() {
    }

    public static void attach(FlutterEngine engine) {
        MethodChannel methodChannel = new MethodChannel(engine.getDartExecutor().getBinaryMessenger(), METHOD_CHANNEL);
        methodChannel.setMethodCallHandler(PrimeMusicChannel::handleMethodCall);

        EventChannel eventChannel = new EventChannel(engine.getDartExecutor().getBinaryMessenger(), EVENT_CHANNEL);
        eventChannel.setStreamHandler(new PlaybackEventStreamHandler());
    }

    private static void handleMethodCall(MethodCall call, MethodChannel.Result result) {
        switch (call.method) {
            case "getRecommendations": {
                Integer limit = call.argument("limit");
                List<PrimeTrack> tracks = PrimeMusicLibrary.getRecommendations(limit != null ? limit : 30);
                List<Map<String, Object>> mapped = new ArrayList<>(tracks.size());
                for (PrimeTrack track : tracks) {
                    mapped.add(trackToMap(track));
                }
                result.success(mapped);
                break;
            }
            case "getCurrentState": {
                result.success(currentStateMap());
                break;
            }
            case "getTrack": {
                Number trackId = call.argument("trackId");
                PrimeTrack track = trackId == null ? null : PrimeMusicLibrary.getTrack(trackId.longValue());
                result.success(track == null ? null : trackToMap(track));
                break;
            }
            case "playSingle": {
                Number trackId = call.argument("trackId");
                if (trackId == null) {
                    result.error("bad_args", "trackId is required", null);
                    return;
                }
                PrimeTrack track = PrimeMusicLibrary.getTrack(trackId.longValue());
                if (track != null) {
                    PrimeMusicPlayback.playSingle(UserConfig.selectedAccount, track);
                }
                result.success(null);
                break;
            }
            case "pauseResume": {
                MediaController controller = MediaController.getInstance();
                MessageObject playing = controller.getPlayingMessageObject();
                if (playing != null) {
                    if (controller.isMessagePaused()) {
                        controller.playMessage(playing);
                    } else {
                        controller.pauseMessage(playing);
                    }
                }
                result.success(null);
                break;
            }
            case "seekTo": {
                Double progress = call.argument("progress");
                MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
                if (playing != null && progress != null) {
                    MediaController.getInstance().seekToProgress(playing, progress.floatValue());
                }
                result.success(null);
                break;
            }
            case "setLiked": {
                Number trackId = call.argument("trackId");
                Boolean liked = call.argument("liked");
                if (trackId != null && liked != null) {
                    PrimeMusicLibrary.setLiked(trackId.longValue(), liked);
                }
                result.success(null);
                break;
            }
            case "setDisliked": {
                Number trackId = call.argument("trackId");
                Boolean disliked = call.argument("disliked");
                if (trackId != null && disliked != null) {
                    PrimeMusicLibrary.setDisliked(trackId.longValue(), disliked);
                }
                result.success(null);
                break;
            }
            case "waveStart": {
                PrimeTrack track = PrimeMusicWave.current() == null ? PrimeMusicWave.start() : PrimeMusicWave.current();
                result.success(track == null ? null : trackToMap(track));
                break;
            }
            case "waveCurrent": {
                PrimeTrack track = PrimeMusicWave.current();
                result.success(track == null ? null : trackToMap(track));
                break;
            }
            case "waveNext": {
                PrimeTrack track = PrimeMusicWave.next();
                result.success(track == null ? null : trackToMap(track));
                break;
            }
            case "waveDislikeAndAdvance": {
                PrimeTrack track = PrimeMusicWave.dislikeCurrentAndAdvance();
                result.success(track == null ? null : trackToMap(track));
                break;
            }
            case "waveStop": {
                PrimeMusicWave.stop();
                result.success(null);
                break;
            }
            case "waveIsActive": {
                result.success(PrimeMusicWave.isActive());
                break;
            }
            case "waveSetMoodFilter": {
                String tag = call.argument("tag");
                if (tag == null) {
                    PrimeMusicWave.setMoodFilter(null);
                } else {
                    List<String> tags = new ArrayList<>();
                    tags.add(tag);
                    PrimeMusicWave.setMoodFilter(tags);
                }
                result.success(null);
                break;
            }
            default:
                result.notImplemented();
        }
    }

    private static Map<String, Object> trackToMap(PrimeTrack track) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", track.id);
        map.put("source", track.source);
        map.put("dialogId", track.dialogId);
        map.put("messageId", track.messageId);
        map.put("localPath", track.localPath);
        map.put("title", track.title);
        map.put("artist", track.artist);
        map.put("duration", track.duration);
        map.put("coverPath", track.coverPath);
        map.put("liked", track.liked);
        map.put("lyrics", track.lyrics);
        map.put("addedAt", track.addedAt);
        map.put("album", track.album);
        map.put("genre", track.genre);
        map.put("dateAdded", track.dateAdded);
        map.put("disliked", track.disliked);
        return map;
    }

    private static Map<String, Object> currentStateMap() {
        Map<String, Object> map = new HashMap<>();
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            map.put("trackId", null);
            map.put("paused", true);
            map.put("progress", 0.0);
            return map;
        }
        Long localId = PrimeMusicPlayback.resolveLocalTrackId(playing);
        map.put("trackId", localId);
        map.put("paused", MediaController.getInstance().isMessagePaused());
        map.put("progress", (double) playing.audioProgress);
        return map;
    }

    /** One native NotificationCenter observer per Dart listener - registered on
     *  {@code onListen}, torn down on {@code onCancel}, exactly like any other
     *  StreamHandler. State is always recomputed fresh from MediaController rather than
     *  read out of each notification's own args, since the shape of those differs per event
     *  id and none of it is needed beyond "something about playback changed, recheck". */
    private static class PlaybackEventStreamHandler implements EventChannel.StreamHandler, NotificationCenter.NotificationCenterDelegate {
        private EventChannel.EventSink sink;

        @Override
        public void onListen(Object arguments, EventChannel.EventSink events) {
            sink = events;
            NotificationCenter center = NotificationCenter.getInstance(UserConfig.selectedAccount);
            center.addObserver(this, NotificationCenter.messagePlayingDidStart);
            center.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            center.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            center.addObserver(this, NotificationCenter.messagePlayingDidReset);
        }

        @Override
        public void onCancel(Object arguments) {
            NotificationCenter center = NotificationCenter.getInstance(UserConfig.selectedAccount);
            center.removeObserver(this, NotificationCenter.messagePlayingDidStart);
            center.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            center.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            center.removeObserver(this, NotificationCenter.messagePlayingDidReset);
            sink = null;
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (sink == null) {
                return;
            }
            Map<String, Object> state = currentStateMap();
            if (id == NotificationCenter.messagePlayingDidStart) {
                state.put("type", "start");
            } else if (id == NotificationCenter.messagePlayingPlayStateChanged) {
                state.put("type", "stateChanged");
            } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
                state.put("type", "progress");
            } else if (id == NotificationCenter.messagePlayingDidReset) {
                state.put("type", "reset");
            }
            sink.success(state);
        }
    }
}
