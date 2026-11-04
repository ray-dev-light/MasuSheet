package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlin.math.abs
import kotlin.math.roundToInt
import com.masusheet.app.MasuSheetViewModel.*

/**
 * ============================================================================
 * [EN] Chart Data Extraction, 32-Subtype Insertion, & Interactive X/Y Sheet Range Binding
 * Extracts numeric/category series from sheet tables, creates charts across 32 subtypes, and binds X-axis / Y-axis columns via dialog or direct sheet selection.
 *
 * ---
 * [JA / 日本語解説]
 * シート上の表からの系列・項目軸データ抽出、全32サブタイプのグラフ作成、およびシート上でのX軸／Y軸セル範囲バインディングを担うモジュールです。
 * ============================================================================
 */

/**
 * [EN] UI/application helper `ExtractedChartTableData` (extracted chart table data).
 * [JA] `ExtractedChartTableData` に対応するUI／アプリケーション補助処理を実行します。
 */
internal data class ExtractedChartTableData(
    val categories: List<String>,
    val seriesList: List<ChartSeries>,
    val rangeAddr: String,
    val xColIndex: Int = -1,
    val yColIndices: List<Int> = emptyList()
)

/**
 * [EN] ViewModel action/helper `resolveChartColumnHeaderName` handling resolve chart column header name while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `resolveChartColumnHeaderName` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.resolveChartColumnHeaderName(
    vp: NativeViewportSnapshot,
    hdrR: Int,
    rStart: Int,
    col: Int
): String {
    val colLetter = MasuSheetConfig.formatColumnLabel(col)
    if (rStart > hdrR) {
        val hTxt = vp.cellAt(hdrR, col)?.displayText?.trim().orEmpty()
        if (hTxt.isNotEmpty() && parseNumericCell(vp.cellAt(hdrR, col)) == null) {
            return hTxt
        }
        if (hTxt.isNotEmpty()) return hTxt
    } else if (hdrR > 0) {
        val aboveCell = vp.cellAt(hdrR - 1, col)
        val aboveTxt = aboveCell?.displayText?.trim().orEmpty()
        if (aboveTxt.isNotEmpty() && parseNumericCell(aboveCell) == null) {
            return aboveTxt
        }
    }
    return "${colLetter}列"
}

/**
 * Extracts 2D table data (`categories`, `seriesList`, `rangeText`) from the currently selected range
 * (or auto-detects the contiguous table around the active cell / sheet when only 1 cell is selected).
 */
fun MasuSheetViewModel.extractChartDataFromViewport(vp: NativeViewportSnapshot): Triple<List<String>, List<ChartSeries>, String> {
    val res = extractChartDataWithColumns(vp)
    return Triple(res.categories, res.seriesList, res.rangeAddr)
}

