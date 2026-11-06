package com.masusheet.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.compositionLocalOf
import com.masusheet.app.MainActivity
import com.masusheet.app.R
import com.masusheet.app.SecondaryWorkbookActivity
import com.masusheet.app.model.OpenWorkbookSession
import com.masusheet.app.storage.ContentResolverDocumentGateway
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.WindowHandoffManager

/**
 * ============================================================================
 * [EN] CompositionLocals, System Clipboard Text/Image Bridge, & Multi-Window Launchers
 * Provides [LocalAppLanguage], [LocalMaterial3Ui], [LocalDarkTheme], clipboard text/PNG helpers via [MasuSheetClipboardImageProvider], and multi-window detach/merge-back helpers.
 *
 * ---
 * [JA / 日本語解説]
 * アプリ全体の `CompositionLocal`（言語・Material3・ダークテーマ）、システムクリップボードへのテキスト／PNG画像入出力、および別ウィンドウ分離・統合ランチャー群を提供します。
 * ============================================================================
 */

val LocalAppLanguage = compositionLocalOf { "ja" }
val LocalMaterial3Ui = compositionLocalOf { true }
val LocalDarkTheme = compositionLocalOf { false }

/**
 * [EN] Copies plain text or TSV cell values onto Android's system `ClipboardManager` and clears any stale in-process image clip.
 * [JA] 選択セルの文字列（複数セル時はTSV）を Android システムクリップボードへコピーし、プロセス内の前回コピー画像キャッシュをクリアします。
 */
fun copyToSystemClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    // Clear cached in-process image when copying plain text so subsequent paste inserts the copied text
    lastReceivedClipboardImage = null
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("MasuSheet Cell", text))
    } catch (_: Exception) {
    }
}

/**
 * [EN] Reads coerced plain text from the primary item on Android's system `ClipboardManager`, or returns `null` when empty.
 * [JA] Android システムクリップボードの先頭アイテムからプレーンテキストを読み取ります（空または取得不可時は `null`）。
 */
fun readFromSystemClipboard(context: Context): String? {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context)?.toString()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * [EN] Hard 20 MiB ceiling and pixel-count guard for clipboard/URI image imports to prevent OutOfMemoryError.
 * [JA] クリップボードおよびURI経由の画像読込における OutOfMemoryError 防止用の上限（20 MiB / 最大 8192px・3200万画素）です。
 */
internal const val MAX_CLIPBOARD_IMAGE_BYTES = 20 * 1024 * 1024
private const val MAX_CLIPBOARD_BASE64_CHARS = (MAX_CLIPBOARD_IMAGE_BYTES * 4) / 3 + 128
private const val MAX_DECODED_IMAGE_DIMENSION = 8192
private const val MAX_DECODED_IMAGE_PIXELS = 33_554_432L

@Volatile
var lastReceivedClipboardImage: Pair<String, ByteArray>? = null

/**
 * [EN] Places a PNG image byte array onto the Android device system clipboard (`ClipboardManager`)
 * via `MasuSheetClipboardImageProvider` (`content://com.masusheet.app.clipboard/...`) as well as
 * updating `lastReceivedClipboardImage` so the combined image can be pasted both inside MasuSheet
 * and into any external Android application.
 *
 * [JA] PNG 画像バイト列を Android デバイスのシステムクリップボード（`ClipboardManager`）に格納します。
 * MasuSheet 内の別シート・別ブックへの貼り付けはもちろん、外部の Android アプリへも画像としてそのまま貼り付け可能です。
 */
fun copyImageBytesToSystemClipboard(
    context: Context,
    imageBytes: ByteArray,
    fileName: String = "結合画像.png"
): Boolean {
    if (imageBytes.isEmpty()) return false
    lastReceivedClipboardImage = fileName to imageBytes
    return try {
        val uri = MasuSheetClipboardImageProvider.writeClipboardImageAndBuildUri(context, imageBytes, fileName)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val description = android.content.ClipDescription(
                fileName,
                arrayOf("image/png", "image/*")
            )
            val item = ClipData.Item(uri)
            val clipData = ClipData(description, item)
            clipboard.setPrimaryClip(clipData)
        }
        true
    } catch (_: Exception) {
        true
    }
}

