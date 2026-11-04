package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import com.masusheet.app.MasuSheetNativeGridView.*

/**
 * ============================================================================
 * [EN] Floating Sheet Objects, Shapes, Downsampled Images (`LruCache`), & 32-Subtype Chart Renderer
 * Draws floating shapes, downsampled images via 32MB `LruCache` (`C-06`), interactive X/Y chart source range frames on the grid, and all 32 chart subtypes with per-element colors.
 *
 * ---
 * [JA / 日本語解説]
 * 図形ジオメトリ、32MB `LruCache` 縮小デコード画像 (`C-06`)、シート上のグラフ参照範囲ハンドル枠、および全32サブタイプのグラフ本体・要素別文字色を描画するレンダラーです。
 * ============================================================================
 */

/**
 * [EN] Grid canvas helper that executes `drawSheetObjectsOverlay` (draw sheet objects overlay) on [MasuSheetNativeGridView].
 * [JA] グリッドキャンバス ([MasuSheetNativeGridView]) における `drawSheetObjectsOverlay` 処理を実行します。
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
 * [EN] Grid canvas helper that executes `drawShapeGeometry` (draw shape geometry) on [MasuSheetNativeGridView].
 * [JA] グリッドキャンバス ([MasuSheetNativeGridView]) における `drawShapeGeometry` 処理を実行します。
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
 * [EN] Grid canvas helper that executes `formatChartValueCompact` (format chart value compact) on [MasuSheetNativeGridView].
 * [JA] グリッドキャンバス ([MasuSheetNativeGridView]) における `formatChartValueCompact` 処理を実行します。
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
 * [EN] Grid canvas helper that executes `effectiveChartSeries` (effective chart series) on [MasuSheetNativeGridView].
 * [JA] グリッドキャンバス ([MasuSheetNativeGridView]) における `effectiveChartSeries` 処理を実行します。
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
 * [EN] Grid canvas helper that executes `drawChartObjectContent` (draw chart object content) on [MasuSheetNativeGridView].
 * [JA] グリッドキャンバス ([MasuSheetNativeGridView]) における `drawChartObjectContent` 処理を実行します。
 */
