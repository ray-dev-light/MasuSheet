package com.masusheet.app.catalog

import kotlin.math.max
import kotlin.math.min

/**
 * ============================================================================
 * [EN] Chart & PivotChart Type Catalog (10 Categories × 32 Subtypes)
 * Defines [SpreadsheetChartCategorySpec], [SpreadsheetChartSubtypeSpec], and [SpreadsheetChartTypeCatalog] covering column, line, pie, bar, area, scatter, stock, radar, combo, and waterfall charts.
 *
 * ---
 * [JA / 日本語解説]
 * 縦棒・折れ線・円・横棒・面・散布図・株価・レーダー・複合・ウォーターフォールなど全10カテゴリ・32サブタイプのグラフ仕様を定義する [SpreadsheetChartTypeCatalog] モジュールです。
 * ============================================================================
 */

// ============================================================================
// 8. FULL SPREADSHEET CHART & PIVOTCHART TYPE CATALOG (10 CATEGORIES × 32 SUBTYPES)
// ============================================================================

/**
 * [EN] Top-level chart family metadata (id, icon, bilingual name) in the 10-category chart catalog.
 * [JA] 全10カテゴリのグラフ分類（縦棒・折れ線・円・散布図等）のメタデータ定義です。
 */
data class SpreadsheetChartCategorySpec(
    val id: String,
    val icon: String,
    val labelJa: String,
    val labelEn: String
) {
    val titleJa: String get() = labelJa
    val titleEn: String get() = labelEn
    val subtypes: List<SpreadsheetChartSubtypeSpec>
        get() = SpreadsheetChartTypeCatalog.SUBTYPES.filter { it.categoryId == id }
}

/**
 * [EN] Concrete chart subtype specification among the 32 supported chart and PivotChart layouts.
 * [JA] 全32種類のグラフ／ピボットグラフ詳細サブタイプの仕様定義です。
 */
data class SpreadsheetChartSubtypeSpec(
    val subType: String,
    val categoryId: String,
    val icon: String,
    val labelJa: String,
    val labelEn: String,
    val descJa: String,
    val descEn: String
) {
    val id: String get() = subType
    val nameJa: String get() = labelJa
    val nameEn: String get() = labelEn
}

/**
 * [EN] Registry of all 10 chart categories and 32 chart subtypes with bilingual labels and lookup helpers.
 * [JA] 全10カテゴリ・32サブタイプのグラフ定義一覧および検索ヘルパーを提供するカタログです。
 */
object SpreadsheetChartTypeCatalog {
    val CATEGORIES: List<SpreadsheetChartCategorySpec> = listOf(
        SpreadsheetChartCategorySpec("all", "🌟", "すべて / おすすめ (All 32)", "All / Recommended (32)"),
        SpreadsheetChartCategorySpec("column", "📊", "縦棒 (Column)", "Column"),
        SpreadsheetChartCategorySpec("line", "📈", "折れ線 (Line)", "Line"),
        SpreadsheetChartCategorySpec("pie", "🥧", "円・ドーナツ (Pie / Doughnut)", "Pie & Doughnut"),
        SpreadsheetChartCategorySpec("bar", "📊", "横棒 (Bar)", "Bar"),
        SpreadsheetChartCategorySpec("area", "⛰️", "面 (Area)", "Area"),
        SpreadsheetChartCategorySpec("scatter", "🫧", "散布図・バブル (X Y / Bubble)", "X Y (Scatter) & Bubble"),
        SpreadsheetChartCategorySpec("radar", "🕸️", "レーダー (Radar)", "Radar"),
        SpreadsheetChartCategorySpec("hierarchy", "🌲", "ツリーマップ・サンバースト", "Treemap & Sunburst"),
        SpreadsheetChartCategorySpec("statistical", "📉", "ヒストグラム・箱ひげ (統計)", "Statistical (Histogram / Box)"),
        SpreadsheetChartCategorySpec("waterfall_combo", "🌊", "滝・じょうご・株価・複合", "Waterfall, Funnel, Stock & Combo")
    )

    val categories: List<SpreadsheetChartCategorySpec>
        get() = CATEGORIES.filter { it.id != "all" }

    val ALL_SUBTYPES: List<SpreadsheetChartSubtypeSpec>
        get() = SUBTYPES

    val allSubtypes: List<SpreadsheetChartSubtypeSpec>
        get() = SUBTYPES

