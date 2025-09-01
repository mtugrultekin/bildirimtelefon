package com.bildirimtelefon.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.bildirimtelefon.app.TEST_NOTIFICATION" -> {
                val title = intent.getStringExtra("title") ?: "Test"
                val message = intent.getStringExtra("message") ?: "Test mesajı"
                val urgent = intent.getBooleanExtra("urgent", false)
                
                Log.d(TAG, "Test bildirimi alındı: $title - $message")
                
                // Burada NotificationService'e bildirim gönderebilirsin
                // veya doğrudan notification gösterebilirsin
            }
        }
    }
}