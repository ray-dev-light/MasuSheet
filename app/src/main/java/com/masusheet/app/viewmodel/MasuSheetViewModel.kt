package com.masusheet.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.masusheet.app.MainActivity
import com.masusheet.app.ai.MacroStep
import com.masusheet.app.bridge.NativeEngineGateway
import com.masusheet.app.bridge.RustJniEngineGateway
import com.masusheet.app.core.FormulaPointModeHelper
import com.masusheet.app.core.InlineCheckboxHelper
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.CustomCellStyleOverride
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeCellModel
import com.masusheet.app.model.NativeMergeRange
import com.masusheet.app.model.NativeSheetObject
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.ContentResolverDocumentGatewayFactory
import com.masusheet.app.storage.DocumentIoGateway
import com.masusheet.app.storage.MasuSheetPreferencesRepository
import com.masusheet.app.storage.SharedPreferencesStoreFactory
import com.masusheet.app.ui.MasuSheetScreen
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] MasuSheet Core ViewModel: StateFlow, Unified Per-Workbook Undo/Redo, Selection, & Clipboard
 * Coordinates [MasuSheetViewModel] state, synchronized Kotlin+Rust Undo/Redo snapshots (`C-10`), custom column/row/style overlays, cell/range selection, AutoFit, and Paste Special (`H-13`).
 *
 * ---
 * [JA / 日本語解説]
 * 画面全体の状態管理 ([MasuSheetViewModel])、ブック別統合Undo/Redoスタック (`C-10`)、カスタム列幅・行高・書式オーバーレイ、セル選択、自動調整、形式を選択して貼り付け (`H-13`) を担う中核クラスです。
 * ============================================================================
 */

/**
 * [EN] Primary spreadsheet `ViewModel` managing workbook sessions, synchronized Kotlin+Rust Undo/Redo, sheet objects, pivots, and UI state.
 * [JA] ワークブック管理、Kotlin＋Rust統合Undo/Redo、グラフ・図形・画像、ピボットテーブル、AI編集を統括するメイン `ViewModel` です。
 */
