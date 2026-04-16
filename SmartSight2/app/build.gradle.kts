import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.example.smartsight"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartsight"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // This creates BuildConfig.VISION_API_KEY in your Java code
        // Ensure you have VISION_API_KEY defined in your local.properties file
        // Read API key from local.properties manually
        val visionApiKey: String by lazy {
            val properties = Properties()
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                properties.load(localPropsFile.inputStream())
            }
            properties.getProperty("VISION_API_KEY") ?: ""
        }

        buildConfigField(
            "String",
            "VISION_API_KEY",
            "\"$visionApiKey\""
        )
    }

    buildFeatures {
        // Required to generate the BuildConfig class
        buildConfig = true
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Room database dependencies
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    // CameraX dependencies for scanning
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // ML Kit OCR
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    // Networking for Cloud Vision API [cite: 5]
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing for API responses [cite: 5]
    implementation("com.google.code.gson:gson:2.10.1")

    // UI components
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
