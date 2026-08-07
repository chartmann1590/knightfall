import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
    alias(libs.plugins.perf)
    alias(libs.plugins.kotlin.serialization)
}

val appVersionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull()
    ?: (project.findProperty("versionCode") as String?)?.toInt()
    ?: 1
val appVersionName = System.getenv("ANDROID_VERSION_NAME")
    ?: (project.findProperty("versionName") as String?)
    ?: "1.0.0-dev"

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

val admobAppId = System.getenv("ADMOB_APP_ID") ?: localProps.getProperty("admob.app.id") ?: ""
val admobBannerAdId = System.getenv("ADMOB_BANNER_AD_ID") ?: localProps.getProperty("admob.banner.ad.id") ?: ""
val admobInterstitialAdId = System.getenv("ADMOB_INTERSTITIAL_AD_ID") ?: localProps.getProperty("admob.interstitial.ad.id") ?: ""
val googleClientId = System.getenv("GOOGLE_CLIENT_ID") ?: localProps.getProperty("google.client.id") ?: ""
val googleServicesJson = System.getenv("GOOGLE_SERVICES_JSON") ?: localProps.getProperty("google.services.json") ?: ""
val googleServicesJsonBase64 = System.getenv("GOOGLE_SERVICES_JSON_BASE64") ?: localProps.getProperty("google.services.json.base64") ?: ""

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

        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_AD_ID", "\"$admobBannerAdId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_ID", "\"$admobInterstitialAdId\"")
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
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
// committed. This task fetches a prebuilt android-armv8 build on machines
// (like CI) that don't have it yet.
//
// This is NOT the stock official-stockfish/Stockfish sf_18 release binary:
// that build's PT_LOAD ELF segments are only 4KB-aligned, which trips
// Android's 16KB page size compatibility warning (PageSizeMismatchDialog)
// on 16KB-page devices. This asset is the same sf_18 source relinked with
// NDK r27c and -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 so
// all LOAD segments are 16KB-aligned; otherwise identical (static, non-PIE).
val stockfishSo = file("src/main/jniLibs/arm64-v8a/libstockfish.so")
val downloadStockfish by tasks.registering {
    outputs.file(stockfishSo)
    onlyIf { !stockfishSo.exists() }
    doLast {
        val url = "https://github.com/chartmann1590/knightfall/releases/download/stockfish-android-16kb-v1/stockfish_static"
        stockfishSo.parentFile.mkdirs()
        ant.invokeMethod("get", mapOf("src" to url, "dest" to stockfishSo, "skipexisting" to "true"))
        if (!stockfishSo.exists()) {
            throw GradleException("Stockfish download failed")
        }
    }
}

tasks.named("preBuild") { dependsOn(downloadStockfish) }

val googleServicesFile = layout.projectDirectory.file("google-services.json")
val generateGoogleServices by tasks.registering {
    outputs.file(googleServicesFile)
    onlyIf { !googleServicesFile.asFile.exists() }
    doLast {
        val json = when {
            googleServicesJson.isNotBlank() -> googleServicesJson
            googleServicesJsonBase64.isNotBlank() -> String(
                Base64.getDecoder().decode(googleServicesJsonBase64),
                Charsets.UTF_8
            )
            else -> throw GradleException(
                "app/google-services.json is missing. Provide GOOGLE_SERVICES_JSON, " +
                    "GOOGLE_SERVICES_JSON_BASE64, or local.properties google.services.json."
            )
        }
        googleServicesFile.asFile.writeText(json)
    }
}

tasks.matching { it.name.startsWith("process") && it.name.endsWith("GoogleServices") }.configureEach {
    dependsOn(generateGoogleServices)
}

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
    implementation("com.google.android.play:review-ktx:2.0.2")
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
    implementation(libs.google.play.services.ads)

    implementation(libs.litertlm)
    implementation(libs.chesslib)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
