package com.masusheet.app.storage

import com.masusheet.app.core.MasuSheetConfig
import com.masusheet.app.viewmodel.getAiCustomEndpoint

/**
 * ============================================================================
 * [EN] User Preferences & Hardware-Backed AI Secret Repository
 * Coordinates [MasuSheetPreferencesRepository] across [PreferenceStore] (UI settings, recent files, custom per-model IDs) and [SecretStore] (AES-256-GCM encrypted API keys).
 *
 * ---
 * [JA / 日本語解説]
 * UI設定・最近使ったファイル・AIモデル別カスタム設定 ([PreferenceStore]) と、Android Keystore AES-256-GCM で暗号化されたAPIキー ([SecretStore]) を一元管理するリポジトリです。
 * ============================================================================
 */

/**
 * [EN] Central repository persisting UI preferences in [PreferenceStore] and encrypting AI API keys in [SecretStore].
 * [JA] UI設定の保存 ([PreferenceStore]) とAI APIキーの暗号化保管 ([SecretStore]) を統合する設定リポジトリです。
 */
class MasuSheetPreferencesRepository(
    private val store: PreferenceStore,
    private val secretStore: SecretStore = InMemorySecretStore()
) {
    companion object {
        private const val KEY_LANGUAGE = "ms_language"
        private const val KEY_DARK_THEME = "ms_dark_theme_v14"
        private const val KEY_MATERIAL3_UI = "ms_material3_ui"
        private const val KEY_ZOOM_PERCENT = "ms_zoom_percent"
        private const val KEY_RECENT_FILES = "ms_recent_files"
        private const val KEY_FOLDER_SAVED_PREFIX = "ms_folder_files_"
        private const val KEY_RECENT_COLORS = "ms_recent_colors"
        private const val KEY_AI_MODEL = "ms_ai_model"
        private const val KEY_AI_EDIT_MODE = "ms_ai_edit_mode"
        private const val KEY_AI_API_KEY_PREFIX = "ms_ai_api_key_"
        private const val KEY_AI_CUSTOM_ENDPOINT = "ms_ai_custom_endpoint"
        private const val KEY_AI_CUSTOM_MODEL = "ms_ai_custom_model"
        private const val KEY_AI_CUSTOM_MODEL_PREFIX = "ms_ai_custom_model_"
        private const val RECENT_DELIMITER = "\n"
    }

    fun getLanguage(): String = store.getString(KEY_LANGUAGE, "ja")

    fun setLanguage(lang: String) {
        store.putString(KEY_LANGUAGE, lang.trim().lowercase().ifEmpty { "ja" })
    }

    fun isDarkTheme(): Boolean = store.getBoolean(KEY_DARK_THEME, false)

    fun setDarkTheme(dark: Boolean) {
        store.putBoolean(KEY_DARK_THEME, dark)
    }

    fun isMaterial3Ui(): Boolean = store.getBoolean(KEY_MATERIAL3_UI, true)

    fun setMaterial3Ui(enabled: Boolean) {
        store.putBoolean(KEY_MATERIAL3_UI, enabled)
    }

    fun getZoomPercent(): Int = store.getInt(KEY_ZOOM_PERCENT, 100)

    fun setZoomPercent(percent: Int) {
        store.putInt(KEY_ZOOM_PERCENT, percent.coerceIn(25, 400))
    }

    fun getRecentWorkbooks(): List<String> {
        val raw = store.getString(KEY_RECENT_FILES, "")
        if (raw.isBlank()) return emptyList()
        return raw.split(RECENT_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun recordRecentWorkbook(title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val updated = mutableListOf(clean)
        for (existing in getRecentWorkbooks()) {
            if (!existing.equals(clean, ignoreCase = false)) {
                updated.add(existing)
            }
            if (updated.size >= MasuSheetConfig.MAX_RECENT_FILES) break
        }
        store.putString(KEY_RECENT_FILES, updated.joinToString(RECENT_DELIMITER))
    }

    fun clearRecentWorkbooks() {
        store.putString(KEY_RECENT_FILES, "")
    }

    private fun normalizeFolderKey(folderPath: String): String =
        "$KEY_FOLDER_SAVED_PREFIX${folderPath.trim().trimEnd('/').lowercase()}"

    fun getSavedFilesInFolder(folderPath: String): List<String> {
        val raw = store.getString(normalizeFolderKey(folderPath), "")
        if (raw.isBlank()) return emptyList()
        return raw.split(RECENT_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun recordSavedFileInFolder(folderPath: String, fileName: String) {
        val cleanName = fileName.trim()
        if (cleanName.isEmpty() || folderPath.isBlank()) return
        val updated = mutableListOf(cleanName)
        for (existing in getSavedFilesInFolder(folderPath)) {
            if (!existing.equals(cleanName, ignoreCase = false)) {
                updated.add(existing)
            }
            if (updated.size >= 24) break
        }
        store.putString(normalizeFolderKey(folderPath), updated.joinToString(RECENT_DELIMITER))
    }

    fun getRecentColors(): List<Int> {
        val raw = store.getString(KEY_RECENT_COLORS, "")
        if (raw.isBlank()) {
            return listOf(
                0xFF1F9D55.toInt(),
                0xFF4472C4.toInt(),
                0xFFED7D31.toInt(),
                0xFFFFC000.toInt(),
                0xFF70AD47.toInt(),
                0xFF5B9BD5.toInt()
            )
        }
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun recordRecentColor(argb: Int) {
        if (argb == 0) return
        val opaque = argb or 0xFF000000.toInt()
        val updated = (listOf(opaque) + getRecentColors().filter { it != opaque }).take(10)
        store.putString(KEY_RECENT_COLORS, updated.joinToString(","))
    }

    fun getAiModelId(): String = store.getString(KEY_AI_MODEL, "auto")
    fun setAiModelId(modelId: String) = store.putString(KEY_AI_MODEL, modelId.ifBlank { "auto" })

    fun getAiEditMode(): String = store.getString(KEY_AI_EDIT_MODE, "allow_edit")
    fun setAiEditMode(mode: String) = store.putString(KEY_AI_EDIT_MODE, mode.ifBlank { "allow_edit" })

    fun getAiApiKey(modelId: String): String {
        val cleanId = modelId.trim().lowercase().ifEmpty { "auto" }
        val secretKeyName = "$KEY_AI_API_KEY_PREFIX$cleanId"
        val encrypted = secretStore.getSecret(secretKeyName)
        if (encrypted.isNotEmpty()) return encrypted
        // Migrate and purge any legacy plaintext preference entry if present
        val legacyPlain = store.getString(secretKeyName, "")
        if (legacyPlain.isNotBlank()) {
            secretStore.putSecret(secretKeyName, legacyPlain)
            store.remove(secretKeyName)
            return legacyPlain
        }
        return ""
    }

    fun setAiApiKey(modelId: String, key: String) {
        val cleanId = modelId.trim().lowercase().ifEmpty { "auto" }
        val secretKeyName = "$KEY_AI_API_KEY_PREFIX$cleanId"
        store.remove(secretKeyName)
        secretStore.putSecret(secretKeyName, key.trim())
    }

    fun getAiCustomEndpoint(): String {
        val raw = store.getString(KEY_AI_CUSTOM_ENDPOINT, "").trim()
        return if (raw == "http://127.0.0.1:11434/v1/chat/completions") "" else raw
    }
    fun setAiCustomEndpoint(url: String) = store.putString(KEY_AI_CUSTOM_ENDPOINT, url.trim())

    fun getAiCustomModel(modelId: String = getAiModelId()): String {
        val cleanId = modelId.trim().lowercase().ifEmpty { "auto" }
        val perProvider = store.getString("$KEY_AI_CUSTOM_MODEL_PREFIX$cleanId", "").trim()
        if (perProvider.isNotEmpty()) return perProvider
        return store.getString(KEY_AI_CUSTOM_MODEL, "").trim()
    }

    fun setAiCustomModel(model: String, modelId: String = getAiModelId()) {
        val cleanId = modelId.trim().lowercase().ifEmpty { "auto" }
        val cleanModel = model.trim()
        store.putString("$KEY_AI_CUSTOM_MODEL_PREFIX$cleanId", cleanModel)
    }
}

