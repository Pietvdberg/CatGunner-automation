plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "nl.pietvdberg.catgunner.autopilot"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.pietvdberg.catgunner.autopilot"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0-single-purchase"
    }
    splits {
        abi {
            isEnable = providers.gradleProperty("splitApks").isPresent
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        warningsAsErrors = true
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}

ktlint {
    version.set("1.5.0")
    android.set(true)
    outputToConsole.set(true)
}
