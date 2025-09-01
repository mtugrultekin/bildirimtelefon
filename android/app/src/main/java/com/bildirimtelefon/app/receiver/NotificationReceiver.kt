package com.bildirimtelefon.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bildirimtelefon.app.service.SimpleNotificationService

class NotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                "com.bildirimtelefon.app.TEST_NOTIFICATION" -> {
                    val title = intent.getStringExtra("title") ?: "Test"
                    val message = intent.getStringExtra("message") ?: "Test mesajı"
                    val urgent = intent.getBooleanExtra("urgent", false)
                    
                    Log.d(TAG, "Test bildirimi alındı: $title - $message")
                    
                    // SimpleNotificationService'ten test notification göster
                    if (SimpleNotificationService.isRunning) {
                        // Service aktifse, service üzerinden göster
                        val serviceIntent = Intent(context, SimpleNotificationService::class.java)
                        serviceIntent.putExtra("action", "show_test_notification")
                        serviceIntent.putExtra("title", title)
                        serviceIntent.putExtra("message", message)
                        context.startService(serviceIntent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast receiver hatası", e)
        }
    }
}