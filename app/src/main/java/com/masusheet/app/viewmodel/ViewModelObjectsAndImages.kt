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
 * [EN] Floating Shapes, TextBoxes, Clipboard/URI Images, Multi-Select Alignment, & Nudge Operations
 * Manages shape/textbox insertion, fill/stroke/per-element text colors, rotation, safe image insertion & downsampling (`C-09`), `Shift` multi-select, `1pt` nudge (`M-08`), alignment, and combined PNG copy.
 *
 * ---
 * [JA / 日本語解説]
 * 図形・テキストボックス挿入、塗りつぶし・枠線・要素別文字色、回転、安全な画像挿入と縮小 (`C-09`)、複数選択・整列・矢印1pt微調整 (`M-08`)・統合PNGコピーを担うモジュールです。
 * ============================================================================
 */

/**
 * [EN] ViewModel action/helper `onSelectSheetObject` handling select sheet object while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSelectSheetObject` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSelectSheetObject(objectId: String?, appendToggle: Boolean = false) {
    if (objectId != null && objectId.startsWith("dashboard_chart_")) {
        ensureDefaultSalesDashboardChartsMaterialized()
    }
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val validIdsOnCurrentSheet = cur.sheetObjects.filter { it.sheetIndex == sIdx }.map { it.id }.toSet()
    val nextSelectedIds: Set<String> = when {
        objectId == null -> emptySet()
        appendToggle -> {
            val base = cur.effectiveSelectedObjectIds.intersect(validIdsOnCurrentSheet).toMutableSet()
            if (objectId in base && base.size > 1) {
                base.remove(objectId)
            } else {
                base.add(objectId)
            }
            base
        }
        else -> setOf(objectId)
    }
    val primaryId = when {
        objectId != null && objectId in nextSelectedIds -> objectId
        else -> nextSelectedIds.lastOrNull()
    }
    val selectedObj = primaryId?.let { id -> _uiState.value.sheetObjects.firstOrNull { it.id == id } }
    val nextTab = when {
        primaryId == null -> _uiState.value.activeRibbonTab
        selectedObj?.kind == SheetObjectKind.CHART &&
            _uiState.value.activeRibbonTab in listOf("chartDesign", "objectFormat") -> _uiState.value.activeRibbonTab
        else -> "objectFormat"
    }
    _uiState.value = _uiState.value.copy(
        selectedObjectId = primaryId,
        selectedObjectIds = nextSelectedIds,
        activeRibbonTab = nextTab
    )
}

/**
 * [EN] Selects all floating objects (or all pasted images if `imagesOnly = true`) on the current worksheet.
 * [JA] 現在のシート上のすべての図・オブジェクト（`imagesOnly = true` の場合はすべての画像）をまとめて同時選択します。
 */
fun MasuSheetViewModel.onSelectAllSheetObjectsOnCurrentSheet(imagesOnly: Boolean = false) {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val matchingIds = cur.sheetObjects
        .filter { it.sheetIndex == sIdx && (!imagesOnly || it.kind == SheetObjectKind.IMAGE) }
        .map { it.id }
        .toSet()
    if (matchingIds.isEmpty()) return
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        selectedObjectId = matchingIds.lastOrNull(),
        selectedObjectIds = matchingIds,
        activeRibbonTab = "objectFormat",
        statusNoticeMessage = if (isEn) {
            "Selected ${matchingIds.size} objects on sheet (right-click to copy as combined image)"
        } else {
            "シート上の ${matchingIds.size} 個の図をまとめて選択しました（右クリックで1枚の画像としてコピー可能）"
        }
    )
}

/**
 * Ensures the built-in Sales Dashboard charts (`dashboard_chart_revenue_by_region` and `dashboard_chart_share_of_total`)
 * are materialized in `_uiState.value.sheetObjects` when the user selects or recolors them.
 */
