package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlin.math.abs
import kotlin.math.roundToInt
import com.masusheet.app.MasuSheetViewModel.*

/**
 * ============================================================================
 * [EN] Sheet Tab Lifecycle, Find & Replace, GoTo Special, Goal Seek, Comments, Hyperlinks, & Defined Names
 * Manages sheet insert/delete/rename/duplicate (`M-11` cloning objects)/hide/move, Find & Replace, GoTo Special, numerical Goal Seek, cell comments, hyperlinks, and defined names.
 *
 * ---
 * [JA / 日本語解説]
 * シートの追加・削除・名前変更・オブジェクト継承付き複製 (`M-11`)・非表示・移動、検索と置換、条件を選択してジャンプ、ゴールシーク、コメント、ハイパーリンク、名前の定義を担います。
 * ============================================================================
 */

/**
 * [EN] ViewModel action/helper `shiftSheetIndexedStateOnInsert` handling shift sheet indexed state on insert while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `shiftSheetIndexedStateOnInsert` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.shiftSheetIndexedStateOnInsert(insertedAt: Int) {
    val cur = _uiState.value
    fun mapIdx(oldIdx: Int): Int = if (oldIdx >= insertedAt) oldIdx + 1 else oldIdx

    val newColWidths = customColWidths.entries.associate { (k, v) -> (mapIdx(k.first) to k.second) to v }
    customColWidths.clear()
    customColWidths.putAll(newColWidths)

    val newRowHeights = customRowHeights.entries.associate { (k, v) -> (mapIdx(k.first) to k.second) to v }
    customRowHeights.clear()
    customRowHeights.putAll(newRowHeights)

    val newWrapCells = customWrapCells.entries.associate { (k, v) -> (mapIdx(k.first) to k.second) to v }
    customWrapCells.clear()
    customWrapCells.putAll(newWrapCells)

    val newCellStyles = customCellStyles.entries.associate { (k, v) -> (mapIdx(k.first) to k.second) to v }
    customCellStyles.clear()
    customCellStyles.putAll(newCellStyles)

    val newMerges = customSheetMerges.entries.associate { (k, v) -> mapIdx(k) to v }
    customSheetMerges.clear()
    customSheetMerges.putAll(newMerges)

    val remappedObjects = cur.sheetObjects.map { obj -> obj.copy(sheetIndex = mapIdx(obj.sheetIndex)) }
    val remappedPivots = cur.pivotTables.map { p ->
        p.copy(
            sourceSheetIndex = mapIdx(p.sourceSheetIndex),
            targetSheetIndex = mapIdx(p.targetSheetIndex)
        )
    }
    val remappedAutoFilters = cur.autoFilterRanges.entries.associate { (k, v) -> mapIdx(k) to v }
    val remappedColFilters = cur.activeColumnFilters.entries.associate { (k, v) -> (mapIdx(k.first) to k.second) to v }
    val remappedHiddenRows = cur.filteredHiddenRows.entries.associate { (k, v) -> mapIdx(k) to v }
    val remappedTabColors = cur.sheetTabColors.entries.associate { (k, v) -> mapIdx(k) to v }
    val remappedLeftAlign = cur.explicitLeftAlignCells.map { (s, p) -> mapIdx(s) to p }.toSet()
    val remappedTopAlign = cur.explicitTopAlignCells.map { (s, p) -> mapIdx(s) to p }.toSet()
    val remappedComments = cur.cellComments.entries.associate { (k, v) -> (mapIdx(k.first) to k.second) to v }
    val remappedHyperlinks = cur.cellHyperlinks.entries.associate { (k, v) -> (mapIdx(k.first) to k.second) to v }
    val remappedFindMatches = cur.findMatches.map { m -> m.copy(sheetIndex = mapIdx(m.sheetIndex)) }

    sizeRevisionBump++
    _uiState.value = cur.copy(
        sheetObjects = remappedObjects,
        pivotTables = remappedPivots,
        autoFilterRanges = remappedAutoFilters,
        activeColumnFilters = remappedColFilters,
        filteredHiddenRows = remappedHiddenRows,
        sheetTabColors = remappedTabColors,
        explicitLeftAlignCells = remappedLeftAlign,
        explicitTopAlignCells = remappedTopAlign,
        cellComments = remappedComments,
        cellHyperlinks = remappedHyperlinks,
        findMatches = remappedFindMatches
    )
}

/**
 * [EN] ViewModel action/helper `shiftSheetIndexedStateOnDelete` handling shift sheet indexed state on delete while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `shiftSheetIndexedStateOnDelete` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.shiftSheetIndexedStateOnDelete(deletedAt: Int) {
    val cur = _uiState.value
    fun mapIdx(oldIdx: Int): Int? = when {
        oldIdx == deletedAt -> null
        oldIdx > deletedAt -> oldIdx - 1
        else -> oldIdx
    }

    val newColWidths = customColWidths.entries.mapNotNull { (k, v) -> mapIdx(k.first)?.let { (it to k.second) to v } }.toMap()
    customColWidths.clear()
    customColWidths.putAll(newColWidths)

    val newRowHeights = customRowHeights.entries.mapNotNull { (k, v) -> mapIdx(k.first)?.let { (it to k.second) to v } }.toMap()
    customRowHeights.clear()
    customRowHeights.putAll(newRowHeights)

    val newWrapCells = customWrapCells.entries.mapNotNull { (k, v) -> mapIdx(k.first)?.let { (it to k.second) to v } }.toMap()
    customWrapCells.clear()
    customWrapCells.putAll(newWrapCells)

    val newCellStyles = customCellStyles.entries.mapNotNull { (k, v) -> mapIdx(k.first)?.let { (it to k.second) to v } }.toMap()
    customCellStyles.clear()
    customCellStyles.putAll(newCellStyles)

    val newMerges = customSheetMerges.entries.mapNotNull { (k, v) -> mapIdx(k)?.let { it to v } }.toMap()
    customSheetMerges.clear()
    customSheetMerges.putAll(newMerges)

    val remappedObjects = cur.sheetObjects.mapNotNull { obj -> mapIdx(obj.sheetIndex)?.let { obj.copy(sheetIndex = it) } }
    val remappedPivots = cur.pivotTables.mapNotNull { p ->
        val newTarget = mapIdx(p.targetSheetIndex) ?: return@mapNotNull null
        val newSource = mapIdx(p.sourceSheetIndex) ?: return@mapNotNull null
        p.copy(sourceSheetIndex = newSource, targetSheetIndex = newTarget)
    }
    val nextActivePivotId = cur.activePivotTableId?.takeIf { id -> remappedPivots.any { it.id == id } }
    val nextPivotPaneOpen = cur.isPivotFieldsPaneOpen && nextActivePivotId != null
    val remappedAutoFilters = cur.autoFilterRanges.entries.mapNotNull { (k, v) -> mapIdx(k)?.let { it to v } }.toMap()
    val remappedColFilters = cur.activeColumnFilters.entries.mapNotNull { (k, v) -> mapIdx(k.first)?.let { (it to k.second) to v } }.toMap()
    val remappedHiddenRows = cur.filteredHiddenRows.entries.mapNotNull { (k, v) -> mapIdx(k)?.let { it to v } }.toMap()
    val remappedTabColors = cur.sheetTabColors.entries.mapNotNull { (k, v) -> mapIdx(k)?.let { it to v } }.toMap()
    val remappedLeftAlign = cur.explicitLeftAlignCells.mapNotNull { (s, p) -> mapIdx(s)?.let { it to p } }.toSet()
    val remappedTopAlign = cur.explicitTopAlignCells.mapNotNull { (s, p) -> mapIdx(s)?.let { it to p } }.toSet()
    val remappedComments = cur.cellComments.entries.mapNotNull { (k, v) -> mapIdx(k.first)?.let { (it to k.second) to v } }.toMap()
    val remappedHyperlinks = cur.cellHyperlinks.entries.mapNotNull { (k, v) -> mapIdx(k.first)?.let { (it to k.second) to v } }.toMap()
    val remappedFindMatches = cur.findMatches.mapNotNull { m -> mapIdx(m.sheetIndex)?.let { m.copy(sheetIndex = it) } }

    sizeRevisionBump++
    _uiState.value = cur.copy(
        sheetObjects = remappedObjects,
        pivotTables = remappedPivots,
        activePivotTableId = nextActivePivotId,
        isPivotFieldsPaneOpen = nextPivotPaneOpen,
        autoFilterRanges = remappedAutoFilters,
        activeColumnFilters = remappedColFilters,
        filteredHiddenRows = remappedHiddenRows,
        sheetTabColors = remappedTabColors,
        explicitLeftAlignCells = remappedLeftAlign,
        explicitTopAlignCells = remappedTopAlign,
        cellComments = remappedComments,
        cellHyperlinks = remappedHyperlinks,
        findMatches = remappedFindMatches
    )
}

/**
 * [EN] ViewModel action/helper `onAddSheet` handling add sheet while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onAddSheet` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onAddSheet() {
    val sheetCount = _uiState.value.viewport.sheetNames.size.coerceAtLeast(1)
    shiftSheetIndexedStateOnInsert(sheetCount)
    val snap = engineGateway.executeCommand("home.insertSheet", "{\"before\":$sheetCount}")
    applyViewportSnapshot(snap)
    _uiState.value = _uiState.value.copy(
        activePivotTableId = null,
        isPivotFieldsPaneOpen = false
    )
}

/**
 * [EN] ViewModel action/helper `onStepSheet` handling step sheet while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onStepSheet` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onStepSheet(delta: Int) {
    val cmd = if (delta >= 0) "sheet.next" else "sheet.previous"
    val snap = engineGateway.executeCommand(cmd, "{}")
    applyViewportSnapshot(snap)
    val cur = _uiState.value
    val sIdx = cur.viewport.sheetIndex
    val hitPivot = cur.pivotTables.firstOrNull {
        it.containsCell(sIdx, cur.viewport.activeRow, cur.viewport.activeCol)
    }
    val hasPivotOnSheet = cur.pivotTables.any { it.targetSheetIndex == sIdx }
    _uiState.value = cur.copy(
        activePivotTableId = hitPivot?.id,
        isPivotFieldsPaneOpen = cur.isPivotFieldsPaneOpen && hasPivotOnSheet
    )
}

/**
 * [EN] ViewModel action/helper `onInsertSheetBefore` handling insert sheet before while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onInsertSheetBefore` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onInsertSheetBefore(sheetIndex: Int) {
    val sheetCount = _uiState.value.viewport.sheetNames.size.coerceAtLeast(1)
    val clamped = sheetIndex.coerceIn(0, sheetCount)
    shiftSheetIndexedStateOnInsert(clamped)
    val snap = engineGateway.executeCommand("home.insertSheet", "{\"before\":$clamped}")
    applyViewportSnapshot(snap)
    _uiState.value = _uiState.value.copy(
        activePivotTableId = null,
        isPivotFieldsPaneOpen = false
    )
}

/**
 * [EN] ViewModel action/helper `onDeleteSheetAt` handling delete sheet at while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onDeleteSheetAt` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onDeleteSheetAt(sheetIndex: Int) {
    val names = _uiState.value.viewport.sheetNames
    if (names.size <= 1) {
        val isEn = _uiState.value.language == "en"
        val deletedName = names.firstOrNull().orEmpty().ifBlank { "Sheet1" }
        // Insert a fresh blank sheet at index 1, delete the old sheet at index 0, and rename the new sheet to Sheet1
        shiftSheetIndexedStateOnInsert(1)
        engineGateway.executeCommand("home.insertSheet", "{\"before\":1}")
        shiftSheetIndexedStateOnDelete(0)
        engineGateway.executeCommand("home.deleteSheet", "{\"sheet\":0}")
        engineGateway.executeCommand("sheet.rename", "{\"sheet\":0,\"name\":\"Sheet1\"}")
        val snap = engineGateway.executeCommand("selection.set", "{\"active\":\"A1\",\"range\":\"A1\"}")
        applyViewportSnapshot(snap)
        _uiState.value = _uiState.value.copy(
            selectedObjectId = null,
            activePivotTableId = null,
            isPivotFieldsPaneOpen = false,
            statusNoticeMessage = if (isEn) {
                "Deleted worksheet '$deletedName' and opened a new blank sheet (Sheet1)"
            } else {
                "シート「$deletedName」を削除し、新しい空白シート (Sheet1) を開きました"
            }
        )
        return
    }
    val clamped = sheetIndex.coerceIn(0, names.lastIndex)
    shiftSheetIndexedStateOnDelete(clamped)
    val snap = engineGateway.executeCommand("home.deleteSheet", "{\"sheet\":$clamped}")
    applyViewportSnapshot(snap)
}

/**
 * [EN] ViewModel action/helper `onRenameSheetAt` handling rename sheet at while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onRenameSheetAt` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onRenameSheetAt(sheetIndex: Int, newName: String) {
    val clean = newName.trim()
    if (clean.isEmpty()) return
    val snap = engineGateway.executeCommand("sheet.rename", "{\"sheet\":$sheetIndex,\"name\":${JSONObject.quote(clean)}}")
    applyViewportSnapshot(snap)
    val cur = _uiState.value
    if (cur.pivotTables.any { it.sourceSheetIndex == sheetIndex }) {
        _uiState.value = cur.copy(
            pivotTables = cur.pivotTables.map { p ->
                if (p.sourceSheetIndex == sheetIndex) p.copy(sourceSheetName = clean) else p
            }
        )
    }
}

/**
 * [EN] ViewModel action/helper `onDuplicateSheetAt` handling duplicate sheet at while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onDuplicateSheetAt` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onDuplicateSheetAt(sheetIndex: Int) {
    val insertPos = (sheetIndex + 1).coerceIn(0, _uiState.value.viewport.sheetNames.size)
    shiftSheetIndexedStateOnInsert(insertPos)
    // Copy custom styles/widths/heights/merges AND floating objects/comments/links/tabColor from source sheetIndex to insertPos (`M-11`)
    customColWidths.entries.filter { it.key.first == sheetIndex }.forEach { (k, v) -> customColWidths[insertPos to k.second] = v }
    customRowHeights.entries.filter { it.key.first == sheetIndex }.forEach { (k, v) -> customRowHeights[insertPos to k.second] = v }
    customWrapCells.entries.filter { it.key.first == sheetIndex }.forEach { (k, v) -> customWrapCells[insertPos to k.second] = v }
    customCellStyles.entries.filter { it.key.first == sheetIndex }.forEach { (k, v) -> customCellStyles[insertPos to k.second] = v }
    customSheetMerges[sheetIndex]?.let { customSheetMerges[insertPos] = it.toList() }

    val curAfterShift = _uiState.value
    val clonedObjects = curAfterShift.sheetObjects
        .filter { it.sheetIndex == sheetIndex }
        .map { obj -> obj.copy(id = "obj_${nextObjectCounter++}", sheetIndex = insertPos) }
    val clonedComments = curAfterShift.cellComments
        .filterKeys { it.first == sheetIndex }
        .entries.associate { (k, v) -> (insertPos to k.second) to v }
    val clonedHyperlinks = curAfterShift.cellHyperlinks
        .filterKeys { it.first == sheetIndex }
        .entries.associate { (k, v) -> (insertPos to k.second) to v }
    val clonedTabColors = curAfterShift.sheetTabColors.toMutableMap().apply {
        curAfterShift.sheetTabColors[sheetIndex]?.let { put(insertPos, it) }
    }

    val snap = engineGateway.executeCommand("sheet.move", "{\"sheet\":$sheetIndex,\"to\":$insertPos,\"copy\":true}")
    applyViewportSnapshot(snap)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        sheetObjects = _uiState.value.sheetObjects + clonedObjects,
        cellComments = _uiState.value.cellComments + clonedComments,
        cellHyperlinks = _uiState.value.cellHyperlinks + clonedHyperlinks,
        sheetTabColors = clonedTabColors,
        statusNoticeMessage = if (isEn) "Duplicated worksheet" else "シートのコピーを作成しました"
    )
}

/**
 * [EN] ViewModel action/helper `onHideSheetAt` handling hide sheet at while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onHideSheetAt` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onHideSheetAt(sheetIndex: Int) {
    val snap = engineGateway.executeCommand("sheet.hide", "{\"sheet\":$sheetIndex}")
    applyViewportSnapshot(snap)
}

/**
 * Moves a worksheet tab from [fromIndex] to [toIndex] and remaps all sheet-indexed state maps.
 */
