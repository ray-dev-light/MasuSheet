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
 * [EN] Workbook Sessions, Multi-Window Handoff, Atomic Local/SAF Export, & Theme/Zoom Settings
 * Manages multi-workbook tabs, full-fidelity multi-window detach & merge-back, SAF/local folder save & `.xlsx`/`.csv`/`.pdf` export, sample templates, and UI/theme/zoom preferences.
 *
 * ---
 * [JA / 日本語解説]
 * 複数ブックタブ管理、グラフ・画像・ピボットを完全保持する別ウィンドウ分離・統合、SAF／ローカルフォルダ保存とエクスポート、サンプル読込、UIテーマ・表示倍率設定を担います。
 * ============================================================================
 */

/**
 * [EN] Marks the spreadsheet engine as initialized with `version` and clears the initial loading state.
 * [JA] 計算エンジンの初期化完了とバージョン文字列をUI状態へ反映し、ローディング表示を解除します。
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
 * [EN] Updates the active workbook title, syncs the default Save Dialog base name, and records the title in recent workbooks.
 * [JA] 現在のブック名を更新し、保存ダイアログの初期ファイル名および最近使ったブック履歴へ反映します。
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
 * [EN] Updates the unsaved-changes (`isDirty`) indicator flag on `MasuSheetUiState`.
 * [JA] 未保存の変更があるかどうかを示す `isDirty` フラグを更新します。
 */
fun MasuSheetViewModel.onDirtyChanged(dirty: Boolean) {
    _uiState.value = _uiState.value.copy(isDirty = dirty)
}

/**
 * [EN] Decodes Base64 export bytes emitted by the engine, embeds floating objects/pivots for `.xlsx`/`.xlsm`, and stages `pendingSaveBytes`.
 * [JA] エンジンから渡されたBase64保存データをデコードし、`.xlsx`/`.xlsm` 時はグラフ・ピボット情報を埋め込んで保存待機状態にセットします。
 */
fun MasuSheetViewModel.onSaveRequestedFromEngine(fileName: String, base64Payload: String) {
    try {
        val rawBytes = Base64.getDecoder().decode(base64Payload)
        val safeName = MasuSheetConfig.sanitizeWorkbookFileName(fileName)
        val ext = MasuSheetConfig.extractExtensionOrDefault(safeName, "xlsx").lowercase()
        val finalBytes = if (ext in setOf("xlsx", "xlsm")) {
            WorkbookXlsxObjectPersistence.embedIntoXlsxBytes(rawBytes, buildCurrentWorkbookPersistenceSession())
        } else {
            rawBytes
        }
        _uiState.value = _uiState.value.copy(
            pendingSaveFileName = safeName,
            pendingSaveBytes = finalBytes
        )
    } catch (e: Exception) {
        onEngineError("Failed to decode workbook payload: ${e.message}")
    }
}

/**
 * [EN] Writes staged/exported workbook bytes to `destinationUriString` via `documentGateway`, updating workbook title, `sourceUri`, and clean state.
 * [JA] 選択された保存先URI `destinationUriString` へブックデータを書き出し、ブック名・保存先URI・保存済み状態を更新します。
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
 * [EN] Non-blocking coroutine entry point for [onCompletePendingSave] performing SAF/file stream writes and folder scanning on `Dispatchers.IO`.
 * [JA] SAF／ファイル書込およびフォルダ走査を `Dispatchers.IO` で非同期実行し、UIスレッドのブロック（ANR）を防止します。
 */
