package com.brahma.mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Settings + API keys. Keys live in EncryptedSharedPreferences (Android Keystore backed). */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "brahma_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences("brahma_fallback", Context.MODE_PRIVATE)
    }

    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var geminiKey: String
        get() = s("gemini_key", ""); set(v) = put("gemini_key", v.trim())
    var openrouterKey: String
        get() = s("or_key", ""); set(v) = put("or_key", v.trim())
    var geminiModel: String
        get() = s("gemini_model", "gemini-2.5-flash"); set(v) = put("gemini_model", v.trim())
    var openrouterModel: String
        get() = s("or_model", "google/gemini-2.5-flash"); set(v) = put("or_model", v.trim())
    /** "gemini" or "openrouter": which provider is tried first. */
    var primary: String
        get() = s("primary", "gemini"); set(v) = put("primary", v)
    /** BCP-47 tag used for speech recognition + TTS. */
    var language: String
        get() = s("lang", "bn-BD"); set(v) = put("lang", v)
    var speakReplies: Boolean
        get() = sp.getBoolean("speak", true); set(v) = sp.edit().putBoolean("speak", v).apply()
    var askConfirm: Boolean
        get() = sp.getBoolean("confirm", true); set(v) = sp.edit().putBoolean("confirm", v).apply()
}
