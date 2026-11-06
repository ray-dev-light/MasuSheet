package com.masusheet.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masusheet.app.SecondaryWorkbookActivity
import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.core.SpreadsheetColorPalette
import com.masusheet.app.core.SpreadsheetI18n
import com.masusheet.app.core.WorkbookFormatClassifier
import com.masusheet.app.model.ExistingFolderFileItem
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeSheetObject
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.model.OpenWorkbookSession
import com.masusheet.app.model.PivotTableDefinition
import com.masusheet.app.model.SheetObjectKind
import com.masusheet.app.storage.DocumentIoGateway
import com.masusheet.app.storage.WorkbookXlsxObjectPersistence
import java.io.File
import java.util.Base64
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Multi-Workbook Tab Sessions, Multi-Window UUID Handoff, Document Open, & Theme/Zoom Settings
 * Manages open workbook tab switching, lossless `:window2` detach/merge handoffs, URI document loading, sample templates, and language/theme/zoom/color preferences.
 *
 * ---
 * [JA / 日本語解説]
 * 複数ブックのタブ管理、グラフ・画像・ピボットを保持した別ウィンドウ分離・再統合、ドキュメントURI読込、サンプル展開、および言語・テーマ・倍率・配色設定を担うモジュールです。
 * ============================================================================
 */

/**
 * [EN] Snapshots the active workbook's overlays, objects, PivotTables, Undo stacks, and optional `.xlsx` bytes into `MasuSheetUiState.openWorkbooks`.
 * [JA] 現在アクティブなブックの書式・オブジェクト・ピボット・Undo履歴（および任意のシリアライズ済みバイト列）を `openWorkbooks` セッション一覧へ保存します。
 */
fun MasuSheetViewModel.saveCurrentWorkbookToSessionList(includeSerializedBytes: Boolean = false) {
    val cur = _uiState.value
    currentWorkbookIdForStacks = cur.activeWorkbookId
    val existing = cur.openWorkbooks.firstOrNull { it.id == cur.activeWorkbookId }
    val serializedBytes = if (includeSerializedBytes) {
        runCatching {
            engineGateway.syncWorkbookObjects(cur.sheetObjects)
            engineGateway.saveWorkbookBytes(cur.workbookTitle)
        }.getOrNull() ?: existing?.savedWorkbookBytes
    } else {
        existing?.savedWorkbookBytes
    }
    val session = OpenWorkbookSession(
        id = cur.activeWorkbookId,
        nativeSessionId = engineGateway.activeSessionId,
        sourceUri = currentActiveSourceUri.ifBlank { existing?.sourceUri.orEmpty() },
        title = cur.workbookTitle,
        sampleKind = cur.activeSample,
        savedWorkbookBytes = serializedBytes,
        activeSheetIndex = cur.viewport.sheetIndex,
        sheetObjects = cur.sheetObjects.toList(),
        pivotTables = cur.pivotTables.toList(),
        customColWidths = HashMap(customColWidths),
        customRowHeights = HashMap(customRowHeights),
        customWrapCells = HashMap(customWrapCells),
        customCellStyles = HashMap(customCellStyles),
        customSheetMerges = HashMap(customSheetMerges),
        sheetTabColors = HashMap(cur.sheetTabColors),
        cellComments = HashMap(cur.cellComments),
        cellHyperlinks = HashMap(cur.cellHyperlinks),
        viewportSnapshot = cur.viewport
    )
    val existingIdx = cur.openWorkbooks.indexOfFirst { it.id == cur.activeWorkbookId }
    val updatedList = if (existingIdx >= 0) {
        cur.openWorkbooks.toMutableList().also { it[existingIdx] = session }
    } else {
        cur.openWorkbooks + session
    }
    _uiState.value = cur.copy(openWorkbooks = updatedList)
}

/**
 * [EN] Creates a new isolated `BookN.xlsx` blank workbook session tab (with its own native engine session) and switches to it.
 * [JA] 既存ブックを保持したまま、独立したエンジンセッションを持つ新規空白ブック（`BookN.xlsx`）タブを作成して切り替えます。
 */
fun MasuSheetViewModel.onCreateNewBlankWorkbookSession() {
    saveCurrentWorkbookToSessionList()
    val existingTitles = _uiState.value.openWorkbooks.map { it.title }.toSet()
    var bookNum = 1
    while ("Book$bookNum.xlsx" in existingTitles) {
        bookNum++
    }
    val newTitle = "Book$bookNum.xlsx"
    val newId = "book_${nextWorkbookCounter++}"
    currentWorkbookIdForStacks = newId
    currentActiveSourceUri = ""

    customColWidths.clear()
    customRowHeights.clear()
    customWrapCells.clear()
    customCellStyles.clear()
    customSheetMerges.clear()
    val newSid = engineGateway.createSession("blank")
    engineGateway.executeCommand("file.new", "{\"title\":${JSONObject.quote(newTitle)}}")
    val snap = engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(newTitle)}}").copy(sessionId = newSid)
    val decorated = decorateWithCustomSizes(snap.copy(title = newTitle, isDirty = false))
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        activeWorkbookId = newId,
        activeSample = "blank",
        workbookTitle = newTitle,
        definedNames = emptyMap(),
        saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(newTitle),
        saveDialogExtension = "xlsx",
        isDirty = false,
        sheetObjects = emptyList(),
        selectedObjectId = null,
        selectedObjectIds = emptySet(),
        pivotTables = emptyList(),
        activePivotTableId = null,
        isPivotFieldsPaneOpen = false,
        isCreatePivotDialogOpen = false,
        dismissedPivotLauncherIds = emptySet(),
        explicitLeftAlignCells = emptySet(),
        explicitTopAlignCells = emptySet(),
        activeColumnFilterPopup = null,
        activeColumnFilters = emptyMap(),
        filteredHiddenRows = emptyMap(),
        sheetTabColors = emptyMap(),
        cellComments = emptyMap(),
        cellHyperlinks = emptyMap(),
        viewport = decorated,
        statusNoticeMessage = if (isEn) {
            "Opened new workbook '$newTitle' (previous workbook kept in Switch Workbook bar)"
        } else {
            "新規ブック「$newTitle」を開きました（前のブックも上部のブック切替バーからいつでも戻れます）"
        }
    )
    saveCurrentWorkbookToSessionList()
}

/**
 * [EN] Saves the current workbook session state and activates open workbook tab `workbookId` along with its isolated engine session and overlays.
 * [JA] 現在のブック状態を退避した上で、指定された `workbookId` のブックセッションと専用エンジン・オーバーレイ状態を復元します。
 */
