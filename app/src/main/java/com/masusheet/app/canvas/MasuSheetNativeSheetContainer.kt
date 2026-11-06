package com.masusheet.app.canvas

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.masusheet.app.R
import com.masusheet.app.core.FormulaPointModeHelper
import com.masusheet.app.core.InlineCheckboxHelper
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeCellModel
import com.masusheet.app.model.NativeSheetObject
import com.masusheet.app.model.SheetObjectKind
import com.masusheet.app.ui.copyImageBytesToSystemClipboard
import com.masusheet.app.ui.copyToSystemClipboard
import com.masusheet.app.ui.lastReceivedClipboardImage
import com.masusheet.app.ui.readFromSystemClipboard
import com.masusheet.app.ui.readImageBytesFromSystemClipboard
import com.masusheet.app.ui.readImageBytesFromUri
import com.masusheet.app.viewmodel.MasuSheetViewModel
import com.masusheet.app.viewmodel.computeColumnAutoCompleteSuggestion
import com.masusheet.app.viewmodel.onCopySelectedSheetObjectsAsCombinedImage
import com.masusheet.app.viewmodel.onInsertImageBytes
import com.masusheet.app.viewmodel.onUpdateChartCategoryLabel
import com.masusheet.app.viewmodel.onUpdateChartDataValue
import com.masusheet.app.viewmodel.onUpdateChartSeriesName
import com.masusheet.app.viewmodel.onUpdateSheetObjectText
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] Native Spreadsheet Container, Standby IME Bridge, & In-Cell/In-Object Editor
 * Implements [MasuSheetNativeSheetContainer], hosting [MasuSheetNativeGridView], zero-flicker IME standby cell editing, formula point-mode spans, and custom `onLayout` bounds preserving canvas coordinates.
 *
 * ---
 * [JA / 日本語解説]
 * 2層グリッド ([MasuSheetNativeGridView])、日本語IME直接入力対応のインプレースセル／オブジェクト編集、数式参照カラーハイライト、および再レイアウト抑止 `onLayout` を統合管理する [MasuSheetNativeSheetContainer] です。
 * ============================================================================
 */

/**
 * [EN] Target element category under a right-click or long-press gesture on the sheet.
 * [JA] 右クリックまたは長押しコンテキストメニューの対象種別（セル・列見出し・行見出し・シートオブジェクト）です。
 */
enum class ContextMenuTargetType {
    CELL,
    COLUMN_HEADER,
    ROW_HEADER,
    SHEET_OBJECT
}

/**
 * [EN] Sub-element of a floating shape or chart currently being edited inline via the overlay text box.
 * [JA] インプレーステキスト編集の対象要素（図形テキスト、グラフタイトル、軸ラベル、系列名、データ値）を表す列挙型です。
 */
enum class InPlaceObjectEditTargetType {
    NONE,
    OBJECT_MAIN_TEXT,
    CHART_CATEGORY_LABEL,
    CHART_SERIES_NAME,
    CHART_DATA_VALUE
}

/**
 * [EN] Hit-test result identifying an editable label or data point inside a rendered chart object.
 * [JA] グラフ内のクリック位置に対応する編集可能ラベル・データ要素のヒットテスト結果です。
 */
data class ChartEditableHitTarget(
    val targetType: InPlaceObjectEditTargetType,
    val subIndex1: Int = 0,
    val subIndex2: Int = 0,
    val screenBounds: RectF,
    val initialText: String
)

/**
 * Container hosting:
 * 1) [MasuSheetNativeGridView]: Partial-update hardware-cached spreadsheet canvas with resizable headers.
 * 2) [inCellEditText]: Genuine Android OS [EditText] that supports:
 *    - **Direct Typing on Selected Cell**: Kept alive in standby mode (`1px × 1px`, `alpha = 0f`) while a cell
 *      is selected (green frame), so typing any character or Japanese IME input immediately begins editing the
 *      selected cell without double-clicking.
 *    - **Double-Click Smart Caret Editing**: Double-clicking a cell opens the existing text with a blinking cursor
 *      placed before `年`, `月`, `日`, or around `@`.
 *    - **Direct In-Place Shape, TextBox, & Chart Text Editing**: Double-clicking any Shape, TextBox, or Chart
 *      text element (Title, Legend/Series Name, X-Axis Category Label, or Data Value) opens [inCellEditText]
 *      directly over that exact element on the canvas!
 *    - **Zero Dark-Mode White Screen**: Never calls `View.GONE` during key dispatch, keeping `AndroidView` focus
 *      and hardware surface buffers 100% stable in Dark Mode.
 * 3) [contextMenuScrollView]: Native right-click (`BUTTON_SECONDARY`) & long-press context menu.
 */
