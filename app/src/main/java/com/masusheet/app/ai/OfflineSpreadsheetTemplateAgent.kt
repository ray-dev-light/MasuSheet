package com.masusheet.app.ai

import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.NativeViewportSnapshot
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Built-in Offline Spreadsheet Smart Script & Domain Table Synthesizer
 * Provides deterministic, network-free table generation, summary formulas, chart creation, and safe non-destructive analysis when running in Built-in Script mode.
 *
 * ---
 * [JA / 日本語解説]
 * ネットワーク通信やAPIキーなしで動作する内蔵スマートスクリプト（業務テンプレート合成・集計数式・グラフ作成・既存データの非破壊要約）を担うモジュールです。
 * ============================================================================
 */

private fun resolveRequestedChartType(prompt: String): String {
    val p = prompt.lowercase(Locale.ROOT)
    return when {
        p.contains("円") || p.contains("pie") || p.contains("内訳") || p.contains("割合") -> "pie"
        p.contains("折れ線") || p.contains("折線") || p.contains("line") || p.contains("推移") || p.contains("トレンド") -> "line"
        p.contains("横棒") || p.contains("bar") -> "bar"
        else -> "column"
    }
}

private fun isChartPrompt(prompt: String): Boolean {
    val p = prompt.lowercase(Locale.ROOT)
    return p.contains("グラフ") || p.contains("チャート") || p.contains("chart") || p.contains("graph") || p.contains("plot") ||
        p.contains("棒グラフ") || p.contains("折れ線") || p.contains("円グラフ")
}

/**
 * [EN] Offline rule-based spreadsheet agent that inspects the active sheet and user prompt to generate tables, formulas, formatting, or charts without network calls.
 * [JA] ネットワーク通信なしでシート構造と指示文を解析し、表作成・数式挿入・書式整形・グラフ生成コマンドを組み立てる内蔵エージェントです。
 */