internal fun MasuSheetViewModel.ensureDefaultSalesDashboardChartsMaterialized() {
    val vp = _uiState.value.viewport
    val sIdx = vp.sheetIndex
    val matKey = "${_uiState.value.activeWorkbookId}:sales_dashboard:$sIdx"
    if (_uiState.value.sheetObjects.any { it.sheetIndex == sIdx }) {
        materializedSampleChartSheetKeys.add(matKey)
        return
    }
    if (matKey in materializedSampleChartSheetKeys) return
    if (vp.cellAt(1, 1)?.displayText != "Quarterly Sales Dashboard") return
    materializedSampleChartSheetKeys.add(matKey)
    val xStart = vp.colOffsets.getOrElse(1) { 64f }
    val yStart = vp.rowOffsets.getOrElse(11) { 242f } + 4f
    val barChart = NativeSheetObject(
        id = "dashboard_chart_revenue_by_region",
        sheetIndex = sIdx,
        kind = SheetObjectKind.CHART,
        subType = "column",
        xPt = xStart,
        yPt = yStart,
        widthPt = 285f,
        heightPt = 165f,
        title = "Revenue by Region (Q1-Q4)",
        text = "B4:F8",
        fillColor = 0xFFFFFFFF.toInt(),
        strokeColor = 0xFFCBD5E1.toInt(),
        strokeWidthPt = 1.2f,
        chartLabels = listOf("North", "South", "East", "West"),
        chartSeries = listOf(
            ChartSeries("Q1", listOf(128f, 94f, 110f, 86f), 0xFF2563EB.toInt()),
            ChartSeries("Q2", listOf(142f, 108f, 126f, 92f), 0xFF10B981.toInt()),
            ChartSeries("Q3", listOf(136f, 115f, 118f, 98f), 0xFFF59E0B.toInt()),
            ChartSeries("Q4", listOf(164f, 124f, 139f, 112f), 0xFF8B5CF6.toInt())
        )
    )
    val pieChart = NativeSheetObject(
        id = "dashboard_chart_share_of_total",
        sheetIndex = sIdx,
        kind = SheetObjectKind.CHART,
        subType = "doughnut",
        xPt = xStart + 298f,
        yPt = yStart,
        widthPt = 250f,
        heightPt = 165f,
        title = "Share of Total Revenue",
        text = "B5:B8,G5:G8",
        fillColor = 0xFFFFFFFF.toInt(),
        strokeColor = 0xFFCBD5E1.toInt(),
        strokeWidthPt = 1.2f,
        chartLabels = listOf("North", "South", "East", "West"),
        chartSeries = listOf(
            ChartSeries("Total", listOf(570f, 441f, 493f, 388f), 0xFF2563EB.toInt())
        )
    )
    _uiState.value = _uiState.value.copy(
        sheetObjects = _uiState.value.sheetObjects + listOf(barChart, pieChart)
    )
}

/**
 * [EN] ViewModel action/helper `onInsertShape` handling insert shape while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onInsertShape` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onInsertShape(
    shapeKind: String,
    defaultLabel: String = "",
    transparentFill: Boolean = false,
    customStrokeWidthPt: Float = 2.2f
) {
    val vp = _uiState.value.viewport
    val id = "obj_${nextObjectCounter++}"
    val normalizedKind = when (shapeKind.lowercase()) {
        "circle", "ellipse", "oval" -> "ellipse"
        "roundrect", "round_rect", "roundedrect" -> "roundRect"
        "triangle" -> "triangle"
        "diamond" -> "diamond"
        "pentagon" -> "pentagon"
        "hexagon" -> "hexagon"
        "star" -> "star"
        "callout" -> "callout"
        "arrow" -> "arrow"
        "arrow_left", "arrowleft" -> "arrow_left"
        "arrow_up", "arrowup" -> "arrow_up"
        "arrow_down", "arrowdown" -> "arrow_down"
        "line" -> "line"
        else -> "rectangle"
    }
    val xPt = (vp.colOffsets.getOrElse(vp.activeCol) { 72f } + 8f).coerceAtLeast(24f)
    val yPt = (vp.rowOffsets.getOrElse(vp.activeRow) { 28f } + 6f).coerceAtLeast(28f)
    val fillCol = if (transparentFill || normalizedKind == "line") {
        0 // 0 = 中の色を抜く (透明)
    } else {
        when (normalizedKind) {
            "ellipse", "hexagon" -> 0xFF2563EB.toInt()
            "triangle", "star" -> 0xFFD97706.toInt()
            "arrow", "arrow_left", "arrow_up", "arrow_down", "pentagon" -> 0xFF7C3AED.toInt()
            "diamond", "callout" -> 0xFF0891B2.toInt()
            else -> 0xFF1F9D55.toInt()
        }
    }
    val strokeCol = when (normalizedKind) {
        "ellipse", "hexagon" -> 0xFF1D4ED8.toInt()
        "triangle", "star" -> 0xFFB45309.toInt()
        "arrow", "arrow_left", "arrow_up", "arrow_down", "pentagon" -> 0xFF5B21B6.toInt()
        "diamond", "callout" -> 0xFF0E7490.toInt()
        else -> 0xFF15803D.toInt()
    }
    val newObj = NativeSheetObject(
        id = id,
        sheetIndex = vp.sheetIndex,
        kind = SheetObjectKind.SHAPE,
        subType = normalizedKind,
        xPt = xPt,
        yPt = yPt,
        widthPt = 150f,
        heightPt = 84f,
        title = defaultLabel,
        text = defaultLabel,
        fillColor = fillCol,
        strokeColor = strokeCol,
        strokeWidthPt = customStrokeWidthPt
    )
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 1))
    val snap = engineGateway.executeCommand(
        "insert.shape",
        "{\"kind\":\"$normalizedKind\",\"at\":\"${vp.activeAddress}\",\"width\":150,\"height\":84,\"text\":${JSONObject.quote(defaultLabel)}}"
    )
    applyViewportSnapshot(snap)
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = _uiState.value.sheetObjects + newObj,
        selectedObjectId = id,
        activeRibbonTab = "objectFormat"
    )
}

/**
 * [EN] ViewModel action/helper `onInsertTextBox` handling insert text box while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onInsertTextBox` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onInsertTextBox(initialText: String = "") {
    val vp = _uiState.value.viewport
    val id = "obj_${nextObjectCounter++}"
    val xPt = vp.colOffsets.getOrElse(vp.activeCol) { 72f } + 8f
    val yPt = vp.rowOffsets.getOrElse(vp.activeRow) { 22f } + 6f
    val newObj = NativeSheetObject(
        id = id,
        sheetIndex = vp.sheetIndex,
        kind = SheetObjectKind.TEXT_BOX,
        subType = "textBox",
        xPt = xPt,
        yPt = yPt,
        widthPt = 180f,
        heightPt = 76f,
        title = initialText,
        text = initialText,
        fillColor = 0xFFFFFFFF.toInt(),
        strokeColor = 0xFF1F9D55.toInt(),
        strokeWidthPt = 2.0f
    )
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 1))
    val snap = engineGateway.executeCommand(
        "insert.textBox",
        "{\"at\":\"${vp.activeAddress}\",\"width\":180,\"height\":76,\"text\":${JSONObject.quote(initialText)}}"
    )
    applyViewportSnapshot(snap)
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = _uiState.value.sheetObjects + newObj,
        selectedObjectId = id,
        activeRibbonTab = "objectFormat"
    )
}

/**
 * Updates the fill color (`fillColor`) of the currently selected object (or the most recently inserted object).
 * Pass `0` to make the interior transparent (`中の色を抜く / 塗りつぶしなし`).
 */
