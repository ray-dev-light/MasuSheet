package com.masusheet.app.model

import com.masusheet.app.core.MasuSheetConfig

/**
 * ============================================================================
 * [EN] Floating Sheet Objects (Charts, Shapes, Images), PivotTables, & Filter Models
 * Defines [NativeSheetObject], [ChartSeries], [PivotTableDefinition], [CustomCellStyleOverride], [TableColumnFilterPopupState], and table range detection models.
 *
 * ---
 * [JA / 日本語解説]
 * シート上のフローティングオブジェクト（グラフ・図形・画像: [NativeSheetObject]）、ピボットテーブル定義 ([PivotTableDefinition])、カスタム書式オーバーレイ、オートフィルタ状態モデルを定義します。
 * ============================================================================
 */

/**
 * [EN] Kind of floating object placed over the worksheet grid (`SHAPE`, `IMAGE`, `CHART`, `TEXTBOX`).
 * [JA] シート上に配置されるフローティングオブジェクトの種別（図形・画像・グラフ・テキストボックス）です。
 */
enum class SheetObjectKind {
    SHAPE,
    IMAGE,
    CHART,
    TEXT_BOX
}

/**
 * [EN] Single data series of a chart holding legend name, numeric values, color, and bound sheet X/Y range addresses.
 * [JA] グラフ内の1データ系列（系列名、数値列、表示色、X軸／Y軸の参照セル範囲）を表すモデルです。
 */
data class ChartSeries(
    val name: String,
    val values: List<Float>,
    val color: Int = 0,
    val nameCellRef: String = "",
    val xRangeText: String = "",
    val yRangeText: String = "",
    val xLabels: List<String> = emptyList()
)

/**
 * [EN] Immutable model of a floating chart, shape, textbox, or image anchored in points (`pt`) on a worksheet.
 * [JA] シート上の座標・サイズ (`pt`)、配色、回転角、グラフ系列・軸設定、画像バイト列等を保持するオブジェクトモデルです。
 */
@androidx.compose.runtime.Immutable
data class NativeSheetObject(
    val id: String,
    val sheetIndex: Int = 0,
    val kind: SheetObjectKind = SheetObjectKind.SHAPE,
    val subType: String = "rectangle",
    val xPt: Float = 80f,
    val yPt: Float = 60f,
    val widthPt: Float = 180f,
    val heightPt: Float = 110f,
    val title: String = "",
    val text: String = "",
    val fillColor: Int = 0xFF1F9D55.toInt(), // 0 = 中の色を抜く (透明 / 塗りつぶしなし)
    val strokeColor: Int = 0xFF163828.toInt(), // 0 = 枠線なし
    val strokeWidthPt: Float = 2.0f, // 枠線の太さ (1f = 細線, 2f = 標準, 4f = 太線, 6.5f = 極太)
    val textColor: Int = 0, // 0 = 自動コントラスト (全体)
    val chartTitleTextColor: Int = 0, // 0 = textColor または自動コントラスト (グラフタイトル)
    val chartXAxisTextColor: Int = 0, // 0 = textColor または自動コントラスト (X軸ラベル)
    val chartYAxisTextColor: Int = 0, // 0 = textColor または自動コントラスト (Y軸ラベル)
    val chartLegendTextColor: Int = 0, // 0 = textColor または自動コントラスト (凡例・右上範囲表示)
    val rotationDeg: Float = 0f, // 回転角度 (0°..360°)
    val imageBytes: ByteArray? = null,
    val chartLabels: List<String> = emptyList(),
    val chartValues: List<Float> = emptyList(),
    val chartSeries: List<ChartSeries> = emptyList(),
    val sourceRangeText: String = "",
    val chartXColIndex: Int = -1,
    val chartYColIndices: List<Int> = emptyList(),
    val selectedSubElement: String = "", // "", "xAxis", "yAxis", "series:0", "series:1", ...
    val xAxisLabelInterval: Int = 0, // 0 = 自動, 1 = すべて表示(1ごと), 2 = 2おき, 5 = 5おき, 10 = 10おき
    val xAxisMajorStep: Float? = null, // 数値X軸の目盛間隔 (null = 自動)
    val xAxisMin: Float? = null,
    val xAxisMax: Float? = null,
    val yAxisMajorStep: Float? = null, // Y軸の表示間隔 / 目盛間隔 (null = 自動)
    val yAxisMin: Float? = null,
    val yAxisMax: Float? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NativeSheetObject) return false
        return id == other.id &&
            sheetIndex == other.sheetIndex &&
            kind == other.kind &&
            subType == other.subType &&
            xPt == other.xPt &&
            yPt == other.yPt &&
            widthPt == other.widthPt &&
            heightPt == other.heightPt &&
            title == other.title &&
            text == other.text &&
            fillColor == other.fillColor &&
            strokeColor == other.strokeColor &&
            strokeWidthPt == other.strokeWidthPt &&
            textColor == other.textColor &&
            chartTitleTextColor == other.chartTitleTextColor &&
            chartXAxisTextColor == other.chartXAxisTextColor &&
            chartYAxisTextColor == other.chartYAxisTextColor &&
            chartLegendTextColor == other.chartLegendTextColor &&
            rotationDeg == other.rotationDeg &&
            (imageBytes contentEquals other.imageBytes) &&
            chartLabels == other.chartLabels &&
            chartValues == other.chartValues &&
            chartSeries == other.chartSeries &&
            sourceRangeText == other.sourceRangeText &&
            chartXColIndex == other.chartXColIndex &&
            chartYColIndices == other.chartYColIndices &&
            selectedSubElement == other.selectedSubElement &&
            xAxisLabelInterval == other.xAxisLabelInterval &&
            xAxisMajorStep == other.xAxisMajorStep &&
            xAxisMin == other.xAxisMin &&
            xAxisMax == other.xAxisMax &&
            yAxisMajorStep == other.yAxisMajorStep &&
            yAxisMin == other.yAxisMin &&
            yAxisMax == other.yAxisMax
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sheetIndex
        result = 31 * result + kind.hashCode()
        result = 31 * result + subType.hashCode()
        result = 31 * result + xPt.hashCode()
        result = 31 * result + yPt.hashCode()
        result = 31 * result + widthPt.hashCode()
        result = 31 * result + heightPt.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + fillColor
        result = 31 * result + strokeColor
        result = 31 * result + strokeWidthPt.hashCode()
        result = 31 * result + textColor
        result = 31 * result + chartTitleTextColor
        result = 31 * result + chartXAxisTextColor
        result = 31 * result + chartYAxisTextColor
        result = 31 * result + chartLegendTextColor
        result = 31 * result + rotationDeg.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + chartLabels.hashCode()
        result = 31 * result + chartValues.hashCode()
        result = 31 * result + chartSeries.hashCode()
        result = 31 * result + sourceRangeText.hashCode()
        result = 31 * result + chartXColIndex
        result = 31 * result + chartYColIndices.hashCode()
        result = 31 * result + selectedSubElement.hashCode()
        result = 31 * result + xAxisLabelInterval
        result = 31 * result + (xAxisMajorStep?.hashCode() ?: 0)
        result = 31 * result + (xAxisMin?.hashCode() ?: 0)
        result = 31 * result + (xAxisMax?.hashCode() ?: 0)
        result = 31 * result + (yAxisMajorStep?.hashCode() ?: 0)
        result = 31 * result + (yAxisMin?.hashCode() ?: 0)
        result = 31 * result + (yAxisMax?.hashCode() ?: 0)
        return result
    }
}

