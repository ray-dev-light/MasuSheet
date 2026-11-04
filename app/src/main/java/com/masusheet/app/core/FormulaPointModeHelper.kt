package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] Interactive Formula Point-Mode Reference Insertion & Color Highlighter
 * Provides [FormulaPointInsertResult], [FormulaRefHighlight], and [FormulaPointModeHelper] for tap/drag A1 range insertion and color-coded reference borders while editing formulas.
 *
 * ---
 * [JA / 日本語解説]
 * 数式入力中にセルや範囲をタップ・ドラッグして参照 (`A1`, `B2:D10` 等) を挿入・置換し、参照枠を色分けハイライト表示する [FormulaPointModeHelper] モジュールです。
 * ============================================================================
 */

/**
 * [EN] Updated formula string and caret position after inserting or replacing a pointed cell/range reference.
 * [JA] 数式ポイントモードでセル・範囲参照を挿入／置換した後の数式文字列とカーソル位置です。
 */
data class FormulaPointInsertResult(
    val updatedText: String,
    val newCaretIndex: Int,
    val newPointSpan: IntRange
)

/**
 * [EN] Parsed A1 reference coordinates and palette color used to highlight referenced cells on the sheet grid.
 * [JA] 編集中数式内のセル参照範囲と、グリッド上に描画するハイライト枠の色情報です。
 */
data class FormulaRefHighlight(
    val textRange: IntRange,
    val sheetName: String?,
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
    val colorArgb: Int
)

/**
 * Ports original Rust MasuSheet's formula Point Mode & colored reference highlighting (`crates/ui-egui/src/editor.rs` & `formula_bar.rs`)
 * to the native Android sheet canvas and in-cell / formula-bar editors.
 */
object FormulaPointModeHelper {

    val REF_COLORS: IntArray = intArrayOf(
        0xFF2563EB.toInt(), // Blue
        0xFFDC2626.toInt(), // Red
        0xFF7C3AED.toInt(), // Purple
        0xFF059669.toInt(), // Emerald Green
        0xFFD97706.toInt(), // Amber / Orange
        0xFFDB2777.toInt(), // Magenta / Pink
        0xFF0891B2.toInt(), // Cyan / Teal
        0xFF4F46E5.toInt()  // Indigo
    )

    private val POINT_TRIGGER_CHARS: Set<Char> = setOf(
        '=', '(', ',', ';', '+', '-', '*', '/', '^', '&', '<', '>', ':', '{',
        '＝', '（', '，', '；', '＋', '－', '＊', '／', '＾', '＆', '＜', '＞', '：', '｛'
    )

    fun isFormulaText(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("=") || trimmed.startsWith("＝")
    }

    /**
     * Normalizes full-width leading `＝` and formula operators outside string literals so Japanese IME input works seamlessly.
     * Also prevents committing a bare `"="` with no expression after it (which would otherwise produce `#ERROR!`).
     */
    fun normalizeCommittedFormula(text: String): String {
        val trimmed = text.trim()
        if (trimmed == "=" || trimmed == "＝") {
            return ""
        }
        if (!isFormulaText(text)) return text
        val sb = StringBuilder(text.length)
        var inQuotes = false
        var leadingEqualReplaced = false
        for (ch in text) {
            if (ch == '"') {
                inQuotes = !inQuotes
                sb.append(ch)
                continue
            }
            if (!inQuotes) {
                val mapped = when (ch) {
                    '＝' -> {
                        leadingEqualReplaced = true
                        '='
                    }
                    '（' -> '('
                    '）' -> ')'
                    '，' -> ','
                    '；' -> ';'
                    '＋' -> '+'
                    '－' -> '-'
                    '＊' -> '*'
                    '／' -> '/'
                    '＾' -> '^'
                    '＆' -> '&'
                    '＜' -> '<'
                    '＞' -> '>'
                    '：' -> ':'
                    else -> ch
                }
                sb.append(mapped)
            } else {
                sb.append(ch)
            }
        }
        // Auto-close up to 8 missing closing parentheses at the end of a formula (e.g. `=SUM(B2:B10` -> `=SUM(B2:B10)`), matching Spreadsheet
        var openParens = 0
        inQuotes = false
        for (ch in sb) {
            if (ch == '"') inQuotes = !inQuotes
            else if (!inQuotes) {
                if (ch == '(') openParens++
                else if (ch == ')' && openParens > 0) openParens--
            }
        }
        if (openParens in 1..8) {
            repeat(openParens) { sb.append(')') }
        }
        return sb.toString()
    }

