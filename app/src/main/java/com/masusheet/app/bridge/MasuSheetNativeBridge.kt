package com.masusheet.app.bridge

/**
 * [EN] JNI singleton binding to `libmasusheet_jni.so` with isolated multi-session support.
 * Each open workbook tab or `:window2` process can allocate its own `sessionId` via
 * [nativeCreateSession] and release it via [nativeCloseSession], preventing cross-workbook state clobbering.
 *
 * [JA] マルチセッション分離に対応した `libmasusheet_jni.so` への JNI ブリッジです。
 * 各ブックタブや別ウィンドウ（`:window2`）ごとに独立した `sessionId` を保持でき、状態の競合や上書きを防止します。
 */
object MasuSheetNativeBridge {
    val isNativeLoaded: Boolean = try {
        System.loadLibrary("masusheet_jni")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun nativeCreateSession(sample: String): Long

    @JvmStatic
    external fun nativeCloseSession(sessionId: Long)

    @JvmStatic
    external fun nativeInitSession(sessionId: Long, sample: String): String

    @JvmStatic
    external fun nativeOpenWorkbookBytesSession(
        sessionId: Long,
        fileName: String,
        bytes: ByteArray
    ): String

    @JvmStatic
    external fun nativeSaveWorkbookBytesSession(sessionId: Long, fileName: String): ByteArray?

    @JvmStatic
    external fun nativeExportPdfBytesSession(sessionId: Long): ByteArray?

    @JvmStatic
    external fun nativeExecuteCommandSession(
        sessionId: Long,
        commandId: String,
        paramsJson: String
    ): String

    @JvmStatic
    external fun nativeGetViewportJsonSession(sessionId: Long, maxRow: Int, maxCol: Int): String

    // [EN] Default session (`0L`) convenience wrappers for single-session callers.
    // [JA] デフォルトセッション (`0L`) 用の簡易ラッパー関数群です。
    fun nativeInit(sample: String): String = nativeInitSession(0L, sample)

    fun nativeOpenWorkbookBytes(fileName: String, bytes: ByteArray): String =
        nativeOpenWorkbookBytesSession(0L, fileName, bytes)

    fun nativeSaveWorkbookBytes(fileName: String): ByteArray? =
        nativeSaveWorkbookBytesSession(0L, fileName)

    fun nativeExportPdfBytes(): ByteArray? = nativeExportPdfBytesSession(0L)

    fun nativeExecuteCommand(commandId: String, paramsJson: String): String =
        nativeExecuteCommandSession(0L, commandId, paramsJson)

    fun nativeGetViewportJson(maxRow: Int, maxCol: Int): String =
        nativeGetViewportJsonSession(0L, maxRow, maxCol)
}
