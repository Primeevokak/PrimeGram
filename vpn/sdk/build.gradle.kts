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
    namespace = "vpn.sdk"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":network"))
    api(project(":tunnel"))
    api(project(":proxy"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.navigation.runtime)
}

