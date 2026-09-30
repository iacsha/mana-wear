plugins {
    alias(libs.plugins.android.application)
}

// Resource-only Watch Face Format package. It holds no code, and it must ship as its own
// APK apart from the Mana app that provides the complication data.
android {
    namespace = "io.github.manawear.watchface"
    // No code at all, so no built-in Kotlin either; otherwise the stdlib lands in the APK.
    enableKotlin = false
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "io.github.manawear.watchface"
        // WFF version 2 runs on Wear OS 5 (API 34) and later.
        minSdk = 34
        targetSdk = 36
        versionCode = providers.gradleProperty("mana.versionCode").get().toInt() * 10
        versionName = providers.gradleProperty("mana.versionName").get()
    }

    buildTypes {
        release {
            // R8 drops the unused R classes, so no dex ships: Play rejects a watch face
            // bundle with minSdk 33+ that has one ("cannot have dex files").
            isMinifyEnabled = true
            // The WFF runtime reads resources by name, which the shrinker cannot see.
            isShrinkResources = false
        }
    }
}
