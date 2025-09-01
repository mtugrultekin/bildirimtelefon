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
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException
import java.util.*

class NotificationService : Service(), TextToSpeech.OnInitListener {
    
    private var socket: Socket? = null
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
            
            // Test notification action
            if (intent?.getStringExtra("action") == "show_test_notification") {
                val title = intent.getStringExtra("title") ?: "Test"
                val message = intent.getStringExtra("message") ?: "Test mesajı"
                showTestNotification(title, message)
            } else {
                // Normal başlatma - WebSocket'e bağlan
                connectToServer()
            }
            
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
            disconnectFromServer()
            textToSpeech?.shutdown()
            Log.d(TAG, "NotificationService durduruldu")
        } catch (e: Exception) {
            Log.e(TAG, "NotificationService durdurma hatası", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectToServer() {
        val serverUrl = preferenceManager.getServerUrl()
        if (serverUrl.isEmpty()) {
            Log.e(TAG, "Sunucu URL'si boş")
            return
        }

        try {
            Log.d(TAG, "Sunucuya bağlanıyor: $serverUrl")
            
            val options = IO.Options().apply {
                timeout = 10000
                reconnection = true
                reconnectionAttempts = Integer.MAX_VALUE
                reconnectionDelay = 2000
            }
            
            // HTTP URL'yi WebSocket URL'ye çevir
            val socketUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://")
            Log.d(TAG, "WebSocket URL: $socketUrl")
            
            socket = IO.socket(socketUrl, options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "✅ WebSocket bağlantısı kuruldu")
                isConnected = true
                updateForegroundNotification()
                registerDevice()
                startHeartbeat()
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "❌ WebSocket bağlantısı kesildi")
                isConnected = false
                updateForegroundNotification()
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "❌ WebSocket bağlantı hatası: ${args.contentToString()}")
                isConnected = false
                updateForegroundNotification()
            }

            // Bildirim alma
            socket?.on("new-notification") { args ->
                Log.d(TAG, "📨 Yeni bildirim alındı")
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        handleNotification(data)
                    } catch (e: Exception) {
                        Log.e(TAG, "Bildirim parse hatası", e)
                    }
                }
            }

            // Acil durum bildirimi
            socket?.on("emergency-notification") { args ->
                Log.d(TAG, "🚨 Acil durum bildirimi alındı")
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        handleEmergencyNotification(data)
                    } catch (e: Exception) {
                        Log.e(TAG, "Acil durum bildirimi parse hatası", e)
                    }
                }
            }

            // Sesli mesaj
            socket?.on("voice-message") { args ->
                Log.d(TAG, "🔊 Sesli mesaj alındı")
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as JSONObject
                        handleVoiceMessage(data)
                    } catch (e: Exception) {
                        Log.e(TAG, "Sesli mesaj parse hatası", e)
                    }
                }
            }

            socket?.connect()
            Log.d(TAG, "WebSocket bağlantısı başlatıldı")
            
        } catch (e: URISyntaxException) {
            Log.e(TAG, "❌ Geçersiz sunucu URL'si: $serverUrl", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ WebSocket bağlantı genel hatası", e)
        }
    }

    private fun disconnectFromServer() {
        try {
            socket?.disconnect()
            socket?.off()
            socket = null
            isConnected = false
            Log.d(TAG, "WebSocket bağlantısı kapatıldı")
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket kapatma hatası", e)
        }
    }

    private fun registerDevice() {
        try {
            val deviceInfo = JSONObject().apply {
                put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("deviceModel", Build.MODEL)
                put("androidVersion", Build.VERSION.RELEASE)
                put("appVersion", "1.0")
            }
            
            socket?.emit("register-device", deviceInfo)
            Log.d(TAG, "📱 Cihaz kaydedildi: $deviceInfo")
        } catch (e: Exception) {
            Log.e(TAG, "Cihaz kaydetme hatası", e)
        }
    }

    private fun startHeartbeat() {
        try {
            val heartbeatRunnable = object : Runnable {
                override fun run() {
                    if (isConnected && socket?.connected() == true) {
                        socket?.emit("heartbeat")
                        Log.d(TAG, "💓 Heartbeat gönderildi")
                        android.os.Handler(mainLooper).postDelayed(this, 30000) // 30 saniye
                    } else {
                        Log.d(TAG, "❌ Heartbeat durdu - bağlantı yok")
                    }
                }
            }
            android.os.Handler(mainLooper).postDelayed(heartbeatRunnable, 30000)
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat başlatma hatası", e)
        }
    }

    private fun handleNotification(data: JSONObject) {
        try {
            val id = data.getString("id")
            val title = data.optString("title", "Bildirim")
            val message = data.getString("message")
            val urgent = data.optBoolean("urgent", false)
            val sound = data.optBoolean("sound", true)
            val vibrate = data.optBoolean("vibrate", true)

            Log.d(TAG, "📨 Bildirim işleniyor: $title - $message")

            showNotification(id, title, message, urgent, sound, vibrate)
            sendNotificationStatus(id, "delivered")

        } catch (e: Exception) {
            Log.e(TAG, "Bildirim işleme hatası", e)
        }
    }

    private fun handleEmergencyNotification(data: JSONObject) {
        try {
            val id = data.getString("id")
            val title = data.optString("title", "🚨 ACİL DURUM")
            val message = data.getString("message")

            Log.d(TAG, "🚨 Acil durum bildirimi işleniyor: $title - $message")

            showNotification(id, title, message, true, true, true)
            
            // Sesli uyarı
            textToSpeech?.speak(
                "Acil durum bildirimi: $message",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "emergency_$id"
            )
            
            sendNotificationStatus(id, "delivered")

        } catch (e: Exception) {
            Log.e(TAG, "Acil durum bildirimi işleme hatası", e)
        }
    }

    private fun handleVoiceMessage(data: JSONObject) {
        try {
            val id = data.getString("id")
            val message = data.getString("message")

            Log.d(TAG, "🔊 Sesli mesaj işleniyor: $message")

            textToSpeech?.speak(
                message,
                TextToSpeech.QUEUE_ADD,
                null,
                "voice_$id"
            )
            
            sendNotificationStatus(id, "delivered")

        } catch (e: Exception) {
            Log.e(TAG, "Sesli mesaj işleme hatası", e)
        }
    }

    private fun showNotification(id: String, title: String, message: String, urgent: Boolean, sound: Boolean, vibrate: Boolean) {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = if (urgent) NOTIFICATION_CHANNEL_URGENT_ID else NOTIFICATION_CHANNEL_ID
            val priority = if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

            val builder = NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(priority)

            if (!sound) {
                builder.setSilent(true)
            }

            if (vibrate) {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = if (urgent) 
                        longArrayOf(0, 500, 200, 500, 200, 500) else 
                        longArrayOf(0, 250, 250, 250)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    val pattern = if (urgent) 
                        longArrayOf(0, 500, 200, 500, 200, 500) else 
                        longArrayOf(0, 250, 250, 250)
                    vibrator.vibrate(pattern, -1)
                }
            }

            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.notify(id.hashCode(), builder.build())
            
            Log.d(TAG, "✅ Bildirim gösterildi: $title")

        } catch (e: Exception) {
            Log.e(TAG, "Bildirim gösterme hatası", e)
        }
    }

    private fun sendNotificationStatus(notificationId: String, status: String) {
        try {
            val statusData = JSONObject().apply {
                put("notificationId", notificationId)
                put("status", status)
                put("timestamp", System.currentTimeMillis())
            }
            
            socket?.emit("notification-status", statusData)
            Log.d(TAG, "📤 Bildirim durumu gönderildi: $status")
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim durumu gönderme hatası", e)
        }
    }

    private fun updateForegroundNotification() {
        try {
            val notification = createForegroundNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(FOREGROUND_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Foreground notification güncelleme hatası", e)
        }
    }

    fun showTestNotification(title: String, message: String) {
        try {
            Log.d(TAG, "🧪 Test bildirimi gösteriliyor: $title - $message")
            showNotification("test_${System.currentTimeMillis()}", title, message, false, true, true)
        } catch (e: Exception) {
            Log.e(TAG, "Test notification hatası", e)
        }
    }

    override fun onInit(status: Int) {
        try {
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("tr", "TR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale.ENGLISH)
                }
                Log.d(TAG, "✅ TextToSpeech başlatıldı")
            } else {
                Log.e(TAG, "❌ TextToSpeech başlatılamadı")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech init hatası", e)
        }
    }

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
                
                Log.d(TAG, "✅ Notification channels oluşturuldu")
                
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

            val statusText = when {
                isConnected -> "✅ Sunucuya bağlı"
                socket != null -> "🔄 Bağlanıyor..."
                else -> "⏳ Bağlantı bekleniyor..."
            }

            return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("📱 Bildirim Telefon Aktif")
                .setContentText(statusText)
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
}