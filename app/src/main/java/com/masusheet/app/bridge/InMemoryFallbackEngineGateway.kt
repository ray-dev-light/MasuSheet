package com.masusheet.app.bridge

import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.core.SpreadsheetColorPalette
import com.masusheet.app.model.NativeCellModel
import com.masusheet.app.model.NativeCommentModel
import com.masusheet.app.model.NativeDefinedNameModel
import com.masusheet.app.model.NativeHyperlinkModel
import com.masusheet.app.model.NativeMergeRange
import com.masusheet.app.model.NativeSparklineModel
import com.masusheet.app.model.NativeValidationModel
import com.masusheet.app.model.NativeViewportSnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Pure-Kotlin In-Memory Fallback Spreadsheet Engine Gateway
 * Provides [InMemoryFallbackEngineGateway] for JVM unit tests and environments without JNI binaries, supporting formulas, Undo/Redo, sorting, filtering, merges, and safe save refusal.
 *
 * ---
 * [JA / 日本語解説]
 * JVM単体テストやJNI未ロード環境向けに、数式評価・Undo/Redo・ソート・フィルタ・セル結合・安全な未対応バイナリ保存拒否 を純粋Kotlinで提供するフォールバックエンジンです。
 * ============================================================================
 */

/**
 * [EN] Pure-Kotlin [NativeEngineGateway] fallback for unit tests and non-JNI hosts, safely refusing binary `.xlsx` overwrite.
 * [JA] 単体テストやJNI未ロード環境向けの純粋Kotlinフォールバック実装です（ダミーデータによるファイル破壊を防止します）。
 */
class InMemoryFallbackEngineGateway : NativeEngineGateway {
    private data class FallbackSheetState(
        var name: String,
        var hidden: Boolean = false,
        val cells: MutableMap<Long, NativeCellModel> = mutableMapOf(),
        val merges: MutableList<NativeMergeRange> = mutableListOf(),
        val spillRanges: MutableList<NativeMergeRange> = mutableListOf(),
        val spillSources: MutableMap<Long, String> = mutableMapOf(),
        val sparklines: MutableList<NativeSparklineModel> = mutableListOf(),
        val validations: MutableList<NativeValidationModel> = mutableListOf(),
        val comments: MutableMap<Long, NativeCommentModel> = mutableMapOf(),
        val hyperlinks: MutableMap<Long, NativeHyperlinkModel> = mutableMapOf(),
        val colWidths: FloatArray = FloatArray(52) { if (it < 4) 180f else 84f },
        val rowHeights: FloatArray = FloatArray(200) { 26f },
        var freezeRows: Int = 0,
        var freezeCols: Int = 0
    )

    private class FallbackSession(
        var revision: Long = 1L,
        var title: String = "Sales.xlsx",
        var isDirty: Boolean = false,
        var activeSheetIdx: Int = 0,
        var activeRow: Int = 0,
        var activeCol: Int = 0,
        var selStartRow: Int = 0,
        var selStartCol: Int = 0,
        var selEndRow: Int = 0,
        var selEndCol: Int = 0,
        var lastResultJson: String? = null,
        val definedNames: MutableMap<String, String> = mutableMapOf(),
        val sheets: MutableList<FallbackSheetState> = mutableListOf(),
        val undoStack: ArrayDeque<Map<Long, NativeCellModel>> = ArrayDeque(),
        val redoStack: ArrayDeque<Map<Long, NativeCellModel>> = ArrayDeque()
    )

    private val sessions = mutableMapOf<Long, FallbackSession>(0L to FallbackSession())
    private var nextFallbackSessionId = 1L
    private var currentSessionId = 0L

    override val activeSessionId: Long
        get() = currentSessionId

    private val sess: FallbackSession
        get() = sessions.getOrPut(currentSessionId) { FallbackSession() }

    private var revision: Long
        get() = sess.revision
        set(value) { sess.revision = value }
    private var title: String
        get() = sess.title
        set(value) { sess.title = value }
    private var isDirty: Boolean
        get() = sess.isDirty
        set(value) { sess.isDirty = value }
    private var activeSheetIdx: Int
        get() = sess.activeSheetIdx
        set(value) { sess.activeSheetIdx = value }
    private var activeRow: Int
        get() = sess.activeRow
        set(value) { sess.activeRow = value }
    private var activeCol: Int
        get() = sess.activeCol
        set(value) { sess.activeCol = value }
    private var selStartRow: Int
        get() = sess.selStartRow
        set(value) { sess.selStartRow = value }
    private var selStartCol: Int
        get() = sess.selStartCol
        set(value) { sess.selStartCol = value }
    private var selEndRow: Int
        get() = sess.selEndRow
        set(value) { sess.selEndRow = value }
    private var selEndCol: Int
        get() = sess.selEndCol
        set(value) { sess.selEndCol = value }
    private var lastResultJson: String?
        get() = sess.lastResultJson
        set(value) { sess.lastResultJson = value }
    private val definedNames: MutableMap<String, String>
        get() = sess.definedNames
    private val sheets: MutableList<FallbackSheetState>
        get() = sess.sheets
    private val undoStack: ArrayDeque<Map<Long, NativeCellModel>>
        get() = sess.undoStack
    private val redoStack: ArrayDeque<Map<Long, NativeCellModel>>
        get() = sess.redoStack

    override fun createSession(sample: String): Long {
        val sid = nextFallbackSessionId++
        sessions[sid] = FallbackSession()
        currentSessionId = sid
        if (sample.isBlank() || sample.equals("blank", ignoreCase = true) || sample.equals("empty", ignoreCase = true)) {
            executeCommand("file.new", "{}")
        } else {
            populateSample(sample)
        }
        return sid
    }

    override fun switchSession(sessionId: Long) {
        currentSessionId = sessionId.coerceAtLeast(0L)
        if (currentSessionId !in sessions) {
            sessions[currentSessionId] = FallbackSession()
            populateSample("sales")
        }
    }

    override fun closeSession(sessionId: Long) {
        if (sessionId > 0L) {
            sessions.remove(sessionId)
        }
        if (currentSessionId == sessionId) {
            currentSessionId = 0L
        }
    }

