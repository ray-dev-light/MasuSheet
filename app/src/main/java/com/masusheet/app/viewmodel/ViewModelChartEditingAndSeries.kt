package com.masusheet.app.viewmodel

import androidx.lifecycle.ViewModel
import com.masusheet.app.catalog.SpreadsheetChartTypeCatalog
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.ChartSeries
import com.masusheet.app.model.DetectedSheetTableRange
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.model.SheetObjectKind
import kotlin.math.abs

/**
 * ============================================================================
 * [EN] Chart Sub-Element Selection, Finite Axis Formatting (`C-07`), Drag Range Resize, & Series Editing
 * Handles chart sub-element selection, NaN/Infinity-safe axis formatting (`C-07`), interactive corner/edge range drag updates, row/column swap, and live series/category edits.
 *
 * ---
 * [JA / 日本語解説]
 * グラフ内要素の個別選択、`NaN`/`Infinity` 安全な軸書式設定 (`C-07`)、シート上での参照範囲ドラッグ移動・拡縮、行列入替、系列・項目ラベル編集を担うモジュールです。
 * ============================================================================
 */

/**
 * [EN] Selects an internal chart sub-element (`""`, `"xAxis"`, `"yAxis"`, or `"series:<idx>"`) and shows its bound cell ranges in the status notice.
 * [JA] グラフ内部の要素（全体・X軸・Y軸・個別データ系列）を選択状態にし、対応するシート参照範囲をステータス通知に表示します。
 */
fun MasuSheetViewModel.onSelectChartSubElement(objectId: String, subElement: String) {
    val cur = _uiState.value
    val chartObj = cur.sheetObjects.firstOrNull { it.id == objectId && it.kind == SheetObjectKind.CHART } ?: return
    val isEn = cur.language == "en"
    val notice = when {
        subElement == "xAxis" -> {
            val xRange = resolveChartSeriesAxisRangeText(chartObj, 0, isXAxis = true)
            val intervalStr = when {
                chartObj.xAxisMajorStep != null -> "${chartObj.xAxisMajorStep}"
                chartObj.xAxisLabelInterval <= 0 -> if (isEn) "Auto" else "自動"
                chartObj.xAxisLabelInterval == 1 -> if (isEn) "Every 1" else "すべて表示(1ごと)"
                else -> if (isEn) "Every ${chartObj.xAxisLabelInterval}" else "${chartObj.xAxisLabelInterval}おき"
            }
            if (isEn) {
                "📏 Selected X-Axis (Range: $xRange, Interval: $intervalStr) — Right-click to adjust display interval"
            } else {
                "📏 【X軸（横軸）】を選択中 (範囲: $xRange / 表示間隔: $intervalStr) — 右クリックで表示間隔を調整可能"
            }
        }
        subElement == "yAxis" -> {
            val stepStr = chartObj.yAxisMajorStep?.let {
                if (it < 0f) {
                    if (isEn) "${kotlin.math.abs(it).toInt()} Divisions" else "${kotlin.math.abs(it).toInt()}分割"
                } else if (it == it.toLong().toFloat()) {
                    java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(it.toLong())
                } else {
                    it.toString()
                }
            } ?: if (isEn) "Auto" else "自動"
            if (isEn) {
                "📐 Selected Y-Axis (Step: $stepStr) — Right-click to adjust display interval / min / max"
            } else {
                "📐 【Y軸（縦軸）】を選択中 (目盛設定: $stepStr) — 右クリックで表示間隔・分割数・最小/最大値を調整可能"
            }
        }
        subElement.startsWith("series:") -> {
            val sIdx = subElement.removePrefix("series:").toIntOrNull() ?: 0
            val s = chartObj.chartSeries.getOrNull(sIdx)
            val sName = s?.name ?: "系列${sIdx + 1}"
            val xRange = resolveChartSeriesAxisRangeText(chartObj, sIdx, isXAxis = true)
            val yRange = resolveChartSeriesAxisRangeText(chartObj, sIdx, isXAxis = false)
            if (isEn) {
                "📈 Selected Series \"$sName\" — X-Axis: $xRange | Y-Axis: $yRange (Drag sheet range frames to change)"
            } else {
                "📈 系列「$sName」を選択中 — 【X軸: $xRange】 ／ 【Y軸: $yRange】 (シート上の色枠をドラッグして範囲を移動可能)"
            }
        }
        else -> cur.statusNoticeMessage
    }
    val updated = cur.sheetObjects.map { obj ->
        if (obj.id == objectId && obj.kind == SheetObjectKind.CHART) {
            obj.copy(selectedSubElement = subElement)
        } else {
            obj
        }
    }
    _uiState.value = cur.copy(
        sheetObjects = updated,
        selectedObjectId = objectId,
        activeRibbonTab = "chartDesign",
        statusNoticeMessage = notice
    )
}

/**
 * [EN] Opens or closes the Chart Axis Format dialog (`min`, `max`, `majorStep`, title, units) for the X or Y axis.
 * [JA] 選択グラフのX軸またはY軸の書式設定ダイアログ（最小値・最大値・目盛間隔・軸ラベル等）を開閉します。
 */
fun MasuSheetViewModel.onOpenChartAxisFormatDialog(objectId: String? = null, axis: String = "y", open: Boolean = true) {
    val cur = _uiState.value
    val resolvedId = if (open) resolveTargetChartObjectId(objectId) else null
    val normAxis = if (axis.lowercase().startsWith("x")) "x" else "y"
    val updatedObjects = if (open && resolvedId != null) {
        cur.sheetObjects.map { obj ->
            if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
                obj.copy(selectedSubElement = if (normAxis == "x") "xAxis" else "yAxis")
            } else {
                obj
            }
        }
    } else {
        cur.sheetObjects
    }
    _uiState.value = cur.copy(
        sheetObjects = updatedObjects,
        isChartAxisFormatDialogOpen = open && resolvedId != null,
        chartAxisFormatTargetAxis = normAxis,
        chartSelectDataTargetId = resolvedId ?: cur.chartSelectDataTargetId,
        selectedObjectId = resolvedId ?: cur.selectedObjectId
    )
}

/**
 * Updates X-Axis or Y-Axis display interval (`labelInterval` / `majorStep`) and bounds (`minVal` / `maxVal`).
 * Note: For `yAxisMajorStep`, a positive value (`> 0f`) sets the exact tick step unit, while a negative value
 * (`< 0f`, e.g. `-5f`) sets the exact number of equal axis divisions (`5分割`).
 */
