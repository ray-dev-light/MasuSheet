package com.masusheet.app.storage

import com.masusheet.app.OpenWorkbookSession
import com.masusheet.app.WorkbookHandoffSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ============================================================================
 * Module: WorkbookXlsxObjectPersistence.kt
 * ============================================================================
 * [EN] Lossless ECMA-376 `.xlsx` Custom XML Persistence for Charts, Images, Shapes, & PivotTables
 *
 * Works alongside the Rust engine's native OOXML DrawingML/ChartML export (`xl/charts/chartN.xml`,
 * `xl/drawings/drawingN.xml`, `xl/media/imageN.png`):
 * - Standard spreadsheet apps (Microsoft Excel, Google Sheets, LibreOffice Calc) render the native
 *   OOXML charts, images, and shapes written by `jni.syncWorkbookObjects`.
 * - Reopening the `.xlsx` in MasuSheet additionally reads `customXml/masusheet_objects_v1.xml` to
 *   restore 100% of MasuSheet-specific chart subtypes (all 32 types), axis bounds/intervals,
 *   per-element typography colors, object rotation angles, PivotTable definitions, and overlays
 *   without leaking any local file paths (`sourceUri` is stripped prior to serialization).
 *
 * ---
 * [JA / 日本語解説]
 * `.xlsx` / `.xlsm` ファイル内にグラフ・画像・図形・ピボットテーブル等の全設定を欠落なく保存・復元するモジュールです。
 * - Rust エンジン側の `jni.syncWorkbookObjects` が出力する標準 OOXML (`xl/charts/chartN.xml` 等) により、
 *   Microsoft Excel や Google スプレッドシート、LibreOffice Calc でもグラフ・画像がそのまま表示されます。
 * - さらに ECMA-376 準拠の `customXml/masusheet_objects_v1.xml` パートへ詳細メタデータを格納することで、
 *   MasuSheet で再度開いた際に全32種のグラフ種別・軸範囲／目盛間隔・要素別文字色・回転角・ピボット定義を完全復元します
 *   （プライバシー保護のためローカル `sourceUri` は保存データから除外します）。
 * ============================================================================
 */
object WorkbookXlsxObjectPersistence {

    internal const val CUSTOM_XML_ENTRY_PATH = "customXml/masusheet_objects_v1.xml"
    private const val WORKBOOK_RELS_PATH = "xl/_rels/workbook.xml.rels"
    private const val CUSTOM_XML_REL_ID = "rIdMasuSheetOverlay1"
    private const val CUSTOM_XML_REL_TYPE =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/customXml"
    private const val CUSTOM_XML_REL_TARGET = "../customXml/masusheet_objects_v1.xml"

    private const val MAX_ZIP_ENTRIES = 8_192
    private const val MAX_OVERLAY_ENTRY_BYTES = 32 * 1024 * 1024 // 32 MiB safety cap

    /**
     * [EN] Returns `true` when [session] holds any floating objects, PivotTables, or custom overlays
     *      worth persisting into `customXml/masusheet_objects_v1.xml`.
     * [JA] 保存対象のフローティングオブジェクト（グラフ・画像・図形）やピボットテーブル等が存在するか判定します。
     */
    fun hasPersistableOverlay(session: OpenWorkbookSession): Boolean {
        return session.sheetObjects.isNotEmpty() ||
            session.pivotTables.isNotEmpty() ||
            session.sheetTabColors.isNotEmpty() ||
            session.customCellStyles.isNotEmpty() ||
            session.customSheetMerges.isNotEmpty() ||
            session.customColWidths.isNotEmpty() ||
            session.customRowHeights.isNotEmpty() ||
            session.customWrapCells.isNotEmpty()
    }

