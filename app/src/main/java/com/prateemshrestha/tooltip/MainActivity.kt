package com.prateemshrestha.tooltip

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val listener = View.OnClickListener { v ->
            val location = if (v.id == R.id.top) "top" else "bottom"
            Tooltip.show(this, "Tooltip for $location button.", v)
        }

        findViewById<Button>(R.id.top).setOnClickListener(listener)
        findViewById<Button>(R.id.bottom).setOnClickListener(listener)
    }
}
