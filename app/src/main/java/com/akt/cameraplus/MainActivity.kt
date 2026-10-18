package com.akt.cameraplus

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var capture: Button
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: androidx.camera.core.Camera? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var address = "Address unavailable"
    private var exposure = 0
    private var flash = false
    private val prefs by lazy { getSharedPreferences("stamp_prefs", MODE_PRIVATE) }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) startCamera()
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true || it[Manifest.permission.ACCESS_COARSE_LOCATION] == true) updateLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        buildUi()
        val req = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) req.add(Manifest.permission.READ_MEDIA_IMAGES)
        val missing = req.filter { ContextCompat.checkSelfPermission(this,it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else { startCamera(); updateLocation() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(TextView(this).apply { text="Camera +"; textSize=23f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setPadding(8,14,8,10) })
        previewView = PreviewView(this).apply { implementationMode=PreviewView.ImplementationMode.COMPATIBLE; scaleType=PreviewView.ScaleType.FILL_CENTER }
        status = TextView(this).apply { text="Locating…"; textSize=12f; setTextColor(Color.WHITE); setPadding(12,6,12,6) }
        root.addView(previewView, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(status)
        val exposureRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(10,2,10,2) }
        exposureRow.addView(TextView(this).apply { text="Brightness"; setTextColor(Color.WHITE) })
        val seek=SeekBar(this).apply { max=12; progress=6 }
        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean) { exposure=p-6; camera?.cameraControl?.setExposureCompensationIndex(exposure) }
            override fun onStartTrackingTouch(s:SeekBar?) {}
            override fun onStopTrackingTouch(s:SeekBar?) {}
        })
        exposureRow.addView(seek,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(exposureRow)
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(4,4,4,12) }
        fun btn(label:String, action:()->Unit)=Button(this).apply { text=label; setOnClickListener { action() } }
        row.addView(btn("⚡ Flash") { flash=!flash; imageCapture?.flashMode=if(flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }, LinearLayout.LayoutParams(0,-2,1f))
        row.addView(btn("Flip") { lens=if(lens==CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK; startCamera() }, LinearLayout.LayoutParams(0,-2,1f))
        capture=btn("📷") { takePhoto() }
        row.addView(capture,LinearLayout.LayoutParams(0,-2,1.2f))
        row.addView(btn("Settings") { startActivity(android.content.Intent(this,SettingsActivity::class.java)) },LinearLayout.LayoutParams(0,-2,1f))
        row.addView(btn("Gallery") { openGallery() },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(row)
        setContentView(root)
    }

    private fun startCamera() {
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider=future.get()
            val preview=Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setFlashMode(if(flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF).build()
            try {
                provider.unbindAll()
                camera=provider.bindToLifecycle(this,CameraSelector.Builder().requireLensFacing(lens).build(),preview,imageCapture)
                camera?.cameraControl?.setExposureCompensationIndex(exposure)
            } catch(e:Exception) { Toast.makeText(this,"Camera start error: ${e.message}",Toast.LENGTH_LONG).show() }
        },ContextCompat.getMainExecutor(this))
    }

    @Suppress("MissingPermission")
    private fun updateLocation() {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            if(loc==null) { status.text="GPS not fixed yet — try outdoors"; return@addOnSuccessListener }
            latitude=loc.latitude; longitude=loc.longitude
            address="Address unavailable"
            try {
                if(Geocoder.isPresent()) {
                    val geocoder=Geocoder(this,Locale.getDefault())
                    if(Build.VERSION.SDK_INT>=33) geocoder.getFromLocation(loc.latitude,loc.longitude,1) { list ->
                        if(list.isNotEmpty()) address=list[0].getAddressLine(0) ?: address
                        runOnUiThread { showLocation() }
                    } else {
                        @Suppress("DEPRECATION")
                        val list=geocoder.getFromLocation(loc.latitude,loc.longitude,1)
                        if(!list.isNullOrEmpty()) address=list[0].getAddressLine(0) ?: address
                    }
                }
            } catch(_:Exception) {}
            showLocation()
        }
    }
    private fun showLocation() {
        val gps=if(latitude!=null) String.format(Locale.US,"%.6f, %.6f",latitude,longitude) else "GPS unavailable"
        status.text="$gps\n$address"
    }

    private fun takePhoto() {
        val cap=imageCapture ?: return
        capture.isEnabled=false
        cap.takePicture(cameraExecutor,object:ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(proxy:ImageProxy) {
                try {
                    val buf=proxy.planes[0].buffer
                    val bytes=ByteArray(buf.remaining()); buf.get(bytes)
                    var bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                    if(bmp!=null) { bmp=applyEffect(bmp); bmp=stamp(bmp); saveBitmap(bmp) }
                } catch(e:Exception) { runOnUiThread { Toast.makeText(this@MainActivity,"Photo failed: ${e.message}",Toast.LENGTH_LONG).show() } }
                finally { proxy.close(); runOnUiThread { capture.isEnabled=true } }
            }
            override fun onError(e:ImageCaptureException) { runOnUiThread { capture.isEnabled=true; Toast.makeText(this@MainActivity,"Capture failed",Toast.LENGTH_SHORT).show() } }
        })
    }

    private fun applyEffect(src:Bitmap):Bitmap {
        val effect=prefs.getInt("effect",0)
        if(effect==0) return src
        val out=Bitmap.createBitmap(src.width,src.height,Bitmap.Config.ARGB_8888)
        val c=Canvas(out); val p=Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix=ColorMatrix()
        if(effect==1) matrix.setSaturation(0f) else {
            matrix.set(floatArrayOf(0.393f,0.769f,0.189f,0f,0f, 0.349f,0.686f,0.168f,0f,0f, 0.272f,0.534f,0.131f,0f,0f, 0f,0f,0f,1f,0f))
        }
        p.colorFilter=ColorMatrixColorFilter(matrix); c.drawBitmap(src,0f,0f,p); return out
    }

    private fun stamp(src:Bitmap):Bitmap {
        val out=src.copy(Bitmap.Config.ARGB_8888,true); val canvas=Canvas(out)
        val scale=out.width/1000f; val pad=18f*scale; val lineH=30f*scale
        val lines=mutableListOf<String>()
        if(prefs.getBoolean("stamp_date",true)) lines.add(SimpleDateFormat("dd MMM yyyy  |  hh:mm:ss a",Locale.getDefault()).format(Date()))
        if(prefs.getBoolean("stamp_gps",true)) lines.add(if(latitude!=null) String.format(Locale.US,"GPS: %.6f, %.6f",latitude,longitude) else "GPS: unavailable")
        if(prefs.getBoolean("stamp_address",true)) lines.add(address.take(115))
        if(prefs.getBoolean("stamp_brand",true)) lines.add("App by AKT")
        val qrOn=prefs.getBoolean("stamp_qr",true) && latitude!=null && longitude!=null
        val qrSize=if(qrOn) 100f*scale else 0f
        val color=when(prefs.getInt("stamp_color",0)){1->Color.YELLOW;2->Color.rgb(120,255,210);else->Color.WHITE}
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; textSize=if(lines.lastOrNull()=="App by AKT") 16f*scale else 23f*scale; setShadowLayer(2f*scale,1f,1f,Color.BLACK) }
        val blockH=lineH*lines.size+pad*1.3f
        val left=pad/2; val bottom=out.height-pad/2
        val box=Paint().apply { this.color=Color.argb(160,0,0,0) }
        canvas.drawRoundRect(RectF(left,bottom-blockH-pad/2,out.width-pad/2,bottom),10f*scale,10f*scale,box)
        lines.forEachIndexed { i,t ->
            if(t=="App by AKT") paint.textSize=16f*scale
            canvas.drawText(t,left+pad,bottom-blockH+lineH*(i+1)-4f*scale,paint)
        }
        if(qrOn) {
            try {
                val url="https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
                val matrix=QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,180,180)
                val qr=Bitmap.createBitmap(180,180,Bitmap.Config.ARGB_8888)
                for(x in 0 until 180) for(y in 0 until 180) qr.setPixel(x,y,if(matrix[x,y]) Color.BLACK else Color.WHITE)
                val dest=RectF(out.width-qrSize-pad,bottom-blockH-qrSize-pad,out.width-pad,bottom-blockH-pad)
                canvas.drawBitmap(qr,null,dest,null)
            } catch(_:Exception) {}
        }
        return out
    }

    private fun saveBitmap(b:Bitmap) {
        val filename="CameraPlus_${SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())}.jpg"
        val cv=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,filename); put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg"); if(Build.VERSION.SDK_INT>=29) put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/CameraPlus") }
        val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv)
        if(uri!=null) contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.JPEG,94,it) }
        runOnUiThread { Toast.makeText(this,if(uri!=null)"Saved to Pictures/CameraPlus" else "Save failed",Toast.LENGTH_LONG).show() }
    }

    private fun openGallery() {
        val intent=android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*"); addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        try { startActivity(intent) } catch(_:Exception) { Toast.makeText(this,"Open your Gallery app",Toast.LENGTH_SHORT).show() }
    }
    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}