fun MasuSheetViewModel.onSwitchOpenWorkbook(workbookId: String): Boolean {
    val cur = _uiState.value
    if (workbookId == cur.activeWorkbookId) return true
    saveCurrentWorkbookToSessionList()
    val latestState = _uiState.value
    val target = latestState.openWorkbooks.firstOrNull { it.id == workbookId } ?: return false

    currentWorkbookIdForStacks = target.id
    currentActiveSourceUri = target.sourceUri
    customColWidths.clear()
    customColWidths.putAll(target.customColWidths)
    customRowHeights.clear()
    customRowHeights.putAll(target.customRowHeights)
    customWrapCells.clear()
    customWrapCells.putAll(target.customWrapCells)
    customCellStyles.clear()
    customCellStyles.putAll(target.customCellStyles)
    customSheetMerges.clear()
    customSheetMerges.putAll(target.customSheetMerges)
    sizeRevisionBump++

    val liveSnap = if (target.nativeSessionId > 0L) {
        engineGateway.switchSession(target.nativeSessionId)
        val vp = engineGateway.getViewport(
            maxRow = target.viewportSnapshot.rowHeights.size.coerceAtLeast(120),
            maxCol = target.viewportSnapshot.colWidths.size.coerceAtLeast(45)
        )
        if (vp.ok) vp.copy(title = target.title) else target.viewportSnapshot
    } else {
        val sid = engineGateway.createSession(target.sampleKind.ifBlank { "blank" })
        if (target.savedWorkbookBytes != null && target.savedWorkbookBytes.isNotEmpty() && MasuSheetNativeBridge.isNativeLoaded) {
            engineGateway.openWorkbookBytes(target.title, target.savedWorkbookBytes)
            engineGateway.executeCommand("sheet.activate", "{\"sheet\":${target.activeSheetIndex}}")
        } else {
            target.viewportSnapshot.copy(sessionId = sid)
        }
    }

    val restoredViewport = decorateWithCustomSizes(liveSnap)
    val isEn = latestState.language == "en"
    _uiState.value = latestState.copy(
        activeWorkbookId = target.id,
        activeSample = target.sampleKind,
        workbookTitle = target.title,
        saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(target.title),
        saveDialogExtension = MasuSheetConfig.extractExtensionOrDefault(target.title, "xlsx"),
        sheetObjects = target.sheetObjects,
        selectedObjectId = null,
        selectedObjectIds = emptySet(),
        pivotTables = target.pivotTables,
        activePivotTableId = target.pivotTables.firstOrNull {
            it.containsCell(restoredViewport.sheetIndex, restoredViewport.activeRow, restoredViewport.activeCol)
        }?.id,
        isPivotFieldsPaneOpen = false,
        isCreatePivotDialogOpen = false,
        sheetTabColors = target.sheetTabColors,
        cellComments = target.cellComments,
        cellHyperlinks = target.cellHyperlinks,
        viewport = restoredViewport,
        statusNoticeMessage = if (isEn) {
            "Switched to workbook '${target.title}'"
        } else {
            "ブック「${target.title}」に切り替えました"
        }
    )
    return true
}

/**
 * [EN] Closes open workbook tab `workbookId`, releases its native Rust engine session (`closeSession`), and activates an adjacent open workbook.
 * [JA] 指定されたブックタブ `workbookId` を閉じて対応するネイティブRustセッションを解放し、残りのブックタブへ切り替えます。
 */
fun MasuSheetViewModel.onCloseOpenWorkbook(workbookId: String): Boolean {
    val cur = _uiState.value
    if (cur.openWorkbooks.size <= 1) return false
    val closing = cur.openWorkbooks.firstOrNull { it.id == workbookId }
    val remaining = cur.openWorkbooks.filterNot { it.id == workbookId }
    if (remaining.isEmpty()) return false
    _uiState.value = cur.copy(openWorkbooks = remaining)
    if (cur.activeWorkbookId == workbookId) {
        val nextTarget = remaining.last()
        onSwitchOpenWorkbook(nextTarget.id)
        _uiState.value = _uiState.value.copy(openWorkbooks = remaining)
    }
    if (closing != null && closing.nativeSessionId > 0L) {
        engineGateway.closeSession(closing.nativeSessionId)
    }
    workbookUndoStacks.remove(workbookId)
    workbookRedoStacks.remove(workbookId)
    return true
}

/**
 * [EN] Captures the latest state of [workbookId] (including `.xlsx` bytes and full overlay objects/styles)
 *      so it can be launched in an isolated `SecondaryWorkbookActivity` process (`:window2`).
 * [JA] 指定したブックタブ（[workbookId]）の最新状態（バイナリ＋グラフ・画像・ピボット・書式一式）を
 *      別ウィンドウ起動用に取得します。
 */
fun MasuSheetViewModel.prepareWorkbookSessionForNewWindow(
    workbookId: String,
    removeFromCurrentWindow: Boolean = true
): OpenWorkbookSession? {
    val prevActiveId = _uiState.value.activeWorkbookId
    if (workbookId == prevActiveId) {
        saveCurrentWorkbookToSessionList(includeSerializedBytes = true)
    } else {
        saveCurrentWorkbookToSessionList(includeSerializedBytes = false)
    }
    val latest = _uiState.value
    var target = latest.openWorkbooks.firstOrNull { it.id == workbookId } ?: return null
    if (target.savedWorkbookBytes == null && target.nativeSessionId > 0L) {
        val prevSid = engineGateway.activeSessionId
        engineGateway.switchSession(target.nativeSessionId)
        val exported = runCatching {
            engineGateway.syncWorkbookObjects(target.sheetObjects)
            engineGateway.saveWorkbookBytes(target.title)
        }.getOrNull()
        engineGateway.switchSession(prevSid)
        target = target.copy(savedWorkbookBytes = exported)
    }
    val isEn = latest.language == "en"
    if (removeFromCurrentWindow && latest.openWorkbooks.size > 1) {
        onCloseOpenWorkbook(workbookId)
        _uiState.value = _uiState.value.copy(
            statusNoticeMessage = if (isEn) {
                "🪟 Moved workbook '${target.title}' to a new window"
            } else {
                "🪟 ブック「${target.title}」を別ウィンドウへ移動しました"
            }
        )
    } else {
        _uiState.value = _uiState.value.copy(
            statusNoticeMessage = if (isEn) {
                "🪟 Opened workbook '${target.title}' in a new window"
            } else {
                "🪟 ブック「${target.title}」を別ウィンドウで開きました"
            }
        )
    }
    return target
}

