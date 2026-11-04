package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] Scoped Storage & SAF Document I/O Gateway
 * Implements [DocumentIoGateway] and [ContentResolverDocumentGateway] for atomic local saves, MediaStore Downloads/Documents export, and SAF tree/file read-write operations.
 *
 * ---
 * [JA / 日本語解説]
 * Android Scoped Storage (`MediaStore`)・SAF (`DocumentFile` / 永続URI権限)・アトミックなローカル一時書込を通じて `.xlsx` / `.csv` 等の安全な読込・保存・フォルダ一覧取得を担うモジュールです。
 * ============================================================================
 */

/**
 * [EN] Storage gateway abstraction for reading, writing, and listing local, SAF, and MediaStore spreadsheet documents.
 * [JA] ローカルファイル・SAF URI・MediaStore経由でのドキュメント読込・アトミック保存・一覧取得を抽象化するインターフェースです。
 */
interface DocumentIoGateway {
    fun readBytes(uriString: String): ByteArray?
    fun writeBytes(uriString: String, bytes: ByteArray): Boolean
    fun resolveDisplayName(uriString: String): String
    fun listFolderFiles(folderPath: String): List<ExistingFolderFileItem> = emptyList()
    fun saveFileInFolder(
        folderPath: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): String? {
        return try {
            val dir = File(folderPath)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName)
            val uriStr = Uri.fromFile(outFile).toString()
            if (writeBytes(uriStr, bytes)) uriStr else null
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * [EN] Android implementation of [DocumentIoGateway] handling Scoped Storage Downloads/Documents, SAF trees, and atomic temp-file writes.
 * [JA] Androidの `ContentResolver`・`MediaStore`・SAF永続権限・一時ファイルアトミック置換を用いた [DocumentIoGateway] 実装です。
 */
class ContentResolverDocumentGateway(
    private val context: Context
) : DocumentIoGateway {

    private fun findMatchingPersistedTreeUri(folderPath: String): Uri? {
        val clean = folderPath.trim().trimEnd('/')
        if (clean.startsWith("content://")) {
            return runCatching { Uri.parse(clean) }.getOrNull()
        }
        val leaf = clean.substringAfterLast('/')
        if (leaf.isBlank()) return null
        return runCatching {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri }
                .firstOrNull { uri ->
                    val s = Uri.decode(uri.toString())
                    s.endsWith(":$leaf", ignoreCase = true) || s.endsWith("/$leaf", ignoreCase = true)
                }
        }.getOrNull()
    }

    private fun queryTreeFolderFiles(
        treeUri: Uri,
        supportedExts: Set<String>,
        seenNames: MutableSet<String>,
        results: MutableList<ExistingFolderFileItem>
    ) {
        runCatching {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val idIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val childId = if (idIdx >= 0) cursor.getString(idIdx) else null
                    if (!name.isNullOrBlank()) {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in supportedExts && seenNames.add(name)) {
                            val docUri = if (childId != null) {
                                android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childId).toString()
                            } else {
                                "$treeUri/$name"
                            }
                            results.add(
                                ExistingFolderFileItem(
                                    fileName = name,
                                    baseName = MasuSheetConfig.extractBaseNameWithoutExtension(name),
                                    extension = ext,
                                    fullPath = docUri
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isInternalPrivateDataFile(file: File): Boolean =
        isInternalPrivateDataFile(context, file)

    companion object {
        fun isInternalPrivateDataFile(context: Context, file: File): Boolean {
            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return true
            val dataRoot = runCatching { File(context.applicationInfo.dataDir).canonicalFile }.getOrNull()
            val handoffDir = runCatching { File(context.cacheDir, "handoff").canonicalFile }.getOrNull()
            if (handoffDir != null && canonical.path.startsWith(handoffDir.path + File.separator)) {
                return false
            }
            val clipboardCacheDir = runCatching { File(context.cacheDir, "clipboard_images").canonicalFile }.getOrNull()
            if (clipboardCacheDir != null && canonical.path.startsWith(clipboardCacheDir.path + File.separator)) {
                return false
            }
            val clipboardFilesDir = runCatching { File(context.filesDir, "clipboard_images").canonicalFile }.getOrNull()
            if (clipboardFilesDir != null && canonical.path.startsWith(clipboardFilesDir.path + File.separator)) {
                return false
            }
            return dataRoot != null && (canonical == dataRoot || canonical.path.startsWith(dataRoot.path + File.separator))
        }
    }

    override fun readBytes(uriString: String): ByteArray? {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                val target = File(path)
                if (isInternalPrivateDataFile(target)) return null
                target.readBytes()
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun writeBytes(uriString: String, bytes: ByteArray): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val path = uri.path ?: return false
                val file = File(path)
                if (isInternalPrivateDataFile(file)) return false
                file.parentFile?.let { if (!it.exists()) it.mkdirs() }
                val atomicFile = androidx.core.util.AtomicFile(file)
                var outStream: java.io.FileOutputStream? = null
                try {
                    outStream = atomicFile.startWrite()
                    outStream.write(bytes)
                    outStream.flush()
                    atomicFile.finishWrite(outStream)
                } catch (e: Exception) {
                    if (outStream != null) {
                        atomicFile.failWrite(outStream)
                    }
                    throw e
                }
                runCatching {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        null,
                        null
                    )
                }
                true
            } else {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(bytes)
                    out.flush()
                    true
                } ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * [EN] Saves binary data directly into the specified folder (Android local `Download` / `Documents`,
     * SAF tree URI, or shared workspace directory).
     * Priority order:
     * 1. Persisted SAF Tree URI (`DocumentsContract.createDocument`).
     * 1b. Persisted single-document SAF URI (`persistedUriPermissions`) for overwriting external apps' files.
     * 2. Android 10+ `MediaStore.Downloads` / `MediaStore.Files` registration for immediate visibility in Android Files app.
     * 3. Direct `java.io.File` fallback + `MediaScannerConnection` indexing.
     *
     * ---
     * [JA] 指定されたフォルダ（Android本体の「ダウンロード」「ドキュメント」、SAFツリーURI、または共有フォルダ）に
     * バイナリデータを直接保存します（SAFツリー → 永続単一URI → MediaStore → Fileフォールバックの順で処理）。
     */
    override fun saveFileInFolder(
        folderPath: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): String? {
        val cleanFolder = folderPath.trim().trimEnd('/')

        // 1. If folderPath is a SAF content:// tree URI (or matches a persisted SAF tree URI for Download/Documents)
        val treeUri = findMatchingPersistedTreeUri(cleanFolder)
        if (treeUri != null) {
            val savedViaTree = runCatching {
                val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
                val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
                var existingDocUri: Uri? = null
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val idIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    while (cursor.moveToNext()) {
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                        val childId = if (idIdx >= 0) cursor.getString(idIdx) else null
                        if (name != null && childId != null && name.equals(fileName, ignoreCase = true)) {
                            existingDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            break
                        }
                    }
                }
                val targetDocUri = existingDocUri ?: android.provider.DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    mimeType,
                    fileName
                )
                if (targetDocUri != null && writeBytes(targetDocUri.toString(), bytes)) {
                    targetDocUri.toString()
                } else {
                    null
                }
            }.getOrNull()
            if (savedViaTree != null) return savedViaTree
        }

        // 1b. If overwriting a file that already has a persisted single-document SAF permission (e.g. a file downloaded by Chrome that the user selected once)
        val persistedDocUri = runCatching {
            val folderLeaf = cleanFolder.substringAfterLast('/')
            val isDownload = folderLeaf.equals("Download", ignoreCase = true) || folderLeaf.equals("Downloads", ignoreCase = true)
            val isDocuments = folderLeaf.equals("Documents", ignoreCase = true)
            context.contentResolver.persistedUriPermissions.firstOrNull { perm ->
                perm.isWritePermission &&
                    !android.provider.DocumentsContract.isTreeUri(perm.uri) &&
                    doesPersistedDocumentMatchFolder(perm.uri, cleanFolder, folderLeaf, isDownload, isDocuments) &&
                    resolveDisplayName(perm.uri.toString()).equals(fileName, ignoreCase = true)
            }?.uri
        }.getOrNull()
        if (persistedDocUri != null && writeBytes(persistedDocUri.toString(), bytes)) {
            return persistedDocUri.toString()
        }

        // 2. If saving to Android's local Download or Documents folder on Android 10+ (API 29+), register directly in MediaStore.Downloads / MediaStore.Files
        val folderLeaf = cleanFolder.substringAfterLast('/')
        val isDownloadFolder = folderLeaf.equals("Download", ignoreCase = true) || folderLeaf.equals("Downloads", ignoreCase = true)
        val isDocumentsFolder = folderLeaf.equals("Documents", ignoreCase = true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && (isDownloadFolder || isDocumentsFolder)) {
            val savedViaMediaStore = runCatching {
                val relDir = if (isDownloadFolder) android.os.Environment.DIRECTORY_DOWNLOADS else android.os.Environment.DIRECTORY_DOCUMENTS
                val collection = if (isDownloadFolder) {
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    android.provider.MediaStore.Files.getContentUri("external")
                }
                var existingMediaUri: Uri? = null
                val projection = arrayOf(
                    android.provider.MediaStore.MediaColumns._ID,
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH
                )
                context.contentResolver.query(
                    collection,
                    projection,
                    "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns._ID)
                    val relCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.RELATIVE_PATH)
                    while (cursor.moveToNext()) {
                        val id = if (idCol >= 0) cursor.getLong(idCol) else -1L
                        val rel = (if (relCol >= 0) cursor.getString(relCol) else null)?.trimEnd('/').orEmpty()
                        if (id >= 0L && (rel.isEmpty() || rel.equals(relDir, ignoreCase = true))) {
                            existingMediaUri = android.content.ContentUris.withAppendedId(collection, id)
                            break
                        }
                    }
                }
                val isCreatedNew = existingMediaUri == null
                val targetUri = existingMediaUri ?: run {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "$relDir/")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    context.contentResolver.insert(collection, values)
                }
                if (targetUri != null && writeBytes(targetUri.toString(), bytes)) {
                    if (isCreatedNew) {
                        val readyValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        runCatching { context.contentResolver.update(targetUri, readyValues, null, null) }
                    }
                    targetUri.toString()
                } else {
                    if (isCreatedNew && targetUri != null) {
                        runCatching { context.contentResolver.delete(targetUri, null, null) }
                    }
                    null
                }
            }.getOrNull()
            if (savedViaMediaStore != null) return savedViaMediaStore
        }

        // 3. Direct filesystem fallback + MediaScannerConnection indexing
        return try {
            val dir = File(cleanFolder)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName)
            val uriStr = Uri.fromFile(outFile).toString()
            if (writeBytes(uriStr, bytes)) uriStr else null
        } catch (_: Exception) {
            null
        }
    }

    override fun resolveDisplayName(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            }
            val lastSeg = uri.lastPathSegment?.substringAfterLast('/')
            if (!lastSeg.isNullOrBlank()) lastSeg else "Book1.xlsx"
        } catch (_: Exception) {
            "Book1.xlsx"
        }
    }

    internal fun doesPersistedDocumentMatchFolder(
        docUri: Uri,
        cleanFolder: String,
        folderLeaf: String,
        isDownloadFolder: Boolean,
        isDocumentsFolder: Boolean
    ): Boolean {
        val raw = docUri.toString()
        val decoded = try { Uri.decode(raw) } catch (_: Exception) { raw }
        val authority = docUri.authority.orEmpty()
        return when {
            cleanFolder.startsWith("content://") -> {
                val cleanTreePrefix = cleanFolder.substringBefore("/document/")
                raw.startsWith(cleanTreePrefix)
            }
            authority == "com.android.providers.downloads.documents" -> isDownloadFolder
            decoded.contains("primary:Download/", ignoreCase = true) ||
                decoded.contains("/Download/", ignoreCase = true) -> isDownloadFolder
            decoded.contains("primary:Documents/", ignoreCase = true) ||
                decoded.contains("/Documents/", ignoreCase = true) -> isDocumentsFolder
            folderLeaf.isNotBlank() &&
                !isDownloadFolder &&
                !isDocumentsFolder &&
                (decoded.contains(":$folderLeaf/", ignoreCase = true) || decoded.contains("/$folderLeaf/", ignoreCase = true)) -> true
            else -> false
        }
    }

    /**
     * [EN] Lists spreadsheet files inside [folderPath] by combining:
     * - Persisted SAF directory tree URIs (`findMatchingPersistedTreeUri`),
     * - Persisted single-document SAF URIs (`persistedUriPermissions`) granted when selecting external apps' files,
     * - `MediaStore.Downloads` / `MediaStore.Files` queries and local `File.listFiles()` fallback.
     *
     * ---
     * [JA] 指定フォルダ内のスプレッドシート一覧を返します（SAFツリー権限、他アプリ作成ファイルの永続単一URI権限、
     * MediaStore、およびローカルディレクトリ検索を統合して返却）。
     */
    override fun listFolderFiles(folderPath: String): List<ExistingFolderFileItem> {
        val supportedExts = setOf("xlsx", "csv", "tsv", "ods", "pdf", "xlsm", "xlsb")
        val results = mutableListOf<ExistingFolderFileItem>()
        val seenNames = mutableSetOf<String>()
        val cleanFolder = folderPath.trimEnd('/')

        // 1. If folderPath is a content:// tree URI OR matches a persisted SAF tree URI (e.g. primary:Download)
        val matchedTreeUri = findMatchingPersistedTreeUri(cleanFolder)
        if (matchedTreeUri != null) {
            queryTreeFolderFiles(matchedTreeUri, supportedExts, seenNames, results)
            if (cleanFolder.startsWith("content://")) {
                return results
            }
        }

        val folderLeaf = cleanFolder.substringAfterLast('/')
        val isDownloadFolder = folderLeaf.equals("Download", ignoreCase = true) || folderLeaf.equals("Downloads", ignoreCase = true)
        val isDocumentsFolder = folderLeaf.equals("Documents", ignoreCase = true)

        // 1b. Query persisted single-document SAF URIs (files from Chrome/Gmail/etc. that the user selected via Android Files picker)
        runCatching {
            val perms = context.contentResolver.persistedUriPermissions
            for (perm in perms) {
                if (!perm.isReadPermission || android.provider.DocumentsContract.isTreeUri(perm.uri)) continue
                if (!doesPersistedDocumentMatchFolder(perm.uri, cleanFolder, folderLeaf, isDownloadFolder, isDocumentsFolder)) continue
                context.contentResolver.query(
                    perm.uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val name = if (idx >= 0) cursor.getString(idx) else null
                        if (!name.isNullOrBlank()) {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in supportedExts && seenNames.add(name)) {
                                results.add(
                                    ExistingFolderFileItem(
                                        fileName = name,
                                        baseName = MasuSheetConfig.extractBaseNameWithoutExtension(name),
                                        extension = ext,
                                        fullPath = perm.uri.toString()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Query Android MediaStore (both MediaStore.Downloads and MediaStore.Files) for files in this folder
        val collections = buildList {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && isDownloadFolder) {
                add(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            }
            add(android.provider.MediaStore.Files.getContentUri("external"))
        }
        for (collection in collections) {
            runCatching {
                val projection = arrayOf(
                    android.provider.MediaStore.MediaColumns._ID,
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.provider.MediaStore.MediaColumns.DATA
                )
                val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    val relCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.RELATIVE_PATH)
                    val dataCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    while (cursor.moveToNext()) {
                        val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                        if (name.isNullOrBlank()) continue
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext !in supportedExts) continue
                        val relPath = (if (relCol >= 0) cursor.getString(relCol) else null)?.trimEnd('/').orEmpty()
                        val dataPath = (if (dataCol >= 0) cursor.getString(dataCol) else null).orEmpty()
                        val parentDataPath = dataPath.substringBeforeLast('/', "")
                        val matchesFolder = when {
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                                collection == android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI &&
                                isDownloadFolder && (relPath.isEmpty() || relPath.equals("Download", ignoreCase = true) || relPath.equals("Downloads", ignoreCase = true)) -> true
                            parentDataPath.isNotEmpty() && parentDataPath.equals(cleanFolder, ignoreCase = true) -> true
                            relPath.equals(folderLeaf, ignoreCase = true) -> true
                            cleanFolder.endsWith("/$relPath", ignoreCase = true) && relPath.isNotEmpty() -> true
                            else -> false
                        }
                        if (matchesFolder && seenNames.add(name)) {
                            val id = if (idCol >= 0) cursor.getLong(idCol) else -1L
                            val uriOrPath = if (dataPath.isNotBlank()) {
                                dataPath
                            } else if (id >= 0L) {
                                android.content.ContentUris.withAppendedId(collection, id).toString()
                            } else {
                                "$cleanFolder/$name"
                            }
                            results.add(
                                ExistingFolderFileItem(
                                    fileName = name,
                                    baseName = MasuSheetConfig.extractBaseNameWithoutExtension(name),
                                    extension = ext,
                                    fullPath = uriOrPath
                                )
                            )
                        }
                    }
                }
            }
        }
        return results
    }
}

/**
 * [EN] Factory creating an application-scoped [ContentResolverDocumentGateway] instance.
 * [JA] アプリケーションコンテキストから [ContentResolverDocumentGateway] を生成するファクトリです。
 */
object ContentResolverDocumentGatewayFactory {
    fun create(context: Context): DocumentIoGateway =
        ContentResolverDocumentGateway(context.applicationContext)
}
