plugins {
//    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
//    alias(libs.plugins.google.services) apply false
//    alias(libs.plugins.google.firebase.crashlytics) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            compileSdk = 35
            defaultConfig {
                minSdk = 21
            }
        }
    }
}