fun MasuSheetViewModel.onUpdateChartAxisFormat(
    objectId: String? = null,
    isXAxis: Boolean,
    labelInterval: Int? = null,
    majorStep: Float? = null,
    minVal: Float? = null,
    maxVal: Float? = null,
    resetToAuto: Boolean = false,
    closeDialog: Boolean = false
) {
    val cur = _uiState.value
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val isEn = cur.language == "en"
    val updated = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
            if (isXAxis) {
                if (resetToAuto) {
                    obj.copy(
                        selectedSubElement = "xAxis",
                        xAxisLabelInterval = 0,
                        xAxisMajorStep = null,
                        xAxisMin = null,
                        xAxisMax = null
                    )
                } else {
                    obj.copy(
                        selectedSubElement = "xAxis",
                        xAxisLabelInterval = labelInterval?.coerceAtLeast(0) ?: obj.xAxisLabelInterval,
                        xAxisMajorStep = majorStep?.takeIf { it > 0f } ?: if (labelInterval != null && majorStep == null) obj.xAxisMajorStep else null,
                        xAxisMin = minVal,
                        xAxisMax = maxVal
                    )
                }
            } else {
                if (resetToAuto) {
                    obj.copy(
                        selectedSubElement = "yAxis",
                        yAxisMajorStep = null,
                        yAxisMin = null,
                        yAxisMax = null
                    )
                } else {
                    obj.copy(
                        selectedSubElement = "yAxis",
                        yAxisMajorStep = majorStep?.takeIf { it != 0f },
                        yAxisMin = minVal,
                        yAxisMax = maxVal
                    )
                }
            }
        } else {
            obj
        }
    }
    val notice = if (isXAxis) {
        if (resetToAuto) {
            if (isEn) "Reset X-Axis display interval to Auto" else "【X軸（横軸）】の表示間隔・目盛を「自動」に戻しました"
        } else {
            val intervalDesc = when {
                majorStep != null && majorStep > 0f -> "目盛間隔: $majorStep"
                labelInterval != null && labelInterval > 1 -> "${labelInterval}項目おき"
                labelInterval == 1 -> "すべて表示 (1項目ごと)"
                else -> "自動"
            }
            if (isEn) "Updated X-Axis display interval ($intervalDesc)" else "【X軸（横軸）】の表示設定を更新しました ($intervalDesc)"
        }
    } else {
        if (resetToAuto) {
            if (isEn) "Reset Y-Axis scale and interval to Auto" else "【Y軸（縦軸）】の表示間隔・最小/最大値を「自動」に戻しました"
        } else {
            val stepDesc = when {
                majorStep == null -> "間隔: 自動"
                majorStep < 0f -> "目盛分割数: ${kotlin.math.abs(majorStep).toInt()}分割"
                majorStep == majorStep.toLong().toFloat() -> "目盛単位: ${java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(majorStep.toLong())}"
                else -> "目盛単位: $majorStep"
            }
            if (isEn) "Updated Y-Axis display interval ($stepDesc)" else "【Y軸（縦軸）】の表示設定を更新しました ($stepDesc)"
        }
    }
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = resolvedId,
        isChartAxisFormatDialogOpen = if (closeDialog) false else cur.isChartAxisFormatDialogOpen,
        statusNoticeMessage = notice
    )
}

/**
 * [EN] Updates a specific series's name, X-Axis cell range (`newXRangeText`), and/or Y-Axis cell range (`newYRangeText`)
 *      by reading actual worksheet cells.
 *      When [preserveExactBounds] is `true` (e.g., dragging or resizing a range frame on the sheet),
 *      the exact `r0..r1, c0..c1` range is kept without stripping text/blank rows or shrinking the frame,
 *      and the series name is only auto-updated from the cell above `r0` (`r0 - 1`) when moved to a new column.
 * [JA] 指定した系列の名前・X軸範囲・Y軸範囲をシートの実セル値から更新します。
 *      [preserveExactBounds] が `true`（シート上の枠ドラッグ移動・リサイズ時）の場合は、文字セルや空白セルを含んでいても
 *      1行削ったり範囲を縮小したりせず指定範囲をそのまま保持し、系列名は別の列へ移動した時のみ枠の1つ上の行（`r0 - 1`）から取得します。
 */