fun MasuSheetViewModel.onMoveSheet(fromIndex: Int, toIndex: Int) {
    val cur = _uiState.value
    val sheetCount = cur.viewport.sheetNames.size
    if (sheetCount <= 1) return
    val from = fromIndex.coerceIn(0, sheetCount - 1)
    val to = toIndex.coerceIn(0, sheetCount - 1)
    if (from == to) return

    fun remapSheetIndex(oldIdx: Int): Int = when {
        oldIdx == from -> to
        from < to && oldIdx in (from + 1)..to -> oldIdx - 1
        from > to && oldIdx in to until from -> oldIdx + 1
        else -> oldIdx
    }

    val movedSheetName = cur.viewport.sheetNames.getOrElse(from) { "Sheet${from + 1}" }

    // Remap internal private maps
    val newColWidths = customColWidths.entries.associate { (k, v) -> (remapSheetIndex(k.first) to k.second) to v }
    customColWidths.clear()
    customColWidths.putAll(newColWidths)

    val newRowHeights = customRowHeights.entries.associate { (k, v) -> (remapSheetIndex(k.first) to k.second) to v }
    customRowHeights.clear()
    customRowHeights.putAll(newRowHeights)

    val newWrapCells = customWrapCells.entries.associate { (k, v) -> (remapSheetIndex(k.first) to k.second) to v }
    customWrapCells.clear()
    customWrapCells.putAll(newWrapCells)

    val newCellStyles = customCellStyles.entries.associate { (k, v) -> (remapSheetIndex(k.first) to k.second) to v }
    customCellStyles.clear()
    customCellStyles.putAll(newCellStyles)

    val newMerges = customSheetMerges.entries.associate { (k, v) -> remapSheetIndex(k) to v }
    customSheetMerges.clear()
    customSheetMerges.putAll(newMerges)

    // Remap UI state sheet-indexed structures
    val remappedObjects = cur.sheetObjects.map { obj -> obj.copy(sheetIndex = remapSheetIndex(obj.sheetIndex)) }
    val remappedPivots = cur.pivotTables.map { p ->
        p.copy(
            sourceSheetIndex = remapSheetIndex(p.sourceSheetIndex),
            targetSheetIndex = remapSheetIndex(p.targetSheetIndex)
        )
    }
    val remappedAutoFilters = cur.autoFilterRanges.entries.associate { (k, v) -> remapSheetIndex(k) to v }
    val remappedColFilters = cur.activeColumnFilters.entries.associate { (k, v) -> (remapSheetIndex(k.first) to k.second) to v }
    val remappedHiddenRows = cur.filteredHiddenRows.entries.associate { (k, v) -> remapSheetIndex(k) to v }
    val remappedTabColors = cur.sheetTabColors.entries.associate { (k, v) -> remapSheetIndex(k) to v }
    val remappedLeftAlign = cur.explicitLeftAlignCells.map { (s, p) -> remapSheetIndex(s) to p }.toSet()
    val remappedTopAlign = cur.explicitTopAlignCells.map { (s, p) -> remapSheetIndex(s) to p }.toSet()
    val remappedComments = cur.cellComments.entries.associate { (k, v) -> (remapSheetIndex(k.first) to k.second) to v }
    val remappedHyperlinks = cur.cellHyperlinks.entries.associate { (k, v) -> (remapSheetIndex(k.first) to k.second) to v }
    val remappedFindMatches = cur.findMatches.map { m -> m.copy(sheetIndex = remapSheetIndex(m.sheetIndex)) }

    _uiState.value = cur.copy(
        isDirty = true,
        sheetObjects = remappedObjects,
        pivotTables = remappedPivots,
        autoFilterRanges = remappedAutoFilters,
        activeColumnFilters = remappedColFilters,
        filteredHiddenRows = remappedHiddenRows,
        sheetTabColors = remappedTabColors,
        explicitLeftAlignCells = remappedLeftAlign,
        explicitTopAlignCells = remappedTopAlign,
        cellComments = remappedComments,
        cellHyperlinks = remappedHyperlinks,
        findMatches = remappedFindMatches
    )

    sizeRevisionBump++
    val snap = engineGateway.executeCommand("sheet.move", "{\"sheet\":$from,\"to\":$to,\"copy\":false}")
    applyViewportSnapshot(snap)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        statusNoticeMessage = if (isEn) {
            "Moved worksheet '$movedSheetName' to position ${to + 1}"
        } else {
            "シート「$movedSheetName」を ${to + 1} 番目へ移動しました"
        }
    )
}

