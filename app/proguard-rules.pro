# [EN] Keep JNI entry points on MasuSheetNativeBridge intact under R8 shrinking/obfuscation
#      while anonymizing raw source file names in release stack traces.
# [JA] R8 難読化・圧縮時に Rust JNI ブリッジのメソッド署名が変更・削除されないよう保護し、
#      リリース版スタックトレース内の生ファイル名を匿名化します。
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keep class com.masusheet.app.bridge.MasuSheetNativeBridge {
    *;
}
