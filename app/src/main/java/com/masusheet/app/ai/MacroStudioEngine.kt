package com.masusheet.app.ai

import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.core.SpreadsheetColorPalette
import com.masusheet.app.model.NativeViewportSnapshot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Macro Recorder, VBA Parser/Generator, & Step-by-Step Debugger Engine
 * Implements [MacroStep], [MacroDefinition], [VbaDebugState], and [MacroStudioEngine] for recording user actions, bidirectional VBA conversion, loop expansion, and single-step execution.
 *
 * ---
 * [JA / 日本語解説]
 * 操作マクロの記録、VBAサブプロシージャの双方向コード生成・ループ展開 (`For..Next`)、および1行ずつのステップ実行デバッガを担う [MacroStudioEngine] モジュールです。
 * ============================================================================
 */

// ============================================================================
// 3. ALL-IN-ONE MACRO & VBA AUTOMATION STUDIO (RECORDER + VBA + 1-STEP DEBUGGER + AI CONVERTER)
// ============================================================================

/**
 * [EN] Single recorded or parsed macro command step with its JSON parameter payload.
 * [JA] 記録または解析されたマクロの1ステップ（コマンドIDとJSON引数）を表すデータクラスです。
 */
data class MacroStep(
    val command: String,
    val paramsJson: String = "{}"
)

/**
 * [EN] Named macro definition holding recorded steps, generated VBA source code, and keyboard shortcut metadata.
 * [JA] マクロ名、ショートカット、記録ステップ列、および生成されたVBAソースコードを保持するマクロ定義モデルです。
 */
data class MacroDefinition(
    val name: String,
    val description: String = "",
    val shortcutKey: String = "Ctrl+Shift+M",
    val relativeReference: Boolean = false,
    val steps: List<MacroStep> = emptyList(),
    val vbaCode: String = ""
)

/**
 * [EN] Interactive VBA step-by-step debugger state tracking current line index, inspected variables, and execution logs.
 * [JA] VBA 1行ステップ実行デバッガの現在行・監視変数・実行ログを保持する状態モデルです。
 */
data class VbaDebugState(
    val isDebugging: Boolean = false,
    val macroName: String = "",
    val executableLines: List<String> = emptyList(),
    val currentLineIndex: Int = 0,
    val variables: Map<String, String> = emptyMap(),
    val consoleLog: List<String> = emptyList(),
    val lastMsgBox: String? = null,
    val undoStepCount: Int = 0
)

/**
 * [EN] Stateless engine converting recorded commands to/from VBA code, expanding `For..Next` loops, and generating macros from prompts.
 * [JA] 記録コマンドとVBAコードの双方向変換、`For..Next` ループ展開、および自然言語からのマクロ生成を行うエンジンです。
 */
object MacroStudioEngine {

    fun defaultBuiltInMacros(): List<MacroDefinition> = listOf(
        MacroDefinition(
            name = "FormatHeaderAndBorders",
            description = "1行目の見出しをネイビー背景・白太字にし、表全体に格子罫線を設定します",
            shortcutKey = "Ctrl+Shift+H",
            relativeReference = false,
            vbaCode = """
                Sub FormatHeaderAndBorders()
                    ' 見出し行のスタイル設定と表全体の罫線・列幅自動調整
                    Range("A1:D1").Interior.Color = RGB(31, 78, 121)
                    Range("A1:D1").Font.Color = RGB(255, 255, 255)
                    Range("A1:D1").Font.Bold = True
                    Range("A1:D5").Borders.LineStyle = xlContinuous
                    Columns("A:D").AutoFit
                    MsgBox "見出しと罫線の自動整形が完了しました！"
                End Sub
            """.trimIndent()
        ),
        MacroDefinition(
            name = "AddRowAndColumnTotals",
            description = "E列に行合計（SUM）、6行目に列合計（SUM）を自動挿入して太字にします",
            shortcutKey = "Ctrl+Shift+T",
            relativeReference = false,
            vbaCode = """
                Sub AddRowAndColumnTotals()
                    ' E列と最終行にSUM集計数式を自動挿入
                    Range("E1").Value = "合計"
                    Range("E1").Font.Bold = True
                    For r = 2 To 5
                        Cells(r, 5).Formula = "=SUM(B" & r & ":D" & r & ")"
                    Next r
                    Range("A6").Value = "全体合計"
                    Range("B6").Formula = "=SUM(B2:B5)"
                    Range("C6").Formula = "=SUM(C2:C5)"
                    Range("D6").Formula = "=SUM(D2:D5)"
                    Range("E6").Formula = "=SUM(E2:E5)"
                    Range("A6:E6").Font.Bold = True
                    Range("A6:E6").Interior.Color = RGB(232, 245, 233)
                End Sub
            """.trimIndent()
        ),
        MacroDefinition(
            name = "ApplyTax10Percent",
            description = "選択範囲の数値を税込（1.10倍）に変換します",
            shortcutKey = "Ctrl+Shift+X",
            relativeReference = true,
            vbaCode = """
                Sub ApplyTax10Percent()
                    ' B2:D5 の数値データを税込（1.1倍）に更新
                    For r = 2 To 5
                        For c = 2 To 4
                            Cells(r, c).MultiplyBy = 1.1
                        Next c
                    Next r
                    MsgBox "数値データを税込(10%)に変換しました。"
                End Sub
            """.trimIndent()
        )
    )

