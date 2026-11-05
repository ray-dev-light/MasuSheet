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
import android.view.ScaleGestureDetector
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
import com.masusheet.app.MasuSheetNativeGridView.*

/**
 * ============================================================================
 * [EN] Touch, Stylus/Mouse, Trackpad Pinch, & Keyboard Navigation / Object Nudge Handler
 * Handles 1-finger touch scroll vs range-select mode (`H-12`), mouse/trackpad wheel & pinch zoom, header resize/AutoFit, object move/resize/rotate, and keyboard navigation (`1pt` arrow nudge).
 *
 * ---
 * [JA / 日本語解説]
 * 1本指タッチスクロール／範囲選択モード切替 (`H-12`)、マウス・トラックパッド操作、行列リサイズ／自動調整、オブジェクトの移動・リサイズ・回転、およびキーボード操作（矢印1pt微調整含む）を処理します。
 * ============================================================================
 */

/**
 * Handles right-click (`BUTTON_SECONDARY`) or long-press at `(screenX, screenY)` by selecting
 * the target object/cell/column/row and displaying the native context menu.
 */
fun MasuSheetNativeGridView.handleRightClickAt(screenX: Float, screenY: Float): Boolean {
    val snap = currentViewport
    if (screenY < colHeaderHeightPx && screenX >= rowHeaderWidthPx) {
        val sheetXPt = pxToPt((screenX - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
        val col = snap.colAtX(sheetXPt)
        val c0 = min(snap.selStartCol, snap.selEndCol)
        val c1 = max(snap.selStartCol, snap.selEndCol)
        if (col !in c0..c1 || min(snap.selStartRow, snap.selEndRow) > 0) {
            viewModel.onSelectEntireColumn(col, col)
        }
        container.bindState(viewModel.uiState.value)
        container.showContextMenuAt(screenX, screenY, ContextMenuTargetType.COLUMN_HEADER, snap.activeRow, col)
        return true
    }
    if (screenX < rowHeaderWidthPx && screenY >= colHeaderHeightPx) {
        val sheetYPt = pxToPt((screenY - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
        val row = snap.rowAtY(sheetYPt)
        val r0 = min(snap.selStartRow, snap.selEndRow)
        val r1 = max(snap.selStartRow, snap.selEndRow)
        if (row !in r0..r1 || min(snap.selStartCol, snap.selEndCol) > 0) {
            viewModel.onSelectEntireRow(row, row)
        }
        container.bindState(viewModel.uiState.value)
        container.showContextMenuAt(screenX, screenY, ContextMenuTargetType.ROW_HEADER, row, snap.activeCol)
        return true
    }
    // Check if right-clicking on an inserted Sheet Object (Shape, TextBox, Chart, Image)
    val hitPair = findSheetObjectAt(screenX, screenY)
    if (hitPair != null) {
        val (hitObj, _) = hitPair
        if (hitObj.kind == SheetObjectKind.CHART && selectedObjectIds.size <= 1) {
            val subElem = hitTestChartInteractiveElement(hitObj, screenX, screenY)
            if (subElem.isNotBlank()) {
                viewModel.onSelectChartSubElement(hitObj.id, subElem)
            } else if (hitObj.id !in selectedObjectIds) {
                viewModel.onSelectSheetObject(hitObj.id)
            }
        } else if (hitObj.id !in selectedObjectIds) {
            viewModel.onSelectSheetObject(hitObj.id)
        }
        container.bindState(viewModel.uiState.value)
        container.showContextMenuAt(
            screenX = screenX,
            screenY = screenY,
            targetType = ContextMenuTargetType.SHEET_OBJECT,
            targetRow = snap.activeRow,
            targetCol = snap.activeCol,
            targetObjectId = hitObj.id
        )
        return true
    }

    val sheetXPt = pxToPt((screenX - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
    val sheetYPt = pxToPt((screenY - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
    val rawRow = snap.rowAtY(sheetYPt)
    val rawCol = snap.colAtX(sheetXPt)
    val (anchorR, anchorC) = snap.resolveAnchorCell(rawRow, rawCol)
    // Preserve multi-cell selection if right-clicking inside the already selected 2D range!
    val r0 = min(snap.selStartRow, snap.selEndRow)
    val r1 = max(snap.selStartRow, snap.selEndRow)
    val c0 = min(snap.selStartCol, snap.selEndCol)
    val c1 = max(snap.selStartCol, snap.selEndCol)
    if (anchorR !in r0..r1 || anchorC !in c0..c1) {
        viewModel.onSelectCell(anchorR, anchorC)
    }
    container.bindState(viewModel.uiState.value)
    container.showContextMenuAt(screenX, screenY, ContextMenuTargetType.CELL, anchorR, anchorC)
    return true
}

/**
 * [EN] Grid canvas helper that executes `handleGenericMotionInput` (handle generic motion input) on [MasuSheetNativeGridView].
 * [JA] グリッドキャンバス ([MasuSheetNativeGridView]) における `handleGenericMotionInput` 処理を実行します。
 */
internal fun MasuSheetNativeGridView.handleGenericMotionInput(event: MotionEvent): Boolean? {
    if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
        event.actionButton == MotionEvent.BUTTON_SECONDARY
    ) {
        return handleRightClickAt(event.x, event.y)
    }
    // [EN] Trackpad two-finger pinch-to-zoom via Android 14+ CLASSIFICATION_PINCH
    // [JA] Android 14+ トラックパッドの2本指ピンチズーム操作 (CLASSIFICATION_PINCH)
    if (Build.VERSION.SDK_INT >= 34 &&
        event.classification == MotionEvent.CLASSIFICATION_PINCH
    ) {
        val pinchFactor = event.getAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR)
        if (pinchFactor > 0f && pinchFactor != 1.0f) {
            scroller.forceFinished(true)
            applyPinchScaleFactor(pinchFactor, event.x, event.y)
            return true
        }
    }
    if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
        if (Build.VERSION.SDK_INT >= 34) {
            val pinchFactor = event.getAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR)
            if (pinchFactor > 0f && pinchFactor != 1.0f) {
                scroller.forceFinished(true)
                applyPinchScaleFactor(pinchFactor, event.x, event.y)
                return true
            }
        }
        val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
        val isCtrlOrMetaHeld = (event.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON)) != 0
        // [EN] Trackpad pinch emulation / Ctrl + Wheel zoom
        // [JA] Ctrl + ホイールまたはトラックパッドピンチによる拡大縮小
        if (isCtrlOrMetaHeld && vScroll != 0f) {
            scroller.forceFinished(true)
            val factor = Math.pow(1.08, vScroll.toDouble()).toFloat().coerceIn(0.80f, 1.25f)
            applyPinchScaleFactor(factor, event.x, event.y)
            return true
        }
        if (vScroll != 0f || hScroll != 0f) {
            val (dxPt, dyPt) = ScrollInputMath.computeWheelScrollDelta(hScroll, vScroll, stepPoints = 72f)
            val deltaXPx = -(dxPt * density).roundToInt()
            val deltaYPx = -(dyPt * density).roundToInt()
            scroller.startScroll(
                scrollXPx,
                scrollYPx,
                deltaXPx,
                deltaYPx,
                180
            )
            postInvalidateOnAnimation()
            return true
        }
    }
    return null
}

/**
 * [EN] Grid canvas helper that executes `handleTouchInput` (handle touch input) on [MasuSheetNativeGridView].
 * [JA] グリッドキャンバス ([MasuSheetNativeGridView]) における `handleTouchInput` 処理を実行します。
 */
internal fun MasuSheetNativeGridView.handleTouchInput(event: MotionEvent): Boolean? {
    // [EN] Handle Android 14+ trackpad two-finger pinch-to-zoom delivered via onTouchEvent
    // [JA] onTouchEvent 経由で届く Android 14+ トラックパッドの2本指ピンチズーム操作を処理
    if (Build.VERSION.SDK_INT >= 34 &&
        event.classification == MotionEvent.CLASSIFICATION_PINCH
    ) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                isPinchZooming = true
                pinchAccumulatedZoom = zoomPercent.toFloat()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val factor = event.getAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR)
                if (factor > 0f && factor != 1.0f) {
                    applyPinchScaleFactor(factor, event.x, event.y)
                } else if (event.pointerCount >= 2) {
                    scaleGestureDetector.onTouchEvent(event)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPinchZooming = false
                return true
            }
        }
    }

    // Handle Android 14+ two-finger trackpad swipe with native velocity & inertial fling
    if (Build.VERSION.SDK_INT >= 34 &&
        event.classification == MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE
    ) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastSwipeX = event.x
                lastSwipeY = event.y
                swipeTrackingActive = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeTrackingActive) {
                    lastSwipeX = event.x
                    lastSwipeY = event.y
                    swipeTrackingActive = true
                    return true
                }
                val dxPx = (lastSwipeX - event.x).roundToInt()
                val dyPx = (lastSwipeY - event.y).roundToInt()
                lastSwipeX = event.x
                lastSwipeY = event.y
                scrollXPx = (scrollXPx + dxPx).coerceIn(0, maxScrollXPx())
                scrollYPx = (scrollYPx + dyPx).coerceIn(0, maxScrollYPx())
                if (container.isEditingInCell) container.updateInCellEditorBounds()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                swipeTrackingActive = false
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = -(velocityTracker?.xVelocity ?: 0f).roundToInt()
                val vy = -(velocityTracker?.yVelocity ?: 0f).roundToInt()
                scroller.fling(scrollXPx, scrollYPx, vx, vy, 0, maxScrollXPx(), 0, maxScrollYPx())
                velocityTracker?.recycle()
                velocityTracker = null
                postInvalidateOnAnimation()
                return true
            }
        }
    }

    val isMouse = event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
    val isSecondaryClick = (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0
    val isShiftHeld = (event.metaState and KeyEvent.META_SHIFT_ON) != 0
    val isCtrlHeld = (event.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON)) != 0

    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        hasMovedSinceDown = false
    } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
        if (hypot(event.x - downX, event.y - downY) > 6f * density) {
            hasMovedSinceDown = true
        }
    }

    if (!isMouse &&
        !isDraggingFillHandle &&
        !isPointOnSelectionCornerHandle(event.x, event.y) &&
        !isPointOnTopLeftSelectionRangeHandle(event.x, event.y)
    ) {
        longPressGestureDetector.onTouchEvent(event)
    }

    if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
    velocityTracker?.addMovement(event)

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            scroller.forceFinished(true)
            downX = event.x
            downY = event.y
            lastTouchX = event.x
            lastTouchY = event.y
            isPanningTouch = false
            isDraggingFillHandle = false
            isDraggingSelectionMove = false
            isDraggingChartAxisRangeMove = false
            isDraggingColMove = false
            isDraggingFreezeRowHandle = false
            isDraggingFreezeColHandle = false
            isDraggingRangeSelection = false
            isDraggingColHeaderSelection = false
            isDraggingRowHeaderSelection = false
            isDraggingFormulaPointRange = false
            isDraggingFormulaPointCol = false
            isDraggingFormulaPointRow = false
            pendingTouchTapSelect = false
            pendingDoubleTapOpenEditor = false
            draggingObjectId = null
            activeObjectResizePart = null
            isRotatingObject = false

            // Right-click (BUTTON_SECONDARY) opens the context menu immediately!
            if (isSecondaryClick) {
                return handleRightClickAt(event.x, event.y)
            }

            // Dismiss context menu if clicking outside it
            if (container.isContextMenuVisible) {
                container.dismissContextMenu()
            }

            // 0. Click on Top-Left Select-All Corner (◢ above Row 1 and left of Col A) selects the entire sheet
            if (event.x < rowHeaderWidthPx && event.y < colHeaderHeightPx) {
                if (container.isEditingInCell) {
                    container.commitAndCloseInCellEditor(0, 0)
                }
                viewModel.onSelectAllCells()
                container.bindState(viewModel.uiState.value)
                container.notifySelectedCellChanged()
                invalidate()
                return true
            }

            // 1. Check Column Header Divider (A|B, B|C, ...) for Drag Resize, Double-Click AutoFit, or A12 Unhide
            val colDiv = findColumnDividerAt(event.x, event.y)
            if (colDiv >= 0) {
                if (container.isEditingInCell) {
                    container.commitAndCloseInCellEditor(0, 0)
                }
                val now = System.currentTimeMillis()
                if (colDiv == lastDividerClickIndex && (now - lastDividerClickTimeMs) <= 400L) {
                    lastDividerClickIndex = -1
                    val hasHiddenAdjacentCol = (
                        currentViewport.colWidths.getOrElse(colDiv) { 72f } < 1f ||
                            currentViewport.colWidths.getOrElse(colDiv + 1) { 72f } < 1f
                        )
                    if (hasHiddenAdjacentCol) {
                        viewModel.onUnhideColumnsAround(colDiv)
                    } else {
                        viewModel.onAutoFitColumn(colDiv)
                    }
                    container.bindState(viewModel.uiState.value)
                    return true
                }
                lastDividerClickIndex = colDiv
                lastDividerClickTimeMs = now
                resizingColIndex = colDiv
                resizeStartScreenPos = event.x
                resizeInitialSizePt = currentViewport.colWidths.getOrElse(colDiv) { 72f }
                invalidate()
                return true
            }

            // 2. Check Row Header Divider (1|2, 2|3, ...) for Drag Resize, Double-Click AutoFit, or A12 Unhide
            val rowDiv = findRowDividerAt(event.x, event.y)
            if (rowDiv >= 0) {
                if (container.isEditingInCell) {
                    container.commitAndCloseInCellEditor(0, 0)
                }
                val now = System.currentTimeMillis()
                if (rowDiv == lastDividerClickIndex && (now - lastDividerClickTimeMs) <= 400L) {
                    lastDividerClickIndex = -1
                    val hasHiddenAdjacentRow = (
                        currentViewport.rowHeights.getOrElse(rowDiv) { 22f } < 1f ||
                            currentViewport.rowHeights.getOrElse(rowDiv + 1) { 22f } < 1f
                        )
                    if (hasHiddenAdjacentRow) {
                        viewModel.onUnhideRowsAround(rowDiv)
                    } else {
                        viewModel.onAutoFitRow(rowDiv)
                    }
                    container.bindState(viewModel.uiState.value)
                    return true
                }
                lastDividerClickIndex = rowDiv
                lastDividerClickTimeMs = now
                resizingRowIndex = rowDiv
                resizeStartScreenPos = event.y
                resizeInitialSizePt = currentViewport.rowHeights.getOrElse(rowDiv) { 22f }
                invalidate()
                return true
            }

            // 2b (A11). Check Draggable Freeze Pane Dividers (`Drag::Freeze`, `interact.rs:371-375`)
            if (findFreezeRowDividerAt(event.x, event.y)) {
                if (container.isEditingInCell) {
                    container.commitAndCloseInCellEditor(0, 0)
                }
                isDraggingFreezeRowHandle = true
                freezePreviewRow = freezeRows
                freezePreviewCol = freezeCols
                invalidate()
                return true
            }
            if (findFreezeColDividerAt(event.x, event.y)) {
                if (container.isEditingInCell) {
                    container.commitAndCloseInCellEditor(0, 0)
                }
                isDraggingFreezeColHandle = true
                freezePreviewRow = freezeRows
                freezePreviewCol = freezeCols
                invalidate()
                return true
            }

            // 3. Click / Drag on Column Header (A, B, C...) selects entire column(s), reorders selected columns (A8), or inserts C:C in Formula Point Mode
            if (event.y < colHeaderHeightPx && event.x >= rowHeaderWidthPx) {
                val sheetXPt = pxToPt((event.x - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
                val col = currentViewport.colAtX(sheetXPt)
                if (container.canPointFormulaReference()) {
                    val startCol = if (isShiftHeld && dragAnchorCol >= 0) dragAnchorCol else col
                    dragAnchorRow = 0
                    dragAnchorCol = startCol
                    isDraggingFormulaPointCol = event.pointerCount == 1
                    val refStr = container.buildSheetQualifiedColRangeRef(startCol, col)
                    container.insertOrUpdatePointedReference(
                        newRef = refStr,
                        appendUnion = isCtrlHeld && !isShiftHeld
                    )
                    return true
                }
                if (container.isEditingInCell) {
                    container.commitAndCloseInCellEditor(0, 0)
                }
                val snap = currentViewport
                val selC0 = min(snap.selStartCol, snap.selEndCol)
                val selC1 = max(snap.selStartCol, snap.selEndCol)
                val selR0 = min(snap.selStartRow, snap.selEndRow)
                val selR1 = max(snap.selStartRow, snap.selEndRow)
                val isFullColSelected = selR0 == 0 && selR1 >= (snap.rowHeights.size - 1).coerceAtLeast(0)
                // A8: If pressing on an already-selected full-column header without Shift, allow drag-reordering columns (`Drag::MoveCols`)
                if (!isShiftHeld && isFullColSelected && col in selC0..selC1 && event.pointerCount == 1) {
                    isDraggingColMove = true
                    colMoveStartCol = selC0
                    colMoveEndCol = selC1
                    colMoveTargetBeforeCol = selC0
                    invalidate()
                    return true
                }
                val startCol = if (isShiftHeld) currentViewport.selStartCol else col
                dragAnchorRow = 0
                dragAnchorCol = startCol
                isDraggingColHeaderSelection = event.pointerCount == 1
                viewModel.onSelectEntireColumn(startCol, col)
                container.bindState(viewModel.uiState.value)
                container.notifySelectedCellChanged()
                return true
            }

            // 4. Click / Drag on Row Header (1, 2, 3...) selects entire row(s) or inserts 4:4 in Formula Point Mode
            if (event.x < rowHeaderWidthPx && event.y >= colHeaderHeightPx) {
                val sheetYPt = pxToPt((event.y - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
                val row = currentViewport.rowAtY(sheetYPt)
                if (container.canPointFormulaReference()) {
                    val startRow = if (isShiftHeld && dragAnchorRow >= 0) dragAnchorRow else row
                    dragAnchorRow = startRow
                    dragAnchorCol = 0
                    isDraggingFormulaPointRow = event.pointerCount == 1
                    val refStr = container.buildSheetQualifiedRowRangeRef(startRow, row)
                    container.insertOrUpdatePointedReference(
                        newRef = refStr,
                        appendUnion = isCtrlHeld && !isShiftHeld
                    )
                    return true
                }
                if (container.isEditingInCell) {
                    container.commitAndCloseInCellEditor(0, 0)
                }
                val startRow = if (isShiftHeld) currentViewport.selStartRow else row
                dragAnchorRow = startRow
                dragAnchorCol = 0
                isDraggingRowHeaderSelection = event.pointerCount == 1
                viewModel.onSelectEntireRow(startRow, row)
                container.bindState(viewModel.uiState.value)
                container.notifySelectedCellChanged()
                return true
            }

            // 5. Click inside Sheet Grid Area
            if (event.x >= rowHeaderWidthPx && event.y >= colHeaderHeightPx) {
                val rawRow = screenYToSheetRow(event.y)
                val rawCol = screenXToSheetCol(event.x)
                val (anchorR, anchorC) = currentViewport.resolveAnchorCell(rawRow, rawCol)

                // 5-point. Formula Point Mode (= + clicking/dragging another cell or range, matching Spreadsheet & original Rust MasuSheet!)
                if (container.canPointFormulaReference()) {
                    val isSameEditingCell = (
                        currentViewport.sheetIndex == container.editingSheetIndex &&
                            anchorR == container.editingCellRow() &&
                            anchorC == container.editingCellCol()
                        )
                    if (!isSameEditingCell) {
                        val startR = if (isShiftHeld && dragAnchorRow >= 0) dragAnchorRow else anchorR
                        val startC = if (isShiftHeld && dragAnchorCol >= 0) dragAnchorCol else anchorC
                        dragAnchorRow = startR
                        dragAnchorCol = startC
                        isDraggingFormulaPointRange = event.pointerCount == 1
                        val refStr = container.buildSheetQualifiedCellOrRangeRef(startR, startC, anchorR, anchorC)
                        container.insertOrUpdatePointedReference(
                            newRef = refStr,
                            appendUnion = isCtrlHeld && !isShiftHeld,
                            anchorRow = startR,
                            anchorCol = startC,
                            cursorRow = anchorR,
                            cursorCol = anchorC
                        )
                    }
                    return true
                }

                // 5a (A5). Check if user is dragging or double-tapping the green bottom-right AutoFill handle (Spreadsheet Fill Series / Copy)
                if (selectedObjectId == null && isPointOnSelectionCornerHandle(event.x, event.y)) {
                    if (container.isEditingInCell) {
                        container.commitAndCloseInCellEditor(0, 0)
                    }
                    val nowCorner = System.currentTimeMillis()
                    if ((nowCorner - lastFillCornerTapTimeMs) <= 400L) {
                        lastFillCornerTapTimeMs = 0L
                        viewModel.onDoubleTapAutoFillCornerHandle()
                        container.bindState(viewModel.uiState.value)
                        container.notifySelectedCellChanged()
                        invalidate()
                        return true
                    }
                    lastFillCornerTapTimeMs = nowCorner
                    val snap = currentViewport
                    fillSrcStartRow = min(snap.selStartRow, snap.selEndRow)
                    fillSrcStartCol = min(snap.selStartCol, snap.selEndCol)
                    fillSrcEndRow = max(snap.selStartRow, snap.selEndRow)
                    fillSrcEndCol = max(snap.selStartCol, snap.selEndCol)
                    fillTargetEndRow = fillSrcEndRow
                    fillTargetEndCol = fillSrcEndCol
                    isDraggingFillHandle = true
                    invalidate()
                    return true
                }

                // 5a-val (A10). Check if user tapped on the Data Validation `[▾]` dropdown button on the active cell!
                if (selectedObjectId == null && isPointOnValidationDropdownBadge(event.x, event.y)) {
                    val valModel = viewModel.getActiveCellValidation()
                    if (valModel != null) {
                        if (container.isEditingInCell) {
                            container.commitAndCloseInCellEditor(0, 0)
                        }
                        container.showValidationListDropdownAt(event.x, event.y, valModel)
                        return true
                    }
                }

                // 5a-tl-range. Check if user grabbed the circular top-left selection range handle (`●`) on a multi-cell selection
                if (selectedObjectId == null && isPointOnTopLeftSelectionRangeHandle(event.x, event.y)) {
                    if (container.isEditingInCell) {
                        container.commitAndCloseInCellEditor(0, 0)
                    }
                    val snap = currentViewport
                    dragAnchorRow = max(snap.selStartRow, snap.selEndRow)
                    dragAnchorCol = max(snap.selStartCol, snap.selEndCol)
                    isDraggingRangeSelection = event.pointerCount == 1
                    invalidate()
                    return true
                }

                // 5a-move (A7). Mouse-only: dragging the selection border moves selected cells (`Drag::Move`, `interact.rs:457-459`).
                // Disabled for finger/stylus touch so pressing inside a selected cell expands the selection range cleanly without cutting/moving data.
                if (isMouse && selectedObjectId == null && isPointOnSelectionBorder(event.x, event.y)) {
                    if (container.isEditingInCell) {
                        container.commitAndCloseInCellEditor(0, 0)
                    }
                    val snap = currentViewport
                    moveSrcStartRow = min(snap.selStartRow, snap.selEndRow)
                    moveSrcStartCol = min(snap.selStartCol, snap.selEndCol)
                    moveSrcEndRow = max(snap.selStartRow, snap.selEndRow)
                    moveSrcEndCol = max(snap.selStartCol, snap.selEndCol)
                    moveAnchorOffsetRow = (rawRow - moveSrcStartRow).coerceIn(0, (moveSrcEndRow - moveSrcStartRow).coerceAtLeast(0))
                    moveAnchorOffsetCol = (rawCol - moveSrcStartCol).coerceIn(0, (moveSrcEndCol - moveSrcStartCol).coerceAtLeast(0))
                    moveTargetStartRow = moveSrcStartRow
                    moveTargetStartCol = moveSrcStartCol
                    isDraggingSelectionMove = true
                    invalidate()
                    return true
                }

                // 5b. Check if user clicked an inserted Sheet Object (Shape / TextBox / Image / Chart)
                val isFingerTouch = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER && !isMouse
                val isPickingChartRange = viewModel.uiState.value.chartRangePickingObjectId != null
                val hadObjectSelection = (selectedObjectId != null || selectedObjectIds.isNotEmpty()) && !isPickingChartRange
                val hitPair = findSheetObjectAt(event.x, event.y)
                if (hitPair != null) {
                    val (hitObj, hitPart) = hitPair
                    val isResizeHandle = hitPart != SheetObjectHitPart.BODY && hitPart != SheetObjectHitPart.ROTATE_HANDLE
                    val isRotHandle = hitPart == SheetObjectHitPart.ROTATE_HANDLE
                    if (container.isEditingInCell) {
                        container.commitAndCloseInCellEditor(0, 0)
                    }
                    val now = System.currentTimeMillis()
                    val isDoubleObjClick = (hitObj.id == lastObjectClickId && (now - lastObjectClickTimeMs) <= 450L)
                    lastObjectClickId = hitObj.id
                    lastObjectClickTimeMs = now

                    if (isShiftHeld && !isResizeHandle && !isRotHandle) {
                        viewModel.onSelectSheetObject(hitObj.id, appendToggle = true)
                    } else if (hitObj.kind == SheetObjectKind.CHART && !isResizeHandle && !isRotHandle && selectedObjectIds.size <= 1) {
                        val subElem = hitTestChartInteractiveElement(hitObj, event.x, event.y)
                        viewModel.onSelectChartSubElement(hitObj.id, subElem)
                    } else if (hitObj.id !in selectedObjectIds || isResizeHandle || isRotHandle) {
                        viewModel.onSelectSheetObject(hitObj.id, appendToggle = false)
                    }
                    container.bindState(viewModel.uiState.value)

                    // If Shift+Click toggled this object OFF, do not start dragging it
                    if (hitObj.id !in selectedObjectIds) {
                        invalidate()
                        return true
                    }

                    if (isDoubleObjClick && !isResizeHandle && !isRotHandle && hitObj.kind != SheetObjectKind.IMAGE) {
                        // Open direct in-place editor on the canvas right over the double-clicked shape/chart element!
                        if (hitObj.kind == SheetObjectKind.CHART) {
                            val chartTarget = resolveChartSubElementAt(hitObj, event.x, event.y)
                            container.openInObjectEditor(
                                obj = hitObj,
                                targetType = chartTarget.targetType,
                                subIndex1 = chartTarget.subIndex1,
                                subIndex2 = chartTarget.subIndex2,
                                targetScreenRect = chartTarget.screenBounds,
                                initialText = chartTarget.initialText
                            )
                        } else {
                            container.openInObjectEditor(
                                obj = hitObj,
                                targetType = InPlaceObjectEditTargetType.OBJECT_MAIN_TEXT,
                                targetScreenRect = getSheetObjectScreenRect(hitObj),
                                initialText = hitObj.title
                            )
                        }
                        return true
                    }

                    draggingObjectId = hitObj.id
                    activeObjectResizePart = if (isResizeHandle) hitPart else null
                    isRotatingObject = isRotHandle
                    objectDragStartScreenX = event.x
                    objectDragStartScreenY = event.y
                    objectStartXPts = hitObj.xPt
                    objectStartYPts = hitObj.yPt
                    objectStartWPts = hitObj.widthPt
                    objectStartHPts = hitObj.heightPt
                    multiDragStartPositions = sheetObjects
                        .filter { it.id in selectedObjectIds }
                        .associate { it.id to (it.xPt to it.yPt) }
                    invalidate()
                    return true
                } else if (selectedObjectId != null || selectedObjectIds.isNotEmpty()) {
                    // 5b-chart-range. Check if the user grabbed the highlighted X-Axis or Y-Axis cell range frame/corner/badge on the sheet!
                    val chartRangeHit = findChartSourceRangeDragHitAt(event.x, event.y)
                    if (chartRangeHit != null) {
                        if (container.isEditingInCell) {
                            container.commitAndCloseInCellEditor(0, 0)
                        }
                        isDraggingChartAxisRangeMove = true
                        chartAxisDragMode = chartRangeHit.dragMode
                        chartAxisMoveChartId = chartRangeHit.chartId
                        chartAxisMoveSeriesIdx = chartRangeHit.seriesIndex
                        chartAxisMoveIsX = chartRangeHit.isXAxis
                        chartAxisMoveSrcStartRow = chartRangeHit.bounds[0]
                        chartAxisMoveSrcStartCol = chartRangeHit.bounds[1]
                        chartAxisMoveSrcEndRow = chartRangeHit.bounds[2]
                        chartAxisMoveSrcEndCol = chartRangeHit.bounds[3]
                        // Exact delta anchor from the touched cell (never clamp, so wide or stacked badges don't jump on move!)
                        chartAxisMoveAnchorOffsetRow = rawRow - chartAxisMoveSrcStartRow
                        chartAxisMoveAnchorOffsetCol = rawCol - chartAxisMoveSrcStartCol
                        chartAxisMoveTargetStartRow = chartAxisMoveSrcStartRow
                        chartAxisMoveTargetStartCol = chartAxisMoveSrcStartCol
                        chartAxisPreviewStartRow = chartAxisMoveSrcStartRow
                        chartAxisPreviewStartCol = chartAxisMoveSrcStartCol
                        chartAxisPreviewEndRow = chartAxisMoveSrcEndRow
                        chartAxisPreviewEndCol = chartAxisMoveSrcEndCol
                        viewModel.onSelectChartSubElement(
                            chartRangeHit.chartId,
                            if (chartRangeHit.isXAxis) "xAxis" else "series:${chartRangeHit.seriesIndex}"
                        )
                        container.bindState(viewModel.uiState.value)
                        invalidate()
                        return true
                    }
                    // [EN] On finger touch, keep the chart/image/shape selected on ACTION_DOWN so 1-finger swipe scrolls
                    //      the sheet while preserving object selection; only deselect on ACTION_UP if tapped without scrolling.
                    // [JA] 指タッチ時は ACTION_DOWN で即座に選択解除せず、グラフや画像を選択したままスワイプでシートをスクロール可能に維持
                    //      （スクロールせずそのまま指を離したタップ時のみセル選択へ切り替え）。
                    if (!isFingerTouch || isPickingChartRange) {
                        viewModel.onSelectSheetObject(null)
                    }
                }

                // If in-cell editor is open (not in Formula Point Mode), commit it first automatically!
                if (container.isEditingInCell) {
                    container.commitAndCloseInCellEditor(0, 0)
                }

                // 5c. Check if user tapped on the `[▾]` AutoFilter dropdown button of a Table Header cell!
                if (viewModel.isTableHeaderCell(currentViewport, anchorR, anchorC)) {
                    val cellRect = getCellScreenRect(anchorR, anchorC)
                    val filterHitLeft = (cellRect.right - 26f * density * zoomScale)
                        .coerceAtLeast(cellRect.left + cellRect.width() * 0.55f)
                    if (event.x >= filterHitLeft && event.x <= cellRect.right + 4f * density) {
                        viewModel.onSelectCell(anchorR, anchorC)
                        viewModel.onOpenColumnFilterMenu(anchorR, anchorC)
                        container.bindState(viewModel.uiState.value)
                        container.notifySelectedCellChanged()
                        invalidate()
                        return true
                    }
                }

                val eventNowMs = event.eventTime.takeIf { it > 0L } ?: System.currentTimeMillis()
                val isDoubleClick = (anchorR == lastClickRow && anchorC == lastClickCol && (eventNowMs - lastClickTimeMs) in 0L..400L)
                lastClickRow = anchorR
                lastClickCol = anchorC
                lastClickTimeMs = eventNowMs

                if (isDoubleClick) {
                    if (!isFingerTouch) {
                        // Mouse / Stylus double-click -> Open native Android EditText over the cell with smart caret placement!
                        viewModel.onSelectCell(anchorR, anchorC)
                        container.bindState(viewModel.uiState.value)
                        container.openInCellEditor(anchorR, anchorC, event.x, event.y)
                        return true
                    } else {
                        // [EN] Defer touch double-tap editor opening until ACTION_UP without movement so rapid "tap + drag" expands range cleanly
                        // [JA] タッチで「セルをタップしてすぐ押さえてドラッグ」した際にキーボードが誤起動しないよう、指を離すまで編集開始を待機
                        pendingDoubleTapOpenEditor = true
                    }
                }

                // Single-click on an inline checkbox toggles it immediately
                val clickedBoxIdx = resolveClickedCheckboxIndex(anchorR, anchorC, event.x)
                if (clickedBoxIdx != null) {
                    pendingDoubleTapOpenEditor = false
                    viewModel.onToggleCellCheckbox(anchorR, anchorC, clickedBoxIdx)
                    container.bindState(viewModel.uiState.value)
                    container.notifySelectedCellChanged()
                    return true
                }

                // Shift+Click extends the 2D range selection from active cell to clicked cell!
                if (isShiftHeld) {
                    pendingDoubleTapOpenEditor = false
                    val startR = currentViewport.activeRow
                    val startC = currentViewport.activeCol
                    dragAnchorRow = startR
                    dragAnchorCol = startC
                    isDraggingRangeSelection = true
                    viewModel.onSelectCell(startR, startC, anchorR, anchorC)
                    container.bindState(viewModel.uiState.value)
                    container.notifySelectedCellChanged()
                    invalidate()
                    return true
                }

                // [EN] Smart Touch vs Pointer Range Selection:
                //  - Mouse or Stylus (S-Pen): 1-stroke drag on any cell immediately selects a 2D cell range.
                //  - Finger Touch:
                //    1) When no floating object (chart/image/shape) is selected, pressing inside the currently selected
                //       cell/range and dragging immediately expands/adjusts the 2D cell selection.
                //    2) Swiping on unselected cells — OR swiping anywhere outside a selected chart/image/shape — scrolls
                //       the sheet smoothly while keeping the current cell or object selection intact; lifting without
                //       scrolling selects the tapped cell cleanly on ACTION_UP.
                // [JA] スマートフォン・タブレットとマウス/ペンの最適共存設計：
                //  - マウス・スタイラス（Sペン）時：どのセルの中央からでも1ストロークで範囲選択。
                //  - 指タッチ時：オブジェクト未選択中に選択中セル（または選択範囲内）を押してドラッグすると範囲選択が広がり、
                //    未選択セルやグラフ・画像選択中のシート上スワイプでは選択状態を維持したまま滑らかにスクロール（タップ時は離した瞬間にそのセルを選択）。
                val prevSnap = currentViewport
                val prevR0 = min(prevSnap.selStartRow, prevSnap.selEndRow)
                val prevR1 = max(prevSnap.selStartRow, prevSnap.selEndRow)
                val prevC0 = min(prevSnap.selStartCol, prevSnap.selEndCol)
                val prevC1 = max(prevSnap.selStartCol, prevSnap.selEndCol)
                val isInsideCurrentSelection = !hadObjectSelection && anchorR in prevR0..prevR1 && anchorC in prevC0..prevC1
                val isMultiCellSelection = prevR0 != prevR1 || prevC0 != prevC1

                if (!isFingerTouch) {
                    dragAnchorRow = anchorR
                    dragAnchorCol = anchorC
                    isDraggingRangeSelection = event.pointerCount == 1
                    viewModel.onSelectCell(anchorR, anchorC)
                    container.bindState(viewModel.uiState.value)
                    container.notifySelectedCellChanged()
                    invalidate()
                } else if (isInsideCurrentSelection) {
                    isDraggingRangeSelection = event.pointerCount == 1
                    if (!isMultiCellSelection) {
                        dragAnchorRow = prevR0
                        dragAnchorCol = prevC0
                    } else {
                        pendingTouchTapSelect = true
                        pendingTouchTapRow = anchorR
                        pendingTouchTapCol = anchorC
                        if (anchorR == prevR1 && anchorC == prevC1) {
                            dragAnchorRow = prevR0
                            dragAnchorCol = prevC0
                        } else {
                            dragAnchorRow = anchorR
                            dragAnchorCol = anchorC
                        }
                    }
                    invalidate()
                } else {
                    dragAnchorRow = anchorR
                    dragAnchorCol = anchorC
                    isDraggingRangeSelection = false
                    pendingTouchTapSelect = event.pointerCount == 1
                    pendingTouchTapRow = anchorR
                    pendingTouchTapCol = anchorC
                }
            }
            return true
        }

        MotionEvent.ACTION_POINTER_DOWN -> {
            if (event.pointerCount >= 2) {
                scroller.forceFinished(true)
                cancelPendingObjectDragKeepSelection()
                isDraggingFillHandle = false
                isDraggingSelectionMove = false
                isDraggingChartAxisRangeMove = false
                isDraggingColMove = false
                isDraggingFreezeRowHandle = false
                isDraggingFreezeColHandle = false
                isDraggingRangeSelection = false
                isDraggingColHeaderSelection = false
                isDraggingRowHeaderSelection = false
                isDraggingFormulaPointRange = false
                isDraggingFormulaPointCol = false
                isDraggingFormulaPointRow = false
                pendingTouchTapSelect = false
                pendingDoubleTapOpenEditor = false
                val span = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
                initialTwoFingerSpanPx = span
                lastTwoFingerSpanPx = span
                pinchAccumulatedZoom = zoomPercent.toFloat()
                isPinchZooming = false
                isPanningTouch = true
                lastTouchX = (event.getX(0) + event.getX(1)) * 0.5f
                lastTouchY = (event.getY(0) + event.getY(1)) * 0.5f
            }
            return true
        }

        MotionEvent.ACTION_POINTER_UP -> {
            if (isPinchZooming) {
                viewModel.onSetZoomPercent(zoomPercent, persistPreference = true)
            }
            isPinchZooming = false
            lastTwoFingerSpanPx = 0f
            val upIdx = event.actionIndex
            val remIdx = if (upIdx == 0 && event.pointerCount > 1) 1 else 0
            lastTouchX = event.getX(remIdx)
            lastTouchY = event.getY(remIdx)
            return true
        }

        MotionEvent.ACTION_MOVE -> {
            // [EN] Touch-screen (and multi-pointer trackpad) two-finger pinch-to-zoom + simultaneous two-finger pan
            //      Runs before 1-finger object drag so 2 fingers placed on top of a large chart/image scroll/zoom cleanly while keeping it selected!
            // [JA] タッチ画面での2本指ピンチズーム＆2本指パン操作（大きなグラフや画像の上に2本指を置いても、オブジェクトを選択したままスクロール／ズーム可能）
            if (event.pointerCount >= 2) {
                cancelPendingObjectDragKeepSelection()
                isDraggingFillHandle = false
                isDraggingSelectionMove = false
                isDraggingChartAxisRangeMove = false
                isDraggingColMove = false
                isDraggingFreezeRowHandle = false
                isDraggingFreezeColHandle = false
                isDraggingRangeSelection = false
                isDraggingColHeaderSelection = false
                isDraggingRowHeaderSelection = false
                isDraggingFormulaPointRange = false
                isDraggingFormulaPointCol = false
                isDraggingFormulaPointRow = false
                val span = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
                val focusX = (event.getX(0) + event.getX(1)) * 0.5f
                val focusY = (event.getY(0) + event.getY(1)) * 0.5f
                if (lastTwoFingerSpanPx <= 0f) {
                    initialTwoFingerSpanPx = span
                    lastTwoFingerSpanPx = span
                    pinchAccumulatedZoom = zoomPercent.toFloat()
                }
                if (!isPinchZooming && abs(span - initialTwoFingerSpanPx) > 4f * density) {
                    isPinchZooming = true
                }
                if (isPinchZooming && lastTwoFingerSpanPx > 10f * density && span > 10f * density) {
                    val factor = (span / lastTwoFingerSpanPx).coerceIn(0.80f, 1.25f)
                    lastTwoFingerSpanPx = span
                    applyPinchScaleFactor(factor, focusX, focusY)
                } else {
                    isPanningTouch = true
                    val stepX = (lastTouchX - focusX).roundToInt()
                    val stepY = (lastTouchY - focusY).roundToInt()
                    scrollXPx = (scrollXPx + stepX).coerceIn(0, maxScrollXPx())
                    scrollYPx = (scrollYPx + stepY).coerceIn(0, maxScrollYPx())
                    if (container.isEditingInCell) container.updateInCellEditorBounds()
                    invalidate()
                }
                lastTouchX = focusX
                lastTouchY = focusY
                return true
            }

            // Live Sheet Object Drag, 8-Direction Resize (4 Corners + Top/Bottom/Left/Right up to 5000x5000pt), or Top-Handle Rotation
            val activeObjId = draggingObjectId
            if (activeObjId != null && event.pointerCount == 1) {
                if (!isRotatingObject && hasMovedSinceDown) {
                    val edgeZonePx = 28f * density
                    val maxAutoStepPx = (14f * density).roundToInt().coerceAtLeast(2)
                    if (width > rowHeaderWidthPx + edgeZonePx * 2f) {
                        val prevScrollX = scrollXPx
                        if (event.x > width - edgeZonePx) {
                            scrollXPx = (scrollXPx + maxAutoStepPx).coerceIn(0, maxScrollXPx())
                        } else if (event.x in rowHeaderWidthPx..(rowHeaderWidthPx + edgeZonePx) && scrollXPx > 0) {
                            scrollXPx = (scrollXPx - maxAutoStepPx).coerceIn(0, maxScrollXPx())
                        }
                        objectDragStartScreenX -= (scrollXPx - prevScrollX).toFloat()
                    }
                    if (height > colHeaderHeightPx + edgeZonePx * 2f) {
                        val prevScrollY = scrollYPx
                        if (event.y > height - edgeZonePx) {
                            scrollYPx = (scrollYPx + maxAutoStepPx).coerceIn(0, maxScrollYPx())
                        } else if (event.y in colHeaderHeightPx..(colHeaderHeightPx + edgeZonePx) && scrollYPx > 0) {
                            scrollYPx = (scrollYPx - maxAutoStepPx).coerceIn(0, maxScrollYPx())
                        }
                        objectDragStartScreenY -= (scrollYPx - prevScrollY).toFloat()
                    }
                }
                val dxPt = pxToPt(event.x - objectDragStartScreenX)
                val dyPt = pxToPt(event.y - objectDragStartScreenY)
                val isMultiMove = !isRotatingObject &&
                    activeObjectResizePart == null &&
                    selectedObjectIds.size > 1 &&
                    activeObjId in selectedObjectIds &&
                    multiDragStartPositions.size > 1
                sheetObjects = sheetObjects.map { obj ->
                    if (isMultiMove && obj.id in selectedObjectIds) {
                        val startPos = multiDragStartPositions[obj.id]
                        if (startPos != null) {
                            obj.copy(
                                xPt = (startPos.first + dxPt).coerceAtLeast(0f),
                                yPt = (startPos.second + dyPt).coerceAtLeast(0f)
                            )
                        } else {
                            obj
                        }
                    } else if (obj.id == activeObjId) {
                        when {
                            isRotatingObject -> {
                                val rect = getSheetObjectScreenRect(obj)
                                val angleRad = atan2(
                                    (event.y - rect.centerY()).toDouble(),
                                    (event.x - rect.centerX()).toDouble()
                                )
                                var deg = ((angleRad * 180.0 / PI).toFloat() + 90f) % 360f
                                if (deg < 0f) deg += 360f
                                val snapAngles = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f, 360f)
                                for (sa in snapAngles) {
                                    if (abs(deg - sa) <= 3.5f) {
                                        deg = if (sa >= 360f) 0f else sa
                                        break
                                    }
                                }
                                obj.copy(rotationDeg = deg)
                            }
                            activeObjectResizePart != null -> {
                                val (localDxPt, localDyPt) = if (abs(obj.rotationDeg) > 0.1f) {
                                    val rad = (-obj.rotationDeg * PI / 180.0).toFloat()
                                    val cosA = cos(rad)
                                    val sinA = sin(rad)
                                    (dxPt * cosA - dyPt * sinA) to (dxPt * sinA + dyPt * cosA)
                                } else {
                                    dxPt to dyPt
                                }
                                val resized = computeSheetObjectResizeBounds(
                                    part = activeObjectResizePart!!,
                                    startX = objectStartXPts,
                                    startY = objectStartYPts,
                                    startW = objectStartWPts,
                                    startH = objectStartHPts,
                                    dxPt = localDxPt,
                                    dyPt = localDyPt
                                )
                                obj.copy(
                                    xPt = resized[0],
                                    yPt = resized[1],
                                    widthPt = resized[2],
                                    heightPt = resized[3]
                                )
                            }
                            else -> {
                                obj.copy(
                                    xPt = (objectStartXPts + dxPt).coerceAtLeast(0f),
                                    yPt = (objectStartYPts + dyPt).coerceAtLeast(0f)
                                )
                            }
                        }
                    } else {
                        obj
                    }
                }
                invalidate()
                return true
            }

            // Live Column Width Drag Resize
            if (resizingColIndex >= 0) {
                val dragDistPx = abs(event.x - resizeStartScreenPos)
                if (dragDistPx >= max(6f * density, scaledTouchSlop * 0.6f)) {
                    val deltaPt = pxToPt(event.x - resizeStartScreenPos)
                    val newWidthPt = (resizeInitialSizePt + deltaPt).coerceIn(24f, 600f)
                    currentViewport = currentViewport.withUpdatedColWidth(resizingColIndex, newWidthPt)
                    invalidate()
                }
                return true
            }

            // Live Row Height Drag Resize
            if (resizingRowIndex >= 0) {
                val dragDistPx = abs(event.y - resizeStartScreenPos)
                if (dragDistPx >= max(6f * density, scaledTouchSlop * 0.6f)) {
                    val deltaPt = pxToPt(event.y - resizeStartScreenPos)
                    val newHeightPt = (resizeInitialSizePt + deltaPt).coerceIn(16f, 400f)
                    currentViewport = currentViewport.withUpdatedRowHeight(resizingRowIndex, newHeightPt)
                    invalidate()
                }
                return true
            }

            // A11: Live Freeze Pane Divider Drag
            if (isDraggingFreezeRowHandle && event.pointerCount == 1) {
                val sheetYPt = pxToPt((event.y - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
                freezePreviewRow = currentViewport.rowAtY(sheetYPt).coerceIn(0, (currentViewport.rowHeights.size - 1).coerceAtLeast(0))
                invalidate()
                return true
            }
            if (isDraggingFreezeColHandle && event.pointerCount == 1) {
                val sheetXPt = pxToPt((event.x - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
                freezePreviewCol = currentViewport.colAtX(sheetXPt).coerceIn(0, (currentViewport.colWidths.size - 1).coerceAtLeast(0))
                invalidate()
                return true
            }

            // A8: Live Column Header Drag Reorder
            if (isDraggingColMove && event.pointerCount == 1) {
                val sheetXPt = pxToPt((event.x - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
                colMoveTargetBeforeCol = currentViewport.colAtX(sheetXPt).coerceIn(0, currentViewport.colWidths.size)
                invalidate()
                return true
            }

            // [EN] Live Chart X-Axis / Y-Axis Cell Range Frame Drag-Move or Corner/Edge Resize on Worksheet Grid
            // [JA] ワークシート上のグラフX軸・Y軸データ範囲ハイライト枠のライブ移動・四隅リサイズ
            if (isDraggingChartAxisRangeMove && event.pointerCount == 1) {
                val sheetXPt = pxToPt((event.x - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
                val sheetYPt = pxToPt((event.y - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
                val rawRow = currentViewport.rowAtY(sheetYPt)
                val rawCol = currentViewport.colAtX(sheetXPt)
                val srcR0 = chartAxisMoveSrcStartRow
                val srcC0 = chartAxisMoveSrcStartCol
                val srcR1 = chartAxisMoveSrcEndRow
                val srcC1 = chartAxisMoveSrcEndCol
                when (chartAxisDragMode) {
                    ChartRangeDragMode.MOVE -> {
                        val spanR = (srcR1 - srcR0).coerceAtLeast(0)
                        val spanC = (srcC1 - srcC0).coerceAtLeast(0)
                        val r0 = (rawRow - chartAxisMoveAnchorOffsetRow).coerceAtLeast(0)
                        val c0 = (rawCol - chartAxisMoveAnchorOffsetCol).coerceAtLeast(0)
                        chartAxisMoveTargetStartRow = r0
                        chartAxisMoveTargetStartCol = c0
                        chartAxisPreviewStartRow = r0
                        chartAxisPreviewStartCol = c0
                        chartAxisPreviewEndRow = r0 + spanR
                        chartAxisPreviewEndCol = c0 + spanC
                    }
                    ChartRangeDragMode.RESIZE_BOTTOM_RIGHT -> {
                        chartAxisPreviewStartRow = min(srcR0, rawRow).coerceAtLeast(0)
                        chartAxisPreviewEndRow = max(srcR0, rawRow).coerceAtLeast(chartAxisPreviewStartRow)
                        chartAxisPreviewStartCol = min(srcC0, rawCol).coerceAtLeast(0)
                        chartAxisPreviewEndCol = max(srcC0, rawCol).coerceAtLeast(chartAxisPreviewStartCol)
                    }
                    ChartRangeDragMode.RESIZE_BOTTOM_LEFT -> {
                        chartAxisPreviewStartRow = min(srcR0, rawRow).coerceAtLeast(0)
                        chartAxisPreviewEndRow = max(srcR0, rawRow).coerceAtLeast(chartAxisPreviewStartRow)
                        chartAxisPreviewStartCol = min(srcC1, rawCol).coerceAtLeast(0)
                        chartAxisPreviewEndCol = max(srcC1, rawCol).coerceAtLeast(chartAxisPreviewStartCol)
                    }
                    ChartRangeDragMode.RESIZE_TOP_RIGHT -> {
                        chartAxisPreviewStartRow = min(srcR1, rawRow).coerceAtLeast(0)
                        chartAxisPreviewEndRow = max(srcR1, rawRow).coerceAtLeast(chartAxisPreviewStartRow)
                        chartAxisPreviewStartCol = min(srcC0, rawCol).coerceAtLeast(0)
                        chartAxisPreviewEndCol = max(srcC0, rawCol).coerceAtLeast(chartAxisPreviewStartCol)
                    }
                    ChartRangeDragMode.RESIZE_TOP_LEFT -> {
                        chartAxisPreviewStartRow = min(srcR1, rawRow).coerceAtLeast(0)
                        chartAxisPreviewEndRow = max(srcR1, rawRow).coerceAtLeast(chartAxisPreviewStartRow)
                        chartAxisPreviewStartCol = min(srcC1, rawCol).coerceAtLeast(0)
                        chartAxisPreviewEndCol = max(srcC1, rawCol).coerceAtLeast(chartAxisPreviewStartCol)
                    }
                    ChartRangeDragMode.RESIZE_BOTTOM -> {
                        chartAxisPreviewStartRow = min(srcR0, rawRow).coerceAtLeast(0)
                        chartAxisPreviewEndRow = max(srcR0, rawRow).coerceAtLeast(chartAxisPreviewStartRow)
                        chartAxisPreviewStartCol = srcC0
                        chartAxisPreviewEndCol = srcC1
                    }
                    ChartRangeDragMode.RESIZE_TOP -> {
                        chartAxisPreviewStartRow = min(srcR1, rawRow).coerceAtLeast(0)
                        chartAxisPreviewEndRow = max(srcR1, rawRow).coerceAtLeast(chartAxisPreviewStartRow)
                        chartAxisPreviewStartCol = srcC0
                        chartAxisPreviewEndCol = srcC1
                    }
                }
                invalidate()
                return true
            }

            // A7: Live Selection Border Move Drag
            if (isDraggingSelectionMove && event.pointerCount == 1) {
                val sheetXPt = pxToPt((event.x - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
                val sheetYPt = pxToPt((event.y - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
                val rawRow = currentViewport.rowAtY(sheetYPt)
                val rawCol = currentViewport.colAtX(sheetXPt)
                moveTargetStartRow = (rawRow - moveAnchorOffsetRow).coerceAtLeast(0)
                moveTargetStartCol = (rawCol - moveAnchorOffsetCol).coerceAtLeast(0)
                invalidate()
                return true
            }

            if (isDraggingFillHandle && event.pointerCount == 1) {
                val sheetXPt = pxToPt((event.x - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
                val sheetYPt = pxToPt((event.y - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
                fillTargetEndRow = currentViewport.rowAtY(sheetYPt)
                fillTargetEndCol = currentViewport.colAtX(sheetXPt)
                invalidate()
                return true
            }

            val dx = event.x - downX
            val dy = event.y - downY
            if (isDraggingFormulaPointCol && event.pointerCount == 1) {
                val sheetXPt = pxToPt((event.x - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
                val curCol = currentViewport.colAtX(sheetXPt)
                val refStr = container.buildSheetQualifiedColRangeRef(dragAnchorCol, curCol)
                container.insertOrUpdatePointedReference(newRef = refStr, appendUnion = false)
                return true
            }
            if (isDraggingFormulaPointRow && event.pointerCount == 1) {
                val sheetYPt = pxToPt((event.y - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
                val curRow = currentViewport.rowAtY(sheetYPt)
                val refStr = container.buildSheetQualifiedRowRangeRef(dragAnchorRow, curRow)
                container.insertOrUpdatePointedReference(newRef = refStr, appendUnion = false)
                return true
            }
            if (isDraggingFormulaPointRange && event.pointerCount == 1) {
                val sheetXPt = pxToPt((event.x - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
                val sheetYPt = pxToPt((event.y - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
                val curRow = currentViewport.rowAtY(sheetYPt)
                val curCol = currentViewport.colAtX(sheetXPt)
                val refStr = container.buildSheetQualifiedCellOrRangeRef(dragAnchorRow, dragAnchorCol, curRow, curCol)
                container.insertOrUpdatePointedReference(
                    newRef = refStr,
                    appendUnion = false,
                    anchorRow = dragAnchorRow,
                    anchorCol = dragAnchorCol,
                    cursorRow = curRow,
                    cursorCol = curCol
                )
                return true
            }
            if (isDraggingColHeaderSelection && event.pointerCount == 1) {
                val sheetXPt = pxToPt((event.x - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
                val curCol = currentViewport.colAtX(sheetXPt)
                viewModel.onSelectEntireColumn(dragAnchorCol, curCol)
                currentViewport = viewModel.uiState.value.viewport
                container.notifySelectedCellChanged()
                invalidate()
                return true
            }
            if (isDraggingRowHeaderSelection && event.pointerCount == 1) {
                val sheetYPt = pxToPt((event.y - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
                val curRow = currentViewport.rowAtY(sheetYPt)
                viewModel.onSelectEntireRow(dragAnchorRow, curRow)
                currentViewport = viewModel.uiState.value.viewport
                container.notifySelectedCellChanged()
                invalidate()
                return true
            }
            if (isDraggingRangeSelection && event.pointerCount == 1) {
                // [EN] Smooth viewport edge auto-scroll when dragging a cell selection near the screen edges on phones/tablets
                // [JA] スマホ・タブレットで画面端近くまで範囲選択ドラッグした際、自動的にシートをスクロール
                val edgeZonePx = 28f * density
                val maxAutoStepPx = (14f * density).roundToInt().coerceAtLeast(2)
                if (width > rowHeaderWidthPx + edgeZonePx * 2f) {
                    if (event.x > width - edgeZonePx) {
                        scrollXPx = (scrollXPx + maxAutoStepPx).coerceIn(0, maxScrollXPx())
                    } else if (event.x in rowHeaderWidthPx..(rowHeaderWidthPx + edgeZonePx) && scrollXPx > 0) {
                        scrollXPx = (scrollXPx - maxAutoStepPx).coerceIn(0, maxScrollXPx())
                    }
                }
                if (height > colHeaderHeightPx + edgeZonePx * 2f) {
                    if (event.y > height - edgeZonePx) {
                        scrollYPx = (scrollYPx + maxAutoStepPx).coerceIn(0, maxScrollYPx())
                    } else if (event.y in colHeaderHeightPx..(colHeaderHeightPx + edgeZonePx) && scrollYPx > 0) {
                        scrollYPx = (scrollYPx - maxAutoStepPx).coerceIn(0, maxScrollYPx())
                    }
                }

                val curRow = screenYToSheetRow(event.y)
                val curCol = screenXToSheetCol(event.x)
                if (curRow != dragAnchorRow || curCol != dragAnchorCol) {
                    hasMovedSinceDown = true
                    pendingTouchTapSelect = false
                    pendingDoubleTapOpenEditor = false
                }
                if (!pendingTouchTapSelect || hasMovedSinceDown) {
                    val prevSnap = currentViewport
                    if (prevSnap.selStartRow != dragAnchorRow ||
                        prevSnap.selStartCol != dragAnchorCol ||
                        prevSnap.selEndRow != curRow ||
                        prevSnap.selEndCol != curCol
                    ) {
                        viewModel.onSelectCell(dragAnchorRow, dragAnchorCol, curRow, curCol)
                        currentViewport = viewModel.uiState.value.viewport
                        container.notifySelectedCellChanged()
                    }
                }
                invalidate()
                return true
            }
            if (!isMouse) {
                if (!isPanningTouch && hypot(dx, dy) > scaledTouchSlop) {
                    isPanningTouch = true
                    pendingTouchTapSelect = false
                }
                if (isPanningTouch) {
                    val stepX = (lastTouchX - event.x).roundToInt()
                    val stepY = (lastTouchY - event.y).roundToInt()
                    lastTouchX = event.x
                    lastTouchY = event.y
                    scrollXPx = (scrollXPx + stepX).coerceIn(0, maxScrollXPx())
                    scrollYPx = (scrollYPx + stepY).coerceIn(0, maxScrollYPx())
                    if (container.isEditingInCell) container.updateInCellEditorBounds()
                    invalidate()
                }
            }
            return true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (isDraggingFormulaPointRange || isDraggingFormulaPointCol || isDraggingFormulaPointRow) {
                isDraggingFormulaPointRange = false
                isDraggingFormulaPointCol = false
                isDraggingFormulaPointRow = false
                invalidate()
                return true
            }
            // A11: Commit Freeze Pane Divider Drag
            if (isDraggingFreezeRowHandle || isDraggingFreezeColHandle) {
                val newR = if (isDraggingFreezeRowHandle) freezePreviewRow else freezeRows
                val newC = if (isDraggingFreezeColHandle) freezePreviewCol else freezeCols
                isDraggingFreezeRowHandle = false
                isDraggingFreezeColHandle = false
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    viewModel.onDragFreezePaneBoundary(newR, newC)
                    container.bindState(viewModel.uiState.value)
                }
                invalidate()
                return true
            }
            // A8: Commit Column Header Drag Reorder
            if (isDraggingColMove) {
                isDraggingColMove = false
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (hasMovedSinceDown && colMoveTargetBeforeCol !in colMoveStartCol..(colMoveEndCol + 1)) {
                        viewModel.onMoveColumns(colMoveStartCol, colMoveEndCol, colMoveTargetBeforeCol)
                        container.bindState(viewModel.uiState.value)
                        container.notifySelectedCellChanged()
                    } else if (!hasMovedSinceDown) {
                        val sheetXPt = pxToPt((event.x - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
                        val col = currentViewport.colAtX(sheetXPt)
                        viewModel.onSelectEntireColumn(col, col)
                        container.bindState(viewModel.uiState.value)
                        container.notifySelectedCellChanged()
                    }
                }
                invalidate()
                return true
            }
            // [EN] Commit Chart X-Axis / Y-Axis Cell Range Frame Drag-Move or Corner/Edge Resize on Worksheet Grid
            // [JA] ワークシート上のグラフX軸・Y軸データ範囲ハイライト枠のドラッグ移動または四隅リサイズを確定
            if (isDraggingChartAxisRangeMove) {
                isDraggingChartAxisRangeMove = false
                val hasRangeChanged =
                    chartAxisPreviewStartRow != chartAxisMoveSrcStartRow ||
                        chartAxisPreviewStartCol != chartAxisMoveSrcStartCol ||
                        chartAxisPreviewEndRow != chartAxisMoveSrcEndRow ||
                        chartAxisPreviewEndCol != chartAxisMoveSrcEndCol
                if (event.actionMasked == MotionEvent.ACTION_UP && hasRangeChanged) {
                    if (chartAxisDragMode == ChartRangeDragMode.MOVE) {
                        viewModel.onMoveChartSeriesAxisRangeByDrag(
                            objectId = chartAxisMoveChartId,
                            seriesIndex = chartAxisMoveSeriesIdx,
                            isXAxis = chartAxisMoveIsX,
                            newStartRow = chartAxisPreviewStartRow,
                            newStartCol = chartAxisPreviewStartCol,
                            newEndRow = chartAxisPreviewEndRow,
                            newEndCol = chartAxisPreviewEndCol
                        )
                    } else {
                        viewModel.onResizeChartAxisRangeByDrag(
                            objectId = chartAxisMoveChartId,
                            seriesIndex = chartAxisMoveSeriesIdx,
                            isXAxis = chartAxisMoveIsX,
                            newStartRow = chartAxisPreviewStartRow,
                            newStartCol = chartAxisPreviewStartCol,
                            newEndRow = chartAxisPreviewEndRow,
                            newEndCol = chartAxisPreviewEndCol
                        )
                    }
                    container.bindState(viewModel.uiState.value)
                }
                invalidate()
                return true
            }
            // A7: Commit Selection Border Move Drag
            if (isDraggingSelectionMove) {
                isDraggingSelectionMove = false
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    (moveTargetStartRow != moveSrcStartRow || moveTargetStartCol != moveSrcStartCol)
                ) {
                    viewModel.onMoveSelectionRange(
                        srcStartRow = moveSrcStartRow,
                        srcStartCol = moveSrcStartCol,
                        srcEndRow = moveSrcEndRow,
                        srcEndCol = moveSrcEndCol,
                        destStartRow = moveTargetStartRow,
                        destStartCol = moveTargetStartCol
                    )
                    container.bindState(viewModel.uiState.value)
                    container.notifySelectedCellChanged()
                }
                invalidate()
                return true
            }
            if (isDraggingFillHandle) {
                isDraggingFillHandle = false
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    (fillTargetEndRow != fillSrcEndRow || fillTargetEndCol != fillSrcEndCol)
                ) {
                    viewModel.onAutoFillRange(
                        srcStartRow = fillSrcStartRow,
                        srcStartCol = fillSrcStartCol,
                        srcEndRow = fillSrcEndRow,
                        srcEndCol = fillSrcEndCol,
                        targetEndRow = fillTargetEndRow,
                        targetEndCol = fillTargetEndCol
                    )
                    container.bindState(viewModel.uiState.value)
                    container.notifySelectedCellChanged()
                }
                invalidate()
                return true
            }
            val activeObjId = draggingObjectId
            if (activeObjId != null) {
                val updated = sheetObjects.firstOrNull { it.id == activeObjId }
                val wasRotating = isRotatingObject
                val wasResizePart = activeObjectResizePart
                val wasMultiMove = !wasRotating &&
                    wasResizePart == null &&
                    selectedObjectIds.size > 1 &&
                    activeObjId in selectedObjectIds &&
                    multiDragStartPositions.size > 1
                draggingObjectId = null
                activeObjectResizePart = null
                isRotatingObject = false
                multiDragStartPositions = emptyMap()
                if (updated != null) {
                    if (wasRotating) {
                        viewModel.onRotateSheetObject(activeObjId, updated.rotationDeg)
                    } else if (wasMultiMove) {
                        val updatedMap = sheetObjects
                            .filter { it.id in selectedObjectIds }
                            .associate { it.id to (it.xPt to it.yPt) }
                        viewModel.onMoveMultipleSheetObjects(updatedMap)
                    } else {
                        viewModel.onMoveOrResizeSheetObject(
                            activeObjId,
                            updated.xPt,
                            updated.yPt,
                            updated.widthPt,
                            updated.heightPt
                        )
                    }
                    container.bindState(viewModel.uiState.value)
                }
                invalidate()
                return true
            }
            if (resizingColIndex >= 0) {
                val col = resizingColIndex
                val dragDistPx = abs(event.x - resizeStartScreenPos)
                val finalWidthPt = currentViewport.colWidths.getOrElse(col) { resizeInitialSizePt }
                val clickSlopPx = max(6f * density, scaledTouchSlop * 0.6f)
                resizingColIndex = -1
                if (event.actionMasked == MotionEvent.ACTION_UP && dragDistPx < clickSlopPx) {
                    val hasHiddenAdjacentCol = (
                        currentViewport.colWidths.getOrElse(col) { 72f } < 1f ||
                            currentViewport.colWidths.getOrElse(col + 1) { 72f } < 1f
                        )
                    if (hasHiddenAdjacentCol) {
                        viewModel.onUnhideColumnsAround(col)
                    } else {
                        viewModel.onAutoFitColumn(col)
                    }
                } else {
                    viewModel.onResizeColumn(col, finalWidthPt)
                }
                container.bindState(viewModel.uiState.value)
                invalidate()
                return true
            }
            if (resizingRowIndex >= 0) {
                val row = resizingRowIndex
                val dragDistPx = abs(event.y - resizeStartScreenPos)
                val finalHeightPt = currentViewport.rowHeights.getOrElse(row) { resizeInitialSizePt }
                val clickSlopPx = max(6f * density, scaledTouchSlop * 0.6f)
                resizingRowIndex = -1
                if (event.actionMasked == MotionEvent.ACTION_UP && dragDistPx < clickSlopPx) {
                    val hasHiddenAdjacentRow = (
                        currentViewport.rowHeights.getOrElse(row) { 22f } < 1f ||
                            currentViewport.rowHeights.getOrElse(row + 1) { 22f } < 1f
                        )
                    if (hasHiddenAdjacentRow) {
                        viewModel.onUnhideRowsAround(row)
                    } else {
                        viewModel.onAutoFitRow(row)
                    }
                } else {
                    viewModel.onResizeRow(row, finalHeightPt)
                }
                container.bindState(viewModel.uiState.value)
                invalidate()
                return true
            }
            if (isPanningTouch) {
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = -(velocityTracker?.xVelocity ?: 0f).roundToInt()
                val vy = -(velocityTracker?.yVelocity ?: 0f).roundToInt()
                scroller.fling(scrollXPx, scrollYPx, vx, vy, 0, maxScrollXPx(), 0, maxScrollYPx())
                postInvalidateOnAnimation()
            } else if (pendingDoubleTapOpenEditor &&
                event.actionMasked == MotionEvent.ACTION_UP &&
                !hasMovedSinceDown &&
                !container.isContextMenuVisible &&
                lastClickRow >= 0 &&
                lastClickCol >= 0
            ) {
                viewModel.onSelectCell(lastClickRow, lastClickCol)
                container.bindState(viewModel.uiState.value)
                container.openInCellEditor(lastClickRow, lastClickCol, event.x, event.y)
                invalidate()
            } else if (pendingTouchTapSelect &&
                event.actionMasked == MotionEvent.ACTION_UP &&
                !hasMovedSinceDown &&
                !container.isContextMenuVisible
            ) {
                viewModel.onSelectCell(pendingTouchTapRow, pendingTouchTapCol)
                container.bindState(viewModel.uiState.value)
                container.notifySelectedCellChanged()
                invalidate()
            }
            pendingTouchTapSelect = false
            pendingDoubleTapOpenEditor = false
            isPanningTouch = false
            isDraggingFillHandle = false
            isDraggingSelectionMove = false
            isDraggingChartAxisRangeMove = false
            isDraggingColMove = false
            isDraggingFreezeRowHandle = false
            isDraggingFreezeColHandle = false
            isDraggingRangeSelection = false
            isDraggingColHeaderSelection = false
            isDraggingRowHeaderSelection = false
            isDraggingFormulaPointRange = false
            isDraggingFormulaPointCol = false
            isDraggingFormulaPointRow = false
            velocityTracker?.recycle()
            velocityTracker = null
            return true
        }
    }
    return null
}

/**
 * [EN] Cancels an in-progress 1-finger object drag/resize/rotation when a second finger touches down
 *      (for 2-finger scroll or pinch-zoom), restoring the object's original geometry while keeping it selected.
 * [JA] グラフや画像上で2本指スクロール／ピンチズームを開始した際、1本目による仮ドラッグ移動を元に戻しつつ選択状態を維持します。
 */
private fun MasuSheetNativeGridView.cancelPendingObjectDragKeepSelection() {
    if (draggingObjectId == null) return
    sheetObjects = viewModel.uiState.value.sheetObjects
    draggingObjectId = null
    activeObjectResizePart = null
    isRotatingObject = false
    multiDragStartPositions = emptyMap()
    invalidate()
}

/**
 * [EN] Handles keyboard navigation and shortcuts when the grid canvas has focus.
 * When one or more sheet objects (e.g. pasted images, shapes, charts) are selected,
 * the arrow keys (`DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`) finely nudge the
 * selected object(s) by `1pt` (or `5pt` when Shift is held) instead of moving the cell cursor.
 *
 * [JA] グリッドキャンバスにフォーカスがある際のキーボード操作を処理します。
 * 貼り付けた画像や図形・グラフなどのシートオブジェクトが選択されている時は、
 * 矢印キー（上下左右）で選択中のオブジェクトを `1pt` 単位（`Shift + 矢印キー` で `5pt` 単位）で
 * 微調整移動できます。
 */
fun MasuSheetNativeGridView.handleSelectionKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val snap = currentViewport
    val isShift = event.isShiftPressed
    val hasSelectedObjects = selectedObjectIds.isNotEmpty() || selectedObjectId != null
    val nudgeStepPt = if (isShift) 5f else 1f
    when (keyCode) {
        KeyEvent.KEYCODE_ESCAPE -> {
            if (container.isContextMenuVisible) {
                container.dismissContextMenu()
                return true
            }
            if (hasSelectedObjects) {
                viewModel.onSelectSheetObject(null)
                container.bindState(viewModel.uiState.value)
                return true
            }
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            if (hasSelectedObjects) {
                viewModel.onNudgeSelectedSheetObjects(0f, -nudgeStepPt)
                container.bindState(viewModel.uiState.value)
                invalidate()
                return true
            }
            if (isShift) {
                val newEndR = (snap.selEndRow - 1).coerceAtLeast(0)
                viewModel.onSelectCell(snap.selStartRow, snap.selStartCol, newEndR, snap.selEndCol)
            } else {
                viewModel.onSelectCell((snap.activeRow - 1).coerceAtLeast(0), snap.activeCol)
            }
            container.bindState(viewModel.uiState.value)
            container.notifySelectedCellChanged()
            return true
        }
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            if (hasSelectedObjects && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                viewModel.onNudgeSelectedSheetObjects(0f, nudgeStepPt)
                container.bindState(viewModel.uiState.value)
                invalidate()
                return true
            }
            if (isShift && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val newEndR = (snap.selEndRow + 1).coerceAtMost(snap.rowHeights.size - 1)
                viewModel.onSelectCell(snap.selStartRow, snap.selStartCol, newEndR, snap.selEndCol)
            } else {
                val merge = snap.mergeAt(snap.activeRow, snap.activeCol)
                val nextR = (merge?.endRow ?: snap.activeRow) + 1
                viewModel.onSelectCell(nextR, snap.activeCol)
            }
            container.bindState(viewModel.uiState.value)
            container.notifySelectedCellChanged()
            return true
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            if (hasSelectedObjects) {
                viewModel.onNudgeSelectedSheetObjects(-nudgeStepPt, 0f)
                container.bindState(viewModel.uiState.value)
                invalidate()
                return true
            }
            if (isShift) {
                val newEndC = (snap.selEndCol - 1).coerceAtLeast(0)
                viewModel.onSelectCell(snap.selStartRow, snap.selStartCol, snap.selEndRow, newEndC)
            } else {
                viewModel.onSelectCell(snap.activeRow, (snap.activeCol - 1).coerceAtLeast(0))
            }
            container.bindState(viewModel.uiState.value)
            container.notifySelectedCellChanged()
            return true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_TAB -> {
            if (hasSelectedObjects && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                viewModel.onNudgeSelectedSheetObjects(nudgeStepPt, 0f)
                container.bindState(viewModel.uiState.value)
                invalidate()
                return true
            }
            if (isShift && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                val newEndC = (snap.selEndCol + 1).coerceAtMost(snap.colWidths.size - 1)
                viewModel.onSelectCell(snap.selStartRow, snap.selStartCol, snap.selEndRow, newEndC)
            } else {
                val merge = snap.mergeAt(snap.activeRow, snap.activeCol)
                val nextC = if (keyCode == KeyEvent.KEYCODE_TAB && isShift) {
                    (snap.activeCol - 1).coerceAtLeast(0)
                } else {
                    (merge?.endCol ?: snap.activeCol) + 1
                }
                viewModel.onSelectCell(snap.activeRow, nextC)
            }
            container.bindState(viewModel.uiState.value)
            container.notifySelectedCellChanged()
            return true
        }
        KeyEvent.KEYCODE_F2 -> {
            val objId = selectedObjectId
            val obj = sheetObjects.firstOrNull { it.id == objId }
            if (obj != null && obj.kind != SheetObjectKind.IMAGE) {
                container.openInObjectEditor(obj)
            } else {
                container.openInCellEditor(snap.activeRow, snap.activeCol, null, null)
            }
            return true
        }
        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
            if (hasSelectedObjects) {
                viewModel.onDeleteSelectedSheetObjects()
            } else {
                // Clears selected cells AND automatically converts any structured Table ("Column1") back to range first
                viewModel.onClearSelectedCells()
            }
            container.bindState(viewModel.uiState.value)
            return true
        }
        KeyEvent.KEYCODE_SPACE -> {
            if (viewModel.onToggleCellCheckbox(snap.activeRow, snap.activeCol, 0)) {
                container.bindState(viewModel.uiState.value)
                return true
            }
        }
    }

    // Direct typing when a printable key is pressed while gridView has focus
    val unicode = event.unicodeChar
    if (unicode > 0 &&
        !Character.isISOControl(unicode) &&
        !event.isCtrlPressed &&
        !event.isAltPressed &&
        !event.isMetaPressed
    ) {
        container.startDirectTypingWithText(unicode.toChar().toString())
        return true
    }
    return false
}

/**
 * [EN] Converts the UI zoom percentage (`25..400`) into the canvas multiplier (`0.25f..4.0f`).
 * Previously capped at `2.0f` (`200%`), which prevented `205%..400%` from visually zooming the canvas.
 *
 * ---
 * [JA] 表示倍率（`25%〜400%`）をキャンバスの拡大スケール（`0.25f〜4.0f`）へ変換します。
 * 205%以上の「＋」ボタン拡大操作でも確実に画面が拡大描画されるように400%（4.0倍）まで対応します。
 */
internal fun computeCanvasZoomScale(zoomPercent: Int): Float =
    (zoomPercent / 100f).coerceIn(0.25f, 4.0f)

/**
 * [EN] Computes the updated `[xPt, yPt, widthPt, heightPt]` bounds when resizing a floating sheet object
 * (pasted image, shape, text box, or chart).
 * - **4-Corner handles** (`RESIZE_BOTTOM_RIGHT`, `RESIZE_BOTTOM_LEFT`, `RESIZE_TOP_RIGHT`, `RESIZE_TOP_LEFT`):
 *   Strictly preserve the figure's original aspect ratio (`startW : startH`) using diagonal projection scaling.
 * - **4 Edge-midpoint handles** (`RESIZE_RIGHT`, `RESIZE_LEFT`, `RESIZE_BOTTOM`, `RESIZE_TOP`):
 *   Resize only the dragged dimension while keeping the perpendicular side length unchanged.
 *
 * ---
 * [JA] シート上の図（貼り付けた画像・図形・テキストボックス・グラフ）のリサイズ後の `[xPt, yPt, widthPt, heightPt]` を算出します。
 * - **四隅のハンドル** (`RESIZE_BOTTOM_RIGHT`, `RESIZE_BOTTOM_LEFT`, `RESIZE_TOP_RIGHT`, `RESIZE_TOP_LEFT`):
 *   元の図形の縦横比（`startW : startH`）を厳密に維持したまま比例拡大・縮小します。
 * - **上下左右の辺の中央ハンドル** (`RESIZE_RIGHT`, `RESIZE_LEFT`, `RESIZE_BOTTOM`, `RESIZE_TOP`):
 *   もう片方の長さを変えずに、触った辺の方向のみ拡大・縮小します。
 */
internal fun computeSheetObjectResizeBounds(
    part: MasuSheetNativeGridView.SheetObjectHitPart,
    startX: Float,
    startY: Float,
    startW: Float,
    startH: Float,
    dxPt: Float,
    dyPt: Float,
    minW: Float = 24f,
    minH: Float = 20f,
    maxW: Float = 5000f,
    maxH: Float = 5000f
): FloatArray {
    val safeW = startW.coerceAtLeast(1f)
    val safeH = startH.coerceAtLeast(1f)
    var newX = startX
    var newY = startY
    var newW = safeW
    var newH = safeH

    when (part) {
        MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_RIGHT -> {
            newW = (safeW + dxPt).coerceIn(minW, maxW)
        }
        MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_LEFT -> {
            val rightEdge = startX + safeW
            newX = (startX + dxPt).coerceIn(0f, (rightEdge - minW).coerceAtLeast(0f))
            newW = (rightEdge - newX).coerceIn(minW, maxW)
        }
        MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM -> {
            newH = (safeH + dyPt).coerceIn(minH, maxH)
        }
        MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP -> {
            val bottomEdge = startY + safeH
            newY = (startY + dyPt).coerceIn(0f, (bottomEdge - minH).coerceAtLeast(0f))
            newH = (bottomEdge - newY).coerceIn(minH, maxH)
        }
        MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM_RIGHT,
        MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM_LEFT,
        MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_RIGHT,
        MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_LEFT -> {
            val deltaW = when (part) {
                MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM_RIGHT,
                MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_RIGHT -> dxPt
                else -> -dxPt
            }
            val deltaH = when (part) {
                MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM_RIGHT,
                MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM_LEFT -> dyPt
                else -> -dyPt
            }
            val denom = (safeW * safeW + safeH * safeH).coerceAtLeast(1f)
            val rawScale = 1f + (deltaW * safeW + deltaH * safeH) / denom
            val minScale = max(minW / safeW, minH / safeH)
            var maxScale = min(maxW / safeW, maxH / safeH)
            if (part == MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM_LEFT ||
                part == MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_LEFT
            ) {
                val rightEdge = startX + safeW
                maxScale = min(maxScale, rightEdge / safeW)
            }
            if (part == MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_RIGHT ||
                part == MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_LEFT
            ) {
                val bottomEdge = startY + safeH
                maxScale = min(maxScale, bottomEdge / safeH)
            }
            val effectiveMaxScale = max(minScale, maxScale)
            val scale = rawScale.coerceIn(minScale, effectiveMaxScale)
            newW = safeW * scale
            newH = safeH * scale
            if (part == MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM_LEFT ||
                part == MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_LEFT
            ) {
                newX = (startX + safeW - newW).coerceAtLeast(0f)
            }
            if (part == MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_RIGHT ||
                part == MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_LEFT
            ) {
                newY = (startY + safeH - newH).coerceAtLeast(0f)
            }
        }
        else -> {}
    }

    return floatArrayOf(newX, newY, newW, newH)
}
