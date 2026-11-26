package com.example.firestickai

import android.content.Context
import android.net.Uri
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class GenResult(val file: File? = null, val error: String? = null)

class VeniceApiClient(private val apiKey: String, private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
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
        val adult = resp.header("x-venice-is-adult-model-content-violation") == "true"
        return when {
            adult -> "Adult content policy violation"
            violation -> "Content policy violation"
            blurred -> "Content blurred by safe mode"
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

    fun editImage(prompt: String, imageUris: List<Uri>): GenResult {
        if (imageUris.isEmpty()) return GenResult(error = "No reference images")
        val base64s = imageUris.mapNotNull { uriToBase64(it) }
        if (base64s.isEmpty()) return GenResult(error = "Failed to read images")
        // Use first image for edit; Seedream edit supports multi via other endpoints but keep simple
        val json = JSONObject().apply {
            put("model", "seedream-v5-5-edit")
            put("prompt", prompt)
            put("image", base64s.first())
            put("safe_mode", false)
            put("format", "jpeg")
        }
        val req = Request.Builder()
            .url("https://api.venice.ai/api/v1/image/edit")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val reason = violationReason(resp)
                if (!resp.isSuccessful || reason != null) {
                    return GenResult(error = reason ?: "HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
                val bytes = extractImageBytes(resp.body?.string(), resp.header("Content-Type"))
                    ?: return GenResult(error = "No image data in response")
                GenResult(file = saveBytes(bytes, "jpg"))
            }
        } catch (e: Exception) { GenResult(error = e.message ?: "Edit exception") }
    }

    fun generateImage(prompt: String): GenResult {
        val json = JSONObject().apply {
            put("model", "seedream-v5-5")
            put("prompt", prompt)
            put("safe_mode", false)
            put("format", "jpeg")
            put("resolution", "1K")
            put("aspect_ratio", "1:1")
        }
        val req = Request.Builder()
            .url("https://api.venice.ai/api/v1/image/generate")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val reason = violationReason(resp)
                if (!resp.isSuccessful || reason != null) {
                    return GenResult(error = reason ?: "HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
                val bytes = extractImageBytes(resp.body?.string(), resp.header("Content-Type"))
                    ?: return GenResult(error = "No image data")
                GenResult(file = saveBytes(bytes, "jpg"))
            }
        } catch (e: Exception) { GenResult(error = e.message ?: "Generate exception") }
    }

    fun animateImage(prompt: String, imageUris: List<Uri>, duration: String = "5s"): GenResult {
        val hasRefs = imageUris.isNotEmpty()
        val model = when {
            !hasRefs -> "seedance-2-5-text-to-video-basic"
            imageUris.size == 1 -> "seedance-2-5-image-to-video-basic"
            else -> "seedance-2-5-reference-to-video-basic"
        }

        val json = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("duration", duration)
            put("resolution", "720p")
            put("aspect_ratio", "16:9")
            if (imageUris.size == 1) {
                val b64 = uriToBase64(imageUris.first()) ?: return GenResult(error = "Failed to encode image")
                put("image_url", "data:image/jpeg;base64,$b64")
            } else if (imageUris.size > 1) {
                val arr = JSONArray()
                imageUris.take(5).forEach { uri ->
                    uriToBase64(uri)?.let { arr.put("data:image/jpeg;base64,$it") }
                }
                if (arr.length() == 0) return GenResult(error = "Failed to encode references")
                put("reference_image_urls", arr)
            }
        }

        val req = Request.Builder()
            .url("https://api.venice.ai/api/v1/video/queue")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    return GenResult(error = "Queue failed HTTP ${resp.code}: ${body.take(300)}")
                }
                val j = JSONObject(resp.body?.string() ?: return GenResult(error = "Empty queue response"))
                val qid = j.optString("queue_id")
                val usedModel = j.optString("model", model)
                if (qid.isBlank()) return GenResult(error = "No queue_id returned")

                for (i in 1..80) {
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
                            return GenResult(file = saveBytes(bytes, "mp4"))
                        }
                        if (!prr.isSuccessful && prr.code != 202 && prr.code != 404) {
                            // continue polling on pending
                        }
                    }
                }
                GenResult(error = "Timeout after polling")
            }
        } catch (e: Exception) { GenResult(error = e.message ?: "Video exception") }
    }
}
