import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    buildTypes {
        create("standalone") {
            matchingFallbacks += listOf("release")
        }
        create("standaloneDebug") {
            matchingFallbacks += listOf("debug")
        }
        create("hockeyapp") {
            matchingFallbacks += listOf("release")
        }
        create("hockeyappDebug") {
            matchingFallbacks += listOf("debug")
        }
    }
    namespace = "vpn.proxy"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // libXray.aar — build from https://github.com/xtls/libxray (gomobile bind -target android -androidapi 21)
    // Place the resulting libXray.aar into vpn/proxy/libs/
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}

