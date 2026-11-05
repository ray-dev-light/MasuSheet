package com.masusheet.app.viewmodel

import androidx.lifecycle.ViewModel
import com.masusheet.app.catalog.SpreadsheetChartTypeCatalog
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.core.SpreadsheetColorPalette
import com.masusheet.app.model.CustomCellStyleOverride
import com.masusheet.app.model.MasuSheetUiState
import com.masusheet.app.model.NativeViewportSnapshot
import org.json.JSONObject

/**
 * ============================================================================
 * [EN] Unified 292-Command Dispatcher & Synchronized Overlay+Engine Undo Recording
 * Implements [dispatchCommand] normalizing all 292 ribbon, AI, and macro commands (`format.*` ⇄ `home.*`, `data.dataValidation`, Undo/Redo, zoom, styles) across Kotlin overlays and the Rust engine.
 *
 * ---
 * [JA / 日本語解説]
 * リボン・AI・マクロからの全292コマンド (`format.*` と `home.*` の正規化、データ入力規則、Undo/Redo、書式変更等) をKotlinオーバーレイとRustエンジンへ整合的にディスパッチする [dispatchCommand] モジュールです。
 * ============================================================================
 */

/**
 * [EN] Central command router handling all 292 ribbon, keyboard, macro, and AI command IDs across ViewModel overlays and the Rust JNI engine.
 * [JA] リボン・ショートカット・マクロ・AIからの全292コマンドIDを受け取り、Kotlin側オーバーレイ処理とRust JNIエンジン実行を統合ディスパッチします。
 */
