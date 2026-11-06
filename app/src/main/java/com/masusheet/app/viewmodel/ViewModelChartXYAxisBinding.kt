package com.masusheet.app.viewmodel

import androidx.lifecycle.ViewModel
import com.masusheet.app.catalog.SpreadsheetChartTypeCatalog
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.ChartSeries
import com.masusheet.app.model.ChartTableColumnOption
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeSheetObject
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.model.SheetObjectKind
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Chart X/Y Axis Column Binding, Sheet Selection Binding, & Axis Range Geometry
 * Handles interactive X-axis and Y-axis column toggling, sheet range-to-axis binding, and cell-coordinate bounds resolution for on-sheet chart range frames.
 *
 * ---
 * [JA / 日本語解説]
 * グラフのX軸・Y軸カラム割当、シート選択範囲からの軸バインド、およびシート上のデータ範囲ハイライト枠のセル座標算出を担うモジュールです。
 * ============================================================================
 */

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
 * [EN] Binds `xColIndex` as the X-axis category/value column and `yColIndices` as plotted Y-axis series columns on the target chart.
 * [JA] 対象グラフに対して、指定されたX軸列 (`xColIndex`) と1つ以上のY軸データ列 (`yColIndices`) を一括適用して再描画します。
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
 * [EN] Parses the 0-based `[startRow, startCol, endRow, endCol]` sheet coordinates for `seriesIndex`'s X-axis or Y-axis source range.
 * [JA] グラフ内 `seriesIndex` 系列のX軸またはY軸参照文字列から、シート上の0始まり範囲座標 `[startRow, startCol, endRow, endCol]` を解決します。
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
 * [EN] Returns the A1-style worksheet range string (`C3:C10`, etc.) bound to `seriesIndex`'s X-axis (`isXAxis = true`) or Y-axis (`isXAxis = false`).
 * [JA] グラフ内 `seriesIndex` 系列のX軸またはY軸に紐づくA1形式のセル範囲文字列（例: `C3:C10`）を返します。
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
