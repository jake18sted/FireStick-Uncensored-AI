package com.example.firestickai

import android.content.Context
import android.net.Uri
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VeniceApiClient(private val apiKey: String, private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    fun editImage(prompt: String, imageUri: Uri?): String? {
        if (imageUri == null) return null
        val base64Image = uriToBase64(imageUri) ?: return null

        val json = JSONObject().apply {
            put("model", "qwen-edit-uncensored")
            put("prompt", prompt)
            put("image", base64Image)
            put("safe_mode", false)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.venice.ai/api/v1/image/edit")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val contentType = response.header("Content-Type") ?: ""
                    if (contentType.contains("image")) {
                        "venice-image-generated"
                    } else {
                        val respJson = JSONObject(response.body?.string() ?: "")
                        respJson.optString("url", respJson.optJSONArray("data")?.optJSONObject(0)?.optString("url"))
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun animateImage(prompt: String, imageUri: Uri?, duration: String = "5s"): String? {
        if (imageUri == null) return null
        val base64Image = uriToBase64(imageUri) ?: return null
        val dataUrl = "data:image/jpeg;base64,$base64Image"

        val json = JSONObject().apply {
            put("model", "ovi-image-to-video")
            put("prompt", prompt)
            put("image_url", dataUrl)
            put("duration", duration)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.venice.ai/api/v1/video/queue")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val respJson = JSONObject(response.body?.string() ?: "")
                val queueId = respJson.optString("queue_id")
                val model = respJson.optString("model", "ovi-image-to-video")
                if (queueId.isBlank()) return null

                // Poll for completion (up to ~2 minutes)
                for (i in 1..24) {
                    Thread.sleep(5000)
                    val pollJson = JSONObject().apply {
                        put("model", model)
                        put("queue_id", queueId)
                    }
                    val pollBody = pollJson.toString().toRequestBody("application/json".toMediaType())
                    val pollReq = Request.Builder()
                        .url("https://api.venice.ai/api/v1/video/retrieve")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(pollBody)
                        .build()
                    client.newCall(pollReq).execute().use { pollResp ->
                        val ct = pollResp.header("Content-Type") ?: ""
                        if (pollResp.isSuccessful && ct.contains("video")) {
                            return "venice-video-ready"
                        }
                        // If JSON status, continue polling
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            null
        }
    }
}