fun MasuSheetViewModel.onUpdateSelectedObjectFillColor(newFillColor: Int) {
    ensureDefaultSalesDashboardChartsMaterialized()
    val targetId = _uiState.value.selectedObjectId
        ?: _uiState.value.sheetObjects.lastOrNull { it.sheetIndex == _uiState.value.viewport.sheetIndex }?.id
        ?: return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id == targetId) obj.copy(fillColor = newFillColor) else obj
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = targetId,
        viewport = decorateWithCustomSizes(_uiState.value.viewport)
    )
}

/**
 * Updates the border/outline color (`strokeColor`) of the currently selected object.
 * Pass `0` for no border.
 */
fun MasuSheetViewModel.onUpdateSelectedObjectStrokeColor(newStrokeColor: Int) {
    ensureDefaultSalesDashboardChartsMaterialized()
    val targetId = _uiState.value.selectedObjectId
        ?: _uiState.value.sheetObjects.lastOrNull { it.sheetIndex == _uiState.value.viewport.sheetIndex }?.id
        ?: return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id == targetId) {
            obj.copy(
                strokeColor = newStrokeColor,
                strokeWidthPt = if (newStrokeColor != 0 && obj.strokeWidthPt <= 0f) 2.0f else obj.strokeWidthPt
            )
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = targetId,
        viewport = decorateWithCustomSizes(_uiState.value.viewport)
    )
}

/**
 * Updates the border thickness (`strokeWidthPt`) of the currently selected object (`0f` = 枠なし, `1f` = 細線, `2f` = 標準, `4f` = 太線, `6.5f` = 極太).
 */
fun MasuSheetViewModel.onUpdateSelectedObjectStrokeWidth(newStrokeWidthPt: Float) {
    ensureDefaultSalesDashboardChartsMaterialized()
    val targetId = _uiState.value.selectedObjectId
        ?: _uiState.value.sheetObjects.lastOrNull { it.sheetIndex == _uiState.value.viewport.sheetIndex }?.id
        ?: return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val clamped = newStrokeWidthPt.coerceIn(0f, 16f)
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id == targetId) {
            obj.copy(
                strokeWidthPt = clamped,
                strokeColor = if (clamped > 0f && obj.strokeColor == 0) 0xFF1E293B.toInt() else obj.strokeColor
            )
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = targetId,
        viewport = decorateWithCustomSizes(_uiState.value.viewport)
    )
}

/**
 * [EN] Updates the text/label color of the currently selected object (shape, textbox, or chart).
 * For charts, `subElement` can target:
 * - `"all"` (or `""`): sets `textColor` and clears individual overrides so all elements match.
 * - `"title"`: sets only `chartTitleTextColor`.
 * - `"xAxis"`: sets only `chartXAxisTextColor`.
 * - `"yAxis"`: sets only `chartYAxisTextColor`.
 * - `"legend"`: sets only `chartLegendTextColor`.
 * Pass `0` for automatic contrast text color.
 *
 * [JA] 選択中の図形・テキストボックス・グラフ内の文字色を更新します。
 * グラフの場合は `subElement` により「全体 (`all`)」「タイトル (`title`)」「X軸 (`xAxis`)」「Y軸 (`yAxis`)」「凡例・右上表示 (`legend`)」を個別に色指定できます。
 */
fun MasuSheetViewModel.onUpdateSelectedObjectTextColor(newTextColor: Int, subElement: String = "all") {
    ensureDefaultSalesDashboardChartsMaterialized()
    val targetId = _uiState.value.selectedObjectId
        ?: _uiState.value.sheetObjects.lastOrNull { it.sheetIndex == _uiState.value.viewport.sheetIndex }?.id
        ?: return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id == targetId) {
            when (subElement) {
                "title" -> obj.copy(chartTitleTextColor = newTextColor)
                "xAxis" -> obj.copy(chartXAxisTextColor = newTextColor)
                "yAxis" -> obj.copy(chartYAxisTextColor = newTextColor)
                "legend" -> obj.copy(chartLegendTextColor = newTextColor)
                else -> obj.copy(
                    textColor = newTextColor,
                    chartTitleTextColor = newTextColor,
                    chartXAxisTextColor = newTextColor,
                    chartYAxisTextColor = newTextColor,
                    chartLegendTextColor = newTextColor
                )
            }
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = targetId,
        viewport = decorateWithCustomSizes(_uiState.value.viewport)
    )
}