    /**
     * Returns true when clicking or dragging a cell/range/column/row on the grid should insert or replace a cell reference
     * at [caretIndex] instead of committing and closing the editor (ported from `EditState::can_point` in `crates/ui-egui/src/editor.rs`).
     */
    fun canPoint(
        text: String,
        caretIndex: Int = text.length,
        activePointSpan: IntRange? = null
    ): Boolean {
        if (!isFormulaText(text)) return false
        val safeCaret = caretIndex.coerceIn(0, text.length)
        if (activePointSpan != null &&
            activePointSpan.first >= 0 &&
            activePointSpan.last + 1 <= text.length &&
            safeCaret == activePointSpan.last + 1
        ) {
            return true
        }
        val prefix = text.substring(0, safeCaret)
        // Check that caret is not inside an unclosed string literal `"..."`
        val quoteCount = prefix.count { it == '"' }
        if (quoteCount % 2 != 0) return false
        val beforeTrimmed = prefix.trimEnd()
        if (beforeTrimmed.isEmpty()) return false
        return beforeTrimmed.last() in POINT_TRIGGER_CHARS
    }

    /**
     * Inserts [newRef] at [caretIndex], or replaces [activePointSpan] if one is active at [caretIndex]
     * (ported from `EditState::insert_ref` in `crates/ui-egui/src/editor.rs`).
     */
    fun insertOrReplaceRef(
        text: String,
        caretIndex: Int = text.length,
        activePointSpan: IntRange? = null,
        newRef: String
    ): FormulaPointInsertResult {
        val normalizedBase = if (text.startsWith("＝")) "=" + text.substring(1) else text
        val safeCaret = caretIndex.coerceIn(0, normalizedBase.length)
        return if (activePointSpan != null &&
            activePointSpan.first >= 0 &&
            activePointSpan.last + 1 <= normalizedBase.length &&
            safeCaret == activePointSpan.last + 1
        ) {
            val start = activePointSpan.first
            val endExcl = activePointSpan.last + 1
            val updated = normalizedBase.substring(0, start) + newRef + normalizedBase.substring(endExcl)
            val newEndExcl = start + newRef.length
            FormulaPointInsertResult(
                updatedText = updated,
                newCaretIndex = newEndExcl,
                newPointSpan = start until newEndExcl
            )
        } else {
            val updated = normalizedBase.substring(0, safeCaret) + newRef + normalizedBase.substring(safeCaret)
            val newEndExcl = safeCaret + newRef.length
            FormulaPointInsertResult(
                updatedText = updated,
                newCaretIndex = newEndExcl,
                newPointSpan = safeCaret until newEndExcl
            )
        }
    }

    /**
     * Appends `,newRef` at [caretIndex] for Ctrl+Click union selection in Point Mode
     * (ported from `EditState::append_ref` in `crates/ui-egui/src/editor.rs`).
     */
    fun appendUnionRef(
        text: String,
        caretIndex: Int = text.length,
        newRef: String
    ): FormulaPointInsertResult {
        val safeCaret = caretIndex.coerceIn(0, text.length)
        val insertion = ",$newRef"
        val updated = text.substring(0, safeCaret) + insertion + text.substring(safeCaret)
        val spanStart = safeCaret + 1
        val spanEndExcl = spanStart + newRef.length
        return FormulaPointInsertResult(
            updatedText = updated,
            newCaretIndex = spanEndExcl,
            newPointSpan = spanStart until spanEndExcl
        )
    }

    fun quoteSheetNameIfNeeded(sheetName: String): String {
        val clean = sheetName.trim()
        if (clean.isEmpty()) return ""
        val needsQuotes = clean.any { ch ->
            ch.isWhitespace() || ch in setOf('!', '\'', '"', '+', '-', '*', '/', '^', '&', '=', '<', '>', '(', ')', ',', ';', ':', '[', ']')
        } || clean.first().isDigit()
        return if (needsQuotes) {
            "'${clean.replace("'", "''")}'"
        } else {
            clean
        }
    }

    private fun qualifyWithSheetIfNeeded(
        refBody: String,
        targetSheetIndex: Int,
        editingSheetIndex: Int,
        targetSheetName: String
    ): String {
        return if (targetSheetIndex != editingSheetIndex && targetSheetName.isNotBlank()) {
            "${quoteSheetNameIfNeeded(targetSheetName)}!$refBody"
        } else {
            refBody
        }
    }

