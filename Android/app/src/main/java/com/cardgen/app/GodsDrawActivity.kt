package com.cardgen.app

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import java.io.File
import java.io.FileOutputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class GodsDrawActivity : AppCompatActivity() {

    private lateinit var btnSummon: Button
    private lateinit var statusText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GodsDrawAdapter
    private val cardItems = ArrayList<GodCardItem>()

    private val client = OkHttpClient()
    private val executor = Executors.newFixedThreadPool(4) // Parallel downloads?
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vlmService = VLMService() // Reused for generation

    data class GodCardItem(
        var imageUrl: String? = null,
        var bitmap: Bitmap? = null,
        var cardData: VLMService.CardData? = null,
        var isFlipped: Boolean = false,
        var isLoading: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gods_draw)
        supportActionBar?.title = "God's Draw"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        CardRepository.init(this) // Init repository

        btnSummon = findViewById(R.id.btnSummon)
        statusText = findViewById(R.id.statusText)
        recyclerView = findViewById(R.id.recyclerView)

        // 1 Column
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GodsDrawAdapter(cardItems) { position ->
            flipCard(position)
        }
        recyclerView.adapter = adapter

        btnSummon.setOnClickListener {
            startSummoning()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun startSummoning() {
        cardItems.clear()
        // Initialize 6 empty slots
        for (i in 0 until 6) {
            cardItems.add(GodCardItem())
        }
        adapter.notifyDataSetChanged()

        btnSummon.isEnabled = false
        statusText.text = "Summoning..."

        // Fetch 6 images
        for (i in 0 until 6) {
            fetchRandomImage(i)
        }
    }

    private fun fetchRandomImage(index: Int) {
        executor.execute {
            var success = false
            try {
                val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val apiUrl = prefs.getString("god_draw_url", "")
                    .takeIf { !it.isNullOrEmpty() }
                    ?: "https://api.tcslw.cn/api/img/tbmjx?type=json"

                val request = Request.Builder()
                    .url(apiUrl)
                    .build()
                val response = client.newCall(request).execute()
                val jsonStr = response.body?.string()

                if (jsonStr != null) {
                    val json = JSONObject(jsonStr)
                    val url = json.optString("image_url")

                    if (url.isNotEmpty()) {
                        // Download
                        val imgRequest = Request.Builder().url(url).build()
                        val imgResponse = client.newCall(imgRequest).execute()
                        val inputStream = imgResponse.body?.byteStream()
                        val bitmap = BitmapFactory.decodeStream(inputStream)

                        if (bitmap != null) {
                            success = true
                            mainHandler.post {
                                cardItems[index].imageUrl = url
                                cardItems[index].bitmap = bitmap
                                // Don't show yet, wait for flip
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!success) {
                mainHandler.post {
                    cardItems[index].imageUrl = "failed"
                    // Optionally set a placeholder error bitmap
                }
            }

            // Check if all done (simple check)
            mainHandler.post {
                val allLoaded = cardItems.all { it.bitmap != null || it.imageUrl == "failed" }
                if (allLoaded) {
                    val successCount = cardItems.count { it.bitmap != null }
                    statusText.text = "Summoned $successCount/6 Cards! Tap to Reveal."
                    btnSummon.isEnabled = true
                }
            }
        }
    }

    private fun flipCard(position: Int) {
        val item = cardItems[position]
        if (item.isFlipped) return // Already flipped
        if (item.bitmap == null) return // Not loaded yet

        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? GodsDrawAdapter.ViewHolder
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

            // Generate Data
            generateCardData(position, item)
        }
    }

    private fun triggerVibration(rarity: String) {
        val r = rarity.uppercase()
        val duration = when {
            r.contains("UR") -> 500L
            r.contains("SSR") -> 300L
            r.contains("SR") -> 150L
            else -> 50L
        }

        val amplitude = when {
            r.contains("UR") -> 255
            r.contains("SSR") -> 200
            r.contains("SR") -> 150
            else -> 80
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                vibrator.vibrate(duration)
            }
        }
    }

    private fun showParticles(holder: GodsDrawAdapter.ViewHolder, rarity: String) {
        holder.particleView.visibility = View.VISIBLE
        val r = rarity.uppercase()
        when {
            r.contains("UR") -> holder.particleView.setConfig(ParticleView.ParticleType.COSMIC)
            r.contains("SSR") -> holder.particleView.setConfig(ParticleView.ParticleType.FLAME)
            r.contains("SR") -> holder.particleView.setConfig(ParticleView.ParticleType.LIGHTNING)
            r.contains("R") -> holder.particleView.setConfig(ParticleView.ParticleType.SPARKLE)
            else -> holder.particleView.setConfig(ParticleView.ParticleType.DUST)
        }
    }

    private fun generateCardData(position: Int, item: GodCardItem) {
        item.isLoading = true
        adapter.notifyItemChanged(position)

        executor.execute {
            try {
                // 0. Check Cache
                val md5 = CardRepository.calculateMD5(item.bitmap!!)
                var data = CardRepository.getCard(md5)
                var fromCache = false

                if (data != null) {
                    fromCache = true
                } else {
                    val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val apiKey = prefs.getString("api_key", "")
                    val apiUrl = prefs.getString("api_url", "https://api.openai.com")
                    val model = prefs.getString("model", "gpt-4o-mini")
                    val customPrompt = prefs.getString("custom_prompt", "Create a funny and creative name and ability description.")

                    if (apiKey.isNullOrEmpty()) throw Exception("No API Key")

                    data = vlmService.analyzeImage(item.bitmap!!, apiKey!!, apiUrl!!, model!!, customPrompt!!)
                    CardRepository.saveCard(this, md5, data)

                    // Save history entry (even if cached, we record the "draw")
                    saveCardToLibrary(data!!, item.bitmap!!)
                }

                mainHandler.post {
                    item.cardData = data
                    item.isLoading = false
                    adapter.notifyItemChanged(position) // Update UI with text

                    if (data != null) {
                        triggerVibration(data.rarity)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    item.isLoading = false
                    // item.error = e.message
                }
            }
        }
    }

    private fun saveCardToLibrary(data: VLMService.CardData, bitmap: Bitmap) {
        // Reuse saving logic
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

    inner class GodsDrawAdapter(
        private val items: List<GodCardItem>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<GodsDrawAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardFlipContainer: FrameLayout = view.findViewById(R.id.cardFlipContainer)
            val cardBack: ImageView = view.findViewById(R.id.cardBack)
            val cardFront: View = view.findViewById(R.id.cardFront) // Include layout
            val particleView: ParticleView = view.findViewById(R.id.particleView)

            // Front Views
            val ivArt: ImageView = view.findViewById(R.id.ivCardArt)
            val tvName: TextView = view.findViewById(R.id.tvCardName)
            val tvRarity: TextView = view.findViewById(R.id.tvCardAttribute)
            val tvDesc: TextView = view.findViewById(R.id.tvCardDesc)
            val tvAtk: TextView = view.findViewById(R.id.tvCardAtk)
            val tvDef: TextView = view.findViewById(R.id.tvCardDef)
            val loadingOverlay: View = view.findViewById(R.id.loadingOverlay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_god_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            // Reset rotation if reusing view?
            // holder.cardFlipContainer.rotationY = if (item.isFlipped) 180f else 0f
            // Doing animations inside bind is tricky. Better to set static state.

            if (item.isFlipped) {
                holder.cardFlipContainer.rotationY = 180f
                holder.cardBack.visibility = View.GONE
                holder.cardFront.visibility = View.VISIBLE
                holder.cardFront.scaleX = -1f

                holder.ivArt.setImageBitmap(item.bitmap)

                if (item.isLoading) {
                    holder.loadingOverlay.visibility = View.VISIBLE
                } else {
                    holder.loadingOverlay.visibility = View.GONE
                }

                if (item.cardData != null) {
                    holder.tvName.text = item.cardData!!.name
                    holder.tvRarity.text = item.cardData!!.rarity
                    holder.tvDesc.text = item.cardData!!.description
                    holder.tvAtk.text = item.cardData!!.atk
                    holder.tvDef.text = item.cardData!!.def

                    // Style
                    val (bg, border) = getRarityColors(item.cardData!!.rarity)
                    val bgDrawable = ContextCompat.getDrawable(holder.itemView.context, R.drawable.bg_card_base) as GradientDrawable
                    bgDrawable.setColor(ContextCompat.getColor(holder.itemView.context, bg))
                    bgDrawable.setStroke(4, ContextCompat.getColor(holder.itemView.context, border))
                    holder.cardFront.background = bgDrawable

                    // Show particles if we have data
                    showParticles(holder, item.cardData!!.rarity)
                } else {
                    holder.tvName.text = "Loading..."
                    holder.tvDesc.text = "Analyzing card data..."
                }

            } else {
                holder.cardFlipContainer.rotationY = 0f
                holder.cardBack.visibility = View.VISIBLE
                holder.cardFront.visibility = View.GONE
                holder.particleView.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onItemClick(position)
            }
        }

        override fun getItemCount() = items.size

        private fun getRarityColors(rarity: String): Pair<Int, Int> {
            val r = rarity.uppercase()
            return when {
                r.contains("UR") -> Pair(R.color.rarity_ur_bg, R.color.rarity_ur_border)
                r.contains("SSR") -> Pair(R.color.rarity_ssr_bg, R.color.rarity_ssr_border)
                r.contains("SR") -> Pair(R.color.rarity_sr_bg, R.color.rarity_sr_border)
                r.contains("R") -> Pair(R.color.rarity_r_bg, R.color.rarity_r_border)
                else -> Pair(R.color.rarity_n_bg, R.color.rarity_n_border)
            }
        }
    }
}
