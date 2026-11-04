package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] 60-Color Theme Palette, HSL Tint/Shade Generator, & Theme Presets
 * Implements [SpreadsheetColorPalette] with 10 base theme colors × 5 HSL tint/shade rows (60 colors), 10 standard colors, RGB/Hex utilities, and named theme presets.
 *
 * ---
 * [JA / 日本語解説]
 * 10種の基本テーマカラー×5段階のHSL明暗バリエーション（計60色）、標準10色、RGB/16進カラー変換、およびテーマプリセットを提供する [SpreadsheetColorPalette] モジュールです。
 * ============================================================================
 */

/**
 * Full Microsoft Spreadsheet Color System:
 * - 10 Base Theme Colors × 6 rows (Base + 5 HSL Tint/Shade rows = 60 colors)
 * - 10 Standard Colors
 * - Recent Colors + Custom 24-bit RGB (0..255 sliders + #RRGGBB Hex)
 * - Theme presets (Office, Grayscale, Blue Warm, Green, Violet)
 */
object SpreadsheetColorPalette {
    val DEFAULT_THEME_BASE_COLORS: List<Int> = listOf(
        0xFFFFFFFF.toInt(), // Background 1 (白)
        0xFF000000.toInt(), // Text 1 (黒)
        0xFFE7E6E6.toInt(), // Background 2 (薄い灰色)
        0xFF44546A.toInt(), // Text 2 (ブルーグレー)
        0xFF4472C4.toInt(), // Accent 1 (青)
        0xFFED7D31.toInt(), // Accent 2 (オレンジ)
        0xFFA5A5A5.toInt(), // Accent 3 (灰色)
        0xFFFFC000.toInt(), // Accent 4 (ゴールド)
        0xFF5B9BD5.toInt(), // Accent 5 (水色)
        0xFF70AD47.toInt()  // Accent 6 (緑)
    )

    val THEME_COLUMN_NAMES_JA: List<String> = listOf(
        "白 (背景1)", "黒 (文字1)", "薄い灰 (背景2)", "青灰 (文字2)",
        "アクセント1", "アクセント2", "アクセント3", "アクセント4", "アクセント5", "アクセント6"
    )

    val STANDARD_COLORS: List<Pair<String, Int>> = listOf(
        "濃い赤" to 0xFFC00000.toInt(),
        "赤" to 0xFFFF0000.toInt(),
        "オレンジ" to 0xFFFFC000.toInt(),
        "黄" to 0xFFFFFF00.toInt(),
        "薄い緑" to 0xFF92D050.toInt(),
        "緑" to 0xFF00B050.toInt(),
        "薄い青" to 0xFF00B0F0.toInt(),
        "青" to 0xFF0070C0.toInt(),
        "濃い青" to 0xFF002060.toInt(),
        "紫" to 0xFF7030A0.toInt()
    )

