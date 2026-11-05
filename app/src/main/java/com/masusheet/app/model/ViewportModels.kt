package com.masusheet.app.model

import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.core.SpreadsheetColorPalette
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Viewport Snapshot, Cell Styling, Merge, Sparkline, & Layer Cache Models
 * Defines [GridLayerCacheKey], [NativeCellModel], [NativeViewportSnapshot], and JSON deserialization from the Rust JNI bridge for high-FPS 2-layer Canvas rendering.
 *
 * ---
 * [JA / 日本語解説]
 * Rust JNIブリッジから受け取るビューポートスナップショット ([NativeViewportSnapshot])、セルモデル ([NativeCellModel])、結合セル、スパークライン、および2層Canvasキャッシュキー ([GridLayerCacheKey]) を定義します。
 * ============================================================================
 */

/**
 * [EN] Immutable cache key for Layer 1's static bitmap; excludes selection and cursor blink state so moving selection never redraws static cells.
 * [JA] 静的セル描画レイヤー（Layer 1）のキャッシュキーです。選択位置やカーソル点滅を含めないことで、セル移動時の全画面再描画を回避します。
 */
data class GridLayerCacheKey(
    val uid: Long,
    val revision: Long,
    val sheetIndex: Int,
    val scrollX: Int,
    val scrollY: Int,
    val zoomPercent: Int,
    val isDarkTheme: Boolean,
    val viewWidth: Int,
    val viewHeight: Int,
    val showGridlines: Boolean = true,
    val showHeadings: Boolean = true,
    val showFormulas: Boolean = false,
    val freezeRows: Int = 0,
    val freezeCols: Int = 0
)

/**
 * [EN] Inclusive 0-based `(startRow, startCol, endRow, endCol)` merged cell rectangle on a worksheet.
 * [JA] シート上の結合セル範囲（開始行・開始列〜終了行・終了列、0始まり）を表すモデルです。
 */
data class NativeMergeRange(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int
) {
    fun contains(row: Int, col: Int): Boolean =
        row in startRow..endRow && col in startCol..endCol
}

/**
 * [EN] In-cell mini chart (line, column, or win/loss sparkline) bound to a source cell range.
 * [JA] セル内に描画されるスパークライン（折れ線・縦棒・勝敗）の配置セル・元データ範囲・配色モデルです。
 */
data class NativeSparklineModel(
    val row: Int,
    val col: Int,
    val source: String,
    val kind: String = "line",
    val color: String = "#1F9D55",
    val markers: Boolean = true,
    val values: List<Float> = emptyList()
) {
    val sourceRange: String get() = source
    val colorRgb: Int get() = (SpreadsheetColorPalette.parseHexOrNull(color) ?: 0xFF1F9D55.toInt()) and 0xFFFFFF
}

/**
 * [EN] Data validation rule (dropdown list, whole/decimal bounds, text length, custom formula) attached to a cell range.
 * [JA] セル範囲に設定されたデータの入力規則（リスト選択・整数／小数範囲・文字列長・エラーメッセージ等）を表すモデルです。
 */
data class NativeValidationModel(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
    val kind: String = "list",
    val operator: String = "between",
    val f1: String = "",
    val f2: String = "",
    val inCellDropdown: Boolean = true,
    val inputTitle: String = "",
    val inputMessage: String = "",
    val showInput: Boolean = true,
    val errorTitle: String = "",
    val errorMessage: String = "",
    val showError: Boolean = true,
    val items: List<String> = emptyList()
) {
    val formula1: String get() = f1
    val formula2: String get() = f2

    fun contains(row: Int, col: Int): Boolean =
        row in startRow..endRow && col in startCol..endCol
}

/**
 * [EN] Note/comment attached to a specific worksheet cell.
 * [JA] 特定セルに付与されたコメント（メモ）の番地・作成者・本文を保持するモデルです。
 */
data class NativeCommentModel(
    val cell: String,
    val author: String = "User",
    val text: String,
    val resolved: Boolean = false
)

/**
 * [EN] External URL or internal sheet cell hyperlink attached to a worksheet cell.
 * [JA] セルに設定された外部URLまたはブック内セル参照ハイパーリンクを保持するモデルです。
 */