/**
 * [EN] Restores a full [OpenWorkbookSession] (including charts, images, shapes, PivotTables,
 *      custom styles, column widths, row heights, and tab colors) into this isolated window.
 * [JA] 別ウィンドウへ移動された [OpenWorkbookSession] の全オブジェクト・ピボット・書式を欠落なく復元します。
 */
fun MasuSheetViewModel.onLoadHandoffWorkbookSession(session: OpenWorkbookSession) {
    val previousSessionIds = buildSet {
        _uiState.value.openWorkbooks.forEach { wb ->
            if (wb.nativeSessionId > 0L) add(wb.nativeSessionId)
        }
        if (engineGateway.activeSessionId > 0L) {
            add(engineGateway.activeSessionId)
        }
    }
    currentWorkbookIdForStacks = "book_1"
    currentActiveSourceUri = session.sourceUri
    customColWidths.clear()
    customColWidths.putAll(session.customColWidths)
    customRowHeights.clear()
    customRowHeights.putAll(session.customRowHeights)
    customWrapCells.clear()
    customWrapCells.putAll(session.customWrapCells)
    customCellStyles.clear()
    customCellStyles.putAll(session.customCellStyles)
    customSheetMerges.clear()
    customSheetMerges.putAll(session.customSheetMerges)
    workbookUndoStacks.clear()
    workbookRedoStacks.clear()
    sizeRevisionBump++

    val safeTitle = session.title.ifBlank { "Book1.xlsx" }
    val bytes = session.savedWorkbookBytes
    val sid = engineGateway.createSession(session.sampleKind.ifBlank { "blank" })
    for (oldSid in previousSessionIds) {
        if (oldSid != sid) {
            runCatching { engineGateway.closeSession(oldSid) }
        }
    }
    val snap = if (bytes != null && bytes.isNotEmpty()) {
        val opened = engineGateway.openWorkbookBytes(safeTitle, bytes)
        if (session.activeSheetIndex > 0 && session.activeSheetIndex < opened.totalSheetCount) {
            engineGateway.executeCommand("sheet.activate", "{\"sheet\":${session.activeSheetIndex}}")
        } else {
            opened
        }
    } else if (session.sampleKind == "blank") {
        engineGateway.executeCommand("file.new", "{\"title\":${JSONObject.quote(safeTitle)}}")
        for (cell in session.viewportSnapshot.cells.values) {
            val input = cell.editText.ifEmpty { cell.displayText }
            if (input.isNotEmpty()) {
                val addr = MasuSheetConfig.formatCellAddress(cell.row, cell.col)
                engineGateway.executeCommand("cell.set", "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(input)}}")
            }
        }
        engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(safeTitle)}}")
            .copy(title = safeTitle, isDirty = false, sessionId = sid)
    } else {
        engineGateway.initSample(session.sampleKind.ifBlank { "sales" })
        engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(safeTitle)}}")
            .copy(title = safeTitle, sessionId = sid)
    }

    val decorated = decorateWithCustomSizes(snap.copy(title = safeTitle, isDirty = false))
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        activeWorkbookId = "book_1",
        openWorkbooks = emptyList(),
        activeSample = session.sampleKind.ifBlank { "blank" },
        workbookTitle = safeTitle,
        saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(safeTitle),
        saveDialogExtension = MasuSheetConfig.extractExtensionOrDefault(safeTitle, "xlsx"),
        isDirty = false,
        sheetObjects = session.sheetObjects,
        selectedObjectId = null,
        selectedObjectIds = emptySet(),
        pivotTables = session.pivotTables,
        activePivotTableId = session.pivotTables.firstOrNull {
            it.containsCell(decorated.sheetIndex, decorated.activeRow, decorated.activeCol)
        }?.id,
        isPivotFieldsPaneOpen = false,
        isCreatePivotDialogOpen = false,
        sheetTabColors = session.sheetTabColors,
        cellComments = session.cellComments,
        cellHyperlinks = session.cellHyperlinks,
        viewport = decorated,
        statusNoticeMessage = if (isEn) {
            "🪟 Opened '$safeTitle' in separate window"
        } else {
            "🪟 別ウィンドウで「$safeTitle」を開きました"
        }
    )
    saveCurrentWorkbookToSessionList()
}

/**
 * [EN] Loads a workbook `(title, bytes, sampleKind, activeSheetIndex)` handed off into a newly opened secondary window.
 * [JA] 別ウィンドウ起動時に引き渡されたブックデータとシート選択位置をこのウィンドウへ読み込みます。
 */
fun MasuSheetViewModel.onLoadHandoffWorkbook(
    title: String,
    bytes: ByteArray?,
    sampleKind: String = "blank",
    activeSheetIndex: Int = 0
) {
    onLoadHandoffWorkbookSession(
        OpenWorkbookSession(
            id = "book_1",
            title = title,
            sampleKind = sampleKind,
            savedWorkbookBytes = bytes,
            activeSheetIndex = activeSheetIndex
        )
    )
}

/**
 * [EN] Merges a full [OpenWorkbookSession] back from `SecondaryWorkbookActivity` into this
 *      window without losing any existing tabs or floating sheet objects/pivots/styles.
 * [JA] 別ウィンドウから戻された [OpenWorkbookSession] を、既存タブおよび全オブジェクト・書式を維持したまま統合します。
 */
