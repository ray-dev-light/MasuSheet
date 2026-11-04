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
 * [EN] Workbook Sessions, Multi-Window Handoff (`C-04`), Atomic Local/SAF Export (`H-08`), & Theme/Zoom Settings
 * Manages multi-workbook tabs, full-fidelity multi-window detach & merge-back (`C-04`), SAF/local folder save & `.xlsx`/`.csv`/`.pdf` export (`H-08`), sample templates, and UI/theme/zoom preferences.
 *
 * ---
 * [JA / 日本語解説]
 * 複数ブックタブ管理、グラフ・画像・ピボットを完全保持する別ウィンドウ分離・統合 (`C-04`)、SAF／ローカルフォルダ保存とエクスポート (`H-08`)、サンプル読込、UIテーマ・表示倍率設定を担います。
 * ============================================================================
 */

/**
 * [EN] ViewModel action/helper `onEngineReady` handling engine ready while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onEngineReady` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onEngineReady(version: String) {
    val current = _uiState.value
    _uiState.value = current.copy(
        engineVersion = version.ifBlank { "0.14.2" },
        isEngineReady = true,
        isLoading = false
    )
    val langCmd = if (current.language == "en") "app.language.english" else "app.language.japanese"
    dispatchCommand(langCmd)
    val themeMode = if (current.isDarkTheme) "dark" else "light"
    dispatchCommand("view.theme", "{\"mode\":\"$themeMode\"}")
    if (current.zoomPercent != 100) {
        dispatchCommand("view.interfaceScale", "{\"percent\":${current.zoomPercent}}")
    }
}

/**
 * [EN] ViewModel action/helper `onTitleChanged` handling title changed while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onTitleChanged` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onTitleChanged(title: String) {
    val clean = title.trim().ifEmpty { "Book1" }
    preferencesRepository.recordRecentWorkbook(clean)
    _uiState.value = _uiState.value.copy(
        workbookTitle = clean,
        saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(clean),
        recentWorkbooks = preferencesRepository.getRecentWorkbooks()
    )
}

/**
 * [EN] ViewModel action/helper `onDirtyChanged` handling dirty changed while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onDirtyChanged` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onDirtyChanged(dirty: Boolean) {
    _uiState.value = _uiState.value.copy(isDirty = dirty)
}

/**
 * [EN] ViewModel action/helper `onSaveRequestedFromEngine` handling save requested from engine while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSaveRequestedFromEngine` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSaveRequestedFromEngine(fileName: String, base64Payload: String) {
    try {
        val bytes = Base64.getDecoder().decode(base64Payload)
        val safeName = MasuSheetConfig.sanitizeWorkbookFileName(fileName)
        _uiState.value = _uiState.value.copy(
            pendingSaveFileName = safeName,
            pendingSaveBytes = bytes
        )
    } catch (e: Exception) {
        onEngineError("Failed to decode workbook payload: ${e.message}")
    }
}

/**
 * [EN] ViewModel action/helper `onCompletePendingSave` handling complete pending save while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onCompletePendingSave` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onCompletePendingSave(destinationUriString: String): Boolean {
    val current = _uiState.value
    val desiredExt = current.saveDialogExtension.ifBlank { "xlsx" }
    val resolvedRaw = documentGateway.resolveDisplayName(destinationUriString)
    val candidateName = if (resolvedRaw.isNotBlank() && resolvedRaw != "Book1.xlsx") {
        MasuSheetConfig.sanitizeWorkbookFileName(resolvedRaw, desiredExt)
    } else {
        MasuSheetConfig.sanitizeWorkbookFileName(current.pendingSaveFileName ?: current.workbookTitle, desiredExt)
    }
    val finalName = MasuSheetConfig.sanitizeWorkbookFileName(current.pendingSaveFileName ?: candidateName, desiredExt)
    val bytes = current.pendingSaveBytes
        ?: (if (current.pendingSaveFileName != null || current.isSaveDialogVisible) buildExportBytesForFormat(finalName, desiredExt) else null)
        ?: return false
    val ok = documentGateway.writeBytes(destinationUriString, bytes)
    return if (ok) {
        currentActiveSourceUri = destinationUriString
        val savedSnap = engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(finalName)}}")
        preferencesRepository.recordRecentWorkbook(finalName)
        preferencesRepository.recordSavedFileInFolder(current.saveDialogFolderPath, finalName)
        _uiState.value = current.copy(
            workbookTitle = finalName,
            saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(finalName),
            saveDialogExistingFiles = scanExistingFilesInFolder(current.saveDialogFolderPath),
            pendingSaveFileName = null,
            pendingSaveBytes = null,
            isSaveDialogVisible = false,
            lastSavedFileName = finalName,
            statusNoticeMessage = if (current.language == "en") "Saved: $finalName" else "保存しました: $finalName",
            isDirty = false,
            viewport = decorateWithCustomSizes(savedSnap.copy(title = finalName, isDirty = false)),
            recentWorkbooks = preferencesRepository.getRecentWorkbooks()
        )
        saveCurrentWorkbookToSessionList()
        true
    } else {
        _uiState.value = current.copy(
            pendingSaveFileName = null,
            pendingSaveBytes = null,
            errorMessage = if (current.language == "en") "Failed to save file: $finalName" else "ファイルの保存に失敗しました: $finalName"
        )
        false
    }
}

/**
 * [EN] ViewModel action/helper `onCancelPendingSave` handling cancel pending save while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onCancelPendingSave` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onCancelPendingSave() {
    _uiState.value = _uiState.value.copy(
        pendingSaveFileName = null,
        pendingSaveBytes = null,
        isSaveDialogVisible = false
    )
}

// --- Interactive Save Dialog (Select Existing File Name & Choose Extension Without Typing .xlsx) ---

/**
 * [EN] ViewModel action/helper `scanExistingFilesInFolder` handling scan existing files in folder while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `scanExistingFilesInFolder` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.scanExistingFilesInFolder(folderPath: String): List<ExistingFolderFileItem> {
    val items = mutableListOf<ExistingFolderFileItem>()
    val seenNames = mutableSetOf<String>()
    val supportedExts = setOf("xlsx", "csv", "tsv", "ods", "pdf", "xlsm", "xlsb")
    val cleanPath = folderPath.trim().trimEnd('/')

    fun addFileCandidate(f: File) {
        if (!f.isFile || f.name.startsWith(".")) return
        val ext = f.extension.lowercase()
        if (ext in supportedExts && seenNames.add(f.name)) {
            items.add(
                ExistingFolderFileItem(
                    fileName = f.name,
                    baseName = MasuSheetConfig.extractBaseNameWithoutExtension(f.name),
                    extension = ext,
                    fullPath = f.absolutePath
                )
            )
        }
    }

    // 1. Direct filesystem directory scan
    try {
        val dir = File(cleanPath)
        if (dir.exists() && dir.isDirectory) {
            val directFiles = dir.listFiles()
                ?.filter { it.isFile && !it.name.startsWith(".") }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            directFiles.forEach { addFileCandidate(it) }
        }
    } catch (_: Exception) {
    }

    // 2. Android MediaStore / SAF Tree query via DocumentIoGateway (resolves Scoped Storage visibility per folder)
    try {
        for (gwItem in documentGateway.listFolderFiles(cleanPath)) {
            if (gwItem.extension.lowercase() in supportedExts && seenNames.add(gwItem.fileName)) {
                items.add(gwItem)
            }
        }
    } catch (_: Exception) {
    }

    // 3. Per-folder saved files history recorded specifically in this folder
    for (savedInFolder in preferencesRepository.getSavedFilesInFolder(cleanPath)) {
        val clean = MasuSheetConfig.sanitizeWorkbookFileName(savedInFolder, "xlsx")
        val ext = MasuSheetConfig.extractExtensionOrDefault(clean, "xlsx")
        if (ext in supportedExts && seenNames.add(clean)) {
            items.add(
                ExistingFolderFileItem(
                    fileName = clean,
                    baseName = MasuSheetConfig.extractBaseNameWithoutExtension(clean),
                    extension = ext,
                    fullPath = "$cleanPath/$clean"
                )
            )
        }
    }

    return items
}

/**
 * [EN] ViewModel action/helper `buildExportBytesForFormat` handling build export bytes for format while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `buildExportBytesForFormat` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
internal fun MasuSheetViewModel.buildExportBytesForFormat(fileNameWithExt: String, extension: String): ByteArray? {
    val ext = extension.lowercase()
    return when (ext) {
        "pdf" -> engineGateway.exportPdfBytes()
        "csv", "tsv" -> {
            val engineBytes = engineGateway.saveWorkbookBytes(fileNameWithExt)
            if (engineBytes != null && engineBytes.isNotEmpty()) {
                engineBytes
            } else {
                val delim = if (ext == "tsv") '\t' else ','
                val snap = _uiState.value.viewport
                val sb = StringBuilder()
                for (r in 0..30) {
                    var rowHasData = false
                    val rowTokens = mutableListOf<String>()
                    for (c in 0..15) {
                        val txt = snap.cellAt(r, c)?.displayText.orEmpty()
                        if (txt.isNotEmpty()) rowHasData = true
                        rowTokens.add(txt)
                    }
                    if (rowHasData) {
                        sb.append(rowTokens.joinToString(delim.toString())).append('\n')
                    }
                }
                sb.toString().toByteArray()
            }
        }
        else -> engineGateway.saveWorkbookBytes(fileNameWithExt)
    }
}

/**
 * [EN] ViewModel action/helper `onOpenSaveDialog` handling open save dialog while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onOpenSaveDialog` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onOpenSaveDialog(defaultExtension: String = "xlsx", prepareBytesImmediately: Boolean = false) {
    val current = _uiState.value
    val base = MasuSheetConfig.extractBaseNameWithoutExtension(current.workbookTitle)
    val ext = defaultExtension.trim().trimStart('.').lowercase().ifEmpty { "xlsx" }
    val safeName = MasuSheetConfig.buildFileNameWithExtension(base, ext)
    val bytes = if (prepareBytesImmediately) buildExportBytesForFormat(safeName, ext) else null
    val folderPath = current.saveDialogFolderPath
    _uiState.value = current.copy(
        isSaveDialogVisible = true,
        saveDialogBaseName = base,
        saveDialogExtension = ext,
        saveDialogExistingFiles = scanExistingFilesInFolder(folderPath),
        pendingSaveFileName = safeName,
        pendingSaveBytes = bytes
    )
}

/**
 * [EN] ViewModel action/helper `onUpdateSaveDialogBaseName` handling update save dialog base name while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onUpdateSaveDialogBaseName` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onUpdateSaveDialogBaseName(newBaseName: String) {
    val current = _uiState.value
    val safeName = MasuSheetConfig.buildFileNameWithExtension(newBaseName, current.saveDialogExtension)
    _uiState.value = current.copy(
        saveDialogBaseName = newBaseName,
        pendingSaveFileName = safeName
    )
}

/**
 * [EN] ViewModel action/helper `onSelectSaveDialogExtension` handling select save dialog extension while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSelectSaveDialogExtension` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSelectSaveDialogExtension(newExtension: String) {
    val current = _uiState.value
    val ext = newExtension.trim().trimStart('.').lowercase().ifEmpty { "xlsx" }
    val safeName = MasuSheetConfig.buildFileNameWithExtension(current.saveDialogBaseName, ext)
    _uiState.value = current.copy(
        saveDialogExtension = ext,
        pendingSaveFileName = safeName,
        pendingSaveBytes = null
    )
}

/**
 * [EN] ViewModel action/helper `onSelectSaveDialogFolder` handling select save dialog folder while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSelectSaveDialogFolder` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onSelectSaveDialogFolder(folderPath: String) {
    _uiState.value = _uiState.value.copy(
        saveDialogFolderPath = folderPath,
        saveDialogExistingFiles = scanExistingFilesInFolder(folderPath)
    )
}

/**
 * Called when the user clicks an existing file in the Save Dialog's folder list:
 * immediately sets the save filename to that existing file's name without blocking on eager export (`M-09`).
 */