/**
 * [EN] ViewModel action/helper `collectSheetFindMatches` handling collect sheet find matches while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `collectSheetFindMatches` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.collectSheetFindMatches(
    query: String,
    matchCase: Boolean = false,
    wholeCell: Boolean = false
): List<FindMatchItem> {
    val clean = query.trim()
    if (clean.isEmpty()) return emptyList()
    val vp = _uiState.value.viewport
    val byCoord = LinkedHashMap<Pair<Int, Int>, FindMatchItem>()

    // 1. Query the Rust engine's full-sheet Find (`edit.find` with `all = true`) so off-viewport cells are also discovered
    try {
        val prevR0 = vp.selStartRow
        val prevC0 = vp.selStartCol
        val prevR1 = vp.selEndRow
        val prevC1 = vp.selEndCol
        val findParams = JSONObject().apply {
            put("what", clean)
            put("matchCase", matchCase)
            put("wholeCell", wholeCell)
            put("all", true)
        }.toString()
        val findSnap = engineGateway.executeCommand("edit.find", findParams)
        engineGateway.executeCommand(
            "selection.set",
            "{\"startRow\":$prevR0,\"startCol\":$prevC0,\"endRow\":$prevR1,\"endCol\":$prevC1}"
        )
        val resJson = findSnap.resultJson.orEmpty()
        if (resJson.isNotBlank() && resJson.startsWith("{")) {
            val resObj = JSONObject(resJson)
            val arr = resObj.optJSONArray("matches")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val sIdx = m.optInt("sheet", vp.sheetIndex)
                    if (sIdx != vp.sheetIndex) continue
                    val addr = m.optString("cell", "")
                    val (r, c) = parseSingleCellRef(addr) ?: continue
                    val txt = m.optString("text", "")
                    val vpCell = vp.cellAt(r, c)
                    byCoord[r to c] = FindMatchItem(
                        sheetIndex = vp.sheetIndex,
                        sheetName = vp.sheetName,
                        row = r,
                        col = c,
                        address = MasuSheetConfig.formatCellAddress(r, c),
                        displayText = vpCell?.displayText?.ifEmpty { txt } ?: txt,
                        editText = vpCell?.editText?.ifEmpty { txt } ?: txt
                    )
                }
            }
        }
    } catch (_: Exception) {
    }

    // 2. Also include any viewport cells (e.g. formatted display text or spilled values)
    val sortedCells = vp.cells.values
        .filter { it.displayText.isNotEmpty() || it.editText.isNotEmpty() }
        .sortedWith(compareBy<NativeCellModel> { it.row }.thenBy { it.col })

    for (cell in sortedCells) {
        val disp = cell.displayText
        val edit = cell.editText
        val matched = if (wholeCell) {
            disp.equals(clean, ignoreCase = !matchCase) || edit.equals(clean, ignoreCase = !matchCase)
        } else {
            disp.contains(clean, ignoreCase = !matchCase) || edit.contains(clean, ignoreCase = !matchCase)
        }
        if (matched) {
            byCoord[cell.row to cell.col] = FindMatchItem(
                sheetIndex = vp.sheetIndex,
                sheetName = vp.sheetName,
                row = cell.row,
                col = cell.col,
                address = MasuSheetConfig.formatCellAddress(cell.row, cell.col),
                displayText = disp,
                editText = edit.ifEmpty { disp }
            )
        }
    }

    return byCoord.values.sortedWith(compareBy<FindMatchItem> { it.row }.thenBy { it.col })
}

/**
 * [EN] ViewModel action/helper `onFindAllCells` handling find all cells while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onFindAllCells` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onFindAllCells(
    query: String,
    matchCase: Boolean = false,
    wholeCell: Boolean = false
): List<FindMatchItem> {
    val clean = query.trim()
    val isEn = _uiState.value.language == "en"
    if (clean.isEmpty()) {
        _uiState.value = _uiState.value.copy(
            findMatches = emptyList(),
            currentFindMatchIndex = -1,
            lastFindSummary = if (isEn) "Enter search text" else "検索する文字列を入力してください",
            statusNoticeMessage = if (isEn) "Enter search text" else "検索する文字列を入力してください"
        )
        return emptyList()
    }
    val matches = collectSheetFindMatches(clean, matchCase, wholeCell)
    if (matches.isEmpty()) {
        val msg = if (isEn) "No cells found matching '$clean'" else "「$clean」に一致するセルは見つかりませんでした"
        _uiState.value = _uiState.value.copy(
            findMatches = emptyList(),
            currentFindMatchIndex = -1,
            lastFindSummary = msg,
            statusNoticeMessage = msg
        )
        return emptyList()
    }
    val first = matches.first()
    onSelectCell(first.row, first.col)
    val summary = if (isEn) {
        "Found ${matches.size} cell(s) matching '$clean' (1/${matches.size}: ${first.address})"
    } else {
        "「$clean」が ${matches.size} 件見つかりました (1/${matches.size}: ${first.address})"
    }
    _uiState.value = _uiState.value.copy(
        findMatches = matches,
        currentFindMatchIndex = 0,
        lastFindSummary = summary,
        statusNoticeMessage = summary
    )
    return matches
}

/**
 * [EN] ViewModel action/helper `onFindNext` handling find next while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onFindNext` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onFindNext(
    query: String,
    forward: Boolean = true,
    matchCase: Boolean = false,
    wholeCell: Boolean = false
): FindMatchItem? {
    val clean = query.trim()
    val isEn = _uiState.value.language == "en"
    if (clean.isEmpty()) {
        val msg = if (isEn) "Enter search text" else "検索する文字列を入力してください"
        _uiState.value = _uiState.value.copy(
            lastFindSummary = msg,
            statusNoticeMessage = msg
        )
        return null
    }
    val matches = collectSheetFindMatches(clean, matchCase, wholeCell)
    if (matches.isEmpty()) {
        val msg = if (isEn) "No cells found matching '$clean'" else "「$clean」に一致するセルは見つかりませんでした"
        _uiState.value = _uiState.value.copy(
            findMatches = emptyList(),
            currentFindMatchIndex = -1,
            lastFindSummary = msg,
            statusNoticeMessage = msg
        )
        return null
    }
    val curVp = _uiState.value.viewport
    val curR = curVp.activeRow
    val curC = curVp.activeCol
    val targetIdx = if (forward) {
        val afterIdx = matches.indexOfFirst { m ->
            m.row > curR || (m.row == curR && m.col > curC)
        }
        if (afterIdx >= 0) afterIdx else 0
    } else {
        val beforeIdx = matches.indexOfLast { m ->
            m.row < curR || (m.row == curR && m.col < curC)
        }
        if (beforeIdx >= 0) beforeIdx else matches.lastIndex
    }
    val target = matches[targetIdx]
    onSelectCell(target.row, target.col)
    val summary = if (isEn) {
        "Match ${targetIdx + 1}/${matches.size}: ${target.address} = ${target.displayText}"
    } else {
        "検索結果 ${targetIdx + 1}/${matches.size} 件目: ${target.address} (${target.displayText})"
    }
    _uiState.value = _uiState.value.copy(
        findMatches = matches,
        currentFindMatchIndex = targetIdx,
        lastFindSummary = summary,
        statusNoticeMessage = summary
    )
    return target
}

/**
 * [EN] ViewModel action/helper `onSelectFindMatch` handling select find match while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSelectFindMatch` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSelectFindMatch(index: Int) {
    val cur = _uiState.value
    val match = cur.findMatches.getOrNull(index) ?: return
    if (match.sheetIndex != cur.viewport.sheetIndex) {
        onSelectSheet(match.sheetIndex)
    }
    onSelectCell(match.row, match.col)
    val isEn = cur.language == "en"
    val summary = if (isEn) {
        "Selected match ${index + 1}/${cur.findMatches.size}: ${match.address} (${match.displayText})"
    } else {
        "検索結果 ${index + 1}/${cur.findMatches.size} 件目を選択: ${match.address} (${match.displayText})"
    }
    _uiState.value = _uiState.value.copy(
        currentFindMatchIndex = index,
        lastFindSummary = summary,
        statusNoticeMessage = summary
    )
}

/**
 * [EN] ViewModel action/helper `replaceSubstringInCellText` handling replace substring in cell text while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `replaceSubstringInCellText` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.replaceSubstringInCellText(
    source: String,
    query: String,
    replacement: String,
    matchCase: Boolean,
    wholeCell: Boolean
): String {
    return if (wholeCell) {
        if (source.equals(query, ignoreCase = !matchCase)) replacement else source
    } else {
        source.replace(query, replacement, ignoreCase = !matchCase)
    }
}

/**
 * [EN] ViewModel action/helper `onReplaceCurrentCell` handling replace current cell while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onReplaceCurrentCell` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onReplaceCurrentCell(
    query: String,
    replacement: String,
    matchCase: Boolean = false,
    wholeCell: Boolean = false
): Int {
    val clean = query.trim()
    val isEn = _uiState.value.language == "en"
    if (clean.isEmpty()) return 0
    val matches = collectSheetFindMatches(clean, matchCase, wholeCell)
    if (matches.isEmpty()) {
        val msg = if (isEn) "No cells found matching '$clean'" else "「$clean」に一致するセルは見つかりませんでした"
        _uiState.value = _uiState.value.copy(
            findMatches = emptyList(),
            currentFindMatchIndex = -1,
            lastFindSummary = msg,
            statusNoticeMessage = msg
        )
        return 0
    }
    val vp = _uiState.value.viewport
    val currentMatch = matches.firstOrNull { it.row == vp.activeRow && it.col == vp.activeCol }
        ?: onFindNext(clean, forward = true, matchCase = matchCase, wholeCell = wholeCell)
        ?: return 0

    val rawBase = if (currentMatch.editText.startsWith("=") && !currentMatch.editText.contains(clean, ignoreCase = !matchCase)) {
        currentMatch.displayText
    } else {
        currentMatch.editText.ifEmpty { currentMatch.displayText }
    }
    val replacedText = replaceSubstringInCellText(rawBase, clean, replacement, matchCase, wholeCell)
    pushCustomOverlayUndoSnapshot()
    val snap = engineGateway.executeCommand(
        "cell.set",
        "{\"cell\":\"${currentMatch.address}\",\"input\":${JSONObject.quote(replacedText)}}"
    )
    applyViewportSnapshot(snap)

    val remaining = collectSheetFindMatches(clean, matchCase, wholeCell)
    val nextIdx = if (remaining.isNotEmpty()) 0 else -1
    if (remaining.isNotEmpty()) {
        onSelectCell(remaining[0].row, remaining[0].col)
    }
    val msg = if (isEn) {
        "Replaced '${currentMatch.address}' with '$replacement' (${remaining.size} remaining)"
    } else {
        "セル ${currentMatch.address} を「$replacement」に置換しました (残り ${remaining.size} 件)"
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        findMatches = remaining,
        currentFindMatchIndex = nextIdx,
        lastFindSummary = msg,
        statusNoticeMessage = msg
    )
    return 1
}

/**
 * [EN] ViewModel action/helper `onReplaceAllCells` handling replace all cells while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onReplaceAllCells` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onReplaceAllCells(
    query: String,
    replacement: String,
    matchCase: Boolean = false,
    wholeCell: Boolean = false
): Int {
    val clean = query.trim()
    val isEn = _uiState.value.language == "en"
    if (clean.isEmpty()) return 0
    val matches = collectSheetFindMatches(clean, matchCase, wholeCell)
    if (matches.isEmpty()) {
        val msg = if (isEn) "No cells found matching '$clean'" else "「$clean」に一致するセルは見つかりませんでした"
        _uiState.value = _uiState.value.copy(
            findMatches = emptyList(),
            currentFindMatchIndex = -1,
            lastFindSummary = msg,
            statusNoticeMessage = msg
        )
        return 0
    }
    pushCustomOverlayUndoSnapshot()
    // B4: Delegate bulk replacement to the Rust engine's atomic `edit.replace` (`all = true`) so it records a single Undo step
    // and covers off-viewport cells; only fall back to per-cell `cell.set` for cells where `displayText` matched a formula result.
    val replaceParams = JSONObject().apply {
        put("what", clean)
        put("with", replacement)
        put("matchCase", matchCase)
        put("wholeCell", wholeCell)
        put("all", true)
    }.toString()
    var lastSnap = engineGateway.executeCommand("edit.replace", replaceParams)
    for (m in matches) {
        if (m.editText.startsWith("=") && !m.editText.contains(clean, ignoreCase = !matchCase)) {
            val nextVal = replaceSubstringInCellText(m.displayText, clean, replacement, matchCase, wholeCell)
            lastSnap = engineGateway.executeCommand(
                "cell.set",
                "{\"cell\":\"${m.address}\",\"input\":${JSONObject.quote(nextVal)}}"
            )
        }
    }
    applyViewportSnapshot(lastSnap)
    val first = matches.first()
    onSelectCell(first.row, first.col)
    val msg = if (isEn) {
        "Replaced ${matches.size} occurrence(s) of '$clean' with '$replacement'"
    } else {
        "「$clean」を「$replacement」にすべて置換しました (${matches.size} 件)"
    }
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        findMatches = emptyList(),
        currentFindMatchIndex = -1,
        lastFindSummary = msg,
        statusNoticeMessage = msg
    )
    return matches.size
}

/**
 * Navigates to a cell, range, `Sheet!A1:B5`, or defined name (matching Spreadsheet's Go To / F5 and Name Box).
 */
