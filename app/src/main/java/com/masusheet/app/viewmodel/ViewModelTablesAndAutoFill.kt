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
 * [EN] Sample Sheet Builder, Smart AutoFill, Formula Shifting (`H-14`), Data Validation, Freeze Panes, & Table Sort/Filter
 * Provides Monthly Budget sheet setup, smart series/formula AutoFill (preserving string literals & cross-sheet refs `H-14`), Data Validation, Freeze Panes, row/column hide/unhide, and table AutoFilter/Sort.
 *
 * ---
 * [JA / 日本語解説]
 * 月間予算シート構築、文字列リテラル・別シート参照を保護するスマートオートフィル (`H-14`)、データの入力規則、ウィンドウ枠の固定、行列の非表示／再表示、テーブルのソート・フィルタを担います。
 * ============================================================================
 */

/**
 * [EN] ViewModel action/helper `populateMonthlyBudgetSheet` handling populate monthly budget sheet while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `populateMonthlyBudgetSheet` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `ensureMonthlyBudgetCharts` handling ensure monthly budget charts while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `ensureMonthlyBudgetCharts` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * Toggles AutoFilter (`data.filter` / `tableDesign.filterButton`) strictly on the selected non-empty table.
 * - Never jumps from an empty nearby cell onto an adjacent block of numbers.
 * - Stores the exact header row & column span in `autoFilterRanges[sheetIndex]` so typing `1, 2, 3` in other cells
 *   while AutoFilter is ON never turns those cells into `[▾]` filter headers.
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
 * A5: Double-clicking / double-tapping the AutoFill corner handle automatically fills down
 * to match the last contiguous non-empty row in the adjacent left (`c0 - 1`) or right (`c1 + 1`) column,
 * ported directly from `crates/ui-egui/src/interact.rs:169-186`.
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
 * A7: Dragging the selection border moves the selected cell range `(srcStartRow..srcEndRow, srcStartCol..srcEndCol)`
 * to `(destStartRow, destStartCol)` (ported from `crates/ui-egui/src/interact.rs:457-459` `Drag::Move`).
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
 * A8: Reorders columns `[startCol..endCol]` so they are inserted before `targetBeforeCol`
 * (`sheet.moveColumns` in `crates/ui-egui/src/interact.rs:316-325` `Drag::MoveCols`).
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
 * A9: Computes same-column plain-text AutoComplete suggestion when typing into `(row, col)`
 * (ported from `crates/ui-egui/src/editor.rs:301-327`).
 */
fun MasuSheetViewModel.computeColumnAutoCompleteSuggestion(typedPrefix: String): String? =
    computeColumnAutoCompleteSuggestion(
        row = _uiState.value.viewport.activeRow,
        col = _uiState.value.viewport.activeCol,
        typedPrefix = typedPrefix
    )

/**
 * [EN] ViewModel action/helper `computeColumnAutoCompleteSuggestion` handling compute column auto complete suggestion while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `computeColumnAutoCompleteSuggestion` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * A10: Returns the Data Validation rule covering the active cell (or `(row, col)`), if any.
 */
fun MasuSheetViewModel.getActiveCellValidation(
    row: Int = _uiState.value.viewport.activeRow,
    col: Int = _uiState.value.viewport.activeCol
): NativeValidationModel? {
    return _uiState.value.viewport.validationAt(row, col)
}

/**
 * [EN] ViewModel action/helper `onApplyDataValidation` handling apply data validation while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onApplyDataValidation` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onSelectValidationListItem` handling select validation list item while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSelectValidationListItem` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSelectValidationListItem(item: String) {
    val vp = _uiState.value.viewport
    onCommitCellEdit(vp.activeRow, vp.activeCol, item)
}

/**
 * A11: Updates freeze pane boundaries by dragging the freeze divider lines directly on the grid/headers
 * (`crates/ui-egui/src/interact.rs:371-375` `Drag::Freeze`).
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
 * A12: Hides or unhides columns/rows (`crates/ui-egui/src/interact.rs:264-270`).
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
 * [EN] ViewModel action/helper `onUnhideColumnsAround` handling unhide columns around while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onUnhideColumnsAround` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onHideSelectedRows` handling hide selected rows while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onHideSelectedRows` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onUnhideRowsAround` handling unhide rows around while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onUnhideRowsAround` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * A13: Inserts an in-cell Sparkline (`line`, `column`, `winloss`) into `locationCell` from `sourceRange`
 * (`crates/ui-egui/src/quadrant.rs:605-625`).
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

/**
 * Computes live preview values `Map<Pair<Int, Int>, String>` for every target cell that will be overwritten
 * if the user releases the AutoFill drag at `(targetEndRow, targetEndCol)`.
 */
