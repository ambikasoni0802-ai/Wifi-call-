// build.gradle.kts (App-level)
// Configures the Android application module: SDK versions, build types,
// and all required dependencies for WiFiCall.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Google Services must come AFTER android/kotlin plugins
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.wificall.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wificall.app"
        minSdk = 26           // Android 8.0 – needed for notification channels
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Enable View Binding so we can reference XML views without findViewById
    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.datastore.preferences)

    // Material Design 3
    implementation(libs.material)

    // Lifecycle – ViewModel + LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation Component (fragment-based nav graph)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Firebase – BOM ensures version alignment across all Firebase libs
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.storage.ktx)

    // Image loading
    implementation(libs.glide)
    implementation(libs.circleimageview)

    // WebRTC (Stream's maintained fork of Google WebRTC for Android)
    implementation(libs.webrtc)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