fun MasuSheetViewModel.onGoToReference(reference: String): Boolean {
    val raw = reference.trim()
    if (raw.isEmpty()) return false
    val cur = _uiState.value
    val resolved = cur.definedNames.entries.firstOrNull { it.key.equals(raw, ignoreCase = true) }?.value ?: raw
    val sheetAndRef = if (resolved.contains('!')) {
        val parts = resolved.split('!', limit = 2)
        parts[0].trim().removeSurrounding("'") to parts[1].trim()
    } else {
        null to resolved.trim()
    }
    val targetSheetName = sheetAndRef.first
    if (!targetSheetName.isNullOrEmpty()) {
        val idx = cur.viewport.sheetNames.indexOfFirst { it.equals(targetSheetName, ignoreCase = true) }
        if (idx >= 0 && idx != cur.viewport.sheetIndex) {
            onSelectSheet(idx)
        }
    }
    val ok = onNavigateToAddressOrRange(sheetAndRef.second)
    val isEn = _uiState.value.language == "en"
    if (ok) {
        _uiState.value = _uiState.value.copy(
            statusNoticeMessage = if (isEn) "Navigated to $resolved" else "参照先「$resolved」へジャンプしました"
        )
    } else {
        _uiState.value = _uiState.value.copy(
            statusNoticeMessage = if (isEn) "Invalid reference: $raw" else "参照先「$raw」が見つかりません"
        )
    }
    return ok
}

