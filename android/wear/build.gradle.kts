plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.manawear.watch"
    // Wear Compose 1.7 and lifecycle 2.11 require compiling against 37. targetSdk stays
    // 36 until the API 37 ACCESS_LOCAL_NETWORK behaviour is handled.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        // Same id as the phone app: the Data Layer only pairs identical package names.
        applicationId = "io.github.manawear"
        minSdk = 30
        targetSdk = 36
        // Form factor 2: watch. Higher than the phone code, as Play expects for Wear builds.
        versionCode = providers.gradleProperty("mana.versionCode").get().toInt() * 10 + 2
        versionName = providers.gradleProperty("mana.versionName").get()
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.complications.data.source)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.concurrent.futures)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(project(":shared"))
    implementation(libs.play.services.wearable)
    implementation(libs.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
