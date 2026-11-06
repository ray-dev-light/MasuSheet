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
        p.contains("ドーナツ") || p.contains("doughnut") || p.contains("donut") -> "doughnut"
        p.contains("円グラフ") || p.contains("パイ") || p.contains("pie") || p.contains("内訳") || p.contains("シェア") || p.contains("構成比グラフ") -> "pie"
        p.contains("散布") || p.contains("scatter") -> "scatter"
        p.contains("レーダー") || p.contains("radar") -> "radar"
        p.contains("ウォーターフォール") || p.contains("滝") || p.contains("waterfall") -> "waterfall"
        p.contains("パレート") || p.contains("pareto") -> "pareto"
        p.contains("ヒストグラム") || p.contains("度数") || p.contains("histogram") -> "histogram"
        p.contains("面グラフ") || p.contains("エリア") || p.contains("area") -> "area"
        p.contains("積み上げ") || p.contains("積上") || p.contains("stacked") -> "stackedColumn"
        p.contains("折れ線") || p.contains("折線") || p.contains("line") || p.contains("推移") || p.contains("トレンド") -> "line"
        p.contains("横棒") || p.contains("bar") -> "bar"
        else -> "column"
    }
}

private fun isChartPrompt(prompt: String): Boolean {
    val p = prompt.lowercase(Locale.ROOT)
    return p.contains("グラフ") || p.contains("チャート") || p.contains("chart") || p.contains("graph") || p.contains("plot") ||
        p.contains("棒グラフ") || p.contains("折れ線") || p.contains("円グラフ") || p.contains("ドーナツ") ||
        p.contains("散布図") || p.contains("レーダー") || p.contains("パレート") || p.contains("ヒストグラム")
}

private fun isAiSummaryFooterLabel(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty()) return false
    val lower = t.lowercase(Locale.ROOT)
    return t == "合計" || t == "総計" || t == "小計" || t == "総合計" || t == "全体合計" ||
        t.startsWith("合計 ") || t.startsWith("合計/") || t.startsWith("合計 /") || t.startsWith("合計（") || t.startsWith("合計(") ||
        t.endsWith(" 合計") || t.endsWith(" 集計") || t.endsWith(" 小計") ||
        lower == "total" || lower == "grand total" || lower == "subtotal" ||
        lower.startsWith("total ") || lower.startsWith("grand total") || lower.endsWith(" total")
}

private data class DetectedMainTableBounds(
    val headerRow: Int,
    val dataStartRow: Int,
    val dataEndRow: Int,
    val startCol: Int,
    val endCol: Int,
    val existingTotalRow: Int?
)

/**
 * [EN] Locates the primary multi-row data table on [vp], skipping standalone 1-row KPI cards or title banners above it and peeling off any existing total footer row.
 * [JA] シート上部のタイトル帯や1行KPIサマリーカードを除外してメイン明細テーブルの範囲を特定し、既存の合計行があれば分離して返します。
 */
private fun detectMainDataTableOnSheet(vp: NativeViewportSnapshot, minR: Int, maxR: Int, minC: Int, maxC: Int): DetectedMainTableBounds {
    var headerR = minR
    for (r in minR..maxR) {
        val nonBlankInRow = (minC..maxC).count { c -> vp.cellAt(r, c)?.displayText?.isNotBlank() == true }
        val nonBlankNextRow = if (r + 1 <= maxR) {
            (minC..maxC).count { c -> vp.cellAt(r + 1, c)?.displayText?.isNotBlank() == true }
        } else {
            0
        }
        // Require >= 2 populated columns AND a populated data row immediately below so 1-row KPI cards (Row 4) are skipped!
        if (nonBlankInRow >= 2 && nonBlankNextRow >= 2) {
            headerR = r
            break
        }
    }
    var rawEndR = headerR
    for (r in (headerR + 1)..maxR) {
        val rowHasAny = (minC..maxC).any { c -> vp.cellAt(r, c)?.displayText?.isNotBlank() == true }
        if (rowHasAny) {
            rawEndR = r
        } else {
            break
        }
    }
    var tblStartC = maxC
    var tblEndC = minC
    for (r in headerR..rawEndR) {
        for (c in minC..maxC) {
            if (vp.cellAt(r, c)?.displayText?.isNotBlank() == true) {
                if (c < tblStartC) tblStartC = c
                if (c > tblEndC) tblEndC = c
            }
        }
    }
    if (tblStartC > tblEndC) {
        tblStartC = minC
        tblEndC = maxC
    }
    var existingTotalR: Int? = null
    var dataEndR = rawEndR
    if (rawEndR > headerR + 1) {
        val firstCell = vp.cellAt(rawEndR, tblStartC)?.displayText.orEmpty()
        if (isAiSummaryFooterLabel(firstCell)) {
            existingTotalR = rawEndR
            dataEndR = rawEndR - 1
        }
    }
    val dataStartR = (headerR + 1).coerceAtMost(dataEndR)
    return DetectedMainTableBounds(
        headerRow = headerR,
        dataStartRow = dataStartR,
        dataEndRow = dataEndR,
        startCol = tblStartC,
        endCol = tblEndC,
        existingTotalRow = existingTotalR
    )
}

/**
 * [EN] Offline rule-based spreadsheet agent that inspects the active sheet and user prompt to generate tables, formulas, formatting, or charts without network calls.
 * [JA] ネットワーク通信なしでシート構造と指示文を解析し、表作成・数式挿入・書式整形・グラフ生成コマンドを組み立てる内蔵エージェントです。
 */
