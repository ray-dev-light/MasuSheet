package com.masusheet.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.view.PointerIcon
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.masusheet.app.SecondaryWorkbookActivity
import com.masusheet.app.canvas.MasuSheetNativeSheetContainer
import com.masusheet.app.catalog.SpreadsheetRibbonCatalog
import com.masusheet.app.core.InlineCheckboxHelper
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.core.SpreadsheetI18n
import com.masusheet.app.model.SheetObjectKind
import com.masusheet.app.ui.ai.AiSpreadsheetAssistantPanel
import com.masusheet.app.ui.dialogs.MacroVbaStudioDialog
import com.masusheet.app.ui.dialogs.MasuSheetSaveDialog
import com.masusheet.app.ui.dialogs.SpreadsheetChartAxisFormatDialog
import com.masusheet.app.ui.dialogs.SpreadsheetChartSelectDataDialog
import com.masusheet.app.ui.dialogs.SpreadsheetColorPickerDialog
import com.masusheet.app.ui.dialogs.SpreadsheetColumnFilterPopupDialog
import com.masusheet.app.ui.dialogs.SpreadsheetCommandCenterDialog
import com.masusheet.app.ui.dialogs.SpreadsheetCreatePivotTableDialog
import com.masusheet.app.ui.dialogs.SpreadsheetFunctionWizardDialog
import com.masusheet.app.ui.dialogs.SpreadsheetOpenSourceLicensesDialog
import com.masusheet.app.ui.dialogs.SpreadsheetPivotChartPickerDialog
import com.masusheet.app.ui.dialogs.SpreadsheetPivotTableFieldsRightPane
import com.masusheet.app.ui.dialogs.SpreadsheetZoomDialog
import com.masusheet.app.ui.ribbon.MasuSheetFullRibbon
import com.masusheet.app.ui.ribbon.MasuSheetPillButton
import com.masusheet.app.ui.sheet.MasuSheetNativeFormulaBar
import com.masusheet.app.ui.sheet.MasuSheetNativeSheetTabsBar
import com.masusheet.app.viewmodel.MasuSheetViewModel
import com.masusheet.app.viewmodel.computeColumnAutoCompleteSuggestion
import com.masusheet.app.viewmodel.detectTablesOnCurrentSheet
import com.masusheet.app.viewmodel.getActivePivotTable
import com.masusheet.app.viewmodel.getAiApiKeyForModel
import com.masusheet.app.viewmodel.getAiCustomEndpoint
import com.masusheet.app.viewmodel.getAiCustomModelName
import com.masusheet.app.viewmodel.getChartSourceTableColumns
import com.masusheet.app.viewmodel.getMaterializedSheetObjectsForCurrentSheet
import com.masusheet.app.viewmodel.onAddChartSeriesFromSheet
import com.masusheet.app.viewmodel.onAddSheet
import com.masusheet.app.viewmodel.onAnswerAiPlanQuestion
import com.masusheet.app.viewmodel.onApplyColumnFilter
import com.masusheet.app.viewmodel.onApplyPickedColor
import com.masusheet.app.viewmodel.onApplyThemePreset
import com.masusheet.app.viewmodel.onAssignPivotFieldToArea
import com.masusheet.app.viewmodel.onBindCurrentSelectionToChart
import com.masusheet.app.viewmodel.onBindSelectionAsChartXAxis
import com.masusheet.app.viewmodel.onBindSelectionAsChartYAxis
import com.masusheet.app.viewmodel.onCancelAiPlan
import com.masusheet.app.viewmodel.onCancelPendingSave
import com.masusheet.app.viewmodel.onClearColumnFilter
import com.masusheet.app.viewmodel.onClearPivotTable
import com.masusheet.app.viewmodel.onCloseOpenWorkbook
import com.masusheet.app.viewmodel.onCompletePendingSaveAsync
import com.masusheet.app.viewmodel.onConfirmSaveDirectlyToFolderAsync
import com.masusheet.app.viewmodel.onCreateNewBlankWorkbookSession
import com.masusheet.app.viewmodel.onCreatePivotTable
import com.masusheet.app.viewmodel.onCyclePivotValueFieldAgg
import com.masusheet.app.viewmodel.onCycleZoom
import com.masusheet.app.viewmodel.onDefineName
import com.masusheet.app.viewmodel.onDeleteMacro
import com.masusheet.app.viewmodel.onDeletePivotTable
import com.masusheet.app.viewmodel.onDeleteSheetAt
import com.masusheet.app.viewmodel.onDeleteSheetObject
import com.masusheet.app.viewmodel.onDismissColumnFilterMenu
import com.masusheet.app.viewmodel.onDismissError
import com.masusheet.app.viewmodel.onDismissPivotLauncher
import com.masusheet.app.viewmodel.onDismissSaveDialog
import com.masusheet.app.viewmodel.onDuplicateSheetAt
import com.masusheet.app.viewmodel.onExecuteConfirmedAiPlan
import com.masusheet.app.viewmodel.onFindAllCells
import com.masusheet.app.viewmodel.onFindNext
import com.masusheet.app.viewmodel.onFinishChartRangePickingOnSheet
import com.masusheet.app.viewmodel.onFitZoomToSelection
import com.masusheet.app.viewmodel.onGenerateVbaFromPrompt
import com.masusheet.app.viewmodel.onGoToReference
import com.masusheet.app.viewmodel.onGoToSpecial
import com.masusheet.app.viewmodel.onHideSheetAt
import com.masusheet.app.viewmodel.onImportWebAiJsonResponse
import com.masusheet.app.viewmodel.onInsertChart
import com.masusheet.app.viewmodel.onInsertImageBytes
import com.masusheet.app.viewmodel.onInsertImageUriAsync
import com.masusheet.app.viewmodel.onInsertOrUpdateChartFromPicker
import com.masusheet.app.viewmodel.onInsertShape
import com.masusheet.app.viewmodel.onInsertSheetBefore
import com.masusheet.app.viewmodel.onInsertTextBox
import com.masusheet.app.viewmodel.onLoadNextSample
import com.masusheet.app.viewmodel.onLoadRepairChecklistSample
import com.masusheet.app.viewmodel.onLoadSampleWorkbook
import com.masusheet.app.viewmodel.onMovePivotFieldInArea
import com.masusheet.app.viewmodel.onMoveSheet
import com.masusheet.app.viewmodel.onNavigateToAddressOrRange
import com.masusheet.app.viewmodel.onOpenChartAxisFormatDialog
import com.masusheet.app.viewmodel.onOpenChartSelectDataDialog
import com.masusheet.app.viewmodel.onOpenCreatePivotTableDialog
import com.masusheet.app.viewmodel.onOpenDocumentUriAsync
import com.masusheet.app.viewmodel.onOpenLicensesDialog
import com.masusheet.app.viewmodel.onOpenPivotChartPicker
import com.masusheet.app.viewmodel.onOpenZoomDialog
import com.masusheet.app.viewmodel.onRefreshPivotTable
import com.masusheet.app.viewmodel.onRemoveChartSeriesAt
import com.masusheet.app.viewmodel.onRemovePivotValueFieldAt
import com.masusheet.app.viewmodel.onRenameSheetAt
import com.masusheet.app.viewmodel.onReplaceAllCells
import com.masusheet.app.viewmodel.onReplaceCurrentCell
import com.masusheet.app.viewmodel.onResetSelectedImageAspectRatio
import com.masusheet.app.viewmodel.onResetSelectedObjectRotation
import com.masusheet.app.viewmodel.onResizeAiPanelWidth
import com.masusheet.app.viewmodel.onResizePivotPaneWidth
import com.masusheet.app.viewmodel.onRollbackLastMacroRun
import com.masusheet.app.viewmodel.onRotateSelectedObjectBy
import com.masusheet.app.viewmodel.onRunMacro
import com.masusheet.app.viewmodel.onSaveAiProviderSettings
import com.masusheet.app.viewmodel.onSaveOrUpdateMacro
import com.masusheet.app.viewmodel.onSelectActivePivotTable
import com.masusheet.app.viewmodel.onSelectAiEditMode
import com.masusheet.app.viewmodel.onSelectAiModel
import com.masusheet.app.viewmodel.onSelectChartXAxisColumn
import com.masusheet.app.viewmodel.onSelectExistingFileForSaveName
import com.masusheet.app.viewmodel.onSelectFindMatch
import com.masusheet.app.viewmodel.onSelectSaveDialogExtension
import com.masusheet.app.viewmodel.onSelectSaveDialogFolderAsync
import com.masusheet.app.viewmodel.onSelectSheetObject
import com.masusheet.app.viewmodel.onSendAiPrompt
import com.masusheet.app.viewmodel.onSetCellComment
import com.masusheet.app.viewmodel.onSetCellHyperlink
import com.masusheet.app.viewmodel.onSetPivotFilterValue
import com.masusheet.app.viewmodel.onSetPivotGrandTotalsMode
import com.masusheet.app.viewmodel.onSetPivotReportLayout
import com.masusheet.app.viewmodel.onSetPivotStylePreset
import com.masusheet.app.viewmodel.onSetPivotSubtotalsMode
import com.masusheet.app.viewmodel.onSetZoomPercent
import com.masusheet.app.viewmodel.onSortTableByColor
import com.masusheet.app.viewmodel.onSortTableByColumn
import com.masusheet.app.viewmodel.onStartChartAxisPickingOnSheet
import com.masusheet.app.viewmodel.onStartChartRangePickingOnSheet
import com.masusheet.app.viewmodel.onStartMacroRecording
import com.masusheet.app.viewmodel.onStartVbaDebug
import com.masusheet.app.viewmodel.onStepAiPlanQuestion
import com.masusheet.app.viewmodel.onStepSheet
import com.masusheet.app.viewmodel.onStepVbaDebug
import com.masusheet.app.viewmodel.onStopMacroRecording
import com.masusheet.app.viewmodel.onStopVbaDebug
import com.masusheet.app.viewmodel.onSwitchChartRowColumn
import com.masusheet.app.viewmodel.onSwitchOpenWorkbook
import com.masusheet.app.viewmodel.onToggleAiPanel
import com.masusheet.app.viewmodel.onToggleChartYAxisColumn
import com.masusheet.app.viewmodel.onToggleDarkTheme
import com.masusheet.app.viewmodel.onToggleLanguage
import com.masusheet.app.viewmodel.onToggleMacroRelativeMode
import com.masusheet.app.viewmodel.onToggleMaterial3Ui
import com.masusheet.app.viewmodel.onTogglePivotBandedCols
import com.masusheet.app.viewmodel.onTogglePivotBandedRows
import com.masusheet.app.viewmodel.onTogglePivotBlankRows
import com.masusheet.app.viewmodel.onTogglePivotFieldChecked
import com.masusheet.app.viewmodel.onTogglePivotFieldsPane
import com.masusheet.app.viewmodel.onTogglePivotRepeatLabels
import com.masusheet.app.viewmodel.onTogglePivotRowGroupCollapse
import com.masusheet.app.viewmodel.onToggleToolbar
import com.masusheet.app.viewmodel.onTriggerExportPdfAsync
import com.masusheet.app.viewmodel.onTriggerSaveAsync
import com.masusheet.app.viewmodel.onUndoLastAiEdit
import com.masusheet.app.viewmodel.onUpdateChartAxisFormat
import com.masusheet.app.viewmodel.onUpdateChartCustomSeriesAndCategories
import com.masusheet.app.viewmodel.onUpdateChartDataRange
import com.masusheet.app.viewmodel.onUpdateChartSeriesRanges
import com.masusheet.app.viewmodel.onUpdateSaveDialogBaseName
import com.masusheet.app.viewmodel.onUpdateSelectedObjectFillColor
import com.masusheet.app.viewmodel.onUpdateSelectedObjectStrokeColor
import com.masusheet.app.viewmodel.onUpdateSelectedObjectStrokeWidth
import com.masusheet.app.viewmodel.prepareWorkbookSessionForNewWindow

