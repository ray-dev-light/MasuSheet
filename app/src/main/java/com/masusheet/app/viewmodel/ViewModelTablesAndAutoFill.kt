package com.masusheet.app.viewmodel

import androidx.lifecycle.ViewModel
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.ChartSeries
import com.masusheet.app.model.CustomCellStyleOverride
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeCellModel
import com.masusheet.app.model.NativeMergeRange
import com.masusheet.app.model.NativeSheetObject
import com.masusheet.app.model.NativeValidationModel
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.model.PivotAggFunction
import com.masusheet.app.model.PivotTableDefinition
import com.masusheet.app.model.PivotValueFieldSpec
import com.masusheet.app.model.SheetObjectKind
import com.masusheet.app.model.TableColumnFilterPopupState
import com.masusheet.app.ui.dialogs.SpreadsheetColumnFilterPopupDialog
import kotlin.math.abs
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Sample Sheet Builder, Smart AutoFill, Formula Shifting, Data Validation, Freeze Panes, & Table Sort/Filter
 * Provides Monthly Budget sheet setup, smart series/formula AutoFill (preserving string literals & cross-sheet refs), Data Validation, Freeze Panes, row/column hide/unhide, and table AutoFilter/Sort.
 *
 * ---
 * [JA / 日本語解説]
 * 月間予算シート構築、文字列リテラル・別シート参照を保護するスマートオートフィル、データの入力規則、ウィンドウ枠の固定、行列の非表示／再表示、テーブルのソート・フィルタを担います。
 * ============================================================================
 */

/**
 * [EN] Populates the localized Monthly Budget (`月間予算`) sample template with formulas, currency formats, summary cards, and comparison charts.
 * [JA] 「月間予算」サンプルテンプレートの見出し・数式・通貨書式・集計テーブル・予算実績グラフを指定シートに構築します。
 */