fun MasuSheetViewModel.onUpdateChartSeriesRanges(
    objectId: String? = null,
    seriesIndex: Int,
    newName: String? = null,
    newXRangeText: String? = null,
    newYRangeText: String? = null,
    closeDialog: Boolean = false,
    preserveExactBounds: Boolean = false
): Boolean {
    val cur = _uiState.value
    val vp = cur.viewport
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return false
    val chartObj = cur.sheetObjects.firstOrNull { it.id == resolvedId && it.kind == SheetObjectKind.CHART } ?: return false
    if (seriesIndex !in chartObj.chartSeries.indices) return false

    val existingSeries = chartObj.chartSeries[seriesIndex]
    val prevYBounds = resolveChartSeriesAxisBounds(chartObj, seriesIndex, isXAxis = false)
    var nextXRange = existingSeries.xRangeText.ifBlank {
        resolveChartSeriesAxisRangeText(chartObj, seriesIndex, isXAxis = true)
    }
    var nextYRange = existingSeries.yRangeText.ifBlank {
        resolveChartSeriesAxisRangeText(chartObj, seriesIndex, isXAxis = false)
    }
    var nextXLabels = existingSeries.xLabels.ifEmpty { chartObj.chartLabels }
    var nextYValues = existingSeries.values
    var nextSeriesName = newName?.takeIf { it.isNotBlank() } ?: existingSeries.name
    var nextNameRef = existingSeries.nameCellRef

    if (!newXRangeText.isNullOrBlank()) {
        val clean = newXRangeText.trim().removePrefix("=").substringAfter('!').trim()
        val parts = clean.split(':', limit = 2)
        val p0 = parseSingleCellRef(parts[0])
        val p1 = if (parts.size >= 2) parseSingleCellRef(parts[1]) else p0
        if (p0 != null && p1 != null) {
            val r0 = minOf(p0.first, p1.first).coerceAtLeast(0)
            val c0 = minOf(p0.second, p1.second).coerceAtLeast(0)
            val r1 = maxOf(p0.first, p1.first).coerceAtLeast(0)
            val c1 = maxOf(p0.second, p1.second).coerceAtLeast(0)
            val isHoriz = (r0 == r1 && c1 > c0)
            val rawPairs = if (isHoriz) {
                (c0..c1).map { c -> c to vp.cellAt(r0, c)?.displayText?.trim().orEmpty() }
            } else {
                (r0..r1).map { r -> r to vp.cellAt(r, c0)?.displayText?.trim().orEmpty() }
            }
            if (preserveExactBounds) {
                nextXLabels = rawPairs.mapIndexed { idx, (_, txt) -> txt.ifEmpty { "${idx + 1}" } }
                nextXRange = "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c1)}"
            } else {
                var nonEmpty = rawPairs.filter { it.second.isNotEmpty() }
                if (nonEmpty.size > 1 && !isHoriz && nextYValues.isNotEmpty() && nonEmpty.size == nextYValues.size + 1 &&
                    parseNumericCell(vp.cellAt(nonEmpty.first().first, c0)) == null
                ) {
                    nonEmpty = nonEmpty.drop(1)
                }
                if (nonEmpty.isNotEmpty()) {
                    nextXLabels = nonEmpty.map { it.second }
                    val effR0 = if (!isHoriz) nonEmpty.first().first else r0
                    val effR1 = if (!isHoriz) nonEmpty.last().first else r1
                    val effC0 = if (isHoriz) nonEmpty.first().first else c0
                    val effC1 = if (isHoriz) nonEmpty.last().first else c1
                    nextXRange = "${MasuSheetConfig.formatCellAddress(effR0, effC0)}:${MasuSheetConfig.formatCellAddress(effR1, effC1)}"
                }
            }
        }
    }

    if (!newYRangeText.isNullOrBlank()) {
        val clean = newYRangeText.trim().removePrefix("=").substringAfter('!').trim()
        val parts = clean.split(':', limit = 2)
        val p0 = parseSingleCellRef(parts[0])
        val p1 = if (parts.size >= 2) parseSingleCellRef(parts[1]) else p0
        if (p0 != null && p1 != null) {
            val r0 = minOf(p0.first, p1.first).coerceAtLeast(0)
            val c0 = minOf(p0.second, p1.second).coerceAtLeast(0)
            val r1 = maxOf(p0.first, p1.first).coerceAtLeast(0)
            val c1 = maxOf(p0.second, p1.second).coerceAtLeast(0)
            val isHoriz = (r0 == r1 && c1 > c0)
            if (isHoriz) {
                if (preserveExactBounds) {
                    nextYValues = (c0..c1).map { c -> parseNumericCell(vp.cellAt(r0, c)) ?: 0f }
                    nextYRange = "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r0, c1)}"
                } else {
                    val vals = mutableListOf<Float>()
                    val cols = mutableListOf<Int>()
                    for (c in c0..c1) {
                        val num = parseNumericCell(vp.cellAt(r0, c))
                        if (num != null) {
                            vals.add(num)
                            cols.add(c)
                        }
                    }
                    if (vals.isNotEmpty()) {
                        nextYValues = vals
                        nextYRange = "${MasuSheetConfig.formatCellAddress(r0, cols.first())}:${MasuSheetConfig.formatCellAddress(r0, cols.last())}"
                    }
                }
            } else if (preserveExactBounds) {
                // Exact bounds mode (when dragging or resizing the Y-Axis frame on the sheet):
                // Never eat the top row `r0` and never shrink `r0..r1` even if `r0..r1` includes text or blank cells!
                nextYValues = (r0..r1).map { r -> parseNumericCell(vp.cellAt(r, c0)) ?: 0f }
                nextYRange = "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c0)}"
                val movedToDifferentColumn = prevYBounds == null || prevYBounds[1] != c0
                if (newName.isNullOrBlank() && movedToDifferentColumn) {
                    val hdrRow = (r0 - 1).takeIf { it >= 0 }
                    val hdrTxt = if (hdrRow != null) vp.cellAt(hdrRow, c0)?.displayText?.trim().orEmpty() else ""
                    if (hdrRow != null && hdrTxt.isNotEmpty() && parseNumericCell(vp.cellAt(hdrRow, c0)) == null) {
                        nextSeriesName = hdrTxt
                        nextNameRef = MasuSheetConfig.formatCellAddress(hdrRow, c0)
                    } else {
                        val isEn = cur.language == "en"
                        nextSeriesName = if (isEn) "Col ${MasuSheetConfig.formatColumnLabel(c0)}" else "${MasuSheetConfig.formatColumnLabel(c0)}列"
                        nextNameRef = ""
                    }
                }
            } else {
                val topCell = vp.cellAt(r0, c0)
                val topIsHeader = r1 > r0 && !topCell?.displayText.isNullOrBlank() && parseNumericCell(topCell) == null
                val startR = if (topIsHeader) r0 + 1 else r0
                val hdrRow = if (topIsHeader) r0 else (r0 - 1).takeIf { it >= 0 }
                if (newName.isNullOrBlank() && hdrRow != null) {
                    val hdrTxt = vp.cellAt(hdrRow, c0)?.displayText?.trim().orEmpty()
                    if (hdrTxt.isNotEmpty() && parseNumericCell(vp.cellAt(hdrRow, c0)) == null) {
                        nextSeriesName = hdrTxt
                        nextNameRef = MasuSheetConfig.formatCellAddress(hdrRow, c0)
                    } else if (existingSeries.name.endsWith("列")) {
                        nextSeriesName = "${MasuSheetConfig.formatColumnLabel(c0)}列"
                    }
                }
                val numRows = (startR..r1).filter { r -> parseNumericCell(vp.cellAt(r, c0)) != null }
                    .ifEmpty { (startR..r1).toList() }
                val vals = numRows.map { r -> parseNumericCell(vp.cellAt(r, c0)) ?: 0f }
                if (vals.isNotEmpty()) {
                    nextYValues = vals
                    nextYRange = "${MasuSheetConfig.formatCellAddress(numRows.first(), c0)}:${MasuSheetConfig.formatCellAddress(numRows.last(), c0)}"
                }
            }
        }
    }

    val updatedSeriesItem = existingSeries.copy(
        name = nextSeriesName,
        values = nextYValues,
        nameCellRef = nextNameRef,
        xRangeText = nextXRange,
        yRangeText = nextYRange,
        xLabels = nextXLabels
    )
    val nextSeriesList = chartObj.chartSeries.mapIndexed { idx, s ->
        if (idx == seriesIndex) updatedSeriesItem else s
    }
    val nextChartLabels = if (seriesIndex == 0 || chartObj.chartSeries.size == 1) {
        nextXLabels
    } else {
        chartObj.chartLabels
    }
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val isEn = cur.language == "en"
    val updatedObjects = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
            val spec = SpreadsheetChartTypeCatalog.findSpec(obj.subType)
            val typeTitle = if (isEn) spec.labelEn else spec.labelJa
            val summary = nextSeriesList.joinToString(", ") { it.name }
            obj.copy(
                title = "$typeTitle: $summary",
                text = "$typeTitle: $summary",
                chartLabels = nextChartLabels,
                chartSeries = nextSeriesList,
                chartValues = nextSeriesList.first().values,
                selectedSubElement = "series:$seriesIndex"
            )
        } else {
            obj
        }
    }
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updatedObjects,
        selectedObjectId = resolvedId,
        viewport = decorateWithCustomSizes(cur.viewport.copy(isDirty = true)),
        isChartSelectDataDialogOpen = if (closeDialog) false else cur.isChartSelectDataDialogOpen,
        statusNoticeMessage = if (isEn) {
            "Updated Series \"$nextSeriesName\" (X: $nextXRange | Y: $nextYRange)"
        } else {
            "系列「$nextSeriesName」を更新しました (X軸: $nextXRange ／ Y軸: $nextYRange)"
        }
    )
    return true
}

