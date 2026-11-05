package com.masusheet.app.ui.ai

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masusheet.app.ai.SpreadsheetAiAgent
import com.masusheet.app.core.SpreadsheetI18n
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.ui.LocalDarkTheme
import com.masusheet.app.ui.ribbon.MasuSheetPillButton
import com.masusheet.app.viewmodel.onUndoLastAiEdit

/**
 * ============================================================================
 * [EN] AI Spreadsheet Edit Sidebar Panel (3-Mode Workflow)
 * Implements [AiSpreadsheetAssistantPanel] supporting Built-in Script templates, Web AI copy/paste JSON bridge, and Cloud/Local LLM API settings with masked API key input (`H-05`) and per-model IDs (`H-07`).
 *
 * ---
 * [JA / 日本語解説]
 * ①内蔵スマートスクリプト、②Web AIコピペJSON連携、③クラウドAPI／ローカルLLM (`127.0.0.1`) の3モードを備えたAI編集サイドパネル ([AiSpreadsheetAssistantPanel]) です。
 * ============================================================================
 */

// ============================================================================
// 5. GENERAL-PURPOSE AI SPREADSHEET AGENT SIDEBAR PANEL (COPILOT / GEMINI / CLAUDE / GPT / LOCAL)
// ============================================================================

/**
 * [EN] Docked right-side AI Edit panel providing Built-in Script templates, Web AI JSON bridge, and Cloud/Local API chat.
 * [JA] 内蔵スクリプト・Web AIコピペJSON連携・クラウド／ローカルLLM API設定とチャットを提供するAI編集サイドパネルです。
 */
