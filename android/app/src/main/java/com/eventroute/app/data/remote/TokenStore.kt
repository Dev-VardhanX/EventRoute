package com.eventroute.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface TokenStore {
    val access: String?
    val refresh: String?
    fun save(access: String, refresh: String)
    fun clear()
}

class EncryptedTokenStore(context: Context) : TokenStore {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "eventroute_auth_tokens_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback for tests or environments without Keystore provider
        context.getSharedPreferences("eventroute_auth_tokens_plain", Context.MODE_PRIVATE)
    }

    override val access: String?
        get() = prefs.getString("access_token", null)

    override val refresh: String?
        get() = prefs.getString("refresh_token", null)

    override fun save(access: String, refresh: String) {
        prefs.edit()
            .putString("access_token", access)
            .putString("refresh_token", refresh)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
