package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * [EN] Formula Bar (Name Box, Auto-Complete, fx) & Bottom Sheet Tabs / Zoom Status Bar
 * Implements [MasuSheetNativeFormulaBar] with live formula auto-complete and [MasuSheetNativeSheetTabsBar] with hidden-sheet-safe tab switching, Unhide menu, stats summary, and zoom controls.
 *
 * ---
 * [JA / 日本語解説]
 * 名前ボックス・数式オートコンプリートを備えた数式バー ([MasuSheetNativeFormulaBar]) と、非表示シート分離・再表示メニュー・集計ステータス・ズーム倍率バーを備えた下部シートタブバー ([MasuSheetNativeSheetTabsBar]) です。
 * ============================================================================
 */

/**
 * [EN] Spreadsheet formula bar featuring the A1 Name Box, `fx` wizard trigger, live formula text field, and function auto-complete chips.
 * [JA] A1名前ボックス・`fx` ボタン・数式入力欄・関数オートコンプリート候補チップを備えた数式バーComposableです。
 */
@Composable
fun MasuSheetNativeFormulaBar(
    activeAddress: String,
    formulaText: String,
    activeSpillFormula: String = "",
    columnAutoCompleteSuggestion: String? = null,
    isDarkTheme: Boolean,
    surfaceBar: Color,
    borderCol: Color,
    textPrimary: Color,
    onFormulaChange: (String) -> Unit,
    onCommitFormula: () -> Unit,
    onCancelFormula: () -> Unit,
    onOpenFunctionWizard: () -> Unit = {},
    onNavigateToAddress: (String) -> Unit = {},
    isTouchRangeSelectMode: Boolean = false,
    onToggleTouchRangeSelectMode: () -> Unit = {}
) {
    val isEn = LocalAppLanguage.current == "en"
    val isM3 = LocalMaterial3Ui.current
    val fieldBg = if (isDarkTheme) Color(0xFF11151A) else Color(0xFFFFFFFF)
    var addressDraft by remember(activeAddress) { mutableStateOf(activeAddress) }
    val autocompleteSuggestions = remember(formulaText) {
        SpreadsheetFunctionCatalog.suggestAutocomplete(formulaText)
    }
    val boxRadius = if (isM3) 8.dp else 6.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceBar)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = borderCol)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Interactive Spreadsheet Name Box (e.g., A1 or A1:D10 — type and press Enter to jump/select!)
            BasicTextField(
                value = addressDraft,
                onValueChange = { addressDraft = it.uppercase() },
                singleLine = true,
                textStyle = TextStyle(
                    color = textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(Color(0xFF1F9D55)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onNavigateToAddress(addressDraft) }),
                modifier = Modifier
                    .width(68.dp)
                    .testTag("ms_formula_name_box")
                    .clip(RoundedCornerShape(boxRadius))
                    .background(fieldBg)
                    .border(1.dp, borderCol, RoundedCornerShape(boxRadius))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )

            // Standard compact Spreadsheet-style Cancel (✕) and Enter (✓) icons next to fx
            MasuSheetPillButton(
                label = "✕",
                tag = "ms_btn_formula_cancel",
                onClick = onCancelFormula
            )
            MasuSheetPillButton(
                label = "✓",
                tag = "ms_btn_formula_ok",
                onClick = onCommitFormula
            )

            // Clickable fx button that opens the 523-function Spreadsheet Function Wizard!
            Box(
                modifier = Modifier
                    .testTag("ms_btn_formula_fx")
                    .clip(RoundedCornerShape(boxRadius))
                    .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF3F2F1))
                    .border(1.dp, borderCol, RoundedCornerShape(boxRadius))
                    .clickable(onClick = onOpenFunctionWizard)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = SpreadsheetI18n.t("fx 関数", isEn),
                    color = if (isDarkTheme) Color(0xFF4ADE80) else Color(0xFF107C41),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            }

            // [EN] 1-tap Touch Range Select mode toggle for smartphones & tablets (1-stroke cell range drag anywhere)
            // [JA] スマートフォン・タブレット向けワンストローク範囲選択モードの1タップ切替チップ
            Box(
                modifier = Modifier
                    .testTag("ms_btn_formula_touch_select")
                    .clip(RoundedCornerShape(boxRadius))
                    .background(
                        if (isTouchRangeSelectMode) {
                            if (isDarkTheme) Color(0xFF14532D) else Color(0xFFDCFCE7)
                        } else {
                            if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF3F2F1)
                        }
                    )
                    .border(
                        1.dp,
                        if (isTouchRangeSelectMode) Color(0xFF1F9D55) else borderCol,
                        RoundedCornerShape(boxRadius)
                    )
                    .clickable(onClick = onToggleTouchRangeSelectMode)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isTouchRangeSelectMode) {
                        if (isEn) "👆 Select:ON" else "👆 範囲選択:ON"
                    } else {
                        if (isEn) "👆 Select" else "👆 範囲選択"
                    },
                    color = if (isTouchRangeSelectMode) {
                        if (isDarkTheme) Color(0xFF4ADE80) else Color(0xFF166534)
                    } else {
                        textPrimary
                    },
                    fontSize = 11.sp,
                    fontWeight = if (isTouchRangeSelectMode) FontWeight.Bold else FontWeight.Medium
                )
            }

            BasicTextField(
                value = formulaText,
                onValueChange = onFormulaChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = textPrimary,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(Color(0xFF1F9D55)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommitFormula() }),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(boxRadius))
                    .background(fieldBg)
                    .border(1.dp, borderCol, RoundedCornerShape(boxRadius))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }

        // A6: Read-only Spill Formula display when an empty spilled cell in a Dynamic Array is selected
        if (formulaText.isEmpty() && activeSpillFormula.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFEFF6FF))
                    .border(width = 0.5.dp, color = Color(0xFF2563EB))
                    .padding(horizontal = 10.dp, vertical = 2.5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) "↗ Spill Formula (Ghost):" else "↗ スピル元の数式 (動的配列):",
                    color = Color(0xFF3B82F6),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activeSpillFormula,
                    color = if (isDarkTheme) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // A9: Same-column plain-text AutoComplete suggestion pill (click or press Tab to accept)
        if (!columnAutoCompleteSuggestion.isNullOrEmpty() && !formulaText.startsWith("=")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF0FDF4))
                    .border(width = 0.5.dp, color = Color(0xFF1F9D55))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) "⚡ Column AutoComplete (Tab to accept):" else "⚡ 同一列の入力候補 (タップまたはTabで確定):",
                    color = Color(0xFF4ADE80),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF163828))
                        .border(1.dp, Color(0xFF1F9D55), RoundedCornerShape(5.dp))
                        .clickable {
                            onFormulaChange(columnAutoCompleteSuggestion)
                            onCommitFormula()
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = columnAutoCompleteSuggestion,
                        color = Color(0xFFF8FAFC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Live Formula Autocomplete Strip when typing "=..."
        if (autocompleteSuggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .border(width = 0.5.dp, color = Color(0xFF1F9D55))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SpreadsheetI18n.t("fx 候補:", isEn),
                    color = Color(0xFF4ADE80),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                autocompleteSuggestions.forEach { fn ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                            .clickable {
                                val lastTokenStart = formulaText.indexOfLast {
                                    !it.isLetterOrDigit() && it != '.' && it != '_'
                                }
                                val prefix = if (lastTokenStart >= 0) {
                                    formulaText.substring(0, lastTokenStart + 1)
                                } else {
                                    "="
                                }
                                onFormulaChange("$prefix${fn.name}(")
                            }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        val fnDesc = if (isEn) fn.descriptionEn else fn.descriptionJa
                        Text(
                            text = "${fn.name} — $fnDesc",
                            color = Color(0xFFF8FAFC),
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * [EN] Bottom bar combining hidden-sheet-safe worksheet tabs, tab context menu (including Unhide), live selection stats, and zoom controls.
 * [JA] 非表示シート分離対応のシートタブ・タブメニュー（再表示含む）・選択範囲リアルタイム集計・ズーム倍率操作を備えた下部バーです。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MasuSheetNativeSheetTabsBar(
    state: MasuSheetUiState,
    surfaceBar: Color,
    borderCol: Color,
    textPrimary: Color,
    onSelectSheet: (Int) -> Unit,
    onAddSheet: () -> Unit,
    onStepSheet: (Int) -> Unit = {},
    onInsertSheetBefore: (Int) -> Unit = {},
    onDeleteSheetAt: (Int) -> Unit = {},
    onRenameSheetAt: (Int, String) -> Unit = { _, _ -> },
    onDuplicateSheetAt: (Int) -> Unit = {},
    onMoveSheetAt: (Int, Int) -> Unit = { _, _ -> },
    onHideSheetAt: (Int) -> Unit = {},
    onOpenSheetColorPicker: () -> Unit = {},
    onOpenSheetManager: () -> Unit = {},
    onOpenMacroStudio: () -> Unit = {},
    onExecuteCommand: (String, String) -> Unit = { _, _ -> },
    onUnhideSheet: (String) -> Unit = {},
    onCycleZoom: () -> Unit = {},
    onSetZoom: (Int) -> Unit = {},
    onOpenZoomDialog: () -> Unit = {},
    onCopyLinkToClipboard: (String) -> Unit = {}
) {
    val isEn = state.language == "en"
    val isDark = state.isDarkTheme
    val vp = state.viewport
    val latestOnSelectSheet by rememberUpdatedState(onSelectSheet)
    val latestOnMoveSheetAt by rememberUpdatedState(onMoveSheetAt)
    val latestVisibleSheetIndices by rememberUpdatedState(vp.visibleSheetIndices)
    val activeColWidthPt = vp.colWidths.getOrElse(vp.activeCol) { 72f }.roundToInt()
    val activeRowHeightPt = vp.rowHeights.getOrElse(vp.activeRow) { 22f }.roundToInt()
    val autoCalcSummary = remember(vp.revision, vp.selStartRow, vp.selStartCol, vp.selEndRow, vp.selEndCol, isEn) {
        vp.computeSelectionAutoCalcSummary(isEn)
    }

    var contextMenuOpenForTab by remember { mutableStateOf<Int?>(null) }
    var renamingTabIndex by remember { mutableStateOf<Int?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var draggingTabIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumX by remember { mutableStateOf(0f) }
    val tabWidthsPx = remember { mutableMapOf<Int, Int>() }

    val sheetBarBg = if (isDark) Color(0xFF1B2026) else Color(0xFFF3F2F1)
    val statusBarBg = if (isDark) Color(0xFF14181D) else Color(0xFFF3F2F1)
    val spreadsheetGreen = Color(0xFF107C41)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sheetBarBg)
            .border(width = 0.5.dp, color = borderCol)
    ) {
        // =========================================================================
        // ROW 1: Spreadsheet Sheet Navigation + Sheet Tabs + '+' Button + Scrollbar Track
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(sheetBarBg)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 1. Spreadsheet Sheet Navigation Arrows (< >)
            Box(
                modifier = Modifier
                    .testTag("ms_btn_sheet_prev")
                    .semantics { contentDescription = if (isEn) "Previous Sheet" else "前のシート" }
                    .size(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onStepSheet(-1) },
                contentAlignment = Alignment.Center
            ) {
                Text("◀", color = textPrimary.copy(alpha = 0.75f), fontSize = 10.sp)
            }
            Box(
                modifier = Modifier
                    .testTag("ms_btn_sheet_next")
                    .semantics { contentDescription = if (isEn) "Next Sheet" else "次のシート" }
                    .size(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onStepSheet(1) },
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = textPrimary.copy(alpha = 0.75f), fontSize = 10.sp)
            }

            // 2. Spreadsheet Sheet Tabs (Active tab has crisp white surface + green bottom indicator bar; draggable horizontally to reorder!)
            vp.sheetNames.forEachIndexed { displayPos, name ->
                val realIdx = vp.visibleSheetIndices.getOrElse(displayPos) { displayPos }
                val selected = realIdx == vp.sheetIndex
                val isDraggingThis = draggingTabIndex == displayPos
                val customTabColor = state.sheetTabColors[realIdx]
                val tabBg = when {
                    isDraggingThis -> spreadsheetGreen.copy(alpha = 0.28f)
                    selected && customTabColor != null && customTabColor != 0 -> Color(customTabColor).copy(alpha = 0.25f)
                    selected -> if (isDark) Color(0xFF262D37) else Color(0xFFFFFFFF)
                    customTabColor != null && customTabColor != 0 -> Color(customTabColor).copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
                val indicatorCol = when {
                    customTabColor != null && customTabColor != 0 -> Color(customTabColor)
                    selected -> spreadsheetGreen
                    else -> Color.Transparent
                }
                val tabTextCol = when {
                    selected -> if (isDark) Color(0xFF4ADE80) else spreadsheetGreen
                    else -> textPrimary.copy(alpha = 0.85f)
                }

                Box {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .testTag("ms_sheet_tab_$displayPos")
                            .onGloballyPositioned { coords ->
                                tabWidthsPx[displayPos] = coords.size.width.coerceAtLeast(60)
                            }
                            .offset {
                                IntOffset(if (isDraggingThis) dragAccumX.roundToInt() else 0, 0)
                            }
                            .fillMaxHeight()
                            .background(tabBg)
                            .border(
                                width = if (isDraggingThis) 1.5.dp else 0.5.dp,
                                color = if (isDraggingThis) spreadsheetGreen else if (selected) borderCol else Color.Transparent
                            )
                            .pointerInput(realIdx) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                            latestOnSelectSheet(realIdx)
                                            contextMenuOpenForTab = realIdx
                                        }
                                    }
                                }
                            }
                            .pointerInput(displayPos, realIdx, vp.sheetNames.size) {
                                var activeDragPos = displayPos
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        activeDragPos = displayPos
                                        draggingTabIndex = displayPos
                                        dragAccumX = 0f
                                        latestOnSelectSheet(realIdx)
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragAccumX += dragAmount
                                        val curPos = draggingTabIndex ?: activeDragPos
                                        val rightNeighborW = (tabWidthsPx[curPos + 1] ?: 84).toFloat()
                                        val leftNeighborW = (tabWidthsPx[curPos - 1] ?: 84).toFloat()
                                        if (dragAccumX > rightNeighborW * 0.55f && curPos < vp.sheetNames.lastIndex) {
                                            val nextPos = curPos + 1
                                            val fromReal = latestVisibleSheetIndices.getOrElse(curPos) { curPos }
                                            val toReal = latestVisibleSheetIndices.getOrElse(nextPos) { nextPos }
                                            latestOnMoveSheetAt(fromReal, toReal)
                                            activeDragPos = nextPos
                                            draggingTabIndex = nextPos
                                            dragAccumX -= rightNeighborW
                                        } else if (dragAccumX < -leftNeighborW * 0.55f && curPos > 0) {
                                            val prevPos = curPos - 1
                                            val fromReal = latestVisibleSheetIndices.getOrElse(curPos) { curPos }
                                            val toReal = latestVisibleSheetIndices.getOrElse(prevPos) { prevPos }
                                            latestOnMoveSheetAt(fromReal, toReal)
                                            activeDragPos = prevPos
                                            draggingTabIndex = prevPos
                                            dragAccumX += leftNeighborW
                                        }
                                    },
                                    onDragEnd = {
                                        draggingTabIndex = null
                                        dragAccumX = 0f
                                    },
                                    onDragCancel = {
                                        draggingTabIndex = null
                                        dragAccumX = 0f
                                    }
                                )
                            }
                            .combinedClickable(
                                onClick = { latestOnSelectSheet(realIdx) },
                                onDoubleClick = {
                                    latestOnSelectSheet(realIdx)
                                    renamingTabIndex = realIdx
                                    renameDraft = name
                                },
                                onLongClick = {
                                    latestOnSelectSheet(realIdx)
                                    contextMenuOpenForTab = realIdx
                                }
                            )
                            .padding(horizontal = 12.dp)
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = name,
                            color = tabTextCol,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .widthIn(min = 32.dp)
                                .height(2.5.dp)
                                .background(indicatorCol)
                        )
                    }

                    // Exact Windows 11 Spreadsheet Sheet Tab Context Menu + Left/Right Reorder Actions
                    DropdownMenu(
                        expanded = contextMenuOpenForTab == realIdx,
                        onDismissRequest = { contextMenuOpenForTab = null },
                        modifier = Modifier
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF))
                            .border(1.dp, spreadsheetGreen, RoundedCornerShape(6.dp))
                    ) {
                        val menuTextColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("挿入(I)...", isEn), color = menuTextColor, fontSize = 12.sp) },
                            onClick = {
                                contextMenuOpenForTab = null
                                onInsertSheetBefore(realIdx)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("削除(D)", isEn), color = menuTextColor, fontSize = 12.sp) },
                            onClick = {
                                contextMenuOpenForTab = null
                                onDeleteSheetAt(realIdx)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("名前の変更(R)", isEn), color = menuTextColor, fontSize = 12.sp) },
                            onClick = {
                                contextMenuOpenForTab = null
                                renamingTabIndex = realIdx
                                renameDraft = name
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("移動またはコピー(M)...", isEn), color = menuTextColor, fontSize = 12.sp) },
                            onClick = {
                                contextMenuOpenForTab = null
                                onDuplicateSheetAt(realIdx)
                            }
                        )
                        if (displayPos > 0) {
                            DropdownMenuItem(
                                text = { Text(SpreadsheetI18n.t("⬅️ シートを左へ移動", isEn), color = spreadsheetGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    contextMenuOpenForTab = null
                                    val prevReal = vp.visibleSheetIndices.getOrElse(displayPos - 1) { (realIdx - 1).coerceAtLeast(0) }
                                    onMoveSheetAt(realIdx, prevReal)
                                }
                            )
                        }
                        if (displayPos < vp.sheetNames.lastIndex) {
                            DropdownMenuItem(
                                text = { Text(SpreadsheetI18n.t("➡️ シートを右へ移動", isEn), color = spreadsheetGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    contextMenuOpenForTab = null
                                    val nextReal = vp.visibleSheetIndices.getOrElse(displayPos + 1) { realIdx + 1 }
                                    onMoveSheetAt(realIdx, nextReal)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("コードの表示(V)", isEn), color = menuTextColor, fontSize = 12.sp) },
                            onClick = {
                                contextMenuOpenForTab = null
                                onOpenMacroStudio()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("シートの保護(P)...", isEn), color = menuTextColor, fontSize = 12.sp) },
                            onClick = {
                                contextMenuOpenForTab = null
                                onExecuteCommand("review.protectSheet", "{}")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("シート見出しの色(T) >", isEn), color = spreadsheetGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            onClick = {
                                contextMenuOpenForTab = null
                                onSelectSheet(realIdx)
                                onOpenSheetColorPicker()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("非表示(H)", isEn), color = menuTextColor, fontSize = 12.sp) },
                            onClick = {
                                contextMenuOpenForTab = null
                                onHideSheetAt(realIdx)
                            }
                        )
                        vp.hiddenSheetNames.forEach { hiddenName ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isEn) "👁️ Unhide: $hiddenName" else "👁️ 再表示: $hiddenName",
                                        color = spreadsheetGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                onClick = {
                                    contextMenuOpenForTab = null
                                    onUnhideSheet(hiddenName)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("再表示(U)...", isEn), color = menuTextColor, fontSize = 12.sp) },
                            onClick = {
                                contextMenuOpenForTab = null
                                onOpenSheetManager()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("すべてのシートを選択(S)", isEn), color = menuTextColor, fontSize = 12.sp) },
                            onClick = {
                                contextMenuOpenForTab = null
                                onOpenSheetManager()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(SpreadsheetI18n.t("このシートへのリンク(L)", isEn), color = menuTextColor, fontSize = 12.sp) },
                            onClick = {
                                contextMenuOpenForTab = null
                                onCopyLinkToClipboard("#'$name'!A1")
                            }
                        )
                    }
                }
            }

            // Inline Sheet Rename Box when double-clicking a tab or choosing "名前の変更(R)"
            val activeRenameIdx = renamingTabIndex
            if (activeRenameIdx != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF))
                        .border(1.dp, spreadsheetGreen, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    BasicTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(color = textPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(spreadsheetGreen),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onRenameSheetAt(activeRenameIdx, renameDraft)
                            renamingTabIndex = null
                        }),
                        modifier = Modifier.width(96.dp)
                    )
                    MasuSheetPillButton(
                        label = "✓",
                        highlight = true,
                        onClick = {
                            onRenameSheetAt(activeRenameIdx, renameDraft)
                            renamingTabIndex = null
                        }
                    )
                    MasuSheetPillButton(
                        label = "✕",
                        onClick = { renamingTabIndex = null }
                    )
                }
            }

            // 3. Spreadsheet '+' New Sheet circle button (Geometric Canvas '+' centered to subpixel accuracy)
            Box(
                modifier = Modifier
                    .testTag("ms_btn_add_sheet")
                    .semantics { contentDescription = if (isEn) "Add Worksheet" else "新しいシートを追加" }
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .border(1.dp, borderCol, RoundedCornerShape(11.dp))
                    .clickable(onClick = onAddSheet),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val arm = size.minDimension * 0.44f
                    val strokePx = 1.8.dp.toPx()
                    drawLine(
                        color = textPrimary,
                        start = Offset(cx - arm, cy),
                        end = Offset(cx + arm, cy),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = textPrimary,
                        start = Offset(cx, cy - arm),
                        end = Offset(cx, cy + arm),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Quick utility buttons preserved for tests & accessibility
            MasuSheetPillButton(
                label = "🎨 タブ色",
                tag = "ms_btn_sheet_color",
                onClick = onOpenSheetColorPicker
            )
            MasuSheetPillButton(
                label = "📑 シート管理",
                tag = "ms_btn_sheet_mgr",
                onClick = onOpenSheetManager
            )
        }

        // =========================================================================
        // ROW 2: Spreadsheet Status Bar (Left: Ready + AutoCalculate Summary | Far Right: View Icons + Zoom Slider + Clickable X%)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(statusBarBg)
                .border(width = 0.5.dp, color = borderCol)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Status Area (scrollable if AutoCalculate summary is long)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = SpreadsheetI18n.t("準備完了", isEn),
                    color = textPrimary.copy(alpha = 0.85f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )

                if (autoCalcSummary.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .testTag("ms_status_autocalc")
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isDark) Color(0xFF163828) else Color(0xFFE6F4EA))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = autoCalcSummary,
                            color = if (isDark) Color(0xFFA7F3D0) else spreadsheetGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = if (isEn) "W:${activeColWidthPt}pt H:${activeRowHeightPt}pt" else "列幅:${activeColWidthPt}pt 行高:${activeRowHeightPt}pt",
                    color = textPrimary.copy(alpha = 0.65f),
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Far-Right Pinned Controls: Spreadsheet View Switcher Icons + Zoom Slider + Clickable X% Badge (一番右)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SpreadsheetSmallIconBtn(
                        icon = "▦",
                        highlight = state.viewMode == "normal",
                        onClick = { onExecuteCommand("view.normal", "{}") }
                    )
                    SpreadsheetSmallIconBtn(
                        icon = "📐",
                        highlight = state.viewMode == "pageLayout",
                        onClick = { onExecuteCommand("view.pageLayout", "{}") }
                    )
                    SpreadsheetSmallIconBtn(
                        icon = "📄",
                        highlight = state.viewMode == "pageBreakPreview",
                        onClick = { onExecuteCommand("view.pageBreakPreview", "{}") }
                    )
                }

                // Interactive Spreadsheet Zoom Slider (− ━━━▢━━━ + X%)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("ms_btn_zoom_minus")
                            .semantics { contentDescription = if (isEn) "Zoom Out" else "縮小" }
                            .size(18.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { onSetZoom((state.zoomPercent - 10).coerceAtLeast(25)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("−", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    // Visual slider bar with thumb proportional to 25%..400% (100% centered when in normal range)
                    val zoomFraction = if (state.zoomPercent <= 100) {
                        ((state.zoomPercent - 25f) / 75f) * 0.5f
                    } else {
                        0.5f + ((state.zoomPercent - 100f) / 300f) * 0.5f
                    }.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(14.dp)
                            .clickable(onClick = onCycleZoom),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isDark) Color(0xFF475569) else Color(0xFFA19F9D))
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = (zoomFraction * 64f).dp)
                                .size(width = 8.dp, height = 10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(spreadsheetGreen)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .testTag("ms_btn_zoom_plus")
                            .semantics { contentDescription = if (isEn) "Zoom In" else "拡大" }
                            .size(18.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { onSetZoom((state.zoomPercent + 10).coerceAtMost(400)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .testTag("ms_btn_zoom_percent")
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                            .border(0.5.dp, borderCol, RoundedCornerShape(4.dp))
                            .clickable(onClick = onOpenZoomDialog)
                            .padding(horizontal = 6.dp, vertical = 1.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${state.zoomPercent}% ▾",
                            color = textPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
