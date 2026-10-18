package com.akt.cameraplus

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("stamp_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 22)
            setBackgroundColor(Color.rgb(12, 16, 23))
        }
        root.addView(TextView(this).apply {
            text = "Camera+ Settings"
            textSize = 25f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Choose the details printed on your photo"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 6, 0, 18)
        })

        val options = listOf(
            "stamp_date" to "Date and time",
            "stamp_gps" to "GPS coordinates",
            "stamp_address" to "Readable address",
            "stamp_qr" to "Add Maps QR code to photo (scan to open location)",
            "stamp_brand" to "Hand-sketched mark + App by AKT"
        )
        options.forEach { (key, label) ->
            val check = CheckBox(this).apply {
                text = label
                textSize = 14f
                setTextColor(Color.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(105, 220, 190))
                isChecked = prefs.getBoolean(key, true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
            }
            root.addView(check)
        }

        root.addView(TextView(this).apply {
            text = "Watermark text color"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 14, 0, 5)
        })
        val colors = arrayOf("White", "Warm yellow", "Mint")
        val colorSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, colors)
            setSelection(prefs.getInt("stamp_color", 0))
        }
        colorSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putInt("stamp_color", position).apply()
            }
        }
        root.addView(colorSpinner)

        root.addView(TextView(this).apply {
            text = "Photo effect"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 14, 0, 5)
        })
        val effects = arrayOf("Natural", "Black & white", "Sepia")
        val effectSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, effects)
            setSelection(prefs.getInt("effect", 0))
        }
        effectSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putInt("effect", position).apply()
            }
        }
        root.addView(effectSpinner)

        root.addView(Button(this).apply {
            text = "Save / Done"
            setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