/**
 * Implements Spreadsheet's Go To Special (`条件を選択してジャンプ`): formulas, constants, blanks, comments, lastCell, visible.
 */
fun MasuSheetViewModel.onGoToSpecial(kind: String): Int {
    val cur = _uiState.value
    val vp = cur.viewport
    val sIdx = vp.sheetIndex
    val isEn = cur.language == "en"
    val normalized = kind.lowercase().trim()
    val matches = mutableListOf<FindMatchItem>()

    when {
        normalized.contains("formula") || normalized.contains("数式") -> {
            vp.cells.values
                .filter { it.editText.trim().startsWith("=") }
                .sortedWith(compareBy<NativeCellModel> { it.row }.thenBy { it.col })
                .forEach { c ->
                    matches.add(
                        FindMatchItem(
                            sheetIndex = sIdx,
                            sheetName = vp.sheetName,
                            row = c.row,
                            col = c.col,
                            address = MasuSheetConfig.formatCellAddress(c.row, c.col),
                            displayText = c.displayText,
                            editText = c.editText
                        )
                    )
                }
        }
        normalized.contains("constant") || normalized.contains("定数") -> {
            vp.cells.values
                .filter { it.displayText.isNotBlank() && !it.editText.trim().startsWith("=") }
                .sortedWith(compareBy<NativeCellModel> { it.row }.thenBy { it.col })
                .forEach { c ->
                    matches.add(
                        FindMatchItem(
                            sheetIndex = sIdx,
                            sheetName = vp.sheetName,
                            row = c.row,
                            col = c.col,
                            address = MasuSheetConfig.formatCellAddress(c.row, c.col),
                            displayText = c.displayText,
                            editText = c.editText.ifEmpty { c.displayText }
                        )
                    )
                }
        }
        normalized.contains("comment") || normalized.contains("note") || normalized.contains("コメント") -> {
            cur.cellComments.entries
                .filter { it.key.first == sIdx && it.value.isNotBlank() }
                .forEach { (key, comment) ->
                    val r = (key.second ushr 32).toInt()
                    val c = key.second.toInt()
                    matches.add(
                        FindMatchItem(
                            sheetIndex = sIdx,
                            sheetName = vp.sheetName,
                            row = r,
                            col = c,
                            address = MasuSheetConfig.formatCellAddress(r, c),
                            displayText = comment,
                            editText = comment
                        )
                    )
                }
        }
        normalized.contains("blank") || normalized.contains("空白") -> {
            val bounds = detectTableBoundsAt(vp, vp.activeRow, vp.activeCol)
            for (r in bounds[0]..bounds[2]) {
                for (c in bounds[3]..bounds[4]) {
                    val txt = vp.cellAt(r, c)?.displayText?.trim().orEmpty()
                    if (txt.isEmpty()) {
                        matches.add(
                            FindMatchItem(
                                sheetIndex = sIdx,
                                sheetName = vp.sheetName,
                                row = r,
                                col = c,
                                address = MasuSheetConfig.formatCellAddress(r, c),
                                displayText = "(空白)",
                                editText = ""
                            )
                        )
                    }
                }
            }
        }
        else -> {
            val nonBlank = vp.cells.values.filter { it.displayText.isNotBlank() || it.editText.isNotBlank() }
            val maxR = nonBlank.maxOfOrNull { it.row } ?: vp.activeRow
            val maxC = nonBlank.maxOfOrNull { it.col } ?: vp.activeCol
            val cell = vp.cellAt(maxR, maxC)
            matches.add(
                FindMatchItem(
                    sheetIndex = sIdx,
                    sheetName = vp.sheetName,
                    row = maxR,
                    col = maxC,
                    address = MasuSheetConfig.formatCellAddress(maxR, maxC),
                    displayText = cell?.displayText.orEmpty(),
                    editText = cell?.editText.orEmpty()
                )
            )
        }
    }

    if (matches.isNotEmpty()) {
        val first = matches.first()
        onSelectCell(first.row, first.col)
        val msg = if (isEn) {
            "Go To Special ($kind): found ${matches.size} cell(s) — selected ${first.address}"
        } else {
            "条件を選択してジャンプ ($kind): ${matches.size} 件見つかりました (${first.address} を選択)"
        }
        _uiState.value = _uiState.value.copy(
            findMatches = matches,
            currentFindMatchIndex = 0,
            lastFindSummary = msg,
            statusNoticeMessage = msg
        )
    } else {
        val msg = if (isEn) "No matching cells found for Go To Special ($kind)" else "該当するセル ($kind) は見つかりませんでした"
        _uiState.value = _uiState.value.copy(
            findMatches = emptyList(),
            currentFindMatchIndex = -1,
            lastFindSummary = msg,
            statusNoticeMessage = msg
        )
    }
    return matches.size
}