@Composable
fun AiSpreadsheetAssistantPanel(
    state: MasuSheetUiState,
    initialApiKey: String,
    initialCustomEndpoint: String,
    initialCustomModel: String,
    onSelectModel: (String) -> Unit,
    onSelectEditMode: (String) -> Unit,
    onSaveProviderSettings: (modelId: String, apiKey: String, endpoint: String, modelName: String) -> Unit,
    onSendPrompt: (String) -> Unit,
    onAnswerPlanQuestion: (qIdx: Int, optIdx: Int, customText: String) -> Unit,
    onStepPlanQuestion: (Int) -> Unit,
    onExecuteConfirmedPlan: () -> Unit,
    onCancelPlan: () -> Unit,
    onUndoLastAiEdit: () -> Unit,
    onClosePanel: () -> Unit,
    onCopyTextToClipboard: (String) -> Unit = {},
    onImportWebAiJson: (String) -> Unit = {}
) {
    val isEn = state.language == "en"
    var promptInput by remember { mutableStateOf("") }
    var showApiSettings by remember { mutableStateOf(false) }
    var showWebBridge by remember { mutableStateOf(false) }
    var webAiPasteDraft by remember { mutableStateOf("") }
    var copiedFeedbackBanner by remember { mutableStateOf<String?>(null) }

    val defaultWebReq = if (isEn) {
        "Analyze this table, add totals, and create a chart"
    } else {
        "この表を集計・分析してグラフ化してください"
    }
    var webPromptScriptDraft by remember(state.viewport.revision, state.viewport.sheetIndex, isEn) {
        mutableStateOf(
            SpreadsheetAiAgent.buildWebAiPromptForCopy(
                prompt = promptInput.ifBlank { defaultWebReq },
                vp = state.viewport,
                isEn = isEn
            )
        )
    }

    fun copyWithFeedback(text: String, labelJa: String, labelEn: String = labelJa) {
        if (text.isBlank()) return
        onCopyTextToClipboard(text)
        copiedFeedbackBanner = if (isEn) "✓ Copied: $labelEn" else "✓ コピーしました: $labelJa"
    }

    val cleanInitialEndpoint = remember(initialCustomEndpoint) {
        initialCustomEndpoint.trim()
    }
    var apiKeyDraft by remember(state.selectedAiModelId, initialApiKey) { mutableStateOf(initialApiKey) }
    var showApiKeyPlain by remember(state.selectedAiModelId) { mutableStateOf(false) }
    var endpointDraft by remember(cleanInitialEndpoint) { mutableStateOf(cleanInitialEndpoint) }
    var customModelDraft by remember(state.selectedAiModelId, initialCustomModel) { mutableStateOf(initialCustomModel) }

    val (modelStatusLine, modeStatusLine) = SpreadsheetAiAgent.describeModelAndModeStatus(
        modelId = state.selectedAiModelId,
        editMode = state.selectedAiEditMode,
        apiKey = initialApiKey,
        customEndpoint = initialCustomEndpoint,
        customModelName = initialCustomModel,
        isEn = isEn
    )

    CompositionLocalProvider(LocalDarkTheme provides true) {
    Column(
        modifier = Modifier
            .width(state.aiPanelWidthDp.coerceIn(220f, 820f).dp)
            .fillMaxHeight()
            .imePadding()
            .background(Color(0xFF0F172A))
            .border(width = 1.dp, color = Color(0xFF1F9D55))
            .padding(10.dp)
    ) {
        // 1. Pinned Top Header (`AI 編集` / `AI Edit` + Web Bridge + Settings + Close)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F9D55)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✨", fontSize = 11.sp)
                }
                Text(
                    text = if (isEn) "AI Edit" else "AI 編集",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MasuSheetPillButton(
                    label = if (isEn) "📋 Web / Script" else "📋 Web・スクリプト",
                    highlight = showWebBridge,
                    onClick = {
                        showWebBridge = !showWebBridge
                        if (showWebBridge) {
                            showApiSettings = false
                            webPromptScriptDraft = SpreadsheetAiAgent.buildWebAiPromptForCopy(
                                prompt = promptInput.ifBlank { defaultWebReq },
                                vp = state.viewport,
                                isEn = isEn
                            )
                        }
                    }
                )
                MasuSheetPillButton(
                    label = if (isEn) "⚙ API / Edge AI" else "⚙ API・エッジAI",
                    highlight = showApiSettings,
                    onClick = {
                        showApiSettings = !showApiSettings
                        if (showApiSettings) showWebBridge = false
                    }
                )
                MasuSheetPillButton("✕", onClick = onClosePanel)
            }
        }

        if (copiedFeedbackBanner != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF14532D))
                    .border(1.dp, Color(0xFF4ADE80), RoundedCornerShape(6.dp))
                    .clickable { copiedFeedbackBanner = null }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = copiedFeedbackBanner.orEmpty(),
                    color = Color(0xFFDCFCE7),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("✕", color = Color(0xFF86EFAC), fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Scrollable Middle Content so Model/Mode controls, Settings, Quick Chips, Plan Card, and Chat Stream are NEVER clipped on landscape heights
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 2. Model Selector Chips (Auto / Gemini / Claude / GPT / Local)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isEn) "Model:" else "モデル:", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                SpreadsheetAiAgent.AVAILABLE_MODELS.forEach { m ->
                    val chipLabel = if (m.id == "local") {
                        if (isEn) "LOCAL (Edge AI)" else "LOCAL (エッジAI)"
                    } else {
                        m.id.uppercase()
                    }
                    MasuSheetPillButton(
                        label = chipLabel,
                        highlight = state.selectedAiModelId == m.id,
                        onClick = { onSelectModel(m.id) }
                    )
                }
            }

            // 3. Edit Mode Selector Chips (`編集を許可` / `プラン` / `チャットのみ`)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isEn) "Mode:" else "権限モード:", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                SpreadsheetAiAgent.EDIT_MODES.forEach { (modeId, labelJa) ->
                    MasuSheetPillButton(
                        label = SpreadsheetI18n.t(labelJa, isEn),
                        highlight = state.selectedAiEditMode == modeId,
                        onClick = { onSelectEditMode(modeId) }
                    )
                }
            }

            // 3b. Active Model & Permission Mode Status Card (Always visible & updates immediately when Model or Mode is clicked!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF162231))
                    .border(
                        width = 1.dp,
                        color = when (state.selectedAiEditMode) {
                            "plan" -> Color(0xFF38BDF8)
                            "chat" -> Color(0xFFF59E0B)
                            else -> Color(0xFF1F9D55)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = modelStatusLine,
                    color = if (initialApiKey.isNotBlank() || state.selectedAiModelId == "auto") Color(0xFF4ADE80) else Color(0xFF38BDF8),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = modeStatusLine,
                    color = Color(0xFFE2E8F0),
                    fontSize = 10.5.sp
                )
                if (state.selectedAiModelId != "auto" && initialApiKey.isBlank() && !showApiSettings) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.selectedAiModelId == "local") {
                                if (isEn) "💡 Local device loopback (127.0.0.1:11434) — No internet used" else "💡 端末内通信 (127.0.0.1:11434・ネット通信なし) でエッジAIへ接続"
                            } else {
                                if (isEn) "💡 Uses template script processing until API key is set" else "💡 API未設定時は定型スクリプト処理で動作"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 9.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        MasuSheetPillButton(
                            label = if (state.selectedAiModelId == "local") {
                                if (isEn) "🧠 Edge AI Guide & Setup" else "🧠 エッジAI解説・設定"
                            } else {
                                if (isEn) "🔑 Set ${state.selectedAiModelId.uppercase()} Key" else "🔑 ${state.selectedAiModelId.uppercase()} キー設定"
                            },
                            highlight = true,
                            onClick = {
                                showApiSettings = true
                                showWebBridge = false
                            }
                        )
                    }
                }
            }

            // Optional Gemini App / Web Copy-Paste & Editable Script Bridge
            if (showWebBridge) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = if (isEn) {
                            "📋 Web AI Bridge & Editable Prompt Script (No API Key Required)"
                        } else {
                            "📋 Web連携・自分のスクリプト確認＆コピー (APIキー不要)"
                        },
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEn) {
                            "① Below is your full MasuSheet prompt script (editable & selectable). Click Copy and paste it into Gemini/ChatGPT/Claude.\n② Paste the AI's JSON script below (or write your own JSON script) and click Apply."
                        } else {
                            "① 下の枠内に現在のシート情報と指示を含むスクリプト全文が表示されます（自由に書き換え・範囲選択コピー可能）。\n② [スクリプト全文をコピー]でGemini等へ貼り付け、返ってきたJSON（または自作JSONスクリプト）を下の欄に貼って[反映]を押します。"
                        },
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (isEn) "① Outgoing Prompt / Script (Editable & Copyable):" else "① 送信用プロンプト / 自分のスクリプト（直接編集・選択コピー可）:",
                        color = Color(0xFF93C5FD),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    BasicTextField(
                        value = webPromptScriptDraft,
                        onValueChange = { webPromptScriptDraft = it },
                        textStyle = TextStyle(color = Color(0xFFE2E8F0), fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                            .padding(6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MasuSheetPillButton(
                            label = if (isEn) "📋 Copy Full Script" else "📋 ① スクリプト全文をコピー",
                            highlight = true,
                            onClick = {
                                val textToCopy = webPromptScriptDraft.ifBlank {
                                    SpreadsheetAiAgent.buildWebAiPromptForCopy(
                                        prompt = promptInput.ifBlank { defaultWebReq },
                                        vp = state.viewport,
                                        isEn = isEn
                                    ).also { webPromptScriptDraft = it }
                                }
                                copyWithFeedback(textToCopy, "Web連携スクリプト全文", "Full Web Script")
                            }
                        )
                        MasuSheetPillButton(
                            label = if (isEn) "🔄 Refresh from Sheet" else "🔄 シート最新状態から再生成",
                            onClick = {
                                webPromptScriptDraft = SpreadsheetAiAgent.buildWebAiPromptForCopy(
                                    prompt = promptInput.ifBlank { defaultWebReq },
                                    vp = state.viewport,
                                    isEn = isEn
                                )
                            }
                        )
                        if (promptInput.isNotBlank()) {
                            MasuSheetPillButton(
                                label = if (isEn) "📋 Copy My Prompt Only" else "📋 自分の指示文だけコピー",
                                onClick = {
                                    copyWithFeedback(promptInput, "自分の指示文", "My Prompt")
                                }
                            )
                        }
                    }
                    Text(
                        text = if (isEn) "② Paste AI JSON Response or Custom Script to Apply:" else "② AIの回答(JSON) または 自作スクリプトをここに貼り付けてシートへ反映:",
                        color = Color(0xFF4ADE80),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    BasicTextField(
                        value = webAiPasteDraft,
                        onValueChange = { webAiPasteDraft = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                            .padding(6.dp),
                        decorationBox = { inner ->
                            if (webAiPasteDraft.isEmpty()) {
                                Text(
                                    text = if (isEn) "Paste AI's JSON response or custom script here..." else "ここにAIの回答(JSON)や自分のスクリプトを貼り付け...",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                            inner()
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (webAiPasteDraft.isNotBlank()) {
                            MasuSheetPillButton(
                                label = if (isEn) "📋 Copy Pasted JSON" else "📋 貼り付けたJSONをコピー",
                                onClick = {
                                    copyWithFeedback(webAiPasteDraft, "JSONスクリプト", "JSON Script")
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        MasuSheetPillButton(
                            label = if (isEn) "📥 2. Apply JSON Script to Sheet" else "📥 ② スクリプト(JSON)をシートに反映",
                            highlight = true,
                            onClick = {
                                if (webAiPasteDraft.isNotBlank()) {
                                    onImportWebAiJson(webAiPasteDraft)
                                    webAiPasteDraft = ""
                                    showWebBridge = false
                                }
                            }
                        )
                    }
                }
            }

            // Optional API Key & Local Edge AI Configuration Card
            if (showApiSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = if (isEn) {
                            "🔑 Cloud LLM / On-Device Edge AI Settings (${state.selectedAiModelId.uppercase()})"
                        } else {
                            "🔑 クラウドLLM / 端末内エッジAI 接続設定 (${state.selectedAiModelId.uppercase()})"
                        },
                        color = Color(0xFF4ADE80),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.selectedAiModelId == "local") {
                        // Clear Q&A Guide Box for Edge AI / 127.0.0.1 / Offline Loopback
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                                .padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (isEn) {
                                    "💡 How Edge AI Works (No Internet / No Browser Used)"
                                } else {
                                    "💡 エッジAIの仕組み（ネット通信なし・ブラウザ不使用）"
                                },
                                color = Color(0xFF38BDF8),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEn) {
                                    "1. Model Setup: Because AI models are 400MB~1.6GB, you download & start the model once inside the Linux Terminal (Ollama / llama.cpp).\n" +
                                        "2. What is 127.0.0.1:11434 (Endpoint)?: '127.0.0.1' means 'this device itself' (internal loopback). It is an internal extension number connecting MasuSheet to the AI running inside your device—it NEVER goes out to the internet and uses 0 bytes of mobile data."
                                } else {
                                    "① モデルの事前セット: AIモデル(約400MB〜1.6GB)は容量が大きいため、初回のみLinuxターミナル等でダウンロード＆起動しておきます。\n" +
                                        "② アドレス(127.0.0.1:11434)とは？: ブラウザや外部ネット通信ではありません！「127.0.0.1」は『この端末自身の中』を指す特別な番号で、同じ端末内で動いているAIモデルにMasuSheetから話しかけるための「端末内の内線番号（窓口アドレス）」です。機内モード（完全オフライン・通信量ゼロ）で動きます。"
                                },
                                color = Color(0xFFE2E8F0),
                                fontSize = 9.5.sp
                            )
                        }
                        Text(
                            text = if (isEn) "🧠 1. Choose Lightweight Edge AI Model:" else "🧠 ① 軽量エッジAIモデルを選択:",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            MasuSheetPillButton(
                                label = if (isEn) "⚡ Qwen2.5 0.5B (Ultra-light ~400MB)" else "⚡ Qwen2.5 0.5B (超軽量・約400MB)",
                                highlight = customModelDraft.ifBlank { "qwen2.5:0.5b" } == "qwen2.5:0.5b",
                                onClick = {
                                    endpointDraft = "http://127.0.0.1:11434/v1/chat/completions"
                                    customModelDraft = "qwen2.5:0.5b"
                                }
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "⚡ Gemma2 2B (High accuracy ~1.6GB)" else "⚡ Gemma2 2B (高精度・約1.6GB)",
                                highlight = customModelDraft == "gemma2:2b",
                                onClick = {
                                    endpointDraft = "http://127.0.0.1:11434/v1/chat/completions"
                                    customModelDraft = "gemma2:2b"
                                }
                            )
                            MasuSheetPillButton(
                                label = if (isEn) "⚡ Llama 3.2 1B (~1.3GB)" else "⚡ Llama 3.2 1B (約1.3GB)",
                                highlight = customModelDraft == "llama3.2:1b",
                                onClick = {
                                    endpointDraft = "http://127.0.0.1:11434/v1/chat/completions"
                                    customModelDraft = "llama3.2:1b"
                                }
                            )
                            MasuSheetPillButton(
                                label = "⚡ llama.cpp (port 8080)",
                                highlight = endpointDraft.contains(":8080"),
                                onClick = {
                                    endpointDraft = "http://127.0.0.1:8080/v1/chat/completions"
                                    if (customModelDraft.isBlank()) customModelDraft = "local-model"
                                }
                            )
                        }
                        val activeMdl = customModelDraft.ifBlank { "qwen2.5:0.5b" }
                        val termCmd = "curl -fsSL https://ollama.com/install.sh | sh && ollama serve & sleep 2 && ollama run $activeMdl"
                        Text(
                            text = if (isEn) "② Terminal Setup & Start Script (Copy & paste into Terminal once):" else "② ターミナル起動スクリプト（コピーしてターミナルに貼り付けるとAIをセット＆起動）:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp
                        )
                        SelectionContainer {
                            Text(
                                text = termCmd,
                                color = Color(0xFF4ADE80),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                                    .padding(6.dp)
                            )
                        }
                        MasuSheetPillButton(
                            label = if (isEn) {
                                "📋 Copy Terminal Setup Command ($activeMdl)"
                            } else {
                                "📋 ターミナル起動コマンドをコピー ($activeMdl)"
                            },
                            highlight = true,
                            onClick = {
                                copyWithFeedback(termCmd, "ターミナル起動コマンド", "Terminal Command")
                            }
                        )
                        Text(
                            text = if (isEn) "Edge AI Model Name:" else "エッジAI モデル名:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp
                        )
                        BasicTextField(
                            value = customModelDraft,
                            onValueChange = { customModelDraft = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                                .padding(6.dp),
                            decorationBox = { inner ->
                                if (customModelDraft.isEmpty()) {
                                    Text(
                                        text = "qwen2.5:0.5b",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                }
                                inner()
                            }
                        )
                        Text(
                            text = if (isEn) "Internal Loopback Endpoint Address (Default: http://127.0.0.1:11434/v1/chat/completions):" else "端末内アクセス窓口 (エンドポイント / 空欄時は http://127.0.0.1:11434/v1/chat/completions):",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp
                        )
                        BasicTextField(
                            value = endpointDraft,
                            onValueChange = { endpointDraft = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                                .padding(6.dp),
                            decorationBox = { inner ->
                                if (endpointDraft.isEmpty()) {
                                    Text(
                                        text = "http://127.0.0.1:11434/v1/chat/completions",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                }
                                inner()
                            }
                        )
                    } else {
                        Text(
                            text = if (isEn) {
                                "• Enter your Gemini (aistudio.google.com), Claude (console.anthropic.com), or OpenAI API key.\n• Without an API key, template script processing is used."
                            } else {
                                "・Gemini (Google AI Studio) / Claude / GPT のAPIキーを登録するとクラウドLLMへ接続します。\n・APIキー未設定時は定型スクリプト処理で動作します。"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                        Text(
                            text = if (isEn) "API Key for ${state.selectedAiModelId.uppercase()}:" else "${state.selectedAiModelId.uppercase()} 用 APIキー:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BasicTextField(
                                value = apiKeyDraft,
                                onValueChange = { apiKeyDraft = it },
                                singleLine = true,
                                visualTransformation = if (showApiKeyPlain) VisualTransformation.None else PasswordVisualTransformation(),
                                textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                                    .padding(6.dp)
                            )
                            MasuSheetPillButton(
                                label = if (showApiKeyPlain) (if (isEn) "🙈 Hide" else "🙈 隠す") else (if (isEn) "👁️ Show" else "👁️ 表示"),
                                onClick = { showApiKeyPlain = !showApiKeyPlain }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        MasuSheetPillButton(
                            label = if (isEn) "💾 Save Settings" else "💾 設定を保存",
                            highlight = true,
                            onClick = {
                                onSaveProviderSettings(state.selectedAiModelId, apiKeyDraft, endpointDraft, customModelDraft)
                                showApiSettings = false
                            }
                        )
                    }
                }
            }

            // 4. Quick Action Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val quickPrompts = if (isEn) {
                    listOf(
                        "グラフ化して" to "📊 Create a chart from this sheet",
                        "円グラフにして" to "🥧 Create a pie chart",
                        "折れ線グラフを作って" to "📈 Create a line chart",
                        "現在の表に合計・平均・構成比(%)を追加して整形" to "➕ Add Sum, Average & Share(%)",
                        "在庫・発注管理シートを作成して" to "📦 Create Inventory Sheet",
                        "月次予算・実績比較管理シートを作成" to "💰 Create Budget vs Actual Sheet"
                    )
                } else {
                    listOf(
                        "グラフ化して" to "📊 グラフ化して",
                        "円グラフにして" to "🥧 円グラフにして",
                        "折れ線グラフを作って" to "📈 折れ線グラフを作って",
                        "現在の表に合計・平均・構成比(%)を追加して整形" to "💡 合計・平均・構成比(%)を追加",
                        "在庫・発注管理シートを作成して" to "💡 在庫・発注管理シートを作成",
                        "月次予算・実績比較管理シートを作成" to "💡 月次予算・実績比較表を作成"
                    )
                }
                quickPrompts.forEach { (rawPrompt, chipLabel) ->
                    MasuSheetPillButton(
                        label = chipLabel,
                        onClick = { onSendPrompt(rawPrompt) }
                    )
                }
            }

            // 5. Interactive Clarifying Question Card (When in `プラン` mode!)
            val planSession = state.activeAiPlanSession
            if (planSession != null && planSession.questions.isNotEmpty()) {
                val qIdx = planSession.currentStepIndex.coerceIn(0, planSession.questions.size - 1)
                val q = planSession.questions[qIdx]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEn) "📋 Plan Confirmation (${qIdx + 1} of ${planSession.questions.size})" else "📋 プラン確認 (${qIdx + 1} of ${planSession.questions.size})",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MasuSheetPillButton("◀", onClick = { onStepPlanQuestion(-1) })
                            MasuSheetPillButton("▶", onClick = { onStepPlanQuestion(1) })
                            MasuSheetPillButton("✕", onClick = onCancelPlan)
                        }
                    }
                    Text(
                        text = q.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    q.options.forEachIndexed { optIdx, optText ->
                        val isSelected = q.selectedIndex == optIdx && q.customText.isBlank()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF163828) else Color(0xFF0F172A))
                                .border(1.dp, if (isSelected) Color(0xFF4ADE80) else Color(0xFF334155), RoundedCornerShape(6.dp))
                                .clickable {
                                    onAnswerPlanQuestion(qIdx, optIdx, "")
                                    if (qIdx < planSession.questions.size - 1) {
                                        onStepPlanQuestion(1)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSelected) "◉ $optText" else "○ $optText",
                                color = if (isSelected) Color(0xFF4ADE80) else Color(0xFFE2E8F0),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    BasicTextField(
                        value = q.customText,
                        onValueChange = { onAnswerPlanQuestion(qIdx, q.selectedIndex, it) },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp),
                        cursorBrush = SolidColor(Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(5.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        MasuSheetPillButton(
                            label = if (isEn) "✓ Execute Confirmed Plan" else "✓ このプランでシートを作成・実行",
                            highlight = true,
                            onClick = onExecuteConfirmedPlan
                        )
                    }
                }
            }

            // 6. Conversation Stream + Copyable Prompts & Executed JSON Scripts + Rollback Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141C26))
                    .border(1.dp, Color(0xFF263344), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.aiMessages.forEach { msg ->
                    val isUser = msg.role == "user"
                    var showMsgScript by remember(msg.id) { mutableStateOf(false) }
                    val resolvedMsgText = if (msg.id == "welcome" && isEn) {
                        "Welcome to the MasuSheet AI Spreadsheet Agent!\nAsk me to build any custom table, create charts, insert formulas, apply formatting, or paste your own JSON script."
                    } else {
                        msg.text
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isUser) Color(0xFF1E3A8A) else Color(0xFF1E293B))
                            .border(
                                1.dp,
                                if (isUser) Color(0xFF3B82F6) else Color(0xFF334155),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUser) {
                                    if (isEn) "👤 You (${msg.timestamp})" else "👤 あなた (${msg.timestamp})"
                                } else {
                                    if (isEn) "✨ AI Agent (${msg.timestamp})" else "✨ AI エージェント (${msg.timestamp})"
                                },
                                color = if (isUser) Color(0xFF93C5FD) else Color(0xFF4ADE80),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                MasuSheetPillButton(
                                    label = if (isEn) "📋 Copy" else "📋 コピー",
                                    onClick = {
                                        val fullCopy = buildString {
                                            append(resolvedMsgText)
                                            if (msg.summaryBullets.isNotEmpty()) {
                                                append("\n")
                                                msg.summaryBullets.forEach { append("・$it\n") }
                                            }
                                        }.trim()
                                        copyWithFeedback(
                                            fullCopy,
                                            if (isUser) "自分のスクリプト/指示" else "AIの回答",
                                            if (isUser) "Your Prompt" else "AI Reply"
                                        )
                                    }
                                )
                                if (isUser) {
                                    MasuSheetPillButton(
                                        label = if (isEn) "✏️ Reuse" else "✏️ 入力欄へ",
                                        onClick = { promptInput = msg.text }
                                    )
                                }
                                if (!isUser && msg.scriptJson.isNotBlank()) {
                                    MasuSheetPillButton(
                                        label = if (showMsgScript) {
                                            if (isEn) "📜 Hide Script" else "📜 スクリプト隠す"
                                        } else {
                                            if (isEn) "📜 View/Copy Script" else "📜 実行スクリプト(JSON)"
                                        },
                                        highlight = showMsgScript,
                                        onClick = { showMsgScript = !showMsgScript }
                                    )
                                }
                            }
                        }
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = resolvedMsgText,
                                    color = Color.White,
                                    fontSize = 11.5.sp
                                )
                                msg.summaryBullets.forEach { bullet ->
                                    Text(
                                        text = "・$bullet",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        if (showMsgScript && msg.scriptJson.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEn) "📜 Generated MasuSheet JSON Script:" else "📜 実行された MasuSheet スクリプト (JSON):",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton(
                                            label = if (isEn) "📋 Copy Script" else "📋 スクリプトをコピー",
                                            highlight = true,
                                            onClick = {
                                                copyWithFeedback(msg.scriptJson, "実行スクリプト(JSON)", "JSON Script")
                                            }
                                        )
                                        MasuSheetPillButton(
                                            label = if (isEn) "✏️ To Input" else "✏️ 入力欄へ引用",
                                            onClick = { promptInput = msg.scriptJson }
                                        )
                                    }
                                }
                                SelectionContainer {
                                    Text(
                                        text = msg.scriptJson,
                                        color = Color(0xFF93C5FD),
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        if (msg.appliedEditCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEn) "✓ Applied (${msg.appliedEditCount} operations)" else "✓ 完了 (${msg.appliedEditCount} 箇所をシートに反映)",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (msg.scriptJson.isNotBlank()) {
                                        MasuSheetPillButton(
                                            label = if (isEn) "📋 Copy JSON Script" else "📋 スクリプトコピー",
                                            onClick = {
                                                copyWithFeedback(msg.scriptJson, "実行スクリプト(JSON)", "JSON Script")
                                            }
                                        )
                                    }
                                    if (msg.canUndo && (state.lastAiUndoSteps > 0 || state.lastAiCreatedObjectIds.isNotEmpty())) {
                                        MasuSheetPillButton(
                                            label = if (isEn) "↶ Undo" else "↶ 元に戻す",
                                            onClick = onUndoLastAiEdit
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 7. Pinned Bottom Prompt / Script Input Box (With 1-tap Copy button!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            BasicTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                cursorBrush = SolidColor(Color(0xFF4ADE80)),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp, max = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF1F9D55), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                decorationBox = { innerTextField ->
                    if (promptInput.isEmpty()) {
                        Text(
                            text = if (isEn) {
                                "Enter prompt or JSON script (e.g. 'Create chart', '{\"commands\":[...]}')"
                            } else {
                                "指示またはJSONスクリプトを入力 (例:「グラフ化して」)"
                            },
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                    innerTextField()
                }
            )
            if (promptInput.isNotBlank()) {
                MasuSheetPillButton(
                    label = if (isEn) "📋 Copy" else "📋 コピー",
                    onClick = {
                        copyWithFeedback(promptInput, "入力中のスクリプト/指示", "Typed Script/Prompt")
                    }
                )
            }
            MasuSheetPillButton(
                label = if (isEn) "Send ➤" else "送信 ➤",
                highlight = true,
                tag = "ms_btn_ai_send",
                onClick = {
                    if (promptInput.isNotBlank()) {
                        val toSend = promptInput
                        promptInput = ""
                        onSendPrompt(toSend)
                    }
                }
            )
        }
    }
    }
}
