plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "org.oneeyedmanlabs.audiotag"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.oneeyedmanlabs.audiotag"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing credentials come from the environment, with no fallbacks. They
    // used to be hardcoded here, which published the upload keystore password
    // in a public repository.
    //
    // The config is only created when the credentials exist. Failing eagerly
    // instead breaks `assembleDebug` too, because Gradle evaluates this block
    // whichever task you run — so a release-only problem stopped every build.
    val releaseStorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
    val releaseKeyPassword: String? = System.getenv("KEY_PASSWORD")
    val canSignRelease = releaseStorePassword != null && releaseKeyPassword != null

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file("audiotag-release.keystore")
                storePassword = releaseStorePassword
                keyAlias = System.getenv("KEY_ALIAS") ?: "audiotag"
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Unsigned when credentials are absent. Play rejects an unsigned
            // bundle with a clear message, which is a better failure than a
            // build that cannot be configured at all.
            signingConfig = if (canSignRelease) signingConfigs.getByName("release") else null
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
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    
    // Media & Audio
    implementation("androidx.media:media:1.7.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    
    // Accessibility testing
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.5.1")
    
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}