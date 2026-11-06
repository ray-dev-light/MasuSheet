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
 * [EN] PivotTable Creation (`home.insertSheet`), Field Layout, Cross-Sheet Refresh, & Materialization
 * Implements PivotTable creation on new or existing sheets, Row/Column/Value/Filter field configuration, 11 aggregation functions, style presets, and cross-sheet source refresh.
 *
 * ---
 * [JA / 日本語解説]
 * 新規または既存シートへのピボットテーブル作成、行・列・値・フィルタ配置、11種の集計関数、デザインプリセット、および別シート元表からの再集計 を担うモジュールです。
 * ============================================================================
 */

// =========================================================================
// Object Discovery & Full Spreadsheet PivotTable Engine (v0.14.2-android.23)
// =========================================================================

/**
 * [EN] Returns floating objects belonging to the active sheet, including virtual preview charts on untouched sample dashboard sheets.
 * [JA] 現在のシートに属するオブジェクト一覧を返します（未編集サンプルダッシュボードの初期グラフも含みます）。
 */
fun MasuSheetViewModel.getMaterializedSheetObjectsForCurrentSheet(): List<NativeSheetObject> {
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val sName = cur.viewport.sheetName
    if (sIdx == 2 || sName.contains("Dashboard", ignoreCase = true)) {
        val hasCurrentObjects = cur.sheetObjects.any { it.sheetIndex == sIdx }
        if (!hasCurrentObjects) {
            ensureDefaultSalesDashboardChartsMaterialized()
        }
    } else if (sName == "月間予算" || sName.equals("Budget", ignoreCase = true)) {
        val hasCurrentObjects = cur.sheetObjects.any { it.sheetIndex == sIdx }
        if (!hasCurrentObjects) {
            ensureMonthlyBudgetCharts(sIdx)
        }
    }
    return _uiState.value.sheetObjects.filter { it.sheetIndex == sIdx }
}

/**
 * [EN] Resolves the PivotTable containing the active cell, matching `activePivotTableId`, or residing on the current worksheet.
 * [JA] アクティブセル位置、`activePivotTableId`、または現在のシート上に存在するピボットテーブル定義を解決して返します。
 */
fun MasuSheetViewModel.getActivePivotTable(): PivotTableDefinition? {
    val cur = _uiState.value
    val snap = cur.viewport
    val atCursor = cur.pivotTables.firstOrNull {
        it.containsCell(snap.sheetIndex, snap.activeRow, snap.activeCol)
    }
    if (atCursor != null) return atCursor
    val byActiveIdOnSheet = cur.pivotTables.firstOrNull {
        it.id == cur.activePivotTableId && it.targetSheetIndex == snap.sheetIndex
    }
    if (byActiveIdOnSheet != null) return byActiveIdOnSheet
    return cur.pivotTables.firstOrNull { it.targetSheetIndex == snap.sheetIndex }
}

/**
 * [EN] Hides the empty PivotTable placeholder launcher card and closes the PivotTable Fields pane.
 * [JA] ピボットテーブルのプレースホルダー案内カードを閉じ、フィールド設定ペインを非表示にします。
 */
fun MasuSheetViewModel.onDismissPivotLauncher(pivotId: String? = null) {
    val cur = _uiState.value
    val targetId = pivotId ?: getActivePivotTable()?.id ?: return
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        dismissedPivotLauncherIds = cur.dismissedPivotLauncherIds + targetId,
        activePivotTableId = if (cur.activePivotTableId == targetId) null else cur.activePivotTableId,
        isPivotFieldsPaneOpen = false,
        statusNoticeMessage = if (isEn) {
            "Hidden PivotTable floating launcher button (reopen anytime from Ribbon > Insert > PivotTable)"
        } else {
            "右上の「ピボットテーブルのフィールドを開く」ボタンを非表示にしました（再表示は［挿入］＞［📊 ピボット］またはピボット分析タブから行えます）"
        }
    )
}

/**
 * [EN] Opens or closes the right-side PivotTable Fields configuration pane for the active PivotTable.
 * [JA] アクティブなピボットテーブルの右側「フィールドリスト」設定ペインを開閉します。
 */
fun MasuSheetViewModel.onTogglePivotFieldsPane(open: Boolean? = null) {
    val cur = _uiState.value
    val targetOpen = open ?: !cur.isPivotFieldsPaneOpen
    val activePivot = getActivePivotTable()
    if (targetOpen && activePivot == null) {
        _uiState.value = cur.copy(
            isPivotFieldsPaneOpen = false,
            statusNoticeMessage = if (cur.language == "en") {
                "No PivotTable on this sheet. Tap [Insert] > [📊 Pivot] to create one."
            } else {
                "このシートにはピボットテーブルがありません。［挿入］タブ ＞［📊 ピボット］から作成できます"
            }
        )
        return
    }
    val nextDismissed = if (targetOpen && activePivot != null) {
        cur.dismissedPivotLauncherIds - activePivot.id
    } else {
        cur.dismissedPivotLauncherIds
    }
    _uiState.value = _uiState.value.copy(
        isPivotFieldsPaneOpen = targetOpen,
        activePivotTableId = activePivot?.id ?: cur.activePivotTableId,
        dismissedPivotLauncherIds = nextDismissed,
        statusNoticeMessage = if (targetOpen) {
            if (cur.language == "en") "Opened PivotTable Fields pane" else "右側に「📊 ピボットテーブルのフィールド」ペインを開きました"
        } else {
            if (cur.language == "en") "Closed PivotTable Fields pane" else "「ピボットテーブルのフィールド」ペインを閉じました"
        }
    )
}

/**
 * [EN] Shows or dismisses the Create PivotTable source-range and destination dialog.
 * [JA] 「ピボットテーブルの作成」（元データ範囲・出力先シート指定）ダイアログを開閉します。
 */
fun MasuSheetViewModel.onOpenCreatePivotTableDialog(open: Boolean = true) {
    _uiState.value = _uiState.value.copy(isCreatePivotDialogOpen = open)
}

