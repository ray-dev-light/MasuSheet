# [EN] Keep JNI entry points on MasuSheetNativeBridge intact under R8 shrinking/obfuscation.
# [JA] R8 難読化・圧縮時に Rust JNI ブリッジのメソッド署名が変更・削除されないよう保護します。
-keepattributes SourceFile,LineNumberTable
-keep class com.masusheet.app.bridge.MasuSheetNativeBridge {
    *;
}
