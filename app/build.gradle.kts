import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.diaabend"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.diaabend"
        // 22 = Fire OS 5, the oldest Fire TV generation still in use
        minSdk = 22
        targetSdk = 34
        // versionName = git tag without the "v", versionCode = MAJOR*10000 + MINOR*100 + PATCH.
        // F-Droid checks both against the tag, see RELEASING.md
        versionCode = 100
        versionName = "0.1.0-rc1"
    }

    // No Google-encrypted dependency blob in the APK: F-Droid rejects it and it breaks reproducible builds
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Release signing comes from the environment (set by the release workflow from GitHub secrets).
    // Without it, assembleRelease builds an unsigned APK.
    val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf("META-INF/versions/**", "META-INF/*.md", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")

    testImplementation("junit:junit:4.13.2")
}

// No baseline profile (assets/dexopt/baseline.prof): it isn't always byte-identical between builds,
// which would fail F-Droid's reproducible build check. Startup gain is negligible for an app this size.
tasks.configureEach {
    if (name.contains("ArtProfile")) enabled = false
}
