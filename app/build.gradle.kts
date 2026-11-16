plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is read from environment variables (set by CI from GitHub Secrets).
// If they are absent, the release build falls back to the debug key so that
// `assembleRelease` always produces an installable APK.
val releaseStoreFile: String? = System.getenv("JR_KEYSTORE_FILE")
val hasReleaseKey = releaseStoreFile != null && file(releaseStoreFile).exists()

android {
    namespace = "com.jarvisrider"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvisrider"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("JR_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("JR_VERSION_NAME") ?: "1.0.0"
        resourceConfigurations += listOf("en", "hi")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = System.getenv("JR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JR_KEY_ALIAS")
                keyPassword = System.getenv("JR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = false
        aidl = false
        renderScript = false
        shaders = false
        resValues = false
    }

    packaging {
        resources {
            excludes += listOf("META-INF/**", "kotlin/**", "DebugProbesKt.bin", "**.kotlin_builtins")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        lintConfig = file("lint.xml")
        htmlReport = true
        xmlReport = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1g"
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}