/**
 * [EN] Reads up to 20 MiB of verified image bytes from a `content://` URI (or external user file), blocking private app-data paths and decompression bombs.
 * [JA] `content://` URI から最大20MiBの画像バイト列と表示名を安全に読み取ります（アプリ内部機密領域および過剰解像度画像は拒否）。
 */
fun readImageBytesFromUri(context: Context, uri: Uri): Pair<String, ByteArray>? {
    val label = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "クリップボード画像.png"
    if (uri.scheme.equals("file", ignoreCase = true)) {
        val path = uri.path.orEmpty()
        if (path.isNotBlank()) {
            val f = java.io.File(path)
            if (ContentResolverDocumentGateway.isInternalPrivateDataFile(context, f)) {
                return null
            }
            if (!f.isFile || f.length() > MAX_CLIPBOARD_IMAGE_BYTES.toLong()) {
                return null
            }
        }
    }
    // 1. Bounded InputStream read (strictly <= 20 MiB) via ContentResolver
    val rawBytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ContentResolverDocumentGateway.readBoundedStream(it, MAX_CLIPBOARD_IMAGE_BYTES)
        }
    }.getOrNull() ?: runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            afd.createInputStream().use {
                ContentResolverDocumentGateway.readBoundedStream(it, MAX_CLIPBOARD_IMAGE_BYTES)
            }
        }
    }.getOrNull() ?: runCatching {
        context.contentResolver.openTypedAssetFileDescriptor(uri, "image/*", null)?.use { afd ->
            afd.createInputStream().use {
                ContentResolverDocumentGateway.readBoundedStream(it, MAX_CLIPBOARD_IMAGE_BYTES)
            }
        }
    }.getOrNull()

    if (rawBytes != null && rawBytes.size in 9..MAX_CLIPBOARD_IMAGE_BYTES) {
        if (isLikelyImageBytes(rawBytes)) {
            return label to rawBytes
        }
        if (!hasSafeDecodeBounds(rawBytes)) {
            return null
        }
        val bmp = runCatching { BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) }.getOrNull()
        if (bmp != null) {
            try {
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                val pngBytes = out.toByteArray()
                if (pngBytes.isNotEmpty() && pngBytes.size <= MAX_CLIPBOARD_IMAGE_BYTES) {
                    return label to pngBytes
                }
            } finally {
                bmp.recycle()
            }
        }
    }
    return null
}

/**
 * [EN] Reads an image strictly from the active Android system clipboard (`ClipboardManager.primaryClip`).
 *      Never scans MediaStore silently and never dereferences `content://` or local filesystem paths pasted as plain text.
 * [JA] アクティブなシステムクリップボード（`ClipboardManager.primaryClip`）上の画像アイテムのみを安全に読み取ります
 *      （プレーンテキスト内の `content://` 文字列やローカルパスの自動参照は行いません）。
 */
