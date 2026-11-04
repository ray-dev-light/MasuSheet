package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import com.masusheet.app.storage.WindowHandoffManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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
 * [EN] UI/application helper `copyToSystemClipboard` (copy to system clipboard).
 * [JA] `copyToSystemClipboard` に対応するUI／アプリケーション補助処理を実行します。
 */
fun copyToSystemClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    // `C-09`: Clear cached in-process image when copying plain text so subsequent paste inserts the copied text
    lastReceivedClipboardImage = null
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("MasuSheet Cell", text))
    } catch (_: Throwable) {
    }
}

/**
 * [EN] UI/application helper `readFromSystemClipboard` (read from system clipboard).
 * [JA] `readFromSystemClipboard` に対応するUI／アプリケーション補助処理を実行します。
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
    } catch (_: Throwable) {
        null
    }
}

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
    } catch (_: Throwable) {
        true
    }
}

/**
 * [EN] UI/application helper `readImageBytesFromUri` (read image bytes from uri).
 * [JA] `readImageBytesFromUri` に対応するUI／アプリケーション補助処理を実行します。
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
        }
    }
    // 1. Direct InputStream via ContentResolver
    val rawBytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            afd.createInputStream().use { it.readBytes() }
        }
    }.getOrNull() ?: runCatching {
        context.contentResolver.openTypedAssetFileDescriptor(uri, "image/*", null)?.use { afd ->
            afd.createInputStream().use { it.readBytes() }
        }
    }.getOrNull()

    if (rawBytes != null && rawBytes.size > 8) {
        if (isLikelyImageBytes(rawBytes)) {
            return label to rawBytes
        }
        val bmp = runCatching { BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) }.getOrNull()
        if (bmp != null) {
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            val pngBytes = out.toByteArray()
            if (pngBytes.isNotEmpty()) return label to pngBytes
        }
    }
    return null
}

/**
 * [EN] `C-09` & `C-02`: Reads an image strictly from the active Android system clipboard (`ClipboardManager.primaryClip`).
 *      Never scans MediaStore silently and never opens arbitrary local filesystem paths from plain text.
 * [JA] アクティブなシステムクリップボード（`ClipboardManager.primaryClip`）上の画像のみを安全に読み取ります。
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
                val bytes = runCatching {
                    android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                }.getOrNull()
                if (bytes != null && bytes.size > 8 && isLikelyImageBytes(bytes)) {
                    val pair = "クリップボード画像.png" to bytes
                    lastReceivedClipboardImage = pair
                    return pair
                }
            }
            val rawText = item.text?.toString()?.trim().orEmpty()
            if (rawText.startsWith("data:image/", ignoreCase = true) && rawText.contains(";base64,")) {
                val base64Part = rawText.substringAfter(";base64,")
                val bytes = runCatching {
                    android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                }.getOrNull()
                if (bytes != null && bytes.size > 8 && isLikelyImageBytes(bytes)) {
                    val pair = "クリップボード画像.png" to bytes
                    lastReceivedClipboardImage = pair
                    return pair
                }
            }
            if (rawText.startsWith("content://", ignoreCase = true)) {
                val parsedUri = runCatching { Uri.parse(rawText) }.getOrNull()
                if (parsedUri != null) {
                    val fromParsed = readImageBytesFromUri(context, parsedUri)
                    if (fromParsed != null) {
                        lastReceivedClipboardImage = fromParsed
                        return fromParsed
                    }
                }
            }
        }
    } catch (_: Throwable) {
    }
    return null
}

/**
 * [EN] UI/application helper `isLikelyImageBytes` (is likely image bytes).
 * [JA] `isLikelyImageBytes` に対応するUI／アプリケーション補助処理を実行します。
 */
internal fun isLikelyImageBytes(bytes: ByteArray): Boolean {
    if (bytes.size < 8) return false
    // PNG magic: 89 50 4E 47
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
        return true
    }
    // JPEG magic: FF D8 FF
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
        return true
    }
    // WEBP magic: "RIFF"...."WEBP"
    if (bytes.size >= 12 &&
        bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
    ) {
        return true
    }
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null }.getOrDefault(false)
}

/**
 * [EN] UI/application helper `launchNewWorkbookWindow` (launch new workbook window).
 * [JA] `launchNewWorkbookWindow` に対応するUI／アプリケーション補助処理を実行します。
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
 *      using `WindowHandoffManager` UUID capability tokens (`C-02` & `C-04`).
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
 *      window (`singleTask`) as a workbook tab via `WindowHandoffManager` UUID tokens (`C-02` & `C-04`).
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
 * [EN] UI/application helper `registerShelfWindowShortcuts` (register shelf window shortcuts).
 * [JA] `registerShelfWindowShortcuts` に対応するUI／アプリケーション補助処理を実行します。
 */
internal fun registerShelfWindowShortcuts(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    runCatching {
        val sm = context.getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
        val icon = android.graphics.drawable.Icon.createWithResource(context, R.mipmap.ic_launcher)

        // `M-12`: Dynamic launcher shortcuts target exported MainActivity and forward safely to internal SecondaryWorkbookActivity
        val newWinIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("masusheet://shortcut/new_window")
            putExtra("masusheet_shortcut_mode", "new_window")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