fun MasuSheetViewModel.previewAutoFillCells(
    srcStartRow: Int,
    srcStartCol: Int,
    srcEndRow: Int,
    srcEndCol: Int,
    targetEndRow: Int,
    targetEndCol: Int
): Map<Pair<Int, Int>, String> {
    val vp = _uiState.value.viewport
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
    if (rowDelta == 0 && colDelta == 0) return emptyMap()

    val isVertical = rowDelta >= colDelta
    val previewMap = LinkedHashMap<Pair<Int, Int>, String>()

    if (isVertical) {
        val forward = targetEndRow >= r1
        val targetRows = if (forward) ((r1 + 1)..targetEndRow).toList() else ((r0 - 1) downTo targetEndRow.coerceAtLeast(0)).toList()
        for (c in c0..c1) {
            var effR0 = r0
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
                previewMap[targetR to c] = generated
            }
        }
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
                previewMap[r to targetC] = generated
            }
        }
    }
    return previewMap
}

/**
 * [EN] ViewModel action/helper `parsePureAutoFillNumber` handling parse pure auto fill number while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `parsePureAutoFillNumber` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.parsePureAutoFillNumber(cell: NativeCellModel?): Double? {
    if (cell == null) return null
    val raw = cell.editText.trim().ifEmpty { cell.displayText.trim() }
    if (raw.isEmpty() || raw.startsWith("=")) return null
    return raw.replace(",", "").toDoubleOrNull()
}

/**
 * Computes the generated cell input string for AutoFill at `step` (`+1, +2, +3...` for forward, `-1, -2...` for backward).
 */
fun MasuSheetViewModel.computeSmartAutoFillItem(
    laneCells: List<NativeCellModel>,
    step: Int,
    deltaRow: Int,
    deltaCol: Int
): String {
    if (laneCells.isEmpty()) return ""
    val n = laneCells.size
    val absIdx = (((step - if (step > 0) 1 else -1) % n) + n) % n
    val templateCell = laneCells[absIdx]
    val rawEdit = templateCell.editText.trim().ifEmpty { templateCell.displayText.trim() }

    // 1. Formula relative reference shifting (`=A1+B1` -> `=A2+B2`)
    if (rawEdit.startsWith("=")) {
        return shiftFormulaReferences(rawEdit, deltaRow, deltaCol)
    }

    val texts = laneCells.map { it.displayText.trim().ifEmpty { it.editText.trim() } }

    // 2. Built-in Cyclical Lists (Weekdays, Months, Quarters in JP & EN)
    val cyclicalLists = listOf(
        listOf("月", "火", "水", "木", "金", "土", "日"),
        listOf("月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日", "日曜日"),
        listOf("(月)", "(火)", "(水)", "(木)", "(金)", "(土)", "(日)"),
        listOf("（月）", "（火）", "（水）", "（木）", "（金）", "（土）", "（日）"),
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
        listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月"),
        listOf("睦月", "如月", "弥生", "卯月", "皐月", "水無月", "文月", "葉月", "長月", "神無月", "霜月", "師走"),
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
        listOf("Q1", "Q2", "Q3", "Q4"),
        listOf("1Q", "2Q", "3Q", "4Q"),
        listOf("第1四半期", "第2四半期", "第3四半期", "第4四半期")
    )
    for (cycle in cyclicalLists) {
        val indices = texts.map { t -> cycle.indexOfFirst { it.equals(t, ignoreCase = true) } }
        if (indices.all { it >= 0 }) {
            val cycleSize = cycle.size
            val stride = if (indices.size >= 2) {
                val diff = (indices[1] - indices[0] + cycleSize) % cycleSize
                if (diff == 0) 1 else diff
            } else {
                1
            }
            val anchorIdx = if (step > 0) indices.last() else indices.first()
            val nextIdx = ((anchorIdx + step * stride) % cycleSize + cycleSize) % cycleSize
            return cycle[nextIdx]
        }
    }

    // 3. Pure Numeric Series (`1, 2, 3 -> 4, 5, 6` or `10, 20 -> 30, 40`)
    val nums = laneCells.map { parsePureAutoFillNumber(it) }
    if (nums.size >= 2 && nums.all { it != null }) {
        val cleanNums = nums.filterNotNull()
        val stride = (cleanNums.last() - cleanNums.first()) / (cleanNums.size - 1).toDouble()
        val baseVal = if (step > 0) cleanNums.last() else cleanNums.first()
        val nextVal = baseVal + stride * step
        val allInts = cleanNums.all { kotlin.math.abs(it - kotlin.math.round(it)) < 1e-6 } &&
            kotlin.math.abs(stride - kotlin.math.round(stride)) < 1e-6
        return if (allInts) {
            kotlin.math.round(nextVal).toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.4f", nextVal).trimEnd('0').trimEnd('.')
        }
    }

    // 4. Date Series (`2026/11/01`, `2026-11-01`, `11/1`, `2026年11月1日`)
    val dateRegexSlash = Regex("^(\\d{4})([/-])(\\d{1,2})\\2(\\d{1,2})$")
    val dateRegexJp = Regex("^(\\d{4})年(\\d{1,2})月(\\d{1,2})日$")
    val dateRegexShort = Regex("^(\\d{1,2})/(\\d{1,2})$")
    val lastText = if (step > 0) texts.last() else texts.first()
    dateRegexSlash.matchEntire(lastText)?.let { m ->
        return try {
            val y = m.groupValues[1].toInt()
            val sep = m.groupValues[2]
            val mo = m.groupValues[3].toInt()
            val d = m.groupValues[4].toInt()
            val padMo = m.groupValues[3].length >= 2
            val padD = m.groupValues[4].length >= 2
            val ld = java.time.LocalDate.of(y, mo, d).plusDays(step.toLong())
            val moStr = if (padMo) String.format(java.util.Locale.US, "%02d", ld.monthValue) else ld.monthValue.toString()
            val dStr = if (padD) String.format(java.util.Locale.US, "%02d", ld.dayOfMonth) else ld.dayOfMonth.toString()
            "${ld.year}$sep$moStr$sep$dStr"
        } catch (_: Exception) {
            lastText
        }
    }
    dateRegexJp.matchEntire(lastText)?.let { m ->
        return try {
            val ld = java.time.LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                .plusDays(step.toLong())
            "${ld.year}年${ld.monthValue}月${ld.dayOfMonth}日"
        } catch (_: Exception) {
            lastText
        }
    }
    dateRegexShort.matchEntire(lastText)?.let { m ->
        return try {
            val currentYear = java.time.LocalDate.now().year
            val ld = java.time.LocalDate.of(currentYear, m.groupValues[1].toInt(), m.groupValues[2].toInt())
                .plusDays(step.toLong())
            "${ld.monthValue}/${ld.dayOfMonth}"
        } catch (_: Exception) {
            lastText
        }
    }

    // 5. Text + Number Series (`項目1`, `第1回`, `A-01`, `Item 1`)
    val textNumRegex = Regex("^(.*?)(\\d+)([^\\d]*)$")
    val matched = texts.map { textNumRegex.matchEntire(it) }
    if (matched.all { it != null && (it.groupValues[1].isNotEmpty() || it.groupValues[3].isNotEmpty()) }) {
        val firstM = matched.first()!!
        val lastM = matched.last()!!
        val prefix = lastM.groupValues[1]
        val suffix = lastM.groupValues[3]
        if (matched.all { it!!.groupValues[1] == prefix && it.groupValues[3] == suffix }) {
            val numValues = matched.map { it!!.groupValues[2].toLongOrNull() ?: 0L }
            val stride = if (numValues.size >= 2) (numValues.last() - numValues.first()) / (numValues.size - 1) else 1L
            val anchorNum = if (step > 0) numValues.last() else numValues.first()
            val nextNum = (anchorNum + stride * step).coerceAtLeast(0L)
            val numStr = lastM.groupValues[2]
            val formattedNum = if (numStr.length > 1 && numStr.startsWith("0")) {
                String.format(java.util.Locale.US, "%0${numStr.length}d", nextNum)
            } else {
                nextNum.toString()
            }
            return "$prefix$formattedNum$suffix"
        }
    }

    // 6. Default: Copy / cycle source cells (`"東京"`, single number `"100"`, or multi-text `"A", "B"`)
    return rawEdit
}

