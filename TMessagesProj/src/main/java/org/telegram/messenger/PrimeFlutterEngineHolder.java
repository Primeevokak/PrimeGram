package org.telegram.messenger;

import android.content.Context;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.dart.DartExecutor;

/**
 * PrimeGram: pre-warms one {@link FlutterEngine} and keeps it in Flutter's own
 * {@link FlutterEngineCache} so {@link org.telegram.ui.PrimeFlutterActivity} can attach to
 * an already-running Dart VM instead of cold-starting one the moment the user opens it -
 * without this, the first frame would visibly stall.
 *
 * <p>Deliberately not warmed from {@code ApplicationLoader.onCreate()} - creating a
 * {@link FlutterEngine} isn't free, and taxing every single app launch for a screen most
 * users may never open is exactly the kind of cold-start regression this codebase already
 * measures for ({@link PrimeStartupTrace}). Callers are expected to invoke {@link #warmUp}
 * lazily, once the entry point becomes reachable, well past the cold-start-critical path.
 */
public final class PrimeFlutterEngineHolder {

    public static final String ENGINE_ID = "prime_flutter_engine";

    private static volatile boolean warmUpAttempted;

    private PrimeFlutterEngineHolder() {
    }

    /** Idempotent - safe to call from more than one entry point. */
    public static void warmUp(Context context) {
        if (warmUpAttempted || !PrimeFlutterSupport.isSupported()) {
            return;
        }
        warmUpAttempted = true;
        try {
            FlutterEngine engine = new FlutterEngine(context.getApplicationContext());
            engine.getDartExecutor().executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault());
            FlutterEngineCache.getInstance().put(ENGINE_ID, engine);
        } catch (Exception e) {
            // Never fatal to the host app - PrimeFlutterActivity falls back to creating its
            // own fresh engine (slower first open, still correct) when the cache misses.
            FileLog.e(e);
            warmUpAttempted = false;
        }
    }
}
