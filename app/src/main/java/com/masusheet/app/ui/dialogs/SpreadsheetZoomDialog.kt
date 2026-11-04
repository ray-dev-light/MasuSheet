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
 * [EN] Zoom Magnification Preset & Custom Percentage Dialog (25%–400%)
 * Implements [SpreadsheetZoomDialog] for quick zoom presets (200%..25%), fit-to-selection, and custom magnification entry.
 *
 * ---
 * [JA / 日本語解説]
 * ステータスバー右端の倍率バッジ等から開き、定型倍率（200%〜25%）・選択範囲に合わせる・自由倍率（25%〜400%）を設定する [SpreadsheetZoomDialog] です。
 * ============================================================================
 */

/**
 * Spreadsheet Zoom Magnification Dialog (`ズーム / 倍率選択・自由指定`):
 * Opened when clicking the `X%` badge at the far right of the bottom status bar (or View -> Zoom).
 * Provides preset magnifications (200%, 150%, 125%, 100%, 75%, 50%, 25%), Fit Selection,
 * and free custom percentage specification (25%..400%).
 */
@Composable
fun SpreadsheetZoomDialog(
    currentZoomPercent: Int,
    language: String,
    onSelectZoom: (Int) -> Unit,
    onFitSelection: () -> Unit,
    onDismiss: () -> Unit
) {
    val isEn = language == "en"
    var customInput by remember(currentZoomPercent) { mutableStateOf(currentZoomPercent.toString()) }
    val parsedZoom = customInput.filter { it.isDigit() }.toIntOrNull()?.coerceIn(25, 400) ?: currentZoomPercent.coerceIn(25, 400)
    val presets = listOf(400, 300, 200, 150, 125, 100, 75, 50, 25)

    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .border(1.5.dp, Color(0xFF1F9D55), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) "🔍 Zoom Magnification (${currentZoomPercent}%)" else "🔍 ズーム・表示倍率の指定 (現在: ${currentZoomPercent}%)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                MasuSheetPillButton(
                    label = if (isEn) "✕ Close" else "✕ 閉じる",
                    onClick = onDismiss
                )
            }

            // 1. Preset Magnification Buttons
            Text(
                text = if (isEn) "1. Preset Magnification" else "1. 倍率を選択 (クリックで即反映)",
                color = Color(0xFF4ADE80),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = currentZoomPercent == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF1F9D55) else Color(0xFF0F172A))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF4ADE80) else Color(0xFF475569),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                customInput = preset.toString()
                                onSelectZoom(preset)
                                onDismiss()
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${preset}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Fit Selection Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF162231))
                    .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(7.dp))
                    .clickable {
                        onFitSelection()
                        onDismiss()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (isEn) "📐 Fit Selection (Auto-scale to selected range)" else "📐 選択範囲に合わせて拡大/縮小 (選択セル範囲を画面にフィット)",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 2. Custom Percentage Specification (25%..400%)
            Text(
                text = if (isEn) "2. Custom Magnification (25% – 400%)" else "2. 倍率の自由指定 (25% 〜 400%)",
                color = Color(0xFF4ADE80),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MasuSheetPillButton(
                    label = "-10%",
                    onClick = {
                        val next = (parsedZoom - 10).coerceIn(25, 400)
                        customInput = next.toString()
                        onSelectZoom(next)
                    }
                )
                MasuSheetPillButton(
                    label = "-1%",
                    onClick = {
                        val next = (parsedZoom - 1).coerceIn(25, 400)
                        customInput = next.toString()
                        onSelectZoom(next)
                    }
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, Color(0xFF1F9D55), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    BasicTextField(
                        value = customInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(3)
                            customInput = digits
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(Color(0xFF4ADE80)),
                        modifier = Modifier.width(48.dp)
                    )
                    Text(
                        text = "%",
                        color = Color(0xFF4ADE80),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                MasuSheetPillButton(
                    label = "+1%",
                    onClick = {
                        val next = (parsedZoom + 1).coerceIn(25, 400)
                        customInput = next.toString()
                        onSelectZoom(next)
                    }
                )
                MasuSheetPillButton(
                    label = "+10%",
                    onClick = {
                        val next = (parsedZoom + 10).coerceIn(25, 400)
                        customInput = next.toString()
                        onSelectZoom(next)
                    }
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MasuSheetPillButton(
                    label = if (isEn) "Reset 100%" else "100% に戻す",
                    onClick = {
                        onSelectZoom(100)
                        onDismiss()
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                MasuSheetPillButton(
                    label = if (isEn) "✓ Apply (${parsedZoom}%)" else "✓ 指定倍率 (${parsedZoom}%) を適用",
                    highlight = true,
                    tag = "ms_btn_apply_custom_zoom",
                    onClick = {
                        onSelectZoom(parsedZoom)
                        onDismiss()
                    }
                )
            }
        }
    }
}

