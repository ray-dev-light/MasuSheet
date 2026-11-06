package com.masusheet.app.canvas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.masusheet.app.canvas.MasuSheetNativeGridView.ChartRangeDragMode
import com.masusheet.app.canvas.MasuSheetNativeGridView.ChartRangeRenderedTarget
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.core.SpreadsheetColorPalette
import com.masusheet.app.core.SpreadsheetI18n
import com.masusheet.app.model.ChartSeries
import com.masusheet.app.model.NativeSheetObject
import com.masusheet.app.model.SheetObjectKind
import com.masusheet.app.viewmodel.resolveChartSeriesAxisBounds
import com.masusheet.app.viewmodel.resolveChartSeriesAxisRangeText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * ============================================================================
 * [EN] Floating Sheet Objects, Shapes, Downsampled Images (`LruCache`), & 32-Subtype Chart Renderer
 * Draws floating shapes, downsampled images via 32MB `LruCache`, interactive X/Y chart source range frames on the grid, and all 32 chart subtypes with per-element colors.
 *
 * ---
 * [JA / 日本語解説]
 * 図形ジオメトリ、32MB `LruCache` 縮小デコード画像、シート上のグラフ参照範囲ハンドル枠、および全32サブタイプのグラフ本体・要素別文字色を描画するレンダラーです。
 * ============================================================================
 */

/**
 * [EN] Draws all floating charts, shapes, text boxes, and images on `sheetIndex`, plus selection handles, rotation knob, and bound source-range highlights.
 * [JA] 対象シート上の全オブジェクト（グラフ・図形・テキストボックス・画像）と、選択時の回転ノブ・8方向リサイズハンドル・元データ範囲枠を描画します。
 */