/**
 * [EN] Opens the Fields pane if the cursor is already inside a PivotTable, opens the creation dialog when requested, or creates a PivotTable immediately.
 * [JA] カーソル位置に既存ピボットがあればフィールドペインを開き、新規時は作成ダイアログ表示または即時ピボット集計を実行します。
 */
fun MasuSheetViewModel.onInsertOrOpenPivotTable(
    openCreateDialog: Boolean = false,
    placeOnNewSheet: Boolean = false,
    presetKind: String = "standard"
) {
    val cur = _uiState.value
    val snap = cur.viewport
    if (openCreateDialog) {
        if (getActivePivotTable() == null) {
            onCreatePivotTable(placeOnNewSheet = false, presetKind = presetKind)
        }
        _uiState.value = _uiState.value.copy(
            isCreatePivotDialogOpen = true,
            isPivotFieldsPaneOpen = true,
            activeRibbonTab = "pivotAnalyze"
        )
        return
    }
    val hitPivot = cur.pivotTables.firstOrNull {
        it.containsCell(snap.sheetIndex, snap.activeRow, snap.activeCol)
    } ?: cur.pivotTables.firstOrNull { it.targetSheetIndex == snap.sheetIndex }

    if (hitPivot != null && !placeOnNewSheet && presetKind == "standard") {
        materializePivotTable(hitPivot.id)
        _uiState.value = _uiState.value.copy(
            activePivotTableId = hitPivot.id,
            isPivotFieldsPaneOpen = true,
            dismissedPivotLauncherIds = _uiState.value.dismissedPivotLauncherIds - hitPivot.id,
            activeRibbonTab = "pivotAnalyze",
            statusNoticeMessage = if (cur.language == "en") {
                "Selected ${hitPivot.name} & opened PivotTable Fields pane"
            } else {
                "「${hitPivot.name}」を選択し、右側にピボットテーブルのフィールド ペインを表示しました"
            }
        )
        return
    }
    onCreatePivotTable(
        placeOnNewSheet = placeOnNewSheet,
        presetKind = presetKind
    )
}

/**
 * [EN] Extracts `(uniqueHeaders, numericFieldSet, rowRecordMaps)` from `(startRow..endRow, startCol..endCol)` on `snap` (skipping `合計`/`Total` footer rows).
 * [JA] 指定セル範囲から重複解消済み見出しリスト・数値列セット・行レコード辞書リスト（末尾の合計行を除外）を抽出します。
 */
internal fun MasuSheetViewModel.extractSourceTableRecords(
    snap: NativeViewportSnapshot,
    startRow: Int,
    startCol: Int,
    endRow: Int,
    endCol: Int
): Triple<List<String>, Set<String>, List<Map<String, String>>> {
    val headers = mutableListOf<String>()
    val usedHeaders = mutableSetOf<String>()
    for (c in startCol..endCol) {
        val rawHeader = snap.cellAt(startRow, c)?.displayText?.trim().orEmpty()
            .ifEmpty { MasuSheetConfig.formatColumnLabel(c) }
        var uniqueHeader = rawHeader
        var suffix = 2
        while (!usedHeaders.add(uniqueHeader)) {
            uniqueHeader = "${rawHeader}_$suffix"
            suffix++
        }
        headers.add(uniqueHeader)
    }
    val records = mutableListOf<Map<String, String>>()
    val numericCounts = IntArray(headers.size)
    val nonBlankCounts = IntArray(headers.size)

    for (r in (startRow + 1)..endRow) {
        val firstColText = snap.cellAt(r, startCol)?.displayText?.trim().orEmpty()
        if (firstColText.equals("合計", ignoreCase = true) ||
            firstColText.equals("総計", ignoreCase = true) ||
            firstColText.equals("Total", ignoreCase = true) ||
            firstColText.equals("Grand Total", ignoreCase = true)
        ) {
            continue
        }
        val rowMap = linkedMapOf<String, String>()
        var hasAnyValue = false
        for ((idx, c) in (startCol..endCol).withIndex()) {
            val cell = snap.cellAt(r, c)
            val rawEdit = cell?.editText?.trim().orEmpty()
            val disp = cell?.displayText?.trim().orEmpty()
            val cleanedNum = disp.replace("¥", "").replace("$", "").replace(",", "").replace("%", "").trim()
            val valueStr = if (cleanedNum.toDoubleOrNull() != null) {
                if (disp.endsWith("%") && !rawEdit.startsWith("=") && rawEdit.toDoubleOrNull() != null) {
                    rawEdit
                } else {
                    cleanedNum
                }
            } else {
                disp.ifEmpty { rawEdit }
            }
            if (valueStr.isNotEmpty()) {
                hasAnyValue = true
                nonBlankCounts[idx]++
                if (valueStr.toDoubleOrNull() != null) {
                    numericCounts[idx]++
                }
            }
            rowMap[headers[idx]] = valueStr
        }
        if (hasAnyValue) {
            records.add(rowMap)
        }
    }
    val numericFieldSet = mutableSetOf<String>()
    for (i in headers.indices) {
        if (nonBlankCounts[i] > 0 && numericCounts[i] * 2 >= nonBlankCounts[i]) {
            numericFieldSet.add(headers[i])
        }
    }
    return Triple(headers, numericFieldSet, records)
}

/**
 * [EN] Creates (or updates) a `PivotTableDefinition` from `sourceRangeText` on either a new worksheet or `targetAddress`, then materializes its cells.
 * [JA] 指定された元データ範囲から新規シートまたは指定セル位置にピボットテーブル定義を作成し、集計結果をセルへ展開します。
 */