/**
 * B3: Unified Goal Seek (`data.goalSeek`) that invokes the Rust engine's real secant+bisection solver (`whatif.rs`)
 * on dependent formulas first, falling back to linear ratio approximation only if the target cell has no formula dependency.
 */
fun MasuSheetViewModel.onRunGoalSeek(setCellRef: String, toValue: Double, byChangingCellRef: String): Boolean {
    val cur = _uiState.value
    val vp = cur.viewport
    val targetCoord = parseSingleCellRef(setCellRef) ?: (vp.activeRow to vp.activeCol)
    val changingCoord = parseSingleCellRef(byChangingCellRef) ?: targetCoord
    val changingAddr = MasuSheetConfig.formatCellAddress(changingCoord.first, changingCoord.second)
    val targetAddr = MasuSheetConfig.formatCellAddress(targetCoord.first, targetCoord.second)

    pushCustomOverlayUndoSnapshot()

    // 1. Try the Rust engine's exact secant+bisection solver (`data.goalSeek`)
    val gsParams = JSONObject().apply {
        put("set", targetAddr)
        put("to", toValue)
        put("changing", changingAddr)
    }.toString()
    val gsSnap = engineGateway.executeCommand("data.goalSeek", gsParams)
    var converged = false
    var solvedVal = Double.NaN
    try {
        val gsResJson = gsSnap.resultJson.orEmpty()
        if (gsResJson.isNotBlank() && gsResJson.startsWith("{")) {
            val resObj = JSONObject(gsResJson)
            converged = resObj.optBoolean("converged", false)
            if (resObj.has("value")) {
                solvedVal = resObj.optDouble("value", Double.NaN)
            }
        }
    } catch (_: Exception) {
    }

    val finalSnap = if (converged && !solvedVal.isNaN()) {
        gsSnap
    } else {
        // Fallback ratio approximation when target cell is not a formula referencing changing cell
        val currentTargetVal = vp.cellAt(targetCoord.first, targetCoord.second)?.displayText
            ?.replace(",", "")?.replace("¥", "")?.replace("$", "")?.trim()?.toDoubleOrNull() ?: 0.0
        val currentChangingVal = vp.cellAt(changingCoord.first, changingCoord.second)?.displayText
            ?.replace(",", "")?.replace("¥", "")?.replace("$", "")?.trim()?.toDoubleOrNull() ?: 1.0

        solvedVal = if (currentTargetVal != 0.0 && currentChangingVal != 0.0 && targetCoord != changingCoord) {
            currentChangingVal * (toValue / currentTargetVal)
        } else {
            toValue
        }
        val formattedFallback = if (solvedVal == Math.floor(solvedVal)) solvedVal.toLong().toString() else String.format(java.util.Locale.US, "%.2f", solvedVal)
        engineGateway.executeCommand(
            "cell.set",
            "{\"cell\":\"$changingAddr\",\"input\":${JSONObject.quote(formattedFallback)}}"
        )
    }

    val formattedSolved = if (solvedVal == Math.floor(solvedVal)) {
        solvedVal.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", solvedVal)
    }
    applyViewportSnapshot(finalSnap)
    onSelectCell(changingCoord.first, changingCoord.second)
    val isEn = _uiState.value.language == "en"
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        statusNoticeMessage = if (isEn) {
            "Goal Seek solved: set $changingAddr = $formattedSolved for $targetAddr → $toValue"
        } else {
            "ゴールシーク完了: $targetAddr = $toValue にするため $changingAddr = $formattedSolved を算出しました"
        }
    )
    return true
}