fun MasuSheetViewModel.onMergeHandoffWorkbookSession(session: OpenWorkbookSession) {
    saveCurrentWorkbookToSessionList()
    val existingTitles = _uiState.value.openWorkbooks.map { it.title }.toSet()
    val rawTitle = session.title.ifBlank { "Book1.xlsx" }
    val finalTitle = if (rawTitle !in existingTitles) {
        rawTitle
    } else {
        val base = MasuSheetConfig.extractBaseNameWithoutExtension(rawTitle)
        val ext = MasuSheetConfig.extractExtensionOrDefault(rawTitle, "xlsx")
        var suffix = 2
        while ("$base ($suffix).$ext" in existingTitles) {
            suffix++
        }
        "$base ($suffix).$ext"
    }
    val newId = "book_${nextWorkbookCounter++}"
    currentWorkbookIdForStacks = newId
    currentActiveSourceUri = session.sourceUri

    customColWidths.clear()
    customColWidths.putAll(session.customColWidths)
    customRowHeights.clear()
    customRowHeights.putAll(session.customRowHeights)
    customWrapCells.clear()
    customWrapCells.putAll(session.customWrapCells)
    customCellStyles.clear()
    customCellStyles.putAll(session.customCellStyles)
    customSheetMerges.clear()
    customSheetMerges.putAll(session.customSheetMerges)
    sizeRevisionBump++

    val bytes = session.savedWorkbookBytes
    val sid = engineGateway.createSession(session.sampleKind.ifBlank { "blank" })
    val snap = if (bytes != null && bytes.isNotEmpty()) {
        val opened = engineGateway.openWorkbookBytes(finalTitle, bytes)
        if (session.activeSheetIndex > 0 && session.activeSheetIndex < opened.totalSheetCount) {
            engineGateway.executeCommand("sheet.activate", "{\"sheet\":${session.activeSheetIndex}}")
        } else {
            opened
        }
    } else if (session.sampleKind == "blank") {
        engineGateway.executeCommand("file.new", "{\"title\":${JSONObject.quote(finalTitle)}}")
        for (cell in session.viewportSnapshot.cells.values) {
            val input = cell.editText.ifEmpty { cell.displayText }
            if (input.isNotEmpty()) {
                val addr = MasuSheetConfig.formatCellAddress(cell.row, cell.col)
                engineGateway.executeCommand("cell.set", "{\"cell\":\"$addr\",\"input\":${JSONObject.quote(input)}}")
            }
        }
        engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(finalTitle)}}")
            .copy(title = finalTitle, isDirty = false, sessionId = sid)
    } else {
        engineGateway.initSample(session.sampleKind.ifBlank { "sales" })
        engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(finalTitle)}}")
            .copy(title = finalTitle, sessionId = sid)
    }

    val decorated = decorateWithCustomSizes(snap.copy(title = finalTitle, isDirty = false))
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        activeWorkbookId = newId,
        activeSample = session.sampleKind.ifBlank { "blank" },
        workbookTitle = finalTitle,
        saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(finalTitle),
        saveDialogExtension = MasuSheetConfig.extractExtensionOrDefault(finalTitle, "xlsx"),
        isDirty = false,
        sheetObjects = session.sheetObjects,
        selectedObjectId = null,
        selectedObjectIds = emptySet(),
        pivotTables = session.pivotTables,
        activePivotTableId = session.pivotTables.firstOrNull {
            it.containsCell(decorated.sheetIndex, decorated.activeRow, decorated.activeCol)
        }?.id,
        isPivotFieldsPaneOpen = false,
        isCreatePivotDialogOpen = false,
        sheetTabColors = session.sheetTabColors,
        cellComments = session.cellComments,
        cellHyperlinks = session.cellHyperlinks,
        viewport = decorated
    )
    saveCurrentWorkbookToSessionList()
    val totalTabs = _uiState.value.openWorkbooks.size
    _uiState.value = _uiState.value.copy(
        statusNoticeMessage = if (isEn) {
            "📥 Merged '$finalTitle' into main window ($totalTabs workbooks open)"
        } else {
            "📥 ブック「$finalTitle」を元のウィンドウに統合しました（全${totalTabs}ブック）"
        }
    )
}

/**
 * [EN] Merges a workbook handed back from a secondary window into the main window's open workbook tab bar.
 * [JA] サブウィンドウからメインウィンドウへ戻されたブックデータを、現在のウィンドウの開いているブック一覧へ統合します。
 */
fun MasuSheetViewModel.onMergeHandoffWorkbookIntoCurrentWindow(
    title: String,
    bytes: ByteArray?,
    sampleKind: String = "blank",
    activeSheetIndex: Int = 0
) {
    onMergeHandoffWorkbookSession(
        OpenWorkbookSession(
            id = "handoff",
            title = title,
            sampleKind = sampleKind,
            savedWorkbookBytes = bytes,
            activeSheetIndex = activeSheetIndex
        )
    )
}

/**
 * [EN] Enables or disables side-by-side Split Book View for comparing two open workbooks in the same window.
 * [JA] 同一ウィンドウ内で2つのブックを左右に並べて比較・参照する「ブック並列表示（分割ビュー）」を切り替えます。
 */
fun MasuSheetViewModel.onToggleSplitBookView(enable: Boolean? = null) {
    val cur = _uiState.value
    val nextSplit = enable ?: !cur.isSplitBookViewActive
    val isEn = cur.language == "en"
    _uiState.value = cur.copy(
        isSplitBookViewActive = nextSplit,
        statusNoticeMessage = if (nextSplit) {
            if (isEn) "Enabled side-by-side Split Workbook View" else "◫ 2つのブックを並べて比較・編集する「スプリット表示」を開始しました"
        } else {
            if (isEn) "Closed Split Workbook View" else "スプリット表示を終了し、1画面表示に戻しました"
        }
    )
}

/**
 * [EN] Alias for `onToggleSplitBookView` invoked by View-tab ribbon actions.
 * [JA] ブック並列表示（分割ビュー）の表示状態を切り替えます（`onToggleSplitBookView` のエイリアス）。
 */
fun MasuSheetViewModel.onToggleSplitWorkbookView(enable: Boolean? = null) {
    onToggleSplitBookView(enable)
}

/**
 * [EN] Returns the non-active `OpenWorkbookSession` (or active session as single-book fallback) displayed in the right-hand Split Book pane.
 * [JA] ブック並列表示時に右側ペインへ表示するサブブックセッション（別ブックが無い場合は現在ブック）を返します。
 */
fun MasuSheetViewModel.getSplitSecondaryWorkbookSession(): OpenWorkbookSession? {
    val cur = _uiState.value
    return cur.openWorkbooks.lastOrNull { it.id != cur.activeWorkbookId }
        ?: cur.openWorkbooks.lastOrNull()
}

/**
 * [EN] Creates a new blank workbook tab via `onCreateNewBlankWorkbookSession`.
 * [JA] 新しい空白ブックセッションを作成します（`onCreateNewBlankWorkbookSession` の呼び出しラッパー）。
 */
fun MasuSheetViewModel.onCreateBlankWorkbook() {
    onCreateNewBlankWorkbookSession()
}

/**
 * [EN] Normalizes `sampleName` (mapping `monthly_budget` -> `budget`) and loads the requested built-in sample workbook.
 * [JA] テンプレート名を正規化した上で、指定された内蔵サンプルブックを読み込みます。
 */
fun MasuSheetViewModel.onLoadSampleTemplate(sampleName: String) {
    val norm = if (sampleName.equals("monthly_budget", ignoreCase = true)) "budget" else sampleName
    onLoadSampleWorkbook(norm)
}