fun MasuSheetViewModel.onCreatePivotTable(
    sourceRangeText: String = "",
    placeOnNewSheet: Boolean = false,
    targetAddress: String = "",
    presetKind: String = "standard"
): PivotTableDefinition? {
    val cur = _uiState.value
    val snap = cur.viewport
    val existingOnSheet = cur.pivotTables.firstOrNull { it.targetSheetIndex == snap.sheetIndex }

    // Determine source bounds
    val srcBounds: IntArray = if (existingOnSheet != null && sourceRangeText.isBlank() &&
        existingOnSheet.containsCell(snap.sheetIndex, snap.activeRow, snap.activeCol)
    ) {
        intArrayOf(
            existingOnSheet.sourceStartRow,
            existingOnSheet.sourceStartRow + 1,
            existingOnSheet.sourceEndRow,
            existingOnSheet.sourceStartCol,
            existingOnSheet.sourceEndCol
        )
    } else if (snap.sheetName == "月間予算" && sourceRangeText.isBlank()) {
        intArrayOf(10, 11, 23, 0, 5)
    } else {
        val cleanRange = sourceRangeText.substringAfterLast('!').trim()
        val parts = cleanRange.split(':')
        val p0 = if (parts.isNotEmpty()) parseSingleCellRef(parts[0]) else null
        val p1 = if (parts.size >= 2) parseSingleCellRef(parts[1]) else p0
        if (p0 != null && p1 != null && (p0.first != p1.first || p0.second != p1.second)) {
            val r0 = minOf(p0.first, p1.first)
            val r1 = maxOf(p0.first, p1.first)
            val c0 = minOf(p0.second, p1.second)
            val c1 = maxOf(p0.second, p1.second)
            intArrayOf(r0, (r0 + 1).coerceAtMost(r1), r1, c0, c1)
        } else {
            detectTableBoundsAt(snap, snap.activeRow, snap.activeCol)
        }
    }

    val srcR0 = srcBounds[0]
    val srcR1 = srcBounds[2]
    val srcC0 = srcBounds[3]
    val srcC1 = srcBounds[4]

    val (extractedHeaders, extractedNumeric, extractedRecords) = extractSourceTableRecords(
        snap = snap,
        startRow = srcR0,
        startCol = srcC0,
        endRow = srcR1,
        endCol = srcC1
    )

    val isMonthlyBudgetSheet = snap.sheetName == "月間予算"
    val headers = if (extractedHeaders.isNotEmpty() && extractedRecords.isNotEmpty()) {
        extractedHeaders
    } else if (existingOnSheet != null && existingOnSheet.availableFields.isNotEmpty()) {
        existingOnSheet.availableFields
    } else if (isMonthlyBudgetSheet) {
        listOf("カテゴリ", "項目", "予算(円)", "実績(円)", "差額(円)")
    } else {
        extractedHeaders.ifEmpty { listOf("項目", "値") }
    }
    val numericFields = if (extractedRecords.isNotEmpty()) {
        extractedNumeric
    } else if (existingOnSheet != null && existingOnSheet.numericFields.isNotEmpty()) {
        existingOnSheet.numericFields
    } else if (isMonthlyBudgetSheet) {
        setOf("予算(円)", "実績(円)", "差額(円)")
    } else {
        extractedNumeric
    }
    val records = if (extractedRecords.isNotEmpty()) {
        extractedRecords
    } else {
        existingOnSheet?.sourceRecords ?: emptyList()
    }

    val textFields = headers.filter { it !in numericFields }
    val numFieldsList = headers.filter { it in numericFields }

    val defaultRowFields: List<String>
    val defaultColFields: List<String>
    val defaultFilterFields: List<String>
    val defaultValueFields: List<PivotValueFieldSpec>

    when (presetKind) {
        "hierarchy" -> {
            defaultRowFields = textFields.take(2).ifEmpty { headers.take(1) }
            defaultColFields = emptyList()
            defaultFilterFields = emptyList()
            defaultValueFields = numFieldsList.filterNot { it.contains("割合") || it.contains("率") }
                .ifEmpty { numFieldsList }
                .take(3)
                .map { PivotValueFieldSpec(it, PivotAggFunction.SUM) }
                .ifEmpty { listOf(PivotValueFieldSpec(headers.last(), PivotAggFunction.COUNT)) }
        }
        "crosstab" -> {
            defaultRowFields = textFields.take(1).ifEmpty { headers.take(1) }
            defaultColFields = if (textFields.size >= 2) listOf(textFields[1]) else emptyList()
            defaultFilterFields = emptyList()
            val primaryVal = numFieldsList.firstOrNull { !it.contains("割合") && !it.contains("率") }
                ?: numFieldsList.firstOrNull()
                ?: headers.last()
            defaultValueFields = listOf(
                PivotValueFieldSpec(
                    primaryVal,
                    if (primaryVal in numericFields) PivotAggFunction.SUM else PivotAggFunction.COUNT
                )
            )
        }
        "average_percent" -> {
            defaultRowFields = textFields.take(1).ifEmpty { headers.take(1) }
            defaultColFields = emptyList()
            defaultFilterFields = if (textFields.size >= 2) listOf(textFields[1]) else emptyList()
            val primaryVal = numFieldsList.firstOrNull { !it.contains("割合") && !it.contains("率") }
                ?: numFieldsList.firstOrNull()
                ?: headers.last()
            val secondaryVal = numFieldsList.drop(1).firstOrNull() ?: primaryVal
            defaultValueFields = listOf(
                PivotValueFieldSpec(primaryVal, PivotAggFunction.SUM),
                PivotValueFieldSpec(primaryVal, PivotAggFunction.PERCENT_OF_TOTAL),
                PivotValueFieldSpec(secondaryVal, PivotAggFunction.AVERAGE)
            )
        }
        else -> {
            defaultRowFields = textFields.take(1).ifEmpty { headers.take(1) }
            defaultColFields = emptyList()
            defaultFilterFields = emptyList()
            defaultValueFields = numFieldsList.filterNot { it.contains("割合") || it.contains("率") }
                .ifEmpty { numFieldsList }
                .take(3)
                .map { PivotValueFieldSpec(it, PivotAggFunction.SUM) }
                .ifEmpty { listOf(PivotValueFieldSpec(headers.last(), PivotAggFunction.COUNT)) }
        }
    }

    val srcSheetIndex = snap.sheetIndex
    val srcSheetName = snap.sheetName

    val destSheetIndex: Int
    val destRow: Int
    val destCol: Int

    if (placeOnNewSheet) {
        val pivotSheetTitle = if (cur.language == "en") {
            "Pivot_${cur.pivotTables.size + 1}"
        } else {
            "ピボット集計${cur.pivotTables.size + 1}"
        }
        onAddSheet()
        val addedSheetIdx = _uiState.value.viewport.sheetIndex
        if (addedSheetIdx == srcSheetIndex) {
            val isEn = cur.language == "en"
            _uiState.value = _uiState.value.copy(
                errorMessage = if (isEn) {
                    "Failed to create new PivotTable sheet; source worksheet was kept untouched."
                } else {
                    "ピボット用の新規シート作成に失敗したため、元データ保護のため処理を中断しました。"
                }
            )
            return null
        }
        onRenameSheetAt(addedSheetIdx, pivotSheetTitle)
        destSheetIndex = _uiState.value.viewport.sheetIndex
        destRow = 1
        destCol = 0
    } else {
        destSheetIndex = srcSheetIndex
        val parsedTarget = parseSingleCellRef(targetAddress.substringAfterLast('!'))
        if (parsedTarget != null) {
            destRow = parsedTarget.first
            destCol = parsedTarget.second
        } else if (existingOnSheet != null) {
            destRow = existingOnSheet.targetStartRow
            destCol = existingOnSheet.targetStartCol
        } else if (srcSheetName == "月間予算") {
            destRow = 10
            destCol = 7
        } else {
            destRow = srcR0.coerceAtLeast(1)
            destCol = (srcC1 + 2).coerceAtMost(14)
        }
    }

    val pivotId = if (!placeOnNewSheet && existingOnSheet != null) {
        existingOnSheet.id
    } else {
        "pivot_${destSheetIndex}_${System.currentTimeMillis() % 100000}"
    }
    val pivotName = if (!placeOnNewSheet && existingOnSheet != null) {
        existingOnSheet.name
    } else {
        if (cur.language == "en") "PivotTable${cur.pivotTables.size + 1}" else "ピボットテーブル${cur.pivotTables.size + 1}"
    }

    val newPivot = PivotTableDefinition(
        id = pivotId,
        name = pivotName,
        sourceSheetIndex = srcSheetIndex,
        sourceSheetName = srcSheetName,
        sourceStartRow = srcR0,
        sourceStartCol = srcC0,
        sourceEndRow = srcR1,
        sourceEndCol = srcC1,
        targetSheetIndex = destSheetIndex,
        targetStartRow = destRow,
        targetStartCol = destCol,
        lastRenderedStartRow = existingOnSheet?.lastRenderedStartRow ?: destRow,
        lastRenderedEndRow = existingOnSheet?.lastRenderedEndRow ?: (destRow + 8),
        lastRenderedEndCol = existingOnSheet?.lastRenderedEndCol ?: (destCol + 3),
        availableFields = headers,
        numericFields = numericFields,
        sourceRecords = records,
        filterFields = defaultFilterFields,
        columnFields = defaultColFields,
        rowFields = defaultRowFields,
        valueFields = defaultValueFields,
        stylePreset = existingOnSheet?.stylePreset ?: cur.pivotStylePreset,
        reportLayout = if (presetKind == "hierarchy") "tabular" else (existingOnSheet?.reportLayout ?: cur.pivotReportLayout),
        subtotalsMode = existingOnSheet?.subtotalsMode ?: cur.pivotSubtotalsMode,
        grandTotalsMode = existingOnSheet?.grandTotalsMode ?: cur.pivotGrandTotalsMode,
        repeatLabels = existingOnSheet?.repeatLabels ?: cur.pivotRepeatLabels,
        blankRows = existingOnSheet?.blankRows ?: cur.pivotBlankRows,
        bandedRows = existingOnSheet?.bandedRows ?: cur.pivotBandedRows,
        bandedCols = existingOnSheet?.bandedCols ?: cur.pivotBandedCols
    )

    val currentPivots = _uiState.value.pivotTables.filterNot { it.id == pivotId } + newPivot
    _uiState.value = _uiState.value.copy(
        pivotTables = currentPivots,
        activePivotTableId = pivotId,
        isPivotFieldsPaneOpen = true,
        isCreatePivotDialogOpen = false,
        dismissedPivotLauncherIds = _uiState.value.dismissedPivotLauncherIds - pivotId,
        activeRibbonTab = "pivotAnalyze"
    )
    materializePivotTable(pivotId)
    onSelectCell(destRow, destCol)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        statusNoticeMessage = if (isEn) {
            "Created ${newPivot.name} (${newPivot.sourceRangeLabel}) and opened PivotTable Fields"
        } else {
            "「${newPivot.name}」（元データ: ${newPivot.sourceRangeLabel}）を作成し、ピボットテーブルのフィールドを開きました"
        }
    )
    return _uiState.value.pivotTables.firstOrNull { it.id == pivotId }
}