fun MasuSheetViewModel.onCompletePendingSaveAsync(destinationUriString: String) {
    val current = _uiState.value
    val desiredExt = current.saveDialogExtension.ifBlank { "xlsx" }
    val fallbackCandidate = MasuSheetConfig.sanitizeWorkbookFileName(
        current.pendingSaveFileName ?: current.workbookTitle,
        desiredExt
    )
    val preparedBytes = current.pendingSaveBytes
        ?: (if (current.pendingSaveFileName != null || current.isSaveDialogVisible) {
            buildExportBytesForFormat(fallbackCandidate, desiredExt)
        } else {
            null
        })
    if (preparedBytes == null) {
        onCompletePendingSave(destinationUriString)
        return
    }
    val originWorkbookId = current.activeWorkbookId
    val folderPath = current.saveDialogFolderPath
    _uiState.value = current.copy(isLoading = true)
    viewModelScope.launch {
        val ioOutcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val resolvedRaw = documentGateway.resolveDisplayName(destinationUriString)
            val candidateName = if (resolvedRaw.isNotBlank() && resolvedRaw != "Book1.xlsx") {
                MasuSheetConfig.sanitizeWorkbookFileName(resolvedRaw, desiredExt)
            } else {
                fallbackCandidate
            }
            val finalName = MasuSheetConfig.sanitizeWorkbookFileName(current.pendingSaveFileName ?: candidateName, desiredExt)
            val ok = documentGateway.writeBytes(destinationUriString, preparedBytes)
            val scanned = if (ok) scanExistingFilesInFolder(folderPath) else emptyList()
            Triple(ok, finalName, scanned)
        }
        val (ok, finalName, scannedFiles) = ioOutcome
        val latest = _uiState.value
        if (ok) {
            preferencesRepository.recordRecentWorkbook(finalName)
            preferencesRepository.recordSavedFileInFolder(folderPath, finalName)
            // [EN] If the user switched workbook tabs while I/O was running, update the background tab entry without overwriting the newly active workbook's title or URI.
            // [JA] 保存I/O中に別ブックタブへ切り替わっていた場合、現在表示中の別ブック名を上書きせず元のブックセッションのみ更新します。
            if (latest.activeWorkbookId != originWorkbookId) {
                val updatedTabs = latest.openWorkbooks.map { wb ->
                    if (wb.id == originWorkbookId) {
                        wb.copy(
                            title = finalName,
                            sourceUri = destinationUriString,
                            viewportSnapshot = wb.viewportSnapshot.copy(title = finalName, isDirty = false)
                        )
                    } else {
                        wb
                    }
                }
                _uiState.value = latest.copy(
                    isLoading = false,
                    openWorkbooks = updatedTabs,
                    pendingSaveFileName = null,
                    pendingSaveBytes = null,
                    isSaveDialogVisible = false,
                    lastSavedFileName = finalName,
                    statusNoticeMessage = if (latest.language == "en") "Saved: $finalName" else "保存しました: $finalName",
                    recentWorkbooks = preferencesRepository.getRecentWorkbooks()
                )
                return@launch
            }
            currentActiveSourceUri = destinationUriString
            val savedSnap = engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(finalName)}}")
            _uiState.value = latest.copy(
                isLoading = false,
                workbookTitle = finalName,
                saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(finalName),
                saveDialogExistingFiles = scannedFiles,
                pendingSaveFileName = null,
                pendingSaveBytes = null,
                isSaveDialogVisible = false,
                lastSavedFileName = finalName,
                statusNoticeMessage = if (latest.language == "en") "Saved: $finalName" else "保存しました: $finalName",
                isDirty = false,
                viewport = decorateWithCustomSizes(savedSnap.copy(title = finalName, isDirty = false)),
                recentWorkbooks = preferencesRepository.getRecentWorkbooks()
            )
            saveCurrentWorkbookToSessionList()
        } else {
            _uiState.value = latest.copy(
                isLoading = false,
                pendingSaveFileName = null,
                pendingSaveBytes = null,
                errorMessage = if (latest.language == "en") "Failed to save file: $finalName" else "ファイルの保存に失敗しました: $finalName"
            )
        }
    }
}

