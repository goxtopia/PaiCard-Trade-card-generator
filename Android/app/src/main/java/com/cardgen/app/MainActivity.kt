package com.cardgen.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var tempImageUri: Uri? = null
    private var tempImageFile: File? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempImageFile != null) {
            processSnappedImage(tempImageFile!!)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Init repositories in case they haven't been
        CardRepository.init(this)
        PackRepository.init(this)

        // Ensure service is running and checking for pending packs
        val resumeIntent = Intent(this, CardGenerationService::class.java)
        resumeIntent.action = CardGenerationService.ACTION_RESUME_PENDING
        startService(resumeIntent)

        findViewById<Button>(R.id.btnSnap).setOnClickListener {
            checkCameraPermissionAndSnap()
        }

        findViewById<Button>(R.id.btnSingleDraw).setOnClickListener {
            startActivity(Intent(this, SingleDrawActivity::class.java))
        }

        findViewById<Button>(R.id.btnGodsDraw).setOnClickListener {
            startActivity(Intent(this, GodsDrawActivity::class.java))
        }

        findViewById<Button>(R.id.btnBatchDraw).setOnClickListener {
            startActivity(Intent(this, BatchDrawActivity::class.java))
        }

        findViewById<Button>(R.id.btnLibrary).setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun checkCameraPermissionAndSnap() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        try {
            val file = File(cacheDir, "snap_${System.currentTimeMillis()}.jpg")
            tempImageFile = file
            // Use the authority defined in AndroidManifest.xml
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            tempImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to launch camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processSnappedImage(file: File) {
        Toast.makeText(this, "Processing image...", Toast.LENGTH_SHORT).show()

        executor.execute {
            try {
                // Correct orientation first
                val correctedFile = correctImageOrientation(file)

                val bytes = correctedFile.readBytes()
                val md5 = CardRepository.calculateMD5(bytes)

                // Move/Save to internal storage for persistence
                val filename = "pack_img_${md5}.jpg"
                val finalFile = File(filesDir, filename)
                if (!finalFile.exists()) {
                     correctedFile.copyTo(finalFile, overwrite = true)
                }

                // Cleanup temp file
                try {
                    file.delete()
                    if (correctedFile != file) correctedFile.delete()
                } catch (e: Exception) { /* ignore */ }

                val items = listOf(PackRepository.PackItem(finalFile.absolutePath, md5))
                val pack = PackRepository.createPack(this, items)

                // Start Service
                val intent = Intent(this, CardGenerationService::class.java)
                intent.action = CardGenerationService.ACTION_PROCESS_PACK
                intent.putExtra(CardGenerationService.EXTRA_PACK_ID, pack.id)
                startService(intent)

                // Navigate to BatchDrawActivity to see progress
                runOnUiThread {
                    startActivity(Intent(this, BatchDrawActivity::class.java))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun correctImageOrientation(file: File): File {
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            val rotationInDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            if (rotationInDegrees == 0) return file

            // Use inSampleSize to prevent OOM
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, options)

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, 2048, 2048)
            options.inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return file
            val matrix = Matrix()
            matrix.preRotate(rotationInDegrees.toFloat())

            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            // Save to a new temp file or overwrite?
            // Let's create a new one to be safe
            val newFile = File(cacheDir, "rotated_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(newFile)
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()

            bitmap.recycle()
            if (rotatedBitmap != bitmap) rotatedBitmap.recycle()

            return newFile

        } catch (e: Exception) {
            e.printStackTrace()
            return file
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