/**
 * [EN] ViewModel action/helper `extractChartDataWithColumns` handling extract chart data with columns while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `extractChartDataWithColumns` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.extractChartDataWithColumns(vp: NativeViewportSnapshot): ExtractedChartTableData {
    var r0 = minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0)
    var r1 = maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(r0)
    var c0 = minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
    var c1 = maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(c0)

    // If only a single cell is selected (or the selected range is completely blank), auto-expand to the contiguous table of cells
    val selectionHasAnyContent = (r0..r1).any { rr ->
        (c0..c1).any { cc -> !vp.cellAt(rr, cc)?.displayText.isNullOrBlank() }
    }
    if ((r0 == r1 && c0 == c1) || !selectionHasAnyContent) {
        val detected = detectTableBoundsAt(vp, r0, c0)
        r0 = detected[0]
        r1 = detected[2]
        c0 = detected[3]
        c1 = detected[4]
    }

    val rangeAddr = "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c1)}"

    // Case 1: Single Row selected across multiple columns (e.g. B5:E5)
    if (r0 == r1 && c1 > c0) {
        val cats = mutableListOf<String>()
        val vals = mutableListOf<Float>()
        var seriesName = ""
        var nameRef = ""
        var hdrRowFound = -1
        val numCols = mutableListOf<Int>()
        for (c in c0..c1) {
            val cell = vp.cellAt(r0, c)
            val num = parseNumericCell(cell)
            if (num != null) {
                vals.add(num)
                numCols.add(c)
                // Look upward for column header (e.g. Q1, Q2, Q3, Q4)
                var hdr = ""
                for (hr in (r0 - 1) downTo 0) {
                    val hTxt = vp.cellAt(hr, c)?.displayText?.trim().orEmpty()
                    if (hTxt.isNotEmpty() && parseNumericCell(vp.cellAt(hr, c)) == null) {
                        hdr = hTxt
                        if (hdrRowFound < 0) hdrRowFound = hr
                        break
                    }
                }
                cats.add(hdr.ifEmpty { MasuSheetConfig.formatColumnLabel(c) })
            } else {
                val txt = cell?.displayText?.trim().orEmpty()
                if (txt.isNotEmpty() && seriesName.isEmpty()) {
                    seriesName = txt
                    nameRef = MasuSheetConfig.formatCellAddress(r0, c)
                }
            }
        }
        if (seriesName.isEmpty()) {
            for (lc in (c0 - 1) downTo 0) {
                val lTxt = vp.cellAt(r0, lc)?.displayText?.trim().orEmpty()
                if (lTxt.isNotEmpty()) {
                    seriesName = lTxt
                    nameRef = MasuSheetConfig.formatCellAddress(r0, lc)
                    break
                }
            }
        }
        if (vals.isNotEmpty()) {
            val cMin = numCols.firstOrNull() ?: c0
            val cMax = numCols.lastOrNull() ?: c1
            val yAddr = "${MasuSheetConfig.formatCellAddress(r0, cMin)}:${MasuSheetConfig.formatCellAddress(r0, cMax)}"
            val xAddr = if (hdrRowFound >= 0) {
                "${MasuSheetConfig.formatCellAddress(hdrRowFound, cMin)}:${MasuSheetConfig.formatCellAddress(hdrRowFound, cMax)}"
            } else {
                ""
            }
            return ExtractedChartTableData(
                categories = cats,
                seriesList = listOf(
                    ChartSeries(
                        name = seriesName.ifEmpty { "データ" },
                        values = vals,
                        nameCellRef = nameRef,
                        xRangeText = xAddr,
                        yRangeText = yAddr,
                        xLabels = cats
                    )
                ),
                rangeAddr = rangeAddr
            )
        }
    }

    // Case 2: Single Column selected across multiple rows (e.g. B4:B9)
    if (c0 == c1 && r1 > r0) {
        val firstNumR = (r0..r1).firstOrNull { r -> parseNumericCell(vp.cellAt(r, c0)) != null } ?: r0
        val hdrR = ((firstNumR - 1) downTo maxOf(0, r0 - 1)).firstOrNull { hr ->
            !vp.cellAt(hr, c0)?.displayText.isNullOrBlank() && parseNumericCell(vp.cellAt(hr, c0)) == null
        }
        val seriesName = if (hdrR != null) {
            vp.cellAt(hdrR, c0)?.displayText?.trim().orEmpty()
        } else {
            "${MasuSheetConfig.formatColumnLabel(c0)}列"
        }
        val nameRef = if (hdrR != null) MasuSheetConfig.formatCellAddress(hdrR, c0) else ""
        val cats = mutableListOf<String>()
        val vals = mutableListOf<Float>()
        var inferredXCol = -1
        var lastNumR = firstNumR
        for (r in firstNumR..r1) {
            val num = parseNumericCell(vp.cellAt(r, c0)) ?: continue
            lastNumR = r
            var rowLbl = ""
            if (c0 > 0) {
                val txt = vp.cellAt(r, c0 - 1)?.displayText?.trim().orEmpty()
                if (txt.isNotEmpty() && parseNumericCell(vp.cellAt(r, c0 - 1)) == null) {
                    rowLbl = txt
                    inferredXCol = c0 - 1
                }
            }
            cats.add(rowLbl.ifEmpty { "行${r + 1}" })
            vals.add(num)
        }
        if (vals.isNotEmpty()) {
            val yAddr = "${MasuSheetConfig.formatCellAddress(firstNumR, c0)}:${MasuSheetConfig.formatCellAddress(lastNumR, c0)}"
            val xAddr = if (inferredXCol >= 0) {
                "${MasuSheetConfig.formatCellAddress(firstNumR, inferredXCol)}:${MasuSheetConfig.formatCellAddress(lastNumR, inferredXCol)}"
            } else {
                ""
            }
            return ExtractedChartTableData(
                categories = cats,
                seriesList = listOf(
                    ChartSeries(
                        name = seriesName,
                        values = vals,
                        nameCellRef = nameRef,
                        xRangeText = xAddr,
                        yRangeText = yAddr,
                        xLabels = cats
                    )
                ),
                rangeAddr = rangeAddr,
                xColIndex = inferredXCol,
                yColIndices = listOf(c0)
            )
        }
    }

    // Case 3: 2D Rectangular Table Range (r0..r1 x c0..c1)
    val topRowHasTextHeader = r1 > r0 && (c0..c1).any { c ->
        val cell = vp.cellAt(r0, c)
        !cell?.displayText.isNullOrBlank() && parseNumericCell(cell) == null
    } && (c0..c1).any { c ->
        ((r0 + 1)..r1).any { r -> parseNumericCell(vp.cellAt(r, c)) != null }
    }
    val firstNumericRow = if (topRowHasTextHeader) {
        ((r0 + 1)..r1).firstOrNull { r ->
            (c0..c1).any { c -> parseNumericCell(vp.cellAt(r, c)) != null }
        } ?: (r0 + 1)
    } else {
        (r0..r1).firstOrNull { r ->
            (c0..c1).any { c -> parseNumericCell(vp.cellAt(r, c)) != null }
        } ?: r0
    }
    val dataStartR = firstNumericRow
    val headerRowIdx: Int? = if (topRowHasTextHeader) {
        r0
    } else {
        ((firstNumericRow - 1) downTo maxOf(0, r0 - 1)).firstOrNull { hr ->
            (c0..c1).any { c ->
                !vp.cellAt(hr, c)?.displayText.isNullOrBlank() && parseNumericCell(vp.cellAt(hr, c)) == null
            }
        }
    }

    // Identify numeric columns and text label column(s) within c0..c1
    val numericCols = (c0..c1).filter { c ->
        (dataStartR..r1).any { r -> parseNumericCell(vp.cellAt(r, c)) != null }
    }
    val textLabelColInSelection = (c0..c1).firstOrNull { c ->
        c !in numericCols && (dataStartR..r1).any { r -> !vp.cellAt(r, c)?.displayText.isNullOrBlank() }
    } ?: run {
        if (c0 > 0 && (dataStartR..r1).any { r ->
                !vp.cellAt(r, c0 - 1)?.displayText.isNullOrBlank() && parseNumericCell(vp.cellAt(r, c0 - 1)) == null
            }
        ) {
            c0 - 1
        } else {
            null
        }
    }

    val validDataRows = (dataStartR..r1).filter { r ->
        val firstColTxt = vp.cellAt(r, c0)?.displayText?.trim().orEmpty()
        firstColTxt !in setOf("合計", "総計", "小計", "平均", "Total", "Grand Total", "Subtotal", "Average") &&
            numericCols.any { c -> parseNumericCell(vp.cellAt(r, c)) != null }
    }

    if (validDataRows.isNotEmpty() && numericCols.isNotEmpty()) {
        // Special case: 1 data row across multiple numeric columns (e.g. Header row Q1..Q4 + 1 Region row)
        // Transpose so X-axis is [Q1, Q2, Q3, Q4] and the row is plotted as a connected line / bar series!
        if (validDataRows.size == 1 && numericCols.size >= 2) {
            val singleR = validDataRows.first()
            val rowName = if (textLabelColInSelection != null) {
                vp.cellAt(singleR, textLabelColInSelection)?.displayText?.trim().orEmpty()
            } else {
                ""
            }.ifEmpty { "行${singleR + 1}" }
            val nameRef = if (textLabelColInSelection != null) {
                MasuSheetConfig.formatCellAddress(singleR, textLabelColInSelection)
            } else {
                ""
            }
            val colCategories = numericCols.map { c ->
                val h = if (headerRowIdx != null) vp.cellAt(headerRowIdx, c)?.displayText?.trim().orEmpty() else ""
                h.ifEmpty { MasuSheetConfig.formatColumnLabel(c) }
            }
            val rowValues = numericCols.map { c -> parseNumericCell(vp.cellAt(singleR, c)) ?: 0f }
            val cMin = numericCols.first()
            val cMax = numericCols.last()
            val yAddr = "${MasuSheetConfig.formatCellAddress(singleR, cMin)}:${MasuSheetConfig.formatCellAddress(singleR, cMax)}"
            val xAddr = if (headerRowIdx != null) {
                "${MasuSheetConfig.formatCellAddress(headerRowIdx, cMin)}:${MasuSheetConfig.formatCellAddress(headerRowIdx, cMax)}"
            } else {
                ""
            }
            return ExtractedChartTableData(
                categories = colCategories,
                seriesList = listOf(
                    ChartSeries(
                        name = rowName,
                        values = rowValues,
                        nameCellRef = nameRef,
                        xRangeText = xAddr,
                        yRangeText = yAddr,
                        xLabels = colCategories
                    )
                ),
                rangeAddr = rangeAddr
            )
        }

        // When 2+ numeric columns are selected and there is no text category column (e.g. X in col I = 1..21, Y in col J = 2..42),
        // use the leftmost column as the X-Axis (Horizontal) and the remaining column(s) as the Y-Axis (Vertical)!
        val useFirstNumericColAsX = textLabelColInSelection == null && numericCols.size >= 2
        val effectiveXCol: Int? = if (useFirstNumericColAsX) numericCols.first() else textLabelColInSelection
        val effectiveYCols: List<Int> = if (useFirstNumericColAsX) numericCols.drop(1) else numericCols

        val rFirst = validDataRows.first()
        val rLast = validDataRows.last()
        val xAddr = if (effectiveXCol != null) {
            "${MasuSheetConfig.formatCellAddress(rFirst, effectiveXCol)}:${MasuSheetConfig.formatCellAddress(rLast, effectiveXCol)}"
        } else {
            ""
        }

        val categories = validDataRows.map { r ->
            val rawLbl = if (effectiveXCol != null) {
                vp.cellAt(r, effectiveXCol)?.displayText?.trim().orEmpty()
            } else {
                ""
            }
            rawLbl.ifEmpty { "行${r + 1}" }
        }

        val seriesList = effectiveYCols.map { c ->
            val sName = if (headerRowIdx != null) {
                vp.cellAt(headerRowIdx, c)?.displayText?.trim().orEmpty()
            } else {
                ""
            }.ifEmpty { "${MasuSheetConfig.formatColumnLabel(c)}列" }
            val nameRef = if (headerRowIdx != null) MasuSheetConfig.formatCellAddress(headerRowIdx, c) else ""
            val yAddr = "${MasuSheetConfig.formatCellAddress(rFirst, c)}:${MasuSheetConfig.formatCellAddress(rLast, c)}"
            val sValues = validDataRows.map { r ->
                parseNumericCell(vp.cellAt(r, c)) ?: 0f
            }
            ChartSeries(
                name = sName,
                values = sValues,
                nameCellRef = nameRef,
                xRangeText = xAddr,
                yRangeText = yAddr,
                xLabels = categories
            )
        }

        return ExtractedChartTableData(
            categories = categories,
            seriesList = seriesList,
            rangeAddr = rangeAddr,
            xColIndex = effectiveXCol ?: -1,
            yColIndices = effectiveYCols
        )
    }

    // Fallback if sheet has no numbers at all
    val fallbackCats = listOf("項目1", "項目2", "項目3", "項目4")
    val fallbackSeries = listOf(ChartSeries("系列1", listOf(0f, 0f, 0f, 0f), xLabels = fallbackCats))
    return ExtractedChartTableData(fallbackCats, fallbackSeries, rangeAddr)
}

/**
 * [EN] ViewModel action/helper `onInsertChart` handling insert chart while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onInsertChart` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onInsertChart(chartType: String) {
    val vp = _uiState.value.viewport
    onInsertChartFromRange(
        chartType = chartType,
        startRow = vp.selStartRow,
        startCol = vp.selStartCol,
        endRow = vp.selEndRow,
        endCol = vp.selEndCol,
        customTitle = null
    )
}

/**
 * [EN] ViewModel action/helper `onInsertChartFromRange` handling insert chart from range while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onInsertChartFromRange` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onInsertChartFromRange(
    chartType: String,
    startRow: Int,
    startCol: Int,
    endRow: Int,
    endCol: Int,
    customTitle: String? = null
): String {
    val vp = _uiState.value.viewport
    val id = "obj_${nextObjectCounter++}"
    val normalizedChartType = SpreadsheetChartTypeCatalog.normalizeSubType(chartType)
    val spec = SpreadsheetChartTypeCatalog.findSpec(normalizedChartType)

    val targetVp = vp.copy(
        selStartRow = startRow.coerceAtLeast(0),
        selStartCol = startCol.coerceAtLeast(0),
        selEndRow = endRow.coerceAtLeast(0),
        selEndCol = endCol.coerceAtLeast(0)
    )
    val extracted = extractChartDataWithColumns(targetVp)
    val categories = extracted.categories
    val seriesList = extracted.seriesList
    val sourceRange = extracted.rangeAddr
    val primaryValues = seriesList.firstOrNull()?.values ?: listOf(0f)
    val isEn = _uiState.value.language == "en"
    val seriesSummary = if (seriesList.size == 1) {
        seriesList.first().name
    } else {
        if (isEn) "${seriesList.size} series ($sourceRange)" else "${seriesList.size}系列 ($sourceRange)"
    }

    val titleJa = customTitle?.takeIf { it.isNotBlank() } ?: run {
        val typeTitle = if (isEn) spec.labelEn else spec.labelJa
        "$typeTitle: $seriesSummary"
    }
    val anchorCol = (maxOf(startCol, endCol) + 1).coerceIn(1, (vp.colWidths.size - 1).coerceAtLeast(1))
    val anchorRow = minOf(startRow, endRow).coerceIn(0, (vp.rowHeights.size - 1).coerceAtLeast(0))
    val xPt = vp.colOffsets.getOrElse(anchorCol) { 140f }
    val yPt = vp.rowOffsets.getOrElse(anchorRow) { 36f }
    val newObj = NativeSheetObject(
        id = id,
        sheetIndex = vp.sheetIndex,
        kind = SheetObjectKind.CHART,
        subType = normalizedChartType,
        xPt = xPt,
        yPt = yPt,
        widthPt = 310f,
        heightPt = 210f,
        title = titleJa,
        text = titleJa,
        fillColor = 0xFFFFFFFF.toInt(),
        strokeColor = 0xFF1F9D55.toInt(),
        strokeWidthPt = 2.0f,
        chartLabels = categories,
        chartValues = primaryValues,
        chartSeries = seriesList,
        sourceRangeText = sourceRange,
        chartXColIndex = extracted.xColIndex,
        chartYColIndices = extracted.yColIndices
    )
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 1))
    val snap = engineGateway.executeCommand("insert.chart", "{\"type\":\"$normalizedChartType\",\"title\":${JSONObject.quote(titleJa)}}")
    applyViewportSnapshot(snap)
    val typeDisplay = if (isEn) spec.labelEn else spec.labelJa
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = _uiState.value.sheetObjects + newObj,
        selectedObjectId = id,
        activeRibbonTab = "chartDesign",
        statusNoticeMessage = if (isEn) {
            "Created $typeDisplay from range $sourceRange (${categories.size} categories × ${seriesList.size} series)"
        } else {
            "範囲 $sourceRange (${categories.size}項目 × ${seriesList.size}系列) から「$typeDisplay」を作成しました"
        }
    )
    return id
}

/**
 * [EN] ViewModel action/helper `onOpenPivotChartPicker` handling open pivot chart picker while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onOpenPivotChartPicker` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onOpenPivotChartPicker(open: Boolean = true) {
    _uiState.value = _uiState.value.copy(isPivotChartPickerOpen = open)
}

/**
 * Opens Spreadsheet's "Select Data Source (`データの選択`)" dialog for a standalone or bound chart.
 */