internal fun MasuSheetNativeGridView.drawSheetObjectsOverlay(canvas: Canvas, sheetIndex: Int) {
    val effectiveObjects = effectiveSheetObjects(sheetIndex)

    // Draw highlighted X-Axis (purple) and Y-Axis (series color / blue) cell range frames on the sheet grid
    // when a Chart (or a Series line/bar inside it) is selected, plus live drag-move preview!
    val selChart = effectiveObjects.firstOrNull {
        it.id == selectedObjectId && it.sheetIndex == sheetIndex && it.kind == SheetObjectKind.CHART
    }
    if (selChart != null) {
        drawSelectedChartSourceRangesOnGrid(canvas, selChart)
    }

    for (obj in effectiveObjects) {
        if (obj.sheetIndex != sheetIndex) continue
        val rect = getSheetObjectScreenRect(obj)
        if (rect.right < rowHeaderWidthPx || rect.bottom < colHeaderHeightPx) continue

        val rotSave = canvas.save()
        if (abs(obj.rotationDeg) > 0.1f) {
            canvas.rotate(obj.rotationDeg, rect.centerX(), rect.centerY())
        }

        when (obj.kind) {
            SheetObjectKind.SHAPE -> {
                val hasFill = obj.fillColor != 0 && ((obj.fillColor ushr 24) and 0xFF) > 0 && obj.subType != "line"
                if (hasFill) {
                    objectPaint.style = Paint.Style.FILL
                    objectPaint.color = obj.fillColor
                    drawShapeGeometry(canvas, rect, obj.subType, objectPaint)
                }

                if (obj.strokeWidthPt > 0.05f && obj.strokeColor != 0) {
                    objectPaint.style = Paint.Style.STROKE
                    objectPaint.strokeWidth = (obj.strokeWidthPt * density * zoomScale).coerceAtLeast(1f)
                    objectPaint.color = obj.strokeColor
                    drawShapeGeometry(canvas, rect, obj.subType, objectPaint)
                }

                // Skip drawing static text if this exact shape is currently being edited in-place
                val isBeingEditedInPlace = container.isEditingInCell &&
                    container.editingObjectId == obj.id &&
                    container.editingObjectTargetType == InPlaceObjectEditTargetType.OBJECT_MAIN_TEXT
                if (obj.title.isNotBlank() && !isBeingEditedInPlace) {
                    val fgCol = when {
                        obj.textColor != 0 -> obj.textColor
                        !hasFill -> if (isDarkTheme) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
                        obj.fillColor == 0xFFFFFFFF.toInt() || obj.fillColor == 0xFFEAB308.toInt() -> 0xFF0F172A.toInt()
                        else -> 0xFFFFFFFF.toInt()
                    }
                    textPaint.color = fgCol
                    textPaint.textSize = 12f * density * zoomScale
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val pad = 6f * density
                    val lines = wrapTextIntoLines(obj.title, (rect.width() - pad * 2f).coerceAtLeast(16f), textPaint)
                    val fm = textPaint.fontMetrics
                    val lh = (fm.descent - fm.ascent + 2f * density)
                    val totalH = lines.size * lh
                    var curBaseline = rect.centerY() - totalH * 0.5f - fm.ascent
                    val clipSave = canvas.save()
                    canvas.clipRect(rect)
                    for (line in lines) {
                        val tw = textPaint.measureText(line)
                        val tx = rect.centerX() - tw * 0.5f
                        canvas.drawText(line, tx, curBaseline, textPaint)
                        curBaseline += lh
                    }
                    canvas.restoreToCount(clipSave)
                }
            }

            SheetObjectKind.TEXT_BOX -> {
                val hasFill = obj.fillColor != 0 && ((obj.fillColor ushr 24) and 0xFF) > 0
                if (hasFill) {
                    objectPaint.style = Paint.Style.FILL
                    objectPaint.color = if (isDarkTheme && obj.fillColor == 0xFFFFFFFF.toInt()) 0xFF1E293B.toInt() else obj.fillColor
                    canvas.drawRoundRect(rect, 6f * density, 6f * density, objectPaint)
                }

                if (obj.strokeWidthPt > 0.05f && obj.strokeColor != 0) {
                    objectPaint.style = Paint.Style.STROKE
                    objectPaint.strokeWidth = (obj.strokeWidthPt * density * zoomScale).coerceAtLeast(1f)
                    objectPaint.color = obj.strokeColor
                    canvas.drawRoundRect(rect, 6f * density, 6f * density, objectPaint)
                }

                val isBeingEditedInPlace = container.isEditingInCell &&
                    container.editingObjectId == obj.id &&
                    container.editingObjectTargetType == InPlaceObjectEditTargetType.OBJECT_MAIN_TEXT
                if (!isBeingEditedInPlace) {
                    textPaint.color = when {
                        obj.textColor != 0 -> obj.textColor
                        isDarkTheme -> 0xFFF8FAFC.toInt()
                        else -> 0xFF0F172A.toInt()
                    }
                    textPaint.textSize = 11.5f * density * zoomScale
                    textPaint.typeface = Typeface.DEFAULT
                    val pad = 8f * density
                    val lines = wrapTextIntoLines(obj.title, (rect.width() - pad * 2f).coerceAtLeast(16f), textPaint)
                    val fm = textPaint.fontMetrics
                    val lh = (fm.descent - fm.ascent + 2f * density)
                    var curY = rect.top + pad - fm.ascent
                    val clipSave = canvas.save()
                    canvas.clipRect(rect)
                    for (line in lines) {
                        if (curY - lh > rect.bottom) break
                        canvas.drawText(line, rect.left + pad, curY, textPaint)
                        curY += lh
                    }
                    canvas.restoreToCount(clipSave)
                }
            }

            SheetObjectKind.IMAGE -> {
                val hasFill = obj.fillColor != 0 && ((obj.fillColor ushr 24) and 0xFF) > 0
                if (hasFill) {
                    objectPaint.style = Paint.Style.FILL
                    objectPaint.color = if (isDarkTheme && obj.fillColor == 0xFFFFFFFF.toInt()) 0xFF0F172A.toInt() else obj.fillColor
                    canvas.drawRoundRect(rect, 6f * density, 6f * density, objectPaint)
                }

                val bmp = getOrDecodeObjectBitmap(obj)
                if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                    val imgSave = canvas.save()
                    canvas.clipRect(rect)
                    // Draw into full `rect` so initial paste is at natural aspect ratio AND 8-direction handles
                    // can stretch vertically-only (top/bottom) or horizontally-only (left/right) when desired!
                    canvas.drawBitmap(bmp, null, rect, null)
                    canvas.restoreToCount(imgSave)
                } else {
                    textPaint.color = if (isDarkTheme) 0xFF93C5FD.toInt() else 0xFF1D4ED8.toInt()
                    textPaint.textSize = 12f * density * zoomScale
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val label = "🖼️ ${obj.title}"
                    val tw = textPaint.measureText(label)
                    val fm = textPaint.fontMetrics
                    canvas.drawText(
                        label,
                        rect.centerX() - tw * 0.5f,
                        rect.centerY() - (fm.descent + fm.ascent) * 0.5f,
                        textPaint
                    )
                }

                if (obj.strokeWidthPt > 0.05f && obj.strokeColor != 0) {
                    objectPaint.style = Paint.Style.STROKE
                    objectPaint.strokeWidth = (obj.strokeWidthPt * density * zoomScale).coerceAtLeast(1f)
                    objectPaint.color = obj.strokeColor
                    canvas.drawRoundRect(rect, 6f * density, 6f * density, objectPaint)
                }
            }

            SheetObjectKind.CHART -> {
                drawChartObjectContent(canvas, rect, obj)
            }
        }

        // Selection border, all 8 resize handles (4 corners + Top/Bottom vertical + Left/Right horizontal), and Spreadsheet Top Rotation Handle (⟳)
        val isObjSelected = obj.id == selectedObjectId || obj.id in selectedObjectIds
        if (isObjSelected) {
            selectionBorderPaint.strokeWidth = 2.2f * density
            canvas.drawRect(rect, selectionBorderPaint)
            val rHandle = 5.5f * density
            val brHandle = 6.5f * density
            val midLong = 7f * density
            val midShort = 4.5f * density
            val cx = rect.centerX()
            val cy = rect.centerY()

            // 4 Corner Handles (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
            canvas.drawRect(rect.left - rHandle, rect.top - rHandle, rect.left + rHandle, rect.top + rHandle, handlePaint)
            canvas.drawRect(rect.right - rHandle, rect.top - rHandle, rect.right + rHandle, rect.top + rHandle, handlePaint)
            canvas.drawRect(rect.left - rHandle, rect.bottom - rHandle, rect.left + rHandle, rect.bottom + rHandle, handlePaint)
            canvas.drawRect(rect.right - brHandle, rect.bottom - brHandle, rect.right + brHandle, rect.bottom + brHandle, handlePaint)

            // 4 Edge Midpoint Handles (Top & Bottom for Vertical-Only Resize; Left & Right for Horizontal-Only Resize)
            canvas.drawRect(cx - midLong, rect.top - midShort, cx + midLong, rect.top + midShort, handlePaint)
            canvas.drawRect(cx - midLong, rect.bottom - midShort, cx + midLong, rect.bottom + midShort, handlePaint)
            canvas.drawRect(rect.left - midShort, cy - midLong, rect.left + midShort, cy + midLong, handlePaint)
            canvas.drawRect(rect.right - midShort, cy - midLong, rect.right + midShort, cy + midLong, handlePaint)

            // Spreadsheet Top Rotation Handle (⟳) above top-center of object
            val rotOffsetPx = 22f * density
            val rotX = rect.centerX()
            val rotY = rect.top - rotOffsetPx
            canvas.drawLine(rotX, rect.top, rotX, rotY, selectionBorderPaint)
            val rotRadius = 9.5f * density
            objectPaint.style = Paint.Style.FILL
            objectPaint.color = if (isDarkTheme) 0xFF1E293B.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawCircle(rotX, rotY, rotRadius, objectPaint)
            objectPaint.style = Paint.Style.STROKE
            objectPaint.strokeWidth = 2f * density
            objectPaint.color = 0xFF1F9D55.toInt()
            canvas.drawCircle(rotX, rotY, rotRadius, objectPaint)

            textPaint.color = 0xFF1F9D55.toInt()
            textPaint.textSize = 11f * density
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val rotSymbol = "⟳"
            val rtw = textPaint.measureText(rotSymbol)
            val rfm = textPaint.fontMetrics
            canvas.drawText(
                rotSymbol,
                rotX - rtw * 0.5f,
                rotY - (rfm.descent + rfm.ascent) * 0.5f,
                textPaint
            )

            // Compact live coordinate & multi-selection badge at top-left (for arrow-key vertical/horizontal alignment)
            val posBadge = if (selectedObjectIds.size > 1) {
                "✓ 選択中 (X:${obj.xPt.roundToInt()} Y:${obj.yPt.roundToInt()}pt)"
            } else {
                "X:${obj.xPt.roundToInt()} Y:${obj.yPt.roundToInt()}pt"
            }
            textPaint.textSize = 8.8f * density
            val pfm = textPaint.fontMetrics
            val pbw = textPaint.measureText(posBadge) + 8f * density
            val pbh = 14f * density
            val pbx = rect.left.coerceAtLeast(rowHeaderWidthPx + 2f * density)
            val pby = (rect.top - pbh - 3f * density).coerceAtLeast(colHeaderHeightPx + 2f * density)
            objectPaint.style = Paint.Style.FILL
            objectPaint.color = 0xDD0F172A.toInt()
            canvas.drawRoundRect(pbx, pby, pbx + pbw, pby + pbh, 3.5f * density, 3.5f * density, objectPaint)
            textPaint.color = 0xFF4ADE80.toInt()
            canvas.drawText(posBadge, pbx + 4f * density, pby + pbh * 0.5f - (pfm.descent + pfm.ascent) * 0.5f, textPaint)

            if (abs(obj.rotationDeg) > 0.5f || isRotatingObject) {
                val degBadge = "${obj.rotationDeg.roundToInt()}°"
                textPaint.textSize = 9.5f * density
                val bw = textPaint.measureText(degBadge) + 8f * density
                val bh = 15f * density
                val bx = rotX + rotRadius + 4f * density
                val by = rotY - bh * 0.5f
                objectPaint.style = Paint.Style.FILL
                objectPaint.color = 0xEE0F172A.toInt()
                canvas.drawRoundRect(bx, by, bx + bw, by + bh, 4f * density, 4f * density, objectPaint)
                textPaint.color = 0xFF4ADE80.toInt()
                canvas.drawText(degBadge, bx + 4f * density, rotY - (rfm.descent + rfm.ascent) * 0.45f, textPaint)
            }
        }
        canvas.restoreToCount(rotSave)
    }
}

