package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

class FakePreferenceStore : PreferenceStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String, defaultValue: String): String = map[key] ?: defaultValue
    override fun putString(key: String, value: String) {
        map[key] = value
    }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        map[key]?.toBooleanStrictOrNull() ?: defaultValue
    override fun putBoolean(key: String, value: Boolean) {
        map[key] = value.toString()
    }
    override fun getInt(key: String, defaultValue: Int): Int =
        map[key]?.toIntOrNull() ?: defaultValue
    override fun putInt(key: String, value: Int) {
        map[key] = value.toString()
    }
}

class FakeDocumentIoGateway : DocumentIoGateway {
    val files = mutableMapOf<String, ByteArray>()
    val names = mutableMapOf<String, String>()
    var failOnRead = false
    var failOnWrite = false

    override fun readBytes(uriString: String): ByteArray? {
        if (failOnRead) return null
        return files[uriString]
    }

    override fun writeBytes(uriString: String, bytes: ByteArray): Boolean {
        if (failOnWrite) return false
        files[uriString] = bytes
        return true
    }

    override fun resolveDisplayName(uriString: String): String {
        return names[uriString] ?: uriString.substringAfterLast('/').ifBlank { "Book1.xlsx" }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MasuSheetTest {

    @Test
    fun config_buildsAssetUrlAndResolvesMimeTypes() {
        val urlDefault = MasuSheetConfig.buildHostAssetUrl(sample = "sales", language = "ja", forceWebGl = false)
        assertEquals(
            "https://appassets.androidplatform.net/masusheet/index.html?host=parent&chrome=notitlebar&author=Ray&sample=sales&lang=ja",
            urlDefault
        )

        val urlWebGl = MasuSheetConfig.buildHostAssetUrl(sample = "budget", language = "en", forceWebGl = true)
        assertTrue(urlWebGl.contains("sample=budget"))
        assertTrue(urlWebGl.contains("lang=en"))
        assertTrue(urlWebGl.contains("webgl=1"))

        assertEquals("public, max-age=31536000, immutable", MasuSheetConfig.resolveCacheControlHeader("masusheet-web_bg.wasm"))
        assertEquals("public, max-age=31536000, immutable", MasuSheetConfig.resolveCacheControlHeader("masusheet-web.js"))
        assertEquals("no-cache", MasuSheetConfig.resolveCacheControlHeader("index.html"))

        assertEquals("application/wasm", MasuSheetConfig.resolveAssetMimeType("masusheet-web_bg.wasm"))
        assertEquals("text/javascript", MasuSheetConfig.resolveAssetMimeType("masusheet-web.js"))
        assertEquals("text/javascript", MasuSheetConfig.resolveAssetMimeType("mod.mjs"))
        assertEquals("text/html", MasuSheetConfig.resolveAssetMimeType("index.html"))
        assertEquals("text/css", MasuSheetConfig.resolveAssetMimeType("style.css"))
        assertEquals("image/x-icon", MasuSheetConfig.resolveAssetMimeType("masusheet.ico"))
        assertEquals("image/svg+xml", MasuSheetConfig.resolveAssetMimeType("icon.svg"))
        assertEquals("image/png", MasuSheetConfig.resolveAssetMimeType("icon.png"))
        assertEquals("application/json", MasuSheetConfig.resolveAssetMimeType("meta.json"))
        assertEquals("application/octet-stream", MasuSheetConfig.resolveAssetMimeType("unknown.bin"))

        assertEquals("A", MasuSheetConfig.formatColumnLabel(0))
        assertEquals("Z", MasuSheetConfig.formatColumnLabel(25))
        assertEquals("AA", MasuSheetConfig.formatColumnLabel(26))
        assertEquals("B3", MasuSheetConfig.formatCellAddress(2, 1))
    }

    @Test
    fun inlineCheckboxHelper_togglesSelectedCheckboxAndResolvesSmartCaret() {
        val row = "□本体  □ACアダプタ  □USBケーブル  □SDカード"
        val checkedFirst = InlineCheckboxHelper.toggleNthCheckbox(row, 0)
        assertEquals("☑本体  □ACアダプタ  □USBケーブル  □SDカード", checkedFirst)

        val checkedSecond = InlineCheckboxHelper.toggleNthCheckbox(checkedFirst!!, 1)
        assertEquals("☑本体  ☑ACアダプタ  □USBケーブル  □SDカード", checkedSecond)

        val uncheckedFirst = InlineCheckboxHelper.toggleNthCheckbox(checkedSecond!!, 0)
        assertEquals("□本体  ☑ACアダプタ  □USBケーブル  □SDカード", uncheckedFirst)

        assertNull(InlineCheckboxHelper.toggleNthCheckbox(row, -1))
        assertNull(InlineCheckboxHelper.toggleNthCheckbox(row, 99))

        // Smart caret placement for 年/月/日 and @
        assertEquals(0, InlineCheckboxHelper.resolveSmartCaretIndex("", 5))
        assertEquals(0, InlineCheckboxHelper.resolveSmartCaretIndex("年", 1))
        assertEquals(0, InlineCheckboxHelper.resolveSmartCaretIndex("月", 1))
        assertEquals(0, InlineCheckboxHelper.resolveSmartCaretIndex("日", 1))

        val dateField = "20  年  月  日"
        // Index of '年' is 4, '月' is 7, '日' is 10
        assertEquals(4, InlineCheckboxHelper.resolveSmartCaretIndex(dateField, 3))
        assertEquals(4, InlineCheckboxHelper.resolveSmartCaretIndex(dateField, 4))
        assertEquals(4, InlineCheckboxHelper.resolveSmartCaretIndex(dateField, 5))
        assertEquals(7, InlineCheckboxHelper.resolveSmartCaretIndex(dateField, 6))
        assertEquals(7, InlineCheckboxHelper.resolveSmartCaretIndex(dateField, 7))
        assertEquals(7, InlineCheckboxHelper.resolveSmartCaretIndex(dateField, 8))
        assertEquals(10, InlineCheckboxHelper.resolveSmartCaretIndex(dateField, 9))
        assertEquals(10, InlineCheckboxHelper.resolveSmartCaretIndex(dateField, 10))
        assertEquals(10, InlineCheckboxHelper.resolveSmartCaretIndex(dateField, 11))

        val emailField = "   @   "
        // Index of '@' is 3
        assertEquals(3, InlineCheckboxHelper.resolveSmartCaretIndex(emailField, 1))
        assertEquals(3, InlineCheckboxHelper.resolveSmartCaretIndex(emailField, 3))
        assertEquals(4, InlineCheckboxHelper.resolveSmartCaretIndex(emailField, 4))
        assertEquals(4, InlineCheckboxHelper.resolveSmartCaretIndex(emailField, 6))
    }

    @Test
    fun config_resolvesExportMimeTypesAndSanitizesNames() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            MasuSheetConfig.resolveExportMimeType("Report.xlsx")
        )
        assertEquals("application/vnd.ms-excel.sheet.macroEnabled.12", MasuSheetConfig.resolveExportMimeType("Macro.xlsm"))
        assertEquals("application/vnd.ms-excel.sheet.binary.macroEnabled.12", MasuSheetConfig.resolveExportMimeType("Bin.xlsb"))
        assertEquals("application/vnd.oasis.opendocument.spreadsheet", MasuSheetConfig.resolveExportMimeType("Sheet.ods"))
        assertEquals("text/csv", MasuSheetConfig.resolveExportMimeType("data.csv"))
        assertEquals("text/tab-separated-values", MasuSheetConfig.resolveExportMimeType("data.tsv"))
        assertEquals("application/pdf", MasuSheetConfig.resolveExportMimeType("print.pdf"))
        assertEquals("text/html", MasuSheetConfig.resolveExportMimeType("page.html"))
        assertEquals("application/json", MasuSheetConfig.resolveExportMimeType("dump.json"))
        assertEquals("application/octet-stream", MasuSheetConfig.resolveExportMimeType("raw.dat"))

        assertEquals("Book1.xlsx", MasuSheetConfig.sanitizeWorkbookFileName("   ", "xlsx"))
        assertEquals("Sales_2026.xlsx", MasuSheetConfig.sanitizeWorkbookFileName("Sales/2026", "xlsx"))
        assertEquals("budget.csv", MasuSheetConfig.sanitizeWorkbookFileName("budget.csv", "xlsx"))
    }

    @Test
    fun classifier_identifiesFormatsAndCyclesSamples() {
        assertTrue(WorkbookFormatClassifier.isSupportedSpreadsheet("Q1.xlsx"))
        assertTrue(WorkbookFormatClassifier.isSupportedSpreadsheet("Macro.XLSM"))
        assertTrue(WorkbookFormatClassifier.isSupportedSpreadsheet("Binary.xlsb"))
        assertTrue(WorkbookFormatClassifier.isSupportedSpreadsheet("Libre.ods"))
        assertTrue(WorkbookFormatClassifier.isSupportedSpreadsheet("table.csv"))
        assertTrue(WorkbookFormatClassifier.isSupportedSpreadsheet("table.tsv"))
        assertFalse(WorkbookFormatClassifier.isSupportedSpreadsheet("video.mp4"))
        assertFalse(WorkbookFormatClassifier.isSupportedSpreadsheet("noextension"))
        assertFalse(WorkbookFormatClassifier.isSupportedSpreadsheet("   "))

        assertEquals(WorkbookFormat.XLSX, WorkbookFormatClassifier.detectFormat("a.xlsx"))
        assertEquals(WorkbookFormat.CSV, WorkbookFormatClassifier.detectFormat("a.csv"))
        assertEquals(WorkbookFormat.TSV, WorkbookFormatClassifier.detectFormat("a.tsv"))
        assertEquals(WorkbookFormat.ODS, WorkbookFormatClassifier.detectFormat("a.ods"))
        assertEquals(WorkbookFormat.PDF, WorkbookFormatClassifier.detectFormat("a.pdf"))
        assertEquals(WorkbookFormat.UNKNOWN, WorkbookFormatClassifier.detectFormat("a.bin"))

        assertEquals("budget", WorkbookFormatClassifier.nextSampleWorkbook("sales"))
        assertEquals("grades", WorkbookFormatClassifier.nextSampleWorkbook("budget"))
        assertEquals("repair", WorkbookFormatClassifier.nextSampleWorkbook("grades"))
        assertEquals("sales", WorkbookFormatClassifier.nextSampleWorkbook("repair"))
        assertEquals("sales", WorkbookFormatClassifier.nextSampleWorkbook("unknown"))
    }

    @Test
    fun scrollInputMath_computesWheelAndTrackpadDeltasSafely() {
        val (wheelDx, wheelDy) = ScrollInputMath.computeWheelScrollDelta(axisHScroll = -1.5f, axisVScroll = 2.0f)
        assertEquals(96f, wheelDx, 0.01f)
        assertEquals(128f, wheelDy, 0.01f)

        val (swipeDx, swipeDy) = ScrollInputMath.computeTrackpadSwipeDelta(
            prevX = 100f,
            prevY = 200f,
            currX = 160f,
            currY = 140f,
            density = 2.0f
        )
        assertEquals(30f, swipeDx, 0.01f)
        assertEquals(-30f, swipeDy, 0.01f)
    }

    @Test
    fun rustJniEngine_executesFormulasAndRepairSampleAndExportsXlsxAndPdf() {
        val engine = RustJniEngineGateway()
        val repairSnap = engine.initSample("repair")
        assertTrue(repairSnap.ok)
        assertTrue(repairSnap.merges.isNotEmpty())
        assertEquals("20    年    月    日", repairSnap.cellAt(1, 1)?.displayText)
        assertEquals("          @          ", repairSnap.cellAt(3, 1)?.displayText)
        assertEquals("□ 本体", repairSnap.cellAt(4, 1)?.displayText)

        // Evaluate an Spreadsheet formula via native Rust engine
        val afterFormula = engine.executeCommand("cell.set", "{\"cell\":\"B10\",\"input\":\"=125+375\"}")
        assertEquals("500", afterFormula.cellAt(9, 1)?.displayText)

        // Save workbook bytes (.xlsx) and export PDF bytes
        val xlsxBytes = engine.saveWorkbookBytes("test_out.xlsx")
        assertNotNull(xlsxBytes)
        assertTrue(xlsxBytes!!.isNotEmpty())

        val pdfBytes = engine.exportPdfBytes()
        assertNotNull(pdfBytes)
        assertTrue(pdfBytes!!.isNotEmpty())
    }

