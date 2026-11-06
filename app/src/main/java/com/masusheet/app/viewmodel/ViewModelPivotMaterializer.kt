package com.masusheet.app.viewmodel

import androidx.lifecycle.ViewModel
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.CustomCellStyleOverride
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeSheetObject
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.model.PivotAggFunction
import com.masusheet.app.model.PivotTableDefinition
import com.masusheet.app.model.PivotValueFieldSpec
import kotlin.math.abs
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] PivotTable Style Palettes, 11-Mode Value Aggregation, & Grid Cell Materialization
 * Computes grouped PivotTable summaries across all 11 aggregation modes and writes styled header, subtotal, grand-total, and data cells onto the worksheet.
 *
 * ---
 * [JA / 日本語解説]
 * ピボットテーブルの配色パレット解決、全11種の集計関数計算、およびワークシート上への見出し・小計・総計・データセル描画反映を担うモジュールです。
 * ============================================================================
 */

/**
 * [EN] Resolved ARGB color palette (header, filter bar, banded rows, subtotal, grand total, and grid border) for a PivotTable style preset.
 * [JA] ピボットスタイルプリセットから解決された各部配色（見出し・フィルター・縞模様・小計・総計・罫線色）を保持します。
 */
internal data class PivotStylePalette(
    val headerBg: Int,
    val headerFg: Int,
    val filterLabelBg: Int,
    val filterValueBg: Int,
    val bandEvenBg: Int,
    val bandOddBg: Int,
    val bandColAccentBg: Int,
    val subtotalBg: Int,
    val grandTotalBg: Int,
    val grandTotalFg: Int,
    val borderColor: Int
)

/**
 * [EN] Maps a PivotTable style preset identifier (`PivotStyleLight*`, `PivotStyleMedium*`, `PivotStyleDark*`) to its `PivotStylePalette`.
 * [JA] スタイルプリセット名に対応する `PivotStylePalette`（ヘッダ・縞模様・総計行などのARGB配色）を返します。
 */
internal fun MasuSheetViewModel.resolvePivotStylePalette(stylePreset: String): PivotStylePalette = when (stylePreset) {
    "PivotStyleMedium7" -> PivotStylePalette(
        headerBg = 0xFF2E7D32.toInt(),
        headerFg = 0xFFFFFFFF.toInt(),
        filterLabelBg = 0xFFC8E6C9.toInt(),
        filterValueBg = 0xFFE8F5E9.toInt(),
        bandEvenBg = 0xFFE2EFDA.toInt(),
        bandOddBg = 0xFFFFFFFF.toInt(),
        bandColAccentBg = 0xFFD5E8D4.toInt(),
        subtotalBg = 0xFFC5E0B4.toInt(),
        grandTotalBg = 0xFFA9D08E.toInt(),
        grandTotalFg = 0xFF1B3B1A.toInt(),
        borderColor = 0xFF81C784.toInt()
    )
    "PivotStyleMedium3" -> PivotStylePalette(
        headerBg = 0xFFC55A11.toInt(),
        headerFg = 0xFFFFFFFF.toInt(),
        filterLabelBg = 0xFFF8CBAD.toInt(),
        filterValueBg = 0xFFFCE4D6.toInt(),
        bandEvenBg = 0xFFFCE4D6.toInt(),
        bandOddBg = 0xFFFFFFFF.toInt(),
        bandColAccentBg = 0xFFF8CBAD.toInt(),
        subtotalBg = 0xFFF8CBAD.toInt(),
        grandTotalBg = 0xFFF4B084.toInt(),
        grandTotalFg = 0xFF3B1D08.toInt(),
        borderColor = 0xFFE69138.toInt()
    )
    "PivotStyleMedium5" -> PivotStylePalette(
        headerBg = 0xFF5E35B1.toInt(),
        headerFg = 0xFFFFFFFF.toInt(),
        filterLabelBg = 0xFFD1C4E9.toInt(),
        filterValueBg = 0xFFEDE7F6.toInt(),
        bandEvenBg = 0xFFEDE7F6.toInt(),
        bandOddBg = 0xFFFFFFFF.toInt(),
        bandColAccentBg = 0xFFD1C4E9.toInt(),
        subtotalBg = 0xFFD1C4E9.toInt(),
        grandTotalBg = 0xFFB39DDB.toInt(),
        grandTotalFg = 0xFF1F1235.toInt(),
        borderColor = 0xFF9575CD.toInt()
    )
    "PivotStyleDark2" -> PivotStylePalette(
        headerBg = 0xFF1E293B.toInt(),
        headerFg = 0xFFFFFFFF.toInt(),
        filterLabelBg = 0xFFCBD5E1.toInt(),
        filterValueBg = 0xFFF1F5F9.toInt(),
        bandEvenBg = 0xFFE2E8F0.toInt(),
        bandOddBg = 0xFFFFFFFF.toInt(),
        bandColAccentBg = 0xFFCBD5E1.toInt(),
        subtotalBg = 0xFFCBD5E1.toInt(),
        grandTotalBg = 0xFF94A3B8.toInt(),
        grandTotalFg = 0xFF0F172A.toInt(),
        borderColor = 0xFF64748B.toInt()
    )
    else -> PivotStylePalette( // PivotStyleMedium2 (Spreadsheet Classic Blue matching 月間予算)
        headerBg = 0xFF2F5597.toInt(),
        headerFg = 0xFFFFFFFF.toInt(),
        filterLabelBg = 0xFFB4C6E7.toInt(),
        filterValueBg = 0xFFD9E1F2.toInt(),
        bandEvenBg = 0xFFD9E1F2.toInt(),
        bandOddBg = 0xFFFFFFFF.toInt(),
        bandColAccentBg = 0xFFCDD8EE.toInt(),
        subtotalBg = 0xFFC5D5F0.toInt(),
        grandTotalBg = 0xFFB4C6E7.toInt(),
        grandTotalFg = 0xFF0F172A.toInt(),
        borderColor = 0xFF8EA9DB.toInt()
    )
}

