package com.masusheet.app.storage

import android.content.Context
import com.masusheet.app.OpenWorkbookSession
import com.masusheet.app.WorkbookHandoffSerializer
import java.io.File
import java.util.UUID

/**
 * ============================================================================
 * Module: WindowHandoffManager.kt
 * ============================================================================
 * [EN] Capability-Token Multi-Window Workbook Handoff Store (`C-02` & `C-04` Fix)
 *
 * Replaces raw Intent file paths with strict UUID capability tokens scoped exclusively to
 * `cacheDir/handoff/`. Prevents Confused Deputy arbitrary file read/delete attacks while
 * preserving full workbook bytes AND overlay metadata (charts, images, shapes, PivotTables,
 * custom cell styles, row heights, column widths, and tab colors) across `:window2` processes.
 *
 * ---
 * [JA / 日本語解説]
 * マルチウィンドウ（`:window2` プロセス）間のブック移動・統合を安全に行うハンドオフ管理モジュールです（`C-02`・`C-04` 対応）。
 * Intent に生のファイルパスを渡す方式を廃止し、`cacheDir/handoff/` 配下に限定された厳格な UUID トークンのみを受け渡すことで、
 * 外部アプリからの任意ファイル読取・削除（Confused Deputy 脆弱性）を完全に遮断しつつ、
 * グラフ・画像・図形・ピボットテーブル・カスタム書式を含む全状態を欠落なく引き継ぎます。
 * ============================================================================
 */
object WindowHandoffManager {

    private const val HANDOFF_DIR_NAME = "handoff"
    private const val STALE_MAX_AGE_MS = 30L * 60L * 1000L // 30 minutes

    private val STRICT_UUID_REGEX = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    /**
     * [EN] Checks whether [token] is a syntactically valid canonical UUID (rejects paths, slashes, and dots).
     * [JA] トークンが厳密な UUID 形式であるか検証します（スラッシュや `..` を含むパス文字列を即時拒否します）。
     */
    fun isValidToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return STRICT_UUID_REGEX.matches(token)
    }

    /**
     * [EN] Serializes [session] (both `.xlsx` engine bytes and overlay metadata JSON) into `cacheDir/handoff/`
     *      and returns a one-time UUID token.
     * [JA] [session] のワークブックバイナリとオーバーレイJSONを `cacheDir/handoff/` に一時保存し、ワンタイムUUIDトークンを返します。
     */
    fun writeHandoffSession(context: Context, session: OpenWorkbookSession): String? {
        return runCatching {
            val dir = File(context.cacheDir, HANDOFF_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            pruneStaleEntries(dir)

            val token = UUID.randomUUID().toString()
            val canonicalDir = dir.canonicalFile

            val jsonFile = File(canonicalDir, "$token.json")
            if (jsonFile.canonicalFile.parentFile != canonicalDir) return null
            val metadataJson = WorkbookHandoffSerializer.encodeOverlayMetadataJson(session)
            jsonFile.writeText(metadataJson, Charsets.UTF_8)

            val wbBytes = session.savedWorkbookBytes
            if (wbBytes != null && wbBytes.isNotEmpty()) {
                val binFile = File(canonicalDir, "$token.bin")
                if (binFile.canonicalFile.parentFile == canonicalDir) {
                    binFile.writeBytes(wbBytes)
                }
            }
            token
        }.getOrNull()
    }

    /**
     * [EN] Validates [token], reads and deletes the one-time handoff bundle from `cacheDir/handoff/`,
     *      or returns `null` if [token] is invalid, forged, or already consumed.
     * [JA] [token] を検証し、`cacheDir/handoff/` からハンドオフデータを読み取って一時ファイルを削除します。
     *      不正なトークンや消費済みトークンの場合は何も削除せず `null` を返します。
     */
    fun consumeHandoffSession(
        context: Context,
        token: String?
    ): Pair<OpenWorkbookSession, ByteArray?>? {
        if (!isValidToken(token)) return null
        val safeToken = token ?: return null
        return runCatching {
            val dir = File(context.cacheDir, HANDOFF_DIR_NAME)
            if (!dir.exists()) return null
            val canonicalDir = dir.canonicalFile

            val jsonFile = File(canonicalDir, "$safeToken.json")
            val binFile = File(canonicalDir, "$safeToken.bin")
            if (jsonFile.canonicalFile.parentFile != canonicalDir) return null
            if (binFile.canonicalFile.parentFile != canonicalDir) return null
            if (!jsonFile.exists() || !jsonFile.isFile) return null

            val jsonText = jsonFile.readText(Charsets.UTF_8)
            val bytes = if (binFile.exists() && binFile.isFile) binFile.readBytes() else null

            runCatching { jsonFile.delete() }
            runCatching { if (binFile.exists()) binFile.delete() }

            val decoded = WorkbookHandoffSerializer.decodeOverlayMetadataJson(jsonText, bytes)
                ?: return null
            decoded to bytes
        }.getOrNull()
    }

    private fun pruneStaleEntries(dir: File) {
        val cutoff = System.currentTimeMillis() - STALE_MAX_AGE_MS
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < cutoff) {
                runCatching { f.delete() }
            }
        }
    }
}
