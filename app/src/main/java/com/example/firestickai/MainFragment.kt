package com.example.firestickai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter

class MainFragment : BrowseSupportFragment() {
    private var selectedImageUri: Uri? = null

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
        onItemViewClickedListener = OnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            when (item) {
                "Select / Upload Reference" -> imagePicker.launch("image/*")
                "Create Image" -> startGenerate("image")
                "Animate This" -> startGenerate("video")
                "Playlist" -> Toast.makeText(requireContext(), "Playlist coming next", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRows() {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val header = HeaderItem(0, "Actions")
        val listRowAdapter = ArrayObjectAdapter()
        listRowAdapter.add("Select / Upload Reference")
        listRowAdapter.add("Create Image")
        listRowAdapter.add("Animate This")
        listRowAdapter.add("Playlist")
        rowsAdapter.add(ListRow(header, listRowAdapter))
        adapter = rowsAdapter
    }

    private fun startGenerate(mode: String) {
        if (selectedImageUri == null && mode == "image") {
            Toast.makeText(requireContext(), "Select a reference image first", Toast.LENGTH_SHORT).show()
            return
        }
        // Placeholder — next step adds prompt dialog + SpicyAPI call
        Toast.makeText(requireContext(), "Ready for prompt + $mode generation (API key needed)", Toast.LENGTH_LONG).show()
    }
}
