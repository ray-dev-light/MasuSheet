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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masusheet.app.R
import com.masusheet.app.catalog.SpreadsheetRibbonCatalog
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.ui.LocalDarkTheme
import com.masusheet.app.ui.ribbon.MasuSheetPillButton
import com.masusheet.app.viewmodel.onDefineName
import com.masusheet.app.viewmodel.onFindNext
import com.masusheet.app.viewmodel.onGoToReference
import com.masusheet.app.viewmodel.onGoToSpecial
import com.masusheet.app.viewmodel.onMoveSheet
import com.masusheet.app.viewmodel.onSelectFindMatch
import com.masusheet.app.viewmodel.onSetCellComment
import com.masusheet.app.viewmodel.onSetCellHyperlink
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Command Center Dialog (Find/Replace, Conditional Format, Validation, Sheet Manager, All 292 Commands)
 * Implements [SpreadsheetCommandCenterDialog] providing Find & Replace, Conditional Formatting, Data Validation (`list` / numeric rules), Goal Seek, Sheet Unhide manager, and searchable 292-command launcher.
 *
 * ---
 * [JA / 日本語解説]
 * 検索と置換、条件付き書式、データの入力規則、ゴールシーク、非表示シート再表示マネージャ、および全292コマンド検索ランチャーを統合した [SpreadsheetCommandCenterDialog] です。
 * ============================================================================
 */

// ============================================================================
// 4. SPREADSHEET COMMAND CENTER DIALOG (FIND/REPLACE, CONDITIONAL FORMAT, VALIDATION, GOAL SEEK, ALL 292 COMMANDS)
// ============================================================================

/**
 * [EN] Unified command dialog hosting Find & Replace, Conditional Formatting, Data Validation, Goal Seek, Sheet Unhide, and 292 commands.
 * [JA] 検索と置換・条件付き書式・データの入力規則・ゴールシーク・非表示シート管理・全292コマンド検索を提供するダイアログです。
 */
