package com.akt.cameraplus

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.graphics.Color

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("stamp_prefs", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        root.addView(TextView(this).apply { text = "Stamp customization"; textSize=24f; setTextColor(Color.BLACK) })
        root.addView(TextView(this).apply { text = "Choose which details appear on the photo"; setTextColor(Color.DKGRAY); setPadding(0,8,0,16) })
        val opts = listOf(
            "stamp_date" to "Date and time",
            "stamp_gps" to "GPS coordinates",
            "stamp_address" to "Address",
            "stamp_qr" to "Google Maps QR code",
            "stamp_brand" to "Small “App by AKT” footer"
        )
        opts.forEach { (key,label) ->
            val cb = CheckBox(this).apply {
                text=label; isChecked=prefs.getBoolean(key, true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
            }
            root.addView(cb)
        }
        root.addView(TextView(this).apply { text="Stamp text color"; setPadding(0,12,0,4); setTextColor(Color.BLACK) })
        val colors = arrayOf("White","Yellow","Mint")
        val spinner = Spinner(this).apply { adapter=ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, colors) }
        spinner.setSelection(prefs.getInt("stamp_color",0))
        spinner.onItemSelectedListener = object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { prefs.edit().putInt("stamp_color",position).apply() }
        }
        root.addView(spinner)
        root.addView(TextView(this).apply { text="Photo color effect (applied after capture)"; setPadding(0,12,0,4); setTextColor(Color.BLACK) })
        val effects = arrayOf("Natural","Black & White","Sepia")
        val effect = Spinner(this).apply { adapter=ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, effects) }
        effect.setSelection(prefs.getInt("effect",0))
        effect.onItemSelectedListener = object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { prefs.edit().putInt("effect",position).apply() }
        }
        root.addView(effect)
        root.addView(Button(this).apply { text="Done"; setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
