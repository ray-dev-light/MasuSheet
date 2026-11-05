package com.masusheet.app.storage

import android.content.Context

/**
 * ============================================================================
 * [EN] Key-Value Preference Store Abstraction & Android SharedPreferences Implementation
 * Provides [PreferenceStore], [SharedPreferencesStore], and [SharedPreferencesStoreFactory] for testable non-secret app preference persistence.
 *
 * ---
 * [JA / 日本語解説]
 * 非機密アプリ設定（テーマ・表示倍率・最近使ったファイル等）をテスト可能な形で永続化する [PreferenceStore] 抽象インターフェースおよび `SharedPreferences` 実装です。
 * ============================================================================
 */

/**
 * [EN] Minimal key-value interface decoupling preference persistence from Android framework classes in unit tests.
 * [JA] 単体テストでAndroidフレームワーク非依存に差し替え可能なキー・バリュー設定ストアのインターフェースです。
 */
interface PreferenceStore {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
    fun remove(key: String) {}
}

/**
 * [EN] Production [PreferenceStore] backed by Android `SharedPreferences`.
 * [JA] Android標準の `SharedPreferences` を用いた本番用 [PreferenceStore] 実装です。
 */
class SharedPreferencesStore(
    private val context: Context,
    private val prefsName: String
) : PreferenceStore {
    private val appContext = context.applicationContext ?: context
    private val prefs get() = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        prefs.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/**
 * [EN] Factory creating [SharedPreferencesStore] with the default `masusheet_prefs` namespace.
 * [JA] 既定の `masusheet_prefs` 名前空間で [SharedPreferencesStore] を生成するファクトリです。
 */
object SharedPreferencesStoreFactory {
    fun create(context: Context, prefsName: String = "masusheet_prefs"): PreferenceStore =
        SharedPreferencesStore(context, prefsName)
}
