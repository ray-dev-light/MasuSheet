package com.masusheet.app.bridge

import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.ChartSeries
import com.masusheet.app.model.NativeCellModel
import com.masusheet.app.model.NativeSheetObject
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.model.SheetObjectKind
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

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
    fun syncWorkbookObjects(objects: List<NativeSheetObject>) {}
    fun readWorkbookObjects(): List<NativeSheetObject> = emptyList()
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
            val safeMaxRows = maxRows.coerceIn(1, 50_000)
            val safeMaxCols = maxCols.coerceIn(1, 1_024)
            val params = JSONObject()
                .put("sheetIndex", sheetIndex)
                .put("sheet", sheetIndex)
                .put("maxRows", safeMaxRows)
                .put("maxCols", safeMaxCols)
                .toString()
            val raw = if (currentSessionId > 0L) {
                MasuSheetNativeBridge.nativeExecuteCommandSession(currentSessionId, "jni.readSheetCells", params)
            } else {
                MasuSheetNativeBridge.nativeExecuteCommand("jni.readSheetCells", params)
            }
            val snapObj = JSONObject(raw)
            val resObj = snapObj.optJSONObject("result")
                ?: snapObj.optString("resultJson", "").takeIf { it.isNotBlank() }?.let { JSONObject(it) }
                ?: return emptyMap()
            val arr = resObj.optJSONArray("cells") ?: return emptyMap()
            val out = HashMap<Long, NativeCellModel>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    val row = obj.optInt("r", 0)
                    val col = obj.optInt("c", 0)
                    val disp = obj.optString("t", "")
                    val edit = obj.optString("e", "").ifEmpty { disp }
                    out[NativeViewportSnapshot.packCellKey(row, col)] = NativeCellModel(
                        row = row,
                        col = col,
                        displayText = disp,
                        editText = edit,
                        bgColor = obj.optInt("bg", 0),
                        fgColor = obj.optInt("fg", 0),
                        fontSize = MasuSheetConfig.parseFiniteFloatOrNull(obj.optDouble("fs", 11.0).toString()) ?: 11f,
                        flags = obj.optInt("fl", 0),
                        borderTop = obj.optInt("bt", 0),
                        borderRight = obj.optInt("br", 0),
                        borderBottom = obj.optInt("bb", 0),
                        borderLeft = obj.optInt("bl", 0)
                    )
                    continue
                }
                val c = arr.optJSONArray(i) ?: continue
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

    override fun syncWorkbookObjects(objects: List<NativeSheetObject>) {
        if (!MasuSheetNativeBridge.isNativeLoaded) return
        runCatching {
            val arr = JSONArray()
            for (obj in objects) {
                val o = JSONObject()
                    .put("id", obj.id)
                    .put("sheetIndex", obj.sheetIndex)
                    .put("kind", obj.kind.name)
                    .put("subType", obj.subType)
                    .put("xPt", obj.xPt.toDouble())
                    .put("yPt", obj.yPt.toDouble())
                    .put("widthPt", obj.widthPt.toDouble())
                    .put("heightPt", obj.heightPt.toDouble())
                    .put("title", obj.title)
                    .put("text", obj.text)
                    .put("fillColor", obj.fillColor)
                    .put("strokeColor", obj.strokeColor)
                    .put("strokeWidthPt", obj.strokeWidthPt.toDouble())
                    .put("sourceRangeText", obj.sourceRangeText)
                    .put("chartLabels", JSONArray(obj.chartLabels))
                    .put("chartValues", JSONArray(obj.chartValues.map { it.toDouble() }))
                if (obj.imageBytes != null && obj.imageBytes.isNotEmpty()) {
                    o.put("imageBase64", Base64.getEncoder().encodeToString(obj.imageBytes))
                }
                val seriesArr = JSONArray()
                for (s in obj.chartSeries) {
                    seriesArr.put(
                        JSONObject()
                            .put("name", s.name)
                            .put("values", JSONArray(s.values.map { it.toDouble() }))
                            .put("color", s.color)
                            .put("nameCellRef", s.nameCellRef)
                            .put("xRangeText", s.xRangeText)
                            .put("yRangeText", s.yRangeText)
                            .put("xLabels", JSONArray(s.xLabels))
                    )
                }
                o.put("chartSeries", seriesArr)
                arr.put(o)
            }
            val payload = JSONObject().put("objects", arr).toString()
            if (currentSessionId > 0L) {
                MasuSheetNativeBridge.nativeExecuteCommandSession(currentSessionId, "jni.syncWorkbookObjects", payload)
            } else {
                MasuSheetNativeBridge.nativeExecuteCommand("jni.syncWorkbookObjects", payload)
            }
        }
    }

    override fun readWorkbookObjects(): List<NativeSheetObject> {
        if (!MasuSheetNativeBridge.isNativeLoaded) return emptyList()
        return runCatching {
            val raw = if (currentSessionId > 0L) {
                MasuSheetNativeBridge.nativeExecuteCommandSession(currentSessionId, "jni.readWorkbookObjects", "{}")
            } else {
                MasuSheetNativeBridge.nativeExecuteCommand("jni.readWorkbookObjects", "{}")
            }
            val snapObj = JSONObject(raw)
            val resObj = snapObj.optJSONObject("result")
                ?: snapObj.optString("resultJson", "").takeIf { it.isNotBlank() }?.let { JSONObject(it) }
                ?: return emptyList()
            val arr = resObj.optJSONArray("objects") ?: return emptyList()
            val out = ArrayList<NativeSheetObject>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = runCatching { SheetObjectKind.valueOf(o.optString("kind", "SHAPE")) }
                    .getOrDefault(SheetObjectKind.SHAPE)
                val labelsArr = o.optJSONArray("chartLabels")
                val labels = if (labelsArr != null) {
                    List(labelsArr.length()) { idx -> labelsArr.optString(idx, "") }
                } else {
                    emptyList()
                }
                val valsArr = o.optJSONArray("chartValues")
                val vals = if (valsArr != null) {
                    List(valsArr.length()) { idx -> valsArr.optDouble(idx, 0.0).toFloat() }
                } else {
                    emptyList()
                }
                val seriesJsonArr = o.optJSONArray("chartSeries")
                val seriesList = if (seriesJsonArr != null) {
                    buildList {
                        for (si in 0 until seriesJsonArr.length()) {
                            val sj = seriesJsonArr.optJSONObject(si) ?: continue
                            val svArr = sj.optJSONArray("values")
                            val sv = if (svArr != null) {
                                List(svArr.length()) { k -> svArr.optDouble(k, 0.0).toFloat() }
                            } else {
                                emptyList()
                            }
                            val sxArr = sj.optJSONArray("xLabels")
                            val sx = if (sxArr != null) {
                                List(sxArr.length()) { k -> sxArr.optString(k, "") }
                            } else {
                                emptyList()
                            }
                            add(
                                ChartSeries(
                                    name = sj.optString("name", "Series ${si + 1}"),
                                    values = sv,
                                    color = sj.optInt("color", 0),
                                    nameCellRef = sj.optString("nameCellRef", ""),
                                    xRangeText = sj.optString("xRangeText", ""),
                                    yRangeText = sj.optString("yRangeText", ""),
                                    xLabels = sx
                                )
                            )
                        }
                    }
                } else {
                    emptyList()
                }
                val imgB64 = o.optString("imageBase64", "")
                val imgBytes = if (imgB64.isNotBlank()) {
                    runCatching { Base64.getDecoder().decode(imgB64) }.getOrNull()
                } else {
                    null
                }
                out.add(
                    NativeSheetObject(
                        id = o.optString("id", "ooxml_obj_$i"),
                        sheetIndex = o.optInt("sheetIndex", 0),
                        kind = kind,
                        subType = o.optString("subType", "column"),
                        xPt = o.optDouble("xPt", 80.0).toFloat(),
                        yPt = o.optDouble("yPt", 60.0).toFloat(),
                        widthPt = o.optDouble("widthPt", 240.0).toFloat(),
                        heightPt = o.optDouble("heightPt", 160.0).toFloat(),
                        title = o.optString("title", ""),
                        text = o.optString("text", ""),
                        fillColor = o.optInt("fillColor", 0xFF1F9D55.toInt()),
                        strokeColor = o.optInt("strokeColor", 0xFF163828.toInt()),
                        strokeWidthPt = o.optDouble("strokeWidthPt", 1.5).toFloat(),
                        imageBytes = imgBytes,
                        chartLabels = labels,
                        chartValues = vals,
                        chartSeries = seriesList,
                        sourceRangeText = o.optString("sourceRangeText", "")
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }
}