data class NativeHyperlinkModel(
    val cell: String,
    val target: String,
    val tooltip: String = ""
)

/**
 * [EN] Workbook-scoped named range or formula constant (`Name` -> `RefersTo`).
 * [JA] ブック内で定義された名前付き範囲・定数（名前と参照先数式）を表すモデルです。
 */
data class NativeDefinedNameModel(
    val name: String,
    val refersTo: String
)

/**
 * [EN] Compact per-cell render model carrying display text, raw formula, packed style flags, ARGB colors, and 4-side borders.
 * [JA] 1セルの表示文字列、生数式、書式ビットフラグ（太字・斜体・下線・取消線・配置等）、背景／文字色、4辺罫線を保持するモデルです。
 */
data class NativeCellModel(
    val row: Int,
    val col: Int,
    val displayText: String,
    val editText: String,
    val bgColor: Int = 0,
    val fgColor: Int = 0,
    val fontSize: Float = 11f,
    val flags: Int = 0,
    val borderTop: Int = 0,
    val borderRight: Int = 0,
    val borderBottom: Int = 0,
    val borderLeft: Int = 0
) {
    val isBold: Boolean get() = (flags and 1) != 0
    val isItalic: Boolean get() = (flags and 2) != 0
    val isUnderline: Boolean get() = (flags and 4) != 0
    val isStrike: Boolean get() = (flags and 8) != 0
    val isWrap: Boolean get() = (flags and 16) != 0
    val hAlign: Int get() = (flags ushr 5) and 3 // 0 = Left, 1 = Center, 2 = Right
    val vAlign: Int get() = (flags ushr 7) and 3 // 0 = Top, 1 = Center, 2 = Bottom
}

/**
 * [EN] Immutable snapshot of the active worksheet's visible geometry, cumulative row/col offsets, cells, merges, and selection.
 * [JA] アクティブシートの累積行高・列幅オフセット、セル辞書、結合範囲、固定枠、選択範囲を保持する不変スナップショットです。
 */
