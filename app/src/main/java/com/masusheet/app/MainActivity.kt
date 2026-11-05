package com.masusheet.app

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.storage.WindowHandoffManager
import com.masusheet.app.ui.MasuSheetScreen
import com.masusheet.app.ui.launchNewWorkbookWindow
import com.masusheet.app.ui.registerShelfWindowShortcuts
import com.masusheet.app.viewmodel.MasuSheetViewModel
import com.masusheet.app.viewmodel.onCreateNewBlankWorkbookSession
import com.masusheet.app.viewmodel.onLoadHandoffWorkbookSession
import com.masusheet.app.viewmodel.onMergeHandoffWorkbookSession
import com.masusheet.app.viewmodel.onOpenDocumentUriAsync

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
            !incomingUri.isNullOrBlank() && intent.data?.scheme == "content" -> {
                // [EN] Accept external document open intents strictly via Scoped Storage `content://` URIs
                //      to block Confused Deputy arbitrary `file://` exfiltration from untrusted callers.
                // [JA] 外部アプリからの Confused Deputy 任意 `file://` 読取を防ぐため、`content://` スキームのみを受け付けます。
                viewModel.onOpenDocumentUriAsync(incomingUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawable(ColorDrawable(MasuSheetConfig.DARK_BG_COLOR_INT))
        super.onCreate(savedInstanceState)

        WindowHandoffManager.pruneStaleHandoffFiles(this)
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