/**
 * [EN] Metadata of an existing spreadsheet file discovered in the currently selected save/open folder.
 * [JA] 保存／読込ダイアログの選択中フォルダ内で検出された既存スプレッドシートファイルの情報です。
 */
data class ExistingFolderFileItem(
    val fileName: String,
    val baseName: String,
    val extension: String,
    val fullPath: String
)

/**
 * [EN] UI state for the active table column AutoFilter & sort popup menu.
 * [JA] テーブル見出しの `[▾]` から開くオートフィルタ・並び替えポップアップの表示状態です。
 */
data class TableColumnFilterPopupState(
    val headerRow: Int,
    val col: Int,
    val tableStartRow: Int,
    val tableEndRow: Int,
    val tableStartCol: Int,
    val tableEndCol: Int,
    val columnTitle: String,
    val distinctValues: List<String>,
    val checkedValues: Set<String>,
    val hasBlanks: Boolean = false,
    val includeBlanks: Boolean = true,
    val isNumericColumn: Boolean = false,
    val currentSortOrder: String = "", // "", "asc", "desc"
    val customFilterOperator: String = "", // "", "equals", "contains", "gt", "gte", "lt", "lte", "aboveAvg", "belowAvg"
    val customFilterOperand: String = ""
)

/**
 * [EN] Kotlin-side cell style override (indent, vertical alignment, strikethrough, underline, borders) layered cleanly with the engine.
 * [JA] インデント・縦位置・取消線・下線・罫線などKotlin側で補完保持するセル書式オーバーレイです。
 */