/**
 * [EN] Shifts relative cell references inside a formula (`=A1+B$1+"A1"+Q1!C2`) while leaving
 *      double-quoted string literals (`"A1"`), sheet prefixes (`Q1!`), and `$` anchors untouched (`H-14`).
 * [JA] 文字列リテラル `"..."` 内、シート修飾子 `Q1!`、および `$` 絶対参照を保護したまま数式内の相対セル参照をシフトします。
 */
internal fun MasuSheetViewModel.shiftFormulaReferences(
    formula: String,
    deltaRow: Int,
    deltaCol: Int,
    skipSheetQualified: Boolean = false
): String {
    if (!formula.startsWith("=") || (deltaRow == 0 && deltaCol == 0)) return formula
    val refRegex = Regex("""(?<![0-9A-Za-z_])(\$?)([A-Z]{1,3})(\$?)(\d+)(?![0-9A-Za-z_\(!])""")
    fun shiftSegmentOutsideQuotes(seg: String): String {
        return refRegex.replace(seg) { m ->
            val startIdx = m.range.first
            if (skipSheetQualified && startIdx > 0 && seg[startIdx - 1] == '!') {
                return@replace m.value
            }
            val colDollar = m.groupValues[1]
            val colLetters = m.groupValues[2]
            val rowDollar = m.groupValues[3]
            val rowDigits = m.groupValues[4]
            if (MasuSheetConfig.parseCellAddressStrict("$colLetters$rowDigits") == null) {
                return@replace m.value
            }
            val origCol = MasuSheetConfig.parseColumnLabel(colLetters)
            val origRow0 = (rowDigits.toIntOrNull() ?: 1) - 1
            val newCol = if (colDollar == "$") origCol else (origCol + deltaCol).coerceAtLeast(0)
            val newRow0 = if (rowDollar == "$") origRow0 else (origRow0 + deltaRow).coerceAtLeast(0)
            "$colDollar${MasuSheetConfig.formatColumnLabel(newCol)}$rowDollar${newRow0 + 1}"
        }
    }
    val sb = StringBuilder(formula.length + 8)
    var inQuotes = false
    var cursor = 0
    var i = 0
    while (i < formula.length) {
        if (formula[i] == '"') {
            if (!inQuotes) {
                sb.append(shiftSegmentOutsideQuotes(formula.substring(cursor, i)))
                cursor = i
                inQuotes = true
            } else if (i + 1 < formula.length && formula[i + 1] == '"') {
                // Escaped double quote `""` inside a formula string literal
                i++
            } else {
                sb.append(formula.substring(cursor, i + 1))
                cursor = i + 1
                inQuotes = false
            }
        }
        i++
    }
    if (cursor < formula.length) {
        val tail = formula.substring(cursor)
        sb.append(if (inQuotes) tail else shiftSegmentOutsideQuotes(tail))
    }
    return sb.toString()
}