/**
 * [EN] Moves the X-Axis or Y-Axis cell range to `newStartRow..newEndRow, newStartCol..newEndCol`
 *      when the user drags the top pill badge or border of a highlighted axis frame on the worksheet grid.
 *      Moving the X-Axis frame updates all series together so the X-Axis never splits into duplicate frames.
 * [JA] シート上のX軸／Y軸ハイライト枠（上部ラベルまたは枠線）をドラッグ移動した際にセル範囲を更新します。
 *      X軸枠を動かした場合は全系列のX軸を一括更新し、X軸枠が増殖しないようにします。
 */
fun MasuSheetViewModel.onMoveChartSeriesAxisRangeByDrag(
    objectId: String,
    seriesIndex: Int,
    isXAxis: Boolean,
    newStartRow: Int,
    newStartCol: Int,
    newEndRow: Int,
    newEndCol: Int
): Boolean {
    val cur = _uiState.value
    cur.sheetObjects.firstOrNull { it.id == objectId && it.kind == SheetObjectKind.CHART } ?: return false
    val r0 = minOf(newStartRow, newEndRow).coerceAtLeast(0)
    val c0 = minOf(newStartCol, newEndCol).coerceAtLeast(0)
    val r1 = maxOf(newStartRow, newEndRow).coerceAtLeast(r0)
    val c1 = maxOf(newStartCol, newEndCol).coerceAtLeast(c0)
    val newRangeAddr = "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c1)}"

    return if (isXAxis) {
        onBindSelectionAsChartXAxis(objectId, newRangeAddr, preserveExactBounds = true)
    } else {
        onUpdateChartSeriesRanges(
            objectId,
            seriesIndex,
            newYRangeText = newRangeAddr,
            closeDialog = false,
            preserveExactBounds = true
        )
    }
}

/**
 * [EN] Resizes (extends or shrinks) the X-Axis or Y-Axis cell range when the user drags one of the
 *      4 corner handles or top/bottom edge handles on the worksheet grid.
 *      - Vertical drag (`r0..r1`): expands or shrinks the row span of the X-Axis or Y-Axis series.
 *      - Horizontal drag across columns (`c0..c1` on a Y-Axis range): expands the chart to include
 *        newly spanned numeric columns as series (or updates existing spanned series to `r0..r1`).
 * [JA] シート上のハイライト枠の四隅の四角ハンドル（または上下端ハンドル）をドラッグしてデータ選択範囲を縦・横に伸縮します。
 */
fun MasuSheetViewModel.onResizeChartAxisRangeByDrag(
    objectId: String,
    seriesIndex: Int,
    isXAxis: Boolean,
    newStartRow: Int,
    newStartCol: Int,
    newEndRow: Int,
    newEndCol: Int
): Boolean {
    val cur = _uiState.value
    val vp = cur.viewport
    val chartObj = cur.sheetObjects.firstOrNull { it.id == objectId && it.kind == SheetObjectKind.CHART } ?: return false
    val r0 = minOf(newStartRow, newEndRow).coerceAtLeast(0)
    val c0 = minOf(newStartCol, newEndCol).coerceAtLeast(0)
    val r1 = maxOf(newStartRow, newEndRow).coerceAtLeast(r0)
    val c1 = maxOf(newStartCol, newEndCol).coerceAtLeast(c0)

    if (isXAxis) {
        val xCol = chartObj.chartXColIndex.takeIf { it >= 0 } ?: c0
        val xRangeAddr = "${MasuSheetConfig.formatCellAddress(r0, xCol)}:${MasuSheetConfig.formatCellAddress(r1, xCol)}"
        return onBindSelectionAsChartXAxis(objectId, xRangeAddr, preserveExactBounds = true)
    }

    // Single-column vertical resize of a Y-Axis series
    if (c0 == c1) {
        val yRangeAddr = "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c0)}"
        return onUpdateChartSeriesRanges(
            objectId,
            seriesIndex,
            newYRangeText = yRangeAddr,
            closeDialog = false,
            preserveExactBounds = true
        )
    }

    // Multi-column horizontal + vertical resize of Y-Axis series (`c0..c1` across `r0..r1`)
    val xBounds = resolveChartSeriesAxisBounds(chartObj, 0, isXAxis = true)
    val xColIdx = xBounds?.getOrNull(1) ?: chartObj.chartXColIndex
    var anyUpdated = false
    for (col in c0..c1) {
        if (col == xColIdx && c1 > c0) continue
        val colRangeAddr = "${MasuSheetConfig.formatCellAddress(r0, col)}:${MasuSheetConfig.formatCellAddress(r1, col)}"
        val latestChart = _uiState.value.sheetObjects.firstOrNull { it.id == objectId && it.kind == SheetObjectKind.CHART } ?: break
        val existingIdx = latestChart.chartSeries.indices.firstOrNull { idx ->
            val b = resolveChartSeriesAxisBounds(latestChart, idx, isXAxis = false)
            b != null && b[1] == col && b[3] == col
        } ?: -1
        if (existingIdx >= 0) {
            if (onUpdateChartSeriesRanges(
                    objectId,
                    existingIdx,
                    newYRangeText = colRangeAddr,
                    closeDialog = false,
                    preserveExactBounds = true
                )
            ) {
                anyUpdated = true
            }
        } else {
            val hasNumeric = (r0..r1).any { r -> parseNumericCell(vp.cellAt(r, col)) != null }
            if (hasNumeric) {
                val colLabel = "${MasuSheetConfig.formatColumnLabel(col)}列"
                if (onAddChartSeriesFromSheet(objectId = objectId, customYRangeText = colRangeAddr, customName = colLabel)) {
                    anyUpdated = true
                }
            }
        }
    }
    return anyUpdated
}

/**
 * [EN] Adds a new data series to the target chart from explicit X/Y ranges, an unplotted table column, the active selection, or interactive Y-axis picking.
 * [JA] 明示指定されたX/Y範囲、表内の未プロット数値列、現在のセル選択範囲、またはシート上の範囲選択モードから新規データ系列を追加します。
 */
