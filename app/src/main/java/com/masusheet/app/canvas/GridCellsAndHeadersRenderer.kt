package com.masusheet.app.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.masusheet.app.core.FormulaPointModeHelper
import com.masusheet.app.core.InlineCheckboxHelper
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.NativeCellModel
import com.masusheet.app.model.NativeSparklineModel
import com.masusheet.app.model.NativeViewportSnapshot
import com.masusheet.app.viewmodel.getActiveCellValidation
import com.masusheet.app.viewmodel.isTableHeaderCell
import com.masusheet.app.viewmodel.previewAutoFillCells
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ============================================================================
 * [EN] Grid Layer 1 (Cells, Merges, Borders, Sparklines, Checkboxes) & Layer 2 (Headers, Selection) Renderer
 * Renders 4-quadrant frozen/scrollable gridlines, off-screen-anchored merged cells, borders, wrapped text, sparklines, checkboxes, row/column headers, and formula highlights.
 *
 * ---
 * [JA / 日本語解説]
 * 4象限対応のセル背景・罫線・画面外起点の結合セル・折り返し文字列・スパークライン・セル内チェックボックス（Layer 1）と、選択枠・行列ヘッダ・数式参照枠（Layer 2）を描画します。
 * ============================================================================
 */

/**
 * [EN] Renders Layer 1 (sheet background, frozen-pane quadrants, gridlines, fills, borders, text, sparklines, and checkboxes) onto the given canvas.
 * [JA] レイヤー1（シート背景、固定ペイン別の4象限セル、グリッド線、塗りつぶし、罫線、文字列、スパークライン、チェックボックス）を描画します。
 */
internal fun MasuSheetNativeGridView.renderStaticCellsLayer(canvas: Canvas, w: Int, h: Int) {
    val sheetBg = if (isDarkTheme) 0xFF14181D.toInt() else 0xFFFFFFFF.toInt()
    val gridlineCol = if (isDarkTheme) 0xFF26303D.toInt() else 0xFFE2E8F0.toInt()
    val defaultTextCol = if (isDarkTheme) 0xFFF1F5F9.toInt() else 0xFF0F172A.toInt()

    bgPaint.style = Paint.Style.FILL
    bgPaint.color = sheetBg
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

    val snap = currentViewport
    if (snap.rowHeights.isEmpty() || snap.colWidths.isEmpty()) return

    val fRows = freezeRows.coerceIn(0, snap.rowHeights.size)
    val fCols = freezeCols.coerceIn(0, snap.colWidths.size)
    val fRightPx = if (fCols > 0) (rowHeaderWidthPx + ptToPx(snap.colOffsets[fCols])).coerceAtMost(w.toFloat()) else rowHeaderWidthPx
    val fBottomPx = if (fRows > 0) (colHeaderHeightPx + ptToPx(snap.rowOffsets[fRows])).coerceAtMost(h.toFloat()) else colHeaderHeightPx

    val scrollStartCol = max(fCols, snap.colAtX(pxToPt(scrollXPx.toFloat())).coerceAtLeast(0))
    val scrollEndCol = snap.colAtX(pxToPt((scrollXPx + w).toFloat())).coerceAtMost(snap.colWidths.size - 1)
    val scrollStartRow = max(fRows, snap.rowAtY(pxToPt(scrollYPx.toFloat())).coerceAtLeast(0))
    val scrollEndRow = snap.rowAtY(pxToPt((scrollYPx + h).toFloat())).coerceAtMost(snap.rowHeights.size - 1)

    val isSalesDashboard = viewModel.uiState.value.activeSample == "sales" &&
        snap.cellAt(1, 1)?.displayText == "Quarterly Sales Dashboard"
    val activeFilters = viewModel.uiState.value.activeColumnFilters

    // 1. Main scrollable quadrant (Bottom-Right, or full sheet when freezeRows == 0 && freezeCols == 0)
    if (fRightPx < w && fBottomPx < h && scrollStartRow <= scrollEndRow && scrollStartCol <= scrollEndCol) {
        renderStaticQuadrant(
            canvas = canvas,
            snap = snap,
            clipLeft = fRightPx,
            clipTop = fBottomPx,
            clipRight = w.toFloat(),
            clipBottom = h.toFloat(),
            qStartRow = scrollStartRow,
            qEndRow = scrollEndRow,
            qStartCol = scrollStartCol,
            qEndCol = scrollEndCol,
            effScrollX = scrollXPx,
            effScrollY = scrollYPx,
            sheetBg = sheetBg,
            gridlineCol = gridlineCol,
            defaultTextCol = defaultTextCol,
            isSalesDashboard = isSalesDashboard,
            activeFilters = activeFilters
        )
    }

    // 2. Frozen Top Rows quadrant (Top-Right: rows 0 until fRows, scrollable columns)
    if (fRows > 0 && fRightPx < w && fBottomPx > colHeaderHeightPx && scrollStartCol <= scrollEndCol) {
        renderStaticQuadrant(
            canvas = canvas,
            snap = snap,
            clipLeft = fRightPx,
            clipTop = colHeaderHeightPx,
            clipRight = w.toFloat(),
            clipBottom = fBottomPx,
            qStartRow = 0,
            qEndRow = fRows - 1,
            qStartCol = scrollStartCol,
            qEndCol = scrollEndCol,
            effScrollX = scrollXPx,
            effScrollY = 0,
            sheetBg = sheetBg,
            gridlineCol = gridlineCol,
            defaultTextCol = defaultTextCol,
            isSalesDashboard = isSalesDashboard,
            activeFilters = activeFilters
        )
    }

    // 3. Frozen Left Columns quadrant (Bottom-Left: scrollable rows, cols 0 until fCols)
    if (fCols > 0 && fRightPx > rowHeaderWidthPx && fBottomPx < h && scrollStartRow <= scrollEndRow) {
        renderStaticQuadrant(
            canvas = canvas,
            snap = snap,
            clipLeft = rowHeaderWidthPx,
            clipTop = fBottomPx,
            clipRight = fRightPx,
            clipBottom = h.toFloat(),
            qStartRow = scrollStartRow,
            qEndRow = scrollEndRow,
            qStartCol = 0,
            qEndCol = fCols - 1,
            effScrollX = 0,
            effScrollY = scrollYPx,
            sheetBg = sheetBg,
            gridlineCol = gridlineCol,
            defaultTextCol = defaultTextCol,
            isSalesDashboard = isSalesDashboard,
            activeFilters = activeFilters
        )
    }

    // 4. Frozen Top-Left Corner quadrant (rows 0 until fRows, cols 0 until fCols)
    if (fRows > 0 && fCols > 0 && fRightPx > rowHeaderWidthPx && fBottomPx > colHeaderHeightPx) {
        renderStaticQuadrant(
            canvas = canvas,
            snap = snap,
            clipLeft = rowHeaderWidthPx,
            clipTop = colHeaderHeightPx,
            clipRight = fRightPx,
            clipBottom = fBottomPx,
            qStartRow = 0,
            qEndRow = fRows - 1,
            qStartCol = 0,
            qEndCol = fCols - 1,
            effScrollX = 0,
            effScrollY = 0,
            sheetBg = sheetBg,
            gridlineCol = gridlineCol,
            defaultTextCol = defaultTextCol,
            isSalesDashboard = isSalesDashboard,
            activeFilters = activeFilters
        )
    }
}

/**
 * [EN] Computes the zoomed screen-space `RectF` of `(row, col)` (spanning any merged range) within a scroll/freeze quadrant.
 * [JA] 指定されたスクロール／固定ペイン象限内におけるセル `(row, col)`（結合セル範囲を含む）のズーム適用済み画面座標 `RectF` を算出します。
 */
internal fun MasuSheetNativeGridView.computeQuadrantCellRect(
    snap: NativeViewportSnapshot,
    row: Int,
    col: Int,
    effScrollX: Int,
    effScrollY: Int
): RectF {
    val boundsPt = snap.cellBoundsPt(row, col)
    val left = rowHeaderWidthPx + ptToPx(boundsPt[0]) - effScrollX
    val top = colHeaderHeightPx + ptToPx(boundsPt[1]) - effScrollY
    val right = rowHeaderWidthPx + ptToPx(boundsPt[2]) - effScrollX
    val bottom = colHeaderHeightPx + ptToPx(boundsPt[3]) - effScrollY
    return RectF(left, top, right, bottom)
}

/**
 * [EN] Clips to `[clipLeft, clipTop, clipRight, clipBottom]` and draws visible gridlines, conditional data bars, cell fills, merged cells, borders, and text for one freeze quadrant.
 * [JA] 指定クリップ領域内の1象限について、可視セルの背景色・データバー・グリッド線・結合セル・罫線・テキストを一括描画します。
 */
