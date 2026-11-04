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
 * [EN] Macro & VBA Automation Studio Dialog
 * Implements [MacroVbaStudioDialog] for macro recording, VBA code editing, step-by-step execution inspection, and natural-language macro generation.
 *
 * ---
 * [JA / 日本語解説]
 * マクロ記録・VBAコード編集・1ステップ実行デバッグ・自然言語からのマクロ生成を統合した [MacroVbaStudioDialog] コンポーネントです。
 * ============================================================================
 */

// ============================================================================
// 3. ALL-IN-ONE MACRO & VBA AUTOMATION STUDIO DIALOG
// ============================================================================

/**
 * [EN] Modal studio dialog for recording macros, editing VBA code, stepping through lines, and rolling back macro runs.
 * [JA] マクロの記録・VBAコード編集・1行ステップ実行・実行ロールバックを行うマクロ／VBAスタジオダイアログです。
 */
@Composable
fun MacroVbaStudioDialog(
    state: MasuSheetUiState,
    onStartRecording: (name: String, shortcut: String, desc: String, relative: Boolean) -> Unit,
    onStopRecording: () -> Unit,
    onToggleRelative: () -> Unit,
    onSaveMacro: (MacroDefinition) -> Unit,
    onDeleteMacro: (String) -> Unit,
    onRunMacro: (MacroDefinition) -> Unit,
    onStartDebug: (String, String) -> Unit,
    onStepDebug: () -> Unit,
    onStopDebug: () -> Unit,
    onRollbackLastRun: () -> Unit,
    onGenerateVbaFromPrompt: (String) -> String,
    onDismiss: () -> Unit
) {
    val isEn = state.language == "en"
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Record/List, 1 = VBA Editor/Debugger, 2 = NL <-> VBA AI
    var recName by remember { mutableStateOf("Macro${state.savedMacros.size + 1}") }
    var recShortcut by remember { mutableStateOf("Ctrl+Shift+M") }
    var recDesc by remember(isEn) {
        mutableStateOf(if (isEn) "Automate formatting and summary totals" else "定型フォーマットと集計の自動実行")
    }

    var editingMacroName by remember { mutableStateOf(state.savedMacros.firstOrNull()?.name ?: "CustomMacro1") }
    var editingVbaCode by remember {
        mutableStateOf(
            state.savedMacros.firstOrNull()?.vbaCode ?: MacroStudioEngine.defaultBuiltInMacros().first().vbaCode
        )
    }
    var nlPrompt by remember(isEn) {
        mutableStateOf(
            if (isEn) "Convert selection to tax-included (*1.1) and add total row & column"
            else "選択範囲を税込(1.1倍)に変換して、合計行と合計列を追加する"
        )
    }
    var vbaExplanation by remember { mutableStateOf("") }

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
                .heightIn(max = 650.dp)
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
                        "🤖 Macro & VBA Automation Studio (Recorder, VBA Runner, Step Debugger & AI Converter)"
                    } else {
                        "🤖 マクロ & VBA 自動化スタジオ (操作記録・VBA実行・1ステップデバッグ・AI変換)"
                    },
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.lastMacroUndoSteps > 0) {
                        MasuSheetPillButton(
                            label = if (isEn) "↶ Undo Macro (${state.lastMacroUndoSteps} steps)" else "↶ 実行前に戻す (${state.lastMacroUndoSteps}手)",
                            highlight = true,
                            onClick = onRollbackLastRun
                        )
                    }
                    MasuSheetPillButton(if (isEn) "✕ Close" else "✕ 閉じる", onClick = onDismiss)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MasuSheetPillButton(
                    label = if (isEn) "⏺ 1. Record, List & Run Macros" else "⏺ 1. マクロの記録・一覧・実行",
                    highlight = activeTab == 0,
                    onClick = { activeTab = 0 }
                )
                MasuSheetPillButton(
                    label = if (isEn) "📝 2. VBA Editor & Step Debugger" else "📝 2. VBA エディタ & 1ステップデバッガ",
                    highlight = activeTab == 1,
                    onClick = { activeTab = 1 }
                )
                MasuSheetPillButton(
                    label = if (isEn) "✨ 3. Natural Language ⇄ VBA AI" else "✨ 3. 自然言語 ⇄ VBA AI変換",
                    highlight = activeTab == 2,
                    onClick = { activeTab = 2 }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (activeTab) {
                0 -> {
                    // Action Recorder box
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
                            text = if (state.isMacroRecording) {
                                if (isEn) {
                                    "🔴 Recording: '${state.currentRecordingName}' (${state.currentRecordingSteps.size} steps recorded — sheet actions are captured automatically)"
                                } else {
                                    "🔴 記録中: 「${state.currentRecordingName}」 (${state.currentRecordingSteps.size} ステップ記録済 — シートを操作すると自動記録されます)"
                                }
                            } else {
                                if (isEn) {
                                    "⏺ Record New Macro (Automatically captures cell edits, formatting, and sorting as VBA code)"
                                } else {
                                    "⏺ 新しいマクロの記録 (シート上のセル入力・書式変更・並び替えなどをそのまま記録＆VBAコード化)"
                                }
                            },
                            color = if (state.isMacroRecording) Color(0xFFF87171) else Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(if (isEn) "Macro Name:" else "マクロ名:", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                            BasicTextField(
                                value = recName,
                                onValueChange = { recName = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Text(if (isEn) "Shortcut:" else "ショートカット:", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                            BasicTextField(
                                value = recShortcut,
                                onValueChange = { recShortcut = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                                modifier = Modifier
                                    .width(110.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            MasuSheetPillButton(
                                label = if (state.macroRelativeMode) {
                                    if (isEn) "📍 Relative: ON" else "📍 相対参照:ON"
                                } else {
                                    if (isEn) "📌 Absolute Ref" else "📌 絶対参照"
                                },
                                highlight = state.macroRelativeMode,
                                onClick = onToggleRelative
                            )
                            if (!state.isMacroRecording) {
                                MasuSheetPillButton(
                                    label = if (isEn) "⏺ Start Recording" else "⏺ 記録開始",
                                    highlight = true,
                                    onClick = {
                                        onStartRecording(recName, recShortcut, recDesc, state.macroRelativeMode)
                                    }
                                )
                            } else {
                                MasuSheetPillButton(
                                    label = if (isEn) "⏹ Stop & Save" else "⏹ 記録停止・保存",
                                    highlight = true,
                                    onClick = onStopRecording
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isEn) "Saved Macros (${state.savedMacros.size}):" else "登録済みマクロ一覧 (${state.savedMacros.size} 件):",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.savedMacros.forEach { macro ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sub ${macro.name}()  [${macro.shortcutKey}]",
                                            color = Color(0xFF4ADE80),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = SpreadsheetI18n.t(macro.description, isEn),
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        MasuSheetPillButton(
                                            label = if (isEn) "▶ Run" else "▶ 実行",
                                            highlight = true,
                                            onClick = { onRunMacro(macro) }
                                        )
                                        MasuSheetPillButton(
                                            label = if (isEn) "🐞 Step Debug" else "🐞 1ステップデバッグ",
                                            onClick = {
                                                editingMacroName = macro.name
                                                editingVbaCode = macro.vbaCode
                                                onStartDebug(macro.name, macro.vbaCode)
                                                activeTab = 1
                                            }
                                        )
                                        MasuSheetPillButton(
                                            label = if (isEn) "📝 Edit VBA" else "📝 VBA編集",
                                            onClick = {
                                                editingMacroName = macro.name
                                                editingVbaCode = macro.vbaCode
                                                activeTab = 1
                                            }
                                        )
                                        MasuSheetPillButton(
                                            label = "🗑️",
                                            onClick = { onDeleteMacro(macro.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // VBA Editor & 1-Step Debugger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(if (isEn) "Module Name:" else "モジュール名:", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                            BasicTextField(
                                value = editingMacroName,
                                onValueChange = { editingMacroName = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                                modifier = Modifier
                                    .width(170.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            MasuSheetPillButton(
                                label = if (isEn) "▶ Run All (F5)" else "▶ すべて実行 (F5)",
                                highlight = true,
                                onClick = {
                                    val tempMacro = MacroDefinition(
                                        name = editingMacroName,
                                        description = if (isEn) "Executed from VBA Editor" else "VBAエディタから実行",
                                        vbaCode = editingVbaCode
                                    )
                                    onSaveMacro(tempMacro)
                                    onRunMacro(tempMacro)
                                }
                            )
                            if (!state.vbaDebugState.isDebugging) {
                                MasuSheetPillButton(
                                    label = if (isEn) "🐞 Start Debug" else "🐞 デバッグ開始",
                                    highlight = true,
                                    onClick = { onStartDebug(editingMacroName, editingVbaCode) }
                                )
                            } else {
                                MasuSheetPillButton(
                                    label = if (isEn) "⏯ Step Into (F8)" else "⏯ 1ステップ実行 (F8)",
                                    highlight = true,
                                    onClick = onStepDebug
                                )
                                MasuSheetPillButton(
                                    label = if (isEn) "⏹ Stop" else "⏹ 停止",
                                    onClick = onStopDebug
                                )
                            }
                            MasuSheetPillButton(
                                label = if (isEn) "💾 Save" else "💾 保存",
                                onClick = {
                                    onSaveMacro(
                                        MacroDefinition(
                                            name = editingMacroName,
                                            description = if (isEn) "VBA Code (${editingVbaCode.lines().size} lines)" else "VBAコード (${editingVbaCode.lines().size}行)",
                                            vbaCode = editingVbaCode
                                        )
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    BasicTextField(
                        value = editingVbaCode,
                        onValueChange = { editingVbaCode = it },
                        textStyle = TextStyle(
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(Color(0xFF4ADE80)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Debugger live watch & execution log
                    val dbg = state.vbaDebugState
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val currentStmt = dbg.executableLines.getOrNull(dbg.currentLineIndex) ?: if (isEn) "(Idle / Done)" else "(待機中 / 完了)"
                        Text(
                            text = if (isEn) {
                                "🔎 Next Statement (${dbg.currentLineIndex}/${dbg.executableLines.size}): $currentStmt"
                            } else {
                                "🔎 次の実行ステートメント (${dbg.currentLineIndex}/${dbg.executableLines.size}): $currentStmt"
                            },
                            color = Color(0xFFFACC15),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (dbg.variables.isNotEmpty()) {
                            Text(
                                text = (if (isEn) "Local Watch: " else "ローカル変数ウォッチ: ") +
                                    dbg.variables.entries.joinToString(", ") { "${it.key} = ${it.value}" },
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (dbg.lastMsgBox != null) {
                            Text(
                                text = (if (isEn) "💬 MsgBox Output: " else "💬 MsgBox出力: ") + dbg.lastMsgBox,
                                color = Color(0xFF4ADE80),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        dbg.consoleLog.takeLast(5).forEach { logLine ->
                            Text(
                                text = logLine,
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                2 -> {
                    // Natural Language <-> VBA AI Converter
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isEn) {
                                "✨ Describe what you want in natural language to generate executable VBA macro code:"
                            } else {
                                "✨ やりたい処理を日本語で入力すると、実行可能な VBA マクロコードを自動生成します:"
                            },
                            color = Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        BasicTextField(
                            value = nlPrompt,
                            onValueChange = { nlPrompt = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                            cursorBrush = SolidColor(Color(0xFF4ADE80)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MasuSheetPillButton(
                                label = if (isEn) "✨ Generate VBA from Prompt" else "✨ 日本語から VBA コードを生成",
                                highlight = true,
                                onClick = {
                                    editingVbaCode = onGenerateVbaFromPrompt(nlPrompt)
                                    vbaExplanation = MacroStudioEngine.explainVbaInJapanese(editingVbaCode)
                                }
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "📖 Explain Current VBA Code" else "📖 現在の VBA コードを日本語で解説",
                                onClick = {
                                    vbaExplanation = MacroStudioEngine.explainVbaInJapanese(editingVbaCode)
                                }
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "▶ Run Generated VBA Now" else "▶ 生成した VBA を今すぐシートで実行",
                                highlight = true,
                                onClick = {
                                    val macro = MacroDefinition(
                                        name = "AI_Generated_Macro",
                                        description = nlPrompt,
                                        vbaCode = editingVbaCode
                                    )
                                    onSaveMacro(macro)
                                    onRunMacro(macro)
                                }
                            )
                        }
                        BasicTextField(
                            value = editingVbaCode,
                            onValueChange = { editingVbaCode = it },
                            textStyle = TextStyle(color = Color(0xFFE2E8F0), fontSize = 11.5.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFF4ADE80)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        )
                        if (vbaExplanation.isNotBlank()) {
                            Text(
                                text = (if (isEn) "[Code Explanation]\n" else "【コード解説】\n") + vbaExplanation,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
