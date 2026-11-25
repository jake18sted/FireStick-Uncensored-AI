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

data class GenResult(val file: File? = null, val error: String? = null)

class VeniceApiClient(private val apiKey: String, private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun uriToBase64(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
    } catch (e: Exception) { null }

    private fun saveBytes(bytes: ByteArray, ext: String): File? = try {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "result_${System.currentTimeMillis()}.$ext")
        FileOutputStream(file).use { it.write(bytes) }
        file
    } catch (e: Exception) { null }

    private fun violationReason(resp: okhttp3.Response): String? {
        val blurred = resp.header("x-venice-is-blurred") == "true"
        val violation = resp.header("x-venice-is-content-violation") == "true"
        val adultViolation = resp.header("x-venice-is-adult-model-content-violation") == "true"
        return when {
            adultViolation -> "Adult-model content violation"
            violation -> "Content policy violation"
            blurred -> "Image was blurred (safe mode or policy)"
            else -> null
        }
    }

    private fun extractImageBytes(responseBody: String?, contentType: String?): ByteArray? {
        if (responseBody == null) return null
        if (contentType?.contains("image") == true) return responseBody.toByteArray()
        return try {
            val j = JSONObject(responseBody)
            val b64 = j.optJSONArray("images")?.optString(0)
                ?: j.optJSONObject("data")?.optString("b64_json")
                ?: j.optString("b64_json")
            if (!b64.isNullOrBlank()) Base64.decode(b64, Base64.DEFAULT) else null
        } catch (e: Exception) { null }
    }

    fun editImage(prompt: String, imageUri: Uri?): GenResult {
        if (imageUri == null) return GenResult(error = "No reference image")
        val base64 = uriToBase64(imageUri) ?: return GenResult(error = "Could not read reference image")
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
                val reason = violationReason(resp)
                if (reason != null) return GenResult(error = reason)
                if (!resp.isSuccessful) return GenResult(error = "HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                val bytes = extractImageBytes(resp.body?.string(), resp.header("Content-Type"))
                    ?: return GenResult(error = "No image data in response")
                val file = saveBytes(bytes, "jpg") ?: return GenResult(error = "Failed to save file")
                GenResult(file = file)
            }
        } catch (e: Exception) {
            GenResult(error = e.message ?: "Network/edit error")
        }
    }

    fun generateImage(prompt: String): GenResult {
        val json = JSONObject().apply {
            put("model", "lustify-v8")
            put("prompt", prompt)
            put("safe_mode", false)
            put("format", "jpeg")
            put("steps", 30)
        }
        val req = Request.Builder()
            .url("https://api.venice.ai/api/v1/image/generate")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val reason = violationReason(resp)
                if (reason != null) return GenResult(error = reason)
                if (!resp.isSuccessful) return GenResult(error = "HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                val bytes = extractImageBytes(resp.body?.string(), resp.header("Content-Type"))
                    ?: return GenResult(error = "No image data in response")
                val file = saveBytes(bytes, "jpg") ?: return GenResult(error = "Failed to save file")
                GenResult(file = file)
            }
        } catch (e: Exception) {
            GenResult(error = e.message ?: "Network/generate error")
        }
    }

    fun animateImage(prompt: String, imageUri: Uri?, duration: String = "5s"): GenResult {
        val isI2V = imageUri != null
        val model = if (isI2V) "minimax-h3-chili-image-to-video" else "minimax-h3-chili-text-to-video"
        val dataUrl = if (isI2V) {
            val b64 = uriToBase64(imageUri!!) ?: return GenResult(error = "Could not read reference image")
            "data:image/jpeg;base64,$b64"
        } else null

        val json = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            if (dataUrl != null) put("image_url", dataUrl)
            put("duration", duration)
            put("resolution", "768P")
            put("aspect_ratio", "16:9")
        }
        val req = Request.Builder()
            .url("https://api.venice.ai/api/v1/video/queue")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return GenResult(error = "Queue failed HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                val j = JSONObject(resp.body?.string() ?: return GenResult(error = "Empty queue response"))
                val qid = j.optString("queue_id")
                val usedModel = j.optString("model", model)
                if (qid.isBlank()) return GenResult(error = "No queue_id returned")

                for (i in 1..60) {
                    Thread.sleep(5000)
                    val poll = JSONObject().apply {
                        put("model", usedModel)
                        put("queue_id", qid)
                    }
                    val pr = Request.Builder()
                        .url("https://api.venice.ai/api/v1/video/retrieve")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(poll.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(pr).execute().use { prr ->
                        val reason = violationReason(prr)
                        if (reason != null) return GenResult(error = reason)
                        val ct = prr.header("Content-Type") ?: ""
                        if (prr.isSuccessful && (ct.contains("video") || ct.contains("octet-stream"))) {
                            val bytes = prr.body?.bytes() ?: return GenResult(error = "Empty video body")
                            val file = saveBytes(bytes, "mp4") ?: return GenResult(error = "Failed to save video")
                            return GenResult(file = file)
                        }
                    }
                }
                GenResult(error = "Timed out after 5 minutes (video still processing or failed)")
            }
        } catch (e: Exception) {
            GenResult(error = e.message ?: "Video network error")
        }
    }
}
