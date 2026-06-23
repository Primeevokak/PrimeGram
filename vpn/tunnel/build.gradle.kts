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
    namespace = "vpn.tunnel"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.amneziawg.zaneschepke)
}

