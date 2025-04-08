buildscript {
    repositories {
        google()
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.dublikunt.dmclient"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dublikunt.dmclient"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            packaging.resources.excludes += setOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "META-INF/*.txt",
                "META-INF/*.md",
                "META-INF/*.version",
                "META-INF/*.textproto",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/androidx/annotation/annotation/LICENSE.txt",
                "okhttp3/internal/publicsuffix/NOTICE",
                "META-INF/license.html",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.html",
                "META-INF/ASL2.0"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    ksp {
        arg("room.generateKotlin", "true")
    }
}

dependencies {
    implementation(libs.kotlin.serialization)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.runtime.livedata)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation)

    implementation(libs.androidx.datastore)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    implementation(libs.accompanist.permissions)

    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
}