    val SUBTYPES: List<SpreadsheetChartSubtypeSpec> = listOf(
        // 1. Column (縦棒)
        SpreadsheetChartSubtypeSpec(
            subType = "column",
            categoryId = "column",
            icon = "📊",
            labelJa = "集合縦棒",
            labelEn = "Clustered Column",
            descJa = "複数の項目間で値を並べて比較します（標準の縦棒グラフ）",
            descEn = "Compare values across categories side by side"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "column_stacked",
            categoryId = "column",
            icon = "📊",
            labelJa = "積み上げ縦棒",
            labelEn = "Stacked Column",
            descJa = "全体に対する各系列の合計と内訳を縦に積み上げて比較します",
            descEn = "Compare parts of a whole stacked vertically across categories"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "column_stacked100",
            categoryId = "column",
            icon = "📊",
            labelJa = "100% 積み上げ縦棒",
            labelEn = "100% Stacked Column",
            descJa = "各項目における構成比 (100%) を縦棒で比較します",
            descEn = "Compare the percentage that each value contributes to a total"
        ),

        // 2. Line (折れ線)
        SpreadsheetChartSubtypeSpec(
            subType = "line",
            categoryId = "line",
            icon = "📈",
            labelJa = "折れ線",
            labelEn = "Line",
            descJa = "時間経過や順序に沿ったデータの推移・トレンドを表示します",
            descEn = "Show trends over time or ordered categories"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "line_markers",
            categoryId = "line",
            icon = "📈",
            labelJa = "マーカー付き折れ線",
            labelEn = "Line with Markers",
            descJa = "各データ要素の位置にマーカーを表示する折れ線グラフです",
            descEn = "Show trends over time with highlighted data point markers"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "line_stacked",
            categoryId = "line",
            icon = "📈",
            labelJa = "積み上げ折れ線",
            labelEn = "Stacked Line",
            descJa = "各系列の累積値の推移を折れ線で表示します",
            descEn = "Show the trend of the contribution of each value over time"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "line_stacked100",
            categoryId = "line",
            icon = "📈",
            labelJa = "100% 積み上げ折れ線",
            labelEn = "100% Stacked Line",
            descJa = "各系列の構成比 (100%) の推移を折れ線で表示します",
            descEn = "Show the percentage trend each value contributes over time"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "line_smooth",
            categoryId = "line",
            icon = "〰️",
            labelJa = "平滑線 (スムーズ折れ線)",
            labelEn = "Smooth Line",
            descJa = "データ間を滑らかな曲線で結んだ推移グラフです",
            descEn = "Connect data points with smooth Bezier curves"
        ),

        // 3. Pie & Doughnut (円・ドーナツ)
        SpreadsheetChartSubtypeSpec(
            subType = "pie",
            categoryId = "pie",
            icon = "🥧",
            labelJa = "2-D 円",
            labelEn = "2-D Pie",
            descJa = "全体に対する各項目の割合を扇形で表示します",
            descEn = "Show proportions of a whole as pie slices"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "pie_exploded",
            categoryId = "pie",
            icon = "🥧",
            labelJa = "分割円 (切り離し円)",
            labelEn = "Exploded Pie",
            descJa = "各扇形を少しずつ切り離して構成比を強調する円グラフです",
            descEn = "Emphasize individual slices separated from the pie center"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "doughnut",
            categoryId = "pie",
            icon = "🍩",
            labelJa = "ドーナツ",
            labelEn = "Doughnut",
            descJa = "中央に合計値を表示できるリング型の構成比グラフです",
            descEn = "Show proportions of a whole in a ring with center total"
        ),

        // 4. Bar (横棒)
        SpreadsheetChartSubtypeSpec(
            subType = "bar_h",
            categoryId = "bar",
            icon = "📊",
            labelJa = "集合横棒",
            labelEn = "Clustered Bar",
            descJa = "項目名が長い場合やランキング比較に適した横棒グラフです",
            descEn = "Compare values horizontally across categories"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "bar_stacked",
            categoryId = "bar",
            icon = "📊",
            labelJa = "積み上げ横棒",
            labelEn = "Stacked Bar",
            descJa = "各系列の合計と内訳を横方向に積み上げて比較します",
            descEn = "Compare parts of a whole stacked horizontally"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "bar_stacked100",
            categoryId = "bar",
            icon = "📊",
            labelJa = "100% 積み上げ横棒",
            labelEn = "100% Stacked Bar",
            descJa = "各項目における構成比 (100%) を横棒で比較します",
            descEn = "Compare horizontal percentage contributions to a total"
        ),

        // 5. Area (面)
        SpreadsheetChartSubtypeSpec(
            subType = "area",
            categoryId = "area",
            icon = "⛰️",
            labelJa = "面 (エリア)",
            labelEn = "Area",
            descJa = "折れ線の下を塗りつぶして時間経過に伴う数量の大きさを強調します",
            descEn = "Emphasize magnitude of change over time with filled area"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "area_stacked",
            categoryId = "area",
            icon = "⛰️",
            labelJa = "積み上げ面",
            labelEn = "Stacked Area",
            descJa = "複数系列の合計量の推移と各系列の寄与度を面で表示します",
            descEn = "Show cumulative totals and series contributions over time"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "area_stacked100",
            categoryId = "area",
            icon = "⛰️",
            labelJa = "100% 積み上げ面",
            labelEn = "100% Stacked Area",
            descJa = "時間経過に伴う構成比 (100%) の変化を面で表示します",
            descEn = "Show percentage contribution trends over time"
        ),

        // 6. Scatter & Bubble (散布図・バブル)
        SpreadsheetChartSubtypeSpec(
            subType = "scatter",
            categoryId = "scatter",
            icon = "🫧",
            labelJa = "散布図 (マーカーのみ)",
            labelEn = "Scatter",
            descJa = "2つの数値データの相関関係や分布をプロット表示します",
            descEn = "Compare pairs of values and observe correlation clusters"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "scatter_lines",
            categoryId = "scatter",
            icon = "📈",
            labelJa = "直線とマーカー付き散布図",
            labelEn = "Scatter with Straight Lines",
            descJa = "散布図のデータ点を直線で結んで表示します",
            descEn = "Scatter points connected with straight lines"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "bubble",
            categoryId = "scatter",
            icon = "🫧",
            labelJa = "バブル チャート",
            labelEn = "Bubble",
            descJa = "値の大きさを円の面積（バブルサイズ）で視覚的に比較します",
            descEn = "Plot data points with bubble area proportional to value"
        ),

        // 7. Radar (レーダー)
        SpreadsheetChartSubtypeSpec(
            subType = "radar",
            categoryId = "radar",
            icon = "🕸️",
            labelJa = "レーダー",
            labelEn = "Radar",
            descJa = "中心点から放射状の軸で複数項目のバランスを比較します",
            descEn = "Compare aggregate values of multiple data series on radial axes"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "radar_markers",
            categoryId = "radar",
            icon = "🕸️",
            labelJa = "マーカー付きレーダー",
            labelEn = "Radar with Markers",
            descJa = "各頂点にマーカーを表示するレーダーチャートです",
            descEn = "Radar chart with markers at each category vertex"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "radar_filled",
            categoryId = "radar",
            icon = "🕸️",
            labelJa = "塗りつぶしレーダー",
            labelEn = "Filled Radar",
            descJa = "多角形領域を半透明色で塗りつぶしたレーダーチャートです",
            descEn = "Radar chart with filled polygon areas"
        ),

        // 8. Hierarchy (ツリーマップ・サンバースト)
        SpreadsheetChartSubtypeSpec(
            subType = "treemap",
            categoryId = "hierarchy",
            icon = "🌲",
            labelJa = "ツリーマップ",
            labelEn = "Treemap",
            descJa = "面積の比率で階層データや項目別の規模を直感的に比較します",
            descEn = "Hierarchical view of data as proportional nested rectangles"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "sunburst",
            categoryId = "hierarchy",
            icon = "☀️",
            labelJa = "サンバースト",
            labelEn = "Sunburst",
            descJa = "同心円状のマルチリングで階層データの内訳を表示します",
            descEn = "Multi-ring concentric hierarchy chart"
        ),

        // 9. Statistical (ヒストグラム・パレート・箱ひげ)
        SpreadsheetChartSubtypeSpec(
            subType = "histogram",
            categoryId = "statistical",
            icon = "📉",
            labelJa = "ヒストグラム (度数分布)",
            labelEn = "Histogram",
            descJa = "データの分布状況を連続した階級バーで表示します",
            descEn = "Show frequency distribution across contiguous bins"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "pareto",
            categoryId = "statistical",
            icon = "📉",
            labelJa = "パレート図 (降順棒＋累積比率線)",
            labelEn = "Pareto",
            descJa = "大きい順の縦棒と累積構成比 (%) の折れ線を同時表示します",
            descEn = "Sorted columns combined with a cumulative percentage line"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "box_whisker",
            categoryId = "statistical",
            icon = "📦",
            labelJa = "箱ひげ図 (Box & Whisker)",
            labelEn = "Box & Whisker",
            descJa = "最小値・第1四分位・中央値・第3四分位・最大値のばらつきを表示します",
            descEn = "Show quartiles, median, and min/max spread"
        ),

        // 10. Waterfall / Funnel / Stock / Combo (滝・じょうご・株価・複合)
        SpreadsheetChartSubtypeSpec(
            subType = "waterfall",
            categoryId = "waterfall_combo",
            icon = "🌊",
            labelJa = "ウォーターフォール (滝グラフ)",
            labelEn = "Waterfall",
            descJa = "増減（プラス・マイナス）の累積推移と最終合計を視覚化します",
            descEn = "Show running total as values are added or subtracted"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "funnel",
            categoryId = "waterfall_combo",
            icon = "🔻",
            labelJa = "じょうご (ファネル)",
            labelEn = "Funnel",
            descJa = "プロセス各段階の減少・コンバージョン推移を中央揃え横棒で表示します",
            descEn = "Show values across progressive stages in a funnel"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "stock",
            categoryId = "waterfall_combo",
            icon = "💹",
            labelJa = "株価 (高値-安値-終値 / ローソク)",
            labelEn = "Stock (HLC / Candlestick)",
            descJa = "系列間の最大・最小レンジと始値・終値を株価チャート形式で表示します",
            descEn = "Show high-low range and open-close bars"
        ),
        SpreadsheetChartSubtypeSpec(
            subType = "combo",
            categoryId = "waterfall_combo",
            icon = "📊",
            labelJa = "複合グラフ (集合縦棒 + 折れ線)",
            labelEn = "Combo (Clustered Column + Line)",
            descJa = "第1系列を縦棒、第2系列以降を折れ線で重ねて比較します",
            descEn = "Combine clustered columns and line series in one chart"
        )
    )

