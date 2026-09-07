package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.BuildConfig
import java.nio.charset.StandardCharsets

class ApiKeyStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_clipper_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENCRYPTED_USER_KEY = "enc_gemini_api_key"
        private const val KEY_SELECTED_MODEL = "selected_gemini_model"
        private const val OBFUSCATION_SALT = "AI_CLIPPER_SALT_9X72"
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        const val PRO_MODEL = "gemini-3.1-pro-preview"
    }

    fun getUserApiKey(): String {
        val stored = prefs.getString(KEY_ENCRYPTED_USER_KEY, null)
        if (!stored.isNullOrEmpty()) {
            return try {
                deobfuscate(stored)
            } catch (e: Exception) {
                ""
            }
        }
        // Fallback to BuildConfig if provided via AI Studio secrets
        return try {
            val buildConfigKey = BuildConfig.GEMINI_API_KEY
            if (buildConfigKey != "MY_GEMINI_API_KEY" && buildConfigKey.isNotBlank()) {
                buildConfigKey
            } else {
                ""
            }
        } catch (e: Throwable) {
            ""
        }
    }

    fun hasCustomUserKey(): Boolean {
        val stored = prefs.getString(KEY_ENCRYPTED_USER_KEY, null)
        return !stored.isNullOrEmpty()
    }

    fun isConfigured(): Boolean {
        return getUserApiKey().isNotBlank()
    }

    fun saveUserApiKey(rawKey: String) {
        if (rawKey.isBlank()) {
            clearUserApiKey()
            return
        }
        val obfuscated = obfuscate(rawKey.trim())
        prefs.edit().putString(KEY_ENCRYPTED_USER_KEY, obfuscated).apply()
    }

    fun clearUserApiKey() {
        prefs.edit().remove(KEY_ENCRYPTED_USER_KEY).apply()
    }

    fun getSelectedModel(): String {
        return prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
    }

    fun getMaskedKey(): String {
        val key = getUserApiKey()
        if (key.isBlank()) return "Not configured"
        if (key.length <= 8) return "••••••••"
        return "${key.take(6)}••••••••${key.takeLast(4)}"
    }

    private fun obfuscate(input: String): String {
        val combined = "$OBFUSCATION_SALT:$input"
        val bytes = combined.toByteArray(StandardCharsets.UTF_8)
        // Simple reversible XOR byte masking before base64
        val masked = ByteArray(bytes.size)
        val saltBytes = OBFUSCATION_SALT.toByteArray(StandardCharsets.UTF_8)
        for (i in bytes.indices) {
            masked[i] = (bytes[i].toInt() xor saltBytes[i % saltBytes.size].toInt()).toByte()
        }
        return Base64.encodeToString(masked, Base64.NO_WRAP)
    }

    private fun deobfuscate(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val saltBytes = OBFUSCATION_SALT.toByteArray(StandardCharsets.UTF_8)
        val unmasked = ByteArray(bytes.size)
        for (i in bytes.indices) {
            unmasked[i] = (bytes[i].toInt() xor saltBytes[i % saltBytes.size].toInt()).toByte()
        }
        val full = String(unmasked, StandardCharsets.UTF_8)
        val prefix = "$OBFUSCATION_SALT:"
        return if (full.startsWith(prefix)) {
            full.substring(prefix.length)
        } else {
            full
        }
    }
}
