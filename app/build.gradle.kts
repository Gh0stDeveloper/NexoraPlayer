
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.nexora.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexora.player"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "2.0.2"
        val nexoraOnlineApiBaseUrl = System.getenv("NEXORA_ONLINE_API_BASE_URL") ?: "https://nexoraplayerapi.vercel.app/api/v1"
        val nexoraSupabaseUrl = System.getenv("NEXORA_SUPABASE_URL") ?: "https://rbzkczwifeqkzcoqwyjq.supabase.co"
        val nexoraSupabaseAnonKey = System.getenv("NEXORA_SUPABASE_ANON_KEY") ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJiemtjendpZmVxa3pjb3F3eWpxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODI3MTY2NDQsImV4cCI6MjA5ODI5MjY0NH0.JYfLzRf25MMcj_MFqB5M1rIU0YeDGRQZtW8yKwWY2V8"
        val nexoraSupabaseGoogleRedirectUrl = System.getenv("NEXORA_SUPABASE_GOOGLE_REDIRECT_URL") ?: "nexoraplayer://auth/callback"
        val nexoraApiAppId = System.getenv("NEXORA_API_APP_ID") ?: "nexora-player-ghost"
        val nexoraApiAppSharedSecret = System.getenv("NEXORA_API_APP_SHARED_SECRET") ?: "9f4f1e1ed32f830f7a9b18e1b7c0c98fd4d9281a97ff7cbf705c7a40c7ec53cba199b6f541d71856d9a2c2ea33192f902b1633e2de8e6a6b87e70ef5a4d66"

        buildConfigField("String", "NEXORA_SERVER_URL", "\"https://nexoraplayer.vercel.app\"")
        buildConfigField("String", "NEXORA_ONLINE_API_BASE_URL", nexoraOnlineApiBaseUrl.asBuildConfigString())
        buildConfigField("String", "NEXORA_SUPABASE_URL", nexoraSupabaseUrl.asBuildConfigString())
        buildConfigField("String", "NEXORA_SUPABASE_ANON_KEY", nexoraSupabaseAnonKey.asBuildConfigString())
        buildConfigField("String", "NEXORA_SUPABASE_GOOGLE_REDIRECT_URL", nexoraSupabaseGoogleRedirectUrl.asBuildConfigString())
        buildConfigField("String", "NEXORA_API_APP_ID", nexoraApiAppId.asBuildConfigString())
        buildConfigField("String", "NEXORA_API_APP_SHARED_SECRET", nexoraApiAppSharedSecret.asBuildConfigString())
    }

    val releaseKeystorePath = System.getenv("KEYSTORE_FILE") ?: "app/ghostnexora-release.jks"

    signingConfigs {
        create("release") {
            storeFile = file(releaseKeystorePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.4")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
