package com.bildirimtelefon.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.bildirimtelefon.app.service.NotificationService
import com.bildirimtelefon.app.utils.PreferenceManager

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                
                Log.d(TAG, "Boot completed veya package replaced: ${intent.action}")
                
                val preferenceManager = PreferenceManager(context)
                
                // Otomatik başlatma aktifse ve sunucu bilgileri varsa servisi başlat
                if (preferenceManager.isAutoStartEnabled() && preferenceManager.isConnected()) {
                    Log.d(TAG, "Otomatik başlatma aktif, NotificationService başlatılıyor")
                    
                    val serviceIntent = Intent(context, NotificationService::class.java)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else {
                    Log.d(TAG, "Otomatik başlatma devre dışı veya sunucu bilgileri yok")
                }
            }
        }
    }
}