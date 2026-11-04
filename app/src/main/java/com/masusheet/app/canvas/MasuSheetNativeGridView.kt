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

/**
 * ============================================================================
 * [EN] 2-Layer Hardware-Accelerated Spreadsheet Grid View, Coordinate Math, & Hit-Testing
 * Implements [MasuSheetNativeGridView] with cached Layer-1 bitmap invalidation, focal-point pinch zoom (`25%..400%`), 4-quadrant Freeze Panes math, and cell/header/object/chart hit-testing.
 *
 * ---
 * [JA / 日本語解説]
 * Layer 1 静的セルキャッシュと Layer 2 高速オーバーレイによる2層Canvas描画 ([MasuSheetNativeGridView])、ピンチズーム座標変換、4象限ウィンドウ枠固定計算、およびセル・見出し・オブジェクト当たり判定を担います。
 * ============================================================================
 */

/**
 * [EN] 2-layer hardware-accelerated custom Android `View` rendering cached static cells (Layer 1) and interactive overlays (Layer 2).
 * [JA] Layer 1（静的セルキャッシュ）と Layer 2（選択枠・見出し・グラフ・オブジェクト）の2層構造で高速描画するカスタム `View` です。
 */
class MasuSheetNativeGridView(
    context: Context,
    internal val viewModel: MasuSheetViewModel,
    internal val container: MasuSheetNativeSheetContainer
) : View(context) {

    internal val density = resources.displayMetrics.density.coerceAtLeast(1f)
    internal val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    internal val scroller = OverScroller(context)
    internal var velocityTracker: VelocityTracker? = null

    var currentViewport: NativeViewportSnapshot = NativeViewportSnapshot()
        internal set
    var isDarkTheme: Boolean = true
        internal set
    var zoomPercent: Int = 100
        internal set
    var showGridlines: Boolean = true
        internal set
    var showHeadings: Boolean = true
        internal set
    var showFormulas: Boolean = false
        internal set
    var viewMode: String = "normal"
        internal set
    var freezeRows: Int = 0
        internal set
    var freezeCols: Int = 0
        internal set
    var isAutoFilterActive: Boolean = false
        internal set
    var sheetObjects: List<NativeSheetObject> = emptyList()
        internal set
    var selectedObjectId: String? = null
        internal set
    var selectedObjectIds: Set<String> = emptySet()
        internal set

    internal val imageBitmapCache = object : android.util.LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }
    internal var lastRequestedCoverageSheetUid: Long = -1L
    internal var lastRequestedCoverageRows: Int = 0
    internal var lastRequestedCoverageCols: Int = 0
    internal val objectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val objectPath = Path()

    enum class SheetObjectHitPart {
        BODY,
        RESIZE_TOP_LEFT,
        RESIZE_TOP_RIGHT,
        RESIZE_BOTTOM_LEFT,
        RESIZE_BOTTOM_RIGHT,
        RESIZE_TOP,
        RESIZE_BOTTOM,
        RESIZE_LEFT,
        RESIZE_RIGHT,
        ROTATE_HANDLE;

        val isResizeHandle: Boolean
            get() = this != BODY && this != ROTATE_HANDLE
    }

    // Sheet Object drag/resize/rotate state
    internal var draggingObjectId: String? = null
    internal var activeObjectResizePart: SheetObjectHitPart? = null
    internal var isRotatingObject: Boolean = false
    internal var objectDragStartScreenX: Float = 0f
    internal var objectDragStartScreenY: Float = 0f
    internal var objectStartXPts: Float = 0f
    internal var objectStartYPts: Float = 0f
    internal var objectStartWPts: Float = 0f
    internal var objectStartHPts: Float = 0f
    internal var multiDragStartPositions: Map<String, Pair<Float, Float>> = emptyMap()
    internal var lastObjectClickId: String? = null
    internal var lastObjectClickTimeMs: Long = 0L

    val zoomScale: Float
        get() = computeCanvasZoomScale(zoomPercent)

    val rowHeaderWidthPx: Float
        get() = if (showHeadings) 44f * density * zoomScale.coerceIn(0.85f, 1.35f) else 0f
    val colHeaderHeightPx: Float
        get() = if (showHeadings) 26f * density * zoomScale.coerceIn(0.85f, 1.35f) else 0f

    internal var scrollXPx: Int = 0
    internal var scrollYPx: Int = 0

    // Partial-Update Layer 1 Hardware Bitmap Cache
    internal var cachedLayerKey: GridLayerCacheKey? = null
    internal var cachedLayerBitmap: Bitmap? = null
    internal var cachedLayerCanvas: Canvas? = null
    var layer1RenderCount: Int = 0
        internal set
    var layer2OverlayRenderCount: Int = 0
        internal set

    // Column & Row Header Boundary Resize State
    var hoverColDividerIndex: Int = -1
        internal set
    var hoverRowDividerIndex: Int = -1
        internal set
    var resizingColIndex: Int = -1
        internal set
    var resizingRowIndex: Int = -1
        internal set
    internal var resizeStartScreenPos: Float = 0f
    internal var resizeInitialSizePt: Float = 0f
    internal var lastDividerClickIndex: Int = -1
    internal var lastDividerClickTimeMs: Long = 0L
    internal var lastFillCornerTapTimeMs: Long = 0L

    // Interaction tracking
    internal var downX: Float = 0f
    internal var downY: Float = 0f
    internal var lastTouchX: Float = 0f
    internal var lastTouchY: Float = 0f
    internal var hasMovedSinceDown: Boolean = false
    internal var isPanningTouch: Boolean = false
    internal var isDraggingRangeSelection: Boolean = false
    var isDraggingFillHandle: Boolean = false
        internal set
    internal var fillSrcStartRow: Int = 0
    internal var fillSrcStartCol: Int = 0
    internal var fillSrcEndRow: Int = 0
    internal var fillSrcEndCol: Int = 0
    internal var fillTargetEndRow: Int = 0
    internal var fillTargetEndCol: Int = 0
    internal var isDraggingSelectionMove: Boolean = false
    internal var moveSrcStartRow: Int = 0
    internal var moveSrcStartCol: Int = 0
    internal var moveSrcEndRow: Int = 0
    internal var moveSrcEndCol: Int = 0
    internal var moveAnchorOffsetRow: Int = 0
    internal var moveAnchorOffsetCol: Int = 0
    internal var moveTargetStartRow: Int = 0
    internal var moveTargetStartCol: Int = 0
    internal var isDraggingColMove: Boolean = false
    internal var colMoveStartCol: Int = 0
    internal var colMoveEndCol: Int = 0
    internal var colMoveTargetBeforeCol: Int = 0
    internal var isDraggingFreezeRowHandle: Boolean = false
    internal var isDraggingFreezeColHandle: Boolean = false
    internal var freezePreviewRow: Int = 0
    internal var freezePreviewCol: Int = 0
    // [EN] Chart X-Axis / Y-Axis cell range frame drag-move & corner-resize state on the worksheet grid
    // [JA] ワークシート上のグラフX軸・Y軸データ範囲ハイライト枠のドラッグ移動＆四隅リサイズ状態
    internal var isDraggingChartAxisRangeMove: Boolean = false
    internal var chartAxisDragMode: ChartRangeDragMode = ChartRangeDragMode.MOVE
    internal var chartAxisMoveChartId: String = ""
    internal var chartAxisMoveSeriesIdx: Int = 0
    internal var chartAxisMoveIsX: Boolean = false
    internal var chartAxisMoveSrcStartRow: Int = 0
    internal var chartAxisMoveSrcStartCol: Int = 0
    internal var chartAxisMoveSrcEndRow: Int = 0
    internal var chartAxisMoveSrcEndCol: Int = 0
    internal var chartAxisMoveAnchorOffsetRow: Int = 0
    internal var chartAxisMoveAnchorOffsetCol: Int = 0
    internal var chartAxisMoveTargetStartRow: Int = 0
    internal var chartAxisMoveTargetStartCol: Int = 0
    internal var chartAxisPreviewStartRow: Int = 0
    internal var chartAxisPreviewStartCol: Int = 0
    internal var chartAxisPreviewEndRow: Int = 0
    internal var chartAxisPreviewEndCol: Int = 0
    internal val lastDrawnChartRangeTargets = mutableListOf<ChartRangeRenderedTarget>()
    internal var isDraggingColHeaderSelection: Boolean = false
    internal var isDraggingRowHeaderSelection: Boolean = false
    internal var isDraggingFormulaPointRange: Boolean = false
    internal var isDraggingFormulaPointCol: Boolean = false
    internal var isDraggingFormulaPointRow: Boolean = false
    internal var dragAnchorRow: Int = 0
    internal var dragAnchorCol: Int = 0

    internal var lastClickRow: Int = -1
    internal var lastClickCol: Int = -1
    internal var lastClickTimeMs: Long = 0L

    internal var lastSwipeX: Float = 0f
    internal var lastSwipeY: Float = 0f
    internal var swipeTrackingActive: Boolean = false

    // =========================================================================
    // [EN] Touch-Screen & Trackpad Two-Finger Pinch-to-Zoom State & Detector
    // [JA] タッチ画面およびトラックパッドの2本指ピンチズーム状態とジェスチャー検出器
    // =========================================================================
    internal var isPinchZooming: Boolean = false
    internal var pinchAccumulatedZoom: Float = 100f
    internal var initialTwoFingerSpanPx: Float = 0f
    internal var lastTwoFingerSpanPx: Float = 0f

    /**
     * [EN] Applies a multiplicative zoom scale factor (`> 0f`) centered around `(focusX, focusY)`
     * so the worksheet cell under the user's fingers/cursor stays anchored during pinch-to-zoom (25%..400%).
     *
     * ---
     * [JA] ピンチ中心座標 `(focusX, focusY)` の直下にあるシート位置を維持したまま、拡大率（25%〜400%）を滑らかに更新します。
     */
    internal fun applyPinchScaleFactor(scaleFactor: Float, focusX: Float, focusY: Float) {
        if (scaleFactor <= 0f || scaleFactor.isNaN() || scaleFactor.isInfinite()) return
        if (abs(pinchAccumulatedZoom - zoomPercent.toFloat()) > 6f) {
            pinchAccumulatedZoom = zoomPercent.toFloat()
        }
        val safeFocusX = focusX.coerceIn(rowHeaderWidthPx, width.toFloat().coerceAtLeast(rowHeaderWidthPx + 1f))
        val safeFocusY = focusY.coerceIn(colHeaderHeightPx, height.toFloat().coerceAtLeast(colHeaderHeightPx + 1f))
        val focusSheetXPt = pxToPt((safeFocusX - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
        val focusSheetYPt = pxToPt((safeFocusY - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))

        pinchAccumulatedZoom = (pinchAccumulatedZoom * scaleFactor).coerceIn(25f, 400f)
        val newZoomInt = pinchAccumulatedZoom.roundToInt().coerceIn(25, 400)
        if (newZoomInt != zoomPercent) {
            zoomPercent = newZoomInt
            scrollXPx = (ptToPx(focusSheetXPt) - (safeFocusX - rowHeaderWidthPx)).roundToInt().coerceIn(0, maxScrollXPx())
            scrollYPx = (ptToPx(focusSheetYPt) - (safeFocusY - colHeaderHeightPx)).roundToInt().coerceIn(0, maxScrollYPx())
            viewModel.onSetZoomPercent(newZoomInt, persistPreference = false)
            if (container.isEditingInCell) {
                container.updateInCellEditorBounds()
            }
            invalidate()
        }
    }

    internal val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scroller.forceFinished(true)
                isPinchZooming = true
                pinchAccumulatedZoom = zoomPercent.toFloat()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                applyPinchScaleFactor(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isPinchZooming = false
                viewModel.onSetZoomPercent(zoomPercent, persistPreference = true)
            }
        }
    )

    internal val longPressGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (resizingColIndex < 0 &&
                    resizingRowIndex < 0 &&
                    !isPanchOrPanning() &&
                    !isDraggingFillHandle &&
                    !isDraggingSelectionMove &&
                    !isDraggingColMove &&
                    !isDraggingFreezeRowHandle &&
                    !isDraggingFreezeColHandle &&
                    !isDraggingFormulaPointRange &&
                    !isDraggingFormulaPointCol &&
                    !isDraggingFormulaPointRow &&
                    !container.canPointFormulaReference() &&
                    !hasMovedSinceDown &&
                    !isPointOnSelectionCornerHandle(downX, downY) &&
                    !isPointOnSelectionCornerHandle(e.x, e.y)
                ) {
                    isDraggingRangeSelection = false
                    handleRightClickAt(e.x, e.y)
                }
            }
        }
    )

    internal fun isPanchOrPanning(): Boolean = isPanningTouch || isPinchZooming || scaleGestureDetector.isInProgress

    // Pre-allocated Paints (zero allocation during onDraw)
    internal val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val gridlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    internal val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    internal val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
    }
    internal val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val headerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    internal val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x221F9D55
    }
    internal val selectionBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF1F9D55.toInt()
        strokeWidth = 2.2f * density
    }
    internal val resizeGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF1F9D55.toInt()
        strokeWidth = 2.5f * density
    }
    internal val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF1F9D55.toInt()
    }
    internal val checkboxBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val checkboxCheckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    internal val scrollbarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x6694A3B8
    }
    internal val scratchRect = RectF()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    var isTouchRangeSelectMode: Boolean = false
        internal set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredW = resolveSize((1080f * density).roundToInt().coerceAtLeast(320), widthMeasureSpec)
        val measuredH = resolveSize((1600f * density).roundToInt().coerceAtLeast(480), heightMeasureSpec)
        setMeasuredDimension(measuredW, measuredH)
    }

    fun bindState(state: MasuSheetUiState) {
        val prevUid = currentViewport.uid
        val prevSheet = currentViewport.sheetIndex
        val prevZoom = zoomPercent
        currentViewport = state.viewport
        isDarkTheme = state.isDarkTheme
        zoomPercent = state.zoomPercent
        showGridlines = state.showGridlines
        showHeadings = state.showHeadings
        showFormulas = state.showFormulas
        viewMode = state.viewMode
        freezeRows = state.freezeRows
        freezeCols = state.freezeCols
        isAutoFilterActive = state.isAutoFilterActive
        sheetObjects = state.sheetObjects
        selectedObjectId = state.selectedObjectId
        selectedObjectIds = state.effectiveSelectedObjectIds
        isTouchRangeSelectMode = state.isTouchRangeSelectMode
        if (prevUid != currentViewport.uid || prevSheet != currentViewport.sheetIndex) {
            scrollXPx = 0
            scrollYPx = 0
            lastRequestedCoverageSheetUid = -1L
            lastRequestedCoverageRows = 0
            lastRequestedCoverageCols = 0
            scroller.forceFinished(true)
        } else if (prevZoom != state.zoomPercent && prevZoom > 0 && !isPinchZooming) {
            val ratio = state.zoomPercent.toFloat() / prevZoom.toFloat()
            scrollXPx = (scrollXPx * ratio).roundToInt()
            scrollYPx = (scrollYPx * ratio).roundToInt()
        }
        clampScrollBounds()
        invalidate()
    }

    internal fun ptToPx(pt: Float): Float = pt * density * zoomScale
    internal fun pxToPt(px: Float): Float = px / (density * zoomScale)

    internal fun maxScrollXPx(): Int {
        val contentW = ptToPx(currentViewport.totalSheetWidthPt)
        val availW = (width - rowHeaderWidthPx).coerceAtLeast(1f)
        return (contentW - availW + 120f * density).roundToInt().coerceAtLeast(0)
    }

    internal fun maxScrollYPx(): Int {
        val contentH = ptToPx(currentViewport.totalSheetHeightPt)
        val availH = (height - colHeaderHeightPx).coerceAtLeast(1f)
        return (contentH - availH + 120f * density).roundToInt().coerceAtLeast(0)
    }

    internal fun clampScrollBounds() {
        scrollXPx = scrollXPx.coerceIn(0, maxScrollXPx())
        scrollYPx = scrollYPx.coerceIn(0, maxScrollYPx())
    }

    internal fun frozenRightPx(snap: NativeViewportSnapshot = currentViewport): Float {
        return if (freezeCols > 0 && freezeCols < snap.colOffsets.size) {
            rowHeaderWidthPx + ptToPx(snap.colOffsets[freezeCols])
        } else {
            rowHeaderWidthPx
        }
    }

    internal fun frozenBottomPx(snap: NativeViewportSnapshot = currentViewport): Float {
        return if (freezeRows > 0 && freezeRows < snap.rowOffsets.size) {
            colHeaderHeightPx + ptToPx(snap.rowOffsets[freezeRows])
        } else {
            colHeaderHeightPx
        }
    }

    internal fun effectiveScrollXForCol(col: Int): Int = if (freezeCols > 0 && col < freezeCols) 0 else scrollXPx
    internal fun effectiveScrollYForRow(row: Int): Int = if (freezeRows > 0 && row < freezeRows) 0 else scrollYPx

    internal fun screenXToSheetCol(screenX: Float): Int {
        val snap = currentViewport
        val fRight = frozenRightPx(snap)
        val effScrollX = if (freezeCols > 0 && screenX < fRight) 0 else scrollXPx
        return snap.colAtX(pxToPt((screenX - rowHeaderWidthPx + effScrollX).coerceAtLeast(0f)))
    }

    internal fun screenYToSheetRow(screenY: Float): Int {
        val snap = currentViewport
        val fBot = frozenBottomPx(snap)
        val effScrollY = if (freezeRows > 0 && screenY < fBot) 0 else scrollYPx
        return snap.rowAtY(pxToPt((screenY - colHeaderHeightPx + effScrollY).coerceAtLeast(0f)))
    }

    fun getCellScreenRect(row: Int, col: Int): RectF {
        val (anchorR, anchorC) = currentViewport.resolveAnchorCell(row, col)
        val effScrollX = effectiveScrollXForCol(anchorC)
        val effScrollY = effectiveScrollYForRow(anchorR)
        val boundsPt = currentViewport.cellBoundsPt(row, col)
        val left = rowHeaderWidthPx + ptToPx(boundsPt[0]) - effScrollX
        val top = colHeaderHeightPx + ptToPx(boundsPt[1]) - effScrollY
        val right = rowHeaderWidthPx + ptToPx(boundsPt[2]) - effScrollX
        val bottom = colHeaderHeightPx + ptToPx(boundsPt[3]) - effScrollY
        return RectF(left, top, right, bottom)
    }

    fun getSheetObjectScreenRect(obj: NativeSheetObject): RectF {
        val left = rowHeaderWidthPx + ptToPx(obj.xPt) - scrollXPx
        val top = colHeaderHeightPx + ptToPx(obj.yPt) - scrollYPx
        val right = left + ptToPx(obj.widthPt)
        val bottom = top + ptToPx(obj.heightPt)
        return RectF(left, top, right, bottom)
    }

    fun getSelectionScreenRect(): RectF {
        val snap = currentViewport
        if (snap.rowHeights.isEmpty() || snap.colWidths.isEmpty()) return RectF()
        val r0 = min(snap.selStartRow, snap.selEndRow).coerceIn(0, snap.rowHeights.size - 1)
        val c0 = min(snap.selStartCol, snap.selEndCol).coerceIn(0, snap.colWidths.size - 1)
        val r1 = max(snap.selStartRow, snap.selEndRow).coerceIn(r0, snap.rowHeights.size - 1)
        val c1 = max(snap.selStartCol, snap.selEndCol).coerceIn(c0, snap.colWidths.size - 1)
        val selLeft = rowHeaderWidthPx + ptToPx(snap.colOffsets[c0]) - scrollXPx
        val selTop = colHeaderHeightPx + ptToPx(snap.rowOffsets[r0]) - scrollYPx
        val selRight = rowHeaderWidthPx + ptToPx(snap.colOffsets[c1 + 1]) - scrollXPx
        val selBottom = colHeaderHeightPx + ptToPx(snap.rowOffsets[r1 + 1]) - scrollYPx
        return RectF(selLeft, selTop, selRight, selBottom)
    }

    fun isPointOnSelectionCornerHandle(screenX: Float, screenY: Float): Boolean {
        val selRect = getSelectionScreenRect()
        if (selRect.isEmpty) return false
        val cornerCenterX = selRect.right + 1.5f * density
        val cornerCenterY = selRect.bottom + 1.5f * density
        val hitRadius = 10f * density
        val insideCellGuardX = selRect.right - 7f * density
        val insideCellGuardY = selRect.bottom - 7f * density
        return screenX >= insideCellGuardX &&
            screenY >= insideCellGuardY &&
            abs(screenX - cornerCenterX) <= hitRadius &&
            abs(screenY - cornerCenterY) <= hitRadius
    }

    /**
     * A7: Hit-tests the top/left/bottom/right border of the selection rectangle (excluding the bottom-right AutoFill corner)
     * so dragging the selection border moves the selected cells (`Drag::Move`, `interact.rs:457-459`).
     */
    fun isPointOnSelectionBorder(screenX: Float, screenY: Float): Boolean {
        if (isPointOnSelectionCornerHandle(screenX, screenY)) return false
        val selRect = getSelectionScreenRect()
        if (selRect.isEmpty) return false
        val borderSlop = 5.5f * density
        val nearTop = abs(screenY - selRect.top) <= borderSlop && screenX in (selRect.left - borderSlop)..(selRect.right + borderSlop)
        val nearBottom = abs(screenY - selRect.bottom) <= borderSlop && screenX in (selRect.left - borderSlop)..(selRect.right - 10f * density)
        val nearLeft = abs(screenX - selRect.left) <= borderSlop && screenY in (selRect.top - borderSlop)..(selRect.bottom + borderSlop)
        val nearRight = abs(screenX - selRect.right) <= borderSlop && screenY in (selRect.top - borderSlop)..(selRect.bottom - 10f * density)
        return nearTop || nearBottom || nearLeft || nearRight
    }

    /**
     * A10: Hit-tests the Data Validation `[▾]` dropdown button on the right edge of the active cell.
     */
    fun isPointOnValidationDropdownBadge(screenX: Float, screenY: Float): Boolean {
        val valModel = viewModel.getActiveCellValidation() ?: return false
        if (!valModel.kind.equals("list", ignoreCase = true) || !valModel.inCellDropdown || valModel.items.isEmpty()) {
            return false
        }
        val activeRect = getCellScreenRect(currentViewport.activeRow, currentViewport.activeCol)
        if (activeRect.isEmpty) return false
        val badgeW = 20f * density
        val badgeRect = RectF(activeRect.right, activeRect.top, activeRect.right + badgeW + 4f * density, activeRect.bottom)
        val insideRight = RectF((activeRect.right - badgeW).coerceAtLeast(activeRect.left + activeRect.width() * 0.55f), activeRect.top, activeRect.right, activeRect.bottom)
        return badgeRect.contains(screenX, screenY) || insideRight.contains(screenX, screenY)
    }

    /**
     * A11: Hit-tests the horizontal Freeze Pane divider line (`Drag::Freeze`, `interact.rs:371-375`).
     */
    fun findFreezeRowDividerAt(screenX: Float, screenY: Float): Boolean {
        val snap = currentViewport
        if (freezeRows <= 0 || freezeRows >= snap.rowOffsets.size) return false
        val freezeY = frozenBottomPx(snap)
        return screenX >= rowHeaderWidthPx && abs(screenY - freezeY) <= 7f * density
    }

    /**
     * A11: Hit-tests the vertical Freeze Pane divider line (`Drag::Freeze`, `interact.rs:371-375`).
     */
    fun findFreezeColDividerAt(screenX: Float, screenY: Float): Boolean {
        val snap = currentViewport
        if (freezeCols <= 0 || freezeCols >= snap.colOffsets.size) return false
        val freezeX = frozenRightPx(snap)
        return screenY >= colHeaderHeightPx && abs(screenX - freezeX) <= 7f * density
    }

    internal fun effectiveSheetObjects(sheetIndex: Int): List<NativeSheetObject> {
        val snap = currentViewport
        val uiVal = viewModel.uiState.value
        return if (sheetObjects.none { it.sheetIndex == sheetIndex } &&
            uiVal.activeSample == "sales" &&
            !viewModel.hasMaterializedSalesDashboardCharts(sheetIndex) &&
            snap.cellAt(1, 1)?.displayText == "Quarterly Sales Dashboard"
        ) {
            val kLeftPt = snap.colOffsets.getOrElse(10) { 690f }
            val qRightPt = snap.colOffsets.getOrElse(17) { kLeftPt + 420f }
            val chartWidthPt = (qRightPt - kLeftPt).coerceAtLeast(300f)
            val topChartY1 = snap.rowOffsets.getOrElse(2) { 44f }
            val topChartY2 = snap.rowOffsets.getOrElse(16) { topChartY1 + 260f }
            val botChartY1 = snap.rowOffsets.getOrElse(18) { 360f }
            val botChartY2 = snap.rowOffsets.getOrElse(32) { botChartY1 + 260f }
            val regions = listOf("North", "South", "East", "West", "APAC", "LATAM")
            listOf(
                NativeSheetObject(
                    id = "dashboard_chart_revenue_by_region",
                    sheetIndex = sheetIndex,
                    kind = SheetObjectKind.CHART,
                    subType = "bar_v",
                    title = "Revenue by Region",
                    xPt = kLeftPt,
                    yPt = topChartY1,
                    widthPt = chartWidthPt,
                    heightPt = (topChartY2 - topChartY1).coerceAtLeast(180f),
                    fillColor = 0xFFFFFFFF.toInt(),
                    strokeColor = 0xFFCBD5E1.toInt(),
                    strokeWidthPt = 1f,
                    chartLabels = regions,
                    chartSeries = listOf(
                        ChartSeries("Q1", listOf(142500f, 98200f, 175800f, 121000f, 89400f, 54300f), 0xFF2563EB.toInt()),
                        ChartSeries("Q2", listOf(158300f, 112400f, 168900f, 134500f, 102100f, 61800f), 0xFF3B82F6.toInt()),
                        ChartSeries("Q3", listOf(171900f, 105800f, 192400f, 128700f, 118600f, 68200f), 0xFF60A5FA.toInt()),
                        ChartSeries("Q4", listOf(195400f, 128600f, 214100f, 149200f, 135200f, 77900f), 0xFF1D4ED8.toInt())
                    ),
                    sourceRangeText = "B4:F10"
                ),
                NativeSheetObject(
                    id = "dashboard_chart_share_of_total",
                    sheetIndex = sheetIndex,
                    kind = SheetObjectKind.CHART,
                    subType = "pie",
                    title = "Share of Annual Total",
                    xPt = kLeftPt,
                    yPt = botChartY1,
                    widthPt = chartWidthPt,
                    heightPt = (botChartY2 - botChartY1).coerceAtLeast(180f),
                    fillColor = 0xFFFFFFFF.toInt(),
                    strokeColor = 0xFFCBD5E1.toInt(),
                    strokeWidthPt = 1f,
                    chartLabels = regions,
                    chartSeries = listOf(
                        ChartSeries("Total", listOf(668100f, 445000f, 751200f, 533400f, 445300f, 262200f), 0xFF2563EB.toInt())
                    ),
                    sourceRangeText = "B4:B10,G4:G10"
                )
            )
        } else {
            sheetObjects
        }
    }

    /**
     * Hit-tests sheet objects on the current sheet in reverse draw order (topmost first).
     * Inverse-rotates `(screenX, screenY)` by `-obj.rotationDeg` around the object center so the
     * top rotation handle (`⟳`), all 8 resize handles (4 corners + Top/Bottom vertical + Left/Right horizontal),
     * and body hit-test accurately at any angle!
     */
    fun findSheetObjectAt(screenX: Float, screenY: Float): Pair<NativeSheetObject, SheetObjectHitPart>? {
        if (screenX < 0f || screenY < 0f) return null
        val curSheet = currentViewport.sheetIndex
        val effectiveObjects = effectiveSheetObjects(curSheet)
        val handleHitRadius = 16f * density
        val edgeStripRadius = 11f * density
        val rotateHandleOffsetPx = 22f * density
        val rotateHitRadius = 18f * density
        for (i in effectiveObjects.indices.reversed()) {
            val obj = effectiveObjects[i]
            if (obj.sheetIndex != curSheet) continue
            val rect = getSheetObjectScreenRect(obj)
            val cx = rect.centerX()
            val cy = rect.centerY()
            val rad = (-obj.rotationDeg * PI / 180.0).toFloat()
            val cosA = cos(rad)
            val sinA = sin(rad)
            val dx = screenX - cx
            val dy = screenY - cy
            val localX = cx + dx * cosA - dy * sinA
            val localY = cy + dx * sinA + dy * cosA
            val isObjSelected = obj.id == selectedObjectId || obj.id in selectedObjectIds

            if (isObjSelected) {
                // 1. Check Spreadsheet Top Rotation Handle (⟳) at (rect.centerX(), rect.top - 22dp)
                val rotHandleX = rect.centerX()
                val rotHandleY = rect.top - rotateHandleOffsetPx
                if (hypot(localX - rotHandleX, localY - rotHandleY) <= rotateHitRadius) {
                    return obj to SheetObjectHitPart.ROTATE_HANDLE
                }

                if (screenX >= rowHeaderWidthPx && screenY >= colHeaderHeightPx) {
                    // 2. Check All 4 Corner Resize Handles
                    if (abs(localX - rect.right) <= handleHitRadius && abs(localY - rect.bottom) <= handleHitRadius) {
                        return obj to SheetObjectHitPart.RESIZE_BOTTOM_RIGHT
                    }
                    if (abs(localX - rect.left) <= handleHitRadius && abs(localY - rect.bottom) <= handleHitRadius) {
                        return obj to SheetObjectHitPart.RESIZE_BOTTOM_LEFT
                    }
                    if (abs(localX - rect.right) <= handleHitRadius && abs(localY - rect.top) <= handleHitRadius) {
                        return obj to SheetObjectHitPart.RESIZE_TOP_RIGHT
                    }
                    if (abs(localX - rect.left) <= handleHitRadius && abs(localY - rect.top) <= handleHitRadius) {
                        return obj to SheetObjectHitPart.RESIZE_TOP_LEFT
                    }

                    // 3. Check 4 Midpoint / Edge Resize Handles (Vertical-Only Top/Bottom & Horizontal-Only Left/Right)
                    if (abs(localY - rect.bottom) <= edgeStripRadius &&
                        (abs(localX - cx) <= handleHitRadius * 1.4f || localX in rect.left..rect.right)
                    ) {
                        return obj to SheetObjectHitPart.RESIZE_BOTTOM
                    }
                    if (abs(localY - rect.top) <= edgeStripRadius &&
                        (abs(localX - cx) <= handleHitRadius * 1.4f || localX in rect.left..rect.right)
                    ) {
                        return obj to SheetObjectHitPart.RESIZE_TOP
                    }
                    if (abs(localX - rect.right) <= edgeStripRadius &&
                        (abs(localY - cy) <= handleHitRadius * 1.4f || localY in rect.top..rect.bottom)
                    ) {
                        return obj to SheetObjectHitPart.RESIZE_RIGHT
                    }
                    if (abs(localX - rect.left) <= edgeStripRadius &&
                        (abs(localY - cy) <= handleHitRadius * 1.4f || localY in rect.top..rect.bottom)
                    ) {
                        return obj to SheetObjectHitPart.RESIZE_LEFT
                    }
                }
            }

            if (screenX < rowHeaderWidthPx || screenY < colHeaderHeightPx) continue

            // 4. Check Object Body
            if (rect.contains(localX, localY)) {
                return obj to SheetObjectHitPart.BODY
            }
        }
        return null
    }

    /**
     * [EN] Drag interaction mode for a highlighted chart X-Axis or Y-Axis cell range frame on the worksheet grid.
     * [JA] ワークシート上のグラフX軸・Y軸データ範囲ハイライト枠のドラッグ操作モード（移動または四隅・上下辺リサイズ）。
     */
    enum class ChartRangeDragMode {
        MOVE,
        RESIZE_TOP_LEFT,
        RESIZE_TOP_RIGHT,
        RESIZE_BOTTOM_LEFT,
        RESIZE_BOTTOM_RIGHT,
        RESIZE_TOP,
        RESIZE_BOTTOM
    }

    /**
     * [EN] Exact screen geometry recorded when drawing a chart's X-Axis or Y-Axis cell range highlight on the grid.
     * [JA] グラフのX軸・Y軸データ範囲ハイライト描画時に記録された正確な画面矩形（枠および上部ラベルバッジ）。
     */
    data class ChartRangeRenderedTarget(
        val chartId: String,
        val seriesIndex: Int,
        val isXAxis: Boolean,
        val bounds: IntArray,
        val frameRect: RectF,
        val pillRect: RectF?
    )

    data class ChartAxisRangeDragHit(
        val chartId: String,
        val seriesIndex: Int,
        val isXAxis: Boolean,
        val bounds: IntArray,
        val dragMode: ChartRangeDragMode = ChartRangeDragMode.MOVE
    )

    fun getCellRangeScreenRect(r0: Int, c0: Int, r1: Int, c1: Int): RectF {
        val snap = currentViewport
        val maxR = (snap.rowHeights.size - 1).coerceAtLeast(0)
        val maxC = (snap.colWidths.size - 1).coerceAtLeast(0)
        val rr0 = min(r0, r1).coerceIn(0, maxR)
        val cc0 = min(c0, c1).coerceIn(0, maxC)
        val rr1 = max(r0, r1).coerceIn(rr0, maxR)
        val cc1 = max(c0, c1).coerceIn(cc0, maxC)
        val left = rowHeaderWidthPx + ptToPx(snap.colOffsets[cc0]) - scrollXPx
        val top = colHeaderHeightPx + ptToPx(snap.rowOffsets[rr0]) - scrollYPx
        val right = rowHeaderWidthPx + ptToPx(snap.colOffsets[cc1 + 1]) - scrollXPx
        val bottom = colHeaderHeightPx + ptToPx(snap.rowOffsets[rr1 + 1]) - scrollYPx
        return RectF(left, top, right, bottom)
    }

    /**
     * [EN] Hit-tests the highlighted X-Axis or Y-Axis cell range frame on the worksheet grid when a Chart is selected:
     * - 4 corner handles (`┌`, `┐`, `└`, `┘`) -> `RESIZE_TOP_LEFT`, `RESIZE_TOP_RIGHT`, `RESIZE_BOTTOM_LEFT`, `RESIZE_BOTTOM_RIGHT`
     *   (drag vertically or horizontally to resize/extend the data range).
     * - Top pill badge (`📏 X軸...`, `📈 系列...`) -> `MOVE` (tested against the exact drawn pill rectangle).
     * - Bottom or top horizontal edge -> `RESIZE_BOTTOM` / `RESIZE_TOP` (drag vertically to extend/shrink rows).
     * - Left or right vertical border -> `MOVE` (disambiguated by prioritizing the column `pointerCol` under the cursor).
     * - Interior cells are NEVER intercepted, preventing accidental X-Axis splitting/duplication when selecting cells.
     *
     * [JA] グラフ選択時にワークシート上に表示されるX軸・Y軸データ範囲ハイライト枠の当たり判定を行います：
     * - 四隅の四角ハンドル (`┌`, `┐`, `└`, `┘`) -> `RESIZE_*`（上下・左右へドラッグして選択範囲を拡張・縮小）
     * - 上部ラベルバッジ -> `MOVE`（実際に描画されたラベル矩形で正確に判定）
     * - 下辺・上辺 -> `RESIZE_BOTTOM` / `RESIZE_TOP`（縦方向の範囲伸縮）
     * - 左右の枠線 -> `MOVE`（カーソル下の列 `pointerCol` を優先して隣接列との境目を正確に判別）
     * - セル内部のドラッグは一切横取りせず、X軸枠の意図しない分裂・増殖を防止します。
     */
    fun findChartSourceRangeDragHitAt(screenX: Float, screenY: Float): ChartAxisRangeDragHit? {
        if (screenX < rowHeaderWidthPx || screenY < colHeaderHeightPx) return null
        val selId = selectedObjectId ?: return null
        val curSheet = currentViewport.sheetIndex
        val chartObj = effectiveSheetObjects(curSheet).firstOrNull {
            it.id == selId && it.sheetIndex == curSheet && it.kind == SheetObjectKind.CHART
        } ?: return null

        // Do not intercept clicks that are inside the chart object itself
        if (findSheetObjectAt(screenX, screenY) != null) return null

        val insetPx = 1.8f * density
        val targets: List<ChartRangeRenderedTarget> = lastDrawnChartRangeTargets
            .filter { it.chartId == chartObj.id }
            .ifEmpty {
                // Fallback target synthesis when called before a render pass (e.g., in unit tests)
                buildList {
                    val xBounds = viewModel.resolveChartSeriesAxisBounds(chartObj, 0, isXAxis = true)
                    if (xBounds != null) {
                        val raw = getCellRangeScreenRect(xBounds[0], xBounds[1], xBounds[2], xBounds[3])
                        if (!raw.isEmpty) {
                            val frame = RectF(raw.left + insetPx, raw.top + insetPx, raw.right - insetPx, raw.bottom - insetPx)
                            val pill = RectF(
                                frame.left + 4f * density,
                                (frame.top - 20f * density).coerceAtLeast(colHeaderHeightPx + 2f * density),
                                frame.left + 92f * density,
                                (frame.top - 3f * density).coerceAtLeast(colHeaderHeightPx + 18f * density)
                            )
                            add(ChartRangeRenderedTarget(chartObj.id, 0, true, xBounds, frame, pill))
                        }
                    }
                    for (sIdx in chartObj.chartSeries.indices) {
                        val yBounds = viewModel.resolveChartSeriesAxisBounds(chartObj, sIdx, isXAxis = false) ?: continue
                        val raw = getCellRangeScreenRect(yBounds[0], yBounds[1], yBounds[2], yBounds[3])
                        if (!raw.isEmpty) {
                            val frame = RectF(raw.left + insetPx, raw.top + insetPx, raw.right - insetPx, raw.bottom - insetPx)
                            val pill = RectF(
                                frame.left + 4f * density,
                                (frame.top - 20f * density).coerceAtLeast(colHeaderHeightPx + 2f * density),
                                frame.left + 92f * density,
                                (frame.top - 3f * density).coerceAtLeast(colHeaderHeightPx + 18f * density)
                            )
                            add(ChartRangeRenderedTarget(chartObj.id, sIdx, false, yBounds, frame, pill))
                        }
                    }
                }
            }
        if (targets.isEmpty()) return null

        val sheetXPt = pxToPt((screenX - rowHeaderWidthPx + scrollXPx).coerceAtLeast(0f))
        val sheetYPt = pxToPt((screenY - colHeaderHeightPx + scrollYPx).coerceAtLeast(0f))
        val pointerCol = currentViewport.colAtX(sheetXPt)
        val pointerRow = currentViewport.rowAtY(sheetYPt)

        // Prioritize targets whose column/row span actually contains the pointer cell
        val prioritizedTargets = targets.sortedWith(
            compareByDescending<ChartRangeRenderedTarget> {
                pointerCol in it.bounds[1]..it.bounds[3] && pointerRow in (it.bounds[0] - 1)..(it.bounds[2] + 1)
            }.thenByDescending {
                pointerCol in it.bounds[1]..it.bounds[3]
            }
        )

        // 1. Check 4 corner square resize handles first (`RESIZE_TOP_LEFT`, `RESIZE_TOP_RIGHT`, `RESIZE_BOTTOM_LEFT`, `RESIZE_BOTTOM_RIGHT`)
        val cornerHitRadius = 9.5f * density
        data class CornerCandidate(
            val target: ChartRangeRenderedTarget,
            val mode: ChartRangeDragMode,
            val distPx: Float,
            val inPointerCol: Boolean
        )
        val cornerCandidates = mutableListOf<CornerCandidate>()
        for (t in prioritizedTargets) {
            val r = t.frameRect
            val inCol = pointerCol in t.bounds[1]..t.bounds[3]
            val corners = listOf(
                Triple(r.left, r.top, ChartRangeDragMode.RESIZE_TOP_LEFT),
                Triple(r.right, r.top, ChartRangeDragMode.RESIZE_TOP_RIGHT),
                Triple(r.left, r.bottom, ChartRangeDragMode.RESIZE_BOTTOM_LEFT),
                Triple(r.right, r.bottom, ChartRangeDragMode.RESIZE_BOTTOM_RIGHT)
            )
            for ((cx, cy, mode) in corners) {
                if (abs(screenX - cx) <= cornerHitRadius && abs(screenY - cy) <= cornerHitRadius) {
                    cornerCandidates.add(
                        CornerCandidate(t, mode, hypot(screenX - cx, screenY - cy), inCol)
                    )
                }
            }
        }
        if (cornerCandidates.isNotEmpty()) {
            val best = cornerCandidates.minWithOrNull(
                compareByDescending<CornerCandidate> { it.inPointerCol }.thenBy { it.distPx }
            )!!
            return ChartAxisRangeDragHit(
                chartId = best.target.chartId,
                seriesIndex = best.target.seriesIndex,
                isXAxis = best.target.isXAxis,
                bounds = best.target.bounds,
                dragMode = best.mode
            )
        }

        // 2. Check top pill badges in reverse draw order (top-most visual badge first) -> `MOVE`
        val pillSlop = 2.5f * density
        val hitPills = targets.asReversed().filter { t ->
            val p = t.pillRect ?: return@filter false
            screenX in (p.left - pillSlop)..(p.right + pillSlop) &&
                screenY in (p.top - pillSlop)..(p.bottom + pillSlop)
        }
        if (hitPills.isNotEmpty()) {
            val bestPillTarget = hitPills.minByOrNull { t ->
                val p = t.pillRect!!
                hypot(screenX - p.centerX(), screenY - p.centerY())
            }!!
            return ChartAxisRangeDragHit(
                chartId = bestPillTarget.chartId,
                seriesIndex = bestPillTarget.seriesIndex,
                isXAxis = bestPillTarget.isXAxis,
                bounds = bestPillTarget.bounds,
                dragMode = ChartRangeDragMode.MOVE
            )
        }

        // 3. Check bottom and top horizontal edges -> `RESIZE_BOTTOM` / `RESIZE_TOP`
        val borderSlop = 7.5f * density
        for (t in prioritizedTargets) {
            val r = t.frameRect
            val withinHoriz = screenX in (r.left - borderSlop)..(r.right + borderSlop)
            if (withinHoriz && abs(screenY - r.bottom) <= borderSlop) {
                return ChartAxisRangeDragHit(
                    chartId = t.chartId,
                    seriesIndex = t.seriesIndex,
                    isXAxis = t.isXAxis,
                    bounds = t.bounds,
                    dragMode = ChartRangeDragMode.RESIZE_BOTTOM
                )
            }
            if (withinHoriz && abs(screenY - r.top) <= borderSlop) {
                return ChartAxisRangeDragHit(
                    chartId = t.chartId,
                    seriesIndex = t.seriesIndex,
                    isXAxis = t.isXAxis,
                    bounds = t.bounds,
                    dragMode = ChartRangeDragMode.RESIZE_TOP
                )
            }
        }

        // 4. Check left and right vertical borders -> `MOVE` (disambiguated by pointerCol and distance to edge)
        data class VerticalEdgeCandidate(
            val target: ChartRangeRenderedTarget,
            val distPx: Float,
            val inPointerCol: Boolean
        )
        val edgeCandidates = mutableListOf<VerticalEdgeCandidate>()
        for (t in prioritizedTargets) {
            val r = t.frameRect
            val withinVert = screenY in (r.top - borderSlop)..(r.bottom + borderSlop)
            if (!withinVert) continue
            val inCol = pointerCol in t.bounds[1]..t.bounds[3]
            val dLeft = abs(screenX - r.left)
            val dRight = abs(screenX - r.right)
            val minD = min(dLeft, dRight)
            if (minD <= borderSlop) {
                edgeCandidates.add(VerticalEdgeCandidate(t, minD, inCol))
            }
        }
        if (edgeCandidates.isNotEmpty()) {
            val bestEdge = edgeCandidates.minWithOrNull(
                compareByDescending<VerticalEdgeCandidate> { it.inPointerCol }.thenBy { it.distPx }
            )!!
            return ChartAxisRangeDragHit(
                chartId = bestEdge.target.chartId,
                seriesIndex = bestEdge.target.seriesIndex,
                isXAxis = bestEdge.target.isXAxis,
                bounds = bestEdge.target.bounds,
                dragMode = ChartRangeDragMode.MOVE
            )
        }

        return null
    }

    /**
     * Hit-tests interactive sub-elements inside a Chart (`"xAxis"`, `"yAxis"`, `"series:0"`, `"series:1"`, ...).
     * - Clicking the X-Axis label strip selects `"xAxis"` (entire X-Axis).
     * - Clicking the Y-Axis label strip selects `"yAxis"` (entire Y-Axis).
     * - Clicking a series line, data point, bar, or legend item selects `"series:<idx>"`.
     */
    fun hitTestChartInteractiveElement(
        obj: NativeSheetObject,
        screenX: Float,
        screenY: Float
    ): String {
        val rect = getSheetObjectScreenRect(obj)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val rad = (-obj.rotationDeg * PI / 180.0).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        val dx = screenX - cx
        val dy = screenY - cy
        val lx = cx + dx * cosA - dy * sinA
        val ly = cy + dx * sinA + dy * cosA
        if (!rect.contains(lx, ly)) return ""

        val pad = 8f * density
        val titleBottom = rect.top + 22f * density * zoomScale
        val legendBottom = titleBottom + 16f * density * zoomScale
        val seriesList = effectiveChartSeries(obj)
        val catCount = max(
            obj.chartLabels.size,
            seriesList.maxOfOrNull { it.values.size } ?: 0
        ).coerceAtLeast(1)

        val isRadialOrTile = obj.subType in setOf(
            "pie", "pie_exploded", "doughnut", "sunburst", "treemap",
            "radar", "radar_markers", "radar_filled", "funnel"
        )
        val isHorizontalBar = obj.subType in setOf("bar_h", "bar_stacked", "bar_stacked100")

        val yAxisLabelWidth = if (isRadialOrTile) 8f * density else 34f * density * zoomScale
        val xAxisLabelHeight = if (isRadialOrTile) 6f * density else 18f * density * zoomScale
        val plotTop = legendBottom + 4f * density
        val plotBottom = rect.bottom - pad - xAxisLabelHeight
        val plotLeft = rect.left + pad + yAxisLabelWidth
        val plotRight = rect.right - pad - 8f * density

        // 1. Check Legend Strip -> select that series
        if (ly in (titleBottom - 2f * density)..(legendBottom + 4f * density) && seriesList.isNotEmpty()) {
            val isEn = viewModel.uiState.value.language == "en"
            textPaint.textSize = 9.2f * density * zoomScale
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            var cursorX = rect.left + pad
            val swatchSize = 8f * density * zoomScale
            for ((sIdx, series) in seriesList.withIndex()) {
                val rawSName = series.name.ifBlank { if (isEn) "Series ${sIdx + 1}" else "系列${sIdx + 1}" }
                val sName = SpreadsheetI18n.translateChartDisplayText(rawSName, isEn)
                val itemRight = cursorX + swatchSize + 3.5f * density + textPaint.measureText(sName) + 8f * density
                if (lx in (cursorX - 3f * density)..itemRight) {
                    return "series:$sIdx"
                }
                cursorX = itemRight + 2f * density
            }
        }

        // 2. Check X-Axis Strip (Bottom horizontal axis) & Y-Axis Strip (Left vertical axis)
        if (!isRadialOrTile) {
            if (ly >= plotBottom - 5f * density && ly <= rect.bottom && lx >= plotLeft - 8f * density && lx <= plotRight + 8f * density) {
                return if (isHorizontalBar) "yAxis" else "xAxis"
            }
            if (lx >= rect.left && lx <= plotLeft + 6f * density && ly >= plotTop - 6f * density && ly <= plotBottom + 6f * density) {
                return if (isHorizontalBar) "xAxis" else "yAxis"
            }
        }

        // 3. Check Plot Area for Series Line / Bar / Point hit
        if (lx in (plotLeft - 4f * density)..(plotRight + 4f * density) &&
            ly in (plotTop - 4f * density)..(plotBottom + 4f * density) &&
            seriesList.isNotEmpty()
        ) {
            val allValues = seriesList.flatMap { it.values }
            val minVal = obj.yAxisMin ?: (allValues.minOrNull() ?: 0f).coerceAtMost(0f)
            val maxVal = (obj.yAxisMax ?: (allValues.maxOrNull() ?: 100f)).coerceAtLeast(minVal + 1f)
            val valSpan = (maxVal - minVal).coerceAtLeast(1f)
            val slotW = ((plotRight - plotLeft) / catCount).coerceAtLeast(1f)

            when (obj.subType) {
                "line", "line_markers", "line_stacked", "line_stacked100", "line_smooth",
                "area", "area_stacked", "area_stacked100",
                "scatter", "scatter_lines", "bubble" -> {
                    val isStacked = obj.subType.endsWith("stacked") || obj.subType.endsWith("stacked100")
                    val is100 = obj.subType.endsWith("stacked100")
                    val maxCatSum = (0 until catCount).maxOfOrNull { cIdx ->
                        seriesList.sumOf { abs(it.values.getOrElse(cIdx) { 0f }).toDouble() }.toFloat()
                    }?.coerceAtLeast(1f) ?: valSpan
                    val effMin = if (is100 || isStacked) 0f else minVal
                    val effSpan = if (is100) 100f else if (isStacked) maxCatSum else valSpan
                    val cumulativeHeights = FloatArray(catCount) { 0f }

                    var bestSeriesIdx = -1
                    var bestDist = Float.MAX_VALUE
                    for ((sIdx, series) in seriesList.withIndex()) {
                        val vals = series.values
                        if (vals.isEmpty()) continue
                        val ptsX = FloatArray(catCount)
                        val ptsY = FloatArray(catCount)
                        for (cIdx in 0 until catCount) {
                            ptsX[cIdx] = if (catCount == 1) (plotLeft + plotRight) * 0.5f else plotLeft + (cIdx + 0.5f) * slotW
                            val rawV = vals.getOrElse(cIdx) { 0f }
                            val catSum = seriesList.sumOf { abs(it.values.getOrElse(cIdx) { 0f }).toDouble() }.toFloat().coerceAtLeast(1f)
                            val prevRatio = cumulativeHeights[cIdx]
                            val addRatio = when {
                                is100 -> (abs(rawV) / catSum)
                                isStacked -> (abs(rawV) / effSpan)
                                else -> ((rawV - effMin) / effSpan)
                            }
                            val newRatio = if (isStacked) (prevRatio + addRatio) else addRatio
                            ptsY[cIdx] = plotBottom - newRatio * (plotBottom - plotTop)
                            if (isStacked) cumulativeHeights[cIdx] = newRatio

                            if (ptsY[cIdx] in (plotTop - 2f * density)..(plotBottom + 2f * density)) {
                                val dPt = hypot(lx - ptsX[cIdx], ly - ptsY[cIdx])
                                if (dPt < bestDist) {
                                    bestDist = dPt
                                    bestSeriesIdx = sIdx
                                }
                            }
                        }
                        for (i in 1 until catCount) {
                            val ax = ptsX[i - 1]
                            val ay = ptsY[i - 1]
                            val bx = ptsX[i]
                            val by = ptsY[i]
                            if ((ay < plotTop && by < plotTop) || (ay > plotBottom && by > plotBottom)) continue
                            val abx = bx - ax
                            val aby = by - ay
                            val abLenSq = abx * abx + aby * aby
                            val t = if (abLenSq > 0.01f) (((lx - ax) * abx + (ly - ay) * aby) / abLenSq).coerceIn(0f, 1f) else 0f
                            val projX = ax + t * abx
                            val projY = ay + t * aby
                            if (projY !in (plotTop - 4f * density)..(plotBottom + 4f * density)) continue
                            val dSeg = hypot(lx - projX, ly - projY)
                            if (dSeg < bestDist) {
                                bestDist = dSeg
                                bestSeriesIdx = sIdx
                            }
                        }
                    }
                    if (bestSeriesIdx >= 0 && bestDist <= 28f * density) {
                        return "series:$bestSeriesIdx"
                    }
                }

                "bar_h", "bar_stacked", "bar_stacked100" -> {
                    val slotH = ((plotBottom - plotTop) / catCount).coerceAtLeast(1f)
                    val sCount = seriesList.size.coerceAtLeast(1)
                    val relY = ((ly - plotTop) % slotH).coerceAtLeast(0f) / slotH
                    val sIdx = (relY * sCount).toInt().coerceIn(0, sCount - 1)
                    return "series:$sIdx"
                }

                else -> {
                    val cIdx = ((lx - plotLeft) / slotW).toInt().coerceIn(0, catCount - 1)
                    val sCount = seriesList.size.coerceAtLeast(1)
                    val isStacked = obj.subType == "column_stacked" || obj.subType == "column_stacked100"
                    if (isStacked && sCount > 1) {
                        val relY = ((plotBottom - ly) / (plotBottom - plotTop).coerceAtLeast(1f)).coerceIn(0f, 0.999f)
                        val sIdx = (relY * sCount).toInt().coerceIn(0, sCount - 1)
                        return "series:$sIdx"
                    }
                    val slotLeft = plotLeft + cIdx * slotW
                    val relX = ((lx - slotLeft) / slotW).coerceIn(0f, 0.999f)
                    val sIdx = (relX * sCount).toInt().coerceIn(0, sCount - 1)
                    return "series:$sIdx"
                }
            }
        }
        return ""
    }

    /**
     * Returns the column index `c` if `(screenX, screenY)` is on the right boundary between column `c` and `c + 1`
     * inside the Column Header Bar (`A, B, C, D...`), or `-1` otherwise.
     */
    /**
     * [EN] Hit-tests column header boundaries (`colOffsets[c + 1]`) AND the blank gap between column letters
     * when a column is expanded (`colW > 96pt`), allowing one-click reset to standard width (`64pt`).
     *
     * ---
     * [JA] 列ヘッダの境界線（前後10dp）および、列が広がっている場合（`colW > 96pt`）の列文字間の空白ギャップを判定し、
     * クリックで元の標準幅（`64pt` または文字幅）へ戻せるようにします。
     */
    fun findColumnDividerAt(screenX: Float, screenY: Float): Int {
        if (!showHeadings || screenY < 0f || screenY > colHeaderHeightPx + 6f * density || screenX < rowHeaderWidthPx) {
            return -1
        }
        val snap = currentViewport
        val hitSlopPx = 10f * density
        val startCol = snap.colAtX(pxToPt((scrollXPx - 20f).coerceAtLeast(0f))).coerceAtLeast(0)
        val endCol = snap.colAtX(pxToPt((scrollXPx + max(width, 1080)).toFloat())).coerceAtMost(snap.colWidths.size - 1)
        var bestCol = -1
        var bestDist = hitSlopPx
        for (c in startCol..endCol) {
            val rightBorderPx = rowHeaderWidthPx + ptToPx(snap.colOffsets[c + 1]) - scrollXPx
            val dist = abs(screenX - rightBorderPx)
            if (dist <= bestDist) {
                bestDist = dist
                bestCol = c
            }
        }
        if (freezeCols > 0) {
            val maxFrozenCol = (freezeCols - 1).coerceAtMost(snap.colWidths.size - 1)
            for (c in 0..maxFrozenCol) {
                val rightBorderPx = rowHeaderWidthPx + ptToPx(snap.colOffsets[c + 1])
                val dist = abs(screenX - rightBorderPx)
                if (dist <= bestDist) {
                    bestDist = dist
                    bestCol = c
                }
            }
        }
        return bestCol
    }

    /**
     * [EN] Hit-tests row header boundaries (`rowOffsets[r + 1]`) within 10dp slop for row resize / double-click AutoFit.
     * [JA] 行ヘッダの境界線（前後10dp）を判定し、ドラッグリサイズやダブルクリック自動調整を行います。
     */
    fun findRowDividerAt(screenX: Float, screenY: Float): Int {
        if (!showHeadings || screenX < 0f || screenX > rowHeaderWidthPx + 6f * density || screenY < colHeaderHeightPx) {
            return -1
        }
        val snap = currentViewport
        val hitSlopPx = 10f * density
        val startRow = snap.rowAtY(pxToPt((scrollYPx - 20f).coerceAtLeast(0f))).coerceAtLeast(0)
        val endRow = snap.rowAtY(pxToPt((scrollYPx + max(height, 720)).toFloat())).coerceAtMost(snap.rowHeights.size - 1)
        var bestRow = -1
        var bestDist = hitSlopPx
        for (r in startRow..endRow) {
            val bottomBorderPx = colHeaderHeightPx + ptToPx(snap.rowOffsets[r + 1]) - scrollYPx
            val dist = abs(screenY - bottomBorderPx)
            if (dist <= bestDist) {
                bestDist = dist
                bestRow = r
            }
        }
        if (freezeRows > 0) {
            val maxFrozenRow = (freezeRows - 1).coerceAtMost(snap.rowHeights.size - 1)
            for (r in 0..maxFrozenRow) {
                val bottomBorderPx = colHeaderHeightPx + ptToPx(snap.rowOffsets[r + 1])
                val dist = abs(screenY - bottomBorderPx)
                if (dist <= bestDist) {
                    bestDist = dist
                    bestRow = r
                }
            }
        }
        return bestRow
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val x = event.getX(pointerIndex)
            val y = event.getY(pointerIndex)
            if (resizingColIndex >= 0 || findColumnDividerAt(x, y) >= 0) {
                return PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
            }
            if (resizingRowIndex >= 0 || findRowDividerAt(x, y) >= 0) {
                return PointerIcon.getSystemIcon(context, PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
            }
            return PointerIcon.getSystemIcon(context, PointerIcon.TYPE_ARROW)
        }
        return super.onResolvePointerIcon(event, pointerIndex)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                val cDiv = findColumnDividerAt(event.x, event.y)
                val rDiv = if (cDiv < 0) findRowDividerAt(event.x, event.y) else -1
                if (cDiv != hoverColDividerIndex || rDiv != hoverRowDividerIndex) {
                    hoverColDividerIndex = cDiv
                    hoverRowDividerIndex = rDiv
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        pointerIcon = when {
                            cDiv >= 0 -> PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
                            rDiv >= 0 -> PointerIcon.getSystemIcon(context, PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
                            else -> PointerIcon.getSystemIcon(context, PointerIcon.TYPE_ARROW)
                        }
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                if (hoverColDividerIndex != -1 || hoverRowDividerIndex != -1) {
                    hoverColDividerIndex = -1
                    hoverRowDividerIndex = -1
                    invalidate()
                }
                return true
            }
        }
        return super.onHoverEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollXPx = scroller.currX.coerceIn(0, maxScrollXPx())
            scrollYPx = scroller.currY.coerceIn(0, maxScrollYPx())
            if (container.isEditingInCell) {
                container.updateInCellEditorBounds()
            }
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        // 1. Check if Layer 1 (static cells, backgrounds, gridlines, borders, text, checkboxes) needs update
        val desiredKey = GridLayerCacheKey(
            uid = currentViewport.uid,
            revision = currentViewport.revision,
            sheetIndex = currentViewport.sheetIndex,
            scrollX = scrollXPx,
            scrollY = scrollYPx,
            zoomPercent = zoomPercent,
            isDarkTheme = isDarkTheme,
            viewWidth = w,
            viewHeight = h,
            showGridlines = showGridlines,
            showHeadings = showHeadings,
            showFormulas = showFormulas,
            freezeRows = freezeRows,
            freezeCols = freezeCols
        )

        // Dynamically expand viewport coverage when scrolling near the loaded row/col horizon (H-03)
        if (scrollXPx > 0 || scrollYPx > 0) {
            val snap = currentViewport
            val visEndCol = snap.colAtX(pxToPt((scrollXPx + w).toFloat()))
            val visEndRow = snap.rowAtY(pxToPt((scrollYPx + h).toFloat()))
            val nearColLimit = scrollXPx > 0 && visEndCol >= snap.colWidths.size - 8 && snap.colWidths.size < 512
            val nearRowLimit = scrollYPx > 0 && visEndRow >= snap.rowHeights.size - 15 && snap.rowHeights.size < 20_000
            if (nearColLimit || nearRowLimit) {
                val reqRows = max(snap.rowHeights.size - 1, if (nearRowLimit) visEndRow + 80 else snap.rowHeights.size - 1)
                val reqCols = max(snap.colWidths.size - 1, if (nearColLimit) visEndCol + 26 else snap.colWidths.size - 1)
                if (lastRequestedCoverageSheetUid != snap.uid || reqRows > lastRequestedCoverageRows || reqCols > lastRequestedCoverageCols) {
                    lastRequestedCoverageSheetUid = snap.uid
                    lastRequestedCoverageRows = max(lastRequestedCoverageRows, reqRows)
                    lastRequestedCoverageCols = max(lastRequestedCoverageCols, reqCols)
                    post {
                        viewModel.onEnsureViewportCoverage(reqRows, reqCols)
                    }
                }
            }
        }

        var bmp = cachedLayerBitmap
        if (bmp == null || bmp.width != w || bmp.height != h) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            cachedLayerBitmap = bmp
            cachedLayerCanvas = Canvas(bmp)
            cachedLayerKey = null
        }

        if (cachedLayerKey != desiredKey) {
            cachedLayerCanvas?.let { layerCanvas ->
                renderStaticCellsLayer(layerCanvas, w, h)
                cachedLayerKey = desiredKey
                layer1RenderCount++
            }
        }

        // Blit cached Layer 1 bitmap in a single hardware operation
        canvas.drawBitmap(bmp, 0f, 0f, null)

        // 2. Render Lightweight Layer 2 Overlay (Selection Box, Sheet Objects, Headers, Resize Indicators, Scrollbars)
        renderSelectionAndHeadersOverlay(canvas, w, h)
        layer2OverlayRenderCount++
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        handleGenericMotionInput(event) ?: super.onGenericMotionEvent(event)

    override fun onTouchEvent(event: MotionEvent): Boolean =
        handleTouchInput(event) ?: super.onTouchEvent(event)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (container.isEditingInCell) {
            return super.onKeyDown(keyCode, event)
        }
        if (handleSelectionKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
