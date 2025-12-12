package com.cardgen.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.Executors
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.Random

class MainActivity : AppCompatActivity() {

    private lateinit var btnPickImage: Button
    private lateinit var btnGenerate: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var webView: WebView

    private var selectedImageBitmap: Bitmap? = null
    private val vlmService = VLMService()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val uri = data?.data
            if (uri != null) {
                loadImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPickImage = findViewById(R.id.btnPickImage)
        btnGenerate = findViewById(R.id.btnGenerate)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.cardWebView)

        setupWebView()

        btnPickImage.setOnClickListener {
            checkPermissionsAndPickImage()
        }

        btnGenerate.setOnClickListener {
            generateCard()
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        // Allow loading from assets
        webView.loadUrl("file:///android_asset/www/android_view.html")
        webView.setBackgroundColor(0x00000000) // Transparent
    }

    private fun checkPermissionsAndPickImage() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
        } else {
            openGallery()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun loadImage(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            selectedImageBitmap = BitmapFactory.decodeStream(inputStream)
            btnGenerate.isEnabled = true
            statusText.text = "Image Selected"

            // Show preview in WebView (optional, or just wait for generation)
            // For now, we wait for generate.
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateCard() {
        if (selectedImageBitmap == null) return

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "")
        val apiUrl = prefs.getString("api_url", "https://api.openai.com")
        val model = prefs.getString("model", "gpt-4o-mini")

        if (apiKey.isNullOrEmpty()) {
            Toast.makeText(this, "Please set API Key in Settings", Toast.LENGTH_LONG).show()
            openSettings()
            return
        }

        setLoading(true)

        executor.execute {
            try {
                // 1. Analyze Image
                val cardData = vlmService.analyzeImage(selectedImageBitmap!!, apiKey!!, apiUrl!!, model!!)

                // 2. Prepare Display Data
                val displayJson = JSONObject()
                displayJson.put("name", cardData.name)
                displayJson.put("description", cardData.description)
                displayJson.put("atk", cardData.atk)
                displayJson.put("def", cardData.def)
                displayJson.put("rarity", cardData.rarity)

                // Add visuals
                val (effect, theme) = getVisuals(cardData.rarity)
                displayJson.put("effect_type", effect)
                displayJson.put("color_theme", theme)

                // Convert Bitmap to Data URI for WebView
                val base64Img = bitmapToDataUri(selectedImageBitmap!!)
                displayJson.put("image_data", base64Img)

                val jsonString = displayJson.toString()

                mainHandler.post {
                    setLoading(false)
                    webView.evaluateJavascript("javascript:renderCard('$jsonString')", null)
                    statusText.text = "Card Generated!"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    setLoading(false)
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    statusText.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun getVisuals(rarity: String): Pair<String, String> {
        // Simple logic based on app.py
        val r = rarity.uppercase()
        val random = Random()

        var effect = ""
        var theme = "theme-gray"

        when (r) {
            "N" -> {
                 if (random.nextFloat() < 0.1) effect = "effect-dust"
                 theme = listOf("theme-gray", "theme-pale-blue", "theme-pale-green").random()
            }
            "R" -> {
                 if (random.nextFloat() < 0.3) effect = "effect-shine"
                 theme = listOf("theme-bronze", "theme-silver", "theme-steel").random()
            }
            "SR" -> {
                 if (random.nextFloat() < 0.6) effect = "effect-holographic"
                 theme = listOf("theme-gold", "theme-orange", "theme-crimson").random()
            }
            "SSR" -> {
                 if (random.nextFloat() < 0.9) effect = "effect-lightning"
                 theme = listOf("theme-purple", "theme-magenta", "theme-deep-blue").random()
            }
            "UR" -> {
                 effect = listOf("effect-cosmic", "effect-pulse", "effect-lightning").random()
                 theme = listOf("theme-rainbow", "theme-black-gold", "theme-galaxy").random()
            }
        }
        return Pair(effect, theme)
    }

    private fun bitmapToDataUri(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val b64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            btnGenerate.isEnabled = false
            btnPickImage.isEnabled = false
            statusText.text = getString(R.string.analyzing)
        } else {
            progressBar.visibility = View.GONE
            btnGenerate.isEnabled = true
            btnPickImage.isEnabled = true
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Settings")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            openSettings()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