class MasuSheetViewModel(
    internal val preferencesRepository: MasuSheetPreferencesRepository,
    internal val documentGateway: DocumentIoGateway,
    internal val engineGateway: NativeEngineGateway = RustJniEngineGateway(),
    internal val startWithBlankWorkbook: Boolean = false
) : ViewModel() {

    /**
     * [EN] Snapshot of Kotlin-side overlays, floating sheet objects (charts, shapes, images),
     *      and the number of native engine steps (`engineStepsCount`) associated with a single user operation.
     * [JA] 1回のユーザー操作に対応するKotlin側のカスタム列幅・行高・書式・結合・シート上オブジェクト（グラフ・図形・画像）
     *      およびネイティブエンジンのステップ数（`engineStepsCount`）を保持するUndo/Redoスナップショット。
     */
    internal data class CustomOverlayUndoSnapshot(
        val colWidths: Map<Pair<Int, Int>, Float>,
        val rowHeights: Map<Pair<Int, Int>, Float>,
        val wrapCells: Map<Pair<Int, Long>, Boolean>,
        val cellStyles: Map<Pair<Int, Long>, CustomCellStyleOverride>,
        val sheetMerges: Map<Int, List<NativeMergeRange>>,
        val sheetObjects: List<NativeSheetObject> = emptyList(),
        val selectedObjectId: String? = null,
        val selectedObjectIds: Set<String> = emptySet(),
        val engineStepsCount: Int = 1
    )

    internal val customColWidths = mutableMapOf<Pair<Int, Int>, Float>()
    internal val customRowHeights = mutableMapOf<Pair<Int, Int>, Float>()
    internal val customWrapCells = mutableMapOf<Pair<Int, Long>, Boolean>()
    internal val customCellStyles = mutableMapOf<Pair<Int, Long>, CustomCellStyleOverride>()
    internal val customSheetMerges = mutableMapOf<Int, List<NativeMergeRange>>()
    internal val workbookUndoStacks = mutableMapOf<String, ArrayDeque<CustomOverlayUndoSnapshot>>()
    internal val workbookRedoStacks = mutableMapOf<String, ArrayDeque<CustomOverlayUndoSnapshot>>()
    internal val customOverlayUndoStack: ArrayDeque<CustomOverlayUndoSnapshot>
        get() = workbookUndoStacks.getOrPut(currentWorkbookIdForStacks) { ArrayDeque() }
    internal val customOverlayRedoStack: ArrayDeque<CustomOverlayUndoSnapshot>
        get() = workbookRedoStacks.getOrPut(currentWorkbookIdForStacks) { ArrayDeque() }
    internal var currentWorkbookIdForStacks: String = "book_1"
    internal var currentActiveSourceUri: String = ""
    internal var sizeRevisionBump: Long = 0L
    internal var nextObjectCounter: Int = 1
    internal var nextWorkbookCounter: Int = 2

    internal val _uiState = MutableStateFlow(
        run {
            val initSid = engineGateway.createSession(if (startWithBlankWorkbook) "blank" else "sales")
            val initialSnap = if (startWithBlankWorkbook) {
                engineGateway.executeCommand("file.new", "{\"title\":\"Book1.xlsx\"}")
                engineGateway.executeCommand("file.markSaved", "{\"title\":\"Book1.xlsx\"}").copy(title = "Book1.xlsx", isDirty = false, sessionId = initSid)
            } else {
                engineGateway.initSample("sales").copy(sessionId = initSid)
            }
            val initTitle = initialSnap.title.ifBlank { if (startWithBlankWorkbook) "Book1.xlsx" else "Sales.xlsx" }
            MasuSheetUiState(
                workbookTitle = initTitle,
                activeSample = if (startWithBlankWorkbook) "blank" else "sales",
                definedNames = emptyMap(),
                saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(initTitle),
                saveDialogExtension = MasuSheetConfig.extractExtensionOrDefault(initTitle, "xlsx"),
                language = preferencesRepository.getLanguage(),
                isDarkTheme = preferencesRepository.isDarkTheme(),
                isMaterial3Ui = preferencesRepository.isMaterial3Ui(),
                zoomPercent = preferencesRepository.getZoomPercent(),
                recentWorkbooks = preferencesRepository.getRecentWorkbooks(),
                recentColors = preferencesRepository.getRecentColors(),
                selectedAiModelId = preferencesRepository.getAiModelId(),
                selectedAiEditMode = preferencesRepository.getAiEditMode(),
                viewport = initialSnap
            )
        }
    )
    val uiState: StateFlow<MasuSheetUiState> = _uiState.asStateFlow()

    internal fun captureCustomOverlaySnapshot(engineStepsCount: Int = 1): CustomOverlayUndoSnapshot =
        CustomOverlayUndoSnapshot(
            colWidths = HashMap(customColWidths),
            rowHeights = HashMap(customRowHeights),
            wrapCells = HashMap(customWrapCells),
            cellStyles = HashMap(customCellStyles),
            sheetMerges = customSheetMerges.mapValues { it.value.toList() },
            sheetObjects = _uiState.value.sheetObjects.toList(),
            selectedObjectId = _uiState.value.selectedObjectId,
            selectedObjectIds = _uiState.value.effectiveSelectedObjectIds.toSet(),
            engineStepsCount = engineStepsCount.coerceAtLeast(0)
        )

    internal fun pushCustomOverlayUndoSnapshot(snapshot: CustomOverlayUndoSnapshot = captureCustomOverlaySnapshot()) {
        customOverlayUndoStack.addLast(snapshot)
        if (customOverlayUndoStack.size > 60) {
            customOverlayUndoStack.removeFirst()
        }
        customOverlayRedoStack.clear()
    }

    internal fun restoreCustomOverlaySnapshot(snapshot: CustomOverlayUndoSnapshot) {
        customColWidths.clear()
        customColWidths.putAll(snapshot.colWidths)
        customRowHeights.clear()
        customRowHeights.putAll(snapshot.rowHeights)
        customWrapCells.clear()
        customWrapCells.putAll(snapshot.wrapCells)
        customCellStyles.clear()
        customCellStyles.putAll(snapshot.cellStyles)
        customSheetMerges.clear()
        customSheetMerges.putAll(snapshot.sheetMerges)
        sizeRevisionBump++
        val cur = _uiState.value
        val validIds = snapshot.sheetObjects.map { it.id }.toSet()
        val validSelectedId = snapshot.selectedObjectId?.takeIf { it in validIds }
        val validSelectedIds = snapshot.selectedObjectIds.intersect(validIds)
        _uiState.value = cur.copy(
            sheetObjects = snapshot.sheetObjects,
            selectedObjectId = validSelectedId,
            selectedObjectIds = validSelectedIds
        )
    }

    init {
        if (!startWithBlankWorkbook) {
            // Add the Windows 11 Spreadsheet "月間予算" sheet tab as Sheet 1 alongside "Sales" (Sheet 0)
            setupInitialMonthlyBudgetTab()
        }
        saveCurrentWorkbookToSessionList()
    }

    internal var scriptDispatcher: ((String) -> Unit)? = null

    /**
     * [EN] Optional UI callback registered by [MasuSheetScreen] so external OS intents
     *      (e.g., Shelf/Taskbar shortcuts in `MainActivity.onNewIntent`) commit any active in-cell
     *      or formula-bar edit before switching workbook sessions or opening a secondary window.
     * [JA] シェルフのショートカット等の外部 Intent (`onNewIntent`) でブック切替や別ウィンドウ起動を行う直前に、
     *      セル内エディタや数式バーで入力途中の未確定テキストを自動確定するためのフックです。
     */
    internal var beforeExternalNavigationCommitHook: (() -> Unit)? = null

    /**
     * [EN] Flushes any uncommitted UI cell/formula edit prior to external shortcut or document navigation.
     * [JA] 外部ショートカットやファイル読込による画面遷移前に、入力途中のセル編集を確定します。
     */
    fun flushPendingUiEditsBeforeNavigation() {
        beforeExternalNavigationCommitHook?.invoke()
    }

    fun setScriptDispatcher(dispatcher: ((String) -> Unit)?) {
        scriptDispatcher = dispatcher
    }

    internal fun setupInitialMonthlyBudgetTab() {
        val curNames = _uiState.value.viewport.sheetNames
        if (curNames.size == 1 && curNames.firstOrNull() == "Sales") {
            engineGateway.executeCommand("home.insertSheet", "{\"before\":1}")
            engineGateway.executeCommand("sheet.rename", "{\"sheet\":1,\"name\":\"月間予算\"}")
            populateMonthlyBudgetSheet(sheetIndex = 1, attachChartsImmediately = false)
            engineGateway.executeCommand("sheet.activate", "{\"sheet\":0}")
            val salesSel = engineGateway.executeCommand("selection.set", "{\"active\":\"A1\",\"range\":\"A1\"}")
            customOverlayUndoStack.clear()
            customOverlayRedoStack.clear()
            val decorated = decorateWithCustomSizes(salesSel.copy(isDirty = false, canUndo = false, canRedo = false))
            _uiState.value = _uiState.value.copy(
                isDirty = false,
                activePivotTableId = null,
                viewport = decorated
            )
        }
    }

    internal var activeBorderColorArgb: Int = 0
    internal var lastNudgeUndoTimestampMs: Long = 0L
    internal val materializedSampleChartSheetKeys = mutableSetOf<String>()

    internal fun hasMaterializedSalesDashboardCharts(sheetIndex: Int): Boolean {
        return "${_uiState.value.activeWorkbookId}:sales_dashboard:$sheetIndex" in materializedSampleChartSheetKeys
    }

    internal fun decorateWithCustomSizes(snap: NativeViewportSnapshot): NativeViewportSnapshot {
        val sIdx = snap.sheetIndex
        val hasColOverrides = customColWidths.keys.any { it.first == sIdx }
        val hasRowOverrides = customRowHeights.keys.any { it.first == sIdx }
        val hasWrapOverrides = customWrapCells.keys.any { it.first == sIdx }
        val hasStyleOverrides = customCellStyles.keys.any { it.first == sIdx }
        val extraMerges = customSheetMerges[sIdx]
        val effectiveCanUndo = snap.canUndo || customOverlayUndoStack.isNotEmpty()
        val effectiveCanRedo = snap.canRedo || customOverlayRedoStack.isNotEmpty()
        if (!hasColOverrides && !hasRowOverrides && !hasWrapOverrides && !hasStyleOverrides && extraMerges == null && sizeRevisionBump == 0L) {
            return if (snap.canUndo == effectiveCanUndo && snap.canRedo == effectiveCanRedo) {
                snap
            } else {
                snap.copy(canUndo = effectiveCanUndo, canRedo = effectiveCanRedo)
            }
        }
        val cw = if (hasColOverrides) {
            snap.colWidths.copyOf().also { arr ->
                for ((key, wPt) in customColWidths) {
                    if (key.first == sIdx && key.second in arr.indices) {
                        arr[key.second] = wPt
                    }
                }
            }
        } else {
            snap.colWidths
        }
        val hiddenRowSet = snap.hiddenRows.toHashSet()
        val rh = snap.rowHeights.copyOf().also { arr ->
            for (r in arr.indices) {
                if (arr[r] < 1f && r !in hiddenRowSet && (sIdx to r) !in customRowHeights) {
                    arr[r] = 22f
                }
            }
            for ((key, hPt) in customRowHeights) {
                if (key.first == sIdx && key.second in arr.indices) {
                    arr[key.second] = hPt
                }
            }
        }
        val updatedCells = if (hasWrapOverrides || hasStyleOverrides) {
            HashMap(snap.cells).also { map ->
                for ((key, wrapOn) in customWrapCells) {
                    if (key.first == sIdx) {
                        val existing = map[key.second]
                        if (existing != null) {
                            val newFlags = if (wrapOn) (existing.flags or 16) else (existing.flags and 16.inv())
                            map[key.second] = existing.copy(flags = newFlags)
                        }
                    }
                }
                for ((key, st) in customCellStyles) {
                    if (key.first == sIdx) {
                        val r = (key.second ushr 32).toInt()
                        val c = key.second.toInt()
                        val existing = map[key.second] ?: NativeCellModel(row = r, col = c, displayText = "", editText = "")
                        var fl = existing.flags
                        if (st.isBold != null) fl = if (st.isBold) (fl or 1) else (fl and 1.inv())
                        if (st.isItalic != null) fl = if (st.isItalic) (fl or 2) else (fl and 2.inv())
                        if (st.isUnderline != null) fl = if (st.isUnderline) (fl or 4) else (fl and 4.inv())
                        if (st.isStrike != null) fl = if (st.isStrike) (fl or 8) else (fl and 8.inv())
                        if (st.hAlign != null) {
                            fl = (fl and (3 shl 5).inv()) or ((st.hAlign and 3) shl 5)
                        }
                        if (st.vAlign != null) {
                            fl = (fl and (3 shl 7).inv()) or ((st.vAlign and 3) shl 7)
                        }
                        val formattedDisp = when (st.numberFormatKind) {
                            "currency" -> {
                                val num = existing.displayText.replace(",", "").replace("¥", "").replace("￥", "").trim().toDoubleOrNull()
                                if (num != null && num.isFinite()) {
                                    val rounded = Math.round(num)
                                    when {
                                        rounded == Long.MIN_VALUE -> "-¥9,223,372,036,854,775,808"
                                        rounded < 0 -> "-¥${String.format(java.util.Locale.US, "%,d", -rounded)}"
                                        else -> "¥${String.format(java.util.Locale.US, "%,d", rounded)}"
                                    }
                                } else existing.displayText
                            }
                            "percent" -> {
                                val raw = existing.displayText.replace("%", "").trim()
                                val num = raw.toDoubleOrNull()
                                if (num != null && num.isFinite()) {
                                    val pct = if (existing.displayText.endsWith("%")) num else num * 100.0
                                    String.format(java.util.Locale.US, "%.1f%%", pct)
                                } else existing.displayText
                            }
                            else -> existing.displayText
                        }
                        val resolvedBg = when (st.bgColor) {
                            CustomCellStyleOverride.CLEARED -> 0
                            0 -> existing.bgColor
                            else -> st.bgColor
                        }
                        val resolvedFg = when (st.fgColor) {
                            CustomCellStyleOverride.CLEARED -> 0
                            0 -> existing.fgColor
                            else -> st.fgColor
                        }
                        val resolvedBt = st.borderTop ?: existing.borderTop
                        val resolvedBr = st.borderRight ?: existing.borderRight
                        val resolvedBb = st.borderBottom ?: existing.borderBottom
                        val resolvedBl = st.borderLeft ?: existing.borderLeft
                        val resolvedFs = if (st.fontSize > 0f) st.fontSize else existing.fontSize
                        if (existing.displayText.isNotEmpty() || resolvedBg != 0 || resolvedFg != 0 ||
                            resolvedBt != 0 || resolvedBr != 0 || resolvedBb != 0 || resolvedBl != 0 ||
                            fl != 0 || key.second in map
                        ) {
                            map[key.second] = existing.copy(
                                displayText = formattedDisp,
                                bgColor = resolvedBg,
                                fgColor = resolvedFg,
                                fontSize = resolvedFs,
                                flags = fl,
                                borderTop = resolvedBt,
                                borderRight = resolvedBr,
                                borderBottom = resolvedBb,
                                borderLeft = resolvedBl
                            )
                        }
                    }
                }
            }
        } else {
            snap.cells
        }
        val updatedMerges = if (extraMerges != null) {
            (snap.merges + extraMerges).distinct()
        } else {
            snap.merges
        }
        return snap.copy(
            revision = snap.revision + sizeRevisionBump,
            colWidths = cw,
            rowHeights = rh,
            merges = updatedMerges,
            cells = updatedCells,
            canUndo = effectiveCanUndo,
            canRedo = effectiveCanRedo
        )
    }

    internal fun recordMacroStepIfNeeded(commandId: String, paramsJson: String) {
        val cur = _uiState.value
        if (!cur.isMacroRecording) return
        if (commandId.startsWith("automate.") || commandId.startsWith("view.record")) return
        _uiState.value = cur.copy(
            currentRecordingSteps = cur.currentRecordingSteps + MacroStep(commandId, paramsJson)
        )
    }

    internal fun resolveCommandTargetRange(pObj: JSONObject): IntArray {
        val vp = _uiState.value.viewport
        val rngStr = pObj.optString("range", "").trim()
        if (rngStr.isNotEmpty()) {
            val clean = rngStr.substringAfterLast('!').trim()
            if (clean.contains(':')) {
                val parts = clean.split(':', limit = 2)
                val p0 = parseSingleCellRef(parts[0])
                val p1 = parseSingleCellRef(parts[1])
                if (p0 != null && p1 != null) {
                    return intArrayOf(
                        minOf(p0.first, p1.first),
                        minOf(p0.second, p1.second),
                        maxOf(p0.first, p1.first),
                        maxOf(p0.second, p1.second)
                    )
                }
            } else {
                val p0 = parseSingleCellRef(clean)
                if (p0 != null) {
                    return intArrayOf(p0.first, p0.second, p0.first, p0.second)
                }
            }
        }
        val r0 = minOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(0)
        val c0 = minOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(0)
        val r1 = maxOf(vp.selStartRow, vp.selEndRow).coerceAtLeast(r0)
        val c1 = maxOf(vp.selStartCol, vp.selEndCol).coerceAtLeast(c0)
        return intArrayOf(r0, c0, r1, c1)
    }

    internal fun updateCustomCellStylesInRange(
        r0: Int,
        c0: Int,
        r1: Int,
        c1: Int,
        sheetIndex: Int = _uiState.value.viewport.sheetIndex,
        transform: (Int, Int, CustomCellStyleOverride) -> CustomCellStyleOverride
    ) {
        val clampedR1 = r1.coerceAtMost(r0 + 199)
        val clampedC1 = c1.coerceAtMost(c0 + 51)
        for (r in r0..clampedR1) {
            for (c in c0..clampedC1) {
                val key = sheetIndex to NativeViewportSnapshot.packCellKey(r, c)
                val prev = customCellStyles[key] ?: CustomCellStyleOverride()
                customCellStyles[key] = transform(r, c, prev)
            }
        }
        sizeRevisionBump++
    }

    internal fun applyViewportSnapshot(snap: NativeViewportSnapshot, allowTitleUpdate: Boolean = false) {
        val decorated = decorateWithCustomSizes(snap)
        val current = _uiState.value
        val nextTitle = if (allowTitleUpdate || current.workbookTitle.isBlank()) {
            decorated.title.ifBlank { current.workbookTitle }
        } else {
            current.workbookTitle
        }
        val rawErr = decorated.error
        val filteredErr = if (rawErr != null && (
                rawErr.contains("nothing to undo", ignoreCase = true) ||
                rawErr.contains("nothing to redo", ignoreCase = true) ||
                rawErr.contains("unknown command", ignoreCase = true)
            )
        ) {
            null
        } else {
            rawErr
        }
        val sIdx = decorated.sheetIndex
        val mergedComments = if (decorated.comments.isNotEmpty()) {
            current.cellComments.toMutableMap().also { map ->
                for (cmt in decorated.comments) {
                    val coord = parseSingleCellRef(cmt.cell) ?: continue
                    val ck = NativeViewportSnapshot.packCellKey(coord.first, coord.second)
                    if (cmt.text.isNotBlank()) map[sIdx to ck] = cmt.text
                }
            }
        } else {
            current.cellComments
        }
        val mergedHyperlinks = if (decorated.hyperlinks.isNotEmpty()) {
            current.cellHyperlinks.toMutableMap().also { map ->
                for (hl in decorated.hyperlinks) {
                    val coord = parseSingleCellRef(hl.cell) ?: continue
                    val ck = NativeViewportSnapshot.packCellKey(coord.first, coord.second)
                    if (hl.target.isNotBlank()) map[sIdx to ck] = hl.target
                }
            }
        } else {
            current.cellHyperlinks
        }
        val mergedDefinedNames = if (decorated.definedNames.isNotEmpty()) {
            current.definedNames.toMutableMap().also { map ->
                for (dn in decorated.definedNames) {
                    if (dn.name.isNotBlank() && dn.refersTo.isNotBlank()) {
                        map[dn.name] = dn.refersTo
                    }
                }
            }
        } else {
            current.definedNames
        }
        _uiState.value = current.copy(
            workbookTitle = nextTitle,
            isDirty = decorated.isDirty || customColWidths.isNotEmpty() || customRowHeights.isNotEmpty() || customWrapCells.isNotEmpty() || current.sheetObjects.isNotEmpty(),
            freezeRows = decorated.freezeRows,
            freezeCols = decorated.freezeCols,
            cellComments = mergedComments,
            cellHyperlinks = mergedHyperlinks,
            definedNames = mergedDefinedNames,
            viewport = decorated,
            errorMessage = filteredErr ?: current.errorMessage
        )
    }

    fun onSelectRibbonTab(tabId: String) {
        val clean = tabId.trim().ifEmpty { "home" }
        _uiState.value = _uiState.value.copy(
            activeRibbonTab = clean,
            toolbarVisible = true
        )
    }

    fun onSelectCell(row: Int, col: Int, endRow: Int = row, endCol: Int = col) {
        val cur = _uiState.value
        val currentSnap = cur.viewport
        val (anchorR, anchorC) = currentSnap.resolveAnchorCell(row, col)
        val mergeAtStart = currentSnap.mergeAt(anchorR, anchorC)
        val r0 = minOf(anchorR, endRow).coerceAtLeast(0)
        val c0 = minOf(anchorC, endCol).coerceAtLeast(0)
        val r1 = maxOf(mergeAtStart?.endRow ?: anchorR, endRow).coerceAtLeast(r0)
        val c1 = maxOf(mergeAtStart?.endCol ?: anchorC, endCol).coerceAtLeast(c0)
        val activeAddr = MasuSheetConfig.formatCellAddress(anchorR, anchorC)
        val rangeAddr = if (r0 == r1 && c0 == c1) {
            activeAddr
        } else {
            "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c1)}"
        }
        val activeCell = currentSnap.cellAt(anchorR, anchorC)
        val hitSpill = currentSnap.spillRanges.firstOrNull { sp ->
            anchorR in sp.startRow..sp.endRow && anchorC in sp.startCol..sp.endCol &&
                (anchorR != sp.startRow || anchorC != sp.startCol)
        }
        val nextSpillFormula = if (hitSpill != null) {
            currentSnap.cellAt(hitSpill.startRow, hitSpill.startCol)?.editText.orEmpty()
        } else {
            ""
        }
        val pickingChartId = cur.chartRangePickingObjectId
        val hitPivot = cur.pivotTables.firstOrNull { it.containsCell(currentSnap.sheetIndex, anchorR, anchorC) }
        val nextActivePivotId = hitPivot?.id ?: if (cur.isPivotFieldsPaneOpen) {
            cur.pivotTables.firstOrNull { it.id == cur.activePivotTableId && it.targetSheetIndex == currentSnap.sheetIndex }?.id
        } else {
            null
        }
        // Immediate 0ms selection update on the existing viewport without changing revision/Layer 1
        _uiState.value = cur.copy(
            selectedObjectId = pickingChartId,
            selectedObjectIds = if (pickingChartId != null) setOf(pickingChartId) else emptySet(),
            activePivotTableId = nextActivePivotId,
            pivotSubtotalsMode = hitPivot?.subtotalsMode ?: cur.pivotSubtotalsMode,
            pivotGrandTotalsMode = hitPivot?.grandTotalsMode ?: cur.pivotGrandTotalsMode,
            pivotReportLayout = hitPivot?.reportLayout ?: cur.pivotReportLayout,
            pivotRepeatLabels = hitPivot?.repeatLabels ?: cur.pivotRepeatLabels,
            pivotBlankRows = hitPivot?.blankRows ?: cur.pivotBlankRows,
            pivotBandedRows = hitPivot?.bandedRows ?: cur.pivotBandedRows,
            pivotBandedCols = hitPivot?.bandedCols ?: cur.pivotBandedCols,
            pivotStylePreset = hitPivot?.stylePreset ?: cur.pivotStylePreset,
            viewport = currentSnap.copy(
                activeRow = anchorR,
                activeCol = anchorC,
                selStartRow = r0,
                selStartCol = c0,
                selEndRow = r1,
                selEndCol = c1,
                activeAddress = activeAddr,
                activeEditText = activeCell?.editText.orEmpty(),
                activeDisplayText = activeCell?.displayText.orEmpty(),
                activeSpillFormula = nextSpillFormula
            )
        )
        engineGateway.executeCommand(
            "selection.set",
            "{\"active\":\"$activeAddr\",\"range\":\"$rangeAddr\"}"
        )
        if (pickingChartId != null) {
            handleChartRangeOrAxisPicking(pickingChartId, rangeAddr, cur.chartRangePickingMode)
        }
    }

    /**
     * [EN] Selects entire column(s) `startCol..endCol` across `0..maxRow` when tapping or dragging column headers `A, B, C...`.
     * [JA] 列ヘッダ（`A, B, C...`）のタップ／ドラッグ時に、`startCol..endCol` の列全体（先頭行〜最終行）を選択します。
     */
    fun onSelectEntireColumn(startCol: Int, endCol: Int = startCol) {
        val cur = _uiState.value
        val currentSnap = cur.viewport
        val maxCol = (currentSnap.colWidths.size - 1).coerceAtLeast(0)
        val maxRow = (currentSnap.rowHeights.size - 1).coerceAtLeast(0)
        val c0 = minOf(startCol, endCol).coerceIn(0, maxCol)
        val c1 = maxOf(startCol, endCol).coerceIn(c0, maxCol)
        val activeAddr = MasuSheetConfig.formatCellAddress(0, c0)
        val rangeAddr = "${MasuSheetConfig.formatCellAddress(0, c0)}:${MasuSheetConfig.formatCellAddress(maxRow, c1)}"
        val activeCell = currentSnap.cellAt(0, c0)
        val pickingChartId = cur.chartRangePickingObjectId
        _uiState.value = cur.copy(
            selectedObjectId = pickingChartId,
            selectedObjectIds = if (pickingChartId != null) setOf(pickingChartId) else emptySet(),
            viewport = currentSnap.copy(
                activeRow = 0,
                activeCol = c0,
                selStartRow = 0,
                selStartCol = c0,
                selEndRow = maxRow,
                selEndCol = c1,
                activeAddress = activeAddr,
                activeEditText = activeCell?.editText.orEmpty(),
                activeDisplayText = activeCell?.displayText.orEmpty()
            )
        )
        engineGateway.executeCommand(
            "selection.set",
            "{\"active\":\"$activeAddr\",\"range\":\"$rangeAddr\"}"
        )
        if (pickingChartId != null) {
            handleChartRangeOrAxisPicking(pickingChartId, rangeAddr, cur.chartRangePickingMode)
        }
    }

    /**
     * [EN] Selects entire row(s) `startRow..endRow` across `0..maxCol` when tapping or dragging row headers `1, 2, 3...`.
     * [JA] 行ヘッダ（`1, 2, 3...`）のタップ／ドラッグ時に、`startRow..endRow` の行全体（先頭列〜最終列）を選択します。
     */
    fun onSelectEntireRow(startRow: Int, endRow: Int = startRow) {
        val cur = _uiState.value
        val currentSnap = cur.viewport
        val maxCol = (currentSnap.colWidths.size - 1).coerceAtLeast(0)
        val maxRow = (currentSnap.rowHeights.size - 1).coerceAtLeast(0)
        val r0 = minOf(startRow, endRow).coerceIn(0, maxRow)
        val r1 = maxOf(startRow, endRow).coerceIn(r0, maxRow)
        val activeAddr = MasuSheetConfig.formatCellAddress(r0, 0)
        val rangeAddr = "${MasuSheetConfig.formatCellAddress(r0, 0)}:${MasuSheetConfig.formatCellAddress(r1, maxCol)}"
        val activeCell = currentSnap.cellAt(r0, 0)
        val pickingChartId = cur.chartRangePickingObjectId
        _uiState.value = cur.copy(
            selectedObjectId = pickingChartId,
            selectedObjectIds = if (pickingChartId != null) setOf(pickingChartId) else emptySet(),
            viewport = currentSnap.copy(
                activeRow = r0,
                activeCol = 0,
                selStartRow = r0,
                selStartCol = 0,
                selEndRow = r1,
                selEndCol = maxCol,
                activeAddress = activeAddr,
                activeEditText = activeCell?.editText.orEmpty(),
                activeDisplayText = activeCell?.displayText.orEmpty()
            )
        )
        engineGateway.executeCommand(
            "selection.set",
            "{\"active\":\"$activeAddr\",\"range\":\"$rangeAddr\"}"
        )
        if (pickingChartId != null) {
            handleChartRangeOrAxisPicking(pickingChartId, rangeAddr, cur.chartRangePickingMode)
        }
    }

    /**
     * [EN] Selects every cell on the active worksheet (`0..maxRow`, `0..maxCol`) when tapping the top-left `◢` corner button or pressing `Ctrl+A`.
     * [JA] 左上の全選択ボタン（`◢`）または `Ctrl+A` 操作時に、アクティブシートの全セル範囲を選択します。
     */
    fun onSelectAllCells() {
        val currentSnap = _uiState.value.viewport
        val maxCol = (currentSnap.colWidths.size - 1).coerceAtLeast(0)
        val maxRow = (currentSnap.rowHeights.size - 1).coerceAtLeast(0)
        onSelectCell(0, 0, maxRow, maxCol)
    }

    /**
     * Toggles text wrapping (`折り返して表示`) on the selected cell(s) and automatically adjusts
     * the row height so all wrapped lines are completely visible.
     */
    fun onToggleWrapText() {
        val snap = _uiState.value.viewport
        val (anchorR, anchorC) = snap.resolveAnchorCell(snap.activeRow, snap.activeCol)
        val activeCell = snap.cellAt(anchorR, anchorC)
        val newWrap = !(activeCell?.isWrap ?: false)
        val r0 = minOf(snap.selStartRow, snap.selEndRow).coerceAtLeast(0)
        val r1 = maxOf(snap.selStartRow, snap.selEndRow).coerceAtLeast(r0)
        val c0 = minOf(snap.selStartCol, snap.selEndCol).coerceAtLeast(0)
        val c1 = maxOf(snap.selStartCol, snap.selEndCol).coerceAtLeast(c0)

        for (r in r0..r1) {
            for (c in c0..c1) {
                val (ar, ac) = snap.resolveAnchorCell(r, c)
                customWrapCells[snap.sheetIndex to NativeViewportSnapshot.packCellKey(ar, ac)] = newWrap
            }
        }
        sizeRevisionBump++
        val afterWrapSnap = engineGateway.executeCommand("home.wrapText", "{}")
        applyViewportSnapshot(afterWrapSnap.copy(isDirty = true))

        // Automatically adjust row height for each affected row so wrapped lines are fully shown
        for (r in r0..r1) {
            onAutoFitRow(r)
        }
    }

    /**
     * [EN] Estimates how many visual wrapped lines `text` occupies inside `availWidthPt` at `fontSizePt` (accounting for CJK full-width characters).
     * [JA] 全角・半角文字幅を考慮し、`availWidthPt` 幅・`fontSizePt` サイズで文字列を折り返した際の視覚行数を算出します。
     */
    fun computeWrappedLineCount(text: String, fontSizePt: Float, availWidthPt: Float): Int {
        if (text.isEmpty()) return 1
        val safeAvailPt = (availWidthPt - 10f).coerceAtLeast(18f)
        val paragraphs = text.split('\n')
        var totalLines = 0
        for (para in paragraphs) {
            if (para.isEmpty()) {
                totalLines += 1
                continue
            }
            var lineW = 0f
            var linesForPara = 1
            for (ch in para) {
                val cw = when {
                    InlineCheckboxHelper.isCheckboxChar(ch) -> 15f
                    ch.code > 0x7F -> fontSizePt * 1.02f
                    else -> fontSizePt * 0.56f
                }
                if (lineW + cw > safeAvailPt && lineW > 0f) {
                    linesForPara++
                    lineW = cw
                } else {
                    lineW += cw
                }
            }
            totalLines += linesForPara
        }
        return totalLines.coerceAtLeast(1)
    }

    fun onResizeColumn(col: Int, newWidthPt: Float) {
        val currentSnap = _uiState.value.viewport
        if (col !in currentSnap.colWidths.indices) return
        val clamped = newWidthPt.coerceIn(24f, 600f)
        customColWidths[currentSnap.sheetIndex to col] = clamped
        sizeRevisionBump++
        val savedActiveAddr = currentSnap.activeAddress
        val colAddr = MasuSheetConfig.formatCellAddress(currentSnap.activeRow, col)
        engineGateway.executeCommand("selection.set", "{\"active\":\"$colAddr\",\"range\":\"$colAddr\"}")
        engineGateway.executeCommand("home.columnWidth", "{\"width\":${clamped.toInt()}}")
        val snap = engineGateway.executeCommand("selection.set", "{\"active\":\"$savedActiveAddr\",\"range\":\"$savedActiveAddr\"}")
        applyViewportSnapshot(snap.copy(isDirty = true))
    }

    /**
     * [EN] Auto-fits column `col` tightly around its widest non-merged cell content (or resets an empty oversized column to `64pt`).
     * [JA] 列内の非結合セルの最大文字幅に合わせて列幅を自動調整します（空の拡幅済み列は標準の `64pt` に戻します）。
     */
    fun onAutoFitColumn(col: Int) {
        val snap = _uiState.value.viewport
        if (col !in snap.colWidths.indices) return
        var maxCellWidthPt = 0f
        var hasAnyText = false
        for (r in snap.rowHeights.indices) {
            val m = snap.mergeAt(r, col)
            if (m != null && m.startCol != m.endCol) continue
            val cell = snap.cellAt(r, col) ?: continue
            val txt = cell.displayText
            if (txt.isNotBlank()) {
                hasAnyText = true
                val fs = cell.fontSize.coerceIn(8f, 28f)
                val boldFactor = if (cell.isBold) 1.06f else 1.0f
                for (line in txt.split('\n')) {
                    var lineW = 0f
                    for (ch in line) {
                        lineW += when {
                            InlineCheckboxHelper.isCheckboxChar(ch) -> 16f
                            ch.code > 0x7F -> fs * 1.0f
                            else -> fs * 0.56f
                        }
                    }
                    val totalCellW = lineW * boldFactor + 10f
                    if (totalCellW > maxCellWidthPt) {
                        maxCellWidthPt = totalCellW
                    }
                }
            }
        }
        if (!hasAnyText) {
            val curW = snap.colWidths.getOrElse(col) { 64f }
            if (kotlin.math.abs(curW - 64f) > 0.5f || (snap.sheetIndex to col) in customColWidths) {
                onResizeColumn(col, 64f)
            } else if (col + 1 in snap.colWidths.indices && snap.colWidths[col + 1] > 68f) {
                onAutoFitColumn(col + 1)
            }
            return
        }
        val bestWidthPt = maxCellWidthPt.coerceIn(36f, 480f)
        val curW = snap.colWidths.getOrElse(col) { 64f }
        if (kotlin.math.abs(curW - bestWidthPt) <= 1.0f && curW > 68f) {
            // If already at auto-fitted expanded width, clicking again resets back to standard 64pt width
            onResizeColumn(col, 64f)
        } else {
            onResizeColumn(col, bestWidthPt)
            if (kotlin.math.abs(curW - bestWidthPt) <= 0.5f && col + 1 in snap.colWidths.indices && snap.colWidths[col + 1] > 68f) {
                onAutoFitColumn(col + 1)
            }
        }
    }

    fun onResizeRow(row: Int, newHeightPt: Float) {
        val currentSnap = _uiState.value.viewport
        if (row !in currentSnap.rowHeights.indices) return
        val clamped = newHeightPt.coerceIn(16f, 400f)
        customRowHeights[currentSnap.sheetIndex to row] = clamped
        sizeRevisionBump++
        val savedActiveAddr = currentSnap.activeAddress
        val rowAddr = MasuSheetConfig.formatCellAddress(row, currentSnap.activeCol)
        engineGateway.executeCommand("selection.set", "{\"active\":\"$rowAddr\",\"range\":\"$rowAddr\"}")
        engineGateway.executeCommand("home.rowHeight", "{\"points\":$clamped}")
        val snap = engineGateway.executeCommand("selection.set", "{\"active\":\"$savedActiveAddr\",\"range\":\"$savedActiveAddr\"}")
        applyViewportSnapshot(snap.copy(isDirty = true))
    }

    /**
     * [EN] Auto-fits row `row` height to accommodate multi-line wrapped text and font sizes, collapsing single-line standard rows cleanly to `20pt`.
     * [JA] 折り返し行数やフォントサイズに合わせて行高を自動調整し、1行標準テキストや空行は標準の `20pt` に収めます。
     */
    fun onAutoFitRow(row: Int) {
        val snap = _uiState.value.viewport
        if (row !in snap.rowHeights.indices) return
        var maxRequiredHeightPt = 20f
        var hasAnyText = false
        for (c in snap.colWidths.indices) {
            val m = snap.mergeAt(row, c)
            if (m != null && m.startRow != m.endRow) continue
            val cell = snap.cellAt(row, c) ?: continue
            val txt = cell.displayText
            if (txt.isEmpty()) continue
            hasAnyText = true
            val fs = cell.fontSize.coerceIn(8f, 28f)
            val cellWidthPt = if (m != null) {
                (m.startCol..m.endCol).sumOf { idx -> snap.colWidths.getOrElse(idx) { 72f }.toDouble() }.toFloat()
            } else {
                snap.colWidths.getOrElse(c) { 72f }
            }
            val lineCount = if (cell.isWrap || txt.contains('\n')) {
                computeWrappedLineCount(txt, fs, cellWidthPt)
            } else {
                1
            }
            val cellNeededPt = if (lineCount == 1 && fs <= 11.5f) {
                20f
            } else {
                (lineCount * (fs * 1.28f) + 6f).coerceIn(20f, 400f)
            }
            if (cellNeededPt > maxRequiredHeightPt) {
                maxRequiredHeightPt = cellNeededPt
            }
        }
        val curH = snap.rowHeights.getOrElse(row) { 20f }
        if (!hasAnyText) {
            if (kotlin.math.abs(curH - 20f) > 0.5f || (snap.sheetIndex to row) in customRowHeights) {
                onResizeRow(row, 20f)
            } else if (row + 1 in snap.rowHeights.indices && snap.rowHeights[row + 1] > 24f) {
                onAutoFitRow(row + 1)
            }
            return
        }
        if (kotlin.math.abs(curH - maxRequiredHeightPt) <= 1.0f && curH > 22f) {
            // If row is already expanded to its wrapped height, clicking the gap collapses it back to standard 20pt height
            onResizeRow(row, 20f)
        } else {
            onResizeRow(row, maxRequiredHeightPt)
            if (kotlin.math.abs(curH - maxRequiredHeightPt) <= 0.5f && row + 1 in snap.rowHeights.indices && snap.rowHeights[row + 1] > 24f) {
                onAutoFitRow(row + 1)
            }
        }
    }

    /**
     * [EN] Deletes the currently selected floating object if one is active, or clears contents in the selected cell range (converting accidental empty tables to ranges).
     * [JA] オブジェクト選択時はそのオブジェクトを削除し、セル選択時は選択範囲の数式・値を消去します。
     */
    fun onClearSelectedCells() {
        val selObjId = _uiState.value.selectedObjectId
        if (selObjId != null) {
            onDeleteSheetObject(selObjId)
            return
        }
        engineGateway.executeCommand("table.convertToRange", "{}")
        val snap = engineGateway.executeCommand("edit.clearContents", "{}")
        applyViewportSnapshot(snap)
    }

    fun onConvertToNormalRange() {
        dispatchCommand("table.convertToRange", "{}")
    }

    /**
     * [EN] Serializes the current cell or multi-cell selection as plain text / tab-separated values (TSV) for system clipboard copy.
     * [JA] 現在選択されている単一セルまたは矩形セル範囲の表示値を、システムクリップボード用のテキスト／TSV形式に変換します。
     */
    fun extractSelectedTextForClipboard(): String {
        val snap = _uiState.value.viewport
        val r0 = minOf(snap.selStartRow, snap.selEndRow).coerceAtLeast(0)
        val r1 = maxOf(snap.selStartRow, snap.selEndRow).coerceAtLeast(r0)
        val c0 = minOf(snap.selStartCol, snap.selEndCol).coerceAtLeast(0)
        val c1 = maxOf(snap.selStartCol, snap.selEndCol).coerceAtLeast(c0)
        if (r0 == r1 && c0 == c1) {
            val (ar, ac) = snap.resolveAnchorCell(r0, c0)
            return snap.cellAt(ar, ac)?.displayText.orEmpty()
        }
        val sb = StringBuilder()
        for (r in r0..r1) {
            if (r > r0) sb.append('\n')
            for (c in c0..c1) {
                if (c > c0) sb.append('\t')
                val m = snap.mergeAt(r, c)
                if (m != null && (m.startRow != r || m.startCol != c)) {
                    continue
                }
                sb.append(snap.cellAt(r, c)?.displayText.orEmpty())
            }
        }
        return sb.toString()
    }

    internal data class CopiedCellEntry(
        val relRow: Int,
        val relCol: Int,
        val displayText: String,
        val editText: String,
        val style: CustomCellStyleOverride?
    )
    internal var lastCopiedOriginRow: Int = 0
    internal var lastCopiedOriginCol: Int = 0
    internal var lastCopiedRowCount: Int = 0
    internal var lastCopiedColCount: Int = 0
    internal var lastCopiedCells: List<CopiedCellEntry> = emptyList()

    internal fun captureCopiedRangeMetadata() {
        val snap = _uiState.value.viewport
        val sIdx = snap.sheetIndex
        val r0 = minOf(snap.selStartRow, snap.selEndRow).coerceIn(0, 19_999)
        val r1 = maxOf(snap.selStartRow, snap.selEndRow).coerceIn(r0, (r0 + 499).coerceAtMost(19_999))
        val c0 = minOf(snap.selStartCol, snap.selEndCol).coerceIn(0, 511)
        val c1 = maxOf(snap.selStartCol, snap.selEndCol).coerceIn(c0, (c0 + 99).coerceAtMost(511))
        val entries = ArrayList<CopiedCellEntry>((r1 - r0 + 1) * (c1 - c0 + 1))
        for (r in r0..r1) {
            for (c in c0..c1) {
                val cm = snap.cellAt(r, c)
                val st = customCellStyles[sIdx to NativeViewportSnapshot.packCellKey(r, c)]
                entries.add(
                    CopiedCellEntry(
                        relRow = r - r0,
                        relCol = c - c0,
                        displayText = cm?.displayText.orEmpty(),
                        editText = cm?.editText.orEmpty(),
                        style = st
                    )
                )
            }
        }
        lastCopiedOriginRow = r0
        lastCopiedOriginCol = c0
        lastCopiedRowCount = r1 - r0 + 1
        lastCopiedColCount = c1 - c0 + 1
        lastCopiedCells = entries
    }

    fun onCopySelectionToClipboard(): String {
        captureCopiedRangeMetadata()
        val text = extractSelectedTextForClipboard()
        val snap = engineGateway.executeCommand("edit.copy", "{}")
        applyViewportSnapshot(snap)
        return text
    }

    fun onCutSelectionToClipboard(): String {
        val cur = _uiState.value
        val sIdx = cur.viewport.sheetIndex
        val selectedIds = cur.effectiveSelectedObjectIds
        if (selectedIds.isNotEmpty()) {
            val matching = cur.sheetObjects.filter { it.sheetIndex == sIdx && it.id in selectedIds }
            if (matching.isNotEmpty()) {
                val clipLabel = matching.joinToString("\n") { it.text.ifBlank { it.title } }
                onDeleteSelectedSheetObjects()
                return clipLabel
            }
        }
        captureCopiedRangeMetadata()
        val text = extractSelectedTextForClipboard()
        val preOverlay = captureCustomOverlaySnapshot()
        val beforeDepth = cur.viewport.undoDepth
        engineGateway.executeCommand("edit.cut", "{}")
        val snap = engineGateway.executeCommand("edit.clearContents", "{}")
        val steps = (snap.undoDepth - beforeDepth).coerceAtLeast(if (snap.revision > cur.viewport.revision) 1 else 0)
        if (steps > 0) {
            pushCustomOverlayUndoSnapshot(preOverlay.copy(engineStepsCount = steps))
        }
        applyViewportSnapshot(snap)
        return text
    }

    fun onPasteTextFromClipboard(clipboardText: String?) {
        val preOverlay = captureCustomOverlaySnapshot()
        val beforeDepth = _uiState.value.viewport.undoDepth
        val beforeRev = _uiState.value.viewport.revision
        if (!clipboardText.isNullOrEmpty()) {
            val snap = _uiState.value.viewport
            val (startR, startC) = snap.resolveAnchorCell(snap.activeRow, snap.activeCol)
            val rows = clipboardText.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n').split('\n')
            var lastSnap = snap
            var cmdCount = 0
            for ((dr, line) in rows.take(500).withIndex()) {
                val cols = line.split('\t')
                for ((dc, token) in cols.take(100).withIndex()) {
                    val addr = MasuSheetConfig.formatCellAddress(startR + dr, startC + dc)
                    val escaped = JSONObject.quote(token)
                    lastSnap = engineGateway.executeCommand(
                        "cell.set",
                        "{\"cell\":\"$addr\",\"input\":$escaped}"
                    )
                    cmdCount++
                }
            }
            val depthDelta = if (lastSnap.undoDepth >= beforeDepth && lastSnap.undoDepth > 0) {
                lastSnap.undoDepth - beforeDepth
            } else if (lastSnap.revision > beforeRev) {
                cmdCount
            } else {
                0
            }
            if (depthDelta > 0) {
                pushCustomOverlayUndoSnapshot(preOverlay.copy(engineStepsCount = depthDelta))
            }
            applyViewportSnapshot(lastSnap)
        } else {
            val snap = engineGateway.executeCommand("edit.paste", "{}")
            val depthDelta = (snap.undoDepth - beforeDepth).coerceAtLeast(if (snap.revision > beforeRev) 1 else 0)
            if (depthDelta > 0) {
                pushCustomOverlayUndoSnapshot(preOverlay.copy(engineStepsCount = depthDelta))
            }
            applyViewportSnapshot(snap)
        }
    }

    /**
     * [EN] Pastes copied cells as Values Only, Formulas Only, Formats Only, or Transposed (`H-13`).
     * [JA] コピー済みのセル範囲を「値のみ」「数式のみ」「書式のみ」「行列を入れ替える (Transpose)」で貼り付けます。
     */
    fun onPasteSpecial(mode: String = "values", transpose: Boolean = false) {
        val cur = _uiState.value
        if (lastCopiedCells.isEmpty()) {
            onPasteTextFromClipboard(null)
            return
        }
        val preOverlay = captureCustomOverlaySnapshot()
        val snap = cur.viewport
        val sIdx = snap.sheetIndex
        val (destStartR, destStartC) = snap.resolveAnchorCell(snap.activeRow, snap.activeCol)
        val beforeDepth = snap.undoDepth
        val beforeRev = snap.revision
        var lastSnap = snap
        var cmdCount = 0
        for (entry in lastCopiedCells) {
            val outRelR = if (transpose) entry.relCol else entry.relRow
            val outRelC = if (transpose) entry.relRow else entry.relCol
            val targetR = destStartR + outRelR
            val targetC = destStartC + outRelC
            val addr = MasuSheetConfig.formatCellAddress(targetR, targetC)
            when (mode) {
                "values", "value" -> {
                    lastSnap = engineGateway.executeCommand(
                        "cell.set",
                        "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(entry.displayText)}}"
                    )
                    cmdCount++
                }
                "values_and_number_formats", "valuesandnumberformats" -> {
                    lastSnap = engineGateway.executeCommand(
                        "cell.set",
                        "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(entry.displayText)}}"
                    )
                    cmdCount++
                    val srcFmt = entry.style?.numberFormatKind
                    if (!srcFmt.isNullOrEmpty()) {
                        val ck = sIdx to NativeViewportSnapshot.packCellKey(targetR, targetC)
                        val curStyle = customCellStyles[ck] ?: CustomCellStyleOverride()
                        customCellStyles[ck] = curStyle.copy(numberFormatKind = srcFmt)
                        sizeRevisionBump++
                    }
                }
                "values_and_source_formatting", "valuesandsourceformatting" -> {
                    lastSnap = engineGateway.executeCommand(
                        "cell.set",
                        "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(entry.displayText)}}"
                    )
                    cmdCount++
                    val ck = sIdx to NativeViewportSnapshot.packCellKey(targetR, targetC)
                    if (entry.style != null) {
                        customCellStyles[ck] = entry.style
                    } else {
                        customCellStyles.remove(ck)
                    }
                    sizeRevisionBump++
                }
                "formulas", "formula" -> {
                    val raw = entry.editText.ifEmpty { entry.displayText }
                    val shifted = if (raw.startsWith("=")) {
                        shiftFormulaReferences(
                            raw,
                            deltaRow = targetR - (lastCopiedOriginRow + entry.relRow),
                            deltaCol = targetC - (lastCopiedOriginCol + entry.relCol)
                        )
                    } else {
                        raw
                    }
                    lastSnap = engineGateway.executeCommand(
                        "cell.set",
                        "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(shifted)}}"
                    )
                    cmdCount++
                }
                "formats", "format" -> {
                    val ck = sIdx to NativeViewportSnapshot.packCellKey(targetR, targetC)
                    if (entry.style != null) {
                        customCellStyles[ck] = entry.style
                    } else {
                        customCellStyles.remove(ck)
                    }
                    sizeRevisionBump++
                }
                else -> {
                    val raw = entry.editText.ifEmpty { entry.displayText }
                    val shifted = if (raw.startsWith("=")) {
                        shiftFormulaReferences(
                            raw,
                            deltaRow = targetR - (lastCopiedOriginRow + entry.relRow),
                            deltaCol = targetC - (lastCopiedOriginCol + entry.relCol)
                        )
                    } else {
                        raw
                    }
                    lastSnap = engineGateway.executeCommand(
                        "cell.set",
                        "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(shifted)}}"
                    )
                    cmdCount++
                    if (entry.style != null) {
                        customCellStyles[sIdx to NativeViewportSnapshot.packCellKey(targetR, targetC)] = entry.style
                        sizeRevisionBump++
                    }
                }
            }
        }
        val depthDelta = if (lastSnap.undoDepth >= beforeDepth && lastSnap.undoDepth > 0) {
            lastSnap.undoDepth - beforeDepth
        } else if (lastSnap.revision > beforeRev) {
            cmdCount
        } else {
            0
        }
        pushCustomOverlayUndoSnapshot(preOverlay.copy(engineStepsCount = depthDelta))
        applyViewportSnapshot(lastSnap)
        val isEn = cur.language == "en"
        _uiState.value = _uiState.value.copy(
            isDirty = true,
            statusNoticeMessage = if (isEn) {
                "Applied Paste Special (${if (transpose) "Transpose" else mode})"
            } else {
                "形式を選択して貼り付け (${if (transpose) "行列を入れ替える" else mode}) を適用しました"
            }
        )
    }

    // --- Sheet Floating Objects (Images, Shapes, TextBoxes, Charts) ---

    fun onCommitCellEdit(
        row: Int,
        col: Int,
        newText: String,
        moveDeltaRow: Int = 0,
        moveDeltaCol: Int = 0,
        targetSheetIndex: Int? = null
    ) {
        if (targetSheetIndex != null && targetSheetIndex != _uiState.value.viewport.sheetIndex) {
            val actSnap = engineGateway.executeCommand("sheet.activate", "{\"sheet\":$targetSheetIndex}")
            applyViewportSnapshot(actSnap)
        }
        val (anchorR, anchorC) = _uiState.value.viewport.resolveAnchorCell(row, col)
        val normalizedText = FormulaPointModeHelper.normalizeCommittedFormula(newText)
        val addr = MasuSheetConfig.formatCellAddress(anchorR, anchorC)
        val escapedInput = JSONObject.quote(normalizedText)
        val paramsJson = "{\"cell\":\"$addr\",\"input\":$escapedInput}"
        pushCustomOverlayUndoSnapshot()
        recordMacroStepIfNeeded("cell.set", paramsJson)
        val snap = engineGateway.executeCommand("cell.set", paramsJson)
        applyViewportSnapshot(snap)
        if (_uiState.value.viewport.cellAt(anchorR, anchorC)?.isWrap == true || normalizedText.contains('\n')) {
            onAutoFitRow(anchorR)
        }
        if (moveDeltaRow != 0 || moveDeltaCol != 0) {
            val merge = snap.mergeAt(anchorR, anchorC)
            val nextR = when {
                moveDeltaRow > 0 -> (merge?.endRow ?: anchorR) + moveDeltaRow
                moveDeltaRow < 0 -> (anchorR + moveDeltaRow).coerceAtLeast(0)
                else -> anchorR
            }
            val nextC = when {
                moveDeltaCol > 0 -> (merge?.endCol ?: anchorC) + moveDeltaCol
                moveDeltaCol < 0 -> (anchorC + moveDeltaCol).coerceAtLeast(0)
                else -> anchorC
            }
            onSelectCell(nextR, nextC)
        }
    }

    fun onToggleCellCheckbox(row: Int, col: Int, checkboxIndex: Int = 0): Boolean {
        val (anchorR, anchorC) = _uiState.value.viewport.resolveAnchorCell(row, col)
        val cell = _uiState.value.viewport.cellAt(anchorR, anchorC) ?: return false
        val toggled = InlineCheckboxHelper.toggleNthCheckbox(cell.displayText, checkboxIndex) ?: return false
        onSelectCell(anchorR, anchorC)
        onCommitCellEdit(anchorR, anchorC, toggled)
        return true
    }

    fun onExecuteEngineCommand(commandId: String, paramsJson: String = "{}") {
        dispatchCommand(commandId, paramsJson)
    }

    fun onSelectSheet(sheetIndex: Int) {
        val snap = engineGateway.executeCommand("sheet.activate", "{\"sheet\":$sheetIndex}")
        applyViewportSnapshot(snap)
        val activatedName = _uiState.value.viewport.sheetName
        if (activatedName == "月間予算" || activatedName.equals("Budget", ignoreCase = true)) {
            ensureMonthlyBudgetCharts(sheetIndex)
        }
        val cur = _uiState.value
        val sIdx = cur.viewport.sheetIndex
        val hitPivot = cur.pivotTables.firstOrNull {
            it.containsCell(sIdx, cur.viewport.activeRow, cur.viewport.activeCol)
        }
        val hasPivotOnSheet = cur.pivotTables.any { it.targetSheetIndex == sIdx }
        _uiState.value = cur.copy(
            selectedObjectId = null,
            selectedObjectIds = emptySet(),
            activePivotTableId = hitPivot?.id,
            isPivotFieldsPaneOpen = cur.isPivotFieldsPaneOpen && hasPivotOnSheet
        )
    }

    /**
     * [EN] Unhides a hidden worksheet by exact name (`H-02`).
     * [JA] 非表示になっているシートをシート名指定で再表示します。
     */
    fun onUnhideSheet(sheetName: String) {
        val clean = sheetName.trim()
        if (clean.isEmpty()) return
        val snap = engineGateway.executeCommand("sheet.unhide", "{\"name\":${JSONObject.quote(clean)}}")
        applyViewportSnapshot(snap)
        val isEn = _uiState.value.language == "en"
        _uiState.value = _uiState.value.copy(
            statusNoticeMessage = if (isEn) "Unhid worksheet '$clean'" else "非表示シート「$clean」を再表示しました"
        )
    }

    /**
     * [EN] Dynamically expands the native viewport snapshot when scrolling past the currently loaded row/col horizon (`H-03`).
     * [JA] スクロール位置が現在ロード済みの行・列の上限に近づいた際、ネイティブエンジンから追加のセル領域を取得します。
     */
    fun onEnsureViewportCoverage(minRows: Int, minCols: Int) {
        val vp = _uiState.value.viewport
        val needRows = minRows.coerceIn(60, 20_000)
        val needCols = minCols.coerceIn(26, 512)
        if (needRows <= vp.rowHeights.size - 1 && needCols <= vp.colWidths.size - 1) return
        val targetMaxR = maxOf(vp.rowHeights.size - 1, needRows).coerceAtMost(20_000)
        val targetMaxC = maxOf(vp.colWidths.size - 1, needCols).coerceAtMost(512)
        val expanded = engineGateway.getViewport(targetMaxR, targetMaxC)
        applyViewportSnapshot(expanded)
    }

    /**
     * [EN] Releases all native Rust engine sessions owned by this ViewModel when the hosting Activity/window finishes.
     * [JA] Activity／ウィンドウ終了時に、この ViewModel が保持するすべての Rust ネイティブセッションを解放します。
     */
    public override fun onCleared() {
        val sessionIds = buildSet {
            _uiState.value.openWorkbooks.forEach { wb ->
                if (wb.nativeSessionId > 0L) add(wb.nativeSessionId)
            }
            if (engineGateway.activeSessionId > 0L) {
                add(engineGateway.activeSessionId)
            }
        }
        for (sid in sessionIds) {
            runCatching { engineGateway.closeSession(sid) }
        }
        super.onCleared()
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val store = SharedPreferencesStoreFactory.create(context)
                    val secretStore = AndroidKeystoreSecretStore(context)
                    val repo = MasuSheetPreferencesRepository(store, secretStore)
                    val gateway = ContentResolverDocumentGatewayFactory.create(context)
                    return MasuSheetViewModel(repo, gateway, startWithBlankWorkbook = true) as T
                }
            }
    }
}