/**
 * Updates the series colors (`chartSeries`) of the currently selected chart (or all charts on the active sheet).
 */
fun MasuSheetViewModel.onUpdateSelectedChartSeriesColor(primaryArgb: Int) {
    ensureDefaultSalesDashboardChartsMaterialized()
    val sIdx = _uiState.value.viewport.sheetIndex
    val baseColor = if (primaryArgb == 0) 0xFF2563EB.toInt() else primaryArgb
    val selId = _uiState.value.selectedObjectId
    val selectedChart = _uiState.value.sheetObjects.firstOrNull { it.id == selId && it.kind == SheetObjectKind.CHART }
    val targetChartIds: Set<String> = if (selectedChart != null) {
        setOf(selectedChart.id)
    } else {
        val allChartsOnSheet = _uiState.value.sheetObjects.filter { it.sheetIndex == sIdx && it.kind == SheetObjectKind.CHART }
        allChartsOnSheet.map { it.id }.toSet()
    }
    if (targetChartIds.isEmpty()) return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val tintFactors = listOf(0.0, 0.28, -0.22, 0.48, -0.38, 0.16)
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id in targetChartIds) {
            val recoloredSeries = if (obj.chartSeries.isEmpty()) {
                listOf(ChartSeries("Series 1", listOf(10f, 20f, 30f), baseColor))
            } else {
                obj.chartSeries.mapIndexed { idx, s ->
                    val c = if (idx == 0) baseColor else SpreadsheetColorPalette.applySpreadsheetTint(baseColor, tintFactors[idx % tintFactors.size])
                    s.copy(color = c)
                }
            }
            obj.copy(chartSeries = recoloredSeries)
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = selectedChart?.id ?: targetChartIds.lastOrNull() ?: selId,
        viewport = decorateWithCustomSizes(_uiState.value.viewport)
    )
}

/**
 * Rotates a specific sheet object (`objectId`) to `rotationDeg` (normalized into `[0f, 360f)`).
 */
fun MasuSheetViewModel.onRotateSheetObject(objectId: String, rotationDeg: Float) {
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val norm = ((rotationDeg % 360f) + 360f) % 360f
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id == objectId) obj.copy(rotationDeg = norm) else obj
    }
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = objectId,
        viewport = decorateWithCustomSizes(_uiState.value.viewport),
        statusNoticeMessage = if (isEn) {
            "Rotated object (${norm.roundToInt()}°)"
        } else {
            "オブジェクトを回転しました (${norm.roundToInt()}°)"
        }
    )
}

/**
 * Rotates the currently selected object (or latest object on the active sheet) by `deltaDeg` degrees
 * (e.g. `+90f` for Rotate Right 90°, `-90f` for Rotate Left 90°, `180f` for Flip Vertical).
 */
fun MasuSheetViewModel.onRotateSelectedObjectBy(deltaDeg: Float) {
    val target = _uiState.value.sheetObjects.firstOrNull { it.id == _uiState.value.selectedObjectId }
        ?: _uiState.value.sheetObjects.lastOrNull { it.sheetIndex == _uiState.value.viewport.sheetIndex }
        ?: return
    onRotateSheetObject(target.id, target.rotationDeg + deltaDeg)
}

/**
 * Resets the rotation of the currently selected object back to `0°`.
 */
fun MasuSheetViewModel.onResetSelectedObjectRotation() {
    val target = _uiState.value.sheetObjects.firstOrNull { it.id == _uiState.value.selectedObjectId }
        ?: _uiState.value.sheetObjects.lastOrNull { it.sheetIndex == _uiState.value.viewport.sheetIndex }
        ?: return
    onRotateSheetObject(target.id, 0f)
}

/**
 * [EN] ViewModel action/helper `parseNumericCell` handling parse numeric cell while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `parseNumericCell` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.parseNumericCell(cell: NativeCellModel?): Float? {
    if (cell == null) return null
    val rawEdit = cell.editText.trim()
    if (rawEdit.isNotEmpty() && !rawEdit.startsWith("=")) {
        MasuSheetConfig.parseFiniteFloatOrNull(rawEdit)?.let { return it }
    }
    val disp = cell.displayText.trim()
    if (disp.isEmpty()) return null
    val isNegParen = disp.startsWith("(") && disp.endsWith(")")
    val cleaned = disp
        .removePrefix("(")
        .removeSuffix(")")
        .replace(",", "")
        .replace("¥", "")
        .replace("￥", "")
        .replace("$", "")
        .replace("€", "")
        .replace("£", "")
        .replace("%", "")
        .replace("円", "")
        .replace("pt", "")
        .replace(" ", "")
        .trim()
    val num = MasuSheetConfig.parseFiniteFloatOrNull(cleaned) ?: return null
    return if (isNegParen) -num else num
}

/**
 * [EN] Pastes an image strictly from the active Android system clipboard (`clipboardImage`).
 *      Never scans device storage or Screenshot/Download directories without explicit user selection (`C-09`).
 * [JA] Androidのシステムクリップボードにある画像のみをシートへ貼り付けます（端末内フォルダの無断走査は行いません）。
 */
