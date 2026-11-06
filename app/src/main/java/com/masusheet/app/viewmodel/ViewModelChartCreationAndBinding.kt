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
 * [EN] Chart Data Extraction, 32-Subtype Insertion, & Interactive X/Y Sheet Range Binding
 * Extracts numeric/category series from sheet tables, creates charts across 32 subtypes, and binds X-axis / Y-axis columns via dialog or direct sheet selection.
 *
 * ---
 * [JA / 日本語解説]
 * シート上の表からの系列・項目軸データ抽出、全32サブタイプのグラフ作成、およびシート上でのX軸／Y軸セル範囲バインディングを担うモジュールです。
 * ============================================================================
 */

/**
 * [EN] Parsed 2D table data (`categories`, `seriesList`, A1 `rangeAddr`, and X/Y column indices) extracted from worksheet cells for chart creation.
 * [JA] シート範囲から抽出したグラフ描画用テーブルデータ（カテゴリラベル列、データ系列リスト、A1範囲文字列、X/Y列番号）を保持します。
 */
internal data class ExtractedChartTableData(
    val categories: List<String>,
    val seriesList: List<ChartSeries>,
    val rangeAddr: String,
    val xColIndex: Int = -1,
    val yColIndices: List<Int> = emptyList()
)

/**
 * [EN] Resolves a readable series/axis header name for column `col` (falling back to `Col A` / `列A` when the header cell is numeric or blank).
 * [JA] グラフ用の列見出し名を解決します（先頭行が空欄または純粋な数値データの場合は `列A` / `Col A` にフォールバックします）。
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
 * [EN] Extracts `(categories, seriesList, rangeAddr)` from the current selection (or auto-detected contiguous table when a single cell is active).
 * [JA] 現在の選択範囲（単一セル選択時は周辺の連続データ表を自動検出）から、グラフ用の `(カテゴリ, 系列リスト, 範囲文字列)` を抽出します。
 */
fun MasuSheetViewModel.extractChartDataFromViewport(vp: NativeViewportSnapshot): Triple<List<String>, List<ChartSeries>, String> {
    val res = extractChartDataWithColumns(vp)
    return Triple(res.categories, res.seriesList, res.rangeAddr)
}