    fun normalizeSubType(raw: String): String {
        val clean = raw.trim().lowercase()
        return when (clean) {
            "column", "columnclustered", "clustered_column" -> "column"
            "column_stacked", "columnstacked", "stacked_column" -> "column_stacked"
            "column_stacked100", "columnstacked100", "stacked100_column" -> "column_stacked100"
            "line" -> "line"
            "line_markers", "linemarkers" -> "line_markers"
            "line_stacked", "linestacked" -> "line_stacked"
            "line_stacked100", "linestacked100" -> "line_stacked100"
            "line_smooth", "linesmooth", "smooth_line" -> "line_smooth"
            "pie" -> "pie"
            "pie_exploded", "exploded_pie", "pieexploded" -> "pie_exploded"
            "doughnut", "donut" -> "doughnut"
            "bar", "bar_h", "horizontal", "barclustered", "clustered_bar" -> "bar_h"
            "bar_stacked", "barstacked", "stacked_bar" -> "bar_stacked"
            "bar_stacked100", "barstacked100", "stacked100_bar" -> "bar_stacked100"
            "area" -> "area"
            "area_stacked", "areastacked", "stacked_area" -> "area_stacked"
            "area_stacked100", "areastacked100", "stacked100_area" -> "area_stacked100"
            "scatter", "xy" -> "scatter"
            "scatter_lines", "scatterlines" -> "scatter_lines"
            "bubble" -> "bubble"
            "radar" -> "radar"
            "radar_markers", "radarmarkers" -> "radar_markers"
            "radar_filled", "radarfilled" -> "radar_filled"
            "treemap" -> "treemap"
            "sunburst" -> "sunburst"
            "histogram" -> "histogram"
            "pareto" -> "pareto"
            "box_whisker", "boxwhisker", "box" -> "box_whisker"
            "waterfall" -> "waterfall"
            "funnel" -> "funnel"
            "stock" -> "stock"
            "combo" -> "combo"
            else -> "column"
        }
    }

    fun findSpec(subType: String): SpreadsheetChartSubtypeSpec {
        val norm = normalizeSubType(subType)
        return SUBTYPES.firstOrNull { it.subType == norm } ?: SUBTYPES.first()
    }
}


