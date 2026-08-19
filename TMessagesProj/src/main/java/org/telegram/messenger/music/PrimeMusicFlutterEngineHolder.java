package org.telegram.messenger.music;

import android.content.Context;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.dart.DartExecutor;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.PrimeFlutterSupport;

/**
 * PrimeGram: pre-warms a {@link FlutterEngine} running the Music tab's own Dart entry point
 * ({@code musicMain()} in {@code prime_flutter_module/lib/music_main.dart}), kept
 * deliberately separate from {@link org.telegram.messenger.PrimeFlutterEngineHolder}'s demo
 * engine - two independent engines, two independent Dart entry points in the same module,
 * neither aware of the other. {@link org.telegram.ui.PrimeMusicActivity} attaches a
 * {@code FlutterView} to this engine every time the Music tab becomes visible and detaches
 * (not destroys) it when hidden, so switching tabs never triggers a cold Dart restart.
 */
public final class PrimeMusicFlutterEngineHolder {

    public static final String ENGINE_ID = "prime_music_engine";
    private static final String ENTRYPOINT_LIBRARY = "package:prime_flutter_module/music_main.dart";
    private static final String ENTRYPOINT_FUNCTION = "musicMain";

    private static volatile boolean warmUpAttempted;

    private PrimeMusicFlutterEngineHolder() {
    }

    /** Idempotent - safe to call every time the Music tab is about to become visible. */
    public static void warmUp(Context context) {
        if (warmUpAttempted || !PrimeFlutterSupport.isSupported()) {
            return;
        }
        warmUpAttempted = true;
        try {
            FlutterEngine engine = new FlutterEngine(context.getApplicationContext());
            // The 2-arg DartEntrypoint(pathToBundle, functionName) constructor leaves
            // dartEntrypointLibrary null, which resolves against the module's default
            // root library (lib/main.dart) only - musicMain() lives in music_main.dart, so
            // that overload silently fails to find it (no crash, just an engine that never
            // renders anything - confirmed on device: blank FlutterView, tabs still
            // navigable). The 3-arg constructor with an explicit package: URI is Flutter's
            // own documented way to select a non-default entry point.
            DartExecutor.DartEntrypoint entrypoint = new DartExecutor.DartEntrypoint(
                    FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                    ENTRYPOINT_LIBRARY, ENTRYPOINT_FUNCTION);
            engine.getDartExecutor().executeDartEntrypoint(entrypoint);
            PrimeMusicChannel.attach(engine);
            FlutterEngineCache.getInstance().put(ENGINE_ID, engine);
        } catch (Exception e) {
            FileLog.e(e);
            warmUpAttempted = false;
        }
    }
}