@Composable
fun SpreadsheetCommandCenterDialog(
    state: MasuSheetUiState,
    initialMode: String = "allCommands",
    onExecuteCommand: (String, String) -> Unit,
    onOpenColorPickerForSheetTab: () -> Unit,
    onFindNext: (query: String, forward: Boolean, matchCase: Boolean, wholeCell: Boolean) -> Unit = { q, fwd, _, _ ->
        onExecuteCommand("edit.find", "{\"find\":${JSONObject.quote(q)},\"backward\":${!fwd}}")
    },
    onFindAll: (query: String, matchCase: Boolean, wholeCell: Boolean) -> Unit = { q, _, _ ->
        onExecuteCommand("edit.find", "{\"find\":${JSONObject.quote(q)}}")
    },
    onSelectFindMatch: (index: Int) -> Unit = {},
    onReplaceCurrent: (query: String, replacement: String, matchCase: Boolean, wholeCell: Boolean) -> Unit = { q, r, _, _ ->
        onExecuteCommand("edit.replace", "{\"find\":${JSONObject.quote(q)},\"replace\":${JSONObject.quote(r)},\"replaceAll\":false}")
    },
    onReplaceAll: (query: String, replacement: String, matchCase: Boolean, wholeCell: Boolean) -> Unit = { q, r, _, _ ->
        onExecuteCommand("edit.replace", "{\"find\":${JSONObject.quote(q)},\"replace\":${JSONObject.quote(r)},\"replaceAll\":true}")
    },
    onGoToReference: (reference: String) -> Unit = { ref ->
        onExecuteCommand("edit.goTo", "{\"reference\":${JSONObject.quote(ref)}}")
    },
    onGoToSpecial: (kind: String) -> Unit = { kind ->
        onExecuteCommand("edit.goToSpecial", "{\"kind\":${JSONObject.quote(kind)}}")
    },
    onMoveSheet: (fromIndex: Int, toIndex: Int) -> Unit = { from, to ->
        onExecuteCommand("home.moveOrCopySheet", "{\"sheet\":$from,\"to\":$to}")
    },
    onSetCellComment: (comment: String) -> Unit = {},
    onSetCellHyperlink: (url: String, label: String) -> Unit = { _, _ -> },
    onDefineName: (name: String, refersTo: String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val isEn = state.language == "en"
    val normalizedInitialMode = remember(initialMode) {
        when (initialMode) {
            "find", "replace", "findReplace" -> "findReplace"
            "goTo", "nameManager" -> "goTo"
            "conditional" -> "conditional"
            "validation" -> "validation"
            "goalSeek" -> "goalSeek"
            "comment", "hyperlink" -> "comment"
            "sheetManage" -> "sheetManage"
            else -> "allCommands"
        }
    }
    var mode by remember(normalizedInitialMode) { mutableStateOf(normalizedInitialMode) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var wholeCell by remember { mutableStateOf(false) }
    var goToRefText by remember { mutableStateOf(state.viewport.activeAddress) }
    var newDefinedName by remember { mutableStateOf("") }
    var newDefinedRef by remember(state.viewport.activeAddress, state.viewport.sheetName) {
        mutableStateOf("${state.viewport.sheetName}!${state.viewport.activeAddress}")
    }
    val activePackedKey = remember(state.viewport.sheetIndex, state.viewport.activeRow, state.viewport.activeCol) {
        state.viewport.sheetIndex to NativeViewportSnapshot.packCellKey(state.viewport.activeRow, state.viewport.activeCol)
    }
    var commentText by remember(activePackedKey) {
        mutableStateOf(state.cellComments[activePackedKey].orEmpty())
    }
    var hyperlinkUrl by remember(activePackedKey) {
        mutableStateOf(state.cellHyperlinks[activePackedKey].orEmpty())
    }
    var hyperlinkLabel by remember(activePackedKey) {
        mutableStateOf(state.viewport.cellAt(state.viewport.activeRow, state.viewport.activeCol)?.displayText.orEmpty())
    }
    var validationList by remember(isEn) {
        mutableStateOf(if (isEn) "Done, In Progress, Not Started, On Hold" else "完了, 進行中, 未着手, 保留")
    }
    var goalTargetCell by remember { mutableStateOf("E2") }
    var goalTargetValue by remember { mutableStateOf("200000") }
    var goalChangingCell by remember { mutableStateOf("B2") }
    var sheetNewName by remember(state.viewport.sheetName) { mutableStateOf(state.viewport.sheetName) }
    var cmdSearch by remember { mutableStateOf("") }

    CompositionLocalProvider(LocalDarkTheme provides true) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        BackHandler(onBack = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 650.dp)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .border(1.5.dp, Color(0xFF1F9D55), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) {
                        "🛠️ Power Tools & All 292 Commands Center"
                    } else {
                        "🛠️ 高度ツール & 全292コマンド実行センター"
                    },
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                MasuSheetPillButton(if (isEn) "✕ Close" else "✕ 閉じる", onClick = onDismiss)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                MasuSheetPillButton(if (isEn) "🔍 Find & Replace" else "🔍 検索と置換", highlight = mode == "findReplace", onClick = { mode = "findReplace" })
                MasuSheetPillButton(if (isEn) "📍 Go To / Names" else "📍 ジャンプ・名前定義", highlight = mode == "goTo", onClick = { mode = "goTo" })
                MasuSheetPillButton(if (isEn) "🌈 Conditional" else "🌈 条件付き書式", highlight = mode == "conditional", onClick = { mode = "conditional" })
                MasuSheetPillButton(if (isEn) "✅ Validation" else "✅ データの入力規則", highlight = mode == "validation", onClick = { mode = "validation" })
                MasuSheetPillButton(if (isEn) "🎯 Goal Seek" else "🎯 ゴールシーク", highlight = mode == "goalSeek", onClick = { mode = "goalSeek" })
                MasuSheetPillButton(if (isEn) "💬 Comment / Link" else "💬 コメント・リンク", highlight = mode == "comment", onClick = { mode = "comment" })
                MasuSheetPillButton(if (isEn) "📑 Sheet Manage" else "📑 シート管理・保護", highlight = mode == "sheetManage", onClick = { mode = "sheetManage" })
                MasuSheetPillButton(if (isEn) "📋 All 292 Commands" else "📋 全292コマンド一覧", highlight = mode == "allCommands", onClick = { mode = "allCommands" })
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (mode) {
                "findReplace" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isEn) "Find & Replace in '${state.viewport.sheetName}' (Active: ${state.viewport.activeAddress}):"
                            else "シート「${state.viewport.sheetName}」内の検索と置換 (現在セル: ${state.viewport.activeAddress}):",
                            color = Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isEn) "Find what:" else "検索文字列:", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(90.dp))
                            BasicTextField(
                                value = findText,
                                onValueChange = { findText = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp)).padding(8.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isEn) "Replace with:" else "置換文字列:", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(90.dp))
                            BasicTextField(
                                value = replaceText,
                                onValueChange = { replaceText = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp)).padding(8.dp)
                            )
                        }
                        // Search Options Row
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .clickable { matchCase = !matchCase }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(if (matchCase) "☑" else "☐", color = if (matchCase) Color(0xFF4ADE80) else Color(0xFF94A3B8), fontSize = 13.sp)
                                Text(if (isEn) "Match case" else "大文字と小文字を区別する", color = Color(0xFFE2E8F0), fontSize = 11.5.sp)
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .clickable { wholeCell = !wholeCell }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(if (wholeCell) "☑" else "☐", color = if (wholeCell) Color(0xFF4ADE80) else Color(0xFF94A3B8), fontSize = 13.sp)
                                Text(if (isEn) "Match entire cell contents" else "セル内容が完全に同一であるものを検索", color = Color(0xFFE2E8F0), fontSize = 11.5.sp)
                            }
                        }
                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MasuSheetPillButton(
                                label = if (isEn) "🔍 Find Next" else "🔍 次を検索",
                                highlight = true,
                                onClick = {
                                    onFindNext(findText, true, matchCase, wholeCell)
                                }
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "⬆️ Find Prev" else "⬆️ 前を検索",
                                onClick = {
                                    onFindNext(findText, false, matchCase, wholeCell)
                                }
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "📋 Find All" else "📋 すべて検索",
                                highlight = true,
                                onClick = {
                                    onFindAll(findText, matchCase, wholeCell)
                                }
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "✏️ Replace" else "✏️ 1件置換",
                                onClick = {
                                    onReplaceCurrent(findText, replaceText, matchCase, wholeCell)
                                }
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "🔄 Replace All" else "🔄 すべて置換",
                                highlight = true,
                                onClick = {
                                    onReplaceAll(findText, replaceText, matchCase, wholeCell)
                                }
                            )
                        }
                        // Live Status & Match Results Table
                        if (state.lastFindSummary.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF1E3A8A), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = state.lastFindSummary,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (state.findMatches.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 70.dp, max = 155.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(7.dp))
                                    .padding(6.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                state.findMatches.forEachIndexed { idx, item ->
                                    val isCurrent = idx == state.currentFindMatchIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(if (isCurrent) Color(0xFF14532D) else Color(0xFF1E293B))
                                            .clickable { onSelectFindMatch(idx) }
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${item.sheetName}!${item.address}",
                                                color = if (isCurrent) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = item.displayText,
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                maxLines = 1
                                            )
                                        }
                                        if (item.editText.startsWith("=")) {
                                            Text(
                                                text = item.editText,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "goTo" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isEn) "📍 Go To Cell / Range / Defined Name & Go To Special:"
                            else "📍 ジャンプ (Go To) / 条件を選択してジャンプ / 名前定義マネージャー:",
                            color = Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isEn) "Reference:" else "参照先:", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                            BasicTextField(
                                value = goToRefText,
                                onValueChange = { goToRefText = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp)).padding(8.dp)
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "📍 Go To" else "📍 ジャンプ",
                                highlight = true,
                                onClick = {
                                    onGoToReference(goToRefText)
                                    onDismiss()
                                }
                            )
                        }
                        Text(
                            if (isEn) "📐 Go To Special (Select matching cells in active sheet):"
                            else "📐 条件を選択してジャンプ (アクティブシート内の該当セルを検索):",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MasuSheetPillButton(if (isEn) "🔢 Formulas" else "🔢 数式セル", highlight = true, onClick = {
                                onGoToSpecial("formulas")
                                mode = "findReplace"
                            })
                            MasuSheetPillButton(if (isEn) "🔤 Constants" else "🔤 定数セル", onClick = {
                                onGoToSpecial("constants")
                                mode = "findReplace"
                            })
                            MasuSheetPillButton(if (isEn) "⬜ Blanks" else "⬜ 空白セル", onClick = {
                                onGoToSpecial("blanks")
                                mode = "findReplace"
                            })
                            MasuSheetPillButton(if (isEn) "💬 Comments" else "💬 コメントセル", onClick = {
                                onGoToSpecial("comments")
                                mode = "findReplace"
                            })
                            MasuSheetPillButton(if (isEn) "🏁 Last Cell" else "🏁 最後のセル", onClick = {
                                onGoToSpecial("lastCell")
                                onDismiss()
                            })
                        }
                        Text(
                            if (isEn) "🏷️ Defined Names (Click to jump):"
                            else "🏷️ 登録済みの名前定義 (クリックでジャンプ):",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.definedNames.forEach { (name, ref) ->
                                MasuSheetPillButton(
                                    label = "🏷️ $name ($ref)",
                                    onClick = {
                                        onGoToReference(ref)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicTextField(
                                value = newDefinedName,
                                onValueChange = { newDefinedName = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                modifier = Modifier.width(140.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp)).padding(6.dp),
                                decorationBox = { inner ->
                                    if (newDefinedName.isEmpty()) Text(if (isEn) "New name..." else "新しい名前...", color = Color(0xFF64748B), fontSize = 11.5.sp)
                                    inner()
                                }
                            )
                            BasicTextField(
                                value = newDefinedRef,
                                onValueChange = { newDefinedRef = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp)).padding(6.dp)
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "➕ Define Name" else "➕ 名前を登録",
                                highlight = true,
                                onClick = {
                                    if (newDefinedName.isNotBlank() && newDefinedRef.isNotBlank()) {
                                        onDefineName(newDefinedName, newDefinedRef)
                                        newDefinedName = ""
                                    }
                                }
                            )
                        }
                    }
                }

                "conditional" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isEn) "Apply Conditional Formatting to Selection (${state.viewport.activeAddress}):"
                            else "選択範囲 (${state.viewport.activeAddress}) に条件付き書式を適用:",
                            color = Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MasuSheetPillButton(if (isEn) "🌈 3-Color Scale (G-Y-R)" else "🌈 3色カラースケール (緑-黄-赤)", highlight = true, onClick = {
                                onExecuteCommand("home.conditionalFormatting", "{\"type\":\"colorScale\",\"rule\":\"colorScale\"}")
                                onDismiss()
                            })
                            MasuSheetPillButton(if (isEn) "📊 Data Bars" else "📊 データバー", highlight = true, onClick = {
                                onExecuteCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"rule\":\"dataBar\"}")
                                onDismiss()
                            })
                            MasuSheetPillButton(if (isEn) "⬆️ Highlight Above Average" else "⬆️ 平均より上を緑強調", onClick = {
                                onExecuteCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"rule\":\"aboveAverage\"}")
                                onDismiss()
                            })
                        }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MasuSheetPillButton(if (isEn) "🔴 Highlight Duplicate Values" else "🔴 重複する値を赤強調", onClick = {
                                onExecuteCommand("home.conditionalFormatting", "{\"type\":\"colorScale\",\"rule\":\"duplicateValues\"}")
                                onDismiss()
                            })
                            MasuSheetPillButton(if (isEn) "🏆 Highlight Top 10%" else "🏆 上位10%をゴールド強調", onClick = {
                                onExecuteCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"rule\":\"top10Percent\"}")
                                onDismiss()
                            })
                            MasuSheetPillButton(if (isEn) "🚫 Clear Conditional Rules" else "🚫 条件付き書式をクリア", onClick = {
                                onExecuteCommand("home.conditionalFormatting", "{\"type\":\"clear\",\"rule\":\"clear\"}")
                                onDismiss()
                            })
                        }
                    }
                }

                "validation" -> {
                    val vpSel = state.viewport
                    val selValidationRange = remember(vpSel.selStartRow, vpSel.selEndRow, vpSel.selStartCol, vpSel.selEndCol) {
                        val r0 = minOf(vpSel.selStartRow, vpSel.selEndRow).coerceAtLeast(0)
                        val r1 = maxOf(vpSel.selStartRow, vpSel.selEndRow).coerceAtLeast(r0)
                        val c0 = minOf(vpSel.selStartCol, vpSel.selEndCol).coerceAtLeast(0)
                        val c1 = maxOf(vpSel.selStartCol, vpSel.selEndCol).coerceAtLeast(c0)
                        val sA1 = MasuSheetConfig.formatCellAddress(r0, c0)
                        val eA1 = MasuSheetConfig.formatCellAddress(r1, c1)
                        if (sA1 == eA1) sA1 else "$sA1:$eA1"
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isEn) "Data Validation (Target Range: $selValidationRange):"
                            else "データの入力規則 (対象範囲: $selValidationRange):",
                            color = Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(if (isEn) "Comma-separated list options:" else "カンマ区切りで選択肢を指定:", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                        BasicTextField(
                            value = validationList,
                            onValueChange = { validationList = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                            cursorBrush = SolidColor(Color(0xFF4ADE80)),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp)).padding(8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MasuSheetPillButton(if (isEn) "✅ Apply Dropdown List" else "✅ リスト入力規則を設定", highlight = true, onClick = {
                                onExecuteCommand(
                                    "data.dataValidation",
                                    "{\"range\":${JSONObject.quote(selValidationRange)},\"type\":\"list\",\"allow\":\"list\",\"formula1\":${JSONObject.quote(validationList)},\"source\":${JSONObject.quote(validationList)}}"
                                )
                                onDismiss()
                            })
                            MasuSheetPillButton(if (isEn) "🔢 Whole Numbers >= 0" else "🔢 0以上の整数のみ許可", onClick = {
                                onExecuteCommand(
                                    "data.dataValidation",
                                    "{\"range\":${JSONObject.quote(selValidationRange)},\"type\":\"whole\",\"allow\":\"whole\",\"operator\":\"greaterOrEqual\",\"formula1\":\"0\",\"source\":\">=0\"}"
                                )
                                onDismiss()
                            })
                            MasuSheetPillButton(if (isEn) "🚫 Clear Validation" else "🚫 入力規則をクリア", onClick = {
                                onExecuteCommand(
                                    "data.dataValidation",
                                    "{\"range\":${JSONObject.quote(selValidationRange)},\"clear\":true,\"allow\":\"any\",\"source\":\"clear\"}"
                                )
                                onDismiss()
                            })
                        }
                    }
                }

                "goalSeek" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isEn) "🎯 What-If Analysis — Goal Seek / Solver:" else "🎯 What-If 分析 — ゴールシーク / ソルバー:", color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isEn) "Set cell:" else "数式セル:", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(100.dp))
                            BasicTextField(
                                value = goalTargetCell,
                                onValueChange = { goalTargetCell = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                modifier = Modifier.width(100.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp)).padding(6.dp)
                            )
                            Text(if (isEn) "To value:" else "目標値:", color = Color.White, fontSize = 12.sp)
                            BasicTextField(
                                value = goalTargetValue,
                                onValueChange = { goalTargetValue = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                modifier = Modifier.width(120.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp)).padding(6.dp)
                            )
                            Text(if (isEn) "By changing cell:" else "変化させるセル:", color = Color.White, fontSize = 12.sp)
                            BasicTextField(
                                value = goalChangingCell,
                                onValueChange = { goalChangingCell = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                modifier = Modifier.width(90.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp)).padding(6.dp)
                            )
                        }
                        MasuSheetPillButton(if (isEn) "🎯 Run Goal Seek" else "🎯 ゴールシークを実行", highlight = true, onClick = {
                            val targetNum = goalTargetValue.toDoubleOrNull() ?: 0.0
                            onExecuteCommand("data.whatIfGoalSeek", "{\"setCell\":\"$goalTargetCell\",\"toValue\":$targetNum,\"byChangingCell\":\"$goalChangingCell\"}")
                            onDismiss()
                        })
                    }
                }

                "comment" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isEn) "💬 Cell Comment & 🔗 Hyperlink (${state.viewport.activeAddress}):"
                            else "💬 セルコメント & 🔗 ハイパーリンク (${state.viewport.activeAddress}):",
                            color = Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isEn) "Comment:" else "コメント:", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(85.dp))
                            BasicTextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp)).padding(8.dp)
                            )
                            MasuSheetPillButton(if (isEn) "💬 Save Comment" else "💬 コメント保存", highlight = true, onClick = {
                                onSetCellComment(commentText)
                                onDismiss()
                            })
                            MasuSheetPillButton(if (isEn) "🗑️ Delete" else "🗑️ 削除", onClick = {
                                onSetCellComment("")
                                onDismiss()
                            })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isEn) "Link URL/Ref:" else "リンク先:", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(85.dp))
                            BasicTextField(
                                value = hyperlinkUrl,
                                onValueChange = { hyperlinkUrl = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp)).padding(8.dp)
                            )
                            BasicTextField(
                                value = hyperlinkLabel,
                                onValueChange = { hyperlinkLabel = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                modifier = Modifier.width(120.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp)).padding(8.dp)
                            )
                            MasuSheetPillButton(if (isEn) "🔗 Set Link" else "🔗 リンク設定", highlight = true, onClick = {
                                onSetCellHyperlink(hyperlinkUrl, hyperlinkLabel)
                                onDismiss()
                            })
                        }
                    }
                }

                "sheetManage" -> {
                    val curSheetIdx = state.viewport.sheetIndex
                    val sheetNames = state.viewport.sheetNames
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isEn) "📑 Manage & Reorder Worksheet '${state.viewport.sheetName}' (${curSheetIdx + 1}/${sheetNames.size}):"
                            else "📑 ワークシート「${state.viewport.sheetName}」(${curSheetIdx + 1}/${sheetNames.size}) の管理・並び替え:",
                            color = Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isEn) "New Sheet Name:" else "新しいシート名:", color = Color.White, fontSize = 12.sp)
                            BasicTextField(
                                value = sheetNewName,
                                onValueChange = { sheetNewName = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                modifier = Modifier.width(180.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp)).padding(6.dp)
                            )
                            MasuSheetPillButton(if (isEn) "✏️ Rename" else "✏️ 名前を変更", highlight = true, onClick = {
                                onExecuteCommand("home.renameSheet", "{\"name\":${JSONObject.quote(sheetNewName)}}")
                                onDismiss()
                            })
                        }
                        // Sheet Reorder Row
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isEn) "Tab Order:" else "タブ順序の入れ替え:", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                            MasuSheetPillButton(
                                label = if (isEn) "⬅️ Move Sheet Left" else "⬅️ シートを左へ移動",
                                highlight = curSheetIdx > 0,
                                onClick = {
                                    if (curSheetIdx > 0) onMoveSheet(curSheetIdx, curSheetIdx - 1)
                                }
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "➡️ Move Sheet Right" else "➡️ シートを右へ移動",
                                highlight = curSheetIdx < sheetNames.lastIndex,
                                onClick = {
                                    if (curSheetIdx < sheetNames.lastIndex) onMoveSheet(curSheetIdx, curSheetIdx + 1)
                                }
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MasuSheetPillButton(if (isEn) "➕ Insert Sheet" else "➕ 新規シート挿入", onClick = {
                                onExecuteCommand("home.insertSheet", "{}")
                                onDismiss()
                            })
                            MasuSheetPillButton(if (isEn) "📋 Duplicate Sheet" else "📋 シートを複製 (コピー)", highlight = true, onClick = {
                                onExecuteCommand("home.moveOrCopySheet", "{\"copy\":true}")
                                onDismiss()
                            })
                            MasuSheetPillButton(if (isEn) "🎨 Sheet Tab Color..." else "🎨 シート見出しの色を変更...", highlight = true, onClick = {
                                onDismiss()
                                onOpenColorPickerForSheetTab()
                            })
                            MasuSheetPillButton(if (isEn) "🔒 Protect Sheet" else "🔒 シートを保護", onClick = {
                                onExecuteCommand("review.protectSheet", "{\"protect\":true}")
                                onDismiss()
                            })
                            MasuSheetPillButton(if (isEn) "🗑️ Delete Sheet" else "🗑️ このシートを削除", onClick = {
                                onExecuteCommand("home.deleteSheet", "{}")
                                onDismiss()
                            })
                        }
                        if (state.viewport.hiddenSheetNames.isNotEmpty()) {
                            Text(
                                text = if (isEn) "Hidden Worksheets (Tap to Unhide):" else "非表示シート一覧 (タップで再表示):",
                                color = Color(0xFF4ADE80),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.viewport.hiddenSheetNames.forEach { hiddenName ->
                                    MasuSheetPillButton(
                                        label = if (isEn) "👁️ Unhide: $hiddenName" else "👁️ 再表示: $hiddenName",
                                        highlight = true,
                                        onClick = {
                                            onExecuteCommand("sheet.unhide", "{\"name\":${JSONObject.quote(hiddenName)}}")
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    // All 292 Commands Browser
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isEn) "🔍 Search Commands (All ${SpreadsheetRibbonCatalog.ALL_292_COMMAND_IDS.size}):"
                            else "🔍 コマンド検索 (全 ${SpreadsheetRibbonCatalog.ALL_292_COMMAND_IDS.size} 件):",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        BasicTextField(
                            value = cmdSearch,
                            onValueChange = { cmdSearch = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(Color(0xFF4ADE80)),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp)).padding(6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val matchingRows = remember(cmdSearch) {
                        SpreadsheetRibbonCatalog.ALL_292_COMMAND_IDS.filter {
                            cmdSearch.isBlank() || it.contains(cmdSearch.trim(), ignoreCase = true)
                        }.chunked(3)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(matchingRows) { rowCmds ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowCmds.forEach { cmdId ->
                                        MasuSheetPillButton(
                                            label = "▶ $cmdId",
                                            onClick = {
                                                onExecuteCommand(cmdId, "{}")
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