/**
 * [EN] Switches to `pivotId`'s target worksheet, selects its top-left cell, and opens the PivotTable Fields pane.
 * [JA] 指定された `pivotId` の配置シートへ移動して先頭セルを選択し、ピボットフィールド設定ペインを表示します。
 */
fun MasuSheetViewModel.onSelectActivePivotTable(pivotId: String) {
    val pivot = _uiState.value.pivotTables.firstOrNull { it.id == pivotId } ?: return
    if (_uiState.value.viewport.sheetIndex != pivot.targetSheetIndex) {
        onSelectSheet(pivot.targetSheetIndex)
    }
    onSelectCell(pivot.targetStartRow, pivot.targetStartCol)
    _uiState.value = _uiState.value.copy(
        activePivotTableId = pivot.id,
        isPivotFieldsPaneOpen = true,
        dismissedPivotLauncherIds = _uiState.value.dismissedPivotLauncherIds - pivot.id
    )
}

/**
 * [EN] Applies `transform` to the active `PivotTableDefinition`, syncs ribbon layout flags, re-renders the PivotTable cells, and posts a localized notice.
 * [JA] アクティブなピボット定義に `transform` を適用し、リボン表示状態を同期した上でシート上の集計表を再展開します。
 */
internal fun MasuSheetViewModel.updateActivePivotAndMaterialize(
    noticeJa: String,
    noticeEn: String,
    transform: (PivotTableDefinition) -> PivotTableDefinition
) {
    val cur = _uiState.value
    val active = getActivePivotTable()
    if (active == null) {
        val dummy = PivotTableDefinition(
            id = "dummy",
            name = "Dummy",
            sourceSheetIndex = cur.viewport.sheetIndex,
            sourceSheetName = cur.viewport.sheetName,
            sourceStartRow = 0,
            sourceStartCol = 0,
            sourceEndRow = 0,
            sourceEndCol = 0,
            targetSheetIndex = cur.viewport.sheetIndex,
            targetStartRow = 0,
            targetStartCol = 0,
            lastRenderedStartRow = 0,
            lastRenderedEndRow = 0,
            lastRenderedEndCol = 0,
            availableFields = emptyList(),
            numericFields = emptySet(),
            sourceRecords = emptyList(),
            filterFields = emptyList(),
            columnFields = emptyList(),
            rowFields = emptyList(),
            valueFields = emptyList(),
            stylePreset = cur.pivotStylePreset,
            reportLayout = cur.pivotReportLayout,
            subtotalsMode = cur.pivotSubtotalsMode,
            grandTotalsMode = cur.pivotGrandTotalsMode,
            repeatLabels = cur.pivotRepeatLabels,
            blankRows = cur.pivotBlankRows,
            bandedRows = cur.pivotBandedRows,
            bandedCols = cur.pivotBandedCols
        )
        val updatedDummy = transform(dummy)
        val isEn = cur.language == "en"
        _uiState.value = cur.copy(
            pivotSubtotalsMode = updatedDummy.subtotalsMode,
            pivotGrandTotalsMode = updatedDummy.grandTotalsMode,
            pivotReportLayout = updatedDummy.reportLayout,
            pivotRepeatLabels = updatedDummy.repeatLabels,
            pivotBlankRows = updatedDummy.blankRows,
            pivotBandedRows = updatedDummy.bandedRows,
            pivotBandedCols = updatedDummy.bandedCols,
            pivotStylePreset = updatedDummy.stylePreset,
            statusNoticeMessage = if (isEn) noticeEn else noticeJa
        )
        return
    }
    val updated = transform(active)
    val nextList = cur.pivotTables.map { if (it.id == updated.id) updated else it }
    _uiState.value = cur.copy(
        pivotTables = nextList,
        activePivotTableId = updated.id,
        pivotSubtotalsMode = updated.subtotalsMode,
        pivotGrandTotalsMode = updated.grandTotalsMode,
        pivotReportLayout = updated.reportLayout,
        pivotRepeatLabels = updated.repeatLabels,
        pivotBlankRows = updated.blankRows,
        pivotBandedRows = updated.bandedRows,
        pivotBandedCols = updated.bandedCols,
        pivotStylePreset = updated.stylePreset
    )
    materializePivotTable(updated.id)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        statusNoticeMessage = if (isEn) noticeEn else noticeJa
    )
}

