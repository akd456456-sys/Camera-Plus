package com.akt.cameraplus

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.effects.OverlayEffect
import androidx.camera.video.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var locationLine: TextView
    private lateinit var addressLine: TextView
    private lateinit var capture: Button
    private lateinit var mapButton: TextView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var videoStampEffect: OverlayEffect? = null
    private var activeRecording: Recording? = null
    private var videoMode = false
    private var camera: androidx.camera.core.Camera? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var address = "Address is resolving…"
    private var exposure = 0
    private var flash = false
    private var zoomRatio = 1f
    private val prefs by lazy { getSharedPreferences("stamp_prefs", MODE_PRIVATE) }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) startCamera()
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true || it[Manifest.permission.ACCESS_COARSE_LOCATION] == true) refreshLocation()
        else renderLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        buildUi()
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        else { startCamera(); refreshLocation() }
    }

    override fun onDestroy() {
        activeRecording?.stop()
        videoStampEffect?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::locationLine.isInitialized && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) refreshLocation()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 12, 18))
        }

        // Refined compact header with hand-sketched brand mark and utility controls
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(9), dp(12), dp(9))
        }
        val brand = TextView(this).apply {
            text = "✎  CAMERA+"
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(headerButton("↻", "Refresh GPS") { refreshLocation() })
        header.addView(headerButton("⚙", "Settings") { startActivity(android.content.Intent(this, SettingsActivity::class.java)) })
        root.addView(header)

        val stage = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        stage.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        // GPS info stays visible over the live camera, before taking a photo.
        val locationCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(10))
            background = rounded(Color.argb(205, 15, 20, 29), dp(16))
        }
        val locationHeading = TextView(this).apply {
            text = "⌖  LIVE LOCATION"
            textSize = 10f
            letterSpacing = .12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(115, 231, 202))
        }
        locationLine = TextView(this).apply {
            text = "Getting GPS coordinates…"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(2))
        }
        addressLine = TextView(this).apply {
            text = "Waiting for location fix"
            textSize = 11f
            setTextColor(Color.rgb(210, 218, 228))
            maxLines = 2
        }
        mapButton = TextView(this).apply {
            text = "OPEN IN MAPS  ↗"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(115, 231, 202))
            setPadding(0, dp(8), 0, 0)
            setOnClickListener { openMap() }
        }
        locationCard.addView(locationHeading)
        locationCard.addView(locationLine)
        locationCard.addView(addressLine)
        locationCard.addView(mapButton)
        val cardLp = FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        cardLp.setMargins(dp(12), 0, dp(12), dp(12))
        stage.addView(locationCard, cardLp)
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

        // Simple, thumb-friendly camera tools.
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(4))
        }
        tools.addView(toolButton(if (videoMode) "VIDEO" else "PHOTO", "Switch photo/video mode") {
            if (activeRecording != null) {
                Toast.makeText(this, "Stop recording before changing mode", Toast.LENGTH_SHORT).show()
            } else {
                videoMode = !videoMode
                capture.text = if (videoMode) "▶" else "●"
                capture.contentDescription = if (videoMode) "Start or stop video" else "Take photo"
                startCamera()
            }
        }, weightLp())
        tools.addView(toolButton(if (flash) "⚡ ON" else "⚡", "Flash") {
            flash = !flash
            imageCapture?.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            startCamera()
        }, weightLp())
        tools.addView(toolButton("−", "Zoom out") { setZoom((zoomRatio - .25f).coerceAtLeast(1f)) }, weightLp())
        tools.addView(toolButton("1×", "Reset zoom") { setZoom(1f) }, weightLp())
        tools.addView(toolButton("+", "Zoom in") { setZoom((zoomRatio + .25f).coerceAtMost(camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 4f)) }, weightLp())
        tools.addView(toolButton("⇄", "Switch camera") {
            if (activeRecording != null) {
                Toast.makeText(this, "Stop recording before switching camera", Toast.LENGTH_SHORT).show()
            } else {
                lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                startCamera()
            }
        }, weightLp())
        tools.addView(toolButton("▧", "Gallery") { openGallery() }, weightLp())
        root.addView(tools)

        val exposureRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(3))
        }
        exposureRow.addView(TextView(this).apply {
            text = "☼"
            textSize = 17f
            setTextColor(Color.WHITE)
        })
        val seek = SeekBar(this).apply {
            max = 12
            progress = 6
            contentDescription = "Exposure brightness"
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                exposure = p - 6
                camera?.cameraControl?.setExposureCompensationIndex(exposure)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        exposureRow.addView(seek, LinearLayout.LayoutParams(0, dp(30), 1f))
        root.addView(exposureRow)

        val shutterRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(2), dp(10), dp(12))
        }
        val gallery = TextView(this).apply {
            text = "▣\nGallery"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            setOnClickListener { openGallery() }
        }
        shutterRow.addView(gallery, LinearLayout.LayoutParams(0, dp(58), 1f))
        capture = Button(this).apply {
            text = "●"
            textSize = 30f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(26, 190, 160), dp(36))
            contentDescription = "Take photo"
            setOnClickListener {
                if (videoMode) toggleVideoRecording() else takePhoto()
            }
        }
        shutterRow.addView(capture, LinearLayout.LayoutParams(dp(76), dp(58)))
        val settings = TextView(this).apply {
            text = "⚙\nSettings"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            setOnClickListener { startActivity(android.content.Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        shutterRow.addView(settings, LinearLayout.LayoutParams(0, dp(58), 1f))
        root.addView(shutterRow)
        setContentView(root)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }

    private fun weightLp() = LinearLayout.LayoutParams(0, dp(43), 1f)

    private fun headerButton(label: String, desc: String, action: () -> Unit) = TextView(this).apply {
        text = label; contentDescription = desc; textSize = 22f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(42))
    }

    private fun toolButton(label: String, desc: String, action: () -> Unit) = TextView(this).apply {
        text = label; contentDescription = desc; textSize = 17f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); background = rounded(Color.rgb(27, 34, 45), dp(13))
        setOnClickListener { action() }
        layoutParams = weightLp()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .build()
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            try {
                provider.unbindAll()
                videoStampEffect?.close()
                // CameraX burns this dynamic GPS/address/date panel into every recorded video frame.
                val stampEffect = OverlayEffect(
                    CameraEffect.VIDEO_CAPTURE,
                    0,
                    Handler(Looper.getMainLooper())
                ) { error ->
                    runOnUiThread {
                        Toast.makeText(this, "Video stamp effect error: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
                stampEffect.setOnDrawListener { frame ->
                    drawVideoStamp(frame.overlayCanvas)
                    true
                }
                videoStampEffect = stampEffect

                val group = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageCapture!!)
                    .addUseCase(videoCapture!!)
                    .addEffect(stampEffect)
                    .build()
                camera = provider.bindToLifecycle(
                    this, CameraSelector.Builder().requireLensFacing(lens).build(), group
                )
                camera?.cameraControl?.setExposureCompensationIndex(exposure)
                camera?.cameraControl?.setZoomRatio(zoomRatio.coerceAtLeast(1f))
            } catch (e: Exception) {
                videoStampEffect?.close()
                videoStampEffect = null
                Toast.makeText(this, "Camera could not start: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setZoom(value: Float) {
        zoomRatio = value
        camera?.cameraControl?.setZoomRatio(value)
    }

    @Suppress("MissingPermission")
    private fun refreshLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            latitude = null; longitude = null
            address = "Allow location permission to show GPS"
            renderLocation()
            return
        }
        locationLine.text = "Requesting a fresh GPS fix…"
        addressLine.text = "Location can take a few seconds"
        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    latitude = loc.latitude; longitude = loc.longitude
                    address = "Address lookup…"
                    renderLocation()
                    reverseGeocode(loc.latitude, loc.longitude)
                } else {
                    client.lastLocation.addOnSuccessListener { old ->
                        if (old != null) {
                            latitude = old.latitude; longitude = old.longitude
                            address = "Address lookup…"
                            renderLocation()
                            reverseGeocode(old.latitude, old.longitude)
                        } else {
                            latitude = null; longitude = null
                            address = "No GPS fix yet. Enable Location and try outdoors."
                            renderLocation()
                        }
                    }.addOnFailureListener {
                        latitude = null; longitude = null
                        address = "Location unavailable. Check device Location settings."
                        renderLocation()
                    }
                }
            }
            .addOnFailureListener {
                address = "Location error. Check permission and GPS."
                renderLocation()
            }
    }

    private fun reverseGeocode(lat: Double, lon: Double) {
        if (!Geocoder.isPresent()) {
            address = "Address lookup not supported; coordinates are available"
            renderLocation(); return
        }
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(lat, lon, 1) { list: List<Address> ->
                    address = list.firstOrNull()?.getAddressLine(0)
                        ?: "Address unavailable offline; coordinates are available"
                    runOnUiThread { renderLocation() }
                }
            } else {
                @Suppress("DEPRECATION")
                val list = geocoder.getFromLocation(lat, lon, 1)
                address = list?.firstOrNull()?.getAddressLine(0)
                    ?: "Address unavailable offline; coordinates are available"
                renderLocation()
            }
        } catch (_: Exception) {
            address = "Address lookup needs connectivity; coordinates are available"
            renderLocation()
        }
    }

    private fun renderLocation() {
        val gps = if (latitude != null && longitude != null)
            String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
        else "GPS fix pending"
        locationLine.text = gps
        addressLine.text = address
        mapButton.visibility = if (latitude != null && longitude != null) View.VISIBLE else View.GONE
    }

    private fun openMap() {
        val lat = latitude ?: return
        val lon = longitude ?: return
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        try { startActivity(intent) } catch (_: Exception) {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")))
        }
    }

    /** Draws the same GPS/address/date identity stamp onto each encoded video frame. */
    private fun drawVideoStamp(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val scale = w / 1000f
        val pad = 18f * scale
        val textSize = prefs.getInt("stamp_text_size", 23).coerceIn(12, 40) * scale
        val lines = mutableListOf<Pair<String, Boolean>>()
        if (prefs.getBoolean("stamp_address", true) && address.isNotBlank()) {
            lines.add("${address.trim()}  🇮🇳" to true)
        }
        if (prefs.getBoolean("stamp_date", true)) {
            lines.add(SimpleDateFormat("dd MMM yyyy  |  hh:mm a", Locale.getDefault()).format(Date()) to false)
        }
        if (prefs.getBoolean("stamp_gps", true)) {
            val lat = latitude
            val lon = longitude
            lines.add((if (lat != null) String.format(Locale.US, "Latitude: %.6f", lat) else "Latitude: unavailable") to false)
            lines.add((if (lon != null) String.format(Locale.US, "Longitude: %.6f", lon) else "Longitude: unavailable") to false)
        }
        lines.add("✎  App by AKT" to false)
        val lineHeight = textSize * 1.42f
        val panelHeight = lines.size * lineHeight + pad * 1.3f
        val left = pad
        val bottom = h - pad
        val top = bottom - panelHeight
        val opacity = prefs.getInt("stamp_opacity", 68).coerceIn(10, 100)
        val bg = when (prefs.getInt("stamp_bg_color", 0)) {
            1 -> Color.BLACK
            2 -> Color.WHITE
            3 -> Color.rgb(20, 75, 55)
            4 -> Color.rgb(35, 55, 115)
            else -> Color.rgb(7, 12, 20)
        }
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(opacity * 255 / 100, Color.red(bg), Color.green(bg), Color.blue(bg))
        }
        canvas.drawRoundRect(RectF(left, top, w - pad, bottom), 14f * scale, 14f * scale, panelPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (prefs.getInt("stamp_bg_color", 0) == 2) Color.BLACK else when (prefs.getInt("stamp_color", 0)) {
                1 -> Color.YELLOW
                2 -> Color.rgb(115, 231, 202)
                else -> Color.WHITE
            }
            this.textSize = textSize
            setShadowLayer(2f * scale, 1f, 1f, Color.BLACK)
        }
        val maxTextWidth = w - pad * 3f
        lines.forEachIndexed { index, (line, isAddress) ->
            textPaint.typeface = if (isAddress) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textPaint.textSize = if (isAddress) textSize * 1.08f else textSize
            val fitted = fitStampText(line, textPaint, maxTextWidth)
            canvas.drawText(fitted, pad * 1.5f, top + pad * .65f + (index + 1) * lineHeight, textPaint)
        }
    }

    private fun takePhoto() {
        val cap = imageCapture ?: return
        capture.isEnabled = false
        cap.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(proxy: ImageProxy) {
                try {
                    val buffer = proxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()); buffer.get(bytes)
                    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val rotation = proxy.imageInfo.rotationDegrees
                        if (rotation != 0) {
                            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        }
                        bitmap = applyEffect(bitmap)
                        bitmap = stamp(bitmap)
                        saveBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Photo failed: ${e.message}", Toast.LENGTH_LONG).show() }
                } finally {
                    proxy.close()
                    runOnUiThread { capture.isEnabled = true }
                }
            }
            override fun onError(e: ImageCaptureException) {
                runOnUiThread { capture.isEnabled = true; Toast.makeText(this@MainActivity, "Capture failed", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun toggleVideoRecording() {
        val vc = videoCapture ?: run {
            Toast.makeText(this, "Video camera is not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        val current = activeRecording
        if (current != null) {
            current.stop()
            capture.text = "■"
            Toast.makeText(this, "Stopping video…", Toast.LENGTH_SHORT).show()
            return
        }
        val name = "CameraPlus_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraPlus")
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()
        var pending = vc.output.prepareRecording(this, output)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        } else {
            Toast.makeText(this, "Recording video without audio (microphone permission not granted)", Toast.LENGTH_LONG).show()
        }
        capture.text = "■"
        activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    capture.text = "■"
                    Toast.makeText(this, "Recording video… tap ■ to stop", Toast.LENGTH_SHORT).show()
                }
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    capture.text = "▶"
                    if (event.hasError()) {
                        Toast.makeText(this, "Video save failed: ${event.error}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Video saved to Movies/CameraPlus", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun applyEffect(src: Bitmap): Bitmap {
        val effect = prefs.getInt("effect", 0)
        if (effect == 0) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = ColorMatrix()
        if (effect == 1) matrix.setSaturation(0f) else matrix.set(floatArrayOf(
            .393f,.769f,.189f,0f,0f, .349f,.686f,.168f,0f,0f, .272f,.534f,.131f,0f,0f, 0f,0f,0f,1f,0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(matrix); canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun stamp(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val scale = out.width / 1000f
        val pad = 20f * scale
        val date = SimpleDateFormat("dd MMM yyyy  |  hh:mm a", Locale.getDefault()).format(Date())
        val lat = latitude
        val lon = longitude
        val coordinateLines = if (lat != null && lon != null) listOf(
            String.format(Locale.US, "Latitude: %.6f", lat),
            String.format(Locale.US, "Longitude: %.6f", lon)
        ) else listOf("Latitude: unavailable", "Longitude: unavailable")

        // Address is intentionally the first, bold stamp line; flag follows its text.
        val addressLineText = if (prefs.getBoolean("stamp_address", true) && address.isNotBlank())
            "${address.trim()}  🇮🇳" else null
        val lines = mutableListOf<Pair<String, Boolean>>()
        if (addressLineText != null) lines.add(addressLineText to true)
        if (prefs.getBoolean("stamp_date", true)) lines.add(date to false)
        if (prefs.getBoolean("stamp_gps", true)) coordinateLines.forEach { lines.add(it to false) }
        // Branding is deliberately permanent (not a Settings toggle); retain the sketch mark.
        lines.add("✎  App by AKT" to false)

        val qrOn = prefs.getBoolean("stamp_qr", true) && lat != null && lon != null
        val textSize = prefs.getInt("stamp_text_size", 23).coerceIn(12, 40) * scale
        val lineH = textSize * 1.42f
        val qrSize = if (qrOn) 118f * scale else 0f
        val panelH = lines.size * lineH + pad * 1.35f
        val panelBottom = out.height - pad
        val panelTop = panelBottom - panelH
        val opacity = prefs.getInt("stamp_opacity", 68).coerceIn(10, 100)
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val bg = when (prefs.getInt("stamp_bg_color", 0)) {
                1 -> Color.BLACK
                2 -> Color.WHITE
                3 -> Color.rgb(20, 75, 55)
                4 -> Color.rgb(35, 55, 115)
                else -> Color.rgb(7, 12, 20)
            }
            color = Color.argb((opacity * 255 / 100), Color.red(bg), Color.green(bg), Color.blue(bg))
        }
        canvas.drawRoundRect(RectF(pad, panelTop, out.width - pad, panelBottom), 16f * scale, 16f * scale, panelPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (prefs.getInt("stamp_bg_color", 0) == 2) Color.BLACK else when (prefs.getInt("stamp_color", 0)) {
                1 -> Color.YELLOW
                2 -> Color.rgb(115, 231, 202)
                else -> Color.WHITE
            }
            this.textSize = textSize
            setShadowLayer(2f * scale, 1f, 1f, Color.BLACK)
        }
        val left = pad * 1.7f
        val rightLimit = out.width - pad * 1.7f - if (qrOn) qrSize + pad else 0f
        lines.forEachIndexed { index, (raw, isAddress) ->
            textPaint.textSize = if (isAddress) textSize * 1.08f else textSize
            textPaint.typeface = if (isAddress) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val text = fitStampText(raw, textPaint, (rightLimit - left).coerceAtLeast(1f))
            val baseline = panelTop + pad * .65f + (index + 1) * lineH
            canvas.drawText(text, left, baseline, textPaint)
        }

        if (qrOn) {
            try {
                val mapsUrl = "https://www.google.com/maps/search/?api=1&query=$lat,$lon"
                val matrix = QRCodeWriter().encode(mapsUrl, BarcodeFormat.QR_CODE, 180, 180)
                val qr = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888)
                for (x in 0 until 180) for (y in 0 until 180)
                    qr.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                val qrRect = RectF(out.width - qrSize - pad * 1.5f, panelTop + (panelH - qrSize) / 2,
                    out.width - pad * 1.5f, panelTop + (panelH + qrSize) / 2)
                canvas.drawBitmap(qr, null, qrRect, null)
            } catch (_: Exception) {}
        }
        return out
    }

    private fun fitStampText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val suffix = "…"
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + suffix) > maxWidth) end--
        return text.substring(0, end).trimEnd() + suffix
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val filename = "CameraPlus_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraPlus")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        runOnUiThread { Toast.makeText(this, if (uri != null) "Saved to Pictures/CameraPlus" else "Save failed", Toast.LENGTH_LONG).show() }
    }

    private fun openGallery() {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(intent) } catch (_: Exception) { Toast.makeText(this, "Open Gallery from your phone", Toast.LENGTH_SHORT).show() }
    }

}