/**
 * Returns `[headerRow, dataStartRow, dataEndRow, startCol, endCol]` for the table containing `(row, col)`.
 * Automatically excludes `合計` / `Total` / `Average` footer rows from `dataStartRow..dataEndRow` so sorting and filtering
 * never move or hide the total row.
 */
fun MasuSheetViewModel.detectTableBoundsAt(vp: NativeViewportSnapshot, row: Int, col: Int): IntArray {
    val isSalesDashboard = vp.cellAt(1, 1)?.displayText == "Quarterly Sales Dashboard"
    if (isSalesDashboard && (row in 3..10 && col in 1..8 || (row == 0 && col == 0))) {
        return intArrayOf(3, 4, 7, 1, 8)
    }
    val isMonthlyBudget = vp.cellAt(0, 0)?.displayText == "月間予算表" || vp.sheetName == "月間予算"
    if (isMonthlyBudget) {
        if (row in 10..18 && col in 7..10) {
            return intArrayOf(10, 11, 17, 7, 10)
        }
        if (row in 10..24 && col in 0..5) {
            return intArrayOf(10, 11, 23, 0, 5)
        }
    }

    // If (row, col) is blank, find the nearest populated cell on the current sheet (preferring numeric table cells)
    var seedR = row.coerceAtLeast(0)
    var seedC = col.coerceAtLeast(0)
    if (vp.cellAt(seedR, seedC)?.displayText.isNullOrBlank()) {
        val nonBlankCells = vp.cells.values.filter { !it.displayText.isBlank() }
        if (nonBlankCells.isNotEmpty()) {
            val numericCells = nonBlankCells.filter { parseNumericCell(it) != null }
            val candidates = numericCells.ifEmpty { nonBlankCells }
            val nearest = candidates.minWithOrNull(
                compareBy<NativeCellModel> { kotlin.math.abs(it.row - seedR) + kotlin.math.abs(it.col - seedC) }
                    .thenBy { it.row }
                    .thenBy { it.col }
            )
            if (nearest != null) {
                seedR = nearest.row
                seedC = nearest.col
            }
        }
    }

    // Generic 2D connected table detection around (seedR, seedC)
    val maxColIdx = (vp.colWidths.size - 1).coerceAtLeast(seedC)
    val maxRowIdx = (vp.rowHeights.size - 1).coerceAtLeast(seedR + 1)
    val hiddenSet = _uiState.value.filteredHiddenRows[vp.sheetIndex].orEmpty()

    var hdrR = seedR
    var dataEnd = seedR
    var c0 = seedC
    var c1 = seedC

    for (pass in 0..2) {
        while (c0 > 0 && (hdrR..dataEnd).any { r -> !vp.cellAt(r, c0 - 1)?.displayText.isNullOrBlank() }) {
            c0--
        }
        while (c1 < maxColIdx && (hdrR..dataEnd).any { r -> !vp.cellAt(r, c1 + 1)?.displayText.isNullOrBlank() }) {
            c1++
        }
        while (hdrR > 0 && (c0..c1).any { cc -> !vp.cellAt(hdrR - 1, cc)?.displayText.isNullOrBlank() }) {
            hdrR--
        }
        while (dataEnd < maxRowIdx) {
            val nextR = dataEnd + 1
            val hasAny = nextR in hiddenSet || (c0..c1).any { cc -> !vp.cellAt(nextR, cc)?.displayText.isNullOrBlank() }
            if (!hasAny) break
            dataEnd = nextR
        }
    }

    if (dataEnd == hdrR && (c0..c1).all { cc -> vp.cellAt(hdrR, cc)?.displayText.isNullOrBlank() }) {
        dataEnd = (hdrR + 1).coerceAtMost(maxRowIdx)
    }

    while (dataEnd > hdrR + 1) {
        val firstColTxt = vp.cellAt(dataEnd, c0)?.displayText?.trim().orEmpty()
        val isFooter = firstColTxt in setOf("合計", "総計", "小計", "平均", "Total", "Grand Total", "Subtotal", "Average")
        if (isFooter) {
            dataEnd--
        } else {
            break
        }
    }
    val dataStart = (hdrR + 1).coerceAtMost(dataEnd)
    return intArrayOf(hdrR, dataStart, dataEnd, c0, c1)
}