/**
 * [EN] Creates an isolated engine session for loaded file `bytes`, restores any embedded MasuSheet object/pivot overlay, and activates the new workbook tab.
 * [JA] 読み込んだファイルバイト列から独立エンジンセッションを生成し、埋め込みグラフ・ピボット情報を復元して新規ブックタブとして開きます。
 */
internal fun MasuSheetViewModel.applyLoadedDocumentBytes(
    uriString: String,
    bytes: ByteArray,
    rawName: String,
    persistedOverlay: OpenWorkbookSession?
): Boolean {
    saveCurrentWorkbookToSessionList()
    val existingByUri = _uiState.value.openWorkbooks.firstOrNull {
        it.sourceUri.isNotBlank() && it.sourceUri == uriString
    }
    if (existingByUri != null && (existingByUri.viewportSnapshot.isDirty || (existingByUri.id == _uiState.value.activeWorkbookId && _uiState.value.isDirty))) {
        return onSwitchOpenWorkbook(existingByUri.id)
    }
    val safeName = MasuSheetConfig.sanitizeWorkbookFileName(rawName)
    preferencesRepository.recordRecentWorkbook(safeName)
    val nextBookId = existingByUri?.id ?: "book_${nextWorkbookCounter++}"
    currentWorkbookIdForStacks = nextBookId
    currentActiveSourceUri = uriString

    customColWidths.clear()
    customRowHeights.clear()
    customWrapCells.clear()
    customCellStyles.clear()
    customSheetMerges.clear()
    workbookUndoStacks.remove(nextBookId)
    workbookRedoStacks.remove(nextBookId)
    val newSid = existingByUri?.nativeSessionId?.takeIf { it > 0L }?.also { engineGateway.switchSession(it) }
        ?: engineGateway.createSession("blank")
    val snap = engineGateway.openWorkbookBytes(safeName, bytes).copy(sessionId = newSid)
    val restoredObjects: List<NativeSheetObject>
    val restoredPivots: List<PivotTableDefinition>
    val restoredTabColors: Map<Int, Int>
    val restoredComments: Map<Pair<Int, Long>, String>
    val restoredLinks: Map<Pair<Int, Long>, String>
    val finalViewport: NativeViewportSnapshot
    if (persistedOverlay != null) {
        customColWidths.putAll(persistedOverlay.customColWidths)
        customRowHeights.putAll(persistedOverlay.customRowHeights)
        customWrapCells.putAll(persistedOverlay.customWrapCells)
        customCellStyles.putAll(persistedOverlay.customCellStyles)
        customSheetMerges.putAll(persistedOverlay.customSheetMerges)
        sizeRevisionBump++
        restoredObjects = persistedOverlay.sheetObjects
        restoredPivots = persistedOverlay.pivotTables
        restoredTabColors = persistedOverlay.sheetTabColors
        restoredComments = persistedOverlay.cellComments
        restoredLinks = persistedOverlay.cellHyperlinks
        engineGateway.syncWorkbookObjects(restoredObjects)
        finalViewport = decorateWithCustomSizes(snap)
    } else {
        restoredObjects = engineGateway.readWorkbookObjects()
        restoredPivots = emptyList()
        restoredTabColors = emptyMap()
        restoredComments = emptyMap()
        restoredLinks = emptyMap()
        finalViewport = snap
    }
    advanceObjectCounterPast(restoredObjects)

    _uiState.value = _uiState.value.copy(
        isLoading = false,
        activeWorkbookId = nextBookId,
        workbookTitle = safeName,
        definedNames = finalViewport.definedNames.associate { it.name to it.refersTo },
        saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(safeName),
        saveDialogExtension = MasuSheetConfig.extractExtensionOrDefault(safeName, "xlsx"),
        isDirty = false,
        sheetObjects = restoredObjects,
        selectedObjectId = null,
        selectedObjectIds = emptySet(),
        pivotTables = restoredPivots,
        activePivotTableId = restoredPivots.firstOrNull {
            it.containsCell(finalViewport.sheetIndex, finalViewport.activeRow, finalViewport.activeCol)
        }?.id,
        isPivotFieldsPaneOpen = false,
        isCreatePivotDialogOpen = false,
        dismissedPivotLauncherIds = emptySet(),
        sheetTabColors = restoredTabColors,
        cellComments = restoredComments,
        cellHyperlinks = restoredLinks,
        activeColumnFilterPopup = null,
        activeColumnFilters = emptyMap(),
        filteredHiddenRows = emptyMap(),
        recentWorkbooks = preferencesRepository.getRecentWorkbooks(),
        viewport = finalViewport,
        errorMessage = finalViewport.error
    )
    saveCurrentWorkbookToSessionList()
    return true
}

fun MasuSheetViewModel.onOpenDocumentUri(uriString: String): Boolean {
    val bytes = documentGateway.readBytes(uriString)
    if (bytes == null || bytes.isEmpty()) {
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "ファイルを読み込めませんでした")
        return false
    }
    val rawName = documentGateway.resolveDisplayName(uriString)
    val persistedOverlay = WorkbookXlsxObjectPersistence.extractFromXlsxBytes(bytes)
    return applyLoadedDocumentBytes(uriString, bytes, rawName, persistedOverlay)
}

/**
 * [EN] Reads and extracts workbook bytes on `Dispatchers.IO` before updating the active session on the main thread.
 * [JA] SAF／ローカルからのファイル読込およびオーバーレイ展開を `Dispatchers.IO` で非同期実行し、UIフリーズを防ぎます。
 */
fun MasuSheetViewModel.onOpenDocumentUriAsync(uriString: String) {
    val existingDirty = _uiState.value.openWorkbooks.firstOrNull {
        it.sourceUri.isNotBlank() && it.sourceUri == uriString &&
            (it.viewportSnapshot.isDirty || (it.id == _uiState.value.activeWorkbookId && _uiState.value.isDirty))
    }
    if (existingDirty != null) {
        onSwitchOpenWorkbook(existingDirty.id)
        return
    }
    _uiState.value = _uiState.value.copy(isLoading = true)
    viewModelScope.launch {
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bytes = documentGateway.readBytes(uriString)
            if (bytes == null || bytes.isEmpty()) {
                null
            } else {
                val rawName = documentGateway.resolveDisplayName(uriString)
                val overlay = WorkbookXlsxObjectPersistence.extractFromXlsxBytes(bytes)
                Triple(bytes, rawName, overlay)
            }
        }
        if (loaded == null) {
            val isEn = _uiState.value.language == "en"
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = if (isEn) "Could not read workbook file" else "ファイルを読み込めませんでした"
            )
        } else {
            applyLoadedDocumentBytes(uriString, loaded.first, loaded.second, loaded.third)
        }
    }
}

