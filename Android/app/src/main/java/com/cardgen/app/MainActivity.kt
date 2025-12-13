package com.cardgen.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
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

    // Native Card Views
    private lateinit var cardContainer: ConstraintLayout
    private lateinit var tvCardName: TextView
    private lateinit var tvCardAttribute: TextView
    private lateinit var ivCardArt: ImageView
    private lateinit var tvCardType: TextView
    private lateinit var tvCardDesc: TextView
    private lateinit var tvCardAtk: TextView
    private lateinit var tvCardDef: TextView
    private lateinit var viewEffectOverlay: View

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

        bindViews()

        btnPickImage.setOnClickListener {
            checkPermissionsAndPickImage()
        }

        btnGenerate.setOnClickListener {
            generateCard()
        }
    }

    private fun bindViews() {
        btnPickImage = findViewById(R.id.btnPickImage)
        btnGenerate = findViewById(R.id.btnGenerate)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        cardContainer = findViewById(R.id.cardContainer)
        tvCardName = findViewById(R.id.tvCardName)
        tvCardAttribute = findViewById(R.id.tvCardAttribute)
        ivCardArt = findViewById(R.id.ivCardArt)
        tvCardType = findViewById(R.id.tvCardType)
        tvCardDesc = findViewById(R.id.tvCardDesc)
        tvCardAtk = findViewById(R.id.tvCardAtk)
        tvCardDef = findViewById(R.id.tvCardDef)
        viewEffectOverlay = findViewById(R.id.viewEffectOverlay)

        // Hide card initially or set empty state
        cardContainer.visibility = View.INVISIBLE
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

            // Show preview in ImageView
            ivCardArt.setImageBitmap(selectedImageBitmap)
            cardContainer.visibility = View.VISIBLE
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
        val customPrompt = prefs.getString("custom_prompt", "Create a funny and creative name and ability description for a trading card based on this image. Name and Description should be in Chinese (Chinese).")

        if (apiKey.isNullOrEmpty()) {
            Toast.makeText(this, "Please set API Key in Settings", Toast.LENGTH_LONG).show()
            openSettings()
            return
        }

        setLoading(true)

        executor.execute {
            try {
                // 1. Analyze Image
                val cardData = vlmService.analyzeImage(selectedImageBitmap!!, apiKey!!, apiUrl!!, model!!, customPrompt!!)

                mainHandler.post {
                    setLoading(false)
                    renderCardNative(cardData)
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

    private fun renderCardNative(data: VLMService.CardData) {
        // Set Text
        tvCardName.text = data.name
        tvCardDesc.text = data.description
        tvCardAtk.text = data.atk
        tvCardDef.text = data.def
        tvCardAttribute.text = data.rarity

        // Ensure image is set (in case it wasn't already)
        if (selectedImageBitmap != null) {
            ivCardArt.setImageBitmap(selectedImageBitmap)
        }

        cardContainer.visibility = View.VISIBLE

        // Rarity Colors & Styles
        val rarity = data.rarity.uppercase()
        val (bgColorId, borderColorId) = getRarityColors(rarity)

        val bgDrawable = ContextCompat.getDrawable(this, R.drawable.bg_card_base) as GradientDrawable
        bgDrawable.setColor(ContextCompat.getColor(this, bgColorId))
        bgDrawable.setStroke(dpToPx(8), ContextCompat.getColor(this, borderColorId))

        cardContainer.background = bgDrawable

        // Simple visual effect based on rarity
        applyVisualEffects(rarity)

        // Animation (Pop in)
        cardContainer.alpha = 0f
        cardContainer.scaleX = 0.8f
        cardContainer.scaleY = 0.8f
        cardContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()
    }

    private fun getRarityColors(rarity: String): Pair<Int, Int> {
        return when {
            rarity.contains("UR") -> Pair(R.color.rarity_ur_bg, R.color.rarity_ur_border)
            rarity.contains("SSR") -> Pair(R.color.rarity_ssr_bg, R.color.rarity_ssr_border)
            rarity.contains("SR") -> Pair(R.color.rarity_sr_bg, R.color.rarity_sr_border)
            rarity.contains("R") -> Pair(R.color.rarity_r_bg, R.color.rarity_r_border)
            else -> Pair(R.color.rarity_n_bg, R.color.rarity_n_border)
        }
    }

    private fun applyVisualEffects(rarity: String) {
        viewEffectOverlay.background = null

        if (rarity.contains("SSR") || rarity.contains("UR")) {
             // Simple shine effect using gradient
             val shine = GradientDrawable(
                 GradientDrawable.Orientation.TL_BR,
                 intArrayOf(Color.TRANSPARENT, Color.parseColor("#40FFFFFF"), Color.TRANSPARENT)
             )
             viewEffectOverlay.background = shine
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
