package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlin.math.abs
import kotlin.math.roundToInt
import com.masusheet.app.MasuSheetViewModel.*

/**
 * ============================================================================
 * [EN] Macro Recorder, Step-by-Step VBA Debugger, & Non-Blocking 3-Mode AI Assistant (`C-08`)
 * Handles macro recording, VBA stepping & safe rollback (`H-11`), and `Dispatchers.IO` coroutine execution (`C-08`) for Built-in Script, Web AI JSON bridge, and Cloud/Local LLM APIs.
 *
 * ---
 * [JA / 日本語解説]
 * マクロ記録、VBAステップ実行と安全なロールバック (`H-11`)、および `Dispatchers.IO` 非同期コルーチン (`C-08`) による3モードAI編集実行を担うモジュールです。
 * ============================================================================
 */

// --- All-in-One Macro & VBA Studio ---

/**
 * [EN] ViewModel action/helper `onStartMacroRecording` handling start macro recording while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onStartMacroRecording` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onStartMacroRecording(
    name: String,
    shortcutKey: String = "Ctrl+Shift+M",
    description: String = "",
    relativeMode: Boolean = _uiState.value.macroRelativeMode
) {
    val cleanName = name.trim().ifEmpty { "Macro${_uiState.value.savedMacros.size + 1}" }
    engineGateway.executeCommand("automate.recordActions", "{}")
    _uiState.value = _uiState.value.copy(
        isMacroRecording = true,
        macroRelativeMode = relativeMode,
        currentRecordingName = cleanName,
        currentRecordingShortcut = shortcutKey.ifBlank { "Ctrl+Shift+M" },
        currentRecordingDescription = description,
        currentRecordingSteps = emptyList(),
        statusNoticeMessage = "● マクロ「$cleanName」の記録を開始しました（操作が自動記録されます）"
    )
}

/**
 * [EN] ViewModel action/helper `onStopMacroRecording` handling stop macro recording while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onStopMacroRecording` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onStopMacroRecording(): MacroDefinition? {
    val cur = _uiState.value
    if (!cur.isMacroRecording) return null
    engineGateway.executeCommand("automate.recordActions", "{\"stop\":true,\"name\":${JSONObject.quote(cur.currentRecordingName)}}")
    val vbaCode = MacroStudioEngine.stepsToVbaCode(
        name = cur.currentRecordingName,
        description = cur.currentRecordingDescription,
        shortcutKey = cur.currentRecordingShortcut,
        steps = cur.currentRecordingSteps
    )
    val newMacro = MacroDefinition(
        name = cur.currentRecordingName,
        description = cur.currentRecordingDescription.ifBlank { "記録された操作 (${cur.currentRecordingSteps.size} ステップ)" },
        shortcutKey = cur.currentRecordingShortcut,
        relativeReference = cur.macroRelativeMode,
        steps = cur.currentRecordingSteps,
        vbaCode = vbaCode
    )
    val updatedList = cur.savedMacros.filterNot { it.name.equals(newMacro.name, ignoreCase = true) } + newMacro
    _uiState.value = cur.copy(
        isMacroRecording = false,
        savedMacros = updatedList,
        statusNoticeMessage = "■ マクロ「${newMacro.name}」を保存しました（${newMacro.steps.size} ステップ + VBAコード生成完了）"
    )
    return newMacro
}

/**
 * [EN] ViewModel action/helper `onToggleMacroRelativeMode` handling toggle macro relative mode while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onToggleMacroRelativeMode` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onToggleMacroRelativeMode() {
    val next = !_uiState.value.macroRelativeMode
    engineGateway.executeCommand("view.useRelativeReferences", "{\"relative\":$next}")
    _uiState.value = _uiState.value.copy(
        macroRelativeMode = next,
        statusNoticeMessage = if (next) "相対参照で記録モード: ON" else "絶対参照で記録モード: ON"
    )
}

/**
 * [EN] ViewModel action/helper `onSaveOrUpdateMacro` handling save or update macro while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSaveOrUpdateMacro` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSaveOrUpdateMacro(macro: MacroDefinition) {
    val cur = _uiState.value
    val updated = cur.savedMacros.filterNot { it.name.equals(macro.name, ignoreCase = true) } + macro
    _uiState.value = cur.copy(
        savedMacros = updated,
        statusNoticeMessage = "マクロ「${macro.name}」を保存しました"
    )
}

/**
 * [EN] ViewModel action/helper `onDeleteMacro` handling delete macro while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onDeleteMacro` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onDeleteMacro(name: String) {
    val cur = _uiState.value
    engineGateway.executeCommand("automate.deleteScript", "{\"name\":${JSONObject.quote(name)}}")
    _uiState.value = cur.copy(
        savedMacros = cur.savedMacros.filterNot { it.name.equals(name, ignoreCase = true) },
        statusNoticeMessage = "マクロ「$name」を削除しました"
    )
}

/**
 * [EN] ViewModel action/helper `onRunMacro` handling run macro while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onRunMacro` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onRunMacro(macro: MacroDefinition): String {
    val undoStackBefore = customOverlayUndoStack.size
    val engineDepthBefore = _uiState.value.viewport.undoDepth
    val wasRecording = _uiState.value.isMacroRecording
    if (wasRecording) {
        _uiState.value = _uiState.value.copy(isMacroRecording = false)
    }
    var executedOps = 0
    var lastMsg: String? = null
    try {
        val statements = MacroStudioEngine.expandVbaStatements(macro.vbaCode)
        val vars = mutableMapOf<String, String>()
        if (statements.isNotEmpty()) {
            for (stmt in statements) {
                MacroStudioEngine.executeSingleVbaStatement(
                    stmt = stmt,
                    variables = vars,
                    vp = _uiState.value.viewport,
                    executeCmd = { cmdId, params ->
                        dispatchCommand(cmdId, params)
                        executedOps++
                    },
                    commitCell = { r, c, txt ->
                        val addr = MasuSheetConfig.formatCellAddress(r, c)
                        dispatchCommand("cell.set", "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(txt)}}")
                        executedOps++
                    },
                    onMsgBox = { msg -> lastMsg = msg }
                )
            }
        } else {
            for (step in macro.steps) {
                dispatchCommand(step.command, step.paramsJson)
                executedOps++
            }
        }
    } finally {
        if (wasRecording) {
            _uiState.value = _uiState.value.copy(isMacroRecording = true)
        }
    }
    val overlayDelta = (customOverlayUndoStack.size - undoStackBefore).coerceAtLeast(0)
    val engineDelta = (_uiState.value.viewport.undoDepth - engineDepthBefore).coerceAtLeast(0)
    val undoSteps = if (overlayDelta > 0) overlayDelta else engineDelta
    val summary = lastMsg ?: "マクロ「${macro.name}」を実行しました（$executedOps ステップ）"
    _uiState.value = _uiState.value.copy(
        lastMacroUndoSteps = undoSteps,
        statusNoticeMessage = summary
    )
    return summary
}

/**
 * [EN] ViewModel action/helper `onStartVbaDebug` handling start vba debug while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onStartVbaDebug` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onStartVbaDebug(macroName: String, vbaCode: String) {
    val lines = MacroStudioEngine.expandVbaStatements(vbaCode)
    _uiState.value = _uiState.value.copy(
        vbaDebugState = VbaDebugState(
            isDebugging = true,
            macroName = macroName,
            executableLines = lines,
            currentLineIndex = 0,
            variables = emptyMap(),
            consoleLog = listOf("▶ デバッグ開始: $macroName (${lines.size} ステップ)"),
            lastMsgBox = null,
            undoStepCount = 0
        )
    )
}

/**
 * [EN] ViewModel action/helper `onStepVbaDebug` handling step vba debug while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onStepVbaDebug` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onStepVbaDebug(): Boolean {
    val cur = _uiState.value
    val dbg = cur.vbaDebugState
    if (!dbg.isDebugging || dbg.currentLineIndex >= dbg.executableLines.size) {
        _uiState.value = cur.copy(
            vbaDebugState = dbg.copy(
                isDebugging = false,
                consoleLog = dbg.consoleLog + "✓ End Sub (デバッグ完了)"
            )
        )
        return false
    }
    val stmt = dbg.executableLines[dbg.currentLineIndex]
    val vars = dbg.variables.toMutableMap()
    val undoStackBefore = customOverlayUndoStack.size
    val engineDepthBefore = cur.viewport.undoDepth
    var msgBoxText: String? = null
    val logEntry = MacroStudioEngine.executeSingleVbaStatement(
        stmt = stmt,
        variables = vars,
        vp = _uiState.value.viewport,
        executeCmd = { cmdId, params ->
            dispatchCommand(cmdId, params)
        },
        commitCell = { r, c, txt ->
            val addr = MasuSheetConfig.formatCellAddress(r, c)
            dispatchCommand("cell.set", "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(txt)}}")
        },
        onMsgBox = { msg -> msgBoxText = msg }
    )
    val overlayDelta = (customOverlayUndoStack.size - undoStackBefore).coerceAtLeast(0)
    val engineDelta = (_uiState.value.viewport.undoDepth - engineDepthBefore).coerceAtLeast(0)
    val stepUndoDelta = if (overlayDelta > 0) overlayDelta else engineDelta
    val nextIdx = dbg.currentLineIndex + 1
    val totalUndo = dbg.undoStepCount + stepUndoDelta
    _uiState.value = _uiState.value.copy(
        lastMacroUndoSteps = totalUndo,
        vbaDebugState = dbg.copy(
            isDebugging = nextIdx < dbg.executableLines.size,
            currentLineIndex = nextIdx,
            variables = vars,
            consoleLog = (dbg.consoleLog + "[Step $nextIdx] $logEntry").takeLast(30),
            lastMsgBox = msgBoxText ?: dbg.lastMsgBox,
            undoStepCount = totalUndo
        )
    )
    return nextIdx < dbg.executableLines.size
}

/**
 * [EN] ViewModel action/helper `onStopVbaDebug` handling stop vba debug while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onStopVbaDebug` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onStopVbaDebug() {
    val dbg = _uiState.value.vbaDebugState
    _uiState.value = _uiState.value.copy(
        vbaDebugState = dbg.copy(isDebugging = false)
    )
}

/**
 * [EN] ViewModel action/helper `onRollbackLastMacroRun` handling rollback last macro run while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onRollbackLastMacroRun` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onRollbackLastMacroRun() {
    val steps = _uiState.value.lastMacroUndoSteps.coerceIn(0, 500)
    if (steps <= 0) {
        _uiState.value = _uiState.value.copy(
            lastMacroUndoSteps = 0,
            vbaDebugState = VbaDebugState(),
            statusNoticeMessage = if (_uiState.value.language == "en") {
                "No macro changes to roll back"
            } else {
                "ロールバック対象のマクロ変更はありません"
            }
        )
        return
    }
    repeat(steps) {
        dispatchCommand("edit.undo", "{}")
    }
    _uiState.value = _uiState.value.copy(
        lastMacroUndoSteps = 0,
        vbaDebugState = VbaDebugState(),
        statusNoticeMessage = "マクロ実行前の状態にロールバック（元に戻す: $steps ステップ）しました"
    )
}

/**
 * [EN] ViewModel action/helper `onGenerateVbaFromPrompt` handling generate vba from prompt while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onGenerateVbaFromPrompt` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onGenerateVbaFromPrompt(prompt: String): String =
    MacroStudioEngine.generateVbaFromNaturalLanguage(prompt, _uiState.value.viewport)

// --- General-Purpose AI Spreadsheet Agent (Copilot / Gemini / Claude / GPT / Local) ---

/**
 * [EN] ViewModel action/helper `onToggleAiPanel` handling toggle ai panel while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onToggleAiPanel` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onToggleAiPanel(open: Boolean? = null) {
    val next = open ?: !_uiState.value.isAiPanelOpen
    _uiState.value = _uiState.value.copy(isAiPanelOpen = next)
}

/**
 * [EN] ViewModel action/helper `onResizePivotPaneWidth` handling resize pivot pane width while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onResizePivotPaneWidth` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onResizePivotPaneWidth(widthDp: Float) {
    val clamped = widthDp.coerceIn(220f, 760f)
    _uiState.value = _uiState.value.copy(pivotPaneWidthDp = clamped)
}

/**
 * [EN] ViewModel action/helper `onResizeSplitPaneWidth` handling resize split pane width while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onResizeSplitPaneWidth` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onResizeSplitPaneWidth(widthDp: Float) {
    val clamped = widthDp.coerceIn(240f, 880f)
    _uiState.value = _uiState.value.copy(splitPaneWidthDp = clamped)
}

/**
 * [EN] ViewModel action/helper `onResizeAiPanelWidth` handling resize ai panel width while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onResizeAiPanelWidth` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onResizeAiPanelWidth(widthDp: Float) {
    val clamped = widthDp.coerceIn(220f, 820f)
    _uiState.value = _uiState.value.copy(aiPanelWidthDp = clamped)
}

/**
 * [EN] ViewModel action/helper `onSelectAiModel` handling select ai model while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSelectAiModel` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSelectAiModel(modelId: String) {
    preferencesRepository.setAiModelId(modelId)
    _uiState.value = _uiState.value.copy(selectedAiModelId = modelId)
}

/**
 * [EN] ViewModel action/helper `onSelectAiEditMode` handling select ai edit mode while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSelectAiEditMode` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSelectAiEditMode(modeId: String) {
    preferencesRepository.setAiEditMode(modeId)
    _uiState.value = _uiState.value.copy(
        selectedAiEditMode = modeId,
        activeAiPlanSession = null
    )
}

/**
 * [EN] ViewModel action/helper `getAiApiKeyForModel` handling get ai api key for model while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `getAiApiKeyForModel` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.getAiApiKeyForModel(modelId: String): String = preferencesRepository.getAiApiKey(modelId)
/**
 * [EN] ViewModel action/helper `getAiCustomEndpoint` handling get ai custom endpoint while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `getAiCustomEndpoint` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.getAiCustomEndpoint(): String = preferencesRepository.getAiCustomEndpoint()
/**
 * [EN] ViewModel action/helper `getAiCustomModelName` handling get ai custom model name while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `getAiCustomModelName` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.getAiCustomModelName(modelId: String = _uiState.value.selectedAiModelId): String =
    preferencesRepository.getAiCustomModel(modelId)

/**
 * [EN] ViewModel action/helper `onSaveAiProviderSettings` handling save ai provider settings while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSaveAiProviderSettings` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSaveAiProviderSettings(modelId: String, apiKey: String, customEndpoint: String, customModelName: String) {
    preferencesRepository.setAiApiKey(modelId, apiKey)
    preferencesRepository.setAiCustomEndpoint(customEndpoint)
    preferencesRepository.setAiCustomModel(customModelName, modelId)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        selectedAiModelId = modelId,
        statusNoticeMessage = if (isEn) {
            "Saved AI model settings (${modelId.uppercase()})"
        } else {
            "AIモデル設定 (${modelId.uppercase()}) を保存しました"
        }
    )
}

/**
 * [EN] ViewModel action/helper `onSendAiPrompt` handling send ai prompt while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSendAiPrompt` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSendAiPrompt(prompt: String) {
    val clean = prompt.trim()
    if (clean.isEmpty()) return
    val cur = _uiState.value
    val userMsg = AiChatMessage(
        id = "u_${System.currentTimeMillis()}",
        role = "user",
        text = clean
    )
    val updatedMsgs = cur.aiMessages + userMsg
    _uiState.value = cur.copy(aiMessages = updatedMsgs)

    // [EN] If the user directly entered a MasuSheet JSON script block (`{"commands": [...]}`), execute it directly!
    // [JA] 入力欄に直接JSONスクリプト (`{"commands": [...]}`) が入力された場合はそのまま実行します。
    if (clean.startsWith("{") && clean.contains("\"commands\"")) {
        val directParsed = SpreadsheetAiAgent.parseJsonAiTurnResult(clean, cur.selectedAiEditMode)
        if (directParsed != null) {
            applyAiExecutionResult(directParsed)
            return
        }
    }

    val apiKey = preferencesRepository.getAiApiKey(cur.selectedAiModelId)
    val customEndpoint = preferencesRepository.getAiCustomEndpoint()
    val customModelName = preferencesRepository.getAiCustomModel(cur.selectedAiModelId)
    val isEn = cur.language == "en"
    val effectiveModelId = if (cur.selectedAiModelId == "auto") {
        if (apiKey.isNotBlank()) "gemini" else "auto"
    } else {
        cur.selectedAiModelId
    }
    val usesLiveNetwork = cur.selectedAiEditMode != "plan" &&
        effectiveModelId != "auto" &&
        (apiKey.isNotBlank() || effectiveModelId == "local")

    fun handleAiTurnResult(result: AiTurnExecutionResult) {
        if (result.clarifyingQuestions.isNotEmpty()) {
            val planReply = AiChatMessage(
                id = "a_${System.currentTimeMillis()}",
                role = "assistant",
                text = result.replyText
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                aiMessages = _uiState.value.aiMessages + planReply,
                activeAiPlanSession = AiPlanSession(
                    originalPrompt = clean,
                    questions = result.clarifyingQuestions,
                    currentStepIndex = 0
                )
            )
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = false)
        applyAiExecutionResult(result)
    }

    if (usesLiveNetwork) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val result = SpreadsheetAiAgent.executeAgentTurnSuspend(
                prompt = clean,
                editMode = cur.selectedAiEditMode,
                clarifyingAnswers = emptyMap(),
                vp = cur.viewport,
                modelId = cur.selectedAiModelId,
                apiKey = apiKey,
                customEndpoint = customEndpoint,
                customModelName = customModelName,
                isEn = isEn
            )
            handleAiTurnResult(result)
        }
    } else {
        val result = SpreadsheetAiAgent.executeAgentTurn(
            prompt = clean,
            editMode = cur.selectedAiEditMode,
            clarifyingAnswers = emptyMap(),
            vp = cur.viewport,
            modelId = cur.selectedAiModelId,
            apiKey = apiKey,
            customEndpoint = customEndpoint,
            customModelName = customModelName,
            isEn = isEn
        )
        handleAiTurnResult(result)
    }
}

/**
 * [EN] ViewModel action/helper `onAnswerAiPlanQuestion` handling answer ai plan question while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onAnswerAiPlanQuestion` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onAnswerAiPlanQuestion(questionIndex: Int, selectedOptionIndex: Int, customText: String = "") {
    val session = _uiState.value.activeAiPlanSession ?: return
    if (questionIndex !in session.questions.indices) return
    val updatedQuestions = session.questions.toMutableList().also { list ->
        list[questionIndex] = list[questionIndex].copy(
            selectedIndex = selectedOptionIndex,
            customText = customText
        )
    }
    _uiState.value = _uiState.value.copy(
        activeAiPlanSession = session.copy(questions = updatedQuestions)
    )
}

/**
 * [EN] ViewModel action/helper `onStepAiPlanQuestion` handling step ai plan question while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onStepAiPlanQuestion` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onStepAiPlanQuestion(delta: Int) {
    val session = _uiState.value.activeAiPlanSession ?: return
    val nextIdx = (session.currentStepIndex + delta).coerceIn(0, (session.questions.size - 1).coerceAtLeast(0))
    _uiState.value = _uiState.value.copy(
        activeAiPlanSession = session.copy(currentStepIndex = nextIdx)
    )
}

/**
 * [EN] ViewModel action/helper `onExecuteConfirmedAiPlan` handling execute confirmed ai plan while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onExecuteConfirmedAiPlan` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onExecuteConfirmedAiPlan() {
    val cur = _uiState.value
    val session = cur.activeAiPlanSession ?: return
    val answersMap = session.questions.associate { q -> q.title to q.resolvedAnswer() }
    val apiKey = preferencesRepository.getAiApiKey(cur.selectedAiModelId)
    val customEndpoint = preferencesRepository.getAiCustomEndpoint()
    val customModelName = preferencesRepository.getAiCustomModel(cur.selectedAiModelId)
    val isEn = cur.language == "en"
    val effectiveModelId = if (cur.selectedAiModelId == "auto") {
        if (apiKey.isNotBlank()) "gemini" else "auto"
    } else {
        cur.selectedAiModelId
    }
    val usesLiveNetwork = effectiveModelId != "auto" && (apiKey.isNotBlank() || effectiveModelId == "local")
    _uiState.value = cur.copy(activeAiPlanSession = null, isLoading = usesLiveNetwork)
    if (usesLiveNetwork) {
        viewModelScope.launch {
            val result = SpreadsheetAiAgent.executeAgentTurnSuspend(
                prompt = session.originalPrompt,
                editMode = "allow_edit",
                clarifyingAnswers = answersMap,
                vp = cur.viewport,
                modelId = cur.selectedAiModelId,
                apiKey = apiKey,
                customEndpoint = customEndpoint,
                customModelName = customModelName,
                isEn = isEn
            )
            _uiState.value = _uiState.value.copy(isLoading = false)
            applyAiExecutionResult(result)
        }
    } else {
        val result = SpreadsheetAiAgent.executeAgentTurn(
            prompt = session.originalPrompt,
            editMode = "allow_edit",
            clarifyingAnswers = answersMap,
            vp = cur.viewport,
            modelId = cur.selectedAiModelId,
            apiKey = apiKey,
            customEndpoint = customEndpoint,
            customModelName = customModelName,
            isEn = isEn
        )
        applyAiExecutionResult(result)
    }
}

/**
 * [EN] ViewModel action/helper `onCancelAiPlan` handling cancel ai plan while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onCancelAiPlan` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onCancelAiPlan() {
    _uiState.value = _uiState.value.copy(activeAiPlanSession = null)
}

/**
 * [EN] ViewModel action/helper `onImportWebAiJsonResponse` handling import web ai json response while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onImportWebAiJsonResponse` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onImportWebAiJsonResponse(rawText: String): Boolean {
    val parsed = SpreadsheetAiAgent.parseJsonAiTurnResult(rawText, _uiState.value.selectedAiEditMode)
    if (parsed == null) {
        val isEn = _uiState.value.language == "en"
        _uiState.value = _uiState.value.copy(
            errorMessage = if (isEn) "Could not parse JSON response from Web AI" else "Web AIの回答から有効なJSONコマンドを検出できませんでした"
        )
        return false
    }
    applyAiExecutionResult(parsed)
    return true
}

/**
 * [EN] ViewModel action/helper `applyAiExecutionResult` handling apply ai execution result while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `applyAiExecutionResult` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.applyAiExecutionResult(result: AiTurnExecutionResult) {
    val undoStackBefore = customOverlayUndoStack.size
    val engineDepthBefore = _uiState.value.viewport.undoDepth
    var appliedCmdCount = 0
    val pendingChartPayloads = mutableListOf<String>()
    for ((cmdId, paramsJson) in result.commands) {
        if (cmdId == "ui.insertChart") {
            pendingChartPayloads.add(paramsJson)
        } else {
            dispatchCommand(cmdId, paramsJson)
            appliedCmdCount++
        }
    }
    val createdObjIds = mutableListOf<String>()
    for (payload in pendingChartPayloads) {
        val pObj = runCatching { JSONObject(payload) }.getOrNull() ?: JSONObject()
        val cType = pObj.optString("type", "column")
        val r0 = pObj.optInt("startRow", _uiState.value.viewport.selStartRow)
        val c0 = pObj.optInt("startCol", _uiState.value.viewport.selStartCol)
        val r1 = pObj.optInt("endRow", _uiState.value.viewport.selEndRow)
        val c1 = pObj.optInt("endCol", _uiState.value.viewport.selEndCol)
        val title = pObj.optString("title", "").takeIf { it.isNotBlank() }
        val chartId = onInsertChartFromRange(
            chartType = cType,
            startRow = r0,
            startCol = c0,
            endRow = r1,
            endCol = c1,
            customTitle = title
        )
        createdObjIds.add(chartId)
    }
    val overlayStepsAdded = (customOverlayUndoStack.size - undoStackBefore).coerceAtLeast(0)
    val engineStepsAdded = (_uiState.value.viewport.undoDepth - engineDepthBefore).coerceAtLeast(0)
    val recordedUndoSteps = if (overlayStepsAdded > 0) overlayStepsAdded else engineStepsAdded
    val totalApplied = appliedCmdCount + createdObjIds.size
    val formattedScript = SpreadsheetAiAgent.formatTurnResultAsScriptJson(result)
    val assistantMsg = AiChatMessage(
        id = "a_${System.currentTimeMillis()}",
        role = "assistant",
        text = result.replyText,
        summaryBullets = result.summaryBullets,
        appliedEditCount = totalApplied,
        canUndo = recordedUndoSteps > 0 || createdObjIds.isNotEmpty(),
        scriptJson = formattedScript
    )
    val cur = _uiState.value
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        aiMessages = cur.aiMessages + assistantMsg,
        lastAiUndoSteps = recordedUndoSteps,
        lastAiCreatedObjectIds = createdObjIds,
        statusNoticeMessage = if (totalApplied > 0) {
            if (isEn) "AI Agent applied $totalApplied sheet operations" else "AIエージェントが $totalApplied 件のシート編集・グラフ作成を適用しました"
        } else {
            cur.statusNoticeMessage
        }
    )
}

/**
 * [EN] ViewModel action/helper `onUndoLastAiEdit` handling undo last ai edit while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onUndoLastAiEdit` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onUndoLastAiEdit() {
    val cur = _uiState.value
    val steps = cur.lastAiUndoSteps.coerceIn(0, 500)
    val removedCharts = cur.lastAiCreatedObjectIds
    if (steps <= 0 && removedCharts.isEmpty()) return
    repeat(steps) {
        dispatchCommand("edit.undo", "{}")
    }
    val isEn = cur.language == "en"
    val undoMsg = AiChatMessage(
        id = "a_undo_${System.currentTimeMillis()}",
        role = "assistant",
        text = if (isEn) {
            "Reverted the last AI operation ($steps steps)."
        } else {
            "直前のAIによる編集・グラフ作成（$steps ステップ）をすべて元に戻しました。"
        }
    )
    val nextState = _uiState.value
    val filteredObjects = if (removedCharts.isNotEmpty()) {
        nextState.sheetObjects.filterNot { it.id in removedCharts }
    } else {
        nextState.sheetObjects
    }
    _uiState.value = nextState.copy(
        sheetObjects = filteredObjects,
        selectedObjectId = if (nextState.selectedObjectId in removedCharts) null else nextState.selectedObjectId,
        selectedObjectIds = nextState.selectedObjectIds - removedCharts.toSet(),
        lastAiUndoSteps = 0,
        lastAiCreatedObjectIds = emptyList(),
        aiMessages = nextState.aiMessages + undoMsg,
        statusNoticeMessage = if (isEn) "Reverted AI edit ($steps steps)" else "AI編集を元に戻しました ($steps ステップ)"
    )
}