@androidx.compose.runtime.Immutable
data class NativeViewportSnapshot(
    val ok: Boolean = true,
    val error: String? = null,
    val resultJson: String? = null,
    val sessionId: Long = 0L,
    val uid: Long = 1L,
    val revision: Long = 1L,
    val undoDepth: Int = 0,
    val redoDepth: Int = 0,
    val title: String = "Sales.xlsx",
    val isDirty: Boolean = false,
    val sheetIndex: Int = 0,
    val totalSheetCount: Int = 1,
    val sheetName: String = "Sales",
    val sheetNames: List<String> = listOf("Sales"),
    val visibleSheetIndices: List<Int> = listOf(0),
    val hiddenSheetNames: List<String> = emptyList(),
    val hiddenSheetIndices: List<Int> = emptyList(),
    val usedEndRow: Int = 0,
    val usedEndCol: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val activeRow: Int = 0,
    val activeCol: Int = 0,
    val selStartRow: Int = 0,
    val selStartCol: Int = 0,
    val selEndRow: Int = 0,
    val selEndCol: Int = 0,
    val activeAddress: String = "A1",
    val activeEditText: String = "",
    val activeDisplayText: String = "",
    val activeSpillFormula: String = "",
    val freezeRows: Int = 0,
    val freezeCols: Int = 0,
    val colWidths: FloatArray = FloatArray(26) { 72f },
    val rowHeights: FloatArray = FloatArray(80) { 22f },
    val hiddenRows: IntArray = IntArray(0),
    val hiddenCols: IntArray = IntArray(0),
    val merges: List<NativeMergeRange> = emptyList(),
    val spillRanges: List<NativeMergeRange> = emptyList(),
    val sparklines: List<NativeSparklineModel> = emptyList(),
    val validations: List<NativeValidationModel> = emptyList(),
    val comments: List<NativeCommentModel> = emptyList(),
    val hyperlinks: List<NativeHyperlinkModel> = emptyList(),
    val definedNames: List<NativeDefinedNameModel> = emptyList(),
    val cells: Map<Long, NativeCellModel> = emptyMap(),
    val themeColors: List<Int> = SpreadsheetColorPalette.DEFAULT_THEME_BASE_COLORS
) {
    val colOffsets: FloatArray = FloatArray(colWidths.size + 1).also { acc ->
        var sum = 0f
        for (i in colWidths.indices) {
            acc[i] = sum
            val w = colWidths[i]
            sum += if (w < 1f) 0f else w
        }
        acc[colWidths.size] = sum
    }

    val rowOffsets: FloatArray = FloatArray(rowHeights.size + 1).also { acc ->
        var sum = 0f
        for (i in rowHeights.indices) {
            acc[i] = sum
            val h = rowHeights[i]
            sum += if (h < 1f) 0f else h
        }
        acc[rowHeights.size] = sum
    }

    val totalSheetWidthPt: Float get() = colOffsets.lastOrNull() ?: 1800f
    val totalSheetHeightPt: Float get() = rowOffsets.lastOrNull() ?: 1600f

    fun cellAt(row: Int, col: Int): NativeCellModel? = cells[packCellKey(row, col)]

    fun mergeAt(row: Int, col: Int): NativeMergeRange? =
        merges.firstOrNull { it.contains(row, col) }

    fun spillRangeAt(row: Int, col: Int): NativeMergeRange? =
        spillRanges.firstOrNull { it.contains(row, col) }

    fun sparklineAt(row: Int, col: Int): NativeSparklineModel? =
        sparklines.firstOrNull { it.row == row && it.col == col }

    fun validationAt(row: Int, col: Int): NativeValidationModel? =
        validations.firstOrNull { it.contains(row, col) }

    fun resolveAnchorCell(row: Int, col: Int): Pair<Int, Int> {
        val m = mergeAt(row, col)
        return if (m != null) m.startRow to m.startCol else row to col
    }

    fun colAtX(sheetXPt: Float): Int {
        val clamped = sheetXPt.coerceAtLeast(0f)
        var lo = 0
        var hi = (colWidths.size - 1).coerceAtLeast(0)
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (colOffsets[mid + 1] <= clamped) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        // Skip hidden columns (width < 1f)
        while (lo < colWidths.size - 1 && colWidths[lo] < 1f) {
            lo++
        }
        return lo
    }

    fun rowAtY(sheetYPt: Float): Int {
        val clamped = sheetYPt.coerceAtLeast(0f)
        var lo = 0
        var hi = (rowHeights.size - 1).coerceAtLeast(0)
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (rowOffsets[mid + 1] <= clamped) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        // Skip hidden rows (height < 1f)
        while (lo < rowHeights.size - 1 && rowHeights[lo] < 1f) {
            lo++
        }
        return lo
    }

    fun cellBoundsPt(row: Int, col: Int): FloatArray {
        val m = mergeAt(row, col)
        val r0 = (m?.startRow ?: row).coerceIn(0, (rowHeights.size - 1).coerceAtLeast(0))
        val c0 = (m?.startCol ?: col).coerceIn(0, (colWidths.size - 1).coerceAtLeast(0))
        val r1 = (m?.endRow ?: row).coerceIn(r0, (rowHeights.size - 1).coerceAtLeast(0))
        val c1 = (m?.endCol ?: col).coerceIn(c0, (colWidths.size - 1).coerceAtLeast(0))
        return floatArrayOf(
            colOffsets[c0],
            rowOffsets[r0],
            colOffsets[c1 + 1],
            rowOffsets[r1 + 1]
        )
    }

    fun withUpdatedColWidth(col: Int, newWidthPt: Float): NativeViewportSnapshot {
        if (col !in colWidths.indices) return this
        val updated = colWidths.copyOf()
        updated[col] = if (newWidthPt < 1f) 0f else newWidthPt.coerceIn(24f, 600f)
        return copy(revision = revision + 1, colWidths = updated)
    }

    fun withUpdatedRowHeight(row: Int, newHeightPt: Float): NativeViewportSnapshot {
        if (row !in rowHeights.indices) return this
        val updated = rowHeights.copyOf()
        updated[row] = if (newHeightPt < 1f) 0f else newHeightPt.coerceIn(16f, 400f)
        return copy(revision = revision + 1, rowHeights = updated)
    }

    /**
     * Computes Spreadsheet's bottom Status Bar AutoCalculate summary for the currently selected range:
     * Average (平均), Count (データの個数), Min (最小値), Max (最大値), and Sum (合計).
     */
    fun computeSelectionAutoCalcSummary(isEn: Boolean = false): String {
        val r0 = minOf(selStartRow, selEndRow).coerceAtLeast(0)
        val r1 = maxOf(selStartRow, selEndRow).coerceAtMost((rowHeights.size - 1).coerceAtLeast(r0))
        val c0 = minOf(selStartCol, selEndCol).coerceAtLeast(0)
        val c1 = maxOf(selStartCol, selEndCol).coerceAtMost((colWidths.size - 1).coerceAtLeast(c0))

        var nonEmptyCount = 0
        val nums = mutableListOf<Double>()
        for (r in r0..r1) {
            for (c in c0..c1) {
                val cell = cellAt(r, c) ?: continue
                val disp = cell.displayText.trim()
                if (disp.isNotEmpty()) {
                    nonEmptyCount++
                    val parsed = MasuSheetConfig.parseFiniteFloatOrNull(disp)?.toDouble()
                    if (parsed != null && parsed.isFinite()) {
                        nums.add(parsed)
                    }
                }
            }
        }
        if (nonEmptyCount == 0) return ""
        fun fmt(v: Double): String =
            if (Math.abs(v - Math.round(v)) < 1e-6) String.format("%,d", Math.round(v)) else String.format("%,.2f", v)

        if (nums.isEmpty()) {
            return if (nonEmptyCount > 1) {
                if (isEn) "Count: $nonEmptyCount" else "データの個数: $nonEmptyCount"
            } else {
                ""
            }
        }
        val sum = nums.sum()
        val avg = sum / nums.size
        val minVal = nums.minOrNull() ?: 0.0
        val maxVal = nums.maxOrNull() ?: 0.0
        return if (nums.size == 1 && r0 == r1 && c0 == c1) {
            if (isEn) "Value: ${fmt(sum)}" else "値: ${fmt(sum)}"
        } else if (isEn) {
            "Average: ${fmt(avg)}   Count: $nonEmptyCount   Min: ${fmt(minVal)}   Max: ${fmt(maxVal)}   Sum: ${fmt(sum)}"
        } else {
            "平均: ${fmt(avg)}   データの個数: $nonEmptyCount   最小値: ${fmt(minVal)}   最大値: ${fmt(maxVal)}   合計: ${fmt(sum)}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NativeViewportSnapshot) return false
        return ok == other.ok &&
            error == other.error &&
            resultJson == other.resultJson &&
            sessionId == other.sessionId &&
            uid == other.uid &&
            revision == other.revision &&
            undoDepth == other.undoDepth &&
            redoDepth == other.redoDepth &&
            title == other.title &&
            isDirty == other.isDirty &&
            sheetIndex == other.sheetIndex &&
            totalSheetCount == other.totalSheetCount &&
            sheetName == other.sheetName &&
            sheetNames == other.sheetNames &&
            visibleSheetIndices == other.visibleSheetIndices &&
            hiddenSheetNames == other.hiddenSheetNames &&
            hiddenSheetIndices == other.hiddenSheetIndices &&
            usedEndRow == other.usedEndRow &&
            usedEndCol == other.usedEndCol &&
            canUndo == other.canUndo &&
            canRedo == other.canRedo &&
            activeRow == other.activeRow &&
            activeCol == other.activeCol &&
            selStartRow == other.selStartRow &&
            selStartCol == other.selStartCol &&
            selEndRow == other.selEndRow &&
            selEndCol == other.selEndCol &&
            activeAddress == other.activeAddress &&
            activeEditText == other.activeEditText &&
            activeDisplayText == other.activeDisplayText &&
            activeSpillFormula == other.activeSpillFormula &&
            freezeRows == other.freezeRows &&
            freezeCols == other.freezeCols &&
            colWidths.contentEquals(other.colWidths) &&
            rowHeights.contentEquals(other.rowHeights) &&
            hiddenRows.contentEquals(other.hiddenRows) &&
            hiddenCols.contentEquals(other.hiddenCols) &&
            merges == other.merges &&
            spillRanges == other.spillRanges &&
            sparklines == other.sparklines &&
            validations == other.validations &&
            comments == other.comments &&
            hyperlinks == other.hyperlinks &&
            definedNames == other.definedNames &&
            cells == other.cells &&
            themeColors == other.themeColors
    }

    override fun hashCode(): Int {
        var result = ok.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + uid.hashCode()
        result = 31 * result + revision.hashCode()
        result = 31 * result + undoDepth
        result = 31 * result + redoDepth
        result = 31 * result + title.hashCode()
        result = 31 * result + isDirty.hashCode()
        result = 31 * result + sheetIndex
        result = 31 * result + sheetNames.hashCode()
        result = 31 * result + visibleSheetIndices.hashCode()
        result = 31 * result + activeRow
        result = 31 * result + activeCol
        result = 31 * result + selStartRow
        result = 31 * result + selStartCol
        result = 31 * result + selEndRow
        result = 31 * result + selEndCol
        result = 31 * result + colWidths.contentHashCode()
        result = 31 * result + rowHeights.contentHashCode()
        result = 31 * result + hiddenRows.contentHashCode()
        result = 31 * result + hiddenCols.contentHashCode()
        result = 31 * result + cells.hashCode()
        return result
    }

    companion object {
        fun packCellKey(row: Int, col: Int): Long =
            (row.toLong() shl 32) or (col.toLong() and 0xFFFF_FFFFL)

        fun fromJsonOrFallback(
            jsonString: String,
            previous: NativeViewportSnapshot? = null
        ): NativeViewportSnapshot {
            val parsed = fromJson(jsonString)
            if (!parsed.ok && parsed.cells.isEmpty() && previous != null && previous.cells.isNotEmpty()) {
                return previous.copy(ok = false, error = parsed.error)
            }
            return parsed
        }

        fun fromJson(jsonString: String): NativeViewportSnapshot {
            return try {
                val root = JSONObject(jsonString)
                val ok = root.optBoolean("ok", true)
                val err = if (root.has("error") && !root.isNull("error")) root.optString("error") else null
                val resJson = if (root.has("result") && !root.isNull("result")) root.get("result").toString() else null
                val sessionId = root.optLong("sessionId", 0L)
                val uid = root.optLong("uid", 1L)
                val rev = root.optLong("revision", 1L)
                val canUndo = root.optBoolean("canUndo", false)
                val canRedo = root.optBoolean("canRedo", false)
                val undoDepth = root.optInt("undoDepth", if (canUndo) 1 else 0).coerceAtLeast(0)
                val redoDepth = root.optInt("redoDepth", if (canRedo) 1 else 0).coerceAtLeast(0)
                val title = root.optString("title", "Sales.xlsx")
                val isDirty = root.optBoolean("isDirty", false)
                val sheetIndex = root.optInt("sheetIndex", 0)
                val sheetName = root.optString("sheetName", "Sheet1")
                val namesArr = root.optJSONArray("sheetNames")
                val sheetNames = if (namesArr != null && namesArr.length() > 0) {
                    List(namesArr.length()) { i -> namesArr.optString(i, "Sheet${i + 1}") }
                } else {
                    listOf(sheetName)
                }
                val visIdxArr = root.optJSONArray("visibleSheetIndices")
                val visibleSheetIndices = if (visIdxArr != null && visIdxArr.length() > 0) {
                    List(visIdxArr.length()) { i -> visIdxArr.optInt(i, i) }
                } else {
                    sheetNames.indices.toList()
                }
                val hidNamesArr = root.optJSONArray("hiddenSheetNames")
                val hiddenSheetNames = if (hidNamesArr != null && hidNamesArr.length() > 0) {
                    List(hidNamesArr.length()) { i -> hidNamesArr.optString(i, "") }.filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
                val hidIdxArr = root.optJSONArray("hiddenSheetIndices")
                val hiddenSheetIndices = if (hidIdxArr != null && hidIdxArr.length() > 0) {
                    List(hidIdxArr.length()) { i -> hidIdxArr.optInt(i, -1) }.filter { it >= 0 }
                } else {
                    emptyList()
                }
                val totalSheetCount = root.optInt("totalSheetCount", sheetNames.size + hiddenSheetNames.size).coerceAtLeast(1)
                val usedEndRow = root.optInt("usedEndRow", 0).coerceAtLeast(0)
                val usedEndCol = root.optInt("usedEndCol", 0).coerceAtLeast(0)
                val activeRow = root.optInt("activeRow", 0)
                val activeCol = root.optInt("activeCol", 0)
                val selStartRow = root.optInt("selStartRow", activeRow)
                val selStartCol = root.optInt("selStartCol", activeCol)
                val selEndRow = root.optInt("selEndRow", activeRow)
                val selEndCol = root.optInt("selEndCol", activeCol)
                val activeAddress = root.optString("activeAddress", MasuSheetConfig.formatCellAddress(activeRow, activeCol))
                val activeEditText = root.optString("activeEditText", "")
                val activeDisplayText = root.optString("activeDisplayText", activeEditText)
                val activeSpillFormula = root.optString("activeSpillFormula", "")
                val freezeRows = root.optInt("freezeRows", 0).coerceAtLeast(0)
                val freezeCols = root.optInt("freezeCols", 0).coerceAtLeast(0)

                val cwArr = root.optJSONArray("colWidths")
                val colWidths = if (cwArr != null && cwArr.length() > 0) {
                    FloatArray(cwArr.length()) { i -> cwArr.optDouble(i, 64.0).toFloat() }
                } else {
                    FloatArray(26) { 72f }
                }

                val rhArr = root.optJSONArray("rowHeights")
                val rowHeights = if (rhArr != null && rhArr.length() > 0) {
                    FloatArray(rhArr.length()) { i -> rhArr.optDouble(i, 20.0).toFloat() }
                } else {
                    FloatArray(80) { 22f }
                }

                val hrArr = root.optJSONArray("hiddenRows")
                val hiddenRows = if (hrArr != null) {
                    IntArray(hrArr.length()) { i -> hrArr.optInt(i, -1) }.filter { it >= 0 }.toIntArray()
                } else {
                    rowHeights.indices.filter { rowHeights[it] < 1f }.toIntArray()
                }

                val hcArr = root.optJSONArray("hiddenCols")
                val hiddenCols = if (hcArr != null) {
                    IntArray(hcArr.length()) { i -> hcArr.optInt(i, -1) }.filter { it >= 0 }.toIntArray()
                } else {
                    colWidths.indices.filter { colWidths[it] < 1f }.toIntArray()
                }

                val mergesArr = root.optJSONArray("merges")
                val merges = mutableListOf<NativeMergeRange>()
                if (mergesArr != null) {
                    for (i in 0 until mergesArr.length()) {
                        val m = mergesArr.optJSONArray(i) ?: continue
                        if (m.length() >= 4) {
                            merges.add(
                                NativeMergeRange(
                                    startRow = m.optInt(0, 0),
                                    startCol = m.optInt(1, 0),
                                    endRow = m.optInt(2, 0),
                                    endCol = m.optInt(3, 0)
                                )
                            )
                        }
                    }
                }

                val spillArr = root.optJSONArray("spillRanges")
                val spillRanges = mutableListOf<NativeMergeRange>()
                if (spillArr != null) {
                    for (i in 0 until spillArr.length()) {
                        val sp = spillArr.optJSONArray(i) ?: continue
                        if (sp.length() >= 4) {
                            spillRanges.add(
                                NativeMergeRange(
                                    startRow = sp.optInt(0, 0),
                                    startCol = sp.optInt(1, 0),
                                    endRow = sp.optInt(2, 0),
                                    endCol = sp.optInt(3, 0)
                                )
                            )
                        }
                    }
                }

                val sparkArr = root.optJSONArray("sparklines")
                val sparklines = mutableListOf<NativeSparklineModel>()
                if (sparkArr != null) {
                    for (i in 0 until sparkArr.length()) {
                        val sObj = sparkArr.optJSONObject(i) ?: continue
                        val valsArr = sObj.optJSONArray("values")
                        val vals = if (valsArr != null) {
                            List(valsArr.length()) { j ->
                                val d = valsArr.optDouble(j, 0.0)
                                if (d.isFinite() && kotlin.math.abs(d) <= Float.MAX_VALUE.toDouble()) d.toFloat() else 0f
                            }
                        } else {
                            emptyList()
                        }
                        val rawColor = sObj.opt("color")
                        val normalizedColor = when (rawColor) {
                            is Number -> String.format("#%06X", rawColor.toLong() and 0xFFFFFFL)
                            is String -> {
                                val trimmed = rawColor.trim()
                                val numericColor = trimmed.toLongOrNull()
                                if (numericColor != null) {
                                    String.format("#%06X", numericColor and 0xFFFFFFL)
                                } else {
                                    trimmed.ifBlank { "#1F9D55" }
                                }
                            }
                            else -> "#1F9D55"
                        }
                        sparklines.add(
                            NativeSparklineModel(
                                row = sObj.optInt("r", 0),
                                col = sObj.optInt("c", 0),
                                source = sObj.optString("source", ""),
                                kind = sObj.optString("kind", "line"),
                                color = normalizedColor,
                                markers = sObj.optBoolean("markers", true),
                                values = vals
                            )
                        )
                    }
                }

                val valArr = root.optJSONArray("validations")
                val validations = mutableListOf<NativeValidationModel>()
                if (valArr != null) {
                    for (i in 0 until valArr.length()) {
                        val vObj = valArr.optJSONObject(i) ?: continue
                        val rng = vObj.optJSONArray("range")
                        val r0: Int
                        val c0: Int
                        val r1: Int
                        val c1: Int
                        if (rng != null && rng.length() >= 2) {
                            r0 = rng.optInt(0, 0)
                            c0 = rng.optInt(1, 0)
                            r1 = rng.optInt(2, r0)
                            c1 = rng.optInt(3, c0)
                        } else {
                            val rngStr = vObj.optString("range", "").substringAfterLast('!').trim()
                            val parts = rngStr.split(':', limit = 2)
                            val p0 = MasuSheetConfig.parseCellAddressStrict(parts[0]) ?: (0 to 0)
                            val p1 = if (parts.size >= 2) {
                                MasuSheetConfig.parseCellAddressStrict(parts[1]) ?: p0
                            } else {
                                p0
                            }
                            r0 = minOf(p0.first, p1.first)
                            c0 = minOf(p0.second, p1.second)
                            r1 = maxOf(p0.first, p1.first)
                            c1 = maxOf(p0.second, p1.second)
                        }
                        val itemsJson = vObj.optJSONArray("items")
                        val items = if (itemsJson != null) {
                            List(itemsJson.length()) { j -> itemsJson.optString(j, "") }.filter { it.isNotEmpty() }
                        } else {
                            emptyList()
                        }
                        validations.add(
                            NativeValidationModel(
                                startRow = r0,
                                startCol = c0,
                                endRow = r1,
                                endCol = c1,
                                kind = vObj.optString("kind", "list"),
                                f1 = vObj.optString("f1", ""),
                                f2 = vObj.optString("f2", ""),
                                inCellDropdown = vObj.optBoolean("inCellDropdown", true),
                                inputTitle = vObj.optString("inputTitle", ""),
                                inputMessage = vObj.optString("inputMessage", ""),
                                showInput = vObj.optBoolean("showInput", true),
                                errorTitle = vObj.optString("errorTitle", ""),
                                errorMessage = vObj.optString("errorMessage", ""),
                                showError = vObj.optBoolean("showError", true),
                                items = items
                            )
                        )
                    }
                }

                val comArr = root.optJSONArray("comments")
                val comments = mutableListOf<NativeCommentModel>()
                if (comArr != null) {
                    for (i in 0 until comArr.length()) {
                        val cm = comArr.optJSONObject(i) ?: continue
                        comments.add(
                            NativeCommentModel(
                                cell = cm.optString("cell", ""),
                                author = cm.optString("author", "User"),
                                text = cm.optString("text", ""),
                                resolved = cm.optBoolean("resolved", false)
                            )
                        )
                    }
                }

                val hlArr = root.optJSONArray("hyperlinks")
                val hyperlinks = mutableListOf<NativeHyperlinkModel>()
                if (hlArr != null) {
                    for (i in 0 until hlArr.length()) {
                        val hl = hlArr.optJSONObject(i) ?: continue
                        hyperlinks.add(
                            NativeHyperlinkModel(
                                cell = hl.optString("cell", ""),
                                target = hl.optString("target", ""),
                                tooltip = hl.optString("tooltip", "")
                            )
                        )
                    }
                }

                val dnArr = root.optJSONArray("definedNames")
                val definedNames = mutableListOf<NativeDefinedNameModel>()
                if (dnArr != null) {
                    for (i in 0 until dnArr.length()) {
                        val dn = dnArr.optJSONObject(i) ?: continue
                        definedNames.add(
                            NativeDefinedNameModel(
                                name = dn.optString("name", ""),
                                refersTo = dn.optString("refersTo", "")
                            )
                        )
                    }
                }

                val cellsArr = root.optJSONArray("cells")
                val cellMap = HashMap<Long, NativeCellModel>((cellsArr?.length() ?: 0) + 8)
                if (cellsArr != null) {
                    for (i in 0 until cellsArr.length()) {
                        val cObj = cellsArr.optJSONObject(i) ?: continue
                        val r = cObj.optInt("r", 0)
                        val c = cObj.optInt("c", 0)
                        val disp = cObj.optString("t", "")
                        val edit = if (cObj.has("e")) cObj.optString("e", disp) else disp
                        val cell = NativeCellModel(
                            row = r,
                            col = c,
                            displayText = disp,
                            editText = edit,
                            bgColor = cObj.optLong("bg", 0L).toInt(),
                            fgColor = cObj.optLong("fg", 0L).toInt(),
                            fontSize = cObj.optDouble("fs", 11.0).toFloat(),
                            flags = cObj.optInt("fl", 0),
                            borderTop = cObj.optLong("bt", 0L).toInt(),
                            borderRight = cObj.optLong("br", 0L).toInt(),
                            borderBottom = cObj.optLong("bb", 0L).toInt(),
                            borderLeft = cObj.optLong("bl", 0L).toInt()
                        )
                        cellMap[packCellKey(r, c)] = cell
                    }
                }

                val tcArr = root.optJSONArray("themeColors")
                val themeColors = if (tcArr != null && tcArr.length() >= 10) {
                    List(10) { i -> tcArr.optLong(i, SpreadsheetColorPalette.DEFAULT_THEME_BASE_COLORS[i].toLong()).toInt() }
                } else {
                    SpreadsheetColorPalette.DEFAULT_THEME_BASE_COLORS
                }

                NativeViewportSnapshot(
                    ok = ok,
                    error = err,
                    resultJson = resJson,
                    sessionId = sessionId,
                    uid = uid,
                    revision = rev,
                    undoDepth = undoDepth,
                    redoDepth = redoDepth,
                    title = title,
                    isDirty = isDirty,
                    sheetIndex = sheetIndex,
                    totalSheetCount = totalSheetCount,
                    sheetName = sheetName,
                    sheetNames = sheetNames,
                    visibleSheetIndices = visibleSheetIndices,
                    hiddenSheetNames = hiddenSheetNames,
                    hiddenSheetIndices = hiddenSheetIndices,
                    usedEndRow = usedEndRow,
                    usedEndCol = usedEndCol,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    activeRow = activeRow,
                    activeCol = activeCol,
                    selStartRow = selStartRow,
                    selStartCol = selStartCol,
                    selEndRow = selEndRow,
                    selEndCol = selEndCol,
                    activeAddress = activeAddress,
                    activeEditText = activeEditText,
                    activeDisplayText = activeDisplayText,
                    activeSpillFormula = activeSpillFormula,
                    freezeRows = freezeRows,
                    freezeCols = freezeCols,
                    colWidths = colWidths,
                    rowHeights = rowHeights,
                    hiddenRows = hiddenRows,
                    hiddenCols = hiddenCols,
                    merges = merges,
                    spillRanges = spillRanges,
                    sparklines = sparklines,
                    validations = validations,
                    comments = comments,
                    hyperlinks = hyperlinks,
                    definedNames = definedNames,
                    cells = cellMap,
                    themeColors = themeColors
                )
            } catch (e: Exception) {
                NativeViewportSnapshot(ok = false, error = e.message ?: "JSON parse error")
            }
        }
    }
}
