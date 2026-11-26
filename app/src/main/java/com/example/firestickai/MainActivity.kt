package com.example.firestickai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.File

class MainActivity : Activity() {
    private val selectedImageUris = mutableListOf<Uri>()
    private val REQUEST_PICK = 1001
    private val prefs by lazy { getSharedPreferences("venice", 0) }
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var resultsContainer: LinearLayout
    private val results = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadResults()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(24, 24, 24, 24)
        }
        val scroll = ScrollView(this).apply { addView(root) }

        val title = TextView(this).apply {
            text = "Uncensored AI (Venice) v1.11"
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
        }
        status = TextView(this).apply {
            text = "Ready. Seedream V5.5 / Seedance 2.5. Select 1+ references or prompt only."
            textSize = 16f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, 8, 0, 16)
        }
        preview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(400, 300).apply { bottomMargin = 16 }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF222222.toInt())
            isFocusable = true
            setOnClickListener { if (selectedImageUris.isNotEmpty()) openViewer(selectedImageUris.first(), false) }
        }

        fun btn(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }

        resultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(title)
        root.addView(status)
        root.addView(preview)
        root.addView(btn("Select / Upload References (multi)") { pickImages() })
        root.addView(btn("Clear References") { selectedImageUris.clear(); preview.setImageDrawable(null); status.text = "References cleared" })
        root.addView(btn("Set Venice API Key") { setKey() })
        root.addView(btn("Edit Image (with reference(s))") { doEdit() })
        root.addView(btn("Animate Video (with reference(s) - Seedance)") { doAnimate(true) })
        root.addView(btn("Generate Image (prompt only - Seedream)") { doGenerateImage() })
        root.addView(btn("Generate Video (prompt only - Seedance)") { doAnimate(false) })
        root.addView(TextView(this).apply {
            text = "Saved Results (click video for MX Player, image for viewer):"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 16, 0, 8)
        })
        root.addView(resultsContainer)

        setContentView(scroll)
        refreshResultsUI()
    }

    private fun loadResults() {
        val json = prefs.getString("results", "[]") ?: "[]"
        try {
            val arr = JSONArray(json)
            results.clear()
            for (i in 0 until arr.length()) {
                val f = File(arr.getString(i))
                if (f.exists()) results.add(f)
            }
        } catch (e: Exception) {}
    }

    private fun saveResults() {
        val arr = JSONArray()
        results.forEach { arr.put(it.absolutePath) }
        prefs.edit().putString("results", arr.toString()).apply()
    }

    private fun refreshResultsUI() {
        resultsContainer.removeAllViews()
        results.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(200, 150)
                scaleType = ImageView.ScaleType.CENTER_CROP
                isFocusable = true
                if (file.name.endsWith("jpg") || file.name.endsWith("jpeg") || file.name.endsWith("png")) {
                    setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                } else {
                    setBackgroundColor(0xFF444444.toInt())
                }
                setOnClickListener {
                    if (file.name.endsWith("mp4")) {
                        playInMxPlayer(file)
                    } else {
                        openViewer(Uri.fromFile(file), false)
                    }
                }
            }
            val saveBtn = Button(this).apply {
                text = "Save"
                isFocusable = true
                setOnClickListener { saveToDownloads(file) }
            }
            row.addView(thumb)
            row.addView(saveBtn)
            resultsContainer.addView(row)
        }
    }

    private fun playInMxPlayer(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val packages = listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad")
        for (pkg in packages) {
            try {
                intent.setPackage(pkg)
                startActivity(intent)
                return
            } catch (_: Exception) { /* try next */ }
        }

        intent.setPackage(null)
        startActivity(Intent.createChooser(intent, "Play video"))
    }

    private fun pickImages() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(Intent.createChooser(intent, "Select references"), REQUEST_PICK)
        } catch (e: Exception) {
            Toast.makeText(this, "No picker", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK && resultCode == RESULT_OK) {
            selectedImageUris.clear()
            data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { selectedImageUris.add(it) }
                }
            } ?: data?.data?.let { selectedImageUris.add(it) }

            if (selectedImageUris.isNotEmpty()) {
                try {
                    contentResolver.openInputStream(selectedImageUris.first())?.use { stream ->
                        preview.setImageBitmap(BitmapFactory.decodeStream(stream))
                    }
                } catch (e: Exception) {}
                status.text = "${selectedImageUris.size} reference(s) selected"
            }
        }
    }

    private fun setKey() {
        val input = EditText(this).apply { hint = "Venice API key" }
        AlertDialog.Builder(this)
            .setTitle("API Key")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("key", input.text.toString().trim()).apply()
                status.text = "Key saved"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun key(): String? = prefs.getString("key", null)?.takeIf { it.isNotBlank() }

    private fun doEdit() {
        val k = key() ?: return toast("Set key first")
        if (selectedImageUris.isEmpty()) return toast("Select reference first")
        val input = EditText(this).apply { setText("high quality, keep person") }
        AlertDialog.Builder(this)
            .setTitle("Edit prompt")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val p = input.text.toString()
                status.text = "Editing..."
                CoroutineScope(Dispatchers.IO).launch {
                    val client = VeniceApiClient(k, this@MainActivity)
                    val result = client.editImage(p, selectedImageUris)
                    withContext(Dispatchers.Main) {
                        if (result.file != null) {
                            addResult(result.file)
                        } else {
                            status.text = "Failed: ${result.error ?: "Unknown error"}"
                            toast(result.error ?: "Edit failed")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doGenerateImage() {
        val k = key() ?: return toast("Set key first")
        val input = EditText(this).apply { hint = "Image prompt" }
        AlertDialog.Builder(this)
            .setTitle("Generate image (Seedream V5.5)")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val p = input.text.toString()
                status.text = "Generating image..."
                CoroutineScope(Dispatchers.IO).launch {
                    val client = VeniceApiClient(k, this@MainActivity)
                    val result = client.generateImage(p)
                    withContext(Dispatchers.Main) {
                        if (result.file != null) {
                            addResult(result.file)
                        } else {
                            status.text = "Failed: ${result.error ?: "Unknown error"}"
                            toast(result.error ?: "Generate failed")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doAnimate(withRef: Boolean) {
        val k = key() ?: return toast("Set key first")
        if (withRef && selectedImageUris.isEmpty()) return toast("Select reference first")
        val input = EditText(this).apply { hint = "Motion prompt" }
        val dur = arrayOf("5s", "8s", "10s", "15s")
        var selectedDur = "5s"
        AlertDialog.Builder(this)
            .setTitle(if (withRef) "Animate (Seedance 2.5)" else "Generate video (Seedance prompt only)")
            .setView(input)
            .setSingleChoiceItems(dur, 0) { _, which -> selectedDur = dur[which] }
            .setPositiveButton("Go") { _, _ ->
                val p = input.text.toString()
                status.text = "Queueing video..."
                CoroutineScope(Dispatchers.IO).launch {
                    val client = VeniceApiClient(k, this@MainActivity)
                    val refs = if (withRef) selectedImageUris else emptyList()
                    val result = client.animateImage(p, refs, selectedDur)
                    withContext(Dispatchers.Main) {
                        if (result.file != null) {
                            addResult(result.file)
                        } else {
                            status.text = "Failed: ${result.error ?: "Unknown error"}"
                            toast(result.error ?: "Video failed")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addResult(file: File) {
        results.add(0, file)
        saveResults()
        status.text = "Done: ${file.name}"
        refreshResultsUI()
    }

    private fun openViewer(uri: Uri, isVideo: Boolean) {
        val intent = Intent(this, ViewerActivity::class.java).apply {
            putExtra("uri", uri.toString())
            putExtra("video", isVideo)
        }
        startActivity(intent)
    }

    private fun saveToDownloads(file: File) {
        try {
            val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val dest = File(downloads, file.name)
            file.copyTo(dest, overwrite = true)
            Toast.makeText(this, "Saved to Downloads: ${dest.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
