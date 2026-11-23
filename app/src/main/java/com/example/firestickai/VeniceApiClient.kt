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
        .readTimeout(180, TimeUnit.SECONDS)
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
                    // Venice returns the image binary or a JSON with URL; handle both
                    val contentType = response.header("Content-Type") ?: ""
                    if (contentType.contains("image")) {
                        // Save binary and return a local note; for now return success marker
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
