package com.masusheet.app.ui.dialogs

import android.graphics.Path
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.ExistingFolderFileItem
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.ui.ribbon.MasuSheetPillButton
import kotlin.math.max
import kotlin.math.min

/**
 * ============================================================================
 * [EN] Workbook Save / Export / Folder Browser Dialog
 * Implements [MasuSheetSaveDialog] supporting `.xlsx`, `.csv`, `.tsv`, `.ods`, `.html`, and `.pdf` export across Android Downloads/Documents, SAF folders, and Linux workspace directories.
 *
 * ---
 * [JA / 日本語解説]
 * Android本体のダウンロード／ドキュメントフォルダ、SAF選択フォルダ、およびLinux共有フォルダに対して `.xlsx` / `.csv` / `.tsv` / `.html` / `.pdf` 等での保存・既存ファイル読込を行う [MasuSheetSaveDialog] です。
 * ============================================================================
 */

/**
 * [EN] Workbook Save/Export dialog with folder picker, format selector, overwrite confirmation, and quick folder file browser.
 * [JA] 保存先フォルダ切替・拡張子選択・同名上書き確認・フォルダ内既存ファイル一覧を備えた保存／エクスポートダイアログです。
 */
@Composable
fun MasuSheetSaveDialog(
    state: MasuSheetUiState,
    onUpdateBaseName: (String) -> Unit,
    onSelectExtension: (String) -> Unit,
    onSelectFolder: (String) -> Unit,
    onPickCustomFolder: () -> Unit = {},
    onImportExternalFiles: () -> Unit = {},
    onSelectExistingFile: (ExistingFolderFileItem) -> Unit,
    onOpenExistingFile: (ExistingFolderFileItem) -> Unit = {},
    onSaveDirectly: () -> Unit,
    onOpenSystemPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isEn = state.language == "en"
    val finalFileName = MasuSheetConfig.buildFileNameWithExtension(
        state.saveDialogBaseName,
        state.saveDialogExtension
    )
    val saveExtensions = listOf("xlsx", "csv", "tsv", "ods", "pdf")
    val folderPresets = remember(context, state.saveDialogFolderPath) {
        val base = MasuSheetConfig.resolveEnvironmentSaveFolders(context).toMutableList()
        if (state.saveDialogFolderPath.isNotBlank() && base.none { it.second == state.saveDialogFolderPath }) {
            val decoded = try {
                Uri.decode(state.saveDialogFolderPath)
            } catch (_: Exception) {
                state.saveDialogFolderPath
            }
            val customLabel = decoded
                .substringAfterLast('/')
                .substringAfterLast(':')
                .ifBlank { "Custom" }
            base.add("📱 $customLabel (Android選択)" to state.saveDialogFolderPath)
        }
        base
    }
    val activeFolderLabel = folderPresets.firstOrNull { it.second == state.saveDialogFolderPath }?.first
        ?: state.saveDialogFolderPath.substringAfterLast('/').ifBlank { "Folder" }

    val folderExplanation = remember(state.saveDialogFolderPath, isEn) {
        val p = state.saveDialogFolderPath
        val lower = p.lowercase()
        when {
            p.startsWith("content://") -> {
                val decoded = try { Uri.decode(p) } catch (_: Exception) { p }
                val shortName = decoded.substringAfterLast(':').ifBlank { decoded }
                if (isEn) {
                    "📱 Android Local Folder (SAF Direct Access): Internal Storage / $shortName"
                } else {
                    "📱 Android本体ローカルフォルダ (「ファイル」アプリ直結): 内部ストレージ / $shortName"
                }
            }
            lower.endsWith("/download") || lower.contains("/download/") -> {
                if (isEn) {
                    "📱 Android Local 'Downloads' Folder (Accessible in Android Files app → Downloads | Path: $p)"
                } else {
                    "📱 Android本体の「ダウンロード」フォルダ (Androidの「ファイル」アプリ ＞ ダウンロード に直接保存されます | 実体パス: $p)"
                }
            }
            lower.endsWith("/documents") || lower.contains("/documents/") -> {
                if (isEn) {
                    "📱 Android Local 'Documents' Folder (Accessible in Android Files app → Documents | Path: $p)"
                } else {
                    "📱 Android本体の「ドキュメント」フォルダ (Androidの「ファイル」アプリ ＞ ドキュメント に直接保存されます | 実体パス: $p)"
                }
            }
            lower.contains("/android/data/") -> {
                if (isEn) {
                    "📱 Android App Storage Folder ($p)"
                } else {
                    "📱 Androidアプリ専用ローカルフォルダ ($p)"
                }
            }
            else -> {
                if (isEn) {
                    "🖥️ Shared Workspace Folder ($p)"
                } else {
                    "🖥️ Linux VM共有ワークスペースフォルダ ($p)"
                }
            }
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF475569), RoundedCornerShape(10.dp))
                .clickable(enabled = false) {}
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) {
                        "💾 Save / Browse Folder (Android Local Downloads & Documents Supported)"
                    } else {
                        "💾 名前を付けて保存 (Android本体のダウンロード・ドキュメント直接保存対応)"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                MasuSheetPillButton(if (isEn) "✕ Close" else "✕ 閉じる", onClick = onDismiss)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1. Dynamic Environment Folder Selector Chips + Device Folder Browser
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (isEn) "Save Folder:" else "保存先:", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                folderPresets.forEach { (label, path) ->
                    val isSelected = state.saveDialogFolderPath == path
                    MasuSheetPillButton(
                        label = label,
                        highlight = isSelected,
                        onClick = { onSelectFolder(path) }
                    )
                }
                MasuSheetPillButton(
                    label = if (isEn) "📂 Browse Subfolder..." else "📂 サブフォルダ参照...",
                    onClick = onPickCustomFolder
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = folderExplanation,
                color = Color(0xFF38BDF8),
                fontSize = 10.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(7.dp))

            // 2. Clickable list of existing files in the SELECTED folder (switches dynamically per folder!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) {
                        "📂 Files in [$activeFolderLabel] (${state.saveDialogExistingFiles.size} files):"
                    } else {
                        "📂 「$activeFolderLabel」内のファイル (${state.saveDialogExistingFiles.size}件 — クリックで名前反映 / [開く]で読込):"
                    },
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )
                MasuSheetPillButton(
                    label = if (isEn) "➕ Add Other Apps' Files (Chrome etc.)" else "➕ 他アプリのファイル(Chrome等)を選択して一覧に追加",
                    highlight = true,
                    onClick = onImportExternalFiles
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isEn) {
                    "※ Due to Android 11+ security, files saved by other apps (Chrome/Gmail) appear here after you select them once via [➕ Add Other Apps' Files]."
                } else {
                    "※Android 11以降の保護仕様上、Chrome等の別アプリが保存したファイルは右上の「➕ 他アプリのファイルを選択して一覧に追加」で一度選択するとここに常時表示されます。"
                },
                color = Color(0xFF94A3B8),
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp, max = 136.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                    .padding(6.dp)
            ) {
                if (state.saveDialogExistingFiles.isEmpty()) {
                    Text(
                        text = if (isEn) {
                            "No spreadsheet files shown in [$activeFolderLabel] yet. Click [➕ Add Other Apps' Files] above to select files downloaded by Chrome/Gmail."
                        } else {
                            "「$activeFolderLabel」内のスプレッドシートはまだ表示されていません。右上の「➕ 他アプリのファイル(Chrome等)を選択して一覧に追加」からファイルを選択してください。"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.saveDialogExistingFiles, key = { it.fullPath }) { fileItem ->
                            val isMatching = fileItem.fileName.equals(finalFileName, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isMatching) Color(0xFF163828) else Color.Transparent)
                                    .clickable { onSelectExistingFile(fileItem) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📄 ${fileItem.fileName}",
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = ".${fileItem.extension}",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                MasuSheetPillButton(
                                    label = if (isEn) "📂 Open" else "📂 開く",
                                    onClick = { onOpenExistingFile(fileItem) }
                                )
                            }
                        }
                    }
                }
            }

            // Optional separate row for Recently Used Workbook Names (so recent files are handy without polluting folder contents)
            if (state.recentWorkbooks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = if (isEn) "🕒 Recent Names:" else "🕒 最近使ったファイル名:",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp
                    )
                    state.recentWorkbooks.take(6).forEach { recentName ->
                        val ext = recentName.substringAfterLast('.', "").lowercase()
                        val base = if (ext.isNotEmpty()) recentName.substringBeforeLast('.') else recentName
                        MasuSheetPillButton(
                            label = "🕒 $recentName",
                            onClick = {
                                onSelectExistingFile(
                                    ExistingFolderFileItem(
                                        fileName = recentName,
                                        baseName = base.ifBlank { recentName },
                                        extension = ext.ifBlank { state.saveDialogExtension },
                                        fullPath = recentName
                                    )
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Base filename input (without extension)
            Text(
                text = if (isEn) {
                    "File Name (without extension):"
                } else {
                    "ファイル名 (拡張子は入力不要・名前だけ入力):"
                },
                color = Color(0xFFCBD5E1),
                fontSize = 11.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicTextField(
                value = state.saveDialogBaseName,
                onValueChange = onUpdateBaseName,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(Color(0xFF1F9D55)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1F9D55), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Selectable extension pills (prevents .bin!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (isEn) "Select Format:" else "拡張子を選択:", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                saveExtensions.forEach { ext ->
                    val selected = state.saveDialogExtension.equals(ext, ignoreCase = true)
                    MasuSheetPillButton(
                        label = ".$ext",
                        highlight = selected,
                        onClick = { onSelectExtension(ext) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isEn) "Output File Name: $finalFileName" else "保存されるファイル名: $finalFileName",
                color = Color(0xFF4ADE80),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Save buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MasuSheetPillButton(
                    label = if (isEn) "📂 Save via Android 'Files' App" else "📂 Androidの「ファイル」画面で場所を選んで保存",
                    onClick = onOpenSystemPicker
                )
                Spacer(modifier = Modifier.width(8.dp))
                MasuSheetPillButton(
                    label = if (isEn) "💾 Save to This Folder Now" else "💾 このフォルダにすぐ保存",
                    highlight = true,
                    onClick = onSaveDirectly
                )
            }
        }
    }
}