fun MasuSheetViewModel.onAddChartSeriesFromSheet(
    objectId: String? = null,
    customXRangeText: String = "",
    customYRangeText: String = "",
    customName: String = ""
): Boolean {
    val cur = _uiState.value
    val vp = cur.viewport
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return false
    val chartObj = cur.sheetObjects.firstOrNull { it.id == resolvedId && it.kind == SheetObjectKind.CHART } ?: return false

    val defaultXRange = resolveChartSeriesAxisRangeText(chartObj, 0, isXAxis = true)
    val defaultXLabels = chartObj.chartSeries.firstOrNull()?.xLabels?.takeIf { it.isNotEmpty() } ?: chartObj.chartLabels

    if (customYRangeText.isNotBlank()) {
        val newIdx = chartObj.chartSeries.size
        val placeholder = ChartSeries(
            name = customName.ifBlank { "系列${newIdx + 1}" },
            values = List(defaultXLabels.size.coerceAtLeast(1)) { 0f },
            xRangeText = customXRangeText.ifBlank { defaultXRange },
            yRangeText = customYRangeText,
            xLabels = defaultXLabels
        )
        _uiState.value = cur.copy(
            sheetObjects = cur.sheetObjects.map { obj ->
                if (obj.id == resolvedId) obj.copy(chartSeries = obj.chartSeries + placeholder) else obj
            }
        )
        return onUpdateChartSeriesRanges(
            objectId = resolvedId,
            seriesIndex = newIdx,
            newName = customName.takeIf { it.isNotBlank() },
            newXRangeText = customXRangeText.takeIf { it.isNotBlank() },
            newYRangeText = customYRangeText,
            closeDialog = false
        )
    }

    // Check if the user has a multi-cell selection on the sheet that isn't already plotted as Y-axis
    val selR0 = minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0)
    val selR1 = maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(selR0)
    val selC0 = minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
    val selC1 = maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(selC0)
    val selRangeAddr = "${MasuSheetConfig.formatCellAddress(selR0, selC0)}:${MasuSheetConfig.formatCellAddress(selR1, selC1)}"
    val hasMultiCellSelection = (selR1 > selR0 || selC1 > selC0) && selRangeAddr != chartObj.sourceRangeText
    if (hasMultiCellSelection && chartObj.chartSeries.none { it.yRangeText == selRangeAddr }) {
        return onBindSelectionAsChartYAxis(resolvedId, append = true, rangeTextOverride = selRangeAddr)
    }

    // Check if there is an unplotted column in the chart's source table
    val tableCols = getChartSourceTableColumns(resolvedId, chartObj.sourceRangeText)
    val existingYCols = chartObj.chartYColIndices.toSet()
    val nextColOption = tableCols.firstOrNull { col ->
        col.isNumeric && col.colIndex != chartObj.chartXColIndex && col.colIndex !in existingYCols &&
            chartObj.chartSeries.none { it.name == col.headerName }
    }
    if (nextColOption != null) {
        return onToggleChartYAxisColumn(resolvedId, nextColOption.colIndex, exclusive = false)
    }

    // Otherwise, create a new series with the current X-Axis and let the user pick its Y-Axis & X-Axis ranges on the sheet!
    val newIdx = chartObj.chartSeries.size
    val yBounds = resolveChartSeriesAxisBounds(chartObj, chartObj.chartSeries.lastIndex.coerceAtLeast(0), isXAxis = false)
    val nextCol = ((yBounds?.get(1) ?: 1) + 1).coerceAtMost((vp.colWidths.size - 1).coerceAtLeast(0))
    val rStart = yBounds?.get(0) ?: 0
    val rEnd = yBounds?.get(2) ?: (defaultXLabels.size - 1).coerceAtLeast(0)
    val nextYAddr = "${MasuSheetConfig.formatCellAddress(rStart, nextCol)}:${MasuSheetConfig.formatCellAddress(rEnd, nextCol)}"
    val nextVals = (rStart..rEnd).map { r -> parseNumericCell(vp.cellAt(r, nextCol)) ?: 0f }
    val seriesTitle = customName.ifBlank { "系列${newIdx + 1} (${MasuSheetConfig.formatColumnLabel(nextCol)}列)" }
    val addedSeries = ChartSeries(
        name = seriesTitle,
        values = nextVals,
        xRangeText = defaultXRange,
        yRangeText = nextYAddr,
        xLabels = defaultXLabels
    )
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val isEn = cur.language == "en"
    val updatedObjects = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
            val nextList = obj.chartSeries + addedSeries
            obj.copy(
                chartSeries = nextList,
                selectedSubElement = "series:$newIdx"
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
            "Added \"$seriesTitle\" (X: $defaultXRange | Y: $nextYAddr) — Drag the frame on the sheet or select X/Y ranges per series"
        } else {
            "新しい系列「$seriesTitle」(X軸: $defaultXRange ／ Y軸: $nextYAddr) を追加しました — シート上の枠をドラッグするか範囲を指定できます"
        }
    )
    return true
}

/**
 * [EN] Removes the data series at `seriesIndex` from the target chart (keeping at least one series) with Undo support.
 * [JA] 最低1系列を残しつつ、対象グラフから `seriesIndex` 番目のデータ系列を削除し、Undo履歴に記録します。
 */
fun MasuSheetViewModel.onRemoveChartSeriesAt(objectId: String? = null, seriesIndex: Int): Boolean {
    val cur = _uiState.value
    val resolvedId = resolveTargetChartObjectId(objectId) ?: return false
    val chartObj = cur.sheetObjects.firstOrNull { it.id == resolvedId && it.kind == SheetObjectKind.CHART } ?: return false
    if (chartObj.chartSeries.size <= 1 || seriesIndex !in chartObj.chartSeries.indices) return false

    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val removedName = chartObj.chartSeries[seriesIndex].name
    val nextSeries = chartObj.chartSeries.filterIndexed { idx, _ -> idx != seriesIndex }
    val nextYCols = chartObj.chartYColIndices.filterIndexed { idx, _ -> idx != seriesIndex }
    val isEn = cur.language == "en"
    val updatedObjects = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
            obj.copy(
                chartSeries = nextSeries,
                chartValues = nextSeries.first().values,
                chartLabels = nextSeries.first().xLabels.ifEmpty { obj.chartLabels },
                chartYColIndices = nextYCols,
                selectedSubElement = "series:${(seriesIndex - 1).coerceAtLeast(0)}"
            )
        } else {
            obj
        }
    }
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updatedObjects,
        selectedObjectId = resolvedId,
        statusNoticeMessage = if (isEn) "Removed series \"$removedName\"" else "系列「$removedName」を削除しました"
    )
    return true
}

/**
 * [EN] Binds the currently selected worksheet cell range directly to the target chart and refreshes its categories and series.
 * [JA] 現在シート上で選択されているセル範囲を対象グラフの元データ範囲として即座に紐づけ、グラフを再集計します。
 */
fun MasuSheetViewModel.onBindCurrentSelectionToChart(objectId: String? = null): Boolean {
    val cur = _uiState.value
    val vp = cur.viewport
    val r0 = minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0)
    val r1 = maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(r0)
    val c0 = minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
    val c1 = maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(c0)
    val rangeStr = "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c1)}"
    return onUpdateChartDataRange(objectId = objectId, rangeText = rangeStr, closeDialog = false)
}

/**
 * [EN] Convenience alias for `onBindCurrentSelectionToChart` invoked by ribbon and dialog controls.
 * [JA] 現在のセル選択範囲を対象グラフのデータ範囲として適用します（`onBindCurrentSelectionToChart` の委譲ラッパー）。
 */
fun MasuSheetViewModel.onApplyCurrentSelectionToChart(chartId: String? = null): Boolean =
    onBindCurrentSelectionToChart(chartId)

/**
 * [EN] Applies `rangeText` to `chartId` with row/column orientation (`seriesInColumns`) and updates the chart immediately.
 * [JA] 指定された範囲文字列 `rangeText` と系列方向（列方向／行方向）をグラフ `chartId` に適用してデータを更新します。
 */