internal fun MasuSheetNativeGridView.renderStaticQuadrant(
    canvas: Canvas,
    snap: NativeViewportSnapshot,
    clipLeft: Float,
    clipTop: Float,
    clipRight: Float,
    clipBottom: Float,
    qStartRow: Int,
    qEndRow: Int,
    qStartCol: Int,
    qEndCol: Int,
    effScrollX: Int,
    effScrollY: Int,
    sheetBg: Int,
    gridlineCol: Int,
    defaultTextCol: Int,
    isSalesDashboard: Boolean,
    activeFilters: Map<Pair<Int, Int>, Set<String>>
) {
    val saveCount = canvas.save()
    canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom)
    bgPaint.style = Paint.Style.FILL
    bgPaint.color = sheetBg
    canvas.drawRect(clipLeft, clipTop, clipRight, clipBottom, bgPaint)

    // Collect merged cells whose anchor is above/left of (qStartRow, qStartCol) yet still intersect this quadrant
    val offscreenAnchorMerges = if (snap.merges.isNotEmpty()) {
        snap.merges.filter { m ->
            m.endRow >= qStartRow && m.startRow <= qEndRow &&
                m.endCol >= qStartCol && m.startCol <= qEndCol &&
                (m.startRow < qStartRow || m.startCol < qStartCol)
        }
    } else {
        emptyList()
    }

    // 2a. Draw cell backgrounds and gridlines (including off-screen-anchored merges)
    gridlinePaint.color = gridlineCol
    for (m in offscreenAnchorMerges) {
        val boundsRect = computeQuadrantCellRect(snap, m.startRow, m.startCol, effScrollX, effScrollY)
        bgPaint.color = sheetBg
        canvas.drawRect(boundsRect, bgPaint)
        val cell = snap.cellAt(m.startRow, m.startCol)
        if (cell != null && cell.bgColor != 0) {
            bgPaint.color = adjustCellBgForTheme(cell.bgColor, isDarkTheme)
            canvas.drawRect(boundsRect, bgPaint)
        }
        if (showGridlines) {
            canvas.drawRect(boundsRect, gridlinePaint)
        }
    }

    for (r in qStartRow..qEndRow) {
        if (snap.rowHeights[r] < 1f) continue
        val rowTopPx = colHeaderHeightPx + ptToPx(snap.rowOffsets[r]) - effScrollY
        val rowBotPx = colHeaderHeightPx + ptToPx(snap.rowOffsets[r + 1]) - effScrollY
        if (rowBotPx <= rowTopPx) continue
        for (c in qStartCol..qEndCol) {
            val merge = snap.mergeAt(r, c)
            if (merge != null && (merge.startRow != r || merge.startCol != c)) {
                continue
            }
            if (merge == null && snap.colWidths[c] < 1f) continue
            val boundsRect = if (merge != null) {
                computeQuadrantCellRect(snap, merge.startRow, merge.startCol, effScrollX, effScrollY)
            } else {
                val colLeftPx = rowHeaderWidthPx + ptToPx(snap.colOffsets[c]) - effScrollX
                val colRightPx = rowHeaderWidthPx + ptToPx(snap.colOffsets[c + 1]) - effScrollX
                scratchRect.set(colLeftPx, rowTopPx, colRightPx, rowBotPx)
                scratchRect
            }

            if (merge != null) {
                bgPaint.color = sheetBg
                canvas.drawRect(boundsRect, bgPaint)
            }
            val cell = snap.cellAt(r, c)
            if (cell != null && cell.bgColor != 0) {
                bgPaint.color = adjustCellBgForTheme(cell.bgColor, isDarkTheme)
                canvas.drawRect(boundsRect, bgPaint)
            }
            if (showGridlines) {
                canvas.drawRect(boundsRect, gridlinePaint)
            }
        }
    }

    // Helper to draw foreground content (borders, sparkline, text, filter badge) for an anchor cell
    fun drawAnchorForeground(r: Int, c: Int) {
        val rect = computeQuadrantCellRect(snap, r, c, effScrollX, effScrollY)
        if (rect.right < clipLeft || rect.bottom < clipTop || rect.left > clipRight || rect.top > clipBottom) return

        val userSparkline = snap.sparklines.firstOrNull { it.row == r && it.col == c }
        if (userSparkline != null) {
            val spVals = resolveSparklineValues(snap, userSparkline.sourceRange)
            if (spVals.isNotEmpty()) {
                drawCustomSparkline(canvas, rect, userSparkline, spVals)
            }
        } else if (isSalesDashboard && r in 4..9 && c == 8) {
            val qVals = floatArrayOf(
                parseCellNumericValue(snap.cellAt(r, 2)?.displayText),
                parseCellNumericValue(snap.cellAt(r, 3)?.displayText),
                parseCellNumericValue(snap.cellAt(r, 4)?.displayText),
                parseCellNumericValue(snap.cellAt(r, 5)?.displayText)
            )
            drawInCellSparkline(canvas, rect, qVals)
        }

        val cell = snap.cellAt(r, c)
        if (cell != null) {
            if (isSalesDashboard && r in 4..9 && c == 6) {
                val numVal = parseCellNumericValue(cell.displayText)
                if (numVal > 0f) {
                    val ratio = (numVal / 751200f).coerceIn(0.05f, 1f)
                    val barPad = 2.5f * density
                    val barRight = rect.left + barPad + (rect.width() - barPad * 2f) * ratio
                    objectPaint.style = Paint.Style.FILL
                    objectPaint.color = if (isDarkTheme) 0x553B82F6 else 0x383B82F6
                    canvas.drawRoundRect(
                        rect.left + barPad,
                        rect.top + barPad,
                        barRight,
                        rect.bottom - barPad,
                        2.5f * density,
                        2.5f * density,
                        objectPaint
                    )
                }
            }

            drawCellBorders(canvas, rect, cell)

            val visibleText = if (showFormulas && cell.editText.isNotEmpty()) cell.editText else cell.displayText
            if (visibleText.isNotEmpty()) {
                drawCellContent(canvas, rect, cell, defaultTextCol)
            }

            if (isSalesDashboard && r in 4..9 && c == 7) {
                val pctVal = parseCellNumericValue(cell.displayText)
                val (kpiIcon, kpiCol) = when {
                    pctVal >= 18f -> "▲" to 0xFF16A34A.toInt()
                    pctVal >= 14f -> "▶" to 0xFFD97706.toInt()
                    else -> "▼" to 0xFFDC2626.toInt()
                }
                textPaint.color = kpiCol
                textPaint.textSize = 9.5f * density * zoomScale
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val fm = textPaint.fontMetrics
                val by = rect.centerY() - (fm.descent + fm.ascent) * 0.5f
                canvas.drawText(kpiIcon, rect.left + 4f * density, by, textPaint)
            }
        }

        if (viewModel.isTableHeaderCell(snap, r, c)) {
            val isColFiltered = (snap.sheetIndex to c) in activeFilters
            val badgeSize = 13.5f * density * zoomScale
            val bLeft = rect.right - badgeSize - 2.5f * density
            val bTop = rect.top + (rect.height() - badgeSize) * 0.5f
            if (bLeft > rect.left + 16f * density) {
                val badgeRect = RectF(bLeft, bTop, bLeft + badgeSize, bTop + badgeSize)
                checkboxBoxPaint.style = Paint.Style.FILL
                checkboxBoxPaint.color = if (isColFiltered) 0xFF107C41.toInt() else 0xDEE2E8F0.toInt()
                canvas.drawRoundRect(badgeRect, 2.5f * density, 2.5f * density, checkboxBoxPaint)
                checkboxBoxPaint.style = Paint.Style.STROKE
                checkboxBoxPaint.strokeWidth = 0.8f * density
                checkboxBoxPaint.color = if (isColFiltered) 0xFF4ADE80.toInt() else 0xFF64748B.toInt()
                canvas.drawRoundRect(badgeRect, 2.5f * density, 2.5f * density, checkboxBoxPaint)
                headerTextPaint.textSize = 9.0f * density * zoomScale
                headerTextPaint.color = if (isColFiltered) 0xFFFFFFFF.toInt() else 0xFF1E293B.toInt()
                canvas.drawText("▾", badgeRect.centerX(), badgeRect.centerY() + 3f * density, headerTextPaint)
                headerTextPaint.textSize = 11.5f * density
            }
        }
    }

    // 2b. Draw off-screen-anchored merges first, then in-range cells
    for (m in offscreenAnchorMerges) {
        drawAnchorForeground(m.startRow, m.startCol)
    }
    for (r in qStartRow..qEndRow) {
        if (snap.rowHeights[r] < 1f) continue
        for (c in qStartCol..qEndCol) {
            val merge = snap.mergeAt(r, c)
            if (merge != null && (merge.startRow != r || merge.startCol != c)) {
                continue
            }
            if (merge == null && snap.colWidths[c] < 1f) continue
            drawAnchorForeground(r, c)
        }
    }

    canvas.restoreToCount(saveCount)
}

/**
 * [EN] Parses a formatted cell string (stripping `$`, `,`, and `%`) into a `Float` for conditional-formatting data bar and sparkline scaling.
 * [JA] 通貨記号・カンマ・パーセント記号を除いてセル表示文字列を `Float` に変換し、データバーやスパークラインの比率計算に使用します。
 */
internal fun MasuSheetNativeGridView.parseCellNumericValue(raw: String?): Float {
    if (raw.isNullOrBlank()) return 0f
    val cleaned = raw.replace("$", "").replace(",", "").replace("%", "").trim()
    return cleaned.toFloatOrNull() ?: 0f
}

/**
 * [EN] Parses an A1-style single cell token (such as `B4` or `$C$12`) into 0-based `(row, col)` coordinates, or `null` if invalid.
 * [JA] `B4` や `$C$12` などの単一A1セル参照文字列を 0始まりの `(row, col)` 座標ペアに変換します（不正時は `null`）。
 */
internal fun MasuSheetNativeGridView.parseSingleA1Coord(ref: String): Pair<Int, Int>? {
    val m = Regex("""^\$?([A-Za-z]+)\$?(\d+)$""").matchEntire(ref.trim()) ?: return null
    val colLetters = m.groupValues[1].uppercase()
    val rowNum = m.groupValues[2].toIntOrNull() ?: return null
    var col = 0
    for (ch in colLetters) {
        col = col * 26 + (ch - 'A' + 1)
    }
    return (rowNum - 1).coerceAtLeast(0) to (col - 1).coerceAtLeast(0)
}

/**
 * [EN] Reads finite numeric cell values across `sourceRange` on `snap` in row-major order to feed in-cell sparkline rendering.
 * [JA] 指定された `sourceRange` 範囲内のセルから有限数値を走査・抽出し、セル内スパークライン描画用の配列を返します。
 */
