package com.masusheet.app.core

/**
 * ============================================================================
 * [EN] Inline Cell Checkbox Parser, Workbook Format Classifier, & Scroll Input Math
 * Provides [InlineCheckboxHelper] for interactive Unicode/boolean cell checkboxes, [WorkbookFormatClassifier] for magic-byte format detection, and [ScrollInputMath] for NaN-safe pointer deltas.
 *
 * ---
 * [JA / 日本語解説]
 * セル内チェックボックス判定・トグル ([InlineCheckboxHelper])、マジックバイトに基づくファイル形式判定 ([WorkbookFormatClassifier])、およびNaN安全なスクロール差分計算 ([ScrollInputMath]) を提供します。
 * ============================================================================
 */

/**
 * [EN] Utility for parsing, rendering, and toggling interactive Unicode/boolean checkboxes inside spreadsheet cells.
 * [JA] セル内のチェックボックス記号 (`□` / `☑` 等) の検出・描画分解・クリック反転を行うヘルパーです。
 */
object InlineCheckboxHelper {
    private val uncheckedBoxes = setOf('□', '☐', '◻', '▫', '⬜')
    private val checkedBoxes = setOf('☑', '☒', '■')

    fun isUncheckedBox(ch: Char): Boolean = ch in uncheckedBoxes

    fun isCheckedBox(ch: Char): Boolean = ch in checkedBoxes

    fun isCheckboxChar(ch: Char): Boolean = isUncheckedBox(ch) || isCheckedBox(ch)

    fun containsCheckbox(text: String): Boolean = text.any { isCheckboxChar(it) }

    fun toggleNthCheckbox(text: String, targetIndex: Int): String? {
        if (targetIndex < 0) return null
        var currentIdx = 0
        val sb = StringBuilder(text.length)
        var toggled = false
        for (ch in text) {
            if (!toggled && isCheckboxChar(ch)) {
                if (currentIdx == targetIndex) {
                    sb.append(if (isUncheckedBox(ch)) '☑' else '□')
                    toggled = true
                    continue
                }
                currentIdx++
            }
            sb.append(ch)
        }
        return if (toggled) sb.toString() else null
    }

    /**
     * Resolves a smart caret position when double-clicking inside a cell containing template
     * markers such as `年`, `月`, `日`, `時`, `分`, or `@` / `＠`.
     *
     * - For single-marker suffix cells (`年`, `月`, `日`), places the caret immediately before the marker (`0`).
     * - When clicking on or just after `年` / `月` / `日` / `時` / `分` (or on spaces leading up to a date unit),
     *   snaps the caret immediately before that marker ("年の前", "月の前", "日の前").
     * - When clicking near `@` / `＠`, places the caret immediately before `@` (local-part) or immediately after `@` (domain-part).
     */
    fun resolveSmartCaretIndex(text: String, approxCharIndex: Int): Int {
        if (text.isEmpty()) return 0
        val chars = text.toCharArray()
        val n = chars.size
        val trimmed = text.trim()
        val dateMarkers = setOf('年', '月', '日', '時', '分')
        if (trimmed.length == 1 && trimmed[0] in dateMarkers) {
            val firstNonSpace = chars.indexOfFirst { !it.isWhitespace() }
            return if (firstNonSpace >= 0) firstNonSpace else 0
        }
        val idx = approxCharIndex.coerceIn(0, n)
        if (idx < n && chars[idx] in dateMarkers) {
            return idx
        }
        if (idx > 0 && idx <= n && chars[idx - 1] in dateMarkers) {
            return idx - 1
        }
        // If text has date markers and click landed on whitespace between units, snap to the next unit marker
        val hasDateMarkers = chars.any { it in dateMarkers }
        if (hasDateMarkers && idx < n && chars[idx].isWhitespace()) {
            for (scan in idx until n) {
                if (chars[scan] in dateMarkers) {
                    return scan
                }
                if (!chars[scan].isWhitespace()) {
                    break
                }
            }
        }
        if (idx < n && (chars[idx] == '@' || chars[idx] == '＠')) {
            return idx
        }
        if (idx > 0 && idx <= n && (chars[idx - 1] == '@' || chars[idx - 1] == '＠')) {
            return idx
        }
        // If text is an email template with spaces and '@', snap left of '@' to '@', or right of '@' to after '@'
        val atPos = chars.indexOfFirst { it == '@' || it == '＠' }
        if (atPos >= 0 && chars.all { it.isWhitespace() || it == '@' || it == '＠' }) {
            return if (idx <= atPos) atPos else (atPos + 1).coerceAtMost(n)
        }
        return idx
    }
}

/**
 * [EN] Detects workbook format (`.xlsx` ZIP magic bytes vs `.csv`/`.tsv` text) from raw file bytes and filenames.
 * [JA] 先頭マジックバイト（ZIP署名等）と拡張子からワークブックの実フォーマットを判定する分類器です。
 */
object WorkbookFormatClassifier {
    private val spreadsheetExtensions = setOf("xlsx", "xlsm", "xlsb", "xls", "ods", "csv", "tsv", "txt")
    private val sampleOrder = listOf("sales", "budget", "grades", "repair")

    fun isSupportedSpreadsheet(fileName: String?): Boolean {
        val name = fileName?.trim().orEmpty()
        if (name.isEmpty() || !name.contains('.')) return false
        val ext = name.substringAfterLast('.').lowercase()
        return ext in spreadsheetExtensions
    }

    fun detectFormat(fileName: String?): WorkbookFormat {
        val ext = fileName?.trim()?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "xlsx", "xlsm", "xlsb", "xls" -> WorkbookFormat.XLSX
            "csv" -> WorkbookFormat.CSV
            "tsv", "txt" -> WorkbookFormat.TSV
            "ods" -> WorkbookFormat.ODS
            "pdf" -> WorkbookFormat.PDF
            else -> WorkbookFormat.UNKNOWN
        }
    }

    fun nextSampleWorkbook(currentSample: String?): String {
        val idx = sampleOrder.indexOf(currentSample?.trim()?.lowercase())
        return if (idx < 0) sampleOrder.first() else sampleOrder[(idx + 1) % sampleOrder.size]
    }
}

/**
 * [EN] NaN-safe scroll wheel and pointer drag coordinate conversion helpers.
 * [JA] マウスホイール回転量やポインタ移動量を `NaN` 安全にシート座標差分へ変換する計算ヘルパーです。
 */
object ScrollInputMath {
    fun computeWheelScrollDelta(
        axisHScroll: Float,
        axisVScroll: Float,
        stepPoints: Float = 64f
    ): Pair<Float, Float> {
        val cleanStep = stepPoints.coerceIn(16f, 240f)
        val dx = if (axisHScroll.isFinite()) -axisHScroll * cleanStep else 0f
        val dy = if (axisVScroll.isFinite()) axisVScroll * cleanStep else 0f
        return dx to dy
    }

    fun computeTrackpadSwipeDelta(
        prevX: Float,
        prevY: Float,
        currX: Float,
        currY: Float,
        density: Float
    ): Pair<Float, Float> {
        val safeDensity = if (density.isFinite() && density > 0.1f) density else 1f
        val dx = if (prevX.isFinite() && currX.isFinite()) (currX - prevX) / safeDensity else 0f
        val dy = if (prevY.isFinite() && currY.isFinite()) (currY - prevY) / safeDensity else 0f
        return dx to dy
    }
}