    fun stepsToVbaCode(
        name: String,
        description: String,
        shortcutKey: String,
        steps: List<MacroStep>
    ): String {
        val safeSubName = name.replace(Regex("[^A-Za-z0-9_一-龠ぁ-んァ-ヶ]"), "_").ifBlank { "Macro1" }
        val sb = StringBuilder()
        sb.appendLine("Sub $safeSubName()")
        if (description.isNotBlank()) {
            sb.appendLine("    ' 説明: $description")
        }
        if (shortcutKey.isNotBlank()) {
            sb.appendLine("    ' ショートカット: $shortcutKey")
        }
        if (steps.isEmpty()) {
            sb.appendLine("    ' 記録された操作ステップはありません")
        }
        for (step in steps) {
            val p = runCatching { JSONObject(step.paramsJson) }.getOrNull() ?: JSONObject()
            val explicitRange = p.optString("range", "").trim()
            val targetExpr = if (explicitRange.isNotEmpty()) "Range(\"$explicitRange\")" else "Selection"
            val vbaLine = when (step.command) {
                "cell.set" -> {
                    val cell = p.optString("cell", "ActiveCell")
                    val input = p.optString("input", "")
                    val prop = if (input.startsWith("=")) "Formula" else "Value"
                    val escaped = input.replace("\"", "\"\"")
                    "Range(\"$cell\").$prop = \"$escaped\""
                }
                "selection.set" -> {
                    val rng = p.optString("range", p.optString("active", "A1"))
                    "Range(\"$rng\").Select"
                }
                "format.bold", "home.bold" -> {
                    val on = if (p.has("bold")) p.optBoolean("bold", true) else if (p.has("on")) p.optBoolean("on", true) else true
                    "$targetExpr.Font.Bold = ${if (on) "True" else "False"}"
                }
                "format.italic", "home.italic" -> {
                    val on = if (p.has("italic")) p.optBoolean("italic", true) else if (p.has("on")) p.optBoolean("on", true) else true
                    "$targetExpr.Font.Italic = ${if (on) "True" else "False"}"
                }
                "format.underline", "home.underline" -> {
                    val on = if (p.has("underline")) p.optBoolean("underline", true) else if (p.has("on")) p.optBoolean("on", true) else true
                    "$targetExpr.Font.Underline = ${if (on) "xlUnderlineStyleSingle" else "xlUnderlineStyleNone"}"
                }
                "format.strikethrough", "home.strikethrough" -> {
                    val on = if (p.has("strikethrough")) p.optBoolean("strikethrough", true) else if (p.has("on")) p.optBoolean("on", true) else true
                    "$targetExpr.Font.Strikethrough = ${if (on) "True" else "False"}"
                }
                "format.fillColor", "home.fillColor" -> {
                    val col = p.optString("color", "#FFFF00")
                    val argb = SpreadsheetColorPalette.parseHexOrNull(col) ?: 0xFFFFFF00.toInt()
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    "$targetExpr.Interior.Color = RGB($r, $g, $b)"
                }
                "format.fontColor", "home.fontColor" -> {
                    val col = p.optString("color", "#000000")
                    val argb = SpreadsheetColorPalette.parseHexOrNull(col) ?: 0xFF000000.toInt()
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    "$targetExpr.Font.Color = RGB($r, $g, $b)"
                }
                "format.borders", "home.borders" -> "$targetExpr.Borders.LineStyle = xlContinuous"
                "format.mergeAndCenter", "format.mergeCells", "home.mergeCenter", "home.mergeCells" -> "$targetExpr.Merge"
                "format.unmergeCells", "home.unmergeCells" -> "$targetExpr.UnMerge"
                "format.numberFormat", "home.numberFormat" -> {
                    val fmt = p.optString("format", p.optString("code", "#,##0")).replace("\"", "\"\"")
                    "$targetExpr.NumberFormatLocal = \"$fmt\""
                }
                "format.alignCenter", "home.alignCenter" -> "$targetExpr.HorizontalAlignment = xlCenter"
                "format.alignRight", "home.alignRight" -> "$targetExpr.HorizontalAlignment = xlRight"
                "format.alignLeft", "home.alignLeft" -> "$targetExpr.HorizontalAlignment = xlLeft"
                "home.autoFitColumnWidth", "home.autofitColumnWidth" -> {
                    val cols = p.optString("cols", explicitRange).trim()
                    if (cols.isNotEmpty()) "Columns(\"$cols\").AutoFit" else "Columns(\"A:Z\").AutoFit"
                }
                "edit.clearContents", "home.clearContents" -> "$targetExpr.ClearContents"
                "edit.clearAll", "home.clearAll" -> "$targetExpr.Clear"
                "edit.clearFormats", "home.clearFormats" -> "$targetExpr.ClearFormats"
                "data.sortAsc", "data.sortAscending" -> "$targetExpr.Sort Order1:=xlAscending"
                "data.sortDesc", "data.sortDescending" -> "$targetExpr.Sort Order1:=xlDescending"
                "home.insertRows" -> "$targetExpr.EntireRow.Insert"
                "home.deleteRows" -> "$targetExpr.EntireRow.Delete"
                "home.insertColumns" -> "$targetExpr.EntireColumn.Insert"
                "home.deleteColumns" -> "$targetExpr.EntireColumn.Delete"
                "home.insertSheet" -> "Worksheets.Add"
                "sheet.rename", "home.renameSheet" -> {
                    val nm = p.optString("name", "Sheet1").replace("\"", "\"\"")
                    "ActiveSheet.Name = \"$nm\""
                }
                else -> "Application.Run \"${step.command}\""
            }
            sb.appendLine("    $vbaLine")
        }
        sb.append("End Sub")
        return sb.toString()
    }

