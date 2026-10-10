plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val generatedIconAssets = layout.buildDirectory.dir("generated/uxaoroAssets")
val syncUxaoroIcons = tasks.register<Sync>("syncUxaoroIcons") {
    from(rootProject.projectDir.resolve("../../icons"))
    into(generatedIconAssets.map { it.dir("icons") })
}
tasks.named("preBuild").configure { dependsOn(syncUxaoroIcons) }

val uxaoroVersionCode = providers.gradleProperty("uxaoroVersionCode").orNull
    ?.toIntOrNull()
    ?.takeIf { it > 0 } ?: 2
val uxaoroVersionName = providers.gradleProperty("uxaoroVersionName").orNull
    ?.takeIf { it.isNotBlank() } ?: "0.1.0"

android {
    namespace = "org.uxaoro.app"
    compileSdk = 34

    sourceSets.getByName("main").assets.srcDir(generatedIconAssets)

    defaultConfig {
        applicationId = "org.uxaoro.app"
        minSdk = 26
        targetSdk = 34
        versionCode = uxaoroVersionCode
        versionName = uxaoroVersionName
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        val ksPath = System.getenv("UXAORO_KEYSTORE_PATH")
        if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("UXAORO_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("UXAORO_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("UXAORO_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            // Keep CI/Android Studio test builds separate from the installable release.
            // Debug keystores are machine-local and are not stable update signatures.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // A release must only use the explicitly configured stable keystore.
            // Never silently sign a release APK with the machine's debug key.
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-svg:2.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.getstream:stream-webrtc-android:1.3.8")
    implementation("dev.chrisbanes.haze:haze:0.7.3")
}