fun MasuSheetViewModel.populateMonthlyBudgetSheet(sheetIndex: Int, attachChartsImmediately: Boolean = true): NativeViewportSnapshot {
    customSheetMerges[sheetIndex] = listOf(NativeMergeRange(0, 0, 0, 5))
    val colWMap = mapOf(
        0 to 88f, 1 to 136f, 2 to 74f, 3 to 86f, 4 to 86f, 5 to 86f,
        6 to 24f, 7 to 88f, 8 to 84f, 9 to 84f, 10 to 84f
    )
    for ((c, w) in colWMap) {
        customColWidths[sheetIndex to c] = w
    }
    customRowHeights[sheetIndex to 0] = 30f
    customRowHeights[sheetIndex to 8] = 24f
    customRowHeights[sheetIndex to 10] = 24f

    var lastSnap = _uiState.value.viewport
    fun setBudgetCell(
        r: Int,
        c: Int,
        input: String,
        bg: Int = 0,
        fg: Int = 0,
        fs: Float = 0f,
        bold: Boolean? = null,
        hAlign: Int? = null,
        numFmt: String = ""
    ) {
        val addr = MasuSheetConfig.formatCellAddress(r, c)
        if (input.isNotEmpty()) {
            lastSnap = engineGateway.executeCommand("cell.set", "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(input)}}")
        }
        if (bg != 0 || fg != 0 || fs > 0f || bold != null || hAlign != null || numFmt.isNotEmpty()) {
            customCellStyles[sheetIndex to NativeViewportSnapshot.packCellKey(r, c)] = CustomCellStyleOverride(
                bgColor = bg,
                fgColor = fg,
                fontSize = fs,
                isBold = bold,
                hAlign = hAlign,
                numberFormatKind = numFmt
            )
        }
    }

    // Title Banner A1:F1
    val titleBg = 0xFFFCE4D6.toInt()
    setBudgetCell(0, 0, "月間予算表", bg = titleBg, fg = 0xFF1F2937.toInt(), fs = 15f, bold = true)
    for (c in 1..5) {
        setBudgetCell(0, c, "", bg = titleBg)
    }

    // Input Parameters Block A3:B8
    val labelBg = 0xFFD9E1F2.toInt()
    val yellowInputBg = 0xFFFFF2CC.toInt()
    val blueInputFg = 0xFF0000FF.toInt()
    val greenCalcBg = 0xFFE2EFDA.toInt()

    setBudgetCell(2, 0, "世帯人数(人)", bg = labelBg, bold = true)
    setBudgetCell(2, 1, "2", bg = yellowInputBg, fg = blueInputFg, hAlign = 2)

    setBudgetCell(3, 0, "年収(税込・円)", bg = labelBg, bold = true)
    setBudgetCell(3, 1, "4000000", bg = yellowInputBg, fg = blueInputFg, hAlign = 2, numFmt = "currency")

    setBudgetCell(4, 0, "手取り率(仮定)", bg = labelBg, bold = true)
    setBudgetCell(4, 1, "0.8", bg = yellowInputBg, fg = blueInputFg, hAlign = 2, numFmt = "percent")

    setBudgetCell(5, 0, "月間手取り収入(円)", bg = labelBg, bold = true)
    setBudgetCell(5, 1, "=ROUND(B4*B5/12,0)", bg = greenCalcBg, bold = true, hAlign = 2, numFmt = "currency")

    setBudgetCell(6, 0, "1人あたり食費率(仮定)", bg = labelBg, bold = true)
    setBudgetCell(6, 1, "0.08", bg = yellowInputBg, fg = blueInputFg, hAlign = 2, numFmt = "percent")

    setBudgetCell(7, 0, "1人あたり日用品率(仮定)", bg = labelBg, bold = true)
    setBudgetCell(7, 1, "0.015", bg = yellowInputBg, fg = blueInputFg, hAlign = 2, numFmt = "percent")

    // Note Row A9
    setBudgetCell(
        8, 0,
        "※ 黄色のセルが入力欄です。予算割合は月間手取り収入に対する一般的な仮定値で、自由に変更できます。「実績」列に毎月の支出を入力してください。",
        fg = 0xFF595959.toInt(), fs = 9.5f
    )

    // Main Budget Table Header A11:F11
    val hdrBg = 0xFF2F5597.toInt()
    val hdrFg = 0xFFFFFFFF.toInt()
    val mainHeaders = listOf("カテゴリ", "項目", "予算割合", "予算(円)", "実績(円)", "差額(円)")
    mainHeaders.forEachIndexed { c, hText ->
        setBudgetCell(10, c, hText, bg = hdrBg, fg = hdrFg, bold = true)
    }

    // Main Budget Table Rows A12:F24 (rows 11..23)
    data class BudgetRowSpec(val cat: String, val item: String, val ratioFormula: String, val actualYen: String)
    val rows = listOf(
        BudgetRowSpec("住居", "家賃・住宅ローン", "0.25", "65000"),
        BudgetRowSpec("食費", "食費(自炊・外食)", "=\$B\$3*\$B\$7", "40000"),
        BudgetRowSpec("生活", "水道光熱費", "0.06", "15000"),
        BudgetRowSpec("生活", "通信費(スマホ・ネット)", "0.04", "10000"),
        BudgetRowSpec("生活", "日用品・消耗品", "=\$B\$3*\$B\$8", "7000"),
        BudgetRowSpec("保険・医療", "保険料", "0.05", "12000"),
        BudgetRowSpec("保険・医療", "医療・衛生費", "0.02", "4000"),
        BudgetRowSpec("生活", "交通費", "0.03", "7500"),
        BudgetRowSpec("娯楽・交際", "交際費・レジャー", "0.06", "18000"),
        BudgetRowSpec("娯楽・交際", "美容・被服費", "0.04", "9500"),
        BudgetRowSpec("娯楽・交際", "趣味・教養・サブスク", "0.03", "8500"),
        BudgetRowSpec("その他", "特別費・予備費", "0.03", "5000"),
        BudgetRowSpec("貯蓄", "貯蓄・投資", "=MAX(0,1-SUM(C12:C23))", "50000")
    )
    rows.forEachIndexed { idx, spec ->
        val r = 11 + idx
        val sheetRowNum = r + 1
        val bandBg = if (idx % 2 == 0) 0xFFD9E1F2.toInt() else 0xFFFFFFFF.toInt()
        setBudgetCell(r, 0, spec.cat, bg = bandBg)
        setBudgetCell(r, 1, spec.item, bg = bandBg)
        setBudgetCell(r, 2, spec.ratioFormula, bg = if (spec.ratioFormula.startsWith("=")) bandBg else yellowInputBg, fg = if (spec.ratioFormula.startsWith("=")) 0 else blueInputFg, hAlign = 2, numFmt = "percent")
        setBudgetCell(r, 3, "=ROUND(\$B\$6*C$sheetRowNum,0)", bg = bandBg, hAlign = 2, numFmt = "currency")
        setBudgetCell(r, 4, spec.actualYen, bg = yellowInputBg, fg = blueInputFg, hAlign = 2, numFmt = "currency")
        setBudgetCell(r, 5, "=D$sheetRowNum-E$sheetRowNum", bg = bandBg, hAlign = 2, numFmt = "currency")
    }

    // Main Budget Table Total Row A25:F25 (r = 24)
    val totalBg = 0xFFB4C6E7.toInt()
    setBudgetCell(24, 0, "合計", bg = totalBg, bold = true)
    setBudgetCell(24, 1, "", bg = totalBg, bold = true)
    setBudgetCell(24, 2, "=SUM(C12:C24)", bg = totalBg, bold = true, hAlign = 2, numFmt = "percent")
    setBudgetCell(24, 3, "=SUM(D12:D24)", bg = totalBg, bold = true, hAlign = 2, numFmt = "currency")
    setBudgetCell(24, 4, "=SUM(E12:E24)", bg = totalBg, bold = true, hAlign = 2, numFmt = "currency")
    setBudgetCell(24, 5, "=D25-E25", bg = totalBg, bold = true, hAlign = 2, numFmt = "currency")

    // Category Summary PivotTable H11:K19 (r = 10..18, c = 7..10)
    val summaryHeaders = listOf("カテゴリ", "予算(円)", "実績(円)", "差額(円)")
    summaryHeaders.forEachIndexed { idx, hText ->
        setBudgetCell(10, 7 + idx, hText, bg = hdrBg, fg = hdrFg, bold = true)
    }
    val summaryCategories = listOf("住居", "食費", "生活", "保険・医療", "娯楽・交際", "その他", "貯蓄")
    summaryCategories.forEachIndexed { idx, catName ->
        val r = 11 + idx
        val sheetRowNum = r + 1
        val bandBg = if (idx % 2 == 0) 0xFFD9E1F2.toInt() else 0xFFFFFFFF.toInt()
        setBudgetCell(r, 7, catName, bg = bandBg)
        setBudgetCell(r, 8, "=SUMIF(\$A\$12:\$A\$24,H$sheetRowNum,\$D\$12:\$D\$24)", bg = bandBg, hAlign = 2, numFmt = "currency")
        setBudgetCell(r, 9, "=SUMIF(\$A\$12:\$A\$24,H$sheetRowNum,\$E\$12:\$E\$24)", bg = bandBg, hAlign = 2, numFmt = "currency")
        setBudgetCell(r, 10, "=I$sheetRowNum-J$sheetRowNum", bg = bandBg, hAlign = 2, numFmt = "currency")
    }
    setBudgetCell(18, 7, "総計", bg = totalBg, bold = true)
    setBudgetCell(18, 8, "=SUM(I12:I18)", bg = totalBg, bold = true, hAlign = 2, numFmt = "currency")
    setBudgetCell(18, 9, "=SUM(J12:J18)", bg = totalBg, bold = true, hAlign = 2, numFmt = "currency")
    setBudgetCell(18, 10, "=I19-J19", bg = totalBg, bold = true, hAlign = 2, numFmt = "currency")

    // Register H11:K19 as a live interactive PivotTableDefinition so selecting it or opening PivotTable Fields works immediately!
    val takeHomePay = 266667.0
    val householdCount = 2.0
    val foodRate = 0.08
    val dailyGoodsRate = 0.015
    val explicitRatios = rows.map { spec ->
        when {
            spec.ratioFormula.contains("B7") -> householdCount * foodRate
            spec.ratioFormula.contains("B8") -> householdCount * dailyGoodsRate
            spec.ratioFormula.startsWith("=") -> 0.0
            else -> spec.ratioFormula.toDoubleOrNull() ?: 0.0
        }
    }
    val sumBeforeSavings = explicitRatios.take(rows.size - 1).sum()
    val budgetSourceRecords = rows.mapIndexed { idx, spec ->
        val ratio = if (idx == rows.lastIndex) (1.0 - sumBeforeSavings).coerceAtLeast(0.0) else explicitRatios[idx]
        val budgetYen = kotlin.math.round(takeHomePay * ratio).toLong()
        val actualYen = spec.actualYen.toLongOrNull() ?: 0L
        val diffYen = budgetYen - actualYen
        mapOf(
            "カテゴリ" to spec.cat,
            "項目" to spec.item,
            "予算割合" to String.format(java.util.Locale.US, "%.3f", ratio),
            "予算(円)" to budgetYen.toString(),
            "実績(円)" to actualYen.toString(),
            "差額(円)" to diffYen.toString()
        )
    }
    val pivotId = "pivot_monthly_budget_$sheetIndex"
    val budgetPivot = PivotTableDefinition(
        id = pivotId,
        name = "月間予算ピボット1",
        sourceSheetIndex = sheetIndex,
        sourceSheetName = "月間予算",
        sourceStartRow = 10,
        sourceStartCol = 0,
        sourceEndRow = 23,
        sourceEndCol = 5,
        targetSheetIndex = sheetIndex,
        targetStartRow = 10,
        targetStartCol = 7,
        lastRenderedStartRow = 10,
        lastRenderedEndRow = 18,
        lastRenderedEndCol = 10,
        availableFields = mainHeaders,
        numericFields = setOf("予算割合", "予算(円)", "実績(円)", "差額(円)"),
        sourceRecords = budgetSourceRecords,
        filterFields = emptyList(),
        columnFields = emptyList(),
        rowFields = listOf("カテゴリ"),
        valueFields = listOf(
            PivotValueFieldSpec("予算(円)", PivotAggFunction.SUM),
            PivotValueFieldSpec("実績(円)", PivotAggFunction.SUM),
            PivotValueFieldSpec("差額(円)", PivotAggFunction.SUM)
        ),
        stylePreset = "PivotStyleMedium2",
        reportLayout = "compact",
        subtotalsMode = "bottom",
        grandTotalsMode = "both",
        repeatLabels = false,
        blankRows = false,
        bandedRows = true,
        bandedCols = false
    )
    val curState = _uiState.value
    val updatedPivots = curState.pivotTables.filterNot { it.id == pivotId } + budgetPivot
    _uiState.value = curState.copy(
        pivotTables = updatedPivots,
        activePivotTableId = curState.activePivotTableId ?: pivotId
    )

    sizeRevisionBump++
    if (attachChartsImmediately) {
        ensureMonthlyBudgetCharts(sheetIndex)
    }
    return lastSnap
}

