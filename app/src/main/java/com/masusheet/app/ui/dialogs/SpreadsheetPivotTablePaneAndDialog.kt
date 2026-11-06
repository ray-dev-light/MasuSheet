package com.masusheet.app.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.PivotAggFunction
import com.masusheet.app.model.PivotTableDefinition
import com.masusheet.app.ui.ribbon.MasuSheetPillButton

/**
 * ============================================================================
 * [EN] PivotTable Fields Docked Task Pane & Create PivotTable Dialog
 * Implements [SpreadsheetPivotTableFieldsRightPane] (Rows, Columns, Values, Filters, aggregation function, grand totals, layout) and [SpreadsheetCreatePivotTableDialog].
 *
 * ---
 * [JA / 日本語解説]
 * ピボットテーブルのフィールド配置（行・列・値・フィルタ・集計方法・総計・レイアウト）を行う右サイドペイン ([SpreadsheetPivotTableFieldsRightPane]) と新規作成ダイアログ ([SpreadsheetCreatePivotTableDialog]) です。
 * ============================================================================
 */

// ============================================================================
// 11. SPREADSHEET PIVOTTABLE FIELDS RIGHT-SIDE DOCKED TASK PANE & CREATE DIALOG
// ============================================================================

/**
 * [EN] Docked right-side PivotTable task pane for configuring Row, Column, Value, and Filter fields plus layout & totals.
 * [JA] ピボットテーブルの行・列・値・フィルタ配置や集計関数・総計・レイアウトを編集する右ドックタスクペインです。
 */