    fun formatCellOrRangeRef(
        startRow: Int,
        startCol: Int,
        endRow: Int = startRow,
        endCol: Int = startCol,
        targetSheetIndex: Int = 0,
        editingSheetIndex: Int = 0,
        targetSheetName: String = ""
    ): String {
        val r0 = minOf(startRow, endRow).coerceAtLeast(0)
        val c0 = minOf(startCol, endCol).coerceAtLeast(0)
        val r1 = maxOf(startRow, endRow).coerceAtLeast(r0)
        val c1 = maxOf(startCol, endCol).coerceAtLeast(c0)
        val body = if (r0 == r1 && c0 == c1) {
            MasuSheetConfig.formatCellAddress(r0, c0)
        } else {
            "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c1)}"
        }
        return qualifyWithSheetIfNeeded(body, targetSheetIndex, editingSheetIndex, targetSheetName)
    }

    fun formatColumnRangeRef(
        startCol: Int,
        endCol: Int = startCol,
        targetSheetIndex: Int = 0,
        editingSheetIndex: Int = 0,
        targetSheetName: String = ""
    ): String {
        val c0 = minOf(startCol, endCol).coerceAtLeast(0)
        val c1 = maxOf(startCol, endCol).coerceAtLeast(c0)
        val body = "${MasuSheetConfig.formatColumnLabel(c0)}:${MasuSheetConfig.formatColumnLabel(c1)}"
        return qualifyWithSheetIfNeeded(body, targetSheetIndex, editingSheetIndex, targetSheetName)
    }

    fun formatRowRangeRef(
        startRow: Int,
        endRow: Int = startRow,
        targetSheetIndex: Int = 0,
        editingSheetIndex: Int = 0,
        targetSheetName: String = ""
    ): String {
        val r0 = minOf(startRow, endRow).coerceAtLeast(0)
        val r1 = maxOf(startRow, endRow).coerceAtLeast(r0)
        val body = "${r0 + 1}:${r1 + 1}"
        return qualifyWithSheetIfNeeded(body, targetSheetIndex, editingSheetIndex, targetSheetName)
    }

    /**
     * Cycles absolute/relative `$` anchors on the cell/range reference at [caretIndex] when F4 is pressed:
     * `A1` -> `$A$1` -> `A$1` -> `$A1` -> `A1` (ported from `formula_bar::cycle_anchor` in `crates/ui-egui/src/formula_bar.rs`).
     */
    fun cycleAbsoluteRelativeAtCaret(text: String, caretIndex: Int = text.length): Pair<String, Int> {
        if (!isFormulaText(text)) return text to caretIndex
        val quoteMask = BooleanArray(text.length)
        var inQuotes = false
        for (i in text.indices) {
            if (text[i] == '"') {
                quoteMask[i] = true
                inQuotes = !inQuotes
            } else {
                quoteMask[i] = inQuotes
            }
        }
        val tokenRegex = Regex("(\\$?[A-Za-z]{1,3}\\$?\\d+(?::\\$?[A-Za-z]{1,3}\\$?\\d+)?)(?![A-Za-z0-9_\\(])")
        val safeCaret = caretIndex.coerceIn(0, text.length)
        val match = tokenRegex.findAll(text).firstOrNull { m ->
            if (m.range.first in quoteMask.indices && quoteMask[m.range.first]) return@firstOrNull false
            if (m.range.first > 0) {
                val prev = text[m.range.first - 1]
                if (prev.isLetterOrDigit() || prev == '_' || prev == '.') return@firstOrNull false
            }
            val endpointsOk = m.value.split(':').all { parseA1Endpoint(it) != null }
            endpointsOk && safeCaret in m.range.first..(m.range.last + 1)
        } ?: return text to caretIndex

        fun cycleSingleRef(single: String): String {
            val m = Regex("^(\\$?)([A-Za-z]{1,3})(\\$?)(\\d+)$").matchEntire(single) ?: return single
            val colAbs = m.groupValues[1] == "$"
            val colStr = m.groupValues[2].uppercase()
            val rowAbs = m.groupValues[3] == "$"
            val rowStr = m.groupValues[4]
            return when {
                !colAbs && !rowAbs -> "\$$colStr\$$rowStr"
                colAbs && rowAbs -> "$colStr\$$rowStr"
                !colAbs && rowAbs -> "\$$colStr$rowStr"
                else -> "$colStr$rowStr"
            }
        }

        val parts = match.value.split(':')
        val cycled = parts.joinToString(":") { cycleSingleRef(it) }
        val updated = text.substring(0, match.range.first) + cycled + text.substring(match.range.last + 1)
        val newCaret = match.range.first + cycled.length
        return updated to newCaret
    }

