package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] Table Header AutoFilter & Multi-Select Value Filter Popup Dialog
 * Implements [SpreadsheetColumnFilterPopupDialog] for ascending/descending sort, search box filtering, and per-value checkbox inclusion on table columns.
 *
 * ---
 * [JA / 日本語解説]
 * テーブル見出しの `[▾]` ボタンから開く昇順・降順ソート、値検索、チェックボックス複数選択フィルタポップアップ ([SpreadsheetColumnFilterPopupDialog]) です。
 * ============================================================================
 */

// ============================================================================
// 6. SPREADSHEET AUTOFILTER & SORT POPUP MENU DIALOG (ON TABLE HEADER `[▾]`)
// ============================================================================

/**
 * [EN] AutoFilter popup dialog attached to table headers with sort actions, search box, and distinct value checkboxes.
 * [JA] テーブル列見出しのオートフィルタ（昇順／降順ソート・値検索・複数値チェック選択）ポップアップダイアログです。
 */
@Composable
fun SpreadsheetColumnFilterPopupDialog(
    popup: TableColumnFilterPopupState,
    isEn: Boolean,
    onSortAscending: () -> Unit,
    onSortDescending: () -> Unit,
    onSortByColor: () -> Unit,
    onClearFilter: () -> Unit,
    onApplyFilter: (selectedValues: Set<String>, includeBlanks: Boolean, customOperator: String, customQuery: String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchText by remember(popup) { mutableStateOf("") }
    var selectedSet by remember(popup) { mutableStateOf(popup.checkedValues) }
    var includeBlanks by remember(popup) { mutableStateOf(popup.includeBlanks) }
    var showCustomCondition by remember(popup) { mutableStateOf(popup.customFilterOperator.isNotBlank()) }
    var customOp by remember(popup) { mutableStateOf(popup.customFilterOperator) }
    var customQuery by remember(popup) { mutableStateOf(popup.customFilterOperand) }

    val matchingValues = remember(popup.distinctValues, searchText) {
        val q = searchText.trim()
        if (q.isEmpty()) {
            popup.distinctValues
        } else {
            popup.distinctValues.filter { it.contains(q, ignoreCase = true) }
        }
    }

    val allMatchingChecked = matchingValues.isNotEmpty() &&
        matchingValues.all { it in selectedSet } &&
        (!popup.hasBlanks || includeBlanks || searchText.isNotBlank())

    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 380.dp)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E293B))
                .border(1.5.dp, Color(0xFF107C41), RoundedCornerShape(10.dp))
                .clickable(enabled = false) {}
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header bar with column title (pinned at top)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF107C41)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▼", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = if (isEn) "Sort & Filter: \"${popup.columnTitle}\"" else "並べ替えとフィルター: 「${popup.columnTitle}」",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                MasuSheetPillButton(if (isEn) "✕ Close" else "✕ 閉じる", onClick = onDismiss)
            }

            // Scrollable middle container so bottom OK / Cancel buttons are NEVER squished on landscape screens
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Sort Actions (Matching Windows 11 Spreadsheet: A↓Z 昇順 / Z↓A 降順 / 色で並べ替え)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(7.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { onSortAscending() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("A↓Z", color = Color(0xFF4ADE80), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(
                            text = if (popup.isNumericColumn) {
                                if (isEn) "Sort Smallest to Largest (Ascending)" else "昇順 (小さい順)"
                            } else {
                                if (isEn) "Sort A to Z (Ascending)" else "昇順 (A → Z)"
                            },
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { onSortDescending() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Z↓A", color = Color(0xFF38BDF8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(
                            text = if (popup.isNumericColumn) {
                                if (isEn) "Sort Largest to Smallest (Descending)" else "降順 (大きい順)"
                            } else {
                                if (isEn) "Sort Z to A (Descending)" else "降順 (Z → A)"
                            },
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { onSortByColor() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎨", fontSize = 12.sp)
                        Text(
                            text = if (isEn) "Sort by Color" else "色で並べ替え",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp
                        )
                    }
                }

                // 2. Clear Filter & Text/Number Filter Submenu Toggle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(7.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { onClearFilter() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("❎", fontSize = 11.5.sp)
                        Text(
                            text = if (isEn) "Clear Filter From \"${popup.columnTitle}\"" else "\"${popup.columnTitle}\" からフィルターをクリア",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { showCustomCondition = !showCustomCondition }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (popup.isNumericColumn) {
                                if (isEn) "🔢 Number Filters (Equals, Greater Than...)" else "🔢 数値フィルター (指定の値より大きい等)"
                            } else {
                                if (isEn) "🔤 Text Filters (Contains, Equals...)" else "🔤 テキスト フィルター (指定の値を含む等)"
                            },
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.5.sp
                        )
                        Text(if (showCustomCondition) "▼" else "▶", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    if (showCustomCondition) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val ops = if (popup.isNumericColumn) {
                                listOf(
                                    "" to if (isEn) "None" else "指定なし",
                                    "gt" to if (isEn) "> Greater" else "> より大きい",
                                    "lt" to if (isEn) "< Less" else "< より小さい",
                                    "equals" to if (isEn) "= Equals" else "= 等しい"
                                )
                            } else {
                                listOf(
                                    "" to if (isEn) "None" else "指定なし",
                                    "contains" to if (isEn) "Contains" else "含む",
                                    "equals" to if (isEn) "Equals" else "等しい"
                                )
                            }
                            ops.forEach { (opId, opLabel) ->
                                MasuSheetPillButton(
                                    label = opLabel,
                                    highlight = customOp == opId,
                                    onClick = { customOp = opId }
                                )
                            }
                        }
                        if (customOp.isNotEmpty()) {
                            BasicTextField(
                                value = customQuery,
                                onValueChange = { newQ -> customQuery = newQ },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                decorationBox = { innerTextField ->
                                    if (customQuery.isEmpty()) {
                                        Text(
                                            text = if (isEn) "Enter filter value..." else "条件の値を入力...",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }

                // 3. Real-time Search Box (`検索 (Search)`)
                BasicTextField(
                    value = searchText,
                    onValueChange = { q ->
                        searchText = q
                        val trimmed = q.trim()
                        if (trimmed.isNotEmpty()) {
                            selectedSet = popup.distinctValues.filter { it.contains(trimmed, ignoreCase = true) }.toSet()
                            includeBlanks = false
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                    cursorBrush = SolidColor(Color(0xFF4ADE80)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    decorationBox = { inner ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchText.isEmpty()) {
                                    Text(
                                        text = if (isEn) "Search" else "検索",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                                inner()
                            }
                            Text("🔍", fontSize = 12.sp)
                        }
                    }
                )

                // 4. Checkbox List (`☑ (すべて選択)` + distinct values + `(空白セル)`)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 150.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(7.dp))
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // (Select All) row with generous vertical padding so top text is never clipped
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                if (allMatchingChecked) {
                                    selectedSet = selectedSet - matchingValues.toSet()
                                    if (searchText.isBlank()) includeBlanks = false
                                } else {
                                    selectedSet = selectedSet + matchingValues.toSet()
                                    if (searchText.isBlank()) includeBlanks = true
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (allMatchingChecked) "☑" else "☐",
                            color = if (allMatchingChecked) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (searchText.isBlank()) {
                                if (isEn) "(Select All)" else "(すべて選択)"
                            } else {
                                if (isEn) "(Select All Search Results)" else "(すべての検索結果を選択)"
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Individual distinct value checkboxes
                    matchingValues.forEach { itemVal ->
                        val checked = itemVal in selectedSet
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    selectedSet = if (checked) selectedSet - itemVal else selectedSet + itemVal
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (checked) "☑" else "☐",
                                color = if (checked) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = itemVal,
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Optional (Blanks) row
                    if (popup.hasBlanks && searchText.isBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { includeBlanks = !includeBlanks }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (includeBlanks) "☑" else "☐",
                                color = if (includeBlanks) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEn) "(Blanks)" else "(空白セル)",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 5. Pinned Bottom Action Bar (OK / Cancel) — guaranteed >= 34dp height so text is always crisp & visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF107C41))
                        .border(1.dp, Color(0xFF4ADE80), RoundedCornerShape(6.dp))
                        .clickable {
                            onApplyFilter(selectedSet, includeBlanks, customOp, customQuery)
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEn) "✓ OK (Apply)" else "✓ OK (適用)",
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
