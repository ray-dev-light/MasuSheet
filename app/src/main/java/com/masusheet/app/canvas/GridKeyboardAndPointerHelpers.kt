package com.masusheet.app.canvas

import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.EditText
import com.masusheet.app.canvas.MasuSheetNativeGridView.ChartRangeDragMode
import com.masusheet.app.canvas.MasuSheetNativeGridView.SheetObjectHitPart
import com.masusheet.app.core.ScrollInputMath
import com.masusheet.app.model.SheetObjectKind
import com.masusheet.app.viewmodel.getActiveCellValidation
import com.masusheet.app.viewmodel.isTableHeaderCell
import com.masusheet.app.viewmodel.onAutoFillRange
import com.masusheet.app.viewmodel.onDeleteSelectedSheetObjects
import com.masusheet.app.viewmodel.onDoubleTapAutoFillCornerHandle
import com.masusheet.app.viewmodel.onDragFreezePaneBoundary
import com.masusheet.app.viewmodel.onMoveChartSeriesAxisRangeByDrag
import com.masusheet.app.viewmodel.onMoveColumns
import com.masusheet.app.viewmodel.onMoveMultipleSheetObjects
import com.masusheet.app.viewmodel.onMoveOrResizeSheetObject
import com.masusheet.app.viewmodel.onMoveSelectionRange
import com.masusheet.app.viewmodel.onNudgeSelectedSheetObjects
import com.masusheet.app.viewmodel.onOpenColumnFilterMenu
import com.masusheet.app.viewmodel.onResizeChartAxisRangeByDrag
import com.masusheet.app.viewmodel.onRotateSheetObject
import com.masusheet.app.viewmodel.onSelectChartSubElement
import com.masusheet.app.viewmodel.onSelectSheetObject
import com.masusheet.app.viewmodel.onSetZoomPercent
import com.masusheet.app.viewmodel.onUnhideColumnsAround
import com.masusheet.app.viewmodel.onUnhideRowsAround
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
 * [EN] Grid Right-Click, Mouse Wheel/Hover, Keyboard Navigation, & Object Resize Geometry
 * Handles right-click context dispatch, mouse/trackpad scroll & zoom, keyboard navigation (`1pt` object nudge), and aspect-preserving resize bounds math.
 *
 * ---
 * [JA / 日本語解説]
 * 右クリックメニュー起動、マウスホイール・ホバー処理、キーボード操作（矢印キーによる `1pt` 微調整含む）、およびアスペクト比維持リサイズ計算を担う補助モジュールです。
 * ============================================================================
 */

/**
 * ============================================================================
 * [EN] Touch, Stylus/Mouse, Trackpad Pinch, & Keyboard Navigation / Object Nudge Handler
 * Handles 1-finger touch scroll vs range-select mode, mouse/trackpad wheel & pinch zoom, header resize/AutoFit, object move/resize/rotate, and keyboard navigation (`1pt` arrow nudge).
 *
 * ---
 * [JA / 日本語解説]
 * 1本指タッチスクロール／範囲選択モード切替、マウス・トラックパッド操作、行列リサイズ／自動調整、オブジェクトの移動・リサイズ・回転、およびキーボード操作（矢印1pt微調整含む）を処理します。
 * ============================================================================
 */

/**
 * [EN] Handles secondary mouse click or long-press at `(screenX, screenY)` by selecting the hit header/object/cell and opening the floating context menu.
 * [JA] 右クリックまたは長押し座標 `(screenX, screenY)` にある行列ヘッダ・オブジェクト・セルを選択し、コンテキストメニューを表示します。
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
 * [EN] Processes mouse wheel scrolling (`Ctrl`+wheel zoom, `Shift`+wheel horizontal scroll), hover cursor icons, trackpad pinch, and right-button press.
 * [JA] マウスホイールスクロール（`Ctrl`+ホイール拡大縮小、`Shift`+横スクロール）、ホバーカーソル切替、トラックパッドピンチ、右クリックを処理します。
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
 * [EN] Cancels an in-progress 1-finger object drag/resize/rotation when a second finger touches down
 *      (for 2-finger scroll or pinch-zoom), restoring the object's original geometry while keeping it selected.
 * [JA] グラフや画像上で2本指スクロール／ピンチズームを開始した際、1本目による仮ドラッグ移動を元に戻しつつ選択状態を維持します。
 */
internal fun MasuSheetNativeGridView.cancelPendingObjectDragKeepSelection() {
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