internal fun MasuSheetNativeGridView.resolveSparklineValues(snap: NativeViewportSnapshot, sourceRange: String): FloatArray {
    val clean = sourceRange.substringAfterLast('!').trim()
    val parts = clean.split(':')
    val p0 = parseSingleA1Coord(parts.firstOrNull().orEmpty()) ?: return FloatArray(0)
    val p1 = if (parts.size >= 2) parseSingleA1Coord(parts[1]) ?: p0 else p0
    val r0 = min(p0.first, p1.first)
    val r1 = max(p0.first, p1.first)
    val c0 = min(p0.second, p1.second)
    val c1 = max(p0.second, p1.second)
    val out = ArrayList<Float>()
    for (rr in r0..r1) {
        for (cc in c0..c1) {
            val raw = snap.cellAt(rr, cc)?.displayText
            val cleaned = raw?.replace("$", "")?.replace(",", "")?.replace("%", "")?.trim()
            val num = cleaned?.toFloatOrNull()
            if (num != null) out.add(num)
        }
    }
    return out.toFloatArray()
}

/**
 * [EN] Draws a user-inserted sparkline (`line`, `column`, or `winloss`, with optional point markers) inside cell bounds `rect`.
 * [JA] セル矩形 `rect` 内にユーザー設定のスパークライン（折れ線・縦棒・勝敗、および任意のマーカー点）を描画します。
 */
internal fun MasuSheetNativeGridView.drawCustomSparkline(
    canvas: Canvas,
    rect: RectF,
    spark: NativeSparklineModel,
    values: FloatArray
) {
    if (values.isEmpty()) return
    val strokeArgb = (0xFF shl 24) or (spark.colorRgb and 0xFFFFFF)
    val padX = 5f * density
    val padY = 4f * density
    val plotW = (rect.width() - padX * 2f).coerceAtLeast(8f)
    val plotH = (rect.height() - padY * 2f).coerceAtLeast(6f)
    when (spark.kind.lowercase()) {
        "column" -> {
            val minV = min(0f, values.minOrNull() ?: 0f)
            val maxV = max(1f, values.maxOrNull() ?: 1f)
            val span = (maxV - minV).coerceAtLeast(1f)
            val zeroY = rect.bottom - padY - ((0f - minV) / span) * plotH
            val slotW = plotW / values.size.coerceAtLeast(1)
            val barW = (slotW * 0.68f).coerceAtLeast(2f * density)
            objectPaint.style = Paint.Style.FILL
            for (i in values.indices) {
                val v = values[i]
                val xLeft = rect.left + padX + i * slotW + (slotW - barW) * 0.5f
                val valY = rect.bottom - padY - ((v - minV) / span) * plotH
                objectPaint.color = if (v >= 0f) strokeArgb else 0xFFDC2626.toInt()
                val topY = min(zeroY, valY)
                val botY = max(zeroY, valY).coerceAtLeast(topY + 1.5f * density)
                canvas.drawRect(xLeft, topY, xLeft + barW, botY, objectPaint)
            }
        }
        "winloss" -> {
            val midY = rect.centerY()
            val slotW = plotW / values.size.coerceAtLeast(1)
            val barW = (slotW * 0.68f).coerceAtLeast(2f * density)
            val halfH = plotH * 0.42f
            objectPaint.style = Paint.Style.FILL
            for (i in values.indices) {
                val v = values[i]
                if (abs(v) < 1e-6f) continue
                val xLeft = rect.left + padX + i * slotW + (slotW - barW) * 0.5f
                if (v > 0f) {
                    objectPaint.color = strokeArgb
                    canvas.drawRect(xLeft, midY - halfH, xLeft + barW, midY - 1f, objectPaint)
                } else {
                    objectPaint.color = 0xFFDC2626.toInt()
                    canvas.drawRect(xLeft, midY + 1f, xLeft + barW, midY + halfH, objectPaint)
                }
            }
        }
        else -> {
            if (values.size == 1) {
                objectPaint.style = Paint.Style.FILL
                objectPaint.color = strokeArgb
                canvas.drawCircle(rect.centerX(), rect.centerY(), 2.5f * density * zoomScale, objectPaint)
                return
            }
            val minV = values.minOrNull() ?: 0f
            val maxV = values.maxOrNull() ?: 1f
            val span = (maxV - minV).coerceAtLeast(1f)
            objectPath.reset()
            var lastX = rect.left + padX
            var lastY = rect.centerY()
            for (i in values.indices) {
                val px = rect.left + padX + (i.toFloat() / (values.size - 1)) * plotW
                val py = rect.bottom - padY - ((values[i] - minV) / span) * plotH
                if (i == 0) objectPath.moveTo(px, py) else objectPath.lineTo(px, py)
                lastX = px
                lastY = py
            }
            objectPaint.style = Paint.Style.STROKE
            objectPaint.strokeWidth = 1.7f * density * zoomScale
            objectPaint.color = strokeArgb
            canvas.drawPath(objectPath, objectPaint)
            objectPaint.style = Paint.Style.FILL
            if (spark.markers) {
                for (i in values.indices) {
                    val px = rect.left + padX + (i.toFloat() / (values.size - 1)) * plotW
                    val py = rect.bottom - padY - ((values[i] - minV) / span) * plotH
                    canvas.drawCircle(px, py, 2.0f * density * zoomScale, objectPaint)
                }
            }
            objectPaint.color = 0xFFDC2626.toInt()
            canvas.drawCircle(lastX, lastY, 2.3f * density * zoomScale, objectPaint)
        }
    }
}

/**
 * [EN] Draws an inline trend polyline with min/max endpoint markers inside `rect` when a cell carries a sparkline array.
 * [JA] 数値配列から最小・最大スケーリングを行い、セル矩形 `rect` 内に折れ線トレンドと終端マーカーを描画します。
 */
internal fun MasuSheetNativeGridView.drawInCellSparkline(canvas: Canvas, rect: RectF, values: FloatArray) {
    if (values.size < 2) return
    val minV = values.minOrNull() ?: 0f
    val maxV = values.maxOrNull() ?: 1f
    val span = (maxV - minV).coerceAtLeast(1f)
    val padX = 7f * density
    val padY = 5f * density
    val plotW = (rect.width() - padX * 2f).coerceAtLeast(8f)
    val plotH = (rect.height() - padY * 2f).coerceAtLeast(6f)

    objectPath.reset()
    var lastX = rect.left + padX
    var lastY = rect.centerY()
    for (i in values.indices) {
        val px = rect.left + padX + (i.toFloat() / (values.size - 1)) * plotW
        val py = rect.bottom - padY - ((values[i] - minV) / span) * plotH
        if (i == 0) objectPath.moveTo(px, py) else objectPath.lineTo(px, py)
        lastX = px
        lastY = py
    }
    objectPaint.style = Paint.Style.STROKE
    objectPaint.strokeWidth = 1.6f * density * zoomScale
    objectPaint.color = 0xFF2563EB.toInt()
    canvas.drawPath(objectPath, objectPaint)

    objectPaint.style = Paint.Style.FILL
    objectPaint.color = 0xFFDC2626.toInt()
    canvas.drawCircle(lastX, lastY, 2.3f * density * zoomScale, objectPaint)
}

/**
 * [EN] Pure helper that adapts bright neutral and soft pastel cell fills for Dark Mode while keeping vivid accents and Light Mode colors intact.
 * [JA] ダークモード時に白系・淡いパステル系のセル背景を落ち着いたダーク配色へ変換し、鮮やかな強調色やライトモード時はそのまま保持します。
 */
internal fun computeAdjustedCellBgForTheme(argb: Int, darkTheme: Boolean): Int {
    if (!darkTheme || argb == 0) return argb
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    val lum = (r * 299 + g * 587 + b * 114) / 1000
    val maxCh = maxOf(r, g, b)
    val minCh = minOf(r, g, b)
    val chroma = maxCh - minCh
    return when {
        // Near-white / neutral grays (#FFFFFF, #F8FAFC, #F1F5F9, #E7E6E6): map to subtle elevated dark surfaces
        lum >= 220 && chroma < 24 -> {
            val shadeStep = (255 - lum).coerceIn(0, 35)
            val dr = (24 + shadeStep * 0.7f).roundToInt().coerceIn(20, 64)
            val dg = (32 + shadeStep * 0.8f).roundToInt().coerceIn(26, 74)
            val db = (40 + shadeStep * 0.9f).roundToInt().coerceIn(32, 86)
            (0xFF shl 24) or (dr shl 16) or (dg shl 8) or db
        }
        // Soft pastel table/pivot/banner tints (#D9E1F2, #EDF2FA, #ECFDF5, #CCFBF1): keep hue, lower glare
        lum >= 195 && chroma < 85 -> {
            val dr = (r * 0.16f + (r - minCh) * 0.35f + 14f).roundToInt().coerceIn(20, 95)
            val dg = (g * 0.16f + (g - minCh) * 0.35f + 16f).roundToInt().coerceIn(22, 100)
            val db = (b * 0.16f + (b - minCh) * 0.35f + 18f).roundToInt().coerceIn(24, 105)
            (0xFF shl 24) or (dr shl 16) or (dg shl 8) or db
        }
        else -> argb
    }
}

/**
 * [EN] Resolves a readable foreground text color against the cell's rendered background in both Light and Dark themes.
 * [JA] ライト／ダーク両テーマにおいて、描画されるセル背景輝度に対して十分なコントラストを持つ文字色を決定します。
 */
internal fun resolveCellTextColorForTheme(
    rawFgColor: Int,
    rawBgColor: Int,
    darkTheme: Boolean,
    sheetBg: Int,
    defaultTextCol: Int
): Int {
    val renderedBg = if (rawBgColor != 0) computeAdjustedCellBgForTheme(rawBgColor, darkTheme) else sheetBg
    val bgR = (renderedBg ushr 16) and 0xFF
    val bgG = (renderedBg ushr 8) and 0xFF
    val bgB = renderedBg and 0xFF
    val bgLum = (bgR * 299 + bgG * 587 + bgB * 114) / 1000

    if (rawFgColor == 0) {
        return when {
            darkTheme && bgLum > 155 -> 0xFF0F172A.toInt()
            !darkTheme && rawBgColor != 0 && bgLum < 75 -> 0xFFFFFFFF.toInt()
            else -> defaultTextCol
        }
    }
    if (!darkTheme) return rawFgColor

    val fgR = (rawFgColor ushr 16) and 0xFF
    val fgG = (rawFgColor ushr 8) and 0xFF
    val fgB = rawFgColor and 0xFF
    val fgLum = (fgR * 299 + fgG * 587 + fgB * 114) / 1000
    val fgChroma = maxOf(fgR, fgG, fgB) - minOf(fgR, fgG, fgB)

    return when {
        bgLum < 110 && fgLum < 105 -> {
            if (fgChroma < 48) {
                defaultTextCol
            } else {
                val lr = (fgR + (255 - fgR) * 0.58f).roundToInt().coerceIn(0, 255)
                val lg = (fgG + (255 - fgG) * 0.58f).roundToInt().coerceIn(0, 255)
                val lb = (fgB + (255 - fgB) * 0.58f).roundToInt().coerceIn(0, 255)
                (0xFF shl 24) or (lr shl 16) or (lg shl 8) or lb
            }
        }
        bgLum > 165 && fgLum > 185 -> 0xFF0F172A.toInt()
        else -> rawFgColor
    }
}

