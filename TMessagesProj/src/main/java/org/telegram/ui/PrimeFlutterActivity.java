package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngineCache;

import org.telegram.messenger.PrimeFlutterEngineHolder;
import org.telegram.messenger.PrimeFlutterSupport;

/**
 * PrimeGram: host activity for the embedded Flutter engine - infrastructure for the
 * client's own future screens, not exposed to plugins (see the doc comments on
 * {@code settings.gradle}'s {@code :flutter} include and
 * {@link PrimeFlutterEngineHolder} for why).
 *
 * <p>Currently launches a single proof-of-life "Hello Flutter" screen
 * ({@code prime_flutter_module/lib/main.dart}) with no real Java↔Dart interop wired up yet -
 * that (a {@code MethodChannel}) is the natural next step once this is confirmed working.
 */
public class PrimeFlutterActivity extends FlutterActivity {

    public static Intent createIntent(Context context) {
        return new Intent(context, PrimeFlutterActivity.class);
    }

    @Override
    public String getCachedEngineId() {
        // Idempotent - covers the case where the lazy warm-up (DialogsActivity.onResume)
        // never got a chance to run before the user reached this activity.
        PrimeFlutterEngineHolder.warmUp(this);
        boolean cached = FlutterEngineCache.getInstance().get(PrimeFlutterEngineHolder.ENGINE_ID) != null;
        return cached ? PrimeFlutterEngineHolder.ENGINE_ID : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!PrimeFlutterSupport.isSupported()) {
            Toast.makeText(this, "Flutter недоступен на этом устройстве", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        super.onCreate(savedInstanceState);
    }
}