@Composable
fun SpreadsheetPivotTableFieldsRightPane(
    state: MasuSheetUiState,
    activePivot: PivotTableDefinition?,
    onSelectPivot: (String) -> Unit,
    onOpenCreateDialog: () -> Unit,
    onRefreshPivot: () -> Unit,
    onClearPivot: () -> Unit,
    onDeletePivot: () -> Unit = {},
    onInsertPivotChart: () -> Unit,
    onApplyQuickPreset: (String) -> Unit,
    onToggleFieldChecked: (String) -> Unit,
    onAssignFieldToArea: (fieldName: String, area: String) -> Unit,
    onMoveFieldInArea: (area: String, fieldName: String, delta: Int) -> Unit,
    onRemoveValueFieldAt: (Int) -> Unit,
    onCycleValueFieldAgg: (fieldName: String, explicitAgg: PivotAggFunction?, valueIndex: Int) -> Unit,
    onSetFilterValue: (fieldName: String, filterValue: String) -> Unit,
    onToggleRowGroupCollapse: (String) -> Unit,
    onSetStylePreset: (String) -> Unit,
    onSetReportLayout: (String) -> Unit,
    onSetSubtotalsMode: (String) -> Unit,
    onSetGrandTotalsMode: (String) -> Unit,
    onToggleRepeatLabels: () -> Unit,
    onToggleBlankRows: () -> Unit,
    onToggleBandedRows: () -> Unit,
    onToggleBandedCols: () -> Unit,
    onClosePane: () -> Unit
) {
    val isEn = state.language == "en"
    val isDark = state.isDarkTheme
    val paneBg = if (isDark) Color(0xFF182232) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    val textMain = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSub = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)

    Column(
        modifier = Modifier
            .width(state.pivotPaneWidthDp.coerceIn(220f, 760f).dp)
            .fillMaxHeight()
            .background(paneBg)
            .border(1.dp, if (isDark) Color(0xFF38BDF8) else Color(0xFF94A3B8))
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEn) "📊 PivotTable Fields" else "📊 ピボットテーブルのフィールド",
                    color = if (isDark) Color(0xFF38BDF8) else Color(0xFF1E40AF),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                if (activePivot != null) {
                    Text(
                        text = if (isEn) {
                            "${activePivot.name} · Src: ${activePivot.sourceRangeLabel} → ${activePivot.targetRangeLabel}"
                        } else {
                            "${activePivot.name} (元:${activePivot.sourceRangeLabel} → 先:${activePivot.targetRangeLabel})"
                        },
                        color = textSub,
                        fontSize = 10.5.sp
                    )
                }
            }
            MasuSheetPillButton(
                label = if (isEn) "✕" else "✕ 閉じる",
                onClick = onClosePane
            )
        }

        // PivotTable Switcher + Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MasuSheetPillButton(
                label = if (isEn) "➕ New / Source" else "➕ 新規作成・範囲変更",
                highlight = true,
                onClick = onOpenCreateDialog
            )
            MasuSheetPillButton(
                label = if (isEn) "🔄 Refresh" else "🔄 最新データ更新",
                onClick = onRefreshPivot
            )
            MasuSheetPillButton(
                label = if (isEn) "📈 PivotChart" else "📈 ピボットグラフ",
                onClick = onInsertPivotChart
            )
            MasuSheetPillButton(
                label = if (isEn) "🧹 Clear All" else "🧹 初期化(クリア)",
                onClick = onClearPivot
            )
            MasuSheetPillButton(
                label = if (isEn) "🗑️ Delete Pivot" else "🗑️ ピボット削除",
                onClick = onDeletePivot
            )
        }

        if (state.pivotTables.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) "Target Pivot:" else "操作対象ピボット:",
                    color = textSub,
                    fontSize = 10.5.sp
                )
                state.pivotTables.forEach { p ->
                    val isSel = p.id == activePivot?.id
                    MasuSheetPillButton(
                        label = (if (isSel) "✅ " else "📊 ") + p.name,
                        highlight = isSel,
                        onClick = { onSelectPivot(p.id) }
                    )
                }
            }
        }

        if (activePivot == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isEn) {
                        "No PivotTable on this sheet yet. Tap below to create one from the current table:"
                    } else {
                        "現在選択中のピボットテーブルがありません。下のボタンからテーブルを集計してピボットテーブルを作成できます："
                    },
                    color = textMain,
                    fontSize = 11.5.sp
                )
                MasuSheetPillButton(
                    label = if (isEn) "📊 Create PivotTable Now" else "📊 現在の表からピボットテーブルを作成",
                    highlight = true,
                    onClick = { onApplyQuickPreset("standard") }
                )
            }
            return@Column
        }

        // 2. Quick Recommended Pivot Presets (1-tap templates)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(7.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = if (isEn) "⚡ Quick Recommended Layouts:" else "⚡ おすすめピボット集計パターン (1タップ切替):",
                color = textMain,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                MasuSheetPillButton(
                    label = if (isEn) "📊 Category Summary" else "📊 カテゴリ別集計 (標準)",
                    highlight = activePivot.rowFields.size == 1 && activePivot.columnFields.isEmpty(),
                    onClick = { onApplyQuickPreset("standard") }
                )
                MasuSheetPillButton(
                    label = if (isEn) "📑 2-Level Hierarchy" else "📑 カテゴリ×項目 (2階層)",
                    highlight = activePivot.rowFields.size >= 2,
                    onClick = { onApplyQuickPreset("hierarchy") }
                )
                MasuSheetPillButton(
                    label = if (isEn) "🧮 % of Total & Avg" else "🧮 構成比(%)＋平均",
                    highlight = activePivot.valueFields.any { it.agg == PivotAggFunction.PERCENT_OF_TOTAL },
                    onClick = { onApplyQuickPreset("average_percent") }
                )
                MasuSheetPillButton(
                    label = if (isEn) "🏛️ Cross-Tab Matrix" else "🏛️ クロス集計 (行×列)",
                    highlight = activePivot.columnFields.isNotEmpty(),
                    onClick = { onApplyQuickPreset("crosstab") }
                )
            }
        }

        // 3. Field List ("レポートに追加するフィールドを選択してください")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(7.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = if (isEn) {
                    "☑ Choose fields to add to report:"
                } else {
                    "☑ レポートに追加するフィールドを選択:"
                },
                color = textMain,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )

            activePivot.availableFields.forEach { fieldName ->
                val isChecked = activePivot.isFieldActive(fieldName)
                val isNumeric = fieldName in activePivot.numericFields
                val currentAreaBadge = when {
                    fieldName in activePivot.filterFields -> if (isEn) "🔎Filter" else "🔎フィルタ"
                    fieldName in activePivot.columnFields -> if (isEn) "🏛️Col" else "🏛️列"
                    fieldName in activePivot.rowFields -> if (isEn) "☰Row" else "☰行"
                    activePivot.valueFields.any { it.fieldName == fieldName } -> if (isEn) "ΣVal" else "Σ値"
                    else -> ""
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isChecked) {
                                if (isDark) Color(0xFF1E3A5F) else Color(0xFFE0F2FE)
                            } else {
                                if (isDark) Color(0xFF162032) else Color(0xFFF1F5F9)
                            }
                        )
                        .border(
                            0.8.dp,
                            if (isChecked) Color(0xFF38BDF8) else cardBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleFieldChecked(fieldName) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isChecked) "☑️" else "⬜",
                                fontSize = 12.sp
                            )
                            Text(
                                text = fieldName,
                                color = textMain,
                                fontSize = 12.sp,
                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = if (isNumeric) (if (isEn) "∑Num" else "∑数値") else "Abc",
                                color = if (isNumeric) Color(0xFF38BDF8) else Color(0xFFA78BFA),
                                fontSize = 9.5.sp
                            )
                        }
                        if (currentAreaBadge.isNotEmpty()) {
                            Text(
                                text = currentAreaBadge,
                                color = Color(0xFF4ADE80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Quick placement buttons for this field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MasuSheetPillButton(
                            label = if (isEn) "+Row" else "☰ 行へ",
                            highlight = fieldName in activePivot.rowFields,
                            onClick = { onAssignFieldToArea(fieldName, if (fieldName in activePivot.rowFields) "none" else "row") }
                        )
                        MasuSheetPillButton(
                            label = if (isEn) "+Col" else "🏛️ 列へ",
                            highlight = fieldName in activePivot.columnFields,
                            onClick = { onAssignFieldToArea(fieldName, if (fieldName in activePivot.columnFields) "none" else "column") }
                        )
                        MasuSheetPillButton(
                            label = if (isEn) "+Σ Value" else "Σ 値に追加",
                            highlight = activePivot.valueFields.any { it.fieldName == fieldName },
                            onClick = { onAssignFieldToArea(fieldName, "value") }
                        )
                        MasuSheetPillButton(
                            label = if (isEn) "+Filter" else "🔎 フィルタへ",
                            highlight = fieldName in activePivot.filterFields,
                            onClick = { onAssignFieldToArea(fieldName, if (fieldName in activePivot.filterFields) "none" else "filter") }
                        )
                    }
                }
            }
        }

        // 4. Four Pivot Areas (2×2 Grid: Filters / Columns / Rows / Values)
        Text(
            text = if (isEn) {
                "📐 Configure the 4 PivotTable Areas (Filters / Columns / Rows / Σ Values):"
            } else {
                "📐 4つのエリア間でフィールドを設定 (フィルター / 列 / 行 / Σ 値):"
            },
            color = textMain,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )

        // Top Row of 2x2: FILTERS & COLUMNS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Area 1: FILTERS
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 95.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(7.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isEn) "🔎 FILTERS" else "🔎 フィルター",
                    color = Color(0xFFFBBF24),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (activePivot.filterFields.isEmpty()) {
                    Text(
                        text = if (isEn) "(Tap '+Filter' above)" else "(上の「🔎フィルタへ」で追加)",
                        color = textSub,
                        fontSize = 9.5.sp
                    )
                } else {
                    activePivot.filterFields.forEach { fField ->
                        val curFilterVal = activePivot.selectedFilterValues[fField] ?: "ALL"
                        val distinctVals = activePivot.sourceRecords
                            .mapNotNull { it[fField]?.takeIf { s -> s.isNotEmpty() } }
                            .distinct()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(fField, color = textMain, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "✕",
                                    color = Color(0xFFF87171),
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable { onAssignFieldToArea(fField, "none") }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                MasuSheetPillButton(
                                    label = if (isEn) "All" else "すべて",
                                    highlight = curFilterVal == "ALL",
                                    onClick = { onSetFilterValue(fField, "ALL") }
                                )
                                distinctVals.forEach { dv ->
                                    MasuSheetPillButton(
                                        label = dv,
                                        highlight = curFilterVal == dv,
                                        onClick = { onSetFilterValue(fField, dv) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Area 2: COLUMNS
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 95.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(7.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isEn) "🏛️ COLUMNS" else "🏛️ 列 (クロス集計)",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (activePivot.columnFields.isEmpty()) {
                    Text(
                        text = if (isEn) "Σ Values (Default)" else "Σ 値 (標準の列見出し)",
                        color = textSub,
                        fontSize = 9.5.sp
                    )
                } else {
                    activePivot.columnFields.forEach { cField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cField, color = textMain, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "✕",
                                color = Color(0xFFF87171),
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { onAssignFieldToArea(cField, "none") }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Row of 2x2: ROWS & Σ VALUES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Area 3: ROWS
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .heightIn(min = 125.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(7.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isEn) "☰ ROWS" else "☰ 行 (グループ階層)",
                    color = Color(0xFF4ADE80),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (activePivot.rowFields.isEmpty()) {
                    Text(
                        text = if (isEn) "(Tap '+Row' above)" else "(上の「☰行へ」で追加)",
                        color = textSub,
                        fontSize = 9.5.sp
                    )
                } else {
                    activePivot.rowFields.forEachIndexed { idx, rField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .padding(horizontal = 5.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}. $rField",
                                color = textMain,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (activePivot.rowFields.size > 1) {
                                    Text(
                                        text = "↑",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable { onMoveFieldInArea("row", rField, -1) }
                                    )
                                    Text(
                                        text = "↓",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable { onMoveFieldInArea("row", rField, 1) }
                                    )
                                }
                                Text(
                                    text = "✕",
                                    color = Color(0xFFF87171),
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable { onAssignFieldToArea(rField, "none") }
                                )
                            }
                        }
                    }

                    // If 2+ Row Fields, show Expand/Collapse toggles for outer groups
                    if (activePivot.rowFields.size >= 2) {
                        val outerField = activePivot.rowFields[0]
                        val outerGroups = activePivot.sourceRecords
                            .mapNotNull { it[outerField]?.takeIf { s -> s.isNotEmpty() } }
                            .distinct()
                        Text(
                            text = if (isEn) "Expand/Collapse Groups:" else "グループの展開/折りたたみ:",
                            color = textSub,
                            fontSize = 9.5.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            outerGroups.forEach { grp ->
                                val isCol = grp in activePivot.collapsedRowGroups
                                MasuSheetPillButton(
                                    label = (if (isCol) "▸ " else "▾ ") + grp,
                                    highlight = !isCol,
                                    onClick = { onToggleRowGroupCollapse(grp) }
                                )
                            }
                        }
                    }
                }
            }

            // Area 4: Σ VALUES
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .heightIn(min = 125.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(7.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = if (isEn) "Σ VALUES (Aggregation)" else "Σ 値 (集計フィールド)",
                    color = Color(0xFFA78BFA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (activePivot.valueFields.isEmpty()) {
                    Text(
                        text = if (isEn) "(Tap '+Σ Value' above)" else "(上の「Σ値に追加」をタップ)",
                        color = textSub,
                        fontSize = 9.5.sp
                    )
                } else {
                    activePivot.valueFields.forEachIndexed { vIdx, spec ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .padding(5.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = spec.displayTitle(isEn),
                                    color = textMain,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "✕",
                                    color = Color(0xFFF87171),
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable { onRemoveValueFieldAt(vIdx) }
                                )
                            }
                            // 1-tap Aggregation Function Switcher
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                listOf(
                                    PivotAggFunction.SUM,
                                    PivotAggFunction.AVERAGE,
                                    PivotAggFunction.COUNT,
                                    PivotAggFunction.MAX,
                                    PivotAggFunction.MIN,
                                    PivotAggFunction.PERCENT_OF_TOTAL
                                ).forEach { aggFn ->
                                    MasuSheetPillButton(
                                        label = if (isEn) aggFn.labelEn else aggFn.labelJa,
                                        highlight = spec.agg == aggFn,
                                        onClick = { onCycleValueFieldAgg(spec.fieldName, aggFn, vIdx) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. PivotTable Style Presets & Layout Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(7.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isEn) "🎨 PivotTable Style & Layout:" else "🎨 ピボットテーブル スタイル配色 & レイアウト:",
                color = textMain,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )

            // Style Color Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val styleItems = listOf(
                    "PivotStyleMedium2" to (if (isEn) "🔵 Classic Blue" else "🔵 標準ブルー(月間予算)"),
                    "PivotStyleMedium7" to (if (isEn) "🟢 Emerald Green" else "🟢 エメラルド"),
                    "PivotStyleMedium3" to (if (isEn) "🟠 Warm Orange" else "🟠 オレンジ"),
                    "PivotStyleMedium5" to (if (isEn) "🟣 Royal Purple" else "🟣 パープル"),
                    "PivotStyleDark2" to (if (isEn) "⚫ Dark Slate" else "⚫ ダーク")
                )
                styleItems.forEach { (presetId, label) ->
                    MasuSheetPillButton(
                        label = label,
                        highlight = activePivot.stylePreset == presetId,
                        onClick = { onSetStylePreset(presetId) }
                    )
                }
            }

            // Report Layout & Subtotals / Grand Totals
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                MasuSheetPillButton(
                    label = if (isEn) "Compact" else "コンパクト形式",
                    highlight = activePivot.reportLayout == "compact",
                    onClick = { onSetReportLayout("compact") }
                )
                MasuSheetPillButton(
                    label = if (isEn) "Outline" else "アウトライン形式",
                    highlight = activePivot.reportLayout == "outline",
                    onClick = { onSetReportLayout("outline") }
                )
                MasuSheetPillButton(
                    label = if (isEn) "Tabular" else "表形式",
                    highlight = activePivot.reportLayout == "tabular",
                    onClick = { onSetReportLayout("tabular") }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                MasuSheetPillButton(
                    label = if (isEn) "Grand Total" else "総計を表示",
                    highlight = activePivot.grandTotalsMode != "none",
                    onClick = { onSetGrandTotalsMode(if (activePivot.grandTotalsMode == "none") "both" else "none") }
                )
                MasuSheetPillButton(
                    label = if (isEn) "Subtotals: Bottom" else "小計:グループ下",
                    highlight = activePivot.subtotalsMode == "bottom",
                    onClick = { onSetSubtotalsMode("bottom") }
                )
                MasuSheetPillButton(
                    label = if (isEn) "Subtotals: Off" else "小計:なし",
                    highlight = activePivot.subtotalsMode == "none",
                    onClick = { onSetSubtotalsMode("none") }
                )
                MasuSheetPillButton(
                    label = if (isEn) "Banded Rows" else "縞模様(行)",
                    highlight = activePivot.bandedRows,
                    onClick = onToggleBandedRows
                )
                MasuSheetPillButton(
                    label = if (isEn) "Banded Cols" else "縞模様(列)",
                    highlight = activePivot.bandedCols,
                    onClick = onToggleBandedCols
                )
                MasuSheetPillButton(
                    label = if (isEn) "Repeat Labels" else "ラベル繰返",
                    highlight = activePivot.repeatLabels,
                    onClick = onToggleRepeatLabels
                )
                MasuSheetPillButton(
                    label = if (isEn) "Blank Rows" else "空白行挿入",
                    highlight = activePivot.blankRows,
                    onClick = onToggleBlankRows
                )
            }
        }
    }
}

/**
 * [EN] Dialog for creating a new PivotTable from a detected table range onto a new worksheet or an existing cell anchor.
 * [JA] 元データ範囲と出力先（新規ワークシートまたは既存シート番地）を指定してピボットテーブルを作成するダイアログです。
 */
@Composable
fun SpreadsheetCreatePivotTableDialog(
    state: MasuSheetUiState,
    defaultSourceRange: String,
    defaultTargetAddress: String,
    onCreatePivot: (sourceRange: String, placeOnNewSheet: Boolean, targetAddress: String, presetKind: String) -> Unit,
    onDismiss: () -> Unit
) {
    val isEn = state.language == "en"
    var sourceRangeInput by remember(defaultSourceRange) { mutableStateOf(defaultSourceRange) }
    var placeOnNewSheet by remember { mutableStateOf(false) }
    var targetAddressInput by remember(defaultTargetAddress) { mutableStateOf(defaultTargetAddress) }
    var selectedPreset by remember { mutableStateOf("standard") }

    val presets = listOf(
        Triple(
            "standard",
            if (isEn) "📊 Standard Category Summary (Sum of Values by Category)" else "📊 標準カテゴリ別集計 (カテゴリごとの予算・実績・差額の合計)",
            if (isEn) "Groups by primary category column and calculates SUM for numeric columns." else "「月間予算」のH11:K19のように、カテゴリごとの合計をクラシックブルーで表集計します。"
        ),
        Triple(
            "hierarchy",
            if (isEn) "📑 2-Level Hierarchy (Category × Item with Subtotals)" else "📑 2階層ドリルダウン集計 (カテゴリ × 項目 + グループ小計)",
            if (isEn) "Adds both Category and Item to Rows with collapsible ▾/▸ group subtotals." else "行に「カテゴリ」と「項目」の2階層を配置し、▾/▸で折りたたみ可能な詳細ピボットを作成します。"
        ),
        Triple(
            "average_percent",
            if (isEn) "🧮 % of Grand Total + Average Analysis" else "🧮 構成比(%) ＋ 平均値・フィルター分析",
            if (isEn) "Shows Sum, % of Grand Total, and Average alongside an interactive report filter." else "合計に加えて「全体に対する構成比(%)」と「平均」を自動算出し、上部フィルターも配置します。"
        ),
        Triple(
            "crosstab",
            if (isEn) "🏛️ Cross-Tab Matrix (Row × Column Matrix)" else "🏛️ クロス集計マトリックス (行 × 列の2軸集計)",
            if (isEn) "Pivots the second text field across columns to build a 2D matrix." else "行と列の2軸にフィールドを展開したクロス集計表を作成します。"
        )
    )

    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 580.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) {
                        "📊 Create PivotTable / Recommended PivotTables"
                    } else {
                        "📊 ピボットテーブルの作成 & おすすめピボットテーブル"
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                MasuSheetPillButton(if (isEn) "✕ Close" else "✕ 閉じる", onClick = onDismiss)
            }

            // 1. Source Range
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isEn) "① Choose the data that you want to analyze (Table/Range):" else "① 分析するデータを選択してください (テーブルまたは範囲):",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isEn) "Table/Range:" else "テーブル/範囲:",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    BasicTextField(
                        value = sourceRangeInput,
                        onValueChange = { sourceRangeInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(Color(0xFF38BDF8)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )
                }
            }

            // 2. Destination (New Worksheet vs Existing Worksheet)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isEn) "② Choose where you want the PivotTable report to be placed:" else "② ピボットテーブル レポートを配置する場所を選択してください:",
                    color = Color(0xFF4ADE80),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MasuSheetPillButton(
                        label = if (isEn) "📄 Existing Worksheet" else "📄 既存のワークシート (${state.viewport.sheetName})",
                        highlight = !placeOnNewSheet,
                        onClick = { placeOnNewSheet = false }
                    )
                    MasuSheetPillButton(
                        label = if (isEn) "➕ New Worksheet" else "➕ 新規ワークシートを作成して配置",
                        highlight = placeOnNewSheet,
                        onClick = { placeOnNewSheet = true }
                    )
                }
                if (!placeOnNewSheet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isEn) "Location (Top-Left Cell):" else "配置先セル (左上):",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        BasicTextField(
                            value = targetAddressInput,
                            onValueChange = { targetAddressInput = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFF4ADE80)),
                            modifier = Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        )
                    }
                }
            }

            // 3. Recommended PivotTable Presets
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isEn) "③ Recommended PivotTable Layout:" else "③ おすすめピボットテーブルの初期レイアウトを選択:",
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                presets.forEach { (id, title, desc) ->
                    val isSel = selectedPreset == id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) Color(0xFF1E3A5F) else Color(0xFF1E293B))
                            .border(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF475569), RoundedCornerShape(6.dp))
                            .clickable { selectedPreset = id }
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = (if (isSel) "✅ " else "⬜ ") + title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = desc,
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MasuSheetPillButton(
                    label = if (isEn) "Cancel" else "キャンセル",
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(8.dp))
                MasuSheetPillButton(
                    label = if (isEn) "✓ OK (Create PivotTable & Open Field Pane)" else "✓ OK (ピボットテーブルを作成して右ペインを開く)",
                    highlight = true,
                    onClick = {
                        onCreatePivot(sourceRangeInput, placeOnNewSheet, targetAddressInput, selectedPreset)
                    }
                )
            }
        }
    }
}




