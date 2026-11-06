package com.masusheet.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masusheet.app.ai.AiChatMessage
import com.masusheet.app.ai.AiClarifyingQuestion
import com.masusheet.app.ai.AiPlanSession
import com.masusheet.app.ai.AiTurnExecutionResult
import com.masusheet.app.ai.MacroDefinition
import com.masusheet.app.ai.MacroStudioEngine
import com.masusheet.app.ai.SpreadsheetAiAgent
import com.masusheet.app.ai.VbaDebugState
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.MasuSheetUiState
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Macro Recorder, Step-by-Step VBA Debugger, & Non-Blocking 3-Mode AI Assistant
 * Handles macro recording, VBA stepping & safe rollback, and `Dispatchers.IO` coroutine execution for Built-in Script, Web AI JSON bridge, and Cloud/Local LLM APIs.
 *
 * ---
 * [JA / 日本語解説]
 * マクロ記録、VBAステップ実行と安全なロールバック、および `Dispatchers.IO` 非同期コルーチン による3モードAI編集実行を担うモジュールです。
 * ============================================================================
 */

// --- All-in-One Macro & VBA Studio ---

/**
 * [EN] Begins recording user spreadsheet commands into a named macro with optional shortcut metadata and relative/absolute reference mode.
 * [JA] 指定されたマクロ名・ショートカット・相対／絶対参照モードでユーザー操作の記録を開始します。
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
 * [EN] Stops macro recording, transpiles captured command steps into VBA source code, and saves the resulting `MacroDefinition`.
 * [JA] マクロ記録を停止し、記録された操作ステップからVBAコードを自動生成して保存済みマクロ一覧に追加します。
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
 * [EN] Toggles macro recording between relative cell offsets (`ActiveCell.Offset`) and absolute A1 references.
 * [JA] マクロ記録時のセル参照方式を「相対参照記録」と「絶対参照記録」の間で切り替えます。
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
 * [EN] Adds or replaces a `MacroDefinition` (matched case-insensitively by name) in the current workbook's saved macro list.
 * [JA] 作成またはVBA編集されたマクロ定義を、現在のブックの保存済みマクロ一覧へ登録・上書き保存します。
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
 * [EN] Removes the named macro from both the native engine script registry and `MasuSheetUiState.savedMacros`.
 * [JA] 指定された名前のマクロをエンジン登録およびUIの保存済みマクロ一覧から削除します。
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
 * [EN] Executes all expanded VBA statements (or recorded steps) of `macro`, tracking net Undo depth so the entire run can be rolled back in one click.
 * [JA] マクロのVBAステートメント（または記録ステップ）を一括実行し、一括ロールバック用に増加したUndoステップ数を記録します。
 */
fun MasuSheetViewModel.onRunMacro(macro: MacroDefinition): String {
    val curBefore = _uiState.value
    val activeWbId = curBefore.activeWorkbookId
    val preRunOverlay = captureCustomOverlaySnapshot(engineStepsCount = 0)
    val undoStackBefore = customOverlayUndoStack.size
    val engineDepthBefore = curBefore.viewport.undoDepth
    val backupBytes = runCatching {
        engineGateway.saveWorkbookBytes(MasuSheetConfig.sanitizeWorkbookFileName(curBefore.workbookTitle, "xlsx"))
    }.getOrNull()
    lastMacroPreRunSnapshots[activeWbId] = MasuSheetViewModel.MacroPreRunSnapshot(
        overlay = preRunOverlay,
        engineDepthBefore = engineDepthBefore,
        undoStackBefore = undoStackBefore,
        xlsxBackupBytes = backupBytes,
        workbookTitle = curBefore.workbookTitle,
        activeSheetIndex = curBefore.viewport.sheetIndex
    )
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
    val undoSteps = maxOf(executedOps, overlayDelta, engineDelta)
    if (undoSteps <= 0) {
        lastMacroPreRunSnapshots.remove(activeWbId)
    }
    val summary = lastMsg ?: "マクロ「${macro.name}」を実行しました（$executedOps ステップ）"
    _uiState.value = _uiState.value.copy(
        lastMacroUndoSteps = undoSteps,
        statusNoticeMessage = summary
    )
    return summary
}

