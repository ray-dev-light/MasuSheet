package com.masusheet.app

import android.view.View.MeasureSpec
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
 * [EN] Floating Right-Click / Long-Press Context Menu & Data Validation Dropdown
 * Provides [showContextMenuAt] (cell, row/column header, chart/shape/image context actions) and [showValidationListDropdownAt] with measured viewport clamping (`H-13`).
 *
 * ---
 * [JA / 日本語解説]
 * セル・行列見出し・グラフ／図形／画像向けの右クリック・長押しコンテキストメニュー ([showContextMenuAt]) と、画面端クランプ付き入力規則リストドロップダウン ([showValidationListDropdownAt]) を提供します。
 * ============================================================================
 */

/**
 * Displays a native floating context menu at `(screenX, screenY)` on right-click (`BUTTON_SECONDARY`)
 * or long-press.
 */
fun MasuSheetNativeSheetContainer.showContextMenuAt(
    screenX: Float,
    screenY: Float,
    targetType: ContextMenuTargetType,
    targetRow: Int,
    targetCol: Int,
    targetObjectId: String? = null
) {
    if (isEditingInCell) {
        commitAndCloseInCellEditor(0, 0)
    }
    contextMenuTargetType = targetType
    contextMenuTargetRow = targetRow
    contextMenuTargetCol = targetCol
    contextMenuTargetObjectId = targetObjectId
    isContextMenuVisible = true

    val isDark = gridView.isDarkTheme
    val cardBg = if (isDark) 0xFF1E293B.toInt() else 0xFFFFFFFF.toInt()
    val cardBorder = if (isDark) 0xFF1F9D55.toInt() else 0xFF15803D.toInt()
    val itemFg = if (isDark) 0xFFF1F5F9.toInt() else 0xFF0F172A.toInt()
    val headerFg = 0xFF1F9D55.toInt()
    val dividerCol = if (isDark) 0xFF334155.toInt() else 0xFFE2E8F0.toInt()

    contextMenuScrollView.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(cardBg)
        setStroke(dpToPx(1.5f).coerceAtLeast(1), cardBorder)
        cornerRadius = dpToPx(8f).toFloat()
    }
    contextMenuList.removeAllViews()
    contextMenuList.setPadding(0, 0, 0, dpToPx(10f))
    val isEn = viewModel.uiState.value.language == "en"

    val titleText = when (targetType) {
        ContextMenuTargetType.COLUMN_HEADER -> if (isEn) {
            "Column ${MasuSheetConfig.formatColumnLabel(targetCol)} Menu"
        } else {
            "列 ${MasuSheetConfig.formatColumnLabel(targetCol)} メニュー"
        }
        ContextMenuTargetType.ROW_HEADER -> if (isEn) {
            "Row ${targetRow + 1} Menu"
        } else {
            "行 ${targetRow + 1} メニュー"
        }
        ContextMenuTargetType.SHEET_OBJECT -> SpreadsheetI18n.t("図形・グラフ・画像 メニュー", isEn)
        ContextMenuTargetType.CELL -> if (isEn) {
            "Cell ${MasuSheetConfig.formatCellAddress(targetRow, targetCol)} Menu"
        } else {
            "セル ${MasuSheetConfig.formatCellAddress(targetRow, targetCol)} メニュー"
        }
    }

    val headerTv = TextView(context).apply {
        text = titleText
        setTextColor(headerFg)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(6f))
    }
    contextMenuList.addView(headerTv)

    fun addDivider() {
        val div = View(context).apply {
            setBackgroundColor(dividerCol)
        }
        contextMenuList.addView(div, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1f)))
    }

    // [EN] Mini Formatting Toolbar at top of Cell / Column / Row Right-Click Menu:
    //      - Displays a compact 2-line (icon + short label) layout so tablet/touch users immediately know each button's purpose without hovering.
    //      - Displays a dynamic 1-phrase popup banner (`miniHoverPopupTv`) + native OS tooltip whenever a mouse/trackpad cursor hovers over a chip (or when long-pressed on touch).
    // [JA] セル／行／列の右クリックメニュー上部のミニ書式バー：
    //      - タブレット（ホバーなし）でも一目で意味が分かるよう、各ボタン内に「アイコン＋極小ラベル（太字・背景色・文字色・罫線・グラフ等）」を2段表示。
    //      - マウス／トラックパッドのカーソルを合わせた際（ホバー時）や長押し時には、直下に1行説明ポップアップバッジを表示。
    if (targetType != ContextMenuTargetType.SHEET_OBJECT) {
        val miniHoverPopupTv = TextView(context).apply {
            visibility = View.GONE
            setTextColor(if (isDark) 0xFFECFDF5.toInt() else 0xFF065F46.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(4f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (isDark) 0xFF064E3B.toInt() else 0xFFD1FAE5.toInt())
                setStroke(dpToPx(1f).coerceAtLeast(1), 0xFF10B981.toInt())
                cornerRadius = dpToPx(6f).toFloat()
            }
        }
        val miniBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8f), dpToPx(4f), dpToPx(8f), dpToPx(4f))
        }
        fun addMiniChip(
            chipIcon: String,
            shortJa: String,
            shortEn: String,
            descJa: String,
            descEn: String,
            chipAction: () -> Unit
        ) {
            val shortLabel = if (isEn) shortEn else shortJa
            val hoverDesc = if (isEn) descEn else descJa
            val normalBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xFF163828.toInt())
                setStroke(dpToPx(1f).coerceAtLeast(1), 0xFF1F9D55.toInt())
                cornerRadius = dpToPx(6f).toFloat()
            }
            val hoverBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xFF1F9D55.toInt())
                setStroke(dpToPx(1.5f).coerceAtLeast(1), 0xFF4ADE80.toInt())
                cornerRadius = dpToPx(6f).toFloat()
            }
            val chipLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(6f), dpToPx(3f), dpToPx(6f), dpToPx(3f))
                background = normalBg
                isClickable = true
                isFocusable = true
                contentDescription = hoverDesc
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    tooltipText = hoverDesc
                }
                val iconTv = TextView(context).apply {
                    text = chipIcon
                    setTextColor(0xFFF8FAFC.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                val subTv = TextView(context).apply {
                    text = shortLabel
                    setTextColor(0xFFA7F3D0.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 7.5f)
                    gravity = Gravity.CENTER
                    maxLines = 1
                }
                addView(iconTv)
                addView(subTv)
                setOnHoverListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                            background = hoverBg
                            miniHoverPopupTv.text = "💡 $hoverDesc"
                            miniHoverPopupTv.visibility = View.VISIBLE
                        }
                        MotionEvent.ACTION_HOVER_EXIT -> {
                            background = normalBg
                            miniHoverPopupTv.visibility = View.GONE
                        }
                    }
                    false
                }
                setOnLongClickListener {
                    background = hoverBg
                    miniHoverPopupTv.text = "💡 $hoverDesc"
                    miniHoverPopupTv.visibility = View.VISIBLE
                    true
                }
                setOnClickListener {
                    dismissContextMenu()
                    chipAction()
                    bindState(viewModel.uiState.value)
                    gridView.invalidate()
                }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dpToPx(4f)
            }
            miniBar.addView(chipLayout, lp)
        }
        addMiniChip("B", "太字", "Bold", "太字: 文字を太くします (Ctrl+B)", "Bold: Make text bold (Ctrl+B)") {
            viewModel.onExecuteEngineCommand("home.bold", "{}")
        }
        addMiniChip("I", "斜体", "Italic", "斜体: 文字を斜めにします (Ctrl+I)", "Italic: Italicize text (Ctrl+I)") {
            viewModel.onExecuteEngineCommand("home.italic", "{}")
        }
        addMiniChip("U", "下線", "Under", "下線: 文字に下線を引きます (Ctrl+U)", "Underline: Underline text (Ctrl+U)") {
            viewModel.onExecuteEngineCommand("home.underline", "{}")
        }
        addMiniChip("🎨", "背景色", "Fill", "背景色: セルの塗りつぶし色を変更", "Fill Color: Change cell background color") {
            onOpenColorPickerRequest?.invoke("fill")
        }
        addMiniChip("A̲", "文字色", "Font", "文字色: フォントの色を変更", "Font Color: Change text color") {
            onOpenColorPickerRequest?.invoke("font")
        }
        addMiniChip("▦", "罫線", "Border", "格子罫線: 選択範囲に枠線を引く", "All Borders: Draw grid borders") {
            viewModel.onExecuteEngineCommand("home.borders", "{\"style\":\"all\"}")
        }
        addMiniChip("%", "％表示", "Percent", "パーセント書式: 数値を％で表示", "Percent Format: Display as percentage") {
            viewModel.onExecuteEngineCommand("home.percentStyle", "{}")
        }
        addMiniChip(",", "桁区切", "Comma", "桁区切りカンマ: 3桁ごとにカンマ表示", "Comma Format: Thousands separator") {
            viewModel.onExecuteEngineCommand("home.commaStyle", "{}")
        }
        addMiniChip("⬌", "中央", "Center", "中央揃え: 文字を左右中央に配置", "Align Center: Center text horizontally") {
            viewModel.onExecuteEngineCommand("home.alignCenter", "{}")
        }
        addMiniChip("📊", "グラフ", "Chart", "グラフ作成: 選択範囲からグラフを描画", "Insert Chart: Draw chart from selection") {
            viewModel.onOpenPivotChartPicker(open = true)
        }
        val miniScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(miniBar)
        }
        contextMenuList.addView(miniScroll)
        val popupLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dpToPx(8f)
            rightMargin = dpToPx(8f)
            bottomMargin = dpToPx(4f)
        }
        contextMenuList.addView(miniHoverPopupTv, popupLp)
    }

    fun addMenuItem(label: String, action: () -> Unit) {
        val tv = TextView(context).apply {
            text = SpreadsheetI18n.t(label, isEn)
            setTextColor(itemFg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(dpToPx(12f), dpToPx(7f), dpToPx(12f), dpToPx(7f))
            isClickable = true
            isFocusable = false
            setOnClickListener {
                dismissContextMenu()
                action()
                bindState(viewModel.uiState.value)
                gridView.invalidate()
            }
        }
        contextMenuList.addView(tv)
    }

    addDivider()

    when (targetType) {
        ContextMenuTargetType.SHEET_OBJECT -> {
            val targetObj = gridView.sheetObjects.firstOrNull { it.id == targetObjectId }
            val selectedIds = viewModel.uiState.value.effectiveSelectedObjectIds
            val selectedCount = selectedIds.size.coerceAtLeast(if (targetObj != null) 1 else 0)
            val sheetObjectCount = gridView.sheetObjects.size

            // 0. Copy Selected Object(s) as Combined Image to Device System Clipboard + Multi-Select & Height Alignment
            if (selectedCount > 1) {
                addMenuItem(
                    if (isEn) "📋 Copy ${selectedCount} Selected Objects as Combined Image (to Clipboard)"
                    else "📋 選択した ${selectedCount} 個の図をまとめて画像としてコピー (クリップボードへ)"
                ) {
                    val combined = viewModel.onCopySelectedSheetObjectsAsCombinedImage(targetObj?.id)
                    if (combined != null) {
                        copyImageBytesToSystemClipboard(context, combined.second, combined.first)
                    }
                }
            } else {
                addMenuItem(
                    if (isEn) "📋 Copy This Object as Image to Clipboard"
                    else "📋 この図を画像としてクリップボードにコピー"
                ) {
                    val combined = viewModel.onCopySelectedSheetObjectsAsCombinedImage(targetObj?.id)
                    if (combined != null) {
                        copyImageBytesToSystemClipboard(context, combined.second, combined.first)
                    }
                }
                if (sheetObjectCount >= 2) {
                    addMenuItem(
                        if (isEn) "📋 Copy All ${sheetObjectCount} Objects on Sheet as Combined Image"
                        else "📋 シート上のすべての図・画像 (${sheetObjectCount}個) をまとめて画像としてコピー"
                    ) {
                        viewModel.onSelectAllSheetObjectsOnCurrentSheet(imagesOnly = false)
                        val combined = viewModel.onCopySelectedSheetObjectsAsCombinedImage()
                        if (combined != null) {
                            copyImageBytesToSystemClipboard(context, combined.second, combined.first)
                        }
                    }
                }
            }

            if (sheetObjectCount >= 2) {
                addMenuItem(
                    if (isEn) "✨ Select All ${sheetObjectCount} Objects on Sheet (or Shift+Click)"
                    else "✨ シート上のすべての図・画像 (${sheetObjectCount}個) を同時に選択 (Shift+クリックでも複数選択可)"
                ) {
                    viewModel.onSelectAllSheetObjectsOnCurrentSheet(imagesOnly = false)
                }
                addMenuItem(
                    if (isEn) "📏 Align Selected Objects Top (Match Y Position)"
                    else "📏 選択した図の「高さ位置 (上端 Y座標)」をぴったり揃える"
                ) {
                    viewModel.onAlignSelectedSheetObjects("alignTop")
                }
                addMenuItem(
                    if (isEn) "📏 Align Selected Objects Vertical Center"
                    else "📏 選択した図の「上下中央」をぴったり揃える"
                ) {
                    viewModel.onAlignSelectedSheetObjects("alignMiddle")
                }
                addMenuItem(
                    if (isEn) "📐 Match Height & Top Position of Selected Objects"
                    else "📐 選択した図の「縦のサイズと高さ位置」を揃える"
                ) {
                    viewModel.onAlignSelectedSheetObjects("matchHeight")
                }
                addMenuItem(
                    if (isEn) "↔ Arrange Selected Objects Side-by-Side at Same Height"
                    else "↔ 選択した図を同じ高さで横に並べて整列"
                ) {
                    viewModel.onAlignSelectedSheetObjects("arrangeSideBySide")
                }
            }
            addDivider()

            if (targetObj != null && targetObj.kind == SheetObjectKind.CHART) {
                val subElem = targetObj.selectedSubElement
                val activeSeriesIdx = if (subElem.startsWith("series:")) {
                    subElem.removePrefix("series:").toIntOrNull()?.takeIf { it in targetObj.chartSeries.indices }
                } else null

                // 1. Show status banner when X-Axis, Y-Axis, or a specific Series line/bar is selected
                if (subElem == "xAxis" || subElem == "yAxis" || activeSeriesIdx != null) {
                    val bannerTv = TextView(context).apply {
                        val bannerText = when {
                            subElem == "xAxis" -> if (isEn) {
                                "📏 Selected: X-Axis (Horizontal) — Adjust label/tick interval below"
                            } else {
                                "📏 選択中: X軸 (横軸全体) — 下のチップで表示間隔をワンタップ調整"
                            }
                            subElem == "yAxis" -> if (isEn) {
                                "📐 Selected: Y-Axis (Vertical) — Adjust major step/interval below"
                            } else {
                                "📐 選択中: Y軸 (縦軸全体) — 下のチップで目盛間隔をワンタップ調整"
                            }
                            activeSeriesIdx != null -> {
                                val s = targetObj.chartSeries[activeSeriesIdx]
                                val dispName = SpreadsheetI18n.translateChartDisplayText(s.name, isEn)
                                val xRef = viewModel.resolveChartSeriesAxisRangeText(targetObj, activeSeriesIdx, isXAxis = true)
                                val yRef = viewModel.resolveChartSeriesAxisRangeText(targetObj, activeSeriesIdx, isXAxis = false)
                                if (isEn) {
                                    "📈 Series [$dispName] ｜ X-Axis: $xRef ｜ Y-Axis: $yRef"
                                } else {
                                    "📈 選択系列「$dispName」｜ X軸: $xRef ｜ Y軸: $yRef"
                                }
                            }
                            else -> ""
                        }
                        text = bannerText
                        setTextColor(if (isDark) 0xFF38BDF8.toInt() else 0xFF0369A1.toInt())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(4f))
                    }
                    contextMenuList.addView(bannerTv)
                }

                // 2. X-Axis & Y-Axis Display Interval 1-Tap Chip Strips
                fun addIntervalChipStrip(
                    titleJa: String,
                    titleEn: String,
                    isXAxis: Boolean,
                    chips: List<Pair<String, () -> Boolean>>,
                    onSelectIdx: (Int) -> Unit
                ) {
                    val titleTv = TextView(context).apply {
                        text = if (isEn) titleEn else titleJa
                        val hdrColor = if (isXAxis) {
                            if (isDark) 0xFF38BDF8.toInt() else 0xFF0369A1.toInt()
                        } else {
                            if (isDark) 0xFFFBBF24.toInt() else 0xFFB45309.toInt()
                        }
                        setTextColor(hdrColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(2f))
                    }
                    contextMenuList.addView(titleTv)
                    val chipRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(dpToPx(8f), dpToPx(2f), dpToPx(8f), dpToPx(5f))
                    }
                    chips.forEachIndexed { idx, (label, isActiveFn) ->
                        val isActive = isActiveFn()
                        val chip = TextView(context).apply {
                            text = if (isActive) "● $label" else label
                            val chipFg = if (isActive) {
                                0xFFFFFFFF.toInt()
                            } else if (isDark) {
                                0xFFF8FAFC.toInt()
                            } else {
                                0xFF0F172A.toInt()
                            }
                            val chipBg = if (isActive) {
                                if (isXAxis) 0xFF0284C7.toInt() else 0xFFD97706.toInt()
                            } else if (isDark) {
                                0xFF1E293B.toInt()
                            } else {
                                0xFFF1F5F9.toInt()
                            }
                            val chipStroke = if (isActive) {
                                if (isXAxis) 0xFF38BDF8.toInt() else 0xFFFBBF24.toInt()
                            } else if (isDark) {
                                0xFF475569.toInt()
                            } else {
                                0xFF94A3B8.toInt()
                            }
                            setTextColor(chipFg)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                            setTypeface(typeface, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                            setPadding(dpToPx(8f), dpToPx(4f), dpToPx(8f), dpToPx(4f))
                            background = GradientDrawable().apply {
                                setColor(chipBg)
                                setStroke(dpToPx(1.1f).coerceAtLeast(1), chipStroke)
                                cornerRadius = dpToPx(6f).toFloat()
                            }
                            setOnClickListener {
                                dismissContextMenu()
                                onSelectIdx(idx)
                                bindState(viewModel.uiState.value)
                                gridView.invalidate()
                            }
                        }
                        val lp = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            rightMargin = dpToPx(5f)
                        }
                        chipRow.addView(chip, lp)
                    }
                    val hScroll = HorizontalScrollView(context).apply {
                        isHorizontalScrollBarEnabled = false
                        addView(chipRow)
                    }
                    contextMenuList.addView(hScroll)
                }

                val xIntervals = listOf(0, 1, 2, 3, 5, 10)
                val xIntervalChips = listOf(
                    (if (isEn) "⚙ Custom..." else "⚙ 詳細設定...") to { targetObj.xAxisMajorStep != null || targetObj.xAxisMin != null || targetObj.xAxisMax != null }
                ) + xIntervals.map { iv ->
                    val lbl = when (iv) {
                        0 -> if (isEn) "Auto" else "自動"
                        1 -> if (isEn) "Every 1" else "1ごと(全表示)"
                        else -> if (isEn) "Every $iv" else "${iv}おき"
                    }
                    lbl to { targetObj.xAxisLabelInterval == iv && targetObj.xAxisMajorStep == null }
                }

                val allYVals = targetObj.chartSeries.flatMap { it.values }.ifEmpty { targetObj.chartValues }
                val effYMin = targetObj.yAxisMin ?: (allYVals.minOrNull() ?: 0f).coerceAtMost(0f)
                val effYMax = (targetObj.yAxisMax ?: (allYVals.maxOrNull() ?: 100f)).coerceAtLeast(effYMin + 1f)
                val effYSpan = (effYMax - effYMin).coerceAtLeast(1f)
                val scaleSteps: List<Float> = if (effYSpan <= 200f) {
                    listOf(1f, 2f, 5f, 10f, 20f, 50f, 100f)
                } else {
                    val exp = kotlin.math.floor(kotlin.math.log10(effYSpan.toDouble())).toInt() - 1
                    val base = Math.pow(10.0, exp.coerceAtLeast(0).toDouble()).toFloat()
                    listOf(0.5f * base, 1f * base, 2f * base, 2.5f * base, 5f * base, 10f * base)
                        .map { it.coerceAtLeast(1f) }
                        .distinct()
                }
                // Negative values (-4f, -5f, -8f, -10f, -20f) represent exact number of equal axis divisions!
                val ySteps = listOf<Float?>(null, -4f, -5f, -8f, -10f, -20f) + scaleSteps
                val yStepChips = listOf(
                    (if (isEn) "⚙ Custom..." else "⚙ 詳細設定...") to { targetObj.yAxisMin != null || targetObj.yAxisMax != null }
                ) + ySteps.map { st ->
                    val lbl = when {
                        st == null -> if (isEn) "Auto" else "自動"
                        st < 0f -> if (isEn) "${abs(st).toInt()} Divs" else "${abs(st).toInt()}分割"
                        else -> java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(st.toLong())
                    }
                    lbl to { targetObj.yAxisMajorStep == st }
                }

                if (subElem != "yAxis") {
                    addIntervalChipStrip(
                        titleJa = "📏 X軸の表示間隔 (ラベル間隔 / 数値間隔):",
                        titleEn = "📏 X-Axis Display Interval:",
                        isXAxis = true,
                        chips = xIntervalChips,
                        onSelectIdx = { idx ->
                            if (idx == 0) {
                                viewModel.onOpenChartAxisFormatDialog(objectId = targetObj.id, axis = "x", open = true)
                            } else {
                                val iv = xIntervals[idx - 1]
                                viewModel.onUpdateChartAxisFormat(
                                    objectId = targetObj.id,
                                    isXAxis = true,
                                    labelInterval = iv,
                                    majorStep = if (iv == 0) null else targetObj.xAxisMajorStep,
                                    minVal = targetObj.xAxisMin,
                                    maxVal = targetObj.xAxisMax,
                                    resetToAuto = iv == 0
                                )
                            }
                        }
                    )
                }
                if (subElem != "xAxis") {
                    addIntervalChipStrip(
                        titleJa = "📐 Y軸の表示間隔 (分割数 / 目盛単位):",
                        titleEn = "📐 Y-Axis Interval (Divisions / Step Unit):",
                        isXAxis = false,
                        chips = yStepChips,
                        onSelectIdx = { idx ->
                            if (idx == 0) {
                                viewModel.onOpenChartAxisFormatDialog(objectId = targetObj.id, axis = "y", open = true)
                            } else {
                                val st = ySteps[idx - 1]
                                viewModel.onUpdateChartAxisFormat(
                                    objectId = targetObj.id,
                                    isXAxis = false,
                                    majorStep = st,
                                    minVal = targetObj.yAxisMin,
                                    maxVal = targetObj.yAxisMax,
                                    resetToAuto = st == null && targetObj.yAxisMin == null && targetObj.yAxisMax == null
                                )
                            }
                        }
                    )
                }
                addDivider()

                // 3. Column quick-select chips for X-Axis and Y-Axis
                val tableCols = viewModel.getChartSourceTableColumns(targetObj.id)
                if (tableCols.isNotEmpty()) {
                    fun addAxisChipStrip(titleJa: String, titleEn: String, isXAxis: Boolean) {
                        val titleTv = TextView(context).apply {
                            text = if (isEn) titleEn else titleJa
                            val hdrColor = if (isXAxis) {
                                if (isDark) 0xFF38BDF8.toInt() else 0xFF0369A1.toInt()
                            } else {
                                if (isDark) 0xFFFBBF24.toInt() else 0xFFB45309.toInt()
                            }
                            setTextColor(hdrColor)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            setPadding(dpToPx(10f), dpToPx(5f), dpToPx(10f), dpToPx(2f))
                        }
                        contextMenuList.addView(titleTv)
                        val activeYSet = targetObj.chartSeries.map { it.name }.toSet()
                        val chipRow = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(dpToPx(8f), dpToPx(2f), dpToPx(8f), dpToPx(5f))
                        }
                        tableCols.forEach { colOpt ->
                            val isActive = if (isXAxis) {
                                if (targetObj.chartXColIndex >= 0) {
                                    colOpt.colIndex == targetObj.chartXColIndex
                                } else {
                                    colOpt.sampleValues.isNotEmpty() &&
                                        targetObj.chartLabels.take(colOpt.sampleValues.size) == colOpt.sampleValues
                                }
                            } else {
                                if (targetObj.chartYColIndices.isNotEmpty()) {
                                    colOpt.colIndex in targetObj.chartYColIndices
                                } else {
                                    colOpt.headerName in activeYSet || "${colOpt.colLetter}列" in activeYSet
                                }
                            }
                            val chip = TextView(context).apply {
                                val prefix = if (isXAxis) {
                                    if (isActive) "● " else "○ "
                                } else {
                                    if (isActive) "☑ " else "☐ "
                                }
                                text = "$prefix${colOpt.displayLabel(isEn)}"
                                val chipFg = if (isActive) {
                                    0xFFFFFFFF.toInt()
                                } else if (isDark) {
                                    0xFFF8FAFC.toInt()
                                } else {
                                    0xFF0F172A.toInt()
                                }
                                val chipBg = if (isActive) {
                                    0xFF107C41.toInt()
                                } else if (isDark) {
                                    0xFF1E293B.toInt()
                                } else {
                                    0xFFF1F5F9.toInt()
                                }
                                val chipStroke = if (isActive) {
                                    if (isDark) 0xFF4ADE80.toInt() else 0xFF0B5A2E.toInt()
                                } else if (isDark) {
                                    0xFF475569.toInt()
                                } else {
                                    0xFF94A3B8.toInt()
                                }
                                setTextColor(chipFg)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                                setTypeface(typeface, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                                setPadding(dpToPx(8f), dpToPx(4f), dpToPx(8f), dpToPx(4f))
                                background = GradientDrawable().apply {
                                    setColor(chipBg)
                                    setStroke(dpToPx(1.1f).coerceAtLeast(1), chipStroke)
                                    cornerRadius = dpToPx(6f).toFloat()
                                }
                                setOnClickListener {
                                    dismissContextMenu()
                                    if (isXAxis) {
                                        viewModel.onSelectChartXAxisColumn(targetObj.id, colOpt.colIndex)
                                    } else {
                                        viewModel.onToggleChartYAxisColumn(targetObj.id, colOpt.colIndex, exclusive = false)
                                    }
                                    bindState(viewModel.uiState.value)
                                    gridView.invalidate()
                                }
                            }
                            val lp = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                rightMargin = dpToPx(5f)
                            }
                            chipRow.addView(chip, lp)
                        }
                        val hScroll = HorizontalScrollView(context).apply {
                            isHorizontalScrollBarEnabled = false
                            addView(chipRow)
                        }
                        contextMenuList.addView(hScroll)
                    }
                    addAxisChipStrip(
                        titleJa = "📏 X軸 (横軸・項目) の列をワンタップ選択:",
                        titleEn = "📏 Select X-Axis (Horizontal) Column:",
                        isXAxis = true
                    )
                    addAxisChipStrip(
                        titleJa = "📈 Y軸 (縦軸・数値系列) の列を選択 (複数可):",
                        titleEn = "📈 Select Y-Axis (Vertical) Columns:",
                        isXAxis = false
                    )
                    addDivider()
                }

                // If a specific series line/bar is selected, offer direct X/Y range picking for that series
                if (activeSeriesIdx != null) {
                    val s = targetObj.chartSeries[activeSeriesIdx]
                    val dispName = SpreadsheetI18n.translateChartDisplayText(s.name, isEn)
                    val xRef = viewModel.resolveChartSeriesAxisRangeText(targetObj, activeSeriesIdx, isXAxis = true)
                    val yRef = viewModel.resolveChartSeriesAxisRangeText(targetObj, activeSeriesIdx, isXAxis = false)
                    addMenuItem(
                        if (isEn) "🖱️ Re-select X-Axis Range ($xRef) for Series [$dispName] on Sheet..."
                        else "🖱️ 選択系列「$dispName」のX軸範囲 ($xRef) をシート上で再選択..."
                    ) {
                        viewModel.onStartChartAxisPickingOnSheet(targetObj.id, "series_x:$activeSeriesIdx")
                    }
                    addMenuItem(
                        if (isEn) "🖱️ Re-select Y-Axis Range ($yRef) for Series [$dispName] on Sheet..."
                        else "🖱️ 選択系列「$dispName」のY軸範囲 ($yRef) をシート上で再選択..."
                    ) {
                        viewModel.onStartChartAxisPickingOnSheet(targetObj.id, "series_y:$activeSeriesIdx")
                    }
                    addDivider()
                }

                addMenuItem("📐 系列ごとのX軸・Y軸とデータ ソースの選択...") {
                    viewModel.onOpenChartSelectDataDialog(open = true, objectId = targetObj.id)
                }
                addMenuItem("➕ 新しい系列を追加 (系列ごとにX軸・Y軸を選択)...") {
                    viewModel.onAddChartSeriesFromSheet(targetObj.id)
                    viewModel.onOpenChartSelectDataDialog(open = true, objectId = targetObj.id)
                }
                addMenuItem("📏 X軸の表示間隔・最小値・最大値の設定...") {
                    viewModel.onOpenChartAxisFormatDialog(objectId = targetObj.id, axis = "x", open = true)
                }
                addMenuItem("📐 Y軸の表示間隔・最小値・最大値の設定...") {
                    viewModel.onOpenChartAxisFormatDialog(objectId = targetObj.id, axis = "y", open = true)
                }
                addDivider()
                addMenuItem("📏 現在のセル選択範囲を「X軸 (横軸)」に設定") {
                    viewModel.onBindSelectionAsChartXAxis(targetObj.id)
                }
                addMenuItem("📈 現在のセル選択範囲を「Y軸 (縦軸)」に設定") {
                    viewModel.onBindSelectionAsChartYAxis(targetObj.id, append = false)
                }
                addMenuItem("🖱️ シート上をドラッグして「X軸」の範囲を選択...") {
                    val mode = if (activeSeriesIdx != null) "series_x:$activeSeriesIdx" else "x"
                    viewModel.onStartChartAxisPickingOnSheet(targetObj.id, mode)
                }
                addMenuItem("🖱️ シート上をドラッグして「Y軸」の範囲を選択...") {
                    val mode = if (activeSeriesIdx != null) "series_y:$activeSeriesIdx" else "y"
                    viewModel.onStartChartAxisPickingOnSheet(targetObj.id, mode)
                }
                addMenuItem("📍 現在のセル選択範囲をこのグラフに反映 (再グラフ化)") {
                    viewModel.onBindCurrentSelectionToChart(targetObj.id)
                }
                addMenuItem("🖱️ シート上をドラッグしてデータ範囲全体を選択...") {
                    viewModel.onStartChartRangePickingOnSheet(targetObj.id)
                }
                addMenuItem("⇄ X軸とY軸 (行/列) の切り替え") {
                    viewModel.onSelectSheetObject(targetObj.id)
                    viewModel.onSwitchChartRowColumn(targetObj.id)
                }
                addMenuItem("🔄 最新データでグラフを再計算") {
                    viewModel.onRefreshChartFromSourceRange(targetObj.id)
                }
                addMenuItem("📊 グラフの種類を変更 (全32種)...") {
                    viewModel.onSelectSheetObject(targetObj.id)
                    viewModel.onOpenPivotChartPicker()
                }
                addDivider()
            }
            if (targetObj != null && targetObj.kind == SheetObjectKind.IMAGE) {
                addMenuItem("📐 画像の縦横比・サイズを元に戻す") {
                    viewModel.onSelectSheetObject(targetObj.id)
                    viewModel.onResetSelectedImageAspectRatio()
                }
                addDivider()
            } else if (targetObj != null) {
                addMenuItem("✏️ キャンバス上で文字・タイトルを編集") {
                    openInObjectEditor(targetObj)
                }
                addDivider()
            }
            addMenuItem("⟳ 右へ90度回転 (+90°)") {
                viewModel.onRotateSelectedObjectBy(90f)
            }
            addMenuItem("⟲ 左へ90度回転 (-90°)") {
                viewModel.onRotateSelectedObjectBy(-90f)
            }
            addMenuItem("↻ 右へ15度回転 (+15°)") {
                viewModel.onRotateSelectedObjectBy(15f)
            }
            addMenuItem("↺ 左へ15度回転 (-15°)") {
                viewModel.onRotateSelectedObjectBy(-15f)
            }
            addMenuItem("↕ 上下反転 (180°)") {
                viewModel.onRotateSelectedObjectBy(180f)
            }
            addMenuItem("🧭 回転角度をリセット (0°)") {
                viewModel.onResetSelectedObjectRotation()
            }
            addDivider()
            if (targetObj != null && targetObj.kind == SheetObjectKind.CHART) {
                addMenuItem("📊 グラフ系列(棒・線・円)の色パレット...") {
                    onOpenColorPickerRequest?.invoke("chartSeries")
                }
            }
            addMenuItem("🎨 中の色(背景)パレット (テーマ60色・24bit RGB・16進数)") {
                onOpenColorPickerRequest?.invoke("objectFill")
            }
            addMenuItem("🖊️ 枠線の色パレット (テーマ60色・24bit RGB・16進数)") {
                onOpenColorPickerRequest?.invoke("objectStroke")
            }
            addMenuItem("🔤 図形・グラフ内の文字色パレット...") {
                onOpenColorPickerRequest?.invoke("objectText")
            }
            if (targetObj != null && targetObj.kind == SheetObjectKind.CHART) {
                addMenuItem("🏷️ グラフタイトルの文字色を変更...") {
                    onOpenColorPickerRequest?.invoke("objectText:title")
                }
                addMenuItem("📏 X軸 (横軸) の文字色を変更...") {
                    onOpenColorPickerRequest?.invoke("objectText:xAxis")
                }
                addMenuItem("📐 Y軸 (縦軸) の文字色を変更...") {
                    onOpenColorPickerRequest?.invoke("objectText:yAxis")
                }
                addMenuItem("📋 凡例・右上表示の文字色を変更...") {
                    onOpenColorPickerRequest?.invoke("objectText:legend")
                }
            }
            addMenuItem("🚫 中の色を抜く (透明にする)") {
                viewModel.onUpdateSelectedObjectFillColor(0)
            }
            addMenuItem("🎨 中の色: 緑") {
                viewModel.onUpdateSelectedObjectFillColor(0xFF1F9D55.toInt())
            }
            addMenuItem("🎨 中の色: 青") {
                viewModel.onUpdateSelectedObjectFillColor(0xFF2563EB.toInt())
            }
            addMenuItem("🎨 中の色: 赤") {
                viewModel.onUpdateSelectedObjectFillColor(0xFFDC2626.toInt())
            }
            addMenuItem("🎨 中の色: 黄") {
                viewModel.onUpdateSelectedObjectFillColor(0xFFEAB308.toInt())
            }
            addMenuItem("🎨 中の色: 白") {
                viewModel.onUpdateSelectedObjectFillColor(0xFFFFFFFF.toInt())
            }
            addDivider()
            addMenuItem("━━ 周りの線を太線にする (4pt)") {
                viewModel.onUpdateSelectedObjectStrokeWidth(4.0f)
            }
            addMenuItem("━━━ 周りの線を極太にする (6.5pt)") {
                viewModel.onUpdateSelectedObjectStrokeWidth(6.5f)
            }
            addMenuItem("━ 周りの線を標準にする (2pt)") {
                viewModel.onUpdateSelectedObjectStrokeWidth(2.0f)
            }
            addMenuItem("━ 周りの線を細線にする (1pt)") {
                viewModel.onUpdateSelectedObjectStrokeWidth(1.0f)
            }
            addMenuItem("🚫 枠線なし (0pt)") {
                viewModel.onUpdateSelectedObjectStrokeWidth(0f)
            }
            addDivider()
            addMenuItem("🖊️ 枠線の色: 黒") {
                viewModel.onUpdateSelectedObjectStrokeColor(0xFF0F172A.toInt())
            }
            addMenuItem("🖊️ 枠線の色: 赤") {
                viewModel.onUpdateSelectedObjectStrokeColor(0xFFDC2626.toInt())
            }
            addMenuItem("🖊️ 枠線の色: 青") {
                viewModel.onUpdateSelectedObjectStrokeColor(0xFF2563EB.toInt())
            }
            addMenuItem("🖊️ 枠線の色: 緑") {
                viewModel.onUpdateSelectedObjectStrokeColor(0xFF16A34A.toInt())
            }
            addDivider()
            if (selectedCount > 1) {
                addMenuItem(
                    if (isEn) "🗑️ Delete All ${selectedCount} Selected Objects"
                    else "🗑️ 選択中の ${selectedCount} 個の図・画像をまとめて削除"
                ) {
                    viewModel.onDeleteSelectedSheetObjects()
                }
            } else {
                addMenuItem("🗑️ この図・グラフを削除") {
                    targetObjectId?.let { viewModel.onDeleteSheetObject(it) }
                }
            }
        }

        ContextMenuTargetType.COLUMN_HEADER -> {
            addMenuItem("✂️ 切り取り") {
                val text = viewModel.onCutSelectionToClipboard()
                copyToSystemClipboard(context, text)
            }
            addMenuItem("📋 コピー (他アプリへも貼付可)") {
                val text = viewModel.onCopySelectionToClipboard()
                copyToSystemClipboard(context, text)
            }
            addMenuItem("📌 貼り付け (テキスト・スクショ画像対応)") {
                pasteFromSystemClipboardOrImage()
            }
            addMenuItem("📋 形式を選択して貼り付け (値のみ)") {
                viewModel.onExecuteEngineCommand("edit.pasteSpecial", "{\"mode\":\"values\"}")
            }
            addDivider()
            addMenuItem("➕ 左に列を挿入") { viewModel.onExecuteEngineCommand("home.insertColumns", "{}") }
            addMenuItem("➖ この列を削除") { viewModel.onExecuteEngineCommand("home.deleteColumns", "{}") }
            addMenuItem("🧹 数式と値のクリア") { viewModel.onClearSelectedCells() }
            addDivider()
            addMenuItem("↔ 列幅を自動調整") { viewModel.onAutoFitColumn(targetCol) }
            addMenuItem("↔ 列幅を広くする (+30pt)") {
                val cur = viewModel.uiState.value.viewport.colWidths.getOrElse(targetCol) { 72f }
                viewModel.onResizeColumn(targetCol, cur + 30f)
            }
            addMenuItem("↔ 列幅を狭くする (-20pt)") {
                val cur = viewModel.uiState.value.viewport.colWidths.getOrElse(targetCol) { 72f }
                viewModel.onResizeColumn(targetCol, cur - 20f)
            }
            addMenuItem("🙈 この列を非表示にする") {
                viewModel.onHideSelectedColumns()
            }
            addMenuItem("👁️ 列の再表示 (隣接する非表示列も復元)") {
                viewModel.onUnhideColumnsAround(targetCol)
            }
            addDivider()
            addMenuItem("🎨 列の塗りつぶし色パレット (全60色+RGB)") {
                onOpenColorPickerRequest?.invoke("fill")
            }
            addMenuItem("🔼 昇順並べ替え (A→Z)") { viewModel.onExecuteEngineCommand("data.sortAscending", "{}") }
            addMenuItem("🔽 降順並べ替え (Z→A)") { viewModel.onExecuteEngineCommand("data.sortDescending", "{}") }
            if (gridView.sheetObjects.any { it.kind == SheetObjectKind.CHART }) {
                addDivider()
                addMenuItem("📏 この列をグラフの「X軸 (横軸)」に設定") {
                    viewModel.onSelectChartXAxisColumn(colIndex = targetCol)
                }
                addMenuItem("📈 この列をグラフの「Y軸 (縦軸)」に設定") {
                    viewModel.onToggleChartYAxisColumn(colIndex = targetCol, exclusive = true)
                }
                addMenuItem("➕ この列をグラフの「Y軸 (複数系列)」に追加/解除") {
                    viewModel.onToggleChartYAxisColumn(colIndex = targetCol, exclusive = false)
                }
            }
        }

        ContextMenuTargetType.ROW_HEADER -> {
            addMenuItem("✂️ 切り取り") {
                val text = viewModel.onCutSelectionToClipboard()
                copyToSystemClipboard(context, text)
            }
            addMenuItem("📋 コピー (他アプリへも貼付可)") {
                val text = viewModel.onCopySelectionToClipboard()
                copyToSystemClipboard(context, text)
            }
            addMenuItem("📌 貼り付け (テキスト・スクショ画像対応)") {
                pasteFromSystemClipboardOrImage()
            }
            addMenuItem("📋 形式を選択して貼り付け (値のみ)") {
                viewModel.onExecuteEngineCommand("edit.pasteSpecial", "{\"mode\":\"values\"}")
            }
            addDivider()
            addMenuItem("➕ 上に行を挿入") { viewModel.onExecuteEngineCommand("home.insertRows", "{}") }
            addMenuItem("➖ この行を削除") { viewModel.onExecuteEngineCommand("home.deleteRows", "{}") }
            addMenuItem("🧹 数式と値のクリア") { viewModel.onClearSelectedCells() }
            addDivider()
            addMenuItem("↕ 行高さを自動調整") { viewModel.onAutoFitRow(targetRow) }
            addMenuItem("↕ 行高さを高くする (+10pt)") {
                val cur = viewModel.uiState.value.viewport.rowHeights.getOrElse(targetRow) { 22f }
                viewModel.onResizeRow(targetRow, cur + 10f)
            }
            addMenuItem("↕ 行高さを低くする (-6pt)") {
                val cur = viewModel.uiState.value.viewport.rowHeights.getOrElse(targetRow) { 22f }
                viewModel.onResizeRow(targetRow, cur - 6f)
            }
            addMenuItem("🙈 この行を非表示にする") {
                viewModel.onHideSelectedRows()
            }
            addMenuItem("👁️ 行の再表示 (隣接する非表示行も復元)") {
                viewModel.onUnhideRowsAround(targetRow)
            }
            addDivider()
            addMenuItem("🎨 行の塗りつぶし色パレット (全60色+RGB)") {
                onOpenColorPickerRequest?.invoke("fill")
            }
        }

        ContextMenuTargetType.CELL -> {
            addMenuItem("✏️ セルを直接編集") {
                openInCellEditor(targetRow, targetCol, null, null)
            }
            addMenuItem("📊 選択範囲からグラフを作成...") {
                viewModel.onOpenPivotChartPicker(open = true)
            }
            addMenuItem("fx 関数の挿入ウィザード (全523関数)") {
                onOpenFunctionWizardRequest?.invoke()
            }
            addMenuItem("🎨 フルカラーパレット (塗りつぶし・文字色・罫線色・RGB)") {
                onOpenColorPickerRequest?.invoke("fill")
            }
            addMenuItem("✨ AI アシスタントに集計・書式・グラフ作成を依頼") {
                onOpenAiAssistantWithPromptRequest?.invoke("")
            }
            addMenuItem("☑ チェックボックス切替 / 挿入") {
                val cur = viewModel.uiState.value.viewport.cellAt(targetRow, targetCol)?.displayText.orEmpty()
                val updated = if (InlineCheckboxHelper.containsCheckbox(cur)) {
                    InlineCheckboxHelper.toggleNthCheckbox(cur, 0) ?: cur
                } else if (cur.isBlank()) {
                    "□ チェック項目"
                } else {
                    "□ $cur"
                }
                viewModel.onCommitCellEdit(targetRow, targetCol, updated)
            }
            addDivider()
            addMenuItem("✂️ 切り取り (他アプリへも貼付可)") {
                val text = viewModel.onCutSelectionToClipboard()
                copyToSystemClipboard(context, text)
            }
            addMenuItem("📋 コピー (他アプリへも貼付可)") {
                val text = viewModel.onCopySelectionToClipboard()
                copyToSystemClipboard(context, text)
            }
            addMenuItem("📌 貼り付け (テキスト・スクショ画像対応)") {
                pasteFromSystemClipboardOrImage()
            }
            addMenuItem("📋 形式を選択して貼り付け: 値のみ (V)") {
                viewModel.onExecuteEngineCommand("edit.pasteSpecial", "{\"mode\":\"values\"}")
            }
            addMenuItem("📋 形式を選択して貼り付け: 数式のみ (F)") {
                viewModel.onExecuteEngineCommand("edit.pasteSpecial", "{\"mode\":\"formulas\"}")
            }
            addMenuItem("📋 形式を選択して貼り付け: 書式のみ (T)") {
                viewModel.onExecuteEngineCommand("edit.pasteSpecial", "{\"mode\":\"formats\"}")
            }
            addMenuItem("📋 形式を選択して貼り付け: 行列を入れ替える (E)") {
                viewModel.onExecuteEngineCommand("edit.pasteSpecial", "{\"mode\":\"transpose\"}")
            }
            addMenuItem("📸 スクショ・クリップボード画像を貼付") {
                val clipImage = readImageBytesFromSystemClipboard(context)
                if (clipImage != null) {
                    viewModel.onInsertImageBytes(clipImage.first, clipImage.second)
                } else {
                    viewModel.onPasteScreenshotOrLatestImage(null, "クリップボード画像.png")
                    if (viewModel.uiState.value.sheetObjects.none { it.kind == SheetObjectKind.IMAGE }) {
                        onRequestOpenImagePicker?.invoke()
                    }
                }
            }
            addMenuItem("🖼️ 端末内の画像ファイルを選択して挿入...") {
                onRequestOpenImagePicker?.invoke()
            }
            addDivider()
            addMenuItem("➕ 挿入: 上に行を挿入") { viewModel.onExecuteEngineCommand("home.insertRows", "{}") }
            addMenuItem("➕ 挿入: 左に列を挿入") { viewModel.onExecuteEngineCommand("home.insertColumns", "{}") }
            addMenuItem("➕ 挿入: セルを下方向にシフト") { viewModel.onExecuteEngineCommand("home.insertCells", "{\"shift\":\"down\"}") }
            addMenuItem("➖ 削除: この行を削除") { viewModel.onExecuteEngineCommand("home.deleteRows", "{}") }
            addMenuItem("➖ 削除: この列を削除") { viewModel.onExecuteEngineCommand("home.deleteColumns", "{}") }
            addMenuItem("➖ 削除: セルを上方向にシフト") { viewModel.onExecuteEngineCommand("home.deleteCells", "{\"shift\":\"up\"}") }
            addDivider()
            addMenuItem("💬 コメント / メモの追加・編集") {
                onOpenCommandCenterRequest?.invoke("comment")
            }
            addMenuItem("🔗 ハイパーリンクの挿入・編集") {
                onOpenCommandCenterRequest?.invoke("hyperlink")
            }
            addMenuItem("🏷️ 名前の定義 (Name Manager)") {
                onOpenCommandCenterRequest?.invoke("nameManager")
            }
            addDivider()
            addMenuItem("⊞ セルを結合して中央揃え") { viewModel.onExecuteEngineCommand("home.mergeCenter", "{}") }
            addMenuItem("⊟ セル結合を解除") { viewModel.onExecuteEngineCommand("home.unmergeCells", "{}") }
            addMenuItem("↩️ 折り返して全体を表示") { viewModel.onToggleWrapText() }
            addMenuItem("↔ 列幅を自動調整") { viewModel.onAutoFitColumn(targetCol) }
            addMenuItem("↕ 行高さを自動調整") { viewModel.onAutoFitRow(targetRow) }
            addDivider()
            addMenuItem("⚡ オートフィル: 下へ連続データ (+5行)") {
                val vp = viewModel.uiState.value.viewport
                val r0 = min(vp.selStartRow, vp.selEndRow)
                val r1 = max(vp.selStartRow, vp.selEndRow)
                val c0 = min(vp.selStartCol, vp.selEndCol)
                val c1 = max(vp.selStartCol, vp.selEndCol)
                viewModel.onAutoFillRange(r0, c0, r1, c1, r1 + 5, c1)
                bindState(viewModel.uiState.value)
            }
            addMenuItem("🔼 昇順並べ替え (A→Z)") { viewModel.onExecuteEngineCommand("data.sortAscending", "{}") }
            addMenuItem("🔽 降順並べ替え (Z→A)") { viewModel.onExecuteEngineCommand("data.sortDescending", "{}") }
            addMenuItem("🔽 オートフィルター切替") { viewModel.onExecuteEngineCommand("data.filter", "{}") }
            if (gridView.sheetObjects.any { it.kind == SheetObjectKind.CHART }) {
                addDivider()
                addMenuItem("📏 選択範囲をグラフの「X軸 (横軸)」に設定") {
                    viewModel.onBindSelectionAsChartXAxis()
                }
                addMenuItem("📈 選択範囲をグラフの「Y軸 (縦軸)」に設定") {
                    viewModel.onBindSelectionAsChartYAxis(append = false)
                }
                addMenuItem("➕ 選択範囲をグラフの「Y軸 (複数系列)」に追加") {
                    viewModel.onBindSelectionAsChartYAxis(append = true)
                }
                addMenuItem("📐 グラフのX軸・Y軸とデータ ソースの選択...") {
                    viewModel.onOpenChartSelectDataDialog(open = true)
                }
            }
            addMenuItem("📊 選択表からグラフを作成 (全32種)...") { viewModel.onOpenPivotChartPicker() }
            addMenuItem("📊 選択表から縦棒グラフを作成") { viewModel.onInsertChart("column") }
            addMenuItem("📈 選択表から折れ線グラフを作成") { viewModel.onInsertChart("line") }
            addMenuItem("✨ セル内スパークライン (折れ線) を挿入") {
                viewModel.onInsertSparkline(kind = "line", markers = true)
                bindState(viewModel.uiState.value)
            }
            addMenuItem("✨ セル内スパークライン (縦棒) を挿入") {
                viewModel.onInsertSparkline(kind = "column", markers = false)
                bindState(viewModel.uiState.value)
            }
            addMenuItem("🤖 マクロ・VBAスタジオを開く") { onOpenMacroStudioRequest?.invoke() }
            addMenuItem("🔓 テーブル解除 (Column1消去用)") { viewModel.onConvertToNormalRange() }
            addMenuItem("🧹 数式と値のクリア (テーブルも解除)") { viewModel.onClearSelectedCells() }
        }
    }

    val menuW = dpToPx(308f)
    val maxMenuH = dpToPx(360f)
    val availW = if (width > 0) width else 1080
    val availH = if (height > 0) height else 720
    val selRect = gridView.getSelectionScreenRect()
    val gapPx = dpToPx(18f)
    // Place menu outside the selection rectangle so it NEVER covers the bottom-right green AutoFill handle!
    val preferredLeft = when {
        selRect.width() > 0f && selRect.right + gapPx + menuW <= availW - dpToPx(4f) ->
            (selRect.right + gapPx).roundToInt()
        selRect.width() > 0f && selRect.left - gapPx - menuW >= dpToPx(4f) ->
            (selRect.left - gapPx - menuW).roundToInt()
        else ->
            (screenX.roundToInt() + gapPx).coerceIn(dpToPx(4f), (availW - menuW - dpToPx(4f)).coerceAtLeast(dpToPx(4f)))
    }
    contextMenuWidthPx = menuW
    contextMenuScrollView.measure(
        MeasureSpec.makeMeasureSpec(menuW, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(maxMenuH, MeasureSpec.AT_MOST)
    )
    val actualMenuH = contextMenuScrollView.measuredHeight.coerceIn(dpToPx(40f), maxMenuH)
    val preferredTop = if (selRect.height() > 0f && preferredLeft in (selRect.left.roundToInt() - menuW)..(selRect.right.roundToInt() + gapPx)) {
        if (selRect.bottom + gapPx + actualMenuH <= availH - dpToPx(4f)) {
            (selRect.bottom + gapPx).roundToInt()
        } else {
            (selRect.top - gapPx - actualMenuH).roundToInt().coerceAtLeast(dpToPx(4f))
        }
    } else {
        (if (selRect.height() > 0f) selRect.top.roundToInt() else screenY.roundToInt())
            .coerceIn(dpToPx(4f), (availH - actualMenuH - dpToPx(4f)).coerceAtLeast(dpToPx(4f)))
    }
    contextMenuLeftPx = preferredLeft.coerceIn(dpToPx(4f), (availW - menuW - dpToPx(4f)).coerceAtLeast(dpToPx(4f)))
    contextMenuTopPx = preferredTop.coerceIn(dpToPx(4f), (availH - actualMenuH - dpToPx(4f)).coerceAtLeast(dpToPx(4f)))
    contextMenuScrollView.visibility = View.VISIBLE
    contextMenuScrollView.bringToFront()
    layoutChildrenInPlace()
    invalidate()
}