/**
 * [EN] Checks or unchecks `fieldName` in the PivotTable Fields list (auto-placing numeric fields into Values and text fields into Rows).
 * [JA] ピボットフィールドのチェック状態を切り替え、ON時は数値列を「値」エリア、文字列列を「行」エリアへ自動配置します。
 */
fun MasuSheetViewModel.onTogglePivotFieldChecked(fieldName: String) {
    val active = getActivePivotTable() ?: return
    val isChecked = active.isFieldActive(fieldName)
    if (isChecked) {
        onAssignPivotFieldToArea(fieldName, "none")
    } else {
        val targetArea = if (fieldName in active.numericFields) "value" else "row"
        onAssignPivotFieldToArea(fieldName, targetArea)
    }
}

/**
 * [EN] Assigns `fieldName` to `row`, `column`, `filter`, `value`, or `none` (removing it from previous areas) and re-materializes the PivotTable.
 * [JA] 指定フィールドを「行」「列」「フィルター」「値」のいずれかのエリアへ移動（または解除）し、ピボット集計を更新します。
 */
fun MasuSheetViewModel.onAssignPivotFieldToArea(fieldName: String, area: String) {
    val cleanArea = area.lowercase().trim()
    updateActivePivotAndMaterialize(
        noticeJa = when (cleanArea) {
            "row" -> "フィールド「$fieldName」を【行】エリアに配置しました"
            "column" -> "フィールド「$fieldName」を【列】エリアに配置しました"
            "filter" -> "フィールド「$fieldName」を【フィルター】エリアに配置しました"
            "value" -> "フィールド「$fieldName」を【値 (集計)】エリアに追加しました"
            else -> "フィールド「$fieldName」をピボット集計から除外しました"
        },
        noticeEn = "Assigned field '$fieldName' to $cleanArea area"
    ) { p ->
        val nextFilters = p.filterFields.filterNot { it == fieldName }.toMutableList()
        val nextCols = p.columnFields.filterNot { it == fieldName }.toMutableList()
        val nextRows = p.rowFields.filterNot { it == fieldName }.toMutableList()
        val nextVals = if (cleanArea == "none") {
            p.valueFields.filterNot { it.fieldName == fieldName }.toMutableList()
        } else {
            p.valueFields.toMutableList()
        }
        when (cleanArea) {
            "filter" -> if (fieldName !in nextFilters) nextFilters.add(fieldName)
            "column" -> if (fieldName !in nextCols) nextCols.add(fieldName)
            "row" -> if (fieldName !in nextRows) nextRows.add(fieldName)
            "value" -> {
                val defaultAgg = if (fieldName in p.numericFields) PivotAggFunction.SUM else PivotAggFunction.COUNT
                if (nextVals.none { it.fieldName == fieldName }) {
                    nextVals.add(PivotValueFieldSpec(fieldName, defaultAgg))
                } else {
                    val existingAggs = nextVals.filter { it.fieldName == fieldName }.map { it.agg }.toSet()
                    val altAgg = PivotAggFunction.values().firstOrNull { it !in existingAggs } ?: PivotAggFunction.AVERAGE
                    nextVals.add(PivotValueFieldSpec(fieldName, altAgg))
                }
            }
        }
        p.copy(
            filterFields = nextFilters,
            columnFields = nextCols,
            rowFields = nextRows,
            valueFields = nextVals
        )
    }
}

