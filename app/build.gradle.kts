import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * [EN] Reads optional local release-signing environment variables without hardcoding secrets in Git.
 * [JA] Git 管理下に秘密情報を含めず、環境変数またはローカル設定ファイルからリリース署名設定を取得します。
 */
fun loadLocalSigningEnv(): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val localEnvFile = File(System.getProperty("user.home"), ".config/masusheet/keystore.env")
    if (localEnvFile.isFile) {
        localEnvFile.readLines().forEach { rawLine ->
            val trimmed = rawLine.trim().removePrefix("export ").trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && '=' in trimmed) {
                val idx = trimmed.indexOf('=')
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                result[key] = value
            }
        }
    }
    return result
}

val localSigningEnv = loadLocalSigningEnv()
fun resolveSigningVar(primary: String, fallback: String? = null): String? {
    return providers.environmentVariable(primary).orNull
        ?: (fallback?.let { providers.environmentVariable(it).orNull })
        ?: localSigningEnv[primary]
        ?: (fallback?.let { localSigningEnv[it] })
}

val releaseKeystorePath = resolveSigningVar("MASUSHEET_KEYSTORE_FILE", "KEYSTORE_FILE")
val releaseStorePassword = resolveSigningVar("MASUSHEET_KEYSTORE_PASSWORD", "KEYSTORE_PASSWORD")
val releaseKeyAlias = resolveSigningVar("MASUSHEET_KEY_ALIAS", "KEY_ALIAS")
val releaseKeyPassword = resolveSigningVar("MASUSHEET_KEY_PASSWORD", "KEY_PASSWORD")
val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    File(releaseKeystorePath).isFile

android {
    namespace = "com.masusheet.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.masusheet.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    base {
        archivesName.set("MasuSheet")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // [EN] Uses standard Android debug keystore (never shares production signing key).
            // [JA] 標準の Android デバッグ署名鍵を使用します（本番署名鍵とは分離）。
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { testTask ->
                val userHome = File(System.getProperty("user.home"))
                val cachedCargoDirs = File(userHome, ".cache/projects")
                    .listFiles()
                    ?.map { File(it, "cargo/release") }
                    ?.filter { it.isDirectory }
                    ?.map { it.absolutePath }
                    .orEmpty()
                val envCargoTarget = System.getenv("CARGO_TARGET_DIR")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { listOf(File(it, "release").absolutePath) }
                    .orEmpty()
                val jniPaths = (
                    envCargoTarget +
                        cachedCargoDirs +
                        listOf(
                            "${rootProject.projectDir}/crates/jni/target/release",
                            "${rootProject.projectDir}/target/release"
                        )
                    ).distinct().joinToString(File.pathSeparator)
                testTask.systemProperty("java.library.path", jniPaths)
            }
        }
    }

    androidResources {
        noCompress += listOf("wasm", "xlsx", "csv", "tsv", "ods")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * [EN] Prevents building a Release APK when prebuilt Rust JNI libraries (`libmasusheet_jni.so`) are missing.
 * [JA] ネイティブ計算エンジン (`libmasusheet_jni.so`) が未ビルドのまま Release APK が生成されるのを防止します。
 */
val arm64JniLibFile = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libmasusheet_jni.so").asFile
val verifyReleaseJniLibs by tasks.registering {
    val targetFile = arm64JniLibFile
    doLast {
        if (!targetFile.isFile || targetFile.length() == 0L) {
            throw GradleException(
                "Missing native library ${targetFile.path}. Run `cargo ndk -t arm64-v8a -t x86_64 -o app/src/main/jniLibs build -p masusheet-jni --release` before assembleRelease."
            )
        }
    }
}

tasks.matching { it.name == "mergeReleaseJniLibFolders" }.configureEach {
    dependsOn(verifyReleaseJniLibs)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
}
