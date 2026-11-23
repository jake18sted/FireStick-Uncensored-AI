package com.example.firestickai

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainFragment : BrowseSupportFragment() {
    private var selectedImageUri: Uri? = null
    private val prefs by lazy { requireContext().getSharedPreferences("spicy", 0) }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            Toast.makeText(requireContext(), "Image selected: ${it.lastPathSegment}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        title = "Uncensored AI"
        setupRows()
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                "Select / Upload Reference" -> imagePicker.launch("image/*")
                "Set API Key" -> promptForApiKey()
                "Create Image" -> promptAndGenerate("image")
                "Animate This" -> promptAndGenerate("video")
                "Playlist" -> Toast.makeText(requireContext(), "Playlist next", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRows() {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val header = HeaderItem(0, "Actions")
        val listRowAdapter = ArrayObjectAdapter()
        listRowAdapter.add("Select / Upload Reference")
        listRowAdapter.add("Set API Key")
        listRowAdapter.add("Create Image")
        listRowAdapter.add("Animate This")
        listRowAdapter.add("Playlist")
        rowsAdapter.add(ListRow(header, listRowAdapter))
        adapter = rowsAdapter
    }

    private fun promptForApiKey() {
        val input = EditText(requireContext()).apply { hint = "SpicyAPI key" }
        AlertDialog.Builder(requireContext())
            .setTitle("SpicyAPI Key")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("key", input.text.toString()).apply()
                Toast.makeText(requireContext(), "Key saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptAndGenerate(mode: String) {
        val key = prefs.getString("key", null)
        if (key.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Set API key first", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedImageUri == null && mode == "image") {
            Toast.makeText(requireContext(), "Select a reference image first", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(requireContext()).apply {
            hint = "Enter prompt"
            setText("edit this image, keep the person")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Prompt for $mode")
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                val prompt = input.text.toString()
                if (prompt.isNotBlank()) generate(mode, prompt, key)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generate(mode: String, prompt: String, key: String) {
        Toast.makeText(requireContext(), "Sending to SpicyAPI...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            val client = SpicyApiClient(key)
            // Note: real upload of local URI requires hosting the image first; this uses a placeholder URL for now
            val resultUrl = client.generateImage(prompt, if (selectedImageUri != null) "https://example.com/ref.jpg" else null)
            withContext(Dispatchers.Main) {
                if (resultUrl != null) {
                    Toast.makeText(requireContext(), "Done: $resultUrl", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Failed — check key / balance / image URL", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
