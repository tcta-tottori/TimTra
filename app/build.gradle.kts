plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.kazuya.timtra"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kazuya.timtra"
        minSdk = 26
        // targetSdk は AGP 9 の既定で compileSdk に揃う
        // CI では GITHUB_RUN_NUMBER をビルド番号にする（このアプリについて画面で見分ける）
        val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = ciRun
        versionName = "0.1.$ciRun"
    }

    // CI で毎回鍵が変わると上書きインストールできないため、リポジトリの固定鍵で署名する（keystore/README.md）
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":data"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // 通知: 前夜の WorkManager ジョブ + AlarmManager（CLAUDE.md 8）
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ホーム画面ウィジェット（Glance）
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // ホームの地図タイル（OpenStreetMap）の取得
    implementation(libs.okhttp)

    // Wear への設定同期（Wearable Data Layer）
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
}