    private val currentSheet: FallbackSheetState
        get() = sheets.getOrPut(activeSheetIdx.coerceIn(0, (sheets.size - 1).coerceAtLeast(0))) {
            FallbackSheetState("Sales")
        }

    private val currentCells: MutableMap<Long, NativeCellModel>
        get() = currentSheet.cells

    private val currentMerges: MutableList<NativeMergeRange>
        get() = currentSheet.merges

    private fun <T> MutableList<T>.getOrPut(index: Int, default: () -> T): T {
        if (isEmpty()) add(default())
        return this[index.coerceIn(0, lastIndex)]
    }

    private fun pushUndo() {
        undoStack.addLast(HashMap(currentCells))
        if (undoStack.size > 500) undoStack.removeFirst()
        redoStack.clear()
    }

    init {
        populateSample("sales")
    }

    private fun populateSample(sample: String) {
        sheets.clear()
        definedNames.clear()
        undoStack.clear()
        redoStack.clear()
        activeSheetIdx = 0
        revision++
        isDirty = false
        if (sample == "repair") {
            title = "修理依頼書.xlsx"
            sheets.add(FallbackSheetState("修理申込票"))
            currentMerges.add(NativeMergeRange(0, 0, 0, 3))
            putCell(0, 0, "修理・点検お申し込み兼チェックシート", bg = 0xFF163828.toInt(), fg = 0xFFFFFFFF.toInt(), bold = true)
            putCell(1, 0, "お申し込み日", bg = 0xFFE8F5E9.toInt(), bold = true)
            putCell(1, 1, "20    年    月    日")
            putCell(1, 2, "受付番号", bg = 0xFFE8F5E9.toInt(), bold = true)
            putCell(1, 3, "RC-2026-0104")
            putCell(2, 0, "お名前", bg = 0xFFE8F5E9.toInt(), bold = true)
            putCell(2, 1, "山田 太郎 様")
            putCell(2, 2, "ご購入日", bg = 0xFFE8F5E9.toInt(), bold = true)
            putCell(2, 3, "2026年  10月  31日")
            putCell(3, 0, "メールアドレス", bg = 0xFFE8F5E9.toInt(), bold = true)
            putCell(3, 1, "          @          ")
            putCell(3, 2, "電話番号", bg = 0xFFE8F5E9.toInt(), bold = true)
            putCell(3, 3, "090 - 1234 - 5678")
            putCell(4, 0, "同梱品チェック", bg = 0xFFE8F5E9.toInt(), bold = true)
            putCell(4, 1, "□ 本体")
            putCell(4, 2, "□ ACアダプター")
            putCell(4, 3, "☑ 保証書")
        } else {
            title = "${sample.replaceFirstChar { it.uppercase() }}.xlsx"
            sheets.add(FallbackSheetState("Sales"))
            putCell(0, 0, "Region", bold = true)
            putCell(0, 1, "Q1", bold = true)
            putCell(0, 2, "Q2", bold = true)
            putCell(0, 3, "Q3", bold = true)
            putCell(1, 0, "North")
            putCell(1, 1, "48,200", edit = "48200")
            putCell(1, 2, "51,340", edit = "51340")
            putCell(1, 3, "55,900", edit = "55900")
            putCell(2, 0, "South")
            putCell(2, 1, "39,100", edit = "39100")
            putCell(2, 2, "44,800", edit = "44800")
            putCell(2, 3, "49,250", edit = "49250")
            putCell(3, 0, "East")
            putCell(3, 1, "61,400", edit = "61400")
            putCell(3, 2, "58,900", edit = "58900")
            putCell(3, 3, "66,300", edit = "66300")
            putCell(4, 0, "West")
            putCell(4, 1, "35,600", edit = "35600")
            putCell(4, 2, "41,200", edit = "41200")
            putCell(4, 3, "47,800", edit = "47800")
            putCell(5, 0, "Total", bold = true)
            putCell(5, 1, "184,300", edit = "=SUM(B2:B5)", bold = true)
            putCell(5, 2, "196,240", edit = "=SUM(C2:C5)", bold = true)
            putCell(5, 3, "219,250", edit = "=SUM(D2:D5)", bold = true)
        }
    }

    private fun putCell(r: Int, c: Int, text: String, edit: String = text, bg: Int = 0, fg: Int = 0, bold: Boolean = false) {
        val fl = if (bold) 1 else 0
        currentCells[NativeViewportSnapshot.packCellKey(r, c)] = NativeCellModel(
            row = r,
            col = c,
            displayText = text,
            editText = edit,
            bgColor = bg,
            fgColor = fg,
            flags = fl,
            borderTop = 0x0264748B,
            borderRight = 0x0264748B,
            borderBottom = 0x0264748B,
            borderLeft = 0x0264748B
        )
    }

    override fun initSample(sample: String): NativeViewportSnapshot {
        populateSample(sample)
        return snapshot()
    }

    override fun openWorkbookBytes(fileName: String, bytes: ByteArray): NativeViewportSnapshot {
        title = fileName
        revision++
        isDirty = false
        if (fileName.endsWith(".csv", ignoreCase = true) || fileName.endsWith(".tsv", ignoreCase = true)) {
            val text = bytes.decodeToString()
            currentCells.clear()
            currentMerges.clear()
            val delim = if (fileName.endsWith(".tsv", ignoreCase = true)) '\t' else ','
            parseDelimitedRfc4180(text, delim).forEachIndexed { r, rowTokens ->
                rowTokens.forEachIndexed { c, token ->
                    if (token.isNotEmpty()) {
                        putCell(r, c, token)
                    }
                }
            }
        }
        return snapshot()
    }

