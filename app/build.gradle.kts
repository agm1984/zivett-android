import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase Cloud Messaging needs the project's google-services.json
// (not in git — see README "Push notifications"). Without it the app
// still builds and runs; push registration just stays quiet.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Local overrides (gitignored): LAN_HOST for a physical phone on the
// same Wi-Fi as the Mac running Sail.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val lanHost: String = localProperties.getProperty("lan.host") ?: "Adams-MacBook-Pro.local"

// Release signing: the Play *upload* key (Play App Signing holds the
// real app-signing key). Read from local.properties, falling back to
// ZIVETT_UPLOAD_* environment variables for CI. See README "Shipping".
fun uploadSetting(property: String, env: String): String? =
    localProperties.getProperty(property) ?: System.getenv(env)
val uploadStoreFile = uploadSetting("upload.store.file", "ZIVETT_UPLOAD_STORE_FILE")

// versionCode = days since 2026-01-01 × 10 + build number (0-9,
// `-PbuildNumber=N` for a second upload the same day). Monotonic without
// bookkeeping and small enough that Play never complains (a raw yyyyMMdd
// code is rejected as "significantly higher than your previous version
// code"). A `-PversionCode=N` override wins outright. providers.exec is a
// tracked configuration-cache input, so the date is re-read every build.
val buildDate: Provider<String> = providers.exec {
    commandLine("date", "+%Y-%m-%d")
}.standardOutput.asText.map { it.trim() }
val buildNumber = (project.findProperty("buildNumber") as String?)?.toInt() ?: 0
val versionEpoch: LocalDate = LocalDate.of(2026, 1, 1)
val computedVersionCode: Int = (project.findProperty("versionCode") as String?)?.toInt()
    ?: (ChronoUnit.DAYS.between(versionEpoch, LocalDate.parse(buildDate.get())).toInt() * 10 + buildNumber)

android {
    namespace = "com.zivett.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.zivett.app"
        minSdk = 26
        targetSdk = 37
        versionCode = computedVersionCode
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Mac's Bonjour name, for a physical phone (Debug only reads it).
        buildConfigField("String", "LAN_HOST", "\"$lanHost\"")
    }

    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = uploadSetting("upload.store.password", "ZIVETT_UPLOAD_STORE_PASSWORD")
                keyAlias = uploadSetting("upload.key.alias", "ZIVETT_UPLOAD_KEY_ALIAS")
                keyPassword = uploadSetting("upload.key.password", "ZIVETT_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // The Sail container, reached through the emulator's host alias.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8090\"")
            buildConfigField("boolean", "BACKEND_SWITCHER", "true")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://zivett.com\"")
            buildConfigField("boolean", "BACKEND_SWITCHER", "false")
            // R8 shrink + obfuscate; keep rules in src/main/keepRules/*.keep.
            // Verified against production and a signed-in local pass
            // (see PARITY.md "Release signing").
            optimization {
                enable = true
            }
            // Unsigned when no upload key is configured (CI without
            // secrets, a fresh checkout): assembleRelease still works,
            // the artefact just is not installable until signed.
            signingConfig = signingConfigs.findByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.stripe.android)
    implementation(libs.osmdroid)
    implementation(libs.zxing.core)
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
