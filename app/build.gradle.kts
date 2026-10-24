import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.chaquopy)
}

// Ключ подписи релизов — вне репозитория: ~/.android/bayun-release.properties (storeFile, storePassword,
// keyAlias, keyPassword). Нет файла — release собирается неподписанным.
val releaseKey = Properties().apply {
    val f = File(System.getProperty("user.home"), ".android/bayun-release.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.animeliodas.bayun"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.animeliodas.bayun"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            // телефоны (arm64) и эмулятор на ПК (x86_64)
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (!releaseKey.isEmpty) create("release") {
            storeFile = file(releaseKey.getProperty("storeFile"))
            storePassword = releaseKey.getProperty("storePassword")
            keyAlias = releaseKey.getProperty("keyAlias")
            keyPassword = releaseKey.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

chaquopy {
    defaultConfig {
        version = "3.12"
        pip {
            // Polytome — движок перевода (Python). Пока он не опубликован на PyPI — локальная сборка:
            // tools/update-polytome.cmd собирает wheel из соседней папки с исходниками Polytome.
            install(file("wheels").listFiles { f -> f.name.endsWith(".whl") }!!.maxBy { it.name }.absolutePath)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
