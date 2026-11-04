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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] Chart Axis Bounds, Units, Log Scale, & Label Formatting Dialog
 * Implements [SpreadsheetChartAxisFormatDialog] for configuring Y/X axis min/max bounds, major units, display units, reverse order, log scale, and axis titles.
 *
 * ---
 * [JA / 日本語解説]
 * グラフ縦軸・横軸の最小値／最大値、目盛間隔、表示単位（千・百万等）、軸の反転、対数目盛、軸ラベルを設定する「軸の書式設定」ダイアログ ([SpreadsheetChartAxisFormatDialog]) です。
 * ============================================================================
 */

/**
 * [EN] Detailed chart axis formatting dialog for min/max bounds, major units, display units, reverse order, and logarithmic scale.
 * [JA] グラフ軸の最小値・最大値・目盛間隔・表示単位・軸反転・対数目盛を設定する詳細設定ダイアログです。
 */
@Composable
fun SpreadsheetChartAxisFormatDialog(
    state: MasuSheetUiState,
    chartObject: NativeSheetObject,
    initialAxis: String, // "x" or "y"
    onApplyAxisFormat: (
        isXAxis: Boolean,
        labelInterval: Int?,
        majorStep: Float?,
        minVal: Float?,
        maxVal: Float?,
        resetToAuto: Boolean,
        closeDialog: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val isEn = state.language == "en"
    var activeAxis by remember(chartObject.id, initialAxis) {
        mutableStateOf(if (initialAxis.lowercase().startsWith("x")) "x" else "y")
    }
    val isX = activeAxis == "x"

    var xLabelInterval by remember(chartObject.id, chartObject.xAxisLabelInterval) {
        mutableStateOf(chartObject.xAxisLabelInterval)
    }
    var xStepInput by remember(chartObject.id, chartObject.xAxisMajorStep) {
        mutableStateOf(chartObject.xAxisMajorStep?.toString().orEmpty())
    }
    var xMinInput by remember(chartObject.id, chartObject.xAxisMin) {
        mutableStateOf(chartObject.xAxisMin?.toString().orEmpty())
    }
    var xMaxInput by remember(chartObject.id, chartObject.xAxisMax) {
        mutableStateOf(chartObject.xAxisMax?.toString().orEmpty())
    }

    var yStepInput by remember(chartObject.id, chartObject.yAxisMajorStep) {
        mutableStateOf(chartObject.yAxisMajorStep?.toString().orEmpty())
    }
    var yMinInput by remember(chartObject.id, chartObject.yAxisMin) {
        mutableStateOf(chartObject.yAxisMin?.toString().orEmpty())
    }
    var yMaxInput by remember(chartObject.id, chartObject.yAxisMax) {
        mutableStateOf(chartObject.yAxisMax?.toString().orEmpty())
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
                .widthIn(max = 500.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .border(1.5.dp, Color(0xFF107C41), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) "Format Chart Axis & Display Interval" else "軸の書式設定・表示間隔（目盛間隔）の調整",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                MasuSheetPillButton(if (isEn) "✕ Close" else "✕ 閉じる", onClick = onDismiss)
            }

            // Axis Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MasuSheetPillButton(
                    label = if (isEn) "📏 X-Axis (Horizontal)" else "📏 X軸（横軸）の表示間隔",
                    highlight = isX,
                    onClick = { activeAxis = "x" }
                )
                MasuSheetPillButton(
                    label = if (isEn) "📐 Y-Axis (Vertical)" else "📐 Y軸（縦軸）の表示間隔・最大/最小",
                    highlight = !isX,
                    onClick = { activeAxis = "y" }
                )
            }

            if (isX) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isEn) "X-Axis Label Display Interval (Skip Interval)" else "X軸ラベルの表示間隔 (何項目おきに表示するか):",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            0 to (if (isEn) "Auto" else "自動"),
                            1 to (if (isEn) "All (Every 1)" else "すべて表示 (1ごと)"),
                            2 to (if (isEn) "Every 2" else "2おき"),
                            3 to (if (isEn) "Every 3" else "3おき"),
                            5 to (if (isEn) "Every 5" else "5おき"),
                            10 to (if (isEn) "Every 10" else "10おき")
                        )
                        presets.forEach { (interval, lbl) ->
                            MasuSheetPillButton(
                                label = lbl,
                                highlight = xLabelInterval == interval,
                                onClick = {
                                    xLabelInterval = interval
                                    onApplyAxisFormat(
                                        true,
                                        interval,
                                        xStepInput.toFloatOrNull(),
                                        xMinInput.toFloatOrNull(),
                                        xMaxInput.toFloatOrNull(),
                                        false,
                                        false
                                    )
                                }
                            )
                        }
                    }

                    Text(
                        text = if (isEn) "Numeric / Scatter X-Axis Scale (Optional):" else "数値X軸・散布図の目盛間隔・最小値・最大値 (任意):",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isEn) "Step:" else "目盛間隔:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        BasicTextField(
                            value = xStepInput,
                            onValueChange = { xStepInput = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                        Text(if (isEn) "Min:" else "最小:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        BasicTextField(
                            value = xMinInput,
                            onValueChange = { xMinInput = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                        Text(if (isEn) "Max:" else "最大:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        BasicTextField(
                            value = xMaxInput,
                            onValueChange = { xMaxInput = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allYVals = chartObject.chartSeries.flatMap { it.values }.ifEmpty { chartObject.chartValues }
                    val effYMin = chartObject.yAxisMin ?: (allYVals.minOrNull() ?: 0f).coerceAtMost(0f)
                    val effYMax = (chartObject.yAxisMax ?: (allYVals.maxOrNull() ?: 100f)).coerceAtLeast(effYMin + 1f)
                    val effYSpan = (effYMax - effYMin).coerceAtLeast(1f)
                    val dynamicSteps = remember(effYSpan) {
                        if (effYSpan <= 200f) {
                            listOf(1f, 2f, 5f, 10f, 20f, 25f, 50f, 100f)
                        } else {
                            val exp = kotlin.math.floor(kotlin.math.log10(effYSpan.toDouble())).toInt() - 1
                            val base = Math.pow(10.0, exp.coerceAtLeast(0).toDouble()).toFloat()
                            listOf(0.5f * base, 1f * base, 2f * base, 2.5f * base, 5f * base, 10f * base)
                                .map { it.coerceAtLeast(1f) }
                                .distinct()
                        }
                    }

                    Text(
                        text = if (isEn) "1. By Number of Divisions (Equal Intervals from Min to Max):" else "1. 目盛の分割数で指定 (最小〜最大を均等分割):",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MasuSheetPillButton(
                            label = if (isEn) "Auto" else "自動",
                            highlight = yStepInput.isBlank(),
                            onClick = {
                                yStepInput = ""
                                onApplyAxisFormat(false, null, null, yMinInput.toFloatOrNull(), yMaxInput.toFloatOrNull(), false, false)
                            }
                        )
                        listOf(4, 5, 8, 10, 15, 20).forEach { divCount ->
                            val negVal = -divCount.toFloat()
                            val divLabel = if (isEn) "$divCount Divs" else "${divCount}分割"
                            MasuSheetPillButton(
                                label = divLabel,
                                highlight = yStepInput.toFloatOrNull() == negVal,
                                onClick = {
                                    yStepInput = negVal.toInt().toString()
                                    onApplyAxisFormat(false, null, negVal, yMinInput.toFloatOrNull(), yMaxInput.toFloatOrNull(), false, false)
                                }
                            )
                        }
                    }

                    Text(
                        text = if (isEn) "2. By Step Unit (Matched to Current Data Scale):" else "2. 目盛の刻み単位で指定 (現在のデータ規模に最適化):",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dynamicSteps.forEach { stepPreset ->
                            val rawStr = stepPreset.toLong().toString()
                            val dispStr = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(stepPreset.toLong())
                            MasuSheetPillButton(
                                label = dispStr,
                                highlight = yStepInput.replace(",", "").toFloatOrNull() == stepPreset,
                                onClick = {
                                    yStepInput = rawStr
                                    onApplyAxisFormat(false, null, stepPreset, yMinInput.toFloatOrNull(), yMaxInput.toFloatOrNull(), false, false)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isEn) "Interval:" else "表示間隔:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        BasicTextField(
                            value = yStepInput,
                            onValueChange = { yStepInput = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFFFBBF24)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                        Text(if (isEn) "Min:" else "最小値:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        BasicTextField(
                            value = yMinInput,
                            onValueChange = { yMinInput = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFFFBBF24)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                        Text(if (isEn) "Max:" else "最大値:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        BasicTextField(
                            value = yMaxInput,
                            onValueChange = { yMaxInput = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFFFBBF24)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MasuSheetPillButton(
                    label = if (isEn) "🔄 Reset to Auto" else "🔄 自動設定に戻す",
                    onClick = {
                        if (isX) {
                            xLabelInterval = 0
                            xStepInput = ""
                            xMinInput = ""
                            xMaxInput = ""
                            onApplyAxisFormat(true, 0, null, null, null, true, false)
                        } else {
                            yStepInput = ""
                            yMinInput = ""
                            yMaxInput = ""
                            onApplyAxisFormat(false, null, null, null, null, true, false)
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MasuSheetPillButton(if (isEn) "Cancel" else "キャンセル", onClick = onDismiss)
                    MasuSheetPillButton(
                        label = if (isEn) "✓ Apply & Close" else "✓ 適用して閉じる",
                        highlight = true,
                        onClick = {
                            if (isX) {
                                onApplyAxisFormat(
                                    true,
                                    xLabelInterval,
                                    xStepInput.toFloatOrNull(),
                                    xMinInput.toFloatOrNull(),
                                    xMaxInput.toFloatOrNull(),
                                    false,
                                    true
                                )
                            } else {
                                onApplyAxisFormat(
                                    false,
                                    null,
                                    yStepInput.toFloatOrNull(),
                                    yMinInput.toFloatOrNull(),
                                    yMaxInput.toFloatOrNull(),
                                    false,
                                    true
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
