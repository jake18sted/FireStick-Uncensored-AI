package com.example.firestickai

import android.content.Context
import android.net.Uri
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class VeniceApiClient(private val apiKey: String, private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun uriToBase64(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
            }
        } catch (e: Exception) { null }
    }

    private fun saveBytes(bytes: ByteArray, ext: String): File? {
        return try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, "result_${System.currentTimeMillis()}.$ext")
            FileOutputStream(file).use { it.write(bytes) }
            file
        } catch (e: Exception) { null }
    }

    fun editImage(prompt: String, imageUri: Uri?): File? {
        if (imageUri == null) return null
        val base64 = uriToBase64(imageUri) ?: return null
        val json = JSONObject().apply {
            put("model", "qwen-edit-uncensored")
            put("prompt", prompt)
            put("image", base64)
            put("safe_mode", false)
        }
        val req = Request.Builder()
            .url("https://api.venice.ai/api/v1/image/edit")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                saveBytes(bytes, "jpg")
            }
        } catch (e: Exception) { null }
    }

    fun generateImage(prompt: String): File? {
        val json = JSONObject().apply {
            put("model", "flux-dev-uncensored") // or qwen-image or similar uncensored
            put("prompt", prompt)
            put("safe_mode", false)
            put("width", 1024)
            put("height", 1024)
        }
        val req = Request.Builder()
            .url("https://api.venice.ai/api/v1/image/generate")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                saveBytes(bytes, "jpg")
            }
        } catch (e: Exception) { null }
    }

    fun animateImage(prompt: String, imageUri: Uri?, duration: String = "5s"): File? {
        val dataUrl = if (imageUri != null) {
            val b64 = uriToBase64(imageUri) ?: return null
            "data:image/jpeg;base64,$b64"
        } else null

        val json = JSONObject().apply {
            put("model", if (dataUrl != null) "ovi-image-to-video" else "wan-2-7-text-to-video")
            put("prompt", prompt)
            if (dataUrl != null) put("image_url", dataUrl)
            put("duration", duration)
        }
        val req = Request.Builder()
            .url("https://api.venice.ai/api/v1/video/queue")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val j = JSONObject(resp.body?.string() ?: return null)
                val qid = j.optString("queue_id")
                val model = j.optString("model")
                if (qid.isBlank()) return null

                for (i in 1..40) {
                    Thread.sleep(5000)
                    val poll = JSONObject().apply {
                        put("model", model)
                        put("queue_id", qid)
                    }
                    val pr = Request.Builder()
                        .url("https://api.venice.ai/api/v1/video/retrieve")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(poll.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(pr).execute().use { prr ->
                        val ct = prr.header("Content-Type") ?: ""
                        if (prr.isSuccessful && (ct.contains("video") || ct.contains("octet"))) {
                            val bytes = prr.body?.bytes() ?: return null
                            return saveBytes(bytes, "mp4")
                        }
                    }
                }
                null
            }
        } catch (e: Exception) { null }
    }
}
