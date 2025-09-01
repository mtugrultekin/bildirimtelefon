package com.bildirimtelefon.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
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
import com.google.gson.Gson
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
        preferenceManager = PreferenceManager(this)
        textToSpeech = TextToSpeech(this, this)
        createNotificationChannels()
        Log.d(TAG, "NotificationService oluşturuldu")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FOREGROUND_ID, createForegroundNotification())
        connectToServer()
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        disconnectFromServer()
        textToSpeech?.shutdown()
        super.onDestroy()
        Log.d(TAG, "NotificationService durduruldu")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), 
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }
            
            notificationManager.createNotificationChannel(normalChannel)
            notificationManager.createNotificationChannel(urgentChannel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Bildirim Telefon Aktif")
            .setContentText(if (isConnected) "Sunucuya bağlı" else "Sunucuya bağlanıyor...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun connectToServer() {
        val serverUrl = preferenceManager.getServerUrl()
        if (serverUrl.isEmpty()) {
            Log.e(TAG, "Sunucu URL'si boş")
            return
        }

        try {
            val options = IO.Options().apply {
                timeout = 5000
                reconnection = true
                reconnectionAttempts = Integer.MAX_VALUE
                reconnectionDelay = 1000
            }
            
            socket = IO.socket(serverUrl.replace("http://", "ws://"), options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "WebSocket bağlantısı kuruldu")
                isConnected = true
                updateForegroundNotification()
                registerDevice()
                startHeartbeat()
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "WebSocket bağlantısı kesildi")
                isConnected = false
                updateForegroundNotification()
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "WebSocket bağlantı hatası: ${args.contentToString()}")
                isConnected = false
                updateForegroundNotification()
            }

            // Bildirim alma
            socket?.on("new-notification") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    handleNotification(data)
                }
            }

            // Acil durum bildirimi
            socket?.on("emergency-notification") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    handleEmergencyNotification(data)
                }
            }

            // Sesli mesaj
            socket?.on("voice-message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    handleVoiceMessage(data)
                }
            }

            socket?.connect()
            
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Geçersiz sunucu URL'si: $serverUrl", e)
        }
    }

    private fun disconnectFromServer() {
        socket?.disconnect()
        socket?.off()
        socket = null
        isConnected = false
    }

    private fun registerDevice() {
        val deviceInfo = JSONObject().apply {
            put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("deviceModel", Build.MODEL)
            put("androidVersion", Build.VERSION.RELEASE)
            put("appVersion", "1.0")
        }
        
        socket?.emit("register-device", deviceInfo)
        Log.d(TAG, "Cihaz kaydedildi: $deviceInfo")
    }

    private fun startHeartbeat() {
        val heartbeatRunnable = object : Runnable {
            override fun run() {
                if (isConnected && socket?.connected() == true) {
                    socket?.emit("heartbeat")
                    android.os.Handler(mainLooper).postDelayed(this, 30000) // 30 saniye
                }
            }
        }
        android.os.Handler(mainLooper).postDelayed(heartbeatRunnable, 30000)
    }

    private fun handleNotification(data: JSONObject) {
        try {
            val id = data.getString("id")
            val title = data.optString("title", "Bildirim")
            val message = data.getString("message")
            val urgent = data.optBoolean("urgent", false)
            val sound = data.optBoolean("sound", true)
            val vibrate = data.optBoolean("vibrate", true)

            Log.d(TAG, "Bildirim alındı: $title - $message")

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

            Log.d(TAG, "Acil durum bildirimi alındı: $title - $message")

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

            Log.d(TAG, "Sesli mesaj alındı: $message")

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
    }

    private fun sendNotificationStatus(notificationId: String, status: String) {
        val statusData = JSONObject().apply {
            put("notificationId", notificationId)
            put("status", status)
            put("timestamp", System.currentTimeMillis())
        }
        
        socket?.emit("notification-status", statusData)
    }

    private fun updateForegroundNotification() {
        val notification = createForegroundNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FOREGROUND_ID, notification)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("tr", "TR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.setLanguage(Locale.ENGLISH)
            }
            Log.d(TAG, "TextToSpeech başlatıldı")
        } else {
            Log.e(TAG, "TextToSpeech başlatılamadı")
        }
    }
}