import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
    alias(libs.plugins.perf)
    alias(libs.plugins.kotlin.serialization)
}

val appVersionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0.0-dev"

val keystorePropsFile = rootProject.file("keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

val localProps = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}
val ghToken = System.getenv("GH_API_TOKEN") ?: localProps.getProperty("github.api.token") ?: ""
val ghRepoOwner = System.getenv("GH_REPO_OWNER") ?: localProps.getProperty("github.repo.owner") ?: ""
val ghRepoName = System.getenv("GH_REPO_NAME") ?: localProps.getProperty("github.repo.name") ?: ""

fun signingValue(key: String): String? =
    System.getenv(key) ?: keystoreProps.getProperty(key)

android {
    namespace = "com.chartmann.knightfall"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chartmann.knightfall"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        ndk { abiFilters += "arm64-v8a" }

        buildConfigField("String", "GITHUB_API_TOKEN", "\"$ghToken\"")
        buildConfigField("String", "GITHUB_REPO_OWNER", "\"$ghRepoOwner\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"$ghRepoName\"")
        buildConfigField("String", "FEEDBACK_ASSETS_DIR", "\"feedback-assets\"")
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("KNIGHTFALL_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingValue("KNIGHTFALL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("KNIGHTFALL_KEY_ALIAS")
                keyPassword = signingValue("KNIGHTFALL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingValue("KNIGHTFALL_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    packaging {
        // Stockfish ships as libstockfish.so and is exec'd from nativeLibraryDir,
        // which requires the library to be extracted on disk.
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    bundle {
        // Keep all ABIs/screen splits default; only arm64 ships anyway.
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// The Stockfish binary exceeds GitHub's 100MB file limit, so it is not
// committed. This task fetches the official android-armv8 build on machines
// (like CI) that don't have it yet.
val stockfishSo = file("src/main/jniLibs/arm64-v8a/libstockfish.so")
val downloadStockfish by tasks.registering {
    outputs.file(stockfishSo)
    onlyIf { !stockfishSo.exists() }
    doLast {
        val url = "https://github.com/official-stockfish/Stockfish/releases/download/sf_18/stockfish-android-armv8.tar"
        val tarFile = File(temporaryDir, "stockfish.tar")
        stockfishSo.parentFile.mkdirs()
        ant.invokeMethod("get", mapOf("src" to url, "dest" to tarFile, "skipexisting" to "true"))
        copy {
            from(tarTree(tarFile)) {
                include("stockfish/stockfish-android-armv8")
                eachFile { path = "libstockfish.so" }
                includeEmptyDirs = false
            }
            into(stockfishSo.parentFile)
        }
        if (!stockfishSo.exists()) {
            throw GradleException("Stockfish download/extract failed")
        }
    }
}

tasks.named("preBuild") { dependsOn(downloadStockfish) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.config)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.litertlm)
    implementation(libs.chesslib)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
