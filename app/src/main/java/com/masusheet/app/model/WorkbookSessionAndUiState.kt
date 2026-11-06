package com.masusheet.app.model

import com.masusheet.app.ai.AiChatMessage
import com.masusheet.app.ai.AiPlanSession
import com.masusheet.app.ai.MacroDefinition
import com.masusheet.app.ai.MacroStep
import com.masusheet.app.ai.MacroStudioEngine
import com.masusheet.app.ai.VbaDebugState
import com.masusheet.app.core.MasuSheetConfig
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Multi-Workbook Session State, Window Handoff Serializer, & Immutable UI State
 * Defines [OpenWorkbookSession], [WorkbookHandoffSerializer] (preserving all sheets, objects, pivots, styles, and tab colors across windows), and [MasuSheetUiState].
 *
 * ---
 * [JA / 日本語解説]
 * 複数ブックセッション ([OpenWorkbookSession])、別ウィンドウ移動時の完全状態シリアライザ ([WorkbookHandoffSerializer])、および画面全体のイミュータブルUI状態 ([MasuSheetUiState]) を定義します。
 * ============================================================================
 */

/**
 * [EN] Full state of an open workbook tab, isolating engine session ID, overlays, objects, pivots, and Undo/Redo history.
 * [JA] 開いている1ブックタブの完全な状態（エンジンセッションID・カスタム書式・オブジェクト・ピボット・保存先URI等）を保持します。
 */
@androidx.compose.runtime.Immutable
data class OpenWorkbookSession(
    val id: String,
    val title: String,
    val sampleKind: String,
    val nativeSessionId: Long = 0L,
    val sourceUri: String = "",
    val savedWorkbookBytes: ByteArray? = null,
    val activeSheetIndex: Int = 0,
    val sheetObjects: List<NativeSheetObject> = emptyList(),
    val customColWidths: Map<Pair<Int, Int>, Float> = emptyMap(),
    val customRowHeights: Map<Pair<Int, Int>, Float> = emptyMap(),
    val customWrapCells: Map<Pair<Int, Long>, Boolean> = emptyMap(),
    val customCellStyles: Map<Pair<Int, Long>, CustomCellStyleOverride> = emptyMap(),
    val customSheetMerges: Map<Int, List<NativeMergeRange>> = emptyMap(),
    val sheetTabColors: Map<Int, Int> = emptyMap(),
    val cellComments: Map<Pair<Int, Long>, String> = emptyMap(),
    val cellHyperlinks: Map<Pair<Int, Long>, String> = emptyMap(),
    val pivotTables: List<PivotTableDefinition> = emptyList(),
    val viewportSnapshot: NativeViewportSnapshot = NativeViewportSnapshot()
) {
    val viewport: NativeViewportSnapshot
        get() = viewportSnapshot

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenWorkbookSession) return false
        return id == other.id &&
            title == other.title &&
            sampleKind == other.sampleKind &&
            nativeSessionId == other.nativeSessionId &&
            sourceUri == other.sourceUri &&
            (savedWorkbookBytes contentEquals other.savedWorkbookBytes) &&
            activeSheetIndex == other.activeSheetIndex &&
            sheetObjects == other.sheetObjects &&
            customColWidths == other.customColWidths &&
            customRowHeights == other.customRowHeights &&
            customWrapCells == other.customWrapCells &&
            customCellStyles == other.customCellStyles &&
            customSheetMerges == other.customSheetMerges &&
            sheetTabColors == other.sheetTabColors &&
            cellComments == other.cellComments &&
            cellHyperlinks == other.cellHyperlinks &&
            pivotTables == other.pivotTables &&
            viewportSnapshot == other.viewportSnapshot
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + sampleKind.hashCode()
        result = 31 * result + nativeSessionId.hashCode()
        result = 31 * result + sourceUri.hashCode()
        result = 31 * result + (savedWorkbookBytes?.contentHashCode() ?: 0)
        result = 31 * result + activeSheetIndex
        result = 31 * result + sheetObjects.hashCode()
        result = 31 * result + customColWidths.hashCode()
        result = 31 * result + customRowHeights.hashCode()
        result = 31 * result + customWrapCells.hashCode()
        result = 31 * result + customCellStyles.hashCode()
        result = 31 * result + customSheetMerges.hashCode()
        result = 31 * result + sheetTabColors.hashCode()
        result = 31 * result + cellComments.hashCode()
        result = 31 * result + cellHyperlinks.hashCode()
        result = 31 * result + pivotTables.hashCode()
        result = 31 * result + viewportSnapshot.hashCode()
        return result
    }
}

/**
 * [EN] Lossless JSON bundle serializer for transferring an [OpenWorkbookSession] across
 * `:window2` process boundaries (`SecondaryWorkbookActivity` <-> `MainActivity`) without losing
 * charts, images, shapes, PivotTables, custom styles, column widths, or row heights.
 *
 * [JA] 別ウィンドウ（`:window2`）への移動およびメインウィンドウへの統合時に、グラフ・画像・図形・
 * ピボットテーブル・カスタム書式・列幅/行高を欠落なく引き継ぐためのシリアライザです。
 */