/**
 * [EN] Moves `fieldName` up or down (`delta`) within its assigned PivotTable area (`row`, `column`, `filter`, or `value`).
 * [JA] ピボットの各エリア（行・列・フィルター・値）内における `fieldName` の並び順を上下（`delta`）に入れ替えます。
 */
fun MasuSheetViewModel.onMovePivotFieldInArea(area: String, fieldName: String, delta: Int) {
    updateActivePivotAndMaterialize(
        noticeJa = "フィールド「$fieldName」の順序を変更しました",
        noticeEn = "Reordered '$fieldName' in $area"
    ) { p ->
        fun <T> moveItem(list: List<T>, pred: (T) -> Boolean): List<T> {
            val idx = list.indexOfFirst(pred)
            if (idx < 0) return list
            val targetIdx = (idx + delta).coerceIn(0, list.lastIndex)
            if (targetIdx == idx) return list
            val m = list.toMutableList()
            val item = m.removeAt(idx)
            m.add(targetIdx, item)
            return m
        }
        when (area.lowercase()) {
            "filter" -> p.copy(filterFields = moveItem(p.filterFields) { it == fieldName })
            "column" -> p.copy(columnFields = moveItem(p.columnFields) { it == fieldName })
            "row" -> p.copy(rowFields = moveItem(p.rowFields) { it == fieldName })
            "value" -> p.copy(valueFields = moveItem(p.valueFields) { it.fieldName == fieldName })
            else -> p
        }
    }
}

/**
 * [EN] Removes the value aggregation field at `index` from the active PivotTable and re-renders its grid output.
 * [JA] アクティブなピボットテーブルの「値」エリアから `index` 番目の集計フィールドを削除して再集計します。
 */
fun MasuSheetViewModel.onRemovePivotValueFieldAt(index: Int) {
    updateActivePivotAndMaterialize(
        noticeJa = "値フィールドを削除しました",
        noticeEn = "Removed value field"
    ) { p ->
        if (index !in p.valueFields.indices) p
        else p.copy(valueFields = p.valueFields.filterIndexed { idx, _ -> idx != index })
    }
}

/**
 * [EN] Sets `explicitAgg` or cycles the summary function (`SUM` -> `AVERAGE` -> `COUNT` -> `MAX` -> `MIN` -> `PRODUCT`) on a PivotTable value field.
 * [JA] ピボット値フィールドの集計方法（合計・平均・個数・最大・最小・積）を指定または順送り変更して再集計します。
 */
fun MasuSheetViewModel.onCyclePivotValueFieldAgg(fieldName: String, explicitAgg: PivotAggFunction? = null, valueIndex: Int = -1) {
    val order = listOf(
        PivotAggFunction.SUM,
        PivotAggFunction.AVERAGE,
        PivotAggFunction.COUNT,
        PivotAggFunction.MAX,
        PivotAggFunction.MIN,
        PivotAggFunction.PERCENT_OF_TOTAL,
        PivotAggFunction.PRODUCT
    )
    var chosenAgg = explicitAgg ?: PivotAggFunction.SUM
    updateActivePivotAndMaterialize(
        noticeJa = "「$fieldName」の集計方法を変更しました",
        noticeEn = "Changed aggregation for '$fieldName'"
    ) { p ->
        val targetIdx = if (valueIndex in p.valueFields.indices) {
            valueIndex
        } else {
            p.valueFields.indexOfFirst { it.fieldName == fieldName }
        }
        if (targetIdx < 0) return@updateActivePivotAndMaterialize p
        val curSpec = p.valueFields[targetIdx]
        val nextAgg = explicitAgg ?: run {
            val curPos = order.indexOf(curSpec.agg).coerceAtLeast(0)
            order[(curPos + 1) % order.size]
        }
        chosenAgg = nextAgg
        val nextList = p.valueFields.toMutableList()
        nextList[targetIdx] = curSpec.copy(agg = nextAgg)
        p.copy(valueFields = nextList)
    }
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        statusNoticeMessage = if (isEn) {
            "Changed '$fieldName' aggregation to ${chosenAgg.labelEn}"
        } else {
            "「$fieldName」の集計方法を【${chosenAgg.labelJa}】に変更しました"
        }
    )
}

/**
 * [EN] Applies a report filter value (`"ALL"` or specific item string) on `fieldName` and re-materializes the active PivotTable.
 * [JA] ピボットのフィルターフィールド `fieldName` に絞り込み値（または `"ALL"` 全解除）を設定して再集計します。
 */
fun MasuSheetViewModel.onSetPivotFilterValue(fieldName: String, filterValue: String) {
    val cleanVal = filterValue.trim().ifEmpty { "ALL" }
    updateActivePivotAndMaterialize(
        noticeJa = if (cleanVal == "ALL") {
            "ピボットフィルター「$fieldName」を（すべて）に解除しました"
        } else {
            "ピボットフィルター「$fieldName = $cleanVal」で絞り込みました"
        },
        noticeEn = "Filtered '$fieldName' by $cleanVal"
    ) { p ->
        val nextMap = p.selectedFilterValues.toMutableMap()
        if (cleanVal == "ALL") {
            nextMap.remove(fieldName)
        } else {
            nextMap[fieldName] = cleanVal
        }
        p.copy(selectedFilterValues = nextMap)
    }
}

/**
 * [EN] Expands or collapses nested outer row group `groupKey` (`[−]` / `[+]`) in multi-level row PivotTables.
 * [JA] 複数行フィールドを持つピボットテーブルにおいて、親グループ `groupKey` の展開（`[−]`）と折りたたみ（`[+]`）を切り替えます。
 */
fun MasuSheetViewModel.onTogglePivotRowGroupCollapse(groupKey: String) {
    updateActivePivotAndMaterialize(
        noticeJa = "行グループ「$groupKey」の展開/折りたたみを切り替えました",
        noticeEn = "Toggled row group '$groupKey'"
    ) { p ->
        val nextSet = p.collapsedRowGroups.toMutableSet()
        if (!nextSet.add(groupKey)) {
            nextSet.remove(groupKey)
        }
        p.copy(collapsedRowGroups = nextSet)
    }
}

