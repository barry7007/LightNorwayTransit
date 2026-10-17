plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

android {
    compileSdk = rootProject.ext["compileSdk"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int

        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String
    }

    // So the app can show its own version. versionName is set by the Light plugin from
    // lighttool.toml, which stays the single place a release number is written.
    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("lightsdkDev") {
            storeFile = file("../sdk/keys/lightsdk-dev.jks")
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
        release {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
    }

    lint {
        warningsAsErrors = false
        error += "RestrictedApi"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
        targetCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(rootProject.ext["jvmTarget"] as String))
    }
}

dependencies {
    // Sanntid never scans a QR code, but :sdk:ui's LightQrCodeScanner drags in ML Kit's
    // barcode model (libbarhopper_v3.so, ~19MB across four ABIs on an arm64-only phone).
    // Excluding it is sanctioned by Light — see light-sdk discussion #177 — until they
    // move dependency declaration into lighttool.toml.
    implementation(project(":sdk:client")) {
        exclude(group = "com.google.mlkit")
        exclude(group = "com.google.android.gms", module = "play-services-mlkit-barcode-scanning")
    }
    testImplementation(libs.kotlin.test)
}
