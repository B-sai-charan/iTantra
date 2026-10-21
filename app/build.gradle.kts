plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.itantra"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.itantra.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
        }
    }

    androidResources {
        noCompress += "onnx"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Offline ONNX Runtime for Silero VAD & local ML
    implementation(libs.onnxruntime.android)

    // Static Sherpa-ONNX AAR (embedded ORT statically linked in libsherpa-onnx-jni.so)
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar"))

    // Experimental Vosk Android diagnostic runtime
    implementation(files("libs/vosk-android-0.3.75.aar"))
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    testImplementation(libs.junit)
    testImplementation(libs.onnxruntime)
}


