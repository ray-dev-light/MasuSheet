package com.masusheet.app.ui.ribbon

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masusheet.app.R
import com.masusheet.app.SecondaryWorkbookActivity
import com.masusheet.app.catalog.SpreadsheetChartTypeCatalog
import com.masusheet.app.core.InlineCheckboxHelper
import com.masusheet.app.core.SpreadsheetI18n
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.model.SheetObjectKind
import com.masusheet.app.ui.RIBBON_TABS
import com.masusheet.app.viewmodel.onToggleAiPanel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] 15-Tab Spreadsheet Ribbon UI & Contextual Object Style Bar
 * Implements [MasuSheetFullRibbon] with Material 3 / Classic UI support across Home, Insert, Draw, Page Layout, Formulas, Data, Review, View, Automate, Developer, and contextual tabs.
 *
 * ---
 * [JA / 日本語解説]
 * Material 3 モダンUIとクラシックUIの両モードに対応し、基本10タブ＋コンテキスト5タブ（テーブル・グラフ・ピボット・図形・図）を描画する [MasuSheetFullRibbon] コンポーネントです。
 * ============================================================================
 */

/**
 * [EN] 15-tab horizontal ribbon toolbar supporting both Material 3 modern styling and classic spreadsheet density.
 * [JA] Material 3 モダンUIとクラシックUIの両デザインに対応した全15タブのリボンツールバーComposableです。
 */