object WorkbookHandoffSerializer {
    fun encodeOverlayMetadataJson(session: OpenWorkbookSession): String {
        val root = JSONObject()
        root.put("title", session.title)
        root.put("sampleKind", session.sampleKind)
        root.put("activeSheetIndex", session.activeSheetIndex)
        if (session.sourceUri.isNotBlank()) {
            root.put("sourceUri", session.sourceUri)
        }

        val objsArr = JSONArray()
        for (obj in session.sheetObjects) {
            val o = JSONObject()
            o.put("id", obj.id)
            o.put("sheetIndex", obj.sheetIndex)
            o.put("kind", obj.kind.name)
            o.put("subType", obj.subType)
            o.put("xPt", obj.xPt.toDouble())
            o.put("yPt", obj.yPt.toDouble())
            o.put("widthPt", obj.widthPt.toDouble())
            o.put("heightPt", obj.heightPt.toDouble())
            o.put("title", obj.title)
            o.put("text", obj.text)
            o.put("fillColor", obj.fillColor)
            o.put("strokeColor", obj.strokeColor)
            o.put("strokeWidthPt", obj.strokeWidthPt.toDouble())
            o.put("textColor", obj.textColor)
            o.put("chartTitleTextColor", obj.chartTitleTextColor)
            o.put("chartXAxisTextColor", obj.chartXAxisTextColor)
            o.put("chartYAxisTextColor", obj.chartYAxisTextColor)
            o.put("chartLegendTextColor", obj.chartLegendTextColor)
            o.put("rotationDeg", obj.rotationDeg.toDouble())
            if (obj.imageBytes != null && obj.imageBytes.isNotEmpty()) {
                o.put("imageB64", Base64.getEncoder().encodeToString(obj.imageBytes))
            }
            o.put("chartLabels", JSONArray(obj.chartLabels))
            o.put("chartValues", JSONArray(obj.chartValues.map { it.toDouble() }))
            val seriesArr = JSONArray()
            for (s in obj.chartSeries) {
                val sJson = JSONObject()
                sJson.put("name", s.name)
                sJson.put("values", JSONArray(s.values.map { it.toDouble() }))
                sJson.put("color", s.color)
                sJson.put("nameCellRef", s.nameCellRef)
                sJson.put("xRangeText", s.xRangeText)
                sJson.put("yRangeText", s.yRangeText)
                sJson.put("xLabels", JSONArray(s.xLabels))
                seriesArr.put(sJson)
            }
            o.put("chartSeries", seriesArr)
            o.put("sourceRangeText", obj.sourceRangeText)
            o.put("chartXColIndex", obj.chartXColIndex)
            o.put("chartYColIndices", JSONArray(obj.chartYColIndices))
            o.put("xAxisLabelInterval", obj.xAxisLabelInterval)
            obj.xAxisMajorStep?.let { o.put("xAxisMajorStep", it.toDouble()) }
            obj.xAxisMin?.let { o.put("xAxisMin", it.toDouble()) }
            obj.xAxisMax?.let { o.put("xAxisMax", it.toDouble()) }
            obj.yAxisMajorStep?.let { o.put("yAxisMajorStep", it.toDouble()) }
            obj.yAxisMin?.let { o.put("yAxisMin", it.toDouble()) }
            obj.yAxisMax?.let { o.put("yAxisMax", it.toDouble()) }
            objsArr.put(o)
        }
        root.put("sheetObjects", objsArr)

        val cwArr = JSONArray()
        session.customColWidths.forEach { (k, v) ->
            cwArr.put(JSONObject().put("s", k.first).put("c", k.second).put("w", v.toDouble()))
        }
        root.put("customColWidths", cwArr)

        val rhArr = JSONArray()
        session.customRowHeights.forEach { (k, v) ->
            rhArr.put(JSONObject().put("s", k.first).put("r", k.second).put("h", v.toDouble()))
        }
        root.put("customRowHeights", rhArr)

        val wrapArr = JSONArray()
        session.customWrapCells.forEach { (k, v) ->
            wrapArr.put(JSONObject().put("s", k.first).put("k", k.second).put("v", v))
        }
        root.put("customWrapCells", wrapArr)

        val stylesArr = JSONArray()
        session.customCellStyles.forEach { (k, st) ->
            val sj = JSONObject()
                .put("s", k.first)
                .put("k", k.second)
                .put("bg", st.bgColor)
                .put("fg", st.fgColor)
                .put("fs", st.fontSize.toDouble())
                .put("nf", st.numberFormatKind)
            st.isBold?.let { sj.put("b", it) }
            st.isItalic?.let { sj.put("i", it) }
            st.isUnderline?.let { sj.put("u", it) }
            st.isStrike?.let { sj.put("st", it) }
            st.hAlign?.let { sj.put("ha", it) }
            st.vAlign?.let { sj.put("va", it) }
            st.borderTop?.let { sj.put("bt", it) }
            st.borderRight?.let { sj.put("br", it) }
            st.borderBottom?.let { sj.put("bb", it) }
            st.borderLeft?.let { sj.put("bl", it) }
            stylesArr.put(sj)
        }
        root.put("customCellStyles", stylesArr)

        val tabColorsObj = JSONObject()
        session.sheetTabColors.forEach { (idx, argb) -> tabColorsObj.put(idx.toString(), argb) }
        root.put("sheetTabColors", tabColorsObj)

        val commentsArr = JSONArray()
        session.cellComments.forEach { (k, v) ->
            commentsArr.put(JSONObject().put("s", k.first).put("k", k.second).put("v", v))
        }
        root.put("cellComments", commentsArr)

        val linksArr = JSONArray()
        session.cellHyperlinks.forEach { (k, v) ->
            linksArr.put(JSONObject().put("s", k.first).put("k", k.second).put("v", v))
        }
        root.put("cellHyperlinks", linksArr)

        val mergesObj = JSONObject()
        session.customSheetMerges.forEach { (sIdx, mList) ->
            val mArr = JSONArray()
            mList.forEach { m ->
                mArr.put(
                    JSONObject()
                        .put("sr", m.startRow)
                        .put("sc", m.startCol)
                        .put("er", m.endRow)
                        .put("ec", m.endCol)
                )
            }
            mergesObj.put(sIdx.toString(), mArr)
        }
        root.put("customSheetMerges", mergesObj)

        val pivotsArr = JSONArray()
        for (p in session.pivotTables) {
            val pj = JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("sourceSheetIndex", p.sourceSheetIndex)
                .put("sourceSheetName", p.sourceSheetName)
                .put("sourceStartRow", p.sourceStartRow)
                .put("sourceStartCol", p.sourceStartCol)
                .put("sourceEndRow", p.sourceEndRow)
                .put("sourceEndCol", p.sourceEndCol)
                .put("targetSheetIndex", p.targetSheetIndex)
                .put("targetStartRow", p.targetStartRow)
                .put("targetStartCol", p.targetStartCol)
                .put("availableFields", JSONArray(p.availableFields))
                .put("numericFields", JSONArray(p.numericFields.toList()))
                .put("currencyFields", JSONArray(p.currencyFields.toList()))
                .put("filterFields", JSONArray(p.filterFields))
                .put("columnFields", JSONArray(p.columnFields))
                .put("rowFields", JSONArray(p.rowFields))
                .put("collapsedRowGroups", JSONArray(p.collapsedRowGroups.toList()))
                .put("stylePreset", p.stylePreset)
                .put("reportLayout", p.reportLayout)
                .put("subtotalsMode", p.subtotalsMode)
                .put("grandTotalsMode", p.grandTotalsMode)
                .put("bandedRows", p.bandedRows)
                .put("bandedCols", p.bandedCols)
                .put("repeatLabels", p.repeatLabels)
                .put("blankRows", p.blankRows)
                .put("lastRenderedStartRow", p.lastRenderedStartRow)
                .put("lastRenderedEndRow", p.lastRenderedEndRow)
                .put("lastRenderedStartCol", p.lastRenderedStartCol)
                .put("lastRenderedEndCol", p.lastRenderedEndCol)
            val sfObj = JSONObject()
            p.selectedFilterValues.forEach { (fk, fv) -> sfObj.put(fk, fv) }
            pj.put("selectedFilterValues", sfObj)
            val vfArr = JSONArray()
            p.valueFields.forEach { vf ->
                vfArr.put(JSONObject().put("fieldName", vf.fieldName).put("agg", vf.agg.name).put("curr", vf.isCurrencyLike))
            }
            pj.put("valueFields", vfArr)
            val recArr = JSONArray()
            p.sourceRecords.forEach { rec ->
                val ro = JSONObject()
                rec.forEach { (rk, rv) -> ro.put(rk, rv) }
                recArr.put(ro)
            }
            pj.put("sourceRecords", recArr)
            pivotsArr.put(pj)
        }
        root.put("pivotTables", pivotsArr)
        return root.toString()
    }

