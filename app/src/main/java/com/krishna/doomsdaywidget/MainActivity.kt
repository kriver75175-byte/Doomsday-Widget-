package com.krishna.doomsdaywidget

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this)
        t.text = "Doomsday widget installed.\n\nHome screen par long-press -> Widgets -> Doomsday Widget."
        t.textSize = 18f
        t.setPadding(48, 160, 48, 48)
        setContentView(t)
    }
}
