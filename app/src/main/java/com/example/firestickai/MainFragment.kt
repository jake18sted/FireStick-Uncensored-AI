package com.example.firestickai

import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter

class MainFragment : BrowseSupportFragment() {
    override fun onActivityCreated(savedInstanceState: android.os.Bundle?) {
        super.onActivityCreated(savedInstanceState)
        title = "Uncensored AI"
        setupRows()
    }

    private fun setupRows() {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val header = HeaderItem(0, "Actions")
        val listRowAdapter = ArrayObjectAdapter()
        listRowAdapter.add("Create Image")
        listRowAdapter.add("Animate This")
        listRowAdapter.add("Playlist")
        rowsAdapter.add(ListRow(header, listRowAdapter))
        adapter = rowsAdapter
    }
}
