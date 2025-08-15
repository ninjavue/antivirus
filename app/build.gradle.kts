plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "uz.csec.antivirus"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.csec.antivirus"
        minSdk = 24
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.activity)
    implementation(libs.viewpager2)
    implementation("com.airbnb.android:lottie:6.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    
    // Ensure Material Design is properly included
    implementation("com.google.android.material:material:1.12.0")
    
    // CameraX dependencies
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // ML Kit Barcode scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}