/**
 * [EN] Softens bright pastel cell fill colors in Dark Mode while preserving saturated accent fills and Light Mode colors verbatim.
 * [JA] ダークモード表示時に淡いパステル背景色の輝度を抑えて白文字の視認性を確保し、ライトモード時は元の色をそのまま返します。
 */
internal fun MasuSheetNativeGridView.adjustCellBgForTheme(argb: Int, darkTheme: Boolean): Int =
    computeAdjustedCellBgForTheme(argb, darkTheme)

/**
 * [EN] Draws the top, right, bottom, and left borders of `cell` around `rect` when encoded border metadata is non-zero.
 * [JA] セルに上下左右の罫線情報が設定されている場合、`rect` の各辺に線幅と色を反映した罫線を描画します。
 */
internal fun MasuSheetNativeGridView.drawCellBorders(canvas: Canvas, rect: RectF, cell: NativeCellModel) {
    if (cell.borderTop != 0) {
        applyBorderPaint(cell.borderTop)
        canvas.drawLine(rect.left, rect.top, rect.right, rect.top, borderPaint)
    }
    if (cell.borderRight != 0) {
        applyBorderPaint(cell.borderRight)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.bottom, borderPaint)
    }
    if (cell.borderBottom != 0) {
        applyBorderPaint(cell.borderBottom)
        canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, borderPaint)
    }
    if (cell.borderLeft != 0) {
        applyBorderPaint(cell.borderLeft)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.bottom, borderPaint)
    }
}

/**
 * [EN] Decodes MasuSheet's packed 32-bit border descriptor (`[halfWidthPt:8 | rgb:24]`) onto `borderPaint`.
 * [JA] 上位8bitに線幅（0.5pt単位）、下位24bitにRGB色を格納した罫線エンコード値をデコードし、`borderPaint` に適用します。
 */
internal fun MasuSheetNativeGridView.applyBorderPaint(encoded: Int) {
    val halfWidth = ((encoded ushr 24) and 0xFF).coerceAtLeast(1)
    val rgb = encoded and 0xFFFFFF
    borderPaint.strokeWidth = (halfWidth * 0.5f) * density
    borderPaint.color = (0xFF shl 24) or rgb
}

/**
 * [EN] Splits `text` into visual lines that fit within `maxWidthPx` using `paint` (with character-level fallback for CJK text and JVM unit tests).
 * [JA] `paint` の計測幅に基づき、日本語等の連続文字列や改行を含めて `maxWidthPx` 内に収まる視覚行リストへ折り返し分割します。
 */
fun MasuSheetNativeGridView.wrapTextIntoLines(text: String, maxWidthPx: Float, paint: Paint): List<String> {
    if (text.isEmpty()) return listOf("")
    val limitPx = maxWidthPx.coerceAtLeast(8f)
    val fontSizePx = paint.textSize.coerceAtLeast(11f)
    fun measureWidth(s: String): Float {
        val m = paint.measureText(s)
        if (m >= s.length * fontSizePx * 0.35f) return m
        var w = 0f
        for (ch in s) {
            w += if (ch.code > 0x7F) fontSizePx * 1.0f else fontSizePx * 0.56f
        }
        return w
    }
    val result = mutableListOf<String>()
    val paragraphs = text.split('\n')
    for (para in paragraphs) {
        if (para.isEmpty()) {
            result.add("")
            continue
        }
        if (measureWidth(para) <= limitPx) {
            result.add(para)
            continue
        }
        val sb = StringBuilder()
        for (ch in para) {
            sb.append(ch)
            if (sb.length > 1 && measureWidth(sb.toString()) > limitPx) {
                val lastChar = sb.last()
                sb.setLength(sb.length - 1)
                result.add(sb.toString())
                sb.setLength(0)
                sb.append(lastChar)
            }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString())
        }
    }
    return if (result.isEmpty()) listOf(text) else result
}

/**
 * [EN] Draws a cell's formatted text (or raw formula when `showFormulas` is active), inline checkboxes, alignment, font style, and validation `[▾]` indicator inside `rect`.
 * [JA] セルのテキスト（数式表示モード時は生数式）、セル内チェックボックス、文字揃え、太字／斜体／取消線、入力規則バッジを `rect` 内に描画します。
 */