/**
 * Returns true if `(row, col)` is a table header cell that displays an interactive `[▾]` AutoFilter & Sort button.
 * Uses `autoFilterRanges[sheetIndex]` for user-toggled AutoFilters so typing numbers (`1, 2, 3`) in other cells
 * while AutoFilter is ON never turns those cells into `[▾]` filter headers.
 */
fun MasuSheetViewModel.isTableHeaderCell(vp: NativeViewportSnapshot, row: Int, col: Int): Boolean {
    val cellTxt = vp.cellAt(row, col)?.displayText?.trim().orEmpty()
    if (cellTxt.isEmpty()) return false
    val isSalesDashboard = vp.cellAt(1, 1)?.displayText == "Quarterly Sales Dashboard"
    if (isSalesDashboard && row == 3 && col in 1..8) return true
    val isMonthlyBudget = vp.cellAt(0, 0)?.displayText == "月間予算表" || vp.sheetName == "月間予算"
    if (isMonthlyBudget && row == 10 && (col in 0..5 || col in 7..10)) return true
    val st = _uiState.value
    if (st.activeSample == "budget" && row == 0 && col in 0..4) return true
    if (st.activeSample == "grades" && row == 0 && col in 0..6) return true
    val afBounds = st.autoFilterRanges[vp.sheetIndex]
    if (afBounds != null && row == afBounds[0] && col in afBounds[3]..afBounds[4]) {
        return true
    }
    return false
}

/**
 * Opens the Windows 11 Spreadsheet AutoFilter & Sort popup menu (`SpreadsheetColumnFilterPopupDialog`) for column `col`.
 */
fun MasuSheetViewModel.onOpenColumnFilterMenu(row: Int, col: Int) {
    val cur = _uiState.value
    val vp = cur.viewport
    val bounds = detectTableBoundsAt(vp, row, col)
    val hdrR = bounds[0]
    val rStart = bounds[1]
    val rEnd = bounds[2]
    val cStart = bounds[3]
    val cEnd = bounds[4]
    val targetCol = col.coerceIn(cStart, cEnd)
    val colTitle = vp.cellAt(hdrR, targetCol)?.displayText?.trim()?.ifEmpty {
        MasuSheetConfig.formatColumnLabel(targetCol)
    } ?: MasuSheetConfig.formatColumnLabel(targetCol)

    val rawValues = mutableListOf<String>()
    var hasBlanks = false
    var numericCount = 0
    var nonEmptyCount = 0
    for (r in rStart..rEnd) {
        val disp = vp.cellAt(r, targetCol)?.displayText?.trim().orEmpty()
        if (disp.isEmpty()) {
            hasBlanks = true
        } else {
            rawValues.add(disp)
            nonEmptyCount++
            val cleaned = disp.replace(",", "").replace("¥", "").replace("￥", "").replace("$", "").replace("%", "").trim()
            if (cleaned.toDoubleOrNull() != null) {
                numericCount++
            }
        }
    }
    val isNumericCol = nonEmptyCount > 0 && numericCount * 2 >= nonEmptyCount
    val distinct = if (isNumericCol) {
        rawValues.distinct().sortedBy {
            it.replace(",", "").replace("¥", "").replace("￥", "").replace("$", "").replace("%", "").trim().toDoubleOrNull() ?: Double.MAX_VALUE
        }
    } else {
        rawValues.distinct().sorted()
    }
    val filterKey = vp.sheetIndex to targetCol
    val existingAllowed = cur.activeColumnFilters[filterKey]
    val checkedSet = if (existingAllowed != null) {
        distinct.filter { it in existingAllowed }.toSet()
    } else {
        distinct.toSet()
    }
    val includeBlanks = existingAllowed == null || "(空白セル)" in existingAllowed

    onSelectCell(hdrR, targetCol)
    _uiState.value = _uiState.value.copy(
        activeColumnFilterPopup = TableColumnFilterPopupState(
            headerRow = hdrR,
            col = targetCol,
            tableStartRow = rStart,
            tableEndRow = rEnd,
            tableStartCol = cStart,
            tableEndCol = cEnd,
            columnTitle = colTitle,
            distinctValues = distinct,
            checkedValues = checkedSet,
            hasBlanks = hasBlanks,
            includeBlanks = includeBlanks,
            isNumericColumn = isNumericCol,
            currentSortOrder = cur.activeSortOrder
        )
    )
}

/**
 * [EN] ViewModel action/helper `onDismissColumnFilterMenu` handling dismiss column filter menu while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onDismissColumnFilterMenu` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onDismissColumnFilterMenu() {
    _uiState.value = _uiState.value.copy(activeColumnFilterPopup = null)
}

/**
 * Sorts the active table's data rows (`tableStartRow..tableEndRow`) by column `col` (ascending or descending),
 * automatically adjusting row-relative formula references while preserving the header row and `合計`/`Total` row!
 */