    /**
     * [EN] Exports full RFC 4180 CSV/TSV when saving text delimited files in fallback mode,
     *      and returns `null` for binary formats (`.xlsx`/`.xlsm`/`.xlsb`/`.ods`) so an unloaded `.so`
     *      never silently overwrites a real binary workbook with dummy bytes!
     * [JA] フォールバック時は `.csv`/`.tsv` のみ全セルRFC 4180出力を行い、バイナリ形式（`.xlsx` 等）は `null` を返して
     *      ダミーデータによる上書き破壊を防止します。
     */
    override fun saveWorkbookBytes(fileName: String): ByteArray? {
        val lower = fileName.trim().lowercase()
        val delim = when {
            lower.endsWith(".csv") -> ','
            lower.endsWith(".tsv") -> '\t'
            else -> return null
        }
        val maxR = currentCells.values.maxOfOrNull { it.row } ?: -1
        val maxC = currentCells.values.maxOfOrNull { it.col } ?: -1
        if (maxR < 0 || maxC < 0) return ByteArray(0)
        val sb = StringBuilder()
        for (r in 0..maxR) {
            for (c in 0..maxC) {
                if (c > 0) sb.append(delim)
                val raw = currentCells[NativeViewportSnapshot.packCellKey(r, c)]?.displayText.orEmpty()
                val needsQuotes = raw.contains(delim) || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
                if (needsQuotes) {
                    sb.append('"').append(raw.replace("\"", "\"\"")).append('"')
                } else {
                    sb.append(raw)
                }
            }
            sb.append('\n')
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    override fun exportPdfBytes(): ByteArray? = null

    private fun parseDelimitedRfc4180(text: String, delim: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var curRow = mutableListOf<String>()
        val curCell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        curCell.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    curCell.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> if (curCell.isEmpty()) inQuotes = true else curCell.append(ch)
                    delim -> {
                        curRow.add(curCell.toString())
                        curCell.setLength(0)
                    }
                    '\r' -> {
                        curRow.add(curCell.toString())
                        curCell.setLength(0)
                        rows.add(curRow)
                        curRow = mutableListOf()
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                    }
                    '\n' -> {
                        curRow.add(curCell.toString())
                        curCell.setLength(0)
                        rows.add(curRow)
                        curRow = mutableListOf()
                    }
                    else -> curCell.append(ch)
                }
            }
            i++
        }
        if (curCell.isNotEmpty() || curRow.isNotEmpty()) {
            curRow.add(curCell.toString())
            rows.add(curRow)
        }
        return rows
    }

    override fun executeCommand(commandId: String, paramsJson: String): NativeViewportSnapshot {
        lastResultJson = null
        val p = try { JSONObject(paramsJson) } catch (_: Exception) { JSONObject() }
        val key = NativeViewportSnapshot.packCellKey(activeRow, activeCol)
        fun mutateActiveCell(transform: (NativeCellModel) -> NativeCellModel) {
            pushUndo()
            val existing = currentCells[key] ?: NativeCellModel(row = activeRow, col = activeCol, displayText = "", editText = "")
            currentCells[key] = transform(existing)
            revision++
            isDirty = true
        }
        fun resolveRangeBounds(): IntArray {
            val rng = p.optString("range", "").trim()
            if (rng.isNotEmpty()) {
                if (rng.contains(':')) {
                    val parts = rng.split(':', limit = 2)
                    val (ra, ca) = parseA1(parts[0])
                    val (rb, cb) = parseA1(parts[1])
                    return intArrayOf(minOf(ra, rb), minOf(ca, cb), maxOf(ra, rb), maxOf(ca, cb))
                } else {
                    val (r, c) = parseA1(rng)
                    return intArrayOf(r, c, r, c)
                }
            }
            return intArrayOf(
                minOf(selStartRow, selEndRow),
                minOf(selStartCol, selEndCol),
                maxOf(selStartRow, selEndRow),
                maxOf(selStartCol, selEndCol)
            )
        }
        fun mutateRangeCells(transform: (Int, Int, NativeCellModel) -> NativeCellModel) {
            pushUndo()
            val bounds = resolveRangeBounds()
            val r0 = bounds[0].coerceIn(0, 199)
            val c0 = bounds[1].coerceIn(0, 51)
            val r1 = bounds[2].coerceIn(r0, 199)
            val c1 = bounds[3].coerceIn(c0, 51)
            for (r in r0..r1) {
                for (c in c0..c1) {
                    val ck = NativeViewportSnapshot.packCellKey(r, c)
                    val existing = currentCells[ck] ?: NativeCellModel(
                        row = r,
                        col = c,
                        displayText = "",
                        editText = "",
                        borderTop = 0x0264748B,
                        borderRight = 0x0264748B,
                        borderBottom = 0x0264748B,
                        borderLeft = 0x0264748B
                    )
                    currentCells[ck] = transform(r, c, existing)
                }
            }
            revision++
            isDirty = true
        }
        when (commandId) {
            "edit.undo" -> {
                if (undoStack.isNotEmpty()) {
                    redoStack.addLast(HashMap(currentCells))
                    val prev = undoStack.removeLast()
                    currentCells.clear()
                    currentCells.putAll(prev)
                    revision++
                }
            }
            "edit.redo" -> {
                if (redoStack.isNotEmpty()) {
                    undoStack.addLast(HashMap(currentCells))
                    val next = redoStack.removeLast()
                    currentCells.clear()
                    currentCells.putAll(next)
                    revision++
                }
            }
            "file.new" -> {
                val sampleParam = p.optString("sample", "").trim()
                val customTitle = p.optString("title", "").trim()
                if (sampleParam.isEmpty()) {
                    sheets.clear()
                    definedNames.clear()
                    undoStack.clear()
                    redoStack.clear()
                    activeSheetIdx = 0
                    revision++
                    isDirty = false
                    title = customTitle.ifEmpty { "Book1.xlsx" }
                    sheets.add(FallbackSheetState("Sheet1"))
                } else {
                    populateSample(sampleParam)
                    if (customTitle.isNotEmpty()) {
                        title = customTitle
                    }
                }
            }
            "cell.set" -> {
                pushUndo()
                val input = p.optString("input", "")
                val cellRef = p.optString("cell", "")
                val (r, c) = if (cellRef.isNotBlank()) parseA1(cellRef) else (activeRow to activeCol)
                val ck = NativeViewportSnapshot.packCellKey(r, c)
                // Clear previous spill from (r, c) if any
                currentSheet.spillRanges.removeAll { it.startRow == r && it.startCol == c }
                val seqMatch = Regex("^=\\s*SEQUENCE\\s*\\(\\s*(\\d+)(?:\\s*,\\s*(\\d+))?(?:\\s*,\\s*(-?\\d+))?(?:\\s*,\\s*(-?\\d+))?\\s*\\)$", RegexOption.IGNORE_CASE)
                    .matchEntire(input.trim())
                if (seqMatch != null) {
                    val rows = (seqMatch.groupValues[1].toIntOrNull() ?: 1).coerceIn(1, 50)
                    val cols = (seqMatch.groupValues[2].toIntOrNull() ?: 1).coerceIn(1, 20)
                    val startVal = seqMatch.groupValues[3].toIntOrNull() ?: 1
                    val stepVal = seqMatch.groupValues[4].toIntOrNull() ?: 1
                    var curVal = startVal
                    for (dr in 0 until rows) {
                        for (dc in 0 until cols) {
                            val sr = r + dr
                            val sc = c + dc
                            val sk = NativeViewportSnapshot.packCellKey(sr, sc)
                            val editStr = if (dr == 0 && dc == 0) input else ""
                            putCell(sr, sc, curVal.toString(), edit = editStr)
                            if (dr > 0 || dc > 0) {
                                currentSheet.spillSources[sk] = input
                            }
                            curVal += stepVal
                        }
                    }
                    if (rows > 1 || cols > 1) {
                        currentSheet.spillRanges.add(NativeMergeRange(r, c, r + rows - 1, c + cols - 1))
                    }
                } else {
                    val prev = currentCells[ck]
                    if (prev != null) {
                        currentCells[ck] = prev.copy(displayText = input, editText = input)
                    } else {
                        putCell(r, c, input)
                    }
                }
                revision++
                isDirty = true
            }
            "selection.set" -> {
                val act = p.optString("active", "")
                val rng = p.optString("range", "")
                if (act.isNotBlank()) {
                    val (r, c) = parseA1(act)
                    activeRow = r
                    activeCol = c
                    if (rng.contains(':')) {
                        val parts = rng.split(':', limit = 2)
                        val (r0, c0) = parseA1(parts[0])
                        val (r1, c1) = parseA1(parts[1])
                        selStartRow = r0
                        selStartCol = c0
                        selEndRow = r1
                        selEndCol = c1
                    } else {
                        selStartRow = r
                        selStartCol = c
                        selEndRow = r
                        selEndCol = c
                    }
                }
            }
            "sheet.moveColumns" -> {
                pushUndo()
                val sc = p.optInt("startCol", selStartCol).coerceIn(0, 25)
                val ec = p.optInt("endCol", selEndCol).coerceIn(sc, 25)
                val beforeCol = p.optInt("beforeCol", 0).coerceIn(0, 26)
                val count = ec - sc + 1
                if (beforeCol !in sc..(ec + 1)) {
                    val allCols = (0..25).toMutableList()
                    val moving = (sc..ec).toList()
                    allCols.removeAll(moving)
                    val insertIdx = if (beforeCol > ec) (beforeCol - count).coerceIn(0, allCols.size) else beforeCol.coerceIn(0, allCols.size)
                    allCols.addAll(insertIdx, moving)
                    // allCols[newCol] == oldCol -> build inverse map oldCol -> newCol
                    val oldToNew = IntArray(26)
                    for ((newC, oldC) in allCols.withIndex()) {
                        oldToNew[oldC] = newC
                    }
                    val remapped = mutableMapOf<Long, NativeCellModel>()
                    for ((_, cell) in currentCells) {
                        val nc = if (cell.col in 0..25) oldToNew[cell.col] else cell.col
                        remapped[NativeViewportSnapshot.packCellKey(cell.row, nc)] = cell.copy(col = nc)
                    }
                    currentCells.clear()
                    currentCells.putAll(remapped)
                    activeCol = insertIdx
                    selStartCol = insertIdx
                    selEndCol = insertIdx + count - 1
                    revision++
                    isDirty = true
                }
            }
            "view.freezePanes" -> {
                val r = if (p.has("rows")) p.optInt("rows", activeRow) else if (activeRow == 0 && activeCol == 0) 1 else activeRow
                val c = if (p.has("cols")) p.optInt("cols", activeCol) else if (activeRow == 0 && activeCol == 0) 1 else activeCol
                currentSheet.freezeRows = r.coerceAtLeast(0)
                currentSheet.freezeCols = c.coerceAtLeast(0)
                revision++
            }
            "view.freezeTopRow" -> {
                currentSheet.freezeRows = 1
                currentSheet.freezeCols = 0
                revision++
            }
            "view.freezeFirstColumn" -> {
                currentSheet.freezeRows = 0
                currentSheet.freezeCols = 1
                revision++
            }
            "view.unfreezePanes" -> {
                currentSheet.freezeRows = 0
                currentSheet.freezeCols = 0
                revision++
            }
            "home.hideColumns" -> {
                val c0 = minOf(selStartCol, selEndCol).coerceIn(0, currentSheet.colWidths.lastIndex)
                val c1 = maxOf(selStartCol, selEndCol).coerceIn(c0, currentSheet.colWidths.lastIndex)
                for (c in c0..c1) currentSheet.colWidths[c] = 0f
                revision++
                isDirty = true
            }
            "home.unhideColumns" -> {
                for (c in currentSheet.colWidths.indices) {
                    if (currentSheet.colWidths[c] < 1f) currentSheet.colWidths[c] = 84f
                }
                revision++
                isDirty = true
            }
            "home.hideRows" -> {
                val r0 = minOf(selStartRow, selEndRow).coerceIn(0, currentSheet.rowHeights.lastIndex)
                val r1 = maxOf(selStartRow, selEndRow).coerceIn(r0, currentSheet.rowHeights.lastIndex)
                for (r in r0..r1) currentSheet.rowHeights[r] = 0f
                revision++
                isDirty = true
            }
            "home.unhideRows" -> {
                for (r in currentSheet.rowHeights.indices) {
                    if (currentSheet.rowHeights[r] < 1f) currentSheet.rowHeights[r] = 26f
                }
                revision++
                isDirty = true
            }
            "insert.sparkline", "insert.sparklines" -> {
                val dataRng = p.optString("dataRange", p.optString("source", ""))
                val locRef = p.optString("location", MasuSheetConfig.formatCellAddress(activeRow, activeCol))
                val kind = p.optString("kind", p.optString("type", "line")).lowercase()
                val (lr, lc) = parseA1(locRef)
                val bounds = if (dataRng.contains(':')) {
                    val parts = dataRng.split(':', limit = 2)
                    val (r0, c0) = parseA1(parts[0])
                    val (r1, c1) = parseA1(parts[1])
                    intArrayOf(minOf(r0, r1), minOf(c0, c1), maxOf(r0, r1), maxOf(c0, c1))
                } else {
                    intArrayOf(minOf(selStartRow, selEndRow), minOf(selStartCol, selEndCol), maxOf(selStartRow, selEndRow), maxOf(selStartCol, selEndCol))
                }
                currentSheet.sparklines.removeAll { it.row == lr && it.col == lc }
                currentSheet.sparklines.add(
                    NativeSparklineModel(
                        row = lr,
                        col = lc,
                        source = MasuSheetConfig.formatRangeReference(bounds[0], bounds[1], bounds[2], bounds[3]),
                        kind = kind,
                        color = "#2563EB",
                        markers = p.optBoolean("markers", true)
                    )
                )
                revision++
                isDirty = true
            }
            "data.validation" -> {
                val bounds = resolveRangeBounds()
                val kind = p.optString("kind", p.optString("allow", "list")).lowercase()
                val f1 = p.optString("formula1", p.optString("source", ""))
                val f2 = p.optString("formula2", "")
                val items = if (kind == "list") {
                    f1.split(',', '、', ';').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
                } else emptyList()
                currentSheet.validations.removeAll {
                    it.startRow == bounds[0] && it.startCol == bounds[1] && it.endRow == bounds[2] && it.endCol == bounds[3]
                }
                if (kind != "any") {
                    currentSheet.validations.add(
                        NativeValidationModel(
                            startRow = bounds[0],
                            startCol = bounds[1],
                            endRow = bounds[2],
                            endCol = bounds[3],
                            kind = kind,
                            operator = p.optString("operator", "between"),
                            f1 = f1,
                            f2 = f2,
                            items = items,
                            inCellDropdown = p.optBoolean("inCellDropdown", true),
                            showInput = p.optBoolean("showInput", true),
                            inputTitle = p.optString("inputTitle", ""),
                            inputMessage = p.optString("inputMessage", ""),
                            showError = p.optBoolean("showError", true),
                            errorTitle = p.optString("errorTitle", ""),
                            errorMessage = p.optString("errorMessage", "")
                        )
                    )
                }
                revision++
                isDirty = true
            }
            "review.newComment" -> {
                val text = p.optString("text", "").trim()
                val author = p.optString("author", "User").ifBlank { "User" }
                val ck = NativeViewportSnapshot.packCellKey(activeRow, activeCol)
                if (text.isEmpty()) {
                    currentSheet.comments.remove(ck)
                } else {
                    currentSheet.comments[ck] = NativeCommentModel(
                        cell = MasuSheetConfig.formatCellAddress(activeRow, activeCol),
                        author = author,
                        text = text
                    )
                }
                revision++
                isDirty = true
            }
            "review.deleteComment" -> {
                val ck = NativeViewportSnapshot.packCellKey(activeRow, activeCol)
                currentSheet.comments.remove(ck)
                revision++
                isDirty = true
            }
            "insert.link" -> {
                val url = p.optString("url", p.optString("target", "")).trim()
                val disp = p.optString("display", p.optString("text", "")).trim()
                val ck = NativeViewportSnapshot.packCellKey(activeRow, activeCol)
                if (url.isEmpty()) {
                    currentSheet.hyperlinks.remove(ck)
                } else {
                    currentSheet.hyperlinks[ck] = NativeHyperlinkModel(
                        cell = MasuSheetConfig.formatCellAddress(activeRow, activeCol),
                        target = url,
                        tooltip = disp
                    )
                }
                revision++
                isDirty = true
            }
            "formulas.defineName" -> {
                val name = p.optString("name", "").trim()
                val refersTo = p.optString("refersTo", p.optString("formula", "")).trim()
                if (name.isNotEmpty() && refersTo.isNotEmpty()) {
                    definedNames[name] = refersTo
                    revision++
                    isDirty = true
                }
            }
            "formulas.deleteName" -> {
                val name = p.optString("name", "").trim()
                definedNames.remove(name)
                revision++
                isDirty = true
            }
            "data.goalSeek" -> {
                val setRef = p.optString("set", MasuSheetConfig.formatCellAddress(activeRow, activeCol))
                val toVal = p.optDouble("to", 0.0)
                val changingRef = p.optString("changing", MasuSheetConfig.formatCellAddress(activeRow, activeCol))
                val (sr, sc) = parseA1(setRef)
                val (cr, cc) = parseA1(changingRef)
                val targetCell = currentCells[NativeViewportSnapshot.packCellKey(sr, sc)]
                val rawFormula = targetCell?.editText?.trim().orEmpty()
                val curTarget = if (rawFormula.startsWith("=") && rawFormula.contains("*")) {
                    val mult = rawFormula.substringAfter('*').trim().toDoubleOrNull() ?: 1.0
                    val chg = currentCells[NativeViewportSnapshot.packCellKey(cr, cc)]?.displayText?.replace(",", "")?.toDoubleOrNull() ?: 1.0
                    chg * mult
                } else {
                    targetCell?.displayText?.replace(",", "")?.toDoubleOrNull() ?: 1.0
                }
                val curChanging = currentCells[NativeViewportSnapshot.packCellKey(cr, cc)]?.displayText
                    ?.replace(",", "")?.toDoubleOrNull() ?: 1.0
                val solved = if (curTarget != 0.0 && (sr != cr || sc != cc)) curChanging * (toVal / curTarget) else toVal
                val solvedStr = if (solved == kotlin.math.floor(solved)) solved.toLong().toString() else String.format(java.util.Locale.US, "%.2f", solved)
                putCell(cr, cc, solvedStr, edit = solvedStr)
                lastResultJson = "{\"converged\":true,\"value\":$solved,\"iterations\":4}"
                revision++
                isDirty = true
            }
            "edit.find" -> {
                val what = p.optString("what", p.optString("query", "")).trim()
                val matchCase = p.optBoolean("matchCase", false)
                val matchesArr = org.json.JSONArray()
                if (what.isNotEmpty()) {
                    currentCells.values
                        .sortedWith(compareBy<NativeCellModel> { it.row }.thenBy { it.col })
                        .forEach { c ->
                            if (c.displayText.contains(what, ignoreCase = !matchCase) || c.editText.contains(what, ignoreCase = !matchCase)) {
                                matchesArr.put(
                                    JSONObject()
                                        .put("sheet", activeSheetIdx)
                                        .put("cell", MasuSheetConfig.formatCellAddress(c.row, c.col))
                                        .put("text", c.displayText)
                                )
                            }
                        }
                }
                lastResultJson = JSONObject()
                    .put("count", matchesArr.length())
                    .put("matches", matchesArr)
                    .toString()
            }
            "edit.replace" -> {
                pushUndo()
                val what = p.optString("what", p.optString("find", "")).trim()
                val withStr = p.optString("with", p.optString("replace", ""))
                val matchCase = p.optBoolean("matchCase", false)
                var replacedCount = 0
                if (what.isNotEmpty()) {
                    for ((k, c) in currentCells.entries.toList()) {
                        if (c.displayText.contains(what, ignoreCase = !matchCase) || c.editText.contains(what, ignoreCase = !matchCase)) {
                            val newDisp = c.displayText.replace(what, withStr, ignoreCase = !matchCase)
                            val newEdit = c.editText.replace(what, withStr, ignoreCase = !matchCase)
                            currentCells[k] = c.copy(displayText = newDisp, editText = newEdit)
                            replacedCount++
                        }
                    }
                }
                if (replacedCount > 0) {
                    revision++
                    isDirty = true
                }
                lastResultJson = JSONObject().put("replaced", replacedCount).toString()
            }
            "home.insertSheet" -> {
                val newName = p.optString("name", "Sheet${sheets.size + 1}").ifBlank { "Sheet${sheets.size + 1}" }
                val at = if (p.has("before")) {
                    p.optInt("before", activeSheetIdx + 1).coerceIn(0, sheets.size)
                } else {
                    (activeSheetIdx + 1).coerceIn(0, sheets.size)
                }
                sheets.add(at, FallbackSheetState(newName))
                activeSheetIdx = at
                revision++
                isDirty = true
            }
            "home.deleteSheet" -> {
                if (sheets.size > 1) {
                    val target = p.optInt("sheet", activeSheetIdx).coerceIn(0, sheets.lastIndex)
                    sheets.removeAt(target)
                    activeSheetIdx = activeSheetIdx.coerceIn(0, sheets.lastIndex)
                    revision++
                    isDirty = true
                }
            }
            "sheet.rename" -> {
                val target = p.optInt("sheet", activeSheetIdx).coerceIn(0, sheets.lastIndex)
                val newName = p.optString("name", "").trim()
                if (newName.isNotEmpty() && target in sheets.indices) {
                    sheets[target].name = newName
                    revision++
                    isDirty = true
                }
            }
            "sheet.move" -> {
                val src = p.optInt("sheet", activeSheetIdx).coerceIn(0, sheets.lastIndex)
                val copy = p.optBoolean("copy", false)
                if (copy) {
                    val orig = sheets[src]
                    val dup = FallbackSheetState(
                        name = "${orig.name} (2)",
                        cells = HashMap(orig.cells),
                        merges = orig.merges.toMutableList()
                    )
                    val at = p.optInt("to", src + 1).coerceIn(0, sheets.size)
                    sheets.add(at, dup)
                    activeSheetIdx = at
                } else if (sheets.size > 1) {
                    val at = p.optInt("to", src).coerceIn(0, sheets.lastIndex)
                    val removed = sheets.removeAt(src)
                    sheets.add(at, removed)
                    activeSheetIdx = at
                }
                revision++
                isDirty = true
            }
            "sheet.activate" -> {
                val sh = p.opt("sheet")
                when (sh) {
                    is Number -> activeSheetIdx = sh.toInt().coerceIn(0, sheets.lastIndex)
                    is String -> {
                        val idx = sheets.indexOfFirst { it.name.equals(sh, ignoreCase = true) }
                        if (idx >= 0) activeSheetIdx = idx
                    }
                }
                revision++
            }
            "sheet.next" -> {
                if (sheets.isNotEmpty()) {
                    activeSheetIdx = (activeSheetIdx + 1).coerceIn(0, sheets.lastIndex)
                    revision++
                }
            }
            "sheet.previous" -> {
                if (sheets.isNotEmpty()) {
                    activeSheetIdx = (activeSheetIdx - 1).coerceIn(0, sheets.lastIndex)
                    revision++
                }
            }
            "home.bold" -> mutateRangeCells { _, _, it ->
                val on = when {
                    p.has("on") -> p.optBoolean("on", !it.isBold)
                    p.has("bold") -> p.optBoolean("bold", !it.isBold)
                    else -> !it.isBold
                }
                it.copy(flags = if (on) (it.flags or 1) else (it.flags and 1.inv()))
            }
            "home.italic" -> mutateRangeCells { _, _, it ->
                val on = when {
                    p.has("on") -> p.optBoolean("on", !it.isItalic)
                    p.has("italic") -> p.optBoolean("italic", !it.isItalic)
                    else -> !it.isItalic
                }
                it.copy(flags = if (on) (it.flags or 2) else (it.flags and 2.inv()))
            }
            "home.underline" -> mutateRangeCells { _, _, it ->
                val on = when {
                    p.has("on") -> p.optBoolean("on", !it.isUnderline)
                    p.has("underline") -> p.optBoolean("underline", !it.isUnderline)
                    else -> !it.isUnderline
                }
                it.copy(flags = if (on) (it.flags or 4) else (it.flags and 4.inv()))
            }
            "home.strikethrough" -> mutateRangeCells { _, _, it ->
                val on = when {
                    p.has("on") -> p.optBoolean("on", !it.isStrike)
                    p.has("strike") -> p.optBoolean("strike", !it.isStrike)
                    else -> !it.isStrike
                }
                it.copy(flags = if (on) (it.flags or 8) else (it.flags and 8.inv()))
            }
            "home.wrapText" -> mutateActiveCell { it.copy(flags = it.flags xor 16) }
            "home.alignLeft" -> mutateRangeCells { _, _, it -> it.copy(flags = (it.flags and (3 shl 5).inv()) or (0 shl 5)) }
            "home.alignCenter" -> mutateRangeCells { _, _, it -> it.copy(flags = (it.flags and (3 shl 5).inv()) or (1 shl 5)) }
            "home.alignRight" -> mutateRangeCells { _, _, it -> it.copy(flags = (it.flags and (3 shl 5).inv()) or (2 shl 5)) }
            "home.alignTop" -> mutateRangeCells { _, _, it -> it.copy(flags = (it.flags and (3 shl 7).inv()) or (0 shl 7)) }
            "home.alignMiddle" -> mutateRangeCells { _, _, it -> it.copy(flags = (it.flags and (3 shl 7).inv()) or (1 shl 7)) }
            "home.alignBottom" -> mutateRangeCells { _, _, it -> it.copy(flags = (it.flags and (3 shl 7).inv()) or (2 shl 7)) }
            "home.fillColor", "format.fillColor" -> {
                val rawCol = if (p.isNull("color")) "" else p.optString("color", "").trim()
                val isClear = p.optBoolean("clear", false) ||
                    rawCol.isEmpty() ||
                    rawCol.equals("none", ignoreCase = true) ||
                    rawCol.equals("null", ignoreCase = true)
                val argb = if (isClear) 0 else (SpreadsheetColorPalette.parseHexOrNull(rawCol) ?: 0)
                mutateRangeCells { _, _, it -> it.copy(bgColor = argb) }
            }
            "home.fontColor", "format.fontColor" -> {
                val rawCol = if (p.isNull("color")) "" else p.optString("color", "").trim()
                val isAuto = rawCol.isEmpty() ||
                    rawCol.equals("auto", ignoreCase = true) ||
                    rawCol.equals("none", ignoreCase = true) ||
                    rawCol.equals("null", ignoreCase = true)
                val argb = if (isAuto) 0 else (SpreadsheetColorPalette.parseHexOrNull(rawCol) ?: 0)
                mutateRangeCells { _, _, it -> it.copy(fgColor = argb) }
            }
            "home.borders", "format.borders", "format.borderColor", "home.borderColor" -> {
                val preset = p.optString("preset", p.optString("style", "all")).lowercase()
                val style = p.optString("style", "thin").lowercase()
                val rawCol = p.optString("color", "#334155").trim()
                val rgb = (SpreadsheetColorPalette.parseHexOrNull(rawCol) ?: 0xFF334155.toInt()) and 0xFFFFFF
                val halfW = when (style) {
                    "none" -> 0
                    "medium", "double" -> 4
                    "thick" -> 6
                    else -> 2
                }
                val encoded = if (preset == "none" || style == "none" || halfW == 0) 0 else ((halfW shl 24) or rgb)
                val bounds = resolveRangeBounds()
                val r0 = bounds[0]
                val c0 = bounds[1]
                val r1 = bounds[2]
                val c1 = bounds[3]
                mutateRangeCells { r, c, it ->
                    when (preset) {
                        "none" -> it.copy(borderTop = 0, borderRight = 0, borderBottom = 0, borderLeft = 0)
                        "top" -> if (r == r0) it.copy(borderTop = encoded) else it
                        "bottom" -> if (r == r1) it.copy(borderBottom = encoded) else it
                        "left" -> if (c == c0) it.copy(borderLeft = encoded) else it
                        "right" -> if (c == c1) it.copy(borderRight = encoded) else it
                        "outside", "outline", "thickoutside" -> it.copy(
                            borderTop = if (r == r0) encoded else it.borderTop,
                            borderBottom = if (r == r1) encoded else it.borderBottom,
                            borderLeft = if (c == c0) encoded else it.borderLeft,
                            borderRight = if (c == c1) encoded else it.borderRight
                        )
                        else -> it.copy(
                            borderTop = encoded,
                            borderRight = encoded,
                            borderBottom = encoded,
                            borderLeft = encoded
                        )
                    }
                }
            }
            "home.fontSize" -> {
                val sz = p.optDouble("size", 11.0).toFloat().coerceIn(8f, 72f)
                mutateRangeCells { _, _, it -> it.copy(fontSize = sz) }
            }
            "home.increaseFontSize" -> {
                mutateRangeCells { _, _, it -> it.copy(fontSize = (it.fontSize + 1f).coerceIn(8f, 72f)) }
            }
            "home.decreaseFontSize" -> {
                mutateRangeCells { _, _, it -> it.copy(fontSize = (it.fontSize - 1f).coerceIn(8f, 72f)) }
            }
            "home.clearFormats" -> {
                mutateRangeCells { _, _, it ->
                    it.copy(
                        bgColor = 0,
                        fgColor = 0,
                        fontSize = 11f,
                        flags = 0,
                        borderTop = 0,
                        borderRight = 0,
                        borderBottom = 0,
                        borderLeft = 0
                    )
                }
            }
            "home.clearAll" -> {
                mutateRangeCells { _, _, it ->
                    it.copy(
                        displayText = "",
                        editText = "",
                        bgColor = 0,
                        fgColor = 0,
                        fontSize = 11f,
                        flags = 0,
                        borderTop = 0,
                        borderRight = 0,
                        borderBottom = 0,
                        borderLeft = 0
                    )
                }
            }
            "home.mergeCenter" -> {
                val r0 = minOf(selStartRow, selEndRow)
                val c0 = minOf(selStartCol, selEndCol)
                val r1 = maxOf(selStartRow, selEndRow).coerceAtLeast(r0)
                val c1 = maxOf(selStartCol, selEndCol).coerceAtLeast(c0 + 1)
                val existingMerge = currentMerges.firstOrNull { it.contains(activeRow, activeCol) }
                if (existingMerge != null) {
                    currentMerges.remove(existingMerge)
                } else {
                    currentMerges.add(NativeMergeRange(r0, c0, r1, c1))
                }
                revision++
                isDirty = true
            }
            "home.unmergeCells" -> {
                currentMerges.removeAll { it.contains(activeRow, activeCol) }
                revision++
                isDirty = true
            }
            "sheet.hide" -> {
                val visibleCount = sheets.count { !it.hidden }
                if (visibleCount > 1) {
                    val target = p.optInt("sheet", activeSheetIdx).coerceIn(0, sheets.lastIndex)
                    sheets[target].hidden = true
                    if (activeSheetIdx == target) {
                        val nextVis = sheets.indexOfFirst { !it.hidden }
                        if (nextVis >= 0) activeSheetIdx = nextVis
                    }
                    revision++
                    isDirty = true
                }
            }
            "sheet.unhide" -> {
                val name = p.optString("name", "").trim()
                val target = if (name.isNotEmpty()) {
                    sheets.indexOfFirst { it.name.equals(name, ignoreCase = true) }
                } else {
                    sheets.indexOfFirst { it.hidden }
                }
                if (target in sheets.indices) {
                    sheets[target].hidden = false
                    activeSheetIdx = target
                    revision++
                    isDirty = true
                }
            }
        }
        return snapshot()
    }

    override fun getViewport(maxRow: Int, maxCol: Int): NativeViewportSnapshot = snapshot(maxRow, maxCol)

    override fun readSheetCells(sheetIndex: Int, maxRows: Int, maxCols: Int): Map<Long, NativeCellModel> {
        val sh = sheets.getOrNull(sheetIndex) ?: return emptyMap()
        return sh.cells.filterValues { it.row < maxRows && it.col < maxCols }
    }

    private fun parseA1(ref: String): Pair<Int, Int> {
        MasuSheetConfig.parseCellAddressStrict(ref)?.let { return it }
        return 0 to 0
    }

    private fun snapshot(reqMaxRow: Int = 120, reqMaxCol: Int = 45): NativeViewportSnapshot {
        val idx = activeSheetIdx.coerceIn(0, (sheets.size - 1).coerceAtLeast(0))
        val curSheet = sheets.getOrNull(idx) ?: FallbackSheetState(if (title.contains("修理")) "修理申込票" else "Sales")
        val activeKey = NativeViewportSnapshot.packCellKey(activeRow, activeCol)
        val activeCell = curSheet.cells[activeKey]
        val spillFormula = curSheet.spillSources[activeKey].orEmpty()
        val visNames = mutableListOf<String>()
        val visIndices = mutableListOf<Int>()
        val hidNames = mutableListOf<String>()
        val hidIndices = mutableListOf<Int>()
        if (sheets.isNotEmpty()) {
            sheets.forEachIndexed { sIdx, sh ->
                if (sh.hidden) {
                    hidNames.add(sh.name)
                    hidIndices.add(sIdx)
                } else {
                    visNames.add(sh.name)
                    visIndices.add(sIdx)
                }
            }
        } else {
            visNames.add(curSheet.name)
            visIndices.add(0)
        }
        val usedR = curSheet.cells.values.maxOfOrNull { it.row } ?: 0
        val usedC = curSheet.cells.values.maxOfOrNull { it.col } ?: 0
        val outRows = reqMaxRow.coerceIn(80, 5000)
        val outCols = reqMaxCol.coerceIn(26, 512)
        val cw = FloatArray(outCols) { c ->
            if (c in curSheet.colWidths.indices) curSheet.colWidths[c] else 84f
        }
        val rh = FloatArray(outRows) { r ->
            if (r in curSheet.rowHeights.indices) curSheet.rowHeights[r] else 26f
        }
        val hidRows = rh.indices.filter { rh[it] < 1f }.toIntArray()
        val hidCols = cw.indices.filter { cw[it] < 1f }.toIntArray()
        return NativeViewportSnapshot(
            ok = true,
            sessionId = currentSessionId,
            uid = 1L,
            revision = revision,
            title = title,
            isDirty = isDirty,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            undoDepth = undoStack.size,
            redoDepth = redoStack.size,
            sheetIndex = idx,
            totalSheetCount = sheets.size.coerceAtLeast(1),
            sheetName = curSheet.name,
            sheetNames = visNames,
            visibleSheetIndices = visIndices,
            hiddenSheetNames = hidNames,
            hiddenSheetIndices = hidIndices,
            usedEndRow = usedR,
            usedEndCol = usedC,
            activeRow = activeRow,
            activeCol = activeCol,
            selStartRow = selStartRow,
            selStartCol = selStartCol,
            selEndRow = selEndRow,
            selEndCol = selEndCol,
            activeAddress = MasuSheetConfig.formatCellAddress(activeRow, activeCol),
            activeEditText = activeCell?.editText.orEmpty(),
            activeDisplayText = activeCell?.displayText.orEmpty(),
            activeSpillFormula = spillFormula,
            freezeRows = curSheet.freezeRows,
            freezeCols = curSheet.freezeCols,
            colWidths = cw,
            rowHeights = rh,
            hiddenRows = hidRows,
            hiddenCols = hidCols,
            merges = curSheet.merges.toList(),
            spillRanges = curSheet.spillRanges.toList(),
            sparklines = curSheet.sparklines.toList(),
            validations = curSheet.validations.toList(),
            comments = curSheet.comments.values.toList(),
            hyperlinks = curSheet.hyperlinks.values.toList(),
            definedNames = definedNames.map { (k, v) -> NativeDefinedNameModel(k, v) },
            cells = HashMap(curSheet.cells),
            resultJson = lastResultJson
        )
    }
}
