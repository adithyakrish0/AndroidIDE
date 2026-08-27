package com.example.foldermind

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveGeminiKey(key: String) {
        sharedPreferences.edit().putString(KEY_GEMINI_API, key).apply()
    }

    fun getGeminiKey(): String? {
        return sharedPreferences.getString(KEY_GEMINI_API, null)
    }

    fun saveGroqKey(key: String) {
        sharedPreferences.edit().putString(KEY_GROQ_API, key).apply()
    }

    fun getGroqKey(): String? {
        return sharedPreferences.getString(KEY_GROQ_API, null)
    }

    fun setAutonomousMode(isAutonomous: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTONOMOUS_MODE, isAutonomous).apply()
    }

    fun isAutonomousMode(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTONOMOUS_MODE, false)
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_GROQ_API = "groq_api_key"
        private const val KEY_AUTONOMOUS_MODE = "autonomous_mode"
    }
}