fun MasuSheetViewModel.onPasteScreenshotOrLatestImage(
    clipboardImageBytes: ByteArray?,
    clipboardLabel: String = "クリップボード画像.png"
): Boolean {
    return onPasteScreenshotOrLatestImage(
        if (clipboardImageBytes != null && clipboardImageBytes.isNotEmpty()) {
            clipboardLabel to clipboardImageBytes
        } else {
            null
        }
    )
}

/**
 * [EN] ViewModel action/helper `onPasteScreenshotOrLatestImage` handling paste screenshot or latest image while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onPasteScreenshotOrLatestImage` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onPasteScreenshotOrLatestImage(clipboardImage: Pair<String, ByteArray>?): Boolean {
    if (clipboardImage != null && clipboardImage.second.isNotEmpty()) {
        val ok = onInsertImageBytes(clipboardImage.first, clipboardImage.second)
        if (ok) {
            val isEn = _uiState.value.language == "en"
            _uiState.value = _uiState.value.copy(
                statusNoticeMessage = if (isEn) {
                    "Pasted clipboard image (${clipboardImage.first}) onto sheet"
                } else {
                    "クリップボードの画像 (${clipboardImage.first}) をシートに添付しました"
                }
            )
            return true
        }
    }
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        errorMessage = if (isEn) {
            "No image found in clipboard. Copy an image first or use [Insert] > [Picture] to choose a file."
        } else {
            "クリップボード内に画像がありません（画像をコピーしてから貼り付けるか、［挿入］＞［画像］から選択してください）"
        }
    )
    return false
}

/**
 * [EN] ViewModel action/helper `onInsertImageUri` handling insert image uri while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onInsertImageUri` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onInsertImageUri(uriString: String): Boolean {
    val bytes = documentGateway.readBytes(uriString)
    if (bytes == null || bytes.isEmpty()) {
        _uiState.value = _uiState.value.copy(errorMessage = "画像ファイルを読み込めませんでした")
        return false
    }
    val dispName = documentGateway.resolveDisplayName(uriString)
    return onInsertImageBytes(dispName, bytes)
}

/**
 * [EN] ViewModel action/helper `downsampleLargeImageBytesIfNeeded` handling downsample large image bytes if needed while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `downsampleLargeImageBytesIfNeeded` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.downsampleLargeImageBytesIfNeeded(rawBytes: ByteArray, maxEdgePx: Int = 2048): ByteArray {
    val dims = MasuSheetConfig.decodeImageDimensions(rawBytes) ?: return rawBytes
    if (dims.first <= maxEdgePx && dims.second <= maxEdgePx) return rawBytes
    return runCatching {
        var sampleSize = 1
        while ((dims.first / sampleSize) > maxEdgePx * 2 || (dims.second / sampleSize) > maxEdgePx * 2) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts) ?: return rawBytes
        val maxDecoded = maxOf(decoded.width, decoded.height)
        val scaled = if (maxDecoded > maxEdgePx && maxDecoded > 0) {
            val ratio = maxEdgePx.toFloat() / maxDecoded.toFloat()
            val targetW = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
            val targetH = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 92, out)
        scaled.recycle()
        out.toByteArray().takeIf { it.isNotEmpty() } ?: rawBytes
    }.getOrDefault(rawBytes)
}

/**
 * [EN] ViewModel action/helper `onInsertImageBytes` handling insert image bytes while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onInsertImageBytes` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onInsertImageBytes(fileName: String, bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val safeBytes = downsampleLargeImageBytesIfNeeded(bytes)
    val vp = _uiState.value.viewport
    val id = "obj_${nextObjectCounter++}"
    val xPt = vp.colOffsets.getOrElse(vp.activeCol) { 72f } + 6f
    val yPt = vp.rowOffsets.getOrElse(vp.activeRow) { 22f } + 6f
    val (naturalW, naturalH) = MasuSheetConfig.computeNaturalImageSizePt(safeBytes)
    val newObj = NativeSheetObject(
        id = id,
        sheetIndex = vp.sheetIndex,
        kind = SheetObjectKind.IMAGE,
        subType = "image",
        xPt = xPt,
        yPt = yPt,
        widthPt = naturalW,
        heightPt = naturalH,
        title = fileName,
        text = fileName,
        fillColor = 0,
        strokeColor = 0,
        strokeWidthPt = 0f,
        imageBytes = safeBytes
    )
    val b64 = Base64.getEncoder().encodeToString(safeBytes)
    val mime = when {
        fileName.endsWith(".png", ignoreCase = true) -> "image/png"
        fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
        fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
        else -> "image/jpeg"
    }
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 1))
    val snap = engineGateway.executeCommand(
        "insert.picture",
        "{\"base64\":\"$b64\",\"mime\":\"$mime\",\"at\":\"${vp.activeAddress}\",\"width\":${naturalW.roundToInt()},\"height\":${naturalH.roundToInt()}}"
    )
    applyViewportSnapshot(snap)
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = _uiState.value.sheetObjects + newObj,
        selectedObjectId = id,
        selectedObjectIds = setOf(id)
    )
    return true
}

/**
 * Resets the selected (or latest) image object on the current sheet back to its original aspect ratio and natural size.
 */