    /**
     * [EN] Expands `For ... Next` loops using 64-bit bounds, enforces nesting and iteration limits, and strips comments/`Sub`/`End Sub` into flat statements for deterministic full-run and 1-step debugging.
     * [JA] `For ... Next` ループを64bit整数境界・ネスト深度上限・反復回数上限付きで安全に展開し、コメントや `Sub`/`End Sub` を除去した実行可能ステートメント列へ変換します。
     */
    fun expandVbaStatements(vbaCode: String): List<String> =
        expandVbaStatementsInternal(vbaCode, nestingDepth = 0)

    private fun expandVbaStatementsInternal(vbaCode: String, nestingDepth: Int): List<String> {
        if (nestingDepth >= MAX_VBA_NESTING_DEPTH) return emptyList()
        val rawLines = vbaCode.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("'") && !it.startsWith("Rem ", ignoreCase = true) }
            .filterNot {
                it.startsWith("Sub ", ignoreCase = true) ||
                    it.equals("End Sub", ignoreCase = true) ||
                    it.startsWith("Option ", ignoreCase = true)
            }

        val expanded = mutableListOf<String>()
        var i = 0
        while (i < rawLines.size) {
            val line = rawLines[i]
            val forMatch = Regex("""^For\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(-?\d+)\s+To\s+(-?\d+)(?:\s+Step\s+(-?\d+))?$""", RegexOption.IGNORE_CASE)
                .find(line)
            if (forMatch != null) {
                val varName = forMatch.groupValues[1]
                // [EN] Use Long arithmetic so extreme bounds (e.g. Int.MAX_VALUE or Step -2147483648) never overflow or throw,
                //      and cap iteration count (rather than numeric endpoint span) so `For r = 0 To 1000 Step 10` runs all 101 steps.
                // [JA] Long 演算でオーバーフローを防ぎつつ、数値幅ではなく反復回数（最大201回）で制限することで `Step 10` 等も正確に展開します。
                val startVal = (forMatch.groupValues[2].toLongOrNull() ?: 1L).coerceIn(-1_000_000_000_000L, 1_000_000_000_000L)
                val endVal = (forMatch.groupValues[3].toLongOrNull() ?: startVal).coerceIn(-1_000_000_000_000L, 1_000_000_000_000L)
                val rawStep = if (forMatch.groupValues[4].isNotEmpty()) {
                    forMatch.groupValues[4].toLongOrNull() ?: 0L
                } else {
                    if (endVal >= startVal) 1L else -1L
                }
                val stepVal = rawStep.coerceIn(-1_000_000_000_000L, 1_000_000_000_000L)
                // Find matching Next
                var depth = 1
                var j = i + 1
                val bodyLines = mutableListOf<String>()
                while (j < rawLines.size && depth > 0) {
                    val inner = rawLines[j]
                    if (inner.startsWith("For ", ignoreCase = true)) depth++
                    if (inner.startsWith("Next", ignoreCase = true)) {
                        depth--
                        if (depth == 0) break
                    }
                    bodyLines.add(inner)
                    j++
                }
                val iterValues = mutableListOf<Long>()
                if (stepVal > 0L) {
                    var curVal = startVal
                    while (curVal <= endVal && iterValues.size < MAX_VBA_LOOP_ITERATIONS) {
                        iterValues.add(curVal)
                        if (curVal > Long.MAX_VALUE - stepVal) break
                        curVal += stepVal
                    }
                } else if (stepVal < 0L) {
                    var curVal = startVal
                    while (curVal >= endVal && iterValues.size < MAX_VBA_LOOP_ITERATIONS) {
                        iterValues.add(curVal)
                        if (curVal < Long.MIN_VALUE - stepVal) break
                        curVal += stepVal
                    }
                }
                for (iterVal in iterValues) {
                    if (expanded.size >= MAX_EXPANDED_VBA_STATEMENTS) break
                    expanded.add("$varName = $iterVal")
                    val subBody = bodyLines.joinToString("\n") { bLine ->
                        substituteVbaLoopVariable(bLine, varName, iterVal)
                    }
                    val subExpanded = expandVbaStatementsInternal(subBody, nestingDepth = nestingDepth + 1)
                    val remainingBudget = (MAX_EXPANDED_VBA_STATEMENTS - expanded.size).coerceAtLeast(0)
                    expanded.addAll(subExpanded.take(remainingBudget))
                }
                i = j + 1
                continue
            }
            if (expanded.size >= MAX_EXPANDED_VBA_STATEMENTS) break
            expanded.add(line)
            i++
        }
        return expanded
    }

    private const val MAX_VBA_NESTING_DEPTH = 32
    private const val MAX_VBA_LOOP_ITERATIONS = 201
    private const val MAX_EXPANDED_VBA_STATEMENTS = 5_000

    /**
     * [EN] Substitutes a `For` loop counter variable in a VBA line, folding `"prefix" & i & "suffix"`
     *      into `"prefix1suffix"` while preserving empty string literals (`""`) and text inside quotes.
     * [JA] VBAのループ変数を置換します。空文字リテラル `""` や文字列内部を壊さずに `&` 連結を展開します。
     */
    private fun substituteVbaLoopVariable(line: String, varName: String, iterVal: Long): String {
        val escapedVar = Regex.escape(varName)
        val iterStr = iterVal.toString()
        var work = line
            .replace(Regex(""""\s*&\s*$escapedVar\s*&\s*""""), iterStr)
            .replace(Regex(""""\s*&\s*$escapedVar\b"""), "$iterStr\"")
            .replace(Regex("""\b$escapedVar\s*&\s*""""), "\"$iterStr")
        // Replace standalone variable occurrences only outside double-quoted string literals
        val sb = StringBuilder(work.length + 8)
        var inQuotes = false
        var idx = 0
        val standaloneRegex = Regex("""\b$escapedVar\b""")
        while (idx < work.length) {
            val quotePos = work.indexOf('"', idx)
            if (quotePos < 0) {
                val tail = work.substring(idx)
                sb.append(if (inQuotes) tail else standaloneRegex.replace(tail, iterStr))
                break
            }
            val segment = work.substring(idx, quotePos)
            sb.append(if (inQuotes) segment else standaloneRegex.replace(segment, iterStr))
            sb.append('"')
            inQuotes = !inQuotes
            idx = quotePos + 1
        }
        return sb.toString()
    }

    /**
     * [EN] Executes one expanded VBA statement (`Range`, `Cells`, `Columns`, `MsgBox`, or allowlisted `Application.Run`) against the active sheet and returns an execution log line.
     * [JA] 展開済みのVBA 1ステートメント（`Range`・`Cells`・`Columns`・`MsgBox`・許可済み `Application.Run`）を実行し、コンソール用の実行ログ行を返します。
     */
    fun executeSingleVbaStatement(
        stmt: String,
        variables: MutableMap<String, String>,
        vp: NativeViewportSnapshot,
        executeCmd: (String, String) -> Unit,
        commitCell: (Int, Int, String) -> Unit,
        onMsgBox: (String) -> Unit
    ): String {
        val clean = stmt.trim()
        // 1. Variable assignment: x = 5 or Dim x
        if (clean.startsWith("Dim ", ignoreCase = true)) {
            val varName = clean.substring(4).substringBefore(" ").trim()
            variables[varName] = "0"
            return "変数宣言: $varName"
        }
        val simpleAssign = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)$""").find(clean)
        if (simpleAssign != null && !clean.contains(".") && !clean.startsWith("MsgBox", ignoreCase = true)) {
            val k = simpleAssign.groupValues[1]
            val v = simpleAssign.groupValues[2].trim().removeSurrounding("\"")
            variables[k] = v
            return "$k = $v"
        }

        // 2. MsgBox "..."
        if (clean.startsWith("MsgBox", ignoreCase = true)) {
            val msg = clean.substring(6).trim().removePrefix("(").removeSuffix(")").trim().removeSurrounding("\"")
            onMsgBox(msg)
            return "MsgBox: $msg"
        }

        // 3. Worksheets.Add or ActiveSheet.Name = "..."
        if (clean.equals("Worksheets.Add", ignoreCase = true) || clean.equals("Sheets.Add", ignoreCase = true)) {
            executeCmd("home.insertSheet", "{}")
            return "新規ワークシートを追加"
        }
        val sheetNameMatch = Regex("""^ActiveSheet\.Name\s*=\s*"([^"]+)"$""", RegexOption.IGNORE_CASE).find(clean)
        if (sheetNameMatch != null) {
            val newName = sheetNameMatch.groupValues[1]
            executeCmd("sheet.rename", "{\"sheet\":${vp.sheetIndex},\"name\":${JSONObject.quote(newName)}}")
            return "シート名を「$newName」に変更"
        }

        // 4. Columns("A:D").AutoFit / Rows("1:10").AutoFit
        if (clean.endsWith(".AutoFit", ignoreCase = true)) {
            if (clean.startsWith("Rows", ignoreCase = true)) {
                val rowRng = Regex("""Rows\(\s*"([^"]+)"\s*\)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)
                executeCmd("home.autoFitRowHeight", if (rowRng != null) "{\"rows\":${JSONObject.quote(rowRng)}}" else "{}")
                return "行の高さの自動調整 (AutoFit)"
            }
            val colRng = Regex("""Columns\(\s*"([^"]+)"\s*\)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)
            executeCmd("home.autoFitColumnWidth", if (colRng != null) "{\"cols\":${JSONObject.quote(colRng)}}" else "{}")
            return "列幅の自動調整 (AutoFit)"
        }

        // 5. Cells(r, c).Value / Formula / MultiplyBy = ...
        val cellsMatch = Regex("""^Cells\(\s*(\d+)\s*,\s*(\d+)\s*\)\.([A-Za-z]+)\s*=\s*(.+)$""", RegexOption.IGNORE_CASE).find(clean)
        if (cellsMatch != null) {
            val r = (cellsMatch.groupValues[1].toIntOrNull() ?: 1).coerceAtLeast(1) - 1
            val c = (cellsMatch.groupValues[2].toIntOrNull() ?: 1).coerceAtLeast(1) - 1
            val prop = cellsMatch.groupValues[3].lowercase()
            val rhsRaw = cellsMatch.groupValues[4].trim().removeSurrounding("\"")
            val addr = MasuSheetConfig.formatCellAddress(r, c)
            return when (prop) {
                "value", "formula", "formulalocal" -> {
                    commitCell(r, c, rhsRaw)
                    "Cells(${r + 1}, ${c + 1}) [$addr] ← $rhsRaw"
                }
                "multiplyby" -> {
                    val factor = rhsRaw.toDoubleOrNull() ?: 1.0
                    val existing = vp.cellAt(r, c)?.displayText?.replace(",", "")?.replace("¥", "")?.trim()?.toDoubleOrNull()
                    if (existing != null) {
                        val updatedVal = (existing * factor * 100.0).roundToInt() / 100.0
                        val formatted = if (updatedVal % 1.0 == 0.0) updatedVal.toLong().toString() else updatedVal.toString()
                        commitCell(r, c, formatted)
                        "$addr の数値を ×$factor ($formatted) に更新"
                    } else {
                        "$addr (数値なしのためスキップ)"
                    }
                }
                else -> {
                    commitCell(r, c, rhsRaw)
                    "$addr.$prop = $rhsRaw"
                }
            }
        }

        // 6. Range("...").<Property> = ... or Selection.<Property> = ...
        val rangePropMatch = Regex("""^(?:Range\(\s*"([^"]+)"\s*\)|Selection)\.([A-Za-z0-9_.]+)(?:\s*=\s*(.+))?$""", RegexOption.IGNORE_CASE).find(clean)
        if (rangePropMatch != null) {
            val targetRange = rangePropMatch.groupValues[1].ifBlank {
                val r0 = min(vp.selStartRow, vp.selEndRow)
                val c0 = min(vp.selStartCol, vp.selEndCol)
                val r1 = max(vp.selStartRow, vp.selEndRow)
                val c1 = max(vp.selStartCol, vp.selEndCol)
                if (r0 == r1 && c0 == c1) vp.activeAddress else "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c1)}"
            }
            val propChain = rangePropMatch.groupValues[2].lowercase()
            val rhs = rangePropMatch.groupValues[3].trim()

            // Select the range first so range-based commands apply to it
            val firstAddr = targetRange.substringBefore(":")
            executeCmd("selection.set", "{\"active\":\"$firstAddr\",\"range\":\"$targetRange\"}")

            return when {
                propChain == "select" || propChain == "activate" -> "範囲 $targetRange を選択"
                propChain == "value" || propChain == "formula" || propChain == "formulalocal" -> {
                    val unquoted = rhs.removeSurrounding("\"")
                    executeCmd("cell.set", "{\"cell\":\"$firstAddr\",\"input\":${JSONObject.quote(unquoted)}}")
                    "Range(\"$targetRange\") ← $unquoted"
                }
                propChain == "clearcontents" -> {
                    executeCmd("edit.clearContents", "{\"range\":\"$targetRange\"}")
                    "Range(\"$targetRange\") の内容をクリア"
                }
                propChain == "clear" -> {
                    executeCmd("edit.clearAll", "{\"range\":\"$targetRange\"}")
                    "Range(\"$targetRange\") をすべてクリア"
                }
                propChain == "clearformats" -> {
                    executeCmd("edit.clearFormats", "{\"range\":\"$targetRange\"}")
                    "Range(\"$targetRange\") の書式をクリア"
                }
                propChain == "merge" -> {
                    executeCmd("home.mergeCells", "{\"range\":\"$targetRange\"}")
                    "Range(\"$targetRange\") を結合"
                }
                propChain == "unmerge" -> {
                    executeCmd("home.unmergeCells", "{\"range\":\"$targetRange\"}")
                    "Range(\"$targetRange\") の結合を解除"
                }
                propChain == "entirerow.insert" -> {
                    executeCmd("home.insertRows", "{}")
                    "Range(\"$targetRange\") に行を挿入"
                }
                propChain == "entirerow.delete" -> {
                    executeCmd("home.deleteRows", "{}")
                    "Range(\"$targetRange\") の行を削除"
                }
                propChain == "entirecolumn.insert" -> {
                    executeCmd("home.insertColumns", "{}")
                    "Range(\"$targetRange\") に列を挿入"
                }
                propChain == "entirecolumn.delete" -> {
                    executeCmd("home.deleteColumns", "{}")
                    "Range(\"$targetRange\") の列を削除"
                }
                propChain == "horizontalalignment" -> {
                    val alignCmd = when {
                        rhs.contains("xlCenter", ignoreCase = true) -> "format.alignCenter"
                        rhs.contains("xlRight", ignoreCase = true) -> "format.alignRight"
                        else -> "format.alignLeft"
                    }
                    executeCmd(alignCmd, "{\"range\":\"$targetRange\"}")
                    "Range(\"$targetRange\").HorizontalAlignment = $rhs"
                }
                propChain == "font.bold" -> {
                    val on = !rhs.equals("False", ignoreCase = true)
                    executeCmd("format.bold", "{\"range\":\"$targetRange\",\"bold\":$on}")
                    "Range(\"$targetRange\").Font.Bold = $on"
                }
                propChain == "font.italic" -> {
                    val on = !rhs.equals("False", ignoreCase = true)
                    executeCmd("format.italic", "{\"range\":\"$targetRange\",\"italic\":$on}")
                    "Range(\"$targetRange\").Font.Italic = $on"
                }
                propChain == "font.strikethrough" -> {
                    val on = !rhs.equals("False", ignoreCase = true)
                    executeCmd("format.strikethrough", "{\"range\":\"$targetRange\",\"strikethrough\":$on}")
                    "Range(\"$targetRange\").Font.Strikethrough = $on"
                }
                propChain.startsWith("font.underline") -> {
                    val on = !(rhs.equals("False", ignoreCase = true) || rhs.contains("None", ignoreCase = true))
                    executeCmd("format.underline", "{\"range\":\"$targetRange\",\"underline\":$on}")
                    "Range(\"$targetRange\") の下線を ${if (on) "ON" else "OFF"} に設定"
                }
                propChain == "interior.color" || propChain == "font.color" -> {
                    val rgbMatch = Regex("""RGB\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""", RegexOption.IGNORE_CASE).find(rhs)
                    val hexColor = if (rgbMatch != null) {
                        val r = rgbMatch.groupValues[1].toIntOrNull() ?: 0
                        val g = rgbMatch.groupValues[2].toIntOrNull() ?: 0
                        val b = rgbMatch.groupValues[3].toIntOrNull() ?: 0
                        SpreadsheetColorPalette.toHexString(SpreadsheetColorPalette.fromRgb(r, g, b))
                    } else {
                        rhs.removeSurrounding("\"").ifBlank { "#4472C4" }
                    }
                    val cmdId = if (propChain == "interior.color") "format.fillColor" else "format.fontColor"
                    executeCmd(cmdId, "{\"range\":\"$targetRange\",\"color\":\"$hexColor\"}")
                    "Range(\"$targetRange\").$propChain = $hexColor"
                }
                propChain.startsWith("borders") -> {
                    executeCmd("format.borders", "{\"range\":\"$targetRange\",\"style\":\"all\"}")
                    "Range(\"$targetRange\") に罫線を設定"
                }
                propChain.startsWith("numberformat") -> {
                    val fmt = rhs.removeSurrounding("\"")
                    executeCmd("format.numberFormat", "{\"range\":\"$targetRange\",\"format\":${JSONObject.quote(fmt)}}")
                    "Range(\"$targetRange\") の表示形式を $fmt に設定"
                }
                propChain.startsWith("sort") -> {
                    val desc = clean.contains("xlDescending", ignoreCase = true)
                    executeCmd(if (desc) "data.sortDescending" else "data.sortAscending", "{\"range\":\"$targetRange\"}")
                    "Range(\"$targetRange\") を並べ替え"
                }
                else -> "実行: $clean"
            }
        }

        // 7. Application.Run "commandId" (restricted to safe worksheet commands; blocks file.*/app.*/jni.* & sheet deletion)
        val appRunMatch = Regex("""^Application\.Run\s+"([^"]+)"$""", RegexOption.IGNORE_CASE).find(clean)
        if (appRunMatch != null) {
            val cmdId = appRunMatch.groupValues[1].trim()
            if (!SpreadsheetAiAgent.isAllowedAiCommand(cmdId)) {
                return "ブロックされたコマンド: $cmdId"
            }
            executeCmd(cmdId, "{}")
            return "コマンド実行: $cmdId"
        }

        return "ステートメント完了: $clean"
    }

    /**
     * [EN] Synthesizes a ready-to-run VBA `Sub ... End Sub` macro tailored to the user's Japanese/English prompt and current cell selection.
     * [JA] 日本語または英語の自然言語指示と現在の選択範囲から、そのまま実行・編集できるVBAサブプロシージャを生成します。
     */
    fun generateVbaFromNaturalLanguage(prompt: String, vp: NativeViewportSnapshot): String {
        val p = prompt.trim()
        val pLower = p.lowercase()
        val explicitRangeMatch = Regex("""\b([A-Z]{1,2}\d+):([A-Z]{1,2}\d+)\b""", RegexOption.IGNORE_CASE).find(p)
        val explicitBounds = explicitRangeMatch?.let { m ->
            val p0 = MasuSheetConfig.parseCellAddressStrict(m.groupValues[1])
            val p1 = MasuSheetConfig.parseCellAddressStrict(m.groupValues[2])
            if (p0 != null && p1 != null) {
                intArrayOf(
                    min(p0.first, p1.first) + 1,
                    min(p0.second, p1.second) + 1,
                    max(p0.first, p1.first) + 1,
                    max(p0.second, p1.second) + 1
                )
            } else null
        }

        val selR0 = min(vp.selStartRow, vp.selEndRow) + 1
        val selC0 = min(vp.selStartCol, vp.selEndCol) + 1
        val selR1 = max(vp.selStartRow, vp.selEndRow) + 1
        val selC1 = max(vp.selStartCol, vp.selEndCol) + 1
        val isSingleCell = selR0 == selR1 && selC0 == selC1
        val nonEmpty = vp.cells.values.filter { it.displayText.isNotBlank() }
        val tblR0 = explicitBounds?.get(0) ?: if (isSingleCell) ((nonEmpty.minOfOrNull { it.row } ?: 0) + 1) else selR0
        val tblR1 = explicitBounds?.let { max(it[2], if (it[0] == it[2]) ((nonEmpty.maxOfOrNull { c -> c.row } ?: 4) + 1).coerceAtLeast(it[0] + 1) else it[2]) }
            ?: if (isSingleCell) ((nonEmpty.maxOfOrNull { it.row } ?: 4) + 1).coerceAtLeast(tblR0 + 1) else selR1
        val tblC0 = explicitBounds?.get(1) ?: if (isSingleCell) ((nonEmpty.minOfOrNull { it.col } ?: 0) + 1) else selC0
        val tblC1 = explicitBounds?.get(3) ?: if (isSingleCell) ((nonEmpty.maxOfOrNull { it.col } ?: 3) + 1).coerceAtLeast(tblC0 + 1) else selC1

        val selRange = "${MasuSheetConfig.formatCellAddress(tblR0 - 1, tblC0 - 1)}:${MasuSheetConfig.formatCellAddress(tblR1 - 1, tblC1 - 1)}"
        val dataR0 = if (isSingleCell) (tblR0 + 1).coerceAtMost(tblR1) else tblR0
        val dataR1 = tblR1
        val dataC0 = if (isSingleCell) (tblC0 + 1).coerceAtMost(tblC1) else tblC0
        val dataC1 = tblC1

        val wantsTax = p.contains("税込") || p.contains("消費税") || p.contains("1.1") || Regex("""\btax\b|\bvat\b""").containsMatchIn(pLower)
        val wantsSum = p.contains("合計") || p.contains("集計") || Regex("""\bsum\b|\btotal""").containsMatchIn(pLower)

        val hdrRow = if (isSingleCell) tblR0 else max(1, tblR0 - 1)
        val sumColIdx1Based = tblC1 + 1
        val sumColLet = MasuSheetConfig.formatColumnLabel(sumColIdx1Based - 1)
        val firstColLet = MasuSheetConfig.formatColumnLabel(tblC0 - 1)
        val firstColSample = vp.cellAt(dataR0 - 1, tblC0 - 1)?.displayText
            ?.replace(",", "")?.replace("¥", "")?.replace("￥", "")?.replace("$", "")?.replace("%", "")?.trim()
        val firstColIsNumeric = !isSingleCell && firstColSample != null && firstColSample.toDoubleOrNull() != null
        val firstValCol1Based = if (firstColIsNumeric || tblC1 <= tblC0) tblC0 else (tblC0 + 1)
        val startValColLet = MasuSheetConfig.formatColumnLabel((firstValCol1Based - 1).coerceAtLeast(0))
        val endValColLet = MasuSheetConfig.formatColumnLabel(tblC1 - 1)
        val totalLabelColLet = if (firstColIsNumeric && tblC0 > 1) MasuSheetConfig.formatColumnLabel(tblC0 - 2) else firstColLet
        val totalRow1Based = dataR1 + 1

        return when {
            wantsTax && wantsSum -> {
                val colSumLines = (firstValCol1Based..sumColIdx1Based).joinToString("\n") { c1 ->
                    val letC = MasuSheetConfig.formatColumnLabel(c1 - 1)
                    "    Range(\"$letC$totalRow1Based\").Formula = \"=SUM(${letC}$dataR0:${letC}$dataR1)\""
                }
                val labelLine = if (firstColIsNumeric && tblC0 == 1) "" else "    Range(\"$totalLabelColLet$totalRow1Based\").Value = \"全体合計\"\n"
                """
                Sub ConvertTaxAndAddTotals()
                    ' 数値範囲を税込(1.1倍)に変換し、合計列($sumColLet)と合計行(${totalRow1Based}行目)を追加します
                    For r = $dataR0 To $dataR1
                        For c = $dataC0 To $dataC1
                            Cells(r, c).MultiplyBy = 1.1
                        Next c
                    Next r
                    Range("$sumColLet$hdrRow").Value = "合計"
                    Range("$sumColLet$hdrRow").Font.Bold = True
                    For r = $dataR0 To $dataR1
                        Cells(r, $sumColIdx1Based).Formula = "=SUM($startValColLet" & r & ":$endValColLet" & r & ")"
                    Next r
                $labelLine$colSumLines
                    Range("$totalLabelColLet$totalRow1Based:$sumColLet$totalRow1Based").Font.Bold = True
                    Range("$totalLabelColLet$totalRow1Based:$sumColLet$totalRow1Based").Interior.Color = RGB(232, 245, 233)
                    MsgBox "税込(10%)変換と合計行・合計列の追加が完了しました！"
                End Sub
                """.trimIndent()
            }

            wantsTax -> """
                Sub ConvertToTaxIncluded()
                    ' 対象範囲 ($selRange) の数値を税込（1.1倍）に変換します
                    For r = $dataR0 To $dataR1
                        For c = $dataC0 To $dataC1
                            Cells(r, c).MultiplyBy = 1.1
                        Next c
                    Next r
                    MsgBox "税込(10%)への変換が完了しました！"
                End Sub
            """.trimIndent()

            wantsSum -> {
                val colSumLines = (firstValCol1Based..sumColIdx1Based).joinToString("\n") { c1 ->
                    val letC = MasuSheetConfig.formatColumnLabel(c1 - 1)
                    "    Range(\"$letC$totalRow1Based\").Formula = \"=SUM(${letC}$dataR0:${letC}$dataR1)\""
                }
                val labelLine = if (firstColIsNumeric && tblC0 == 1) "" else "    Range(\"$totalLabelColLet$totalRow1Based\").Value = \"合計\"\n"
                """
                Sub InsertRowAndColumnTotals()
                    ' 各行と各列の合計（SUM関数）を自動挿入します
                    Range("$sumColLet$hdrRow").Value = "合計"
                    Range("$sumColLet$hdrRow").Font.Bold = True
                    For r = $dataR0 To $dataR1
                        Cells(r, $sumColIdx1Based).Formula = "=SUM($startValColLet" & r & ":$endValColLet" & r & ")"
                    Next r
                $labelLine$colSumLines
                    Range("$totalLabelColLet$totalRow1Based:$sumColLet$totalRow1Based").Font.Bold = True
                    Range("$totalLabelColLet$totalRow1Based:$sumColLet$totalRow1Based").Interior.Color = RGB(232, 245, 233)
                    MsgBox "合計行・合計列を追加しました！"
                End Sub
                """.trimIndent()
            }

            p.contains("連番") || p.contains("番号") || pLower.contains("sequence") -> """
                Sub FillSequenceNumbers()
                    ' ${firstColLet}列に連番を自動入力します
                    Range("${firstColLet}1").Value = "No."
                    Range("${firstColLet}1").Font.Bold = True
                    For r = 2 To ${max(11, tblR1)}
                        Cells(r, $tblC0).Value = r - 1
                    Next r
                    MsgBox "連番の入力が完了しました！"
                End Sub
            """.trimIndent()

            else -> {
                val hdrFillRgb = when {
                    p.contains("緑") || pLower.contains("green") -> "RGB(46, 125, 50)"
                    p.contains("赤") || pLower.contains("red") -> "RGB(198, 40, 40)"
                    else -> "RGB(68, 114, 196)"
                }
                val headerRange = explicitRangeMatch?.value?.uppercase() ?: "${firstColLet}$tblR0:${endValColLet}$tblR0"
                """
                Sub AutoFormatTableMacro()
                    ' AI生成マクロ: 「$p」
                    Range("$headerRange").Interior.Color = $hdrFillRgb
                    Range("$headerRange").Font.Color = RGB(255, 255, 255)
                    Range("$headerRange").Font.Bold = True
                    Range("$selRange").Borders.LineStyle = xlContinuous
                    Columns("$firstColLet:$endValColLet").AutoFit
                    MsgBox "マクロ「$p」の実行が完了しました！"
                End Sub
                """.trimIndent()
            }
        }
    }

    fun explainVbaInJapanese(vbaCode: String): String {
        val lines = vbaCode.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val explanations = mutableListOf<String>()
        for (line in lines) {
            when {
                line.startsWith("Sub ", ignoreCase = true) ->
                    explanations.add("・マクロ定義の開始: ${line.removePrefix("Sub ").trim()}")
                line.contains(".Interior.Color", ignoreCase = true) ->
                    explanations.add("・セル範囲の背景色（塗りつぶし色）を RGB 指定で変更します。")
                line.contains(".Font.Bold", ignoreCase = true) ->
                    explanations.add("・フォントを太字（Bold）に設定します。")
                line.contains(".Font.Color", ignoreCase = true) ->
                    explanations.add("・文字色を RGB 指定で変更します。")
                line.contains(".Formula", ignoreCase = true) ->
                    explanations.add("・セルに数式（=SUM など）を入力します。")
                line.contains(".Value", ignoreCase = true) ->
                    explanations.add("・セルに値またはラベルを入力します。")
                line.startsWith("For ", ignoreCase = true) ->
                    explanations.add("・繰り返しループ処理（${line}）を開始します。")
                line.contains(".Borders", ignoreCase = true) ->
                    explanations.add("・セル範囲に格子罫線を引きます。")
                line.contains(".AutoFit", ignoreCase = true) ->
                    explanations.add("・文字数に合わせて列幅を自動調整します。")
                line.startsWith("MsgBox", ignoreCase = true) ->
                    explanations.add("・完了メッセージダイアログを表示します。")
            }
        }
        return if (explanations.isEmpty()) {
            "この VBA マクロは、選択範囲のセル値や書式を順番に自動処理します。"
        } else {
            explanations.distinct().joinToString("\n")
        }
    }
}
