package com.masusheet.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masusheet.app.ai.MacroDefinition
import com.masusheet.app.ai.MacroStudioEngine
import com.masusheet.app.ai.SpreadsheetAiAgent
import com.masusheet.app.bridge.InMemoryFallbackEngineGateway
import com.masusheet.app.bridge.RustJniEngineGateway
import com.masusheet.app.canvas.MasuSheetNativeSheetContainer
import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.model.CustomCellStyleOverride
import com.masusheet.app.model.NativeSheetObject
import com.masusheet.app.model.OpenWorkbookSession
import com.masusheet.app.model.PivotAggFunction
import com.masusheet.app.model.PivotTableDefinition
import com.masusheet.app.model.PivotValueFieldSpec
import com.masusheet.app.model.SheetObjectKind
import com.masusheet.app.storage.ContentResolverDocumentGateway
import com.masusheet.app.storage.DocumentIoGateway
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.MasuSheetPreferencesRepository
import com.masusheet.app.storage.PreferenceStore
import com.masusheet.app.storage.SecretStore
import com.masusheet.app.storage.WindowHandoffManager
import com.masusheet.app.viewmodel.MasuSheetViewModel
import com.masusheet.app.viewmodel.buildExportBytesForFormat
import com.masusheet.app.viewmodel.onAddSheet
import com.masusheet.app.viewmodel.onAutoFillRange
import com.masusheet.app.viewmodel.onCompletePendingSave
import com.masusheet.app.viewmodel.onCreateNewBlankWorkbookSession
import com.masusheet.app.viewmodel.onCreatePivotTable
import com.masusheet.app.viewmodel.onDragFreezePaneBoundary
import com.masusheet.app.viewmodel.onExecuteConfirmedAiPlan
import com.masusheet.app.viewmodel.onOpenSaveDialog
import com.masusheet.app.viewmodel.onSelectSaveDialogExtension
import com.masusheet.app.viewmodel.onHideSelectedRows
import com.masusheet.app.viewmodel.onHideSheetAt
import com.masusheet.app.viewmodel.onInsertChart
import com.masusheet.app.viewmodel.onInsertShape
import com.masusheet.app.viewmodel.onOpenDocumentUri
import com.masusheet.app.viewmodel.onPasteScreenshotOrLatestImage
import com.masusheet.app.viewmodel.onRefreshPivotTable
import com.masusheet.app.viewmodel.onRollbackLastMacroRun
import com.masusheet.app.viewmodel.onRunMacro
import com.masusheet.app.viewmodel.onSelectAiEditMode
import com.masusheet.app.viewmodel.onSelectSheetObject
import com.masusheet.app.viewmodel.onSendAiPrompt
import com.masusheet.app.viewmodel.onSwitchOpenWorkbook
import com.masusheet.app.viewmodel.onToggleToolbar
import com.masusheet.app.viewmodel.onTriggerSave
import com.masusheet.app.viewmodel.onUpdateChartAxisFormat
import com.masusheet.app.viewmodel.onUpdateSheetObjectText
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

        // 1. Default 1-finger touch drag on unselected cells must scroll the sheet vertically!
        val t0 = SystemClock.uptimeMillis()
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0, MotionEvent.ACTION_DOWN, 400f, 900f))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0 + 16, MotionEvent.ACTION_MOVE, 400f, 600f))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0 + 32, MotionEvent.ACTION_UP, 400f, 600f))
        assertTrue("1-finger touch swipe up must pan scrollYPx > 0, got ${grid.scrollYPx}", grid.scrollYPx > 0)

        // 2. Pressing inside the currently selected cell and dragging expands the 2D cell selection directly!
        grid.scrollYPx = 0
        vm.onSelectCell(1, 1)
        container.bindState(vm.uiState.value)
        val r0Rect = grid.getCellScreenRect(1, 1)
        val r2Rect = grid.getCellScreenRect(3, 2)
        val t1 = t0 + 100
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1, MotionEvent.ACTION_DOWN, r0Rect.centerX(), r0Rect.centerY()))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1 + 16, MotionEvent.ACTION_MOVE, r2Rect.centerX(), r2Rect.centerY()))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1 + 32, MotionEvent.ACTION_UP, r2Rect.centerX(), r2Rect.centerY()))
        assertTrue("Selected-cell touch drag must span multiple rows", vm.uiState.value.viewport.selEndRow >= 3)

        // 3. Clicking the center of a wide (300pt) column header must select the column, NOT trigger divider resize (H-12)
        vm.onResizeColumn(0, 300f)
        container.bindState(vm.uiState.value)
        val col0CenterScreenX = grid.rowHeaderWidthPx + (150f * grid.resources.displayMetrics.density)
        assertEquals("Center of wide 300pt column must not hit resize divider", -1, grid.findColumnDividerAt(col0CenterScreenX, 10f))
    }

    @Test
    fun smartphoneAndTabletTouch_selectedCellDragExpandsRangeWithoutMovingCell() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = createTestViewModel(blank = true)
        vm.onCommitCellEdit(2, 2, "KeepInC3")
        val container = MasuSheetNativeSheetContainer(context, vm)
        container.bindState(vm.uiState.value)
        container.measure(
            MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 1920)
        val grid = container.gridView

        // 1. Tap C3 (row=2, col=2) with 1 finger -> selects C3 on ACTION_UP
        val c3Rect = grid.getCellScreenRect(2, 2)
        val t0 = SystemClock.uptimeMillis()
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0, MotionEvent.ACTION_DOWN, c3Rect.centerX(), c3Rect.centerY()))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0 + 20, MotionEvent.ACTION_UP, c3Rect.centerX(), c3Rect.centerY()))
        assertEquals(2, vm.uiState.value.viewport.activeRow)
        assertEquals(2, vm.uiState.value.viewport.activeCol)

        // 2. Immediately (within 180ms!) press inside selected C3 (even near top border where mouse border-move lives) and drag to E7
        //    -> Must NOT open in-cell editor mid-drag, must NOT cut/move C3's value, and MUST expand selection to C3:E7!
        val e7Rect = grid.getCellScreenRect(6, 4)
        val nearTopInsideC3Y = c3Rect.top + 2f * grid.resources.displayMetrics.density
        val t1 = t0 + 180
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1, MotionEvent.ACTION_DOWN, c3Rect.centerX(), nearTopInsideC3Y))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1 + 25, MotionEvent.ACTION_MOVE, e7Rect.centerX(), e7Rect.centerY()))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1 + 50, MotionEvent.ACTION_UP, e7Rect.centerX(), e7Rect.centerY()))

        assertFalse("Rapid tap-then-drag must not open in-cell editor", container.isEditingInCell)
        assertEquals("C3 value must not be cut/moved on finger drag", "KeepInC3", vm.uiState.value.viewport.cellAt(2, 2)?.displayText)
        assertEquals("", vm.uiState.value.viewport.cellAt(6, 4)?.displayText.orEmpty())
        assertEquals(0, grid.scrollYPx)
        assertEquals(2, vm.uiState.value.viewport.selStartRow)
        assertEquals(2, vm.uiState.value.viewport.selStartCol)
        assertEquals(6, vm.uiState.value.viewport.selEndRow)
        assertEquals(4, vm.uiState.value.viewport.selEndCol)

        // 3. Swiping up on an unselected cell scrolls the sheet WITHOUT losing the C3:E7 selection!
        val unSelectedStart = grid.getCellScreenRect(14, 1)
        val t2 = t1 + 600
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t2, t2, MotionEvent.ACTION_DOWN, unSelectedStart.centerX(), unSelectedStart.centerY()))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t2, t2 + 25, MotionEvent.ACTION_MOVE, unSelectedStart.centerX(), unSelectedStart.centerY() - 240f))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t2, t2 + 50, MotionEvent.ACTION_UP, unSelectedStart.centerX(), unSelectedStart.centerY() - 240f))
        assertTrue("1-finger swipe outside selection must scroll", grid.scrollYPx > 0)
        assertEquals("Selection C3:E7 must stay intact during scroll", 2, vm.uiState.value.viewport.selStartRow)
        assertEquals(6, vm.uiState.value.viewport.selEndRow)

        // 4. Dragging the circular top-left selection handle resizes C3:E7 -> B2:E7
        grid.scrollYPx = 0
        container.bindState(vm.uiState.value)
        val selRect = grid.getSelectionScreenRect()
        assertTrue(grid.isPointOnTopLeftSelectionRangeHandle(selRect.left, selRect.top))
        val b2Rect = grid.getCellScreenRect(1, 1)
        val t3 = t2 + 600
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t3, t3, MotionEvent.ACTION_DOWN, selRect.left, selRect.top))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t3, t3 + 25, MotionEvent.ACTION_MOVE, b2Rect.centerX(), b2Rect.centerY()))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t3, t3 + 50, MotionEvent.ACTION_UP, b2Rect.centerX(), b2Rect.centerY()))
        assertEquals(1, kotlin.math.min(vm.uiState.value.viewport.selStartRow, vm.uiState.value.viewport.selEndRow))
        assertEquals(1, kotlin.math.min(vm.uiState.value.viewport.selStartCol, vm.uiState.value.viewport.selEndCol))
        assertEquals(6, kotlin.math.max(vm.uiState.value.viewport.selStartRow, vm.uiState.value.viewport.selEndRow))
        assertEquals(4, kotlin.math.max(vm.uiState.value.viewport.selStartCol, vm.uiState.value.viewport.selEndCol))
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

    @Test
    fun shelfShortcuts_onlyTwoDynamicShortcutsRegistered_andPreserveEnteredDataOnNewWindowAndNewBlank() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        // 1. Static <meta-data android:name="android.app.shortcuts"> MUST NOT exist on MainActivity
        //    (prevents 4-item Shelf menu duplication and Android ShortcutParser's forced FLAG_ACTIVITY_CLEAR_TASK data wipe)
        val activityInfo = ctx.packageManager.getActivityInfo(
            ComponentName(ctx, MainActivity::class.java),
            PackageManager.GET_META_DATA
        )
        assertFalse(
            "Static android.app.shortcuts metadata must be absent to prevent 4-item duplication and CLEAR_TASK data loss",
            activityInfo.metaData?.containsKey("android.app.shortcuts") == true
        )

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val sm = ctx.getSystemService(ShortcutManager::class.java)
            assertNotNull(sm)
            val dynShortcuts = sm!!.dynamicShortcuts
            assertEquals("Exactly 2 Shelf shortcuts must be registered", 2, dynShortcuts.size)
            assertEquals(
                listOf("dyn_new_window_workbook", "dyn_new_blank_workbook_window"),
                dynShortcuts.map { it.id }
            )

            val newWinIntent = dynShortcuts[0].intent!!
            val newBlankIntent = dynShortcuts[1].intent!!
            for (shortcutIntent in listOf(newWinIntent, newBlankIntent)) {
                val flags = shortcutIntent.flags
                assertTrue(
                    "Shortcut intent must carry FLAG_ACTIVITY_SINGLE_TOP",
                    (flags and Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0
                )
                assertTrue(
                    "Shortcut intent must carry FLAG_ACTIVITY_CLEAR_TOP",
                    (flags and Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0
                )
                assertEquals(
                    "Shortcut intent must NEVER carry destructive FLAG_ACTIVITY_CLEAR_TASK",
                    0,
                    flags and Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            }

            // 2. Enter sheet data in Book1.xlsx (plus an uncommitted edit via beforeExternalNavigationCommitHook)
            val vm = activity.activeViewModelForTest
            val book1Id = vm.uiState.value.activeWorkbookId
            vm.onCommitCellEdit(1, 1, "保持される入力データ")
            var pendingDraft = "未確定セル入力"
            vm.beforeExternalNavigationCommitHook = {
                if (pendingDraft.isNotEmpty()) {
                    vm.onCommitCellEdit(2, 1, pendingDraft, moveDeltaRow = 0)
                    pendingDraft = ""
                }
            }

            // 3. Trigger Shelf "新しいウィンドウ" (New Window) via onNewIntent -> spawns SecondaryWorkbookActivity without losing Book1 data
            Shadows.shadowOf(activity).clearNextStartedActivities()
            controller.newIntent(newWinIntent)
            val startedWindowIntent = Shadows.shadowOf(activity).nextStartedActivity
            assertNotNull("Expected SecondaryWorkbookActivity to be launched", startedWindowIntent)
            assertEquals(SecondaryWorkbookActivity::class.java.name, startedWindowIntent.component?.className)
            assertEquals("保持される入力データ", vm.uiState.value.viewport.cellAt(1, 1)?.displayText)
            assertEquals("未確定セル入力", vm.uiState.value.viewport.cellAt(2, 1)?.displayText)

            // 4. Trigger Shelf "新規空白ブック" (New Blank Book) via onNewIntent -> adds Book2.xlsx while keeping Book1.xlsx intact
            controller.newIntent(newBlankIntent)
            assertEquals("Book2.xlsx", vm.uiState.value.workbookTitle)
            assertEquals(2, vm.uiState.value.openWorkbooks.size)

            // Switch back to Book1.xlsx and verify every entered cell is still 100% intact
            assertTrue(vm.onSwitchOpenWorkbook(book1Id))
            assertEquals("保持される入力データ", vm.uiState.value.viewport.cellAt(1, 1)?.displayText)
            assertEquals("未確定セル入力", vm.uiState.value.viewport.cellAt(2, 1)?.displayText)
        } finally {
            controller.destroy()
        }
    }

    private fun obtainTwoFingerTouchEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ): MotionEvent {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
            MotionEvent.PointerProperties().apply {
                id = 1
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = x0
                y = y0
                pressure = 1f
                size = 1f
            },
            MotionEvent.PointerCoords().apply {
                x = x1
                y = y1
                pressure = 1f
                size = 1f
            }
        )
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            2,
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
    fun smartphoneTouch_scrollWhileChartOrImageSelected_keepsObjectSelected_andTapOutsideDeselects() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = createTestViewModel(blank = true)
        vm.onCommitCellEdit(0, 0, "X")
        vm.onCommitCellEdit(0, 1, "Y")
        vm.onCommitCellEdit(1, 0, "1")
        vm.onCommitCellEdit(1, 1, "10")
        vm.onCommitCellEdit(2, 0, "2")
        vm.onCommitCellEdit(2, 1, "20")
        vm.onSelectCell(0, 0, 2, 1)
        vm.onInsertChart("scatter_lines")
        val chart = vm.uiState.value.sheetObjects.single()
        val chartId = chart.id
        val origXPt = chart.xPt
        val origYPt = chart.yPt
        assertEquals(chartId, vm.uiState.value.selectedObjectId)

        val container = MasuSheetNativeSheetContainer(context, vm)
        container.bindState(vm.uiState.value)
        container.measure(
            MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 1920)
        val grid = container.gridView

        // 1. 1-finger swipe UP on the grid outside the chart MUST scroll the sheet while keeping the chart selected!
        val swipeCellRect = grid.getCellScreenRect(25, 1)
        val t0 = SystemClock.uptimeMillis()
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0, MotionEvent.ACTION_DOWN, swipeCellRect.centerX(), swipeCellRect.centerY()))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0 + 20, MotionEvent.ACTION_MOVE, swipeCellRect.centerX(), swipeCellRect.centerY() - 260f))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t0, t0 + 40, MotionEvent.ACTION_UP, swipeCellRect.centerX(), swipeCellRect.centerY() - 260f))
        assertTrue("1-finger swipe while chart selected must scroll sheet", grid.scrollYPx > 0)
        assertEquals("Chart must remain selected during 1-finger sheet scroll", chartId, vm.uiState.value.selectedObjectId)

        // 2. 2-finger pan starting DIRECTLY ON TOP OF the selected chart body must cancel 1-finger drag, keep chart at original position, and scroll while keeping chart selected!
        grid.scrollYPx = 0
        container.bindState(vm.uiState.value)
        val chartRect = grid.getSheetObjectScreenRect(chart)
        val cx = chartRect.centerX()
        val cy = chartRect.centerY()
        val t1 = t0 + 600
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1, MotionEvent.ACTION_DOWN, cx - 30f, cy))
        val ptr2Down = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        grid.dispatchTouchEvent(obtainTwoFingerTouchEvent(t1, t1 + 10, ptr2Down, cx - 30f, cy, cx + 30f, cy))
        grid.dispatchTouchEvent(obtainTwoFingerTouchEvent(t1, t1 + 30, MotionEvent.ACTION_MOVE, cx - 30f, cy - 220f, cx + 30f, cy - 220f))
        val ptr2Up = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        grid.dispatchTouchEvent(obtainTwoFingerTouchEvent(t1, t1 + 45, ptr2Up, cx - 30f, cy - 220f, cx + 30f, cy - 220f))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t1, t1 + 60, MotionEvent.ACTION_UP, cx - 30f, cy - 220f))

        assertTrue("2-finger pan on top of chart must scroll sheet", grid.scrollYPx > 0)
        assertEquals("Chart must remain selected after 2-finger pan", chartId, vm.uiState.value.selectedObjectId)
        val afterPanChart = vm.uiState.value.sheetObjects.single()
        assertEquals("Chart xPt must not drift during 2-finger pan", origXPt, afterPanChart.xPt, 0.01f)
        assertEquals("Chart yPt must not drift during 2-finger pan", origYPt, afterPanChart.yPt, 0.01f)

        // 3. 1-finger stationary tap (no movement) on an empty cell deselects the chart and selects that cell!
        grid.scrollYPx = 0
        container.bindState(vm.uiState.value)
        val tapCellRect = grid.getCellScreenRect(22, 1)
        val t2 = t1 + 600
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t2, t2, MotionEvent.ACTION_DOWN, tapCellRect.centerX(), tapCellRect.centerY()))
        grid.dispatchTouchEvent(obtainFingerTouchEvent(t2, t2 + 20, MotionEvent.ACTION_UP, tapCellRect.centerX(), tapCellRect.centerY()))
        assertNull("Stationary tap outside chart must deselect chart", vm.uiState.value.selectedObjectId)
        assertEquals(22, vm.uiState.value.viewport.activeRow)
        assertEquals(1, vm.uiState.value.viewport.activeCol)
    }

    @Test
    fun chartImageAndShapePersistence_savedXlsxContainsOoxmlChartAndRestoresLosslesslyOnReopen() {
        val docGw = FakeDocumentIoGateway()
        val repo1 = MasuSheetPreferencesRepository(FakePreferenceStore(), InMemorySecretStore())
        val vm1 = MasuSheetViewModel(
            preferencesRepository = repo1,
            documentGateway = docGw,
            engineGateway = RustJniEngineGateway(),
            startWithBlankWorkbook = true
        )
        vm1.onCommitCellEdit(0, 0, "Month")
        vm1.onCommitCellEdit(0, 1, "Revenue")
        vm1.onCommitCellEdit(1, 0, "Jan")
        vm1.onCommitCellEdit(1, 1, "150")
        vm1.onCommitCellEdit(2, 0, "Feb")
        vm1.onCommitCellEdit(2, 1, "280")
        vm1.onCommitCellEdit(3, 0, "Mar")
        vm1.onCommitCellEdit(3, 1, "410")

        vm1.onSelectCell(0, 0, 3, 1)
        vm1.onInsertChart("scatter_lines")
        val createdChartId = vm1.uiState.value.sheetObjects.single().id
        vm1.onUpdateSheetObjectText(createdChartId, "Q1 Growth")
        vm1.onUpdateChartAxisFormat(objectId = createdChartId, isXAxis = false, majorStep = 50f)

        vm1.onInsertShape("rounded_rect")
        val createdShapeId = vm1.uiState.value.sheetObjects.last().id
        vm1.onUpdateSheetObjectText(createdShapeId, "Summary Note")

        val tinyPng = ByteArray(16).also {
            val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            System.arraycopy(sig, 0, it, 0, sig.size)
        }
        assertTrue(vm1.onPasteScreenshotOrLatestImage(tinyPng, "Logo.png"))
        assertEquals(3, vm1.uiState.value.sheetObjects.size)

        // Save workbook via standard save flow into FakeDocumentIoGateway
        val uri = "content://test/ChartPersistenceTest.xlsx"
        vm1.onTriggerSave()
        assertTrue(vm1.onCompletePendingSave(uri))
        val savedBytes = docGw.files[uri]
        assertNotNull("Saved .xlsx bytes must exist", savedBytes)
        assertTrue(savedBytes!!.size > 200)

        // Verify ZIP entries contain BOTH genuine Excel OOXML chart/drawing/media AND lossless MasuSheet customXml
        val zipEntries = mutableSetOf<String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(savedBytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                zipEntries.add(e.name)
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        assertTrue("Expected genuine Excel OOXML chart xl/charts/chart1.xml in $zipEntries", zipEntries.any { it.startsWith("xl/charts/chart") && it.endsWith(".xml") })
        assertTrue("Expected genuine Excel OOXML drawing xl/drawings/drawing1.xml in $zipEntries", zipEntries.any { it.startsWith("xl/drawings/drawing") && it.endsWith(".xml") })
        assertTrue("Expected genuine Excel OOXML media image in $zipEntries", zipEntries.any { it.startsWith("xl/media/") })
        assertTrue("Expected MasuSheet lossless customXml overlay in $zipEntries", "customXml/masusheet_objects_v1.xml" in zipEntries)

        // Reopen the saved .xlsx in a completely fresh MasuSheetViewModel instance
        val repo2 = MasuSheetPreferencesRepository(FakePreferenceStore(), InMemorySecretStore())
        val vm2 = MasuSheetViewModel(
            preferencesRepository = repo2,
            documentGateway = docGw,
            engineGateway = RustJniEngineGateway(),
            startWithBlankWorkbook = true
        )
        assertTrue(vm2.onOpenDocumentUri(uri))
        assertEquals(3, vm2.uiState.value.sheetObjects.size)

        val restoredChart = vm2.uiState.value.sheetObjects.first { it.kind == SheetObjectKind.CHART }
        assertEquals("scatter_lines", restoredChart.subType)
        assertEquals("Q1 Growth", restoredChart.title)
        assertEquals(50f, restoredChart.yAxisMajorStep ?: 0f, 0.01f)
        assertEquals(listOf(150f, 280f, 410f), restoredChart.chartSeries.first().values)

        val restoredShape = vm2.uiState.value.sheetObjects.first { it.kind == SheetObjectKind.SHAPE }
        assertEquals("roundRect", restoredShape.subType)
        assertEquals("Summary Note", restoredShape.text)
        assertEquals("Summary Note", restoredShape.title)

        val restoredImg = vm2.uiState.value.sheetObjects.first { it.kind == SheetObjectKind.IMAGE }
        assertArrayEquals(tinyPng, restoredImg.imageBytes)

        // Inserting a new object after reopen must get a unique non-colliding ID
        val existingIds = vm2.uiState.value.sheetObjects.map { it.id }.toSet()
        vm2.onInsertShape("rectangle")
        assertEquals(4, vm2.uiState.value.sheetObjects.size)
        assertFalse(vm2.uiState.value.sheetObjects.last().id in existingIds)
    }

    @Test
    fun fullRibbonShowAndHide_togglesEntireRibbonAndStaysHiddenWhenSelectingChartOrCell() {
        val vm = createTestViewModel(blank = true)
        assertTrue(vm.uiState.value.toolbarVisible)

        vm.onToggleToolbar()
        assertFalse("Ribbon must hide when toggled off", vm.uiState.value.toolbarVisible)

        // Selecting cells or inserting/selecting objects must NOT force the hidden ribbon back open
        vm.onCommitCellEdit(0, 0, "100")
        vm.onInsertShape("rectangle")
        val shapeId = vm.uiState.value.sheetObjects.single().id
        vm.onSelectSheetObject(shapeId)
        assertFalse("Ribbon must stay hidden while selecting objects", vm.uiState.value.toolbarVisible)

        vm.onToggleToolbar()
        assertTrue("Ribbon must reappear when toggled back on", vm.uiState.value.toolbarVisible)
    }

    @Test
    fun phase2And3A_crossSheetPivotRefreshReadsRemoteRustSheetCells() {
        val repo = MasuSheetPreferencesRepository(FakePreferenceStore(), InMemorySecretStore())
        val vm = MasuSheetViewModel(
            preferencesRepository = repo,
            documentGateway = FakeDocumentIoGateway(),
            engineGateway = RustJniEngineGateway(),
            startWithBlankWorkbook = true
        )
        // Populate source table on Sheet 0 (A1:B3)
        vm.onCommitCellEdit(0, 0, "Category")
        vm.onCommitCellEdit(0, 1, "Amount")
        vm.onCommitCellEdit(1, 0, "Books")
        vm.onCommitCellEdit(1, 1, "100")
        vm.onCommitCellEdit(2, 0, "Books")
        vm.onCommitCellEdit(2, 1, "250")

        // Create PivotTable on a NEW sheet (Sheet 1)
        val created = vm.onCreatePivotTable(sourceRangeText = "A1:B3", placeOnNewSheet = true)
        assertNotNull("PivotTable on new sheet must be created", created)
        assertEquals(1, vm.uiState.value.viewport.sheetIndex)

        // Switch back to Sheet 0 and update B3 from 250 -> 900
        vm.onSelectSheet(0)
        vm.onCommitCellEdit(2, 1, "900")
        assertEquals("900", vm.engineGateway.readSheetCells(0)[0L.shl(32) or 0L]?.let { vm.uiState.value.viewport.cellAt(2, 1)?.displayText })

        // Verify RustJniEngineGateway.readSheetCells(0) works even while active sheet is Sheet 1
        vm.onSelectSheet(1)
        val remoteSheet0 = vm.engineGateway.readSheetCells(0)
        val keyB3 = (2L shl 32) or (1L and 0xFFFFFFFFL)
        assertEquals("900", remoteSheet0[keyB3]?.displayText)

        // Refresh PivotTable from Sheet 1; aggregated Books total must become 100 + 900 = 1000 ("¥1,000" or "1,000")
        vm.onRefreshPivotTable(created!!.id)
        val sheet1Texts = vm.uiState.value.viewport.cells.values.map { it.displayText }
        assertTrue(
            "Refreshed cross-sheet PivotTable must include updated sum 1000 (got $sheet1Texts)",
            sheet1Texts.any { it.replace("¥", "").replace("$", "").replace(",", "").trim() == "1000" }
        )
    }

    @Test
    fun phase3A_macroVbaLoopExtremeIntBoundsAndCommandAllowlistGuard() {
        // Step -2147483648 (Int.MIN_VALUE) must not overflow abs() or hang; emits ["i = 5", "Range(\"A5\").Value = \"ok\""]
        val minStepCode = """
            Sub ExtremeNegativeStep()
                For i = 5 To 1 Step -2147483648
                    Range("A" & i).Value = "ok"
                Next i
            End Sub
        """.trimIndent()
        val expandedMinStep = MacroStudioEngine.expandVbaStatements(minStepCode)
        assertEquals(2, expandedMinStep.size)
        assertEquals("i = 5", expandedMinStep[0])
        assertTrue(expandedMinStep[1].contains("A5"))

        // Near Int.MAX_VALUE upper bound must preserve exact 32-bit values and terminate cleanly without wraparound
        val maxBoundCode = """
            Sub ExtremeUpperBound()
                For i = 2147483646 To 2147483647
                    Cells(1, 1).Value = i
                Next i
            End Sub
        """.trimIndent()
        val expandedMax = MacroStudioEngine.expandVbaStatements(maxBoundCode)
        assertEquals(4, expandedMax.size)
        assertEquals("i = 2147483646", expandedMax[0])
        assertEquals("i = 2147483647", expandedMax[2])

        // Destructive commands via Application.Run must be blocked by SpreadsheetAiAgent.isAllowedAiCommand
        val vm = createTestViewModel(blank = true)
        vm.onCommitCellEdit(0, 0, "KeepMe")
        vm.onAddSheet()
        assertEquals(2, vm.uiState.value.viewport.sheetNames.size)
        val maliciousMacro = MacroDefinition(
            name = "TryDeleteSheet",
            vbaCode = """
                Sub TryDeleteSheet()
                    Application.Run "home.deleteSheet"
                    Application.Run "file.new"
                End Sub
            """.trimIndent()
        )
        vm.onRunMacro(maliciousMacro)
        assertEquals("Destructive Application.Run must not delete sheets", 2, vm.uiState.value.viewport.sheetNames.size)
        vm.onSelectSheet(0)
        assertEquals("Destructive Application.Run must not wipe workbook", "KeepMe", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)
    }

    @Test
    fun phase3A_aiPlanModeConfirmationAndBalancedJsonExtractionAndBlockedDestructiveCommands() {
        // 1. Prose containing {braces} before actual JSON block must still parse cleanly
        val prosePlusJson = """
            Here is an example notation {row: 1} before the real payload:
            {"reply":"Updated A1","bullets":["Set A1 to 42"],"commands":[{"cmd":"cell.set","params":{"cell":"A1","input":"42"}},{"cmd":"home.deleteSheet","params":{}}]}
        """.trimIndent()
        val parsed = SpreadsheetAiAgent.parseJsonAiTurnResult(prosePlusJson, "allow_edit")
        assertNotNull(parsed)
        assertEquals("Updated A1", parsed!!.replyText)
        assertEquals("home.deleteSheet must be filtered out", 1, parsed.commands.size)
        assertEquals("cell.set", parsed.commands.single().first)

        // 2. In "plan" mode, pasting raw JSON commands must stage activeAiPlanSession WITHOUT mutating cells until confirmed
        val vm = createTestViewModel(blank = true)
        vm.onCommitCellEdit(0, 0, "Original")
        vm.onSelectAiEditMode("plan")
        vm.onSendAiPrompt("""{"reply":"Plan preview","commands":[{"cmd":"cell.set","params":{"cell":"A1","input":"PlannedValue"}}]}""")
        assertEquals("Plan mode must NOT mutate A1 before confirmation", "Original", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)
        assertNotNull("Plan mode must create an activeAiPlanSession confirmation prompt", vm.uiState.value.activeAiPlanSession)

        // Confirm execution (option 0 = execute)
        vm.onExecuteConfirmedAiPlan()
        assertNull(vm.uiState.value.activeAiPlanSession)
        assertEquals("Confirmed plan must apply A1 edit", "PlannedValue", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)

        // 3. In "chat" mode, pasting JSON commands must execute 0 commands
        vm.onSelectAiEditMode("chat")
        vm.onSendAiPrompt("""{"reply":"Chat explanation","commands":[{"cmd":"cell.set","params":{"cell":"A1","input":"ShouldNotApply"}}]}""")
        assertEquals("Chat mode must never mutate cells", "PlannedValue", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)
    }

    @Test
    fun phase3A_externalFileSchemeIntentRejectedAndDocumentGatewaySanitizesTraversal() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val secretCsv = File(ctx.cacheDir, "secret_local.csv").apply {
            writeText("TopSecretToken,9999\n")
        }
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                component = ComponentName(ctx, MainActivity::class.java)
                data = android.net.Uri.fromFile(secretCsv)
            }
            val controller = Robolectric.buildActivity(MainActivity::class.java, viewIntent).create().start().resume()
            val activity = controller.get()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val vm = activity.activeViewModelForTest
            assertNotNull(vm)
            val allTexts = vm.uiState.value.viewport.cells.values.map { it.displayText }
            assertFalse("External file:// ACTION_VIEW URI must be ignored", allTexts.any { it.contains("TopSecretToken") })
            controller.pause().stop().destroy()
        } finally {
            secretCsv.delete()
        }

        // Verify ContentResolverDocumentGateway.saveFileInFolder strips "../" traversal components
        val gw = ContentResolverDocumentGateway(ctx)
        val targetFolder = File(ctx.cacheDir, "safe_export_folder").apply { mkdirs() }
        val escapedSibling = File(ctx.cacheDir, "escaped_outside.xlsx").apply { delete() }
        try {
            val savedPath = gw.saveFileInFolder(
                folderPath = targetFolder.absolutePath,
                fileName = "../../escaped_outside.xlsx",
                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes = byteArrayOf(1, 2, 3, 4)
            )
            assertFalse("Escaped file outside target folder must NEVER be created", escapedSibling.exists())
            if (savedPath != null && !savedPath.startsWith("content://")) {
                val resolvedFile = if (savedPath.startsWith("file://")) {
                    File(java.net.URI.create(savedPath))
                } else {
                    File(savedPath)
                }
                assertEquals(targetFolder.canonicalFile, resolvedFile.canonicalFile.parentFile)
            }
        } finally {
            escapedSibling.delete()
            targetFolder.deleteRecursively()
        }
    }

    @Test
    fun phase3A_clipboardImageProviderRequiresExplicitCapabilityTokenAndBlocksLatestFallback() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val provider = MasuSheetClipboardImageProvider()
        val info = android.content.pm.ProviderInfo().apply {
            authority = "${ctx.packageName}.clipboard"
            exported = false
            grantUriPermissions = true
        }
        provider.attachInfo(ctx, info)

        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val validUri = MasuSheetClipboardImageProvider.writeClipboardImageAndBuildUri(ctx, pngBytes, "chart.png")

        // Valid token URI succeeds in read-only mode
        val pfd = provider.openFile(validUri, "r")
        assertNotNull(pfd)
        pfd?.close()

        // Write mode must throw SecurityException
        var writeBlocked = false
        try {
            provider.openFile(validUri, "w")?.close()
        } catch (_: SecurityException) {
            writeBlocked = true
        }
        assertTrue("Write mode on MasuSheetClipboardImageProvider must throw SecurityException", writeBlocked)

        // Tokenless URI must NOT fall back to returning the latest copied image
        val tokenlessUri = android.net.Uri.parse("content://${ctx.packageName}.clipboard/chart.png")
        var tokenlessRejected = false
        try {
            provider.openFile(tokenlessUri, "r")?.close()
        } catch (_: java.io.FileNotFoundException) {
            tokenlessRejected = true
        }
        assertTrue("Tokenless clipboard URI must be rejected without leaking latest image", tokenlessRejected)
    }

    @Test
    fun phase1And3A_immutableByteArrayStructuralEqualityAndBundledLicenseAssetsExist() {
        val a = NativeSheetObject(id = "img1", kind = SheetObjectKind.IMAGE, imageBytes = byteArrayOf(1, 2, 3))
        val b = NativeSheetObject(id = "img1", kind = SheetObjectKind.IMAGE, imageBytes = byteArrayOf(1, 2, 3))
        val c = NativeSheetObject(id = "img1", kind = SheetObjectKind.IMAGE, imageBytes = byteArrayOf(1, 2, 9))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)

        val s1 = OpenWorkbookSession(id = "wb1", title = "Book1.xlsx", sampleKind = "blank", savedWorkbookBytes = byteArrayOf(10, 20))
        val s2 = OpenWorkbookSession(id = "wb1", title = "Book1.xlsx", sampleKind = "blank", savedWorkbookBytes = byteArrayOf(10, 20))
        val s3 = OpenWorkbookSession(id = "wb1", title = "Book1.xlsx", sampleKind = "blank", savedWorkbookBytes = byteArrayOf(10, 99))
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
        assertNotEquals(s1, s3)

        // Verify all 4 offline OSS license texts are bundled in app/src/main/assets/licenses/
        val assetsDir = File("src/main/assets/licenses").let {
            if (it.isDirectory) it else File("app/src/main/assets/licenses")
        }
        for (fileName in listOf("LICENSE-APACHE.txt", "LICENSE-MIT.txt", "NOTICE.txt", "THIRD_PARTY_LICENSES.txt")) {
            val f = File(assetsDir, fileName)
            assertTrue("Bundled license asset $fileName must exist", f.isFile && f.length() > 100L)
        }
    }

    @Test
    fun reauditItem1_securityMacroAiClipboardAndLicenseRegression() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        // 1. Private internal app-data directories (including handoff & clipboard_images) block file:// access
        val handoffSecret = File(File(ctx.cacheDir, "handoff").apply { mkdirs() }, "test_token.json")
        val clipSecret = File(File(ctx.cacheDir, "clipboard_images").apply { mkdirs() }, "test.png")
        assertTrue(ContentResolverDocumentGateway.isInternalPrivateDataFile(ctx, handoffSecret))
        assertTrue(ContentResolverDocumentGateway.isInternalPrivateDataFile(ctx, clipSecret))
        val docGw = ContentResolverDocumentGateway(ctx)
        assertFalse(docGw.isSafeLocalFolderPath(ctx.cacheDir.absolutePath))

        // 2. Plain-text content:// strings on clipboard are never auto-dereferenced as images
        com.masusheet.app.ui.copyToSystemClipboard(ctx, "content://com.android.contacts/data/1")
        assertNull(com.masusheet.app.ui.readImageBytesFromSystemClipboard(ctx))

        // 3. VBA For..Next Step > 1 iterates full count (101 steps for 0..1000 Step 10) and deep nesting never overflows stack
        val step10Vba = """
            Sub TestStep10()
                For r = 0 To 1000 Step 10
                    Cells(1, 1).Value = r
                Next r
            End Sub
        """.trimIndent()
        val expandedStep10 = MacroStudioEngine.expandVbaStatements(step10Vba)
        assertEquals(202, expandedStep10.size)
        assertEquals("r = 0", expandedStep10.first())
        assertEquals("r = 1000", expandedStep10[expandedStep10.size - 2])

        val deepNestedVba = buildString {
            appendLine("Sub DeepNest()")
            for (d in 1..150) {
                appendLine("For v$d = 1 To 2")
            }
            appendLine("Cells(1, 1).Value = 1")
            for (d in 150 downTo 1) {
                appendLine("Next v$d")
            }
            appendLine("End Sub")
        }
        val deepExpanded = MacroStudioEngine.expandVbaStatements(deepNestedVba)
        assertTrue("Deeply nested VBA must terminate safely without StackOverflowError", deepExpanded.size <= 5_000)

        // 4. AI Agent: Populated sheet is NOT wiped by unmatched analytical prompts, and destructive / path params are blocked
        val vm = createTestViewModel(blank = true)
        vm.onCommitCellEdit(0, 0, "Product")
        vm.onCommitCellEdit(0, 1, "Sales")
        vm.onCommitCellEdit(1, 0, "Alpha")
        vm.onCommitCellEdit(1, 1, "1200")
        vm.onCommitCellEdit(2, 0, "Beta")
        vm.onCommitCellEdit(2, 1, "3400")
        vm.onSelectAiEditMode("allow_edit")
        vm.onSendAiPrompt("Summarize this table")
        assertEquals("Alpha", vm.uiState.value.viewport.cellAt(1, 0)?.displayText)
        assertEquals("3400", vm.uiState.value.viewport.cellAt(2, 1)?.displayText)

        val blockedJson = """
            {
              "reply": "Test",
              "commands": [
                {"cmd": "edit.clearContents", "params": {"range": "A1:B3"}},
                {"cmd": "home.deleteRows", "params": {}},
                {"cmd": "data.removeDuplicates", "params": {}},
                {"cmd": "pivot.delete", "params": {}},
                {"cmd": "data.fromTextCsv", "params": {"path": "/etc/hosts"}},
                {"cmd": "insert.picture", "params": {"nested": {"path": "/data/data/com.masusheet.app/shared_prefs/x.xml"}}},
                {"cmd": "cell.set", "params": {"cell": "C1", "input": "Allowed"}}
              ]
            }
        """.trimIndent()
        val parsedBlocked = SpreadsheetAiAgent.parseJsonAiTurnResult(blockedJson, "allow_edit")
        assertNotNull(parsedBlocked)
        assertEquals("Only cell.set without path param should pass", 1, parsedBlocked!!.commands.size)
        assertEquals("cell.set", parsedBlocked.commands.single().first)

        // 5. If native Rust JNI is loaded in JVM test environment, verify SEC-01 filesystem command & path blocking + file.markSaved
        if (com.masusheet.app.bridge.MasuSheetNativeBridge.isNativeLoaded) {
            val jniGw = RustJniEngineGateway()
            val sid = jniGw.createSession("blank")
            try {
                jniGw.executeCommand("cell.set", """{"cell":"A1","input":"DirtyCheck"}""")
                assertTrue(jniGw.getViewport().isDirty)
                val marked = jniGw.executeCommand("file.markSaved", """{"title":"Clean.xlsx"}""")
                assertFalse("file.markSaved must clear dirty state", marked.isDirty)

                val blockedSave = jniGw.executeCommand("file.save", """{"path":"/tmp/should_not_write.xlsx"}""")
                assertFalse("Direct file.save must be rejected in JNI", blockedSave.ok)
                val blockedCsvPath = jniGw.executeCommand("data.fromTextCsv", """{"path":"/etc/hosts"}""")
                assertFalse("Command containing filesystem path parameter must be rejected in JNI", blockedCsvPath.ok)
            } finally {
                jniGw.closeSession(sid)
            }
        }

        // 6. OSS License completeness: Unicode-DFS-2016 & Unicode-3.0 present, zero garbled Apache boilerplate Copyright lines, NOTICE synced
        val assetsDir = File("src/main/assets/licenses").let {
            if (it.isDirectory) it else File("app/src/main/assets/licenses")
        }
        val rootNotice = File("../NOTICE").let { if (it.isFile) it else File("NOTICE") }
        val assetNotice = File(assetsDir, "NOTICE.txt")
        assertEquals("Root NOTICE and bundled NOTICE.txt must match byte-for-byte", rootNotice.readText(), assetNotice.readText())
        assertFalse(assetNotice.readText().contains("rayon"))
        assertFalse(assetNotice.readText().contains("Chromebook"))
        assertFalse(assetNotice.readText().contains("ChromeOS"))
        assertTrue(assetNotice.readText().contains("Googlebook"))

        // Verify bare extension MIME resolution and import-only extension normalization for Save Dialog (.html supported, .ods normalized to .xlsx)
        assertEquals("text/html", MasuSheetConfig.resolveExportMimeType("html"))
        assertEquals("text/html", MasuSheetConfig.resolveExportMimeType(".html"))
        assertEquals("application/pdf", MasuSheetConfig.resolveExportMimeType("pdf"))
        assertEquals("xlsx", MasuSheetConfig.normalizeExportExtension("ods"))
        assertEquals("html", MasuSheetConfig.normalizeExportExtension("htm"))
        val saveVm = createTestViewModel(blank = true)
        saveVm.onCommitCellEdit(0, 0, "Hello <HTML>")
        saveVm.onOpenSaveDialog("ods")
        assertEquals("xlsx", saveVm.uiState.value.saveDialogExtension)
        saveVm.onSelectSaveDialogExtension("html")
        assertEquals("html", saveVm.uiState.value.saveDialogExtension)
        val htmlExport = saveVm.buildExportBytesForFormat("Book1.html", "html")
        assertNotNull("HTML export must produce non-null bytes", htmlExport)
        val htmlStr = String(htmlExport!!, Charsets.UTF_8)
        assertTrue("HTML export must contain <table> and escaped cell text", htmlStr.contains("<table") && htmlStr.contains("Hello &lt;HTML&gt;"))

        val thirdParty = File(assetsDir, "THIRD_PARTY_LICENSES.txt").readText()
        assertTrue("THIRD_PARTY_LICENSES.txt must include Unicode, Inc. notice", thirdParty.contains("Unicode, Inc."))
        assertTrue("THIRD_PARTY_LICENSES.txt must include Unicode-DFS-2016", thirdParty.contains("Unicode-DFS-2016"))
        assertFalse("Must have zero garbled Licensor copyright lines", thirdParty.contains("Copyright: \"Licensor\""))
        assertFalse("Must have zero Apache clause (c) copyright lines", thirdParty.contains("Copyright: Copyright (c) You must retain"))
        assertFalse("Must not list unused rayon crate", thirdParty.contains("Package: rayon "))
        assertTrue("NOTICE must include interoperability trademark disclaimer", assetNotice.readText().contains("SmartArt, WordArt, and Copilot"))

        // 7. COPILOT function name preserved for compatibility while description accurately states offline #N/A behavior
        val copilotEntry = com.masusheet.app.catalog.SpreadsheetFunctionCatalog.ALL_ENTRIES.firstOrNull { it.name == "COPILOT" }
        assertNotNull("COPILOT must remain in 523-function catalog for formula compatibility", copilotEntry)
        assertTrue(copilotEntry!!.descriptionJa.contains("#N/A"))
        assertTrue(copilotEntry.descriptionEn.contains("#N/A"))

        // 8. Macro full-snapshot rollback beyond 60-step overlay Undo ring buffer
        val macroVm = createTestViewModel(blank = true)
        macroVm.onResizeColumn(0, 145f)
        val widthBeforeMacro = macroVm.uiState.value.viewport.colWidths[0]
        assertEquals(145f, widthBeforeMacro, 0.01f)
        val longMacro = MacroDefinition(
            name = "Bulk80Steps",
            vbaCode = """
                Sub Bulk80Steps()
                    For r = 1 To 80
                        Cells(r, 1).Value = "Row" & r
                    Next r
                End Sub
            """.trimIndent()
        )
        macroVm.run {
            onRunMacro(longMacro)
            assertEquals("Row80", uiState.value.viewport.cellAt(79, 0)?.displayText)
            onRollbackLastMacroRun()
            assertEquals("", uiState.value.viewport.cellAt(0, 0)?.displayText.orEmpty())
            assertEquals("", uiState.value.viewport.cellAt(79, 0)?.displayText.orEmpty())
            assertEquals(145f, uiState.value.viewport.colWidths[0], 0.01f)
        }
    }

    @Test
    fun inventoryTemplatePivotFormattingTotalsColumnWidthAndMacroI18nRegression() {
        val repo = MasuSheetPreferencesRepository(FakePreferenceStore(), InMemorySecretStore())
        val engineGw = if (com.masusheet.app.bridge.MasuSheetNativeBridge.isNativeLoaded) {
            RustJniEngineGateway()
        } else {
            InMemoryFallbackEngineGateway()
        }
        val vm = MasuSheetViewModel(
            preferencesRepository = repo,
            documentGateway = FakeDocumentIoGateway(),
            engineGateway = engineGw,
            startWithBlankWorkbook = true
        )
        vm.onSelectAiEditMode("allow_edit")
        vm.onSendAiPrompt("在庫・発注管理シートを作成して")

        val vpAfterAi = vm.uiState.value.viewport
        // 1. Green table: monetary columns (B=単価, E=在庫金額) have ¥; quantity columns (C=現在庫数, D=発注点) have NO ¥
        val b8 = vpAfterAi.cellAt(7, 1)?.displayText.orEmpty()
        val c8 = vpAfterAi.cellAt(7, 2)?.displayText.orEmpty()
        val d8 = vpAfterAi.cellAt(7, 3)?.displayText.orEmpty()
        val e8 = vpAfterAi.cellAt(7, 4)?.displayText.orEmpty()
        val c14 = vpAfterAi.cellAt(13, 2)?.displayText.orEmpty()
        val d14 = vpAfterAi.cellAt(13, 3)?.displayText.orEmpty()
        val e14 = vpAfterAi.cellAt(13, 4)?.displayText.orEmpty()

        assertTrue("Unit price B8 should display ¥, got: $b8", b8.startsWith("¥"))
        assertTrue("Stock amount E8 should display ¥, got: $e8", e8.startsWith("¥"))
        assertEquals("¥1,302,700", e14)
        assertEquals("45", c8)
        assertEquals("20", d8)
        assertEquals("136", c14)
        assertEquals("87", d14)
        assertFalse("Quantity C8 must not have ¥", c8.contains("¥"))
        assertFalse("Reorder point D8 must not have ¥", d8.contains("¥"))

        // 2. Column widths widened so Row 4 KPI labels (C4=平均在庫金額, E4=最大在庫金額) do not clip
        assertTrue("Col A width >= 190pt", vpAfterAi.colWidths[0] >= 190f)
        assertTrue("Col C width >= 100pt", vpAfterAi.colWidths[2] >= 100f)
        assertTrue("Col E width >= 100pt", vpAfterAi.colWidths[4] >= 100f)

        // 3. Create 2-level hierarchy PivotTable from A8: excludes Row 14 ("合計 / 集計"), orders category->item, formats quantities without ¥
        vm.onSelectCell(7, 0)
        val pivot = vm.onCreatePivotTable(placeOnNewSheet = false, presetKind = "hierarchy")
        assertNotNull(pivot)
        assertEquals("Row 14 ('合計 / 集計') must be excluded from Pivot source", 12, pivot!!.sourceEndRow)
        assertEquals(6, pivot.sourceRecords.size)
        assertFalse(pivot.sourceRecords.any { it["商品名"]?.contains("合計") == true })
        assertEquals(listOf("発注判定", "商品名"), pivot.rowFields)
        assertEquals(listOf("現在庫数", "発注点", "在庫金額"), pivot.valueFields.map { it.fieldName })

        val vpAfterPivot = vm.uiState.value.viewport
        val grandTotalRow = pivot.lastRenderedEndRow
        val startCol = pivot.targetStartCol
        assertEquals("総計", vpAfterPivot.cellAt(grandTotalRow, startCol)?.displayText)
        assertEquals("136", vpAfterPivot.cellAt(grandTotalRow, startCol + 2)?.displayText)
        assertEquals("87", vpAfterPivot.cellAt(grandTotalRow, startCol + 3)?.displayText)
        assertEquals("¥1,302,700", vpAfterPivot.cellAt(grandTotalRow, startCol + 4)?.displayText)
        assertTrue("Pivot item column widened for long Japanese product names", vpAfterPivot.colWidths[startCol + 1] >= 180f)

        // 4. Built-in Macro descriptions translate cleanly to English when isEn = true
        for (macro in MacroStudioEngine.defaultBuiltInMacros()) {
            val enDesc = com.masusheet.app.core.SpreadsheetI18n.t(macro.description, isEn = true)
            assertNotEquals("Macro description should translate in EN mode: ${macro.description}", macro.description, enDesc)
            assertFalse("Translated macro description should contain no Hiragana", enDesc.any { it in '\u3040'..'\u309F' })
        }
    }
}