/**
 * [EN] Attaches the default Budget vs. Actual column chart and category breakdown pie chart to `sheetIndex` if not yet materialized.
 * [JA] 月間予算シートに「予算 vs 実績」縦棒グラフと「カテゴリ別実績構成比」円グラフが未生成の場合に初期配置します。
 */
internal fun MasuSheetViewModel.ensureMonthlyBudgetCharts(sheetIndex: Int) {
    val matKey = "${_uiState.value.activeWorkbookId}:monthly_budget:$sheetIndex"
    val existingOnSheet = _uiState.value.sheetObjects.any { it.sheetIndex == sheetIndex && it.kind == SheetObjectKind.CHART }
    if (existingOnSheet) {
        materializedSampleChartSheetKeys.add(matKey)
        return
    }
    if (matKey in materializedSampleChartSheetKeys) return
    materializedSampleChartSheetKeys.add(matKey)
    val cats = listOf("住居", "食費", "生活", "保険・医療", "娯楽・交際", "その他", "貯蓄")
    val budgetVals = listOf(66667f, 42667f, 38667f, 18667f, 34667f, 8000f, 57332f)
    val actualVals = listOf(65000f, 40000f, 39500f, 16000f, 36000f, 5000f, 50000f)

    val pieChart = NativeSheetObject(
        id = "obj_${nextObjectCounter++}",
        sheetIndex = sheetIndex,
        kind = SheetObjectKind.CHART,
        subType = "pie",
        xPt = 580f,
        yPt = 8f,
        widthPt = 335f,
        heightPt = 205f,
        title = "カテゴリ別 予算配分",
        text = "カテゴリ別 予算配分",
        fillColor = 0xFFFFFFFF.toInt(),
        strokeColor = 0xFF2F5597.toInt(),
        strokeWidthPt = 1.5f,
        chartLabels = cats,
        chartValues = budgetVals,
        chartSeries = listOf(ChartSeries("予算(円)", budgetVals)),
        sourceRangeText = "H11:I18"
    )
    val colChart = NativeSheetObject(
        id = "obj_${nextObjectCounter++}",
        sheetIndex = sheetIndex,
        kind = SheetObjectKind.CHART,
        subType = "column",
        xPt = 580f,
        yPt = 442f,
        widthPt = 335f,
        heightPt = 205f,
        title = "カテゴリ別 予算 vs 実績(円)",
        text = "カテゴリ別 予算 vs 実績(円)",
        fillColor = 0xFFFFFFFF.toInt(),
        strokeColor = 0xFF2F5597.toInt(),
        strokeWidthPt = 1.5f,
        chartLabels = cats,
        chartValues = budgetVals,
        chartSeries = listOf(
            ChartSeries("予算(円)", budgetVals),
            ChartSeries("実績(円)", actualVals)
        ),
        sourceRangeText = "H11:J18"
    )
    _uiState.value = _uiState.value.copy(
        sheetObjects = _uiState.value.sheetObjects + listOf(pieChart, colChart)
    )
}

/**
 * [EN] Enables or clears AutoFilter `[▾]` headers strictly on the selected non-empty table, restoring any filtered-out rows when turned off.
 * [JA] 選択中の非空データ表に対してオートフィルター `[▾]` のON/OFFを切り替え、解除時には非表示行をすべて復元します。
 */