/**
 * [EN] Computes `(rawNumericString, formattedDisplayString)` for `records` under `spec.agg` (`SUM`, `AVERAGE`, `COUNT`, `MAX`, `MIN`, `PRODUCT`, `PERCENT_OF_TOTAL`).
 * [JA] 対象レコード群に対して指定集計関数（合計・平均・個数・最大・最小・積・総計比）を計算し、生数値と表示文字列のペアを返します。
 */
internal fun MasuSheetViewModel.computePivotAggregation(
    records: List<Map<String, String>>,
    spec: PivotValueFieldSpec,
    grandTotalBaseRecords: List<Map<String, String>>
): Pair<String, String> {
    val field = spec.fieldName
    val isRateField = field.contains("割合") || field.contains("率") || field.contains("%")
    val nums = records.mapNotNull { rec ->
        rec[field]?.replace(",", "")?.replace("¥", "")?.replace("%", "")?.trim()?.toDoubleOrNull()
    }
    return when (spec.agg) {
        PivotAggFunction.COUNT -> {
            val count = records.count { !it[field].isNullOrBlank() }
            count.toString() to "number"
        }
        PivotAggFunction.SUM -> {
            val sum = nums.sum()
            if (isRateField) {
                String.format(java.util.Locale.US, "%.3f", sum) to "percent"
            } else {
                kotlin.math.round(sum).toLong().toString() to "currency"
            }
        }
        PivotAggFunction.AVERAGE -> {
            val avg = if (nums.isNotEmpty()) nums.average() else 0.0
            if (isRateField) {
                String.format(java.util.Locale.US, "%.3f", avg) to "percent"
            } else {
                kotlin.math.round(avg).toLong().toString() to "currency"
            }
        }
        PivotAggFunction.MAX -> {
            val mx = nums.maxOrNull() ?: 0.0
            if (isRateField) {
                String.format(java.util.Locale.US, "%.3f", mx) to "percent"
            } else {
                kotlin.math.round(mx).toLong().toString() to "currency"
            }
        }
        PivotAggFunction.MIN -> {
            val mn = nums.minOrNull() ?: 0.0
            if (isRateField) {
                String.format(java.util.Locale.US, "%.3f", mn) to "percent"
            } else {
                kotlin.math.round(mn).toLong().toString() to "currency"
            }
        }
        PivotAggFunction.PRODUCT -> {
            val prod = if (nums.isEmpty()) 0.0 else nums.fold(1.0) { acc, d -> acc * d }
            String.format(java.util.Locale.US, "%.2f", prod) to "number"
        }
        PivotAggFunction.PERCENT_OF_TOTAL -> {
            val allNums = grandTotalBaseRecords.mapNotNull { rec ->
                rec[field]?.replace(",", "")?.replace("¥", "")?.replace("%", "")?.trim()?.toDoubleOrNull()
            }
            val grandSum = allNums.sum()
            val ratio = if (kotlin.math.abs(grandSum) > 1e-9) {
                if (nums.isNotEmpty()) nums.sum() / grandSum else 0.0
            } else {
                val totalCnt = grandTotalBaseRecords.size.coerceAtLeast(1)
                records.size.toDouble() / totalCnt.toDouble()
            }
            String.format(java.util.Locale.US, "%.4f", ratio) to "percent"
        }
    }
}

