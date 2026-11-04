package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] 60-Color Theme Palette, Standard Colors, & 24-Bit RGB/Hex Picker Dialog
 * Implements [SpreadsheetColorPickerDialog] for cell fill, font color, tab color, and per-element chart colors (All, Title, X-Axis, Y-Axis, Legend).
 *
 * ---
 * [JA / 日本語解説]
 * 60色テーマパレット・標準10色・最近使った色・RGBスライダー・HEX入力を備え、セル背景・文字色・シートタブ色・グラフ要素別文字色に対応する [SpreadsheetColorPickerDialog] です。
 * ============================================================================
 */

// ============================================================================
// 1. FULL SPREADSHEET COLOR PICKER DIALOG (60 THEME COLORS + 10 STANDARD + RECENT + 24-BIT RGB + HEX)
// ============================================================================

/**
 * [EN] Full color picker dialog offering 60 theme colors, 10 standard colors, recent swatches, RGB sliders, and `#RRGGBB` input.
 * [JA] 60色テーマカラー・標準10色・最近使った色・RGBスライダー・HEXコード入力を備えたカラーピッカーダイアログです。
 */
@Composable
fun SpreadsheetColorPickerDialog(
    state: MasuSheetUiState,
    initialTarget: String = "fill", // "fill", "font", "border", "objectFill", "objectStroke", "objectText", "objectText:title", "objectText:xAxis", "objectText:yAxis", "objectText:legend", "chartSeries", "sheetTab"
    sheetObjectsOnCurrentSheet: List<NativeSheetObject> = state.sheetObjects.filter { it.sheetIndex == state.viewport.sheetIndex },
    onSelectObject: (String?) -> Unit = {},
    onSelectSheetTab: (Int) -> Unit = {},
    onApplyColor: (target: String, argb: Int) -> Unit,
    onApplyThemePreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isEn = state.language == "en"
    val selectedObj = sheetObjectsOnCurrentSheet.firstOrNull { it.id == state.selectedObjectId }
    val normalizedInitialBase = initialTarget.substringBefore(':')
    val normalizedInitialSub = initialTarget.substringAfter(':', missingDelimiterValue = "")
    // Always default to "fill" (leftmost "🎨 セルの塗りつぶし") unless an object is actually selected or user asked for font/border/sheetTab
    val resolvedStartTarget = if (
        selectedObj == null &&
        normalizedInitialBase in setOf("objectFill", "objectStroke", "objectText", "chartSeries")
    ) {
        "fill"
    } else {
        normalizedInitialBase
    }
    var activeTarget by remember { mutableStateOf(resolvedStartTarget) }
    var chartTextSubElement by remember {
        mutableStateOf(
            when {
                normalizedInitialSub in setOf("title", "xAxis", "yAxis", "legend") -> normalizedInitialSub
                selectedObj?.selectedSubElement == "xAxis" -> "xAxis"
                selectedObj?.selectedSubElement == "yAxis" -> "yAxis"
                else -> "all"
            }
        )
    }
    var customR by remember { mutableIntStateOf(68) }
    var customG by remember { mutableIntStateOf(114) }
    var customB by remember { mutableIntStateOf(196) }
    var hexInput by remember { mutableStateOf("#4472C4") }

    val themeMatrix = remember(state.viewport.themeColors) {
        SpreadsheetColorPalette.buildThemeColorMatrix(state.viewport.themeColors)
    }

    val targets = listOf(
        "fill" to if (isEn) "🎨 Cell Fill" else "🎨 セルの塗りつぶし",
        "font" to if (isEn) "🔤 Font Color" else "🔤 文字の色",
        "border" to if (isEn) "▦ Border Color" else "▦ 罫線の色",
        "objectFill" to if (isEn) "▭ Shape/Chart Fill" else "▭ 図形・グラフ背景の塗りつぶし",
        "objectStroke" to if (isEn) "🖊️ Shape/Chart Outline" else "🖊️ 図形・グラフの枠線",
        "objectText" to if (isEn) "🔤 Shape/Chart Text Color" else "🔤 図形・グラフ内の文字色",
        "chartSeries" to if (isEn) "📊 Chart Series (Bars/Lines)" else "📊 グラフ系列(棒・線・円)の色",
        "sheetTab" to if (isEn) "📑 Sheet Tab Color" else "📑 シート見出しの色"
    )

    val vp = state.viewport
    val selRangeStr = if (vp.selStartRow == vp.selEndRow && vp.selStartCol == vp.selEndCol) {
        vp.activeAddress
    } else {
        "${MasuSheetConfig.formatCellAddress(vp.selStartRow, vp.selStartCol)}:${MasuSheetConfig.formatCellAddress(vp.selEndRow, vp.selEndCol)}"
    }
    val isObjectTarget = activeTarget in setOf("objectFill", "objectStroke", "objectText", "chartSeries")
    val effectiveObj = selectedObj ?: sheetObjectsOnCurrentSheet.firstOrNull()

    fun resolveEffectiveColorTarget(): String {
        return if (activeTarget == "objectText" && effectiveObj?.kind == SheetObjectKind.CHART && chartTextSubElement != "all") {
            "objectText:$chartTextSubElement"
        } else {
            activeTarget
        }
    }

    fun formatObjectLabel(obj: NativeSheetObject): String {
        val kindIcon = when (obj.kind) {
            SheetObjectKind.CHART -> "📊"
            SheetObjectKind.SHAPE -> "▭"
            SheetObjectKind.TEXT_BOX -> "📝"
            SheetObjectKind.IMAGE -> "🖼️"
        }
        val kindName = when (obj.kind) {
            SheetObjectKind.CHART -> if (isEn) "Chart" else "グラフ"
            SheetObjectKind.SHAPE -> if (isEn) "Shape(${obj.subType})" else "図形(${obj.subType})"
            SheetObjectKind.TEXT_BOX -> if (isEn) "TextBox" else "テキスト"
            SheetObjectKind.IMAGE -> if (isEn) "Image" else "画像"
        }
        val titlePart = obj.title.trim().take(18).ifEmpty { obj.id }
        return "$kindIcon $kindName「$titlePart」"
    }

    BackHandler(onBack = onDismiss)
    androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides true) {
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
                    .widthIn(max = 630.dp)
                    .heightIn(max = 680.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.5.dp, Color(0xFF1F9D55), RoundedCornerShape(12.dp))
                    .clickable(enabled = false) {}
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) {
                            "🎨 Color Picker (60 Theme · 10 Standard · 24-bit RGB · HEX)"
                        } else {
                            "🎨 フルカラーパレット (操作対象の確認・選択 + テーマ60色・標準10色・RGB)"
                        },
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF334155))
                            .border(1.dp, Color(0xFF64748B), RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEn) "✕ Close" else "✕ 閉じる",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 1. Target category selector
                Text(
                    text = if (isEn) "① Select what attribute to color:" else "① 色を変更する箇所（カテゴリ）を選択:",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    targets.forEach { (id, label) ->
                        MasuSheetPillButton(
                            label = label,
                            highlight = activeTarget == id,
                            onClick = {
                                activeTarget = id
                                if (id in setOf("objectFill", "objectStroke", "objectText", "chartSeries") &&
                                    selectedObj == null &&
                                    sheetObjectsOnCurrentSheet.isNotEmpty()
                                ) {
                                    onSelectObject(sheetObjectsOnCurrentSheet.first().id)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Current Target Inspection & Interactive Object/Cell/Sheet Selector Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.2.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        activeTarget in setOf("fill", "font", "border") -> {
                            val activeCell = vp.cellAt(vp.activeRow, vp.activeCol)
                            val curCellColor = when (activeTarget) {
                                "fill" -> activeCell?.bgColor ?: 0
                                "font" -> activeCell?.fgColor ?: 0
                                else -> activeCell?.borderTop?.takeIf { it != 0 }?.let { (it and 0x00FFFFFF) or 0xFF000000.toInt() } ?: 0
                            }
                            val attrNameJa = when (activeTarget) {
                                "fill" -> "セルの塗りつぶし"
                                "font" -> "セルの文字色"
                                else -> "セルの罫線色"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (curCellColor != 0) Color(curCellColor) else Color(0xFF334155))
                                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                                    )
                                    Text(
                                        text = if (isEn) {
                                            "🎯 Current Target: Cell Range [$selRangeStr] on sheet '${vp.sheetName}'"
                                        } else {
                                            "🎯 現在の適用対象: セル範囲 【$selRangeStr】 (シート: ${vp.sheetName} / $attrNameJa)"
                                        },
                                        color = Color(0xFF38BDF8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (state.selectedObjectId != null) {
                                    MasuSheetPillButton(
                                        label = if (isEn) "Deselect Shape" else "図形選択を解除してセル優先",
                                        onClick = { onSelectObject(null) }
                                    )
                                }
                            }
                            val previewVal = activeCell?.displayText?.takeIf { it.isNotBlank() } ?: (if (isEn) "(Empty cell)" else "(空白セル)")
                            Text(
                                text = if (isEn) {
                                    "Active cell ${vp.activeAddress} content: $previewVal"
                                } else {
                                    "選択中セル ${vp.activeAddress} の内容: 「$previewVal」 — パレットの色をタップするとこのセル範囲に適用されます。"
                                },
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }

                        isObjectTarget -> {
                            val activeObjForPreview = effectiveObj
                            if (activeObjForPreview != null) {
                                val currentObjColor = when (activeTarget) {
                                    "objectFill" -> activeObjForPreview.fillColor
                                    "objectStroke" -> activeObjForPreview.strokeColor
                                    "objectText" -> when (chartTextSubElement) {
                                        "title" -> activeObjForPreview.chartTitleTextColor.takeIf { it != 0 } ?: activeObjForPreview.textColor
                                        "xAxis" -> activeObjForPreview.chartXAxisTextColor.takeIf { it != 0 } ?: activeObjForPreview.textColor
                                        "yAxis" -> activeObjForPreview.chartYAxisTextColor.takeIf { it != 0 } ?: activeObjForPreview.textColor
                                        "legend" -> activeObjForPreview.chartLegendTextColor.takeIf { it != 0 } ?: activeObjForPreview.textColor
                                        else -> activeObjForPreview.textColor
                                    }
                                    "chartSeries" -> activeObjForPreview.chartSeries.firstOrNull()?.color ?: 0
                                    else -> activeObjForPreview.fillColor
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (currentObjColor != 0) Color(currentObjColor) else Color(0xFF334155))
                                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                                    )
                                    Text(
                                        text = if (isEn) {
                                            "🎯 Selected Target Object: ${formatObjectLabel(activeObjForPreview)}"
                                        } else {
                                            "🎯 現在選択中の対象オブジェクト: 【${formatObjectLabel(activeObjForPreview)}】"
                                        },
                                        color = Color(0xFF4ADE80),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = if (isEn) {
                                        "💡 No shapes/charts exist on this sheet yet. Select a cell category above or insert a shape/chart first."
                                    } else {
                                        "💡 このシートにはまだ図形・グラフがありません。先に図形やグラフを挿入するか、上の「セルの塗りつぶし」等を選択してください。"
                                    },
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.5.sp
                                )
                            }

                            if (sheetObjectsOnCurrentSheet.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEn) "② Target Object:" else "② 対象オブジェクトを選択:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    sheetObjectsOnCurrentSheet.forEach { obj ->
                                        val isThisSelected = obj.id == activeObjForPreview?.id
                                        MasuSheetPillButton(
                                            label = (if (isThisSelected) "✅ " else "") + formatObjectLabel(obj),
                                            highlight = isThisSelected,
                                            onClick = { onSelectObject(obj.id) }
                                        )
                                    }
                                }
                            }

                            // Per-element text color selector when coloring a Chart's text!
                            if (activeTarget == "objectText" && activeObjForPreview?.kind == SheetObjectKind.CHART) {
                                val textElemOptions = listOf(
                                    "all" to if (isEn) "🎯 All Text" else "🎯 すべて一括",
                                    "title" to if (isEn) "🏷️ Chart Title" else "🏷️ グラフタイトル",
                                    "xAxis" to if (isEn) "📏 X-Axis Labels" else "📏 X軸 (横軸)",
                                    "yAxis" to if (isEn) "📐 Y-Axis Labels" else "📐 Y軸 (縦軸)",
                                    "legend" to if (isEn) "📋 Legend & Range" else "📋 凡例・右上表示"
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEn) "③ Chart Element to Color:" else "③ 文字色を変えるグラフ要素:",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    textElemOptions.forEach { (elemId, elemLabel) ->
                                        val isElemSel = chartTextSubElement == elemId
                                        MasuSheetPillButton(
                                            label = (if (isElemSel) "● " else "") + elemLabel,
                                            highlight = isElemSel,
                                            onClick = { chartTextSubElement = elemId }
                                        )
                                    }
                                }
                            }
                        }

                        activeTarget == "sheetTab" -> {
                            val curTabColor = state.sheetTabColors[vp.sheetIndex] ?: 0
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (curTabColor != 0) Color(curTabColor) else Color(0xFF334155))
                                        .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = if (isEn) {
                                        "🎯 Current Target: Sheet Tab '${vp.sheetName}' (Sheet #${vp.sheetIndex + 1})"
                                    } else {
                                        "🎯 現在の適用対象: シート見出し 【${vp.sheetName}】 (シート ${vp.sheetIndex + 1})"
                                    },
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (vp.sheetNames.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEn) "Switch target sheet:" else "対象シートを切替:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                    vp.sheetNames.forEachIndexed { idx, sName ->
                                        val isCur = idx == vp.sheetIndex
                                        MasuSheetPillButton(
                                            label = (if (isCur) "✅ " else "📑 ") + sName,
                                            highlight = isCur,
                                            onClick = { onSelectSheetTab(idx) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // No Fill / Automatic + Theme Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MasuSheetPillButton(
                        label = if (isEn) "🚫 No Fill / Automatic" else "🚫 塗りつぶしなし / 自動 (クリア)",
                        highlight = true,
                        onClick = {
                            onApplyColor(resolveEffectiveColorTarget(), 0)
                            onDismiss()
                        }
                    )
                    Text(if (isEn) "Workbook Theme:" else "ブックのテーマ配色:", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    SpreadsheetColorPalette.THEME_PRESETS.keys.forEach { presetName ->
                        MasuSheetPillButton(
                            label = SpreadsheetI18n.t(presetName, isEn),
                            onClick = { onApplyThemePreset(presetName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1. Theme Colors (10 columns × 6 rows = 60 colors)
                Text(
                    text = if (isEn) {
                        "Theme Colors (10 Base Colors × 6 Tint/Shade Gradations = 60 Colors):"
                    } else {
                        "テーマの色 (10ベース色 × 6段階の明暗グラデーション = 60色):"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    themeMatrix.forEachIndexed { rowIdx, rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rowColors.forEach { argb ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 52.dp, height = if (rowIdx == 0) 24.dp else 19.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(argb))
                                        .border(0.8.dp, Color(0xFF64748B), RoundedCornerShape(3.dp))
                                        .clickable {
                                            onApplyColor(resolveEffectiveColorTarget(), argb)
                                            onDismiss()
                                        }
                                )
                            }
                        }
                        if (rowIdx == 0) {
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Standard Colors (10 colors)
                Text(
                    text = if (isEn) "Standard Colors (10 Colors):" else "標準の色 (10色):",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SpreadsheetColorPalette.STANDARD_COLORS.forEach { (_, argb) ->
                        Box(
                            modifier = Modifier
                                .size(width = 52.dp, height = 24.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(argb))
                                .border(0.8.dp, Color(0xFF94A3B8), RoundedCornerShape(3.dp))
                                .clickable {
                                    onApplyColor(resolveEffectiveColorTarget(), argb)
                                    onDismiss()
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Recent Colors
                if (state.recentColors.isNotEmpty()) {
                    Text(
                        text = if (isEn) "Recent Colors:" else "最近使用した色:",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.recentColors.take(10).forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(width = 52.dp, height = 22.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(argb))
                                    .border(0.8.dp, Color(0xFF94A3B8), RoundedCornerShape(3.dp))
                                    .clickable {
                                        onApplyColor(resolveEffectiveColorTarget(), argb)
                                        onDismiss()
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 4. Custom 24-bit RGB & Hex Editor ("その他の色...")
                Text(
                    text = if (isEn) {
                        "More Colors (24-bit RGB Sliders & #RRGGBB Hex Code):"
                    } else {
                        "その他の色 (24bit RGB スライダー & #RRGGBB 16進コード指定):"
                    },
                    color = Color(0xFF4ADE80),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                val previewArgb = SpreadsheetColorPalette.fromRgb(customR, customG, customB)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(previewArgb))
                            .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("R:${customR.toString().padStart(3)}", color = Color(0xFFF87171), fontSize = 11.sp, modifier = Modifier.width(44.dp))
                            Slider(
                                value = customR.toFloat(),
                                onValueChange = {
                                    customR = it.roundToInt().coerceIn(0, 255)
                                    hexInput = SpreadsheetColorPalette.toHexString(SpreadsheetColorPalette.fromRgb(customR, customG, customB))
                                },
                                valueRange = 0f..255f,
                                modifier = Modifier.height(22.dp).weight(1f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("G:${customG.toString().padStart(3)}", color = Color(0xFF4ADE80), fontSize = 11.sp, modifier = Modifier.width(44.dp))
                            Slider(
                                value = customG.toFloat(),
                                onValueChange = {
                                    customG = it.roundToInt().coerceIn(0, 255)
                                    hexInput = SpreadsheetColorPalette.toHexString(SpreadsheetColorPalette.fromRgb(customR, customG, customB))
                                },
                                valueRange = 0f..255f,
                                modifier = Modifier.height(22.dp).weight(1f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("B:${customB.toString().padStart(3)}", color = Color(0xFF60A5FA), fontSize = 11.sp, modifier = Modifier.width(44.dp))
                            Slider(
                                value = customB.toFloat(),
                                onValueChange = {
                                    customB = it.roundToInt().coerceIn(0, 255)
                                    hexInput = SpreadsheetColorPalette.toHexString(SpreadsheetColorPalette.fromRgb(customR, customG, customB))
                                },
                                valueRange = 0f..255f,
                                modifier = Modifier.height(22.dp).weight(1f)
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BasicTextField(
                            value = hexInput,
                            onValueChange = { raw ->
                                hexInput = raw
                                SpreadsheetColorPalette.parseHexOrNull(raw)?.let { parsed ->
                                    customR = (parsed ushr 16) and 0xFF
                                    customG = (parsed ushr 8) and 0xFF
                                    customB = parsed and 0xFF
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(Color(0xFF4ADE80)),
                            modifier = Modifier
                                .width(96.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        MasuSheetPillButton(
                            label = if (isEn) "✓ Apply Custom Color" else "✓ このカスタム色を適用",
                            highlight = true,
                            onClick = {
                                val chosen = SpreadsheetColorPalette.parseHexOrNull(hexInput) ?: previewArgb
                                onApplyColor(resolveEffectiveColorTarget(), chosen)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}