/**
 * B5: Unified Comment, Hyperlink, and Defined Name mutations that sync directly with the Rust engine
 * (`review.newComment`, `review.deleteComment`, `insert.link`, `formulas.defineName`) so `.xlsx` save/load
 * and formula evaluation (`=MyName * 2`) share a single source of truth.
 */
fun MasuSheetViewModel.onSetCellComment(comment: String) {
    val cur = _uiState.value
    val vp = cur.viewport
    val key = vp.sheetIndex to NativeViewportSnapshot.packCellKey(vp.activeRow, vp.activeCol)
    val addr = MasuSheetConfig.formatCellAddress(vp.activeRow, vp.activeCol)
    val clean = comment.trim()
    pushCustomOverlayUndoSnapshot()
    val snap = if (clean.isEmpty()) {
        engineGateway.executeCommand("review.deleteComment", "{\"cell\":\"$addr\"}")
    } else {
        engineGateway.executeCommand(
            "review.newComment",
            "{\"cell\":\"$addr\",\"author\":\"User\",\"text\":${JSONObject.quote(clean)}}"
        )
    }
    val updated = cur.cellComments.toMutableMap()
    if (clean.isEmpty()) {
        updated.remove(key)
    } else {
        updated[key] = clean
    }
    applyViewportSnapshot(snap)
    val isEn = cur.language == "en"
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        cellComments = updated,
        showAllComments = true,
        statusNoticeMessage = if (clean.isEmpty()) {
            if (isEn) "Deleted comment on $addr" else "セル $addr のコメントを削除しました"
        } else {
            if (isEn) "Saved comment on $addr: $clean" else "セル $addr にコメント「$clean」を保存しました"
        }
    )
}

