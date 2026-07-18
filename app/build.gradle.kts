plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.noriskservis.wpayin.wallet"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.noriskservis.wpayin.wallet"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField(
            "String",
            "COINGECKO_API_KEY",
            "\"${providers.gradleProperty("COINGECKO_API_KEY").orNull.orEmpty()}\"",
        )
        buildConfigField(
            "String",
            "ETHERSCAN_API_KEY",
            "\"${providers.gradleProperty("ETHERSCAN_API_KEY").orNull.orEmpty()}\"",
        )
    }

    flavorDimensions += "walletEngine"
    productFlavors {
        create("preview") {
            dimension = "walletEngine"
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            buildConfigField("boolean", "REAL_WALLET_ENABLED", "false")
        }
        create("walletCore") {
            dimension = "walletEngine"
            buildConfigField("boolean", "REAL_WALLET_ENABLED", "true")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    packaging.resources.excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "META-INF/DEPENDENCIES",
    )
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.google.zxing:core:3.5.4")

    "walletCoreImplementation"("com.trustwallet:wallet-core:4.6.13")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