/**
 * [EN] Draws vector geometry (`rectangle`, `roundRect`, `oval`, `diamond`, `triangle`, `pentagon`, `hexagon`, `star`, `arrow`, `callout`, `line`) inside `rect`.
 * [JA] 図形サブタイプに応じたベクターパス（四角形、角丸、円、ひし形、三角形、多角形、星、矢印、吹き出し、直線）を `rect` 内に描画します。
 */
internal fun MasuSheetNativeGridView.drawShapeGeometry(canvas: Canvas, rect: RectF, subType: String, paint: Paint) {
    when (subType.lowercase()) {
        "circle", "ellipse", "oval" -> canvas.drawOval(rect, paint)
        "round_rect", "roundrect" -> canvas.drawRoundRect(rect, 14f * density, 14f * density, paint)
        "line" -> canvas.drawLine(rect.left, rect.bottom, rect.right, rect.top, paint)
        "triangle" -> {
            objectPath.reset()
            objectPath.moveTo(rect.centerX(), rect.top)
            objectPath.lineTo(rect.right, rect.bottom)
            objectPath.lineTo(rect.left, rect.bottom)
            objectPath.close()
            canvas.drawPath(objectPath, paint)
        }
        "diamond" -> {
            objectPath.reset()
            objectPath.moveTo(rect.centerX(), rect.top)
            objectPath.lineTo(rect.right, rect.centerY())
            objectPath.lineTo(rect.centerX(), rect.bottom)
            objectPath.lineTo(rect.left, rect.centerY())
            objectPath.close()
            canvas.drawPath(objectPath, paint)
        }
        "pentagon" -> {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val rx = rect.width() * 0.5f
            val ry = rect.height() * 0.5f
            objectPath.reset()
            for (i in 0 until 5) {
                val angle = (-90.0 + i * 72.0) * PI / 180.0
                val px = (cx + rx * cos(angle)).toFloat()
                val py = (cy + ry * sin(angle)).toFloat()
                if (i == 0) objectPath.moveTo(px, py) else objectPath.lineTo(px, py)
            }
            objectPath.close()
            canvas.drawPath(objectPath, paint)
        }
        "hexagon" -> {
            val insetX = rect.width() * 0.22f
            objectPath.reset()
            objectPath.moveTo(rect.left + insetX, rect.top)
            objectPath.lineTo(rect.right - insetX, rect.top)
            objectPath.lineTo(rect.right, rect.centerY())
            objectPath.lineTo(rect.right - insetX, rect.bottom)
            objectPath.lineTo(rect.left + insetX, rect.bottom)
            objectPath.lineTo(rect.left, rect.centerY())
            objectPath.close()
            canvas.drawPath(objectPath, paint)
        }
        "star" -> {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val outerRx = rect.width() * 0.5f
            val outerRy = rect.height() * 0.5f
            val innerRx = outerRx * 0.40f
            val innerRy = outerRy * 0.40f
            objectPath.reset()
            for (i in 0 until 10) {
                val angle = (-90.0 + i * 36.0) * PI / 180.0
                val rx = if (i % 2 == 0) outerRx else innerRx
                val ry = if (i % 2 == 0) outerRy else innerRy
                val px = (cx + rx * cos(angle)).toFloat()
                val py = (cy + ry * sin(angle)).toFloat()
                if (i == 0) objectPath.moveTo(px, py) else objectPath.lineTo(px, py)
            }
            objectPath.close()
            canvas.drawPath(objectPath, paint)
        }
        "callout" -> {
            val bodyBottom = rect.top + rect.height() * 0.78f
            objectPath.reset()
            objectPath.moveTo(rect.left, rect.top)
            objectPath.lineTo(rect.right, rect.top)
            objectPath.lineTo(rect.right, bodyBottom)
            objectPath.lineTo(rect.left + rect.width() * 0.42f, bodyBottom)
            objectPath.lineTo(rect.left + rect.width() * 0.22f, rect.bottom)
            objectPath.lineTo(rect.left + rect.width() * 0.26f, bodyBottom)
            objectPath.lineTo(rect.left, bodyBottom)
            objectPath.close()
            canvas.drawPath(objectPath, paint)
        }
        "arrow", "arrow_right" -> {
            val headW = (rect.width() * 0.32f).coerceAtLeast(12f * density)
            val shaftTop = rect.top + rect.height() * 0.28f
            val shaftBot = rect.bottom - rect.height() * 0.28f
            val headLeft = rect.right - headW
            objectPath.reset()
            objectPath.moveTo(rect.left, shaftTop)
            objectPath.lineTo(headLeft, shaftTop)
            objectPath.lineTo(headLeft, rect.top)
            objectPath.lineTo(rect.right, rect.centerY())
            objectPath.lineTo(headLeft, rect.bottom)
            objectPath.lineTo(headLeft, shaftBot)
            objectPath.lineTo(rect.left, shaftBot)
            objectPath.close()
            canvas.drawPath(objectPath, paint)
        }
        "arrow_left" -> {
            val headW = (rect.width() * 0.32f).coerceAtLeast(12f * density)
            val shaftTop = rect.top + rect.height() * 0.28f
            val shaftBot = rect.bottom - rect.height() * 0.28f
            val headRight = rect.left + headW
            objectPath.reset()
            objectPath.moveTo(rect.right, shaftTop)
            objectPath.lineTo(headRight, shaftTop)
            objectPath.lineTo(headRight, rect.top)
            objectPath.lineTo(rect.left, rect.centerY())
            objectPath.lineTo(headRight, rect.bottom)
            objectPath.lineTo(headRight, shaftBot)
            objectPath.lineTo(rect.right, shaftBot)
            objectPath.close()
            canvas.drawPath(objectPath, paint)
        }
        "arrow_up" -> {
            val headH = (rect.height() * 0.32f).coerceAtLeast(12f * density)
            val shaftLeft = rect.left + rect.width() * 0.28f
            val shaftRight = rect.right - rect.width() * 0.28f
            val headBottom = rect.top + headH
            objectPath.reset()
            objectPath.moveTo(shaftLeft, rect.bottom)
            objectPath.lineTo(shaftLeft, headBottom)
            objectPath.lineTo(rect.left, headBottom)
            objectPath.lineTo(rect.centerX(), rect.top)
            objectPath.lineTo(rect.right, headBottom)
            objectPath.lineTo(shaftRight, headBottom)
            objectPath.lineTo(shaftRight, rect.bottom)
            objectPath.close()
            canvas.drawPath(objectPath, paint)
        }
        "arrow_down" -> {
            val headH = (rect.height() * 0.32f).coerceAtLeast(12f * density)
            val shaftLeft = rect.left + rect.width() * 0.28f
            val shaftRight = rect.right - rect.width() * 0.28f
            val headTop = rect.bottom - headH
            objectPath.reset()
            objectPath.moveTo(shaftLeft, rect.top)
            objectPath.lineTo(shaftLeft, headTop)
            objectPath.lineTo(rect.left, headTop)
            objectPath.lineTo(rect.centerX(), rect.bottom)
            objectPath.lineTo(rect.right, headTop)
            objectPath.lineTo(shaftRight, headTop)
            objectPath.lineTo(shaftRight, rect.top)
            objectPath.close()
            canvas.drawPath(objectPath, paint)
        }
        else -> canvas.drawRect(rect, paint)
    }
}