    /**
     * [EN] Embeds (or updates/removes) `customXml/masusheet_objects_v1.xml` inside [xlsxBytes].
     *      Returns the original [xlsxBytes] untouched if it is not a valid ZIP package.
     * [JA] `.xlsx` ZIP パッケージ内に `customXml/masusheet_objects_v1.xml` を安全に埋め込み（対象が空なら除去）ます。
     */
    fun embedIntoXlsxBytes(xlsxBytes: ByteArray, session: OpenWorkbookSession): ByteArray {
        if (xlsxBytes.size < 4 || xlsxBytes[0] != 'P'.code.toByte() || xlsxBytes[1] != 'K'.code.toByte()) {
            return xlsxBytes
        }
        val shouldInclude = hasPersistableOverlay(session)
        val sanitizedSession = session.copy(
            sourceUri = "",
            savedWorkbookBytes = null
        )
        val overlayXmlBytes: ByteArray? = if (shouldInclude) {
            val jsonStr = WorkbookHandoffSerializer.encodeOverlayMetadataJson(sanitizedSession)
            val b64Payload = Base64.getEncoder().encodeToString(jsonStr.toByteArray(Charsets.UTF_8))
            val xml = buildString(b64Payload.length + 128) {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                append("<masusheetOverlay xmlns=\"https://masusheet.app/schema/overlay/v1\" version=\"1\">")
                append(b64Payload)
                append("</masusheetOverlay>")
            }
            xml.toByteArray(Charsets.UTF_8)
        } else {
            null
        }

        return runCatching {
            val entries = LinkedHashMap<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
                var count = 0
                var entry = zis.nextEntry
                while (entry != null) {
                    count++
                    if (count > MAX_ZIP_ENTRIES) {
                        return xlsxBytes
                    }
                    if (!entry.isDirectory) {
                        val name = entry.name
                        if (!name.equals(CUSTOM_XML_ENTRY_PATH, ignoreCase = true)) {
                            entries[name] = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (entries.isEmpty()) return xlsxBytes

            val wbRelsKey = entries.keys.firstOrNull { it.equals(WORKBOOK_RELS_PATH, ignoreCase = true) }
            if (wbRelsKey != null) {
                val origRels = entries[wbRelsKey]!!.toString(Charsets.UTF_8)
                entries[wbRelsKey] = updateWorkbookRelsXml(origRels, includeRel = shouldInclude)
                    .toByteArray(Charsets.UTF_8)
            }

            if (overlayXmlBytes != null) {
                entries[CUSTOM_XML_ENTRY_PATH] = overlayXmlBytes
            }

            val out = ByteArrayOutputStream(xlsxBytes.size + (overlayXmlBytes?.size ?: 0) + 512)
            ZipOutputStream(out).use { zos ->
                for ((name, data) in entries) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(data)
                    zos.closeEntry()
                }
            }
            out.toByteArray()
        }.getOrDefault(xlsxBytes)
    }

    /**
     * [EN] Extracts MasuSheet's lossless overlay session (`sheetObjects`, `pivotTables`, etc.) from
     *      `customXml/masusheet_objects_v1.xml` inside [xlsxBytes], or returns `null` if absent.
     * [JA] `.xlsx` バイト列内の `customXml/masusheet_objects_v1.xml` からオブジェクト・ピボット情報を復元します。
     */
    fun extractFromXlsxBytes(xlsxBytes: ByteArray): OpenWorkbookSession? {
        if (xlsxBytes.size < 4 || xlsxBytes[0] != 'P'.code.toByte() || xlsxBytes[1] != 'K'.code.toByte()) {
            return null
        }
        return runCatching {
            var rawXmlBytes: ByteArray? = null
            ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
                var count = 0
                var entry = zis.nextEntry
                while (entry != null) {
                    count++
                    if (count > MAX_ZIP_ENTRIES) break
                    if (!entry.isDirectory && entry.name.equals(CUSTOM_XML_ENTRY_PATH, ignoreCase = true)) {
                        val buf = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val n = zis.read(chunk)
                            if (n <= 0) break
                            total += n
                            if (total > MAX_OVERLAY_ENTRY_BYTES) return null
                            buf.write(chunk, 0, n)
                        }
                        rawXmlBytes = buf.toByteArray()
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val xmlStr = rawXmlBytes?.toString(Charsets.UTF_8) ?: return null
            val openTagEnd = xmlStr.indexOf('>', xmlStr.indexOf("<masusheetOverlay").takeIf { it >= 0 } ?: return null)
            val closeTagStart = xmlStr.lastIndexOf("</masusheetOverlay>")
            if (openTagEnd < 0 || closeTagStart <= openTagEnd) return null
            val b64Body = xmlStr.substring(openTagEnd + 1, closeTagStart).trim()
            if (b64Body.isEmpty()) return null
            val jsonBytes = Base64.getMimeDecoder().decode(b64Body)
            val jsonStr = jsonBytes.toString(Charsets.UTF_8)
            WorkbookHandoffSerializer.decodeOverlayMetadataJson(jsonStr, xlsxBytes)
        }.getOrNull()
    }

    private fun updateWorkbookRelsXml(relsXml: String, includeRel: Boolean): String {
        val cleaned = relsXml.replace(
            Regex("""<Relationship\b[^>]*Id="$CUSTOM_XML_REL_ID"[^>]*/>"""),
            ""
        )
        if (!includeRel) return cleaned
        if (cleaned.contains(CUSTOM_XML_ENTRY_PATH, ignoreCase = true)) return cleaned
        val closeIdx = cleaned.lastIndexOf("</Relationships>")
        if (closeIdx < 0) return cleaned
        val relTag =
            """<Relationship Id="$CUSTOM_XML_REL_ID" Type="$CUSTOM_XML_REL_TYPE" Target="$CUSTOM_XML_REL_TARGET"/>"""
        return cleaned.substring(0, closeIdx) + relTag + cleaned.substring(closeIdx)
    }
}