fun MasuSheetViewModel.onToggleAutoFilter() {
    val cur = _uiState.value
    val vp = cur.viewport
    val sIdx = vp.sheetIndex
    val isEn = cur.language == "en"

    if (cur.isAutoFilterActive || sIdx in cur.autoFilterRanges) {
        // Turn AutoFilter OFF on this sheet and restore any filtered rows
        val prevHidden = cur.filteredHiddenRows[sIdx].orEmpty()
        for (r in prevHidden) {
            customRowHeights.remove(sIdx to r)
        }
        sizeRevisionBump++
        val decorated = decorateWithCustomSizes(vp)
        _uiState.value = cur.copy(
            isAutoFilterActive = false,
            tableFilterButton = false,
            autoFilterRanges = cur.autoFilterRanges - sIdx,
            activeColumnFilters = cur.activeColumnFilters.filterKeys { it.first != sIdx },
            filteredHiddenRows = cur.filteredHiddenRows - sIdx,
            activeColumnFilterPopup = null,
            viewport = decorated,
            statusNoticeMessage = if (isEn) "Cleared AutoFilter" else "オートフィルターを解除しました"
        )
        return
    }

    val r0 = minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0)
    val r1 = maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(r0)
    val c0 = minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
    val c1 = maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(c0)
    val hasDataInSelection = (r0..r1).any { r ->
        (c0..c1).any { c -> !vp.cellAt(r, c)?.displayText.isNullOrBlank() }
    }
    val isSalesTopLeft = vp.cellAt(1, 1)?.displayText == "Quarterly Sales Dashboard" && r0 == 0 && c0 == 0
    if (!hasDataInSelection && !isSalesTopLeft) {
        // Selected cell/range is completely empty: do NOT attach AutoFilter to nearby unrelated cells!
        _uiState.value = cur.copy(
            isAutoFilterActive = false,
            statusNoticeMessage = if (isEn) {
                "Select a cell inside a table with data before enabling AutoFilter"
            } else {
                "空のセルにはオートフィルターを設定できません。表の項目またはデータ内のセルを選択してください"
            }
        )
        return
    }

    val bounds = if (r0 != r1 || c0 != c1) {
        // User explicitly selected a multi-cell range: use its top non-empty row as header
        var hdrR = r0
        while (hdrR < r1 && (c0..c1).all { cc -> vp.cellAt(hdrR, cc)?.displayText.isNullOrBlank() }) {
            hdrR++
        }
        val detected = detectTableBoundsAt(vp, hdrR, c0)
        intArrayOf(hdrR, (hdrR + 1).coerceAtMost(maxOf(r1, detected[2])), maxOf(r1, detected[2]), c0, c1)
    } else {
        detectTableBoundsAt(vp, vp.activeRow, vp.activeCol)
    }

    sizeRevisionBump++
    val decorated = decorateWithCustomSizes(vp)
    val hdrAddrStart = MasuSheetConfig.formatCellAddress(bounds[0], bounds[3])
    val hdrAddrEnd = MasuSheetConfig.formatCellAddress(bounds[0], bounds[4])
    val rangeLabel = if (bounds[3] == bounds[4]) hdrAddrStart else "$hdrAddrStart:$hdrAddrEnd"
    _uiState.value = cur.copy(
        isAutoFilterActive = true,
        tableFilterButton = true,
        autoFilterRanges = cur.autoFilterRanges + (sIdx to bounds),
        viewport = decorated,
        statusNoticeMessage = if (isEn) {
            "AutoFilter enabled on $rangeLabel"
        } else {
            "範囲 $rangeLabel にオートフィルターを設定しました"
        }
    )
}

/**
 * [EN] Smart AutoFill (`1, 2, 3 -> 4, 5, 6`, weekdays, months, quarters, dates, text+number, formulas, or copy)
 *      triggered by dragging or double-clicking the green square handle at the bottom-right corner of the selection.
 *      Records `engineStepsCount = engineWrites` so pressing Undo once reverts the entire AutoFill batch in a single step.
 * [JA] 選択範囲右下の緑のフィルハンドルをドラッグまたはダブルクリックした際のスマートオートフィル処理です。
 *      書き込んだセル数（`engineStepsCount = engineWrites`）を1つのUndoスナップショットにまとめて記録するため、
 *      「元に戻す」を1回押すだけで一括オートフィルした全行が1回で元に戻ります。
 */
fun MasuSheetViewModel.onAutoFillRange(
    srcStartRow: Int,
    srcStartCol: Int,
    srcEndRow: Int,
    srcEndCol: Int,
    targetEndRow: Int,
    targetEndCol: Int
) {
    val vp = _uiState.value.viewport
    val sIdx = vp.sheetIndex
    val r0 = minOf(srcStartRow, srcEndRow).coerceAtLeast(0)
    val r1 = maxOf(srcStartRow, srcEndRow).coerceAtLeast(r0)
    val c0 = minOf(srcStartCol, srcEndCol).coerceAtLeast(0)
    val c1 = maxOf(srcStartCol, srcEndCol).coerceAtLeast(c0)

    val rowDelta = when {
        targetEndRow > r1 -> targetEndRow - r1
        targetEndRow < r0 -> r0 - targetEndRow
        else -> 0
    }
    val colDelta = when {
        targetEndCol > c1 -> targetEndCol - c1
        targetEndCol < c0 -> c0 - targetEndCol
        else -> 0
    }
    if (rowDelta == 0 && colDelta == 0) return

    val preAutoFillSnapshot = captureCustomOverlaySnapshot()
    var engineWrites = 0
    val isVertical = rowDelta >= colDelta
    var lastSnap = vp
    val previewSamples = mutableListOf<String>()

    fun writeAutoFillCell(r: Int, c: Int, newEdit: String, templateRow: Int, templateCol: Int) {
        val addr = MasuSheetConfig.formatCellAddress(r, c)
        lastSnap = engineGateway.executeCommand(
            "cell.set",
            "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(newEdit)}}"
        )
        engineWrites++
        val templateStyle = customCellStyles[sIdx to NativeViewportSnapshot.packCellKey(templateRow, templateCol)]
        if (templateStyle != null) {
            customCellStyles[sIdx to NativeViewportSnapshot.packCellKey(r, c)] = templateStyle
        }
        if (previewSamples.size < 4 && newEdit.isNotBlank()) {
            previewSamples.add(newEdit)
        }
    }

    if (isVertical) {
        val forward = targetEndRow >= r1
        val targetRows = if (forward) ((r1 + 1)..targetEndRow).toList() else ((r0 - 1) downTo targetEndRow.coerceAtLeast(0)).toList()
        for (c in c0..c1) {
            // Collect source cells in this column
            var effR0 = r0
            // Convenience: if only 1 cell is selected (e.g. "3" at the bottom of "1, 2, 3"), check contiguous numbers above it!
            if (r0 == r1 && forward) {
                val curNum = parsePureAutoFillNumber(vp.cellAt(r0, c))
                if (curNum != null) {
                    var scanR = r0 - 1
                    while (scanR >= 0 && parsePureAutoFillNumber(vp.cellAt(scanR, c)) != null && (r0 - scanR) <= 20) {
                        effR0 = scanR
                        scanR--
                    }
                }
            }
            val laneCells = (effR0..r1).map { rr -> rr to (vp.cellAt(rr, c) ?: NativeCellModel(row = rr, col = c, displayText = "", editText = "")) }
            targetRows.forEachIndexed { stepIdx, targetR ->
                val step = if (forward) (stepIdx + 1) else -(stepIdx + 1)
                val templatePair = laneCells[((stepIdx % laneCells.size) + laneCells.size) % laneCells.size]
                val generated = computeSmartAutoFillItem(
                    laneCells = laneCells.map { it.second },
                    step = step,
                    deltaRow = targetR - templatePair.first,
                    deltaCol = 0
                )
                writeAutoFillCell(targetR, c, generated, templatePair.first, c)
            }
        }
        if (engineWrites > 0) {
            pushCustomOverlayUndoSnapshot(preAutoFillSnapshot.copy(engineStepsCount = engineWrites))
        }
        sizeRevisionBump++
        applyViewportSnapshot(lastSnap)
        val finalStartR = minOf(r0, targetEndRow).coerceAtLeast(0)
        val finalEndR = maxOf(r1, targetEndRow)
        onSelectCell(finalStartR, c0, finalEndR, c1)
    } else {
        val forward = targetEndCol >= c1
        val targetCols = if (forward) ((c1 + 1)..targetEndCol).toList() else ((c0 - 1) downTo targetEndCol.coerceAtLeast(0)).toList()
        for (r in r0..r1) {
            var effC0 = c0
            if (c0 == c1 && forward) {
                val curNum = parsePureAutoFillNumber(vp.cellAt(r, c0))
                if (curNum != null) {
                    var scanC = c0 - 1
                    while (scanC >= 0 && parsePureAutoFillNumber(vp.cellAt(r, scanC)) != null && (c0 - scanC) <= 20) {
                        effC0 = scanC
                        scanC--
                    }
                }
            }
            val laneCells = (effC0..c1).map { cc -> cc to (vp.cellAt(r, cc) ?: NativeCellModel(row = r, col = cc, displayText = "", editText = "")) }
            targetCols.forEachIndexed { stepIdx, targetC ->
                val step = if (forward) (stepIdx + 1) else -(stepIdx + 1)
                val templatePair = laneCells[((stepIdx % laneCells.size) + laneCells.size) % laneCells.size]
                val generated = computeSmartAutoFillItem(
                    laneCells = laneCells.map { it.second },
                    step = step,
                    deltaRow = 0,
                    deltaCol = targetC - templatePair.first
                )
                writeAutoFillCell(r, targetC, generated, r, templatePair.first)
            }
        }
        if (engineWrites > 0) {
            pushCustomOverlayUndoSnapshot(preAutoFillSnapshot.copy(engineStepsCount = engineWrites))
        }
        sizeRevisionBump++
        applyViewportSnapshot(lastSnap)
        val finalStartC = minOf(c0, targetEndCol).coerceAtLeast(0)
        val finalEndC = maxOf(c1, targetEndCol)
        onSelectCell(r0, finalStartC, r1, finalEndC)
    }

    val isEn = _uiState.value.language == "en"
    val previewStr = previewSamples.joinToString(", ")
    _uiState.value = _uiState.value.copy(
        statusNoticeMessage = if (isEn) {
            "AutoFill completed ($previewStr...)"
        } else {
            "オートフィル完了 ($previewStr...)"
        }
    )
}

