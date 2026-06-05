package com.sadap.bocahapp.ui

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sadap.bocahapp.ChildDeviceManager
import com.sadap.bocahapp.R
import com.sadap.template.network.C2Client
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

class ChatActivity : AppCompatActivity() {

    private lateinit var chatTextView: TextView
    private lateinit var messageEditText: EditText
    private lateinit var deviceManager: ChildDeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatTextView = findViewById(R.id.chatTextView)
        messageEditText = findViewById(R.id.messageEditText)
        val sendButton: Button = findViewById(R.id.sendButton)

        deviceManager = ChildDeviceManager(this)

        sendButton.setOnClickListener {
            val message = messageEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessageToC2(message)
                messageEditText.text.clear()
            } else {
                Toast.makeText(this, "Pesan tidak boleh kosong.", Toast.LENGTH_SHORT).show()
            }
        }

        // Contoh: Ambil histori chat atau polling untuk pesan baru
        // Untuk demo ini, kita hanya akan menampilkan pesan yang dikirim dari sini.
        // Implementasi sebenarnya akan mem-polling C2 atau menggunakan Firebase/WebSocket.
    }

    private fun sendMessageToC2(message: String) {
        val deviceId = deviceManager.getDeviceId()
        val json = JSONObject().apply {
            put("device_id", deviceId)
            put("message", message)
            put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        }

        thread {
            val response = C2Client.post("/send_chat", json)
            runOnUiThread {
                if (response != null && response.contains("Pesan chat diterima")) {
                    // Update UI dengan pesan yang baru dikirim
                    val formattedMessage = "Me (${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}): $message\n"
                    chatTextView.append(formattedMessage)
                    Toast.makeText(this, "Pesan terkirim.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Gagal mengirim pesan: $response", Toast.LENGTH_LONG).show()
                    Log.e("ChatActivity", "Failed to send chat: $response")
                }
            }
        }
    }
}
