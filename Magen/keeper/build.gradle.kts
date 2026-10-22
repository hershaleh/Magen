plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.magen.keeper"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.magen.keeper"
        // transferOwnership() needs Android 9.
        minSdk = 28
        targetSdk = 34
        versionCode = maxOf(1, System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0"
    }

    // One fixed key for every build, so a new APK always installs over the old one and Magen Keeper
    // can recognise Magen by its signature. The key file sits next to settings.gradle.kts.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("magen-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
