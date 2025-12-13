package com.cardgen.app

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var btnSort: Button
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

        btnSort = findViewById(R.id.btnSort)
        recyclerView = findViewById(R.id.recyclerView)
        // Changed to 2 columns as requested
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        allCards = loadCards().toMutableList()
        sortCards() // Initial sort

        adapter = LibraryAdapter(allCards,
            onClick = { card -> openCardDetail(card) },
            onLongClick = { card -> showDeleteDialog(card) }
        )
        recyclerView.adapter = adapter

        btnSort.setOnClickListener { view ->
            showSortMenu(view)
        }
        updateSortButtonText()
    }

    private fun showSortMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, "Date: Newest")
        popup.menu.add(0, 2, 0, "Date: Oldest")
        popup.menu.add(0, 3, 0, "Rarity: High to Low")
        popup.menu.add(0, 4, 0, "Rarity: Low to High")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { currentSortMode = SortMode.DATE_NEWEST; sortCards() }
                2 -> { currentSortMode = SortMode.DATE_OLDEST; sortCards() }
                3 -> { currentSortMode = SortMode.RARITY_HIGH; sortCards() }
                4 -> { currentSortMode = SortMode.RARITY_LOW; sortCards() }
            }
            updateSortButtonText()
            true
        }
        popup.show()
    }

    private fun updateSortButtonText() {
        val text = when (currentSortMode) {
            SortMode.DATE_NEWEST -> "Newest"
            SortMode.DATE_OLDEST -> "Oldest"
            SortMode.RARITY_HIGH -> "High Rarity"
            SortMode.RARITY_LOW -> "Low Rarity"
        }
        btnSort.text = "Sort: $text"
    }

    // Keep Options Menu for consistency, though UI button is primary now
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
            1 -> { currentSortMode = SortMode.DATE_NEWEST; sortCards(); updateSortButtonText() }
            2 -> { currentSortMode = SortMode.DATE_OLDEST; sortCards(); updateSortButtonText() }
            3 -> { currentSortMode = SortMode.RARITY_HIGH; sortCards(); updateSortButtonText() }
            4 -> { currentSortMode = SortMode.RARITY_LOW; sortCards(); updateSortButtonText() }
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

    private fun showDeleteDialog(card: SingleDrawActivity.SavedCard) {
        AlertDialog.Builder(this)
            .setTitle("Delete Card")
            .setMessage("Are you sure you want to delete '${card.name}'? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteCard(card)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCard(card: SingleDrawActivity.SavedCard) {
        // 1. Remove file
        val file = File(card.imagePath)
        if (file.exists()) {
            file.delete()
        }

        // 2. Remove from list
        allCards.remove(card)
        adapter.notifyDataSetChanged()

        // 3. Save updated list
        saveCards(allCards)

        Toast.makeText(this, "Card deleted", Toast.LENGTH_SHORT).show()
    }

    private fun saveCards(cards: List<SingleDrawActivity.SavedCard>) {
        val prefs = getSharedPreferences("card_library", Context.MODE_PRIVATE)
        val json = Gson().toJson(cards)
        prefs.edit().putString("history", json).apply()
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
        private val onClick: (SingleDrawActivity.SavedCard) -> Unit,
        private val onLongClick: (SingleDrawActivity.SavedCard) -> Unit
    ) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.itemImg)
            val name: TextView = view.findViewById(R.id.itemName)
            val rarity: TextView = view.findViewById(R.id.itemRarity)
            val border: View = view.findViewById(R.id.rarityBorder)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // Using the new V2 layout
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_library_card_v2, parent, false)
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
                    options.inSampleSize = calculateInSampleSize(options, 200, 260) // Adjusted for larger view
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

            // Apply Rarity Colors to Border and Badge
            val colorRes = getRarityColorRes(item.rarity)
            val colorInt = ContextCompat.getColor(holder.itemView.context, colorRes)

            // Set border color
            val borderDrawable = holder.border.background as? GradientDrawable
            // Note: GradientDrawable might be wrapped or state list.
            // In the XML it's a shape.
            // We need to re-fetch or cast properly.
            // If it fails, we reconstruct or set Tint.
            // Safest for Shape drawable stroke modification:

            // Approach 2: Use PorterDuff for simple tint on the stroke drawable if it's white
            holder.border.background.mutate().setColorFilter(colorInt, PorterDuff.Mode.SRC_IN)

            // Set Badge background color (semi-transparent version of rarity color)
            val badgeDrawable = holder.rarity.background.mutate() as GradientDrawable
            badgeDrawable.setColor(colorInt) // Solid color for badge? Or semi-transparent?
            // Let's make the badge solid color but slightly dark text or white text. Text is white in XML.
            // Badge background in XML is #80000000 (semi trans black). Let's override it with rarity color.
            badgeDrawable.setColor(colorInt)

            holder.itemView.setOnClickListener {
                onClick(item)
            }

            holder.itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }

        override fun getItemCount() = items.size

        private fun getRarityColorRes(rarity: String): Int {
            val r = rarity.uppercase()
            return when {
                r.contains("UR") -> R.color.rarity_ur_border // We can use the border colors defined in colors.xml or fallback
                r.contains("SSR") -> R.color.rarity_ssr_border
                r.contains("SR") -> R.color.rarity_sr_border
                r.contains("R") -> R.color.rarity_r_border
                else -> R.color.rarity_n_border
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