/**
 * [EN] Clears staged pending-save bytes and closes the Save / Export dialog without writing to storage.
 * [JA] 保留中の保存データ状態をクリアし、ストレージ書き込みを行わずに保存ダイアログを閉じます。
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
 * [EN] Lists existing spreadsheet/PDF files in `folderPath` (merging `documentGateway.listFolderFiles` and readable local directory entries) for quick overwrite selection.
 * [JA] 指定フォルダ内の既存スプレッドシート／PDFファイルを走査し、保存ダイアログでの一覧表示・上書き選択用に返します。
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

    // 1. Direct filesystem directory scan (guarded against private internal app-data directories)
    try {
        if (documentGateway.isSafeLocalFolderPath(cleanPath)) {
            val dir = File(cleanPath)
            if (dir.exists() && dir.isDirectory) {
                val directFiles = dir.listFiles()
                    ?.filter { it.isFile && !it.name.startsWith(".") }
                    ?.sortedByDescending { it.lastModified() }
                    .orEmpty()
                directFiles.forEach { addFileCandidate(it) }
            }
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

internal fun MasuSheetViewModel.buildCurrentWorkbookPersistenceSession(): OpenWorkbookSession {
    val cur = _uiState.value
    return OpenWorkbookSession(
        id = cur.activeWorkbookId,
        nativeSessionId = engineGateway.activeSessionId,
        sourceUri = "",
        title = cur.workbookTitle,
        sampleKind = cur.activeSample,
        savedWorkbookBytes = null,
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
}

internal fun MasuSheetViewModel.advanceObjectCounterPast(objects: List<NativeSheetObject>) {
    var maxSeen = nextObjectCounter - 1
    for (obj in objects) {
        val trailingNum = obj.id.substringAfterLast('_').toIntOrNull()
        if (trailingNum != null && trailingNum > maxSeen) {
            maxSeen = trailingNum
        }
    }
    if (maxSeen + 1 > nextObjectCounter) {
        nextObjectCounter = maxSeen + 1
    }
}

/**
 * [EN] Exports the active workbook as `.xlsx`/`.xlsm` (with embedded OOXML charts/images + lossless overlay), `.pdf`, `.csv`, `.tsv`, or `.ods` bytes.
 * [JA] 指定拡張子（グラフ・画像・ピボット完全保持の `.xlsx`/`.xlsm`、または `.pdf`・`.csv`・`.tsv`・`.ods`）の保存用バイト列を生成します。
 */
internal fun MasuSheetViewModel.buildExportBytesForFormat(fileNameWithExt: String, extension: String): ByteArray? {
    val ext = extension.lowercase()
    ensureDefaultSalesDashboardChartsMaterialized()
    engineGateway.syncWorkbookObjects(_uiState.value.sheetObjects)
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
        "xlsx", "xlsm" -> {
            val rawBytes = engineGateway.saveWorkbookBytes(fileNameWithExt) ?: return null
            WorkbookXlsxObjectPersistence.embedIntoXlsxBytes(rawBytes, buildCurrentWorkbookPersistenceSession())
        }
        else -> engineGateway.saveWorkbookBytes(fileNameWithExt)
    }
}

/**
 * [EN] Opens the Save / Export dialog initialized with `defaultExtension` and existing files in the target folder.
 * [JA] 指定された既定拡張子（`xlsx` や `pdf` など）と保存先フォルダ内の既存ファイル一覧をセットして保存ダイアログを開きます。
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
 * [EN] Updates the base filename (without extension) in the Save Dialog and refreshes `pendingSaveFileName`.
 * [JA] 保存ダイアログのファイル名（拡張子を除くベース名）を更新します。
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
 * [EN] Changes the target file format extension (`xlsx`, `pdf`, `csv`, `tsv`, `ods`) inside the Save Dialog.
 * [JA] 保存ダイアログの出力ファイル形式（`xlsx`・`pdf`・`csv`・`tsv`・`ods`）を切り替えます。
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
 * [EN] Switches the target folder in the Save Dialog and refreshes its existing-files list.
 * [JA] 保存ダイアログの保存先フォルダを切り替え、そのフォルダ内の既存ファイル一覧を再取得します。
 */
fun MasuSheetViewModel.onSelectSaveDialogFolder(folderPath: String) {
    _uiState.value = _uiState.value.copy(
        saveDialogFolderPath = folderPath,
        saveDialogExistingFiles = scanExistingFilesInFolder(folderPath)
    )
}

