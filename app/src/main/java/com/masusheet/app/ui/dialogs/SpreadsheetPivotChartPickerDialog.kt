package com.masusheet.app.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masusheet.app.catalog.SpreadsheetChartTypeCatalog
import com.masusheet.app.core.SpreadsheetI18n
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.SheetObjectKind
import com.masusheet.app.ui.LocalDarkTheme

/**
 * ============================================================================
 * [EN] 32-Subtype Chart & PivotChart Selector Dialog
 * Implements [SpreadsheetPivotChartPickerDialog] allowing users to browse all 10 chart categories and 32 subtypes to insert a new chart or convert an existing chart.
 *
 * ---
 * [JA / 日本語解説]
 * 全10カテゴリ・32サブタイプから新規グラフの挿入や既存グラフの種類変更を行えるグラフ選択ダイアログ ([SpreadsheetPivotChartPickerDialog]) です。
 * ============================================================================
 */

// ============================================================================
// 7. SPREADSHEET PIVOTCHART & ALL 32 CHART TYPES SELECTOR POPUP DIALOG
// ============================================================================

/**
 * [EN] Visual gallery dialog for browsing and selecting any of the 10 categories × 32 chart subtypes.
 * [JA] 全10カテゴリ・32種類のグラフサブタイプを一覧から選択して挿入・種類変更できるグラフ選択ダイアログです。
 */
