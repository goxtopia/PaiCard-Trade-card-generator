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
import android.widget.ScrollView
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.animation.ObjectAnimator
import android.view.animation.AccelerateDecelerateInterpolator

class SingleDrawActivity : AppCompatActivity() {

    private lateinit var btnPickImage: Button
    private lateinit var btnGenerate: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    // Native Card Views
    private lateinit var cardFlipContainer: FrameLayout
    private lateinit var cardContainer: ConstraintLayout
    private lateinit var cardBack: ImageView
    private lateinit var tvCardName: TextView
    private lateinit var tvCardAttribute: TextView
    private lateinit var ivCardArt: ImageView
    private lateinit var tvCardType: TextView
    private lateinit var tvCardDesc: TextView
    private lateinit var tvCardAtk: TextView
    private lateinit var tvCardDef: TextView
    private lateinit var viewEffectOverlay: View

    // Library Views
    private lateinit var libraryContainer: FrameLayout
    private lateinit var libraryList: LinearLayout
    private lateinit var btnCloseLibrary: Button

    private var selectedImageBitmap: Bitmap? = null
    private val vlmService = VLMService()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private val gson = Gson()

    private var isFlipped = false

    data class SavedCard(
        val name: String,
        val rarity: String,
        val description: String,
        val atk: String,
        val def: String,
        val imagePath: String,
        val timestamp: Long
    )

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
        setContentView(R.layout.activity_single_draw)
        supportActionBar?.title = "Single Draw"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bindViews()

        btnPickImage.setOnClickListener {
            checkPermissionsAndPickImage()
        }

        btnGenerate.setOnClickListener {
            generateCard()
        }

        cardFlipContainer.setOnClickListener {
            if (selectedImageBitmap != null || tvCardName.text != "Card Name") {
                flipCard()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindViews() {
        btnPickImage = findViewById(R.id.btnPickImage)
        btnGenerate = findViewById(R.id.btnGenerate)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        cardFlipContainer = findViewById(R.id.cardFlipContainer)
        cardContainer = findViewById(R.id.cardContainer)
        cardBack = findViewById(R.id.cardBack)
        tvCardName = findViewById(R.id.tvCardName)
        tvCardAttribute = findViewById(R.id.tvCardAttribute)
        ivCardArt = findViewById(R.id.ivCardArt)
        tvCardType = findViewById(R.id.tvCardType)
        tvCardDesc = findViewById(R.id.tvCardDesc)
        tvCardAtk = findViewById(R.id.tvCardAtk)
        tvCardDef = findViewById(R.id.tvCardDef)
        viewEffectOverlay = findViewById(R.id.viewEffectOverlay)

        // Ensure proper initial state
        cardContainer.visibility = View.VISIBLE // Keep layout bounds
        cardBack.visibility = View.GONE
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

            // Reset flip state if needed
            if (isFlipped) {
                cardBack.visibility = View.GONE
                cardContainer.visibility = View.VISIBLE
                isFlipped = false
                cardFlipContainer.rotationY = 0f
            }

            // Show preview in ImageView
            ivCardArt.setImageBitmap(selectedImageBitmap)
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
                // Initialize Repo if needed
                CardRepository.init(this)

                // 0. Check Cache
                val md5 = CardRepository.calculateMD5(selectedImageBitmap!!)
                var cardData = CardRepository.getCard(md5)
                var fromCache = false

                if (cardData != null) {
                    fromCache = true
                } else {
                    // 1. Analyze Image
                    cardData = vlmService.analyzeImage(selectedImageBitmap!!, apiKey!!, apiUrl!!, model!!, customPrompt!!)
                    // Cache it
                    CardRepository.saveCard(this, md5, cardData)
                }

                // 2. Save Card (on BG thread) - Only if NOT from cache (duplicate prevention)
                if (!fromCache) {
                    saveCardToLibrary(cardData, selectedImageBitmap!!)
                }

                mainHandler.post {
                    setLoading(false)
                    renderCardNative(cardData!!)
                    statusText.text = if (fromCache) "Card Loaded (Duplicate Skipped)" else "Card Generated & Saved!"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    setLoading(false)
                    Toast.makeText(this@SingleDrawActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    statusText.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun saveCardToLibrary(data: VLMService.CardData, bitmap: Bitmap) {
        try {
            val filename = "card_${System.currentTimeMillis()}.png"
            val file = File(filesDir, filename)
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()

            val savedCard = SavedCard(
                data.name, data.rarity, data.description, data.atk, data.def,
                file.absolutePath, System.currentTimeMillis()
            )

            val prefs = getSharedPreferences("card_library", Context.MODE_PRIVATE)
            val json = prefs.getString("history", "[]")
            val type = object : TypeToken<ArrayList<SavedCard>>() {}.type
            val list: ArrayList<SavedCard> = gson.fromJson(json, type) ?: ArrayList()

            list.add(0, savedCard) // Add to top

            prefs.edit().putString("history", gson.toJson(list)).apply()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun flipCard() {
        val scale = resources.displayMetrics.density
        val cameraDist = 8000 * scale
        cardFlipContainer.cameraDistance = cameraDist

        val start = if (isFlipped) 180f else 0f
        val end = if (isFlipped) 0f else 180f

        // Disable interaction during animation
        cardFlipContainer.isClickable = false

        val animator = ObjectAnimator.ofFloat(cardFlipContainer, "rotationY", start, end)
        animator.duration = 600
        animator.interpolator = AccelerateDecelerateInterpolator()

        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            // We want to swap visibility exactly at 90 degrees
            // When going 0 -> 180, swap at 90.
            // When going 180 -> 0, swap at 90.

            if (value >= 90f) {
                // Should show Back
                if (cardContainer.visibility == View.VISIBLE) {
                     cardContainer.visibility = View.GONE
                     cardBack.visibility = View.VISIBLE
                     cardBack.scaleX = -1f // Fix mirroring
                }
            } else {
                // Should show Front
                if (cardBack.visibility == View.VISIBLE) {
                    cardBack.visibility = View.GONE
                    cardContainer.visibility = View.VISIBLE
                }
            }
        }

        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isFlipped = !isFlipped
                cardFlipContainer.isClickable = true
            }
        })

        animator.start()
    }

    private var currentBreathingAnimX: ObjectAnimator? = null
    private var currentBreathingAnimY: ObjectAnimator? = null

    private fun renderCardNative(data: VLMService.CardData) {
        // Cancel any existing breathing animations
        currentBreathingAnimX?.cancel()
        currentBreathingAnimY?.cancel()
        cardContainer.scaleX = 1f
        cardContainer.scaleY = 1f

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

             // Add a subtle breathing animation for High Rarity
             currentBreathingAnimX = ObjectAnimator.ofFloat(cardContainer, "scaleX", 1f, 1.02f, 1f)
             currentBreathingAnimX?.duration = 2000
             currentBreathingAnimX?.repeatCount = ObjectAnimator.INFINITE
             currentBreathingAnimX?.start()

             currentBreathingAnimY = ObjectAnimator.ofFloat(cardContainer, "scaleY", 1f, 1.02f, 1f)
             currentBreathingAnimY?.duration = 2000
             currentBreathingAnimY?.repeatCount = ObjectAnimator.INFINITE
             currentBreathingAnimY?.start()
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
