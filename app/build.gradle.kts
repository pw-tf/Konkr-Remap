plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from GitHub Actions secrets (see README).
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")

android {
    namespace = "com.pwtf.konkrremap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pwtf.konkrremap"
        minSdk = 30 // Wireless debugging (used to start the helper) needs Android 11.
        targetSdk = 35
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = findProperty("versionName") as String? ?: "0.0.0"
    }

    signingConfigs {
        if (keystorePassword != null) create("release") {
            storeFile = rootProject.file("release.jks")
            storePassword = keystorePassword
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // ponytail: no R8, the helper is started by class name via app_process. Add keep rules if APK size matters.
            isMinifyEnabled = false
            if (keystorePassword != null) signingConfig = signingConfigs["release"]
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")

    // ADB client used to pair with Wireless debugging and start the helper.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")

    testImplementation("junit:junit:4.13.2")
}
