package com.masusheet.app

import android.app.Application
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masusheet.app.ai.MacroDefinition
import com.masusheet.app.ai.MacroStep
import com.masusheet.app.ai.MacroStudioEngine
import com.masusheet.app.bridge.InMemoryFallbackEngineGateway
import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.bridge.RustJniEngineGateway
import com.masusheet.app.canvas.computeAdjustedCellBgForTheme
import com.masusheet.app.canvas.resolveCellTextColorForTheme
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetPreferencesRepository
import com.masusheet.app.viewmodel.MasuSheetViewModel
import com.masusheet.app.viewmodel.detectTableBoundsAt
import com.masusheet.app.viewmodel.dispatchCommand
import com.masusheet.app.viewmodel.getActivePivotTable
import com.masusheet.app.viewmodel.onAssignPivotFieldToArea
import com.masusheet.app.viewmodel.onCreateNewBlankWorkbookSession
import com.masusheet.app.viewmodel.onCreatePivotTable
import com.masusheet.app.viewmodel.onExecuteConfirmedAiPlan
import com.masusheet.app.viewmodel.onRunMacro
import com.masusheet.app.viewmodel.onSelectAiEditMode
import com.masusheet.app.viewmodel.onSendAiPrompt
import com.masusheet.app.viewmodel.onSwitchOpenWorkbook
import com.masusheet.app.viewmodel.onToggleLanguage
import com.masusheet.app.viewmodel.onUndoLastAiEdit
import kotlin.math.roundToInt
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