fun MasuSheetViewModel.onResetSelectedImageAspectRatio(objectId: String? = null): Boolean {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val targetId = objectId
        ?: cur.selectedObjectId
        ?: cur.sheetObjects.lastOrNull { it.sheetIndex == sIdx && it.kind == SheetObjectKind.IMAGE }?.id
        ?: return false
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    var changed = false
    val updated = cur.sheetObjects.map { obj ->
        if (obj.id == targetId && obj.imageBytes != null && obj.imageBytes.isNotEmpty()) {
            val (natW, natH) = MasuSheetConfig.computeNaturalImageSizePt(obj.imageBytes)
            changed = true
            obj.copy(widthPt = natW, heightPt = natH)
        } else {
            obj
        }
    }
    if (changed) {
        val isEn = cur.language == "en"
        _uiState.value = cur.copy(
            isDirty = true,
            sheetObjects = updated,
            selectedObjectId = targetId,
            selectedObjectIds = setOf(targetId),
            viewport = decorateWithCustomSizes(cur.viewport),
            statusNoticeMessage = if (isEn) "Reset image to natural aspect ratio and size" else "画像の縦横比・サイズを元の比率にリセットしました"
        )
    }
    return changed
}

/**
 * [EN] ViewModel action/helper `onMoveOrResizeSheetObject` handling move or resize sheet object while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onMoveOrResizeSheetObject` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onMoveOrResizeSheetObject(objectId: String, newXPt: Float, newYPt: Float, newWidthPt: Float, newHeightPt: Float) {
    val existing = _uiState.value.sheetObjects.firstOrNull { it.id == objectId } ?: return
    val clampedX = newXPt.coerceAtLeast(0f)
    val clampedY = newYPt.coerceAtLeast(0f)
    val clampedW = newWidthPt.coerceIn(20f, 5000f)
    val clampedH = newHeightPt.coerceIn(14f, 5000f)
    if (abs(existing.xPt - clampedX) < 0.01f &&
        abs(existing.yPt - clampedY) < 0.01f &&
        abs(existing.widthPt - clampedW) < 0.01f &&
        abs(existing.heightPt - clampedH) < 0.01f
    ) {
        return
    }
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id == objectId) {
            obj.copy(
                xPt = clampedX,
                yPt = clampedY,
                widthPt = clampedW,
                heightPt = clampedH
            )
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = updated,
        viewport = decorateWithCustomSizes(_uiState.value.viewport)
    )
}

/**
 * [EN] Nudges all currently selected sheet object(s) (`effectiveSelectedObjectIds`) by `(dxPt, dyPt)` points
 * when the user presses keyboard arrow keys (`↑`, `↓`, `←`, `→`), enabling 1pt precision alignment of pasted images.
 * Consecutive nudges within 600ms coalesce into a single Undo entry (`M-08`).
 *
 * ---
 * [JA] キーボードの矢印キー（`↑` / `↓` / `←` / `→`）で選択中の画像・図形を `(dxPt, dyPt)` ポイントずつ微調整移動します。
 * 600ms以内の連続押下は1回のUndo操作にまとめられます。
 */
fun MasuSheetViewModel.onNudgeSelectedSheetObjects(dxPt: Float, dyPt: Float): Boolean {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val targetIds = cur.effectiveSelectedObjectIds
    if (targetIds.isEmpty()) return false
    val matching = cur.sheetObjects.filter { it.sheetIndex == sIdx && it.id in targetIds }
    if (matching.isEmpty()) return false

    val nowMs = System.currentTimeMillis()
    if (nowMs - lastNudgeUndoTimestampMs > 600L || customOverlayUndoStack.isEmpty()) {
        pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    }
    lastNudgeUndoTimestampMs = nowMs
    val updated = cur.sheetObjects.map { obj ->
        if (obj.sheetIndex == sIdx && obj.id in targetIds) {
            obj.copy(
                xPt = (obj.xPt + dxPt).coerceAtLeast(0f),
                yPt = (obj.yPt + dyPt).coerceAtLeast(0f)
            )
        } else {
            obj
        }
    }
    val primary = updated.firstOrNull { it.id == cur.selectedObjectId }
        ?: updated.firstOrNull { it.sheetIndex == sIdx && it.id in targetIds }
    val isEn = cur.language == "en"
    val coordSummary = if (primary != null) {
        "X=${primary.xPt.roundToInt()}pt, Y=${primary.yPt.roundToInt()}pt"
    } else {
        ""
    }
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updated,
        viewport = decorateWithCustomSizes(cur.viewport),
        statusNoticeMessage = if (isEn) {
            "Nudged ${matching.size} object(s) ($coordSummary) — Arrow keys: 1pt, Shift+Arrow: 5pt"
        } else {
            "図の位置を微調整しました ($coordSummary) — 矢印キー: 1pt移動 / Shift+矢印: 5pt移動"
        }
    )
    return true
}

