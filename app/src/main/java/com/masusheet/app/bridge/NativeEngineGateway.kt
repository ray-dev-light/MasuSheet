package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] Native Spreadsheet Engine Gateway & Multi-Session Rust JNI Adapter
 * Defines [NativeEngineGateway] and [RustJniEngineGateway], connecting each workbook session cleanly to an isolated Rust calculation engine instance via `libmasusheet_jni.so`.
 *
 * ---
 * [JA / 日本語解説]
 * ネイティブ計算エンジンの抽象ゲートウェイ ([NativeEngineGateway]) と、マルチセッション対応Rust JNIブリッジ (`libmasusheet_jni.so`) アダプタ ([RustJniEngineGateway]) を定義します。
 * ============================================================================
 */

/**
 * [EN] Abstraction over the spreadsheet calculation engine for workbook lifecycle, viewport queries, and command execution.
 * [JA] ワークブックの読込・保存・ビューポート取得・コマンド実行を抽象化する計算エンジンゲートウェイです。
 */
interface NativeEngineGateway {
    val activeSessionId: Long
        get() = 0L

    fun createSession(sample: String = "blank"): Long = 0L
    fun switchSession(sessionId: Long) {}
    fun closeSession(sessionId: Long) {}

    fun initSample(sample: String): NativeViewportSnapshot
    fun openWorkbookBytes(fileName: String, bytes: ByteArray): NativeViewportSnapshot
    fun saveWorkbookBytes(fileName: String): ByteArray?
    fun exportPdfBytes(): ByteArray?
    fun executeCommand(commandId: String, paramsJson: String = "{}"): NativeViewportSnapshot
    fun getViewport(maxRow: Int = 120, maxCol: Int = 45): NativeViewportSnapshot
    fun readSheetCells(sheetIndex: Int, maxRows: Int = 5000, maxCols: Int = 256): Map<Long, NativeCellModel> = emptyMap()
}

/**
 * [EN] Production [NativeEngineGateway] backed by an isolated Rust workbook session in `libmasusheet_jni.so`.
 * [JA] Rust製ネイティブライブラリ (`libmasusheet_jni.so`) の独立セッションと通信する本番用ゲートウェイ実装です。
 */
class RustJniEngineGateway : NativeEngineGateway {
    private val fallback = InMemoryFallbackEngineGateway()
    private var currentSessionId: Long = 0L

    override val activeSessionId: Long
        get() = if (MasuSheetNativeBridge.isNativeLoaded) currentSessionId else fallback.activeSessionId

    override fun createSession(sample: String): Long {
        return if (MasuSheetNativeBridge.isNativeLoaded) {
            val sid = MasuSheetNativeBridge.nativeCreateSession(sample)
            if (sid > 0L) {
                currentSessionId = sid
            }
            sid
        } else {
            fallback.createSession(sample)
        }
    }

    override fun switchSession(sessionId: Long) {
        if (MasuSheetNativeBridge.isNativeLoaded) {
            currentSessionId = sessionId.coerceAtLeast(0L)
        } else {
            fallback.switchSession(sessionId)
        }
    }

    override fun closeSession(sessionId: Long) {
        if (MasuSheetNativeBridge.isNativeLoaded) {
            if (sessionId > 0L) {
                MasuSheetNativeBridge.nativeCloseSession(sessionId)
            }
            if (currentSessionId == sessionId) {
                currentSessionId = 0L
            }
        } else {
            fallback.closeSession(sessionId)
        }
    }

    override fun initSample(sample: String): NativeViewportSnapshot {
        return if (MasuSheetNativeBridge.isNativeLoaded) {
            val json = if (currentSessionId > 0L) {
                MasuSheetNativeBridge.nativeInitSession(currentSessionId, sample)
            } else {
                MasuSheetNativeBridge.nativeInit(sample)
            }
            NativeViewportSnapshot.fromJsonOrFallback(json)
        } else {
            fallback.initSample(sample)
        }
    }

    override fun openWorkbookBytes(fileName: String, bytes: ByteArray): NativeViewportSnapshot {
        return if (MasuSheetNativeBridge.isNativeLoaded) {
            val json = if (currentSessionId > 0L) {
                MasuSheetNativeBridge.nativeOpenWorkbookBytesSession(currentSessionId, fileName, bytes)
            } else {
                MasuSheetNativeBridge.nativeOpenWorkbookBytes(fileName, bytes)
            }
            NativeViewportSnapshot.fromJsonOrFallback(json)
        } else {
            fallback.openWorkbookBytes(fileName, bytes)
        }
    }