class MasuSheetNativeSheetContainer(
    context: Context,
    internal val viewModel: MasuSheetViewModel
) : FrameLayout(context) {

    val gridView = MasuSheetNativeGridView(context, viewModel, this)
    val inCellEditText = object : EditText(context) {
        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val baseConn = super.onCreateInputConnection(outAttrs) ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                outAttrs.contentMimeTypes = arrayOf("image/png", "image/jpeg", "image/webp", "image/gif", "image/*")
                return object : InputConnectionWrapper(baseConn, false) {
                    override fun commitContent(
                        inputContentInfo: InputContentInfo,
                        flags: Int,
                        opts: Bundle?
                    ): Boolean {
                        try {
                            if ((flags and InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                                inputContentInfo.requestPermission()
                            }
                            val parsed = readImageBytesFromUri(context, inputContentInfo.contentUri)
                            if (parsed != null) {
                                lastReceivedClipboardImage = parsed
                                viewModel.onInsertImageBytes(parsed.first, parsed.second)
                                bindState(viewModel.uiState.value)
                                gridView.invalidate()
                                return true
                            }
                        } catch (_: Exception) {
                        }
                        return super.commitContent(inputContentInfo, flags, opts)
                    }
                }
            }
            return baseConn
        }

        override fun onTextContextMenuItem(id: Int): Boolean {
            if (!isEditingInCell && (id == android.R.id.paste || id == android.R.id.pasteAsPlainText)) {
                pasteFromSystemClipboardOrImage()
                bindState(viewModel.uiState.value)
                placeEditorInStandbyMode(requestEditorFocus = true)
                return true
            }
            return super.onTextContextMenuItem(id)
        }
    }
    val contextMenuScrollView = ScrollView(context)
    internal val contextMenuList = LinearLayout(context)

    var isEditingInCell: Boolean = false
        internal set
    internal var editingRow: Int = -1
    internal var editingCol: Int = -1
    var editingSheetIndex: Int = -1
        internal set
    var formulaPointSpan: IntRange? = null
        internal set
    internal var pointAnchorRow: Int = -1
    internal var pointAnchorCol: Int = -1
    internal var pointCursorRow: Int = -1
    internal var pointCursorCol: Int = -1
    var editingObjectId: String? = null
        internal set
    var editingObjectTargetType: InPlaceObjectEditTargetType = InPlaceObjectEditTargetType.NONE
        internal set
    internal var editingObjectSubIndex1: Int = 0
    internal var editingObjectSubIndex2: Int = 0
    internal var editingObjectCustomRect: RectF? = null
    internal var editingObjectInitialObjLeftPx: Float = 0f
    internal var editingObjectInitialObjTopPx: Float = 0f
    internal var activeEditSessionToken: Long = 0L
    internal var suppressStandbyTextWatcher: Boolean = false

    internal var editorLeftPx: Int = 0
    internal var editorTopPx: Int = 0
    internal var editorWidthPx: Int = 1
    internal var editorHeightPx: Int = 1

    var isContextMenuVisible: Boolean = false
        internal set
    var contextMenuTargetType: ContextMenuTargetType = ContextMenuTargetType.CELL
        internal set
    var contextMenuTargetRow: Int = 0
        internal set
    var contextMenuTargetCol: Int = 0
        internal set
    var contextMenuTargetObjectId: String? = null
        internal set
    internal var contextMenuLeftPx: Int = 0
    internal var contextMenuTopPx: Int = 0
    internal var contextMenuWidthPx: Int = 0

    var onLiveEditorTextChanged: ((String) -> Unit)? = null
    var onOpenColorPickerRequest: ((String) -> Unit)? = null
    var onOpenFunctionWizardRequest: (() -> Unit)? = null
    var onOpenMacroStudioRequest: (() -> Unit)? = null
    var onOpenCommandCenterRequest: ((String) -> Unit)? = null
    var onOpenAiAssistantWithPromptRequest: ((String) -> Unit)? = null
    var onRequestOpenImagePicker: (() -> Unit)? = null

    init {
        clipChildren = true
        clipToPadding = true
        setBackgroundColor(MasuSheetConfig.DARK_BG_COLOR_INT)

        addView(
            gridView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        inCellEditText.apply {
            // Set inputType BEFORE setSingleLine(true) so Enter key triggers EditorAction / KeyListener cleanly
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dpToPx(6f), dpToPx(2f), dpToPx(6f), dpToPx(2f))
            isFocusable = true
            isFocusableInTouchMode = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setSize(dpToPx(2.2f).coerceAtLeast(2), dpToPx(18f))
                    setColor(0xFF1F9D55.toInt())
                }
            }

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (suppressStandbyTextWatcher) return
                    val currentText = s?.toString().orEmpty()
                    if (!isEditingInCell && currentText.isNotEmpty()) {
                        // User typed directly into a selected cell (via hardware keyboard or Japanese IME) without double-clicking!
                        promoteStandbyToDirectCellEditor()
                    }
                    if (isEditingInCell && editingObjectId == null) {
                        formulaPointSpan = null
                        pointAnchorRow = -1
                        pointAnchorCol = -1
                        pointCursorRow = -1
                        pointCursorCol = -1
                        applyFormulaReferenceSpansToEditor()
                        updateInCellEditorBounds()
                        onLiveEditorTextChanged?.invoke(currentText)
                        gridView.invalidate()
                    }
                }
            })

            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_NEXT ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    if (isEditingInCell) {
                        commitAndCloseInCellEditor(moveDeltaRow = 1, moveDeltaCol = 0)
                    } else {
                        gridView.handleSelectionKeyDown(KeyEvent.KEYCODE_ENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    }
                    true
                } else {
                    false
                }
            }
        }
        addView(inCellEditText, LayoutParams(1, 1))
        placeEditorInStandbyMode(requestEditorFocus = false)

        // Setup floating right-click context menu card
        contextMenuList.orientation = LinearLayout.VERTICAL
        contextMenuScrollView.apply {
            visibility = View.GONE
            isVerticalScrollBarEnabled = true
            addView(
                contextMenuList,
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        addView(contextMenuScrollView, LayoutParams(dpToPx(244f), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /**
     * [EN] Keeps Android keyboard focus anchored inside `inCellEditText` when pressing `Enter` so focus does not jump down to the Compose sheet-tab bar.
     * [JA] `Enter` キー確定時に Android のフォーカス探索が下部シートタブバーへ逃げるのを防ぎ、セル入力エディタにフォーカスを維持します。
     */
    override fun focusSearch(focused: View?, direction: Int): View? {
        if (focused == null || focused == inCellEditText || focused == gridView || isEditingInCell) {
            return inCellEditText
        }
        return super.focusSearch(focused, direction)
    }

    internal fun pasteFromSystemClipboardOrImage() {
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

    fun currentEditorText(): String = inCellEditText.text?.toString().orEmpty()

    fun currentEditorCaret(): Int {
        val len = currentEditorText().length
        val selEnd = inCellEditText.selectionEnd
        return if (selEnd < 0) len else selEnd.coerceIn(0, len)
    }

    fun editingCellRow(): Int = editingRow
    fun editingCellCol(): Int = editingCol

    fun isEditingFormulaInCell(): Boolean {
        return isEditingInCell && editingObjectId == null && FormulaPointModeHelper.isFormulaText(currentEditorText())
    }

    fun canPointFormulaReference(): Boolean {
        return isEditingInCell &&
            editingObjectId == null &&
            FormulaPointModeHelper.canPoint(currentEditorText(), currentEditorCaret(), formulaPointSpan)
    }

    fun currentTargetSheetName(): String {
        val curSheet = gridView.currentViewport.sheetIndex
        return viewModel.uiState.value.viewport.sheetNames.getOrNull(curSheet)?.takeIf { it.isNotBlank() }
            ?: "Sheet${curSheet + 1}"
    }

    fun buildSheetQualifiedCellOrRangeRef(
        startRow: Int,
        startCol: Int,
        endRow: Int = startRow,
        endCol: Int = startCol
    ): String {
        val curSheet = gridView.currentViewport.sheetIndex
        val origSheet = if (editingSheetIndex >= 0) editingSheetIndex else curSheet
        return FormulaPointModeHelper.formatCellOrRangeRef(
            startRow = startRow,
            startCol = startCol,
            endRow = endRow,
            endCol = endCol,
            targetSheetIndex = curSheet,
            editingSheetIndex = origSheet,
            targetSheetName = currentTargetSheetName()
        )
    }

    fun buildSheetQualifiedColRangeRef(startCol: Int, endCol: Int = startCol): String {
        val curSheet = gridView.currentViewport.sheetIndex
        val origSheet = if (editingSheetIndex >= 0) editingSheetIndex else curSheet
        return FormulaPointModeHelper.formatColumnRangeRef(
            startCol = startCol,
            endCol = endCol,
            targetSheetIndex = curSheet,
            editingSheetIndex = origSheet,
            targetSheetName = currentTargetSheetName()
        )
    }

    fun buildSheetQualifiedRowRangeRef(startRow: Int, endRow: Int = startRow): String {
        val curSheet = gridView.currentViewport.sheetIndex
        val origSheet = if (editingSheetIndex >= 0) editingSheetIndex else curSheet
        return FormulaPointModeHelper.formatRowRangeRef(
            startRow = startRow,
            endRow = endRow,
            targetSheetIndex = curSheet,
            editingSheetIndex = origSheet,
            targetSheetName = currentTargetSheetName()
        )
    }

    fun insertOrUpdatePointedReference(
        newRef: String,
        appendUnion: Boolean = false,
        anchorRow: Int = -1,
        anchorCol: Int = -1,
        cursorRow: Int = anchorRow,
        cursorCol: Int = anchorCol
    ) {
        if (!isEditingInCell || editingObjectId != null) return
        val curText = currentEditorText()
        val curCaret = currentEditorCaret()
        val res = if (appendUnion) {
            FormulaPointModeHelper.appendUnionRef(curText, curCaret, newRef)
        } else {
            FormulaPointModeHelper.insertOrReplaceRef(curText, curCaret, formulaPointSpan, newRef)
        }
        formulaPointSpan = res.newPointSpan
        if (anchorRow >= 0 && anchorCol >= 0) {
            pointAnchorRow = anchorRow
            pointAnchorCol = anchorCol
            pointCursorRow = if (cursorRow >= 0) cursorRow else anchorRow
            pointCursorCol = if (cursorCol >= 0) cursorCol else anchorCol
        }
        suppressStandbyTextWatcher = true
        try {
            inCellEditText.setText(res.updatedText)
            applyFormulaReferenceSpansToEditor()
            val safeCaret = res.newCaretIndex.coerceIn(0, res.updatedText.length)
            inCellEditText.setSelection(safeCaret)
        } finally {
            suppressStandbyTextWatcher = false
        }
        updateInCellEditorBounds()
        onLiveEditorTextChanged?.invoke(res.updatedText)
        if (!inCellEditText.hasFocus()) {
            inCellEditText.requestFocus()
        }
        gridView.invalidate()
    }

    fun syncFromFormulaBar(newText: String) {
        if (editingObjectId != null) return
        if (!isEditingInCell) {
            if (newText != gridView.currentViewport.activeEditText) {
                startDirectTypingWithText(newText, requestEditorFocus = false)
            }
            return
        }
        if (newText == currentEditorText()) return
        formulaPointSpan = null
        pointAnchorRow = -1
        pointAnchorCol = -1
        pointCursorRow = -1
        pointCursorCol = -1
        suppressStandbyTextWatcher = true
        try {
            inCellEditText.setText(newText)
            applyFormulaReferenceSpansToEditor()
            inCellEditText.setSelection(newText.length)
        } finally {
            suppressStandbyTextWatcher = false
        }
        updateInCellEditorBounds()
        gridView.invalidate()
    }

    internal fun applyFormulaReferenceSpansToEditor() {
        val editable = inCellEditText.text ?: return
        val existingSpans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        for (sp in existingSpans) {
            editable.removeSpan(sp)
        }
        val raw = editable.toString()
        if (!FormulaPointModeHelper.isFormulaText(raw)) return
        val snap = gridView.currentViewport
        val maxR = (snap.rowHeights.size - 1).coerceAtLeast(0)
        val maxC = (snap.colWidths.size - 1).coerceAtLeast(0)
        val refs = FormulaPointModeHelper.extractFormulaReferences(raw, maxR, maxC)
        for (ref in refs) {
            val s = ref.textRange.first.coerceIn(0, editable.length)
            val e = (ref.textRange.last + 1).coerceIn(s, editable.length)
            if (e > s) {
                editable.setSpan(
                    ForegroundColorSpan(ref.colorArgb),
                    s,
                    e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    internal fun handleFormulaPointArrowKey(dr: Int, dc: Int, isShift: Boolean) {
        val snap = gridView.currentViewport
        val maxR = (snap.rowHeights.size - 1).coerceAtLeast(0)
        val maxC = (snap.colWidths.size - 1).coerceAtLeast(0)
        val baseR = if (editingSheetIndex == snap.sheetIndex && editingRow >= 0) editingRow else snap.activeRow
        val baseC = if (editingSheetIndex == snap.sheetIndex && editingCol >= 0) editingCol else snap.activeCol
        if (pointCursorRow < 0 || pointCursorCol < 0 || formulaPointSpan == null) {
            val startR = (baseR + dr).coerceIn(0, maxR)
            val startC = (baseC + dc).coerceIn(0, maxC)
            pointAnchorRow = startR
            pointAnchorCol = startC
            pointCursorRow = startR
            pointCursorCol = startC
        } else if (isShift) {
            pointCursorRow = (pointCursorRow + dr).coerceIn(0, maxR)
            pointCursorCol = (pointCursorCol + dc).coerceIn(0, maxC)
        } else {
            val nextR = (pointCursorRow + dr).coerceIn(0, maxR)
            val nextC = (pointCursorCol + dc).coerceIn(0, maxC)
            pointAnchorRow = nextR
            pointAnchorCol = nextC
            pointCursorRow = nextR
            pointCursorCol = nextC
        }
        val refStr = buildSheetQualifiedCellOrRangeRef(
            startRow = pointAnchorRow,
            startCol = pointAnchorCol,
            endRow = pointCursorRow,
            endCol = pointCursorCol
        )
        insertOrUpdatePointedReference(
            newRef = refStr,
            appendUnion = false,
            anchorRow = pointAnchorRow,
            anchorCol = pointAnchorCol,
            cursorRow = pointCursorRow,
            cursorCol = pointCursorCol
        )
    }

    /**
     * [EN] Intercepts hardware keyboard shortcuts (`Ctrl+C/X/V/Z/Y`) and grid navigation/commit keys (`Enter`, `Tab`, `Esc`, arrows, `Delete`, `F2`, `F4`) cleanly across `ACTION_DOWN`/`UP`.
     * [JA] ハードウェアキーボードのショートカット（`Ctrl+C/X/V/Z/Y`）およびセル移動・確定キー（`Enter`・`Tab`・矢印・`Delete`・`F2`・`F4`）を一元処理します。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isCtrl = event.isCtrlPressed || event.isMetaPressed

        if (isCtrl && !isEditingInCell) {
            when (keyCode) {
                KeyEvent.KEYCODE_C -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val selectedIds = viewModel.uiState.value.effectiveSelectedObjectIds
                        if (selectedIds.isNotEmpty()) {
                            val combined = viewModel.onCopySelectedSheetObjectsAsCombinedImage()
                            if (combined != null) {
                                copyImageBytesToSystemClipboard(context, combined.second, combined.first)
                            }
                        } else {
                            val copied = viewModel.onCopySelectionToClipboard()
                            copyToSystemClipboard(context, copied)
                        }
                        bindState(viewModel.uiState.value)
                    }
                    return true
                }
                KeyEvent.KEYCODE_X -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val cut = viewModel.onCutSelectionToClipboard()
                        copyToSystemClipboard(context, cut)
                        bindState(viewModel.uiState.value)
                    }
                    return true
                }
                KeyEvent.KEYCODE_V -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        pasteFromSystemClipboardOrImage()
                        bindState(viewModel.uiState.value)
                        placeEditorInStandbyMode(requestEditorFocus = true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_Z -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        viewModel.onExecuteEngineCommand("edit.undo", "{}")
                        bindState(viewModel.uiState.value)
                    }
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        viewModel.onExecuteEngineCommand("edit.redo", "{}")
                        bindState(viewModel.uiState.value)
                    }
                    return true
                }
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val dr = if (event.isShiftPressed) -1 else 1
                    if (isEditingInCell) {
                        commitAndCloseInCellEditor(moveDeltaRow = dr, moveDeltaCol = 0)
                    } else {
                        val snap = gridView.currentViewport
                        val merge = snap.mergeAt(snap.activeRow, snap.activeCol)
                        val nextR = if (dr > 0) (merge?.endRow ?: snap.activeRow) + 1 else (snap.activeRow - 1).coerceAtLeast(0)
                        viewModel.onSelectCell(nextR, snap.activeCol)
                        bindState(viewModel.uiState.value)
                        notifySelectedCellChanged()
                    }
                }
                // Consume BOTH ACTION_DOWN and ACTION_UP so EditText never runs focusSearch(FOCUS_DOWN)!
                return true
            }

            KeyEvent.KEYCODE_TAB -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val dc = if (event.isShiftPressed) -1 else 1
                    if (isEditingInCell) {
                        if (editingObjectId == null && !event.isShiftPressed) {
                            val suggestion = viewModel.computeColumnAutoCompleteSuggestion(
                                row = editingRow,
                                col = editingCol,
                                typedPrefix = currentEditorText()
                            )
                            if (!suggestion.isNullOrEmpty() && suggestion != currentEditorText()) {
                                suppressStandbyTextWatcher = true
                                try {
                                    inCellEditText.setText(suggestion)
                                    inCellEditText.setSelection(suggestion.length)
                                } finally {
                                    suppressStandbyTextWatcher = false
                                }
                            }
                        }
                        commitAndCloseInCellEditor(moveDeltaRow = 0, moveDeltaCol = dc)
                    } else {
                        gridView.handleSelectionKeyDown(keyCode, event)
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (isEditingInCell) {
                        cancelInCellEditor()
                    } else {
                        gridView.handleSelectionKeyDown(keyCode, event)
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_F4 -> {
                if (isEditingInCell && editingObjectId == null && isEditingFormulaInCell()) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val (cycledText, cycledCaret) = FormulaPointModeHelper.cycleAbsoluteRelativeAtCaret(
                            text = currentEditorText(),
                            caretIndex = currentEditorCaret()
                        )
                        if (cycledText != currentEditorText()) {
                            suppressStandbyTextWatcher = true
                            try {
                                inCellEditText.setText(cycledText)
                                applyFormulaReferenceSpansToEditor()
                                inCellEditText.setSelection(cycledCaret.coerceIn(0, cycledText.length))
                            } finally {
                                suppressStandbyTextWatcher = false
                            }
                            updateInCellEditorBounds()
                            onLiveEditorTextChanged?.invoke(cycledText)
                            gridView.invalidate()
                        }
                    }
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (isEditingInCell) {
                        if (canPointFormulaReference()) {
                            handleFormulaPointArrowKey(-1, 0, event.isShiftPressed)
                        } else {
                            commitAndCloseInCellEditor(moveDeltaRow = -1, moveDeltaCol = 0)
                        }
                    } else {
                        gridView.handleSelectionKeyDown(keyCode, event)
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (isEditingInCell) {
                        if (canPointFormulaReference()) {
                            handleFormulaPointArrowKey(1, 0, event.isShiftPressed)
                        } else {
                            commitAndCloseInCellEditor(moveDeltaRow = 1, moveDeltaCol = 0)
                        }
                    } else {
                        gridView.handleSelectionKeyDown(keyCode, event)
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isEditingInCell) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        gridView.handleSelectionKeyDown(keyCode, event)
                    }
                    return true
                } else if (canPointFormulaReference()) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val dc = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        handleFormulaPointArrowKey(0, dc, event.isShiftPressed)
                    }
                    return true
                }
            }

            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_F2 -> {
                if (!isEditingInCell) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        gridView.handleSelectionKeyDown(keyCode, event)
                    }
                    return true
                }
            }

            KeyEvent.KEYCODE_SPACE -> {
                if (!isEditingInCell) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val snap = gridView.currentViewport
                        if (viewModel.onToggleCellCheckbox(snap.activeRow, snap.activeCol, 0)) {
                            bindState(viewModel.uiState.value)
                            return true
                        }
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun requestLayout() {
        if (width > 0 && height > 0) {
            layoutChildrenInPlace()
            invalidate()
            return
        }
        super.requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        gridView.layout(0, 0, w, h)
        layoutChildrenInPlace()
    }

    /**
     * [EN] Measures and positions `inCellEditText` and `contextMenuScrollView` directly without `requestLayout()`, preventing `AndroidView` flicker in Dark Mode.
     * [JA] `requestLayout()` を呼ばずにセル内エディタとコンテキストメニューを直接 `measure`/`layout` 配置し、ダークモード時の画面ちらつきを防ぎます。
     */
    internal fun layoutChildrenInPlace() {
        val ew = editorWidthPx.coerceAtLeast(1)
        val eh = editorHeightPx.coerceAtLeast(1)
        inCellEditText.measure(
            MeasureSpec.makeMeasureSpec(ew, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(eh, MeasureSpec.EXACTLY)
        )
        inCellEditText.layout(editorLeftPx, editorTopPx, editorLeftPx + ew, editorTopPx + eh)

        if (isContextMenuVisible && contextMenuScrollView.visibility == View.VISIBLE) {
            val menuW = if (contextMenuWidthPx > 0) contextMenuWidthPx else dpToPx(308f)
            val maxMenuH = dpToPx(360f)
            contextMenuScrollView.measure(
                MeasureSpec.makeMeasureSpec(menuW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(maxMenuH, MeasureSpec.AT_MOST)
            )
            val mw = contextMenuScrollView.measuredWidth.coerceAtLeast(menuW)
            val mh = contextMenuScrollView.measuredHeight.coerceAtLeast(dpToPx(40f))
            val marginPx = dpToPx(4f)
            val clampedLeft = if (width > 0) {
                contextMenuLeftPx.coerceIn(marginPx, (width - mw - marginPx).coerceAtLeast(marginPx))
            } else {
                contextMenuLeftPx
            }
            val clampedTop = if (height > 0) {
                contextMenuTopPx.coerceIn(marginPx, (height - mh - marginPx).coerceAtLeast(marginPx))
            } else {
                contextMenuTopPx
            }
            contextMenuScrollView.layout(
                clampedLeft,
                clampedTop,
                clampedLeft + mw,
                clampedTop + mh
            )
        }
    }

    fun bindState(state: MasuSheetUiState) {
        val sheetBg = if (state.isDarkTheme) 0xFF14181D.toInt() else 0xFFFFFFFF.toInt()
        setBackgroundColor(sheetBg)
        gridView.setBackgroundColor(sheetBg)
        gridView.bindState(state)
        if (isEditingInCell) {
            styleVisibleEditorForTheme(state.isDarkTheme)
            updateInCellEditorBounds()
        }
    }

    internal fun styleVisibleEditorForTheme(isDark: Boolean) {
        val editorBg = if (isDark) 0xEE0F172A.toInt() else 0xF5FFFFFF.toInt()
        val editorFg = if (isDark) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
        val borderColor = if (editingObjectId != null) 0xFF2563EB.toInt() else 0xFF1F9D55.toInt()
        inCellEditText.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(editorBg)
            setStroke(dpToPx(2.2f).coerceAtLeast(2), borderColor)
            cornerRadius = dpToPx(4f).toFloat()
        }
        inCellEditText.setTextColor(editorFg)
    }

    internal fun placeEditorInStandbyMode(requestEditorFocus: Boolean = true) {
        activeEditSessionToken++
        isEditingInCell = false
        editingRow = -1
        editingCol = -1
        editingSheetIndex = -1
        formulaPointSpan = null
        pointAnchorRow = -1
        pointAnchorCol = -1
        pointCursorRow = -1
        pointCursorCol = -1
        editingObjectId = null
        editingObjectTargetType = InPlaceObjectEditTargetType.NONE
        editingObjectSubIndex1 = 0
        editingObjectSubIndex2 = 0
        editingObjectCustomRect = null
        editingObjectInitialObjLeftPx = 0f
        editingObjectInitialObjTopPx = 0f
        inCellEditText.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        suppressStandbyTextWatcher = true
        try {
            if (inCellEditText.text.isNotEmpty()) {
                inCellEditText.setText("")
            }
        } finally {
            suppressStandbyTextWatcher = false
        }
        inCellEditText.alpha = 0f
        inCellEditText.background = null
        editorLeftPx = 0
        editorTopPx = 0
        editorWidthPx = 1
        editorHeightPx = 1
        inCellEditText.visibility = View.VISIBLE
        layoutChildrenInPlace()
        if (requestEditorFocus && !inCellEditText.hasFocus()) {
            inCellEditText.requestFocus()
        }
    }

    /**
     * [EN] Promotes `inCellEditText` from its 1×1 invisible standby state into a visible active-cell editor while preserving newly typed characters and IME composition.
     * [JA] 待機中の1×1不可視エディタを入力開始と同時に選択セル上の可視エディタへ昇格させ、最初の打鍵や日本語IME変換状態を欠落なく引き継ぎます。
     */
    internal fun promoteStandbyToDirectCellEditor() {
        val snap = gridView.currentViewport
        val (anchorR, anchorC) = snap.resolveAnchorCell(snap.activeRow, snap.activeCol)
        editingRow = anchorR
        editingCol = anchorC
        editingSheetIndex = snap.sheetIndex
        formulaPointSpan = null
        pointAnchorRow = -1
        pointAnchorCol = -1
        pointCursorRow = -1
        pointCursorCol = -1
        editingObjectId = null
        editingObjectTargetType = InPlaceObjectEditTargetType.NONE
        isEditingInCell = true
        inCellEditText.gravity = Gravity.CENTER_VERTICAL or Gravity.START

        styleVisibleEditorForTheme(gridView.isDarkTheme)
        val cell = snap.cellAt(anchorR, anchorC)
        val scaledFontSp = ((cell?.fontSize ?: 11f) * gridView.zoomScale).coerceIn(8f, 48f)
        inCellEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledFontSp)

        val cellScreenRect = gridView.getCellScreenRect(anchorR, anchorC)
        updateInCellEditorBounds(cellScreenRect)
        inCellEditText.alpha = 1f
        inCellEditText.visibility = View.VISIBLE
        inCellEditText.bringToFront()
        inCellEditText.requestFocus()
        gridView.invalidate()
    }

    /**
     * [EN] Opens the in-cell editor on the active cell (resolving merge anchors) initialized with `initialTypedText`.
     * [JA] 結合セルのアンカーを解決した上でアクティブセル上にセル内エディタを開き、`initialTypedText` を初期入力としてセットします。
     */
    fun startDirectTypingWithText(initialTypedText: String, requestEditorFocus: Boolean = true) {
        if (isContextMenuVisible) dismissContextMenu()
        val snap = gridView.currentViewport
        val (anchorR, anchorC) = snap.resolveAnchorCell(snap.activeRow, snap.activeCol)
        if (isEditingInCell && (editingRow != anchorR || editingCol != anchorC || editingObjectId != null)) {
            commitAndCloseInCellEditor(0, 0)
        }
        editingRow = anchorR
        editingCol = anchorC
        editingSheetIndex = snap.sheetIndex
        formulaPointSpan = null
        pointAnchorRow = -1
        pointAnchorCol = -1
        pointCursorRow = -1
        pointCursorCol = -1
        editingObjectId = null
        editingObjectTargetType = InPlaceObjectEditTargetType.NONE
        isEditingInCell = true
        inCellEditText.gravity = Gravity.CENTER_VERTICAL or Gravity.START

        styleVisibleEditorForTheme(gridView.isDarkTheme)
        val cell = snap.cellAt(anchorR, anchorC)
        val scaledFontSp = ((cell?.fontSize ?: 11f) * gridView.zoomScale).coerceIn(8f, 48f)
        inCellEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledFontSp)

        val cellScreenRect = gridView.getCellScreenRect(anchorR, anchorC)
        updateInCellEditorBounds(cellScreenRect)

        suppressStandbyTextWatcher = true
        try {
            inCellEditText.setText(initialTypedText)
            applyFormulaReferenceSpansToEditor()
            inCellEditText.setSelection(initialTypedText.length)
        } finally {
            suppressStandbyTextWatcher = false
        }
        onLiveEditorTextChanged?.invoke(initialTypedText)
        inCellEditText.alpha = 1f
        inCellEditText.visibility = View.VISIBLE
        inCellEditText.bringToFront()
        if (requestEditorFocus) {
            inCellEditText.requestFocus()
        }
        gridView.invalidate()
    }

    /**
     * [EN] Opens `inCellEditText` directly over a double-tapped shape, text box, or chart text element for seamless in-place editing.
     * [JA] ダブルタップされた図形・テキストボックス・グラフ内ラベルの画面座標上に直接エディタを重ねてインプレース編集を開始します。
     */
    fun openInObjectEditor(
        obj: NativeSheetObject,
        targetType: InPlaceObjectEditTargetType = InPlaceObjectEditTargetType.OBJECT_MAIN_TEXT,
        subIndex1: Int = 0,
        subIndex2: Int = 0,
        targetScreenRect: RectF? = null,
        initialText: String? = null
    ) {
        if (isContextMenuVisible) dismissContextMenu()
        if (isEditingInCell) {
            commitAndCloseInCellEditor(0, 0)
        }

        val resolvedText = initialText ?: obj.title
        val fullObjRect = gridView.getSheetObjectScreenRect(obj)
        val editorRect = when {
            targetScreenRect != null -> targetScreenRect
            obj.kind == SheetObjectKind.CHART -> {
                // Position directly over the chart's top title bar
                RectF(
                    fullObjRect.left + dpToPx(6f),
                    fullObjRect.top + dpToPx(4f),
                    (fullObjRect.right - dpToPx(6f)).coerceAtLeast(fullObjRect.left + dpToPx(96f)),
                    fullObjRect.top + dpToPx(32f)
                )
            }
            obj.kind == SheetObjectKind.SHAPE -> {
                // Position centered inside the shape
                val boxH = min(fullObjRect.height(), dpToPx(36f).toFloat()).coerceAtLeast(dpToPx(28f).toFloat())
                val cy = fullObjRect.centerY()
                RectF(
                    fullObjRect.left + dpToPx(6f),
                    cy - boxH * 0.5f,
                    (fullObjRect.right - dpToPx(6f)).coerceAtLeast(fullObjRect.left + dpToPx(72f)),
                    cy + boxH * 0.5f
                )
            }
            else -> fullObjRect
        }

        editingRow = -1
        editingCol = -1
        editingSheetIndex = -1
        formulaPointSpan = null
        editingObjectId = obj.id
        editingObjectTargetType = targetType
        editingObjectSubIndex1 = subIndex1
        editingObjectSubIndex2 = subIndex2
        editingObjectCustomRect = RectF(editorRect)
        editingObjectInitialObjLeftPx = fullObjRect.left
        editingObjectInitialObjTopPx = fullObjRect.top
        val sessionToken = ++activeEditSessionToken
        isEditingInCell = true

        inCellEditText.gravity = if (obj.kind == SheetObjectKind.SHAPE) {
            Gravity.CENTER
        } else {
            Gravity.CENTER_VERTICAL or Gravity.START
        }

        styleVisibleEditorForTheme(gridView.isDarkTheme)
        val scaledFontSp = (12f * gridView.zoomScale).coerceIn(11f, 20f)
        inCellEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledFontSp)
        updateInCellEditorBounds(editorRect)

        suppressStandbyTextWatcher = true
        try {
            inCellEditText.setText(resolvedText)
            inCellEditText.setSelection(resolvedText.length)
        } finally {
            suppressStandbyTextWatcher = false
        }
        inCellEditText.alpha = 1f
        inCellEditText.visibility = View.VISIBLE
        inCellEditText.bringToFront()
        inCellEditText.requestFocus()
        inCellEditText.post {
            if (isEditingInCell && activeEditSessionToken == sessionToken && editingObjectId == obj.id) {
                inCellEditText.requestFocus()
                if (inCellEditText.text?.toString() == resolvedText) {
                    inCellEditText.setSelection(inCellEditText.text?.length ?: 0)
                }
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(inCellEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        gridView.invalidate()
    }

    fun openInObjectEditorForSelected(obj: NativeSheetObject) {
        openInObjectEditor(
            obj = obj,
            targetType = InPlaceObjectEditTargetType.OBJECT_MAIN_TEXT,
            initialText = obj.title
        )
    }

    fun openInCellEditor(
        row: Int,
        col: Int,
        clickScreenX: Float?,
        clickScreenY: Float?
    ) {
        if (isContextMenuVisible) dismissContextMenu()
        val snap = gridView.currentViewport
        val (anchorR, anchorC) = snap.resolveAnchorCell(row, col)
        if (isEditingInCell && (editingRow != anchorR || editingCol != anchorC || editingObjectId != null)) {
            commitAndCloseInCellEditor(0, 0)
        }

        val cell = snap.cellAt(anchorR, anchorC)
        val initialText = cell?.editText ?: cell?.displayText.orEmpty()
        editingRow = anchorR
        editingCol = anchorC
        editingSheetIndex = snap.sheetIndex
        formulaPointSpan = null
        pointAnchorRow = -1
        pointAnchorCol = -1
        pointCursorRow = -1
        pointCursorCol = -1
        editingObjectId = null
        editingObjectTargetType = InPlaceObjectEditTargetType.NONE
        editingObjectCustomRect = null
        editingObjectInitialObjLeftPx = 0f
        editingObjectInitialObjTopPx = 0f
        val sessionToken = ++activeEditSessionToken
        isEditingInCell = true
        inCellEditText.gravity = Gravity.CENTER_VERTICAL or Gravity.START

        styleVisibleEditorForTheme(gridView.isDarkTheme)
        val scaledFontSp = ((cell?.fontSize ?: 11f) * gridView.zoomScale).coerceIn(8f, 48f)
        inCellEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledFontSp)

        val cellScreenRect = gridView.getCellScreenRect(anchorR, anchorC)
        updateInCellEditorBounds(cellScreenRect)

        suppressStandbyTextWatcher = true
        try {
            inCellEditText.setText(initialText)
            applyFormulaReferenceSpansToEditor()
        } finally {
            suppressStandbyTextWatcher = false
        }
        onLiveEditorTextChanged?.invoke(initialText)
        inCellEditText.alpha = 1f
        inCellEditText.visibility = View.VISIBLE
        inCellEditText.bringToFront()
        inCellEditText.requestFocus()

        val caretIdx = computeSmartCaretFromClick(
            text = initialText,
            cell = cell,
            cellScreenRect = cellScreenRect,
            clickScreenX = clickScreenX
        )
        val safeCaret = caretIdx.coerceIn(0, initialText.length)
        inCellEditText.setSelection(safeCaret)
        inCellEditText.post {
            if (isEditingInCell && activeEditSessionToken == sessionToken) {
                inCellEditText.requestFocus()
                if (inCellEditText.text?.toString() == initialText) {
                    inCellEditText.setSelection(safeCaret.coerceIn(0, inCellEditText.text?.length ?: 0))
                }
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(inCellEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        gridView.invalidate()
    }

    fun computeSmartCaretFromClick(
        text: String,
        cell: NativeCellModel?,
        cellScreenRect: RectF,
        clickScreenX: Float?
    ): Int {
        if (text.isEmpty()) return 0
        if (clickScreenX == null) {
            return InlineCheckboxHelper.resolveSmartCaretIndex(text, text.length)
        }
        val paint = inCellEditText.paint
        val textWidth = measureSubTextWidth(paint, text, 0, text.length)
        val padPx = dpToPx(6f).toFloat()
        val cellW = cellScreenRect.width()
        val hAlign = cell?.hAlign ?: 0
        val textStartScreenX = when (hAlign) {
            1 -> cellScreenRect.left + ((cellW - textWidth) * 0.5f).coerceAtLeast(padPx)
            2 -> (cellScreenRect.right - padPx - textWidth).coerceAtLeast(cellScreenRect.left + padPx)
            else -> cellScreenRect.left + padPx
        }
        val relX = (clickScreenX - textStartScreenX).coerceAtLeast(0f)

        val dateUnits = setOf('年', '月', '日', '時', '分')
        val unitPositions = mutableListOf<Int>()
        for (i in text.indices) {
            if (text[i] in dateUnits) {
                unitPositions.add(i)
            }
        }
        if (unitPositions.isNotEmpty()) {
            for (uIdx in unitPositions) {
                val uRight = measureSubTextWidth(paint, text, 0, uIdx + 1)
                if (relX <= uRight + dpToPx(4f)) {
                    val approx = measureCharIndexAtAdvance(paint, text, relX)
                    if (approx < uIdx && !text[approx].isWhitespace() && text[approx] !in dateUnits) {
                        val prefixEnd = (approx until uIdx).firstOrNull { text[it].isWhitespace() } ?: uIdx
                        val prefixRight = measureSubTextWidth(paint, text, 0, prefixEnd)
                        if (relX >= prefixRight - dpToPx(2f)) {
                            return uIdx
                        }
                        return approx
                    }
                    return uIdx
                }
            }
            val lastUnit = unitPositions.last()
            if (lastUnit == text.length - 1) {
                return lastUnit
            }
        }

        val atIdx = text.indexOfFirst { it == '@' || it == '＠' }
        if (atIdx >= 0) {
            val atLeft = measureSubTextWidth(paint, text, 0, atIdx)
            val atRight = measureSubTextWidth(paint, text, 0, atIdx + 1)
            val onlyAtAndSpaces = text.all { it.isWhitespace() || it == '@' || it == '＠' }
            if (onlyAtAndSpaces) {
                return if (relX <= atRight + dpToPx(2f)) atIdx else (atIdx + 1).coerceAtMost(text.length)
            }
            if (relX >= atLeft - dpToPx(4f) && relX <= atRight + dpToPx(2f)) {
                return atIdx
            }
        }

        val approxIdx = measureCharIndexAtAdvance(paint, text, relX)
        return InlineCheckboxHelper.resolveSmartCaretIndex(text, approxIdx)
    }

    internal fun measureSubTextWidth(paint: Paint, text: String, start: Int, end: Int): Float {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        if (s >= e) return 0f
        if (paint.measureText("M") >= 4.0f) {
            val nativeW = paint.measureText(text, s, e)
            if (nativeW > 0.5f) return nativeW
        }
        var fallback = 0f
        for (i in s until e) {
            fallback += if (text[i].code > 0x7F) 14f else 7.5f
        }
        return fallback
    }

    internal fun measureCharIndexAtAdvance(paint: Paint, text: String, advancePx: Float): Int {
        if (text.isEmpty() || advancePx <= 0f) return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && paint.measureText("M") >= 4.0f) {
            return paint.getOffsetForAdvance(text, 0, text.length, 0, text.length, false, advancePx)
                .coerceIn(0, text.length)
        }
        var bestIdx = text.length
        for (i in 0..text.length) {
            val w = measureSubTextWidth(paint, text, 0, i)
            if (w >= advancePx) {
                bestIdx = i
                break
            }
        }
        return bestIdx
    }

    fun updateInCellEditorBounds(precomputedRect: RectF? = null) {
        if (!isEditingInCell) return
        if (editingObjectId == null && editingSheetIndex >= 0 && editingSheetIndex != gridView.currentViewport.sheetIndex) {
            // While pointing on another sheet in cross-sheet Formula Point Mode, hide the cell box visually on the other sheet
            inCellEditText.alpha = 0f
            editorLeftPx = 0
            editorTopPx = 0
            editorWidthPx = 1
            editorHeightPx = 1
            layoutChildrenInPlace()
            return
        }
        inCellEditText.alpha = 1f
        val rect = precomputedRect
            ?: editingObjectId?.let { id ->
                val curObjRect = gridView.sheetObjects.firstOrNull { it.id == id }?.let { gridView.getSheetObjectScreenRect(it) }
                val baseCustom = editingObjectCustomRect
                if (curObjRect != null && baseCustom != null) {
                    val shiftX = curObjRect.left - editingObjectInitialObjLeftPx
                    val shiftY = curObjRect.top - editingObjectInitialObjTopPx
                    RectF(
                        baseCustom.left + shiftX,
                        baseCustom.top + shiftY,
                        baseCustom.right + shiftX,
                        baseCustom.bottom + shiftY
                    )
                } else {
                    curObjRect ?: baseCustom
                }
            }
            ?: gridView.getCellScreenRect(editingRow.coerceAtLeast(0), editingCol.coerceAtLeast(0))
        val minW = dpToPx(84f)
        val minH = dpToPx(26f)
        val textW = (measureSubTextWidth(inCellEditText.paint, currentEditorText(), 0, currentEditorText().length).roundToInt() + dpToPx(20f))
        editorLeftPx = rect.left.roundToInt()
        editorTopPx = rect.top.roundToInt()
        editorWidthPx = max(max(rect.width().roundToInt(), minW), textW.coerceAtMost(max(width - editorLeftPx - dpToPx(8f), minW)))
        editorHeightPx = max(rect.height().roundToInt(), minH)
        layoutChildrenInPlace()
    }

    fun commitAndCloseInCellEditor(moveDeltaRow: Int = 0, moveDeltaCol: Int = 0) {
        if (!isEditingInCell) return
        val activeObjId = editingObjectId
        val activeObjTarget = editingObjectTargetType
        val subIdx1 = editingObjectSubIndex1
        val subIdx2 = editingObjectSubIndex2
        val r = editingRow
        val c = editingCol
        val targetSheet = editingSheetIndex.takeIf { it >= 0 }
        val newText = inCellEditText.text?.toString().orEmpty()
        // Return editor to 1x1 invisible standby mode WITHOUT View.GONE or requestLayout() so dark-mode surface never blanks out!
        placeEditorInStandbyMode(requestEditorFocus = true)
        if (activeObjId != null) {
            when (activeObjTarget) {
                InPlaceObjectEditTargetType.CHART_CATEGORY_LABEL -> {
                    viewModel.onUpdateChartCategoryLabel(activeObjId, subIdx1, newText)
                }
                InPlaceObjectEditTargetType.CHART_SERIES_NAME -> {
                    viewModel.onUpdateChartSeriesName(activeObjId, subIdx1, newText)
                }
                InPlaceObjectEditTargetType.CHART_DATA_VALUE -> {
                    viewModel.onUpdateChartDataValue(activeObjId, subIdx1, subIdx2, newText)
                }
                else -> {
                    viewModel.onUpdateSheetObjectText(activeObjId, newText)
                }
            }
        } else if (r >= 0 && c >= 0) {
            viewModel.onCommitCellEdit(
                row = r,
                col = c,
                newText = newText,
                moveDeltaRow = moveDeltaRow,
                moveDeltaCol = moveDeltaCol,
                targetSheetIndex = targetSheet
            )
            if (targetSheet != null && targetSheet != viewModel.uiState.value.viewport.sheetIndex) {
                viewModel.onSelectSheet(targetSheet)
            }
        }
        // Synchronously bind updated state so the newly entered text renders immediately without clicking anything else!
        bindState(viewModel.uiState.value)
        onLiveEditorTextChanged?.invoke(viewModel.uiState.value.viewport.activeEditText)
        gridView.invalidate()
    }

    fun cancelInCellEditor() {
        if (!isEditingInCell) return
        val targetSheet = editingSheetIndex.takeIf { it >= 0 }
        placeEditorInStandbyMode(requestEditorFocus = true)
        if (targetSheet != null && targetSheet != viewModel.uiState.value.viewport.sheetIndex) {
            viewModel.onSelectSheet(targetSheet)
        }
        bindState(viewModel.uiState.value)
        onLiveEditorTextChanged?.invoke(viewModel.uiState.value.viewport.activeEditText)
        gridView.invalidate()
    }

    fun notifySelectedCellChanged() {
        if (!isEditingInCell) {
            placeEditorInStandbyMode(requestEditorFocus = true)
        }
    }

    fun dismissContextMenu() {
        if (!isContextMenuVisible) return
        isContextMenuVisible = false
        contextMenuScrollView.visibility = View.GONE
        invalidate()
    }

    internal fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).roundToInt()
}
