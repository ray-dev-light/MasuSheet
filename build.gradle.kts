// [EN] Top-level Gradle build configuration for MasuSheet Android application.
// [JA] MasuSheet Android アプリケーションのルートビルド定義です。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// [EN] Redirect intermediate build outputs to local ext4 cache when hosted on shared storage.
// [JA] 共有ストレージ上でビルドする際、中間出力を高速なローカルキャッシュ領域へ自動リダイレクトします。
if (rootDir.absolutePath.startsWith("/mnt/shared") || rootDir.absolutePath.startsWith("/storage/emulated")) {
    allprojects {
        layout.buildDirectory.set(
            file("${System.getProperty("user.home")}/.cache/projects/MasuSheet-build/${project.name}")
        )
    }
}
