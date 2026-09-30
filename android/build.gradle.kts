import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Release signing for every app module. The phone and watch apps must carry the same
// certificate or the Wear Data Layer will not connect them, so the key is configured once.
//
// MANA_KEYSTORE      path to the PKCS12 release keystore
// MANA_KEYSTORE_PASSWORD
// MANA_KEY_ALIAS     defaults to "mana"
//
// Without MANA_KEYSTORE a release build falls back to the debug key for local iteration.
// CI passes -Pmana.requireReleaseKey=true so a missing key fails the build instead.
val releaseKeystore = System.getenv("MANA_KEYSTORE")?.takeIf { it.isNotBlank() }
val requireReleaseKey = providers.gradleProperty("mana.requireReleaseKey").orNull == "true"
if (requireReleaseKey && releaseKeystore == null) {
    throw GradleException("mana.requireReleaseKey is set but MANA_KEYSTORE is not")
}

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            if (releaseKeystore != null) {
                signingConfigs.create("release") {
                    storeFile = file(releaseKeystore)
                    storePassword = System.getenv("MANA_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("MANA_KEY_ALIAS") ?: "mana"
                    keyPassword = storePassword
                }
            }
            buildTypes.getByName("release") {
                signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
            }
        }
    }
}
