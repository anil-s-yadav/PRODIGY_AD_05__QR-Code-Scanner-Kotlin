package com.example.qrcodescannerkotlin

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Patterns
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import java.io.InputStream

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, Camera.PreviewCallback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var camera: Camera
    private lateinit var tvResult: TextView
    private lateinit var btnTorch: ImageView
    private lateinit var btnChooseImage: ImageView
    private lateinit var copyTextButton: Button
    private lateinit var openUrlButton: Button
    private var scanning = false
    private var torchOn = false

    private val GALLERY_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)
        tvResult = findViewById(R.id.tvResult)
        btnTorch = findViewById(R.id.imgFlash)
        btnChooseImage = findViewById(R.id.imgGallery)
         openUrlButton = findViewById(R.id.openUrlButton)
         copyTextButton = findViewById(R.id.copyTextButton)


        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            startScanning()
        }

        btnTorch.setOnClickListener({
            toggleTorch()
        })
        btnChooseImage.setOnClickListener({
            chooseImage()
        })
    }

    private fun startScanning() {
        scanning = true
        camera = Camera.open()
        camera.setPreviewDisplay(surfaceHolder)
        camera.setDisplayOrientation(90)
        camera.setPreviewCallback(this)
        camera.startPreview()
    }

    private fun stopScanning() {
        scanning = false
        camera.setPreviewCallback(null)
        camera.stopPreview()
        camera.release()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Surface created, start scanning
        startScanning()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Surface changed, nothing to do here
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Surface destroyed, release resources
        stopScanning()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScanning()
        } else {
            Log.e(TAG, "Camera permission denied")
        }
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        if (data != null && scanning) {
            val size = camera?.parameters?.previewSize
            val binaryBitmap = BinaryBitmap(
                HybridBinarizer(
                    PlanarYUVLuminanceSource(
                        data,
                        size?.width ?: 1,
                        size?.height ?: 1,
                        0,
                        0,
                        size?.width ?: 1,
                        size?.height ?: 1,
                        false
                    )
                )
            )

            try {
                val result: Result = MultiFormatReader().decode(binaryBitmap)
                // Handle the QR code result here
                Log.d(TAG, "Scanned QR Code: ${result.text}")
                runOnUiThread {
                    handleQRCodeResult(result)
                }
            } catch (e: Exception) {
                // QR code not found
            }
        }
    }

    // Function to toggle torch
    fun toggleTorch() {
        if (torchOn) {
            turnOffTorch()
        } else {
            turnOnTorch()
        }
    }

    private fun turnOnTorch() {
        val parameters = camera.parameters
        parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
        camera.parameters = parameters
        torchOn = true
        btnTorch.setImageResource(R.drawable.lightbulb_on)
    }

    private fun turnOffTorch() {
        val parameters = camera.parameters
        parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
        camera.parameters = parameters
        torchOn = false
        btnTorch.setImageResource(R.drawable.bulb_off)
    }

    // Function to choose an image from the gallery
    fun chooseImage() {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Images"), GALLERY_REQUEST_CODE)



    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val selectedImage: Uri = data.data!!
            val inputStream: InputStream? = contentResolver.openInputStream(selectedImage)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // Convert the bitmap to a binary bitmap for QR code scanning
            val binaryBitmap = BinaryBitmap(HybridBinarizer(
                RGBLuminanceSource(bitmap.width, bitmap.height, IntArray(bitmap.width * bitmap.height).apply {
                    // Copy pixel data from the bitmap
                    bitmap.getPixels(this, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                })
            ))

            // Use the MultiFormatReader to decode the QR code
            try {
                val result: Result = MultiFormatReader().decode(binaryBitmap)
                handleQRCodeResult(result)
            } catch (e: Exception) {
                // QR code not found
                Log.e(TAG, "Error decoding QR code from Gallery: ${e.message}")
            }
        }
    }

    private fun handleQRCodeResult(result: Result) {
        val scannedData = result.text
        if (Patterns.WEB_URL.matcher(scannedData).matches()) {
            showOpenUrlButton(scannedData)
            showCopyTextButton(scannedData)
        } else {
            showCopyTextButton(scannedData)
            openUrlButton.visibility = View.GONE
        }
    }

    private fun showOpenUrlButton(url: String) {
        tvResult.text = "$url"
         openUrlButton.visibility = View.VISIBLE
        copyTextButton.visibility = View.VISIBLE
         openUrlButton.setOnClickListener {
             val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
             startActivity(intent)
         }
    }

    private fun showCopyTextButton(text: String) {
        tvResult.text = "$text"
        copyTextButton.visibility = View.VISIBLE
         copyTextButton.setOnClickListener {
             val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
             val clip = ClipData.newPlainText("Copied Text", text)
             clipboard.setPrimaryClip(clip)
             Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
         }
    }





    companion object {
        private const val TAG = "QRCodeScannerApp"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
}