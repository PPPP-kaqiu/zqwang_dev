package com.flashpick.app.data.repository

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object AsrRepository {
    private const val TAG = "AsrRepository"
    
    // User credentials
    private const val APP_ID = "8819568573"
    private const val ACCESS_TOKEN = "gADSQs0HjZRjEagJjuB_bP7quOuQ38Gy"
    
    // One Sentence ASR Endpoint (Supports direct binary upload)
    // Note: If this fails, we might need to fallback or ask user for OSS
    private const val URL = "https://openspeech.bytedance.com/api/v1/asr"
    private const val RESOURCE_ID = "volc.asr.sauc" // Standard Short Audio

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // ASR can take time
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun transcribe(audioFile: File): String? {
        if (!audioFile.exists() || audioFile.length() == 0L) return null

        try {
            val reqId = UUID.randomUUID().toString()
            
            // Specify format via query params if possible, or header
            // Common Volcengine params: appid, format, transport=https
            val urlWithParams = "$URL?appid=$APP_ID&format=aac&rate=16000&channel=1"
            
            val body = audioFile.asRequestBody("audio/mp4".toMediaType()) // .m4a usually container mp4, codec aac

            val request = Request.Builder()
                .url(urlWithParams)
                .header("Authorization", "Bearer; $ACCESS_TOKEN")
                .header("X-Api-Resource-Id", RESOURCE_ID)
                .header("X-Api-Request-Id", reqId)
                .header("X-Api-App-Key", APP_ID)
                .post(body)
                .build()

            Log.d(TAG, "Sending ASR Request: $reqId size=${audioFile.length()}")
            val response = client.newCall(request).execute()
            val respBody = response.body?.string()
            
            Log.d(TAG, "ASR Response ($reqId): code=${response.code} body=$respBody")

            if (response.isSuccessful && !respBody.isNullOrEmpty()) {
                val json = JSONObject(respBody)
                // Parse: { "result": [{ "text": "..." }] }
                // Or: { "text": "..." } depending on version
                
                if (json.has("result")) {
                    val result = json.getJSONArray("result")
                    if (result.length() > 0) {
                        return result.getJSONObject(0).getString("text")
                    }
                } else if (json.has("text")) {
                    return json.getString("text")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ASR Error", e)
        }
        return null
    }
}

