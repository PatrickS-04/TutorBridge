import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    //Firebase
    id("com.google.gms.google-services")
}

android {
    namespace = "com.tutorbridge.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tutorbridge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // local.properties keeps machine-specific values out of git.
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }
                ?.inputStream()?.use { load(it) }
        }

        // 10.0.2.2 is the emulator's alias for the host's localhost and is
        // unroutable from a physical device, which needs the dev machine's LAN
        // IP (or the deployed URL) set as API_BASE_URL in local.properties.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${localProperties.getProperty("API_BASE_URL", "http://10.0.2.2:5000/")}\""
        )

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
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
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
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    //Firebase
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
    implementation("com.google.firebase:firebase-messaging")
    //MongoDB
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    //Biometric
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("com.google.code.gson:gson:2.11.0")
    //Google Maps is reached by intent (see VenueLink), so the SDK is not a dependency.
}
