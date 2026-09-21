// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    //Firebase
    // Reference: Firebase. 2026. Add Firebase to your Android project. [Online].
    //   Available at: https://firebase.google.com/docs/android/setup [Accessed 21 September 2026].
    id("com.google.gms.google-services") version "4.5.0" apply false
}