    private const val MAX_HANDOFF_OBJECTS = 500
    private const val MAX_HANDOFF_SERIES = 128
    private const val MAX_HANDOFF_SERIES_POINTS = 10_000
    private const val MAX_HANDOFF_PIVOTS = 64
    private const val MAX_HANDOFF_PIVOT_RECORDS = 20_000
    private const val MAX_HANDOFF_ARRAY_ITEMS = 50_000

    fun decodeOverlayMetadataJson(rawJson: String, workbookBytes: ByteArray? = null): OpenWorkbookSession? {
        if (rawJson.isBlank()) return null
        return runCatching {
            val root = JSONObject(rawJson)
            val title = root.optString("title", "Book1.xlsx").ifBlank { "Book1.xlsx" }
            val sampleKind = root.optString("sampleKind", "blank").ifBlank { "blank" }
            val activeSheetIndex = root.optInt("activeSheetIndex", 0).coerceAtLeast(0)
            val sourceUri = root.optString("sourceUri", "")

            val sheetObjects = mutableListOf<NativeSheetObject>()
            val objsArr = root.optJSONArray("sheetObjects")
            if (objsArr != null) {
                for (i in 0 until minOf(objsArr.length(), MAX_HANDOFF_OBJECTS)) {
                    val o = objsArr.optJSONObject(i) ?: continue
                    val kind = runCatching { SheetObjectKind.valueOf(o.optString("kind", "SHAPE")) }
                        .getOrDefault(SheetObjectKind.SHAPE)
                    val imgB64 = o.optString("imageB64", "")
                    val imgBytes = if (imgB64.isNotBlank()) {
                        runCatching { Base64.getDecoder().decode(imgB64) }.getOrNull()
                    } else {
                        null
                    }
                    val labelsArr = o.optJSONArray("chartLabels")
                    val labels = if (labelsArr != null) List(minOf(labelsArr.length(), MAX_HANDOFF_SERIES_POINTS)) { labelsArr.optString(it, "") } else emptyList()
                    val valsArr = o.optJSONArray("chartValues")
                    val vals = if (valsArr != null) List(minOf(valsArr.length(), MAX_HANDOFF_SERIES_POINTS)) { valsArr.optDouble(it, 0.0).toFloat() } else emptyList()
                    val seriesList = mutableListOf<ChartSeries>()
                    val seriesArr = o.optJSONArray("chartSeries")
                    if (seriesArr != null) {
                        for (si in 0 until minOf(seriesArr.length(), MAX_HANDOFF_SERIES)) {
                            val sj = seriesArr.optJSONObject(si) ?: continue
                            val svArr = sj.optJSONArray("values")
                            val sv = if (svArr != null) List(minOf(svArr.length(), MAX_HANDOFF_SERIES_POINTS)) { svArr.optDouble(it, 0.0).toFloat() } else emptyList()
                            val xlArr = sj.optJSONArray("xLabels")
                            val xl = if (xlArr != null) List(minOf(xlArr.length(), MAX_HANDOFF_SERIES_POINTS)) { xlArr.optString(it, "") } else emptyList()
                            seriesList.add(
                                ChartSeries(
                                    name = sj.optString("name", ""),
                                    values = sv,
                                    color = sj.optInt("color", 0),
                                    nameCellRef = sj.optString("nameCellRef", ""),
                                    xRangeText = sj.optString("xRangeText", ""),
                                    yRangeText = sj.optString("yRangeText", ""),
                                    xLabels = xl
                                )
                            )
                        }
                    }
                    val yColsArr = o.optJSONArray("chartYColIndices")
                    val yCols = if (yColsArr != null) List(minOf(yColsArr.length(), MAX_HANDOFF_SERIES)) { yColsArr.optInt(it, 0) } else emptyList()
                    sheetObjects.add(
                        NativeSheetObject(
                            id = o.optString("id", "obj_$i"),
                            sheetIndex = o.optInt("sheetIndex", 0),
                            kind = kind,
                            subType = o.optString("subType", "rectangle"),
                            xPt = o.optDouble("xPt", 80.0).toFloat(),
                            yPt = o.optDouble("yPt", 60.0).toFloat(),
                            widthPt = o.optDouble("widthPt", 180.0).toFloat(),
                            heightPt = o.optDouble("heightPt", 110.0).toFloat(),
                            title = o.optString("title", ""),
                            text = o.optString("text", ""),
                            fillColor = o.optInt("fillColor", 0),
                            strokeColor = o.optInt("strokeColor", 0),
                            strokeWidthPt = o.optDouble("strokeWidthPt", 2.0).toFloat(),
                            textColor = o.optInt("textColor", 0),
                            chartTitleTextColor = o.optInt("chartTitleTextColor", 0),
                            chartXAxisTextColor = o.optInt("chartXAxisTextColor", 0),
                            chartYAxisTextColor = o.optInt("chartYAxisTextColor", 0),
                            chartLegendTextColor = o.optInt("chartLegendTextColor", 0),
                            rotationDeg = o.optDouble("rotationDeg", 0.0).toFloat(),
                            imageBytes = imgBytes,
                            chartLabels = labels,
                            chartValues = vals,
                            chartSeries = seriesList,
                            sourceRangeText = o.optString("sourceRangeText", ""),
                            chartXColIndex = o.optInt("chartXColIndex", -1),
                            chartYColIndices = yCols,
                            xAxisLabelInterval = o.optInt("xAxisLabelInterval", 0),
                            xAxisMajorStep = if (o.has("xAxisMajorStep")) o.optDouble("xAxisMajorStep").toFloat() else null,
                            xAxisMin = if (o.has("xAxisMin")) o.optDouble("xAxisMin").toFloat() else null,
                            xAxisMax = if (o.has("xAxisMax")) o.optDouble("xAxisMax").toFloat() else null,
                            yAxisMajorStep = if (o.has("yAxisMajorStep")) o.optDouble("yAxisMajorStep").toFloat() else null,
                            yAxisMin = if (o.has("yAxisMin")) o.optDouble("yAxisMin").toFloat() else null,
                            yAxisMax = if (o.has("yAxisMax")) o.optDouble("yAxisMax").toFloat() else null
                        )
                    )
                }
            }

            val cwMap = mutableMapOf<Pair<Int, Int>, Float>()
            root.optJSONArray("customColWidths")?.let { arr ->
                for (i in 0 until minOf(arr.length(), MAX_HANDOFF_ARRAY_ITEMS)) {
                    val o = arr.optJSONObject(i) ?: continue
                    cwMap[o.optInt("s", 0) to o.optInt("c", 0)] = o.optDouble("w", 72.0).toFloat()
                }
            }

            val rhMap = mutableMapOf<Pair<Int, Int>, Float>()
            root.optJSONArray("customRowHeights")?.let { arr ->
                for (i in 0 until minOf(arr.length(), MAX_HANDOFF_ARRAY_ITEMS)) {
                    val o = arr.optJSONObject(i) ?: continue
                    rhMap[o.optInt("s", 0) to o.optInt("r", 0)] = o.optDouble("h", 22.0).toFloat()
                }
            }

            val wrapMap = mutableMapOf<Pair<Int, Long>, Boolean>()
            root.optJSONArray("customWrapCells")?.let { arr ->
                for (i in 0 until minOf(arr.length(), MAX_HANDOFF_ARRAY_ITEMS)) {
                    val o = arr.optJSONObject(i) ?: continue
                    wrapMap[o.optInt("s", 0) to o.optLong("k", 0L)] = o.optBoolean("v", false)
                }
            }

            val styleMap = mutableMapOf<Pair<Int, Long>, CustomCellStyleOverride>()
            root.optJSONArray("customCellStyles")?.let { arr ->
                for (i in 0 until minOf(arr.length(), MAX_HANDOFF_ARRAY_ITEMS)) {
                    val o = arr.optJSONObject(i) ?: continue
                    styleMap[o.optInt("s", 0) to o.optLong("k", 0L)] = CustomCellStyleOverride(
                        bgColor = o.optInt("bg", 0),
                        fgColor = o.optInt("fg", 0),
                        fontSize = o.optDouble("fs", 0.0).toFloat(),
                        isBold = if (o.has("b")) o.optBoolean("b") else null,
                        isItalic = if (o.has("i")) o.optBoolean("i") else null,
                        isUnderline = if (o.has("u")) o.optBoolean("u") else null,
                        isStrike = if (o.has("st")) o.optBoolean("st") else null,
                        hAlign = if (o.has("ha")) o.optInt("ha") else null,
                        vAlign = if (o.has("va")) o.optInt("va") else null,
                        numberFormatKind = o.optString("nf", ""),
                        borderTop = if (o.has("bt")) o.optInt("bt") else null,
                        borderRight = if (o.has("br")) o.optInt("br") else null,
                        borderBottom = if (o.has("bb")) o.optInt("bb") else null,
                        borderLeft = if (o.has("bl")) o.optInt("bl") else null
                    )
                }
            }

            val tabColors = mutableMapOf<Int, Int>()
            root.optJSONObject("sheetTabColors")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val idx = k.toIntOrNull() ?: continue
                    tabColors[idx] = obj.optInt(k, 0)
                }
            }