internal fun runGeneralPurposeLocalAgent(
    prompt: String,
    editMode: String,
    clarifyingAnswers: Map<String, String>,
    vp: NativeViewportSnapshot
): AiTurnExecutionResult {
    val cmds = mutableListOf<Pair<String, String>>()
    fun setCell(addr: String, input: String) {
        cmds.add("cell.set" to "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(input)}}")
    }
    fun styleRange(
        range: String,
        bgHex: String? = null,
        fgHex: String? = null,
        bold: Boolean? = null,
        numFmt: String? = null,
        borders: String? = null,
        hAlign: String? = null
    ) {
        if (bgHex != null) cmds.add("format.fillColor" to "{\"range\":\"$range\",\"color\":\"$bgHex\"}")
        if (fgHex != null) cmds.add("format.fontColor" to "{\"range\":\"$range\",\"color\":\"$fgHex\"}")
        if (bold != null) cmds.add("format.bold" to "{\"range\":\"$range\",\"bold\":$bold}")
        if (numFmt != null) cmds.add("format.numberFormat" to "{\"range\":\"$range\",\"format\":${JSONObject.quote(numFmt)}}")
        if (borders != null) cmds.add("format.borders" to "{\"range\":\"$range\",\"style\":\"$borders\"}")
        when (hAlign) {
            "center" -> cmds.add("format.alignCenter" to "{\"range\":\"$range\"}")
            "right" -> cmds.add("format.alignRight" to "{\"range\":\"$range\"}")
            "left" -> cmds.add("format.alignLeft" to "{\"range\":\"$range\"}")
        }
    }

    // Detect existing bounding box of non-empty cells on the active sheet
    val nonEmpty = vp.cells.values.filter { it.displayText.isNotBlank() }
    val minR = nonEmpty.minOfOrNull { it.row } ?: 0
    val maxR = nonEmpty.maxOfOrNull { it.row } ?: 0
    val minC = nonEmpty.minOfOrNull { it.col } ?: 0
    val maxC = nonEmpty.maxOfOrNull { it.col } ?: 0

    // Chat-only mode: analyze current sheet and answer the user's question with real metrics
    if (editMode == "chat") {
        val nums = nonEmpty.mapNotNull { cell ->
            cell.displayText.replace(",", "").replace("¥", "").replace("$", "").replace("%", "").trim().toDoubleOrNull()
        }
        val sum = nums.sum()
        val avg = if (nums.isNotEmpty()) sum / nums.size else 0.0
        val maxVal = nums.maxOrNull() ?: 0.0
        val minVal = nums.minOrNull() ?: 0.0
        return AiTurnExecutionResult(
            replyText = "現在のシート「${vp.sheetName}」を分析しました（チャットのみモード）：\n" +
                "・入力済みセル数: ${nonEmpty.size} セル（数値データ: ${nums.size} 件）\n" +
                "・数値合計: ${String.format("%,.2f", sum)} / 平均: ${String.format("%,.2f", avg)}\n" +
                "・最大値: ${String.format("%,.2f", maxVal)} / 最小値: ${String.format("%,.2f", minVal)}\n" +
                "シートを直接編集・グラフ作成する場合は、モードを「編集を許可」または「プラン」に切り替えて指示してください。",
            summaryBullets = listOf(
                "対象範囲: ${MasuSheetConfig.formatCellAddress(minR, minC)}:${MasuSheetConfig.formatCellAddress(maxR, maxC)}",
                "合計 = ${String.format("%,.0f", sum)}、平均 = ${String.format("%,.1f", avg)}"
            )
        )
    }

    // CASE 0: User asked to chart/graph the current sheet ("グラフ化して", "円グラフにして", "折れ線グラフを作って", etc.)
    val wantsChart = isChartPrompt(prompt)
    val wantsNewSheetTable = prompt.contains("表を作成") || prompt.contains("表を作って") || prompt.contains("シートを作成") ||
        prompt.contains("新規") || nonEmpty.size < 2

    if (wantsChart && !wantsNewSheetTable) {
        val chartType = resolveRequestedChartType(prompt)
        val chartTypeLabel = when (chartType) {
            "pie" -> "円グラフ"
            "line" -> "折れ線グラフ"
            "bar" -> "横棒グラフ"
            else -> "縦棒グラフ"
        }
        val hasMultiSelection = (vp.selEndRow > vp.selStartRow) || (vp.selEndCol > vp.selStartCol)
        // Find the best contiguous data table on the sheet if only 1 cell is selected
        val (cStartR, cStartC, cEndR, cEndC) = if (hasMultiSelection) {
            listOf(vp.selStartRow, vp.selStartCol, vp.selEndRow, vp.selEndCol)
        } else {
            // Find the first row that has at least 2 non-empty columns (table header row)
            var bestStartR = minR
            for (r in minR..maxR) {
                val rowCount = (minC..maxC).count { c -> vp.cellAt(r, c)?.displayText?.isNotBlank() == true }
                if (rowCount >= 2 && r >= minR) {
                    bestStartR = r
                    break
                }
            }
            // Exclude bottom total row ("合計" / "Total") if present so the chart bars aren't skewed by the sum row
            var bestEndR = maxR
            if (bestEndR > bestStartR + 1) {
                val lastRowFirstCell = vp.cellAt(bestEndR, minC)?.displayText.orEmpty()
                if (lastRowFirstCell.contains("合計") || lastRowFirstCell.contains("Total", ignoreCase = true)) {
                    bestEndR -= 1
                }
            }
            listOf(bestStartR, minC, bestEndR.coerceAtLeast(bestStartR + 1), maxC.coerceAtLeast(minC + 1))
        }
        val rangeLabel = "${MasuSheetConfig.formatCellAddress(cStartR, cStartC)}:${MasuSheetConfig.formatCellAddress(cEndR, cEndC)}"
        val chartTitle = vp.cellAt(cStartR, cStartC)?.displayText?.takeIf { it.isNotBlank() }?.let { "${vp.sheetName} - $chartTypeLabel" }
            ?: "${vp.sheetName} $chartTypeLabel"
        val chartParams = JSONObject().apply {
            put("type", chartType)
            put("startRow", cStartR)
            put("startCol", cStartC)
            put("endRow", cEndR)
            put("endCol", cEndC)
            put("title", chartTitle)
        }.toString()
        cmds.add("ui.insertChart" to chartParams)

        return AiTurnExecutionResult(
            replyText = "現在のシート「${vp.sheetName}」のデータ範囲 ($rangeLabel) から「$chartTypeLabel」を作成し、シート上に配置しました。",
            summaryBullets = listOf(
                "グラフ種類: $chartTypeLabel ($chartType)",
                "データ範囲: $rangeLabel （見出しと数値系列を自動認識）",
                "グラフ内のタイトルや項目ラベルはダブルクリックで直接文字編集できます"
            ),
            commands = cmds
        )
    }

    // CASE A: Modifying / enhancing the existing table on the sheet (when the user asks to add totals, averages, ratios, tax, rank, format, sort, etc. without asking to build a brand-new sheet)
    val pLower = prompt.lowercase(Locale.ROOT)
    val explicitlyWantsNewTable = clarifyingAnswers.isNotEmpty() ||
        prompt.contains("作成") || prompt.contains("作って") || prompt.contains("新規") ||
        prompt.contains("テンプレート") || prompt.contains("フォーマット") ||
        pLower.contains("create") || pLower.contains("build") || pLower.contains("generate") ||
        pLower.contains("make ") || pLower.contains("new ") || pLower.contains("template")
    val isModifyExistingRequest = nonEmpty.size >= 4 &&
        !explicitlyWantsNewTable &&
        (prompt.contains("合計") || prompt.contains("平均") || prompt.contains("構成比") ||
            prompt.contains("割合") || prompt.contains("順位") || prompt.contains("ランク") ||
            prompt.contains("税込") || prompt.contains("消費税") || prompt.contains("整形") ||
            prompt.contains("罫線") || prompt.contains("色") || prompt.contains("条件付き書式") ||
            prompt.contains("並び替え") || prompt.contains("ソート") || prompt.contains("追加") ||
            pLower.contains("total") || pLower.contains("sum") || pLower.contains("average") ||
            pLower.contains("ratio") || pLower.contains("percent") || pLower.contains("rank") ||
            pLower.contains("format") || pLower.contains("style") || pLower.contains("border") ||
            pLower.contains("sort"))

    if (isModifyExistingRequest) {
        var nextFreeCol = maxC + 1
        var sumColLetCreated: String? = null
        val startColLetter = MasuSheetConfig.formatColumnLabel(minC + 1)
        val endColLetter = MasuSheetConfig.formatColumnLabel(maxC)
        val bullets = mutableListOf<String>()

        if (prompt.contains("合計") || prompt.contains("平均") || prompt.contains("集計") || prompt.contains("追加") ||
            pLower.contains("total") || pLower.contains("sum") || pLower.contains("average")
        ) {
            val sumColIdx = nextFreeCol
            val avgColIdx = nextFreeCol + 1
            nextFreeCol += 2
            val newColLetter = MasuSheetConfig.formatColumnLabel(sumColIdx)
            sumColLetCreated = newColLetter
            setCell("${newColLetter}${minR + 1}", "合計")
            val avgColLetter = MasuSheetConfig.formatColumnLabel(avgColIdx)
            setCell("${avgColLetter}${minR + 1}", "平均")
            for (r in (minR + 1)..maxR) {
                val rowNum = r + 1
                setCell("${newColLetter}$rowNum", "=SUM(${startColLetter}$rowNum:${endColLetter}$rowNum)")
                setCell("${avgColLetter}$rowNum", "=AVERAGE(${startColLetter}$rowNum:${endColLetter}$rowNum)")
            }
            val totalRowNum = maxR + 2
            setCell("${MasuSheetConfig.formatColumnLabel(minC)}$totalRowNum", "全体合計")
            for (c in (minC + 1)..avgColIdx) {
                val colLet = MasuSheetConfig.formatColumnLabel(c)
                setCell("$colLet$totalRowNum", "=SUM($colLet${minR + 2}:$colLet${maxR + 1})")
            }
            val fullEndCol = MasuSheetConfig.formatColumnLabel(avgColIdx)
            styleRange(
                "${MasuSheetConfig.formatColumnLabel(minC)}${minR + 1}:${fullEndCol}${minR + 1}",
                bgHex = "#1F4E79",
                fgHex = "#FFFFFF",
                bold = true,
                hAlign = "center"
            )
            styleRange(
                "${MasuSheetConfig.formatColumnLabel(minC)}$totalRowNum:${fullEndCol}$totalRowNum",
                bgHex = "#E8F5E9",
                fgHex = "#0F291E",
                bold = true,
                numFmt = "#,##0"
            )
            styleRange(
                "${startColLetter}${minR + 2}:${fullEndCol}$totalRowNum",
                numFmt = "#,##0",
                borders = "all"
            )
            bullets.add("${newColLetter}列に「合計 (=SUM)」、${avgColLetter}列に「平均 (=AVERAGE)」を追加")
            bullets.add("${totalRowNum}行目に各列の全体合計行を追加し、桁区切り (#,##0) と罫線を設定")
        }

        if (prompt.contains("構成比") || prompt.contains("割合") || prompt.contains("パーセント") || prompt.contains("%") ||
            pLower.contains("ratio") || pLower.contains("percent")
        ) {
            val totalRowNum = maxR + 2
            val sumColLet = if (sumColLetCreated != null) {
                sumColLetCreated
            } else {
                val sumIdx = nextFreeCol++
                val letStr = MasuSheetConfig.formatColumnLabel(sumIdx)
                setCell("${letStr}${minR + 1}", "合計")
                for (r in (minR + 1)..maxR) {
                    val rowNum = r + 1
                    setCell("${letStr}$rowNum", "=SUM(${startColLetter}$rowNum:${endColLetter}$rowNum)")
                }
                setCell("${MasuSheetConfig.formatColumnLabel(minC)}$totalRowNum", "合計")
                setCell("${letStr}$totalRowNum", "=SUM(${letStr}${minR + 2}:${letStr}${maxR + 1})")
                letStr
            }
            val ratioColIdx = nextFreeCol++
            val ratioColLet = MasuSheetConfig.formatColumnLabel(ratioColIdx)
            setCell("${ratioColLet}${minR + 1}", "構成比")
            for (r in (minR + 1)..maxR) {
                val rowNum = r + 1
                setCell("${ratioColLet}$rowNum", "=${sumColLet}$rowNum/$sumColLet$$totalRowNum")
            }
            setCell("${ratioColLet}$totalRowNum", "=SUM(${ratioColLet}${minR + 2}:${ratioColLet}${maxR + 1})")
            styleRange("${ratioColLet}${minR + 1}:${ratioColLet}${minR + 1}", bgHex = "#1F4E79", fgHex = "#FFFFFF", bold = true, hAlign = "center")
            styleRange("${ratioColLet}${minR + 2}:${ratioColLet}$totalRowNum", numFmt = "0.0%", borders = "all")
            bullets.add("${ratioColLet}列に構成比（=${sumColLet}行/$sumColLet$$totalRowNum）とパーセント書式 (0.0%) を設定")
        }

        if (prompt.contains("順位") || prompt.contains("ランク") || prompt.contains("RANK", ignoreCase = true)) {
            val rankColIdx = nextFreeCol++
            val rankColLet = MasuSheetConfig.formatColumnLabel(rankColIdx)
            setCell("${rankColLet}${minR + 1}", "順位")
            for (r in (minR + 1)..maxR) {
                val rowNum = r + 1
                setCell("${rankColLet}$rowNum", "=RANK(${endColLetter}$rowNum, ${endColLetter}$${minR + 2}:${endColLetter}$${maxR + 1})")
            }
            styleRange("${rankColLet}${minR + 1}:${rankColLet}${minR + 1}", bgHex = "#1F4E79", fgHex = "#FFFFFF", bold = true, hAlign = "center")
            styleRange("${rankColLet}${minR + 1}:${rankColLet}${maxR + 1}", borders = "all", hAlign = "center")
            bullets.add("${rankColLet}列に RANK 関数による順位列を追加")
        }

        if (prompt.contains("整形") || prompt.contains("見やすく") || prompt.contains("色") || prompt.contains("罫線") || prompt.contains("条件付き書式") ||
            pLower.contains("format") || pLower.contains("style") || pLower.contains("border")
        ) {
            val fullRange = "${MasuSheetConfig.formatColumnLabel(minC)}${minR + 1}:${MasuSheetConfig.formatColumnLabel(maxC)}${maxR + 1}"
            val hdrRange = "${MasuSheetConfig.formatColumnLabel(minC)}${minR + 1}:${MasuSheetConfig.formatColumnLabel(maxC)}${minR + 1}"
            styleRange(hdrRange, bgHex = "#1F4E79", fgHex = "#FFFFFF", bold = true, hAlign = "center")
            styleRange(fullRange, borders = "all")
            val numRange = "${MasuSheetConfig.formatColumnLabel(minC + 1)}${minR + 2}:${MasuSheetConfig.formatColumnLabel(maxC)}${maxR + 1}"
            styleRange(numRange, numFmt = "#,##0")
            cmds.add("format.conditional" to "{\"range\":\"$numRange\",\"type\":\"colorScale\"}")
            bullets.add("見出し行 ($hdrRange) の配色、表全体 ($fullRange) の格子罫線、数値のカラースケール条件付き書式を適用")
        }

        if (prompt.contains("並び替え") || prompt.contains("ソート") || pLower.contains("sort")) {
            val desc = prompt.contains("降順") || prompt.contains("高い順") || prompt.contains("大きい順") || pLower.contains("desc")
            cmds.add((if (desc) "data.sortDesc" else "data.sortAsc") to "{}")
            bullets.add("データを${if (desc) "降順" else "昇順"}に並べ替え")
        }

        return AiTurnExecutionResult(
            replyText = "現在のシート「${vp.sheetName}」に対して、「$prompt」の編集を実行しました。",
            summaryBullets = bullets.ifEmpty { listOf("現在のシートの表に数式と書式を適用しました") },
            commands = cmds
        )
    }

    // [EN] Protect populated worksheets from accidental erasure when the offline prompt is a general question/summary
    //      and did NOT explicitly ask to create/build a new sheet or template (`explicitlyWantsNewTable == false`).
    // [JA] 既にデータが存在するシートに対し、新規作成の指示ではない一般的な質問・要約プロンプトが入力された場合は、
    //      既存セルを白紙化（`edit.clearAll`）せず、非破壊の集計サマリーを返してユーザーデータを保護します。
    if (nonEmpty.size >= 2 && !explicitlyWantsNewTable) {
        val nums = nonEmpty.mapNotNull { cell ->
            cell.displayText.replace(",", "").replace("¥", "").replace("$", "").replace("%", "").trim().toDoubleOrNull()
        }
        val sum = nums.sum()
        val avg = if (nums.isNotEmpty()) sum / nums.size else 0.0
        val maxVal = nums.maxOrNull() ?: 0.0
        val minVal = nums.minOrNull() ?: 0.0
        val rangeStr = "${MasuSheetConfig.formatCellAddress(minR, minC)}:${MasuSheetConfig.formatCellAddress(maxR, maxC)}"
        return AiTurnExecutionResult(
            replyText = "現在のシート「${vp.sheetName}」の既存データ（$rangeStr）を保護したまま集計・分析しました：\n" +
                "・入力済みセル数: ${nonEmpty.size} セル（数値データ: ${nums.size} 件）\n" +
                "・合計: ${String.format("%,.2f", sum)} / 平均: ${String.format("%,.2f", avg)} / 最大: ${String.format("%,.2f", maxVal)} / 最小: ${String.format("%,.2f", minVal)}\n" +
                "（※既存の表へ列を追加する場合は「合計・平均を追加」「グラフ化して」、新しい表を作る場合は「新しいシートに〇〇表を作成して」と指示してください）",
            summaryBullets = listOf(
                "既存データを維持（上書き消去なし）: $rangeStr",
                "合計 = ${String.format("%,.0f", sum)}、平均 = ${String.format("%,.1f", avg)}"
            ),
            commands = emptyList()
        )
    }

    // CASE B: General-Purpose Custom Worksheet Synthesis when blank OR explicitly requested to create/build a table
    val spec = synthesizeCustomSheetSpec(prompt, clarifyingAnswers)

    val wantsSeparateSheetTab = nonEmpty.isNotEmpty() &&
        (prompt.contains("新しいシート") || prompt.contains("別シート") || pLower.contains("new sheet"))
    if (wantsSeparateSheetTab) {
        cmds.add("home.insertSheet" to "{}")
    } else if (nonEmpty.isNotEmpty()) {
        cmds.add("edit.clearAll" to "{\"range\":\"A1:K35\"}")
    }

    // 1. Title Banner (Row 1: A1:F1)
    setCell("A1", spec.title)
    styleRange("A1:F1", bgHex = spec.primaryHex, fgHex = "#FFFFFF", bold = true)
    cmds.add("format.fontSize" to "{\"range\":\"A1\",\"size\":14}")

    // 2. Subtitle / Metadata (Row 2)
    val answerSummary = if (clarifyingAnswers.isEmpty()) spec.subtitle else clarifyingAnswers.values.joinToString(" / ")
    setCell("A2", "作成基準: $answerSummary")
    styleRange("A2:F2", bgHex = spec.softBgHex, fgHex = "#334155", bold = false)

    // 3. KPI Summary Cards (Rows 4..5)
    val dataStartRowNum = 8
    val dataEndRowNum = dataStartRowNum + spec.rows.size - 1
    val totalRowNum = dataEndRowNum + 1
    val primaryValueColLetter = MasuSheetConfig.formatColumnLabel(spec.primaryNumericColIndex)

    setCell("A4", spec.kpi1Label)
    setCell("B4", "=SUM(${primaryValueColLetter}$dataStartRowNum:${primaryValueColLetter}$dataEndRowNum)")
    setCell("C4", spec.kpi2Label)
    setCell("D4", "=AVERAGE(${primaryValueColLetter}$dataStartRowNum:${primaryValueColLetter}$dataEndRowNum)")
    setCell("E4", spec.kpi3Label)
    setCell("F4", "=MAX(${primaryValueColLetter}$dataStartRowNum:${primaryValueColLetter}$dataEndRowNum)")

    styleRange("A4:F4", bgHex = "#F1F5F9", fgHex = "#0F172A", bold = true, borders = "all")
    styleRange("B4:B4", numFmt = spec.numberFormat)
    styleRange("D4:D4", numFmt = spec.numberFormat)
    styleRange("F4:F4", numFmt = spec.numberFormat)

    // 4. Main Table Headers (Row 7)
    val headerRowNum = 7
    spec.headers.forEachIndexed { cIdx, hText ->
        val colLet = MasuSheetConfig.formatColumnLabel(cIdx)
        setCell("$colLet$headerRowNum", hText)
    }
    val lastColLet = MasuSheetConfig.formatColumnLabel(spec.headers.size - 1)
    styleRange("A$headerRowNum:$lastColLet$headerRowNum", bgHex = spec.primaryHex, fgHex = "#FFFFFF", bold = true, borders = "all", hAlign = "center")

    // 5. Data Rows with Live Formulas (Rows 8 .. dataEndRowNum)
    spec.rows.forEachIndexed { rOffset, rowCells ->
        val rNum = dataStartRowNum + rOffset
        val rowBg = if (rOffset % 2 == 1) "#F8FAFC" else "#FFFFFF"
        rowCells.forEachIndexed { cIdx, rawVal ->
            val colLet = MasuSheetConfig.formatColumnLabel(cIdx)
            val resolvedFormulaOrVal = rawVal
                .replace("{ROW}", rNum.toString())
                .replace("{TOTAL_ROW}", totalRowNum.toString())
            setCell("$colLet$rNum", resolvedFormulaOrVal)
        }
        styleRange("A$rNum:$lastColLet$rNum", bgHex = rowBg, borders = "all")
    }

    // 6. Total Row (Row totalRowNum)
    setCell("A$totalRowNum", "合計 / 集計")
    for (cIdx in 1 until spec.headers.size) {
        val colLet = MasuSheetConfig.formatColumnLabel(cIdx)
        val colFormula = spec.totalRowFormulas.getOrNull(cIdx)
            ?.replace("{START}", dataStartRowNum.toString())
            ?.replace("{END}", dataEndRowNum.toString())
            ?.replace("{COL}", colLet)
            ?: ""
        if (colFormula.isNotBlank()) {
            setCell("$colLet$totalRowNum", colFormula)
        }
    }
    styleRange("A$totalRowNum:$lastColLet$totalRowNum", bgHex = spec.softBgHex, fgHex = "#0F172A", bold = true, borders = "all")

    // 7. Number Formats & Conditional Formatting on Numeric / Percent Columns
    for (cIdx in spec.numericColIndices) {
        val colLet = MasuSheetConfig.formatColumnLabel(cIdx)
        styleRange("$colLet$dataStartRowNum:$colLet$totalRowNum", numFmt = spec.numberFormat)
    }
    for (cIdx in spec.percentColIndices) {
        val colLet = MasuSheetConfig.formatColumnLabel(cIdx)
        styleRange("$colLet$dataStartRowNum:$colLet$totalRowNum", numFmt = "0.0%")
    }
    cmds.add(
        "format.conditional" to "{\"range\":\"${primaryValueColLetter}$dataStartRowNum:${primaryValueColLetter}$dataEndRowNum\",\"type\":\"colorScale\"}"
    )

    if (wantsChart) {
        val chartType = resolveRequestedChartType(prompt)
        val chartParams = JSONObject().apply {
            put("type", chartType)
            put("startRow", headerRowNum - 1)
            put("startCol", 0)
            put("endRow", dataEndRowNum - 1)
            put("endCol", spec.primaryNumericColIndex)
            put("title", "${spec.title} グラフ")
        }.toString()
        cmds.add("ui.insertChart" to chartParams)
    }

    return AiTurnExecutionResult(
        replyText = "「${spec.title}」を作成しました。上部に KPI サマリーカード（SUM / AVERAGE / MAX）、下部に数式連動の明細テーブルと条件付き書式（カラースケール）${if (wantsChart) "、および連動グラフ" else ""}を配置しています。",
        summaryBullets = listOf(
            "タイトル・KPIサマリー（A1:F4）に自動集計数式（=SUM, =AVERAGE, =MAX）を設定",
            "メインテーブル（A$headerRowNum:$lastColLet$totalRowNum）に ${spec.headers.joinToString("・")} の ${spec.rows.size} 行データと数式を挿入",
            "数値表示形式 (${spec.numberFormat})・構成比/達成率 (0.0%)・カラースケール条件付き書式${if (wantsChart) "・グラフ" else ""}を適用"
        ),
        commands = cmds
    )
}