/**
 * [EN] Formats chart axis ticks and data labels compactly (`1.2M`, comma-grouped integers, or trimmed decimals).
 * [JA] グラフの軸目盛りやデータラベル数値を、桁数に応じたコンパクトな表記（`1.2M`、カンマ区切り整数、小数）に整形します。
 */
internal fun MasuSheetNativeGridView.formatChartValueCompact(v: Float): String {
    if (!v.isFinite()) return "0"
    val absV = abs(v)
    return when {
        absV >= 10_000_000f -> String.format(java.util.Locale.US, "%.1fM", v / 1_000_000f)
        abs(v - v.roundToInt()) < 0.05f -> java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(v.roundToInt().toLong())
        else -> String.format(java.util.Locale.US, "%.1f", v)
    }
}

/**
 * [EN] Returns `obj.chartSeries` when populated, or synthesizes a single fallback series from legacy `obj.chartValues` so every chart renders cleanly.
 * [JA] `obj.chartSeries` が存在する場合はそれを返し、単一配列 `chartValues` のみの場合は互換用の1系列データを生成して返します。
 */
internal fun MasuSheetNativeGridView.effectiveChartSeries(obj: NativeSheetObject): List<ChartSeries> {
    val rawList = if (obj.chartSeries.isNotEmpty()) {
        obj.chartSeries
    } else {
        val fallbackVals = if (obj.chartValues.isNotEmpty()) obj.chartValues else listOf(0f)
        listOf(ChartSeries(name = "データ", values = fallbackVals, color = 0xFF2563EB.toInt()))
    }
    return rawList.map { s ->
        if (s.values.all { it.isFinite() }) s
        else s.copy(values = s.values.map { if (it.isFinite()) it else 0f })
    }
}