/**
 * ============================================================================
 * [EN] Main MasuSheet Compose Workspace Screen & Resizable Side Pane Layout
 * Implements [MasuSheetScreen] coordinating the top title/workbook-tab bar, 15-tab ribbon, formula bar, native 2-layer Canvas sheet container, docked AI/Pivot side panes, and modal dialogs.
 *
 * ---
 * [JA / 日本語解説]
 * 上部ブックタブバー・15タブリボン・数式バー・2層ネイティブCanvasシート領域・サイズ変更可能な右ペイン（AI編集／ピボット設定）・下部シートタブバー・各種ダイアログを統合するメイン画面 ([MasuSheetScreen]) です。
 * ============================================================================
 */

private class CreateTypedDocumentContract : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent {
        val (fileName, mimeType) = input
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    putExtra(
                        android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                        Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
                    )
                }
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
    }
}

private class OpenMultipleLocalDocumentsContract : ActivityResultContract<Array<String>, List<Uri>>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, input)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    putExtra(
                        android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                        Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
                    )
                }
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null) return emptyList()
        val uris = mutableListOf<Uri>()
        intent.data?.let { uris.add(it) }
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let { u ->
                    if (u !in uris) uris.add(u)
                }
            }
        }
        return uris
    }
}

/**
 * [EN] Top-level MasuSheet workspace composable assembling workbook tabs, 15-tab ribbon, formula bar, Canvas sheet, side panes, and dialogs.
 * [JA] ブックタブ・15タブリボン・数式バー・ネイティブCanvasシート・右サイドペイン・全モーダルダイアログを統合するメイン画面Composableです。
 */