/**
 * [EN] Fills the current selection downward to match the last contiguous populated row of adjacent left/right columns when double-tapping the fill handle.
 * [JA] 選択枠右下のフィルハンドルをダブルタップした際、隣接する左右列の最終データ行までオートフィルを自動延長します。
 */
fun MasuSheetViewModel.onDoubleTapAutoFillCornerHandle(): Int {
    val vp = _uiState.value.viewport
    val r0 = minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0)
    val r1 = maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(r0)
    val c0 = minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
    val c1 = maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(c0)

    fun isCellNonBlank(r: Int, c: Int): Boolean {
        if (r < 0 || c < 0) return false
        val cell = vp.cellAt(r, c) ?: return false
        return cell.displayText.isNotBlank() || cell.editText.isNotBlank()
    }

    val candidateCols = buildList {
        if (c0 > 0) add(c0 - 1)
        add(c1 + 1)
    }
    var targetEndRow = r1
    for (adjC in candidateCols) {
        if (isCellNonBlank(r1, adjC) || isCellNonBlank(r1 + 1, adjC)) {
            var rr = r1 + 1
            while (rr <= 500 && isCellNonBlank(rr, adjC)) {
                targetEndRow = maxOf(targetEndRow, rr)
                rr++
            }
        }
    }
    if (targetEndRow <= r1) {
        return 0
    }
    onAutoFillRange(r0, c0, r1, c1, targetEndRow, c1)
    return targetEndRow - r1
}

/**
 * [EN] Moves cell contents and custom styles from `(srcStartRow..srcEndRow, srcStartCol..srcEndCol)` to `(destStartRow, destStartCol)` via border drag.
 * [JA] 選択枠の境界ドラッグ操作により、選択セル範囲の値・数式・カスタム書式を移動先 `(destStartRow, destStartCol)` へ移動します。
 */
