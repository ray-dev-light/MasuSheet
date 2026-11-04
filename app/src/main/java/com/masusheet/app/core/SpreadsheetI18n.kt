package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] Bilingual Application Localization Engine (Japanese ⇄ English)
 * Provides [SpreadsheetI18n] with exact dictionary lookup and pattern-based translation for dynamic status messages, dialogs, ribbon labels, and sheet notifications.
 *
 * ---
 * [JA / 日本語解説]
 * リボン・ダイアログ・右クリックメニュー・動的なステータスメッセージを日本語 (`ja`) と英語 (`en`) の間でリアルタイム翻訳する [SpreadsheetI18n] ローカライズエンジンです。
 * ============================================================================
 */

// ============================================================================
// 5. FULL APPLICATION LOCALIZATION ENGINE (JA ⇄ EN FOR 100% OF UI ELEMENTS)
// ============================================================================

/**
 * [EN] Bilingual (`ja` ⇄ `en`) translation dictionary and dynamic pattern translator for all UI labels and notices.
 * [JA] UIラベル・リボン項目・動的なステータスメッセージを日英双方向に翻訳するローカライズエンジンです。
 */
object SpreadsheetI18n {
    private val EXACT_MAP: Map<String, String> = mapOf(
        // Top Bar & Common Buttons
        "📂 開く" to "📂 Open",
        "💾 ファイル保存" to "💾 Save File",
        "📄 PDF出力" to "📄 Export PDF",
        "↶ 元に戻す" to "↶ Undo",
        "↷ やり直し" to "↷ Redo",
        "✨ AI アシスタント" to "✨ AI Assistant",
        "✨ AI アシスタント:ON" to "✨ AI Assistant:ON",
        "🤖 マクロ・VBA" to "🤖 Macro & VBA",
        "🎨 フル色パレット" to "🎨 Color Palette",
        "fx 関数(523)" to "fx Functions(523)",
        "🧭 全292コマンド" to "🧭 All 292 Cmds",
        "⬚ 範囲選択" to "⬚ Range Select",
        "⬚ 範囲選択:ON" to "⬚ Range Select:ON",
        "☑ チェック表" to "☑ Checklist",
        "🌙 ダーク" to "🌙 Dark",
        "☀️ ライト" to "☀️ Light",
        "▲ リボン隠す" to "▲ Hide Ribbon",
        "▼ リボン表示" to "▼ Show Ribbon",
        "✕ 閉じる" to "✕ Close",
        "fx 関数" to "fx Function",
        "fx 候補:" to "fx Suggest:",
        "＋ シート追加" to "＋ Add Sheet",
        "🎨 タブ色" to "🎨 Tab Color",
        "📑 シート管理" to "📑 Sheets",
        "✨ M3 UI" to "✨ M3 UI",
        "🧱 クラシックUI" to "🧱 Classic UI",

        // File Tab
        "📄 新規空白ブック" to "📄 New Blank Workbook",
        "📂 ファイルを開く (.xlsx/.csv/.ods)" to "📂 Open File (.xlsx/.csv/.ods)",
        "💾 名前を付けて保存 (拡張子選択・既存名反映)" to "💾 Save As (Choose Format & Folder)",
        "📄 PDFとして出力 (.pdf)" to "📄 Export as PDF (.pdf)",
        "📊 XLSXエクスポート" to "📊 Export XLSX",
        "📋 CSVエクスポート" to "📋 Export CSV",
        "📋 TSVエクスポート" to "📋 Export TSV",
        "📋 ODSエクスポート" to "📋 Export ODS",
        "📋 修理・点検チェック表を開く" to "📋 Open Repair Checklist",
        "📈 売上サンプル (Sales)" to "📈 Sales Sample",
        "💰 予算サンプル (Budget)" to "💰 Budget Sample",
        "🎓 成績サンプル (Grades)" to "🎓 Grades Sample",
        "🔒 ブックの保護" to "🔒 Protect Workbook",
        "ℹ️ ブックのプロパティ" to "ℹ️ Workbook Properties",
        "➕ 新しいシートを追加" to "➕ Add New Sheet",

        // Home Tab
        "✂️ 切取" to "✂️ Cut",
        "📋 コピー" to "📋 Copy",
        "📌 貼付" to "📌 Paste",
        "📋 値のみ貼付" to "📋 Paste Values",
        "📋 数式貼付" to "📋 Paste Formulas",
        "📋 書式貼付" to "📋 Paste Formats",
        "📋 行列入替貼付" to "📋 Transpose",
        "🖌️ 書式のコピー/貼り付け" to "🖌️ Format Painter",
        "B 太字" to "B Bold",
        "I 斜体" to "I Italic",
        "U 下線" to "U Underline",
        "S 取消線" to "S Strikethrough",
        "A+ 文字大" to "A+ Font Larger",
        "A- 文字小" to "A- Font Smaller",
        "🎨 塗りつぶし色 (全60色+RGB)" to "🎨 Fill Color (60 Theme + RGB)",
        "🖍️ 文字色 (全60色+RGB)" to "🖍️ Font Color (60 Theme + RGB)",
        "▦ 罫線色 (全60色+RGB)" to "▦ Border Color (60 Theme + RGB)",
        "🎨 塗潰:なし" to "🎨 No Fill",
        "⬅ 左" to "⬅ Left",
        "⬌ 中央" to "⬌ Center",
        "➡ 右" to "➡ Right",
        "⬆️ 上詰" to "⬆️ Top",
        "↕️ 上下中央" to "↕️ Middle",
        "⬇️ 下詰" to "⬇️ Bottom",
        "↩️ 折返し表示" to "↩️ Wrap Text",
        "⊞ 結合して中央" to "⊞ Merge & Center",
        "⊟ 結合解除" to "⊟ Unmerge Cells",
        "▦ 格子罫線" to "▦ All Borders",
        "□ 外枠太線" to "□ Thick Box Border",
        "¥ 通貨" to "¥ Currency",
        "% 百分率" to "% Percent",
        ", 桁区切り" to ", Comma Style",
        ".00 小数+" to ".00 Increase Decimal",
        ".0 小数-" to ".0 Decrease Decimal",
        "🌈 条件付き書式(データバー)" to "🌈 Cond. Format (Data Bars)",
        "🌈 条件付き書式(カラースケール)" to "🌈 Cond. Format (Color Scale)",
        "☑ チェック切替/挿入" to "☑ Toggle/Insert Checkbox",
        "➕ 行挿入" to "➕ Insert Rows",
        "➕ 列挿入" to "➕ Insert Columns",
        "➖ 行削除" to "➖ Delete Rows",
        "➖ 列削除" to "➖ Delete Columns",
        "↔ 列幅自動" to "↔ AutoFit Column Width",
        "↕ 行高自動" to "↕ AutoFit Row Height",
        "🔍 検索と置換" to "🔍 Find & Select",
        "🧹 消去" to "🧹 Clear",

        // Insert Tab
        "📸 スクショ・クリップボード画像を貼付" to "📸 Paste Screenshot / Clipboard Image",
        "🖼️ 画像ファイル挿入" to "🖼️ Insert Picture",
        "▭ 四角形" to "▭ Rectangle",
        "▢ 角丸四角形" to "▢ Rounded Rectangle",
        "◯ 円・楕円" to "◯ Oval / Circle",
        "△ 三角形" to "△ Triangle",
        "➡️ 矢印" to "➡️ Arrow",
        "╱ 直線" to "╱ Line",
        "📝 テキストボックス" to "📝 Text Box",
        "🎨 図形の書式タブへ" to "🎨 Shape Format Tab",
        "⬚ 表の範囲選択モード" to "⬚ Table Range Select Mode",
        "⬚ 表の範囲選択モード:ON" to "⬚ Table Range Select Mode:ON",
        "📊 縦棒グラフ (表から作成)" to "📊 Column Chart (From Table)",
        "📈 折れ線グラフ (表から作成)" to "📈 Line Chart (From Table)",
        "🥧 円グラフ (表から作成)" to "🥧 Pie Chart (From Table)",
        "📊 横棒グラフ (表から作成)" to "📊 Bar Chart (From Table)",
        "📉 面グラフ" to "📉 Area Chart",
        "🔵 散布図" to "🔵 Scatter Chart",
        "📊 ヒストグラム" to "📊 Histogram",
        "🌊 ウォーターフォール" to "🌊 Waterfall Chart",
        "🕸️ レーダー" to "🕸️ Radar Chart",
        "📈 スパークライン(折れ線)" to "📈 Sparkline (Line)",
        "📊 スパークライン(縦棒)" to "📊 Sparkline (Column)",
        "📊 ピボットテーブル作成" to "📊 Insert PivotTable",
        "📊 テーブル作成" to "📊 Insert Table",
        "🔓 テーブル解除" to "🔓 Convert to Range",
        "🔗 ハイパーリンク" to "🔗 Hyperlink",
        "💬 コメント挿入" to "💬 New Comment",
        "☑ チェックボックス切替/挿入" to "☑ Toggle/Insert Checkbox",
        "📆 年月日テンプレート" to "📆 Date Template",
        "📧 メール (@) テンプレート" to "📧 Email (@) Template",
        "Ω 記号(✓)" to "Ω Symbol (✓)",
        "Ω 記号(★)" to "Ω Symbol (★)",

        // Draw Tab
        "🖊️ 黒ペン描画" to "🖊️ Black Pen",
        "🖍️ 赤ペン・矢印" to "🖍️ Red Arrow Pen",
        "🟨 蛍光ペン枠" to "🟨 Highlighter Box",
        "◯ 赤丸囲み" to "◯ Red Circle Callout",
        "🎨 描画色パレット (RGB/テーマ色)" to "🎨 Ink Color Palette (RGB/Theme)",
        "━━ 線の太さ:細 (1pt)" to "━━ Stroke: Thin (1pt)",
        "━━ 線の太さ:中 (2.5pt)" to "━━ Stroke: Medium (2.5pt)",
        "━━ 線の太さ:太 (4.5pt)" to "━━ Stroke: Thick (4.5pt)",
        "🧹 選択した描画を消去" to "🧹 Delete Selected Drawing",

        // Object Format Tab
        "🎨 中の色パレット (全60色+RGB)" to "🎨 Shape Fill (60 Theme + RGB)",
        "🖊️ 枠線の色パレット (全60色+RGB)" to "🖊️ Shape Outline (60 Theme + RGB)",
        "🚫 中の色を抜く(透明)" to "🚫 No Fill (Transparent)",
        "🟢 緑" to "🟢 Green",
        "🔵 青" to "🔵 Blue",
        "🔴 赤" to "🔴 Red",
        "🟠 橙" to "🟠 Orange",
        "🟡 黄" to "🟡 Yellow",
        "🟣 紫" to "🟣 Purple",
        "⚪ 白" to "⚪ White",
        "⚫ 黒" to "⚫ Black",
        "━ 細線(1pt)" to "━ Thin (1pt)",
        "━ 標準(2pt)" to "━ Medium (2pt)",
        "━━ 太線(4pt)" to "━━ Thick (4pt)",
        "━━━ 極太(6.5pt)" to "━━━ Extra Thick (6.5pt)",
        "🚫 枠なし(0pt)" to "🚫 No Outline (0pt)",
        "🖊️ 枠色:黒" to "🖊️ Outline: Black",
        "🖊️ 枠色:赤" to "🖊️ Outline: Red",
        "🖊️ 枠色:青" to "🖊️ Outline: Blue",
        "🖊️ 枠色:緑" to "🖊️ Outline: Green",
        "✏️ 文字を直接編集" to "✏️ Edit Text In-Place",
        "✏️ キャンバス上で文字編集" to "✏️ Edit Text on Canvas",
        "🗑️ 削除" to "🗑️ Delete",

        // Page Layout Tab
        "🎨 テーマ配色変更 (Office / 緑 / 青 / 暖色)" to "🎨 Themes & Colors (Office / Green / Blue / Warm)",
        "📄 縦向き (Portrait)" to "📄 Portrait",
        "📄 横向き (Landscape)" to "📄 Landscape",
        "📐 用紙: A4" to "📐 Size: A4",
        "📐 用紙: Letter" to "📐 Size: Letter",
        "📐 用紙: A3" to "📐 Size: A3",
        "📏 余白: 標準" to "📏 Margins: Normal",
        "📏 余白: 狭い" to "📏 Margins: Narrow",
        "📏 余白: 広い" to "📏 Margins: Wide",
        "🔲 印刷範囲を設定" to "🔲 Set Print Area",
        "🚫 印刷範囲をクリア" to "🚫 Clear Print Area",
        "➕ 改ページ挿入" to "➕ Insert Page Break",
        "📑 印刷タイトル設定" to "📑 Print Titles",
        "1️⃣ 1ページに収める" to "1️⃣ Fit to 1 Page",
        "▦ 枠線の表示/印刷" to "▦ Gridlines View/Print",

        // Formulas Tab
        "fx 関数の挿入 (全523関数・14カテゴリ)" to "fx Insert Function (All 523 Functions)",
        "Σ オートSUM (合計)" to "Σ AutoSum (SUM)",
        "x̄ AVERAGE (平均)" to "x̄ AVERAGE",
        "# COUNT (数値個数)" to "# COUNT Numbers",
        "⬆️ MAX (最大)" to "⬆️ MAX",
        "⬇️ MIN (最小)" to "⬇️ MIN",
        "❓ IF関数" to "❓ IF Function",
        "💰 PMT(ローン計算)" to "💰 PMT (Loan Payment)",
        "🏷️ 名前の管理・定義" to "🏷️ Name Manager & Define Name",
        "📐 数式の表示切替" to "📐 Show Formulas",
        "🔍 参照元のトレース" to "🔍 Trace Precedents",
        "🔍 参照先のトレース" to "🔍 Trace Dependents",
        "🚫 トレース矢印の削除" to "🚫 Remove Arrows",
        "⚠️ エラーチェック" to "⚠️ Error Checking",
        "🔎 数式の検証" to "🔎 Evaluate Formula",
        "🔄 今すぐ再計算 (F9)" to "🔄 Calculate Now (F9)",
        "📄 シート再計算 (Shift+F9)" to "📄 Calculate Sheet (Shift+F9)",

        // Data Tab
        "🔼 昇順並べ替え (A→Z)" to "🔼 Sort A to Z",
        "🔽 降順並べ替え (Z→A)" to "🔽 Sort Z to A",
        "🗂️ 複数条件並べ替え" to "🗂️ Custom Sort",
        "🔽 オートフィルター切替" to "🔽 Filter (AutoFilter)",
        "🚫 フィルタークリア" to "🚫 Clear Filter",
        "🔄 フィルター再適用" to "🔄 Reapply Filter",
        "✂️ 区切り位置 (分割)" to "✂️ Text to Columns",
        "⚡ フラッシュフィル" to "⚡ Flash Fill",
        "🧹 重複の削除" to "🧹 Remove Duplicates",
        "✅ データの入力規則" to "✅ Data Validation",
        "🧮 統合 (Consolidate)" to "🧮 Consolidate",
        "🎯 ゴールシーク (What-If)" to "🎯 Goal Seek (What-If)",
        "📊 シナリオ管理" to "📊 Scenario Manager",
        "➕ グループ化" to "➕ Group",
        "➖ グループ解除" to "➖ Ungroup",
        "Σ 小計 (Subtotal)" to "Σ Subtotal",

        // Review Tab
        "🔤 スペルチェック" to "🔤 Spelling",
        "📊 ブックの統計情報" to "📊 Workbook Statistics",
        "♿ アクセシビリティチェック" to "♿ Check Accessibility",
        "💬 新しいコメント/メモ" to "💬 New Comment / Note",
        "🗑️ コメント削除" to "🗑️ Delete Comment",
        "⬅️ 前のコメント" to "⬅️ Previous Comment",
        "➡️ 次のコメント" to "➡️ Next Comment",
        "👁️ コメント表示切替" to "👁️ Show/Hide Comments",
        "🔒 シートの保護" to "🔒 Protect Sheet",
        "🔐 ブックの保護" to "🔐 Protect Workbook",
        "📜 変更履歴の表示" to "📜 Show Changes",

        // View Tab
        "📋 標準ビュー" to "📋 Normal View",
        "📄 改ページプレビュー" to "📄 Page Break Preview",
        "📐 ページレイアウトビュー" to "📐 Page Layout View",
        "❄️ 先頭行を固定" to "❄️ Freeze Top Row",
        "❄️ 先頭列を固定" to "❄️ Freeze First Column",
        "❄️ 選択位置で枠固定" to "❄️ Freeze Panes",
        "🔓 枠固定を解除" to "🔓 Unfreeze Panes",
        "▦ 目盛線の表示切替" to "▦ Gridlines",
        "🔤 行列見出し表示切替" to "🔤 Headings",
        "💯 100%に戻す" to "💯 100% Zoom",
        "🌙 ダークモード切替" to "🌙 Toggle Dark Mode",
        "☀️ ライトモード切替" to "☀️ Toggle Light Mode",

        // Automate Tab
        "🔴 マクロの記録を開始" to "🔴 Record Macro",
        "⏹️ マクロ記録を停止して保存" to "⏹️ Stop & Save Macro Recording",
        "🤖 マクロ・VBAスタジオ (エディタ・1行デバッグ・自然言語変換)" to "🤖 Macro & VBA Studio (Editor, Step Debug & AI Generator)",
        "✨ AI アシスタントを開く" to "✨ Open AI Assistant",

        // Table Design / Chart Design / Pivot Tabs
        "📊 テーブルとして書式設定" to "📊 Format as Table",
        "Σ 集計行の表示切替" to "Σ Total Row",
        "🏷️ 見出し行切替" to "🏷️ Header Row",
        "🦓 縞模様(行)" to "🦓 Banded Rows",
        "🦓 縞模様(列)" to "🦓 Banded Columns",
        "1️⃣ 最初の列を強調" to "1️⃣ First Column",
        "🔚 最後の列を強調" to "🔚 Last Column",
        "🔽 フィルターボタン" to "🔽 Filter Button",
        "📊 ピボットテーブルで集計" to "📊 Summarize with PivotTable",
        "🔓 通常範囲に変換 (テーブル解除)" to "🔓 Convert to Range",
        "📊 縦棒グラフへ変更" to "📊 Change to Column Chart",
        "📈 折れ線グラフへ変更" to "📈 Change to Line Chart",
        "🥧 円グラフへ変更" to "🥧 Change to Pie Chart",
        "📊 横棒グラフへ変更" to "📊 Change to Bar Chart",
        "🔄 行と列の切り替え" to "🔄 Switch Row/Column",
        "🎯 データの選択" to "🎯 Select Data",
        "➕ グラフ要素を追加" to "➕ Add Chart Element",
        "📐 クイックレイアウト" to "📐 Quick Layout",
        "🎨 グラフの色を変更" to "🎨 Change Colors",
        "✏️ グラフ内テキスト編集" to "✏️ Edit Chart Text",
        "🗑️ グラフを削除" to "🗑️ Delete Chart",
        "🔄 ピボット更新" to "🔄 Refresh PivotTable",
        "🔄 すべて更新" to "🔄 Refresh All",
        "🎯 データソースの変更" to "🎯 Change Data Source",
        "🎛️ スライサーの挿入" to "🎛️ Insert Slicer",
        "📅 タイムラインの挿入" to "📅 Insert Timeline",
        "🧮 集計フィールド追加" to "🧮 Fields, Items, & Sets",
        "📊 ピボットグラフ作成" to "📊 PivotChart",
        "📋 フィールドリスト表示" to "📋 Field List",
        "🧹 ピボットクリア" to "🧹 Clear PivotTable",
        "Σ 小計を表示しない" to "Σ Do Not Show Subtotals",
        "Σ 小計をグループ下に表示" to "Σ Show all Subtotals at Bottom",
        "Σ 行と列の総計を表示" to "Σ On for Rows and Columns",
        "📐 コンパクト形式" to "📐 Show in Compact Form",
        "📐 アウトライン形式" to "📐 Show in Outline Form",
        "📐 表形式で表示" to "📐 Show in Tabular Form",
        "🔁 アイテムのラベルをすべて繰り返す" to "🔁 Repeat All Item Labels",
        "📏 空白行を挿入" to "📏 Insert Blank Line after Each Item",

        // Context Menu Items (Cell / Column / Row / Object / Sheet Tab)
        "✏️ セルを直接編集" to "✏️ Edit Cell Directly",
        "fx 関数の挿入ウィザード (全523関数)" to "fx Insert Function Wizard (All 523 Functions)",
        "🎨 フルカラーパレット (塗りつぶし・文字色・罫線色・RGB)" to "🎨 Full Color Picker (Fill, Font, Border, RGB)",
        "✨ AI アシスタントに集計・書式・グラフ作成を依頼" to "✨ Ask AI Assistant (Analyze, Format, Chart)",
        "☑ チェックボックス切替 / 挿入" to "☑ Toggle / Insert Checkbox",
        "✂️ 切り取り" to "✂️ Cut (T)",
        "✂️ 切り取り (他アプリへも貼付可)" to "✂️ Cut (T)",
        "📋 コピー (他アプリへも貼付可)" to "📋 Copy (C)",
        "📌 貼り付け (テキスト・スクショ画像対応)" to "📌 Paste (P)",
        "📋 形式を選択して貼り付け (値のみ)" to "📋 Paste Special: Values (V)",
        "📋 形式を選択して貼り付け: 値のみ (V)" to "📋 Paste Special: Values (V)",
        "📋 形式を選択して貼り付け: 数式のみ (F)" to "📋 Paste Special: Formulas (F)",
        "📋 形式を選択して貼り付け: 書式のみ (T)" to "📋 Paste Special: Formatting (T)",
        "📋 形式を選択して貼り付け: 行列を入れ替える (E)" to "📋 Paste Special: Transpose (E)",
        "➕ 挿入: 上に行を挿入" to "➕ Insert: Entire Row Above",
        "➕ 挿入: 左に列を挿入" to "➕ Insert: Entire Column Left",
        "➕ 挿入: セルを下方向にシフト" to "➕ Insert: Shift Cells Down",
        "➖ 削除: この行を削除" to "➖ Delete: Entire Row",
        "➖ 削除: この列を削除" to "➖ Delete: Entire Column",
        "➖ 削除: セルを上方向にシフト" to "➖ Delete: Shift Cells Up",
        "💬 コメント / メモの追加・編集" to "💬 New Comment / Note",
        "🔗 ハイパーリンクの挿入・編集" to "🔗 Link (Hyperlink)...",
        "🏷️ 名前の定義 (Name Manager)" to "🏷️ Define Name...",
        "⊞ セルを結合して中央揃え" to "⊞ Merge & Center",
        "⊟ セル結合を解除" to "⊟ Unmerge Cells",
        "↩️ 折り返して全体を表示" to "↩️ Wrap Text",
        "↔ 列幅を自動調整" to "↔ AutoFit Column Width",
        "↕ 行高さを自動調整" to "↕ AutoFit Row Height",
        "📊 選択表から縦棒グラフを作成" to "📊 Quick Chart: Column Chart",
        "📈 選択表から折れ線グラフを作成" to "📈 Quick Chart: Line Chart",
        "🤖 マクロ・VBAスタジオを開く" to "🤖 Open Macro & VBA Studio",
        "🔓 テーブル解除 (Column1消去用)" to "🔓 Convert Table to Normal Range",
        "🧹 数式と値のクリア (テーブルも解除)" to "🧹 Clear Contents (N)",
        "🧹 数式と値のクリア" to "🧹 Clear Contents (N)",
        "➕ 左に列を挿入" to "➕ Insert Column (I)",
        "➖ この列を削除" to "➖ Delete Column (D)",
        "↔ 列幅を広くする (+30pt)" to "↔ Column Width (+30pt)",
        "↔ 列幅を狭くする (-20pt)" to "↔ Column Width (-20pt)",
        "🙈 この列を非表示にする" to "🙈 Hide Column (H)",
        "👁️ 列の再表示 (標準72ptに戻す)" to "👁️ Unhide Column (U)",
        "🎨 列の塗りつぶし色パレット (全60色+RGB)" to "🎨 Column Fill Color...",
        "➕ 上に行を挿入" to "➕ Insert Row (I)",
        "➖ この行を削除" to "➖ Delete Row (D)",
        "↕ 行高さを高くする (+10pt)" to "↕ Row Height (+10pt)",
        "↕ 行高さを低くする (-6pt)" to "↕ Row Height (-6pt)",
        "🙈 この行を非表示にする" to "🙈 Hide Row (H)",
        "👁️ 行の再表示 (標準22ptに戻す)" to "👁️ Unhide Row (U)",
        "🎨 行の塗りつぶし色パレット (全60色+RGB)" to "🎨 Row Fill Color...",
        "✏️ キャンバス上で文字・タイトルを編集" to "✏️ Edit Text / Title In-Place",
        "🎨 中の色パレット (テーマ60色・24bit RGB・16進数)" to "🎨 Shape Fill Palette (60 Theme + RGB)",
        "🖊️ 枠線の色パレット (テーマ60色・24bit RGB・16進数)" to "🖊️ Outline Color Palette (60 Theme + RGB)",
        "🚫 中の色を抜く (透明にする)" to "🚫 No Fill (Transparent)",
        "🎨 中の色: 緑" to "🎨 Fill: Green",
        "🎨 中の色: 青" to "🎨 Fill: Blue",
        "🎨 中の色: 赤" to "🎨 Fill: Red",
        "🎨 中の色: 黄" to "🎨 Fill: Yellow",
        "🎨 中の色: 白" to "🎨 Fill: White",
        "━━ 周りの線を太線にする (4pt)" to "━━ Outline Weight: Thick (4pt)",
        "━━━ 周りの線を極太にする (6.5pt)" to "━━━ Outline Weight: Extra Thick (6.5pt)",
        "━ 周りの線を標準にする (2pt)" to "━ Outline Weight: Medium (2pt)",
        "━ 周りの線を細線にする (1pt)" to "━ Outline Weight: Thin (1pt)",
        "🚫 枠線なし (0pt)" to "🚫 No Outline (0pt)",
        "🖊️ 枠線の色: 黒" to "🖊️ Outline Color: Black",
        "🖊️ 枠線の色: 赤" to "🖊️ Outline Color: Red",
        "🖊️ 枠線の色: 青" to "🖊️ Outline Color: Blue",
        "🖊️ 枠線の色: 緑" to "🖊️ Outline Color: Green",
        "🗑️ この図・グラフを削除" to "🗑️ Delete Shape / Chart",

        // Sheet Tab Right-Click Menu (Exact Spreadsheet 11 Items + Move Left/Right)
        "挿入(I)..." to "Insert(I)...",
        "削除(D)" to "Delete(D)",
        "名前の変更(R)" to "Rename(R)",
        "移動またはコピー(M)..." to "Move or Copy(M)...",
        "⬅️ シートを左へ移動" to "⬅️ Move Sheet Left",
        "➡️ シートを右へ移動" to "➡️ Move Sheet Right",
        "コードの表示(V)" to "View Code(V)",
        "シートの保護(P)..." to "Protect Sheet(P)...",
        "シート見出しの色(T) >" to "Tab Color(T) >",
        "非表示(H)" to "Hide(H)",
        "再表示(U)..." to "Unhide(U)...",
        "すべてのシートを選択(S)" to "Select All Sheets(S)",
        "このシートへのリンク(L)" to "Link to this Sheet(L)",
        "⚡ オートフィル: 下へ連続データ (+5行)" to "⚡ AutoFill Series Down (+5 Rows)",
        "📊 ピボットグラフ・全32種グラフ挿入..." to "📊 Insert PivotChart / All 32 Chart Types...",
        "📊 グラフの種類を変更 (全32種)..." to "📊 Change Chart Type (All 32 Types)...",
        "📊 おすすめグラフ / 全32種" to "📊 Recommended & All 32 Charts",
        "準備完了" to "Ready",

        // Function Categories
        "すべて" to "All",
        "よく使う関数" to "Most Recently Used",
        "数学/三角" to "Math & Trig",
        "統計" to "Statistical",
        "検索/行列" to "Lookup & Reference",
        "論理" to "Logical",
        "文字列操作" to "Text",
        "日付/時刻" to "Date & Time",
        "財務" to "Financial",
        "情報" to "Information",
        "データベース" to "Database",
        "エンジニアリング" to "Engineering",
        "キューブ" to "Cube",
        "AI・動的配列・互換性" to "AI / Dynamic Array / Compat.",
        "動的配列/Lambda" to "Dynamic Array & Lambda",
        "Web/キューブ" to "Web & Cube",

        // Workbook Theme Presets (Color Picker)
        "Office (標準)" to "Office (Default)",
        "グレースケール" to "Grayscale",
        "エメラルド・グリーン" to "Emerald Green",
        "オーシャン・ブルー" to "Ocean Blue",
        "ウォーム・サンセット" to "Warm Sunset",

        // AI Edit Modes
        "編集を許可" to "Allow Edit",
        "プラン" to "Plan",
        "チャットのみ" to "Chat Only",

        // Additional Top Bar, Window & UI Switcher Buttons
        "● 未保存変更あり" to "● Unsaved",
        "✓ シート反映済" to "✓ Saved",
        "📄 新規ブック" to "📄 New Book",
        "🪟 別ウィンドウ" to "🪟 New Window",
        "🪟 別ウィンドウを起動" to "🪟 New Window",
        "🪟 別ウィンドウを起動 (2画面独立)" to "🪟 Launch Separate Window",
        "＋ 新規空白ブック" to "＋ New Blank Book",
        "🎨 現在:M3 UI (切替)" to "🎨 UI: M3 (Switch)",
        "📊 現在:クラシックUI (切替)" to "📊 UI: Classic (Switch)",
                "◫ スプリット表示" to "◫ Split View",
        "◫ スプリット:ON" to "◫ Split:ON",
        "◫ スプリット表示を閉じる" to "◫ Close Split View",
        "◫ 2つのブックを並べて表示" to "◫ Side-by-Side View",
        "◫ 2つのブックをスプリット表示" to "◫ Split View",
        "📊 選択範囲からグラフを作成..." to "📊 Insert Chart from Selection...",
        "🪟 別のウィンドウへ移動" to "🪟 Move to New Window",
        "📑 別のウィンドウで開く (タブを残す)" to "📑 Open in New Window (Keep Tab)",
        "📗 このブックを表示" to "📗 Switch to This Workbook",
        "✕ このタブを閉じる" to "✕ Close This Tab",

        // Ribbon Group Titles (Bottom Labels of Ribbon Groups)
        "ブック操作・ウィンドウ" to "Workbook & Window",
        "エクスポート" to "Export",
        "保護・シート" to "Protect & Sheets",
        "テーブル" to "Tables",
        "図・画像・図形" to "Illustrations & Shapes",
        "グラフ" to "Charts",
        "スパークライン・テキスト" to "Sparklines & Text",
        "リンク・テンプレート" to "Links & Templates",
        "描画ツール" to "Drawing Tools",
        "ペンの色・太さ・回転" to "Ink Color, Weight & Rotate",
        "図形のスタイル" to "Shape Styles",
        "クイック色・枠線の太さ" to "Quick Colors & Weight",
        "配置・回転" to "Arrange & Rotate",
        "編集・削除" to "Edit & Delete",
        "テーマ" to "Themes",
        "ページ設定" to "Page Setup",
        "シートのオプション" to "Sheet Options",
        "配置・印刷" to "Arrange & Print",
        "関数ライブラリ" to "Function Library",
        "定義された名前" to "Defined Names",
        "ワークシートの分析" to "Formula Auditing",
        "計算方法" to "Calculation",
        "並べ替えとフィルター" to "Sort & Filter",
        "データ ツール" to "Data Tools",
        "アウトライン" to "Outline",
        "文章校正" to "Proofing",
        "コメント" to "Comments",
        "保護・変更履歴" to "Protect & Changes",
        "ブックの表示" to "Workbook Views",
        "表示" to "Show",
        "ズーム" to "Zoom",
        "ウィンドウ" to "Window",
        "マクロ・自動化スクリプト" to "Macros & Scripts",
        "保存済みマクロ" to "Saved Macros",
        "テーブル スタイル" to "Table Styles",
        "テーブル スタイルのオプション" to "Table Style Options",
        "ツール" to "Tools",
        "グラフの種類" to "Chart Types",
        "データ・スタイル・回転" to "Data, Style & Rotate",
        "表示・データソース" to "Show & Data Source",
        "おすすめ集計パターン" to "Recommended Layouts",
        "フィルター・ツール" to "Filter & Tools",
        "レイアウト" to "Layout",
        "ピボットテーブル スタイルのオプション" to "PivotTable Style Options",
        "ピボットテーブル スタイル (配色)" to "PivotTable Styles",
        "クリップボード" to "Clipboard",
        "フォント" to "Font",
        "配置" to "Alignment",
        "スタイル" to "Styles",
        "セル" to "Cells",
        "編集" to "Editing",

        // Chart Design, PivotTable & Shape Format Ribbon Buttons
        "🎯 データの選択..." to "🎯 Select Data...",
        "📊 グラフ系列の色を変更" to "📊 Series Color",
        "🎨 グラフ背景の色" to "🎨 Chart Fill",
        "🔤 グラフ文字の色" to "🔤 Chart Text Color",
        "🔄 グラフを回転" to "🔄 Rotate Chart",
        "🔄 図形を回転" to "🔄 Rotate Shape",
        "🔄 描画を回転" to "🔄 Rotate Drawing",
        "🔄 オブジェクトの回転" to "🔄 Rotate Object",
        "🔄 回転" to "🔄 Rotate",
        "⟳ 右90°" to "⟳ Right 90°",
        "⟲ 左90°" to "⟲ Left 90°",
        "↺ 0°リセット" to "↺ Reset 0°",
        "📐 元の縦横比にリセット" to "📐 Reset Aspect Ratio",
        "🔤 図形内の文字色パレット" to "🔤 Shape Text Color",
        "📋 フィールド リスト(右ペイン)" to "📋 Field List (Pane)",
        "➕ ピボット作成・範囲変更" to "➕ Create / Edit Pivot",
        "📊 カテゴリ別(標準)" to "📊 By Category",
        "📑 カテゴリ×項目(2階層)" to "📑 2-Level Hierarchy",
        "🧮 構成比(%)＋平均" to "🧮 % of Total + Avg",
        "🏛️ クロス集計(行×列)" to "🏛️ Cross-Tab (Row×Col)",
        "📊 ピボットグラフの挿入 (全10カテゴリ・32種)" to "📊 Insert PivotChart (All 32 Types)",
        "🧹 ピボット初期化" to "🧹 Reset PivotTable",
        "🗑️ ピボット削除" to "🗑️ Delete PivotTable",
        "🔵 標準ブルー(月間予算)" to "🔵 Standard Blue",
        "🟢 エメラルドグリーン" to "🟢 Emerald Green",
        "🟠 ウォームオレンジ" to "🟠 Warm Orange",
        "🟣 ロイヤルパープル" to "🟣 Royal Purple",
        "⚫ ダークスレート" to "⚫ Dark Slate",
        "fx 数式バー表示切替" to "fx Formula Bar",
        "◇ ひし形" to "◇ Diamond",
        "⭐ 星形" to "⭐ Star",
        "⬡ 図形一覧" to "⬡ Shapes",

        // Dropdown Menus & Object Style Bar
        "🔄 オブジェクトの回転 (ドラッグでも360°回転可)" to "🔄 Rotate Object (or drag handle 360°)",
        "⟳ 右へ 90° 回転 (+90°)" to "⟳ Rotate Right 90° (+90°)",
        "⟲ 左へ 90° 回転 (-90°)" to "⟲ Rotate Left 90° (-90°)",
        "↗ 右へ 15° 回転 (+15°)" to "↗ Rotate Right 15° (+15°)",
        "↖ 左へ 15° 回転 (-15°)" to "↖ Rotate Left 15° (-15°)",
        "↕ 上下反転 / 180° 回転" to "↕ Flip Vertical / 180°",
        "↺ 回転を 0° にリセット" to "↺ Reset Rotation to 0°",
        "🖼️ 画像の挿入" to "🖼️ Insert Pictures",
        "📱 このデバイスから画像を挿入..." to "📱 Insert from This Device...",
        "📋 クリップボード・最新スクショから画像を貼付" to "📋 Paste from Clipboard / Latest Screenshot",
        "⬡ 図形ギャラリー (挿入後 ⟳ ハンドルで回転可)" to "⬡ Shapes Gallery (Rotatable 360°)",
        "⭐ 星とリボン・吹き出し" to "⭐ Stars, Banners & Callouts",
        "📊 2-D 縦棒 / 横棒グラフ" to "📊 2-D Column / Bar Charts",
        "📊 その他の縦棒グラフ (全32種)..." to "📊 More Column Charts (All 32 Types)...",
        "📈 折れ線 / 面グラフ" to "📈 Line / Area Charts",
        "🥧 円 / ドーナツ / 階層グラフ" to "🥧 Pie / Doughnut / Hierarchy Charts",
        "📊 横棒・統計・高度グラフ" to "📊 Bar, Statistical & Advanced Charts",
        "📊 全32種類のグラフ一覧を開く..." to "📊 Open All 32 Chart Types...",
        "Σ オートSUM 関数" to "Σ AutoSum Functions",
        "fx その他の関数 (全523関数)..." to "fx More Functions (All 523)...",
        "📋 貼り付けのオプション" to "📋 Paste Options",
        "📋 貼り付け (P) — セルまたはクリップボード画像を貼付" to "📋 Paste (P) — Cells or Clipboard Image",
        "123 値のみ貼り付け (V)" to "123 Paste Values Only (V)",
        "123% 値と数値の書式 (A)" to "123% Values & Number Formatting (A)",
        "123🎨 値と元の書式 (E)" to "123🎨 Values & Source Formatting (E)",
        "fx 数式の貼り付け (F)" to "fx Paste Formulas (F)",
        "⇄ 行列を入れ替える (T)" to "⇄ Transpose Rows/Columns (T)",
        "🎨 書式のみ貼り付け (R)" to "🎨 Paste Formatting Only (R)",
        "📸 クリップボード画像・最新スクショをシートへ添付" to "📸 Paste Clipboard / Screenshot Image",
        "🖼️ 画像ファイルを選択して挿入..." to "🖼️ Choose Image File...",
        "🔤 フォントの選択" to "🔤 Select Font",
        "⊞ 格子 (すべての罫線)" to "⊞ All Borders",
        "🎨 線の色を選択..." to "🎨 Line Color...",
        "⊞ セルの結合オプション" to "⊞ Merge Cells Options",
        "通貨" to "Currency",
        "パーセント" to "Percent",
        "標準" to "General",
        "🔢 数値の表示形式" to "🔢 Number Format",
        "数値 (1,234)" to "Number (1,234)",
        "¥ 日本円 (¥1,234)" to "¥ Japanese Yen (¥1,234)",
        "$ 米ドル ($1,234.00)" to "$ US Dollar ($1,234.00)",
        "€ ユーロ (€1,234.00)" to "€ Euro (€1,234.00)",
        "会計 (¥ #,##0)" to "Accounting (¥ #,##0)",
        "短い日付 (2026/10/31)" to "Short Date (2026/10/31)",
        "長い日付 (2026年10月31日)" to "Long Date (Oct 31, 2026)",
        "パーセンテージ (0.0%)" to "Percentage (0.0%)",
        "指数 (1.23E+03)" to "Scientific (1.23E+03)",
        "文字列 (@)" to "Text (@)",
        "緑 - 黄 - 赤 カラースケール" to "Green - Yellow - Red Color Scale",
        "赤 - 黄 - 緑 カラースケール" to "Red - Yellow - Green Color Scale",
        "青 - 白 - 赤 カラースケール" to "Blue - White - Red Color Scale",
        "⬆️➡️⬇️ 3方向の矢印" to "⬆️➡️⬇️ 3 Arrows",
        "🟢🟡🔴 3色の信号機" to "🟢🟡🔴 3 Traffic Lights",
        "✅⚠️❌ 3つの記号" to "✅⚠️❌ 3 Symbols",
        "📊 テーブル スタイル" to "📊 Table Styles",
        "テーブル スタイル (淡色 1 - 緑)" to "Table Style Light 1 (Green)",
        "テーブル スタイル (淡色 2 - 青)" to "Table Style Light 2 (Blue)",
        "テーブル スタイル (淡色 9 - グレー)" to "Table Style Light 9 (Gray)",
        "テーブル スタイル (中間 2 - 標準緑)" to "Table Style Medium 2 (Green)",
                "テーブル スタイル (中間 4 - 赤)" to "Table Style Medium 4 (Red)",
        "テーブル スタイル (中間 6 - 青)" to "Table Style Medium 6 (Blue)",
        "テーブル スタイル (濃色 1 - 黒緑)" to "Table Style Dark 1 (Dark Green)",
        "テーブル スタイル (濃色 2 - 濃青)" to "Table Style Dark 2 (Dark Blue)",
        "🎨 セルのスタイル・塗りつぶし" to "🎨 Cell Styles & Fill",
        "⬜ 標準 (Normal - 色なし)" to "⬜ Normal (No Fill)",
        "🟢 良い (Good - 緑ハイライト)" to "🟢 Good (Green Highlight)",
        "🔴 悪い (Bad - 赤ハイライト)" to "🔴 Bad (Red Highlight)",
        "🟡 中立 (Neutral - 黄ハイライト)" to "🟡 Neutral (Yellow Highlight)",
        "🎨 フル色パレット (60色+RGB) を開く..." to "🎨 Open Full Color Picker (60 Theme + RGB)...",
        "➕ 挿入メニュー" to "➕ Insert Menu",
        "➕ セルを挿入 (下方向にシフト)" to "➕ Insert Cells (Shift Down)",
        "➖ 削除メニュー" to "➖ Delete Menu",
        "➖ セルを削除 (上方向にシフト)" to "➖ Delete Cells (Shift Up)",
        "📐 セルのサイズ・書式" to "📐 Cell Size & Format",
        "↕ 行の高さを広げる (+6pt)" to "↕ Increase Row Height (+6pt)",
        "↔ 列の幅を広げる (+16pt)" to "↔ Increase Column Width (+16pt)",
        "⇅ 並べ替えとフィルター" to "⇅ Sort & Filter",
        "🔼 昇順 (A → Z)" to "🔼 Sort A to Z",
        "🔽 降順 (Z → A)" to "🔽 Sort Z to A",
        "🗂️ ユーザー設定の並べ替え..." to "🗂️ Custom Sort...",
        "🚫 フィルターのクリア" to "🚫 Clear Filter",
        "図形" to "Shape",
        "テキスト枠" to "Text Box",
        "画像" to "Picture",
        "枠線の太さ:" to "Outline Weight:",
        "細線 1pt" to "Thin 1pt",
        "標準 2pt" to "Medium 2pt",
        "太線 4pt" to "Thick 4pt",
        "極太 6.5pt" to "Heavy 6.5pt",
        "枠なし" to "No Outline",
        "枠線の色:" to "Outline Color:",
        "枠:黒" to "Black",
        "枠:赤" to "Red",
        "枠:青" to "Blue",
        "枠:緑" to "Green",
        "枠:橙" to "Orange",
        "枠:白" to "White",

        // Chart / Sheet Context Menu Items (MasuSheetSheetContainer.kt)
        "⚙ 詳細設定..." to "⚙ Custom Settings...",
        "自動" to "Auto",
        "1ごと(全表示)" to "Every 1 (All)",
        "📏 選択中: X軸 (横軸全体) — 下のチップで表示間隔をワンタップ調整" to "📏 Selected: X-Axis — Tap chips below to adjust interval",
        "📐 選択中: Y軸 (縦軸全体) — 下のチップで目盛間隔をワンタップ調整" to "📐 Selected: Y-Axis — Tap chips below to adjust step",
        "📏 X軸の表示間隔 (ラベル間隔 / 数値間隔):" to "📏 X-Axis Display Interval:",
        "📐 Y軸の表示間隔 (分割数 / 目盛単位):" to "📐 Y-Axis Interval (Divisions / Step Unit):",
        "📏 X軸 (横軸・項目) の列をワンタップ選択:" to "📏 Select X-Axis (Horizontal) Column:",
        "📈 Y軸 (縦軸・数値系列) の列を選択 (複数可):" to "📈 Select Y-Axis (Vertical) Columns:",
        "📐 系列ごとのX軸・Y軸とデータ ソースの選択..." to "📐 Select Per-Series X/Y & Data Source...",
        "➕ 新しい系列を追加 (系列ごとにX軸・Y軸を選択)..." to "➕ Add New Series (Select Custom X/Y)...",
        "📏 X軸の表示間隔・最小値・最大値の設定..." to "📏 Format X-Axis (Min / Max / Step)...",
        "📐 Y軸の表示間隔・最小値・最大値の設定..." to "📐 Format Y-Axis (Min / Max / Step)...",
        "📏 現在のセル選択範囲を「X軸 (横軸)」に設定" to "📏 Set Selected Cells as X-Axis",
        "📈 現在のセル選択範囲を「Y軸 (縦軸)」に設定" to "📈 Set Selected Cells as Y-Axis",
        "🖱️ シート上をドラッグして「X軸」の範囲を選択..." to "🖱️ Drag on Sheet to Select X-Axis Range...",
        "🖱️ シート上をドラッグして「Y軸」の範囲を選択..." to "🖱️ Drag on Sheet to Select Y-Axis Range...",
        "📍 現在のセル選択範囲をこのグラフに反映 (再グラフ化)" to "📍 Bind Chart to Current Selection",
        "🖱️ シート上をドラッグしてデータ範囲全体を選択..." to "🖱️ Drag on Sheet to Select Full Data Range...",
        "⇄ X軸とY軸 (行/列) の切り替え" to "⇄ Switch Row / Column (X/Y)",
        "🔄 最新データでグラフを再計算" to "🔄 Refresh Chart with Latest Data",
        "📐 画像の縦横比・サイズを元に戻す" to "📐 Reset Picture Aspect Ratio & Size",
        "⟳ 右へ90度回転 (+90°)" to "⟳ Rotate Right 90° (+90°)",
        "⟲ 左へ90度回転 (-90°)" to "⟲ Rotate Left 90° (-90°)",
        "↻ 右へ15度回転 (+15°)" to "↻ Rotate Right 15° (+15°)",
        "↺ 左へ15度回転 (-15°)" to "↺ Rotate Left 15° (-15°)",
        "↕ 上下反転 (180°)" to "↕ Flip Vertical (180°)",
        "🧭 回転角度をリセット (0°)" to "🧭 Reset Rotation (0°)",
        "📊 グラフ系列(棒・線・円)の色パレット..." to "📊 Chart Series Color Palette...",
        "🎨 中の色(背景)パレット (テーマ60色・24bit RGB・16進数)" to "🎨 Fill Color Palette (60 Theme + 24-bit RGB)",
        "🔤 図形・グラフ内の文字色パレット..." to "🔤 Text Color Palette (All / Title / X / Y / Legend)...",
        "🏷️ グラフタイトルの文字色を変更..." to "🏷️ Change Chart Title Text Color...",
        "📏 X軸 (横軸) の文字色を変更..." to "📏 Change X-Axis (Horizontal) Text Color...",
        "📐 Y軸 (縦軸) の文字色を変更..." to "📐 Change Y-Axis (Vertical) Text Color...",
        "📋 凡例・右上表示の文字色を変更..." to "📋 Change Legend & Range Text Color...",
        "🔤 グラフ要素ごとの文字色を選択" to "🔤 Select Chart Element Text Color",
        "🎯 グラフ内の文字すべて一括..." to "🎯 All Chart Text at Once...",
        "🏷️ グラフタイトルの文字色..." to "🏷️ Chart Title Text Color...",
        "📏 X軸 (横軸) の文字色..." to "📏 X-Axis (Horizontal) Text Color...",
        "📐 Y軸 (縦軸) の文字色..." to "📐 Y-Axis (Vertical) Text Color...",
        "📋 凡例・右上範囲の文字色..." to "📋 Legend & Range Text Color...",
        "📋 この図を画像としてクリップボードにコピー" to "📋 Copy This Object as Image to Clipboard",
        "📏 選択した図の「高さ位置 (上端 Y座標)」をぴったり揃える" to "📏 Align Selected Objects Top (Match Y Position)",
        "📏 選択した図の「上下中央」をぴったり揃える" to "📏 Align Selected Objects Vertical Center",
        "📐 選択した図の「縦のサイズと高さ位置」を揃える" to "📐 Match Height & Top Position of Selected Objects",
        "↔ 選択した図を同じ高さで横に並べて整列" to "↔ Arrange Selected Objects Side-by-Side at Same Height",
        "👁️ 列の再表示 (隣接する非表示列も復元)" to "👁️ Unhide Column(s)",
        "📏 この列をグラフの「X軸 (横軸)」に設定" to "📏 Set This Column as Chart X-Axis",
        "📈 この列をグラフの「Y軸 (縦軸)」に設定" to "📈 Set This Column as Chart Y-Axis",
        "➕ この列をグラフの「Y軸 (複数系列)」に追加/解除" to "➕ Toggle This Column in Chart Y-Axis Series",
        "👁️ 行の再表示 (隣接する非表示行も復元)" to "👁️ Unhide Row(s)",
        "🖼️ 端末内の画像ファイルを選択して挿入..." to "🖼️ Insert Picture from Device...",
        "📏 選択範囲をグラフの「X軸 (横軸)」に設定" to "📏 Set Selection as Chart X-Axis",
        "📈 選択範囲をグラフの「Y軸 (縦軸)」に設定" to "📈 Set Selection as Chart Y-Axis",
        "➕ 選択範囲をグラフの「Y軸 (複数系列)」に追加" to "➕ Add Selection to Chart Y-Axis Series",
        "📐 グラフのX軸・Y軸とデータ ソースの選択..." to "📐 Select Chart X/Y & Data Source...",
        "📊 選択表からピボットグラフ・全32種を作成..." to "📊 Create Chart from Table (All 32 Types)...",
        "📊 選択表からグラフを作成 (全32種)..." to "📊 Create Chart from Table (All 32 Types)...",
        "✨ セル内スパークライン (折れ線) を挿入" to "✨ Insert In-Cell Sparkline (Line)",
        "✨ セル内スパークライン (縦棒) を挿入" to "✨ Insert In-Cell Sparkline (Column)",
        "数値" to "Number",
        "総計" to "Grand Total",
        "小計" to "Subtotal",
        "全体集計" to "Grand Summary",
        "全体合計" to "Grand Total",
        "(空白)" to "(Blanks)",
        "(空白セル)" to "(Blanks)",
        "ページ 1" to "Page 1",
        "準備完了" to "Ready"
    )

