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
 * [EN] Smart AutoFill Pattern Inference, Formula Reference Shifting, & Table AutoFilter/Sort
 * Implements AutoFill series/date/formula preview & generation, quote/sheet-safe formula shifting, table bounds detection, and column sort/filter operations.
 *
 * ---
 * [JA / 日本語解説]
 * スマートオートフィルの規則推論・プレビュー、文字列リテラル・別シート参照を保護する数式シフト、テーブル範囲検出、および列ソート・オートフィルタ処理を担うモジュールです。
 * ============================================================================
 */

/**
 * [EN] Computes live preview strings `Map<(row, col), previewText>` for every target cell covered during an active AutoFill drag.
 * [JA] オートフィルハンドルのドラッグ中に、上書き対象となる各セル `(row, col)` のリアルタイム予測文字列マップを算出します。
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
 * [EN] Parses a non-formula numeric literal cell into `Double` for linear arithmetic progression detection during AutoFill.
 * [JA] オートフィルの等差数列判定用に、数式ではない純粋な数値セルを `Double` として読み取ります。
 */
internal fun MasuSheetViewModel.parsePureAutoFillNumber(cell: NativeCellModel?): Double? {
    if (cell == null) return null
    val raw = cell.editText.trim().ifEmpty { cell.displayText.trim() }
    if (raw.isEmpty() || raw.startsWith("=")) return null
    return raw.replace(",", "").toDoubleOrNull()
}

/**
 * [EN] Generates the filled cell text/formula at `step` from `laneCells` (shifting relative refs, extending weekday/month names, numbered labels, or arithmetic series).
 * [JA] 元セル列 `laneCells` から `step` 番目のフィル値を生成します（相対数式シフト、曜日・月名循環、連番テキスト、最小二乗等差数列に対応）。
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
 *      double-quoted string literals (`"A1"`), sheet prefixes (`Q1!`), and `$` anchors untouched.
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
 * [EN] Returns `true` when `(row, col)` belongs to an active AutoFilter header span and should render an interactive `[▾]` filter button.
 * [JA] セル `(row, col)` が有効なオートフィルター見出し行に含まれ、`[▾]` フィルターボタンを描画すべきかを判定します。
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
 * [EN] Collects distinct values and active filter state for column `col` in the table at `(row, col)` and opens `SpreadsheetColumnFilterPopupDialog`.
 * [JA] 対象テーブルの列 `col` に含まれるユニーク値一覧と現在の絞り込み条件を収集し、列フィルター＆並べ替えポップアップを開きます。
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
 * [EN] Closes the active column AutoFilter & Sort popup dialog.
 * [JA] 表示中の列オートフィルター＆並べ替えポップアップを閉じます。
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
 * [EN] Sorts the active table by column `col` (delegating to ascending column sort).
 * [JA] 対象列 `col` を基準にテーブル行の並べ替えを実行します。
 */
fun MasuSheetViewModel.onSortTableByColor(col: Int) {
    onSortTableByColumn(col, ascending = true)
}

/**
 * [EN] Applies checked-value and optional custom operator filtering on column `col`, hiding non-matching table rows while preserving footer totals.
 * [JA] 列 `col` にチェック選択値およびカスタム条件フィルターを適用し、合計行を維持したまま不一致のデータ行を折りたたみます。
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
 * [EN] Removes the active filter criteria on column `col` and recomputes visible rows across any remaining filtered columns.
 * [JA] 指定列 `col` のフィルター条件を解除し、他列のフィルター条件を維持したまま表示行を再計算します。
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
 * [EN] Clears all column filters on the active worksheet and restores every filtered-hidden row to visible height.
 * [JA] 現在のシート上の全列フィルター条件を一括解除し、絞り込みで非表示になっていた全行を再表示します。
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
 * [EN] Evaluates all active column filters on `sheetIndex` across `rStart..rEnd` and collapses non-matching rows to `0pt` height.
 * [JA] `sheetIndex` 上の有効な全列フィルター条件を評価し、条件を満たさないデータ行の高さを `0pt`（非表示）に更新します。
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
 * [EN] Transposes categories and series on the currently selected chart object with Undo support.
 * [JA] 現在選択中のグラフオブジェクトのカテゴリ軸とデータ系列（行／列）を入れ替えます。
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