fun MasuSheetViewModel.onMoveSelectionRange(
    srcStartRow: Int,
    srcStartCol: Int,
    srcEndRow: Int,
    srcEndCol: Int,
    destStartRow: Int,
    destStartCol: Int
): Boolean {
    val r0 = minOf(srcStartRow, srcEndRow).coerceAtLeast(0)
    val r1 = maxOf(srcStartRow, srcEndRow).coerceAtLeast(r0)
    val c0 = minOf(srcStartCol, srcEndCol).coerceAtLeast(0)
    val c1 = maxOf(srcStartCol, srcEndCol).coerceAtLeast(c0)
    val dstR0 = destStartRow.coerceAtLeast(0)
    val dstC0 = destStartCol.coerceAtLeast(0)
    if (dstR0 == r0 && dstC0 == c0) return false

    val vp = _uiState.value.viewport
    val sIdx = vp.sheetIndex
    val height = r1 - r0
    val width = c1 - c0
    val dstR1 = dstR0 + height
    val dstC1 = dstC0 + width

    val preMoveSnapshot = captureCustomOverlaySnapshot()
    var engineWrites = 0

    // Capture source cells and custom styles before mutating
    data class MovedCellEntry(
        val dr: Int,
        val dc: Int,
        val inputText: String,
        val style: CustomCellStyleOverride?
    )
    val entries = mutableListOf<MovedCellEntry>()
    for (r in r0..r1) {
        for (c in c0..c1) {
            val cell = vp.cellAt(r, c)
            val input = cell?.editText?.ifEmpty { cell.displayText } ?: ""
            val style = customCellStyles[sIdx to NativeViewportSnapshot.packCellKey(r, c)]
            entries.add(MovedCellEntry(r - r0, c - c0, input, style))
        }
    }

    var lastSnap = vp
    // Clear source cells that do not overlap with destination
    for (r in r0..r1) {
        for (c in c0..c1) {
            if (r !in dstR0..dstR1 || c !in dstC0..dstC1) {
                val addr = MasuSheetConfig.formatCellAddress(r, c)
                lastSnap = engineGateway.executeCommand("cell.set", "{\"cell\":\"$addr\",\"input\":\"\"}")
                engineWrites++
                customCellStyles.remove(sIdx to NativeViewportSnapshot.packCellKey(r, c))
            }
        }
    }
    // Write destination cells
    for (entry in entries) {
        val tr = dstR0 + entry.dr
        val tc = dstC0 + entry.dc
        val addr = MasuSheetConfig.formatCellAddress(tr, tc)
        lastSnap = engineGateway.executeCommand(
            "cell.set",
            "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(entry.inputText)}}"
        )
        engineWrites++
        if (entry.style != null) {
            customCellStyles[sIdx to NativeViewportSnapshot.packCellKey(tr, tc)] = entry.style
        } else {
            customCellStyles.remove(sIdx to NativeViewportSnapshot.packCellKey(tr, tc))
        }
    }
    if (engineWrites > 0) {
        pushCustomOverlayUndoSnapshot(preMoveSnapshot.copy(engineStepsCount = engineWrites))
    }
    sizeRevisionBump++
    applyViewportSnapshot(lastSnap)
    onSelectCell(dstR0, dstC0, dstR1, dstC1)
    val isEn = _uiState.value.language == "en"
    val srcLabel = MasuSheetConfig.formatRangeReference(r0, c0, r1, c1)
    val dstLabel = MasuSheetConfig.formatRangeReference(dstR0, dstC0, dstR1, dstC1)
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        statusNoticeMessage = if (isEn) "Moved $srcLabel to $dstLabel" else "範囲 $srcLabel を $dstLabel へ移動しました"
    )
    return true
}

/**
 * [EN] Reorders columns `startCol..endCol` to appear immediately before `targetBeforeCol`, remapping custom column widths in tandem.
 * [JA] 列範囲 `startCol..endCol` を `targetBeforeCol` の手前へ並び替え移動し、カスタム列幅設定も追従させます。
 */
fun MasuSheetViewModel.onMoveColumns(startCol: Int, endCol: Int, targetBeforeCol: Int): Boolean {
    val c0 = minOf(startCol, endCol).coerceAtLeast(0)
    val c1 = maxOf(startCol, endCol).coerceAtLeast(c0)
    val before = targetBeforeCol.coerceAtLeast(0)
    if (before in c0..(c1 + 1)) return false

    pushCustomOverlayUndoSnapshot()
    val sIdx = _uiState.value.viewport.sheetIndex
    val count = c1 - c0 + 1

    fun remapCol(col: Int): Int {
        return if (before < c0) {
            when (col) {
                in c0..c1 -> before + (col - c0)
                in before until c0 -> col + count
                else -> col
            }
        } else {
            val ins = before - count
            when (col) {
                in c0..c1 -> ins + (col - c0)
                in (c1 + 1) until before -> col - count
                else -> col
            }
        }
    }

    val updatedWidths = customColWidths.entries.associate { (k, v) ->
        if (k.first == sIdx) (sIdx to remapCol(k.second)) to v else k to v
    }
    customColWidths.clear()
    customColWidths.putAll(updatedWidths)

    val updatedStyles = customCellStyles.entries.associate { (k, v) ->
        if (k.first == sIdx) {
            val r = (k.second ushr 32).toInt()
            val c = k.second.toInt()
            (sIdx to NativeViewportSnapshot.packCellKey(r, remapCol(c))) to v
        } else {
            k to v
        }
    }
    customCellStyles.clear()
    customCellStyles.putAll(updatedStyles)

    sizeRevisionBump++
    val snap = engineGateway.executeCommand(
        "sheet.moveColumns",
        "{\"startCol\":$c0,\"endCol\":$c1,\"beforeCol\":$before}"
    )
    applyViewportSnapshot(snap)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        statusNoticeMessage = if (isEn) {
            "Moved column(s) ${MasuSheetConfig.formatColumnLabel(c0)}:${MasuSheetConfig.formatColumnLabel(c1)}"
        } else {
            "列 ${MasuSheetConfig.formatColumnLabel(c0)}:${MasuSheetConfig.formatColumnLabel(c1)} を移動しました"
        }
    )
    return true
}

/**
 * [EN] Computes a same-column plain-text AutoComplete completion for the active cell based on `typedPrefix`.
 * [JA] アクティブセルと同じ列の入力済み文字列から、`typedPrefix` に前方一致するオートコンプリート候補を返します。
 */
fun MasuSheetViewModel.computeColumnAutoCompleteSuggestion(typedPrefix: String): String? =
    computeColumnAutoCompleteSuggestion(
        row = _uiState.value.viewport.activeRow,
        col = _uiState.value.viewport.activeCol,
        typedPrefix = typedPrefix
    )

/**
 * [EN] Scans populated non-numeric text cells in column `col` to suggest a unique case-insensitive prefix completion for `typedPrefix`.
 * [JA] 同じ列 `col` 内の既存テキストセルを走査し、入力途中の `typedPrefix` に対する補完候補文字列を算出します。
 */
fun MasuSheetViewModel.computeColumnAutoCompleteSuggestion(row: Int, col: Int, typedPrefix: String): String? {
    val prefix = typedPrefix.trim()
    if (prefix.isEmpty() || prefix.startsWith("=") || prefix.all { it.isDigit() || it == '.' || it == '-' }) {
        return null
    }
    val vp = _uiState.value.viewport
    val candidates = vp.cells.values
        .filter { it.col == col && it.row != row && !it.editText.startsWith("=") }
        .map { it.displayText.trim().ifEmpty { it.editText.trim() } }
        .filter { text ->
            text.length > prefix.length &&
                text.startsWith(prefix, ignoreCase = true) &&
                text.toDoubleOrNull() == null
        }
    if (candidates.isEmpty()) return null
    // Prefer the nearest row in the same column
    return vp.cells.values
        .filter { cell ->
            cell.col == col && cell.row != row && !cell.editText.startsWith("=") &&
                cell.displayText.trim().let { t ->
                    t.length > prefix.length && t.startsWith(prefix, ignoreCase = true) && t.toDoubleOrNull() == null
                }
        }
        .minByOrNull { kotlin.math.abs(it.row - row) }
        ?.displayText
        ?.trim()
}

