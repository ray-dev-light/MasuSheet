package com.masusheet.app.storage

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/**
 * [EN] Read-only [ContentProvider] serving uniquely named clipboard PNG files (`clipboard_<pid>_<uuid>.png`)
 * from `filesDir/clipboard_images/` to the Android system clipboard and receiving apps without cross-process
 * race conditions (`:window2`) or path traversal risks.
 *
 * [JA] マルチウィンドウ（`:window2`）競合やパストラバーサルを防ぎつつ、一意名 (`clipboard_<pid>_<uuid>.png`) の
 * コピー画像を Android システムクリップボード経由で安全に読み取り専用配信する [ContentProvider] です。
 */
class MasuSheetClipboardImageProvider : ContentProvider() {

    companion object {
        private const val DIR_NAME = "clipboard_images"
        private const val MAX_RETAINED_FILES = 10

        fun writeClipboardImageAndBuildUri(
            context: Context,
            imageBytes: ByteArray,
            displayFileName: String
        ): Uri {
            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
            pruneOldFiles(dir)
            val tokenName = "clipboard_${Process.myPid()}_${UUID.randomUUID()}.png"
            val targetFile = File(dir, tokenName)
            targetFile.writeBytes(imageBytes)
            val safeDisplay = displayFileName
                .replace(Regex("[^a-zA-Z0-9._\\-\\u3000-\\u9FFF]"), "_")
                .ifBlank { "clipboard_image.png" }
            return Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.clipboard")
                .appendPath(tokenName)
                .appendPath(safeDisplay)
                .build()
        }

        private fun pruneOldFiles(dir: File) {
            val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".png") } ?: return
            if (files.size < MAX_RETAINED_FILES) return
            files.sortedByDescending { it.lastModified() }
                .drop(MAX_RETAINED_FILES - 1)
                .forEach { runCatching { it.delete() } }
        }
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String> = arrayOf("image/png")

    private fun resolveSafeFile(uri: Uri): File? {
        val ctx = context ?: return null
        val dir = File(ctx.filesDir, DIR_NAME)
        if (!dir.isDirectory) return null
        val segments = uri.pathSegments
        // [EN] Require an explicit `clipboard_<pid>_<uuid>.png` capability segment; never fall back to the latest file.
        // [JA] URI パス内に明示的な `clipboard_<pid>_<uuid>.png` トークンがある場合のみ解決し、最新ファイルへのフォールバックを禁止します。
        val tokenSegment = segments.firstOrNull { it.startsWith("clipboard_") && it.endsWith(".png") }
            ?: return null
        if ('/' in tokenSegment || '\\' in tokenSegment || ".." in tokenSegment) return null
        val baseCanonical = runCatching { dir.canonicalFile }.getOrNull() ?: return null
        val candidateCanonical = runCatching { File(dir, tokenSegment).canonicalFile }.getOrNull() ?: return null
        if (candidateCanonical.parentFile != baseCanonical || !candidateCanonical.isFile) return null
        return candidateCanonical
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") {
            throw SecurityException("MasuSheetClipboardImageProvider is read-only")
        }
        val file = resolveSafeFile(uri) ?: throw FileNotFoundException("Clipboard image not found")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val file = resolveSafeFile(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols, 1)
        val row = cursor.newRow()
        val displayName = uri.lastPathSegment?.ifBlank { null } ?: "clipboard_image.png"
        for (col in cols) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(displayName)
                OpenableColumns.SIZE -> row.add(if (file != null && file.exists()) file.length() else 0L)
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