fun MasuSheetViewModel.onApplyChartSourceRange(
    chartId: String,
    rangeText: String,
    seriesInColumns: Boolean = true,
    firstRowAsHeader: Boolean = true,
    firstColAsCategory: Boolean = true
): Boolean {
    return onUpdateChartDataRange(
        objectId = chartId,
        rangeText = rangeText,
        switchRowCol = !seriesInColumns,
        closeDialog = true
    )
}

/**
 * [EN] Transposes a chart's categories and series (swapping X categories with legend series) while preserving Undo history.
 * [JA] 対象グラフの「行／列の切り替え」（カテゴリ軸と凡例系列の転置）を実行し、Undo履歴へ記録します。
 */
fun MasuSheetViewModel.onSwitchChartRowsAndColumns(objectId: String? = null): Boolean {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val resolvedId = objectId
        ?: cur.chartSelectDataTargetId
        ?: cur.selectedObjectId?.takeIf { id -> cur.sheetObjects.any { it.id == id && it.kind == SheetObjectKind.CHART } }
        ?: cur.sheetObjects.lastOrNull { it.sheetIndex == sIdx && it.kind == SheetObjectKind.CHART }?.id
        ?: return false
    var switched = false
    val updated = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART && obj.chartLabels.isNotEmpty() && obj.chartSeries.isNotEmpty()) {
            val swappedCats = obj.chartSeries.map { it.name }
            val swappedSeries = obj.chartLabels.mapIndexed { catIdx, catName ->
                ChartSeries(
                    name = catName,
                    values = obj.chartSeries.map { s -> s.values.getOrElse(catIdx) { 0f } }
                )
            }
            switched = true
            obj.copy(
                chartLabels = swappedCats,
                chartSeries = swappedSeries,
                chartValues = swappedSeries.firstOrNull()?.values ?: obj.chartValues,
                chartXColIndex = -1,
                chartYColIndices = emptyList()
            )
        } else {
            obj
        }
    }
    if (switched) {
        pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
        val isEn = cur.language == "en"
        _uiState.value = cur.copy(
            isDirty = true,
            sheetObjects = updated,
            selectedObjectId = resolvedId,
            statusNoticeMessage = if (isEn) "Switched chart rows and columns" else "グラフの行/列を切り替えました"
        )
    }
    return switched
}

/**
 * [EN] Alias for `onSwitchChartRowsAndColumns` used by contextual chart ribbon actions.
 * [JA] 選択グラフの行と列を入れ替えます（`onSwitchChartRowsAndColumns` の呼び出しエイリアス）。
 */
fun MasuSheetViewModel.onSwitchChartRowColumn(objectId: String? = null): Boolean =
    onSwitchChartRowsAndColumns(objectId)

/**
 * [EN] Re-reads cell values from a chart's `sourceRangeText` (and per-series X/Y ranges) so edits on the sheet reflect in the chart.
 * [JA] グラフに紐づく `sourceRangeText` および各系列のX/Yセル範囲から最新値を再取得してグラフ表示を更新します。
 */
fun MasuSheetViewModel.onRefreshChartFromSourceRange(objectId: String? = null): Boolean {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val targetObj = cur.sheetObjects.firstOrNull { it.id == (objectId ?: cur.selectedObjectId) && it.kind == SheetObjectKind.CHART }
        ?: cur.sheetObjects.lastOrNull { it.sheetIndex == sIdx && it.kind == SheetObjectKind.CHART }
        ?: return false
    if (targetObj.chartXColIndex >= 0 && targetObj.chartYColIndices.isNotEmpty()) {
        return onApplyChartXYColumns(
            objectId = targetObj.id,
            xColIndex = targetObj.chartXColIndex,
            yColIndices = targetObj.chartYColIndices,
            customTableRangeText = targetObj.sourceRangeText,
            closeDialog = false
        )
    }
    val rangeStr = targetObj.sourceRangeText.ifBlank {
        "${MasuSheetConfig.formatCellAddress(cur.viewport.selStartRow, cur.viewport.selStartCol)}:${MasuSheetConfig.formatCellAddress(cur.viewport.selEndRow, cur.viewport.selEndCol)}"
    }
    return onUpdateChartDataRange(targetObj.id, rangeStr, closeDialog = false)
}

/**
 * [EN] Parses `rangeText` (auto-expanding single-cell inputs to the surrounding table), extracts categories and series, and updates the target chart.
 * [JA] `rangeText` を解析（単一セル指定時は周囲の表へ自動拡張）してカテゴリと系列を再抽出し、対象グラフへ反映します。
 */
