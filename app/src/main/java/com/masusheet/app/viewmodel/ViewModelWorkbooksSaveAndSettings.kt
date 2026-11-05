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
 * [EN] Workbook Sessions, Multi-Window Handoff (`C-04`), Atomic Local/SAF Export (`H-08`), & Theme/Zoom Settings
 * Manages multi-workbook tabs, full-fidelity multi-window detach & merge-back (`C-04`), SAF/local folder save & `.xlsx`/`.csv`/`.pdf` export (`H-08`), sample templates, and UI/theme/zoom preferences.
 *
 * ---
 * [JA / 日本語解説]
 * 複数ブックタブ管理、グラフ・画像・ピボットを完全保持する別ウィンドウ分離・統合 (`C-04`)、SAF／ローカルフォルダ保存とエクスポート (`H-08`)、サンプル読込、UIテーマ・表示倍率設定を担います。
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
            currentActiveSourceUri = destinationUriString
            val savedSnap = engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(finalName)}}")
            preferencesRepository.recordRecentWorkbook(finalName)
            preferencesRepository.recordSavedFileInFolder(folderPath, finalName)
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
            currentActiveSourceUri = savedUriStr
            val savedSnap = engineGateway.executeCommand("file.markSaved", "{\"title\":${JSONObject.quote(finalName)}}")
            preferencesRepository.recordRecentWorkbook(finalName)
            preferencesRepository.recordSavedFileInFolder(folderPath, finalName)
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
 * [EN] `C-04`: Restores a full [OpenWorkbookSession] (including charts, images, shapes, PivotTables,
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
