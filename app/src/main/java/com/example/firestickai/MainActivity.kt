package com.example.firestickai

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF000000.toInt())
                setPadding(48, 48, 48, 48)
            }
            val title = TextView(this).apply {
                text = "Uncensored AI - Running"
                textSize = 32f
                setTextColor(0xFFFFFFFF.toInt())
            }
            val info = TextView(this).apply {
                text = "If you see this, the app launched successfully.\nNext version will add image picker and generation."
                textSize = 20f
                setTextColor(0xFFCCCCCC.toInt())
                setPadding(0, 32, 0, 0)
            }
            layout.addView(title)
            layout.addView(info)
            setContentView(layout)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