/**
 * [EN] Applies a named PivotTable color style preset (such as `PivotStyleMedium2`, `PivotStyleMedium7`, `PivotStyleDark2`) and re-renders cells.
 * [JA] ピボットテーブルのデザインスタイルプリセット（青・緑・ダーク等）を変更し、セル配色を再描画します。
 */
fun MasuSheetViewModel.onSetPivotStylePreset(stylePreset: String) {
    val clean = stylePreset.ifBlank { "PivotStyleMedium2" }
    updateActivePivotAndMaterialize(
        noticeJa = "ピボットテーブル スタイルを「$clean」に変更しました",
        noticeEn = "Applied PivotTable style '$clean'"
    ) { p ->
        p.copy(stylePreset = clean)
    }
}

/**
 * [EN] Switches PivotTable report layout between `compact`, `outline`, and `tabular` forms.
 * [JA] ピボットテーブルのレポートレイアウト（コンパクト形式・アウトライン形式・表形式）を切り替えます。
 */
fun MasuSheetViewModel.onSetPivotReportLayout(layout: String) {
    val clean = layout.lowercase().ifBlank { "compact" }
    val labelJa = when (clean) {
        "outline" -> "アウトライン形式"
        "tabular" -> "表形式"
        else -> "コンパクト形式"
    }
    updateActivePivotAndMaterialize(
        noticeJa = "レポートのレイアウトを「$labelJa」に変更しました",
        noticeEn = "Changed report layout to $clean"
    ) { p ->
        p.copy(reportLayout = clean)
    }
}

/**
 * [EN] Configures group subtotals display (`none`, `top`, or `bottom`) on the active PivotTable.
 * [JA] ピボットテーブルのグループ小計表示モード（表示しない・グループの先頭・グループの末尾）を設定します。
 */
fun MasuSheetViewModel.onSetPivotSubtotalsMode(mode: String) {
    val clean = mode.lowercase().ifBlank { "bottom" }
    val labelJa = when (clean) {
        "none" -> "小計を表示しない"
        "top" -> "グループの先頭に小計を表示"
        else -> "グループの末尾に小計を表示"
    }
    updateActivePivotAndMaterialize(
        noticeJa = "ピボット小計の設定を「$labelJa」に変更しました",
        noticeEn = "Changed subtotals mode to $clean"
    ) { p ->
        p.copy(subtotalsMode = clean)
    }
}

/**
 * [EN] Enables or disables row and column Grand Totals (`both`, `rows`, `cols`, or `none`) on the active PivotTable.
 * [JA] ピボットテーブルの行・列の総計（Grand Totals）の表示モードを切り替えます。
 */
fun MasuSheetViewModel.onSetPivotGrandTotalsMode(mode: String) {
    val clean = mode.lowercase().ifBlank { "both" }
    val labelJa = if (clean == "none") "総計を表示しない" else "行と列の集計を行う（総計を表示）"
    updateActivePivotAndMaterialize(
        noticeJa = "ピボット総計の設定を「$labelJa」に変更しました",
        noticeEn = "Changed grand totals mode to $clean"
    ) { p ->
        p.copy(grandTotalsMode = clean)
    }
}

/**
 * [EN] Toggles repeating outer row item labels on every detail row in Outline/Tabular PivotTable layouts.
 * [JA] アウトライン／表形式レイアウトにおける「すべてのアイテムのラベルを繰り返し表示」のON/OFFを切り替えます。
 */
fun MasuSheetViewModel.onTogglePivotRepeatLabels() {
    updateActivePivotAndMaterialize(
        noticeJa = "アイテムのラベルの繰り返し表示を切り替えました",
        noticeEn = "Toggled Repeat All Item Labels"
    ) { p ->
        p.copy(repeatLabels = !p.repeatLabels)
    }
}

/**
 * [EN] Toggles inserting a blank separator row after each outer row group in the active PivotTable.
 * [JA] ピボットテーブルの各行グループ末尾への空白行挿入のON/OFFを切り替えます。
 */
fun MasuSheetViewModel.onTogglePivotBlankRows() {
    updateActivePivotAndMaterialize(
        noticeJa = "各アイテムの後の空白行挿入を切り替えました",
        noticeEn = "Toggled Blank Rows after each item"
    ) { p ->
        p.copy(blankRows = !p.blankRows)
    }
}

/**
 * [EN] Toggles alternating row shading (banded rows) on the active PivotTable.
 * [JA] ピボットテーブルの「縞模様（行）」スタイルのON/OFFを切り替えます。
 */
fun MasuSheetViewModel.onTogglePivotBandedRows() {
    updateActivePivotAndMaterialize(
        noticeJa = "ピボットテーブルの「縞模様（行）」を切り替えました",
        noticeEn = "Toggled PivotTable Banded Rows"
    ) { p ->
        p.copy(bandedRows = !p.bandedRows)
    }
}

/**
 * [EN] Toggles alternating column shading (banded columns) on the active PivotTable.
 * [JA] ピボットテーブルの「縞模様（列）」スタイルのON/OFFを切り替えます。
 */
fun MasuSheetViewModel.onTogglePivotBandedCols() {
    updateActivePivotAndMaterialize(
        noticeJa = "ピボットテーブルの「縞模様（列）」を切り替えました",
        noticeEn = "Toggled PivotTable Banded Columns"
    ) { p ->
        p.copy(bandedCols = !p.bandedCols)
    }
}

/**
 * [EN] Recalculates workbook formulas, re-reads source table records (including cross-sheet sources via `readSheetCells`), and re-materializes target PivotTables.
 * [JA] ブック全体の数式を再計算し、元データ範囲（別シートのセルも含む）から最新レコードを再取得してピボットテーブルを更新します。
 */
