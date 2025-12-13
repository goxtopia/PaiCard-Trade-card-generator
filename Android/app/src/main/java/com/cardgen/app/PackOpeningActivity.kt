package com.cardgen.app

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class PackOpeningActivity : AppCompatActivity() {

    private lateinit var packContainer: View
    private lateinit var gridContainer: View
    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: PackGridAdapter
    private var packId: String? = null
    private var packItems: List<PackRepository.PackItem> = ArrayList()

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pack_opening)
        supportActionBar?.hide()

        packId = intent.getStringExtra("packId")
        if (packId == null) {
            finish()
            return
        }

        val pack = PackRepository.getPack(packId!!)
        if (pack == null) {
            finish()
            return
        }
        packItems = pack.items

        initViews(pack)
    }

    private fun initViews(pack: PackRepository.Pack) {
        packContainer = findViewById(R.id.packContainer)
        gridContainer = findViewById(R.id.gridContainer)

        val packItem = findViewById<View>(R.id.packItem)
        val packCount = findViewById<TextView>(R.id.packCountText)

        val btnBack = findViewById<View>(R.id.btnBackToPacks)
        recyclerView = findViewById(R.id.recyclerView)

        // Initial State

        if (pack.status == PackRepository.PackStatus.OPENED) {
            packContainer.visibility = View.GONE
            gridContainer.visibility = View.VISIBLE
            setupGrid()
        } else {
            packContainer.visibility = View.VISIBLE
            gridContainer.visibility = View.GONE
            packCount.text = "Contains ${packItems.size} Cards"

            packItem.setOnClickListener {
                openPack(pack)
            }
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun openPack(pack: PackRepository.Pack) {
        // Animation
        packContainer.animate().alpha(0f).setDuration(500).withEndAction {
            packContainer.visibility = View.GONE
            gridContainer.visibility = View.VISIBLE
            gridContainer.alpha = 0f
            gridContainer.animate().alpha(1f).setDuration(500).start()

            PackRepository.updatePackStatus(this, pack.id, PackRepository.PackStatus.OPENED)
            savePackToLibrary()

            setupGrid()
        }.start()
    }

    private fun savePackToLibrary() {
        executor.execute {
            for (item in packItems) {
                val data = CardRepository.getCard(item.md5)
                if (data != null) {
                    try {
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(android.net.Uri.parse(item.uri)))
                        if (bitmap != null) {
                            val filename = "card_${System.currentTimeMillis()}_${item.md5.take(5)}.png"
                            val file = File(filesDir, filename)
                            val out = FileOutputStream(file)
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                            out.flush()
                            out.close()

                            val savedCard = SingleDrawActivity.SavedCard(
                                data.name, data.rarity, data.description, data.atk, data.def,
                                file.absolutePath, System.currentTimeMillis()
                            )
                            addToHistory(savedCard)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            runOnUiThread {
                Toast.makeText(this, "Cards added to Library!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addToHistory(card: SingleDrawActivity.SavedCard) {
        val prefs = getSharedPreferences("card_library", Context.MODE_PRIVATE)
        val json = prefs.getString("history", "[]")
        val type = object : TypeToken<ArrayList<SingleDrawActivity.SavedCard>>() {}.type
        val list: ArrayList<SingleDrawActivity.SavedCard> = Gson().fromJson(json, type) ?: ArrayList()
        list.add(0, card)
        prefs.edit().putString("history", Gson().toJson(list)).apply()
    }

    private fun setupGrid() {
        adapter = PackGridAdapter(packItems) { item, holder ->
             flipCard(item, holder)
        }
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
    }

    private fun flipCard(item: PackRepository.PackItem, holder: PackGridAdapter.ViewHolder) {
         if (holder.isFlipped) return

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
                     holder.cardFront.scaleX = -1f
                 }
            }
        }

        animator.start()
        holder.isFlipped = true
    }

    // Adapter
    inner class PackGridAdapter(
        private val items: List<PackRepository.PackItem>,
        private val onItemClick: (PackRepository.PackItem, ViewHolder) -> Unit
    ) : RecyclerView.Adapter<PackGridAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var isFlipped = false
            val cardFlipContainer: FrameLayout = view.findViewById(R.id.cardFlipContainer)
            val cardBack: ImageView = view.findViewById(R.id.cardBack)
            val cardFront: CardView = view.findViewById(R.id.cardFront)

            val ivArt: ImageView = view.findViewById(R.id.ivCardArt)
            val tvName: TextView = view.findViewById(R.id.tvCardName)
            val tvRarity: TextView = view.findViewById(R.id.tvCardAttribute)
            val tvDesc: TextView = view.findViewById(R.id.tvCardDesc)
            val tvAtk: TextView = view.findViewById(R.id.tvCardAtk)
            val tvDef: TextView = view.findViewById(R.id.tvCardDef)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_god_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val cardData = CardRepository.getCard(item.md5)

             if (cardData != null) {
                holder.tvName.text = cardData.name
                holder.tvRarity.text = cardData.rarity
                holder.tvDesc.text = cardData.description
                holder.tvAtk.text = cardData.atk
                holder.tvDef.text = cardData.def

                try {
                     val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(android.net.Uri.parse(item.uri)))
                     holder.ivArt.setImageBitmap(bitmap)
                } catch(e:Exception){}

                applyRarityVisuals(holder, cardData.rarity)
            }

            holder.itemView.setOnClickListener {
                onItemClick(item, holder)
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

            if (holder.cardFront.childCount > 0) {
                 val innerLayout = holder.cardFront.getChildAt(0)
                 val bgDrawable = ContextCompat.getDrawable(holder.itemView.context, R.drawable.bg_card_base) as GradientDrawable
                 bgDrawable.setColor(ContextCompat.getColor(holder.itemView.context, bgColor))
                 bgDrawable.setStroke(4, ContextCompat.getColor(holder.itemView.context, borderColor))
                 innerLayout.background = bgDrawable
            }
        }

        override fun getItemCount() = items.size
    }
}