/**
 * [EN] Opens the Save Dialog asynchronously for `.xlsx` export without blocking the UI thread.
 * [JA] UIスレッドをブロックせずに `.xlsx` 保存ダイアログを開きます。
 */
fun MasuSheetViewModel.onTriggerSaveAsync() {
    onOpenSaveDialogAsync("xlsx")
}

/**
 * [EN] Opens the Save Dialog asynchronously for `.pdf` export without blocking the UI thread.
 * [JA] UIスレッドをブロックせずに `.pdf` 出力ダイアログを開きます。
 */
fun MasuSheetViewModel.onTriggerExportPdfAsync() {
    onOpenSaveDialogAsync("pdf")
}

/**
 * [EN] Opens the Save Dialog for `.xlsx` with pre-built export bytes ready for immediate save.
 * [JA] `.xlsx` 形式のエクスポートデータを準備した状態でファイル保存ダイアログを開きます。
 */
fun MasuSheetViewModel.onTriggerSave() {
    onOpenSaveDialog("xlsx", prepareBytesImmediately = true)
}

/**
 * [EN] Opens the Save Dialog configured for `.pdf` export with rendered PDF bytes ready.
 * [JA] `.pdf` 形式の出力データを準備した状態でPDFエクスポート用保存ダイアログを開きます。
 */
fun MasuSheetViewModel.onTriggerExportPdf() {
    onOpenSaveDialog("pdf", prepareBytesImmediately = true)
}

/**
 * [EN] Loads a built-in sample workbook (`sales`, `budget`, `repair`, `inventory`, etc.) into a fresh open-workbook session tab.
 * [JA] 指定された内蔵サンプルブック（売上・月間予算・修理依頼書など）を新しいブックセッションとして読み込みます。
 */
fun MasuSheetViewModel.onLoadSampleWorkbook(sampleName: String) {
    val clean = sampleName.trim().lowercase().ifEmpty { "sales" }
    saveCurrentWorkbookToSessionList()
    val resolvedTitle = when (clean) {
        "repair" -> "修理依頼書.xlsx"
        "budget" -> "月間予算.xlsx"
        else -> "${clean.replaceFirstChar { it.uppercase() }}.xlsx"
    }
    val existingBySample = _uiState.value.openWorkbooks.firstOrNull {
        it.sampleKind.equals(clean, ignoreCase = true) && it.sourceUri.isBlank()
    }
    val nextBookId = existingBySample?.id ?: "book_${nextWorkbookCounter++}"
    currentWorkbookIdForStacks = nextBookId
    currentActiveSourceUri = ""
    customColWidths.clear()
    customRowHeights.clear()
    customWrapCells.clear()
    customCellStyles.clear()
    customSheetMerges.clear()
    workbookUndoStacks.remove(nextBookId)
    workbookRedoStacks.remove(nextBookId)
    val sid = existingBySample?.nativeSessionId?.takeIf { it > 0L }?.also { engineGateway.switchSession(it) }
        ?: engineGateway.createSession(clean)
    val snap = engineGateway.initSample(clean).copy(sessionId = sid)
    _uiState.value = _uiState.value.copy(
        activeWorkbookId = nextBookId,
        activeSample = clean,
        workbookTitle = resolvedTitle,
        definedNames = snap.definedNames.associate { it.name to it.refersTo },
        saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(resolvedTitle),
        saveDialogExtension = "xlsx",
        isDirty = false,
        sheetObjects = emptyList(),
        selectedObjectId = null,
        selectedObjectIds = emptySet(),
        pivotTables = emptyList(),
        activePivotTableId = null,
        isPivotFieldsPaneOpen = false,
        isCreatePivotDialogOpen = false,
        dismissedPivotLauncherIds = emptySet(),
        activeColumnFilterPopup = null,
        activeColumnFilters = emptyMap(),
        filteredHiddenRows = emptyMap(),
        viewport = snap
    )
    if (clean == "budget") {
        engineGateway.executeCommand("sheet.rename", "{\"sheet\":0,\"name\":\"月間予算\"}")
        val budgetSnap = populateMonthlyBudgetSheet(0, attachChartsImmediately = true)
        val decorated = decorateWithCustomSizes(
            budgetSnap.copy(
                title = resolvedTitle,
                sheetName = "月間予算",
                sheetNames = listOf("月間予算"),
                isDirty = false,
                canUndo = false,
                canRedo = false
            )
        )
        _uiState.value = _uiState.value.copy(
            workbookTitle = resolvedTitle,
            isDirty = false,
            viewport = decorated
        )
    } else if (clean == "sales") {
        setupInitialMonthlyBudgetTab()
    }
    saveCurrentWorkbookToSessionList()
}

/**
 * [EN] Cycles to the next built-in sample workbook template.
 * [JA] 次の内蔵サンプルブックテンプレートへ順番に切り替えます。
 */
fun MasuSheetViewModel.onLoadNextSample() {
    val next = WorkbookFormatClassifier.nextSampleWorkbook(_uiState.value.activeSample)
    onLoadSampleWorkbook(next)
}

/**
 * [EN] Loads the Japanese Equipment Repair Request (`修理依頼書.xlsx`) sample template.
 * [JA] 「修理依頼書.xlsx」サンプルテンプレートを読み込みます。
 */
fun MasuSheetViewModel.onLoadRepairChecklistSample() {
    onLoadSampleWorkbook("repair")
}

/**
 * [EN] Switches UI language between Japanese (`ja`) and English (`en`), persisting the preference and translating default chart labels.
 * [JA] アプリ表示言語を日本語（`ja`）と英語（`en`）の間で切り替え、設定保存と標準グラフラベルの翻訳を行います。
 */
fun MasuSheetViewModel.onToggleLanguage() {
    val nextLang = if (_uiState.value.language == "ja") "en" else "ja"
    val isEn = nextLang == "en"
    preferencesRepository.setLanguage(nextLang)
    val translatedObjects = _uiState.value.sheetObjects.map { obj ->
        if (obj.kind == SheetObjectKind.CHART) {
            val nextTitle = SpreadsheetI18n.translateChartDisplayText(obj.title, isEn)
            val nextText = SpreadsheetI18n.translateChartDisplayText(obj.text, isEn)
            val nextSeries = obj.chartSeries.map { s ->
                s.copy(name = SpreadsheetI18n.translateChartDisplayText(s.name, isEn))
            }
            obj.copy(title = nextTitle, text = nextText, chartSeries = nextSeries)
        } else {
            obj
        }
    }
    _uiState.value = _uiState.value.copy(
        language = nextLang,
        sheetObjects = translatedObjects
    )
    val cmd = if (isEn) "app.language.english" else "app.language.japanese"
    dispatchCommand(cmd)
}