fun MasuSheetViewModel.onSelectExistingFileForSaveName(item: ExistingFolderFileItem) {
    val current = _uiState.value
    val ext = item.extension.ifBlank { current.saveDialogExtension }
    val safeName = MasuSheetConfig.buildFileNameWithExtension(item.baseName, ext)
    _uiState.value = current.copy(
        saveDialogBaseName = item.baseName,
        saveDialogExtension = ext,
        pendingSaveFileName = safeName,
        pendingSaveBytes = null
    )
}

/**
 * [EN] ViewModel action/helper `onConfirmSaveDirectlyToFolder` handling confirm save directly to folder while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onConfirmSaveDirectlyToFolder` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onConfirmSaveDirectlyToFolder(): Boolean {
    val current = _uiState.value
    val finalName = MasuSheetConfig.buildFileNameWithExtension(current.saveDialogBaseName, current.saveDialogExtension)
    val bytes = buildExportBytesForFormat(finalName, current.saveDialogExtension)
    if (bytes == null) {
        _uiState.value = current.copy(
            errorMessage = if (current.language == "en") {
                "Native spreadsheet engine is unavailable for binary export ($finalName). Saved data was protected from overwrite."
            } else {
                "ネイティブ計算エンジンが未ロードのためバイナリ出力を中止し、既存ファイルの上書き破損を防止しました: $finalName"
            }
        )
        return false
    }
    val mimeType = MasuSheetConfig.resolveExportMimeType(current.saveDialogExtension)
    val savedUriStr = documentGateway.saveFileInFolder(
        folderPath = current.saveDialogFolderPath,
        fileName = finalName,
        mimeType = mimeType,
        bytes = bytes
    )
    return if (savedUriStr != null) {
        currentActiveSourceUri = savedUriStr
        val savedSnap = engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(finalName)}}")
        preferencesRepository.recordRecentWorkbook(finalName)
        preferencesRepository.recordSavedFileInFolder(current.saveDialogFolderPath, finalName)
        _uiState.value = current.copy(
            workbookTitle = finalName,
            saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(finalName),
            saveDialogExistingFiles = scanExistingFilesInFolder(current.saveDialogFolderPath),
            pendingSaveFileName = null,
            pendingSaveBytes = null,
            isSaveDialogVisible = false,
            lastSavedFileName = finalName,
            statusNoticeMessage = if (current.language == "en") "Saved: $finalName" else "保存しました: $finalName",
            isDirty = false,
            viewport = decorateWithCustomSizes(savedSnap.copy(title = finalName, isDirty = false)),
            recentWorkbooks = preferencesRepository.getRecentWorkbooks()
        )
        saveCurrentWorkbookToSessionList()
        true
    } else {
        _uiState.value = current.copy(
            pendingSaveFileName = null,
            pendingSaveBytes = null,
            errorMessage = if (current.language == "en") "Failed to save file: $finalName" else "ファイルの保存に失敗しました: $finalName"
        )
        false
    }
}

/**
 * [EN] ViewModel action/helper `onDismissSaveDialog` handling dismiss save dialog while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onDismissSaveDialog` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onDismissSaveDialog() {
    _uiState.value = _uiState.value.copy(isSaveDialogVisible = false)
}

/**
 * [EN] ViewModel action/helper `onDismissStatusNotice` handling dismiss status notice while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onDismissStatusNotice` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onDismissStatusNotice() {
    _uiState.value = _uiState.value.copy(statusNoticeMessage = null)
}

/**
 * [EN] ViewModel action/helper `saveCurrentWorkbookToSessionList` handling save current workbook to session list while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `saveCurrentWorkbookToSessionList` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.saveCurrentWorkbookToSessionList(includeSerializedBytes: Boolean = false) {
    val cur = _uiState.value
    currentWorkbookIdForStacks = cur.activeWorkbookId
    val existing = cur.openWorkbooks.firstOrNull { it.id == cur.activeWorkbookId }
    val serializedBytes = if (includeSerializedBytes) {
        runCatching { engineGateway.saveWorkbookBytes(cur.workbookTitle) }.getOrNull() ?: existing?.savedWorkbookBytes
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
 * [EN] ViewModel action/helper `onCreateNewBlankWorkbookSession` handling create new blank workbook session while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onCreateNewBlankWorkbookSession` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onSwitchOpenWorkbook` handling switch open workbook while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSwitchOpenWorkbook` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onCloseOpenWorkbook` handling close open workbook while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onCloseOpenWorkbook` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
        val exported = runCatching { engineGateway.saveWorkbookBytes(target.title) }.getOrNull()
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
 * [EN] `C-04`: Restores a full [OpenWorkbookSession] (including charts, images, shapes, PivotTables,
 *      custom styles, column widths, row heights, and tab colors) into this isolated window.
 * [JA] 別ウィンドウへ移動された [OpenWorkbookSession] の全オブジェクト・ピボット・書式を欠落なく復元します。
 */
