package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import com.masusheet.app.storage.WindowHandoffManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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
            viewModel.onOpenDocumentUri(uri.toString())
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
            viewModel.onSelectSaveDialogFolder(viewModel.uiState.value.saveDialogFolderPath)
            val firstAdded = viewModel.uiState.value.saveDialogExistingFiles.firstOrNull {
                it.fullPath == uris.first().toString()
            }
            if (firstAdded != null) {
                viewModel.onSelectExistingFileForSaveName(firstAdded)
            }
        }
    }

    val createTypedSafLauncher = rememberLauncherForActivityResult(
        contract = CreateTypedDocumentContract()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onCompletePendingSave(uri.toString())
        } else {
            viewModel.onCancelPendingSave()
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            commitActiveInCellEditIfNeeded()
            viewModel.onInsertImageUri(uri.toString())
        }
    }

    val openImageFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            commitActiveInCellEditIfNeeded()
            viewModel.onInsertImageUri(uri.toString())
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
            viewModel.onSelectSaveDialogFolder(treeUri.toString())
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
                        viewModel.onTriggerSave()
                    },
                    onExportPdfClick = {
                        commitActiveInCellEditIfNeeded()
                        viewModel.onTriggerExportPdf()
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

                        // Floating Bottom-Right AI Assistant Button (Spreadsheet Copilot-style launcher)
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

            // 5. Save File Dialog Overlay (Selectable Extension + Clickable Existing Folder Files — Never saves as .bin!)
            if (state.isSaveDialogVisible) {
                MasuSheetSaveDialog(
                    state = state,
                    onUpdateBaseName = { viewModel.onUpdateSaveDialogBaseName(it) },
                    onSelectExtension = { viewModel.onSelectSaveDialogExtension(it) },
                    onSelectFolder = { viewModel.onSelectSaveDialogFolder(it) },
                    onPickCustomFolder = {
                        val initialDownloadUri = Uri.parse(
                            "content://com.android.externalstorage.documents/document/primary%3ADownload"
                        )
                        openFolderTreeLauncher.launch(initialDownloadUri)
                    },
                    onImportExternalFiles = {
                        importExternalFilesLauncher.launch(
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
                        viewModel.onOpenDocumentUri(uriStr)
                    },
                    onSaveDirectly = { viewModel.onConfirmSaveDirectlyToFolder() },
                    onOpenSystemPicker = {
                        val fullFileName = MasuSheetConfig.buildFileNameWithExtension(
                            state.saveDialogBaseName,
                            state.saveDialogExtension
                        )
                        val mimeType = MasuSheetConfig.resolveExportMimeType(fullFileName)
                        createTypedSafLauncher.launch(fullFileName to mimeType)
                    },
                    onDismiss = { viewModel.onDismissSaveDialog() }
                )
            }

            // 6. Full Spreadsheet Color Picker Dialog (Theme 60 Colors + Standard 10 + Recent + Custom 24-bit RGB/Hex + Target Selector)
            val activeColorTarget = showColorPickerTarget
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
                    onDismiss = { showColorPickerTarget = null }
                )
            }

            // 6b. Create PivotTable / Recommended PivotTables Dialog
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
                        formulaDraft = formula
                        viewModel.onCommitCellEdit(
                            state.viewport.activeRow,
                            state.viewport.activeCol,
                            formula,
                            moveDeltaRow = 1
                        )
                    },
                    onDismiss = { showFunctionWizard = false },
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
                    onDismiss = { showMacroStudio = false }
                )
            }

            // 9. Spreadsheet Command Center Dialog (All 292 Commands, Find/Replace, Go To, Conditional Formatting, Data Validation, Goal Seek, Sheet Manager)
            val activeCommandCenter = showCommandCenterMode
            if (activeCommandCenter != null) {
                SpreadsheetCommandCenterDialog(
                    state = state,
                    initialMode = activeCommandCenter,
                    onExecuteCommand = { cmd, params ->
                        commitActiveInCellEditIfNeeded()
                        viewModel.onExecuteEngineCommand(cmd, params)
                    },
                    onOpenColorPickerForSheetTab = {
                        showCommandCenterMode = null
                        showColorPickerTarget = "sheetTab"
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
                    onDismiss = { showCommandCenterMode = null }
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

internal val RIBBON_TABS = SpreadsheetRibbonCatalog.ALL_15_RIBBON_TABS
