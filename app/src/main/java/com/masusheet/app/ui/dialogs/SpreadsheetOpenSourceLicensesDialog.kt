package com.masusheet.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ============================================================================
 * [EN] In-App About & Open-Source Software Licenses Dialog
 * Displays MasuSheet version information, dual `MIT OR Apache-2.0` licensing,
 * upstream GridCraft attribution, trademark disclaimers, and full offline texts
 * read from bundled APK assets (`assets/licenses/NOTICE.txt`, `LICENSE-APACHE.txt`,
 * `LICENSE-MIT.txt`, and `THIRD_PARTY_LICENSES.txt`).
 *
 * ---
 * [JA / 日本語解説]
 * アプリ内「MasuSheet について・オープンソースライセンス」表示ダイアログです。
 * バージョン情報、`MIT OR Apache-2.0` デュアルライセンス表記、上流 GridCraft への謝辞、
 * 商標帰属注記に加え、APK 内 `assets/licenses/` に同梱された `NOTICE.txt`・
 * `LICENSE-APACHE.txt`・`LICENSE-MIT.txt`・`THIRD_PARTY_LICENSES.txt` の全文を
 * オフラインで閲覧できます。
 * ============================================================================
 */

private enum class LicenseDocTab(val assetPath: String, val labelJa: String, val labelEn: String) {
    NOTICE("licenses/NOTICE.txt", "NOTICE (謝辞・商標)", "NOTICE"),
    APACHE("licenses/LICENSE-APACHE.txt", "Apache-2.0", "Apache-2.0"),
    MIT("licenses/LICENSE-MIT.txt", "MIT", "MIT"),
    THIRD_PARTY("licenses/THIRD_PARTY_LICENSES.txt", "第三者ライブラリ一覧", "Third-Party Crates & Libs")
}

/**
 * [EN] Renders the modal dialog displaying bundled OSS license files on `Dispatchers.IO`.
 * [JA] 同梱された OSS ライセンス文書を `Dispatchers.IO` で非同期読込して表示するモーダルダイアログです。
 */
@Composable
fun SpreadsheetOpenSourceLicensesDialog(
    isEn: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(LicenseDocTab.NOTICE) }
    var loadedText by remember { mutableStateOf("") }

    LaunchedEffect(activeTab) {
        loadedText = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(activeTab.assetPath).bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }.getOrElse { err ->
                if (isEn) {
                    "Unable to load ${activeTab.assetPath}: ${err.message}"
                } else {
                    "${activeTab.assetPath} を読み込めませんでした: ${err.message}"
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 760.dp)
                .fillMaxWidth(0.94f)
                .heightIn(min = 360.dp, max = 640.dp)
                .testTag("ms_dialog_oss_licenses"),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEn) {
                                "📜 About MasuSheet & Open Source Licenses"
                            } else {
                                "📜 MasuSheet について・オープンソースライセンス"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEn) {
                                "MasuSheet v1.0.0 — Dual-licensed under Apache-2.0 OR MIT"
                            } else {
                                "MasuSheet v1.0.0 — Apache-2.0 または MIT デュアルライセンス"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("ms_btn_close_oss_licenses")
                    ) {
                        Text(if (isEn) "Close" else "閉じる")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LicenseDocTab.entries.forEach { tab ->
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            label = {
                                Text(
                                    text = if (isEn) tab.labelEn else tab.labelJa,
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = loadedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