fun MasuSheetViewModel.onUpdateChartDataRange(
    objectId: String? = null,
    rangeText: String,
    switchRowCol: Boolean = false,
    closeDialog: Boolean = true
): Boolean {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val resolvedId = objectId
        ?: cur.chartSelectDataTargetId
        ?: cur.selectedObjectId?.takeIf { id -> cur.sheetObjects.any { it.id == id && it.kind == SheetObjectKind.CHART } }
        ?: cur.sheetObjects.lastOrNull { it.sheetIndex == sIdx && it.kind == SheetObjectKind.CHART }?.id
        ?: return false

    val cleanRange = rangeText.trim().removePrefix("=")
    if (cleanRange.isEmpty()) return false
    val rangePart = cleanRange.substringAfter('!').trim()
    val parsedCoords: IntArray? = if (rangePart.contains(':')) {
        val parts = rangePart.split(':', limit = 2)
        val p0 = parseSingleCellRef(parts[0])
        val p1 = parseSingleCellRef(parts[1])
        if (p0 != null && p1 != null) {
            intArrayOf(
                minOf(p0.first, p1.first).coerceAtLeast(0),
                minOf(p0.second, p1.second).coerceAtLeast(0),
                maxOf(p0.first, p1.first).coerceAtLeast(0),
                maxOf(p0.second, p1.second).coerceAtLeast(0)
            )
        } else {
            null
        }
    } else {
        val single = parseSingleCellRef(rangePart)
        if (single != null) {
            intArrayOf(single.first, single.second, single.first, single.second)
        } else {
            null
        }
    }
    if (parsedCoords == null) return false

    val vp = cur.viewport
    val targetVp = vp.copy(
        selStartRow = parsedCoords[0],
        selStartCol = parsedCoords[1],
        selEndRow = parsedCoords[2],
        selEndCol = parsedCoords[3]
    )
    val extracted = extractChartDataWithColumns(targetVp)
    val rawCats = extracted.categories
    val rawSeries = extracted.seriesList
    val resolvedRangeAddr = extracted.rangeAddr
    val (finalCats, finalSeries) = if (switchRowCol && rawCats.isNotEmpty() && rawSeries.isNotEmpty()) {
        val swappedCats = rawSeries.map { it.name }
        val swappedSeries = rawCats.mapIndexed { catIdx, catName ->
            ChartSeries(
                name = catName,
                values = rawSeries.map { s -> s.values.getOrElse(catIdx) { 0f } }
            )
        }
        swappedCats to swappedSeries
    } else {
        rawCats to rawSeries
    }
    val primaryValues = finalSeries.firstOrNull()?.values ?: listOf(0f)
    val isEn = cur.language == "en"

    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updatedObjects = cur.sheetObjects.map { obj ->
        if (obj.id == resolvedId && obj.kind == SheetObjectKind.CHART) {
            val spec = SpreadsheetChartTypeCatalog.findSpec(obj.subType)
            val typeTitle = if (isEn) spec.labelEn else spec.labelJa
            val seriesSummary = if (finalSeries.size == 1) {
                finalSeries.first().name
            } else {
                if (isEn) "${finalSeries.size} series ($resolvedRangeAddr)" else "${finalSeries.size}系列 ($resolvedRangeAddr)"
            }
            val nextTitle = if (obj.title.isBlank() || obj.title.contains("売上推移") || obj.title.contains(": ")) {
                "$typeTitle: $seriesSummary"
            } else {
                obj.title
            }
            obj.copy(
                title = nextTitle,
                text = nextTitle,
                chartLabels = finalCats,
                chartValues = primaryValues,
                chartSeries = finalSeries,
                sourceRangeText = resolvedRangeAddr,
                chartXColIndex = if (switchRowCol) -1 else extracted.xColIndex,
                chartYColIndices = if (switchRowCol) emptyList() else extracted.yColIndices
            )
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        sheetObjects = updatedObjects,
        selectedObjectId = resolvedId,
        isChartSelectDataDialogOpen = if (closeDialog) false else _uiState.value.isChartSelectDataDialogOpen,
        statusNoticeMessage = if (isEn) {
            "Updated chart data source to $resolvedRangeAddr (${finalCats.size} categories × ${finalSeries.size} series)"
        } else {
            "グラフのデータ範囲を $resolvedRangeAddr (${finalCats.size}項目 × ${finalSeries.size}系列) に更新しました"
        }
    )
    return true
}

/**
 * Directly updates a chart's category labels and series list (for manual series/category editing in `データの選択` dialog).
 */
fun MasuSheetViewModel.onUpdateChartCustomSeriesAndCategories(
    objectId: String,
    categories: List<String>,
    seriesList: List<ChartSeries>,
    rangeText: String = "",
    customTitle: String? = null
) {
    if (categories.isEmpty() || seriesList.isEmpty()) return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val cur = _uiState.value
    val isEn = cur.language == "en"
    val primaryVals = seriesList.first().values
    val updated = cur.sheetObjects.map { obj ->
        if (obj.id == objectId && obj.kind == SheetObjectKind.CHART) {
            val spec = SpreadsheetChartTypeCatalog.findSpec(obj.subType)
            val typeTitle = if (isEn) spec.labelEn else spec.labelJa
            val summary = if (seriesList.size == 1) seriesList.first().name else "${seriesList.size}系列"
            val finalTitle = customTitle?.takeIf { it.isNotBlank() } ?: "$typeTitle: $summary"
            obj.copy(
                title = finalTitle,
                text = finalTitle,
                chartLabels = categories,
                chartValues = primaryVals,
                chartSeries = seriesList,
                sourceRangeText = rangeText.ifBlank { obj.sourceRangeText }
            )
        } else {
            obj
        }
    }
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = objectId,
        isChartSelectDataDialogOpen = false,
        statusNoticeMessage = if (isEn) "Updated chart series and categories" else "グラフの系列・項目軸データを反映しました"
    )
}

/**
 * [EN] Replaces a chart's title, category labels, and numeric series values from `(seriesName, doubleValues)` pairs with Undo support.
 * [JA] グラフのタイトル・カテゴリラベル配列・各系列の数値リストを一括更新し、Undo履歴へ記録します。
 */
fun MasuSheetViewModel.onUpdateChartCustomSeriesAndCategories(
    chartId: String,
    newTitle: String,
    categories: List<String>,
    seriesPairs: List<Pair<String, List<Double>>>
) {
    val convertedSeries = seriesPairs.map { (name, doubles) ->
        ChartSeries(name = name, values = doubles.map { it.toFloat() })
    }
    onUpdateChartCustomSeriesAndCategories(
        objectId = chartId,
        categories = categories,
        seriesList = convertedSeries,
        customTitle = newTitle
    )
}

/**
 * [EN] Scans the active worksheet for contiguous non-empty 2D tables so users can bind an existing table to a chart in one tap.
 * [JA] アクティブシート上の連続した2次元データ表領域を自動走査・検出し、1タップでグラフへ紐づけられる候補一覧を返します。
 */
fun MasuSheetViewModel.detectTablesOnCurrentSheet(): List<DetectedSheetTableRange> {
    val vp = _uiState.value.viewport
    val visited = HashSet<Long>()
    val results = mutableListOf<DetectedSheetTableRange>()
    val maxR = (vp.rowHeights.size - 1).coerceAtMost(80)
    val maxC = (vp.colWidths.size - 1).coerceAtMost(25)

    fun hasCellContent(r: Int, c: Int): Boolean =
        !vp.cellAt(r, c)?.displayText.isNullOrBlank()

    for (r in 0..maxR) {
        for (c in 0..maxC) {
            val key = NativeViewportSnapshot.packCellKey(r, c)
            if (key in visited) continue
            if (parseNumericCell(vp.cellAt(r, c)) == null) continue

            var r0 = r
            var r1 = r
            var c0 = c
            var c1 = c
            while (c0 > 0 && (r0..r1).any { rr -> hasCellContent(rr, c0 - 1) }) c0--
            while (c1 < maxC && (r0..r1).any { rr -> hasCellContent(rr, c1 + 1) }) c1++
            while (r0 > 0 && (c0..c1).any { cc -> hasCellContent(r0 - 1, cc) }) r0--
            while (r1 < maxR && (c0..c1).any { cc -> hasCellContent(r1 + 1, cc) }) r1++

            for (rr in r0..r1) {
                for (cc in c0..c1) {
                    visited.add(NativeViewportSnapshot.packCellKey(rr, cc))
                }
            }
            if (r1 > r0 || c1 > c0) {
                val rangeStr = "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c1)}"
                if (results.none { it.rangeText == rangeStr }) {
                    val sampleHeaders = (c0..minOf(c1, c0 + 3)).mapNotNull { cc ->
                        vp.cellAt(r0, cc)?.displayText?.trim()?.takeIf { it.isNotEmpty() }
                    }.joinToString(" / ")
                    val label = if (sampleHeaders.isNotEmpty()) {
                        "$rangeStr ($sampleHeaders)"
                    } else {
                        rangeStr
                    }
                    results.add(
                        DetectedSheetTableRange(
                            rangeText = rangeStr,
                            label = label,
                            startRow = r0,
                            startCol = c0,
                            endRow = r1,
                            endCol = c1
                        )
                    )
                }
            }
        }
    }
    return results
}