fun MasuSheetViewModel.onOpenChartSelectDataDialog(objectId: String? = null, open: Boolean = true) {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val resolvedId = if (open) {
        objectId
            ?: cur.selectedObjectId?.takeIf { id -> cur.sheetObjects.any { it.id == id && it.kind == SheetObjectKind.CHART } }
            ?: cur.sheetObjects.lastOrNull { it.sheetIndex == sIdx && it.kind == SheetObjectKind.CHART }?.id
            ?: cur.sheetObjects.lastOrNull { it.kind == SheetObjectKind.CHART }?.id
    } else {
        null
    }
    _uiState.value = cur.copy(
        isChartSelectDataDialogOpen = open && resolvedId != null,
        chartSelectDataTargetId = resolvedId,
        selectedObjectId = resolvedId ?: cur.selectedObjectId,
        chartRangePickingObjectId = if (open) null else cur.chartRangePickingObjectId
    )
}

/**
 * [EN] ViewModel action/helper `resolveTargetChartObjectId` handling resolve target chart object id while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `resolveTargetChartObjectId` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.resolveTargetChartObjectId(objectId: String? = null): String? {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    return objectId
        ?: cur.chartSelectDataTargetId?.takeIf { id -> cur.sheetObjects.any { it.id == id && it.kind == SheetObjectKind.CHART } }
        ?: cur.selectedObjectId?.takeIf { id -> cur.sheetObjects.any { it.id == id && it.kind == SheetObjectKind.CHART } }
        ?: cur.sheetObjects.lastOrNull { it.sheetIndex == sIdx && it.kind == SheetObjectKind.CHART }?.id
        ?: cur.sheetObjects.lastOrNull { it.kind == SheetObjectKind.CHART }?.id
}

/**
 * [EN] ViewModel action/helper `handleChartRangeOrAxisPicking` handling handle chart range or axis picking while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `handleChartRangeOrAxisPicking` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.handleChartRangeOrAxisPicking(chartId: String, rangeAddr: String, mode: String) {
    when {
        mode == "x" -> onBindSelectionAsChartXAxis(chartId, rangeAddr)
        mode == "y" -> onBindSelectionAsChartYAxis(chartId, append = false, rangeTextOverride = rangeAddr)
        mode == "y_append" -> onBindSelectionAsChartYAxis(chartId, append = true, rangeTextOverride = rangeAddr)
        mode.startsWith("series_x:") -> {
            val sIdx = mode.removePrefix("series_x:").toIntOrNull() ?: 0
            onUpdateChartSeriesRanges(chartId, sIdx, newXRangeText = rangeAddr, closeDialog = false)
        }
        mode.startsWith("series_y:") -> {
            val sIdx = mode.removePrefix("series_y:").toIntOrNull() ?: 0
            onUpdateChartSeriesRanges(chartId, sIdx, newYRangeText = rangeAddr, closeDialog = false)
        }
        else -> onUpdateChartDataRange(chartId, rangeAddr, closeDialog = false)
    }
}

/**
 * Enters interactive on-sheet range picking mode (`🖱️ シート上をドラッグしてデータ範囲を選択`):
 * while active, clicking or dragging cells on the grid immediately updates the target chart's data range live!
 */
fun MasuSheetViewModel.onStartChartRangePickingOnSheet(objectId: String? = null) {
    val cur = _uiState.value
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        isChartSelectDataDialogOpen = false,
        chartSelectDataTargetId = resolvedId,
        chartRangePickingObjectId = resolvedId,
        chartRangePickingMode = "range",
        selectedObjectId = resolvedId,
        statusNoticeMessage = if (isEn) {
            "Drag or click a cell range on the sheet to bind data to the chart"
        } else {
            "📊 シート上のセル範囲をドラッグ（またはクリック）するとリアルタイムでグラフ化されます"
        }
    )
}

/**
 * Enters interactive on-sheet X-axis (`"x"` / `"series_x:idx"`) or Y-axis (`"y"` / `"y_append"` / `"series_y:idx"`) picking mode!
 */
fun MasuSheetViewModel.onStartChartAxisPickingOnSheet(objectId: String? = null, axisMode: String = "x") {
    val cur = _uiState.value
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return
    val isEn = cur.language == "en"
    val cleanMode = axisMode.lowercase().trim()
    val msg = when {
        cleanMode == "x" || cleanMode.startsWith("series_x:") -> if (isEn) {
            "📏 Click or drag a column/range on the sheet to set as Chart X-Axis (Horizontal)"
        } else {
            "📏 シート上の列またはセル範囲をクリック/ドラッグすると【X軸（横軸）】に反映されます"
        }
        cleanMode == "y_append" -> if (isEn) {
            "➕ Click or drag a column/range on the sheet to add as Chart Y-Axis series"
        } else {
            "➕ シート上の列またはセル範囲をクリック/ドラッグすると【Y軸（縦軸）系列】に追加されます"
        }
        else -> if (isEn) {
            "📈 Click or drag a column/range on the sheet to set as Chart Y-Axis (Vertical)"
        } else {
            "📈 シート上の列またはセル範囲をクリック/ドラッグすると【Y軸（縦軸）】に反映されます"
        }
    }
    _uiState.value = cur.copy(
        isChartSelectDataDialogOpen = false,
        chartSelectDataTargetId = resolvedId,
        chartRangePickingObjectId = resolvedId,
        chartRangePickingMode = cleanMode,
        selectedObjectId = resolvedId,
        statusNoticeMessage = msg
    )
}

/**
 * [EN] ViewModel action/helper `onFinishChartRangePickingOnSheet` handling finish chart range picking on sheet while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onFinishChartRangePickingOnSheet` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onFinishChartRangePickingOnSheet() {
    val cur = _uiState.value
    val targetId = cur.chartRangePickingObjectId
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        chartRangePickingObjectId = null,
        chartRangePickingMode = "range",
        selectedObjectId = targetId ?: cur.selectedObjectId,
        statusNoticeMessage = if (isEn) "Applied selected data range to chart" else "選択したデータ範囲をグラフに反映しました"
    )
}

/**
 * Returns `[headerRow, dataStartRow, dataEndRow, startCol, endCol]` for the table associated with `objectId`
 * (or `customTableRangeText` if provided), expanding horizontally to include all columns of that table!
 * Also detects headerless numeric tables (e.g. `1..21` in col I, `2..42` in col J) so row 1 is never skipped.
 */
