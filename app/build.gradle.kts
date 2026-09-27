plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "app.filemate"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.filemate"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0-stage2c"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    // The private test signing key is outside Git, backed up separately.
    val proofKey = providers.environmentVariable("FILEMATE_TEST_KEY").orNull
    if (proofKey != null) {
        signingConfigs.getByName("debug") {
            storeFile = file(proofKey)
            storePassword = providers.environmentVariable("FILEMATE_KEY_PASSWORD").get()
            keyAlias = "filemate-proof"
            keyPassword = storePassword
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
