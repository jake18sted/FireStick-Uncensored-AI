package com.example.firestickai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private var selectedImageUri: Uri? = null
    private val REQUEST_PICK_IMAGE = 1001
    private val prefs by lazy { getSharedPreferences("spicy", 0) }
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF000000.toInt())
                setPadding(32, 32, 32, 32)
            }
            val scroll = ScrollView(this).apply { addView(root) }

            val title = TextView(this).apply {
                text = "Uncensored AI"
                textSize = 28f
                setTextColor(0xFFFFFFFF.toInt())
            }
            status = TextView(this).apply {
                text = "Ready. Select a reference or set your API key."
                textSize = 18f
                setTextColor(0xFFCCCCCC.toInt())
                setPadding(0, 16, 0, 24)
            }

            fun makeButton(label: String, onClick: () -> Unit) = Button(this).apply {
                text = label
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 16 }
            }

            root.addView(title)
            root.addView(status)
            root.addView(makeButton("Select / Upload Reference") { pickImage() })
            root.addView(makeButton("Set API Key") { promptForApiKey() })
            root.addView(makeButton("Create Image") { promptAndGenerate("image") })
            root.addView(makeButton("Animate This") { promptAndGenerate("video") })

            setContentView(scroll)
        } catch (e: Exception) {
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickImage() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "Select Reference"), REQUEST_PICK_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker found. Put images in Downloads.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let {
                selectedImageUri = it
                status.text = "Reference selected"
                Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun promptForApiKey() {
        val input = EditText(this).apply { hint = "Paste SpicyAPI key" }
        AlertDialog.Builder(this)
            .setTitle("SpicyAPI Key")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("key", input.text.toString().trim()).apply()
                status.text = "API key saved"
                Toast.makeText(this, "Key saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptAndGenerate(mode: String) {
        val key = prefs.getString("key", null)
        if (key.isNullOrBlank()) {
            Toast.makeText(this, "Set API key first", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedImageUri == null && mode == "image") {
            Toast.makeText(this, "Select a reference image first", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            hint = "Enter prompt"
            setText("edit this image, keep the person, high quality")
        }
        AlertDialog.Builder(this)
            .setTitle("Prompt for $mode")
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                val prompt = input.text.toString().trim()
                if (prompt.isNotBlank()) generate(mode, prompt, key)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generate(mode: String, prompt: String, key: String) {
        status.text = "Sending to SpicyAPI..."
        CoroutineScope(Dispatchers.IO).launch {
            val client = SpicyApiClient(key)
            val resultUrl = client.generateImage(prompt, if (selectedImageUri != null) "https://example.com/ref.jpg" else null)
            withContext(Dispatchers.Main) {
                if (resultUrl != null) {
                    status.text = "Done: $resultUrl"
                    Toast.makeText(this@MainActivity, "Generation finished", Toast.LENGTH_LONG).show()
                } else {
                    status.text = "Failed — check key or balance"
                    Toast.makeText(this@MainActivity, "Generation failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