fun MasuSheetViewModel.onSortTableByColumn(col: Int, ascending: Boolean) {
    val cur = _uiState.value
    val vp = cur.viewport
    val popup = cur.activeColumnFilterPopup
    val bounds = if (popup != null) {
        intArrayOf(popup.headerRow, popup.tableStartRow, popup.tableEndRow, popup.tableStartCol, popup.tableEndCol)
    } else {
        detectTableBoundsAt(vp, vp.activeRow, col)
    }
    val rStart = bounds[1]
    val rEnd = bounds[2]
    val cStart = bounds[3]
    val cEnd = bounds[4]
    if (rEnd <= rStart) {
        _uiState.value = cur.copy(
            activeSortOrder = if (ascending) "asc" else "desc",
            activeColumnFilterPopup = null
        )
        return
    }
    val targetCol = col.coerceIn(cStart, cEnd)

    data class RowSnapshot(
        val originalRow: Int,
        val sortText: String,
        val sortNum: Double?,
        val cellsByCol: Map<Int, Pair<String, String>>,
        val styleByCol: Map<Int, CustomCellStyleOverride?>
    )

    val sIdx = vp.sheetIndex
    val rowSnapshots = (rStart..rEnd).map { r ->
        val sortCell = vp.cellAt(r, targetCol)
        val disp = sortCell?.displayText?.trim().orEmpty()
        val num = disp
            .removePrefix("(").removeSuffix(")")
            .replace(",", "").replace("¥", "").replace("￥", "").replace("$", "").replace("%", "").trim()
            .toDoubleOrNull()
            ?.let { if (disp.startsWith("(") && disp.endsWith(")")) -it else it }
        val cellMap = (cStart..cEnd).associateWith { c ->
            val cm = vp.cellAt(r, c)
            (cm?.displayText.orEmpty()) to (cm?.editText.orEmpty())
        }
        val styleMap = (cStart..cEnd).associateWith { c ->
            customCellStyles[sIdx to NativeViewportSnapshot.packCellKey(r, c)]
        }
        RowSnapshot(r, disp, num, cellMap, styleMap)
    }

    val sortedRows = rowSnapshots.sortedWith { a, b ->
        if (a.sortText.isEmpty() && b.sortText.isEmpty()) 0
        else if (a.sortText.isEmpty()) 1
        else if (b.sortText.isEmpty()) -1
        else {
            val cmp = if (a.sortNum != null && b.sortNum != null) {
                a.sortNum.compareTo(b.sortNum)
            } else {
                a.sortText.compareTo(b.sortText)
            }
            if (ascending) cmp else -cmp
        }
    }

    fun rewriteRowFormula(formula: String, oldRow0: Int, newRow0: Int): String {
        if (!formula.startsWith("=") || oldRow0 == newRow0) return formula
        val oldRow1 = oldRow0 + 1
        val newRow1 = newRow0 + 1
        val rowRefRegex = Regex("""(?<![0-9A-Za-z_\$!])(\$?[A-Z]{1,3})$oldRow1(?![0-9A-Za-z_\(!])""")
        val sb = StringBuilder(formula.length + 8)
        var inQuotes = false
        var cursor = 0
        var i = 0
        while (i < formula.length) {
            if (formula[i] == '"') {
                if (!inQuotes) {
                    sb.append(rowRefRegex.replace(formula.substring(cursor, i)) { "${it.groupValues[1]}$newRow1" })
                    cursor = i
                    inQuotes = true
                } else if (i + 1 < formula.length && formula[i + 1] == '"') {
                    i++
                } else {
                    sb.append(formula.substring(cursor, i + 1))
                    cursor = i + 1
                    inQuotes = false
                }
            }
            i++
        }
        if (cursor < formula.length) {
            val tail = formula.substring(cursor)
            sb.append(if (inQuotes) tail else rowRefRegex.replace(tail) { "${it.groupValues[1]}$newRow1" })
        }
        return sb.toString()
    }

    var lastSnap = vp
    sortedRows.forEachIndexed { idx, rowSnap ->
        val targetR = rStart + idx
        val bandBg = if (idx % 2 == 0) 0xFFD9E1F2.toInt() else 0xFFFFFFFF.toInt()
        for (c in cStart..cEnd) {
            val (_, rawEdit) = rowSnap.cellsByCol[c] ?: ("" to "")
            val rewrittenInput = rewriteRowFormula(rawEdit, rowSnap.originalRow, targetR)
            val addr = MasuSheetConfig.formatCellAddress(targetR, c)
            lastSnap = engineGateway.executeCommand(
                "cell.set",
                "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(rewrittenInput)}}"
            )
            val origStyle = rowSnap.styleByCol[c]
            if (origStyle != null) {
                val keepYellow = origStyle.bgColor == 0xFFFFF2CC.toInt()
                customCellStyles[sIdx to NativeViewportSnapshot.packCellKey(targetR, c)] = origStyle.copy(
                    bgColor = if (keepYellow) origStyle.bgColor else if (origStyle.bgColor != 0) bandBg else 0
                )
            }
        }
    }
    sizeRevisionBump++
    applyViewportSnapshot(lastSnap)
    recomputeFilteredRowsForTable(sIdx, rStart, rEnd, cStart, cEnd)
    val isEn = _uiState.value.language == "en"
    val colName = vp.cellAt(bounds[0], targetCol)?.displayText?.ifBlank { MasuSheetConfig.formatColumnLabel(targetCol) }
        ?: MasuSheetConfig.formatColumnLabel(targetCol)
    _uiState.value = _uiState.value.copy(
        activeSortOrder = if (ascending) "asc" else "desc",
        activeColumnFilterPopup = null,
        statusNoticeMessage = if (isEn) {
            "Sorted \"$colName\" in ${if (ascending) "ascending (A→Z)" else "descending (Z→A)"} order"
        } else {
            "「$colName」列を${if (ascending) "昇順 (A→Z)" else "降順 (Z→A)"}で並べ替えました"
        }
    )
}