internal fun MasuSheetNativeGridView.drawChartObjectContent(canvas: Canvas, rect: RectF, obj: NativeSheetObject) {
    val isEn = viewModel.uiState.value.language == "en"
    val hasFill = obj.fillColor != 0 && ((obj.fillColor ushr 24) and 0xFF) > 0
    if (hasFill) {
        objectPaint.style = Paint.Style.FILL
        objectPaint.color = if (isDarkTheme && obj.fillColor == 0xFFFFFFFF.toInt()) 0xFF1E293B.toInt() else obj.fillColor
        canvas.drawRoundRect(rect, 8f * density, 8f * density, objectPaint)
    }

    if (obj.strokeWidthPt > 0.05f && obj.strokeColor != 0) {
        objectPaint.style = Paint.Style.STROKE
        objectPaint.strokeWidth = (obj.strokeWidthPt * density * zoomScale).coerceAtLeast(1f)
        objectPaint.color = obj.strokeColor
        canvas.drawRoundRect(rect, 8f * density, 8f * density, objectPaint)
    }

    val clipSave = canvas.save()
    canvas.clipRect(rect)

    val pad = 8f * density
    val primaryFg = when {
        obj.textColor != 0 -> obj.textColor
        isDarkTheme -> 0xFFF1F5F9.toInt()
        else -> 0xFF0F172A.toInt()
    }
    val secondaryFg = when {
        obj.textColor != 0 -> obj.textColor
        isDarkTheme -> 0xFF94A3B8.toInt()
        else -> 0xFF475569.toInt()
    }
    val titleFg = if (obj.chartTitleTextColor != 0) obj.chartTitleTextColor else primaryFg
    val xAxisFg = if (obj.chartXAxisTextColor != 0) obj.chartXAxisTextColor else primaryFg
    val yAxisFg = if (obj.chartYAxisTextColor != 0) obj.chartYAxisTextColor else secondaryFg
    val legendFg = if (obj.chartLegendTextColor != 0) obj.chartLegendTextColor else primaryFg
    val axisGridCol = if (isDarkTheme) 0xFF334155.toInt() else 0xFFE2E8F0.toInt()

    // 1. Draw Chart Title (localized dynamically between JA and EN when using default/auto titles)
    textPaint.color = titleFg
    textPaint.textSize = 11.5f * density * zoomScale
    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val fmTitle = textPaint.fontMetrics
    val titleBaseline = rect.top + pad - fmTitle.ascent
    val displayTitle = SpreadsheetI18n.translateChartDisplayText(obj.title, isEn)
    val titleWidthPx = textPaint.measureText(displayTitle)
    canvas.drawText(displayTitle, rect.left + pad, titleBaseline, textPaint)

    val seriesList = effectiveChartSeries(obj)
    val subElem = obj.selectedSubElement
    val selectedSeriesIdx = if (subElem.startsWith("series:")) {
        subElem.removePrefix("series:").toIntOrNull()?.takeIf { it in seriesList.indices }
    } else null

    if (obj.sourceRangeText.isNotBlank()) {
        textPaint.color = legendFg
        textPaint.textSize = 8.5f * density * zoomScale
        textPaint.typeface = Typeface.DEFAULT_BOLD
        val rw = textPaint.measureText(obj.sourceRangeText)
        val rightX = rect.right - pad - rw
        if (rightX > rect.left + pad + titleWidthPx + 6f * density) {
            canvas.drawText(obj.sourceRangeText, rightX, titleBaseline, textPaint)
        } else {
            // If the chart title is wide, draw the source range text cleanly at the right end of the legend row
            val legendRowY = titleBaseline + fmTitle.descent + 11.5f * density * zoomScale
            if (rightX > rect.left + pad + 40f * density) {
                canvas.drawText(obj.sourceRangeText, rightX, legendRowY, textPaint)
            }
        }
    }
    val primarySeriesColor = seriesList.firstOrNull()?.color ?: 0
    val palette = if (primarySeriesColor != 0 && primarySeriesColor != 0xFF2563EB.toInt()) {
        intArrayOf(
            primarySeriesColor,
            SpreadsheetColorPalette.applySpreadsheetTint(primarySeriesColor, 0.28),
            SpreadsheetColorPalette.applySpreadsheetTint(primarySeriesColor, -0.22),
            SpreadsheetColorPalette.applySpreadsheetTint(primarySeriesColor, 0.48),
            SpreadsheetColorPalette.applySpreadsheetTint(primarySeriesColor, -0.38),
            SpreadsheetColorPalette.applySpreadsheetTint(primarySeriesColor, 0.16)
        )
    } else {
        intArrayOf(
            0xFF2563EB.toInt(),
            0xFF10B981.toInt(),
            0xFFF59E0B.toInt(),
            0xFFEC4899.toInt(),
            0xFF8B5CF6.toInt(),
            0xFF06B6D4.toInt()
        )
    }

    // 2. Draw Legend Row under Title
    val legendTop = titleBaseline + fmTitle.descent + 3f * density
    textPaint.textSize = 9.2f * density * zoomScale
    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val fmLegend = textPaint.fontMetrics
    val legendBaseline = legendTop - fmLegend.ascent
    var legendCursorX = rect.left + pad
    val swatchSize = 8f * density * zoomScale
    for ((sIdx, series) in seriesList.withIndex()) {
        val sColor = if (series.color != 0) series.color else palette[sIdx % palette.size]
        if (legendCursorX + 40f * density > rect.right - pad) break
        val isThisSeriesSelected = selectedSeriesIdx == sIdx
        val rawSName = series.name.ifBlank { if (isEn) "Series ${sIdx + 1}" else "系列${sIdx + 1}" }
        val sName = SpreadsheetI18n.translateChartDisplayText(rawSName, isEn)
        val itemW = swatchSize + 3.5f * density + textPaint.measureText(sName)
        val swTop = legendBaseline + (fmLegend.ascent + fmLegend.descent) * 0.5f - swatchSize * 0.5f
        if (isThisSeriesSelected) {
            objectPaint.style = Paint.Style.FILL
            objectPaint.color = (sColor and 0x00FFFFFF) or 0x2E000000
            canvas.drawRoundRect(
                legendCursorX - 3f * density,
                legendTop - 1f * density,
                legendCursorX + itemW + 3f * density,
                legendBaseline + fmLegend.descent + 2f * density,
                4f * density,
                4f * density,
                objectPaint
            )
        }
        objectPaint.style = Paint.Style.FILL
        objectPaint.color = sColor
        canvas.drawRoundRect(
            legendCursorX,
            swTop,
            legendCursorX + swatchSize,
            swTop + swatchSize,
            2f * density,
            2f * density,
            objectPaint
        )
        legendCursorX += swatchSize + 3.5f * density
        textPaint.color = if (obj.chartLegendTextColor != 0 || obj.textColor != 0) {
            legendFg
        } else if (isThisSeriesSelected) {
            sColor
        } else {
            legendFg
        }
        canvas.drawText(sName, legendCursorX, legendBaseline, textPaint)
        legendCursorX += textPaint.measureText(sName) + 10f * density
    }

    val allValues = seriesList.flatMap { it.values }
    val minVal = obj.yAxisMin ?: (allValues.minOrNull() ?: 0f).coerceAtMost(0f)
    val maxVal = (obj.yAxisMax ?: (allValues.maxOrNull() ?: 100f)).coerceAtLeast(minVal + 1f)
    val valSpan = (maxVal - minVal).coerceAtLeast(1f)
    val activeXLabels = if (selectedSeriesIdx != null && seriesList[selectedSeriesIdx].xLabels.isNotEmpty()) {
        seriesList[selectedSeriesIdx].xLabels
    } else if (seriesList.firstOrNull()?.xLabels?.isNotEmpty() == true) {
        seriesList.first().xLabels
    } else {
        obj.chartLabels
    }
    val catCount = max(
        activeXLabels.size,
        seriesList.maxOfOrNull { it.values.size } ?: 0
    ).coerceAtLeast(1)

    val isRadialOrTile = obj.subType in setOf(
        "pie", "pie_exploded", "doughnut", "sunburst", "treemap",
        "radar", "radar_markers", "radar_filled", "funnel"
    )
    val isHorizontalBar = obj.subType in setOf("bar_h", "bar_stacked", "bar_stacked100")

    val yAxisLabelWidth = if (isRadialOrTile) {
        8f * density
    } else {
        (if (maxVal >= 10_000f) 46f else 36f) * density * zoomScale
    }
    val xAxisLabelHeight = if (isRadialOrTile) 6f * density else 18f * density * zoomScale
    val plotTop = legendBaseline + fmLegend.descent + 6f * density
    val plotBottom = rect.bottom - pad - xAxisLabelHeight
    val plotLeft = rect.left + pad + yAxisLabelWidth
    val plotRight = rect.right - pad - 8f * density

    fun drawYGridAndLabels(effectiveMin: Float = minVal, effectiveSpan: Float = valSpan) {
        textPaint.textSize = 8.5f * density * zoomScale
        textPaint.typeface = if (subElem == "yAxis") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val plotHeightPx = (plotBottom - plotTop).coerceAtLeast(32f)
        val minLabelGapPx = (10f * density).coerceAtLeast(8f)
        val maxReadableTicks = (plotHeightPx / minLabelGapPx).toInt().coerceIn(5, 60)

        val rawCustomStep = obj.yAxisMajorStep
        val tickValues: List<Float> = when {
            rawCustomStep != null && rawCustomStep < 0f -> {
                // Negative yAxisMajorStep (-4f, -5f, -8f, -10f, -20f) = exact number of equal axis divisions!
                val divs = abs(rawCustomStep).roundToInt().coerceIn(1, 100)
                (0..divs).map { t -> effectiveMin + (t.toFloat() / divs) * effectiveSpan }
            }
            rawCustomStep != null && rawCustomStep > 0f -> {
                val rawTickCount = (effectiveSpan / rawCustomStep).toDouble()
                // Never cut off partway up the axis! If rawTickCount exceeds the vertical pixel capacity,
                // automatically scale by a clean 1-2-5-10 power-of-10 multiple of rawCustomStep so ticks
                // span the entire [effectiveMin .. effectiveMin + effectiveSpan] range from bottom to top.
                val effectiveStep = if (rawTickCount <= maxReadableTicks * 1.5) {
                    rawCustomStep
                } else {
                    val rawStride = kotlin.math.ceil(rawTickCount / maxReadableTicks.toDouble()).toFloat().coerceAtLeast(1f)
                    val mag = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(rawStride.toDouble()))).toFloat().coerceAtLeast(1f)
                    val norm = rawStride / mag
                    val niceMult = when {
                        norm <= 1f -> 1f
                        norm <= 2f -> 2f
                        norm <= 2.5f -> 2.5f
                        norm <= 5f -> 5f
                        else -> 10f
                    } * mag
                    rawCustomStep * niceMult
                }
                val list = mutableListOf<Float>()
                var cur = effectiveMin
                val maxEnd = effectiveMin + effectiveSpan + effectiveStep * 0.01f
                while (cur <= maxEnd && list.size <= 200) {
                    list.add(cur)
                    cur += effectiveStep
                }
                val topVal = effectiveMin + effectiveSpan
                if (list.isEmpty()) {
                    list.add(effectiveMin)
                    list.add(topVal)
                } else if (topVal - list.last() > effectiveStep * 0.15f) {
                    list.add(topVal)
                }
                list
            }
            else -> {
                val ticks = 4
                (0..ticks).map { t -> effectiveMin + (t.toFloat() / ticks) * effectiveSpan }
            }
        }

        var lastDrawnLabelY = Float.MAX_VALUE
        for ((idx, tickVal) in tickValues.withIndex()) {
            val ratio = ((tickVal - effectiveMin) / effectiveSpan).coerceIn(0f, 1f)
            val gy = plotBottom - ratio * (plotBottom - plotTop)
            objectPaint.style = Paint.Style.STROKE
            objectPaint.strokeWidth = 1f * density
            objectPaint.color = axisGridCol
            canvas.drawLine(plotLeft, gy, plotRight, gy, objectPaint)

            val isLast = idx == tickValues.lastIndex
            if (idx == 0 || isLast || abs(lastDrawnLabelY - gy) >= minLabelGapPx) {
                val tickStr = formatChartValueCompact(tickVal)
                textPaint.color = if (obj.chartYAxisTextColor != 0 || obj.textColor != 0) {
                    yAxisFg
                } else if (subElem == "yAxis") {
                    if (isDarkTheme) 0xFFFBBF24.toInt() else 0xFFB45309.toInt()
                } else {
                    yAxisFg
                }
                val tw = textPaint.measureText(tickStr)
                canvas.drawText(
                    tickStr,
                    (plotLeft - 4f * density - tw).coerceAtLeast(rect.left + 2f * density),
                    gy + 3f * density,
                    textPaint
                )
                lastDrawnLabelY = gy
            }
        }
    }

    fun drawXCategoryLabels(slotW: Float) {
        textPaint.textSize = (if (catCount > 12) 8.2f else 9f) * density * zoomScale
        textPaint.typeface = if (subElem == "xAxis") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        var lastRightX = -Float.MAX_VALUE
        val minGapPx = 3.5f * density
        val explicitInterval = when {
            obj.xAxisLabelInterval > 0 -> obj.xAxisLabelInterval
            obj.xAxisMajorStep != null && obj.xAxisMajorStep > 0f -> obj.xAxisMajorStep.roundToInt().coerceAtLeast(1)
            else -> 0
        }
        for (cIdx in 0 until catCount) {
            if (explicitInterval > 0 && cIdx % explicitInterval != 0) continue
            val lbl = activeXLabels.getOrNull(cIdx).orEmpty().ifEmpty { "${cIdx + 1}" }
            val numLbl = lbl.replace(",", "").toFloatOrNull()
            if (numLbl != null) {
                if (obj.xAxisMin != null && numLbl < obj.xAxisMin - 1e-4f) continue
                if (obj.xAxisMax != null && numLbl > obj.xAxisMax + 1e-4f) continue
            }
            val cx = if (catCount == 1) (plotLeft + plotRight) * 0.5f else plotLeft + (cIdx + 0.5f) * slotW
            textPaint.color = if (obj.chartXAxisTextColor != 0 || obj.textColor != 0) {
                xAxisFg
            } else if (subElem == "xAxis") {
                if (isDarkTheme) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()
            } else {
                xAxisFg
            }
            val lw = textPaint.measureText(lbl)
            val leftX = cx - lw * 0.5f
            if (explicitInterval == 1 || leftX >= lastRightX + minGapPx || cIdx == 0) {
                canvas.drawText(lbl, leftX, plotBottom + 11.5f * density, textPaint)
                lastRightX = leftX + lw
            }
        }
    }

    if (plotBottom > plotTop + 12f && plotRight > plotLeft + 16f) {
        when (obj.subType) {
            "pie", "pie_exploded", "doughnut", "sunburst" -> {
                val values = seriesList.firstOrNull()?.values ?: listOf(42f, 68f, 55f, 84f)
                val diameter = min((plotRight - plotLeft) * 0.58f, plotBottom - plotTop).coerceAtLeast(20f)
                val cx = plotLeft + diameter * 0.55f
                val cy = (plotTop + plotBottom) * 0.5f
                val radius = diameter * 0.5f
                val sum = values.map { abs(it) }.sum().coerceAtLeast(1f)
                var startAngle = -90f
                objectPaint.style = Paint.Style.FILL
                for (i in values.indices) {
                    val sweep = (abs(values[i]) / sum) * 360f
                    val midRad = Math.toRadians((startAngle + sweep * 0.5f).toDouble())
                    val explodeOffset = if (obj.subType == "pie_exploded") 4.5f * density else 0f
                    val ox = (cos(midRad) * explodeOffset).toFloat()
                    val oy = (sin(midRad) * explodeOffset).toFloat()
                    val arcRect = RectF(cx - radius + ox, cy - radius + oy, cx + radius + ox, cy + radius + oy)
                    objectPaint.color = palette[i % palette.size]
                    canvas.drawArc(arcRect, startAngle, (sweep - 1.2f).coerceAtLeast(1f), true, objectPaint)
                    startAngle += sweep
                }
                if (obj.subType == "sunburst" && seriesList.size > 1) {
                    val innerVals = seriesList[1].values
                    val innerSum = innerVals.map { abs(it) }.sum().coerceAtLeast(1f)
                    val innerR = radius * 0.65f
                    val innerRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
                    var sa2 = -90f
                    for (i in innerVals.indices) {
                        val sw2 = (abs(innerVals[i]) / innerSum) * 360f
                        objectPaint.color = palette[(i + 2) % palette.size]
                        canvas.drawArc(innerRect, sa2, (sw2 - 1.5f).coerceAtLeast(1f), true, objectPaint)
                        sa2 += sw2
                    }
                }
                if (obj.subType == "doughnut" || obj.subType == "sunburst") {
                    objectPaint.style = Paint.Style.FILL
                    objectPaint.color = if (isDarkTheme) 0xFF1E293B.toInt() else 0xFFFFFFFF.toInt()
                    val holeR = if (obj.subType == "sunburst") radius * 0.32f else radius * 0.48f
                    canvas.drawCircle(cx, cy, holeR, objectPaint)
                }
                // Draw category + value list on the right of the pie/doughnut
                val listLeft = (cx + radius + 14f * density).coerceAtMost(rect.right - 65f * density)
                textPaint.textSize = 9f * density * zoomScale
                textPaint.typeface = Typeface.DEFAULT
                val rowH = 13f * density * zoomScale
                for (i in 0 until min(catCount, 6)) {
                    val itemY = plotTop + (i + 1) * rowH
                    if (itemY > rect.bottom - pad) break
                    objectPaint.style = Paint.Style.FILL
                    objectPaint.color = palette[i % palette.size]
                    canvas.drawCircle(listLeft + 4f * density, itemY - 3.5f * density, 3.5f * density, objectPaint)
                    val catLbl = obj.chartLabels.getOrNull(i).orEmpty().ifEmpty { "項目${i + 1}" }
                    val v = values.getOrElse(i) { 0f }
                    val pct = ((abs(v) / sum) * 100f).roundToInt()
                    textPaint.color = primaryFg
                    canvas.drawText("$catLbl: ${formatChartValueCompact(v)} ($pct%)", listLeft + 11f * density, itemY, textPaint)
                }
            }

            "treemap" -> {
                val values = seriesList.firstOrNull()?.values ?: listOf(45f, 30f, 25f, 20f)
                val sum = values.map { abs(it) }.sum().coerceAtLeast(1f)
                var curLeft = plotLeft
                var curTop = plotTop
                val totalW = plotRight - plotLeft
                val totalH = plotBottom - plotTop
                for (i in values.indices) {
                    val ratio = (abs(values[i]) / sum).coerceIn(0.08f, 1f)
                    val isHoriz = i % 2 == 0
                    val boxW = if (i == values.lastIndex) (plotRight - curLeft) else if (isHoriz) (totalW * ratio).coerceAtMost(plotRight - curLeft - 8f) else (plotRight - curLeft)
                    val boxH = if (i == values.lastIndex) (plotBottom - curTop) else if (!isHoriz) (totalH * ratio).coerceAtMost(plotBottom - curTop - 8f) else (plotBottom - curTop)
                    val rRight = (curLeft + boxW).coerceAtMost(plotRight)
                    val rBottom = (curTop + boxH).coerceAtMost(plotBottom)
                    objectPaint.style = Paint.Style.FILL
                    objectPaint.color = palette[i % palette.size]
                    canvas.drawRoundRect(curLeft + 1.5f, curTop + 1.5f, rRight - 1.5f, rBottom - 1.5f, 4f * density, 4f * density, objectPaint)
                    val lbl = obj.chartLabels.getOrNull(i).orEmpty().ifEmpty { "項目${i + 1}" }
                    textPaint.color = 0xFFFFFFFF.toInt()
                    textPaint.textSize = 9f * density * zoomScale
                    textPaint.typeface = Typeface.DEFAULT_BOLD
                    if (rRight - curLeft > 28f * density && rBottom - curTop > 18f * density) {
                        canvas.drawText("$lbl (${formatChartValueCompact(values[i])})", curLeft + 5f * density, curTop + 13f * density, textPaint)
                    }
                    if (isHoriz) curLeft = rRight else curTop = rBottom
                    if (curLeft >= plotRight - 6f || curTop >= plotBottom - 6f) break
                }
            }

            "funnel" -> {
                val values = seriesList.firstOrNull()?.values ?: listOf(100f, 78f, 54f, 32f)
                val maxF = (values.maxOrNull() ?: 100f).coerceAtLeast(1f)
                val stageCount = values.size.coerceAtLeast(1)
                val rowH = (plotBottom - plotTop) / stageCount
                val centerX = (plotLeft + plotRight) * 0.5f
                val maxHalfW = (plotRight - plotLeft) * 0.44f
                for (i in values.indices) {
                    val ratio = (abs(values[i]) / maxF).coerceIn(0.15f, 1f)
                    val halfW = maxHalfW * ratio
                    val topY = plotTop + i * rowH + 1.5f * density
                    val botY = plotTop + (i + 1) * rowH - 1.5f * density
                    objectPaint.style = Paint.Style.FILL
                    objectPaint.color = palette[i % palette.size]
                    canvas.drawRoundRect(centerX - halfW, topY, centerX + halfW, botY, 4f * density, 4f * density, objectPaint)
                    val lbl = obj.chartLabels.getOrNull(i).orEmpty().ifEmpty { "段階${i + 1}" }
                    val txt = "$lbl: ${formatChartValueCompact(values[i])}"
                    textPaint.color = 0xFFFFFFFF.toInt()
                    textPaint.textSize = 8.5f * density * zoomScale
                    textPaint.typeface = Typeface.DEFAULT_BOLD
                    val tw = textPaint.measureText(txt)
                    canvas.drawText(txt, centerX - tw * 0.5f, (topY + botY) * 0.5f + 3f * density, textPaint)
                }
            }

            "radar", "radar_markers", "radar_filled" -> {
                val cx = (plotLeft + plotRight) * 0.5f
                val cy = (plotTop + plotBottom) * 0.5f
                val radius = (min(plotRight - plotLeft, plotBottom - plotTop) * 0.42f).coerceAtLeast(16f)
                val axes = catCount.coerceAtLeast(3)
                objectPaint.style = Paint.Style.STROKE
                objectPaint.strokeWidth = 1f * density
                objectPaint.color = axisGridCol
                for (ring in 1..3) {
                    val r = radius * (ring / 3f)
                    objectPath.reset()
                    for (a in 0 until axes) {
                        val ang = Math.toRadians((-90.0 + 360.0 * a / axes))
                        val px = cx + (cos(ang) * r).toFloat()
                        val py = cy + (sin(ang) * r).toFloat()
                        if (a == 0) objectPath.moveTo(px, py) else objectPath.lineTo(px, py)
                    }
                    objectPath.close()
                    canvas.drawPath(objectPath, objectPaint)
                }
                textPaint.textSize = 8.5f * density * zoomScale
                textPaint.typeface = Typeface.DEFAULT
                textPaint.color = primaryFg
                for (a in 0 until axes) {
                    val ang = Math.toRadians((-90.0 + 360.0 * a / axes))
                    val ax = cx + (cos(ang) * radius).toFloat()
                    val ay = cy + (sin(ang) * radius).toFloat()
                    canvas.drawLine(cx, cy, ax, ay, objectPaint)
                    val lbl = obj.chartLabels.getOrNull(a).orEmpty().ifEmpty { "軸${a + 1}" }
                    val lx = cx + (cos(ang) * (radius + 11f * density)).toFloat()
                    val ly = cy + (sin(ang) * (radius + 9f * density)).toFloat()
                    val lw = textPaint.measureText(lbl)
                    canvas.drawText(lbl, lx - lw * 0.5f, ly + 3f * density, textPaint)
                }
                for ((sIdx, series) in seriesList.withIndex()) {
                    val sColor = if (series.color != 0) series.color else palette[sIdx % palette.size]
                    objectPath.reset()
                    for (a in 0 until axes) {
                        val v = series.values.getOrElse(a) { 0f }
                        val ratio = ((v - minVal) / valSpan).coerceIn(0.1f, 1f)
                        val ang = Math.toRadians((-90.0 + 360.0 * a / axes))
                        val px = cx + (cos(ang) * radius * ratio).toFloat()
                        val py = cy + (sin(ang) * radius * ratio).toFloat()
                        if (a == 0) objectPath.moveTo(px, py) else objectPath.lineTo(px, py)
                    }
                    objectPath.close()
                    if (obj.subType == "radar_filled") {
                        objectPaint.style = Paint.Style.FILL
                        objectPaint.color = (sColor and 0x00FFFFFF) or 0x55000000
                        canvas.drawPath(objectPath, objectPaint)
                    }
                    objectPaint.style = Paint.Style.STROKE
                    objectPaint.strokeWidth = 2f * density
                    objectPaint.color = sColor
                    canvas.drawPath(objectPath, objectPaint)
                    if (obj.subType == "radar_markers") {
                        objectPaint.style = Paint.Style.FILL
                        for (a in 0 until axes) {
                            val v = series.values.getOrElse(a) { 0f }
                            val ratio = ((v - minVal) / valSpan).coerceIn(0.1f, 1f)
                            val ang = Math.toRadians((-90.0 + 360.0 * a / axes))
                            val px = cx + (cos(ang) * radius * ratio).toFloat()
                            val py = cy + (sin(ang) * radius * ratio).toFloat()
                            canvas.drawCircle(px, py, 3.2f * density, objectPaint)
                        }
                    }
                }
            }

            "bar_h", "bar_stacked", "bar_stacked100" -> {
                val isStacked = obj.subType == "bar_stacked" || obj.subType == "bar_stacked100"
                val is100 = obj.subType == "bar_stacked100"
                val maxCatSum = (0 until catCount).maxOfOrNull { cIdx ->
                    seriesList.sumOf { abs(it.values.getOrElse(cIdx) { 0f }).toDouble() }.toFloat()
                }?.coerceAtLeast(1f) ?: valSpan
                val horizSpan = if (is100) 100f else if (isStacked) maxCatSum else valSpan

                objectPaint.style = Paint.Style.STROKE
                objectPaint.strokeWidth = 1f * density
                objectPaint.color = axisGridCol
                val ticks = 4
                textPaint.textSize = 8.5f * density * zoomScale
                textPaint.typeface = Typeface.DEFAULT
                for (t in 0..ticks) {
                    val ratio = t.toFloat() / ticks
                    val gx = plotLeft + ratio * (plotRight - plotLeft)
                    canvas.drawLine(gx, plotTop, gx, plotBottom, objectPaint)
                    val tickVal = if (is100) ratio * 100f else (if (isStacked) 0f else minVal) + ratio * horizSpan
                    val tickStr = if (is100) "${tickVal.roundToInt()}%" else formatChartValueCompact(tickVal)
                    textPaint.color = secondaryFg
                    val tw = textPaint.measureText(tickStr)
                    canvas.drawText(tickStr, gx - tw * 0.5f, plotBottom + 11f * density, textPaint)
                }

                val slotH = (plotBottom - plotTop) / catCount
                val sCount = seriesList.size.coerceAtLeast(1)
                val groupH = (slotH * 0.72f).coerceAtLeast(4f)
                val singleBarH = (groupH / sCount).coerceAtLeast(2.5f)
                for (cIdx in 0 until catCount) {
                    val slotTop = plotTop + cIdx * slotH
                    val catLbl = obj.chartLabels.getOrNull(cIdx).orEmpty().ifEmpty { "${cIdx + 1}" }
                    textPaint.color = primaryFg
                    textPaint.textSize = 9f * density * zoomScale
                    val lw = textPaint.measureText(catLbl)
                    canvas.drawText(
                        catLbl,
                        (plotLeft - 4f * density - lw).coerceAtLeast(rect.left + 4f * density),
                        slotTop + slotH * 0.5f + 3f * density,
                        textPaint
                    )
                    if (isStacked) {
                        val catSum = seriesList.sumOf { abs(it.values.getOrElse(cIdx) { 0f }).toDouble() }.toFloat().coerceAtLeast(1f)
                        var curLeft = plotLeft
                        val barTop = slotTop + (slotH - groupH * 0.8f) * 0.5f
                        val barH = (groupH * 0.8f).coerceAtLeast(4f)
                        for ((sIdx, series) in seriesList.withIndex()) {
                            val v = abs(series.values.getOrElse(cIdx) { 0f })
                            val ratio = if (is100) (v / catSum) else (v / horizSpan)
                            val segW = (ratio * (plotRight - plotLeft)).coerceAtLeast(1.5f)
                            val segRight = (curLeft + segW).coerceAtMost(plotRight)
                            objectPaint.style = Paint.Style.FILL
                            objectPaint.color = if (series.color != 0) series.color else palette[sIdx % palette.size]
                            canvas.drawRect(curLeft, barTop, segRight, barTop + barH, objectPaint)
                            curLeft = segRight
                        }
                    } else {
                        for ((sIdx, series) in seriesList.withIndex()) {
                            val v = series.values.getOrElse(cIdx) { 0f }
                            val ratio = ((v - minVal) / valSpan).coerceIn(0.02f, 1f)
                            val barTop = slotTop + (slotH - groupH) * 0.5f + sIdx * singleBarH
                            val barRight = plotLeft + ratio * (plotRight - plotLeft)
                            objectPaint.style = Paint.Style.FILL
                            objectPaint.color = if (series.color != 0) series.color else palette[sIdx % palette.size]
                            canvas.drawRoundRect(plotLeft, barTop, barRight, barTop + singleBarH - 1f, 2.5f * density, 2.5f * density, objectPaint)
                        }
                    }
                }
            }

            "line", "line_markers", "line_stacked", "line_stacked100", "line_smooth",
            "area", "area_stacked", "area_stacked100" -> {
                val isArea = obj.subType.startsWith("area")
                val isStacked = obj.subType.endsWith("stacked") || obj.subType.endsWith("stacked100")
                val is100 = obj.subType.endsWith("stacked100")
                val isSmooth = obj.subType == "line_smooth"
                val showMarkers = obj.subType == "line_markers" || obj.subType == "line"
                val maxCatSum = (0 until catCount).maxOfOrNull { cIdx ->
                    seriesList.sumOf { abs(it.values.getOrElse(cIdx) { 0f }).toDouble() }.toFloat()
                }?.coerceAtLeast(1f) ?: valSpan
                val effMin = if (is100 || isStacked) 0f else minVal
                val effSpan = if (is100) 100f else if (isStacked) maxCatSum else valSpan

                drawYGridAndLabels(effMin, effSpan)
                val slotW = (plotRight - plotLeft) / catCount
                drawXCategoryLabels(slotW)

                val cumulativeHeights = FloatArray(catCount) { 0f }
                for ((sIdx, series) in seriesList.withIndex()) {
                    val sColor = if (series.color != 0) series.color else palette[sIdx % palette.size]
                    val vals = series.values
                    if (vals.isEmpty()) continue
                    val ptsX = FloatArray(catCount)
                    val ptsY = FloatArray(catCount)
                    val prevY = FloatArray(catCount)
                    for (cIdx in 0 until catCount) {
                        ptsX[cIdx] = if (catCount == 1) (plotLeft + plotRight) * 0.5f else plotLeft + (cIdx + 0.5f) * slotW
                        val rawV = vals.getOrElse(cIdx) { 0f }
                        val catSum = seriesList.sumOf { abs(it.values.getOrElse(cIdx) { 0f }).toDouble() }.toFloat().coerceAtLeast(1f)
                        val prevRatio = cumulativeHeights[cIdx]
                        val addRatio = when {
                            is100 -> (abs(rawV) / catSum)
                            isStacked -> (abs(rawV) / effSpan)
                            else -> ((rawV - effMin) / effSpan)
                        }
                        val newRatio = if (isStacked) (prevRatio + addRatio) else addRatio
                        prevY[cIdx] = plotBottom - prevRatio * (plotBottom - plotTop)
                        ptsY[cIdx] = plotBottom - newRatio * (plotBottom - plotTop)
                        if (isStacked) cumulativeHeights[cIdx] = newRatio
                    }

                    if (isArea && catCount >= 2) {
                        canvas.save()
                        canvas.clipRect(plotLeft - 6f * density, plotTop, plotRight + 6f * density, plotBottom)
                        objectPath.reset()
                        objectPath.moveTo(ptsX[0], prevY[0])
                        for (i in 0 until catCount) objectPath.lineTo(ptsX[i], ptsY[i])
                        for (i in catCount - 1 downTo 0) objectPath.lineTo(ptsX[i], prevY[i])
                        objectPath.close()
                        objectPaint.style = Paint.Style.FILL
                        objectPaint.color = (sColor and 0x00FFFFFF) or 0x55000000
                        canvas.drawPath(objectPath, objectPaint)
                        canvas.restore()
                    }

                    val isSelectedLine = selectedSeriesIdx == sIdx
                    objectPaint.style = Paint.Style.STROKE
                    objectPaint.strokeWidth = (if (isSelectedLine) 3.4f else 2.2f) * density
                    objectPaint.color = sColor
                    if (isSmooth) {
                        canvas.save()
                        canvas.clipRect(plotLeft - 6f * density, plotTop, plotRight + 6f * density, plotBottom)
                        objectPath.reset()
                        var hasMoved = false
                        for (i in 1 until catCount) {
                            val y0 = ptsY[i - 1]
                            val y1 = ptsY[i]
                            if ((y0 < plotTop && y1 < plotTop) || (y0 > plotBottom && y1 > plotBottom)) {
                                hasMoved = false
                                continue
                            }
                            if (!hasMoved) {
                                objectPath.moveTo(ptsX[i - 1], y0)
                                hasMoved = true
                            }
                            val midX = (ptsX[i - 1] + ptsX[i]) * 0.5f
                            objectPath.cubicTo(midX, y0, midX, y1, ptsX[i], y1)
                        }
                        canvas.drawPath(objectPath, objectPaint)
                        canvas.restore()
                    } else {
                        objectPath.reset()
                        var lastEndX = Float.NaN
                        var lastEndY = Float.NaN
                        for (i in 1 until catCount) {
                            val x0 = ptsX[i - 1]
                            val y0 = ptsY[i - 1]
                            val x1 = ptsX[i]
                            val y1 = ptsY[i]
                            if ((y0 < plotTop && y1 < plotTop) || (y0 > plotBottom && y1 > plotBottom)) {
                                lastEndX = Float.NaN
                                lastEndY = Float.NaN
                                continue
                            }
                            var cx0 = x0
                            var cy0 = y0
                            var cx1 = x1
                            var cy1 = y1
                            val dy = y1 - y0
                            if (abs(dy) > 1e-4f) {
                                if (cy0 < plotTop) {
                                    val t = (plotTop - y0) / dy
                                    cx0 = x0 + t * (x1 - x0)
                                    cy0 = plotTop
                                } else if (cy0 > plotBottom) {
                                    val t = (plotBottom - y0) / dy
                                    cx0 = x0 + t * (x1 - x0)
                                    cy0 = plotBottom
                                }
                                if (cy1 < plotTop) {
                                    val t = (plotTop - y0) / dy
                                    cx1 = x0 + t * (x1 - x0)
                                    cy1 = plotTop
                                } else if (cy1 > plotBottom) {
                                    val t = (plotBottom - y0) / dy
                                    cx1 = x0 + t * (x1 - x0)
                                    cy1 = plotBottom
                                }
                            }
                            if (lastEndX.isNaN() || abs(cx0 - lastEndX) > 0.25f || abs(cy0 - lastEndY) > 0.25f) {
                                objectPath.moveTo(cx0, cy0)
                            }
                            objectPath.lineTo(cx1, cy1)
                            lastEndX = cx1
                            lastEndY = cy1
                        }
                        canvas.drawPath(objectPath, objectPaint)
                    }

                    if (showMarkers || isSelectedLine) {
                        for (i in 0 until catCount) {
                            if (ptsY[i] < plotTop - 0.5f || ptsY[i] > plotBottom + 0.5f) continue
                            if (isSelectedLine) {
                                objectPaint.style = Paint.Style.FILL
                                objectPaint.color = 0xFFFFFFFF.toInt()
                                canvas.drawCircle(ptsX[i], ptsY[i], 5.2f * density, objectPaint)
                                objectPaint.style = Paint.Style.STROKE
                                objectPaint.strokeWidth = 2.2f * density
                                objectPaint.color = sColor
                                canvas.drawCircle(ptsX[i], ptsY[i], 5.2f * density, objectPaint)
                            } else {
                                objectPaint.style = Paint.Style.FILL
                                objectPaint.color = sColor
                                canvas.drawCircle(ptsX[i], ptsY[i], 3.5f * density, objectPaint)
                            }
                        }
                    }
                }
            }

            "scatter", "scatter_lines", "bubble" -> {
                drawYGridAndLabels()
                val slotW = (plotRight - plotLeft) / catCount
                drawXCategoryLabels(slotW)
                for ((sIdx, series) in seriesList.withIndex()) {
                    val sColor = if (series.color != 0) series.color else palette[sIdx % palette.size]
                    val isSelectedScatter = selectedSeriesIdx == sIdx
                    val vals = series.values
                    if (vals.isEmpty()) continue
                    if ((obj.subType == "scatter_lines" || isSelectedScatter) && vals.size >= 2) {
                        objectPaint.style = Paint.Style.STROKE
                        objectPaint.strokeWidth = (if (isSelectedScatter) 2.8f else 1.8f) * density
                        objectPaint.color = sColor
                        objectPath.reset()
                        var lastEndX = Float.NaN
                        var lastEndY = Float.NaN
                        for (i in 1 until vals.size) {
                            val x0 = plotLeft + (i - 0.5f) * slotW
                            val y0 = plotBottom - ((vals[i - 1] - minVal) / valSpan) * (plotBottom - plotTop)
                            val x1 = plotLeft + (i + 0.5f) * slotW
                            val y1 = plotBottom - ((vals[i] - minVal) / valSpan) * (plotBottom - plotTop)
                            if ((y0 < plotTop && y1 < plotTop) || (y0 > plotBottom && y1 > plotBottom)) {
                                lastEndX = Float.NaN
                                lastEndY = Float.NaN
                                continue
                            }
                            var cx0 = x0
                            var cy0 = y0
                            var cx1 = x1
                            var cy1 = y1
                            val dy = y1 - y0
                            if (abs(dy) > 1e-4f) {
                                if (cy0 < plotTop) {
                                    val t = (plotTop - y0) / dy
                                    cx0 = x0 + t * (x1 - x0)
                                    cy0 = plotTop
                                } else if (cy0 > plotBottom) {
                                    val t = (plotBottom - y0) / dy
                                    cx0 = x0 + t * (x1 - x0)
                                    cy0 = plotBottom
                                }
                                if (cy1 < plotTop) {
                                    val t = (plotTop - y0) / dy
                                    cx1 = x0 + t * (x1 - x0)
                                    cy1 = plotTop
                                } else if (cy1 > plotBottom) {
                                    val t = (plotBottom - y0) / dy
                                    cx1 = x0 + t * (x1 - x0)
                                    cy1 = plotBottom
                                }
                            }
                            if (lastEndX.isNaN() || abs(cx0 - lastEndX) > 0.25f || abs(cy0 - lastEndY) > 0.25f) {
                                objectPath.moveTo(cx0, cy0)
                            }
                            objectPath.lineTo(cx1, cy1)
                            lastEndX = cx1
                            lastEndY = cy1
                        }
                        canvas.drawPath(objectPath, objectPaint)
                    }
                    for (i in vals.indices) {
                        val ratio = (vals[i] - minVal) / valSpan
                        val px = plotLeft + (i + 0.5f) * slotW
                        val py = plotBottom - ratio * (plotBottom - plotTop)
                        if (py < plotTop - 0.5f || py > plotBottom + 0.5f) continue
                        val r = if (obj.subType == "bubble") (4f + ratio.coerceIn(0f, 1f) * 9f) * density else (if (isSelectedScatter) 5.4f else 4.2f) * density
                        objectPaint.style = Paint.Style.FILL
                        objectPaint.color = if (obj.subType == "bubble") ((sColor and 0x00FFFFFF) or 0x99000000.toInt()) else sColor
                        canvas.drawCircle(px, py, r, objectPaint)
                        if (obj.subType == "bubble" || isSelectedScatter) {
                            objectPaint.style = Paint.Style.STROKE
                            objectPaint.strokeWidth = (if (isSelectedScatter) 2.2f else 1.2f) * density
                            objectPaint.color = if (isSelectedScatter) 0xFFFFFFFF.toInt() else sColor
                            canvas.drawCircle(px, py, r, objectPaint)
                        }
                    }
                }
            }

            "box_whisker", "stock" -> {
                drawYGridAndLabels()
                val slotW = (plotRight - plotLeft) / catCount
                drawXCategoryLabels(slotW)
                for (cIdx in 0 until catCount) {
                    val cx = plotLeft + (cIdx + 0.5f) * slotW
                    val baseV = seriesList.firstOrNull()?.values?.getOrElse(cIdx) { 50f } ?: 50f
                    val secondV = seriesList.getOrNull(1)?.values?.getOrElse(cIdx) { baseV * 0.75f } ?: (baseV * 0.75f)
                    val highV = max(baseV, secondV) + valSpan * 0.12f
                    val lowV = (min(baseV, secondV) - valSpan * 0.12f).coerceAtLeast(minVal)
                    val q3Y = plotBottom - ((max(baseV, secondV) - minVal) / valSpan).coerceIn(0.08f, 0.92f) * (plotBottom - plotTop)
                    val q1Y = plotBottom - ((min(baseV, secondV) - minVal) / valSpan).coerceIn(0.05f, 0.88f) * (plotBottom - plotTop)
                    val highY = plotBottom - ((highV - minVal) / valSpan).coerceIn(0f, 1f) * (plotBottom - plotTop)
                    val lowY = plotBottom - ((lowV - minVal) / valSpan).coerceIn(0f, 1f) * (plotBottom - plotTop)
                    val boxHalfW = (slotW * 0.26f).coerceAtLeast(4f * density)
                    val col = if (obj.subType == "stock" && secondV < baseV) 0xFFEF4444.toInt() else palette[cIdx % palette.size]
                    objectPaint.style = Paint.Style.STROKE
                    objectPaint.strokeWidth = 1.5f * density
                    objectPaint.color = col
                    canvas.drawLine(cx, highY, cx, lowY, objectPaint)
                    canvas.drawLine(cx - boxHalfW * 0.6f, highY, cx + boxHalfW * 0.6f, highY, objectPaint)
                    canvas.drawLine(cx - boxHalfW * 0.6f, lowY, cx + boxHalfW * 0.6f, lowY, objectPaint)
                    objectPaint.style = Paint.Style.FILL
                    objectPaint.color = (col and 0x00FFFFFF) or 0xAA000000.toInt()
                    canvas.drawRoundRect(cx - boxHalfW, min(q3Y, q1Y), cx + boxHalfW, max(q3Y, q1Y) + 2f * density, 2f * density, 2f * density, objectPaint)
                    if (obj.subType == "box_whisker") {
                        val medY = (q3Y + q1Y) * 0.5f
                        objectPaint.color = 0xFFFFFFFF.toInt()
                        canvas.drawLine(cx - boxHalfW, medY, cx + boxHalfW, medY, objectPaint)
                    }
                }
            }

            "waterfall" -> {
                val vals = seriesList.firstOrNull()?.values ?: listOf(40f, 25f, -15f, 50f)
                var running = 0f
                var maxRun = 1f
                var minRun = 0f
                for (v in vals) {
                    running += v
                    maxRun = max(maxRun, running)
                    minRun = min(minRun, running)
                }
                val wfSpan = (maxRun - minRun).coerceAtLeast(1f)
                drawYGridAndLabels(minRun, wfSpan)
                val slotW = (plotRight - plotLeft) / catCount
                drawXCategoryLabels(slotW)
                var cur = 0f
                val barW = (slotW * 0.62f).coerceAtLeast(6f)
                for (cIdx in 0 until catCount) {
                    val v = vals.getOrElse(cIdx) { 0f }
                    val next = cur + v
                    val y0 = plotBottom - ((cur - minRun) / wfSpan).coerceIn(0f, 1f) * (plotBottom - plotTop)
                    val y1 = plotBottom - ((next - minRun) / wfSpan).coerceIn(0f, 1f) * (plotBottom - plotTop)
                    val bLeft = plotLeft + cIdx * slotW + (slotW - barW) * 0.5f
                    objectPaint.style = Paint.Style.FILL
                    objectPaint.color = if (v >= 0f) 0xFF10B981.toInt() else 0xFFEF4444.toInt()
                    canvas.drawRoundRect(bLeft, min(y0, y1), bLeft + barW, max(y0, y1).coerceAtLeast(min(y0, y1) + 3f), 2.5f * density, 2.5f * density, objectPaint)
                    cur = next
                }
            }

            else -> {
                // Column subtypes: "column", "column_stacked", "column_stacked100", "histogram", "pareto", "combo"
                val isStacked = obj.subType == "column_stacked" || obj.subType == "column_stacked100"
                val is100 = obj.subType == "column_stacked100"
                val isHistogram = obj.subType == "histogram" || obj.subType == "pareto"
                val hasLineOverlay = obj.subType == "pareto" || obj.subType == "combo"

                val maxCatSum = (0 until catCount).maxOfOrNull { cIdx ->
                    seriesList.sumOf { abs(it.values.getOrElse(cIdx) { 0f }).toDouble() }.toFloat()
                }?.coerceAtLeast(1f) ?: valSpan
                val effMin = if (is100 || isStacked) 0f else minVal
                val effSpan = if (is100) 100f else if (isStacked) maxCatSum else valSpan

                drawYGridAndLabels(effMin, effSpan)

                val slotW = (plotRight - plotLeft) / catCount
                val barSeriesList = if (obj.subType == "combo" && seriesList.size > 1) seriesList.dropLast(1) else seriesList
                val sCount = barSeriesList.size.coerceAtLeast(1)
                val groupW = if (isHistogram) (slotW * 0.94f).coerceAtLeast(6f) else (slotW * 0.72f).coerceAtLeast(6f)
                val singleBarW = (groupW / sCount).coerceAtLeast(3f)

                for (cIdx in 0 until catCount) {
                    val slotLeft = plotLeft + cIdx * slotW
                    val groupLeft = slotLeft + (slotW - groupW) * 0.5f
                    if (isStacked) {
                        val catSum = barSeriesList.sumOf { abs(it.values.getOrElse(cIdx) { 0f }).toDouble() }.toFloat().coerceAtLeast(1f)
                        var curBottom = plotBottom
                        val barW = (slotW * 0.6f).coerceAtLeast(6f)
                        val bLeft = slotLeft + (slotW - barW) * 0.5f
                        for ((sIdx, series) in barSeriesList.withIndex()) {
                            val v = abs(series.values.getOrElse(cIdx) { 0f })
                            val ratio = if (is100) (v / catSum) else (v / effSpan)
                            val segH = (ratio * (plotBottom - plotTop)).coerceAtLeast(2f)
                            val bTop = (curBottom - segH).coerceAtLeast(plotTop)
                            objectPaint.style = Paint.Style.FILL
                            objectPaint.color = if (series.color != 0) series.color else palette[sIdx % palette.size]
                            canvas.drawRect(bLeft, bTop, bLeft + barW, curBottom, objectPaint)
                            curBottom = bTop
                        }
                    } else {
                        for ((sIdx, series) in barSeriesList.withIndex()) {
                            val v = series.values.getOrElse(cIdx) { 0f }
                            val ratio = ((v - minVal) / valSpan).coerceIn(0.02f, 1f)
                            val bLeft = groupLeft + sIdx * singleBarW
                            val bTop = plotBottom - ratio * (plotBottom - plotTop)
                            val sCol = if (series.color != 0) series.color else palette[sIdx % palette.size]
                            objectPaint.style = Paint.Style.FILL
                            objectPaint.color = sCol
                            canvas.drawRoundRect(
                                bLeft,
                                bTop,
                                bLeft + singleBarW - 1f,
                                plotBottom,
                                if (isHistogram) 1f * density else 2.5f * density,
                                if (isHistogram) 1f * density else 2.5f * density,
                                objectPaint
                            )
                            if (selectedSeriesIdx == sIdx) {
                                objectPaint.style = Paint.Style.STROKE
                                objectPaint.strokeWidth = 1.8f * density
                                objectPaint.color = 0xFFFFFFFF.toInt()
                                canvas.drawRoundRect(
                                    bLeft,
                                    bTop,
                                    bLeft + singleBarW - 1f,
                                    plotBottom,
                                    2.5f * density,
                                    2.5f * density,
                                    objectPaint
                                )
                                val cxBar = bLeft + (singleBarW - 1f) * 0.5f
                                objectPaint.style = Paint.Style.FILL
                                objectPaint.color = 0xFFFFFFFF.toInt()
                                canvas.drawCircle(cxBar, bTop, 3.8f * density, objectPaint)
                                objectPaint.style = Paint.Style.STROKE
                                objectPaint.color = sCol
                                canvas.drawCircle(cxBar, bTop, 3.8f * density, objectPaint)
                            }
                            if (sCount == 1 && !hasLineOverlay) {
                                val vStr = formatChartValueCompact(v)
                                textPaint.color = primaryFg
                                textPaint.textSize = 8f * density * zoomScale
                                val vw = textPaint.measureText(vStr)
                                canvas.drawText(vStr, bLeft + (singleBarW - vw) * 0.5f, (bTop - 3f * density).coerceAtLeast(plotTop + 8f), textPaint)
                            }
                        }
                    }
                }
                drawXCategoryLabels(slotW)

                if (hasLineOverlay && catCount >= 2) {
                    val lineColor = 0xFFF59E0B.toInt()
                    objectPaint.style = Paint.Style.STROKE
                    objectPaint.strokeWidth = 2.2f * density
                    objectPaint.color = lineColor
                    objectPath.reset()
                    val firstVals = seriesList.last().values
                    val totalSum = firstVals.sumOf { abs(it).toDouble() }.toFloat().coerceAtLeast(1f)
                    var cum = 0f
                    for (cIdx in 0 until catCount) {
                        val px = plotLeft + (cIdx + 0.5f) * slotW
                        val v = firstVals.getOrElse(cIdx) { 0f }
                        cum += abs(v)
                        val ratio = if (obj.subType == "pareto") (cum / totalSum).coerceIn(0f, 1f) else ((v - minVal) / valSpan).coerceIn(0f, 1f)
                        val py = plotBottom - ratio * (plotBottom - plotTop)
                        if (cIdx == 0) objectPath.moveTo(px, py) else objectPath.lineTo(px, py)
                    }
                    canvas.drawPath(objectPath, objectPaint)
                    objectPaint.style = Paint.Style.FILL
                    cum = 0f
                    for (cIdx in 0 until catCount) {
                        val px = plotLeft + (cIdx + 0.5f) * slotW
                        val v = firstVals.getOrElse(cIdx) { 0f }
                        cum += abs(v)
                        val ratio = if (obj.subType == "pareto") (cum / totalSum).coerceIn(0f, 1f) else ((v - minVal) / valSpan).coerceIn(0f, 1f)
                        val py = plotBottom - ratio * (plotBottom - plotTop)
                        canvas.drawCircle(px, py, 3.5f * density, objectPaint)
                    }
                }
            }
        }

        // 4. Whole-Axis Selection Frame ("xAxis" or "yAxis") or Selected Series Info Badge ("series:k")
        if (obj.id == selectedObjectId && (subElem == "xAxis" || subElem == "yAxis" || selectedSeriesIdx != null)) {
            fun drawSubSelectionBox(box: RectF, accentCol: Int) {
                checkboxBoxPaint.style = Paint.Style.FILL
                checkboxBoxPaint.color = (accentCol and 0x00FFFFFF) or 0x24000000
                canvas.drawRoundRect(box, 4f * density, 4f * density, checkboxBoxPaint)
                objectPaint.style = Paint.Style.STROKE
                objectPaint.strokeWidth = 1.9f * density
                objectPaint.color = accentCol
                canvas.drawRoundRect(box, 4f * density, 4f * density, objectPaint)
                val hs = 3.5f * density
                checkboxBoxPaint.color = accentCol
                canvas.drawRect(box.left - hs, box.top - hs, box.left + hs, box.top + hs, checkboxBoxPaint)
                canvas.drawRect(box.right - hs, box.top - hs, box.right + hs, box.top + hs, checkboxBoxPaint)
                canvas.drawRect(box.left - hs, box.bottom - hs, box.left + hs, box.bottom + hs, checkboxBoxPaint)
                canvas.drawRect(box.right - hs, box.bottom - hs, box.right + hs, box.bottom + hs, checkboxBoxPaint)
            }

            val infoBadgeText: String
            val infoBadgeCol: Int
            val isEnBadge = viewModel.uiState.value.language == "en"
            when {
                subElem == "xAxis" -> {
                    val xBox = if (isHorizontalBar) {
                        RectF(rect.left + 2f * density, plotTop - 2f * density, plotLeft + 2f * density, plotBottom + 2f * density)
                    } else {
                        RectF(plotLeft - 3f * density, plotBottom - 1f * density, plotRight + 3f * density, rect.bottom - 2f * density)
                    }
                    drawSubSelectionBox(xBox, 0xFF0284C7.toInt())
                    val ivDesc = if (obj.xAxisLabelInterval > 0) {
                        if (isEnBadge) "Every ${obj.xAxisLabelInterval}" else "${obj.xAxisLabelInterval}おき"
                    } else {
                        if (isEnBadge) "Auto" else "自動"
                    }
                    infoBadgeText = if (isEnBadge) {
                        "📏 X-Axis Selected (Interval: $ivDesc | Right-click to adjust)"
                    } else {
                        "📏 X軸選択中 (間隔:$ivDesc｜右クリックで調整)"
                    }
                    infoBadgeCol = 0xFF0284C7.toInt()
                }
                subElem == "yAxis" -> {
                    val yBox = if (isHorizontalBar) {
                        RectF(plotLeft - 3f * density, plotBottom - 1f * density, plotRight + 3f * density, rect.bottom - 2f * density)
                    } else {
                        RectF(rect.left + 2f * density, plotTop - 2f * density, plotLeft + 2f * density, plotBottom + 2f * density)
                    }
                    drawSubSelectionBox(yBox, 0xFFD97706.toInt())
                    val stepDesc = obj.yAxisMajorStep?.let {
                        if (it < 0f) {
                            if (isEnBadge) "${abs(it).roundToInt()} divs" else "${abs(it).roundToInt()}分割"
                        } else {
                            formatChartValueCompact(it)
                        }
                    } ?: if (isEnBadge) "Auto" else "自動"
                    infoBadgeText = if (isEnBadge) {
                        "📐 Y-Axis Selected (Step: $stepDesc | Right-click to adjust)"
                    } else {
                        "📐 Y軸選択中 (目盛:$stepDesc｜右クリックで調整)"
                    }
                    infoBadgeCol = 0xFFD97706.toInt()
                }
                else -> {
                    val sIdx = selectedSeriesIdx ?: 0
                    val s = seriesList[sIdx]
                    val xRef = viewModel.resolveChartSeriesAxisRangeText(obj, sIdx, isXAxis = true)
                    val yRef = viewModel.resolveChartSeriesAxisRangeText(obj, sIdx, isXAxis = false)
                    val rawSName = s.name.ifBlank { if (isEnBadge) "Series ${sIdx + 1}" else "系列${sIdx + 1}" }
                    val sName = SpreadsheetI18n.translateChartDisplayText(rawSName, isEnBadge)
                    infoBadgeText = if (isEnBadge) {
                        "📈 $sName | X-Axis: $xRef | Y-Axis: $yRef"
                    } else {
                        "📈 $sName ｜ X軸:$xRef ｜ Y軸:$yRef"
                    }
                    infoBadgeCol = if (s.color != 0) s.color else palette[sIdx % palette.size]
                }
            }

            textPaint.textSize = 8.8f * density * zoomScale.coerceIn(0.85f, 1.25f)
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val bw = (textPaint.measureText(infoBadgeText) + 12f * density).coerceAtMost(rect.width() - 8f * density)
            val bh = 15.5f * density
            val bx = (rect.right - bw - 5f * density).coerceAtLeast(rect.left + 4f * density)
            val by = rect.top + 4f * density
            val pill = RectF(bx, by, bx + bw, by + bh)
            checkboxBoxPaint.style = Paint.Style.FILL
            checkboxBoxPaint.color = infoBadgeCol
            canvas.drawRoundRect(pill, 4f * density, 4f * density, checkboxBoxPaint)
            textPaint.color = 0xFFFFFFFF.toInt()
            val fm = textPaint.fontMetrics
            canvas.drawText(
                infoBadgeText,
                pill.left + 6f * density,
                pill.centerY() - (fm.descent + fm.ascent) * 0.5f,
                textPaint
            )
        }
    }
    canvas.restoreToCount(clipSave)
}