/**
 * [EN] Populates the Save Dialog filename and extension from a tapped `ExistingFolderFileItem` without blocking on eager export.
 * [JA] 保存ダイアログ内の既存ファイル項目をタップした際、エクスポート処理でブロックすることなくファイル名と拡張子を即座に欄へ反映します。
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
 * [EN] Exports the active workbook in the chosen format and saves it directly into `saveDialogFolderPath` (falling back to SAF picker only if required).
 * [JA] 選択された形式でブックデータを生成し、指定フォルダへ直接保存します（権限不足等で直接書込不可の場合のみシステムピッカーへ誘導）。
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
 * [EN] Opens the Save Dialog immediately on the UI thread and scans existing folder files asynchronously on `Dispatchers.IO`.
 * [JA] 保存ダイアログを即座に表示し、既存ファイル一覧の取得（MediaStore / SAF 走査）を `Dispatchers.IO` で非同期実行します。
 */
fun MasuSheetViewModel.onOpenSaveDialogAsync(defaultExtension: String = "xlsx") {
    val current = _uiState.value
    val base = MasuSheetConfig.extractBaseNameWithoutExtension(current.workbookTitle)
    val ext = defaultExtension.trim().trimStart('.').lowercase().ifEmpty { "xlsx" }
    val safeName = MasuSheetConfig.buildFileNameWithExtension(base, ext)
    val folderPath = current.saveDialogFolderPath
    _uiState.value = current.copy(
        isSaveDialogVisible = true,
        saveDialogBaseName = base,
        saveDialogExtension = ext,
        pendingSaveFileName = safeName,
        pendingSaveBytes = null
    )
    viewModelScope.launch {
        val existing = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            scanExistingFilesInFolder(folderPath)
        }
        if (_uiState.value.saveDialogFolderPath == folderPath) {
            _uiState.value = _uiState.value.copy(saveDialogExistingFiles = existing)
        }
    }
}

/**
 * [EN] Updates the target folder in the Save Dialog and scans its spreadsheet files asynchronously on `Dispatchers.IO`.
 * [JA] 保存先フォルダを切り替え、フォルダ内ファイル一覧の走査を `Dispatchers.IO` で非同期実行します。
 */
fun MasuSheetViewModel.onSelectSaveDialogFolderAsync(folderPath: String, onScanCompleted: (() -> Unit)? = null) {
    _uiState.value = _uiState.value.copy(saveDialogFolderPath = folderPath)
    viewModelScope.launch {
        val existing = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            scanExistingFilesInFolder(folderPath)
        }
        if (_uiState.value.saveDialogFolderPath == folderPath) {
            _uiState.value = _uiState.value.copy(saveDialogExistingFiles = existing)
        }
        onScanCompleted?.invoke()
    }
}

/**
 * [EN] Exports workbook bytes and writes them to the selected folder on `Dispatchers.IO` without blocking the UI thread.
 * [JA] ブックバイナリのパッケージングおよびフォルダへの保存処理を `Dispatchers.IO` で非同期実行します。
 */
