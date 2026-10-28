plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val aquaVersion = rootProject.file("../VERSION").readText().trim()
val keystore: String? = System.getenv("AQUA_KEYSTORE")?.takeIf { file(it).exists() }

android {
    namespace = "app.aquabrowser.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.aquabrowser.android"
        minSdk = 26
        targetSdk = 36
        versionName = aquaVersion
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
    }

    signingConfigs {
        if (keystore != null) {
            create("aqua") {
                storeFile = file(keystore)
                storePassword = System.getenv("AQUA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AQUA_KEY_ALIAS") ?: "aqua"
                keyPassword = System.getenv("AQUA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("aqua") ?: signingConfigs.getByName("debug")
        }
    }

    // One APK per processor type: arm64 for phones and tablets, x86_64 for the emulator.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.mozilla.geckoview:geckoview:+")
}