/**
 * Resolves which specific text or data element inside a Chart was double-clicked so [inCellEditText]
 * opens directly over that exact sub-element (Title, Legend Series Name, X-Axis Category Label, or Data Value)!
 */
fun MasuSheetNativeGridView.resolveChartSubElementAt(
    obj: NativeSheetObject,
    screenX: Float,
    screenY: Float
): ChartEditableHitTarget {
    val rect = getSheetObjectScreenRect(obj)
    val pad = 8f * density
    val titleBottom = rect.top + 24f * density * zoomScale
    val legendBottom = titleBottom + 16f * density * zoomScale
    val seriesList = effectiveChartSeries(obj)
    val catCount = max(
        obj.chartLabels.size,
        seriesList.maxOfOrNull { it.values.size } ?: 0
    ).coerceAtLeast(1)

    // 1. Top Title Strip
    if (screenY <= titleBottom) {
        return ChartEditableHitTarget(
            targetType = InPlaceObjectEditTargetType.OBJECT_MAIN_TEXT,
            screenBounds = RectF(
                rect.left + pad,
                rect.top + 3f * density,
                (rect.right - pad).coerceAtLeast(rect.left + 90f * density),
                titleBottom + 4f * density
            ),
            initialText = obj.title
        )
    }

    // 2. Legend Row (Series Names)
    if (screenY <= legendBottom && seriesList.isNotEmpty()) {
        val slotW = ((rect.width() - pad * 2f) / seriesList.size.coerceAtLeast(1)).coerceAtLeast(48f * density)
        val sIdx = ((screenX - (rect.left + pad)) / slotW).toInt().coerceIn(0, seriesList.size - 1)
        val itemLeft = rect.left + pad + sIdx * slotW
        return ChartEditableHitTarget(
            targetType = InPlaceObjectEditTargetType.CHART_SERIES_NAME,
            subIndex1 = sIdx,
            screenBounds = RectF(
                itemLeft,
                titleBottom - 2f * density,
                (itemLeft + max(slotW, 84f * density)).coerceAtMost(rect.right - 4f * density),
                legendBottom + 6f * density
            ),
            initialText = seriesList[sIdx].name
        )
    }

    val isRadialOrTile = obj.subType in setOf(
        "pie", "pie_exploded", "doughnut", "sunburst", "treemap",
        "radar", "radar_markers", "radar_filled", "funnel"
    )
    val isHorizontalBar = obj.subType in setOf("bar_h", "bar_stacked", "bar_stacked100")

    val yAxisLabelWidth = if (isRadialOrTile) 8f * density else 34f * density * zoomScale
    val xAxisLabelHeight = if (isRadialOrTile) 6f * density else 18f * density * zoomScale
    val plotTop = legendBottom + 4f * density
    val plotBottom = rect.bottom - pad - xAxisLabelHeight
    val plotLeft = rect.left + pad + yAxisLabelWidth
    val plotRight = rect.right - pad - 8f * density

    // 3. Bottom X-Axis Category Labels (or Left Y-Axis Category Labels for horizontal bar)
    if (isHorizontalBar && screenX < plotLeft + 10f * density && screenY >= plotTop) {
        val slotH = ((plotBottom - plotTop) / catCount).coerceAtLeast(16f * density)
        val cIdx = ((screenY - plotTop) / slotH).toInt().coerceIn(0, catCount - 1)
        val top = plotTop + cIdx * slotH
        return ChartEditableHitTarget(
            targetType = InPlaceObjectEditTargetType.CHART_CATEGORY_LABEL,
            subIndex1 = cIdx,
            screenBounds = RectF(
                rect.left + 4f * density,
                top,
                plotLeft + 40f * density,
                top + max(slotH, 26f * density)
            ),
            initialText = obj.chartLabels.getOrNull(cIdx).orEmpty().ifEmpty { "項目${cIdx + 1}" }
        )
    }

    if (screenY >= plotBottom - 4f * density && !isRadialOrTile) {
        val slotW = ((plotRight - plotLeft) / catCount).coerceAtLeast(24f * density)
        val cIdx = ((screenX - plotLeft) / slotW).toInt().coerceIn(0, catCount - 1)
        val left = plotLeft + cIdx * slotW
        return ChartEditableHitTarget(
            targetType = InPlaceObjectEditTargetType.CHART_CATEGORY_LABEL,
            subIndex1 = cIdx,
            screenBounds = RectF(
                left.coerceAtLeast(rect.left + 4f * density),
                plotBottom - 4f * density,
                (left + max(slotW, 76f * density)).coerceAtMost(rect.right - 4f * density),
                rect.bottom - 2f * density
            ),
            initialText = obj.chartLabels.getOrNull(cIdx).orEmpty().ifEmpty { "${cIdx + 1}" }
        )
    }

    // 4. Plot Data Area -> Edit numeric value of the clicked category & series in-place!
    if (seriesList.isNotEmpty() && screenX >= plotLeft && screenY in plotTop..plotBottom) {
        val slotW = ((plotRight - plotLeft) / catCount).coerceAtLeast(20f * density)
        val cIdx = ((screenX - plotLeft) / slotW).toInt().coerceIn(0, catCount - 1)
        val sCount = seriesList.size.coerceAtLeast(1)
        val relWithinSlot = ((screenX - (plotLeft + cIdx * slotW)) / slotW).coerceIn(0f, 0.999f)
        val sIdx = (relWithinSlot * sCount).toInt().coerceIn(0, sCount - 1)
        val curVal = seriesList[sIdx].values.getOrElse(cIdx) { 0f }
        val valText = if (abs(curVal - curVal.roundToInt()) < 0.05f) curVal.roundToInt().toString() else curVal.toString()
        return ChartEditableHitTarget(
            targetType = InPlaceObjectEditTargetType.CHART_DATA_VALUE,
            subIndex1 = sIdx,
            subIndex2 = cIdx,
            screenBounds = RectF(
                (screenX - 40f * density).coerceAtLeast(rect.left + 6f * density),
                (screenY - 14f * density).coerceAtLeast(rect.top + 6f * density),
                (screenX + 44f * density).coerceAtMost(rect.right - 6f * density),
                (screenY + 14f * density).coerceAtMost(rect.bottom - 6f * density)
            ),
            initialText = valText
        )
    }

    // Fallback: edit Chart Title in-place
    return ChartEditableHitTarget(
        targetType = InPlaceObjectEditTargetType.OBJECT_MAIN_TEXT,
        screenBounds = RectF(
            rect.left + pad,
            rect.top + 3f * density,
            (rect.right - pad).coerceAtLeast(rect.left + 90f * density),
            titleBottom + 4f * density
        ),
        initialText = obj.title
    )
}

/**
 * [EN] Grid canvas helper that executes `getOrDecodeObjectBitmap` (get or decode object bitmap) on [MasuSheetNativeGridView].
 * [JA] グリッドキャンバス ([MasuSheetNativeGridView]) における `getOrDecodeObjectBitmap` 処理を実行します。
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
