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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masusheet.app.catalog.SpreadsheetFunctionCatalog
import com.masusheet.app.core.SpreadsheetI18n
import com.masusheet.app.ui.LocalDarkTheme
import com.masusheet.app.ui.ribbon.MasuSheetPillButton

/**
 * ============================================================================
 * [EN] 523-Function Insert Wizard Dialog (`fx`)
 * Implements [SpreadsheetFunctionWizardDialog] with category filtering, keyword search, virtualized `LazyColumn` list, and argument signature preview.
 *
 * ---
 * [JA / 日本語解説]
 * カテゴリ絞込・キーワード検索・`LazyColumn` 仮想化リスト・構文プレビューを備えた523関数対応の「関数の挿入 (`fx`)」ウィザード ([SpreadsheetFunctionWizardDialog]) です。
 * ============================================================================
 */

// ============================================================================
// 2. FULL 523-FUNCTION SPREADSHEET WIZARD DIALOG (`fx 関数の挿入`)
// ============================================================================

/**
 * [EN] Searchable 523-function insertion wizard (`fx`) using virtualized `LazyColumn` rendering and syntax previews.
 * [JA] `LazyColumn` 仮想化リストとカテゴリ・キーワード検索で523関数から挿入できる「関数の挿入 (`fx`)」ダイアログです。
 */
@Composable
fun SpreadsheetFunctionWizardDialog(
    activeAddress: String,
    isEn: Boolean = false,
    onInsertFormula: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("すべて") }
    var selectedFunc by remember { mutableStateOf(SpreadsheetFunctionCatalog.ALL_ENTRIES.first()) }
    var argsInput by remember { mutableStateOf("B2:D5") }

    val filteredFunctions = remember(searchQuery, selectedCategory) {
        SpreadsheetFunctionCatalog.search(searchQuery, selectedCategory)
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
                .widthIn(max = 640.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .border(1.5.dp, Color(0xFF1F9D55), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) {
                        "fx Insert Function — All ${SpreadsheetFunctionCatalog.ALL_ENTRIES.size} Functions (Cell $activeAddress)"
                    } else {
                        "fx 関数の挿入 — 全 ${SpreadsheetFunctionCatalog.ALL_ENTRIES.size} 関数対応 (セル $activeAddress)"
                    },
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                MasuSheetPillButton(if (isEn) "✕ Close" else "✕ 閉じる", onClick = onDismiss)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (isEn) "🔍 Search:" else "🔍 関数検索:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                    cursorBrush = SolidColor(Color(0xFF4ADE80)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Text(
                    if (isEn) "${filteredFunctions.size} funcs" else "${filteredFunctions.size} 件",
                    color = Color(0xFF4ADE80),
                    fontSize = 11.5.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Category chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                SpreadsheetFunctionCatalog.CATEGORIES_JA.forEach { cat ->
                    MasuSheetPillButton(
                        label = SpreadsheetI18n.t(cat, isEn),
                        highlight = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Function list (LazyColumn for O(1) initial composition across all 523 functions)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                    .padding(6.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(filteredFunctions, key = { it.name }) { fnEntry ->
                        val isSel = fnEntry.name == selectedFunc.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isSel) Color(0xFF163828) else Color.Transparent)
                                .clickable {
                                    selectedFunc = fnEntry
                                    argsInput = when {
                                        fnEntry.syntax.endsWith("()") -> ""
                                        fnEntry.name == "IF" -> if (isEn) "B2>=50000, \"Target Met\", \"Below\"" else "B2>=50000, \"達成\", \"未達\""
                                        fnEntry.name == "VLOOKUP" -> "A2, A2:D10, 2, FALSE"
                                        fnEntry.name == "XLOOKUP" -> if (isEn) "A2, A2:A10, B2:B10, \"Not Found\"" else "A2, A2:A10, B2:B10, \"該当なし\""
                                        fnEntry.name in setOf("SUMIF", "COUNTIF", "AVERAGEIF") -> "B2:B10, \">=50000\""
                                        fnEntry.name in setOf("ROUND", "ROUNDUP", "ROUNDDOWN", "TRUNC") -> "B2, 0"
                                        fnEntry.name in setOf("INT", "ABS", "SIGN", "SQRT", "LN", "LOG10", "EXP", "EVEN", "ODD", "FACT", "SIN", "COS", "TAN") -> "B2"
                                        fnEntry.name == "TEXT" -> "B2, \"#,##0\""
                                        fnEntry.name == "TRANSLATE" -> "\"Hello World\", \"en\", \"ja\""
                                        fnEntry.name == "COPILOT" -> if (isEn) "\"Summarize sales trend\", A1:D5" else "\"売上傾向を要約して\", A1:D5"
                                        else -> "B2:D5"
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = fnEntry.name,
                                    color = Color(0xFF4ADE80),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(120.dp)
                                )
                                Text(
                                    text = if (isEn) fnEntry.descriptionEn else fnEntry.descriptionJa,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.5.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = SpreadsheetI18n.t(fnEntry.categoryJa, isEn),
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selected function details & Argument builder
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
                    text = if (isEn) "Syntax: ${selectedFunc.syntaxEn}" else "構文: ${selectedFunc.syntax}",
                    color = Color(0xFF4ADE80),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (isEn) selectedFunc.descriptionEn else selectedFunc.descriptionJa,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "=${selectedFunc.name}(",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    BasicTextField(
                        value = argsInput,
                        onValueChange = { argsInput = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(Color(0xFF4ADE80)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                    Text(
                        text = ")",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    MasuSheetPillButton(
                        label = if (isEn) "✓ Insert Formula" else "✓ 数式をセルに挿入",
                        highlight = true,
                        onClick = {
                            onInsertFormula("=${selectedFunc.name}($argsInput)")
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
    }
}