/**
 * [EN] Moves multiple selected sheet objects together in a single undo step when dragging a multi-selection.
 * [JA] `Shift+クリック` で複数選択した画像・図形をドラッグ移動した際、相対位置を保ったまま一括で移動します。
 */
fun MasuSheetViewModel.onMoveMultipleSheetObjects(updatedPositions: Map<String, Pair<Float, Float>>) {
    if (updatedPositions.isEmpty()) return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = _uiState.value.sheetObjects.map { obj ->
        val pos = updatedPositions[obj.id]
        if (pos != null) {
            obj.copy(
                xPt = pos.first.coerceAtLeast(0f),
                yPt = pos.second.coerceAtLeast(0f)
            )
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = updated,
        viewport = decorateWithCustomSizes(_uiState.value.viewport)
    )
}

/**
 * [EN] Aligns 2 or more selected sheet objects (or all images on the current sheet if only 1 is selected):
 * - `"alignTop"`: Aligns top edges (`yPt`) to the top-most selected object.
 * - `"alignMiddle"`: Aligns vertical centers (`yPt + heightPt / 2`).
 * - `"matchHeight"`: Matches `heightPt` (and proportionally scales `widthPt` for images) and aligns `yPt`.
 * - `"arrangeSideBySide"`: Aligns top edges (`yPt`), matches heights, and places them side-by-side horizontally.
 *
 * ---
 * [JA] 複数選択された図・画像（1つのみ選択時は同シート上の複数画像）の高さ位置やサイズを一括で揃えます。
 */
fun MasuSheetViewModel.onAlignSelectedSheetObjects(mode: String): Boolean {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    var targetIds = cur.effectiveSelectedObjectIds.filter { id ->
        cur.sheetObjects.any { it.sheetIndex == sIdx && it.id == id }
    }.toSet()
    if (targetIds.size < 2) {
        val allSheetImageIds = cur.sheetObjects
            .filter { it.sheetIndex == sIdx && it.kind == SheetObjectKind.IMAGE }
            .map { it.id }
            .toSet()
        if (allSheetImageIds.size >= 2) {
            targetIds = allSheetImageIds
        } else {
            val allSheetObjIds = cur.sheetObjects.filter { it.sheetIndex == sIdx }.map { it.id }.toSet()
            if (allSheetObjIds.size >= 2) {
                targetIds = allSheetObjIds
            } else {
                return false
            }
        }
    }
    val targets = cur.sheetObjects.filter { it.sheetIndex == sIdx && it.id in targetIds }
    if (targets.size < 2) return false

    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val refTopY = targets.minOf { it.yPt }
    val refCenterY = targets.map { it.yPt + it.heightPt * 0.5f }.average().toFloat()
    val refHeight = targets.first().heightPt.coerceAtLeast(20f)
    val sortedLeftToRight = targets.sortedBy { it.xPt }
    val sideBySidePositions = mutableMapOf<String, Pair<Float, Float>>()
    var runningX = sortedLeftToRight.first().xPt
    for (t in sortedLeftToRight) {
        val ratio = (t.widthPt / t.heightPt.coerceAtLeast(1f)).coerceIn(0.05f, 20f)
        val w = if (mode == "arrangeSideBySide" && t.kind == SheetObjectKind.IMAGE) refHeight * ratio else t.widthPt
        sideBySidePositions[t.id] = runningX to refTopY
        runningX += w + 6f
    }

    val updated = cur.sheetObjects.map { obj ->
        if (obj.sheetIndex == sIdx && obj.id in targetIds) {
            when (mode) {
                "alignTop" -> obj.copy(yPt = refTopY)
                "alignMiddle" -> obj.copy(yPt = (refCenterY - obj.heightPt * 0.5f).coerceAtLeast(0f))
                "matchHeight" -> {
                    val ratio = (obj.widthPt / obj.heightPt.coerceAtLeast(1f)).coerceIn(0.05f, 20f)
                    val newW = if (obj.kind == SheetObjectKind.IMAGE) (refHeight * ratio).coerceIn(24f, 5000f) else obj.widthPt
                    obj.copy(yPt = refTopY, widthPt = newW, heightPt = refHeight)
                }
                "arrangeSideBySide" -> {
                    val pos = sideBySidePositions[obj.id] ?: (obj.xPt to refTopY)
                    val ratio = (obj.widthPt / obj.heightPt.coerceAtLeast(1f)).coerceIn(0.05f, 20f)
                    val newW = if (obj.kind == SheetObjectKind.IMAGE) (refHeight * ratio).coerceIn(24f, 5000f) else obj.widthPt
                    obj.copy(xPt = pos.first, yPt = pos.second, widthPt = newW, heightPt = refHeight)
                }
                else -> obj.copy(yPt = refTopY)
            }
        } else {
            obj
        }
    }
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = targetIds.lastOrNull(),
        selectedObjectIds = targetIds,
        viewport = decorateWithCustomSizes(cur.viewport),
        statusNoticeMessage = if (isEn) {
            "Aligned ${targets.size} objects (Y=${refTopY.roundToInt()}pt)"
        } else {
            "選択した ${targets.size} 個の図の高さ・位置をぴったり揃えました (Y=${refTopY.roundToInt()}pt)"
        }
    )
    return true
}