            val commentsMap = mutableMapOf<Pair<Int, Long>, String>()
            root.optJSONArray("cellComments")?.let { arr ->
                for (i in 0 until minOf(arr.length(), MAX_HANDOFF_ARRAY_ITEMS)) {
                    val o = arr.optJSONObject(i) ?: continue
                    commentsMap[o.optInt("s", 0) to o.optLong("k", 0L)] = o.optString("v", "")
                }
            }

            val linksMap = mutableMapOf<Pair<Int, Long>, String>()
            root.optJSONArray("cellHyperlinks")?.let { arr ->
                for (i in 0 until minOf(arr.length(), MAX_HANDOFF_ARRAY_ITEMS)) {
                    val o = arr.optJSONObject(i) ?: continue
                    linksMap[o.optInt("s", 0) to o.optLong("k", 0L)] = o.optString("v", "")
                }
            }

            val mergesMap = mutableMapOf<Int, List<NativeMergeRange>>()
            root.optJSONObject("customSheetMerges")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val sIdx = k.toIntOrNull() ?: continue
                    val mArr = obj.optJSONArray(k) ?: continue
                    val mList = mutableListOf<NativeMergeRange>()
                    for (mi in 0 until minOf(mArr.length(), MAX_HANDOFF_ARRAY_ITEMS)) {
                        val mo = mArr.optJSONObject(mi) ?: continue
                        mList.add(
                            NativeMergeRange(
                                startRow = mo.optInt("sr", 0),
                                startCol = mo.optInt("sc", 0),
                                endRow = mo.optInt("er", 0),
                                endCol = mo.optInt("ec", 0)
                            )
                        )
                    }
                    mergesMap[sIdx] = mList
                }
            }

            val pivots = mutableListOf<PivotTableDefinition>()
            root.optJSONArray("pivotTables")?.let { arr ->
                for (i in 0 until minOf(arr.length(), MAX_HANDOFF_PIVOTS)) {
                    val pj = arr.optJSONObject(i) ?: continue
                    fun jsonStrList(key: String): List<String> {
                        val a = pj.optJSONArray(key) ?: return emptyList()
                        return List(minOf(a.length(), MAX_HANDOFF_SERIES_POINTS)) { a.optString(it, "") }
                    }
                    val sfMap = mutableMapOf<String, String>()
                    pj.optJSONObject("selectedFilterValues")?.let { sfObj ->
                        val sfKeys = sfObj.keys()
                        while (sfKeys.hasNext()) {
                            val fk = sfKeys.next()
                            sfMap[fk] = sfObj.optString(fk, "")
                        }
                    }
                    val decodedCurrFields = jsonStrList("currencyFields").toSet()
                    val vfList = mutableListOf<PivotValueFieldSpec>()
                    pj.optJSONArray("valueFields")?.let { vfa ->
                        for (vi in 0 until minOf(vfa.length(), MAX_HANDOFF_SERIES)) {
                            val vfo = vfa.optJSONObject(vi) ?: continue
                            val fName = vfo.optString("fieldName", "")
                            val agg = runCatching { PivotAggFunction.valueOf(vfo.optString("agg", "SUM")) }
                                .getOrDefault(PivotAggFunction.SUM)
                            val defaultCurr = PivotValueFieldSpec.resolveCurrencyLike(fName, decodedCurrFields)
                            val isCurr = vfo.optBoolean("curr", defaultCurr) && !PivotValueFieldSpec.isExplicitNonCurrencyField(fName)
                            vfList.add(PivotValueFieldSpec(fName, agg, isCurr))
                        }
                    }
                    val recs = mutableListOf<Map<String, String>>()
                    pj.optJSONArray("sourceRecords")?.let { ra ->
                        for (ri in 0 until minOf(ra.length(), MAX_HANDOFF_PIVOT_RECORDS)) {
                            val ro = ra.optJSONObject(ri) ?: continue
                            val m = mutableMapOf<String, String>()
                            val ks = ro.keys()
                            while (ks.hasNext()) {
                                val k = ks.next()
                                m[k] = ro.optString(k, "")
                            }
                            recs.add(m)
                        }
                    }
                    pivots.add(
                        PivotTableDefinition(
                            id = pj.optString("id", "pivot_$i"),
                            name = pj.optString("name", "PivotTable${i + 1}"),
                            sourceSheetIndex = pj.optInt("sourceSheetIndex", 0),
                            sourceSheetName = pj.optString("sourceSheetName", "Sheet1"),
                            sourceStartRow = pj.optInt("sourceStartRow", 0),
                            sourceStartCol = pj.optInt("sourceStartCol", 0),
                            sourceEndRow = pj.optInt("sourceEndRow", 0),
                            sourceEndCol = pj.optInt("sourceEndCol", 0),
                            targetSheetIndex = pj.optInt("targetSheetIndex", 0),
                            targetStartRow = pj.optInt("targetStartRow", 1),
                            targetStartCol = pj.optInt("targetStartCol", 0),
                            availableFields = jsonStrList("availableFields"),
                            numericFields = jsonStrList("numericFields").toSet(),
                            currencyFields = decodedCurrFields,
                            sourceRecords = recs,
                            filterFields = jsonStrList("filterFields"),
                            selectedFilterValues = sfMap,
                            columnFields = jsonStrList("columnFields"),
                            rowFields = jsonStrList("rowFields"),
                            valueFields = vfList,
                            collapsedRowGroups = jsonStrList("collapsedRowGroups").toSet(),
                            stylePreset = pj.optString("stylePreset", "PivotStyleMedium2"),
                            reportLayout = pj.optString("reportLayout", "compact"),
                            subtotalsMode = pj.optString("subtotalsMode", "bottom"),
                            grandTotalsMode = pj.optString("grandTotalsMode", "both"),
                            bandedRows = pj.optBoolean("bandedRows", true),
                            bandedCols = pj.optBoolean("bandedCols", false),
                            repeatLabels = pj.optBoolean("repeatLabels", false),
                            blankRows = pj.optBoolean("blankRows", false),
                            lastRenderedStartRow = pj.optInt("lastRenderedStartRow", 1),
                            lastRenderedEndRow = pj.optInt("lastRenderedEndRow", 1),
                            lastRenderedStartCol = pj.optInt("lastRenderedStartCol", pj.optInt("targetStartCol", 0)),
                            lastRenderedEndCol = pj.optInt("lastRenderedEndCol", 0)
                        )
                    )
                }
            }

            OpenWorkbookSession(
                id = "handoff_${System.currentTimeMillis()}",
                title = title,
                sampleKind = sampleKind,
                sourceUri = sourceUri,
                savedWorkbookBytes = workbookBytes,
                activeSheetIndex = activeSheetIndex,
                sheetObjects = sheetObjects,
                customColWidths = cwMap,
                customRowHeights = rhMap,
                customWrapCells = wrapMap,
                customCellStyles = styleMap,
                customSheetMerges = mergesMap,
                sheetTabColors = tabColors,
                cellComments = commentsMap,
                cellHyperlinks = linksMap,
                pivotTables = pivots
            )
        }.getOrNull()
    }
}

