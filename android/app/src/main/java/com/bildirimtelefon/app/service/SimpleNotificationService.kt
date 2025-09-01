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

class SimpleNotificationService : Service(), TextToSpeech.OnInitListener {
    
    private var okHttpClient: OkHttpClient? = null
    private lateinit var preferenceManager: PreferenceManager
    private var textToSpeech: TextToSpeech? = null
    private var isRegistered = false
    private var pollingHandler = Handler(Looper.getMainLooper())
    private var deviceId: String = ""
    
    companion object {
        private const val TAG = "SimpleNotificationService"
        private const val FOREGROUND_ID = 1002
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
            
            // OkHttp client oluştur
            okHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
                
            Log.d(TAG, "✅ SimpleNotificationService oluşturuldu")
        } catch (e: Exception) {
            Log.e(TAG, "❌ SimpleNotificationService oluşturma hatası", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(FOREGROUND_ID, createForegroundNotification())
            
            // Test notification action
            if (intent?.getStringExtra("action") == "show_test_notification") {
                val title = intent.getStringExtra("title") ?: "Test"
                val message = intent.getStringExtra("message") ?: "Test mesajı"
                Log.d(TAG, "🧪 Test notification action alındı: $title")
                showTestNotification(title, message)
            } else {
                // Normal başlatma - HTTP polling başlat
                Log.d(TAG, "🔄 Normal başlatma - HTTP polling başlıyor...")
                registerDeviceHttp()
                startPolling()
            }
            
            isRunning = true
            Log.d(TAG, "✅ SimpleNotificationService başlatıldı")
        } catch (e: Exception) {
            Log.e(TAG, "❌ SimpleNotificationService başlatma hatası", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            isRunning = false
            pollingHandler.removeCallbacksAndMessages(null)
            unregisterDeviceHttp()
            textToSpeech?.shutdown()
            Log.d(TAG, "✅ SimpleNotificationService durduruldu")
        } catch (e: Exception) {
            Log.e(TAG, "❌ SimpleNotificationService durdurma hatası", e)
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
            
            val deviceInfo = JSONObject().apply {
                put("deviceId", deviceId)
                put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("deviceModel", Build.MODEL)
                put("androidVersion", Build.VERSION.RELEASE)
                put("appVersion", "1.0")
                put("registeredAt", System.currentTimeMillis())
            }
            
            val requestBody = deviceInfo.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$serverUrl/api/register-device")
                .post(requestBody)
                .build()

            okHttpClient?.newCall(request)?.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "❌ Cihaz kaydetme HTTP hatası", e)
                    // 10 saniye sonra tekrar dene
                    pollingHandler.postDelayed({ registerDeviceHttp() }, 10000)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        isRegistered = true
                        Log.d(TAG, "✅ Cihaz HTTP ile kaydedildi")
                        updateForegroundNotification()
                    } else {
                        Log.e(TAG, "❌ Cihaz kaydetme başarısız: ${response.code}")
                        // 10 saniye sonra tekrar dene
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
                    pollingHandler.postDelayed(this, 2000) // 2 saniyede bir kontrol
                }
            }
        }
        pollingHandler.postDelayed(pollingRunnable, 2000)
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
                        response.body?.string()?.let { responseBody ->
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                if (jsonResponse.getBoolean("success")) {
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
            val type = notification.optString("type", "normal")
            val urgent = notification.optBoolean("urgent", false)

            Log.d(TAG, "📨 Bildirim işleniyor: $title - $message (type: $type)")

            when (type) {
                "emergency" -> {
                    showNotification(id, "🚨 $title", message, true, true, true)
                    textToSpeech?.speak("Acil durum: $message", TextToSpeech.QUEUE_FLUSH, null, "emergency_$id")
                }
                "voice" -> {
                    textToSpeech?.speak(message, TextToSpeech.QUEUE_ADD, null, "voice_$id")
                }
                else -> {
                    showNotification(id, title, message, urgent, true, true)
                }
            }

            // Bildirim işlendiğini sunucuya bildir
            markNotificationAsDelivered(id)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Bildirim işleme hatası", e)
        }
    }

    private fun markNotificationAsDelivered(notificationId: String) {
        val serverUrl = preferenceManager.getServerUrl()
        if (serverUrl.isEmpty()) return

        try {
            val statusData = JSONObject().apply {
                put("notificationId", notificationId)
                put("deviceId", deviceId)
                put("status", "delivered")
                put("timestamp", System.currentTimeMillis())
            }
            
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