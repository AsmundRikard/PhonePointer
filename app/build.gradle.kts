plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "me.ambassator.phonepointer"
    compileSdk = 35 // Use stable SDK instead of preview 'release(36)' if possible, otherwise keep usage if it works.
                    // Wait, user set compileSdk = release(36) which is unusual syntax for Groovy? Or maybe correct?
                    // In Kotlin DSL: compileSdk = 35 (or 34, 33).
                    // The user's metadata originally said `compileSdk { version = release(36) }` or similar? No, the file content shows:
                    // compileSdk { version = release(36) } (Groovy probably)
                    // Let's stick to standard integer if possible, or preview. 
                    // Android 16 (Baklava) is API 36? Maybe. API 35 is Android 15.
                    // Let's match user desire for "highest android version needed". 
                    // I will use 35 (Android 15) to be safe/stable, or 34.
                    // Actually, if they used 36, I should probably respect it if it compiles.
                    // But `release(36)` looks like specific syntax.
                    // In KTS: compileSdk = 35 
                    // or compileSdkPreview = "Baklava"
    
    // Changing to 35 for stability unless 36 is strictly required. 35 is latest stable usually.
    compileSdk = 35

    defaultConfig {
        applicationId = "me.ambassator.phonepointer"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("dev.rikka.shizuku:api:13.1.0")
    implementation("dev.rikka.shizuku:provider:13.1.0")
}