/**
 * [EN] ViewModel action/helper `onSortTableByColor` handling sort table by color while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSortTableByColor` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSortTableByColor(col: Int) {
    onSortTableByColumn(col, ascending = true)
}

/**
 * Applies the checked value filter (and optional custom text/number operator filter) on column `col`,
 * collapsing non-matching rows in the table and updating the column's `[▾]` header badge.
 */
fun MasuSheetViewModel.onApplyColumnFilter(
    col: Int,
    checkedValues: Set<String>,
    includeBlanks: Boolean,
    customOp: String = "",
    customVal: String = ""
) {
    val cur = _uiState.value
    val vp = cur.viewport
    val popup = cur.activeColumnFilterPopup
    val bounds = if (popup != null) {
        intArrayOf(popup.headerRow, popup.tableStartRow, popup.tableEndRow, popup.tableStartCol, popup.tableEndCol)
    } else {
        detectTableBoundsAt(vp, vp.activeRow, col)
    }
    val rStart = bounds[1]
    val rEnd = bounds[2]
    val cStart = bounds[3]
    val cEnd = bounds[4]
    val targetCol = col.coerceIn(cStart, cEnd)
    val sIdx = vp.sheetIndex

    // Compute effective allowed set considering customOp / customVal if specified
    val allDistinct = popup?.distinctValues ?: (rStart..rEnd).mapNotNull { r ->
        vp.cellAt(r, targetCol)?.displayText?.trim()?.takeIf { it.isNotEmpty() }
    }.distinct()

    val numsInCol = allDistinct.mapNotNull {
        it.replace(",", "").replace("¥", "").replace("￥", "").replace("$", "").replace("%", "").trim().toDoubleOrNull()
    }
    val avgVal = if (numsInCol.isNotEmpty()) numsInCol.average() else 0.0
    val opClean = customOp.trim()
    val valClean = customVal.trim()
    val valNum = valClean.replace(",", "").replace("¥", "").replace("￥", "").replace("$", "").replace("%", "").trim().toDoubleOrNull()

    val filteredByCustomOp = if (opClean.isNotEmpty() && (valClean.isNotEmpty() || opClean in setOf("aboveAvg", "belowAvg"))) {
        allDistinct.filter { item ->
            val itemNum = item.replace(",", "").replace("¥", "").replace("￥", "").replace("$", "").replace("%", "").trim().toDoubleOrNull()
            when (opClean) {
                "equals" -> item.equals(valClean, ignoreCase = true) || (itemNum != null && valNum != null && Math.abs(itemNum - valNum) < 1e-6)
                "contains" -> item.contains(valClean, ignoreCase = true)
                "gt" -> itemNum != null && valNum != null && itemNum > valNum
                "gte" -> itemNum != null && valNum != null && itemNum >= valNum
                "lt" -> itemNum != null && valNum != null && itemNum < valNum
                "lte" -> itemNum != null && valNum != null && itemNum <= valNum
                "aboveAvg" -> itemNum != null && itemNum > avgVal
                "belowAvg" -> itemNum != null && itemNum < avgVal
                else -> true
            }
        }.toSet()
    } else {
        checkedValues
    }

    val finalAllowed = if (includeBlanks) filteredByCustomOp + "(空白セル)" else filteredByCustomOp
    val isAllSelected = finalAllowed.containsAll(allDistinct) && includeBlanks && opClean.isEmpty()

    val updatedFilters = cur.activeColumnFilters.toMutableMap()
    if (isAllSelected) {
        updatedFilters.remove(sIdx to targetCol)
    } else {
        updatedFilters[sIdx to targetCol] = finalAllowed
    }
    _uiState.value = cur.copy(
        activeColumnFilters = updatedFilters,
        activeColumnFilterPopup = null
    )
    recomputeFilteredRowsForTable(sIdx, rStart, rEnd, cStart, cEnd)
}