fun MasuSheetViewModel.getChartSourceTableBounds(objectId: String? = null, customTableRangeText: String = ""): IntArray {
    val cur = _uiState.value
    val vp = cur.viewport
    val resolvedId = resolveTargetChartObjectId(objectId)
    val chartObj = cur.sheetObjects.firstOrNull { it.id == resolvedId && it.kind == SheetObjectKind.CHART }
    val rawRange = customTableRangeText.ifBlank { chartObj?.sourceRangeText.orEmpty() }
        .trim()
        .removePrefix("=")
        .substringAfter('!')
        .trim()

    val baseBounds: IntArray = if (rawRange.isNotEmpty()) {
        val parts = rawRange.split(':', limit = 2)
        val p0 = parseSingleCellRef(parts[0])
        val p1 = if (parts.size >= 2) parseSingleCellRef(parts[1]) else p0
        if (p0 != null && p1 != null) {
            val r0 = minOf(p0.first, p1.first).coerceAtLeast(0)
            val c0 = minOf(p0.second, p1.second).coerceAtLeast(0)
            val r1 = maxOf(p0.first, p1.first).coerceAtLeast(r0)
            val c1 = maxOf(p0.second, p1.second).coerceAtLeast(c0)
            val rangeHasContent = (r0..r1).any { rr ->
                (c0..c1).any { cc -> !vp.cellAt(rr, cc)?.displayText.isNullOrBlank() }
            }
            val detected = detectTableBoundsAt(vp, r0, c0)
            if (!rangeHasContent) {
                detected
            } else {
                // Expand column bounds to include both the explicit range and the contiguous table columns
                val fullC0 = minOf(c0, detected[3])
                val fullC1 = maxOf(c1, detected[4])
                val hdrR = minOf(r0, detected[0])
                val dataEnd = maxOf(r1, detected[2]).let { endCandidate ->
                    val firstTxt = vp.cellAt(endCandidate, fullC0)?.displayText?.trim().orEmpty()
                    if (endCandidate > hdrR + 1 && firstTxt in setOf("合計", "総計", "小計", "平均", "Total", "Grand Total", "Subtotal", "Average")) {
                        endCandidate - 1
                    } else {
                        endCandidate
                    }
                }
                intArrayOf(hdrR, (hdrR + 1).coerceAtMost(dataEnd), dataEnd, fullC0, fullC1)
            }
        } else {
            detectTableBoundsAt(vp, vp.activeRow, vp.activeCol)
        }
    } else {
        detectTableBoundsAt(vp, vp.activeRow, vp.activeCol)
    }

    val hdrR = baseBounds[0]
    val dataEnd = baseBounds[2]
    val fullC0 = baseBounds[3]
    val fullC1 = baseBounds[4]
    val topRowHasTextHeader = (fullC0..fullC1).any { cc ->
        val cell = vp.cellAt(hdrR, cc)
        !cell?.displayText.isNullOrBlank() && parseNumericCell(cell) == null
    }
    val dataStart = if (topRowHasTextHeader && dataEnd > hdrR) {
        (hdrR + 1).coerceAtMost(dataEnd)
    } else {
        hdrR
    }
    return intArrayOf(hdrR, dataStart, dataEnd, fullC0, fullC1)
}

/**
 * Returns all columns of the chart's source table so the user can pick any column for X-Axis (`横軸`)
 * and any column(s) for Y-Axis (`縦軸`) with 1 click from the right-click menu or Select Data dialog.
 */
fun MasuSheetViewModel.getChartSourceTableColumns(objectId: String? = null, customTableRangeText: String = ""): List<ChartTableColumnOption> {
    val vp = _uiState.value.viewport
    val bounds = getChartSourceTableBounds(objectId, customTableRangeText)
    val hdrR = bounds[0]
    val rStart = bounds[1]
    val rEnd = bounds[2]
    val cStart = bounds[3]
    val cEnd = bounds[4]

    val validRows = (rStart..rEnd).filter { r ->
        val firstColTxt = vp.cellAt(r, cStart)?.displayText?.trim().orEmpty()
        firstColTxt !in setOf("合計", "総計", "小計", "平均", "Total", "Grand Total", "Subtotal", "Average") &&
            (cStart..cEnd).any { cc -> !vp.cellAt(r, cc)?.displayText.isNullOrBlank() }
    }.ifEmpty { (rStart..rEnd).toList() }

    val allCols = (cStart..cEnd).map { c ->
        val colLetter = MasuSheetConfig.formatColumnLabel(c)
        val headerName = resolveChartColumnHeaderName(vp, hdrR, rStart, c)
        var numCount = 0
        var nonBlankCount = 0
        val samples = mutableListOf<String>()
        for (r in validRows) {
            val cell = vp.cellAt(r, c)
            val disp = cell?.displayText?.trim().orEmpty()
            if (disp.isNotEmpty()) {
                nonBlankCount++
                if (parseNumericCell(cell) != null) numCount++
                if (samples.size < 3) samples.add(disp)
            }
        }
        val isNum = nonBlankCount > 0 && numCount * 2 >= nonBlankCount
        val colRange = "${MasuSheetConfig.formatCellAddress(hdrR, c)}:${MasuSheetConfig.formatCellAddress(rEnd, c)}"
        ChartTableColumnOption(
            colIndex = c,
            colLetter = colLetter,
            headerName = headerName,
            rangeText = colRange,
            isNumeric = isNum,
            sampleValues = samples
        )
    }
    return allCols.filter { it.sampleValues.isNotEmpty() }.ifEmpty { allCols }
}

/**
 * Sets the specified column (`colIndex`) of the source table as the chart's X-Axis (`横軸・項目軸ラベル`).
 */
fun MasuSheetViewModel.onSelectChartXAxisColumn(
    objectId: String? = null,
    colIndex: Int,
    customTableRangeText: String = ""
): Boolean {
    val cur = _uiState.value
    val vp = cur.viewport
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return false
    val bounds = getChartSourceTableBounds(resolvedId, customTableRangeText)
    val hdrR = bounds[0]
    val rStart = bounds[1]
    val rEnd = bounds[2]
    val cStart = bounds[3]
    val cEnd = bounds[4]
    val targetCol = colIndex.coerceIn(0, (vp.colWidths.size - 1).coerceAtLeast(0))

    val validRows = (rStart..rEnd).filter { r ->
        val firstColTxt = vp.cellAt(r, cStart)?.displayText?.trim().orEmpty()
        firstColTxt !in setOf("合計", "総計", "小計", "平均", "Total", "Grand Total", "Subtotal", "Average") &&
            (cStart..cEnd).any { cc -> !vp.cellAt(r, cc)?.displayText.isNullOrBlank() }
    }.ifEmpty { (rStart..rEnd).toList() }

    val newLabels = validRows.map { r ->
        vp.cellAt(r, targetCol)?.displayText?.trim().orEmpty().ifEmpty { "行${r + 1}" }
    }
    if (newLabels.isEmpty()) return false

    val rFirst = validRows.first()
    val rLast = validRows.last()
    val xRangeAddr = "${MasuSheetConfig.formatCellAddress(rFirst, targetCol)}:${MasuSheetConfig.formatCellAddress(rLast, targetCol)}"
    val colLetter = MasuSheetConfig.formatColumnLabel(targetCol)
    val colHeader = resolveChartColumnHeaderName(vp, hdrR, rStart, targetCol)
    val tableRangeAddr = "${MasuSheetConfig.formatCellAddress(hdrR, minOf(cStart, targetCol))}:${MasuSheetConfig.formatCellAddress(rEnd, maxOf(cEnd, targetCol))}"

    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updatedObjects = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
            val cols = getChartSourceTableColumns(resolvedId, tableRangeAddr)
            val selectedSIdx = obj.selectedSubElement.removePrefix("series:").toIntOrNull()
            if (selectedSIdx != null && selectedSIdx in obj.chartSeries.indices && obj.chartSeries.size > 1) {
                // Update X-axis only for the currently selected series!
                val updatedSeries = obj.chartSeries.mapIndexed { idx, s ->
                    if (idx == selectedSIdx) {
                        s.copy(xRangeText = xRangeAddr, xLabels = newLabels)
                    } else {
                        s
                    }
                }
                obj.copy(
                    chartLabels = if (selectedSIdx == 0) newLabels else obj.chartLabels,
                    chartSeries = updatedSeries,
                    sourceRangeText = tableRangeAddr,
                    chartXColIndex = if (selectedSIdx == 0) targetCol else obj.chartXColIndex
                )
            } else {
                // Ensure Y-axis series match the row count of this table and exclude the new X-axis column if other columns exist
                val keepExistingSeries = obj.chartSeries.isNotEmpty() &&
                    obj.chartSeries.first().values.size == validRows.size &&
                    (obj.chartYColIndices.isEmpty() || obj.chartYColIndices != listOf(targetCol) || cols.size <= 1)
                val (alignedSeries, nextYCols) = if (keepExistingSeries) {
                    val resolvedY = if (obj.chartYColIndices.isNotEmpty()) {
                        obj.chartYColIndices
                    } else {
                        cols.filter { it.headerName in obj.chartSeries.map { s -> s.name } }.map { it.colIndex }
                    }
                    val syncedSeries = obj.chartSeries.mapIndexed { idx, s ->
                        val yCol = resolvedY.getOrNull(idx)
                        val yAddr = if (yCol != null) {
                            "${MasuSheetConfig.formatCellAddress(rFirst, yCol)}:${MasuSheetConfig.formatCellAddress(rLast, yCol)}"
                        } else {
                            s.yRangeText
                        }
                        s.copy(xRangeText = xRangeAddr, yRangeText = yAddr, xLabels = newLabels)
                    }
                    syncedSeries to resolvedY
                } else {
                    val yCols = cols.filter { it.isNumeric && it.colIndex != targetCol }
                        .ifEmpty { cols.filter { it.colIndex != targetCol }.take(1) }
                        .ifEmpty { cols.take(1) }
                    val builtSeries = yCols.map { yc ->
                        val yAddr = "${MasuSheetConfig.formatCellAddress(rFirst, yc.colIndex)}:${MasuSheetConfig.formatCellAddress(rLast, yc.colIndex)}"
                        val nameRef = if (hdrR < rStart) MasuSheetConfig.formatCellAddress(hdrR, yc.colIndex) else ""
                        ChartSeries(
                            name = yc.headerName,
                            values = validRows.map { r -> parseNumericCell(vp.cellAt(r, yc.colIndex)) ?: 0f },
                            nameCellRef = nameRef,
                            xRangeText = xRangeAddr,
                            yRangeText = yAddr,
                            xLabels = newLabels
                        )
                    }.ifEmpty { obj.chartSeries }
                    builtSeries to yCols.map { it.colIndex }
                }
                obj.copy(
                    chartLabels = newLabels,
                    chartSeries = alignedSeries,
                    chartValues = alignedSeries.firstOrNull()?.values ?: obj.chartValues,
                    sourceRangeText = tableRangeAddr,
                    chartXColIndex = targetCol,
                    chartYColIndices = nextYCols
                )
            }
        } else {
            obj
        }
    }
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updatedObjects,
        selectedObjectId = resolvedId,
        statusNoticeMessage = if (isEn) {
            "Set Chart X-Axis (Horizontal) to column $colLetter ($colHeader: $xRangeAddr)"
        } else {
            "グラフの【X軸（横軸）】を「$colHeader」($xRangeAddr / ${newLabels.size}項目) に設定しました"
        }
    )
    return true
}