    override fun saveWorkbookBytes(fileName: String): ByteArray? {
        return if (MasuSheetNativeBridge.isNativeLoaded) {
            if (currentSessionId > 0L) {
                MasuSheetNativeBridge.nativeSaveWorkbookBytesSession(currentSessionId, fileName)
            } else {
                MasuSheetNativeBridge.nativeSaveWorkbookBytes(fileName)
            }
        } else {
            fallback.saveWorkbookBytes(fileName)
        }
    }

    override fun exportPdfBytes(): ByteArray? {
        return if (MasuSheetNativeBridge.isNativeLoaded) {
            if (currentSessionId > 0L) {
                MasuSheetNativeBridge.nativeExportPdfBytesSession(currentSessionId)
            } else {
                MasuSheetNativeBridge.nativeExportPdfBytes()
            }
        } else {
            fallback.exportPdfBytes()
        }
    }

    override fun executeCommand(commandId: String, paramsJson: String): NativeViewportSnapshot {
        return if (MasuSheetNativeBridge.isNativeLoaded) {
            val json = if (currentSessionId > 0L) {
                MasuSheetNativeBridge.nativeExecuteCommandSession(currentSessionId, commandId, paramsJson)
            } else {
                MasuSheetNativeBridge.nativeExecuteCommand(commandId, paramsJson)
            }
            NativeViewportSnapshot.fromJsonOrFallback(json)
        } else {
            fallback.executeCommand(commandId, paramsJson)
        }
    }

    override fun getViewport(maxRow: Int, maxCol: Int): NativeViewportSnapshot {
        return if (MasuSheetNativeBridge.isNativeLoaded) {
            val json = if (currentSessionId > 0L) {
                MasuSheetNativeBridge.nativeGetViewportJsonSession(currentSessionId, maxRow, maxCol)
            } else {
                MasuSheetNativeBridge.nativeGetViewportJson(maxRow, maxCol)
            }
            NativeViewportSnapshot.fromJsonOrFallback(json)
        } else {
            fallback.getViewport(maxRow, maxCol)
        }
    }

    override fun readSheetCells(sheetIndex: Int, maxRows: Int, maxCols: Int): Map<Long, NativeCellModel> {
        if (!MasuSheetNativeBridge.isNativeLoaded) {
            return fallback.readSheetCells(sheetIndex, maxRows, maxCols)
        }
        return runCatching {
            val params = JSONObject()
                .put("sheet", sheetIndex)
                .put("maxRows", maxRows)
                .put("maxCols", maxCols)
                .toString()
            val raw = if (currentSessionId > 0L) {
                MasuSheetNativeBridge.nativeExecuteCommandSession(currentSessionId, "jni.readSheetCells", params)
            } else {
                MasuSheetNativeBridge.nativeExecuteCommand("jni.readSheetCells", params)
            }
            val snapObj = JSONObject(raw)
            val resJson = snapObj.optString("resultJson", "")
            if (resJson.isBlank()) return emptyMap()
            val resObj = JSONObject(resJson)
            val arr = resObj.optJSONArray("cells") ?: return emptyMap()
            val out = HashMap<Long, NativeCellModel>(arr.length())
            for (i in 0 until arr.length()) {
                val c = arr.getJSONArray(i)
                val row = c.optInt(0, 0)
                val col = c.optInt(1, 0)
                out[NativeViewportSnapshot.packCellKey(row, col)] = NativeCellModel(
                    row = row,
                    col = col,
                    displayText = c.optString(2, ""),
                    editText = c.optString(3, ""),
                    bgColor = c.optInt(4, 0),
                    fgColor = c.optInt(5, 0),
                    fontSize = MasuSheetConfig.parseFiniteFloatOrNull(c.optDouble(6, 11.0).toString()) ?: 11f,
                    flags = c.optInt(7, 0),
                    borderTop = c.optInt(8, 0),
                    borderRight = c.optInt(9, 0),
                    borderBottom = c.optInt(10, 0),
                    borderLeft = c.optInt(11, 0)
                )
            }
            out
        }.getOrDefault(emptyMap())
    }
}
