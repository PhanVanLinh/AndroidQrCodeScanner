package vn.linh.androidqrcodescanner

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaActionSound
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var barcodeDetector: BarcodeDetector
    lateinit var cameraSource: CameraSource
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Enable camera permission and restart the application", Toast.LENGTH_LONG).show()
        }

        barcodeDetector = BarcodeDetector.Builder(this).setBarcodeFormats(Barcode.QR_CODE).build()
        cameraSource = CameraSource.Builder(this, barcodeDetector).setFacing(CameraSource.CAMERA_FACING_BACK)
            .setRequestedFps(35.0f)
            .setAutoFocusEnabled(true)
            .build()

        camera_view.holder.addCallback(object : SurfaceHolder.Callback {
            @SuppressLint("MissingPermission")
            override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
                try {
                    cameraSource.start(camera_view.holder)
                    playAnimation()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, i1: Int, i2: Int) {
            }

            override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
                cameraSource.stop()
            }
        })

        barcodeDetector.setProcessor(object : Detector.Processor<Barcode> {
            override fun release() {}

            override fun receiveDetections(detections: Detector.Detections<Barcode>) {
                val qrCodes = detections.detectedItems
                Log.i("TAG", "receive " + qrCodes.size())
                if (qrCodes != null && qrCodes.size() > 0) {
                    runOnUiThread {
                        playSound()
                    }
                    Log.i("TAG", "stop " + qrCodes.valueAt(0).displayValue)
                    runOnUiThread {
                        text_result.visibility = View.VISIBLE
                        text_result.text = qrCodes.valueAt(0).displayValue
                    }
                    cameraSource.stop()
                }
            }
        })

        button_zoom_in.setOnClickListener {
            zoomIn()
        }

        button_zoom_out.setOnClickListener {
            zoomOut()
        }

        button_flash.setOnClickListener {
            handleFlash(true)
        }

        button_recreate.button_recreate.setOnClickListener {
            recreate()
        }
    }

    private fun zoomIn() {
        handleZoom(true)
    }

    private fun zoomOut() {
        handleZoom(false)
    }

    private fun handleZoom(zoomIn: Boolean) {
        camera = getCamera(cameraSource)
        if (camera != null) {
            try {
                val param = camera?.parameters
                val zoomLevel = param?.zoom ?: 0
                if (zoomIn) {
                    param?.zoom = zoomLevel + 2
                } else {
                    param?.zoom = zoomLevel - 2
                }
                camera?.parameters = param
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun handleFlash(flashMode: Boolean) {
        camera = getCamera(cameraSource)
        if (camera != null) {
            try {
                val param = camera?.parameters
                param?.flashMode =
                        if (!flashMode) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
                camera?.parameters = param
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getCamera(cameraSource: CameraSource): Camera? {
        val declaredFields = CameraSource::class.java.declaredFields
        for (field in declaredFields) {
            if (field.type === Camera::class.java) {
                field.isAccessible = true
                try {
                    return field.get(cameraSource) as Camera
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                }
                break
            }
        }
        return null
    }

    private fun playAnimation() {
        val left = animate_view.left
        val animator =
            ObjectAnimator.ofFloat(
                animate_view,
                "translationX",
                0f,
                (animate_view.parent as View).width.toFloat() - animate_view.width - left * 2
            )
        animator.duration = 1000
        animator.repeatMode = ValueAnimator.REVERSE
        animator.repeatCount = ValueAnimator.INFINITE
        animator.start()
    }

    private fun playSound() {
        val sound = MediaActionSound()
        sound.play(MediaActionSound.SHUTTER_CLICK)
    }
}
