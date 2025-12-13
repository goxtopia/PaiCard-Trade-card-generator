package com.cardgen.app

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

class LibraryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LibraryAdapter
    private lateinit var imageExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)
        supportActionBar?.title = "Card Library"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Single pool for image loading
        imageExecutor = Executors.newFixedThreadPool(4)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3) // 3 Columns

        val cards = loadCards()
        adapter = LibraryAdapter(cards)
        recyclerView.adapter = adapter
    }

    override fun onDestroy() {
        super.onDestroy()
        imageExecutor.shutdownNow()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadCards(): List<SingleDrawActivity.SavedCard> {
        val prefs = getSharedPreferences("card_library", Context.MODE_PRIVATE)
        val json = prefs.getString("history", "[]")
        val type = object : TypeToken<ArrayList<SingleDrawActivity.SavedCard>>() {}.type
        return Gson().fromJson(json, type) ?: ArrayList()
    }

    inner class LibraryAdapter(private val items: List<SingleDrawActivity.SavedCard>) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.itemImg)
            val name: TextView = view.findViewById(R.id.itemName)
            val rarity: TextView = view.findViewById(R.id.itemRarity)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_library_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.img.setImageBitmap(null) // Clear previous
            holder.img.tag = item.imagePath // Set tag to check if view recycled

            val imgFile = File(item.imagePath)
            if (imgFile.exists()) {
                // Async Load using shared executor
                imageExecutor.execute {
                    // Downsample
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(imgFile.absolutePath, options)
                    options.inSampleSize = calculateInSampleSize(options, 100, 140)
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
        }

        override fun getItemCount() = items.size

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