/**
 * [EN] Extracts full `ExtractedChartTableData` including detected X-axis and Y-axis column indices, handling both headed and headerless numeric tables.
 * [JA] 見出し行の有無を自動判定しながら、選択範囲または隣接データ表からX軸列・Y軸列インデックス付きのグラフ用データを抽出します。
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

    val isEn = _uiState.value.language == "en"
    val rawCandidateRows = (dataStartR..r1).filter { r ->
        val firstColTxt = vp.cellAt(r, c0)?.displayText?.trim().orEmpty()
        !isSummaryFooterRowLabel(firstColTxt) &&
            !firstColTxt.endsWith(" 集計") &&
            !firstColTxt.endsWith(" Total", ignoreCase = true) &&
            !firstColTxt.startsWith("🔎 ") &&
            numericCols.any { c -> parseNumericCell(vp.cellAt(r, c)) != null }
    }
    val hasIndentedPivotChildren = rawCandidateRows.any { r ->
        vp.cellAt(r, c0)?.displayText?.startsWith("   ") == true
    }
    val validDataRows = if (hasIndentedPivotChildren) {
        rawCandidateRows.filterNot { r ->
            vp.cellAt(r, c0)?.displayText?.trim()?.startsWith("▾ ") == true
        }
    } else {
        rawCandidateRows
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
            }.ifEmpty { if (isEn) "Row ${singleR + 1}" else "行${singleR + 1}" }
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
        val secondaryTextCols = if (effectiveXCol != null && !useFirstNumericColAsX) {
            ((effectiveXCol + 1)..c1).filter { c ->
                c !in numericCols && validDataRows.any { r -> !vp.cellAt(r, c)?.displayText.isNullOrBlank() }
            }
        } else {
            emptyList()
        }

        val rFirst = validDataRows.first()
        val rLast = validDataRows.last()
        val xAddr = if (effectiveXCol != null) {
            "${MasuSheetConfig.formatCellAddress(rFirst, effectiveXCol)}:${MasuSheetConfig.formatCellAddress(rLast, effectiveXCol)}"
        } else {
            ""
        }

        var lastOuterGroup = ""
        val categories = validDataRows.map { r ->
            val primaryRaw = if (effectiveXCol != null) {
                vp.cellAt(r, effectiveXCol)?.displayText?.trim()?.removePrefix("▾ ")?.removePrefix("▸ ")?.trim().orEmpty()
            } else {
                ""
            }
            if (primaryRaw.isNotEmpty()) {
                lastOuterGroup = primaryRaw
            }
            val subParts = secondaryTextCols.mapNotNull { sc ->
                vp.cellAt(r, sc)?.displayText?.trim()?.removePrefix("▾ ")?.removePrefix("▸ ")?.trim()?.takeIf { it.isNotEmpty() }
            }
            val combined = when {
                subParts.isNotEmpty() && lastOuterGroup.isNotEmpty() -> "$lastOuterGroup / ${subParts.joinToString(" / ")}"
                subParts.isNotEmpty() -> subParts.joinToString(" / ")
                else -> primaryRaw
            }
            combined.ifEmpty { if (isEn) "Row ${r + 1}" else "行${r + 1}" }
        }

        val seriesList = effectiveYCols.map { c ->
            val colLet = MasuSheetConfig.formatColumnLabel(c)
            val sName = if (headerRowIdx != null) {
                vp.cellAt(headerRowIdx, c)?.displayText?.trim().orEmpty()
            } else {
                ""
            }.ifEmpty { if (isEn) "Col $colLet" else "${colLet}列" }
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
    val fallbackCats = if (isEn) listOf("Item 1", "Item 2", "Item 3", "Item 4") else listOf("項目1", "項目2", "項目3", "項目4")
    val fallbackSeries = listOf(ChartSeries(if (isEn) "Series 1" else "系列1", listOf(0f, 0f, 0f, 0f), xLabels = fallbackCats))
    return ExtractedChartTableData(fallbackCats, fallbackSeries, rangeAddr)
}

/**
 * [EN] Inserts a floating chart of `chartType` bound to the current cell selection (or nearby table) on the active sheet.
 * [JA] 現在のセル選択範囲（または近傍のデータ表）を元データとして、指定種別 `chartType` のグラフをアクティブシートに挿入します。
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
 * [EN] Creates and selects a floating chart of `chartType` bound to `(startRow..endRow, startCol..endCol)` and records an overlay Undo snapshot.
 * [JA] 指定セル範囲 `(startRow..endRow, startCol..endCol)` に紐づくグラフを生成・選択状態にし、Undo履歴へ記録します。
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
 * [EN] Opens or closes the 32-subtype chart / PivotChart picker dialog.
 * [JA] 全32種類のグラフ／ピボットグラフ選択ダイアログの表示・非表示を切り替えます。
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
 * [EN] Resolves which chart ID to operate on from `objectId`, `chartSelectDataTargetId`, `selectedObjectId`, or the latest chart on the active sheet.
 * [JA] 明示指定ID、データ選択対象ID、現在選択中のオブジェクト、またはシート上の最新グラフの順で操作対象のグラフIDを解決します。
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
 * [EN] Routes interactive on-sheet cell-range selection to whole-table binding, X-axis binding, or Y-axis series binding according to `mode`.
 * [JA] シート上での範囲ドラッグ選択結果を、`mode` に応じて表全体・X軸・Y軸追加／個別系列バインド処理へ振り分けます。
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
 * [EN] Enters interactive on-sheet range-picking mode (`"x"`, `"y"`, `"y_append"`, `"series_x:idx"`, or `"series_y:idx"`) for the target chart.
 * [JA] 対象グラフのX軸またはY軸データ範囲をシート上のセルドラッグで直接指定するインタラクティブ選択モードを開始します。
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
 * [EN] Exits interactive on-sheet chart range picking mode and reopens the Select Data dialog for confirmation.
 * [JA] シート上でのグラフ範囲選択モードを終了し、「データの選択」ダイアログを再表示します。
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
 * [EN] Computes `[headerRow, dataStartRow, dataEndRow, startCol, endCol]` for a chart's source table, preserving row 0 on headerless numeric tables.
 * [JA] グラフの元データ表の境界 `[見出し行, データ開始行, データ終了行, 開始列, 終了列]` を算出します（見出しなし数値表の先頭行も欠落なく保持）。
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
