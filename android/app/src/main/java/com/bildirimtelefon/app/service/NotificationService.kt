package com.bildirimtelefon.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bildirimtelefon.app.MainActivity
import com.bildirimtelefon.app.R
import com.bildirimtelefon.app.utils.PreferenceManager
import org.json.JSONObject
import java.util.*

class NotificationService : Service(), TextToSpeech.OnInitListener {
    
    private lateinit var preferenceManager: PreferenceManager
    private var textToSpeech: TextToSpeech? = null
    private var isConnected = false
    
    companion object {
        private const val TAG = "NotificationService"
        private const val FOREGROUND_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "bildirim_telefon_channel"
        private const val NOTIFICATION_CHANNEL_URGENT_ID = "bildirim_telefon_urgent_channel"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        try {
            preferenceManager = PreferenceManager(this)
            textToSpeech = TextToSpeech(this, this)
            createNotificationChannels()
            Log.d(TAG, "NotificationService oluşturuldu")
        } catch (e: Exception) {
            Log.e(TAG, "NotificationService oluşturma hatası", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(FOREGROUND_ID, createForegroundNotification())
            isRunning = true
            Log.d(TAG, "NotificationService başlatıldı")
        } catch (e: Exception) {
            Log.e(TAG, "NotificationService başlatma hatası", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            isRunning = false
            textToSpeech?.shutdown()
            Log.d(TAG, "NotificationService durduruldu")
        } catch (e: Exception) {
            Log.e(TAG, "NotificationService durdurma hatası", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Normal bildirim kanalı
                val normalChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Bildirim Telefon",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Bildirim telefon sistemi bildirimleri"
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)
                }
                
                // Acil durum bildirim kanalı
                val urgentChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_URGENT_ID,
                    "Acil Durum Bildirimleri",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Acil durum bildirimleri"
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    enableLights(true)
                    lightColor = android.graphics.Color.RED
                }
                
                notificationManager.createNotificationChannel(normalChannel)
                notificationManager.createNotificationChannel(urgentChannel)
                
            } catch (e: Exception) {
                Log.e(TAG, "Notification channel oluşturma hatası", e)
            }
        }
    }

    private fun createForegroundNotification(): Notification {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Bildirim Telefon Aktif")
                .setContentText(if (isConnected) "Sunucuya bağlı" else "Bağlantı bekleniyor...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()
                
        } catch (e: Exception) {
            Log.e(TAG, "Foreground notification oluşturma hatası", e)
            
            // Fallback basit notification
            return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Bildirim Telefon")
                .setContentText("Servis aktif")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
    }

    fun showTestNotification(title: String, message: String) {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            
            // Titreşim
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(250)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Test notification gösterme hatası", e)
        }
    }

    override fun onInit(status: Int) {
        try {
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("tr", "TR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale.ENGLISH)
                }
                Log.d(TAG, "TextToSpeech başlatıldı")
            } else {
                Log.e(TAG, "TextToSpeech başlatılamadı")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech init hatası", e)
        }
    }
}