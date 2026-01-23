package com.minescope.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SettingsDataStore(val context: Context) {

    private var sharedPreferences: android.content.SharedPreferences? = null

    init {
        try {
            sharedPreferences = createEncryptedSharedPreferences()
        } catch (e: Exception) {
            e.printStackTrace()
            // Recovery: Delete specific file and key, then retry
            try {
                val prefsFile = java.io.File(context.filesDir.parent + "/shared_prefs/secret_shared_prefs.xml")
                if (prefsFile.exists()) {
                    prefsFile.delete()
                }
                
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (keyStore.containsAlias(masterKeyAlias)) {
                    keyStore.deleteEntry(masterKeyAlias)
                }

                sharedPreferences = createEncryptedSharedPreferences()
            } catch (retryException: Exception) {
                retryException.printStackTrace()
                // Fallback: Use standard SharedPreferences (Less secure but functional)
                sharedPreferences = context.getSharedPreferences("fallback_prefs", Context.MODE_PRIVATE)
            }
        }
    }

    private fun createEncryptedSharedPreferences(): android.content.SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "secret_shared_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(apiKey: String) {
        sharedPreferences?.edit()?.putString("x_api_key", apiKey.trim())?.apply()
    }

    fun getApiKey(): String? {
        return sharedPreferences?.getString("x_api_key", null)
    }

    fun clearApiKey() {
        sharedPreferences?.edit()?.remove("x_api_key")?.apply()
    }
}
