import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
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
    namespace = "vpn.network"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":utils"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
}

