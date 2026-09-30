plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.manawear.phone"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        // Same id as the watch app: the Data Layer only pairs identical package names.
        applicationId = "io.github.manawear"
        minSdk = 29
        targetSdk = 36
        // Form factor 1: phone.
        versionCode = providers.gradleProperty("mana.versionCode").get().toInt() * 10 + 1
        versionName = providers.gradleProperty("mana.versionName").get()
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.play.services.wearable)
    // Google's scanner UI runs in Play services, so the app needs no camera permission.
    implementation(libs.play.services.code.scanner)
    implementation(libs.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