    val THEME_PRESETS: Map<String, List<Int>> = mapOf(
        "Office (標準)" to DEFAULT_THEME_BASE_COLORS,
        "グレースケール" to listOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFF2F2F2.toInt(), 0xFF333333.toInt(),
            0xFF595959.toInt(), 0xFF7F7F7F.toInt(), 0xFFA6A6A6.toInt(), 0xFFBFBFBF.toInt(), 0xFFD9D9D9.toInt(), 0xFF404040.toInt()
        ),
        "エメラルド・グリーン" to listOf(
            0xFFFFFFFF.toInt(), 0xFF0F291E.toInt(), 0xFFE8F5E9.toInt(), 0xFF1B4332.toInt(),
            0xFF1F9D55.toInt(), 0xFF2D6A4F.toInt(), 0xFF52B788.toInt(), 0xFF74C69D.toInt(), 0xFF40916C.toInt(), 0xFF95D5B2.toInt()
        ),
        "オーシャン・ブルー" to listOf(
            0xFFFFFFFF.toInt(), 0xFF0F172A.toInt(), 0xFFEFF6FF.toInt(), 0xFF1E3A8A.toInt(),
            0xFF2563EB.toInt(), 0xFF0284C7.toInt(), 0xFF0891B2.toInt(), 0xFF4F46E5.toInt(), 0xFF38BDF8.toInt(), 0xFF14B8A6.toInt()
        ),
        "ウォーム・サンセット" to listOf(
            0xFFFFFFFF.toInt(), 0xFF27120B.toInt(), 0xFFFFF7ED.toInt(), 0xFF7C2D12.toInt(),
            0xFFEA580C.toInt(), 0xFFD97706.toInt(), 0xFFDC2626.toInt(), 0xFFF59E0B.toInt(), 0xFFE11D48.toInt(), 0xFF65A30D.toInt()
        )
    )

    fun applySpreadsheetTint(argb: Int, tint: Double): Int {
        if (kotlin.math.abs(tint) < 1e-6) return argb or 0xFF000000.toInt()
        val r = ((argb ushr 16) and 0xFF) / 255.0
        val g = ((argb ushr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        var l = (max + min) / 2.0
        val s: Double
        val h: Double
        if (d < 1e-9) {
            h = 0.0
            s = 0.0
        } else {
            s = if (l > 0.5) d / (2.0 - max - min) else d / (max + min)
            val rawH = when (max) {
                r -> (g - b) / d + (if (g < b) 6.0 else 0.0)
                g -> (b - r) / d + 2.0
                else -> (r - g) / d + 4.0
            }
            h = rawH / 6.0
        }

        val t = tint.coerceIn(-1.0, 1.0)
        l = if (t < 0.0) l * (1.0 + t) else l * (1.0 - t) + t
        l = l.coerceIn(0.0, 1.0)

        fun hueToRgb(p: Double, q: Double, tcInput: Double): Double {
            var tc = tcInput
            if (tc < 0.0) tc += 1.0
            if (tc > 1.0) tc -= 1.0
            return when {
                tc < 1.0 / 6.0 -> p + (q - p) * 6.0 * tc
                tc < 1.0 / 2.0 -> q
                tc < 2.0 / 3.0 -> p + (q - p) * (2.0 / 3.0 - tc) * 6.0
                else -> p
            }
        }

        val (ro, go, bo) = if (s < 1e-9) {
            Triple(l, l, l)
        } else {
            val q = if (l < 0.5) l * (1.0 + s) else l + s - l * s
            val p = 2.0 * l - q
            Triple(
                hueToRgb(p, q, h + 1.0 / 3.0),
                hueToRgb(p, q, h),
                hueToRgb(p, q, h - 1.0 / 3.0)
            )
        }
        val ri = (ro * 255.0 + 0.5).toInt().coerceIn(0, 255)
        val gi = (go * 255.0 + 0.5).toInt().coerceIn(0, 255)
        val bi = (bo * 255.0 + 0.5).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /**
     * Generates the 6 rows × 10 columns (60 colors) Spreadsheet Theme Color Matrix from the 10 base theme colors.
     * Row 0 = Base colors; Rows 1..5 = Spreadsheet's 5 tint/shade steps per column.
     */
    fun buildThemeColorMatrix(baseColors: List<Int> = DEFAULT_THEME_BASE_COLORS): List<List<Int>> {
        val cols = (0 until 10).map { idx ->
            baseColors.getOrElse(idx) { DEFAULT_THEME_BASE_COLORS[idx] } or 0xFF000000.toInt()
        }
        val tintsPerColumn: List<DoubleArray> = cols.map { c ->
            val r = ((c ushr 16) and 0xFF) / 255.0
            val g = ((c ushr 8) and 0xFF) / 255.0
            val b = (c and 0xFF) / 255.0
            val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            when {
                lum >= 0.96 -> doubleArrayOf(0.0, -0.05, -0.15, -0.25, -0.35, -0.50)
                lum <= 0.04 -> doubleArrayOf(0.0, 0.50, 0.35, 0.25, 0.15, 0.05)
                lum >= 0.80 -> doubleArrayOf(0.0, -0.10, -0.25, -0.50, -0.75, -0.90)
                else -> doubleArrayOf(0.0, 0.80, 0.60, 0.40, -0.25, -0.50)
            }
        }
        return (0 until 6).map { rowIdx ->
            (0 until 10).map { colIdx ->
                applySpreadsheetTint(cols[colIdx], tintsPerColumn[colIdx][rowIdx])
            }
        }
    }

    fun toHexString(argb: Int): String {
        val rgb = argb and 0xFFFFFF
        return String.format("#%06X", rgb)
    }

    fun parseHexOrNull(raw: String): Int? {
        val clean = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (clean.length != 6 && clean.length != 8) return null
        val parsed = clean.toLongOrNull(16) ?: return null
        return if (clean.length == 6) {
            (0xFF000000L or parsed).toInt()
        } else {
            parsed.toInt()
        }
    }

    fun fromRgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