/**
 * [EN] Draws highlighted cell-range frames on the worksheet grid for the selected Chart's X-Axis (Purple)
 * and Y-Axis (Series Color / Blue), complete with inset borders (to separate adjacent columns),
 * 4 corner square resize handles + bottom edge grip, a compact top pill badge connected to its column,
 * and a live preview box while moving or resizing the range on the sheet.
 *
 * [JA] 選択中グラフのX軸（紫）および各Y軸系列（青・緑・橙など）のデータ範囲ハイライト枠をワークシート上に描画します。
 * 隣接列同士が重ならないよう枠線をわずかに内側へオフセットし、四隅のリサイズ四角ハンドル・下辺グリップ・
 * 列と結ばれた上部移動ラベルバッジ、およびドラッグ移動／サイズ変更中のライブプレビュー枠を表示します。
 */
internal fun MasuSheetNativeGridView.drawSelectedChartSourceRangesOnGrid(canvas: Canvas, chartObj: NativeSheetObject) {
    lastDrawnChartRangeTargets.clear()
    val seriesList = effectiveChartSeries(chartObj)
    if (seriesList.isEmpty()) return
    val subElem = chartObj.selectedSubElement
    val activeSeriesIdx = if (subElem.startsWith("series:")) {
        subElem.removePrefix("series:").toIntOrNull()?.takeIf { it in seriesList.indices } ?: 0
    } else 0
    val isSeriesExclusivelySelected = subElem.startsWith("series:")
    val isXAxisExclusivelySelected = subElem == "xAxis"
    val palette = intArrayOf(
        0xFF2563EB.toInt(),
        0xFF10B981.toInt(),
        0xFFF59E0B.toInt(),
        0xFFEC4899.toInt(),
        0xFF8B5CF6.toInt(),
        0xFF06B6D4.toInt()
    )

    val drawnPillRects = mutableListOf<RectF>()
    val insetPx = 1.8f * density

    fun drawRangeBoxWithBadge(
        bounds: IntArray,
        colorArgb: Int,
        badgeText: String,
        isPrimary: Boolean,
        isEmphasized: Boolean = false,
        recordTarget: Boolean = false,
        seriesIndex: Int = 0,
        isXAxis: Boolean = false
    ) {
        val rawRect = getCellRangeScreenRect(bounds[0], bounds[1], bounds[2], bounds[3])
        if (rawRect.right < rowHeaderWidthPx || rawRect.bottom < colHeaderHeightPx ||
            rawRect.left > width || rawRect.top > height
        ) {
            return
        }
        val rect = if (rawRect.width() > insetPx * 3f && rawRect.height() > insetPx * 3f) {
            RectF(
                rawRect.left + insetPx,
                rawRect.top + insetPx,
                rawRect.right - insetPx,
                rawRect.bottom - insetPx
            )
        } else {
            rawRect
        }

        checkboxBoxPaint.style = Paint.Style.FILL
        checkboxBoxPaint.color = (colorArgb and 0x00FFFFFF) or (if (isEmphasized) 0x2C000000 else if (isPrimary) 0x1E000000 else 0x12000000)
        canvas.drawRect(rect, checkboxBoxPaint)

        objectPaint.style = Paint.Style.STROKE
        objectPaint.strokeWidth = (if (isEmphasized) 2.8f else if (isPrimary) 2.2f else 1.6f) * density
        objectPaint.color = colorArgb
        canvas.drawRect(rect, objectPaint)

        var recordedPill: RectF? = null
        if (isPrimary) {
            val hs = (if (isEmphasized) 5.0f else 4.4f) * density
            val halo = hs + 1.2f * density
            checkboxBoxPaint.style = Paint.Style.FILL
            // White contrast outline around the 4 corner resize squares so adjacent columns are crisp
            checkboxBoxPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawRect(rect.left - halo, rect.top - halo, rect.left + halo, rect.top + halo, checkboxBoxPaint)
            canvas.drawRect(rect.right - halo, rect.top - halo, rect.right + halo, rect.top + halo, checkboxBoxPaint)
            canvas.drawRect(rect.left - halo, rect.bottom - halo, rect.left + halo, rect.bottom + halo, checkboxBoxPaint)
            canvas.drawRect(rect.right - halo, rect.bottom - halo, rect.right + halo, rect.bottom + halo, checkboxBoxPaint)

            checkboxBoxPaint.color = colorArgb
            canvas.drawRect(rect.left - hs, rect.top - hs, rect.left + hs, rect.top + hs, checkboxBoxPaint)
            canvas.drawRect(rect.right - hs, rect.top - hs, rect.right + hs, rect.top + hs, checkboxBoxPaint)
            canvas.drawRect(rect.left - hs, rect.bottom - hs, rect.left + hs, rect.bottom + hs, checkboxBoxPaint)
            canvas.drawRect(rect.right - hs, rect.bottom - hs, rect.right + hs, rect.bottom + hs, checkboxBoxPaint)

            // Bottom-center horizontal resize bar handle for easy vertical range pulling
            if (rect.width() >= 28f * density) {
                val bw = min(14f * density, rect.width() * 0.25f)
                val bh = 2.8f * density
                canvas.drawRoundRect(
                    rect.centerX() - bw,
                    rect.bottom - bh,
                    rect.centerX() + bw,
                    rect.bottom + bh,
                    2f * density,
                    2f * density,
                    checkboxBoxPaint
                )
            }

            if (badgeText.isNotBlank()) {
                textPaint.textSize = 9.6f * density
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val tw = textPaint.measureText(badgeText) + 11f * density
                val th = 16.5f * density
                val bx = (rect.left + 3f * density).coerceAtLeast(rowHeaderWidthPx + 2f * density)
                val baseBy = (rect.top - th - 4f * density).coerceAtLeast(colHeaderHeightPx + 2f * density)
                var by = baseBy
                var pillRect = RectF(bx, by, bx + tw, by + th)
                var stackStep = 0
                val stepH = th + 3f * density
                while (drawnPillRects.any { RectF.intersects(it, pillRect) } && stackStep < 8) {
                    stackStep++
                    val candidateAbove = rect.top - 4f * density - th - stepH * stackStep
                    by = if (candidateAbove >= colHeaderHeightPx + 2f * density) {
                        candidateAbove
                    } else {
                        // Keep stacked pills above corner squares or offset cleanly inside the top margin
                        (colHeaderHeightPx + 2f * density + stepH * stackStep)
                    }
                    pillRect = RectF(bx, by, bx + tw, by + th)
                }
                drawnPillRects.add(pillRect)
                recordedPill = pillRect

                // Draw connector line linking stacked pill badge directly to its column's top edge
                val connX = (rect.left + 8f * density).coerceIn(rect.left + 2f * density, rect.right - 2f * density)
                val connYStart = if (pillRect.bottom <= rect.top) pillRect.bottom else pillRect.top
                if (abs(connYStart - rect.top) > 1.5f * density) {
                    objectPaint.style = Paint.Style.STROKE
                    objectPaint.strokeWidth = 1.8f * density
                    objectPaint.color = colorArgb
                    canvas.drawLine(connX, connYStart, connX, rect.top, objectPaint)
                }

                // White outer border around the pill badge so stacked badges remain visually distinct
                checkboxBoxPaint.style = Paint.Style.FILL
                checkboxBoxPaint.color = 0xEEFFFFFF.toInt()
                val outerPill = RectF(
                    pillRect.left - 1f * density,
                    pillRect.top - 1f * density,
                    pillRect.right + 1f * density,
                    pillRect.bottom + 1f * density
                )
                canvas.drawRoundRect(outerPill, 5f * density, 5f * density, checkboxBoxPaint)

                checkboxBoxPaint.color = colorArgb
                canvas.drawRoundRect(pillRect, 4.5f * density, 4.5f * density, checkboxBoxPaint)
                textPaint.color = 0xFFFFFFFF.toInt()
                val fm = textPaint.fontMetrics
                canvas.drawText(
                    badgeText,
                    pillRect.left + 5.5f * density,
                    pillRect.centerY() - (fm.descent + fm.ascent) * 0.5f,
                    textPaint
                )
            }
        }

        if (recordTarget) {
            lastDrawnChartRangeTargets.add(
                ChartRangeRenderedTarget(
                    chartId = chartObj.id,
                    seriesIndex = seriesIndex,
                    isXAxis = isXAxis,
                    bounds = bounds,
                    frameRect = rect,
                    pillRect = recordedPill
                )
            )
        }
    }

    val orderedSeriesIndices = if (isSeriesExclusivelySelected) {
        buildList {
            for (i in seriesList.indices) if (i != activeSeriesIdx) add(i)
            add(activeSeriesIdx)
        }
    } else {
        seriesList.indices.toList()
    }

    val isEn = viewModel.uiState.value.language == "en"

    // 1. Draw a single unified X-Axis cell range (Purple) with 4 corner handles & draggable badge (never split/duplicated)
    val xCol = 0xFF7C3AED.toInt()
    val primaryXBounds = viewModel.resolveChartSeriesAxisBounds(chartObj, 0, isXAxis = true)
    val primaryXRef = viewModel.resolveChartSeriesAxisRangeText(chartObj, 0, isXAxis = true)
    if (primaryXBounds != null && primaryXRef.isNotBlank()) {
        val xBadge = if (isEn) "⠿ 📏 X: $primaryXRef" else "⠿ 📏 X軸: $primaryXRef"
        drawRangeBoxWithBadge(
            bounds = primaryXBounds,
            colorArgb = xCol,
            badgeText = xBadge,
            isPrimary = true,
            isEmphasized = isXAxisExclusivelySelected,
            recordTarget = true,
            seriesIndex = 0,
            isXAxis = true
        )
    }

    // 2. Draw EVERY series's Y-Axis cell range in its own series color (Blue, Green, Amber, ...) with its badge!
    for (sIdx in orderedSeriesIndices) {
        val yBounds = viewModel.resolveChartSeriesAxisBounds(chartObj, sIdx, isXAxis = false) ?: continue
        val yRef = viewModel.resolveChartSeriesAxisRangeText(chartObj, sIdx, isXAxis = false)
        val series = seriesList[sIdx]
        val rawSName = series.name.ifBlank { if (isEn) "Series ${sIdx + 1}" else "系列${sIdx + 1}" }
        val sName = SpreadsheetI18n.translateChartDisplayText(rawSName, isEn)
        val yCol = series.color.takeIf { it != 0 } ?: palette[sIdx % palette.size]
        val yBadge = "⠿ 📈 $sName: $yRef"
        drawRangeBoxWithBadge(
            bounds = yBounds,
            colorArgb = yCol,
            badgeText = yBadge,
            isPrimary = true,
            isEmphasized = isSeriesExclusivelySelected && sIdx == activeSeriesIdx,
            recordTarget = true,
            seriesIndex = sIdx,
            isXAxis = false
        )
    }

    // 3. Live Drag-Move or Corner-Resize Preview when the user is manipulating the X-Axis or Y-Axis cell range on the sheet
    if (isDraggingChartAxisRangeMove && chartAxisMoveChartId == chartObj.id) {
        val r0 = chartAxisPreviewStartRow.coerceAtLeast(0)
        val c0 = chartAxisPreviewStartCol.coerceAtLeast(0)
        val r1 = chartAxisPreviewEndRow.coerceAtLeast(r0)
        val c1 = chartAxisPreviewEndCol.coerceAtLeast(c0)
        val newRef = if (r0 == r1 && c0 == c1) {
            "${MasuSheetConfig.formatColumnLabel(c0)}${r0 + 1}"
        } else {
            "${MasuSheetConfig.formatColumnLabel(c0)}${r0 + 1}:${MasuSheetConfig.formatColumnLabel(c1)}${r1 + 1}"
        }
        val moveCol = if (chartAxisMoveIsX) 0xFF9333EA.toInt() else 0xFF0284C7.toInt()
        val isResizing = chartAxisDragMode != ChartRangeDragMode.MOVE
        val axisTitle = if (chartAxisMoveIsX) {
            if (isResizing) {
                if (isEn) "📏 Resizing X-Axis" else "📏 X軸範囲を伸縮中"
            } else {
                if (isEn) "📏 Moving X-Axis" else "📏 X軸範囲を移動中"
            }
        } else {
            if (isResizing) {
                if (isEn) "📈 Resizing Y-Axis" else "📈 Y軸範囲を伸縮中"
            } else {
                if (isEn) "📈 Moving Y-Axis" else "📈 Y軸範囲を移動中"
            }
        }
        val confirmHint = if (isEn) "(Release to apply)" else "(離して確定)"
        drawRangeBoxWithBadge(
            bounds = intArrayOf(r0, c0, r1, c1),
            colorArgb = moveCol,
            badgeText = "$axisTitle → $newRef $confirmHint",
            isPrimary = true,
            isEmphasized = true,
            recordTarget = false
        )
    }
}


