import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.taskdroid.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.taskdroid.manager"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

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

    signingConfigs {
        create("release") {
            val props = Properties()
            val f = rootProject.file("keystore.properties")
            if (f.exists()) {
                f.inputStream().use { props.load(it) }
            }
            val storePath = (rootProject.findProperty("TD_KEYSTORE_PATH") as? String)
                ?: props.getProperty("storeFile")?.orEmpty().orEmpty()
            val storePassword = (rootProject.findProperty("TD_KEYSTORE_PASS") as? String)
                ?: props.getProperty("storePassword").orEmpty()
            val keyAlias = (rootProject.findProperty("TD_KEY_ALIAS") as? String)
                ?: props.getProperty("keyAlias").orEmpty()
            val keyPassword = (rootProject.findProperty("TD_KEY_PASS") as? String)
                ?: props.getProperty("keyPassword").orEmpty()

            val sf = if (storePath.isNotBlank()) file(storePath) else file("")
            if (sf.isFile && storePassword.isNotBlank() && keyAlias.isNotBlank() && keyPassword.isNotBlank()) {
                storeFile = sf
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}