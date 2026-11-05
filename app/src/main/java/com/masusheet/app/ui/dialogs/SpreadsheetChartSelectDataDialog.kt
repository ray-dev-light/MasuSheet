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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.ChartTableColumnOption
import com.masusheet.app.model.DetectedSheetTableRange
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeSheetObject
import com.masusheet.app.ui.ribbon.MasuSheetPillButton

/**
 * ============================================================================
 * [EN] Chart Data Source, Series, & Category Axis Range Selector Dialog
 * Implements [SpreadsheetChartSelectDataDialog] for editing chart data range, swapping row/column orientation, and toggling individual series or category columns.
 *
 * ---
 * [JA / 日本語解説]
 * グラフのデータ範囲変更、行/列の切り替え、系列（凡例項目）および横（項目）軸ラベルの個別選択を行う「データソースの選択」ダイアログ ([SpreadsheetChartSelectDataDialog]) です。
 * ============================================================================
 */

// ============================================================================
// 11. SPREADSHEET "SELECT DATA SOURCE (データ ソースの選択)" DIALOG FOR CHARTS
// ============================================================================

/**
 * [EN] Chart data source dialog for editing source ranges, toggling row/column series orientation, and picking X/Y columns.
 * [JA] グラフのデータ範囲・行列切替・系列（Y軸）および項目軸（X軸）列を対話的に編集するデータソース選択ダイアログです。
 */