fun MasuSheetViewModel.onRefreshPivotTable(pivotId: String? = null, refreshAll: Boolean = false) {
    val calcSnap = engineGateway.executeCommand("formulas.calculateNow", "{}")
    applyViewportSnapshot(calcSnap)
    val cur = _uiState.value
    val targets = if (refreshAll) {
        cur.pivotTables
    } else {
        val active = if (pivotId != null) cur.pivotTables.firstOrNull { it.id == pivotId } else getActivePivotTable()
        listOfNotNull(active)
    }
    if (targets.isEmpty()) {
        val isEn = cur.language == "en"
        _uiState.value = cur.copy(
            statusNoticeMessage = if (isEn) "Recalculated all formulas" else "すべての数式を再計算しました"
        )
        return
    }
    val currentSnap = _uiState.value.viewport
    val updatedPivots = cur.pivotTables.map { p ->
        if (targets.any { it.id == p.id }) {
            val sourceSnap = if (p.sourceSheetIndex == currentSnap.sheetIndex) {
                currentSnap
            } else {
                val remoteCells = engineGateway.readSheetCells(p.sourceSheetIndex)
                if (remoteCells.isNotEmpty()) {
                    currentSnap.copy(sheetIndex = p.sourceSheetIndex, cells = remoteCells)
                } else {
                    null
                }
            }
            if (sourceSnap != null) {
                val (hdrs, numSet, recs) = extractSourceTableRecords(
                    snap = sourceSnap,
                    startRow = p.sourceStartRow,
                    startCol = p.sourceStartCol,
                    endRow = p.sourceEndRow,
                    endCol = p.sourceEndCol
                )
                if (recs.isNotEmpty()) {
                    p.copy(availableFields = hdrs, numericFields = numSet, sourceRecords = recs)
                } else p
            } else p
        } else p
    }
    _uiState.value = _uiState.value.copy(pivotTables = updatedPivots)
    for (t in targets) {
        materializePivotTable(t.id)
    }
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        statusNoticeMessage = if (isEn) {
            "Refreshed PivotTable (${targets.first().name}) from latest source data"
        } else {
            "ピボットテーブル「${targets.first().name}」を最新の元データで再集計しました"
        }
    )
}

/**
 * [EN] Clears all assigned row, column, filter, and value fields from the active PivotTable (or clears sheet AutoFilters if none is active).
 * [JA] ピボットテーブルの全エリア配置とフィルター選択を初期状態にクリアします（ピボット未選択時はシートフィルターを解除）。
 */
fun MasuSheetViewModel.onClearPivotTable(pivotId: String? = null) {
    val active = (if (pivotId != null) _uiState.value.pivotTables.firstOrNull { it.id == pivotId } else getActivePivotTable())
    if (active == null) {
        onClearAllFiltersOnCurrentSheet()
        return
    }
    updateActivePivotAndMaterialize(
        noticeJa = "「${active.name}」を初期化（すべてクリア）し、シート上の集計表とフィールド選択を空にしました",
        noticeEn = "Cleared all fields and rendered cells for ${active.name}"
    ) { p ->
        p.copy(
            selectedFilterValues = emptyMap(),
            collapsedRowGroups = emptySet(),
            filterFields = emptyList(),
            columnFields = emptyList(),
            rowFields = emptyList(),
            valueFields = emptyList()
        )
    }
}

/**
 * [EN] Deletes `pivotId` (or the active PivotTable), wiping its rendered cells and formatting from the target worksheet with Undo support.
 * [JA] 対象ピボットテーブルを削除し、シート上に展開されていた集計セルと書式をクリアします（Undo対応）。
 */
fun MasuSheetViewModel.onDeletePivotTable(pivotId: String? = null) {
    val cur = _uiState.value
    val target = (if (pivotId != null) cur.pivotTables.firstOrNull { it.id == pivotId } else getActivePivotTable())
    if (target == null) {
        val isEn = cur.language == "en"
        _uiState.value = cur.copy(
            activePivotTableId = null,
            isPivotFieldsPaneOpen = false,
            statusNoticeMessage = if (isEn) "No PivotTable on this sheet to delete" else "このシートに削除対象のピボットテーブルはありません"
        )
        return
    }
    val sIdx = target.targetSheetIndex
    val switchedSheet = cur.viewport.sheetIndex != sIdx
    if (switchedSheet) {
        val actSnap = engineGateway.executeCommand("sheet.activate", "{\"sheet\":$sIdx}")
        applyViewportSnapshot(actSnap)
    }
    val clearR0 = minOf(target.lastRenderedStartRow, target.targetStartRow).coerceAtLeast(0)
    val clearR1 = maxOf(target.lastRenderedEndRow, target.targetStartRow + 1)
    val clearC0 = target.targetStartCol.coerceAtLeast(0)
    val clearC1 = maxOf(target.lastRenderedEndCol, target.targetStartCol + 3)

    var lastSnap = _uiState.value.viewport
    for (r in clearR0..clearR1) {
        for (c in clearC0..clearC1) {
            val addr = MasuSheetConfig.formatCellAddress(r, c)
            lastSnap = engineGateway.executeCommand("cell.set", "{\"cell\":\"$addr\",\"input\":\"\"}")
            customCellStyles.remove(sIdx to NativeViewportSnapshot.packCellKey(r, c))
        }
    }
    sizeRevisionBump++
    applyViewportSnapshot(lastSnap)

    val latest = _uiState.value
    val remaining = latest.pivotTables.filterNot { it.id == target.id }
    val nextRibbon = if (latest.activeRibbonTab == "pivotAnalyze" || latest.activeRibbonTab == "pivotDesign") {
        "home"
    } else {
        latest.activeRibbonTab
    }
    val isEn = latest.language == "en"
    _uiState.value = latest.copy(
        pivotTables = remaining,
        activePivotTableId = null,
        isPivotFieldsPaneOpen = false,
        dismissedPivotLauncherIds = latest.dismissedPivotLauncherIds - target.id,
        activeRibbonTab = nextRibbon,
        statusNoticeMessage = if (isEn) {
            "Deleted PivotTable '${target.name}' and cleared its cells from the sheet"
        } else {
            "ピボットテーブル「${target.name}」を削除し、シート上の集計表を消去しました"
        }
    )
}