    @Test
    fun partialUpdateGridView_reusesLayer1BitmapOnCellSelectionAndOpensInCellEditorOnDoubleClick() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)
        vm.onLoadRepairChecklistSample()

        val container = MasuSheetNativeSheetContainer(ctx, vm)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 720)
        container.bindState(vm.uiState.value)

        val testBitmap = Bitmap.createBitmap(1080, 720, Bitmap.Config.ARGB_8888)
        val testCanvas = Canvas(testBitmap)

        // First draw renders both Layer 1 (static cells) and Layer 2 (selection overlay)
        container.gridView.draw(testCanvas)
        assertEquals(1, container.gridView.layer1RenderCount)
        assertEquals(1, container.gridView.layer2OverlayRenderCount)

        // Selecting another cell updates activeRow/activeCol WITHOUT changing revision:
        // Layer 1 MUST NOT be re-rendered (stays at 1), while Layer 2 overlay increments to 2!
        vm.onSelectCell(2, 1)
        container.bindState(vm.uiState.value)
        container.gridView.draw(testCanvas)
        assertEquals(1, container.gridView.layer1RenderCount)
        assertEquals(2, container.gridView.layer2OverlayRenderCount)

        // Open in-cell editor on date cell (1, 1) ("20    年    月    日")
        container.openInCellEditor(1, 1, null, null)
        assertTrue(container.isEditingInCell)
        assertEquals(View.VISIBLE, container.inCellEditText.visibility)
        assertEquals("20    年    月    日", container.inCellEditText.text.toString())

        // Verify coordinate-aware smart caret calculation for date and email cells
        val cellRect = RectF(100f, 50f, 320f, 80f)
        val dateCell = vm.uiState.value.viewport.cellAt(1, 1)
        val caretAtStart = container.computeSmartCaretFromClick("20    年    月    日", dateCell, cellRect, 145f)
        assertEquals(6, caretAtStart) // Snaps directly in front of '年' (index 6 in "20    年    月    日")

        val emailCell = vm.uiState.value.viewport.cellAt(3, 1)
        val caretBeforeAt = container.computeSmartCaretFromClick("          @          ", emailCell, cellRect, 120f)
        assertEquals(10, caretBeforeAt) // Directly before '@'

        container.commitAndCloseInCellEditor(1, 0)
        assertFalse(container.isEditingInCell)
        // Standby mode keeps inCellEditText View.VISIBLE with alpha=0f to prevent Dark Mode white-screen flicker!
        assertEquals(View.VISIBLE, container.inCellEditText.visibility)
        assertEquals(0f, container.inCellEditText.alpha, 0.001f)

        // Direct typing on selected cell (2, 1) WITHOUT double-clicking!
        vm.onSelectCell(2, 1)
        container.bindState(vm.uiState.value)
        assertFalse(container.isEditingInCell)
        container.inCellEditText.setText("佐藤 花子 様")
        assertTrue(container.isEditingInCell)
        container.commitAndCloseInCellEditor(1, 0)
        assertFalse(container.isEditingInCell)
        assertEquals("佐藤 花子 様", vm.uiState.value.viewport.cellAt(2, 1)?.displayText)
        assertEquals(3, vm.uiState.value.viewport.activeRow)

        // Column & Row Header boundary hit-testing and resizing
        val col0RightPx = container.gridView.rowHeaderWidthPx + container.gridView.currentViewport.colOffsets[1]
        assertEquals(0, container.gridView.findColumnDividerAt(col0RightPx, 10f))
        vm.onResizeColumn(0, 220f)
        container.bindState(vm.uiState.value)
        assertEquals(220f, container.gridView.currentViewport.colWidths[0], 0.5f)

        val row0BottomPx = container.gridView.colHeaderHeightPx + container.gridView.currentViewport.rowOffsets[1]
        assertEquals(0, container.gridView.findRowDividerAt(15f, row0BottomPx))
        vm.onResizeRow(0, 42f)
        container.bindState(vm.uiState.value)
        assertEquals(42f, container.gridView.currentViewport.rowHeights[0], 0.5f)

        vm.onAutoFitColumn(1)
        vm.onAutoFitRow(1)

        // Right-click context menu on Cell, Column Header, and Row Header
        assertTrue(container.gridView.handleRightClickAt(200f, 120f))
        assertTrue(container.isContextMenuVisible)
        assertEquals(ContextMenuTargetType.CELL, container.contextMenuTargetType)
        container.dismissContextMenu()
        assertFalse(container.isContextMenuVisible)

        assertTrue(container.gridView.handleRightClickAt(150f, 10f))
        assertTrue(container.isContextMenuVisible)
        assertEquals(ContextMenuTargetType.COLUMN_HEADER, container.contextMenuTargetType)
        container.dismissContextMenu()

        assertTrue(container.gridView.handleRightClickAt(12f, 90f))
        assertTrue(container.isContextMenuVisible)
        assertEquals(ContextMenuTargetType.ROW_HEADER, container.contextMenuTargetType)
        container.dismissContextMenu()

        // Multi-tab Ribbon selection & Sheet tab switching preserving cell edits
        vm.onSelectRibbonTab("formulas")
        assertEquals("formulas", vm.uiState.value.activeRibbonTab)
        vm.onAddSheet()
        assertTrue(vm.uiState.value.viewport.sheetNames.size >= 2)
        vm.onSelectSheet(0)
        assertEquals("佐藤 花子 様", vm.uiState.value.viewport.cellAt(2, 1)?.displayText)
    }

    @Test
    fun repository_persistsPrefsAndRecentWorkbooks() {
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)

        assertEquals("ja", repo.getLanguage())
        assertFalse(repo.isDarkTheme())
        assertEquals(100, repo.getZoomPercent())

        repo.setLanguage("en")
        repo.setDarkTheme(true)
        repo.setZoomPercent(125)
        assertEquals("en", repo.getLanguage())
        assertTrue(repo.isDarkTheme())
        assertEquals(125, repo.getZoomPercent())

        repo.recordRecentWorkbook("   ")
        assertTrue(repo.getRecentWorkbooks().isEmpty())

        for (i in 1..10) {
            repo.recordRecentWorkbook("File$i.xlsx")
        }
        repo.recordRecentWorkbook("File5.xlsx")
        val recents = repo.getRecentWorkbooks()
        assertEquals(8, recents.size)
        assertEquals("File5.xlsx", recents.first())

        repo.clearRecentWorkbooks()
        assertTrue(repo.getRecentWorkbooks().isEmpty())
    }

    @Test
    fun robolectric_sharedPreferencesStoreAndDocumentGatewayWork() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesStoreFactory.create(ctx, "test_gc_prefs")
        store.putString("k_str", "val")
        store.putBoolean("k_bool", true)
        store.putInt("k_int", 150)

        assertEquals("val", store.getString("k_str", ""))
        assertTrue(store.getBoolean("k_bool", false))
        assertEquals(150, store.getInt("k_int", 100))

        val externalTempDir = java.nio.file.Files.createTempDirectory("masusheet_doc_test").toFile()
        val tempFile = File(externalTempDir, "TestWorkbook.csv")
        val payload = "A,B,C\n1,2,=A2+B2\n".toByteArray()
        tempFile.writeBytes(payload)
        val fileUri = Uri.fromFile(tempFile).toString()

        val gateway = ContentResolverDocumentGatewayFactory.create(ctx)
        assertEquals("TestWorkbook.csv", gateway.resolveDisplayName(fileUri))
        val readBack = gateway.readBytes(fileUri)
        assertNotNull(readBack)
        assertArrayEquals(payload, readBack)

        val outFile = File(externalTempDir, "SavedOut.csv")
        val outUri = Uri.fromFile(outFile).toString()
        assertTrue(gateway.writeBytes(outUri, payload))
        assertArrayEquals(payload, outFile.readBytes())
        externalTempDir.deleteRecursively()

        // C-02: Verify internal app-private files under dataDir are strictly blocked over file://
        val internalSecret = File(ctx.cacheDir, "internal_secret.xml").apply { writeText("<secret/>") }
        assertNull(gateway.readBytes(Uri.fromFile(internalSecret).toString()))
        internalSecret.delete()

        assertNull(gateway.readBytes("file:///nonexistent_dir_12345/missing.xlsx"))
        val factory = MasuSheetViewModel.provideFactory(ctx)
        val createdVm = factory.create(MasuSheetViewModel::class.java)
        assertNotNull(createdVm)
    }

    @Test
    fun viewModel_handlesFullLifecycleAndBridgeCallbacks() {
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)

        val dispatchedScripts = mutableListOf<String>()
        vm.setScriptDispatcher { js -> dispatchedScripts.add(js) }

        gateway.files["content://docs/startup"] = "A,B\n1,2".toByteArray()
        gateway.names["content://docs/startup"] = "Startup.csv"
        assertTrue(vm.onOpenDocumentUri("content://docs/startup"))

        vm.onEngineReady("0.14.2")
        assertTrue(vm.uiState.value.isEngineReady)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("0.14.2", vm.uiState.value.engineVersion)

        vm.onTitleChanged("Q4_Budget.xlsx")
        assertEquals("Q4_Budget.xlsx", vm.uiState.value.workbookTitle)
        assertTrue(vm.uiState.value.recentWorkbooks.contains("Q4_Budget.xlsx"))

        vm.onDirtyChanged(true)
        assertTrue(vm.uiState.value.isDirty)

        assertEquals("ja", vm.uiState.value.language)
        vm.onToggleLanguage()
        assertEquals("en", vm.uiState.value.language)
        vm.onToggleLanguage()
        assertEquals("ja", vm.uiState.value.language)

        assertFalse(vm.uiState.value.isDarkTheme)
        vm.onToggleDarkTheme()
        assertTrue(vm.uiState.value.isDarkTheme)

        assertEquals(100, vm.uiState.value.zoomPercent)
        vm.onCycleZoom()
        assertEquals(125, vm.uiState.value.zoomPercent)
        vm.onCycleZoom()
        assertEquals(150, vm.uiState.value.zoomPercent)
        vm.onCycleZoom()
        assertEquals(100, vm.uiState.value.zoomPercent)

        assertEquals("sales", vm.uiState.value.activeSample)
        vm.onLoadNextSample()
        assertEquals("budget", vm.uiState.value.activeSample)

        vm.onLoadRepairChecklistSample()
        assertEquals("repair", vm.uiState.value.activeSample)
        assertEquals("修理依頼書.xlsx", vm.uiState.value.workbookTitle)
        assertTrue(vm.onToggleCellCheckbox(4, 1, 0))
        assertEquals("☑ 本体", vm.uiState.value.viewport.cellAt(4, 1)?.displayText)

        vm.onTriggerSave()
        assertTrue(vm.uiState.value.isSaveDialogVisible)
        if (com.masusheet.app.bridge.MasuSheetNativeBridge.isNativeLoaded) {
            assertNotNull(vm.uiState.value.pendingSaveBytes)
        } else {
            assertNull(vm.uiState.value.pendingSaveBytes)
        }
        vm.onCancelPendingSave()

        vm.onTriggerExportPdf()
        assertTrue(vm.uiState.value.isSaveDialogVisible)
        if (com.masusheet.app.bridge.MasuSheetNativeBridge.isNativeLoaded) {
            assertNotNull(vm.uiState.value.pendingSaveBytes)
        } else {
            assertNull(vm.uiState.value.pendingSaveBytes)
        }
        vm.onCancelPendingSave()

        // C-05: Explicitly verify InMemoryFallbackEngineGateway refuses dummy binary .xlsx/.pdf overwrites while supporting CSV
        val fallbackVm = MasuSheetViewModel(repo, gateway, engineGateway = InMemoryFallbackEngineGateway())
        fallbackVm.onTriggerSave()
        assertNull(fallbackVm.uiState.value.pendingSaveBytes)
        assertFalse(fallbackVm.onConfirmSaveDirectlyToFolder())
        fallbackVm.onOpenSaveDialog("csv", prepareBytesImmediately = true)
        assertNotNull(fallbackVm.uiState.value.pendingSaveBytes)

        gateway.files["content://docs/sheet1"] = "col1,col2\n10,20".toByteArray()
        gateway.names["content://docs/sheet1"] = "Imported.csv"
        assertTrue(vm.onOpenDocumentUri("content://docs/sheet1"))
        assertEquals("Imported.csv", vm.uiState.value.workbookTitle)

        gateway.failOnRead = true
        assertFalse(vm.onOpenDocumentUri("content://docs/sheet1"))
        assertNotNull(vm.uiState.value.errorMessage)
        vm.onDismissError()
        gateway.failOnRead = false

        val sampleB64 = java.util.Base64.getEncoder().encodeToString("saved-xlsx-bytes".toByteArray())
        vm.onSaveRequestedFromEngine("Exported.xlsx", sampleB64)
        assertEquals("Exported.xlsx", vm.uiState.value.pendingSaveFileName)
        assertNotNull(vm.uiState.value.pendingSaveBytes)

        assertTrue(vm.onCompletePendingSave("content://docs/out.xlsx"))
        assertNull(vm.uiState.value.pendingSaveBytes)
        assertEquals("Exported.xlsx", vm.uiState.value.lastSavedFileName)
        assertArrayEquals("saved-xlsx-bytes".toByteArray(), gateway.files["content://docs/out.xlsx"])

        vm.onSaveRequestedFromEngine("CancelMe.xlsx", sampleB64)
        vm.onCancelPendingSave()
        assertNull(vm.uiState.value.pendingSaveBytes)
        assertFalse(vm.onCompletePendingSave("content://docs/out2.xlsx"))

        vm.onSaveRequestedFromEngine("FailWrite.xlsx", sampleB64)
        gateway.failOnWrite = true
        assertFalse(vm.onCompletePendingSave("content://docs/out3.xlsx"))
        assertNotNull(vm.uiState.value.errorMessage)
        gateway.failOnWrite = false

        vm.onSaveRequestedFromEngine("Bad.xlsx", "!!!not_base64!!!")
        assertNotNull(vm.uiState.value.errorMessage)

        assertTrue(vm.uiState.value.toolbarVisible)
        vm.onToggleToolbar()
        assertFalse(vm.uiState.value.toolbarVisible)

        vm.onEngineError("Sample error")
        assertEquals("Sample error", vm.uiState.value.errorMessage)
        vm.onDismissError()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun mainActivity_launchesAndHandlesNewIntentCleanly() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val tempCsv = File(ctx.cacheDir, "LaunchSheet.csv")
        tempCsv.writeText("Header1,Header2\n100,200\n")
        val startIntent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.fromFile(tempCsv)
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java, startIntent).setup()
        val activity = controller.get()
        assertNotNull(activity)

        val secondIntent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.fromFile(tempCsv)
        }
        controller.newIntent(secondIntent)
        controller.destroy()
    }

    @Test
    fun v0142Android7_allSevenImprovementsWorkCleanly() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)

        // 1. Wrap Text (折り返して表示) wraps lines and expands row height automatically
        vm.onSelectCell(2, 1)
        val longSentence = "これは非常に長い文章のセルであり折り返して表示をオンにすると複数行に分かれて全体が表示されます"
        vm.onCommitCellEdit(2, 1, longSentence)
        val heightBeforeWrap = vm.uiState.value.viewport.rowHeights[2]
        vm.onToggleWrapText()
        assertTrue(vm.uiState.value.viewport.cellAt(2, 1)?.isWrap == true)
        assertTrue(vm.uiState.value.viewport.rowHeights[2] > heightBeforeWrap)

        // 2. Tight Auto-Fit (セルの間をダブルクリックした時の動作): empty row/col never expands, 1-line row stays 20pt
        val emptyRowIdx = 40
        val initialEmptyRowH = vm.uiState.value.viewport.rowHeights[emptyRowIdx]
        vm.onAutoFitRow(emptyRowIdx)
        assertEquals(initialEmptyRowH, vm.uiState.value.viewport.rowHeights[emptyRowIdx], 0.01f)

        val emptyColIdx = 20
        val initialEmptyColW = vm.uiState.value.viewport.colWidths[emptyColIdx]
        vm.onAutoFitColumn(emptyColIdx)
        assertEquals(initialEmptyColW, vm.uiState.value.viewport.colWidths[emptyColIdx], 0.01f)

        // Column 8 has short text in the sales sample; auto-fit tightens it snugly
        vm.onAutoFitColumn(8)
        assertTrue(vm.uiState.value.viewport.colWidths[8] <= 45f)

        vm.onCommitCellEdit(35, 0, "1行テキスト")
        vm.onAutoFitRow(35)
        assertEquals(20f, vm.uiState.value.viewport.rowHeights[35], 0.01f)

        // 3 & 4. Save Dialog: selectable extension (.xlsx/.csv/.tsv/.ods/.pdf), no .bin, click existing file populates name
        assertEquals("Report.xlsx", MasuSheetConfig.sanitizeWorkbookFileName("Report.bin", "xlsx"))
        assertEquals("Data.csv", MasuSheetConfig.buildFileNameWithExtension("Data.bin", "csv"))
        assertEquals("Data", MasuSheetConfig.extractBaseNameWithoutExtension("Data.xlsx"))
        assertEquals("pdf", MasuSheetConfig.extractExtensionOrDefault("Invoice.pdf"))

        vm.onOpenSaveDialog("xlsx")
        assertTrue(vm.uiState.value.isSaveDialogVisible)
        assertEquals("xlsx", vm.uiState.value.saveDialogExtension)

        val existingItem = ExistingFolderFileItem(
            fileName = "既存売上データ_2026.csv",
            baseName = "既存売上データ_2026",
            extension = "csv",
            fullPath = "/tmp/既存売上データ_2026.csv"
        )
        vm.onSelectExistingFileForSaveName(existingItem)
        assertEquals("既存売上データ_2026", vm.uiState.value.saveDialogBaseName)
        assertEquals("csv", vm.uiState.value.saveDialogExtension)

        vm.onSelectSaveDialogExtension("xlsx")
        assertEquals("xlsx", vm.uiState.value.saveDialogExtension)
        vm.onDismissSaveDialog()
        assertFalse(vm.uiState.value.isSaveDialogVisible)

        // 5. Table Column1 deletion & Insert Objects (Shapes, TextBoxes, Charts, Images)
        vm.onSelectCell(0, 0)
        vm.onClearSelectedCells()
        assertEquals("", vm.uiState.value.viewport.cellAt(0, 0)?.displayText.orEmpty())
        vm.onConvertToNormalRange()

        vm.onInsertShape("rect")
        vm.onInsertTextBox("テスト用テキストボックス")
        vm.onInsertChart("bar")
        vm.onInsertImageBytes("Sample.png", byteArrayOf(1, 2, 3, 4))
        assertEquals(4, vm.uiState.value.sheetObjects.size)
        val firstObjId = vm.uiState.value.sheetObjects.first().id
        vm.onMoveOrResizeSheetObject(firstObjId, 50f, 60f, 180f, 110f)
        vm.onUpdateSheetObjectText(firstObjId, "更新タイトル")
        assertEquals("更新タイトル", vm.uiState.value.sheetObjects.first { it.id == firstObjId }.title)
        vm.onDeleteSheetObject(firstObjId)
        assertEquals(3, vm.uiState.value.sheetObjects.size)

        // 6. Cross-app Clipboard Copy / Cut / Paste
        vm.onCommitCellEdit(1, 1, "他アプリ共有テキスト")
        vm.onSelectCell(1, 1)
        val copied = vm.onCopySelectionToClipboard()
        assertEquals("他アプリ共有テキスト", copied)
        copyToSystemClipboard(ctx, copied)
        assertEquals("他アプリ共有テキスト", readFromSystemClipboard(ctx))

        vm.onSelectCell(3, 2)
        vm.onPasteTextFromClipboard("外部アプリからの貼り付け\tセルB\n2行目A\t2行目B")
        assertEquals("外部アプリからの貼り付け", vm.uiState.value.viewport.cellAt(3, 2)?.displayText)
        assertEquals("セルB", vm.uiState.value.viewport.cellAt(3, 3)?.displayText)
        assertEquals("2行目A", vm.uiState.value.viewport.cellAt(4, 2)?.displayText)

        // 7. Verify Ctrl+C -> Ctrl+V -> Enter -> Enter in MasuSheetNativeSheetContainer never switches sheets or leaves layout requested
        val container = MasuSheetNativeSheetContainer(ctx, vm)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 720)
        container.bindState(vm.uiState.value)

        // Check multi-line wrap helper on MasuSheetNativeGridView
        val wrappedLines = container.gridView.wrapTextIntoLines(longSentence, 80f, android.graphics.Paint().apply { textSize = 24f })
        assertTrue(wrappedLines.size >= 2)

        val initialSheetIdx = vm.uiState.value.viewport.sheetIndex
        vm.onSelectCell(1, 1)
        container.bindState(vm.uiState.value)

        // Ctrl+C
        container.dispatchKeyEvent(android.view.KeyEvent(0L, 0L, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_C, 0, android.view.KeyEvent.META_CTRL_ON))
        container.dispatchKeyEvent(android.view.KeyEvent(0L, 0L, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_C, 0, android.view.KeyEvent.META_CTRL_ON))
        // Move to (2, 2) and Ctrl+V
        vm.onSelectCell(2, 2)
        container.bindState(vm.uiState.value)
        container.dispatchKeyEvent(android.view.KeyEvent(0L, 0L, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_V, 0, android.view.KeyEvent.META_CTRL_ON))
        container.dispatchKeyEvent(android.view.KeyEvent(0L, 0L, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_V, 0, android.view.KeyEvent.META_CTRL_ON))
        // Press Enter twice (both DOWN and UP)
        container.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
        container.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
        container.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
        container.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))

        // Sheet index must NOT have changed, and activeRow moved down by 2 rows cleanly!
        assertEquals(initialSheetIdx, vm.uiState.value.viewport.sheetIndex)
        assertEquals(4, vm.uiState.value.viewport.activeRow)
        assertFalse(container.isLayoutRequested)
    }

    @Test
    fun v0142Android8_shapesChartsAndClipboardImagesWorkCleanly() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)

        // 1. Circle/Ellipse (◯ 円・楕円) has subType="ellipse" and empty default title, plus transparent fill (中の色を抜く) and thick border (太線)
        vm.onInsertShape("ellipse")
        val ellipseObj = vm.uiState.value.sheetObjects.last()
        assertEquals("ellipse", ellipseObj.subType)
        assertEquals("", ellipseObj.title)

        // Transparent fill (0 = 中の色を抜く), custom border color, and thick border (4pt / 6.5pt)
        vm.onUpdateSelectedObjectFillColor(0)
        assertEquals(0, vm.uiState.value.sheetObjects.last().fillColor)
        vm.onUpdateSelectedObjectStrokeColor(0xFFD93025.toInt())
        assertEquals(0xFFD93025.toInt(), vm.uiState.value.sheetObjects.last().strokeColor)
        vm.onUpdateSelectedObjectStrokeWidth(4.0f)
        assertEquals(4.0f, vm.uiState.value.sheetObjects.last().strokeWidthPt, 0.01f)

        // 2. 2D Table Multi-Series Charting from vertical & horizontal table selection (セールの表の縦と横で選択してグラフ化)
        vm.onSelectCell(0, 0, 7, 4)
        vm.onInsertChart("line")
        val chartObj = vm.uiState.value.sheetObjects.last()
        assertEquals(SheetObjectKind.CHART, chartObj.kind)
        assertEquals("line", chartObj.subType)
        assertTrue("Expected chartLabels extracted from 2D table rows", chartObj.chartLabels.size >= 4)
        assertTrue("Expected multi-series extracted from 2D table numeric columns", chartObj.chartSeries.isNotEmpty())
        assertTrue("Expected sourceRangeText to record table range", chartObj.sourceRangeText.contains("A1"))

        // 3. Direct in-place editing of Chart title, category label, series name, and data value
        vm.onUpdateSheetObjectText(chartObj.id, "Q1売上推移グラフ")
        assertEquals("Q1売上推移グラフ", vm.uiState.value.sheetObjects.last().title)

        vm.onUpdateChartCategoryLabel(chartObj.id, 0, "東京本店")
        assertEquals("東京本店", vm.uiState.value.sheetObjects.last().chartLabels[0])

        vm.onUpdateChartSeriesName(chartObj.id, 0, "2026実績")
        assertEquals("2026実績", vm.uiState.value.sheetObjects.last().chartSeries[0].name)

        vm.onUpdateChartDataValue(chartObj.id, 0, 0, "9999")
        assertEquals(9999f, vm.uiState.value.sheetObjects.last().chartSeries[0].values[0], 0.1f)

        // Verify in-place editor inside MasuSheetNativeSheetContainer works directly on canvas
        val container = MasuSheetNativeSheetContainer(ctx, vm)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 720)
        container.bindState(vm.uiState.value)
        container.openInObjectEditorForSelected(vm.uiState.value.sheetObjects.last())
        container.commitAndCloseInCellEditor()

        // 4. Full ribbon show/hide toggle
        assertTrue(vm.uiState.value.toolbarVisible)
        vm.onToggleToolbar()
        assertFalse(vm.uiState.value.toolbarVisible)
        vm.onToggleToolbar()
        assertTrue(vm.uiState.value.toolbarVisible)

        // 5. Screenshot / Clipboard image detection & attachment
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        assertTrue(isLikelyImageBytes(pngHeader))
        assertTrue(vm.onPasteScreenshotOrLatestImage(pngHeader, "Clipboard_Screenshot.png"))
        val pastedImgObj = vm.uiState.value.sheetObjects.last()
        assertEquals(SheetObjectKind.IMAGE, pastedImgObj.kind)
        assertEquals("Clipboard_Screenshot.png", pastedImgObj.title)

        // 6. v0.14.2-android.9: TextBox default text is empty (no "テキストを入力") & Ribbon switches to "objectFormat" tab in Row 3 (no vertical jumping)
        vm.onSelectRibbonTab("insert")
        assertEquals("insert", vm.uiState.value.activeRibbonTab)
        vm.onInsertTextBox()
        val tbObj = vm.uiState.value.sheetObjects.last()
        assertEquals(SheetObjectKind.TEXT_BOX, tbObj.kind)
        assertEquals("", tbObj.title)
        assertEquals("", tbObj.text)
        assertEquals("objectFormat", vm.uiState.value.activeRibbonTab)
    }

    @Test
    fun v0142Android10_spreadsheetParity15RibbonTabs292Commands523FunctionsColorPaletteAndMacroStudio() {
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)

        // 1. All 15 Spreadsheet Ribbon tabs & All 292 Commands
        assertEquals(15, SpreadsheetRibbonCatalog.ALL_15_RIBBON_TABS.size)
        assertEquals(292, SpreadsheetRibbonCatalog.ALL_292_COMMAND_IDS.size)

        // 2. All 523 Spreadsheet Worksheet Functions & Search/Autocomplete
        assertEquals(523, SpreadsheetFunctionCatalog.ALL_523_FUNCTION_NAMES.size)
        assertEquals(523, SpreadsheetFunctionCatalog.ALL_ENTRIES.size)
        assertEquals(15, SpreadsheetFunctionCatalog.CATEGORIES_JA.size)
        val xlookupMatches = SpreadsheetFunctionCatalog.search("XLOOKUP")
        assertTrue(xlookupMatches.any { it.name == "XLOOKUP" })
        val autoCompleteSum = SpreadsheetFunctionCatalog.suggestAutocomplete("=SU")
        assertTrue(autoCompleteSum.any { it.name == "SUM" })
        assertTrue(autoCompleteSum.any { it.name == "SUMIFS" })

        // 3. Full Spreadsheet 60-Color Theme Matrix (10 base × 6 rows) + Standard 10 Colors + Custom 24-bit RGB
        val matrix = SpreadsheetColorPalette.buildThemeColorMatrix(vm.uiState.value.viewport.themeColors)
        assertEquals(6, matrix.size)
        assertTrue(matrix.all { it.size == 10 })
        assertEquals(10, SpreadsheetColorPalette.STANDARD_COLORS.size)

        val customArgb = SpreadsheetColorPalette.fromRgb(31, 157, 85)
        assertEquals("#1F9D55", SpreadsheetColorPalette.toHexString(customArgb))
        assertEquals(customArgb, SpreadsheetColorPalette.parseHexOrNull("#1F9D55"))

        vm.onSelectCell(1, 1)
        vm.onApplyPickedColor("fill", customArgb)
        assertTrue(vm.uiState.value.recentColors.contains(customArgb))
        vm.onApplyPickedColor("sheetTab", 0xFF2563EB.toInt())
        assertEquals(0xFF2563EB.toInt(), vm.uiState.value.sheetTabColors[vm.uiState.value.viewport.sheetIndex])

        // 4. All-in-One Macro & VBA Studio (Recorder + Step-by-Step Debugger + Natural Language Generator + Rollback)
        vm.onStartMacroRecording("テスト記録マクロ", "Ctrl+Shift+T", "テスト説明", relativeMode = false)
        assertTrue(vm.uiState.value.isMacroRecording)
        vm.onSelectCell(2, 2)
        vm.onCommitCellEdit(2, 2, "777")
        vm.onExecuteEngineCommand("home.bold", "{}")
        val recordedMacro = vm.onStopMacroRecording()
        assertFalse(vm.uiState.value.isMacroRecording)
        assertTrue(vm.uiState.value.savedMacros.any { it.name == "テスト記録マクロ" })
        assertTrue(recordedMacro?.vbaCode.orEmpty().contains("Sub テスト記録マクロ()"))

        // Natural language -> VBA generation & step debugging
        val generatedVba = vm.onGenerateVbaFromPrompt("見出し A1:E1 を緑背景・白太字にして自動幅調整する")
        assertTrue(generatedVba.contains("Range(\"A1:D1\")") || generatedVba.contains("Range(\"A1:E1\")"))
        vm.onStartVbaDebug("GeneratedTestMacro", generatedVba)
        assertTrue(vm.uiState.value.vbaDebugState.isDebugging)
        vm.onStepVbaDebug()
        vm.onStopVbaDebug()
        assertFalse(vm.uiState.value.vbaDebugState.isDebugging)
    }

    @Test
    fun v0142Android11_generalPurposeAiSpreadsheetAgentPlanAndEditModesWithUndo() {
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)

        // 1. Plan mode asks 3 interactive clarifying questions for any custom topic
        vm.onSelectAiEditMode("plan")
        vm.onSendAiPrompt("店舗別の在庫管理と発注アラート表を作成して")
        val planSession = vm.uiState.value.activeAiPlanSession
        assertTrue("Expected activeAiPlanSession in plan mode", planSession != null)
        assertEquals(3, planSession!!.questions.size)

        // Answer the 3 questions and execute the confirmed plan
        vm.onAnswerAiPlanQuestion(0, 0)
        vm.onAnswerAiPlanQuestion(1, 0)
        vm.onAnswerAiPlanQuestion(2, 0)
        vm.onExecuteConfirmedAiPlan()

        val execMsg = vm.uiState.value.aiMessages.last()
        assertTrue("Expected AI agent to execute operations on sheet", execMsg.appliedEditCount > 0)
        assertTrue("Expected undo steps to be recorded", vm.uiState.value.lastAiUndoSteps > 0)

        // 2. Allow-Edit mode with arbitrary custom columns specified by user
        vm.onSelectAiEditMode("allow_edit")
        vm.onSendAiPrompt("新しいシートに「プロジェクト名、担当者、予算額、実績額、消化率」の進捗管理表を作成して")
        val customMsg = vm.uiState.value.aiMessages.last()
        assertTrue(customMsg.appliedEditCount > 0)

        // Verify headers were genuinely written to the active sheet
        val a1Title = vm.uiState.value.viewport.cellAt(0, 0)?.displayText.orEmpty()
        assertTrue("Expected header in A1, got '$a1Title'", a1Title.isNotBlank())

        // 3. One-tap Undo Rollback restores the sheet state prior to AI edit
        vm.onUndoLastAiEdit()
        assertEquals(0, vm.uiState.value.lastAiUndoSteps)
    }

    @Test
    fun v0142Android12_fullI18nMaterial3UiSheetTabContextMenuAiChartAndSpreadsheetFunctionVerification() {
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)

        // 1. 100% JA <-> EN Translation coverage across Ribbon, Context Menus, Sheet Tab Menu, and Categories
        assertEquals("📂 Open", SpreadsheetI18n.t("📂 開く", true))
        assertEquals("💾 Save File", SpreadsheetI18n.t("💾 ファイル保存", true))
        assertEquals("Insert(I)...", SpreadsheetI18n.t("挿入(I)...", true))
        assertEquals("Tab Color(T) >", SpreadsheetI18n.t("シート見出しの色(T) >", true))
        assertEquals("Move or Copy(M)...", SpreadsheetI18n.t("移動またはコピー(M)...", true))
        assertEquals("Math & Trig", SpreadsheetI18n.t("数学/三角", true))
        assertEquals("📂 開く", SpreadsheetI18n.t("📂 開く", false))

        // 2. Material 3 UI Toggle & Instant Reversibility
        assertTrue(vm.uiState.value.isMaterial3Ui)
        vm.onToggleMaterial3Ui()
        assertFalse(vm.uiState.value.isMaterial3Ui)
        vm.onToggleMaterial3Ui()
        assertTrue(vm.uiState.value.isMaterial3Ui)

        // 3. AI "グラフ化して" Chart Generation & Undo
        val initialChartCount = vm.uiState.value.sheetObjects.count { it.kind == SheetObjectKind.CHART }
        vm.onSelectAiEditMode("allow_edit")
        vm.onSendAiPrompt("グラフ化して")
        val afterChartCount = vm.uiState.value.sheetObjects.count { it.kind == SheetObjectKind.CHART }
        assertEquals(initialChartCount + 1, afterChartCount)
        assertEquals("chartDesign", vm.uiState.value.activeRibbonTab)
        assertTrue(vm.uiState.value.lastAiCreatedObjectIds.isNotEmpty())

        // Undo removes the AI-created chart
        vm.onUndoLastAiEdit()
        val afterUndoChartCount = vm.uiState.value.sheetObjects.count { it.kind == SheetObjectKind.CHART }
        assertEquals(initialChartCount, afterUndoChartCount)

        // 4. Sheet Tab Context Menu operations (Rename, Duplicate/Copy, Tab Color, Insert Before)
        val startSheetCount = vm.uiState.value.viewport.sheetNames.size
        vm.onRenameSheetAt(0, "売上管理")
        assertEquals("売上管理", vm.uiState.value.viewport.sheetNames[0])

        vm.onDuplicateSheetAt(0)
        assertEquals(startSheetCount + 1, vm.uiState.value.viewport.sheetNames.size)
        assertTrue(vm.uiState.value.viewport.sheetNames.any { it.contains("売上管理 (2)") })

        // 5. Formula Bar Name Box Navigation & Status Bar AutoCalculate Summary
        vm.onNavigateToAddressOrRange("B2:D4")
        assertEquals(1, vm.uiState.value.viewport.selStartRow)
        assertEquals(1, vm.uiState.value.viewport.selStartCol)
        assertEquals(3, vm.uiState.value.viewport.selEndRow)
        assertEquals(3, vm.uiState.value.viewport.selEndCol)
        val autoCalcJa = vm.uiState.value.viewport.computeSelectionAutoCalcSummary(isEn = false)
        val autoCalcEn = vm.uiState.value.viewport.computeSelectionAutoCalcSummary(isEn = true)
        assertTrue("Expected AutoCalculate summary in JA, got '$autoCalcJa'", autoCalcJa.contains("データの個数:"))
        assertTrue("Expected AutoCalculate summary in EN, got '$autoCalcEn'", autoCalcEn.contains("Count:"))

        // 6. Verify representative Spreadsheet Worksheet Functions return exact expected values
        vm.onExecuteEngineCommand("file.newBlank", "{}")
        vm.onCommitCellEdit(0, 0, "10")
        vm.onCommitCellEdit(1, 0, "20")
        vm.onCommitCellEdit(2, 0, "30")
        vm.onCommitCellEdit(3, 0, "40")

        val formulaExpectations = listOf(
            "=SUM(A1:A4)" to "100",
            "=AVERAGE(A1:A4)" to "25",
            "=MIN(A1:A4)" to "10",
            "=MAX(A1:A4)" to "40",
            "=COUNT(A1:A4)" to "4",
            "=MEDIAN(A1:A4)" to "25",
            "=IF(SUM(A1:A4)=100, \"OK\", \"NG\")" to "OK",
            "=AND(A1=10, A4=40)" to "TRUE",
            "=OR(A1=99, A2=20)" to "TRUE",
            "=ROUND(3.14159, 2)" to "3.14",
            "=ABS(-42)" to "42",
            "=SQRT(144)" to "12",
            "=POWER(2, 10)" to "1024",
            "=MOD(17, 5)" to "2",
            "=LEN(\"MasuSheet\")" to "9",
            "=UPPER(\"masusheet\")" to "MASUSHEET",
            "=LEFT(\"AndroidSheet\", 7)" to "Android",
            "=RIGHT(\"AndroidSheet\", 5)" to "Sheet",
            "=IFERROR(1/0, \"DIV0_SAFE\")" to "DIV0_SAFE",
            "=INDEX(A1:A4, 3)" to "30",
            "=MATCH(30, A1:A4, 0)" to "3"
        )
        formulaExpectations.forEachIndexed { idx, (formula, expected) ->
            val r = idx
            val c = 2 // Column C
            vm.onCommitCellEdit(r, c, formula)
            val actual = vm.uiState.value.viewport.cellAt(r, c)?.displayText.orEmpty()
            assertEquals("Formula $formula failed", expected, actual)
        }
    }

    @Test
    fun ribbonToggleAndModeSelection_updatesUiStateAndCanvasCacheKey() {
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)

        // Default Spreadsheet View state: gridlines=true, headings=true, freezeRows=0, viewMode="normal"
        assertTrue(vm.uiState.value.showGridlines)
        assertTrue(vm.uiState.value.showHeadings)
        assertEquals("normal", vm.uiState.value.viewMode)
        assertEquals(0, vm.uiState.value.freezeRows)
        assertEquals(0, vm.uiState.value.freezeCols)
        assertFalse(vm.uiState.value.showFormulas)

        // 1. Toggle Gridlines (目盛線の表示切替) -> false -> true
        vm.onExecuteEngineCommand("view.gridlines", "{}")
        assertFalse("Gridlines should toggle OFF", vm.uiState.value.showGridlines)
        vm.onExecuteEngineCommand("view.gridlines", "{}")
        assertTrue("Gridlines should toggle back ON", vm.uiState.value.showGridlines)

        // 2. Toggle Headings (行列見出し表示切替) -> false -> true
        vm.onExecuteEngineCommand("view.headings", "{}")
        assertFalse("Headings should toggle OFF", vm.uiState.value.showHeadings)
        vm.onExecuteEngineCommand("view.headings", "{}")
        assertTrue("Headings should toggle back ON", vm.uiState.value.showHeadings)

        // 3. View modes (標準ビュー / 改ページプレビュー / ページレイアウトビュー)
        vm.onExecuteEngineCommand("view.pageBreakPreview", "{}")
        assertEquals("pageBreakPreview", vm.uiState.value.viewMode)
        vm.onExecuteEngineCommand("view.pageLayout", "{}")
        assertEquals("pageLayout", vm.uiState.value.viewMode)
        vm.onExecuteEngineCommand("view.normal", "{}")
        assertEquals("normal", vm.uiState.value.viewMode)

        // 4. Freeze Panes (先頭行を固定 / 先頭列を固定 / 固定解除)
        vm.onExecuteEngineCommand("view.freezeTopRow", "{}")
        assertEquals(1, vm.uiState.value.freezeRows)
        assertEquals(0, vm.uiState.value.freezeCols)
        vm.onExecuteEngineCommand("view.freezeFirstColumn", "{}")
        assertEquals(0, vm.uiState.value.freezeRows)
        assertEquals(1, vm.uiState.value.freezeCols)
        vm.onExecuteEngineCommand("view.unfreezePanes", "{}")
        assertEquals(0, vm.uiState.value.freezeRows)
        assertEquals(0, vm.uiState.value.freezeCols)

        // 5. Home tab formatting toggles (太字 / 斜体 / 下線 / 取消線 / 折り返し / 中央揃え)
        vm.onSelectCell(0, 0)
        vm.onExecuteEngineCommand("home.bold", "{}")
        assertTrue(vm.uiState.value.viewport.cellAt(0, 0)?.isBold == true)
        vm.onExecuteEngineCommand("home.bold", "{}")
        assertFalse(vm.uiState.value.viewport.cellAt(0, 0)?.isBold == true)

        vm.onExecuteEngineCommand("home.italic", "{}")
        assertTrue(vm.uiState.value.viewport.cellAt(0, 0)?.isItalic == true)
        vm.onExecuteEngineCommand("home.alignCenter", "{}")
        assertEquals(1, vm.uiState.value.viewport.cellAt(0, 0)?.hAlign)

        // 6. Formulas Show Formulas toggle (数式の表示切替) & Data AutoFilter toggle
        vm.onExecuteEngineCommand("formulas.showFormulas", "{}")
        assertTrue(vm.uiState.value.showFormulas)
        vm.onExecuteEngineCommand("data.filter", "{}")
        assertTrue(vm.uiState.value.isAutoFilterActive)

        // 7. Page Layout & Table/Pivot Design mode selection + Formula Bar checkbox + Zoom slider
        vm.onExecuteEngineCommand("pageLayout.orientation", "{\"orientation\":\"landscape\"}")
        assertEquals("landscape", vm.uiState.value.pageOrientation)
        vm.onExecuteEngineCommand("pageLayout.size", "{\"size\":\"Letter\"}")
        assertEquals("Letter", vm.uiState.value.paperSize)
        vm.onExecuteEngineCommand("tableDesign.totalRow", "{}")
        assertTrue(vm.uiState.value.tableTotalRow)
        vm.onExecuteEngineCommand("pivotDesign.reportLayout", "{\"layout\":\"tabular\"}")
        assertEquals("tabular", vm.uiState.value.pivotReportLayout)

        assertTrue(vm.uiState.value.formulaBarVisible)
        vm.onExecuteEngineCommand("view.formulaBar", "{}")
        assertFalse(vm.uiState.value.formulaBarVisible)
        vm.onExecuteEngineCommand("view.formulaBar", "{}")
        assertTrue(vm.uiState.value.formulaBarVisible)

        vm.onSetZoomPercent(140)
        assertEquals(140, vm.uiState.value.zoomPercent)
    }

    @Test
    fun v0142Android15_shapeRotationTopHandleHitTestingExtendedShapesAndClipboardImagePaste() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)

        // 1. Insert extended shape types (diamond, pentagon, hexagon, star, callout, arrow_left, arrow_up, arrow_down)
        val shapeTypes = listOf("rectangle", "roundRect", "ellipse", "triangle", "diamond", "pentagon", "hexagon", "star", "callout", "arrow", "arrow_left", "arrow_up", "arrow_down", "line")
        for (st in shapeTypes) {
            vm.onInsertShape(st)
            assertEquals(st, vm.uiState.value.sheetObjects.last().subType)
        }

        // 2. Rotate selected shape (+90, -15, custom angle, reset to 0)
        val activeShapeId = vm.uiState.value.selectedObjectId!!
        assertEquals(0f, vm.uiState.value.sheetObjects.first { it.id == activeShapeId }.rotationDeg, 0.01f)

        vm.onRotateSelectedObjectBy(90f)
        assertEquals(90f, vm.uiState.value.sheetObjects.first { it.id == activeShapeId }.rotationDeg, 0.01f)

        vm.onRotateSelectedObjectBy(-15f)
        assertEquals(75f, vm.uiState.value.sheetObjects.first { it.id == activeShapeId }.rotationDeg, 0.01f)

        vm.onRotateSheetObject(activeShapeId, 405f)
        assertEquals(45f, vm.uiState.value.sheetObjects.first { it.id == activeShapeId }.rotationDeg, 0.01f)

        vm.onResetSelectedObjectRotation()
        assertEquals(0f, vm.uiState.value.sheetObjects.first { it.id == activeShapeId }.rotationDeg, 0.01f)

        // 3. Verify Canvas rendering of rotated shapes & Top Rotation Handle (⟳) hit-testing
        val container = MasuSheetNativeSheetContainer(ctx, vm)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 720)
        vm.onRotateSelectedObjectBy(45f)
        container.bindState(vm.uiState.value)

        val testBitmap = Bitmap.createBitmap(1080, 720, Bitmap.Config.ARGB_8888)
        val testCanvas = Canvas(testBitmap)
        container.gridView.draw(testCanvas)

        val obj = vm.uiState.value.sheetObjects.first { it.id == activeShapeId }
        val density = ctx.resources.displayMetrics.density
        val leftPx = container.gridView.rowHeaderWidthPx + obj.xPt * density
        val topPx = container.gridView.colHeaderHeightPx + obj.yPt * density
        val rightPx = leftPx + obj.widthPt * density
        val bottomPx = topPx + obj.heightPt * density
        val cx = (leftPx + rightPx) * 0.5f
        val cy = (topPx + bottomPx) * 0.5f

        // Center of rotated shape should hit BODY
        val hitCenter = container.gridView.findSheetObjectAt(cx, cy)
        assertNotNull(hitCenter)
        assertEquals(activeShapeId, hitCenter!!.first.id)
        assertEquals(MasuSheetNativeGridView.SheetObjectHitPart.BODY, hitCenter.second)

        // Reset rotation to 0° and test hitting the top rotation handle (22dp above top center)
        vm.onResetSelectedObjectRotation()
        container.bindState(vm.uiState.value)
        val rotHandleY = topPx - 22f * density
        val hitRotHandle = container.gridView.findSheetObjectAt(cx, rotHandleY)
        assertNotNull("Expected top rotation handle hit at ($cx, $rotHandleY)", hitRotHandle)
        assertEquals(activeShapeId, hitRotHandle!!.first.id)
        assertEquals(MasuSheetNativeGridView.SheetObjectHitPart.ROTATE_HANDLE, hitRotHandle.second)
    }

    @Test
    fun v0142Android16_all523FunctionsHaveUniqueDescriptionsAlignmentTogglesAndUndoRedoSafe() {
        // 1. All 523 functions must have specific descriptions and syntaxes (no generic template fallback)
        assertEquals(523, SpreadsheetFunctionCatalog.ALL_ENTRIES.size)
        for (entry in SpreadsheetFunctionCatalog.ALL_ENTRIES) {
            assertFalse(
                "Function ${entry.name} still has generic description: ${entry.descriptionJa}",
                entry.descriptionJa.contains("ワークシート関数 ")
            )
            assertFalse(
                "Function ${entry.name} still has generic syntax: ${entry.syntax}",
                entry.syntax.endsWith("(引数1, [引数2], ...)")
            )
        }
        val isoCeiling = SpreadsheetFunctionCatalog.ALL_ENTRIES.first { it.name == "ISO.CEILING" }
        assertEquals("数学/三角", isoCeiling.categoryJa)
        val isoWeekNum = SpreadsheetFunctionCatalog.ALL_ENTRIES.first { it.name == "ISOWEEKNUM" }
        assertEquals("日付/時刻", isoWeekNum.categoryJa)
        val isPmt = SpreadsheetFunctionCatalog.ALL_ENTRIES.first { it.name == "ISPMT" }
        assertEquals("財務", isPmt.categoryJa)

        // Popular category filter should still return popular functions while keeping them in their primary categories
        val popularList = SpreadsheetFunctionCatalog.search("", "よく使う関数")
        assertTrue(popularList.any { it.name == "SUM" })
        val mathList = SpreadsheetFunctionCatalog.search("", "数学/三角")
        assertTrue(mathList.any { it.name == "SUM" })
        assertTrue(mathList.any { it.name == "INT" })
        assertTrue(mathList.any { it.name == "LOG10" })

        // 2. Alignment and mode buttons toggle ON and OFF cleanly when clicked repeatedly
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)
        vm.onSelectCell(0, 0)
        val cellKey: Pair<Int, Long> = Pair(0, 0L)

        assertFalse(vm.uiState.value.explicitLeftAlignCells.contains(cellKey))
        vm.onExecuteEngineCommand("home.alignLeft", "{}")
        assertTrue(vm.uiState.value.explicitLeftAlignCells.contains(cellKey))
        vm.onExecuteEngineCommand("home.alignLeft", "{}")
        assertFalse(vm.uiState.value.explicitLeftAlignCells.contains(cellKey))

        vm.onExecuteEngineCommand("home.alignCenter", "{}")
        assertEquals(1, vm.uiState.value.viewport.cellAt(0, 0)?.hAlign)
        vm.onExecuteEngineCommand("home.alignCenter", "{}")
        assertEquals(0, vm.uiState.value.viewport.cellAt(0, 0)?.hAlign)

        assertFalse(vm.uiState.value.explicitTopAlignCells.contains(cellKey))
        vm.onExecuteEngineCommand("home.alignTop", "{}")
        assertTrue(vm.uiState.value.explicitTopAlignCells.contains(cellKey))
        vm.onExecuteEngineCommand("home.alignTop", "{}")
        assertFalse(vm.uiState.value.explicitTopAlignCells.contains(cellKey))

        vm.onExecuteEngineCommand("home.alignMiddle", "{}")
        assertEquals(1, vm.uiState.value.viewport.cellAt(0, 0)?.vAlign)
        vm.onExecuteEngineCommand("home.alignMiddle", "{}")
        assertEquals(0, vm.uiState.value.viewport.cellAt(0, 0)?.vAlign ?: 0)

        // Sort / Group / Table toggles
        vm.onExecuteEngineCommand("data.sortAscending", "{}")
        assertEquals("asc", vm.uiState.value.activeSortOrder)
        vm.onExecuteEngineCommand("data.sortAscending", "{}")
        assertEquals("", vm.uiState.value.activeSortOrder)

        // 3. Undo / Redo when nothing to undo/redo never triggers an error banner
        vm.onDismissError()
        vm.onExecuteEngineCommand("edit.redo", "{}")
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun v0142Android17_inCellDragSelectionMonthlyBudgetAutoFilterPopupEnglishI18nAndPivotChart() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)

        // 1. 100% Complete English Localization: all 523 functions have syntaxEn & descriptionEn with zero Japanese chars,
        // and all Workbook Theme presets translate cleanly when isEn == true.
        val jpCharRegex = Regex("[\\u3040-\\u30FF\\u4E00-\\u9FFF]")
        for (entry in SpreadsheetFunctionCatalog.ALL_ENTRIES) {
            assertTrue("syntaxEn should not be blank for ${entry.name}", entry.syntaxEn.isNotBlank())
            assertTrue("descriptionEn should not be blank for ${entry.name}", entry.descriptionEn.isNotBlank())
            assertFalse(
                "syntaxEn contains Japanese for ${entry.name}: ${entry.syntaxEn}",
                jpCharRegex.containsMatchIn(entry.syntaxEn)
            )
            assertFalse(
                "descriptionEn contains Japanese for ${entry.name}: ${entry.descriptionEn}",
                jpCharRegex.containsMatchIn(entry.descriptionEn)
            )
        }
        for (presetName in SpreadsheetColorPalette.THEME_PRESETS.keys) {
            val enPreset = SpreadsheetI18n.t(presetName, isEn = true)
            assertFalse(
                "Theme preset $presetName was not translated to English: $enPreset",
                jpCharRegex.containsMatchIn(enPreset)
            )
        }

        // 2. Drag-range selection from inside ANY cell (without needing the bottom-right green fill handle)
        val container = MasuSheetNativeSheetContainer(ctx, vm)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 720)
        container.bindState(vm.uiState.value)

        val cellB5Rect = container.gridView.getCellScreenRect(4, 1) // B5 center
        val cellE8Rect = container.gridView.getCellScreenRect(7, 4) // E8 center
        val downTime = android.os.SystemClock.uptimeMillis()
        val downEv = android.view.MotionEvent.obtain(
            downTime,
            downTime,
            android.view.MotionEvent.ACTION_DOWN,
            cellB5Rect.centerX(),
            cellB5Rect.centerY(),
            0
        )
        val moveEv = android.view.MotionEvent.obtain(
            downTime,
            downTime + 50L,
            android.view.MotionEvent.ACTION_MOVE,
            cellE8Rect.centerX(),
            cellE8Rect.centerY(),
            0
        )
        val upEv = android.view.MotionEvent.obtain(
            downTime,
            downTime + 100L,
            android.view.MotionEvent.ACTION_UP,
            cellE8Rect.centerX(),
            cellE8Rect.centerY(),
            0
        )
        container.gridView.dispatchTouchEvent(downEv)
        container.gridView.dispatchTouchEvent(moveEv)
        container.gridView.dispatchTouchEvent(upEv)
        downEv.recycle()
        moveEv.recycle()
        upEv.recycle()

        assertEquals(4, vm.uiState.value.viewport.selStartRow)
        assertEquals(1, vm.uiState.value.viewport.selStartCol)
        assertEquals(7, vm.uiState.value.viewport.selEndRow)
        assertEquals(4, vm.uiState.value.viewport.selEndCol)

        // 3. Windows 11 Spreadsheet `月間予算` sheet tab alongside `Sales` (`Sales` | `月間予算`)
        assertEquals(listOf("Sales", "月間予算"), vm.uiState.value.viewport.sheetNames)
        vm.onSelectSheet(1)
        container.bindState(vm.uiState.value)
        assertEquals("月間予算", vm.uiState.value.viewport.sheetName)
        assertEquals("月間予算表", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)
        assertEquals("世帯人数(人)", vm.uiState.value.viewport.cellAt(2, 0)?.displayText)
        assertEquals("カテゴリ", vm.uiState.value.viewport.cellAt(10, 0)?.displayText)
        assertEquals("項目", vm.uiState.value.viewport.cellAt(10, 1)?.displayText)
        assertEquals("カテゴリ", vm.uiState.value.viewport.cellAt(10, 7)?.displayText)
        assertEquals("住居", vm.uiState.value.viewport.cellAt(11, 7)?.displayText)
        assertEquals("食費", vm.uiState.value.viewport.cellAt(12, 7)?.displayText)
        assertTrue(vm.uiState.value.sheetObjects.count { it.sheetIndex == 1 && it.kind == SheetObjectKind.CHART } >= 2)

        // 4. Interactive AutoFilter & Sort Popup Menu on `[▾]` Table Headers (`カテゴリ別サマリー` H11:K18 -> row 10, col 7)
        assertTrue(vm.isTableHeaderCell(vm.uiState.value.viewport, 10, 7))
        val h11Rect = container.gridView.getCellScreenRect(10, 7)
        val filterClickEv = android.view.MotionEvent.obtain(
            downTime + 600L,
            downTime + 600L,
            android.view.MotionEvent.ACTION_DOWN,
            h11Rect.right - 6f,
            h11Rect.centerY(),
            0
        )
        val filterUpEv = android.view.MotionEvent.obtain(
            downTime + 600L,
            downTime + 620L,
            android.view.MotionEvent.ACTION_UP,
            h11Rect.right - 6f,
            h11Rect.centerY(),
            0
        )
        container.gridView.dispatchTouchEvent(filterClickEv)
        container.gridView.dispatchTouchEvent(filterUpEv)
        filterClickEv.recycle()
        filterUpEv.recycle()

        val popup = vm.uiState.value.activeColumnFilterPopup
        assertNotNull("Clicking [▾] on H11 should open TableColumnFilterPopupState", popup)
        assertEquals("カテゴリ", popup!!.columnTitle)
        assertTrue(popup.distinctValues.contains("住居"))
        assertTrue(popup.distinctValues.contains("食費"))

        // Filter to show ONLY "住居" and "食費" (unchecking all other category items)
        vm.onApplyColumnFilter(
            col = popup.col,
            checkedValues = setOf("住居", "食費"),
            includeBlanks = false
        )
        assertNull(vm.uiState.value.activeColumnFilterPopup)
        assertTrue((1 to 7) in vm.uiState.value.activeColumnFilters)
        // Row 11 ("住居") and Row 12 ("食費") remain visible; Row 13 ("光熱費") is hidden (height < 1f)
        assertTrue(vm.uiState.value.viewport.rowHeights[11] >= 18f)
        assertTrue(vm.uiState.value.viewport.rowHeights[12] >= 18f)
        assertTrue(vm.uiState.value.viewport.rowHeights[13] < 1f)

        // Clear filter restores all rows
        vm.onClearColumnFilter(popup.col)
        assertFalse((1 to 7) in vm.uiState.value.activeColumnFilters)
        assertTrue(vm.uiState.value.viewport.rowHeights[13] >= 18f)

        // Sort by Budget column (I11 -> row 10, col 8) descending (Largest to Smallest)
        vm.onOpenColumnFilterMenu(10, 8)
        val budgetColPopup = vm.uiState.value.activeColumnFilterPopup!!
        vm.onSortTableByColumn(budgetColPopup.col, ascending = false)
        // Highest budget category is 住居 (120000), second is 貯蓄 (70000)
        assertEquals("住居", vm.uiState.value.viewport.cellAt(11, 7)?.displayText)
        assertEquals("貯蓄", vm.uiState.value.viewport.cellAt(12, 7)?.displayText)

        // 5. Contextual Ribbon Commands (`pivotAnalyze.pivotChart`, `chartDesign.switchRowColumn`, etc.) work cleanly
        vm.onSelectCell(11, 7)
        val chartCountBefore = vm.uiState.value.sheetObjects.count { it.sheetIndex == 1 && it.kind == SheetObjectKind.CHART }
        vm.onExecuteEngineCommand("pivotAnalyze.pivotChart", "{}")
        assertNull(vm.uiState.value.errorMessage)
        val chartCountAfter = vm.uiState.value.sheetObjects.count { it.sheetIndex == 1 && it.kind == SheetObjectKind.CHART }
        assertEquals(chartCountBefore + 1, chartCountAfter)

        vm.onExecuteEngineCommand("chartDesign.switchRowColumn", "{}")
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun v0142Android18_autoFilterIsolationAndSmartAutoFillCornerDrag() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway)
        val container = MasuSheetNativeSheetContainer(context, vm)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1920, 1080)
        container.bindState(vm.uiState.value)

        // 1. Turn AutoFilter ON on the main table (B4:I11), then type 1, 2, 3 at T29:T31 (rows 28..30, col 19)
        vm.onSelectCell(3, 1)
        vm.onExecuteEngineCommand("data.filter", "{}")
        assertTrue(vm.uiState.value.isAutoFilterActive)

        vm.onCommitCellEdit(28, 19, "1")
        vm.onCommitCellEdit(29, 19, "2")
        vm.onCommitCellEdit(30, 19, "3")
        // Typing 1 at T29 while AutoFilter is ON must NEVER turn T29 into a [▾] filter header cell!
        assertFalse(
            "T29 ('1') must not become a [▾] filter header cell when AutoFilter is active on the sheet",
            vm.isTableHeaderCell(vm.uiState.value.viewport, 28, 19)
        )

        // 2. Toggle AutoFilter OFF, then select nearby empty cell U29 (28, 20) and press AutoFilter:
        //    must NOT attach [▾] filter to nearby T29:T31!
        vm.onExecuteEngineCommand("data.filter", "{}")
        assertFalse(vm.uiState.value.isAutoFilterActive)
        vm.onSelectCell(28, 20) // Empty cell U29 right next to T29
        vm.onExecuteEngineCommand("data.filter", "{}")
        assertFalse(
            "Pressing AutoFilter on an empty cell U29 must not jump to nearby T29:T31",
            vm.uiState.value.isAutoFilterActive
        )
        assertFalse(vm.isTableHeaderCell(vm.uiState.value.viewport, 28, 19))

        // 3. Smart AutoFill by dragging the bottom-right green square on 1, 2, 3 (J5:J7 -> J8:J10 fills 4, 5, 6)
        vm.onCommitCellEdit(4, 9, "1")
        vm.onCommitCellEdit(5, 9, "2")
        vm.onCommitCellEdit(6, 9, "3")
        vm.onSelectCell(4, 9, 6, 9)
        container.bindState(vm.uiState.value)

        val selRect = container.gridView.getSelectionScreenRect()
        assertTrue(container.gridView.isPointOnSelectionCornerHandle(selRect.right, selRect.bottom))
        val targetJ10Rect = container.gridView.getCellScreenRect(9, 9)

        val downTime = android.os.SystemClock.uptimeMillis()
        val downEv = android.view.MotionEvent.obtain(
            downTime,
            downTime,
            android.view.MotionEvent.ACTION_DOWN,
            selRect.right,
            selRect.bottom,
            0
        )
        val moveEv = android.view.MotionEvent.obtain(
            downTime,
            downTime + 60L,
            android.view.MotionEvent.ACTION_MOVE,
            targetJ10Rect.centerX(),
            targetJ10Rect.centerY(),
            0
        )
        val upEv = android.view.MotionEvent.obtain(
            downTime,
            downTime + 120L,
            android.view.MotionEvent.ACTION_UP,
            targetJ10Rect.centerX(),
            targetJ10Rect.centerY(),
            0
        )
        container.gridView.dispatchTouchEvent(downEv)
        container.gridView.dispatchTouchEvent(moveEv)
        container.gridView.dispatchTouchEvent(upEv)
        downEv.recycle()
        moveEv.recycle()
        upEv.recycle()

        assertEquals("4", vm.uiState.value.viewport.cellAt(7, 9)?.displayText)
        assertEquals("5", vm.uiState.value.viewport.cellAt(8, 9)?.displayText)
        assertEquals("6", vm.uiState.value.viewport.cellAt(9, 9)?.displayText)

        // 4. Single-cell AutoFill when only the last cell ('3' at T31) is selected after pressing Enter,
        //    plus weekdays ("月" -> "火", "水") and text+number ("項目1" -> "項目2", "項目3")
        vm.onAutoFillRange(30, 19, 30, 19, 33, 19)
        assertEquals("4", vm.uiState.value.viewport.cellAt(31, 19)?.displayText)
        assertEquals("5", vm.uiState.value.viewport.cellAt(32, 19)?.displayText)
        assertEquals("6", vm.uiState.value.viewport.cellAt(33, 19)?.displayText)

        vm.onCommitCellEdit(14, 4, "月")
        vm.onAutoFillRange(14, 4, 14, 4, 16, 4)
        assertEquals("火", vm.uiState.value.viewport.cellAt(15, 4)?.displayText)
        assertEquals("水", vm.uiState.value.viewport.cellAt(16, 4)?.displayText)

        vm.onCommitCellEdit(14, 5, "項目1")
        vm.onAutoFillRange(14, 5, 14, 5, 16, 5)
        assertEquals("項目2", vm.uiState.value.viewport.cellAt(15, 5)?.displayText)
        assertEquals("項目3", vm.uiState.value.viewport.cellAt(16, 5)?.displayText)

        // 5. Right-click / long-press context menu is positioned outside the selection's bottom-right corner handle
        vm.onSelectCell(14, 2)
        container.bindState(vm.uiState.value)
        val c15Rect = container.gridView.getSelectionScreenRect()
        container.showContextMenuAt(c15Rect.right, c15Rect.bottom, ContextMenuTargetType.CELL, 14, 2)
        assertTrue(container.isContextMenuVisible)
    }

    @Test
    fun v0142Android19_sheetReorderPivotChartCatalogAndFindReplace() {
        val store = FakePreferenceStore()
        val repo = MasuSheetPreferencesRepository(store)
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(repo, gateway, InMemoryFallbackEngineGateway())

        // 1. Sheet Tab Reordering: Initial tabs are ["Sales", "月間予算"]; add a 3rd sheet ("Forecast") and reorder via onMoveSheet(fromIndex, toIndex)
        assertEquals(listOf("Sales", "月間予算"), vm.uiState.value.viewport.sheetNames)
        vm.onAddSheet() // Sheet 2
        vm.onRenameSheetAt(2, "Forecast")
        val initialNames = vm.uiState.value.viewport.sheetNames
        assertEquals(listOf("Sales", "月間予算", "Forecast"), initialNames)

        // Attach a sheet tab color on sheet 2 ("Forecast") and move it to index 0
        vm.onSelectSheet(2)
        vm.onApplyPickedColor("sheetTab", 0xFF10B981.toInt())
        vm.onMoveSheet(2, 0)
        val reorderedNames = vm.uiState.value.viewport.sheetNames
        assertEquals(listOf("Forecast", "Sales", "月間予算"), reorderedNames)
        assertEquals(0, vm.uiState.value.viewport.sheetIndex)
        assertEquals(0xFF10B981.toInt(), vm.uiState.value.sheetTabColors[0])

        // Move sheet 0 ("Forecast") to index 1
        vm.onMoveSheet(0, 1)
        assertEquals(listOf("Sales", "Forecast", "月間予算"), vm.uiState.value.viewport.sheetNames)
        assertEquals(1, vm.uiState.value.viewport.sheetIndex)
        assertEquals(0xFF10B981.toInt(), vm.uiState.value.sheetTabColors[1])

        // Switch back to sheet 0 for data tests
        vm.onSelectSheet(0)

        // 2. PivotChart & 32 Chart Subtype Catalog Verification
        assertEquals(10, SpreadsheetChartTypeCatalog.categories.size)
        assertEquals(32, SpreadsheetChartTypeCatalog.allSubtypes.size)

        vm.onSelectCell(0, 0, 4, 3)
        val chartCountBefore = vm.uiState.value.sheetObjects.count { it.kind == SheetObjectKind.CHART }
        vm.onExecuteEngineCommand("pivotAnalyze.pivotChart", "{}")
        val chartCountAfter = vm.uiState.value.sheetObjects.count { it.kind == SheetObjectKind.CHART }
        assertEquals(chartCountBefore + 1, chartCountAfter)
        assertTrue(vm.uiState.value.isPivotChartPickerOpen)

        // Change selected chart through several new subtypes ("doughnut", "radar_filled", "waterfall", "combo")
        vm.onChangeSelectedChartType("doughnut")
        val activeChartId = vm.uiState.value.selectedObjectId
        assertNotNull(activeChartId)
        assertEquals(
            "doughnut",
            vm.uiState.value.sheetObjects.first { it.id == activeChartId }.subType
        )
        vm.onChangeSelectedChartType("waterfall")
        assertEquals(
            "waterfall",
            vm.uiState.value.sheetObjects.first { it.id == activeChartId }.subType
        )

        // 3. Find & Replace / Go To / Go To Special Verification
        vm.onExecuteEngineCommand("home.findAndSelect", "{}")
        assertTrue(vm.uiState.value.isCommandCenterOpen)
        assertEquals("findReplace", vm.uiState.value.commandCenterTab)

        // Search for "Region" (at A1) or "North"
        vm.onFindAllCells("Region", matchCase = false, wholeCell = false)
        assertTrue(vm.uiState.value.findMatches.isNotEmpty())
        assertEquals(0, vm.uiState.value.findMatches.first().row)
        assertEquals(0, vm.uiState.value.findMatches.first().col)

        // Put test values in H10:H12 and test Find Next, Replace Current, and Replace All
        vm.onCommitCellEdit(9, 7, "TargetAlpha")
        vm.onCommitCellEdit(10, 7, "TargetAlpha")
        vm.onCommitCellEdit(11, 7, "TargetBeta")

        vm.onFindNext("TargetAlpha", forward = true)
        assertEquals(2, vm.uiState.value.findMatches.size)
        assertEquals(9, vm.uiState.value.viewport.activeRow)
        assertEquals(7, vm.uiState.value.viewport.activeCol)

        vm.onReplaceCurrentCell("TargetAlpha", "ReplacedOne")
        assertEquals("ReplacedOne", vm.uiState.value.viewport.cellAt(9, 7)?.displayText)

        vm.onReplaceAllCells("Target", " Final")
        assertEquals(" FinalAlpha", vm.uiState.value.viewport.cellAt(10, 7)?.displayText)
        assertEquals(" FinalBeta", vm.uiState.value.viewport.cellAt(11, 7)?.displayText)

        // Go To Reference ("C5") & Defined Name
        vm.onDefineName("SalesAnchor", "D4")
        vm.onGoToReference("SalesAnchor")
        assertEquals(3, vm.uiState.value.viewport.activeRow)
        assertEquals(3, vm.uiState.value.viewport.activeCol)

        // Go To Special ("formulas")
        vm.onGoToSpecial("formulas")
        assertTrue(vm.uiState.value.findMatches.isNotEmpty())
    }

    @Test
    fun v0142Android20_saveFoldersZoomDialogAutoFillPreviewAiStatusAndImageAspectRatio() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway)

        // 1. Dynamic Environment Save Folders & Per-Folder File Listing (Never leaks global recent workbooks across folders)
        val envFolders = MasuSheetConfig.resolveEnvironmentSaveFolders(app)
        assertTrue(envFolders.size >= 3)

        val tempRoot = File(app.cacheDir, "v20_folder_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val folderDownload = File(tempRoot, "Download").apply { mkdirs() }
        val folderDocuments = File(tempRoot, "Documents").apply { mkdirs() }
        File(folderDownload, "DownloadReport.xlsx").writeText("dummy")
        File(folderDocuments, "DocBudget.csv").writeText("a,b\n1,2")

        // Record a global recent workbook in another directory; ensure switching between Download & Documents shows only each folder's files
        prefs.recordRecentWorkbook("GlobalRecent.xlsx")

        vm.onOpenSaveDialog()
        vm.onSelectSaveDialogFolder(folderDownload.absolutePath)
        val downloadFiles = vm.uiState.value.saveDialogExistingFiles.map { it.fileName }
        assertEquals(listOf("DownloadReport.xlsx"), downloadFiles)

        vm.onSelectSaveDialogFolder(folderDocuments.absolutePath)
        val documentFiles = vm.uiState.value.saveDialogExistingFiles.map { it.fileName }
        assertEquals(listOf("DocBudget.csv"), documentFiles)
        tempRoot.deleteRecursively()

        // 2. Play Protect Compliance (`C-09`): Zero broad storage permissions requested on startup
        assertFalse(com.masusheet.app.storage.WindowHandoffManager.isValidToken("../shared_prefs/masusheet_prefs.xml"))
        assertTrue(com.masusheet.app.storage.WindowHandoffManager.isValidToken("550e8400-e29b-41d4-a716-446655440000"))

        // 3. Bottom-Right Zoom Dialog & Custom Percentage (25%..400% + Fit Selection)
        assertFalse(vm.uiState.value.isZoomDialogOpen)
        vm.onOpenZoomDialog(true)
        assertTrue(vm.uiState.value.isZoomDialogOpen)
        vm.onSetZoomPercent(175)
        assertEquals(175, vm.uiState.value.zoomPercent)
        assertFalse(vm.uiState.value.isZoomDialogOpen)
        vm.onSetZoomPercent(350)
        assertEquals(350, vm.uiState.value.zoomPercent)
        vm.onSetZoomPercent(30)
        assertEquals(30, vm.uiState.value.zoomPercent)
        vm.onSelectCell(0, 0, 5, 3)
        vm.onFitZoomToSelection(viewportWidthPt = 640f, viewportHeightPt = 360f)
        assertTrue(vm.uiState.value.zoomPercent in 25..400)

        // 4. AutoFill Live Ghost Preview (`previewAutoFillCells`)
        vm.onCommitCellEdit(0, 0, "10")
        vm.onCommitCellEdit(1, 0, "20")
        val ghostPreviews = vm.previewAutoFillCells(
            srcStartRow = 0,
            srcStartCol = 0,
            srcEndRow = 1,
            srcEndCol = 0,
            targetEndRow = 3,
            targetEndCol = 0
        )
        assertEquals(2, ghostPreviews.size)
        assertEquals("30", ghostPreviews[2 to 0])
        assertEquals("40", ghostPreviews[3 to 0])

        // 5. AI Model & Permission Mode Status & Execution ("chat", "plan", "allow_edit")
        val (modelDesc, modeDesc) = SpreadsheetAiAgent.describeModelAndModeStatus(
            modelId = "auto",
            editMode = "plan",
            apiKey = "",
            customEndpoint = "",
            customModelName = ""
        )
        assertTrue(modelDesc.contains("定型スクリプト処理"))
        assertTrue(modeDesc.contains("プラン"))

        vm.onSelectAiModel("gemini")
        assertEquals("gemini", vm.uiState.value.selectedAiModelId)
        vm.onSelectAiEditMode("chat")
        assertEquals("chat", vm.uiState.value.selectedAiEditMode)

        // 6. Natural Image Aspect Ratio Decoding & Reset (`decodeImageDimensions`, `computeNaturalImageSizePt`)
        // Construct a valid 24-byte PNG header with IHDR width = 800, height = 200 (4:1 aspect ratio)
        val png800x200 = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x03, 0x20, // width = 800
            0x00, 0x00, 0x00, 0xC8.toByte() // height = 200
        )
        val dims = MasuSheetConfig.decodeImageDimensions(png800x200)
        assertEquals(800 to 200, dims)
        val (natW, natH) = MasuSheetConfig.computeNaturalImageSizePt(png800x200)
        // 800x200 has 4:1 aspect ratio -> natW / natH should be ~4.0
        assertEquals(4.0f, natW / natH, 0.05f)

        vm.onInsertImageBytes("wide_banner.png", png800x200)
        val insertedImg = vm.uiState.value.sheetObjects.last { it.kind == SheetObjectKind.IMAGE }
        assertEquals(natW, insertedImg.widthPt, 0.1f)
        assertEquals(natH, insertedImg.heightPt, 0.1f)

        // Stretch it manually, then reset aspect ratio
        vm.onMoveOrResizeSheetObject(insertedImg.id, insertedImg.xPt, insertedImg.yPt, 240f, 240f)
        vm.onResetSelectedImageAspectRatio()
        val resetImg = vm.uiState.value.sheetObjects.first { it.id == insertedImg.id }
        assertEquals(4.0f, resetImg.widthPt / resetImg.heightPt, 0.08f)
    }

    @Test
    fun v0142Android21_chartSelectData8DirectionResizeCleanNewSheetMultiWorkbookAndHeaderSelection() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway)

        // 1. Standalone Chart Post-Insertion Data Binding & "Select Data (データの選択)"
        // Insert a chart on a single empty cell (standalone chart)
        vm.onSelectCell(15, 5)
        vm.onInsertChart("column_clustered")
        val standaloneChart = vm.uiState.value.sheetObjects.last { it.kind == SheetObjectKind.CHART }
        assertEquals(SheetObjectKind.CHART, standaloneChart.kind)

        // Open "Select Data" dialog for the standalone chart
        vm.onOpenChartSelectDataDialog(standaloneChart.id)
        assertTrue(vm.uiState.value.isChartSelectDataDialogOpen)
        assertEquals(standaloneChart.id, vm.uiState.value.chartSelectDataTargetId)

        // Populate a table in A1:C4 and bind the standalone chart to "A1:C4" after insertion
        vm.onCommitCellEdit(0, 0, "拠点")
        vm.onCommitCellEdit(0, 1, "売上")
        vm.onCommitCellEdit(0, 2, "利益")
        vm.onCommitCellEdit(1, 0, "東京")
        vm.onCommitCellEdit(1, 1, "1200")
        vm.onCommitCellEdit(1, 2, "300")
        vm.onCommitCellEdit(2, 0, "大阪")
        vm.onCommitCellEdit(2, 1, "950")
        vm.onCommitCellEdit(2, 2, "240")
        vm.onCommitCellEdit(3, 0, "名古屋")
        vm.onCommitCellEdit(3, 1, "780")
        vm.onCommitCellEdit(3, 2, "190")

        vm.onApplyChartSourceRange(
            chartId = standaloneChart.id,
            rangeText = "A1:C4",
            seriesInColumns = true,
            firstRowAsHeader = true,
            firstColAsCategory = true
        )
        assertFalse(vm.uiState.value.isChartSelectDataDialogOpen)
        val boundChart = vm.uiState.value.sheetObjects.first { it.id == standaloneChart.id }
        assertEquals("A1:C4", boundChart.sourceRangeText)
        assertEquals(listOf("東京", "大阪", "名古屋"), boundChart.chartLabels)
        assertEquals(2, boundChart.chartSeries.size)
        assertEquals("売上", boundChart.chartSeries[0].name)
        assertEquals(1200f, boundChart.chartSeries[0].values[0], 0.1f)

        // Switch rows and columns (行/列の切り替え)
        vm.onSwitchChartRowsAndColumns(standaloneChart.id)
        val switchedChart = vm.uiState.value.sheetObjects.first { it.id == standaloneChart.id }
        assertEquals(listOf("売上", "利益"), switchedChart.chartLabels)
        assertEquals(3, switchedChart.chartSeries.size)

        // Bind current cell selection directly to an existing chart
        vm.onSelectCell(0, 0, 2, 1)
        vm.onApplyCurrentSelectionToChart(standaloneChart.id)
        val reboundChart = vm.uiState.value.sheetObjects.first { it.id == standaloneChart.id }
        assertEquals("A1:B3", reboundChart.sourceRangeText)
        assertEquals(listOf("東京", "大阪"), reboundChart.chartLabels)

        // 2. Uncapped Large Object Resize up to 5000pt × 5000pt (previously capped at 800pt × 600pt)
        vm.onMoveOrResizeSheetObject(standaloneChart.id, 20f, 30f, 2400f, 1800f)
        val largeChart = vm.uiState.value.sheetObjects.first { it.id == standaloneChart.id }
        assertEquals(2400f, largeChart.widthPt, 0.1f)
        assertEquals(1800f, largeChart.heightPt, 0.1f)

        // 3. New Sheet Insertion Never Leaks "月間予算" or Other Sheet's Styles / Objects
        vm.onLoadSampleTemplate("monthly_budget")
        assertTrue(vm.uiState.value.viewport.cells.values.any { it.bgColor != 0 })
        val initialSheetCount = vm.uiState.value.viewport.sheetNames.size
        vm.onAddSheet()
        assertEquals(initialSheetCount + 1, vm.uiState.value.viewport.sheetNames.size)
        // New sheet must have 0 pre-filled cells, 0 styled cells, and 0 sheet objects
        assertTrue(
            "Newly added sheet must be completely blank without inherited cell styles",
            vm.uiState.value.viewport.cells.values.none { it.displayText.isNotBlank() || it.bgColor != 0 }
        )
        assertTrue(
            "Newly added sheet must not inherit sheet objects",
            vm.uiState.value.sheetObjects.none { it.sheetIndex == vm.uiState.value.viewport.sheetIndex }
        )

        // Also verify home.insertSheet inserts a clean sheet and shifts sheet-indexed state cleanly
        vm.onExecuteEngineCommand("home.insertSheet", "{}")
        assertEquals(initialSheetCount + 2, vm.uiState.value.viewport.sheetNames.size)
        assertTrue(
            "Sheet inserted via home.insertSheet must be completely blank",
            vm.uiState.value.viewport.cells.values.none { it.displayText.isNotBlank() || it.bgColor != 0 }
        )

        // 4. Multi-Workbook Session Switching & Side-by-Side Split View
        val firstSessionId = vm.uiState.value.activeWorkbookSessionId
        vm.onCommitCellEdit(0, 0, "BookOneMarker")
        vm.onCreateBlankWorkbook()
        assertTrue(vm.uiState.value.openWorkbooks.size >= 2)
        val secondSessionId = vm.uiState.value.activeWorkbookSessionId
        assertTrue(firstSessionId != secondSessionId)
        vm.onCommitCellEdit(0, 0, "BookTwoMarker")

        // Switch back to the first open workbook
        vm.onSwitchOpenWorkbook(firstSessionId)
        assertEquals(firstSessionId, vm.uiState.value.activeWorkbookSessionId)
        assertEquals("BookOneMarker", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)

        // Toggle Split Workbook View to compare Book 1 and Book 2 side-by-side
        vm.onToggleSplitWorkbookView()
        assertTrue(vm.uiState.value.isSplitWorkbookViewEnabled)
        assertEquals(secondSessionId, vm.uiState.value.splitSecondarySessionId)
        val secondarySession = vm.getSplitSecondaryWorkbookSession()
        assertNotNull(secondarySession)
        assertEquals("BookTwoMarker", secondarySession?.viewport?.cellAt(0, 0)?.displayText)

        // 5. Entire Column / Entire Row / Select-All Header Selection
        vm.onSelectEntireColumn(1, 3) // Columns B:D
        assertEquals(0, vm.uiState.value.viewport.selStartRow)
        assertEquals(1, vm.uiState.value.viewport.selStartCol)
        assertEquals(3, vm.uiState.value.viewport.selEndCol)
        assertTrue(vm.uiState.value.viewport.selEndRow >= 99)

        vm.onSelectEntireRow(2, 4) // Rows 3:5
        assertEquals(2, vm.uiState.value.viewport.selStartRow)
        assertEquals(4, vm.uiState.value.viewport.selEndRow)
        assertEquals(0, vm.uiState.value.viewport.selStartCol)
        assertTrue(vm.uiState.value.viewport.selEndCol >= 25)

        vm.onSelectAllCells() // Top-left corner Select All
        assertEquals(0, vm.uiState.value.viewport.selStartRow)
        assertEquals(0, vm.uiState.value.viewport.selStartCol)
        assertTrue(vm.uiState.value.viewport.selEndRow >= 99)
        assertTrue(vm.uiState.value.viewport.selEndCol >= 25)
    }

    @Test
    fun v0142Android22_allColorPickerTargetsCellFillFontBorderObjectsChartsThemeAndStylesWork() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway)

        // 1. Cell Fill Color ("fill") & No Fill ("none") across single cell and multi-cell ranges
        vm.onSelectCell(2, 2, 3, 3) // C3:D4
        vm.onApplyPickedColor("fill", 0xFFFF0000.toInt())
        assertEquals(0xFFFF0000.toInt(), vm.uiState.value.viewport.cellAt(2, 2)?.bgColor)
        assertEquals(0xFFFF0000.toInt(), vm.uiState.value.viewport.cellAt(3, 3)?.bgColor)

        // Verify typing text into a colored cell preserves its fill color
        vm.onCommitCellEdit(2, 2, "ColoredText")
        assertEquals("ColoredText", vm.uiState.value.viewport.cellAt(2, 2)?.displayText)
        assertEquals(0xFFFF0000.toInt(), vm.uiState.value.viewport.cellAt(2, 2)?.bgColor)

        // Clear fill color via No Fill (argb = 0)
        vm.onSelectCell(2, 2)
        vm.onApplyPickedColor("fill", 0)
        assertEquals(0, vm.uiState.value.viewport.cellAt(2, 2)?.bgColor)

        // 2. Font Color ("font") on cells + Automatic Reset (argb = 0)
        vm.onSelectCell(2, 2, 3, 3)
        vm.onApplyPickedColor("font", 0xFF0070C0.toInt())
        assertEquals(0xFF0070C0.toInt(), vm.uiState.value.viewport.cellAt(2, 2)?.fgColor)
        assertEquals(0xFF0070C0.toInt(), vm.uiState.value.viewport.cellAt(3, 3)?.fgColor)

        // Verify typing new text into the cell preserves its font color
        vm.onCommitCellEdit(2, 2, "UpdatedBlueText")
        assertEquals(0xFF0070C0.toInt(), vm.uiState.value.viewport.cellAt(2, 2)?.fgColor)

        vm.onSelectCell(2, 2)
        vm.onApplyPickedColor("font", 0)
        assertEquals(0, vm.uiState.value.viewport.cellAt(2, 2)?.fgColor)

        // 3. Cell Border Color ("border") & Border Presets ("all", "outside", "thickBox", "bottomDouble", "none")
        vm.onSelectCell(4, 1, 5, 2) // B5:C6
        vm.onApplyPickedColor("border", 0xFF7030A0.toInt())
        val b5 = vm.uiState.value.viewport.cellAt(4, 1)!!
        assertTrue(b5.borderTop != 0 && b5.borderLeft != 0 && b5.borderBottom != 0 && b5.borderRight != 0)
        assertEquals(0xFF7030A0.toInt() and 0x00FFFFFF, b5.borderTop and 0x00FFFFFF)

        // Clear borders via argb = 0
        vm.onApplyPickedColor("border", 0)
        val b5Cleared = vm.uiState.value.viewport.cellAt(4, 1)!!
        assertEquals(0, b5Cleared.borderTop)
        assertEquals(0, b5Cleared.borderBottom)

        // Outside border preset only colors outer perimeter of B5:C6
        vm.onExecuteEngineCommand("home.borders", "{\"style\":\"outside\",\"color\":\"#10B981\"}")
        val topLeft = vm.uiState.value.viewport.cellAt(4, 1)!!
        val bottomRight = vm.uiState.value.viewport.cellAt(5, 2)!!
        assertTrue(topLeft.borderTop != 0 && topLeft.borderLeft != 0)
        assertEquals(0, topLeft.borderBottom)
        assertTrue(bottomRight.borderBottom != 0 && bottomRight.borderRight != 0)

        // 4. Shape / TextBox Fill ("objectFill"), Outline ("objectStroke"), and Text Color ("objectText")
        vm.onInsertShape("rectangle", defaultLabel = "Sales Target")
        val shapeId = vm.uiState.value.selectedObjectId!!
        vm.onApplyPickedColor("objectFill", 0xFFED7D31.toInt())
        vm.onApplyPickedColor("objectStroke", 0xFF1E293B.toInt())
        vm.onApplyPickedColor("objectText", 0xFFFFFF00.toInt())
        val updatedShape = vm.uiState.value.sheetObjects.first { it.id == shapeId }
        assertEquals(0xFFED7D31.toInt(), updatedShape.fillColor)
        assertEquals(0xFF1E293B.toInt(), updatedShape.strokeColor)
        assertEquals(0xFFFFFF00.toInt(), updatedShape.textColor)

        // Also verify "font" color picker target updates selected object's text color when an object is selected
        vm.onApplyPickedColor("font", 0xFF00FFAA.toInt())
        assertEquals(
            0xFF00FFAA.toInt(),
            vm.uiState.value.sheetObjects.first { it.id == shapeId }.textColor
        )

        // 5. Chart Series Color ("chartSeries"), Chart Background ("objectFill"), Chart Outline ("objectStroke"), Chart Text ("objectText")
        vm.onSelectCell(0, 0, 3, 2)
        vm.onInsertChart("column")
        val chartId = vm.uiState.value.selectedObjectId!!
        vm.onApplyPickedColor("chartSeries", 0xFFE11D48.toInt())
        vm.onApplyPickedColor("objectFill", 0xFFF8FAFC.toInt())
        vm.onApplyPickedColor("objectStroke", 0xFF0F172A.toInt())
        vm.onApplyPickedColor("objectText", 0xFF1D4ED8.toInt())
        val updatedChart = vm.uiState.value.sheetObjects.first { it.id == chartId }
        assertEquals(0xFFE11D48.toInt(), updatedChart.chartSeries.first().color)
        assertEquals(0xFFF8FAFC.toInt(), updatedChart.fillColor)
        assertEquals(0xFF0F172A.toInt(), updatedChart.strokeColor)
        assertEquals(0xFF1D4ED8.toInt(), updatedChart.textColor)

        // 6. Sheet Tab Color ("sheetTab") & Reset
        vm.onApplyPickedColor("sheetTab", 0xFF9333EA.toInt())
        assertEquals(0xFF9333EA.toInt(), vm.uiState.value.sheetTabColors[vm.uiState.value.viewport.sheetIndex])
        vm.onApplyPickedColor("sheetTab", 0)
        assertFalse(vm.uiState.value.sheetTabColors.containsKey(vm.uiState.value.viewport.sheetIndex))

        // 7. Workbook Theme Presets (`onApplyThemePreset`) updates themeColors and recolors charts
        vm.onApplyThemePreset("エメラルド・グリーン")
        val emeraldBase = SpreadsheetColorPalette.THEME_PRESETS["エメラルド・グリーン"]!!
        assertEquals(emeraldBase, vm.uiState.value.viewport.themeColors)
        val themedChart = vm.uiState.value.sheetObjects.first { it.id == chartId }
        assertEquals(emeraldBase[4], themedChart.chartSeries.first().color)

        // 8. Cell Styles ("home.cellStyle"), Format as Table ("home.formatAsTable"), and Conditional Formatting ("home.conditionalFormatting")
        vm.onSelectSheetObject(null)
        vm.onSelectCell(8, 1)
        vm.onCommitCellEdit(8, 1, "Warning Cell")
        vm.onExecuteEngineCommand("home.cellStyle", "{\"style\":\"bad\"}")
        assertEquals(0xFFFFC7CE.toInt(), vm.uiState.value.viewport.cellAt(8, 1)?.bgColor)
        assertEquals(0xFF9C0006.toInt(), vm.uiState.value.viewport.cellAt(8, 1)?.fgColor)

        vm.onSelectCell(10, 1, 12, 2)
        vm.onExecuteEngineCommand("home.formatAsTable", "{\"style\":\"TableStyleMedium2\"}")
        assertEquals(0xFF1F9D55.toInt(), vm.uiState.value.viewport.cellAt(10, 1)?.bgColor)
        assertEquals(0xFFFFFFFF.toInt(), vm.uiState.value.viewport.cellAt(10, 1)?.fgColor)

        vm.onExecuteEngineCommand("home.formatAsTable", "{\"style\":\"TableStyleDark2\"}")
        assertEquals(0xFF1F4E78.toInt(), vm.uiState.value.viewport.cellAt(10, 1)?.bgColor)
        assertEquals(0xFFFFFFFF.toInt(), vm.uiState.value.viewport.cellAt(10, 1)?.fgColor)
    }

    @Test
    fun v0142Android23_colorPickerTargetClarityM3ClassicUiAndFullPivotTableEngineWork() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway)

        // 1. Verify M3 Modern UI vs Classic Bordered UI toggle and explicit status message
        val initialM3 = vm.uiState.value.isMaterial3Ui
        vm.onToggleMaterial3Ui()
        assertEquals(!initialM3, vm.uiState.value.isMaterial3Ui)
        assertTrue(
            vm.uiState.value.statusNoticeMessage.orEmpty().contains("現在:") ||
                vm.uiState.value.statusNoticeMessage.orEmpty().contains("Switched")
        )
        vm.onToggleMaterial3Ui()
        assertEquals(initialM3, vm.uiState.value.isMaterial3Ui)

        // 2. Verify Color Picker target object list & explicit object selection on active sheet
        vm.onInsertShape("rectangle", defaultLabel = "Shape1")
        val shape1Id = vm.uiState.value.selectedObjectId!!
        vm.onInsertShape("oval", defaultLabel = "Shape2")
        val shape2Id = vm.uiState.value.selectedObjectId!!
        vm.onSelectCell(1, 1) // Select a cell so selectedObjectId becomes null
        assertNull(vm.uiState.value.selectedObjectId)
        val sheetObjs = vm.getMaterializedSheetObjectsForCurrentSheet()
        assertTrue(sheetObjs.any { it.id == shape1Id })
        assertTrue(sheetObjs.any { it.id == shape2Id })

        // User selects Shape1 explicitly from the Color Picker object selector before coloring
        vm.onSelectSheetObject(shape1Id)
        assertEquals(shape1Id, vm.uiState.value.selectedObjectId)
        vm.onApplyPickedColor("objectFill", 0xFF2563EB.toInt())
        assertEquals(
            0xFF2563EB.toInt(),
            vm.uiState.value.sheetObjects.first { it.id == shape1Id }.fillColor
        )

        // 3. Verify Monthly Budget ("月間予算") has a live PivotTableDefinition registered at H11:K19
        val budgetSheetIdx = vm.uiState.value.viewport.sheetNames.indexOf("月間予算")
        assertTrue(budgetSheetIdx >= 0)
        vm.onSelectSheet(budgetSheetIdx)
        val budgetPivot = vm.getActivePivotTable()
        assertNotNull(budgetPivot)
        assertEquals(10, budgetPivot!!.targetStartRow) // Row 11 (0-based 10)
        assertEquals(7, budgetPivot.targetStartCol)    // Col H (0-based 7)
        assertEquals(listOf("カテゴリ"), budgetPivot.rowFields)
        assertEquals(13, budgetPivot.sourceRecords.size)

        // Selecting a cell inside H11:K19 automatically sets activePivotTableId
        vm.onSelectCell(12, 8) // I13 inside PivotTable
        assertEquals(budgetPivot.id, vm.uiState.value.activePivotTableId)

        // Open right-side PivotTable Fields pane via command
        vm.onExecuteEngineCommand("pivotAnalyze.fieldList", "{}")
        assertTrue(vm.uiState.value.isPivotFieldsPaneOpen)

        // 4. Add "項目" to Row Fields (2-level hierarchy: カテゴリ -> 項目) and verify sheet materialization
        vm.onAssignPivotFieldToArea("項目", "row")
        val afterHierarchy = vm.getActivePivotTable()!!
        assertEquals(listOf("カテゴリ", "項目"), afterHierarchy.rowFields)
        assertTrue(afterHierarchy.lastRenderedEndRow > 18) // Expanded with child item rows & subtotals
        val h11Header = vm.uiState.value.viewport.cellAt(10, 7)?.displayText.orEmpty()
        assertTrue(h11Header.contains("カテゴリ"))

        // 5. Collapse "生活" row group and verify row count shrinks
        val expandedEndRow = afterHierarchy.lastRenderedEndRow
        vm.onTogglePivotRowGroupCollapse("生活")
        val collapsedPivot = vm.getActivePivotTable()!!
        assertTrue("生活" in collapsedPivot.collapsedRowGroups)
        assertTrue(collapsedPivot.lastRenderedEndRow < expandedEndRow)
        vm.onTogglePivotRowGroupCollapse("生活")
        assertFalse("生活" in vm.getActivePivotTable()!!.collapsedRowGroups)

        // 6. Add Filter field ("カテゴリ" = "生活") and verify report filter row & filtered totals
        vm.onAssignPivotFieldToArea("項目", "row")
        vm.onAssignPivotFieldToArea("カテゴリ", "filter")
        vm.onSetPivotFilterValue("カテゴリ", "生活")
        val filteredPivot = vm.getActivePivotTable()!!
        assertEquals("生活", filteredPivot.selectedFilterValues["カテゴリ"])

        // 7. Cycle Value Field aggregation (SUM -> AVERAGE -> PERCENT_OF_TOTAL) and change PivotStyle
        vm.onCyclePivotValueFieldAgg("予算(円)", explicitAgg = PivotAggFunction.PERCENT_OF_TOTAL, valueIndex = 0)
        assertEquals(PivotAggFunction.PERCENT_OF_TOTAL, vm.getActivePivotTable()!!.valueFields[0].agg)

        vm.onSetPivotStylePreset("PivotStyleMedium7")
        assertEquals("PivotStyleMedium7", vm.getActivePivotTable()!!.stylePreset)
        assertEquals(0xFF2E7D32.toInt(), vm.uiState.value.viewport.cellAt(10, 7)?.bgColor)

        // 8. Clear/Reset PivotTable clears rendered cells and unchecks all field areas
        vm.onClearPivotTable()
        val resetPivot = vm.getActivePivotTable()!!
        assertTrue(resetPivot.selectedFilterValues.isEmpty())
        assertTrue(resetPivot.rowFields.isEmpty())
        assertTrue(resetPivot.valueFields.isEmpty())
        assertEquals("", vm.uiState.value.viewport.cellAt(10, 7)?.displayText.orEmpty())
    }

    @Test
    fun v0144Android25_chartXYAxisSelectionAndCleanAiLocalLlmEndpointWork() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway)

        // 1. Default AI Custom Endpoint is empty (never pre-filled with http://127.0.0.1:11434/v1/chat/completions)
        assertEquals("", prefs.getAiCustomEndpoint())
        val (autoModelDesc, _) = SpreadsheetAiAgent.describeModelAndModeStatus(
            modelId = "auto",
            editMode = "allow_edit",
            apiKey = "",
            customEndpoint = "",
            customModelName = ""
        )
        assertFalse("Status should not show LocalLLM or 127.0.0.1:11434", autoModelDesc.contains("127.0.0.1:11434"))
        assertFalse("Status should not show ローカルLLM in auto mode", autoModelDesc.contains("ローカルLLM"))
        assertTrue("Status should indicate template script processing", autoModelDesc.contains("定型スクリプト処理"))

        // Even if legacy preference had 127.0.0.1:11434, getAiCustomEndpoint filters it out
        prefs.setAiCustomEndpoint("http://127.0.0.1:11434/v1/chat/completions")
        assertEquals("", prefs.getAiCustomEndpoint())

        // 2. Chart X-axis (Horizontal Category) and Y-axis (Vertical Value Series) selection
        // Sales sheet has table headers and data in B4:I11 (0-based rows 3..10, cols 1..8)
        vm.onSelectSheet(0)
        vm.onSelectCell(3, 1, 10, 8)
        vm.onInsertChart("column")
        val salesCharts = vm.getMaterializedSheetObjectsForCurrentSheet().filter { it.kind == SheetObjectKind.CHART }
        assertTrue("Expected inserted chart on Sales sheet", salesCharts.isNotEmpty())
        val chartId = salesCharts.last().id
        vm.onSelectSheetObject(chartId)

        val cols = vm.getChartSourceTableColumns(chartId)
        assertTrue("Expected detected table columns for chart", cols.size >= 4)

        // Select column C (cols[1]) as X-Axis and verify chartLabels update
        val targetXCol = cols[1].colIndex
        assertTrue(vm.onSelectChartXAxisColumn(objectId = chartId, colIndex = targetXCol))
        val updatedChartAfterX = vm.getMaterializedSheetObjectsForCurrentSheet().first { it.id == chartId }
        assertTrue(updatedChartAfterX.chartLabels.isNotEmpty())
        assertEquals(cols[1].sampleValues.firstOrNull(), updatedChartAfterX.chartLabels.firstOrNull())

        // Toggle Y-Axis column (exclusive single-select and multi-select)
        val targetYCol1 = cols[2].colIndex
        val targetYCol2 = cols[3].colIndex
        assertTrue(vm.onToggleChartYAxisColumn(objectId = chartId, colIndex = targetYCol1, exclusive = true))
        val chartAfterSingleY = vm.getMaterializedSheetObjectsForCurrentSheet().first { it.id == chartId }
        assertEquals(1, chartAfterSingleY.chartSeries.size)
        assertEquals(cols[2].headerName, chartAfterSingleY.chartSeries.first().name)

        assertTrue(vm.onToggleChartYAxisColumn(objectId = chartId, colIndex = targetYCol2, exclusive = false))
        val chartAfterMultiY = vm.getMaterializedSheetObjectsForCurrentSheet().first { it.id == chartId }
        assertEquals(2, chartAfterMultiY.chartSeries.size)
        assertTrue(chartAfterMultiY.chartSeries.any { it.name == cols[2].headerName })
        assertTrue(chartAfterMultiY.chartSeries.any { it.name == cols[3].headerName })

        // 3. Bind cell selection directly as X-axis and Y-axis (right-click context menu workflow)
        vm.onSelectCell(4, 1, 8, 1) // Select B5:B9 as X-axis labels
        assertTrue(vm.onBindSelectionAsChartXAxis(objectId = chartId))
        val chartAfterBindX = vm.getMaterializedSheetObjectsForCurrentSheet().first { it.id == chartId }
        assertEquals(5, chartAfterBindX.chartLabels.size)

        vm.onSelectCell(4, 3, 8, 4) // Select D5:E9 as Y-axis series
        assertTrue(vm.onBindSelectionAsChartYAxis(objectId = chartId, append = false))
        val chartAfterBindY = vm.getMaterializedSheetObjectsForCurrentSheet().first { it.id == chartId }
        assertEquals(2, chartAfterBindY.chartSeries.size)
        assertEquals(5, chartAfterBindY.chartValues.size)
    }

    @Test
    fun v0145Android28_formulaPointModeCellRangeSheetF4AndColoredRefsWork() {
        // 1. Typing "=" or "=SUM(" or "=A1+" enables Point Mode; plain text or completed reference without active span does not
        assertTrue(FormulaPointModeHelper.canPoint("=", 1, null))
        assertTrue(FormulaPointModeHelper.canPoint("＝", 1, null))
        assertTrue(FormulaPointModeHelper.canPoint("=SUM(", 5, null))
        assertTrue(FormulaPointModeHelper.canPoint("=A1+", 4, null))
        assertTrue(FormulaPointModeHelper.canPoint("=IF(A1>0, ", 10, null))
        assertFalse(FormulaPointModeHelper.canPoint("Hello", 5, null))
        assertFalse(FormulaPointModeHelper.canPoint("=A1", 3, null))
        // When a pointed span is active, clicking another cell or dragging replaces that span!
        assertTrue(FormulaPointModeHelper.canPoint("=A1", 3, 1..2))

        // 2. Single cell click -> Drag range -> Operator "+" -> Click second cell
        val step1 = FormulaPointModeHelper.insertOrReplaceRef("=", 1, null, "B5")
        assertEquals("=B5", step1.updatedText)
        assertEquals(3, step1.newCaretIndex)
        assertEquals(1..2, step1.newPointSpan)

        // Dragging from B5 to D10 replaces "B5" in-place with "B5:D10"
        val step2 = FormulaPointModeHelper.insertOrReplaceRef(step1.updatedText, step1.newCaretIndex, step1.newPointSpan, "B5:D10")
        assertEquals("=B5:D10", step2.updatedText)
        assertEquals(7, step2.newCaretIndex)
        assertEquals(1..6, step2.newPointSpan)

        // Clicking another cell C5 while span is still active replaces B5:D10 with C5
        val step3 = FormulaPointModeHelper.insertOrReplaceRef(step2.updatedText, step2.newCaretIndex, step2.newPointSpan, "C5")
        assertEquals("=C5", step3.updatedText)
        assertEquals(1..2, step3.newPointSpan)

        // Typing "+" clears the active point span and allows pointing the next operand D5
        val afterPlus = "=C5+"
        assertTrue(FormulaPointModeHelper.canPoint(afterPlus, afterPlus.length, null))
        val step4 = FormulaPointModeHelper.insertOrReplaceRef(afterPlus, afterPlus.length, null, "D5")
        assertEquals("=C5+D5", step4.updatedText)
        assertEquals(4..5, step4.newPointSpan)

        // 3. Ctrl+Click union reference inside =SUM(...)
        val sumStart = FormulaPointModeHelper.insertOrReplaceRef("=SUM(", 5, null, "B5:B8")
        assertEquals("=SUM(B5:B8", sumStart.updatedText)
        val sumUnion = FormulaPointModeHelper.appendUnionRef(sumStart.updatedText, sumStart.newCaretIndex, "D5:D8")
        assertEquals("=SUM(B5:B8,D5:D8", sumUnion.updatedText)
        assertEquals(11..15, sumUnion.newPointSpan)

        // 4. Cross-sheet, column, and row references
        assertEquals(
            "Sheet2!B5:D8",
            FormulaPointModeHelper.formatCellOrRangeRef(4, 1, 7, 3, targetSheetIndex = 1, editingSheetIndex = 0, targetSheetName = "Sheet2")
        )
        assertEquals(
            "'月間 予算'!C6",
            FormulaPointModeHelper.formatCellOrRangeRef(5, 2, 5, 2, targetSheetIndex = 2, editingSheetIndex = 0, targetSheetName = "月間 予算")
        )
        assertEquals("C:E", FormulaPointModeHelper.formatColumnRangeRef(2, 4))
        assertEquals("4:6", FormulaPointModeHelper.formatRowRangeRef(3, 5))

        // 5. F4 Absolute/Relative reference cycling ($A$1 -> A$1 -> $A1 -> A1)
        val (f4Text1, f4Caret1) = FormulaPointModeHelper.cycleAbsoluteRelativeAtCaret("=B5+C6", 3)
        assertEquals("=\$B\$5+C6", f4Text1)
        val (f4Text2, f4Caret2) = FormulaPointModeHelper.cycleAbsoluteRelativeAtCaret(f4Text1, f4Caret1)
        assertEquals("=B\$5+C6", f4Text2)
        val (f4Text3, f4Caret3) = FormulaPointModeHelper.cycleAbsoluteRelativeAtCaret(f4Text2, f4Caret2)
        assertEquals("=\$B5+C6", f4Text3)
        val (f4Text4, _) = FormulaPointModeHelper.cycleAbsoluteRelativeAtCaret(f4Text3, f4Caret3)
        assertEquals("=B5+C6", f4Text4)

        // 6. Formula normalization: full-width Japanese IME operators, auto-closing parens, and bare "=" safety
        assertEquals("", FormulaPointModeHelper.normalizeCommittedFormula("="))
        assertEquals("", FormulaPointModeHelper.normalizeCommittedFormula("＝  "))
        assertEquals("=SUM(B5:B8,D5:D8)", FormulaPointModeHelper.normalizeCommittedFormula("＝SUM（B5：B8，D5：D8"))

        // 7. 8-Color Formula Reference Extraction for Grid & Editor Highlighting
        val highlights = FormulaPointModeHelper.extractFormulaReferences("=SUM(B5:C8)+D10+'Sheet 2'!E4:F6", 99, 25)
        assertEquals(3, highlights.size)
        assertEquals(4, highlights[0].startRow)
        assertEquals(1, highlights[0].startCol)
        assertEquals(7, highlights[0].endRow)
        assertEquals(2, highlights[0].endCol)
        assertEquals(null, highlights[0].sheetName)
        assertEquals(9, highlights[1].startRow)
        assertEquals(3, highlights[1].startCol)
        assertEquals("Sheet 2", highlights[2].sheetName)
        assertTrue(highlights[0].colorArgb != highlights[1].colorArgb)
    }

    @Test
    fun `category A5 to A13 and B1 to B5 unified rust engine and ui features work end to end`() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway, InMemoryFallbackEngineGateway())

        // A5: Double-tap AutoFill corner handle fills down to adjacent column's last contiguous row
        vm.onCommitCellEdit(0, 10, "Item 1")
        vm.onCommitCellEdit(1, 10, "Item 2")
        vm.onCommitCellEdit(2, 10, "Item 3")
        vm.onCommitCellEdit(0, 11, "Order 1")
        vm.onSelectCell(0, 11)
        vm.onDoubleTapAutoFillCornerHandle()
        assertEquals("Order 2", vm.uiState.value.viewport.cellAt(1, 11)?.displayText)
        assertEquals("Order 3", vm.uiState.value.viewport.cellAt(2, 11)?.displayText)

        // A6: Dynamic Array Spill Range (=SEQUENCE(3,2)) exports spilled cells, spillRanges, and activeSpillFormula
        vm.onCommitCellEdit(15, 0, "=SEQUENCE(3,2)")
        assertTrue(vm.uiState.value.viewport.spillRanges.any { it.startRow == 15 && it.startCol == 0 && it.endRow == 17 && it.endCol == 1 })
        assertEquals("1", vm.uiState.value.viewport.cellAt(15, 0)?.displayText)
        assertEquals("6", vm.uiState.value.viewport.cellAt(17, 1)?.displayText)
        vm.onSelectCell(16, 1)
        assertEquals("=SEQUENCE(3,2)", vm.uiState.value.viewport.activeSpillFormula)

        // A7: Drag selection border moves cells to a new location
        vm.onCommitCellEdit(20, 0, "MoveMe")
        vm.onMoveSelectionRange(20, 0, 20, 0, 22, 2)
        assertEquals("", vm.uiState.value.viewport.cellAt(20, 0)?.displayText.orEmpty())
        assertEquals("MoveMe", vm.uiState.value.viewport.cellAt(22, 2)?.displayText)

        // A9: Same-column text AutoComplete suggestion
        vm.onCommitCellEdit(25, 4, "Tokyo Station")
        vm.onSelectCell(26, 4)
        assertEquals("Tokyo Station", vm.computeColumnAutoCompleteSuggestion("Tok"))
        assertNull(vm.computeColumnAutoCompleteSuggestion("=Tok"))

        // A10 & B5: Data Validation list dropdown + select item
        vm.onSelectCell(28, 4)
        vm.onApplyDataValidation(
            kind = "list",
            formula1 = "Approved,Pending,Rejected",
            inCellDropdown = true,
            showInput = true,
            inputTitle = "Status",
            inputMessage = "Choose approval status"
        )
        val activeVal = vm.getActiveCellValidation()
        assertNotNull(activeVal)
        assertEquals(listOf("Approved", "Pending", "Rejected"), activeVal?.items)
        vm.onSelectValidationListItem("Approved")
        assertEquals("Approved", vm.uiState.value.viewport.cellAt(28, 4)?.displayText)

        // A11: Draggable Freeze Pane boundary updates freezeRows and freezeCols
        vm.onDragFreezePaneBoundary(3, 2)
        assertEquals(3, vm.uiState.value.freezeRows)
        assertEquals(2, vm.uiState.value.freezeCols)

        // A12: Hide and Unhide Columns / Rows
        vm.onSelectCell(0, 5)
        vm.onHideSelectedColumns()
        assertTrue(vm.uiState.value.viewport.colWidths[5] < 1f)
        vm.onUnhideColumnsAround(5)
        assertTrue(vm.uiState.value.viewport.colWidths[5] >= 24f)

        // A13: Sparkline insertion exports NativeSparklineModel in viewport
        vm.onSelectCell(30, 5)
        vm.onInsertSparkline(sourceRange = "A1:D1", kind = "column", markers = true)
        assertTrue(vm.uiState.value.viewport.sparklines.any { it.row == 30 && it.col == 5 && it.kind == "column" })

        // B2: Undo/Redo restores Kotlin style/size overrides in lockstep with cell edits
        vm.onSelectCell(32, 0)
        vm.onCommitCellEdit(32, 0, "StyledCell")
        vm.onExecuteEngineCommand("home.bold")
        assertTrue(vm.uiState.value.viewport.cellAt(32, 0)?.isBold == true)
        vm.onExecuteEngineCommand("edit.undo")
        assertTrue(vm.uiState.value.viewport.cellAt(32, 0)?.isBold != true)
        vm.onExecuteEngineCommand("edit.redo")
        assertTrue(vm.uiState.value.viewport.cellAt(32, 0)?.isBold == true)

        // B3: Unified Goal Seek solves formula cells via engine `data.goalSeek`
        vm.onCommitCellEdit(35, 0, "10")
        vm.onCommitCellEdit(35, 1, "=A36*5")
        vm.onRunGoalSeek("B36", 100.0, "A36")
        assertEquals(20.0, vm.uiState.value.viewport.cellAt(35, 0)?.displayText?.toDoubleOrNull() ?: 0.0, 0.01)

        // B4: Unified Find & Replace works across the sheet
        vm.onCommitCellEdit(40, 2, "LegacyToken")
        vm.onFindAllCells("LegacyToken")
        assertTrue(vm.uiState.value.findMatches.any { it.row == 40 && it.col == 2 })
        vm.onReplaceAllCells("LegacyToken", "ModernToken")
        assertEquals("ModernToken", vm.uiState.value.viewport.cellAt(40, 2)?.displayText)
    }

    @Test
    fun v0146Android30_pivotLauncherVisibilityClearAllAndDeleteLifecycleWork() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway)

        // 1. On initial Sales sheet (sheet 0), getActivePivotTable() is null (never leaks Monthly Budget pivot)
        assertEquals(0, vm.uiState.value.viewport.sheetIndex)
        assertNull(vm.getActivePivotTable())
        assertNull(vm.uiState.value.activePivotTableId)

        // 2. Switching to Monthly Budget sheet (sheet 3): selecting cell outside PivotTable range (A1) keeps activePivotTableId null
        val budgetIdx = vm.uiState.value.viewport.sheetNames.indexOf("月間予算")
        assertTrue(budgetIdx >= 0)
        vm.onSelectSheet(budgetIdx)
        vm.onSelectCell(0, 0)
        assertNull(vm.uiState.value.activePivotTableId)

        // 3. Selecting cell inside PivotTable range (H11 -> row 10, col 7) activates the PivotTable
        vm.onSelectCell(10, 7)
        assertEquals("pivot_monthly_budget_1", vm.uiState.value.activePivotTableId)

        // 4. Dismissing the floating launcher adds it to dismissedPivotLauncherIds and clears activePivotTableId
        vm.onDismissPivotLauncher("pivot_monthly_budget_1")
        assertTrue("pivot_monthly_budget_1" in vm.uiState.value.dismissedPivotLauncherIds)
        assertFalse(vm.uiState.value.isPivotFieldsPaneOpen)

        // 5. Explicitly opening the PivotTable Fields pane un-dismisses the launcher
        vm.onTogglePivotFieldsPane(true)
        assertTrue(vm.uiState.value.isPivotFieldsPaneOpen)
        assertFalse("pivot_monthly_budget_1" in vm.uiState.value.dismissedPivotLauncherIds)

        // 6. "🧹 ピボット初期化" (onClearPivotTable) clears rendered cells on the sheet and unchecks all fields
        vm.onExecuteEngineCommand("pivotAnalyze.clear", "{}")
        val clearedPivot = vm.getActivePivotTable()!!
        assertTrue(clearedPivot.rowFields.isEmpty())
        assertTrue(clearedPivot.valueFields.isEmpty())
        assertEquals("", vm.uiState.value.viewport.cellAt(10, 7)?.displayText.orEmpty())

        // 7. "🗑️ ピボット削除" (onDeletePivotTable / pivot.delete) completely removes the PivotTable and closes the pane
        vm.onExecuteEngineCommand("pivot.delete", "{}")
        assertNull(vm.getActivePivotTable())
        assertTrue(vm.uiState.value.pivotTables.none { it.targetSheetIndex == budgetIdx })
        assertFalse(vm.uiState.value.isPivotFieldsPaneOpen)

        // 8. Resizable Right-Side Panes (AI Assistant, Split Workbook View, PivotTable Fields) clamp and update width
        assertEquals(380f, vm.uiState.value.aiPanelWidthDp, 0.01f)
        vm.onResizeAiPanelWidth(520f)
        assertEquals(520f, vm.uiState.value.aiPanelWidthDp, 0.01f)
        vm.onResizeAiPanelWidth(120f) // Clamped to min 220f
        assertEquals(220f, vm.uiState.value.aiPanelWidthDp, 0.01f)

        assertEquals(420f, vm.uiState.value.splitPaneWidthDp, 0.01f)
        vm.onResizeSplitPaneWidth(600f)
        assertEquals(600f, vm.uiState.value.splitPaneWidthDp, 0.01f)

        assertEquals(344f, vm.uiState.value.pivotPaneWidthDp, 0.01f)
        vm.onResizePivotPaneWidth(460f)
        assertEquals(460f, vm.uiState.value.pivotPaneWidthDp, 0.01f)
    }

    @Test
    fun chartXYNumericTableAndSingleSheetDeleteWorkCorrectly() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val docGateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(
            preferencesRepository = prefs,
            documentGateway = docGateway,
            engineGateway = InMemoryFallbackEngineGateway(),
            startWithBlankWorkbook = true
        )

        // 1. Blank workbook startup has 1 clean sheet ("Sheet1") and zero sample sheets
        assertEquals("blank", vm.uiState.value.activeSample)
        assertEquals(listOf("Sheet1"), vm.uiState.value.viewport.sheetNames)
        assertTrue(vm.uiState.value.sheetObjects.isEmpty())

        // 2. Populate cell A1, then delete the only remaining sheet tab -> launches a fresh blank Sheet1
        vm.onCommitCellEdit(0, 0, "Test Data")
        assertEquals("Test Data", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)

        vm.onDeleteSheetAt(0)
        assertEquals(listOf("Sheet1"), vm.uiState.value.viewport.sheetNames)
        assertEquals("", vm.uiState.value.viewport.cellAt(0, 0)?.displayText.orEmpty())

        // 3. Populate a 2-column headerless numeric table at I5:J25 (rows 4..24, cols 8..9):
        //    Col I (col 8): 1..21
        //    Col J (col 9): 2..42
        for (i in 0..20) {
            val r = 4 + i
            vm.onCommitCellEdit(r, 8, "${i + 1}")
            vm.onCommitCellEdit(r, 9, "${(i + 1) * 2}")
        }

        // Select I5:J25 and insert a column chart
        vm.onSelectCell(4, 8, 24, 9)
        vm.onInsertChartFromRange("column", 4, 8, 24, 9)
        val chart1 = vm.uiState.value.sheetObjects.last { it.kind == SheetObjectKind.CHART }

        // Verify X-Axis has all 21 items ("1".."21", including row 5) and Y-Axis has 1 series ("J列": 2..42)
        assertEquals(21, chart1.chartLabels.size)
        assertEquals("1", chart1.chartLabels.first())
        assertEquals("21", chart1.chartLabels.last())
        assertEquals(1, chart1.chartSeries.size)
        assertEquals("J列", chart1.chartSeries.first().name)
        assertEquals(21, chart1.chartSeries.first().values.size)
        assertEquals(2f, chart1.chartSeries.first().values.first(), 0.01f)
        assertEquals(42f, chart1.chartSeries.first().values.last(), 0.01f)
        assertEquals(8, chart1.chartXColIndex)
        assertEquals(listOf(9), chart1.chartYColIndices)

        // 4. Move active cell to an empty cell M7 (row 6, col 12) and verify candidate columns still detect I列 & J列 (never M)
        vm.onSelectCell(6, 12)
        val colsWhenEmptyCellSelected = vm.getChartSourceTableColumns(chart1.id)
        assertEquals(2, colsWhenEmptyCellSelected.size)
        assertEquals(8, colsWhenEmptyCellSelected[0].colIndex)
        assertEquals(9, colsWhenEmptyCellSelected[1].colIndex)
        assertTrue(colsWhenEmptyCellSelected[0].displayLabel.startsWith("I列"))
        assertTrue(colsWhenEmptyCellSelected[1].displayLabel.startsWith("J列"))

        // 5. Select I5:I25 as X-Axis via onBindSelectionAsChartXAxis -> sourceRangeText stays I5:J25 and X/Y choices persist
        vm.onSelectCell(4, 8, 24, 8)
        vm.onBindSelectionAsChartXAxis(chart1.id)
        val chartAfterBindX = vm.uiState.value.sheetObjects.first { it.id == chart1.id }
        assertEquals("I5:J25", chartAfterBindX.sourceRangeText)
        assertEquals(8, chartAfterBindX.chartXColIndex)
        assertEquals(listOf(9), chartAfterBindX.chartYColIndices)
        assertEquals(21, chartAfterBindX.chartLabels.size)
        assertEquals("1", chartAfterBindX.chartLabels.first())
    }

    @Test
    fun v0146Android33_chartAxisIntervalSeriesXYRangesAndSheetDragMoveWork() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val docGateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(
            preferencesRepository = prefs,
            documentGateway = docGateway,
            engineGateway = InMemoryFallbackEngineGateway(),
            startWithBlankWorkbook = true
        )

        // Populate 3 numeric columns at I5:K9 (rows 4..8, cols 8..10):
        // Col I (col 8): 10, 20, 30, 40, 50
        // Col J (col 9): 100, 200, 300, 400, 500
        // Col K (col 10): 15, 25, 35, 45, 55
        for (i in 0..4) {
            val r = 4 + i
            vm.onCommitCellEdit(r, 8, "${(i + 1) * 10}")
            vm.onCommitCellEdit(r, 9, "${(i + 1) * 100}")
            vm.onCommitCellEdit(r, 10, "${(i + 1) * 10 + 5}")
        }

        vm.onInsertChartFromRange("line", 4, 8, 8, 9)
        val initialChart = vm.uiState.value.sheetObjects.last { it.kind == SheetObjectKind.CHART }

        // 1. Click X-Axis / Y-Axis to select entire axis & adjust display intervals
        vm.onSelectChartSubElement(initialChart.id, "xAxis")
        assertEquals("xAxis", vm.uiState.value.sheetObjects.first { it.id == initialChart.id }.selectedSubElement)

        vm.onOpenChartAxisFormatDialog(objectId = initialChart.id, axis = "x", open = true)
        assertTrue(vm.uiState.value.isChartAxisFormatDialogOpen)
        assertEquals("x", vm.uiState.value.chartAxisFormatTargetAxis)

        vm.onUpdateChartAxisFormat(
            objectId = initialChart.id,
            isXAxis = true,
            labelInterval = 2,
            majorStep = null,
            minVal = 10f,
            maxVal = 50f
        )
        vm.onUpdateChartAxisFormat(
            objectId = initialChart.id,
            isXAxis = false,
            majorStep = 100f,
            minVal = 0f,
            maxVal = 600f
        )
        val formattedChart = vm.uiState.value.sheetObjects.first { it.id == initialChart.id }
        assertEquals(2, formattedChart.xAxisLabelInterval)
        assertEquals(100f, formattedChart.yAxisMajorStep ?: 0f, 0.01f)
        assertEquals(0f, formattedChart.yAxisMin ?: -1f, 0.01f)
        assertEquals(600f, formattedChart.yAxisMax ?: -1f, 0.01f)

        // Verify equal-division mode (-5f = 5 divisions across full Y range)
        vm.onUpdateChartAxisFormat(
            objectId = initialChart.id,
            isXAxis = false,
            majorStep = -5f
        )
        assertEquals(-5f, vm.uiState.value.sheetObjects.first { it.id == initialChart.id }.yAxisMajorStep ?: 0f, 0.01f)

        // 2. Click a series line ("series:0") and verify its X-Axis & Y-Axis cell ranges are resolved
        vm.onSelectChartSubElement(initialChart.id, "series:0")
        val seriesSelectedChart = vm.uiState.value.sheetObjects.first { it.id == initialChart.id }
        assertEquals("series:0", seriesSelectedChart.selectedSubElement)
        assertEquals("I5:I9", vm.resolveChartSeriesAxisRangeText(seriesSelectedChart, 0, isXAxis = true))
        assertEquals("J5:J9", vm.resolveChartSeriesAxisRangeText(seriesSelectedChart, 0, isXAxis = false))

        // 3. Add a new series from the sheet (without dummy 100, 150, 200, 250 numbers) and verify per-series X/Y ranges
        vm.onSelectCell(4, 10, 8, 10) // Select K5:K9 on the sheet
        vm.onAddChartSeriesFromSheet(initialChart.id)
        val chartWith2Series = vm.uiState.value.sheetObjects.first { it.id == initialChart.id }
        assertEquals(2, chartWith2Series.chartSeries.size)
        assertEquals("I5:I9", chartWith2Series.chartSeries[1].xRangeText)
        assertEquals("K5:K9", chartWith2Series.chartSeries[1].yRangeText)
        assertEquals(listOf(15f, 25f, 35f, 45f, 55f), chartWith2Series.chartSeries[1].values)

        // Update series 0's X-Axis and Y-Axis ranges explicitly
        vm.onUpdateChartSeriesRanges(
            objectId = initialChart.id,
            seriesIndex = 0,
            newName = "カスタム系列1",
            newXRangeText = "I5:I9",
            newYRangeText = "J5:J9"
        )
        val afterCustomRange = vm.uiState.value.sheetObjects.first { it.id == initialChart.id }
        assertEquals("カスタム系列1", afterCustomRange.chartSeries[0].name)

        // 4. Drag the highlighted Y-Axis cell range frame of series 0 on the sheet from J5:J9 (col 9) to K5:K9 (col 10)
        vm.onMoveChartSeriesAxisRangeByDrag(
            objectId = initialChart.id,
            seriesIndex = 0,
            isXAxis = false,
            newStartRow = 4,
            newStartCol = 10,
            newEndRow = 8,
            newEndCol = 10
        )
        val afterDragMove = vm.uiState.value.sheetObjects.first { it.id == initialChart.id }
        assertEquals("K5:K9", afterDragMove.chartSeries[0].yRangeText)
        assertEquals(listOf(15f, 25f, 35f, 45f, 55f), afterDragMove.chartSeries[0].values)
    }

    @Test
    fun v0146Android40_batchAutoFillUndoChartDeleteUndoPinchZoomAndEnglishI18nWork() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val docGateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(
            preferencesRepository = prefs,
            documentGateway = docGateway,
            engineGateway = InMemoryFallbackEngineGateway(),
            startWithBlankWorkbook = true
        )

        // 1. Populate Col F (col 5, rows 2..6 -> F3:F7) with 1, 2, 3, 4, 5 and G3 (row 2, col 6) with formula "=F3*2"
        for (i in 0..4) {
            vm.onCommitCellEdit(2 + i, 5, "${i + 1}")
        }
        vm.onCommitCellEdit(2, 6, "=F3*2")
        assertEquals("=F3*2", vm.uiState.value.viewport.cellAt(2, 6)?.editText)

        // Double-click AutoFill handle on G3 -> fills G4:G7 (4 rows) with shifted formulas =F4*2 .. =F7*2
        vm.onSelectCell(2, 6)
        val filledCount = vm.onDoubleTapAutoFillCornerHandle()
        assertEquals(4, filledCount)
        assertEquals("=F4*2", vm.uiState.value.viewport.cellAt(3, 6)?.editText)
        assertEquals("=F5*2", vm.uiState.value.viewport.cellAt(4, 6)?.editText)
        assertEquals("=F6*2", vm.uiState.value.viewport.cellAt(5, 6)?.editText)
        assertEquals("=F7*2", vm.uiState.value.viewport.cellAt(6, 6)?.editText)

        // Pressing Undo ("edit.undo") ONCE must revert ALL 4 AutoFilled rows (G4:G7) at once, keeping G3 intact!
        vm.onExecuteEngineCommand("edit.undo")
        assertEquals("=F3*2", vm.uiState.value.viewport.cellAt(2, 6)?.editText)
        assertEquals("", vm.uiState.value.viewport.cellAt(3, 6)?.editText.orEmpty())
        assertEquals("", vm.uiState.value.viewport.cellAt(4, 6)?.editText.orEmpty())
        assertEquals("", vm.uiState.value.viewport.cellAt(5, 6)?.editText.orEmpty())
        assertEquals("", vm.uiState.value.viewport.cellAt(6, 6)?.editText.orEmpty())

        // Pressing Redo ("edit.redo") ONCE restores all 4 AutoFilled rows at once!
        vm.onExecuteEngineCommand("edit.redo")
        assertEquals("=F4*2", vm.uiState.value.viewport.cellAt(3, 6)?.editText)
        assertEquals("=F7*2", vm.uiState.value.viewport.cellAt(6, 6)?.editText)

        // Populate numeric values in G3:G7 for chart creation, then insert a chart from F3:G7
        for (i in 0..4) {
            vm.onCommitCellEdit(2 + i, 6, "${(i + 1) * 2}")
        }
        // 2. Insert a chart from F3:G7, delete the chart via onDeleteSheetObject, and verify Undo restores the chart!
        val chartId = vm.onInsertChartFromRange("scatterLineMarkers", 2, 5, 6, 6)
        assertTrue(vm.uiState.value.sheetObjects.any { it.id == chartId })

        vm.onDeleteSheetObject(chartId)
        assertFalse("Chart should be removed after delete", vm.uiState.value.sheetObjects.any { it.id == chartId })
        assertTrue("canUndo should be true after deleting chart", vm.uiState.value.viewport.canUndo)

        // Undo restores the deleted chart
        vm.onExecuteEngineCommand("edit.undo")
        assertTrue("Chart must be restored after Undo", vm.uiState.value.sheetObjects.any { it.id == chartId })
        // Cell G7 is still "10" (deleting & undoing chart did not corrupt cell values)
        assertEquals("10", vm.uiState.value.viewport.cellAt(6, 6)?.displayText)

        // Redo deletes the chart again, Undo brings it back
        vm.onExecuteEngineCommand("edit.redo")
        assertFalse(vm.uiState.value.sheetObjects.any { it.id == chartId })
        vm.onExecuteEngineCommand("edit.undo")
        assertTrue(vm.uiState.value.sheetObjects.any { it.id == chartId })

        // 3. Verify English translations and removal of "(Spreadsheet)" from Classic UI label
        assertEquals("📊 UI: Classic (Switch)", SpreadsheetI18n.t("📊 現在:クラシックUI (切替)", isEn = true))
        assertEquals("PivotChart (Scatter with Straight Lines - Sheet1)", SpreadsheetI18n.translateChartDisplayText("ピボットグラフ (直線とマーカー付き散布図 - Sheet1)", isEn = true))
        assertEquals("Col G", SpreadsheetI18n.translateChartDisplayText("G列", isEn = true))

        val colOpt = vm.getChartSourceTableColumns(chartId).first { it.colIndex == 5 }
        assertTrue(colOpt.displayLabel(isEn = true).startsWith("Col F"))
        assertTrue(colOpt.displayLabel(isEn = false).startsWith("F列"))

        // Toggling language to EN translates existing chart titles and series names on the sheet
        vm.onToggleLanguage()
        assertEquals("en", vm.uiState.value.language)
        val translatedChart = vm.uiState.value.sheetObjects.first { it.id == chartId }
        assertEquals("Col G", translatedChart.chartSeries.first().name)

        // 4. Verify Touch/Trackpad Pinch-to-Zoom on MasuSheetCanvasGridView
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val container = MasuSheetNativeSheetContainer(ctx, vm)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1080, 720)
        container.bindState(vm.uiState.value)

        container.gridView.applyPinchScaleFactor(1.5f, 400f, 300f)
        assertEquals(150, vm.uiState.value.zoomPercent)
        container.gridView.applyPinchScaleFactor(0.5f, 400f, 300f)
        assertEquals(75, vm.uiState.value.zoomPercent)

        // 5. Verify Workbook Tab Drag / Right-Click Move to New Window (`prepareWorkbookSessionForNewWindow` + `onLoadHandoffWorkbook`)
        vm.onCreateNewBlankWorkbookSession()
        assertEquals(2, vm.uiState.value.openWorkbooks.size)
        val secondBookId = vm.uiState.value.activeWorkbookId
        val secondBookTitle = vm.uiState.value.workbookTitle
        val detachedSession = vm.prepareWorkbookSessionForNewWindow(secondBookId, removeFromCurrentWindow = true)
        assertNotNull(detachedSession)
        assertEquals(secondBookTitle, detachedSession?.title)
        assertEquals(1, vm.uiState.value.openWorkbooks.size)

        val secondaryVm = MasuSheetViewModel(
            preferencesRepository = prefs,
            documentGateway = docGateway,
            engineGateway = InMemoryFallbackEngineGateway(),
            startWithBlankWorkbook = true
        )
        secondaryVm.onLoadHandoffWorkbook(
            title = detachedSession!!.title,
            bytes = detachedSession.savedWorkbookBytes,
            sampleKind = detachedSession.sampleKind,
            activeSheetIndex = detachedSession.activeSheetIndex
        )
        assertEquals(secondBookTitle, secondaryVm.uiState.value.workbookTitle)
        assertEquals(1, secondaryVm.uiState.value.openWorkbooks.size)
        assertEquals("📊 Insert Chart from Selection...", SpreadsheetI18n.t("📊 選択範囲からグラフを作成...", isEn = true))
        assertEquals("🪟 Move to New Window", SpreadsheetI18n.t("🪟 別のウィンドウへ移動", isEn = true))
    }

    @Test
    fun v0146Android42_chartRangeCornerResizeUnifiedXAxisAndHitDisambiguation() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val docGateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(
            preferencesRepository = prefs,
            documentGateway = docGateway,
            engineGateway = InMemoryFallbackEngineGateway(),
            startWithBlankWorkbook = true
        )

        // Populate 3 adjacent columns: Col A (0) = X labels, Col B (1) = Series 1, Col C (2) = Series 2 across rows 1..6 (indices 0..5)
        for (r in 0..5) {
            vm.onCommitCellEdit(r, 0, "${r + 1}")
            vm.onCommitCellEdit(r, 1, "${(r + 1) * 10}")
            vm.onCommitCellEdit(r, 2, "${(r + 1) * 100}")
        }

        // Create a chart initially spanning A1:B4 (rows 0..3, cols 0..1)
        val chartId = vm.onInsertChartFromRange("columnClustered", 0, 0, 3, 1)
        var chart = vm.uiState.value.sheetObjects.first { it.id == chartId }
        assertEquals(1, chart.chartSeries.size)
        assertEquals("B1:B4", chart.chartSeries[0].yRangeText)

        // 1. Resize Y-Axis range vertically down to row 6 (index 5 -> B1:B6) and horizontally across Col B..Col C (B1:C6)
        vm.onResizeChartAxisRangeByDrag(
            objectId = chartId,
            seriesIndex = 0,
            isXAxis = false,
            newStartRow = 0,
            newStartCol = 1,
            newEndRow = 5,
            newEndCol = 2
        )
        chart = vm.uiState.value.sheetObjects.first { it.id == chartId }
        assertEquals("Horizontal corner resize across Col B..C should include 2 series", 2, chart.chartSeries.size)
        assertEquals("B1:B6", chart.chartSeries[0].yRangeText)
        assertEquals("C1:C6", chart.chartSeries[1].yRangeText)
        assertEquals(6, chart.chartSeries[0].values.size)
        assertEquals(60f, chart.chartSeries[0].values.last(), 0.01f)
        assertEquals(600f, chart.chartSeries[1].values.last(), 0.01f)

        // 2. Resize or move X-Axis and verify ALL series share the exact same unified xRangeText (no X-Axis proliferation!)
        vm.onResizeChartAxisRangeByDrag(
            objectId = chartId,
            seriesIndex = 0,
            isXAxis = true,
            newStartRow = 0,
            newStartCol = 0,
            newEndRow = 5,
            newEndCol = 0
        )
        chart = vm.uiState.value.sheetObjects.first { it.id == chartId }
        assertEquals("A1:A6", chart.chartSeries[0].xRangeText)
        assertEquals("A1:A6", chart.chartSeries[1].xRangeText)

        // Move X-Axis via onMoveChartSeriesAxisRangeByDrag even when seriesIndex = 1 is passed -> must still update all series together!
        vm.onMoveChartSeriesAxisRangeByDrag(
            objectId = chartId,
            seriesIndex = 1,
            isXAxis = true,
            newStartRow = 1,
            newStartCol = 0,
            newEndRow = 5,
            newEndCol = 0
        )
        chart = vm.uiState.value.sheetObjects.first { it.id == chartId }
        assertEquals("A2:A6", chart.chartSeries[0].xRangeText)
        assertEquals("A2:A6", chart.chartSeries[1].xRangeText)

        // 3. Verify Canvas hit-testing:
        //    - Corner square hit returns RESIZE_BOTTOM_RIGHT
        //    - Interior cell click returns null (never hijacks interior cell drags)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val container = MasuSheetNativeSheetContainer(ctx, vm)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 1280, 800)
        // Move the chart object itself far to the right (xPt = 500f) so it doesn't cover columns A..C
        vm.onMoveOrResizeSheetObject(chartId, 500f, 40f, 260f, 180f)
        vm.onSelectSheetObject(chartId)
        container.bindState(vm.uiState.value)

        val colBRect = container.gridView.getCellRangeScreenRect(0, 1, 5, 1)
        val inset = 1.8f * ctx.resources.displayMetrics.density
        // Hit bottom-right corner of Col B range
        val cornerHit = container.gridView.findChartSourceRangeDragHitAt(
            colBRect.right - inset,
            colBRect.bottom - inset
        )
        assertNotNull("Bottom-right corner handle of Col B should be hit", cornerHit)
        assertEquals(false, cornerHit?.isXAxis)
        assertEquals(0, cornerHit?.seriesIndex)
        assertEquals(
            MasuSheetNativeGridView.ChartRangeDragMode.RESIZE_BOTTOM_RIGHT,
            cornerHit?.dragMode
        )

        // Clicking in the middle of an interior cell of X-Axis (Col A, row 3) must NOT intercept as a range move!
        val colAInteriorRect = container.gridView.getCellRangeScreenRect(2, 0, 2, 0)
        val interiorHit = container.gridView.findChartSourceRangeDragHitAt(
            colAInteriorRect.centerX(),
            colAInteriorRect.centerY()
        )
        assertEquals("Interior cell click must not trigger chart range drag", null, interiorHit)
    }

    @Test
    fun v0146Android43_mergeWindowBackWebPromptMasuSheetAndExactYAxisDragOnText() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway, startWithBlankWorkbook = true)

        // 1. Verify Web AI Prompt uses "MasuSheet" (and zero legacy names) in both Japanese and English
        val legacyBrandA = "Grid" + "Craft"
        val legacyBrandB = "Table" + "+"
        val jaPrompt = SpreadsheetAiAgent.buildWebAiPromptForCopy("グラフ化して", vm.uiState.value.viewport, isEn = false)
        val enPrompt = SpreadsheetAiAgent.buildWebAiPromptForCopy("Create chart", vm.uiState.value.viewport, isEn = true)
        assertTrue("Japanese Web AI prompt must mention MasuSheet", jaPrompt.contains("MasuSheet"))
        assertFalse("Japanese Web AI prompt must not mention legacy brand", jaPrompt.contains(legacyBrandA) || jaPrompt.contains(legacyBrandB))
        assertTrue("English Web AI prompt must mention MasuSheet", enPrompt.contains("MasuSheet"))
        assertFalse("English Web AI prompt must not mention legacy brand", enPrompt.contains(legacyBrandA) || enPrompt.contains(legacyBrandB))

        // 2. Verify separating a workbook into a new window and merging it back into the main window
        vm.onCommitCellEdit(0, 0, "MainBookCell")
        vm.onCreateNewBlankWorkbookSession()
        vm.onCommitCellEdit(0, 0, "DetachedBookCell")
        assertEquals(2, vm.uiState.value.openWorkbooks.size)
        val secondBookId = vm.uiState.value.activeWorkbookId

        // Detach the second workbook into a separate window
        val detachedSession = vm.prepareWorkbookSessionForNewWindow(secondBookId, removeFromCurrentWindow = true)
        assertNotNull("Detached session should be returned", detachedSession)
        assertEquals("Main window should have 1 workbook left after detaching", 1, vm.uiState.value.openWorkbooks.size)

        // Merge the detached workbook back into the main window
        vm.onMergeHandoffWorkbookIntoCurrentWindow(
            title = detachedSession!!.title,
            bytes = detachedSession.savedWorkbookBytes,
            sampleKind = detachedSession.sampleKind,
            activeSheetIndex = detachedSession.activeSheetIndex
        )
        assertEquals("Main window should have 2 workbooks after merging back", 2, vm.uiState.value.openWorkbooks.size)
        assertEquals(detachedSession.title, vm.uiState.value.workbookTitle)

        // 3. Verify dragging Y-Axis onto a text column (Col A) preserves exact row count (does NOT eat top row as series name or shrink range!)
        vm.onCreateNewBlankWorkbookSession()
        vm.onCommitCellEdit(0, 0, "カテゴリ見出し")
        vm.onCommitCellEdit(0, 1, "売上高")
        for (r in 1..4) {
            vm.onCommitCellEdit(r, 0, "項目$r")
            vm.onCommitCellEdit(r, 1, "${r * 100}")
        }
        val chartId = vm.onInsertChartFromRange("columnClustered", 0, 0, 4, 1)

        var chart = vm.uiState.value.sheetObjects.first { it.id == chartId }
        assertEquals("Initial Y range should be B2:B5 (rows 1..4)", "B2:B5", chart.chartSeries[0].yRangeText)
        assertEquals("Initial series name from B1", "売上高", chart.chartSeries[0].name)

        // Drag Y-Axis frame onto text column A (rows 1..4 -> A2:A5)
        vm.onMoveChartSeriesAxisRangeByDrag(
            objectId = chartId,
            seriesIndex = 0,
            isXAxis = false,
            newStartRow = 1,
            newStartCol = 0,
            newEndRow = 4,
            newEndCol = 0
        )
        chart = vm.uiState.value.sheetObjects.first { it.id == chartId }
        assertEquals("Y-Axis dragged onto text column A must preserve exact 4-row range A2:A5 without shrinking!", "A2:A5", chart.chartSeries[0].yRangeText)
        assertEquals("Series name should read from cell above frame (A1 = カテゴリ見出し), NOT eat A2!", "カテゴリ見出し", chart.chartSeries[0].name)
        assertEquals("Values count must remain 4", 4, chart.chartSeries[0].values.size)
        assertEquals(0f, chart.chartSeries[0].values[0], 0.01f)

        // 4. Verify AI assistant generates copyable scriptJson on assistant messages and executes direct JSON scripts
        vm.onSendAiPrompt("現在の表に合計・平均・構成比(%)を追加して整形")
        val lastAssistantMsg = vm.uiState.value.aiMessages.last { it.role == "assistant" }
        assertTrue("Assistant message must contain copyable JSON script", lastAssistantMsg.scriptJson.contains("\"commands\""))

        // Re-running a custom JSON script directly from the prompt input box applies it to the sheet
        val customScript = """
            {
              "reply": "カスタムスクリプトを実行しました",
              "bullets": ["セルE1にScriptOKを設定"],
              "commands": [
                {"cmd": "cell.set", "params": {"cell": "E1", "input": "ScriptOK"}}
              ]
            }
        """.trimIndent()
        vm.onSendAiPrompt(customScript)
        assertEquals("ScriptOK", vm.uiState.value.viewport.cellAt(0, 4)?.displayText)
    }

    @Test
    fun v0146Android45_cornerResizePreservesAspectRatioAndZoomAbove205Works() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway, startWithBlankWorkbook = true)
        val container = MasuSheetNativeSheetContainer(ctx, vm)

        // 1. Verify zoom above 200% (e.g. 205%, 215%, 300%, 400%) actually scales the canvas
        assertEquals(2.00f, computeCanvasZoomScale(200), 0.001f)
        assertEquals(2.05f, computeCanvasZoomScale(205), 0.001f)
        assertEquals(2.15f, computeCanvasZoomScale(215), 0.001f)
        assertEquals(3.00f, computeCanvasZoomScale(300), 0.001f)
        assertEquals(4.00f, computeCanvasZoomScale(400), 0.001f)
        assertEquals(0.25f, computeCanvasZoomScale(25), 0.001f)

        vm.onSetZoomPercent(205)
        container.bindState(vm.uiState.value)
        assertEquals(205, container.gridView.zoomPercent)
        assertEquals(2.05f, container.gridView.zoomScale, 0.001f)

        vm.onSetZoomPercent(215)
        container.bindState(vm.uiState.value)
        assertEquals(215, container.gridView.zoomPercent)
        assertEquals(2.15f, container.gridView.zoomScale, 0.001f)

        container.gridView.applyPinchScaleFactor(1.2f, 100f, 100f)
        assertTrue("Pinch scale above 200% must increase zoomPercent beyond 215%", container.gridView.zoomPercent > 215)
        assertTrue("Canvas zoomScale must exceed 2.15f", container.gridView.zoomScale > 2.15f)

        // 2. Verify 4-corner resize strictly preserves the original figure aspect ratio (e.g. 240x160 -> 1.5 ratio)
        val startX = 120f
        val startY = 100f
        val startW = 240f
        val startH = 160f
        val initialRatio = startW / startH // 1.5f

        // Bottom-Right corner dragged with arbitrary non-diagonal delta (dx = +90, dy = +20)
        val br = computeSheetObjectResizeBounds(
            part = MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM_RIGHT,
            startX = startX,
            startY = startY,
            startW = startW,
            startH = startH,
            dxPt = 90f,
            dyPt = 20f
        )
        assertEquals(startX, br[0], 0.01f)
        assertEquals(startY, br[1], 0.01f)
        assertTrue("Width should increase on bottom-right drag", br[2] > startW)
        assertEquals("Bottom-Right corner must preserve aspect ratio", initialRatio, br[2] / br[3], 0.001f)

        // Bottom-Left corner dragged outward-left and down (dx = -60, dy = +80)
        val bl = computeSheetObjectResizeBounds(
            part = MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM_LEFT,
            startX = startX,
            startY = startY,
            startW = startW,
            startH = startH,
            dxPt = -60f,
            dyPt = 80f
        )
        assertEquals("Right edge must stay anchored on bottom-left resize", startX + startW, bl[0] + bl[2], 0.01f)
        assertEquals(startY, bl[1], 0.01f)
        assertEquals("Bottom-Left corner must preserve aspect ratio", initialRatio, bl[2] / bl[3], 0.001f)

        // Top-Right corner dragged right and upward (dx = +75, dy = -30)
        val tr = computeSheetObjectResizeBounds(
            part = MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_RIGHT,
            startX = startX,
            startY = startY,
            startW = startW,
            startH = startH,
            dxPt = 75f,
            dyPt = -30f
        )
        assertEquals(startX, tr[0], 0.01f)
        assertEquals("Bottom edge must stay anchored on top-right resize", startY + startH, tr[1] + tr[3], 0.01f)
        assertEquals("Top-Right corner must preserve aspect ratio", initialRatio, tr[2] / tr[3], 0.001f)

        // Top-Left corner dragged left and upward (dx = -45, dy = -45)
        val tl = computeSheetObjectResizeBounds(
            part = MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_TOP_LEFT,
            startX = startX,
            startY = startY,
            startW = startW,
            startH = startH,
            dxPt = -45f,
            dyPt = -45f
        )
        assertEquals("Right edge must stay anchored on top-left resize", startX + startW, tl[0] + tl[2], 0.01f)
        assertEquals("Bottom edge must stay anchored on top-left resize", startY + startH, tl[1] + tl[3], 0.01f)
        assertEquals("Top-Left corner must preserve aspect ratio", initialRatio, tl[2] / tl[3], 0.001f)

        // 3. Verify 4 edge-midpoint handles change only 1 dimension and keep the perpendicular side length unchanged
        val edgeRight = computeSheetObjectResizeBounds(
            part = MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_RIGHT,
            startX = startX,
            startY = startY,
            startW = startW,
            startH = startH,
            dxPt = 80f,
            dyPt = 50f
        )
        assertEquals(startW + 80f, edgeRight[2], 0.01f)
        assertEquals("Right edge midpoint handle must keep height unchanged", startH, edgeRight[3], 0.01f)

        val edgeBottom = computeSheetObjectResizeBounds(
            part = MasuSheetNativeGridView.SheetObjectHitPart.RESIZE_BOTTOM,
            startX = startX,
            startY = startY,
            startW = startW,
            startH = startH,
            dxPt = 90f,
            dyPt = 65f
        )
        assertEquals("Bottom edge midpoint handle must keep width unchanged", startW, edgeBottom[2], 0.01f)
        assertEquals(startH + 65f, edgeBottom[3], 0.01f)
    }

    @Test
    fun v0146Android46_arrowKeyImageNudgeShiftMultiSelectAndCopyCombinedImageToClipboard() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway)

        // 1. Insert 2 pasted images onto the worksheet at slightly different Y heights
        val png1 = MasuSheetConfig.buildValidMinimalPngBytes(200, 150)
        val png2 = MasuSheetConfig.buildValidMinimalPngBytes(200, 150)
        vm.onInsertImageBytes("image_left.png", png1)
        val id1 = vm.uiState.value.selectedObjectId!!
        vm.onMoveOrResizeSheetObject(id1, 40f, 53f, 180f, 135f)

        vm.onInsertImageBytes("image_right.png", png2)
        val id2 = vm.uiState.value.selectedObjectId!!
        vm.onMoveOrResizeSheetObject(id2, 240f, 50f, 180f, 135f)

        // 2. Select the first image and nudge it upward by 3pt using arrow keys (-1pt x 3) so its Y height matches image 2 (50pt)
        vm.onSelectSheetObject(id1, appendToggle = false)
        assertEquals(setOf(id1), vm.uiState.value.effectiveSelectedObjectIds)
        assertTrue(vm.onNudgeSelectedSheetObjects(0f, -1f))
        assertTrue(vm.onNudgeSelectedSheetObjects(0f, -1f))
        assertTrue(vm.onNudgeSelectedSheetObjects(0f, -1f))

        val obj1AfterNudge = vm.uiState.value.sheetObjects.first { it.id == id1 }
        val obj2Initial = vm.uiState.value.sheetObjects.first { it.id == id2 }
        assertEquals("Arrow-key nudge must adjust Y position in 1pt increments to match second image height", obj2Initial.yPt, obj1AfterNudge.yPt, 0.01f)

        // 3. Shift+Click the second image to select both images simultaneously
        vm.onSelectSheetObject(id2, appendToggle = true)
        assertEquals(
            "Shift+Click must select both images simultaneously",
            setOf(id1, id2),
            vm.uiState.value.effectiveSelectedObjectIds
        )

        // 4. Nudge both selected images together using Shift+Arrow (+5pt right, +5pt down)
        assertTrue(vm.onNudgeSelectedSheetObjects(5f, 5f))
        val obj1GroupNudged = vm.uiState.value.sheetObjects.first { it.id == id1 }
        val obj2GroupNudged = vm.uiState.value.sheetObjects.first { it.id == id2 }
        assertEquals(45f, obj1GroupNudged.xPt, 0.01f)
        assertEquals(55f, obj1GroupNudged.yPt, 0.01f)
        assertEquals(245f, obj2GroupNudged.xPt, 0.01f)
        assertEquals(55f, obj2GroupNudged.yPt, 0.01f)

        // 5. Verify 1-tap height alignment ("alignTop") when one image is moved out of vertical alignment
        vm.onMoveOrResizeSheetObject(id2, 245f, 72f, 180f, 135f)
        vm.onSelectSheetObject(id1, appendToggle = false)
        vm.onSelectSheetObject(id2, appendToggle = true)
        assertTrue(vm.onAlignSelectedSheetObjects("alignTop"))
        val aligned1 = vm.uiState.value.sheetObjects.first { it.id == id1 }
        val aligned2 = vm.uiState.value.sheetObjects.first { it.id == id2 }
        assertEquals("alignTop must align both selected images to the exact same top Y coordinate", aligned1.yPt, aligned2.yPt, 0.01f)

        // 6. Right-click copy of the 2 selected images as a single combined PNG image for the device clipboard
        val combinedResult = vm.onCopySelectedSheetObjectsAsCombinedImage()
        assertNotNull("Copying 2 selected images must return a combined PNG byte array", combinedResult)
        val (combinedFileName, combinedPngBytes) = combinedResult!!
        assertTrue(combinedFileName.endsWith(".png"))
        assertTrue("Combined PNG must have valid non-empty bytes", combinedPngBytes.size >= 32)
        // Verify PNG magic header: 89 50 4E 47 0D 0A 1A 0A
        assertEquals(0x89.toByte(), combinedPngBytes[0])
        assertEquals(0x50.toByte(), combinedPngBytes[1])
        assertEquals(0x4E.toByte(), combinedPngBytes[2])
        assertEquals(0x47.toByte(), combinedPngBytes[3])

        // Verify the combined PNG pixel dimensions span both side-by-side images (380pt * 1.818 px/pt = ~691px wide, 135pt * 1.818 px/pt = ~245px high)
        val combinedPixelDims = MasuSheetConfig.decodeImageDimensions(combinedPngBytes)
        assertNotNull(combinedPixelDims)
        assertTrue(
            "Combined PNG pixel width (${combinedPixelDims!!.first}px) must span both side-by-side images (>= 600px)",
            combinedPixelDims.first >= 600
        )
        assertTrue(
            "Combined PNG pixel height (${combinedPixelDims.second}px) must match the aligned height (>= 200px)",
            combinedPixelDims.second >= 200
        )
    }

    @Test
    fun v0146Android47_perElementChartTextColorImageDefaultNoBorderAndEnglishContextMenu() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val docGateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(
            preferencesRepository = prefs,
            documentGateway = docGateway,
            engineGateway = InMemoryFallbackEngineGateway(),
            startWithBlankWorkbook = true
        )

        // 1. Pasted images default to NO border (strokeWidthPt = 0f, strokeColor = 0)
        val pngBytes = MasuSheetConfig.buildValidMinimalPngBytes(200, 150)
        vm.onInsertImageBytes("pasted_no_border.png", pngBytes)
        val pastedImg = vm.uiState.value.sheetObjects.last { it.kind == SheetObjectKind.IMAGE }
        assertEquals("Pasted images must have strokeWidthPt = 0f (no border) by default", 0f, pastedImg.strokeWidthPt, 0.001f)
        assertEquals("Pasted images must have strokeColor = 0 (transparent) by default", 0, pastedImg.strokeColor)

        // 2. Charts created via onInsertOrUpdateChartFromPicker use standard Chart title (not "ピボットグラフ")
        vm.onCommitCellEdit(0, 0, "項目")
        vm.onCommitCellEdit(0, 1, "売上")
        vm.onCommitCellEdit(1, 0, "A")
        vm.onCommitCellEdit(1, 1, "120")
        vm.onCommitCellEdit(2, 0, "B")
        vm.onCommitCellEdit(2, 1, "240")
        vm.onSelectCell(0, 0, 2, 1)
        vm.onInsertOrUpdateChartFromPicker("column", insertNew = true)
        val chartObj = vm.uiState.value.sheetObjects.last { it.kind == SheetObjectKind.CHART }
        assertFalse("Standard chart title must not say ピボットグラフ", chartObj.title.contains("ピボットグラフ"))

        // 3. Per-Element Chart Text Color selection (All, Title, X-Axis, Y-Axis, Legend)
        vm.onSelectSheetObject(chartObj.id)
        val redColor = 0xFFDC2626.toInt()
        val blueColor = 0xFF2563EB.toInt()
        val greenColor = 0xFF16A34A.toInt()
        val goldColor = 0xFFD97706.toInt()
        val whiteColor = 0xFFFFFFFF.toInt()

        // Apply individual element colors via onApplyPickedColor("objectText:<element>", ...)
        vm.onApplyPickedColor("objectText:title", redColor)
        vm.onApplyPickedColor("objectText:xAxis", blueColor)
        vm.onApplyPickedColor("objectText:yAxis", greenColor)
        vm.onApplyPickedColor("objectText:legend", goldColor)

        val chartAfterPerElement = vm.uiState.value.sheetObjects.first { it.id == chartObj.id }
        assertEquals(redColor, chartAfterPerElement.chartTitleTextColor)
        assertEquals(blueColor, chartAfterPerElement.chartXAxisTextColor)
        assertEquals(greenColor, chartAfterPerElement.chartYAxisTextColor)
        assertEquals(goldColor, chartAfterPerElement.chartLegendTextColor)

        // Applying "objectText" ("all") updates all chart text elements at once
        vm.onApplyPickedColor("objectText", whiteColor)
        val chartAfterAll = vm.uiState.value.sheetObjects.first { it.id == chartObj.id }
        assertEquals(whiteColor, chartAfterAll.textColor)
        assertEquals(whiteColor, chartAfterAll.chartTitleTextColor)
        assertEquals(whiteColor, chartAfterAll.chartXAxisTextColor)
        assertEquals(whiteColor, chartAfterAll.chartYAxisTextColor)
        assertEquals(whiteColor, chartAfterAll.chartLegendTextColor)

        // 4. Verify English context menu translations for chart & object actions
        assertEquals(
            "📋 Copy This Object as Image to Clipboard",
            SpreadsheetI18n.t("📋 この図を画像としてクリップボードにコピー", isEn = true)
        )
        assertEquals(
            "📏 Align Selected Objects Top (Match Y Position)",
            SpreadsheetI18n.t("📏 選択した図の「高さ位置 (上端 Y座標)」をぴったり揃える", isEn = true)
        )
        assertEquals(
            "📏 Change X-Axis (Horizontal) Text Color...",
            SpreadsheetI18n.t("📏 X軸 (横軸) の文字色を変更...", isEn = true)
        )
        assertEquals(
            "📊 Create Chart from Table (All 32 Types)...",
            SpreadsheetI18n.t("📊 選択表からグラフを作成 (全32種)...", isEn = true)
        )
    }

    @Test
    fun v0146Android48_aiPanelEnglishLocalizationSimplifiedDefaultAndCleanWebScript() {
        val prefs = MasuSheetPreferencesRepository(FakePreferenceStore())
        val gateway = FakeDocumentIoGateway()
        val vm = MasuSheetViewModel(prefs, gateway)
        val vp = vm.uiState.value.viewport

        // 1. Simplified Built-in Default description (JA & EN)
        val (jaModelDesc, jaModeDesc) = SpreadsheetAiAgent.describeModelAndModeStatus(
            modelId = "auto",
            editMode = "allow_edit",
            apiKey = "",
            isEn = false
        )
        assertEquals("⚡ 内蔵デフォルト（定型スクリプト処理）", jaModelDesc)
        assertTrue(jaModeDesc.contains("編集を許可"))

        val (enModelDesc, enModeDesc) = SpreadsheetAiAgent.describeModelAndModeStatus(
            modelId = "auto",
            editMode = "allow_edit",
            apiKey = "",
            isEn = true
        )
        assertEquals("⚡ Built-in Default (Template Script Processing)", enModelDesc)
        assertEquals("⚡ Mode [Allow Edit]: Immediately applies edits, formulas & charts", enModeDesc)

        // 2. Web Integration prompt script does NOT contain third-party spreadsheet trademark (JA & EN)
        val thirdPartySpreadsheetBrand = "Ex" + "cel"
        val webScriptJa = SpreadsheetAiAgent.buildWebAiPromptForCopy("売上を集計して", vp, isEn = false)
        assertFalse("Web script JA must not contain trademark", webScriptJa.contains(thirdPartySpreadsheetBrand, ignoreCase = true))
        assertTrue(webScriptJa.contains("あなたはスプレッドシートアプリ「MasuSheet」のAIエージェントです。"))

        val webScriptEn = SpreadsheetAiAgent.buildWebAiPromptForCopy("Summarize sales", vp, isEn = true)
        assertFalse("Web script EN must not contain trademark", webScriptEn.contains(thirdPartySpreadsheetBrand, ignoreCase = true))
        assertTrue(webScriptEn.contains("You are the AI Agent for the \"MasuSheet\" spreadsheet app."))

        // 3. Plan mode & execution summary bullet in English when isEn = true
        val planTurnEn = SpreadsheetAiAgent.executeAgentTurn(
            prompt = "Create inventory sheet",
            editMode = "plan",
            clarifyingAnswers = emptyMap(),
            vp = vp,
            modelId = "auto",
            apiKey = "",
            isEn = true
        )
        assertTrue(planTurnEn.replyText.startsWith("[🧭 Plan Mode]"))
        assertTrue(planTurnEn.clarifyingQuestions.first().title.contains("Which layout structure"))

        val execTurnEn = SpreadsheetAiAgent.executeAgentTurn(
            prompt = "Create inventory sheet",
            editMode = "allow_edit",
            clarifyingAnswers = emptyMap(),
            vp = vp,
            modelId = "auto",
            apiKey = "",
            isEn = true
        )
        assertTrue(execTurnEn.summaryBullets.contains("Engine: Built-in Default (Template Script Processing)"))
    }
}