/**
 * [EN] Changes the chart subtype (`column`, `line`, `pie`, `scatter_lines`, etc.) of the currently selected chart while keeping its bound data intact.
 * [JA] 元データとの紐づけを維持したまま、選択中のグラフの種類（サブタイプ）を変更します。
 */
fun MasuSheetViewModel.onChangeSelectedChartType(chartType: String): Boolean {
    val cur = _uiState.value
    val normalized = SpreadsheetChartTypeCatalog.normalizeSubType(chartType)
    val spec = SpreadsheetChartTypeCatalog.findSpec(normalized)
    val sIdx = cur.viewport.sheetIndex
    val targetId = cur.selectedObjectId
        ?.takeIf { id -> cur.sheetObjects.any { it.id == id && it.sheetIndex == sIdx && it.kind == SheetObjectKind.CHART } }
        ?: cur.sheetObjects.lastOrNull { it.sheetIndex == sIdx && it.kind == SheetObjectKind.CHART }?.id
        ?: return false

    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val isEn = cur.language == "en"
    val typeLabel = if (isEn) spec.labelEn else spec.labelJa
    val updated = cur.sheetObjects.map { obj ->
        if (obj.id == targetId && obj.kind == SheetObjectKind.CHART) {
            val suffix = obj.title.substringAfter(": ", missingDelimiterValue = obj.sourceRangeText.ifBlank { cur.viewport.sheetName })
            val nextTitle = if (obj.title.startsWith("ピボットグラフ") || obj.title.startsWith("PivotChart")) {
                if (isEn) "PivotChart ($typeLabel - ${cur.viewport.sheetName})" else "ピボットグラフ ($typeLabel - ${cur.viewport.sheetName})"
            } else {
                "$typeLabel: $suffix"
            }
            obj.copy(
                subType = normalized,
                title = nextTitle,
                text = nextTitle
            )
        } else {
            obj
        }
    }
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updated,
        selectedObjectId = targetId,
        isPivotChartPickerOpen = false,
        activeRibbonTab = "chartDesign",
        viewport = decorateWithCustomSizes(cur.viewport),
        statusNoticeMessage = if (isEn) "Changed chart type to $typeLabel" else "グラフの種類を「$typeLabel」に変更しました"
    )
    return true
}

/**
 * [EN] Updates the selected chart's subtype if one is selected (unless `insertNew = true`), or inserts a new chart of `chartType` and returns its ID.
 * [JA] グラフ選択中（かつ `insertNew = false`）ならその種類を変更し、未選択または新規指定時は新しいグラフを挿入してIDを返します。
 */
fun MasuSheetViewModel.onInsertOrUpdateChartFromPicker(chartType: String, insertNew: Boolean = false): String {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val hasSelectedChart = !insertNew && (
        cur.selectedObjectId?.let { id ->
            cur.sheetObjects.any { it.id == id && it.sheetIndex == sIdx && it.kind == SheetObjectKind.CHART }
        } == true
    )
    if (hasSelectedChart && onChangeSelectedChartType(chartType)) {
        return cur.selectedObjectId
    }
    val vp = cur.viewport
    val hasMultiCellSelection = vp.selStartRow != vp.selEndRow || vp.selStartCol != vp.selEndCol
    val id = if (hasMultiCellSelection) {
        onInsertChartFromRange(
            chartType = chartType,
            startRow = vp.selStartRow,
            startCol = vp.selStartCol,
            endRow = vp.selEndRow,
            endCol = vp.selEndCol,
            customTitle = null
        )
    } else {
        val bounds = detectTableBoundsAt(vp, vp.activeRow, vp.activeCol)
        val spec = SpreadsheetChartTypeCatalog.findSpec(chartType)
        onInsertChartFromRange(
            chartType = spec.subType,
            startRow = bounds[0],
            startCol = bounds[3],
            endRow = bounds[2],
            endCol = bounds[4],
            customTitle = null
        )
    }
    _uiState.value = _uiState.value.copy(isPivotChartPickerOpen = false)
    return id
}


/**
 * [EN] Updates `chartLabels[categoryIndex]` on `objectId` after an in-place double-tap label edit.
 * [JA] グラフ上の軸ラベル直接編集で確定された `categoryIndex` 番目のカテゴリ名を更新します。
 */
fun MasuSheetViewModel.onUpdateChartCategoryLabel(objectId: String, categoryIndex: Int, newLabel: String) {
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id == objectId && categoryIndex in obj.chartLabels.indices) {
            val newLabels = obj.chartLabels.toMutableList().also { it[categoryIndex] = newLabel }
            obj.copy(chartLabels = newLabels)
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(isDirty = true, sheetObjects = updated)
}

/**
 * [EN] Updates `chartSeries[seriesIndex].name` on `objectId` after an in-place legend edit.
 * [JA] グラフ凡例の直接編集で確定された `seriesIndex` 番目の系列名を更新します。
 */
fun MasuSheetViewModel.onUpdateChartSeriesName(objectId: String, seriesIndex: Int, newName: String) {
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id == objectId && seriesIndex in obj.chartSeries.indices) {
            val newSeries = obj.chartSeries.toMutableList().also { list ->
                list[seriesIndex] = list[seriesIndex].copy(name = newName)
            }
            obj.copy(chartSeries = newSeries)
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(isDirty = true, sheetObjects = updated)
}

/**
 * [EN] Updates `chartSeries[seriesIndex].values[categoryIndex]` (parsing formatted numbers) after an in-place chart value edit.
 * [JA] グラフ上のデータ値直接編集で入力された数値をパースし、`seriesIndex` 系列の `categoryIndex` 番目の値を更新します。
 */
fun MasuSheetViewModel.onUpdateChartDataValue(objectId: String, seriesIndex: Int, categoryIndex: Int, newValueText: String) {
    val cleaned = newValueText.replace(",", "").replace("¥", "").replace("$", "").trim()
    val parsed = MasuSheetConfig.parseFiniteFloatOrNull(cleaned) ?: return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = _uiState.value.sheetObjects.map { obj ->
        if (obj.id == objectId) {
            val newSeries = if (seriesIndex in obj.chartSeries.indices) {
                obj.chartSeries.toMutableList().also { sList ->
                    val targetSeries = sList[seriesIndex]
                    if (categoryIndex in targetSeries.values.indices) {
                        val newVals = targetSeries.values.toMutableList().also { it[categoryIndex] = parsed }
                        sList[seriesIndex] = targetSeries.copy(values = newVals)
                    }
                }
            } else {
                obj.chartSeries
            }
            val newPrimaryVals = if (seriesIndex == 0 && categoryIndex in obj.chartValues.indices) {
                obj.chartValues.toMutableList().also { it[categoryIndex] = parsed }
            } else {
                newSeries.firstOrNull()?.values ?: obj.chartValues
            }
            obj.copy(chartSeries = newSeries, chartValues = newPrimaryVals)
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(isDirty = true, sheetObjects = updated)
}
