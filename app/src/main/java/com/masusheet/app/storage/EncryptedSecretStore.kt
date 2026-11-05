package com.masusheet.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [EN] Abstraction for storing sensitive credentials (such as user-entered Cloud AI API keys) encrypted at rest.
 *
 * [JA] クラウド AI の API キーなどの機密情報を暗号化して保管するためのインターフェースです。
 */
interface SecretStore {
    fun getSecret(key: String): String
    fun putSecret(key: String, plainText: String)
    fun clearSecret(key: String)
}

/**
 * [EN] In-memory [SecretStore] implementation used in JVM/Robolectric unit tests where hardware Android Keystore is absent.
 *
 * [JA] ハードウェア Android Keystore が利用できない単体テスト環境向けのインメモリ [SecretStore] 実装です。
 */
class InMemorySecretStore : SecretStore {
    private val map = ConcurrentHashMap<String, String>()

    override fun getSecret(key: String): String = map[key].orEmpty()

    override fun putSecret(key: String, plainText: String) {
        if (plainText.isBlank()) {
            map.remove(key)
        } else {
            map[key] = plainText
        }
    }

    override fun clearSecret(key: String) {
        map.remove(key)
    }
}

/**
 * [EN] Hardware-backed Android Keystore `AES/GCM/NoPadding` encrypted secret store (`masusheet_secure_secrets.xml`).
 * Excluded from Android Auto Backup and device-to-device transfer via `backup_rules.xml` and `data_extraction_rules.xml`.
 * Falls back safely to ephemeral in-memory storage if Keystore initialization fails (e.g. under unit test runners),
 * never persisting plaintext secrets to disk.
 *
 * [JA] Android Keystore の `AES/GCM/NoPadding`（256-bit）で API キーを暗号化保管するストア実装です。
 * バックアップ除外設定と組み合わせ、平文の秘密情報がディスクやクラウドに残らないよう保護します。
 */
class AndroidKeystoreSecretStore(context: Context) : SecretStore {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "masusheet_secret_master_aes256_v1"
        private const val PREFS_NAME = "masusheet_secure_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val fallbackMemory = ConcurrentHashMap<String, String>()

    private fun getOrCreateMasterKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    private fun resetCorruptedKeystoreEntry(prefKeyToClear: String?) {
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) {
                ks.deleteEntry(KEY_ALIAS)
            }
        }
        if (prefKeyToClear != null) {
            prefs.edit().remove(prefKeyToClear).commit()
        }
    }

    override fun getSecret(key: String): String {
        fallbackMemory[key]?.let { return it }
        val encoded = prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: return ""
        val parts = encoded.split(':', limit = 2)
        if (parts.size != 2) {
            prefs.edit().remove(key).commit()
            return ""
        }
        val secretKey = getOrCreateMasterKey() ?: return ""
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Throwable) {
            // [EN] Self-heal invalidated/corrupted Keystore key and purge unreadable ciphertext so subsequent saves persist cleanly.
            // [JA] Keystore 鍵の失効や暗号文破損時は古い鍵・暗号文を安全に破棄し、次回保存時に新しい鍵で正常に永続化できるように自己修復します。
            resetCorruptedKeystoreEntry(key)
            ""
        }
    }

    override fun putSecret(key: String, plainText: String) {
        val clean = plainText.trim()
        if (clean.isEmpty()) {
            clearSecret(key)
            return
        }
        fun tryEncryptWith(secretKey: SecretKey): Boolean {
            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
                val encB64 = Base64.encodeToString(cipher.doFinal(clean.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
                fallbackMemory.remove(key)
                prefs.edit().putString(key, "$ivB64:$encB64").commit()
                true
            } catch (_: Throwable) {
                false
            }
        }

        val firstKey = getOrCreateMasterKey()
        if (firstKey != null && tryEncryptWith(firstKey)) {
            return
        }
        // Retry once after rotating a potentially invalidated AndroidKeyStore entry
        resetCorruptedKeystoreEntry(key)
        val regeneratedKey = getOrCreateMasterKey()
        if (regeneratedKey != null && tryEncryptWith(regeneratedKey)) {
            return
        }
        fallbackMemory[key] = clean
    }

    override fun clearSecret(key: String) {
        fallbackMemory.remove(key)
        prefs.edit().remove(key).commit()
    }
}