/**
 * [EN] Root immutable UI state observed by Compose screens, dialogs, ribbons, and the 2-layer Canvas container.
 * [JA] Compose UI全体および2層Canvasコンテナが購読するアプリ全体のルート不変UI状態クラスです。
 */
@androidx.compose.runtime.Immutable
data class MasuSheetUiState(
    val workbookTitle: String = "Sales.xlsx",
    val engineVersion: String = "0.14.2-native",
    val isEngineReady: Boolean = true,
    val isLoading: Boolean = false,
    val isDirty: Boolean = false,
    val language: String = "ja",
    val isDarkTheme: Boolean = false,
    val isMaterial3Ui: Boolean = true,
    val zoomPercent: Int = 100,
    val isZoomDialogOpen: Boolean = false,
    val isOpenSourceLicensesDialogOpen: Boolean = false,
    val activeSample: String = "sales",
    val activeRibbonTab: String = "home",
    val toolbarVisible: Boolean = true,
    val formulaBarVisible: Boolean = true,
    // Spreadsheet View, Page Layout, Formulas, Data, Review, Table & Pivot Toggle/Mode States
    val showGridlines: Boolean = true,
    val showHeadings: Boolean = true,
    val showFormulas: Boolean = false,
    val viewMode: String = "normal", // "normal", "pageBreakPreview", "pageLayout"
    val freezeRows: Int = 0,
    val freezeCols: Int = 0,
    val pageOrientation: String = "portrait", // "portrait", "landscape"
    val paperSize: String = "A4", // "A4", "Letter", "A3"
    val pageMargins: String = "normal", // "normal", "narrow", "wide"
    val hasPrintArea: Boolean = false,
    val hasPageBreak: Boolean = false,
    val hasPrintTitles: Boolean = false,
    val scaleToFitOnePage: Boolean = false,
    val activeConditionalFormat: String = "", // "", "dataBar", "colorScale"
    val activeSortOrder: String = "", // "", "asc", "desc"
    val isAutoFilterActive: Boolean = false,
    val autoFilterRanges: Map<Int, IntArray> = emptyMap(),
    val activeColumnFilterPopup: TableColumnFilterPopupState? = null,
    val activeColumnFilters: Map<Pair<Int, Int>, Set<String>> = emptyMap(),
    val filteredHiddenRows: Map<Int, Set<Int>> = emptyMap(),
    val isGrouped: Boolean = false,
    val tracePrecedentsActive: Boolean = false,
    val traceDependentsActive: Boolean = false,
    val showAllComments: Boolean = false,
    val isSheetProtected: Boolean = false,
    val isWorkbookProtected: Boolean = false,
    val showChangesActive: Boolean = false,
    val isTableActive: Boolean = false,
    val explicitLeftAlignCells: Set<Pair<Int, Long>> = emptySet(),
    val explicitTopAlignCells: Set<Pair<Int, Long>> = emptySet(),
    val tableTotalRow: Boolean = false,
    val tableHeaderRow: Boolean = true,
    val tableBandedRows: Boolean = true,
    val tableBandedCols: Boolean = false,
    val tableFirstCol: Boolean = false,
    val tableLastCol: Boolean = false,
    val tableFilterButton: Boolean = true,
    val pivotSubtotalsMode: String = "bottom", // "none", "bottom", "top"
    val pivotGrandTotalsMode: String = "both", // "none", "both", "rows", "columns"
    val pivotReportLayout: String = "tabular", // "compact", "outline", "tabular"
    val pivotRepeatLabels: Boolean = false,
    val pivotBlankRows: Boolean = false,
    val pivotBandedRows: Boolean = true,
    val pivotBandedCols: Boolean = false,
    val pivotStylePreset: String = "PivotStyleMedium2",
    val pivotTables: List<PivotTableDefinition> = emptyList(),
    val activePivotTableId: String? = null,
    val dismissedPivotLauncherIds: Set<String> = emptySet(),
    val isPivotFieldsPaneOpen: Boolean = false,
    val isCreatePivotDialogOpen: Boolean = false,
    val isPivotChartPickerOpen: Boolean = false,
    val isChartSelectDataDialogOpen: Boolean = false,
    val chartSelectDataTargetId: String? = null,
    val chartRangePickingObjectId: String? = null,
    val chartRangePickingMode: String = "range",
    val isChartAxisFormatDialogOpen: Boolean = false,
    val chartAxisFormatTargetAxis: String = "y", // "x" or "y"
    val openWorkbooks: List<OpenWorkbookSession> = emptyList(),
    val activeWorkbookId: String = "book_1",
    val isSplitBookViewActive: Boolean = false,
    val pivotPaneWidthDp: Float = 344f,
    val splitPaneWidthDp: Float = 420f,
    val aiPanelWidthDp: Float = 380f,
    val isCommandCenterOpen: Boolean = false,
    val commandCenterTab: String = "findReplace",
    val findMatches: List<FindMatchItem> = emptyList(),
    val currentFindMatchIndex: Int = -1,
    val lastFindSummary: String = "",
    val cellComments: Map<Pair<Int, Long>, String> = emptyMap(),
    val cellHyperlinks: Map<Pair<Int, Long>, String> = emptyMap(),
    val definedNames: Map<String, String> = emptyMap(),
    val recentWorkbooks: List<String> = emptyList(),
    val recentColors: List<Int> = emptyList(),
    val sheetTabColors: Map<Int, Int> = emptyMap(),
    val pendingSaveFileName: String? = null,
    val pendingSaveBytes: ByteArray? = null,
    val lastSavedFileName: String? = null,
    val isSaveDialogVisible: Boolean = false,
    val saveDialogBaseName: String = "Sales",
    val saveDialogExtension: String = "xlsx",
    val saveDialogFolderPath: String = MasuSheetConfig.defaultDownloadsFolderPath(),
    val saveDialogExistingFiles: List<ExistingFolderFileItem> = emptyList(),
    val statusNoticeMessage: String? = null,
    val errorMessage: String? = null,
    val sheetObjects: List<NativeSheetObject> = emptyList(),
    val selectedObjectId: String? = null,
    val selectedObjectIds: Set<String> = emptySet(),
    // Macro & VBA Studio State
    val isMacroRecording: Boolean = false,
    val macroRelativeMode: Boolean = false,
    val currentRecordingName: String = "Macro1",
    val currentRecordingShortcut: String = "Ctrl+Shift+M",
    val currentRecordingDescription: String = "",
    val currentRecordingSteps: List<MacroStep> = emptyList(),
    val savedMacros: List<MacroDefinition> = MacroStudioEngine.defaultBuiltInMacros(),
    val vbaDebugState: VbaDebugState = VbaDebugState(),
    val lastMacroUndoSteps: Int = 0,
    // AI Assistant State (Gemini / Claude / GPT / Local)
    val isAiPanelOpen: Boolean = false,
    val selectedAiModelId: String = "auto",
    val selectedAiEditMode: String = "allow_edit",
    val aiMessages: List<AiChatMessage> = listOf(
        AiChatMessage(
            id = "welcome",
            role = "assistant",
            text = "AI スプレッドシートエージェント（Gemini / Claude / GPT / ローカルAI・内蔵エージェント対応）へようこそ！\n任意のテーマの表作成、グラフ化、数式の自動挿入、条件付き書式、データ集計・分析など自由にご指示ください。"
        )
    ),
    val activeAiPlanSession: AiPlanSession? = null,
    val lastAiUndoSteps: Int = 0,
    val lastAiCreatedObjectIds: List<String> = emptyList(),
    val viewport: NativeViewportSnapshot = NativeViewportSnapshot()
) {
    val effectiveSelectedObjectIds: Set<String>
        get() = when {
            selectedObjectIds.isNotEmpty() && (selectedObjectId == null || selectedObjectId in selectedObjectIds) -> selectedObjectIds
            selectedObjectId != null -> setOf(selectedObjectId)
            else -> emptySet()
        }
    val activeWorkbookSessionId: String
        get() = activeWorkbookId
    val isSplitWorkbookViewEnabled: Boolean
        get() = isSplitBookViewActive
    val splitSecondarySessionId: String?
        get() = (openWorkbooks.lastOrNull { it.id != activeWorkbookId } ?: openWorkbooks.lastOrNull())?.id
}