    private val COL_CHIP_JA_REGEX = Regex("""^([A-Z]{1,3})列(\s*\(.*\))?$""")
    private val COL_CHIP_EN_REGEX = Regex("""^Col\s+([A-Z]{1,3})(\s*\(.*\))?$""")
    private val SERIES_JA_REGEX = Regex("""^系列\s*(\d+)$""")
    private val SERIES_EN_REGEX = Regex("""^Series\s*(\d+)$""")

    fun t(text: String, isEn: Boolean): String {
        if (!isEn) return text
        EXACT_MAP[text]?.let { return it }
        // If menu item already contains English in parentheses like "📊 集合縦棒 (Clustered Column)", keep or extract cleanly
        COL_CHIP_JA_REGEX.matchEntire(text.trim())?.let { m ->
            val col = m.groupValues[1]
            val rest = m.groupValues[2]
            return "Col $col$rest"
        }
        return when {
            text.startsWith("📊 ") && text.contains("未保存変更あり") ->
                text.replace("● 未保存変更あり", "● Unsaved").replace("✓ シート反映済", "✓ Saved")
            text.startsWith("📊 ") && text.contains("✓ シート反映済") ->
                text.replace("✓ シート反映済", "✓ Saved")
            text.startsWith("⏹️ マクロ停止(") ->
                text.replace("⏹️ マクロ停止(", "⏹️ Stop Macro(")
            text.startsWith("🔄 回転 (") ->
                text.replace("🔄 回転 (", "🔄 Rotate (")
            text.startsWith("📈 サンプル切替 (") ->
                text.replace("📈 サンプル切替 (", "📈 Switch Sample (")
            text.startsWith("🔍 ズーム (") ->
                text.replace("🔍 ズーム (", "🔍 Zoom (")
            text.startsWith("↶ マクロ実行前の状態に戻す (") ->
                text.replace("↶ マクロ実行前の状態に戻す (", "↶ Undo Macro Run (").replace("手)", " steps)")
            text.startsWith("🖱️ 選択系列「") && text.contains("」のX軸範囲") ->
                text.replace("🖱️ 選択系列「", "🖱️ Re-select X-Axis Range for '")
                    .replace("」のX軸範囲", "'")
                    .replace("をシート上で再選択...", " on Sheet...")
            text.startsWith("🖱️ 選択系列「") && text.contains("」のY軸範囲") ->
                text.replace("🖱️ 選択系列「", "🖱️ Re-select Y-Axis Range for '")
                    .replace("」のY軸範囲", "'")
                    .replace("をシート上で再選択...", " on Sheet...")
            text.startsWith("列 ") && text.endsWith(" メニュー") ->
                "Column " + text.removePrefix("列 ").removeSuffix(" メニュー") + " Menu"
            text.startsWith("行 ") && text.endsWith(" メニュー") ->
                "Row " + text.removePrefix("行 ").removeSuffix(" メニュー") + " Menu"
            text.startsWith("セル ") && text.endsWith(" メニュー") ->
                "Cell " + text.removePrefix("セル ").removeSuffix(" メニュー") + " Menu"
            text == "図形・グラフ・画像 メニュー" -> "Shape / Chart / Picture Menu"
            else -> text
        }
    }

