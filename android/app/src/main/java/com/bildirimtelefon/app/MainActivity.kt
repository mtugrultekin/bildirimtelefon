package com.bildirimtelefon.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bildirimtelefon.app.service.NotificationService
import com.bildirimtelefon.app.utils.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    
    private lateinit var preferenceManager: PreferenceManager
    private var textToSpeech: TextToSpeech? = null
    private val PERMISSION_REQUEST_CODE = 1001
    
    // UI Elements
    private lateinit var deviceNameText: TextView
    private lateinit var androidVersionText: TextView
    private lateinit var connectionStatusText: TextView
    private lateinit var connectButton: Button
    private lateinit var settingsButton: Button
    private lateinit var testNotificationButton: Button
    private lateinit var testVoiceButton: Button

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleQRResult(result.contents)
        } else {
            Toast.makeText(this, "QR kod taraması iptal edildi", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main_simple)
            
            preferenceManager = PreferenceManager(this)
            textToSpeech = TextToSpeech(this, this)
            
            initViews()
            setupUI()
            checkPermissions()
            updateConnectionStatus()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Başlatma hatası: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
    
    private fun initViews() {
        deviceNameText = findViewById(R.id.deviceNameText)
        androidVersionText = findViewById(R.id.androidVersionText)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        connectButton = findViewById(R.id.connectButton)
        settingsButton = findViewById(R.id.settingsButton)
        testNotificationButton = findViewById(R.id.testNotificationButton)
        testVoiceButton = findViewById(R.id.testVoiceButton)
    }

    private fun setupUI() {
        // Cihaz bilgileri
        deviceNameText.text = "${Build.MANUFACTURER} ${Build.MODEL}"
        androidVersionText.text = "Android ${Build.VERSION.RELEASE}"
        
        // Buton click listeners
        connectButton.setOnClickListener {
            if (preferenceManager.isConnected()) {
                disconnectFromServer()
            } else {
                startQRScanner()
            }
        }
        
        settingsButton.setOnClickListener {
            try {
                startActivity(Intent(this, SettingsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Ayarlar açılamadı", Toast.LENGTH_SHORT).show()
            }
        }
        
        testNotificationButton.setOnClickListener {
            testNotification()
        }
        
        testVoiceButton.setOnClickListener {
            testVoiceMessage()
        }
        
        // Servis durumu kontrolü
        if (preferenceManager.isConnected() && !isServiceRunning()) {
            startNotificationService()
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        // Bildirim izni (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Kamera izni (QR tarama için)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun startQRScanner() {
        try {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("QR kodu tarayın")
                setCameraId(0)
                setBeepEnabled(true)
                setBarcodeImageEnabled(true)
                setOrientationLocked(false)
            }
            qrScannerLauncher.launch(options)
        } catch (e: Exception) {
            Toast.makeText(this, "QR tarayıcı açılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleQRResult(qrContent: String) {
        try {
            val connectionInfo = com.google.gson.Gson().fromJson(qrContent, ConnectionInfo::class.java)
            
            MaterialAlertDialogBuilder(this)
                .setTitle("Sunucuya Bağlan")
                .setMessage("Sunucu: ${connectionInfo.serverUrl}\n\nBağlantı kurulsun mu?")
                .setPositiveButton("Bağlan") { _, _ ->
                    connectToServer(connectionInfo)
                }
                .setNegativeButton("İptal", null)
                .show()
                
        } catch (e: Exception) {
            Toast.makeText(this, "Geçersiz QR kod: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToServer(connectionInfo: ConnectionInfo) {
        try {
            preferenceManager.saveConnectionInfo(connectionInfo)
            startNotificationService()
            updateConnectionStatus()
            Toast.makeText(this, "Sunucuya bağlanıyor...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Bağlantı hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun disconnectFromServer() {
        try {
            stopNotificationService()
            preferenceManager.clearConnectionInfo()
            updateConnectionStatus()
            Toast.makeText(this, "Sunucu bağlantısı kesildi", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Bağlantı kesme hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startNotificationService() {
        try {
            val intent = Intent(this, NotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Servis başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopNotificationService() {
        try {
            val intent = Intent(this, NotificationService::class.java)
            stopService(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Servis durdurulamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isServiceRunning(): Boolean {
        return NotificationService.isRunning
    }

    private fun updateConnectionStatus() {
        try {
            val isConnected = preferenceManager.isConnected()
            val serverUrl = preferenceManager.getServerUrl()
            
            if (isConnected && serverUrl.isNotEmpty()) {
                connectionStatusText.text = "Bağlı: $serverUrl"
                connectionStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                connectButton.text = "Bağlantıyı Kes"
                connectButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                testNotificationButton.isEnabled = true
                testVoiceButton.isEnabled = true
            } else {
                connectionStatusText.text = "Bağlı değil"
                connectionStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                connectButton.text = "QR Kod Tarat"
                connectButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_bright))
                testNotificationButton.isEnabled = false
                testVoiceButton.isEnabled = false
            }
        } catch (e: Exception) {
            Toast.makeText(this, "UI güncelleme hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun testNotification() {
        try {
            val intent = Intent("com.bildirimtelefon.app.TEST_NOTIFICATION")
            intent.putExtra("title", "Test Bildirimi")
            intent.putExtra("message", "Bu bir test bildirimidir.")
            intent.putExtra("urgent", false)
            sendBroadcast(intent)
            
            Toast.makeText(this, "Test bildirimi gönderildi", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Test bildirimi hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun testVoiceMessage() {
        try {
            if (textToSpeech?.isSpeaking == false) {
                val message = "Bu bir test sesli mesajıdır. Bildirim telefon sistemi çalışıyor."
                textToSpeech?.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "test_message"
                )
                Toast.makeText(this, "Test sesli mesajı çalıyor", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sesli mesaj zaten çalıyor", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Sesli mesaj hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onInit(status: Int) {
        try {
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("tr", "TR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Türkçe desteklenmiyorsa İngilizce kullan
                    textToSpeech?.setLanguage(Locale.ENGLISH)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "TTS başlatma hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            updateConnectionStatus()
        } catch (e: Exception) {
            Toast.makeText(this, "Resume hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        try {
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        try {
            if (requestCode == PERMISSION_REQUEST_CODE) {
                val deniedPermissions = mutableListOf<String>()
                
                for (i in permissions.indices) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        deniedPermissions.add(permissions[i])
                    }
                }
                
                if (deniedPermissions.isNotEmpty()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("İzin Gerekli")
                        .setMessage("Uygulamanın düzgün çalışması için gerekli izinler verilmedi.")
                        .setPositiveButton("Tamam", null)
                        .show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "İzin kontrolü hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    data class ConnectionInfo(
        val serverUrl: String,
        val socketUrl: String
    )
}