/**
 * A10: Opens the in-cell Data Validation list picker popup directly below the active cell (`crates/ui-egui/src/interact.rs:392-398`).
 */
fun MasuSheetNativeSheetContainer.showValidationListDropdownAt(
    screenX: Float,
    screenY: Float,
    validation: NativeValidationModel
) {
    if (validation.items.isEmpty()) return
    if (isEditingInCell) {
        commitAndCloseInCellEditor(0, 0)
    }
    isContextMenuVisible = true
    val isDark = gridView.isDarkTheme
    val cardBg = if (isDark) 0xFF1E293B.toInt() else 0xFFFFFFFF.toInt()
    val cardBorder = 0xFF1F9D55.toInt()
    val itemFg = if (isDark) 0xFFF1F5F9.toInt() else 0xFF0F172A.toInt()
    val isEn = viewModel.uiState.value.language == "en"

    contextMenuScrollView.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(cardBg)
        setStroke(dpToPx(1.5f).coerceAtLeast(1), cardBorder)
        cornerRadius = dpToPx(8f).toFloat()
    }
    contextMenuList.removeAllViews()
    contextMenuList.setPadding(0, 0, 0, dpToPx(8f))

    val headerTv = TextView(context).apply {
        text = if (isEn) "▾ Select from List (${validation.items.size})" else "▾ リストから選択 (${validation.items.size}件)"
        setTextColor(0xFF1F9D55.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(6f))
    }
    contextMenuList.addView(headerTv)

    val curVal = gridView.currentViewport.activeDisplayText
    validation.items.forEach { item ->
        val isSelected = item == curVal
        val tv = TextView(context).apply {
            text = if (isSelected) "✓ $item" else "   $item"
            setTextColor(if (isSelected) 0xFF4ADE80.toInt() else itemFg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
            isClickable = true
            setOnClickListener {
                dismissContextMenu()
                viewModel.onSelectValidationListItem(item)
                bindState(viewModel.uiState.value)
                gridView.invalidate()
            }
        }
        contextMenuList.addView(tv)
    }

    val cellRect = gridView.getSelectionScreenRect()
    val menuW = dpToPx(220f)
    val maxMenuH = dpToPx(280f)
    contextMenuWidthPx = menuW
    contextMenuScrollView.measure(
        MeasureSpec.makeMeasureSpec(menuW, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(maxMenuH, MeasureSpec.AT_MOST)
    )
    val actualMenuH = contextMenuScrollView.measuredHeight.coerceIn(dpToPx(40f), maxMenuH)
    val availW = if (width > 0) width else 1080
    val availH = if (height > 0) height else 720
    contextMenuLeftPx = cellRect.left.roundToInt().coerceIn(dpToPx(4f), (availW - menuW - dpToPx(4f)).coerceAtLeast(dpToPx(4f)))
    contextMenuTopPx = (cellRect.bottom.roundToInt() + dpToPx(2f)).coerceIn(dpToPx(4f), (availH - actualMenuH - dpToPx(4f)).coerceAtLeast(dpToPx(4f)))
    contextMenuScrollView.visibility = View.VISIBLE
    contextMenuScrollView.bringToFront()
    layoutChildrenInPlace()
    invalidate()
}