@Composable
fun SpreadsheetPivotChartPickerDialog(
    state: MasuSheetUiState,
    onSelectChartType: (subType: String, insertNew: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val isEn = state.language == "en"
    val currentSheetIdx = state.viewport.sheetIndex
    val activeChartObj = remember(state.selectedObjectId, state.sheetObjects, currentSheetIdx) {
        state.selectedObjectId
            ?.let { id -> state.sheetObjects.firstOrNull { it.id == id && it.sheetIndex == currentSheetIdx && it.kind == SheetObjectKind.CHART } }
            ?: state.sheetObjects.lastOrNull { it.sheetIndex == currentSheetIdx && it.kind == SheetObjectKind.CHART }
    }
    val initialSubType = remember(activeChartObj?.subType) {
        SpreadsheetChartTypeCatalog.normalizeSubType(activeChartObj?.subType ?: "column")
    }
    val initialCategory = remember(initialSubType) {
        SpreadsheetChartTypeCatalog.findSpec(initialSubType).categoryId
    }

    var selectedCategoryId by remember(initialCategory) { mutableStateOf(initialCategory) }
    var selectedSubType by remember(initialSubType) { mutableStateOf(initialSubType) }

    val subtypesInCategory = remember(selectedCategoryId) {
        if (selectedCategoryId == "recommended") {
            listOf(
                "column", "column_stacked", "line_markers", "pie",
                "doughnut", "bar_h", "area_stacked", "combo",
                "waterfall", "treemap", "radar_markers", "pareto"
            ).map { SpreadsheetChartTypeCatalog.findSpec(it) }
        } else {
            SpreadsheetChartTypeCatalog.ALL_SUBTYPES.filter { it.categoryId == selectedCategoryId }
        }
    }
    val activeSpec = remember(selectedSubType) {
        SpreadsheetChartTypeCatalog.findSpec(selectedSubType)
    }

    BackHandler(onBack = onDismiss)
    CompositionLocalProvider(LocalDarkTheme provides true) {
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
                    .widthIn(max = 660.dp)
                    .heightIn(max = 520.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.5.dp, Color(0xFF107C41), RoundedCornerShape(12.dp))
                    .clickable(enabled = false) {}
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) {
                                "📊 Insert Chart / Change Chart Type (10 Categories · 32 Chart Types)"
                            } else {
                                "📊 グラフの挿入 / グラフの種類の変更 (全10カテゴリ・32種類)"
                            },
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (activeChartObj != null) {
                                if (isEn) "Currently selected chart: ${SpreadsheetI18n.translateChartDisplayText(activeChartObj.title, true)} (${activeChartObj.subType})"
                                else "選択中のグラフ: ${SpreadsheetI18n.translateChartDisplayText(activeChartObj.title, false)} (種類をワンタップで変更、または新規挿入できます)"
                            } else {
                                if (isEn) "Select any of the 32 chart types below to create a Chart"
                                else "全32種類の標準グラフから選択して作成できます"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF334155))
                            .border(1.dp, Color(0xFF64748B), RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEn) "✕ Close" else "✕ 閉じる",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Two-pane body: Left = 10 Categories + Recommended, Right = Subtypes & Live Preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left Category Sidebar
                    Column(
                        modifier = Modifier
                            .width(195.dp)
                            .heightIn(min = 220.dp, max = 360.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(6.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val recSelected = selectedCategoryId == "recommended"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (recSelected) Color(0xFF107C41) else Color.Transparent)
                                .clickable {
                                    selectedCategoryId = "recommended"
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⭐", fontSize = 12.sp)
                            Text(
                                text = if (isEn) "Recommended (12)" else "おすすめグラフ (12)",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = if (recSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }

                        SpreadsheetChartTypeCatalog.CATEGORIES.forEach { cat ->
                            val isCatSelected = selectedCategoryId == cat.id
                            val count = SpreadsheetChartTypeCatalog.ALL_SUBTYPES.count { it.categoryId == cat.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isCatSelected) Color(0xFF107C41) else Color.Transparent)
                                    .clickable {
                                        selectedCategoryId = cat.id
                                        val firstInCat = SpreadsheetChartTypeCatalog.ALL_SUBTYPES.firstOrNull { it.categoryId == cat.id }
                                        if (firstInCat != null) {
                                            selectedSubType = firstInCat.subType
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(cat.icon, fontSize = 12.sp)
                                    Text(
                                        text = if (isEn) cat.labelEn else cat.labelJa,
                                        color = if (isCatSelected) Color.White else Color(0xFFE2E8F0),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = "$count",
                                    color = if (isCatSelected) Color(0xFFBBF7D0) else Color(0xFF64748B),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Right Subtype Cards & Selected Preview
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 220.dp, max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subtypesInCategory.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowItems.forEach { spec ->
                                    val isSel = spec.subType == selectedSubType
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) Color(0xFF14532D) else Color(0xFF0F172A))
                                            .border(
                                                width = if (isSel) 1.5.dp else 1.dp,
                                                color = if (isSel) Color(0xFF4ADE80) else Color(0xFF334155),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                selectedSubType = spec.subType
                                                if (activeChartObj != null) {
                                                    onSelectChartType(spec.subType, false)
                                                }
                                            }
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(spec.icon, fontSize = 15.sp)
                                                Text(
                                                    text = if (isEn) spec.labelEn else spec.labelJa,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                            if (isSel) {
                                                Text("✓", color = Color(0xFF4ADE80), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text(
                                            text = if (isEn) spec.descEn else spec.descJa,
                                            color = if (isSel) Color(0xFFDCFCE7) else Color(0xFF94A3B8),
                                            fontSize = 10.5.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        // Selected Subtype Details Banner
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF1E3A8A), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isEn) {
                                    "${activeSpec.icon} Selected: ${activeSpec.labelEn} (${activeSpec.subType})"
                                } else {
                                    "${activeSpec.icon} 選択中: ${activeSpec.labelJa} (${activeSpec.labelEn})"
                                },
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEn) activeSpec.descEn else activeSpec.descJa,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Bottom Action Bar (pinned)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeChartObj != null) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF107C41))
                                .border(1.dp, Color(0xFF4ADE80), RoundedCornerShape(6.dp))
                                .clickable {
                                    onSelectChartType(selectedSubType, false)
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEn) "✓ Apply to Selected Chart" else "✓ 選択中のグラフに適用",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeChartObj == null) Color(0xFF107C41) else Color(0xFF0284C7))
                            .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                            .clickable {
                                onSelectChartType(selectedSubType, true)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEn) "📊 Insert as New Chart" else "📊 新規グラフとして挿入",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF334155))
                            .border(1.dp, Color(0xFF64748B), RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEn) "Cancel" else "キャンセル",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