fun MasuSheetViewModel.onLoadHandoffWorkbookSession(session: OpenWorkbookSession) {
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
 * [EN] ViewModel action/helper `onLoadHandoffWorkbook` handling load handoff workbook while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onLoadHandoffWorkbook` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] `C-04`: Merges a full [OpenWorkbookSession] back from `SecondaryWorkbookActivity` into this
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
 * [EN] ViewModel action/helper `onMergeHandoffWorkbookIntoCurrentWindow` handling merge handoff workbook into current window while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onMergeHandoffWorkbookIntoCurrentWindow` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onToggleSplitBookView` handling toggle split book view while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onToggleSplitBookView` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onToggleSplitWorkbookView` handling toggle split workbook view while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onToggleSplitWorkbookView` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onToggleSplitWorkbookView(enable: Boolean? = null) {
    onToggleSplitBookView(enable)
}

/**
 * [EN] ViewModel action/helper `getSplitSecondaryWorkbookSession` handling get split secondary workbook session while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `getSplitSecondaryWorkbookSession` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.getSplitSecondaryWorkbookSession(): OpenWorkbookSession? {
    val cur = _uiState.value
    return cur.openWorkbooks.lastOrNull { it.id != cur.activeWorkbookId }
        ?: cur.openWorkbooks.lastOrNull()
}

/**
 * [EN] ViewModel action/helper `onCreateBlankWorkbook` handling create blank workbook while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onCreateBlankWorkbook` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onCreateBlankWorkbook() {
    onCreateNewBlankWorkbookSession()
}

/**
 * [EN] ViewModel action/helper `onLoadSampleTemplate` handling load sample template while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onLoadSampleTemplate` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onLoadSampleTemplate(sampleName: String) {
    val norm = if (sampleName.equals("monthly_budget", ignoreCase = true)) "budget" else sampleName
    onLoadSampleWorkbook(norm)
}

/**
 * [EN] ViewModel action/helper `onOpenDocumentUri` handling open document uri while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onOpenDocumentUri` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onOpenDocumentUri(uriString: String): Boolean {
    val bytes = documentGateway.readBytes(uriString)
    if (bytes == null || bytes.isEmpty()) {
        _uiState.value = _uiState.value.copy(errorMessage = "ファイルを読み込めませんでした")
        return false
    }
    saveCurrentWorkbookToSessionList()
    val existingByUri = _uiState.value.openWorkbooks.firstOrNull {
        it.sourceUri.isNotBlank() && it.sourceUri == uriString
    }
    if (existingByUri != null && (existingByUri.viewportSnapshot.isDirty || (existingByUri.id == _uiState.value.activeWorkbookId && _uiState.value.isDirty))) {
        return onSwitchOpenWorkbook(existingByUri.id)
    }
    val rawName = documentGateway.resolveDisplayName(uriString)
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
    _uiState.value = _uiState.value.copy(
        activeWorkbookId = nextBookId,
        workbookTitle = safeName,
        definedNames = snap.definedNames.associate { it.name to it.refersTo },
        saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(safeName),
        saveDialogExtension = MasuSheetConfig.extractExtensionOrDefault(safeName, "xlsx"),
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
        recentWorkbooks = preferencesRepository.getRecentWorkbooks(),
        viewport = snap,
        errorMessage = snap.error
    )
    saveCurrentWorkbookToSessionList()
    return true
}

/**
 * [EN] ViewModel action/helper `onTriggerSave` handling trigger save while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onTriggerSave` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onTriggerSave() {
    onOpenSaveDialog("xlsx", prepareBytesImmediately = true)
}

/**
 * [EN] ViewModel action/helper `onTriggerExportPdf` handling trigger export pdf while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onTriggerExportPdf` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onTriggerExportPdf() {
    onOpenSaveDialog("pdf", prepareBytesImmediately = true)
}

/**
 * [EN] ViewModel action/helper `onLoadSampleWorkbook` handling load sample workbook while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onLoadSampleWorkbook` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onLoadNextSample` handling load next sample while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onLoadNextSample` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onLoadNextSample() {
    val next = WorkbookFormatClassifier.nextSampleWorkbook(_uiState.value.activeSample)
    onLoadSampleWorkbook(next)
}

/**
 * [EN] ViewModel action/helper `onLoadRepairChecklistSample` handling load repair checklist sample while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onLoadRepairChecklistSample` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onLoadRepairChecklistSample() {
    onLoadSampleWorkbook("repair")
}

/**
 * [EN] ViewModel action/helper `onToggleLanguage` handling toggle language while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onToggleLanguage` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onToggleDarkTheme` handling toggle dark theme while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onToggleDarkTheme` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onToggleDarkTheme() {
    val nextDark = !_uiState.value.isDarkTheme
    preferencesRepository.setDarkTheme(nextDark)
    _uiState.value = _uiState.value.copy(isDarkTheme = nextDark)
    val mode = if (nextDark) "dark" else "light"
    dispatchCommand("view.theme", "{\"mode\":\"$mode\"}")
}

/**
 * [EN] ViewModel action/helper `onToggleMaterial3Ui` handling toggle material3 ui while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onToggleMaterial3Ui` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onCycleZoom` handling cycle zoom while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onCycleZoom` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onSetZoomPercent` handling set zoom percent while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onSetZoomPercent` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onOpenZoomDialog` handling open zoom dialog while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onOpenZoomDialog` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
 * [EN] ViewModel action/helper `onToggleToolbar` handling toggle toolbar while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onToggleToolbar` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onToggleToolbar() {
    _uiState.value = _uiState.value.copy(toolbarVisible = !_uiState.value.toolbarVisible)
}

/**
 * [EN] ViewModel action/helper `onEngineError` handling engine error while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onEngineError` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onEngineError(error: String) {
    _uiState.value = _uiState.value.copy(
        isLoading = false,
        errorMessage = error.trim().ifEmpty { "Unknown error" }
    )
}

/**
 * [EN] ViewModel action/helper `onDismissError` handling dismiss error while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onDismissError` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onDismissError() {
    _uiState.value = _uiState.value.copy(errorMessage = null)
}

// --- Spreadsheet Color Picker & Theme System ---

/**
 * [EN] ViewModel action/helper `onRecordRecentColor` handling record recent color while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onRecordRecentColor` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
 */
fun MasuSheetViewModel.onRecordRecentColor(argb: Int) {
    if (argb != 0) {
        preferencesRepository.recordRecentColor(argb)
        _uiState.value = _uiState.value.copy(recentColors = preferencesRepository.getRecentColors())
    }
}

/**
 * Applies a color (`argb`, where `0` = No Fill / Automatic) to the specified target:
 * `"fill"`, `"font"`, `"border"`, `"objectFill"`, `"objectStroke"`, `"objectText"`, `"chartSeries"`, or `"sheetTab"`.
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
 * [EN] ViewModel action/helper `onApplyThemePreset` handling apply theme preset while keeping [MasuSheetUiState] and Undo history consistent.
 * [JA] [MasuSheetViewModel] の `onApplyThemePreset` アクション／ヘルパー処理を実行し、UI状態と履歴の整合性を保ちます。
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
    _uiState.value = _uiState.value.copy(
        viewport = _uiState.value.viewport.copy(themeColors = baseColors),
        sheetObjects = updatedObjs,
        statusNoticeMessage = "テーマ配色「$presetName」を適用しました"
    )
}
