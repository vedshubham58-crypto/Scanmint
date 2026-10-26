import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Google's official test IDs. Safe to click; they never earn money.
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"
val testRewardedId = "ca-app-pub-3940256099942544/5224354917"

fun prop(name: String): String =
    (System.getenv(name) ?: project.findProperty(name)?.toString()).orEmpty().trim()

fun realOrTest(name: String, test: String): String = prop(name).ifEmpty { test }

android {
    namespace = "com.vedshubham.scanmint"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vedshubham.scanmint"
        minSdk = 26
        targetSdk = 36
        versionCode = (prop("VERSION_CODE").ifEmpty { "1" }).toInt()
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "BANNER_ID", "\"$testBannerId\"")
            buildConfigField("String", "INTERSTITIAL_ID", "\"$testInterstitialId\"")
            buildConfigField("String", "REWARDED_ID", "\"$testRewardedId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = realOrTest("ADMOB_APP_ID", testAppId)
            buildConfigField("String", "BANNER_ID", "\"${realOrTest("ADMOB_BANNER_ID", testBannerId)}\"")
            buildConfigField("String", "INTERSTITIAL_ID", "\"${realOrTest("ADMOB_INTERSTITIAL_ID", testInterstitialId)}\"")
            buildConfigField("String", "REWARDED_ID", "\"${realOrTest("ADMOB_REWARDED_ID", testRewardedId)}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Google's on-device scanner (camera, edge detection, crop, filters) and text reading.
    // Both run inside Google Play services, so they add almost nothing to the APK size.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-devanagari:16.0.1")

    // Ads (AdMob) and the consent popup required in Europe/UK
    implementation("com.google.android.gms:play-services-ads:24.3.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")

    testImplementation("junit:junit:4.13.2")
}