@Composable
fun SpreadsheetChartSelectDataDialog(
    state: MasuSheetUiState,
    chartObject: NativeSheetObject,
    detectedTables: List<DetectedSheetTableRange>,
    tableColumns: List<ChartTableColumnOption> = emptyList(),
    onSelectXAxisColumn: (colIndex: Int) -> Unit = {},
    onToggleYAxisColumn: (colIndex: Int, exclusive: Boolean) -> Unit = { _, _ -> },
    onBindCurrentSelectionAsXAxis: () -> Unit = {},
    onBindCurrentSelectionAsYAxis: (append: Boolean) -> Unit = {},
    onStartAxisPickingOnSheet: (axisMode: String) -> Unit = {},
    onUpdateSeriesRanges: (seriesIndex: Int, newName: String?, newXRange: String?, newYRange: String?) -> Unit = { _, _, _, _ -> },
    onAddSeriesFromSheet: (xRange: String, yRange: String, name: String) -> Unit = { _, _, _ -> },
    onRemoveSeriesAt: (seriesIndex: Int) -> Unit = {},
    onOpenAxisFormatDialog: (axis: String) -> Unit = {},
    onApplyDataRange: (rangeText: String) -> Unit,
    onBindCurrentSelection: () -> Unit,
    onStartRangePickingOnSheet: () -> Unit,
    onSwitchRowColumn: () -> Unit,
    onApplyCustomSeriesAndCategories: (title: String, categories: List<String>, seriesList: List<Pair<String, List<Double>>>) -> Unit,
    onDismiss: () -> Unit
) {
    val isEn = state.language == "en"
    val vp = state.viewport
    val currentSelectionRef = remember(vp.selStartRow, vp.selEndRow, vp.selStartCol, vp.selEndCol) {
        val rMin = minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0)
        val rMax = maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(rMin)
        val cMin = minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
        val cMax = maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(cMin)
        val startRef = MasuSheetConfig.formatCellAddress(rMin, cMin)
        val endRef = MasuSheetConfig.formatCellAddress(rMax, cMax)
        if (startRef == endRef) startRef else "$startRef:$endRef"
    }

    var rangeInput by remember(chartObject.id, chartObject.sourceRangeText, currentSelectionRef) {
        mutableStateOf(
            chartObject.sourceRangeText.ifBlank {
                if (currentSelectionRef.contains(":")) currentSelectionRef else "A1:B10"
            }
        )
    }
    var hasUnappliedRangeEdit by remember(chartObject.id, chartObject.sourceRangeText) {
        mutableStateOf(false)
    }
    var titleInput by remember(chartObject.id, chartObject.title) {
        mutableStateOf(chartObject.title)
    }
    var categoriesCsv by remember(chartObject.id, chartObject.chartLabels) {
        mutableStateOf(chartObject.chartLabels.joinToString(", "))
    }
    var seriesTextLines by remember(chartObject.id, chartObject.chartSeries) {
        mutableStateOf(
            if (chartObject.chartSeries.isNotEmpty()) {
                chartObject.chartSeries.joinToString("\n") { s ->
                    "${s.name}: ${s.values.joinToString(", ") { v -> if (v % 1.0f == 0.0f) v.toLong().toString() else v.toString() }}"
                }
            } else {
                ""
            }
        )
    }
    var hasUnappliedCustomEdit by remember(chartObject.id, chartObject.title, chartObject.chartLabels, chartObject.chartSeries) {
        mutableStateOf(false)
    }

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
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .border(1.5.dp, Color(0xFF107C41), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF107C41)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📊", fontSize = 13.sp)
                    }
                    Column {
                        Text(
                            text = if (isEn) "Select X-Axis / Y-Axis & Data Source (${chartObject.title})" else "X軸・Y軸とデータ ソースの選択 (${chartObject.title})",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEn) {
                                "Select which column or cell range is plotted on the X-Axis and Y-Axis for each series."
                            } else {
                                "系列ごとに「X軸 (横軸)」と「Y軸 (縦軸)」のセル範囲を選択・調整できます"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }
                MasuSheetPillButton(if (isEn) "✕ Close" else "✕ 閉じる", onClick = onDismiss)
            }

            // Scrollable Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section 0: Interactive X-Axis (Horizontal) & Y-Axis (Vertical) Column / Range Selector!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, Color(0xFF107C41), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEn) {
                                "📐 Select X-Axis (Horizontal) & Y-Axis (Vertical) Columns / Ranges"
                            } else {
                                "📐 X軸（横軸・項目ラベル）と Y軸（縦軸・データ系列）の選択"
                            },
                            color = Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MasuSheetPillButton(
                                label = if (isEn) "📏 X-Axis Interval" else "📏 X軸の間隔設定",
                                onClick = { onOpenAxisFormatDialog("x") }
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "📐 Y-Axis Interval" else "📐 Y軸の間隔設定",
                                onClick = { onOpenAxisFormatDialog("y") }
                            )
                        }
                    }

                    // --- X-Axis (Horizontal) ---
                    val currentXPreview = chartObject.chartLabels.take(5).joinToString(", ") +
                        if (chartObject.chartLabels.size > 5) "..." else ""
                    Text(
                        text = if (isEn) {
                            "📏 X-Axis (Horizontal Categories) — Current (${chartObject.chartLabels.size} items): [$currentXPreview]"
                        } else {
                            "📏 【X軸 (横軸・項目)】にする列を選択 — 現在 (${chartObject.chartLabels.size}項目): [$currentXPreview]"
                        },
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (tableColumns.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tableColumns.forEach { colOpt ->
                                val isCurrentX = if (chartObject.chartXColIndex >= 0) {
                                    colOpt.colIndex == chartObject.chartXColIndex
                                } else {
                                    colOpt.sampleValues.isNotEmpty() &&
                                        chartObject.chartLabels.take(colOpt.sampleValues.size) == colOpt.sampleValues
                                }
                                MasuSheetPillButton(
                                    label = if (isCurrentX) "● X軸: ${colOpt.displayLabel}" else "○ ${colOpt.displayLabel}",
                                    highlight = isCurrentX,
                                    onClick = {
                                        hasUnappliedRangeEdit = false
                                        onSelectXAxisColumn(colOpt.colIndex)
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MasuSheetPillButton(
                            label = if (isEn) {
                                "📏 Set Selection ($currentSelectionRef) as X-Axis"
                            } else {
                                "📏 選択中のセル範囲 ($currentSelectionRef) をX軸に設定"
                            },
                            highlight = true,
                            onClick = {
                                hasUnappliedRangeEdit = false
                                onBindCurrentSelectionAsXAxis()
                            }
                        )
                        MasuSheetPillButton(
                            label = if (isEn) {
                                "🖱️ Drag on Sheet to Pick X-Axis..."
                            } else {
                                "🖱️ シート上でX軸の範囲をドラッグ選択..."
                            },
                            onClick = { onStartAxisPickingOnSheet("x") }
                        )
                    }

                    // --- Y-Axis (Vertical) ---
                    val activeYNames = chartObject.chartSeries.map { it.name }.toSet()
                    val currentYPreview = chartObject.chartSeries.joinToString(", ") { it.name }
                    Text(
                        text = if (isEn) {
                            "📈 Y-Axis (Vertical Values / Series, Multi-Select) — Current (${chartObject.chartSeries.size} series): [$currentYPreview]"
                        } else {
                            "📈 【Y軸 (縦軸・数値系列)】にする列を選択 (複数選択可) — 現在 (${chartObject.chartSeries.size}系列): [$currentYPreview]"
                        },
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (tableColumns.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tableColumns.forEach { colOpt ->
                                val isCheckedY = if (chartObject.chartYColIndices.isNotEmpty()) {
                                    colOpt.colIndex in chartObject.chartYColIndices
                                } else {
                                    colOpt.headerName in activeYNames || "${colOpt.colLetter}列" in activeYNames
                                }
                                MasuSheetPillButton(
                                    label = if (isCheckedY) "☑ Y軸: ${colOpt.displayLabel}" else "☐ ${colOpt.displayLabel}",
                                    highlight = isCheckedY,
                                    onClick = {
                                        hasUnappliedRangeEdit = false
                                        onToggleYAxisColumn(colOpt.colIndex, false)
                                    }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEn) "Single Y-Axis Only:" else "Y軸を1列だけに切り替える:",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                            tableColumns.filter { it.isNumeric }.ifEmpty { tableColumns }.forEach { colOpt ->
                                val isOnlyY = if (chartObject.chartYColIndices.isNotEmpty()) {
                                    chartObject.chartYColIndices.size == 1 && chartObject.chartYColIndices.first() == colOpt.colIndex
                                } else {
                                    chartObject.chartSeries.size == 1 && chartObject.chartSeries.first().name == colOpt.headerName
                                }
                                MasuSheetPillButton(
                                    label = if (isEn) "Only ${colOpt.displayLabel}" else "${colOpt.displayLabel} のみ",
                                    highlight = isOnlyY,
                                    onClick = {
                                        hasUnappliedRangeEdit = false
                                        onToggleYAxisColumn(colOpt.colIndex, true)
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MasuSheetPillButton(
                            label = if (isEn) {
                                "📈 Set Selection ($currentSelectionRef) as Y-Axis"
                            } else {
                                "📈 選択中のセル範囲 ($currentSelectionRef) をY軸に設定"
                            },
                            highlight = true,
                            onClick = {
                                hasUnappliedRangeEdit = false
                                onBindCurrentSelectionAsYAxis(false)
                            }
                        )
                        MasuSheetPillButton(
                            label = if (isEn) {
                                "➕ Add Series from Sheet"
                            } else {
                                "➕ 系列を追加 (シート範囲から)"
                            },
                            onClick = {
                                hasUnappliedRangeEdit = false
                                onAddSeriesFromSheet("", "", "")
                            }
                        )
                        MasuSheetPillButton(
                            label = if (isEn) {
                                "🖱️ Drag on Sheet to Pick Y-Axis..."
                            } else {
                                "🖱️ シート上でY軸の範囲をドラッグ選択..."
                            },
                            onClick = { onStartAxisPickingOnSheet("y") }
                        )
                    }
                }

                // Section 0.5: Per-Series X-Axis Range & Y-Axis Range Configuration!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, Color(0xFF3B82F6), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEn) {
                                "📈 Per-Series X-Axis & Y-Axis Cell Ranges (${chartObject.chartSeries.size} Series)"
                            } else {
                                "📈 系列ごとの X軸範囲・Y軸範囲の個別指定 (${chartObject.chartSeries.size}系列)"
                            },
                            color = Color(0xFF60A5FA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        MasuSheetPillButton(
                            label = if (isEn) "➕ Add Series" else "➕ 新しい系列を追加",
                            highlight = true,
                            onClick = { onAddSeriesFromSheet("", "", "") }
                        )
                    }

                    chartObject.chartSeries.forEachIndexed { sIdx, series ->
                        var sNameInput by remember(chartObject.id, sIdx, series.name) {
                            mutableStateOf(series.name)
                        }
                        var sXRangeInput by remember(chartObject.id, sIdx, series.xRangeText) {
                            mutableStateOf(series.xRangeText)
                        }
                        var sYRangeInput by remember(chartObject.id, sIdx, series.yRangeText) {
                            mutableStateOf(series.yRangeText)
                        }
                        val isSelectedSeries = chartObject.selectedSubElement == "series:$sIdx"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelectedSeries) Color(0xFF1E3A8A).copy(alpha = 0.35f) else Color(0xFF1E293B))
                                .border(
                                    width = if (isSelectedSeries) 1.5.dp else 1.dp,
                                    color = if (isSelectedSeries) Color(0xFF60A5FA) else Color(0xFF334155),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isEn) "Series ${sIdx + 1}:" else "系列${sIdx + 1}:",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                BasicTextField(
                                    value = sNameInput,
                                    onValueChange = { sNameInput = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                                    cursorBrush = SolidColor(Color(0xFF60A5FA)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                                MasuSheetPillButton(
                                    label = if (isEn) "✓ Apply" else "✓ 反映",
                                    highlight = true,
                                    onClick = {
                                        onUpdateSeriesRanges(sIdx, sNameInput, sXRangeInput, sYRangeInput)
                                    }
                                )
                                if (chartObject.chartSeries.size > 1) {
                                    MasuSheetPillButton(
                                        label = "🗑️",
                                        onClick = { onRemoveSeriesAt(sIdx) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = if (isEn) "X Range:" else "X軸範囲:",
                                    color = Color(0xFFC084FC),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(52.dp)
                                )
                                BasicTextField(
                                    value = sXRangeInput,
                                    onValueChange = { sXRangeInput = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace),
                                    cursorBrush = SolidColor(Color(0xFFC084FC)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, Color(0xFF7C3AED), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                                MasuSheetPillButton(
                                    label = if (isEn) "🖱️ Pick X" else "🖱️ X軸選択",
                                    onClick = { onStartAxisPickingOnSheet("series_x:$sIdx") }
                                )
                                Text(
                                    text = if (isEn) "Y Range:" else "Y軸範囲:",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(52.dp)
                                )
                                BasicTextField(
                                    value = sYRangeInput,
                                    onValueChange = { sYRangeInput = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace),
                                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, Color(0xFF2563EB), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                                MasuSheetPillButton(
                                    label = if (isEn) "🖱️ Pick Y" else "🖱️ Y軸選択",
                                    onClick = { onStartAxisPickingOnSheet("series_y:$sIdx") }
                                )
                            }
                        }
                    }
                }

                // Section 1: Chart Data Range (`グラフ データの範囲`)
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
                        text = if (isEn) "1. Chart Data Range (Cell Range on Sheet)" else "1. グラフ データの範囲 (シート上のセル範囲からグラフ化)",
                        color = Color(0xFF4ADE80),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isEn) "Range:" else "データ範囲:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.5.sp
                        )
                        BasicTextField(
                            value = rangeInput,
                            onValueChange = {
                                rangeInput = it
                                hasUnappliedRangeEdit = true
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(Color(0xFF4ADE80)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.5.dp, Color(0xFF1F9D55), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                        MasuSheetPillButton(
                            label = if (isEn) "📊 Plot Range" else "📊 この範囲でグラフ化",
                            highlight = true,
                            tag = "ms_btn_chart_apply_range",
                            onClick = {
                                hasUnappliedRangeEdit = false
                                onApplyDataRange(rangeInput)
                            }
                        )
                    }

                    // Quick Range Picking Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MasuSheetPillButton(
                            label = if (isEn) {
                                "📍 Use Current Selection ($currentSelectionRef)"
                            } else {
                                "📍 現在の選択範囲 ($currentSelectionRef) を反映"
                            },
                            highlight = true,
                            onClick = onBindCurrentSelection
                        )
                        MasuSheetPillButton(
                            label = if (isEn) {
                                "🖱️ Drag on Sheet to Pick Range..."
                            } else {
                                "🖱️ シート上をドラッグして範囲を選択..."
                            },
                            highlight = true,
                            onClick = onStartRangePickingOnSheet
                        )
                        MasuSheetPillButton(
                            label = if (isEn) "⇄ Switch Row/Column" else "⇄ 行/列の切り替え",
                            onClick = onSwitchRowColumn
                        )
                    }

                    // Detected Tables on Current Sheet
                    if (detectedTables.isNotEmpty()) {
                        Text(
                            text = if (isEn) {
                                "📋 Detected Data Tables on Current Sheet (1-Tap Plot):"
                            } else {
                                "📋 現在のシート内で検出されたデータ表 (ワンタップでグラフ化):"
                            },
                            color = Color(0xFF38BDF8),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            detectedTables.forEach { tbl ->
                                MasuSheetPillButton(
                                    label = "📋 ${tbl.label}",
                                    highlight = chartObject.sourceRangeText.equals(tbl.rangeText, ignoreCase = true),
                                    onClick = {
                                        rangeInput = tbl.rangeText
                                        onApplyDataRange(tbl.rangeText)
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 2: Legend Entries (Series) & Horizontal (Category) Axis Labels
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
                        text = if (isEn) {
                            "2. Legend Entries (Series) & Horizontal (Category) Axis Labels"
                        } else {
                            "2. 凡例項目 (系列) と 横 (項目) 軸ラベルの直接編集"
                        },
                        color = Color(0xFF4ADE80),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isEn) "Chart Title:" else "グラフ タイトル:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            modifier = Modifier.width(105.dp)
                        )
                        BasicTextField(
                            value = titleInput,
                            onValueChange = {
                                titleInput = it
                                hasUnappliedCustomEdit = true
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(Color(0xFF4ADE80)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isEn) "Categories (X):" else "横軸ラベル (カンマ区切り):",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            modifier = Modifier.width(105.dp)
                        )
                        BasicTextField(
                            value = categoriesCsv,
                            onValueChange = {
                                categoriesCsv = it
                                hasUnappliedCustomEdit = true
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFF4ADE80)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    Text(
                        text = if (isEn) {
                            "Series (1 per line: `SeriesName: 10, 20, 30`):"
                        } else {
                            "凡例項目・データ系列 (1行に1系列 `系列名: 数値1, 数値2, ...` 形式):"
                        },
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                    BasicTextField(
                        value = seriesTextLines,
                        onValueChange = {
                            seriesTextLines = it
                            hasUnappliedCustomEdit = true
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(Color(0xFF4ADE80)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp))
                            .padding(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MasuSheetPillButton(
                            label = if (isEn) "＋ Add Series from Sheet" else "＋ 系列を追加 (シートから)",
                            onClick = {
                                onAddSeriesFromSheet("", "", "")
                            }
                        )
                        MasuSheetPillButton(
                            label = if (isEn) "✓ Apply Custom Series & Labels" else "✓ 編集した系列・ラベルをグラフに反映",
                            highlight = true,
                            onClick = {
                                val cats = categoriesCsv.split(",", "、")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                val parsedSeries = seriesTextLines.lines()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .mapIndexed { idx, line ->
                                        val colonIdx = line.indexOf(':').takeIf { it >= 0 } ?: line.indexOf('：')
                                        val sName = if (colonIdx >= 0) line.substring(0, colonIdx).trim() else "系列${idx + 1}"
                                        val rawVals = if (colonIdx >= 0) line.substring(colonIdx + 1) else line
                                        val nums = rawVals.split(",", "、", " ")
                                            .mapNotNull { token ->
                                                token.trim()
                                                    .replace("¥", "")
                                                    .replace("$", "")
                                                    .replace("%", "")
                                                    .toDoubleOrNull()
                                            }
                                        sName to nums
                                    }
                                    .filter { it.second.isNotEmpty() }
                                hasUnappliedCustomEdit = false
                                onApplyCustomSeriesAndCategories(titleInput, cats, parsedSeries)
                            }
                        )
                    }
                }
            }

            // Bottom Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (chartObject.sourceRangeText.isNotBlank()) {
                        if (isEn) "Current Source: ${chartObject.sourceRangeText}" else "現在の参照元: ${chartObject.sourceRangeText}"
                    } else {
                        if (isEn) "Standalone Chart (Select a range above to bind)" else "単体グラフ (上の範囲指定またはシート選択でデータ連動可能)"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MasuSheetPillButton(
                        label = if (isEn) "Close" else "閉じる",
                        onClick = onDismiss
                    )
                    MasuSheetPillButton(
                        label = if (isEn) "✓ OK" else "✓ OK (完了)",
                        highlight = true,
                        onClick = {
                            if (hasUnappliedRangeEdit || chartObject.chartSeries.isEmpty()) {
                                onApplyDataRange(rangeInput)
                            } else if (hasUnappliedCustomEdit) {
                                val cats = categoriesCsv.split(",", "、")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                val parsedSeries = seriesTextLines.lines()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .mapIndexed { idx, line ->
                                        val colonIdx = line.indexOf(':').takeIf { it >= 0 } ?: line.indexOf('：')
                                        val sName = if (colonIdx >= 0) line.substring(0, colonIdx).trim() else "系列${idx + 1}"
                                        val rawVals = if (colonIdx >= 0) line.substring(colonIdx + 1) else line
                                        val nums = rawVals.split(",", "、", " ")
                                            .mapNotNull { token ->
                                                token.trim()
                                                    .replace("¥", "")
                                                    .replace("$", "")
                                                    .replace("%", "")
                                                    .toDoubleOrNull()
                                            }
                                        sName to nums
                                    }
                                    .filter { it.second.isNotEmpty() }
                                onApplyCustomSeriesAndCategories(titleInput, cats, parsedSeries)
                            } else {
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}