/**
 * [EN] ViewModel action/helper `onSetCellHyperlink` handling set cell hyperlink while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSetCellHyperlink` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSetCellHyperlink(urlOrRef: String, displayLabel: String = "") {
    val cur = _uiState.value
    val vp = cur.viewport
    val key = vp.sheetIndex to NativeViewportSnapshot.packCellKey(vp.activeRow, vp.activeCol)
    val addr = MasuSheetConfig.formatCellAddress(vp.activeRow, vp.activeCol)
    val cleanUrl = urlOrRef.trim()
    pushCustomOverlayUndoSnapshot()
    val updated = cur.cellHyperlinks.toMutableMap()
    if (cleanUrl.isEmpty()) {
        updated.remove(key)
    } else {
        updated[key] = cleanUrl
    }
    val snap = engineGateway.executeCommand(
        "insert.link",
        JSONObject().apply {
            put("cell", addr)
            put("target", cleanUrl)
            put("display", displayLabel.trim())
        }.toString()
    )
    applyViewportSnapshot(snap)
    val isEn = cur.language == "en"
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        cellHyperlinks = updated,
        statusNoticeMessage = if (cleanUrl.isEmpty()) {
            if (isEn) "Removed hyperlink on $addr" else "セル $addr のハイパーリンクを解除しました"
        } else {
            if (isEn) "Linked $addr → $cleanUrl" else "セル $addr にリンク ($cleanUrl) を設定しました"
        }
    )
}

/**
 * [EN] ViewModel action/helper `onDefineName` handling define name while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onDefineName` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onDefineName(name: String, refersTo: String) {
    val cleanName = name.trim()
    val cleanRef = refersTo.trim()
    if (cleanName.isEmpty() || cleanRef.isEmpty()) return
    val cur = _uiState.value
    val formulaExpr = if (cleanRef.startsWith("=")) cleanRef else "=$cleanRef"
    val snap = engineGateway.executeCommand(
        "formulas.defineName",
        JSONObject().apply {
            put("name", cleanName)
            put("refersTo", formulaExpr)
            put("formula", formulaExpr)
        }.toString()
    )
    val updated = cur.definedNames.toMutableMap()
    updated[cleanName] = cleanRef.removePrefix("=")
    applyViewportSnapshot(snap)
    val isEn = cur.language == "en"
    _uiState.value = _uiState.value.copy(
        isDirty = true,
        definedNames = updated,
        statusNoticeMessage = if (isEn) "Defined name '$cleanName' = $cleanRef" else "名前定義「$cleanName」($cleanRef) を登録しました"
    )
}

/**
 * [EN] ViewModel action/helper `parseSingleCellRef` handling parse single cell ref while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `parseSingleCellRef` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.parseSingleCellRef(ref: String): Pair<Int, Int>? {
    val cleaned = ref.trim().substringAfter('!').substringBefore(':')
    return MasuSheetConfig.parseCellAddressStrict(cleaned)
}

/**
 * Navigates to a cell or range typed into the Formula Bar Name Box (e.g., `D18` or `A1:D10`), matching Spreadsheet's Name Box behavior.
 */
fun MasuSheetViewModel.onNavigateToAddressOrRange(addressInput: String): Boolean {
    val clean = addressInput.trim().uppercase()
    if (clean.isEmpty()) return false
    return if (clean.contains(':')) {
        val parts = clean.split(':', limit = 2)
        val start = parseSingleCellRef(parts[0]) ?: return false
        val end = parseSingleCellRef(parts[1]) ?: return false
        onSelectCell(start.first, start.second, end.first, end.second)
        true
    } else {
        val cell = parseSingleCellRef(clean) ?: return false
        onSelectCell(cell.first, cell.second)
        true
    }
}