/**
 * [EN] Switches between Light Mode and Dark Mode, persisting the theme preference and syncing the native engine viewport theme.
 * [JA] ライトモードとダークモードを切り替え、設定の永続化とエンジン側のテーマ同期を行います。
 */
fun MasuSheetViewModel.onToggleDarkTheme() {
    val nextDark = !_uiState.value.isDarkTheme
    preferencesRepository.setDarkTheme(nextDark)
    _uiState.value = _uiState.value.copy(isDarkTheme = nextDark)
    val mode = if (nextDark) "dark" else "light"
    dispatchCommand("view.theme", "{\"mode\":\"$mode\"}")
}

/**
 * [EN] Switches ribbon chrome between Modern Material 3 UI (`isMaterial3Ui = true`) and Classic Desktop Ribbon UI (`false`).
 * [JA] リボン外観を「Material 3 モダンUI」と「クラシック・デスクトップリボンUI」の間で切り替えます。
 */
fun MasuSheetViewModel.onToggleMaterial3Ui() {
    val nextM3 = !_uiState.value.isMaterial3Ui
    preferencesRepository.setMaterial3Ui(nextM3)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        isMaterial3Ui = nextM3,
        statusNoticeMessage = if (nextM3) {
            if (isEn) {
                "Switched to Material 3 UI (Rounded pill cards & tonal buttons)"
            } else {
                "【現在: M3 UI】に切り替えました（角丸ピルカード＆立体ボタン表示）"
            }
        } else {
            if (isEn) {
                "Switched to Classic Flat UI (Clean borderless ribbon buttons)"
            } else {
                "【現在: クラシックUI】に切り替えました（枠線なしのフラットなリボン表示）"
            }
        }
    )
}

/**
 * [EN] Cycles sheet zoom percentage through `100% -> 125% -> 150% -> 100%`.
 * [JA] シートの表示倍率を `100% → 125% → 150% → 100%` の順で切り替えます。
 */
fun MasuSheetViewModel.onCycleZoom() {
    val nextZoom = when (_uiState.value.zoomPercent) {
        100 -> 125
        125 -> 150
        else -> 100
    }
    onSetZoomPercent(nextZoom)
}

/**
 * [EN] Clamps sheet zoom to `25%..400%`, updates `MasuSheetUiState.zoomPercent`, and optionally persists the zoom level.
 * [JA] シート表示倍率を `25%..400%` の範囲にクランプして反映し、必要に応じ設定へ永続化します。
 */
fun MasuSheetViewModel.onSetZoomPercent(percent: Int, persistPreference: Boolean = true) {
    val clamped = percent.coerceIn(25, 400)
    if (persistPreference) {
        preferencesRepository.setZoomPercent(clamped)
    }
    _uiState.value = _uiState.value.copy(
        zoomPercent = clamped,
        isZoomDialogOpen = false
    )
    if (persistPreference) {
        dispatchCommand("view.interfaceScale", "{\"percent\":$clamped}")
    }
}

/**
 * [EN] Opens or closes the Zoom preset & custom percentage dialog.
 * [JA] 表示倍率（ズーム）選択・カスタム指定ダイアログを開閉します。
 */
fun MasuSheetViewModel.onOpenZoomDialog(open: Boolean = true) {
    _uiState.value = _uiState.value.copy(isZoomDialogOpen = open)
}

/**
 * Spreadsheet's "Fit Selection (選択範囲に合わせて拡大/縮小)": computes the zoom level (25%..400%)
 * that fits the currently selected cell range comfortably into the viewport.
 */
fun MasuSheetViewModel.onFitZoomToSelection(viewportWidthPt: Float = 560f, viewportHeightPt: Float = 320f) {
    val vp = _uiState.value.viewport
    val r0 = minOf(vp.selStartRow, vp.selEndRow).coerceIn(0, (vp.rowHeights.size - 1).coerceAtLeast(0))
    val r1 = maxOf(vp.selStartRow, vp.selEndRow).coerceIn(r0, (vp.rowHeights.size - 1).coerceAtLeast(0))
    val c0 = minOf(vp.selStartCol, vp.selEndCol).coerceIn(0, (vp.colWidths.size - 1).coerceAtLeast(0))
    val c1 = maxOf(vp.selStartCol, vp.selEndCol).coerceIn(c0, (vp.colWidths.size - 1).coerceAtLeast(0))
    val selW = (vp.colOffsets.getOrElse(c1 + 1) { 240f } - vp.colOffsets.getOrElse(c0) { 0f }).coerceAtLeast(64f)
    val selH = (vp.rowOffsets.getOrElse(r1 + 1) { 80f } - vp.rowOffsets.getOrElse(r0) { 0f }).coerceAtLeast(24f)
    val fitW = (viewportWidthPt / selW) * 100f
    val fitH = (viewportHeightPt / selH) * 100f
    val targetZoom = minOf(fitW, fitH).roundToInt().coerceIn(25, 400)
    onSetZoomPercent(targetZoom)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        isZoomDialogOpen = false,
        statusNoticeMessage = if (isEn) {
            "Fit selection to ${targetZoom}%"
        } else {
            "選択範囲に合わせて表示倍率を ${targetZoom}% に調整しました"
        }
    )
}

/**
 * [EN] Shows or hides the entire top Ribbon and Quick Access bar so maximum screen area is dedicated to the grid.
 * [JA] 上部リボンおよびクイックアクセスバー全体の表示／非表示を切り替え、シート表示領域を最大化します。
 */
fun MasuSheetViewModel.onToggleToolbar() {
    _uiState.value = _uiState.value.copy(toolbarVisible = !_uiState.value.toolbarVisible)
}

/**
 * [EN] Clears loading state and surfaces a human-readable engine error message banner.
 * [JA] ローディング状態を解除し、エンジンエラーメッセージをエラーバナーへ表示します。
 */
fun MasuSheetViewModel.onEngineError(error: String) {
    _uiState.value = _uiState.value.copy(
        isLoading = false,
        errorMessage = error.trim().ifEmpty { "Unknown error" }
    )
}