/**
 * Toggles or sets `colIndex` as a Y-Axis (`縦軸・データ系列`) column on the target chart.
 * - When `exclusive == true`, replaces existing Y-axis series with just this column (or replaces the selected series).
 * - When `exclusive == false`, toggles this column in/out of the chart's Y-axis series list (multi-series support).
 */
fun MasuSheetViewModel.onToggleChartYAxisColumn(
    objectId: String? = null,
    colIndex: Int,
    exclusive: Boolean = false,
    customTableRangeText: String = ""
): Boolean {
    val cur = _uiState.value
    val vp = cur.viewport
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return false
    val bounds = getChartSourceTableBounds(resolvedId, customTableRangeText)
    val hdrR = bounds[0]
    val rStart = bounds[1]
    val rEnd = bounds[2]
    val cStart = bounds[3]
    val cEnd = bounds[4]
    val targetCol = colIndex.coerceIn(0, (vp.colWidths.size - 1).coerceAtLeast(0))

    val validRows = (rStart..rEnd).filter { r ->
        val firstColTxt = vp.cellAt(r, cStart)?.displayText?.trim().orEmpty()
        firstColTxt !in setOf("合計", "総計", "小計", "平均", "Total", "Grand Total", "Subtotal", "Average") &&
            (cStart..cEnd).any { cc -> !vp.cellAt(r, cc)?.displayText.isNullOrBlank() }
    }.ifEmpty { (rStart..rEnd).toList() }

    val seriesName = resolveChartColumnHeaderName(vp, hdrR, rStart, targetCol)
    val seriesValues = validRows.map { r -> parseNumericCell(vp.cellAt(r, targetCol)) ?: 0f }
    if (seriesValues.isEmpty()) return false

    val rFirst = validRows.first()
    val rLast = validRows.last()
    val yRangeAddr = "${MasuSheetConfig.formatCellAddress(rFirst, targetCol)}:${MasuSheetConfig.formatCellAddress(rLast, targetCol)}"
    val nameRef = if (hdrR < rStart) MasuSheetConfig.formatCellAddress(hdrR, targetCol) else ""
    val tableRangeAddr = "${MasuSheetConfig.formatCellAddress(hdrR, minOf(cStart, targetCol))}:${MasuSheetConfig.formatCellAddress(rEnd, maxOf(cEnd, targetCol))}"
    val isEn = cur.language == "en"
    var finalSeriesSummary = seriesName

    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updatedObjects = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
            val resolvedXCol = if (obj.chartXColIndex in cStart..cEnd) {
                obj.chartXColIndex
            } else {
                (cStart..cEnd).firstOrNull { cc ->
                    cc != targetCol && validRows.any { r -> !vp.cellAt(r, cc)?.displayText.isNullOrBlank() && parseNumericCell(vp.cellAt(r, cc)) == null }
                } ?: (cStart..cEnd).firstOrNull { cc -> cc != targetCol } ?: cStart
            }
            val xRangeAddr = "${MasuSheetConfig.formatCellAddress(rFirst, resolvedXCol)}:${MasuSheetConfig.formatCellAddress(rLast, resolvedXCol)}"
            val alignedLabels = if (obj.chartLabels.size == validRows.size && obj.chartXColIndex >= 0) {
                obj.chartLabels
            } else {
                validRows.map { r -> vp.cellAt(r, resolvedXCol)?.displayText?.trim().orEmpty().ifEmpty { "行${r + 1}" } }
            }

            val existingAligned = if (obj.chartSeries.all { it.values.size == validRows.size }) {
                obj.chartSeries
            } else {
                emptyList()
            }
            val existingYCols = if (existingAligned.isNotEmpty() && obj.chartYColIndices.size == existingAligned.size) {
                obj.chartYColIndices
            } else {
                existingAligned.mapNotNull { s ->
                    (cStart..cEnd).firstOrNull { cc -> resolveChartColumnHeaderName(vp, hdrR, rStart, cc) == s.name }
                }
            }
            val builtCandidate = ChartSeries(
                name = seriesName,
                values = seriesValues,
                nameCellRef = nameRef,
                xRangeText = xRangeAddr,
                yRangeText = yRangeAddr,
                xLabels = alignedLabels
            )
            val selectedSIdx = obj.selectedSubElement.removePrefix("series:").toIntOrNull()
            val (newSeriesList, nextYCols) = if (exclusive && selectedSIdx != null && selectedSIdx in existingAligned.indices && existingAligned.size > 1) {
                val replaced = existingAligned.mapIndexed { idx, s ->
                    if (idx == selectedSIdx) {
                        builtCandidate.copy(
                            color = s.color,
                            xRangeText = s.xRangeText.ifBlank { xRangeAddr },
                            xLabels = s.xLabels.ifEmpty { alignedLabels }
                        )
                    } else {
                        s
                    }
                }
                val replacedCols = existingYCols.toMutableList().apply {
                    if (selectedSIdx in indices) this[selectedSIdx] = targetCol
                }
                replaced to replacedCols
            } else if (exclusive || existingAligned.isEmpty()) {
                listOf(builtCandidate) to listOf(targetCol)
            } else {
                val alreadyHas = targetCol in existingYCols || existingAligned.any { it.name == seriesName }
                if (alreadyHas && existingAligned.size > 1) {
                    val filteredPairs = existingAligned.mapIndexedNotNull { idx, s ->
                        val cIdx = existingYCols.getOrNull(idx)
                        if (cIdx == targetCol || s.name == seriesName) null else (s to cIdx)
                    }
                    filteredPairs.map { it.first } to filteredPairs.mapNotNull { it.second }
                } else if (alreadyHas) {
                    existingAligned.map {
                        if (it.name == seriesName) builtCandidate.copy(color = it.color) else it
                    } to existingYCols.ifEmpty { listOf(targetCol) }
                } else {
                    (existingAligned + builtCandidate) to (existingYCols + targetCol).distinct()
                }
            }
            finalSeriesSummary = newSeriesList.joinToString(", ") { it.name }
            val spec = SpreadsheetChartTypeCatalog.findSpec(obj.subType)
            val typeTitle = if (isEn) spec.labelEn else spec.labelJa
            val nextTitle = "$typeTitle: ${if (newSeriesList.size == 1) newSeriesList.first().name else finalSeriesSummary}"
            obj.copy(
                title = nextTitle,
                text = nextTitle,
                chartLabels = alignedLabels,
                chartSeries = newSeriesList,
                chartValues = newSeriesList.first().values,
                sourceRangeText = tableRangeAddr,
                chartXColIndex = resolvedXCol,
                chartYColIndices = nextYCols
            )
        } else {
            obj
        }
    }

    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updatedObjects,
        selectedObjectId = resolvedId,
        statusNoticeMessage = if (isEn) {
            "Updated Chart Y-Axis (Vertical) series: $finalSeriesSummary ($yRangeAddr)"
        } else {
            "グラフの【Y軸（縦軸）】データ系列を「$finalSeriesSummary」($yRangeAddr) に設定しました"
        }
    )
    return true
}

