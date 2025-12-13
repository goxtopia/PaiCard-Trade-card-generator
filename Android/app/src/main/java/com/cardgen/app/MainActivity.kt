package com.cardgen.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
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
                val bytes = file.readBytes()
                val md5 = CardRepository.calculateMD5(bytes)

                // Move/Save to internal storage for persistence
                val filename = "pack_img_${md5}.jpg"
                val finalFile = File(filesDir, filename)
                if (!finalFile.exists()) {
                     file.copyTo(finalFile, overwrite = true)
                }

                // Cleanup temp file
                try {
                    file.delete()
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
}