/**
 * [EN] Clears the currently displayed error banner message.
 * [JA] 表示中のエラーバナーメッセージを閉じます。
 */
fun MasuSheetViewModel.onDismissError() {
    _uiState.value = _uiState.value.copy(errorMessage = null)
}

// --- Spreadsheet Color Picker & Theme System ---

/**
 * [EN] Pushes non-transparent `argb` onto the persisted MRU recent-colors palette.
 * [JA] 選択された色（`argb != 0`）を「最近使った色」履歴へ記録・永続化します。
 */
fun MasuSheetViewModel.onRecordRecentColor(argb: Int) {
    if (argb != 0) {
        preferencesRepository.recordRecentColor(argb)
        _uiState.value = _uiState.value.copy(recentColors = preferencesRepository.getRecentColors())
    }
}

/**
 * [EN] Applies `argb` (`0` = No Fill / Automatic) to the requested target (`fill`, `font`, `border`, `objectFill`, `objectStroke`, `objectText`, `chartSeries`, or `sheetTab`).
 * [JA] カラーピッカーで選択された色 `argb`（`0` は塗りつぶしなし／自動）をセル背景・文字・罫線・図形・グラフ系列・シートタブ等の対象へ適用します。
 */
fun MasuSheetViewModel.onApplyPickedColor(target: String, argb: Int, sheetIndex: Int = _uiState.value.viewport.sheetIndex) {
    onRecordRecentColor(argb)
    val hex = if (argb == 0) "" else SpreadsheetColorPalette.toHexString(argb)
    when (target) {
        "fill" -> {
            if (argb == 0) {
                dispatchCommand("home.fillColor", "{\"color\":\"none\"}")
            } else {
                dispatchCommand("home.fillColor", "{\"color\":\"$hex\"}")
            }
            if (_uiState.value.selectedObjectId != null) {
                onUpdateSelectedObjectFillColor(argb)
            }
        }
        "font" -> {
            if (argb == 0) {
                dispatchCommand("home.fontColor", "{\"color\":\"auto\"}")
            } else {
                dispatchCommand("home.fontColor", "{\"color\":\"$hex\"}")
            }
        }
        "border" -> {
            if (argb == 0) {
                dispatchCommand("home.borders", "{\"preset\":\"none\",\"style\":\"none\"}")
            } else {
                dispatchCommand("home.borders", "{\"preset\":\"all\",\"style\":\"thin\",\"color\":\"$hex\"}")
            }
            if (_uiState.value.selectedObjectId != null) {
                onUpdateSelectedObjectStrokeColor(argb)
            }
        }
        "objectFill" -> onUpdateSelectedObjectFillColor(argb)
        "objectStroke" -> onUpdateSelectedObjectStrokeColor(argb)
        "objectText" -> onUpdateSelectedObjectTextColor(argb, "all")
        "objectText:title" -> onUpdateSelectedObjectTextColor(argb, "title")
        "objectText:xAxis" -> onUpdateSelectedObjectTextColor(argb, "xAxis")
        "objectText:yAxis" -> onUpdateSelectedObjectTextColor(argb, "yAxis")
        "objectText:legend" -> onUpdateSelectedObjectTextColor(argb, "legend")
        "chartSeries" -> onUpdateSelectedChartSeriesColor(argb)
        "sheetTab" -> {
            val updatedTabs = _uiState.value.sheetTabColors.toMutableMap()
            if (argb == 0) updatedTabs.remove(sheetIndex) else updatedTabs[sheetIndex] = argb
            _uiState.value = _uiState.value.copy(sheetTabColors = updatedTabs)
            if (argb == 0) {
                dispatchCommand("home.tabColor", "{\"color\":\"none\"}")
            } else if (hex.isNotEmpty()) {
                dispatchCommand("home.tabColor", "{\"color\":\"$hex\"}")
            }
        }
    }
}

/**
 * [EN] Applies a 12-color workbook theme palette preset (`Standard`, `Emerald`, `Ocean`, `Sunset`, `Monochrome`, etc.) to both engine and UI.
 * [JA] 選択された12色テーマカラープリセットをエンジンおよびUIカラーパレットへ適用します。
 */
fun MasuSheetViewModel.onApplyThemePreset(presetName: String) {
    val baseColors = SpreadsheetColorPalette.THEME_PRESETS[presetName] ?: SpreadsheetColorPalette.DEFAULT_THEME_BASE_COLORS
    val twelveColors = if (baseColors.size >= 12) {
        baseColors.take(12)
    } else {
        baseColors + listOf(
            baseColors.getOrElse(4) { 0xFF4472C4.toInt() },
            baseColors.getOrElse(5) { 0xFFED7D31.toInt() }
        )
    }
    val hexList = twelveColors.joinToString(",") { "\"${SpreadsheetColorPalette.toHexString(it)}\"" }
    dispatchCommand("pageLayout.themeColors", "{\"colors\":[$hexList]}")
    ensureDefaultSalesDashboardChartsMaterialized()
    val sIdx = _uiState.value.viewport.sheetIndex
    val accentColors = baseColors.drop(4).ifEmpty { listOf(0xFF4472C4.toInt(), 0xFFED7D31.toInt(), 0xFF70AD47.toInt(), 0xFFFFC000.toInt()) }
    val updatedObjs = _uiState.value.sheetObjects.map { obj ->
        if (obj.sheetIndex == sIdx && obj.kind == SheetObjectKind.CHART && obj.chartSeries.isNotEmpty()) {
            val recolored = obj.chartSeries.mapIndexed { idx, s ->
                s.copy(color = accentColors[idx % accentColors.size])
            }
            obj.copy(chartSeries = recolored)
        } else {
            obj
        }
    }
    sizeRevisionBump++
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        viewport = _uiState.value.viewport.copy(themeColors = baseColors),
        sheetObjects = updatedObjs,
        statusNoticeMessage = if (isEn) {
            "Applied theme palette '${SpreadsheetI18n.t(presetName, isEn = true)}'"
        } else {
            "テーマ配色「$presetName」を適用しました"
        }
    )
}

/**
 * [EN] Opens or closes the in-app About & Open Source Licenses dialog (`assets/licenses/`).
 * [JA] アプリ同梱のOSSライセンス・著作権表示ダイアログ（`assets/licenses/`）を開閉します。
 */
fun MasuSheetViewModel.onOpenLicensesDialog(open: Boolean = true) {
    _uiState.value = _uiState.value.copy(isOpenSourceLicensesDialogOpen = open)
}