/**
 * Atomically applies both the selected X-Axis column (`xColIndex`) and one or more Y-Axis columns (`yColIndices`)
 * from the table to the target chart!
 */
fun MasuSheetViewModel.onApplyChartXYColumns(
    objectId: String? = null,
    xColIndex: Int,
    yColIndices: List<Int>,
    customTableRangeText: String = "",
    closeDialog: Boolean = false
): Boolean {
    val cur = _uiState.value
    val vp = cur.viewport
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return false
    if (yColIndices.isEmpty()) return onSelectChartXAxisColumn(resolvedId, xColIndex, customTableRangeText)

    val bounds = getChartSourceTableBounds(resolvedId, customTableRangeText)
    val hdrR = bounds[0]
    val rStart = bounds[1]
    val rEnd = bounds[2]
    val cStart = bounds[3]
    val cEnd = bounds[4]

    val validRows = (rStart..rEnd).filter { r ->
        val firstColTxt = vp.cellAt(r, cStart)?.displayText?.trim().orEmpty()
        firstColTxt !in setOf("合計", "総計", "小計", "平均", "Total", "Grand Total", "Subtotal", "Average") &&
            (cStart..cEnd).any { cc -> !vp.cellAt(r, cc)?.displayText.isNullOrBlank() }
    }.ifEmpty { (rStart..rEnd).toList() }

    val maxCol = (vp.colWidths.size - 1).coerceAtLeast(0)
    val safeXCol = xColIndex.coerceIn(0, maxCol)
    val distinctYCols = yColIndices.distinct().map { it.coerceIn(0, maxCol) }
    val rFirst = validRows.first()
    val rLast = validRows.last()
    val xRangeAddr = "${MasuSheetConfig.formatCellAddress(rFirst, safeXCol)}:${MasuSheetConfig.formatCellAddress(rLast, safeXCol)}"
    val newLabels = validRows.map { r ->
        vp.cellAt(r, safeXCol)?.displayText?.trim().orEmpty().ifEmpty { "行${r + 1}" }
    }
    val newSeries = distinctYCols.map { yc ->
        val sName = resolveChartColumnHeaderName(vp, hdrR, rStart, yc)
        val sVals = validRows.map { r -> parseNumericCell(vp.cellAt(r, yc)) ?: 0f }
        val yRangeAddr = "${MasuSheetConfig.formatCellAddress(rFirst, yc)}:${MasuSheetConfig.formatCellAddress(rLast, yc)}"
        val nameRef = if (hdrR < rStart) MasuSheetConfig.formatCellAddress(hdrR, yc) else ""
        ChartSeries(
            name = sName,
            values = sVals,
            nameCellRef = nameRef,
            xRangeText = xRangeAddr,
            yRangeText = yRangeAddr,
            xLabels = newLabels
        )
    }
    if (newLabels.isEmpty() || newSeries.isEmpty()) return false

    val minCol = minOf(cStart, safeXCol, distinctYCols.minOrNull() ?: safeXCol)
    val maxUsedCol = maxOf(cEnd, safeXCol, distinctYCols.maxOrNull() ?: safeXCol)
    val rangeStr = "${MasuSheetConfig.formatCellAddress(hdrR, minCol)}:${MasuSheetConfig.formatCellAddress(rEnd, maxUsedCol)}"
    val xHeader = resolveChartColumnHeaderName(vp, hdrR, rStart, safeXCol)
    val ySummary = newSeries.joinToString(", ") { it.name }
    val isEn = cur.language == "en"

    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updatedObjects = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
            val spec = SpreadsheetChartTypeCatalog.findSpec(obj.subType)
            val typeTitle = if (isEn) spec.labelEn else spec.labelJa
            val nextTitle = "$typeTitle: $ySummary (X軸: $xHeader)"
            obj.copy(
                title = nextTitle,
                text = nextTitle,
                chartLabels = newLabels,
                chartSeries = newSeries,
                chartValues = newSeries.first().values,
                sourceRangeText = rangeStr,
                chartXColIndex = safeXCol,
                chartYColIndices = distinctYCols
            )
        } else {
            obj
        }
    }
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updatedObjects,
        selectedObjectId = resolvedId,
        isChartSelectDataDialogOpen = if (closeDialog) false else cur.isChartSelectDataDialogOpen,
        statusNoticeMessage = if (isEn) {
            "Updated Chart X-Axis ($xHeader) and Y-Axis ($ySummary)"
        } else {
            "グラフの【X軸: $xHeader】・【Y軸: $ySummary】を反映しました"
        }
    )
    return true
}

/**
 * [EN] Binds the currently selected cell range (or column) on the sheet as the target chart's X-Axis (`横軸・項目軸ラベル`).
 *      When [preserveExactBounds] is `true` (e.g., dragging or resizing the X-Axis frame on the sheet),
 *      the exact `r0..r1, c0..c1` bounds are preserved without stripping blank or header rows.
 * [JA] シート上の選択範囲（またはドラッグ・リサイズされた枠）を対象グラフのX軸（横軸・項目軸）として設定します。
 *      [preserveExactBounds] が `true` の場合は、枠の行数・列数を削らず指定範囲そのままを維持します。
 */
fun MasuSheetViewModel.onBindSelectionAsChartXAxis(
    objectId: String? = null,
    rangeTextOverride: String = "",
    preserveExactBounds: Boolean = false
): Boolean {
    val cur = _uiState.value
    val vp = cur.viewport
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return false

    val coords: IntArray = if (rangeTextOverride.isNotBlank()) {
        val clean = rangeTextOverride.trim().removePrefix("=").substringAfter('!').trim()
        val parts = clean.split(':', limit = 2)
        val p0 = parseSingleCellRef(parts[0])
        val p1 = if (parts.size >= 2) parseSingleCellRef(parts[1]) else p0
        if (p0 != null && p1 != null) {
            intArrayOf(
                minOf(p0.first, p1.first).coerceAtLeast(0),
                minOf(p0.second, p1.second).coerceAtLeast(0),
                maxOf(p0.first, p1.first).coerceAtLeast(0),
                maxOf(p0.second, p1.second).coerceAtLeast(0)
            )
        } else {
            return false
        }
    } else {
        intArrayOf(
            minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0),
            minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0),
            maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0),
            maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
        )
    }

    val r0 = coords[0]
    val c0 = coords[1]
    val r1 = coords[2]
    val c1 = coords[3]

    // If a single cell or an entire column was selected from the ribbon/dialog (not a direct frame drag), delegate to column X-axis binding!
    val maxSheetRow = (vp.rowHeights.size - 1).coerceAtLeast(0)
    if (!preserveExactBounds && ((r0 == r1 && c0 == c1) || (c0 == c1 && r0 == 0 && r1 >= maxSheetRow - 1))) {
        return onSelectChartXAxisColumn(resolvedId, c0)
    }

    val isHorizontal = (r0 == r1 && c1 > c0)
    val rawCells: List<Pair<Int, String>> = if (isHorizontal) {
        (c0..c1).map { c -> c to vp.cellAt(r0, c)?.displayText?.trim().orEmpty() }
    } else {
        (r0..r1).map { r -> r to vp.cellAt(r, c0)?.displayText?.trim().orEmpty() }
    }

    val effectiveStartRow: Int
    val effectiveEndRow: Int
    val effectiveStartCol: Int
    val effectiveEndCol: Int
    val labels: List<String>

    if (preserveExactBounds) {
        effectiveStartRow = r0
        effectiveEndRow = r1
        effectiveStartCol = c0
        effectiveEndCol = c1
        labels = rawCells.mapIndexed { idx, (_, txt) -> txt.ifEmpty { "${idx + 1}" } }
    } else {
        val nonEmpty = rawCells.filter { it.second.isNotEmpty() }
        if (nonEmpty.isEmpty()) return false

        val chartObj = cur.sheetObjects.firstOrNull { it.id == resolvedId && it.kind == SheetObjectKind.CHART }
        val targetCount = chartObj?.chartSeries?.firstOrNull()?.values?.size ?: 0
        // Strip header row or footer `合計`/`総計` row if present
        var filtered = nonEmpty.filterNot { (_, txt) ->
            txt in setOf("合計", "総計", "小計", "平均", "Total", "Grand Total", "Subtotal", "Average")
        }
        val firstRowHasTextOnly = !isHorizontal && parseNumericCell(vp.cellAt(filtered.first().first, c0)) == null
        if (filtered.size > 1 && ((targetCount > 0 && filtered.size == targetCount + 1 && firstRowHasTextOnly) || (!isHorizontal && isTableHeaderCell(vp, filtered.first().first, c0)))) {
            filtered = filtered.drop(1)
        }
        labels = filtered.map { it.second }
        if (labels.isEmpty()) return false

        effectiveStartRow = if (!isHorizontal) filtered.first().first else r0
        effectiveEndRow = if (!isHorizontal) filtered.last().first else r1
        effectiveStartCol = if (isHorizontal) filtered.first().first else c0
        effectiveEndCol = if (isHorizontal) filtered.last().first else c1
    }
    val rangeLabel = "${MasuSheetConfig.formatCellAddress(effectiveStartRow, effectiveStartCol)}:${MasuSheetConfig.formatCellAddress(effectiveEndRow, effectiveEndCol)}"

    val detectedBounds = detectTableBoundsAt(vp, r0, c0)
    val fullTableRange = "${MasuSheetConfig.formatCellAddress(minOf(r0, detectedBounds[0]), minOf(c0, detectedBounds[3]))}:${MasuSheetConfig.formatCellAddress(maxOf(r1, detectedBounds[2]), maxOf(c1, detectedBounds[4]))}"
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updatedObjects = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
            val nextSeries = obj.chartSeries.map { s ->
                s.copy(xRangeText = rangeLabel, xLabels = labels)
            }
            obj.copy(
                chartLabels = labels,
                chartSeries = nextSeries,
                chartXColIndex = if (!isHorizontal) c0 else obj.chartXColIndex,
                sourceRangeText = fullTableRange
            )
        } else {
            obj
        }
    }
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updatedObjects,
        selectedObjectId = resolvedId,
        viewport = decorateWithCustomSizes(cur.viewport.copy(isDirty = true)),
        statusNoticeMessage = if (isEn) {
            "Set Chart X-Axis (Horizontal) to range $rangeLabel (${labels.size} items)"
        } else {
            "選択範囲 ($rangeLabel) をグラフの【X軸（横軸）】に設定しました (${labels.size}項目: ${labels.take(3).joinToString(", ")}${if (labels.size > 3) "..." else ""})"
        }
    )
    return true
}

