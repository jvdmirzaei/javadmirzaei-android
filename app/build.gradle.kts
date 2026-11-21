plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

base {
    archivesName.set("JAVADMIRZAEI.IR")
}

android {
    namespace = "ir.javadmirzaei.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.javadmirzaei.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.1.0"
    }

    // کلید امضا: این فایل را حتماً نگه دارید و گم نکنید. برای آپدیت‌های بعدی برنامه لازم است.
    signingConfigs {
        create("release") {
            storeFile = file("jm-release.keystore")
            storePassword = "JMapp#2026Key"
            keyAlias = "jm"
            keyPassword = "JMapp#2026Key"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
