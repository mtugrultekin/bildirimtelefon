package com.bildirimtelefon.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.CaptureActivity

class QRScanActivity : CaptureActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // QR tarama ayarları
        val intent = Intent()
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE")
        intent.putExtra("SCAN_FORMATS", "QR_CODE")
        intent.putExtra("PROMPT_MESSAGE", "QR kodu tarayın")
        intent.putExtra("BEEP_ENABLED", true)
        intent.putExtra("TIMEOUT", 30000L) // 30 saniye timeout
    }
    
    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}