/**
 * Binds the currently selected cell range (or column(s)) on the sheet as the target chart's Y-Axis (`縦軸・数値系列`).
 * Supports both overwriting (`append = false`) and adding an extra series (`append = true`).
 */
fun MasuSheetViewModel.onBindSelectionAsChartYAxis(
    objectId: String? = null,
    append: Boolean = false,
    rangeTextOverride: String = ""
): Boolean {
    val cur = _uiState.value
    val vp = cur.viewport
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return false

    val coords: IntArray = if (rangeTextOverride.isNotBlank()) {
        val clean = rangeTextOverride.trim().removePrefix("=").substringAfter('!').trim()
        val parts = clean.split(':', limit = 2)
        val p0 = parseSingleCellRef(parts[0])
        val p1 = if (parts.size >= 2) parseSingleCellRef(parts[1]) else p0
        if (p0 != null && p1 != null) {
            intArrayOf(
                minOf(p0.first, p1.first).coerceAtLeast(0),
                minOf(p0.second, p1.second).coerceAtLeast(0),
                maxOf(p0.first, p1.first).coerceAtLeast(0),
                maxOf(p0.second, p1.second).coerceAtLeast(0)
            )
        } else {
            return false
        }
    } else {
        intArrayOf(
            minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0),
            minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0),
            maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0),
            maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
        )
    }

    val r0 = coords[0]
    val c0 = coords[1]
    val r1 = coords[2]
    val c1 = coords[3]

    // If a single cell or an entire column was selected, use table column Y-axis binding!
    val maxSheetRow = (vp.rowHeights.size - 1).coerceAtLeast(0)
    if ((r0 == r1 && c0 == c1) || (c0 == c1 && r0 == 0 && r1 >= maxSheetRow - 1)) {
        if (append && rangeTextOverride.isBlank()) {
            return onAddChartSeriesFromSheet(resolvedId)
        }
        return onToggleChartYAxisColumn(resolvedId, c0, exclusive = !append)
    }

    val extractedSeries = mutableListOf<ChartSeries>()
    val extractedYCols = mutableListOf<Int>()
    var inferredLabels: List<String>? = null
    var inferredXCol = -1
    var inferredXRange = ""
    val detectedBounds = detectTableBoundsAt(vp, r0, c0)
    val fullTableRange = "${MasuSheetConfig.formatCellAddress(minOf(r0, detectedBounds[0]), minOf(c0, detectedBounds[3]))}:${MasuSheetConfig.formatCellAddress(maxOf(r1, detectedBounds[2]), maxOf(c1, detectedBounds[4]))}"

    if (r0 == r1 && c1 > c0) {
        // Horizontal single-row series selection
        val vals = mutableListOf<Float>()
        val cats = mutableListOf<String>()
        val numCols = mutableListOf<Int>()
        var sName = ""
        var nameRef = ""
        var hdrRowFound = -1
        for (c in c0..c1) {
            val cell = vp.cellAt(r0, c)
            val num = parseNumericCell(cell)
            if (num != null) {
                vals.add(num)
                numCols.add(c)
                val hdrPair = ((r0 - 1) downTo 0).firstNotNullOfOrNull { hr ->
                    vp.cellAt(hr, c)?.displayText?.trim()?.takeIf { it.isNotEmpty() && parseNumericCell(vp.cellAt(hr, c)) == null }?.let { hr to it }
                }
                if (hdrPair != null && hdrRowFound < 0) hdrRowFound = hdrPair.first
                cats.add(hdrPair?.second ?: MasuSheetConfig.formatColumnLabel(c))
            } else {
                val txt = cell?.displayText?.trim().orEmpty()
                if (txt.isNotEmpty() && sName.isEmpty()) {
                    sName = txt
                    nameRef = MasuSheetConfig.formatCellAddress(r0, c)
                }
            }
        }
        if (sName.isEmpty()) {
            for (lc in (c0 - 1) downTo 0) {
                val lTxt = vp.cellAt(r0, lc)?.displayText?.trim().orEmpty()
                if (lTxt.isNotEmpty()) {
                    sName = lTxt
                    nameRef = MasuSheetConfig.formatCellAddress(r0, lc)
                    break
                }
            }
            if (sName.isEmpty()) sName = "行${r0 + 1}"
        }
        if (vals.isNotEmpty()) {
            val cMin = numCols.firstOrNull() ?: c0
            val cMax = numCols.lastOrNull() ?: c1
            val yAddr = "${MasuSheetConfig.formatCellAddress(r0, cMin)}:${MasuSheetConfig.formatCellAddress(r0, cMax)}"
            val xAddr = if (hdrRowFound >= 0) {
                "${MasuSheetConfig.formatCellAddress(hdrRowFound, cMin)}:${MasuSheetConfig.formatCellAddress(hdrRowFound, cMax)}"
            } else {
                ""
            }
            inferredXRange = xAddr
            extractedSeries.add(
                ChartSeries(
                    name = sName,
                    values = vals,
                    nameCellRef = nameRef,
                    xRangeText = xAddr,
                    yRangeText = yAddr,
                    xLabels = cats
                )
            )
            inferredLabels = cats
        }
    } else {
        // Vertical 1-or-more column selection (r0..r1 across c0..c1)
        val tableStartCol = detectedBounds[3]
        val topRowHasText = r1 > r0 && (c0..c1).any { c ->
            val cell = vp.cellAt(r0, c)
            !cell?.displayText.isNullOrBlank() && parseNumericCell(cell) == null
        }
        val firstNumR = if (topRowHasText) {
            ((r0 + 1)..r1).firstOrNull { r ->
                (c0..c1).any { c -> parseNumericCell(vp.cellAt(r, c)) != null }
            } ?: r0
        } else {
            (r0..r1).firstOrNull { r ->
                (c0..c1).any { c -> parseNumericCell(vp.cellAt(r, c)) != null }
            } ?: r0
        }
        val hdrR = ((firstNumR - 1) downTo maxOf(0, r0 - 1)).firstOrNull { hr ->
            (c0..c1).any { c -> !vp.cellAt(hr, c)?.displayText.isNullOrBlank() && parseNumericCell(vp.cellAt(hr, c)) == null }
        } ?: detectedBounds[0]

        val dataRows = (firstNumR..r1).filter { r ->
            val firstTxt = vp.cellAt(r, tableStartCol)?.displayText?.trim().orEmpty()
            firstTxt !in setOf("合計", "総計", "小計", "平均", "Total", "Grand Total", "Subtotal", "Average") &&
                (c0..c1).any { c -> parseNumericCell(vp.cellAt(r, c)) != null }
        }

        if (dataRows.isNotEmpty()) {
            val rFirst = dataRows.first()
            val rLast = dataRows.last()
            val labelCol = (tableStartCol..detectedBounds[4]).firstOrNull { lc ->
                lc !in c0..c1 && dataRows.any { r -> !vp.cellAt(r, lc)?.displayText.isNullOrBlank() && parseNumericCell(vp.cellAt(r, lc)) == null }
            } ?: (tableStartCol..detectedBounds[4]).firstOrNull { lc -> lc !in c0..c1 } ?: tableStartCol
            inferredXCol = labelCol
            inferredXRange = "${MasuSheetConfig.formatCellAddress(rFirst, labelCol)}:${MasuSheetConfig.formatCellAddress(rLast, labelCol)}"
            inferredLabels = dataRows.map { r ->
                vp.cellAt(r, labelCol)?.displayText?.trim().orEmpty().ifEmpty { "行${r + 1}" }
            }

            for (c in c0..c1) {
                val hasNums = dataRows.any { r -> parseNumericCell(vp.cellAt(r, c)) != null }
                if (!hasNums && c1 > c0) continue
                val sName = resolveChartColumnHeaderName(vp, hdrR, firstNumR, c)
                val sVals = dataRows.map { r -> parseNumericCell(vp.cellAt(r, c)) ?: 0f }
                if (sVals.isNotEmpty()) {
                    val yAddr = "${MasuSheetConfig.formatCellAddress(rFirst, c)}:${MasuSheetConfig.formatCellAddress(rLast, c)}"
                    val nameRef = if (hdrR < firstNumR) MasuSheetConfig.formatCellAddress(hdrR, c) else ""
                    extractedSeries.add(
                        ChartSeries(
                            name = sName,
                            values = sVals,
                            nameCellRef = nameRef,
                            xRangeText = inferredXRange,
                            yRangeText = yAddr,
                            xLabels = inferredLabels.orEmpty()
                        )
                    )
                    extractedYCols.add(c)
                }
            }
        }
    }

    if (extractedSeries.isEmpty()) return false

    val rangeLabel = "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c1)}"
    val isEn = cur.language == "en"
    var finalSummary = ""

    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updatedObjects = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
            val selSIdx = obj.selectedSubElement.removePrefix("series:").toIntOrNull()
            val mergedSeries = if (!append && selSIdx != null && selSIdx in obj.chartSeries.indices && extractedSeries.size == 1 && obj.chartSeries.size > 1) {
                val ns = extractedSeries.first()
                obj.chartSeries.mapIndexed { idx, s ->
                    if (idx == selSIdx) {
                        ns.copy(
                            color = s.color,
                            xRangeText = s.xRangeText.ifBlank { ns.xRangeText },
                            xLabels = s.xLabels.ifEmpty { ns.xLabels }
                        )
                    } else {
                        s
                    }
                }
            } else if (append && obj.chartSeries.isNotEmpty()) {
                val base = obj.chartSeries.toMutableList()
                val defaultXRange = base.first().xRangeText.ifBlank { inferredXRange }
                val defaultXLabels = base.first().xLabels.ifEmpty { obj.chartLabels }
                for (ns in extractedSeries) {
                    val alignedNs = ns.copy(
                        xRangeText = ns.xRangeText.ifBlank { defaultXRange },
                        xLabels = ns.xLabels.ifEmpty { defaultXLabels }
                    )
                    val existingIdx = base.indexOfFirst { it.name == alignedNs.name && it.yRangeText == alignedNs.yRangeText }
                    if (existingIdx >= 0) base[existingIdx] = alignedNs else base.add(alignedNs)
                }
                base
            } else {
                val existingXRange = obj.chartSeries.firstOrNull()?.xRangeText.orEmpty()
                val existingXLabels = obj.chartLabels
                extractedSeries.map { ns ->
                    if (existingXLabels.size == ns.values.size && existingXRange.isNotBlank()) {
                        ns.copy(xRangeText = existingXRange, xLabels = existingXLabels)
                    } else {
                        ns
                    }
                }
            }
            val mergedYCols = if (append && obj.chartYColIndices.isNotEmpty()) {
                (obj.chartYColIndices + extractedYCols).distinct()
            } else {
                extractedYCols
            }
            val targetLen = mergedSeries.first().values.size
            val finalLabels = if (obj.chartLabels.size == targetLen) {
                obj.chartLabels
            } else {
                inferredLabels?.takeIf { it.size == targetLen }
                    ?: (1..targetLen).map { idx -> obj.chartLabels.getOrElse(idx - 1) { "項目$idx" } }
            }
            finalSummary = mergedSeries.joinToString(", ") { it.name }
            val spec = SpreadsheetChartTypeCatalog.findSpec(obj.subType)
            val typeTitle = if (isEn) spec.labelEn else spec.labelJa
            val nextTitle = "$typeTitle: $finalSummary"
            obj.copy(
                title = nextTitle,
                text = nextTitle,
                chartLabels = finalLabels,
                chartSeries = mergedSeries,
                chartValues = mergedSeries.first().values,
                sourceRangeText = fullTableRange,
                chartXColIndex = if (obj.chartXColIndex >= 0) obj.chartXColIndex else inferredXCol,
                chartYColIndices = mergedYCols
            )
        } else {
            obj
        }
    }

    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updatedObjects,
        selectedObjectId = resolvedId,
        statusNoticeMessage = if (isEn) {
            "Set Chart Y-Axis (Vertical) from $rangeLabel ($finalSummary)"
        } else {
            "選択範囲 ($rangeLabel) をグラフの【Y軸（縦軸）】に反映しました ($finalSummary)"
        }
    )
    return true
}

