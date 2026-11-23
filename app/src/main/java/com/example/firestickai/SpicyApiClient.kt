package com.example.firestickai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SpicyApiClient(private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun generateImage(prompt: String, imageUrl: String? = null): String? {
        val json = JSONObject().apply {
            put("model", if (imageUrl != null) "spicy-image-edit-1" else "spicy-image-1")
            put("prompt", prompt)
            put("n", 1)
            if (imageUrl != null) put("image_url", imageUrl)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.spicyapi.com/v1/images/${if (imageUrl != null) "edits" else "generations"}")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string() ?: "")
                    respJson.optJSONArray("data")?.optJSONObject(0)?.optString("url")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