/**
 * [EN] Clears the previous footprint of `pivotId` and writes its freshly aggregated headers, grouped rows, subtotals, grand totals, and theme colors onto the target sheet.
 * [JA] 前回の出力領域をクリアした上で、最新のピボット集計結果（フィルター行・列見出し・階層行・小計・総計・配色）をシートセルへ書き出します。
 */
fun MasuSheetViewModel.materializePivotTable(pivotId: String) {
    val cur = _uiState.value
    val pivot = cur.pivotTables.firstOrNull { it.id == pivotId } ?: return
    val sIdx = pivot.targetSheetIndex

    val switchedSheet = cur.viewport.sheetIndex != sIdx
    if (switchedSheet) {
        val actSnap = engineGateway.executeCommand("sheet.activate", "{\"sheet\":$sIdx}")
        applyViewportSnapshot(actSnap)
    }

    val palette = resolvePivotStylePalette(pivot.stylePreset)

    // 1. Clear old rendered bounds on target sheet
    val clearR0 = minOf(pivot.lastRenderedStartRow, pivot.targetStartRow).coerceAtLeast(0)
    val clearR1 = maxOf(pivot.lastRenderedEndRow, pivot.targetStartRow + 1)
    val clearC0 = pivot.targetStartCol.coerceAtLeast(0)
    val clearC1 = maxOf(pivot.lastRenderedEndCol, pivot.targetStartCol + 3)

    var lastSnap = _uiState.value.viewport
    for (r in clearR0..clearR1) {
        for (c in clearC0..clearC1) {
            val addr = MasuSheetConfig.formatCellAddress(r, c)
            lastSnap = engineGateway.executeCommand("cell.set", "{\"cell\":\"$addr\",\"input\":\"\"}")
            customCellStyles.remove(sIdx to NativeViewportSnapshot.packCellKey(r, c))
        }
    }

    // If all field areas are empty (e.g. after "🧹 ピボット初期化"), leave the sheet cells blank
    if (pivot.rowFields.isEmpty() && pivot.columnFields.isEmpty() && pivot.valueFields.isEmpty() && pivot.filterFields.isEmpty()) {
        val clearedPivot = pivot.copy(
            lastRenderedStartRow = pivot.targetStartRow,
            lastRenderedEndRow = pivot.targetStartRow,
            lastRenderedEndCol = pivot.targetStartCol
        )
        sizeRevisionBump++
        applyViewportSnapshot(lastSnap)
        val latest = _uiState.value
        _uiState.value = latest.copy(
            pivotTables = latest.pivotTables.map { if (it.id == clearedPivot.id) clearedPivot else it }
        )
        return
    }

    fun writePivotCell(
        r: Int,
        c: Int,
        text: String,
        bg: Int,
        fg: Int = 0xFF0F172A.toInt(),
        bold: Boolean = false,
        hAlign: Int = 0,
        numFmt: String = ""
    ) {
        val addr = MasuSheetConfig.formatCellAddress(r, c)
        lastSnap = engineGateway.executeCommand("cell.set", "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(text)}}")
        val packedBorder = (1 shl 24) or (palette.borderColor and 0x00FFFFFF)
        customCellStyles[sIdx to NativeViewportSnapshot.packCellKey(r, c)] = CustomCellStyleOverride(
            bgColor = bg,
            fgColor = fg,
            isBold = bold,
            hAlign = hAlign,
            numberFormatKind = numFmt,
            borderTop = packedBorder,
            borderRight = packedBorder,
            borderBottom = packedBorder,
            borderLeft = packedBorder
        )
    }

    // 2. Filter sourceRecords by active report filters
    val filteredRecords = pivot.sourceRecords.filter { rec ->
        pivot.selectedFilterValues.all { (fField, fVal) ->
            fVal.isBlank() || fVal == "ALL" || rec[fField] == fVal
        }
    }

    val baseStartCol = pivot.targetStartCol
    val filterCount = pivot.filterFields.size
    val renderStartRow = if (filterCount > 0 && pivot.targetStartRow >= filterCount + 1) {
        pivot.targetStartRow - filterCount - 1
    } else {
        pivot.targetStartRow
    }

    var cursorRow = renderStartRow

    // 3. Render Report Filter rows at the top if filterFields is non-empty
    if (filterCount > 0) {
        for (fField in pivot.filterFields) {
            val activeVal = pivot.selectedFilterValues[fField]?.takeIf { it.isNotBlank() && it != "ALL" }
            val displayFilterVal = if (activeVal != null) "$activeVal ▾" else "(すべて) ▾"
            writePivotCell(
                r = cursorRow,
                c = baseStartCol,
                text = "🔎 $fField",
                bg = palette.filterLabelBg,
                fg = 0xFF0F172A.toInt(),
                bold = true,
                hAlign = 0
            )
            writePivotCell(
                r = cursorRow,
                c = baseStartCol + 1,
                text = displayFilterVal,
                bg = palette.filterValueBg,
                fg = if (activeVal != null) 0xFF1D4ED8.toInt() else 0xFF0F172A.toInt(),
                bold = activeVal != null,
                hAlign = 0
            )
            cursorRow++
        }
        cursorRow++ // 1 blank separator row between filters and pivot table header
    }

    val rFields = pivot.rowFields
    val cFields = pivot.columnFields
    val vFields = pivot.valueFields.ifEmpty {
        val fallbackField = pivot.availableFields.firstOrNull { it in pivot.numericFields }
            ?: pivot.availableFields.lastOrNull().orEmpty()
        listOf(
            PivotValueFieldSpec(
                fallbackField,
                if (fallbackField in pivot.numericFields) PivotAggFunction.SUM else PivotAggFunction.COUNT
            )
        )
    }

    val isTabularOrOutline = (pivot.reportLayout == "tabular" || pivot.reportLayout == "outline") && rFields.size >= 2
    val labelColCount = if (isTabularOrOutline) rFields.size.coerceAtMost(2) else 1

    // Distinct column values when columnFields is non-empty
    val colFieldPrimary = cFields.firstOrNull()
    val distinctColValues: List<String> = if (colFieldPrimary != null) {
        filteredRecords.mapNotNull { it[colFieldPrimary]?.takeIf { s -> s.isNotEmpty() } }
            .distinct()
            .take(8)
    } else {
        emptyList()
    }

    // 4. Render Pivot Header Row
    val headerRow = cursorRow
    if (isTabularOrOutline) {
        for (i in 0 until labelColCount) {
            writePivotCell(
                r = headerRow,
                c = baseStartCol + i,
                text = rFields[i],
                bg = palette.headerBg,
                fg = palette.headerFg,
                bold = true,
                hAlign = 0
            )
        }
    } else {
        val rowHeaderTitle = when {
            rFields.size == 1 -> rFields[0]
            rFields.size >= 2 -> "${rFields[0]} / ${rFields[1]}"
            else -> "集計"
        }
        writePivotCell(
            r = headerRow,
            c = baseStartCol,
            text = rowHeaderTitle,
            bg = palette.headerBg,
            fg = palette.headerFg,
            bold = true,
            hAlign = 0
        )
    }

    var maxColWritten = baseStartCol + labelColCount - 1
    if (distinctColValues.isNotEmpty()) {
        val primarySpec = vFields.first()
        distinctColValues.forEachIndexed { idx, colVal ->
            val c = baseStartCol + labelColCount + idx
            writePivotCell(
                r = headerRow,
                c = c,
                text = colVal,
                bg = palette.headerBg,
                fg = palette.headerFg,
                bold = true,
                hAlign = 2
            )
            maxColWritten = maxOf(maxColWritten, c)
        }
        if (pivot.grandTotalsMode != "none") {
            val gtCol = baseStartCol + labelColCount + distinctColValues.size
            writePivotCell(
                r = headerRow,
                c = gtCol,
                text = "総計 (${primarySpec.displayTitle(_uiState.value.language == "en")})",
                bg = palette.headerBg,
                fg = palette.headerFg,
                bold = true,
                hAlign = 2
            )
            maxColWritten = maxOf(maxColWritten, gtCol)
        }
    } else {
        vFields.forEachIndexed { idx, spec ->
            val c = baseStartCol + labelColCount + idx
            val headerText = if (spec.agg == PivotAggFunction.SUM && vFields.count { it.fieldName == spec.fieldName } == 1) {
                spec.fieldName
            } else {
                spec.displayTitle(_uiState.value.language == "en")
            }
            writePivotCell(
                r = headerRow,
                c = c,
                text = headerText,
                bg = palette.headerBg,
                fg = palette.headerFg,
                bold = true,
                hAlign = 2
            )
            maxColWritten = maxOf(maxColWritten, c)
        }
    }
    cursorRow++

    // Helper to write value cells for a given subset of records on `rowIdx`
    fun writeValueCellsForRow(
        rowIdx: Int,
        groupRecords: List<Map<String, String>>,
        rowBg: Int,
        rowFg: Int = 0xFF0F172A.toInt(),
        isBold: Boolean = false
    ) {
        if (distinctColValues.isNotEmpty() && colFieldPrimary != null) {
            val primarySpec = vFields.first()
            distinctColValues.forEachIndexed { cIdx, colVal ->
                val cellRecs = groupRecords.filter { it[colFieldPrimary] == colVal }
                val (valStr, fmt) = computePivotAggregation(cellRecs, primarySpec, filteredRecords)
                val cellBg = if (!isBold && pivot.bandedCols && cIdx % 2 == 1) palette.bandColAccentBg else rowBg
                writePivotCell(
                    r = rowIdx,
                    c = baseStartCol + labelColCount + cIdx,
                    text = valStr,
                    bg = cellBg,
                    fg = rowFg,
                    bold = isBold,
                    hAlign = 2,
                    numFmt = fmt
                )
            }
            if (pivot.grandTotalsMode != "none") {
                val (gtStr, gtFmt) = computePivotAggregation(groupRecords, primarySpec, filteredRecords)
                writePivotCell(
                    r = rowIdx,
                    c = baseStartCol + labelColCount + distinctColValues.size,
                    text = gtStr,
                    bg = if (isBold) rowBg else palette.subtotalBg,
                    fg = rowFg,
                    bold = true,
                    hAlign = 2,
                    numFmt = gtFmt
                )
            }
        } else {
            vFields.forEachIndexed { vIdx, spec ->
                val (valStr, fmt) = computePivotAggregation(groupRecords, spec, filteredRecords)
                val cellBg = if (!isBold && pivot.bandedCols && vIdx % 2 == 1) palette.bandColAccentBg else rowBg
                val numVal = valStr.toDoubleOrNull()
                val finalFg = if (numVal != null && numVal < 0.0) 0xFFDC2626.toInt() else rowFg
                writePivotCell(
                    r = rowIdx,
                    c = baseStartCol + labelColCount + vIdx,
                    text = valStr,
                    bg = cellBg,
                    fg = finalFg,
                    bold = isBold,
                    hAlign = 2,
                    numFmt = fmt
                )
            }
        }
    }

    // 5. Render Data Rows (0, 1, or 2+ rowFields)
    var visualRowCounter = 0
    if (rFields.isEmpty()) {
        val rowBg = if (pivot.bandedRows) palette.bandEvenBg else palette.bandOddBg
        writePivotCell(cursorRow, baseStartCol, "全体集計", bg = rowBg, bold = true)
        writeValueCellsForRow(cursorRow, filteredRecords, rowBg = rowBg, isBold = true)
        cursorRow++
    } else if (rFields.size == 1) {
        val rf0 = rFields[0]
        val grouped = filteredRecords.groupBy { it[rf0].orEmpty().ifEmpty { "(空白)" } }
        for ((groupKey, groupRecs) in grouped) {
            val rowBg = if (pivot.bandedRows && visualRowCounter % 2 == 0) palette.bandEvenBg else palette.bandOddBg
            writePivotCell(cursorRow, baseStartCol, groupKey, bg = rowBg, bold = false)
            writeValueCellsForRow(cursorRow, groupRecs, rowBg = rowBg, isBold = false)
            cursorRow++
            visualRowCounter++
        }
    } else {
        val rf0 = rFields[0]
        val rf1 = rFields[1]
        val outerGroups = filteredRecords.groupBy { it[rf0].orEmpty().ifEmpty { "(空白)" } }
        for ((outerKey, outerRecs) in outerGroups) {
            val isCollapsed = outerKey in pivot.collapsedRowGroups
            val prefixIcon = if (isCollapsed) "▸ " else "▾ "
            val innerGroups = outerRecs.groupBy { it[rf1].orEmpty().ifEmpty { "(空白)" } }

            if (!isTabularOrOutline) {
                // Compact Layout: Group Header Row + Indented Child Rows
                val grpBg = if (pivot.subtotalsMode != "none" || isCollapsed) palette.subtotalBg else palette.bandEvenBg
                writePivotCell(cursorRow, baseStartCol, "$prefixIcon$outerKey", bg = grpBg, bold = true)
                if (pivot.subtotalsMode == "top" || isCollapsed) {
                    writeValueCellsForRow(cursorRow, outerRecs, rowBg = grpBg, isBold = true)
                } else {
                    for (c in (baseStartCol + 1)..maxColWritten) {
                        writePivotCell(cursorRow, c, "", bg = grpBg, bold = true)
                    }
                }
                cursorRow++

                if (!isCollapsed) {
                    for ((innerKey, innerRecs) in innerGroups) {
                        val childBg = if (pivot.bandedRows && visualRowCounter % 2 == 0) palette.bandEvenBg else palette.bandOddBg
                        writePivotCell(cursorRow, baseStartCol, "   $innerKey", bg = childBg, bold = false)
                        writeValueCellsForRow(cursorRow, innerRecs, rowBg = childBg, isBold = false)
                        cursorRow++
                        visualRowCounter++
                    }
                    if (pivot.subtotalsMode == "bottom") {
                        writePivotCell(cursorRow, baseStartCol, "$outerKey 集計", bg = palette.subtotalBg, bold = true)
                        writeValueCellsForRow(cursorRow, outerRecs, rowBg = palette.subtotalBg, isBold = true)
                        cursorRow++
                    }
                }
            } else {
                // Tabular or Outline Layout (2 label columns: rf0 | rf1)
                if (isCollapsed) {
                    writePivotCell(cursorRow, baseStartCol, "$prefixIcon$outerKey", bg = palette.subtotalBg, bold = true)
                    writePivotCell(cursorRow, baseStartCol + 1, "(${innerGroups.size} 件折りたたみ)", bg = palette.subtotalBg, bold = false)
                    writeValueCellsForRow(cursorRow, outerRecs, rowBg = palette.subtotalBg, isBold = true)
                    cursorRow++
                } else {
                    if (pivot.reportLayout == "outline" && pivot.subtotalsMode == "top") {
                        writePivotCell(cursorRow, baseStartCol, "$prefixIcon$outerKey", bg = palette.subtotalBg, bold = true)
                        writePivotCell(cursorRow, baseStartCol + 1, "", bg = palette.subtotalBg, bold = true)
                        writeValueCellsForRow(cursorRow, outerRecs, rowBg = palette.subtotalBg, isBold = true)
                        cursorRow++
                    }
                    var isFirstInner = true
                    for ((innerKey, innerRecs) in innerGroups) {
                        val childBg = if (pivot.bandedRows && visualRowCounter % 2 == 0) palette.bandEvenBg else palette.bandOddBg
                        val outerLabel = if (isFirstInner) {
                            "$prefixIcon$outerKey"
                        } else if (pivot.repeatLabels) {
                            outerKey
                        } else {
                            ""
                        }
                        writePivotCell(cursorRow, baseStartCol, outerLabel, bg = childBg, bold = isFirstInner)
                        writePivotCell(cursorRow, baseStartCol + 1, innerKey, bg = childBg, bold = false)
                        writeValueCellsForRow(cursorRow, innerRecs, rowBg = childBg, isBold = false)
                        cursorRow++
                        visualRowCounter++
                        isFirstInner = false
                    }
                    if (pivot.subtotalsMode == "bottom") {
                        writePivotCell(cursorRow, baseStartCol, "$outerKey 集計", bg = palette.subtotalBg, bold = true)
                        writePivotCell(cursorRow, baseStartCol + 1, "", bg = palette.subtotalBg, bold = true)
                        writeValueCellsForRow(cursorRow, outerRecs, rowBg = palette.subtotalBg, isBold = true)
                        cursorRow++
                    }
                }
            }

            if (pivot.blankRows) {
                for (c in baseStartCol..maxColWritten) {
                    writePivotCell(cursorRow, c, "", bg = 0xFFFFFFFF.toInt(), bold = false)
                }
                cursorRow++
            }
        }
    }

    // 6. Render Grand Total Row at bottom
    if (pivot.grandTotalsMode != "none" && rFields.isNotEmpty()) {
        writePivotCell(
            r = cursorRow,
            c = baseStartCol,
            text = "総計",
            bg = palette.grandTotalBg,
            fg = palette.grandTotalFg,
            bold = true,
            hAlign = 0
        )
        if (labelColCount > 1) {
            writePivotCell(
                r = cursorRow,
                c = baseStartCol + 1,
                text = "",
                bg = palette.grandTotalBg,
                fg = palette.grandTotalFg,
                bold = true,
                hAlign = 0
            )
        }
        writeValueCellsForRow(
            rowIdx = cursorRow,
            groupRecords = filteredRecords,
            rowBg = palette.grandTotalBg,
            rowFg = palette.grandTotalFg,
            isBold = true
        )
        cursorRow++
    }

    // Ensure readable column widths for the rendered PivotTable
    for (c in baseStartCol..maxColWritten) {
        val curW = customColWidths[sIdx to c] ?: 84f
        val minNeeded = if (c < baseStartCol + labelColCount) 96f else 88f
        if (curW < minNeeded) {
            customColWidths[sIdx to c] = minNeeded
        }
    }

    val finalEndRow = (cursorRow - 1).coerceAtLeast(renderStartRow)
    val updatedPivot = pivot.copy(
        lastRenderedStartRow = renderStartRow,
        lastRenderedEndRow = finalEndRow,
        lastRenderedEndCol = maxColWritten
    )
    sizeRevisionBump++
    applyViewportSnapshot(lastSnap)
    val latest = _uiState.value
    _uiState.value = latest.copy(
        pivotTables = latest.pivotTables.map { if (it.id == updatedPivot.id) updatedPivot else it }
    )
}
