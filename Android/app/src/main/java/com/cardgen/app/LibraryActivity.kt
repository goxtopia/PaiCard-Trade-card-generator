package com.cardgen.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

class LibraryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LibraryAdapter
    private lateinit var imageExecutor: ExecutorService

    private var allCards: MutableList<SingleDrawActivity.SavedCard> = ArrayList()
    private var currentSortMode = SortMode.DATE_NEWEST

    enum class SortMode {
        DATE_NEWEST, DATE_OLDEST, RARITY_HIGH, RARITY_LOW
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)
        supportActionBar?.title = "Card Library"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        imageExecutor = Executors.newFixedThreadPool(4)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        allCards = loadCards().toMutableList()
        sortCards() // Initial sort

        adapter = LibraryAdapter(allCards) { card ->
            openCardDetail(card)
        }
        recyclerView.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Date: Newest")
        menu?.add(0, 2, 0, "Date: Oldest")
        menu?.add(0, 3, 0, "Rarity: High to Low")
        menu?.add(0, 4, 0, "Rarity: Low to High")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            1 -> { currentSortMode = SortMode.DATE_NEWEST; sortCards() }
            2 -> { currentSortMode = SortMode.DATE_OLDEST; sortCards() }
            3 -> { currentSortMode = SortMode.RARITY_HIGH; sortCards() }
            4 -> { currentSortMode = SortMode.RARITY_LOW; sortCards() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun sortCards() {
        when (currentSortMode) {
            SortMode.DATE_NEWEST -> allCards.sortByDescending { it.timestamp }
            SortMode.DATE_OLDEST -> allCards.sortBy { it.timestamp }
            SortMode.RARITY_HIGH -> allCards.sortWith(compareByDescending { getRarityValue(it.rarity) })
            SortMode.RARITY_LOW -> allCards.sortWith(compareBy { getRarityValue(it.rarity) })
        }
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun getRarityValue(rarity: String): Int {
        val r = rarity.uppercase()
        return when {
            r.contains("UR") -> 5
            r.contains("SSR") -> 4
            r.contains("SR") -> 3
            r.contains("R") -> 2
            else -> 1
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageExecutor.shutdownNow()
    }

    private fun loadCards(): List<SingleDrawActivity.SavedCard> {
        val prefs = getSharedPreferences("card_library", Context.MODE_PRIVATE)
        val json = prefs.getString("history", "[]")
        val type = object : TypeToken<ArrayList<SingleDrawActivity.SavedCard>>() {}.type
        return Gson().fromJson(json, type) ?: ArrayList()
    }

    private fun openCardDetail(card: SingleDrawActivity.SavedCard) {
        val intent = Intent(this, CardDetailActivity::class.java)
        intent.putExtra("name", card.name)
        intent.putExtra("rarity", card.rarity)
        intent.putExtra("desc", card.description)
        intent.putExtra("atk", card.atk)
        intent.putExtra("def", card.def)
        intent.putExtra("imagePath", card.imagePath)
        startActivity(intent)
    }

    inner class LibraryAdapter(
        private val items: List<SingleDrawActivity.SavedCard>,
        private val onClick: (SingleDrawActivity.SavedCard) -> Unit
    ) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.itemImg)
            val name: TextView = view.findViewById(R.id.itemName)
            val rarity: TextView = view.findViewById(R.id.itemRarity)
            val innerLayout: View = view.findViewById(R.id.cardInnerLayout)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_library_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.img.setImageBitmap(null)
            holder.img.tag = item.imagePath

            val imgFile = File(item.imagePath)
            if (imgFile.exists()) {
                imageExecutor.execute {
                    // Downsample
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(imgFile.absolutePath, options)
                    options.inSampleSize = calculateInSampleSize(options, 100, 145)
                    options.inJustDecodeBounds = false
                    val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath, options)

                    runOnUiThread {
                        if (holder.img.tag == item.imagePath) {
                             holder.img.setImageBitmap(bitmap)
                        }
                    }
                }
            } else {
                holder.img.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            holder.name.text = item.name
            holder.rarity.text = item.rarity

            // Apply Rarity Colors (Mini Version)
            val (bgColor, borderColor) = getRarityColors(item.rarity)
            val bgDrawable = ContextCompat.getDrawable(holder.itemView.context, R.drawable.bg_card_base) as GradientDrawable
            bgDrawable.setColor(ContextCompat.getColor(holder.itemView.context, bgColor))
            bgDrawable.setStroke(2, ContextCompat.getColor(holder.itemView.context, borderColor)) // Thinner border for mini
            holder.innerLayout.background = bgDrawable

            holder.itemView.setOnClickListener {
                onClick(item)
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

        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val (height: Int, width: Int) = options.run { outHeight to outWidth }
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }
}
