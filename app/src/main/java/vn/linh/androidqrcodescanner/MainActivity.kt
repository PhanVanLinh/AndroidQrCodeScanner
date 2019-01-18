package vn.linh.androidqrcodescanner

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Camera
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.SurfaceHolder
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var barcodeDetector: BarcodeDetector
    lateinit var cameraSource: CameraSource
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Toast.makeText(this, "Developer: Make sure you enable all permission in Settings", Toast.LENGTH_LONG).show()

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
                if (qrCodes != null && qrCodes.size() > 0) {
                    runOnUiThread {
                        playSound()
                    }
                    runOnUiThread {
                        text_result.visibility = View.VISIBLE
                        text_result.text = qrCodes.valueAt(0).displayValue
                    }
                    cameraSource.stop()
                }
            }
        })

        image_flash.setOnClickListener {
            if (isFlashOn()) {
                image_flash.drawable.setTint(Color.WHITE)
                turnFlash(false)
            } else {
                image_flash.drawable.setTint(ContextCompat.getColor(this, R.color.colorPrimary))
                turnFlash(true)
            }
        }

        image_gallery.setOnClickListener {
            pickImage()
        }

        seek_bar_zoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (seekBar != null) {
                    val maxCameraZoom = getCamera(cameraSource)?.parameters?.maxZoom ?: 0
                    val zoomLevel = progress.toFloat() / seekBar.max * maxCameraZoom
                    zoom(zoomLevel.toInt())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
    }


    private fun zoom(zoomLevel: Int) {
        camera = getCamera(cameraSource)
        if (camera != null) {
            try {
                val param = camera?.parameters
                param?.zoom = zoomLevel
                camera?.parameters = param
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun turnFlash(flashMode: Boolean) {
        camera = getCamera(cameraSource)
        if (camera != null) {
            try {
                val param = camera?.parameters
                param?.flashMode =
                        if (flashMode) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
                camera?.parameters = param
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isFlashOn(): Boolean {
        camera = getCamera(cameraSource)
        if (camera != null) {
            val param = camera?.parameters
            return param?.flashMode == Camera.Parameters.FLASH_MODE_TORCH
        }
        return false
    }

    private fun getCamera(cameraSource: CameraSource): Camera? {
        val declaredFields = CameraSource::class.java.declaredFields
        for (field in declaredFields) {
            if (field.type === Camera::class.java) {
                field.isAccessible = true
                try {
                    return field.get(cameraSource) as Camera?
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

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_FOR_SCAN)
    }

    private fun scanPickedImage(image: Bitmap) {
        val detections = barcodeDetector.detect(Frame.Builder().setBitmap(image).build())
        if (detections != null && detections.size() > 0) {
            runOnUiThread {
                playSound()
            }
            runOnUiThread {
                text_result.visibility = View.VISIBLE
                text_result.text = detections.valueAt(0).displayValue
            }
            cameraSource.stop()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_FOR_SCAN && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            uri?.let {
                scanPickedImage(uriToBitmap(it))
            }
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap {
        return MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
    }

    companion object {
        const val PICK_IMAGE_FOR_SCAN = 100
    }
}