fun MasuSheetViewModel.onConfirmSaveDirectlyToFolderAsync() {
    val current = _uiState.value
    val ext = current.saveDialogExtension.lowercase()
    val finalName = MasuSheetConfig.buildFileNameWithExtension(current.saveDialogBaseName, ext)
    ensureDefaultSalesDashboardChartsMaterialized()
    engineGateway.syncWorkbookObjects(current.sheetObjects)
    val rawBytes: ByteArray? = when (ext) {
        "pdf" -> engineGateway.exportPdfBytes()
        "csv", "tsv" -> buildExportBytesForFormat(finalName, ext)
        else -> engineGateway.saveWorkbookBytes(finalName)
    }
    if (rawBytes == null) {
        _uiState.value = current.copy(
            errorMessage = if (current.language == "en") {
                "Native spreadsheet engine is unavailable for binary export ($finalName). Saved data was protected from overwrite."
            } else {
                "ネイティブ計算エンジンが未ロードのためバイナリ出力を中止し、既存ファイルの上書き破損を防止しました: $finalName"
            }
        )
        return
    }
    val originWorkbookId = current.activeWorkbookId
    val persistenceSession = if (ext in setOf("xlsx", "xlsm")) buildCurrentWorkbookPersistenceSession() else null
    val folderPath = current.saveDialogFolderPath
    val mimeType = MasuSheetConfig.resolveExportMimeType(ext)
    _uiState.value = current.copy(isLoading = true)
    viewModelScope.launch {
        val ioResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val finalBytes = if (persistenceSession != null) {
                WorkbookXlsxObjectPersistence.embedIntoXlsxBytes(rawBytes, persistenceSession)
            } else {
                rawBytes
            }
            val savedUri = documentGateway.saveFileInFolder(
                folderPath = folderPath,
                fileName = finalName,
                mimeType = mimeType,
                bytes = finalBytes
            )
            val scanned = if (savedUri != null) scanExistingFilesInFolder(folderPath) else emptyList()
            savedUri to scanned
        }
        val (savedUriStr, scannedFiles) = ioResult
        val latest = _uiState.value
        if (savedUriStr != null) {
            preferencesRepository.recordRecentWorkbook(finalName)
            preferencesRepository.recordSavedFileInFolder(folderPath, finalName)
            if (latest.activeWorkbookId != originWorkbookId) {
                val updatedTabs = latest.openWorkbooks.map { wb ->
                    if (wb.id == originWorkbookId) {
                        wb.copy(
                            title = finalName,
                            sourceUri = savedUriStr,
                            viewportSnapshot = wb.viewportSnapshot.copy(title = finalName, isDirty = false)
                        )
                    } else {
                        wb
                    }
                }
                _uiState.value = latest.copy(
                    isLoading = false,
                    openWorkbooks = updatedTabs,
                    pendingSaveFileName = null,
                    pendingSaveBytes = null,
                    isSaveDialogVisible = false,
                    lastSavedFileName = finalName,
                    statusNoticeMessage = if (latest.language == "en") "Saved: $finalName" else "保存しました: $finalName",
                    recentWorkbooks = preferencesRepository.getRecentWorkbooks()
                )
                return@launch
            }
            currentActiveSourceUri = savedUriStr
            val savedSnap = engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(finalName)}}")
            _uiState.value = latest.copy(
                isLoading = false,
                workbookTitle = finalName,
                saveDialogBaseName = MasuSheetConfig.extractBaseNameWithoutExtension(finalName),
                saveDialogExistingFiles = scannedFiles,
                pendingSaveFileName = null,
                pendingSaveBytes = null,
                isSaveDialogVisible = false,
                lastSavedFileName = finalName,
                statusNoticeMessage = if (latest.language == "en") "Saved: $finalName" else "保存しました: $finalName",
                isDirty = false,
                viewport = decorateWithCustomSizes(savedSnap.copy(title = finalName, isDirty = false)),
                recentWorkbooks = preferencesRepository.getRecentWorkbooks()
            )
            saveCurrentWorkbookToSessionList()
        } else {
            _uiState.value = latest.copy(
                isLoading = false,
                pendingSaveFileName = null,
                pendingSaveBytes = null,
                errorMessage = if (latest.language == "en") "Failed to save file: $finalName" else "ファイルの保存に失敗しました: $finalName"
            )
        }
    }
}

/**
 * [EN] Hides the Save / Export dialog while preserving workbook state.
 * [JA] 保存／エクスポートダイアログを閉じます。
 */
fun MasuSheetViewModel.onDismissSaveDialog() {
    _uiState.value = _uiState.value.copy(isSaveDialogVisible = false)
}

/**
 * [EN] Clears the transient status notice banner message.
 * [JA] 画面上の一時ステータス通知メッセージを消去します。
 */
fun MasuSheetViewModel.onDismissStatusNotice() {
    _uiState.value = _uiState.value.copy(statusNoticeMessage = null)
}
