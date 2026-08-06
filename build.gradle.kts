// build.gradle.kts (Project-level)
// Root build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Google Services plugin for Firebase (must be declared here)
    alias(libs.plugins.google.services) apply false
}