/**
 * [EN] Returns the `NativeValidationModel` rule governing `(row, col)` (defaulting to the active cell), or `null` if unconstrained.
 * [JA] 指定セル `(row, col)`（既定はアクティブセル）に適用されているデータの入力規則モデルを返します。
 */
fun MasuSheetViewModel.getActiveCellValidation(
    row: Int = _uiState.value.viewport.activeRow,
    col: Int = _uiState.value.viewport.activeCol
): NativeValidationModel? {
    return _uiState.value.viewport.validationAt(row, col)
}

/**
 * [EN] Applies or clears (`clearRule = true`) a Data Validation rule (`list`, `whole`, `decimal`, `textLength`, etc.) on the target range.
 * [JA] 対象セル範囲にデータの入力規則（リスト・整数・小数・文字列長など）を適用、または `clearRule = true` で解除します。
 */
fun MasuSheetViewModel.onApplyDataValidation(
    kind: String = "list",
    formula1: String,
    formula2: String = "",
    operator: String = "between",
    targetRange: String = "",
    clearRule: Boolean = false,
    allowBlank: Boolean = true,
    inCellDropdown: Boolean = true,
    showInput: Boolean = true,
    inputTitle: String = "",
    inputMessage: String = "",
    errorTitle: String = "",
    errorMessage: String = ""
) {
    val preOverlay = captureCustomOverlaySnapshot()
    val vp = _uiState.value.viewport
    val resolvedRange = targetRange.trim().ifEmpty {
        val r0 = minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0)
        val r1 = maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(r0)
        val c0 = minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
        val c1 = maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(c0)
        val startA1 = MasuSheetConfig.formatCellAddress(r0, c0)
        val endA1 = MasuSheetConfig.formatCellAddress(r1, c1)
        if (startA1 == endA1) startA1 else "$startA1:$endA1"
    }
    val trimmedF1 = formula1.trim()
    val (resolvedOp, resolvedF1) = when {
        kind != "list" && trimmedF1.startsWith(">=") -> "greaterOrEqual" to trimmedF1.removePrefix(">=").trim()
        kind != "list" && trimmedF1.startsWith("<=") -> "lessOrEqual" to trimmedF1.removePrefix("<=").trim()
        kind != "list" && trimmedF1.startsWith(">") -> "greater" to trimmedF1.removePrefix(">").trim()
        kind != "list" && trimmedF1.startsWith("<") -> "less" to trimmedF1.removePrefix("<").trim()
        else -> operator to formula1
    }
    val isClearing = clearRule || (kind == "any" && trimmedF1 == "clear")
    val params = JSONObject().apply {
        put("range", resolvedRange)
        if (isClearing) {
            put("clear", true)
        } else {
            put("type", kind)
            put("kind", kind)
            put("allow", kind)
            put("operator", resolvedOp)
            put("formula1", resolvedF1)
            put("source", resolvedF1)
            if (formula2.isNotBlank()) {
                put("formula2", formula2)
            }
            put("ignoreBlank", allowBlank)
            put("allowBlank", allowBlank)
            put("dropdown", inCellDropdown)
            put("inCellDropdown", inCellDropdown)
            put("showInput", showInput && (inputTitle.isNotBlank() || inputMessage.isNotBlank()))
            put("inputTitle", inputTitle)
            put("inputMessage", inputMessage)
            put("showError", true)
            put("errorTitle", errorTitle)
            put("errorMessage", errorMessage)
        }
    }.toString()
    recordMacroStepIfNeeded("data.validation", params)
    val beforeDepth = vp.undoDepth
    val beforeRev = vp.revision
    val snap = engineGateway.executeCommand("data.validation", params)
    val depthDelta = if (snap.undoDepth >= beforeDepth && snap.undoDepth > 0) {
        snap.undoDepth - beforeDepth
    } else if (snap.revision > beforeRev) {
        1
    } else {
        0
    }
    pushCustomOverlayUndoSnapshot(preOverlay.copy(engineStepsCount = depthDelta))
    applyViewportSnapshot(snap)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        statusNoticeMessage = if (isClearing) {
            if (isEn) "Cleared data validation ($resolvedRange)" else "データの入力規則 ($resolvedRange) をクリアしました"
        } else {
            if (isEn) "Applied data validation ($kind -> $resolvedRange)" else "データの入力規則 ($kind -> $resolvedRange) を設定しました"
        }
    )
}

/**
 * [EN] Commits `item` chosen from an in-cell Data Validation dropdown list into the active cell.
 * [JA] セル内ドロップダウンリストから選択された項目 `item` をアクティブセルの値として確定入力します。
 */
fun MasuSheetViewModel.onSelectValidationListItem(item: String) {
    val vp = _uiState.value.viewport
    onCommitCellEdit(vp.activeRow, vp.activeCol, item)
}

/**
 * [EN] Updates frozen top rows (`newFreezeRows`) and left columns (`newFreezeCols`) when dragging freeze divider lines on the grid.
 * [JA] グリッド上の固定枠線ドラッグ操作に応じて、固定行数 (`newFreezeRows`) と固定列数 (`newFreezeCols`) を更新します。
 */
fun MasuSheetViewModel.onDragFreezePaneBoundary(newFreezeRows: Int, newFreezeCols: Int) {
    val r = newFreezeRows.coerceAtLeast(0)
    val c = newFreezeCols.coerceAtLeast(0)
    pushCustomOverlayUndoSnapshot()
    val snap = if (r == 0 && c == 0) {
        engineGateway.executeCommand("view.unfreezePanes", "{}")
    } else {
        val prevActiveR = _uiState.value.viewport.activeRow
        val prevActiveC = _uiState.value.viewport.activeCol
        engineGateway.executeCommand(
            "selection.set",
            "{\"startRow\":$r,\"startCol\":$c,\"endRow\":$r,\"endCol\":$c}"
        )
        val frzSnap = engineGateway.executeCommand("view.freezePanes", "{\"rows\":$r,\"cols\":$c}")
        engineGateway.executeCommand(
            "selection.set",
            "{\"startRow\":$prevActiveR,\"startCol\":$prevActiveC,\"endRow\":$prevActiveR,\"endCol\":$prevActiveC}"
        )
        frzSnap.copy(freezeRows = r, freezeCols = c)
    }
    applyViewportSnapshot(snap)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        freezeRows = r,
        freezeCols = c,
        statusNoticeMessage = if (r == 0 && c == 0) {
            if (isEn) "Unfroze panes" else "ウィンドウ枠の固定を解除しました"
        } else {
            if (isEn) "Froze panes ($r row(s), $c col(s))" else "ウィンドウ枠を固定しました (${r}行・${c}列)"
        }
    )
}