    /**
     * [EN] Dynamically translates auto-generated chart titles (e.g., `ピボットグラフ (直線とマーカー付き散布図 - Sheet1)`
     * <-> `PivotChart (Scatter with Straight Lines - Sheet1)`) and series names (`G列` <-> `Col G`, `系列1` <-> `Series 1`)
     * according to the active UI language (`isEn`).
     *
     * ---
     * [JA] 自動生成されたグラフタイトル（例：`ピボットグラフ (...)` ⇄ `PivotChart (...)`）および系列名（`G列` ⇄ `Col G`、`系列1` ⇄ `Series 1`）を
     * 現在の表示言語 (`isEn`) に合わせて動的に相互翻訳します。
     */
    fun translateChartDisplayText(text: String, isEn: Boolean): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return text
        if (isEn) {
            COL_CHIP_JA_REGEX.matchEntire(trimmed)?.let { m ->
                return "Col ${m.groupValues[1]}${m.groupValues[2]}"
            }
            SERIES_JA_REGEX.matchEntire(trimmed)?.let { m ->
                return "Series ${m.groupValues[1]}"
            }
            if (trimmed == "データ") return "Data"
            var out = trimmed
            if (out.startsWith("ピボットグラフ (") || out.startsWith("ピボットグラフ(")) {
                out = out.replaceFirst("ピボットグラフ", "PivotChart")
            }
            for (spec in SpreadsheetChartTypeCatalog.SUBTYPES.sortedByDescending { it.labelJa.length }) {
                if (out.contains(spec.labelJa)) {
                    out = out.replace(spec.labelJa, spec.labelEn)
                }
            }
            out = out.replace(Regex("""(\d+)系列\s*\("""), "$1 series (")
            out = out.replace(Regex("""\b([A-Z]{1,3})列\b"""), "Col $1")
            return out
        } else {
            COL_CHIP_EN_REGEX.matchEntire(trimmed)?.let { m ->
                return "${m.groupValues[1]}列${m.groupValues[2]}"
            }
            SERIES_EN_REGEX.matchEntire(trimmed)?.let { m ->
                return "系列${m.groupValues[1]}"
            }
            if (trimmed.equals("Data", ignoreCase = true)) return "データ"
            var out = trimmed
            if (out.startsWith("PivotChart (") || out.startsWith("PivotChart(")) {
                out = out.replaceFirst("PivotChart", "ピボットグラフ")
            }
            for (spec in SpreadsheetChartTypeCatalog.SUBTYPES.sortedByDescending { it.labelEn.length }) {
                if (out.contains(spec.labelEn)) {
                    out = out.replace(spec.labelEn, spec.labelJa)
                }
            }
            out = out.replace(Regex("""(\d+)\s+series\s*\("""), "$1系列 (")
            out = out.replace(Regex("""\bCol\s+([A-Z]{1,3})\b"""), "$1列")
            return out
        }
    }
}
