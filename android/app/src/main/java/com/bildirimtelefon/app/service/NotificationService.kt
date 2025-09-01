package com.bildirimtelefon.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bildirimtelefon.app.MainActivity
import com.bildirimtelefon.app.R
import com.bildirimtelefon.app.utils.PreferenceManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationService : Service(), TextToSpeech.OnInitListener {
    
    private var okHttpClient: OkHttpClient? = null
    private lateinit var preferenceManager: PreferenceManager
    private var textToSpeech: TextToSpeech? = null
    private var isRegistered = false
    private var pollingHandler = Handler(Looper.getMainLooper())
    private var deviceId: String = ""
    
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
            deviceId = "android_${System.currentTimeMillis()}"
            
            okHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
                
            Log.d(TAG, "✅ NotificationService oluşturuldu - DeviceID: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ NotificationService oluşturma hatası", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(FOREGROUND_ID, createForegroundNotification())
            
            val action = intent?.getStringExtra("action")
            if (action == "show_test_notification") {
                val title = intent.getStringExtra("title") ?: "Test"
                val message = intent.getStringExtra("message") ?: "Test mesajı"
                Log.d(TAG, "🧪 Test notification action alındı: $title")
                showTestNotification(title, message)
            } else {
                Log.d(TAG, "🔄 Normal başlatma - HTTP polling başlıyor...")
                registerDeviceHttp()
                startPolling()
            }
            
            isRunning = true
            Log.d(TAG, "✅ NotificationService başlatıldı")
        } catch (e: Exception) {
            Log.e(TAG, "❌ NotificationService başlatma hatası", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            isRunning = false
            pollingHandler.removeCallbacksAndMessages(null)
            unregisterDeviceHttp()
            textToSpeech?.shutdown()
            Log.d(TAG, "✅ NotificationService durduruldu")
        } catch (e: Exception) {
            Log.e(TAG, "❌ NotificationService durdurma hatası", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerDeviceHttp() {
        val serverUrl = preferenceManager.getServerUrl()
        if (serverUrl.isEmpty()) {
            Log.e(TAG, "❌ Sunucu URL'si boş")
            return
        }

        try {
            Log.d(TAG, "🔗 HTTP ile cihaz kaydediliyor: $serverUrl")
            
            val deviceInfo = JSONObject()
            deviceInfo.put("deviceId", deviceId)
            deviceInfo.put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
            deviceInfo.put("deviceModel", Build.MODEL)
            deviceInfo.put("androidVersion", Build.VERSION.RELEASE)
            deviceInfo.put("appVersion", "1.0")
            deviceInfo.put("registeredAt", System.currentTimeMillis())
            
            val requestBody = deviceInfo.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$serverUrl/api/register-device")
                .post(requestBody)
                .build()

            okHttpClient?.newCall(request)?.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "❌ Cihaz kaydetme HTTP hatası", e)
                    pollingHandler.postDelayed({ registerDeviceHttp() }, 10000)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        isRegistered = true
                        Log.d(TAG, "✅ Cihaz HTTP ile kaydedildi")
                        updateForegroundNotification()
                    } else {
                        Log.e(TAG, "❌ Cihaz kaydetme başarısız: ${response.code}")
                        pollingHandler.postDelayed({ registerDeviceHttp() }, 10000)
                    }
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ HTTP cihaz kaydetme genel hatası", e)
        }
    }

    private fun unregisterDeviceHttp() {
        val serverUrl = preferenceManager.getServerUrl()
        if (serverUrl.isEmpty() || !isRegistered) return

        try {
            val request = Request.Builder()
                .url("$serverUrl/api/unregister-device/$deviceId")
                .delete()
                .build()

            okHttpClient?.newCall(request)?.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "❌ Cihaz kayıt silme hatası", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d(TAG, "✅ Cihaz kaydı silindi")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Cihaz kayıt silme genel hatası", e)
        }
    }

    private fun startPolling() {
        val pollingRunnable = object : Runnable {
            override fun run() {
                if (isRunning && isRegistered) {
                    checkForNotifications()
                    pollingHandler.postDelayed(this, 3000)
                }
            }
        }
        pollingHandler.postDelayed(pollingRunnable, 3000)
    }

    private fun checkForNotifications() {
        val serverUrl = preferenceManager.getServerUrl()
        if (serverUrl.isEmpty()) return

        try {
            val request = Request.Builder()
                .url("$serverUrl/api/notifications-for-device/$deviceId")
                .get()
                .build()

            okHttpClient?.newCall(request)?.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "❌ Bildirim kontrol hatası", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                val success = jsonResponse.getBoolean("success")
                                if (success) {
                                    val notifications = jsonResponse.getJSONArray("notifications")
                                    for (i in 0 until notifications.length()) {
                                        val notification = notifications.getJSONObject(i)
                                        processNotification(notification)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Bildirim response parse hatası", e)
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Bildirim kontrol genel hatası", e)
        }
    }

    private fun processNotification(notification: JSONObject) {
        try {
            val id = notification.getString("id")
            val title = notification.optString("title", "Bildirim")
            val message = notification.getString("message")
            val urgent = notification.optBoolean("urgent", false)
            val emergency = notification.optBoolean("emergency", false)

            Log.d(TAG, "📨 Bildirim işleniyor: $title - $message")

            when {
                emergency -> {
                    showNotification(id, "🚨 $title", message, true, true, true)
                    textToSpeech?.speak("Acil durum: $message", TextToSpeech.QUEUE_FLUSH, null, "emergency_$id")
                }
                else -> {
                    showNotification(id, title, message, urgent, true, true)
                }
            }

            markNotificationAsDelivered(id)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Bildirim işleme hatası", e)
        }
    }

    private fun markNotificationAsDelivered(notificationId: String) {
        val serverUrl = preferenceManager.getServerUrl()
        if (serverUrl.isEmpty()) return

        try {
            val statusData = JSONObject()
            statusData.put("notificationId", notificationId)
            statusData.put("deviceId", deviceId)
            statusData.put("status", "delivered")
            statusData.put("timestamp", System.currentTimeMillis())
            
            val requestBody = statusData.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$serverUrl/api/notification-delivered")
                .post(requestBody)
                .build()

            okHttpClient?.newCall(request)?.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "❌ Bildirim durumu gönderme hatası", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d(TAG, "✅ Bildirim durumu gönderildi: delivered")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Bildirim durumu gönderme genel hatası", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                val normalChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Bildirim Telefon",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                normalChannel.description = "Bildirim telefon sistemi bildirimleri"
                normalChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                normalChannel.enableVibration(true)
                normalChannel.vibrationPattern = longArrayOf(0, 250, 250, 250)
                
                val urgentChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_URGENT_ID,
                    "Acil Durum Bildirimleri",
                    NotificationManager.IMPORTANCE_HIGH
                )
                urgentChannel.description = "Acil durum bildirimleri"
                urgentChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
                urgentChannel.enableVibration(true)
                urgentChannel.vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                urgentChannel.enableLights(true)
                urgentChannel.lightColor = android.graphics.Color.RED
                
                notificationManager.createNotificationChannel(normalChannel)
                notificationManager.createNotificationChannel(urgentChannel)
                
                Log.d(TAG, "✅ Notification channels oluşturuldu")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Notification channel oluşturma hatası", e)
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

            val statusText: String = if (isRegistered) {
                "✅ Sunucuya bağlı (HTTP)"
            } else {
                "🔄 Bağlanıyor..."
            }

            return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("📱 Bildirim Telefon")
                .setContentText(statusText)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()
                
        } catch (e: Exception) {
            Log.e(TAG, "❌ Foreground notification oluşturma hatası", e)
            
            return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Bildirim Telefon")
                .setContentText("Servis aktif")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
    }

    private fun updateForegroundNotification() {
        try {
            val notification = createForegroundNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(FOREGROUND_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Foreground notification güncelleme hatası", e)
        }
    }

    private fun showNotification(id: String, title: String, message: String, urgent: Boolean, sound: Boolean, vibrate: Boolean) {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId: String = if (urgent) {
                NOTIFICATION_CHANNEL_URGENT_ID
            } else {
                NOTIFICATION_CHANNEL_ID
            }
            
            val priority: Int = if (urgent) {
                NotificationCompat.PRIORITY_HIGH
            } else {
                NotificationCompat.PRIORITY_DEFAULT
            }

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
                    val pattern: LongArray = if (urgent) {
                        longArrayOf(0, 500, 200, 500, 200, 500)
                    } else {
                        longArrayOf(0, 250, 250, 250)
                    }
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    val pattern: LongArray = if (urgent) {
                        longArrayOf(0, 500, 200, 500, 200, 500)
                    } else {
                        longArrayOf(0, 250, 250, 250)
                    }
                    vibrator.vibrate(pattern, -1)
                }
            }

            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.notify(id.hashCode(), builder.build())
            
            Log.d(TAG, "✅ Bildirim gösterildi: $title")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Bildirim gösterme hatası", e)
        }
    }

    fun showTestNotification(title: String, message: String) {
        try {
            Log.d(TAG, "🧪 Test bildirimi gösteriliyor: $title - $message")
            showNotification("test_${System.currentTimeMillis()}", title, message, false, true, true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Test notification hatası", e)
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
            Log.e(TAG, "❌ TextToSpeech init hatası", e)
        }
    }
}