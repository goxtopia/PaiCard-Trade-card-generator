package com.cardgen.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class BatchDrawActivity : AppCompatActivity() {

    // Views
    private lateinit var uploadContainer: LinearLayout
    private lateinit var packContainer: LinearLayout
    private lateinit var gridContainer: ConstraintLayout

    // Upload UI
    private lateinit var btnPickImages: Button
    private lateinit var btnCreatePacks: Button
    private lateinit var fileCountText: TextView
    private lateinit var processingText: TextView

    // Pack UI
    private lateinit var packItem: FrameLayout
    private lateinit var packCountText: TextView

    // Grid UI
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnBackToPacks: Button

    private lateinit var adapter: BatchGridAdapter
    private val processedCards = ArrayList<BatchCardItem>()

    private val vlmService = VLMService()
    private val executor: ExecutorService = Executors.newFixedThreadPool(4) // Parallel processing
    private val mainHandler = Handler(Looper.getMainLooper())

    data class BatchCardItem(
        val bitmap: Bitmap,
        var cardData: VLMService.CardData? = null,
        var isFlipped: Boolean = false,
        var isAnalyzing: Boolean = false,
        var error: String? = null
    )

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            processedCards.clear() // Clear previous selection

            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    val uri = data.clipData!!.getItemAt(i).uri
                    loadImage(uri)
                }
            } else if (data?.data != null) {
                val uri = data.data!!
                loadImage(uri)
            }

            updateUploadUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_draw)
        supportActionBar?.title = "Card Packs"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initViews()
        setupListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun initViews() {
        uploadContainer = findViewById(R.id.uploadContainer)
        packContainer = findViewById(R.id.packContainer)
        gridContainer = findViewById(R.id.gridContainer)

        btnPickImages = findViewById(R.id.btnPickImages)
        btnCreatePacks = findViewById(R.id.btnCreatePacks)
        fileCountText = findViewById(R.id.fileCountText)
        processingText = findViewById(R.id.processingText)

        packItem = findViewById(R.id.packItem)
        packCountText = findViewById(R.id.packCountText)

        recyclerView = findViewById(R.id.recyclerView)
        btnBackToPacks = findViewById(R.id.btnBackToPacks)

        adapter = BatchGridAdapter(processedCards) { position ->
             flipCard(position)
        }
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnPickImages.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            pickImagesLauncher.launch(Intent.createChooser(intent, "Select Pictures"))
        }

        btnCreatePacks.setOnClickListener {
            startProcessing()
        }

        packItem.setOnClickListener {
            openPack()
        }

        btnBackToPacks.setOnClickListener {
            // Reset to upload state
            processedCards.clear()
            adapter.notifyDataSetChanged()

            gridContainer.visibility = View.GONE
            uploadContainer.visibility = View.VISIBLE
            updateUploadUI()
        }
    }

    private fun updateUploadUI() {
        val count = processedCards.size
        fileCountText.text = "$count files selected"

        if (count > 0) {
            btnCreatePacks.isEnabled = true
            btnCreatePacks.backgroundTintList = ContextCompat.getColorStateList(this, R.color.theme_gold)
            btnCreatePacks.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        } else {
            btnCreatePacks.isEnabled = false
            btnCreatePacks.backgroundTintList = ContextCompat.getColorStateList(this, R.color.rarity_r_border) // Grayish
        }
    }

    private fun loadImage(uri: android.net.Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                // Resize if too big to avoid OOM
                val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
                    Bitmap.createScaledBitmap(bitmap, 512, 768, true)
                } else {
                    bitmap
                }
                processedCards.add(BatchCardItem(scaled))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProcessing() {
        btnPickImages.isEnabled = false
        btnCreatePacks.isEnabled = false
        processingText.visibility = View.VISIBLE
        processingText.text = "Processing ${processedCards.size} images... (0/${processedCards.size})"

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "")
        val apiUrl = prefs.getString("api_url", "https://api.openai.com")
        val model = prefs.getString("model", "gpt-4o-mini")
        val customPrompt = prefs.getString("custom_prompt", "Create a funny and creative name and ability description.")

        if (apiKey.isNullOrEmpty()) {
             processingText.text = "Error: API Key not set"
             btnPickImages.isEnabled = true
             return
        }

        // Process all images in background
        Thread {
            val latch = java.util.concurrent.CountDownLatch(processedCards.size)
            var completedCount = 0

            for (item in processedCards) {
                executor.execute {
                    try {
                        item.isAnalyzing = true
                        val data = vlmService.analyzeImage(item.bitmap, apiKey!!, apiUrl!!, model!!, customPrompt!!)
                        saveCardToLibrary(data, item.bitmap)
                        item.cardData = data
                    } catch (e: Exception) {
                        item.error = e.message
                    } finally {
                        item.isAnalyzing = false
                        synchronized(this) {
                            completedCount++
                            mainHandler.post {
                                processingText.text = "Processing... ($completedCount/${processedCards.size})"
                            }
                        }
                        latch.countDown()
                    }
                }
            }

            try {
                latch.await(60, TimeUnit.SECONDS) // Timeout safety
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            mainHandler.post {
                uploadContainer.visibility = View.GONE
                packContainer.visibility = View.VISIBLE
                packCountText.text = "Contains ${processedCards.size} Cards"
                processingText.visibility = View.GONE
                btnPickImages.isEnabled = true
            }
        }.start()
    }

    private fun openPack() {
        // Simple animation: Fade out pack, Fade in Grid
        packContainer.animate().alpha(0f).setDuration(300).withEndAction {
            packContainer.visibility = View.GONE
            packContainer.alpha = 1f // Reset

            gridContainer.visibility = View.VISIBLE
            gridContainer.alpha = 0f
            gridContainer.animate().alpha(1f).setDuration(500).start()

            // Trigger layout
            adapter.notifyDataSetChanged()
        }.start()
    }

    private fun flipCard(position: Int) {
        val item = processedCards[position]
        if (item.isFlipped) return

        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? BatchGridAdapter.ViewHolder
        if (holder != null) {
            val scale = resources.displayMetrics.density
            holder.cardFlipContainer.cameraDistance = 8000 * scale

            val animator = ObjectAnimator.ofFloat(holder.cardFlipContainer, "rotationY", 0f, 180f)
            animator.duration = 600
            animator.interpolator = AccelerateDecelerateInterpolator()

            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                if (value >= 90f) {
                     if (holder.cardBack.visibility == View.VISIBLE) {
                         holder.cardBack.visibility = View.GONE
                         holder.cardFront.visibility = View.VISIBLE
                         holder.cardFront.scaleX = -1f // Fix mirroring
                     }
                }
            }

            animator.start()
            item.isFlipped = true
            adapter.notifyItemChanged(position) // Ensure data is bound
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

            val savedCard = SingleDrawActivity.SavedCard(
                data.name, data.rarity, data.description, data.atk, data.def,
                file.absolutePath, System.currentTimeMillis()
            )

            val prefs = getSharedPreferences("card_library", Context.MODE_PRIVATE)
            val json = prefs.getString("history", "[]")
            val type = object : TypeToken<ArrayList<SingleDrawActivity.SavedCard>>() {}.type
            val list: ArrayList<SingleDrawActivity.SavedCard> = Gson().fromJson(json, type) ?: ArrayList()

            list.add(0, savedCard)
            prefs.edit().putString("history", Gson().toJson(list)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // Adapter
    inner class BatchGridAdapter(
        private val items: List<BatchCardItem>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<BatchGridAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardFlipContainer: FrameLayout = view.findViewById(R.id.cardFlipContainer)
            val cardBack: ImageView = view.findViewById(R.id.cardBack)
            val cardFront: CardView = view.findViewById(R.id.cardFront) // Changed to CardView
            val innerLayout: ConstraintLayout = view.findViewById(R.id.cardFront) // Need to access ConstraintLayout logic inside? No, view finding inside view

            // Wait, cardFront is now the CardView.
            // But I need to find the ConstraintLayout inside it to set the background.
            // Or I can set the background on the inner ConstraintLayout.

            val ivArt: ImageView = view.findViewById(R.id.ivCardArt)
            val tvName: TextView = view.findViewById(R.id.tvCardName)
            val tvRarity: TextView = view.findViewById(R.id.tvCardAttribute)
            val tvDesc: TextView = view.findViewById(R.id.tvCardDesc)
            val tvAtk: TextView = view.findViewById(R.id.tvCardAtk)
            val tvDef: TextView = view.findViewById(R.id.tvCardDef)
            val loadingOverlay: ProgressBar = view.findViewById(R.id.loadingOverlay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_god_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.itemView.clearAnimation()

            if (item.isFlipped) {
                holder.cardFlipContainer.rotationY = 180f
                holder.cardBack.visibility = View.GONE
                holder.cardFront.visibility = View.VISIBLE
                holder.cardFront.scaleX = -1f

                holder.ivArt.setImageBitmap(item.bitmap)
                holder.loadingOverlay.visibility = View.GONE

                if (item.cardData != null) {
                    holder.tvName.text = item.cardData!!.name
                    holder.tvRarity.text = item.cardData!!.rarity
                    holder.tvDesc.text = item.cardData!!.description
                    holder.tvAtk.text = item.cardData!!.atk
                    holder.tvDef.text = item.cardData!!.def

                    applyRarityVisuals(holder, item.cardData!!.rarity)
                } else if (item.error != null) {
                     holder.tvName.text = "Error"
                     holder.tvDesc.text = item.error
                } else {
                    holder.tvName.text = "Loading..."
                }

            } else {
                holder.cardFlipContainer.rotationY = 0f
                holder.cardBack.visibility = View.VISIBLE
                holder.cardFront.visibility = View.GONE
                holder.loadingOverlay.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onItemClick(position)
            }
        }

        private fun applyRarityVisuals(holder: ViewHolder, rarity: String) {
            val r = rarity.uppercase()
            val (bgColor, borderColor) = when {
                r.contains("UR") -> Pair(R.color.rarity_ur_bg, R.color.rarity_ur_border)
                r.contains("SSR") -> Pair(R.color.rarity_ssr_bg, R.color.rarity_ssr_border)
                r.contains("SR") -> Pair(R.color.rarity_sr_bg, R.color.rarity_sr_border)
                r.contains("R") -> Pair(R.color.rarity_r_bg, R.color.rarity_r_border)
                else -> Pair(R.color.rarity_n_bg, R.color.rarity_n_border)
            }

            // To set the background color of the card content, we need to access the child of the CardView
            // which is the ConstraintLayout.
            val innerLayout = holder.cardFront.getChildAt(0)

            val bgDrawable = ContextCompat.getDrawable(holder.itemView.context, R.drawable.bg_card_base) as GradientDrawable
            bgDrawable.setColor(ContextCompat.getColor(holder.itemView.context, bgColor))
            bgDrawable.setStroke(4, ContextCompat.getColor(holder.itemView.context, borderColor))

            innerLayout.background = bgDrawable

            // Animations for High Rarity
            if (r.contains("SSR") || r.contains("UR")) {
                startBreathingAnimation(holder.itemView)
            }
        }

        private fun startBreathingAnimation(view: View) {
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.02f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.02f)
            val animator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY)
            animator.duration = 1500
            animator.repeatCount = ObjectAnimator.INFINITE
            animator.repeatMode = ObjectAnimator.REVERSE
            animator.start()
        }

        override fun getItemCount() = items.size
    }
}
