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

android {
    namespace = "com.zivett.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.zivett.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Mac's Bonjour name, for a physical phone (Debug only reads it).
        buildConfigField("String", "LAN_HOST", "\"$lanHost\"")
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
            optimization {
                enable = false
            }
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
