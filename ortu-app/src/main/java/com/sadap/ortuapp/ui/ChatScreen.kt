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
import com.sadap.template.models.ChatMessage
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

class ChatScreen : AppCompatActivity() {

    private lateinit var chatHistoryTextView: TextView
    private lateinit var messageEditText: EditText
    private lateinit var deviceId: String
    private val chatMessages = mutableListOf<ChatMessage>()
    private val handler = Handler(Looper.getMainLooper())
    private val chatRefresher = object : Runnable {
        override fun run() {
            fetchChatHistory()
            handler.postDelayed(this, 3 * 1000) // Refresh chat setiap 3 detik
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_ortu)

        deviceId = intent.getStringExtra("device_id") ?: ""
        if (deviceId.isEmpty()) {
            Toast.makeText(this, "Device ID tidak ditemukan.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatHistoryTextView = findViewById(R.id.chatHistoryTextView)
        messageEditText = findViewById(R.id.messageEditText)
        val sendButton: Button = findViewById(R.id.sendButton)

        sendButton.setOnClickListener {
            val message = messageEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessageToChild(message)
                messageEditText.text.clear()
            } else {
                Toast.makeText(this, "Pesan tidak boleh kosong.", Toast.LENGTH_SHORT).show()
            }
        }

        title = "Obrolan dengan $deviceId" // Set judul activity
        handler.post(chatRefresher) // Mulai refresh chat
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(chatRefresher)
    }

    private fun fetchChatHistory() {
        thread {
            val response = C2Client.get("/get_chat_history", mapOf("device_id" to deviceId))
            runOnUiThread {
                if (response != null) {
                    try {
                        val json = JSONObject(response)
                        val chatsArray = json.optJSONArray("chats")
                        chatsArray?.let {
                            val newMessages = mutableListOf<ChatMessage>()
                            for (i in 0 until it.length()) {
                                val chatJson = it.getJSONObject(i)
                                newMessages.add(ChatMessage(
                                    chatJson.optString("device_id"),
                                    chatJson.optString("sender"),
                                    chatJson.optString("message"),
                                    chatJson.optString("timestamp")
                                ))
                            }
                            if (newMessages.size != chatMessages.size || newMessages.lastOrNull() != chatMessages.lastOrNull()) {
                                chatMessages.clear()
                                chatMessages.addAll(newMessages.sortedBy { msg -> msg.timestamp })
                                updateChatUI()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatScreen", "JSON parsing error for chat history: ${e.message}")
                    }
                } else {
                    Log.e("ChatScreen", "Gagal mengambil histori chat.")
                }
            }
        }
    }

    private fun sendMessageToChild(message: String) {
        val json = JSONObject().apply {
            put("device_id", deviceId)
            put("command", "CHAT_RECEIVE") // Perintah untuk memicu penerimaan chat
            put("chat_message", message)
            put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        }

        thread {
            val response = C2Client.post("/send_command", json) // Kirim via send_command
            runOnUiThread {
                if (response != null && response.contains("Perintah diatur")) {
                    Toast.makeText(this, "Pesan terkirim.", Toast.LENGTH_SHORT).show()
                    fetchChatHistory() // Refresh untuk melihat pesan yang baru dikirim
                } else {
                    Toast.makeText(this, "Gagal mengirim pesan: $response", Toast.LENGTH_LONG).show()
                    Log.e("ChatScreen", "Failed to send chat command: $response")
                }
            }
        }
    }

    private fun updateChatUI() {
        val builder = StringBuilder()
        chatMessages.forEach { chat ->
            val timestamp = LocalDateTime.parse(chat.timestamp).format(DateTimeFormatter.ofPattern("HH:mm"))
            val senderLabel = if (chat.sender == "anak") "Anak" else "Anda"
            builder.append("$senderLabel ($timestamp): ${chat.message}\n")
        }
        chatHistoryTextView.text = builder.toString()
    }
}