/**
 * [EN] ViewModel action/helper `onClearColumnFilter` handling clear column filter while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onClearColumnFilter` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onClearColumnFilter(col: Int) {
    val cur = _uiState.value
    val vp = cur.viewport
    val popup = cur.activeColumnFilterPopup
    val bounds = if (popup != null) {
        intArrayOf(popup.headerRow, popup.tableStartRow, popup.tableEndRow, popup.tableStartCol, popup.tableEndCol)
    } else {
        detectTableBoundsAt(vp, vp.activeRow, col)
    }
    val sIdx = vp.sheetIndex
    val updatedFilters = cur.activeColumnFilters.toMutableMap()
    updatedFilters.remove(sIdx to col)
    _uiState.value = cur.copy(
        activeColumnFilters = updatedFilters,
        activeColumnFilterPopup = null
    )
    recomputeFilteredRowsForTable(sIdx, bounds[1], bounds[2], bounds[3], bounds[4])
}

/**
 * [EN] ViewModel action/helper `onClearAllFiltersOnCurrentSheet` handling clear all filters on current sheet while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onClearAllFiltersOnCurrentSheet` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onClearAllFiltersOnCurrentSheet() {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val updatedFilters = cur.activeColumnFilters.filterKeys { it.first != sIdx }
    val prevHidden = cur.filteredHiddenRows[sIdx].orEmpty()
    for (r in prevHidden) {
        customRowHeights.remove(sIdx to r)
    }
    val updatedHiddenMap = cur.filteredHiddenRows - sIdx
    sizeRevisionBump++
    val decorated = decorateWithCustomSizes(cur.viewport)
    _uiState.value = cur.copy(
        activeColumnFilters = updatedFilters,
        filteredHiddenRows = updatedHiddenMap,
        activeColumnFilterPopup = null,
        viewport = decorated
    )
}

/**
 * [EN] ViewModel action/helper `recomputeFilteredRowsForTable` handling recompute filtered rows for table while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `recomputeFilteredRowsForTable` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.recomputeFilteredRowsForTable(
    sheetIndex: Int,
    rStart: Int,
    rEnd: Int,
    cStart: Int,
    cEnd: Int
) {
    val cur = _uiState.value
    val vp = cur.viewport
    val activeFiltersInTable = cur.activeColumnFilters.filterKeys { (s, c) ->
        s == sheetIndex && c in cStart..cEnd
    }
    val hiddenOnSheet = cur.filteredHiddenRows[sheetIndex].orEmpty().toMutableSet()
    var visibleCount = 0
    for (r in rStart..rEnd) {
        val rowMatches = activeFiltersInTable.all { (key, allowedSet) ->
            val c = key.second
            val txt = vp.cellAt(r, c)?.displayText?.trim().orEmpty()
            if (txt.isEmpty()) "(空白セル)" in allowedSet else txt in allowedSet
        }
        if (rowMatches) {
            hiddenOnSheet.remove(r)
            customRowHeights.remove(sheetIndex to r)
            visibleCount++
        } else {
            hiddenOnSheet.add(r)
            customRowHeights[sheetIndex to r] = 0.01f
        }
    }
    sizeRevisionBump++
    val decorated = decorateWithCustomSizes(vp)
    val isEn = cur.language == "en"
    val totalRows = (rEnd - rStart + 1).coerceAtLeast(0)
    _uiState.value = cur.copy(
        filteredHiddenRows = cur.filteredHiddenRows + (sheetIndex to hiddenOnSheet),
        viewport = decorated,
        statusNoticeMessage = if (activeFiltersInTable.isEmpty()) {
            if (isEn) "Cleared filter (showing all $totalRows rows)" else "フィルターをクリアしました（全 $totalRows 件を表示）"
        } else {
            if (isEn) "Filter applied: $visibleCount of $totalRows records found" else "フィルター適用: $totalRows 件中 $visibleCount 件が見つかりました"
        }
    )
}

/**
 * [EN] ViewModel action/helper `onSwitchSelectedChartRowColumn` handling switch selected chart row column while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSwitchSelectedChartRowColumn` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSwitchSelectedChartRowColumn() {
    val cur = _uiState.value
    val targetId = cur.selectedObjectId ?: cur.sheetObjects.lastOrNull { it.kind == SheetObjectKind.CHART }?.id ?: return
    pushCustomOverlayUndoSnapshot(captureCustomOverlaySnapshot(engineStepsCount = 0))
    val updated = cur.sheetObjects.map { obj ->
        if (obj.id == targetId && obj.kind == SheetObjectKind.CHART && obj.chartSeries.isNotEmpty() && obj.chartLabels.isNotEmpty()) {
            val oldCats = obj.chartLabels
            val oldSeries = obj.chartSeries
            val newCats = oldSeries.map { it.name }
            val newSeries = oldCats.mapIndexed { catIdx, catName ->
                ChartSeries(
                    name = catName,
                    values = oldSeries.map { s -> s.values.getOrElse(catIdx) { 0f } }
                )
            }
            obj.copy(
                chartLabels = newCats,
                chartValues = newSeries.firstOrNull()?.values ?: obj.chartValues,
                chartSeries = newSeries
            )
        } else {
            obj
        }
    }
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = updated,
        statusNoticeMessage = if (isEn) "Switched chart rows and columns" else "グラフの行と列を切り替えました"
    )
}