/**
 * [EN] Initializes the interactive 1-step VBA debugger at line 0 with expanded loop statements and an empty variable watch table.
 * [JA] VBAコードのループを展開して1行ステップ実行デバッガを先頭行（0行目）で開始し、監視変数とログを初期化します。
 */
fun MasuSheetViewModel.onStartVbaDebug(macroName: String, vbaCode: String) {
    val lines = MacroStudioEngine.expandVbaStatements(vbaCode)
    val curDbg = _uiState.value
    lastMacroPreRunSnapshots[curDbg.activeWorkbookId] = MasuSheetViewModel.MacroPreRunSnapshot(
        overlay = captureCustomOverlaySnapshot(engineStepsCount = 0),
        engineDepthBefore = curDbg.viewport.undoDepth,
        undoStackBefore = customOverlayUndoStack.size,
        xlsxBackupBytes = runCatching {
            engineGateway.saveWorkbookBytes(MasuSheetConfig.sanitizeWorkbookFileName(curDbg.workbookTitle, "xlsx"))
        }.getOrNull(),
        workbookTitle = curDbg.workbookTitle,
        activeSheetIndex = curDbg.viewport.sheetIndex
    )
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
 * [EN] Executes the current line in the VBA 1-step debugger, updates watched loop/local variables and console logs, and advances the instruction pointer.
 * [JA] VBAデバッガの現在行を1ステップ実行し、変数ウォッチ一覧・実行ログ・Undoカウントを更新して次の行へ進めます。
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
 * [EN] Exits interactive VBA step-debugging mode while keeping accumulated console output visible for inspection.
 * [JA] VBA 1ステップ実行デバッガを終了します（確認用のコンソール出力ログは保持します）。
 */
fun MasuSheetViewModel.onStopVbaDebug() {
    val dbg = _uiState.value.vbaDebugState
    _uiState.value = _uiState.value.copy(
        vbaDebugState = dbg.copy(isDebugging = false)
    )
}

/**
 * [EN] Reverts every engine and overlay Undo step produced by the most recent macro run or VBA debug session.
 * [JA] 直前のマクロ一括実行またはVBAステップ実行で行われたすべてのシート変更を一括で元に戻します。
 */
fun MasuSheetViewModel.onRollbackLastMacroRun() {
    val activeWbId = _uiState.value.activeWorkbookId
    val steps = _uiState.value.lastMacroUndoSteps.coerceIn(0, 5000)
    val savedPreRun = lastMacroPreRunSnapshots.remove(activeWbId)
    if (steps <= 0 && savedPreRun == null) {
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
    if (savedPreRun != null) {
        val latestSnap = if (steps > 50 && savedPreRun.xlsxBackupBytes != null) {
            engineGateway.openWorkbookBytes(savedPreRun.workbookTitle, savedPreRun.xlsxBackupBytes)
            engineGateway.executeCommand("sheet.activate", "{\"sheet\":${savedPreRun.activeSheetIndex}}")
        } else {
            val engineDelta = (_uiState.value.viewport.undoDepth - savedPreRun.engineDepthBefore)
                .coerceIn(0, 5000)
            var snap = _uiState.value.viewport
            repeat(engineDelta) {
                snap = engineGateway.executeCommand("edit.undo", "{}")
            }
            snap
        }
        while (customOverlayUndoStack.size > savedPreRun.undoStackBefore.coerceAtLeast(0)) {
            customOverlayUndoStack.removeLast()
        }
        customOverlayRedoStack.clear()
        restoreCustomOverlaySnapshot(savedPreRun.overlay)
        val decorated = decorateWithCustomSizes(latestSnap)
        _uiState.value = _uiState.value.copy(
            viewport = decorated,
            isDirty = decorated.isDirty,
            lastMacroUndoSteps = 0,
            vbaDebugState = VbaDebugState(),
            statusNoticeMessage = "マクロ実行前の状態にロールバック（元に戻す: $steps ステップ）しました"
        )
        saveCurrentWorkbookToSessionList()
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
 * [EN] Generates a VBA macro script from a natural-language prompt using the current sheet selection context.
 * [JA] 現在のシート選択範囲をコンテキストとして、自然言語の指示文から実行可能なVBAコードを生成します。
 */
fun MasuSheetViewModel.onGenerateVbaFromPrompt(prompt: String): String =
    MacroStudioEngine.generateVbaFromNaturalLanguage(prompt, _uiState.value.viewport)

// --- General-Purpose AI Spreadsheet Agent (Gemini / Claude / GPT / Local) ---

/**
 * [EN] Opens or closes the right-side AI Spreadsheet Assistant panel.
 * [JA] 画面右側のAIスプレッドシートアシスタントパネルの表示／非表示を切り替えます。
 */
fun MasuSheetViewModel.onToggleAiPanel(open: Boolean? = null) {
    val next = open ?: !_uiState.value.isAiPanelOpen
    _uiState.value = _uiState.value.copy(isAiPanelOpen = next)
}

/**
 * [EN] Updates the width of the right-side PivotTable Fields pane, clamped to `220dp..760dp`.
 * [JA] 右側の「ピボットテーブルのフィールド」ペインの幅を `220dp..760dp` の範囲内でリサイズします。
 */
fun MasuSheetViewModel.onResizePivotPaneWidth(widthDp: Float) {
    val clamped = widthDp.coerceIn(220f, 760f)
    _uiState.value = _uiState.value.copy(pivotPaneWidthDp = clamped)
}

/**
 * [EN] Updates the width of the secondary workbook pane in side-by-side Split Book View, clamped to `240dp..880dp`.
 * [JA] ブック並列表示（分割ビュー）時の右側サブブックペイン幅を `240dp..880dp` の範囲内でリサイズします。
 */
fun MasuSheetViewModel.onResizeSplitPaneWidth(widthDp: Float) {
    val clamped = widthDp.coerceIn(240f, 880f)
    _uiState.value = _uiState.value.copy(splitPaneWidthDp = clamped)
}

/**
 * [EN] Updates the width of the right-side AI Assistant panel, clamped to `220dp..820dp`.
 * [JA] 右側のAIアシスタントパネルの幅を `220dp..820dp` の範囲内でリサイズします。
 */
fun MasuSheetViewModel.onResizeAiPanelWidth(widthDp: Float) {
    val clamped = widthDp.coerceIn(220f, 820f)
    _uiState.value = _uiState.value.copy(aiPanelWidthDp = clamped)
}

/**
 * [EN] Persists and activates the selected AI model provider (`auto`, `gemini`, `claude`, `gpt`, or `local`).
 * [JA] 使用するAIモデル（`auto`・`gemini`・`claude`・`gpt`・`local`）を選択し、設定に永続化します。
 */
fun MasuSheetViewModel.onSelectAiModel(modelId: String) {
    preferencesRepository.setAiModelId(modelId)
    _uiState.value = _uiState.value.copy(selectedAiModelId = modelId)
}

/**
 * [EN] Persists and switches the AI permission mode (`allow_edit`, `plan`, or `chat`) and clears any pending plan confirmation session.
 * [JA] AI権限モード（`allow_edit` 編集許可・`plan` 事前確認・`chat` 会話のみ）を切り替え、未確定のプラン確認状態をリセットします。
 */
fun MasuSheetViewModel.onSelectAiEditMode(modeId: String) {
    preferencesRepository.setAiEditMode(modeId)
    _uiState.value = _uiState.value.copy(
        selectedAiEditMode = modeId,
        activeAiPlanSession = null
    )
}

/**
 * [EN] Retrieves the Keystore-encrypted API key stored for `modelId` (or empty string if unset).
 * [JA] 指定された `modelId` に対応する暗号化保存済みAPIキーを取得します（未設定時は空文字）。
 */
fun MasuSheetViewModel.getAiApiKeyForModel(modelId: String): String = preferencesRepository.getAiApiKey(modelId)
/**
 * [EN] Retrieves the user-configured custom HTTPS or `127.0.0.1` local LLM endpoint URL.
 * [JA] 設定済みのカスタムAIエンドポイントURL（HTTPSまたは `127.0.0.1` ローカルLLM）を取得します。
 */
fun MasuSheetViewModel.getAiCustomEndpoint(): String = preferencesRepository.getAiCustomEndpoint()
/**
 * [EN] Retrieves the custom model identifier configured for `modelId` (e.g. `qwen2.5:0.5b` or custom deployment name).
 * [JA] 指定された `modelId` 向けに保存されているカスタムモデル名を取得します。
 */
fun MasuSheetViewModel.getAiCustomModelName(modelId: String = _uiState.value.selectedAiModelId): String =
    preferencesRepository.getAiCustomModel(modelId)

/**
 * [EN] Saves the API key (into `AndroidKeystoreSecretStore`), custom endpoint URL, and custom model name for `modelId`.
 * [JA] `modelId` のAPIキー（Keystore暗号化保存）、カスタムエンドポイントURL、およびカスタムモデル名を保存します。
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
 * [EN] Submits a user prompt or pasted JSON command script to the AI agent, staging a confirmation card first whenever `plan` mode is active.
 * [JA] AIへの指示文または貼り付けられたJSONコマンドを送信します（`plan` モード時はセル変更前に必ず確認セッションを挟みます）。
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

    // [EN] If the user directly entered a MasuSheet JSON script block (`{"commands": [...]}`), honor the active AI permission mode:
    //      - "allow_edit": execute validated commands immediately.
    //      - "plan": stage a confirmation plan session first so no cells mutate without explicit approval.
    //      - "chat" / "chat_only": display the reply/summary without executing commands.
    // [JA] JSONスクリプト (`{"commands": [...]}`) 直接入力時も選択中の権限モードを厳守します
    //      （「編集を許可」のみ即時実行、「プラン」は確認後に反映、「チャットのみ」はシート変更なし）。
    if (clean.startsWith("{") && clean.contains("\"commands\"")) {
        val directParsed = SpreadsheetAiAgent.parseJsonAiTurnResult(clean, cur.selectedAiEditMode)
        if (directParsed != null) {
            if (cur.selectedAiEditMode == "plan" && directParsed.commands.isNotEmpty()) {
                val isEn = cur.language == "en"
                val confirmQuestion = AiClarifyingQuestion(
                    id = "confirm_json_script",
                    title = if (isEn) {
                        "Apply ${directParsed.commands.size} JSON script commands to the active sheet?"
                    } else {
                        "貼り付けられたJSONスクリプト（${directParsed.commands.size} 件の操作）をシートへ反映しますか？"
                    },
                    options = if (isEn) {
                        listOf("Apply commands to sheet", "Cancel (do not modify sheet)")
                    } else {
                        listOf("構成を確認してシートへ反映する", "キャンセル（シートを変更しない）")
                    }
                )
                val previewMsg = AiChatMessage(
                    id = "a_${System.currentTimeMillis()}",
                    role = "assistant",
                    text = if (isEn) {
                        "[🧭 Plan Mode] Ready to apply ${directParsed.commands.size} validated commands: ${directParsed.replyText}"
                    } else {
                        "【🧭 プランモード】検証済みのJSONコマンド（${directParsed.commands.size} 件）を確認しました。実行前にご確認ください：\n${directParsed.replyText}"
                    },
                    summaryBullets = directParsed.summaryBullets
                )
                _uiState.value = _uiState.value.copy(
                    aiMessages = _uiState.value.aiMessages + previewMsg,
                    activeAiPlanSession = AiPlanSession(
                        originalPrompt = clean,
                        questions = listOf(confirmQuestion),
                        currentStepIndex = 0
                    )
                )
            } else {
                applyAiExecutionResult(directParsed)
            }
            return
        }
    }

    val isEn = cur.language == "en"
    val (resolvedProviderForAuto, apiKey) = if (cur.selectedAiModelId == "auto") {
        val autoKey = preferencesRepository.getAiApiKey("auto")
        if (autoKey.isNotBlank()) {
            "auto" to autoKey
        } else {
            listOf("gemini", "claude", "gpt")
                .map { id -> id to preferencesRepository.getAiApiKey(id) }
                .firstOrNull { it.second.isNotBlank() }
                ?: ("auto" to "")
        }
    } else {
        cur.selectedAiModelId to preferencesRepository.getAiApiKey(cur.selectedAiModelId)
    }
    val customEndpoint = preferencesRepository.getAiCustomEndpoint()
    val customModelName = preferencesRepository.getAiCustomModel(resolvedProviderForAuto)
    val effectiveModelId = if (resolvedProviderForAuto == "auto") {
        if (apiKey.isNotBlank()) "gemini" else "auto"
    } else {
        resolvedProviderForAuto
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
        val originWorkbookId = cur.activeWorkbookId
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val result = SpreadsheetAiAgent.executeAgentTurnSuspend(
                prompt = clean,
                editMode = cur.selectedAiEditMode,
                clarifyingAnswers = emptyMap(),
                vp = cur.viewport,
                modelId = resolvedProviderForAuto,
                apiKey = apiKey,
                customEndpoint = customEndpoint,
                customModelName = customModelName,
                isEn = isEn
            )
            // [EN] Guard against cross-workbook mutation if the user switched workbook tabs while the network request was in flight.
            // [JA] AI通信中にユーザーが別のブックタブへ切り替えた場合、切り替え先の別ブックを誤って書き換えないよう保護します。
            if (_uiState.value.activeWorkbookId != originWorkbookId) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }
            handleAiTurnResult(result)
        }
    } else {
        val result = SpreadsheetAiAgent.executeAgentTurn(
            prompt = clean,
            editMode = cur.selectedAiEditMode,
            clarifyingAnswers = emptyMap(),
            vp = cur.viewport,
            modelId = resolvedProviderForAuto,
            apiKey = apiKey,
            customEndpoint = customEndpoint,
            customModelName = customModelName,
            isEn = isEn
        )
        handleAiTurnResult(result)
    }
}

/**
 * [EN] Records the user's selected option or free-text answer for question `questionIndex` in the active AI planning session.
 * [JA] AIプラン確認セッション内の質問 `questionIndex` に対する選択肢または自由記述回答を更新します。
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
 * [EN] Moves backward or forward (`delta`) between clarifying questions in the active AI planning card.
 * [JA] AIプラン確認カードの現在の質問ステップを前後（`delta`）へ移動します。
 */
fun MasuSheetViewModel.onStepAiPlanQuestion(delta: Int) {
    val session = _uiState.value.activeAiPlanSession ?: return
    val nextIdx = (session.currentStepIndex + delta).coerceIn(0, (session.questions.size - 1).coerceAtLeast(0))
    _uiState.value = _uiState.value.copy(
        activeAiPlanSession = session.copy(currentStepIndex = nextIdx)
    )
}

/**
 * [EN] Executes (or cancels if declined) the active AI plan session after user confirmation and applies the resulting validated commands.
 * [JA] ユーザーが確認したAIプラン（またはJSONコマンド確認カード）を確定実行し、検証済みコマンドをシートへ反映します。
 */
fun MasuSheetViewModel.onExecuteConfirmedAiPlan() {
    val cur = _uiState.value
    val session = cur.activeAiPlanSession ?: return
    val rawOrig = session.originalPrompt.trim()
    if (rawOrig.startsWith("{") && rawOrig.contains("\"commands\"")) {
        val firstQuestion = session.questions.firstOrNull()
        if (firstQuestion != null && firstQuestion.selectedIndex == 1 && firstQuestion.customText.isBlank()) {
            _uiState.value = cur.copy(activeAiPlanSession = null)
            return
        }
        val parsed = SpreadsheetAiAgent.parseJsonAiTurnResult(rawOrig, "allow_edit")
        _uiState.value = cur.copy(activeAiPlanSession = null)
        if (parsed != null) {
            applyAiExecutionResult(parsed)
        }
        return
    }
    val answersMap = session.questions.associate { q -> q.title to q.resolvedAnswer() }
    val isEn = cur.language == "en"
    val (resolvedProviderForAuto, apiKey) = if (cur.selectedAiModelId == "auto") {
        val autoKey = preferencesRepository.getAiApiKey("auto")
        if (autoKey.isNotBlank()) {
            "auto" to autoKey
        } else {
            listOf("gemini", "claude", "gpt")
                .map { id -> id to preferencesRepository.getAiApiKey(id) }
                .firstOrNull { it.second.isNotBlank() }
                ?: ("auto" to "")
        }
    } else {
        cur.selectedAiModelId to preferencesRepository.getAiApiKey(cur.selectedAiModelId)
    }
    val customEndpoint = preferencesRepository.getAiCustomEndpoint()
    val customModelName = preferencesRepository.getAiCustomModel(resolvedProviderForAuto)
    val effectiveModelId = if (resolvedProviderForAuto == "auto") {
        if (apiKey.isNotBlank()) "gemini" else "auto"
    } else {
        resolvedProviderForAuto
    }
    val usesLiveNetwork = effectiveModelId != "auto" && (apiKey.isNotBlank() || effectiveModelId == "local")
    _uiState.value = cur.copy(activeAiPlanSession = null, isLoading = usesLiveNetwork)
    if (usesLiveNetwork) {
        val originWorkbookId = cur.activeWorkbookId
        viewModelScope.launch {
            val result = SpreadsheetAiAgent.executeAgentTurnSuspend(
                prompt = session.originalPrompt,
                editMode = "allow_edit",
                clarifyingAnswers = answersMap,
                vp = cur.viewport,
                modelId = resolvedProviderForAuto,
                apiKey = apiKey,
                customEndpoint = customEndpoint,
                customModelName = customModelName,
                isEn = isEn
            )
            if (_uiState.value.activeWorkbookId != originWorkbookId) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
            applyAiExecutionResult(result)
        }
    } else {
        val result = SpreadsheetAiAgent.executeAgentTurn(
            prompt = session.originalPrompt,
            editMode = "allow_edit",
            clarifyingAnswers = answersMap,
            vp = cur.viewport,
            modelId = resolvedProviderForAuto,
            apiKey = apiKey,
            customEndpoint = customEndpoint,
            customModelName = customModelName,
            isEn = isEn
        )
        applyAiExecutionResult(result)
    }
}

/**
 * [EN] Dismisses the active AI planning confirmation session without modifying any cells.
 * [JA] シートを一切変更せずに、現在表示中のAIプラン確認セッションをキャンセルします。
 */
fun MasuSheetViewModel.onCancelAiPlan() {
    _uiState.value = _uiState.value.copy(activeAiPlanSession = null)
}

/**
 * [EN] Parses a JSON response pasted from an external Web AI, routing through `plan` confirmation when in Plan Mode or applying allowlisted commands.
 * [JA] 外部Web AIから貼り付けられたJSON応答を解析し、プランモード時は確認カードを表示、編集許可時は検証済みコマンドを適用します。
 */
fun MasuSheetViewModel.onImportWebAiJsonResponse(rawText: String): Boolean {
    val cur = _uiState.value
    val parsed = SpreadsheetAiAgent.parseJsonAiTurnResult(rawText, cur.selectedAiEditMode)
    if (parsed == null) {
        val isEn = cur.language == "en"
        _uiState.value = cur.copy(
            errorMessage = if (isEn) "Could not parse JSON response from Web AI" else "Web AIの回答から有効なJSONコマンドを検出できませんでした"
        )
        return false
    }
    if (cur.selectedAiEditMode == "plan" && parsed.commands.isNotEmpty()) {
        val scriptJson = SpreadsheetAiAgent.formatTurnResultAsScriptJson(parsed)
        onSendAiPrompt(scriptJson)
        return true
    }
    applyAiExecutionResult(parsed)
    return true
}

/**
 * [EN] Dispatches validated AI spreadsheet and chart-creation commands, records net Undo depth, and appends the assistant summary message.
 * [JA] AI応答の検証済みコマンドとグラフ挿入を実行し、1クリックで元に戻せるようUndoステップ数を記録してチャット履歴を更新します。
 */
internal fun MasuSheetViewModel.applyAiExecutionResult(result: AiTurnExecutionResult) {
    val curBefore = _uiState.value
    val activeWbId = curBefore.activeWorkbookId
    val preAiOverlay = captureCustomOverlaySnapshot(engineStepsCount = 0)
    val undoStackCopyBefore = customOverlayUndoStack.toList()
    val engineDepthBefore = curBefore.viewport.undoDepth
    val backupBytes = if (result.commands.isNotEmpty()) {
        runCatching {
            engineGateway.saveWorkbookBytes(MasuSheetConfig.sanitizeWorkbookFileName(curBefore.workbookTitle, "xlsx"))
        }.getOrNull()
    } else {
        null
    }

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

    val engineStepsAdded = (_uiState.value.viewport.undoDepth - engineDepthBefore).coerceAtLeast(0)
    val totalApplied = appliedCmdCount + createdObjIds.size
    val recordedUndoSteps = if (totalApplied > 0) maxOf(totalApplied, engineStepsAdded) else 0
    if (totalApplied > 0) {
        // Collapse the entire AI turn (even 100+ commands + charts) into a single atomic Undo snapshot
        // so both AI Undo and toolbar Ctrl+Z / Undo revert 100% of the AI turn in 1 click without evicting earlier Undo history!
        val needsByteSnapshotRestore = backupBytes != null &&
            (appliedCmdCount > 50 || _uiState.value.viewport.undoDepth >= 95)
        customOverlayUndoStack.clear()
        customOverlayUndoStack.addAll(undoStackCopyBefore)
        pushCustomOverlayUndoSnapshot(
            preAiOverlay.copy(
                engineStepsCount = engineStepsAdded,
                xlsxBackupBytes = if (needsByteSnapshotRestore) backupBytes else null,
                backupWorkbookTitle = curBefore.workbookTitle,
                backupActiveSheetIndex = curBefore.viewport.sheetIndex
            )
        )
        lastAiPreRunSnapshots[activeWbId] = MasuSheetViewModel.MacroPreRunSnapshot(
            overlay = preAiOverlay,
            engineDepthBefore = engineDepthBefore,
            undoStackBefore = undoStackCopyBefore.size,
            xlsxBackupBytes = backupBytes,
            workbookTitle = curBefore.workbookTitle,
            activeSheetIndex = curBefore.viewport.sheetIndex,
            undoStackSnapshotsBefore = undoStackCopyBefore,
            engineDepthAfter = _uiState.value.viewport.undoDepth,
            createdObjectIds = createdObjIds,
            appliedStepsCount = recordedUndoSteps
        )
    }
    val formattedScript = SpreadsheetAiAgent.formatTurnResultAsScriptJson(result)
    val assistantMsg = AiChatMessage(
        id = "a_${System.currentTimeMillis()}",
        role = "assistant",
        text = result.replyText,
        summaryBullets = result.summaryBullets,
        appliedEditCount = totalApplied,
        canUndo = totalApplied > 0,
        scriptJson = formattedScript
    )
    val cur = _uiState.value
    val isEn = cur.language == "en"
    val decoratedVp = decorateWithCustomSizes(cur.viewport)
    val clearedPriorMsgs = if (totalApplied > 0) {
        cur.aiMessages.map { if (it.canUndo) it.copy(canUndo = false) else it }
    } else {
        cur.aiMessages
    }
    _uiState.value = cur.copy(
        viewport = decoratedVp,
        aiMessages = clearedPriorMsgs + assistantMsg,
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
 * [EN] Reverts all spreadsheet mutations and removes any charts created by the most recent AI assistant turn on the active workbook.
 * [JA] 現在のブックに対して直前のAI実行で行われたすべてのセル編集・書式・挿入グラフを一括で元に戻します。
 */
fun MasuSheetViewModel.onUndoLastAiEdit() {
    val cur = _uiState.value
    val activeWbId = cur.activeWorkbookId
    val savedPreRun = lastAiPreRunSnapshots.remove(activeWbId)
    if (savedPreRun == null && cur.lastAiUndoSteps <= 0 && cur.lastAiCreatedObjectIds.isEmpty()) return
    val isEn = cur.language == "en"

    if (savedPreRun == null) {
        // Active workbook has no pending AI snapshot (e.g. user switched workbooks) — do not mutate this workbook!
        _uiState.value = cur.copy(
            statusNoticeMessage = if (isEn) "No AI edits to undo on this workbook" else "このブックで元に戻せるAI編集はありません"
        )
        return
    }
    val steps = maxOf(
        cur.lastAiUndoSteps,
        savedPreRun.appliedStepsCount,
        savedPreRun.engineDepthAfter - savedPreRun.engineDepthBefore
    ).coerceIn(1, 5000)
    val removedCharts = savedPreRun.createdObjectIds.ifEmpty { cur.lastAiCreatedObjectIds }

    val alreadyUndoneViaToolbar = cur.viewport.undoDepth <= savedPreRun.engineDepthBefore &&
        customOverlayUndoStack.size <= savedPreRun.undoStackBefore

    if (!alreadyUndoneViaToolbar) {
        val engineDeltaFromPre = (cur.viewport.undoDepth - savedPreRun.engineDepthBefore).coerceIn(0, 5000)
        val topSnap = customOverlayUndoStack.lastOrNull()
        val topIsGroupedAiStep = cur.viewport.undoDepth == savedPreRun.engineDepthAfter &&
            topSnap?.engineStepsCount == (savedPreRun.engineDepthAfter - savedPreRun.engineDepthBefore) &&
            (topSnap.xlsxBackupBytes != null || (steps <= 50 && savedPreRun.engineDepthAfter < 95))

        if (topIsGroupedAiStep) {
            dispatchCommand("edit.undo", "{}")
        } else {
            val latestSnap = if ((steps > 50 || engineDeltaFromPre > 50 || savedPreRun.engineDepthAfter >= 95) && savedPreRun.xlsxBackupBytes != null) {
                engineGateway.openWorkbookBytes(savedPreRun.workbookTitle, savedPreRun.xlsxBackupBytes)
                engineGateway.executeCommand("sheet.activate", "{\"sheet\":${savedPreRun.activeSheetIndex}}")
            } else {
                var snap = _uiState.value.viewport
                repeat(engineDeltaFromPre) {
                    snap = engineGateway.executeCommand("edit.undo", "{}")
                }
                snap
            }
            customOverlayUndoStack.clear()
            customOverlayUndoStack.addAll(savedPreRun.undoStackSnapshotsBefore)
            customOverlayRedoStack.clear()
            restoreCustomOverlaySnapshot(savedPreRun.overlay)
            val decorated = decorateWithCustomSizes(latestSnap)
            _uiState.value = _uiState.value.copy(
                viewport = decorated,
                isDirty = decorated.isDirty
            )
        }
    }

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
    val clearedMsgs = nextState.aiMessages.map { if (it.canUndo) it.copy(canUndo = false) else it }
    _uiState.value = nextState.copy(
        sheetObjects = filteredObjects,
        selectedObjectId = if (nextState.selectedObjectId in removedCharts) null else nextState.selectedObjectId,
        selectedObjectIds = nextState.selectedObjectIds - removedCharts.toSet(),
        lastAiUndoSteps = 0,
        lastAiCreatedObjectIds = emptyList(),
        aiMessages = clearedMsgs + undoMsg,
        statusNoticeMessage = if (isEn) "Reverted AI edit ($steps steps)" else "AI編集を元に戻しました ($steps ステップ)"
    )
    saveCurrentWorkbookToSessionList()
}
