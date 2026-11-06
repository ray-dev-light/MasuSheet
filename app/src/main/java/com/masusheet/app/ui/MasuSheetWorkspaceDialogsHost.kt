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
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.viewmodel.prepareWorkbookSessionForNewWindow

/**
 * ============================================================================
 * [EN] Workspace Modal Dialogs Host & Resizable Right-Pane Splitter
 * Hosts all modal dialogs (Save, 60-Color Picker, OSS Licenses, PivotTable, 523-Function Wizard, Macro Studio, Command Center, AutoFilter, Chart Picker/Data/Axis, Zoom) and [SpreadsheetResizableRightPaneDivider].
 *
 * ---
 * [JA / 日本語解説]
 * ワークスペース上の各種モーダルダイアログ（保存・60色パレット・OSSライセンス・ピボット作成・523関数ウィザード・マクロスタジオ・コマンドセンター・フィルタ・グラフ設定・表示倍率）と右ペイン幅調整バー ([SpreadsheetResizableRightPaneDivider]) を管理します。
 * ============================================================================
 */

/**
 * [EN] Renders active workspace modal dialogs above the sheet canvas.
 * [JA] 現在開かれている各種モーダルダイアログを描画・結線します。
 */
@Composable
internal fun MasuSheetWorkspaceDialogsHost(
    state: MasuSheetUiState,
    viewModel: MasuSheetViewModel,
    colorPickerTarget: String?,
    onSetColorPickerTarget: (String?) -> Unit,
    showFunctionWizard: Boolean,
    onDismissFunctionWizard: () -> Unit,
    onUpdateFormulaDraft: (String) -> Unit,
    showMacroStudio: Boolean,
    onDismissMacroStudio: () -> Unit,
    commandCenterMode: String?,
    onSetCommandCenterMode: (String?) -> Unit,
    onCommitActiveInCellEdit: () -> Unit,
    onLaunchFolderTree: (Uri) -> Unit,
    onLaunchImportExternalFiles: (Array<String>) -> Unit,
    onLaunchCreateTypedSaf: (String, String) -> Unit
) {
    fun commitActiveInCellEditIfNeeded() = onCommitActiveInCellEdit()
    // 5. Save File Dialog Overlay (Selectable Extension + Clickable Existing Folder Files — Never saves as .bin!)
    if (state.isSaveDialogVisible) {
        MasuSheetSaveDialog(
            state = state,
            onUpdateBaseName = { viewModel.onUpdateSaveDialogBaseName(it) },
            onSelectExtension = { viewModel.onSelectSaveDialogExtension(it) },
            onSelectFolder = { viewModel.onSelectSaveDialogFolderAsync(it) },
            onPickCustomFolder = {
                val initialDownloadUri = Uri.parse(
                    "content://com.android.externalstorage.documents/document/primary%3ADownload"
                )
                onLaunchFolderTree(initialDownloadUri)
            },
            onImportExternalFiles = {
                onLaunchImportExternalFiles(
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
            },
            onSelectExistingFile = { viewModel.onSelectExistingFileForSaveName(it) },
            onOpenExistingFile = { item ->
                viewModel.onDismissSaveDialog()
                val uriStr = if (item.fullPath.startsWith("content://") || item.fullPath.startsWith("file://")) {
                    item.fullPath
                } else {
                    Uri.fromFile(java.io.File(item.fullPath)).toString()
                }
                viewModel.onOpenDocumentUriAsync(uriStr)
            },
            onSaveDirectly = {
                viewModel.onConfirmSaveDirectlyToFolderAsync()
                true
            },
            onOpenSystemPicker = {
                val fullFileName = MasuSheetConfig.buildFileNameWithExtension(
                    state.saveDialogBaseName,
                    state.saveDialogExtension
                )
                val mimeType = MasuSheetConfig.resolveExportMimeType(fullFileName)
                onLaunchCreateTypedSaf(fullFileName, mimeType)
            },
            onDismiss = { viewModel.onDismissSaveDialog() }
        )
    }

    // 6. Full Spreadsheet Color Picker Dialog (Theme 60 Colors + Standard 10 + Recent + Custom 24-bit RGB/Hex + Target Selector)
    val activeColorTarget = colorPickerTarget
    if (activeColorTarget != null) {
        val currentSheetObjs = viewModel.getMaterializedSheetObjectsForCurrentSheet()
        SpreadsheetColorPickerDialog(
            state = state,
            initialTarget = activeColorTarget,
            sheetObjectsOnCurrentSheet = currentSheetObjs,
            onSelectObject = { objId ->
                commitActiveInCellEditIfNeeded()
                viewModel.onSelectSheetObject(objId)
            },
            onSelectSheetTab = { sheetIdx ->
                commitActiveInCellEditIfNeeded()
                viewModel.onSelectSheet(sheetIdx)
            },
            onApplyColor = { target, argbColor ->
                commitActiveInCellEditIfNeeded()
                viewModel.onApplyPickedColor(target, argbColor)
            },
            onApplyThemePreset = { presetName ->
                commitActiveInCellEditIfNeeded()
                viewModel.onApplyThemePreset(presetName)
            },
            onDismiss = { onSetColorPickerTarget(null) }
        )
    }

    // 6b. Create PivotTable / Recommended PivotTables Dialog
    if (state.isOpenSourceLicensesDialogOpen) {
        SpreadsheetOpenSourceLicensesDialog(
            isEn = state.language == "en",
            onDismiss = { viewModel.onOpenLicensesDialog(false) }
        )
    }

    if (state.isCreatePivotDialogOpen) {
        val activePivot = viewModel.getActivePivotTable()
        val defaultSrc = activePivot?.let {
            "${MasuSheetConfig.formatCellAddress(it.sourceStartRow, it.sourceStartCol)}:${MasuSheetConfig.formatCellAddress(it.sourceEndRow, it.sourceEndCol)}"
        } ?: if (state.viewport.sheetName == "月間予算") "A11:F24" else "A2:F6"
        val defaultDest = activePivot?.let {
            MasuSheetConfig.formatCellAddress(it.targetStartRow, it.targetStartCol)
        } ?: if (state.viewport.sheetName == "月間予算") "H11" else "H2"
        SpreadsheetCreatePivotTableDialog(
            state = state,
            defaultSourceRange = defaultSrc,
            defaultTargetAddress = defaultDest,
            onCreatePivot = { srcRange, placeOnNewSheet, targetAddr, presetKind ->
                commitActiveInCellEditIfNeeded()
                viewModel.onCreatePivotTable(
                    sourceRangeText = srcRange,
                    placeOnNewSheet = placeOnNewSheet,
                    targetAddress = targetAddr,
                    presetKind = presetKind
                )
            },
            onDismiss = { viewModel.onOpenCreatePivotTableDialog(false) }
        )
    }

    // 7. Full 523-Function Spreadsheet Wizard Dialog
    if (showFunctionWizard) {
        SpreadsheetFunctionWizardDialog(
            activeAddress = state.viewport.activeAddress,
            onInsertFormula = { formula ->
                onUpdateFormulaDraft(formula)
                viewModel.onCommitCellEdit(
                    state.viewport.activeRow,
                    state.viewport.activeCol,
                    formula,
                    moveDeltaRow = 1
                )
            },
            onDismiss = onDismissFunctionWizard,
            isEn = state.language == "en"
        )
    }

    // 8. All-in-One Macro & VBA Studio Dialog (Recorder + VBA Editor/Step Debugger + Natural Language Generator)
    if (showMacroStudio) {
        MacroVbaStudioDialog(
            state = state,
            onStartRecording = { name, shortcut, desc, relative ->
                viewModel.onStartMacroRecording(name, shortcut, desc, relative)
            },
            onStopRecording = { viewModel.onStopMacroRecording() },
            onToggleRelative = { viewModel.onToggleMacroRelativeMode() },
            onSaveMacro = { viewModel.onSaveOrUpdateMacro(it) },
            onDeleteMacro = { viewModel.onDeleteMacro(it) },
            onRunMacro = { viewModel.onRunMacro(it) },
            onStartDebug = { macroName, vbaCode -> viewModel.onStartVbaDebug(macroName, vbaCode) },
            onStepDebug = { viewModel.onStepVbaDebug() },
            onStopDebug = { viewModel.onStopVbaDebug() },
            onRollbackLastRun = { viewModel.onRollbackLastMacroRun() },
            onGenerateVbaFromPrompt = { prompt -> viewModel.onGenerateVbaFromPrompt(prompt) },
            onDismiss = onDismissMacroStudio
        )
    }

    // 9. Spreadsheet Command Center Dialog (All 292 Commands, Find/Replace, Go To, Conditional Formatting, Data Validation, Goal Seek, Sheet Manager)
    val activeCommandCenter = commandCenterMode
    if (activeCommandCenter != null) {
        SpreadsheetCommandCenterDialog(
            state = state,
            initialMode = activeCommandCenter,
            onExecuteCommand = { cmd, params ->
                commitActiveInCellEditIfNeeded()
                viewModel.onExecuteEngineCommand(cmd, params)
            },
            onOpenColorPickerForSheetTab = {
                onSetCommandCenterMode(null)
                onSetColorPickerTarget("sheetTab")
            },
            onFindNext = { q, fwd, matchCase, wholeCell ->
                commitActiveInCellEditIfNeeded()
                viewModel.onFindNext(q, fwd, matchCase, wholeCell)
            },
            onFindAll = { q, matchCase, wholeCell ->
                commitActiveInCellEditIfNeeded()
                viewModel.onFindAllCells(q, matchCase, wholeCell)
            },
            onSelectFindMatch = { idx ->
                commitActiveInCellEditIfNeeded()
                viewModel.onSelectFindMatch(idx)
            },
            onReplaceCurrent = { q, r, matchCase, wholeCell ->
                commitActiveInCellEditIfNeeded()
                viewModel.onReplaceCurrentCell(q, r, matchCase, wholeCell)
            },
            onReplaceAll = { q, r, matchCase, wholeCell ->
                commitActiveInCellEditIfNeeded()
                viewModel.onReplaceAllCells(q, r, matchCase, wholeCell)
            },
            onGoToReference = { ref ->
                commitActiveInCellEditIfNeeded()
                viewModel.onGoToReference(ref)
            },
            onGoToSpecial = { kind ->
                commitActiveInCellEditIfNeeded()
                viewModel.onGoToSpecial(kind)
            },
            onMoveSheet = { fromIdx, toIdx ->
                commitActiveInCellEditIfNeeded()
                viewModel.onMoveSheet(fromIdx, toIdx)
            },
            onSetCellComment = { comment ->
                commitActiveInCellEditIfNeeded()
                viewModel.onSetCellComment(comment)
            },
            onSetCellHyperlink = { url, label ->
                commitActiveInCellEditIfNeeded()
                viewModel.onSetCellHyperlink(url, label)
            },
            onDefineName = { name, refersTo ->
                commitActiveInCellEditIfNeeded()
                viewModel.onDefineName(name, refersTo)
            },
            onDismiss = { onSetCommandCenterMode(null) }
        )
    }

    // 10. Windows Spreadsheet AutoFilter & Sort Popup Menu Dialog (`[▾]` on Table Headers)
    val activeFilterPopup = state.activeColumnFilterPopup
    if (activeFilterPopup != null) {
        SpreadsheetColumnFilterPopupDialog(
            popup = activeFilterPopup,
            isEn = state.language == "en",
            onSortAscending = {
                commitActiveInCellEditIfNeeded()
                viewModel.onSortTableByColumn(activeFilterPopup.col, ascending = true)
            },
            onSortDescending = {
                commitActiveInCellEditIfNeeded()
                viewModel.onSortTableByColumn(activeFilterPopup.col, ascending = false)
            },
            onSortByColor = {
                commitActiveInCellEditIfNeeded()
                viewModel.onSortTableByColor(activeFilterPopup.col)
            },
            onClearFilter = {
                commitActiveInCellEditIfNeeded()
                viewModel.onClearColumnFilter(activeFilterPopup.col)
            },
            onApplyFilter = { selectedVals, includeBlanks, customOp, customQuery ->
                commitActiveInCellEditIfNeeded()
                viewModel.onApplyColumnFilter(
                    col = activeFilterPopup.col,
                    checkedValues = selectedVals,
                    includeBlanks = includeBlanks,
                    customOp = customOp,
                    customVal = customQuery
                )
            },
            onDismiss = { viewModel.onDismissColumnFilterMenu() }
        )
    }

    // 11. Windows Spreadsheet PivotChart & All 32 Chart Types Selector Dialog
    if (state.isPivotChartPickerOpen) {
        SpreadsheetPivotChartPickerDialog(
            state = state,
            onSelectChartType = { subType, insertNew ->
                commitActiveInCellEditIfNeeded()
                viewModel.onInsertOrUpdateChartFromPicker(subType, insertNew = insertNew)
            },
            onDismiss = { viewModel.onOpenPivotChartPicker(false) }
        )
    }

    // 12. Windows Spreadsheet Zoom Magnification Selector & Custom Percentage Dialog (25%..400% + Fit Selection)
    if (state.isZoomDialogOpen) {
        SpreadsheetZoomDialog(
            currentZoomPercent = state.zoomPercent,
            language = state.language,
            onSelectZoom = { pct ->
                commitActiveInCellEditIfNeeded()
                viewModel.onSetZoomPercent(pct)
            },
            onFitSelection = {
                commitActiveInCellEditIfNeeded()
                viewModel.onFitZoomToSelection()
            },
            onDismiss = { viewModel.onOpenZoomDialog(false) }
        )
    }

    // 13. Windows Spreadsheet "Select Data Source (データ ソースの選択)" Dialog for Standalone or Existing Charts
    if (state.isChartSelectDataDialogOpen) {
        val targetChart = state.sheetObjects.firstOrNull {
            it.id == state.chartSelectDataTargetId && it.kind == SheetObjectKind.CHART
        } ?: state.sheetObjects.firstOrNull {
            it.id == state.selectedObjectId && it.kind == SheetObjectKind.CHART
        } ?: state.sheetObjects.lastOrNull {
            it.sheetIndex == state.viewport.sheetIndex && it.kind == SheetObjectKind.CHART
        }
        if (targetChart != null) {
            val detectedTables = remember(state.viewport) {
                viewModel.detectTablesOnCurrentSheet()
            }
            val tableColumns = remember(
                state.viewport,
                targetChart.id,
                targetChart.sourceRangeText,
                targetChart.chartXColIndex,
                targetChart.chartYColIndices
            ) {
                viewModel.getChartSourceTableColumns(targetChart.id)
            }
            SpreadsheetChartSelectDataDialog(
                state = state,
                chartObject = targetChart,
                detectedTables = detectedTables,
                tableColumns = tableColumns,
                onSelectXAxisColumn = { colIdx ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onSelectChartXAxisColumn(targetChart.id, colIdx)
                },
                onToggleYAxisColumn = { colIdx, exclusive ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onToggleChartYAxisColumn(targetChart.id, colIdx, exclusive = exclusive)
                },
                onBindCurrentSelectionAsXAxis = {
                    commitActiveInCellEditIfNeeded()
                    viewModel.onBindSelectionAsChartXAxis(targetChart.id)
                },
                onBindCurrentSelectionAsYAxis = { append ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onBindSelectionAsChartYAxis(targetChart.id, append = append)
                },
                onStartAxisPickingOnSheet = { axisMode ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onStartChartAxisPickingOnSheet(targetChart.id, axisMode)
                },
                onApplyDataRange = { rangeText ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onUpdateChartDataRange(objectId = targetChart.id, rangeText = rangeText)
                    viewModel.onOpenChartSelectDataDialog(open = false)
                },
                onBindCurrentSelection = {
                    commitActiveInCellEditIfNeeded()
                    viewModel.onBindCurrentSelectionToChart(targetChart.id)
                    viewModel.onOpenChartSelectDataDialog(open = false)
                },
                onStartRangePickingOnSheet = {
                    commitActiveInCellEditIfNeeded()
                    viewModel.onStartChartRangePickingOnSheet(targetChart.id)
                },
                onSwitchRowColumn = {
                    commitActiveInCellEditIfNeeded()
                    viewModel.onSelectSheetObject(targetChart.id)
                    viewModel.onSwitchChartRowColumn(targetChart.id)
                },
                onUpdateSeriesRanges = { seriesIdx, seriesName, xRange, yRange ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onUpdateChartSeriesRanges(
                        objectId = targetChart.id,
                        seriesIndex = seriesIdx,
                        newName = seriesName,
                        newXRangeText = xRange,
                        newYRangeText = yRange
                    )
                },
                onAddSeriesFromSheet = { xRange, yRange, name ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onAddChartSeriesFromSheet(
                        objectId = targetChart.id,
                        customXRangeText = xRange,
                        customYRangeText = yRange,
                        customName = name
                    )
                },
                onRemoveSeriesAt = { seriesIdx ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onRemoveChartSeriesAt(targetChart.id, seriesIdx)
                },
                onOpenAxisFormatDialog = { axisTarget ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onOpenChartAxisFormatDialog(
                        objectId = targetChart.id,
                        axis = axisTarget,
                        open = true
                    )
                },
                onApplyCustomSeriesAndCategories = { title, cats, seriesList ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onUpdateChartCustomSeriesAndCategories(
                        chartId = targetChart.id,
                        newTitle = title,
                        categories = cats,
                        seriesPairs = seriesList
                    )
                    viewModel.onOpenChartSelectDataDialog(open = false)
                },
                onDismiss = { viewModel.onOpenChartSelectDataDialog(open = false) }
            )
        }
    }

    // 14. Windows Spreadsheet "Format Axis (軸の書式設定・表示間隔)" Dialog for X-Axis & Y-Axis
    if (state.isChartAxisFormatDialogOpen) {
        val targetChart = state.sheetObjects.firstOrNull {
            it.id == state.chartSelectDataTargetId && it.kind == SheetObjectKind.CHART
        } ?: state.sheetObjects.firstOrNull {
            it.id == state.selectedObjectId && it.kind == SheetObjectKind.CHART
        } ?: state.sheetObjects.lastOrNull {
            it.sheetIndex == state.viewport.sheetIndex && it.kind == SheetObjectKind.CHART
        }
        if (targetChart != null) {
            SpreadsheetChartAxisFormatDialog(
                state = state,
                chartObject = targetChart,
                initialAxis = state.chartAxisFormatTargetAxis,
                onApplyAxisFormat = { isXAxis, labelInterval, majorStep, minVal, maxVal, resetToAuto, closeDialog ->
                    commitActiveInCellEditIfNeeded()
                    viewModel.onUpdateChartAxisFormat(
                        objectId = targetChart.id,
                        isXAxis = isXAxis,
                        labelInterval = labelInterval,
                        majorStep = majorStep,
                        minVal = minVal,
                        maxVal = maxVal,
                        resetToAuto = resetToAuto,
                        closeDialog = closeDialog
                    )
                },
                onDismiss = { viewModel.onOpenChartAxisFormatDialog(open = false) }
            )
        }
    }
}

/**
 * [EN] Draggable vertical splitter handle allowing smooth width resizing of docked right-side task panes (AI / Pivot / Split View).
 * [JA] 右ドックパネル（AIアシスタント・ピボット設定・分割表示）の横幅をドラッグで自在に変更できる垂直分割バーです。
 */
@Composable
fun SpreadsheetResizableRightPaneDivider(
    currentWidthDp: Float,
    defaultWidthDp: Float,
    minWidthDp: Float = 220f,
    maxWidthDp: Float = 860f,
    accentColor: Color = Color(0xFF38BDF8),
    isDarkTheme: Boolean = false,
    testTag: String = "ms_right_pane_resize_divider",
    onWidthChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density.coerceAtLeast(1f)
    val latestWidthDp by rememberUpdatedState(currentWidthDp)
    val latestOnWidthChange by rememberUpdatedState(onWidthChange)
    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartRootX by remember { mutableStateOf(0f) }
    var dragStartWidthDp by remember { mutableStateOf(defaultWidthDp) }
    var layoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val horizontalResizeIcon = remember(context) {
        androidx.compose.ui.input.pointer.PointerIcon(
            PointerIcon.getSystemIcon(
                context,
                PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
            )
        )
    }

    val isActive = isHovered || isDragging
    val trackBg = when {
        isActive -> accentColor.copy(alpha = 0.22f)
        isDarkTheme -> Color(0xFF0F172A)
        else -> Color(0xFFE2E8F0)
    }
    val lineCol = when {
        isActive -> accentColor
        isDarkTheme -> Color(0xFF334155)
        else -> Color(0xFFCBD5E1)
    }

    Box(
        modifier = Modifier
            .width(12.dp)
            .fillMaxHeight()
            .testTag(testTag)
            .background(trackBg)
            .onGloballyPositioned { layoutCoords = it }
            .pointerHoverIcon(horizontalResizeIcon)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .pointerInput(minWidthDp, maxWidthDp) {
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        isDragging = true
                        dragStartRootX = layoutCoords?.localToRoot(startOffset)?.x ?: startOffset.x
                        dragStartWidthDp = latestWidthDp
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val curRootX = layoutCoords?.localToRoot(change.position)?.x ?: change.position.x
                        val deltaDp = (dragStartRootX - curRootX) / density
                        val nextW = (dragStartWidthDp + deltaDp).coerceIn(minWidthDp, maxWidthDp)
                        latestOnWidthChange(nextW)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Center vertical splitter line
        Box(
            modifier = Modifier
                .width(if (isActive) 3.dp else 1.5.dp)
                .fillMaxHeight()
                .background(lineCol)
        )
        // Center visual grip / left-right arrow badge (`↔`) when hovered or dragging
        if (isActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xEE0F172A))
                    .border(1.dp, accentColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↔",
                    color = Color(0xFFF8FAFC),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8))
            )
        }
    }
}
