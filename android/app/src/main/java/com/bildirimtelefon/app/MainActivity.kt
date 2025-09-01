package com.bildirimtelefon.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bildirimtelefon.app.databinding.ActivityMainBinding
import com.bildirimtelefon.app.service.NotificationService
import com.bildirimtelefon.app.utils.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceManager: PreferenceManager
    private var textToSpeech: TextToSpeech? = null
    private val PERMISSION_REQUEST_CODE = 1001

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleQRResult(result.contents)
        } else {
            Toast.makeText(this, "QR kod taraması iptal edildi", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferenceManager = PreferenceManager(this)
        textToSpeech = TextToSpeech(this, this)
        
        setupUI()
        checkPermissions()
        updateConnectionStatus()
    }

    private fun setupUI() {
        // Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Bildirim Telefon"
        
        // Cihaz bilgileri
        binding.deviceNameText.text = "${Build.MANUFACTURER} ${Build.MODEL}"
        binding.androidVersionText.text = "Android ${Build.VERSION.RELEASE}"
        
        // Buton click listeners
        binding.connectButton.setOnClickListener {
            if (preferenceManager.isConnected()) {
                disconnectFromServer()
            } else {
                startQRScanner()
            }
        }
        
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.testNotificationButton.setOnClickListener {
            testNotification()
        }
        
        binding.testVoiceButton.setOnClickListener {
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
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("QR kodu tarayın")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(true)
            setOrientationLocked(false)
        }
        qrScannerLauncher.launch(options)
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
            Toast.makeText(this, "Geçersiz QR kod", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToServer(connectionInfo: ConnectionInfo) {
        preferenceManager.saveConnectionInfo(connectionInfo)
        startNotificationService()
        updateConnectionStatus()
        Toast.makeText(this, "Sunucuya bağlanıyor...", Toast.LENGTH_SHORT).show()
    }

    private fun disconnectFromServer() {
        stopNotificationService()
        preferenceManager.clearConnectionInfo()
        updateConnectionStatus()
        Toast.makeText(this, "Sunucu bağlantısı kesildi", Toast.LENGTH_SHORT).show()
    }

    private fun startNotificationService() {
        val intent = Intent(this, NotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopNotificationService() {
        val intent = Intent(this, NotificationService::class.java)
        stopService(intent)
    }

    private fun isServiceRunning(): Boolean {
        return NotificationService.isRunning
    }

    private fun updateConnectionStatus() {
        val isConnected = preferenceManager.isConnected()
        val serverUrl = preferenceManager.getServerUrl()
        
        if (isConnected && serverUrl.isNotEmpty()) {
            binding.connectionStatusText.text = "Bağlı: $serverUrl"
            binding.connectionStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.connectButton.text = "Bağlantıyı Kes"
            binding.connectButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            binding.testNotificationButton.isEnabled = true
            binding.testVoiceButton.isEnabled = true
        } else {
            binding.connectionStatusText.text = "Bağlı değil"
            binding.connectionStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.connectButton.text = "QR Kod Tarat"
            binding.connectButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_bright))
            binding.testNotificationButton.isEnabled = false
            binding.testVoiceButton.isEnabled = false
        }
    }

    private fun testNotification() {
        val intent = Intent("com.bildirimtelefon.app.TEST_NOTIFICATION")
        intent.putExtra("title", "Test Bildirimi")
        intent.putExtra("message", "Bu bir test bildirimidir.")
        intent.putExtra("urgent", false)
        sendBroadcast(intent)
        
        Toast.makeText(this, "Test bildirimi gönderildi", Toast.LENGTH_SHORT).show()
    }

    private fun testVoiceMessage() {
        if (textToSpeech?.isSpeaking == false) {
            textToSpeech?.speak(
                "Bu bir test sesli mesajıdır. Bildirim telefon sistemi çalışıyor.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "test_message"
            )
            Toast.makeText(this, "Test sesli mesajı çalıyor", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Sesli mesaj zaten çalıyor", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("tr", "TR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Türkçe desteklenmiyorsa İngilizce kullan
                textToSpeech?.setLanguage(Locale.ENGLISH)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateConnectionStatus()
    }

    override fun onDestroy() {
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
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
                    .setMessage("Uygulamanın düzgün çalışması için gerekli izinler verilmedi. Ayarlardan izinleri aktifleştirebilirsiniz.")
                    .setPositiveButton("Tamam", null)
                    .show()
            }
        }
    }

    data class ConnectionInfo(
        val serverUrl: String,
        val socketUrl: String
    )
}