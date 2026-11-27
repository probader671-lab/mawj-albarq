plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.mawjalbarq.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mawjalbarq.app"
        minSdk = 24
        targetSdk = 34
        versionCode = System.getenv("BUILD_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "1.0." + (System.getenv("BUILD_VERSION_CODE") ?: "0")
    }
    signingConfigs { create("fixed") {
        storeFile = rootProject.file("release.keystore"); storePassword = "mawjalbarq2026"
        keyAlias = "mawj"; keyPassword = "mawjalbarq2026" } }
    buildTypes { debug { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("fixed") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("com.google.mlkit:face-detection:16.1.6")
    implementation("androidx.media3:media3-transformer:1.3.1")
    implementation("androidx.media3:media3-effect:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