    private val FORMULA_REF_TOKEN_REGEX = Regex(
        """(?:'((?:[^']|'')+)'!|([A-Za-z0-9_\u3000-\u9FFF]+)!)?(\$?[A-Za-z]{1,3}\$?\d+(?::\$?[A-Za-z]{1,3}\$?\d+)?|\$?[A-Za-z]{1,3}:\$?[A-Za-z]{1,3}|\$?\d+:\$?\d+)(?![A-Za-z0-9_\(])"""
    )

    private fun parseA1Endpoint(part: String): Pair<Int, Int>? {
        return MasuSheetConfig.parseCellAddressStrict(part)
    }

    /**
     * Extracts all cell, range, column-range, and row-range references in [text] along with their 8-color cycle
     * (ported from `editor::formula_refs` in `crates/ui-egui/src/editor.rs`).
     */
    fun extractFormulaReferences(
        text: String,
        maxRowIndex: Int = 999,
        maxColIndex: Int = 51
    ): List<FormulaRefHighlight> {
        if (!isFormulaText(text)) return emptyList()
        // Mask out characters inside double quotes `"..."` so strings like `"A1"` are not highlighted as cell refs
        val quoteMask = BooleanArray(text.length)
        var inQuotes = false
        for (i in text.indices) {
            if (text[i] == '"') {
                quoteMask[i] = true
                inQuotes = !inQuotes
            } else {
                quoteMask[i] = inQuotes
            }
        }

        val results = mutableListOf<FormulaRefHighlight>()
        var colorIdx = 0
        for (m in FORMULA_REF_TOKEN_REGEX.findAll(text)) {
            if (m.range.first in quoteMask.indices && quoteMask[m.range.first]) continue
            // Ensure this token isn't immediately preceded by a letter/digit/underscore
            if (m.range.first > 0) {
                val prev = text[m.range.first - 1]
                if (prev.isLetterOrDigit() || prev == '_' || prev == '.') continue
            }
            val quotedSheet = m.groups[1]?.value?.replace("''", "'")
            val unquotedSheet = m.groups[2]?.value
            val sheetName = quotedSheet ?: unquotedSheet
            val body = m.groups[3]?.value ?: continue

            val coords: IntArray? = if (body.contains(':')) {
                val (leftRaw, rightRaw) = body.split(':', limit = 2)
                val leftClean = leftRaw.replace("$", "").uppercase()
                val rightClean = rightRaw.replace("$", "").uppercase()
                val p0 = parseA1Endpoint(leftClean)
                val p1 = parseA1Endpoint(rightClean)
                if (p0 != null && p1 != null) {
                    intArrayOf(
                        minOf(p0.first, p1.first),
                        minOf(p0.second, p1.second),
                        maxOf(p0.first, p1.first),
                        maxOf(p0.second, p1.second)
                    )
                } else if (leftClean.all { it in 'A'..'Z' } && rightClean.all { it in 'A'..'Z' }) {
                    val c0 = MasuSheetConfig.parseColumnLabel(leftClean)
                    val c1 = MasuSheetConfig.parseColumnLabel(rightClean)
                    intArrayOf(0, minOf(c0, c1), maxRowIndex.coerceAtLeast(0), maxOf(c0, c1))
                } else if (leftClean.all { it.isDigit() } && rightClean.all { it.isDigit() }) {
                    val r0 = (leftClean.toIntOrNull() ?: 1) - 1
                    val r1 = (rightClean.toIntOrNull() ?: 1) - 1
                    if (r0 >= 0 && r1 >= 0) {
                        intArrayOf(minOf(r0, r1), 0, maxOf(r0, r1), maxColIndex.coerceAtLeast(0))
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                val p = parseA1Endpoint(body)
                if (p != null) {
                    intArrayOf(p.first, p.second, p.first, p.second)
                } else {
                    null
                }
            }

            if (coords != null) {
                val color = REF_COLORS[colorIdx % REF_COLORS.size]
                colorIdx++
                results.add(
                    FormulaRefHighlight(
                        textRange = m.range,
                        sheetName = sheetName,
                        startRow = coords[0],
                        startCol = coords[1],
                        endRow = coords[2],
                        endCol = coords[3],
                        colorArgb = color
                    )
                )
            }
        }
        return results
    }
}

