package com.masusheet.app.ui.ribbon

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masusheet.app.R
import com.masusheet.app.SecondaryWorkbookActivity
import com.masusheet.app.catalog.SpreadsheetChartTypeCatalog
import com.masusheet.app.core.InlineCheckboxHelper
import com.masusheet.app.core.SpreadsheetI18n
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.model.SheetObjectKind
import com.masusheet.app.ui.RIBBON_TABS
import com.masusheet.app.viewmodel.onToggleAiPanel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

import androidx.compose.foundation.layout.RowScope
import com.masusheet.app.model.NativeCellModel
import com.masusheet.app.model.NativeMergeRange

/**
 * ============================================================================
 * [EN] Home Ribbon Tab Groups (Clipboard, Font, Alignment, Number, Styles, Cells, Editing)
 * Renders the 7 grouped multi-row command sections of the default `Home` ribbon tab.
 *
 * ---
 * [JA / 日本語解説]
 * リボンの既定タブである「ホーム」タブの7グループ（クリップボード・フォント・配置・数値・スタイル・セル・編集）を描画するComposableモジュールです。
 * ============================================================================
 */

/**
 * [EN] Renders all command groups inside the `Home` ribbon tab.
 * [JA] 「ホーム」タブ内の全コマンドグループを描画します。
 */