@Composable
fun MasuSheetScreen(
    viewModel: MasuSheetViewModel,
    mainThreadPoster: (() -> Unit) -> Unit = { it() }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var sheetContainerRef by remember { mutableStateOf<MasuSheetNativeSheetContainer?>(null) }

    var formulaDraft by remember { mutableStateOf(state.viewport.activeEditText) }
    LaunchedEffect(state.viewport.activeAddress, state.viewport.revision, state.viewport.sheetIndex) {
        if (sheetContainerRef?.isEditingInCell != true) {
            formulaDraft = state.viewport.activeEditText
        }
    }

    fun commitActiveInCellEditIfNeeded() {
        sheetContainerRef?.let { container ->
            if (container.isEditingInCell) {
                container.commitAndCloseInCellEditor(0, 0)
            }
            if (container.isContextMenuVisible) {
                container.dismissContextMenu()
            }
        }
    }

    val latestFormulaDraft by rememberUpdatedState(formulaDraft)
    DisposableEffect(viewModel) {
        viewModel.beforeExternalNavigationCommitHook = {
            val wasEditingInCell = sheetContainerRef?.isEditingInCell == true
            commitActiveInCellEditIfNeeded()
            if (!wasEditingInCell) {
                val curVp = viewModel.uiState.value.viewport
                if (latestFormulaDraft != curVp.activeEditText) {
                    viewModel.onCommitCellEdit(
                        curVp.activeRow,
                        curVp.activeCol,
                        latestFormulaDraft,
                        moveDeltaRow = 0
                    )
                }
            }
        }
        onDispose {
            viewModel.beforeExternalNavigationCommitHook = null
        }
    }

    val openSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            commitActiveInCellEditIfNeeded()
            viewModel.onOpenDocumentUriAsync(uri.toString())
        }
    }

    val importExternalFilesLauncher = rememberLauncherForActivityResult(
        contract = OpenMultipleLocalDocumentsContract()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }
            viewModel.onSelectSaveDialogFolderAsync(viewModel.uiState.value.saveDialogFolderPath) {
                val firstAdded = viewModel.uiState.value.saveDialogExistingFiles.firstOrNull {
                    it.fullPath == uris.first().toString()
                }
                if (firstAdded != null) {
                    viewModel.onSelectExistingFileForSaveName(firstAdded)
                }
            }
        }
    }

    val createTypedSafLauncher = rememberLauncherForActivityResult(
        contract = CreateTypedDocumentContract()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onCompletePendingSaveAsync(uri.toString())
        } else {
            viewModel.onCancelPendingSave()
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            commitActiveInCellEditIfNeeded()
            viewModel.onInsertImageUriAsync(uri.toString())
        }
    }

    val openImageFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            commitActiveInCellEditIfNeeded()
            viewModel.onInsertImageUriAsync(uri.toString())
        }
    }

    val launchPickImage: () -> Unit = {
        runCatching {
            pickVisualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            runCatching { openImageFallbackLauncher.launch("image/*") }
        }
    }

    val openFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Ignore if provider does not support persistable permissions
            }
            viewModel.onSelectSaveDialogFolderAsync(treeUri.toString())
        }
    }

    val bgPrimary = when {
        state.isDarkTheme && state.isMaterial3Ui -> Color(0xFF1A2433)
        state.isDarkTheme -> Color(0xFF1E242B)
        state.isMaterial3Ui -> Color(0xFFF8FAFC)
        else -> Color(0xFFF3F2F1)
    }
    val surfaceBar = when {
        state.isDarkTheme && state.isMaterial3Ui -> Color(0xFF223044)
        state.isDarkTheme -> Color(0xFF262E38)
        state.isMaterial3Ui -> Color(0xFFFFFFFF)
        else -> Color(0xFFFAF9F8)
    }
    val surfaceHeader = when {
        state.isDarkTheme && state.isMaterial3Ui -> Color(0xFF1D293B)
        state.isDarkTheme -> Color(0xFF1B2128)
        state.isMaterial3Ui -> Color(0xFFEFF6FF)
        else -> Color(0xFFEDEBE9)
    }
    val borderCol = when {
        state.isDarkTheme && state.isMaterial3Ui -> Color(0xFF475B73)
        state.isDarkTheme -> Color(0xFF334155)
        state.isMaterial3Ui -> Color(0xFFCBD5E1)
        else -> Color(0xFFE1DFDD)
    }
    val textPrimary = if (state.isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF1E293B)

    // Dialog states for full Spreadsheet Parity & Automation
    var showColorPickerTarget by remember { mutableStateOf<String?>(null) }
    var showFunctionWizard by remember { mutableStateOf(false) }
    var showMacroStudio by remember { mutableStateOf(false) }
    var showCommandCenterMode by remember { mutableStateOf<String?>(null) }

    val launchOpenDocument: () -> Unit = {
        commitActiveInCellEditIfNeeded()
        openSafLauncher.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel.sheet.macroEnabled.12",
                "application/vnd.ms-excel.sheet.binary.macroEnabled.12",
                "application/vnd.oasis.opendocument.spreadsheet",
                "text/csv",
                "text/tab-separated-values",
                "*/*"
            )
        )
    }

    val triggerCheckboxToggleOrInsert: () -> Unit = {
        commitActiveInCellEditIfNeeded()
        val cur = viewModel.uiState.value.viewport.activeDisplayText
        val updated = if (InlineCheckboxHelper.containsCheckbox(cur)) {
            InlineCheckboxHelper.toggleNthCheckbox(cur, 0) ?: cur
        } else if (cur.isBlank()) {
            "□ チェック項目"
        } else {
            "□ $cur"
        }
        val vp = viewModel.uiState.value.viewport
        viewModel.onCommitCellEdit(vp.activeRow, vp.activeCol, updated)
    }

    val handleCopyAction: () -> Unit = {
        commitActiveInCellEditIfNeeded()
        val text = viewModel.onCopySelectionToClipboard()
        copyToSystemClipboard(context, text)
    }

    val handleCutAction: () -> Unit = {
        commitActiveInCellEditIfNeeded()
        val text = viewModel.onCutSelectionToClipboard()
        copyToSystemClipboard(context, text)
    }

    val handlePasteAction: () -> Unit = {
        commitActiveInCellEditIfNeeded()
        val clipImage = readImageBytesFromSystemClipboard(context)
        if (clipImage != null) {
            viewModel.onInsertImageBytes(clipImage.first, clipImage.second)
        } else {
            val clipText = readFromSystemClipboard(context)
            if (clipText != null && clipText.isNotEmpty()) {
                viewModel.onPasteTextFromClipboard(clipText)
            } else {
                viewModel.onPasteTextFromClipboard(null)
            }
        }
    }

    val handlePasteScreenshotOrImageAction: () -> Unit = {
        commitActiveInCellEditIfNeeded()
        val clipImage = readImageBytesFromSystemClipboard(context)
        if (clipImage != null) {
            viewModel.onInsertImageBytes(clipImage.first, clipImage.second)
        } else {
            launchPickImage()
        }
    }

    CompositionLocalProvider(
        LocalAppLanguage provides state.language,
        LocalMaterial3Ui provides state.isMaterial3Ui,
        LocalDarkTheme provides state.isDarkTheme
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgPrimary)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgPrimary)
            ) {
                // 1. Top Full Multi-Tab Ribbon (All 15 Spreadsheet Ribbon Tabs & 292 Commands)
                MasuSheetFullRibbon(
                    state = state,
                    surfaceHeader = surfaceHeader,
                    surfaceBar = surfaceBar,
                    borderCol = borderCol,
                    textPrimary = textPrimary,
                    onSelectTab = { tabId ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onSelectRibbonTab(tabId)
                    },
                    onCommand = { cmd, params ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onExecuteEngineCommand(cmd, params)
                    },
                    onOpenDocumentClick = launchOpenDocument,
                    onSaveClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onTriggerSaveAsync()
                    },
                    onExportPdfClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onTriggerExportPdfAsync()
                    },
                    onLoadSampleClick = { sample ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onLoadSampleWorkbook(sample)
                    },
                    onNextSampleClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onLoadNextSample()
                    },
                    onRepairSampleClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onLoadRepairChecklistSample()
                    },
                    onInsertCheckboxClick = triggerCheckboxToggleOrInsert,
                    onInsertTemplateText = { templateText ->
                        commitActiveInCellEditIfNeeded()
                        val vp = viewModel.uiState.value.viewport
                        viewModel.onCommitCellEdit(vp.activeRow, vp.activeCol, templateText)
                    },
                    onAutoFitColClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onAutoFitColumn(viewModel.uiState.value.viewport.activeCol)
                    },
                    onAutoFitRowClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onAutoFitRow(viewModel.uiState.value.viewport.activeRow)
                    },
                    onResizeColDeltaClick = { deltaPt ->
                        commitActiveInCellEditIfNeeded()
                        val vp = viewModel.uiState.value.viewport
                        val curW = vp.colWidths.getOrElse(vp.activeCol) { 72f }
                        viewModel.onResizeColumn(vp.activeCol, curW + deltaPt)
                    },
                    onResizeRowDeltaClick = { deltaPt ->
                        commitActiveInCellEditIfNeeded()
                        val vp = viewModel.uiState.value.viewport
                        val curH = vp.rowHeights.getOrElse(vp.activeRow) { 22f }
                        viewModel.onResizeRow(vp.activeRow, curH + deltaPt)
                    },
                    onAddSheetClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onAddSheet()
                    },
                    onToggleLanguageClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onToggleLanguage()
                    },
                    onToggleThemeClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onToggleDarkTheme()
                    },
                    onToggleMaterial3UiClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onToggleMaterial3Ui()
                    },
                    onCycleZoomClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onCycleZoom()
                    },
                    onToggleToolbarClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onToggleToolbar()
                    },
                    onDismissErrorClick = {
                        viewModel.onDismissError()
                    },
                    onCopyClick = handleCopyAction,
                    onCutClick = handleCutAction,
                    onPasteClick = handlePasteAction,
                    onInsertImageClick = {
                        commitActiveInCellEditIfNeeded()
                        launchPickImage()
                    },
                    onPasteScreenshotClick = handlePasteScreenshotOrImageAction,
                    onInsertShapeClick = { kind, label ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onInsertShape(kind, label)
                    },
                    onInsertTextBoxClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onInsertTextBox("")
                    },
                    onInsertChartClick = { chartType ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onInsertChart(chartType)
                    },
                    onDeleteSelectedObjectClick = {
                        commitActiveInCellEditIfNeeded()
                        state.selectedObjectId?.let { id -> viewModel.onDeleteSheetObject(id) }
                    },
                    onConvertToRangeClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onConvertToNormalRange()
                    },
                    onUpdateObjectFillColor = { color ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onUpdateSelectedObjectFillColor(color)
                    },
                    onUpdateObjectStrokeColor = { color ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onUpdateSelectedObjectStrokeColor(color)
                    },
                    onUpdateObjectStrokeWidth = { widthPt ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onUpdateSelectedObjectStrokeWidth(widthPt)
                    },
                    onRotateObjectBy = { deltaDeg ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onRotateSelectedObjectBy(deltaDeg)
                    },
                    onResetObjectRotation = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onResetSelectedObjectRotation()
                    },
                    onResetObjectAspectRatio = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onResetSelectedImageAspectRatio()
                    },
                    onOpenZoomDialogClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onOpenZoomDialog(true)
                    },
                    onEditSelectedObjectInPlace = {
                        val selectedObj = state.sheetObjects.firstOrNull { it.id == state.selectedObjectId }
                            ?: state.sheetObjects.lastOrNull { it.sheetIndex == state.viewport.sheetIndex }
                        if (selectedObj != null) {
                            sheetContainerRef?.openInObjectEditorForSelected(selectedObj)
                        }
                    },
                    onOpenColorPicker = { target ->
                        commitActiveInCellEditIfNeeded()
                        showColorPickerTarget = target
                    },
                    onOpenFunctionWizard = {
                        commitActiveInCellEditIfNeeded()
                        showFunctionWizard = true
                    },
                    onOpenMacroStudio = {
                        commitActiveInCellEditIfNeeded()
                        showMacroStudio = true
                    },
                    onOpenCommandCenter = { mode ->
                        commitActiveInCellEditIfNeeded()
                        showCommandCenterMode = mode
                    },
                    onToggleAiPanel = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onToggleAiPanel()
                    },
                    onToggleMacroRecording = {
                        commitActiveInCellEditIfNeeded()
                        if (state.isMacroRecording) {
                            viewModel.onStopMacroRecording()
                        } else {
                            viewModel.onStartMacroRecording("Macro${state.savedMacros.size + 1}")
                        }
                    },
                    onOpenNewWindowClick = { blank ->
                        commitActiveInCellEditIfNeeded()
                        launchNewWorkbookWindow(context, blankWorkbook = blank)
                    },
                    onToggleSplitBookViewClick = {
                        // [EN] Side-by-side comparison is handled via SecondaryWorkbookActivity (:window2 process)
                        //      so each window has its own full native canvas and isolated Rust engine instance.
                        // [JA] 2画面同時表示は独立プロセス (:window2) の別ウィンドウで完全描画されるよう統合。
                        commitActiveInCellEditIfNeeded()
                        launchNewWorkbookWindow(context, blankWorkbook = false)
                    },
                    onSwitchOpenWorkbookClick = { sessionId ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onSwitchOpenWorkbook(sessionId)
                    },
                    onCloseOpenWorkbookClick = { sessionId ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onCloseOpenWorkbook(sessionId)
                    },
                    onMoveWorkbookToNewWindowClick = { sessionId, removeFromCurrent ->
                        commitActiveInCellEditIfNeeded()
                        val session = viewModel.prepareWorkbookSessionForNewWindow(
                            workbookId = sessionId,
                            removeFromCurrentWindow = removeFromCurrent
                        )
                        if (session != null) {
                            launchWorkbookSessionInNewWindow(context, session)
                        }
                    },
                    isSecondaryWindow = context is SecondaryWorkbookActivity,
                    onMergeWorkbookToMainWindowClick = { sessionId ->
                        commitActiveInCellEditIfNeeded()
                        val isLastTabInWindow = state.openWorkbooks.size <= 1
                        val session = viewModel.prepareWorkbookSessionForNewWindow(
                            workbookId = sessionId,
                            removeFromCurrentWindow = !isLastTabInWindow
                        )
                        if (session != null) {
                            mergeWorkbookSessionIntoMainWindow(
                                context = context,
                                session = session,
                                finishCurrentWindow = isLastTabInWindow
                            )
                        }
                    },
                    onNewBlankWorkbookClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onCreateNewBlankWorkbookSession()
                    },
                    onOpenChartSelectDataClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onOpenChartSelectDataDialog(open = true)
                    }
                )

                // 2. Native Formula Bar with Interactive Name Box (A1), Clickable fx Button & Live 523-Function Autocomplete
                AnimatedVisibility(visible = state.formulaBarVisible) {
                    val colAutoComplete = remember(formulaDraft, state.viewport.activeRow, state.viewport.activeCol, state.viewport.revision) {
                        viewModel.computeColumnAutoCompleteSuggestion(
                            row = state.viewport.activeRow,
                            col = state.viewport.activeCol,
                            typedPrefix = formulaDraft
                        )
                    }
                    MasuSheetNativeFormulaBar(
                        activeAddress = state.viewport.activeAddress,
                        formulaText = formulaDraft,
                        activeSpillFormula = state.viewport.activeSpillFormula,
                        columnAutoCompleteSuggestion = colAutoComplete,
                        isDarkTheme = state.isDarkTheme,
                        surfaceBar = surfaceBar,
                        borderCol = borderCol,
                        textPrimary = textPrimary,
                        onFormulaChange = {
                            formulaDraft = it
                            sheetContainerRef?.syncFromFormulaBar(it)
                        },
                        onCommitFormula = {
                            if (sheetContainerRef?.isEditingInCell == true) {
                                sheetContainerRef?.commitAndCloseInCellEditor(moveDeltaRow = 1, moveDeltaCol = 0)
                            } else {
                                viewModel.onCommitCellEdit(
                                    state.viewport.activeRow,
                                    state.viewport.activeCol,
                                    formulaDraft,
                                    moveDeltaRow = 1
                                )
                            }
                        },
                        onCancelFormula = {
                            sheetContainerRef?.cancelInCellEditor()
                            formulaDraft = state.viewport.activeEditText
                        },
                        onOpenFunctionWizard = {
                            commitActiveInCellEditIfNeeded()
                            showFunctionWizard = true
                        },
                        onNavigateToAddress = { addrText ->
                            commitActiveInCellEditIfNeeded()
                            viewModel.onNavigateToAddressOrRange(addrText)
                        },
                        isRibbonVisible = state.toolbarVisible,
                        onToggleRibbonClick = {
                            commitActiveInCellEditIfNeeded()
                            viewModel.onToggleToolbar()
                        }
                    )
                }

                // Fallback minimal restore strip when BOTH Ribbon and Formula Bar are hidden
                if (!state.toolbarVisible && !state.formulaBarVisible) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(surfaceHeader)
                            .border(width = 0.5.dp, color = borderCol)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MasuSheetPillButton(
                            label = "▼ リボン表示",
                            highlight = true,
                            tag = "ms_btn_expand",
                            onClick = {
                                commitActiveInCellEditIfNeeded()
                                viewModel.onToggleToolbar()
                            }
                        )
                        Text(
                            text = "📊 ${state.workbookTitle}",
                            color = textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 3. Main Workspace Row: Spreadsheet Canvas (left) + Optional Split Book View + AI Spreadsheet Assistant Panel (right when open)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("ms_native_sheet_container"),
                            factory = { ctx ->
                                MasuSheetNativeSheetContainer(ctx, viewModel).also { container ->
                                    sheetContainerRef = container
                                    container.onLiveEditorTextChanged = { liveText -> formulaDraft = liveText }
                                    container.onOpenColorPickerRequest = { target -> showColorPickerTarget = target }
                                    container.onOpenFunctionWizardRequest = { showFunctionWizard = true }
                                    container.onOpenMacroStudioRequest = { showMacroStudio = true }
                                    container.onOpenCommandCenterRequest = { mode -> showCommandCenterMode = mode }
                                    container.onRequestOpenImagePicker = { launchPickImage() }
                                    container.onOpenAiAssistantWithPromptRequest = { prompt ->
                                        viewModel.onToggleAiPanel(true)
                                        if (prompt.isNotBlank()) {
                                            viewModel.onSendAiPrompt(prompt)
                                        }
                                    }
                                }
                            },
                            update = { container ->
                                sheetContainerRef = container
                                container.onLiveEditorTextChanged = { liveText -> formulaDraft = liveText }
                                container.onOpenColorPickerRequest = { target -> showColorPickerTarget = target }
                                container.onOpenFunctionWizardRequest = { showFunctionWizard = true }
                                container.onOpenMacroStudioRequest = { showMacroStudio = true }
                                container.onOpenCommandCenterRequest = { mode -> showCommandCenterMode = mode }
                                container.onRequestOpenImagePicker = { launchPickImage() }
                                container.onOpenAiAssistantWithPromptRequest = { prompt ->
                                    viewModel.onToggleAiPanel(true)
                                    if (prompt.isNotBlank()) {
                                        viewModel.onSendAiPrompt(prompt)
                                    }
                                }
                                container.bindState(state)
                            }
                        )

                        // Floating Interactive Chart Data Range Picking Banner (when user chooses "シート上をドラッグしてデータ範囲を選択")
                        if (state.chartRangePickingObjectId != null) {
                            val isEn = state.language == "en"
                            val vp = state.viewport
                            val selRef = "${MasuSheetConfig.formatCellAddress(minOf(vp.selStartRow, vp.selEndRow), minOf(vp.selStartCol, vp.selEndCol))}:${MasuSheetConfig.formatCellAddress(maxOf(vp.selStartRow, vp.selEndRow), maxOf(vp.selStartCol, vp.selEndCol))}"
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xF00F172A))
                                    .border(1.8.dp, Color(0xFF4ADE80), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isEn) {
                                        "🎯 Selecting Chart Data Range: Drag across cells on sheet (Current: $selRef)"
                                    } else {
                                        "🎯 グラフのデータ範囲を選択中: シート上の表をドラッグしてください (現在: $selRef)"
                                    },
                                    color = Color(0xFF4ADE80),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                MasuSheetPillButton(
                                    label = if (isEn) "✓ Confirm Range" else "✓ この範囲で確定",
                                    highlight = true,
                                    onClick = {
                                        viewModel.onBindCurrentSelectionToChart(state.chartRangePickingObjectId)
                                        viewModel.onFinishChartRangePickingOnSheet()
                                    }
                                )
                                MasuSheetPillButton(
                                    label = if (isEn) "⚙ Select Data Dialog" else "⚙ 詳細設定へ戻る",
                                    onClick = {
                                        val cid = state.chartRangePickingObjectId
                                        viewModel.onFinishChartRangePickingOnSheet()
                                        viewModel.onOpenChartSelectDataDialog(open = true, objectId = cid)
                                    }
                                )
                            }
                        }

                        // Floating Macro Recording Badge (Top-Right over Sheet Canvas when recording)
                        if (state.isMacroRecording) {
                            val isEn = state.language == "en"
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xEE7F1D1D))
                                    .border(1.5.dp, Color(0xFFF87171), RoundedCornerShape(18.dp))
                                    .clickable { viewModel.onStopMacroRecording() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isEn) {
                                        "🔴 Recording: ${state.currentRecordingName} (${state.currentRecordingSteps.size} steps) — Tap to Stop"
                                    } else {
                                        "🔴 マクロ記録中: ${state.currentRecordingName} (${state.currentRecordingSteps.size}手) — タップで停止・保存"
                                    },
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Floating Top-Right PivotTable Fields Pane Launcher (shown only when active cell is inside a PivotTable range and not dismissed)
                        val activePivotOnCurrentSheet = state.pivotTables.firstOrNull {
                            it.containsCell(state.viewport.sheetIndex, state.viewport.activeRow, state.viewport.activeCol) &&
                                it.id !in state.dismissedPivotLauncherIds
                        }
                        if (!state.isPivotFieldsPaneOpen && activePivotOnCurrentSheet != null) {
                            val isEn = state.language == "en"
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 10.dp, end = 14.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF1E3A8A))
                                    .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(20.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .testTag("ms_btn_pivot_pane_fab")
                                        .clickable {
                                            commitActiveInCellEditIfNeeded()
                                            viewModel.onTogglePivotFieldsPane(true)
                                        }
                                        .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isEn) {
                                            "📊 Open PivotTable Fields (${activePivotOnCurrentSheet.name})"
                                        } else {
                                            "📊 ピボットテーブルのフィールドを開く (${activePivotOnCurrentSheet.name})"
                                        },
                                        color = Color(0xFFF8FAFC),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .testTag("ms_btn_pivot_pane_fab_close")
                                        .clickable {
                                            commitActiveInCellEditIfNeeded()
                                            viewModel.onDismissPivotLauncher(activePivotOnCurrentSheet.id)
                                        }
                                        .padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✕",
                                        color = Color(0xFFBAE6FD),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Floating Bottom-Right AI Assistant Button (Floating AI Assistant launcher)
                        if (!state.isAiPanelOpen) {
                            val isEn = state.language == "en"
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(14.dp)
                                    .testTag("ms_btn_ai_fab")
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFF163828))
                                    .border(1.5.dp, Color(0xFF4ADE80), RoundedCornerShape(22.dp))
                                    .clickable {
                                        commitActiveInCellEditIfNeeded()
                                        viewModel.onToggleAiPanel(true)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = SpreadsheetI18n.t("✨ AI アシスタント", isEn),
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Right-Side Docked PivotTable Fields Task Pane (Microsoft Spreadsheet Desktop style)
                    AnimatedVisibility(visible = state.isPivotFieldsPaneOpen) {
                        val activePivot = viewModel.getActivePivotTable()
                        Row(modifier = Modifier.fillMaxHeight()) {
                            SpreadsheetResizableRightPaneDivider(
                                currentWidthDp = state.pivotPaneWidthDp,
                                defaultWidthDp = 344f,
                                minWidthDp = 220f,
                                maxWidthDp = 760f,
                                accentColor = Color(0xFF38BDF8),
                                isDarkTheme = state.isDarkTheme,
                                testTag = "ms_pivot_pane_resize_divider",
                                onWidthChange = { viewModel.onResizePivotPaneWidth(it) }
                            )
                            SpreadsheetPivotTableFieldsRightPane(
                                state = state,
                                activePivot = activePivot,
                                onSelectPivot = { pivotId ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onSelectActivePivotTable(pivotId)
                                },
                                onOpenCreateDialog = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onOpenCreatePivotTableDialog(true)
                                },
                                onRefreshPivot = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onRefreshPivotTable(refreshAll = true)
                                },
                                onClearPivot = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onClearPivotTable()
                                },
                                onDeletePivot = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onDeletePivotTable()
                                },
                                onInsertPivotChart = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onExecuteEngineCommand("pivotAnalyze.pivotChart", "{}")
                                },
                                onApplyQuickPreset = { presetKind ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onCreatePivotTable(placeOnNewSheet = false, presetKind = presetKind)
                                },
                                onToggleFieldChecked = { fieldName ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onTogglePivotFieldChecked(fieldName)
                                },
                                onAssignFieldToArea = { fieldName, area ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onAssignPivotFieldToArea(fieldName, area)
                                },
                                onMoveFieldInArea = { area, fieldName, delta ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onMovePivotFieldInArea(area, fieldName, delta)
                                },
                                onRemoveValueFieldAt = { vIdx ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onRemovePivotValueFieldAt(vIdx)
                                },
                                onCycleValueFieldAgg = { fieldName, explicitAgg, vIdx ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onCyclePivotValueFieldAgg(fieldName, explicitAgg, vIdx)
                                },
                                onSetFilterValue = { fieldName, filterValue ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onSetPivotFilterValue(fieldName, filterValue)
                                },
                                onToggleRowGroupCollapse = { groupKey ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onTogglePivotRowGroupCollapse(groupKey)
                                },
                                onSetStylePreset = { stylePreset ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onSetPivotStylePreset(stylePreset)
                                },
                                onSetReportLayout = { layout ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onSetPivotReportLayout(layout)
                                },
                                onSetSubtotalsMode = { mode ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onSetPivotSubtotalsMode(mode)
                                },
                                onSetGrandTotalsMode = { mode ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onSetPivotGrandTotalsMode(mode)
                                },
                                onToggleRepeatLabels = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onTogglePivotRepeatLabels()
                                },
                                onToggleBlankRows = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onTogglePivotBlankRows()
                                },
                                onToggleBandedRows = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onTogglePivotBandedRows()
                                },
                                onToggleBandedCols = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onTogglePivotBandedCols()
                                },
                                onClosePane = {
                                    viewModel.onTogglePivotFieldsPane(false)
                                }
                            )
                        }
                    }

                    // [EN] Note: Degraded in-window SplitWorkbookComparisonPane has been removed in favor of
                    //      full-featured multi-window via SecondaryWorkbookActivity (isolated in :window2 process).
                    // [JA] 簡易テーブル表示だったウィンドウ内スプリット表示を廃止し、フル機能・独立プロセスの「別ウィンドウ」に一本化。

                    // Right-Side Docked AI Spreadsheet Assistant Panel
                    AnimatedVisibility(visible = state.isAiPanelOpen) {
                        Row(modifier = Modifier.fillMaxHeight()) {
                            SpreadsheetResizableRightPaneDivider(
                                currentWidthDp = state.aiPanelWidthDp,
                                defaultWidthDp = 380f,
                                minWidthDp = 220f,
                                maxWidthDp = 820f,
                                accentColor = Color(0xFF38BDF8),
                                isDarkTheme = state.isDarkTheme,
                                testTag = "ms_ai_pane_resize_divider",
                                onWidthChange = { viewModel.onResizeAiPanelWidth(it) }
                            )
                            AiSpreadsheetAssistantPanel(
                                state = state,
                                initialApiKey = viewModel.getAiApiKeyForModel(state.selectedAiModelId),
                                initialCustomEndpoint = viewModel.getAiCustomEndpoint(),
                                initialCustomModel = viewModel.getAiCustomModelName(state.selectedAiModelId),
                                onSelectModel = { viewModel.onSelectAiModel(it) },
                                onSelectEditMode = { viewModel.onSelectAiEditMode(it) },
                                onSaveProviderSettings = { modelId, apiKey, endpoint, modelName ->
                                    viewModel.onSaveAiProviderSettings(modelId, apiKey, endpoint, modelName)
                                },
                                onSendPrompt = { prompt ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onSendAiPrompt(prompt)
                                },
                                onAnswerPlanQuestion = { qIdx, optIdx, customText ->
                                    viewModel.onAnswerAiPlanQuestion(qIdx, optIdx, customText)
                                },
                                onStepPlanQuestion = { delta ->
                                    viewModel.onStepAiPlanQuestion(delta)
                                },
                                onExecuteConfirmedPlan = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onExecuteConfirmedAiPlan()
                                },
                                onCancelPlan = { viewModel.onCancelAiPlan() },
                                onUndoLastAiEdit = {
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onUndoLastAiEdit()
                                },
                                onClosePanel = { viewModel.onToggleAiPanel(false) },
                                onCopyTextToClipboard = { text -> copyToSystemClipboard(context, text) },
                                onImportWebAiJson = { raw ->
                                    commitActiveInCellEditIfNeeded()
                                    viewModel.onImportWebAiJsonResponse(raw)
                                }
                            )
                        }
                    }
                }

                // 4. Native Sheet Tabs & Spreadsheet Status Bar (with Right-Click Sheet Tab Menu, AutoCalculate Summary & Zoom)
                MasuSheetNativeSheetTabsBar(
                    state = state,
                    surfaceBar = surfaceBar,
                    borderCol = borderCol,
                    textPrimary = textPrimary,
                    onSelectSheet = { idx ->
                        val container = sheetContainerRef
                        if (container != null && container.canPointFormulaReference()) {
                            // Keep formula editor open across sheet switch so clicking a cell on the target sheet inserts SheetName!A1!
                            viewModel.onSelectSheet(idx)
                        } else {
                            commitActiveInCellEditIfNeeded()
                            viewModel.onSelectSheet(idx)
                        }
                    },
                    onAddSheet = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onAddSheet()
                    },
                    onStepSheet = { delta ->
                        val container = sheetContainerRef
                        if (container != null && container.canPointFormulaReference()) {
                            viewModel.onStepSheet(delta)
                        } else {
                            commitActiveInCellEditIfNeeded()
                            viewModel.onStepSheet(delta)
                        }
                    },
                    onInsertSheetBefore = { idx ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onInsertSheetBefore(idx)
                    },
                    onDeleteSheetAt = { idx ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onDeleteSheetAt(idx)
                    },
                    onRenameSheetAt = { idx, newName ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onRenameSheetAt(idx, newName)
                    },
                    onDuplicateSheetAt = { idx ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onDuplicateSheetAt(idx)
                    },
                    onMoveSheetAt = { fromIdx, toIdx ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onMoveSheet(fromIdx, toIdx)
                    },
                    onHideSheetAt = { idx ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onHideSheetAt(idx)
                    },
                    onUnhideSheet = { name ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onUnhideSheet(name)
                    },
                    onOpenSheetColorPicker = {
                        commitActiveInCellEditIfNeeded()
                        showColorPickerTarget = "sheetTab"
                    },
                    onOpenSheetManager = {
                        commitActiveInCellEditIfNeeded()
                        showCommandCenterMode = "sheetManage"
                    },
                    onOpenMacroStudio = {
                        commitActiveInCellEditIfNeeded()
                        showMacroStudio = true
                    },
                    onExecuteCommand = { cmd, params ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onExecuteEngineCommand(cmd, params)
                    },
                    onCycleZoom = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onCycleZoom()
                    },
                    onSetZoom = { pct ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onSetZoomPercent(pct)
                    },
                    onOpenZoomDialog = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onOpenZoomDialog(true)
                    },
                    onCopyLinkToClipboard = { linkText ->
                        copyToSystemClipboard(context, linkText)
                    }
                )
            }

            MasuSheetWorkspaceDialogsHost(
                state = state,
                viewModel = viewModel,
                colorPickerTarget = showColorPickerTarget,
                onSetColorPickerTarget = { showColorPickerTarget = it },
                showFunctionWizard = showFunctionWizard,
                onDismissFunctionWizard = { showFunctionWizard = false },
                onUpdateFormulaDraft = { formulaDraft = it },
                showMacroStudio = showMacroStudio,
                onDismissMacroStudio = { showMacroStudio = false },
                commandCenterMode = showCommandCenterMode,
                onSetCommandCenterMode = { showCommandCenterMode = it },
                onCommitActiveInCellEdit = { commitActiveInCellEditIfNeeded() },
                onLaunchFolderTree = { openFolderTreeLauncher.launch(it) },
                onLaunchImportExternalFiles = { importExternalFilesLauncher.launch(it) },
                onLaunchCreateTypedSaf = { name, mime -> createTypedSafLauncher.launch(name to mime) }
            )
        }
    }
}

internal val RIBBON_TABS = SpreadsheetRibbonCatalog.ALL_15_RIBBON_TABS