private data class SynthesizedSheetSpec(
    val title: String,
    val subtitle: String,
    val primaryHex: String,
    val softBgHex: String,
    val kpi1Label: String,
    val kpi2Label: String,
    val kpi3Label: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val totalRowFormulas: List<String>,
    val primaryNumericColIndex: Int,
    val numericColIndices: List<Int>,
    val percentColIndices: List<Int>,
    val numberFormat: String
)

/**
 * [EN] Infers column headers, sample rows, and summary formulas for a custom domain table requested in natural language.
 * [JA] 自然言語プロンプトとプラン回答から、業務・用途に応じたカスタム表の列見出し・初期データ・集計数式仕様を合成します。
 */
private fun synthesizeCustomSheetSpec(
    prompt: String,
    answers: Map<String, String>
): SynthesizedSheetSpec {
    val p = prompt.trim()

    // 1. Check if the user explicitly listed custom column names in their prompt (e.g., "列は A, B, C..." or comma/slash separated items)
    val explicitColsMatch = Regex("""(?:項目|列|カラム)[は：:]\s*([^\n。]+)""").find(p)
    if (explicitColsMatch != null) {
        val userCols = explicitColsMatch.groupValues[1]
            .split("、", ",", "・", "/", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (userCols.size >= 2) {
            val headers = userCols + listOf("合計", "構成比")
            val numCols = (1 until headers.size - 1).toList()
            val sumEndCol = MasuSheetConfig.formatColumnLabel(headers.size - 3)
            val sumCol = MasuSheetConfig.formatColumnLabel(headers.size - 2)
            val rows = (1..6).map { idx ->
                val baseRow = mutableListOf("項目 $idx")
                for (c in 1 until userCols.size) {
                    baseRow.add(((idx * 12000 + c * 4500)).toString())
                }
                baseRow.add("=SUM(B{ROW}:${sumEndCol}{ROW})")
                baseRow.add("=${sumCol}{ROW}/${sumCol}${ '$' }{TOTAL_ROW}")
                baseRow
            }
            val totals = headers.indices.map { cIdx ->
                when (cIdx) {
                    0 -> ""
                    else -> "=SUM({COL}{START}:{COL}{END})"
                }
            }
            return SynthesizedSheetSpec(
                title = p.take(28),
                subtitle = "ユーザー指定カラム構成 (${userCols.joinToString(", ")})",
                primaryHex = "#1E3A8A",
                softBgHex = "#DBEAFE",
                kpi1Label = "総合計",
                kpi2Label = "平均値",
                kpi3Label = "最大値",
                headers = headers,
                rows = rows,
                totalRowFormulas = totals,
                primaryNumericColIndex = headers.size - 2,
                numericColIndices = numCols,
                percentColIndices = listOf(headers.size - 1),
                numberFormat = "#,##0"
            )
        }
    }

    // 2. Domain-adaptive synthesis covering diverse business, operations, engineering, personal & academic sheets
    return when {
        p.contains("在庫") || p.contains("発注") || p.contains("棚卸") || p.contains("商品") -> SynthesizedSheetSpec(
            title = "在庫・発注管理ワークシート",
            subtitle = "安全在庫基準による自動発注判定・在庫金額集計",
            primaryHex = "#0F766E",
            softBgHex = "#CCFBF1",
            kpi1Label = "総在庫金額",
            kpi2Label = "平均在庫金額",
            kpi3Label = "最大在庫金額",
            headers = listOf("商品名", "単価", "現在庫数", "発注点", "在庫金額", "構成比", "発注判定"),
            rows = listOf(
                listOf("ワイヤレスマウス Pro", "3800", "45", "20", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")"),
                listOf("メカニカルキーボード JP", "12400", "12", "15", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")"),
                listOf("27インチ 4Kモニター", "42800", "8", "10", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")"),
                listOf("USB-C ドッキングステーション", "9800", "28", "15", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")"),
                listOf("ノイズキャンセリングヘッドセット", "16500", "9", "12", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")"),
                listOf("Webカメラ FullHD", "6400", "34", "15", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")")
            ),
            totalRowFormulas = listOf("", "=AVERAGE(B{START}:B{END})", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=SUM(E{START}:E{END})", "=SUM(F{START}:F{END})", ""),
            primaryNumericColIndex = 4,
            numericColIndices = listOf(1, 2, 3, 4),
            percentColIndices = listOf(5),
            numberFormat = "¥#,##0"
        )

        p.contains("シフト") || p.contains("勤務") || p.contains("勤怠") || p.contains("タイムカード") -> SynthesizedSheetSpec(
            title = "スタッフ週間シフト・人件費試算表",
            subtitle = "日別勤務時間・週合計時間・概算人件費の自動計算",
            primaryHex = "#1D4ED8",
            softBgHex = "#DBEAFE",
            kpi1Label = "総人件費試算",
            kpi2Label = "平均人件費",
            kpi3Label = "最高支給額",
            headers = listOf("スタッフ名", "時給", "平日勤務(h)", "休日勤務(h)", "合計時間(h)", "概算支給額", "構成比"),
            rows = listOf(
                listOf("佐藤 健太", "1350", "28", "8", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf("鈴木 美咲", "1300", "24", "6", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf("高橋 誠", "1450", "32", "8", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf("田中 彩", "1280", "20", "12", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf("伊藤 翔太", "1400", "30", "4", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf("渡辺 結衣", "1320", "18", "8", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}")
            ),
            totalRowFormulas = listOf("", "=AVERAGE(B{START}:B{END})", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=SUM(E{START}:E{END})", "=SUM(F{START}:F{END})", "=SUM(G{START}:G{END})"),
            primaryNumericColIndex = 5,
            numericColIndices = listOf(1, 2, 3, 4, 5),
            percentColIndices = listOf(6),
            numberFormat = "¥#,##0"
        )

        p.contains("見積") || p.contains("請求") || p.contains("発注書") || p.contains("納品") -> SynthesizedSheetSpec(
            title = "御見積書・請求金額計算シート",
            subtitle = "品目別単価×数量・消費税(10%)・税込金額自動計算",
            primaryHex = "#1E293B",
            softBgHex = "#E2E8F0",
            kpi1Label = "税込合計金額",
            kpi2Label = "1品目平均(税込)",
            kpi3Label = "最大品目金額",
            headers = listOf("品目・作業内容", "単価", "数量", "税抜小計", "消費税(10%)", "税込金額", "構成比"),
            rows = listOf(
                listOf("要件定義・基本設計費", "180000", "1", "=B{ROW}*C{ROW}", "=ROUND(D{ROW}*0.1,0)", "=D{ROW}+E{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf("フロントエンド画面開発", "95000", "4", "=B{ROW}*C{ROW}", "=ROUND(D{ROW}*0.1,0)", "=D{ROW}+E{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf("API・データベース構築", "120000", "2", "=B{ROW}*C{ROW}", "=ROUND(D{ROW}*0.1,0)", "=D{ROW}+E{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf("総合テスト・品質検証", "85000", "2", "=B{ROW}*C{ROW}", "=ROUND(D{ROW}*0.1,0)", "=D{ROW}+E{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf("導入支援・ドキュメント作成", "60000", "1", "=B{ROW}*C{ROW}", "=ROUND(D{ROW}*0.1,0)", "=D{ROW}+E{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}")
            ),
            totalRowFormulas = listOf("", "", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=SUM(E{START}:E{END})", "=SUM(F{START}:F{END})", "=SUM(G{START}:G{END})"),
            primaryNumericColIndex = 5,
            numericColIndices = listOf(1, 2, 3, 4, 5),
            percentColIndices = listOf(6),
            numberFormat = "¥#,##0"
        )

        p.contains("プロジェクト") || p.contains("タスク") || p.contains("WBS") || p.contains("進捗") || p.contains("工程") -> SynthesizedSheetSpec(
            title = "プロジェクト WBS・進捗管理表",
            subtitle = "予定工数・実績工数・消化率・ステータス自動判定",
            primaryHex = "#4338CA",
            softBgHex = "#E0E7FF",
            kpi1Label = "総予定工数(h)",
            kpi2Label = "平均予定工数(h)",
            kpi3Label = "最大タスク工数(h)",
            headers = listOf("タスク名", "予定工数(h)", "実績工数(h)", "残工数(h)", "進捗率", "状態判定"),
            rows = listOf(
                listOf("企画・要件ヒアリング", "24", "24", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")"),
                listOf("UI/UXプロトタイプ設計", "40", "36", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")"),
                listOf("コアエンジン実装", "80", "64", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")"),
                listOf("自動テスト・CI整備", "32", "20", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")"),
                listOf("パフォーマンステスト", "24", "8", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")"),
                listOf("リリース準備・ドキュメント", "16", "4", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")")
            ),
            totalRowFormulas = listOf("", "=SUM(B{START}:B{END})", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=C{END}/B{END}", ""),
            primaryNumericColIndex = 1,
            numericColIndices = listOf(1, 2, 3),
            percentColIndices = listOf(4),
            numberFormat = "#,##0"
        )

        p.contains("予算") || p.contains("家計簿") || p.contains("経費") || p.contains("収支") -> SynthesizedSheetSpec(
            title = "月次予算・実績比較管理シート",
            subtitle = "予算額・実績額・差額・消化率(%)・ステータス判定",
            primaryHex = "#166534",
            softBgHex = "#DCFCE7",
            kpi1Label = "実績合計",
            kpi2Label = "カテゴリ平均",
            kpi3Label = "最大支出項目",
            headers = listOf("費目カテゴリ", "月間予算", "実績額", "予算残高", "消化率", "構成比", "判定"),
            rows = listOf(
                listOf("住居・固定費", "95000", "95000", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")"),
                listOf("食費・日用品", "55000", "48200", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")"),
                listOf("水道光熱・通信費", "26000", "24500", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")"),
                listOf("交通・交際・教養費", "35000", "31800", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")"),
                listOf("医療・保険・予備費", "25000", "19400", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")"),
                listOf("貯蓄・積立投資", "60000", "60000", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")")
            ),
            totalRowFormulas = listOf("", "=SUM(B{START}:B{END})", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=C{END}/B{END}", "=SUM(F{START}:F{END})", ""),
            primaryNumericColIndex = 2,
            numericColIndices = listOf(1, 2, 3),
            percentColIndices = listOf(4, 5),
            numberFormat = "¥#,##0"
        )

        else -> {
            // Dynamic topic extraction for any free-form prompt!
            val cleanTopic = p
                .removeSuffix("を作成して")
                .removeSuffix("を作って")
                .removeSuffix("を作成")
                .removeSuffix("の表")
                .removeSuffix("シート")
                .trim()
                .ifBlank { "データ分析・管理" }
            SynthesizedSheetSpec(
                title = "${cleanTopic}・管理集計シート",
                subtitle = "AI自動設計テーブル（目標・実績・達成率・構成比・自動判定）",
                primaryHex = "#1F4E79",
                softBgHex = "#E0F2FE",
                kpi1Label = "実績総合計",
                kpi2Label = "項目平均値",
                kpi3Label = "最高実績値",
                headers = listOf("${cleanTopic}項目", "目標・基準値", "前期実績", "今期実績", "増減差", "達成率", "構成比"),
                rows = listOf(
                    listOf("${cleanTopic} A", "120000", "108000", "126500", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}"),
                    listOf("${cleanTopic} B", "95000", "89000", "98400", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}"),
                    listOf("${cleanTopic} C", "150000", "142000", "158200", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}"),
                    listOf("${cleanTopic} D", "80000", "76500", "84900", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}"),
                    listOf("${cleanTopic} E", "110000", "98000", "114000", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}"),
                    listOf("${cleanTopic} F", "75000", "71200", "79500", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}")
                ),
                totalRowFormulas = listOf("", "=SUM(B{START}:B{END})", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=SUM(E{START}:E{END})", "=AVERAGE(F{START}:F{END})", "=SUM(G{START}:G{END})"),
                primaryNumericColIndex = 3,
                numericColIndices = listOf(1, 2, 3, 4),
                percentColIndices = listOf(5, 6),
                numberFormat = "#,##0"
            )
        }
    }
}
