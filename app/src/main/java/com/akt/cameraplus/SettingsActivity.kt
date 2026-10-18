package com.akt.cameraplus

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.*

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("stamp_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
            setBackgroundColor(Color.rgb(12, 16, 23))
        }
        root.addView(TextView(this).apply {
            text = "Camera+ Settings"
            textSize = 25f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Customize the information and appearance printed on your photos"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(6), 0, dp(18))
        })

        // Branding is intentionally not a checkbox: sketch mark + App by AKT
        // remains on every stamped photo, as requested.
        val options = listOf(
            "stamp_date" to "Date and time",
            "stamp_gps" to "Latitude / longitude coordinates",
            "stamp_address" to "Readable address (shown first, with India flag)",
            "stamp_qr" to "Add Maps QR code to photo (scan to open location)"
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

        addSlider(root, "Stamp background opacity", 10, 100,
            prefs.getInt("stamp_opacity", 68), "% (higher = less transparent)", "stamp_opacity")
        addSlider(root, "Stamp text size", 12, 40,
            prefs.getInt("stamp_text_size", 23), " px scale", "stamp_text_size")

        root.addView(TextView(this).apply {
            text = "Watermark text color"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, dp(5))
        })
        val colors = arrayOf("White", "Warm yellow", "Mint")
        val colorSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, colors)
            setSelection(prefs.getInt("stamp_color", 0).coerceIn(colors.indices))
        }
        colorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("stamp_color", position).apply()
            }
        }
        root.addView(colorSpinner)

        root.addView(TextView(this).apply {
            text = "Stamp background colour"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, dp(5))
        })
        val backgroundColors = arrayOf("Deep charcoal", "Black", "White", "Dark green", "Navy blue")
        val backgroundSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, backgroundColors)
            setSelection(prefs.getInt("stamp_bg_color", 0).coerceIn(backgroundColors.indices))
        }
        backgroundSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("stamp_bg_color", position).apply()
            }
        }
        root.addView(backgroundSpinner)

        root.addView(TextView(this).apply {
            text = "Photo effect"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, dp(5))
        })
        val effects = arrayOf("Natural", "Black & white", "Sepia")
        val effectSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, effects)
            setSelection(prefs.getInt("effect", 0).coerceIn(effects.indices))
        }
        effectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("effect", position).apply()
            }
        }
        root.addView(effectSpinner)

        root.addView(Button(this).apply {
            text = "Save / Done"
            setOnClickListener { finish() }
        })

        // Use ViewGroup.LayoutParams constants explicitly to avoid Kotlin's
        // unresolved LinearLayout.LayoutParams.MATCH_PARENT reference.
        root.addView(View(this), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(TextView(this).apply {
            text = "Proudly Made in India with love 🇮🇳"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.rgb(115, 231, 202))
            setPadding(0, dp(18), 0, dp(8))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(scroll)
    }

    private fun addSlider(
        parent: LinearLayout,
        title: String,
        min: Int,
        max: Int,
        initial: Int,
        suffix: String,
        key: String
    ) {
        val label = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(3))
        }
        parent.addView(label)
        val slider = SeekBar(this).apply {
            this.max = max - min
            progress = initial.coerceIn(min, max) - min
            contentDescription = title
        }
        slider.progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(105, 220, 190))
        slider.thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(105, 220, 190))
        fun update(value: Int) {
            label.text = "$title: $value$suffix"
            prefs.edit().putInt(key, value).apply()
        }
        update(min + slider.progress)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                update(min + progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        parent.addView(slider)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