/**
 * [EN] Hides the currently selected column span (`selStartCol..selEndCol`) with Undo support.
 * [JA] 現在選択されている列範囲（`selStartCol..selEndCol`）を非表示にします（Undo対応）。
 */
fun MasuSheetViewModel.onHideSelectedColumns() {
    pushCustomOverlayUndoSnapshot()
    val vp = _uiState.value.viewport
    val sIdx = vp.sheetIndex
    val c0 = minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
    val c1 = maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(c0)
    for (c in c0..c1) {
        customColWidths[sIdx to c] = 0.01f
    }
    sizeRevisionBump++
    recordMacroStepIfNeeded("home.hideColumns", "{}")
    val snap = engineGateway.executeCommand("home.hideColumns", "{}")
    applyViewportSnapshot(snap)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        statusNoticeMessage = if (isEn) "Hid selected column(s)" else "選択した列を非表示にしました"
    )
}

/**
 * [EN] Restores any hidden columns adjacent to `col` (`col - 1..col + 1`) back to standard width.
 * [JA] 指定列 `col` の前後（`col - 1..col + 1`）にある非表示列を再表示して標準幅へ戻します。
 */
fun MasuSheetViewModel.onUnhideColumnsAround(col: Int) {
    pushCustomOverlayUndoSnapshot()
    val sIdx = _uiState.value.viewport.sheetIndex
    for (c in (col - 1).coerceAtLeast(0)..(col + 1)) {
        if ((customColWidths[sIdx to c] ?: (_uiState.value.viewport.colWidths.getOrNull(c) ?: 84f)) < 1f) {
            customColWidths.remove(sIdx to c)
        }
    }
    sizeRevisionBump++
    val snap = engineGateway.executeCommand(
        "home.unhideColumns",
        "{\"startCol\":${(col - 1).coerceAtLeast(0)},\"endCol\":${col + 1}}"
    )
    applyViewportSnapshot(snap)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        statusNoticeMessage = if (isEn) "Unhid column(s)" else "非表示の列を再表示しました"
    )
}

/**
 * [EN] Hides the currently selected row span (`selStartRow..selEndRow`) with Undo support.
 * [JA] 現在選択されている行範囲（`selStartRow..selEndRow`）を非表示にします（Undo対応）。
 */
fun MasuSheetViewModel.onHideSelectedRows() {
    pushCustomOverlayUndoSnapshot()
    val vp = _uiState.value.viewport
    val sIdx = vp.sheetIndex
    val r0 = minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0)
    val r1 = maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(r0)
    for (r in r0..r1) {
        customRowHeights[sIdx to r] = 0.01f
    }
    sizeRevisionBump++
    recordMacroStepIfNeeded("home.hideRows", "{}")
    val snap = engineGateway.executeCommand("home.hideRows", "{}")
    applyViewportSnapshot(snap)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        statusNoticeMessage = if (isEn) "Hid selected row(s)" else "選択した行を非表示にしました"
    )
}

/**
 * [EN] Restores any hidden rows adjacent to `row` (`row - 1..row + 1`) back to standard height.
 * [JA] 指定行 `row` の前後（`row - 1..row + 1`）にある非表示行を再表示して標準行高へ戻します。
 */
fun MasuSheetViewModel.onUnhideRowsAround(row: Int) {
    pushCustomOverlayUndoSnapshot()
    val sIdx = _uiState.value.viewport.sheetIndex
    for (r in (row - 1).coerceAtLeast(0)..(row + 1)) {
        if ((customRowHeights[sIdx to r] ?: (_uiState.value.viewport.rowHeights.getOrNull(r) ?: 24f)) < 1f) {
            customRowHeights.remove(sIdx to r)
        }
    }
    sizeRevisionBump++
    val snap = engineGateway.executeCommand(
        "home.unhideRows",
        "{\"startRow\":${(row - 1).coerceAtLeast(0)},\"endRow\":${row + 1}}"
    )
    applyViewportSnapshot(snap)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        statusNoticeMessage = if (isEn) "Unhid row(s)" else "非表示の行を再表示しました"
    )
}

/**
 * [EN] Inserts an in-cell sparkline (`line`, `column`, or `winloss`) into `locationCell` bound to `sourceRange`.
 * [JA] 元データ範囲 `sourceRange` に連動するセル内スパークライン（折れ線・縦棒・勝敗）を `locationCell` に挿入します。
 */
fun MasuSheetViewModel.onInsertSparkline(
    sourceRange: String = "",
    locationCell: String = _uiState.value.viewport.activeAddress,
    kind: String = "line",
    markers: Boolean = true
) {
    pushCustomOverlayUndoSnapshot()
    val vp = _uiState.value.viewport
    val resolvedSource = sourceRange.trim().ifEmpty {
        val r0 = minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0)
        val r1 = maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(r0)
        val c0 = minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
        val c1 = maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(c0)
        if (r0 != r1 || c0 != c1) {
            MasuSheetConfig.formatRangeReference(r0, c0, r1, c1)
        } else {
            // Auto-detect adjacent numeric cells to the left of active cell
            val ar = vp.activeRow
            val ac = vp.activeCol
            if (ac >= 2) {
                var startC = ac - 1
                while (startC > 0 && vp.cellAt(ar, startC - 1)?.displayText?.replace(",", "")?.toDoubleOrNull() != null) {
                    startC--
                }
                MasuSheetConfig.formatRangeReference(ar, startC, ar, ac - 1)
            } else {
                MasuSheetConfig.formatRangeReference(ar, (ac + 1).coerceAtLeast(0), ar, (ac + 4).coerceAtLeast(1))
            }
        }
    }
    val targetLoc = locationCell.trim().ifEmpty { vp.activeAddress }
    val params = JSONObject().apply {
        put("source", resolvedSource)
        put("dataRange", resolvedSource)
        put("location", targetLoc)
        put("kind", kind)
        put("type", kind)
        put("markers", markers)
    }.toString()
    recordMacroStepIfNeeded("insert.sparkline", params)
    val snap = engineGateway.executeCommand("insert.sparkline", params)
    applyViewportSnapshot(snap)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        statusNoticeMessage = if (isEn) "Inserted $kind sparkline ($resolvedSource → $targetLoc)" else "スパークライン ($kind: $resolvedSource → $targetLoc) を挿入しました"
    )
}