data class CustomCellStyleOverride(
    val bgColor: Int = 0, // 0 = no override, CLEARED (0x01000000) = explicitly cleared (0), else ARGB
    val fgColor: Int = 0, // 0 = no override, CLEARED (0x01000000) = explicitly cleared (0), else ARGB
    val fontSize: Float = 0f,
    val isBold: Boolean? = null,
    val isItalic: Boolean? = null,
    val isUnderline: Boolean? = null,
    val isStrike: Boolean? = null,
    val hAlign: Int? = null, // 0 = Left, 1 = Center, 2 = Right
    val vAlign: Int? = null, // 0 = Top, 1 = Middle, 2 = Bottom
    val numberFormatKind: String = "", // "", "currency", "percent"
    val borderTop: Int? = null,
    val borderRight: Int? = null,
    val borderBottom: Int? = null,
    val borderLeft: Int? = null
) {
    companion object {
        const val CLEARED: Int = 0x01000000
    }
}

/**
 * [EN] Single cell match entry produced by Find All across the active sheet or entire workbook.
 * [JA] 「すべて検索」で一致したセルのシート・番地・表示値プレビューを保持する検索結果項目です。
 */
data class FindMatchItem(
    val sheetIndex: Int,
    val sheetName: String,
    val row: Int,
    val col: Int,
    val address: String,
    val displayText: String,
    val editText: String
)

/**
 * [EN] Aggregation function (`SUM`, `COUNT`, `AVERAGE`, `MAX`, `MIN`, `PRODUCT`, `STDDEV`, `VAR`, etc.) for PivotTable value fields.
 * [JA] ピボットテーブルの値フィールドで使用可能な全11種類の集計関数（合計・個数・平均・最大・最小・標準偏差等）です。
 */