/**
 * [EN] Renders all currently selected sheet objects (`effectiveSelectedObjectIds`)—or [targetObjectId]—into a single
 * combined PNG image (`fileName to pngBytes`) so it can be placed onto the Android system clipboard.
 *
 * ---
 * [JA] 現在選択されている複数の図・画像（`Shift+クリック`等で複数選択された画像群、または単一画像）を
 * 1枚の結合PNG画像（`fileName to pngBytes`）に合成し、デバイスのシステムクリップボードへ格納できるように返します。
 */
fun MasuSheetViewModel.onCopySelectedSheetObjectsAsCombinedImage(targetObjectId: String? = null): Pair<String, ByteArray>? {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val activeIds = cur.effectiveSelectedObjectIds
    val resolvedIds: Set<String> = when {
        activeIds.size > 1 && (targetObjectId == null || targetObjectId in activeIds) -> activeIds
        targetObjectId != null -> setOf(targetObjectId)
        activeIds.isNotEmpty() -> activeIds
        else -> emptySet()
    }
    val objs = cur.sheetObjects.filter { it.sheetIndex == sIdx && it.id in resolvedIds }
    if (objs.isEmpty()) return null
    val rendered = MasuSheetConfig.renderSheetObjectsToCombinedPng(objs) ?: return null
    val dims = MasuSheetConfig.decodeImageDimensions(rendered.second)
    val dimText = if (dims != null) "${dims.first}×${dims.second}px" else ""
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        statusNoticeMessage = if (isEn) {
            if (objs.size > 1) {
                "Copied ${objs.size} selected objects as a single combined PNG image ($dimText) to device clipboard"
            } else {
                "Copied object as PNG image ($dimText) to device clipboard"
            }
        } else {
            if (objs.size > 1) {
                "選択した ${objs.size} 個の図をまとめて1枚の画像 ($dimText) としてデバイスのクリップボードにコピーしました"
            } else {
                "図を画像 ($dimText) としてデバイスのクリップボードにコピーしました"
            }
        }
    )
    return rendered
}

/**
 * [EN] ViewModel action/helper `onUpdateSheetObjectText` handling update sheet object text while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onUpdateSheetObjectText` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onUpdateSheetObjectText(objectId: String, newText: String) {
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id == objectId) {
            obj.copy(title = newText, text = newText)
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = updated,
        viewport = decorateWithCustomSizes(_uiState.value.viewport)
    )
}

/**
 * [EN] Deletes a floating sheet object (`objectId`, such as a chart, shape, textbox, or image)
 *      after recording an object-level undo snapshot (`engineStepsCount = 0`) so pressing Undo restores it.
 * [JA] グラフ・図形・テキストボックス・画像などのシートオブジェクト（`objectId`）を削除します。
 *      事前に `engineStepsCount = 0` のUndoスナップショットを記録するため、「元に戻す」で削除したグラフを即座に復活できます。
 */
fun MasuSheetViewModel.onDeleteSheetObject(objectId: String) {
    if (objectId.startsWith("dashboard_chart_")) {
        ensureDefaultSalesDashboardChartsMaterialized()
    }
    val cur = _uiState.value
    if (cur.sheetObjects.none { it.id == objectId }) return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = cur.sheetObjects.filterNot { it.id == objectId }
    val remainingIds = cur.effectiveSelectedObjectIds - objectId
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = if (cur.selectedObjectId == objectId) remainingIds.lastOrNull() else cur.selectedObjectId,
        selectedObjectIds = remainingIds,
        viewport = decorateWithCustomSizes(cur.viewport),
        statusNoticeMessage = if (isEn) {
            "Deleted object (press Undo to restore)"
        } else {
            "オブジェクトを削除しました（「元に戻す」で復活できます）"
        }
    )
}

/**
 * [EN] ViewModel action/helper `onDeleteSelectedSheetObjects` handling delete selected sheet objects while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onDeleteSelectedSheetObjects` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onDeleteSelectedSheetObjects() {
    val cur = _uiState.value
    val targetIds = cur.effectiveSelectedObjectIds
    if (targetIds.isEmpty()) return
    if (targetIds.size == 1) {
        onDeleteSheetObject(targetIds.first())
        return
    }
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = cur.sheetObjects.filterNot { it.id in targetIds }
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = null,
        selectedObjectIds = emptySet(),
        viewport = decorateWithCustomSizes(cur.viewport),
        statusNoticeMessage = if (isEn) {
            "Deleted ${targetIds.size} selected objects (press Undo to restore)"
        } else {
            "選択した ${targetIds.size} 個のオブジェクトを削除しました（「元に戻す」で復活できます）"
        }
    )
}