fun readImageBytesFromSystemClipboard(context: Context): Pair<String, ByteArray>? {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip ?: return null
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            val uri = item.uri
            if (uri != null) {
                val fromUri = readImageBytesFromUri(context, uri)
                if (fromUri != null) {
                    lastReceivedClipboardImage = fromUri
                    return fromUri
                }
                if (uri.authority == "${context.packageName}.clipboard") {
                    lastReceivedClipboardImage?.let { return it }
                }
            }
            val html = item.htmlText.orEmpty()
            if (html.contains("data:image/", ignoreCase = true) && html.contains(";base64,")) {
                val b64 = html.substringAfter(";base64,").substringBefore("\"").substringBefore("'")
                if (b64.length in 12..MAX_CLIPBOARD_BASE64_CHARS) {
                    val bytes = runCatching {
                        android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    }.getOrNull()
                    if (bytes != null && bytes.size in 9..MAX_CLIPBOARD_IMAGE_BYTES && isLikelyImageBytes(bytes)) {
                        val pair = "クリップボード画像.png" to bytes
                        lastReceivedClipboardImage = pair
                        return pair
                    }
                }
            }
            val rawText = item.text?.toString()?.trim().orEmpty()
            if (rawText.startsWith("data:image/", ignoreCase = true) && rawText.contains(";base64,")) {
                val base64Part = rawText.substringAfter(";base64,")
                if (base64Part.length in 12..MAX_CLIPBOARD_BASE64_CHARS) {
                    val bytes = runCatching {
                        android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                    }.getOrNull()
                    if (bytes != null && bytes.size in 9..MAX_CLIPBOARD_IMAGE_BYTES && isLikelyImageBytes(bytes)) {
                        val pair = "クリップボード画像.png" to bytes
                        lastReceivedClipboardImage = pair
                        return pair
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    return null
}

private fun hasSafeDecodeBounds(bytes: ByteArray): Boolean {
    return runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        w in 1..MAX_DECODED_IMAGE_DIMENSION &&
            h in 1..MAX_DECODED_IMAGE_DIMENSION &&
            w.toLong() * h.toLong() <= MAX_DECODED_IMAGE_PIXELS
    }.getOrDefault(false)
}

/**
 * [EN] Checks magic-byte signatures for PNG, JPEG, GIF, WebP (`RIFF....WEBP`), and BMP headers, using bounds-only probing as fallback.
 * [JA] 先頭マジックバイトおよび `inJustDecodeBounds` 境界検査から PNG・JPEG・GIF・WebP・BMP の安全な画像バイナリであるかを判定します。
 */
internal fun isLikelyImageBytes(bytes: ByteArray): Boolean {
    if (bytes.size < 8 || bytes.size > MAX_CLIPBOARD_IMAGE_BYTES) return false
    // PNG magic: 89 50 4E 47 0D 0A 1A 0A
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
        val dims = com.masusheet.app.core.MasuSheetConfig.decodeImageDimensions(bytes)
        return dims == null || (dims.first in 1..MAX_DECODED_IMAGE_DIMENSION && dims.second in 1..MAX_DECODED_IMAGE_DIMENSION && dims.first.toLong() * dims.second.toLong() <= MAX_DECODED_IMAGE_PIXELS)
    }
    // JPEG magic: FF D8 FF
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
        val dims = com.masusheet.app.core.MasuSheetConfig.decodeImageDimensions(bytes)
        return dims == null || (dims.first in 1..MAX_DECODED_IMAGE_DIMENSION && dims.second in 1..MAX_DECODED_IMAGE_DIMENSION && dims.first.toLong() * dims.second.toLong() <= MAX_DECODED_IMAGE_PIXELS)
    }
    // GIF magic: "GIF87a" or "GIF89a"
    if (bytes.size >= 10 &&
        bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() &&
        bytes[3] == '8'.code.toByte() && (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
        bytes[5] == 'a'.code.toByte()
    ) {
        val dims = com.masusheet.app.core.MasuSheetConfig.decodeImageDimensions(bytes)
        return dims != null && dims.first in 1..MAX_DECODED_IMAGE_DIMENSION && dims.second in 1..MAX_DECODED_IMAGE_DIMENSION && dims.first.toLong() * dims.second.toLong() <= MAX_DECODED_IMAGE_PIXELS
    }
    // WEBP magic: "RIFF"...."WEBP"
    if (bytes.size >= 12 &&
        bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
    ) {
        val dims = com.masusheet.app.core.MasuSheetConfig.decodeImageDimensions(bytes)
        return dims == null || (dims.first in 1..MAX_DECODED_IMAGE_DIMENSION && dims.second in 1..MAX_DECODED_IMAGE_DIMENSION && dims.first.toLong() * dims.second.toLong() <= MAX_DECODED_IMAGE_PIXELS)
    }
    // BMP magic: "BM"
    if (bytes.size >= 26 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) {
        return hasSafeDecodeBounds(bytes)
    }
    return hasSafeDecodeBounds(bytes)
}

/**
 * [EN] Launches an independent multi-window `SecondaryWorkbookActivity` (`FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_MULTIPLE_TASK`).
 * [JA] Android マルチウィンドウ環境向けに、独立タスクの新しいウィンドウ（`SecondaryWorkbookActivity`）を起動します。
 */
internal fun launchNewWorkbookWindow(context: Context, blankWorkbook: Boolean = false) {
    runCatching {
        val intent = Intent(context, SecondaryWorkbookActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("masusheet://window/${System.currentTimeMillis()}")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
            )
            if (blankWorkbook) {
                putExtra("masusheet_window_mode", "blank")
            }
        }
        context.startActivity(intent)
    }
}

/**
 * [EN] Launches a specific [OpenWorkbookSession] in an isolated `SecondaryWorkbookActivity` window (`:window2` process)
 *      using `WindowHandoffManager` UUID capability tokens.
 * [JA] 指定したブックタブ（[OpenWorkbookSession]）を UUID トークン方式で安全に独立ウィンドウ（`SecondaryWorkbookActivity`）へ起動します。
 */