internal fun MasuSheetNativeGridView.drawCellContent(canvas: Canvas, rect: RectF, cell: NativeCellModel, defaultTextCol: Int) {
    val text = if (showFormulas && cell.editText.isNotEmpty()) cell.editText else cell.displayText
    val sheetBg = if (isDarkTheme) 0xFF14181D.toInt() else 0xFFFFFFFF.toInt()
    val fgColor = resolveCellTextColorForTheme(
        rawFgColor = cell.fgColor,
        rawBgColor = cell.bgColor,
        darkTheme = isDarkTheme,
        sheetBg = sheetBg,
        defaultTextCol = defaultTextCol
    )

    textPaint.color = fgColor
    textPaint.textSize = (cell.fontSize.coerceIn(8f, 28f)) * density * zoomScale
    val styleBits = when {
        cell.isBold && cell.isItalic -> Typeface.BOLD_ITALIC
        cell.isBold -> Typeface.BOLD
        cell.isItalic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    textPaint.typeface = Typeface.create(Typeface.DEFAULT, styleBits)
    textPaint.isUnderlineText = cell.isUnderline
    textPaint.isStrikeThruText = cell.isStrike

    val padX = 5f * density
    val rightPadX = if (viewModel.isTableHeaderCell(currentViewport, cell.row, cell.col)) {
        padX + 16f * density * zoomScale
    } else {
        padX
    }
    val fm = textPaint.fontMetrics

    val save = canvas.save()
    canvas.clipRect(rect.left + 1f, rect.top + 1f, rect.right - 1f, rect.bottom - 1f)

    if (cell.isWrap || text.contains('\n')) {
        val availWidthPx = (rect.width() - padX - rightPadX).coerceAtLeast(10f)
        val lines = wrapTextIntoLines(text, availWidthPx, textPaint)
        val lineHeightPx = (fm.descent - fm.ascent + 2f * density).coerceAtLeast(11f * density)
        val totalBlockHeight = lines.size * lineHeightPx
        val firstLineTopY = when (cell.vAlign) {
            0 -> rect.top + 2.5f * density
            2 -> (rect.bottom - totalBlockHeight - 2.5f * density).coerceAtLeast(rect.top + 2f * density)
            else -> (rect.top + (rect.height() - totalBlockHeight) * 0.5f).coerceAtLeast(rect.top + 2f * density)
        }
        for ((idx, line) in lines.withIndex()) {
            val lineBaseline = firstLineTopY + idx * lineHeightPx - fm.ascent
            if (lineBaseline - lineHeightPx > rect.bottom) break
            if (InlineCheckboxHelper.containsCheckbox(line)) {
                drawInteractiveCheckboxLine(canvas, rect, line, lineBaseline, padX)
            } else {
                val measuredW = textPaint.measureText(line)
                val drawX = when (cell.hAlign) {
                    1 -> rect.left + ((rect.width() - rightPadX + padX - measuredW) * 0.5f).coerceAtLeast(padX)
                    2 -> (rect.right - rightPadX - measuredW).coerceAtLeast(rect.left + padX)
                    else -> rect.left + padX
                }
                canvas.drawText(line, drawX, lineBaseline, textPaint)
            }
        }
    } else {
        val textCenterOffset = (fm.descent + fm.ascent) * 0.5f
        val baselineY = when (cell.vAlign) {
            0 -> rect.top - fm.ascent + 3f * density
            2 -> rect.bottom - fm.descent - 3f * density
            else -> rect.centerY() - textCenterOffset
        }

        if (InlineCheckboxHelper.containsCheckbox(text)) {
            drawInteractiveCheckboxLine(canvas, rect, text, baselineY, padX)
        } else {
            val measuredW = textPaint.measureText(text)
            val drawX = when (cell.hAlign) {
                1 -> rect.left + ((rect.width() - rightPadX + padX - measuredW) * 0.5f).coerceAtLeast(padX)
                2 -> (rect.right - rightPadX - measuredW).coerceAtLeast(rect.left + padX)
                else -> rect.left + padX
            }
            canvas.drawText(text, drawX, baselineY, textPaint)
        }
    }

    textPaint.isUnderlineText = false
    textPaint.isStrikeThruText = false
    canvas.restoreToCount(save)
}

/**
 * [EN] Renders a line containing `☐` / `☑` vector checkbox glyphs alongside label text so individual checkboxes can be tapped in-cell.
 * [JA] `☐`／`☑` を含むテキスト行をベクターチェックボックスアイコンとラベル文字に分解してセル内へ描画します。
 */
internal fun MasuSheetNativeGridView.drawInteractiveCheckboxLine(
    canvas: Canvas,
    rect: RectF,
    text: String,
    baselineY: Float,
    padX: Float
) {
    var curX = rect.left + padX
    val boxSize = (13.5f * density * zoomScale).coerceIn(11f * density, 22f * density)
    val fm = textPaint.fontMetrics
    val lineCenterY = baselineY + (fm.ascent + fm.descent) * 0.5f
    val boxTop = lineCenterY - boxSize * 0.5f
    val cornerR = 3f * density
    val sb = StringBuilder()

    fun flushRun() {
        if (sb.isNotEmpty()) {
            val seg = sb.toString()
            canvas.drawText(seg, curX, baselineY, textPaint)
            curX += textPaint.measureText(seg)
            sb.setLength(0)
        }
    }

    for (ch in text) {
        if (InlineCheckboxHelper.isCheckboxChar(ch)) {
            flushRun()
            val checked = InlineCheckboxHelper.isCheckedBox(ch)
            val boxRect = RectF(curX, boxTop, curX + boxSize, boxTop + boxSize)
            if (checked) {
                checkboxBoxPaint.style = Paint.Style.FILL
                checkboxBoxPaint.color = 0xFF1F9D55.toInt()
                canvas.drawRoundRect(boxRect, cornerR, cornerR, checkboxBoxPaint)

                checkboxCheckPaint.strokeWidth = (1.8f * density).coerceAtLeast(1.5f)
                val x1 = boxRect.left + boxSize * 0.22f
                val y1 = boxRect.top + boxSize * 0.52f
                val x2 = boxRect.left + boxSize * 0.44f
                val y2 = boxRect.top + boxSize * 0.74f
                val x3 = boxRect.left + boxSize * 0.78f
                val y3 = boxRect.top + boxSize * 0.28f
                canvas.drawLine(x1, y1, x2, y2, checkboxCheckPaint)
                canvas.drawLine(x2, y2, x3, y3, checkboxCheckPaint)
            } else {
                checkboxBoxPaint.style = Paint.Style.FILL
                checkboxBoxPaint.color = if (isDarkTheme) 0xFF1E293B.toInt() else 0xFFF8FAFC.toInt()
                canvas.drawRoundRect(boxRect, cornerR, cornerR, checkboxBoxPaint)
                checkboxBoxPaint.style = Paint.Style.STROKE
                checkboxBoxPaint.strokeWidth = 1.5f * density
                checkboxBoxPaint.color = if (isDarkTheme) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
                canvas.drawRoundRect(boxRect, cornerR, cornerR, checkboxBoxPaint)
            }
            curX += boxSize + 4f * density
        } else {
            sb.append(ch)
        }
    }
    flushRun()
}

/**
 * [EN] Hit-tests which 0-based `☐` / `☑` checkbox glyph inside `(row, col)` lies under `screenX`, or returns `null` if the tap missed.
 * [JA] セル `(row, col)` 内の複数チェックボックスのうち、画面X座標 `screenX` でタップされたチェックボックスの0始まり番号を返します。
 */
internal fun MasuSheetNativeGridView.resolveClickedCheckboxIndex(row: Int, col: Int, screenX: Float): Int? {
    val snap = currentViewport
    val (anchorR, anchorC) = snap.resolveAnchorCell(row, col)
    val cell = snap.cellAt(anchorR, anchorC) ?: return null
    val text = cell.displayText
    if (!InlineCheckboxHelper.containsCheckbox(text)) return null

    val rect = getCellScreenRect(anchorR, anchorC)
    textPaint.textSize = (cell.fontSize.coerceIn(8f, 28f)) * density * zoomScale
    val boxSize = (13.5f * density * zoomScale).coerceIn(11f * density, 22f * density)
    var curX = rect.left + 5f * density
    var boxIdx = 0
    val hitRanges = mutableListOf<Triple<Float, Float, Int>>()
    val sb = StringBuilder()

    for (ch in text) {
        if (InlineCheckboxHelper.isCheckboxChar(ch)) {
            if (sb.isNotEmpty()) {
                curX += textPaint.measureText(sb.toString())
                sb.setLength(0)
            }
            val startX = curX - 6f * density
            val endX = curX + boxSize + 8f * density
            hitRanges.add(Triple(startX, endX, boxIdx))
            curX += boxSize + 4f * density
            boxIdx++
        } else {
            sb.append(ch)
        }
    }
    for ((startX, endX, idx) in hitRanges) {
        if (screenX in startX..endX) {
            return idx
        }
    }
    return null
}

/**
 * [EN] Renders Layer 2 (selection border, AutoFill handle, spill ghost frame, 8-color formula highlights, row/column headers, freeze dividers, and filter/comment badges).
 * [JA] レイヤー2（選択枠、オートフィルハンドル、スピル枠、8色数式参照ハイライト、行列ヘッダ、固定枠線、フィルター／コメント印）を描画します。
 */
internal fun MasuSheetNativeGridView.renderSelectionAndHeadersOverlay(canvas: Canvas, w: Int, h: Int) {
    val snap = currentViewport
    val headerBg = if (isDarkTheme) 0xFF1E242B.toInt() else 0xFFF1F5F9.toInt()
    val headerBorder = if (isDarkTheme) 0xFF334155.toInt() else 0xFFCBD5E1.toInt()
    val headerTextCol = if (isDarkTheme) 0xFFCBD5E1.toInt() else 0xFF334155.toInt()
    val activeHeaderTextCol = 0xFF1F9D55.toInt()

    // 1. Draw Active Selection Rectangle & Inserted Sheet Objects in Sheet Area
    val selSave = canvas.save()
    canvas.clipRect(rowHeaderWidthPx, colHeaderHeightPx, w.toFloat(), h.toFloat())

    val r0 = min(snap.selStartRow, snap.selEndRow).coerceIn(0, snap.rowHeights.size - 1)
    val c0 = min(snap.selStartCol, snap.selEndCol).coerceIn(0, snap.colWidths.size - 1)
    val r1 = max(snap.selStartRow, snap.selEndRow).coerceIn(r0, snap.rowHeights.size - 1)
    val c1 = max(snap.selStartCol, snap.selEndCol).coerceIn(c0, snap.colWidths.size - 1)

    val selLeft = rowHeaderWidthPx + ptToPx(snap.colOffsets[c0]) - scrollXPx
    val selTop = colHeaderHeightPx + ptToPx(snap.rowOffsets[r0]) - scrollYPx
    val selRight = rowHeaderWidthPx + ptToPx(snap.colOffsets[c1 + 1]) - scrollXPx
    val selBottom = colHeaderHeightPx + ptToPx(snap.rowOffsets[r1 + 1]) - scrollYPx

    // 1a. Page Break Preview / Page Layout Guides
    if (viewMode == "pageBreakPreview" || viewMode == "pageLayout") {
        val pageBreakCol = 7.coerceAtMost(snap.colOffsets.size - 1)
        val pageBreakRow = 25.coerceAtMost(snap.rowOffsets.size - 1)
        val pbX = rowHeaderWidthPx + ptToPx(snap.colOffsets[pageBreakCol]) - scrollXPx
        val pbY = colHeaderHeightPx + ptToPx(snap.rowOffsets[pageBreakRow]) - scrollYPx
        objectPaint.style = Paint.Style.STROKE
        objectPaint.strokeWidth = 2.0f * density
        objectPaint.color = if (viewMode == "pageBreakPreview") 0xFF3B82F6.toInt() else 0xFF10B981.toInt()
        canvas.drawLine(pbX, colHeaderHeightPx, pbX, h.toFloat(), objectPaint)
        canvas.drawLine(rowHeaderWidthPx, pbY, w.toFloat(), pbY, objectPaint)
        if (viewMode == "pageBreakPreview") {
            headerTextPaint.textSize = 22f * density
            headerTextPaint.color = 0x3894A3B8
            canvas.drawText("1 ページ", (rowHeaderWidthPx + pbX) * 0.5f, (colHeaderHeightPx + pbY) * 0.5f, headerTextPaint)
            headerTextPaint.textSize = 11.5f * density
        }
    }

    // 1b. Freeze Panes Divider Lines + A11 Draggable Freeze Handle Preview
    val displayFreezeRow = if (isDraggingFreezeRowHandle) freezePreviewRow else freezeRows
    if (displayFreezeRow > 0 && displayFreezeRow < snap.rowOffsets.size) {
        val freezeY = if (isDraggingFreezeRowHandle) {
            colHeaderHeightPx + ptToPx(snap.rowOffsets[displayFreezeRow]) - scrollYPx
        } else {
            colHeaderHeightPx + ptToPx(snap.rowOffsets[displayFreezeRow])
        }
        objectPaint.style = Paint.Style.STROKE
        objectPaint.strokeWidth = (if (isDraggingFreezeRowHandle) 3.4f else 2.4f) * density
        objectPaint.color = if (isDraggingFreezeRowHandle) 0xFF0EA5E9.toInt() else 0xFF10B981.toInt()
        canvas.drawLine(rowHeaderWidthPx, freezeY, w.toFloat(), freezeY, objectPaint)
    }
    val displayFreezeCol = if (isDraggingFreezeColHandle) freezePreviewCol else freezeCols
    if (displayFreezeCol > 0 && displayFreezeCol < snap.colOffsets.size) {
        val freezeX = if (isDraggingFreezeColHandle) {
            rowHeaderWidthPx + ptToPx(snap.colOffsets[displayFreezeCol]) - scrollXPx
        } else {
            rowHeaderWidthPx + ptToPx(snap.colOffsets[displayFreezeCol])
        }
        objectPaint.style = Paint.Style.STROKE
        objectPaint.strokeWidth = (if (isDraggingFreezeColHandle) 3.4f else 2.4f) * density
        objectPaint.color = if (isDraggingFreezeColHandle) 0xFF0EA5E9.toInt() else 0xFF10B981.toInt()
        canvas.drawLine(freezeX, colHeaderHeightPx, freezeX, h.toFloat(), objectPaint)
    }

    // 1b-spill (A6). Dynamic Array Spill Range Blue Ghost Border (`#2563EB`, dynamic array spill range indicator)
    if (snap.spillRanges.isNotEmpty()) {
        val maxR = (snap.rowHeights.size - 1).coerceAtLeast(0)
        val maxC = (snap.colWidths.size - 1).coerceAtLeast(0)
        for (spill in snap.spillRanges) {
            val sr0 = spill.startRow.coerceIn(0, maxR)
            val sc0 = spill.startCol.coerceIn(0, maxC)
            val sr1 = spill.endRow.coerceIn(sr0, maxR)
            val sc1 = spill.endCol.coerceIn(sc0, maxC)
            val spLeft = rowHeaderWidthPx + ptToPx(snap.colOffsets[sc0]) - scrollXPx
            val spTop = colHeaderHeightPx + ptToPx(snap.rowOffsets[sr0]) - scrollYPx
            val spRight = rowHeaderWidthPx + ptToPx(snap.colOffsets[sc1 + 1]) - scrollXPx
            val spBottom = colHeaderHeightPx + ptToPx(snap.rowOffsets[sr1 + 1]) - scrollYPx
            if (spRight < rowHeaderWidthPx || spLeft > w || spBottom < colHeaderHeightPx || spTop > h) continue
            checkboxBoxPaint.style = Paint.Style.FILL
            checkboxBoxPaint.color = 0x142563EB
            canvas.drawRect(spLeft, spTop, spRight, spBottom, checkboxBoxPaint)
            objectPaint.style = Paint.Style.STROKE
            objectPaint.strokeWidth = 1.9f * density
            objectPaint.color = 0xFF2563EB.toInt()
            canvas.drawRect(spLeft, spTop, spRight, spBottom, objectPaint)
        }
    }

    if (r0 != r1 || c0 != c1) {
        canvas.drawRect(selLeft, selTop, selRight, selBottom, selectionFillPaint)
    }
    canvas.drawRect(selLeft, selTop, selRight, selBottom, selectionBorderPaint)

    // [EN] Draw circular top-left range-adjustment handle on multi-cell selections for intuitive touch resizing
    // [JA] 複数セル選択時、タッチでも直感的に左上角から選択範囲を調整できる丸型ハンドルを描画
    if ((r0 != r1 || c0 != c1) && !isDraggingFillHandle && selectedObjectId == null) {
        val tlRadius = 5.0f * density
        checkboxBoxPaint.style = Paint.Style.FILL
        checkboxBoxPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(selLeft, selTop, tlRadius + 1.5f * density, checkboxBoxPaint)
        canvas.drawCircle(selLeft, selTop, tlRadius, handlePaint)
    }

    // 1b-move (A7). Drag Selection Border Move Preview Rectangle (`Drag::Move`, `interact.rs:457-459`)
    if (isDraggingSelectionMove) {
        val rowSpan = (moveSrcEndRow - moveSrcStartRow).coerceAtLeast(0)
        val colSpan = (moveSrcEndCol - moveSrcStartCol).coerceAtLeast(0)
        val maxR = (snap.rowHeights.size - 1).coerceAtLeast(0)
        val maxC = (snap.colWidths.size - 1).coerceAtLeast(0)
        val mr0 = moveTargetStartRow.coerceIn(0, maxR)
        val mc0 = moveTargetStartCol.coerceIn(0, maxC)
        val mr1 = (mr0 + rowSpan).coerceIn(mr0, maxR)
        val mc1 = (mc0 + colSpan).coerceIn(mc0, maxC)
        val mLeft = rowHeaderWidthPx + ptToPx(snap.colOffsets[mc0]) - scrollXPx
        val mTop = colHeaderHeightPx + ptToPx(snap.rowOffsets[mr0]) - scrollYPx
        val mRight = rowHeaderWidthPx + ptToPx(snap.colOffsets[mc1 + 1]) - scrollXPx
        val mBottom = colHeaderHeightPx + ptToPx(snap.rowOffsets[mr1 + 1]) - scrollYPx
        checkboxBoxPaint.style = Paint.Style.FILL
        checkboxBoxPaint.color = 0x280EA5E9
        canvas.drawRect(mLeft, mTop, mRight, mBottom, checkboxBoxPaint)
        objectPaint.style = Paint.Style.STROKE
        objectPaint.strokeWidth = 2.6f * density
        objectPaint.color = 0xFF0EA5E9.toInt()
        canvas.drawRect(mLeft, mTop, mRight, mBottom, objectPaint)
    }

    // 1b-colmove (A8). Drag Column Header Reorder Vertical Insertion Line (`Drag::MoveCols`, `interact.rs:316-325`)
    if (isDraggingColMove) {
        val targetC = colMoveTargetBeforeCol.coerceIn(0, snap.colOffsets.size - 1)
        val lineX = rowHeaderWidthPx + ptToPx(snap.colOffsets[targetC]) - scrollXPx
        objectPaint.style = Paint.Style.STROKE
        objectPaint.strokeWidth = 3.2f * density
        objectPaint.color = 0xFF0EA5E9.toInt()
        canvas.drawLine(lineX, colHeaderHeightPx, lineX, h.toFloat(), objectPaint)
    }

    // 1b-validation (A10). Active Cell Data Validation `[▾]` Dropdown Button & Input Message Tooltip (`validation_arrow`)
    val activeValidation = viewModel.getActiveCellValidation()
    if (activeValidation != null && !container.isEditingInCell) {
        val activeRect = getCellScreenRect(snap.activeRow, snap.activeCol)
        if (activeValidation.kind.equals("list", ignoreCase = true) &&
            activeValidation.inCellDropdown &&
            activeValidation.items.isNotEmpty()
        ) {
            val btnW = 18f * density
            val btnH = activeRect.height().coerceIn(16f * density, 24f * density)
            val btnLeft = (activeRect.right + 1.5f * density).coerceAtMost(w - btnW - 2f * density)
            val btnTop = activeRect.centerY() - btnH * 0.5f
            val btnRect = RectF(btnLeft, btnTop, btnLeft + btnW, btnTop + btnH)
            checkboxBoxPaint.style = Paint.Style.FILL
            checkboxBoxPaint.color = if (isDarkTheme) 0xFF1E293B.toInt() else 0xFFF8FAFC.toInt()
            canvas.drawRoundRect(btnRect, 3.5f * density, 3.5f * density, checkboxBoxPaint)
            objectPaint.style = Paint.Style.STROKE
            objectPaint.strokeWidth = 1.3f * density
            objectPaint.color = 0xFF10B981.toInt()
            canvas.drawRoundRect(btnRect, 3.5f * density, 3.5f * density, objectPaint)
            headerTextPaint.textSize = 10.5f * density
            headerTextPaint.color = 0xFF10B981.toInt()
            canvas.drawText("▾", btnRect.centerX(), btnRect.centerY() + 3.5f * density, headerTextPaint)
            headerTextPaint.textSize = 11.5f * density
        }
        if (activeValidation.showInput &&
            (activeValidation.inputTitle.isNotBlank() || activeValidation.inputMessage.isNotBlank())
        ) {
            val tipText = buildString {
                if (activeValidation.inputTitle.isNotBlank()) {
                    append(activeValidation.inputTitle)
                    if (activeValidation.inputMessage.isNotBlank()) append(": ")
                }
                append(activeValidation.inputMessage)
            }
            textPaint.textSize = 10.5f * density
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val tw = textPaint.measureText(tipText) + 14f * density
            val th = 20f * density
            val tx = activeRect.left.coerceIn(rowHeaderWidthPx + 4f * density, (w - tw - 4f * density).coerceAtLeast(rowHeaderWidthPx + 4f * density))
            val ty = (activeRect.bottom + 4f * density).coerceAtMost(h - th - 4f * density)
            val tipRect = RectF(tx, ty, tx + tw, ty + th)
            checkboxBoxPaint.style = Paint.Style.FILL
            checkboxBoxPaint.color = if (isDarkTheme) 0xEE1E293B.toInt() else 0xEEFEF9C3.toInt()
            canvas.drawRoundRect(tipRect, 4f * density, 4f * density, checkboxBoxPaint)
            objectPaint.style = Paint.Style.STROKE
            objectPaint.strokeWidth = 1.1f * density
            objectPaint.color = 0xFFF59E0B.toInt()
            canvas.drawRoundRect(tipRect, 4f * density, 4f * density, objectPaint)
            textPaint.color = if (isDarkTheme) 0xFFFDE68A.toInt() else 0xFF78350F.toInt()
            val tfm = textPaint.fontMetrics
            canvas.drawText(tipText, tipRect.left + 7f * density, tipRect.centerY() - (tfm.descent + tfm.ascent) * 0.5f, textPaint)
        }
    }

    // 1b-ref. 8-Color Formula Reference Highlights on Grid
    if (container.isEditingFormulaInCell()) {
        val maxR = (snap.rowHeights.size - 1).coerceAtLeast(0)
        val maxC = (snap.colWidths.size - 1).coerceAtLeast(0)
        val curSheetName = snap.sheetNames.getOrNull(snap.sheetIndex).orEmpty()
        val refs = FormulaPointModeHelper.extractFormulaReferences(
            text = container.currentEditorText(),
            maxRowIndex = maxR,
            maxColIndex = maxC
        )
        val cornerR = 3.2f * density
        for (ref in refs) {
            val matchesSheet = if (ref.sheetName == null) {
                snap.sheetIndex == container.editingSheetIndex || container.editingSheetIndex < 0
            } else {
                curSheetName.equals(ref.sheetName, ignoreCase = true)
            }
            if (!matchesSheet) continue
            val rr0 = ref.startRow.coerceIn(0, maxR)
            val cc0 = ref.startCol.coerceIn(0, maxC)
            val rr1 = ref.endRow.coerceIn(rr0, maxR)
            val cc1 = ref.endCol.coerceIn(cc0, maxC)
            val refL = rowHeaderWidthPx + ptToPx(snap.colOffsets[cc0]) - scrollXPx
            val refT = colHeaderHeightPx + ptToPx(snap.rowOffsets[rr0]) - scrollYPx
            val refR = rowHeaderWidthPx + ptToPx(snap.colOffsets[cc1 + 1]) - scrollXPx
            val refB = colHeaderHeightPx + ptToPx(snap.rowOffsets[rr1 + 1]) - scrollYPx
            if (refR < rowHeaderWidthPx || refL > w || refB < colHeaderHeightPx || refT > h) continue

            checkboxBoxPaint.style = Paint.Style.FILL
            checkboxBoxPaint.color = (ref.colorArgb and 0x00FFFFFF) or 0x1E000000
            canvas.drawRect(refL, refT, refR, refB, checkboxBoxPaint)

            objectPaint.style = Paint.Style.STROKE
            objectPaint.strokeWidth = 2.2f * density
            objectPaint.color = ref.colorArgb
            canvas.drawRect(refL, refT, refR, refB, objectPaint)

            checkboxBoxPaint.color = ref.colorArgb
            canvas.drawRect(refL - cornerR, refT - cornerR, refL + cornerR, refT + cornerR, checkboxBoxPaint)
            canvas.drawRect(refR - cornerR, refT - cornerR, refR + cornerR, refT + cornerR, checkboxBoxPaint)
            canvas.drawRect(refL - cornerR, refB - cornerR, refL + cornerR, refB + cornerR, checkboxBoxPaint)
            canvas.drawRect(refR - cornerR, refB - cornerR, refR + cornerR, refB + cornerR, checkboxBoxPaint)
        }
    }

    // 1c. Live AutoFill Target Preview Box + Ghost Overwrite Values + Floating Overwrite Warning Banner
    if (isDraggingFillHandle) {
        val previewR0 = min(fillSrcStartRow, fillTargetEndRow).coerceIn(0, snap.rowHeights.size - 1)
        val previewC0 = min(fillSrcStartCol, fillTargetEndCol).coerceIn(0, snap.colWidths.size - 1)
        val previewR1 = max(fillSrcEndRow, fillTargetEndRow).coerceIn(previewR0, snap.rowHeights.size - 1)
        val previewC1 = max(fillSrcEndCol, fillTargetEndCol).coerceIn(previewC0, snap.colWidths.size - 1)
        val pLeft = rowHeaderWidthPx + ptToPx(snap.colOffsets[previewC0]) - scrollXPx
        val pTop = colHeaderHeightPx + ptToPx(snap.rowOffsets[previewR0]) - scrollYPx
        val pRight = rowHeaderWidthPx + ptToPx(snap.colOffsets[previewC1 + 1]) - scrollXPx
        val pBottom = colHeaderHeightPx + ptToPx(snap.rowOffsets[previewR1 + 1]) - scrollYPx

        // Warm Amber/Orange tint & thick border so AutoFill is 100% distinct from green cell selection
        checkboxBoxPaint.style = Paint.Style.FILL
        checkboxBoxPaint.color = 0x36F59E0B
        canvas.drawRect(pLeft, pTop, pRight, pBottom, checkboxBoxPaint)
        objectPaint.style = Paint.Style.STROKE
        objectPaint.strokeWidth = 2.8f * density
        objectPaint.color = 0xFFF59E0B.toInt()
        canvas.drawRect(pLeft, pTop, pRight, pBottom, objectPaint)

        // Render live ghost preview values inside every target cell that will be overwritten
        val ghostCells = viewModel.previewAutoFillCells(
            srcStartRow = fillSrcStartRow,
            srcStartCol = fillSrcStartCol,
            srcEndRow = fillSrcEndRow,
            srcEndCol = fillSrcEndCol,
            targetEndRow = fillTargetEndRow,
            targetEndCol = fillTargetEndCol
        )
        textPaint.textSize = (10.5f * density * zoomScale).coerceIn(9f * density, 16f * density)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val fm = textPaint.fontMetrics
        for ((cellCoord, previewVal) in ghostCells) {
            val (tr, tc) = cellCoord
            val cRect = getCellScreenRect(tr, tc)
            if (cRect.right < rowHeaderWidthPx || cRect.bottom < colHeaderHeightPx || cRect.left > w || cRect.top > h) {
                continue
            }
            // Draw subtle amber cell highlight + "→ previewVal" badge
            checkboxBoxPaint.style = Paint.Style.FILL
            checkboxBoxPaint.color = if (isDarkTheme) 0xCC451A03.toInt() else 0xDDFFFBEB.toInt()
            canvas.drawRect(
                cRect.left + 1.5f * density,
                cRect.top + 1.5f * density,
                cRect.right - 1.5f * density,
                cRect.bottom - 1.5f * density,
                checkboxBoxPaint
            )
            textPaint.color = if (isDarkTheme) 0xFFFDE68A.toInt() else 0xFFB45309.toInt()
            val label = "→ ${previewVal.ifEmpty { "(空)" }}"
            val clipSave = canvas.save()
            canvas.clipRect(cRect.left + 2f * density, cRect.top + 1f, cRect.right - 2f * density, cRect.bottom - 1f)
            val by = cRect.centerY() - (fm.descent + fm.ascent) * 0.5f
            canvas.drawText(label, cRect.left + 5f * density, by, textPaint)
            canvas.restoreToCount(clipSave)
        }

        // Floating high-contrast AutoFill Overwrite Banner above (or below) the active fill range
        val isEn = viewModel.uiState.value.language == "en"
        val bannerText = if (ghostCells.isNotEmpty()) {
            if (isEn) {
                "⚡ AutoFill Active: Release to overwrite ${ghostCells.size} cells (Drag back to source to cancel)"
            } else {
                "⚡ オートフィル作動中: 指/クリックを離すと ${ghostCells.size} セルを上書き (元のセルへ戻すとキャンセル)"
            }
        } else {
            if (isEn) {
                "⚡ AutoFill Ready: Drag down/right to fill series (Release here = no overwrite)"
            } else {
                "⚡ オートフィル待機中: 下・右へドラッグで連続データ上書き (ここで離すと変更なし)"
            }
        }
        textPaint.textSize = 11.2f * density
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val bw = textPaint.measureText(bannerText) + 20f * density
        val bh = 24f * density
        val bx = pLeft.coerceIn(rowHeaderWidthPx + 6f * density, (w - bw - 6f * density).coerceAtLeast(rowHeaderWidthPx + 6f * density))
        val byTop = if (pTop - bh - 6f * density >= colHeaderHeightPx + 4f * density) {
            pTop - bh - 6f * density
        } else {
            (pBottom + 6f * density).coerceAtMost(h - bh - 6f * density)
        }
        val bannerRect = RectF(bx, byTop, bx + bw, byTop + bh)
        checkboxBoxPaint.style = Paint.Style.FILL
        checkboxBoxPaint.color = 0xEE78350F.toInt()
        canvas.drawRoundRect(bannerRect, 6f * density, 6f * density, checkboxBoxPaint)
        objectPaint.style = Paint.Style.STROKE
        objectPaint.strokeWidth = 1.6f * density
        objectPaint.color = 0xFFF59E0B.toInt()
        canvas.drawRoundRect(bannerRect, 6f * density, 6f * density, objectPaint)
        textPaint.color = 0xFFFFFBEB.toInt()
        val bfm = textPaint.fontMetrics
        canvas.drawText(
            bannerText,
            bannerRect.left + 10f * density,
            bannerRect.centerY() - (bfm.descent + bfm.ascent) * 0.5f,
            textPaint
        )
    }

    // Autofill corner square (amber & enlarged when actively dragging AutoFill; crisp green square otherwise)
    val hs = if (isDraggingFillHandle) 6.5f * density else 5.0f * density
    checkboxBoxPaint.style = Paint.Style.FILL
    checkboxBoxPaint.color = 0xFFFFFFFF.toInt()
    canvas.drawRect(
        selRight - hs - 1.4f * density,
        selBottom - hs - 1.4f * density,
        selRight + hs + 1.4f * density,
        selBottom + hs + 1.4f * density,
        checkboxBoxPaint
    )
    if (isDraggingFillHandle) {
        checkboxBoxPaint.color = 0xFFF59E0B.toInt()
        canvas.drawRect(selRight - hs, selBottom - hs, selRight + hs, selBottom + hs, checkboxBoxPaint)
    } else {
        canvas.drawRect(selRight - hs, selBottom - hs, selRight + hs, selBottom + hs, handlePaint)
    }

    // 1d. Draw Inserted Sheet Objects (Shapes, TextBoxes, Images, Charts)
    drawSheetObjectsOverlay(canvas, snap.sheetIndex)

    canvas.restoreToCount(selSave)

    // 2 & 3 & 5. Draw Pinned Column & Row Header Bars ONLY when showHeadings is ON
    if (showHeadings) {
        // 2. Draw Pinned Column Header Bar (A, B, C...) + Resize Grips + A12 Hidden Column Double-Line Indicator
        headerBgPaint.style = Paint.Style.FILL
        headerBgPaint.color = headerBg
        canvas.drawRect(rowHeaderWidthPx, 0f, w.toFloat(), colHeaderHeightPx, headerBgPaint)

        headerHighlightPaint.style = Paint.Style.FILL
        headerHighlightPaint.color = 0x2E1F9D55
        headerTextPaint.textSize = 11.5f * density
        gridlinePaint.color = headerBorder

        val fCols = freezeCols.coerceIn(0, snap.colWidths.size)
        val fRightPx = if (fCols > 0) (rowHeaderWidthPx + ptToPx(snap.colOffsets[fCols])).coerceAtMost(w.toFloat()) else rowHeaderWidthPx
        val startCol = max(fCols, snap.colAtX(pxToPt(scrollXPx.toFloat())).coerceAtLeast(0))
        val endCol = snap.colAtX(pxToPt((scrollXPx + w).toFloat())).coerceAtMost(snap.colWidths.size - 1)
        val colSave = canvas.save()
        canvas.clipRect(fRightPx, 0f, w.toFloat(), colHeaderHeightPx)
        for (c in startCol..endCol) {
            val left = rowHeaderWidthPx + ptToPx(snap.colOffsets[c]) - scrollXPx
            val right = rowHeaderWidthPx + ptToPx(snap.colOffsets[c + 1]) - scrollXPx
            // A12: Draw double-line indicator on the column header boundary if column `c` is hidden (`< 1f`)
            if (snap.colWidths[c] < 1f) {
                objectPaint.style = Paint.Style.STROKE
                objectPaint.strokeWidth = 1.6f * density
                objectPaint.color = 0xFF10B981.toInt()
                canvas.drawLine(left - 1.5f * density, 2f * density, left - 1.5f * density, colHeaderHeightPx - 2f * density, objectPaint)
                canvas.drawLine(left + 1.5f * density, 2f * density, left + 1.5f * density, colHeaderHeightPx - 2f * density, objectPaint)
                continue
            }
            if (right <= left) continue
            val isSelectedCol = c in c0..c1
            if (isSelectedCol) {
                canvas.drawRect(left, 0f, right, colHeaderHeightPx, headerHighlightPaint)
            }
            canvas.drawRect(left, 0f, right, colHeaderHeightPx, gridlinePaint)
            headerTextPaint.color = if (isSelectedCol) activeHeaderTextCol else headerTextCol
            val fm = headerTextPaint.fontMetrics
            val by = colHeaderHeightPx * 0.5f - (fm.descent + fm.ascent) * 0.5f
            canvas.drawText(MasuSheetConfig.formatColumnLabel(c), (left + right) * 0.5f, by, headerTextPaint)

            // Draw visual double-arrow indicator when hovering or resizing this column boundary
            if (c == hoverColDividerIndex || c == resizingColIndex) {
                canvas.drawLine(right, 0f, right, colHeaderHeightPx, resizeGuidePaint)
                headerTextPaint.color = activeHeaderTextCol
                canvas.drawText("↔", right, by, headerTextPaint)
            }
        }
        canvas.restoreToCount(colSave)

        if (fCols > 0 && fRightPx > rowHeaderWidthPx) {
            val fColSave = canvas.save()
            canvas.clipRect(rowHeaderWidthPx, 0f, fRightPx, colHeaderHeightPx)
            canvas.drawRect(rowHeaderWidthPx, 0f, fRightPx, colHeaderHeightPx, headerBgPaint)
            for (c in 0 until fCols) {
                val left = rowHeaderWidthPx + ptToPx(snap.colOffsets[c])
                val right = rowHeaderWidthPx + ptToPx(snap.colOffsets[c + 1])
                if (snap.colWidths[c] < 1f || right <= left) continue
                val isSelectedCol = c in c0..c1
                if (isSelectedCol) {
                    canvas.drawRect(left, 0f, right, colHeaderHeightPx, headerHighlightPaint)
                }
                canvas.drawRect(left, 0f, right, colHeaderHeightPx, gridlinePaint)
                headerTextPaint.color = if (isSelectedCol) activeHeaderTextCol else headerTextCol
                val fm = headerTextPaint.fontMetrics
                val by = colHeaderHeightPx * 0.5f - (fm.descent + fm.ascent) * 0.5f
                canvas.drawText(MasuSheetConfig.formatColumnLabel(c), (left + right) * 0.5f, by, headerTextPaint)
            }
            canvas.restoreToCount(fColSave)
        }

        // 3. Draw Pinned Row Header Bar (1, 2, 3...) + Resize Grips + A12 Hidden Row Double-Line Indicator
        canvas.drawRect(0f, colHeaderHeightPx, rowHeaderWidthPx, h.toFloat(), headerBgPaint)
        val fRows = freezeRows.coerceIn(0, snap.rowHeights.size)
        val fBottomPx = if (fRows > 0) (colHeaderHeightPx + ptToPx(snap.rowOffsets[fRows])).coerceAtMost(h.toFloat()) else colHeaderHeightPx
        val startRow = max(fRows, snap.rowAtY(pxToPt(scrollYPx.toFloat())).coerceAtLeast(0))
        val endRow = snap.rowAtY(pxToPt((scrollYPx + h).toFloat())).coerceAtMost(snap.rowHeights.size - 1)
        val rowSave = canvas.save()
        canvas.clipRect(0f, fBottomPx, rowHeaderWidthPx, h.toFloat())
        for (r in startRow..endRow) {
            val top = colHeaderHeightPx + ptToPx(snap.rowOffsets[r]) - scrollYPx
            val bot = colHeaderHeightPx + ptToPx(snap.rowOffsets[r + 1]) - scrollYPx
            // A12: Draw double-line indicator on the row header boundary if row `r` is hidden (`< 1f`)
            if (snap.rowHeights[r] < 1f) {
                objectPaint.style = Paint.Style.STROKE
                objectPaint.strokeWidth = 1.6f * density
                objectPaint.color = 0xFF10B981.toInt()
                canvas.drawLine(2f * density, top - 1.5f * density, rowHeaderWidthPx - 2f * density, top - 1.5f * density, objectPaint)
                canvas.drawLine(2f * density, top + 1.5f * density, rowHeaderWidthPx - 2f * density, top + 1.5f * density, objectPaint)
                continue
            }
            if (bot <= top) continue
            val isSelectedRow = r in r0..r1
            if (isSelectedRow) {
                canvas.drawRect(0f, top, rowHeaderWidthPx, bot, headerHighlightPaint)
            }
            canvas.drawRect(0f, top, rowHeaderWidthPx, bot, gridlinePaint)
            headerTextPaint.color = if (isSelectedRow) activeHeaderTextCol else headerTextCol
            val fm = headerTextPaint.fontMetrics
            val by = (top + bot) * 0.5f - (fm.descent + fm.ascent) * 0.5f
            canvas.drawText((r + 1).toString(), rowHeaderWidthPx * 0.5f, by, headerTextPaint)

            // Draw visual vertical double-arrow indicator when hovering or resizing this row boundary
            if (r == hoverRowDividerIndex || r == resizingRowIndex) {
                canvas.drawLine(0f, bot, rowHeaderWidthPx, bot, resizeGuidePaint)
                headerTextPaint.color = activeHeaderTextCol
                canvas.drawText("↕", rowHeaderWidthPx - 8f * density, bot, headerTextPaint)
            }
        }
        canvas.restoreToCount(rowSave)

        if (fRows > 0 && fBottomPx > colHeaderHeightPx) {
            val fRowSave = canvas.save()
            canvas.clipRect(0f, colHeaderHeightPx, rowHeaderWidthPx, fBottomPx)
            canvas.drawRect(0f, colHeaderHeightPx, rowHeaderWidthPx, fBottomPx, headerBgPaint)
            for (r in 0 until fRows) {
                val top = colHeaderHeightPx + ptToPx(snap.rowOffsets[r])
                val bot = colHeaderHeightPx + ptToPx(snap.rowOffsets[r + 1])
                if (snap.rowHeights[r] < 1f || bot <= top) continue
                val isSelectedRow = r in r0..r1
                if (isSelectedRow) {
                    canvas.drawRect(0f, top, rowHeaderWidthPx, bot, headerHighlightPaint)
                }
                canvas.drawRect(0f, top, rowHeaderWidthPx, bot, gridlinePaint)
                headerTextPaint.color = if (isSelectedRow) activeHeaderTextCol else headerTextCol
                val fm = headerTextPaint.fontMetrics
                val by = (top + bot) * 0.5f - (fm.descent + fm.ascent) * 0.5f
                canvas.drawText((r + 1).toString(), rowHeaderWidthPx * 0.5f, by, headerTextPaint)
            }
            canvas.restoreToCount(fRowSave)
        }

        // 4. Draw full-sheet guide line while actively dragging a column or row boundary
        if (resizingColIndex in snap.colWidths.indices) {
            val guideX = rowHeaderWidthPx + ptToPx(snap.colOffsets[resizingColIndex + 1]) - scrollXPx
            canvas.drawLine(guideX, 0f, guideX, h.toFloat(), resizeGuidePaint)
        }
        if (resizingRowIndex in snap.rowHeights.indices) {
            val guideY = colHeaderHeightPx + ptToPx(snap.rowOffsets[resizingRowIndex + 1]) - scrollYPx
            canvas.drawLine(0f, guideY, w.toFloat(), guideY, resizeGuidePaint)
        }

        // 5. Top-left Corner Select-All Box (`◢` — click to select entire sheet)
        canvas.drawRect(0f, 0f, rowHeaderWidthPx, colHeaderHeightPx, headerBgPaint)
        canvas.drawRect(0f, 0f, rowHeaderWidthPx, colHeaderHeightPx, gridlinePaint)
        headerTextPaint.color = activeHeaderTextCol
        headerTextPaint.textSize = 10.5f * density
        val cornerFm = headerTextPaint.fontMetrics
        val cornerBy = colHeaderHeightPx * 0.5f - (cornerFm.descent + cornerFm.ascent) * 0.5f
        canvas.drawText("◢", rowHeaderWidthPx * 0.5f, cornerBy, headerTextPaint)
        headerTextPaint.textSize = 11.5f * density
    }

    // 6. Lightweight Scrollbar Indicators
    val maxSx = maxScrollXPx()
    if (maxSx > 0) {
        val trackW = (w - rowHeaderWidthPx).coerceAtLeast(1f)
        val thumbW = (trackW * 0.25f).coerceAtLeast(36f * density)
        val thumbLeft = rowHeaderWidthPx + (scrollXPx.toFloat() / maxSx) * (trackW - thumbW)
        canvas.drawRoundRect(
            thumbLeft,
            h - 5f * density,
            thumbLeft + thumbW,
            h - 1.5f * density,
            3f * density,
            3f * density,
            scrollbarPaint
        )
    }
    val maxSy = maxScrollYPx()
    if (maxSy > 0) {
        val trackH = (h - colHeaderHeightPx).coerceAtLeast(1f)
        val thumbH = (trackH * 0.25f).coerceAtLeast(36f * density)
        val thumbTop = colHeaderHeightPx + (scrollYPx.toFloat() / maxSy) * (trackH - thumbH)
        canvas.drawRoundRect(
            w - 5f * density,
            thumbTop,
            w - 1.5f * density,
            thumbTop + thumbH,
            3f * density,
            3f * density,
            scrollbarPaint
        )
    }
}