internal fun runGeneralPurposeLocalAgent(
    prompt: String,
    editMode: String,
    clarifyingAnswers: Map<String, String>,
    vp: NativeViewportSnapshot,
    isEn: Boolean = false
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
            replyText = if (isEn) {
                "Analyzed current sheet \"${vp.sheetName}\" (Chat Only mode):\n" +
                    "• Populated cells: ${nonEmpty.size} (Numeric values: ${nums.size})\n" +
                    "• Sum: ${String.format("%,.2f", sum)} / Average: ${String.format("%,.2f", avg)}\n" +
                    "• Max: ${String.format("%,.2f", maxVal)} / Min: ${String.format("%,.2f", minVal)}\n" +
                    "Switch to \"Allow Edit\" or \"Plan\" mode to edit cells or insert charts."
            } else {
                "現在のシート「${vp.sheetName}」を分析しました（チャットのみモード）：\n" +
                    "・入力済みセル数: ${nonEmpty.size} セル（数値データ: ${nums.size} 件）\n" +
                    "・数値合計: ${String.format("%,.2f", sum)} / 平均: ${String.format("%,.2f", avg)}\n" +
                    "・最大値: ${String.format("%,.2f", maxVal)} / 最小値: ${String.format("%,.2f", minVal)}\n" +
                    "シートを直接編集・グラフ作成する場合は、モードを「編集を許可」または「プラン」に切り替えて指示してください。"
            },
            summaryBullets = if (isEn) {
                listOf(
                    "Range: ${MasuSheetConfig.formatCellAddress(minR, minC)}:${MasuSheetConfig.formatCellAddress(maxR, maxC)}",
                    "Sum = ${String.format("%,.0f", sum)}, Average = ${String.format("%,.1f", avg)}"
                )
            } else {
                listOf(
                    "対象範囲: ${MasuSheetConfig.formatCellAddress(minR, minC)}:${MasuSheetConfig.formatCellAddress(maxR, maxC)}",
                    "合計 = ${String.format("%,.0f", sum)}、平均 = ${String.format("%,.1f", avg)}"
                )
            }
        )
    }

    val pLower = prompt.lowercase(Locale.ROOT)
    val answersCombined = clarifyingAnswers.values.joinToString(" ")
    val answersLower = answersCombined.lowercase(Locale.ROOT)

    // CASE 0: User asked to chart/graph the current sheet ("グラフ化して", "円グラフにして", "折れ線グラフを作って", etc.)
    val wantsChart = isChartPrompt(prompt)
    val wantsNewSheetTableForChart = prompt.contains("表を作成") || prompt.contains("表を作って") ||
        prompt.contains("シートを作成") || prompt.contains("シートを作って") || prompt.contains("新規") ||
        (pLower.contains("create") && (pLower.contains("sheet") || pLower.contains("table") || pLower.contains("template"))) ||
        nonEmpty.size < 2

    if (wantsChart && !wantsNewSheetTableForChart) {
        val chartType = resolveRequestedChartType(prompt)
        val chartTypeLabel = if (isEn) {
            when (chartType) {
                "pie" -> "Pie Chart"
                "doughnut" -> "Doughnut Chart"
                "line" -> "Line Chart"
                "bar" -> "Bar Chart"
                "area" -> "Area Chart"
                "scatter" -> "Scatter Chart"
                "radar" -> "Radar Chart"
                "waterfall" -> "Waterfall Chart"
                "pareto" -> "Pareto Chart"
                "histogram" -> "Histogram"
                "stackedColumn" -> "Stacked Column Chart"
                else -> "Column Chart"
            }
        } else {
            when (chartType) {
                "pie" -> "円グラフ"
                "doughnut" -> "ドーナツグラフ"
                "line" -> "折れ線グラフ"
                "bar" -> "横棒グラフ"
                "area" -> "面グラフ"
                "scatter" -> "散布図"
                "radar" -> "レーダーチャート"
                "waterfall" -> "ウォーターフォール図"
                "pareto" -> "パレート図"
                "histogram" -> "ヒストグラム"
                "stackedColumn" -> "積み上げ縦棒グラフ"
                else -> "縦棒グラフ"
            }
        }
        val selR0 = minOf(vp.selStartRow, vp.selEndRow)
        val selR1 = maxOf(vp.selStartRow, vp.selEndRow)
        val selC0 = minOf(vp.selStartCol, vp.selEndCol)
        val selC1 = maxOf(vp.selStartCol, vp.selEndCol)
        val hasMultiSelection = (selR1 > selR0) || (selC1 > selC0)
        val (cStartR, cStartC, cEndR, cEndC) = if (hasMultiSelection) {
            var endR = selR1
            if (endR > selR0 + 1 && isAiSummaryFooterLabel(vp.cellAt(endR, selC0)?.displayText.orEmpty())) {
                endR -= 1
            }
            listOf(selR0, selC0, endR, selC1)
        } else {
            val tbl = detectMainDataTableOnSheet(vp, minR, maxR, minC, maxC)
            listOf(tbl.headerRow, tbl.startCol, tbl.dataEndRow.coerceAtLeast(tbl.headerRow + 1), tbl.endCol.coerceAtLeast(tbl.startCol + 1))
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
            replyText = if (isEn) {
                "Created a $chartTypeLabel from data range ($rangeLabel) on \"${vp.sheetName}\"."
            } else {
                "現在のシート「${vp.sheetName}」のデータ範囲 ($rangeLabel) から「$chartTypeLabel」を作成し、シート上に配置しました。"
            },
            summaryBullets = if (isEn) {
                listOf(
                    "Chart type: $chartTypeLabel ($chartType)",
                    "Data range: $rangeLabel (summary footer excluded automatically)",
                    "Double-click chart titles or labels to edit text inline"
                )
            } else {
                listOf(
                    "グラフ種類: $chartTypeLabel ($chartType)",
                    "データ範囲: $rangeLabel （見出しと数値系列を自動認識・合計行は自動除外）",
                    "グラフ内のタイトルや項目ラベルはダブルクリックで直接文字編集できます"
                )
            },
            commands = cmds
        )
    }

    // CASE A: Modifying / enhancing the existing table on the sheet
    val hasDomainTemplateNoun = prompt.contains("在庫") || prompt.contains("発注") || prompt.contains("棚卸") ||
        prompt.contains("シフト") || prompt.contains("勤務") || prompt.contains("勤怠") ||
        prompt.contains("見積") || prompt.contains("請求") || prompt.contains("プロジェクト") ||
        prompt.contains("WBS", ignoreCase = true) || prompt.contains("予算") || prompt.contains("家計簿") ||
        prompt.contains("経費") || prompt.contains("収支") || prompt.contains("テンプレート") ||
        prompt.contains("フォーマット") || prompt.contains("シート") ||
        prompt.contains("表を作成") || prompt.contains("表を作って") ||
        pLower.contains("inventory") || pLower.contains("shift") || pLower.contains("payroll") ||
        pLower.contains("invoice") || pLower.contains("quote") || pLower.contains("wbs") ||
        pLower.contains("budget") || pLower.contains("expense") || pLower.contains("template") ||
        pLower.contains("sheet") || pLower.contains("new table") || pLower.contains("create table") ||
        pLower.contains("create a table")
    val hasCreateVerb = prompt.contains("作成") || prompt.contains("作って") || prompt.contains("新規") ||
        pLower.contains("create") || pLower.contains("build") || pLower.contains("generate") ||
        pLower.contains("make ") || pLower.contains("new ")
    val explicitlyWantsNewTable = if (nonEmpty.size >= 4) {
        hasCreateVerb && hasDomainTemplateNoun
    } else {
        hasCreateVerb || hasDomainTemplateNoun
    }

    val hasStandaloneSumWord = Regex("""\bsum\b""").containsMatchIn(pLower)
    val hasTaxWord = prompt.contains("税込") || prompt.contains("消費税") || Regex("""\btax\b|\bvat\b""").containsMatchIn(pLower)
    val isModifyExistingRequest = nonEmpty.size >= 4 &&
        !explicitlyWantsNewTable &&
        (prompt.contains("合計") || prompt.contains("平均") || prompt.contains("構成比") ||
            prompt.contains("割合") || prompt.contains("順位") || prompt.contains("ランク") ||
            hasTaxWord || prompt.contains("整形") || prompt.contains("見やすく") ||
            prompt.contains("罫線") || prompt.contains("色") || prompt.contains("条件付き書式") ||
            prompt.contains("並び替え") || prompt.contains("並べ替え") || prompt.contains("ソート") ||
            prompt.contains("集計") || prompt.contains("追加") ||
            pLower.contains("total") || hasStandaloneSumWord || pLower.contains("average") ||
            pLower.contains("ratio") || pLower.contains("percent") || pLower.contains("share") ||
            pLower.contains("rank") || pLower.contains("format") || pLower.contains("style") ||
            pLower.contains("border") || pLower.contains("sort"))

    if (isModifyExistingRequest) {
        if (answersCombined.contains("新しいシート") || answersLower.contains("new sheet")) {
            // Respect user choosing "output to new sheet" in Plan Mode by synthesizing cleanly on a new tab
            val specOnNew = synthesizeCustomSheetSpec(prompt, clarifyingAnswers, isEn)
            cmds.add("home.insertSheet" to "{}")
            val lastCol = MasuSheetConfig.formatColumnLabel(specOnNew.headers.size - 1)
            specOnNew.headers.forEachIndexed { idx, h -> setCell("${MasuSheetConfig.formatColumnLabel(idx)}1", h) }
            styleRange("A1:${lastCol}1", bgHex = "#1F4E79", fgHex = "#FFFFFF", bold = true, borders = "all", hAlign = "center")
            return AiTurnExecutionResult(
                replyText = if (isEn) "Created a new sheet for \"$prompt\"." else "新しいシートを追加し、「$prompt」の構成を作成しました。",
                summaryBullets = listOf(if (isEn) "Inserted new worksheet tab" else "既存シートを保護して新規シートへ出力"),
                commands = cmds
            )
        }

        val selR0 = minOf(vp.selStartRow, vp.selEndRow)
        val selR1 = maxOf(vp.selStartRow, vp.selEndRow)
        val selC0 = minOf(vp.selStartCol, vp.selEndCol)
        val selC1 = maxOf(vp.selStartCol, vp.selEndCol)
        val useSelectionOnly = answersCombined.contains("選択中のセル範囲") && (selR1 > selR0 || selC1 > selC0)
        val tbl = if (useSelectionOnly) {
            detectMainDataTableOnSheet(vp, selR0, selR1, selC0, selC1)
        } else {
            detectMainDataTableOnSheet(vp, minR, maxR, minC, maxC)
        }
        val hdrR = tbl.headerRow
        val dStartR = tbl.dataStartRow
        val dEndR = tbl.dataEndRow
        val tC0 = tbl.startCol
        val tC1 = tbl.endCol
        val totalRowIdx = tbl.existingTotalRow ?: (dEndR + 1)
        val totalRowNum = totalRowIdx + 1

        // Identify numeric value columns inside the detected table (excluding % and text status columns)
        val numericColsInTable = (tC0..tC1).filter { c ->
            (dStartR..dEndR).any { r ->
                val txt = vp.cellAt(r, c)?.displayText?.trim().orEmpty()
                txt.isNotEmpty() && !txt.endsWith("%") &&
                    txt.replace(",", "").replace("¥", "").replace("$", "").toDoubleOrNull() != null
            }
        }
        val firstNumCol = numericColsInTable.firstOrNull() ?: (tC0 + 1).coerceAtMost(tC1)
        val lastNumCol = numericColsInTable.lastOrNull() ?: tC1
        val startColLetter = MasuSheetConfig.formatColumnLabel(firstNumCol)
        val endColLetter = MasuSheetConfig.formatColumnLabel(lastNumCol)
        val primaryValColLetter = MasuSheetConfig.formatColumnLabel(lastNumCol)

        var nextFreeCol = tC1 + 1
        var sumColLetCreated: String? = null
        var hasTotalRowOut = tbl.existingTotalRow != null
        val bullets = mutableListOf<String>()

        val formatOnlyPlan = answersCombined.contains("デザイン整形のみ")
        var wantsSum = !formatOnlyPlan && (prompt.contains("合計") || prompt.contains("集計") || pLower.contains("total") || hasStandaloneSumWord)
        val wantsAvg = !formatOnlyPlan && (prompt.contains("平均") || pLower.contains("average") || pLower.contains("avg"))
        val wantsRatio = !formatOnlyPlan && (prompt.contains("構成比") || prompt.contains("割合") || prompt.contains("パーセント") ||
            prompt.contains("%") || pLower.contains("ratio") || pLower.contains("percent") || pLower.contains("share"))
        val wantsRank = !formatOnlyPlan && (prompt.contains("順位") || prompt.contains("ランク") || pLower.contains("rank"))
        val wantsTax = !formatOnlyPlan && hasTaxWord
        val wantsFormat = prompt.contains("整形") || prompt.contains("見やすく") || prompt.contains("色") ||
            prompt.contains("罫線") || prompt.contains("条件付き書式") || formatOnlyPlan ||
            pLower.contains("format") || pLower.contains("style") || pLower.contains("border")
        val wantsSort = prompt.contains("並び替え") || prompt.contains("並べ替え") || prompt.contains("ソート") || pLower.contains("sort")

        if (!wantsSum && !wantsAvg && !wantsRatio && !wantsRank && !wantsTax && !wantsFormat && !wantsSort) {
            wantsSum = true
        }

        if (wantsSum || wantsAvg) {
            var lastAddedIdx = tC1
            var avgColLetter: String? = null
            if (wantsSum) {
                val sumColIdx = nextFreeCol++
                lastAddedIdx = sumColIdx
                val newColLetter = MasuSheetConfig.formatColumnLabel(sumColIdx)
                sumColLetCreated = newColLetter
                setCell("${newColLetter}${hdrR + 1}", if (isEn) "Total" else "合計")
                for (r in dStartR..dEndR) {
                    val rowNum = r + 1
                    setCell("${newColLetter}$rowNum", "=SUM(${startColLetter}$rowNum:${endColLetter}$rowNum)")
                }
            }
            if (wantsAvg) {
                val avgColIdx = nextFreeCol++
                lastAddedIdx = avgColIdx
                avgColLetter = MasuSheetConfig.formatColumnLabel(avgColIdx)
                setCell("${avgColLetter}${hdrR + 1}", if (isEn) "Average" else "平均")
                for (r in dStartR..dEndR) {
                    val rowNum = r + 1
                    setCell("${avgColLetter}$rowNum", "=AVERAGE(${startColLetter}$rowNum:${endColLetter}$rowNum)")
                }
            }
            hasTotalRowOut = true
            val existingFooterLabel = vp.cellAt(totalRowIdx, tC0)?.displayText?.takeIf { it.isNotBlank() }
            setCell("${MasuSheetConfig.formatColumnLabel(tC0)}$totalRowNum", existingFooterLabel ?: if (isEn) "Grand Total" else "全体合計")
            for (c in firstNumCol..lastAddedIdx) {
                if (c in (tC0..tC1) && c !in numericColsInTable) continue
                val colLet = MasuSheetConfig.formatColumnLabel(c)
                setCell("$colLet$totalRowNum", "=SUM($colLet${dStartR + 1}:$colLet${dEndR + 1})")
            }
            val fullEndCol = MasuSheetConfig.formatColumnLabel(lastAddedIdx)
            styleRange(
                "${MasuSheetConfig.formatColumnLabel(tC0)}${hdrR + 1}:${fullEndCol}${hdrR + 1}",
                bgHex = "#1F4E79",
                fgHex = "#FFFFFF",
                bold = true,
                hAlign = "center"
            )
            styleRange(
                "${MasuSheetConfig.formatColumnLabel(tC0)}$totalRowNum:${fullEndCol}$totalRowNum",
                bgHex = "#E8F5E9",
                fgHex = "#0F291E",
                bold = true,
                borders = "all"
            )
            val addedStartLet = sumColLetCreated ?: avgColLetter ?: fullEndCol
            styleRange(
                "${addedStartLet}${dStartR + 1}:${fullEndCol}$totalRowNum",
                numFmt = "#,##0",
                borders = "all"
            )
            bullets.add(
                if (isEn) {
                    "Added ${listOfNotNull(sumColLetCreated?.let { "$it (SUM)" }, avgColLetter?.let { "$it (AVERAGE)" }).joinToString(" & ")} and total row ($totalRowNum)"
                } else {
                    "${listOfNotNull(sumColLetCreated?.let { "${it}列に「合計 (=SUM)」" }, avgColLetter?.let { "${it}列に「平均 (=AVERAGE)」" }).joinToString("、")}および${totalRowNum}行目の集計行を設定"
                }
            )
        }

        if (wantsRatio && !answersCombined.contains("SUM/AVERAGE）のみ")) {
            hasTotalRowOut = true
            val sumColLet = if (sumColLetCreated != null) {
                sumColLetCreated
            } else if (numericColsInTable.size == 1) {
                val singleLet = MasuSheetConfig.formatColumnLabel(numericColsInTable.first())
                setCell("${MasuSheetConfig.formatColumnLabel(tC0)}$totalRowNum", vp.cellAt(totalRowIdx, tC0)?.displayText?.takeIf { it.isNotBlank() } ?: if (isEn) "Total" else "合計")
                setCell("${singleLet}$totalRowNum", "=SUM(${singleLet}${dStartR + 1}:${singleLet}${dEndR + 1})")
                singleLet
            } else {
                val sumIdx = nextFreeCol++
                val letStr = MasuSheetConfig.formatColumnLabel(sumIdx)
                setCell("${letStr}${hdrR + 1}", if (isEn) "Total" else "合計")
                for (r in dStartR..dEndR) {
                    val rowNum = r + 1
                    setCell("${letStr}$rowNum", "=SUM(${startColLetter}$rowNum:${endColLetter}$rowNum)")
                }
                setCell("${MasuSheetConfig.formatColumnLabel(tC0)}$totalRowNum", vp.cellAt(totalRowIdx, tC0)?.displayText?.takeIf { it.isNotBlank() } ?: if (isEn) "Total" else "合計")
                setCell("${letStr}$totalRowNum", "=SUM(${letStr}${dStartR + 1}:${letStr}${dEndR + 1})")
                styleRange("${letStr}${hdrR + 1}:${letStr}${hdrR + 1}", bgHex = "#1F4E79", fgHex = "#FFFFFF", bold = true, hAlign = "center")
                styleRange("${letStr}${dStartR + 1}:${letStr}$totalRowNum", numFmt = "#,##0", borders = "all")
                sumColLetCreated = letStr
                letStr
            }
            val ratioColIdx = nextFreeCol++
            val ratioColLet = MasuSheetConfig.formatColumnLabel(ratioColIdx)
            setCell("${ratioColLet}${hdrR + 1}", if (isEn) "Share" else "構成比")
            for (r in dStartR..dEndR) {
                val rowNum = r + 1
                setCell("${ratioColLet}$rowNum", "=${sumColLet}$rowNum/$sumColLet$$totalRowNum")
            }
            setCell("${ratioColLet}$totalRowNum", "=SUM(${ratioColLet}${dStartR + 1}:${ratioColLet}${dEndR + 1})")
            styleRange("${ratioColLet}${hdrR + 1}:${ratioColLet}${hdrR + 1}", bgHex = "#1F4E79", fgHex = "#FFFFFF", bold = true, hAlign = "center")
            styleRange("${ratioColLet}${dStartR + 1}:${ratioColLet}$totalRowNum", numFmt = "0.0%", borders = "all")
            bullets.add(
                if (isEn) {
                    "Added Share (%) column in $ratioColLet (=${sumColLet}row/$sumColLet$$totalRowNum)"
                } else {
                    "${ratioColLet}列に構成比（=${sumColLet}行/$sumColLet$$totalRowNum）とパーセント書式 (0.0%) を設定"
                }
            )
        }

        if (wantsTax) {
            val baseColLet = sumColLetCreated ?: primaryValColLetter
            val taxColIdx = nextFreeCol++
            val inclColIdx = nextFreeCol++
            val taxColLet = MasuSheetConfig.formatColumnLabel(taxColIdx)
            val inclColLet = MasuSheetConfig.formatColumnLabel(inclColIdx)
            setCell("${taxColLet}${hdrR + 1}", if (isEn) "Tax (10%)" else "消費税(10%)")
            setCell("${inclColLet}${hdrR + 1}", if (isEn) "Tax-Incl." else "税込金額")
            for (r in dStartR..dEndR) {
                val rowNum = r + 1
                setCell("${taxColLet}$rowNum", "=ROUND(${baseColLet}$rowNum*0.1,0)")
                setCell("${inclColLet}$rowNum", "=${baseColLet}$rowNum+${taxColLet}$rowNum")
            }
            val lastRowForTax = if (hasTotalRowOut) {
                setCell("${taxColLet}$totalRowNum", "=SUM(${taxColLet}${dStartR + 1}:${taxColLet}${dEndR + 1})")
                setCell("${inclColLet}$totalRowNum", "=SUM(${inclColLet}${dStartR + 1}:${inclColLet}${dEndR + 1})")
                totalRowNum
            } else {
                dEndR + 1
            }
            styleRange("${taxColLet}${hdrR + 1}:${inclColLet}${hdrR + 1}", bgHex = "#1F4E79", fgHex = "#FFFFFF", bold = true, hAlign = "center")
            styleRange("${taxColLet}${dStartR + 1}:${inclColLet}$lastRowForTax", numFmt = "¥#,##0", borders = "all")
            bullets.add(
                if (isEn) {
                    "Added 10% Tax ($taxColLet) and Tax-Included ($inclColLet) formula columns"
                } else {
                    "${taxColLet}列に「消費税(10%)」、${inclColLet}列に「税込金額」の数式列を追加"
                }
            )
        }

        if (wantsRank) {
            val rankTargetLet = sumColLetCreated ?: primaryValColLetter
            val rankColIdx = nextFreeCol++
            val rankColLet = MasuSheetConfig.formatColumnLabel(rankColIdx)
            setCell("${rankColLet}${hdrR + 1}", if (isEn) "Rank" else "順位")
            for (r in dStartR..dEndR) {
                val rowNum = r + 1
                setCell("${rankColLet}$rowNum", "=RANK(${rankTargetLet}$rowNum, ${rankTargetLet}$${dStartR + 1}:${rankTargetLet}$${dEndR + 1})")
            }
            styleRange("${rankColLet}${hdrR + 1}:${rankColLet}${hdrR + 1}", bgHex = "#1F4E79", fgHex = "#FFFFFF", bold = true, hAlign = "center")
            styleRange("${rankColLet}${hdrR + 1}:${rankColLet}${dEndR + 1}", borders = "all", hAlign = "center")
            bullets.add(
                if (isEn) "Added Rank column ($rankColLet) using RANK()" else "${rankColLet}列に RANK 関数による順位列を追加"
            )
        }

        val finalEndColIdx = (nextFreeCol - 1).coerceAtLeast(tC1)
        val finalEndRowNum = if (hasTotalRowOut) totalRowNum else (dEndR + 1)
        if (wantsFormat) {
            val fullRange = "${MasuSheetConfig.formatColumnLabel(tC0)}${hdrR + 1}:${MasuSheetConfig.formatColumnLabel(finalEndColIdx)}$finalEndRowNum"
            val hdrRange = "${MasuSheetConfig.formatColumnLabel(tC0)}${hdrR + 1}:${MasuSheetConfig.formatColumnLabel(finalEndColIdx)}${hdrR + 1}"
            styleRange(hdrRange, bgHex = "#1F4E79", fgHex = "#FFFFFF", bold = true, hAlign = "center")
            styleRange(fullRange, borders = "all")
            val cfColLet = sumColLetCreated ?: primaryValColLetter
            val cfRange = "${cfColLet}${dStartR + 1}:${cfColLet}${dEndR + 1}"
            cmds.add("format.conditional" to "{\"range\":\"$cfRange\",\"type\":\"colorScale\"}")
            bullets.add(
                if (isEn) {
                    "Styled header ($hdrRange), grid borders ($fullRange), and color scale ($cfRange)"
                } else {
                    "見出し行 ($hdrRange) の配色、表全体 ($fullRange) の格子罫線、数値列 ($cfRange) のカラースケール条件付き書式を適用"
                }
            )
        }

        if (wantsSort) {
            val desc = prompt.contains("降順") || prompt.contains("高い順") || prompt.contains("大きい順") || pLower.contains("desc")
            val sortRange = "${MasuSheetConfig.formatColumnLabel(tC0)}${hdrR + 1}:${MasuSheetConfig.formatColumnLabel(finalEndColIdx)}${dEndR + 1}"
            cmds.add(
                (if (desc) "data.sortDescending" else "data.sortAscending") to
                    "{\"range\":\"$sortRange\",\"col\":$lastNumCol}"
            )
            bullets.add(
                if (isEn) {
                    "Sorted data ($sortRange) in ${if (desc) "descending" else "ascending"} order"
                } else {
                    "データ範囲 ($sortRange) を${if (desc) "降順" else "昇順"}に並べ替え"
                }
            )
        }

        return AiTurnExecutionResult(
            replyText = if (isEn) {
                "Applied requested table edits (\"$prompt\") to sheet \"${vp.sheetName}\"."
            } else {
                "現在のシート「${vp.sheetName}」に対して、「$prompt」の編集を実行しました。"
            },
            summaryBullets = bullets.ifEmpty {
                listOf(if (isEn) "Applied formulas and formatting to the active table" else "現在のシートの表に数式と書式を適用しました")
            },
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
            replyText = if (isEn) {
                "Analyzed existing data ($rangeStr) on \"${vp.sheetName}\" without overwriting cells:\n" +
                    "• Populated cells: ${nonEmpty.size} (Numeric: ${nums.size})\n" +
                    "• Sum: ${String.format("%,.2f", sum)} / Average: ${String.format("%,.2f", avg)} / Max: ${String.format("%,.2f", maxVal)} / Min: ${String.format("%,.2f", minVal)}\n" +
                    "(Tip: ask \"Add Sum & Average\", \"Create chart\", or \"Create Inventory Sheet on new sheet\")"
            } else {
                "現在のシート「${vp.sheetName}」の既存データ（$rangeStr）を保護したまま集計・分析しました：\n" +
                    "・入力済みセル数: ${nonEmpty.size} セル（数値データ: ${nums.size} 件）\n" +
                    "・合計: ${String.format("%,.2f", sum)} / 平均: ${String.format("%,.2f", avg)} / 最大: ${String.format("%,.2f", maxVal)} / 最小: ${String.format("%,.2f", minVal)}\n" +
                    "（※既存の表へ列を追加する場合は「合計・平均を追加」「グラフ化して」、新しい表を作る場合は「新しいシートに〇〇表を作成して」と指示してください）"
            },
            summaryBullets = if (isEn) {
                listOf(
                    "Preserved existing cells (no overwrite): $rangeStr",
                    "Sum = ${String.format("%,.0f", sum)}, Average = ${String.format("%,.1f", avg)}"
                )
            } else {
                listOf(
                    "既存データを維持（上書き消去なし）: $rangeStr",
                    "合計 = ${String.format("%,.0f", sum)}、平均 = ${String.format("%,.1f", avg)}"
                )
            },
            commands = emptyList()
        )
    }

    // CASE B: General-Purpose Custom Worksheet Synthesis when blank OR explicitly requested to create/build a table
    val spec = synthesizeCustomSheetSpec(prompt, clarifyingAnswers, isEn)
    val includeKpiCards = !(answersCombined.contains("明細テーブルのみ") ||
        answersCombined.contains("シンプルな一覧テーブル") ||
        answersLower.contains("simple table"))
    val plainUnstyled = answersCombined.contains("装飾なし") || answersLower.contains("plain unstyled")
    val skipColorScale = plainUnstyled ||
        answersCombined.contains("罫線と見出し") ||
        answersLower.contains("standard borders")

    val wantsSeparateSheetTab = nonEmpty.isNotEmpty() &&
        (prompt.contains("新しいシート") || prompt.contains("別シート") || pLower.contains("new sheet") ||
            answersCombined.contains("新しいシート") || answersLower.contains("new sheet"))
    if (wantsSeparateSheetTab) {
        cmds.add("home.insertSheet" to "{}")
    } else if (nonEmpty.isNotEmpty()) {
        val clearLastCol = MasuSheetConfig.formatColumnLabel(maxOf(maxC, spec.headers.size + 2))
        val clearLastRow = maxOf(maxR + 2, 35)
        cmds.add("edit.clearAll" to "{\"range\":\"A1:${clearLastCol}$clearLastRow\"}")
    }

    // 1. Title Banner (Row 1: A1:lastColLet1)
    val lastColLet = MasuSheetConfig.formatColumnLabel(spec.headers.size - 1)
    setCell("A1", spec.title)
    if (!plainUnstyled) {
        styleRange("A1:${lastColLet}1", bgHex = spec.primaryHex, fgHex = "#FFFFFF", bold = true)
    } else {
        styleRange("A1:${lastColLet}1", bold = true)
    }
    cmds.add("format.fontSize" to "{\"range\":\"A1\",\"size\":14}")

    // 2. Subtitle / Metadata (Row 2)
    val answerSummary = if (clarifyingAnswers.isEmpty()) spec.subtitle else clarifyingAnswers.values.joinToString(" / ")
    setCell("A2", if (isEn) "Basis: $answerSummary" else "作成基準: $answerSummary")
    if (!plainUnstyled) {
        styleRange("A2:${lastColLet}2", bgHex = spec.softBgHex, fgHex = "#334155", bold = false)
    }

    // 3. Optional KPI Summary Cards (Row 4) + Main Table Placement
    val headerRowNum = if (includeKpiCards) 7 else 4
    val dataStartRowNum = headerRowNum + 1
    val dataEndRowNum = dataStartRowNum + spec.rows.size - 1
    val totalRowNum = dataEndRowNum + 1
    val primaryValueColLetter = MasuSheetConfig.formatColumnLabel(spec.primaryNumericColIndex)

    if (includeKpiCards) {
        setCell("A4", spec.kpi1Label)
        setCell("B4", "=SUM(${primaryValueColLetter}$dataStartRowNum:${primaryValueColLetter}$dataEndRowNum)")
        setCell("C4", spec.kpi2Label)
        setCell("D4", "=AVERAGE(${primaryValueColLetter}$dataStartRowNum:${primaryValueColLetter}$dataEndRowNum)")
        setCell("E4", spec.kpi3Label)
        setCell("F4", "=MAX(${primaryValueColLetter}$dataStartRowNum:${primaryValueColLetter}$dataEndRowNum)")

        if (!plainUnstyled) {
            styleRange("A4:F4", bgHex = "#F1F5F9", fgHex = "#0F172A", bold = true, borders = "all")
        } else {
            styleRange("A4:F4", bold = true, borders = "all")
        }
        styleRange("B4:B4", numFmt = spec.numberFormat)
        styleRange("D4:D4", numFmt = spec.numberFormat)
        styleRange("F4:F4", numFmt = spec.numberFormat)
    }

    // 4. Main Table Headers
    spec.headers.forEachIndexed { cIdx, hText ->
        val colLet = MasuSheetConfig.formatColumnLabel(cIdx)
        setCell("$colLet$headerRowNum", hText)
    }
    if (!plainUnstyled) {
        styleRange("A$headerRowNum:$lastColLet$headerRowNum", bgHex = spec.primaryHex, fgHex = "#FFFFFF", bold = true, borders = "all", hAlign = "center")
    } else {
        styleRange("A$headerRowNum:$lastColLet$headerRowNum", bold = true, borders = "all", hAlign = "center")
    }

    // 5. Data Rows with Live Formulas (dataStartRowNum .. dataEndRowNum)
    spec.rows.forEachIndexed { rOffset, rowCells ->
        val rNum = dataStartRowNum + rOffset
        val rowBg = if (plainUnstyled) null else if (rOffset % 2 == 1) "#F8FAFC" else "#FFFFFF"
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
    setCell("A$totalRowNum", if (isEn) "Total / Summary" else "合計 / 集計")
    for (cIdx in 1 until spec.headers.size) {
        val colLet = MasuSheetConfig.formatColumnLabel(cIdx)
        val colFormula = spec.totalRowFormulas.getOrNull(cIdx)
            ?.replace("{START}", dataStartRowNum.toString())
            ?.replace("{END}", dataEndRowNum.toString())
            ?.replace("{TOTAL_ROW}", totalRowNum.toString())
            ?.replace("{COL}", colLet)
            ?: ""
        if (colFormula.isNotBlank()) {
            setCell("$colLet$totalRowNum", colFormula)
        }
    }
    if (!plainUnstyled) {
        styleRange("A$totalRowNum:$lastColLet$totalRowNum", bgHex = spec.softBgHex, fgHex = "#0F172A", bold = true, borders = "all")
    } else {
        styleRange("A$totalRowNum:$lastColLet$totalRowNum", bold = true, borders = "all")
    }

    // 7. Number Formats & Conditional Formatting on Currency / Numeric / Percent Columns
    for (cIdx in spec.currencyColIndices) {
        val colLet = MasuSheetConfig.formatColumnLabel(cIdx)
        styleRange("$colLet$dataStartRowNum:$colLet$totalRowNum", numFmt = "¥#,##0")
    }
    for (cIdx in spec.numericColIndices) {
        if (cIdx in spec.currencyColIndices) continue
        val colLet = MasuSheetConfig.formatColumnLabel(cIdx)
        styleRange("$colLet$dataStartRowNum:$colLet$totalRowNum", numFmt = "#,##0")
    }
    for (cIdx in spec.percentColIndices) {
        val colLet = MasuSheetConfig.formatColumnLabel(cIdx)
        styleRange("$colLet$dataStartRowNum:$colLet$totalRowNum", numFmt = "0.0%")
    }
    if (!skipColorScale) {
        cmds.add(
            "format.conditional" to "{\"range\":\"${primaryValueColLetter}$dataStartRowNum:${primaryValueColLetter}$dataEndRowNum\",\"type\":\"colorScale\"}"
        )
    }

    // 8. Readable Column Widths + Place Active Selection on Main Table Header (A7 / A4) so immediate PivotTable / Filter works cleanly
    cmds.add("home.columnWidth" to "{\"range\":\"A1:A1\",\"width\":196}")
    if (spec.headers.size >= 2) {
        cmds.add("home.columnWidth" to "{\"range\":\"B1:${lastColLet}1\",\"width\":104}")
    }
    cmds.add("selection.set" to "{\"active\":\"A$headerRowNum\",\"range\":\"A$headerRowNum\"}")

    if (wantsChart) {
        val chartType = resolveRequestedChartType(prompt)
        val chartParams = JSONObject().apply {
            put("type", chartType)
            put("startRow", headerRowNum - 1)
            put("startCol", 0)
            put("endRow", dataEndRowNum - 1)
            put("endCol", spec.primaryNumericColIndex)
            put("title", if (isEn) "${spec.title} Chart" else "${spec.title} グラフ")
        }.toString()
        cmds.add("ui.insertChart" to chartParams)
    }

    val numFmtSummary = if (spec.currencyColIndices.isNotEmpty() && spec.numericColIndices.isNotEmpty()) {
        if (isEn) "Currency (¥#,##0) & Quantity (#,##0)" else "金額 (¥#,##0)・数量 (#,##0)"
    } else {
        spec.numberFormat
    }
    return AiTurnExecutionResult(
        replyText = if (isEn) {
            "Created \"${spec.title}\" with ${if (includeKpiCards) "top KPI summary cards (SUM / AVERAGE / MAX), " else ""}formula-driven data rows${if (!skipColorScale) ", color-scale conditional formatting" else ""}${if (wantsChart) ", and a linked chart" else ""}."
        } else {
            "「${spec.title}」を作成しました。${if (includeKpiCards) "上部に KPI サマリーカード（SUM / AVERAGE / MAX）、下部に" else ""}数式連動の明細テーブル${if (!skipColorScale) "と条件付き書式（カラースケール）" else ""}${if (wantsChart) "、および連動グラフ" else ""}を配置しています。"
        },
        summaryBullets = if (isEn) {
            listOfNotNull(
                if (includeKpiCards) "Title & KPI Summary (A1:F4) with live =SUM, =AVERAGE, =MAX formulas" else null,
                "Main table (A$headerRowNum:$lastColLet$totalRowNum) with ${spec.rows.size} rows across ${spec.headers.joinToString(", ")}",
                "Applied number formats ($numFmtSummary), share/progress (0.0%)${if (!skipColorScale) ", and color scale" else ""}${if (wantsChart) " + chart" else ""}"
            )
        } else {
            listOfNotNull(
                if (includeKpiCards) "タイトル・KPIサマリー（A1:F4）に自動集計数式（=SUM, =AVERAGE, =MAX）を設定" else null,
                "メインテーブル（A$headerRowNum:$lastColLet$totalRowNum）に ${spec.headers.joinToString("・")} の ${spec.rows.size} 行データと数式を挿入",
                "数値表示形式 ($numFmtSummary)・構成比/達成率 (0.0%)${if (!skipColorScale) "・カラースケール条件付き書式" else ""}${if (wantsChart) "・グラフ" else ""}を適用"
            )
        },
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
    val currencyColIndices: List<Int> = emptyList(),
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
    answers: Map<String, String>,
    isEn: Boolean = false
): SynthesizedSheetSpec {
    val p = prompt.trim()
    val pLower = p.lowercase(Locale.ROOT)
    val answersText = answers.values.joinToString(" ")
    val answersLower = answersText.lowercase(Locale.ROOT)
    val requestedRowCount: Int? = when {
        answersText.contains("10行以上") || answersText.contains("10名以上") || answersLower.contains("10+ rows") -> 10
        answersText.contains("8名") -> 8
        answersText.contains("最小限") || answersLower.contains("minimal rows") -> 3
        else -> null
    }

    fun adjustSampleRows(baseRows: List<List<String>>): List<List<String>> {
        val target = requestedRowCount ?: return baseRows
        if (target <= baseRows.size) return baseRows.take(target.coerceAtLeast(1))
        val result = baseRows.toMutableList()
        while (result.size < target) {
            val templateRow = baseRows[result.size % baseRows.size].toMutableList()
            if (templateRow.isNotEmpty() && !templateRow[0].startsWith("=")) {
                templateRow[0] = "${templateRow[0]} (${result.size + 1})"
            }
            result.add(templateRow)
        }
        return result
    }

    // 1. Check if the user explicitly listed custom column names in their prompt (e.g., "列は A, B, C..." or "columns: A, B, C")
    val explicitColsMatch = Regex("""(?:項目|列|カラム|columns?)[は：:]\s*([^\n。]+)""", RegexOption.IGNORE_CASE).find(p)
    if (explicitColsMatch != null) {
        val userCols = explicitColsMatch.groupValues[1]
            .split("、", ",", "・", "/")
            .flatMap { if (it.contains(" ") && !isEn) it.split(" ") else listOf(it) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (userCols.size >= 2) {
            val headers = userCols + if (isEn) listOf("Total", "Share") else listOf("合計", "構成比")
            val numCols = (1 until headers.size - 1).toList()
            val sumEndCol = MasuSheetConfig.formatColumnLabel(headers.size - 3)
            val sumCol = MasuSheetConfig.formatColumnLabel(headers.size - 2)
            val rows = (1..(requestedRowCount ?: 6)).map { idx ->
                val baseRow = mutableListOf(if (isEn) "Item $idx" else "項目 $idx")
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
                subtitle = if (isEn) "Custom columns (${userCols.joinToString(", ")})" else "ユーザー指定カラム構成 (${userCols.joinToString(", ")})",
                primaryHex = "#1E3A8A",
                softBgHex = "#DBEAFE",
                kpi1Label = if (isEn) "Grand Total" else "総合計",
                kpi2Label = if (isEn) "Average" else "平均値",
                kpi3Label = if (isEn) "Maximum" else "最大値",
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
    val rawSpec = when {
        p.contains("在庫") || p.contains("発注") || p.contains("棚卸") || p.contains("商品") ||
            pLower.contains("inventory") || pLower.contains("stock") || pLower.contains("reorder") -> SynthesizedSheetSpec(
            title = if (isEn) "Inventory & Reorder Management" else "在庫・発注管理ワークシート",
            subtitle = if (isEn) "Safety-stock reorder alerts & stock valuation" else "安全在庫基準による自動発注判定・在庫金額集計",
            primaryHex = "#0F766E",
            softBgHex = "#CCFBF1",
            kpi1Label = if (isEn) "Total Stock Value" else "総在庫金額",
            kpi2Label = if (isEn) "Avg Stock Value" else "平均在庫金額",
            kpi3Label = if (isEn) "Max Item Value" else "最大在庫金額",
            headers = if (isEn) {
                listOf("Item Name", "Unit Price", "In Stock", "Reorder Point", "Stock Value", "Share", "Status")
            } else {
                listOf("商品名", "単価", "現在庫数", "発注点", "在庫金額", "構成比", "発注判定")
            },
            rows = if (isEn) {
                listOf(
                    listOf("Wireless Mouse Pro", "3800", "45", "20", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"Reorder\",\"OK\")"),
                    listOf("Mechanical Keyboard", "12400", "12", "15", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"Reorder\",\"OK\")"),
                    listOf("27-inch 4K Monitor", "42800", "8", "10", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"Reorder\",\"OK\")"),
                    listOf("USB-C Docking Station", "9800", "28", "15", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"Reorder\",\"OK\")"),
                    listOf("ANC Headset", "16500", "9", "12", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"Reorder\",\"OK\")"),
                    listOf("FullHD Webcam", "6400", "34", "15", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"Reorder\",\"OK\")")
                )
            } else {
                listOf(
                    listOf("ワイヤレスマウス Pro", "3800", "45", "20", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")"),
                    listOf("メカニカルキーボード JP", "12400", "12", "15", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")"),
                    listOf("27インチ 4Kモニター", "42800", "8", "10", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")"),
                    listOf("USB-C ドッキングステーション", "9800", "28", "15", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")"),
                    listOf("ノイズキャンセリングヘッドセット", "16500", "9", "12", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")"),
                    listOf("Webカメラ FullHD", "6400", "34", "15", "=B{ROW}*C{ROW}", "=E{ROW}/E${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=D{ROW},\"要発注\",\"適正\")")
                )
            },
            totalRowFormulas = listOf("", "=AVERAGE(B{START}:B{END})", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=SUM(E{START}:E{END})", "=SUM(F{START}:F{END})", ""),
            primaryNumericColIndex = 4,
            currencyColIndices = listOf(1, 4),
            numericColIndices = listOf(2, 3),
            percentColIndices = listOf(5),
            numberFormat = "¥#,##0"
        )

        p.contains("シフト") || p.contains("勤務") || p.contains("勤怠") || p.contains("タイムカード") ||
            pLower.contains("shift") || pLower.contains("payroll") || pLower.contains("timesheet") -> SynthesizedSheetSpec(
            title = if (isEn) "Weekly Staff Shift & Payroll Sheet" else "スタッフ週間シフト・人件費試算表",
            subtitle = if (isEn) "Automatic weekly hours & overtime payroll calculation" else "日別勤務時間・週合計時間・概算人件費の自動計算",
            primaryHex = "#1D4ED8",
            softBgHex = "#DBEAFE",
            kpi1Label = if (isEn) "Total Payroll" else "総人件費試算",
            kpi2Label = if (isEn) "Avg Pay" else "平均人件費",
            kpi3Label = if (isEn) "Max Pay" else "最高支給額",
            headers = if (isEn) {
                listOf("Staff Name", "Hourly Rate", "Weekday (h)", "Weekend (h)", "Total Hours", "Est. Pay", "Share")
            } else {
                listOf("スタッフ名", "時給", "平日勤務(h)", "休日勤務(h)", "合計時間(h)", "概算支給額", "構成比")
            },
            rows = listOf(
                listOf(if (isEn) "Kenta Sato" else "佐藤 健太", "1350", "28", "8", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf(if (isEn) "Misaki Suzuki" else "鈴木 美咲", "1300", "24", "6", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf(if (isEn) "Makoto Takahashi" else "高橋 誠", "1450", "32", "8", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf(if (isEn) "Aya Tanaka" else "田中 彩", "1280", "20", "12", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf(if (isEn) "Shota Ito" else "伊藤 翔太", "1400", "30", "4", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf(if (isEn) "Yui Watanabe" else "渡辺 結衣", "1320", "18", "8", "=C{ROW}+D{ROW}", "=B{ROW}*C{ROW}+B{ROW}*1.25*D{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}")
            ),
            totalRowFormulas = listOf("", "=AVERAGE(B{START}:B{END})", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=SUM(E{START}:E{END})", "=SUM(F{START}:F{END})", "=SUM(G{START}:G{END})"),
            primaryNumericColIndex = 5,
            currencyColIndices = listOf(1, 5),
            numericColIndices = listOf(2, 3, 4),
            percentColIndices = listOf(6),
            numberFormat = "¥#,##0"
        )

        p.contains("見積") || p.contains("請求") || p.contains("発注書") || p.contains("納品") ||
            pLower.contains("invoice") || pLower.contains("quote") || pLower.contains("estimate") -> SynthesizedSheetSpec(
            title = if (isEn) "Quotation & Invoice Calculation Sheet" else "御見積書・請求金額計算シート",
            subtitle = if (isEn) "Unit price × quantity, 10% tax & tax-included total" else "品目別単価×数量・消費税(10%)・税込金額自動計算",
            primaryHex = "#1E293B",
            softBgHex = "#E2E8F0",
            kpi1Label = if (isEn) "Total (Tax-Incl.)" else "税込合計金額",
            kpi2Label = if (isEn) "Avg Item (Incl.)" else "1品目平均(税込)",
            kpi3Label = if (isEn) "Max Item (Incl.)" else "最大品目金額",
            headers = if (isEn) {
                listOf("Item / Deliverable", "Unit Price", "Qty", "Subtotal", "Tax (10%)", "Total (Incl.)", "Share")
            } else {
                listOf("品目・作業内容", "単価", "数量", "税抜小計", "消費税(10%)", "税込金額", "構成比")
            },
            rows = listOf(
                listOf(if (isEn) "Requirements & Architecture" else "要件定義・基本設計費", "180000", "1", "=B{ROW}*C{ROW}", "=ROUND(D{ROW}*0.1,0)", "=D{ROW}+E{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf(if (isEn) "Frontend UI Implementation" else "フロントエンド画面開発", "95000", "4", "=B{ROW}*C{ROW}", "=ROUND(D{ROW}*0.1,0)", "=D{ROW}+E{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf(if (isEn) "API & Database Engineering" else "API・データベース構築", "120000", "2", "=B{ROW}*C{ROW}", "=ROUND(D{ROW}*0.1,0)", "=D{ROW}+E{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf(if (isEn) "QA & Integration Testing" else "総合テスト・品質検証", "85000", "2", "=B{ROW}*C{ROW}", "=ROUND(D{ROW}*0.1,0)", "=D{ROW}+E{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}"),
                listOf(if (isEn) "Deployment & Documentation" else "導入支援・ドキュメント作成", "60000", "1", "=B{ROW}*C{ROW}", "=ROUND(D{ROW}*0.1,0)", "=D{ROW}+E{ROW}", "=F{ROW}/F${'$'}{TOTAL_ROW}")
            ),
            totalRowFormulas = listOf("", "", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=SUM(E{START}:E{END})", "=SUM(F{START}:F{END})", "=SUM(G{START}:G{END})"),
            primaryNumericColIndex = 5,
            currencyColIndices = listOf(1, 3, 4, 5),
            numericColIndices = listOf(2),
            percentColIndices = listOf(6),
            numberFormat = "¥#,##0"
        )

        p.contains("プロジェクト") || p.contains("タスク") || p.contains("WBS", ignoreCase = true) || p.contains("進捗") || p.contains("工程") ||
            pLower.contains("project") || pLower.contains("task") -> SynthesizedSheetSpec(
            title = if (isEn) "Project WBS & Progress Tracker" else "プロジェクト WBS・進捗管理表",
            subtitle = if (isEn) "Planned vs actual hours, completion rate & status" else "予定工数・実績工数・消化率・ステータス自動判定",
            primaryHex = "#4338CA",
            softBgHex = "#E0E7FF",
            kpi1Label = if (isEn) "Planned Hours" else "総予定工数(h)",
            kpi2Label = if (isEn) "Avg Task (h)" else "平均予定工数(h)",
            kpi3Label = if (isEn) "Max Task (h)" else "最大タスク工数(h)",
            headers = if (isEn) {
                listOf("Task Name", "Planned (h)", "Actual (h)", "Remaining (h)", "Progress", "Status")
            } else {
                listOf("タスク名", "予定工数(h)", "実績工数(h)", "残工数(h)", "進捗率", "状態判定")
            },
            rows = if (isEn) {
                listOf(
                    listOf("Requirements Discovery", "24", "24", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"Done\",\"In Progress\")"),
                    listOf("UI/UX Prototyping", "40", "36", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"Done\",\"In Progress\")"),
                    listOf("Core Engine Implementation", "80", "64", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"Done\",\"In Progress\")"),
                    listOf("Automated Tests & CI", "32", "20", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"Done\",\"In Progress\")"),
                    listOf("Performance Tuning", "24", "8", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"Done\",\"In Progress\")"),
                    listOf("Release & Docs", "16", "4", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"Done\",\"In Progress\")")
                )
            } else {
                listOf(
                    listOf("企画・要件ヒアリング", "24", "24", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")"),
                    listOf("UI/UXプロトタイプ設計", "40", "36", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")"),
                    listOf("コアエンジン実装", "80", "64", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")"),
                    listOf("自動テスト・CI整備", "32", "20", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")"),
                    listOf("パフォーマンステスト", "24", "8", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")"),
                    listOf("リリース準備・ドキュメント", "16", "4", "=MAX(0,B{ROW}-C{ROW})", "=C{ROW}/B{ROW}", "=IF(C{ROW}>=B{ROW},\"完了\",\"進行中\")")
                )
            },
            totalRowFormulas = listOf("", "=SUM(B{START}:B{END})", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=C{TOTAL_ROW}/B{TOTAL_ROW}", ""),
            primaryNumericColIndex = 1,
            numericColIndices = listOf(1, 2, 3),
            percentColIndices = listOf(4),
            numberFormat = "#,##0"
        )

        p.contains("予算") || p.contains("家計簿") || p.contains("経費") || p.contains("収支") ||
            pLower.contains("budget") || pLower.contains("expense") -> SynthesizedSheetSpec(
            title = if (isEn) "Monthly Budget vs Actual Sheet" else "月次予算・実績比較管理シート",
            subtitle = if (isEn) "Budget, actual spend, variance, utilization (%) & status" else "予算額・実績額・差額・消化率(%)・ステータス判定",
            primaryHex = "#166534",
            softBgHex = "#DCFCE7",
            kpi1Label = if (isEn) "Total Actual" else "実績合計",
            kpi2Label = if (isEn) "Category Avg" else "カテゴリ平均",
            kpi3Label = if (isEn) "Largest Spend" else "最大支出項目",
            headers = if (isEn) {
                listOf("Category", "Budget", "Actual", "Remaining", "Utilization", "Share", "Status")
            } else {
                listOf("費目カテゴリ", "月間予算", "実績額", "予算残高", "消化率", "構成比", "判定")
            },
            rows = if (isEn) {
                listOf(
                    listOf("Housing & Fixed", "95000", "95000", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"Within Budget\",\"Over\")"),
                    listOf("Food & Groceries", "55000", "48200", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"Within Budget\",\"Over\")"),
                    listOf("Utilities & Telecom", "26000", "24500", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"Within Budget\",\"Over\")"),
                    listOf("Transit & Education", "35000", "31800", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"Within Budget\",\"Over\")"),
                    listOf("Healthcare & Reserve", "25000", "19400", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"Within Budget\",\"Over\")"),
                    listOf("Savings & Investment", "60000", "60000", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"Within Budget\",\"Over\")")
                )
            } else {
                listOf(
                    listOf("住居・固定費", "95000", "95000", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")"),
                    listOf("食費・日用品", "55000", "48200", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")"),
                    listOf("水道光熱・通信費", "26000", "24500", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")"),
                    listOf("交通・交際・教養費", "35000", "31800", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")"),
                    listOf("医療・保険・予備費", "25000", "19400", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")"),
                    listOf("貯蓄・積立投資", "60000", "60000", "=B{ROW}-C{ROW}", "=C{ROW}/B{ROW}", "=C{ROW}/C${'$'}{TOTAL_ROW}", "=IF(C{ROW}<=B{ROW},\"予算内\",\"超過\")")
                )
            },
            totalRowFormulas = listOf("", "=SUM(B{START}:B{END})", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=C{TOTAL_ROW}/B{TOTAL_ROW}", "=SUM(F{START}:F{END})", ""),
            primaryNumericColIndex = 2,
            currencyColIndices = listOf(1, 2, 3),
            numericColIndices = emptyList(),
            percentColIndices = listOf(4, 5),
            numberFormat = "¥#,##0"
        )

        else -> {
            // Dynamic topic extraction for any free-form prompt!
            val cleanTopic = p
                .replace(Regex("""^(?:create|build|make|generate)\s+(?:a\s+|an\s+)?""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+(?:sheet|table|template|worksheet)$""", RegexOption.IGNORE_CASE), "")
                .removeSuffix("を作成して")
                .removeSuffix("を作って")
                .removeSuffix("を作成")
                .removeSuffix("の表")
                .removeSuffix("シート")
                .trim()
                .ifBlank { if (isEn) "Analysis" else "データ分析・管理" }
            SynthesizedSheetSpec(
                title = if (isEn) "$cleanTopic Summary Sheet" else "${cleanTopic}・管理集計シート",
                subtitle = if (isEn) "Target vs Actual, Growth, Achievement (%) & Share" else "AI自動設計テーブル（目標・実績・達成率・構成比・自動判定）",
                primaryHex = "#1F4E79",
                softBgHex = "#E0F2FE",
                kpi1Label = if (isEn) "Total Actual" else "実績総合計",
                kpi2Label = if (isEn) "Average" else "項目平均値",
                kpi3Label = if (isEn) "Top Actual" else "最高実績値",
                headers = if (isEn) {
                    listOf("$cleanTopic Item", "Target", "Prior Period", "Current Actual", "Variance", "Achievement", "Share")
                } else {
                    listOf("${cleanTopic}項目", "目標・基準値", "前期実績", "今期実績", "増減差", "達成率", "構成比")
                },
                rows = listOf(
                    listOf("$cleanTopic A", "120000", "108000", "126500", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}"),
                    listOf("$cleanTopic B", "95000", "89000", "98400", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}"),
                    listOf("$cleanTopic C", "150000", "142000", "158200", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}"),
                    listOf("$cleanTopic D", "80000", "76500", "84900", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}"),
                    listOf("$cleanTopic E", "110000", "98000", "114000", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}"),
                    listOf("$cleanTopic F", "75000", "71200", "79500", "=D{ROW}-C{ROW}", "=D{ROW}/B{ROW}", "=D{ROW}/D${'$'}{TOTAL_ROW}")
                ),
                totalRowFormulas = listOf("", "=SUM(B{START}:B{END})", "=SUM(C{START}:C{END})", "=SUM(D{START}:D{END})", "=SUM(E{START}:E{END})", "=D{TOTAL_ROW}/B{TOTAL_ROW}", "=SUM(G{START}:G{END})"),
                primaryNumericColIndex = 3,
                numericColIndices = listOf(1, 2, 3, 4),
                percentColIndices = listOf(5, 6),
                numberFormat = "#,##0"
            )
        }
    }
    return rawSpec.copy(rows = adjustSampleRows(rawSpec.rows))
}