@Composable
internal fun RowScope.MasuSheetHomeRibbonTabGroups(
    state: MasuSheetUiState,
    isEn: Boolean,
    activeCell: NativeCellModel?,
    activeMerge: NativeMergeRange?,
    onCommand: (String, String) -> Unit,
    onCopyClick: () -> Unit,
    onCutClick: () -> Unit,
    onPasteClick: () -> Unit,
    onPasteScreenshotClick: () -> Unit,
    onInsertImageClick: () -> Unit,
    onOpenColorPicker: (String) -> Unit,
    onAutoFitColClick: () -> Unit,
    onAutoFitRowClick: () -> Unit,
    onResizeColDeltaClick: (Float) -> Unit,
    onResizeRowDeltaClick: (Float) -> Unit,
    onAddSheetClick: () -> Unit,
    onOpenFunctionWizard: () -> Unit,
    onOpenCommandCenter: (String) -> Unit,
    onInsertCheckboxClick: () -> Unit
) {
    // Default: "Home" Tab Grouped Multi-Row Ribbon
    val activeCellIsWrap = activeCell?.isWrap == true
    val activeCellAlignKey = state.viewport.sheetIndex to NativeViewportSnapshot.packCellKey(state.viewport.activeRow, state.viewport.activeCol)
    val hasAllBorders = activeCell != null &&
        activeCell.borderTop != 0 && activeCell.borderBottom != 0 &&
        activeCell.borderLeft != 0 && activeCell.borderRight != 0
    val dispText = activeCell?.displayText.orEmpty()
    val isCurrencyFmt = dispText.contains("¥") || dispText.contains("￥") || dispText.contains("$")
    val isPercentFmt = dispText.endsWith("%")
    val isCommaFmt = dispText.contains(",") && !isCurrencyFmt
    val activeFontSize = (activeCell?.fontSize ?: 11f).roundToInt()

    // Group 1: Clipboard (クリップボード)
    SpreadsheetRibbonGroup(title = "クリップボード") {
        SpreadsheetTallRibbonButton(
            icon = "📋",
            label = "📌 貼付",
            hasDropdown = true,
            menuItems = listOf(
                SpreadsheetDropdownActionItem("📋 貼り付けのオプション", isHeader = true),
                SpreadsheetDropdownActionItem("📋 貼り付け (P) — セルまたはクリップボード画像を貼付") { onPasteClick() },
                SpreadsheetDropdownActionItem(
                    label = "🔢 値の貼り付け (Paste Values)",
                    subItems = listOf(
                        "123 値のみ貼り付け (V)" to { onCommand("edit.pasteSpecial", """{"mode":"values"}""") },
                        "123% 値と数値の書式 (A)" to { onCommand("edit.pasteSpecial", """{"mode":"values_and_number_formats"}""") },
                        "123🎨 値と元の書式 (E)" to { onCommand("edit.pasteSpecial", """{"mode":"values_and_source_formatting"}""") }
                    )
                ),
                SpreadsheetDropdownActionItem("fx 数式の貼り付け (F)") { onCommand("edit.pasteSpecial", """{"mode":"formulas"}""") },
                SpreadsheetDropdownActionItem("⇄ 行列を入れ替える (T)") { onCommand("home.pasteTranspose", "{}") },
                SpreadsheetDropdownActionItem("🎨 書式のみ貼り付け (R)") { onCommand("home.pasteFormatting", "{}") },
                SpreadsheetDropdownActionItem(label = "", isDivider = true),
                SpreadsheetDropdownActionItem("📸 クリップボード画像をシートへ貼り付け") { onPasteScreenshotClick() },
                SpreadsheetDropdownActionItem("🖼️ 画像ファイルを選択して挿入...") { onInsertImageClick() }
            ),
            onClick = onPasteClick
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SpreadsheetCompactRowButton("✂️", "✂️ 切取", onClick = onCutClick)
            SpreadsheetCompactRowButton("📄", "📋 コピー", onClick = onCopyClick)
        }
    }

    SpreadsheetRibbonGroupDivider()

    // Group 2: Font (フォント — 2-row Spreadsheet layout: [Calibri ▾] [11 ▾] [+][-] / [B][I][U][ab][⊞][⊞▾][🪣][A])
    SpreadsheetRibbonGroup(title = "フォント") {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                SpreadsheetDropdownBox(
                    valueText = "Calibri",
                    width = 82.dp,
                    menuItems = listOf(
                        SpreadsheetDropdownActionItem("🔤 フォントの選択", isHeader = true),
                        SpreadsheetDropdownActionItem("Yu Gothic (游ゴシック)") { onCommand("home.fontFamily", "{\"family\":\"Yu Gothic\"}") },
                        SpreadsheetDropdownActionItem("Meiryo (メイリオ)") { onCommand("home.fontFamily", "{\"family\":\"Meiryo\"}") },
                        SpreadsheetDropdownActionItem("Calibri (本文のフォント)") { onCommand("home.fontFamily", "{\"family\":\"Calibri\"}") },
                        SpreadsheetDropdownActionItem("Arial") { onCommand("home.fontFamily", "{\"family\":\"Arial\"}") },
                        SpreadsheetDropdownActionItem("Times New Roman") { onCommand("home.fontFamily", "{\"family\":\"Times New Roman\"}") },
                        SpreadsheetDropdownActionItem("Consolas (等幅)") { onCommand("home.fontFamily", "{\"family\":\"Consolas\"}") },
                        SpreadsheetDropdownActionItem("MS PGothic (ＭＳ Ｐゴシック)") { onCommand("home.fontFamily", "{\"family\":\"MS PGothic\"}") }
                    ),
                    onClick = {}
                )
                SpreadsheetDropdownBox(
                    valueText = "$activeFontSize",
                    width = 42.dp,
                    menuItems = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36).map { pt ->
                        SpreadsheetDropdownActionItem("${pt} pt") {
                            onCommand("home.fontSize", "{\"size\":$pt}")
                        }
                    },
                    onClick = { onCommand("home.increaseFontSize", "{}") }
                )
                SpreadsheetSmallIconBtn(symbol = "A▲", onClick = { onCommand("home.increaseFontSize", "{}") })
                SpreadsheetSmallIconBtn(symbol = "A▼", onClick = { onCommand("home.decreaseFontSize", "{}") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                SpreadsheetSmallIconBtn(
                    symbol = "B",
                    bold = true,
                    highlight = activeCell?.isBold == true,
                    tag = "ms_btn_bold",
                    onClick = { onCommand("home.bold", "{}") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "I",
                    italic = true,
                    highlight = activeCell?.isItalic == true,
                    tag = "ms_btn_italic",
                    onClick = { onCommand("home.italic", "{}") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "U",
                    underline = true,
                    highlight = activeCell?.isUnderline == true,
                    tag = "ms_btn_underline",
                    onClick = { onCommand("home.underline", "{}") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "ab",
                    strike = true,
                    highlight = activeCell?.isStrike == true,
                    tag = "ms_btn_strike",
                    onClick = { onCommand("home.strikethrough", "{}") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "⊞",
                    highlight = hasAllBorders,
                    tag = "ms_btn_borders_all",
                    onClick = {
                        if (hasAllBorders) {
                            onCommand("home.borders", "{\"preset\":\"none\",\"style\":\"none\"}")
                        } else {
                            onCommand("home.borders", "{\"preset\":\"all\",\"style\":\"thin\"}")
                        }
                    }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "▾",
                    menuItems = listOf(
                        SpreadsheetDropdownActionItem("⊞ 罫線 (Borders)", isHeader = true),
                        SpreadsheetDropdownActionItem("⊞ 格子 (すべての罫線)") { onCommand("home.borders", "{\"preset\":\"all\",\"style\":\"thin\"}") },
                        SpreadsheetDropdownActionItem("▢ 外枠 (Outside Borders)") { onCommand("home.borders", "{\"preset\":\"outline\",\"style\":\"thin\"}") },
                        SpreadsheetDropdownActionItem("┏━┓ 太い外枠 (Thick Box Border)") { onCommand("home.borders", "{\"preset\":\"outline\",\"style\":\"medium\"}") },
                        SpreadsheetDropdownActionItem("▁ 下罫線 (Bottom Border)") { onCommand("home.borders", "{\"preset\":\"bottom\",\"style\":\"thin\"}") },
                        SpreadsheetDropdownActionItem("▔ 上罫線 (Top Border)") { onCommand("home.borders", "{\"preset\":\"top\",\"style\":\"thin\"}") },
                        SpreadsheetDropdownActionItem("▏ 左罫線 (Left Border)") { onCommand("home.borders", "{\"preset\":\"left\",\"style\":\"thin\"}") },
                        SpreadsheetDropdownActionItem("▕ 右罫線 (Right Border)") { onCommand("home.borders", "{\"preset\":\"right\",\"style\":\"thin\"}") },
                        SpreadsheetDropdownActionItem("═ 下二重罫線 (Bottom Double Border)") { onCommand("home.borders", "{\"preset\":\"bottom\",\"style\":\"double\"}") },
                        SpreadsheetDropdownActionItem("━ 太い下罫線 (Thick Bottom Border)") { onCommand("home.borders", "{\"preset\":\"bottom\",\"style\":\"thick\"}") },
                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                        SpreadsheetDropdownActionItem("🚫 枠なし (No Border)") { onCommand("home.borders", "{\"preset\":\"none\",\"style\":\"none\"}") },
                        SpreadsheetDropdownActionItem("🎨 線の色を選択...") { onOpenColorPicker("border") }
                    ),
                    onClick = {}
                )
                SpreadsheetSmallIconBtn(
                    symbol = "🪣",
                    colorBar = if (activeCell != null && activeCell.bgColor != 0) Color(activeCell.bgColor) else Color(0xFFFACC15),
                    onClick = { onOpenColorPicker("fill") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "A",
                    bold = true,
                    colorBar = if (activeCell != null && activeCell.fgColor != 0) Color(activeCell.fgColor) else Color(0xFFDC2626),
                    onClick = { onOpenColorPicker("font") }
                )
            }
        }
    }

    SpreadsheetRibbonGroupDivider()

    // Group 3: Alignment (配置 — 2-row Spreadsheet layout: [⬆][↕][⬇][↵ Wrap Text] / [⬅][⬌][➡][⊞ Merge & Center ▾])
    SpreadsheetRibbonGroup(title = "配置") {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                SpreadsheetSmallIconBtn(
                    symbol = "⬆",
                    highlight = (activeCell == null || activeCell.vAlign == 0) && activeCellAlignKey in state.explicitTopAlignCells,
                    onClick = { onCommand("home.alignTop", "{}") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "↕",
                    highlight = activeCell?.vAlign == 1,
                    onClick = { onCommand("home.alignMiddle", "{}") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "⬇",
                    highlight = activeCell?.vAlign == 2,
                    onClick = { onCommand("home.alignBottom", "{}") }
                )
                SpreadsheetCompactRowButton(
                    icon = "↵",
                    label = "↩️ 折返し表示",
                    highlight = activeCellIsWrap,
                    onClick = { onCommand("home.wrapText", "{}") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                SpreadsheetSmallIconBtn(
                    symbol = "⬅",
                    highlight = (activeCell == null || activeCell.hAlign == 0) && activeCellAlignKey in state.explicitLeftAlignCells,
                    tag = "ms_btn_align_left",
                    onClick = { onCommand("home.alignLeft", "{}") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "⬌",
                    highlight = activeCell?.hAlign == 1,
                    tag = "ms_btn_align_center",
                    onClick = { onCommand("home.alignCenter", "{}") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "➡",
                    highlight = activeCell?.hAlign == 2,
                    tag = "ms_btn_align_right",
                    onClick = { onCommand("home.alignRight", "{}") }
                )
                SpreadsheetCompactRowButton(
                    icon = "⊞",
                    label = "⊞ 結合して中央",
                    highlight = activeMerge != null,
                    tag = "ms_btn_merge_center",
                    onClick = { onCommand("home.mergeCenter", "{}") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "▾",
                    menuItems = listOf(
                        SpreadsheetDropdownActionItem("⊞ セルの結合オプション", isHeader = true),
                        SpreadsheetDropdownActionItem("⊞ 結合して中央揃え (Merge & Center)") { onCommand("home.mergeCenter", "{}") },
                        SpreadsheetDropdownActionItem("↔ 横方向に結合 (Merge Across)") { onCommand("home.mergeAcross", "{}") },
                        SpreadsheetDropdownActionItem("⬚ セルの結合 (Merge Cells)") { onCommand("home.mergeCells", "{}") },
                        SpreadsheetDropdownActionItem("🔓 セル結合の解除 (Unmerge Cells)") { onCommand("home.unmergeCells", "{}") }
                    ),
                    onClick = {}
                )
            }
        }
    }

    SpreadsheetRibbonGroupDivider()

    // Group 4: Number (数値 — [General ▾] / [$][%][,][.00][→.0])
    SpreadsheetRibbonGroup(title = "数値") {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SpreadsheetDropdownBox(
                valueText = when {
                    isCurrencyFmt -> if (isEn) "Currency" else "通貨"
                    isPercentFmt -> if (isEn) "Percentage" else "パーセント"
                    isCommaFmt -> if (isEn) "Number" else "数値"
                    else -> if (isEn) "General" else "標準"
                },
                width = 108.dp,
                menuItems = listOf(
                    SpreadsheetDropdownActionItem("🔢 数値の表示形式", isHeader = true),
                    SpreadsheetDropdownActionItem("標準 (General)") { onCommand("home.numberFormat", "{\"format\":\"General\"}") },
                    SpreadsheetDropdownActionItem("数値 (1,234)") { onCommand("home.numberFormat", "{\"format\":\"#,##0\"}") },
                    SpreadsheetDropdownActionItem(
                        label = "通貨 (Currency)",
                        subItems = listOf(
                            "¥ 日本円 (¥1,234)" to { onCommand("home.numberFormat", "{\"format\":\"¥#,##0\"}") },
                            "$ 米ドル ($1,234.00)" to { onCommand("home.numberFormat", "{\"format\":\"$#,##0.00\"}") },
                            "€ ユーロ (€1,234.00)" to { onCommand("home.numberFormat", "{\"format\":\"€#,##0.00\"}") }
                        )
                    ),
                    SpreadsheetDropdownActionItem("会計 (¥ #,##0)") { onCommand("home.numberFormat", "{\"format\":\"¥#,##0\"}") },
                    SpreadsheetDropdownActionItem("短い日付 (2026/10/31)") { onCommand("home.numberFormat", "{\"format\":\"yyyy/mm/dd\"}") },
                    SpreadsheetDropdownActionItem("長い日付 (2026年10月31日)") { onCommand("home.numberFormat", "{\"format\":\"yyyy年m月d日\"}") },
                    SpreadsheetDropdownActionItem("パーセンテージ (0.0%)") { onCommand("home.numberFormat", "{\"format\":\"0.0%\"}") },
                    SpreadsheetDropdownActionItem("指数 (1.23E+03)") { onCommand("home.numberFormat", "{\"format\":\"0.00E+00\"}") },
                    SpreadsheetDropdownActionItem("文字列 (@)") { onCommand("home.numberFormat", "{\"format\":\"@\"}") }
                ),
                onClick = { onCommand("home.numberFormat", "{\"format\":\"General\"}") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                SpreadsheetSmallIconBtn(
                    symbol = "¥/$",
                    highlight = isCurrencyFmt,
                    onClick = {
                        if (isCurrencyFmt) {
                            onCommand("home.numberFormat", "{\"format\":\"General\"}")
                        } else {
                            onCommand("home.numberFormat", "{\"format\":\"¥#,##0\"}")
                        }
                    }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "%",
                    highlight = isPercentFmt,
                    onClick = {
                        if (isPercentFmt) {
                            onCommand("home.numberFormat", "{\"format\":\"General\"}")
                        } else {
                            onCommand("home.numberFormat", "{\"format\":\"0.0%\"}")
                        }
                    }
                )
                SpreadsheetSmallIconBtn(
                    symbol = ",",
                    highlight = isCommaFmt,
                    onClick = {
                        if (isCommaFmt) {
                            onCommand("home.numberFormat", "{\"format\":\"General\"}")
                        } else {
                            onCommand("home.numberFormat", "{\"format\":\"#,##0\"}")
                        }
                    }
                )
                SpreadsheetSmallIconBtn(
                    symbol = ".00",
                    onClick = { onCommand("home.increaseDecimal", "{}") }
                )
                SpreadsheetSmallIconBtn(
                    symbol = "→.0",
                    onClick = { onCommand("home.decreaseDecimal", "{}") }
                )
            }
        }
    }

    SpreadsheetRibbonGroupDivider()

    // Group 5: Styles (スタイル — Tall Conditional Formatting, Format as Table, Cell Styles)
    SpreadsheetRibbonGroup(title = "スタイル") {
        SpreadsheetTallRibbonButton(
            icon = "📊",
            label = "🌈 条件付き書式(データバー)",
            hasDropdown = true,
            highlight = state.activeConditionalFormat.isNotEmpty(),
            menuItems = listOf(
                SpreadsheetDropdownActionItem("🌈 条件付き書式 (Conditional Formatting)", isHeader = true),
                SpreadsheetDropdownActionItem(
                    label = "📏 セルの強調表示ルール (Highlight Cells Rules)",
                    subItems = listOf(
                        "指定の値より大きい (Greater Than)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"greaterThan\"}") },
                        "指定の値より小さい (Less Than)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"lessThan\"}") },
                        "次の値の間 (Between)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"between\"}") },
                        "指定の値に等しい (Equal To)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"equalTo\"}") },
                        "文字列を含む (Text that Contains)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"textContains\"}") },
                        "重複する値 (Duplicate Values)..." to { onCommand("home.conditionalFormatting", "{\"type\":\"duplicateValues\"}") }
                    )
                ),
                SpreadsheetDropdownActionItem(
                    label = "🔝 上位/下位ルール (Top/Bottom Rules)",
                    subItems = listOf(
                        "上位 10 項目 (Top 10 Items)" to { onCommand("home.conditionalFormatting", "{\"type\":\"top10\"}") },
                        "上位 10% (Top 10%)" to { onCommand("home.conditionalFormatting", "{\"type\":\"top10Percent\"}") },
                        "下位 10 項目 (Bottom 10 Items)" to { onCommand("home.conditionalFormatting", "{\"type\":\"bottom10\"}") },
                        "平均より上 (Above Average)" to { onCommand("home.conditionalFormatting", "{\"type\":\"aboveAverage\"}") },
                        "平均より下 (Below Average)" to { onCommand("home.conditionalFormatting", "{\"type\":\"belowAverage\"}") }
                    )
                ),
                SpreadsheetDropdownActionItem(
                    label = "📊 データバー (Data Bars)",
                    subItems = listOf(
                        "🟢 緑のデータバー (Green Data Bar)" to { onCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"color\":\"#1F9D55\"}") },
                        "🔵 青のデータバー (Blue Data Bar)" to { onCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"color\":\"#2563EB\"}") },
                        "🟠 オレンジのデータバー (Orange Data Bar)" to { onCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"color\":\"#D97706\"}") },
                        "🔴 赤のデータバー (Red Data Bar)" to { onCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"color\":\"#DC2626\"}") }
                    )
                ),
                SpreadsheetDropdownActionItem(
                    label = "🌈 カラースケール (Color Scales)",
                    subItems = listOf(
                        "緑 - 黄 - 赤 カラースケール" to { onCommand("home.conditionalFormatting", "{\"type\":\"colorScale\",\"scale\":\"GYR\"}") },
                        "赤 - 黄 - 緑 カラースケール" to { onCommand("home.conditionalFormatting", "{\"type\":\"colorScale\",\"scale\":\"RYG\"}") },
                        "青 - 白 - 赤 カラースケール" to { onCommand("home.conditionalFormatting", "{\"type\":\"colorScale\",\"scale\":\"BWR\"}") }
                    )
                ),
                SpreadsheetDropdownActionItem(
                    label = "🚦 アイコンセット (Icon Sets)",
                    subItems = listOf(
                        "⬆️➡️⬇️ 3方向の矢印" to { onCommand("home.conditionalFormatting", "{\"type\":\"iconSet\",\"set\":\"arrows3\"}") },
                        "🟢🟡🔴 3色の信号機" to { onCommand("home.conditionalFormatting", "{\"type\":\"iconSet\",\"set\":\"trafficLights3\"}") },
                        "✅⚠️❌ 3つの記号" to { onCommand("home.conditionalFormatting", "{\"type\":\"iconSet\",\"set\":\"symbols3\"}") }
                    )
                ),
                SpreadsheetDropdownActionItem(label = "", isDivider = true),
                SpreadsheetDropdownActionItem("🧹 ルールのクリア (Clear Rules)") { onCommand("home.clearConditionalFormatting", "{}") }
            ),
            onClick = { onCommand("home.conditionalFormatting", "{\"type\":\"dataBar\",\"color\":\"#1F9D55\"}") }
        )
        SpreadsheetTallRibbonButton(
            icon = "📅",
            label = "📊 テーブルとして書式設定",
            hasDropdown = true,
            highlight = state.isTableActive,
            menuItems = listOf(
                SpreadsheetDropdownActionItem("📊 テーブル スタイル", isHeader = true),
                SpreadsheetDropdownActionItem(
                    label = "🌤️ 淡色 (Light)",
                    subItems = listOf(
                        "テーブル スタイル (淡色 1 - 緑)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleLight1\"}") },
                        "テーブル スタイル (淡色 2 - 青)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleLight2\"}") },
                        "テーブル スタイル (淡色 9 - グレー)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleLight9\"}") }
                    )
                ),
                SpreadsheetDropdownActionItem(
                    label = "⛅ 中間 (Medium)",
                    subItems = listOf(
                        "テーブル スタイル (中間 2 - 標準緑)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleMedium2\"}") },
                        "テーブル スタイル (中間 4 - 赤)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleMedium4\"}") },
                        "テーブル スタイル (中間 6 - 青)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleMedium6\"}") }
                    )
                ),
                SpreadsheetDropdownActionItem(
                    label = "🌑 濃色 (Dark)",
                    subItems = listOf(
                        "テーブル スタイル (濃色 1 - 黒緑)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleDark1\"}") },
                        "テーブル スタイル (濃色 2 - 濃青)" to { onCommand("home.formatAsTable", "{\"style\":\"TableStyleDark2\"}") }
                    )
                )
            ),
            onClick = { onCommand("home.formatAsTable", "{\"style\":\"TableStyleMedium2\"}") }
        )
        SpreadsheetTallRibbonButton(
            icon = "🎨",
            label = "🎨 塗りつぶし色 (全60色+RGB)",
            hasDropdown = true,
            menuItems = listOf(
                SpreadsheetDropdownActionItem("🎨 セルのスタイル・塗りつぶし", isHeader = true),
                SpreadsheetDropdownActionItem("⬜ 標準 (Normal - 色なし)") { onCommand("home.cellStyle", "{\"style\":\"Normal\"}") },
                SpreadsheetDropdownActionItem("🟢 良い (Good - 緑ハイライト)") { onCommand("home.cellStyle", "{\"style\":\"Good\"}") },
                SpreadsheetDropdownActionItem("🔴 悪い (Bad - 赤ハイライト)") { onCommand("home.cellStyle", "{\"style\":\"Bad\"}") },
                SpreadsheetDropdownActionItem("🟡 中立 (Neutral - 黄ハイライト)") { onCommand("home.cellStyle", "{\"style\":\"Neutral\"}") },
                SpreadsheetDropdownActionItem(
                    label = "📊 見出しとタイトル (Titles & Headings)",
                    subItems = listOf(
                        "見出し 1 (Heading 1)" to { onCommand("home.cellStyle", "{\"style\":\"Heading 1\"}") },
                        "見出し 2 (Heading 2)" to { onCommand("home.cellStyle", "{\"style\":\"Heading 2\"}") },
                        "タイトル (Title)" to { onCommand("home.cellStyle", "{\"style\":\"Title\"}") },
                        "集計 (Total)" to { onCommand("home.cellStyle", "{\"style\":\"Total\"}") }
                    )
                ),
                SpreadsheetDropdownActionItem(label = "", isDivider = true),
                SpreadsheetDropdownActionItem("🎨 フル色パレット (60色+RGB) を開く...") { onOpenColorPicker("fill") }
            ),
            onClick = { onOpenColorPicker("fill") }
        )
    }

    SpreadsheetRibbonGroupDivider()

    // Group 6: Cells (セル — Insert / Delete / Format)
    SpreadsheetRibbonGroup(title = "セル") {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SpreadsheetCompactRowButton(
                    icon = "➕",
                    label = "➕ 行挿入",
                    hasDropdown = true,
                    menuItems = listOf(
                        SpreadsheetDropdownActionItem("➕ 挿入メニュー", isHeader = true),
                        SpreadsheetDropdownActionItem("➕ シートの行を挿入 (Insert Sheet Rows)") { onCommand("home.insertRows", "{}") },
                        SpreadsheetDropdownActionItem("➕ シートの列を挿入 (Insert Sheet Columns)") { onCommand("home.insertColumns", "{}") },
                        SpreadsheetDropdownActionItem("➕ セルを挿入 (下方向にシフト)") { onCommand("home.insertCells", "{\"shift\":\"down\"}") },
                        SpreadsheetDropdownActionItem("➕ 新しいシートを挿入 (Insert Sheet)") { onAddSheetClick() }
                    ),
                    onClick = { onCommand("home.insertRows", "{}") }
                )
                SpreadsheetCompactRowButton(
                    icon = "➕",
                    label = "➕ 列挿入",
                    onClick = { onCommand("home.insertColumns", "{}") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SpreadsheetCompactRowButton(
                    icon = "➖",
                    label = "➖ 行削除",
                    hasDropdown = true,
                    menuItems = listOf(
                        SpreadsheetDropdownActionItem("➖ 削除メニュー", isHeader = true),
                        SpreadsheetDropdownActionItem("➖ シートの行を削除 (Delete Sheet Rows)") { onCommand("home.deleteRows", "{}") },
                        SpreadsheetDropdownActionItem("➖ シートの列を削除 (Delete Sheet Columns)") { onCommand("home.deleteColumns", "{}") },
                        SpreadsheetDropdownActionItem("➖ セルを削除 (上方向にシフト)") { onCommand("home.deleteCells", "{\"shift\":\"up\"}") }
                    ),
                    onClick = { onCommand("home.deleteRows", "{}") }
                )
                SpreadsheetCompactRowButton(
                    icon = "📐",
                    label = "↔ 列幅自動",
                    hasDropdown = true,
                    menuItems = listOf(
                        SpreadsheetDropdownActionItem("📐 セルのサイズ・書式", isHeader = true),
                        SpreadsheetDropdownActionItem("↕ 行の高さの自動調整 (AutoFit Row Height)") { onAutoFitRowClick() },
                        SpreadsheetDropdownActionItem("↔ 列の幅の自動調整 (AutoFit Column Width)") { onAutoFitColClick() },
                        SpreadsheetDropdownActionItem("↕ 行の高さを広げる (+6pt)") { onResizeRowDeltaClick(6f) },
                        SpreadsheetDropdownActionItem("↔ 列の幅を広げる (+16pt)") { onResizeColDeltaClick(16f) },
                        SpreadsheetDropdownActionItem(label = "", isDivider = true),
                        SpreadsheetDropdownActionItem("🔒 シートの保護 (Protect Sheet)...") { onCommand("review.protectSheet", "{}") }
                    ),
                    onClick = onAutoFitColClick
                )
            }
        }
    }

    SpreadsheetRibbonGroupDivider()

    // Group 7: Editing (編集 — AutoSum / Clear / Sort & Filter / Find & Select)
    SpreadsheetRibbonGroup(title = "編集") {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SpreadsheetCompactRowButton(
                icon = "Σ",
                label = "Σ オートSUM (合計)",
                hasDropdown = true,
                menuItems = listOf(
                    SpreadsheetDropdownActionItem("Σ 合計 (SUM)") { onCommand("formulas.autoSum", "{\"function\":\"SUM\"}") },
                    SpreadsheetDropdownActionItem("x̄ 平均 (AVERAGE)") { onCommand("formulas.autoSum", "{\"function\":\"AVERAGE\"}") },
                    SpreadsheetDropdownActionItem("# 数値の個数 (COUNT)") { onCommand("formulas.autoSum", "{\"function\":\"COUNT\"}") },
                    SpreadsheetDropdownActionItem("⬆️ 最大値 (MAX)") { onCommand("formulas.autoSum", "{\"function\":\"MAX\"}") },
                    SpreadsheetDropdownActionItem("⬇️ 最小値 (MIN)") { onCommand("formulas.autoSum", "{\"function\":\"MIN\"}") },
                    SpreadsheetDropdownActionItem(label = "", isDivider = true),
                    SpreadsheetDropdownActionItem("fx その他の関数 (全523関数)...") { onOpenFunctionWizard() }
                ),
                onClick = { onCommand("formulas.autoSum", "{\"function\":\"SUM\"}") }
            )
            SpreadsheetCompactRowButton(
                icon = "🧹",
                label = "🧹 消去",
                hasDropdown = true,
                menuItems = listOf(
                    SpreadsheetDropdownActionItem("🧹 クリア (Clear)", isHeader = true),
                    SpreadsheetDropdownActionItem("🧹 すべてクリア (Clear All)") { onCommand("home.clearAll", "{}") },
                    SpreadsheetDropdownActionItem("🎨 書式のクリア (Clear Formats)") { onCommand("home.clearFormats", "{}") },
                    SpreadsheetDropdownActionItem("📝 数式と値のクリア (Clear Contents)") { onCommand("edit.clearContents", "{}") },
                    SpreadsheetDropdownActionItem("💬 コメントとメモのクリア (Clear Comments)") { onCommand("review.deleteComment", "{}") }
                ),
                onClick = { onCommand("edit.clearContents", "{}") }
            )
        }
        SpreadsheetTallRibbonButton(
            icon = "⇅",
            label = "🔽 オートフィルター切替",
            hasDropdown = true,
            highlight = state.isAutoFilterActive,
            menuItems = listOf(
                SpreadsheetDropdownActionItem("⇅ 並べ替えとフィルター", isHeader = true),
                SpreadsheetDropdownActionItem("🔼 昇順 (A → Z)") { onCommand("data.sortAscending", "{}") },
                SpreadsheetDropdownActionItem("🔽 降順 (Z → A)") { onCommand("data.sortDescending", "{}") },
                SpreadsheetDropdownActionItem("🗂️ ユーザー設定の並べ替え...") { onCommand("data.sortCustom", "{}") },
                SpreadsheetDropdownActionItem(label = "", isDivider = true),
                SpreadsheetDropdownActionItem("🔽 フィルター切替 (AutoFilter)") { onCommand("data.filter", "{}") },
                SpreadsheetDropdownActionItem("🚫 フィルターのクリア") { onCommand("data.clearFilter", "{}") }
            ),
            onClick = { onCommand("data.filter", "{}") }
        )
        SpreadsheetTallRibbonButton(
            icon = "🔍",
            label = "🔍 検索と置換",
            hasDropdown = true,
            menuItems = listOf(
                SpreadsheetDropdownActionItem("🔍 検索と選択 (Find & Select)", isHeader = true),
                SpreadsheetDropdownActionItem("🔍 検索 (Find)...") { onOpenCommandCenter("findReplace") },
                SpreadsheetDropdownActionItem("🔄 置換 (Replace)...") { onOpenCommandCenter("findReplace") },
                SpreadsheetDropdownActionItem("📍 ジャンプ (Go To)...") { onOpenCommandCenter("goTo") },
                SpreadsheetDropdownActionItem(
                    label = "📐 条件を選択してジャンプ (Go To Special)",
                    subItems = listOf(
                        "fx 数式 (Formulas)" to { onCommand("edit.goToSpecial", "{\"mode\":\"formulas\"}") },
                        "🔢 定数 (Constants)" to { onCommand("edit.goToSpecial", "{\"mode\":\"constants\"}") },
                        "⬜ 空白セル (Blanks)" to { onCommand("edit.goToSpecial", "{\"mode\":\"blanks\"}") },
                        "💬 コメント (Comments)" to { onCommand("edit.goToSpecial", "{\"mode\":\"comments\"}") },
                        "🌈 条件付き書式 (Conditional Formats)" to { onOpenCommandCenter("conditional") },
                        "✅ データの入力規則 (Data Validation)" to { onOpenCommandCenter("validation") }
                    )
                ),
                SpreadsheetDropdownActionItem(label = "", isDivider = true),
                SpreadsheetDropdownActionItem("🌈 条件付き書式 (Conditional Formatting)...") { onOpenCommandCenter("conditional") },
                SpreadsheetDropdownActionItem("✅ データの入力規則 (Data Validation)...") { onOpenCommandCenter("validation") }
            ),
            onClick = { onOpenCommandCenter("findReplace") }
        )
        SpreadsheetTallRibbonButton(
            icon = "☑",
            label = "☑ チェック切替/挿入",
            highlight = activeCell != null && InlineCheckboxHelper.containsCheckbox(activeCell.displayText),
            tag = "ms_btn_checkbox_toggle",
            onClick = onInsertCheckboxClick
        )
    }
}