/**
 * ============================================================================
 * [EN] End-to-End Regression Suite for Dark Mode Cell Contrast, PivotTables, AI Agent & Macro Studio
 * Verifies theme-adaptive contrast, atomic Pivot & AI Undo/Redo, cross-sheet presets, 3+ level hierarchy,
 * Plan Mode table preservation, tax/rank/WBS formulas, and bidirectional VBA macro generation.
 *
 * ---
 * [JA / 日本語解説]
 * ダークモードのセル文字視認性、ピボットテーブル全機能（一括Undo/Redo・3階層・総計モード・別シートプリセット・数式%）、
 * AIアシスタント（100ステップ超の一括Undo・プランモード保護・税込/WBS数式）、およびマクロ／VBA双方向変換の総合検証テストです。
 * ============================================================================
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MasuSheetPivotAiAndDarkContrastAuditTest {

    private fun newVm(blank: Boolean = true): MasuSheetViewModel {
        val repo = MasuSheetPreferencesRepository(FakePreferenceStore(), InMemorySecretStore())
        val gw = if (MasuSheetNativeBridge.isNativeLoaded) {
            RustJniEngineGateway()
        } else {
            InMemoryFallbackEngineGateway()
        }
        return MasuSheetViewModel(
            preferencesRepository = repo,
            documentGateway = FakeDocumentIoGateway(),
            engineGateway = gw,
            startWithBlankWorkbook = blank
        )
    }

    private fun luminance(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return ((r * 299 + g * 587 + b * 114) / 1000.0).roundToInt()
    }

    @Test
    fun darkModeCellContrast_lightNeutralPastelAndVividConditionalCellsRemainHighContrast() {
        val darkSheetBg = Color.parseColor("#11171D")
        val defaultLightOnDark = Color.parseColor("#F1F5F9")

        // 1. KPI bar (#F1F5F9 bg + #0F172A explicit dark text) -> dark surface + bright readable text in Dark Mode
        val rawKpiBg = Color.parseColor("#F1F5F9")
        val kpiBg = computeAdjustedCellBgForTheme(rawKpiBg, darkTheme = true)
        val kpiFg = resolveCellTextColorForTheme(Color.parseColor("#0F172A"), rawKpiBg, darkTheme = true, sheetBg = darkSheetBg, defaultTextCol = defaultLightOnDark)
        assertTrue("KPI dark surface should have low luminance, got ${luminance(kpiBg)}", luminance(kpiBg) < 60)
        assertTrue("KPI text on dark surface must lift to bright luminance, got ${luminance(kpiFg)}", luminance(kpiFg) > 200)

        // 2. Pivot zebra rows (#FFFFFF vs #D9E1F2) -> distinct dark surfaces + bright readable text
        val rawWhite = Color.parseColor("#FFFFFF")
        val rawBlueBand = Color.parseColor("#D9E1F2")
        val whiteRowBg = computeAdjustedCellBgForTheme(rawWhite, darkTheme = true)
        val blueBandBg = computeAdjustedCellBgForTheme(rawBlueBand, darkTheme = true)
        assertNotEquals("White and banded Pivot rows must remain visually distinct in Dark Mode", whiteRowBg, blueBandBg)
        assertTrue(luminance(blueBandBg) < 85)
        val blueBandFg = resolveCellTextColorForTheme(0, rawBlueBand, darkTheme = true, sheetBg = darkSheetBg, defaultTextCol = defaultLightOnDark)
        assertTrue("Default text on darkened Pivot band must stay bright", luminance(blueBandFg) > 200)

        // 3. Vivid yellow ColorScale cell (#FFEB84 + fgColor = 0) -> keeps vivid yellow bg + switches text to crisp dark!
        val rawYellow = Color.parseColor("#FFEB84")
        val yellowBg = computeAdjustedCellBgForTheme(rawYellow, darkTheme = true)
        val yellowFg = resolveCellTextColorForTheme(0, rawYellow, darkTheme = true, sheetBg = darkSheetBg, defaultTextCol = defaultLightOnDark)
        assertTrue("Vivid yellow conditional format fill should stay bright", luminance(yellowBg) > 180)
        assertTrue("Default text on bright yellow fill must switch to dark ink, got ${luminance(yellowFg)}", luminance(yellowFg) < 50)

        // 4. Pastel emerald total row (#CCFBF1 + dark teal #115E59 text) -> dark tinted surface + brightened teal text
        val rawTealBg = Color.parseColor("#CCFBF1")
        val tealBg = computeAdjustedCellBgForTheme(rawTealBg, darkTheme = true)
        val tealFg = resolveCellTextColorForTheme(Color.parseColor("#115E59"), rawTealBg, darkTheme = true, sheetBg = darkSheetBg, defaultTextCol = defaultLightOnDark)
        assertTrue(luminance(tealBg) < 80)
        assertTrue("Dark semantic teal text must brighten on dark surface, got ${luminance(tealFg)}", luminance(tealFg) > 130)
    }

    @Test
    fun pivotTable_atomicUndoRedo_grandTotalsModes_threeRowFields_andFormulaPercentage() {
        val vm = newVm(blank = true)
        // Build 3-level hierarchy source table with a formula percentage column
        val headers = listOf("地域", "部門", "商品", "売上金額", "達成率")
        headers.forEachIndexed { c, h -> vm.onCommitCellEdit(0, c, h) }
        val rows = listOf(
            listOf("東日本", "法人", "PC", "250", "=D2/1000"),
            listOf("東日本", "個人", "マウス", "150", "=D3/1000"),
            listOf("西日本", "法人", "モニター", "400", "=D4/1000")
        )
        rows.forEachIndexed { idx, rVals ->
            val r = idx + 1
            rVals.forEachIndexed { c, v -> vm.onCommitCellEdit(r, c, v) }
        }
        vm.onSelectCell(1, 4, 3, 4)
        vm.dispatchCommand("format.numberFormat", """{"range":"E2:E4","format":"0.0%"}""")

        // Verify formula % cell renders as 25.0%
        assertEquals("25.0%", vm.uiState.value.viewport.cellAt(1, 4)?.displayText)

        vm.onSelectCell(0, 0)
        val pivot = vm.onCreatePivotTable(placeOnNewSheet = false)
        assertNotNull(pivot)
        assertEquals(1, vm.uiState.value.pivotTables.size)
        val pStartR = pivot!!.targetStartRow
        val pStartC = pivot.targetStartCol
        assertTrue(vm.uiState.value.viewport.cellAt(pStartR, pStartC)?.displayText?.isNotBlank() == true)

        // 1. Atomic 1-click Undo removes ENTIRE PivotTable + rendered cells, and Redo restores them!
        vm.dispatchCommand("edit.undo", "{}")
        assertTrue("1-click Undo must restore empty pivotTables list", vm.uiState.value.pivotTables.isEmpty())
        assertEquals("", vm.uiState.value.viewport.cellAt(pStartR, pStartC)?.displayText.orEmpty())

        vm.dispatchCommand("edit.redo", "{}")
        assertEquals(1, vm.uiState.value.pivotTables.size)
        assertTrue(vm.uiState.value.viewport.cellAt(pStartR, pStartC)?.displayText?.isNotBlank() == true)

        // 2. Formula % column in Pivot must sum to 80.0% (0.25 + 0.15 + 0.40 = 0.80), NOT 8000.0%!
        vm.onAssignPivotFieldToArea("達成率", "value")
        val updatedPivot = vm.getActivePivotTable()!!
        val gtRow = updatedPivot.lastRenderedEndRow
        val pctCol = updatedPivot.lastRenderedEndCol
        assertEquals("80.0%", vm.uiState.value.viewport.cellAt(gtRow, pctCol)?.displayText)

        // 3. 3+ Row Fields ("地域", "部門", "商品") render all 3 hierarchy levels in both Outline and Compact layouts!
        vm.onAssignPivotFieldToArea("部門", "row")
        vm.onAssignPivotFieldToArea("商品", "row")
        val p3 = vm.getActivePivotTable()!!
        assertEquals(listOf("地域", "部門", "商品"), p3.rowFields)
        val outlineTexts = (p3.lastRenderedStartRow..p3.lastRenderedEndRow).flatMap { r ->
            (p3.targetStartCol..(p3.targetStartCol + 2)).map { c ->
                vm.uiState.value.viewport.cellAt(r, c)?.displayText.orEmpty()
            }
        }
        assertTrue("3rd-level item 'PC' must appear in Outline 3-level hierarchy: $outlineTexts", outlineTexts.any { it.contains("PC") })

        vm.dispatchCommand("pivotDesign.reportLayout", """{"layout":"compact"}""")
        val p3Compact = vm.getActivePivotTable()!!
        val compactColTexts = (p3Compact.lastRenderedStartRow..p3Compact.lastRenderedEndRow).map { r ->
            vm.uiState.value.viewport.cellAt(r, p3Compact.targetStartCol)?.displayText.orEmpty()
        }
        assertTrue("3rd-level item 'PC' must appear in single-column Compact 3-level hierarchy: $compactColTexts", compactColTexts.any { it.contains("PC") })

        // Switch to Tabular layout with 3 row fields -> headers occupy 3 separate columns
        vm.dispatchCommand("pivotDesign.reportLayout", """{"layout":"tabular"}""")
        val p3Tab = vm.getActivePivotTable()!!
        assertEquals("地域", vm.uiState.value.viewport.cellAt(p3Tab.targetStartRow, p3Tab.targetStartCol)?.displayText)
        assertEquals("部門", vm.uiState.value.viewport.cellAt(p3Tab.targetStartRow, p3Tab.targetStartCol + 1)?.displayText)
        assertEquals("商品", vm.uiState.value.viewport.cellAt(p3Tab.targetStartRow, p3Tab.targetStartCol + 2)?.displayText)

        // 4. GrandTotalsMode: "rows" vs "columns"
        vm.onAssignPivotFieldToArea("部門", "column")
        vm.dispatchCommand("pivotDesign.grandTotals", """{"mode":"columns"}""")
        val pColsOnly = vm.getActivePivotTable()!!
        assertEquals("総計", vm.uiState.value.viewport.cellAt(pColsOnly.lastRenderedEndRow, pColsOnly.targetStartCol)?.displayText)
        assertNotEquals("総計", vm.uiState.value.viewport.cellAt(pColsOnly.targetStartRow, pColsOnly.lastRenderedEndCol)?.displayText)

        vm.dispatchCommand("pivotDesign.grandTotals", """{"mode":"rows"}""")
        val pRowsOnly = vm.getActivePivotTable()!!
        assertNotEquals("総計", vm.uiState.value.viewport.cellAt(pRowsOnly.lastRenderedEndRow, pRowsOnly.targetStartCol)?.displayText)
        assertTrue(vm.uiState.value.viewport.cellAt(pRowsOnly.targetStartRow, pRowsOnly.lastRenderedEndCol)?.displayText?.contains("総計") == true)
    }

    @Test
    fun pivotTable_filterNeverOverwritesCellsAboveTarget_andNewSheetPresetPreservesSourceSheet() {
        val vm = newVm(blank = true)
        vm.onSelectAiEditMode("allow_edit")
        vm.onSendAiPrompt("在庫・発注管理シートを作成して")

        // Put an important user note at I6 (row=5, col=8), right above where same-sheet Pivot starts at row=6 (I7)
        vm.onCommitCellEdit(5, 8, "上部の重要メモ")
        vm.onSelectCell(7, 0) // A8 inside main inventory table
        val sameSheetPivot = vm.onCreatePivotTable(placeOnNewSheet = false)
        assertNotNull(sameSheetPivot)
        assertEquals(6, sameSheetPivot!!.targetStartRow)

        // Add report filter -> must push Pivot headers down instead of overwriting row 5 ("上部の重要メモ")!
        vm.onAssignPivotFieldToArea("発注判定", "filter")
        assertEquals("上部の重要メモ", vm.uiState.value.viewport.cellAt(5, 8)?.displayText)
        assertEquals("🔎 発注判定", vm.uiState.value.viewport.cellAt(6, 8)?.displayText)

        // Language toggle re-materializes Pivot labels in English ("Grand Total") and back to Japanese ("全体集計" / "総計")
        vm.onToggleLanguage()
        val enPivot = vm.getActivePivotTable()!!
        assertEquals("Grand Total", vm.uiState.value.viewport.cellAt(enPivot.lastRenderedEndRow, enPivot.targetStartCol)?.displayText)
        vm.onToggleLanguage()
        val jaPivot = vm.getActivePivotTable()!!
        assertEquals("全体集計", vm.uiState.value.viewport.cellAt(jaPivot.lastRenderedEndRow, jaPivot.targetStartCol)?.displayText)
        vm.onAssignPivotFieldToArea("商品名", "row")
        val jaRowPivot = vm.getActivePivotTable()!!
        assertEquals("総計", vm.uiState.value.viewport.cellAt(jaRowPivot.lastRenderedEndRow, jaRowPivot.targetStartCol)?.displayText)

        // Create a New-Sheet PivotTable (Sheet2 sourcing Sheet1), then click Quick Preset ("hierarchy")!
        vm.onSelectCell(7, 0)
        val newSheetPivot = vm.onCreatePivotTable(placeOnNewSheet = true)
        assertNotNull(newSheetPivot)
        assertEquals("Source must be Sheet 0", 0, newSheetPivot!!.sourceSheetIndex)
        assertNotEquals("Target must be new sheet", 0, newSheetPivot.targetSheetIndex)

        val afterPreset = vm.onCreatePivotTable(placeOnNewSheet = false, presetKind = "hierarchy")
        assertNotNull(afterPreset)
        assertEquals("Quick preset on New-Sheet Pivot must preserve sourceSheetIndex = 0", 0, afterPreset!!.sourceSheetIndex)
        assertEquals(6, afterPreset.sourceRecords.size)
        assertEquals("¥1,302,700", vm.uiState.value.viewport.cellAt(afterPreset.lastRenderedEndRow, afterPreset.lastRenderedEndCol)?.displayText)
    }

    @Test
    fun pivotTable_detectTableBoundsSkipsKpiRowAndPivotCells_andPivotChartExcludesSubtotals() {
        val vm = newVm(blank = true)
        vm.onSelectAiEditMode("allow_edit")
        vm.onSendAiPrompt("在庫・発注管理シートを作成して")

        // Even when active cell is on Row 4 KPI card (B4 -> row=3, col=1), detectTableBoundsAt(excludePivotFootprints=true) finds A7:G13!
        val detectedFromKpi = vm.detectTableBoundsAt(vm.uiState.value.viewport, 3, 1, excludePivotFootprints = true)
        assertEquals("Must skip 1-row KPI card and find header at row 6 (A7)", 6, detectedFromKpi[0])
        assertEquals("Must peel off Row 14 ('合計 / 集計') and end at row 12 (G13)", 12, detectedFromKpi[2])

        vm.onSelectCell(7, 0)
        vm.onCreatePivotTable(placeOnNewSheet = false, presetKind = "hierarchy")

        // PivotChart command inserts a PivotChart anchored to clean Pivot table bounds AND opens live subtype picker
        val objCountBefore = vm.uiState.value.sheetObjects.size
        vm.dispatchCommand("pivotAnalyze.pivotChart", "{}")
        assertEquals(objCountBefore + 1, vm.uiState.value.sheetObjects.size)
        assertTrue(vm.uiState.value.isPivotChartPickerOpen)

        // PivotChart excludes "集計" / "総計" subtotal rows from categories
        val chartObj = vm.uiState.value.sheetObjects.last()
        val cats = chartObj.chartLabels
        assertTrue(cats.isNotEmpty())
        assertFalse("Chart categories must exclude Pivot '集計' subtotal rows: $cats", cats.any { it.endsWith("集計") || it == "総計" })
    }

    @Test
    fun aiAgent_atomicOneClickUndoFor100PlusSteps_preservesPriorEditsAndGuardsCrossWorkbook() {
        val vm = newVm(blank = true)
        vm.onCommitCellEdit(0, 12, "事前メモM1")
        assertEquals("事前メモM1", vm.uiState.value.viewport.cellAt(0, 12)?.displayText)

        vm.onSelectAiEditMode("allow_edit")
        vm.onSendAiPrompt("在庫・発注管理シートを作成して")
        assertTrue("Inventory sheet applies > 90 commands", vm.uiState.value.lastAiUndoSteps >= 90)
        assertEquals("在庫・発注管理ワークシート", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)

        // Switch to Workbook 2 -> onUndoLastAiEdit must NOT mutate Workbook 2!
        val wb1Id = vm.uiState.value.activeWorkbookId
        vm.onCreateNewBlankWorkbookSession()
        vm.onCommitCellEdit(0, 0, "Book2Cell")
        vm.onUndoLastAiEdit()
        assertEquals("Book2Cell", vm.uiState.value.viewport.cellAt(0, 0)?.displayText)

        // Switch back to Workbook 1 -> 1-click onUndoLastAiEdit reverts ALL 100+ AI ops while keeping M1!
        assertTrue(vm.onSwitchOpenWorkbook(wb1Id))
        vm.onUndoLastAiEdit()
        assertEquals("Title A1 must be completely cleared", "", vm.uiState.value.viewport.cellAt(0, 0)?.displayText.orEmpty())
        assertEquals("KPI A4 must be completely cleared", "", vm.uiState.value.viewport.cellAt(3, 0)?.displayText.orEmpty())
        assertEquals("Table header A7 must be completely cleared", "", vm.uiState.value.viewport.cellAt(6, 0)?.displayText.orEmpty())
        assertEquals("Total row E14 must be completely cleared", "", vm.uiState.value.viewport.cellAt(13, 4)?.displayText.orEmpty())
        assertEquals("Pre-AI user cell M1 must remain intact!", "事前メモM1", vm.uiState.value.viewport.cellAt(0, 12)?.displayText)
    }

    @Test
    fun aiAgent_planModeOnExistingTableDoesNotWipeData_andSupportsTaxRankWbsRatesAndChartTypes() {
        val vm = newVm(blank = true)
        vm.onCommitCellEdit(0, 0, "品目")
        vm.onCommitCellEdit(0, 1, "金額")
        vm.onCommitCellEdit(1, 0, "ノートPC")
        vm.onCommitCellEdit(1, 1, "120000")
        vm.onCommitCellEdit(2, 0, "マウス")
        vm.onCommitCellEdit(2, 1, "4000")
        vm.onCommitCellEdit(3, 0, "キーボード")
        vm.onCommitCellEdit(3, 1, "16000")

        // 1. Plan Mode on existing table -> confirm plan -> MUST NOT wipe A1:B4, and MUST add 消費税(10%) & 税込金額 & 順位!
        vm.onSelectAiEditMode("plan")
        vm.onSendAiPrompt("消費税と税込金額と順位を追加して")
        assertNotNull("Plan session should be staged before mutating sheet", vm.uiState.value.activeAiPlanSession)
        assertNull("No columns added yet before confirmation", vm.uiState.value.viewport.cellAt(0, 2)?.displayText?.takeIf { it.isNotBlank() })

        vm.onExecuteConfirmedAiPlan()
        val vp = vm.uiState.value.viewport
        assertEquals("ノートPC", vp.cellAt(1, 0)?.displayText)
        assertEquals("消費税(10%)", vp.cellAt(0, 2)?.displayText)
        assertEquals("税込金額", vp.cellAt(0, 3)?.displayText)
        assertEquals("順位", vp.cellAt(0, 4)?.displayText)
        assertEquals("¥12,000", vp.cellAt(1, 2)?.displayText)
        assertEquals("¥132,000", vp.cellAt(1, 3)?.displayText)
        assertEquals("1", vp.cellAt(1, 4)?.displayText)

        // 2. "売上(円)を棒グラフにして" must NOT misclassify "円" (Yen currency) as a Pie chart!
        vm.onSelectAiEditMode("allow_edit")
        vm.onSendAiPrompt("売上(円)を棒グラフにして")
        val barChart = vm.uiState.value.sheetObjects.last()
        assertEquals("column", barChart.subType)

        vm.onSendAiPrompt("ドーナツグラフにして")
        assertEquals("doughnut", vm.uiState.value.sheetObjects.last().subType)

        // 3. WBS template total-row progress rate divides total-row sums (=C14/B14 = 156/216 = 72.2%), NOT last row (=4/16 = 25.0%)!
        val wbsVm = newVm(blank = true)
        wbsVm.onSelectAiEditMode("allow_edit")
        wbsVm.onSendAiPrompt("プロジェクト WBS 進捗管理シートを作成して")
        val wbsTotalRateFormula = wbsVm.uiState.value.viewport.cellAt(13, 4)?.editText.orEmpty()
        assertEquals("=C14/B14", wbsTotalRateFormula)
        assertEquals("72.2%", wbsVm.uiState.value.viewport.cellAt(13, 4)?.displayText)
    }

    @Test
    fun macroStudio_homeCommandVbaRoundTrip_sheetRename_andCompoundTaxTotalsNlMacro() {
        // 1. Recorded home.* / data.* / sheet.rename steps transpile to real VBA (zero Application.Run fallbacks)
        val steps = listOf(
            MacroStep("selection.set", """{"active":"B2","range":"B2:D4"}"""),
            MacroStep("home.bold", """{"range":"B2:D4","on":true}"""),
            MacroStep("home.fillColor", """{"range":"B2:D4","color":"#1F4E79"}"""),
            MacroStep("home.numberFormat", """{"range":"B2:D4","code":"¥#,##0"}"""),
            MacroStep("edit.clearFormats", """{"range":"E2:E4"}"""),
            MacroStep("data.sortDescending", """{"range":"A1:D4"}"""),
            MacroStep("sheet.rename", """{"sheet":0,"name":"売上集計"}""")
        )
        val vba = MacroStudioEngine.stepsToVbaCode("RecordedTest", "テスト", "Ctrl+Shift+R", steps)
        assertFalse("All standard Ribbon commands should transpile to native VBA without Application.Run: $vba", vba.contains("Application.Run"))
        assertTrue(vba.contains("""Range("B2:D4").Font.Bold = True"""))
        assertTrue(vba.contains("""Range("B2:D4").NumberFormatLocal = "¥#,##0""""))
        assertTrue(vba.contains("""ActiveSheet.Name = "売上集計""""))

        // 2. ActiveSheet.Name + compound Tax + Totals NL macro on exact B2:C3 selection
        val vm = newVm(blank = true)
        vm.onCommitCellEdit(0, 0, "品名")
        vm.onCommitCellEdit(0, 1, "Q1")
        vm.onCommitCellEdit(0, 2, "Q2")
        vm.onCommitCellEdit(1, 0, "A")
        vm.onCommitCellEdit(1, 1, "100")
        vm.onCommitCellEdit(1, 2, "200")
        vm.onCommitCellEdit(2, 0, "B")
        vm.onCommitCellEdit(2, 1, "300")
        vm.onCommitCellEdit(2, 2, "400")
        vm.onSelectCell(1, 1, 2, 2) // Exact B2:C3 selection

        val nlVba = MacroStudioEngine.generateVbaFromNaturalLanguage("選択範囲を税込にして合計行と合計列を追加", vm.uiState.value.viewport)
        assertTrue("Must respect exact row bounds 2..3 without expanding to 5: $nlVba", nlVba.contains("For r = 2 To 3"))
        assertTrue("Must respect exact col bounds 2..3 without expanding to 4: $nlVba", nlVba.contains("For c = 2 To 3"))
        assertTrue(nlVba.contains(".MultiplyBy = 1.1"))
        assertTrue(nlVba.contains("=SUM("))

        vm.onRunMacro(MacroDefinition(name = "TaxAndSum", vbaCode = nlVba + "\nActiveSheet.Name = \"税込集計\""))
        val vp = vm.uiState.value.viewport
        assertEquals("税込集計", vp.sheetName)
        assertEquals("110", vp.cellAt(1, 1)?.displayText)
        assertEquals("220", vp.cellAt(1, 2)?.displayText)
        assertEquals("330", vp.cellAt(1, 3)?.displayText)
        assertEquals("1100", vp.cellAt(3, 3)?.displayText)
    }
}