enum class PivotAggFunction(
    val id: String,
    val labelJa: String,
    val labelEn: String
) {
    SUM("sum", "合計", "Sum"),
    COUNT("count", "個数", "Count"),
    AVERAGE("avg", "平均", "Average"),
    MAX("max", "最大", "Max"),
    MIN("min", "最小", "Min"),
    PRODUCT("product", "積", "Product"),
    PERCENT_OF_TOTAL("pctTotal", "構成比(%)", "% of Total");

    fun next(): PivotAggFunction {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

/**
 * [EN] Configuration of a single value field in a PivotTable (source column name, aggregation function, custom label).
 * [JA] ピボットテーブルの値エリアに配置された1フィールドの元列名・集計方法・表示名設定です。
 */
data class PivotValueFieldSpec(
    val fieldName: String,
    val agg: PivotAggFunction = PivotAggFunction.SUM,
    val isCurrencyLike: Boolean = isCurrencyFieldByName(fieldName)
) {
    val aggFunction: PivotAggFunction
        get() = agg

    fun displayTitle(isEn: Boolean): String =
        if (isEn) "${agg.labelEn} of $fieldName" else "${agg.labelJa} / $fieldName"

    fun displayHeader(isEn: Boolean): String = displayTitle(isEn)

    companion object {
        private val NON_CURRENCY_KEYWORDS = listOf(
            "在庫数", "数量", "個数", "件数", "人数", "台数", "枚数", "本数", "回数", "日数",
            "発注点", "安全在庫", "点数", "スコア", "順位", "ランク", "時間", "(h)", "工数",
            "割合", "率", "構成比", "%",
            "count", "qty", "quantity", "units", "rate", "ratio", "percent",
            "hours", "days", "score", "rank", "points", "items", "stock", "reorder"
        )
        private val CURRENCY_KEYWORDS = listOf(
            "金額", "単価", "価格", "売上", "予算", "実績額", "残高", "差額", "支給", "時給",
            "人件費", "税込", "税抜", "小計", "消費税", "経費", "収支", "費用", "原価", "利益",
            "円", "¥", "￥", "$", "price", "amount", "cost", "budget", "revenue", "sales", "salary", "tax"
        )

        fun isExplicitNonCurrencyField(fieldName: String): Boolean {
            val f = fieldName.trim().lowercase()
            if (f.isEmpty()) return false
            return NON_CURRENCY_KEYWORDS.any { f.contains(it.lowercase()) }
        }

        fun isCurrencyFieldByName(fieldName: String): Boolean {
            val f = fieldName.trim()
            if (f.isEmpty() || isExplicitNonCurrencyField(f)) return false
            val lower = f.lowercase()
            return CURRENCY_KEYWORDS.any { lower.contains(it.lowercase()) }
        }

        fun resolveCurrencyLike(fieldName: String, detectedCurrencyFields: Set<String>): Boolean {
            if (isExplicitNonCurrencyField(fieldName)) return false
            return fieldName in detectedCurrencyFields || isCurrencyFieldByName(fieldName)
        }
    }
}

/**
 * [EN] Complete PivotTable specification storing source sheet/range, target anchor cell, Row/Column/Value/Filter fields, and layout options.
 * [JA] ピボットテーブルの元データ範囲・出力先シート／セル・行／列／値／フィルタ配置・デザイン設定を保持する定義モデルです。
 */
data class PivotTableDefinition(
    val id: String,
    val name: String,
    val sourceSheetIndex: Int,
    val sourceSheetName: String,
    val sourceStartRow: Int,
    val sourceStartCol: Int,
    val sourceEndRow: Int,
    val sourceEndCol: Int,
    val targetSheetIndex: Int,
    val targetStartRow: Int,
    val targetStartCol: Int,
    val availableFields: List<String>,
    val numericFields: Set<String>,
    val currencyFields: Set<String> = emptySet(),
    val sourceRecords: List<Map<String, String>> = emptyList(),
    val filterFields: List<String> = emptyList(),
    val selectedFilterValues: Map<String, String> = emptyMap(),
    val columnFields: List<String> = emptyList(),
    val rowFields: List<String> = emptyList(),
    val valueFields: List<PivotValueFieldSpec> = emptyList(),
    val collapsedRowGroups: Set<String> = emptySet(),
    val stylePreset: String = "PivotStyleMedium2",
    val reportLayout: String = "compact", // "compact", "outline", "tabular"
    val subtotalsMode: String = "bottom", // "none", "bottom", "top"
    val grandTotalsMode: String = "both", // "both", "rows", "columns", "none"
    val bandedRows: Boolean = true,
    val bandedCols: Boolean = false,
    val repeatLabels: Boolean = false,
    val blankRows: Boolean = false,
    val lastRenderedStartRow: Int = targetStartRow,
    val lastRenderedEndRow: Int = targetStartRow,
    val lastRenderedStartCol: Int = targetStartCol,
    val lastRenderedEndCol: Int = targetStartCol
) {
    val sourceRangeLabel: String
        get() = "$sourceSheetName!${MasuSheetConfig.formatCellAddress(sourceStartRow, sourceStartCol)}:${MasuSheetConfig.formatCellAddress(sourceEndRow, sourceEndCol)}"

    val targetRangeLabel: String
        get() {
            val c0 = lastRenderedStartCol.coerceAtLeast(0)
            val c1 = maxOf(c0, lastRenderedEndCol)
            val r0 = lastRenderedStartRow.coerceAtLeast(0)
            val r1 = maxOf(r0, lastRenderedEndRow)
            return "${MasuSheetConfig.formatCellAddress(r0, c0)}:${MasuSheetConfig.formatCellAddress(r1, c1)}"
        }

    fun containsCell(sheetIdx: Int, row: Int, col: Int): Boolean {
        if (sheetIdx != targetSheetIndex) return false
        if (lastRenderedEndRow < lastRenderedStartRow || lastRenderedEndCol < lastRenderedStartCol) {
            return row == targetStartRow && col == targetStartCol
        }
        return row in minOf(targetStartRow, lastRenderedStartRow)..maxOf(targetStartRow, lastRenderedEndRow) &&
            col in minOf(targetStartCol, lastRenderedStartCol)..maxOf(targetStartCol, lastRenderedEndCol)
    }

    fun isFieldActive(fieldName: String): Boolean =
        fieldName in filterFields ||
            fieldName in columnFields ||
            fieldName in rowFields ||
            valueFields.any { it.fieldName == fieldName }

    fun isFieldChecked(fieldName: String): Boolean = isFieldActive(fieldName)

    fun distinctValuesForField(fieldName: String): List<String> =
        sourceRecords.mapNotNull { it[fieldName]?.trim()?.takeIf { s -> s.isNotEmpty() } }.distinct()
}

/**
 * [EN] Contiguous non-empty rectangular table region detected on a worksheet with header preview.
 * [JA] シート上で自動検出された連続データ表領域（範囲番地・行数・列見出し一覧）を表すモデルです。
 */
data class DetectedSheetTableRange(
    val rangeText: String,
    val label: String,
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int
)

/**
 * [EN] Selectable table column candidate (index, letter, header title, numeric flag, sample values) for chart X/Y axis binding.
 * [JA] グラフのX軸・Y軸選択UIに提示される表内の各列候補（列記号・見出し名・数値列判定・サンプル値）です。
 */
data class ChartTableColumnOption(
    val colIndex: Int,
    val colLetter: String,
    val headerName: String,
    val rangeText: String,
    val isNumeric: Boolean,
    val sampleValues: List<String> = emptyList()
) {
    val displayLabel: String
        get() = displayLabel(isEn = false)

    fun displayLabel(isEn: Boolean): String = if (
        headerName.equals(colLetter, ignoreCase = true) ||
        headerName.equals("${colLetter}列", ignoreCase = true) ||
        headerName.equals("Col $colLetter", ignoreCase = true) ||
        headerName.isBlank()
    ) {
        val preview = if (sampleValues.isNotEmpty()) {
            " (${sampleValues.take(2).joinToString(", ")}${if (sampleValues.size > 2) "…" else ""})"
        } else {
            ""
        }
        if (isEn) "Col $colLetter$preview" else "${colLetter}列$preview"
    } else {
        "$colLetter: $headerName"
    }
}