/**
 * [EN] Fetches or decodes the `Bitmap` for an image `NativeSheetObject` via `imageBitmapCache`, skipping recycled bitmaps safely.
 * [JA] 画像オブジェクトの `imageBytes` から `imageBitmapCache` 経由で `Bitmap` を取得・デコードします（破棄済みBitmapは自動再生成）。
 */
internal fun MasuSheetNativeGridView.getOrDecodeObjectBitmap(obj: NativeSheetObject): Bitmap? {
    val bytes = obj.imageBytes ?: return null
    val cacheKey = "${obj.id}_${bytes.size}"
    imageBitmapCache.get(cacheKey)?.let { cached ->
        if (!cached.isRecycled) return cached
    }
    val decoded = runCatching {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val maxEdge = 2048
        var sampleSize = 1
        if (boundsOpts.outWidth > maxEdge || boundsOpts.outHeight > maxEdge) {
            var halfW = boundsOpts.outWidth / 2
            var halfH = boundsOpts.outHeight / 2
            while ((halfW / sampleSize) >= maxEdge || (halfH / sampleSize) >= maxEdge) {
                sampleSize *= 2
            }
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }.getOrNull()
    if (decoded != null) {
        imageBitmapCache.put(cacheKey, decoded)
    }
    return decoded
}