@Composable
fun MasuSheetFullRibbon(
    state: MasuSheetUiState,
    surfaceHeader: Color,
    surfaceBar: Color,
    borderCol: Color,
    textPrimary: Color,
    onSelectTab: (String) -> Unit,
    onCommand: (String, String) -> Unit,
    onOpenDocumentClick: () -> Unit,
    onSaveClick: () -> Unit,
    onExportPdfClick: () -> Unit,
    onLoadSampleClick: (String) -> Unit,
    onNextSampleClick: () -> Unit,
    onRepairSampleClick: () -> Unit,
    onInsertCheckboxClick: () -> Unit,
    onInsertTemplateText: (String) -> Unit,
    onAutoFitColClick: () -> Unit,
    onAutoFitRowClick: () -> Unit,
    onResizeColDeltaClick: (Float) -> Unit,
    onResizeRowDeltaClick: (Float) -> Unit,
    onAddSheetClick: () -> Unit,
    onToggleLanguageClick: () -> Unit,
    onToggleThemeClick: () -> Unit,
    onToggleMaterial3UiClick: () -> Unit = {},
    onCycleZoomClick: () -> Unit,
    onToggleToolbarClick: () -> Unit,
    onDismissErrorClick: () -> Unit,
    onCopyClick: () -> Unit = {},
    onCutClick: () -> Unit = {},
    onPasteClick: () -> Unit = {},
    onInsertImageClick: () -> Unit = {},
    onPasteScreenshotClick: () -> Unit = {},
    onInsertShapeClick: (String, String) -> Unit = { _, _ -> },
    onInsertTextBoxClick: () -> Unit = {},
    onInsertChartClick: (String) -> Unit = {},
    onDeleteSelectedObjectClick: () -> Unit = {},
    onConvertToRangeClick: () -> Unit = {},
    onUpdateObjectFillColor: (Int) -> Unit = {},
    onUpdateObjectStrokeColor: (Int) -> Unit = {},
    onUpdateObjectStrokeWidth: (Float) -> Unit = {},
    onRotateObjectBy: (Float) -> Unit = {},
    onResetObjectRotation: () -> Unit = {},
    onResetObjectAspectRatio: () -> Unit = {},
    onOpenZoomDialogClick: () -> Unit = {},
    onEditSelectedObjectInPlace: () -> Unit = {},
    onOpenColorPicker: (String) -> Unit = {},
    onOpenFunctionWizard: () -> Unit = {},
    onOpenMacroStudio: () -> Unit = {},
    onOpenCommandCenter: (String) -> Unit = {},
    onToggleAiPanel: () -> Unit = {},
    onToggleMacroRecording: () -> Unit = {},
    onOpenNewWindowClick: (Boolean) -> Unit = {},
    onToggleSplitBookViewClick: () -> Unit = {},
    onSwitchOpenWorkbookClick: (String) -> Unit = {},
    onCloseOpenWorkbookClick: (String) -> Unit = {},
    onMoveWorkbookToNewWindowClick: (String, Boolean) -> Unit = { _, _ -> },
    isSecondaryWindow: Boolean = false,
    onMergeWorkbookToMainWindowClick: (String) -> Unit = {},
    onNewBlankWorkbookClick: () -> Unit = {},
    onOpenChartSelectDataClick: () -> Unit = {}
) {
    val isEn = state.language == "en"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceBar)
    ) {
        if (state.errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ms_error_banner")
                    .background(Color(0xEE7F1D1D))
                    .clickable(onClick = onDismissErrorClick)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) "⚠️ ${state.errorMessage} (Tap to dismiss)" else "⚠️ ${state.errorMessage} (タップで閉じる)",
                    color = Color(0xFFFEE2E2),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // [EN] Entire Ribbon (Top Action Bar + Open Workbooks Bar + 15-Tab Strip + Command Card) collapses/expands cleanly
        // [JA] リボン全体（上部クイックバー・ブック切替バー・15タブバー・コマンドカード）を一括で表示／非表示切り替え
        AnimatedVisibility(visible = state.toolbarVisible) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: Top Workbook Title Bar + Quick File Actions & AI / Macro / Color Center (Fixed 34.dp height)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("ms_toolbar_row")
                        .background(surfaceHeader)
                        .border(width = 0.5.dp, color = borderCol)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val saveBadge = SpreadsheetI18n.t(if (state.isDirty) "● 未保存変更あり" else "✓ シート反映済", isEn)
                    MasuSheetPillButton(
                        label = "📊 ${state.workbookTitle} ($saveBadge)",
                        highlight = state.activeRibbonTab == "file",
                        tag = "ms_btn_title",
                        onClick = { onSelectTab("file") }
                    )

                    MasuSheetPillButton(
                        label = "▲ リボン隠す",
                        tag = "ms_btn_collapse",
                        onClick = onToggleToolbarClick
                    )

                    MasuSheetPillButton(
                        label = "📄 新規ブック",
                        tag = "ms_btn_new_blank",
                        onClick = onNewBlankWorkbookClick
                    )
                    MasuSheetPillButton(
                        label = "📂 開く",
                        tag = "ms_btn_open",
                        onClick = onOpenDocumentClick
                    )
                    MasuSheetPillButton(
                        label = "🪟 別ウィンドウ",
                        tag = "ms_btn_new_window",
                        onClick = { onOpenNewWindowClick(false) }
                    )
                    if (isSecondaryWindow) {
                        MasuSheetPillButton(
                            label = if (isEn) "📥 Merge into Main Window" else "📥 元のウィンドウへ戻す",
                            highlight = true,
                            tag = "ms_btn_merge_window",
                            onClick = { onMergeWorkbookToMainWindowClick(state.activeWorkbookId) }
                        )
                    }
                    MasuSheetPillButton(
                        label = "💾 ファイル保存",
                        highlight = state.isSaveDialogVisible,
                        tag = "ms_btn_save",
                        onClick = onSaveClick
                    )
                    MasuSheetPillButton(
                        label = "📄 PDF出力",
                        tag = "ms_btn_pdf",
                        onClick = onExportPdfClick
                    )
                    MasuSheetPillButton(
                        label = "↶ 元に戻す",
                        tag = "ms_btn_undo",
                        onClick = { onCommand("edit.undo", "{}") }
                    )
                    MasuSheetPillButton(
                        label = "↷ やり直し",
                        tag = "ms_btn_redo",
                        onClick = { onCommand("edit.redo", "{}") }
                    )
                    MasuSheetPillButton(
                        label = if (state.isAiPanelOpen) "✨ AI アシスタント:ON" else "✨ AI アシスタント",
                        highlight = state.isAiPanelOpen,
                        tag = "ms_btn_ai_toggle",
                        onClick = onToggleAiPanel
                    )
                    MasuSheetPillButton(
                        label = if (state.isMacroRecording) "⏹️ マクロ停止(${state.currentRecordingSteps.size})" else "🤖 マクロ・VBA",
                        highlight = state.isMacroRecording,
                        tag = "ms_btn_macro_studio",
                        onClick = onOpenMacroStudio
                    )
                    MasuSheetPillButton(
                        label = "🎨 フル色パレット",
                        tag = "ms_btn_color_picker",
                        onClick = { onOpenColorPicker("fill") }
                    )
                    MasuSheetPillButton(
                        label = "fx 関数(523)",
                        tag = "ms_btn_fx_wizard",
                        onClick = onOpenFunctionWizard
                    )
                    MasuSheetPillButton(
                        label = "🧭 全292コマンド",
                        tag = "ms_btn_cmd_center",
                        onClick = { onOpenCommandCenter("catalog") }
                    )
                    MasuSheetPillButton(
                        label = if (state.language == "ja") "🇯🇵 日本語" else "🇺🇸 EN",
                        tag = "ms_btn_lang",
                        onClick = onToggleLanguageClick
                    )
                    MasuSheetPillButton(
                        label = if (state.isMaterial3Ui) "🎨 現在:M3 UI (切替)" else "📊 現在:クラシックUI (切替)",
                        highlight = state.isMaterial3Ui,
                        tag = "ms_btn_m3_ui",
                        onClick = onToggleMaterial3UiClick
                    )
                    MasuSheetPillButton(
                        label = if (state.isDarkTheme) "🌙 ダーク" else "☀️ ライト",
                        tag = "ms_btn_theme",
                        onClick = onToggleThemeClick
                    )
                    MasuSheetPillButton(
                        label = "🔍 ${state.zoomPercent}%",
                        tag = "ms_btn_zoom",
                        onClick = onCycleZoomClick
                    )
                }

        // Row 1b: Open Workbooks Switcher Bar (`📚 開いているブック切替バー`) — appears whenever 2+ workbooks are open OR in SecondaryWorkbookActivity.
        // [EN] Supports:
        //      1. Left-click / Tap to switch active workbook.
        //      2. Dragging a workbook tab (`hypot > 32f`) to detach into a new window OR (in SecondaryWorkbookActivity) merge back into the main window.
        //      3. Right-click or long-press on a workbook tab to open a context menu ("🪟 Move to New Window" / "📥 Merge Back into Main Window").
        // [JA] ブックタブのクリック切替に加え、タブを掴んでドラッグするか、右クリック（または長押し）メニューから
        //      「🪟 別のウィンドウへ移動」または（別ウィンドウ側では）「📥 元のウィンドウへ戻す（1つに統合）」を実行できます。
        if (state.openWorkbooks.size > 1 || isSecondaryWindow) {
            val latestMergeToMain by rememberUpdatedState(onMergeWorkbookToMainWindowClick)
            val latestMoveToNew by rememberUpdatedState(onMoveWorkbookToNewWindowClick)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(if (state.isDarkTheme) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                    .border(width = 0.5.dp, color = borderCol)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) "📚 Open Workbooks (${state.openWorkbooks.size}):" else "📚 開いているブック (${state.openWorkbooks.size}件):",
                    color = if (state.isDarkTheme) Color(0xFF4ADE80) else Color(0xFF15803D),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                state.openWorkbooks.forEach { wb ->
                    val isCurrent = wb.id == state.activeWorkbookId
                    var tabMenuExpanded by remember(wb.id) { mutableStateOf(false) }
                    var dragAccumX by remember(wb.id) { mutableStateOf(0f) }
                    var dragAccumY by remember(wb.id) { mutableStateOf(0f) }
                    val dragDist = hypot(dragAccumX, dragAccumY)
                    val isReadyToDetach = dragDist > 32f

                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (dragAccumX * 0.35f).roundToInt().coerceIn(-36, 36),
                                        (dragAccumY * 0.35f).roundToInt().coerceIn(-12, 16)
                                    )
                                }
                                .pointerInput(wb.id) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                                event.changes.forEach { it.consume() }
                                                tabMenuExpanded = true
                                            }
                                        }
                                    }
                                }
                                .pointerInput(wb.id, isSecondaryWindow) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragAccumX = 0f
                                            dragAccumY = 0f
                                        },
                                        onDragCancel = {
                                            dragAccumX = 0f
                                            dragAccumY = 0f
                                        },
                                        onDragEnd = {
                                            val totalDist = hypot(dragAccumX, dragAccumY)
                                            dragAccumX = 0f
                                            dragAccumY = 0f
                                            if (totalDist > 32f) {
                                                if (isSecondaryWindow) {
                                                    latestMergeToMain(wb.id)
                                                } else {
                                                    latestMoveToNew(wb.id, true)
                                                }
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragAccumX += dragAmount.x
                                            dragAccumY += dragAmount.y
                                        }
                                    )
                                }
                        ) {
                            val tabLabel = when {
                                isReadyToDetach && isSecondaryWindow -> if (isEn) {
                                    "📥 Release -> Merge into Main Window (${wb.title})"
                                } else {
                                    "📥 離すと元のウィンドウへ戻す (${wb.title})"
                                }
                                isReadyToDetach -> if (isEn) {
                                    "🪟 Release -> New Window (${wb.title})"
                                } else {
                                    "🪟 離すと別ウィンドウへ移動 (${wb.title})"
                                }
                                isCurrent -> if (isEn) "📗 ${wb.title} (Active)" else "📗 ${wb.title} (表示中)"
                                else -> "📘 ${wb.title}"
                            }
                            MasuSheetPillButton(
                                label = tabLabel,
                                highlight = isCurrent || isReadyToDetach,
                                onClick = { onSwitchOpenWorkbookClick(wb.id) },
                                onLongClick = { tabMenuExpanded = true }
                            )
                            if (isSecondaryWindow) {
                                MasuSheetPillButton(
                                    label = if (isEn) "📥 Merge to Main" else "📥 元のウィンドウへ戻す",
                                    highlight = true,
                                    onClick = { onMergeWorkbookToMainWindowClick(wb.id) },
                                    onLongClick = { tabMenuExpanded = true }
                                )
                            } else {
                                MasuSheetPillButton(
                                    label = "🪟",
                                    onClick = { onMoveWorkbookToNewWindowClick(wb.id, true) },
                                    onLongClick = { tabMenuExpanded = true }
                                )
                            }
                            if (state.openWorkbooks.size > 1) {
                                MasuSheetPillButton(
                                    label = "✕",
                                    onClick = { onCloseOpenWorkbookClick(wb.id) }
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = tabMenuExpanded,
                            onDismissRequest = { tabMenuExpanded = false }
                        ) {
                            if (isSecondaryWindow) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isEn) "📥 Merge Back into Main Window" else "📥 元のウィンドウへ戻す（1つに統合）",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    onClick = {
                                        tabMenuExpanded = false
                                        onMergeWorkbookToMainWindowClick(wb.id)
                                    }
                                )
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isEn) "🪟 Move to New Window" else "🪟 別のウィンドウへ移動",
                                        fontSize = 12.5.sp,
                                        fontWeight = if (!isSecondaryWindow) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    tabMenuExpanded = false
                                    onMoveWorkbookToNewWindowClick(wb.id, true)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isEn) "📑 Open in New Window (Keep Tab)" else "📑 別のウィンドウで開く (タブを残す)",
                                        fontSize = 12.sp
                                    )
                                },
                                onClick = {
                                    tabMenuExpanded = false
                                    onMoveWorkbookToNewWindowClick(wb.id, false)
                                }
                            )
                            if (!isCurrent) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isEn) "📗 Switch to This Workbook" else "📗 このブックを表示",
                                            fontSize = 12.sp
                                        )
                                    },
                                    onClick = {
                                        tabMenuExpanded = false
                                        onSwitchOpenWorkbookClick(wb.id)
                                    }
                                )
                            }
                            if (state.openWorkbooks.size > 1) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isEn) "✕ Close This Tab" else "✕ このタブを閉じる",
                                            fontSize = 12.sp
                                        )
                                    },
                                    onClick = {
                                        tabMenuExpanded = false
                                        onCloseOpenWorkbookClick(wb.id)
                                    }
                                )
                            }
                        }
                    }
                }
                Text(
                    text = if (isSecondaryWindow) {
                        if (isEn) "💡 Drag tab or click 📥 -> Merge into Main Window" else "💡 タブをドラッグ／右クリック／[📥 元のウィンドウへ戻す]で統合"
                    } else {
                        if (isEn) "💡 Drag or right-click tab -> New Window" else "💡 タブをドラッグ／右クリックで別ウィンドウへ移動"
                    },
                    color = if (state.isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569),
                    fontSize = 10.sp
                )
                MasuSheetPillButton(
                    label = if (isEn) "＋ New Blank Book" else "＋ 新規空白ブック",
                    onClick = onNewBlankWorkbookClick
                )
            }
        }

        // Row 2: Authentic Spreadsheet Ribbon Category Tabs (Clean text tabs with #107C41 green bottom underline bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(surfaceHeader)
                .border(width = 0.5.dp, color = borderCol)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (tab in RIBBON_TABS) {
                val selected = state.activeRibbonTab == tab.id
                val isContextualHighlight = (tab.id == "objectFormat" || tab.id == "chartDesign") && state.selectedObjectId != null
                val accentGreen = if (state.isDarkTheme) Color(0xFF34D399) else Color(0xFF107C41)
                val tabFg = when {
                    selected -> if (state.isDarkTheme) Color(0xFFFFFFFF) else Color(0xFF107C41)
                    isContextualHighlight -> accentGreen
                    else -> textPrimary.copy(alpha = 0.85f)
                }
                Column(
                    modifier = Modifier
                        .testTag("ms_ribbon_tab_${tab.id}")
                        .fillMaxHeight()
                        .clickable { onSelectTab(tab.id) }
                        .padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEn) tab.labelEn else tab.labelJa,
                        color = tabFg,
                        fontSize = 12.sp,
                        fontWeight = if (selected || isContextualHighlight) FontWeight.Bold else FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .width(if (selected) 36.dp else if (isContextualHighlight) 24.dp else 0.dp)
                            .height(2.5.dp)
                            .background(if (selected || isContextualHighlight) accentGreen else Color.Transparent, RoundedCornerShape(1.5.dp))
                    )
                }
            }
        }

        // Row 3: Classic Grouped Multi-Row Ribbon Card (heightIn(min = 82.dp) — Zero vertical clipping!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceHeader)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 82.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceBar)
                        .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeCell = state.viewport.cellAt(state.viewport.activeRow, state.viewport.activeCol)
                    val activeMerge = state.viewport.mergeAt(state.viewport.activeRow, state.viewport.activeCol)
                    val selectedObj = state.sheetObjects.firstOrNull { it.id == state.selectedObjectId }
                    val activeObj = selectedObj
                        ?: state.sheetObjects.lastOrNull { it.sheetIndex == state.viewport.sheetIndex }

                    val rotateMenuItems = listOf(
                        SpreadsheetDropdownActionItem("🔄 オブジェクトの回転 (ドラッグでも360°回転可)", isHeader = true),
                        SpreadsheetDropdownActionItem("⟳ 右へ 90° 回転 (+90°)") { onRotateObjectBy(90f) },
                        SpreadsheetDropdownActionItem("⟲ 左へ 90° 回転 (-90°)") { onRotateObjectBy(-90f) },
                        SpreadsheetDropdownActionItem("↗ 右へ 15° 回転 (+15°)") { onRotateObjectBy(15f) },
                        SpreadsheetDropdownActionItem("↖ 左へ 15° 回転 (-15°)") { onRotateObjectBy(-15f) },
                        SpreadsheetDropdownActionItem("↕ 上下反転 / 180° 回転") { onRotateObjectBy(180f) },
                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                        SpreadsheetDropdownActionItem("↺ 回転を 0° にリセット") { onResetObjectRotation() }
                    )

                    when (state.activeRibbonTab) {
                        "file" -> {
                            SpreadsheetRibbonGroup(title = "ブック操作・ウィンドウ") {
                                SpreadsheetTallRibbonButton("📄", "📄 新規空白ブック", onClick = onNewBlankWorkbookClick)
                                SpreadsheetTallRibbonButton("📂", "📂 ファイルを開く (.xlsx/.csv/.ods)", onClick = onOpenDocumentClick)
                                SpreadsheetTallRibbonButton("🪟", "🪟 別ウィンドウを起動 (2画面独立)", highlight = true, onClick = { onOpenNewWindowClick(false) })
                                SpreadsheetTallRibbonButton("💾", "💾 名前を付けて保存 (拡張子選択・既存名反映)", onClick = onSaveClick)
                                SpreadsheetTallRibbonButton("📕", "📄 PDFとして出力 (.pdf)", onClick = onExportPdfClick)
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "エクスポート") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("📊 XLSXエクスポート", onClick = { onCommand("file.exportXlsx", "{}") })
                                        MasuSheetPillButton("📋 CSVエクスポート", onClick = { onCommand("file.exportCsv", "{}") })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("📋 TSVエクスポート", onClick = { onCommand("file.exportTsv", "{}") })
                                        MasuSheetPillButton("📋 ODSエクスポート", onClick = { onCommand("file.exportOds", "{}") })
                                    }
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "保護・シート") {
                                SpreadsheetRibbonCheckbox("🔒 ブックの保護", checked = state.isWorkbookProtected, onClick = { onCommand("file.protectWorkbook", "{}") })
                                SpreadsheetTallRibbonButton("➕", "➕ 新しいシートを追加", onClick = onAddSheetClick)
                                SpreadsheetTallRibbonButton("📜", "📜 OSSライセンス・このアプリについて", onClick = { onCommand("app.aboutLicenses", "{}") })
                            }
                        }

                        "insert" -> {
                            SpreadsheetRibbonGroup(title = "テーブル") {
                                SpreadsheetTallRibbonButton("📊", "📊 ピボットテーブル作成", onClick = { onCommand("insert.pivotTable", "{}") })
                                SpreadsheetTallRibbonButton("📅", "📊 テーブル作成", highlight = state.isTableActive, onClick = { onCommand("insert.table", "{}") })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "図・画像・図形") {
                                SpreadsheetTallRibbonButton(
                                    icon = "🖼️",
                                    label = "🖼️ 画像ファイル挿入",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("🖼️ 画像の挿入", isHeader = true),
                                        SpreadsheetDropdownActionItem("📱 このデバイスから画像を挿入...") { onInsertImageClick() },
                                        SpreadsheetDropdownActionItem("📋 クリップボード・最新スクショから画像を貼付") { onPasteScreenshotClick() }
                                    ),
                                    onClick = onInsertImageClick
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "📸",
                                    label = "📸 スクショ・クリップボード画像を貼付",
                                    onClick = onPasteScreenshotClick
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "⬡",
                                    label = "⬡ 図形一覧",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("⬡ 図形ギャラリー (挿入後 ⟳ ハンドルで回転可)", isHeader = true),
                                        SpreadsheetDropdownActionItem(
                                            label = "📏 線 (Lines)",
                                            subItems = listOf(
                                                "╱ 直線 (Line)" to { onInsertShapeClick("line", "") },
                                                "➡️ 矢印 (Arrow)" to { onInsertShapeClick("arrow", "") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(
                                            label = "▭ 四角形 (Rectangles)",
                                            subItems = listOf(
                                                "▭ 四角形 (Rectangle)" to { onInsertShapeClick("rectangle", "") },
                                                "▢ 角丸四角形 (Rounded Rectangle)" to { onInsertShapeClick("roundRect", "") },
                                                "◇ ひし形 (Diamond)" to { onInsertShapeClick("diamond", "") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(
                                            label = "◯ 基本図形 (Basic Shapes)",
                                            subItems = listOf(
                                                "◯ 円・楕円 (Oval)" to { onInsertShapeClick("ellipse", "") },
                                                "△ 二等辺三角形 (Triangle)" to { onInsertShapeClick("triangle", "") },
                                                "⬠ 五角形 (Pentagon)" to { onInsertShapeClick("pentagon", "") },
                                                "⬡ 六角形 (Hexagon)" to { onInsertShapeClick("hexagon", "") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(
                                            label = "➡️ ブロック矢印 (Block Arrows)",
                                            subItems = listOf(
                                                "➡️ 右矢印 (Right Arrow)" to { onInsertShapeClick("arrow", "") },
                                                "⬅️ 左矢印 (Left Arrow)" to { onInsertShapeClick("arrow_left", "") },
                                                "⬆️ 上矢印 (Up Arrow)" to { onInsertShapeClick("arrow_up", "") },
                                                "⬇️ 下矢印 (Down Arrow)" to { onInsertShapeClick("arrow_down", "") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(
                                            label = "⭐ 星とリボン・吹き出し",
                                            subItems = listOf(
                                                "⭐ 星 (5-Point Star)" to { onInsertShapeClick("star", "") },
                                                "💬 四角形吹き出し (Callout)" to { onInsertShapeClick("callout", "") }
                                            )
                                        )
                                    ),
                                    onClick = { onInsertShapeClick("rectangle", "") }
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("▭ 四角形", onClick = { onInsertShapeClick("rectangle", "") })
                                        MasuSheetPillButton("▢ 角丸四角形", onClick = { onInsertShapeClick("roundRect", "") })
                                        MasuSheetPillButton("◯ 円・楕円", onClick = { onInsertShapeClick("ellipse", "") })
                                        MasuSheetPillButton("◇ ひし形", onClick = { onInsertShapeClick("diamond", "") })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("△ 三角形", onClick = { onInsertShapeClick("triangle", "") })
                                        MasuSheetPillButton("➡️ 矢印", onClick = { onInsertShapeClick("arrow", "") })
                                        MasuSheetPillButton("╱ 直線", onClick = { onInsertShapeClick("line", "") })
                                        MasuSheetPillButton("⭐ 星形", onClick = { onInsertShapeClick("star", "") })
                                    }
                                }
                                SpreadsheetTallRibbonButton(
                                    icon = "🔄",
                                    label = if (selectedObj != null && selectedObj.rotationDeg != 0f) "🔄 回転 (${selectedObj.rotationDeg.roundToInt()}°)" else "🔄 図形を回転",
                                    hasDropdown = true,
                                    highlight = selectedObj != null && selectedObj.rotationDeg != 0f,
                                    menuItems = rotateMenuItems,
                                    onClick = { onRotateObjectBy(90f) }
                                )
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "グラフ") {
                                SpreadsheetTallRibbonButton(
                                    icon = "📊",
                                    label = "📊 おすすめグラフ / 全32種",
                                    highlight = state.isPivotChartPickerOpen,
                                    onClick = { onCommand("insert.recommendedCharts", "{}") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "📊",
                                    label = "📊 縦棒グラフ (表から作成)",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("📊 2-D 縦棒 / 横棒グラフ", isHeader = true),
                                        SpreadsheetDropdownActionItem("📊 集合縦棒 (Clustered Column)") { onInsertChartClick("column") },
                                        SpreadsheetDropdownActionItem("📊 積み上げ縦棒 (Stacked Column)") { onInsertChartClick("column_stacked") },
                                        SpreadsheetDropdownActionItem("📊 100% 積み上げ縦棒 (100% Stacked)") { onInsertChartClick("column_stacked100") },
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("📊 集合横棒 (Clustered Bar)") { onInsertChartClick("bar_h") },
                                        SpreadsheetDropdownActionItem("📊 積み上げ横棒 (Stacked Bar)") { onInsertChartClick("bar_stacked") },
                                        SpreadsheetDropdownActionItem("📊 100% 積み上げ横棒 (100% Stacked Bar)") { onInsertChartClick("bar_stacked100") },
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("📊 その他の縦棒グラフ (全32種)...") { onCommand("insert.recommendedCharts", "{}") }
                                    ),
                                    onClick = { onInsertChartClick("column") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "📈",
                                    label = "📈 折れ線グラフ (表から作成)",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("📈 折れ線 / 面グラフ", isHeader = true),
                                        SpreadsheetDropdownActionItem("📈 折れ線 (Line)") { onInsertChartClick("line") },
                                        SpreadsheetDropdownActionItem("📈 マーカー付き折れ線 (Line with Markers)") { onInsertChartClick("line_markers") },
                                        SpreadsheetDropdownActionItem("📈 積み上げ折れ線 (Stacked Line)") { onInsertChartClick("line_stacked") },
                                        SpreadsheetDropdownActionItem("📈 100% 積み上げ折れ線 (100% Stacked)") { onInsertChartClick("line_stacked100") },
                                        SpreadsheetDropdownActionItem("〰️ 平滑線 (Smooth Line)") { onInsertChartClick("line_smooth") },
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("⛰️ 面 (Area)") { onInsertChartClick("area") },
                                        SpreadsheetDropdownActionItem("⛰️ 積み上げ面 (Stacked Area)") { onInsertChartClick("area_stacked") },
                                        SpreadsheetDropdownActionItem("⛰️ 100% 積み上げ面 (100% Stacked Area)") { onInsertChartClick("area_stacked100") }
                                    ),
                                    onClick = { onInsertChartClick("line") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "🥧",
                                    label = "🥧 円グラフ (表から作成)",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("🥧 円 / ドーナツ / 階層グラフ", isHeader = true),
                                        SpreadsheetDropdownActionItem("🥧 2-D 円 (Pie)") { onInsertChartClick("pie") },
                                        SpreadsheetDropdownActionItem("🥧 分割円 (Exploded Pie)") { onInsertChartClick("pie_exploded") },
                                        SpreadsheetDropdownActionItem("🍩 ドーナツ (Doughnut)") { onInsertChartClick("doughnut") },
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("🟩 ツリーマップ (Treemap)") { onInsertChartClick("treemap") },
                                        SpreadsheetDropdownActionItem("☀️ サンバースト (Sunburst)") { onInsertChartClick("sunburst") }
                                    ),
                                    onClick = { onInsertChartClick("pie") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "📊",
                                    label = "📊 横棒グラフ (表から作成)",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("📊 横棒・統計・高度グラフ", isHeader = true),
                                        SpreadsheetDropdownActionItem("📊 集合横棒 (Clustered Bar)") { onInsertChartClick("bar_h") },
                                        SpreadsheetDropdownActionItem("📊 積み上げ横棒 (Stacked Bar)") { onInsertChartClick("bar_stacked") },
                                        SpreadsheetDropdownActionItem("📊 ヒストグラム (Histogram)") { onInsertChartClick("histogram") },
                                        SpreadsheetDropdownActionItem("📉 パレート図 (Pareto)") { onInsertChartClick("pareto") },
                                        SpreadsheetDropdownActionItem("📦 箱ひげ図 (Box & Whisker)") { onInsertChartClick("box_whisker") },
                                        SpreadsheetDropdownActionItem("🌊 ウォーターフォール (Waterfall)") { onInsertChartClick("waterfall") },
                                        SpreadsheetDropdownActionItem("🔻 じょうご (Funnel)") { onInsertChartClick("funnel") },
                                        SpreadsheetDropdownActionItem("🕯️ 株価 (Stock HLC)") { onInsertChartClick("stock") },
                                        SpreadsheetDropdownActionItem("🕸️ レーダー (Radar)") { onInsertChartClick("radar_markers") },
                                        SpreadsheetDropdownActionItem("🫧 散布図 / バブル (Scatter / Bubble)") { onInsertChartClick("scatter") },
                                        SpreadsheetDropdownActionItem("📊 複合グラフ (Combo: 縦棒+折れ線)") { onInsertChartClick("combo") },
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("📊 全32種類のグラフ一覧を開く...") { onCommand("insert.recommendedCharts", "{}") }
                                    ),
                                    onClick = { onInsertChartClick("bar") }
                                )
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "スパークライン・テキスト") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("📈 スパークライン(折れ線)", onClick = { onCommand("insert.sparklineLine", "{}") })
                                        MasuSheetPillButton("📊 スパークライン(縦棒)", onClick = { onCommand("insert.sparklineColumn", "{}") })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("📝 テキストボックス", onClick = onInsertTextBoxClick)
                                        MasuSheetPillButton(
                                            label = "☑ チェックボックス切替/挿入",
                                            highlight = activeCell != null && InlineCheckboxHelper.containsCheckbox(activeCell.displayText),
                                            tag = "ms_btn_checkbox_toggle",
                                            onClick = onInsertCheckboxClick
                                        )
                                    }
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "リンク・テンプレート") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("🔗 ハイパーリンク", onClick = { onOpenCommandCenter("hyperlink") })
                                        MasuSheetPillButton("💬 コメント挿入", onClick = { onOpenCommandCenter("comment") })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("📆 年月日テンプレート", onClick = { onInsertTemplateText("2026年  10月  31日") })
                                        MasuSheetPillButton("📧 メール (@) テンプレート", onClick = { onInsertTemplateText("          @          ") })
                                    }
                                }
                            }
                        }

                        "draw" -> {
                            SpreadsheetRibbonGroup(title = "描画ツール") {
                                SpreadsheetTallRibbonButton("🖊️", "🖊️ 黒ペン描画", onClick = { onInsertShapeClick("line", "") })
                                SpreadsheetTallRibbonButton("🖍️", "🖍️ 赤ペン・矢印", onClick = { onInsertShapeClick("arrow", "") })
                                SpreadsheetTallRibbonButton("🟨", "🟨 蛍光ペン枠", onClick = {
                                    onInsertShapeClick("rectangle", "")
                                    onUpdateObjectFillColor(0x66FEF08A)
                                })
                                SpreadsheetTallRibbonButton("◯", "◯ 赤丸囲み", onClick = {
                                    onInsertShapeClick("ellipse", "")
                                    onUpdateObjectFillColor(0)
                                    onUpdateObjectStrokeColor(0xFFDC2626.toInt())
                                    onUpdateObjectStrokeWidth(4.0f)
                                })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "ペンの色・太さ・回転") {
                                SpreadsheetTallRibbonButton("🎨", "🎨 描画色パレット (RGB/テーマ色)", onClick = { onOpenColorPicker("objectStroke") })
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("━━ 線の太さ:細 (1pt)", highlight = selectedObj != null && abs(selectedObj.strokeWidthPt - 1.0f) < 0.35f, onClick = { onUpdateObjectStrokeWidth(1.0f) })
                                        MasuSheetPillButton("━━ 線の太さ:中 (2.5pt)", highlight = selectedObj != null && abs(selectedObj.strokeWidthPt - 2.5f) < 0.6f, onClick = { onUpdateObjectStrokeWidth(2.5f) })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("━━ 線の太さ:太 (4.5pt)", highlight = selectedObj != null && selectedObj.strokeWidthPt >= 3.8f, onClick = { onUpdateObjectStrokeWidth(4.5f) })
                                        MasuSheetPillButton("🧹 選択した描画を消去", onClick = onDeleteSelectedObjectClick)
                                    }
                                }
                                SpreadsheetTallRibbonButton(
                                    icon = "🔄",
                                    label = "🔄 描画を回転",
                                    hasDropdown = true,
                                    menuItems = rotateMenuItems,
                                    onClick = { onRotateObjectBy(90f) }
                                )
                            }
                        }

                        "objectFormat" -> {
                            SpreadsheetRibbonGroup(title = "図形のスタイル") {
                                SpreadsheetTallRibbonButton("🎨", "🎨 中の色パレット (全60色+RGB)", onClick = { onOpenColorPicker("objectFill") })
                                SpreadsheetTallRibbonButton("🖊️", "🖊️ 枠線の色パレット (全60色+RGB)", onClick = { onOpenColorPicker("objectStroke") })
                                SpreadsheetTallRibbonButton("🔤", "🔤 図形内の文字色パレット", onClick = { onOpenColorPicker("objectText") })
                                SpreadsheetRibbonCheckbox("🚫 中の色を抜く(透明)", checked = activeObj?.fillColor == 0, onClick = { onUpdateObjectFillColor(0) })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "クイック色・枠線の太さ") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("🟢 緑", highlight = selectedObj?.fillColor == 0xFF1F9D55.toInt(), onClick = { onUpdateObjectFillColor(0xFF1F9D55.toInt()) })
                                        MasuSheetPillButton("🔵 青", highlight = selectedObj?.fillColor == 0xFF2563EB.toInt(), onClick = { onUpdateObjectFillColor(0xFF2563EB.toInt()) })
                                        MasuSheetPillButton("🔴 赤", highlight = selectedObj?.fillColor == 0xFFDC2626.toInt(), onClick = { onUpdateObjectFillColor(0xFFDC2626.toInt()) })
                                        MasuSheetPillButton("🟠 橙", highlight = selectedObj?.fillColor == 0xFFD97706.toInt(), onClick = { onUpdateObjectFillColor(0xFFD97706.toInt()) })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("━ 細線(1pt)", highlight = selectedObj != null && abs(selectedObj.strokeWidthPt - 1.0f) < 0.3f, onClick = { onUpdateObjectStrokeWidth(1.0f) })
                                        MasuSheetPillButton("━ 標準(2pt)", highlight = selectedObj != null && abs(selectedObj.strokeWidthPt - 2.0f) < 0.3f, onClick = { onUpdateObjectStrokeWidth(2.0f) })
                                        MasuSheetPillButton("━━ 太線(4pt)", highlight = selectedObj != null && abs(selectedObj.strokeWidthPt - 4.0f) < 0.5f, onClick = { onUpdateObjectStrokeWidth(4.0f) })
                                        MasuSheetPillButton("🚫 枠なし(0pt)", highlight = selectedObj != null && selectedObj.strokeWidthPt <= 0.05f, onClick = { onUpdateObjectStrokeWidth(0f) })
                                    }
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "配置・回転") {
                                SpreadsheetTallRibbonButton(
                                    icon = "🔄",
                                    label = if (activeObj != null) "🔄 回転 (${activeObj.rotationDeg.roundToInt()}°)" else "🔄 回転",
                                    hasDropdown = true,
                                    highlight = activeObj != null && activeObj.rotationDeg != 0f,
                                    tag = "ms_btn_object_rotate_menu",
                                    menuItems = rotateMenuItems,
                                    onClick = { onRotateObjectBy(90f) }
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("⟳ 右90°", tag = "ms_btn_rotate_right_90", onClick = { onRotateObjectBy(90f) })
                                        MasuSheetPillButton("⟲ 左90°", tag = "ms_btn_rotate_left_90", onClick = { onRotateObjectBy(-90f) })
                                        MasuSheetPillButton("↕ 180°", onClick = { onRotateObjectBy(180f) })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("↗ +15°", onClick = { onRotateObjectBy(15f) })
                                        MasuSheetPillButton("↖ -15°", onClick = { onRotateObjectBy(-15f) })
                                        MasuSheetPillButton("↺ 0°リセット", tag = "ms_btn_rotate_reset", highlight = activeObj != null && activeObj.rotationDeg != 0f, onClick = onResetObjectRotation)
                                    }
                                }
                            }
                            if (activeObj != null) {
                                SpreadsheetRibbonGroupDivider()
                                SpreadsheetRibbonGroup(title = "編集・削除") {
                                    if (activeObj.kind == SheetObjectKind.IMAGE) {
                                        SpreadsheetTallRibbonButton(
                                            icon = "📐",
                                            label = "📐 元の縦横比にリセット",
                                            tag = "ms_btn_reset_image_aspect",
                                            onClick = onResetObjectAspectRatio
                                        )
                                    } else {
                                        SpreadsheetTallRibbonButton("✏️", "✏️ 文字を直接編集", onClick = onEditSelectedObjectInPlace)
                                    }
                                    SpreadsheetTallRibbonButton("🗑️", "🗑️ 削除", onClick = onDeleteSelectedObjectClick)
                                }
                            }
                        }

                        "pageLayout" -> {
                            SpreadsheetRibbonGroup(title = "テーマ") {
                                SpreadsheetTallRibbonButton("🎨", "🎨 テーマ配色変更 (標準 / 緑 / 青 / 暖色)", onClick = { onOpenColorPicker("fill") })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "ページ設定") {
                                SpreadsheetTallRibbonButton("📄", "📄 縦向き (Portrait)", highlight = state.pageOrientation == "portrait", onClick = { onCommand("pageLayout.orientation", "{\"orientation\":\"portrait\"}") })
                                SpreadsheetTallRibbonButton("📃", "📄 横向き (Landscape)", highlight = state.pageOrientation == "landscape", onClick = { onCommand("pageLayout.orientation", "{\"orientation\":\"landscape\"}") })
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("📐 用紙: A4", highlight = state.paperSize == "A4", onClick = { onCommand("pageLayout.size", "{\"paper\":\"A4\"}") })
                                        MasuSheetPillButton("📐 用紙: Letter", highlight = state.paperSize == "Letter", onClick = { onCommand("pageLayout.size", "{\"paper\":\"Letter\"}") })
                                        MasuSheetPillButton("📐 用紙: A3", highlight = state.paperSize == "A3", onClick = { onCommand("pageLayout.size", "{\"paper\":\"A3\"}") })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("📏 余白: 標準", highlight = state.pageMargins == "normal", onClick = { onCommand("pageLayout.margins", "{\"preset\":\"normal\"}") })
                                        MasuSheetPillButton("📏 余白: 狭い", highlight = state.pageMargins == "narrow", onClick = { onCommand("pageLayout.margins", "{\"preset\":\"narrow\"}") })
                                        MasuSheetPillButton("📏 余白: 広い", highlight = state.pageMargins == "wide", onClick = { onCommand("pageLayout.margins", "{\"preset\":\"wide\"}") })
                                    }
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "シートのオプション") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    SpreadsheetRibbonCheckbox("▦ 枠線の表示/印刷", checked = state.showGridlines, onClick = { onCommand("pageLayout.sheetOptions", "{\"gridlines\":true}") })
                                    SpreadsheetRibbonCheckbox("1️⃣ 1ページに収める", checked = state.scaleToFitOnePage, onClick = { onCommand("pageLayout.scaleToFit", "{\"width\":1,\"height\":1}") })
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "配置・印刷") {
                                SpreadsheetTallRibbonButton("🔲", "🔲 印刷範囲を設定", highlight = state.hasPrintArea, onClick = { onCommand("pageLayout.printArea", "{}") })
                                SpreadsheetTallRibbonButton("➕", "➕ 改ページ挿入", highlight = state.hasPageBreak, onClick = { onCommand("pageLayout.breaks", "{\"action\":\"insert\"}") })
                                SpreadsheetTallRibbonButton("📄", "📄 PDF出力", onClick = onExportPdfClick)
                                SpreadsheetTallRibbonButton(
                                    icon = "🔄",
                                    label = "🔄 オブジェクトの回転",
                                    hasDropdown = true,
                                    menuItems = rotateMenuItems,
                                    onClick = { onRotateObjectBy(90f) }
                                )
                            }
                        }

                        "formulas" -> {
                            SpreadsheetRibbonGroup(title = "関数ライブラリ") {
                                SpreadsheetTallRibbonButton("fx", "fx 関数の挿入 (全523関数・14カテゴリ)", onClick = onOpenFunctionWizard)
                                SpreadsheetTallRibbonButton(
                                    icon = "Σ",
                                    label = "Σ オートSUM (合計)",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("Σ オートSUM 関数", isHeader = true),
                                        SpreadsheetDropdownActionItem("Σ 合計 (SUM)") { onCommand("formulas.autoSum", "{\"function\":\"SUM\"}") },
                                        SpreadsheetDropdownActionItem("x̄ 平均 (AVERAGE)") { onCommand("formulas.autoSum", "{\"function\":\"AVERAGE\"}") },
                                        SpreadsheetDropdownActionItem("# 数値の個数 (COUNT)") { onCommand("formulas.autoSum", "{\"function\":\"COUNT\"}") },
                                        SpreadsheetDropdownActionItem("⬆️ 最大値 (MAX)") { onCommand("formulas.autoSum", "{\"function\":\"MAX\"}") },
                                        SpreadsheetDropdownActionItem("⬇️ 最小値 (MIN)") { onCommand("formulas.autoSum", "{\"function\":\"MIN\"}") },
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("fx その他の関数 (全523関数)...") { onOpenFunctionWizard() }
                                    ),
                                    onClick = { onCommand("formulas.autoSum", "{\"function\":\"SUM\"}") }
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("x̄ AVERAGE (平均)", onClick = { onCommand("formulas.autoSum", "{\"function\":\"AVERAGE\"}") })
                                        MasuSheetPillButton("# COUNT (数値個数)", onClick = { onCommand("formulas.autoSum", "{\"function\":\"COUNT\"}") })
                                        MasuSheetPillButton("⬆️ MAX (最大)", onClick = { onCommand("formulas.autoSum", "{\"function\":\"MAX\"}") })
                                        MasuSheetPillButton("⬇️ MIN (最小)", onClick = { onCommand("formulas.autoSum", "{\"function\":\"MIN\"}") })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("❓ IF関数", onClick = { onInsertTemplateText("=IF(A1>0, \"OK\", \"NG\")") })
                                        MasuSheetPillButton("🔍 XLOOKUP", onClick = { onInsertTemplateText("=XLOOKUP(A2, A2:A20, B2:B20, \"未登録\")") })
                                        MasuSheetPillButton("🔍 VLOOKUP", onClick = { onInsertTemplateText("=VLOOKUP(A2, A1:D20, 2, FALSE)") })
                                        MasuSheetPillButton("💰 PMT(ローン計算)", onClick = { onInsertTemplateText("=PMT(0.02/12, 360, -30000000)") })
                                    }
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "定義された名前") {
                                SpreadsheetTallRibbonButton("🏷️", "🏷️ 名前の管理・定義", onClick = { onOpenCommandCenter("nameManager") })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "ワークシートの分析") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    SpreadsheetRibbonCheckbox("📐 数式の表示切替", checked = state.showFormulas, tag = "ms_btn_show_formulas", onClick = { onCommand("formulas.showFormulas", "{}") })
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("🔍 参照元のトレース", highlight = state.tracePrecedentsActive, onClick = { onCommand("formulas.tracePrecedents", "{}") })
                                        MasuSheetPillButton("🔍 参照先のトレース", highlight = state.traceDependentsActive, onClick = { onCommand("formulas.traceDependents", "{}") })
                                        MasuSheetPillButton("🚫 トレース矢印の削除", onClick = { onCommand("formulas.removeArrows", "{}") })
                                    }
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "計算方法") {
                                SpreadsheetTallRibbonButton("🔄", "🔄 今すぐ再計算 (F9)", onClick = { onCommand("formulas.calculateNow", "{}") })
                            }
                        }

                        "data" -> {
                            SpreadsheetRibbonGroup(title = "並べ替えとフィルター") {
                                SpreadsheetTallRibbonButton("🔼", "🔼 昇順並べ替え (A→Z)", highlight = state.activeSortOrder == "asc", onClick = { onCommand("data.sortAscending", "{}") })
                                SpreadsheetTallRibbonButton("🔽", "🔽 降順並べ替え (Z→A)", highlight = state.activeSortOrder == "desc", onClick = { onCommand("data.sortDescending", "{}") })
                                SpreadsheetTallRibbonButton("🔽", "🔽 オートフィルター切替", highlight = state.isAutoFilterActive, tag = "ms_btn_autofilter", onClick = { onCommand("data.filter", "{}") })
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    MasuSheetPillButton("🚫 フィルタークリア", onClick = { onCommand("data.clearFilter", "{}") })
                                    MasuSheetPillButton("🗂️ 複数条件並べ替え", onClick = { onCommand("data.sortCustom", "{}") })
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "データ ツール") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("✂️ 区切り位置 (分割)", onClick = { onCommand("data.textToColumns", "{\"delimiter\":\",\"}") })
                                        MasuSheetPillButton("⚡ フラッシュフィル", onClick = { onCommand("data.flashFill", "{}") })
                                        MasuSheetPillButton("🧹 重複の削除", onClick = { onCommand("data.removeDuplicates", "{}") })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("✅ データの入力規則", onClick = { onCommand("data.dataValidation", "{\"type\":\"list\"}") })
                                        MasuSheetPillButton("🧮 統合 (Consolidate)", onClick = { onCommand("data.consolidate", "{\"function\":\"SUM\"}") })
                                        MasuSheetPillButton("🎯 ゴールシーク (What-If)", onClick = { onCommand("data.goalSeek", "{}") })
                                    }
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "アウトライン") {
                                SpreadsheetTallRibbonButton("➕", "➕ グループ化", highlight = state.isGrouped, onClick = { onCommand("data.group", "{}") })
                                SpreadsheetTallRibbonButton("➖", "➖ グループ解除", onClick = { onCommand("data.ungroup", "{}") })
                                SpreadsheetTallRibbonButton("Σ", "Σ 小計 (Subtotal)", onClick = { onCommand("data.subtotal", "{}") })
                            }
                        }

                        "review" -> {
                            SpreadsheetRibbonGroup(title = "文章校正") {
                                SpreadsheetTallRibbonButton("🔤", "🔤 スペルチェック", onClick = { onCommand("review.spelling", "{}") })
                                SpreadsheetTallRibbonButton("📊", "📊 ブックの統計情報", onClick = { onCommand("review.workbookStatistics", "{}") })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "コメント") {
                                SpreadsheetTallRibbonButton("💬", "💬 新しいコメント/メモ", onClick = { onOpenCommandCenter("comment") })
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    SpreadsheetRibbonCheckbox("👁️ コメント表示切替", checked = state.showAllComments, onClick = { onCommand("review.showAllComments", "{}") })
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MasuSheetPillButton("🗑️ コメント削除", onClick = { onCommand("review.deleteComment", "{}") })
                                        MasuSheetPillButton("⬅️ 前のコメント", onClick = { onCommand("review.previousComment", "{}") })
                                        MasuSheetPillButton("➡️ 次のコメント", onClick = { onCommand("review.nextComment", "{}") })
                                    }
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "保護・変更履歴") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SpreadsheetRibbonCheckbox("🔒 シートの保護", checked = state.isSheetProtected, onClick = { onCommand("review.protectSheet", "{}") })
                                        SpreadsheetRibbonCheckbox("🔐 ブックの保護", checked = state.isWorkbookProtected, onClick = { onCommand("review.protectWorkbook", "{}") })
                                    }
                                    SpreadsheetRibbonCheckbox("📜 変更履歴の表示", checked = state.showChangesActive, onClick = { onCommand("review.ShowChanges", "{}") })
                                }
                            }
                        }

                        "view" -> {
                            val isTopRowFrozen = state.freezeRows == 1 && state.freezeCols == 0
                            val isFirstColFrozen = state.freezeRows == 0 && state.freezeCols == 1
                            val isCustomPanesFrozen = (state.freezeRows > 0 || state.freezeCols > 0) && !isTopRowFrozen && !isFirstColFrozen

                            // Group 1: Workbook Views (ブックの表示)
                            SpreadsheetRibbonGroup(title = "ブックの表示") {
                                SpreadsheetTallRibbonButton(
                                    icon = "📋",
                                    label = "📋 標準ビュー",
                                    highlight = state.viewMode == "normal",
                                    tag = "ms_btn_view_normal",
                                    onClick = { onCommand("view.normal", "{}") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "⊞",
                                    label = "📄 改ページプレビュー",
                                    highlight = state.viewMode == "pageBreakPreview",
                                    tag = "ms_btn_view_page_break",
                                    onClick = { onCommand("view.pageBreakPreview", "{}") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "📐",
                                    label = "📐 ページレイアウトビュー",
                                    highlight = state.viewMode == "pageLayout",
                                    tag = "ms_btn_view_page_layout",
                                    onClick = { onCommand("view.pageLayout", "{}") }
                                )
                            }

                            SpreadsheetRibbonGroupDivider()

                            // Group 2: Show (表示 — Authentic Spreadsheet Checkboxes ☑/☐ so ON/OFF state is 100% unmistakable!)
                            SpreadsheetRibbonGroup(title = "表示") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SpreadsheetRibbonCheckbox(
                                            label = "▦ 目盛線の表示切替",
                                            checked = state.showGridlines,
                                            tag = "ms_btn_view_gridlines",
                                            onClick = { onCommand("view.gridlines", "{}") }
                                        )
                                        SpreadsheetRibbonCheckbox(
                                            label = "🔤 行列見出し表示切替",
                                            checked = state.showHeadings,
                                            tag = "ms_btn_view_headings",
                                            onClick = { onCommand("view.headings", "{}") }
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SpreadsheetRibbonCheckbox(
                                            label = "fx 数式バー表示切替",
                                            checked = state.formulaBarVisible,
                                            tag = "ms_btn_view_formulabar",
                                            onClick = { onCommand("view.formulaBar", "{}") }
                                        )
                                    }
                                }
                            }

                            SpreadsheetRibbonGroupDivider()

                            // Group 3: Zoom (ズーム)
                            SpreadsheetRibbonGroup(title = "ズーム") {
                                SpreadsheetTallRibbonButton("🔍", "🔍 ズーム (${state.zoomPercent}%)...", onClick = onOpenZoomDialogClick)
                                SpreadsheetTallRibbonButton("100%", "💯 100%に戻す", onClick = { onCommand("view.zoom100", "{}") })
                            }

                            SpreadsheetRibbonGroupDivider()

                            // Group 4: Window / Freeze Panes (ウィンドウ)
                            SpreadsheetRibbonGroup(title = "ウィンドウ") {
                                SpreadsheetTallRibbonButton(
                                    icon = "❄️",
                                    label = "❄️ 先頭行を固定",
                                    highlight = isTopRowFrozen,
                                    tag = "ms_btn_freeze_top_row",
                                    onClick = { onCommand("view.freezeTopRow", "{}") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "❄️",
                                    label = "❄️ 先頭列を固定",
                                    highlight = isFirstColFrozen,
                                    tag = "ms_btn_freeze_first_col",
                                    onClick = { onCommand("view.freezeFirstColumn", "{}") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "🧊",
                                    label = "❄️ 選択位置で枠固定",
                                    highlight = isCustomPanesFrozen,
                                    tag = "ms_btn_freeze_panes",
                                    onClick = {
                                        val addr = state.viewport.activeAddress
                                        onCommand("view.freezePanes", "{\"cell\":\"$addr\"}")
                                    }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "🔓",
                                    label = "🔓 枠固定を解除",
                                    tag = "ms_btn_unfreeze_panes",
                                    onClick = { onCommand("view.unfreezePanes", "{}") }
                                )
                            }
                        }

                        "automate" -> {
                            SpreadsheetRibbonGroup(title = "マクロ・自動化スクリプト") {
                                SpreadsheetTallRibbonButton(
                                    icon = if (state.isMacroRecording) "⏹️" else "🔴",
                                    label = if (state.isMacroRecording) "⏹️ マクロ記録を停止して保存" else "🔴 マクロの記録を開始",
                                    highlight = state.isMacroRecording,
                                    onClick = onToggleMacroRecording
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "🤖",
                                    label = "🤖 マクロ・VBAスタジオ (エディタ・1行デバッグ・自然言語変換)",
                                    onClick = onOpenMacroStudio
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "✨",
                                    label = "✨ AI アシスタントを開く",
                                    highlight = state.isAiPanelOpen,
                                    onClick = onToggleAiPanel
                                )
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "保存済みマクロ") {
                                state.savedMacros.forEach { macro ->
                                    MasuSheetPillButton(
                                        label = "▶️ ${macro.name} (${macro.shortcutKey})",
                                        onClick = onOpenMacroStudio
                                    )
                                }
                                if (state.lastMacroUndoSteps > 0) {
                                    MasuSheetPillButton(
                                        label = "↶ マクロ実行前の状態に戻す (${state.lastMacroUndoSteps}手)",
                                        onClick = onOpenMacroStudio
                                    )
                                }
                            }
                        }

                        "tableDesign" -> {
                            SpreadsheetRibbonGroup(title = "テーブル スタイル") {
                                SpreadsheetTallRibbonButton("📊", "📊 テーブルとして書式設定", highlight = state.isTableActive, onClick = { onCommand("home.formatAsTable", "{\"style\":\"TableStyleMedium2\"}") })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "テーブル スタイルのオプション") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SpreadsheetRibbonCheckbox("🏷️ 見出し行切替", checked = state.tableHeaderRow, onClick = { onCommand("tableDesign.headerRow", "{}") })
                                        SpreadsheetRibbonCheckbox("Σ 集計行の表示切替", checked = state.tableTotalRow, onClick = { onCommand("tableDesign.totalRow", "{}") })
                                        SpreadsheetRibbonCheckbox("🦓 縞模様(行)", checked = state.tableBandedRows, onClick = { onCommand("tableDesign.bandedRows", "{}") })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SpreadsheetRibbonCheckbox("1️⃣ 最初の列を強調", checked = state.tableFirstCol, onClick = { onCommand("tableDesign.firstColumn", "{}") })
                                        SpreadsheetRibbonCheckbox("🔚 最後の列を強調", checked = state.tableLastCol, onClick = { onCommand("tableDesign.lastColumn", "{}") })
                                        SpreadsheetRibbonCheckbox("🦓 縞模様(列)", checked = state.tableBandedCols, onClick = { onCommand("tableDesign.bandedColumns", "{}") })
                                        SpreadsheetRibbonCheckbox("🔽 フィルターボタン", checked = state.tableFilterButton, onClick = { onCommand("tableDesign.filterButton", "{}") })
                                    }
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "ツール") {
                                SpreadsheetTallRibbonButton("🧹", "🧹 重複の削除", onClick = { onCommand("tableDesign.removeDuplicates", "{}") })
                                SpreadsheetTallRibbonButton("📊", "📊 ピボットテーブルで集計", onClick = { onCommand("tableDesign.summarizeWithPivot", "{}") })
                                SpreadsheetTallRibbonButton("🔓", "🔓 通常範囲に変換 (テーブル解除)", onClick = onConvertToRangeClick)
                            }
                        }

                        "chartDesign" -> {
                            SpreadsheetRibbonGroup(title = "グラフの種類") {
                                SpreadsheetTallRibbonButton(
                                    icon = "📊",
                                    label = "📊 グラフの種類を変更 (全32種)...",
                                    highlight = true,
                                    onClick = { onCommand("chartDesign.changeType", "{}") }
                                )
                                SpreadsheetTallRibbonButton("📊", "📊 縦棒グラフへ変更", highlight = selectedObj?.kind == SheetObjectKind.CHART && selectedObj.subType == "column", onClick = { onCommand("chartDesign.changeType", "{\"type\":\"column\"}") })
                                SpreadsheetTallRibbonButton("📈", "📈 折れ線グラフへ変更", highlight = selectedObj?.kind == SheetObjectKind.CHART && selectedObj.subType == "line", onClick = { onCommand("chartDesign.changeType", "{\"type\":\"line\"}") })
                                SpreadsheetTallRibbonButton("🥧", "🥧 円グラフへ変更", highlight = selectedObj?.kind == SheetObjectKind.CHART && selectedObj.subType == "pie", onClick = { onCommand("chartDesign.changeType", "{\"type\":\"pie\"}") })
                                SpreadsheetTallRibbonButton("📊", "📊 横棒グラフへ変更", highlight = selectedObj?.kind == SheetObjectKind.CHART && selectedObj.subType == "bar_h", onClick = { onCommand("chartDesign.changeType", "{\"type\":\"bar_h\"}") })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "データ・スタイル・回転") {
                                SpreadsheetTallRibbonButton(
                                    icon = "🎯",
                                    label = "🎯 データの選択...",
                                    highlight = true,
                                    tag = "ms_btn_chart_select_data",
                                    onClick = onOpenChartSelectDataClick
                                )
                                SpreadsheetTallRibbonButton("🔄", "🔄 行と列の切り替え", onClick = { onCommand("chartDesign.switchRowColumn", "{}") })
                                SpreadsheetTallRibbonButton("📊", "📊 グラフ系列の色を変更", onClick = { onOpenColorPicker("chartSeries") })
                                SpreadsheetTallRibbonButton("🎨", "🎨 グラフ背景の色", onClick = { onOpenColorPicker("objectFill") })
                                SpreadsheetTallRibbonButton(
                                    icon = "🔤",
                                    label = "🔤 グラフ文字の色",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("🔤 グラフ要素ごとの文字色を選択", isHeader = true),
                                        SpreadsheetDropdownActionItem("🎯 グラフ内の文字すべて一括...") { onOpenColorPicker("objectText") },
                                        SpreadsheetDropdownActionItem("🏷️ グラフタイトルの文字色...") { onOpenColorPicker("objectText:title") },
                                        SpreadsheetDropdownActionItem("📏 X軸 (横軸) の文字色...") { onOpenColorPicker("objectText:xAxis") },
                                        SpreadsheetDropdownActionItem("📐 Y軸 (縦軸) の文字色...") { onOpenColorPicker("objectText:yAxis") },
                                        SpreadsheetDropdownActionItem("📋 凡例・右上範囲の文字色...") { onOpenColorPicker("objectText:legend") }
                                    ),
                                    onClick = { onOpenColorPicker("objectText") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "🔄",
                                    label = "🔄 グラフを回転",
                                    hasDropdown = true,
                                    menuItems = rotateMenuItems,
                                    onClick = { onRotateObjectBy(90f) }
                                )
                                SpreadsheetTallRibbonButton("✏️", "✏️ グラフ内テキスト編集", onClick = onEditSelectedObjectInPlace)
                                SpreadsheetTallRibbonButton("🗑️", "🗑️ グラフを削除", onClick = onDeleteSelectedObjectClick)
                            }
                        }

                        "pivotAnalyze" -> {
                            SpreadsheetRibbonGroup(title = "表示・データソース") {
                                SpreadsheetTallRibbonButton(
                                    icon = "📋",
                                    label = "📋 フィールド リスト(右ペイン)",
                                    highlight = state.isPivotFieldsPaneOpen,
                                    tag = "ms_btn_pivot_field_list",
                                    onClick = { onCommand("pivotAnalyze.fieldList", "{}") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "➕",
                                    label = "➕ ピボット作成・範囲変更",
                                    onClick = { onCommand("insert.recommendedPivotTables", "{\"openDialog\":true}") }
                                )
                                SpreadsheetTallRibbonButton("🔄", "🔄 ピボット更新", onClick = { onCommand("pivotAnalyze.refresh", "{}") })
                                SpreadsheetTallRibbonButton("🔄", "🔄 すべて更新", onClick = { onCommand("pivotAnalyze.refreshAll", "{}") })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "おすすめ集計パターン") {
                                SpreadsheetTallRibbonButton("📊", "📊 カテゴリ別(標準)", onClick = { onCommand("insert.pivotTable", "{\"preset\":\"standard\"}") })
                                SpreadsheetTallRibbonButton("📑", "📑 カテゴリ×項目(2階層)", onClick = { onCommand("insert.pivotTable", "{\"preset\":\"hierarchy\"}") })
                                SpreadsheetTallRibbonButton("🧮", "🧮 構成比(%)＋平均", onClick = { onCommand("insert.pivotTable", "{\"preset\":\"average_percent\"}") })
                                SpreadsheetTallRibbonButton("🏛️", "🏛️ クロス集計(行×列)", onClick = { onCommand("insert.pivotTable", "{\"preset\":\"crosstab\"}") })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "フィルター・ツール") {
                                SpreadsheetTallRibbonButton("🎛️", "🎛️ スライサーの挿入", onClick = { onCommand("pivotAnalyze.insertSlicer", "{}") })
                                SpreadsheetTallRibbonButton("📅", "📅 タイムラインの挿入", onClick = { onCommand("pivotAnalyze.insertTimeline", "{}") })
                                SpreadsheetTallRibbonButton(
                                    icon = "📊",
                                    label = "📊 ピボットグラフ作成",
                                    hasDropdown = true,
                                    menuItems = buildList {
                                        add(SpreadsheetDropdownActionItem("📊 ピボットグラフの挿入 (全10カテゴリ・32種)", isHeader = true))
                                        add(SpreadsheetDropdownActionItem("📊 ピボットグラフ・全32種グラフ挿入...") { onCommand("pivotAnalyze.pivotChart", "{}") })
                                        add(SpreadsheetDropdownActionItem(label = "", isDivider = true))
                                        SpreadsheetChartTypeCatalog.categories.forEach { cat ->
                                            add(
                                                SpreadsheetDropdownActionItem(
                                                    label = "${cat.icon} ${cat.titleJa} (${cat.titleEn})",
                                                    subItems = cat.subtypes.map { sub ->
                                                        "${sub.icon} ${sub.nameJa} (${sub.nameEn})" to {
                                                            onCommand("pivotAnalyze.pivotChart", "{\"type\":\"${sub.id}\"}")
                                                        }
                                                    }
                                                )
                                            )
                                        }
                                    },
                                    onClick = { onCommand("pivotAnalyze.pivotChart", "{}") }
                                )
                                SpreadsheetTallRibbonButton("🧹", "🧹 ピボット初期化", onClick = { onCommand("pivotAnalyze.clear", "{}") })
                                SpreadsheetTallRibbonButton("🗑️", "🗑️ ピボット削除", onClick = { onCommand("pivot.delete", "{}") })
                            }
                        }

                        "pivotDesign" -> {
                            SpreadsheetRibbonGroup(title = "レイアウト") {
                                SpreadsheetTallRibbonButton("Σ", "Σ 小計を表示しない", highlight = state.pivotSubtotalsMode == "none", onClick = { onCommand("pivotDesign.subtotals", "{\"mode\":\"none\"}") })
                                SpreadsheetTallRibbonButton("Σ", "Σ 小計をグループ下に表示", highlight = state.pivotSubtotalsMode == "bottom", onClick = { onCommand("pivotDesign.subtotals", "{\"mode\":\"bottom\"}") })
                                SpreadsheetTallRibbonButton("Σ", "Σ 行と列の総計を表示", highlight = state.pivotGrandTotalsMode == "both", onClick = { onCommand("pivotDesign.grandTotals", "{\"mode\":\"both\"}") })
                                SpreadsheetTallRibbonButton("📐", "📐 コンパクト形式", highlight = state.pivotReportLayout == "compact", onClick = { onCommand("pivotDesign.reportLayout", "{\"layout\":\"compact\"}") })
                                SpreadsheetTallRibbonButton("📐", "📐 アウトライン形式", highlight = state.pivotReportLayout == "outline", onClick = { onCommand("pivotDesign.reportLayout", "{\"layout\":\"outline\"}") })
                                SpreadsheetTallRibbonButton("📐", "📐 表形式で表示", highlight = state.pivotReportLayout == "tabular", onClick = { onCommand("pivotDesign.reportLayout", "{\"layout\":\"tabular\"}") })
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "ピボットテーブル スタイルのオプション") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SpreadsheetRibbonCheckbox("🦓 縞模様(行)", checked = state.pivotBandedRows, onClick = { onCommand("pivotDesign.bandedRows", "{}") })
                                        SpreadsheetRibbonCheckbox("🦓 縞模様(列)", checked = state.pivotBandedCols, onClick = { onCommand("pivotDesign.bandedColumns", "{}") })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SpreadsheetRibbonCheckbox("🔁 アイテムのラベルをすべて繰り返す", checked = state.pivotRepeatLabels, onClick = { onCommand("pivotDesign.reportLayout", "{\"repeatLabels\":true}") })
                                        SpreadsheetRibbonCheckbox("📏 空白行を挿入", checked = state.pivotBlankRows, onClick = { onCommand("pivotDesign.blankRows", "{\"insert\":true}") })
                                    }
                                }
                            }
                            SpreadsheetRibbonGroupDivider()
                            SpreadsheetRibbonGroup(title = "ピボットテーブル スタイル (配色)") {
                                SpreadsheetTallRibbonButton("🔵", "🔵 標準ブルー(月間予算)", highlight = state.pivotStylePreset == "PivotStyleMedium2", onClick = { onCommand("pivotDesign.style", "{\"style\":\"PivotStyleMedium2\"}") })
                                SpreadsheetTallRibbonButton("🟢", "🟢 エメラルドグリーン", highlight = state.pivotStylePreset == "PivotStyleMedium7", onClick = { onCommand("pivotDesign.style", "{\"style\":\"PivotStyleMedium7\"}") })
                                SpreadsheetTallRibbonButton("🟠", "🟠 ウォームオレンジ", highlight = state.pivotStylePreset == "PivotStyleMedium3", onClick = { onCommand("pivotDesign.style", "{\"style\":\"PivotStyleMedium3\"}") })
                                SpreadsheetTallRibbonButton("🟣", "🟣 ロイヤルパープル", highlight = state.pivotStylePreset == "PivotStyleMedium5", onClick = { onCommand("pivotDesign.style", "{\"style\":\"PivotStyleMedium5\"}") })
                                SpreadsheetTallRibbonButton("⚫", "⚫ ダークスレート", highlight = state.pivotStylePreset == "PivotStyleDark2", onClick = { onCommand("pivotDesign.style", "{\"style\":\"PivotStyleDark2\"}") })
                            }
                        }

                        else -> {
                            // Default: "Home" Tab Grouped Multi-Row Ribbon
                            val activeCellIsWrap = activeCell?.isWrap == true
                            val activeCellAlignKey = state.viewport.sheetIndex to NativeViewportSnapshot.packCellKey(state.viewport.activeRow, state.viewport.activeCol)
                            val hasAllBorders = activeCell != null &&
                                activeCell.borderTop != 0 && activeCell.borderBottom != 0 &&
                                activeCell.borderLeft != 0 && activeCell.borderRight != 0
                            val dispText = activeCell?.displayText.orEmpty()
                            val isCurrencyFmt = dispText.contains("¥") || dispText.contains("￥") || dispText.contains("$")
                            val isPercentFmt = dispText.endsWith("%")
                            val isCommaFmt = dispText.contains(",") && !isCurrencyFmt
                            val activeFontSize = (activeCell?.fontSize ?: 11f).roundToInt()

                            // Group 1: Clipboard (クリップボード)
                            SpreadsheetRibbonGroup(title = "クリップボード") {
                                SpreadsheetTallRibbonButton(
                                    icon = "📋",
                                    label = "📌 貼付",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("📋 貼り付けのオプション", isHeader = true),
                                        SpreadsheetDropdownActionItem("📋 貼り付け (P) — セルまたはクリップボード画像を貼付") { onPasteClick() },
                                        SpreadsheetDropdownActionItem(
                                            label = "🔢 値の貼り付け (Paste Values)",
                                            subItems = listOf(
                                                "123 値のみ貼り付け (V)" to { onCommand("edit.pasteSpecial", """{"mode":"values"}""") },
                                                "123% 値と数値の書式 (A)" to { onCommand("edit.pasteSpecial", """{"mode":"values_and_number_formats"}""") },
                                                "123🎨 値と元の書式 (E)" to { onCommand("edit.pasteSpecial", """{"mode":"values_and_source_formatting"}""") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem("fx 数式の貼り付け (F)") { onCommand("edit.pasteSpecial", """{"mode":"formulas"}""") },
                                        SpreadsheetDropdownActionItem("⇄ 行列を入れ替える (T)") { onCommand("home.pasteTranspose", "{}") },
                                        SpreadsheetDropdownActionItem("🎨 書式のみ貼り付け (R)") { onCommand("home.pasteFormatting", "{}") },
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("📸 クリップボード画像をシートへ貼り付け") { onPasteScreenshotClick() },
                                        SpreadsheetDropdownActionItem("🖼️ 画像ファイルを選択して挿入...") { onInsertImageClick() }
                                    ),
                                    onClick = onPasteClick
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    SpreadsheetCompactRowButton("✂️", "✂️ 切取", onClick = onCutClick)
                                    SpreadsheetCompactRowButton("📄", "📋 コピー", onClick = onCopyClick)
                                }
                            }

                            SpreadsheetRibbonGroupDivider()

                            // Group 2: Font (フォント — 2-row Spreadsheet layout: [Calibri ▾] [11 ▾] [+][-] / [B][I][U][ab][⊞][⊞▾][🪣][A])
                            SpreadsheetRibbonGroup(title = "フォント") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SpreadsheetDropdownBox(
                                            valueText = "Calibri",
                                            width = 82.dp,
                                            menuItems = listOf(
                                                SpreadsheetDropdownActionItem("🔤 フォントの選択", isHeader = true),
                                                SpreadsheetDropdownActionItem("Yu Gothic (游ゴシック)") { onCommand("home.fontFamily", "{\"family\":\"Yu Gothic\"}") },
                                                SpreadsheetDropdownActionItem("Meiryo (メイリオ)") { onCommand("home.fontFamily", "{\"family\":\"Meiryo\"}") },
                                                SpreadsheetDropdownActionItem("Calibri (本文のフォント)") { onCommand("home.fontFamily", "{\"family\":\"Calibri\"}") },
                                                SpreadsheetDropdownActionItem("Arial") { onCommand("home.fontFamily", "{\"family\":\"Arial\"}") },
                                                SpreadsheetDropdownActionItem("Times New Roman") { onCommand("home.fontFamily", "{\"family\":\"Times New Roman\"}") },
                                                SpreadsheetDropdownActionItem("Consolas (等幅)") { onCommand("home.fontFamily", "{\"family\":\"Consolas\"}") },
                                                SpreadsheetDropdownActionItem("MS PGothic (ＭＳ Ｐゴシック)") { onCommand("home.fontFamily", "{\"family\":\"MS PGothic\"}") }
                                            ),
                                            onClick = {}
                                        )
                                        SpreadsheetDropdownBox(
                                            valueText = "$activeFontSize",
                                            width = 42.dp,
                                            menuItems = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36).map { pt ->
                                                SpreadsheetDropdownActionItem("${pt} pt") {
                                                    onCommand("home.fontSize", "{\"size\":$pt}")
                                                }
                                            },
                                            onClick = { onCommand("home.increaseFontSize", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(symbol = "A▲", onClick = { onCommand("home.increaseFontSize", "{}") })
                                        SpreadsheetSmallIconBtn(symbol = "A▼", onClick = { onCommand("home.decreaseFontSize", "{}") })
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SpreadsheetSmallIconBtn(
                                            symbol = "B",
                                            bold = true,
                                            highlight = activeCell?.isBold == true,
                                            tag = "ms_btn_bold",
                                            onClick = { onCommand("home.bold", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "I",
                                            italic = true,
                                            highlight = activeCell?.isItalic == true,
                                            tag = "ms_btn_italic",
                                            onClick = { onCommand("home.italic", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "U",
                                            underline = true,
                                            highlight = activeCell?.isUnderline == true,
                                            tag = "ms_btn_underline",
                                            onClick = { onCommand("home.underline", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "ab",
                                            strike = true,
                                            highlight = activeCell?.isStrike == true,
                                            tag = "ms_btn_strike",
                                            onClick = { onCommand("home.strikethrough", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "⊞",
                                            highlight = hasAllBorders,
                                            tag = "ms_btn_borders_all",
                                            onClick = {
                                                if (hasAllBorders) {
                                                    onCommand("home.borders", "{\"preset\":\"none\",\"style\":\"none\"}")
                                                } else {
                                                    onCommand("home.borders", "{\"preset\":\"all\",\"style\":\"thin\"}")
                                                }
                                            }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "▾",
                                            menuItems = listOf(
                                                SpreadsheetDropdownActionItem("⊞ 罫線 (Borders)", isHeader = true),
                                                SpreadsheetDropdownActionItem("⊞ 格子 (すべての罫線)") { onCommand("home.borders", "{\"preset\":\"all\",\"style\":\"thin\"}") },
                                                SpreadsheetDropdownActionItem("▢ 外枠 (Outside Borders)") { onCommand("home.borders", "{\"preset\":\"outline\",\"style\":\"thin\"}") },
                                                SpreadsheetDropdownActionItem("┏━┓ 太い外枠 (Thick Box Border)") { onCommand("home.borders", "{\"preset\":\"outline\",\"style\":\"medium\"}") },
                                                SpreadsheetDropdownActionItem("▁ 下罫線 (Bottom Border)") { onCommand("home.borders", "{\"preset\":\"bottom\",\"style\":\"thin\"}") },
                                                SpreadsheetDropdownActionItem("▔ 上罫線 (Top Border)") { onCommand("home.borders", "{\"preset\":\"top\",\"style\":\"thin\"}") },
                                                SpreadsheetDropdownActionItem("▏ 左罫線 (Left Border)") { onCommand("home.borders", "{\"preset\":\"left\",\"style\":\"thin\"}") },
                                                SpreadsheetDropdownActionItem("▕ 右罫線 (Right Border)") { onCommand("home.borders", "{\"preset\":\"right\",\"style\":\"thin\"}") },
                                                SpreadsheetDropdownActionItem("═ 下二重罫線 (Bottom Double Border)") { onCommand("home.borders", "{\"preset\":\"bottom\",\"style\":\"double\"}") },
                                                SpreadsheetDropdownActionItem("━ 太い下罫線 (Thick Bottom Border)") { onCommand("home.borders", "{\"preset\":\"bottom\",\"style\":\"thick\"}") },
                                                SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                                SpreadsheetDropdownActionItem("🚫 枠なし (No Border)") { onCommand("home.borders", "{\"preset\":\"none\",\"style\":\"none\"}") },
                                                SpreadsheetDropdownActionItem("🎨 線の色を選択...") { onOpenColorPicker("border") }
                                            ),
                                            onClick = {}
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "🪣",
                                            colorBar = if (activeCell != null && activeCell.bgColor != 0) Color(activeCell.bgColor) else Color(0xFFFACC15),
                                            onClick = { onOpenColorPicker("fill") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "A",
                                            bold = true,
                                            colorBar = if (activeCell != null && activeCell.fgColor != 0) Color(activeCell.fgColor) else Color(0xFFDC2626),
                                            onClick = { onOpenColorPicker("font") }
                                        )
                                    }
                                }
                            }

                            SpreadsheetRibbonGroupDivider()

                            // Group 3: Alignment (配置 — 2-row Spreadsheet layout: [⬆][↕][⬇][↵ Wrap Text] / [⬅][⬌][➡][⊞ Merge & Center ▾])
                            SpreadsheetRibbonGroup(title = "配置") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SpreadsheetSmallIconBtn(
                                            symbol = "⬆",
                                            highlight = (activeCell == null || activeCell.vAlign == 0) && activeCellAlignKey in state.explicitTopAlignCells,
                                            onClick = { onCommand("home.alignTop", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "↕",
                                            highlight = activeCell?.vAlign == 1,
                                            onClick = { onCommand("home.alignMiddle", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "⬇",
                                            highlight = activeCell?.vAlign == 2,
                                            onClick = { onCommand("home.alignBottom", "{}") }
                                        )
                                        SpreadsheetCompactRowButton(
                                            icon = "↵",
                                            label = "↩️ 折返し表示",
                                            highlight = activeCellIsWrap,
                                            onClick = { onCommand("home.wrapText", "{}") }
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SpreadsheetSmallIconBtn(
                                            symbol = "⬅",
                                            highlight = (activeCell == null || activeCell.hAlign == 0) && activeCellAlignKey in state.explicitLeftAlignCells,
                                            tag = "ms_btn_align_left",
                                            onClick = { onCommand("home.alignLeft", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "⬌",
                                            highlight = activeCell?.hAlign == 1,
                                            tag = "ms_btn_align_center",
                                            onClick = { onCommand("home.alignCenter", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "➡",
                                            highlight = activeCell?.hAlign == 2,
                                            tag = "ms_btn_align_right",
                                            onClick = { onCommand("home.alignRight", "{}") }
                                        )
                                        SpreadsheetCompactRowButton(
                                            icon = "⊞",
                                            label = "⊞ 結合して中央",
                                            highlight = activeMerge != null,
                                            tag = "ms_btn_merge_center",
                                            onClick = { onCommand("home.mergeCenter", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "▾",
                                            menuItems = listOf(
                                                SpreadsheetDropdownActionItem("⊞ セルの結合オプション", isHeader = true),
                                                SpreadsheetDropdownActionItem("⊞ 結合して中央揃え (Merge & Center)") { onCommand("home.mergeCenter", "{}") },
                                                SpreadsheetDropdownActionItem("↔ 横方向に結合 (Merge Across)") { onCommand("home.mergeAcross", "{}") },
                                                SpreadsheetDropdownActionItem("⬚ セルの結合 (Merge Cells)") { onCommand("home.mergeCells", "{}") },
                                                SpreadsheetDropdownActionItem("🔓 セル結合の解除 (Unmerge Cells)") { onCommand("home.unmergeCells", "{}") }
                                            ),
                                            onClick = {}
                                        )
                                    }
                                }
                            }

                            SpreadsheetRibbonGroupDivider()

                            // Group 4: Number (数値 — [General ▾] / [$][%][,][.00][→.0])
                            SpreadsheetRibbonGroup(title = "数値") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    SpreadsheetDropdownBox(
                                        valueText = when {
                                            isCurrencyFmt -> if (isEn) "Currency" else "通貨"
                                            isPercentFmt -> if (isEn) "Percentage" else "パーセント"
                                            isCommaFmt -> if (isEn) "Number" else "数値"
                                            else -> if (isEn) "General" else "標準"
                                        },
                                        width = 108.dp,
                                        menuItems = listOf(
                                            SpreadsheetDropdownActionItem("🔢 数値の表示形式", isHeader = true),
                                            SpreadsheetDropdownActionItem("標準 (General)") { onCommand("home.numberFormat", "{\"format\":\"General\"}") },
                                            SpreadsheetDropdownActionItem("数値 (1,234)") { onCommand("home.numberFormat", "{\"format\":\"#,##0\"}") },
                                            SpreadsheetDropdownActionItem(
                                                label = "通貨 (Currency)",
                                                subItems = listOf(
                                                    "¥ 日本円 (¥1,234)" to { onCommand("home.numberFormat", "{\"format\":\"¥#,##0\"}") },
                                                    "$ 米ドル ($1,234.00)" to { onCommand("home.numberFormat", "{\"format\":\"$#,##0.00\"}") },
                                                    "€ ユーロ (€1,234.00)" to { onCommand("home.numberFormat", "{\"format\":\"€#,##0.00\"}") }
                                                )
                                            ),
                                            SpreadsheetDropdownActionItem("会計 (¥ #,##0)") { onCommand("home.numberFormat", "{\"format\":\"¥#,##0\"}") },
                                            SpreadsheetDropdownActionItem("短い日付 (2026/10/31)") { onCommand("home.numberFormat", "{\"format\":\"yyyy/mm/dd\"}") },
                                            SpreadsheetDropdownActionItem("長い日付 (2026年10月31日)") { onCommand("home.numberFormat", "{\"format\":\"yyyy年m月d日\"}") },
                                            SpreadsheetDropdownActionItem("パーセンテージ (0.0%)") { onCommand("home.numberFormat", "{\"format\":\"0.0%\"}") },
                                            SpreadsheetDropdownActionItem("指数 (1.23E+03)") { onCommand("home.numberFormat", "{\"format\":\"0.00E+00\"}") },
                                            SpreadsheetDropdownActionItem("文字列 (@)") { onCommand("home.numberFormat", "{\"format\":\"@\"}") }
                                        ),
                                        onClick = { onCommand("home.numberFormat", "{\"format\":\"General\"}") }
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SpreadsheetSmallIconBtn(
                                            symbol = "¥/$",
                                            highlight = isCurrencyFmt,
                                            onClick = {
                                                if (isCurrencyFmt) {
                                                    onCommand("home.numberFormat", "{\"format\":\"General\"}")
                                                } else {
                                                    onCommand("home.numberFormat", "{\"format\":\"¥#,##0\"}")
                                                }
                                            }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "%",
                                            highlight = isPercentFmt,
                                            onClick = {
                                                if (isPercentFmt) {
                                                    onCommand("home.numberFormat", "{\"format\":\"General\"}")
                                                } else {
                                                    onCommand("home.numberFormat", "{\"format\":\"0.0%\"}")
                                                }
                                            }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = ",",
                                            highlight = isCommaFmt,
                                            onClick = {
                                                if (isCommaFmt) {
                                                    onCommand("home.numberFormat", "{\"format\":\"General\"}")
                                                } else {
                                                    onCommand("home.numberFormat", "{\"format\":\"#,##0\"}")
                                                }
                                            }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = ".00",
                                            onClick = { onCommand("home.increaseDecimal", "{}") }
                                        )
                                        SpreadsheetSmallIconBtn(
                                            symbol = "→.0",
                                            onClick = { onCommand("home.decreaseDecimal", "{}") }
                                        )
                                    }
                                }
                            }

                            SpreadsheetRibbonGroupDivider()

                            // Group 5: Styles (スタイル — Tall Conditional Formatting, Format as Table, Cell Styles)
                            SpreadsheetRibbonGroup(title = "スタイル") {
                                SpreadsheetTallRibbonButton(
                                    icon = "📊",
                                    label = "🌈 条件付き書式(データバー)",
                                    hasDropdown = true,
                                    highlight = state.activeConditionalFormat.isNotEmpty(),
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("🌈 条件付き書式 (Conditional Formatting)", isHeader = true),
                                        SpreadsheetDropdownActionItem(
                                            label = "📏 セルの強調表示ルール (Highlight Cells Rules)",
                                            subItems = listOf(
                                                "指定の値より大きい (Greater Than)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"greaterThan\"}") },
                                                "指定の値より小さい (Less Than)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"lessThan\"}") },
                                                "次の値の間 (Between)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"between\"}") },
                                                "指定の値に等しい (Equal To)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"equalTo\"}") },
                                                "文字列を含む (Text that Contains)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"textContains\"}") },
                                                "重複する値 (Duplicate Values)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"duplicateValues\"}") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(
                                            label = "🔝 上位/下位ルール (Top/Bottom Rules)",
                                            subItems = listOf(
                                                "上位 10 項目 (Top 10 Items)" to { onCommand("home.conditionalFormatting", "{\"type\":\"top10\"}") },
                                                "上位 10% (Top 10%)" to { onCommand("home.conditionalFormatting", "{\"type\":\"top10Percent\"}") },
                                                "下位 10 項目 (Bottom 10 Items)" to { onCommand("home.conditionalFormatting", "{\"type\":\"bottom10\"}") },
                                                "平均より上 (Above Average)" to { onCommand("home.conditionalFormatting", "{\"type\":\"aboveAverage\"}") },
                                                "平均より下 (Below Average)" to { onCommand("home.conditionalFormatting", "{\"type\":\"belowAverage\"}") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(
                                            label = "📊 データバー (Data Bars)",
                                            subItems = listOf(
                                                "🟢 緑のデータバー (Green Data Bar)" to { onCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"color\":\"#1F9D55\"}") },
                                                "🔵 青のデータバー (Blue Data Bar)" to { onCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"color\":\"#2563EB\"}") },
                                                "🟠 オレンジのデータバー (Orange Data Bar)" to { onCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"color\":\"#D97706\"}") },
                                                "🔴 赤のデータバー (Red Data Bar)" to { onCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"color\":\"#DC2626\"}") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(
                                            label = "🌈 カラースケール (Color Scales)",
                                            subItems = listOf(
                                                "緑 - 黄 - 赤 カラースケール" to { onCommand("home.conditionalFormatting", "{\"type\":\"colorScale\",\"scale\":\"GYR\"}") },
                                                "赤 - 黄 - 緑 カラースケール" to { onCommand("home.conditionalFormatting", "{\"type\":\"colorScale\",\"scale\":\"RYG\"}") },
                                                "青 - 白 - 赤 カラースケール" to { onCommand("home.conditionalFormatting", "{\"type\":\"colorScale\",\"scale\":\"BWR\"}") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(
                                            label = "🚦 アイコンセット (Icon Sets)",
                                            subItems = listOf(
                                                "⬆️➡️⬇️ 3方向の矢印" to { onCommand("home.conditionalFormatting", "{\"type\":\"iconSet\",\"set\":\"arrows3\"}") },
                                                "🟢🟡🔴 3色の信号機" to { onCommand("home.conditionalFormatting", "{\"type\":\"iconSet\",\"set\":\"trafficLights3\"}") },
                                                "✅⚠️❌ 3つの記号" to { onCommand("home.conditionalFormatting", "{\"type\":\"iconSet\",\"set\":\"symbols3\"}") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("🧹 ルールのクリア (Clear Rules)") { onCommand("home.clearConditionalFormatting", "{}") }
                                    ),
                                    onClick = { onCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"color\":\"#1F9D55\"}") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "📅",
                                    label = "📊 テーブルとして書式設定",
                                    hasDropdown = true,
                                    highlight = state.isTableActive,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("📊 テーブル スタイル", isHeader = true),
                                        SpreadsheetDropdownActionItem(
                                            label = "🌤️ 淡色 (Light)",
                                            subItems = listOf(
                                                "テーブル スタイル (淡色 1 - 緑)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleLight1\"}") },
                                                "テーブル スタイル (淡色 2 - 青)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleLight2\"}") },
                                                "テーブル スタイル (淡色 9 - グレー)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleLight9\"}") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(
                                            label = "⛅ 中間 (Medium)",
                                            subItems = listOf(
                                                "テーブル スタイル (中間 2 - 標準緑)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleMedium2\"}") },
                                                "テーブル スタイル (中間 4 - 赤)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleMedium4\"}") },
                                                "テーブル スタイル (中間 6 - 青)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleMedium6\"}") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(
                                            label = "🌑 濃色 (Dark)",
                                            subItems = listOf(
                                                "テーブル スタイル (濃色 1 - 黒緑)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleDark1\"}") },
                                                "テーブル スタイル (濃色 2 - 濃青)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleDark2\"}") }
                                            )
                                        )
                                    ),
                                    onClick = { onCommand("home.formatAsTable", "{\"style\":\"TableStyleMedium2\"}") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "🎨",
                                    label = "🎨 塗りつぶし色 (全60色+RGB)",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("🎨 セルのスタイル・塗りつぶし", isHeader = true),
                                        SpreadsheetDropdownActionItem("⬜ 標準 (Normal - 色なし)") { onCommand("home.cellStyle", "{\"style\":\"Normal\"}") },
                                        SpreadsheetDropdownActionItem("🟢 良い (Good - 緑ハイライト)") { onCommand("home.cellStyle", "{\"style\":\"Good\"}") },
                                        SpreadsheetDropdownActionItem("🔴 悪い (Bad - 赤ハイライト)") { onCommand("home.cellStyle", "{\"style\":\"Bad\"}") },
                                        SpreadsheetDropdownActionItem("🟡 中立 (Neutral - 黄ハイライト)") { onCommand("home.cellStyle", "{\"style\":\"Neutral\"}") },
                                        SpreadsheetDropdownActionItem(
                                            label = "📊 見出しとタイトル (Titles & Headings)",
                                            subItems = listOf(
                                                "見出し 1 (Heading 1)" to { onCommand("home.cellStyle", "{\"style\":\"Heading 1\"}") },
                                                "見出し 2 (Heading 2)" to { onCommand("home.cellStyle", "{\"style\":\"Heading 2\"}") },
                                                "タイトル (Title)" to { onCommand("home.cellStyle", "{\"style\":\"Title\"}") },
                                                "集計 (Total)" to { onCommand("home.cellStyle", "{\"style\":\"Total\"}") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("🎨 フル色パレット (60色+RGB) を開く...") { onOpenColorPicker("fill") }
                                    ),
                                    onClick = { onOpenColorPicker("fill") }
                                )
                            }

                            SpreadsheetRibbonGroupDivider()

                            // Group 6: Cells (セル — Insert / Delete / Format)
                            SpreadsheetRibbonGroup(title = "セル") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        SpreadsheetCompactRowButton(
                                            icon = "➕",
                                            label = "➕ 行挿入",
                                            hasDropdown = true,
                                            menuItems = listOf(
                                                SpreadsheetDropdownActionItem("➕ 挿入メニュー", isHeader = true),
                                                SpreadsheetDropdownActionItem("➕ シートの行を挿入 (Insert Sheet Rows)") { onCommand("home.insertRows", "{}") },
                                                SpreadsheetDropdownActionItem("➕ シートの列を挿入 (Insert Sheet Columns)") { onCommand("home.insertColumns", "{}") },
                                                SpreadsheetDropdownActionItem("➕ セルを挿入 (下方向にシフト)") { onCommand("home.insertCells", "{\"shift\":\"down\"}") },
                                                SpreadsheetDropdownActionItem("➕ 新しいシートを挿入 (Insert Sheet)") { onAddSheetClick() }
                                            ),
                                            onClick = { onCommand("home.insertRows", "{}") }
                                        )
                                        SpreadsheetCompactRowButton(
                                            icon = "➕",
                                            label = "➕ 列挿入",
                                            onClick = { onCommand("home.insertColumns", "{}") }
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        SpreadsheetCompactRowButton(
                                            icon = "➖",
                                            label = "➖ 行削除",
                                            hasDropdown = true,
                                            menuItems = listOf(
                                                SpreadsheetDropdownActionItem("➖ 削除メニュー", isHeader = true),
                                                SpreadsheetDropdownActionItem("➖ シートの行を削除 (Delete Sheet Rows)") { onCommand("home.deleteRows", "{}") },
                                                SpreadsheetDropdownActionItem("➖ シートの列を削除 (Delete Sheet Columns)") { onCommand("home.deleteColumns", "{}") },
                                                SpreadsheetDropdownActionItem("➖ セルを削除 (上方向にシフト)") { onCommand("home.deleteCells", "{\"shift\":\"up\"}") }
                                            ),
                                            onClick = { onCommand("home.deleteRows", "{}") }
                                        )
                                        SpreadsheetCompactRowButton(
                                            icon = "📐",
                                            label = "↔ 列幅自動",
                                            hasDropdown = true,
                                            menuItems = listOf(
                                                SpreadsheetDropdownActionItem("📐 セルのサイズ・書式", isHeader = true),
                                                SpreadsheetDropdownActionItem("↕ 行の高さの自動調整 (AutoFit Row Height)") { onAutoFitRowClick() },
                                                SpreadsheetDropdownActionItem("↔ 列の幅の自動調整 (AutoFit Column Width)") { onAutoFitColClick() },
                                                SpreadsheetDropdownActionItem("↕ 行の高さを広げる (+6pt)") { onResizeRowDeltaClick(6f) },
                                                SpreadsheetDropdownActionItem("↔ 列の幅を広げる (+16pt)") { onResizeColDeltaClick(16f) },
                                                SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                                SpreadsheetDropdownActionItem("🔒 シートの保護 (Protect Sheet)...") { onCommand("review.protectSheet", "{}") }
                                            ),
                                            onClick = onAutoFitColClick
                                        )
                                    }
                                }
                            }

                            SpreadsheetRibbonGroupDivider()

                            // Group 7: Editing (編集 — AutoSum / Clear / Sort & Filter / Find & Select)
                            SpreadsheetRibbonGroup(title = "編集") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    SpreadsheetCompactRowButton(
                                        icon = "Σ",
                                        label = "Σ オートSUM (合計)",
                                        hasDropdown = true,
                                        menuItems = listOf(
                                            SpreadsheetDropdownActionItem("Σ 合計 (SUM)") { onCommand("formulas.autoSum", "{\"function\":\"SUM\"}") },
                                            SpreadsheetDropdownActionItem("x̄ 平均 (AVERAGE)") { onCommand("formulas.autoSum", "{\"function\":\"AVERAGE\"}") },
                                            SpreadsheetDropdownActionItem("# 数値の個数 (COUNT)") { onCommand("formulas.autoSum", "{\"function\":\"COUNT\"}") },
                                            SpreadsheetDropdownActionItem("⬆️ 最大値 (MAX)") { onCommand("formulas.autoSum", "{\"function\":\"MAX\"}") },
                                            SpreadsheetDropdownActionItem("⬇️ 最小値 (MIN)") { onCommand("formulas.autoSum", "{\"function\":\"MIN\"}") },
                                            SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                            SpreadsheetDropdownActionItem("fx その他の関数 (全523関数)...") { onOpenFunctionWizard() }
                                        ),
                                        onClick = { onCommand("formulas.autoSum", "{\"function\":\"SUM\"}") }
                                    )
                                    SpreadsheetCompactRowButton(
                                        icon = "🧹",
                                        label = "🧹 消去",
                                        hasDropdown = true,
                                        menuItems = listOf(
                                            SpreadsheetDropdownActionItem("🧹 クリア (Clear)", isHeader = true),
                                            SpreadsheetDropdownActionItem("🧹 すべてクリア (Clear All)") { onCommand("home.clearAll", "{}") },
                                            SpreadsheetDropdownActionItem("🎨 書式のクリア (Clear Formats)") { onCommand("home.clearFormats", "{}") },
                                            SpreadsheetDropdownActionItem("📝 数式と値のクリア (Clear Contents)") { onCommand("edit.clearContents", "{}") },
                                            SpreadsheetDropdownActionItem("💬 コメントとメモのクリア (Clear Comments)") { onCommand("review.deleteComment", "{}") }
                                        ),
                                        onClick = { onCommand("edit.clearContents", "{}") }
                                    )
                                }
                                SpreadsheetTallRibbonButton(
                                    icon = "⇅",
                                    label = "🔽 オートフィルター切替",
                                    hasDropdown = true,
                                    highlight = state.isAutoFilterActive,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("⇅ 並べ替えとフィルター", isHeader = true),
                                        SpreadsheetDropdownActionItem("🔼 昇順 (A → Z)") { onCommand("data.sortAscending", "{}") },
                                        SpreadsheetDropdownActionItem("🔽 降順 (Z → A)") { onCommand("data.sortDescending", "{}") },
                                        SpreadsheetDropdownActionItem("🗂️ ユーザー設定の並べ替え...") { onCommand("data.sortCustom", "{}") },
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("🔽 フィルター切替 (AutoFilter)") { onCommand("data.filter", "{}") },
                                        SpreadsheetDropdownActionItem("🚫 フィルターのクリア") { onCommand("data.clearFilter", "{}") }
                                    ),
                                    onClick = { onCommand("data.filter", "{}") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "🔍",
                                    label = "🔍 検索と置換",
                                    hasDropdown = true,
                                    menuItems = listOf(
                                        SpreadsheetDropdownActionItem("🔍 検索と選択 (Find & Select)", isHeader = true),
                                        SpreadsheetDropdownActionItem("🔍 検索 (Find)...") { onOpenCommandCenter("findReplace") },
                                        SpreadsheetDropdownActionItem("🔄 置換 (Replace)...") { onOpenCommandCenter("findReplace") },
                                        SpreadsheetDropdownActionItem("📍 ジャンプ (Go To)...") { onOpenCommandCenter("goTo") },
                                        SpreadsheetDropdownActionItem(
                                            label = "📐 条件を選択してジャンプ (Go To Special)",
                                            subItems = listOf(
                                                "fx 数式 (Formulas)" to { onCommand("edit.goToSpecial", "{\"mode\":\"formulas\"}") },
                                                "🔢 定数 (Constants)" to { onCommand("edit.goToSpecial", "{\"mode\":\"constants\"}") },
                                                "⬜ 空白セル (Blanks)" to { onCommand("edit.goToSpecial", "{\"mode\":\"blanks\"}") },
                                                "💬 コメント (Comments)" to { onCommand("edit.goToSpecial", "{\"mode\":\"comments\"}") },
                                                "🌈 条件付き書式 (Conditional Formats)" to { onOpenCommandCenter("conditional") },
                                                "✅ データの入力規則 (Data Validation)" to { onOpenCommandCenter("validation") }
                                            )
                                        ),
                                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                                        SpreadsheetDropdownActionItem("🌈 条件付き書式 (Conditional Formatting)...") { onOpenCommandCenter("conditional") },
                                        SpreadsheetDropdownActionItem("✅ データの入力規則 (Data Validation)...") { onOpenCommandCenter("validation") }
                                    ),
                                    onClick = { onOpenCommandCenter("findReplace") }
                                )
                                SpreadsheetTallRibbonButton(
                                    icon = "☑",
                                    label = "☑ チェック切替/挿入",
                                    highlight = activeCell != null && InlineCheckboxHelper.containsCheckbox(activeCell.displayText),
                                    tag = "ms_btn_checkbox_toggle",
                                    onClick = onInsertCheckboxClick
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