/**
 * Resolves the 0-based sheet cell bounds `[startRow, startCol, endRow, endCol]` for `seriesIndex`'s
 * X-Axis range (`isXAxis = true`) or Y-Axis range (`isXAxis = false`).
 */
fun MasuSheetViewModel.resolveChartSeriesAxisBounds(
    chartObj: NativeSheetObject,
    seriesIndex: Int = 0,
    isXAxis: Boolean = false
): IntArray? {
    if (chartObj.kind != SheetObjectKind.CHART) return null
    val safeIdx = seriesIndex.coerceIn(0, (chartObj.chartSeries.size - 1).coerceAtLeast(0))
    val series = chartObj.chartSeries.getOrNull(safeIdx)
    val rawRange = if (isXAxis) series?.xRangeText.orEmpty() else series?.yRangeText.orEmpty()
    if (rawRange.isNotBlank()) {
        val clean = rawRange.trim().removePrefix("=").substringAfter('!').trim()
        val parts = clean.split(':', limit = 2)
        val p0 = parseSingleCellRef(parts[0])
        val p1 = if (parts.size >= 2) parseSingleCellRef(parts[1]) else p0
        if (p0 != null && p1 != null) {
            return intArrayOf(
                minOf(p0.first, p1.first).coerceAtLeast(0),
                minOf(p0.second, p1.second).coerceAtLeast(0),
                maxOf(p0.first, p1.first).coerceAtLeast(0),
                maxOf(p0.second, p1.second).coerceAtLeast(0)
            )
        }
    }
    val bounds = getChartSourceTableBounds(chartObj.id, chartObj.sourceRangeText)
    val rStart = bounds[1]
    val rEnd = bounds[2]
    val cStart = bounds[3]
    val cEnd = bounds[4]
    return if (isXAxis) {
        val xCol = if (chartObj.chartXColIndex >= 0) chartObj.chartXColIndex else cStart
        intArrayOf(rStart, xCol, rEnd, xCol)
    } else {
        val yCol = chartObj.chartYColIndices.getOrNull(safeIdx)
            ?: (if (chartObj.chartXColIndex == cStart) (cStart + 1 + safeIdx).coerceAtMost(cEnd) else (cStart + safeIdx).coerceAtMost(cEnd))
        intArrayOf(rStart, yCol, rEnd, yCol)
    }
}

/**
 * [EN] ViewModel action/helper `resolveChartSeriesAxisRangeText` handling resolve chart series axis range text while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `resolveChartSeriesAxisRangeText` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.resolveChartSeriesAxisRangeText(
    chartObj: NativeSheetObject,
    seriesIndex: Int = 0,
    isXAxis: Boolean = false
): String {
    val safeIdx = seriesIndex.coerceIn(0, (chartObj.chartSeries.size - 1).coerceAtLeast(0))
    val series = chartObj.chartSeries.getOrNull(safeIdx)
    val explicit = if (isXAxis) series?.xRangeText.orEmpty() else series?.yRangeText.orEmpty()
    if (explicit.isNotBlank()) return explicit
    val b = resolveChartSeriesAxisBounds(chartObj, safeIdx, isXAxis) ?: return ""
    return "${MasuSheetConfig.formatCellAddress(b[0], b[1])}:${MasuSheetConfig.formatCellAddress(b[2], b[3])}"
}
