package com.bildirimtelefon.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.bildirimtelefon.app.MainActivity.ConnectionInfo

class PreferenceManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("bildirim_telefon_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SOCKET_URL = "socket_url"
        private const val KEY_IS_CONNECTED = "is_connected"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATE_ENABLED = "vibrate_enabled"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
    }
    
    fun saveConnectionInfo(connectionInfo: ConnectionInfo) {
        sharedPreferences.edit().apply {
            putString(KEY_SERVER_URL, connectionInfo.serverUrl)
            putString(KEY_SOCKET_URL, connectionInfo.socketUrl)
            putBoolean(KEY_IS_CONNECTED, true)
            apply()
        }
    }
    
    fun getServerUrl(): String {
        return sharedPreferences.getString(KEY_SERVER_URL, "") ?: ""
    }
    
    fun getSocketUrl(): String {
        return sharedPreferences.getString(KEY_SOCKET_URL, "") ?: ""
    }
    
    fun isConnected(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_CONNECTED, false)
    }
    
    fun clearConnectionInfo() {
        sharedPreferences.edit().apply {
            remove(KEY_SERVER_URL)
            remove(KEY_SOCKET_URL)
            putBoolean(KEY_IS_CONNECTED, false)
            apply()
        }
    }
    
    fun setDeviceName(name: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_NAME, name).apply()
    }
    
    fun getDeviceName(): String {
        return sharedPreferences.getString(KEY_DEVICE_NAME, android.os.Build.MODEL) ?: android.os.Build.MODEL
    }
    
    fun setAutoStart(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }
    
    fun isAutoStartEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_START, true)
    }
    
    fun setSoundEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }
    
    fun isSoundEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SOUND_ENABLED, true)
    }
    
    fun setVibrateEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_VIBRATE_ENABLED, enabled).apply()
    }
    
    fun isVibrateEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_VIBRATE_ENABLED, true)
    }
    
    fun setVoiceEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
    }
    
    fun isVoiceEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_VOICE_ENABLED, true)
    }
}