internal fun MasuSheetViewModel.dispatchCommand(commandId: String, paramsJson: String = "{}") {
    if (commandId == "file.newBlank") {
        onCreateNewBlankWorkbookSession()
        return
    }
    if (commandId == "app.aboutLicenses") {
        onOpenLicensesDialog(true)
        return
    }
    if (commandId == "home.wrapText" || commandId == "format.wrapText") {
        onToggleWrapText()
        recordMacroStepIfNeeded("format.wrapText", paramsJson)
        return
    }
    if (commandId == "edit.clearContents" && paramsJson == "{}") {
        onClearSelectedCells()
        recordMacroStepIfNeeded("edit.clearContents", paramsJson)
        return
    }

    // Guard empty Undo / Redo so clicking them when nothing is available never triggers a red error banner
    val cur = _uiState.value
    if (commandId == "edit.undo") {
        if (!cur.viewport.canUndo && customOverlayUndoStack.isEmpty()) return
        if (customOverlayUndoStack.isNotEmpty()) {
            val targetSnap = customOverlayUndoStack.removeLast()
            val stepsToUndo = targetSnap.engineStepsCount
            customOverlayRedoStack.addLast(captureCustomOverlaySnapshot(engineStepsCount = stepsToUndo))
            restoreCustomOverlaySnapshot(targetSnap)
            if (stepsToUndo == 0) {
                val decorated = decorateWithCustomSizes(_uiState.value.viewport)
                _uiState.value = _uiState.value.copy(viewport = decorated)
                return
            }
            var snap = _uiState.value.viewport
            repeat(stepsToUndo) {
                snap = engineGateway.executeCommand("edit.undo", "{}")
            }
            applyViewportSnapshot(snap)
            return
        }
        val snap = engineGateway.executeCommand("edit.undo", "{}")
        applyViewportSnapshot(snap)
        return
    }
    if (commandId == "edit.redo") {
        if (!cur.viewport.canRedo && customOverlayRedoStack.isEmpty()) return
        if (customOverlayRedoStack.isNotEmpty()) {
            val targetSnap = customOverlayRedoStack.removeLast()
            val stepsToRedo = targetSnap.engineStepsCount
            customOverlayUndoStack.addLast(captureCustomOverlaySnapshot(engineStepsCount = stepsToRedo))
            restoreCustomOverlaySnapshot(targetSnap)
            if (stepsToRedo == 0) {
                val decorated = decorateWithCustomSizes(_uiState.value.viewport)
                _uiState.value = _uiState.value.copy(viewport = decorated)
                return
            }
            var snap = _uiState.value.viewport
            repeat(stepsToRedo) {
                snap = engineGateway.executeCommand("edit.redo", "{}")
            }
            applyViewportSnapshot(snap)
            return
        }
        val snap = engineGateway.executeCommand("edit.redo", "{}")
        applyViewportSnapshot(snap)
        return
    }

    val preCommandOverlaySnapshot = captureCustomOverlaySnapshot()
    val beforeRawRevision = (cur.viewport.revision - sizeRevisionBump).coerceAtLeast(0L)
    val beforeUndoDepth = cur.viewport.undoDepth

    // Normalize command aliases & update Ribbon Toggle/Selection states
    val pObj = try { JSONObject(paramsJson) } catch (_: Exception) { JSONObject() }
    var effectiveCmd = commandId
    var effectiveParams = paramsJson
    val activeCellKey = cur.viewport.sheetIndex to NativeViewportSnapshot.packCellKey(cur.viewport.activeRow, cur.viewport.activeCol)
    val activeCell = cur.viewport.cellAt(cur.viewport.activeRow, cur.viewport.activeCol)
    val targetBounds = resolveCommandTargetRange(pObj)
    val tr0 = targetBounds[0]
    val tc0 = targetBounds[1]
    val tr1 = targetBounds[2]
    val tc1 = targetBounds[3]
    val targetRangeAddr = if (tr0 == tr1 && tc0 == tc1) {
        MasuSheetConfig.formatCellAddress(tr0, tc0)
    } else {
        "${MasuSheetConfig.formatCellAddress(tr0, tc0)}:${MasuSheetConfig.formatCellAddress(tr1, tc1)}"
    }

    when (commandId) {
        "home.insertSheet" -> {
            val sheetCount = cur.viewport.sheetNames.size.coerceAtLeast(1)
            val insertAt = if (pObj.has("before")) {
                pObj.optInt("before", cur.viewport.sheetIndex + 1).coerceIn(0, sheetCount)
            } else {
                (cur.viewport.sheetIndex + 1).coerceIn(0, sheetCount)
            }
            onInsertSheetBefore(insertAt)
            return
        }
        "home.deleteSheet" -> {
            val targetSheet = pObj.optInt("sheet", cur.viewport.sheetIndex)
            onDeleteSheetAt(targetSheet)
            return
        }
        "chartDesign.selectData", "chart.selectData" -> {
            onOpenChartSelectDataDialog(open = true)
            return
        }
        "home.bold", "format.bold" -> {
            effectiveCmd = "home.bold"
            if (pObj.has("range")) {
                engineGateway.executeCommand("selection.set", "{\"active\":\"${MasuSheetConfig.formatCellAddress(tr0, tc0)}\",\"range\":\"$targetRangeAddr\"}")
            }
            val nextBold = when {
                pObj.has("on") -> pObj.optBoolean("on", true)
                pObj.has("bold") -> pObj.optBoolean("bold", true)
                else -> activeCell?.isBold != true
            }
            effectiveParams = JSONObject().put("range", targetRangeAddr).put("on", nextBold).toString()
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(isBold = nextBold) }
        }
        "home.italic", "format.italic" -> {
            effectiveCmd = "home.italic"
            if (pObj.has("range")) {
                engineGateway.executeCommand("selection.set", "{\"active\":\"${MasuSheetConfig.formatCellAddress(tr0, tc0)}\",\"range\":\"$targetRangeAddr\"}")
            }
            val nextItalic = when {
                pObj.has("on") -> pObj.optBoolean("on", true)
                pObj.has("italic") -> pObj.optBoolean("italic", true)
                else -> activeCell?.isItalic != true
            }
            effectiveParams = JSONObject().put("range", targetRangeAddr).put("on", nextItalic).toString()
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(isItalic = nextItalic) }
        }
        "home.underline", "format.underline" -> {
            effectiveCmd = "home.underline"
            if (pObj.has("range")) {
                engineGateway.executeCommand("selection.set", "{\"active\":\"${MasuSheetConfig.formatCellAddress(tr0, tc0)}\",\"range\":\"$targetRangeAddr\"}")
            }
            val nextUnderline = when {
                pObj.has("on") -> pObj.optBoolean("on", true)
                pObj.has("underline") -> pObj.optBoolean("underline", true)
                else -> activeCell?.isUnderline != true
            }
            effectiveParams = JSONObject().put("range", targetRangeAddr).put("on", nextUnderline).toString()
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(isUnderline = nextUnderline) }
        }
        "home.strikethrough", "format.strikethrough" -> {
            effectiveCmd = "home.strikethrough"
            if (pObj.has("range")) {
                engineGateway.executeCommand("selection.set", "{\"active\":\"${MasuSheetConfig.formatCellAddress(tr0, tc0)}\",\"range\":\"$targetRangeAddr\"}")
            }
            val nextStrike = when {
                pObj.has("on") -> pObj.optBoolean("on", true)
                pObj.has("strike") -> pObj.optBoolean("strike", true)
                else -> activeCell?.isStrike != true
            }
            effectiveParams = JSONObject().put("range", targetRangeAddr).put("on", nextStrike).toString()
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(isStrike = nextStrike) }
        }
        "home.fontSize", "format.fontSize" -> {
            effectiveCmd = "home.fontSize"
            if (pObj.has("range")) {
                engineGateway.executeCommand("selection.set", "{\"active\":\"${MasuSheetConfig.formatCellAddress(tr0, tc0)}\",\"range\":\"$targetRangeAddr\"}")
            }
            val sz = pObj.optDouble("size", 11.0).toFloat().coerceIn(8f, 72f)
            effectiveParams = JSONObject().put("size", sz.toDouble()).toString()
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(fontSize = sz) }
        }
        "home.increaseFontSize" -> {
            val curSz = activeCell?.fontSize ?: 11f
            val nextSz = (curSz + 1f).coerceIn(8f, 72f)
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(fontSize = nextSz) }
        }
        "home.decreaseFontSize" -> {
            val curSz = activeCell?.fontSize ?: 11f
            val nextSz = (curSz - 1f).coerceIn(8f, 72f)
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(fontSize = nextSz) }
        }
        "home.alignLeft", "format.alignLeft" -> {
            effectiveCmd = "home.alignLeft"
            if (pObj.has("range")) {
                engineGateway.executeCommand("selection.set", "{\"active\":\"${MasuSheetConfig.formatCellAddress(tr0, tc0)}\",\"range\":\"$targetRangeAddr\"}")
            }
            val alreadyLeft = (activeCell == null || activeCell.hAlign == 0) && activeCellKey in cur.explicitLeftAlignCells
            _uiState.value = cur.copy(
                explicitLeftAlignCells = if (alreadyLeft) cur.explicitLeftAlignCells - activeCellKey else cur.explicitLeftAlignCells + activeCellKey
            )
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(hAlign = 0) }
        }
        "home.alignCenter", "format.alignCenter" -> {
            effectiveCmd = "home.alignCenter"
            if (pObj.has("range")) {
                engineGateway.executeCommand("selection.set", "{\"active\":\"${MasuSheetConfig.formatCellAddress(tr0, tc0)}\",\"range\":\"$targetRangeAddr\"}")
            }
            val alreadyCenter = !pObj.has("range") && activeCell?.hAlign == 1
            _uiState.value = cur.copy(explicitLeftAlignCells = cur.explicitLeftAlignCells - activeCellKey)
            val nextH = if (alreadyCenter) 0 else 1
            if (alreadyCenter) {
                effectiveCmd = "home.alignLeft"
            }
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(hAlign = nextH) }
        }
        "home.alignRight", "format.alignRight" -> {
            effectiveCmd = "home.alignRight"
            if (pObj.has("range")) {
                engineGateway.executeCommand("selection.set", "{\"active\":\"${MasuSheetConfig.formatCellAddress(tr0, tc0)}\",\"range\":\"$targetRangeAddr\"}")
            }
            val alreadyRight = !pObj.has("range") && activeCell?.hAlign == 2
            _uiState.value = cur.copy(explicitLeftAlignCells = cur.explicitLeftAlignCells - activeCellKey)
            val nextH = if (alreadyRight) 0 else 2
            if (alreadyRight) {
                effectiveCmd = "home.alignLeft"
            }
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(hAlign = nextH) }
        }
        "format.numberFormat" -> {
            if (pObj.has("range")) {
                engineGateway.executeCommand("selection.set", "{\"active\":\"${MasuSheetConfig.formatCellAddress(tr0, tc0)}\",\"range\":\"$targetRangeAddr\"}")
            }
            val fmt = pObj.optString("format", "").trim()
            when {
                fmt.contains("%") -> {
                    effectiveCmd = "home.percent"
                    effectiveParams = "{}"
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(numberFormatKind = "percent") }
                }
                fmt.contains("¥") || fmt.contains("￥") || fmt.contains("$") -> {
                    effectiveCmd = "home.currency"
                    effectiveParams = "{}"
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(numberFormatKind = "currency") }
                }
                fmt.contains(",") || fmt.contains("#,##0") -> {
                    effectiveCmd = "home.comma"
                    effectiveParams = "{}"
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(numberFormatKind = "") }
                }
                else -> {
                    effectiveCmd = "home.numberFormat"
                    effectiveParams = JSONObject().put("format", fmt.ifBlank { "General" }).toString()
                }
            }
        }
        "home.alignTop" -> {
            val alreadyTop = (activeCell == null || activeCell.vAlign == 0) && activeCellKey in cur.explicitTopAlignCells
            _uiState.value = cur.copy(
                explicitTopAlignCells = if (alreadyTop) cur.explicitTopAlignCells - activeCellKey else cur.explicitTopAlignCells + activeCellKey
            )
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(vAlign = 0) }
        }
        "home.alignMiddle" -> {
            val alreadyMiddle = activeCell?.vAlign == 1
            _uiState.value = cur.copy(explicitTopAlignCells = cur.explicitTopAlignCells - activeCellKey)
            val nextV = if (alreadyMiddle) 0 else 1
            if (alreadyMiddle) {
                effectiveCmd = "home.alignTop"
            }
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(vAlign = nextV) }
        }
        "home.alignBottom" -> {
            val alreadyBottom = activeCell?.vAlign == 2
            _uiState.value = cur.copy(explicitTopAlignCells = cur.explicitTopAlignCells - activeCellKey)
            val nextV = if (alreadyBottom) 0 else 2
            if (alreadyBottom) {
                effectiveCmd = "home.alignTop"
            }
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st -> st.copy(vAlign = nextV) }
        }
        "home.percentStyle" -> effectiveCmd = "home.percent"
        "home.commaStyle" -> effectiveCmd = "home.comma"
        "home.fillColor", "format.fillColor" -> {
            effectiveCmd = "home.fillColor"
            val rawCol = if (pObj.isNull("color")) "" else pObj.optString("color", "").trim()
            val isClear = pObj.optBoolean("clear", false) ||
                rawCol.isEmpty() ||
                rawCol.equals("none", ignoreCase = true) ||
                rawCol.equals("null", ignoreCase = true)
            val parsedArgb = if (isClear) 0 else (SpreadsheetColorPalette.parseHexOrNull(rawCol) ?: 0)
            val hexStr = if (isClear || parsedArgb == 0) "none" else SpreadsheetColorPalette.toHexString(parsedArgb)
            effectiveParams = JSONObject()
                .put("range", targetRangeAddr)
                .put("color", hexStr)
                .toString()
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st ->
                st.copy(bgColor = if (isClear || parsedArgb == 0) CustomCellStyleOverride.CLEARED else parsedArgb)
            }
        }
        "home.fontColor", "format.fontColor" -> {
            effectiveCmd = "home.fontColor"
            val rawCol = if (pObj.isNull("color")) "" else pObj.optString("color", "").trim()
            val isAuto = pObj.optBoolean("clear", false) ||
                rawCol.isEmpty() ||
                rawCol.equals("auto", ignoreCase = true) ||
                rawCol.equals("none", ignoreCase = true) ||
                rawCol.equals("null", ignoreCase = true)
            val parsedArgb = if (isAuto) 0 else (SpreadsheetColorPalette.parseHexOrNull(rawCol) ?: 0)
            val hexStr = if (isAuto || parsedArgb == 0) "auto" else SpreadsheetColorPalette.toHexString(parsedArgb)
            effectiveParams = JSONObject()
                .put("range", targetRangeAddr)
                .put("color", hexStr)
                .toString()
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st ->
                st.copy(fgColor = if (isAuto || parsedArgb == 0) CustomCellStyleOverride.CLEARED else parsedArgb)
            }
            if (_uiState.value.selectedObjectId != null) {
                onUpdateSelectedObjectTextColor(if (isAuto) 0 else parsedArgb)
            }
        }
        "home.borders", "format.borders", "format.borderColor", "home.borderColor" -> {
            effectiveCmd = "home.borders"
            val rawColor = if (pObj.isNull("color")) "" else pObj.optString("color", "").trim()
            if (rawColor.isNotEmpty() && !rawColor.equals("none", ignoreCase = true) && !rawColor.equals("null", ignoreCase = true)) {
                SpreadsheetColorPalette.parseHexOrNull(rawColor)?.let { activeBorderColorArgb = it }
            }
            val rawPreset = pObj.optString("preset", "").trim()
            val rawStyle = pObj.optString("style", "").trim()
            val normalizedPreset = when {
                rawPreset.equals("outline", ignoreCase = true) &&
                    (rawStyle.equals("medium", ignoreCase = true) || rawStyle.equals("thick", ignoreCase = true)) -> "thickOutside"
                rawPreset.equals("outline", ignoreCase = true) -> "outside"
                rawPreset.isNotEmpty() -> rawPreset
                rawStyle in setOf("all", "outside", "thickOutside", "bottom", "top", "left", "right", "none") -> rawStyle
                else -> "all"
            }
            val normalizedStyle = when {
                normalizedPreset.equals("none", ignoreCase = true) || rawStyle.equals("none", ignoreCase = true) -> "none"
                rawStyle in setOf("thin", "medium", "thick", "double", "dashed", "dotted") -> rawStyle
                normalizedPreset.equals("thickOutside", ignoreCase = true) -> "medium"
                else -> "thin"
            }
            val borderColorArgb = when {
                rawColor.isNotEmpty() -> SpreadsheetColorPalette.parseHexOrNull(rawColor) ?: 0xFF334155.toInt()
                activeBorderColorArgb != 0 -> activeBorderColorArgb
                else -> 0xFF334155.toInt()
            }
            val borderHex = SpreadsheetColorPalette.toHexString(borderColorArgb)
            effectiveParams = JSONObject()
                .put("range", targetRangeAddr)
                .put("preset", normalizedPreset)
                .put("style", normalizedStyle)
                .put("color", borderHex)
                .toString()

            val halfW = when (normalizedStyle.lowercase()) {
                "none" -> 0
                "medium", "double" -> 4
                "thick" -> 6
                else -> 2
            }
            val encodedBorder = if (normalizedPreset.equals("none", ignoreCase = true) || halfW == 0) {
                0
            } else {
                (halfW shl 24) or (borderColorArgb and 0xFFFFFF)
            }
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { r, c, st ->
                when (normalizedPreset.lowercase()) {
                    "none" -> st.copy(borderTop = 0, borderRight = 0, borderBottom = 0, borderLeft = 0)
                    "top" -> if (r == tr0) st.copy(borderTop = encodedBorder) else st
                    "bottom" -> if (r == tr1) st.copy(borderBottom = encodedBorder) else st
                    "left" -> if (c == tc0) st.copy(borderLeft = encodedBorder) else st
                    "right" -> if (c == tc1) st.copy(borderRight = encodedBorder) else st
                    "outside", "thickoutside" -> st.copy(
                        borderTop = if (r == tr0) encodedBorder else st.borderTop,
                        borderBottom = if (r == tr1) encodedBorder else st.borderBottom,
                        borderLeft = if (c == tc0) encodedBorder else st.borderLeft,
                        borderRight = if (c == tc1) encodedBorder else st.borderRight
                    )
                    else -> st.copy(
                        borderTop = encodedBorder,
                        borderRight = encodedBorder,
                        borderBottom = encodedBorder,
                        borderLeft = encodedBorder
                    )
                }
            }
        }
        "home.cellStyle" -> {
            val styleName = pObj.optString("name", pObj.optString("style", "Normal")).ifBlank { "Normal" }
            effectiveParams = JSONObject()
                .put("range", targetRangeAddr)
                .put("name", styleName)
                .toString()
            when (styleName.lowercase()) {
                "normal", "標準" -> {
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st ->
                        st.copy(
                            bgColor = CustomCellStyleOverride.CLEARED,
                            fgColor = CustomCellStyleOverride.CLEARED,
                            fontSize = 11f,
                            isBold = false,
                            isItalic = false,
                            isUnderline = false,
                            isStrike = false,
                            borderTop = 0,
                            borderRight = 0,
                            borderBottom = 0,
                            borderLeft = 0
                        )
                    }
                }
                "good", "良い" -> {
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st ->
                        st.copy(bgColor = 0xFFC6EFCE.toInt(), fgColor = 0xFF006100.toInt())
                    }
                }
                "bad", "悪い" -> {
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st ->
                        st.copy(bgColor = 0xFFFFC7CE.toInt(), fgColor = 0xFF9C0006.toInt())
                    }
                }
                "neutral", "中立" -> {
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st ->
                        st.copy(bgColor = 0xFFFFEB9C.toInt(), fgColor = 0xFF9C6500.toInt())
                    }
                }
                "heading 1", "heading1", "見出し 1" -> {
                    val bBot = (6 shl 24) or 0x4472C4
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { r, _, st ->
                        st.copy(
                            bgColor = CustomCellStyleOverride.CLEARED,
                            fgColor = 0xFF2F5597.toInt(),
                            fontSize = 15f,
                            isBold = true,
                            borderBottom = if (r == tr1) bBot else st.borderBottom
                        )
                    }
                }
                "heading 2", "heading2", "見出し 2" -> {
                    val bBot = (4 shl 24) or 0x8EA9DB
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { r, _, st ->
                        st.copy(
                            bgColor = CustomCellStyleOverride.CLEARED,
                            fgColor = 0xFF2F5597.toInt(),
                            fontSize = 13f,
                            isBold = true,
                            borderBottom = if (r == tr1) bBot else st.borderBottom
                        )
                    }
                }
                "title", "タイトル" -> {
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, st ->
                        st.copy(bgColor = CustomCellStyleOverride.CLEARED, fgColor = 0xFF1F4E78.toInt(), fontSize = 18f, isBold = true)
                    }
                }
                "total", "集計" -> {
                    val bTop = (2 shl 24) or 0x4472C4
                    val bBot = (4 shl 24) or 0x4472C4
                    updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { r, _, st ->
                        st.copy(
                            isBold = true,
                            borderTop = if (r == tr0) bTop else st.borderTop,
                            borderBottom = if (r == tr1) bBot else st.borderBottom
                        )
                    }
                }
            }
        }
        "home.clearFormats" -> {
            updateCustomCellStylesInRange(tr0, tc0, tr1, tc1) { _, _, _ ->
                CustomCellStyleOverride(
                    bgColor = CustomCellStyleOverride.CLEARED,
                    fgColor = CustomCellStyleOverride.CLEARED,
                    fontSize = 11f,
                    isBold = false,
                    isItalic = false,
                    isUnderline = false,
                    isStrike = false,
                    hAlign = 0,
                    vAlign = 1,
                    borderTop = 0,
                    borderRight = 0,
                    borderBottom = 0,
                    borderLeft = 0
                )
            }
        }
        "home.clearAll", "edit.clearAll" -> {
            effectiveCmd = "home.clearAll"
            if (pObj.has("range")) {
                engineGateway.executeCommand("selection.set", "{\"active\":\"${MasuSheetConfig.formatCellAddress(tr0, tc0)}\",\"range\":\"$targetRangeAddr\"}")
            }
            val sIdx = cur.viewport.sheetIndex
            for (r in tr0..tr1.coerceAtMost(tr0 + 199)) {
                for (c in tc0..tc1.coerceAtMost(tc0 + 51)) {
                    customCellStyles.remove(sIdx to NativeViewportSnapshot.packCellKey(r, c))
                }
            }
            sizeRevisionBump++
        }
        "edit.pasteSpecial" -> {
            val mode = pObj.optString("mode", pObj.optString("kind", "values")).lowercase()
            val transpose = pObj.optBoolean("transpose", mode == "transpose")
            onPasteSpecial(mode = mode, transpose = transpose)
            return
        }
        "view.gridlines" -> {
            val next = if (pObj.has("on")) pObj.optBoolean("on", !cur.showGridlines) else !cur.showGridlines
            _uiState.value = cur.copy(showGridlines = next)
            effectiveParams = "{\"on\":$next}"
        }
        "view.headings" -> {
            val next = if (pObj.has("on")) pObj.optBoolean("on", !cur.showHeadings) else !cur.showHeadings
            _uiState.value = cur.copy(showHeadings = next)
            effectiveParams = "{\"on\":$next}"
        }
        "view.formulaBar" -> {
            val next = if (pObj.has("on")) pObj.optBoolean("on", !cur.formulaBarVisible) else !cur.formulaBarVisible
            _uiState.value = cur.copy(formulaBarVisible = next)
            effectiveParams = "{\"on\":$next}"
        }
        "view.normal" -> {
            _uiState.value = cur.copy(viewMode = "normal")
        }
        "view.pageBreakPreview" -> {
            val nextMode = if (cur.viewMode == "pageBreakPreview") "normal" else "pageBreakPreview"
            _uiState.value = cur.copy(viewMode = nextMode)
        }
        "view.pageLayout" -> {
            val nextMode = if (cur.viewMode == "pageLayout") "normal" else "pageLayout"
            _uiState.value = cur.copy(viewMode = nextMode)
        }
        "view.freezeTopRow" -> {
            if (cur.freezeRows == 1 && cur.freezeCols == 0) {
                _uiState.value = cur.copy(freezeRows = 0, freezeCols = 0)
                effectiveCmd = "view.unfreezePanes"
            } else {
                _uiState.value = cur.copy(freezeRows = 1, freezeCols = 0)
            }
        }
        "view.freezeFirstColumn" -> {
            if (cur.freezeRows == 0 && cur.freezeCols == 1) {
                _uiState.value = cur.copy(freezeRows = 0, freezeCols = 0)
                effectiveCmd = "view.unfreezePanes"
            } else {
                _uiState.value = cur.copy(freezeRows = 0, freezeCols = 1)
            }
        }
        "view.freezePanes" -> {
            val r = cur.viewport.activeRow
            val c = cur.viewport.activeCol
            val targetR = if (r == 0 && c == 0) 1 else r
            val targetC = if (r == 0 && c == 0) 1 else c
            if (cur.freezeRows == targetR && cur.freezeCols == targetC) {
                _uiState.value = cur.copy(freezeRows = 0, freezeCols = 0)
                effectiveCmd = "view.unfreezePanes"
                effectiveParams = "{}"
            } else {
                _uiState.value = cur.copy(freezeRows = targetR, freezeCols = targetC)
            }
        }
        "view.unfreezePanes" -> {
            _uiState.value = cur.copy(freezeRows = 0, freezeCols = 0)
        }
        "view.zoom100" -> {
            preferencesRepository.setZoomPercent(100)
            _uiState.value = cur.copy(zoomPercent = 100)
        }
        "pageLayout.orientation" -> {
            val orient = pObj.optString("orientation", "portrait").lowercase()
            _uiState.value = cur.copy(pageOrientation = orient)
        }
        "pageLayout.size" -> {
            val paper = pObj.optString("paper", pObj.optString("size", "A4")).ifBlank { "A4" }
            _uiState.value = cur.copy(paperSize = paper)
        }
        "pageLayout.margins" -> {
            val preset = pObj.optString("preset", "normal").lowercase()
            _uiState.value = cur.copy(pageMargins = preset)
        }
        "pageLayout.printArea" -> {
            val clear = pObj.optBoolean("clear", false)
            _uiState.value = cur.copy(hasPrintArea = if (clear) false else !cur.hasPrintArea)
        }
        "pageLayout.breaks" -> {
            _uiState.value = cur.copy(hasPageBreak = !cur.hasPageBreak)
        }
        "pageLayout.printTitles" -> {
            _uiState.value = cur.copy(hasPrintTitles = !cur.hasPrintTitles)
        }
        "pageLayout.scaleToFit" -> {
            _uiState.value = cur.copy(scaleToFitOnePage = !cur.scaleToFitOnePage)
        }
        "pageLayout.sheetOptions", "pageLayout.printGridlines" -> {
            val next = !cur.showGridlines
            _uiState.value = cur.copy(showGridlines = next)
            effectiveCmd = "view.gridlines"
            effectiveParams = "{\"on\":$next}"
        }
        "formulas.showFormulas" -> {
            _uiState.value = cur.copy(showFormulas = !cur.showFormulas)
        }
        "formulas.tracePrecedents" -> {
            _uiState.value = cur.copy(tracePrecedentsActive = !cur.tracePrecedentsActive)
        }
        "formulas.traceDependents" -> {
            _uiState.value = cur.copy(traceDependentsActive = !cur.traceDependentsActive)
        }
        "formulas.removeArrows" -> {
            _uiState.value = cur.copy(tracePrecedentsActive = false, traceDependentsActive = false)
        }
        "data.sortAscending" -> {
            _uiState.value = cur.copy(activeSortOrder = if (cur.activeSortOrder == "asc") "" else "asc")
        }
        "data.sortDescending" -> {
            _uiState.value = cur.copy(activeSortOrder = if (cur.activeSortOrder == "desc") "" else "desc")
        }
        "data.filter" -> {
            onToggleAutoFilter()
            return
        }
        "data.clearFilter" -> {
            onClearAllFiltersOnCurrentSheet()
            val sIdx = _uiState.value.viewport.sheetIndex
            _uiState.value = _uiState.value.copy(
                isAutoFilterActive = false,
                autoFilterRanges = _uiState.value.autoFilterRanges - sIdx,
                activeSortOrder = ""
            )
            return
        }
        "data.group" -> {
            _uiState.value = cur.copy(isGrouped = !cur.isGrouped)
        }
        "data.ungroup" -> {
            _uiState.value = cur.copy(isGrouped = false)
        }
        "review.showAllComments" -> {
            _uiState.value = cur.copy(showAllComments = !cur.showAllComments)
        }
        "review.protectSheet" -> {
            _uiState.value = cur.copy(isSheetProtected = !cur.isSheetProtected)
        }
        "review.protectWorkbook", "file.protectWorkbook" -> {
            _uiState.value = cur.copy(isWorkbookProtected = !cur.isWorkbookProtected)
        }
        "review.ShowChanges" -> {
            _uiState.value = cur.copy(showChangesActive = !cur.showChangesActive)
        }
        "home.conditionalFormatting", "format.conditional", "home.clearConditionalFormatting", "home.conditionalFormat", "home.clearRules" -> {
            pushCustomOverlayUndoSnapshot(preCommandOverlaySnapshot)
            val rawRule = if (commandId == "home.clearConditionalFormatting" || commandId == "home.clearRules") {
                "clear"
            } else {
                pObj.optString("type", pObj.optString("rule", "dataBar"))
            }
            val cfType = when {
                rawRule.contains("color", ignoreCase = true) || rawRule.contains("scale", ignoreCase = true) || rawRule.contains("色", ignoreCase = true) -> "colorScale"
                rawRule.contains("clear", ignoreCase = true) || rawRule.contains("クリア", ignoreCase = true) -> ""
                else -> "dataBar"
            }
            val nextCf = if (cfType.isEmpty()) "" else if (cur.activeConditionalFormat == cfType && !pObj.has("rule") && !pObj.has("type")) "" else cfType
            val cfBounds = if (tr0 == tr1 && tc0 == tc1) {
                val tb = detectTableBoundsAt(cur.viewport, tr0, tc0)
                intArrayOf(tb[1], tb[3], tb[2], tb[4])
            } else {
                intArrayOf(tr0, tc0, tr1, tc1)
            }
            val cr0 = cfBounds[0].coerceAtLeast(0)
            val cc0 = cfBounds[1].coerceAtLeast(0)
            val cr1 = cfBounds[2].coerceIn(cr0, cr0 + 500)
            val cc1 = cfBounds[3].coerceIn(cc0, cc0 + 60)
            val cfRangeAddr = "${MasuSheetConfig.formatCellAddress(cr0, cc0)}:${MasuSheetConfig.formatCellAddress(cr1, cc1)}"
            if (nextCf.isEmpty()) {
                updateCustomCellStylesInRange(cr0, cc0, cr1, cc1) { _, _, st ->
                    st.copy(bgColor = CustomCellStyleOverride.CLEARED, fgColor = CustomCellStyleOverride.CLEARED)
                }
                engineGateway.executeCommand("home.clearRules", "{\"sheet\":false}")
                val snap = engineGateway.executeCommand("home.fillColor", "{\"range\":\"$cfRangeAddr\",\"color\":\"none\"}")
                applyViewportSnapshot(snap)
            } else {
                val nums = mutableListOf<Float>()
                for (r in cr0..cr1) {
                    for (c in cc0..cc1) {
                        parseNumericCell(cur.viewport.cellAt(r, c))?.let { nums.add(it) }
                    }
                }
                val minV = nums.minOrNull() ?: 0f
                val maxV = nums.maxOrNull() ?: 100f
                val avgV = if (nums.isNotEmpty()) nums.average().toFloat() else 50f
                val span = (maxV - minV).takeIf { it > 1e-5f } ?: 1f
                val scaleName = pObj.optString("scale", "GYR").uppercase()
                val barHex = pObj.optString("color", "#1F9D55")
                val barBaseCol = SpreadsheetColorPalette.parseHexOrNull(barHex) ?: 0xFF1F9D55.toInt()
                updateCustomCellStylesInRange(cr0, cc0, cr1, cc1) { r, c, st ->
                    val v = parseNumericCell(cur.viewport.cellAt(r, c))
                    val ratio = if (v != null) ((v - minV) / span).coerceIn(0f, 1f) else 0.5f
                    val bg = when {
                        cfType == "colorScale" && scaleName == "RYG" -> when {
                            ratio >= 0.66f -> 0xFF63BE7B.toInt()
                            ratio >= 0.33f -> 0xFFFFEB84.toInt()
                            else -> 0xFFF8696B.toInt()
                        }
                        cfType == "colorScale" && scaleName == "BWR" -> when {
                            ratio >= 0.66f -> 0xFFF8696B.toInt()
                            ratio >= 0.33f -> 0xFFFFFFFF.toInt()
                            else -> 0xFF5A8AC6.toInt()
                        }
                        cfType == "colorScale" -> when {
                            ratio >= 0.66f -> 0xFFF8696B.toInt()
                            ratio >= 0.33f -> 0xFFFFEB84.toInt()
                            else -> 0xFF63BE7B.toInt()
                        }
                        rawRule.contains("less", ignoreCase = true) || rawRule.contains("below", ignoreCase = true) || rawRule.contains("bottom", ignoreCase = true) -> {
                            if (v != null && v <= avgV) 0xFFFFC7CE.toInt() else st.bgColor
                        }
                        rawRule.contains("greater", ignoreCase = true) || rawRule.contains("above", ignoreCase = true) || rawRule.contains("top", ignoreCase = true) -> {
                            if (v != null && v >= avgV) 0xFFC6EFCE.toInt() else st.bgColor
                        }
                        else -> SpreadsheetColorPalette.applySpreadsheetTint(barBaseCol, (0.82 - ratio * 0.42).coerceIn(0.25, 0.85))
                    }
                    val fg = when {
                        rawRule.contains("less", ignoreCase = true) || rawRule.contains("below", ignoreCase = true) || rawRule.contains("bottom", ignoreCase = true) -> {
                            if (v != null && v <= avgV) 0xFF9C0006.toInt() else st.fgColor
                        }
                        rawRule.contains("greater", ignoreCase = true) || rawRule.contains("above", ignoreCase = true) || rawRule.contains("top", ignoreCase = true) -> {
                            if (v != null && v >= avgV) 0xFF006100.toInt() else st.fgColor
                        }
                        else -> st.fgColor
                    }
                    st.copy(bgColor = bg, fgColor = fg)
                }
                // Also forward rule to Rust engine so `sh.cond_formats` is populated and persisted in `.xlsx`!
                val engineCfRule = when {
                    cfType == "colorScale" -> "colorScale"
                    rawRule.contains("less", ignoreCase = true) -> "lessThan"
                    rawRule.contains("greater", ignoreCase = true) -> "greaterThan"
                    rawRule.contains("below", ignoreCase = true) -> "belowAverage"
                    rawRule.contains("above", ignoreCase = true) -> "aboveAverage"
                    else -> "dataBar"
                }
                val cfSnap = engineGateway.executeCommand(
                    "home.conditionalFormat",
                    JSONObject()
                        .put("rule", engineCfRule)
                        .put("color", barHex)
                        .put("value", avgV.toString())
                        .toString()
                )
                applyViewportSnapshot(cfSnap)
            }
            val isEn = cur.language == "en"
            _uiState.value = _uiState.value.copy(
                activeConditionalFormat = nextCf,
                statusNoticeMessage = if (nextCf.isEmpty()) {
                    if (isEn) "Cleared conditional formatting" else "条件付き書式をクリアしました"
                } else {
                    if (isEn) "Applied conditional formatting ($rawRule)" else "条件付き書式 ($rawRule) を選択範囲に適用しました"
                }
            )
            return
        }
        "data.dataValidation", "data.validation" -> {
            val allow = pObj.optString("type", pObj.optString("kind", pObj.optString("allow", "list")))
            val src = pObj.optString("formula1", pObj.optString("source", ""))
            val f2 = pObj.optString("formula2", "")
            val op = pObj.optString("operator", "between")
            val explicitRange = pObj.optString("range", "")
            val isClear = pObj.optBoolean("clear", false) || (allow == "any" && src == "clear")
            val inTitle = pObj.optString("inputTitle", "")
            val inMsg = pObj.optString("inputMessage", "")
            val errTitle = pObj.optString("errorTitle", "")
            val errMsg = pObj.optString("errorMessage", "")
            onApplyDataValidation(
                kind = allow,
                formula1 = src,
                formula2 = f2,
                operator = op,
                targetRange = explicitRange,
                clearRule = isClear,
                inputTitle = inTitle,
                inputMessage = inMsg,
                errorTitle = errTitle,
                errorMessage = errMsg
            )
            return
        }
        "insert.sparkline", "insert.sparklines", "insert.sparklineLine", "insert.sparklineColumn", "insert.sparklineWinLoss" -> {
            val defaultKind = when (commandId) {
                "insert.sparklineColumn" -> "column"
                "insert.sparklineWinLoss" -> "winloss"
                else -> pObj.optString("kind", pObj.optString("type", "line"))
            }
            val srcRange = pObj.optString("dataRange", pObj.optString("source", ""))
            val locCell = pObj.optString("location", cur.viewport.activeAddress)
            onInsertSparkline(sourceRange = srcRange, locationCell = locCell, kind = defaultKind)
            return
        }
        "home.hideColumns" -> {
            onHideSelectedColumns()
            return
        }
        "home.unhideColumns" -> {
            onUnhideColumnsAround(cur.viewport.activeCol)
            return
        }
        "home.hideRows" -> {
            onHideSelectedRows()
            return
        }
        "home.unhideRows" -> {
            onUnhideRowsAround(cur.viewport.activeRow)
            return
        }
        "data.whatIfGoalSeek", "data.goalSeek" -> {
            val setCell = pObj.optString("setCell", pObj.optString("set", MasuSheetConfig.formatCellAddress(cur.viewport.activeRow, cur.viewport.activeCol)))
            val toVal = pObj.optDouble("toValue", pObj.optDouble("to", 1000.0))
            val byCell = pObj.optString("byChangingCell", pObj.optString("changing", "B2"))
            onRunGoalSeek(setCell, toVal, byCell)
            return
        }
        "home.findAndSelect", "edit.find" -> {
            val q = pObj.optString("find", pObj.optString("query", "")).trim()
            val r = pObj.optString("replace", "")
            if (q.isNotEmpty()) {
                if (pObj.optBoolean("replaceAll", false) || (effectiveCmd == "home.findAndSelect" && r.isNotEmpty() && pObj.has("replace"))) {
                    if (pObj.optBoolean("replaceAll", false)) {
                        onReplaceAllCells(q, r)
                    } else {
                        onFindNext(q, forward = true)
                    }
                } else {
                    onFindNext(q, forward = !pObj.optBoolean("backward", false))
                }
            } else {
                val isEn = cur.language == "en"
                _uiState.value = cur.copy(
                    isCommandCenterOpen = true,
                    commandCenterTab = "findReplace",
                    statusNoticeMessage = if (isEn) "Enter text in Find & Replace to search cells" else "検索する文字列を入力してください"
                )
            }
            return
        }
        "edit.replace" -> {
            val q = pObj.optString("find", pObj.optString("query", "")).trim()
            val r = pObj.optString("replace", "")
            if (q.isNotEmpty()) {
                if (pObj.optBoolean("replaceAll", true)) {
                    onReplaceAllCells(q, r)
                } else {
                    onReplaceCurrentCell(q, r)
                }
            } else {
                _uiState.value = cur.copy(
                    isCommandCenterOpen = true,
                    commandCenterTab = "findReplace"
                )
            }
            return
        }
        "edit.goTo" -> {
            val ref = pObj.optString("reference", pObj.optString("address", "")).trim()
            if (ref.isNotEmpty()) {
                onGoToReference(ref)
            }
            return
        }
        "edit.goToSpecial" -> {
            val kind = pObj.optString("kind", pObj.optString("type", "formulas"))
            onGoToSpecial(kind)
            return
        }
        "home.renameSheet" -> {
            val newName = pObj.optString("name", "").trim()
            val targetSheet = pObj.optInt("sheet", cur.viewport.sheetIndex)
            if (newName.isNotEmpty()) {
                onRenameSheetAt(targetSheet, newName)
            }
            return
        }
        "home.moveOrCopySheet" -> {
            val copy = pObj.optBoolean("copy", false)
            val targetSheet = pObj.optInt("sheet", cur.viewport.sheetIndex)
            if (copy) {
                onDuplicateSheetAt(targetSheet)
            } else {
                val sheetCount = cur.viewport.sheetNames.size.coerceAtLeast(1)
                val toIdx = if (pObj.has("to")) {
                    pObj.optInt("to", targetSheet).coerceIn(0, sheetCount - 1)
                } else if (pObj.has("delta")) {
                    (targetSheet + pObj.optInt("delta", 1)).coerceIn(0, sheetCount - 1)
                } else {
                    (targetSheet + 1) % sheetCount
                }
                onMoveSheet(targetSheet, toIdx)
            }
            return
        }
        "insert.table", "home.formatAsTable" -> {
            val styleName = pObj.optString("style", pObj.optString("name", "TableStyleMedium2")).ifBlank { "TableStyleMedium2" }
            val hasExplicitStyle = pObj.has("style") || pObj.has("name")
            val nextActive = if (hasExplicitStyle) true else !cur.isTableActive
            val tbBounds = if (tr0 == tr1 && tc0 == tc1) {
                val tb = detectTableBoundsAt(cur.viewport, tr0, tc0)
                intArrayOf(tb[0], tb[3], tb[2], tb[4])
            } else {
                intArrayOf(tr0, tc0, tr1, tc1)
            }
            val hr = tbBounds[0]
            val hc0 = tbBounds[1]
            val hrEnd = tbBounds[2]
            val hc1 = tbBounds[3]
            if (nextActive) {
                val (hdrBg, hdrFg, bandBg) = when (styleName) {
                    "TableStyleLight1" -> Triple(0xFFD9F2E6.toInt(), 0xFF0F291E.toInt(), 0xFFF2FBF7.toInt())
                    "TableStyleLight2" -> Triple(0xFFD9E1F2.toInt(), 0xFF1F4E78.toInt(), 0xFFF2F5FB.toInt())
                    "TableStyleLight9" -> Triple(0xFFE7E6E6.toInt(), 0xFF262626.toInt(), 0xFFF7F7F7.toInt())
                    "TableStyleMedium4" -> Triple(0xFFC00000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFCE4D6.toInt())
                    "TableStyleMedium6" -> Triple(0xFF2F5597.toInt(), 0xFFFFFFFF.toInt(), 0xFFD9E1F2.toInt())
                    "TableStyleDark1" -> Triple(0xFF0F291E.toInt(), 0xFFFFFFFF.toInt(), 0xFFD9F2E6.toInt())
                    "TableStyleDark2" -> Triple(0xFF1F4E78.toInt(), 0xFFFFFFFF.toInt(), 0xFFD9E1F2.toInt())
                    else -> Triple(0xFF1F9D55.toInt(), 0xFFFFFFFF.toInt(), 0xFFECFDF5.toInt())
                }
                updateCustomCellStylesInRange(hr, hc0, hrEnd, hc1) { r, _, st ->
                    if (r == hr) {
                        st.copy(bgColor = hdrBg, fgColor = hdrFg, isBold = true)
                    } else if ((r - hr) % 2 == 1) {
                        st.copy(bgColor = bandBg, fgColor = CustomCellStyleOverride.CLEARED)
                    } else {
                        st.copy(bgColor = 0xFFFFFFFF.toInt(), fgColor = CustomCellStyleOverride.CLEARED)
                    }
                }
            }
            _uiState.value = cur.copy(isTableActive = nextActive)
        }
        "table.convertToRange" -> {
            _uiState.value = cur.copy(isTableActive = false)
        }
        "tableDesign.totalRow" -> {
            val next = !cur.tableTotalRow
            _uiState.value = cur.copy(tableTotalRow = next)
            effectiveCmd = "table.totalRow"
            effectiveParams = "{\"enabled\":$next}"
        }
        "tableDesign.headerRow" -> {
            val next = !cur.tableHeaderRow
            _uiState.value = cur.copy(tableHeaderRow = next)
            effectiveCmd = "table.headerRow"
            effectiveParams = "{\"enabled\":$next}"
        }
        "tableDesign.bandedRows" -> {
            val next = !cur.tableBandedRows
            _uiState.value = cur.copy(tableBandedRows = next)
            effectiveCmd = "table.bandedRows"
            effectiveParams = "{\"enabled\":$next}"
        }
        "tableDesign.bandedColumns" -> {
            val next = !cur.tableBandedCols
            _uiState.value = cur.copy(tableBandedCols = next)
            effectiveCmd = "table.bandedColumns"
            effectiveParams = "{\"enabled\":$next}"
        }
        "tableDesign.firstColumn" -> {
            val next = !cur.tableFirstCol
            _uiState.value = cur.copy(tableFirstCol = next)
            effectiveCmd = "table.firstColumn"
            effectiveParams = "{\"enabled\":$next}"
        }
        "tableDesign.lastColumn" -> {
            val next = !cur.tableLastCol
            _uiState.value = cur.copy(tableLastCol = next)
            effectiveCmd = "table.lastColumn"
            effectiveParams = "{\"enabled\":$next}"
        }
        "tableDesign.filterButton" -> {
            onToggleAutoFilter()
            return
        }
        "tableDesign.removeDuplicates", "table.removeDuplicates" -> {
            effectiveCmd = "data.removeDuplicates"
        }
        "tableDesign.summarizeWithPivot", "table.summarizePivot", "insert.pivotTable", "insert.recommendedPivotTables" -> {
            onInsertOrOpenPivotTable(
                openCreateDialog = commandId == "insert.recommendedPivotTables" || pObj.optBoolean("openDialog", false),
                placeOnNewSheet = pObj.optBoolean("newSheet", false),
                presetKind = pObj.optString("preset", "standard")
            )
            return
        }
        "pivotAnalyze.fieldList", "pivot.fieldList" -> {
            onTogglePivotFieldsPane()
            return
        }
        "chartDesign.switchRowColumn", "chart.switchRowColumn" -> {
            onSwitchSelectedChartRowColumn()
            return
        }
        "chartDesign.changeType", "chart.changeType", "insert.recommendedCharts" -> {
            val requestedType = pObj.optString("type", "").trim()
            if (requestedType.isNotEmpty()) {
                onInsertOrUpdateChartFromPicker(requestedType, insertNew = effectiveCmd == "insert.recommendedCharts")
            } else {
                _uiState.value = _uiState.value.copy(isPivotChartPickerOpen = true)
            }
            return
        }
        "pivotAnalyze.pivotChart", "insert.pivotChart" -> {
            val requestedType = pObj.optString("type", "column").ifBlank { "column" }
            val activePivot = getActivePivotTable()
            val bounds = if (activePivot != null && activePivot.targetSheetIndex == cur.viewport.sheetIndex) {
                intArrayOf(
                    activePivot.lastRenderedStartRow,
                    activePivot.lastRenderedStartRow + 1,
                    activePivot.lastRenderedEndRow,
                    activePivot.targetStartCol,
                    activePivot.lastRenderedEndCol
                )
            } else {
                detectTableBoundsAt(cur.viewport, cur.viewport.activeRow, cur.viewport.activeCol)
            }
            val spec = SpreadsheetChartTypeCatalog.findSpec(requestedType)
            val typeLabel = if (cur.language == "en") spec.labelEn else spec.labelJa
            onInsertChartFromRange(
                chartType = spec.subType,
                startRow = bounds[0],
                startCol = bounds[3],
                endRow = bounds[2],
                endCol = bounds[4],
                customTitle = if (cur.language == "en") "PivotChart ($typeLabel - ${cur.viewport.sheetName})" else "ピボットグラフ ($typeLabel - ${cur.viewport.sheetName})"
            )
            if (pObj.optBoolean("openPicker", true)) {
                _uiState.value = _uiState.value.copy(isPivotChartPickerOpen = true)
            }
            return
        }
        "pivotAnalyze.insertSlicer", "insert.slicer", "table.insertSlicer" -> {
            val activePivot = getActivePivotTable()
            if (activePivot != null) {
                onTogglePivotFieldsPane(open = true)
                val firstField = activePivot.rowFields.firstOrNull() ?: activePivot.availableFields.firstOrNull().orEmpty()
                if (firstField.isNotEmpty() && firstField !in activePivot.filterFields) {
                    onAssignPivotFieldToArea(firstField, "filter")
                }
                return
            }
            val bounds = detectTableBoundsAt(cur.viewport, cur.viewport.activeRow, cur.viewport.activeCol)
            val targetCol = cur.viewport.activeCol.coerceIn(bounds[3], bounds[4])
            onOpenColumnFilterMenu(bounds[0], targetCol)
            return
        }
        "pivotAnalyze.insertTimeline", "insert.timeline" -> {
            onInsertTextBox(if (cur.language == "en") "📅 Timeline: Q1 ▪ Q2 ▪ Q3 ▪ Q4 (All Periods)" else "📅 タイムライン: Q1 ▪ Q2 ▪ Q3 ▪ Q4 (全期間)")
            return
        }
        "pivotAnalyze.refresh", "pivotAnalyze.refreshAll", "pivot.refresh", "data.refreshAll" -> {
            onRefreshPivotTable(refreshAll = true)
            return
        }
        "pivotAnalyze.clear" -> {
            onClearPivotTable()
            return
        }
        "pivot.delete", "pivotAnalyze.delete" -> {
            onDeletePivotTable()
            return
        }
        "pivotDesign.subtotals", "pivot.subtotals" -> {
            val mode = pObj.optString("mode", "bottom")
            onSetPivotSubtotalsMode(mode)
            return
        }
        "pivotDesign.grandTotals", "pivot.grandTotals" -> {
            val nextMode = if (pObj.has("mode")) {
                pObj.optString("mode", "both")
            } else {
                if (cur.pivotGrandTotalsMode == "both") "none" else "both"
            }
            onSetPivotGrandTotalsMode(nextMode)
            return
        }
        "pivotDesign.reportLayout", "pivot.reportLayout" -> {
            if (pObj.has("repeatLabels")) {
                onTogglePivotRepeatLabels()
            } else {
                val layout = pObj.optString("layout", "tabular")
                onSetPivotReportLayout(layout)
            }
            return
        }
        "pivotDesign.blankRows" -> {
            onTogglePivotBlankRows()
            return
        }
        "pivotDesign.bandedRows" -> {
            onTogglePivotBandedRows()
            return
        }
        "pivotDesign.bandedColumns" -> {
            onTogglePivotBandedCols()
            return
        }
        "pivot.style", "pivotDesign.style" -> {
            val stylePreset = pObj.optString("style", "PivotStyleMedium2")
            onSetPivotStylePreset(stylePreset)
            return
        }
    }

    recordMacroStepIfNeeded(effectiveCmd, effectiveParams)
    val snap = engineGateway.executeCommand(effectiveCmd, effectiveParams)
    val depthDelta = if (snap.undoDepth > beforeUndoDepth) {
        snap.undoDepth - beforeUndoDepth
    } else {
        0
    }
    val inferredEngineSteps = when {
        depthDelta > 0 -> depthDelta
        snap.error == null && snap.canUndo && snap.revision > beforeRawRevision &&
            !effectiveCmd.startsWith("view.") && !effectiveCmd.startsWith("selection.") && !effectiveCmd.startsWith("app.") -> 1
        else -> 0
    }
    val currentOverlayAfter = captureCustomOverlaySnapshot()
    if (currentOverlayAfter != preCommandOverlaySnapshot || inferredEngineSteps > 0) {
        pushCustomOverlayUndoSnapshot(preCommandOverlaySnapshot.copy(engineStepsCount = inferredEngineSteps))
    }
    applyViewportSnapshot(snap, allowTitleUpdate = effectiveCmd == "file.new")
}
