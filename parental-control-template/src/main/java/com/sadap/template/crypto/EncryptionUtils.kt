package com.sadap.template.crypto

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.Key
import java.security.NoSuchAlgorithmException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {

    private const val ALGORITHM = "AES"
    private const val TRANSFORM = "AES" // Untuk mode default (ECB, PKCS5Padding)
    private const val KEY_SIZE_BITS = 256 // AES-256

    // Menghasilkan kunci AES baru
    @Throws(NoSuchAlgorithmException::class)
    fun generateAESKey(): Key {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(KEY_SIZE_BITS)
        return keyGen.generateKey()
    }

    // Mengubah kunci menjadi string Base64
    fun keyToBase64(key: Key): String {
        return Base64.encodeToString(key.encoded, Base64.DEFAULT)
    }

    // Mengubah string Base64 menjadi kunci
    fun base64ToKey(base64Key: String): Key {
        val decodedKey = Base64.decode(base64Key, Base64.DEFAULT)
        return SecretKeySpec(decodedKey, ALGORITHM)
    }

    // Enkripsi atau dekripsi file
    @Throws(Exception::class)
    fun processFile(cipherMode: Int, key: Key, inputFile: File, outputFile: File) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(cipherMode, key)

        FileInputStream(inputFile).use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(4096) // 4KB buffer
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val output = cipher.update(buffer, 0, bytesRead)
                    if (output != null) {
                        outputStream.write(output)
                    }
                }
                val output = cipher.doFinal()
                if (output != null) {
                    outputStream.write(output)
                }
            }
        }
    }
}
