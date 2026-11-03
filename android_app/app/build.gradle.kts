import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Reference: Android Developers. 2026. Save data in a local database using Room. [Online].
    //   Available at: https://developer.android.com/training/data-storage/room [Accessed 20 October 2026].
    alias(libs.plugins.ksp)
    //Firebase
    id("com.google.gms.google-services")
}

// local.properties keeps machine-specific values out of git.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}

// A fresh clone talks to the API on this machine (npm run dev, port 5000). The
// default 127.0.0.1 makes every build tunnel that port on each connected device or
// emulator to this machine over adb, whatever network the phone is on. To override,
// set API_BASE_URL in local.properties:
//   emulator -> http://10.0.2.2:5000/ (the emulator's alias for the host's localhost,
//               unroutable from a physical device)
// Reference: Android Developers. 2026. Android Emulator networking. [Online].
//   Available at: https://developer.android.com/studio/run/emulator-networking [Accessed 21 September 2026].
val apiBaseUrl: String = localProperties.getProperty(
    "API_BASE_URL",
    "http://127.0.0.1:5000/"
)

android {
    namespace = "com.tutorbridge.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tutorbridge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    testOptions {
        // android.util.Log is not on the JVM unit-test classpath; without this the
        // first Log call in code under test throws "not mocked" instead of returning.
        unitTests.isReturnDefaultValues = true
    }
}

// Room writes each schema version here, so a migration can be checked against
// the one before it. The folder is committed.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// adb reverse drops whenever a device is unplugged or restarts, so redo it on
// every build instead of leaving the app timing out against 127.0.0.1.
// Reference: Android Developers. 2026. Android Debug Bridge (adb). [Online].
//   Available at: https://developer.android.com/tools/adb [Accessed 21 September 2026].
val apiUri = URI(apiBaseUrl)
if (apiUri.host == "127.0.0.1" || apiUri.host == "localhost") {
    val adb = androidComponents.sdkComponents.adb
    val port = "tcp:${apiUri.port}"
    val adbReverse = tasks.register("adbReverse") {
        doLast {
            val adbPath = adb.get().asFile.path
            ProcessBuilder(adbPath, "devices").start().inputStream.bufferedReader().readLines()
                .filter { it.endsWith("\tdevice") }
                .forEach { ProcessBuilder(adbPath, "-s", it.substringBefore('\t'), "reverse", port, port).start().waitFor() }
        }
    }
    tasks.named("preBuild") { dependsOn(adbReverse) }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.android)

    // Offline mode: Room caches what the API sent, WorkManager replays what the
    // user did while offline once the connection is back.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    //Firebase
    // Reference: Firebase. 2026. Add Firebase to your Android project. [Online].
    //   Available at: https://firebase.google.com/docs/android/setup [Accessed 21 September 2026].
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    // Sign-in gives an ID token; the calendar scope and its offline auth code
    // come from the authorisation API, which lives here.
    implementation("com.google.android.gms:play-services-auth:21.1.1")
    //feat-notification but still falls under firebase
    //MongoDB
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    //Biometric
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("com.google.code.gson:gson:2.11.0")
    //Google Maps is reached by intent (see VenueLink), so the SDK is not a dependency.

    implementation("androidx.appcompat:appcompat:1.8.0")

    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.3")
}