internal fun launchWorkbookSessionInNewWindow(context: Context, session: OpenWorkbookSession) {
    runCatching {
        val token = WindowHandoffManager.writeHandoffSession(context, session) ?: return
        val intent = Intent(context, SecondaryWorkbookActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("masusheet://window/handoff/$token")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
            )
            putExtra("masusheet_window_mode", "handoff")
            putExtra("masusheet_handoff_token", token)
        }
        context.startActivity(intent)
    }
}

/**
 * [EN] Merges a detached workbook session from `SecondaryWorkbookActivity` back into the primary `MainActivity`
 *      window (`singleTask`) as a workbook tab via `WindowHandoffManager` UUID tokens.
 * [JA] 別ウィンドウ (`SecondaryWorkbookActivity`) に分離されたブックを UUID トークン方式で元のメインウィンドウへ戻して統合します。
 */
internal fun mergeWorkbookSessionIntoMainWindow(
    context: Context,
    session: OpenWorkbookSession,
    finishCurrentWindow: Boolean = false
) {
    runCatching {
        val token = WindowHandoffManager.writeHandoffSession(context, session) ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("masusheet://window/merge/$token")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra("masusheet_window_mode", "merge_into_main")
            putExtra("masusheet_handoff_token", token)
        }
        context.startActivity(intent)
        if (finishCurrentWindow && context is android.app.Activity) {
            context.finish()
        }
    }
}

/**
 * [EN] Registers the 2 non-destructive dynamic Shelf/Taskbar shortcuts (`New Window` and `New Blank Book`).
 *      Static `res/xml/shortcuts.xml` is intentionally avoided because Android's `ShortcutParser` forces
 *      `Intent.FLAG_ACTIVITY_CLEAR_TASK` on static shortcuts, which destroys the running `MainActivity`
 *      and wipes unsaved sheet edits. Using dynamic shortcuts with `FLAG_ACTIVITY_SINGLE_TOP` routes
 *      cleanly to `MainActivity.onNewIntent()` without recreating the Activity or duplicating menu items.
 * [JA] シェルフ／タスクバー右クリック用の動的ショートカット2件（「新しいウィンドウ」「新規空白ブック」）のみを登録します。
 *      静的 `shortcuts.xml` を併用すると4件に重複するうえ、Android の仕様で `FLAG_ACTIVITY_CLEAR_TASK` が強制付与されて
 *      入力中のシートが白紙化するため、既存データを保持したまま `MainActivity.onNewIntent()` へ安全に配送する
 *      `FLAG_ACTIVITY_SINGLE_TOP` 付き動的ショートカットに一本化しています。
 */
internal fun registerShelfWindowShortcuts(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    runCatching {
        val sm = context.getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
        val icon = android.graphics.drawable.Icon.createWithResource(context, R.mipmap.ic_launcher)
        val nonDestructiveFlags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP

        // Dynamic launcher shortcuts target exported MainActivity and forward safely to internal SecondaryWorkbookActivity
        val newWinIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("masusheet://shortcut/new_window")
            putExtra("masusheet_shortcut_mode", "new_window")
            addFlags(nonDestructiveFlags)
        }
        val newWinShortcut = android.content.pm.ShortcutInfo.Builder(context, "dyn_new_window_workbook")
            .setShortLabel(context.getString(R.string.shortcut_new_window_short))
            .setLongLabel(context.getString(R.string.shortcut_new_window_long))
            .setIcon(icon)
            .setIntent(newWinIntent)
            .build()

        val blankWinIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("masusheet://shortcut/new_blank")
            putExtra("masusheet_shortcut_mode", "new_blank")
            addFlags(nonDestructiveFlags)
        }
        val blankWinShortcut = android.content.pm.ShortcutInfo.Builder(context, "dyn_new_blank_workbook_window")
            .setShortLabel(context.getString(R.string.shortcut_new_blank_short))
            .setLongLabel(context.getString(R.string.shortcut_new_blank_long))
            .setIcon(icon)
            .setIntent(blankWinIntent)
            .build()

        sm.dynamicShortcuts = listOf(newWinShortcut, blankWinShortcut)
    }
}
