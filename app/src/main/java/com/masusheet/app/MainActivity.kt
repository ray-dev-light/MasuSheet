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
 * [EN] Android Application Entry Activity & Multi-Window Secondary Activity
 * Defines [MainActivity] and [SecondaryWorkbookActivity] handling app startup, safe UUID window handoff intents (`C-02`), document open intents, and Compose theme setup.
 *
 * ---
 * [JA / 日本語解説]
 * アプリのメインActivity ([MainActivity]) およびマルチウィンドウ用セカンダリActivity ([SecondaryWorkbookActivity]) を定義し、UUID検証付きウィンドウハンドオフ (`C-02`) やファイル起動Intentを処理します。
 * ============================================================================
 */

/**
 * [EN] Primary Android entry Activity hosting the Compose theme, multi-window shortcuts, and safe UUID handoff routing.
 * [JA] アプリのメインエントリーActivity。Composeテーマ適用、ショートカット登録、およびUUID検証付き別ウィンドウ引き継ぎを担当します。
 */
open class MainActivity : ComponentActivity() {

    protected val viewModel: MasuSheetViewModel by viewModels {
        MasuSheetViewModel.provideFactory(this)
    }

    internal val activeViewModelForTest: MasuSheetViewModel
        get() = viewModel

    /**
     * [EN] Routes incoming Shelf/Taskbar shortcut intents, multi-window UUID handoffs (`C-02`),
     *      and document open intents without ever discarding existing workbook sessions or active cell edits.
     * [JA] シェルフの右クリックショートカット、UUID検証付き別ウィンドウ引き継ぎ (`C-02`)、およびファイル起動 Intent を、
     *      既存のブックデータや入力途中のセル編集を一切失わずに処理します。
     */
    private fun handleWindowIntent(intent: Intent?, isNewIntent: Boolean) {
        if (intent == null) return
        if (isNewIntent) {
            viewModel.flushPendingUiEditsBeforeNavigation()
        }

        val shortcutMode = intent.getStringExtra("masusheet_shortcut_mode")
        if (shortcutMode == "new_window") {
            // [EN] When MainActivity is already open (isNewIntent == true), spawn an independent SecondaryWorkbookActivity
            //      window while leaving all workbooks in MainActivity untouched. On a cold launch (isNewIntent == false),
            //      MainActivity itself just opened as the new window.
            // [JA] 既に MainActivity が開いている状態（isNewIntent == true）では既存シートをそのまま保持して別ウィンドウを起動し、
            //      未起動からの初回起動時（isNewIntent == false）はこの MainActivity 自体を新しいウィンドウとして開きます。
            if (isNewIntent) {
                launchNewWorkbookWindow(this, blankWorkbook = false)
            }
            return
        }
        if (shortcutMode == "new_blank") {
            // [EN] Preserve all existing workbooks in the top Switch Workbook bar and add a new blank workbook session
            //      whenever MainActivity is already open or holds any user state; on a fresh cold start, Book1.xlsx is already blank.
            // [JA] 起動中のウィンドウがある場合は既存ブックを上部の切替バーに保持したまま新規空白ブックを追加し、
            //      コールドスタート時は初期化済みの空白 Book1.xlsx をそのまま使用します。
            val curState = viewModel.uiState.value
            if (isNewIntent || curState.isDirty || curState.openWorkbooks.size > 1 || curState.viewport.cells.isNotEmpty()) {
                viewModel.onCreateNewBlankWorkbookSession()
            }
            return
        }

        val winMode = intent.getStringExtra("masusheet_window_mode")
        val incomingUri = intent.dataString
        when {
            winMode == "handoff" || winMode == "merge_into_main" -> {
                val token = intent.getStringExtra("masusheet_handoff_token")
                val consumed = WindowHandoffManager.consumeHandoffSession(this, token) ?: return
                if (winMode == "merge_into_main") {
                    viewModel.onMergeHandoffWorkbookSession(consumed.first)
                } else {
                    viewModel.onLoadHandoffWorkbookSession(consumed.first)
                }
            }
            winMode == "blank" || winMode == "new_blank" -> {
                val curState = viewModel.uiState.value
                if (isNewIntent || curState.isDirty || curState.openWorkbooks.size > 1 || curState.viewport.cells.isNotEmpty()) {
                    viewModel.onCreateNewBlankWorkbookSession()
                }
            }
            !incomingUri.isNullOrBlank() && !incomingUri.startsWith("masusheet://") -> {
                viewModel.onOpenDocumentUri(incomingUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawable(ColorDrawable(MasuSheetConfig.DARK_BG_COLOR_INT))
        super.onCreate(savedInstanceState)

        if (this !is SecondaryWorkbookActivity) {
            registerShelfWindowShortcuts(this)
        }
        if (savedInstanceState == null) {
            handleWindowIntent(intent, isNewIntent = false)
        }

        setContent {
            val state by viewModel.uiState.collectAsState()
            val scheme = if (state.isDarkTheme) {
                darkColorScheme(
                    primary = Color(0xFF1F9D55),
                    background = Color(0xFF14181D),
                    surface = Color(0xFF1E242B),
                    onBackground = Color(0xFFF1F5F9),
                    onSurface = Color(0xFFF1F5F9)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF15803D),
                    background = Color(0xFFF8FAFC),
                    surface = Color(0xFFFFFFFF),
                    onBackground = Color(0xFF0F172A),
                    onSurface = Color(0xFF0F172A)
                )
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = scheme.background
                ) {
                    MasuSheetScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWindowIntent(intent, isNewIntent = true)
    }
}

/**
 * [EN] Secondary independent OS Window Activity (`documentLaunchMode="always"`, `launchMode="standard"`, `:window2` process)
 *      launched from the Shelf/Taskbar right-click menu (`New Window`) or in-app `🪟 New Window` button.
 * [JA] シェルフ／タスクバーの右クリックメニュー（「新しいウィンドウ」）やアプリ内の「🪟 別ウィンドウ」ボタンから起動される
 *      独立プロセス (`:window2`) のセカンダリウィンドウ Activity です。
 */
class SecondaryWorkbookActivity : MainActivity()
