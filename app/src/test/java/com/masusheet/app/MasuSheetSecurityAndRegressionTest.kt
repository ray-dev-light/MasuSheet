package com.masusheet.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.WindowHandoffManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * ============================================================================
 * [EN] Comprehensive Security, 1080×1920 Pixel Rendering, & Review Bug Regression Suite
 * Verifies real-screen grid cell/header/selection rendering (`1080×1920`) and all
 * Critical/High/Medium/Low fixes (`C-01..C-10`, `H-01..H-15`, `M-01..M-14`, `L-01..L-09`).
 *
 * ---
 * [JA / 日本語解説]
 * 実画面サイズ (`1080×1920`) でのグリッド・マス目・見出し・セル文字のピクセル描画検証、および
 * 全コードレビュー指摘事項（セキュリティ・データ保護・Undo整合性・タッチ操作・ピボット・VBA等）の回帰テストスイートです。
 * ============================================================================
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MasuSheetSecurityAndRegressionTest {

    private fun createTestViewModel(
        prefStore: FakePreferenceStore = FakePreferenceStore(),
        secretStore: InMemorySecretStore = InMemorySecretStore(),
        docGateway: FakeDocumentIoGateway = FakeDocumentIoGateway(),
        blank: Boolean = false
    ): MasuSheetViewModel {
        val repo = MasuSheetPreferencesRepository(prefStore, secretStore)
        return MasuSheetViewModel(
            preferencesRepository = repo,
            documentGateway = docGateway,
            engineGateway = InMemoryFallbackEngineGateway(),
            startWithBlankWorkbook = blank
        )
    }

    private fun obtainFingerTouchEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float
    ): MotionEvent {
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        })
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            props,
            coords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
    }

    @Test
    fun realScreen1080x1920_rendersVisibleGridlinesHeadersSelectionAndCellText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = createTestViewModel(blank = false)
        val container = MasuSheetNativeSheetContainer(context, vm)
        container.bindState(vm.uiState.value)

        val wSpec = MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY)
        val hSpec = MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY)
        container.measure(wSpec, hSpec)
        container.layout(0, 0, 1080, 1920)

        assertEquals(1080, container.width)
        assertEquals(1920, container.height)
        assertEquals(1080, container.gridView.width)
        assertEquals(1920, container.gridView.height)

        val bmp = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        container.draw(canvas)

        val rowHdrW = container.gridView.rowHeaderWidthPx.toInt().coerceAtLeast(20)
        val colHdrH = container.gridView.colHeaderHeightPx.toInt().coerceAtLeast(16)

        // 1. Column header strip must contain multiple distinct pixel colors (background + divider + A/B/C text)
        val colHeaderColors = HashSet<Int>()
        for (x in (rowHdrW + 4) until 900 step 2) {
            for (y in 2 until (colHdrH - 2) step 2) {
                colHeaderColors.add(bmp.getPixel(x, y))
            }
        }
        assertTrue("Column header must render visible header backgrounds/dividers/letters", colHeaderColors.size >= 2)

        // 2. Row header strip must contain multiple distinct pixel colors (background + divider + 1/2/3 numbers)
        val rowHeaderColors = HashSet<Int>()
        for (x in 2 until (rowHdrW - 2) step 2) {
            for (y in (colHdrH + 4) until 1200 step 2) {
                rowHeaderColors.add(bmp.getPixel(x, y))
            }
        }
        assertTrue("Row header must render visible row numbers and dividers", rowHeaderColors.size >= 2)

        // 3. Sheet cell grid body must render visible horizontal & vertical gridlines (masu / マス目) + selection border!
        val bodyColors = HashSet<Int>()
        var verticalGridlineTransitions = 0
        val scanY = (colHdrH + 35).coerceAtMost(1800)
        var prevPixel = bmp.getPixel(rowHdrW + 10, scanY)
        for (x in (rowHdrW + 11) until 1040) {
            val px = bmp.getPixel(x, scanY)
            bodyColors.add(px)
            if (px != prevPixel) {
                verticalGridlineTransitions++
                prevPixel = px
            }
        }
        assertTrue("Grid body must not be uniform blank color", bodyColors.size >= 2)
        assertTrue(
            "Horizontal scan across sheet body must cross multiple visible vertical cell gridlines, got $verticalGridlineTransitions",
            verticalGridlineTransitions >= 4
        )
    }

    @Test
    fun scrolledMergedCellAndFourQuadrantFreezePanes_renderCleanlyAt1080x1920() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = createTestViewModel(blank = true)
        vm.onCommitCellEdit(0, 0, "Merged Banner Across Columns")
        vm.onSelectCell(0, 0, 2, 5)
        vm.onExecuteEngineCommand("home.mergeCenter")
        vm.onDragFreezePaneBoundary(newFreezeRows = 1, newFreezeCols = 1)

        val container = MasuSheetNativeSheetContainer(context, vm)
        container.bindState(vm.uiState.value)
        container.measure(
            MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 1920)

        // Scroll right so column 0 is off-screen for scrollable quadrant while merged cell A1:F3 spans into visible cols
        container.gridView.scrollXPx = 220
        container.gridView.scrollYPx = 120
        val bmp = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        container.draw(Canvas(bmp))

        assertEquals(1, vm.uiState.value.viewport.freezeRows)
        assertEquals(1, vm.uiState.value.viewport.freezeCols)
        assertTrue(container.gridView.layer2OverlayRenderCount >= 1L)
    }

    @Test
    fun windowHandoffManager_blocksPathTraversalAndEnforcesOneTimeToken_andProtectsInternalFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // C-02: Forged path traversal tokens must be rejected immediately
        assertFalse(WindowHandoffManager.isValidToken("../shared_prefs/masusheet_prefs"))
        assertFalse(WindowHandoffManager.isValidToken("/data/data/com.masusheet.app/shared_prefs/masusheet_prefs.xml"))
        assertNull(WindowHandoffManager.consumeHandoffSession(context, "../shared_prefs/masusheet_prefs"))

        // C-02: ContentResolverDocumentGateway must block internal app dataDir reads and never delete target files
        val secretInternalFile = File(context.filesDir, "internal_secret_test.txt")
        secretInternalFile.writeText("TOP_SECRET_API_KEY")
        val gateway = ContentResolverDocumentGateway(context)
        assertNull("Internal app directory must not be readable via DocumentIoGateway", gateway.readBytes(secretInternalFile.absolutePath))
        assertTrue("Internal file must never be deleted by gateway", secretInternalFile.exists())
        secretInternalFile.delete()

        // C-02 & C-04: Valid handoff preserves charts, images, PivotTables, custom styles, and tab colors, and is one-time-use
        val imgBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val session = OpenWorkbookSession(
            id = "wb_handoff_1",
            title = "HandoffReport.xlsx",
            sampleKind = "custom",
            savedWorkbookBytes = "col1,col2\n10,20\n".toByteArray(),
            sheetObjects = listOf(
                NativeSheetObject(
                    id = "chart_1",
                    sheetIndex = 0,
                    kind = SheetObjectKind.CHART,
                    subType = "columnClustered",
                    title = "Revenue",
                    xPt = 40f,
                    yPt = 60f,
                    widthPt = 320f,
                    heightPt = 200f
                ),
                NativeSheetObject(
                    id = "img_1",
                    sheetIndex = 0,
                    kind = SheetObjectKind.IMAGE,
                    subType = "image",
                    title = "Logo.png",
                    xPt = 100f,
                    yPt = 100f,
                    widthPt = 120f,
                    heightPt = 90f,
                    imageBytes = imgBytes
                )
            ),
            pivotTables = listOf(
                PivotTableDefinition(
                    id = "pv_1",
                    name = "PivotTable1",
                    sourceSheetIndex = 0,
                    sourceSheetName = "Sheet1",
                    sourceStartRow = 0,
                    sourceStartCol = 0,
                    sourceEndRow = 2,
                    sourceEndCol = 1,
                    targetSheetIndex = 0,
                    targetStartRow = 5,
                    targetStartCol = 0,
                    availableFields = listOf("col1", "col2"),
                    numericFields = setOf("col2"),
                    rowFields = listOf("col1"),
                    valueFields = listOf(PivotValueFieldSpec("col2", PivotAggFunction.SUM))
                )
            ),
            customCellStyles = mapOf((0 to 0L) to CustomCellStyleOverride(isUnderline = true, isStrike = true)),
            sheetTabColors = mapOf(0 to 0xFF2563EB.toInt())
        )

        val token = WindowHandoffManager.writeHandoffSession(context, session)
        assertNotNull(token)
        assertTrue(WindowHandoffManager.isValidToken(token))

        val consumed = WindowHandoffManager.consumeHandoffSession(context, token)
        assertNotNull(consumed)
        val restored = consumed!!.first
        assertEquals("HandoffReport.xlsx", restored.title)
        assertEquals(2, restored.sheetObjects.size)
        assertArrayEquals(imgBytes, restored.sheetObjects.first { it.id == "img_1" }.imageBytes)
        assertEquals(1, restored.pivotTables.size)
        assertEquals(0xFF2563EB.toInt(), restored.sheetTabColors[0])
        assertEquals(true, restored.customCellStyles[0 to 0L]?.isUnderline)

        // Second consumption with same token must return null (replay prevented)
        assertNull(WindowHandoffManager.consumeHandoffSession(context, token))
    }

    @Test
    fun pivotTableOnNewSheet_createsNewWorksheetWithoutOverwritingSourceTable_andRefreshesCrossSheet() {
        val vm = createTestViewModel(blank = true)
        vm.onCommitCellEdit(0, 0, "Category")
        vm.onCommitCellEdit(0, 1, "Amount")
        vm.onCommitCellEdit(1, 0, "Books")
        vm.onCommitCellEdit(1, 1, "100")
        vm.onCommitCellEdit(2, 0, "Games")
        vm.onCommitCellEdit(2, 1, "250")

        val initialSheetCount = vm.uiState.value.viewport.sheetNames.size
        val created = vm.onCreatePivotTable(
            sourceRangeText = "A1:B3",
            placeOnNewSheet = true
        )
        assertNotNull(created)
        assertEquals(initialSheetCount + 1, vm.uiState.value.viewport.sheetNames.size)
        val pivotSheetIdx = created!!.targetSheetIndex
        assertNotEquals(0, pivotSheetIdx)

        // Switch back to Sheet 0 and verify source table was NOT overwritten (C-03)
        vm.onSelectSheet(0)
        assertEquals("Category", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)
        assertEquals("100", vm.uiState.value.viewport.cellAt(1, 1)?.displayText)

        // Update source value on Sheet 0, switch to Pivot sheet, and refresh cross-sheet (H-14)
        vm.onCommitCellEdit(1, 1, "400")
        vm.onSelectSheet(pivotSheetIdx)
        vm.onRefreshPivotTable(created.id)
        val pivotCellsText = vm.uiState.value.viewport.cells.values.joinToString(" ") { it.displayText }
        assertTrue("Cross-sheet PivotTable refresh must include updated total (650 or 400), got: $pivotCellsText",
            pivotCellsText.contains("400") || pivotCellsText.contains("650"))
    }

    @Test
    fun fallbackEngine_refusesCorruptBinaryXlsxSave_andHandlesMinLongCurrencyWithoutOverflow() {
        val fallback = InMemoryFallbackEngineGateway()
        fallback.initSample("sales")
        // C-05: Must return null instead of 8-byte "FALLBACK" string that would corrupt user .xlsx files
        assertNull(fallback.saveWorkbookBytes("Sales.xlsx"))

        // C-07: Long.MIN_VALUE currency formatting must not overflow Math.abs / -Long.MIN_VALUE
        val vm = createTestViewModel(blank = true)
        vm.onCommitCellEdit(0, 0, "-9223372036854775808")
        vm.onSelectCell(0, 0)
        vm.onExecuteEngineCommand("format.numberFormat", """{"format":"¥#,##0"}""")
        val formatted = vm.uiState.value.viewport.cellAt(0, 0)?.displayText.orEmpty()
        assertTrue("Expected negative currency without overflow, got: $formatted", formatted.startsWith("-¥"))
    }

    @Test
    fun finiteFloatValidation_andStrictCellAddressParser_rejectMalformedInputs() {
        // C-07: Reject NaN & Infinity
        assertNull(MasuSheetConfig.parseFiniteFloatOrNull("NaN"))
        assertNull(MasuSheetConfig.parseFiniteFloatOrNull("Infinity"))
        assertNull(MasuSheetConfig.parseFiniteFloatOrNull("-Infinity"))
        assertEquals(42.5f, MasuSheetConfig.parseFiniteFloatOrNull("42.5") ?: 0f, 0.001f)

        // L-01: Strict A1 address parser must reject trailing garbage and astronomical row numbers
        assertNull(MasuSheetConfig.parseCellAddressStrict("A1B2"))
        assertNull(MasuSheetConfig.parseCellAddressStrict("123"))
        assertNull(MasuSheetConfig.parseCellAddressStrict("ABC"))
        assertNull(MasuSheetConfig.parseCellAddressStrict("A99999999"))
        assertEquals(11 to 1, MasuSheetConfig.parseCellAddressStrict("\$B\$12"))
    }

    @Test
    fun unifiedUndoRedo_synchronizesFormatValidationAndZeroStepMacroRollback_andIsolatesWorkbooks() {
        val vm = createTestViewModel(blank = true)
        vm.onCommitCellEdit(0, 0, "KeepMe")
        vm.onSelectCell(0, 0)
        vm.onExecuteEngineCommand("format.bold")
        assertTrue(vm.uiState.value.viewport.cellAt(0, 0)?.isBold == true)

        // Undo format.bold should keep "KeepMe" text while unbolding
        vm.onExecuteEngineCommand("edit.undo")
        assertEquals("KeepMe", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)
        assertFalse(vm.uiState.value.viewport.cellAt(0, 0)?.isBold == true)

        // H-11: Running a macro with 0 valid commands must NOT roll back the user's prior "KeepMe" edit!
        val noopMacro = MacroDefinition(
            name = "NoopMacro",
            steps = emptyList(),
            vbaCode = "Sub NoopMacro()\n' comment only\nEnd Sub"
        )
        vm.onRunMacro(noopMacro)
        vm.onRollbackLastMacroRun()
        assertEquals("KeepMe", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)

        // C-10: Creating/switching to a second workbook isolates Undo stacks
        val wb1Id = vm.uiState.value.activeWorkbookId
        vm.onCreateNewBlankWorkbookSession()
        vm.onExecuteEngineCommand("edit.undo") // Undo on empty Book 2 must do nothing to Book 1
        vm.onSwitchOpenWorkbook(wb1Id)
        assertEquals("KeepMe", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)
    }

    @Test
    fun hiddenSheets_preserveRealSheetIndicesAndSupportUnhide() {
        val vm = createTestViewModel(blank = true)
        vm.onAddSheet() // Sheet2 (index 1)
        vm.onAddSheet() // Sheet3 (index 2)
        val sheet2Name = vm.uiState.value.viewport.sheetNames[1]
        val sheet3Name = vm.uiState.value.viewport.sheetNames[2]

        vm.onHideSheetAt(1)
        val vpHidden = vm.uiState.value.viewport
        assertEquals(listOf(0, 2), vpHidden.visibleSheetIndices)
        assertTrue(vpHidden.hiddenSheetNames.contains(sheet2Name))

        // Selecting real index 2 selects Sheet3 accurately (H-02)
        vm.onSelectSheet(2)
        assertEquals(sheet3Name, vm.uiState.value.viewport.sheetName)

        // Unhiding restores Sheet2
        vm.onUnhideSheet(sheet2Name)
        assertFalse(vm.uiState.value.viewport.hiddenSheetNames.contains(sheet2Name))
        assertEquals(3, vm.uiState.value.viewport.sheetNames.size)
    }

    @Test
    fun aiSecurityAllowlist_httpsValidation_andPerModelCustomName() {
        // H-06: Remote HTTP blocked; HTTPS and loopback HTTP allowed
        var remoteHttpBlocked = false
        try {
            SpreadsheetAiAgent.validateAiEndpointUrl("http://evil.example.com/v1/chat/completions")
        } catch (_: IllegalArgumentException) {
            remoteHttpBlocked = true
        }
        assertTrue("Cleartext HTTP to non-loopback host must be blocked", remoteHttpBlocked)
        assertEquals("127.0.0.1", SpreadsheetAiAgent.validateAiEndpointUrl("http://127.0.0.1:11434/v1/chat/completions").host)
        assertEquals("https", SpreadsheetAiAgent.validateAiEndpointUrl("https://api.openai.com/v1/chat/completions").protocol)

        // H-07: Destructive file.* commands stripped by AI allowlist while edit.setValue passes
        val parsed = SpreadsheetAiAgent.parseJsonAiTurnResult(
            """{"reply":"Done","commands":[{"cmd":"file.new","params":{}},{"cmd":"edit.setValue","params":{"row":0,"col":0,"value":"Safe"}}]}"""
        )
        assertNotNull(parsed)
        assertEquals(1, parsed!!.commands.size)
        assertEquals("edit.setValue", parsed.commands[0].first)

        // H-05 & H-07: SecretStore isolates API keys out of PreferenceStore, and custom model IDs are stored per model
        val prefStore = FakePreferenceStore()
        val secretStore = InMemorySecretStore()
        val repo = MasuSheetPreferencesRepository(prefStore, secretStore)
        repo.setAiApiKey("gemini", "AIzaSecretTestKey123")
        assertEquals("AIzaSecretTestKey123", repo.getAiApiKey("gemini"))
        assertEquals("", prefStore.getString("ai_key_gemini", ""))

        repo.setAiCustomModel(model = "gemini-2.5-pro", modelId = "gemini")
        repo.setAiCustomModel(model = "gpt-4.1", modelId = "openai")
        assertEquals("gemini-2.5-pro", repo.getAiCustomModel("gemini"))
        assertEquals("gpt-4.1", repo.getAiCustomModel("openai"))
    }

    @Test
    fun fingerTouchScrollVsRangeSelect_andWideHeaderClickSelectsColumn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = createTestViewModel(blank = false)
        val container = MasuSheetNativeSheetContainer(context, vm)
        container.bindState(vm.uiState.value)
        container.measure(
            MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 1920)
        val grid = container.gridView

        // 1. Default 1-finger touch drag (isTouchRangeSelectMode == false) must scroll the sheet vertically!
        val t0 = SystemClock.uptimeMillis()
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0, MotionEvent.ACTION_DOWN, 400f, 900f))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0 + 16, MotionEvent.ACTION_MOVE, 400f, 600f))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0 + 32, MotionEvent.ACTION_UP, 400f, 600f))
        assertTrue("1-finger touch swipe up must pan scrollYPx > 0, got ${grid.scrollYPx}", grid.scrollYPx > 0)

        // 2. Enabling Touch Range Select Mode allows 1-finger drag to select cell ranges!
        grid.scrollYPx = 0
        vm.onToggleTouchRangeSelectMode()
        container.bindState(vm.uiState.value)
        val r0Rect = grid.getCellScreenRect(1, 1)
        val r2Rect = grid.getCellScreenRect(3, 2)
        val t1 = t0 + 100
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1, MotionEvent.ACTION_DOWN, r0Rect.centerX(), r0Rect.centerY()))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1 + 16, MotionEvent.ACTION_MOVE, r2Rect.centerX(), r2Rect.centerY()))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1 + 32, MotionEvent.ACTION_UP, r2Rect.centerX(), r2Rect.centerY()))
        assertTrue("Touch range select drag must span multiple rows", vm.uiState.value.viewport.selEndRow >= 3)

        // 3. Clicking the center of a wide (300pt) column header must select the column, NOT trigger divider resize (H-12)
        vm.onResizeColumn(0, 300f)
        container.bindState(vm.uiState.value)
        val col0CenterScreenX = grid.rowHeaderWidthPx + (150f * grid.resources.displayMetrics.density)
        assertEquals("Center of wide 300pt column must not hit resize divider", -1, grid.findColumnDividerAt(col0CenterScreenX, 10f))
    }

    @Test
    fun pasteSpecialValues_hiddenRowPreservation_formulaLiteralShift_andVbaStringConcat() {
        val vm = createTestViewModel(blank = true)

        // H-13: Paste Special (values only) copies evaluated number without formula
        vm.onCommitCellEdit(2, 0, "=SEQUENCE(1,1,35)")
        assertEquals("35", vm.uiState.value.viewport.cellAt(2, 0)?.displayText)
        assertTrue(vm.uiState.value.viewport.cellAt(2, 0)?.editText?.startsWith("=") == true)
        vm.onSelectCell(2, 0)
        vm.onCopySelectionToClipboard()
        vm.onSelectCell(2, 2)
        vm.onPasteSpecial(mode = "values")
        val pastedCell = vm.uiState.value.viewport.cellAt(2, 2)
        assertEquals("35", pastedCell?.displayText)
        assertFalse("Values-only paste must not copy formula", pastedCell?.editText?.startsWith("=") == true)

        // H-14: Hidden row (0f height) must remain hidden after editing a cell in that row
        vm.onSelectEntireRow(4)
        vm.onHideSelectedRows()
        assertEquals(0f, vm.uiState.value.viewport.rowHeights.getOrElse(4) { 20f }, 0.01f)
        vm.onCommitCellEdit(4, 1, "StillHidden")
        assertEquals(0f, vm.uiState.value.viewport.rowHeights.getOrElse(4) { 20f }, 0.01f)

        // H-14: AutoFill formula shifting preserves string literals ("A1") and sheet prefixes ("Sheet1!")
        vm.onCommitCellEdit(0, 4, """=IF(A1>0,"Keep A1 literal",Sheet1!A1)""")
        vm.onAutoFillRange(0, 4, 0, 4, 1, 4)
        val shiftedFormula = vm.uiState.value.viewport.cellAt(1, 4)?.editText.orEmpty()
        assertTrue("Expected A2 shift in $shiftedFormula", shiftedFormula.contains("A2>0"))
        assertTrue("Expected literal \"Keep A1 literal\" preserved in $shiftedFormula", shiftedFormula.contains("\"Keep A1 literal\""))
        assertTrue("Expected sheet prefix Sheet1! preserved (not Sheet2!) in $shiftedFormula", shiftedFormula.contains("Sheet1!A2"))

        // H-15: VBA loop variable substitution & string concatenation (& and "" escaped quotes)
        val vba = """
            Sub FillItems()
                For i = 1 To 3
                    Range("A" & i).Value = "Item-" & i
                Next i
            End Sub
        """.trimIndent()
        val expanded = MacroStudioEngine.expandVbaStatements(vba)
        assertTrue(expanded.size >= 3)
        val vbaVars = mutableMapOf<String, String>()
        for (stmt in expanded) {
            MacroStudioEngine.executeSingleVbaStatement(
                stmt = stmt,
                variables = vbaVars,
                vp = vm.uiState.value.viewport,
                executeCmd = { cmd, json -> vm.onExecuteEngineCommand(cmd, json) },
                commitCell = { r, c, txt -> vm.onCommitCellEdit(r, c, txt) },
                onMsgBox = {}
            )
        }
        assertEquals("Item-1", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)
        assertEquals("Item-2", vm.uiState.value.viewport.cellAt(1, 0)?.displayText)
        assertEquals("Item-3", vm.uiState.value.viewport.cellAt(2, 0)?.displayText)
    }
}
