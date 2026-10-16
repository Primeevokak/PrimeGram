-keep public class com.google.android.gms.* { public *; }
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

-keep @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * { @androidx.annotation.Keep *; }

-keep class org.webrtc.* { *; }
-keep class org.webrtc.audio.* { *; }
-keep class org.webrtc.voiceengine.* { *; }
-keep class org.telegram.messenger.* { *; }
# PrimeGram: everything Python reaches into via Chaquopy reflection. R8 cannot see a call made
# through java.lang.reflect the way Chaquopy makes it, so a class or member with no ordinary
# Java caller looks unused and gets stripped or renamed - which is invisible until a plugin
# calls it and gets a raw AttributeError with no clue that the real cause is the release build,
# not the plugin. The two packages below are exactly what plugins touch: our own engine, and the
# Telegram classes the SDK deliberately hands them (Bulletin, AlertDialog, and the rest of
# org.telegram.ui.* that ui/*.py and client_utils.py import by name).
-keep class org.telegram.messenger.plugins.** { *; }
# PrimeGram: the exteraGram-compatibility surface (compat/PrimePluginXposed shims a plugin written
# for the real exteraGram reaches for by name, e.g. zwylib's `from com.exteragram.messenger.plugins
# import PythonPluginsEngine`) - same reasoning as the block above, and the same failure mode: this
# was added without a matching keep rule once, and every method on it not otherwise referenced from
# Java got stripped or renamed in a release build, which is what actually made
# PythonPluginsEngine.getClass().getDeclaredMethods() come back wrong - not the class being a
# placeholder, the real class just wasn't the real class anymore by the time R8 was done with it.
-keep class com.exteragram.** { *; }
-keep class org.telegram.ui.** { *; }
-keep class org.telegram.tgnet.** { *; }
-keep class org.telegram.messenger.camera.* { *; }
-keep class org.telegram.messenger.secretmedia.* { *; }
-keep class org.telegram.messenger.support.* { *; }
-keep class org.telegram.messenger.support.* { *; }
-keep class org.telegram.messenger.time.* { *; }
-keep class org.telegram.messenger.video.* { *; }
-keep class org.telegram.messenger.voip.* { *; }
-keep class org.telegram.SQLite.** { *; }
-keep class org.telegram.tgnet.ConnectionsManager { *; }
-keep class org.telegram.tgnet.NativeByteBuffer { *; }
-keep class org.telegram.tgnet.RequestTimeDelegate { *; }
-keep class org.telegram.tgnet.RequestDelegate { *; }
-keep class org.telegram.ui.Stories.recorder.FfmpegAudioWaveformLoader { *; }
-keep class androidx.mediarouter.app.MediaRouteButton { *; }
-keepclassmembers class ** {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class org.telegram.utils.code.highlight.PrismaHighlighter {
    private static native <methods>;
}

# https://developers.google.com/ml-kit/known-issues#android_issues
-keep class com.google.mlkit.nl.languageid.internal.LanguageIdentificationJni { *; }

# Huawei Services
-keep class com.huawei.hianalytics.**{ *; }
-keep class com.huawei.updatesdk.**{ *; }
-keep class com.huawei.hms.**{ *; }

# Don't warn about checkerframework and Kotlin annotations
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

# MVEL's JSR-223 scripting-engine integration is optional and unused here - we only ever call
# MVEL.compileExpression/executeExpression directly - and javax.script does not exist on Android.
-dontwarn javax.script.**

-keep class io.nano.tex.** {*;}

-keep class org.telegram.tgnet.** { *; }

# JLatexMath: macro/atom classes are loaded reflectively by Class.forName
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**

# Use -keep to explicitly keep any other classes shrinking would remove
-dontoptimize
-dontobfuscate
-dontwarn org.slf4j.**
