package com.example.firestickai

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.VideoView

class ViewerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriStr = intent.getStringExtra("uri") ?: return finish()
        val isVideo = intent.getBooleanExtra("video", false)
        val uri = Uri.parse(uriStr)

        if (isVideo) {
            val vv = VideoView(this)
            setContentView(vv)
            vv.setVideoURI(uri)
            vv.setOnPreparedListener { it.start() }
            vv.setOnCompletionListener { /* loop or stop */ }
        } else {
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(0xFF000000.toInt())
            }
            setContentView(iv)
            try {
                contentResolver.openInputStream(uri)?.use {
                    iv.setImageBitmap(BitmapFactory.decodeStream(it))
                } ?: iv.setImageBitmap(BitmapFactory.decodeFile(uri.path))
            } catch (e: Exception) {}
        }
    }
}
